package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.entity.projectile.ThrownWarmthEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * THE POTION OF WARMTH: no longer the game's potion with
 * our effect in it (two of them, a short one and a long one), but a thing of its own - the expedition's fire sealed in
 * a brass-bound flask. Sixty-four to a slot. Found, never brewed (tools/add_warmth_loot.py).
 *
 * <p>THROWN, not drunk: it bursts where it
 * lands, and everyone within four blocks of it has a minute of Warmth, frostbite kept off (ThrownWarmthEntity) -
 * thrown at your own feet, you as well.
 */
public class WarmthPotionItem extends Item {

    /** A minute of Warmth. */
    public static final int DURATION = 1200;

    public WarmthPotionItem() {
        super(new Properties().stacksTo(64).rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW,
                SoundSource.PLAYERS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            ThrownWarmthEntity flask = new ThrownWarmthEntity(level, player);
            flask.setItem(stack);
            flask.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F);
            level.addFreshEntity(flask);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("effect.frozen_dominion.warmth").append(" (1:00)")
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("item.frozen_dominion.warmth_potion.desc").withStyle(ChatFormatting.GRAY));
    }
}
