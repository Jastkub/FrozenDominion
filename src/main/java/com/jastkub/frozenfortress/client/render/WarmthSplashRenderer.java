package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.WarmthSplashEntity;
import com.jastkub.frozenfortress.entity.projectile.ThrownWarmthEntity;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * A thrown flask of warmth bursting, as GEOMETRY, built fresh
 * every frame at the size it has reached:
 * <ul>
 *   <li>the DOME - a low shell of heat swelling out to the warmth's reach (ThrownWarmthEntity.RADIUS), its base on the
 *   floor and half as high as it is wide, bright at the floor and gone at its crown, its streaks turning slowly;</li>
 *   <li>the FRONT - a band over the floor just inside the dome's foot, brightest at its outer edge;</li>
 *   <li>the TONGUES - little flames standing up along the front, each its own height, thinning as they rise.</li>
 * </ul>
 * Lit from within (it ignores the room's light), all of it fading out over its short life
 * (tools/gen_warmth_splash.py draws its heat: warmth_splash.png, bright at the bottom, gone at the top).
 */
public class WarmthSplashRenderer extends EntityRenderer<WarmthSplashEntity> {

    private static final ResourceLocation TEX = FrozenFortress.id("textures/entity/warmth_splash.png");
    private static final float REACH = (float) ThrownWarmthEntity.RADIUS;
    private static final int AROUND = 32, UP = 6, TONGUES = 13;

    public WarmthSplashRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(WarmthSplashEntity splash) {
        return TEX;
    }

    @Override
    public void render(WarmthSplashEntity splash, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        float t = splash.progress(partialTick);
        float grow = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);          // out fast, then slowing
        float r = 0.35F + (REACH - 0.35F) * grow;
        float left = 1.0F - t;
        float domeA = 0.75F * (float) Math.pow(left, 1.5D);
        float frontA = 0.95F * (float) Math.pow(left, 1.1D);
        if (frontA <= 0.01F) {
            return;
        }
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(TEX));
        Matrix4f m = poseStack.last().pose();
        PoseStack.Pose n = poseStack.last();
        float turn = splash.getId() * 0.61F + t * 0.9F;                     // where its streaks start, and their drift
        float h = r * 0.5F;
        // THE DOME: rings from the floor (v 1, bright) to the crown (v 0, gone)
        for (int i = 0; i < AROUND; i++) {
            float a0 = turn + Mth.TWO_PI * i / AROUND, a1 = turn + Mth.TWO_PI * (i + 1) / AROUND;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = 2.0F * i / AROUND + t * 0.5F, u1 = 2.0F * (i + 1) / AROUND + t * 0.5F;
            for (int k = 0; k < UP; k++) {
                float p0 = Mth.HALF_PI * k / UP, p1 = Mth.HALF_PI * (k + 1) / UP;
                float r0 = r * Mth.cos(p0), r1 = r * Mth.cos(p1);
                float y0 = h * Mth.sin(p0), y1 = h * Mth.sin(p1);
                float v0 = 1.0F - (float) k / UP, v1 = 1.0F - (float) (k + 1) / UP;
                put(vc, m, n, c0 * r0, y0, s0 * r0, u0, v0, domeA);
                put(vc, m, n, c1 * r0, y0, s1 * r0, u1, v0, domeA);
                put(vc, m, n, c1 * r1, y1, s1 * r1, u1, v1, domeA);
                put(vc, m, n, c0 * r1, y1, s0 * r1, u0, v1, domeA);
            }
        }
        // THE FRONT over the floor: from a block inside the dome's foot (dim) out to it (bright)
        float ri = Math.max(0.0F, r - 1.0F), ro = r;
        for (int i = 0; i < AROUND; i++) {
            float a0 = turn + Mth.TWO_PI * i / AROUND, a1 = turn + Mth.TWO_PI * (i + 1) / AROUND;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = 2.0F * i / AROUND, u1 = 2.0F * (i + 1) / AROUND;
            put(vc, m, n, c0 * ri, 0.03F, s0 * ri, u0, 0.45F, frontA);
            put(vc, m, n, c0 * ro, 0.03F, s0 * ro, u0, 1.0F, frontA);
            put(vc, m, n, c1 * ro, 0.03F, s1 * ro, u1, 1.0F, frontA);
            put(vc, m, n, c1 * ri, 0.03F, s1 * ri, u1, 0.45F, frontA);
        }
        // THE TONGUES: a blade along the front and one across it, each, leaning a little outward as they rise
        for (int j = 0; j < TONGUES; j++) {
            float hash = Mth.frac(Mth.sin(j * 12.9898F + splash.getId() * 78.233F) * 43758.547F);
            float a = turn + Mth.TWO_PI * (j + 0.5F * hash) / TONGUES;
            float c = Mth.cos(a), s = Mth.sin(a);
            float th = (0.45F + 0.65F * hash) * (0.4F + 0.6F * left) + 0.5F * t;
            float w = (0.16F + 0.08F * hash) * (1.0F - 0.5F * t);
            float rb = r * 0.92F, rt = r * 0.92F + th * 0.25F;
            float bx = c * rb, bz = s * rb, tx = c * rt, tz = s * rt;
            float u = 0.25F * j;
            put(vc, m, n, bx - s * w, 0.0F, bz + c * w, u, 1.0F, frontA);
            put(vc, m, n, bx + s * w, 0.0F, bz - c * w, u + 0.12F, 1.0F, frontA);
            put(vc, m, n, tx + s * w * 0.2F, th, tz - c * w * 0.2F, u + 0.12F, 0.0F, frontA);
            put(vc, m, n, tx - s * w * 0.2F, th, tz + c * w * 0.2F, u, 0.0F, frontA);
            put(vc, m, n, bx - c * w, 0.0F, bz - s * w, u, 1.0F, frontA);
            put(vc, m, n, bx + c * w, 0.0F, bz + s * w, u + 0.12F, 1.0F, frontA);
            put(vc, m, n, tx + c * w * 0.2F, th, tz + s * w * 0.2F, u + 0.12F, 0.0F, frontA);
            put(vc, m, n, tx - c * w * 0.2F, th, tz - s * w * 0.2F, u, 0.0F, frontA);
        }
        super.render(splash, yaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static void put(VertexConsumer vc, Matrix4f m, PoseStack.Pose n, float x, float y, float z,
                            float u, float v, float alpha) {
        vc.addVertex(m, x, y + 0.02F, z).setColor(1.0F, 1.0F, 1.0F, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(n, 0.0F, 1.0F, 0.0F);
    }
}
