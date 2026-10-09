package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.jastkub.frozenfortress.registry.FFSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/**
 * BIALA CIEMNOSC - Velkhar's second-phase blizzard as seen and heard from inside it. Nothing here is a particle:
 *
 * <ul>
 *   <li>THE WHITE: the fog closes to WHITE_FAR blocks, in the storm's colour - a white-grey now, and the sky behind it
 *       veiled in the same. Through it the king is a shape at
 *       five blocks and nothing at eight - his runs are heard long before they are seen (VelkharEntity.tickWhiteout).</li>
 *   <li>THE SNOW: FLAKES, every one its own. Now two thousand small soft quads of one round flake, each where the
 *       wind has carried it (a near swarm and a far one, anchored in the world so they pass you as you move), each
 *       drawn stretched along its own flight - driven snow streaking past, not panes - and the storm's wall, a
 *       churning drum at its edge that thins out upward instead of ending on a line.</li>
 *   <li>THE FLOOR: snowed over while it lasts (StormFloorSnow).</li>
 *   <li>THE STORM CLOSING IN: a white-grey drift shutting the screen in from all four sides, breathing with the gusts,
 *       and the frost creeping in over its edge.</li>
 *   <li>THE HOWL, looped while you are inside (whiteout_wind).</li>
 * </ul>
 *
 * All of it fades in over a second inside his storm and out outside it, read off the king (VelkharEntity STORM_ON,
 * STORM_AT - ClientEvents tracks him).
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class WhiteoutClient {

    private static final ResourceLocation STREAKS = FrozenFortress.id("textures/environment/whiteout_streaks.png");
    private static final ResourceLocation FLAKE = FrozenFortress.id("textures/environment/whiteout_flake.png");
    private static final ResourceLocation FROST = FrozenFortress.id("textures/gui/whiteout_frost.png");
    private static final ResourceLocation VIGNETTE = FrozenFortress.id("textures/gui/whiteout_vignette.png");

    /** How far one sees inside it, and the haze it fades into. WHITE-GREY (08.10.2026): the storm-slate it was made the
     *  hall above him a dark lid; the flakes are pure white and still stand out against this, a shade darker. */
    private static final float WHITE_FAR = 12.0F, WHITE_NEAR = 1.5F;
    private static final float RED = 0.80F, GREEN = 0.84F, BLUE = 0.89F;
    private static final float RISE = 0.05F, FALL = 0.04F;
    private static final int WALL_SEGMENTS = 72;

    // ------------------------------------------------------------------------------------------------ the flakes
    /** The near swarm (a box this wide round the eye) and the far one (this wide, and FAR_TALL high). */
    private static final int NEAR = 650, FAR = 1450;
    private static final float NEAR_BOX = 10.0F, FAR_BOX = 26.0F, FAR_TALL = 16.0F;
    /** The wind's push and the fall, blocks a tick (before each flake's own share of it). */
    private static final float WIND = 0.42F, DROP = 0.065F;
    /** How long a streak a flake leaves: this many ticks of its flight, either side of it. */
    private static final float BLUR = 0.2F;
    /** Each flake's place in its box (0..1), its share of the wind and of the fall, its size and its swirl. */
    private static final float[] FX = new float[NEAR + FAR], FY = new float[NEAR + FAR], FZ = new float[NEAR + FAR];
    private static final float[] FK = new float[NEAR + FAR], FD = new float[NEAR + FAR];
    private static final float[] FS = new float[NEAR + FAR], FP = new float[NEAR + FAR], FW = new float[NEAR + FAR];

    static {
        java.util.Random r = new java.util.Random(0x5E0F1AL);
        for (int i = 0; i < NEAR + FAR; i++) {
            boolean near = i < NEAR;
            FX[i] = r.nextFloat();
            FY[i] = r.nextFloat();
            FZ[i] = r.nextFloat();
            FK[i] = 0.7F + 0.6F * r.nextFloat();
            FD[i] = 0.6F + 0.8F * r.nextFloat();
            // half-widths: a near flake is small (it is near), a far one a little bigger so it still reads
            FS[i] = near ? 0.018F + 0.022F * r.nextFloat() : 0.03F + 0.035F * r.nextFloat();
            FP[i] = r.nextFloat() * Mth.TWO_PI;
            FW[i] = 0.6F + 0.8F * r.nextFloat();
        }
    }

    private static float strength;
    private static float lastStrength;
    @Nullable
    private static Vec3 centre;
    @Nullable
    private static WindLoop loop;
    /** How far the wind and the fall have carried the snow (world blocks), and this tick's push - what a frame
     *  between two ticks adds on. Summed tick by tick: the wind veers, and a heading times the clock is no distance. */
    private static double carriedX, carriedZ, fallen;
    private static float pushX, pushZ, drop;

    private WhiteoutClient() {
    }

    /** 0 outside his storm, 1 well inside it, eased between. */
    public static float strength(float partialTick) {
        return Mth.lerp(partialTick, lastStrength, strength);
    }

    // ------------------------------------------------------------------------------------------------ inside or not
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        lastStrength = strength;
        boolean inside = false;
        VelkharEntity king = ClientEvents.getActiveBoss();
        if (p != null && king != null && king.isAlive() && king.stormActive() && !p.isSpectator()) {
            Vec3 c = king.stormCentreSeen();
            centre = c;
            double dx = p.getX() - c.x;
            double dz = p.getZ() - c.z;
            double r = VelkharEntity.stormRadius() + 0.75D;
            inside = dx * dx + dz * dz <= r * r && p.getY() > c.y - 4.0D
                    && p.getY() < c.y + VelkharEntity.stormCeiling() + 3.0D;
        }
        strength = inside ? Math.min(1.0F, strength + RISE) : Math.max(0.0F, strength - FALL);
        if (strength > 0.0F && !mc.isPaused() && (loop == null || loop.isStopped())) {
            loop = new WindLoop();
            mc.getSoundManager().play(loop);
        }
        // the wind carries the snow on, a tick at a time
        if (strength <= 0.0F || mc.level == null) {
            carriedX = carriedZ = fallen = 0.0D;
            pushX = pushZ = drop = 0.0F;
        } else if (!mc.isPaused()) {
            float time = mc.level.getGameTime() % 24000L;
            float g = gust(time);
            float w = windAngle(time);
            pushX = Mth.cos(w) * WIND * g;
            pushZ = Mth.sin(w) * WIND * g;
            drop = DROP * (0.85F + 0.3F * g);
            carriedX += pushX;
            carriedZ += pushZ;
            fallen += drop;
        }
    }

    // ------------------------------------------------------------------------------------------------ the white
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        float s = strength((float) event.getPartialTick());
        if (s <= 0.01F) {
            return;
        }
        float e = s * s * (3.0F - 2.0F * s);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), Mth.lerp(e, event.getFarPlaneDistance(), WHITE_FAR)));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), Mth.lerp(e, event.getNearPlaneDistance(), WHITE_NEAR)));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float s = strength((float) event.getPartialTick());
        if (s <= 0.01F) {
            return;
        }
        event.setRed(Mth.lerp(s, event.getRed(), RED));
        event.setGreen(Mth.lerp(s, event.getGreen(), GREEN));
        event.setBlue(Mth.lerp(s, event.getBlue(), BLUE));
    }

    // ------------------------------------------------------------------------------------------------ the snow
    /** The wind's heading: it veers slowly, and gusts swing it a little. */
    private static float windAngle(float time) {
        return time * 0.0045F + 0.5F * Mth.sin(time * 0.013F);
    }

    /** How hard it is blowing, 0.6 to 1: the gusts. */
    private static float gust(float time) {
        return 0.8F + 0.12F * Mth.sin(time * 0.05F) + 0.08F * Mth.sin(time * 0.137F + 1.1F);
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        boolean sky = event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY;
        if (!sky && event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        float pt = event.getPartialTick();
        float s = strength(pt);
        Minecraft mc = Minecraft.getInstance();
        if (s <= 0.01F || centre == null || mc.level == null) {
            return;
        }
        Matrix4f pose = event.getPoseStack().last().pose();
        // (the corners are in view space already: the level's pose stack carries the camera's turn - as RoarWarpFx)
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        if (sky) {
            veil(pose, s * s * (3.0F - 2.0F * s));
        } else {
            Vec3 cam = event.getCamera().getPosition();
            float time = (mc.level.getGameTime() % 24000L) + pt;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            // (no fog on the snow - it is what the eye is meant to catch against the haze; distance fades it by hand)
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            flakes(pose, cam, time, pt, s);
            wall(pose, cam, time, 0.55F * s);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    /**
     * THE SKY IS THE STORM (as ChasmMist.onSky): a box of the haze's own colour round the eye, drawn right after the
     * sky and writing no depth, so whatever sky this hall has - sun, moon, stars, a night - is under the white, and
     * everything in the world is drawn over it as usual.
     */
    private static void veil(Matrix4f pose, float alpha) {
        float[] fog = RenderSystem.getShaderFogColor();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float r = 32.0F;
        float[][] c = {{-r, -r, -r}, {r, -r, -r}, {r, r, -r}, {-r, r, -r}, {-r, -r, r}, {r, -r, r}, {r, r, r}, {-r, r, r}};
        int[][] faces = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
        for (int[] f : faces) {
            for (int k : f) {
                bb.vertex(pose, c[k][0], c[k][1], c[k][2]).color(fog[0], fog[1], fog[2], alpha).endVertex();
            }
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    /** `v` brought into [-box/2, box/2): where in the box round the eye a flake carried to `v` is. */
    private static float wrap(double v, float box) {
        return (float) (v - box * Math.floor(v / box + 0.5D));
    }

    /**
     * THE FLAKES. Each one is at its own place in a box round the eye, carried on by the wind and the fall since the
     * storm began (its own share of both: some are quicker), swirling a little, and brought back into the box when it
     * leaves it - the near box ten blocks across, the far one twenty-six, both faded out toward their faces so no
     * flake is seen to jump across. Only inside the storm, and never under its floor. Each is one quad of the round
     * flake, as wide as the flake and stretched along its flight by BLUR ticks of it: a streak in the gusts.
     */
    private static void flakes(Matrix4f pose, Vec3 cam, float time, float pt, float s) {
        Vec3 c = centre;
        double floor = c.y + 0.02D;
        double top = c.y + VelkharEntity.stormCeiling() + 5.0D;
        double stormR = VelkharEntity.stormRadius() - 0.3D;
        double ox = carriedX + pushX * pt;
        double oz = carriedZ + pushZ * pt;
        double oy = fallen + drop * pt;
        RenderSystem.setShaderTexture(0, FLAKE);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int quads = 0;
        for (int i = 0; i < NEAR + FAR; i++) {
            boolean near = i < NEAR;
            float box = near ? NEAR_BOX : FAR_BOX;
            float tall = near ? NEAR_BOX : FAR_TALL;
            float k = FK[i];
            float sw = FW[i];
            float swirlX = 0.35F * Mth.sin(time * 0.11F * sw + FP[i]);
            float swirlY = 0.2F * Mth.cos(time * 0.09F * sw + FP[i] * 1.7F);
            float swirlZ = 0.35F * Mth.cos(time * 0.1F * sw + FP[i] * 0.6F);
            float rx = wrap(FX[i] * box + ox * k + swirlX - cam.x, box);
            float ry = wrap(FY[i] * tall - oy * FD[i] + swirlY - cam.y, tall);
            float rz = wrap(FZ[i] * box + oz * k + swirlZ - cam.z, box);
            double wy = cam.y + ry;
            if (wy < floor || wy > top) {
                continue;
            }
            double sx = cam.x + rx - c.x;
            double sz = cam.z + rz - c.z;
            if (sx * sx + sz * sz > stormR * stormR) {
                continue;                                       // only inside his storm
            }
            float dist = Mth.sqrt(rx * rx + ry * ry + rz * rz);
            // out toward the box's faces it thins to nothing, so the wrap is never seen
            float face = Math.min(box * 0.5F - Math.max(Math.abs(rx), Math.abs(rz)), tall * 0.5F - Math.abs(ry));
            float fade = Mth.clamp(face / (near ? 1.2F : 2.0F), 0.0F, 1.0F);
            if (near) {
                fade *= Mth.clamp((dist - 0.3F) / 0.5F, 0.0F, 1.0F);          // not in the eye itself
            } else {
                fade *= Mth.clamp((dist - 3.5F) / 1.5F, 0.0F, 1.0F)          // (the near swarm has that)
                        * (1.0F - Mth.clamp((dist - 7.0F) / 5.5F, 0.0F, 1.0F));  // and the haze takes it
            }
            float a = (near ? 0.95F : 0.85F) * s * fade;
            if (a < 0.02F) {
                continue;
            }
            // along its flight: its share of the wind and of the fall
            float vx = pushX * k;
            float vy = -drop * FD[i];
            float vz = pushZ * k;
            float speed = Mth.sqrt(vx * vx + vy * vy + vz * vz);
            float hw = FS[i];
            float ax, ay, az;
            if (speed > 1.0E-4F) {
                ax = vx / speed;
                ay = vy / speed;
                az = vz / speed;
            } else {
                ax = 0.0F;
                ay = -1.0F;
                az = 0.0F;
            }
            // across it, square to the line of sight: (flight x toward the eye)
            float tx = -rx, ty = -ry, tz = -rz;
            float cx = ay * tz - az * ty;
            float cy = az * tx - ax * tz;
            float cz = ax * ty - ay * tx;
            float cl = Mth.sqrt(cx * cx + cy * cy + cz * cz);
            float half = hw * 1.3F + speed * BLUR;
            if (cl < 0.25F * dist) {
                // flying straight at the eye (or away): seen end-on, it is a flake and not a streak
                half = hw * 1.3F;
                cx = tz;
                cy = 0.0F;
                cz = -tx;
                cl = Mth.sqrt(cx * cx + cz * cz);
                if (cl < 1.0E-4F) {
                    cx = 1.0F;
                    cz = 0.0F;
                    cl = 1.0F;
                }
            }
            cx = cx / cl * hw;
            cy = cy / cl * hw;
            cz = cz / cl * hw;
            float hx = ax * half, hy = ay * half, hz = az * half;
            bb.vertex(pose, rx - hx - cx, ry - hy - cy, rz - hz - cz).uv(0.0F, 1.0F).color(1.0F, 1.0F, 1.0F, a).endVertex();
            bb.vertex(pose, rx - hx + cx, ry - hy + cy, rz - hz + cz).uv(1.0F, 1.0F).color(1.0F, 1.0F, 1.0F, a).endVertex();
            bb.vertex(pose, rx + hx + cx, ry + hy + cy, rz + hz + cz).uv(1.0F, 0.0F).color(1.0F, 1.0F, 1.0F, a).endVertex();
            bb.vertex(pose, rx + hx - cx, ry + hy - cy, rz + hz - cz).uv(0.0F, 0.0F).color(1.0F, 1.0F, 1.0F, a).endVertex();
            quads++;
        }
        BufferBuilder.RenderedBuffer done = bb.end();
        if (quads == 0) {
            done.release();
        } else {
            BufferUploader.drawWithShader(done);
        }
    }

    /** The storm's wall: two drums of snow at its edge turning the wind's way, the inner one faster - full from the
     *  floor to the storm's lid, then thinning out over three blocks above it, so it never ends on a line. */
    private static void wall(Matrix4f pose, Vec3 cam, float time, float alpha) {
        Vec3 c = centre;
        RenderSystem.setShaderTexture(0, STREAKS);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float yb = (float) (c.y - cam.y);
        float ym = (float) (c.y + VelkharEntity.stormCeiling() - cam.y);
        float yt = ym + 3.0F;
        for (int layer = 0; layer < 2; layer++) {
            double r = VelkharEntity.stormRadius() - layer * 0.45D;
            float spin = time * (layer == 0 ? 0.05F : 0.08F);
            float a = alpha * (layer == 0 ? 1.0F : 0.7F);
            for (int i = 0; i < WALL_SEGMENTS; i++) {
                double a0 = Math.PI * 2.0D * i / WALL_SEGMENTS;
                double a1 = Math.PI * 2.0D * (i + 1) / WALL_SEGMENTS;
                float x0 = (float) (c.x + Math.cos(a0) * r - cam.x);
                float z0 = (float) (c.z + Math.sin(a0) * r - cam.z);
                float x1 = (float) (c.x + Math.cos(a1) * r - cam.x);
                float z1 = (float) (c.z + Math.sin(a1) * r - cam.z);
                float u0 = i * 0.6F - spin;
                float u1 = u0 + 0.6F;
                float v0 = layer * 0.5F + time * 0.01F;
                float vm = v0 + (ym - yb) * 0.2F;
                float vt = vm + (yt - ym) * 0.2F;
                bb.vertex(pose, x0, ym, z0).uv(u0, v0).color(0.97F, 0.98F, 1.0F, a).endVertex();
                bb.vertex(pose, x0, yb, z0).uv(u0, vm).color(0.97F, 0.98F, 1.0F, a).endVertex();
                bb.vertex(pose, x1, yb, z1).uv(u1, vm).color(0.97F, 0.98F, 1.0F, a).endVertex();
                bb.vertex(pose, x1, ym, z1).uv(u1, v0).color(0.97F, 0.98F, 1.0F, a).endVertex();
                bb.vertex(pose, x0, yt, z0).uv(u0, vt).color(0.97F, 0.98F, 1.0F, 0.0F).endVertex();
                bb.vertex(pose, x0, ym, z0).uv(u0, v0).color(0.97F, 0.98F, 1.0F, a).endVertex();
                bb.vertex(pose, x1, ym, z1).uv(u1, v0).color(0.97F, 0.98F, 1.0F, a).endVertex();
                bb.vertex(pose, x1, yt, z1).uv(u1, vt).color(0.97F, 0.98F, 1.0F, 0.0F).endVertex();
            }
        }
        BufferUploader.drawWithShader(bb.end());
    }

    // ------------------------------------------------------------------------------------------------ the frost
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.VIGNETTE.id())) {
            return;
        }
        float s = strength(event.getPartialTick());
        if (s <= 0.01F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics gg = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        // THE STORM CLOSING IN: stretched over the screen, so it shuts in from all four sides; it breathes with the gusts -
        // drawn a little past the screen and drawn back, never inside it, so no edge of it is ever seen
        float time = mc.level != null ? (mc.level.getGameTime() % 24000L) + event.getPartialTick() : 0.0F;
        float open = 0.05F + 0.035F * Mth.sin(time * 0.045F) + 0.015F * Mth.sin(time * 0.13F + 1.7F);
        int vx = Math.round(w * open * 0.5F);
        int vy = Math.round(h * open * 0.5F);
        gg.setColor(1.0F, 1.0F, 1.0F, s);
        gg.blit(VIGNETTE, -vx, -vy, -90, 0.0F, 0.0F, w + 2 * vx, h + 2 * vy, w + 2 * vx, h + 2 * vy);
        gg.setColor(1.0F, 1.0F, 1.0F, 0.6F * s);
        gg.blit(FROST, 0, 0, -90, 0.0F, 0.0F, w, h, w, h);
        gg.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------------------------------------ the howl
    /** The storm's howl, at the listener, as loud as they are deep in it; it stops itself once they are out. */
    private static final class WindLoop extends AbstractTickableSoundInstance {
        WindLoop() {
            super(FFSounds.WHITEOUT_WIND.get(), SoundSource.WEATHER, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.volume = 0.01F;
        }

        @Override
        public void tick() {
            if (strength <= 0.0F) {
                stop();
                return;
            }
            this.volume = Math.max(0.01F, strength * 0.95F);
        }
    }
}
