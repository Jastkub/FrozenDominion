package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.client.mesh.ObjMesh;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Optional;

/**
 * Draws the mask as a REAL MESH, carried by the bone the cuboid one used.
 *
 * <p>WHY A MESH AT ALL. Bedrock {@code .geo.json} - the format GeckoLib reads
 * - can describe exactly one primitive: an axis-aligned box with an optional
 * rotation. There is no triangle in it and no free vertex, so a chamfer, an
 * undercut or a bevelled recess cannot be written down, only approximated by
 * stacking more boxes. Sixty-two cubes went into the last faceplate and it
 * still read as a staircase, because a staircase is the only thing that
 * format can say. Minecraft has no such limit: its renderer is ordinary
 * OpenGL and {@link VertexConsumer} accepts any triangle handed to it.
 *
 * <p>WHY {@code render} AND NOT {@code renderForBone}. This is the bug that
 * cost three passes and it was not geometry. renderForBone is called from
 * inside the loop that is writing the model's own vertices, and
 * {@code MultiBufferSource.BufferSource} keeps ONE active builder: asking it
 * for a different RenderType part way through ends the builder the model is
 * in the middle of, and what is written afterwards goes nowhere. Every layer
 * in this mod that works - ShieldGlowLayer, InnerLightLayer - draws from
 * {@code render}, after the model pass is finished.
 *
 * <p>The diagnostics are what settled it: the log said the OBJ loaded with
 * 10,423 triangles, that the layer reached the visor bone, that the draw ran,
 * and that a point at the middle of the face landed 4.4 blocks above his feet
 * - which is exactly where his head is. Everything was right except where the
 * vertices were going.
 *
 * <p>The bone still drives it. {@link GeoBone#getModelSpaceMatrix()} carries
 * bone-local coordinates into model space, so head tracking and the
 * third-phase fall come along with no extra machinery, and the mesh is
 * authored bone-local to match.
 */
public class MaskMeshLayer extends GeoRenderLayer<VelkharEntity> {

    private static final ResourceLocation MESH = FrozenFortress.id("models/mesh/velkhar_mask.obj");
    private static final ResourceLocation SKIN =
            FrozenFortress.id("textures/entity/velkhar_mask.png");
    /**
     * The lights, on their own sheet.
     *
     * <p>AutoGlowingGeoLayer works off the geo model's cubes and the mask no
     * longer has any, so the emissive pass is done here. The sheet is
     * transparent except at the two sights, so drawing the whole mesh through
     * it lights exactly those texels and nothing else.
     */
    private static final ResourceLocation GLOW =
            FrozenFortress.id("textures/entity/velkhar_mask_glow.png");

    /** The bone the mask rides. The same one the boxes were on. */
    private static final String BONE = "visor";

    private static Optional<ObjMesh> CACHED;
    private static boolean REPORTED;

    /**
     * The transform that was in force at the visor bone, captured verbatim.
     *
     * <p>CAPTURE AND REPLAY, rather than reconstructing the space. Two
     * reconstructions have failed: renderForBone is the right SPACE but the
     * wrong place to draw, because MultiBufferSource keeps one active builder
     * and asking for another mid-model throws the vertices away; and
     * getModelSpaceMatrix() is relative to the model root while a layer's
     * render() runs at entity level, and the difference is a matrix GeckoLib
     * does not expose. Taking a copy of the matrix where it is correct and
     * restoring it where the buffer works needs no theory about either.
     */
    private static final Matrix4f POSE = new Matrix4f();
    private static final Matrix3f NORMAL = new Matrix3f();
    private static boolean CAPTURED;

    public MaskMeshLayer(GeoRenderer<VelkharEntity> renderer) {
        super(renderer);
    }

    /**
     * The mesh, loaded on first use.
     *
     * <p>Static and lazy: the resource manager is not ready when the renderer
     * is constructed, and the renderer needs the answer before the first frame
     * so it knows whether to hide the cuboid faceplate.
     */
    public static boolean available() {
        if (CACHED == null) {
            CACHED = ObjMesh.load(MESH);
            com.mojang.logging.LogUtils.getLogger().info(
                    "[FF/mask] obj {} -> {}", MESH,
                    CACHED.isPresent() ? (CACHED.get().triangleCount() + " triangles")
                                       : "NOT FOUND");
        }
        return CACHED.isPresent();
    }

    /** Forgets the mesh so a resource reload picks up a new export. */
    public static void invalidate() {
        CACHED = null;
        REPORTED = false;
    }

    @Override
    public void renderForBone(PoseStack poseStack, VelkharEntity animatable, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight,
                              int packedOverlay) {
        // Nothing is drawn here - only remembered. This is the one point in
        // the frame where the pose stack is exactly the space the mesh is
        // authored in, and it is also the one point where drawing does not
        // work, so the two are separated.
        if (BONE.equals(bone.getName())) {
            POSE.set(poseStack.last().pose());
            NORMAL.set(poseStack.last().normal());
            CAPTURED = true;
        }
    }

    @Override
    public void render(PoseStack poseStack, VelkharEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        if (!available()) {
            return;
        }
        // The mask comes off in the third phase. The bone is animated through
        // the fall - that is what makes it drop - so the mesh honours the same
        // condition the cuboid faceplate did, or it would hang in the air
        // after the helm is gone.
        if (animatable.getPhase() >= 3 && !animatable.isDiscardingVisor()) {
            return;
        }
        if (!CAPTURED) {
            return;      // the bone has not been walked yet this frame
        }
        if (!REPORTED) {
            REPORTED = true;
            com.mojang.logging.LogUtils.getLogger().info(
                    "[FF/mask] replaying bone '{}' pose for {} triangles: {}",
                    BONE, CACHED.get().triangleCount(), POSE.toString());
        }

        poseStack.pushPose();
        // the captured state, restored exactly - not multiplied into whatever
        // the stack happens to hold at this point in the frame
        poseStack.last().pose().set(POSE);
        poseStack.last().normal().set(NORMAL);
        // entityCutoutNoCull: the mask has openings cut through it and a
        // one-sided draw would show nothing at all through them
        CACHED.get().render(poseStack,
                bufferSource.getBuffer(RenderType.entityCutoutNoCull(SKIN)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
        // and the sights again, full bright, so they do not go out with the
        // room the way the rest of the helm should
        CACHED.get().render(poseStack,
                bufferSource.getBuffer(RenderType.entityTranslucentEmissive(GLOW)),
                0xF000F0, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
}
