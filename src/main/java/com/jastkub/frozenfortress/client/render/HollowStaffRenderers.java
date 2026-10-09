package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.HollowStaffBellEntity;
import com.jastkub.frozenfortress.entity.HollowStaffFxEntity;
import com.jastkub.frozenfortress.entity.HollowStaffRuneEntity;
import com.jastkub.frozenfortress.entity.HollowStaffShadeEntity;
import com.jastkub.frozenfortress.entity.HollowStaffTideEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The shapes the Staff of the Hollow King's spells are drawn with (tools/gen_hollow_staff.py) - each its own model
 * with its own light (every glow mask has lit pixels: the generator counts them).
 *
 * <p>TURNS ONLY ON THE FIRST PASS. GeckoLib calls preRender again for every re-render a layer asks for (the glow is
 * one), on a pose stack that already carries the first pass's transforms - so everything here that moves, turns or
 * scales does it under !isReRender.
 */
public final class HollowStaffRenderers {

    private HollowStaffRenderers() {
    }

    /** A rune of the Litany: face out from him while it orbits, face first down its flight when it flies. */
    public static class Rune extends GeoEntityRenderer<HollowStaffRuneEntity> {
        public Rune(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("fx_hollow_staff_rune"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, HollowStaffRuneEntity rune, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            if (!isReRender) {
                poseStack.translate(0.0F, 0.25F, 0.0F);              // the model is drawn about its middle
                Vec3 dir;
                Entity owner = rune.ownerEntity();
                if (rune.state() == HollowStaffRuneEntity.ORBIT && owner != null) {
                    dir = rune.getPosition(partialTick).subtract(owner.getPosition(partialTick)).multiply(1.0D, 0.0D, 1.0D);
                } else {
                    dir = new Vec3(rune.getX() - rune.xo, rune.getY() - rune.yo, rune.getZ() - rune.zo);
                }
                if (dir.lengthSqr() > 1.0E-6) {
                    dir = dir.normalize();
                    float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                    float pitch = (float) Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1.0D, 1.0D)));
                    poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
                    poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                }
            }
            super.preRender(poseStack, rune, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, red, green, blue, alpha);
        }
    }

    /** The Bell of Penance: round, so never turned; its clip carries it down from eight blocks up. */
    public static class Bell extends GeoEntityRenderer<HollowStaffBellEntity> {
        public Bell(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("fx_hollow_staff_bell"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    /** The Black Tide: a ring round where he stood - round, so never turned. */
    public static class Tide extends GeoEntityRenderer<HollowStaffTideEntity> {
        public Tide(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("fx_hollow_staff_tide"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    /** A picture (HollowStaffFxEntity): the model of its kind, at the size and yaw it was cast with - and, when it
     *  rides an entity, drawn at that entity's interpolated position so it never trails it. */
    public static class Fx extends GeoEntityRenderer<HollowStaffFxEntity> {
        public Fx(EntityRendererProvider.Context ctx) {
            super(ctx, new FxModel());
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, HollowStaffFxEntity fx, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            if (!isReRender) {
                Vec3 rider = fx.riderPosition(partialTick);
                if (rider != null) {
                    Vec3 me = fx.getPosition(partialTick);
                    poseStack.translate(rider.x - me.x, rider.y - me.y, rider.z - me.z);
                }
                poseStack.mulPose(Axis.YP.rotationDegrees(-fx.getYRot()));
                float s = fx.size();
                poseStack.scale(s, s, s);
            }
            super.preRender(poseStack, fx, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, red, green, blue, alpha);
        }
    }

    static class FxModel extends GeoModel<HollowStaffFxEntity> {
        @Override
        public ResourceLocation getModelResource(HollowStaffFxEntity fx) {
            return FrozenFortress.id("geo/entity/fx_hollow_staff_" + fx.kind() + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(HollowStaffFxEntity fx) {
            return FrozenFortress.id("textures/entity/fx_hollow_staff_" + fx.kind() + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(HollowStaffFxEntity fx) {
            return FrozenFortress.id("animations/entity/fx_hollow_staff_" + fx.kind() + ".animation.json");
        }
    }

    /** His shade: a little see-through (it is a shade), its eyes and crown lighting themselves only as much as the
     *  rest of it shows (ShadeRenderer.FadingGlowLayer); fading out with its last clip. */
    public static class Shade extends GeoEntityRenderer<HollowStaffShadeEntity> {
        static final float MAX_ALPHA = 0.84F;

        public Shade(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("hollow_staff_shade"), true));
            this.shadowRadius = 0.35F;
            addRenderLayer(new ShadeRenderer.FadingGlowLayer<>(this, Shade::alpha));
        }

        static float alpha(HollowStaffShadeEntity shade, float partialTick) {
            float a = MAX_ALPHA;
            if (shade.tickCount < HollowStaffShadeEntity.RISE_T) {
                a *= Mth.clamp((shade.tickCount + partialTick) / HollowStaffShadeEntity.RISE_T, 0.0F, 1.0F);
            }
            if (shade.fadeStart >= 0) {
                a *= Mth.clamp(1.0F - (shade.tickCount - shade.fadeStart + partialTick) / HollowStaffShadeEntity.FADE_T,
                        0.0F, 1.0F);
            }
            return a;
        }

        @Override
        public RenderType getRenderType(HollowStaffShadeEntity shade, ResourceLocation texture,
                                        MultiBufferSource bufferSource, float partialTick) {
            return RenderType.entityTranslucent(texture);
        }

        @Override
        public Color getRenderColor(HollowStaffShadeEntity shade, float partialTick, int packedLight) {
            return Color.ofRGBA(1.0F, 1.0F, 1.0F, alpha(shade, partialTick));
        }

        @Override
        protected float getDeathMaxRotation(HollowStaffShadeEntity shade) {
            return 0.0F;
        }
    }
}
