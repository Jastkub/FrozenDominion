package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.BoneLordEntity;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * THE BONE LORD'S GRIP: whoever he grabs rides him in his fist through the slam (BoneLordEntity.positionRider - smooth,
 * where a teleport a tick jolted), and a rider can get off a mount with Shift. Not off him: only his fist opening lets
 * them go (BoneLordEntity.letGo), or a death, his or theirs. And whoever leaves the game in his fist is let go first -
 * the game takes a player's mount away with them when they log out on it, and the Lord would have gone too.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class BoneLordGrip {

    private BoneLordGrip() {
    }

    @SubscribeEvent
    public static void onDismount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof BoneLordEntity lord
                && lord.holds(event.getEntityMounting())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().getVehicle() instanceof BoneLordEntity lord) {
            lord.letGo();
        }
    }
}