package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.StormEyeArena;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * NO WAY OUT OF THE EYE OF THE STORM but through him. The arena itself (StormEyeArena) clips wings, shocks the walls and the lid and catches falls;
 * this stops the three things it cannot see coming: a pearl or a chorus fruit across the walls, a block put down
 * to stand on (a bridge, a pillar, a platform under the floes), and a bucket of water poured to break a fall.
 *
 * <p>Survival players only: a creative player is watching, not fighting.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StormEyeEvents {

    private StormEyeEvents() {
    }

    // ------------------------------------------------------------------------------------------------ coming back
    /** Players who logged in carrying the mark of the sky, and the game time to look at them. */
    private static final java.util.Map<java.util.UUID, Long> PENDING = new java.util.HashMap<>();

    /**
     * LOGGED OUT UP THERE, LOGGED IN AFTER THE SKY WAS TAKEN DOWN. A player who left in the middle of the fight comes
     * back where he left - over a storm that may no longer exist. He falls softly for a while; five seconds after he is
     * in, if no arena holds him (the king's own load rebuilds it if the fight is still on), he is set down in the hall.
     */
    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p
                && p.getPersistentData().contains(StormEyeArena.HOME_TAG)) {
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOW_FALLING, 200, 0, false, false));
            PENDING.put(p.getUUID(), p.level().getGameTime() + 100L);
        }
    }

    /** Dying ends a player's part in the fight: the mark goes. */
    @SubscribeEvent
    public static void onRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        event.getEntity().getPersistentData().remove(StormEyeArena.HOME_TAG);
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        net.minecraft.server.MinecraftServer server = event.getServer();
        java.util.Iterator<java.util.Map.Entry<java.util.UUID, Long>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<java.util.UUID, Long> e = it.next();
            net.minecraft.server.level.ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            if (p.level().getGameTime() < e.getValue()) {
                continue;
            }
            it.remove();
            if (!(p.level() instanceof ServerLevel level) || StormEyeArena.at(level, p.position()) != null) {
                continue;                                   // the fight is still on: he is in it
            }
            net.minecraft.core.BlockPos home = net.minecraft.core.BlockPos.of(
                    p.getPersistentData().getLong(StormEyeArena.HOME_TAG));
            p.getPersistentData().remove(StormEyeArena.HOME_TAG);
            double dx = p.getX() - home.getX(), dz = p.getZ() - home.getZ();
            if (p.getY() > home.getY() + 40 && dx * dx + dz * dz < 48.0D * 48.0D) {
                p.teleportTo(level, home.getX() + 0.5D, home.getY() + 0.2D, home.getZ() + 0.5D, p.getYRot(), p.getXRot());
                p.fallDistance = 0.0F;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ carried
    /** HELD IN HIS HAND, they cannot get down (shift would): only he lets go - or death, or leaving the game. */
    @SubscribeEvent
    public static void onHeldMount(net.minecraftforge.event.entity.EntityMountEvent event) {
        net.minecraft.world.entity.Entity rider = event.getEntityMounting();
        net.minecraft.world.entity.Entity king = event.getEntityBeingMounted();
        if (event.isDismounting() && king instanceof com.jastkub.frozenfortress.entity.boss.VelkharEntity
                && rider != null && StormEyeArena.HELD.contains(rider.getUUID())
                && rider.isAlive() && !rider.isRemoved() && king.isAlive() && !king.isRemoved()) {
            event.setCanceled(true);
        }
    }

    /** Whoever the storm is carrying - in his hand, up the column, onto a floe - is not hurt, by anything. */
    @SubscribeEvent
    public static void onCarriedHurt(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity() instanceof Player p && !p.level().isClientSide && StormEyeArena.shielded(p)) {
            event.setCanceled(true);
            p.fallDistance = 0.0F;
        }
    }

    /** ...and hurts nobody: the one in his hand cannot strike him or anything else on the way up. */
    @SubscribeEvent
    public static void onHeldAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (StormEyeArena.HELD.contains(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    /** Leaving the game in his hand: let go first, or the game would save him into the player's file with them. */
    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        Player p = event.getEntity();
        if (StormEyeArena.HELD.remove(p.getUUID()) && p.isPassenger()) {
            p.stopRiding();
        }
        StormEyeArena.SHIELD_UNTIL.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void onPearl(EntityTeleportEvent.EnderPearl event) {
        refuseTeleport(event, event.getPlayer());
    }

    @SubscribeEvent
    public static void onChorus(EntityTeleportEvent.ChorusFruit event) {
        if (event.getEntityLiving() instanceof Player p) {
            refuseTeleport(event, p);
        }
    }

    private static void refuseTeleport(EntityTeleportEvent event, Player p) {
        if (!(p.level() instanceof ServerLevel level) || p.isCreative()) {
            return;
        }
        StormEyeArena from = StormEyeArena.at(level, event.getPrev());
        StormEyeArena to = StormEyeArena.at(level, event.getTarget());
        if (from != null || to != null) {
            event.setCanceled(true);
            p.displayClientMessage(Component.translatable("message.frozen_dominion.storm_eye_no_escape")
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || (event.getEntity() instanceof Player p && p.isCreative())) {
            return;
        }
        Vec3 at = Vec3.atCenterOf(event.getPos());
        if (StormEyeArena.at(level, at) != null) {
            event.setCanceled(true);
            if (event.getEntity() instanceof Player p) {
                p.displayClientMessage(Component.translatable("message.frozen_dominion.storm_eye_no_build")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
    }

    @SubscribeEvent
    public static void onBucket(FillBucketEvent event) {
        Player p = event.getEntity();
        if (p == null || p.isCreative() || !(p.level() instanceof ServerLevel level)) {
            return;
        }
        if (StormEyeArena.at(level, p.position()) != null) {
            event.setCanceled(true);
            p.displayClientMessage(Component.translatable("message.frozen_dominion.storm_eye_no_build")
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }
}
