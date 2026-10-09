package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.projectile.SovereignBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the player's beam as real geometry - two nested square tubes, a wide
 * translucent mantle around a narrow full-bright core - built from the
 * wielder's own view vector so it points exactly where they are looking, in
 * three dimensions rather than Velkhar's flat sweep.
 */
public class SovereignBeamRenderer extends EntityRenderer<SovereignBeamEntity> {

    private static final ResourceLocation BEAM_OUTER =
            FrozenFortress.id("textures/entity/velkhar_beam_outer.png");
    private static final ResourceLocation BEAM_INNER =
            FrozenFortress.id("textures/entity/velkhar_beam_inner.png");

    private static final float OUTER_RADIUS = 0.55F;
    private static final float INNER_RADIUS = 0.20F;
    private static final float TILE_LENGTH = 4.0F;

    public SovereignBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SovereignBeamEntity entity) {
        return BEAM_OUTER;
    }

    @Override
    public void render(SovereignBeamEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        float open = entity.getOpenness(partialTick);
        float length = entity.getLength();
        Player owner = entity.getOwner();
        if (open <= 0.001F || length <= 0.1F || owner == null) {
            return;
        }

        // The entity's own position lags a tick behind the camera; anchoring to
        // the owner's interpolated eye keeps the beam welded to the view.
        Vec3 eye = owner.getEyePosition(partialTick);
        double sx = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double sy = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double sz = Mth.lerp(partialTick, entity.zOld, entity.getZ());

        Vec3 dir = owner.getViewVector(partialTick).normalize();
        Vec3 hint = Math.abs(dir.y) > 0.995D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = dir.cross(hint).normalize();
        Vec3 up = right.cross(dir).normalize();

        float time = entity.tickCount + partialTick;
        float scroll = -time * 0.3F;
        float vLen = length / TILE_LENGTH;
        float pulse = open * (1.0F + 0.14F * Mth.sin(time * 1.1F));

        poseStack.pushPose();
        poseStack.translate(eye.x - sx, eye.y - sy, eye.z - sz);
        PoseStack.Pose pose = poseStack.last();

        drawTube(pose, bufferSource.getBuffer(RenderType.entityTranslucent(BEAM_OUTER)),
                dir, right, up, length, OUTER_RADIUS * pulse,
                scroll, vLen, 0.45F, 0.78F, 1.0F, 0.55F * open);
        drawTube(pose, bufferSource.getBuffer(RenderType.entityTranslucentEmissive(BEAM_INNER)),
                dir, right, up, length, INNER_RADIUS * pulse,
                scroll * 1.7F, vLen * 2.0F, 0.85F, 0.97F, 1.0F, 0.95F * open);

        poseStack.popPose();
    }

    private void drawTube(PoseStack.Pose pose, VertexConsumer buffer,
                          Vec3 dir, Vec3 right, Vec3 up,
                          float length, float radius,
                          float vOffset, float vLength,
                          float r, float g, float b, float a) {
        Vec3 end = dir.scale(length);
        // the square's four corners, expressed in the beam's own basis
        Vec3[] corners = {
                right.scale(radius).add(up.scale(radius)),
                right.scale(radius).subtract(up.scale(radius)),
                right.scale(-radius).subtract(up.scale(radius)),
                right.scale(-radius).add(up.scale(radius)),
        };
        for (int i = 0; i < 4; i++) {
            Vec3 c0 = corners[i];
            Vec3 c1 = corners[(i + 1) % 4];
            // both windings, so the tube is solid seen from inside as well
            quad(pose, buffer, c0, c1, end, vOffset, vLength, r, g, b, a);
            quad(pose, buffer, c1, c0, end, vOffset, vLength, r, g, b, a);
        }
    }

    private void quad(PoseStack.Pose pose, VertexConsumer buffer,
                      Vec3 c0, Vec3 c1, Vec3 end,
                      float v0, float vLen, float r, float g, float b, float a) {
        Matrix4f mat = pose.pose();
        Matrix3f nrm = pose.normal();
        vertex(buffer, mat, nrm, c0, 0.0F, v0, r, g, b, a);
        vertex(buffer, mat, nrm, c1, 1.0F, v0, r, g, b, a);
        vertex(buffer, mat, nrm, c1.add(end), 1.0F, v0 + vLen, r, g, b, a);
        vertex(buffer, mat, nrm, c0.add(end), 0.0F, v0 + vLen, r, g, b, a);
    }

    private void vertex(VertexConsumer buffer, Matrix4f mat, Matrix3f nrm, Vec3 p,
                        float u, float v, float r, float g, float b, float a) {
        buffer.vertex(mat, (float) p.x, (float) p.y, (float) p.z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(nrm, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
