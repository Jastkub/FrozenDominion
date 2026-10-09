package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.FrostWaveEntity;
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
 * The wave of frost as GEOMETRY: a ring-shaped wall of ice
 * built fresh every frame at the radius the wave has reached - so it keeps its
 * own thickness however wide it runs, where a scaled model would stretch.
 *
 * <p>Its cross-section, from behind to before: a long low skirt rising to the
 * crest, the crest, and a short steep fall at the front - a wave breaking
 * outward. Teeth of rime stand along the crest, each its own height. It is lit
 * from within (it ignores the room's light), and fades through the last
 * quarter of its run.
 */
public class FrostWaveRenderer extends EntityRenderer<FrostWaveEntity> {

    private static final ResourceLocation TEX = FrozenFortress.id("textures/entity/frost_wave.png");
    /** The cross-section: {offset from the crest's radius, height as a share of the crest, v}. */
    private static final float[][] PROFILE = {
            {-1.25F, 0.0F, 1.0F}, {-0.55F, 0.42F, 0.72F}, {-0.18F, 0.86F, 0.56F}, {0.0F, 1.0F, 0.5F},
            {0.14F, 0.62F, 0.4F}, {0.26F, 0.16F, 0.2F}, {0.32F, 0.0F, 0.0F}};

    public FrostWaveRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(FrostWaveEntity wave) {
        return TEX;
    }

    @Override
    public void render(FrostWaveEntity wave, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        float r = wave.radius(partialTick);
        float h = wave.crest(partialTick);
        float t = Mth.clamp((wave.tickCount + partialTick) / wave.span(), 0.0F, 1.0F);
        float alpha = Mth.clamp((1.0F - t) / 0.25F, 0.0F, 1.0F);
        if (alpha <= 0.01F) {
            return;
        }
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(TEX));
        Matrix4f m = poseStack.last().pose();
        PoseStack.Pose n = poseStack.last();
        // the whole ring, or only its arc: from (yaw - half) to (yaw + half), measured as entity yaw is
        float half = wave.arcHalf();
        float span = half >= 179.9F ? (float) (Math.PI * 2.0D) : (float) Math.toRadians(half * 2.0F);
        float start = half >= 179.9F ? 0.0F : (float) Math.toRadians(wave.arcYaw() + 90.0F - half);
        int seg = Mth.clamp((int) (r * 10.0F * span / (Math.PI * 2.0D)) + 12, 12, 120);
        float arc = (float) (span * r);
        for (int i = 0; i < seg; i++) {
            float a0 = start + span * i / seg;
            float a1 = start + span * (i + 1) / seg;
            float u0 = arc * i / seg / 2.0F;                     // the texture tiles every two blocks round
            float u1 = arc * (i + 1) / seg / 2.0F;
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
        // the teeth of rime along the crest: a blade each, along the ring and across it
        int teeth = Math.max(4, (int) (arc / 0.55F));
        for (int i = 0; i < teeth; i++) {
            float a = start + span * (i + 0.5F) / teeth;
            float hash = Mth.frac(Mth.sin(i * 12.9898F + wave.getId() * 78.233F) * 43758.547F);
            float th = h * (0.35F + 0.45F * hash);
            float c = Mth.cos(a), s = Mth.sin(a);
            float bx = c * r, bz = s * r, by = h * 0.92F;
            float tx = c * (r + th * 0.35F), tz = s * (r + th * 0.35F), ty = h + th;   // leaning outward
            float w = 0.13F + 0.06F * hash;
            // along the ring
            put(vc, m, n, bx - s * w, by, bz + c * w, 0.0F, 0.6F, alpha);
            put(vc, m, n, bx + s * w, by, bz - c * w, 0.25F, 0.6F, alpha);
            put(vc, m, n, tx, ty, tz, 0.125F, 0.46F, alpha);
            put(vc, m, n, tx, ty, tz, 0.125F, 0.46F, alpha);
            // across it
            put(vc, m, n, bx - c * w, by, bz - s * w, 0.0F, 0.6F, alpha);
            put(vc, m, n, bx + c * w, by, bz + s * w, 0.25F, 0.6F, alpha);
            put(vc, m, n, tx, ty, tz, 0.125F, 0.46F, alpha);
            put(vc, m, n, tx, ty, tz, 0.125F, 0.46F, alpha);
        }
        super.render(wave, yaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static void put(VertexConsumer vc, Matrix4f m, PoseStack.Pose n, float x, float y, float z,
                            float u, float v, float alpha) {
        vc.addVertex(m, x, y + 0.02F, z).setColor(0.86F, 0.96F, 1.0F, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(n, 0.0F, 1.0F, 0.0F);
    }
}
