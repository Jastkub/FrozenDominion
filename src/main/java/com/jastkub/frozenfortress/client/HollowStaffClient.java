package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.item.HollowStaffItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The client's side of the Staff of the Hollow King (07.10.2026): the "frozen_dominion:requiem" item property (1 while
 * Requiem lasts - models/item/hollow_kings_staff.json switches to the lit model, its crown and halo alight), and the
 * client's game time for the item's Requiem bar (HollowStaffItem#getBarWidth, reached through DistExecutor).
 * Register from FMLClientSetupEvent#enqueueWork: HollowStaffClient.registerItemProperties(FFItems.HOLLOW_KINGS_STAFF.get()).
 */
public final class HollowStaffClient {

    private HollowStaffClient() {
    }

    public static void registerItemProperties(Item staff) {
        ItemProperties.register(staff, FrozenFortress.id("requiem"), (stack, level, entity, seed) -> {
            Level l = level != null ? level : (entity != null ? entity.level() : Minecraft.getInstance().level);
            return HollowStaffItem.requiemLit(stack, l) ? 1.0F : 0.0F;
        });
    }

    /** The client world's game time, or -1 out of a world. */
    public static Long gameTime() {
        ClientLevel l = Minecraft.getInstance().level;
        return l == null ? -1L : l.getGameTime();
    }
}
