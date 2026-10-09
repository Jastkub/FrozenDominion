package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.item.ThroneBaneItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Zmora Tronu on the client (07.10.2026):
 * <ul>
 * <li>THE CAMERA SHAKE its wielder feels - the dash rumbling under him, the slam and the plunge landing, the
 *     throne-slayer let go, Gniew Tura waking (ThroneBaneSkills.clientShake; only the wielder's own camera).
 *     Registered by its annotation on the Forge bus, client only.</li>
 * <li>{@link #blaze}: the item property frozen_dominion:blaze (registered in ClientSetup) - 1 while the blade burns:
 *     Gniew Tura's charged form, or Tronobojca wound (held two seconds). models/item/throne_bane.json overrides to
 *     throne_bane_blazing on it.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class ThroneBaneClient {

    private static float amplitude;
    private static int left;
    private static int length = 1;
    private static boolean wasCharged;

    private ThroneBaneClient() {
    }

    /** Shake the wielder's own camera: `amp` about a degree and a half at 1, dying away over `ticks`. */
    public static void shake(float amp, int ticks) {
        if (amp >= amplitude * left / (float) Math.max(1, length)) {
            amplitude = amp;
            left = ticks;
            length = Math.max(1, ticks);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (left > 0) {
            left--;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            wasCharged = false;
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        boolean charged = held.getItem() instanceof ThroneBaneItem && ThroneBaneItem.isCharged(held, mc.level);
        if (charged && !wasCharged) {
            shake(0.9F, 14);                                  // Gniew Tura wakes
        }
        wasCharged = charged;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (left <= 0 || Minecraft.getInstance().options.getCameraType().isMirrored()) {
            return;
        }
        float t = (float) (left - event.getPartialTick());
        float k = amplitude * Mth.clamp(t / length, 0.0F, 1.0F) * 1.5F;
        double time = (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L)
                + event.getPartialTick();
        event.setPitch(event.getPitch() + k * (float) Math.sin(time * 2.9D));
        event.setYaw(event.getYaw() + k * 0.7F * (float) Math.sin(time * 2.3D + 1.3D));
        event.setRoll(event.getRoll() + k * 0.8F * (float) Math.sin(time * 3.7D + 0.4D));
    }

    /** The item property frozen_dominion:blaze. */
    public static float blaze(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        ClientLevel lv = level != null ? level : Minecraft.getInstance().level;
        if (lv != null && ThroneBaneItem.isCharged(stack, lv)) {
            return 1.0F;
        }
        if (entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                && stack.getUseDuration() - entity.getUseItemRemainingTicks() >= ThroneBaneItem.WOUND) {
            return 1.0F;
        }
        return 0.0F;
    }
}
