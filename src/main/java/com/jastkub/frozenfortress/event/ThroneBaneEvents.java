package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.item.ThroneBaneSkills;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What Zmora Tronu's skills need from the game every tick (ThroneBaneSkills), on the Forge bus by its annotation:
 * its moves ticked for every player on both sides, the fall that ends a leap or a plunge spared, the stunned kept on
 * the ground and standing, a leaving player's state forgotten.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class ThroneBaneEvents {

    private ThroneBaneEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        ThroneBaneSkills.tick(event.player, event.phase == TickEvent.Phase.START);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player p && !p.level().isClientSide && ThroneBaneSkills.protects(p)) {
            event.setCanceled(true);
        }
    }

    /** The stunned do not jump: whatever the jump gave them going up is taken back. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity e = event.getEntity();
        if (!e.level().isClientSide && ThroneBaneSkills.isStunned(e)) {
            Vec3 dm = e.getDeltaMovement();
            e.setDeltaMovement(dm.x * 0.2D, Math.min(dm.y, 0.0D), dm.z * 0.2D);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ThroneBaneSkills.tickStuns();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ThroneBaneSkills.forget(event.getEntity());
    }
}
