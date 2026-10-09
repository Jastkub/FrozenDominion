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
 * Everfrost plate - forged from the ice that outlived a kingdom.
 * A complete set makes its wearer as untouchable by cold as the dead court.
 */
public class EverfrostArmorItem extends ArmorItem {

    public static final ArmorMaterial EVERFROST = new ArmorMaterial() {
        // in ArmorItem.Type's order - helmet, chestplate, leggings, boots (08.10.2026: they were in the old boots-first
        // order, the chestplate giving 6 and the leggings 8)
        private static final int[] DURABILITY = {11, 16, 15, 13};
        private static final int[] DEFENSE = {3, 8, 6, 3};

        @Override
        public int getDurabilityForType(Type type) {
            return DURABILITY[type.ordinal()] * 34;
        }

        @Override
        public int getDefenseForType(Type type) {
            return DEFENSE[type.ordinal()];
        }

        @Override
        public int getEnchantmentValue() {
            return 16;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_DIAMOND;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(FFItems.EVERFROST_INGOT.get());
        }

        @Override
        public String getName() {
            return "frozen_dominion:everfrost";
        }

        @Override
        public float getToughness() {
            return 2.5F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.05F;
        }
    };

    public EverfrostArmorItem(Type type) {
        super(EVERFROST, type, new Properties().rarity(Rarity.RARE).fireResistant());
    }

    /** For the plate forged on from this one (Kingsrime): its own material, the same Rimeguard. */
    protected EverfrostArmorItem(ArmorMaterial material, Type type, Properties properties) {
        super(material, type, properties);
    }

    /** True when all four Everfrost pieces are worn. */
    public static boolean hasFullSet(Player player) {
        for (ItemStack stack : player.getInventory().armor) {
            if (!(stack.getItem() instanceof EverfrostArmorItem)) {
                return false;
            }
        }
        return true;
    }

    /** Frost stored up from blows taken, spent on the next swing. */
    public static final String CHARGE_TAG = "frozen_dominion:rimeguard";
    private static final int MAX_CHARGE = 5;

    /**
     * Set bonus: Rimeguard.
     *
     * <p>It used to be immunity to Frostbite, which was a poor reward for the
     * hardest craft in the mod - the Crown already grants exactly that, and it
     * drops from the boss you have to beat before you can gather the shards
     * anyway, so the armour's one trick was something you already owned.
     *
     * <p>Instead the plate now feeds on the cold thrown at it. Every hit from
     * a servant of the winter packs another layer of rime into it, up to five,
     * and the wearer's next strike discharges the lot into whatever they hit -
     * turning the fortress's own weapon around on it. Armour that gets better
     * the deeper into the fight you are, rather than armour that switches one
     * hazard off.
     */
    @Override
    public void onArmorTick(ItemStack stack, Level level, Player player) {
        if (level.isClientSide || type != Type.CHESTPLATE || !hasFullSet(player)) {
            return;
        }
        // The plate sheds a little of what it holds if the wearer stays out of
        // the fight, so the charge is something you spend rather than bank.
        if (player.tickCount % 200 == 0) {
            int held = getCharge(player);
            if (held > 0 && player.getLastHurtByMob() == null) {
                setCharge(player, held - 1);
            }
        }
    }

    public static int getCharge(Player player) {
        return player.getPersistentData().getInt(CHARGE_TAG);
    }

    public static void setCharge(Player player, int value) {
        player.getPersistentData().putInt(CHARGE_TAG, Math.max(0, Math.min(MAX_CHARGE, value)));
    }

    public static int maxCharge() {
        return MAX_CHARGE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozen_dominion.everfrost_armor.set"));
        tooltip.add(Component.translatable("item.frozen_dominion.everfrost_armor.set2"));
    }
}
