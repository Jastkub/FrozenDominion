package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.ArmourShardEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/**
 * Draws a broken piece of the king's plate - A PIECE OF HIS OWN MODEL.
 *
 * <p>The kind names one box of velkhar.geo.json and one cell of the grid it is cut into (ArmourShardEntity.PIECES,
 * tools/gen_velkhar_armour_pieces.py). The cell is cut out of the box here, corner by corner: every face of it is the
 * matching part of the box's face - its corners and its texels, interpolated - so a chunk of the breastplate carries
 * the breastplate's paint and an edge of the gate its rim. The faces the cut opens up show the plate's own surface,
 * which at a plate's thickness reads as the broken edge.
 *
 * <p>It tumbles in the air (the entity's roll) and, landed, falls flat on its broad face - the cell is turned so its
 * thinnest side is up before the tumble, and the tumble is eased to the nearest flat. Fresh off him it still burns
 * in its cracks (the lit network the break drew, cooling over a second and a half); at the end of its life it sinks
 * through the floor instead of blinking out.
 */
public class ArmourShardRenderer extends EntityRenderer<ArmourShardEntity> {

    /** The sheet he wears when the plate blows (VelkharModel's sovereign one - the skin lags until the break). */
    private static final ResourceLocation SHEET = FrozenFortress.id("textures/entity/velkhar.png");
    private static final ResourceLocation CRACKS = FrozenFortress.id("textures/entity/velkhar_cracks_lit.png");
    private static final ResourceLocation GEO = FrozenFortress.id("geo/entity/velkhar.geo.json");
    /** The bones pieces come from, by the index in their kind (tools/gen_velkhar_armour_pieces.BONES). */
    public static final String[] BONES = {"shield", "plate_torso", "plate_skirt", "plate_pauld_r", "plate_arm_r",
            "plate_arm_l"};
    /** How long a piece still burns in its cracks, ticks. */
    private static final float COOL = 30.0F;

    public ArmourShardRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.2F;
    }

    @Override
    public ResourceLocation getTextureLocation(ArmourShardEntity entity) {
        return SHEET;
    }

    @Override
    public void render(ArmourShardEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        int kind = entity.kind();
        if (!ArmourShardEntity.isPiece(kind)) {
            return;
        }
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(GEO);
        int bi = kind & 7;
        if (model == null || bi >= BONES.length) {
            return;
        }
        GeoBone bone = model.getBone(BONES[bi]).orElse(null);
        int ci = (kind >> 3) & 63;
        if (bone == null || ci >= bone.getCubes().size()) {
            return;
        }
        GeoCube cube = bone.getCubes().get(ci);
        int au = (kind >> 9) & 3, av = (kind >> 11) & 3;
        int nu = Math.max(1, (kind >> 13) & 7), nv = Math.max(1, (kind >> 16) & 7);
        int iu = (kind >> 19) & 7, iv = (kind >> 22) & 7;

        // ---- the box, and the cell of it
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) {
                continue;
            }
            for (GeoVertex v : quad.vertices()) {
                Vector3f p = v.position();
                for (int k = 0; k < 3; k++) {
                    float c = k == 0 ? p.x() : k == 1 ? p.y() : p.z();
                    min[k] = Math.min(min[k], c);
                    max[k] = Math.max(max[k], c);
                }
            }
        }
        if (min[0] > max[0]) {
            return;
        }
        float[] lo = {0.0F, 0.0F, 0.0F};
        float[] hi = {1.0F, 1.0F, 1.0F};
        if (au < 3 && av < 3) {
            lo[au] = iu / (float) nu;
            hi[au] = (iu + 1) / (float) nu;
            lo[av] = iv / (float) nv;
            hi[av] = (iv + 1) / (float) nv;
        }
        float[] size = new float[3];
        float[] mid = new float[3];
        int thin = 0;
        for (int k = 0; k < 3; k++) {
            size[k] = (max[k] - min[k]) * (hi[k] - lo[k]);
            mid[k] = min[k] + (max[k] - min[k]) * (lo[k] + hi[k]) * 0.5F;
            if (size[k] < size[thin]) {
                thin = k;
            }
        }
        float halfThick = size[thin] * 0.5F;

        // ---- where it is and how it lies
        float age = entity.tickCount + partialTick;
        float settle = Mth.lerp(partialTick, entity.prevSettle, entity.settle);
        settle = settle * settle * (3.0F - 2.0F * settle);
        float pitch = Mth.lerp(partialTick, entity.prevRollPitch, entity.rollPitch);
        float flat = Math.round(pitch / 180.0F) * 180.0F;
        pitch = Mth.lerp(settle, pitch, flat);
        float sink = Math.max(0.0F, age - (ArmourShardEntity.LIFETIME - ArmourShardEntity.SINK))
                / ArmourShardEntity.SINK;

        poses.pushPose();
        poses.translate(0.0F, Mth.lerp(settle, 0.15F, halfThick + 0.01F) - sink * (size[thin] + 0.1F), 0.0F);
        poses.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.prevRollYaw, entity.rollYaw)));
        poses.mulPose(Axis.XP.rotationDegrees(pitch));
        // its thinnest side up, so "flat" is flat
        if (thin == 0) {
            poses.mulPose(Axis.ZP.rotationDegrees(90.0F));
        } else if (thin == 2) {
            poses.mulPose(Axis.XP.rotationDegrees(90.0F));
        }
        poses.translate(-mid[0], -mid[1], -mid[2]);
        PoseStack.Pose pose = poses.last();
        cell(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(SHEET)), cube, min, max, lo, hi, light,
                1.0F, 1.0F, 1.0F, 1.0F);
        // still burning in its cracks, and cooling
        float hot = 1.0F - age / COOL;
        if (hot > 0.0F) {
            cell(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(CRACKS)), cube, min, max, lo, hi,
                    LightTexture.FULL_BRIGHT, 0.75F, 0.93F, 1.0F, hot * hot);
        }
        poses.popPose();
    }

    /**
     * The cell [lo, hi] (fractions of the box along each axis) of one box: each face of the box gives the cell's face
     * on the same side, its corners moved in to the cell and its texture coordinates interpolated across the face
     * between the box's own four - so the cell is painted with exactly the part of the face it was cut from.
     */
    private static void cell(PoseStack.Pose pose, VertexConsumer vc, GeoCube cube, float[] min, float[] max,
                             float[] lo, float[] hi, int light, float r, float g, float b, float a) {
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        float[] span = {max[0] - min[0], max[1] - min[1], max[2] - min[2]};
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) {
                continue;
            }
            Vector3f qn = quad.normal();
            float[] nv = {qn.x(), qn.y(), qn.z()};
            int n = 0;
            for (int k = 1; k < 3; k++) {
                if (Math.abs(nv[k]) > Math.abs(nv[n])) {
                    n = k;
                }
            }
            int p = n == 0 ? 1 : 0;
            int q = n == 2 ? 1 : 2;
            GeoVertex[] vs = quad.vertices();
            // the face's texture coordinates at its four corners, by which corner
            float[][] u = new float[4][];
            for (GeoVertex v : vs) {
                float[] at = {v.position().x(), v.position().y(), v.position().z()};
                int fp = span[p] < 1.0E-6F ? 0 : Math.round((at[p] - min[p]) / span[p]);
                int fq = span[q] < 1.0E-6F ? 0 : Math.round((at[q] - min[q]) / span[q]);
                u[fp + 2 * fq] = new float[]{v.texU(), v.texV()};
            }
            boolean whole = true;
            for (float[] c : u) {
                whole &= c != null;
            }
            if (!whole) {
                continue;
            }
            Vector3f normal = nm.transform(new Vector3f(qn));
            float side = nv[n] > 0.0F ? hi[n] : lo[n];
            for (GeoVertex v : vs) {
                float[] at = {v.position().x(), v.position().y(), v.position().z()};
                float sp = span[p] < 1.0E-6F ? 0.0F : (at[p] - min[p]) / span[p] < 0.5F ? lo[p] : hi[p];
                float sq = span[q] < 1.0E-6F ? 0.0F : (at[q] - min[q]) / span[q] < 0.5F ? lo[q] : hi[q];
                float[] out = new float[3];
                out[n] = min[n] + span[n] * side;
                out[p] = min[p] + span[p] * sp;
                out[q] = min[q] + span[q] * sq;
                float tu = bilinear(u, 0, sp, sq);
                float tv = bilinear(u, 1, sp, sq);
                org.joml.Vector4f w = m.transform(new org.joml.Vector4f(out[0], out[1], out[2], 1.0F));
                vc.vertex(w.x(), w.y(), w.z(), r, g, b, a, tu, tv, OverlayTexture.NO_OVERLAY, light,
                        normal.x(), normal.y(), normal.z());
            }
        }
    }

    private static float bilinear(float[][] c, int k, float s, float t) {
        return (1 - s) * (1 - t) * c[0][k] + s * (1 - t) * c[1][k] + (1 - s) * t * c[2][k] + s * t * c[3][k];
    }
}
