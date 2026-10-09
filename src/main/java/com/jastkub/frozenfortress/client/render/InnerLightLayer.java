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
 * The light gets out of him.
 *
 * <p>Two moments need it and they need the same thing: the Hollow Winter
 * beam, where he is visibly spending what is inside him, and the death,
 * where it stops being contained at all. In both cases the problem with the
 * old version was the same - the effect was entirely OUTSIDE the model. A
 * boss who pours a river of light out of his chest for five seconds while
 * looking exactly as he did a moment before reads as a turret with a good
 * particle effect attached.
 *
 * <p>This is a second pass over the same geometry using the sheet the
 * AutoGlowing layer already uses - the eyes, the heart, the crown and the
 * ice in his armour - drawn full-bright with the alpha driven by the
 * entity's own {@code innerLight}. So the parts of him that were always
 * meant to be lit simply go over-bright, which is what "the light is
 * escaping" actually looks like on a solid object.
 *
 * <p>It stacks on top of the normal glow layer rather than replacing it, so
 * at zero it contributes nothing and nothing else has to change.
 */
public class InnerLightLayer extends GeoRenderLayer<VelkharEntity> {

    private static final ResourceLocation GLOW =
            FrozenFortress.id("textures/entity/velkhar_glowmask.png");

    public InnerLightLayer(GeoRenderer<VelkharEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, VelkharEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        float light = animatable.innerLight();
        if (light <= 0.02F) {
            return;
        }
        // A flicker on top of the ramp. Held perfectly steady it reads as a
        // lamp being turned up; unsteady, it reads as pressure.
        float time = animatable.tickCount + partialTick;
        float flicker = 0.86F + 0.14F * Mth.sin(time * 1.7F) * Mth.sin(time * 0.61F);
        float alpha = Mth.clamp(light * flicker, 0.0F, 1.0F);

        RenderType glow = RenderType.entityTranslucentEmissive(GLOW);
        // Drawn TWICE at high light: the second pass over the same pixels is
        // what pushes them past white and makes it look like it is coming
        // through the plate rather than sitting on it.
        int passes = light > 0.55F ? 2 : 1;
        for (int i = 0; i < passes; i++) {
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow,
                    bufferSource.getBuffer(glow), partialTick,
                    LightTexture.FULL_BRIGHT, packedOverlay,
                    com.jastkub.frozenfortress.util.FFColor.argb(1.0F, 1.0F, 1.0F, alpha));
        }
    }
}
