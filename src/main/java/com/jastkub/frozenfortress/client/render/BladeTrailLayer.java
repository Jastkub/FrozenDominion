package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The arc the blade leaves behind it.
 *
 * WHY A RIBBON AND NOT PARTICLES. Particles are emitted at a point and then
 * live their own lives, so they say "something happened here". A swing is not
 * a place, it is a PATH - and the thing that sells weight is that the path is
 * continuous and that it lags. So this samples where the edge actually was on
 * each of the last few frames and joins those samples into one surface. What
 * the player reads is the shape of the cut, which is exactly the thing the
 * combos were rebuilt around.
 *
 * WHERE THE SAMPLES COME FROM. The blade's position is not something the
 * entity knows - it is the product of the whole bone chain, and only the
 * renderer ever composes that. So the layer waits for GeckoLib to walk the
 * sword bone, steals the pose matrix at that instant (the same trick
 * MaskMeshLayer uses, and for the same reason), and pushes the hilt and tip
 * through it. Two points per frame, joined to the previous frame's two, is a
 * quad; a few frames of those is the arc.
 *
 * WHY IT IS NOT ALWAYS ON. A trail on an idle weapon is smear. It fades in
 * with the SPEED of the edge, so a wind-up shows nothing, the cut shows
 * everything, and the recovery bleeds out on its own - which means it never
 * has to be told when a combo starts or ends.
 */
public class BladeTrailLayer extends GeoRenderLayer<VelkharEntity> {
    /**
     * ITS OWN SHEET. It used to borrow the beam's inner texture on the theory
     * that "a soft bright core" was the same thing - and measured, that sheet
     * is nearly black at alpha 1 to 39 out of 255, because it is designed to
     * sit INSIDE a bright beam and be multiplied up by everything around it.
     * On its own it is a dark smear you cannot see, which is most of why
     * lowering the speed threshold twice changed nothing.
     */
    private static final ResourceLocation TRAIL =
            FrozenFortress.id("textures/entity/velkhar_slash.png");

    /**
     * Hilt and tip in the sword bone's own space, in blocks.
     *
     * The bone pivots at the hand and its cubes run from -46.4 to +19.1 model
     * units about that pivot, so the edge is the long way down. GeckoLib works
     * in sixteenths, hence the divide.
     */
    private static final float HILT = 0.0f;
    private static final float TIP = -46.6f / 16.0f;

    /**
     * And the same two points on a twin blade, which is a different weapon.
     *
     * <p>It is the same figure as the greatsword now: the pair was rebuilt to
     * his original blade's length, 46.6 below the grip on both. It is kept as
     * its own constant rather than folded into TIP because the two weapons
     * being the same length is a DESIGN choice that could change, and a
     * ribbon silently inheriting the wrong reach is exactly the class of fault
     * that put a streak a third of a blade past the end of the steel when the
     * pair was short.
     */
    private static final float TWIN_TIP = -46.6f / 16.0f;

    /**
     * How many frames of history make the ribbon.
     *
     * <p>FRAMES, NOT TICKS - and that distinction is what was wrong with the
     * thresholds below. This runs once per rendered frame, so at 60fps three
     * of them pass per game tick and the edge moves a THIRD as far between
     * samples as it does between ticks. The old numbers were reasoned about
     * per tick, so on any normal machine the swing never once cleared the bar
     * to start drawing and all the player saw was the impact particles.
     */
    private static final int SPAN = 10;
    /** Below this the edge is loafing and nothing is drawn. Blocks per FRAME. */
    /**
     * EVERY SWING, not just the heaviest one.
     *
     * <p>0.045 was set from the phase-one floor splitter, which throws the tip
     * further and faster than anything else he has. Everything smaller - the
     * thrust, the rising cut, the dash slash, the ripostes - never cleared it,
     * so those attacks came out with no ribbon at all and read as the boss
     * waving a bar of metal. The threshold has to sit under the SLOWEST swing
     * that is still a swing, not under the fastest.
     *
     * <p>It cannot go to zero: an idle weapon drifts a little every frame from
     * the breathing loop, and a trail on an idle weapon is smear. 0.018 is
     * comfortably above that drift and comfortably below a deliberate cut.
     */
    /**
     * THE THRESHOLD MEANS SOMETHING AGAIN.
     *
     * <p>It was dropped to 0.018 twice while chasing a ribbon that was
     * invisible for an entirely different reason - it was being drawn in the
     * camera's space, so no threshold could have helped. Now that the samples
     * are in the entity's own space, the number does exactly what it says, and
     * 0.018 is far too low: the idle loop drifts the tip by more than that, so
     * a ribbon hung off the blade permanently and read as a torch beam.
     *
     * <p>0.075 is above the idle breathe and the walk sway, below any
     * deliberate cut.
     */
    private static final float WAKE = 0.075f;
    /** And here it is at full strength. */
    /** Where the ribbon reaches full strength. Lowered with WAKE so an
     *  ordinary cut arrives at full opacity rather than at a hint of one. */
    /**
     * Where the ribbon reaches full strength.
     *
     * <p>0.30 was set as a pair with the restored WAKE and it is too far
     * above it: an ordinary combo swing lands between the two and comes out
     * at a fraction of its opacity, which is why the trail was there but
     * barely worth having. The gap between WAKE and FULL is what decides how
     * much of a swing's speed range is spent fading IN rather than being
     * seen, and it wants to be narrow - a cut is either happening or it is
     * not.
     */
    private static final float FULL = 0.135f;

    private record Sample(float ax, float ay, float az, float bx, float by, float bz) {}

    /**
     * Weak, because it is keyed on entities that die: a boss that is gone must
     * not keep a tail of geometry alive in a static map.
     */
    private static final Map<VelkharEntity, Deque<Sample>> HISTORY = new WeakHashMap<>();
    /** The off-hand blade keeps its own history, or the two ribbons braid. */
    private static final Map<VelkharEntity, Deque<Sample>> HISTORY_OFF = new WeakHashMap<>();

    private static final Matrix4f POSE = new Matrix4f();
    private static final Matrix4f POSE_OFF = new Matrix4f();
    private static boolean captured;
    private static boolean capturedOff;

    public BladeTrailLayer(GeoRenderer<VelkharEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, VelkharEntity animatable, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight,
                              int packedOverlay) {
        // Nothing is drawn here. This is simply the only moment in the frame
        // when the pose stack IS the sword's space.
        // WHICHEVER BLADE HE IS ACTUALLY HOLDING. Hard-coding "sword" meant
        // the layer went blind the moment he tore it in half: that bone is
        // hidden from then on, so nothing was ever captured and the fastest
        // half of the fight had no streak at all.
        String name = bone.getName();
        if ("sword".equals(name) || "twin_r".equals(name)) {
            POSE.set(poseStack.last().pose());
            captured = true;
        } else if ("twin".equals(name)) {
            POSE_OFF.set(poseStack.last().pose());
            capturedOff = true;
        }
    }

    @Override
    public void render(PoseStack poseStack, VelkharEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        // THE BLADE STREAK IS A COMBO-ONLY EFFECT NOW. It used to fire on any
        // fast blade, phase one included; the ask is that it read as the
        // signature of the phase-two combos, so everything else is dropped even
        // when the tip is moving fast enough.
        boolean dual = animatable.isDualWielding();
        if (animatable.isSwordGone() || !animatable.inComboCast()
                || (!captured && !capturedOff)) {
            HISTORY.remove(animatable);
            HISTORY_OFF.remove(animatable);
            captured = false;
            capturedOff = false;
            return;
        }

        // THE SAMPLES MUST BE IN THE ENTITY'S OWN SPACE, NOT THE CAMERA'S.
        //
        // This was the whole bug behind two complaints that looked unrelated.
        // `poseStack` inside an entity renderer already carries the
        // translation from the CAMERA to the entity, so POSE mapped the blade
        // into camera-relative space - and these samples are kept across
        // frames. Every time the player moved, the older entries were still
        // measured from where the camera used to be, so the ribbon was drawn
        // stretched between the sword and a point however far the player had
        // travelled since. Hence: no slash at the blade, and a long straight
        // streak of light standing in the world.
        //
        // Undoing the root transform before storing makes the samples relative
        // to the model's own origin, a space that does not move under them.
        Matrix4f rootInv = new Matrix4f(poseStack.last().pose()).invert();
        Matrix4f m = poseStack.last().pose();
        // ONE buffer, taken here and not inside the ribbon: a BufferSource
        // keeps a single active builder, so asking it for another render type
        // partway through quietly throws away everything written afterwards.
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(TRAIL));

        float tip = dual ? TWIN_TIP : TIP;
        if (captured) {
            ribbon(vc, m, rootInv, POSE, HISTORY.computeIfAbsent(animatable,
                    k -> new ArrayDeque<>()), tip, packedLight);
        }
        // The off hand only exists as a weapon once the sword is in two.
        if (dual && capturedOff) {
            ribbon(vc, m, rootInv, POSE_OFF, HISTORY_OFF.computeIfAbsent(animatable,
                    k -> new ArrayDeque<>()), tip, packedLight);
        } else {
            HISTORY_OFF.remove(animatable);
        }
        captured = false;
        capturedOff = false;
    }

    /** One blade's worth of ribbon: sample it, measure it, and draw it. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Matrix4f rootInv,
                               Matrix4f pose, Deque<Sample> hist, float tip, int light) {
        Matrix4f local = rootInv.mul(pose, new Matrix4f());
        Vector4f a = local.transform(new Vector4f(0.0f, HILT, 0.0f, 1.0f));
        Vector4f b = local.transform(new Vector4f(0.0f, tip, 0.0f, 1.0f));

        hist.addFirst(new Sample(a.x(), a.y(), a.z(), b.x(), b.y(), b.z()));
        while (hist.size() > SPAN) {
            hist.removeLast();
        }
        if (hist.size() < 3) {
            return;
        }

        List<Sample> s = new ArrayList<>(hist);
        // Speed is measured at the TIP, because that is the part that moves
        // when the shoulder barely does.
        Sample n0 = s.get(0), n1 = s.get(1);
        float dx = n0.bx() - n1.bx(), dy = n0.by() - n1.by(), dz = n0.bz() - n1.bz();
        float speed = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float strength = (speed - WAKE) / (FULL - WAKE);
        if (strength <= 0.0f) {
            return;
        }
        strength = Math.min(1.0f, strength);

        for (int i = 0; i + 1 < s.size(); i++) {
            Sample p = s.get(i), q = s.get(i + 1);
            // Older segments are fainter, so the ribbon has a head and a tail
            // rather than being a flat sheet that pops out of existence.
            float f0 = strength * (1.0f - i / (float) SPAN);
            float f1 = strength * (1.0f - (i + 1) / (float) SPAN);
            quad(vc, m, p, q, f0, f1, light);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Sample p, Sample q,
                             float f0, float f1, int light) {
        // hilt edge -> tip edge, newer frame first, so the sheet is wound the
        // same way every segment and does not flicker on the seams
        vert(vc, m, p.ax(), p.ay(), p.az(), 0.0f, 0.0f, f0, light);
        vert(vc, m, q.ax(), q.ay(), q.az(), 1.0f, 0.0f, f1, light);
        vert(vc, m, q.bx(), q.by(), q.bz(), 1.0f, 1.0f, f1, light);
        vert(vc, m, p.bx(), p.by(), p.bz(), 0.0f, 1.0f, f0, light);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                             float u, float v, float fade, int light) {
        vc.vertex(m, x, y, z)
                // Near-white rather than a blue tint. The sheet already
                // carries the colour, and multiplying a cold sheet by a cold
                // vertex colour twice is what made the ribbon dim: 0.42 red
                // meant the brightest part of the trail could never exceed
                // forty per cent of the red in its own texture.
                .color(0.88f, 0.96f, 1.0f, fade)
                .uv(u, v)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)   // it lights itself; a cut does not take shadow
                .normal(0.0f, 1.0f, 0.0f)
                .endVertex();
    }
}
