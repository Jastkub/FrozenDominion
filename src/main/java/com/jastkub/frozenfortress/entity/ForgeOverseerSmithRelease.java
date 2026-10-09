package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * THE SMITH'S CHAINS SNAP THE MOMENT THE OVERSEER FALLS: the listener on {@link ForgeOverseerSmithHook.SmithFreedEvent}
 * that frees the king's smith in his cell at once ({@link VelkharSmithEntity#releaseNear}). Without it the smith still
 * goes free, only some ten seconds later (he looks for a living Overseer near him every two seconds). Kept in a file of
 * its own so the Overseer itself never depends on the smith's class.
 *
 * <p>The cell (x 144-160, z 46-62) is some 31 blocks from the middle of the forge hall where the Overseer stands his
 * post, hence the radius.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeOverseerSmithRelease {

    static final double RADIUS = 48.0D;

    private ForgeOverseerSmithRelease() {
    }

    @SubscribeEvent
    public static void onSmithFreed(ForgeOverseerSmithHook.SmithFreedEvent event) {
        VelkharSmithEntity.releaseNear(event.getLevel(), event.getForge(), RADIUS);
    }
}
