package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The Kingsrime sword's skills drawn as GEOMETRY: the shapes the four renderers share, built fresh each frame. tools/gen_kingsrime_skills.py
 * paints their sheets and draws the same shapes (its review sheets), so the two must be changed together.
 *
 * <p>Everything is its own light (entityTranslucentEmissive, full bright) - and because that shader does not shade,
 * every facet is shaded HERE by how it faces a fixed light, so a blade of ice reads as a solid with facets, not as a
 * flat cut-out.
 */
public final class KingsrimeSwordMesh {

    public static final ResourceLocation CRESCENT = FrozenFortress.id("textures/entity/fx_kingsrime_crescent.png");
    public static final ResourceLocation CRYSTAL = FrozenFortress.id("textures/entity/fx_kingsrime_crystal.png");
    public static final ResourceLocation RING = FrozenFortress.id("textures/entity/fx_kingsrime_ring.png");

    /** The ring sheet's bands (v): the crown's circlet, the burst's wave, the frost on the floor. */
    public static final float CIRCLET_V0 = 0.0F, CIRCLET_V1 = 0.34F, WAVE_V0 = 0.375F, WAVE_V1 = 0.69F,
            DISC_V0 = 0.75F, DISC_V1 = 1.0F;

    private static final float LX = 0.36F, LY = 0.86F, LZ = 0.36F;

    private KingsrimeSwordMesh() {
    }

    public static void put(VertexConsumer vc, Matrix4f m, com.mojang.blaze3d.vertex.PoseStack.Pose n, float x, float y, float z, float u, float v,
                           float r, float g, float b, float a) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(n, 0.0F, 1.0F, 0.0F);
    }

    /** How lit a facet with this (unnormalised) normal is: 0.6 edge-on to the light, 1 square to it, either side. */
    public static float shade(float nx, float ny, float nz) {
        float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1.0E-6F) {
            return 0.8F;
        }
        return 0.6F + 0.4F * Math.abs((nx * LX + ny * LY + nz * LZ) / l);
    }

    /** A triangle (a quad with its last corner doubled), shaded by its facing. */
    public static void tri(VertexConsumer vc, Matrix4f m, com.mojang.blaze3d.vertex.PoseStack.Pose n, float[] a, float[] b, float[] c,
                           float ua, float va, float ub, float vb, float uc, float vc2,
                           float r, float g, float bl, float alpha) {
        float ex = b[0] - a[0], ey = b[1] - a[1], ez = b[2] - a[2];
        float fx = c[0] - a[0], fy = c[1] - a[1], fz = c[2] - a[2];
        float k = shade(ey * fz - ez * fy, ez * fx - ex * fz, ex * fy - ey * fx);
        put(vc, m, n, a[0], a[1], a[2], ua, va, r * k, g * k, bl * k, alpha);
        put(vc, m, n, b[0], b[1], b[2], ub, vb, r * k, g * k, bl * k, alpha);
        put(vc, m, n, c[0], c[1], c[2], uc, vc2, r * k, g * k, bl * k, alpha);
        put(vc, m, n, c[0], c[1], c[2], uc, vc2, r * k, g * k, bl * k, alpha);
    }

    /** A quad a-b-c-d, shaded by the facing of its first three corners. */
    public static void quad(VertexConsumer vc, Matrix4f m, com.mojang.blaze3d.vertex.PoseStack.Pose n, float[] a, float[] b, float[] c, float[] d,
                            float u0, float v0, float u1, float v1, float r, float g, float bl, float alpha) {
        float ex = b[0] - a[0], ey = b[1] - a[1], ez = b[2] - a[2];
        float fx = c[0] - a[0], fy = c[1] - a[1], fz = c[2] - a[2];
        float k = shade(ey * fz - ez * fy, ez * fx - ex * fz, ex * fy - ey * fx);
        put(vc, m, n, a[0], a[1], a[2], u0, v0, r * k, g * k, bl * k, alpha);
        put(vc, m, n, b[0], b[1], b[2], u1, v0, r * k, g * k, bl * k, alpha);
        put(vc, m, n, c[0], c[1], c[2], u1, v1, r * k, g * k, bl * k, alpha);
        put(vc, m, n, d[0], d[1], d[2], u0, v1, r * k, g * k, bl * k, alpha);
    }

    /**
     * A BLADE OF ICE (on the crystal sheet): from `base` along `axis` (unit) for `len`, a diamond in section - `wide`
     * along `flat` (unit, square to the axis), `thin` across both - widest at `waist` of the way up; a long point to
     * its tip, a short one to its root. Four facets each way, the sheet's bright edges on their edges.
     */
    public static void blade(VertexConsumer vc, Matrix4f m, com.mojang.blaze3d.vertex.PoseStack.Pose n, float[] base, float[] axis, float[] flat,
                             float len, float wide, float thin, float waist, float r, float g, float b, float alpha) {
        if (len <= 0.01F || alpha <= 0.01F) {
            return;
        }
        float tx = axis[1] * flat[2] - axis[2] * flat[1];
        float ty = axis[2] * flat[0] - axis[0] * flat[2];
        float tz = axis[0] * flat[1] - axis[1] * flat[0];
        float wx = base[0] + axis[0] * len * waist, wy = base[1] + axis[1] * len * waist, wz = base[2] + axis[2] * len * waist;
        float[][] ring = {
                {wx + flat[0] * wide, wy + flat[1] * wide, wz + flat[2] * wide},
                {wx + tx * thin, wy + ty * thin, wz + tz * thin},
                {wx - flat[0] * wide, wy - flat[1] * wide, wz - flat[2] * wide},
                {wx - tx * thin, wy - ty * thin, wz - tz * thin}};
        float[] tip = {base[0] + axis[0] * len, base[1] + axis[1] * len, base[2] + axis[2] * len};
        float vr = 1.0F - waist;
        for (int k = 0; k < 4; k++) {
            float[] p = ring[k], q = ring[(k + 1) % 4];
            tri(vc, m, n, p, q, tip, 0.0F, vr, 1.0F, vr, 0.5F, 0.0F, r, g, b, alpha);
            tri(vc, m, n, q, p, base, 1.0F, vr, 0.0F, vr, 0.5F, 1.0F, r, g, b, alpha);
        }
    }

    /** A flat band round the vertical axis between radii r0 and r1 at height y, the sheet tiled `tiles` times round. */
    public static void band(VertexConsumer vc, Matrix4f m, com.mojang.blaze3d.vertex.PoseStack.Pose n, float r0, float r1, float y, float v0, float v1,
                            int tiles, float spin, float r, float g, float b, float alpha) {
        int seg = Math.max(24, tiles * 4);
        for (int i = 0; i < seg; i++) {
            float a0 = spin + Mth.TWO_PI * i / seg, a1 = spin + Mth.TWO_PI * (i + 1) / seg;
            float u0 = (float) tiles * i / seg, u1 = (float) tiles * (i + 1) / seg;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            put(vc, m, n, c0 * r0, y, s0 * r0, u0, v0, r, g, b, alpha);
            put(vc, m, n, c1 * r0, y, s1 * r0, u1, v0, r, g, b, alpha);
            put(vc, m, n, c1 * r1, y, s1 * r1, u1, v1, r, g, b, alpha);
            put(vc, m, n, c0 * r1, y, s0 * r1, u0, v1, r, g, b, alpha);
        }
    }

    /** A cheap stable hash of (i, salt) in 0..1. */
    public static float hash(int i, int salt) {
        return Mth.frac(Mth.sin(i * 12.9898F + salt * 78.233F) * 43758.547F);
    }

    public static float[] norm(float x, float y, float z) {
        float l = Mth.sqrt(x * x + y * y + z * z);
        return l < 1.0E-6F ? new float[]{0.0F, 1.0F, 0.0F} : new float[]{x / l, y / l, z / l};
    }

    /** A unit vector square to `a` (prefers lying level). */
    public static float[] square(float[] a) {
        float[] s = norm(-a[2], 0.0F, a[0]);
        if (Math.abs(a[0]) < 1.0E-3F && Math.abs(a[2]) < 1.0E-3F) {
            s = new float[]{1.0F, 0.0F, 0.0F};
        }
        return s;
    }
}
