package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.IceTowerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The wind that goes round the spire while it comes out of the ground.
 *
 * <p>GEOMETRY, NOT PARTICLES, and that was the ask. A few hundred flakes
 * thrown outward is the cheapest way to say "something is happening here" and
 * it says nothing else: no direction, no speed, no shape. What a gale looks
 * like from outside is BANDS - long thin sheets of driven snow wrapping the
 * thing they are blowing around, and a band is a ribbon of quads.
 *
 * <p>Three of them, climbing as they turn, so the whole helix reads as air
 * being dragged UP the column by whatever is pushing the column up. They wind
 * the opposite way to the spire's own rise for the same reason a drill and its
 * swarf turn opposite ways - two things turning together read as one object,
 * two things turning against each other read as one acting on the other.
 *
 * <p>The band is wide where it is fast and narrow where it is not, which is
 * the only cue that makes a flat ribbon read as moving rather than as a
 * painted stripe, and every one of them fades out at both ends so nothing has
 * a visible beginning.
 */
public class TowerWindLayer {

    private static final ResourceLocation SHEET =
            FrozenFortress.id("textures/entity/ice_trail.png");

    /** How many bands, how many segments each, and how far out they ride. */
    private static final int BANDS = 3;
    private static final int STEPS = 26;
    private static final double RADIUS = 2.15D;

    /** Turns one band makes over the full height of the spire. */
    private static final double TWIST = 1.35D;

    private TowerWindLayer() {
    }

    /**
     * @param risen how much of the spire is out of the ground, 0 to 1
     */
    public static void draw(IceTowerEntity tower, PoseStack poseStack,
                            MultiBufferSource buffers, float partialTick,
                            float risen, float height) {
        // ---- IT BLOWS HARDEST WHILE THE GROUND IS OPENING, and is gone by
        //      the time he is standing up there. A wind that keeps going after
        //      the event is weather; this is the event.
        float gust = risen < 0.86F
                ? Mth.clamp(risen / 0.22F, 0.0F, 1.0F)
                : Mth.clamp((1.0F - risen) / 0.14F, 0.0F, 1.0F);
        if (gust <= 0.01F) {
            return;
        }

        float age = tower.tickCount + partialTick;
        double top = IceTowerEntity.TOP * risen * height;

        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(SHEET));
        poseStack.pushPose();
        // the whole helix turns, so the bands travel round him rather than
        // sitting on the column like a barber's pole
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 7.2F));
        PoseStack.Pose pose = poseStack.last();

        for (int band = 0; band < BANDS; band++) {
            double phase = Math.PI * 2.0D * band / BANDS;
            for (int i = 0; i < STEPS; i++) {
                double t0 = (double) i / STEPS;
                double t1 = (double) (i + 1) / STEPS;
                // ---- FADED AT BOTH ENDS. A ribbon that starts and stops
                //      abruptly reads as a ribbon; one that arrives out of
                //      nothing reads as air.
                float a0 = taper(t0) * gust * 0.55F;
                float a1 = taper(t1) * gust * 0.55F;
                if (a0 <= 0.004F && a1 <= 0.004F) {
                    continue;
                }
                // wider in the middle of its run, which is where it is fastest
                double w0 = 0.16D + 0.34D * Math.sin(t0 * Math.PI);
                double w1 = 0.16D + 0.34D * Math.sin(t1 * Math.PI);
                segment(pose, buffer, phase, t0, t1, top, w0, w1, a0, a1, age);
            }
        }
        poseStack.popPose();
    }

    /** 0 at both ends of the band, 1 across its middle. */
    private static float taper(double t) {
        return (float) Math.sin(Mth.clamp(t, 0.0D, 1.0D) * Math.PI);
    }

    private static void segment(PoseStack.Pose pose, VertexConsumer buffer,
                                double phase, double t0, double t1, double top,
                                double w0, double w1, float a0, float a1,
                                float age) {
        double an0 = phase + t0 * Math.PI * 2.0D * TWIST;
        double an1 = phase + t1 * Math.PI * 2.0D * TWIST;
        // the radius breathes a little along the band, so it is not a perfect
        // cylinder - a perfect cylinder is a machine part, not moving air
        double r0 = RADIUS + 0.28D * Math.sin(t0 * 9.0D + age * 0.25D);
        double r1 = RADIUS + 0.28D * Math.sin(t1 * 9.0D + age * 0.25D);
        double x0 = Math.cos(an0) * r0;
        double z0 = Math.sin(an0) * r0;
        double x1 = Math.cos(an1) * r1;
        double z1 = Math.sin(an1) * r1;
        double y0 = top * t0;
        double y1 = top * t1;

        // drawn from both sides: a one-sided ribbon vanishes as it comes round
        // the back of the column, which is exactly half the time
        quad(pose, buffer, x0, y0 - w0, z0, x0, y0 + w0, z0,
             x1, y1 + w1, z1, x1, y1 - w1, z1, a0, a1);
        quad(pose, buffer, x1, y1 - w1, z1, x1, y1 + w1, z1,
             x0, y0 + w0, z0, x0, y0 - w0, z0, a1, a0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             double cx, double cy, double cz,
                             double dx, double dy, double dz,
                             float a0, float a1) {
        vertex(pose, buffer, ax, ay, az, 0.0F, 0.0F, a0);
        vertex(pose, buffer, bx, by, bz, 0.0F, 1.0F, a0);
        vertex(pose, buffer, cx, cy, cz, 1.0F, 1.0F, a1);
        vertex(pose, buffer, dx, dy, dz, 1.0F, 0.0F, a1);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer,
                               double x, double y, double z,
                               float u, float v, float alpha) {
        Matrix4f mat = pose.pose();
        PoseStack.Pose nrm = pose;
        buffer.addVertex(mat, (float) x, (float) y, (float) z)
                .setColor(0.82F, 0.93F, 1.0F, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(nrm, 0.0F, 1.0F, 0.0F);
    }
}
