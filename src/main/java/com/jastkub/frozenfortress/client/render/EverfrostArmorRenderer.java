package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.item.EverfrostGeoArmorItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The Everfrost plate in three dimensions; only the eyes in the sallet's slit light themselves. */
public class EverfrostArmorRenderer extends GeoArmorRenderer<EverfrostGeoArmorItem> {

    public EverfrostArmorRenderer() {
        super(new DefaultedItemGeoModel<>(FrozenFortress.id("armor/everfrost_armor")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
