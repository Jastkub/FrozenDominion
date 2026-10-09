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
    /** How far into the hall each layer hangs, and how much of what is behind it each takes. (five layers of 0.36; and that night, a keeper plain through it:
     *  the
     *  five, thinning as they went and through a texture three quarters opaque, hid two thirds. Six now, all of 0.6:
     *  about 97% - a shape at most; the Lamplighter's Candle thins it to about 70%.) */
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
        if (room.contains(cam.getPosition())) {
            return;                                          // from inside, the way out is clear
        }
        //
        if (!keeperIn(gate, room) && !gate.prisonerHeld()) {
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
        int light = LevelRenderer.getLightColor(gate.getLevel(), BlockPos.containing(c.x + (alongX ? into : 0), c.y,
                c.z + (alongX ? 0 : into)));
        light = Math.max(light & 0xFFFF, HAZE_LIGHT << 4) | (light & 0xFFFF0000);   // a pale haze even in the dark
        float t = (gate.getLevel().getGameTime() + partialTick) / 20.0F;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEX));
        Matrix4f m = pose.last().pose();
        PoseStack.Pose n = pose.last();
        double y0 = open.minY, y1 = open.maxY;
        for (int i = 0; i < DEPTHS.length; i++) {
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
