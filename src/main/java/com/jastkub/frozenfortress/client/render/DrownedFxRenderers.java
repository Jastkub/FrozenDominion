package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.DrownedHandsEntity;
import com.jastkub.frozenfortress.entity.DrownedIcePlateEntity;
import com.jastkub.frozenfortress.entity.DrownedShadowEntity;
import com.jastkub.frozenfortress.entity.DrownedTideEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.util.Color;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The shapes the Drowned Lady's fight is drawn with (tools/gen_drowned_lady.py) - each a model of its own with its own
 * light (the glow layers: the cracks in the ice, the runes, her eyes under it, the crest of her tide).
 *
 * <p>TURNS ONLY ON THE FIRST PASS. GeckoLib calls preRender again for every re-render a layer asks for (the glow is
 * one), on a pose stack that already carries the first pass's transforms - so anything turned or scaled there without
 * minding isReRender is turned twice in its glow. Everything here that turns does it under !isReRender.
 */
public final class DrownedFxRenderers {

    private DrownedFxRenderers() {
    }

    /** A plate of the floor: the breakable one, or the thick one with the rune. Square, so never turned. */
    public static class Plate extends GeoEntityRenderer<DrownedIcePlateEntity> {
        public Plate(EntityRendererProvider.Context ctx) {
            super(ctx, new PlateModel());
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    static class PlateModel extends GeoModel<DrownedIcePlateEntity> {
        private static String kind(DrownedIcePlateEntity p) {
            return p.isRune() ? "drowned_rune_plate" : "drowned_ice_plate";
        }

        @Override
        public ResourceLocation getModelResource(DrownedIcePlateEntity p) {
            return FrozenFortress.id("geo/entity/" + kind(p) + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(DrownedIcePlateEntity p) {
            return FrozenFortress.id("textures/entity/" + kind(p) + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(DrownedIcePlateEntity p) {
            return FrozenFortress.id("animations/entity/" + kind(p) + ".animation.json");
        }
    }

    /**
     * Her shape under the ice: dark and a little see-through, as a thing seen through ice is - turned the way it
     * glides. Its eyes light themselves.
     */
    public static class Shadow extends GeoEntityRenderer<DrownedShadowEntity> {
        /** a
         *  smudge under the ice now: mostly see-through and dark, its eyes only a dull gleam. */
        static final float SEEN = 0.3F, TONE = 0.55F, EYES = 0.35F;

        public Shadow(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("drowned_shadow"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this) {
                @Override
                public void render(PoseStack poseStack, DrownedShadowEntity animatable, BakedGeoModel bakedModel,
                                   RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                   float partialTick, int packedLight, int packedOverlay) {
                    RenderType glow = getRenderType(animatable);
                    getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow,
                            bufferSource.getBuffer(glow), partialTick, 15728640,
                            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, com.jastkub.frozenfortress.util.FFColor.argb(EYES, EYES, EYES, 1.0F));
                }
            });
        }

        @Override
        public void preRender(PoseStack poseStack, DrownedShadowEntity shadow, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                              float partialTick, int packedLight, int packedOverlay,
                              int colour) {
            if (!isReRender) {
                poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, shadow.yRotO, shadow.getYRot())));
            }
            super.preRender(poseStack, shadow, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, colour);
        }

        @Override
        public RenderType getRenderType(DrownedShadowEntity shadow, ResourceLocation texture,
                                        MultiBufferSource bufferSource, float partialTick) {
            return RenderType.entityTranslucent(texture);
        }

        @Override
        public Color getRenderColor(DrownedShadowEntity shadow, float partialTick, int packedLight) {
            return Color.ofRGBA(TONE, TONE * 1.05F, TONE * 1.2F, SEEN);
        }
    }

    /** Her hands up through the ice: a living thing, turned by GeckoLib like any; a fifth bigger than her own. */
    public static class Hands extends GeoEntityRenderer<DrownedHandsEntity> {
        public Hands(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("drowned_hands"), false));
            this.shadowRadius = 0.0F;
            withScale(1.2F);
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        protected float getDeathMaxRotation(DrownedHandsEntity hands) {
            return 0.0F;
        }
    }

    /** The black tide: a ring round where she stood - round, so never turned. */
    public static class Tide extends GeoEntityRenderer<DrownedTideEntity> {
        public Tide(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("drowned_tide"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }
}
