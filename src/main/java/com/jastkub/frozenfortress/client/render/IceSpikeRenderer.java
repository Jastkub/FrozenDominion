package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Nothing is drawn until it actually erupts.
 *
 * <p>The entity's controller returns PlayState.STOP while it is waiting out
 * its delay, and a stopped controller means NO animation is driving the model
 * - so GeckoLib falls back to the bind pose, which for this model is a spike
 * standing fully grown on the floor. The telegraph was therefore the finished
 * spike sitting there, and the eruption that followed was a thing that was
 * already present jumping slightly.
 *
 * <p>Held at zero size until the emerge animation has something to say.
 */
public class IceSpikeRenderer extends GeoEntityRenderer<IceSpikeEntity> {

    public IceSpikeRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("ice_spike"), false));
        this.shadowRadius = 0.0F;
    }

    @Override
    public void preRender(PoseStack poseStack, IceSpikeEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, int colour) {
        if (animatable.tickCount < animatable.getDelay()) {
            poseStack.scale(0.0F, 0.0F, 0.0F);
        } else {
            // the run grows as it travels - see IceSpikeEntity.SCALE
            float k = animatable.scale();
            if (k != 1.0F) {
                poseStack.scale(k, k, k);
            }
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }
}
