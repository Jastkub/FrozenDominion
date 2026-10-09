package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.ShadowShardEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * A bright shard with a dark wake behind it.
 *
 * <p>The wake is the point of the whole thing, and it is drawn from the path
 * the client recorded rather than from a particle emitter: particles left
 * behind a fast projectile smear into a dotted line, while a strip through the
 * actual positions stays a continuous tail no matter how fast it is going.
 * Both the head and the tail are camera-facing quads, so there is no wrong
 * angle to look at it from.
 */
public class ShadowShardRenderer extends EntityRenderer<ShadowShardEntity> {

    private static final ResourceLocation SHARD =
            FrozenFortress.id("textures/entity/shadow_shard.png");
    private static final ResourceLocation TAIL =
            FrozenFortress.id("textures/entity/shadow_tail.png");

    private static final float HEAD_SIZE = 0.44F;
    private static final float TAIL_SIZE = 0.40F;

    public ShadowShardRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowShardEntity entity) {
        return SHARD;
    }

    @Override
    public void render(ShadowShardEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        Vec3 here = new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));

        // --- the wake, oldest and smallest last
        List<Vec3> trail = entity.trail();
        VertexConsumer tail = buffers.getBuffer(RenderType.entityTranslucent(TAIL));
        for (int i = 0; i < trail.size(); i++) {
            float t = i / (float) ShadowShardEntity.TRAIL_LENGTH;
            Vec3 offset = trail.get(i).subtract(here);
            poses.pushPose();
            poses.translate(offset.x, offset.y, offset.z);
            poses.mulPose(this.entityRenderDispatcher.cameraOrientation());
            // it thickens just behind the head before it thins out, which is
            // what makes a tail look like it is being dragged
            float grow = 1.0F + 0.5F * Mth.sin(Mth.clamp(t * 3.0F, 0.0F, 1.0F) * Mth.PI);
            quad(poses.last(), tail, TAIL_SIZE * grow * (1.0F - t * 0.85F),
                    0.09F, 0.11F, 0.22F, 0.72F * (1.0F - t) * (1.0F - t));
            poses.popPose();
        }

        // --- the shard, full bright so it stays legible against its own wake
        float spin = (entity.tickCount + partialTick) * 26.0F;
        poses.pushPose();
        poses.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(spin));
        quad(poses.last(), buffers.getBuffer(RenderType.entityTranslucentEmissive(SHARD)),
                HEAD_SIZE, 0.82F, 0.95F, 1.0F, 1.0F);
        poses.popPose();
    }

    private void quad(PoseStack.Pose pose, VertexConsumer buffer, float size,
                      float r, float g, float b, float a) {
        Matrix4f mat = pose.pose();
        PoseStack.Pose nrm = pose;
        vertex(buffer, mat, nrm, -size, -size, 0.0F, 1.0F, r, g, b, a);
        vertex(buffer, mat, nrm, size, -size, 1.0F, 1.0F, r, g, b, a);
        vertex(buffer, mat, nrm, size, size, 1.0F, 0.0F, r, g, b, a);
        vertex(buffer, mat, nrm, -size, size, 0.0F, 0.0F, r, g, b, a);
    }

    private void vertex(VertexConsumer buffer, Matrix4f mat, PoseStack.Pose nrm,
                        float x, float y, float u, float v,
                        float r, float g, float b, float a) {
        buffer.addVertex(mat, x, y, 0.0F)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(nrm, 0.0F, 1.0F, 0.0F);
    }
}
