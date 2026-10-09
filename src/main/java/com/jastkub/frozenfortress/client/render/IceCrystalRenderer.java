package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.projectile.FrostBoltEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Axis;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * A real shard of ice, where there used to be nothing at all.
 *
 * <p>EVERY ONE IS DIFFERENT, and that costs nothing: the model is one
 * splinter, but its spin axis, its rate and its size are derived from the
 * entity's own id, so a volley of a dozen is a dozen shapes rather than a
 * dozen copies of one. Tumbling also solves the flat-sprite problem the old
 * shards had - a turning solid always presents some silhouette, where a
 * billboard edge-on presents a line.
 */
public class IceCrystalRenderer extends GeoEntityRenderer<FrostBoltEntity> {

    public IceCrystalRenderer(EntityRendererProvider.Context context) {
        super(context, new BoltModel());
        this.shadowRadius = 0.0F;
    }

    @Override
    public void preRender(PoseStack poseStack, FrostBoltEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {
        int seed = animatable.getId();
        float age = animatable.tickCount + partialTick;
        // three different rates on three axes, picked off the id, so no two in
        // a volley ever line up with each other
        poseStack.mulPose(Axis.YP.rotationDegrees(age * (7.0F + (seed % 5) * 2.4F)));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * (5.0F + (seed % 3) * 3.1F)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(seed * 37.0F));
        // the entity's own size on top of the per-id variation, so a volley
        // is still a range of shapes rather than N copies of one big one
        float size = (0.82F + (seed % 7) * 0.05F) * animatable.size();
        poseStack.scale(size, size, size);
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /**
     * A REAL TAIL, drawn before the crystal so the sheet sits behind it.
     *
     * <p>These used to trail particles, and a particle trail on something
     * moving half a block a tick is a dotted line. The tail is geometry now -
     * see ProjectileTrail - which is what makes the heart barrage's lobbed
     * arcs read as arcs: a dozen curves drawn in the air at once, rather than
     * a dozen scatterings of motes that happen to be curved.
     */
    private static final ResourceLocation TRAIL =
            FrozenFortress.id("textures/entity/ice_trail.png");
    private static final ResourceLocation GLOW =
            FrozenFortress.id("textures/entity/bomb_glow.png");

    @Override
    public void render(FrostBoltEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (entity.isHeavy()) {
            // ================================================================
            // A BOMB IS A STREAK WITH A HEAD ON IT.
            //
            // The same fourteen-tick hairline every other bolt gets was being
            // drawn behind an object four times the size, which is why the
            // colossus's salvo read as three crystals quietly falling rather
            // than as three shells crossing the room. The reference is very
            // clear about the two halves of it:
            //
            //   THE STREAK RUNS THE WHOLE FLIGHT. Not a tail - a RIBBON from
            //   the hand to wherever the thing is now, tapering to nothing at
            //   the launch end. Sixty ticks of history at three times the
            //   width, which on a lob lasting fifty ticks means the entire
            //   parabola is drawn in the air. THAT is what makes a lobbed shot
            //   legible: you are not tracking a dot, you are reading a curve
            //   that already tells you where it is going to land.
            //
            //   AND IT IS TWO-TONE. A wide cold band with a narrow white-hot
            //   line down the middle of it, drawn as two passes. One pass at
            //   one colour is a painted stripe; two is depth, and it costs a
            //   second call - the history is sampled once per game tick, so
            //   drawing the strip twice adds no points to it.
            // ================================================================
            ProjectileTrail.draw(entity, poseStack, bufferSource, TRAIL,
                    partialTick, 0.60F, 60, 0.52F, 0.82F, 1.0F);
            ProjectileTrail.draw(entity, poseStack, bufferSource, TRAIL,
                    partialTick, 0.21F, 60, 1.0F, 1.0F, 1.0F);
            head(entity, poseStack, bufferSource, partialTick);
        } else {
            ProjectileTrail.draw(entity, poseStack, bufferSource, TRAIL,
                    partialTick, 0.20F, 14, 0.72F, 0.90F, 1.0F);
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * THE HEAD: three camera-facing sprites, stacked and counter-turning.
     *
     * <p>One quad is a sticker. The reference's head is plainly several of
     * them - the silhouette has steps in it that no single sprite has, and the
     * white patch inside sits off-centre from the outline around it. Three at
     * different scales, rotated against each other and slowly turning in
     * opposite directions, give exactly that: an outline that changes shape as
     * it flies without anything actually being animated.
     *
     * <p>Drawn BEFORE the crystal, so the tumbling solid sits inside its own
     * glow instead of behind it.
     */
    private void head(FrostBoltEntity entity, PoseStack poses,
                      MultiBufferSource buffers, float partialTick) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
        float age = entity.tickCount + partialTick;
        float base = entity.size() * 0.46F;
        poses.pushPose();
        poses.mulPose(net.minecraft.client.Minecraft.getInstance()
                .getEntityRenderDispatcher().cameraOrientation());
        float[] layer = {1.0F, 0.76F, 0.54F};
        for (int i = 0; i < layer.length; i++) {
            poses.pushPose();
            poses.mulPose(Axis.ZP.rotationDegrees(
                    age * (i - 1) * 2.6F + i * 53.0F + entity.getId() * 29.0F));
            quad(poses.last(), vc, base * layer[i]);
            poses.popPose();
        }
        poses.popPose();
    }

    /** One flat sprite, wound both ways so it survives being seen from behind. */
    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float hw) {
        org.joml.Matrix4f mat = pose.pose();
        org.joml.Matrix3f nrm = pose.normal();
        for (boolean back : new boolean[]{false, true}) {
            float[][] uv = back
                    ? new float[][]{{-hw, -hw, 0, 1}, {hw, -hw, 1, 1},
                                    {hw, hw, 1, 0}, {-hw, hw, 0, 0}}
                    : new float[][]{{-hw, -hw, 0, 1}, {-hw, hw, 0, 0},
                                    {hw, hw, 1, 0}, {hw, -hw, 1, 1}};
            for (float[] v : uv) {
                vc.vertex(mat, v[0], v[1], 0.0F)
                        .color(1.0F, 1.0F, 1.0F, 1.0F)
                        .uv(v[2], v[3])
                        .overlayCoords(net.minecraft.client.renderer.texture
                                .OverlayTexture.NO_OVERLAY)
                        .uv2(0xF000F0)
                        .normal(nrm, 0.0F, 0.0F, 1.0F)
                        .endVertex();
            }
        }
    }

    @Override
    public RenderType getRenderType(FrostBoltEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    /**
     * ONE RENDERER, TWO OBJECTS.
     *
     * <p>GeckoLib asks the model for its geometry and texture per entity, so
     * the choice can be made here rather than by standing up a second entity
     * type whose only difference would be two resource paths. See
     * FrostBoltEntity.HEAVY.
     */
    static class BoltModel extends DefaultedEntityGeoModel<FrostBoltEntity> {
        BoltModel() {
            super(FrozenFortress.id("ice_crystal"), false);
        }

        @Override
        public net.minecraft.resources.ResourceLocation getModelResource(FrostBoltEntity entity) {
            return entity.isHeavy()
                    ? FrozenFortress.id("geo/entity/golem_shard.geo.json")
                    : super.getModelResource(entity);
        }

        @Override
        public net.minecraft.resources.ResourceLocation getTextureResource(FrostBoltEntity entity) {
            return entity.isHeavy()
                    ? FrozenFortress.id("textures/entity/golem_shard.png")
                    : super.getTextureResource(entity);
        }
    }
}
