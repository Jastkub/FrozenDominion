package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.HunterOrbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The seeker, with its fuse drawn on it.
 *
 * <p>Once attached it goes from the cold blue everything else in this fight is
 * to a hot white over the second and a half before it opens, and it swells
 * while it does. That ramp is the only warning anybody gets: the player it is
 * riding cannot see it at all, so it is really for their allies and for their
 * own decision about whether to keep running or turn and smash it. A fuse
 * nobody can read is just a delay.
 */
public class HunterOrbRenderer extends GeoEntityRenderer<HunterOrbEntity> {

    public HunterOrbRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("ice_orb"), false));
        this.shadowRadius = 0.0F;
    }

    @Override
    public void preRender(PoseStack poseStack, HunterOrbEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {
        // PUSHED THROUGH, not faded in: it comes out stretched along one axis
        // and settles into a sphere, which reads as something arriving from
        // elsewhere rather than as an object being turned up in opacity.
        float born = animatable.birth();
        if (born < 1.0F) {
            float ease = born * born * (3.0F - 2.0F * born);
            poseStack.scale(ease, 0.15F + 0.85F * ease, ease);
        }
        float charge = animatable.charge();
        if (charge > 0.0F) {
            // it grows as it fills, and the growth accelerates - a linear
            // swell reads as a balloon, a curved one reads as pressure
            float swell = 1.0F + charge * charge * 0.8F;
            poseStack.scale(swell, swell, swell);
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public Color getRenderColor(HunterOrbEntity animatable, float partialTick, int packedLight) {
        float charge = animatable.charge();
        // Blue to white, with GREEN leading red so the ramp passes through a
        // cyan rather than through a grey. A grey midpoint on an ice effect
        // reads as the texture failing to load.
        float r = 0.52F + 0.48F * charge;
        float g = 0.78F + 0.22F * Math.min(1.0F, charge * 1.7F);
        return Color.ofRGBA(r, g, 1.0F, 1.0F);
    }

    @Override
    public RenderType getRenderType(HunterOrbEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }
}
