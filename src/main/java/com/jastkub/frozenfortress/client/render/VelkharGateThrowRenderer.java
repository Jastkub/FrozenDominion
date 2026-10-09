package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharGateThrowEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * RZUT WROTAMI: his own tower shield, thrown - its geometry copied out of his model (velkhar_gate_throw.geo.json, by
 * tools/gen_velkhar_attacks.py) and drawn with HIS sheet and glow mask, so it is the very shield that just left his
 * arm. It spins flat like a discus, turning the way it is flying, frame-smooth (the spin is drawn here, not keyed),
 * and it tilts a little into its arc.
 */
public class VelkharGateThrowRenderer extends GeoEntityRenderer<VelkharGateThrowEntity> {

    public VelkharGateThrowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new GateModel());
        this.shadowRadius = 0.5F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /** TURNS ONLY ON THE FIRST PASS (the glow layer draws the model again through here). */
    @Override
    public void preRender(PoseStack ps, VelkharGateThrowEntity g, BakedGeoModel model, MultiBufferSource buffers,
                          VertexConsumer buffer, boolean isReRender, float pt, int light, int overlay,
                          int colour) {
        if (!isReRender) {
            float t = g.tickCount + pt;
            ps.translate(0.0D, VelkharGateThrowEntity.LIFT, 0.0D);
            ps.mulPose(Axis.ZP.rotationDegrees(g.back() ? -12.0F : 12.0F));          // banked into its arc
            ps.mulPose(Axis.YP.rotationDegrees(t * (g.back() ? -34.0F : 38.0F)));    // the spin
            ps.translate(0.0D, -VelkharGateThrowEntity.LIFT, 0.0D);
        }
        super.preRender(ps, g, model, buffers, buffer, isReRender, pt, light, overlay, colour);
    }

    @Override
    public boolean shouldRender(VelkharGateThrowEntity g, Frustum frustum, double x, double y, double z) {
        return g.shouldRender(x, y, z);
    }

    /** His geometry for the shield alone, his sheet. */
    static class GateModel extends GeoModel<VelkharGateThrowEntity> {
        private static final ResourceLocation GEO = FrozenFortress.id("geo/entity/velkhar_gate_throw.geo.json");
        private static final ResourceLocation TEX = FrozenFortress.id("textures/entity/velkhar.png");
        private static final ResourceLocation ANIM = FrozenFortress.id("animations/entity/velkhar_gate_throw.animation.json");

        @Override
        public ResourceLocation getModelResource(VelkharGateThrowEntity g) {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(VelkharGateThrowEntity g) {
            return TEX;
        }

        @Override
        public ResourceLocation getAnimationResource(VelkharGateThrowEntity g) {
            return ANIM;
        }
    }
}
