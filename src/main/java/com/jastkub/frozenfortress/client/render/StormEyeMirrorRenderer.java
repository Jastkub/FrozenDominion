package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.StormEyeMirrorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * A MIRROR OF THE HOLLOW MAGUS (StormEyeMirrorEntity), drawn from HIS model and HIS third-phase sheet, wearing exactly
 * what he wears in the Eye of the Storm: the bare skull under the hood, the crown floating, the plate shed but for the
 * left pauldron, the wand in his fist and nothing else.
 *
 * <p>THE MODEL IS SHARED with him and with every other Velkhar on screen, so this states the whole magus look on the
 * bones it touches EVERY frame, the same bones and the same values VelkharRenderer writes for him in that phase - it
 * never leaves anything behind that his renderer does not overwrite the next time he draws. Weapons are declined at
 * draw time (renderRecursively), never hidden on the shared model.
 *
 * <p>A lie that is almost perfect: every couple of seconds it thins for three frames, and the anchors' beams never
 * reach it.
 */
public class StormEyeMirrorRenderer extends GeoEntityRenderer<StormEyeMirrorEntity> {

    /** No name over it: a LivingEntity that is not a Mob gets GeckoLib's name tag at 64 blocks. */
    @Override
    public boolean shouldShowName(StormEyeMirrorEntity m) {
        return false;
    }

    private static final java.util.Set<String> NOT_HIS = java.util.Set.of("sword", "twin", "twin_r", "shield", "crossbow");
    private static final String[] WEAPON_PATH = {"body", "chest", "arm_r", "lower_arm_r", "hand_r", "arm_l",
            "lower_arm_l", "gauntlet_l", "sword", "twin", "twin_r", "ice_staff", "crossbow", "shield"};
    private static final String[] SHED = {"plate_arm_r", "plate_arm_l", "plate_skirt", "plate_pauld_r", "plate_torso"};

    public StormEyeMirrorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new MirrorModel());
        this.shadowRadius = 0.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected float getDeathMaxRotation(StormEyeMirrorEntity m) {
        return 0.0F;
    }

    @Override
    public RenderType getRenderType(StormEyeMirrorEntity m, ResourceLocation texture, MultiBufferSource buffers,
                                    float pt) {
        return RenderType.entityTranslucent(texture);
    }

    /** Whole, but for three thin frames every two and a half seconds - and fading out as it breaks. */
    @Override
    public Color getRenderColor(StormEyeMirrorEntity m, float pt, int light) {
        float whole = m.whole(pt);
        int cycle = Math.floorMod(m.tickCount + m.getId() * 11, 50);
        float thin = cycle < 3 ? 0.55F : 1.0F;
        float a = whole * thin;
        return Color.ofRGBA(thin < 1.0F ? 0.75F : 1.0F, thin < 1.0F ? 0.85F : 1.0F, 1.0F, a);
    }

    @Override
    public void preRender(PoseStack ps, StormEyeMirrorEntity m, BakedGeoModel model, MultiBufferSource buffers,
                          VertexConsumer buffer, boolean isReRender, float pt, int light, int overlay,
                          float red, float green, float blue, float alpha) {
        for (String name : WEAPON_PATH) {
            model.getBone(name).ifPresent(b -> {
                b.setHidden(false);
                b.setChildrenHidden(false);
            });
        }
        // the third phase's head: the skull (following the helm's animated delta), the hood, the floating crown
        model.getBone("head").ifPresent(helm -> model.getBone("head_bare").ifPresent(skull -> {
            var helmRest = helm.getInitialSnapshot();
            var skullRest = skull.getInitialSnapshot();
            skull.setRotX(skullRest.getRotX() + (helm.getRotX() - helmRest.getRotX()));
            skull.setRotY(skullRest.getRotY() + (helm.getRotY() - helmRest.getRotY()));
            skull.setRotZ(skullRest.getRotZ() + (helm.getRotZ() - helmRest.getRotZ()));
            skull.setPosX(helm.getPosX());
            skull.setPosY(helm.getPosY());
            skull.setPosZ(helm.getPosZ());
        }));
        state(model, "visor", true);
        state(model, "head", true);
        state(model, "head_bare", false);
        state(model, "hood", false);
        state(model, "crown", true);
        state(model, "crown_bare", false);
        for (String plate : SHED) {
            state(model, plate, true);
        }
        state(model, "plate_pauld_l", false);
        for (String cloth : new String[]{"cape1", "cape2", "cape3"}) {
            model.getBone(cloth).ifPresent(b -> b.setPosZ(-1.4F));
        }
        model.getBone("eyes").ifPresent(b -> {
            float breath = (1.0F + 0.055F * Mth.sin((m.tickCount + pt) * 0.11F)) * 1.24F;
            b.setScaleX(breath);
            b.setScaleY(breath);
            b.setScaleZ(breath);
        });
        if (!isReRender) {
            float whole = m.whole(pt);
            if (whole < 1.0F) {
                float s = 1.0F + 0.18F * (1.0F - whole);
                ps.scale(s, s, s);
            }
        }
        super.preRender(ps, m, model, buffers, buffer, isReRender, pt, light, overlay, red, green, blue, alpha);
    }

    private static void state(BakedGeoModel model, String bone, boolean hidden) {
        model.getBone(bone).ifPresent(b -> {
            b.setHidden(hidden);
            b.setChildrenHidden(false);
        });
    }

    @Override
    public void renderRecursively(PoseStack ps, StormEyeMirrorEntity m, GeoBone bone, RenderType renderType,
                                  MultiBufferSource buffers, VertexConsumer buffer, boolean isReRender, float pt,
                                  int light, int overlay, float red, float green, float blue, float alpha) {
        String name = bone.getName();
        if (NOT_HIS.contains(name)) {
            bone.setScaleX(1.0F);
            bone.setScaleY(1.0F);
            bone.setScaleZ(1.0F);
            return;
        }
        if ("ice_staff".equals(name)) {
            bone.setScaleX(1.0F);
            bone.setScaleY(1.0F);
            bone.setScaleZ(1.0F);
        }
        super.renderRecursively(ps, m, bone, renderType, buffers, buffer, isReRender, pt, light, overlay,
                red, green, blue, alpha);
    }

    /** His geometry, his clips, his third-phase sheet. */
    static class MirrorModel extends GeoModel<StormEyeMirrorEntity> {
        private static final ResourceLocation GEO = FrozenFortress.id("geo/entity/velkhar.geo.json");
        private static final ResourceLocation TEX = FrozenFortress.id("textures/entity/velkhar_hollow.png");
        private static final ResourceLocation ANIM = FrozenFortress.id("animations/entity/velkhar.animation.json");

        @Override
        public ResourceLocation getModelResource(StormEyeMirrorEntity m) {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(StormEyeMirrorEntity m) {
            return TEX;
        }

        @Override
        public ResourceLocation getAnimationResource(StormEyeMirrorEntity m) {
            return ANIM;
        }
    }
}
