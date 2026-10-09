package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.StormcrownBeaconBlockEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * THE CITADEL'S CHILL (08.10.2026): the keep should feel cold to be in, not only look it. While the Stormcrown still
 * burns and you are in its reach (the same test as CitadelMist: StormcrownBeaconBlockEntity.clientSeenAt / RADIUS /
 * clientSeenTaken - its Chill lifts with it), and not a spectator:
 *
 * <ul>
 *   <li>THE HALLS' OWN SOUND, quiet, never every tick: now and then the wind howls somewhere through the halls and
 *       passes by (ambient.fortress.wind, every 26-46 s), a low draft rises and falls as it goes past you (the elytra's
 *       rush, pitched down to half, every 8-20 s), and in between the ice settles somewhere up the walls - a crystalline
 *       tick or two - or snow sifts off a ledge above (every 3.5-11 s). Each sound sits at a point a few blocks off
 *       round you, so it comes from somewhere. No ice-crack here: that sound is the traps' warning (icicles, ambushes)
 *       and must stay one. The vanilla sounds picked carry no subtitle of their own.</li>
 *   <li>YOUR BREATH: a small puff of vapour in front of your face every 2.75-4.25 s, every 1.1-1.7 s and fuller when
 *       you sprint - a few soft frost-swirl motes that swell and fade in about a second. Ambience, so particles are
 *       allowed here (the rule against particles is for attacks).</li>
 *   <li>FROST AT THE EDGE OF THE EYE: stand still a while, or linger in the dark, and frost creeps in at the four
 *       corners of the screen, each corner at its own pace (textures/misc/citadel_frost_edges.png, four corner pieces,
 *       tools/gen_citadel_frost.py). It thaws when you move on, and next to a lamp; with the Hearth Amulet's coal at
 *       your throat (no Chill on you) it never gets past ~60%. At most 0.35 alpha, drawn just over the vignette - under
 *       the hotbar, the bars and the chat. F1 hides it.</li>
 *   <li>THE DARK IS COLD: in a dark room the fog's colour goes a little towards a deep cold blue.</li>
 * </ul>
 *
 * It all comes in over ~4 s and goes over ~6 s, and it hushes at once for a boss's scene (BossScenes, Velkhar's own -
 * ClientEvents.cutsceneActive) and inside Velkhar's whiteout, which has its own frost and its own howl
 * (WhiteoutClient). The trap rooms' cold (ColdOverlay) draws over this, unchanged.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class CitadelChill {

    private static final RandomSource RAND = RandomSource.create();

    // ------------------------------------------------------------------------------------------------ presence
    /** In the crown's reach: comes in over ~4 s, goes over ~6 s. */
    private static final float RISE = 0.0125F, FALL = 0.008F;
    /** Hushed by a scene or the whiteout in half a second; back over two once it is over. */
    private static final float HUSH = 0.1F, UNHUSH = 0.025F;

    private static float strength;
    private static float lastStrength;
    private static float allowed = 1.0F;
    private static float lastAllowed = 1.0F;

    // ------------------------------------------------------------------------------------------------ frost
    private static final ResourceLocation FROST = FrozenFortress.id("textures/misc/citadel_frost_edges.png");
    private static final int SHEET = 1024, PIECE = 512;
    private static final float MAX_ALPHA = 0.35F;
    /** A corner piece's side at full frost, as a part of the screen's height; grown from 0.6 of that to all of it. */
    private static final float CORNER = 0.58F;
    /** How far behind the others each corner creeps in: top-left, top-right, bottom-left, bottom-right. */
    private static final float[] LAG = {0.0F, 0.10F, 0.05F, 0.16F};
    /** Standing still: it starts creeping after 4 s and has all it will get after 20 s more. */
    private static final int STILL_FROM = 80, STILL_FULL = 400;
    /** In the dark: after 3 s, all of it after 15 s more. */
    private static final int DARK_FROM = 60, DARK_FULL = 300;
    /** It creeps in slowly (~12 s to two-thirds) and thaws fast (~2.5 s). */
    private static final float CREEP = 0.004F, THAW = 0.02F;
    /** Without the Chill on you (the Hearth Amulet keeps it off) the frost gets only this far. */
    private static final float WARDED = 0.6F;

    private static float frost;
    private static float lastFrost;
    private static int stillTicks;
    private static int darkTicks;
    /** How dark it is at your eyes, 0-1, eased (the eye takes a moment). */
    private static float dark;
    private static float lastDark;

    // ------------------------------------------------------------------------------------------------ the dark is cold
    private static final float TINT = 0.28F;
    private static final float DARK_RED = 0.07F, DARK_GREEN = 0.10F, DARK_BLUE = 0.16F;

    // ------------------------------------------------------------------------------------------------ breath
    private static final ResourceLocation BREATH_SPRITE = FrozenFortress.id("frost_swirl_0");
    private static int toBreath;

    // ------------------------------------------------------------------------------------------------ sound
    private static int toHowl;
    private static int toDraft;
    private static int toDetail;
    @Nullable
    private static Drift howl;
    @Nullable
    private static Drift draft;

    private CitadelChill() {
    }

    /** How much of the chill is on you now, 0-1. */
    private static float presence() {
        return strength * allowed;
    }

    private static float presence(float partialTick) {
        return Mth.lerp(partialTick, lastStrength, strength) * Mth.lerp(partialTick, lastAllowed, allowed);
    }

    private static float smooth(float x) {
        float t = Mth.clamp(x, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    // ================================================================================================ tick
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;                                           // (all of it holds while the game is paused)
        }
        LocalPlayer p = mc.player;
        lastStrength = strength;
        lastAllowed = allowed;
        lastFrost = frost;
        lastDark = dark;
        boolean in = p != null && mc.level != null && p.isAlive() && inReach(p);
        strength = in ? Math.min(1.0F, strength + RISE) : Math.max(0.0F, strength - FALL);
        boolean hush = ClientEvents.cutsceneActive() || WhiteoutClient.strength(1.0F) > 0.02F;
        allowed = hush ? Math.max(0.0F, allowed - HUSH) : Math.min(1.0F, allowed + UNHUSH);
        if (p == null || mc.level == null || strength <= 0.0F) {
            frost = 0.0F;
            dark = 0.0F;
            stillTicks = 0;
            darkTicks = 0;
            prime();
            return;
        }
        tickFrost(mc.level, p, hush);
        float present = presence();
        if (hush || present < 0.3F) {
            return;
        }
        tickBreath(mc, p, present);
        tickSounds(mc, p, present);
    }

    /** Is `p` in the reach of a Stormcrown that still burns (the one last seen, in this world)? As CitadelMist. */
    private static boolean inReach(LocalPlayer p) {
        BlockPos at = StormcrownBeaconBlockEntity.clientSeenAt;
        if (at == null || StormcrownBeaconBlockEntity.clientSeenTaken
                || p.level().dimension() != StormcrownBeaconBlockEntity.clientSeenIn || p.isSpectator()) {
            return false;
        }
        int r = StormcrownBeaconBlockEntity.RADIUS;
        return Math.abs(p.getX() - at.getX() - 0.5D) <= r && Math.abs(p.getY() - at.getY()) <= r
                && Math.abs(p.getZ() - at.getZ() - 0.5D) <= r;
    }

    /** Outside, the clocks are set for the next time in: the first sounds come a few seconds after you enter. */
    private static void prime() {
        toBreath = 40 + RAND.nextInt(40);
        toHowl = 100 + RAND.nextInt(200);
        toDraft = 120 + RAND.nextInt(160);
        toDetail = 60 + RAND.nextInt(100);
    }

    // ================================================================================================ frost
    private static void tickFrost(ClientLevel level, LocalPlayer p, boolean hush) {
        BlockPos eye = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
        int block = level.getBrightness(LightLayer.BLOCK, eye);
        float sky = level.getBrightness(LightLayer.SKY, eye) * level.getSkyDarken(1.0F);   // (0.2 of it at night)
        float light = Math.max(block, sky);
        float darkNow = Mth.clamp((8.0F - light) / 8.0F, 0.0F, 1.0F);
        dark += (darkNow - dark) * 0.03F;

        double moved = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) + 0.5D * Math.abs(p.getY() - p.yo);
        if (hush) {
            stillTicks = Math.max(0, stillTicks - 12);        // (standing through a scene is not standing in the cold)
        } else {
            stillTicks = moved < 0.01D ? Math.min(stillTicks + 1, 1200) : Math.max(0, stillTicks - 12);
        }
        darkTicks = darkNow > 0.5F ? Math.min(darkTicks + 1, 1200) : Math.max(0, darkTicks - 4);

        float still = smooth((stillTicks - STILL_FROM) / (float) STILL_FULL);
        float gloom = smooth((darkTicks - DARK_FROM) / (float) DARK_FULL) * dark;
        float warmth = Mth.clamp((block - 11) / 4.0F, 0.0F, 1.0F);          // right by a lamp
        float target = Mth.clamp(0.85F * still + 0.65F * gloom, 0.0F, 1.0F) * (1.0F - warmth);
        if (!p.hasEffect(FFEffects.CHILL.get())) {
            target *= WARDED;
        }
        target *= presence();
        frost += (target - frost) * (target > frost ? CREEP : THAW);
        if (frost < 0.001F) {
            frost = 0.0F;
        }
    }

    /** The frost, in the four corners: drawn just over the vignette, so the hotbar, the bars and the chat sit on it. */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.VIGNETTE.id())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || ClientEvents.cutsceneActive()) {
            return;
        }
        float pt = event.getPartialTick();
        float f = Mth.lerp(pt, lastFrost, frost) * presence(pt);
        if (f <= 0.01F) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        int side = Math.round(Math.min(h, w * 0.5F) * CORNER);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        for (int i = 0; i < 4; i++) {
            float e = smooth((f - LAG[i]) / (1.0F - LAG[i]));
            if (e <= 0.005F) {
                continue;
            }
            boolean right = (i & 1) != 0;
            boolean bottom = (i & 2) != 0;
            float grow = 0.6F + 0.4F * e;                     // it creeps out of the corner, not just fades in
            g.pose().pushPose();
            g.pose().translate(right ? (float) w : 0.0F, bottom ? (float) h : 0.0F, 0.0F);
            g.pose().scale(grow, grow, 1.0F);
            g.setColor(1.0F, 1.0F, 1.0F, MAX_ALPHA * e);
            g.blit(FROST, right ? -side : 0, bottom ? -side : 0, side, side,
                    right ? PIECE : 0.0F, bottom ? PIECE : 0.0F, PIECE, PIECE, SHEET, SHEET);
            g.pose().popPose();
        }
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    // ================================================================================================ the dark is cold
    /** After the mist's own tint (CitadelMist): in a dark room the fog goes a little towards a deep cold blue. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float pt = (float) event.getPartialTick();
        float k = TINT * Mth.lerp(pt, lastDark, dark) * presence(pt);
        if (k <= 0.005F) {
            return;
        }
        event.setRed(Mth.lerp(k, event.getRed(), DARK_RED));
        event.setGreen(Mth.lerp(k, event.getGreen(), DARK_GREEN));
        event.setBlue(Mth.lerp(k, event.getBlue(), DARK_BLUE));
    }

    // ================================================================================================ breath
    private static void tickBreath(Minecraft mc, LocalPlayer p, float present) {
        if (--toBreath > 0) {
            return;
        }
        boolean hard = p.isSprinting();
        toBreath = hard ? 22 + RAND.nextInt(13) : 55 + RAND.nextInt(31);
        ParticleStatus setting = mc.options.particles().get();
        if (setting == ParticleStatus.MINIMAL || p.isUnderWater() || p.isInvisible() || mc.level == null) {
            return;
        }
        AbstractTexture atlas = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        if (!(atlas instanceof TextureAtlas particles)) {
            return;
        }
        TextureAtlasSprite sprite = particles.getSprite(BREATH_SPRITE);
        // out of the mouth: a little below the eyes, ahead of the face - mostly level, half following the look
        Vec3 level = Vec3.directionFromRotation(0.0F, p.getYRot());
        Vec3 look = p.getViewVector(1.0F);
        Vec3 dir = level.scale(0.6D).add(look.scale(0.4D)).normalize();
        Vec3 mouth = p.getEyePosition().add(dir.scale(0.42D)).add(0.0D, -0.17D, 0.0D);
        Vec3 carry = p.getDeltaMovement();
        int puffs = setting == ParticleStatus.DECREASED ? 1 : hard ? 3 : 2;
        float alpha = (hard ? 0.42F : 0.32F) * present;
        for (int i = 0; i < puffs; i++) {
            double push = (hard ? 0.05D : 0.032D) * (0.8D + RAND.nextDouble() * 0.4D);
            Breath b = new Breath(mc.level,
                    mouth.x + (RAND.nextDouble() - 0.5D) * 0.06D,
                    mouth.y + (RAND.nextDouble() - 0.5D) * 0.04D,
                    mouth.z + (RAND.nextDouble() - 0.5D) * 0.06D,
                    sprite, alpha, (hard ? 18 : 24) + RAND.nextInt(10));
            b.setParticleSpeed(carry.x * 0.8D + dir.x * push + (RAND.nextDouble() - 0.5D) * 0.008D,
                    0.004D + dir.y * push * 0.5D,
                    carry.z * 0.8D + dir.z * push + (RAND.nextDouble() - 0.5D) * 0.008D);
            mc.particleEngine.add(b);
        }
    }

    /** A breath's vapour: a soft mote that swells and fades in about a second, drifting up a little. */
    private static final class Breath extends TextureSheetParticle {
        private final float peak;
        private final float size0;
        private final float size1;

        Breath(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, float peak, int life) {
            super(level, x, y, z);
            setSprite(sprite);
            this.lifetime = life;
            this.gravity = -0.01F;
            this.friction = 0.9F;
            this.hasPhysics = false;
            this.peak = peak;
            this.size0 = 0.03F + this.random.nextFloat() * 0.015F;
            this.size1 = this.size0 * (2.8F + this.random.nextFloat() * 0.8F);
            this.quadSize = this.size0;
            this.alpha = 0.0F;
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
            setColor(0.96F, 0.98F, 1.0F);
        }

        @Override
        public void tick() {
            super.tick();
            float t = Mth.clamp(this.age / (float) this.lifetime, 0.0F, 1.0F);
            this.quadSize = Mth.lerp(1.0F - (1.0F - t) * (1.0F - t), this.size0, this.size1);
            this.alpha = this.peak * Mth.clamp(t / 0.12F, 0.0F, 1.0F) * (1.0F - t);
            this.oRoll = this.roll;
            this.roll += 0.015F;
        }

        /** Never quite black: even in an unlit hall the breath catches what light there is. */
        @Override
        protected int getLightColor(float partialTick) {
            int packed = super.getLightColor(partialTick);
            return LightTexture.pack(Math.max(LightTexture.block(packed), 6), LightTexture.sky(packed));
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // ================================================================================================ sound
    private static void tickSounds(Minecraft mc, LocalPlayer p, float present) {
        boolean howling = howl != null && mc.getSoundManager().isActive(howl);
        // the wind, howling somewhere through the halls and passing by
        if (--toHowl <= 0) {
            toHowl = 520 + RAND.nextInt(400);
            if (!howling) {
                howl = drift(FFSounds.FORTRESS_WIND.get(), p, 9.0D, 14.0D, -1.0D, 3.0D,
                        0.20F + 0.08F * RAND.nextFloat(), 0.78F + 0.17F * RAND.nextFloat(),
                        170 + RAND.nextInt(90), 50, 70, 0.03D + 0.03D * RAND.nextDouble());
                mc.getSoundManager().play(howl);
                howling = true;
            }
        }
        // a low draft going past you
        if (--toDraft <= 0) {
            toDraft = 160 + RAND.nextInt(240);
            if (!howling && (draft == null || !mc.getSoundManager().isActive(draft))) {
                draft = drift(SoundEvents.ELYTRA_FLYING, p, 4.0D, 7.0D, -0.5D, 1.5D,
                        0.7F + 0.3F * RAND.nextFloat(), 0.5F + 0.08F * RAND.nextFloat(),
                        70 + RAND.nextInt(50), 30, 40, 0.08D + 0.04D * RAND.nextDouble());
                mc.getSoundManager().play(draft);
            }
        }
        // the ice settling up the walls, or snow sifting off a ledge
        if (--toDetail <= 0) {
            toDetail = 70 + RAND.nextInt(150);
            if (RAND.nextFloat() < 0.65F) {
                Vec3 at = around(p, 6.0D, 12.0D, 1.0D, 5.0D);
                int ticks = 1 + RAND.nextInt(3);
                float volume = (0.18F + 0.10F * RAND.nextFloat()) * present;
                int delay = 0;
                for (int i = 0; i < ticks; i++) {
                    tap(mc, SoundEvents.AMETHYST_CLUSTER_FALL, at, volume, 0.5F + 0.12F * RAND.nextFloat(), delay);
                    delay += 3 + RAND.nextInt(7);
                    volume *= 0.6F;
                    at = at.add(RAND.nextGaussian() * 0.6D, RAND.nextGaussian() * 0.3D, RAND.nextGaussian() * 0.6D);
                }
            } else {
                Vec3 at = around(p, 7.0D, 12.0D, 3.0D, 6.0D);
                int sifts = 2 + RAND.nextInt(3);
                float volume = (0.16F + 0.08F * RAND.nextFloat()) * present;
                int delay = 0;
                for (int i = 0; i < sifts; i++) {
                    tap(mc, SoundEvents.POWDER_SNOW_FALL, at, volume, 0.7F + 0.2F * RAND.nextFloat(), delay);
                    delay += 2 + RAND.nextInt(4);
                    volume *= 0.75F;
                    at = at.add(0.0D, -0.4D, 0.0D);                // falling
                }
            }
        }
    }

    /** A point `minD`-`maxD` blocks off round the player, `yLo`-`yHi` above their eyes. */
    private static Vec3 around(LocalPlayer p, double minD, double maxD, double yLo, double yHi) {
        double a = RAND.nextDouble() * Math.PI * 2.0D;
        double d = minD + RAND.nextDouble() * (maxD - minD);
        return new Vec3(p.getX() + Math.cos(a) * d, p.getEyeY() + yLo + RAND.nextDouble() * (yHi - yLo),
                p.getZ() + Math.sin(a) * d);
    }

    /** One short sound at `at`, as loud wherever you stand (it is placed only so it comes from somewhere). */
    private static void tap(Minecraft mc, SoundEvent sound, Vec3 at, float volume, float pitch, int delay) {
        SimpleSoundInstance s = new SimpleSoundInstance(sound.getLocation(), SoundSource.AMBIENT, volume, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false);
        if (delay > 0) {
            mc.getSoundManager().playDelayed(s, delay);
        } else {
            mc.getSoundManager().play(s);
        }
    }

    /** A sound that rises and falls at a point round the player, moving across their line to it as it plays. */
    private static Drift drift(SoundEvent sound, LocalPlayer p, double minD, double maxD, double yLo, double yHi,
                               float peak, float pitch, int life, int fadeIn, int fadeOut, double speed) {
        double a = RAND.nextDouble() * Math.PI * 2.0D;
        double d = minD + RAND.nextDouble() * (maxD - minD);
        double way = RAND.nextBoolean() ? 1.0D : -1.0D;
        return new Drift(sound, p.getX() + Math.cos(a) * d, p.getEyeY() + yLo + RAND.nextDouble() * (yHi - yLo),
                p.getZ() + Math.sin(a) * d, -Math.sin(a) * speed * way, Math.cos(a) * speed * way,
                peak, pitch, life, fadeIn, fadeOut);
    }

    /**
     * A passing sound: its own swell and fade over `life` ticks, under the chill's presence (so a scene or leaving
     * the reach takes it down at once), drifting as it plays. Not attenuated by distance - its loudness is set here,
     * its place only says where it comes from.
     */
    private static final class Drift extends AbstractTickableSoundInstance {
        private final double vx;
        private final double vz;
        private final float peak;
        private final int life;
        private final int fadeIn;
        private final int fadeOut;
        private int age;

        Drift(SoundEvent sound, double x, double y, double z, double vx, double vz, float peak, float pitch,
              int life, int fadeIn, int fadeOut) {
            super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vz = vz;
            this.peak = peak;
            this.pitch = pitch;
            this.life = life;
            this.fadeIn = fadeIn;
            this.fadeOut = fadeOut;
            this.looping = false;
            this.delay = 0;
            this.relative = false;
            this.attenuation = Attenuation.NONE;
            this.volume = 0.0F;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            this.age++;
            float gate = presence();
            if (this.age >= this.life || (gate <= 0.01F && this.age > 2)) {
                stop();
                return;
            }
            float env = Math.min(1.0F, this.age / (float) this.fadeIn)
                    * Math.min(1.0F, (this.life - this.age) / (float) this.fadeOut);
            this.volume = this.peak * smooth(env) * gate;
            this.x += this.vx;
            this.z += this.vz;
        }
    }
}
