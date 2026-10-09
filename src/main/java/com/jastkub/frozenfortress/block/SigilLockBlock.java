package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.registry.FFBlocks;
import com.jastkub.frozenfortress.registry.FFItems;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The sealed keyhole of the throne cathedral. Unbreakable; opens only for
 * the Sigil of the Hollow King, dissolving the sealed masonry around it.
 */
public class SigilLockBlock extends Block {

    public SigilLockBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE)
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.AMETHYST)
                .lightLevel(state -> 15)
                .noLootTable());
    }

    /** It must be impossible to walk past: the keyhole spits light. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        for (int i = 0; i < 3; i++) {
            level.addParticle(FFParticles.SOUL_FROST.get(),
                    pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 1.6D,
                    pos.getY() + 0.5D + (random.nextDouble() - 0.5D) * 1.6D,
                    pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 1.6D,
                    0.0D, 0.03D, 0.0D);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(FFParticles.FROST_SWIRL.get(),
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    (random.nextDouble() - 0.5D) * 0.1D, 0.06D,
                    (random.nextDouble() - 0.5D) * 0.1D);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(FFItems.FROZEN_SIGIL.get())) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("block.frozen_dominion.sigil_lock.locked"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        ServerLevel serverLevel = (ServerLevel) level;
        // Only the ward itself dissolves - the glowing ice barring the stair.
        // The fortress's ordinary sealed masonry is never touched, so the
        // cathedral floor and its pillars cannot be undermined by opening it.
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -6, -8), pos.offset(3, 6, 8))) {
            BlockState bs = serverLevel.getBlockState(p);
            if (bs.is(FFBlocks.SEALED_SOVEREIGN_ICE.get())) {
                serverLevel.removeBlock(p, false);
                serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                        p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D, 6, 0.3D, 0.3D, 0.3D, 0.05D);
            }
        }
        serverLevel.removeBlock(pos, false);
        serverLevel.playSound(null, pos, FFSounds.ICE_PRISON.get(), SoundSource.BLOCKS, 1.6F, 0.7F);
        serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 40, 1.2D, 1.5D, 1.2D, 0.06D);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("block.frozen_dominion.sigil_lock.opened"), true);
        return InteractionResult.CONSUME;
    }
}
