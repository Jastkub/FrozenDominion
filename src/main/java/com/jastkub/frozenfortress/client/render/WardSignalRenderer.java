package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.WardSignalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The healing pulse, and the ribbon it drags behind it.
 *
 * <p>THE RIBBON IS THE WHOLE POINT. This was particles first and it could not
 * be seen from across the arena at all - the same failure the projectiles had.
 * A tail made of geometry is what makes a link between two things legible at
 * range, and legible at range is the entire job: a player who cannot see the
 * mending arriving has no reason to leave the boss and go break a pillar.
 */
public class WardSignalRenderer extends GeoEntityRenderer<WardSignalEntity> {

    public WardSignalRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("ice_crystal"), false));
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(WardSignalEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // wider and longer-lived than a projectile's: this one is meant to be
        // read as a LINE between two places rather than as a thing in flight
        ProjectileTrail.draw(entity, poseStack, bufferSource,
                FrozenFortress.id("textures/entity/ice_trail.png"),
                partialTick, 0.26F, 20, 0.80F, 0.96F, 1.0F);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public void preRender(PoseStack poseStack, WardSignalEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource,
                          com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        float age = animatable.tickCount + partialTick;
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 11.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * 7.0F));
        poseStack.scale(0.6F, 0.6F, 0.6F);
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public RenderType getRenderType(WardSignalEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }
}
