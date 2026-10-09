package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.effect.FloorSigilEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Draws the mark flat on the floor.
 *
 * <p>One quad, lying face-up a fraction above the ground, spinning slowly and
 * emissive. Three details are what stop it looking like a sticker:
 *
 * <ul>
 *   <li>It is drawn from BOTH sides, so standing under a ledge and looking up
 *       at it does not make it vanish.</li>
 *   <li>It fades in over its first few ticks and out over its last, and the
 *       fade is on ALPHA rather than on scale - a mark that grows into place
 *       reads as being drawn, which is right for the sigil and wrong for the
 *       rift, and both share this renderer.</li>
 *   <li>The rift turns roughly three times faster than the sigil and in the
 *       opposite direction. A vortex that idles at the same rate as a scribed
 *       dial is not a vortex.</li>
 * </ul>
 */
public class FloorSigilRenderer extends EntityRenderer<FloorSigilEntity> {

    private static final ResourceLocation SIGIL =
            FrozenFortress.id("textures/entity/velkhar_sigil.png");
    private static final ResourceLocation RIFT =
            FrozenFortress.id("textures/entity/velkhar_rift_portal.png");

    public FloorSigilRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(FloorSigilEntity entity) {
        return entity.getVariant() == FloorSigilEntity.RIFT ? RIFT : SIGIL;
    }

    @Override
    public void render(FloorSigilEntity entity, float yaw, float partialTick,
                       PoseStack poses, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        int life = entity.getLifetime();
        boolean rift = entity.getVariant() == FloorSigilEntity.RIFT;

        float alpha = Mth.clamp(age / 8.0F, 0.0F, 1.0F)
                * Mth.clamp((life - age) / 10.0F, 0.0F, 1.0F);
        if (alpha <= 0.01F) {
            return;
        }
        // it breathes, so a mark that sits still for four seconds is not still
        alpha *= rift ? 0.86F + 0.14F * Mth.sin(age * 0.4F)
                : 0.72F + 0.28F * Mth.abs(Mth.sin(age * 0.22F));

        float radius = entity.getRadius();
        float spin = rift ? -age * 3.6F : age * 1.1F;

        poses.pushPose();
        // A hair off the floor. Any less and it fights the block it lies on.
        poses.translate(0.0D, 0.055D, 0.0D);
        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
        PoseStack.Pose pose = poses.last();
        RenderType type = RenderType.entityTranslucentEmissive(getTextureLocation(entity));
        VertexConsumer buffer = buffers.getBuffer(type);

        float r = rift ? 0.62F : 0.66F;
        float g = rift ? 0.90F : 0.94F;
        float b = 1.0F;
        quad(pose, buffer, radius, r, g, b, alpha, false);
        quad(pose, buffer, radius, r, g, b, alpha * 0.7F, true);
        poses.popPose();
    }

    /** One face-up square. `flip` emits the underside winding. */
    private void quad(PoseStack.Pose pose, VertexConsumer buffer, float radius,
                      float r, float g, float b, float a, boolean flip) {
        float[][] corners = {
                {-radius, -radius, 0.0F, 0.0F}, {-radius, radius, 0.0F, 1.0F},
                {radius, radius, 1.0F, 1.0F}, {radius, -radius, 1.0F, 0.0F},
        };
        for (int i = 0; i < 4; i++) {
            float[] c = corners[flip ? 3 - i : i];
            vertex(buffer, pose.pose(), pose, c[0], c[1], c[2], c[3], r, g, b, a,
                    flip ? -1.0F : 1.0F);
        }
    }

    private void vertex(VertexConsumer buffer, Matrix4f mat, PoseStack.Pose nrm,
                        float x, float z, float u, float v,
                        float r, float g, float b, float a, float ny) {
        buffer.addVertex(mat, x, 0.0F, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(nrm, 0.0F, ny, 0.0F);
    }
}
