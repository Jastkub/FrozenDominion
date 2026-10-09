package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharJudgmentMarkEntity;
import com.jastkub.frozenfortress.entity.boss.VelkharJudgmentSwordEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * LODOWY SAD on screen (tools/gen_velkhar_attacks.py): the spectral sword (GeckoLib, translucent ice that lights
 * itself, lifted over its point of impact by the height it still has to fall) and the circle under it (geometry: the
 * rune ring, the ring closing in from its rim as the fall nears, the flash when it lands).
 */
public final class VelkharJudgmentRenderers {

    static final ResourceLocation MARK = FrozenFortress.id("textures/entity/fx_velkhar_judgment_mark.png");

    private VelkharJudgmentRenderers() {
    }

    // ============================================================================================== the sword
    public static class Sword extends GeoEntityRenderer<VelkharJudgmentSwordEntity> {
        public Sword(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("velkhar_judgment_sword"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public RenderType getRenderType(VelkharJudgmentSwordEntity s, ResourceLocation texture,
                                        MultiBufferSource buffers, float pt) {
            return RenderType.entityTranslucent(texture);
        }

        /** Spectral: ice you can half see through, and fading in as it forms. */
        @Override
        public Color getRenderColor(VelkharJudgmentSwordEntity s, float pt, int light) {
            float a = Mth.clamp(s.age(pt) / 6.0F, 0.0F, 1.0F) * 0.82F;
            return Color.ofRGBA(0.86F, 0.96F, 1.0F, a);
        }

        /** Lifted over its point of impact by the height it still has to fall. TURNS ONLY ON THE FIRST PASS. */
        @Override
        public void preRender(PoseStack ps, VelkharJudgmentSwordEntity s, BakedGeoModel model, MultiBufferSource buffers,
                              VertexConsumer buffer, boolean isReRender, float pt, int light, int overlay,
                              float red, float green, float blue, float alpha) {
            if (!isReRender) {
                ps.translate(0.0D, s.height(s.age(pt)), 0.0D);
            }
            super.preRender(ps, s, model, buffers, buffer, isReRender, pt, light, overlay, red, green, blue, alpha);
        }

        @Override
        public boolean shouldRender(VelkharJudgmentSwordEntity s, Frustum frustum, double x, double y, double z) {
            return s.shouldRender(x, y, z);
        }
    }

    // ============================================================================================== the circle
    public static class Mark extends EntityRenderer<VelkharJudgmentMarkEntity> {
        public Mark(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0.0F;
        }

        @Override
        public ResourceLocation getTextureLocation(VelkharJudgmentMarkEntity m) {
            return MARK;
        }

        @Override
        public boolean shouldRender(VelkharJudgmentMarkEntity m, Frustum frustum, double x, double y, double z) {
            return m.shouldRender(x, y, z);
        }

        @Override
        public void render(VelkharJudgmentMarkEntity m, float yaw, float pt, PoseStack ps, MultiBufferSource buffers,
                           int light) {
            float t = m.age(pt);
            float impact = m.impact();
            float r = m.radius();
            float in = Mth.clamp(t / 5.0F, 0.0F, 1.0F);
            float after = t - impact;
            float out = after <= 0.0F ? 1.0F : Mth.clamp(1.0F - after / VelkharJudgmentMarkEntity.AFTER, 0.0F, 1.0F);
            if (out <= 0.01F) {
                return;
            }
            float charge = Mth.clamp(t / impact, 0.0F, 1.0F);
            Matrix4f mx = ps.last().pose();
            Matrix3f n = ps.last().normal();
            // the rune ring itself, brightening and turning faster as the fall nears
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(MARK));
            float flick = after < 0.0F && charge > 0.75F ? 0.8F + 0.2F * Mth.sin(t * 2.2F) : 1.0F;
            StormEyeRenderers.disc(vc, mx, n, 0.04F, r * (0.85F + 0.15F * in), t * (0.01F + 0.05F * charge * charge),
                    0.7F + 0.3F * charge, 0.85F + 0.15F * charge, 1.0F, (0.55F + 0.45F * charge) * in * flick * out);
            // THE COUNTDOWN: a ring closing in from the rim; it reaches the heart as the point does
            if (after < 0.0F) {
                float rr = Math.max(0.15F, r * (1.0F - charge));
                annulus(buffers.getBuffer(RenderType.entityTranslucentEmissive(StormEyeRenderers.VEIN)), mx, n, 0.06F,
                        rr, rr + 0.22F, 0.75F, 0.88F, 1.0F, 0.9F * in);
            } else {
                // the flash where it landed
                float grow = 1.0F + after * 0.08F;
                StormEyeRenderers.disc(buffers.getBuffer(RenderType.entityTranslucentEmissive(StormEyeRenderers.EYE)),
                        mx, n, 0.08F, (r + 0.6F) * grow, 0.0F, 0.85F, 0.92F, 1.0F, out);
            }
            super.render(m, yaw, pt, ps, buffers, light);
        }

        /** A flat ring between two radii. */
        static void annulus(VertexConsumer vc, Matrix4f m, Matrix3f n, float y, float r0, float r1,
                            float red, float green, float blue, float a) {
            int seg = Mth.clamp((int) (r1 * 12.0F) + 12, 16, 72);
            for (int i = 0; i < seg; i++) {
                float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
                float u0 = (float) i / seg, u1 = (float) (i + 1) / seg;
                StormEyeRenderers.vtx(vc, m, n, Mth.cos(a0) * r0, y, Mth.sin(a0) * r0, u0, 1.0F, red, green, blue, a);
                StormEyeRenderers.vtx(vc, m, n, Mth.cos(a1) * r0, y, Mth.sin(a1) * r0, u1, 1.0F, red, green, blue, a);
                StormEyeRenderers.vtx(vc, m, n, Mth.cos(a1) * r1, y, Mth.sin(a1) * r1, u1, 0.0F, red, green, blue, a);
                StormEyeRenderers.vtx(vc, m, n, Mth.cos(a0) * r1, y, Mth.sin(a0) * r1, u0, 0.0F, red, green, blue, a);
            }
        }
    }
}
