package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.FrostSkeletonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The Frost Skeleton: the remains' own bones, two points of frost lit in its
 * sockets. The frost goes out as it falls apart - it flickers in the pile,
 * and comes back as the pile pulls itself together.
 */
public class FrostSkeletonRenderer extends FrostGeoRenderer<FrostSkeletonEntity> {

    public FrostSkeletonRenderer(EntityRendererProvider.Context context) {
        super(context, new SkeletonModel());
        this.shadowRadius = 0.35F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void preRender(PoseStack poseStack, FrostSkeletonEntity skel, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          int colour) {
        super.preRender(poseStack, skel, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                colour);
        boolean out = skel.isDeadOrDying() || (skel.isPile() && (skel.tickCount / 3) % 7 != 0);
        model.getBone("eyes").ifPresent(b -> b.setHidden(out));
        // its gear: only its own variant's (the baked model is shared - every bone of gear set, every frame)
        int variant = skel.variant();
        for (String gear : FrostSkeletonEntity.ALL_VARIANT_BONES) {
            model.getBone(gear).ifPresent(b -> b.setHidden(!FrostSkeletonEntity.wears(variant, gear)));
        }
        // the Frost Rider's snowball: in its hand from the wind-up to the let-go
        boolean ball = skel instanceof com.jastkub.frozenfortress.entity.FrostRiderEntity r && r.holdsSnowball();
        model.getBone("snowball").ifPresent(b -> b.setHidden(!ball));
    }

    /** Its head turns to what it looks at only while it is on its feet and not mid-swing. */
    static class SkeletonModel extends DefaultedEntityGeoModel<FrostSkeletonEntity> {
        SkeletonModel() {
            super(FrozenFortress.id("frost_skeleton"), true);
        }

        @Override
        public void setCustomAnimations(FrostSkeletonEntity skel, long instanceId, AnimationState<FrostSkeletonEntity> state) {
            if (skel.isPassenger() && skel instanceof com.jastkub.frozenfortress.entity.FrostRiderEntity
                    && !skel.isDeadOrDying()) {
                // in the saddle the hips go with the hound: the body turns from the waist to what it throws at,
                // the head the rest of the way
                software.bernie.geckolib.model.data.EntityModelData data =
                        state.getData(software.bernie.geckolib.constant.DataTickets.ENTITY_MODEL_DATA);
                if (data != null) {
                    float yaw = net.minecraft.util.Mth.clamp(data.netHeadYaw(), -85.0F, 85.0F)
                            * net.minecraft.util.Mth.DEG_TO_RAD;
                    float pitch = data.headPitch() * net.minecraft.util.Mth.DEG_TO_RAD;
                    software.bernie.geckolib.cache.object.GeoBone spine = getAnimationProcessor().getBone("spine");
                    software.bernie.geckolib.cache.object.GeoBone head = getAnimationProcessor().getBone("head");
                    if (spine != null) {
                        spine.setRotY(spine.getRotY() + yaw * 0.6F);
                    }
                    if (head != null) {
                        head.setRotY(head.getRotY() + yaw * 0.4F);
                        head.setRotX(head.getRotX() + pitch * 0.6F);
                    }
                }
                return;
            }
            if (skel.getAttackState() == 0 && !skel.isDeadOrDying()) {
                super.setCustomAnimations(skel, instanceId, state);
            }
        }
    }
}
