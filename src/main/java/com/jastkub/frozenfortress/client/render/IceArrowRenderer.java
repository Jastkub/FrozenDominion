package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.projectile.IceArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class IceArrowRenderer extends ArrowRenderer<IceArrowEntity> {

    private static final ResourceLocation TEXTURE = FrozenFortress.id("textures/entity/ice_arrow.png");

    public IceArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(IceArrowEntity entity) {
        return TEXTURE;
    }
}
