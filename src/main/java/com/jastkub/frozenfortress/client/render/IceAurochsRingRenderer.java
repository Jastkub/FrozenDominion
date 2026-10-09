package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.IceAurochsRingEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The ring of ice the Ice Aurochs' stomp sends over the floor (tools/gen_ice_aurochs.py, ice_aurochs_ring): round, so
 * never turned - nothing touches the pose stack here. The points of its spikes and the cracks of its crater light
 * themselves.
 */
public class IceAurochsRingRenderer extends GeoEntityRenderer<IceAurochsRingEntity> {

    public IceAurochsRingRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("ice_aurochs_ring"), false));
        this.shadowRadius = 0.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
