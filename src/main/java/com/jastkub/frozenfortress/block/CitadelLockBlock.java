package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.block.entity.CitadelLockBlockEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The lock of a sealed gate. Which keys it wants is written into its block
 * entity by the structure (none at all makes it a lever: a one-way shortcut
 * that opens from the side it is on). Turn every key it wants in it and the
 * gate it touches grinds away, from the floor up. Keys are not taken - in a
 * shared world the next player must not find the door shut on them.
 */
public class CitadelLockBlock extends BaseEntityBlock {

    /** (1.21) an entity block names its codec. */
    public static final com.mojang.serialization.MapCodec<CitadelLockBlock> CODEC = com.mojang.serialization.MapCodec.unit(CitadelLockBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    /** For a block that works as a lock but looks like something else (the secret switches). */
    protected CitadelLockBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    public CitadelLockBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.DEEPSLATE_BRICKS)
                .lightLevel(state -> state.getValue(OPEN) ? 2 : 9)
                .noLootTable());
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CitadelLockBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, FFBlockEntities.CITADEL_LOCK.get(), CitadelLockBlockEntity::serverTick);
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack heldStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CitadelLockBlockEntity lock) {
            lock.tryOpen(player, player.getItemInHand(hand));
        }
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(OPEN) || random.nextInt(3) != 0) {
            return;
        }
        level.addParticle(FFParticles.SOUL_FROST.get(),
                pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 1.2D,
                pos.getY() + 0.5D + (random.nextDouble() - 0.5D) * 1.2D,
                pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 1.2D,
                0.0D, 0.02D, 0.0D);
    }
}
