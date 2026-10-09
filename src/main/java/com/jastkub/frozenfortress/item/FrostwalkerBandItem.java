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
 * THE FROSTWALKER'S BAND: a band of black iron round the wrist, a bead of sovereign ice in it - worn, nothing slows its wearer
 * (CuriosHooks.Band; CommonEvents.onEffectApplicable). Found in the citadel's chests; nothing makes one.
 */
public class FrostwalkerBandItem extends Item {

    public FrostwalkerBandItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CuriosHooks.appendSlotTooltip(tooltip, "bracelet");
        tooltip.add(Component.translatable("item.frozen_dominion.frostwalker_band.desc"));
    }
}
