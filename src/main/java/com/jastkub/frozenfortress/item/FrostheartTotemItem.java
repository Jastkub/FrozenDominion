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
 * Frostheart Totem - the one relic in the whole hoard the Warden never
 * touched, because it is not treasure, it is a promise. Carried in either
 * hand, it answers a killing blow the way the fortress itself never
 * answered Velkhar's: it does not let the cold finish what it started. See
 * {@link com.jastkub.frozenfortress.event.FrostheartTotemHandler} for what
 * actually happens - this class is just the item sitting in the slot.
 */
public class FrostheartTotemItem extends Item {

    public FrostheartTotemItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozen_dominion.frostheart_totem.desc1"));
        tooltip.add(Component.translatable("item.frozen_dominion.frostheart_totem.desc2"));
        tooltip.add(Component.translatable("item.frozen_dominion.frostheart_totem.lore"));
        com.jastkub.frozenfortress.integration.curios.CuriosHooks.appendSlotTooltip(tooltip, "charm");
    }
}
