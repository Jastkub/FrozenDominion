package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.entity.BossGateBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * THE CHASM OF BONES' MIST, AND NO SKY IN IT.
 *
 * <p>THE MIST: layers of drifting frost over the whole chasm (its hall: the Bone Lord's gate's Room), between the spikes
 * and the bridges, each taking two fifths of what is behind it - ten of them, and the floor is gone: what is under the
 * bridges is a white-grey nothing. Drawn whether the Lord lives or not.
 *
 * <p>NO SKY: a hall this big is wider than its fog (BoneLordFog), and the renderer leaves out what lies past the fog -
 * where the far walls should be the sky showed through, night and stars. Inside the hall a shell of the fog's own
 * colour is drawn right after the sky, so what is not drawn past the fog is fog.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class ChasmMist {

    private static final ResourceLocation TEX = FrozenFortress.id("textures/misc/frost_veil.png");
    private static final String KEEPER = "frozen_dominion:bone_lord";
    /**
     * The layers' heights over the hall's floor (the bridges' deck is at 25) - and under it, down the shaft the spikes
     * now stand at the foot of, on the bedrock (CitadelShaftPiece). MORE OF IT: ten where there were five, the top one five under the deck - from a bridge the eye goes through
     * the first few into white, and none of the shaft is seen.
     */
    private static final float[] LAYERS = {-12.0F, -6.0F, -1.0F, 2.5F, 5.5F, 8.5F, 11.5F, 14.0F, 16.5F, 19.0F};
    private static final float ALPHA = 0.42F;
    private static final float RED = 0.58F, GREEN = 0.64F, BLUE = 0.74F;
    private static final int LIGHT = (8 << 4) | (2 << 20);

    private ChasmMist() {
    }

    /** The chasm halls near the camera (their gates' Rooms). */
    private static List<AABB> halls(Vec3 cam) {
        List<AABB> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        for (BossGateBlockEntity g : List.copyOf(BossGateBlockEntity.CLIENT)) {
            if (!g.isRemoved() && g.getLevel() == mc.level && !g.isExit() && KEEPER.equals(g.keeper())
                    && g.getBlockPos().distToCenterSqr(cam) < 200.0D * 200.0D) {
                out.add(g.roomBox());
            }
        }
        return out;
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || BossGateBlockEntity.CLIENT.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        List<AABB> halls = halls(cam);
        if (halls.isEmpty()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource src = mc.renderBuffers().bufferSource();
        VertexConsumer vc = src.getBuffer(RenderType.entityTranslucent(TEX));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float t = (mc.level.getGameTime() + event.getPartialTick()) / 20.0F;
        for (AABB h : halls) {
            // far to near: the layers furthest from the eye first
            Integer[] order = new Integer[LAYERS.length];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            java.util.Arrays.sort(order, (a, b) -> Double.compare(Math.abs(h.minY + LAYERS[b] - cam.y),
                    Math.abs(h.minY + LAYERS[a] - cam.y)));
            for (int i : order) {
                float y = (float) (h.minY + LAYERS[i]);
                float du = t * (0.02F + 0.01F * i) * (i % 2 == 0 ? 1 : -1), dv = t * (0.008F + 0.004F * i);
                layer(vc, m, n, (float) h.minX, y, (float) h.minZ, (float) h.maxX, (float) h.maxZ, du, dv,
                        ALPHA * (0.85F + 0.15F * (i % 2)));
            }
        }
        src.endBatch(RenderType.entityTranslucent(TEX));
        pose.popPose();
    }

    /** One layer, ONE face: it was laid twice, its top and its
     *  underside in the same plane - entityTranslucent culls neither, the two split into triangles along opposite
     *  diagonals and so came out a hair apart in depth, and fought over every pixel of the hall. Seen from above and
     *  from below alike, one is enough. */
    private static void layer(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float y, float z0, float x1, float z1,
                              float du, float dv, float a) {
        float us = (x1 - x0) / 7.0F, vs = (z1 - z0) / 7.0F;
        float[][] p = {{x0, z0}, {x0, z1}, {x1, z1}, {x1, z0}};
        float[][] uv = {{du, dv}, {du, dv + vs}, {du + us, dv + vs}, {du + us, dv}};
        for (int k = 0; k < 4; k++) {
            vc.vertex(m, p[k][0], y, p[k][1]).color(RED, GREEN, BLUE, a).uv(uv[k][0], uv[k][1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LIGHT).normal(n, 0.0F, 1.0F, 0.0F)
                    .endVertex();
        }
    }

    /** Inside a chasm hall: the sky covered with the fog's colour (after anything else drawn with the sky). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSky(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || BossGateBlockEntity.CLIENT.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        boolean inside = false;
        for (AABB h : halls(cam)) {
            if (h.inflate(2.0D).contains(cam)) {
                inside = true;
                break;
            }
        }
        if (!inside) {
            return;
        }
        float[] fog = RenderSystem.getShaderFogColor();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        Matrix4f m = event.getPoseStack().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float r = 32.0F;
        float[][] c = {{-r, -r, -r}, {r, -r, -r}, {r, r, -r}, {-r, r, -r}, {-r, -r, r}, {r, -r, r}, {r, r, r}, {-r, r, r}};
        int[][] faces = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
        for (int[] f : faces) {
            for (int k : f) {
                bb.vertex(m, c[k][0], c[k][1], c[k][2]).color(fog[0], fog[1], fog[2], 1.0F).endVertex();
            }
        }
        BufferUploader.drawWithShader(bb.end());
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }
}
