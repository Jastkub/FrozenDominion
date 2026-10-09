package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.item.BoneWandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.Item;

/**
 * The client's side of the Wand of the Dead (07.10.2026): the "frozen_dominion:bone_wand_spent" item property (1 while
 * the Call of the Grave cools - models/item/bone_wand.json switches to bone_wand_spent, the skull's eyes gone dark),
 * and the client's game time for the item's bar (BoneWandItem#getBarWidth, reached only on the client).
 * Register from FMLClientSetupEvent#enqueueWork: BoneWandClient.registerItemProperties(FFItems.BONE_WAND.get()).
 */
public final class BoneWandClient {

    private BoneWandClient() {
    }

    public static void registerItemProperties(Item wand) {
        ItemProperties.register(wand, FrozenFortress.id("bone_wand_spent"), (stack, level, entity, seed) -> {
            long now = level != null ? level.getGameTime() : gameTime();
            return BoneWandItem.spent(stack, now) ? 1.0F : 0.0F;
        });
    }

    /** The client world's game time, or -1 out of a world. */
    public static long gameTime() {
        ClientLevel l = Minecraft.getInstance().level;
        return l == null ? -1L : l.getGameTime();
    }
}
