package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.CitadelStatueBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Draws a statue: the model the block entity names, in its ice hide, held in
 * its pose, scaled and turned as the structure placed it.
 *
 * <p>The statues read their own COPIES of the creatures' geometry (geo/block),
 * never the living ones'. A baked model is shared by everything drawn from
 * it, and the king's renderer writes visibility onto his bones every frame -
 * a statue on the same model would wear whatever the live king last wore.
 * Bones a statue does not show are skipped while drawing, never flagged, so
 * nothing written here can leak into anything else either.
 */
public class CitadelStatueRenderer extends GeoBlockRenderer<CitadelStatueBlockEntity> {

    /** (1.21) the box it is drawn in is the renderer's to say; the block entity knows it. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(com.jastkub.frozenfortress.block.entity.CitadelStatueBlockEntity be) {
        return be.renderBox();
    }

    /** Its cracks as it shakes (tools/gen_statue_cracks.py), four stages, each the last grown. */
    private static final ResourceLocation[] CRACKS = {FrozenFortress.id("textures/block/statue/cracks_0.png"),
            FrozenFortress.id("textures/block/statue/cracks_1.png"), FrozenFortress.id("textures/block/statue/cracks_2.png"),
            FrozenFortress.id("textures/block/statue/cracks_3.png")};

    public CitadelStatueRenderer() {
        super(new StatueModel());
        // THE CRACKS: over the statue's own face while it shakes, a few at first, growing and branching as it goes, all of
        // them once it is breaking
        addRenderLayer(new software.bernie.geckolib.renderer.layer.GeoRenderLayer<>(this) {
            @Override
            public void render(PoseStack poseStack, CitadelStatueBlockEntity statue,
                               software.bernie.geckolib.cache.object.BakedGeoModel model, RenderType renderType,
                               MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight,
                               int packedOverlay) {
                float shake = statue.shake(partialTick);
                if (shake <= 0.03F || statue.isRemains() || statue.isDrift()) {
                    return;
                }
                float k = statue.shakingHard() ? 1.0F : shake;
                int stage = Math.min(CRACKS.length - 1, (int) (k * CRACKS.length));
                float a = Math.min(1.0F, 0.35F + 0.65F * k);
                RenderType cracks = RenderType.entityTranslucent(CRACKS[stage]);
                getRenderer().reRender(model, poseStack, buffers, statue, cracks, buffers.getBuffer(cracks), partialTick,
                        packedLight, packedOverlay, com.jastkub.frozenfortress.util.FFColor.argb(1.0F, 1.0F, 1.0F, a));
            }
        });
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        CitadelStatueBlockEntity statue = this.animatable;
        if (statue == null) {
            return;
        }
        float shake = statue.shake(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
        if (shake > 0.0F) {
            // worse as it goes - a tremor that becomes a shudder
            float t = (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0)
                    + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            // trembling: from a quiver to a rattle; breaking: a hard shudder, rocking on its base
            float amp = statue.shakingHard() ? 0.10F + 0.08F * shake : 0.025F + 0.085F * shake * shake;
            poseStack.translate(Mth.sin(t * 2.9F) * amp, 0.0F, Mth.cos(t * 3.7F) * amp);
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 4.3F) * amp * 22.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.cos(t * 3.1F) * amp * 16.0F));
        }
        // the models face north; the yaw is a creature's: 180 is north
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - statue.yaw()));
        float s = statue.scale();
        poseStack.scale(s, s, s);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, CitadelStatueBlockEntity animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  int colour) {
        if (animatable.isHidden(bone.getName())) {
            return;
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public boolean shouldRenderOffScreen(CitadelStatueBlockEntity statue) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    /** Every statue's resources come from its own block entity. */
    static class StatueModel extends GeoModel<CitadelStatueBlockEntity> {
        @Override
        public ResourceLocation getModelResource(CitadelStatueBlockEntity statue) {
            return FrozenFortress.id("geo/block/" + statue.model() + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(CitadelStatueBlockEntity statue) {
            return FrozenFortress.id("textures/block/statue/" + statue.texture() + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(CitadelStatueBlockEntity statue) {
            return FrozenFortress.id("animations/block/" + statue.model() + ".animation.json");
        }
    }
}
