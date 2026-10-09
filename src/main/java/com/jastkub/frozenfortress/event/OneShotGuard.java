package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharCloneEntity;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * NO ONE-SHOT FROM FULL HEALTH.
 *
 * <p>It was not random. Coming back into the hall a player is far off, and the king answers distance with his gap
 * closers - the shield charge, the chain, the thrown blade, the bash, the gate thrown: two and a half to three and a
 * half hearts of intent each, every one of them at the raw ceiling of 45, which is some 25 through netherite without
 * Protection - more than a whole bar. In close he swings combos, and those leave a bar standing. The ceilings were set
 * for netherite with Protection IV, which takes almost two thirds off on top; without it the first blow ended the
 * attempt.
 *
 * <p>So: a player at nine tenths of their health or more is never killed by one blow of his (his copies' and his
 * missiles' too) - it leaves them a heart. Measured here, on what actually reaches their health after armour,
 * enchantments, resistance and absorption, so it holds in any gear and changes nothing for a player who can take the
 * hit anyway. Two blows in a row still kill; a player already hurt is not covered.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class OneShotGuard {

    /** At this share of their health or more, a blow of his cannot kill them... */
    private static final float GUARDED_FROM = 0.9F;
    /** ...it leaves them this much (one heart). */
    private static final float LEFT = 2.0F;

    private OneShotGuard() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player p) || p.level().isClientSide || p.isCreative()) {
            return;
        }
        Entity by = event.getSource().getEntity();
        if (!(by instanceof VelkharEntity) && !(by instanceof VelkharCloneEntity)) {
            return;
        }
        float health = p.getHealth();                      // (absorption is already taken off the amount here)
        if (health >= p.getMaxHealth() * GUARDED_FROM && event.getNewDamage() >= health) {
            event.setNewDamage(Math.max(0.0F, health - LEFT));
        }
    }
}
