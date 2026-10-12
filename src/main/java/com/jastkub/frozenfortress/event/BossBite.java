package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.config.FFConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A BOSS'S BLOW STILL BITES. The minibosses' ceiling (BossCeiling) keeps a
 * strong weapon from cutting a fight short; this is its other half - strong armour and great health from other mods
 * must not turn a boss's blow into nothing:
 * <ul>
 *   <li>its blow grows with the player's maximum health past the vanilla twenty (bossHealthScaling), so forty hearts
 *       of another mod's making are not twice as far from death as they should be;</li>
 *   <li>of what is left of it after everything that protects the player - armour, enchantments, resistance, other
 *       mods' reductions - never less than bossDamageFloor of the blow reaches them. A shield that blocks it still
 *       blocks it all: a skill, not a stat.</li>
 * </ul>
 * Who counts: the keepers of the citadel's halls and the Vault Warden (PartyScaling.SCALED, but for the two below).
 * NOT the Monstrosity and Velkhar: their blows were written for the heaviest armour of other mods already (Velkhar's
 * "three hearts through Ignitium" is 150) - a floor under those would end a player in vanilla iron in one blow.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class BossBite {

    /** The blow as it was before anything took from it (this tick's), by whom it struck. */
    private static final Map<LivingEntity, Float> RAW = new WeakHashMap<>();

    /** Whose blows are left as they are (see the class's note). */
    private static final java.util.Set<String> UNTOUCHED =
            java.util.Set.of("frozen_dominion:velkhar", "frozen_dominion:ice_monstrosity");

    private BossBite() {
    }

    private static boolean bitten(LivingIncomingDamageEvent e) {
        return e.getEntity() instanceof Player p && !p.level().isClientSide
                && e.getSource().getEntity() instanceof LivingEntity boss && PartyScaling.scaled(boss)
                && !UNTOUCHED.contains(String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(boss.getType())));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!bitten(event) || !FFConfig.loaded) {
            return;
        }
        LivingEntity p = event.getEntity();
        float amount = event.getAmount();
        double k = FFConfig.COMMON.bossHealthScaling.get();
        if (k > 0.0D && p.getMaxHealth() > 20.0F) {
            double grow = Math.min(FFConfig.COMMON.bossHealthScalingMax.get(), Math.pow(p.getMaxHealth() / 20.0D, k));
            amount *= (float) grow;
            event.setAmount(amount);
        }
        RAW.put(p, amount);
    }

    /**
     * GOLDEN HEARTS TAKE IT AS THEY ALWAYS DO: the new damage here is what is left after the golden hearts ate
     * what they could, so what got through is that and what they took; and what the floor adds lands on the golden
     * hearts first, like any blow.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        Float raw = RAW.remove(event.getEntity());
        if (raw == null || !FFConfig.loaded) {
            return;
        }
        LivingEntity p = event.getEntity();
        float through = event.getNewDamage()
                + event.getContainer().getReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ABSORPTION);
        // (nothing of it reached them, golden hearts or red: a shield took it - a shield blocks it all)
        if (through <= 0.0F) {
            return;
        }
        float floor = raw * FFConfig.COMMON.bossDamageFloor.get().floatValue();
        if (through < floor) {
            float extra = floor - through;
            float gold = p.getAbsorptionAmount();
            float soak = Math.min(extra, gold);
            p.setAbsorptionAmount(gold - soak);
            event.setNewDamage(event.getNewDamage() + extra - soak);
        }
    }
}
