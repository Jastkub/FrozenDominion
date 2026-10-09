package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFItems;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Crown of the Hollow King - Velkhar's shattered crown, reforged.
 * A trophy that shields its bearer from all cold.
 */
public class CrownOfTheHollowKingItem extends ArmorItem {

    public static final ArmorMaterial HOLLOW_CROWN = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(Type type) {
            return 592;
        }

        @Override
        public int getDefenseForType(Type type) {
            return 4;
        }

        @Override
        public int getEnchantmentValue() {
            return 20;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_NETHERITE;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(FFItems.EVERFROST_SHARD.get());
        }

        @Override
        public String getName() {
            return "frozen_dominion:hollow_crown";
        }

        @Override
        public float getToughness() {
            return 3.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.1F;
        }
    };

    /** Its armour layer is clear: the crown on a head is its 3D model, drawn by CrownHeadLayer. */
    @Override
    public String getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity,
                                  net.minecraft.world.entity.EquipmentSlot slot, String type) {
        return "frozen_dominion:textures/models/armor/clear_layer.png";
    }

    public CrownOfTheHollowKingItem() {
        super(HOLLOW_CROWN, Type.HELMET, new net.minecraft.world.item.Item.Properties()
                .rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public void onArmorTick(ItemStack stack, Level level, Player player) {
        if (!level.isClientSide) {
            player.setTicksFrozen(0);
            if (player.hasEffect(FFEffects.FROSTBITE.get())) {
                player.removeEffect(FFEffects.FROSTBITE.get());
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozen_dominion.crown_of_the_hollow_king.desc"));
        tooltip.add(Component.translatable("item.frozen_dominion.crown_of_the_hollow_king.lore"));
        com.jastkub.frozenfortress.integration.curios.CuriosHooks.appendSlotTooltip(tooltip, "head");
    }
}
