package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.IceAurochsEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * LODOWY TUR (tools/gen_ice_aurochs.py). His eyes, the lit points of his horns and the crystals of his back light
 * themselves - the hall is dark but for its hearths. The head is NOT turned to where he looks: every pose of his
 * (the levelled horns of the charge, the hook of the toss, the hung head of the stun) is authored, and the effects
 * that are laid where his head is (the snort, the stars) are measured off those poses.
 *
 * <p>The rage-ice on his back (the "rage" bone) is only there in his second half - it grows out of him in the bellow.
 * Hiding a bone is not a transform of the pose stack, so it is safe on the glow layer's second pass too; nothing here
 * turns or scales the stack (were it to, it would have to be under !isReRender).
 */
public class IceAurochsRenderer extends FrostGeoRenderer<IceAurochsEntity> {

    public IceAurochsRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("ice_aurochs"), false));
        this.shadowRadius = 1.5F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void preRender(PoseStack poseStack, IceAurochsEntity aurochs, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        boolean rage = aurochs.isEnraged();
        model.getBone("rage").ifPresent(b -> b.setHidden(!rage));
        super.preRender(poseStack, aurochs, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);
    }
}
