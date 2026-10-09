package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.IceSpearEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Straight GeckoLib render with two additions: a lean, and a fade.
 *
 * <p>The lean is derived from the entity id rather than synced - a dozen
 * spears standing at exactly ninety degrees looks like a fence, and nothing
 * about which way any individual one tips is worth a network packet. The fade
 * is the last twelve ticks of its planted life, so they leave rather than
 * blink out.
 */
public class IceSpearRenderer extends GeoEntityRenderer<IceSpearEntity> {

    private static final int FADE_TICKS = 12;

    public IceSpearRenderer(EntityRendererProvider.Context context) {
        super(context, new SpearModel());
        this.shadowRadius = 0.0F;
        addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(IceSpearEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void preRender(PoseStack poseStack, IceSpearEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {
        if (animatable.isQuarrel()) {
            // ================================================================
            // THE BOLT IS AIMED HERE, and nowhere else.
            //
            // Two things were making it fly crabwise. The random per-id tilt
            // below is decoration for the weather's spears - a field of them
            // should not all face the same way - and it was being applied to
            // the bolt as well, which is ten degrees of wrong on something
            // that has a correct direction. And the model is long along Z
            // while the spear's is long along Y, so one renderer aiming both
            // off one entity yaw cannot be right for both.
            //
            // The entity holds its rotation at zero (see IceSpearEntity), so
            // whatever GeckoLib does with yaw is the identity and this is the
            // only thing pointing it. Vanilla's arrow does the same pair of
            // rotations; its model runs along X, so it takes yaw-90 and
            // pitches about Z. Ours runs along Z, which is X turned a quarter
            // the other way, so it takes yaw and pitches about X.
            // ================================================================
            Vec3 go = animatable.getDeltaMovement();
            if (!isReRender && go.lengthSqr() > 1.0E-6D) {      // (a glow layer draws the model again through preRender, on the stack this already turned: once only)
                float yaw = (float) (Mth.atan2(go.x, go.z) * (180.0D / Math.PI));
                float pitch = (float) (Mth.atan2(go.y, go.horizontalDistance())
                        * (180.0D / Math.PI));
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-pitch));
            }
            super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                    partialTick, packedLight, packedOverlay, red, green, blue, alpha);
            return;
        }
        int id = animatable.getId();
        float tiltX = (Math.floorMod(id * 7, 9) - 4) * 2.4F;
        float tiltZ = (Math.floorMod(id * 13, 9) - 4) * 2.4F;
        if (!isReRender) {        // (a glow layer draws the model again through preRender, on the stack this already turned: once only)
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(tiltX));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(tiltZ));
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void actuallyRender(PoseStack poseStack, IceSpearEntity animatable, BakedGeoModel model,
                               RenderType renderType, MultiBufferSource bufferSource,
                               VertexConsumer buffer, boolean isReRender, float partialTick,
                               int packedLight, int packedOverlay, float red, float green,
                               float blue, float alpha) {
        float out = 1.0F;
        if (animatable.isPlanted()) {
            int left = IceSpearEntity.plantedLifetime() - animatable.plantedFor();
            if (left < FADE_TICKS) {
                out = Mth.clamp(left / (float) FADE_TICKS, 0.0F, 1.0F);
            }
        }
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha * (0.35F + 0.65F * out));
    }

    /**
     * A REAL TAIL behind each one.
     *
     * <p>The whole point of thinning the volley from thirty spears to a
     * handful is that each survivor has to carry its own weight, and a falling
     * shard with nothing behind it is a texture moving down the screen. The
     * ribbon is what turns six of them into six visible LINES converging on
     * the floor - see ProjectileTrail.
     */
    @Override
    public void render(com.jastkub.frozenfortress.entity.boss.IceSpearEntity entity,
                       float entityYaw, float partialTick,
                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource bufferSource,
                       int packedLight) {
        ProjectileTrail.draw(entity, poseStack, bufferSource,
                com.jastkub.frozenfortress.FrozenFortress.id("textures/entity/ice_trail.png"),
                partialTick, 0.22F, 16, 0.74F, 0.92F, 1.0F);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** One renderer, two objects. See IceSpearEntity.QUARREL. */
    static class SpearModel extends DefaultedEntityGeoModel<IceSpearEntity> {
        SpearModel() {
            super(FrozenFortress.id("ice_spear"), false);
        }

        @Override
        public ResourceLocation getModelResource(IceSpearEntity entity) {
            return entity.isQuarrel()
                    ? FrozenFortress.id("geo/entity/ice_quarrel.geo.json")
                    : super.getModelResource(entity);
        }

        @Override
        public ResourceLocation getTextureResource(IceSpearEntity entity) {
            return entity.isQuarrel()
                    ? FrozenFortress.id("textures/entity/ice_quarrel.png")
                    : super.getTextureResource(entity);
        }
    }
}
