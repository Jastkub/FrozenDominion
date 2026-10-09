package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.LastWatchBeamEntity;
import com.jastkub.frozenfortress.entity.LastWatchChainsEntity;
import com.jastkub.frozenfortress.entity.LastWatchMarkEntity;
import com.jastkub.frozenfortress.entity.LastWatchSentinelEntity;
import com.jastkub.frozenfortress.entity.projectile.LastWatchArrowEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The pictures of the Bow of the Last Watch (tools/gen_last_watch.py): each a GeckoLib model with its own light (the
 * glow layers - the citadel is dark).
 *
 * <p>TURNS ONLY ON THE FIRST PASS: the glow layer draws the model again through preRender on the pose stack the first
 * pass already turned, so every turn, scale and offset here is under !isReRender. What is set on BONES (the beam's
 * trimmed segments, the watchman's lean) is set again identically on the re-render, which is what it must be.
 */
public final class LastWatchRenderers {

    private LastWatchRenderers() {
    }

    /** An arrow-convention turn (yaw atan2(x, z), pitch up +) of a model whose front is -z (GeckoLib adds a half turn
     *  to every entity that is not alive, which brings that front to +z first). */
    static void pointAlong(PoseStack ps, double x, double y, double z) {
        float yaw = (float) (Mth.atan2(x, z) * Mth.RAD_TO_DEG);
        float pitch = (float) (Mth.atan2(y, Math.sqrt(x * x + z * z)) * Mth.RAD_TO_DEG);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.mulPose(Axis.XP.rotationDegrees(-pitch));
    }

    // ============================================================================================== the beam
    public static class Beam extends GeoEntityRenderer<LastWatchBeamEntity> {
        /** tools/gen_last_watch.py: SEGMENT (2 blocks), BEAM_START (4 units), TIP_REST (24 units), RINGS. */
        static final float SEG_BLOCKS = 2.0F;
        static final float START = 4.0F / 16.0F;
        static final float TIP_REST = 24.0F;
        static final float[] RING_OUT = {0.0F, 2.0F, 4.5F, 7.5F};

        public Beam(EntityRendererProvider.Context ctx) {
            super(ctx, new BeamModel());
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, LastWatchBeamEntity beam, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                              float partialTick, int packedLight, int packedOverlay,
                              int colour) {
            if (!isReRender) {
                Vector3f d = beam.dir();
                pointAlong(poseStack, d.x(), d.y(), d.z());
            }
            super.preRender(poseStack, beam, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, colour);
        }

        /** Each segment shown only as far as the beam goes (the last one cut to fit), the flare moved to its end,
         *  a ring that has run past the end put out. After the animation, so these win. */
        @Override
        public void renderRecursively(PoseStack poseStack, LastWatchBeamEntity beam, GeoBone bone, RenderType renderType,
                                      MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                      float partialTick, int packedLight, int packedOverlay,
                                      int colour) {
            String n = bone.getName();
            float len = beam.length();
            if (n.startsWith("seg_")) {
                float from = START + SEG_BLOCKS * Integer.parseInt(n.substring(4));
                boolean shown = from < len;
                bone.setHidden(!shown);
                if (shown) {
                    bone.setScaleZ(Math.min(1.0F, (len - from) / SEG_BLOCKS));
                }
            } else if (n.equals("tip")) {
                bone.setPosZ(TIP_REST - len * 16.0F);
            } else if (n.startsWith("ring_")) {
                int i = Integer.parseInt(n.substring(5));
                float out = RING_OUT[Math.min(i, RING_OUT.length - 1)] - bone.getPosZ() / 16.0F;
                bone.setHidden(out > len - 0.3F);
            }
            super.renderRecursively(poseStack, beam, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                    packedLight, packedOverlay, colour);
        }

        @Override
        public boolean shouldRender(LastWatchBeamEntity beam, Frustum frustum, double x, double y, double z) {
            return true;
        }
    }

    /** One geometry, two lights: the cold one, and the gold of a split beam. */
    static class BeamModel extends GeoModel<LastWatchBeamEntity> {
        private static final ResourceLocation GEO = FrozenFortress.id("geo/entity/fx_last_watch_beam.geo.json");
        private static final ResourceLocation TEX = FrozenFortress.id("textures/entity/fx_last_watch_beam.png");
        private static final ResourceLocation TEX_SPLIT = FrozenFortress.id("textures/entity/fx_last_watch_beam_vigil.png");
        private static final ResourceLocation ANIM = FrozenFortress.id("animations/entity/fx_last_watch_beam.animation.json");

        @Override
        public ResourceLocation getModelResource(LastWatchBeamEntity beam) {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(LastWatchBeamEntity beam) {
            return beam.split() ? TEX_SPLIT : TEX;
        }

        @Override
        public ResourceLocation getAnimationResource(LastWatchBeamEntity beam) {
            return ANIM;
        }
    }

    // ============================================================================================== the mark
    public static class Mark extends GeoEntityRenderer<LastWatchMarkEntity> {
        public Mark(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("fx_last_watch_mark"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        /** Drawn where its foe is drawn this frame (the two tick in either order: the mark would trail a tick). */
        @Override
        public void preRender(PoseStack poseStack, LastWatchMarkEntity mark, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                              float partialTick, int packedLight, int packedOverlay,
                              int colour) {
            if (!isReRender) {
                LivingEntity on = mark.target();
                if (on != null) {
                    Vec3 want = on.getPosition(partialTick).add(0.0D, on.getBbHeight() + LastWatchMarkEntity.ABOVE, 0.0D);
                    Vec3 at = mark.getPosition(partialTick);
                    poseStack.translate(want.x - at.x, want.y - at.y, want.z - at.z);
                }
            }
            super.preRender(poseStack, mark, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, colour);
        }
    }

    // ============================================================================================== the chains
    public static class Chains extends GeoEntityRenderer<LastWatchChainsEntity> {
        public Chains(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("fx_last_watch_chains"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, LastWatchChainsEntity chains, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                              float partialTick, int packedLight, int packedOverlay,
                              int colour) {
            if (!isReRender) {
                poseStack.mulPose(Axis.YP.rotationDegrees(-chains.getYRot()));
                float s = chains.size();
                poseStack.scale(s, s, s);
            }
            super.preRender(poseStack, chains, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, colour);
        }
    }

    // ============================================================================================== the watchman
    public static class Sentinel extends GeoEntityRenderer<LastWatchSentinelEntity> {
        private float lean;

        public Sentinel(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("fx_last_watch_sentinel"), false));
            this.shadowRadius = 0.7F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, LastWatchSentinelEntity s, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                              float partialTick, int packedLight, int packedOverlay,
                              int colour) {
            if (!isReRender) {
                poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, s.yRotO, s.getYRot())));
                lean = Mth.lerp(partialTick, s.xRotO, s.getXRot()) * Mth.DEG_TO_RAD;
            }
            super.preRender(poseStack, s, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, colour);
        }

        /**
         * His upper body leans to what he shoots at (a foe below him: forward, a negative turn about x) - put on the pose
         * stack about the "aim" bone's pivot, not added to the bone: a bone the playing clip does not key keeps what it
         * was given last frame, and an added lean would pile up frame on frame.
         */
        @Override
        public void renderRecursively(PoseStack poseStack, LastWatchSentinelEntity s, GeoBone bone, RenderType renderType,
                                      MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                      float partialTick, int packedLight, int packedOverlay,
                                      int colour) {
            if (bone.getName().equals("aim") && lean != 0.0F) {
                float py = bone.getPivotY() / 16.0F;
                poseStack.pushPose();
                poseStack.translate(0.0F, py, 0.0F);
                poseStack.mulPose(Axis.XP.rotation(-lean));
                poseStack.translate(0.0F, -py, 0.0F);
                super.renderRecursively(poseStack, s, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                        packedLight, packedOverlay, colour);
                poseStack.popPose();
                return;
            }
            super.renderRecursively(poseStack, s, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                    packedLight, packedOverlay, colour);
        }
    }

    // ============================================================================================== the arrows
    public static class Arrow extends GeoEntityRenderer<LastWatchArrowEntity> {
        public Arrow(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("fx_last_watch_arrow"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, LastWatchArrowEntity arrow, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                              float partialTick, int packedLight, int packedOverlay,
                              int colour) {
            if (!isReRender) {
                // an arrow's yRot is atan2(x, z) and its xRot is + upward (AbstractArrow), as pointAlong wants
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, arrow.yRotO, arrow.getYRot())));
                poseStack.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, arrow.xRotO, arrow.getXRot())));
                float s = arrow.kind() == LastWatchArrowEntity.SENTINEL ? 0.8F
                        : arrow.kind() == LastWatchArrowEntity.RAIN ? 0.9F : 1.0F;
                poseStack.scale(s, s, s);
            }
            super.preRender(poseStack, arrow, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, colour);
        }
    }
}
