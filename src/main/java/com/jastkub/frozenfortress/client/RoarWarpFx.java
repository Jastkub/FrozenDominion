package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.jastkub.frozenfortress.entity.RoarWarpEntity;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * THE AIR BENT BY THE MONSTROSITY'S ROAR, drawn: each RoarWarpEntity is a stream of churned air out of the maw (a cone)
 * and rings thrown out round it (spheres going out to its reach). They are drawn last, after the particles, with
 * shaders/core/roar_warp: what was drawn so far is copied aside, and each of their pixels shows that copy displaced -
 * the shells most where they are seen edge on (a lens's rim), the stream by a noise that flows out of the maw. Walls in
 * front hide them (the frame's depth); nothing is lit or coloured but a breath of frost on the crest.
 *
 * <p>With a shader pack on (Oculus) the frame is not ours to copy: then RoarWarpRenderer draws the same meshes as a
 * faint frost glow instead.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class RoarWarpFx {

    /** The rings' and the stream's mesh: segments round, and rings along. */
    private static final int SHELL_AROUND = 28, SHELL_RINGS = 16, STREAM_AROUND = 20, STREAM_ALONG = 12;

    @Nullable
    private static ShaderInstance shader;
    @Nullable
    private static TextureTarget scene;

    private RoarWarpFx() {
    }

    public static void setShader(ShaderInstance s) {
        shader = s;
    }

    /** One corner of a quad, in the world; four make a quad. kind 0: a ring's shell, 1: the stream. */
    public interface Sink {
        void corner(double x, double y, double z, float u, float v, float power, float kind, float fade,
                    float nx, float ny, float nz);
    }

    // ------------------------------------------------------------------------------------------------ drawing
    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || shader == null || shaderPackOn()) {
            return;
        }
        List<RoarWarpEntity> warps = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof RoarWarpEntity w && w.owner() != null) {
                warps.add(w);
            }
        }
        if (warps.isEmpty()) {
            return;
        }
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();

        // the frame so far, aside
        RenderTarget main = mc.getMainRenderTarget();
        if (scene == null) {
            scene = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            scene.setFilterMode(GL11.GL_LINEAR);
        } else if (scene.width != main.width || scene.height != main.height) {
            scene.resize(main.width, main.height, Minecraft.ON_OSX);
            scene.setFilterMode(GL11.GL_LINEAR);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, scene.width, scene.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(false);

        PoseStack poses = event.getPoseStack();
        Matrix4f pose = poses.last().pose();
        Matrix3f normal = poses.last().normal();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        for (RoarWarpEntity w : warps) {
            build(w, pt, (x, y, z, u, v, power, kind, fade, nx, ny, nz) -> bb
                    .vertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                    .uv(u, v).color(power, kind, 0.0F, fade).normal(normal, nx, ny, nz).endVertex());
        }

        float time = mc.level.getGameTime() % 24000L + pt;
        shader.safeGetUniform("WarpTime").set(time);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, scene.getColorTextureId());
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        // (the corners are in view space already: the level's pose stack carries the camera's turn)
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        BufferUploader.drawWithShader(bb.end());
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    // ------------------------------------------------------------------------------------------------ the meshes
    /** A warp's stream and rings, as quads (four corners each), in the world. */
    public static void build(RoarWarpEntity w, float pt, Sink out) {
        Entity o = w.owner();
        if (o == null) {
            return;
        }
        float age = w.tickCount + pt;
        float power = w.power();
        Vec3 base = o.getPosition(pt);
        Vec3 maw;
        Vec3 dir;
        if (o instanceof HollowGolemEntity g && g.clientMaw != null && g.tickCount - g.clientMawTick <= 2) {
            maw = base.add(g.clientMaw);                   // where the head bone had it this frame
            dir = g.clientMawDir;
        } else {
            float yaw = Mth.rotLerp(pt, o.yRotO, o.getYRot()) * Mth.DEG_TO_RAD;
            dir = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
            maw = base.add(dir.scale(2.6D)).add(0.0D, o.getBbHeight() * 0.6D, 0.0D);
        }

        // THE STREAM: churned air out of the maw, reaching out over its first eight ticks, dying over its last fourteen
        float fadeIn = Mth.clamp(age / 4.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((w.life() - age) / 14.0F, 0.0F, 1.0F);
        float reachOut = 1.0F - (float) Math.pow(1.0F - Mth.clamp(age / 8.0F, 0.0F, 1.0F), 2.0D);
        float streamPower = power * fadeIn * fadeOut;
        if (streamPower > 0.01F) {
            stream(out, maw, dir, (4.0F + 8.0F * power) * reachOut, 0.5F, 1.4F + 3.4F * power, streamPower);
        }

        // THE RINGS: one every RING_EVERY ticks, each going out to the reach and fading as it goes
        for (int i = 0; i < w.rings(); i++) {
            float s = age - i * RoarWarpEntity.RING_EVERY;
            if (s < 0.0F || s > RoarWarpEntity.RING_LIFE) {
                continue;
            }
            float f = s / RoarWarpEntity.RING_LIFE;
            float r = 0.6F + w.reach() * (1.0F - (float) Math.pow(1.0F - f, 1.6D));
            float strength = power * (float) Math.pow(1.0F - f, 1.2D) * Mth.clamp(s / 1.5F, 0.0F, 1.0F)
                    * (i == 0 ? 1.0F : 0.75F);
            if (strength > 0.01F) {
                shell(out, maw, r, strength);
            }
        }
    }

    private static void shell(Sink out, Vec3 c, float r, float strength) {
        for (int j = 0; j < SHELL_RINGS; j++) {
            float la0 = -Mth.HALF_PI + Mth.PI * j / SHELL_RINGS;
            float la1 = -Mth.HALF_PI + Mth.PI * (j + 1) / SHELL_RINGS;
            for (int i = 0; i < SHELL_AROUND; i++) {
                float lo0 = Mth.TWO_PI * i / SHELL_AROUND;
                float lo1 = Mth.TWO_PI * (i + 1) / SHELL_AROUND;
                shellCorner(out, c, r, la0, lo0, (float) i / SHELL_AROUND, (float) j / SHELL_RINGS, strength);
                shellCorner(out, c, r, la1, lo0, (float) i / SHELL_AROUND, (float) (j + 1) / SHELL_RINGS, strength);
                shellCorner(out, c, r, la1, lo1, (float) (i + 1) / SHELL_AROUND, (float) (j + 1) / SHELL_RINGS, strength);
                shellCorner(out, c, r, la0, lo1, (float) (i + 1) / SHELL_AROUND, (float) j / SHELL_RINGS, strength);
            }
        }
    }

    private static void shellCorner(Sink out, Vec3 c, float r, float lat, float lon, float u, float v, float strength) {
        float nx = Mth.cos(lat) * Mth.cos(lon), ny = Mth.sin(lat), nz = Mth.cos(lat) * Mth.sin(lon);
        out.corner(c.x + nx * r, c.y + ny * r, c.z + nz * r, u, v, strength, 0.0F, 1.0F, nx, ny, nz);
    }

    private static void stream(Sink out, Vec3 maw, Vec3 dir, float len, float r0, float r1, float strength) {
        Vec3 d = dir.normalize();
        Vec3 a = Math.abs(d.y) < 0.95D ? d.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize()
                : d.cross(new Vec3(1.0D, 0.0D, 0.0D)).normalize();
        Vec3 b = a.cross(d).normalize();
        for (int k = 0; k < STREAM_ALONG; k++) {
            float t0 = (float) k / STREAM_ALONG, t1 = (float) (k + 1) / STREAM_ALONG;
            for (int i = 0; i < STREAM_AROUND; i++) {
                float th0 = Mth.TWO_PI * i / STREAM_AROUND, th1 = Mth.TWO_PI * (i + 1) / STREAM_AROUND;
                streamCorner(out, maw, d, a, b, len, r0, r1, t0, th0, (float) i / STREAM_AROUND, strength);
                streamCorner(out, maw, d, a, b, len, r0, r1, t1, th0, (float) i / STREAM_AROUND, strength);
                streamCorner(out, maw, d, a, b, len, r0, r1, t1, th1, (float) (i + 1) / STREAM_AROUND, strength);
                streamCorner(out, maw, d, a, b, len, r0, r1, t0, th1, (float) (i + 1) / STREAM_AROUND, strength);
            }
        }
    }

    private static void streamCorner(Sink out, Vec3 maw, Vec3 d, Vec3 a, Vec3 b, float len, float r0, float r1,
                                     float t, float th, float u, float strength) {
        float r = r0 + (r1 - r0) * (float) Math.pow(t, 0.8D);
        float ca = Mth.cos(th), sa = Mth.sin(th);
        Vec3 radial = a.scale(ca).add(b.scale(sa));
        Vec3 p = maw.add(d.scale(len * t)).add(radial.scale(r));
        // strongest a little out of the maw, gone at the far end
        float fade = Mth.clamp(t / 0.12F, 0.0F, 1.0F) * (float) Math.pow(1.0F - t, 1.1D);
        out.corner(p.x, p.y, p.z, u, t * len, strength, 1.0F, fade, (float) radial.x, (float) radial.y, (float) radial.z);
    }

    // ------------------------------------------------------------------------------------------------ Oculus
    private static boolean irisLooked;
    @Nullable
    private static Object iris;
    @Nullable
    private static java.lang.reflect.Method inUse;

    /** Is a shader pack drawing the world (Oculus)? Then the frame is not ours to copy and bend. */
    public static boolean shaderPackOn() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                inUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                iris = null;
                inUse = null;
            }
        }
        if (iris == null || inUse == null) {
            return false;
        }
        try {
            return (Boolean) inUse.invoke(iris);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
