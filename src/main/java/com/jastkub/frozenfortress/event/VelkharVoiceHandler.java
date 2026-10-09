package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * Velkhar's opinion of whoever he just hit.
 *
 * <p>Hung off the damage event rather than off his sword swing, because it
 * should answer <em>anything</em> he lands - beam, spikes, shield, a clone's
 * work - not only melee.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class VelkharVoiceHandler {

    /** Roughly one line per ten seconds of taking hits, at most. */
    private static final int COOLDOWN_MIN = 220;
    private static final int COOLDOWN_SPREAD = 240;
    private static final int CHANCE = 3;

    // (1.21: LivingDamageEvent.Pre fires where LivingIncomingDamageEvent did - only for a blow that lands)
    @SubscribeEvent
    public static void onPlayerHurt(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof VelkharEntity velkhar)) {
            return;
        }
        if (velkhar.tauntCooldown > 0 || velkhar.getRandom().nextInt(CHANCE) != 0) {
            return;
        }
        velkhar.tauntCooldown = COOLDOWN_MIN + velkhar.getRandom().nextInt(COOLDOWN_SPREAD);
        // A charge that connected gets its own line; anything else gets the
        // general one. `dashLanded` is set by the cleave and the spin and
        // cleared here, so it always describes the blow that just happened.
        boolean fromDash = velkhar.dashLanded;
        velkhar.dashLanded = false;
        if (fromDash) {
            velkhar.speak(FFSounds.VELKHAR_LINE_TOO_SLOW.get(), "velkhar.line.too_slow");
        } else {
            velkhar.speak(FFSounds.VELKHAR_PATHETIC.get(), "velkhar.line.pathetic");
        }
    }

    /** He has something to say when a challenger does not get up. */
    @SubscribeEvent
    public static void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (event.getSource().getEntity() instanceof VelkharEntity velkhar) {
            velkhar.tauntCooldown = 0;      // this one always lands
            velkhar.speak(FFSounds.VELKHAR_LINE_DISAPPOINT.get(), "velkhar.line.disappoint");
        }
    }

    /** And an opinion about being hit by someone who cannot hurt him yet. */
    // (1.21: LivingDamageEvent.Pre fires where LivingIncomingDamageEvent did - only for a blow that lands)
    @SubscribeEvent
    public static void onVelkharHurt(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof VelkharEntity velkhar
                && !velkhar.level().isClientSide
                && event.getSource().getEntity() instanceof Player) {
            velkhar.mockDamage();
        }
    }

    private VelkharVoiceHandler() {
    }
}
