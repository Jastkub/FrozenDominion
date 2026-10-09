package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.CitadelSpawnerBlockEntity;
import com.jastkub.frozenfortress.block.entity.FrostCannonBlockEntity;
import com.jastkub.frozenfortress.block.entity.FrostHeartBlockEntity;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The trap rooms' heart and maws (tools/gen_trap_blocks.py): their ice lights itself, the rooms are dark. */
public final class TrapBlockRenderers {

    private TrapBlockRenderers() {
    }

    public static class Heart extends GeoBlockRenderer<FrostHeartBlockEntity> {
        /** (1.21) the box it is drawn in is the renderer's to say; the block entity knows it. */
        @Override
        public net.minecraft.world.phys.AABB getRenderBoundingBox(com.jastkub.frozenfortress.block.entity.FrostHeartBlockEntity be) {
            return be.renderBox();
        }

        public Heart() {
            super(new DefaultedBlockGeoModel<>(FrozenFortress.id("frost_heart")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    /** A Frost Nest's body (tools/gen_nest_beacon.py: frost_nest): its heart and its shards' tips light themselves. */
    public static class Nest extends GeoBlockRenderer<CitadelSpawnerBlockEntity> {
        public Nest() {
            super(new DefaultedBlockGeoModel<>(FrozenFortress.id("frost_nest")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    /** Turned by its facing (GeoBlockRenderer reads the block's FACING): its throat looks out of the wall. */
    public static class Cannon extends GeoBlockRenderer<FrostCannonBlockEntity> {
        /** (1.21) the box it is drawn in is the renderer's to say; the block entity knows it. */
        @Override
        public net.minecraft.world.phys.AABB getRenderBoundingBox(com.jastkub.frozenfortress.block.entity.FrostCannonBlockEntity be) {
            return be.renderBox();
        }

        public Cannon() {
            super(new DefaultedBlockGeoModel<>(FrozenFortress.id("frost_cannon")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }
}
