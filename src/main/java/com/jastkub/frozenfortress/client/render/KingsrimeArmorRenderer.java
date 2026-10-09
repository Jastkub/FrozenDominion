package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.item.KingsrimeItems;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The Kingsrime armour in three dimensions; the crown's ice and the medallion light themselves. */
public class KingsrimeArmorRenderer extends GeoArmorRenderer<KingsrimeItems.KingsrimeArmor> {

    public KingsrimeArmorRenderer() {
        super(new DefaultedItemGeoModel<>(FrozenFortress.id("armor/kingsrime_armor")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
