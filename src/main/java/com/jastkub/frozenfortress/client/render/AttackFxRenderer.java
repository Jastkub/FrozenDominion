package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.AttackFxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Draws an attack's picture (AttackFxEntity): the model of its kind, turned to the yaw it was cast with (or, a
 * glint, to the eye), at the size it was cast at. Its light is its own (AutoGlowingGeoLayer): the citadel is dark.
 */
public class AttackFxRenderer extends GeoEntityRenderer<AttackFxEntity> {

    /**
     * Kinds with nothing in them that glows. GeckoLib refuses an empty glow mask outright - it took the game down on
     * the drift's snow (06.10.2026, "Invalid glow layer texture provided, must have at least one pixel") - so for
     * these the glow layer is not drawn at all. gen_defender_fx.py checks every mask and names the empty ones.
     */
    private static final java.util.Set<String> NO_GLOW = java.util.Set.of(AttackFxEntity.SNOW_BURST);

    public AttackFxRenderer(EntityRendererProvider.Context context) {
        super(context, new FxModel());
        this.shadowRadius = 0.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(PoseStack poseStack, AttackFxEntity fx, BakedGeoModel bakedModel,
                               net.minecraft.client.renderer.RenderType renderType, MultiBufferSource bufferSource,
                               VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                if (!NO_GLOW.contains(fx.kind())) {
                    super.render(poseStack, fx, bakedModel, renderType, bufferSource, buffer, partialTick,
                            packedLight, packedOverlay);
                }
            }
        });
    }

    @Override
    public void preRender(PoseStack poseStack, AttackFxEntity fx, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {
        if (!isReRender) {        // (a glow layer draws the model again through preRender, on the stack this already turned: once only)
            if (AttackFxEntity.GLINT.equals(fx.kind())) {
                // a star of light is seen face on from wherever you are (the 180 cancels the turn GeckoLib gives
                // every entity that is not alive)
                poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            } else {
                poseStack.mulPose(Axis.YP.rotationDegrees(-fx.getYRot()));
            }
            float s = fx.size();
            poseStack.scale(s, s, s);
        }
        super.preRender(poseStack, fx, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);
    }

    /** Every kind's own model, clip and sheet. */
    static class FxModel extends GeoModel<AttackFxEntity> {
        @Override
        public ResourceLocation getModelResource(AttackFxEntity fx) {
            return FrozenFortress.id("geo/entity/fx_" + fx.kind() + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(AttackFxEntity fx) {
            return FrozenFortress.id("textures/entity/fx_" + fx.kind() + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(AttackFxEntity fx) {
            return FrozenFortress.id("animations/entity/fx_" + fx.kind() + ".animation.json");
        }
    }
}
