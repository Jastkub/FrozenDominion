package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.FrostCandleEntity;
import com.jastkub.frozenfortress.entity.PortcullisEntity;
import com.jastkub.frozenfortress.entity.RimePriestessEntity;
import com.jastkub.frozenfortress.entity.TurnkeyEntity;
import com.jastkub.frozenfortress.entity.TurnkeyKeyEntity;
import com.jastkub.frozenfortress.entity.projectile.TurnkeyManacleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The renderers of what the Turnkey and the Priestess of Rime fight with, and
 * of the Priestess herself.
 */
public final class CourtFoesRenderers {

    private CourtFoesRenderers() {
    }

    /** A flung key: its bow's ice lit. */
    public static class Key extends FrostGeoRenderer<TurnkeyKeyEntity> {
        public Key(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("turnkey_key"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    /** A candle of ice: its flame and its runes lit. */
    public static class Candle extends FrostGeoRenderer<FrostCandleEntity> {
        public Candle(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("frost_candle"), false));
            this.shadowRadius = 0.4F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    /** The Priestess of Rime: her eyes, the hymn in her book, the hook of her crook lit -
     *  and over the book, while she reads, the one rune of her Litany. */
    public static class Priestess extends FrostGeoRenderer<RimePriestessEntity> {
        public Priestess(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("rime_priestess"), true));
            this.shadowRadius = 0.8F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, RimePriestessEntity animatable, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                              float partialTick, int packedLight, int packedOverlay,
                              int colour) {
            showBook(model, true, animatable.bookRune());
            super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                    packedLight, packedOverlay, colour);
        }

        /**
         * THE RUNE TO STAND ON, SEEN FROM ANYWHERE. While she reads, the same rune hangs high over her
         * head, big, lit, turned to whoever looks, breathing - drawn from the very sheet the
         * floor's tile of that rune wears, so what is matched is the same picture, not a
         * likeness of it. A wider, fainter copy behind it is its glow.
         */
        @Override
        public void render(RimePriestessEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                           MultiBufferSource bufferSource, int packedLight) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            int rune = entity.bookRune();
            if (rune < 0 || rune > 3 || entity.isDeadOrDying()) {
                return;
            }
            float t = entity.tickCount + partialTick;
            float pulse = 0.5F + 0.5F * Mth.sin(t * 0.22F);
            VertexConsumer vc = bufferSource.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(
                    FrozenFortress.id("textures/entity/litany_rune_" + rune + ".png")));
            poseStack.pushPose();
            poseStack.translate(0.0D, entity.getBbHeight() + 1.25D + 0.12D * Mth.sin(t * 0.09F), 0.0D);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            runeQuad(vc, poseStack, 2.6F + 0.25F * pulse, 0.18F + 0.12F * pulse);   // its glow
            runeQuad(vc, poseStack, 1.7F + 0.08F * pulse, 0.82F + 0.18F * pulse);   // the rune
            poseStack.popPose();
        }

        private static void runeQuad(VertexConsumer vc, PoseStack poseStack, float size, float alpha) {
            org.joml.Matrix4f m = poseStack.last().pose();
            PoseStack.Pose n = poseStack.last();
            float h = size / 2.0F;
            int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            float[][] corners = {{-h, -h, 0, 1}, {h, -h, 1, 1}, {h, h, 1, 0}, {-h, h, 0, 0}};
            for (float[] c : corners) {
                vc.addVertex(m, c[0], c[1], 0.0F).setColor(0.85F, 0.95F, 1.0F, alpha).setUv(c[2], c[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n, 0.0F, 1.0F, 0.0F);
            }
        }
    }

    /** The model is shared: every renderer of it sets the book's bones each frame. */
    static void showBook(BakedGeoModel model, boolean book, int rune) {
        for (String b : new String[]{"book", "cover_l", "cover_r"}) {
            model.getBone(b).ifPresent(g -> g.setHidden(!book));
        }
        for (int k = 0; k < 4; k++) {
            final int kk = k;
            model.getBone("rune_" + k).ifPresent(g -> g.setHidden(!book || rune != kk));
        }
    }

    /** A reflection: her shape - with empty hands. */
    public static class Mirror extends FrostGeoRenderer<com.jastkub.frozenfortress.entity.PriestessMirrorEntity> {
        public Mirror(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("rime_priestess"), true));
            this.shadowRadius = 0.8F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack poseStack, com.jastkub.frozenfortress.entity.PriestessMirrorEntity animatable,
                              BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                              int colour) {
            showBook(model, false, -1);
            super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                    packedLight, packedOverlay, colour);
        }
    }

    /** A leaf torn from her book: its hymn lit. */
    public static class Page extends FrostGeoRenderer<com.jastkub.frozenfortress.entity.FrostPageEntity> {
        public Page(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("frost_page"), false));
            this.shadowRadius = 0.0F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    /** The bell of penance: its lip lit. */
    public static class Bell extends GeoEntityRenderer<com.jastkub.frozenfortress.entity.PenanceBellEntity> {
        public Bell(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("penance_bell"), false));
            this.shadowRadius = 1.2F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    /** A rune of the Litany on the floor: its own sheet per rune, brighter at every verse. */
    public static class Rune extends GeoEntityRenderer<com.jastkub.frozenfortress.entity.LitanyRuneEntity> {
        public Rune(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("litany_rune"), false) {
                @Override
                public net.minecraft.resources.ResourceLocation getTextureResource(
                        com.jastkub.frozenfortress.entity.LitanyRuneEntity rune) {
                    return FrozenFortress.id("textures/entity/litany_rune_" + Mth.clamp(rune.rune(), 0, 3) + ".png");
                }
            });
            this.shadowRadius = 0.0F;
        }

        @Override
        public net.minecraft.client.renderer.RenderType getRenderType(com.jastkub.frozenfortress.entity.LitanyRuneEntity rune,
                                                                       net.minecraft.resources.ResourceLocation texture,
                                                                       MultiBufferSource bufferSource, float partialTick) {
            return net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(texture);
        }

        @Override
        public software.bernie.geckolib.util.Color getRenderColor(com.jastkub.frozenfortress.entity.LitanyRuneEntity rune,
                                                                          float partialTick, int packedLight) {
            float a = Mth.clamp(0.3F + 0.23F * rune.verse(), 0.0F, 1.0F);
            return software.bernie.geckolib.util.Color.ofRGBA(1.0F, 1.0F, 1.0F, a);
        }
    }

    /** A grate out of the vault, turned with the wall it makes. */
    public static class Grate extends GeoEntityRenderer<PortcullisEntity> {
        public Grate(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("portcullis"), false));
            this.shadowRadius = 0.0F;
        }

        @Override
        public void preRender(PoseStack poseStack, PortcullisEntity animatable, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                              float partialTick, int packedLight, int packedOverlay,
                              int colour) {
            if (!animatable.alongX()) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            }
            // one post to a corner: the north and south grates carry them, the others run into them
            model.getBone("posts").ifPresent(b -> b.setHidden(!animatable.alongX()));
            super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                    packedLight, packedOverlay, colour);
        }
    }

    /**
     * The manacle - and the chain behind it, link by link (the game's own chain
     * model) from the Turnkey's right hand to wherever the manacle is.
     */
    public static class Manacle extends GeoEntityRenderer<TurnkeyManacleEntity> {
        private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();

        public Manacle(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(FrozenFortress.id("turnkey_manacle"), false));
            this.shadowRadius = 0.0F;
        }

        @Override
        public void render(TurnkeyManacleEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                           MultiBufferSource bufferSource, int packedLight) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            Entity owner = entity.getOwner();
            if (!(owner instanceof TurnkeyEntity t)) {
                return;
            }
            Vec3 at = entity.getPosition(partialTick);
            Vec3 hand = TurnkeyRenderer.fist(t, partialTick);
            if (hand == null) {                                  // he was not drawn this frame: about where it is
                float body = Mth.rotLerp(partialTick, t.yBodyRotO, t.yBodyRot);
                Vec3 right = Vec3.directionFromRotation(0.0F, body + 90.0F);
                hand = t.getPosition(partialTick).add(right.scale(0.75D)).add(0.0D, 1.6D, 0.0D);
            }
            Vec3 d = hand.subtract(at);
            double span = d.length();
            if (span < 0.3D) {
                return;
            }
            // A CHAIN HANGS: thrown it pays out nearly taut, on someone it is
            // its seven blocks long - slack when they are close, straight when they pull - and
            // dropped it goes loose. The curve is a parabola hung by that much slack (its arc
            // L = span + 8 sag^2 / 3 span), lying on the floor where it would go through it.
            double length = entity.heldId() >= 0 ? Math.max(span, 7.0D)
                    : entity.getDeltaMovement().lengthSqr() < 0.05D ? span * 1.3D : span * 1.06D;
            double sag = Math.sqrt(3.0D * span * Math.max(0.0D, length - span) / 8.0D);
            double floor = Math.min(t.getPosition(partialTick).y, at.y - 0.35D) + 0.06D - at.y;
            int fine = 32;
            Vec3[] curve = new Vec3[fine + 1];
            double[] run = new double[fine + 1];
            for (int i = 0; i <= fine; i++) {
                double u = i / (double) fine;
                Vec3 p = d.scale(u).add(0.0D, -4.0D * sag * u * (1.0D - u), 0.0D);
                curve[i] = p.y < floor ? new Vec3(p.x, floor, p.z) : p;
                run[i] = i == 0 ? 0.0D : run[i - 1] + curve[i].distanceTo(curve[i - 1]);
            }
            // links of a block each (the game's chain, unsquashed), laid along the curve
            int n = Math.max(1, (int) Math.round(run[fine]));
            Vec3[] q = new Vec3[n + 1];
            for (int k = 0, i = 0; k <= n; k++) {
                double want = run[fine] * k / n;
                while (i < fine - 1 && run[i + 1] < want) {
                    i++;
                }
                double f = (want - run[i]) / Math.max(1.0E-6D, run[i + 1] - run[i]);
                q[k] = curve[i].lerp(curve[i + 1], Mth.clamp(f, 0.0D, 1.0D));
            }
            var blocks = Minecraft.getInstance().getBlockRenderer();
            for (int k = 0; k < n; k++) {
                Vec3 s = q[k + 1].subtract(q[k]);
                double len = s.length();
                if (len < 1.0E-4D) {
                    continue;
                }
                poseStack.pushPose();
                poseStack.translate(q[k].x, q[k].y, q[k].z);
                poseStack.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F,
                        (float) (s.x / len), (float) (s.y / len), (float) (s.z / len)));
                poseStack.mulPose(Axis.YP.rotationDegrees(k % 2 == 0 ? 0.0F : 90.0F));
                poseStack.scale(1.0F, (float) len, 1.0F);
                poseStack.translate(-0.5D, 0.0D, -0.5D);
                blocks.renderSingleBlock(CHAIN, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }

        @Override
        public boolean shouldRender(TurnkeyManacleEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                    double x, double y, double z) {
            return true;                                         // the chain reaches far past its own box
        }
    }
}
