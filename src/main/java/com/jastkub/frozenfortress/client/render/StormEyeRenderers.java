package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.StormEyeAnchorEntity;
import com.jastkub.frozenfortress.entity.boss.StormEyeArena;
import com.jastkub.frozenfortress.entity.boss.StormEyeBoltEntity;
import com.jastkub.frozenfortress.entity.boss.StormEyeGaleEntity;
import com.jastkub.frozenfortress.entity.boss.StormEyeGustEntity;
import com.jastkub.frozenfortress.entity.boss.StormEyeOrbEntity;
import com.jastkub.frozenfortress.entity.boss.StormEyeRuneEntity;
import com.jastkub.frozenfortress.entity.boss.StormEyeVortexEntity;
import com.jastkub.frozenfortress.entity.boss.StormEyeWallEntity;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * EVERY BODY OF THE EYE OF THE STORM, as geometry. The sheets
 * are painted by tools/gen_storm_eye.py; the shapes are built here, fresh each frame, at whatever size the moment
 * needs - the same way ShadePulseRenderer and FrostWaveRenderer build theirs.
 *
 * <ul>
 *   <li>{@link Vortex} - the funnel under the floes (spiral-sheared cloud shells, veins of light, the eye, lightning
 *       inside it) and the column in the throne room;</li>
 *   <li>{@link Wall} - the ring of electrified cloud (three billowing shells, the top curling in, arcs crawling);</li>
 *   <li>{@link Rune} - Znak Gromu: the sigil on the floe, the cloud knot over it, the bolt;</li>
 *   <li>{@link Gale} - Wichura: the wall of wind with its one gap;</li>
 *   <li>{@link Gust} - the rescue updraft and the mirrors' squall;</li>
 *   <li>{@link Bolt} - a single arc; {@link Orb} and {@link Anchor} are GeckoLib models with their arcs and beam drawn
 *       on top.</li>
 * </ul>
 *
 * <p>Everything here is its own light (full bright): the arena is above the clouds at night as often as by day, and
 * the storm is what lights it.
 */
public final class StormEyeRenderers {

    static final ResourceLocation CLOUD = FrozenFortress.id("textures/entity/fx_storm_eye_cloud.png");
    static final ResourceLocation WALL = FrozenFortress.id("textures/entity/fx_storm_eye_wall.png");
    static final ResourceLocation VEIN = FrozenFortress.id("textures/entity/fx_storm_eye_vein.png");
    static final ResourceLocation BOLT = FrozenFortress.id("textures/entity/fx_storm_eye_bolt.png");
    static final ResourceLocation EYE = FrozenFortress.id("textures/entity/fx_storm_eye_eye.png");
    static final ResourceLocation RUNE = FrozenFortress.id("textures/entity/fx_storm_eye_rune.png");
    static final ResourceLocation WIND = FrozenFortress.id("textures/entity/fx_storm_eye_wind.png");

    private StormEyeRenderers() {
    }

    // ============================================================================================== shared pieces
    static void vtx(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                    float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, Mth.clamp(a, 0.0F, 1.0F)).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    /** A deterministic number in [0, 1) from a few ints - every client draws the same storm. */
    static float hash(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 31);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 29);
        return (h >>> 40) / (float) (1L << 24);
    }

    /** Where the camera is, in the coordinates the renderer draws in (the entity's own, lerped). */
    static Vec3 camera(EntityRenderer<?> r, Entity e, float pt) {
        Vec3 cam = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition();
        return cam.subtract(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    /**
     * LIGHTNING between two points: a jagged path re-struck every other tick (`strike` changes), drawn twice - a wide
     * faint glow and a narrow white-hot core - as camera-facing ribbons, with `forks` short branches off it. The
     * sheet's u runs across the ribbon (its glow falls off to both edges), v along it.
     */
    static void bolt(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 cam, long seed, long strike,
                     float width, float r, float g, float bl, float alpha, int forks) {
        bolt(buffers, pose, a, b, cam, seed, strike, width, r, g, bl, alpha, forks, 1.0F);
    }

    /** As above, its zigzag scaled by `jagScale` (a held beam is nearly straight; a strike is wild). */
    static void bolt(MultiBufferSource buffers, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 cam, long seed, long strike,
                     float width, float r, float g, float bl, float alpha, int forks, float jagScale) {
        if (alpha <= 0.01F) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(BOLT));
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-3D) {
            return;
        }
        int n = Mth.clamp((int) (len / 0.7D), 4, 28);
        Vec3 dir = d.scale(1.0D / len);
        Vec3 p1 = dir.cross(Math.abs(dir.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 p2 = dir.cross(p1).normalize();
        double jag = Math.min(1.6D, 0.12D * len + 0.1D) * jagScale;
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double env = Math.sin(Math.PI * t);
            double o1 = (hash(seed * 31 + strike, i * 2L) - 0.5D) * 2.0D * jag * env;
            double o2 = (hash(seed * 31 + strike, i * 2L + 1) - 0.5D) * 2.0D * jag * env;
            pts[i] = a.add(d.scale(t)).add(p1.scale(o1)).add(p2.scale(o2));
        }
        polyline(vc, pose, pts, cam, width * 2.6F, r, g, bl, alpha * 0.35F);
        polyline(vc, pose, pts, cam, width, Math.min(1.0F, r + 0.3F), Math.min(1.0F, g + 0.3F), 1.0F, alpha);
        for (int f = 0; f < forks; f++) {
            int from = 1 + (int) (hash(seed + strike * 7, 100 + f) * (n - 2));
            Vec3 start = pts[from];
            Vec3 bend = dir.add(p1.scale((hash(seed, 200 + f + strike) - 0.5D) * 2.2D))
                    .add(p2.scale((hash(seed, 300 + f + strike) - 0.5D) * 2.2D)).normalize();
            double fl = len * (0.15D + 0.2D * hash(seed, 400 + f));
            int fn = 4;
            Vec3[] fp = new Vec3[fn + 1];
            for (int i = 0; i <= fn; i++) {
                double t = i / (double) fn;
                double o = (hash(seed + strike, 500 + f * 10 + i) - 0.5D) * fl * 0.3D * Math.sin(Math.PI * t);
                fp[i] = start.add(bend.scale(fl * t)).add(p1.scale(o));
            }
            polyline(vc, pose, fp, cam, width * 0.55F, r, g, bl, alpha * 0.8F);
        }
    }

    /** A camera-facing ribbon along points. */
    static void polyline(VertexConsumer vc, PoseStack.Pose pose, Vec3[] pts, Vec3 cam, float width,
                         float r, float g, float b, float a) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float v = 0.0F;
        for (int i = 0; i + 1 < pts.length; i++) {
            Vec3 p = pts[i], q = pts[i + 1];
            Vec3 seg = q.subtract(p);
            Vec3 side = seg.cross(cam.subtract(p.add(q).scale(0.5D)));
            if (side.lengthSqr() < 1.0E-8D) {
                continue;
            }
            side = side.normalize().scale(width * 0.5D);
            float v1 = v + (float) seg.length() * 0.25F;
            vtx(vc, m, n, (float) (p.x - side.x), (float) (p.y - side.y), (float) (p.z - side.z), 0.0F, v, r, g, b, a);
            vtx(vc, m, n, (float) (q.x - side.x), (float) (q.y - side.y), (float) (q.z - side.z), 0.0F, v1, r, g, b, a);
            vtx(vc, m, n, (float) (q.x + side.x), (float) (q.y + side.y), (float) (q.z + side.z), 1.0F, v1, r, g, b, a);
            vtx(vc, m, n, (float) (p.x + side.x), (float) (p.y + side.y), (float) (p.z + side.z), 1.0F, v, r, g, b, a);
            v = v1;
        }
    }

    /**
     * A surface of revolution, row by row: rows[i] = {y, radius, u-shift, alpha}; `wobble` (if not null) moves each
     * vertex's radius by angle and height. u runs `uRep` times round, v `vRep` times down the whole thing.
     */
    static void shell(VertexConsumer vc, Matrix4f m, Matrix3f n, float[][] rows, int seg, float uRep, float vRep,
                      float r, float g, float b, Wobble wobble) {
        for (int i = 0; i + 1 < rows.length; i++) {
            float[] p = rows[i], q = rows[i + 1];
            float v0 = vRep * i / (rows.length - 1), v1 = vRep * (i + 1) / (rows.length - 1);
            if (p[3] <= 0.004F && q[3] <= 0.004F) {
                continue;
            }
            for (int k = 0; k < seg; k++) {
                float a0 = Mth.TWO_PI * k / seg, a1 = Mth.TWO_PI * (k + 1) / seg;
                float u0 = uRep * k / seg, u1 = uRep * (k + 1) / seg;
                float rp0 = p[1] + (wobble == null ? 0 : wobble.at(a0, p[0])), rp1 = p[1] + (wobble == null ? 0 : wobble.at(a1, p[0]));
                float rq0 = q[1] + (wobble == null ? 0 : wobble.at(a0, q[0])), rq1 = q[1] + (wobble == null ? 0 : wobble.at(a1, q[0]));
                vtx(vc, m, n, Mth.cos(a0) * rp0, p[0], Mth.sin(a0) * rp0, u0 + p[2], v0, r, g, b, p[3]);
                vtx(vc, m, n, Mth.cos(a1) * rp1, p[0], Mth.sin(a1) * rp1, u1 + p[2], v0, r, g, b, p[3]);
                vtx(vc, m, n, Mth.cos(a1) * rq1, q[0], Mth.sin(a1) * rq1, u1 + q[2], v1, r, g, b, q[3]);
                vtx(vc, m, n, Mth.cos(a0) * rq0, q[0], Mth.sin(a0) * rq0, u0 + q[2], v1, r, g, b, q[3]);
            }
        }
    }

    interface Wobble {
        float at(float angle, float y);
    }

    /** A flat disc of a sheet (the sheet's middle at the disc's middle), turned by `spin` radians. */
    static void disc(VertexConsumer vc, Matrix4f m, Matrix3f n, float y, float radius, float spin,
                     float r, float g, float b, float a) {
        float c = Mth.cos(spin) * radius, s = Mth.sin(spin) * radius;
        vtx(vc, m, n, -c + s, y, -s - c, 0.0F, 0.0F, r, g, b, a);
        vtx(vc, m, n, -c - s, y, -s + c, 0.0F, 1.0F, r, g, b, a);
        vtx(vc, m, n, c - s, y, s + c, 1.0F, 1.0F, r, g, b, a);
        vtx(vc, m, n, c + s, y, s - c, 1.0F, 0.0F, r, g, b, a);
    }

    /** Base for the procedural ones: no shadow, never culled by its little box. */
    abstract static class Procedural<T extends Entity> extends EntityRenderer<T> {
        Procedural(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0.0F;
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return CLOUD;
        }

        @Override
        public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
            return entity.shouldRender(x, y, z);
        }
    }

    // ============================================================================================== the vortex
    public static class Vortex extends Procedural<StormEyeVortexEntity> {
        /** Rows of the funnel, segments round it. */
        private static final int ROWS = 18, SEG = 64;

        public Vortex(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(StormEyeVortexEntity v, float yaw, float pt, PoseStack ps, MultiBufferSource buffers,
                           int light) {
            if (v.mode() == StormEyeVortexEntity.LIFT) {
                renderLift(v, pt, ps, buffers);
            } else if (v.mode() == StormEyeVortexEntity.FOLLOW) {
                renderFollow(v, pt, ps, buffers);
            } else {
                renderFunnel(v, pt, ps, buffers);
            }
            super.render(v, yaw, pt, ps, buffers, light);
        }

        /** The funnel's radius at depth fraction h (0 rim .. 1 eye): flared at the top, a throat at the bottom. */
        static float radiusAt(float h, float rimR, float eyeR) {
            float k = 1.0F - h;
            return eyeR + (rimR - eyeR) * k * k * (float) Math.sqrt(k);
        }

        private void renderFunnel(StormEyeVortexEntity v, float pt, PoseStack ps, MultiBufferSource buffers) {
            float t = v.tickCount + pt;
            float rise = v.rise();
            float vis = 1.0F - v.fade();
            if (vis <= 0.01F) {
                return;
            }
            float rimY = -3.0F + rise * 2.4F;
            float eyeY = (float) -StormEyeArena.VORTEX_DEPTH + rise * 6.0F;
            float rimR = (float) StormEyeArena.VORTEX_R * (1.0F - 0.06F * rise);
            float eyeR = 3.0F;
            float spin = 1.0F + 0.6F * rise;
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            // ---- the cloud: the outer shell, u sheared by depth so the streaks run in a spiral into the eye
            float[][] outer = new float[ROWS + 1][];
            float[][] inner = new float[ROWS + 1][];
            for (int i = 0; i <= ROWS; i++) {
                float h = i / (float) ROWS;
                float y = rimY + (eyeY - rimY) * (float) Math.pow(h, 0.8D);
                float r = radiusAt(h, rimR, eyeR);
                float turn = t * (0.0012F + 0.012F * h * h) * spin;
                float edge = Math.min(1.0F, h / 0.12F) * Math.min(1.0F, (1.0F - h) / 0.08F + 0.15F);
                outer[i] = new float[]{y, r, turn + h * 1.6F, 0.92F * edge * vis};
                inner[i] = new float[]{y + 0.6F, r * 0.9F, turn * 1.7F + h * 2.4F, 0.55F * edge * vis};
            }
            float lit = 0.75F + 0.25F * rise;
            shell(buffers.getBuffer(RenderType.entityTranslucent(CLOUD)), m, n, outer, SEG, 6.0F, 3.0F,
                    0.30F * lit, 0.34F * lit, 0.46F * lit, null);
            shell(buffers.getBuffer(RenderType.entityTranslucentEmissive(VEIN)), m, n, inner, SEG, 5.0F, 2.0F,
                    0.55F, 0.62F + 0.2F * rise, 1.0F, null);
            // ---- the eye: a slow white-violet light at the bottom, brighter as it closes
            float pulse = 0.75F + 0.25F * Mth.sin(t * 0.09F) + 0.6F * rise;
            VertexConsumer eye = buffers.getBuffer(RenderType.entityTranslucentEmissive(EYE));
            disc(eye, m, n, eyeY + 0.2F, eyeR * 2.6F, t * 0.02F * spin, 0.80F, 0.78F, 1.0F, Math.min(1.0F, pulse) * vis);
            disc(eye, m, n, eyeY + 1.2F, eyeR * 4.2F, -t * 0.011F, 0.55F, 0.62F, 1.0F, 0.45F * vis * pulse);
            // ---- lightning in its throat: a few short-lived arcs between two points of the inner shell
            Vec3 cam = camera(this, v, pt);
            long bucket = (long) Math.floor(t / 3.0F);
            float chance = 0.32F + 0.4F * rise;
            for (int k = 0; k < 3; k++) {
                if (hash(bucket, k) > chance) {
                    continue;
                }
                float h0 = 0.2F + 0.7F * hash(bucket, k + 10), h1 = Mth.clamp(h0 + 0.12F + 0.2F * hash(bucket, k + 20), 0, 1);
                float a0 = Mth.TWO_PI * hash(bucket, k + 30), a1 = a0 + 0.5F + hash(bucket, k + 40);
                Vec3 p = ring(h0, a0, rimY, eyeY, rimR * 0.86F, eyeR);
                Vec3 q = ring(h1, a1, rimY, eyeY, rimR * 0.86F, eyeR);
                float fresh = 1.0F - ((t / 3.0F) - bucket);
                bolt(buffers, ps.last(), p, q, cam, v.getId() * 7L + k, bucket, 0.5F, 0.7F, 0.75F, 1.0F,
                        fresh * vis, 2);
            }
        }

        private static Vec3 ring(float h, float a, float rimY, float eyeY, float rimR, float eyeR) {
            float y = rimY + (eyeY - rimY) * (float) Math.pow(h, 0.8D);
            float r = radiusAt(h, rimR, eyeR);
            return new Vec3(Mth.cos(a) * r, y, Mth.sin(a) * r);
        }

        /**
         * A COLUMN OF WIND THAT CAN BE SEEN THROUGH (07.10.2026: everything has to be shown, so nobody in it may be
         * blinded by it): three bands of cloud wound round the axis from y0 to y1, a turn every `pitch` blocks, each
         * band `width` tall; a vein of light along one of them; and only a faint shell under all of it. Between the
         * bands is open air - whoever is carried up inside it sees the hall drop away and the roof come apart.
         */
        static void helixColumn(MultiBufferSource buffers, PoseStack.Pose pose, float y0, float y1,
                                java.util.function.DoubleUnaryOperator radius, float pitch, float width, float turn,
                                float alpha, float shellAlpha, float lit) {
            if (alpha <= 0.01F || y1 - y0 < 0.5F) {
                return;
            }
            Matrix4f m = pose.pose();
            Matrix3f n = pose.normal();
            int steps = Mth.clamp((int) ((y1 - y0) / 0.8F), 4, 260);
            // ONE CONSUMER AT A TIME (07.10.2026, the port's crash "Not building!"): asking the buffer source for a
            // second render type ends the first, so the cloud is written whole - bands, then shell - and only then the
            // vein, from the first band's points kept for it
            VertexConsumer cloud = buffers.getBuffer(RenderType.entityTranslucent(CLOUD));
            float[] vx = null, vy = null, vz = null, vu = null, vfa = null;
            float vh = 0.0F;
            for (int b = 0; b < 3; b++) {
                float[] px = new float[steps + 1], pz = new float[steps + 1], py = new float[steps + 1];
                float[] u = new float[steps + 1], fa = new float[steps + 1];
                float arc = 0.0F;
                for (int i = 0; i <= steps; i++) {
                    float y = y0 + (y1 - y0) * i / steps;
                    float a = turn + b * Mth.TWO_PI / 3.0F + y * Mth.TWO_PI / pitch;
                    float r = (float) radius.applyAsDouble(y);
                    px[i] = Mth.cos(a) * r;
                    pz[i] = Mth.sin(a) * r;
                    py[i] = y;
                    if (i > 0) {
                        arc += (float) Math.sqrt((px[i] - px[i - 1]) * (px[i] - px[i - 1])
                                + (pz[i] - pz[i - 1]) * (pz[i] - pz[i - 1]) + (py[i] - py[i - 1]) * (py[i] - py[i - 1]));
                    }
                    u[i] = arc / 5.0F;
                    fa[i] = alpha * Math.min(1.0F, (y - y0) / 3.0F) * Math.min(1.0F, (y1 - y) / 3.0F);
                }
                float h = width * 0.5F;
                for (int i = 0; i < steps; i++) {
                    vtx(cloud, m, n, px[i], py[i] - h, pz[i], u[i], 1.0F, lit, lit + 0.04F, lit + 0.16F, fa[i]);
                    vtx(cloud, m, n, px[i + 1], py[i + 1] - h, pz[i + 1], u[i + 1], 1.0F, lit, lit + 0.04F, lit + 0.16F, fa[i + 1]);
                    vtx(cloud, m, n, px[i + 1], py[i + 1] + h, pz[i + 1], u[i + 1], 0.0F, lit, lit + 0.04F, lit + 0.16F, fa[i + 1]);
                    vtx(cloud, m, n, px[i], py[i] + h, pz[i], u[i], 0.0F, lit, lit + 0.04F, lit + 0.16F, fa[i]);
                }
                if (b == 0) {
                    vx = px;
                    vy = py;
                    vz = pz;
                    vu = u;
                    vfa = fa;
                    vh = h * 0.35F;
                }
            }
            if (shellAlpha > 0.01F) {
                int rows = Mth.clamp((int) ((y1 - y0) / 4.0F), 2, 60);
                float[][] sh = new float[rows + 1][];
                for (int i = 0; i <= rows; i++) {
                    float y = y0 + (y1 - y0) * i / rows;
                    float e = Math.min(1.0F, (y - y0) / 4.0F) * Math.min(1.0F, (y1 - y) / 4.0F);
                    sh[i] = new float[]{y, (float) radius.applyAsDouble(y) + 0.3F, turn * 0.4F + y * 0.02F, shellAlpha * e};
                }
                shell(cloud, m, n, sh, 28, 3.0F, (y1 - y0) / 16.0F, lit, lit + 0.04F, lit + 0.16F, null);
            }
            if (vx != null) {
                VertexConsumer vein = buffers.getBuffer(RenderType.entityTranslucentEmissive(VEIN));
                float k = 0.97F;
                for (int i = 0; i < steps; i++) {
                    vtx(vein, m, n, vx[i] * k, vy[i] - vh, vz[i] * k, vu[i], 0.7F, 0.62F, 0.72F, 1.0F, vfa[i]);
                    vtx(vein, m, n, vx[i + 1] * k, vy[i + 1] - vh, vz[i + 1] * k, vu[i + 1], 0.7F, 0.62F, 0.72F, 1.0F, vfa[i + 1]);
                    vtx(vein, m, n, vx[i + 1] * k, vy[i + 1] + vh, vz[i + 1] * k, vu[i + 1], 0.3F, 0.62F, 0.72F, 1.0F, vfa[i + 1]);
                    vtx(vein, m, n, vx[i] * k, vy[i] + vh, vz[i] * k, vu[i], 0.3F, 0.62F, 0.72F, 1.0F, vfa[i]);
                }
            }
        }

        /**
         * The hall's column under the hole in the dome, reaching up through it to the ice: THE AIR CORRIDOR, the way
         * (back) up. It no longer goes quiet once the fight is up: it is how anybody gets there now, and it
         * has to be seen from the hall's door - the bands of cloud wound round it at full strength and lit brighter, a
         * second, finer winding inside them turning the other way and climbing fast (the updraft), rings of light
         * running up it through the roof, and the lit pool on the floor where it takes you.
         */
        private void renderLift(StormEyeVortexEntity v, float pt, PoseStack ps, MultiBufferSource buffers) {
            float t = v.tickCount + pt;
            float open = v.opened(pt);
            float calm = 1.0F;
            float h = v.height() * (0.15F + 0.85F * open);
            float r0 = StormEyeVortexEntity.LIFT_R * (0.3F + 0.7F * open);
            helixColumn(buffers, ps.last(), 0.0F, h, y -> r0 * (1.0D + 0.12D * Math.sin(y * 0.09D + t * 0.03D)),
                    11.0F, 2.2F, -t * 0.06F, 0.78F, 0.16F, 0.62F);
            helixColumn(buffers, ps.last(), 0.0F, h, y -> r0 * 0.7D * (1.0D + 0.08D * Math.sin(y * 0.13D - t * 0.05D)),
                    6.5F, 0.8F, t * 0.15F, 0.6F * open, 0.0F, 0.86F);
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            VertexConsumer rings = buffers.getBuffer(RenderType.entityTranslucentEmissive(VEIN));
            for (int k = 0; k < 6; k++) {
                float y = (t * 0.55F + k * h / 6.0F) % Math.max(1.0F, h);
                float fa = 0.8F * open * Math.min(1.0F, y / 3.0F) * Math.min(1.0F, (h - y) / 6.0F);
                risingRing(rings, m, n, y, r0 * (1.04F + 0.04F * Mth.sin(t * 0.2F + k)), fa);
            }
            VertexConsumer eye = buffers.getBuffer(RenderType.entityTranslucentEmissive(EYE));
            disc(eye, m, n, 0.06F, r0 * 1.8F, t * 0.04F, 0.75F, 0.8F, 1.0F, 0.95F * open);
            Vec3 cam = camera(this, v, pt);
            long bucket = (long) Math.floor(t / 4.0F);
            if (hash(bucket, 5) < 0.45F * calm) {
                float a0 = Mth.TWO_PI * hash(bucket, 6);
                float y0 = Math.min(h, 26.0F) * hash(bucket, 8);
                Vec3 p = new Vec3(Mth.cos(a0) * r0 * 0.6F, y0, Mth.sin(a0) * r0 * 0.6F);
                Vec3 q = new Vec3(Mth.cos(a0 + 1.4F) * r0, y0 + 4.0F + 6.0F * hash(bucket, 7), Mth.sin(a0 + 1.4F) * r0);
                bolt(buffers, ps.last(), p, q, cam, v.getId(), bucket, 0.35F, 0.7F, 0.75F, 1.0F, 0.9F, 1);
            }
        }

        /** A band of light round the corridor at height y - one of the rings climbing it. */
        private static void risingRing(VertexConsumer vc, Matrix4f m, Matrix3f n, float y, float r, float a) {
            if (a <= 0.01F) {
                return;
            }
            int seg = 40;
            float hh = 0.28F;
            for (int i = 0; i < seg; i++) {
                float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
                float u0 = 6.0F * i / seg, u1 = 6.0F * (i + 1) / seg;
                float x0 = Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
                vtx(vc, m, n, x0, y - hh, z0, u0, 1.0F, 0.8F, 0.92F, 1.0F, a);
                vtx(vc, m, n, x1, y - hh, z1, u1, 1.0F, 0.8F, 0.92F, 1.0F, a);
                vtx(vc, m, n, x1, y + hh, z1, u1, 0.0F, 0.8F, 0.92F, 1.0F, a);
                vtx(vc, m, n, x0, y + hh, z0, u0, 0.0F, 0.8F, 0.92F, 1.0F, a);
                vtx(vc, m, n, x0, y + hh, z0, u0, 0.0F, 0.8F, 0.92F, 1.0F, a);
                vtx(vc, m, n, x1, y + hh, z1, u1, 0.0F, 0.8F, 0.92F, 1.0F, a);
                vtx(vc, m, n, x1, y - hh, z1, u1, 1.0F, 0.8F, 0.92F, 1.0F, a);
                vtx(vc, m, n, x0, y - hh, z0, u0, 1.0F, 0.8F, 0.92F, 1.0F, a);
            }
        }

        /**
         * THE VORTEX ROUND HIM (FOLLOW): drawn on HIS interpolated position, not its own, so it never lags him; from
         * seven and a half blocks over his feet down the length of the column it trails (the others are carried up in
         * it); tighter round him and the one he holds, wider below; lightning running down it.
         */
        private void renderFollow(StormEyeVortexEntity v, float pt, PoseStack ps, MultiBufferSource buffers) {
            float t = v.tickCount + pt;
            float open = v.opened(pt);
            float vis = (1.0F - v.fade()) * open;
            if (vis <= 0.01F) {
                return;
            }
            double ox = 0.0D, oy = 0.0D, oz = 0.0D;
            if (v.level().getEntity(v.kingId()) instanceof VelkharEntity king) {
                ox = Mth.lerp(pt, king.xo, king.getX()) - Mth.lerp(pt, v.xo, v.getX());
                oy = Mth.lerp(pt, king.yo, king.getY()) - Mth.lerp(pt, v.yo, v.getY());
                oz = Mth.lerp(pt, king.zo, king.getZ()) - Mth.lerp(pt, v.zo, v.getZ());
            }
            ps.pushPose();
            ps.translate(ox, oy, oz);
            float trail = Math.max(2.0F, v.height());
            float grow = 0.4F + 0.6F * open;
            helixColumn(buffers, ps.last(), -trail, 7.5F,
                    y -> grow * (3.5D + (y < 0.0D ? Math.min(1.4D, -y * 0.05D) : 0.0D) + 0.25D * Math.sin(y * 0.4D + t * 0.1D)),
                    9.0F, 2.4F, t * 0.16F, 0.7F * vis, 0.1F * vis, 0.46F);
            Vec3 cam = camera(this, v, pt).subtract(ox, oy, oz);
            long bucket = (long) Math.floor(t / 3.0F);
            for (int k = 0; k < 2; k++) {
                if (hash(bucket, k + 60) > 0.55F) {
                    continue;
                }
                float a0 = Mth.TWO_PI * hash(bucket, k + 61);
                float y0 = -trail * hash(bucket, k + 62) + 4.0F;
                Vec3 p = new Vec3(Mth.cos(a0) * 3.3F, y0, Mth.sin(a0) * 3.3F);
                Vec3 q = new Vec3(Mth.cos(a0 + 1.2F) * 3.6F, y0 - 5.0F - 4.0F * hash(bucket, k + 63), Mth.sin(a0 + 1.2F) * 3.6F);
                bolt(buffers, ps.last(), p, q, cam, v.getId() * 11L + k, bucket, 0.3F, 0.72F, 0.78F, 1.0F, 0.85F * vis, 1);
            }
            ps.popPose();
        }
    }

    // ============================================================================================== rubble
    /** A piece of the roof, the block it was, turning as it falls. */
    public static class Rubble extends EntityRenderer<com.jastkub.frozenfortress.entity.boss.StormEyeRubbleEntity> {
        public Rubble(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0.0F;
        }

        @Override
        public void render(com.jastkub.frozenfortress.entity.boss.StormEyeRubbleEntity r, float yaw, float pt,
                           PoseStack ps, MultiBufferSource buffers, int light) {
            net.minecraft.world.level.block.state.BlockState st = r.block();
            if (st.isAir() || st.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) {
                return;
            }
            float s = 0.62F + 0.18F * ((r.getId() * 37) % 10) / 10.0F;
            ps.pushPose();
            ps.translate(0.0D, 0.3D, 0.0D);
            float spin = Mth.lerp(pt, r.spinO, r.spin);
            ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
            ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(spin * 0.7F));
            ps.scale(s, s, s);
            ps.translate(-0.5D, -0.5D, -0.5D);
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.getBlockRenderer().getModelRenderer().tesselateBlock(r.level(),
                    mc.getBlockRenderer().getBlockModel(st), st, r.blockPosition(), ps,
                    buffers.getBuffer(RenderType.cutout()), false, net.minecraft.util.RandomSource.create(),
                    st.getSeed(r.blockPosition()), OverlayTexture.NO_OVERLAY);
            ps.popPose();
            super.render(r, yaw, pt, ps, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(com.jastkub.frozenfortress.entity.boss.StormEyeRubbleEntity r) {
            return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
        }
    }

    // ============================================================================================== the wall
    public static class Wall extends Procedural<StormEyeWallEntity> {
        private static final int SEG = 96, ROWS = 12;

        public Wall(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(StormEyeWallEntity w, float yaw, float pt, PoseStack ps, MultiBufferSource buffers,
                           int light) {
            float t = w.tickCount + pt;
            float vis = (1.0F - w.fade()) * w.formed(pt);
            if (vis <= 0.01F) {
                return;
            }
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            float R = (float) StormEyeArena.WALL_R;
            float lo = (float) -StormEyeArena.WALL_BELOW, hi = (float) StormEyeArena.WALL_ABOVE;
            // three shells: inner (darker, veined), the body, an outer haze - each turning its own way
            float[] off = {-1.3F, 0.0F, 1.5F};
            float[] alpha = {0.6F, 0.9F, 0.65F};
            float[] speed = {0.0016F, -0.0009F, 0.0006F};
            for (int sh = 0; sh < 3; sh++) {
                final int s = sh;
                float[][] rows = new float[ROWS + 1][];
                for (int i = 0; i <= ROWS; i++) {
                    float k = i / (float) ROWS;
                    float y = lo + (hi - lo) * k;
                    // the top curls in over the arena: the last rows pulled toward the middle
                    float curl = k > 0.75F ? (k - 0.75F) / 0.25F : 0.0F;
                    float r = R + off[s] - 4.5F * curl * curl;
                    float yy = y + (curl > 0 ? -1.5F * curl * curl : 0.0F);
                    float edge = Math.min(1.0F, k / 0.18F) * Math.min(1.0F, (1.0F - k) / 0.1F + 0.2F);
                    rows[i] = new float[]{yy, r, t * speed[s] + k * 0.3F * (s - 1), alpha[s] * edge * vis};
                }
                Wobble wob = (a, y) -> 1.0F * Mth.sin(3.0F * a + y * 0.22F + t * 0.012F * (s + 1))
                        + 0.6F * Mth.sin(7.0F * a - t * 0.025F + y * 0.45F + s);
                float lit = s == 0 ? 0.26F : (s == 1 ? 0.36F : 0.44F);
                shell(buffers.getBuffer(RenderType.entityTranslucent(WALL)), m, n, rows, SEG, 12.0F, 1.0F,
                        lit, lit + 0.04F, lit + 0.14F, wob);
                if (s == 0) {
                    float[][] veins = new float[ROWS + 1][];
                    for (int i = 0; i <= ROWS; i++) {
                        veins[i] = new float[]{rows[i][0], rows[i][1] - 0.25F, -t * 0.004F + i * 0.07F,
                                rows[i][3] * (0.55F + 0.45F * Mth.sin(t * 0.13F + i))};
                    }
                    shell(buffers.getBuffer(RenderType.entityTranslucentEmissive(VEIN)), m, n, veins, SEG, 10.0F, 2.0F,
                            0.62F, 0.72F, 1.0F, wob);
                }
            }
            // ---- arcs crawling along the inside of it
            Vec3 cam = camera(this, w, pt);
            long bucket = (long) Math.floor(t / 2.0F);
            for (int k = 0; k < 5; k++) {
                if (hash(bucket, k) > 0.45F) {
                    continue;
                }
                float a0 = Mth.TWO_PI * hash(bucket / 3, k + 50);
                float y0 = lo + 4.0F + (hi - lo - 8.0F) * hash(bucket / 3, k + 60);
                float a1 = a0 + 0.08F + 0.12F * hash(bucket, k + 70);
                float y1 = y0 + (hash(bucket, k + 80) - 0.5F) * 7.0F;
                Vec3 p = new Vec3(Mth.cos(a0) * (R - 1.6F), y0, Mth.sin(a0) * (R - 1.6F));
                Vec3 q = new Vec3(Mth.cos(a1) * (R - 1.6F), y1, Mth.sin(a1) * (R - 1.6F));
                bolt(buffers, ps.last(), p, q, cam, w.getId() * 13L + k, bucket, 0.32F, 0.72F, 0.78F, 1.0F, 0.85F * vis, 2);
            }
            super.render(w, yaw, pt, ps, buffers, light);
        }
    }

    // ============================================================================================== the rune
    public static class Rune extends Procedural<StormEyeRuneEntity> {
        public Rune(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(StormEyeRuneEntity rn, float yaw, float pt, PoseStack ps, MultiBufferSource buffers,
                           int light) {
            float t = rn.tickCount + pt;
            float R = rn.radius();
            int delay = StormEyeRuneEntity.DELAY;
            float charge = Mth.clamp(t / delay, 0.0F, 1.0F);
            float after = t - delay;
            float out = after <= 0 ? 1.0F : Mth.clamp(1.0F - (after - StormEyeRuneEntity.STRIKE_SHOW) / 8.0F, 0.0F, 1.0F);
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            // ---- the sigil, burning up through the ice, faster and brighter as the strike nears
            float in = Mth.clamp(t / 6.0F, 0.0F, 1.0F);
            float flick = after < 0 && charge > 0.7F ? 0.75F + 0.25F * Mth.sin(t * 2.4F) : 1.0F;
            VertexConsumer sig = buffers.getBuffer(RenderType.entityTranslucentEmissive(RUNE));
            float spin = t * (0.02F + 0.06F * charge * charge);
            disc(sig, m, n, 0.04F, R * in, spin, 0.62F + 0.38F * charge, 0.66F + 0.3F * charge, 1.0F,
                    (0.55F + 0.45F * charge) * flick * out);
            disc(sig, m, n, 0.07F, R * 1.12F * in, -spin * 0.6F, 0.5F, 0.55F, 1.0F, 0.35F * charge * out);
            // its rim, lifting as it charges
            float[][] rim = {{0.0F, R + 0.1F, -t * 0.01F, 0.8F * charge * out}, {0.25F + 0.9F * charge, R + 0.1F, -t * 0.01F, 0.0F}};
            shell(buffers.getBuffer(RenderType.entityTranslucentEmissive(VEIN)), m, n, rim, 40, R * 1.2F, 1.0F,
                    0.65F, 0.72F, 1.0F, null);
            // ---- the cloud knot over it
            float H = StormEyeRuneEntity.CLOUD_H;
            float knotIn = Mth.clamp(t / 10.0F, 0.0F, 1.0F) * out;
            float[][] knot = {{H + 1.6F, (R + 1.8F) * knotIn, t * 0.01F, 0.0F},
                    {H, (R + 1.4F) * knotIn, t * 0.012F, 0.85F * knotIn},
                    {H - 1.2F, (R * 0.7F) * knotIn, t * 0.02F + 0.4F, 0.8F * knotIn},
                    {H - 2.0F, 0.6F * knotIn, t * 0.03F + 0.8F, 0.0F}};
            float glow = after >= 0 && after < 4 ? 1.0F : 0.3F + 0.4F * charge;
            shell(buffers.getBuffer(RenderType.entityTranslucent(CLOUD)), m, n, knot, 32, 3.0F, 1.0F,
                    0.28F * (1 + glow), 0.3F * (1 + glow), 0.42F * (1 + glow), null);
            // ---- the strike
            if (after >= 0 && after < StormEyeRuneEntity.STRIKE_SHOW) {
                Vec3 cam = camera(this, rn, pt);
                float a = 1.0F - after / StormEyeRuneEntity.STRIKE_SHOW;
                long strike = (long) (after / 2.0F);
                bolt(buffers, ps.last(), new Vec3(0, H - 1.6F, 0), new Vec3(0, 0.15F, 0), cam, rn.getId(), strike,
                        1.25F, 0.8F, 0.82F, 1.0F, a, 3);
                for (int k = 0; k < 4; k++) {
                    float ang = Mth.TWO_PI * (k / 4.0F + hash(rn.getId(), k) * 0.2F);
                    Vec3 start = new Vec3(0, H * (0.25F + 0.2F * hash(rn.getId(), k + 9)), 0);
                    Vec3 end = new Vec3(Mth.cos(ang) * R * 0.85F, 0.1F, Mth.sin(ang) * R * 0.85F);
                    bolt(buffers, ps.last(), start, end, cam, rn.getId() * 5L + k, strike, 0.45F, 0.75F, 0.8F, 1.0F,
                            a * 0.85F, 1);
                }
                // the flash on the ice
                disc(buffers.getBuffer(RenderType.entityTranslucentEmissive(EYE)), m, n, 0.1F, (R + 1.0F) * (1.0F + after * 0.08F),
                        0.0F, 0.85F, 0.88F, 1.0F, a);
            }
            super.render(rn, yaw, pt, ps, buffers, light);
        }
    }

    // ============================================================================================== the gale
    public static class Gale extends Procedural<StormEyeGaleEntity> {
        public Gale(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(StormEyeGaleEntity g, float yaw, float pt, PoseStack ps, MultiBufferSource buffers,
                           int light) {
            float t = g.tickCount + pt;
            int life = StormEyeGaleEntity.life();
            float r = StormEyeGaleEntity.radius(Math.min(t, life));
            float vis = Mth.clamp(t / 4.0F, 0.0F, 1.0F) * Mth.clamp((life + 6 - t) / 8.0F, 0.0F, 1.0F);
            if (vis <= 0.01F) {
                return;
            }
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            float half = StormEyeGaleEntity.HALF;
            float dir = g.dir(), gap = g.gap();
            float gh = StormEyeGaleEntity.gapHalf(r);
            float H = StormEyeGaleEntity.HEIGHT;
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(WIND));
            int steps = Mth.clamp((int) (r * half * 2.0F / 0.6F), 12, 90);
            // three sheets: the front, and two trailing behind it thinner
            for (int layer = 0; layer < 3; layer++) {
                float rr = r - layer * 0.75F;
                float la = (layer == 0 ? 0.85F : (layer == 1 ? 0.45F : 0.22F)) * vis;
                float lean = 0.9F - layer * 0.2F;
                for (int i = 0; i < steps; i++) {
                    float s0 = -half + 2.0F * half * i / steps, s1 = -half + 2.0F * half * (i + 1) / steps;
                    float mid = (s0 + s1) * 0.5F;
                    if (Math.abs(mid - gap) < gh) {
                        continue;                                  // the gap
                    }
                    float a0 = dir + s0, a1 = dir + s1;
                    float e0 = edgeFade(s0, half, gap, gh), e1 = edgeFade(s1, half, gap, gh);
                    float u0 = (rr * s0) / 4.0F - t * 0.06F, u1 = (rr * s1) / 4.0F - t * 0.06F;
                    float c0 = Mth.cos(a0), z0 = Mth.sin(a0), c1 = Mth.cos(a1), z1 = Mth.sin(a1);
                    vtx(vc, m, n, c0 * rr, -0.4F, z0 * rr, u0, 1.0F, 0.82F, 0.9F, 1.0F, la * e0);
                    vtx(vc, m, n, c1 * rr, -0.4F, z1 * rr, u1, 1.0F, 0.82F, 0.9F, 1.0F, la * e1);
                    vtx(vc, m, n, c1 * (rr + lean), H, z1 * (rr + lean), u1, 0.0F, 0.82F, 0.9F, 1.0F, la * e1 * 0.6F);
                    vtx(vc, m, n, c0 * (rr + lean), H, z0 * (rr + lean), u0, 0.0F, 0.82F, 0.9F, 1.0F, la * e0 * 0.6F);
                }
            }
            // the gap's torn edges: two bright posts so it can be found at a glance
            VertexConsumer posts = buffers.getBuffer(RenderType.entityTranslucentEmissive(VEIN));
            for (float side : new float[]{-1.0F, 1.0F}) {
                float a = dir + gap + side * gh;
                float c = Mth.cos(a), s = Mth.sin(a);
                float tx = -s * 0.35F, tz = c * 0.35F;
                vtx(posts, m, n, c * r - tx, -0.4F, s * r - tz, 0.0F, 1.0F, 0.85F, 0.92F, 1.0F, vis);
                vtx(posts, m, n, c * r + tx, -0.4F, s * r + tz, 1.0F, 1.0F, 0.85F, 0.92F, 1.0F, vis);
                vtx(posts, m, n, c * (r + 0.9F) + tx, H + 0.4F, s * (r + 0.9F) + tz, 1.0F, 0.0F, 0.85F, 0.92F, 1.0F, vis * 0.3F);
                vtx(posts, m, n, c * (r + 0.9F) - tx, H + 0.4F, s * (r + 0.9F) - tz, 0.0F, 0.0F, 0.85F, 0.92F, 1.0F, vis * 0.3F);
            }
            super.render(g, yaw, pt, ps, buffers, light);
        }

        /** Soft at the two ends of the sweep, crisp at the gap. */
        private static float edgeFade(float s, float half, float gap, float gh) {
            float ends = Mth.clamp((half - Math.abs(s)) / 0.12F, 0.0F, 1.0F);
            float hole = Mth.clamp((Math.abs(s - gap) - gh) / 0.03F, 0.0F, 1.0F);
            return Math.min(ends, 0.3F + 0.7F * hole);
        }
    }

    // ============================================================================================== a gust
    public static class Gust extends Procedural<StormEyeGustEntity> {
        public Gust(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(StormEyeGustEntity g, float yaw, float pt, PoseStack ps, MultiBufferSource buffers,
                           int light) {
            float t = g.tickCount + pt;
            float vis = Mth.clamp(g.strength(pt), 0.0F, 1.0F);
            if (vis <= 0.01F) {
                return;
            }
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            boolean rescue = g.mode() == StormEyeGustEntity.RESCUE;
            float H = rescue ? 6.0F : 4.0F;
            float r0 = rescue ? 0.9F : 0.8F, r1 = rescue ? 2.2F : 2.8F;
            float y0 = rescue ? -2.5F : 0.0F;
            int rows = 6;
            float[][] cone = new float[rows + 1][];
            for (int i = 0; i <= rows; i++) {
                float s = i / (float) rows;
                float edge = Math.min(1.0F, s / 0.15F) * Math.min(1.0F, (1.0F - s) / 0.25F);
                cone[i] = new float[]{y0 + s * H, r0 + (r1 - r0) * s, -t * 0.08F + s * 0.9F, 0.7F * edge * vis};
            }
            shell(buffers.getBuffer(RenderType.entityTranslucentEmissive(rescue ? WIND : CLOUD)), m, n, cone, 24,
                    2.0F, 1.0F, 0.85F, 0.9F, 1.0F, null);
            // the helix ribbons of wind round it
            if (rescue) {
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(WIND));
                Vec3 cam = camera(this, g, pt);
                for (int k = 0; k < 3; k++) {
                    Vec3[] pts = new Vec3[16];
                    for (int i = 0; i < pts.length; i++) {
                        float s = i / (float) (pts.length - 1);
                        float a = t * 0.55F + k * Mth.TWO_PI / 3.0F + s * Mth.TWO_PI * 1.5F;
                        float rr = r0 + (r1 - r0) * s + 0.2F;
                        pts[i] = new Vec3(Mth.cos(a) * rr, y0 + s * H, Mth.sin(a) * rr);
                    }
                    polyline(vc, ps.last(), pts, cam, 0.5F, 0.9F, 0.95F, 1.0F, 0.8F * vis);
                }
            }
            super.render(g, yaw, pt, ps, buffers, light);
        }
    }

    // ============================================================================================== a bolt
    public static class Bolt extends Procedural<StormEyeBoltEntity> {
        public Bolt(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(StormEyeBoltEntity b, float yaw, float pt, PoseStack ps, MultiBufferSource buffers,
                           int light) {
            float t = b.tickCount + pt;
            float a = Mth.clamp(1.0F - t / Math.max(1, b.life() + 1), 0.0F, 1.0F);
            boolean violet = b.tint() == StormEyeBoltEntity.VIOLET;
            bolt(buffers, ps.last(), Vec3.ZERO, b.span(), camera(this, b, pt), b.getId(), (long) (t / 2.0F),
                    b.width(), violet ? 0.75F : 0.72F, violet ? 0.6F : 0.8F, 1.0F, (float) Math.sqrt(a), 2);
            super.render(b, yaw, pt, ps, buffers, light);
        }
    }

    // ============================================================================================== an orb
    public static class Orb extends GeoEntityRenderer<StormEyeOrbEntity> {
        public Orb(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("fx_storm_eye_orb"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void render(StormEyeOrbEntity o, float yaw, float pt, PoseStack ps, MultiBufferSource buffers,
                           int light) {
            super.render(o, yaw, pt, ps, buffers, light);
            if (o.bursting()) {
                return;
            }
            // its cage of arcs: three short licks from the core to the shell, re-struck every other tick
            float t = o.tickCount + pt;
            long strike = (long) (t / 2.0F);
            Vec3 cam = camera(this, o, pt);
            Vec3 core = new Vec3(0.0D, 0.4D, 0.0D);
            for (int k = 0; k < 3; k++) {
                float a = Mth.TWO_PI * hash(strike, k + o.getId());
                float e = (hash(strike, k + 9) - 0.5F) * 2.2F;
                Vec3 to = core.add(Mth.cos(a) * 0.95F, e * 0.6F, Mth.sin(a) * 0.95F);
                bolt(buffers, ps.last(), core, to, cam, o.getId() * 3L + k, strike, 0.08F, 0.75F, 0.8F, 1.0F, 0.9F, 0);
            }
        }

        @Override
        public boolean shouldRender(StormEyeOrbEntity o, Frustum frustum, double x, double y, double z) {
            return o.shouldRender(x, y, z);
        }
    }

    // ============================================================================================== an anchor
    public static class Anchor extends GeoEntityRenderer<StormEyeAnchorEntity> {
        public Anchor(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("fx_storm_eye_anchor"), false));
            this.shadowRadius = 0.6F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        protected float getDeathMaxRotation(StormEyeAnchorEntity a) {
            return 0.0F;
        }

        /** No name over it: a LivingEntity that is not a Mob gets GeckoLib's name tag at 64 blocks. */
        @Override
        public boolean shouldShowName(StormEyeAnchorEntity a) {
            return false;
        }

        /** It comes up out of the ice: lowered by what is still under the floe. TURNS ONLY ON THE FIRST PASS. */
        @Override
        public void preRender(PoseStack ps, StormEyeAnchorEntity a, BakedGeoModel model, MultiBufferSource buffers,
                              VertexConsumer buffer, boolean isReRender, float pt, int light, int overlay,
                              float red, float green, float blue, float alpha) {
            if (!isReRender) {
                float risen = a.risen(pt);
                float e = 1.0F - (1.0F - risen) * (1.0F - risen);
                ps.translate(0.0D, -3.3D * (1.0F - e), 0.0D);
            }
            super.preRender(ps, a, model, buffers, buffer, isReRender, pt, light, overlay, red, green, blue, alpha);
        }

        @Override
        public void render(StormEyeAnchorEntity a, float yaw, float pt, PoseStack ps, MultiBufferSource buffers,
                           int light) {
            super.render(a, yaw, pt, ps, buffers, light);
            if (!a.isAlive() || a.risen(pt) < 1.0F || a.level() == null) {
                return;
            }
            // ---- THE BEAM: from the crystal to his chest - to HIM, never to a mirror: that is the mirrors' tell
            if (!(a.level().getEntity(a.kingId()) instanceof VelkharEntity king) || !king.isAlive()) {
                return;
            }
            float t = a.tickCount + pt;
            Vec3 me = new Vec3(Mth.lerp(pt, a.xo, a.getX()), Mth.lerp(pt, a.yo, a.getY()), Mth.lerp(pt, a.zo, a.getZ()));
            Vec3 him = new Vec3(Mth.lerp(pt, king.xo, king.getX()), Mth.lerp(pt, king.yo, king.getY()),
                    Mth.lerp(pt, king.zo, king.getZ())).add(0.0D, VelkharEntity.BEAM_ORIGIN_HEIGHT, 0.0D);
            Vec3 from = new Vec3(0.0D, StormEyeAnchorEntity.CRYSTAL_Y, 0.0D);
            Vec3 to = him.subtract(me);
            Vec3 cam = camera(this, a, pt);
            long strike = (long) (t / 2.0F);
            // a steady thin thread under the jagged one, so the line reads as held, not as flicker
            polyline(buffers.getBuffer(RenderType.entityTranslucentEmissive(BOLT)), ps.last(),
                    new Vec3[]{from, to}, cam, 0.16F, 0.8F, 0.85F, 1.0F, 0.75F);
            bolt(buffers, ps.last(), from, to, cam, a.getId(), strike, 0.22F, 0.68F, 0.74F, 1.0F,
                    0.75F + 0.25F * Mth.sin(t * 0.7F), 1, 0.28F);
        }

        @Override
        public boolean shouldRender(StormEyeAnchorEntity a, Frustum frustum, double x, double y, double z) {
            return a.shouldRender(x, y, z);
        }
    }
}
