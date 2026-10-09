package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.entity.effect.FallingDebrisEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Draws the block chunk, tumbling and shrinking as its timer runs out. */
@OnlyIn(Dist.CLIENT)
public class FallingDebrisRenderer extends EntityRenderer<FallingDebrisEntity> {

    public FallingDebrisRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(FallingDebrisEntity entity, float yaw, float partialTick,
                       PoseStack poses, MultiBufferSource buffers, int light) {
        BlockState state = entity.getBlockState();
        if (state.isAir()) {
            return;
        }

        float life = (entity.tickCount + partialTick) / Math.max(1.0F, entity.getLifetime());
        // Shrinks away over the last third rather than blinking out.
        float scale = 0.45F * (1.0F - Mth.clamp((life - 0.66F) / 0.34F, 0.0F, 1.0F));
        if (scale <= 0.01F) {
            return;
        }

        poses.pushPose();
        poses.translate(0.0D, 0.15D, 0.0D);
        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(
                Mth.lerp(partialTick, entity.prevSpinYaw, entity.spinYaw)));
        poses.mulPose(com.mojang.math.Axis.XP.rotationDegrees(
                Mth.lerp(partialTick, entity.prevSpinPitch, entity.spinPitch)));
        poses.scale(scale, scale, scale);
        poses.translate(-0.5D, -0.5D, -0.5D);

        net.minecraft.client.Minecraft.getInstance().getBlockRenderer()
                .getModelRenderer()
                .tesselateBlock(entity.level(),
                        net.minecraft.client.Minecraft.getInstance().getBlockRenderer()
                                .getBlockModel(state),
                        state, entity.blockPosition(), poses,
                        buffers.getBuffer(net.minecraft.client.renderer.RenderType.cutout()),
                        false, RandomSource.create(), state.getSeed(entity.blockPosition()),
                        OverlayTexture.NO_OVERLAY);
        poses.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(FallingDebrisEntity entity) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}
