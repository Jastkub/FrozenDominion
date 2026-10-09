package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * THE TAMED MONSTROSITY'S SADDLE (07.10.2026), drawn on the trough of its back while its Saddle is on: a seat of dark
 * leather on a frame plated in Kingsrime, a raised cantle and pommel, girth straps down its flanks and iron stirrups.
 * Raw boxes on the trough bone, as the bomb in its fist is drawn (GolemOrbLayer) - bone space is blocks.
 * Its sheet: tools/gen_golem_saddle.py.
 */
public class GolemSaddleLayer extends GeoRenderLayer<HollowGolemEntity> {

    private static final ResourceLocation TEX = FrozenFortress.id("textures/entity/golem_saddle.png");
    private static final String BONE = "trough";
    /** Each box: centre (x, y, z), half-size (x, y, z), and the strip of the sheet it wears (0 leather, 1 plate, 2 iron). */
    private static final float[][] BOXES = {
            {0.0F, 0.16F, 0.0F, 0.85F, 0.12F, 0.95F, 1},          // the frame
            {0.0F, 0.32F, 0.0F, 0.72F, 0.08F, 0.80F, 0},          // the seat
            {0.0F, 0.52F, 0.82F, 0.72F, 0.24F, 0.10F, 1},         // the cantle
            {0.0F, 0.56F, -0.86F, 0.26F, 0.30F, 0.10F, 1},        // the pommel
            {0.88F, -0.55F, 0.05F, 0.06F, 0.75F, 0.22F, 0},       // the girth, right
            {-0.88F, -0.55F, 0.05F, 0.06F, 0.75F, 0.22F, 0},      // and left
            {0.94F, -1.40F, 0.05F, 0.12F, 0.08F, 0.20F, 2},       // the stirrups
            {-0.94F, -1.40F, 0.05F, 0.12F, 0.08F, 0.20F, 2},
    };

    public GolemSaddleLayer(GeoRenderer<HollowGolemEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poses, HollowGolemEntity golem, GeoBone bone, RenderType type,
                              MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int light, int overlay) {
        if (!BONE.equals(bone.getName()) || !golem.isSaddled()) {
            return;
        }
        poses.pushPose();
        poses.translate(bone.getPivotX() / 16.0F, (bone.getPivotY() + 3.0F) / 16.0F, bone.getPivotZ() / 16.0F);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        Matrix4f m = poses.last().pose();
        Matrix3f n = poses.last().normal();
        for (float[] b : BOXES) {
            box(vc, m, n, b, light);
        }
        poses.popPose();
    }

    private static void box(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] b, int light) {
        float cx = b[0], cy = b[1], cz = b[2], hx = b[3], hy = b[4], hz = b[5];
        float v0 = b[6] / 3.0F, v1 = (b[6] + 1.0F) / 3.0F;
        float[][] faces = {                                     // normal, then the two in-plane axes
                {0, 0, 1, 1, 0, 0, 0, 1, 0}, {0, 0, -1, -1, 0, 0, 0, 1, 0},
                {1, 0, 0, 0, 0, -1, 0, 1, 0}, {-1, 0, 0, 0, 0, 1, 0, 1, 0},
                {0, 1, 0, 1, 0, 0, 0, 0, -1}, {0, -1, 0, 1, 0, 0, 0, 0, 1}};
        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) {
                float u = (i == 0 || i == 3) ? -1 : 1;
                float v = i < 2 ? -1 : 1;
                float x = cx + (f[0] + f[3] * u + f[6] * v) * hx;
                float y = cy + (f[1] + f[4] * u + f[7] * v) * hy;
                float z = cz + (f[2] + f[5] * u + f[8] * v) * hz;
                vc.vertex(m, x, y, z).color(1.0F, 1.0F, 1.0F, 1.0F)
                        .uv(u < 0 ? 0.0F : 1.0F, v < 0 ? v0 : v1)
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, f[0], f[1], f[2]).endVertex();
            }
        }
    }
}
