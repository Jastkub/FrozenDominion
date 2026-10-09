package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.client.sound.VelkharMusicInstance;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private static final ResourceLocation BOSS_BAR_TEXTURE = FrozenFortress.id("textures/gui/boss_bar.png");
    private static final String BOSS_TITLE_KEY = "entity.frozen_dominion.velkhar.title";
    private static final String GOLEM_TITLE_KEY = "entity.frozen_dominion.ice_monstrosity";
    private static final String TURNKEY_TITLE_KEY = "entity.frozen_dominion.turnkey";
    private static final String BONE_LORD_TITLE_KEY = "entity.frozen_dominion.bone_lord";
    private static final String PRIESTESS_TITLE_KEY = "entity.frozen_dominion.rime_priestess";
    private static final String LAMPLIGHTER_TITLE_KEY = "entity.frozen_dominion.lamplighter";
    private static final String DROWNED_TITLE_KEY = "entity.frozen_dominion.drowned_lady";
    private static final String AUROCHS_TITLE_KEY = "entity.frozen_dominion.ice_aurochs";
    private static final String SHEPHERD_TITLE_KEY = "entity.frozen_dominion.shade_shepherd";
    private static final String OVERSEER_TITLE_KEY = "entity.frozen_dominion.forge_overseer";

    /** The Velkhar this client is currently watching, refreshed every tick. */
    @Nullable
    /** How long one blow's shake lasts, in ticks. */
    private static final float IMPACT_TICKS = 8.0F;
    /** The last packed impact seen, so a CHANGE is what starts a shake. */
    private static int lastImpact;
    private static float impactAmp;
    private static long impactStamp;
    /** The same three, for the colossus - it lands its own blows. */
    private static int lastGolemImpact;
    private static float golemImpactAmp;
    private static long golemImpactStamp;

    private static VelkharEntity activeBoss;
    /** The Hrimthar this client is watching, if one is standing. */
    @Nullable
    private static com.jastkub.frozenfortress.entity.HollowGolemEntity activeGolem;
    @Nullable
    private static VelkharMusicInstance currentMusic;
    private static boolean outroPlayed;
    /** Ticks the current cutscene has been running, for the letterbox ease-in. */
    private static int cutsceneTicks;

    /**
     * THE SPOKEN SENTENCE, put on the screen while it is being spoken.
     *
     * <p>Chat is one of the overlays a cutscene cancels, so the words {@code
     * speak()} sends there go nowhere during a scene - which is precisely when
     * they matter most. These three fields mirror the impact-shake pattern: the
     * boss publishes {@code (sequence << 4) | id}, and the client watches for
     * the NUMBER CHANGING rather than for a particular value, so the same line
     * can be shown twice and a line can repeat across two fights.
     */
    private static int lastCutLine;
    private static int cutLineId;
    private static int cutLineTicks;

    @Nullable
    public static VelkharEntity getActiveBoss() {
        return activeBoss;
    }

    /** Is one of his sentences being spoken now (its audio running - the caption's clock past its fade-in)? */
    public static boolean lineSpoken() {
        return cutLineId > 0 && cutLineTicks > CAPTION_FADE;
    }

    // ================================================================
    // Tick: track the boss, drive the music
    // ================================================================

    /** The attack key on a saddled Monstrosity's back: it sweeps (07.10.2026, RiderStrikePacket). The rider's own blow
     *  still lands as well - this only adds the colossus's arm to it. */
    @SubscribeEvent
    public static void onRiderAttack(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (event.isAttack() && mc.player != null
                && mc.player.getVehicle() instanceof com.jastkub.frozenfortress.entity.HollowGolemEntity golem
                && golem.getControllingPassenger() == mc.player) {
            com.jastkub.frozenfortress.network.FFNetwork.CHANNEL.sendToServer(
                    new com.jastkub.frozenfortress.network.RiderStrikePacket());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            activeBoss = null;
            prisonScene = false;
            prisonShown.clear();
            stopMusic();
            outroPlayed = false;
            com.jastkub.frozenfortress.client.gui.VelkharBossBar.reset();
            return;
        }

        // Find the closest fighting Velkhar.
        VelkharEntity closest = null;
        double bestDist = 100.0D * 100.0D;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof VelkharEntity velkhar && !velkhar.isDormant()) {
                double d = velkhar.distanceToSqr(mc.player);
                if (d < bestDist) {
                    bestDist = d;
                    closest = velkhar;
                }
            }
        }
        if (closest == null && activeBoss != null) {
            // the fight is over, one way or the other: drop the bar's smoothing
            com.jastkub.frozenfortress.client.gui.VelkharBossBar.reset();
        }
        activeBoss = closest;

        tickPrisonScene(mc);
        com.jastkub.frozenfortress.client.BossScenes.tick(mc);
        // how many ticks the current cutscene has run, for the letterbox ease
        if ((activeBoss != null && activeBoss.cutscene() != 0) || prisonScene || com.jastkub.frozenfortress.client.BossScenes.active()) {
            cutsceneTicks++;
        } else {
            cutsceneTicks = 0;
        }

        // --- the caption clock. A new packed value means a new line; it then
        //     runs down over exactly the audio's own length plus the fades.
        if (activeBoss != null) {
            int packed = activeBoss.cutLinePacked();
            if (packed != lastCutLine) {
                lastCutLine = packed;
                cutLineId = packed & 0xF;
                cutLineTicks = cutLineId > 0
                        ? VelkharEntity.cutLineTicks(cutLineId) + CAPTION_FADE * 2 : 0;
            }
        } else {
            cutLineId = 0;
            cutLineTicks = 0;
        }
        if (cutLineTicks > 0) {
            cutLineTicks--;
        }

        com.jastkub.frozenfortress.entity.HollowGolemEntity golem = null;
        double golemDist = 100.0D * 100.0D;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof com.jastkub.frozenfortress.entity.HollowGolemEntity g
                    && g.isAlive() && !g.isTamed()) {
                double d = g.distanceToSqr(mc.player);
                if (d < golemDist) {
                    golemDist = d;
                    golem = g;
                }
            }
        }
        if (golem == null && activeGolem != null) {
            com.jastkub.frozenfortress.client.gui.HrimtharBossBar.reset();
            com.jastkub.frozenfortress.client.gui.MonstrosityBossBar.reset();
        }
        activeGolem = golem;

        tickMusic(mc, closest);
        tickCitadelMusic(mc, closest);
    }

    // ================================================================
    // The citadel's own theme, while inside it
    // ================================================================
    private static com.jastkub.frozenfortress.client.sound.CitadelMusicInstance citadelMusic;
    private static int citadelScan;
    private static int citadelAway;
    private static boolean inCitadel;

    /**
     * Whether the player is in the citadel is read off the blocks round them -
     * the frosted and sealed stone, the sconces and candelabra, the statues
     * and the doors are the citadel's and nobody else's - every two seconds,
     * and left only after three empty looks in a row, so a corridor of plain
     * rock does not stop the music for a breath. The kings' music and the
     * colossus's fight take precedence: under them the theme fades out.
     */
    private static void tickCitadelMusic(Minecraft mc, @Nullable VelkharEntity boss) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (++citadelScan >= 40) {
            citadelScan = 0;
            if (scanCitadel(mc)) {
                inCitadel = true;
                citadelAway = 0;
            } else if (++citadelAway >= 3) {
                inCitadel = false;
            }
        }
        boolean kingsMusic = boss != null || currentMusic != null;
        boolean golemAlive = activeGolem != null && !activeGolem.isDeadOrDying();
        // THE FIGHTS HAVE THEIR OWN MUSIC: the Monstrosity's while it is up and
        // near (the king's own music wins when it is his summon), and the court's while a boss gate
        // is down with the player inside its room - the Turnkey's hall, the Chapel of Rime; the gate
        // rising (the keeper dead, or the player) brings the citadel back
        String court = kingsMusic ? null
                : golemAlive && activeGolem.distanceToSqr(mc.player) < 56.0D * 56.0D ? "frozen_dominion:ice_monstrosity"
                : courtFight(mc);
        if (court != null && mc.getSoundManager().getSoundEvent(fightTrack(court).getLocation()) == null) {
            court = null;                          // no track for it shipped yet
        }
        tickCourtMusic(mc, court);
        boolean bossMusic = kingsMusic || golemAlive || court != null;
        if (inCitadel && !bossMusic) {
            mc.getMusicManager().stopPlaying();
            if (citadelMusic != null && citadelMusic.isFading() && !citadelMusic.isStopped()) {
                citadelMusic.stay();
            }
            if (citadelMusic == null || citadelMusic.isStopped() || !mc.getSoundManager().isActive(citadelMusic)) {
                citadelMusic = new com.jastkub.frozenfortress.client.sound.CitadelMusicInstance(
                        FFSounds.MUSIC_CITADEL.get());
                mc.getSoundManager().play(citadelMusic);
            }
        } else if (citadelMusic != null) {
            citadelMusic.fadeOut();
            if (citadelMusic.isStopped()) {
                citadelMusic = null;
            }
        }
    }

    private static com.jastkub.frozenfortress.client.sound.CitadelMusicInstance courtMusic;
    @Nullable
    private static String courtPlaying;

    private static net.minecraft.sounds.SoundEvent fightTrack(String keeper) {
        if (keeper.endsWith("rime_priestess")) {
            return FFSounds.MUSIC_PRIESTESS.get();
        }
        if (keeper.endsWith("drowned_lady")) {
            return FFSounds.MUSIC_DROWNED.get();
        }
        if (keeper.endsWith("ice_aurochs")) {
            return FFSounds.MUSIC_AUROCHS.get();
        }
        if (keeper.endsWith("shade_shepherd")) {
            return FFSounds.MUSIC_SHEPHERD.get();
        }
        if (keeper.endsWith("forge_overseer")) {
            return FFSounds.MUSIC_OVERSEER.get();
        }
        if (keeper.endsWith("lamplighter")) {
            return FFSounds.MUSIC_LAMPLIGHTER.get();
        }
        if (keeper.endsWith("bone_lord")) {
            return FFSounds.MUSIC_BONE_LORD.get();
        }
        return keeper.endsWith("ice_monstrosity") ? FFSounds.MUSIC_MONSTROSITY.get() : FFSounds.MUSIC_TURNKEY.get();
    }

    /** The keeper of the gate that is down round the player, or null. */
    @Nullable
    private static String courtFight(Minecraft mc) {
        net.minecraft.world.phys.Vec3 p = mc.player.position();
        for (com.jastkub.frozenfortress.block.entity.BossGateBlockEntity g
                : com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.CLIENT) {
            // the king's own gate: his fight has its own music (the phases'), not a court track
            if (!g.isRemoved() && g.getLevel() == mc.level && g.shutOn(p) && !g.keeper().endsWith(":velkhar")) {
                return g.keeper();
            }
        }
        return null;
    }

    private static void tickCourtMusic(Minecraft mc, @Nullable String keeper) {
        if (keeper != null) {
            mc.getMusicManager().stopPlaying();
            if (courtMusic != null && keeper.equals(courtPlaying) && courtMusic.isFading() && !courtMusic.isStopped()) {
                courtMusic.stay();
            }
            if (courtMusic == null || !keeper.equals(courtPlaying) || courtMusic.isStopped()
                    || !mc.getSoundManager().isActive(courtMusic)) {
                if (courtMusic != null) {
                    mc.getSoundManager().stop(courtMusic);
                }
                courtPlaying = keeper;
                courtMusic = new com.jastkub.frozenfortress.client.sound.CitadelMusicInstance(fightTrack(keeper));
                mc.getSoundManager().play(courtMusic);
            }
        } else if (courtMusic != null) {
            courtMusic.fadeOut();
            if (courtMusic.isStopped()) {
                courtMusic = null;
                courtPlaying = null;
            }
        }
    }

    private static boolean scanCitadel(Minecraft mc) {
        net.minecraft.core.BlockPos c = mc.player.blockPosition();
        net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
        int found = 0;
        for (int dx = -10; dx <= 10; dx += 2) {
            for (int dy = -8; dy <= 8; dy += 2) {
                for (int dz = -10; dz <= 10; dz += 2) {
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    net.minecraft.world.level.block.Block b = mc.level.getBlockState(p).getBlock();
                    if (b == com.jastkub.frozenfortress.registry.FFBlocks.FROSTED_STONE_BRICKS.get()
                            || b == com.jastkub.frozenfortress.registry.FFBlocks.SNOWY_STONE_BRICKS.get()
                            || b == com.jastkub.frozenfortress.registry.FFBlocks.ICEBOUND_STONE_BRICKS.get()
                            || b == com.jastkub.frozenfortress.registry.FFBlocks.SEALED_STONE_BRICKS.get()
                            || b == com.jastkub.frozenfortress.registry.FFBlocks.SEALED_DEEPSLATE_TILES.get()
                            || b == com.jastkub.frozenfortress.registry.FFBlocks.FROST_SCONCE.get()
                            || b == com.jastkub.frozenfortress.registry.FFBlocks.FROST_CANDELABRA.get()
                            || b == com.jastkub.frozenfortress.registry.FFBlocks.FLUTED_STONE_PILLAR.get()
                            || b == com.jastkub.frozenfortress.registry.FFBlocks.CITADEL_STATUE.get()
                            || b == com.jastkub.frozenfortress.registry.FFBlocks.STATUE_CORE.get()
                            || b == com.jastkub.frozenfortress.registry.FFBlocks.CITADEL_SPAWNER.get()
                            || b == com.jastkub.frozenfortress.registry.FFBlocks.DOOR_BARRIER.get()) {
                        if (++found >= 3) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void tickMusic(Minecraft mc, @Nullable VelkharEntity boss) {
        if (boss == null) {
            if (currentMusic != null && !currentMusic.isOutro()) {
                stopMusic();
            }
            if (currentMusic != null && currentMusic.isOutro()
                    && !mc.getSoundManager().isActive(currentMusic)) {
                currentMusic = null;
                outroPlayed = false;
            }
            return;
        }

        // Silence the vanilla soundtrack while the king sings.
        mc.getMusicManager().stopPlaying();

        if (boss.isDeadOrDying()) {
            if (!outroPlayed) {
                stopMusic();
                currentMusic = new VelkharMusicInstance(FFSounds.MUSIC_VELKHAR_OUTRO.get(), boss, 0, true);
                mc.getSoundManager().play(currentMusic);
                outroPlayed = true;
            }
            return;
        }

        int phase = boss.getPhase();
        if (currentMusic == null || currentMusic.isOutro() || currentMusic.getForPhase() != phase
                || !mc.getSoundManager().isActive(currentMusic)) {
            stopMusic();
            var track = switch (phase) {
                case 1 -> FFSounds.MUSIC_VELKHAR_PHASE1;
                case 2 -> FFSounds.MUSIC_VELKHAR_PHASE2;
                default -> FFSounds.MUSIC_VELKHAR_PHASE3;
            };
            currentMusic = new VelkharMusicInstance(track.get(), boss, phase, false);
            mc.getSoundManager().play(currentMusic);
        }
    }

    private static void stopMusic() {
        if (currentMusic != null) {
            Minecraft.getInstance().getSoundManager().stop(currentMusic);
            currentMusic = null;
        }
    }

    // ================================================================
    // Custom boss bar
    // ================================================================

    /**
     * THE HUD GETS OUT OF THE WAY FOR A CUTSCENE.
     *
     * <p>Cancelling the Pre event for every vanilla overlay takes the hotbar,
     * the health and hunger rows, the experience bar, the crosshair and the
     * vanilla boss bar off the screen for the length of the shot - a letterbox
     * with an inventory sitting in the middle of it is a paused game, not a
     * scene. The letterbox and title are drawn from RenderGuiEvent.Post, which
     * is a different event, so they still come through.
     */
    @SubscribeEvent
    public static void onCutsceneHud(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
        if (cutsceneActive()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        // and OUR bars go with it - they are drawn by hand here rather than by
        // the overlay above, so cancelling that one does not catch them
        if (cutsceneActive()) {
            event.setCanceled(true);
            event.setIncrement(0);
            return;
        }
        Component name = event.getBossEvent().getName();
        // a party's "\u00d7N" (PartyScaling) rides on the name: drawn by us off the bar's end, not in the name
        int party = com.jastkub.frozenfortress.event.PartyScaling.count(name);
        name = com.jastkub.frozenfortress.event.PartyScaling.strip(name);
        if (hasKey(name, BOSS_TITLE_KEY)) {
            event.setCanceled(true);
            // Tells vanilla how far down to push anything below this bar -
            // which is the only thing keeping Hrimthar's off the top of it.
            event.setIncrement(badge(event, party, com.jastkub.frozenfortress.client.gui.VelkharBossBar.render(
                    event.getGuiGraphics(), event.getY(), name,
                    event.getBossEvent().getProgress(), activeBoss)));
            return;
        }
        if (hasKey(name, GOLEM_TITLE_KEY)) {
            event.setCanceled(true);
            event.setIncrement(badge(event, party, com.jastkub.frozenfortress.client.gui.MonstrosityBossBar.render(
                    event.getGuiGraphics(), event.getY(), name,
                    event.getBossEvent().getProgress(), activeGolem)));
            return;
        }
        if (hasKey(name, BONE_LORD_TITLE_KEY)) {
            event.setCanceled(true);
            event.setIncrement(badge(event, party, com.jastkub.frozenfortress.client.gui.CourtBossBar.renderBoneLord(
                    event.getGuiGraphics(), event.getY(), name, event.getBossEvent().getProgress())));
            return;
        }
        // the court's two minibosses, bars of the king's family
        if (hasKey(name, TURNKEY_TITLE_KEY)) {
            event.setCanceled(true);
            event.setIncrement(badge(event, party, com.jastkub.frozenfortress.client.gui.CourtBossBar.renderTurnkey(
                    event.getGuiGraphics(), event.getY(), name, event.getBossEvent().getProgress())));
            return;
        }
        if (hasKey(name, OVERSEER_TITLE_KEY)) {
            event.setCanceled(true);
            event.setIncrement(badge(event, party, com.jastkub.frozenfortress.client.gui.CourtBossBar.renderOverseer(
                    event.getGuiGraphics(), event.getY(), name, event.getBossEvent().getProgress())));
            return;
        }
        if (hasKey(name, SHEPHERD_TITLE_KEY)) {
            event.setCanceled(true);
            event.setIncrement(badge(event, party, com.jastkub.frozenfortress.client.gui.CourtBossBar.renderShepherd(
                    event.getGuiGraphics(), event.getY(), name, event.getBossEvent().getProgress())));
            return;
        }
        if (hasKey(name, AUROCHS_TITLE_KEY)) {
            event.setCanceled(true);
            event.setIncrement(badge(event, party, com.jastkub.frozenfortress.client.gui.CourtBossBar.renderAurochs(
                    event.getGuiGraphics(), event.getY(), name, event.getBossEvent().getProgress())));
            return;
        }
        if (hasKey(name, DROWNED_TITLE_KEY)) {
            event.setCanceled(true);
            event.setIncrement(badge(event, party, com.jastkub.frozenfortress.client.gui.CourtBossBar.renderDrowned(
                    event.getGuiGraphics(), event.getY(), name, event.getBossEvent().getProgress())));
            return;
        }
        if (hasKey(name, LAMPLIGHTER_TITLE_KEY)) {
            event.setCanceled(true);
            event.setIncrement(badge(event, party, com.jastkub.frozenfortress.client.gui.CourtBossBar.renderLamplighter(
                    event.getGuiGraphics(), event.getY(), name, event.getBossEvent().getProgress())));
            return;
        }
        if (hasKey(name, PRIESTESS_TITLE_KEY)) {
            event.setCanceled(true);
            event.setIncrement(badge(event, party, com.jastkub.frozenfortress.client.gui.CourtBossBar.renderPriestess(
                    event.getGuiGraphics(), event.getY(), name, event.getBossEvent().getProgress())));
        }
    }

    /** A party's "\u00d7N" (PartyScaling), just off the right end of our bars, level with the track. */
    private static int badge(CustomizeGuiOverlayEvent.BossEventProgress event, int party, int increment) {
        if (party > 1) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            String text = "\u00d7" + party;
            int x = mc.getWindow().getGuiScaledWidth() / 2 + 98 + 9;
            int y = event.getY() + 6 + 16 - mc.font.lineHeight / 2;
            event.getGuiGraphics().drawString(mc.font, text, x, y, 0xFFD27F, true);
        }
        return increment;
    }

    private static boolean hasKey(Component name, String key) {
        if (name.getContents() instanceof TranslatableContents translatable
                && key.equals(translatable.getKey())) {
            return true;
        }
        for (Component sibling : name.getSiblings()) {
            if (hasKey(sibling, key)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================
    // The sky goes dark in phase 3
    // ================================================================

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        VelkharEntity boss = activeBoss;
        Minecraft mc = Minecraft.getInstance();
        if (!com.jastkub.frozenfortress.config.FFConfig.darkness()
                || boss == null || mc.player == null || boss.getPhase() < 3) {
            return;
        }
        float strength = darknessStrength(boss);
        if (strength <= 0.0F) {
            return;
        }
        // Sink the world into deep glacial dusk.
        event.setRed(Mth.lerp(strength, event.getRed(), 0.02F));
        event.setGreen(Mth.lerp(strength, event.getGreen(), 0.05F));
        event.setBlue(Mth.lerp(strength, event.getBlue(), 0.09F));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        VelkharEntity boss = activeBoss;
        if (!com.jastkub.frozenfortress.config.FFConfig.darkness()
                || boss == null || boss.getPhase() < 3) {
            return;
        }
        float strength = darknessStrength(boss);
        if (strength <= 0.0F) {
            return;
        }
        event.setNearPlaneDistance(Mth.lerp(strength, event.getNearPlaneDistance(), 6.0F));
        event.setFarPlaneDistance(Mth.lerp(strength, event.getFarPlaneDistance(), 48.0F));
        event.setCanceled(true);
    }

    private static float darknessStrength(VelkharEntity boss) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0.0F;
        }
        double dist = Math.sqrt(boss.distanceToSqr(mc.player));
        return (float) Mth.clamp(1.0D - (dist - 30.0D) / 30.0D, 0.0D, 1.0D);
    }

    // ================================================================
    // Camera shake on the heavy hits
    // ================================================================

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        VelkharEntity boss = activeBoss;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused()) {
            return;
        }
        // A CUTSCENE OWNS THE CAMERA. While the fortress king is running one of
        // his scripted beats, the view locks onto him and drifts slowly - the
        // horizon stops answering to the mouse and the shot frames the boss.
        // It stays at the player's eye (no mixin, so no risk to loading) but
        // the aim is scripted, and the letterbox and the frozen controls sell
        // it as a cutscene rather than as the player just looking at him.
        // A KEEPER'S ENTRANCE (BossCutscenes): a film - its shots, cuts and moves are BossScenes' own
        if (com.jastkub.frozenfortress.client.BossScenes.camera(event, boss == null || boss.cutscene() == 0)) {
            return;
        }
        // A BOSS'S SCENE (BossCutscenes) otherwise - a death: the same held, drifting shot, on that boss
        if (com.jastkub.frozenfortress.client.BossScenes.active() && com.jastkub.frozenfortress.client.BossScenes.subject() != null && (boss == null || boss.cutscene() == 0)) {
            float pt = (float) event.getPartialTick();
            net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(pt);
            net.minecraft.world.phys.Vec3 d = com.jastkub.frozenfortress.client.BossScenes.subject().subtract(eye);
            double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
            double time = (mc.level != null ? mc.level.getGameTime() : 0L) + pt;
            event.setYaw((float) (Math.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F + (float) (Math.sin(time * 0.03) * 5.0));
            event.setPitch((float) (-Math.atan2(d.y, Math.max(0.01, horiz)) * (180.0 / Math.PI))
                    + (float) (Math.sin(time * 0.021) * 2.0));
            event.setRoll((float) (Math.sin(time * 0.017) * 0.8));
            return;
        }
        if (prisonScene && prisonSubject != null && (boss == null || boss.cutscene() == 0)) {
            float pt = (float) event.getPartialTick();
            net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(pt);
            net.minecraft.world.phys.Vec3 d = prisonSubject.subtract(eye);
            double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
            double time = (mc.level != null ? mc.level.getGameTime() : 0L) + pt;
            event.setYaw((float) (Math.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F + (float) (Math.sin(time * 0.03) * 6.0));
            event.setPitch((float) (-Math.atan2(d.y, Math.max(0.01, horiz)) * (180.0 / Math.PI))
                    + (float) (Math.sin(time * 0.021) * 2.0));
            // the statue's shudder and the roar shake the shot
            event.setRoll((float) (Math.sin(time * 0.9) * (prisonSceneTicks < 70 ? 0.25 + prisonSceneTicks * 0.01 : 1.2)));
            return;
        }
        if (boss != null && !boss.isRemoved() && boss.cutscene() != 0) {
            float pt = (float) event.getPartialTick();
            // HIS SCENES IN SHOTS (08.10.2026): the camera moved round him, cut to cut (VelkharScenes) - and, should it
            // not take (no camera to move), the old shot from the player's eyes, turned on him
            if (!com.jastkub.frozenfortress.client.VelkharScenes.camera(event, boss, activeGolem)) {
            net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(pt);
            // THE SUMMONING FRAMES THE BEAST, NOT THE KING. In every other
            // cutscene he is the subject; in this one he opens a hole in the
            // floor and the thing that climbs out of it is what the shot is
            // for, so once the Monstrosity is up the camera goes to it.
            net.minecraft.world.entity.Entity subject = boss;
            if (boss.cutscene() == 6 && activeGolem != null && activeGolem.isAlive()) {
                subject = activeGolem;
            }
            double dx = subject.getX() - eye.x;
            double dy = (subject.getY() + subject.getBbHeight() * 0.62D) - eye.y;
            double dz = subject.getZ() - eye.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            double time = (mc.level != null ? mc.level.getGameTime() : 0L) + pt;
            float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F
                    + (float) (Math.sin(time * 0.03) * 9.0);
            float pitch = (float) (-Math.atan2(dy, Math.max(0.01, horiz)) * (180.0 / Math.PI))
                    + (float) (Math.sin(time * 0.021) * 2.5);
            event.setYaw(yaw);
            event.setPitch(pitch);
            event.setRoll((float) (Math.sin(time * 0.017) * 1.1));
            }
            // HIS BLOWS LAND IN HIS OWN SCENES TOO (08.10.2026: the armour's splits, the burst, the weapons set). Only
            // the impulses (thump), never a state's rumble, and softer than in the fight so the shot stays readable;
            // kept in step here so a thump inside a scene no longer fires late, the moment the scene hands back
            int packed = boss.impactPacked();
            long now = mc.level != null ? mc.level.getGameTime() : 0L;
            if (packed != lastImpact) {
                lastImpact = packed;
                impactAmp = (packed & 0xFF) / 255.0F;
                impactStamp = now;
            }
            if (impactAmp > 0.0F) {
                float since = (now - impactStamp) + pt;
                if (since >= IMPACT_TICKS) {
                    impactAmp = 0.0F;
                } else {
                    float k = 1.0F - since / IMPACT_TICKS;
                    applyShake(event, mc, impactAmp * k * k * 1.2F);
                }
            }
            return;
        }
        // THE COLOSSUS SHAKES THE ROOM TOO, and until now it did not shake it
        // at all. This whole handler was gated on the KING being present and
        // read only his state, so Hrimthar - six blocks of ice putting both
        // fists through the floor - landed every blow into a camera that did
        // not move. That is the entire reason its weight could not be felt:
        // the particles and the sound said one thing and the perfectly steady
        // horizon said another, and the horizon wins.
        //
        // Attenuated from ITS position, not his, and added rather than
        // maxed - if both of them land something at once the room should be
        // worse, not merely as bad as the louder one.
        float golemShake = 0.0F;
        var golem = activeGolem;
        if (golem != null && golem.isAlive()) {
            long gnow = mc.level != null ? mc.level.getGameTime() : 0L;
            // A LEVEL while it runs: the charge is a sustained rumble under
            // the impulses its footfalls throw on top.
            float gamp = golem.isCharging() ? 0.42F : golem.isWindingUp() ? 0.1F : 0.0F;
            int gpacked = golem.impactPacked();
            if (gpacked != lastGolemImpact) {
                lastGolemImpact = gpacked;
                golemImpactAmp = (gpacked & 0xFF) / 255.0F;
                golemImpactStamp = gnow;
            }
            if (golemImpactAmp > 0.0F) {
                float since = (gnow - golemImpactStamp) + (float) event.getPartialTick();
                if (since >= IMPACT_TICKS) {
                    golemImpactAmp = 0.0F;
                } else {
                    float k = 1.0F - since / IMPACT_TICKS;
                    gamp += golemImpactAmp * k * k * 2.1F;
                }
            }
            if (gamp > 0.0F) {
                double gd = Math.sqrt(golem.distanceToSqr(mc.player));
                golemShake = gamp * (float) Mth.clamp(1.0D - gd / 44.0D, 0.0D, 1.0D);
            }
        }
        if (boss == null) {
            if (golemShake > 0.0F) {
                applyShake(event, mc, golemShake);
            }
            return;
        }
        // EVERY BLOW THAT GOES INTO THE FLOOR MOVES THE CAMERA.
        //
        // The three ground strikes were sending debris and playing impact
        // sounds, but the screen sat perfectly still through all of them -
        // shakeNearbyPlayers() only throws particles; the camera is driven
        // from here, off the attack state, and these states were simply not
        // in the list. A room that erupts under a still camera tells the
        // player two different stories about the same hit.
        float amp = switch (boss.getAttackState()) {
            // the phase-one floor splitter, the heaviest thing he does with
            // the sword and the one the whole phase is built around
            case VelkharEntity.COMBO -> boss.getPhase() == 1 ? 0.75F : 0.0F;
            // The gate driven rim-first into the ground. This is now only the
            // BED of the effect - a faint tremble under the wave as it crosses
            // the room. The blow itself arrives as an impulse (see below), so
            // the level here is cut right down; at 0.8 the whole fifty-two
            // ticks shook and the actual landing had nothing left to stand out
            // against.
            case VelkharEntity.SHIELD_PULSE -> 0.14F;
            case VelkharEntity.SEISMIC_CLEAVE -> 0.85F;
            // the charge: a low rumble while he is actually moving, with the
            // launch and the connection arriving as impulses on top
            case VelkharEntity.SPIN_CHARGE -> 0.28F;
            case VelkharEntity.SLAM, VelkharEntity.FROST_NOVA -> 0.5F;
            case VelkharEntity.P2_TRANSITION, VelkharEntity.INTRO -> 0.7F;
            case VelkharEntity.P3_TRANSITION, VelkharEntity.WINTERS_COLLAPSE -> 0.9F;
            // The executioner's blade: the ground answers a falling mountain.
            case VelkharEntity.DOOM_BLADE -> 1.1F;
            case VelkharEntity.LAST_WINTER -> 0.4F;
            default -> 0.0F;
        };
        if (boss.isDeadOrDying()) {
            amp = 0.3F;
        }

        // THE BLOW, on top of whatever the state was already doing.
        //
        // Everything above is a LEVEL - it holds for as long as the attack
        // state does, which for the shield pulse is fifty-two ticks of the
        // screen wobbling. That is rumble, and rumble is what a machine in the
        // next room does. What a blow does is hit once and stop, and this is
        // that: the entity bumps a counter when something actually lands, the
        // client notices the number changed, and the shake decays from full to
        // nothing over eight ticks by itself.
        //
        // The two add, so an attack can have both - a low rumble through the
        // wind-up and a crack at the moment of contact.
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        int packed = boss.impactPacked();
        if (packed != lastImpact) {
            lastImpact = packed;
            impactAmp = (packed & 0xFF) / 255.0F;
            impactStamp = now;
        }
        if (impactAmp > 0.0F) {
            // Decay against the world clock, not a per-frame counter, so it
            // lasts the same wall time at 30 fps as at 240. Eight ticks from
            // full to nothing, squared, so it cracks and lets go rather than
            // sagging.
            float since = (now - impactStamp) + (float) event.getPartialTick();
            if (since >= IMPACT_TICKS) {
                impactAmp = 0.0F;
            } else {
                float k = 1.0F - since / IMPACT_TICKS;
                amp += impactAmp * k * k * 1.9F;
            }
        }

        double dist = Math.sqrt(boss.distanceToSqr(mc.player));
        float falloff = (float) Mth.clamp(1.0D - dist / 40.0D, 0.0D, 1.0D);
        float shake = amp > 0.0F ? amp * falloff : 0.0F;
        shake += golemShake;
        if (shake <= 0.0F) {
            return;
        }
        applyShake(event, mc, shake);
    }

    /** One place that actually moves the camera, so two sources cannot each
     *  invent their own waveform and beat against one another. */
    private static void applyShake(ViewportEvent.ComputeCameraAngles event,
                                   Minecraft mc, float shake) {
        double time = (mc.level != null ? mc.level.getGameTime() : 0) + event.getPartialTick();
        event.setPitch((float) (event.getPitch() + Math.sin(time * 3.1D) * shake));
        event.setYaw((float) (event.getYaw() + Math.cos(time * 2.7D) * shake));
        event.setRoll((float) (event.getRoll() + Math.sin(time * 3.7D) * shake * 0.6D));
    }

    /** True while a cutscene owns the camera. */
    static boolean cutsceneActive() {
        // !isRemoved rather than isAlive, so the DEATH cutscene (scene five)
        // still owns the camera while he comes apart - he is dying, not alive,
        // for the whole of it, and only vanishes when the animation ends.
        return (activeBoss != null && !activeBoss.isRemoved() && activeBoss.cutscene() != 0) || prisonScene
                || com.jastkub.frozenfortress.client.BossScenes.active();
    }

    // ================================================================
    // THE ICE MONSTROSITY WAKES
    // ================================================================
    /** The prison's awakening on screen: the statue shaking, then what was in it, roaring. */
    private static boolean prisonScene;
    private static int prisonSceneTicks;
    /** The scene has had the creature on screen (it ends when its roar does), and ticks since the ice broke. */
    private static boolean prisonSawIt;
    private static int prisonAfterIce;
    @Nullable
    private static net.minecraft.world.phys.Vec3 prisonSubject;
    /** Hearts whose awakening this client has shown - each is shown once. */
    private static final java.util.Set<net.minecraft.core.BlockPos> prisonShown = new java.util.HashSet<>();

    /**
     * Starts when a heart within reach begins to wake (its statue shakes for
     * three and a half seconds - the camera on the statue), follows the
     * Monstrosity once the ice breaks, and ends when its greeting roar does;
     * never longer than nine seconds, whatever happens.
     */
    private static void tickPrisonScene(Minecraft mc) {
        if (!prisonScene) {
            for (com.jastkub.frozenfortress.block.entity.PrisonHeartBlockEntity h
                    : com.jastkub.frozenfortress.block.entity.PrisonHeartBlockEntity.CLIENT) {
                if (!h.isRemoved() && h.getLevel() == mc.level && h.isWaking()
                        && h.statueCentre().distanceToSqr(mc.player.position()) < 48.0D * 48.0D
                        && prisonShown.add(h.getBlockPos().immutable())) {
                    prisonScene = true;
                    prisonSceneTicks = 0;
                    prisonSawIt = false;
                    prisonAfterIce = 0;
                    prisonSubject = h.statueCentre();
                    break;
                }
            }
            return;
        }
        prisonSceneTicks++;
        com.jastkub.frozenfortress.entity.HollowGolemEntity g = activeGolem;
        boolean waking = false;
        for (com.jastkub.frozenfortress.block.entity.PrisonHeartBlockEntity h
                : com.jastkub.frozenfortress.block.entity.PrisonHeartBlockEntity.CLIENT) {
            if (!h.isRemoved() && h.getLevel() == mc.level && h.isWaking()) {
                waking = true;
            }
        }
        if (!waking && g != null && g.isAlive()) {
            prisonSubject = g.position().add(0.0D, g.getBbHeight() * 0.62D, 0.0D);
            prisonSawIt = true;
            // OUT OF THE ICE, A FILM: the prison's own shot
            // (the player's eye turned on the shaking statue) hands over to BossScenes - its cuts round her, and her
            // roar the film's own sound, not turned down under the scene as the world's are
            if (g.isArriving() && !com.jastkub.frozenfortress.client.BossScenes.active()) {
                com.jastkub.frozenfortress.client.BossScenes.start(g.getId(), (byte) 1, 150,
                        "frozen_dominion:ice_monstrosity", g.getX(), g.getY(), g.getZ(), g.yBodyRot);
                prisonSawIt = false;
                prisonScene = false;
                prisonSubject = null;
                return;
            }
        }
        prisonAfterIce = waking ? 0 : prisonAfterIce + 1;
        // the ice
        // breaks a tick or two before this client has the creature - and "no creature" read as "it has finished". It
        // waits for it now, and for its roar; only if none comes in two seconds does it let go
        boolean over = !waking && ((prisonSawIt && (g == null || !g.isAlive() || !g.isArriving()))
                || (!prisonSawIt && prisonAfterIce > 40)) && prisonSceneTicks > 40;
        if (over || prisonSceneTicks > 240 || mc.player.isDeadOrDying()) {
            prisonSawIt = false;
            prisonScene = false;
            prisonSubject = null;
        }
    }

    /**
     * THE SHOT CLOSES ON ITS SUBJECT. The push-in is measured, not fixed: the field of view
     * narrows until the subject fills about three fifths of the frame's height, whatever the
     * distance - a long shot zooms hard, a close one hardly at all, so a head is never cut off -
     * eased in over the first second of the scene.
     */
    @SubscribeEvent
    public static void onCutsceneFov(net.minecraftforge.client.event.ComputeFovModifierEvent event) {
        if (!cutsceneActive()) {
            return;
        }
        if (com.jastkub.frozenfortress.client.BossScenes.films() && (activeBoss == null || activeBoss.cutscene() == 0)) {
            return;                                     // a keeper's film sets its own lens (BossScenes.onFov)
        }
        if (com.jastkub.frozenfortress.client.VelkharScenes.active()) {
            return;                                     // the king's shots stand where they frame him: no push-in on top
        }
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.phys.Vec3 centre = null;
        double height = 4.0D;
        if (com.jastkub.frozenfortress.client.BossScenes.active() && com.jastkub.frozenfortress.client.BossScenes.subject() != null && (activeBoss == null || activeBoss.cutscene() == 0)) {
            centre = com.jastkub.frozenfortress.client.BossScenes.subject();
            height = com.jastkub.frozenfortress.client.BossScenes.height();
        } else if (prisonScene && prisonSubject != null && (activeBoss == null || activeBoss.cutscene() == 0)) {
            centre = prisonSubject;
            height = activeGolem != null && activeGolem.isAlive() ? activeGolem.getBbHeight() : 9.0D;
        } else if (activeBoss != null) {
            Entity subject = activeBoss.cutscene() == 6 && activeGolem != null && activeGolem.isAlive()
                    ? activeGolem : activeBoss;
            centre = subject.position().add(0.0D, subject.getBbHeight() * 0.62D, 0.0D);
            height = subject.getBbHeight();
        }
        float zoom = 0.86F;
        if (centre != null && mc.player != null) {
            double dist = Math.max(1.0D, centre.distanceTo(mc.player.getEyePosition()));
            double want = Math.toDegrees(2.0D * Math.atan(height * 0.5D / dist)) / 0.6D;
            double base = mc.options.fov().get();
            zoom = (float) Mth.clamp(want / base, 0.42D, 0.95D);
        }
        float k = Mth.clamp(cutsceneTicks / 20.0F, 0.0F, 1.0F);
        k = k * k * (3.0F - 2.0F * k);
        event.setNewFovModifier(event.getNewFovModifier() * Mth.lerp(k, 1.0F, zoom));
    }

    /** The controls are frozen for the length of the cutscene. */
    @SubscribeEvent
    public static void onCutsceneInput(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        if (!cutsceneActive()) {
            return;
        }
        net.minecraft.client.player.Input in = event.getInput();
        in.forwardImpulse = 0.0F;
        in.leftImpulse = 0.0F;
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    /** The letterbox and the title, drawn over everything else. */
    @SubscribeEvent
    public static void onCutsceneOverlay(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!cutsceneActive() || mc.player == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        // the bars ease in over the first half-second and out is instant when
        // the cutscene ends (the whole overlay simply stops drawing)
        float frac = Mth.clamp(cutsceneTicks / 10.0F, 0.0F, 1.0F);
        int bar = (int) (Math.max(20, h / 7) * frac);
        if (bar <= 0) {
            return;
        }
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        String title = activeBoss != null && activeBoss.cutscene() != 0 ? cutsceneTitle(activeBoss.cutscene())
                : com.jastkub.frozenfortress.client.BossScenes.active() ? null : Component.translatable("frozen_dominion.cutscene.monstrosity_wakes").getString();
        if (title != null && bar > 8) {
            int tw = mc.font.width(title);
            int alpha = (int) (Mth.clamp((cutsceneTicks - 4) / 12.0F, 0.0F, 1.0F) * 255.0F);
            if (alpha > 8) {
                int col = (alpha << 24) | 0xDCEEFF;
                g.drawString(mc.font, title, w / 2 - tw / 2, h - bar / 2 - 4, col, true);
            }
        }
        drawCaption(g, mc, w, h, bar, event.getPartialTick());
        com.jastkub.frozenfortress.client.BossScenes.drawCard(g, mc, w, h, bar, event.getPartialTick());
    }

    /** How long the sentence fades up and back down, in ticks. */
    private static final int CAPTION_FADE = 10;

    /**
     * THE SENTENCE, across the lower third.
     *
     * <p>Above the bottom letterbox bar rather than inside it, because the bar
     * already carries the scene's title and two lines of text in one black
     * strip is a credits roll. It is drawn twice - once offset by a pixel in
     * near-black, once in pale blue - which is the cheapest legible drop shadow
     * there is and matters here because this text lands over a lit ice room.
     *
     * <p>Wrapped by the font rather than by hand: these are recorded lines and
     * they will be edited, so hard-coding where "blood" falls would break the
     * moment the wording changed.
     */
    private static void drawCaption(GuiGraphics g, Minecraft mc, int w, int h,
                                    int bar, float partial) {
        if (cutLineId <= 0 || cutLineTicks <= 0) {
            return;
        }
        int total = VelkharEntity.cutLineTicks(cutLineId) + CAPTION_FADE * 2;
        float elapsed = total - (cutLineTicks - partial);
        float in = Mth.clamp(elapsed / CAPTION_FADE, 0.0F, 1.0F);
        float out = Mth.clamp((cutLineTicks - partial) / CAPTION_FADE, 0.0F, 1.0F);
        int alpha = (int) (Math.min(in, out) * 255.0F);
        if (alpha <= 6) {
            return;
        }
        String key = switch (cutLineId) {
            case VelkharEntity.CUT_THRONE -> "velkhar.cut.throne";
            case VelkharEntity.CUT_PHASE2 -> "velkhar.cut.phase2";
            case VelkharEntity.CUT_SPLIT -> "velkhar.cut.split";
            case VelkharEntity.CUT_PHASE3 -> "velkhar.cut.phase3";
            case VelkharEntity.CUT_GOLEM -> "velkhar.cut.golem";
            case VelkharEntity.CUT_DEATH -> "velkhar.cut.death";
            case VelkharEntity.CUT_THRONE_GATE -> "velkhar.line.gate";
            case VelkharEntity.CUT_THRONE_FORTRESS -> "velkhar.line.fortress";
            default -> null;
        };
        if (key == null) {
            return;
        }
        Component line = Component.translatable(key);
        int wrapAt = Math.min(w - 60, 340);
        var rows = mc.font.split(line, wrapAt);
        int lh = mc.font.lineHeight + 3;
        int y = h - bar - 14 - (rows.size() - 1) * lh;
        for (var row : rows) {
            int rw = mc.font.width(row);
            int x = w / 2 - rw / 2;
            g.drawString(mc.font, row, x + 1, y + 1, (alpha << 24), false);
            g.drawString(mc.font, row, x, y, (alpha << 24) | 0xE8F6FF, false);
            y += lh;
        }
    }

    @Nullable
    private static String cutsceneTitle(int scene) {
        return switch (scene) {
            case 1 -> Component.translatable("velkhar.cutscene.awaken").getString();
            case 2 -> Component.translatable("velkhar.cutscene.phase2").getString();
            case 3 -> Component.translatable("velkhar.cutscene.tear").getString();
            case 4 -> Component.translatable("velkhar.cutscene.mage").getString();
            case 5 -> Component.translatable("velkhar.cutscene.death").getString();
            case 6 -> Component.translatable("velkhar.cutscene.summon").getString();
            default -> null;
        };
    }
}
