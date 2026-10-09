package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.registry.FFItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * SWIECA LATARNIKA - THE LAMPLIGHTER'S CANDLE: the stub he lit
 * the great lantern with, still burning cold. Worn (a charm), it thins the frost that hangs in a keeper's doorway to
 * a little over a third (FrostVeil) and pushes the citadel's haze back (CitadelMist) - you see further, and see what
 * waits. It does its work on your own client; it changes nothing for anyone else.
 */
public class LamplighterCandleItem extends Item {

    public LamplighterCandleItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    /** Is it worn (a charm, through Curios)? */
    public static boolean worn(@Nullable LivingEntity who) {
        return who != null && com.jastkub.frozenfortress.integration.curios.CuriosHooks.isEquipped(who,
                FFItems.LAMPLIGHTER_CANDLE.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.frozen_dominion.lamplighter_candle.desc1").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.frozen_dominion.lamplighter_candle.desc2").withStyle(ChatFormatting.DARK_AQUA));
    }
}
