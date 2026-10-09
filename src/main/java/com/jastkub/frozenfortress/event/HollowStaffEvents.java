package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.HollowStaffMagic;
import com.jastkub.frozenfortress.entity.HollowStaffShadeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.UUID;

/**
 * THE STAFF OF THE HOLLOW KING's passive (07.10.2026): whatever dies carrying the King's MARK (HollowStaffMagic#mark -
 * a Litany rune put it there) rises as the marker's shade for ten seconds (HollowStaffShadeEntity#rise), wherever he
 * is within 48 blocks of it. Not a player, not one of the shades themselves.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class HollowStaffEvents {

    static final double REACH = 48.0D;

    private HollowStaffEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || dead instanceof Player || dead instanceof HollowStaffShadeEntity) {
            return;
        }
        if (!HollowStaffMagic.marked(dead)) {
            return;
        }
        UUID by = HollowStaffMagic.markedBy(dead);
        Player owner = by == null ? null : level.getPlayerByUUID(by);
        if (owner == null || !owner.isAlive() || owner.distanceToSqr(dead) > REACH * REACH) {
            return;
        }
        dead.getPersistentData().remove(HollowStaffMagic.MARK);
        HollowStaffShadeEntity.rise(level, owner, dead);
    }
}
