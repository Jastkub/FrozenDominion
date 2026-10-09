package com.jastkub.frozenfortress.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * THE GLACIER RING: a band of old glacier ice with a block of the glacier on it - worn, every knockback, throw, shove and
 * gust on its wearer is seventy percent weaker (FFEnchantments.steady -).
 * One in the citadel, in the chest of the Ancestors' hidden stash ("w jakiejs niszowej skrzynce": secret_glacier).
 */
public class GlacierRingItem extends Item {

    public GlacierRingItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozen_dominion.glacier_ring.slot"));
        tooltip.add(Component.translatable("item.frozen_dominion.glacier_ring.desc"));
        tooltip.add(Component.translatable("item.frozen_dominion.glacier_ring.desc2"));
    }
}
