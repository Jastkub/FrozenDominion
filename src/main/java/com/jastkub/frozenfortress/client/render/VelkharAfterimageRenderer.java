package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharAfterimageEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The departure shadow: his own model, drawn cold and translucent, going out
 * over fifteen ticks.
 *
 * <p>Tinted rather than merely faded. A straight alpha ramp on the normal
 * sheet looks like a rendering bug; pulling the red channel down and leaving
 * the blue up makes it read as an absence of him rather than a half-drawn
 * copy of him.
 */
public class VelkharAfterimageRenderer extends GeoEntityRenderer<VelkharAfterimageEntity> {

    public VelkharAfterimageRenderer(EntityRendererProvider.Context context) {
        super(context, new GhostModel());
        this.shadowRadius = 0.0F;
    }

    @Override
    public RenderType getRenderType(VelkharAfterimageEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void preRender(PoseStack poseStack, VelkharAfterimageEntity animatable,
                          BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay,
                          int colour) {
        // Same two pieces of kit the living boss drops as he changes: a ghost
        // of phase three still holding a shield would be a giveaway.
        // These write onto the model every Velkhar shares, so they say what
        // they want BOTH ways - an afterimage that only ever hid things left
        // them hidden for the living king behind it.
        boolean noShield = animatable.getPhase() >= 2;
        boolean noVisor = animatable.getPhase() >= 3;
        model.getBone("shield").ifPresent(bone -> bone.setHidden(noShield));
        model.getBone("visor").ifPresent(bone -> bone.setHidden(noVisor));
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public void actuallyRender(PoseStack poseStack, VelkharAfterimageEntity animatable,
                               BakedGeoModel model, RenderType renderType,
                               MultiBufferSource bufferSource, VertexConsumer buffer,
                               boolean isReRender, float partialTick, int packedLight,
                               int packedOverlay, int colour) {
        float fade = animatable.fade(partialTick);
        // FULL BRIGHT, and the fade curve is held high before it drops.
        //
        // The first version of this was invisible in play, and neither the entity
        // nor the animation was at fault: it was drawn at 0.55 alpha in a dark
        // hall using the room's light, on a model that is already dark blue. Lit
        // from itself and starting near opaque, it is a ghost. The 0.45 exponent
        // keeps it readable for most of its life and then loses it quickly,
        // instead of being half gone by the time anyone looks.
        float out = (float) Math.pow(fade, 0.45D);
        // and it swells slightly as it goes, so it dissipates rather than blinks
        poseStack.scale(1.0F + 0.16F * (1.0F - fade), 1.0F + 0.16F * (1.0F - fade),
                1.0F + 0.16F * (1.0F - fade));
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer,
                isReRender, partialTick, 0xF000F0, packedOverlay,
                com.jastkub.frozenfortress.util.FFColor.argb(0.52F, 0.78F, 1.0F,
                        com.jastkub.frozenfortress.util.FFColor.alpha(colour) * 0.92F * out));
    }

    static class GhostModel extends DefaultedEntityGeoModel<VelkharAfterimageEntity> {
        GhostModel() {
            super(FrozenFortress.id("velkhar"), false);
        }

        @Override
        public ResourceLocation getTextureResource(VelkharAfterimageEntity entity) {
            return switch (entity.getPhase()) {
                case 1 -> FrozenFortress.id("textures/entity/velkhar.png");
                case 2 -> FrozenFortress.id("textures/entity/velkhar_storm.png");
                default -> FrozenFortress.id("textures/entity/velkhar_hollow.png");
            };
        }
    }
}
