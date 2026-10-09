package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.DrownedLadyEntity;
import com.jastkub.frozenfortress.entity.ForgeOverseerEntity;
import com.jastkub.frozenfortress.entity.IceAurochsEntity;
import com.jastkub.frozenfortress.entity.LamplighterEntity;
import com.jastkub.frozenfortress.entity.RimePriestessEntity;
import com.jastkub.frozenfortress.entity.ShadeShepherdEntity;
import com.jastkub.frozenfortress.entity.TurnkeyEntity;
import com.jastkub.frozenfortress.registry.FFSounds;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A boss's scene on this client (BossCutscenes sent it): its entrance or its death. The letterbox comes down
 * (ClientEvents), the controls freeze, and the card in the lower bar names it - on its entrance its name and what it
 * is called, on its death its name, FELLED, and a line for its grave. It cannot be skipped.
 *
 * <p>AN ENTRANCE IS A FILM. Each keeper has its shots ({@link Film}):
 * a wide establishing shot of its hall, a closer one on what it does (the Overseer's hammer on his bench, the Lady
 * coming up out of the ice, a leaf of the Priestess's book turned...), and the hero shot on its face for the card and
 * its words - each an eased move (a dolly, a slow orbit, a push-in by the lens), cut to its beats (the keeper's own
 * "intro" clip, whose ticks the keepers publish as INTRO_*). Between them a dip to black, a hard cut or a flash of
 * light; the film opens out of black and closes into it, and the game comes back up out of black after. The camera
 * really moves (Camera.setPosition, by reflection: no mixin), measured against the hall - a shot that would put it in
 * a wall is turned the other way or drawn in toward the keeper - and the player is seen standing in the hall. Its death
 * is a film as well; a boss with no film of its death keeps the held shot from the player's eyes (ClientEvents).
 *
 * <p>ITS VOICE: its line -
 * entity/[boss]_vo_intro or _vo_death - on the voice channel, not fading with distance, its words on the screen over
 * the lower bar while it is said; in a film on its hero shot. Nothing of the scene is turned down under it
 * (CutsceneDuck: the scene's own sounds, and the sounds from where the boss stands, are left as they are).
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class BossScenes {

    private static byte kind;
    private static int entityId;
    private static int ticks;
    private static int age;
    private static String path = "";
    @Nullable
    private static Vec3 subject;
    private static float height = 3.0F;
    /** Its line is said (once, at voiceAt()). */
    private static boolean said;
    private static final int VOICE_AT = 8;
    /** Where the boss stood as its scene began and which way it faced: the frame of its shots. */
    private static Vec3 anchor = Vec3.ZERO;
    private static float anchorYaw;
    /** Where it stands now (feet). */
    private static Vec3 feet = Vec3.ZERO;
    /** The level the scene began in: another, and it is over. */
    @Nullable
    private static Level sceneLevel;
    /** Its entrance's film (null: a death, or no camera to move). */
    @Nullable
    private static Film film;
    /** Each shot as framed in this hall: drawn in toward the keeper (fit), mirrored, turned (worked out once, -1 not
     *  yet). */
    private static float[] shotFit = new float[0];
    private static int[] shotSign = new int[0];
    private static float[] shotTurn = new float[0];
    /** Ticks left of the game coming back up out of black after a film. */
    private static int after;
    private static final int FADE_IN = 10, FADE_OUT = 10, AFTER = 12;

    private BossScenes() {
    }

    public static void start(int id, byte k, int t, String type, double x, double y, double z, float yaw) {
        Minecraft mc = Minecraft.getInstance();
        entityId = id;
        kind = k;
        ticks = t;
        age = 0;
        path = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        anchor = new Vec3(x, y, z);
        anchorYaw = yaw;
        feet = anchor;
        sceneLevel = mc.level;
        Entity e = mc.level != null ? mc.level.getEntity(id) : null;
        height = e != null ? e.getBbHeight() : 3.0F;
        film = !CAMERA_OK ? null : k == 1 ? Film.of(path) : Film.death(path);
        subject = e != null ? centre(e) : film != null ? anchor.add(0.0D, height * 0.62D, 0.0D) : null;
        said = false;
        after = 0;
        int n = film != null ? film.shots.length : 0;
        shotFit = new float[n];
        shotSign = new int[n];
        shotTurn = new float[n];
        java.util.Arrays.fill(shotFit, -1.0F);
        if (film != null) {
            flat(mc, FFSounds.CUTSCENE_WHOOSH.get().getLocation(), SoundSource.AMBIENT, 0.4F);
        }
    }

    /** The scene's own sounds: never turned down under it (CutsceneDuck). */
    private static final java.util.Set<SoundInstance> OWN =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /**
     * Is this sound the scene's own (CutsceneDuck leaves it as it is)? What the scene plays itself - and what the boss
     * does in it: a sound from where it stands (the clank of the Overseer's hammer, the Lady's ice breaking) is part
     * of the film, not of the world under it.
     */
    public static boolean own(SoundInstance s) {
        if (OWN.contains(s)) {
            return true;
        }
        if (!active() || s.isRelative() || s.getAttenuation() == SoundInstance.Attenuation.NONE) {
            return false;
        }
        double r = 2.5D + 0.5D * Math.min(height, 12.0F);
        double dx = s.getX() - feet.x, dy = s.getY() - (feet.y + height * 0.5D), dz = s.getZ() - feet.z;
        return dx * dx + dz * dz < r * r && Math.abs(dy) < r + height * 0.5D;
    }

    /** A sound heard as it is, wherever the camera is (the scene's own, like a film's). */
    private static void flat(Minecraft mc, ResourceLocation id, SoundSource source, float volume) {
        flat(mc, id, source, volume, 1.0F);
    }

    private static void flat(Minecraft mc, ResourceLocation id, SoundSource source, float volume, float pitch) {
        SimpleSoundInstance s = new SimpleSoundInstance(id, source, volume, pitch, SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true);
        OWN.add(s);
        mc.getSoundManager().play(s);
    }

    /** When its line is said: in a film, on its hero shot; else a moment in - but the Bone Lord says his ("run") once
     *  he has put his ushanka on and leaned to you, at the end of his assembling (animation.bone_lord.assemble). */
    private static int voiceAt() {
        if (film != null) {
            return film.voiceAt;
        }
        return kind == 1 && "bone_lord".equals(path) ? 94 : VOICE_AT;
    }

    /** When its card comes up: in a film, as its hero shot begins. */
    private static int cardAt() {
        return film != null ? film.cardAt : 8;
    }

    /** The line it says in this scene, if it has one ("say_intro" / "say_death"). */
    private static String sayKey() {
        return "cutscene.frozen_dominion." + path + (kind == 1 ? ".say_intro" : ".say_death");
    }

    public static boolean active() {
        return kind != 0;
    }

    /** An entrance shot as a film: this class moves the camera (ClientEvents keeps out of it). */
    public static boolean films() {
        return active() && film != null && CAMERA_OK;
    }

    @Nullable
    public static Vec3 subject() {
        return subject;
    }

    public static float height() {
        return height;
    }

    private static Vec3 centre(Entity e) {
        return e.position().add(0.0D, e.getBbHeight() * 0.62D, 0.0D);
    }

    public static void tick(Minecraft mc) {
        if (!active()) {
            if (after > 0) {
                after--;
            }
            return;
        }
        if (mc.level == null || mc.level != sceneLevel) {
            end(false);
            return;
        }
        age++;
        if (!said && age >= voiceAt() && voiceAt() >= 0) {
            said = true;
            if (net.minecraft.client.resources.language.I18n.exists(sayKey())) {
                flat(mc, ResourceLocation.fromNamespaceAndPath("frozen_dominion", "entity_" + path + (kind == 1 ? "_vo_intro" : "_vo_death")), SoundSource.VOICE, 1.0F);
            }
        }
        if (film != null) {
            for (int i = 1; i < film.shots.length; i++) {
                if (film.shots[i].in == Shot.DIP && age == film.shots[i].at - 3) {
                    flat(mc, FFSounds.CUTSCENE_WHOOSH.get().getLocation(), SoundSource.AMBIENT, 0.45F);
                }
            }
            if (age == film.cardAt) {
                flat(mc, FFSounds.CUTSCENE_TITLE.get().getLocation(), SoundSource.AMBIENT, 0.65F);
            }
            for (int b : film.beats) {
                if (age == b) {
                    flat(mc, FFSounds.COLD_HEARTBEAT.get().getLocation(), SoundSource.AMBIENT, 1.0F);
                }
            }
            for (Film.Cue c : film.cues) {
                if (age == c.at) {
                    flat(mc, c.sound.getLocation(), SoundSource.HOSTILE, c.volume, c.pitch);
                }
            }
        }
        Entity e = mc.level.getEntity(entityId);
        if (e != null) {                                  // (a dead one's body goes before its card does)
            subject = centre(e);
            height = e.getBbHeight();
            feet = e.position();
        }
        if (films() && mc.player != null) {
            faceIt(mc);                                   // his own body, seen in the shots, stands looking at it
        }
        // (the boss may reach this client a moment after its scene does: wait a second for it before giving up)
        if (age >= ticks || mc.player == null || mc.player.isDeadOrDying() || (subject == null && age > 20)) {
            end(film != null);
        }
    }

    private static void end(boolean fadeBack) {
        kind = 0;
        subject = null;
        film = null;
        after = fadeBack ? AFTER : 0;
    }

    /** The player turned to the boss: still in the wide shots, and looking at it when the game comes back. */
    private static void faceIt(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 d = feet.add(0.0D, height * 0.62D, 0.0D).subtract(eye);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        if (h < 0.5D) {
            return;
        }
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = Mth.clamp((float) (-Mth.atan2(d.y, h) * Mth.RAD_TO_DEG), -40.0F, 40.0F);
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setXRot(pitch);
        mc.player.xRotO = pitch;
        mc.player.setYHeadRot(yaw);
        mc.player.yHeadRotO = yaw;
    }

    // =================================================================================================== the camera
    @Nullable
    private static final Method SET_POSITION = method(Camera.class, "setPosition", Vec3.class);  // (1.21: Mojang names at run time)
    @Nullable
    private static final Field DETACHED = field(Camera.class, "detached");
    /** The camera can be moved (both handles found, and no call has failed). */
    private static boolean CAMERA_OK = SET_POSITION != null && DETACHED != null;

    @Nullable
    private static Method method(Class<?> c, String srg, Class<?>... args) {
        try {
            return ObfuscationReflectionHelper.findMethod(c, srg, args);
        } catch (RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("[scenes] no camera move ({}): the held shot instead", srg);
            return null;
        }
    }

    @Nullable
    private static Field field(Class<?> c, String srg) {
        try {
            return ObfuscationReflectionHelper.findField(c, srg);
        } catch (RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("[scenes] no camera move ({}): the held shot instead", srg);
            return null;
        }
    }

    /**
     * THE FILM'S CAMERA, this frame (ClientEvents.onCameraSetup asks first): where it is and where it looks, from the
     * shot the film is in. False when there is no film (a death, the king's scenes) or Velkhar's own scene has the
     * camera (`free` false).
     */
    public static boolean camera(ViewportEvent.ComputeCameraAngles event, boolean free) {
        Minecraft mc = Minecraft.getInstance();
        if (!free || !films() || mc.level == null || mc.player == null) {
            return false;
        }
        float t = age + (float) event.getPartialTick();
        int i = shotIndex(t);
        if (shotFit[i] < 0.0F) {
            frame(mc, i);
        }
        Shot s = film.shots[i];
        float u = ease(progress(i, t));
        Vec3 cam = camAt(s, u, i, shotFit[i]);
        Vec3 look = lookAt(s, u, i, (float) event.getPartialTick());
        try {
            SET_POSITION.invoke(event.getCamera(), cam);
            DETACHED.setBoolean(event.getCamera(), true);
            CutsceneCamera.place(cam);                    // (and again after setup: CameraSetupMixin)
        } catch (ReflectiveOperationException | RuntimeException e) {
            CAMERA_OK = false;                            // (the held shot from here on: ClientEvents)
            return false;
        }
        Vec3 d = look.subtract(cam);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-Mth.atan2(d.y, Math.max(1.0E-4D, h)) * Mth.RAD_TO_DEG);
        // a hand on the camera: a slow drift - and the blows of the scene, felt
        float sh = shake(t);
        event.setYaw(yaw + Mth.sin(t * 0.071F) * 0.35F + Mth.sin(t * 0.031F) * 0.25F + Mth.cos(t * 2.7F) * sh);
        event.setPitch(pitch + Mth.sin(t * 0.053F) * 0.25F + Mth.sin(t * 3.1F) * sh);
        event.setRoll(Mth.sin(t * 0.029F) * 0.4F + Mth.sin(t * 3.7F) * sh * 0.6F);
        return true;
    }

    /** The lens of the shot: its FOV, eased as the shot goes (a push-in by the lens), opened a little if the hall
     *  drew the camera in. */
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov() || !films()) {
            return;
        }
        float t = age + (float) event.getPartialTick();
        int i = shotIndex(t);
        Shot s = film.shots[i];
        float u = ease(progress(i, t));
        float fit = shotFit[i] < 0.0F ? 1.0F : shotFit[i];
        event.setFOV(Mth.clamp(Mth.lerp(u, s.fov0, s.fov1) / (0.6F + 0.4F * fit), 20.0F, 85.0F));
    }

    /** No hand and no block outline over a film. */
    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (films()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onOutline(RenderHighlightEvent.Block event) {
        if (films()) {
            event.setCanceled(true);
        }
    }

    /** After a film the game comes back up out of black (the film went into it). */
    @SubscribeEvent
    public static void onAfter(RenderGuiEvent.Post event) {
        if (active() || after <= 0) {
            return;
        }
        float a = Mth.clamp((after - event.getPartialTick().getGameTimeDeltaPartialTick(true)) / AFTER, 0.0F, 1.0F);
        GuiGraphics g = event.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), argb(a * a, 0x000000));
    }

    private static int shotIndex(float t) {
        int i = 0;
        while (i + 1 < film.shots.length && t >= film.shots[i + 1].at) {
            i++;
        }
        return i;
    }

    private static float progress(int i, float t) {
        float a = film.shots[i].at;
        float b = i + 1 < film.shots.length ? film.shots[i + 1].at : ticks;
        return Mth.clamp((t - a) / Math.max(1.0F, b - a), 0.0F, 1.0F);
    }

    private static float ease(float u) {
        return 0.5F - 0.5F * Mth.cos(u * Mth.PI);
    }

    private static Vec3 fwd() {
        float y = anchorYaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(y), 0.0D, Mth.cos(y));
    }

    /** His right hand's side (as the keepers' own right()). */
    private static Vec3 right() {
        float y = anchorYaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(y), 0.0D, -Mth.sin(y));
    }

    /** The point the camera turns round: the middle of the keeper as it stood (never in a wall). */
    private static Vec3 pivot() {
        return anchor.add(0.0D, Math.min(height * 0.55D, 4.5D), 0.0D);
    }

    /** Where shot `s` puts the camera at `u`, as framed in this hall (`fit` 1: as written; less: drawn in). */
    private static Vec3 camAt(Shot s, float u, int i, float fit) {
        float a = (Mth.lerp(u, s.a0, s.a1) * shotSign[i] + shotTurn[i]) * Mth.DEG_TO_RAD;
        double dist = Mth.lerp(u, s.d0, s.d1);
        Vec3 dir = fwd().scale(Mth.cos(a)).add(right().scale(Mth.sin(a)));
        Vec3 want = anchor.add(dir.scale(dist)).add(0.0D, Mth.lerp(u, s.h0, s.h1), 0.0D);
        Vec3 p = pivot();
        return p.add(want.subtract(p).scale(Math.max(0.0F, fit)));
    }

    /** What shot `s` looks at: a point of the keeper (before him, up, to his right) - where he is now. */
    private static Vec3 lookAt(Shot s, float u, int i, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level != null ? mc.level.getEntity(entityId) : null;
        Vec3 at = e != null ? e.getPosition(partial) : feet;
        return at.add(fwd().scale(Mth.lerp(u, s.f0, s.f1)))
                .add(right().scale(Mth.lerp(u, s.r0, s.r1) * shotSign[i]))
                .add(0.0D, Mth.lerp(u, s.u0, s.u1), 0.0D);
    }

    /** Ways a shot may be turned when the hall will not have it as written: mirrored, swung round. */
    private static final float[][] TRIES = {{1, 0}, {-1, 0}, {1, 30}, {1, -30}, {-1, 30}, {-1, -30}, {1, 65},
            {1, -65}, {1, 110}, {1, -110}};

    /**
     * THE SHOT FRAMED IN THIS HALL, once, as it begins: the line from the keeper's middle to where the shot wants the
     * camera - at its start, its middle and its end - must be clear; where a wall, a pillar or a vault is in the way,
     * the shot is mirrored or swung round, and failing that drawn in along that line toward him, short of the wall
     * (the lens opened a little for it). So no shot looks out of a wall, whatever the hall.
     */
    private static void frame(Minecraft mc, int i) {
        if (film.placed) {                                // (a place's shots stand as they were written and checked)
            shotSign[i] = 1;
            shotTurn[i] = 0.0F;
            shotFit[i] = 1.0F;
            return;
        }
        Shot s = film.shots[i];
        float best = -1.0F;
        int bs = 1;
        float bt = 0.0F;
        for (float[] tr : TRIES) {
            shotSign[i] = (int) tr[0];
            shotTurn[i] = tr[1];
            float f = 1.0F;
            for (float u : new float[]{0.0F, 0.5F, 1.0F}) {
                f = Math.min(f, clear(mc, camAt(s, u, i, 1.0F)));
            }
            if (f > best + 0.05F) {
                best = f;
                bs = shotSign[i];
                bt = shotTurn[i];
            }
            if (f >= 0.85F) {
                break;
            }
        }
        shotSign[i] = bs;
        shotTurn[i] = bt;
        shotFit[i] = Mth.clamp(best, 0.25F, 1.0F);
    }

    /** How much of the way from the keeper's middle to `to` is clear (1 all of it). */
    private static float clear(Minecraft mc, Vec3 to) {
        Vec3 from = pivot();
        double len = to.distanceTo(from);
        if (len < 1.0E-3D) {
            return 1.0F;
        }
        HitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                mc.player));
        if (hit.getType() == HitResult.Type.MISS) {
            return 1.0F;
        }
        return (float) Mth.clamp((hit.getLocation().distanceTo(from) - 0.5D) / len, 0.0D, 1.0D);
    }

    /** The blows of the scene, on the camera: each a jolt that dies away over nine ticks. */
    private static float shake(float t) {
        float sh = 0.0F;
        for (int k = 0; k < film.hits.length; k++) {
            float since = t - film.hits[k];
            if (since >= 0.0F && since < 9.0F) {
                float q = 1.0F - since / 9.0F;
                sh += film.amps[k] * q * q;
            }
        }
        return sh;
    }

    // =================================================================================================== on screen
    /** Black over the frame: out of it as the film opens, a dip at each DIP cut, into it as it closes. */
    private static float black(float t) {
        float b = 0.0F;
        if (t < FADE_IN) {
            float q = 1.0F - t / FADE_IN;
            b = q * q * (3.0F - 2.0F * q);
        }
        if (t > ticks - FADE_OUT) {
            float q = Mth.clamp((t - (ticks - FADE_OUT)) / FADE_OUT, 0.0F, 1.0F);
            b = Math.max(b, q * q * (3.0F - 2.0F * q));
        }
        for (int i = 1; i < film.shots.length; i++) {
            if (film.shots[i].in != Shot.DIP) {
                continue;
            }
            float c = film.shots[i].at;
            if (t >= c - 4.0F && t < c) {
                b = Math.max(b, (t - (c - 4.0F)) / 4.0F);
            } else if (t >= c && t < c + 5.0F) {
                b = Math.max(b, 1.0F - (t - c) / 5.0F);
            }
        }
        return b;
    }

    /** Light over the frame: the scene's flashes (a hammer's last blow, a lantern's shutters thrown open). */
    private static float white(float t) {
        float w = 0.0F;
        for (int k = 0; k < film.flashes.length; k++) {
            float since = t - film.flashes[k];
            float len = film.flashLen[k];
            if (since >= 0.0F && since < len) {
                float q = 1.0F - since / len;
                w = Math.max(w, film.flashAmp[k] * q * q);
            }
        }
        return w;
    }

    private static int argb(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0.0F, 1.0F) * 255.0F) << 24) | rgb;
    }

    private static final ResourceLocation VIGNETTE = ResourceLocation.parse("textures/misc/vignette.png");

    /** The edges of the frame darkened (the game's own vignette, deeper): a lens, not a window. */
    private static void vignette(GuiGraphics g, int w, int h, float k) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        g.setColor(k, k, k, 1.0F);
        g.blit(VIGNETTE, 0, 0, -90, 0.0F, 0.0F, w, h, w, h);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /**
     * Over the letterbox (ClientEvents draws the bars first): in a film its lens and its cuts - the vignette, the dips
     * to black, the flashes - then the card in the lower bar: the name large, under it its title (entrance) or FELLED
     * (death); on a death, its epitaph over the bar. The card fades in as the hero shot begins (a death's, after the
     * bars are down).
     */
    public static void drawCard(GuiGraphics g, Minecraft mc, int w, int h, int bar, float partial) {
        if (!active()) {
            return;
        }
        float t = age + partial;
        if (films()) {
            vignette(g, w, h, 0.6F);
            float wh = white(t);
            if (wh > 0.004F) {
                g.fill(0, 0, w, h, argb(wh, 0xEAF6FF));
            }
            float bl = black(t);
            if (bl > 0.004F) {
                g.fill(0, 0, w, h, argb(bl, 0x000000));
            }
        }
        int cardAt = cardAt();
        int alpha = (int) (Mth.clamp((t - cardAt) / 12.0F, 0.0F, 1.0F) * Mth.clamp((ticks - t) / 8.0F, 0.0F, 1.0F) * 255.0F);
        if (alpha <= 8 || bar < 14) {
            return;
        }
        String name = Component.translatable("entity.frozen_dominion." + path).getString().toUpperCase();
        String under = kind == 1 ? Component.translatable("cutscene.frozen_dominion." + path + ".epithet").getString()
                : net.minecraft.client.resources.language.I18n.exists("cutscene.frozen_dominion." + path + ".felled")
                ? Component.translatable("cutscene.frozen_dominion." + path + ".felled").getString()
                : Component.translatable("cutscene.frozen_dominion.felled").getString();
        // (in a film the name settles in from a little larger, as a title does)
        float big = 1.6F + (film != null ? 0.25F * (1.0F - Mth.clamp((t - cardAt) / 16.0F, 0.0F, 1.0F)) : 0.0F);
        int nameY = h - bar + Math.max(2, (bar - 26) / 2);
        g.pose().pushPose();
        g.pose().translate(w / 2.0F, nameY, 0.0F);
        g.pose().scale(big, big, 1.0F);
        int nw = mc.font.width(name);
        g.drawString(mc.font, name, -nw / 2, 0, (alpha << 24) | (kind == 1 ? 0xDCEEFF : 0xFFE2B0), true);
        g.pose().popPose();
        int uw = mc.font.width(under);
        g.drawString(mc.font, under, w / 2 - uw / 2, nameY + 16, (alpha << 24) | 0x9FB8CC, true);
        int lh = mc.font.lineHeight + 3;
        int top = h - bar - 14;
        if (kind == 2) {                                  // its grave's line, over the bar
            Component line = Component.translatable("cutscene.frozen_dominion." + path + ".epitaph");
            var rows = mc.font.split(line, Math.min(w - 60, 340));
            int y = h - bar - 14 - (rows.size() - 1) * lh;
            top = y - lh - 4;
            for (var row : rows) {
                int rw = mc.font.width(row);
                g.drawString(mc.font, row, w / 2 - rw / 2 + 1, y + 1, (alpha << 24), false);
                g.drawString(mc.font, row, w / 2 - rw / 2, y, (alpha << 24) | 0xE8F6FF, false);
                y += lh;
            }
        }
        if (voiceAt() >= 0 && age >= voiceAt() && net.minecraft.client.resources.language.I18n.exists(sayKey())) {
            // what it says, in its own words, over the bar (over the epitaph on a death)
            float said_ = Mth.clamp((t - voiceAt()) / 6.0F, 0.0F, 1.0F);
            int a2 = (int) (alpha * said_);
            if (a2 > 8) {
                Component line = Component.literal("„").append(Component.translatable(sayKey()))
                        .append("”").withStyle(net.minecraft.ChatFormatting.ITALIC);
                var rows = mc.font.split(line, Math.min(w - 60, 360));
                int y = top - (rows.size() - 1) * lh;
                for (var row : rows) {
                    int rw = mc.font.width(row);
                    g.drawString(mc.font, row, w / 2 - rw / 2 + 1, y + 1, (a2 << 24), false);
                    g.drawString(mc.font, row, w / 2 - rw / 2, y, (a2 << 24) | 0xBFE6FF, false);
                    y += lh;
                }
            }
        }
    }

    // =================================================================================================== the films
    /**
     * ONE SHOT: from the tick it starts (it runs to the next one's, the last to the scene's end), the camera moved
     * from one place to another round the keeper - its angle off his facing (degrees, + to his right; 0 is before him,
     * looking at his face), its distance and its height over his feet (blocks) - looking from one point of him to
     * another (before him, up, to his right, in blocks), the lens from one FOV to another; all eased in and out. `in`:
     * how it is cut to.
     */
    static final class Shot {
        static final byte FADE = 0, CUT = 1, DIP = 2;
        final int at;
        final byte in;
        float a0, d0, h0, a1, d1, h1;
        float f0, u0, r0, f1, u1, r1;
        float fov0 = 60.0F, fov1 = 60.0F;

        Shot(int at, byte in) {
            this.at = at;
            this.in = in;
        }

        Shot from(float a, float d, float h) {
            a0 = a1 = a;
            d0 = d1 = d;
            h0 = h1 = h;
            return this;
        }

        Shot to(float a, float d, float h) {
            a1 = a;
            d1 = d;
            h1 = h;
            return this;
        }

        Shot look(float f, float u, float r) {
            f0 = f1 = f;
            u0 = u1 = u;
            r0 = r1 = r;
            return this;
        }

        Shot lookTo(float f, float u, float r) {
            f1 = f;
            u1 = u;
            r1 = r;
            return this;
        }

        Shot fov(float a, float b) {
            fov0 = a;
            fov1 = b;
            return this;
        }
    }

    /**
     * A KEEPER'S ENTRANCE AS A FILM: its shots (the first opens out of black), when its card comes up and its line is
     * said (-1: none), the blows felt on the camera (ticks, strength in degrees) and the flashes of light (ticks,
     * strength, length). The ticks are its "intro" clip's (the keepers' INTRO_* - tools/gen_*.py), so a cut falls on
     * a blow and the card on the look up.
     */
    static final class Film {
        final Shot[] shots;
        final int cardAt, voiceAt;
        int[] hits = {};
        float[] amps = {};
        int[] flashes = {};
        float[] flashAmp = {};
        float[] flashLen = {};

        Film(int cardAt, int voiceAt, Shot... shots) {
            this.cardAt = cardAt;
            this.voiceAt = voiceAt;
            this.shots = shots;
        }

        Film hits(int[] at, float... amp) {
            hits = at;
            amps = amp;
            return this;
        }

        /** A Frost Heart's beats, heard (ticks) - the Shepherd's, the first met. */
        int[] beats = {};

        Film beats(int... at) {
            beats = at;
            return this;
        }

        /** Sounds of the film's own, heard whole wherever its camera is. */
        record Cue(int at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        }

        java.util.List<Cue> cues = new java.util.ArrayList<>();

        Film cue(int at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
            cues.add(new Cue(at, sound, volume, pitch));
            return this;
        }

        /** A place's film (BossCutscenes.placeScene): its shots stand as written - each checked against the citadel
         *  it is shot in (tools: scene_probe) - not mirrored or drawn in toward a keeper's middle, which has none. */
        boolean placed;

        Film placed() {
            placed = true;
            return this;
        }

        Film flash(int at, float amp, float len) {
            flashes = java.util.Arrays.copyOf(flashes, flashes.length + 1);
            flashAmp = java.util.Arrays.copyOf(flashAmp, flashAmp.length + 1);
            flashLen = java.util.Arrays.copyOf(flashLen, flashLen.length + 1);
            flashes[flashes.length - 1] = at;
            flashAmp[flashAmp.length - 1] = amp;
            flashLen[flashLen.length - 1] = len;
            return this;
        }

        @Nullable
        static Film of(String path) {
            return switch (path) {
                case "forge_overseer" -> overseer();
                case "turnkey" -> turnkey();
                case "drowned_lady" -> lady();
                case "rime_priestess" -> priestess();
                case "ice_aurochs" -> aurochs();
                case "shade_shepherd" -> shepherd();
                case "lamplighter" -> lamplighter();
                case "bone_lord" -> boneLord();
                case "ice_monstrosity" -> monstrosity();
                case "stormcrown_taken" -> stormTaken();
                case "rift" -> rift();
                default -> null;
            };
        }

        /**
         * THE STORMCROWN TAKEN: low by
         * the crown in the Storm's Sanctuary as it gives - the storm screams once, a flash, a blow; up the beam from its
         * foot; then out under the sky, by the altar where the beam leaves the court, looking up it into the storm as it
         * unwinds; and from far off and high, the whole vortex over the citadel coming apart. (Its frame faces south;
         * the anchor is the crown's block.)
         */
        private static Film stormTaken() {
            return new Film(160, -1,
                    new Shot(0, Shot.FADE).from(25, 5.0F, 1.2F).to(15, 4.5F, 1.0F).look(0, 2.6F, 0)
                            .lookTo(0, 2.4F, 0).fov(56, 50),
                    new Shot(36, Shot.CUT).from(-40, 4.0F, 0.5F).to(-55, 3.5F, 0.4F).look(0, 3.0F, 0)
                            .lookTo(0, 10.0F, 0).fov(60, 66),
                    new Shot(80, Shot.DIP).from(15, 8.0F, 60.0F).to(0, 8.0F, 61.0F).look(0, 70.0F, 0)
                            .lookTo(0, 185.0F, 0).fov(70, 76),
                    new Shot(150, Shot.CUT).from(120, 90.0F, 140.0F).to(135, 100.0F, 150.0F).look(0, 175.0F, 0)
                            .lookTo(0, 185.0F, 0).fov(64, 58))
                    .hits(new int[]{6, 9}, 2.6F, 1.2F).flash(6, 1.0F, 12.0F).placed();
        }

        /**
         * THE RIFT: from the Seal's ledge along the first run, west to its turn; over the ridge between the
         * second and the third, the camera sliding east with the way; then close on the heart in the far corner,
         * beating - the card on it. (Its frame faces north, from the heart; the shots stand clear of the ridges.)
         */
        private static Film rift() {
            return new Film(150, -1,
                    new Shot(0, Shot.FADE).from(-14.2F, 34.6F, 13.0F).to(-31.5F, 39.3F, 13.0F).look(33.5F, 8.0F, -27.5F)
                            .lookTo(33.5F, 7.0F, -36.5F).fov(66, 60),
                    new Shot(70, Shot.CUT).from(-58.1F, 35.9F, 13.0F).to(-18.9F, 20.1F, 13.0F).look(22.5F, 5.0F, -18.5F)
                            .lookTo(22.5F, 5.0F, -4.5F).fov(62, 58),
                    new Shot(140, Shot.CUT).from(-55.3F, 7.9F, 3.0F).to(-53.1F, 5.0F, 2.5F).look(0, 1.0F, 0)
                            .lookTo(0, 1.0F, 0).fov(48, 38))
                    .beats(150, 168, 186, 204, 222).placed();
        }

        /** A death shot as a film. */
        @Nullable
        static Film death(String path) {
            return switch (path) {
                case "ice_monstrosity" -> monstrosityDeath();
                case "turnkey" -> turnkeyDeath();
                case "rime_priestess" -> priestessDeath();
                case "drowned_lady" -> ladyDeath();
                case "forge_overseer" -> overseerDeath();
                case "ice_aurochs" -> aurochsDeath();
                case "shade_shepherd" -> shepherdDeath();
                case "lamplighter" -> lamplighterDeath();
                case "bone_lord" -> boneLordDeath();
                default -> null;
            };
        }

        // ---- THE MINIBOSSES' DEATHS: each cut to its death clip's beats (the keepers' DEATH_*, tools/gen_*.py), every
        // one of them DEATH_LAG later in the scene than in the clip (the controller blends into the clip first); the
        // card and their last words where the words fit what is happening (they are said as the card comes up)

        /** THE TURNKEY'S END: before him as the blow throws him back and he goes down on his knees (felt); low at his
         *  hip as the keys slip off the ring one by one and ring on the floor - "the locks... they open"; close on his
         *  mask lit by the lantern he holds up to it as its cold flame gutters out, and the keyhole goes dark; wide from
         *  his side as he goes over onto his face, the lantern rolling away. */
        private static Film turnkeyDeath() {
            int l = TurnkeyEntity.DEATH_LAG;
            int kn = TurnkeyEntity.DEATH_KNEEL + l, k0 = TurnkeyEntity.DEATH_KEYS[0] + l;
            int gu = TurnkeyEntity.DEATH_GUTTER + l, dr = TurnkeyEntity.DEATH_DROP + l, fa = TurnkeyEntity.DEATH_FALL + l;
            return new Film(k0 - 4, k0 - 2,
                    new Shot(0, Shot.FADE).from(28, 7.0F, 2.6F).to(18, 6.0F, 2.0F).look(0.3F, 2.0F, 0)
                            .lookTo(0.3F, 1.3F, 0).fov(58, 50),
                    new Shot(kn, Shot.CUT).from(78, 3.0F, 0.6F).to(64, 2.6F, 0.5F).look(0.1F, 0.6F, 0.55F)
                            .lookTo(0.1F, 0.25F, 0.6F).fov(48, 42),
                    new Shot(gu - 6, Shot.CUT).from(-22, 2.8F, 1.5F).to(-14, 2.4F, 1.4F).look(0.5F, 1.6F, -0.3F)
                            .lookTo(0.5F, 1.5F, -0.3F).fov(40, 34),
                    new Shot(dr + 2, Shot.CUT).from(-92, 6.5F, 2.0F).to(-82, 7.5F, 2.6F).look(0.9F, 0.7F, 0)
                            .lookTo(1.3F, 0.4F, 0).fov(54, 58))
                    .hits(new int[]{kn, fa}, 0.5F, 0.9F);
        }

        /** THE PRIESTESS'S END: up at her as the blow throws her up and she hangs in the air, shaking, the halo
         *  spinning; wider as she drops out of it onto her knees (felt); low before her as the book slips from her hand
         *  and falls shut on the stones - "the last page... is turned"; from behind her as her halo falls and shatters
         *  (its flash); wide from her side as she goes over on her back. */
        private static Film priestessDeath() {
            int l = RimePriestessEntity.DEATH_LAG;
            int dr = RimePriestessEntity.DEATH_DROP + l, kn = RimePriestessEntity.DEATH_KNEEL + l;
            int bk = RimePriestessEntity.DEATH_BOOK + l, ha = RimePriestessEntity.DEATH_HALO + l;
            int hb = ha + RimePriestessEntity.DEATH_HALO_FALL, fa = RimePriestessEntity.DEATH_FALL + l;
            return new Film(bk, bk + 2,
                    new Shot(0, Shot.FADE).from(20, 6.5F, 0.8F).to(12, 6.0F, 0.6F).look(0.0F, 3.0F, 0)
                            .lookTo(0.0F, 3.2F, 0).fov(56, 50),
                    new Shot(dr, Shot.CUT).from(-30, 7.0F, 3.0F).to(-24, 6.0F, 2.4F).look(0.2F, 2.2F, 0)
                            .lookTo(0.2F, 1.4F, 0).fov(54, 50),
                    new Shot(bk - 2, Shot.CUT).from(10, 3.4F, 0.5F).to(6, 3.0F, 0.45F).look(1.0F, 0.6F, -0.25F)
                            .lookTo(1.15F, 0.2F, -0.28F).fov(44, 38),
                    new Shot(ha + 2, Shot.CUT).from(150, 5.0F, 2.5F).to(140, 4.6F, 2.2F).look(-0.3F, 0.8F, 0)
                            .lookTo(-0.5F, 0.3F, 0).fov(48, 44),
                    new Shot(RimePriestessEntity.DEATH_TIP + l, Shot.CUT).from(95, 7.0F, 2.4F).to(85, 8.0F, 3.2F)
                            .look(-0.6F, 0.8F, 0).lookTo(-1.4F, 0.3F, 0).fov(54, 58))
                    .hits(new int[]{kn, fa}, 0.6F, 0.6F)
                    .flash(hb, 0.35F, 6.0F);
        }

        /** THE LADY'S END: before her as she shrieks and claws at the air; close as she reaches out to you - and the
         *  ice gives under her (felt, its flash); low at the ice as she sinks, her hair streaming up - "at last... the
         *  music stops"; high over the black water as the last of her hand goes under, her crown left floating. */
        private static Film ladyDeath() {
            int l = DrownedLadyEntity.DEATH_LAG;
            int re = DrownedLadyEntity.DEATH_REACH + l, cr = DrownedLadyEntity.DEATH_CRACK + l;
            int un = DrownedLadyEntity.DEATH_UNDER + l, go = DrownedLadyEntity.DEATH_GONE + l;
            return new Film(cr + 4, cr + 6,
                    new Shot(0, Shot.FADE).from(24, 7.0F, 2.2F).to(16, 6.0F, 1.8F).look(0.2F, 1.8F, 0)
                            .lookTo(0.2F, 1.9F, 0).fov(56, 50),
                    new Shot(re, Shot.CUT).from(6, 3.6F, 1.6F).to(4, 3.2F, 1.5F).look(0.4F, 1.7F, 0.2F)
                            .lookTo(0.4F, 1.5F, 0.2F).fov(42, 38),
                    new Shot(cr + 2, Shot.CUT).from(-48, 5.0F, 0.5F).to(-40, 4.4F, 0.4F).look(0.1F, 1.2F, 0)
                            .lookTo(0.1F, 0.2F, 0).fov(50, 46),
                    new Shot(un - 2, Shot.CUT).from(20, 3.5F, 4.5F).to(10, 3.0F, 4.2F).look(-0.3F, 0.2F, 0.1F)
                            .lookTo(-0.55F, 0.0F, 0).fov(46, 40))
                    .hits(new int[]{cr, cr + 4, go}, 0.9F, 0.4F, 0.3F)
                    .flash(cr, 0.2F, 5.0F);
        }

        /** THE OVERSEER'S END: wide as he reels, the tongs falling, and drives his hammer's head down on the stones
         *  (felt); low by the hammer, up at him on his knees over it as the cold fire flares one last time (its flash)
         *  - "the fire... goes cold"; close on his visor as it gutters out; wide from his side as he goes down on his
         *  face across his hammer (felt). */
        private static Film overseerDeath() {
            int l = ForgeOverseerEntity.DEATH_LAG;
            int pl = ForgeOverseerEntity.DEATH_PLANT + l, kn = ForgeOverseerEntity.DEATH_KNEEL + l;
            int fl = ForgeOverseerEntity.DEATH_FLARE + l, ou = ForgeOverseerEntity.DEATH_OUT + l;
            int sl = ForgeOverseerEntity.DEATH_SLIP + l, fa = ForgeOverseerEntity.DEATH_FALL + l;
            return new Film(fl - 2, fl + 4,
                    new Shot(0, Shot.FADE).from(30, 9.0F, 3.5F).to(22, 8.0F, 3.0F).look(0.6F, 2.2F, 0)
                            .lookTo(0.8F, 1.5F, 0).fov(58, 52),
                    new Shot(kn - 2, Shot.CUT).from(28, 4.0F, 0.4F).to(20, 3.6F, 0.5F).look(1.0F, 1.6F, 0.4F)
                            .lookTo(0.8F, 2.0F, 0.3F).fov(48, 42),
                    new Shot(ou - 12, Shot.CUT).from(-10, 4.0F, 2.4F).to(-6, 3.5F, 2.3F).look(0.6F, 2.2F, 0)
                            .lookTo(0.6F, 2.0F, 0).fov(40, 34),
                    new Shot(sl, Shot.CUT).from(-95, 8.0F, 2.5F).to(-85, 9.0F, 3.0F).look(1.2F, 0.9F, 0)
                            .lookTo(1.8F, 0.5F, 0).fov(54, 58))
                    .hits(new int[]{pl, kn, fl, fa}, 0.7F, 0.3F, 0.5F, 1.2F)
                    .flash(fl, 0.45F, 8.0F);
        }

        /** THE AUROCHS'S END: wide as the blow rears him and he scrapes the floor once more, as if to charge - and his
         *  forelegs buckle (felt); low under his head heaved up in a last bellow, felt; wide from his side as he rolls
         *  over (felt); high over him as the ice of him breaks apart (its flash). (No words of his.) */
        private static Film aurochsDeath() {
            int l = IceAurochsEntity.DEATH_LAG;
            int kn = IceAurochsEntity.DEATH_KNEEL + l, be = IceAurochsEntity.DEATH_BELLOW + l;
            int fa = IceAurochsEntity.DEATH_FALL + l, sh = IceAurochsEntity.DEATH_SHATTER + l;
            return new Film(sh + 4, -1,
                    new Shot(0, Shot.FADE).from(40, 9.0F, 3.0F).to(30, 8.0F, 2.6F).look(1.2F, 1.2F, 0)
                            .lookTo(1.4F, 0.9F, 0).fov(58, 52),
                    new Shot(be - 2, Shot.CUT).from(10, 6.0F, 0.5F).to(5, 5.4F, 0.6F).look(2.0F, 2.2F, 0)
                            .lookTo(2.0F, 2.6F, 0).fov(50, 46),
                    new Shot(fa - 4, Shot.CUT).from(-80, 8.5F, 2.0F).to(-70, 9.0F, 2.6F).look(0.6F, 0.8F, 0)
                            .lookTo(0.4F, 0.5F, 0).fov(54, 50),
                    new Shot(IceAurochsEntity.DEATH_EYES + l + 2, Shot.CUT).from(30, 10.0F, 6.0F).to(20, 12.0F, 7.0F)
                            .look(0.6F, 0.4F, -0.6F).fov(52, 58))
                    .hits(new int[]{kn, be, be + 4, be + 8, fa, sh}, 0.7F, 1.2F, 0.6F, 0.4F, 1.0F, 1.0F)
                    .flash(sh, 0.55F, 10.0F);
        }

        /** THE SHEPHERD'S END: before him as he reels and goes down on his knees over his crook; high and wide as his
         *  herd-lights are flung out from him and go out - "who will keep them... in the dark?"; low at the stones
         *  before him as his mask falls there, and his crook after it, its bell ringing; and before him as he sinks
         *  into the floor's dark, leaving them lying there. */
        private static Film shepherdDeath() {
            int l = ShadeShepherdEntity.DEATH_LAG;
            int kn = ShadeShepherdEntity.DEATH_KNEEL + l, mo = ShadeShepherdEntity.DEATH_MOTES + l;
            int ms = ShadeShepherdEntity.DEATH_MASK + l, sk = ShadeShepherdEntity.DEATH_SINK + l;
            int cl = ShadeShepherdEntity.DEATH_CROOK + ShadeShepherdEntity.DEATH_CROOK_FALL + l;
            return new Film(mo + 2, mo + 4,
                    new Shot(0, Shot.FADE).from(-26, 7.0F, 2.4F).to(-18, 6.0F, 2.0F).look(0.3F, 1.8F, 0)
                            .lookTo(0.3F, 1.2F, 0).fov(58, 50),
                    new Shot(mo, Shot.CUT).from(30, 8.0F, 5.0F).to(24, 7.5F, 4.4F).look(0, 1.6F, 0)
                            .lookTo(0, 1.2F, 0).fov(60, 56),
                    new Shot(ms + 6, Shot.CUT).from(25, 3.2F, 0.4F).to(15, 3.0F, 0.35F).look(1.0F, 0.2F, 0.1F)
                            .lookTo(1.1F, 0.3F, 0.3F).fov(46, 42),
                    new Shot(sk + 8, Shot.CUT).from(8, 6.0F, 2.0F).to(4, 7.0F, 2.6F).look(0.5F, 0.6F, 0)
                            .lookTo(0.8F, 0.1F, 0.1F).fov(50, 54))
                    .hits(new int[]{kn, cl}, 0.3F, 0.25F);
        }

        /** THE LAMPLIGHTER'S END (his death clip as it was, tools/gen_lamplighter.py: to his knees by 14, the pole
         *  falling before him to 32, the face guttering out by 54; three ticks of the controller's blend before it):
         *  before him as he goes down; low from his side as the pole falls - "the lamp... goes out"; close on the flame
         *  of his face as it gutters and goes out; high and wide over the dark dome. */
        private static Film lamplighterDeath() {
            int l = 3;
            return new Film(28, 30,
                    new Shot(0, Shot.FADE).from(24, 7.0F, 2.2F).to(16, 6.0F, 1.8F).look(0.3F, 2.0F, 0)
                            .lookTo(0.3F, 1.5F, 0).fov(56, 50),
                    new Shot(20 + l, Shot.CUT).from(-70, 6.0F, 0.5F).to(-60, 5.4F, 0.6F).look(1.4F, 0.6F, 0.3F)
                            .lookTo(1.6F, 0.4F, 0.3F).fov(52, 48),
                    new Shot(38 + l, Shot.CUT).from(6, 3.0F, 1.9F).to(3, 2.5F, 1.8F).look(0.4F, 2.0F, 0)
                            .lookTo(0.4F, 1.9F, 0).fov(40, 33),
                    new Shot(56 + l, Shot.DIP).from(30, 9.0F, 4.0F).to(22, 10.0F, 4.6F).look(0.6F, 0.8F, 0)
                            .fov(54, 58))
                    .hits(new int[]{14 + l, 32 + l}, 0.4F, 0.6F);
        }

        /** THE BONE LORD'S END (its death clip as it is, death_fall, and its body driven back and down by
         *  BoneLordEntity.tickDeath - over the edge on 18): before it as it staggers back to the edge; low on the bridge
         *  as it goes over backwards, screaming (felt) - "a king may fall... but never kneel"; and from over the chasm,
         *  looking down after it as it falls away into the dark, the lens closing on it. */
        private static Film boneLordDeath() {
            return new Film(16, 18,
                    new Shot(0, Shot.FADE).from(25, 14.0F, 6.0F).to(15, 12.0F, 5.0F).look(0, 4.0F, 0)
                            .lookTo(0, 3.6F, 0).fov(60, 54),
                    new Shot(18, Shot.CUT).from(-30, 9.0F, 1.0F).to(-40, 8.0F, 0.5F).look(-1.0F, 3.5F, 0)
                            .lookTo(-1.5F, 1.0F, 0).fov(56, 60),
                    new Shot(36, Shot.CUT).from(180, 2.5F, 8.0F).to(180, 2.0F, 9.0F).look(0, 0, 0)
                            .fov(50, 36))
                    .hits(new int[]{18}, 0.9F);
        }

        /**
         * THE ICE MONSTROSITY OUT OF HER PRISON: taken over from the prison's own shot (ClientEvents.tickPrisonScene) the moment she is out of the
         * ice - wide and high as she heaves herself up out of the broken floor; low under her as her fists come down on
         * it (EMERGE_PLANT); then before her maw, close, for the roar (her greeting, from tick 84) - felt, and heard over
         * everything (a sound from where she stands is the film's own). No words of hers.
         */
        private static Film monstrosity() {
            return new Film(88, -1,
                    new Shot(0, Shot.FADE).from(32, 17.0F, 8.0F).to(22, 15.0F, 7.0F).look(0.0F, 3.0F, 0)
                            .lookTo(0.0F, 5.0F, 0).fov(62, 56),
                    new Shot(44, Shot.DIP).from(-42, 9.0F, 1.2F).to(-30, 8.0F, 1.6F).look(1.5F, 4.5F, 0)
                            .lookTo(1.5F, 6.0F, 0).fov(56, 50),
                    new Shot(84, Shot.CUT).from(6, 10.0F, 5.0F).to(2, 8.5F, 5.5F).look(2.5F, 6.0F, 0)
                            .lookTo(2.5F, 6.4F, 0).fov(46, 38))
                    .hits(new int[]{66, 94, 100, 106}, 0.8F, 1.6F, 1.0F, 0.6F)
                    // HER BELLOW, whole wherever the camera is (it was hers alone, from where she stood - far off in a
                    // wide shot, and under the music)
                    .cue(94, FFSounds.GOLEM_ROAR.get(), 1.0F, 1.0F)
                    .cue(95, FFSounds.GOLEM_ROAR.get(), 0.8F, 0.85F);
        }

        /** HER END: wide as she staggers; low beside her as she goes down, cracking through; high over her as she
         *  shatters (her death clip: 124 ticks to the bursting), the flash of it. */
        private static Film monstrosityDeath() {
            return new Film(110, -1,
                    new Shot(0, Shot.FADE).from(28, 15.0F, 6.0F).to(16, 13.0F, 5.0F).look(0.0F, 4.0F, 0)
                            .lookTo(0.0F, 3.5F, 0).fov(60, 54),
                    new Shot(50, Shot.DIP).from(-62, 8.0F, 2.0F).to(-46, 7.0F, 2.5F).look(0.5F, 4.0F, 0)
                            .lookTo(0.5F, 2.8F, 0).fov(50, 44),
                    new Shot(100, Shot.CUT).from(12, 15.0F, 10.0F).to(6, 18.0F, 12.0F).look(0.0F, 2.0F, 0)
                            .fov(56, 64))
                    .hits(new int[]{124, 128}, 1.6F, 0.8F)
                    .flash(124, 0.5F, 10.0F);
        }

        /** THE OVERSEER AT WORK: the forge from high in its corner, he over his bench (the first blow); along the
         *  bench from its end (over the chain lying on it), the hammer coming down on the lump - twice, the last from
         *  over his head, its flash - cut to him from below as he looks up from the work and straightens, the hammer
         *  onto his shoulder. */
        private static Film overseer() {
            int[] b = ForgeOverseerEntity.INTRO_BLOWS;
            return new Film(64, 74,
                    new Shot(0, Shot.FADE).from(38, 10.0F, 5.0F).to(26, 8.5F, 4.2F).look(0.9F, 1.7F, 0)
                            .lookTo(1.0F, 1.9F, 0).fov(62, 55),
                    new Shot(30, Shot.DIP).from(64, 4.4F, 2.2F).to(50, 3.8F, 2.0F).look(1.9F, 1.35F, 0.15F)
                            .lookTo(1.8F, 1.45F, 0.1F).fov(50, 45),
                    new Shot(62, Shot.CUT).from(-14, 4.8F, 1.3F).to(-8, 3.6F, 1.5F).look(1.0F, 2.8F, 0)
                            .lookTo(0.4F, 3.2F, 0).fov(42, 35))
                    .hits(new int[]{b[0], b[1], b[2]}, 0.5F, 0.7F, 1.3F)
                    .flash(b[2], 0.35F, 6.0F);
        }

        /** THE TURNKEY: his hall, he asleep on his feet - startled awake; low at his hip, the ring of keys shaken
         *  three times; cut on a lock's clack to his mask lit by the lantern he holds by it, leaning in at you. */
        private static Film turnkey() {
            return new Film(64, 70,
                    new Shot(0, Shot.FADE).from(34, 10.0F, 5.0F).to(24, 8.5F, 4.2F).look(0.5F, 1.6F, 0)
                            .lookTo(0.6F, 2.0F, 0).fov(60, 54),
                    new Shot(30, Shot.DIP).from(58, 2.4F, 0.9F).to(46, 2.1F, 1.0F).look(0.3F, 1.1F, 0.45F)
                            .lookTo(0.3F, 1.15F, 0.42F).fov(50, 46),
                    new Shot(62, Shot.CUT).from(-18, 4.0F, 1.6F).to(-10, 3.1F, 1.85F).look(0.9F, 2.2F, -0.2F)
                            .lookTo(1.05F, 2.15F, -0.3F).fov(42, 35))
                    .hits(new int[]{TurnkeyEntity.INTRO_STIR, TurnkeyEntity.INTRO_LEAN - 4}, 0.25F, 0.35F);
        }

        /** THE DROWNED LADY: the black ice of the cistern from high above, groaning, cracking - her hands burst up
         *  through it; low at the ice, she draws herself up out of the water; her face as it comes up, a hand held
         *  out to you. */
        private static Film lady() {
            return new Film(64, 68,
                    new Shot(0, Shot.FADE).from(30, 11.0F, 6.5F).to(22, 9.5F, 5.5F).look(0.3F, 0.0F, 0)
                            .lookTo(0.3F, 0.6F, 0).fov(60, 54),
                    new Shot(30, Shot.DIP).from(80, 4.4F, 0.7F).to(62, 3.9F, 0.9F).look(0.4F, 0.6F, 0)
                            .lookTo(0.4F, 1.8F, 0).fov(48, 44),
                    new Shot(62, Shot.DIP).from(12, 3.1F, 1.5F).to(6, 2.5F, 1.75F).look(0.25F, 2.25F, 0)
                            .lookTo(0.15F, 2.35F, 0).fov(40, 34))
                    .hits(new int[]{DrownedLadyEntity.INTRO_BURST}, 0.8F)
                    .flash(DrownedLadyEntity.INTRO_BURST, 0.25F, 5.0F);
        }

        /** THE RIME PRIESTESS: the chapel down its nave, she at prayer before her altar; over her book as a leaf of it
         *  turns, and another; her face as the book is shut and she rises into the air, the halo flaring. */
        private static Film priestess() {
            return new Film(64, 76,
                    new Shot(0, Shot.FADE).from(20, 10.0F, 4.5F).to(12, 8.5F, 4.0F).look(0.5F, 1.5F, 0)
                            .lookTo(0.6F, 1.7F, 0).fov(60, 54),
                    new Shot(30, Shot.DIP).from(-48, 2.3F, 2.3F).to(-38, 2.0F, 2.25F).look(0.8F, 1.7F, -0.2F)
                            .lookTo(0.8F, 1.72F, -0.2F).fov(48, 44),
                    new Shot(62, Shot.DIP).from(10, 3.6F, 1.4F).to(4, 2.9F, 1.8F).look(0.4F, 2.45F, 0)
                            .lookTo(0.2F, 2.75F, 0).fov(42, 35))
                    .hits(new int[]{RimePriestessEntity.INTRO_SHUT + 2}, 0.25F)
                    .flash(RimePriestessEntity.INTRO_RISE + 6, 0.25F, 8.0F);
        }

        /** THE ICE AUROCHS: the Frost Heart hall, he asleep under his rime - heaving up, shaking it off; low by his
         *  forehooves as they scrape the floor, the snort of frost; cut to his horns levelled at you, the stamp - and
         *  cut on it, from below and wider, to the bellow, felt. (No words of his.) */
        private static Film aurochs() {
            int[] p = IceAurochsEntity.INTRO_PAWS;
            int be = IceAurochsEntity.INTRO_BELLOW;
            return new Film(68, -1,
                    new Shot(0, Shot.FADE).from(48, 11.0F, 5.5F).to(38, 9.5F, 4.5F).look(1.2F, 0.8F, 0)
                            .lookTo(1.2F, 1.4F, 0).fov(60, 54),
                    new Shot(34, Shot.DIP).from(62, 3.4F, 0.6F).to(50, 3.0F, 0.65F).look(1.0F, 0.5F, 0.4F)
                            .lookTo(1.1F, 0.7F, 0.35F).fov(50, 46),
                    new Shot(66, Shot.CUT).from(6, 6.4F, 0.9F).to(3, 5.8F, 1.0F).look(2.3F, 1.4F, 0)
                            .lookTo(2.3F, 1.35F, 0).fov(44, 40),
                    new Shot(be - 2, Shot.CUT).from(-25, 7.5F, 0.6F).to(-18, 6.8F, 0.8F).look(2.0F, 2.6F, 0)
                            .lookTo(2.1F, 2.4F, 0).fov(52, 48))
                    .hits(new int[]{p[0], p[1], IceAurochsEntity.INTRO_STAMP, be, be + 4, be + 8},
                            0.25F, 0.25F, 0.6F, 1.5F, 0.8F, 0.5F);
        }

        /** THE SHADE SHEPHERD: his dark chamber, he asleep on one knee over his crook - rising; the crook high, its
         *  bell tolled three times; his mask as a finger goes to it - hush - then, behind him, HIS HEART: over his altar, the lens closing on it as it beats twice, heard; and cut back to his hand held out:
         *  come. (The heart stands thirteen behind where he sleeps, three up - citadel6 RITUAL, frost_heart.) */
        private static Film shepherd() {
            int[] tl = ShadeShepherdEntity.INTRO_TOLLS;
            int bk = ShadeShepherdEntity.INTRO_BECKON;
            return new Film(64, 70,
                    new Shot(0, Shot.FADE).from(30, 10.0F, 4.5F).to(20, 8.5F, 4.0F).look(0.4F, 1.2F, 0)
                            .lookTo(0.4F, 1.7F, 0).fov(60, 54),
                    new Shot(30, Shot.DIP).from(40, 3.6F, 1.0F).to(30, 3.2F, 1.15F).look(0.5F, 2.25F, 0.15F)
                            .lookTo(0.5F, 2.3F, 0.15F).fov(50, 46),
                    new Shot(62, Shot.DIP).from(-12, 3.3F, 1.5F).to(-6, 2.7F, 1.8F).look(0.45F, 2.45F, 0)
                            .lookTo(0.5F, 2.5F, 0).fov(40, 34),
                    new Shot(bk - 18, Shot.CUT).from(180, 7.0F, 3.7F).to(180, 8.6F, 3.6F).look(-13.0F, 3.5F, 0)
                            .fov(48, 26),
                    new Shot(bk, Shot.CUT).from(8, 4.2F, 1.3F).to(4, 3.4F, 1.6F).look(0.6F, 2.0F, 0)
                            .lookTo(0.5F, 2.3F, 0.2F).fov(44, 38))
                    .hits(new int[]{tl[0], tl[1], tl[2]}, 0.15F, 0.15F, 0.2F)
                    .beats(bk - 15, bk - 7);
        }

        /** THE LAMPLIGHTER: the dome dark, he asleep over his pole, the flame of his face guttering - it catches;
         *  close by his face and the lantern he raises and levels, its shutters thrown open in a blast of light; cut
         *  to him from below as the pole's foot is struck down, his hand up at you. */
        private static Film lamplighter() {
            int sh = LamplighterEntity.INTRO_SHUTTERS;
            return new Film(64, 72,
                    new Shot(0, Shot.FADE).from(30, 10.0F, 4.5F).to(20, 8.5F, 4.0F).look(0.6F, 1.6F, 0)
                            .lookTo(0.5F, 2.0F, 0).fov(60, 54),
                    new Shot(24, Shot.DIP).from(-40, 3.2F, 2.3F).to(-32, 2.9F, 2.4F).look(1.1F, 2.9F, 0.25F)
                            .lookTo(1.2F, 3.0F, 0.3F).fov(52, 48),
                    new Shot(62, Shot.DIP).from(12, 4.0F, 1.0F).to(6, 3.2F, 1.3F).look(0.45F, 2.3F, 0)
                            .lookTo(0.4F, 2.55F, 0).fov(42, 35))
                    .hits(new int[]{LamplighterEntity.INTRO_STRIKE}, 0.9F)
                    .flash(sh, 0.7F, 10.0F);
        }

        /** THE BONE LORD (its assembling, animation.bone_lord.assemble, as it was): the chasm from high over it, the
         *  heap on its pillar pulling itself together; low under him as he sits up and kneels and puts his ushanka
         *  on; up at his skull as he stands and leans down to you - "run". */
        private static Film boneLord() {
            return new Film(82, 94,
                    new Shot(0, Shot.FADE).from(35, 24.0F, 13.0F).to(26, 21.0F, 11.0F).look(3.0F, 2.5F, -2.5F)
                            .lookTo(3.0F, 5.0F, -1.5F).fov(62, 56),
                    new Shot(36, Shot.DIP).from(50, 14.0F, 3.0F).to(38, 13.0F, 4.0F).look(3.5F, 7.0F, 0)
                            .lookTo(2.5F, 9.5F, 0).fov(55, 50),
                    new Shot(80, Shot.DIP).from(4, 13.0F, 8.0F).to(2, 10.5F, 8.0F).look(2.6F, 10.0F, 0)
                            .lookTo(3.6F, 9.2F, 0).fov(46, 38))
                    // ITS SOUNDS, on the assembling clip's beats (tools/gen_bone_lord.py ASSEMBLE_*, three ticks
                    // later: the blend into it): the heap stirring and its bones dragging together, the jaw knocked
                    // shut, sitting up, a knee on the bridge, the ushanka taken off the heap and set on, the creak of
                    // him rising, and his feet planted - nothing over his "run" at 94
                    .cue(3, SoundEvents.BONE_BLOCK_BREAK, 0.9F, 0.5F)
                    .cue(7, SoundEvents.SKELETON_STEP, 0.8F, 0.55F).cue(10, SoundEvents.BONE_BLOCK_STEP, 0.8F, 0.6F)
                    .cue(13, SoundEvents.SKELETON_STEP, 0.8F, 0.5F).cue(16, SoundEvents.BONE_BLOCK_STEP, 0.9F, 0.55F)
                    .cue(19, SoundEvents.SKELETON_STEP, 0.9F, 0.45F)
                    .cue(23, SoundEvents.BONE_BLOCK_PLACE, 1.0F, 0.5F).cue(24, SoundEvents.SKELETON_HURT, 0.7F, 0.4F)
                    .cue(31, FFSounds.BONE_LORD_STEP.get(), 0.7F, 0.75F)
                    .cue(43, FFSounds.BONE_LORD_STEP.get(), 1.0F, 0.62F).cue(44, SoundEvents.BONE_BLOCK_HIT, 0.8F, 0.5F)
                    .cue(53, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 0.6F)
                    .cue(60, SoundEvents.SKELETON_AMBIENT, 0.8F, 0.4F).cue(68, SoundEvents.BONE_BLOCK_STEP, 0.8F, 0.5F)
                    .cue(81, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 0.75F)
                    .cue(90, FFSounds.BONE_LORD_STEP.get(), 1.0F, 0.72F)
                    .cue(91, FFSounds.BONE_LORD_STEP.get(), 0.9F, 0.8F);
        }
    }
}
