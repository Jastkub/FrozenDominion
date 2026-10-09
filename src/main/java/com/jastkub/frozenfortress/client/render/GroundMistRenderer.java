package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.GroundMistEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * A STORM CLOUD, drawn as a mass of lumps with lightning in it.
 *
 * <p>THE LAST VERSION WAS SIX FLAT DISCS TURNING AGAINST EACH OTHER, and that
 * was a fog bank: correct for fog, and fog is not what this is. Two things
 * separate a cloud from a haze and the discs had neither.
 *
 * <p>IT IS A VOLUME, NOT A SHEET. A couple of dozen camera-facing lumps
 * scattered through an ellipsoid, each its own size and drifting at its own
 * rate. What makes it read as a solid is that they OVERLAP - the middle of the
 * cloud stacks six deep and goes nearly opaque while the edges are one lump
 * thick and ragged, so the silhouette breaks up on its own without anything
 * being drawn to break it up. Flat discs could never do this: they occupy no
 * depth, so stacking them changes the alpha and nothing else.
 *
 * <p>IT IS LIT FROM ABOVE. The sheet carries a top-to-bottom value ramp and
 * the tint darkens with depth into the cloud, so the crown catches and the
 * underside is heavy. This is the whole difference between a storm and a grey
 * smudge, and it costs one multiply.
 *
 * <p>AND IT HAS WEATHER IN IT. Every second or two something goes off inside:
 * a point is chosen in the volume and the lumps near it flash for three ticks,
 * brightest at the centre and falling off with distance, so the light appears
 * to come from WITHIN the mass rather than being painted on the front of it.
 * The cloud is the only source of that light - it is never fullbright
 * otherwise - which is what sells it as a thing with a storm inside.
 *
 * <p>Two shapes, one cloud: flat and wide when it lies on a floor, domed and
 * deep when it hangs overhead. Everything else is identical.
 */
public class GroundMistRenderer extends EntityRenderer<GroundMistEntity> {

    private static final ResourceLocation SHEET =
            FrozenFortress.id("textures/entity/ground_mist.png");

    private static final ResourceLocation ARC =
            FrozenFortress.id("textures/entity/ice_arc.png");

    /** Lumps in the mass. Enough to overlap several deep in the middle. */
    private static final int PUFFS = 34;
    /** How often something goes off inside it, in ticks. */
    private static final int STRIKE_EVERY = 30;
    private static final int STRIKE_LEN = 5;
    /** Filaments drawn through the mass while it is going off. */
    private static final int BOLTS = 6;
    private static final int BOLT_STEPS = 9;

    /**
     * THE CLOUD IS LIVE BETWEEN STRIKES, not just during them.
     *
     * <p>It had filaments only inside a five-tick flash every thirty - so for
     * five sixths of its life it was a grey mass with nothing happening in it,
     * and the thing it is supposed to be is a CHARGED cloud that occasionally
     * discharges. The difference between those two readings is entirely what
     * happens in the quiet part.
     *
     * <p>So a constant crawl underneath: filaments at a fifth of the flash's
     * brightness, re-seeded twice a second so they writhe rather than sit.
     * Faint enough to be texture at a distance and unmistakable up close,
     * which is the right way round - the boulders come out of this and the
     * player needs to be looking at it before they do.
     */
    private static final int CRAWL = 5;
    private static final int CRAWL_SEED_TICKS = 10;
    private static final float CRAWL_ALPHA = 0.22F;

    public GroundMistRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(GroundMistEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float density = entity.density(partialTick);
        if (density <= 0.001F) {
            return;
        }
        float spread = entity.spread();
        boolean air = entity.isAirborne();
        float age = entity.tickCount + partialTick;

        // THE LAYOUT IS RESEEDED FROM THE ENTITY ID EVERY FRAME, which is the
        // cheap way to have a couple of dozen fixed positions without storing
        // any of them: same seed, same scatter, and two clouds in the same
        // fight are never the same cloud.
        RandomSource rnd = RandomSource.create(entity.getId() * 0x9E3779B9L);

        // ---- what is going off inside it right now
        int window = (int) (age / STRIKE_EVERY);
        RandomSource bolt = RandomSource.create(entity.getId() * 31L + window);
        float at = bolt.nextFloat() * (STRIKE_EVERY - STRIKE_LEN);
        float since = age % STRIKE_EVERY - at;
        float flash = since >= 0.0F && since < STRIKE_LEN
                ? (1.0F - since / STRIKE_LEN) * (0.55F + bolt.nextFloat() * 0.45F)
                : 0.0F;
        // where in the volume it went off
        float bx = (bolt.nextFloat() - 0.5F) * spread * 1.4F;
        float by = bolt.nextFloat() * (air ? spread * 0.5F : spread * 0.2F);
        float bz = (bolt.nextFloat() - 0.5F) * spread * 1.4F;

        Quaternionf cam = this.entityRenderDispatcher.cameraOrientation();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(SHEET));

        // it GATHERS rather than appearing at size - a cloud born full width
        // reads as a decal switched on
        float grow = 0.55F + 0.45F * Math.min(1.0F, age / 30.0F);

        for (int i = 0; i < PUFFS; i++) {
            // ---- position in the mass. Flat and wide on the floor, domed and
            //      deep in the air; the square root spreads them evenly by AREA
            //      rather than crowding the middle.
            float a = rnd.nextFloat() * Mth.TWO_PI;
            float r = Mth.sqrt(rnd.nextFloat());
            float hx = Mth.cos(a) * r * spread * grow;
            float hz = Mth.sin(a) * r * spread * grow;
            float lift = rnd.nextFloat();
            // THE FLOOR BANK IS DEEP, not a carpet.
            //
            // At a sixth of its own width it came up to a man's knees, which
            // is fog lying on a floor - correct for what this was first built
            // for and wrong for what it is now. The boulders come up out of a
            // storm that the king is STANDING IN THE MIDDLE OF, and standing
            // in the middle of something means it is over your head. At three
            // tenths and nine metres across it closes over a four-block boss
            // with room to spare, which is the read: he goes into his own
            // weather and the stones come out of it.
            float hy = air
                    // a dome: deepest in the middle, thinning at the rim
                    ? (0.15F + lift * 0.85F) * spread * 0.42F * (1.0F - r * 0.55F)
                    // a bank: deepest in the middle for the same reason, so
                    // the rim still lies down on the floor and only the body
                    // of it rises
                    : lift * spread * 0.34F * (1.0F - r * 0.45F);

            // ---- its own slow drift, so the mass boils instead of sitting
            float drift = age * (0.012F + rnd.nextFloat() * 0.018F) + i;
            hx += Mth.sin(drift) * spread * 0.07F;
            hz += Mth.cos(drift * 0.83F) * spread * 0.07F;
            hy += Mth.sin(drift * 1.31F) * spread * 0.025F;

            float size = spread * (0.42F + rnd.nextFloat() * 0.34F);
            float spin = rnd.nextFloat() * Mth.TWO_PI + age * (rnd.nextFloat() - 0.5F) * 0.01F;

            // ---- how deep into the cloud it sits. The crown catches the
            //      light; anything under the middle is in its own shadow.
            float depth = air
                    ? Mth.clamp(hy / Math.max(0.5F, spread * 0.42F), 0.0F, 1.0F)
                    : Mth.clamp(hy / Math.max(0.3F, spread * 0.34F), 0.0F, 1.0F);
            float tone = 0.46F + 0.54F * depth;

            // storm blue-grey. Dark enough to be weather, cold enough to be his.
            float cr = 0.20F * tone;
            float cg = 0.25F * tone;
            float cb = 0.37F * tone;

            // ---- the strike, falling off with distance through the volume
            int light = packedLight;
            if (flash > 0.0F) {
                float dist = Mth.sqrt((hx - bx) * (hx - bx) + (hy - by) * (hy - by)
                        + (hz - bz) * (hz - bz));
                float near = Math.max(0.0F, 1.0F - dist / Math.max(1.0F, spread * 0.9F));
                float lit = flash * near * near;
                if (lit > 0.01F) {
                    cr += (0.72F - cr) * lit;
                    cg += (0.86F - cg) * lit;
                    cb += (1.00F - cb) * lit;
                    // it is the cloud that is glowing, so the cloud emits
                    light = 0xF000F0;
                }
            }

            // ---- alpha. Thin at the rim so the mass has a soft outside, and
            //      HEAVY through the body: this is a storm, and a storm you can
            //      read the far wall through is a haze with a name. Thirty-four
            //      lumps at this alpha stack five or six deep in the middle,
            //      which is where "ledwo co widac" comes from - not from any
            //      single puff being opaque, but from the count and the depth.
            float edge = 1.0F - r * 0.38F;
            float alpha = density * 0.88F * edge;
            if (alpha <= 0.004F) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(hx, hy, hz);
            poseStack.mulPose(cam);
            poseStack.mulPose(new Quaternionf().rotateZ(spin));
            puff(vc, poseStack.last().pose(), size, cr, cg, cb, alpha, light);
            poseStack.popPose();
        }

        // ================================================================
        // THE CURRENT RUNNING THROUGH IT.
        //
        // Brightening the lumps says a storm is going off SOMEWHERE and leaves
        // the eye to take it on trust. Filaments struck between two points
        // inside the volume say it went off HERE, and they are the difference
        // between weather-coloured fog and weather.
        //
        // The path is a walk from one end to the other with every waypoint
        // thrown off the straight line, anchored at both tips and wild in
        // between - the same shape the ground arcs use, because it is the
        // shape lightning has. The jaggedness is in the geometry rather than
        // in the texture: kinks painted into a sheet repeat on every segment
        // and the whole thing comes out looking like chain-link.
        // ================================================================
        // The camera in the entity's own frame - both the strike and the crawl
        // turn their filaments edge-on to it, so it is computed once here
        // rather than inside either block.
        Vec3 eye = this.entityRenderDispatcher.camera.getPosition()
                .subtract(entity.getX(), entity.getY(), entity.getZ());

        if (flash > 0.02F) {
            VertexConsumer arc = bufferSource.getBuffer(RenderType.entityTranslucent(ARC));
            for (int b = 0; b < BOLTS; b++) {
                RandomSource path = RandomSource.create(
                        entity.getId() * 131L + window * 17L + b);
                boltPath(arc, poseStack, path, bx, by, bz, spread, air, eye,
                         flash * 0.9F, spread * 0.045F);
            }
        }

        // ---- AND THE CRAWL, every frame, flash or no flash.
        {
            VertexConsumer arc = bufferSource.getBuffer(
                    RenderType.entityTranslucent(ARC));
            long seed = (long) (age / CRAWL_SEED_TICKS);
            for (int b = 0; b < CRAWL; b++) {
                RandomSource path = RandomSource.create(
                        entity.getId() * 977L + seed * 41L + b);
                // each one wanders between two points INSIDE the mass rather
                // than from the strike point, so the charge reads as being
                // spread through the volume instead of radiating from a spot
                float ox = (path.nextFloat() - 0.5F) * spread * 1.1F;
                float oy = path.nextFloat() * spread * (air ? 0.40F : 0.16F);
                float oz = (path.nextFloat() - 0.5F) * spread * 1.1F;
                // fade each filament in and out across its own seed window, so
                // they do not all pop together when the seed rolls over
                float ph = (age % CRAWL_SEED_TICKS) / (float) CRAWL_SEED_TICKS;
                float a2 = Mth.sin(ph * Mth.PI) * CRAWL_ALPHA;
                boltPath(arc, poseStack, path, ox, oy, oz, spread * 0.55F, air,
                         eye, a2, spread * 0.022F);
            }
        }
    }

    /**
     * One filament, walked from a point out into the mass.
     *
     * <p>Pulled out of the strike loop so the crawl can use the same geometry:
     * two paths that look different because they are drawn differently is how
     * a cloud ends up with two unrelated kinds of lightning in it.
     */
    private void boltPath(VertexConsumer arc, PoseStack poseStack, RandomSource path,
                          float bx, float by, float bz, float spread, boolean air,
                          Vec3 eye, float alpha, float width) {
        if (alpha <= 0.01F) {
            return;
        }
        {
                float reach = spread * (air ? 1.1F : 1.3F);
                Vec3 from = new Vec3(bx, by, bz);
                Vec3 to = new Vec3(
                        (path.nextFloat() - 0.5F) * reach,
                        air ? path.nextFloat() * spread * 0.45F
                            : path.nextFloat() * spread * 0.14F,
                        (path.nextFloat() - 0.5F) * reach);
                Vec3 prev = from;
                Matrix4f m = poseStack.last().pose();
                for (int stp = 1; stp <= BOLT_STEPS; stp++) {
                    float k = stp / (float) BOLT_STEPS;
                    // anchored at both ends, loosest in the middle
                    float wild = Mth.sin(k * Mth.PI) * spread * 0.22F;
                    Vec3 next = from.lerp(to, k).add(
                            (path.nextFloat() - 0.5F) * wild,
                            (path.nextFloat() - 0.5F) * wild * 0.7F,
                            (path.nextFloat() - 0.5F) * wild);
                    segment(arc, m, prev, next, eye, width, alpha);
                    // ---- IT FORKS. A filament that runs from A to B without
                    //      branching is a wire; the branch is what says the
                    //      charge is looking for a way out and taking several.
                    if (stp > 2 && stp < BOLT_STEPS - 1 && path.nextFloat() < 0.30F) {
                        Vec3 off = next.add(
                                (path.nextFloat() - 0.5F) * spread * 0.30F,
                                (path.nextFloat() - 0.5F) * spread * 0.18F,
                                (path.nextFloat() - 0.5F) * spread * 0.30F);
                        segment(arc, m, next, off, eye, width * 0.6F, alpha * 0.7F);
                    }
                    prev = next;
                }
            }
    }

    /** One length of filament, turned edge-on to the camera. */
    private static void segment(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b,
                                Vec3 eye, float width, float alpha) {
        Vec3 along = b.subtract(a);
        if (along.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 mid = a.add(b).scale(0.5D);
        Vec3 side = along.cross(eye.subtract(mid));
        if (side.lengthSqr() < 1.0E-8D) {
            return;
        }
        side = side.normalize().scale(width);
        bolt(vc, m, a.subtract(side), 0.0F, 0.0F, alpha);
        bolt(vc, m, b.subtract(side), 1.0F, 0.0F, alpha);
        bolt(vc, m, b.add(side), 1.0F, 1.0F, alpha);
        bolt(vc, m, a.add(side), 0.0F, 1.0F, alpha);
    }

    private static void bolt(VertexConsumer vc, Matrix4f m, Vec3 p,
                             float u, float v, float alpha) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z)
                .setColor(0.86F, 0.96F, 1.0F, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /** One lump, square to the camera. */
    private static void puff(VertexConsumer vc, Matrix4f m, float s,
                             float r, float g, float b, float alpha, int light) {
        float[][] corners = {{-s, -s, 0.0F, 1.0F}, {s, -s, 1.0F, 1.0F},
                             {s, s, 1.0F, 0.0F}, {-s, s, 0.0F, 0.0F}};
        for (float[] c : corners) {
            vc.addVertex(m, c[0], c[1], 0.0F)
                    .setColor(r, g, b, alpha)
                    .setUv(c[2], c[3])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(0.0F, 0.0F, 1.0F);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(GroundMistEntity entity) {
        return SHEET;
    }
}
