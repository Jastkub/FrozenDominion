package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.ThroneBaneWaveEntity;
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
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Zmora Tronu's crescents (ThroneBaneWaveEntity): the model of their kind (fx_throne_bane_crescent / _slash,
 * tools/gen_throne_bane.py), turned to the way they fly and tilted with the swing, through its own light (the ice
 * translucent and lit from within; its white-hot edge and runes on the glow layer). It flares up out of nothing over
 * its first two ticks and thins and fades over its last three.
 *
 * <p>Turned and sized only on the first pass (the glow layer draws it again through preRender, on this same stack).
 */
public class ThroneBaneWaveRenderer extends GeoEntityRenderer<ThroneBaneWaveEntity> {

    public ThroneBaneWaveRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new WaveModel());
        this.shadowRadius = 0.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void preRender(PoseStack poseStack, ThroneBaneWaveEntity wave, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay, int colour) {
        if (!isReRender) {
            float t = wave.tickCount + partialTick;
            float grow = Mth.clamp(t / 2.0F, 0.0F, 1.0F);
            float fade = Mth.clamp((wave.life() - t) / 3.0F, 0.0F, 1.0F);
            float s = (0.45F + 0.55F * grow) * (wave.empowered() ? 1.25F : 1.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(-wave.waveYaw()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(wave.roll()));
            poseStack.scale(s * (1.0F + 0.1F * (1.0F - fade)), s * (0.25F + 0.75F * fade), s);
        }
        super.preRender(poseStack, wave, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, colour);
    }

    @Override
    public RenderType getRenderType(ThroneBaneWaveEntity wave, ResourceLocation texture, MultiBufferSource bufferSource,
                                    float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    public Color getRenderColor(ThroneBaneWaveEntity wave, float partialTick, int packedLight) {
        float t = wave.tickCount + partialTick;
        float fade = Mth.clamp((wave.life() - t) / 3.0F, 0.0F, 1.0F);
        return Color.ofRGBA(1.0F, 1.0F, 1.0F, 0.9F * fade);
    }

    /** Each kind its own model, sheet and clip. */
    static class WaveModel extends GeoModel<ThroneBaneWaveEntity> {
        private static String name(ThroneBaneWaveEntity w) {
            return w.kind() == ThroneBaneWaveEntity.SLASH ? "fx_throne_bane_slash" : "fx_throne_bane_crescent";
        }

        @Override
        public ResourceLocation getModelResource(ThroneBaneWaveEntity w) {
            return FrozenFortress.id("geo/entity/" + name(w) + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(ThroneBaneWaveEntity w) {
            return FrozenFortress.id("textures/entity/" + name(w) + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(ThroneBaneWaveEntity w) {
            return FrozenFortress.id("animations/entity/" + name(w) + ".animation.json");
        }
    }
}
