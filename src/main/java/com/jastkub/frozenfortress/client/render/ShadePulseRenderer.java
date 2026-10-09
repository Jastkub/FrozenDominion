package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.ShadePulseEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * PULS MROKU as GEOMETRY (ShadePulseEntity; tools/gen_shade_pulse.py paints its sheet): built fresh every frame at the
 * radius it has reached, so it keeps its own thickness however far it runs - as the wave of frost (FrostWaveRenderer).
 *
 * <p>While he gathers it: a POOL of the dark spreading at his feet, its rim lifting into a low ring with flames of
 * shadow licking up off it - higher as the tell goes on. Then the RING: a wall of shadow a block high, its skirt long
 * behind it, its crest a line of pale violet fire, and flames of shadow standing off the crest, flickering, leaning
 * out the way it runs. Its own light (it shows in the dark it makes); it fades once it has run all the way.
 */
public class ShadePulseRenderer extends EntityRenderer<ShadePulseEntity> {

    private static final ResourceLocation TEX = FrozenFortress.id("textures/entity/shade_pulse.png");
    /** The cross-section: {offset from the crest's radius, height as a share of the crest, v}. */
    private static final float[][] PROFILE = {
            {-1.6F, 0.0F, 0.8F}, {-0.8F, 0.35F, 0.624F}, {-0.25F, 0.85F, 0.464F}, {0.0F, 1.0F, 0.4F},
            {0.12F, 0.7F, 0.32F}, {0.24F, 0.2F, 0.16F}, {0.3F, 0.0F, 0.0F}};   // (v 0-0.8 the ring's rows; 0.86-1 the pool's)
    /** The crest's height (blocks) once it runs: a jump clears it (ShadePulseEntity.CLEAR). */
    private static final float CREST = 1.1F;

    public ShadePulseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ShadePulseEntity pulse) {
        return TEX;
    }

    @Override
    public void render(ShadePulseEntity pulse, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        float tt = pulse.tickCount + partialTick;
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(TEX));
        Matrix4f m = poseStack.last().pose();
        PoseStack.Pose n = poseStack.last();
        int gather = pulse.gatherTicks();
        if (tt < gather) {
            float u = Mth.clamp(tt / Math.max(1, gather), 0.0F, 1.0F);
            float alpha = Mth.clamp(tt / 5.0F, 0.0F, 1.0F);
            float rim = Math.max(0.3F, pulse.pool(tt));
            disc(vc, m, n, rim, alpha * 0.9F);
            ring(vc, m, n, rim, 0.18F + 0.55F * u, alpha, pulse.getId(), tt, 0.6F + 0.8F * u);
        } else {
            float r = Math.max(0.3F, pulse.radius(tt));
            float end = gather + pulse.runTicks();
            float alpha = tt <= end ? 1.0F : Mth.clamp(1.0F - (tt - end) / ShadePulseEntity.FADE, 0.0F, 1.0F);
            if (alpha <= 0.01F) {
                return;
            }
            // the first ticks of the run: the pool it came from, sinking away behind it
            float sink = Mth.clamp(1.0F - (tt - gather) / 6.0F, 0.0F, 1.0F);
            if (sink > 0.0F) {
                disc(vc, m, n, ShadePulseEntity.POOL, sink * 0.9F);
            }
            ring(vc, m, n, r, CREST, alpha, pulse.getId(), tt, 1.0F);
        }
        super.render(pulse, yaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** The pool of the dark, flat on the floor: a fan from the middle to the rim (the sheet's darkest rows). */
    private static void disc(VertexConsumer vc, Matrix4f m, PoseStack.Pose n, float r, float alpha) {
        int seg = Mth.clamp((int) (r * 8.0F) + 12, 12, 48);
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (Math.PI * 2.0D * i / seg), a1 = (float) (Math.PI * 2.0D * (i + 1) / seg);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            put(vc, m, n, 0.0F, 0.03F, 0.0F, 0.5F, 0.97F, alpha);
            put(vc, m, n, c0 * r, 0.03F, s0 * r, 0.0F, 0.88F, alpha);
            put(vc, m, n, c1 * r, 0.03F, s1 * r, 1.0F, 0.88F, alpha);
            put(vc, m, n, 0.0F, 0.03F, 0.0F, 0.5F, 0.97F, alpha);
        }
    }

    /** The ring at radius r, crest h high, with its flames (each its own height, flickering; `flame` scales them). */
    private static void ring(VertexConsumer vc, Matrix4f m, PoseStack.Pose n, float r, float h, float alpha, int id, float tt,
                             float flame) {
        int seg = Mth.clamp((int) (r * 10.0F) + 12, 12, 160);
        float arc = (float) (Math.PI * 2.0D * r);
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (Math.PI * 2.0D * i / seg), a1 = (float) (Math.PI * 2.0D * (i + 1) / seg);
            float u0 = arc * i / seg / 2.0F, u1 = arc * (i + 1) / seg / 2.0F;    // the sheet tiles every two blocks
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            for (int k = 0; k + 1 < PROFILE.length; k++) {
                float[] p = PROFILE[k], q = PROFILE[k + 1];
                float rp = Math.max(0.05F, r + p[0]), rq = Math.max(0.05F, r + q[0]);
                put(vc, m, n, c0 * rp, p[1] * h, s0 * rp, u0, p[2], alpha);
                put(vc, m, n, c1 * rp, p[1] * h, s1 * rp, u1, p[2], alpha);
                put(vc, m, n, c1 * rq, q[1] * h, s1 * rq, u1, q[2], alpha);
                put(vc, m, n, c0 * rq, q[1] * h, s0 * rq, u0, q[2], alpha);
            }
        }
        // the flames off the crest: a blade along the ring and one across it, leaning out the way it runs
        int flames = Math.max(6, (int) (arc / 0.5F));
        for (int i = 0; i < flames; i++) {
            float a = (float) (Math.PI * 2.0D * (i + 0.5F) / flames);
            float hash = Mth.frac(Mth.sin(i * 12.9898F + id * 78.233F) * 43758.547F);
            float lick = 0.75F + 0.25F * Mth.sin(tt * (0.5F + 0.4F * hash) + i * 1.7F);
            float th = h * (0.45F + 0.8F * hash) * lick * flame;
            float c = Mth.cos(a), s = Mth.sin(a);
            float bx = c * r, bz = s * r, by = h * 0.9F;
            float tx = c * (r + th * 0.45F), tz = s * (r + th * 0.45F), ty = h + th;
            float w = 0.16F + 0.08F * hash;
            put(vc, m, n, bx - s * w, by, bz + c * w, 0.0F, 0.5F, alpha);
            put(vc, m, n, bx + s * w, by, bz - c * w, 0.25F, 0.5F, alpha);
            put(vc, m, n, tx, ty, tz, 0.125F, 0.38F, alpha);
            put(vc, m, n, tx, ty, tz, 0.125F, 0.38F, alpha);
            put(vc, m, n, bx - c * w, by, bz - s * w, 0.0F, 0.5F, alpha);
            put(vc, m, n, bx + c * w, by, bz + s * w, 0.25F, 0.5F, alpha);
            put(vc, m, n, tx, ty, tz, 0.125F, 0.38F, alpha);
            put(vc, m, n, tx, ty, tz, 0.125F, 0.38F, alpha);
        }
    }

    private static void put(VertexConsumer vc, Matrix4f m, PoseStack.Pose n, float x, float y, float z, float u, float v,
                            float alpha) {
        vc.addVertex(m, x, y + 0.02F, z).setColor(1.0F, 1.0F, 1.0F, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(n, 0.0F, 1.0F, 0.0F);
    }
}
