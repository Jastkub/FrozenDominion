package com.jastkub.frozenfortress.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * THE MEDALLION IN THE MIDDLE OF EVERY BAR OF THE KING'S FAMILY (VelkharBossBar, CourtBossBar, MonstrosityBossBar - all
 * cut from the author's one drawing) is left alone.
 *
 * <ul>
 *   <li>It is never cut through: the whole bar is drawn over the spent one up to the health, but the medallion is
 *       whole or spent entire - whole while the health is past its middle. Cut through, it was half of each.</li>
 *   <li>Nothing laid over the track crosses it: the white of a lost blow, the washes of a state, a phase's burn.</li>
 *   <li>No threshold's stone sits on it: half is the medallion itself; one beside it stands at its edge.</li>
 * </ul>
 *
 * Columns in the 196-wide sprite (measured off velkhar_bossbar.png: the ring, dark edge to dark edge, is 89 to 106).
 */
final class BarEmblem {

    static final int X0 = 89, X1 = 107, MID = 98;

    private BarEmblem() {
    }

    /** The bar: spent underneath, whole over it up to `cut` (sprite columns) - the medallion whole or spent entire. */
    static void blitBar(GuiGraphics g, ResourceLocation tex, int x, int y, int vFull, int vSpent, int cut,
                        int sprW, int sprH, int texW, int texH) {
        g.blit(tex, x, y, 0, 0.0F, vSpent, sprW, sprH, texW, texH);
        if (cut <= 0) {
            return;
        }
        int left = Math.min(cut, X0);
        if (left > 0) {
            g.blit(tex, x, y, 0, 0.0F, vFull, left, sprH, texW, texH);
        }
        if (cut > MID) {
            g.blit(tex, x + X0, y, 0, X0, vFull, X1 - X0, sprH, texW, texH);
        }
        if (cut > X1) {
            g.blit(tex, x + X1, y, 0, X1, vFull, Math.min(cut, sprW) - X1, sprH, texW, texH);
        }
    }

    /** A fill over the track (screen coordinates, the bar drawn at `barX`) that goes round the medallion. */
    static void fill(GuiGraphics g, int barX, int x0, int y0, int x1, int y1, int col) {
        int e0 = barX + X0;
        int e1 = barX + X1;
        if (x1 <= e0 || x0 >= e1) {
            g.fill(x0, y0, x1, y1, col);
            return;
        }
        if (x0 < e0) {
            g.fill(x0, y0, e0, y1, col);
        }
        if (x1 > e1) {
            g.fill(e1, y0, x1, y1, col);
        }
    }

    /** Where a threshold's stone goes (screen x), or Integer.MIN_VALUE for none: half is the medallion itself. */
    static int markX(int barX, int railX, int barW, float mark) {
        int rel = railX + Math.round(barW * mark);
        if (rel >= X0 - 3 && rel < X1 + 3) {
            if (Math.abs(rel - MID) <= 3) {
                return Integer.MIN_VALUE;
            }
            rel = rel < MID ? X0 - 4 : X1 + 3;          // beside it, at its edge
        }
        return barX + rel;
    }
}
