package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * THE FLOOR SHAKES. A small, general camera shake: anything heavy calls {@link #shake} with where it landed, how hard (degrees
 * of camera swing at its own feet), how far it carries (blocks; nothing past that) and how long it lasts (ticks). Each
 * one falls off with the distance and dies away with time, squared, so it cracks and lets go instead of sagging;
 * several at once add. Nothing shakes while a cutscene owns the camera (ClientEvents.cutsceneActive), and it is added
 * on top of whatever else moved the camera this frame - after it, at low priority.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class ScreenShake {

    /** The most the camera is ever swung, all of them together (degrees). */
    private static final float MAX = 4.5F;

    private record Shake(Vec3 at, float strength, float radius, int ticks, long born, float phase) {
    }

    private static final List<Shake> ACTIVE = new ArrayList<>();

    private ScreenShake() {
    }

    /**
     * A blow felt through the floor.
     *
     * @param at       where it landed
     * @param strength degrees of swing at its foot
     * @param radius   blocks: felt to here, falling away linearly (and a little faster near the edge)
     * @param ticks    how long it takes to die away
     */
    public static void shake(Vec3 at, float strength, float radius, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || strength <= 0.0F || ticks <= 0) {
            return;
        }
        if (ACTIVE.size() > 24) {
            ACTIVE.remove(0);
        }
        ACTIVE.add(new Shake(at, strength, radius, ticks, mc.level.getGameTime(), mc.level.random.nextFloat() * 6.28F));
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        long now = mc.level.getGameTime();
        ACTIVE.removeIf(s -> now - s.born() > s.ticks() || now < s.born());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (ACTIVE.isEmpty() || mc.player == null || mc.level == null || mc.isPaused()
                || ClientEvents.cutsceneActive()) {
            return;
        }
        double pt = event.getPartialTick();
        double time = mc.level.getGameTime() + pt;
        Vec3 eye = mc.player.getEyePosition((float) pt);
        float pitch = 0.0F, yaw = 0.0F, roll = 0.0F;
        for (Shake s : ACTIVE) {
            double age = time - s.born();
            if (age < 0.0D || age >= s.ticks()) {
                continue;
            }
            double d = Math.sqrt(eye.distanceToSqr(s.at()));
            double reach = Mth.clamp(1.0D - d / s.radius(), 0.0D, 1.0D);
            if (reach <= 0.0D) {
                continue;
            }
            double k = 1.0D - age / s.ticks();
            double amp = s.strength() * reach * Math.sqrt(reach) * k * k;
            double ph = s.phase();
            // three incommensurate wobbles, fast at first: a jolt, not a sway
            pitch += (float) (Math.sin(time * 3.1D + ph) * amp);
            yaw += (float) (Math.cos(time * 2.7D + ph * 1.7D) * amp * 0.7D);
            roll += (float) (Math.sin(time * 3.7D + ph * 0.6D) * amp * 0.55D);
        }
        float total = Math.abs(pitch) + Math.abs(yaw) + Math.abs(roll);
        if (total <= 1.0E-4F) {
            return;
        }
        float cap = total > MAX ? MAX / total : 1.0F;
        event.setPitch(event.getPitch() + pitch * cap);
        event.setYaw(event.getYaw() + yaw * cap);
        event.setRoll(event.getRoll() + roll * cap);
    }
}
