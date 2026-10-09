package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFItems;
import com.jastkub.frozenfortress.registry.FFArmorMaterials;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Crown of the Hollow King - Velkhar's shattered crown, reforged.
 * A trophy that shields its bearer from all cold.
 */
public class CrownOfTheHollowKingItem extends ArmorItem {

    /** Its armour layer is clear: the crown on a head is its 3D model, drawn by CrownHeadLayer. */
    private static final ResourceLocation CLEAR = ResourceLocation.parse("frozen_dominion:textures/models/armor/clear_layer.png");

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, net.minecraft.world.entity.EquipmentSlot slot,
                                            ArmorMaterial.Layer layer, boolean innerModel) {
        return CLEAR;
    }

    /** (the material: FFArmorMaterials.HOLLOW_CROWN; its durability now goes on the piece) */
    public CrownOfTheHollowKingItem() {
        super(FFArmorMaterials.HOLLOW_CROWN, Type.HELMET, new net.minecraft.world.item.Item.Properties()
                .rarity(Rarity.EPIC).fireResistant().durability(FFArmorMaterials.HOLLOW_CROWN_DURABILITY));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        // (1.21.1 has no onArmorTick: the same tick, for the crown actually worn)
        if (entity instanceof Player player && EverfrostArmorItem.worn(player, stack)) {
            onArmorTick(stack, level, player);
        }
    }

    public void onArmorTick(ItemStack stack, Level level, Player player) {
        if (!level.isClientSide) {
            player.setTicksFrozen(0);
            if (player.hasEffect(FFEffects.FROSTBITE)) {
                player.removeEffect(FFEffects.FROSTBITE);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozen_dominion.crown_of_the_hollow_king.desc"));
        tooltip.add(Component.translatable("item.frozen_dominion.crown_of_the_hollow_king.lore"));
        com.jastkub.frozenfortress.integration.curios.CuriosHooks.appendSlotTooltip(tooltip, "head");
    }
}
