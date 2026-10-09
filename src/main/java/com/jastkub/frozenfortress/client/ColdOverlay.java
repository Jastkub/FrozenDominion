package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.FrostHeartBlockEntity;
import com.jastkub.frozenfortress.registry.FFSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A TRAP ROOM'S COLD, AS YOU FEEL IT: while a frost
 * heart's cold is on you (FrostHeartBlockEntity), mist closes in from the edges of the screen over the game's own
 * frost, the air itself thickens - and your heart beats in your ears, slower the deeper the cold goes. All of it
 * follows how frozen you are, and thaws with you by a hearth. (tools/gen_cold_fx.py)
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class ColdOverlay {

    private static final ResourceLocation MIST = FrozenFortress.id("textures/misc/cold_mist.png");
    /** The game's own rime at the screen's rim - drawn here from the heart's cold, which is not the game's freezing. */
    private static final ResourceLocation RIME = new ResourceLocation("textures/misc/powder_snow_outline.png");
    /** How deep a heart's cold is in you (HeartColdPacket), and when the heart last said so. */
    private static float heartDepth;
    private static long heartSaid = -1000L;
    /** Beats begin past this much frost. */
    private static final float BEATS_FROM = 0.2F;

    private static float strength;
    private static float lastStrength;
    private static int toBeat;

    private ColdOverlay() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        float target = 0.0F;
        if (p != null && p.isAlive() && !p.isSpectator() && p.level().getGameTime() - heartSaid < 10L) {
            target = heartDepth;                      // (the heart's own cold)
        }
        lastStrength = strength;
        strength += (target - strength) * (target > strength ? 0.06F : 0.1F);
        if (strength < 0.003F) {
            strength = 0.0F;
        }
        if (p == null || mc.isPaused() || strength < BEATS_FROM) {
            toBeat = Math.min(toBeat, 10);
            return;
        }
        if (--toBeat <= 0) {
            float s = Mth.clamp((strength - BEATS_FROM) / (1.0F - BEATS_FROM), 0.0F, 1.0F);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(FFSounds.COLD_HEARTBEAT.get(),
                    1.0F - 0.16F * s, 0.35F + 0.6F * s));
            toBeat = (int) Mth.lerp(s, 20.0F, 46.0F);             // a beat a second, slowing to one in two and more
        }
    }

    /** The mist: over the game's own frost at the screen's rim, two layers of it drifting. */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.FROSTBITE.id())) {
            return;
        }
        float s = Mth.lerp(event.getPartialTick(), lastStrength, strength);
        if (s <= 0.01F) {
            return;
        }
        float a = 0.8F * s * s * (3.0F - 2.0F * s);
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.player == null ? 0 : mc.player.tickCount) + event.getPartialTick();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        g.setColor(1.0F, 1.0F, 1.0F, s);
        g.blit(RIME, 0, 0, -90, 0.0F, 0.0F, w, h, w, h);
        layer(g, w, h, 1.14F + 0.04F * Mth.sin(t * 0.013F), 9.0F * Mth.sin(t * 0.007F), 0.0F, 0.62F * a);
        layer(g, w, h, 1.22F + 0.05F * Mth.sin(t * 0.009F + 1.3F), -7.0F * Mth.sin(t * 0.005F), 180.0F, 0.5F * a);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** (HeartColdPacket) How deep a heart's cold is in you now: 0 to 1. */
    public static void heartCold(float depth) {
        Minecraft mc = Minecraft.getInstance();
        heartDepth = depth;
        heartSaid = mc.level != null ? mc.level.getGameTime() : 0L;
    }

    private static void layer(GuiGraphics g, int w, int h, float scale, float dx, float turn, float alpha) {
        g.pose().pushPose();
        g.pose().translate(w / 2.0F + dx, h / 2.0F, 0.0F);
        g.pose().mulPose(Axis.ZP.rotationDegrees(turn));
        g.pose().scale(scale, scale, 1.0F);
        g.setColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(MIST, -w / 2, -h / 2, w, h, 0.0F, 0.0F, 512, 512, 512, 512);
        g.pose().popPose();
    }

    /** The air thickens: the far fog drawn in and paled towards rime. */
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        float s = (float) Mth.lerp(event.getPartialTick(), lastStrength, strength);
        if (s <= 0.02F) {
            return;
        }
        event.setFarPlaneDistance(Mth.lerp(s, event.getFarPlaneDistance(), 26.0F));
        event.setNearPlaneDistance(Mth.lerp(s, event.getNearPlaneDistance(), 0.0F));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float s = (float) Mth.lerp(event.getPartialTick(), lastStrength, strength) * 0.55F;
        if (s <= 0.01F) {
            return;
        }
        event.setRed(Mth.lerp(s, event.getRed(), 0.62F));
        event.setGreen(Mth.lerp(s, event.getGreen(), 0.70F));
        event.setBlue(Mth.lerp(s, event.getBlue(), 0.80F));
    }
}
