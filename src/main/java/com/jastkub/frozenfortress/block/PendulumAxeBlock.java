package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.block.entity.PendulumAxeBlockEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The anchor of a swinging axe in the vault (PendulumAxeBlockEntity): its iron plate; the rod and blade are drawn. */
public class PendulumAxeBlock extends BaseEntityBlock {

    private static final VoxelShape PLATE = Block.box(4.0D, 13.0D, 4.0D, 12.0D, 16.0D, 12.0D);

    public PendulumAxeBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.CHAIN)
                .noOcclusion()
                .noLootTable()
                .pushReaction(PushReaction.BLOCK));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return PLATE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PendulumAxeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, FFBlockEntities.PENDULUM_AXE.get(), PendulumAxeBlockEntity::serverTick);
    }
}
