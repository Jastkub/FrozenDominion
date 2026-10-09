package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.block.entity.ArrowSlitBlockEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import javax.annotation.Nullable;

/**
 * AN ARROW SLIT (ArrowSlitBlockEntity): a block of the wall with a dark slit in its face, iced at
 * the lips - it shoots a bolt of ice out of it, FACING the way it looks, on its beat. Masonry: it breaks like the wall
 * round it (which the Stormcrown's hold on the pick makes slow), and gives nothing.
 */
public class ArrowSlitBlock extends BaseEntityBlock {

    /** (1.21) an entity block names its codec. */
    public static final com.mojang.serialization.MapCodec<ArrowSlitBlock> CODEC = com.mojang.serialization.MapCodec.unit(ArrowSlitBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ArrowSlitBlock() {
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS).noLootTable());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArrowSlitBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, FFBlockEntities.ARROW_SLIT.get(), ArrowSlitBlockEntity::serverTick);
    }
}
