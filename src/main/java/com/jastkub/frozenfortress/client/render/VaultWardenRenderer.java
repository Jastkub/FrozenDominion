package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.VaultWardenEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class VaultWardenRenderer extends FrostGeoRenderer<VaultWardenEntity> {

    public VaultWardenRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("vault_warden"), false));
        this.shadowRadius = 1.0F;
    }

    /** A struck core swells and flares for a moment (VaultWardenEntity.coreFlash) - its glow pass with it. */
    @Override
    public void renderRecursively(com.mojang.blaze3d.vertex.PoseStack poseStack,
                                  com.jastkub.frozenfortress.entity.VaultWardenEntity warden,
                                  software.bernie.geckolib.cache.object.GeoBone bone,
                                  net.minecraft.client.renderer.RenderType renderType,
                                  net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                  com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        float flash = "core".equals(bone.getName()) ? warden.coreFlash(partialTick) : 0.0F;
        if (flash <= 0.0F) {
            super.renderRecursively(poseStack, warden, bone, renderType, bufferSource, buffer, isReRender,
                    partialTick, packedLight, packedOverlay, red, green, blue, alpha);
            return;
        }
        float sx = bone.getScaleX(), sy = bone.getScaleY(), sz = bone.getScaleZ();
        float k = 1.0F + 0.45F * flash;
        bone.updateScale(sx * k, sy * k, sz * k);
        super.renderRecursively(poseStack, warden, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, 0xF000F0, packedOverlay, red, green, blue, alpha);
        bone.updateScale(sx, sy, sz);
    }
}
