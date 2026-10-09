package com.jastkub.frozenfortress.client.gui;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * The Ice Monstrosity's bar: the king's construction and the king's artwork
 * (see VelkharBossBar for how the atlas and the two-blit decay work), turned
 * to the colossus - cold iron for gold, glacier for crown-ice, a horned skull
 * of ice in the medallion, and one turn of the fight, at half its
 * health, where the ice goes violet. Its name goes under the middle; the turn
 * replaces it with the fury notice.
 */
public final class MonstrosityBossBar {

    private static final ResourceLocation TEX =
            FrozenFortress.id("textures/gui/monstrosity_bossbar.png");

    // --- the atlas, as tools/gen_bossbar_texture.py lays it out
    private static final int TEX_W = 256, TEX_H = 128;
    private static final int SPR_W = 196, SPR_H = 25;
    private static final int V_ICE_FULL = 0, V_ICE_SPENT = 32;
    private static final int V_RED_FULL = 64, V_RED_SPENT = 96;

    /**
     * WHERE THE HEALTH ACTUALLY SHOWS, which is not the same as where the rail
     * is drawn.
     *
     * <p>This was 7 and 182 - the full width of the rail - and that is the
     * "he lost fifty health and the bar did not move" bug. The artwork caps
     * each end of that rail with a gold ferrule and a crystal cluster, roughly
     * eleven pixels on the left and twelve on the right, and those are painted
     * OVER the track. Mapping health across the whole rail therefore spent the
     * first six per cent of the fight hidden under the right-hand crystal:
     * measured, dropping to 92.8% moved the cut to 176 while the ferrule does
     * not end until 178, so nothing changed on screen at all.
     *
     * <p>Measured off the sprite rather than guessed. The lit track runs 18 to
     * 177, broken by the emblem between 89 and 106 - and the emblem does not
     * matter here, because the cut passes behind it either way.
     */
    private static final int RAIL_X = 18;
    private static final int BAR_W = 160;
    private static final int TRACK_Y = 14, TRACK_H = 4;
    /**
     * Where the phase turns are, as a fraction of his health.
     *
     * <p>The colossus turns once, at FURY. (In the king's bar: VelkharEntity's - 0.66 for the
     * Stormblade and 0.33 for the Hollow Magus - and if those ever move, this
     * moves with them or the bar starts lying.
     */
    private static final float[] PHASE_MARKS = {0.5F};

    /** The emblem's own columns - the name is kept clear of them. */
    private static final int EMBLEM_X0 = 84;

    private static final int GHOST = 0xEAF6FF;

    /** Where the colossus turns: HollowGolemEntity roars at half its health. */
    private static final float FURY = 0.5F;

    private static final float GHOST_DECAY = 0.055F;

    private static float ghost = -1.0F;
    private static float shownProgress;
    private static int flashTicks;
    private static long lastFrameTime = -1L;

    private MonstrosityBossBar() {
    }

    /** Forgets the smoothing state, so the next fight starts clean. */
    public static void reset() {
        ghost = -1.0F;
        flashTicks = 0;
        lastFrameTime = -1L;
    }

    public static int render(GuiGraphics g, int topY, Component name, float progress,
                             @Nullable HollowGolemEntity golem) {
        Minecraft mc = Minecraft.getInstance();
        // the colossus has one turn in it: below FURY of its health the ice
        // goes violet and the bar goes with it
        int phase = progress <= FURY ? 3 : 1;
        boolean warded = false;
        boolean storming = false;
        boolean transitioning = golem != null && golem.isEmerging();

        advance(mc, progress);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int x = screenW / 2 - SPR_W / 2;
        int y = topY + 6;

        // a struck bar shakes - two pixels, and only for six ticks
        if (flashTicks > 0) {
            x += (flashTicks % 2 == 0 ? 2 : -2);
        }

        int fillW = Math.round(Mth.clamp(shownProgress, 0.0F, 1.0F) * BAR_W);
        int ghostW = Math.round(Mth.clamp(ghost, 0.0F, 1.0F) * BAR_W);
        int vFull = phase >= 3 ? V_RED_FULL : V_ICE_FULL;
        int vSpent = phase >= 3 ? V_RED_SPENT : V_ICE_SPENT;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // --- the spent bar underneath: tarnished frame, cracked, dark track
        // (the medallion whole or spent entire, never cut through: BarEmblem)

        // --- and the whole bar over it, cut off at the health.
        //
        // At full it draws entire, caps and all; below that the cut runs
        // through the rail and the bright frame simply stops, letting the
        // tarnished one underneath show. One blit, and the bar ages.
        int cut = fillW >= BAR_W ? SPR_W : RAIL_X + fillW;
        BarEmblem.blitBar(g, TEX, x, y, vFull, vSpent, cut, SPR_W, SPR_H, TEX_W, TEX_H);

        // --- the ghost: what he just lost, held white over the track for half
        //     a second so a hit is seen and not merely subtracted
        if (ghostW > fillW) {
            float a = 0.45F + 0.35F * Mth.abs(Mth.sin(time(mc) * 0.55F));
            BarEmblem.fill(g, x, x + RAIL_X + fillW, y + TRACK_Y,
                    x + RAIL_X + ghostW, y + TRACK_Y + TRACK_H, withAlpha(GHOST, a));
        }

        // --- a phase turn burns the whole run white for its duration
        if (transitioning && fillW > 0) {
            float p = 0.5F + 0.5F * Mth.sin(time(mc) * 0.5F);
            BarEmblem.fill(g, x, x + RAIL_X, y + TRACK_Y, x + RAIL_X + fillW,
                    y + TRACK_Y + TRACK_H, withAlpha(0xFFFFFF, 0.14F + 0.26F * p));
        }

        // ================================================================
        // THE TWO MARKS, at the health where the fight becomes a different
        // fight: 0.66 and 0.33.
        //
        // ONLY TWO. The half-phase is deliberately unmarked - it is not a
        // health threshold at all, it is something he does when the sword
        // comes apart, so a notch there would promise a line the bar cannot
        // keep. Marking what does not exist is worse than marking nothing.
        //
        // THEY ARE CRYSTALS NOW, not hairlines. One pixel at a third alpha is
        // findable on the second or third attempt, which was the old brief -
        // but these two marks are where the fight becomes a different fight,
        // and that deserves to be seen the first time. A shard set on the rail
        // says "something happens here" in a way a notch cannot.
        //
        // Built from stacked rows rather than a sprite, so it stays sharp at
        // any GUI scale and costs no atlas: 1-3-5-3-1 pixels wide, which is
        // the smallest run that reads as a cut stone rather than a blob. A
        // white core down the middle gives it the facet, a soft halo lifts it
        // off the dark track, and a slow pulse keeps it from looking painted
        // on. The pulses are offset by the mark's own position so the two do
        // not breathe in lockstep, which would read as a UI animation instead
        // of as two separate stones.
        // ================================================================
        for (float mark : PHASE_MARKS) {
            int mx = BarEmblem.markX(x, RAIL_X, BAR_W, mark);
            if (mx == Integer.MIN_VALUE) {
                continue;                                      // half: the medallion is the mark
            }
            boolean lit = BAR_W * mark <= fillW;
            int cy = y + TRACK_Y + TRACK_H / 2;
            float pulse = 0.5F + 0.5F * Mth.sin(time(mc) * 0.18F + mark * 9.0F);
            float base = lit ? 0.78F : 0.92F;

            // the halo, under everything, so the stone sits ON the rail -
            // and DIAMOND-SHAPED, not a box. A rectangular glow behind a
            // diamond draws its own corners and the player sees a square
            // panel with a gem in it; following the stone's own outline two
            // pixels out reads as light coming off the stone.
            int[] halo = {1, 2, 3, 4, 5, 4, 3, 2, 1};
            for (int r = 0; r < halo.length; r++) {
                int ry = cy - 4 + r;
                g.fill(mx - halo[r], ry, mx + halo[r] + 1, ry + 1,
                        withAlpha(0x7FC8FF, (0.11F + 0.09F * pulse) * base));
            }

            // the stone: seven rows, 1-3-5-7-5-3-1. It stands a pixel and a
            // half proud of the track top and bottom, which is the point -
            // something set ON the rail rather than drawn into it.
            int[] half = {0, 1, 2, 3, 2, 1, 0};
            for (int r = 0; r < half.length; r++) {
                int ry = cy - 3 + r;
                g.fill(mx - half[r], ry, mx + half[r] + 1, ry + 1,
                        withAlpha(0xCFE9FF, (0.80F + 0.18F * pulse) * base));
            }
            // the facet: a brighter core down the middle, and two short
            // highlights off the widest row, which is what turns a diamond
            // into something cut
            g.fill(mx, cy - 3, mx + 1, cy + 4,
                    withAlpha(0xFFFFFF, (0.62F + 0.30F * pulse) * base));
            g.fill(mx - 2, cy, mx + 3, cy + 1,
                    withAlpha(0xFFFFFF, (0.34F + 0.24F * pulse) * base));
            // and the seam it is set into, so the rail still reads as divided
            g.fill(mx, y + TRACK_Y - 2, mx + 1, y + TRACK_Y + TRACK_H + 2,
                    withAlpha(0xDCEBFF, 0.30F * base));
        }

        // --- untouchable: an icy wash while the wards hold, because a bar that
        //     simply stops moving reads as frozen-the-bug
        if (warded) {
            float p = 0.5F + 0.5F * Mth.abs(Mth.sin(time(mc) * 0.4F));
            BarEmblem.fill(g, x, x + RAIL_X, y + TRACK_Y - 1, x + RAIL_X + BAR_W,
                    y + TRACK_Y + TRACK_H + 1, withAlpha(0xBFEFFF, 0.18F + 0.14F * p));
        }

        RenderSystem.disableBlend();

        // --- NO NAME. "VELKHAR, THE HOLLOW SOVEREIGN" sat across the top left
        //     of the bar and it was saying nothing the player did not already
        //     know ten seconds into the fight - a fixed string that never
        //     changes is furniture, and this one was competing with the crown
        //     of ice for the same corner. The phase line under the middle is
        //     the part that carries information, and it reads better alone.

        // --- the phase, or the ward notice, under the middle
        String subText = phase >= 3
                ? Component.translatable("entity.frozen_dominion.ice_monstrosity.fury").getString()
                : name.getString();
        int subCol = phase >= 3 ? 0xC9A6FF : 0x8FB4CF;
        int subW = mc.font.width(subText);
        g.drawString(mc.font, subText, screenW / 2 - subW / 2, y + SPR_H - 2, subCol, true);

        // --- a lone storm marker, off the right cap, clear of everything
        if (storming) {
            int sx = x + SPR_W + 3;
            g.fill(sx, y + TRACK_Y - 3, sx + 3, y + TRACK_Y + TRACK_H + 3,
                    withAlpha(0xBFE8FF, 0.85F));
        }
        return SPR_H + 8;
    }

    // The ghost chase and the hit flash, stepped once per game tick so it runs
    // at the same wall-speed on any framerate and freezes when paused.
    private static void advance(Minecraft mc, float progress) {
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        if (ghost < 0.0F) {
            ghost = progress;
            shownProgress = progress;
            lastFrameTime = now;
            return;
        }
        if (progress < shownProgress - 0.0005F) {
            flashTicks = 6;
        }
        shownProgress = progress;
        if (now != lastFrameTime) {
            long steps = Math.max(0L, Math.min(40L, now - lastFrameTime));
            lastFrameTime = now;
            for (long i = 0; i < steps; i++) {
                ghost = ghost > shownProgress
                        ? Math.max(shownProgress, ghost - GHOST_DECAY) : shownProgress;
                if (flashTicks > 0) {
                    flashTicks--;
                }
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
}
