package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.entity.effect.KingsrimeSwordCrownEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * KORONACJA MROZU as GEOMETRY (KingsrimeSwordCrownEntity). While it gathers: ten blades of ice, tall and short by
 * turns, coming up out of the floor one after another round the wielder, leaning out like the points of a crown, and
 * a circlet of frost on the floor under them brightening with them - how many are up IS how full it is. Whole: the
 * circlet flashes out in a ring, and the crown turns slowly, pulsing. Let go: the blades are flung out, tipping to
 * point the way they fly, and a wave of ice runs over the floor to the burst's reach. Let go early: the blades sink
 * back the way they came.
 */
public class KingsrimeSwordCrownRenderer extends EntityRenderer<KingsrimeSwordCrownEntity> {

    /** The blades' tilt outward (degrees) while it gathers, and as they fly. */
    static final float TILT = 18.0F, TILT_FLY = 78.0F;
    /** The wave's section: {offset from its crest's radius, height as a share of the crest, v (0..1 in its band)}. */
    static final float[][] WAVE = {{-1.0F, 0.0F, 1.0F}, {-0.45F, 0.45F, 0.72F}, {-0.12F, 0.9F, 0.55F}, {0.0F, 1.0F, 0.5F},
            {0.12F, 0.55F, 0.35F}, {0.22F, 0.0F, 0.0F}};

    public KingsrimeSwordCrownRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(KingsrimeSwordCrownEntity e) {
        return KingsrimeSwordMesh.CRYSTAL;
    }

    @Override
    public void render(KingsrimeSwordCrownEntity e, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        float t = e.tickCount + partialTick;
        poseStack.pushPose();
        if (e.state() == KingsrimeSwordCrownEntity.GATHER) {
            // on its owner, where the client sees him this frame (it follows him only as often as it is synced)
            Entity o = e.owner();
            if (o != null) {
                poseStack.translate(Mth.lerp(partialTick, o.xo, o.getX()) - Mth.lerp(partialTick, e.xo, e.getX()),
                        Mth.lerp(partialTick, o.yo, o.getY()) - Mth.lerp(partialTick, e.yo, e.getY()),
                        Mth.lerp(partialTick, o.zo, o.getZ()) - Mth.lerp(partialTick, e.zo, e.getZ()));
            }
        }
        Matrix4f m = poseStack.last().pose();
        Matrix3f n = poseStack.last().normal();
        Frame f = Frame.at(e.state(), t, e.since(), e.fullTicks(), e.held());
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(KingsrimeSwordMesh.CRYSTAL));
        blades(vc, m, n, f, e.getId());
        VertexConsumer rc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(KingsrimeSwordMesh.RING));
        floor(rc, m, n, f, e.ring(t));
        poseStack.popPose();
        super.render(e, yaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** What the crown is doing at one moment, worked out once for the blades and the floor. */
    static final class Frame {
        int state;
        /** How whole (0..1), how far into the burst (0..1), how far sunk (0..1), the crown's turn (radians). */
        float gathered, burst, sunk, spin;
        /** Ticks since it was whole (negative before), ticks since the burst. */
        float sinceFull, sinceBurst;

        static Frame at(int state, float t, int since, int full, float held) {
            Frame f = new Frame();
            f.state = state;
            if (state == KingsrimeSwordCrownEntity.GATHER) {
                f.gathered = Mth.clamp(t / full, 0.0F, 1.0F);
                f.sinceFull = t - full;
            } else if (state == KingsrimeSwordCrownEntity.BURST) {
                f.gathered = 1.0F;
                f.sinceBurst = t - since;
                f.burst = Mth.clamp(f.sinceBurst / 6.0F, 0.0F, 1.0F);
                f.sinceFull = since - full + f.sinceBurst;
            } else {
                f.sunk = Mth.clamp((t - since) / KingsrimeSwordCrownEntity.SINK_LIFE, 0.0F, 1.0F);
                f.gathered = held * (1.0F - f.sunk);
                f.sinceFull = -1.0F;
            }
            f.spin = Math.max(0.0F, Math.min(f.sinceFull, state == KingsrimeSwordCrownEntity.BURST ? since - full : 1.0E9F))
                    * 0.03F;
            return f;
        }
    }

    static void blades(VertexConsumer vc, Matrix4f m, Matrix3f n, Frame f, int id) {
        int count = KingsrimeSwordCrownEntity.BLADES;
        float whole = f.state == KingsrimeSwordCrownEntity.GATHER && f.sinceFull >= 0.0F ? 1.0F : 0.0F;
        float pulse = whole * (0.5F + 0.5F * Mth.sin(f.sinceFull * 0.45F));
        float flash = f.sinceFull >= 0.0F && f.sinceFull < 4.0F && f.state == KingsrimeSwordCrownEntity.GATHER
                ? 1.0F - f.sinceFull / 4.0F : 0.0F;
        for (int k = 0; k < count; k++) {
            float a = f.spin + Mth.TWO_PI * k / count;
            float c = Mth.cos(a), s = Mth.sin(a);
            float len = (k % 2 == 0 ? 1.85F : 1.35F) * (0.95F + 0.1F * KingsrimeSwordMesh.hash(k, id));
            float p = Mth.clamp(f.gathered * 1.45F - 0.45F * k / (count - 1), 0.0F, 1.0F);
            float up = 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
            float alpha = (0.55F + 0.45F * up) * (1.0F - f.sunk);
            float tilt = TILT, r = KingsrimeSwordCrownEntity.RADIUS, y = -len * (1.0F - up) - 0.15F * (1.0F - up);
            if (f.state == KingsrimeSwordCrownEntity.BURST) {
                float b = f.burst;
                float go = 1.0F - (1.0F - b) * (1.0F - b);
                tilt = TILT + (TILT_FLY - TILT) * go;
                r = KingsrimeSwordCrownEntity.RADIUS + (KingsrimeSwordCrownEntity.REACH - 1.2F - KingsrimeSwordCrownEntity.RADIUS)
                        * go;
                y = 0.25F + 0.55F * Mth.sin(b * Mth.PI);
                alpha = b < 0.6F ? 1.0F : 1.0F - (b - 0.6F) / 0.4F;
            }
            if (alpha <= 0.01F || up <= 0.0F && f.state != KingsrimeSwordCrownEntity.BURST) {
                continue;
            }
            float tr = (float) Math.toRadians(tilt);
            float[] axis = {c * Mth.sin(tr), Mth.cos(tr), s * Mth.sin(tr)};
            float[] flat = {-s, 0.0F, c};
            float[] base = {c * r, y, s * r};
            float bright = 0.78F + 0.22F * Math.max(pulse, Math.max(flash, f.state == KingsrimeSwordCrownEntity.BURST ? 1.0F : 0.0F));
            float wide = k % 2 == 0 ? 0.2F : 0.15F;
            KingsrimeSwordMesh.blade(vc, m, n, base, axis, flat, len, wide, 0.06F, 0.24F,
                    0.82F * bright + 0.18F * flash, 0.93F * bright + 0.07F * flash, 1.0F, alpha);
        }
    }

    static void floor(VertexConsumer vc, Matrix4f m, Matrix3f n, Frame f, float ring) {
        float r0 = KingsrimeSwordCrownEntity.RADIUS - 0.28F, r1 = KingsrimeSwordCrownEntity.RADIUS + 0.28F;
        // the circlet: as bright as it is whole; gone over the burst's first ticks
        float ca = (0.2F + 0.7F * f.gathered) * (1.0F - f.sunk);
        if (f.state == KingsrimeSwordCrownEntity.BURST) {
            ca = Mth.clamp(1.0F - f.sinceBurst / 4.0F, 0.0F, 1.0F);
        }
        if (ca > 0.01F) {
            KingsrimeSwordMesh.band(vc, m, n, r0, r1, 0.04F, KingsrimeSwordMesh.CIRCLET_V0, KingsrimeSwordMesh.CIRCLET_V1, 10,
                    -f.spin, 1.0F, 1.0F, 1.0F, ca);
        }
        // the moment it is whole: the circlet's ring flashed out a block
        if (f.state == KingsrimeSwordCrownEntity.GATHER && f.sinceFull >= 0.0F && f.sinceFull < 6.0F) {
            float k = f.sinceFull / 6.0F;
            float grow = 1.0F + 0.5F * k;
            KingsrimeSwordMesh.band(vc, m, n, r0 * grow, r1 * grow, 0.06F, KingsrimeSwordMesh.CIRCLET_V0,
                    KingsrimeSwordMesh.CIRCLET_V1, 10, -f.spin, 1.0F, 1.0F, 1.0F, 0.9F * (1.0F - k));
        }
        if (f.state != KingsrimeSwordCrownEntity.BURST) {
            return;
        }
        // the wave over the floor
        float run = Mth.clamp(f.sinceBurst / KingsrimeSwordCrownEntity.RUN, 0.0F, 1.0F);
        float wa = f.sinceBurst <= KingsrimeSwordCrownEntity.RUN ? 1.0F
                : Mth.clamp(1.0F - (f.sinceBurst - KingsrimeSwordCrownEntity.RUN) / 6.0F, 0.0F, 1.0F);
        if (wa > 0.01F) {
            wave(vc, m, n, ring, 0.85F * (1.0F - 0.45F * run), wa);
        }
    }

    static void wave(VertexConsumer vc, Matrix4f m, Matrix3f n, float r, float h, float alpha) {
        int seg = Mth.clamp((int) (r * 10.0F) + 16, 16, 96);
        float arc = Mth.TWO_PI * r;
        float dv = KingsrimeSwordMesh.WAVE_V1 - KingsrimeSwordMesh.WAVE_V0;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float u0 = arc * i / seg / 2.0F, u1 = arc * (i + 1) / seg / 2.0F;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            for (int k = 0; k + 1 < WAVE.length; k++) {
                float[] p = WAVE[k], q = WAVE[k + 1];
                float rp = Math.max(0.05F, r + p[0]), rq = Math.max(0.05F, r + q[0]);
                float vp = KingsrimeSwordMesh.WAVE_V0 + dv * p[2], vq = KingsrimeSwordMesh.WAVE_V0 + dv * q[2];
                float[] a = {c0 * rp, p[1] * h + 0.02F, s0 * rp}, b = {c1 * rp, p[1] * h + 0.02F, s1 * rp},
                        c = {c1 * rq, q[1] * h + 0.02F, s1 * rq}, d = {c0 * rq, q[1] * h + 0.02F, s0 * rq};
                KingsrimeSwordMesh.put(vc, m, n, a[0], a[1], a[2], u0, vp, 0.9F, 0.97F, 1.0F, alpha);
                KingsrimeSwordMesh.put(vc, m, n, b[0], b[1], b[2], u1, vp, 0.9F, 0.97F, 1.0F, alpha);
                KingsrimeSwordMesh.put(vc, m, n, c[0], c[1], c[2], u1, vq, 0.9F, 0.97F, 1.0F, alpha);
                KingsrimeSwordMesh.put(vc, m, n, d[0], d[1], d[2], u0, vq, 0.9F, 0.97F, 1.0F, alpha);
            }
        }
    }
}
