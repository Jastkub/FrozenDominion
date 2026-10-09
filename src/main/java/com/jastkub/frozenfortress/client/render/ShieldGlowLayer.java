package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The gate catches fire while nothing can get through it.
 *
 * <p>This is the one readout the player has for "hitting this is pointless
 * right now", and it did not exist: the shield wall lit some particles in
 * front of him and left the shield itself exactly as dark as it is when it
 * can be broken. A state you cannot see is a state the player learns by
 * dying to it.
 *
 * <p>It works the way {@link software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer}
 * does - a second pass over the same geometry with a second texture - with
 * one difference: that texture is transparent everywhere except the shield's
 * own UV island, so only the gate lights up however the model is posed, and
 * nothing here needs to know which bone is which. The alpha breathes, because
 * a constant glow reads as a material and a pulsing one reads as a charge.
 */
public class ShieldGlowLayer extends GeoRenderLayer<VelkharEntity> {

    private static final ResourceLocation GLOW =
            FrozenFortress.id("textures/entity/velkhar_shield_glow.png");

    public ShieldGlowLayer(GeoRenderer<VelkharEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, VelkharEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        if (!animatable.isShieldLit() || animatable.getPhase() >= 2) {
            return;
        }
        float time = animatable.tickCount + partialTick;
        // Fast enough to read as electrical rather than as a slow breath, and
        // it never goes fully out - a glow that reaches zero flickers.
        float pulse = 0.62F + 0.38F * Mth.abs(Mth.sin(time * 0.32F));
        RenderType glow = RenderType.entityTranslucentEmissive(GLOW);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow,
                bufferSource.getBuffer(glow), partialTick,
                LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, pulse);
    }
}
