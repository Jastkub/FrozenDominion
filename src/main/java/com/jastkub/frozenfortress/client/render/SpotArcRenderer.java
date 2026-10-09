package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.SpotArcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Real filaments, struck between the fog and the point the stone will come out.
 *
 * <p>THE JAGGEDNESS IS IN THE GEOMETRY. A lightning texture with kinks painted
 * into it repeats those kinks on every segment of every bolt, which is how
 * these effects end up looking like chain-link fencing - so the sheet here is
 * a plain filament and the shape is generated: each arc is a walk from the
 * floor to the head with a random sideways nudge at every step, and the nudges
 * come from a seed that changes every few ticks so the whole set is re-struck
 * rather than animated.
 *
 * <p>Seeded off the entity's id and the world clock, so it costs nothing on
 * the wire and every client sees the same flicker at the same moment.
 */
public class SpotArcRenderer extends EntityRenderer<SpotArcEntity> {

    private static final ResourceLocation SHEET =
            FrozenFortress.id("textures/entity/ice_arc.png");

    /** How often the whole set is re-struck, in ticks. */
    private static final int RESTRIKE = 2;
    private static final int STEPS = 7;

    public SpotArcRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(SpotArcEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float charge = entity.charge();
        float reach = entity.reach();
        long now = entity.level().getGameTime();
        // more of them, and brighter, as it builds
        int count = 2 + (int) (charge * 5.0F);
        Random rng = new Random((entity.getId() * 8191L) ^ (now / RESTRIKE));
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 here = entity.position();

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(SHEET));
        poseStack.pushPose();
        Matrix4f m = poseStack.last().pose();

        for (int b = 0; b < count; b++) {
            // each filament starts somewhere out in the fog and converges on
            // the point above the spot - they are being DRAWN IN, which is the
            // read the attack wants: the floor is feeding something
            double a = rng.nextDouble() * Math.PI * 2.0D;
            double r = 0.6D + rng.nextDouble() * 1.7D * (1.2D - charge);
            Vec3 from = new Vec3(Math.cos(a) * r, 0.05D, Math.sin(a) * r);
            Vec3 to = new Vec3(0.0D, reach * (0.55D + 0.45D * charge), 0.0D);

            Vec3 prev = from;
            for (int s = 1; s <= STEPS; s++) {
                double f = s / (double) STEPS;
                Vec3 straight = from.add(to.subtract(from).scale(f));
                // the kink: biggest in the middle, nothing at either end, so
                // the filament is anchored at both tips and wild between them
                double wobble = Math.sin(f * Math.PI) * (0.30D + 0.35D * charge);
                Vec3 point = s == STEPS ? to : straight.add(
                        (rng.nextDouble() - 0.5D) * wobble,
                        (rng.nextDouble() - 0.5D) * wobble * 0.5D,
                        (rng.nextDouble() - 0.5D) * wobble);
                segment(vc, m, prev, point, here, eye, 0.035F + 0.045F * charge,
                        0.35F + 0.65F * charge);
                prev = point;
            }
        }
        poseStack.popPose();
    }

    /** One length of filament, turned edge-on to the camera. */
    private static void segment(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b,
                                Vec3 origin, Vec3 eye, float width, float alpha) {
        Vec3 along = b.subtract(a);
        if (along.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 mid = a.add(b).scale(0.5D).add(origin);
        Vec3 side = along.cross(eye.subtract(mid));
        if (side.lengthSqr() < 1.0E-8D) {
            return;
        }
        side = side.normalize().scale(width);
        vert(vc, m, a.subtract(side), 0.0F, 0.0F, alpha);
        vert(vc, m, b.subtract(side), 1.0F, 0.0F, alpha);
        vert(vc, m, b.add(side), 1.0F, 1.0F, alpha);
        vert(vc, m, a.add(side), 0.0F, 1.0F, alpha);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Vec3 p,
                             float u, float v, float alpha) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color(0.86F, 0.96F, 1.0F, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(SpotArcEntity entity) {
        return SHEET;
    }
}
