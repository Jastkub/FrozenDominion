package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.entity.projectile.AvalancheEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * THE AVALANCHE, as geometry: blocks of snow, packed ice and ice, big and small, churning forward in a
 * wave - highest at its middle, ragged at its edges, every block tumbling over and over the way it is
 * going - and a spray of smaller ones flung up off its crest. Grown out of the floor as it starts;
 * sunk back into it when a pier or a wall stops it.
 */
public class AvalancheRenderer extends EntityRenderer<AvalancheEntity> {

    private static final int CHUNKS = 150;
    private static final int SPRAY = 60;
    private static final BlockState[] KINDS = {Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState(),
            Blocks.PACKED_ICE.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.ICE.defaultBlockState(),
            Blocks.POWDER_SNOW.defaultBlockState()};

    public AvalancheRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(AvalancheEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    private static float hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
    }

    @Override
    public void render(AvalancheEntity e, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int light) {
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        float age = e.tickCount + partialTick;
        float grow = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
        float stopped = e.stoppedFor(partialTick);
        float sink = stopped < 0.0F ? 1.0F : Mth.clamp(1.0F - stopped / AvalancheEntity.COLLAPSE, 0.0F, 1.0F);
        float k = grow * sink;
        float big = e.size(age);                                    // it gathers as it goes (AvalancheEntity.size)
        if (k <= 0.01F) {
            return;
        }
        Vec3 d = e.dir();
        float heading = (float) (Math.atan2(d.z, d.x) * (180.0D / Math.PI)) - 90.0F;
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(-heading));          // local: +z is the way it goes, x across it
        for (int i = 0; i < CHUNKS + SPRAY; i++) {
            boolean spray = i >= CHUNKS;
            float across = (hash(i, 1) * 2.0F - 1.0F) * (float) AvalancheEntity.HALF_WIDTH * big;
            float edge = 1.0F - Math.abs(across) / ((float) AvalancheEntity.HALF_WIDTH * big);  // 1 mid, 0 edge
            float crest = (float) AvalancheEntity.HEIGHT * big * (0.35F + 0.65F * edge * edge) * (0.7F + 0.3F * hash(i, 9));
            float size = (spray ? 0.25F + 0.3F * hash(i, 2) : 0.6F + 0.85F * hash(i, 2) * (0.5F + 0.5F * edge))
                    * (0.55F + 0.45F * big);
            // each block rolls round a loop through the front of the wave: up its face, over, and down
            float phase = (age * (0.10F + 0.05F * hash(i, 3)) + hash(i, 4)) % 1.0F;
            float fwd = Mth.sin(phase * Mth.TWO_PI) * 1.3F - 0.5F - hash(i, 13) * 1.2F;   // deeper body
            float up = (0.5F - 0.5F * Mth.cos(phase * Mth.TWO_PI)) * crest;
            if (spray) {
                fwd = -0.6F - phase * 2.6F;                          // flung up off the crest and left behind
                up = crest + 0.4F + Mth.sin(phase * Mth.PI) * 2.0F;
                size *= 1.0F - phase;
            }
            float s = size * k;
            if (s <= 0.02F) {
                continue;
            }
            poses.pushPose();
            poses.translate(across, up * sink, fwd);
            poses.mulPose(Axis.XP.rotationDegrees(age * (14.0F + 10.0F * hash(i, 5)) + 360.0F * hash(i, 6)));
            poses.mulPose(Axis.ZP.rotationDegrees(70.0F * hash(i, 7)));
            poses.scale(s, s, s);
            poses.translate(-0.5F, -0.5F, -0.5F);
            BlockState st = spray ? Blocks.SNOW_BLOCK.defaultBlockState() : KINDS[(int) (hash(i, 8) * KINDS.length) % KINDS.length];
            blocks.renderSingleBlock(st, poses, buffers, light, OverlayTexture.NO_OVERLAY);
            poses.popPose();
        }
        poses.popPose();
        super.render(e, yaw, partialTick, poses, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AvalancheEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
