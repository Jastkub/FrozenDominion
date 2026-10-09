package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.DamageEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * THE CITADEL'S ENCHANTMENTS: five of
 * them, at the enchanting table like any other and as books in the citadel's chests.
 * <ul>
 *   <li>ROZLUPANIE (shattering, I-V, weapons): two and a half more a level on the king's own - his servants, his copies,
 *   his colossus, him (FFAllies.ofTheKing). Not with Sharpness, Smite or Bane.</li>
 *   <li>UKASZENIE MROZU (frostbite_edge, I-III, swords): a blow lays Frostbite on whatever it cuts.</li>
 *   <li>ZAR OGNISKA (hearth_ember, I-III, chestplates): a frost heart's cold gets into you 15% a level slower
 *   (FrostHeartBlockEntity).</li>
 *   <li>PEWNY KROK (sure_footing, I-III, boots): every throw that lands on you 20% a level shorter (steady()).</li>
 *   <li>ECHO KROLA (kings_echo, I-II, bows): where an arrow strikes, a spike of ice comes up out of the floor a
 *   second later (CommonEvents).</li>
 * </ul>
 */
public final class FFEnchantments {

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, FrozenFortress.MODID);

    private static final EquipmentSlot[] HAND = {EquipmentSlot.MAINHAND};

    public static final RegistryObject<Enchantment> SHATTERING = ENCHANTMENTS.register("shattering", Shattering::new);
    public static final RegistryObject<Enchantment> FROSTBITE_EDGE = ENCHANTMENTS.register("frostbite_edge", FrostbiteEdge::new);
    public static final RegistryObject<Enchantment> HEARTH_EMBER = ENCHANTMENTS.register("hearth_ember", HearthEmber::new);
    public static final RegistryObject<Enchantment> SURE_FOOTING = ENCHANTMENTS.register("sure_footing", SureFooting::new);
    public static final RegistryObject<Enchantment> KINGS_ECHO = ENCHANTMENTS.register("kings_echo", KingsEcho::new);

    private FFEnchantments() {
    }

    /** The level of `e` on whatever `who` wears or holds in its slots (0 if none). */
    public static int level(RegistryObject<Enchantment> e, LivingEntity who) {
        return who == null ? 0 : EnchantmentHelper.getEnchantmentLevel(e.get(), who);
    }

    /** What is left of a throw that lands on `who` (Pewny Krok): 1 with none, 0.4 at III - and of that, three tenths
     *  with the Glacier Ring worn. Every knockback passes
     *  through here too (CommonEvents.onSureFooting), so this is the ring's one door: no attribute besides it. */
    public static double steady(Entity who) {
        if (!(who instanceof LivingEntity l)) {
            return 1.0D;
        }
        double ring = com.jastkub.frozenfortress.integration.curios.CuriosHooks.isEquipped(l,
                com.jastkub.frozenfortress.registry.FFItems.GLACIER_RING.get()) ? GLACIER_KEEPS : 1.0D;
        return (1.0D - 0.2D * level(SURE_FOOTING, l)) * ring;
    }

    /** What the Glacier Ring leaves of a knockback or a throw: thirty percent (seventy taken off). */
    public static final double GLACIER_KEEPS = 0.3D;

    // ------------------------------------------------------------------------------------------------ the five
    static class Shattering extends Enchantment {
        Shattering() {
            super(Rarity.UNCOMMON, EnchantmentCategory.WEAPON, HAND);
        }

        @Override
        public int getMaxLevel() {
            return 5;
        }

        @Override
        public int getMinCost(int level) {
            return 5 + (level - 1) * 8;
        }

        @Override
        public int getMaxCost(int level) {
            return getMinCost(level) + 20;
        }

        @Override
        protected boolean checkCompatibility(Enchantment other) {
            return !(other instanceof DamageEnchantment) && super.checkCompatibility(other);
        }
    }

    static class FrostbiteEdge extends Enchantment {
        FrostbiteEdge() {
            super(Rarity.RARE, EnchantmentCategory.WEAPON, HAND);
        }

        @Override
        public int getMaxLevel() {
            return 3;
        }

        @Override
        public int getMinCost(int level) {
            return 10 + (level - 1) * 10;
        }

        @Override
        public int getMaxCost(int level) {
            return getMinCost(level) + 30;
        }

        @Override
        protected boolean checkCompatibility(Enchantment other) {
            return other != Enchantments.FIRE_ASPECT && super.checkCompatibility(other);
        }

        @Override
        public void doPostAttack(LivingEntity attacker, Entity target, int level) {
            if (target instanceof LivingEntity victim && !attacker.level().isClientSide) {
                victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 60 + 40 * level, level - 1), attacker);
            }
        }
    }

    static class HearthEmber extends Enchantment {
        HearthEmber() {
            super(Rarity.UNCOMMON, EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
        }

        @Override
        public int getMaxLevel() {
            return 3;
        }

        @Override
        public int getMinCost(int level) {
            return 8 + (level - 1) * 9;
        }

        @Override
        public int getMaxCost(int level) {
            return getMinCost(level) + 25;
        }
    }

    static class SureFooting extends Enchantment {
        SureFooting() {
            super(Rarity.UNCOMMON, EnchantmentCategory.ARMOR_FEET, new EquipmentSlot[]{EquipmentSlot.FEET});
        }

        @Override
        public int getMaxLevel() {
            return 3;
        }

        @Override
        public int getMinCost(int level) {
            return 6 + (level - 1) * 9;
        }

        @Override
        public int getMaxCost(int level) {
            return getMinCost(level) + 25;
        }
    }

    static class KingsEcho extends Enchantment {
        KingsEcho() {
            super(Rarity.RARE, EnchantmentCategory.BOW, HAND);
        }

        @Override
        public int getMaxLevel() {
            return 2;
        }

        @Override
        public int getMinCost(int level) {
            return 15 + (level - 1) * 15;
        }

        @Override
        public int getMaxCost(int level) {
            return getMinCost(level) + 30;
        }

        @Override
        protected boolean checkCompatibility(Enchantment other) {
            return other != Enchantments.MULTISHOT && super.checkCompatibility(other);
        }
    }
}
