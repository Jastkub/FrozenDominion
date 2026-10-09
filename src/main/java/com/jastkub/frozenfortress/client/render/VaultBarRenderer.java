package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.VaultBarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** A grate of the vault, its face turned out from the cage it is part of. */
public class VaultBarRenderer extends GeoEntityRenderer<VaultBarEntity> {

    public VaultBarRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("vault_bar"), false));
        this.shadowRadius = 0.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void preRender(PoseStack poseStack, VaultBarEntity bar, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {
        if (!isReRender) {        // (a glow layer draws the model again through preRender, on the stack this already turned: once only)
            poseStack.mulPose(Axis.YP.rotationDegrees(-bar.getYRot()));
        }
        super.preRender(poseStack, bar, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);
    }
}
