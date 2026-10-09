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
 * The hollow face is alive.
 *
 * <p>In the third phase the eyes were two constant dots and the mouth did not
 * light at all - a skull with a night-light in it. This is a second emissive
 * pass, the same trick {@link ShieldGlowLayer} uses, over a sheet that is
 * transparent everywhere except the drawn eyes and teeth. What makes it a face
 * rather than a lamp is that the alpha MOVES: a slow breath, a faster shiver on
 * top of it, and a rare blink where the light nearly goes out for a few ticks -
 * the eyes and the grin burning, guttering, and burning again out of the dark.
 *
 * <p>The base hollow glow-mask no longer lights the sockets (see
 * {@code gen_textures.glow_mask}), so there is no constant floor under this to
 * wash the flicker out - the face is lit ONLY here, and only in the third phase
 * with the helm off.
 */
public class FaceGlowLayer extends GeoRenderLayer<VelkharEntity> {

    private static final ResourceLocation GLOW =
            FrozenFortress.id("textures/entity/velkhar_hollow_faceglow.png");

    public FaceGlowLayer(GeoRenderer<VelkharEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, VelkharEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        // Exactly the frames head_bare is drawn - the same gate the renderer
        // uses for the hollow face - or the light hangs in the air where there
        // is still a helm. That gate moved: the skull now appears on the frame
        // the faceplate leaves (tick 58) rather than when the whole transition
        // ends, and if this one had not moved with it the sockets would have
        // stayed dark for the fifty-four ticks the reveal is actually on.
        if (!animatable.wearsMagus() || !animatable.isMaskOff()) {
            return;
        }
        float time = animatable.tickCount + partialTick;
        // the breath, and a quicker shiver on top so it is never quite steady
        float breath = 0.72F + 0.28F * Mth.sin(time * 0.11F);
        float shiver = 0.90F + 0.10F * Mth.sin(time * 0.9F + animatable.getId() % 7);
        float alpha = breath * shiver;
        // THE BLINK. A seven-second cycle, offset per entity so a boss and his
        // mirrors do not gutter in lockstep, with the light all but gone across
        // its last few ticks - a sine dip so it closes and opens rather than
        // being cut.
        float cycle = 150.0F;
        float phase = (time + animatable.getId() * 23.0F) % cycle;
        if (phase >= cycle - 5.0F) {
            float k = (phase - (cycle - 5.0F)) / 5.0F;
            alpha *= 1.0F - 0.9F * Mth.sin(k * (float) Math.PI);
        }
        RenderType glow = RenderType.entityTranslucentEmissive(GLOW);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow,
                bufferSource.getBuffer(glow), partialTick,
                LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, alpha);
    }
}
