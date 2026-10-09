package com.jastkub.frozenfortress.client.render;

import com.jastkub.frozenfortress.block.StormEyeFloeBlock;
import com.jastkub.frozenfortress.entity.boss.StormEyeFloeEntity;
import com.jastkub.frozenfortress.registry.FFBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import java.util.List;

/**
 * A MOVING FLOE OF THE EYE OF THE STORM (StormEyeFloeEntity), drawn from the floe block's own models - the same ice as
 * the central floe, which is blocks - laid out cell by cell as the block floes were (the lens: a full top, a narrower
 * layer under it) and turned with the floe about its middle.
 *
 * <p>THE READ, WITHOUT A PARTICLE (the particles are the floe's own garnish):
 * <ul>
 *   <li>SOLID - clear ice;</li>
 *   <li>MARKED - the floe block's hairline-cracked look, and vanilla's breaking cracks starting over it;</li>
 *   <li>CRACKED - split through and lit from inside (it shines: the one to get off is the brightest), the breaking
 *       cracks wide;</li>
 *   <li>SLUSH - grey and wet, a little see-through, the cracks at their last stage, and the floe sinking (its lift);</li>
 *   <li>GONE - the cells go edge first, the middle last (as the hold under a body shrinks - StormEyeFloeEntity
 *       .holdingDisc);</li>
 *   <li>FORMING - a frost ghost, see-through, firming as it freezes, setting middle first.</li>
 * </ul>
 * Faces between two cells of the same kind are not drawn (as ice against ice), so the floe reads as one slab, not a
 * pile of glass boxes.
 */
public class StormEyeFloeRenderer extends EntityRenderer<StormEyeFloeEntity> {

    /** Block light each look gives off (StormEyeFloeBlock's lightLevel). */
    private static final int[] GLOW = {3, 4, 9, 5, 6};
    private static final Direction[] DIRS = Direction.values();

    private final BlockRenderDispatcher blocks;
    private final RandomSource rand = RandomSource.create();
    private int[] looks = new int[64];
    private final BlockState[] states = new BlockState[5];

    public StormEyeFloeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
        this.blocks = ctx.getBlockRenderDispatcher();
    }

    @Override
    public ResourceLocation getTextureLocation(StormEyeFloeEntity f) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public boolean shouldRender(StormEyeFloeEntity f, Frustum frustum, double x, double y, double z) {
        return f.shown(f.renderTime(0.0F)) && super.shouldRender(f, frustum, x, y, z);
    }

    private BlockState state(int look) {
        if (states[look] == null) {
            states[look] = FFBlocks.STORM_EYE_FLOE.get().defaultBlockState().setValue(StormEyeFloeBlock.STAGE, look);
        }
        return states[look];
    }

    @Override
    public void render(StormEyeFloeEntity f, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        double t = f.renderTime(pt);
        if (!f.shown(t)) {
            return;
        }
        StormEyeFloeEntity.Layout lay = StormEyeFloeEntity.layout(f.cellRadius());
        int n = lay.cells.length;
        if (looks.length < n) {
            looks = new int[n];
        }
        f.looks(t, looks);
        int st = f.state();
        int len = f.stateLength();
        float progress = len > 0 ? Mth.clamp((float) ((t - f.stateAt()) / len), 0.0F, 1.0F) : 1.0F;
        // the breaking cracks over a floe that is going: MARKED 0-2, CRACKED 3-6, SLUSH 7-9 - they spread as it goes
        int crack = switch (st) {
            case StormEyeFloeEntity.MARKED -> (int) (progress * 2.99F);
            case StormEyeFloeEntity.CRACKED -> 3 + (int) (progress * 3.99F);
            case StormEyeFloeEntity.SLUSH -> 7 + (int) (progress * 2.99F);
            default -> -1;
        };
        float ghost = 0.32F + 0.58F * (st == StormEyeFloeEntity.FORMING ? progress : 1.0F);
        int sky = LightTexture.sky(light), block = LightTexture.block(light);

        ps.pushPose();
        ps.mulPose(Axis.YP.rotation((float) -f.renderSpin(pt)));          // the floe's own turn (Motion.local's sense)
        VertexConsumer ice = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        VertexConsumer cracks = crack >= 0 ? buffers.getBuffer(ModelBakery.DESTROY_TYPES.get(crack)) : null;
        for (int i = 0; i < n; i++) {
            int look = looks[i];
            if (look == StormEyeFloeEntity.LOOK_AIR) {
                continue;
            }
            int[] c = lay.cells[i];
            boolean ghostly = look == StormEyeFloeEntity.LOOK_FORMING;
            float alpha = ghostly ? ghost : look == StormEyeFloeEntity.LOOK_SLUSH ? 0.86F : 1.0F;
            int lit = LightTexture.pack(Math.max(block, GLOW[look]), sky);
            BlockState bs = state(look);
            BakedModel model = blocks.getBlockModel(bs);
            long seed = 0x5EEDL + i * 7919L + f.slot() * 104729L;            // each cell keeps its turn of the texture
            ps.pushPose();
            ps.translate(c[0] - 0.5D, -1.0D - c[1], c[2] - 0.5D);
            PoseStack.Pose pose = ps.last();
            VertexConsumer decal = cracks != null && !ghostly && look != StormEyeFloeEntity.LOOK_FRESH
                    ? new SheetedDecalTextureGenerator(cracks, pose.pose(), pose.normal(), 1.0F) : null;
            for (int d = 0; d < 6; d++) {
                int j = lay.next[i][d];
                if (j >= 0) {
                    int other = looks[j];
                    // ice against ice draws no face between them - but a ghost hides nothing behind it
                    if (other != StormEyeFloeEntity.LOOK_AIR && (other == StormEyeFloeEntity.LOOK_FORMING) == ghostly) {
                        continue;
                    }
                }
                rand.setSeed(seed);
                List<BakedQuad> quads = model.getQuads(bs, DIRS[d], rand, ModelData.EMPTY, null);
                for (BakedQuad q : quads) {
                    ice.putBulkData(pose, q, 1.0F, 1.0F, 1.0F, alpha, lit, OverlayTexture.NO_OVERLAY, false);
                    if (decal != null) {
                        decal.putBulkData(pose, q, 1.0F, 1.0F, 1.0F, lit, OverlayTexture.NO_OVERLAY);
                    }
                }
            }
            ps.popPose();
        }
        ps.popPose();
        super.render(f, yaw, pt, ps, buffers, light);
    }
}
