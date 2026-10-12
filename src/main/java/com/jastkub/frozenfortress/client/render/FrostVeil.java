package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.BossGateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * THE ICE FOG OVER A KEEPER'S HALL:
 * while a keeper lives in its hall, the doorway its gate hangs over is filled, seen from outside, with drifting frost -
 * six layers of it a little way into the hall: nothing of the room comes through but a shape, the boss itself at most a
 * shadow. Step through and it is behind you; kill the keeper and it lifts.
 *
 * <p>The Lamplighter's Candle (worn) thins it to a little over a third (LamplighterCandleItem) - and from inside the
 * hall it is never drawn: it hides the hall from the way in, not the way out.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class FrostVeil {

    private static final ResourceLocation TEX = FrozenFortress.id("textures/misc/frost_veil.png");
    /** How far into the hall each layer hangs, and how much of what is behind it each takes. */
    private static final float[] DEPTHS = {0.25F, 0.75F, 1.3F, 1.9F, 2.5F, 3.1F};
    private static final float LAYER_ALPHA = 0.6F, CANDLE = 0.42F;
    /** The least block light it is drawn in: a pale haze in the darkest hall, not a dark pane. */
    private static final int HAZE_LIGHT = 12;
    private static final float RED = 0.80F, GREEN = 0.88F, BLUE = 0.97F;
    /** A keeper looked for once a second per gate, not every frame. */
    private static final Map<BossGateBlockEntity, long[]> SEEN = new WeakHashMap<>();

    private FrostVeil() {
    }

    /** Drawn after the translucent blocks, in its own batch - not inside the gate's render, whose buffer is GeckoLib's. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || BossGateBlockEntity.CLIENT.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);                      // world coordinates from here
        MultiBufferSource.BufferSource src = mc.renderBuffers().bufferSource();
        for (BossGateBlockEntity gate : java.util.List.copyOf(BossGateBlockEntity.CLIENT)) {
            if (!gate.isRemoved() && gate.getLevel() == mc.level
                    && gate.getBlockPos().distToCenterSqr(cam) < 96.0D * 96.0D) {
                draw(gate, pose, src, event.getPartialTick().getGameTimeDeltaPartialTick(true));
            }
        }
        src.endBatch(RenderType.entityTranslucent(TEX));
        pose.popPose();
    }

    public static void draw(BossGateBlockEntity gate, PoseStack pose, MultiBufferSource buffers, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (gate.getLevel() == null || mc.player == null || gate.isExit() || gate.keeper().isEmpty()) {
            return;
        }
        AABB room = gate.roomBox();
        Camera cam = mc.gameRenderer.getMainCamera();
        //
        if (!keeperIn(gate, room) && !gate.prisonerHeld()) {
            return;
        }
        // ONCE: a hall you have gone into once is not veiled from you again
        String key = passKey(gate);
        if (key != null && passed().contains(key) && !midPassage(gate)) {
            return;
        }
        float alpha = LAYER_ALPHA * (com.jastkub.frozenfortress.item.LamplighterCandleItem.worn(mc.player) ? CANDLE : 1.0F);
        AABB open = gate.openingBox();
        // the opening's plane: the axis it is one cell thin along, and which way the hall lies across it
        boolean alongX = open.getXsize() <= open.getZsize();
        Vec3 c = open.getCenter();
        Vec3 rc = room.getCenter();
        double into = alongX ? Math.signum(rc.x - c.x) : Math.signum(rc.z - c.z);
        if (into == 0.0D) {
            into = 1.0D;
        }
        // GOING THROUGH IT: inside the hall, the layers still ahead of you are still there - only
        // those behind you (the way out) are not drawn; and the frost you walked through clings to you a while
        // (linger: the fog round you, thinning over LINGER ticks).
        Vec3 eye = cam.getPosition();
        double past = Double.NEGATIVE_INFINITY;
        if (room.contains(eye)) {
            double plane = alongX ? (into > 0 ? open.maxX : open.minX) : (into > 0 ? open.maxZ : open.minZ);
            past = ((alongX ? eye.x : eye.z) - plane) * into;
            if (past > DEPTHS[DEPTHS.length - 1] + 0.5D) {
                if (key != null && MID.remove(gate) && passed().add(key)) {
                    save();                                  // through it, the first time: remembered
                }
                return;                                      // through it all: from inside, the way out is clear
            }
            if (past > -0.5D && past < DEPTHS[DEPTHS.length - 1] + 0.5D) {
                linger(gate.getLevel().getGameTime());
                MID.add(gate);
            }
        }
        int light = LevelRenderer.getLightColor(gate.getLevel(), BlockPos.containing(c.x + (alongX ? into : 0), c.y,
                c.z + (alongX ? 0 : into)));
        light = Math.max(light & 0xFFFF, HAZE_LIGHT << 4) | (light & 0xFFFF0000);   // a pale haze even in the dark
        float t = (gate.getLevel().getGameTime() + partialTick) / 20.0F;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEX));
        Matrix4f m = pose.last().pose();
        PoseStack.Pose n = pose.last();
        double y0 = open.minY, y1 = open.maxY;
        for (int i = 0; i < DEPTHS.length; i++) {
            if (DEPTHS[i] < past + 0.1D) {
                continue;                                    // a layer behind you as you go through
            }
            double d = (alongX ? (into > 0 ? open.maxX : open.minX) : (into > 0 ? open.maxZ : open.minZ)) + into * DEPTHS[i];
            float du = t * (0.035F + 0.018F * i) * (i % 2 == 0 ? 1 : -1), dv = t * (0.012F + 0.006F * i);
            float a = alpha;
            if (alongX) {
                double z0 = open.minZ - 0.15D, z1 = open.maxZ + 0.15D;
                quad(vc, m, n, (float) d, (float) y0, (float) z0, (float) d, (float) y1, (float) z1, true, du, dv,
                        (float) (z1 - z0) / 3.0F, (float) (y1 - y0) / 3.0F, a, light);
            } else {
                double x0 = open.minX - 0.15D, x1 = open.maxX + 0.15D;
                quad(vc, m, n, (float) x0, (float) y0, (float) d, (float) x1, (float) y1, (float) d, false, du, dv,
                        (float) (x1 - x0) / 3.0F, (float) (y1 - y0) / 3.0F, a, light);
            }
        }
    }

    /** One layer: a plane across the opening (at x = const when `xPlane`, else z = const) - ONE face: entityTranslucent
     *  culls neither side, and a second face in the same plane split along the other diagonal fought it pixel by pixel
     *  (the chasm's mist flickered for it - 08.10.2026). */
    private static void quad(VertexConsumer vc, Matrix4f m, PoseStack.Pose n, float ax, float ay, float az, float bx, float by,
                             float bz, boolean xPlane, float du, float dv, float us, float vs, float alpha, int light) {
        float[][] p = xPlane
                ? new float[][]{{ax, ay, az}, {ax, by, az}, {ax, by, bz}, {ax, ay, bz}}
                : new float[][]{{ax, ay, az}, {ax, by, az}, {bx, by, az}, {bx, ay, az}};
        float[][] uv = {{du, dv + vs}, {du, dv}, {du + us, dv}, {du + us, dv + vs}};
        float nx = xPlane ? 1 : 0, nz = xPlane ? 0 : 1;
        for (int k = 0; k < 4; k++) {
            vc.addVertex(m, p[k][0], p[k][1], p[k][2]).setColor(RED, GREEN, BLUE, alpha).setUv(uv[k][0], uv[k][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n, nx, 0.0F, nz);
        }
    }

    // ------------------------------------------------------------------------------------------------ the frost that clings
    /** How long the veil's frost clings to whoever came through it (ticks), and how close it closes in at first: the
     *  hall a shape a few blocks off, coming clear as it thins. */
    private static final int LINGER = 64;
    private static final float LINGER_NEAR = 0.0F, LINGER_FAR = 5.0F;
    /** Its colour - the veil's, a little darker (the halls are dark: a bright fog would glow). */
    private static final float LR = 0.66F, LG = 0.73F, LB = 0.84F;
    private static long lingerAt = Long.MIN_VALUE;

    /** In the veil this frame: the frost on you is renewed - it starts thinning once you are through. */
    private static void linger(long now) {
        lingerAt = now;
    }

    /** How thick the clinging frost is now: 1 in the veil, thinning to 0 over LINGER ticks after it. */
    private static float clinging(double partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || lingerAt == Long.MIN_VALUE) {
            return 0.0F;
        }
        double since = mc.level.getGameTime() - lingerAt + partialTick;
        if (since < 0.0D || since > LINGER) {
            return 0.0F;
        }
        float k = 1.0F - (float) since / LINGER;
        float s = k * k * (3.0F - 2.0F * k);                 // slow to start thinning, slow to finish
        return s * (com.jastkub.frozenfortress.item.LamplighterCandleItem.worn(mc.player) ? CANDLE + 0.2F : 1.0F);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onRenderFog(net.neoforged.neoforge.client.event.ViewportEvent.RenderFog event) {
        float s = clinging((float) event.getPartialTick());
        if (s <= 0.01F) {
            return;
        }
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(),
                net.minecraft.util.Mth.lerp(s, event.getFarPlaneDistance(), LINGER_FAR)));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(),
                net.minecraft.util.Mth.lerp(s, event.getNearPlaneDistance(), LINGER_NEAR)));
        event.setCanceled(true);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onFogColor(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor event) {
        float s = clinging((float) event.getPartialTick());
        if (s <= 0.01F) {
            return;
        }
        event.setRed(net.minecraft.util.Mth.lerp(s, event.getRed(), LR));
        event.setGreen(net.minecraft.util.Mth.lerp(s, event.getGreen(), LG));
        event.setBlue(net.minecraft.util.Mth.lerp(s, event.getBlue(), LB));
    }

    // ------------------------------------------------------------------------------------------------ once a hall
    /** Gates whose veil is being walked through right now (so it does not vanish halfway). */
    private static final java.util.Set<BossGateBlockEntity> MID =
            java.util.Collections.newSetFromMap(new WeakHashMap<>());
    /** The halls this player has gone into, for this world - kept in config/frozen_dominion_veils.txt across restarts. */
    private static java.util.Set<String> passed;
    private static final java.nio.file.Path FILE = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
            .resolve("frozen_dominion_veils.txt");

    private static boolean midPassage(BossGateBlockEntity gate) {
        return MID.contains(gate);
    }

    /** Which world this is (the save's name, or the server's address) and which gate in it. */
    @javax.annotation.Nullable
    private static String passKey(BossGateBlockEntity gate) {
        Minecraft mc = Minecraft.getInstance();
        String world = mc.getSingleplayerServer() != null
                ? "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName()
                : mc.getCurrentServer() != null ? "mp:" + mc.getCurrentServer().ip : null;
        if (world == null || gate.getLevel() == null) {
            return null;
        }
        return world + "|" + gate.getLevel().dimension().location() + "|" + gate.getBlockPos().asLong();
    }

    private static java.util.Set<String> passed() {
        if (passed == null) {
            passed = new java.util.HashSet<>();
            try {
                if (java.nio.file.Files.exists(FILE)) {
                    passed.addAll(java.nio.file.Files.readAllLines(FILE));
                }
            } catch (java.io.IOException ignored) {
                // (no memory of halls: each is veiled once more)
            }
        }
        return passed;
    }

    private static void save() {
        try {
            java.nio.file.Files.write(FILE, passed());
        } catch (java.io.IOException ignored) {
            // (not written: remembered for this session only)
        }
    }

    /** Is its keeper (asleep or awake) alive in its hall? Looked once a second. */
    private static boolean keeperIn(BossGateBlockEntity gate, AABB room) {
        long now = gate.getLevel().getGameTime();
        long[] c = SEEN.computeIfAbsent(gate, g -> new long[]{-100L, 0L});
        if (now - c[0] >= 20L || now < c[0]) {
            c[0] = now;
            c[1] = 0L;
            EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(gate.keeper()));
            if (type != null) {
                for (Entity e : gate.getLevel().getEntities((Entity) null, room.inflate(3.0D), e -> e.getType() == type)) {
                    if (e instanceof LivingEntity l && l.isAlive()) {
                        c[1] = 1L;
                        break;
                    }
                }
            }
        }
        return c[1] == 1L;
    }
}
