package com.jastkub.frozenfortress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * A lantern of cold fire on an iron bracket, fixed to a wall. FACING is the
 * way it points out of the wall; the wall it hangs on is behind it.
 */
public class FrostSconceBlock extends HorizontalDirectionalBlock {

    /** (1.21) a block with a facing names its codec. */
    public static final com.mojang.serialization.MapCodec<FrostSconceBlock> CODEC = com.mojang.serialization.MapCodec.unit(FrostSconceBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    private static final VoxelShape SOUTH = Block.box(5.0D, 2.0D, 0.0D, 11.0D, 14.0D, 13.0D);
    private static final VoxelShape NORTH = Block.box(5.0D, 2.0D, 3.0D, 11.0D, 14.0D, 16.0D);
    private static final VoxelShape EAST = Block.box(0.0D, 2.0D, 5.0D, 13.0D, 14.0D, 11.0D);
    private static final VoxelShape WEST = Block.box(3.0D, 2.0D, 5.0D, 16.0D, 14.0D, 11.0D);

    public FrostSconceBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE)
                .strength(1.0F)
                .sound(SoundType.LANTERN)
                .lightLevel(state -> 15)
                .noOcclusion()
                .noCollission());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> SOUTH;
        };
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction back = state.getValue(FACING).getOpposite();
        BlockPos wall = pos.relative(back);
        return level.getBlockState(wall).isFaceSturdy(level, wall, state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isVertical()) {
            return null;
        }
        BlockState state = defaultBlockState().setValue(FACING, face);
        return state.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? state : null;
    }
}
