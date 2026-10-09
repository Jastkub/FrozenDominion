package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.FrostmawEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * THE FROSTMAW'S BREATH, as geometry. It was two kinds of particle a tick. Now it is what the Monstrosity's is
 * (GolemBreathLayer) on a hound's scale: a short gust of things - splinters and chips of ice tumbling out of
 * the jaws, clods of snow-cloud swelling among them - spreading to the five blocks the bite of it reaches
 * (FrostmawEntity.breathe: within 5.5, a cone of about fifty degrees), wavering on its own wind.
 *
 * <p>Drawn from the HEAD bone at the line of its lips, in model space in blocks, going out along -Z.
 */
public class FrostmawBreathLayer extends GeoRenderLayer<FrostmawEntity> {

    private static final ResourceLocation SHEET = FrozenFortress.id("textures/entity/frost_breath.png");

    /** Between the jaws (tools/gen_defenders.py frostmaw_model: the muzzle's front at z -19.8). */
    private static final float MOUTH_Y = 13.9F / 16.0F;
    private static final float MOUTH_Z = -20.2F / 16.0F;

    private static final float LENGTH = 5.2F;
    private static final float R0 = 0.10F;
    private static final float R1 = 1.7F;
    private static final int CHUNKS = 80;
    private static final int PUFFS = 16;
    private static final float TRAVEL = 10.0F;

    public FrostmawBreathLayer(GeoRenderer<FrostmawEntity> renderer) {
        super(renderer);
    }

    private static float hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263 + 0x5bd1e995;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
    }

    @Override
    public void renderForBone(PoseStack poses, FrostmawEntity maw, GeoBone bone, RenderType type,
                              MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!"head".equals(bone.getName())) {
            return;
        }
        float power = maw.breathPower(partialTick);
        if (power <= 0.01F) {
            return;
        }
        float age = maw.tickCount + partialTick;
        float len = LENGTH * maw.breathReach(partialTick);
        poses.pushPose();
        poses.translate(0.0F, MOUTH_Y, MOUTH_Z);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(SHEET));
        float gx = Mth.sin(age * 0.19F) * 0.3F + Mth.sin(age * 0.43F + 2.0F) * 0.12F;
        float gy = Mth.cos(age * 0.17F) * 0.14F;
        for (int i = 0; i < CHUNKS + PUFFS; i++) {
            boolean puff = i >= CHUNKS;
            float f = ((age / (puff ? TRAVEL * 1.5F : TRAVEL)) + hash(i, 1)) % 1.0F;
            if (f * LENGTH > len) {
                continue;                                         // still reaching out
            }
            float z = -LENGTH * (1.0F - (float) Math.pow(1.0F - f, 1.4D));
            float spread = R0 + (R1 - R0) * (float) Math.pow(f, 0.85D);
            float r = spread * (puff ? 0.2F + 0.5F * hash(i, 2) : 0.15F + 0.85F * hash(i, 2));
            float th = hash(i, 3) * Mth.TWO_PI + age * 0.12F * (hash(i, 4) - 0.5F) + Mth.sin(age * 0.25F + i) * 0.5F;
            float x = Mth.cos(th) * r + gx * f;
            float y = Mth.sin(th) * r * 0.7F + gy * f - (puff ? 0.0F : f * f * 0.35F);
            float fadeIn = Mth.clamp(f / 0.1F, 0.0F, 1.0F);
            float alpha = power * fadeIn * (float) Math.pow(1.0F - f, puff ? 1.2D : 0.8D);
            if (alpha <= 0.02F) {
                continue;
            }
            poses.pushPose();
            poses.translate(x, y, z);
            if (puff) {
                float s = (0.22F + 0.6F * f) * (0.7F + 0.6F * hash(i, 5));
                poses.mulPose(Axis.YP.rotationDegrees(age * 3.0F + 360.0F * hash(i, 6)));
                poses.mulPose(Axis.XP.rotationDegrees(age * 2.0F + 360.0F * hash(i, 7)));
                poses.scale(s, s * 0.8F, s);
                cube(poses.last(), vc, hash(i, 8) * 0.6F, 0.95F, 0.98F, 1.0F, alpha * 0.24F);
            } else {
                float s = (0.05F + 0.11F * hash(i, 5)) * (0.8F + 1.0F * f);
                boolean shard = hash(i, 9) < 0.45F;
                poses.mulPose(Axis.XP.rotationDegrees(age * (14.0F + 24.0F * hash(i, 6)) + 360.0F * hash(i, 7)));
                poses.mulPose(Axis.YP.rotationDegrees(age * (9.0F + 16.0F * hash(i, 8)) + 360.0F * hash(i, 4)));
                if (shard) {
                    poses.scale(s * 0.55F, s * 0.55F, s * 2.0F);   // a splinter
                } else {
                    poses.scale(s, s * (0.7F + 0.5F * hash(i, 10)), s);
                }
                boolean snow = hash(i, 11) < 0.4F;
                cube(poses.last(), vc, hash(i, 12) * 0.75F, snow ? 1.0F : 0.78F, snow ? 1.0F : 0.92F, 1.0F,
                        Math.min(1.0F, alpha));
            }
            poses.popPose();
        }
        poses.popPose();
    }

    /** A unit cube about the origin, its faces taking a patch of the frost sheet at `u`. */
    private static void cube(PoseStack.Pose pose, VertexConsumer vc, float u, float r, float g, float b, float a) {
        Matrix4f m = pose.pose();
        PoseStack.Pose n = pose;
        float u0 = u, u1 = u + 0.25F, v0 = (u * 1.7F) % 0.75F, v1 = v0 + 0.25F;
        float h = 0.5F;
        float[][] faces = {
                {-h, -h, h, h, -h, h, h, h, h, -h, h, h, 0, 0, 1},
                {h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h, 0, 0, -1},
                {h, -h, h, h, -h, -h, h, h, -h, h, h, h, 1, 0, 0},
                {-h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, -1, 0, 0},
                {-h, h, h, h, h, h, h, h, -h, -h, h, -h, 0, 1, 0},
                {-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h, 0, -1, 0}};
        float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        for (float[] f : faces) {
            for (int k = 0; k < 4; k++) {
                vc.addVertex(m, f[k * 3], f[k * 3 + 1], f[k * 3 + 2])
                        .setColor(r, g, b, a)
                        .setUv(uv[k][0], uv[k][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(0xF000F0)
                        .setNormal(n, f[12], f[13], f[14]);
            }
        }
    }
}
