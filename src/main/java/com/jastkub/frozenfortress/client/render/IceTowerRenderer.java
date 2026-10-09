package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.IceTowerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The spire, and it GROWS on Y alone.
 *
 * <p>Scaling all three axes would be a model being inflated. Scaling only the
 * height, from a base that stays where it is, is a thing coming out of the
 * ground - which is the entire effect, and the reason every stage of the
 * geometry is centred on the axis.
 *
 * <p>Not translucent. Everything else made of ice in this fight is see-through
 * and that is right for shards and shells, but a column somebody is standing
 * on has to read as solid or the eye refuses to accept the weight.
 */
public class IceTowerRenderer extends GeoEntityRenderer<IceTowerEntity> {

    public IceTowerRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("ice_tower"), false));
        this.shadowRadius = 1.4F;
    }

    @Override
    public void preRender(PoseStack poseStack, IceTowerEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {
        // the rise curve AND the room's ceiling, both on Y, both from the base
        float risen = Math.max(0.02F, animatable.risen(partialTick) * animatable.height());
        poseStack.scale(1.0F, risen, 1.0F);
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /**
     * The wind goes on OUTSIDE the spire's own scale.
     *
     * <p>preRender scales the pose stack on Y by the rise, and everything
     * drawn after that inherits it - so a band drawn there would be squashed
     * flat at the start of the rise and stretched at the end, which is the one
     * thing wind must not do. render() is before that scale is pushed.
     */
    @Override
    public void render(IceTowerEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight) {
        TowerWindLayer.draw(entity, poseStack, bufferSource, partialTick,
                entity.risen(partialTick), entity.height());
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public RenderType getRenderType(IceTowerEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }
}
