package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.ForgeOverseerPlateEntity;
import com.jastkub.frozenfortress.entity.ForgeOverseerRimeEntity;
import com.jastkub.frozenfortress.entity.ForgeOverseerShockwaveEntity;
import com.jastkub.frozenfortress.entity.ForgeOverseerSlagEntity;
import com.jastkub.frozenfortress.entity.ForgeOverseerSteamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The things the Forge Overseer's fight is drawn with (tools/gen_forge_overseer.py): his plates on the floor, his slag,
 * the steam off his quench, the ring off his slam, the frost of a quenched blow. (The sweep's arc and the floor bursting
 * under the hammer are AttackFxEntity kinds "overseer_sweep" / "overseer_impact", drawn by AttackFxRenderer.)
 *
 * <p>TURNS AND SCALES ONLY ON THE FIRST PASS: GeckoLib calls preRender again for every re-render a layer asks for (the
 * glow), on a pose stack that already carries the first pass's transforms. Everything here that turns or scales does it
 * under !isReRender.
 */
public final class ForgeOverseerFxRenderers {

    private ForgeOverseerFxRenderers() {
    }

    /** A plate off him: the model of the plate it is (forge_overseer_plate_&lt;bone&gt;), turned as he was. No light of
     *  its own - it is cold iron - so no glow layer (and no glow mask to be empty). */
    public static class Plate extends GeoEntityRenderer<ForgeOverseerPlateEntity> {
        public Plate(EntityRendererProvider.Context ctx) {
            super(ctx, new PlateModel());
            this.shadowRadius = 0.35F;
        }

        @Override
        public void preRender(PoseStack poseStack, ForgeOverseerPlateEntity plate, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            if (!isReRender) {
                poseStack.mulPose(Axis.YP.rotationDegrees(-plate.getYRot()));
            }
            super.preRender(poseStack, plate, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, red, green, blue, alpha);
        }
    }

    static class PlateModel extends GeoModel<ForgeOverseerPlateEntity> {
        private static String name(ForgeOverseerPlateEntity p) {
            return "forge_overseer_plate_" + p.bone();
        }

        @Override
        public ResourceLocation getModelResource(ForgeOverseerPlateEntity p) {
            return FrozenFortress.id("geo/entity/" + name(p) + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(ForgeOverseerPlateEntity p) {
            return FrozenFortress.id("textures/entity/" + name(p) + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(ForgeOverseerPlateEntity p) {
            return FrozenFortress.id("animations/entity/" + name(p) + ".animation.json");
        }
    }

    /** A spark of frozen slag: a spinning lump, the cold in its cracks lit. */
    public static class Slag extends GeoEntityRenderer<ForgeOverseerSlagEntity> {
        public Slag(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("forge_overseer_slag"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    /** The steam off the quench: see-through, thinning away over its second half. Round, so never turned. */
    public static class Steam extends GeoEntityRenderer<ForgeOverseerSteamEntity> {
        public Steam(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("fx_overseer_steam"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public RenderType getRenderType(ForgeOverseerSteamEntity steam, ResourceLocation texture,
                                        MultiBufferSource bufferSource, float partialTick) {
            return RenderType.entityTranslucent(texture);
        }

        @Override
        public Color getRenderColor(ForgeOverseerSteamEntity steam, float partialTick, int packedLight) {
            return steam.isPuff() ? Color.ofRGBA(0.74F, 0.88F, 1.0F, 0.55F * steam.thickness(partialTick))
                    : Color.ofRGBA(1.0F, 1.0F, 1.0F, 0.78F * steam.thickness(partialTick));
        }

        /** A hearth's breath: half the quench's billow. */
        @Override
        public void preRender(PoseStack poseStack, ForgeOverseerSteamEntity steam, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            if (!isReRender && steam.isPuff()) {
                poseStack.scale(0.5F, 0.5F, 0.5F);
            }
            super.preRender(poseStack, steam, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, red, green, blue, alpha);
        }
    }

    /** The ring off the slam: round (never turned), drawn at its size (9/7 in his second half). */
    public static class Shockwave extends GeoEntityRenderer<ForgeOverseerShockwaveEntity> {
        public Shockwave(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("fx_overseer_shockwave"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, ForgeOverseerShockwaveEntity ring, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            if (!isReRender) {
                float s = ring.size();
                poseStack.scale(s, s, s);
            }
            super.preRender(poseStack, ring, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, red, green, blue, alpha);
        }
    }

    /** The frost of a quenched blow, cut to whoever it holds; clouding as it is struck. */
    public static class Rime extends GeoEntityRenderer<ForgeOverseerRimeEntity> {
        public Rime(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("fx_overseer_rime"), false));
            this.shadowRadius = 0.4F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, ForgeOverseerRimeEntity rime, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            if (!isReRender) {
                poseStack.scale(rime.fitWidth(), rime.fitHeight(), rime.fitWidth());
            }
            super.preRender(poseStack, rime, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, red, green, blue, alpha);
        }

        @Override
        public Color getRenderColor(ForgeOverseerRimeEntity rime, float partialTick, int packedLight) {
            return Color.ofRGBA(0.88F, 0.96F, 1.0F, Mth.clamp(1.0F - 0.5F * rime.shellDamage(), 0.3F, 1.0F));
        }

        @Override
        public RenderType getRenderType(ForgeOverseerRimeEntity rime, ResourceLocation texture,
                                        MultiBufferSource bufferSource, float partialTick) {
            return RenderType.entityTranslucent(texture);
        }
    }
}
