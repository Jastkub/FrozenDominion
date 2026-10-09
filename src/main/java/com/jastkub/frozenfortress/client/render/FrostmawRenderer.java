package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.FrostmawEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class FrostmawRenderer extends FrostGeoRenderer<FrostmawEntity> {

    public FrostmawRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("frostmaw"), true));
        this.shadowRadius = 0.8F;
        addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));   // its ice and its eyes light themselves
        addRenderLayer(new FrostmawBreathLayer(this));                                              // its breath, as geometry
    }

    /** Its saddle only under a rider (FrostRiderEntity) - and then the middle of its crest is not there to sit on. */
    @Override
    public void preRender(PoseStack poseStack, FrostmawEntity maw, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          int colour) {
        super.preRender(poseStack, maw, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                colour);
        boolean ridden = maw.rider() != null;
        model.getBone("saddle").ifPresent(b -> b.setHidden(!ridden));
        model.getBone("crest_mid").ifPresent(b -> b.setHidden(ridden));
    }
}
