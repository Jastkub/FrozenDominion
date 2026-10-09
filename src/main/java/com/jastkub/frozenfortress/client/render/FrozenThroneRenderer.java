package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.FrozenThroneBlockEntity;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Draws the throne.
 *
 * <p>The culling box that stops it popping out of view lives on the block
 * entity rather than here: in Forge the override is IForgeBlockEntity's
 * getRenderBoundingBox(), and GeoBlockRenderer has no such method to
 * override. Only shouldRenderOffScreen belongs to the renderer.
 */
public class FrozenThroneRenderer extends GeoBlockRenderer<FrozenThroneBlockEntity> {

    /** (1.21) the box it is drawn in is the renderer's to say; the block entity knows it. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(com.jastkub.frozenfortress.block.entity.FrozenThroneBlockEntity be) {
        return be.renderBox();
    }

    public FrozenThroneRenderer() {
        super(new DefaultedBlockGeoModel<>(FrozenFortress.id("frozen_throne")));
        addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));
    }

    /**
     * Translucent, for the folds of clear ice at its flanks and the two of the court frozen inside them
     * (07.10.2026). The model draws its ice last (gen_models_throne.py), so everything behind the ice is
     * already in the depth buffer when the ice blends over it.
     */
    @Override
    public net.minecraft.client.renderer.RenderType getRenderType(FrozenThroneBlockEntity animatable,
            net.minecraft.resources.ResourceLocation texture,
            @javax.annotation.Nullable net.minecraft.client.renderer.MultiBufferSource bufferSource,
            float partialTick) {
        return net.minecraft.client.renderer.RenderType.entityTranslucent(texture);
    }

    @Override
    public boolean shouldRenderOffScreen(FrozenThroneBlockEntity block) {
        return true;
    }
}
