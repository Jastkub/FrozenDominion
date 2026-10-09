package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * THE FROST BREATH, as a gust of geometry.
 *
 * <p>The first breath was seven particles a tick - a haze with no edge; the second a bundle of sheets
 * - a cone, too perfect. This one is a STREAM OF THINGS: ice chunks and splinters, each its own little
 * solid, tumbling as they go, thrown out of the maw fast and slowing as they spread; and through them
 * soft clods of snow-cloud swelling and thinning. No two take the same line - some go down the middle,
 * some out at the edges, all of them swirl - and the whole of it wavers on the wind of itself, bending
 * this way and that, the heavy ice dropping a little as it flies. Where its edge is you can still read
 * (it fades out at the cone the server strikes in), but it is a gust, not a shape.
 *
 * <p>Drawn from the HEAD bone (not the jaw - it drops fifty degrees open), in model space in blocks, the
 * breath going out along -Z.
 */
public class GolemBreathLayer extends GeoRenderLayer<HollowGolemEntity> {

    private static final ResourceLocation SHEET =
            FrozenFortress.id("textures/entity/frost_breath.png");

    /** The lip line, measured off the model. */
    private static final float MOUTH_Y = 56.8F / 16.0F;
    private static final float MOUTH_Z = -41.5F / 16.0F;

    /** A touch short of the server's sixteen, so nothing is drawn that cannot hit. */
    private static final float LENGTH = 15.4F;
    /** Radius at the maw and at the far end. */
    private static final float R0 = 0.32F;
    private static final float R1 = 6.0F;

    private static final int CHUNKS = 210;
    private static final int PUFFS = 38;
    /** Ticks a chunk takes from the maw to the end of the breath. */
    private static final float TRAVEL = 22.0F;

    public GolemBreathLayer(GeoRenderer<HollowGolemEntity> renderer) {
        super(renderer);
    }

    private static float hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263 + 0x5bd1e995;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
    }

    @Override
    public void renderForBone(PoseStack poses, HollowGolemEntity golem, GeoBone bone,
                              RenderType type, MultiBufferSource buffers,
                              VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!"head".equals(bone.getName())) {
            return;
        }
        float power = golem.breathPower(partialTick);
        if (power <= 0.01F) {
            return;
        }
        float reach = golem.breathReach(partialTick);
        float age = golem.tickCount + partialTick;
        float len = LENGTH * reach;

        poses.pushPose();
        poses.translate(0.0F, MOUTH_Y, MOUTH_Z);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(SHEET));
        // the gust's own sway: the whole stream bends on its wind
        float gx = Mth.sin(age * 0.13F) * 0.9F + Mth.sin(age * 0.31F + 2.0F) * 0.35F;
        float gy = Mth.cos(age * 0.11F) * 0.4F;
        for (int i = 0; i < CHUNKS + PUFFS; i++) {
            boolean puff = i >= CHUNKS;
            float f = ((age / (puff ? TRAVEL * 1.6F : TRAVEL)) + hash(i, 1)) % 1.0F;
            if (f * LENGTH > len) {
                continue;                                         // still reaching out
            }
            float z = -LENGTH * (1.0F - (float) Math.pow(1.0F - f, 1.5D));
            float spread = R0 + (R1 * 1.15F - R0) * (float) Math.pow(f, 0.85D);
            float r = spread * (puff ? 0.2F + 0.5F * hash(i, 2) : 0.2F + 0.8F * hash(i, 2));
            float th = hash(i, 3) * Mth.TWO_PI + age * 0.1F * (hash(i, 4) - 0.5F) + Mth.sin(age * 0.21F + i) * 0.5F;
            float x = Mth.cos(th) * r + gx * f;
            float y = Mth.sin(th) * r * 0.75F + gy * f - (puff ? 0.0F : f * f * 0.9F);
            float fadeIn = Mth.clamp(f / 0.08F, 0.0F, 1.0F);
            float alpha = power * fadeIn * (float) Math.pow(1.0F - f, puff ? 1.2D : 0.8D);
            if (alpha <= 0.02F) {
                continue;
            }
            poses.pushPose();
            poses.translate(x, y, z);
            if (puff) {
                float s = (0.6F + 1.6F * f) * (0.7F + 0.6F * hash(i, 5));
                poses.mulPose(Axis.YP.rotationDegrees(age * 3.0F + 360.0F * hash(i, 6)));
                poses.mulPose(Axis.XP.rotationDegrees(age * 2.0F + 360.0F * hash(i, 7)));
                poses.scale(s, s * 0.8F, s);
                cube(poses.last(), vc, hash(i, 8) * 0.6F, 0.95F, 0.98F, 1.0F, alpha * 0.22F);
            } else {
                float s = (0.1F + 0.26F * hash(i, 5)) * (0.8F + 1.1F * f);
                boolean shard = hash(i, 9) < 0.45F;
                poses.mulPose(Axis.XP.rotationDegrees(age * (14.0F + 24.0F * hash(i, 6)) + 360.0F * hash(i, 7)));
                poses.mulPose(Axis.YP.rotationDegrees(age * (9.0F + 16.0F * hash(i, 8)) + 360.0F * hash(i, 4)));
                if (shard) {
                    poses.scale(s * 0.55F, s * 0.55F, s * 2.0F);       // a splinter
                } else {
                    poses.scale(s, s * (0.7F + 0.5F * hash(i, 10)), s);
                }
                boolean snow = hash(i, 11) < 0.4F;
                cube(poses.last(), vc, hash(i, 12) * 0.75F, snow ? 1.0F : 0.78F, snow ? 1.0F : 0.92F, 1.0F,
                        Math.min(1.0F, alpha * (snow ? 0.9F : 1.0F)));
            }
            poses.popPose();
        }
        poses.popPose();
    }

    /** A unit cube about the origin, its faces taking a patch of the frost sheet at `u`. */
    private static void cube(PoseStack.Pose pose, VertexConsumer vc, float u, float r, float g, float b, float a) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
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
                vc.vertex(m, f[k * 3], f[k * 3 + 1], f[k * 3 + 2])
                        .color(r, g, b, a)
                        .uv(uv[k][0], uv[k][1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(0xF000F0)
                        .normal(n, f[12], f[13], f[14])
                        .endVertex();
            }
        }
    }
}
