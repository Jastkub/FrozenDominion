package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.FrostStrikeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Real geometry for the ground strike: a beacon-style column, plus the ring
 * that warns about it.
 *
 * <p>Both used to be particles sent from the entity's tick, and at eight
 * blocks tall that reads as a thin smear of snow rather than something that
 * will kill you. This is the same nested-tube trick the sword beam uses - a
 * wide translucent mantle around a narrow full-bright core - so the column is
 * solid, and the warning ring is drawn as a flat band on the floor that
 * closes as the clock runs out.
 */
public class FrostStrikeRenderer extends EntityRenderer<FrostStrikeEntity> {

    private static final ResourceLocation OUTER =
            FrozenFortress.id("textures/entity/velkhar_beam_outer.png");
    private static final ResourceLocation INNER =
            FrozenFortress.id("textures/entity/velkhar_beam_inner.png");

    private static final float HEIGHT = 14.0F;
    private static final float OUTER_RADIUS = 1.7F;
    private static final float MID_RADIUS = 1.05F;
    private static final float INNER_RADIUS = 0.72F;
    private static final float TILE = 4.0F;

    public FrostStrikeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(FrostStrikeEntity entity) {
        return OUTER;
    }

    @Override
    public void render(FrostStrikeEntity entity, float yaw, float partialTick,
                       PoseStack poses, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        float time = age * 0.35F;

        if (age <= FrostStrikeEntity.DRAW_TICKS) {
            // The warning: a band on the floor drawing in toward the centre.
            float t = age / FrostStrikeEntity.DRAW_TICKS;
            float r = OUTER_RADIUS * (1.5F - t * 0.5F);
            float alpha = 0.30F + 0.45F * Mth.abs(Mth.sin(age * 0.35F));
            poses.pushPose();
            poses.translate(0.0D, 0.06D, 0.0D);
            ring(poses.last(), buffers.getBuffer(RenderType.entityTranslucentEmissive(INNER)),
                    r * 0.82F, r, 0.55F, 0.85F, 1.0F, alpha);
            poses.popPose();
            return;
        }

        float since = age - FrostStrikeEntity.DRAW_TICKS;
        if (since > FrostStrikeEntity.STRIKE_TICKS) {
            return;
        }
        // Full width the instant it fires, then bleeding out over its life.
        float fade = 1.0F - (since / FrostStrikeEntity.STRIKE_TICKS);
        float punch = since < 2.0F ? 1.45F : 1.0F;
        float scroll = -time * 1.6F;

        poses.pushPose();
        PoseStack.Pose pose = poses.last();
        // Three nested shells rather than two, and the middle one is what
        // makes it look solid: a beacon beam reads as dense because there is
        // no gap between its haze and its core.
        column(pose, buffers.getBuffer(RenderType.entityTranslucent(OUTER)),
                OUTER_RADIUS * punch * (0.6F + 0.4F * fade), scroll,
                0.40F, 0.74F, 1.0F, 0.62F * fade);
        column(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(OUTER)),
                MID_RADIUS * punch * (0.65F + 0.35F * fade), scroll * 1.3F,
                0.66F, 0.90F, 1.0F, 0.80F * fade);
        column(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(INNER)),
                INNER_RADIUS * punch * (0.7F + 0.3F * fade), scroll * 1.9F,
                0.96F, 1.0F, 1.0F, 1.0F * fade);
        // a flare where it comes out of the floor
        ring(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(INNER)),
                OUTER_RADIUS * 0.4F, OUTER_RADIUS * (1.6F + since * 0.16F),
                0.7F, 0.92F, 1.0F, 0.6F * fade);
        poses.popPose();
    }

    /** A vertical square tube, drawn from both sides so it is solid inside. */
    private void column(PoseStack.Pose pose, VertexConsumer buffer, float radius,
                        float vOffset, float r, float g, float b, float a) {
        float[][] corners = {
                {radius, radius}, {radius, -radius}, {-radius, -radius}, {-radius, radius}
        };
        float vLen = HEIGHT / TILE;
        for (int i = 0; i < 4; i++) {
            float[] c0 = corners[i];
            float[] c1 = corners[(i + 1) % 4];
            wall(pose, buffer, c0, c1, vOffset, vLen, r, g, b, a);
            wall(pose, buffer, c1, c0, vOffset, vLen, r, g, b, a);
        }
    }

    private void wall(PoseStack.Pose pose, VertexConsumer buffer, float[] c0, float[] c1,
                      float v0, float vLen, float r, float g, float b, float a) {
        Matrix4f mat = pose.pose();
        Matrix3f nrm = pose.normal();
        vertex(buffer, mat, nrm, c0[0], 0.0F, c0[1], 0.0F, v0, r, g, b, a);
        vertex(buffer, mat, nrm, c1[0], 0.0F, c1[1], 1.0F, v0, r, g, b, a);
        // Held nearly full strength all the way up - fading it out at the
        // top was what made the old column look like a puff of snow.
        vertex(buffer, mat, nrm, c1[0], HEIGHT, c1[1], 1.0F, v0 + vLen, r, g, b, a * 0.75F);
        vertex(buffer, mat, nrm, c0[0], HEIGHT, c0[1], 0.0F, v0 + vLen, r, g, b, a * 0.75F);
    }

    /** A flat annulus lying on the floor. */
    private void ring(PoseStack.Pose pose, VertexConsumer buffer, float inner, float outer,
                      float r, float g, float b, float a) {
        Matrix4f mat = pose.pose();
        Matrix3f nrm = pose.normal();
        int steps = 28;
        for (int i = 0; i < steps; i++) {
            double a0 = Math.PI * 2.0D * i / steps;
            double a1 = Math.PI * 2.0D * (i + 1) / steps;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            // both windings, so it is visible from underneath too
            for (int pass = 0; pass < 2; pass++) {
                float y = pass == 0 ? 0.0F : -0.001F;
                vertex(buffer, mat, nrm, c0 * inner, y, s0 * inner, 0.0F, 0.0F, r, g, b, a);
                vertex(buffer, mat, nrm, c0 * outer, y, s0 * outer, 1.0F, 0.0F, r, g, b, a * 0.1F);
                vertex(buffer, mat, nrm, c1 * outer, y, s1 * outer, 1.0F, 1.0F, r, g, b, a * 0.1F);
                vertex(buffer, mat, nrm, c1 * inner, y, s1 * inner, 0.0F, 1.0F, r, g, b, a);
            }
        }
    }

    private void vertex(VertexConsumer buffer, Matrix4f mat, Matrix3f nrm,
                        float x, float y, float z, float u, float v,
                        float r, float g, float b, float a) {
        buffer.vertex(mat, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(nrm, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
