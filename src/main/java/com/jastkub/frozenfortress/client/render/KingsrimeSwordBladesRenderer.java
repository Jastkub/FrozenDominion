package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.entity.effect.KingsrimeSwordBladesEntity;
import com.jastkub.frozenfortress.item.KingsrimeSwordItem;
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
 * KRÓLEWSKI KROK's hanging blades as GEOMETRY (KingsrimeSwordBladesEntity): along the path, each where the step passed
 * it, a blade of ice like a sword hung point-down in the air - a diamond-section blade, a short crossguard of ice at its
 * top - snapping into being, bobbing, then trembling harder and burning whiter as the burst comes. A breath before it,
 * a line of light opens along the whole path at their height; at the burst it flares and closes, and every blade flies
 * apart in three shards, flung out from the line, tumbling and falling, gone in half a second.
 */
public class KingsrimeSwordBladesRenderer extends EntityRenderer<KingsrimeSwordBladesEntity> {

    public KingsrimeSwordBladesRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(KingsrimeSwordBladesEntity e) {
        return KingsrimeSwordMesh.CRYSTAL;
    }

    @Override
    public void render(KingsrimeSwordBladesEntity e, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        float t = e.tickCount + partialTick;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-e.pathYaw()));
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(KingsrimeSwordMesh.CRYSTAL));
        blades(vc, poseStack.last().pose(), poseStack.last().normal(), t, e.pathLength(), e.rise(), e.count(), e.getId());
        poseStack.popPose();
        super.render(e, yaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** All of it at `t` ticks: the path runs along +z, `len` long, climbing `rise`. */
    static void blades(VertexConsumer vc, Matrix4f m, Matrix3f n, float t, float len, float rise, int count, int id) {
        float burst = KingsrimeSwordBladesEntity.BURST, stepT = KingsrimeSwordItem.STEP_T;
        float charge = Mth.clamp((t - stepT) / (burst - stepT), 0.0F, 1.0F);
        // the line of the cut: opens a breath before the burst, flares at it, closes
        float lineW = t < burst - 3.0F ? 0.0F
                : t < burst ? 0.06F * (t - burst + 3.0F) / 3.0F
                : 0.28F * Mth.clamp(1.0F - (t - burst) / 5.0F, 0.0F, 1.0F);
        if (lineW > 0.005F) {
            float y0 = 1.05F, y1 = 1.05F + rise;
            float[] a = {-lineW, y0, 0.0F}, b = {-lineW, y1, len}, c = {lineW, y1, len}, d = {lineW, y0, 0.0F};
            KingsrimeSwordMesh.quad(vc, m, n, a, b, c, d, 0.35F, 0.0F, 0.65F, 0.06F, 1.0F, 1.0F, 1.0F, 0.95F);
            float[] a2 = {0.0F, y0 - lineW, 0.0F}, b2 = {0.0F, y1 - lineW, len}, c2 = {0.0F, y1 + lineW, len},
                    d2 = {0.0F, y0 + lineW, 0.0F};
            KingsrimeSwordMesh.quad(vc, m, n, a2, b2, c2, d2, 0.35F, 0.0F, 0.65F, 0.06F, 1.0F, 1.0F, 1.0F, 0.95F);
        }
        float after = Mth.clamp((t - burst) / KingsrimeSwordBladesEntity.AFTER, 0.0F, 1.0F);
        for (int i = 0; i < count; i++) {
            Blade bl = new Blade(i, count, len, rise, id);
            float born = stepT * (i + 0.5F) / count;
            if (t < born) {
                continue;
            }
            if (t < burst) {
                float g = Mth.clamp((t - born) / 2.0F, 0.0F, 1.0F);
                float size = g < 1.0F ? 1.15F * Mth.sin(g * Mth.HALF_PI) : 1.0F;
                if (g >= 1.0F) {
                    size = 1.0F + 0.15F * Mth.clamp(1.0F - (t - born - 2.0F) / 2.0F, 0.0F, 1.0F);
                }
                float bob = 0.04F * Mth.sin(t * 0.6F + i * 1.3F);
                float jit = 0.09F * charge * charge * Mth.sin(t * 2.7F + i * 1.9F);
                float[] axis = KingsrimeSwordMesh.norm(bl.axis[0] + jit, bl.axis[1], bl.axis[2] - jit * 0.6F);
                float[] top = {bl.top[0], bl.top[1] + bob, bl.top[2]};
                float w = 0.7F * charge * charge;
                float r = 0.80F + 0.20F * w, gg = 0.92F + 0.08F * w;
                float a = 0.82F + 0.18F * charge;
                sword(vc, m, n, top, axis, bl.len * size, size, r, gg, 1.0F, a);
            } else if (after < 1.0F) {
                // three shards each, flung out from the line
                for (int k = 0; k < 3; k++) {
                    float h1 = KingsrimeSwordMesh.hash(i * 3 + k, id + 11), h2 = KingsrimeSwordMesh.hash(i * 3 + k, id + 23);
                    float side = (k == 1 ? -1.0F : 1.0F) * (bl.top[0] >= 0.0F ? 1.0F : -1.0F);
                    float[] dir = KingsrimeSwordMesh.norm(side * (0.7F + 0.5F * h1), 0.35F + 0.6F * h2, (h1 - 0.5F) * 0.9F);
                    float go = 2.2F * (1.0F - (1.0F - after) * (1.0F - after));
                    float frac = (k + 0.5F) / 3.0F;
                    float[] from = {bl.top[0] + bl.axis[0] * bl.len * frac, bl.top[1] + bl.axis[1] * bl.len * frac,
                            bl.top[2] + bl.axis[2] * bl.len * frac};
                    float[] at = {from[0] + dir[0] * go, from[1] + dir[1] * go - 0.9F * after * after, from[2] + dir[2] * go};
                    float spin = after * (3.0F + 3.0F * h2);
                    float[] ax = KingsrimeSwordMesh.norm(bl.axis[0] + dir[0] * spin, bl.axis[1] + Mth.sin(spin) * 0.8F,
                            bl.axis[2] + dir[2] * spin);
                    float sl = bl.len * 0.4F;
                    KingsrimeSwordMesh.blade(vc, m, n, new float[]{at[0] - ax[0] * sl * 0.5F, at[1] - ax[1] * sl * 0.5F,
                                    at[2] - ax[2] * sl * 0.5F}, ax, KingsrimeSwordMesh.square(ax), sl, 0.09F, 0.04F, 0.4F,
                            1.0F, 1.0F, 1.0F, 1.0F - after * after);
                }
            }
        }
    }

    /** A hanging sword of ice: its crossguard at `top`, its blade down `axis`. */
    static void sword(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] top, float[] axis, float len, float size,
                      float r, float g, float b, float a) {
        float[] flat = KingsrimeSwordMesh.square(axis);
        KingsrimeSwordMesh.blade(vc, m, n, top, axis, flat, len, 0.17F * size, 0.055F * size, 0.16F, r, g, b, a);
        float gl = 0.5F * size;
        float[] gBase = {top[0] - flat[0] * gl * 0.5F, top[1] - flat[1] * gl * 0.5F, top[2] - flat[2] * gl * 0.5F};
        KingsrimeSwordMesh.blade(vc, m, n, gBase, flat, axis, gl, 0.05F * size, 0.045F * size, 0.5F, r, g, b, a);
        float hl = 0.26F * size;
        float[] up = {-axis[0], -axis[1], -axis[2]};
        KingsrimeSwordMesh.blade(vc, m, n, top, up, flat, hl, 0.04F * size, 0.04F * size, 0.45F, r, g, b, a);
    }

    /** Where blade i of `count` hangs and how it leans (the same each frame: hashed on the entity). */
    static final class Blade {
        final float[] top;
        final float[] axis;
        final float len;

        Blade(int i, int count, float len, float rise, int id) {
            float h1 = KingsrimeSwordMesh.hash(i, id), h2 = KingsrimeSwordMesh.hash(i, id + 1);
            float h3 = KingsrimeSwordMesh.hash(i, id + 2), h4 = KingsrimeSwordMesh.hash(i, id + 3);
            float h5 = KingsrimeSwordMesh.hash(i, id + 4);
            float z = len * (i + 0.5F) / count;
            float x = (i % 2 == 0 ? -1.0F : 1.0F) * (0.15F + 0.4F * h1);
            this.top = new float[]{x, 1.75F + 0.4F * h2 + rise * z / Math.max(0.1F, len), z};
            this.axis = KingsrimeSwordMesh.norm((h3 - 0.5F) * 0.8F + x * 0.4F, -1.0F, (h4 - 0.5F) * 0.7F - 0.2F);
            this.len = 1.25F + 0.45F * h5;
        }
    }
}
