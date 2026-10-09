package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.ChargeLaneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The lane of the Monstrosity's charge (ChargeLaneEntity; tools/gen_charge_lane.py paints its sheet): a strip of frost
 * flat on the floor, its own light, the chevrons on it crawling the way the charge will run - faster and brighter as the
 * launch comes - and its edges two hard lines. It grows out from its feet over a quarter second, and fades once the
 * charge is on it.
 */
public class ChargeLaneRenderer extends EntityRenderer<ChargeLaneEntity> {

    private static final ResourceLocation TEX = FrozenFortress.id("textures/entity/charge_lane.png");
    /** Ticks of the lane before the launch (HollowGolemEntity.CHARGE_LOCK). */
    private static final float BEFORE = 24.0F;

    public ChargeLaneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ChargeLaneEntity lane) {
        return TEX;
    }

    @Override
    public void render(ChargeLaneEntity lane, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        float t = lane.tickCount + partialTick;
        float life = Math.max(1.0F, lane.life());
        float grow = Mth.clamp(t / 5.0F, 0.0F, 1.0F);
        float fade = Mth.clamp((life - t) / 10.0F, 0.0F, 1.0F);
        float urgency = Mth.clamp(t / BEFORE, 0.0F, 1.0F);
        float alpha = (0.45F + 0.5F * urgency) * fade * (0.85F + 0.15F * Mth.sin(t * (0.4F + urgency)));
        if (alpha <= 0.01F) {
            return;
        }
        float len = lane.laneLength() * grow;
        float half = lane.laneWidth() * 0.5F;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-lane.laneYaw()));
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(TEX));
        Matrix4f m = poseStack.last().pose();
        PoseStack.Pose n = poseStack.last();
        // the strip runs along +z (the yaw turned onto it), chevrons tiling every two blocks and crawling forward
        float scroll = -t * (0.06F + 0.12F * urgency);
        int seg = Math.max(1, (int) Math.ceil(len / 2.0F));
        for (int i = 0; i < seg; i++) {
            float z0 = len * i / seg, z1 = len * (i + 1) / seg;
            float v0 = z0 / 2.0F + scroll, v1 = z1 / 2.0F + scroll;
            put(vc, m, n, -half, z0, 0.0F, v0, alpha);
            put(vc, m, n, -half, z1, 0.0F, v1, alpha);
            put(vc, m, n, half, z1, 1.0F, v1, alpha);
            put(vc, m, n, half, z0, 1.0F, v0, alpha);
        }
        poseStack.popPose();
        super.render(lane, yaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static void put(VertexConsumer vc, Matrix4f m, PoseStack.Pose n, float x, float z, float u, float v, float alpha) {
        vc.addVertex(m, x, 0.06F, z).setColor(1.0F, 1.0F, 1.0F, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(n, 0.0F, 1.0F, 0.0F);
    }
}
