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
 * THE HOLLOW KING'S STAFF, MADE AGAIN: its own model (tools/gen_velkhar_staff.py) drawn on his "ice_staff" bone, whose
 * own cubes are no longer drawn (VelkharRenderer). Authored in his model space in the old wand's own frame, so it is
 * held as that one was - and since the pose is taken at that bone, the bone's scale is in it: it grows out of his fist
 * across the forge as the old one did. CAPTURE AND REPLAY, as MagusGarbLayer: the bone's pose copied in renderForBone,
 * the staff drawn from it in render, after the model pass; the wood and the gold lit by the room, the ice from its
 * glowmask full bright.
 */
public class VelkharStaffLayer extends GeoRenderLayer<VelkharEntity> {

    private static final ResourceLocation GEO = FrozenFortress.id("geo/entity/velkhar_staff.geo.json");
    private static final ResourceLocation TEX = FrozenFortress.id("textures/entity/velkhar_staff.png");
    /** His bone it rides. */
    public static final String BONE = "ice_staff";

    private static final Matrix4f POSE = new Matrix4f();
    private static final Matrix3f NORMAL = new Matrix3f();
    private static boolean captured;

    public VelkharStaffLayer(GeoRenderer<VelkharEntity> renderer) {
        super(renderer);
    }

    /** Called by VelkharRenderer with the pose at his "ice_staff" bone (its own transform, its forging scale, in): the
     *  old wand's cubes are not drawn, this is what is. */
    static void capture(PoseStack poseStack) {
        POSE.set(poseStack.last().pose());
        NORMAL.set(poseStack.last().normal());
        captured = true;
    }

    @Override
    public void render(PoseStack poseStack, VelkharEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        try {
            if (!captured || animatable.isInvisible()) {
                return;
            }
            BakedGeoModel staff = GeckoLibCache.getBakedModels().get(GEO);
            GeoBone bone = staff == null ? null : staff.getBone("staff").orElse(null);
            if (bone == null) {
                return;
            }
            draw(poseStack, bone, bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEX)), packedLight);
            draw(poseStack, bone, bufferSource.getBuffer(AutoGlowingTexture.getRenderType(TEX)), 0xF000F0);
        } finally {
            captured = false;
        }
    }

    private void draw(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int light) {
        poseStack.pushPose();
        poseStack.last().pose().set(POSE);
        poseStack.last().normal().set(NORMAL);
        getRenderer().renderCubesOfBone(poseStack, bone, buffer, light, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
}
