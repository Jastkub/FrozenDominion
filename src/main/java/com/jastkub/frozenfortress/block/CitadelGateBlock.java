package com.jastkub.frozenfortress.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * One block of a sealed gate in the citadel. Nothing breaks it; the lock it
 * touches removes the whole gate when the right key is turned in it (see
 * {@link CitadelLockBlock}). Gates are found by flood: every gate block joined
 * to the lock, face to face, is part of the same gate.
 */
public class CitadelGateBlock extends Block {

    public CitadelGateBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.NETHERITE_BLOCK)
                .noLootTable());
    }
}
