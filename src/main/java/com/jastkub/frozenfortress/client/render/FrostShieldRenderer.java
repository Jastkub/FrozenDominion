package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.item.KingsrimeItems;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The Frost shields, drawn from their own models. The models sit where the
 * vanilla shield's quads sit, so the item's display (vanilla's numbers, in its
 * item model) holds it the same way, raised or lowered.
 *
 * Only the Kingsrime shield glows: GeckoLib throws on a glow mask without a
 * single lit pixel, and the Everfrost shield's star is plain ice.
 */
public class FrostShieldRenderer extends GeoItemRenderer<KingsrimeItems.FrostShield> {

    public FrostShieldRenderer(String model, boolean glows) {
        super(new DefaultedItemGeoModel<>(FrozenFortress.id("shield/" + model)));
        if (glows) {
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }
}
