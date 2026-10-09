package com.jastkub.fdspells.client;

import com.jastkub.fdspells.FDSpells;
import com.jastkub.fdspells.entity.Fx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import javax.annotation.Nullable;

/**
 * Draws any of the spells' things from its own model (geo/entity/KIND, textures/entity/KIND with its _glowmask,
 * animations/entity/KIND), translucent - it is all ice - turned to where it faces and sized as it says this frame.
 */
public class FxRenderer<T extends Entity & GeoEntity & Fx> extends GeoEntityRenderer<T> {

    public FxRenderer(EntityRendererProvider.Context ctx, String kind) {
        super(ctx, new Model<>(kind));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        shadowRadius = 0.0F;
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource buffers,
                                    float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    /** Its size this frame (the turning is in applyRotations, which the renderer calls once, not on re-renders). */
    @Override
    public void preRender(PoseStack poses, T fx, BakedGeoModel model, MultiBufferSource buffers,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, int colour) {
        super.preRender(poses, fx, model, buffers, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        if (!isReRender) {
            float k = fx.size(partialTick), s = fx.spread(partialTick);
            poses.scale(k * s, k, k * s);
        }
    }

    /**
     * Turned to where it faces, as a living thing is (the model's front, -z, to its yaw), and tipped nose-down by
     * its pitch. GeckoLib's own turn for a thing that is not alive is a flat half turn whatever it faces.
     *
     * <p>(1.21.1, GeckoLib 4.9) THE SIX-ARGUMENT ONE. GeckoLib calls applyRotations(.., nativeScale) and its old
     * five-argument form only forwards to it - an override of the old form is never reached, and every effect would
     * have lost its facing without a word.
     */
    @Override
    protected void applyRotations(T fx, PoseStack poses, float ageInTicks, float rotationYaw, float partialTick,
                                  float nativeScale) {
        if (!fx.faces()) {
            return;
        }
        poses.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, fx.yRotO, fx.getYRot())));
        if (fx.pitched()) {
            poses.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, fx.xRotO, fx.getXRot())));
        }
    }

    /** geo/entity/KIND, textures/entity/KIND, animations/entity/KIND. */
    static final class Model<T extends Entity & GeoEntity & Fx> extends GeoModel<T> {
        private final ResourceLocation geo, tex, anim;

        Model(String kind) {
            geo = FDSpells.id("geo/entity/" + kind + ".geo.json");
            tex = FDSpells.id("textures/entity/" + kind + ".png");
            anim = FDSpells.id("animations/entity/" + kind + ".animation.json");
        }

        @Override
        public ResourceLocation getModelResource(T animatable) {
            return geo;
        }

        @Override
        public ResourceLocation getTextureResource(T animatable) {
            return tex;
        }

        @Override
        public ResourceLocation getAnimationResource(T animatable) {
            return anim;
        }
    }
}
