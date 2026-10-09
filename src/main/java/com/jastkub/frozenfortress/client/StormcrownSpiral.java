package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.StormcrownBeaconBlockEntity;
import com.jastkub.frozenfortress.entity.boss.StormEyeFloeEntity;
import com.jastkub.frozenfortress.registry.FFBlocks;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * THE KING'S STORM OVER THE CITADEL: while the Stormcrown burns for the court, its beam climbs into an enormous dark
 * storm turning slowly over the whole citadel - three arms of black cloud wound like a galaxy round a dark eye, threads
 * of the king's violet light inside them, lightning crawling through the deck now and then, and a funnel hanging from
 * the eye that the beam feeds. While the wedge is in the crown the storm thrashes (faster, lightning on lightning).
 * Take the crown and the storm screams once - one great flash - then unwinds: its arms straighten and drift apart, its
 * eye opens, the light in it goes out, it tears away in tatters from the middle outward, and is gone in sixteen seconds.
 *
 * <p>NOTHING HERE IS A PARTICLE. Each layer is a disc of quads laid in log-polar coordinates: u goes round the centre
 * (one texture tile per arm) plus WIND x ln r, which is what winds every tile into a spiral; v runs along ln r and is
 * scrolled, so the cloud streams inward along the arms while the whole turns (once in three minutes; the lower deck,
 * the core, the eye and the funnel faster). The layers, top to bottom: a broad faint HAZE, the ARMS, the violet GLOW
 * (added), the dense CORE, the lightning, the broken lower FLOW, the EYE (a near-black disc, a swirl and a violet rim),
 * the FUNNEL down to the beam. Textures: tools/gen_storm_spiral.py (its --preview lays the same layers as this class,
 * so the look can be judged without the game). Some 5000 vertices (5700 with four flashes at once), in 13 draws.
 *
 * <p>Drawn right after the sky (AFTER_SKY) and writing no depth: it is part of the sky. Everything in the world, the
 * vanilla clouds (it hangs OVER_CLOUDS above them), the weather and the beam itself is drawn in front of it - so it
 * never fights anything for depth and needs no culling. Sky-scale, so it is drawn whatever the chunk distance: it
 * gathers within ~300 blocks of the crown (less when the far plane is near) and thins as you leave.
 *
 * <p>Where the crown is, whether it is taken and whether the wedge is in it come from StormcrownBeaconBlockEntity's
 * client tick, remembered when its chunk unloads (as CitadelMist reads them). The beam (StormcrownBeaconRenderer) ends
 * in the eye while the storm hangs there - beamHeight - and where the crown's chunk is not loaded (no beam drawn) a
 * thin thread of light stands in for it (faintly, too, where a thick haze swallows the beam on its way up).
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class StormcrownSpiral {

    private static final ResourceLocation ARMS_TEX = FrozenFortress.id("textures/environment/storm_arms.png");
    private static final ResourceLocation CLOUD_TEX = FrozenFortress.id("textures/environment/storm_cloud.png");
    private static final ResourceLocation GLOW_TEX = FrozenFortress.id("textures/environment/storm_glow.png");
    /** A soft line across u - and, sampled at u = 0.5, a solid fill (the eye). */
    private static final ResourceLocation BOLT_TEX = FrozenFortress.id("textures/environment/storm_bolt.png");

    /** How far its arms reach from the eye (blocks; the haze a little further). */
    private static final float RADIUS = 165.0F;
    /** How high it hangs: far over the crown, and always over the vanilla clouds (they are drawn in front of it). */
    private static final float OVER_CROWN = 160.0F, OVER_CLOUDS = 30.0F;
    /** Its arms (one u tile each) and how tightly they wind (u per unit of ln r, about PIVOT blocks out) - as
     *  tools/gen_storm_spiral.py ARMS, WIND. */
    private static final int ARMS = 3;
    private static final float WIND = 1.1F;
    private static final float LN_PIVOT = (float) Math.log(48.0D);
    /** The deck hangs lowest at the eye and curls up toward the rim. */
    private static final float SAG = 9.0F, CURL = 4.0F;
    /** Each layer's height over the storm's altitude. */
    private static final float HAZE_Y = 12.0F, ARMS_Y = 4.0F, GLOW_Y = 1.5F, CORE_Y = -1.0F, FLASH_Y = 0.0F,
            FLOW_Y = -4.0F, EYE_Y = -6.0F;
    /** The funnel under the eye, up which the beam climbs into it. */
    private static final float FUNNEL_LEN = 38.0F, FUNNEL_TOP = 15.0F, FUNNEL_TIP = 1.2F;
    /** The arms turn once in three minutes. */
    private static final double TURN = Math.PI * 2.0D / 3600.0D;
    /** It gathers over six seconds once within reach (FAR, less if the far plane is nearer) and thins over three. */
    private static final float FAR = 330.0F, FADE_SPAN = 100.0F, RISE = 1.0F / 120.0F, FALL = 1.0F / 60.0F;
    /** Taken: unwound and gone in sixteen seconds. Unwinding, it spreads by this much and rises this far. */
    private static final float DISSOLVE_STEP = 1.0F / 320.0F, SPREAD = 0.45F, LIFT = 14.0F;
    /** The sky under it is overcast (StormSky): in full within SKY_FULL blocks of the crown, clearing by SKY_EDGE -
     *  wherever the storm itself is drawn or not (a short render distance thins the storm, not the weather). */
    private static final float SKY_FULL = 190.0F, SKY_EDGE = 330.0F;
    /** How much of the sky's own haze (the fog colour) is laid over its cloud. */
    private static final float AERIAL = 0.12F;
    /** The king's violet (his third phase, #C9A6FF). */
    private static final float VR = 0.79F, VG = 0.65F, VB = 1.0F;

    /** The rings each layer is laid on (blocks out from the eye). */
    private static final float[] HAZE_RINGS = {8, 30, 62, 100, 145, 190};
    private static final float[] ARMS_RINGS = {3, 5, 8, 13, 20, 30, 44, 62, 86, 118, 165};
    private static final float[] GLOW_RINGS = {5, 9, 15, 24, 37, 55, 80, 120};
    private static final float[] CORE_RINGS = {2, 7, 15, 26, 40, 58};
    private static final float[] FLOW_RINGS = {4, 8, 14, 22, 34, 50, 72, 100, 130, 150};
    private static final float[] EYE_RINGS = {0, 4, 9, 15, 24};
    private static final float[] RIM_RINGS = {6, 9, 12, 15};
    /** Which profile a disc is laid with (and the seed of its tatters). */
    private static final int HAZE = 0, ARM_LAYER = 1, GLOW = 2, CORE = 3, FLOW = 4, EYE = 5, SWIRL = 6, RIM = 7;

    // ------------------------------------------------------------------------------------------------ state
    /** The crown the storm hangs over (null: none here). */
    @Nullable
    private static BlockPos anchor;
    private static boolean wasTaken;
    /** A Storm Eye arena (Velkhar's last phase, fought above the clouds) is up: the storm stands aside. */
    private static boolean arenaUp;
    /** Within reach (0-1); taken and unwinding (0-1); the wedge in the crown (0-1); the stand-in thread (0-1). */
    private static float presence, lastPresence, dissolve, lastDissolve, heat, lastHeat, thread, lastThread;
    /** How overcast the sky is where the camera stands (0-1, StormSky). */
    private static float overcast;
    /** How far the arms have turned (radians). */
    private static double spin, lastSpin;
    private static long ticks;
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final RandomSource RNG = RandomSource.create();

    private StormcrownSpiral() {
    }

    // ------------------------------------------------------------------------------------------------ ticking
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            reset();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        lastPresence = presence;
        lastDissolve = dissolve;
        lastHeat = heat;
        lastThread = thread;
        lastSpin = spin;
        ticks++;

        BlockPos at = StormcrownBeaconBlockEntity.clientSeenAt;
        if (at == null || level.dimension() != StormcrownBeaconBlockEntity.clientSeenIn) {
            reset();
            return;
        }
        boolean taken = StormcrownBeaconBlockEntity.clientSeenTaken;
        boolean loaded = level.isLoaded(at);
        if (loaded && !level.getBlockState(at).is(FFBlocks.STORMCROWN_BEACON.get())) {
            // broken out rather than taken: the storm goes all the same (and the mist with it - CitadelMist reads this)
            taken = true;
            StormcrownBeaconBlockEntity.clientSeenTaken = true;
        }
        if (!at.equals(anchor)) {
            // a crown first seen (or another one): if it is already taken there is nothing left to see go
            anchor = at;
            wasTaken = taken;
            dissolve = lastDissolve = taken ? 1.0F : 0.0F;
            presence = lastPresence = 0.0F;
            FLASHES.clear();
        }
        if (ticks % 20L == 0L) {
            arenaUp = false;
            for (Entity e : level.entitiesForRendering()) {
                if (e instanceof StormEyeFloeEntity) {
                    arenaUp = true;
                    break;
                }
            }
        }

        // within reach (and nothing in the sky in its way)?
        double dx = mc.player.getX() - (at.getX() + 0.5D);
        double dz = mc.player.getZ() - (at.getZ() + 0.5D);
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        float far = Mth.clamp(mc.gameRenderer.getDepthFar() - 240.0F, 140.0F, FAR);
        float target = arenaUp ? 0.0F : 1.0F - smoothstep(far - FADE_SPAN, far, dist);
        presence = target > presence ? Math.min(target, presence + RISE) : Math.max(target, presence - FALL);
        // the sky under it: grey while it stands, clearing as it unwinds (and gone while the Storm Eye arena is up)
        float sky = arenaUp ? 0.0F : (1.0F - smoothstep(SKY_FULL, SKY_EDGE, dist))
                * (1.0F - smoothstep(0.0F, 0.7F, dissolve));
        overcast = sky > overcast ? Math.min(sky, overcast + RISE) : Math.max(sky, overcast - FALL);
        StormSky.set(overcast);

        // taken: the storm screams once, then unwinds (seen from afar or arriving later, it is simply gone)
        if (taken) {
            if (!wasTaken && presence > 0.02F && dissolve < 0.05F) {
                FLASHES.clear();
                strike(true);
            }
            dissolve = presence > 0.02F ? Math.min(1.0F, dissolve + DISSOLVE_STEP) : 1.0F;
        } else {
            dissolve = Math.max(0.0F, dissolve - 1.0F / 80.0F);        // a crown set burning again: it gathers again
        }
        wasTaken = taken;
        heat = !taken && StormcrownBeaconBlockEntity.clientSeenBreaking
                ? Math.min(1.0F, heat + 0.05F) : Math.max(0.0F, heat - 0.02F);
        // the beam is only drawn while the crown's chunk is loaded: past that, the thread stands in for it
        thread = loaded ? Math.max(0.0F, thread - 0.05F) : Math.min(1.0F, thread + 0.05F);
        spin += TURN * (1.0D + 1.6D * heat) * (1.0D - 0.8D * ease(dissolve));

        // lightning: now and then while it burns, on and on while the wedge is in it
        FLASHES.removeIf(f -> ++f.age >= f.life);
        float chance = heat > 0.05F ? 0.04F + 0.22F * heat : 1.0F / 110.0F;
        if (presence > 0.05F && dissolve < 0.1F && FLASHES.size() < 4 && RNG.nextFloat() < chance) {
            strike(false);
        }
    }

    private static void reset() {
        anchor = null;
        presence = lastPresence = 0.0F;
        dissolve = lastDissolve = 0.0F;
        heat = lastHeat = 0.0F;
        thread = lastThread = 0.0F;
        overcast = 0.0F;
        StormSky.clear();
        arenaUp = false;
        FLASHES.clear();
    }

    /** Leaving a world forgets its crown (or the next world's sky would gather a storm over where it stood). */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        StormcrownBeaconBlockEntity.clientSeenAt = null;
        StormcrownBeaconBlockEntity.clientSeenTaken = false;
        StormcrownBeaconBlockEntity.clientSeenBreaking = false;
        StormcrownBeaconBlockEntity.clientSeenIn = null;
        reset();
    }

    // ------------------------------------------------------------------------------------------------ the beam
    /**
     * How far up the crown's beam reaches (StormcrownBeaconRenderer; `from` is where it leaves, above the block): into
     * the storm's eye while the storm hangs there, the whole `full` way to the sky once it has unwound.
     */
    public static int beamHeight(BlockPos pos, int from, int full) {
        ClientLevel level = Minecraft.getInstance().level;
        BlockPos at = anchor;
        if (level == null || at == null || !at.equals(pos)) {
            return full;
        }
        float hold = smoothstep(0.0F, 0.35F, presence) * (1.0F - smoothstep(0.0F, 0.45F, dissolve));
        if (hold <= 0.001F) {
            return full;
        }
        float eye = altitude(level, pos) + LIFT * ease(dissolve) + EYE_Y + sag(0.0F);
        float top = Math.min(full, eye + 1.0F - (pos.getY() + from));
        return Math.max(1, Math.round(Mth.lerp(hold, full, top)));
    }

    /** Where the storm hangs: far over the crown, and over the clouds. */
    private static float altitude(ClientLevel level, BlockPos at) {
        float h = at.getY() + OVER_CROWN;
        float clouds = level.effects().getCloudHeight();
        if (!Float.isNaN(clouds)) {
            h = Math.max(h, clouds + OVER_CLOUDS);
        }
        return h;
    }

    // ------------------------------------------------------------------------------------------------ drawing
    /** Everything one frame needs, relative to the camera. */
    private static final class Frame {
        Matrix4f pose;
        /** The eye, relative to the camera (x, z), the storm's altitude and the eye's height (y). */
        float cx, cz, base, eyeY;
        float dissolve, scale, unwind, vis, heat;
        /** Its cloud colour this frame (sky-lit by day, a little violet and lit from within at night). */
        float cr, cg, cb, glow;
        double time, spin;
    }

    private interface Builder {
        int build(BufferBuilder bb);
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        BlockPos at = anchor;
        if (level == null || at == null || level.dimension() != StormcrownBeaconBlockEntity.clientSeenIn) {
            return;
        }
        float pt = event.getPartialTick();
        float d = Mth.lerp(pt, lastDissolve, dissolve);
        float vis = Mth.lerp(pt, lastPresence, presence) * (1.0F - smoothstep(0.85F, 1.0F, d));
        Camera camera = event.getCamera();
        if (vis <= 0.004F || camera.getFluidInCamera() != FogType.NONE || blinded(camera.getEntity())) {
            return;                                    // (under water, in powder snow or blind the sky is not seen)
        }
        Vec3 cam = camera.getPosition();

        Frame f = new Frame();
        f.pose = event.getPoseStack().last().pose();
        f.cx = (float) (at.getX() + 0.5D - cam.x);
        f.cz = (float) (at.getZ() + 0.5D - cam.z);
        f.dissolve = d;
        float e = ease(d);
        f.scale = 1.0F + SPREAD * e;
        f.unwind = 1.0F - 0.85F * e;
        f.base = (float) (altitude(level, at) + LIFT * e - cam.y);
        f.eyeY = f.base + EYE_Y + sag(0.0F);
        f.vis = vis;
        f.heat = Mth.lerp(pt, lastHeat, heat);
        f.time = ticks + (double) pt;
        f.spin = Mth.lerp((double) pt, lastSpin, spin);
        Vec3 sky = level.getSkyColor(cam, pt);
        float lum = Mth.clamp((float) (0.2126D * sky.x + 0.7152D * sky.y + 0.0722D * sky.z), 0.0F, 1.0F);
        float c = 0.075F + 0.13F * lum;
        float[] fog = RenderSystem.getShaderFogColor();
        f.cr = Mth.lerp(AERIAL, c * Mth.lerp(lum, 0.92F, 0.95F), fog[0]);
        f.cg = Mth.lerp(AERIAL, c * Mth.lerp(lum, 0.84F, 0.93F), fog[1]);
        f.cb = Mth.lerp(AERIAL, c * Mth.lerp(lum, 1.35F, 1.10F), fog[2]);
        f.glow = (0.6F + 1.4F * (1.0F - lum)) * (1.0F + 0.8F * f.heat);
        // the thread: in full where the beam is not drawn at all, and faintly where a thick haze (CitadelMist, a short
        // render distance) swallows the beam long before it reaches the eye (at AFTER_SKY the fog is the sky's)
        float unloaded = Mth.lerp(pt, lastThread, thread);
        float mist = 1.0F - smoothstep(100.0F, 160.0F, RenderSystem.getShaderFogEnd());
        float threadA = 0.6F * unloaded + 0.3F * mist * (1.0F - unloaded);

        // the eye goes first, the light in it soon after - but its rim flares as the crown is taken
        float eyeKeep = 1.0F - smoothstep(0.0F, 0.3F, d);
        float glowKeep = 1.0F - smoothstep(0.0F, 0.4F, d);
        float pulse = 0.5F + 0.12F * Mth.sin((float) ((f.time * 0.06D) % (Math.PI * 2.0D)))
                + 0.35F * f.heat * (0.5F + 0.5F * Mth.sin((float) ((f.time * 1.3D) % (Math.PI * 2.0D))));
        float flare = smoothstep(0.0F, 0.03F, d) * (1.0F - smoothstep(0.05F, 0.3F, d)) * 1.6F;
        float rimA = vis * (pulse * f.glow * 0.5F * glowKeep + flare);

        List<Runnable> steps = new ArrayList<>(13);
        steps.add(() -> draw(CLOUD_TEX, false, bb -> disc(bb, f, HAZE, HAZE_RINGS, 20, 2, WIND * 0.5F, 0.0F, 0.32F,
                scroll(f, 0.0002D), f.spin * 0.6D + 2.1D, HAZE_Y, f.cr * 1.15F, f.cg * 1.15F, f.cb * 1.15F, vis)));
        steps.add(() -> draw(ARMS_TEX, false, bb -> disc(bb, f, ARM_LAYER, ARMS_RINGS, 28, ARMS, WIND, 0.0F, 1.28F,
                scroll(f, 0.0005D), f.spin, ARMS_Y, f.cr, f.cg, f.cb, vis)));
        steps.add(() -> draw(GLOW_TEX, true, bb -> disc(bb, f, GLOW, GLOW_RINGS, 24, ARMS, WIND, 0.0F, 0.9F,
                0.3F + scroll(f, 0.0011D), f.spin, GLOW_Y, VR, VG, VB, vis * 0.55F * f.glow * glowKeep)));
        steps.add(() -> draw(CLOUD_TEX, false, bb -> disc(bb, f, CORE, CORE_RINGS, 20, 2, WIND * 1.5F, 0.0F, 0.5F,
                0.4F + scroll(f, 0.0016D), f.spin * 2.2D + 1.0D, CORE_Y, f.cr * 0.85F, f.cg * 0.85F, f.cb * 0.85F,
                vis)));
        steps.add(() -> draw(CLOUD_TEX, true, bb -> flashes(bb, f, pt)));
        steps.add(() -> draw(BOLT_TEX, true, bb -> bolts(bb, f, pt)));
        steps.add(() -> draw(CLOUD_TEX, false, bb -> disc(bb, f, FLOW, FLOW_RINGS, 24, ARMS, WIND * 1.12F, 0.0F, 0.6F,
                0.1F + scroll(f, 0.0009D), f.spin * 1.35D + 0.7D, FLOW_Y, f.cr * 0.8F, f.cg * 0.8F, f.cb * 0.8F, vis)));
        steps.add(() -> draw(BOLT_TEX, false, bb -> disc(bb, f, EYE, EYE_RINGS, 20, 0, 0.0F, 0.5F, 0.0F, 0.5F,
                0.0D, EYE_Y, 0.035F, 0.025F, 0.06F, vis * eyeKeep)));
        steps.add(() -> draw(CLOUD_TEX, false, bb -> disc(bb, f, SWIRL, EYE_RINGS, 20, 2, 1.6F, 0.0F, 0.5F,
                scroll(f, 0.003D), f.spin * 5.0D, EYE_Y - 0.3F, f.cr * 0.5F, f.cg * 0.5F, f.cb * 0.55F,
                vis * eyeKeep)));
        steps.add(() -> draw(CLOUD_TEX, true, bb -> disc(bb, f, RIM, RIM_RINGS, 20, 3, 0.6F, 0.0F, 1.2F,
                scroll(f, 0.002D), f.spin * 3.0D, EYE_Y - 0.6F, VR, VG, VB, rimA)));
        steps.add(() -> draw(CLOUD_TEX, false, bb -> funnel(bb, f, false, f.cr * 0.7F, f.cg * 0.65F, f.cb * 0.8F,
                vis * 0.8F * (1.0F - smoothstep(0.1F, 0.4F, d)))));
        steps.add(() -> draw(GLOW_TEX, true, bb -> funnel(bb, f, true, VR, VG, VB,
                vis * 0.5F * f.glow * glowKeep)));
        steps.add(() -> draw(BOLT_TEX, true, bb -> thread(bb, f, at, cam,
                vis * threadA * (1.0F - smoothstep(0.0F, 0.3F, d)))));

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();                // the sky's depth is clear; nothing to test against yet
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        // (the corners are in view space already: the level's pose stack carries the camera's turn - as RoarWarpFx)
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();

        // far to near: from below, the top layer first; from above it (flying), the other way round
        boolean above = f.base + HAZE_Y + 6.0F < 0.0F;
        if (above) {
            for (int i = steps.size() - 1; i >= 0; i--) {
                steps.get(i).run();
            }
        } else {
            for (Runnable step : steps) {
                step.run();
            }
        }

        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    private static boolean blinded(@Nullable Entity e) {
        return e instanceof LivingEntity l && (l.hasEffect(MobEffects.BLINDNESS) || l.hasEffect(MobEffects.DARKNESS));
    }

    /** One draw: its texture, laid over (dark cloud) or added (light), its quads built by `b`. */
    private static void draw(ResourceLocation tex, boolean additive, Builder b) {
        RenderSystem.setShaderTexture(0, tex);
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int quads = b.build(bb);
        BufferBuilder.RenderedBuffer done = bb.end();
        if (quads > 0) {
            BufferUploader.drawWithShader(done);
        } else {
            done.release();
        }
    }

    private static float scroll(Frame f, double rate) {
        return (float) ((f.time * rate) % 1.0D);
    }

    /**
     * One layer: a disc laid on `rings` (blocks from the eye) x `segs` round, turned by `angle`, in log-polar
     * coordinates - u = uBase + n round the centre + wind x (ln r - ln PIVOT), v = vScale x ln r + vOff. Its alpha is the
     * layer's profile out from the eye times `gain`, torn away in tatters as the storm dissolves.
     */
    private static int disc(BufferBuilder bb, Frame f, int layer, float[] rings, int segs, int n, float wind,
                            float uBase, float vScale, float vOff, double angle, float dy, float r, float g, float b,
                            float gain) {
        if (gain <= 0.003F) {
            return 0;
        }
        int count = rings.length;
        float[] cs = new float[segs + 1];
        float[] sn = new float[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double t = Math.PI * 2.0D * i / segs + angle;
            cs[i] = (float) Math.cos(t);
            sn[i] = (float) Math.sin(t);
        }
        float[] rad = new float[count];
        float[] ys = new float[count];
        float[] uo = new float[count];
        float[] vs = new float[count];
        float[] al = new float[count];
        float[] rns = new float[count];
        for (int k = 0; k < count; k++) {
            float rb = rings[k];
            float rn = rb / RADIUS;
            float ln = (float) Math.log(Math.max(rb, 1.0F));
            rad[k] = rb * f.scale;
            ys[k] = f.base + dy + sag(rn);
            uo[k] = uBase + wind * f.unwind * (ln - LN_PIVOT);
            vs[k] = vScale * ln + vOff;
            al[k] = profile(layer, rn, rb) * gain;
            rns[k] = rn;
        }
        int quads = 0;
        for (int k = 0; k < count - 1; k++) {
            if (al[k] < 0.003F && al[k + 1] < 0.003F) {
                continue;
            }
            for (int i = 0; i < segs; i++) {
                float a00 = al[k] * tatter(f, rns[k], k, i, segs, layer);
                float a10 = al[k + 1] * tatter(f, rns[k + 1], k + 1, i, segs, layer);
                float a11 = al[k + 1] * tatter(f, rns[k + 1], k + 1, i + 1, segs, layer);
                float a01 = al[k] * tatter(f, rns[k], k, i + 1, segs, layer);
                if (a00 + a10 + a11 + a01 < 0.004F) {
                    continue;
                }
                float u0 = (float) n * i / segs;
                float u1 = (float) n * (i + 1) / segs;
                put(bb, f, cs[i] * rad[k], ys[k], sn[i] * rad[k], u0 + uo[k], vs[k], r, g, b, a00);
                put(bb, f, cs[i] * rad[k + 1], ys[k + 1], sn[i] * rad[k + 1], u0 + uo[k + 1], vs[k + 1], r, g, b, a10);
                put(bb, f, cs[i + 1] * rad[k + 1], ys[k + 1], sn[i + 1] * rad[k + 1], u1 + uo[k + 1], vs[k + 1],
                        r, g, b, a11);
                put(bb, f, cs[i + 1] * rad[k], ys[k], sn[i + 1] * rad[k], u1 + uo[k], vs[k], r, g, b, a01);
                quads++;
            }
        }
        return quads;
    }

    /** How dense a layer is `rn` (of RADIUS) / `rb` (blocks) out from the eye. */
    private static float profile(int layer, float rn, float rb) {
        return switch (layer) {
            case HAZE -> 0.5F * (1.0F - smoothstep(0.45F, 1.0F, rn / 1.15F));
            case ARM_LAYER -> smoothstep(0.0F, 0.03F, rn) * (1.0F - smoothstep(0.7F, 1.0F, rn)) * (0.97F - 0.12F * rn);
            case GLOW -> smoothstep(0.03F, 0.12F, rn) * (1.0F - smoothstep(0.32F, 0.72F, rn));
            case CORE -> 0.85F * (1.0F - smoothstep(0.06F, 0.34F, rn));
            case FLOW -> 0.6F * (1.0F - smoothstep(0.42F, 0.85F, rn));
            case EYE -> 0.94F * (1.0F - smoothstep(5.0F, 24.0F, rb));
            case SWIRL -> 0.55F * (1.0F - smoothstep(7.0F, 24.0F, rb));
            default -> (float) Math.exp(-Math.pow((rb - 10.5F) / 2.8F, 2.0D));          // RIM
        };
    }

    /** As it dissolves, each corner goes at its own moment: the middle first, then outward, raggedly. */
    private static float tatter(Frame f, float rn, int k, int i, int segs, int layer) {
        if (f.dissolve <= 0.0F) {
            return 1.0F;
        }
        float h = hash(k, i % segs, layer);
        float when = 0.1F + 0.5F * Math.min(rn, 1.2F) + 0.3F * h;
        return 1.0F - smoothstep(when - 0.2F, when + 0.03F, f.dissolve);
    }

    /** The deck hangs lowest at the eye and curls up toward the rim. */
    private static float sag(float rn) {
        float in = 1.0F - Math.min(rn, 1.0F);
        return -SAG * in * in + CURL * rn * rn;
    }

    /** The funnel under the eye: a cone of cloud (or, `glow`, of violet light inside it) narrowing to the beam. */
    private static int funnel(BufferBuilder bb, Frame f, boolean glow, float r, float g, float b, float gain) {
        if (gain <= 0.003F) {
            return 0;
        }
        final int bands = 5;
        final int segs = 16;
        float len = FUNNEL_LEN * (1.0F - 0.85F * smoothstep(0.0F, 0.4F, f.dissolve));
        float shrink = (glow ? 0.72F : 1.0F) * f.scale;
        double angle = f.spin * (glow ? 14.0D : 11.0D);
        float off = scroll(f, glow ? 0.009D : 0.005D);
        float[] cs = new float[segs + 1];
        float[] sn = new float[segs + 1];
        for (int i = 0; i <= segs; i++) {
            double t = Math.PI * 2.0D * i / segs + angle;
            cs[i] = (float) Math.cos(t);
            sn[i] = (float) Math.sin(t);
        }
        int quads = 0;
        for (int k = 0; k < bands; k++) {
            float t0 = (float) k / bands;
            float t1 = (float) (k + 1) / bands;
            float r0 = (FUNNEL_TIP + (FUNNEL_TOP - FUNNEL_TIP) * (float) Math.pow(t0, 2.2D)) * shrink;
            float r1 = (FUNNEL_TIP + (FUNNEL_TOP - FUNNEL_TIP) * (float) Math.pow(t1, 2.2D)) * shrink;
            float y0 = f.eyeY - len * (1.0F - t0);
            float y1 = f.eyeY - len * (1.0F - t1);
            float a0 = smoothstep(0.04F, 0.4F, t0) * gain;
            float a1 = smoothstep(0.04F, 0.4F, t1) * gain;
            float tw0 = 1.4F * (1.0F - t0);                    // twisted: it is a vortex, not a cone
            float tw1 = 1.4F * (1.0F - t1);
            float v0 = 1.3F * t0 - off;                         // (scrolled down the texture: the cloud rises)
            float v1 = 1.3F * t1 - off;
            for (int i = 0; i < segs; i++) {
                float u0 = 2.0F * i / segs;
                float u1 = 2.0F * (i + 1) / segs;
                put(bb, f, cs[i] * r0, y0, sn[i] * r0, u0 + tw0, v0, r, g, b, a0);
                put(bb, f, cs[i] * r1, y1, sn[i] * r1, u0 + tw1, v1, r, g, b, a1);
                put(bb, f, cs[i + 1] * r1, y1, sn[i + 1] * r1, u1 + tw1, v1, r, g, b, a1);
                put(bb, f, cs[i + 1] * r0, y0, sn[i + 1] * r0, u1 + tw0, v0, r, g, b, a0);
                quads++;
            }
        }
        return quads;
    }

    /** The stand-in for the beam where it is not drawn: a thin thread of glacial light from the crown to the eye,
     *  turned across the line of sight. */
    private static int thread(BufferBuilder bb, Frame f, BlockPos at, Vec3 cam, float a) {
        if (a <= 0.005F) {
            return 0;
        }
        float len = Mth.sqrt(f.cx * f.cx + f.cz * f.cz);
        if (len < 0.5F) {
            return 0;
        }
        float sx = -f.cz / len * 0.8F;
        float sz = f.cx / len * 0.8F;
        float y0 = (float) (at.getY() + 1.0D - cam.y);
        float y1 = f.eyeY;
        float v0 = scroll(f, 0.02D);
        float v1 = v0 + (y1 - y0) / 12.0F;
        bb.vertex(f.pose, f.cx - sx, y0, f.cz - sz).uv(0.0F, v1).color(0.62F, 0.8F, 1.0F, clamp01(a)).endVertex();
        bb.vertex(f.pose, f.cx + sx, y0, f.cz + sz).uv(1.0F, v1).color(0.62F, 0.8F, 1.0F, clamp01(a)).endVertex();
        bb.vertex(f.pose, f.cx + sx, y1, f.cz + sz).uv(1.0F, v0).color(0.62F, 0.8F, 1.0F, clamp01(a)).endVertex();
        bb.vertex(f.pose, f.cx - sx, y1, f.cz - sz).uv(0.0F, v0).color(0.62F, 0.8F, 1.0F, clamp01(a)).endVertex();
        return 1;
    }

    private static void put(BufferBuilder bb, Frame f, float x, float y, float z, float u, float v, float r, float g,
                            float b, float a) {
        bb.vertex(f.pose, f.cx + x, y, f.cz + z).uv(u, v).color(clamp01(r), clamp01(g), clamp01(b), clamp01(a))
                .endVertex();
    }

    // ------------------------------------------------------------------------------------------------ lightning
    /** A flash inside the deck: a brightening of the cloud round a point on an arm (in the arms' frame, so it turns
     *  with them), with a jagged bolt crawling from it through the brightest beats. */
    private static final class Flash {
        final float r, th, sigma, power, width;
        final int life;
        final float[] bolt, branch;
        int age;

        Flash(float r, float th, float sigma, float power, float width, int life, float[] bolt, float[] branch) {
            this.r = r;
            this.th = th;
            this.sigma = sigma;
            this.power = power;
            this.width = width;
            this.life = life;
            this.bolt = bolt;
            this.branch = branch;
        }

        /** Bright, out, bright again, dying away. */
        float flicker(float a) {
            float k;
            if (a < 0.0F) {
                return 0.0F;
            } else if (a < 1.5F) {
                k = 1.0F;
            } else if (a < 3.0F) {
                k = 0.3F;
            } else if (a < 4.5F) {
                k = 0.85F;
            } else {
                k = 0.85F * Math.max(0.0F, 1.0F - (a - 4.5F) / Math.max(1.0F, life - 4.5F));
            }
            return k * power;
        }
    }

    /** A new flash: on a random arm (or, `big` - the crown taken - the whole storm from the eye out). */
    private static void strike(boolean big) {
        float r;
        float th;
        float sigma;
        float power;
        float width;
        float step;
        int life;
        float heading;
        if (big) {
            r = 0.0F;
            th = 0.0F;
            sigma = 60.0F;
            power = 1.7F;
            width = 1.6F;
            step = 14.0F;
            life = 18;
            heading = RNG.nextFloat() * Mth.TWO_PI;
        } else {
            r = RADIUS * (0.14F + 0.56F * RNG.nextFloat());
            int arm = RNG.nextInt(ARMS);
            // the middle of an arm: where u = arm + 1/2 (the arms texture's centre line)
            th = (float) (Math.PI * 2.0D / ARMS * (arm + 0.5D - WIND * (Math.log(r) - LN_PIVOT)))
                    + (RNG.nextFloat() - 0.5F) * 0.12F;
            sigma = 13.0F + 15.0F * RNG.nextFloat();
            power = 0.7F + 0.3F * RNG.nextFloat();
            width = 1.0F;
            step = 6.0F;
            life = 6 + RNG.nextInt(9);
            // along the arm there: radial - (2 pi WIND / ARMS) x tangential, either way
            float k = (float) (Math.PI * 2.0D * WIND / ARMS);
            float dx = Mth.cos(th) + k * Mth.sin(th);
            float dz = Mth.sin(th) - k * Mth.cos(th);
            heading = (float) Mth.atan2(dz, dx) + (RNG.nextBoolean() ? 0.0F : Mth.PI);
        }
        float[] bolt = zigzag(r * Mth.cos(th), r * Mth.sin(th), heading, 7, step);
        float[] branch = zigzag(bolt[6], bolt[7], heading + (RNG.nextBoolean() ? 0.7F : -0.7F), 3, step * 0.8F);
        FLASHES.add(new Flash(r, th, sigma, power, width, life, bolt, branch));
    }

    /** A jagged line of `segs` steps from (x, z), keeping roughly to `heading`: x0, z0, x1, z1, ... */
    private static float[] zigzag(float x, float z, float heading, int segs, float step) {
        float[] p = new float[(segs + 1) * 2];
        p[0] = x;
        p[1] = z;
        for (int j = 1; j <= segs; j++) {
            float a = heading + (RNG.nextFloat() - 0.5F) * 0.9F;
            float len = step * (0.7F + 0.8F * RNG.nextFloat());
            x += Mth.cos(a) * len;
            z += Mth.sin(a) * len;
            p[j * 2] = x;
            p[j * 2 + 1] = z;
        }
        return p;
    }

    /** The flashes: a glow in the cloud round each, fading out over three sigmas (drawn added, with the cloud). */
    private static int flashes(BufferBuilder bb, Frame f, float pt) {
        float fade = f.vis * (1.0F - smoothstep(0.6F, 0.9F, f.dissolve));
        if (FLASHES.isEmpty() || fade <= 0.003F) {
            return 0;
        }
        float ca = (float) Math.cos(f.spin);
        float sa = (float) Math.sin(f.spin);
        final int segs = 12;
        int quads = 0;
        for (Flash fl : FLASHES) {
            float k = fl.flicker(fl.age + pt) * fade;
            if (k <= 0.01F) {
                continue;
            }
            float qx = fl.r * Mth.cos(fl.th);
            float qz = fl.r * Mth.sin(fl.th);
            for (int ring = 0; ring < 3; ring++) {
                float r0 = fl.sigma * ring;
                float r1 = fl.sigma * (ring + 1);
                float a0 = k * (float) Math.exp(-0.5D * ring * ring);
                float a1 = k * (float) Math.exp(-0.5D * (ring + 1) * (ring + 1));
                for (int i = 0; i < segs; i++) {
                    float p0 = Mth.TWO_PI * i / segs;
                    float p1 = Mth.TWO_PI * (i + 1) / segs;
                    flashCorner(bb, f, ca, sa, qx + r0 * Mth.cos(p0), qz + r0 * Mth.sin(p0), a0);
                    flashCorner(bb, f, ca, sa, qx + r1 * Mth.cos(p0), qz + r1 * Mth.sin(p0), a1);
                    flashCorner(bb, f, ca, sa, qx + r1 * Mth.cos(p1), qz + r1 * Mth.sin(p1), a1);
                    flashCorner(bb, f, ca, sa, qx + r0 * Mth.cos(p1), qz + r0 * Mth.sin(p1), a0);
                    quads++;
                }
            }
        }
        return quads;
    }

    /** A corner at (qx, qz) in the arms' frame, turned with them into the world. */
    private static void flashCorner(BufferBuilder bb, Frame f, float ca, float sa, float qx, float qz, float a) {
        float rn = Mth.sqrt(qx * qx + qz * qz) / RADIUS;
        put(bb, f, (qx * ca - qz * sa) * f.scale, f.base + FLASH_Y + sag(rn), (qx * sa + qz * ca) * f.scale,
                qx / 70.0F, qz / 70.0F, 0.86F, 0.8F, 1.0F, a);
    }

    /** The bolts, in the bright beats of their flashes only. */
    private static int bolts(BufferBuilder bb, Frame f, float pt) {
        float fade = f.vis * (1.0F - smoothstep(0.6F, 0.9F, f.dissolve));
        if (FLASHES.isEmpty() || fade <= 0.003F) {
            return 0;
        }
        float ca = (float) Math.cos(f.spin);
        float sa = (float) Math.sin(f.spin);
        int quads = 0;
        for (Flash fl : FLASHES) {
            float a = Mth.clamp((fl.flicker(fl.age + pt) / fl.power - 0.45F) * 1.8F, 0.0F, 1.0F) * fade;
            if (a <= 0.01F) {
                continue;
            }
            quads += ribbon(bb, f, ca, sa, fl.bolt, 2.4F * fl.width, a);
            quads += ribbon(bb, f, ca, sa, fl.branch, 1.5F * fl.width, a * 0.8F);
        }
        return quads;
    }

    /** A flat ribbon along a jagged line (the bolt texture's soft line across it), in the deck. */
    private static int ribbon(BufferBuilder bb, Frame f, float ca, float sa, float[] pts, float width, float a) {
        int quads = 0;
        for (int j = 0; j + 3 < pts.length; j += 2) {
            float x0 = pts[j];
            float z0 = pts[j + 1];
            float x1 = pts[j + 2];
            float z1 = pts[j + 3];
            float dx = x1 - x0;
            float dz = z1 - z0;
            float len = Mth.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-3F) {
                continue;
            }
            float px = -dz / len * width * 0.5F;
            float pz = dx / len * width * 0.5F;
            boltCorner(bb, f, ca, sa, x0 - px, z0 - pz, 0.0F, 0.0F, a);
            boltCorner(bb, f, ca, sa, x0 + px, z0 + pz, 1.0F, 0.0F, a);
            boltCorner(bb, f, ca, sa, x1 + px, z1 + pz, 1.0F, 1.0F, a);
            boltCorner(bb, f, ca, sa, x1 - px, z1 - pz, 0.0F, 1.0F, a);
            quads++;
        }
        return quads;
    }

    private static void boltCorner(BufferBuilder bb, Frame f, float ca, float sa, float qx, float qz, float u, float v,
                                   float a) {
        float rn = Mth.sqrt(qx * qx + qz * qz) / RADIUS;
        put(bb, f, (qx * ca - qz * sa) * f.scale, f.base + FLASH_Y - 0.5F + sag(rn), (qx * sa + qz * ca) * f.scale,
                u, v, 0.93F, 0.88F, 1.0F, a);
    }

    // ------------------------------------------------------------------------------------------------ helpers
    private static float smoothstep(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float ease(float x) {
        return smoothstep(0.0F, 1.0F, x);
    }

    private static float clamp01(float x) {
        return x < 0.0F ? 0.0F : Math.min(x, 1.0F);
    }

    private static float hash(int a, int b, int seed) {
        int h = a * 73856093 ^ b * 19349663 ^ seed * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65536.0F;
    }
}
