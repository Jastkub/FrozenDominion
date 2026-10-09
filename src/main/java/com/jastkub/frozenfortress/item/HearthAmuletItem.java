package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.integration.curios.CuriosHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Hearth Amulet: a live coal from the watchtower's hearth, sealed in brass
 *. Worn at the neck it keeps the Stormcrown's Chill away -
 * and only that: it is no armour against any other cold. One lies in every
 * watchtower, and nothing makes another.
 */
public class HearthAmuletItem extends Item {

    public HearthAmuletItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CuriosHooks.appendSlotTooltip(tooltip, "necklace");
        tooltip.add(Component.translatable("item.frozen_dominion.hearth_amulet.desc"));
        tooltip.add(Component.translatable("item.frozen_dominion.hearth_amulet.lore"));
    }
}
