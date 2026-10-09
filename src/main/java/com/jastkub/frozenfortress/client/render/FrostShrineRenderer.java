package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.FrostShrineBlockEntity;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The Frost Shrine (FrostShrineBlock): its plinth and bowl, and the crystal flame in the bowl - hidden while it is
 * cold, embers while it stirs, the flame once kindled (tools/gen_frost_shrine.py). Turned to its FACING by
 * GeoBlockRenderer; its own light through AutoGlowingGeoLayer.
 */
public class FrostShrineRenderer extends GeoBlockRenderer<FrostShrineBlockEntity> {

    public FrostShrineRenderer() {
        super(new DefaultedBlockGeoModel<>(FrozenFortress.id("frost_shrine")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(FrostShrineBlockEntity be) {
        return be.renderBox();
    }
}
