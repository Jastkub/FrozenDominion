package com.jastkub.frozenfortress.client;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.boss.VelkharEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/**
 * THE FLOOR UNDER VELKHAR'S BLIZZARD, SNOWED OVER. Drawn, not placed: no block of the hall changes.
 *
 * <p>A sheet of snow over every bit of floor inside his storm (VelkharEntity.stormRadius round STORM_AT): the floor of
 * each column is found once (and again every two seconds - the fight breaks floors), the highest thing to stand on a
 * little above or below where he called it, so it lies on the steps of the dais as well and stops at a column or a
 * wall. Each block of it is four quads whose corners lift 0.04 to 0.13 over the floor on a slow noise - drifts and
 * hollows, seamless from block to block - in whiteout_snow.png, sixteen texels to the block, lit by the hall but never
 * darker than block light 12 (snow in the dark is still snow), and taken by the storm's own haze with distance.
 *
 * <p>It SETTLES over four seconds once the storm is called - in drifts first, the hollows last (each corner has its own
 * moment on another, slower noise) - thins toward the storm's wall, and goes again over five seconds when it ends,
 * the hollows first. Seen from outside the storm too: it is on the floor, not in the eye.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID, value = Dist.CLIENT)
public final class StormFloorSnow {

    private static final ResourceLocation SNOW = FrozenFortress.id("textures/environment/whiteout_snow.png");
    /** Of the floor covered each tick while it snows, and bared each tick after: four seconds in, five out. */
    private static final float SETTLE = 1.0F / 80.0F, MELT = 1.0F / 100.0F;
    /** Quads to a block's side. */
    private static final int SUB = 2;
    private static final int VERTS = (SUB + 1) * (SUB + 1);
    /** How far over the floor it lies: this much at least, and up to BUMP more on the drifts. */
    private static final float LIFT = 0.04F, BUMP = 0.09F;
    private static final float MAX_ALPHA = 0.96F;
    private static final int MIN_BLOCK_LIGHT = 12;
    private static final int RESCAN = 40;

    private static float cover;
    private static float lastCover;
    @Nullable
    private static Vec3 centre;
    private static int scanAge;
    private static boolean stale;
    /** The floor, a block column at a time: its corner, its top, its light. */
    private static int cells;
    private static int[] cellX = new int[0], cellZ = new int[0], cellLight = new int[0];
    private static float[] cellTop = new float[0];
    /** Per corner of every quad (cells x VERTS): its lift over the floor, the moment it whitens, its shade, and how
     *  near the storm's wall it is (1 well inside, 0 at the wall). */
    private static float[] vLift = new float[0], vPatch = new float[0], vShade = new float[0], vEdge = new float[0];

    private StormFloorSnow() {
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            cover = lastCover = 0.0F;
            centre = null;
            cells = 0;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        lastCover = cover;
        VelkharEntity king = ClientEvents.getActiveBoss();
        boolean on = king != null && king.isAlive() && king.stormActive();
        if (on) {
            Vec3 c = king.stormCentreSeen();
            if (centre == null || centre.distanceToSqr(c) > 0.01D) {
                centre = c;
                stale = true;
            }
        }
        cover = on ? Math.min(1.0F, cover + SETTLE) : Math.max(0.0F, cover - MELT);
        if (cover <= 0.0F && !on) {
            centre = null;
            cells = 0;
            return;
        }
        if (centre != null && (stale || ++scanAge >= RESCAN)) {
            scan(mc.level, centre);
            scanAge = 0;
            stale = false;
        }
    }

    /** Smooth value noise, 0..1, on the world's own coordinates (so it is the same snow from every side). */
    private static float noise(float x, float z) {
        int ix = Mth.floor(x);
        int iz = Mth.floor(z);
        float fx = x - ix;
        float fz = z - iz;
        fx = fx * fx * (3.0F - 2.0F * fx);
        fz = fz * fz * (3.0F - 2.0F * fz);
        float a = Mth.lerp(fx, lattice(ix, iz), lattice(ix + 1, iz));
        float b = Mth.lerp(fx, lattice(ix, iz + 1), lattice(ix + 1, iz + 1));
        return Mth.lerp(fz, a, b);
    }

    private static float lattice(int x, int z) {
        int h = x * 0x27D4EB2D ^ z * 0x165667B1;
        h = (h ^ (h >>> 15)) * 0x2C1B3C6D;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65535.0F;
    }

    /** Finds the floor under the storm, column by column, and lays the corners of its snow. */
    private static void scan(ClientLevel level, Vec3 c) {
        double radius = VelkharEntity.stormRadius();
        int r = Mth.ceil(radius) + 1;
        int bx = Mth.floor(c.x);
        int bz = Mth.floor(c.z);
        int fy = Mth.floor(c.y + 0.01D);
        int max = (2 * r + 1) * (2 * r + 1);
        if (cellX.length < max) {
            cellX = new int[max];
            cellZ = new int[max];
            cellLight = new int[max];
            cellTop = new float[max];
            vLift = new float[max * VERTS];
            vPatch = new float[max * VERTS];
            vShade = new float[max * VERTS];
            vEdge = new float[max * VERTS];
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int n = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = bx + dx;
                int z = bz + dz;
                double ox = x + 0.5D - c.x;
                double oz = z + 0.5D - c.z;
                if (ox * ox + oz * oz > (radius + 0.75D) * (radius + 0.75D)) {
                    continue;
                }
                // head room solid two up: a wall or a column, nothing to snow on
                p.set(x, fy + 2, z);
                if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                    continue;
                }
                float top = Float.NaN;
                for (int y = fy + 1; y >= fy - 3; y--) {
                    p.set(x, y, z);
                    VoxelShape shape = level.getBlockState(p).getCollisionShape(level, p);
                    if (!shape.isEmpty()) {
                        top = y + (float) shape.max(Direction.Axis.Y);
                        break;
                    }
                }
                if (Float.isNaN(top)) {
                    continue;                                   // a hole: the snow falls into it
                }
                int light = LevelRenderer.getLightColor(level, BlockPos.containing(x + 0.5D, top + 0.1D, z + 0.5D));
                light = LightTexture.pack(Math.max(MIN_BLOCK_LIGHT, LightTexture.block(light)), LightTexture.sky(light));
                cellX[n] = x;
                cellZ[n] = z;
                cellTop[n] = top;
                cellLight[n] = light;
                for (int i = 0; i <= SUB; i++) {
                    for (int j = 0; j <= SUB; j++) {
                        float vx = x + i / (float) SUB;
                        float vz = z + j / (float) SUB;
                        float drift = 0.6F * noise(vx * 0.55F, vz * 0.55F) + 0.4F * noise(vx * 1.7F + 31.7F, vz * 1.7F - 5.3F);
                        int v = n * VERTS + i * (SUB + 1) + j;
                        vLift[v] = LIFT + BUMP * drift;
                        vShade[v] = 0.86F + 0.14F * drift;
                        // the drifts whiten first, the hollows last
                        vPatch[v] = Mth.clamp(1.0F - drift + 0.35F * (noise(vx * 0.3F + 7.7F, vz * 0.3F + 1.3F) - 0.5F),
                                0.0F, 1.0F);
                        double wx = vx - c.x;
                        double wz = vz - c.z;
                        float d = (float) Math.sqrt(wx * wx + wz * wz);
                        vEdge[v] = 1.0F - Mth.clamp((d - ((float) radius - 2.5F)) / 2.8F, 0.0F, 1.0F);
                    }
                }
                n++;
            }
        }
        cells = n;
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        float cv = Mth.lerp(event.getPartialTick().getGameTimeDeltaPartialTick(true), lastCover, cover);
        Minecraft mc = Minecraft.getInstance();
        if (cv <= 0.002F || cells <= 0 || mc.level == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = new org.joml.Matrix4f(event.getModelViewMatrix()).mul(event.getPoseStack().last().pose());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        // the particle shader: a texture, a colour, the hall's light and the fog (the storm's haze takes it at range)
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, SNOW);
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        org.joml.Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        RenderSystem.applyModelViewMatrix();

        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        float[] alpha = new float[VERTS];
        int quads = 0;
        for (int c = 0; c < cells; c++) {
            int base = c * VERTS;
            boolean any = false;
            for (int v = 0; v < VERTS; v++) {
                // each corner whitens on its own moment of the settling (and bares on it going)
                float from = vPatch[base + v] * 0.55F;
                float t = Mth.clamp((cv - from) / 0.45F, 0.0F, 1.0F);
                alpha[v] = MAX_ALPHA * vEdge[base + v] * t * t * (3.0F - 2.0F * t);
                any |= alpha[v] >= 0.1F;
            }
            if (!any) {
                continue;
            }
            float x0 = (float) (cellX[c] - cam.x);
            float z0 = (float) (cellZ[c] - cam.z);
            float y0 = (float) (cellTop[c] - cam.y);
            int light = cellLight[c];
            for (int i = 0; i < SUB; i++) {
                for (int j = 0; j < SUB; j++) {
                    corner(bb, pose, base, alpha, i, j, x0, y0, z0, cellX[c], cellZ[c], light);
                    corner(bb, pose, base, alpha, i, j + 1, x0, y0, z0, cellX[c], cellZ[c], light);
                    corner(bb, pose, base, alpha, i + 1, j + 1, x0, y0, z0, cellX[c], cellZ[c], light);
                    corner(bb, pose, base, alpha, i + 1, j, x0, y0, z0, cellX[c], cellZ[c], light);
                    quads++;
                }
            }
        }
        com.mojang.blaze3d.vertex.MeshData done = bb.build();
        if (quads == 0) {
            if (done != null) {
                done.close();
            }
        } else {
            if (done != null) {
                BufferUploader.drawWithShader(done);
            }
        }

        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
        mc.gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void corner(BufferBuilder bb, Matrix4f pose, int base, float[] alpha, int i, int j,
                               float x0, float y0, float z0, int bx, int bz, int light) {
        int k = i * (SUB + 1) + j;
        int v = base + k;
        float fx = i / (float) SUB;
        float fz = j / (float) SUB;
        float shade = vShade[v];
        bb.addVertex(pose, x0 + fx, y0 + vLift[v], z0 + fz)
                .setUv((bx + fx) * 0.25F, (bz + fz) * 0.25F)
                .setColor(shade * 0.97F, shade * 0.985F, shade, alpha[k])
                .setLight(light);
    }
}
