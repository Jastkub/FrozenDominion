package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.client.RoarWarpFx;
import com.jastkub.frozenfortress.entity.RoarWarpEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The roar's bent air is drawn by RoarWarpFx, after everything else, through the frame itself. Only with a shader pack
 * on (Oculus), when the frame is not ours to bend, is it drawn here: the same shells and stream as a faint glow of
 * frost - the crest of each ring and the breath out of the maw, seen rather than seen through.
 */
public class RoarWarpRenderer extends EntityRenderer<RoarWarpEntity> {

    public RoarWarpRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(RoarWarpEntity warp, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(RoarWarpEntity warp, float entityYaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        if (!RoarWarpFx.shaderPackOn()) {
            return;
        }
        Vec3 at = warp.getPosition(partialTick);
        Matrix4f pose = poses.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        RoarWarpFx.build(warp, partialTick, (x, y, z, u, v, power, kind, fade, nx, ny, nz) -> {
            float a = power * fade * (kind > 0.5F ? 0.06F : 0.10F);
            vc.addVertex(pose, (float) (x - at.x), (float) (y - at.y), (float) (z - at.z))
                    .setColor(0.70F, 0.88F, 1.0F, a);
        });
    }

    @Override
    public ResourceLocation getTextureLocation(RoarWarpEntity warp) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
