package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * THE FLASH, ON THE EYE: the instant the
 * Twin Blades become the Hollow Magus (VelkharEntity.P3_FLASH) the screen goes white and comes back over six ticks -
 * about a third of a second - so the change happens inside the light. The light itself, the shell bursting off him
 * and the ring on the floor, is geometry (VelkharRenderer.renderMagusFlash); this is only the glare of it.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class MagusFlash {

    private static final float TICKS = 6.0F;
    private static final double REACH = 48.0D;

    private MagusFlash() {
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.CAMERA_OVERLAYS)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        VelkharEntity king = ClientEvents.getActiveBoss();
        if (king == null || mc.player == null || king.distanceToSqr(mc.player) > REACH * REACH) {
            return;
        }
        float age = king.tickCount - king.magusFlashAt + event.getPartialTick().getGameTimeDeltaPartialTick(true);
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
        gg.fill(0, 0, event.getGuiGraphics().guiWidth(), event.getGuiGraphics().guiHeight(),
                (a << 24) | 0xEEF6FF);
        RenderSystem.disableBlend();
    }
}
