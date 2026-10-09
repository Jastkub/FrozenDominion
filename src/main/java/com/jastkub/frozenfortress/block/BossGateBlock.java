package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.block.entity.BossGateBlockEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import javax.annotation.Nullable;

/**
 * The block in the lintel over a miniboss's door that the boss gate hangs
 * from: masonry like the wall round it (deepslate or stone bricks), owning the
 * portcullis (BossGateBlockEntity) drawn below it.
 */
public class BossGateBlock extends Block implements EntityBlock {

    public static final BooleanProperty DEEP = BooleanProperty.create("deep");

    public BossGateBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.DEEPSLATE_BRICKS)
                .noLootTable()
                .pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(DEEP, true).setValue(TemplateTurn.FACING,
                net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DEEP, TemplateTurn.FACING);
    }

    /** Turned with the structure it is in: its block entity reads how far (TemplateTurn). */
    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return TemplateTurn.rotate(state, rotation);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BossGateBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide && type == FFBlockEntities.BOSS_GATE.get()
                ? (l, p, s, be) -> BossGateBlockEntity.serverTick(l, p, s, (BossGateBlockEntity) be)
                : null;
    }
}
