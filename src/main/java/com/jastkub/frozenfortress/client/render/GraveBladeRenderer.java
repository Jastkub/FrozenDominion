package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.GraveBladeEntity;
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
 * The blade standing in the floor.
 *
 * <p>TWO CROSSED PLANES, not a billboard. A billboard always faces you, which
 * for a sword means it never shows its edge - and an edge-on moment is most of
 * what makes a blade read as a blade rather than as a poster of one. Crossed
 * quads turn with the world, so walking round it gives you the flat, then the
 * edge, then the flat again.
 *
 * <p>It rises from BELOW the floor rather than growing in place: the quad is
 * drawn at full length and pushed down out of sight, and what the ramp animates
 * is how far up it has been driven. A sword that scales up looks like a sword
 * being inflated; one that slides up looks like one coming through the ground,
 * which is the whole idea.
 */
public class GraveBladeRenderer extends EntityRenderer<GraveBladeEntity> {

    private static final ResourceLocation BLADE =
            FrozenFortress.id("textures/entity/grave_blade.png");

    /**
     * Blocks from hilt to point, and it is meant to be oversized.
     *
     * <p>A sword the size of a sword coming out of the floor is a trap; a sword
     * twice the height of the man who threw it is HIS sword arriving somewhere
     * he is not, which is the whole idea. Three blocks read as scenery - this
     * is the one attack in the phase whose entire job is to be seen from across
     * the room.
     */
    private static final float LENGTH = 5.4F;
    /** Half-width in blocks. */
    private static final float HALF_W = 0.72F;

    public GraveBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(GraveBladeEntity entity) {
        return BLADE;
    }

    @Override
    public void render(GraveBladeEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        float rise = entity.rise();
        if (rise <= 0.001F) {
            return;
        }
        // Eased on the way out so it leaves the ground fast and settles - a
        // linear rise has no impact in it, and this is meant to arrive.
        float eased = 1.0F - (1.0F - rise) * (1.0F - rise);

        poses.pushPose();
        // Buried, then driven up. At rise 0 the point is at floor level and the
        // whole blade is under it; at 1 the hilt is roughly at the floor.
        poses.translate(0.0F, -LENGTH * (1.0F - eased), 0.0F);
        // a slow turn while it stands there, so it is never a static prop
        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(
                (entity.tickCount + partialTick) * 1.6F));

        VertexConsumer buffer =
                buffers.getBuffer(RenderType.entityTranslucentEmissive(BLADE));
        // ghostlier: it is a shadow of the blade, not the blade
        float alpha = Mth.clamp(0.34F + 0.40F * rise, 0.0F, 1.0F);
        // two planes at right angles, each drawn both ways so neither
        // disappears when you cross its back
        for (int i = 0; i < 2; i++) {
            poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(i == 0 ? 0.0F : 90.0F));
            quad(poses.last(), buffer, alpha, false);
            quad(poses.last(), buffer, alpha, true);
        }
        poses.popPose();
    }

    private void quad(PoseStack.Pose pose, VertexConsumer buffer, float a, boolean flip) {
        Matrix4f mat = pose.pose();
        PoseStack.Pose nrm = pose;
        float n = flip ? -1.0F : 1.0F;
        if (flip) {
            vertex(buffer, mat, nrm, -HALF_W, 0.0F, 0.0F, 1.0F, a, n);
            vertex(buffer, mat, nrm, -HALF_W, LENGTH, 0.0F, 0.0F, a, n);
            vertex(buffer, mat, nrm, HALF_W, LENGTH, 1.0F, 0.0F, a, n);
            vertex(buffer, mat, nrm, HALF_W, 0.0F, 1.0F, 1.0F, a, n);
        } else {
            vertex(buffer, mat, nrm, -HALF_W, 0.0F, 0.0F, 1.0F, a, n);
            vertex(buffer, mat, nrm, HALF_W, 0.0F, 1.0F, 1.0F, a, n);
            vertex(buffer, mat, nrm, HALF_W, LENGTH, 1.0F, 0.0F, a, n);
            vertex(buffer, mat, nrm, -HALF_W, LENGTH, 0.0F, 0.0F, a, n);
        }
    }

    private void vertex(VertexConsumer buffer, Matrix4f mat, PoseStack.Pose nrm,
                        float x, float y, float u, float v, float a, float n) {
        buffer.addVertex(mat, x, y, 0.0F)
                .setColor(1.0F, 1.0F, 1.0F, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(nrm, 0.0F, 0.0F, n);
    }
}
