package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.RimeBindEntity;
import com.jastkub.frozenfortress.entity.projectile.RimeShuttleEntity;
import com.jastkub.frozenfortress.entity.projectile.RimeThornEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The Rimeweaver's own things: its shuttle, its thorns, its binding (tools/gen_defender_fx.py). */
public final class RimeweaverFxRenderers {

    private RimeweaverFxRenderers() {
    }

    /** The shuttle points where it flies, rolls about that line, and pays out its thread of frost behind it. */
    public static class Shuttle extends GeoEntityRenderer<RimeShuttleEntity> {
        private static final ResourceLocation THREAD = FrozenFortress.id("textures/entity/ice_trail.png");

        public Shuttle(EntityRendererProvider.Context context) {
            super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("rime_shuttle"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void render(RimeShuttleEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                           MultiBufferSource bufferSource, int packedLight) {
            ProjectileTrail.draw(entity, poseStack, bufferSource, THREAD, partialTick, 0.12F, 24, 0.80F, 0.95F, 1.0F);
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        }

        @Override
        public void preRender(PoseStack poseStack, RimeShuttleEntity shuttle, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                              float partialTick, int packedLight, int packedOverlay, float red, float green,
                              float blue, float alpha) {
            Vec3 v = shuttle.getDeltaMovement();
            double h = Math.sqrt(v.x * v.x + v.z * v.z);
            if (!isReRender) {    // (a glow layer draws the model again through preRender, on the stack this already turned: once only)
                if (v.lengthSqr() > 1.0E-6D) {
                    // its point (-z) along the flight: the turn about y, then the lift about x; the last 180
                    // undoes the turn GeckoLib gives a thing that is not alive
                    poseStack.mulPose(Axis.YP.rotation((float) Math.atan2(-v.x, -v.z)));
                    poseStack.mulPose(Axis.XP.rotation((float) Math.atan2(v.y, h)));
                }
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            super.preRender(poseStack, shuttle, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, red, green, blue, alpha);
        }
    }

    /** The knot on the floor while it waits (its warn clip), the thorns when it comes. */
    public static class Thorn extends GeoEntityRenderer<RimeThornEntity> {
        public Thorn(EntityRendererProvider.Context context) {
            super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("rime_thorn"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    /** Cut to its prisoner like the ice prison, and thinner as it is broken. */
    public static class Bind extends GeoEntityRenderer<RimeBindEntity> {
        public Bind(EntityRendererProvider.Context context) {
            super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("rime_bind"), false));
            this.shadowRadius = 0.4F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, RimeBindEntity bind, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                              float partialTick, int packedLight, int packedOverlay, float red, float green,
                              float blue, float alpha) {
            if (!isReRender) {    // (a glow layer draws the model again through preRender, on the stack this already turned: once only)
                poseStack.scale(bind.fitWidth(), bind.fitHeight(), bind.fitWidth());
            }
            super.preRender(poseStack, bind, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, red, green, blue, alpha);
        }

        @Override
        public Color getRenderColor(RimeBindEntity bind, float partialTick, int packedLight) {
            return Color.ofRGBA(0.85F, 0.95F, 1.0F, 1.0F - 0.5F * bind.shellDamage());
        }

        @Override
        public RenderType getRenderType(RimeBindEntity bind, ResourceLocation texture, MultiBufferSource bufferSource,
                                        float partialTick) {
            return RenderType.entityTranslucent(texture);
        }
    }
}
