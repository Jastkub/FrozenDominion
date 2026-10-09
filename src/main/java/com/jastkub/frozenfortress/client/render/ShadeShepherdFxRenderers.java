package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.ShadeShepherdDecoyEntity;
import com.jastkub.frozenfortress.entity.ShadeShepherdGustEntity;
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
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The things of the Shade Shepherd's fight that are not him or his herd (tools/gen_shade_shepherd.py) - each a model of
 * its own with its own light. (His AttackFx kinds, "shade_snuff" and "shade_split", are drawn by AttackFxRenderer.)
 *
 * <p>TURNS ONLY ON THE FIRST PASS: a glow layer calls preRender again on the already turned pose stack, so everything
 * turned here is turned under !isReRender.
 */
public final class ShadeShepherdFxRenderers {

    private ShadeShepherdFxRenderers() {
    }

    /**
     * HIS DECOY: his geometry and his clips under its own sheet (shade_shepherd_decoy: no light in the eye slits or the
     * bell). Its herd-lights are always hidden. In the dark it is drawn whole, like him - all you see is a mask, and the
     * eyes are dark; within a lit fire's light it goes see-through ({@link #SEEN_THROUGH}), the shade it is. It fades as
     * it bursts.
     */
    public static class Decoy extends FrostGeoRenderer<ShadeShepherdDecoyEntity> {

        static final float SEEN_THROUGH = 0.45F;
        static final float DEATH_FADE = 10.0F;

        public Decoy(EntityRendererProvider.Context ctx) {
            super(ctx, new DecoyModel());
            this.shadowRadius = 0.0F;
            addRenderLayer(new ShadeRenderer.FadingGlowLayer<>(this, Decoy::alpha));
        }

        static float alpha(ShadeShepherdDecoyEntity d, float partialTick) {
            float a = Mth.lerp(d.sight(partialTick), 1.0F, SEEN_THROUGH);
            if (d.deathTime > 0) {
                a *= Math.max(0.0F, 1.0F - (d.deathTime + partialTick) / DEATH_FADE);
            }
            return a;
        }

        @Override
        public void preRender(PoseStack poseStack, ShadeShepherdDecoyEntity decoy, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            ShadeShepherdRenderer.herdLights(model, 0);
            super.preRender(poseStack, decoy, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, red, green, blue, alpha);
        }

        @Override
        public Color getRenderColor(ShadeShepherdDecoyEntity decoy, float partialTick, int packedLight) {
            return Color.ofRGBA(1.0F, 1.0F, 1.0F, alpha(decoy, partialTick));
        }

        @Override
        public RenderType getRenderType(ShadeShepherdDecoyEntity decoy, ResourceLocation texture,
                                        MultiBufferSource bufferSource, float partialTick) {
            return RenderType.entityTranslucent(texture);
        }
    }

    /** His geometry and clips, the decoy's sheet. */
    static class DecoyModel extends DefaultedEntityGeoModel<ShadeShepherdDecoyEntity> {
        DecoyModel() {
            super(FrozenFortress.id("shade_shepherd"), true);
        }

        @Override
        public ResourceLocation getTextureResource(ShadeShepherdDecoyEntity decoy) {
            return FrozenFortress.id("textures/entity/shade_shepherd_decoy.png");
        }
    }

    /**
     * THE SHADOWS OF HIS DASH (ShadeAfterimageEntity): his geometry under the decoy's lightless sheet, in the glide's
     * pose, tinted to a cold violet-black and half see-through, fading to nothing in its short life. Never darker than
     * a dim room's light, so the streak still reads in his darkened chambers. No glow layer: it has no light of its own.
     */
    public static class Afterimage extends GeoEntityRenderer<com.jastkub.frozenfortress.entity.ShadeAfterimageEntity> {

        static final float ALPHA = 0.55F;

        public Afterimage(EntityRendererProvider.Context ctx) {
            super(ctx, new AfterimageModel());
            this.shadowRadius = 0.0F;
        }

        @Override
        public void render(com.jastkub.frozenfortress.entity.ShadeAfterimageEntity shade, float entityYaw, float partialTick,
                           PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
            int lit = net.minecraft.client.renderer.LightTexture.pack(
                    Math.max(net.minecraft.client.renderer.LightTexture.block(packedLight), 6),
                    net.minecraft.client.renderer.LightTexture.sky(packedLight));
            super.render(shade, entityYaw, partialTick, poseStack, bufferSource, lit);
        }

        @Override
        public void preRender(PoseStack poseStack, com.jastkub.frozenfortress.entity.ShadeAfterimageEntity shade,
                              BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red,
                              float green, float blue, float alpha) {
            ShadeShepherdRenderer.herdLights(model, 0);
            if (!isReRender) {
                poseStack.mulPose(Axis.YP.rotationDegrees(-shade.getYRot()));
            }
            super.preRender(poseStack, shade, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, red, green, blue, alpha);
        }

        @Override
        public Color getRenderColor(com.jastkub.frozenfortress.entity.ShadeAfterimageEntity shade, float partialTick,
                                    int packedLight) {
            float left = 1.0F - shade.faded(partialTick);
            return Color.ofRGBA(0.30F, 0.26F, 0.42F, ALPHA * left * left);
        }

        @Override
        public RenderType getRenderType(com.jastkub.frozenfortress.entity.ShadeAfterimageEntity shade,
                                        ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
            return RenderType.entityTranslucent(texture);
        }
    }

    /** His geometry and clips, the decoy's sheet; no head turned (it has none to turn). */
    static class AfterimageModel extends DefaultedEntityGeoModel<com.jastkub.frozenfortress.entity.ShadeAfterimageEntity> {
        AfterimageModel() {
            super(FrozenFortress.id("shade_shepherd"), false);
        }

        @Override
        public ResourceLocation getTextureResource(com.jastkub.frozenfortress.entity.ShadeAfterimageEntity shade) {
            return FrozenFortress.id("textures/entity/shade_shepherd_decoy.png");
        }
    }

    /**
     * HIS BREATH in flight (fx_shade_gust): turned the way it flies - its yaw, and its pitch as it rises or drops to the
     * fire (the entity keeps both). A third bigger than its model, so it reads across a hall.
     */
    public static class Gust extends GeoEntityRenderer<ShadeShepherdGustEntity> {
        public Gust(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("fx_shade_gust"), false));
            this.shadowRadius = 0.0F;
            withScale(1.3F);
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, ShadeShepherdGustEntity gust, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            if (!isReRender) {
                poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, gust.yRotO, gust.getYRot())));
                poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, gust.xRotO, gust.getXRot())));
            }
            super.preRender(poseStack, gust, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, red, green, blue, alpha);
        }
    }
}
