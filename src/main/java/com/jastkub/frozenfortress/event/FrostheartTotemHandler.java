package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity;
import com.jastkub.frozenfortress.integration.curios.CuriosHooks;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the Frostheart Totem actually does, kept apart from the item class
 * itself because a totem is not something a player ever chooses to use -
 * it is something that goes off on its own the one moment it matters.
 *
 * <p>Modelled on vanilla's own totem save (health to 1, fire out, a run of
 * buffs) but with one thing vanilla's never had: the same cold that was
 * about to kill the wielder gets thrown back outward at whatever was
 * closing in on them.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class FrostheartTotemHandler {

    private static final double BURST_RADIUS = 6.0D;
    private static final float BURST_DAMAGE = 8.0F;
    private static final int RING_SPIKES = 8;
    private static final double RING_RADIUS = 2.6D;

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }

        ItemStack stack = findTotem(player);
        if (stack.isEmpty()) {
            return;
        }

        event.setCanceled(true);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        player.setHealth(1.0F);
        player.clearFire();
        player.removeEffect(FFEffects.FROSTBITE.get());
        player.setTicksFrozen(0);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));

        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                player.getX(), player.getY(1.0D), player.getZ(), 60, 0.6D, 0.9D, 0.6D, 0.08D);

        // the cold that nearly took him goes out one last time, in every direction
        AABB burst = player.getBoundingBox().inflate(BURST_RADIUS);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, burst,
                other -> other != player && other.isAlive() && !other.isAlliedTo(player))) {
            victim.hurt(level.damageSources().playerAttack(player), BURST_DAMAGE);
            victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 160, 1));
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
            double dx = victim.getX() - player.getX();
            double dz = victim.getZ() - player.getZ();
            double dist = Math.max(0.5D, Math.hypot(dx, dz));
            victim.push(dx / dist * 0.9D, 0.35D, dz / dist * 0.9D);
        }

        // A ring of spikes goes up where the burst pushed everything out to,
        // so the save buys real seconds instead of just resetting the fight
        // to the same crowd standing on top of you.
        for (int i = 0; i < RING_SPIKES; i++) {
            double angle = Math.PI * 2.0D * i / RING_SPIKES;
            level.addFreshEntity(new IceSpikeEntity(level, player,
                    player.getX() + Math.cos(angle) * RING_RADIUS, player.getY(),
                    player.getZ() + Math.sin(angle) * RING_RADIUS, 6.0F, 2 + i % 3));
        }
    }

    /**
     * Either hand first, then the charm slot. Returns
     * the live stack so the caller can shrink the one that actually saved
     * the player, wherever it was being carried.
     */
    private static ItemStack findTotem(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(FFItems.FROSTHEART_TOTEM.get())) {
                return held;
            }
        }
        return CuriosHooks.findEquipped(player, FFItems.FROSTHEART_TOTEM.get());
    }

    private FrostheartTotemHandler() {
    }
}
