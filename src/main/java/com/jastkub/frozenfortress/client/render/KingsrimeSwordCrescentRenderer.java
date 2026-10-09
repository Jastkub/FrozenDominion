package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.entity.effect.KingsrimeSwordCrescentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * SZRONOWE CIĘCIE as GEOMETRY (KingsrimeSwordCrescentEntity): a blade of ice bent into a crescent, flying middle
 * first, its horns trailing. In section a flat diamond - a hard white front edge, a pale body, a thinner back - and
 * off its back a wake of frost lying flat, thinning to nothing. Thick in the middle, drawn out to points at the horns.
 *
 * <p>It snaps out to full size over its first two ticks; at its end it shatters: the arc breaks into six pieces, each
 * flung outward from where it was, shrinking and fading. The wrath's crescents are a deeper, royal blue.
 */
public class KingsrimeSwordCrescentRenderer extends EntityRenderer<KingsrimeSwordCrescentEntity> {

    /** Segments along the arc, and how many go to each piece when it shatters. */
    static final int SEG = 24, PIECE = 4;
    /** Its section at the thickest (scale 1): the front edge ahead of the arc, the back behind it, half its height,
     *  and its wake's length. */
    static final float FRONT = 0.26F, BACK = 0.30F, HALF_H = 0.26F, WAKE = 1.1F;

    public KingsrimeSwordCrescentRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(KingsrimeSwordCrescentEntity e) {
        return KingsrimeSwordMesh.CRESCENT;
    }

    @Override
    public void render(KingsrimeSwordCrescentEntity e, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        float t = e.tickCount + partialTick;
        float end = e.endTick();
        float shatter = Mth.clamp((t - end - 1.0F) / KingsrimeSwordCrescentEntity.SHATTER, 0.0F, 1.0F);
        float alpha = 1.0F - shatter * shatter;
        if (alpha <= 0.01F) {
            return;
        }
        float grow = t < 2.0F ? 0.55F + 0.45F * Mth.clamp(t / 2.0F, 0.0F, 1.0F) : 1.0F;
        float s = e.scale() * grow;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-e.flightYaw()));
        poseStack.mulPose(Axis.XP.rotationDegrees(e.flightPitch()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(e.roll()));
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(KingsrimeSwordMesh.CRESCENT));
        float r = e.wrath() ? 0.70F : 0.93F, g = e.wrath() ? 0.79F : 0.98F, b = 1.0F;
        crescent(vc, poseStack.last().pose(), poseStack.last().normal(), s, shatter, r, g, b, alpha);
        poseStack.popPose();
        super.render(e, yaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** The crescent at scale s, `shatter` (0..1) of the way through breaking. */
    static void crescent(VertexConsumer vc, Matrix4f m, Matrix3f n, float s, float shatter, float r, float g, float b,
                         float alpha) {
        float rad = KingsrimeSwordCrescentEntity.R * s;
        float sweep = (float) Math.toRadians(KingsrimeSwordCrescentEntity.SWEEP);
        for (int i = 0; i < SEG; i++) {
            int piece = i / PIECE;
            float pc = -sweep + 2.0F * sweep * (piece * PIECE + PIECE * 0.5F) / SEG;     // the piece's middle
            float[][] a = section(i, sweep, rad, s), c = section(i + 1, sweep, rad, s);
            if (shatter > 0.0F) {
                float[] mid = {rad * Mth.sin(pc), 0.0F, rad * (Mth.cos(pc) - 1.0F)};
                float[] out = {Mth.sin(pc) * 1.3F * s * shatter, -0.35F * shatter * shatter, Mth.cos(pc) * 1.3F * s * shatter};
                float k = 1.0F - 0.5F * shatter;
                for (float[][] sec : new float[][][]{a, c}) {
                    for (float[] p : sec) {
                        for (int j = 0; j < 3; j++) {
                            p[j] = mid[j] + (p[j] - mid[j]) * k + out[j];
                        }
                    }
                }
            }
            float u0 = (float) i / SEG, u1 = (float) (i + 1) / SEG;
            // the four facets of the blade, front edge round to front edge; then the wake
            KingsrimeSwordMesh.quad(vc, m, n, a[0], c[0], c[1], a[1], u0, 0.02F, u1, 0.30F, r, g, b, alpha);
            KingsrimeSwordMesh.quad(vc, m, n, a[1], c[1], c[2], a[2], u0, 0.30F, u1, 0.55F, r, g, b, alpha);
            KingsrimeSwordMesh.quad(vc, m, n, a[2], c[2], c[3], a[3], u0, 0.55F, u1, 0.30F, r, g, b, alpha);
            KingsrimeSwordMesh.quad(vc, m, n, a[3], c[3], c[0], a[0], u0, 0.30F, u1, 0.02F, r, g, b, alpha);
            KingsrimeSwordMesh.quad(vc, m, n, a[2], c[2], c[4], a[4], u0, 0.55F, u1, 0.98F, r, g, b,
                    alpha * (1.0F - shatter));
        }
    }

    /** The section at arc step i: {front edge, top, back, bottom, wake's end}. */
    static float[][] section(int i, float sweep, float rad, float s) {
        float th = -sweep + 2.0F * sweep * i / SEG;
        float taper = (float) Math.pow(Math.max(0.0F, Mth.cos(th / sweep * Mth.HALF_PI)), 0.7D);
        float ex = Mth.sin(th), ez = Mth.cos(th);
        float px = rad * ex, pz = rad * (ez - 1.0F);
        float f = FRONT * taper * s, bk = BACK * taper * s, h = HALF_H * taper * s, w = (BACK + WAKE) * taper * s;
        return new float[][]{
                {px + ex * f, 0.0F, pz + ez * f},
                {px, h, pz},
                {px - ex * bk, 0.0F, pz - ez * bk},
                {px, -h, pz},
                {px - ex * w, 0.0F, pz - ez * w}};
    }
}
