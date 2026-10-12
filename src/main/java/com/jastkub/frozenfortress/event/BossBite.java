package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.config.FFConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class BossBite {

    /** The blow as it was before anything took from it, and the golden hearts its target had then (this tick's). */
    private static final Map<LivingEntity, float[]> RAW = new WeakHashMap<>();

    /** Whose blows are left as they are (see the class's note). */
    private static final java.util.Set<String> UNTOUCHED =
            java.util.Set.of("frozen_dominion:velkhar", "frozen_dominion:ice_monstrosity");

    private BossBite() {
    }

    private static boolean bitten(LivingHurtEvent e) {
        return e.getEntity() instanceof Player p && !p.level().isClientSide
                && e.getSource().getEntity() instanceof LivingEntity boss && PartyScaling.scaled(boss)
                && !UNTOUCHED.contains(String.valueOf(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(boss.getType())));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
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
        RAW.put(p, new float[]{amount, p.getAbsorptionAmount()});
    }

    /**
     * GOLDEN HEARTS TAKE IT AS THEY ALWAYS DO: this event comes after the golden hearts have eaten what they
     * could, so the floor counted only what reached the red ones - a blow the golden hearts took whole read as nothing
     * and was raised to the floor, straight into the red. What got through is what reached either; and what the floor
     * adds lands on the golden hearts first, like any blow.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        float[] at = RAW.remove(event.getEntity());
        if (at == null || !FFConfig.loaded) {
            return;
        }
        LivingEntity p = event.getEntity();
        float gold = p.getAbsorptionAmount();
        float through = event.getAmount() + Math.max(0.0F, at[1] - gold);
        float floor = at[0] * FFConfig.COMMON.bossDamageFloor.get().floatValue();
        if (through < floor) {
            float extra = floor - through;
            float soak = Math.min(extra, gold);
            p.setAbsorptionAmount(gold - soak);
            event.setAmount(event.getAmount() + extra - soak);
        }
    }
}
