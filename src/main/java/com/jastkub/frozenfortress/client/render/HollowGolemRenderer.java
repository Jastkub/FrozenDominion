package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the golem as the outline it is.
 *
 * <p>There is no wireframe code here on purpose. The texture is transparent
 * everywhere except a lit border on each face, so the mesh already renders as
 * nothing but its own edges - and the mesh IS the set of edges wanted. Adding
 * a line-drawing pass would have meant a custom render type, a second vertex
 * format and a per-cube edge buffer to reproduce something the UV layout gives
 * away for free.
 *
 * <p>The glow layer is what makes it visible at all: an unlit wireframe in a
 * dark cathedral is a handful of grey pixels.
 */
public class HollowGolemRenderer extends GeoEntityRenderer<HollowGolemEntity> {

    public HollowGolemRenderer(EntityRendererProvider.Context context) {
        super(context, new GolemModel());
        this.shadowRadius = 1.6F;
        // (the orb is drawn in render() now: light gathering before its palm, see drawOrb)
        addRenderLayer(new GolemBreathLayer(this));
        addRenderLayer(new GolemSaddleLayer(this));
        // ONLY IF THERE IS SOMETHING TO GLOW.
        //
        // This layer is not free to add speculatively: GeckoLib builds its
        // emissive texture by scanning the mask for lit pixels and throws if
        // it finds none, from inside render() - so a hide with nothing lit on
        // it does not lose its glow, it crashes the client the first frame the
        // golem is visible. The flat-ice pass produced exactly that, because
        // the simplified shapes carry no ice_core anywhere.
        //
        // The generator now deletes the mask when nothing is lit, so its
        // presence is the honest test of whether this layer has work to do.
        if (Minecraft.getInstance().getResourceManager()
                .getResource(FrozenFortress.id(
                        "textures/entity/ice_monstrosity_glowmask.png")).isPresent()) {
            // its ice, drawn lit and beating (GolemPulseLayer) - the one glow it has
            addRenderLayer(new GolemPulseLayer(this));
        }
    }

    /**
     * One geometry, one hide.
     *
     * <p>Two textures were carried while there was a real question about how
     * to fix a flat surface - with depth or with emission. It is answered, so
     * the switch and the loser are both gone: a model with a variant nobody
     * intends to ship is a model with a second thing to keep in sync.
     */
    /** Its pauldrons are torn off in its second phase (the FRACTURE): gone from then on. */
    @Override
    public void preRender(com.mojang.blaze3d.vertex.PoseStack poseStack, HollowGolemEntity golem,
                          software.bernie.geckolib.cache.object.BakedGeoModel model,
                          net.minecraft.client.renderer.MultiBufferSource bufferSource,
                          com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay, int colour) {
        boolean off = golem.berserk();
        model.getBone("pauldron_r").ifPresent(b -> b.setHidden(off));
        model.getBone("pauldron_l").ifPresent(b -> b.setHidden(off));
        model.getBone("head").ifPresent(b -> b.setTrackingMatrices(true));
        model.getBone("hand_r").ifPresent(b -> b.setTrackingMatrices(true));
        if (!isReRender) {
            headBone = model.getBone("head").orElse(null);
            handBone = model.getBone("hand_r").orElse(null);
        }
        super.preRender(poseStack, golem, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    private software.bernie.geckolib.cache.object.GeoBone headBone;
    /** The maw, in the head's frame: where the breath leaves from (GolemBreathLayer's MOUTH_Y, MOUTH_Z). */
    private static final org.joml.Vector4f MAW = new org.joml.Vector4f(0.0F, 56.8F / 16.0F, -41.5F / 16.0F, 1.0F);
    private static final org.joml.Vector4f MAW_OUT = new org.joml.Vector4f(0.0F, 0.0F, -1.0F, 0.0F);

    /** Where its maw is and where it looks, this frame (the roar's bent air pours out of it - RoarWarpFx). */
    private void trackMaw(HollowGolemEntity golem) {
        if (headBone == null) {
            return;
        }
        org.joml.Matrix4f m = headBone.getWorldSpaceMatrix();
        org.joml.Vector4f p = m.transform(new org.joml.Vector4f(MAW));
        org.joml.Vector4f d = m.transform(new org.joml.Vector4f(MAW_OUT));
        net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(p.x - golem.getX(), p.y - golem.getY(),
                p.z - golem.getZ());
        net.minecraft.world.phys.Vec3 out = new net.minecraft.world.phys.Vec3(d.x, d.y, d.z);
        if (at.lengthSqr() < 1.0E-4D || at.lengthSqr() > 144.0D || out.lengthSqr() < 1.0E-6D) {
            return;                                   // not drawn yet, or not believable
        }
        golem.clientMaw = at;
        golem.clientMawDir = out.normalize();
        golem.clientMawTick = golem.tickCount;
    }
    /** Its right hand, tracked: the orb gathers IN it, wherever the clip has it. */
    private software.bernie.geckolib.cache.object.GeoBone handBone;
    /** The middle of the hand (hand_r's fingers, Bedrock 59, 12, 0), in the baked model's blocks - x mirrored. */
    private static final org.joml.Vector4f PALM = new org.joml.Vector4f(-59.0F / 16.0F, 12.0F / 16.0F, 0.0F, 1.0F);
    private static final net.minecraft.resources.ResourceLocation STAR =
            FrozenFortress.id("textures/entity/dizzy_star.png");

    /**
     * STUNNED, AND IT SHOWS: while it is on its knee
     * after its charge broke on a pillar, five stars of ice wheel round over its head, each
     * spinning, bobbing out of step - the cartoon's sign for "hit it now", in the citadel's ice.
     * Drawn where the head actually is this frame (its bone, tracked).
     */
    @Override
    public void render(HollowGolemEntity golem, float entityYaw, float partialTick,
                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
        super.render(golem, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        trackMaw(golem);
        if (golem.orb() > 0.0F) {
            drawOrb(golem, partialTick, poseStack, bufferSource);
        }
        if (!golem.isStaggered() || headBone == null) {
            return;
        }
        org.joml.Vector3d head = headBone.getWorldPosition();
        double hx = head.x - golem.getX(), hy = head.y - golem.getY(), hz = head.z - golem.getZ();
        if (head.lengthSquared() < 1.0E-6D) {
            hx = 0.0D;
            hy = golem.getBbHeight() - 1.6D;
            hz = 0.0D;
        }
        float t = golem.tickCount + partialTick;
        var vc = bufferSource.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(STAR));
        for (int i = 0; i < 5; i++) {
            double a = t * 0.16D + i * Math.PI * 2.0D / 5.0D;
            poseStack.pushPose();
            poseStack.translate(hx + Math.cos(a) * 1.7D, hy + 2.6D + Math.sin(t * 0.2D + i * 1.3D) * 0.18D,
                    hz + Math.sin(a) * 1.7D);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(t * 0.3F + i));
            float sz = 0.42F + 0.08F * net.minecraft.util.Mth.sin(t * 0.3F + i * 2.0F);
            org.joml.Matrix4f m = poseStack.last().pose();
            com.mojang.blaze3d.vertex.PoseStack.Pose n = poseStack.last();
            float[][] corners = {{-sz, -sz, 0, 1}, {sz, -sz, 1, 1}, {sz, sz, 1, 0}, {-sz, sz, 0, 0}};
            for (float[] c : corners) {
                vc.addVertex(m, c[0], c[1], 0.0F).setColor(1.0F, 1.0F, 1.0F, 0.95F).setUv(c[2], c[3])
                        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).setNormal(n, 0.0F, 1.0F, 0.0F);
            }
            poseStack.popPose();
        }
    }

    /**
     * THE ORB, AS LIGHT: before
     * its palm, where the bombs will leave from, a white heart swelling inside a cyan halo, rays of
     * light wheeling off it, and streaks of light pouring into it from all round - faster and
     * brighter as it fills.
     */
    private void drawOrb(HollowGolemEntity golem, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                         net.minecraft.client.renderer.MultiBufferSource buffers) {
        float c = golem.orb();
        float t = golem.tickCount + partialTick;
        float yaw = net.minecraft.util.Mth.rotLerp(partialTick, golem.yBodyRotO, golem.yBodyRot);
        net.minecraft.world.phys.Vec3 feet = golem.getPosition(partialTick);
        net.minecraft.world.phys.Vec3 at = HollowGolemEntity.palmAt(feet, yaw).subtract(feet);
        if (handBone != null) {
            // where the hand IS this frame (its bone's world matrix, from this render)
            // the palm off the bone's OWN pivot (the model was reworked by hand: a fixed one was the old hand's), with
            // the orb layer's offset - so the light and the ice of the orb are one thing, in the fist
            org.joml.Vector4f p = handBone.getWorldSpaceMatrix().transform(new org.joml.Vector4f(handBone.getPivotX() / 16.0F,
                    (handBone.getPivotY() - 4.5F) / 16.0F, (handBone.getPivotZ() - 2.0F) / 16.0F, 1.0F));
            net.minecraft.world.phys.Vec3 bone = new net.minecraft.world.phys.Vec3(p.x - golem.getX(), p.y - golem.getY(),
                    p.z - golem.getZ());
            // the raised fist is six blocks from the palm the throw leaves from, and a check
            // of four sent the orb to that palm - before its head - until the arm came down: the bone, wherever it is
            if (bone.lengthSqr() > 1.0E-4D && bone.lengthSqr() < 14.0D * 14.0D) {
                at = bone;                        // (and the measured palm only if the bone is not to be had)
            }
        }
        net.minecraft.world.phys.Vec3 cam = this.entityRenderDispatcher.camera.getPosition().subtract(feet).subtract(at);
        org.joml.Quaternionf facing = this.entityRenderDispatcher.cameraOrientation();
        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        float swell = c * c;
        float pulse = 0.5F + 0.5F * net.minecraft.util.Mth.sin(t * (0.3F + c * 0.6F));
        GolemHazardRenderers.glow(poseStack, buffers, facing, 2.0F + 5.0F * swell, (0.25F + 0.25F * pulse) * (0.4F + c),
                t * 0.02F);
        GolemHazardRenderers.glow(poseStack, buffers, facing, 0.5F + 2.4F * swell, 0.95F, -t * 0.05F);
        // the rays, wheeling
        for (int i = 0; i < 6; i++) {
            double a = t * 0.06D + i * Math.PI / 3.0D;
            double len = 1.0D + 3.2D * swell;
            net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(Math.cos(a), Math.sin(a * 0.7D) * 0.6D,
                    Math.sin(a)).normalize();
            GolemHazardRenderers.streak(poseStack, buffers, cam, net.minecraft.world.phys.Vec3.ZERO, dir.scale(len),
                    0.12F + 0.1F * c, 0.25F + 0.35F * c);
        }
        // and the light pouring in
        for (int i = 0; i < 10; i++) {
            double ph = (t * (0.05D + 0.07D * c) + i / 10.0D) % 1.0D;
            double a = i * 2.399D;
            double b = Math.acos(1.0D - 2.0D * ((i + 0.5D) / 10.0D));
            net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(Math.sin(b) * Math.cos(a), Math.cos(b),
                    Math.sin(b) * Math.sin(a));
            double r0 = 4.2D * (1.0D - ph);
            double r1 = Math.max(0.0D, r0 - 1.1D);
            GolemHazardRenderers.streak(poseStack, buffers, cam, dir.scale(r0), dir.scale(r1), 0.1F,
                    (float) (Math.sin(ph * Math.PI) * (0.35D + 0.5D * c)));
        }
        poseStack.popPose();
    }

    /** It does its own dying (the "death" clip): no tipping over on to its side as a common mob does. */
    @Override
    protected float getDeathMaxRotation(HollowGolemEntity golem) {
        return 0.0F;
    }

    static class GolemModel extends DefaultedEntityGeoModel<HollowGolemEntity> {
        /** How far the head comes round off its body, and up or down - a beast this size turns its head, it does not
         *  wring its neck. */
        private static final float HEAD_YAW = 42.0F, HEAD_PITCH = 22.0F;
        /** How far its head swings side to side with its stride, walking. */
        private static final float WALK_SWAY = 11.0F;

        GolemModel() {
            super(FrozenFortress.id("ice_monstrosity"), false);
        }

        /**
         * THE HEAD FOLLOWS ITS EYES: turned by where it looks off its body (the look control's head yaw and pitch), ADDED to
         * the clip's own head rather than put in its place - GeckoLib's turnsHead sets it outright, which would still
         * the head in the roar and every blow. How much depends on what it is doing (HollowGolemEntity.headFreedom).
         */
        @Override
        public void setCustomAnimations(HollowGolemEntity golem, long instanceId,
                                        software.bernie.geckolib.animation.AnimationState<HollowGolemEntity> state) {
            super.setCustomAnimations(golem, instanceId, state);
            float k = golem.headFreedom();
            if (k <= 0.0F) {
                return;
            }
            var head = getAnimationProcessor().getBone("head");
            software.bernie.geckolib.model.data.EntityModelData data =
                    state.getData(software.bernie.geckolib.constant.DataTickets.ENTITY_MODEL_DATA);
            if (head == null || data == null) {
                return;
            }
            float yaw = net.minecraft.util.Mth.clamp(data.netHeadYaw(), -HEAD_YAW, HEAD_YAW) * k;
            float pitch = net.minecraft.util.Mth.clamp(data.headPitch(), -HEAD_PITCH, HEAD_PITCH) * k;
            //  walking straight at you
            // it has no reason to look aside, so its head swings with its gait too: side to side once a stride, the
            // heavier the stride the further
            float stride = net.minecraft.util.Mth.clamp(state.getLimbSwingAmount() * 1.6F, 0.0F, 1.0F);
            yaw += net.minecraft.util.Mth.sin(state.getLimbSwing() * 0.3331F) * WALK_SWAY * stride * k;
            pitch += net.minecraft.util.Mth.abs(net.minecraft.util.Mth.cos(state.getLimbSwing() * 0.3331F)) * 3.0F * stride * k;
            head.setRotY(head.getRotY() + yaw * net.minecraft.util.Mth.DEG_TO_RAD);
            head.setRotX(head.getRotX() + pitch * net.minecraft.util.Mth.DEG_TO_RAD);
        }
    }
}
