package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.IceBoulderEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Straight GeckoLib render; the motion all lives in the entity. */
public class IceBoulderRenderer extends GeoEntityRenderer<IceBoulderEntity> {

    public IceBoulderRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("ice_boulder"), false));
        this.shadowRadius = 0.0F;
    }
}
