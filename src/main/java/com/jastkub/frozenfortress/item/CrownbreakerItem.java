package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.block.entity.StormcrownBeaconBlockEntity;
import com.jastkub.frozenfortress.registry.FFBlocks;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Crownbreaker - an iron wedge the expedition forged for one job only.
 * No pick will touch the Stormcrown while the Sovereign's weight lies on the
 * fortress; this will. Drive it into the crown and stand back.
 */
public class CrownbreakerItem extends Item {

    @Override
    public boolean onEntitySwing(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        FFSwing.swing(entity, FFSwing.great(), false);              // Better Combat does not wield it
        return super.onEntitySwing(stack, entity);
    }

    public CrownbreakerItem() {
        // sixty-four: one for the crown and enough for every nest in the citadel
        super(new Properties().stacksTo(1).durability(64).rarity(Rarity.RARE));
    }

    /** A Frost Nest splits under the wedge - the only thing that splits one. */
    private InteractionResult breakNest(UseOnContext ctx, Level level, BlockPos pos) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof com.jastkub.frozenfortress.block.entity.CitadelSpawnerBlockEntity nest)
                || nest.isBreaking()) {
            return InteractionResult.CONSUME;
        }
        nest.beginBreaking();
        ((ServerLevel) level).playSound(null, pos, FFSounds.ICE_GRIND.get(), SoundSource.BLOCKS, 2.0F, 0.9F);
        Player player = ctx.getPlayer();
        if (player != null) {
            player.displayClientMessage(Component.translatable("item.frozen_dominion.crownbreaker.nest"), true);
            ctx.getItemInHand().hurtAndBreak(1, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(ctx.getHand()));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        // SNOW GIVES TO THE WEDGE: it shovels snow away - and if a nest lies under
        // the snow it was used on, it goes at the nest
        if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.SNOW)) {
            if (!level.isClientSide) {
                for (int up = 0; up < 3 && level.getBlockState(pos.above(up)).is(net.minecraft.tags.BlockTags.SNOW); up++) {
                    level.destroyBlock(pos.above(up), false);
                }
                level.destroyBlock(pos, false);
            }
            BlockPos under = pos.below();
            if (level.getBlockState(under).is(FFBlocks.CITADEL_SPAWNER.get())) {
                return breakNest(ctx, level, under);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockState(pos).is(FFBlocks.CITADEL_SPAWNER.get())) {
            return breakNest(ctx, level, pos);
        }
        // THE RIFT'S HEART splits under the wedge
        if (level.getBlockEntity(pos) instanceof com.jastkub.frozenfortress.block.entity.FrostHeartBlockEntity heart) {
            if (!level.isClientSide && ctx.getPlayer() != null && heart.wedge(ctx.getPlayer())) {
                ctx.getItemInHand().hurtAndBreak(1, ctx.getPlayer(), net.minecraft.world.entity.LivingEntity.getSlotForHand(ctx.getHand()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.getBlockState(pos).is(FFBlocks.STORMCROWN_BEACON.get())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof StormcrownBeaconBlockEntity beacon)) {
            return InteractionResult.PASS;
        }
        if (beacon.isBreaking()) {
            return InteractionResult.CONSUME;
        }

        beacon.beginBreaking();
        ServerLevel serverLevel = (ServerLevel) level;
        serverLevel.playSound(null, pos, FFSounds.ICE_GRIND.get(), SoundSource.BLOCKS, 2.0F, 0.7F);
        serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 30, 0.5D, 0.5D, 0.5D, 0.2D);

        Player player = ctx.getPlayer();
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("item.frozen_dominion.crownbreaker.driven"), true);
            ItemStack stack = ctx.getItemInHand();
            stack.hurtAndBreak(1, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(ctx.getHand()));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozen_dominion.crownbreaker.desc"));
        tooltip.add(Component.translatable("item.frozen_dominion.crownbreaker.lore"));
    }
}
