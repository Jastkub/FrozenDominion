package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Light tearing out of him, in beams, while the armour fails.
 *
 * <p>Modelled on the dragon's death and for the same reason it works there:
 * the beams do not describe an explosion, they describe something INSIDE
 * that is too big for its container. A body that flashes has been hit; a body
 * that leaks light in hard straight lines is coming apart from within, and
 * the difference is entirely in whether the light has direction.
 *
 * <p>THE BEAMS ARE FIXED IN PLACE, not random per frame. Each one is seeded
 * from its own index, so beam eleven is in the same place this frame as it
 * was last frame and simply gets longer. Re-rolling the directions every
 * frame - which is the obvious way to write this - produces a strobing ball
 * of noise: the eye cannot track any individual ray, so it reads the whole
 * thing as flicker rather than as light escaping from specific splits in the
 * armour. Fixed rays that GROW are what makes it look like pressure finding
 * its way out of particular cracks.
 *
 * <p>Drawn additive against the sky, with no depth write, so beams behind him
 * still reach the camera - light does not get occluded by the thing it is
 * bursting out of.
 */
public class ShatterBeamLayer extends GeoRenderLayer<VelkharEntity> {

    /** Enough to fill the sky without any two lying on top of each other. */
    private static final int BEAMS = 26;

    // ================================================================
    // THE ANCHORS RIDE HIS BONES.
    //
    // They were three points written in the layer's own space - and a layer's render() is handed the pose BEFORE
    // GeckoLib turns the model to his heading, so "out on the left arm" was a fixed compass point: face him north
    // and the gate's beams came out of the air beside him. Each anchor is now a point on one of his bones, captured
    // in renderForBone (the pose in force at that bone), with the way its plate faces: the beams leave the gate's
    // face, the breastplate, the backplate and the shoulders, follow him as he moves, and lean out of the surface
    // they escape through. While the gate is still on his arm it takes the place of the gauntlet.
    // ================================================================
    private static final String[] BONE = {"shield", "chest", "chest", "pauldron_r", "pauldron_l", "gauntlet_l"};
    /** Where on the bone, in his model's sixteenths, and which way the plate there faces (his model: -Z is front). */
    private static final float[][] AT = {
            {-14.0F, 16.5F, -12.6F}, {0.0F, 44.0F, -9.9F}, {0.0F, 43.0F, 7.4F},
            {17.0F, 55.0F, 0.0F}, {-17.0F, 55.0F, 0.0F}, {-11.0F, 20.0F, -2.0F}};
    private static final float[][] FACING = {
            {0.0F, 0.1F, -1.0F}, {0.0F, 0.15F, -1.0F}, {0.0F, 0.15F, 1.0F},
            {0.6F, 0.8F, 0.0F}, {-0.6F, 0.8F, 0.0F}, {-0.8F, 0.0F, -0.6F}};
    private static final int GATE = 0, GAUNTLET = 5;
    private static final Matrix4f[] POSE = new Matrix4f[BONE.length];
    private static final boolean[] CAPTURED = new boolean[BONE.length];

    static {
        for (int i = 0; i < POSE.length; i++) {
            POSE[i] = new Matrix4f();
        }
    }

    public ShatterBeamLayer(GeoRenderer<VelkharEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, VelkharEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (animatable.shatterGlow() <= 0.5F) {
            return;
        }
        String name = bone.getName();
        for (int i = 0; i < BONE.length; i++) {
            if (BONE[i].equals(name)) {
                POSE[i].set(poseStack.last().pose());
                CAPTURED[i] = true;
            }
        }
    }

    @Override
    public void render(PoseStack poseStack, VelkharEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        try {
            draw(animatable, bufferSource, partialTick);
        } finally {
            java.util.Arrays.fill(CAPTURED, false);
        }
    }

    private void draw(VelkharEntity animatable, MultiBufferSource bufferSource, float partialTick) {
        // THE UPPER HALF OF THE VALUE IS OURS, and below it we draw nothing.
        //
        // The entity ramps one float through two stages. The first half is the
        // cracks taking fire; only in the second does anything come out of
        // them. Starting the beams at zero here is what buys the two seconds
        // in which the armour is visibly FRACTURING and nothing has escaped
        // yet - and that interval is the whole reason the beams land, because
        // a split has to be established as a split before light coming
        // through it means anything. Lit from the first frame, the beams read
        // as an effect switched on rather than as a consequence.
        float glow = (animatable.shatterGlow() - 0.5F) / 0.5F;
        if (glow <= 0.02F) {
            return;
        }
        glow = Math.min(1.0F, glow);
        // the gate while it is on his arm, else the gauntlet; the rest as they were caught
        boolean gate = CAPTURED[GATE] && animatable.getAttackState() == VelkharEntity.P2_TRANSITION
                && animatable.shedStage() < 1 && !animatable.isShieldGone();
        int[] live = new int[BONE.length];
        int n = 0;
        for (int i = 0; i < BONE.length; i++) {
            if (!CAPTURED[i] || (i == GATE && !gate) || (i == GAUNTLET && gate)) {
                continue;
            }
            live[n++] = i;
        }
        if (n == 0) {
            return;
        }

        float time = animatable.tickCount + partialTick;
        RandomSource rnd = RandomSource.create(0x5EEDL);
        VertexConsumer vc = bufferSource.getBuffer(RenderType.lightning());

        for (int i = 0; i < BEAMS; i++) {
            int anchor = live[i % n];
            // Fixed direction per beam - see the class note. Two angles rather
            // than three random components so they spread evenly over the
            // sphere instead of bunching at its poles.
            float yaw = rnd.nextFloat() * Mth.TWO_PI;
            float pitch = (rnd.nextFloat() - 0.5F) * 2.4F;
            float phase = rnd.nextFloat() * 8.0F;
            float rank = rnd.nextFloat();

            // Beams arrive in waves - the ones with a low rank start early and
            // the rest come in as the pressure rises, so the effect BUILDS
            // rather than switching on at full width.
            float on = (glow - rank * 0.55F) / 0.45F;
            if (on <= 0.0F) {
                continue;
            }
            on = Math.min(1.0F, on);

            // a hard, fast flutter on the length: escaping light is not steady
            float jitter = 0.72F + 0.28F * Mth.sin(time * (1.4F + rank * 2.2F) + phase);
            float len = (2.2F + 16.0F * glow * glow) * on * jitter;
            float half = (0.055F + 0.16F * on) * (0.6F + 0.4F * glow);

            // out of the plate: the random ray leant hard along the way the plate faces, in its bone's own space
            float[] f = FACING[anchor];
            Vector3f dir = new Vector3f(Mth.cos(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.sin(yaw) * Mth.cos(pitch))
                    .add(f[0] * 1.3F, f[1] * 1.3F, f[2] * 1.3F);
            Matrix4f m = POSE[anchor];
            Vector3f root = m.transformPosition(new Vector3f(AT[anchor][0], AT[anchor][1], AT[anchor][2])
                    .mul(1.0F / 16.0F));
            m.transformDirection(dir).normalize();
            // as wide as it can be from where it is seen (the camera is the origin of this space)
            Vector3f side = new Vector3f(dir).cross(root).normalize();
            if (!Float.isFinite(side.x())) {
                continue;
            }
            float a = Math.min(1.0F, on * (0.35F + 0.65F * glow));
            // white at the root, ice blue by the tip: a beam that is one
            // colour end to end reads as a painted stick
            quad(vc, root, dir, side, half, len, a);
        }
    }

    private void quad(VertexConsumer vc, Vector3f o, Vector3f d, Vector3f s, float half, float len, float a) {
        float ex = o.x() + d.x() * len, ey = o.y() + d.y() * len, ez = o.z() + d.z() * len;
        // root: hot white, and sitting ON the plate it is escaping through
        vert(vc, o.x() - s.x() * half, o.y() - s.y() * half, o.z() - s.z() * half, 0.96F, 0.99F, 1.0F, a);
        vert(vc, o.x() + s.x() * half, o.y() + s.y() * half, o.z() + s.z() * half, 0.96F, 0.99F, 1.0F, a);
        // tip: cold, and gone
        float t = half * 0.35F;
        vert(vc, ex + s.x() * t, ey + s.y() * t, ez + s.z() * t, 0.35F, 0.78F, 1.0F, 0.0F);
        vert(vc, ex - s.x() * t, ey - s.y() * t, ez - s.z() * t, 0.35F, 0.78F, 1.0F, 0.0F);
    }

    private void vert(VertexConsumer vc, float x, float y, float z, float r, float g, float b, float a) {
        vc.addVertex(x, y, z).setColor(r, g, b, a);
    }
}
