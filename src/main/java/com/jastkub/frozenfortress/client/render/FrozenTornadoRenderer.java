package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.FrozenTornadoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the frozen tornado as an actual funnel of stacked, spinning rings
 * rather than leaving it to particles - a column this dangerous has to be
 * unmissable from across the arena.
 */
public class FrozenTornadoRenderer extends EntityRenderer<FrozenTornadoEntity> {

    private static final ResourceLocation TEXTURE =
            FrozenFortress.id("textures/entity/frozen_tornado.png");

    /**
     * PLATES, NOT A FUNNEL.
     *
     * <p>It was fourteen eight-sided rings blended into a smooth cone, which
     * is a good way to draw a tornado and the wrong way to draw THIS one. A
     * smooth funnel is air; the arena is full of ice, and everything in it -
     * the spikes, the boulders, the shards he throws - is faceted and stepped.
     * A round column in the middle of that reads as belonging to a different
     * mod.
     *
     * <p>Three changes, and they all say the same thing:
     *
     *     FIVE SIDES, not eight. Low enough that every plate is plainly a
     *     flat surface with hard edges between them. Eight is the count you
     *     pick when you want round-looking and cheap; five is the count you
     *     pick when you want the corners SEEN.
     *
     *     EVERY LAYER TURNED OFF THE ONE BELOW. Each ring is offset by a
     *     fraction of a facet, so the vertical edges never line up into long
     *     seams - the column is a twisted stack of pentagons rather than a
     *     five-sided tube, and the twist is what makes it read as turning
     *     even before anything animates.
     *
     *     AND THE PROFILE STEPS. The radius runs on a sawtooth over the
     *     smooth taper, so some layers jut out past the ones above and below.
     *     That is the whole difference between a cone and a stack: a cone has
     *     one silhouette, a stack has a notch at every layer.
     */
    private static final int RINGS = 11;
    private static final float HEIGHT = 6.0F;
    private static final float TOP_RADIUS = 2.6F;
    private static final float BOTTOM_RADIUS = 0.45F;
    /** Sides per ring. Five, so the facets are unmistakably facets. */
    private static final int SIDES = 5;
    /** How far each layer is turned relative to the one under it. */
    private static final float LAYER_TWIST = 0.42F;
    /** How far the stepped layers jut past the smooth taper. */
    private static final float LAYER_STEP = 0.26F;

    public FrozenTornadoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(FrozenTornadoEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(FrozenTornadoEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        return true;   // it is taller than its hitbox; never cull it
    }

    @Override
    public void render(FrozenTornadoEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float time = entity.tickCount + partialTick;
        // fade in as it forms and out as it dies, so it never pops
        float age = time / (float) FrozenTornadoEntity.LIFETIME;
        float fade = Mth.clamp(Math.min(time / 12.0F, (1.0F - age) * 6.0F), 0.0F, 1.0F);
        if (fade <= 0.01F) {
            return;
        }

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));
        poseStack.pushPose();
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f nrm = pose.normal();

        for (int ring = 0; ring < RINGS; ring++) {
            float f0 = ring / (float) RINGS;
            float f1 = (ring + 1) / (float) RINGS;
            float y0 = f0 * HEIGHT;
            float y1 = f1 * HEIGHT;
            // the funnel widens toward the top, with a slight flare
            float r0 = Mth.lerp((float) Math.pow(f0, 0.7), BOTTOM_RADIUS, TOP_RADIUS);
            float r1 = Mth.lerp((float) Math.pow(f1, 0.7), BOTTOM_RADIUS, TOP_RADIUS);
            // AND THEN IT STEPS. Alternate layers jut out past the taper, so
            // the silhouette has a notch at every joint instead of one
            // continuous slope. Applied to the layer rather than interpolated
            // across it - a step that fades in is a bevel.
            float jut = (ring % 2 == 0 ? 1.0F : -1.0F) * LAYER_STEP;
            // the widest plates sit low, where the column is thickest and the
            // steps have room to read
            jut *= 1.0F - f0 * 0.45F;
            r0 += jut;
            r1 += jut;
            // each slice lags the one below it, which is what sells the spin
            float spin0 = time * 0.55F - f0 * 2.6F;
            float spin1 = time * 0.55F - f1 * 2.6F;
            // AND EACH LAYER IS TURNED OFF THE LAST. Without this the facet
            // edges stack into five long seams running the height of the
            // column, which is the one thing that would make a five-sided
            // stack read as a five-sided tube.
            spin0 += ring * LAYER_TWIST;
            spin1 += ring * LAYER_TWIST;
            // and the whole column leans as it turns
            float lean0 = Mth.sin(time * 0.09F + f0 * 1.4F) * f0 * 0.9F;
            float lean1 = Mth.sin(time * 0.09F + f1 * 1.4F) * f1 * 0.9F;

            // alternate layers are a shade denser, so the stack is readable
            // as layers even head-on, where the stepped profile is edge-on
            // and contributes nothing
            float alpha = fade * (0.30F + 0.42F * (1.0F - f0))
                    * (ring % 2 == 0 ? 1.0F : 0.78F);
            float v0 = -time * 0.06F + f0 * 2.0F;
            float v1 = -time * 0.06F + f1 * 2.0F;

            for (int s = 0; s < SIDES; s++) {
                float a0 = (float) (Math.PI * 2.0 * s / SIDES);
                float a1 = (float) (Math.PI * 2.0 * (s + 1) / SIDES);
                float u0 = s / (float) SIDES;
                float u1 = (s + 1) / (float) SIDES;

                float x00 = Mth.cos(a0 + spin0) * r0 + lean0;
                float z00 = Mth.sin(a0 + spin0) * r0;
                float x01 = Mth.cos(a1 + spin0) * r0 + lean0;
                float z01 = Mth.sin(a1 + spin0) * r0;
                float x10 = Mth.cos(a0 + spin1) * r1 + lean1;
                float z10 = Mth.sin(a0 + spin1) * r1;
                float x11 = Mth.cos(a1 + spin1) * r1 + lean1;
                float z11 = Mth.sin(a1 + spin1) * r1;

                // both faces, so the funnel is solid seen from inside too
                quad(buffer, mat, nrm, x00, y0, z00, x01, y0, z01,
                        x11, y1, z11, x10, y1, z10, u0, u1, v0, v1, alpha);
                quad(buffer, mat, nrm, x01, y0, z01, x00, y0, z00,
                        x10, y1, z10, x11, y1, z11, u0, u1, v0, v1, alpha);
            }
        }
        poseStack.popPose();
    }

    private void quad(VertexConsumer buffer, Matrix4f mat, Matrix3f nrm,
                      float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz,
                      float u0, float u1, float v0, float v1, float alpha) {
        vertex(buffer, mat, nrm, ax, ay, az, u0, v0, alpha);
        vertex(buffer, mat, nrm, bx, by, bz, u1, v0, alpha);
        vertex(buffer, mat, nrm, cx, cy, cz, u1, v1, alpha);
        vertex(buffer, mat, nrm, dx, dy, dz, u0, v1, alpha);
    }

    private void vertex(VertexConsumer buffer, Matrix4f mat, Matrix3f nrm,
                        float x, float y, float z, float u, float v, float alpha) {
        buffer.vertex(mat, x, y, z)
                .color(0.78F, 0.92F, 1.0F, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(nrm, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
