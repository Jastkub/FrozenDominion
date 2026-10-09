package com.jastkub.frozenfortress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * THE MONSTROSITY'S SKULL (07.10.2026): what it leaves behind when it falls, and what the statue of Sovereign Ice
 * wears for a head before the Heart wakes it (HeartOfWinterItem). Its own head, horns and jaw, drawn by its block
 * entity (MonstrositySkullRenderer); placed facing whoever sets it down.
 */
public class MonstrositySkullBlock extends HorizontalDirectionalBlock implements net.minecraft.world.level.block.EntityBlock {

    /** (1.21) a block with a facing names its codec. */
    public static final com.mojang.serialization.MapCodec<MonstrositySkullBlock> CODEC = com.mojang.serialization.MapCodec.unit(MonstrositySkullBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);

    public MonstrositySkullBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE)
                .strength(1.5F, 6.0F)
                .sound(SoundType.BONE_BLOCK)
                .noOcclusion()
                .lightLevel(s -> 5));
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.jastkub.frozenfortress.block.entity.MonstrositySkullBlockEntity(pos, state);
    }
}
