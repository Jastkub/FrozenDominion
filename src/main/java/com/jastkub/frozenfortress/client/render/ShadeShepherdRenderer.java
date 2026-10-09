package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.ShadeShepherdEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * PASTERZ CIENI (tools/gen_shade_shepherd.py). In the chambers' dark, all of him there is to see is what lights itself
 * (his glow mask): the pale ram's-skull mask and the slits of its eyes, the clapper of the bell in his crook - and the
 * HERD-LIGHTS, the ring of small cold lights round his shoulders. There is one for each shade of his herd that lives
 * ({@link ShadeShepherdEntity#herd}, synced): the rest are hidden here, so as the herd is thinned in the firelight the
 * lights go out one by one, and you can see his shield going. (In firelight he is lit like anything else.)
 *
 * <p>The herd-lights' visibility is set on every frame, for all six, because his decoy is drawn from the same baked model
 * (ShadeShepherdFxRenderers.Decoy, which hides them all): whatever one renderer left hidden would otherwise stay hidden
 * on the other. Hiding a bone is not a pose transform, so it is safe on the glow layer's second preRender.
 */
public class ShadeShepherdRenderer extends FrostGeoRenderer<ShadeShepherdEntity> {

    static final int MOTES = 6;

    public ShadeShepherdRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("shade_shepherd"), true));
        this.shadowRadius = 0.7F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            // in his shadow-form even what lights itself is dimmed: the mask and the herd-lights a faint glimmer,
            // still there to be read - not a lantern to aim at
            @Override
            public void render(PoseStack poseStack, ShadeShepherdEntity animatable, BakedGeoModel bakedModel,
                               net.minecraft.client.renderer.RenderType renderType, MultiBufferSource bufferSource,
                               VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                net.minecraft.client.renderer.RenderType glow = getRenderType(animatable);
                float f = 1.0F - GLOW_DIM * animatable.shadowFade;
                getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow,
                        bufferSource.getBuffer(glow), partialTick, 15728640,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, f, f, f, 1.0F);
            }
        });
    }

    /**
     * OUT OF THE FIRELIGHT, A SHADOW: where the fires' light does not reach him (block light
     * under LIT at his eyes) he is drawn as his own shadow - near black and mostly see-through - and he comes back into
     * his shape as he steps into the light. Eased, a little each frame, so he melts rather than blinks.
     */
    static final int LIT = 8;
    static final float SHADOW_DARK = 0.86F, SHADOW_CLEAR = 0.74F, GLOW_DIM = 0.6F, EASE = 0.06F;

    @Override
    public void render(ShadeShepherdEntity shepherd, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        int block = shepherd.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK,
                net.minecraft.core.BlockPos.containing(shepherd.getX(), shepherd.getEyeY(), shepherd.getZ()));
        float want = shepherd.isDeadOrDying() || block >= LIT ? 0.0F : 1.0F;
        shepherd.shadowFade += (want - shepherd.shadowFade) * EASE;
        super.render(shepherd, yaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public software.bernie.geckolib.core.object.Color getRenderColor(ShadeShepherdEntity shepherd, float partialTick,
                                                                     int packedLight) {
        float f = shepherd.shadowFade;
        if (f < 0.01F) {
            return super.getRenderColor(shepherd, partialTick, packedLight);
        }
        float c = 1.0F - SHADOW_DARK * f;
        return software.bernie.geckolib.core.object.Color.ofRGBA(c * 0.9F, c * 0.9F, c, 1.0F - SHADOW_CLEAR * f);
    }

    @Override
    public net.minecraft.client.renderer.RenderType getRenderType(ShadeShepherdEntity shepherd,
                                                                  net.minecraft.resources.ResourceLocation texture,
                                                                  MultiBufferSource bufferSource, float partialTick) {
        return shepherd.shadowFade >= 0.01F ? net.minecraft.client.renderer.RenderType.entityTranslucent(texture)
                : super.getRenderType(shepherd, texture, bufferSource, partialTick);
    }

    /** The first `n` herd-lights shown, the rest hidden. */
    static void herdLights(BakedGeoModel model, int n) {
        for (int k = 0; k < MOTES; k++) {
            final boolean hide = k >= n;
            model.getBone("mote_" + k).ifPresent(b -> b.setHidden(hide));
        }
    }

    @Override
    public void preRender(PoseStack poseStack, ShadeShepherdEntity shepherd, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        herdLights(model, shepherd.isDeadOrDying() ? MOTES : shepherd.herd());
        super.preRender(poseStack, shepherd, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);
    }
}
