package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.ShadeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * A SHADE (tools/gen_shade_shepherd.py, "shade") - DRAWN ONLY IN FIRELIGHT.
 *
 * <p>THE RULE: a shade is drawn only while a lit campfire is within {@link com.jastkub.frozenfortress.entity.ShadeLight#SIGHT}
 * blocks of it. The entity works that out on the client every third tick and eases its {@link ShadeEntity#sight} towards
 * 1 (lit) or 0 (dark) by 0.18 a tick, so it fades in and out over five or six ticks instead of popping; this draws it at
 * that alpha (times {@link #MAX_ALPHA}: even in the light it is a shadow, a little see-through), and not at all below
 * {@link #CUTOFF}. Dying, it fades out over its death clip as it sinks.
 *
 * <p>Nothing else gives it away in the dark: no shadow on the floor (the dispatcher draws that whether or not this
 * draws the model - so there is none), no glow - its eyes light themselves only as much as the rest of it shows
 * ({@link FadingGlowLayer}: GeckoLib's own AutoGlowingGeoLayer always draws at full alpha, so it would leave two eyes
 * floating in the dark). It is heard instead - its own footsteps, its breath, its hiss.
 */
public class ShadeRenderer extends FrostGeoRenderer<ShadeEntity> {

    /** Even in the light it is a shadow. */
    static final float MAX_ALPHA = 0.88F;
    static final float CUTOFF = 0.02F;
    /** Its death clip's length: it fades out over it. */
    static final float DEATH_FADE = 24.0F;

    public ShadeRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("shade"), true));
        this.shadowRadius = 0.0F;
        addRenderLayer(new FadingGlowLayer<>(this, ShadeRenderer::alpha));
    }

    /** How much of it is drawn this frame. */
    static float alpha(ShadeEntity shade, float partialTick) {
        float a = shade.sight(partialTick) * MAX_ALPHA;
        if (shade.deathTime > 0) {
            a *= Math.max(0.0F, 1.0F - (shade.deathTime + partialTick) / DEATH_FADE);
        }
        return a;
    }

    @Override
    public void render(ShadeEntity shade, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (alpha(shade, partialTick) < CUTOFF) {
            return;                                                   // out of the light: nothing of it is drawn
        }
        super.render(shade, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public Color getRenderColor(ShadeEntity shade, float partialTick, int packedLight) {
        return Color.ofRGBA(1.0F, 1.0F, 1.0F, alpha(shade, partialTick));
    }

    @Override
    public RenderType getRenderType(ShadeEntity shade, ResourceLocation texture, MultiBufferSource bufferSource,
                                    float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    /**
     * The glow mask drawn at the model's own alpha. (AutoGlowingGeoLayer reRenders at alpha 1 whatever the model is
     * drawn at.) The mask is the same _glowmask sheet GeckoLib composes for AutoGlowingGeoLayer - and like it, it must
     * have lit pixels in it (gen_shade_shepherd.py checks every mask). A reRender goes through preRender again, so
     * nothing turned in a preRender of these renderers is turned outside !isReRender.
     */
    public static class FadingGlowLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {

        /** The alpha its owner is drawn at this frame. */
        public interface Alpha<T> {
            float of(T animatable, float partialTick);
        }

        private final Alpha<T> alpha;

        public FadingGlowLayer(GeoRenderer<T> renderer, Alpha<T> alpha) {
            super(renderer);
            this.alpha = alpha;
        }

        @Override
        public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                           int packedOverlay) {
            float a = alpha.of(animatable, partialTick);
            if (a < CUTOFF) {
                return;
            }
            RenderType glow = AutoGlowingTexture.getRenderType(getTextureResource(animatable));
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow),
                    partialTick, 15728640, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, a);
        }
    }
}
