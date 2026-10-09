package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.block.entity.StairWardBlockEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Under the Frozen Cisterns' floor, in the masonry the stair stands on: a deepslate brick like the rest of it, that
 * keeps the stair (StairWardBlockEntity). Nothing breaks it.
 */
public class StairWardBlock extends BaseEntityBlock {

    public StairWardBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.DEEPSLATE_BRICKS).strength(-1.0F, 3600000.0F).noLootTable());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StairWardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, FFBlockEntities.STAIR_WARD.get(), StairWardBlockEntity::serverTick);
    }
}
