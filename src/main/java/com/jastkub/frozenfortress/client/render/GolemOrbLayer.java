package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * THE BOMB, FORMING IN ITS FIST - and it is solid.
 *
 * <p>The charge used to be particles: latitude bands of motes placed on a
 * sphere, counter-rotating, with more of them as it filled. That is about as
 * far as particles go toward being an object and it still was not one - motes
 * have no surface, so there was nothing to catch the light, nothing to occlude
 * the hand behind it, and at any distance the whole thing dissolved into a
 * smudge. A thing being BUILT has to be solid or it is just weather.
 *
 * <p>So it is three nested boxes, drawn at the hand bone, counter-rotating and
 * growing on the square of the charge. Not a model lookup - raw quads, because
 * the shape is four numbers and loading a second geometry to draw a lump would
 * cost more than it saves.
 *
 * <p>The growth curve is deliberate. Linear growth reads as a progress bar;
 * the square creeps for the first second and then swells, which is what
 * something being forced into existence looks like and, more usefully, means
 * the last half second before it is thrown is the half second that LOOKS
 * dangerous.
 *
 * <p>The core is emissive and always bigger than the shell is thick, so the
 * light is visible from the first frame - before there is enough ice around it
 * to have an outline at all.
 */
public class GolemOrbLayer extends GeoRenderLayer<HollowGolemEntity> {

    private static final ResourceLocation SKIN =
            FrozenFortress.id("textures/entity/golem_shard.png");
    private static final ResourceLocation CORE =
            FrozenFortress.id("textures/entity/bomb_glow.png");

    /** The hand it charges in. The clip raises the right arm; see shardPalm. */
    private static final String HAND = "hand_r";

    public GolemOrbLayer(GeoRenderer<HollowGolemEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poses, HollowGolemEntity golem, GeoBone bone,
                              RenderType type, MultiBufferSource buffers,
                              VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!HAND.equals(bone.getName())) {
            return;
        }
        float charge = golem.orb();
        if (charge <= 0.002F) {
            return;
        }
        float age = golem.tickCount + partialTick;
        // ---- BONE SPACE IS BLOCKS, NOT MODEL UNITS.
        //
        // This is the whole of "a huge shell of block textures appears while
        // the bomb charges". GeckoLib divides every pivot by sixteen before it
        // hands the matrix over, so one unit in here is one BLOCK - which is
        // why BladeTrailLayer writes its tip as -46.6f / 16.0f rather than as
        // -46.6f. These numbers were written as model units and never divided,
        // so a ball meant to be half a block across was drawn at a half-extent
        // of eight blocks: a sixteen-metre cube of ice texture standing around
        // the golem, which at that size reads as scenery rather than as a
        // sphere. (The dead line that used to sit here, multiplying by 16,
        // 0.0625, 16 and then by zero, was someone arriving at the same
        // suspicion and leaving the attempt behind.)
        //
        // Divided, the ball tops out near half a block across, inside the
        // 1.65-block shell of motes the entity feeds it with - so the
        // particles now converge on something instead of rattling around
        // inside it.
        float r = (0.9F + charge * charge * 7.4F) / 16.0F;
        poses.pushPose();
        // ---- AT THE HAND, WHICH MEANS AT THE HAND'S PIVOT.
        //
        // The /16 above fixed the SIZE and left the PLACE wrong, and the
        // reason is the same misreading once removed: GeckoLib's matrix prep
        // ends by translating back OUT of the bone's pivot (read off its
        // bytecode - prepMatrixForBone finishes on translateAwayFromPivot
        // Point), so the origin in here is the MODEL's origin, the golem's
        // feet, turned with the arm. An offset of a few sixteenths from there
        // put the ball under the golem, not in its fist.
        //
        // Read off the bone rather than written in, because the model has been
        // edited by hand since it was generated: the pivot is wherever the
        // wrist actually is. Then a little below and in front, into the palm.
        poses.translate(bone.getPivotX() / 16.0F,
                (bone.getPivotY() - 4.5F) / 16.0F,
                (bone.getPivotZ() - 2.0F) / 16.0F);

        VertexConsumer ice = buffers.getBuffer(RenderType.entityTranslucent(SKIN));
        shell(poses, ice, r, age * 2.3F, age * 1.5F, 0.0F, packedLight);
        shell(poses, ice, r * 0.72F, -age * 1.9F, age * 2.7F, 24.0F, packedLight);

        VertexConsumer lit = buffers.getBuffer(RenderType.entityTranslucentEmissive(CORE));
        shell(poses, lit, r * (0.40F + 0.10F * (float) Math.sin(age * 0.5F)),
              age * 3.4F, -age * 2.2F, 12.0F, 0xF000F0);
        poses.popPose();
    }

    /** One box, turned. Six quads and nothing clever. */
    private static void shell(PoseStack poses, VertexConsumer vc, float r,
                              float yaw, float pitch, float roll, int light) {
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(yaw));
        poses.mulPose(Axis.XP.rotationDegrees(pitch));
        poses.mulPose(Axis.ZP.rotationDegrees(roll));
        Matrix4f m = poses.last().pose();
        PoseStack.Pose n = poses.last();
        // (nx, ny, nz) then the two in-plane axes for that face
        float[][] faces = {
                {0, 0, 1, 1, 0, 0, 0, 1, 0}, {0, 0, -1, -1, 0, 0, 0, 1, 0},
                {1, 0, 0, 0, 0, -1, 0, 1, 0}, {-1, 0, 0, 0, 0, 1, 0, 1, 0},
                {0, 1, 0, 1, 0, 0, 0, 0, -1}, {0, -1, 0, 1, 0, 0, 0, 0, 1},
        };
        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) {
                float u = (i == 0 || i == 3) ? -1 : 1;
                float v = (i < 2) ? -1 : 1;
                float x = (f[0] + f[3] * u + f[6] * v) * r;
                float y = (f[1] + f[4] * u + f[7] * v) * r;
                float z = (f[2] + f[5] * u + f[8] * v) * r;
                vc.addVertex(m, x, y, z)
                        .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                        .setUv(i == 0 || i == 3 ? 0.0F : 1.0F, i < 2 ? 0.0F : 1.0F)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(n, f[0], f[1], f[2]);
            }
        }
        poses.popPose();
    }
}
