package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The wake a body leaves when it crosses a room faster than the eye follows.
 *
 * <p>WHY THIS IS NOT AFTERIMAGES. Dropping a copy of the model every few ticks
 * gives you a row of Velkhars standing still along the path, and a row of
 * stationary copies says "he was in these places" - which reads as teleporting
 * between them. A continuous surface through the same path says "he went from
 * here to there", which is the thing actually being claimed. Same information,
 * opposite impression, and the difference is entirely whether the samples are
 * joined up.
 *
 * <p>It is the blade trail's construction applied to the whole man: two points
 * instead of hilt and tip - one at the shoulders, one at the boots - so the
 * ribbon has his height rather than his edge.
 *
 * <p>THE SAMPLES ARE IN THE ENTITY'S OWN SPACE. This cost two rounds on the
 * blade trail and is worth writing down twice: the pose stack inside an entity
 * renderer already carries the camera-to-entity translation, so storing raw
 * matrices across frames leaves the older ones measured from wherever the
 * camera used to be. The ribbon then stretches between the model and a point
 * however far the player has walked since.
 */
public class MotionTrailLayer extends GeoRenderLayer<VelkharEntity> {

    /** Reused: the slash sheet already fades from a hot core to nothing. */
    private static final ResourceLocation SHEET =
            FrozenFortress.id("textures/entity/velkhar_slash.png");

    private static final int SPAN = 10;
    /** Head and heel of the ribbon, in blocks above the entity origin. */
    private static final float TOP = 3.4f;
    private static final float FOOT = 0.15f;

    private record Sample(float x, float y, float z) {}

    private static final Map<VelkharEntity, Deque<Sample>> HISTORY = new WeakHashMap<>();

    public MotionTrailLayer(GeoRenderer<VelkharEntity> renderer) {
        super(renderer);
    }

    /**
     * The moves fast enough to need one. Everything else must NOT have it -
     * a wake on an ordinary step is what makes a boss look like he is
     * permanently sprinting, and then the wake stops meaning speed.
     */
    private static boolean streaking(VelkharEntity boss) {
        if (boss.evadeKind() != 0 && boss.evadeKind() != 4) {
            return true;                       // dodges and backsteps, not parries
        }
        // Phase two is the dynamic warrior - every combo whips the blade fast
        // enough to leave a streak, and none of them were on this list, which
        // is why the trail "stopped" once the fight moved past phase one. All
        // the phase-two attacks added: the combos, the fury, the whirl, and the
        // sky/grave/phantom set.
        return switch (boss.getAttackState()) {
            case VelkharEntity.GLACIAL_CATACLYSM,
                 VelkharEntity.BERSERK_DIVE,
                 VelkharEntity.DASH_SLASH,
                 VelkharEntity.LUNGE_STRIKE,
                 VelkharEntity.DASH_CUT,
                 VelkharEntity.HIP_FIST,
                 VelkharEntity.SPIN_CUT,
                 VelkharEntity.PHANTOM_EDGE,
                 VelkharEntity.GRAVE_BLADE,
                 VelkharEntity.SKYHOOK,
                 VelkharEntity.WHIRLWIND,
                 VelkharEntity.FURY,
                 VelkharEntity.COMBO,
                 VelkharEntity.TELEPORT_STRIKE -> true;
            default -> false;
        };
    }

    @Override
    public void render(PoseStack poseStack, VelkharEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        // THE STREAK OUTLIVES THE MOVEMENT. This deleted the history on the
        // frame the dash ended, which throws the trail away at the exact
        // moment it is most visible - a real motion streak is something you
        // notice AFTER the thing has stopped, hanging in the air where it
        // was. Cutting it at the stop meant the ribbon only ever existed
        // during the fastest part of the move, where the eye is tracking the
        // body and not the space behind it.
        //
        // So when he stops streaking the history DRAINS instead: one sample
        // leaves per frame and the whole ribbon fades as it goes, which is
        // the ninja read - the shape stays for a moment and peels away from
        // the tail.
        boolean live = streaking(animatable);
        Deque<Sample> existing = HISTORY.get(animatable);
        if (!live) {
            if (existing == null || existing.isEmpty()) {
                return;
            }
            existing.removeLast();
            if (existing.size() < 3) {
                HISTORY.remove(animatable);
                return;
            }
            drawRibbon(animatable, existing, poseStack, bufferSource, partialTick,
                       existing.size() / (float) SPAN);
            return;
        }

        // Entity-local: the position of the model's origin relative to where it
        // is being drawn this frame is (0,0,0), so a history of world offsets
        // has to be built from the entity's own motion rather than from the
        // pose stack. Interpolated so the ribbon is smooth between ticks.
        double ix = net.minecraft.util.Mth.lerp(partialTick, animatable.xOld, animatable.getX());
        double iy = net.minecraft.util.Mth.lerp(partialTick, animatable.yOld, animatable.getY());
        double iz = net.minecraft.util.Mth.lerp(partialTick, animatable.zOld, animatable.getZ());

        Deque<Sample> hist = HISTORY.computeIfAbsent(animatable, k -> new ArrayDeque<>());
        hist.addFirst(new Sample((float) ix, (float) iy, (float) iz));
        while (hist.size() > SPAN) {
            hist.removeLast();
        }
        if (hist.size() < 2) {
            return;
        }

        drawRibbon(animatable, hist, poseStack, bufferSource, partialTick, 1.0f);
    }

    /**
     * Draw the ribbon from a history of world positions.
     *
     * <p>{@code strength} scales the whole thing, so the drain after a dash can
     * fade out as one object rather than each segment guttering on its own.
     */
    private void drawRibbon(VelkharEntity animatable, Deque<Sample> hist,
                            PoseStack poseStack, MultiBufferSource bufferSource,
                            float partialTick, float strength) {
        List<Sample> s = new ArrayList<>(hist);
        if (s.size() < 2) {
            return;
        }
        // Anchored to where he is NOW rather than to where the ribbon started,
        // because the pose stack has already been translated to him - the two
        // rounds this cost the first time were exactly this mistake.
        double ix = net.minecraft.util.Mth.lerp(partialTick, animatable.xOld, animatable.getX());
        double iy = net.minecraft.util.Mth.lerp(partialTick, animatable.yOld, animatable.getY());
        double iz = net.minecraft.util.Mth.lerp(partialTick, animatable.zOld, animatable.getZ());
        VertexConsumer vc =
                bufferSource.getBuffer(RenderType.entityTranslucentEmissive(SHEET));
        Matrix4f m = poseStack.last().pose();

        for (int i = 0; i + 1 < s.size(); i++) {
            Sample a = s.get(i);
            Sample b = s.get(i + 1);
            float ax = (float) (a.x() - ix), ay = (float) (a.y() - iy), az = (float) (a.z() - iz);
            float bx = (float) (b.x() - ix), by = (float) (b.y() - iy), bz = (float) (b.z() - iz);
            float f0 = 1.0f - i / (float) SPAN;
            float f1 = 1.0f - (i + 1) / (float) SPAN;
            // squared, so the tail thins away rather than ending on an edge
            quad(vc, m, ax, ay, az, bx, by, bz,
                 f0 * f0 * strength, f1 * f1 * strength, 0xF000F0);
        }
    }

    private void quad(VertexConsumer vc, Matrix4f m,
                      float ax, float ay, float az, float bx, float by, float bz,
                      float f0, float f1, int light) {
        vert(vc, m, ax, ay + TOP, az, 0.0f, 0.0f, f0, light);
        vert(vc, m, bx, by + TOP, bz, 1.0f, 0.0f, f1, light);
        vert(vc, m, bx, by + FOOT, bz, 1.0f, 1.0f, f1, light);
        vert(vc, m, ax, ay + FOOT, az, 0.0f, 1.0f, f0, light);
    }

    private void vert(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                      float u, float v, float fade, int light) {
        vc.vertex(m, x, y, z)
                .color(0.80f, 0.93f, 1.0f, fade * 0.45f)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(m.normal(new org.joml.Matrix3f()), 0.0f, 1.0f, 0.0f)
                .endVertex();
    }
}
