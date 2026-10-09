package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.block.entity.IcicleTrapBlockEntity;
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

/** An icicle trap (IcicleTrapBlockEntity) - the Lower Gate's icicles let go over a cracked plate. Hidden in the masonry under the floor; nothing breaks it. */
public class IcicleTrapBlock extends BaseEntityBlock {

    /** (1.21) an entity block names its codec. */
    public static final com.mojang.serialization.MapCodec<IcicleTrapBlock> CODEC = com.mojang.serialization.MapCodec.unit(IcicleTrapBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public IcicleTrapBlock() {
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS).strength(-1.0F, 3600000.0F).noLootTable());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IcicleTrapBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, FFBlockEntities.ICICLE_TRAP.get(), IcicleTrapBlockEntity::serverTick);
    }
}
