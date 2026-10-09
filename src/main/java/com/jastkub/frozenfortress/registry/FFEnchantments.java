package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

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
 *
 * <p>Since 1.21.1 an enchantment is data, not a class: its rules (max level, items, weight, costs, exclusivity) live in
 * data/frozen_dominion/enchantment/&lt;id&gt;.json, and where it shows up (table, loot, trades, mob gear) in the
 * minecraft:non_treasure enchantment tag. What each one does stays code, as before, reading the level through the keys
 * below. The only piece of code the JSON names is Frostbite Edge's blow, an effect type of its own, so it lands at the
 * same moment as the old doPostAttack did.
 */
public final class FFEnchantments {

    /** Frostbite Edge's blow, as the effect type its JSON's post_attack names. */
    public static final DeferredRegister<MapCodec<? extends EnchantmentEntityEffect>> ENTITY_EFFECTS =
            DeferredRegister.create(Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE, FrozenFortress.MODID);

    public static final DeferredHolder<MapCodec<? extends EnchantmentEntityEffect>, MapCodec<FrostbiteEdgeBlow>> FROSTBITE_EDGE_BLOW =
            ENTITY_EFFECTS.register("frostbite_edge", () -> FrostbiteEdgeBlow.CODEC);

    public static final ResourceKey<Enchantment> SHATTERING = key("shattering");
    public static final ResourceKey<Enchantment> FROSTBITE_EDGE = key("frostbite_edge");
    public static final ResourceKey<Enchantment> HEARTH_EMBER = key("hearth_ember");
    public static final ResourceKey<Enchantment> SURE_FOOTING = key("sure_footing");
    public static final ResourceKey<Enchantment> KINGS_ECHO = key("kings_echo");

    private FFEnchantments() {
    }

    private static ResourceKey<Enchantment> key(String id) {
        return ResourceKey.create(Registries.ENCHANTMENT, FrozenFortress.id(id));
    }

    /** The enchantment behind `key` in this world's registry, or null if a datapack took it away. */
    @Nullable
    public static Holder<Enchantment> holder(ResourceKey<Enchantment> key, Level level) {
        if (level == null) {
            return null;
        }
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).<Holder<Enchantment>>map(h -> h).orElse(null);
    }

    /** The level of `e` on whatever `who` wears or holds in its slots (0 if none). */
    public static int level(ResourceKey<Enchantment> e, LivingEntity who) {
        if (who == null) {
            return 0;
        }
        Holder<Enchantment> h = holder(e, who.level());
        return h == null ? 0 : EnchantmentHelper.getEnchantmentLevel(h, who);
    }

    /** The level of `e` on one stack (0 if none). */
    public static int level(ResourceKey<Enchantment> e, ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Holder<Enchantment> h = holder(e, level);
        return h == null ? 0 : stack.getEnchantmentLevel(h);
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

    // ------------------------------------------------------------------------------------------------ frostbite edge
    /**
     * UKASZENIE MROZU's blow: Frostbite on whatever the blade cuts, 3 + 2 s a level, one amplifier a level above the
     * first, laid by the attacker. Its JSON runs it on post_attack (attacker enchanted, victim affected), the hook that
     * replaced Enchantment.doPostAttack.
     */
    public record FrostbiteEdgeBlow() implements EnchantmentEntityEffect {
        public static final MapCodec<FrostbiteEdgeBlow> CODEC = MapCodec.unit(FrostbiteEdgeBlow::new);

        @Override
        public void apply(ServerLevel level, int enchantLevel, EnchantedItemInUse item, Entity target, Vec3 at) {
            if (target instanceof LivingEntity victim) {
                victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 60 + 40 * enchantLevel, enchantLevel - 1), item.owner());
            }
        }

        @Override
        public MapCodec<? extends EnchantmentEntityEffect> codec() {
            return CODEC;
        }
    }
}
