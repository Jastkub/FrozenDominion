package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.TurnkeyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * THE TURNKEY'S WARD: under his last ring of health nothing but his own keys
 * goes through him - and it shows. A shell of rime over the whole of him, a little proud of his
 * hide, breathing slowly: a lock with no keyhole but the one he carries.
 */
public class TurnkeyWardLayer extends GeoRenderLayer<TurnkeyEntity> {

    private static final ResourceLocation WARD = FrozenFortress.id("textures/entity/turnkey_ward.png");

    public TurnkeyWardLayer(GeoRenderer<TurnkeyEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poses, TurnkeyEntity turnkey, BakedGeoModel model, RenderType type,
                       MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        if (turnkey.getHealth() > TurnkeyEntity.LAST_RING || turnkey.isDeadOrDying()) {
            return;
        }
        float t = turnkey.tickCount + partialTick;
        float alpha = 0.32F + 0.14F * Mth.sin(t * 0.12F);
        poses.pushPose();
        poses.translate(0.0D, turnkey.getBbHeight() * 0.5D, 0.0D);
        poses.scale(1.05F, 1.03F, 1.05F);
        poses.translate(0.0D, -turnkey.getBbHeight() * 0.5D, 0.0D);
        RenderType ward = RenderType.entityTranslucentEmissive(WARD);
        getRenderer().reRender(model, poses, buffers, turnkey, ward, buffers.getBuffer(ward), partialTick,
                0xF000F0, packedOverlay, 0.75F, 0.95F, 1.0F, alpha);
        poses.popPose();
    }
}
