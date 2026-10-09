package com.jastkub.frozenfortress.client.gui;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.RimePriestessEntity;
import com.jastkub.frozenfortress.entity.TurnkeyEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * The bars of the court's two minibosses, of the king's
 * family: the same atlas layout and the same two-blit decay as VelkharBossBar
 * and MonstrosityBossBar, art from tools/gen_miniboss_bars.py.
 *
 * <ul>
 *   <li>THE TURNKEY - lantern-teal on brass, his keyhole in the medallion. Three
 *   small stones where he throws his ring of keys (80, 55, 30 per cent), and a
 *   padlock where THE LAST RING begins: below it the track goes molten gold
 *   and a chain is laid over what is left of it, because nothing but his keys
 *   will move it now - and the line under the bar says so.</li>
 *   <li>THE PRIESTESS - lilac on silver, her book in the medallion. Three stones
 *   where she begins the Litany (75, 50, 25 per cent). While she reads the bar
 *   is washed white (she cannot be hurt, and a bar that simply stops reads as
 *   a bug); while she is spent after the Amen it pulses gold (she is open).
 *   Below half, where her mirrors walk, the frost turns rose.</li>
 * </ul>
 */
public final class CourtBossBar {

    private static final int TEX_W = 256, TEX_H = 128;
    private static final int SPR_W = 196, SPR_H = 25;
    private static final int V_FULL = 0, V_SPENT = 32, V_SECOND_FULL = 64, V_SECOND_SPENT = 96;
    /** Where the health shows (see MonstrosityBossBar: the caps are painted over the rail's ends). */
    private static final int RAIL_X = 18, BAR_W = 160;
    private static final int TRACK_Y = 14, TRACK_H = 4;
    private static final float GHOST_DECAY = 0.055F;

    /** One bar's art and its fixed marks. */
    private enum Kind {
        TURNKEY("turnkey_bossbar", new float[]{0.80F, 0.55F, 0.30F}, 0x8FF0FF, 0x9FC6C8),
        PRIESTESS("priestess_bossbar", new float[]{0.75F, 0.50F, 0.25F}, 0xD8B8FF, 0xBCA8D8),
        LAMPLIGHTER("lamplighter_bossbar", new float[]{0.50F, 0.25F}, 0xFFD27A, 0xE6D6A8),
        DROWNED("drowned_lady_bossbar", new float[]{0.50F}, 0x7FE8F0, 0xA8DCE2),
        AUROCHS("ice_aurochs_bossbar", new float[]{0.50F}, 0xBFE8FF, 0xC6D6E2),
        SHEPHERD("shade_shepherd_bossbar", new float[]{0.50F}, 0xC8C0A8, 0xB8B4A8),
        OVERSEER("forge_overseer_bossbar", new float[]{0.50F}, 0x9FE6FF, 0xB8C2CC),
        BONE_LORD("bone_lord_bossbar", new float[]{0.50F}, 0xE8E0C8, 0xD8D0BC);

        final ResourceLocation tex;
        final float[] marks;
        final int stone;
        final int nameCol;
        float ghost = -1.0F;
        float shown;
        int flash;
        long last = -1L;

        Kind(String tex, float[] marks, int stone, int nameCol) {
            this.tex = FrozenFortress.id("textures/gui/" + tex + ".png");
            this.marks = marks;
            this.stone = stone;
            this.nameCol = nameCol;
        }
    }

    private CourtBossBar() {
    }

    /** Forgets the smoothing state, so the next fight starts clean. */
    public static void reset() {
        for (Kind k : Kind.values()) {
            k.ghost = -1.0F;
            k.flash = 0;
            k.last = -1L;
        }
    }

    /**
     * THE BONE LORD - old bone and frozen fur, his skull in its ushanka in the medallion; one stone at half, where its
     * eyes go red. The line says when it is down (strike now) and when it bursts after you.
     */
    public static int renderBoneLord(GuiGraphics g, int topY, Component name, float progress) {
        Minecraft mc = Minecraft.getInstance();
        com.jastkub.frozenfortress.entity.BoneLordEntity b =
                nearest(mc, com.jastkub.frozenfortress.entity.BoneLordEntity.class);
        int st = b != null ? b.getAttackState() : 0;
        Component sub = name;
        int col = Kind.BONE_LORD.nameCol;
        if (st == com.jastkub.frozenfortress.entity.BoneLordEntity.TRIP
                || st == com.jastkub.frozenfortress.entity.BoneLordEntity.DOWN) {
            sub = Component.translatable("entity.frozen_dominion.bone_lord.down");
            col = 0x9FF0FF;
        } else if (b != null && b.isBursting()) {
            sub = Component.translatable("entity.frozen_dominion.bone_lord.burst");
            col = 0xFF8A70;
        }
        return draw(mc, g, topY, Kind.BONE_LORD, progress, progress <= 0.5F, -1.0F, sub, col, Wash.NONE);
    }

    public static int renderTurnkey(GuiGraphics g, int topY, Component name, float progress) {
        Minecraft mc = Minecraft.getInstance();
        TurnkeyEntity t = nearest(mc, TurnkeyEntity.class);
        float ring = TurnkeyEntity.LAST_RING / (t != null ? t.getMaxHealth() : 260.0F);
        boolean lastRing = progress <= ring + 0.0005F;
        Component sub = lastRing
                ? Component.translatable("entity.frozen_dominion.turnkey.last_ring")
                : name;
        return draw(mc, g, topY, Kind.TURNKEY, progress, lastRing, ring, sub,
                lastRing ? 0xFFD27A : Kind.TURNKEY.nameCol, lastRing ? Wash.CHAINED : Wash.NONE);
    }

    public static int renderPriestess(GuiGraphics g, int topY, Component name, float progress) {
        Minecraft mc = Minecraft.getInstance();
        RimePriestessEntity p = nearest(mc, RimePriestessEntity.class);
        int st = p != null ? p.getAttackState() : 0;
        Component sub = name;
        int col = Kind.PRIESTESS.nameCol;
        Wash wash = Wash.NONE;
        if (st == RimePriestessEntity.LITANY) {
            sub = Component.translatable("entity.frozen_dominion.rime_priestess.litany");
            col = 0xF2ECFF;
            wash = Wash.WARDED;
        } else if (st == RimePriestessEntity.AMEN) {
            sub = Component.translatable("entity.frozen_dominion.rime_priestess.amen");
            col = 0xFFFFFF;
            wash = Wash.WARDED;
        } else if (st == RimePriestessEntity.SPENT) {
            sub = Component.translatable("entity.frozen_dominion.rime_priestess.spent");
            col = 0xFFD98A;
            wash = Wash.OPEN;
        }
        return draw(mc, g, topY, Kind.PRIESTESS, progress, progress <= 0.5F, -1.0F, sub, col, wash);
    }

    /**
     * THE LAMPLIGHTER - iron and pale gold, his lantern in the medallion; amber below half. Two stones:
     * THE SIGNAL (half) and THE LAST WATCH (a quarter). While the lantern's light is on him the bar is
     * washed white (a quarter of a blow goes through); in a pier's shadow it pulses gold - he is open.
     */
    public static int renderLamplighter(GuiGraphics g, int topY, Component name, float progress) {
        Minecraft mc = Minecraft.getInstance();
        com.jastkub.frozenfortress.entity.LamplighterEntity l = nearest(mc, com.jastkub.frozenfortress.entity.LamplighterEntity.class);
        int st = l != null ? l.getAttackState() : 0;
        Component sub = name;
        int col = Kind.LAMPLIGHTER.nameCol;
        Wash wash = Wash.NONE;
        if (st == com.jastkub.frozenfortress.entity.LamplighterEntity.SIGNAL) {
            sub = Component.translatable("entity.frozen_dominion.lamplighter.signal");
            col = 0xFFE6A0;
        } else if (st == com.jastkub.frozenfortress.entity.LamplighterEntity.WATCH) {
            sub = Component.translatable("entity.frozen_dominion.lamplighter.watch");
            col = 0xFFB070;
        } else if (l != null && l.handLit()) {
            sub = Component.translatable("entity.frozen_dominion.lamplighter.hand");
            col = 0xFFF0C0;
            wash = Wash.WARDED;
        } else if (l != null && l.isShaded()) {
            sub = Component.translatable("entity.frozen_dominion.lamplighter.shaded");
            col = 0xFFD98A;
            wash = Wash.OPEN;
        } else if (l != null) {
            wash = Wash.WARDED;
        }
        return draw(mc, g, topY, Kind.LAMPLIGHTER, progress, progress <= 0.5F, -1.0F, sub, col, wash);
    }

    /**
     * THE DROWNED LADY - verdigris on wet black bronze, her crown of icicles over black water in the medallion; one
     * stone, at half, where she wails (below it the track goes pale). While she is under the ice the bar is washed
     * white (nothing reaches her); torn up out of it by her broken hands it pulses gold - she is open; while her
     * black tide comes the line says where to stand.
     */
    public static int renderDrowned(GuiGraphics g, int topY, Component name, float progress) {
        Minecraft mc = Minecraft.getInstance();
        com.jastkub.frozenfortress.entity.DrownedLadyEntity d =
                nearest(mc, com.jastkub.frozenfortress.entity.DrownedLadyEntity.class);
        int st = d != null ? d.getAttackState() : 0;
        Component sub = name;
        int col = Kind.DROWNED.nameCol;
        Wash wash = Wash.NONE;
        if (d != null && (d.hidden() || st == com.jastkub.frozenfortress.entity.DrownedLadyEntity.SINK)) {
            sub = Component.translatable("entity.frozen_dominion.drowned_lady.under_ice");
            col = 0xCFF4FF;
            wash = Wash.WARDED;
        } else if (st == com.jastkub.frozenfortress.entity.DrownedLadyEntity.STUNNED) {
            sub = Component.translatable("entity.frozen_dominion.drowned_lady.torn");
            col = 0xFFD98A;
            wash = Wash.OPEN;
        } else if (st == com.jastkub.frozenfortress.entity.DrownedLadyEntity.TIDE) {
            sub = Component.translatable("entity.frozen_dominion.drowned_lady.tide");
            col = 0x9FF0FF;
        }
        return draw(mc, g, topY, Kind.DROWNED, progress, progress <= 0.5F, -1.0F, sub, col, wash);
    }

    /**
     * THE ICE AUROCHS - dark iron, glacier ice, his horned head in the medallion; one stone at half, where he bellows
     * and the rage grows on his back (the track goes ember red). While he scrapes and charges, the line says what
     * stops him; stunned against a pillar, the bar pulses gold - he is open.
     */
    public static int renderAurochs(GuiGraphics g, int topY, Component name, float progress) {
        Minecraft mc = Minecraft.getInstance();
        com.jastkub.frozenfortress.entity.IceAurochsEntity a =
                nearest(mc, com.jastkub.frozenfortress.entity.IceAurochsEntity.class);
        int st = a != null ? a.getAttackState() : 0;
        Component sub = name;
        int col = Kind.AUROCHS.nameCol;
        Wash wash = Wash.NONE;
        if (a != null && a.isStunned()) {
            sub = Component.translatable("entity.frozen_dominion.ice_aurochs.stunned");
            col = 0xFFD98A;
            wash = Wash.OPEN;
        } else if (st == com.jastkub.frozenfortress.entity.IceAurochsEntity.PAW
                || st == com.jastkub.frozenfortress.entity.IceAurochsEntity.PAW_QUICK
                || st == com.jastkub.frozenfortress.entity.IceAurochsEntity.CHARGE) {
            sub = Component.translatable("entity.frozen_dominion.ice_aurochs.charge");
            col = 0xFFB0A0;
        }
        return draw(mc, g, topY, Kind.AUROCHS, progress, progress <= 0.5F, -1.0F, sub, col, wash);
    }

    /**
     * THE SHADE SHEPHERD - wool-dark iron and pale bone, his ram's skull in the medallion; one stone at half, where he
     * blows out every fire at once. While his herd lives the bar is washed white and the line says so (each shade
     * takes some of every blow); when he breathes at a fire, the line says to stand in its way.
     */
    public static int renderShepherd(GuiGraphics g, int topY, Component name, float progress) {
        Minecraft mc = Minecraft.getInstance();
        com.jastkub.frozenfortress.entity.ShadeShepherdEntity s =
                nearest(mc, com.jastkub.frozenfortress.entity.ShadeShepherdEntity.class);
        int st = s != null ? s.getAttackState() : 0;
        Component sub = name;
        int col = Kind.SHEPHERD.nameCol;
        Wash wash = Wash.NONE;
        if (st == com.jastkub.frozenfortress.entity.ShadeShepherdEntity.SNUFF
                || st == com.jastkub.frozenfortress.entity.ShadeShepherdEntity.GREAT) {
            sub = Component.translatable("entity.frozen_dominion.shade_shepherd.breath");
            col = 0x9FF0FF;
        } else if (s != null && s.herd() > 0) {
            sub = Component.translatable("entity.frozen_dominion.shade_shepherd.herd", s.herd());
            col = 0xE2E2E8;
            wash = Wash.WARDED;
        }
        return draw(mc, g, topY, Kind.SHEPHERD, progress, progress <= 0.5F, -1.0F, sub, col, wash);
    }

    /**
     * THE FORGE OVERSEER - blackened iron and furnace-blue, his grated helm in the medallion; one stone at half,
     * where he roars (the track goes molten). The line counts his plates; with the grate in his chest bared the bar
     * pulses gold (he takes more); with his hammer quenched it says its blows freeze.
     */
    public static int renderOverseer(GuiGraphics g, int topY, Component name, float progress) {
        Minecraft mc = Minecraft.getInstance();
        com.jastkub.frozenfortress.entity.ForgeOverseerEntity o =
                nearest(mc, com.jastkub.frozenfortress.entity.ForgeOverseerEntity.class);
        Component sub = name;
        int col = Kind.OVERSEER.nameCol;
        Wash wash = Wash.NONE;
        if (o != null && o.isQuenched()) {
            sub = Component.translatable("entity.frozen_dominion.forge_overseer.quenched");
            col = 0x9FF0FF;
        } else if (o != null && o.coreBare()) {
            sub = Component.translatable("entity.frozen_dominion.forge_overseer.core_bare");
            col = 0xFFD98A;
            wash = Wash.OPEN;
        } else if (o != null) {
            sub = Component.translatable("entity.frozen_dominion.forge_overseer.bar_plates", o.platesLeft());
        }
        if (o != null && o.coreBare()) {
            wash = Wash.OPEN;
        }
        return draw(mc, g, topY, Kind.OVERSEER, progress, progress <= 0.5F, -1.0F, sub, col, wash);
    }

    private enum Wash { NONE, WARDED, OPEN, CHAINED }

    private static int draw(Minecraft mc, GuiGraphics g, int topY, Kind k, float progress, boolean second,
                            float lock, Component sub, int subCol, Wash wash) {
        advance(mc, k, progress);
        int screenW = mc.getWindow().getGuiScaledWidth();
        int x = screenW / 2 - SPR_W / 2;
        int y = topY + 6;
        if (k.flash > 0) {
            x += (k.flash % 2 == 0 ? 2 : -2);                  // a struck bar shakes
        }
        float t = time(mc);
        int fillW = Math.round(Mth.clamp(k.shown, 0.0F, 1.0F) * BAR_W);
        int ghostW = Math.round(Mth.clamp(k.ghost, 0.0F, 1.0F) * BAR_W);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int cut = fillW >= BAR_W ? SPR_W : RAIL_X + fillW;
        BarEmblem.blitBar(g, k.tex, x, y, second ? V_SECOND_FULL : V_FULL, second ? V_SECOND_SPENT : V_SPENT, cut,
                SPR_W, SPR_H, TEX_W, TEX_H);                  // (the medallion never cut through)
        if (ghostW > fillW) {                                  // what was just lost, held white a moment
            float a = 0.45F + 0.35F * Mth.abs(Mth.sin(t * 0.55F));
            BarEmblem.fill(g, x, x + RAIL_X + fillW, y + TRACK_Y, x + RAIL_X + ghostW, y + TRACK_Y + TRACK_H,
                    withAlpha(0xFFFFFF, a));
        }

        int ty = y + TRACK_Y;
        switch (wash) {
            case WARDED -> {                                   // she cannot be touched while she reads
                float p = 0.5F + 0.5F * Mth.abs(Mth.sin(t * 0.4F));
                BarEmblem.fill(g, x, x + RAIL_X, ty - 1, x + RAIL_X + BAR_W, ty + TRACK_H + 1,
                        withAlpha(0xF4EEFF, 0.22F + 0.16F * p));
            }
            case OPEN -> {                                     // spent: struck now, she takes more
                float p = 0.5F + 0.5F * Mth.sin(t * 0.9F);
                BarEmblem.fill(g, x, x + RAIL_X, ty, x + RAIL_X + fillW, ty + TRACK_H,
                        withAlpha(0xFFD27A, 0.20F + 0.30F * p));
            }
            case CHAINED -> {                                  // only his keys will move it now
                for (int cx = x + RAIL_X; cx < x + RAIL_X + fillW - 1; cx += 4) {
                    if (cx + 4 > x + BarEmblem.X0 && cx < x + BarEmblem.X1) {
                        continue;                              // (the chain goes under the medallion)
                    }
                    boolean across = ((cx - x) / 4) % 2 == 0;
                    int col = withAlpha(0x2A2C33, 0.92F);
                    if (across) {
                        g.fill(cx, ty, cx + 4, ty + 1, col);
                        g.fill(cx, ty + TRACK_H - 1, cx + 4, ty + TRACK_H, col);
                        g.fill(cx, ty, cx + 1, ty + TRACK_H, col);
                    } else {
                        g.fill(cx, ty + 1, cx + 4, ty + 3, col);
                    }
                }
            }
            default -> {
            }
        }

        for (float mark : k.marks) {
            int mx = BarEmblem.markX(x, RAIL_X, BAR_W, mark);
            if (mx != Integer.MIN_VALUE) {                     // (half: the medallion is the mark)
                stone(g, mx, ty + TRACK_H / 2, BAR_W * mark <= fillW, k.stone, t, mark);
            }
        }
        if (lock > 0.0F) {
            int lx = BarEmblem.markX(x, RAIL_X, BAR_W, lock);
            padlock(g, lx == Integer.MIN_VALUE ? x + BarEmblem.X1 + 3 : lx, ty, BAR_W * lock <= fillW, t);
        }
        RenderSystem.disableBlend();

        String text = sub.getString();
        g.drawString(mc.font, text, screenW / 2 - mc.font.width(text) / 2, y + SPR_H - 2, subCol, true);
        return SPR_H + 8;
    }

    /** A cut stone set on the rail: where the fight turns (built as MonstrosityBossBar's, smaller). */
    private static void stone(GuiGraphics g, int mx, int cy, boolean ahead, int rgb, float t, float mark) {
        float pulse = 0.5F + 0.5F * Mth.sin(t * 0.18F + mark * 9.0F);
        float base = ahead ? 0.95F : 0.55F;                    // the ones still to come burn, the passed go dim
        int[] halo = {1, 2, 3, 2, 1};
        for (int r = 0; r < halo.length; r++) {
            g.fill(mx - halo[r], cy - 2 + r, mx + halo[r] + 1, cy - 1 + r,
                    withAlpha(rgb, (0.12F + 0.10F * pulse) * base));
        }
        int[] half = {0, 1, 2, 1, 0};
        for (int r = 0; r < half.length; r++) {
            g.fill(mx - half[r], cy - 2 + r, mx + half[r] + 1, cy - 1 + r,
                    withAlpha(rgb, (0.78F + 0.20F * pulse) * base));
        }
        g.fill(mx, cy - 2, mx + 1, cy + 3, withAlpha(0xFFFFFF, (0.55F + 0.35F * pulse) * base));
    }

    /** The padlock of the last ring: a shackle over a body with a keyhole. */
    private static void padlock(GuiGraphics g, int mx, int ty, boolean ahead, float t) {
        float pulse = 0.5F + 0.5F * Mth.sin(t * 0.25F);
        float a = ahead ? 1.0F : 0.85F;
        int metal = withAlpha(0xE9B85A, a);
        int dark = withAlpha(0x3A2A14, a);
        int top = ty - 5;
        g.fill(mx - 2, top, mx + 3, top + 1, metal);           // the shackle
        g.fill(mx - 2, top, mx - 1, top + 4, metal);
        g.fill(mx + 2, top, mx + 3, top + 4, metal);
        g.fill(mx - 3, top + 3, mx + 4, top + 9, dark);        // the body, outlined
        g.fill(mx - 2, top + 4, mx + 3, top + 8, metal);
        g.fill(mx, top + 5, mx + 1, top + 7, withAlpha(ahead ? 0x8FF0FF : 0xFFE6A0, 0.7F + 0.3F * pulse));
    }

    @Nullable
    private static <T extends Entity> T nearest(Minecraft mc, Class<T> type) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        T best = null;
        double bd = Double.MAX_VALUE;
        for (T e : mc.level.getEntitiesOfClass(type, mc.player.getBoundingBox().inflate(48.0D))) {
            double d = e.distanceToSqr(mc.player);
            if (e.isAlive() && d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    // the ghost chase and the hit shake, stepped once per game tick
    private static void advance(Minecraft mc, Kind k, float progress) {
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        if (k.ghost < 0.0F || now - k.last > 100L || now < k.last) {
            k.ghost = progress;
            k.shown = progress;
            k.last = now;
            return;
        }
        if (progress < k.shown - 0.0005F) {
            k.flash = 6;
        }
        k.shown = progress;
        if (now != k.last) {
            long steps = Math.max(0L, Math.min(40L, now - k.last));
            k.last = now;
            for (long i = 0; i < steps; i++) {
                k.ghost = k.ghost > k.shown ? Math.max(k.shown, k.ghost - GHOST_DECAY) : k.shown;
                if (k.flash > 0) {
                    k.flash--;
                }
            }
        }
    }

    private static float time(Minecraft mc) {
        return mc.level != null ? mc.level.getGameTime() + mc.getFrameTime() : 0.0F;
    }

    private static int withAlpha(int rgb, float a) {
        return (Mth.clamp((int) (a * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }
}
