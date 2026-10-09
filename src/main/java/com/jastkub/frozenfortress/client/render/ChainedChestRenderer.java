package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.ChainedChestBlockEntity;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** A Chained Coffer, turned to its block's facing (its front is the model's north); the lock's ice glows. */
public class ChainedChestRenderer extends GeoBlockRenderer<ChainedChestBlockEntity> {

    public ChainedChestRenderer() {
        super(new DefaultedBlockGeoModel<>(FrozenFortress.id("chained_chest")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
