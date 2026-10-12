package com.jastkub.frozenfortress.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * THE VIGIL SHARD: a sliver of the shrines' own ice with
 * a cold flame in it. Given to a Frost Shrine (FrostShrineBlock.use) it is one VIGIL kept over its giver: one death in
 * the citadel that ends in rising before their shrine with everything they carried. Thirteen lie in the citadel's
 * chests, one in each of thirteen, never by chance (tools/loot_rules.py). How many a player has kept is said at the
 * shrine and at every death.
 */
public class VigilShardItem extends Item {

    public VigilShardItem() {
        super(new Properties().stacksTo(16).rarity(Rarity.RARE));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozen_dominion.vigil_shard.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.frozen_dominion.vigil_shard.desc2").withStyle(ChatFormatting.GRAY));
    }
}
