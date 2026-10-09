package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.BoneWandFxEntity;
import com.jastkub.frozenfortress.entity.BoneWandRingEntity;
import com.jastkub.frozenfortress.entity.BoneWandSkeletonEntity;
import com.jastkub.frozenfortress.entity.FrostSkeletonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The Wand of the Dead's shapes (tools/gen_bone_wand.py) - its servants, its grave's ring and its pictures - each with
 * its own light (every glow mask has lit pixels: the generator counts them; GeckoLib's glow layer crashes on an
 * empty one).
 *
 * <p>TURNS ONLY ON THE FIRST PASS. GeckoLib calls preRender again for every re-render a layer asks for (the glow is
 * one), on a pose stack that already carries the first pass's transforms - so everything here that moves, turns or
 * scales does it under !isReRender.
 */
public final class BoneWandRenderers {

    private BoneWandRenderers() {
    }

    /**
     * A servant: the Frost Skeleton's own model and clips (geo + animations of frost_skeleton) under the wand's sheet,
     * bone_wand_skeleton - its frost gone the grave's green, its eyes and its iced hand lighting themselves, so it is
     * told from the citadel's own at a glance and in the dark. The green goes out as it falls apart (flickering in
     * the pile) and gutters in its last three seconds.
     */
    public static class Skeleton extends FrostGeoRenderer<BoneWandSkeletonEntity> {
        public Skeleton(EntityRendererProvider.Context ctx) {
            super(ctx, new SkeletonModel());
            this.shadowRadius = 0.35F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, BoneWandSkeletonEntity skel, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                              int packedLight, int packedOverlay, int colour) {
            super.preRender(poseStack, skel, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, colour);
            int t = skel.tickCount;
            boolean out = skel.isDeadOrDying() || skel.crumbling()
                    || (skel.isPile() && (t / 3) % 7 != 0)
                    || (skel.expiring() && (t / 2) % 3 == 0);
            model.getBone("eyes").ifPresent(b -> b.setHidden(out));
            int variant = skel.variant();
            for (String gear : FrostSkeletonEntity.ALL_VARIANT_BONES) {
                model.getBone(gear).ifPresent(b -> b.setHidden(!FrostSkeletonEntity.wears(variant, gear)));
            }
            model.getBone("snowball").ifPresent(b -> b.setHidden(true));     // (the Frost Rider's, not a servant's)
        }
    }

    /** The frost skeleton's model and clips, the wand's sheet; its head turns to what it looks at only on its feet. */
    static class SkeletonModel extends DefaultedEntityGeoModel<BoneWandSkeletonEntity> {
        SkeletonModel() {
            super(FrozenFortress.id("frost_skeleton"), true);
            withAltTexture(FrozenFortress.id("bone_wand_skeleton"));
        }

        @Override
        public void setCustomAnimations(BoneWandSkeletonEntity skel, long instanceId,
                                        AnimationState<BoneWandSkeletonEntity> state) {
            if (skel.state() == 0 && !skel.isDeadOrDying()) {
                super.setCustomAnimations(skel, instanceId, state);
            }
        }
    }

    /** The grave's ring: round, turned to the cast, at its size (a servant called back comes up out of a small one). */
    public static class Ring extends GeoEntityRenderer<BoneWandRingEntity> {
        public Ring(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("fx_bone_wand_ring"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, BoneWandRingEntity ring, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                              int packedLight, int packedOverlay, int colour) {
            if (!isReRender) {
                poseStack.mulPose(Axis.YP.rotationDegrees(-ring.getYRot()));
                float s = ring.size();
                poseStack.scale(s, s, s);
            }
            super.preRender(poseStack, ring, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, colour);
        }
    }

    /** A picture (BoneWandFxEntity): the model of its kind, at its size and yaw - and, riding, at its rider's
     *  interpolated position so it never trails it. */
    public static class Fx extends GeoEntityRenderer<BoneWandFxEntity> {
        public Fx(EntityRendererProvider.Context ctx) {
            super(ctx, new FxModel());
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, BoneWandFxEntity fx, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                              int packedLight, int packedOverlay, int colour) {
            if (!isReRender) {
                Vec3 rider = fx.riderPosition(partialTick);
                if (rider != null) {
                    Vec3 me = fx.getPosition(partialTick);
                    poseStack.translate(rider.x - me.x, rider.y - me.y, rider.z - me.z);
                }
                // a spur turns with the body it rides (its flames stream back from where the servant is going)
                float yaw = fx.getYRot();
                if (BoneWandFxEntity.SPUR.equals(fx.kind()) && fx.rider() instanceof LivingEntity le) {
                    yaw = Mth.rotLerp(partialTick, le.yBodyRotO, le.yBodyRot);
                }
                poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
                float s = fx.size();
                poseStack.scale(s, s, s);
            }
            super.preRender(poseStack, fx, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, colour);
        }
    }

    static class FxModel extends GeoModel<BoneWandFxEntity> {
        @Override
        public ResourceLocation getModelResource(BoneWandFxEntity fx) {
            return FrozenFortress.id("geo/entity/fx_bone_wand_" + fx.kind() + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(BoneWandFxEntity fx) {
            return FrozenFortress.id("textures/entity/fx_bone_wand_" + fx.kind() + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(BoneWandFxEntity fx) {
            return FrozenFortress.id("animations/entity/fx_bone_wand_" + fx.kind() + ".animation.json");
        }
    }
}
