package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.FrostboundSentinelEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class FrostboundSentinelRenderer extends FrostGeoRenderer<FrostboundSentinelEntity> {

    public FrostboundSentinelRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("frostbound_sentinel"), true));
        this.shadowRadius = 0.6F;
        addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));   // its ice and its eyes light themselves
    }

    /**
     * The rime on the blade is only there while he gathers the cold for the crush (its clip grows it); the ring of
     * stars over his helm only while an axe has him stunned, and the few ticks after it in which they shrink away
     * (his clips scale them; this keeps them out of every frame they do not own - a spawn's first blend included).
     */
    @Override
    public void preRender(com.mojang.blaze3d.vertex.PoseStack poseStack, FrostboundSentinelEntity knight,
                          software.bernie.geckolib.cache.object.BakedGeoModel model,
                          net.minecraft.client.renderer.MultiBufferSource bufferSource,
                          com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        boolean crush = knight.getAttackState() == FrostboundSentinelEntity.CRUSH;
        model.getBone("sword_rime").ifPresent(b -> b.setHidden(!crush));
        boolean stars = knight.showsStars();
        model.getBone("stun_stars").ifPresent(b -> b.setHidden(!stars));
        super.preRender(poseStack, knight, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);
    }
}
