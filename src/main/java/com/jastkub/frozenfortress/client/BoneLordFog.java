package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.BoneLordEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * THE CHASM'S FOG: in the Bone Lord's hall the fog closes in, dark and cold, so the bridges
 * round you stand clear and the chasm under them goes down into nothing - the ice spikes on its floor some thirty
 * blocks below are past where the fog shuts. Its hall comes from the Lord himself (BoneLordEntity.arenaBox, synced);
 * it comes in over a second or two and lifts as slowly.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class BoneLordFog {

    /** Where it starts, and where it shuts: the floor of the chasm is past it. */
    private static final float NEAR = 3.0F, FAR = 26.0F;
    private static final float RED = 0.10F, GREEN = 0.13F, BLUE = 0.19F;
    private static final float RISE = 0.04F, FALL = 0.02F;

    private static float strength;
    private static float lastStrength;

    private BoneLordFog() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        lastStrength = strength;
        float target = p != null && inChasm(p) ? 1.0F : 0.0F;
        strength = target > strength ? Math.min(target, strength + RISE) : Math.max(target, strength - FALL);
    }

    private static boolean inChasm(LocalPlayer p) {
        if (p.isSpectator() || p.level() == null) {
            return false;
        }
        for (BoneLordEntity lord : p.level().getEntitiesOfClass(BoneLordEntity.class, p.getBoundingBox().inflate(90.0D))) {
            AABB box = lord.arenaBox();
            if (box != null && box.contains(p.position())) {
                return true;
            }
        }
        return false;
    }

    private static float strength(double partialTick) {
        return (float) Mth.lerp(partialTick, lastStrength, strength);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        float s = strength(event.getPartialTick());
        if (s <= 0.01F) {
            return;
        }
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), Mth.lerp(s, event.getFarPlaneDistance(), FAR)));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), Mth.lerp(s, event.getNearPlaneDistance(), NEAR)));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float s = strength(event.getPartialTick());
        if (s <= 0.01F) {
            return;
        }
        event.setRed(Mth.lerp(s, event.getRed(), RED));
        event.setGreen(Mth.lerp(s, event.getGreen(), GREEN));
        event.setBlue(Mth.lerp(s, event.getBlue(), BLUE));
    }
}
