package com.jastkub.frozenfortress.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A REAL TAIL: geometry and a texture, not a line of particles.
 *
 * <p>Every projectile in this fight was trailing particles, and particles are
 * the wrong tool for a tail. One puff per tick at any speed worth having is a
 * dotted line; enough of them to close the gaps is a cloud, and neither of
 * those is a streak. What a streak needs is a SHEET - a strip of quads
 * threaded through where the thing has been, turned to face the camera,
 * narrowing and fading toward the old end.
 *
 * <p>HOW IT KNOWS WHERE THE THING HAS BEEN. Nothing on the entity records its
 * own history, so the history is kept here, client-side, keyed by entity id
 * and sampled once per render. That also means it survives being drawn twice
 * in a frame (shaders do this) without laying down the same point twice - the
 * sample is only taken when the game's own tick counter has moved on.
 *
 * <p>Dead entries are swept on a slow cycle rather than on entity removal,
 * because a renderer is never told that anything died.
 */
public final class ProjectileTrail {

    private ProjectileTrail() {
    }

    /** One remembered position, in world space, and how old it is. */
    private record Point(double x, double y, double z, long at) {
    }

    private static final Map<Integer, Deque<Point>> HISTORY = new HashMap<>();
    private static long lastSweep;

    /**
     * Draws the tail for one entity and takes this frame's sample.
     *
     * @param width  half-width of the strip at the head, in blocks
     * @param life   how many ticks of history to keep
     */
    public static void draw(Entity entity, PoseStack poseStack, MultiBufferSource buffers,
                            ResourceLocation texture, float partialTick,
                            float width, int life,
                            float red, float green, float blue) {
        long now = entity.level().getGameTime();
        sweep(now);

        Deque<Point> path = HISTORY.computeIfAbsent(entity.getId(), id -> new ArrayDeque<>());
        // one sample per game tick, whatever the frame rate is doing
        if (path.isEmpty() || path.peekLast().at() != now) {
            path.addLast(new Point(entity.getX(), entity.getY(), entity.getZ(), now));
        }
        while (!path.isEmpty() && now - path.peekFirst().at() > life) {
            path.removeFirst();
        }
        if (path.size() < 2) {
            return;
        }

        // The strip is built in the ENTITY's local frame: the pose stack is
        // already translated to its interpolated position, so every remembered
        // point is drawn relative to where it is now. Anything else fights the
        // interpolation and the tail swims.
        double ox = Mth(entity.xOld, entity.getX(), partialTick);
        double oy = Mth(entity.yOld, entity.getY(), partialTick);
        double oz = Mth(entity.zOld, entity.getZ(), partialTick);

        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        List<Point> pts = new ArrayList<>(path);

        poseStack.pushPose();
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));

        for (int i = 0; i < pts.size() - 1; i++) {
            Point a = pts.get(i);
            Point b = pts.get(i + 1);
            float fa = (float) (a.at() - pts.get(0).at() + 1) / (pts.size());
            float fb = (float) (b.at() - pts.get(0).at() + 1) / (pts.size());

            // each segment is turned edge-on to the camera, so the sheet is
            // always presented flat however the projectile is travelling
            Vec3 along = new Vec3(b.x() - a.x(), b.y() - a.y(), b.z() - a.z());
            if (along.lengthSqr() < 1.0E-8D) {
                continue;
            }
            Vec3 mid = new Vec3((a.x() + b.x()) * 0.5D, (a.y() + b.y()) * 0.5D,
                                (a.z() + b.z()) * 0.5D);
            Vec3 toEye = eye.subtract(mid);
            Vec3 side = along.cross(toEye);
            if (side.lengthSqr() < 1.0E-8D) {
                continue;
            }
            side = side.normalize();

            float wa = width * fa * fa;      // narrows toward the old end, fast
            float wb = width * fb * fb;
            emit(vc, m, a, side, wa, (float) ox, (float) oy, (float) oz,
                 0.0F, fa, red, green, blue);
            emit(vc, m, b, side, wb, (float) ox, (float) oy, (float) oz,
                 1.0F, fb, red, green, blue);
            emit(vc, m, b, side, -wb, (float) ox, (float) oy, (float) oz,
                 1.0F, fb, red, green, blue);
            emit(vc, m, a, side, -wa, (float) ox, (float) oy, (float) oz,
                 0.0F, fa, red, green, blue);
        }
        poseStack.popPose();
    }

    private static void emit(VertexConsumer vc, Matrix4f m, Point p, Vec3 side, float w,
                             float ox, float oy, float oz,
                             float u, float fade, float r, float g, float b) {
        float x = (float) (p.x() - ox) + (float) side.x * w;
        float y = (float) (p.y() - oy) + (float) side.y * w;
        float z = (float) (p.z() - oz) + (float) side.z * w;
        vc.addVertex(m, x, y, z)
                .setColor(r, g, b, fade * fade)
                .setUv(u, w > 0 ? 0.0F : 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)      // it lights itself
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static double Mth(double was, double now, float partial) {
        return was + (now - was) * partial;
    }

    /**
     * Forgets the paths of things that are gone.
     *
     * <p>A renderer is never told that an entity died, so without this the map
     * grows for as long as the world is loaded - and a boss fight that fires
     * four hundred crystals would leave four hundred dead deques behind it.
     */
    private static void sweep(long now) {
        if (now - lastSweep < 100L) {
            return;
        }
        lastSweep = now;
        HISTORY.entrySet().removeIf(e -> {
            Deque<Point> path = e.getValue();
            return path.isEmpty() || now - path.peekLast().at() > 40L;
        });
    }
}
