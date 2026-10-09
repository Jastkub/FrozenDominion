package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.IceOrbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Two things wear this: the four smooth orbs of the barrage, and the dozens of
 * jagged splinters that make up the third phase's rings.
 *
 * <p>ONE ENTITY, TWO MODELS. The rings need exactly the behaviour the orbs
 * already have - hold a slot on a circle, then launch at somebody - and the
 * only thing that differs is what they look like, so the model is picked per
 * entity here rather than by writing a second class. A splinter also tumbles
 * and comes in a range of sizes, both derived from its own id, because a ring
 * of two dozen identical shards all facing the same way reads as a cog.
 */
public class IceOrbRenderer extends GeoEntityRenderer<IceOrbEntity> {

    public IceOrbRenderer(EntityRendererProvider.Context context) {
        super(context, new SwapModel());
        this.shadowRadius = 0.0F;
    }

    @Override
    public void preRender(PoseStack poseStack, IceOrbEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {
        // they GROW rather than appear - see IceOrbEntity.form()
        float form = animatable.form();
        if (form < 1.0F) {
            float ease = form * form * (3.0F - 2.0F * form);
            poseStack.scale(ease, ease, ease);
        }
        if (animatable.isSplinter()) {
            int seed = animatable.getId();
            float age = animatable.tickCount + partialTick;
            poseStack.mulPose(Axis.YP.rotationDegrees(age * (4.0F + (seed % 5) * 1.7F)));
            poseStack.mulPose(Axis.XP.rotationDegrees(age * (3.0F + (seed % 4) * 2.2F)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(seed * 53.0F));
            float size = 0.55F + (seed % 9) * 0.06F;
            poseStack.scale(size, size, size);
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /**
     * The tail, and only once it is actually travelling.
     *
     * <p>A ring of splinters holding station around him does not want a
     * streak behind each one - forty little comets orbiting a man is soup -
     * so this starts the moment one is launched and not before. The orbs get a
     * fatter sheet than the splinters for the same reason they are bigger.
     */
    @Override
    public void render(IceOrbEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (entity.isLaunched()) {
            ProjectileTrail.draw(entity, poseStack, bufferSource,
                    FrozenFortress.id("textures/entity/ice_trail.png"),
                    // FATTER AND LONGER ON THE SPLINTERS. At 0.16 over twelve
                    // segments the ring's shards had a ribbon you could not see
                    // past the crystal itself, so they read as bare particles
                    // while every other projectile of his read as a comet.
                    partialTick, entity.isSplinter() ? 0.26F : 0.34F, 18,
                    0.70F, 0.90F, 1.0F);
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** Picks the geometry off the entity rather than off the renderer. */
    static class SwapModel extends DefaultedEntityGeoModel<IceOrbEntity> {
        SwapModel() {
            super(FrozenFortress.id("ice_orb"), false);
        }

        @Override
        public ResourceLocation getModelResource(IceOrbEntity entity) {
            return entity.isSplinter()
                    ? FrozenFortress.id("geo/entity/ice_crystal.geo.json")
                    : FrozenFortress.id("geo/entity/ice_orb.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(IceOrbEntity entity) {
            return entity.isSplinter()
                    ? FrozenFortress.id("textures/entity/ice_crystal.png")
                    : FrozenFortress.id("textures/entity/ice_orb.png");
        }

        @Override
        public ResourceLocation getAnimationResource(IceOrbEntity entity) {
            return entity.isSplinter()
                    ? FrozenFortress.id("animations/entity/ice_crystal.animation.json")
                    : FrozenFortress.id("animations/entity/ice_orb.animation.json");
        }
    }
}
