package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.DoomBladeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renders the executioner's blade.
 *
 * The model is authored at roughly four blocks and scaled up here, so the
 * thing that drops on the player is unmistakably enormous rather than merely
 * large. Drawn translucent - it is conjured ice, not a dropped item.
 */
public class DoomBladeRenderer extends GeoEntityRenderer<DoomBladeEntity> {

    private static final float SCALE = 3.0F;

    public DoomBladeRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(FrozenFortress.id("doom_blade"), false));
        this.shadowRadius = 1.2F;
    }

    @Override
    public RenderType getRenderType(DoomBladeEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void preRender(PoseStack poseStack, DoomBladeEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {
        poseStack.scale(SCALE, SCALE, SCALE);
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /**
     * IT BREAKS RATHER THAN BLINKING OUT.
     *
     * <p>The blade is six bones stacked up its own length (see
     * {@code build_doom_blade}), and each one is thrown off its own way once
     * the entity says the break has started: out and sideways, tumbling, and
     * falling faster the further it has gone. The pieces keep their positions
     * exactly while the shatter is at zero, so nothing about the whole weapon
     * changes - this only ever moves what is already coming apart.
     *
     * <p>THE DIRECTIONS ARE DERIVED FROM THE BONE INDEX, not rolled. A shared
     * BakedGeoModel means anything written here is written for every blade in
     * the world, so it must be a pure function of which bone this is - two
     * blades landing at once would otherwise fight over the same numbers and
     * both would stutter.
     */
    @Override
    public void renderRecursively(PoseStack poseStack, DoomBladeEntity animatable,
                                  software.bernie.geckolib.cache.object.GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        String name = bone.getName();
        float burst = animatable.shatter(partialTick);
        if (name.startsWith("shard_")) {
            if (burst <= 0.0F) {
                bone.setPosX(0.0F);
                bone.setPosY(0.0F);
                bone.setPosZ(0.0F);
                bone.setRotX(0.0F);
                bone.setRotZ(0.0F);
            } else {
                int i = name.charAt(name.length() - 1) - '0';
                // a fan out of the break, one piece per band, spun off the
                // index so the six of them never travel together
                float a = i * 2.39996F;           // the golden angle, in radians
                float reach = (3.0F + i * 1.9F) * burst;
                // gravity on what was thrown: out fast, down increasingly
                float drop = -(1.2F + i * 0.8F) * burst * burst * 5.0F;
                bone.setPosX(Mth.cos(a) * reach);
                bone.setPosZ(Mth.sin(a) * reach);
                bone.setPosY(drop);
                bone.setRotX(burst * (1.4F + i * 0.35F));
                bone.setRotZ(burst * (i % 2 == 0 ? -1.9F : 1.6F));
                // and each piece fades as it goes, the small tip first
                alpha *= Math.max(0.0F, 1.0F - burst * (0.75F + i * 0.05F));
            }
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
