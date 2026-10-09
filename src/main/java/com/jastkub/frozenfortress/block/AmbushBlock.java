package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.block.entity.AmbushBlockEntity;
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

/** The treasury's ambush (AmbushBlockEntity) - its guards break out of their statues when its chest is opened. Hidden in the masonry under the floor; nothing breaks it. */
public class AmbushBlock extends BaseEntityBlock {

    /** (1.21) an entity block names its codec. */
    public static final com.mojang.serialization.MapCodec<AmbushBlock> CODEC = com.mojang.serialization.MapCodec.unit(AmbushBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public AmbushBlock() {
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_BRICKS).strength(-1.0F, 3600000.0F).noLootTable());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AmbushBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, FFBlockEntities.AMBUSH.get(), AmbushBlockEntity::serverTick);
    }
}
