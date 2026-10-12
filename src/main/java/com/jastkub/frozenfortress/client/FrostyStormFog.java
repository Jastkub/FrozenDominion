package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.FrostyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * FROSTY'S BLIZZARD, as the eye sees it (FrostyEntity BLIZZARD): while he has the storm down on his den, whoever is in
 * it sees a white wall close round them - the far edge of the fog drawn in to ten blocks, the colour of driven snow -
 * and in it he circles where they cannot see him. Eased in and out over two seconds.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FrostyStormFog {

    private static float storm, stormO;

    private FrostyStormFog() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        stormO = storm;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        boolean in = false;
        if (p != null && mc.level != null) {
            for (FrostyEntity f : mc.level.getEntitiesOfClass(FrostyEntity.class, p.getBoundingBox().inflate(40.0D))) {
                if (f.stormy() && f.isAlive() && f.den().contains(p.position())) {
                    in = true;
                    break;
                }
            }
        }
        storm = Mth.clamp(storm + (in ? 0.025F : -0.025F), 0.0F, 1.0F);
    }

    private static float now(double partial) {
        return Mth.lerp((float) partial, stormO, storm);
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        float k = now(event.getPartialTick());
        if (k <= 0.0F) {
            return;
        }
        float far = Mth.lerp(k, event.getFarPlaneDistance(), 10.0F);
        float near = Mth.lerp(k, event.getNearPlaneDistance(), 0.0F);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(near);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        float k = now(event.getPartialTick());
        if (k <= 0.0F) {
            return;
        }
        event.setRed(Mth.lerp(k, event.getRed(), 0.80F));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.86F));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.94F));
    }
}
