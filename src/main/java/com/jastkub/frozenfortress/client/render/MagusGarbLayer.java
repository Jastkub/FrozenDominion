package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * THE HOLLOW MAGUS DRESSED AS ONE: the long coat split at the sides and falling past his feet, the inner robe, a
 * girdle and stoles of runes, a two-tiered mantle, open bell sleeves. (Its collar of ice, the crown of shards turning
 * round his skull and the crystals circling his waist are gone)
 *
 * <p>Its own model (tools/gen_velkhar_magus.py), so not one island of his twelve sheets moves. Every bone of it rides
 * one of his, authored in his model space: CAPTURE AND REPLAY, as MaskMeshLayer - the pose in force at each of his
 * bones is copied in renderForBone (the right space, but the wrong moment to draw: asking the buffer source for another
 * render type mid-model ends the batch the model is writing) and the garb is drawn from those copies in render, after
 * the model pass. So a sleeve swings with his forearm and the coat with his thigh, frame for frame.
 *
 * <p>Worn only as the Magus ({@link VelkharEntity#wearsMagus}) - from the flash of the third transition on - and drawn
 * twice: the cloth lit by the room, then its runes and ice from the glowmask, full bright.
 */
public class MagusGarbLayer extends GeoRenderLayer<VelkharEntity> {

    private static final ResourceLocation GEO = FrozenFortress.id("geo/entity/velkhar_magus.geo.json");
    private static final ResourceLocation TEX = FrozenFortress.id("textures/entity/velkhar_magus.png");
    /** His bone -> the garb's bone on it. */
    private static final String[] HIS = {"body", "chest", "arm_r", "arm_l", "lower_arm_r", "lower_arm_l",
            "leg_r", "leg_l"};
    private static final String[] GARB = {"m_body", "m_chest", "m_arm_r", "m_arm_l", "m_fore_r", "m_fore_l",
            "m_leg_r", "m_leg_l"};

    private static final Matrix4f[] POSE = new Matrix4f[HIS.length];
    private static final Matrix3f[] NORMAL = new Matrix3f[HIS.length];
    private static final boolean[] CAPTURED = new boolean[HIS.length];

    static {
        for (int i = 0; i < HIS.length; i++) {
            POSE[i] = new Matrix4f();
            NORMAL[i] = new Matrix3f();
        }
    }

    public MagusGarbLayer(GeoRenderer<VelkharEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, VelkharEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!animatable.wearsMagus()) {
            return;
        }
        String name = bone.getName();
        for (int i = 0; i < HIS.length; i++) {
            if (HIS[i].equals(name)) {
                POSE[i].set(poseStack.last().pose());
                NORMAL[i].set(poseStack.last().normal());
                CAPTURED[i] = true;
                return;
            }
        }
    }

    @Override
    public void render(PoseStack poseStack, VelkharEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        try {
            if (!animatable.wearsMagus() || animatable.isInvisible()) {
                return;
            }
            BakedGeoModel garb = GeckoLibCache.getBakedModels().get(GEO);
            if (garb == null) {
                return;
            }
            draw(poseStack, garb, bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEX)), packedLight);
            draw(poseStack, garb, bufferSource.getBuffer(AutoGlowingTexture.getRenderType(TEX)), 0xF000F0);
        } finally {
            java.util.Arrays.fill(CAPTURED, false);
        }
    }

    private void draw(PoseStack poseStack, BakedGeoModel garb, VertexConsumer buffer, int light) {
        for (int i = 0; i < HIS.length; i++) {
            if (!CAPTURED[i]) {
                continue;
            }
            GeoBone bone = garb.getBone(GARB[i]).orElse(null);
            if (bone == null) {
                continue;
            }
            poseStack.pushPose();
            poseStack.last().pose().set(POSE[i]);
            poseStack.last().normal().set(NORMAL[i]);
            getRenderer().renderCubesOfBone(poseStack, bone, buffer, light, OverlayTexture.NO_OVERLAY,
                    com.jastkub.frozenfortress.util.FFColor.argb(1.0F, 1.0F, 1.0F, 1.0F));
            poseStack.popPose();
        }
    }
}
