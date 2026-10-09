package com.jastkub.frozenfortress.client.gui;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * The king's health bar, drawn from the author's own artwork.
 *
 * <h2>Why this is a texture again</h2>
 *
 * <p>It was a texture once and that version was rightly thrown out: a sword cut
 * from a shared atlas in four separate strips, every one a set of UV numbers
 * that had to line up with a generated sheet, and when any of it drifted the
 * HUD looked broken. What replaced it drew the whole bar from coloured
 * rectangles - unbreakable, and honestly a bit stiff, which is what the author
 * said about it.
 *
 * <p>So it is a texture again, but nothing like the first one. There is ONE
 * image, it is a picture of the finished bar rather than parts to assemble, and
 * the only numbers here are where the health run starts and how tall the track
 * is. There is no layout left to drift.
 *
 * <h2>How the states work</h2>
 *
 * <p>The atlas holds four finished bars, cut by {@code tools/gen_bossbar_texture.py}
 * from the source picture: an icy one whole, an icy one spent, and the same
 * pair in red. Drawing is two blits - the SPENT bar full width, then the WHOLE
 * bar clipped to the health. Because the artwork tarnishes the frame and cracks
 * the rail on the spent version, that single clip makes the bar decay from the
 * right as he is worn down, which is exactly what the reference showed and
 * would have been a lot of code to fake.
 *
 * <p>A COLOUR FOR EACH PHASE: the same drawing, its ice
 * recoloured four ways by {@code tools/gen_bossbar_phases.py} - steel blue for the Bulwark King, storm cyan for the
 * Stormblade, frost white for the Twin Blades, void violet for the Hollow Magus - the gold and the iron as drawn. The
 * old atlas (ice and red) stays as the drawing's own cut, read by nothing now.
 *
 * <p>AND A TURN IS SEEN: when the phase goes up, the whole bar flashes white and cracks run out from the emblem along
 * the rail, both fading over a second and a half, the bar shaking a pixel for the first half second of it.
 *
 * <p>The name is drawn by the game, not by the picture. It was painted into the
 * artwork and had to come out, or the bar could never show a translated name.
 * It goes back in the same place the art had it - to the LEFT of the emblem,
 * because the middle is where the crown of ice is.
 *
 * <p>The smoothing is client-side and keyed on nothing - there is one king -
 * and reset when the bar leaves so a second fight starts fresh.
 */
public final class VelkharBossBar {

    private static final ResourceLocation TEX =
            FrozenFortress.id("textures/gui/velkhar_bossbar_phases.png");

    // --- the atlas, as tools/gen_bossbar_phases.py lays it out: whole and spent for each phase, rows of 32
    private static final int TEX_W = 256, TEX_H = 512;
    private static final int SPR_W = 196, SPR_H = 25;
    private static final int V_PHASE = 64, V_SPENT = 32;
    private static final int V_CRACKS = 288;                    // (256, the white flash, is no longer drawn)
    /** Each phase's own light: the crystals on the rail and the line under it. */
    private static final int[] ACCENT = {0xA9C4E4, 0x7FE6FF, 0xEAF7FF, 0xC9A0FF};
    private static final int[] SUB = {0x8FA9C4, 0x7FC8DF, 0xC4D6E0, 0xA88FCF};
    /** How long a turn of phase is seen, in ticks. */
    private static final int TURN_TICKS = 30;

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
     * <p>These are the thresholds VelkharEntity actually tests - 0.66 for the
     * Stormblade and 0.33 for the Hollow Magus - and if those ever move, this
     * moves with them or the bar starts lying. The third, the sword tearing
     * (the Twin Blades), is his own: VelkharEntity.tearFraction().
     */
    private static final float[] PHASE_MARKS = {VelkharEntity.PHASE2_AT, VelkharEntity.PHASE3_AT};
    private static final String[] ROMAN = {"I", "II", "III", "IV"};

    /** The emblem's own columns - the name is kept clear of them. */
    private static final int EMBLEM_X0 = 84;

    private static final int GHOST = 0xEAF6FF;

    private static final float GHOST_DECAY = 0.055F;

    private static float ghost = -1.0F;
    private static float shownProgress;
    private static int flashTicks;
    private static int turnTicks;
    private static int lastPhase;
    /** The king the bar is following, and the highest phase it has already broken for: a turn flashes ONCE. */
    private static int lastBossId = -1;
    private static int flashedUpTo;
    private static long lastFrameTime = -1L;

    private VelkharBossBar() {
    }

    /** The official phase a share of his health falls in (the turns: VelkharEntity's marks). */
    private static int phaseAt(float progress) {
        return progress > VelkharEntity.PHASE2_AT ? 1 : progress > VelkharEntity.TEAR_FRAC ? 2
                : progress > VelkharEntity.PHASE3_AT ? 3 : 4;
    }

    /** Forgets the smoothing state, so the next fight starts clean. */
    public static void reset() {
        ghost = -1.0F;
        flashTicks = 0;
        turnTicks = 0;
        lastPhase = 0;
        lastBossId = -1;
        flashedUpTo = 0;
        lastFrameTime = -1L;
    }

    public static int render(GuiGraphics g, int topY, Component name, float progress,
                             @Nullable VelkharEntity boss) {
        Minecraft mc = Minecraft.getInstance();
        // FOUR PHASES: the sword torn in two is a phase of its own now. And with HIM OUT OF SIGHT -
        // out of this client's range, up in the Eye of the Storm, the bar still sent - it was the
        // first phase every time: the phase his health on the bar says instead, by the same marks
        int phase = Mth.clamp(boss != null ? boss.officialPhase() : phaseAt(progress), 1, 4);
        boolean warded = boss != null && boss.wardsStanding() > 0;
        boolean storming = boss != null && boss.stormActive();

        // A PHASE GIVES WAY ONCE:
        // the turn is flashed for a king only on the first time each phase is reached, whatever the phase number does
        // in between, and a new king (or the bar's first sight of this one) flashes nothing
        int bossId = boss != null ? boss.getId() : -1;
        if (bossId != lastBossId) {
            lastBossId = bossId;
            flashedUpTo = phase;
        } else if (phase > flashedUpTo) {
            turnTicks = TURN_TICKS;                            // a phase gave way: the bar breaks with it
            flashedUpTo = phase;
        }
        lastPhase = phase;
        advance(mc, progress);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int x = screenW / 2 - SPR_W / 2;
        int y = topY + 6;

        // a struck bar shakes - two pixels, and only for six ticks; a breaking one a pixel, for ten
        if (flashTicks > 0) {
            x += (flashTicks % 2 == 0 ? 2 : -2);
        } else if (turnTicks > TURN_TICKS - 10) {
            x += (turnTicks % 2 == 0 ? 1 : -1);
        }

        int fillW = Math.round(Mth.clamp(shownProgress, 0.0F, 1.0F) * BAR_W);
        int ghostW = Math.round(Mth.clamp(ghost, 0.0F, 1.0F) * BAR_W);
        int vFull = V_PHASE * (phase - 1);
        int vSpent = vFull + V_SPENT;
        int accent = ACCENT[phase - 1];

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

        // (a phase turn used to burn the whole run white while it lasted - gone with the flash below)

        // ================================================================
        // THE THREE MARKS, at the health where the fight becomes a different
        // fight: 0.66, where the sword tears, and 0.33.
        //
        // THREE NOW. The tear was left
        // unmarked once, as "something he does" rather than a threshold - but
        // it IS a threshold, maybeTearSword tests his health for it, and the
        // phase it opens is one of the four the player is told of.
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
        // (and a fourth, 08.10.2026: ASCENT_AT, where the Magus takes the fight up into the Eye of the Storm)
        float[] marks = {PHASE_MARKS[0], VelkharEntity.TEAR_FRAC, PHASE_MARKS[1], VelkharEntity.ASCENT_AT};
        for (float mark : marks) {
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
                        withAlpha(accent, (0.11F + 0.09F * pulse) * base));
            }

            // the stone: seven rows, 1-3-5-7-5-3-1. It stands a pixel and a
            // half proud of the track top and bottom, which is the point -
            // something set ON the rail rather than drawn into it.
            int[] half = {0, 1, 2, 3, 2, 1, 0};
            for (int r = 0; r < half.length; r++) {
                int ry = cy - 3 + r;
                g.fill(mx - half[r], ry, mx + half[r] + 1, ry + 1,
                        withAlpha(mix(accent, 0xFFFFFF, 0.45F), (0.80F + 0.18F * pulse) * base));
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

        // --- THE TURN: cracks out from the emblem, going out. NO FLASH: the white silhouette over the whole bar (the atlas's V_FLASH row) is not drawn at all
        if (turnTicks > 0) {
            float f = turnTicks / (float) TURN_TICKS;
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.8F * f);
            g.blit(TEX, x, y, 0, 0.0F, V_CRACKS, SPR_W, SPR_H, TEX_W, TEX_H);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        RenderSystem.disableBlend();

        // --- NO NAME. "VELKHAR, THE HOLLOW SOVEREIGN" sat across the top left
        //     of the bar and it was saying nothing the player did not already
        //     know ten seconds into the fight - a fixed string that never
        //     changes is furniture, and this one was competing with the crown
        //     of ice for the same corner. The phase line under the middle is
        //     the part that carries information, and it reads better alone.

        // --- the phase, or the ward notice, under the middle: "Phase II - The Stormblade"
        String subText = warded
                ? Component.translatable("velkhar.bar.warded").getString()
                : Component.translatable("velkhar.bar.phase", ROMAN[phase - 1],
                        Component.translatable("entity.frozen_dominion.velkhar.phase" + phase)).getString();
        int subCol = warded ? 0xBFEFFF : SUB[phase - 1];
        int subW = mc.font.width(subText);
        g.drawString(mc.font, subText, screenW / 2 - subW / 2, y + SPR_H - 2, subCol, true);

        // (the advice for each phase is in the books on the lecterns at his statues)

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
                if (turnTicks > 0) {
                    turnTicks--;
                }
            }
        }
    }

    private static float time(Minecraft mc) {
        return mc.level != null ? mc.level.getGameTime() : 0.0F;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1.0F - t) + ((b >> 16) & 255) * t);
        int gr = Math.round(((a >> 8) & 255) * (1.0F - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1.0F - t) + (b & 255) * t);
        return (r << 16) | (gr << 8) | bl;
    }

    private static int withAlpha(int rgb, float a) {
        int alpha = Mth.clamp((int) (a * 255.0F), 0, 255);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
