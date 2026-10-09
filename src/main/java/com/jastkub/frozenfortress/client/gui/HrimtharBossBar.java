package com.jastkub.frozenfortress.client.gui;

import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * The colossus's bar, drawn from rectangles like the king's - and deliberately
 * the poorer of the two.
 *
 * <p>Same construction, half the money on it: no gold, a duller grey-blue
 * fill, narrower, and a plainer frame. A miniboss bar that looks as expensive
 * as the boss bar tells the player the wrong thing about what they are
 * fighting, and Hrimthar exists to be the thing you deal with WHILE the real
 * fight is happening. It sits below the king's because Forge hands each boss
 * event the Y the previous one finished at.
 */
public final class HrimtharBossBar {

    private static final int W = 150;
    private static final int H = 8;

    private static final int FRAME_DARK = 0xFF0A0E14;
    private static final int BEVEL = 0xFF2E3A46;
    private static final int WELL = 0xFF161C24;
    private static final int FILL_LO = 0xFF4A6472;
    private static final int FILL_HI = 0xFF87A6B6;
    private static final int GHOST = 0xC0E6F2FA;

    private static final float GHOST_DECAY = 0.05F;

    private static float ghost = -1.0F;
    private static float shownProgress;
    private static long lastFrameTime = -1L;

    private HrimtharBossBar() {
    }

    public static void reset() {
        ghost = -1.0F;
        lastFrameTime = -1L;
    }

    public static int render(GuiGraphics g, int topY, Component name, float progress,
                             @Nullable HollowGolemEntity golem) {
        Minecraft mc = Minecraft.getInstance();
        advance(mc, progress);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int x = screenW / 2 - W / 2;
        int y = topY + 10;
        boolean climbing = golem != null && golem.isEmerging();

        // --- frame and well
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, FRAME_DARK);
        g.fill(x, y, x + W, y + H, BEVEL);
        g.fill(x, y, x + W, y + H - 1, WELL);

        int fillW = Math.round(Mth.clamp(shownProgress, 0.0F, 1.0F) * W);
        int ghostW = Math.round(Mth.clamp(ghost, 0.0F, 1.0F) * W);

        if (ghostW > fillW) {
            g.fill(x + fillW, y, x + ghostW, y + H, withAlpha(GHOST, 0.75F));
        }
        if (fillW > 0) {
            int lo = FILL_LO;
            int hi = FILL_HI;
            if (climbing) {                       // untouchable while it rises
                float p = 0.45F + 0.4F * Mth.abs(Mth.sin(time(mc) * 0.5F));
                lo = lerp(lo, 0xFFBFE8FF, 0.5F * p);
                hi = lerp(hi, 0xFFDFF4FF, 0.5F * p);
            }
            g.fill(x, y, x + fillW, y + H, lo);
            g.fill(x, y, x + fillW, y + 2, hi);
            g.fill(x, y + H - 1, x + fillW, y + H, FRAME_DARK);
        }

        String label = climbing
                ? Component.translatable("velkhar.bar.rising").getString()
                : name.getString();
        int width = mc.font.width(label);
        g.drawString(mc.font, label, screenW / 2 - width / 2, y - 9,
                climbing ? 0xBFEFFF : 0x9FC1D2, true);
        return 22;
    }

    private static void advance(Minecraft mc, float progress) {
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        if (ghost < 0.0F) {
            ghost = progress;
            shownProgress = progress;
            lastFrameTime = now;
            return;
        }
        shownProgress = progress;
        if (now != lastFrameTime) {
            long steps = Math.max(0L, Math.min(40L, now - lastFrameTime));
            lastFrameTime = now;
            for (long i = 0; i < steps; i++) {
                ghost = ghost > shownProgress
                        ? Math.max(shownProgress, ghost - GHOST_DECAY) : shownProgress;
            }
        }
    }

    private static float time(Minecraft mc) {
        return mc.level != null ? mc.level.getGameTime() : 0.0F;
    }

    private static int withAlpha(int rgb, float a) {
        int alpha = Mth.clamp((int) (a * 255.0F), 0, 255);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerp(int a, int b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
