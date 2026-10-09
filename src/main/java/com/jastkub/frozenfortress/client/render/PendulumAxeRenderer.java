package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.PendulumAxeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The swinging axe (PendulumAxeBlockEntity): its plate in the vault, a rod of dark iron down to the blade, and the
 * blade - a socket and two crescent bits of frosted iron, rimed along their edges - swung on the world's clock exactly
 * as the server swings it. Raw boxes, three strips of its sheet (tools/gen_pendulum_axe.py): 0 dark iron, 1 frosted
 * iron, 2 ice.
 */
public class PendulumAxeRenderer implements BlockEntityRenderer<PendulumAxeBlockEntity> {

    private static final ResourceLocation TEX = FrozenFortress.id("textures/entity/pendulum_axe.png");
    /** The head, along z (its swing) about the blade's middle: centre (x, y, z), half-size (x, y, z), strip. */
    private static final float[][] HEAD = {
            {0.0F, 0.0F, 0.0F, 0.2F, 0.34F, 0.2F, 0},              // the socket
            {0.0F, 0.04F, 0.34F, 0.07F, 0.36F, 0.16F, 1},          // the bit, inner
            {0.0F, 0.0F, 0.58F, 0.07F, 0.5F, 0.1F, 1},             //          its swell
            {0.0F, 0.0F, 0.74F, 0.08F, 0.58F, 0.06F, 2},           //          its rimed edge
            {0.0F, 0.04F, -0.34F, 0.07F, 0.36F, 0.16F, 1},         // and the other
            {0.0F, 0.0F, -0.58F, 0.07F, 0.5F, 0.1F, 1},
            {0.0F, 0.0F, -0.74F, 0.08F, 0.58F, 0.06F, 2},
            {0.0F, -0.42F, 0.0F, 0.1F, 0.14F, 0.1F, 0},            // the spike under the socket
    };

    public PendulumAxeRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(PendulumAxeBlockEntity axe, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int light, int overlay) {
        if (axe.getLevel() == null) {
            return;
        }
        double a = axe.angle(axe.getLevel().getGameTime() + partialTick);
        float len = axe.length();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        // the plate, unswung
        box(vc, poses.last().pose(), poses.last().normal(), new float[]{0.0F, 0.38F, 0.0F, 0.3F, 0.12F, 0.3F, 0}, light);
        if (axe.alongX()) {
            poses.mulPose(Axis.YP.rotationDegrees(90.0F));       // (its z is the world's x)
        }
        poses.mulPose(Axis.XP.rotation((float) -a));
        Matrix4f m = poses.last().pose();
        Matrix3f n = poses.last().normal();
        box(vc, m, n, new float[]{0.0F, -len / 2.0F, 0.0F, 0.06F, len / 2.0F, 0.06F, 0}, light);     // the rod
        for (float[] b : HEAD) {
            float[] c = b.clone();
            c[1] -= len;
            box(vc, m, n, c, light);
        }
        poses.popPose();
    }

    private static void box(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] b, int light) {
        float cx = b[0], cy = b[1], cz = b[2], hx = b[3], hy = b[4], hz = b[5];
        float v0 = b[6] / 3.0F, v1 = (b[6] + 1.0F) / 3.0F;
        float[][] faces = {
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

    @Override
    public boolean shouldRenderOffScreen(PendulumAxeBlockEntity axe) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
