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
 * The gauntlet saturating with frost before the fist goes out.
 *
 * <p>ONE LIMB CANNOT BE LIT FROM CODE. GeckoLib's {@code reRender} paints the
 * whole model with whatever sheet it is given, so there is no call that says
 * "brighten the gauntlet". The selection is therefore done in the TEXTURE:
 * {@code velkhar_fist_charge.png} is painted only where the gauntlet and its
 * cuff sit on the atlas and left clear everywhere else, so re-rendering the
 * entire figure through it lights that limb alone.
 *
 * <p>The charge is a value the entity ramps, not a fixed clip: the fist fills
 * through the wind-up and then dumps on contact, so what the player reads is
 * energy being gathered and then spent rather than a lamp that happens to be
 * on.
 */
public class FistChargeLayer extends GeoRenderLayer<VelkharEntity> {

    private static final ResourceLocation SHEET =
            FrozenFortress.id("textures/entity/velkhar_fist_charge.png");

    public FistChargeLayer(GeoRenderer<VelkharEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, VelkharEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        float charge = animatable.fistCharge();
        if (charge <= 0.002F) {
            return;
        }

        // IT DOES NOT FILL SMOOTHLY. Something being forced into a container
        // pulses as it goes, and the pulse quickens as the container fills -
        // held steady this reads as a lamp on a dimmer, which is the opposite
        // of what a fist about to be thrown should look like.
        float time = animatable.tickCount + partialTick;
        float rate = 0.8F + charge * 2.6F;
        float pulse = 0.78F + 0.22F * Mth.sin(time * rate)
                            * Mth.sin(time * rate * 0.41F + 0.7F);

        // Alpha rises faster than the charge does, so the last third of the
        // wind-up is where it visibly bites - the moment worth watching is the
        // one just before the arm leaves.
        float alpha = Mth.clamp((float) Math.pow(charge, 0.65D) * pulse, 0.0F, 1.0F);

        // Ice blue at the start, whitening as it saturates: colour is how the
        // eye reads intensity, and a blue that simply gets brighter reads as
        // closer rather than as hotter.
        float hot = charge * charge;
        float r = 0.42F + 0.55F * hot;
        float g = 0.80F + 0.19F * hot;

        RenderType type = RenderType.entityTranslucentEmissive(SHEET);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type,
                bufferSource.getBuffer(type), partialTick,
                LightTexture.FULL_BRIGHT, packedOverlay, r, g, 1.0F, alpha);
    }
}
