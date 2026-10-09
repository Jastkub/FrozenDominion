package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.ShieldEchoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Draws the shield's shadow: one image of the gate, growing and pulsing.
 *
 * <p>NOT CAMERA-FACING, which is the one decision here worth arguing about.
 * Every other billboard in this mod turns to face the player, because a shard
 * or a spark has no front. A shield does. If this turned to face the camera it
 * would be a picture of a shield sliding sideways when seen from the flank,
 * and the illusion that a wall of ice is sweeping the room would go with it.
 * So it is locked to the heading it was thrown along and presents its face
 * forward - which means from the side you correctly see it edge-on, thin, the
 * way a real slab passing you would look.
 *
 * <p>THE PULSE IS TWO BEATS AGAINST EACH OTHER. Scale swells smoothly the
 * whole way out, while brightness beats about three times over the flight. One
 * rhythm alone reads as a fade; two out of phase read as something alive under
 * pressure, which is what a ward being driven outward should look like.
 *
 * <p>Drawn double-sided, because the wave passes THROUGH the player and the
 * back of it has to still be there on the way out.
 */
public class ShieldEchoRenderer extends EntityRenderer<ShieldEchoEntity> {

    private static final ResourceLocation ECHO =
            FrozenFortress.id("textures/entity/velkhar_shield_echo.png");

    /** Half-height in blocks at birth, and how much it gains over its life. */
    private static final float H0 = 1.55F;
    private static final float H_GROW = 3.30F;
    /** A heater shield is taller than it is wide; keep that ratio while it grows. */
    private static final float ASPECT = 0.74F;

    public ShieldEchoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ShieldEchoEntity entity) {
        return ECHO;
    }

    @Override
    public void render(ShieldEchoEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        float t = entity.progress(partialTick);
        float flash = entity.flash();

        float h, w, alpha, breathe;
        if (flash > 0.0F) {
            // A BLOW, not a wave. It is born nearly full size and its whole
            // life is the ring leaving - so the growth is fast and front
            // loaded (sqrt, not linear) and the fade starts immediately.
            // Anything slower reads as a projectile being launched, and this
            // is not launched, it is what a struck bell does.
            h = (H0 * 0.85F + H_GROW * 0.55F * (float) Math.sqrt(t)) * (0.72F + 0.55F * flash);
            w = h * ASPECT;
            alpha = 0.95F * flash * (1.0F - t) * (1.0F - t);
            breathe = 1.0F;
        } else {
            h = H0 + H_GROW * t;
            w = h * ASPECT;
            // The beat. Three pulses across the flight, riding on top of a
            // fade that only really bites in the last third - it should look
            // like it is being driven outward, not running out of fuel.
            float beat = 0.78F + 0.22F * Mth.cos(t * Mth.TWO_PI * 3.0F);
            float fadeOut = 1.0F - Mth.clamp((t - 0.62F) / 0.38F, 0.0F, 1.0F);
            alpha = 0.90F * beat * fadeOut;
            // the scale beats with it, a little behind, so the swelling and
            // the brightening are not the same movement
            breathe = 1.0F + 0.06F * Mth.cos(t * Mth.TWO_PI * 3.0F - 0.9F);
        }

        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(-entity.heading()));
        poses.translate(0.0D, h * 0.55D, 0.0D);       // stand it on the floor, not through it

        VertexConsumer buffer =
                buffers.getBuffer(RenderType.entityTranslucentEmissive(ECHO));
        float hw = w * breathe;
        float hh = h * breathe;
        // front face, then the same quad wound the other way so it survives
        // being walked through
        quad(poses.last(), buffer, hw, hh, alpha, false);
        quad(poses.last(), buffer, hw, hh, alpha, true);
        poses.popPose();
    }

    private void quad(PoseStack.Pose pose, VertexConsumer buffer,
                      float hw, float hh, float a, boolean flip) {
        Matrix4f mat = pose.pose();
        PoseStack.Pose nrm = pose;
        float n = flip ? -1.0F : 1.0F;
        if (flip) {
            vertex(buffer, mat, nrm, -hw, -hh, 0.0F, 1.0F, a, n);
            vertex(buffer, mat, nrm, -hw, hh, 0.0F, 0.0F, a, n);
            vertex(buffer, mat, nrm, hw, hh, 1.0F, 0.0F, a, n);
            vertex(buffer, mat, nrm, hw, -hh, 1.0F, 1.0F, a, n);
        } else {
            vertex(buffer, mat, nrm, -hw, -hh, 0.0F, 1.0F, a, n);
            vertex(buffer, mat, nrm, hw, -hh, 1.0F, 1.0F, a, n);
            vertex(buffer, mat, nrm, hw, hh, 1.0F, 0.0F, a, n);
            vertex(buffer, mat, nrm, -hw, hh, 0.0F, 0.0F, a, n);
        }
    }

    private void vertex(VertexConsumer buffer, Matrix4f mat, PoseStack.Pose nrm,
                        float x, float y, float u, float v, float a, float n) {
        buffer.addVertex(mat, x, y, 0.0F)
                // the colour is in the sheet; this only carries the fade, so
                // the ice blue cannot drift out of step with the shield itself
                .setColor(1.0F, 1.0F, 1.0F, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(nrm, 0.0F, 0.0F, n);
    }
}
