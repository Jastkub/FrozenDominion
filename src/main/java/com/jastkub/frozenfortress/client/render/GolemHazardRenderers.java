package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.FrostPuddleEntity;
import com.jastkub.frozenfortress.entity.projectile.FallingIcicleEntity;
import com.jastkub.frozenfortress.entity.projectile.IceJavelinEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * What the Monstrosity's new attacks leave in the world, drawn as geometry: its
 * javelin (flying tip-first, then standing as a pillar), the avalanche's icicle
 * with its shadow on the floor, and the geyser's pool.
 */
public final class GolemHazardRenderers {

    private static final ResourceLocation RING = FrozenFortress.id("textures/entity/frost_wave.png");
    private static final ResourceLocation POOL = FrozenFortress.id("textures/entity/frost_puddle.png");

    private GolemHazardRenderers() {
    }

    /** A flat ring on the floor (a shadow, a warning), radius r, width w. */
    static void ring(PoseStack poseStack, MultiBufferSource buffers, float r, float w, float alpha) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(RING));
        Matrix4f m = poseStack.last().pose();
        PoseStack.Pose n = poseStack.last();
        int seg = 40;
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (Math.PI * 2.0D * i / seg), a1 = (float) (Math.PI * 2.0D * (i + 1) / seg);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float ri = Math.max(0.0F, r - w);
            put(vc, m, n, c0 * ri, s0 * ri, i / 4.0F, 1.0F, alpha * 0.4F);
            put(vc, m, n, c1 * ri, s1 * ri, (i + 1) / 4.0F, 1.0F, alpha * 0.4F);
            put(vc, m, n, c1 * r, s1 * r, (i + 1) / 4.0F, 0.5F, alpha);
            put(vc, m, n, c0 * r, s0 * r, i / 4.0F, 0.5F, alpha);
        }
    }

    private static void put(VertexConsumer vc, Matrix4f m, PoseStack.Pose n, float x, float z, float u, float v, float alpha) {
        vc.addVertex(m, x, 0.03F, z).setColor(0.86F, 0.96F, 1.0F, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(n, 0.0F, 1.0F, 0.0F);
    }

    private static final ResourceLocation GLOW = FrozenFortress.id("textures/entity/golem_orb_glow.png");

    /** A soft round light turned to the camera (the poseStack already at its centre). */
    static void glow(PoseStack poseStack, MultiBufferSource buffers, Quaternionf camera, float size, float alpha,
                     float spin) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
        poseStack.pushPose();
        poseStack.mulPose(camera);
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(spin));
        Matrix4f m = poseStack.last().pose();
        PoseStack.Pose n = poseStack.last();
        float h = size / 2.0F;
        float[][] c = {{-h, -h, 0, 1}, {h, -h, 1, 1}, {h, h, 1, 0}, {-h, h, 0, 0}};
        for (float[] p : c) {
            vc.addVertex(m, p[0], p[1], 0.0F).setColor(1.0F, 1.0F, 1.0F, alpha).setUv(p[2], p[3])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(n, 0.0F, 1.0F, 0.0F);
        }
        poseStack.popPose();
    }

    /** A thin streak of light from `from` to `to` (both relative to the poseStack), facing the camera. */
    static void streak(PoseStack poseStack, MultiBufferSource buffers, Vec3 cam, Vec3 from, Vec3 to, float width,
                       float alpha) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
        Vec3 d = to.subtract(from);
        Vec3 side = d.cross(cam.subtract(from)).normalize().scale(width / 2.0D);
        Matrix4f m = poseStack.last().pose();
        PoseStack.Pose n = poseStack.last();
        Vec3[] q = {from.subtract(side), from.add(side), to.add(side), to.subtract(side)};
        float[][] uv = {{0.5F, 0.0F}, {0.5F, 1.0F}, {0.5F, 1.0F}, {0.5F, 0.0F}};
        for (int i = 0; i < 4; i++) {
            vc.addVertex(m, (float) q[i].x, (float) q[i].y, (float) q[i].z).setColor(0.8F, 0.95F, 1.0F, alpha)
                    .setUv(i < 2 ? 0.48F : 0.5F, uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(n, 0.0F, 1.0F, 0.0F);
        }
    }

    /** The Monstrosity's bomb: its crystal, turning, in a halo of its own light (every one of them). */
    public static class Bomb extends GeoEntityRenderer<com.jastkub.frozenfortress.entity.projectile.IceBombEntity> {
        public Bomb(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("ice_bomb"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            withScale(1.6F);
        }

        @Override
        protected void applyRotations(com.jastkub.frozenfortress.entity.projectile.IceBombEntity bomb, PoseStack poseStack,
                                      float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
            float t = bomb.tickCount + partialTick;
            poseStack.translate(0.0D, 0.25D, 0.0D);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(bomb.spin() + t * 14.0F));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(t * 9.0F));
            poseStack.translate(0.0D, -0.25D, 0.0D);
        }

        @Override
        public void render(com.jastkub.frozenfortress.entity.projectile.IceBombEntity bomb, float yaw, float partialTick,
                           PoseStack poseStack, MultiBufferSource buffers, int light) {
            super.render(bomb, yaw, partialTick, poseStack, buffers, light);
            float t = bomb.tickCount + partialTick;
            poseStack.pushPose();
            poseStack.translate(0.0D, 0.4D, 0.0D);
            glow(poseStack, buffers, this.entityRenderDispatcher.cameraOrientation(), 2.6F + 0.3F * Mth.sin(t * 0.6F), 0.55F, t * 0.05F);
            glow(poseStack, buffers, this.entityRenderDispatcher.cameraOrientation(), 1.1F, 0.95F, -t * 0.08F);
            poseStack.popPose();
        }
    }

    /**
     * A gout of the trough's water: the blob leading, its drops trailing, wobbling as it flies -
     * and its shadow on the floor where it will come down, tightening as it falls.
     */
    public static class Glob extends GeoEntityRenderer<com.jastkub.frozenfortress.entity.projectile.FrostGlobEntity> {
        public Glob(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("frost_glob"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            withScale(1.5F);
        }

        @Override
        protected void applyRotations(com.jastkub.frozenfortress.entity.projectile.FrostGlobEntity glob, PoseStack poseStack,
                                      float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
            Vec3 h = glob.heading(partialTick);
            poseStack.translate(0.0D, 0.3D, 0.0D);
            if (h.lengthSqr() > 1.0E-6D) {
                Vec3 d = h.normalize();
                poseStack.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) d.x, (float) d.y, (float) d.z));
            }
            float t = glob.tickCount + partialTick;
            float w = 1.0F + 0.12F * Mth.sin(t * 0.9F);                // it wobbles: squash, stretch
            poseStack.scale(w, 1.15F / w, w);
        }

        @Override
        public RenderType getRenderType(com.jastkub.frozenfortress.entity.projectile.FrostGlobEntity glob,
                                        ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
            return RenderType.entityTranslucent(texture);
        }

        @Override
        public void render(com.jastkub.frozenfortress.entity.projectile.FrostGlobEntity glob, float yaw, float partialTick,
                           PoseStack poseStack, MultiBufferSource buffers, int light) {
            super.render(glob, yaw, partialTick, poseStack, buffers, light);
            Vec3 at = glob.target();
            Vec3 here = glob.getPosition(partialTick);
            float k = glob.progress(partialTick);
            poseStack.pushPose();
            poseStack.translate(at.x - here.x, at.y - here.y + 0.02D, at.z - here.z);
            ring(poseStack, buffers, 2.3F * (1.6F - 0.6F * k), 0.3F + 0.25F * k, 0.25F + 0.6F * k);
            poseStack.popPose();
        }
    }

    /** A shard of a bomb's shell, tumbling. */
    public static class Fragment extends GeoEntityRenderer<com.jastkub.frozenfortress.entity.projectile.IceBombEntity.IceFragmentEntity> {
        public Fragment(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("ice_javelin"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            withScale(0.2F);
        }

        @Override
        protected void applyRotations(com.jastkub.frozenfortress.entity.projectile.IceBombEntity.IceFragmentEntity f,
                                      PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
            float t = f.tickCount + partialTick;
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(f.getId() * 61.0F % 360.0F + t * 20.0F));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(t * 31.0F));
        }
    }

    /** The javelin: its model turned to lie back along the line it flew, tip at its position. */
    public static class Javelin extends GeoEntityRenderer<IceJavelinEntity> {
        public Javelin(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("ice_javelin"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        protected void applyRotations(IceJavelinEntity javelin, PoseStack poseStack, float ageInTicks,
                                      float rotationYaw, float partialTick, float nativeScale) {
            Vec3 aim = Vec3.directionFromRotation(javelin.getViewXRot(partialTick), javelin.getViewYRot(partialTick));
            poseStack.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F,
                    (float) -aim.x, (float) -aim.y, (float) -aim.z));
        }
    }

    /** The falling icicle: tip down, and its shadow tightening on the floor until it drops. */
    public static class Icicle extends GeoEntityRenderer<FallingIcicleEntity> {
        public Icicle(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("ice_javelin"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        protected void applyRotations(FallingIcicleEntity icicle, PoseStack poseStack, float ageInTicks,
                                      float rotationYaw, float partialTick, float nativeScale) {
            // hanging, it shudders - a little at first, then hard, then it lets go
            float sh = icicle.shudder(partialTick);
            if (sh > 0.0F) {
                float t = icicle.tickCount + partialTick;
                float a = sh * sh * 4.0F;
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(Mth.sin(t * 2.7F) * a));
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(Mth.cos(t * 3.3F) * a));
            }
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(icicle.getId() * 47.0F % 360.0F));
            float k = icicle.modelScale();
            poseStack.scale(k, k, k);
        }

        @Override
        public void render(FallingIcicleEntity icicle, float yaw, float partialTick, PoseStack poseStack,
                           MultiBufferSource buffers, int light) {
            super.render(icicle, yaw, partialTick, poseStack, buffers, light);
            float k = icicle.warn(partialTick);
            double drop = Mth.lerp(partialTick, icicle.yOld, icicle.getY()) - icicle.floorY();
            poseStack.pushPose();
            poseStack.translate(0.0D, -drop, 0.0D);
            float r = FallingIcicleEntity.RADIUS * (1.7F - 0.7F * k);
            ring(poseStack, buffers, r, 0.35F + 0.25F * k, 0.3F + 0.6F * k);
            poseStack.popPose();
        }
    }

    /** The geyser's pool: a warning ring until the water lands, then a disc of black ice. */
    public static class Puddle extends EntityRenderer<FrostPuddleEntity> {
        public Puddle(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0.0F;
        }

        @Override
        public ResourceLocation getTextureLocation(FrostPuddleEntity puddle) {
            return POOL;
        }

        @Override
        public void render(FrostPuddleEntity puddle, float yaw, float partialTick, PoseStack poseStack,
                           MultiBufferSource buffers, int light) {
            float t = puddle.tickCount + partialTick;
            float size = puddle.radius();
            if (!puddle.landed(partialTick)) {
                float k = t / Math.max(1, puddle.warnTicks());
                ring(poseStack, buffers, size * (0.4F + 0.6F * k), 0.4F, 0.25F + 0.6F * k);
                return;
            }
            float life = t - puddle.warnTicks();
            float alpha = Mth.clamp(Math.min(life / 6.0F, (FrostPuddleEntity.LIE - life) / 20.0F), 0.0F, 1.0F);
            float r = size * Mth.clamp(0.6F + life / 8.0F, 0.0F, 1.0F);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(POOL));
            Matrix4f m = poseStack.last().pose();
            PoseStack.Pose n = poseStack.last();
            int seg = 32;
            for (int i = 0; i < seg; i++) {
                float a0 = (float) (Math.PI * 2.0D * i / seg), a1 = (float) (Math.PI * 2.0D * (i + 1) / seg);
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                // a fan of quads (the centre doubled), its texture mapped across the disc
                disc(vc, m, n, 0.0F, 0.0F, r, alpha);
                disc(vc, m, n, 0.0F, 0.0F, r, alpha);
                disc(vc, m, n, c1 * r, s1 * r, r, alpha);
                disc(vc, m, n, c0 * r, s0 * r, r, alpha);
            }
        }

        private void disc(VertexConsumer vc, Matrix4f m, PoseStack.Pose n, float x, float z, float r, float alpha) {
            float u = 0.5F + x / (Math.max(0.01F, r) * 2.0F), v = 0.5F + z / (Math.max(0.01F, r) * 2.0F);
            vc.addVertex(m, x, 0.02F, z).setColor(1.0F, 1.0F, 1.0F, alpha * 0.92F).setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(n, 0.0F, 1.0F, 0.0F);
        }
    }
}
