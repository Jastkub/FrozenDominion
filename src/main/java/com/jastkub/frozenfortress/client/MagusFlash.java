package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * THE FLASH, ON THE EYE: the instant the
 * Twin Blades become the Hollow Magus (VelkharEntity.P3_FLASH) the screen goes white and comes back over six ticks -
 * about a third of a second - so the change happens inside the light. The light itself, the shell bursting off him
 * and the ring on the floor, is geometry (VelkharRenderer.renderMagusFlash); this is only the glare of it.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class MagusFlash {

    private static final float TICKS = 6.0F;
    private static final double REACH = 48.0D;

    private MagusFlash() {
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.VIGNETTE.id())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        VelkharEntity king = ClientEvents.getActiveBoss();
        if (king == null || mc.player == null || king.distanceToSqr(mc.player) > REACH * REACH) {
            return;
        }
        float age = king.tickCount - king.magusFlashAt + event.getPartialTick();
        if (age < 0.0F || age >= TICKS) {
            return;
        }
        float k = 1.0F - age / TICKS;
        int a = Math.round(235.0F * k * k);
        if (a <= 0) {
            return;
        }
        GuiGraphics gg = event.getGuiGraphics();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        gg.fill(0, 0, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(),
                (a << 24) | 0xEEF6FF);
        RenderSystem.disableBlend();
    }
}
