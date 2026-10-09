package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.block.entity.CitadelSpawnerBlockEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFItems;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * A Frost Nest: the citadel's own spawner. What it breeds and how fast is
 * written into it by the structure, room by room. No tool, no blast and no
 * fire touches it - only the Crownbreaker, the expedition's wedge, which is
 * what makes clearing a section a decision rather than a reflex.
 */
public class CitadelSpawnerBlock extends BaseEntityBlock {

    public CitadelSpawnerBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.GLASS)
                .lightLevel(state -> 7)
                .noOcclusion()
                .noLootTable());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;              // its body is a GeckoLib model (TrapBlockRenderers.Nest)
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CitadelSpawnerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, FFBlockEntities.CITADEL_SPAWNER.get(), CitadelSpawnerBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).is(FFItems.CROWNBREAKER.get())) {
            return InteractionResult.PASS;           // the Crownbreaker's own useOn takes it
        }
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("block.frozen_dominion.citadel_spawner.untouchable"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            level.addParticle(FFParticles.FROST_SWIRL.get(),
                    pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.9D,
                    pos.getY() + 0.3D + random.nextDouble() * 0.6D,
                    pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.9D,
                    0.0D, 0.02D, 0.0D);
        }
    }
}
