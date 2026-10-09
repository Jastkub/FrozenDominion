package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.LamplighterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The Lamplighter, and the light he keeps.
 *
 * <p>THE BEAM is drawn from the great lantern, not from him: a sheet of light from the lantern down
 * to the floor across the beam's width, and the floor it lights. Each of its rays is cast against the
 * blocks on the way, so a pier stops it - the beam breaks round the piers and leaves their shadows
 * dark behind them, which is the whole fight (the server tests the same straight line).
 *
 * <p>While the lantern's light is on him he wears it: a pale gold shell over the whole of him (he
 * takes a quarter of any blow). In a pier's shadow it goes out.
 *
 * <p>THE SHUTTER: for a moment after he throws his lantern open, a cone of white light out of it.
 */
public class LamplighterRenderer extends FrostGeoRenderer<LamplighterEntity> {

    private static final int RAYS = 12;
    private static final int FLASH_TICKS = 8;

    public LamplighterRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("lamplighter"), true));
        this.shadowRadius = 0.9F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        addRenderLayer(new LightShell(this));
    }

    /** The beam sweeps the dome when he is off the screen: draw him (it) while it is on and near. */
    @Override
    public boolean shouldRender(LamplighterEntity e, Frustum frustum, double x, double y, double z) {
        if (e.beamMode() != LamplighterEntity.BEAM_OFF && !e.isDeadOrDying()
                && e.distanceToSqr(x, y, z) < 64.0D * 64.0D) {
            return true;
        }
        return super.shouldRender(e, frustum, x, y, z);
    }

    @Override
    public void render(LamplighterEntity e, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int packedLight) {
        super.render(e, yaw, partialTick, poses, buffers, packedLight);
        Vec3 at = new Vec3(Mth.lerp(partialTick, e.xo, e.getX()), Mth.lerp(partialTick, e.yo, e.getY()),
                Mth.lerp(partialTick, e.zo, e.getZ()));
        // the great lantern draws its own beam (GreatLanternRenderer); one hatched from an egg has none
        if (e.beamMode() != LamplighterEntity.BEAM_OFF && !e.isDeadOrDying() && !e.level().getBlockState(
                net.minecraft.core.BlockPos.containing(e.lantern())).is(com.jastkub.frozenfortress.registry.FFBlocks.GREAT_LANTERN.get())) {
            beams(e, at, partialTick, poses, buffers);
        }
        float since = e.level().getGameTime() + partialTick - e.flashAt;
        if (since >= 0.0F && since < FLASH_TICKS && !e.isDeadOrDying()) {
            flash(e, at, 1.0F - since / FLASH_TICKS, poses, buffers);
        }
        if (e.handLit() && !e.isDeadOrDying()) {
            handPool(e, partialTick, poses, buffers);
        }
    }

    // ------------------------------------------------------------------ his own lantern
    /**
     * HIS OWN LANTERN OPEN (LamplighterEntity.OPEN): the pool of its light on the floor round him, out to its reach
     * (HAND_R) with a bright rim at the edge - where you stand in it or not is plain. It flickers as it gutters.
     */
    private static void handPool(LamplighterEntity e, float pt, PoseStack poses, MultiBufferSource buffers) {
        float left = e.handLeft(pt);
        float k = left < 30.0F ? 0.55F + 0.45F * Mth.sin((e.tickCount + pt) * 1.4F) : 1.0F;
        k *= Math.min(1.0F, (LamplighterEntity.HAND_T - left) / 6.0F);           // (it spreads as it opens)
        float r = (float) LamplighterEntity.HAND_R, rim = r - 0.45F, y = 0.06F;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = poses.last().pose();
        int n = 40;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            for (int side = 0; side < 2; side++) {
                // the pool: bright at his feet, fading toward the rim
                pool(m, vc, side, 0.0F, y, 0.0F, 0.16F * k, rim * c0, y, rim * s0, rim * c1, y, rim * s1, 0.05F * k);
                // the rim
                ring(m, vc, side, rim * c0, rim * s0, rim * c1, rim * s1, r * c0, r * s0, r * c1, r * s1, y,
                        0.32F * k, 0.0F);
            }
        }
    }

    private static void pool(Matrix4f m, VertexConsumer vc, int side, float ax, float ay, float az, float aa, float bx,
                             float by, float bz, float cx, float cy, float cz, float edge) {
        float[][] p = side == 0 ? new float[][]{{bx, by, bz}, {cx, cy, cz}} : new float[][]{{cx, cy, cz}, {bx, by, bz}};
        vc.vertex(m, ax, ay, az).color(1.0F, 0.92F, 0.70F, aa).endVertex();
        vc.vertex(m, p[0][0], p[0][1], p[0][2]).color(1.0F, 0.88F, 0.62F, edge).endVertex();
        vc.vertex(m, p[1][0], p[1][1], p[1][2]).color(1.0F, 0.88F, 0.62F, edge).endVertex();
        vc.vertex(m, p[1][0], p[1][1], p[1][2]).color(1.0F, 0.88F, 0.62F, edge).endVertex();
    }

    private static void ring(Matrix4f m, VertexConsumer vc, int side, float ix0, float iz0, float ix1, float iz1,
                             float ox0, float oz0, float ox1, float oz1, float y, float inner, float outer) {
        float[][] q = {{ix0, iz0, inner}, {ox0, oz0, outer}, {ox1, oz1, outer}, {ix1, iz1, inner}};
        for (int j = 0; j < 4; j++) {
            float[] v = q[side == 0 ? j : 3 - j];
            vc.vertex(m, v[0], y, v[1]).color(1.0F, 0.95F, 0.78F, v[2]).endVertex();
        }
    }

    // ------------------------------------------------------------------ the beam
    /** The beam(s) of `e`'s lantern, the pose stack standing at `at` in the world. */
    static void beams(LamplighterEntity e, Vec3 at, float pt, PoseStack poses, MultiBufferSource buffers) {
        Vec3 l = e.lantern();
        if (l.lengthSqr() < 1.0E-4D) {
            return;
        }
        double floor = e.getY() + 0.03D;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        poses.pushPose();
        poses.translate(l.x - at.x, l.y - at.y, l.z - at.z);
        Matrix4f m = poses.last().pose();
        float a = e.beamAngle(pt);
        float flicker = 0.92F + 0.08F * Mth.sin((e.tickCount + pt) * 0.7F);
        sweep(e, l, floor, a, m, vc, flicker);
        if (e.beamMode() == LamplighterEntity.BEAM_TWO) {
            sweep(e, l, floor, a + 180.0F, m, vc, flicker);
        }
        poses.popPose();
    }

    /** One beam at angle `a` (yaw convention): its sheet from the lantern and the floor it lights. */
    private static void sweep(LamplighterEntity e, Vec3 l, double floor, float a, Matrix4f m, VertexConsumer vc, float k) {
        Level level = e.level();
        Vec3[] hit = new Vec3[RAYS + 1];
        boolean[] onFloor = new boolean[RAYS + 1];
        for (int i = 0; i <= RAYS; i++) {
            float ang = (a - LamplighterEntity.BEAM_HALF + 2.0F * LamplighterEntity.BEAM_HALF * i / RAYS) * Mth.DEG_TO_RAD;
            double dx = -Mth.sin(ang), dz = Mth.cos(ang);
            // how far the floor goes that way (the drum's wall), then the ray from the lantern to its foot
            Vec3 low = new Vec3(l.x, floor + 0.5D, l.z);
            HitResult wall = level.clip(new ClipContext(low, low.add(dx * LamplighterEntity.REACH, 0.0D,
                    dz * LamplighterEntity.REACH), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            double reach = wall.getType() == HitResult.Type.MISS ? LamplighterEntity.REACH
                    : Math.max(1.0D, wall.getLocation().subtract(low).horizontalDistance() - 0.05D);
            Vec3 foot = new Vec3(l.x + dx * reach, floor, l.z + dz * reach);
            Vec3 from = l.add(dx * 1.6D, -1.2D, dz * 1.6D);         // out of the lantern's cage
            HitResult ray = level.clip(new ClipContext(from, foot, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            onFloor[i] = ray.getType() == HitResult.Type.MISS || ray.getLocation().y < floor + 0.3D;
            hit[i] = (ray.getType() == HitResult.Type.MISS ? foot : ray.getLocation()).subtract(l);
        }
        Vec3 apex = Vec3.ZERO;
        Vec3 under = new Vec3(0.0D, floor - l.y, 0.0D);
        for (int i = 0; i < RAYS; i++) {
            float edge = 1.0F - Math.abs(i + 0.5F - RAYS / 2.0F) / (RAYS / 2.0F);   // brighter at its heart
            float sheet = (0.10F + 0.16F * edge) * k;
            // the sheet, both faces
            tri(m, vc, apex, hit[i], hit[i + 1], sheet * 1.6F, sheet * 0.7F);
            tri(m, vc, apex, hit[i + 1], hit[i], sheet * 1.6F, sheet * 0.7F);
            // the floor it lights (where the ray reached it - not behind a pier)
            if (onFloor[i] && onFloor[i + 1]) {
                Vec3 p = new Vec3(hit[i].x, under.y, hit[i].z), q = new Vec3(hit[i + 1].x, under.y, hit[i + 1].z);
                float pool = (0.10F + 0.14F * edge) * k;
                tri(m, vc, under, q, p, pool * 0.5F, pool);
            }
        }
    }

    /** A triangle from `a` (alpha a0) to b, c (alpha a1), warm white, drawn as a quad (c twice). */
    private static void tri(Matrix4f m, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, float a0, float a1) {
        vc.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(1.0F, 0.93F, 0.74F, a0).endVertex();
        vc.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(1.0F, 0.90F, 0.66F, a1).endVertex();
        vc.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(1.0F, 0.90F, 0.66F, a1).endVertex();
        vc.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(1.0F, 0.90F, 0.66F, a1).endVertex();
    }

    // ------------------------------------------------------------------ the shutter
    private static void flash(LamplighterEntity e, Vec3 at, float k, PoseStack poses, MultiBufferSource buffers) {
        Vec3 src = e.handLantern().subtract(e.position());
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        poses.pushPose();
        poses.translate(src.x, src.y, src.z);
        Matrix4f m = poses.last().pose();
        float yaw = e.getYRot() * Mth.DEG_TO_RAD;
        float len = 12.0F * (0.55F + 0.45F * (1.0F - k * k));
        for (float pitch : new float[]{-18.0F, -6.0F, 6.0F}) {
            float p = pitch * Mth.DEG_TO_RAD;
            Vec3 prev = null;
            for (int i = 0; i <= 10; i++) {
                float s = yaw + (-36.0F + 7.2F * i) * Mth.DEG_TO_RAD;
                Vec3 d = new Vec3(-Mth.sin(s) * Mth.cos(p), Mth.sin(p), Mth.cos(s) * Mth.cos(p)).scale(len);
                if (prev != null) {
                    tri(m, vc, Vec3.ZERO, prev, d, 0.55F * k, 0.04F * k);
                    tri(m, vc, Vec3.ZERO, d, prev, 0.55F * k, 0.04F * k);
                }
                prev = d;
            }
        }
        poses.popPose();
    }

    /** His light: a pale gold shell over him while the lantern's light is on him. */
    static class LightShell extends GeoRenderLayer<LamplighterEntity> {
        private static final ResourceLocation SHELL = FrozenFortress.id("textures/entity/lamplighter_light.png");

        LightShell(GeoRenderer<LamplighterEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poses, LamplighterEntity e, BakedGeoModel model, RenderType type,
                           MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight,
                           int packedOverlay) {
            if (e.isDormant() || e.isShaded() || e.isDeadOrDying()) {
                return;
            }
            float t = e.tickCount + partialTick;
            float alpha = 0.22F + 0.10F * Mth.sin(t * 0.15F);
            poses.pushPose();
            poses.translate(0.0D, e.getBbHeight() * 0.5D, 0.0D);
            poses.scale(1.05F, 1.03F, 1.05F);
            poses.translate(0.0D, -e.getBbHeight() * 0.5D, 0.0D);
            RenderType shell = RenderType.entityTranslucentEmissive(SHELL);
            getRenderer().reRender(model, poses, buffers, e, shell, buffers.getBuffer(shell), partialTick,
                    0xF000F0, packedOverlay, 1.0F, 0.88F, 0.6F, alpha);
            poses.popPose();
        }
    }
}
