package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFItems;
import com.jastkub.frozenfortress.registry.FFArmorMaterials;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
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
 * Everfrost plate - forged from the ice that outlived a kingdom.
 * A complete set makes its wearer as untouchable by cold as the dead court.
 */
public class EverfrostArmorItem extends ArmorItem {

    /** (the material: FFArmorMaterials.EVERFROST; its durability now goes on each piece) */
    public EverfrostArmorItem(Type type) {
        super(FFArmorMaterials.EVERFROST, type, new Properties().rarity(Rarity.RARE).fireResistant()
                .durability(FFArmorMaterials.EVERFROST_DURABILITY[type.ordinal()]));
    }

    /** For the plate forged on from this one (Kingsrime): its own material, the same Rimeguard. */
    protected EverfrostArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
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
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        // (1.21.1 has no onArmorTick: the same tick, for the piece actually worn)
        if (entity instanceof Player player && worn(player, stack)) {
            onArmorTick(stack, level, player);
        }
    }

    /** Whether this very stack sits in one of the player's armour slots (where onArmorTick used to run). */
    public static boolean worn(Player player, ItemStack stack) {
        for (ItemStack piece : player.getInventory().armor) {
            if (piece == stack) {
                return true;
            }
        }
        return false;
    }

    protected void onArmorTick(ItemStack stack, Level level, Player player) {
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozen_dominion.everfrost_armor.set"));
        tooltip.add(Component.translatable("item.frozen_dominion.everfrost_armor.set2"));
    }
}
