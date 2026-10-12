package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.util.Color;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Base renderer for everything in the fortress that dies with a hand-authored
 * death animation. Vanilla flips a corpse onto its side, which would hide the
 * animation entirely - here the body stays upright and does its own falling.
 *
 * <p>THE BODY FADES (FrostServantEntity.fadeTicks): over the last ticks of its dying it is drawn ever fainter, until
 * nothing is left where it lay - and what lit it (its eyes, its ice: the glowing layers) goes out the moment it dies.
 */
public class FrostGeoRenderer<T extends LivingEntity & GeoAnimatable> extends GeoEntityRenderer<T> {

    public FrostGeoRenderer(EntityRendererProvider.Context context, GeoModel<T> model) {
        super(context, model);
    }

    @Override
    protected float getDeathMaxRotation(T animatable) {
        return 0.0F;
    }

    /** How much of it is still there: 1 alive and through most of its dying, falling to 0 over its fade. */
    public static float presence(LivingEntity e, float partialTick) {
        if (!(e instanceof FrostServantEntity s) || s.deathTime <= 0 || s.fadeTicks() <= 0) {
            return 1.0F;
        }
        float left = s.deathDuration() - (s.deathTime + partialTick);
        return Mth.clamp(left / s.fadeTicks(), 0.0F, 1.0F);
    }

    @Override
    public Color getRenderColor(T animatable, float partialTick, int packedLight) {
        float a = presence(animatable, partialTick);
        return a >= 1.0F ? super.getRenderColor(animatable, partialTick, packedLight) : Color.ofRGBA(1.0F, 1.0F, 1.0F, a);
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, MultiBufferSource bufferSource,
                                    float partialTick) {
        return presence(animatable, partialTick) < 1.0F ? RenderType.entityTranslucent(texture)
                : super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    /** Its glow (eyes, ice) out once it is dead - no lit eyes in a body that is fading. */
    @Override
    public void applyRenderLayers(PoseStack poseStack, T animatable, BakedGeoModel model, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                                  int packedLight, int packedOverlay) {
        if (animatable instanceof FrostServantEntity s && s.deathTime > 0 && s.fadeTicks() > 0) {
            return;
        }
        super.applyRenderLayers(poseStack, animatable, model, renderType, bufferSource, buffer, partialTick, packedLight,
                packedOverlay);
    }
}
