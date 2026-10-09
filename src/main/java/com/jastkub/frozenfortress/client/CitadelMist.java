package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.StormcrownBeaconBlockEntity;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * THE CITADEL'S FROST MIST: while the Stormcrown burns for the court, the air in its reach (StormcrownBeaconBlockEntity
 * .RADIUS, the reach of its hold on the pick) hangs with a light, pale, icy haze - the far fog drawn in and paled a
 * little, motes of frost drifting up round you. Taken, it thins away over some ten seconds.
 *
 * <p>Where the crown is and whether it is taken comes from its block entity's client tick (remembered there, so the
 * mist does not lift when its chunk drops out of view). The trap rooms' own cold (ColdOverlay) and Velkhar's dark
 * (ClientEvents) still draw in over this - each takes the nearer fog.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class CitadelMist {

    /** Where the haze starts and where it closes over everything. */
    // (07.10.2026: "lekko podkrec" - it was 110 and 8; - it was 85 and 5, a wall forty off a third gone; now half gone at twenty-five)
    private static final float FAR = 55.0F, NEAR = 3.0F;
    /** Its colour, and how far the fog is turned to it. */
    private static final float RED = 0.62F, GREEN = 0.71F, BLUE = 0.82F, TINT = 0.46F;
    /** Comes in over ~4 s; lifts over ~10 s once the crown is taken. */
    private static final float RISE = 0.0125F, FALL = 0.005F;

    private static float strength;
    private static float lastStrength;

    private CitadelMist() {
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        lastStrength = strength;
        float target = p != null && inMist(p) ? 1.0F : 0.0F;
        strength = target > strength ? Math.min(target, strength + RISE) : Math.max(target, strength - FALL);
        if (p == null || mc.isPaused() || strength <= 0.05F) {
            return;
        }
        // motes of frost drifting up round you
        RandomSource r = p.getRandom();
        if (r.nextFloat() < 0.32F * strength) {
            double x = p.getX() + (r.nextDouble() - 0.5D) * 16.0D;
            double y = p.getY() + r.nextDouble() * 4.0D - 0.5D;
            double z = p.getZ() + (r.nextDouble() - 0.5D) * 16.0D;
            if (p.level().getBlockState(BlockPos.containing(x, y, z)).isAir()) {
                p.level().addParticle(FFParticles.FROST_SWIRL.get(), x, y, z,
                        (r.nextDouble() - 0.5D) * 0.01D, 0.0D, (r.nextDouble() - 0.5D) * 0.01D);
            }
        }
    }

    /** Is `p` in the reach of a Stormcrown that still burns (the one last seen, in this world)? */
    private static boolean inMist(LocalPlayer p) {
        BlockPos at = StormcrownBeaconBlockEntity.clientSeenAt;
        if (at == null || StormcrownBeaconBlockEntity.clientSeenTaken
                || p.level().dimension() != StormcrownBeaconBlockEntity.clientSeenIn || p.isSpectator()) {
            return false;
        }
        int r = StormcrownBeaconBlockEntity.RADIUS;
        return Math.abs(p.getX() - at.getX() - 0.5D) <= r && Math.abs(p.getY() - at.getY()) <= r
                && Math.abs(p.getZ() - at.getZ() - 0.5D) <= r;
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
        // (the Lamplighter's Candle, worn: the haze pushed back - you see further)
        float far = com.jastkub.frozenfortress.item.LamplighterCandleItem.worn(Minecraft.getInstance().player) ? FAR * 1.7F : FAR;
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), Mth.lerp(s, event.getFarPlaneDistance(), far)));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), Mth.lerp(s, event.getNearPlaneDistance(), NEAR)));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float s = strength(event.getPartialTick()) * TINT;
        if (s <= 0.01F) {
            return;
        }
        event.setRed(Mth.lerp(s, event.getRed(), RED));
        event.setGreen(Mth.lerp(s, event.getGreen(), GREEN));
        event.setBlue(Mth.lerp(s, event.getBlue(), BLUE));
    }
}
