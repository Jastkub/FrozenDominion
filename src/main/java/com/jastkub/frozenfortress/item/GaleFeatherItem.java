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
 * A GALE FEATHER:
 * a feather of the storm's own ice. In the Eye of the Storm the gust throws you back up once in a fight for nothing;
 * after that each fall eats one feather carried in the pack, and each costs half your health as the first did
 * (StormEyeArena.fall). Four lie in the citadel, by the runes in the hidden rooms of the Prisons, the Crypts, the Storm
 * and the Depths. Those eaten in a fight
 * you lose come back with your things at the shrine (FrostShrineBlockEntity: "Loose") - spent for good only in the
 * fight you win.
 */
public class GaleFeatherItem extends Item {

    public GaleFeatherItem() {
        super(new Properties().stacksTo(16).rarity(Rarity.RARE));
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozen_dominion.gale_feather.desc"));
        tooltip.add(Component.translatable("item.frozen_dominion.gale_feather.desc2"));
    }
}
