package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.BossGateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The boss gate, hung from its block in the lintel and turned the way its
 * face looks. Its light is taken from the opening under the lintel, not from
 * the block it hangs from: that block is solid masonry, dark inside, and a
 * gate lit by it would be black.
 */
public class BossGateRenderer extends GeoBlockRenderer<BossGateBlockEntity> {

    /** (1.21) the box it is drawn in is the renderer's to say; the block entity knows it. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(com.jastkub.frozenfortress.block.entity.BossGateBlockEntity be) {
        return be.renderBox();
    }

    public BossGateRenderer() {
        super(new DefaultedBlockGeoModel<>(FrozenFortress.id("boss_gate")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void actuallyRender(PoseStack poseStack, BossGateBlockEntity gate, BakedGeoModel model, RenderType renderType,
                               MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                               float partialTick, int packedLight, int packedOverlay,
                               int colour) {
        if (gate.getLevel() != null) {
            packedLight = LevelRenderer.getLightColor(gate.getLevel(), gate.getBlockPos().below(2));
        }
        super.actuallyRender(poseStack, gate, model, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        BossGateBlockEntity gate = this.animatable;
        if (gate == null) {
            return;
        }
        float yaw = switch (gate.facing()) {                 // drawn with its face to the north
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> 270.0F;
            default -> 0.0F;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        if (gate.shift() != 0.0F) {
            poseStack.translate(gate.shift(), 0.0F, 0.0F);
        }
        if (gate.scaleX() != 1.0F || gate.scaleY() != 1.0F) {
            poseStack.scale(gate.scaleX(), gate.scaleY(), Math.max(1.0F, gate.scaleX() * 0.6F));
        }
    }

    @Override
    public boolean shouldRenderOffScreen(BossGateBlockEntity gate) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
