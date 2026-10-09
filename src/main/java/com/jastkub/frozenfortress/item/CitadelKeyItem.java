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
 * A key, a seal or a piece of one: says in its tooltip what it opens, and -
 * for the ones that matter most - shines.
 */
public class CitadelKeyItem extends Item {

    private final boolean shines;

    public CitadelKeyItem(Rarity rarity, int stack, boolean shines) {
        super(new Properties().rarity(rarity).stacksTo(stack).fireResistant());
        this.shines = shines;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return shines || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
