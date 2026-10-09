package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.integration.curios.CuriosHooks;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFItems;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The reactive half of the artifacts - the parts that fire off a hit or a
 * kill rather than off a tick. The passive halves live in
 * {@link com.jastkub.frozenfortress.integration.curios.CuriosHooks}.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class ArtifactEffects {

    /** Crown: how long the cold clings to something the wearer struck. */
    private static final int TOLL_DURATION = 100;
    /** Crown: radius and damage of the burst when a frostbitten victim dies. */
    private static final double SHATTER_RADIUS = 4.5D;
    private static final float SHATTER_DAMAGE = 7.0F;
    /** Lodestone: how hard a struck target is dragged back toward the wearer. */
    private static final double HAUL = 0.42D;

    /**
     * Crown of the Hollow King - Sovereign's Toll: everything the wearer hits
     * starts freezing. Lodestone - Warden's Haul: everything the wearer hits
     * gets pulled back in, so nothing gets to fight them at arm's length.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)
                || attacker.level().isClientSide) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim == attacker || victim.isAlliedTo(attacker)) {
            return;
        }

        if (CuriosHooks.isEquipped(attacker, FFItems.CROWN_OF_THE_HOLLOW_KING.get())) {
            victim.addEffect(new MobEffectInstance(
                    FFEffects.FROSTBITE.get(), TOLL_DURATION, 0), attacker);
        }

        if (CuriosHooks.isEquipped(attacker, FFItems.WARDENS_LODESTONE.get())) {
            Vec3 pull = attacker.position().subtract(victim.position());
            if (pull.lengthSqr() > 1.0D) {
                // Scaled by the victim's own weight, exactly like the
                // Lodestone's active pull, so bosses barely shift.
                double resistance = victim.getAttribute(
                        net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) != null
                        ? victim.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)
                        : 0.0D;
                double strength = Math.max(0.0D, 1.0D - resistance) * HAUL;
                if (strength > 0.01D) {
                    Vec3 step = pull.normalize().scale(strength);
                    victim.push(step.x, 0.08D, step.z);
                    victim.hurtMarked = true;
                }
            }
        }
    }

    /**
     * Crown of the Hollow King - Hollow Shatter: anything that dies while the
     * wearer's cold is still on it bursts, spreading that same cold outward.
     * A crowd killed one at a time turns into a chain.
     *
     * <p>LOWEST priority so the Frostheart Totem gets to cancel a player's
     * death before this ever looks at it.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide
                || !(event.getSource().getEntity() instanceof Player killer)
                || !victim.hasEffect(FFEffects.FROSTBITE.get())
                || !CuriosHooks.isEquipped(killer, FFItems.CROWN_OF_THE_HOLLOW_KING.get())) {
            return;
        }

        ServerLevel level = (ServerLevel) victim.level();
        Vec3 centre = victim.position();

        for (LivingEntity bystander : level.getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(SHATTER_RADIUS),
                other -> other != victim && other != killer && other.isAlive()
                        && !other.isAlliedTo(killer) && !(other instanceof Player))) {
            bystander.hurt(level.damageSources().playerAttack(killer), SHATTER_DAMAGE);
            bystander.addEffect(new MobEffectInstance(
                    FFEffects.FROSTBITE.get(), TOLL_DURATION, 0), killer);
            Vec3 away = bystander.position().subtract(centre);
            if (away.lengthSqr() > 0.01D) {
                Vec3 shove = away.normalize().scale(0.4D);
                bystander.push(shove.x, 0.25D, shove.z);
            }
        }

        level.sendParticles(FFParticles.ICE_SHARD.get(),
                centre.x, centre.y + 0.9D, centre.z, 40, 0.5D, 0.5D, 0.5D, 0.35D);
        level.sendParticles(FFParticles.SHOCKWAVE.get(),
                centre.x, centre.y + 0.2D, centre.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, victim.blockPosition(), FFSounds.FROST_RELEASE.get(),
                SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    private ArtifactEffects() {
    }
}
