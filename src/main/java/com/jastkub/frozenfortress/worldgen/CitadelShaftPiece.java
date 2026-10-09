package com.jastkub.frozenfortress.worldgen;

import com.jastkub.frozenfortress.block.FrostIcicleBlock;
import com.jastkub.frozenfortress.registry.FFBlocks;
import com.jastkub.frozenfortress.registry.FFStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * THE CHASM GOES DOWN TO THE BEDROCK. The citadel's
 * tiles stand wherever the land puts them - ten to thirty blocks over the world's floor - so the Chasm of Bones' depth
 * cannot be in them: under its hall (the structure's "shafts", written by tools/citadel/export.py from CHASM) this piece
 * goes on down through the rock to the bedrock, after the tiles are in place:
 * <ul>
 *   <li>every cell of the hall's floor that is open is carved open down to the bedrock, and on the bedrock (or what
 *   stands of it) the spikes - a frost icicle on every cell, half of them tall, as the floor of the hall had them;</li>
 *   <li>every cell of it that is a pillar's stone (the bridges' pillars, the heart's) goes on down in that stone;</li>
 *   <li>and round it all a wall of deepslate brick two thick, from the bedrock up to the hall's own, so the caves'
 *   water and lava stay where they are.</li>
 * </ul>
 * The bedrock itself is never touched.
 */
public class CitadelShaftPiece extends StructurePiece {

    /** The wall round the shaft, cells thick. */
    private static final int RING = 2;
    private static final DripstoneThickness[][] STACKS = {
            {DripstoneThickness.FRUSTUM, DripstoneThickness.TIP},
            {DripstoneThickness.BASE, DripstoneThickness.FRUSTUM, DripstoneThickness.TIP},
            {DripstoneThickness.BASE, DripstoneThickness.MIDDLE, DripstoneThickness.FRUSTUM, DripstoneThickness.TIP}};

    /** The hall's interior in the world, [x0, x1) x [z0, z1), and the y of its floor (its first open cell). */
    private final int x0, z0, x1, z1, floorY;

    public CitadelShaftPiece(BlockPos origin, CitadelStructure.Shaft s, int minY) {
        super(FFStructures.CITADEL_SHAFT.get(), 0, new BoundingBox(origin.getX() + s.x0() - RING, minY,
                origin.getZ() + s.z0() - RING, origin.getX() + s.x1() - 1 + RING, origin.getY() + s.floor(),
                origin.getZ() + s.z1() - 1 + RING));
        this.x0 = origin.getX() + s.x0();
        this.z0 = origin.getZ() + s.z0();
        this.x1 = origin.getX() + s.x1();
        this.z1 = origin.getZ() + s.z1();
        this.floorY = origin.getY() + s.floor();
    }

    /** Read back from a saved chunk. */
    public CitadelShaftPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(FFStructures.CITADEL_SHAFT.get(), tag);
        this.x0 = tag.getInt("X0");
        this.z0 = tag.getInt("Z0");
        this.x1 = tag.getInt("X1");
        this.z1 = tag.getInt("Z1");
        this.floorY = tag.getInt("Floor");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("X0", x0);
        tag.putInt("Z0", z0);
        tag.putInt("X1", x1);
        tag.putInt("Z1", z1);
        tag.putInt("Floor", floorY);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        int bottom = level.getMinBuildHeight();
        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = Math.max(box.minX(), x0 - RING); x <= Math.min(box.maxX(), x1 - 1 + RING); x++) {
            for (int z = Math.max(box.minZ(), z0 - RING); z <= Math.min(box.maxZ(), z1 - 1 + RING); z++) {
                boolean inside = x >= x0 && x < x1 && z >= z0 && z < z1;
                if (!inside) {
                    // the wall: from the bedrock up to the hall's own shell
                    for (int y = bottom; y < floorY - 1; y++) {
                        if (!level.getBlockState(p.set(x, y, z)).is(Blocks.BEDROCK)) {
                            level.setBlock(p, wall, 2);
                        }
                    }
                    continue;
                }
                BlockState top = level.getBlockState(p.set(x, floorY, z));
                if (top.isSolidRender(level, p)) {
                    // a pillar: on down in its own stone
                    for (int y = floorY - 1; y >= bottom; y--) {
                        if (!level.getBlockState(p.set(x, y, z)).is(Blocks.BEDROCK)) {
                            level.setBlock(p, top, 2);
                        }
                    }
                    continue;
                }
                // open: carved down to the bedrock...
                int foot = Integer.MIN_VALUE;
                for (int y = floorY - 1; y >= bottom; y--) {
                    if (level.getBlockState(p.set(x, y, z)).is(Blocks.BEDROCK)) {
                        if (foot == Integer.MIN_VALUE) {
                            foot = y + 1;                       // (the highest bedrock under it)
                        }
                        continue;
                    }
                    if (foot == Integer.MIN_VALUE) {
                        level.setBlock(p, air, 2);
                    }
                }
                if (foot == Integer.MIN_VALUE) {
                    foot = bottom;                              // (a world with no bedrock floor: on its last block)
                    level.setBlock(p.set(x, bottom, z), Blocks.PACKED_ICE.defaultBlockState(), 2);
                    foot++;
                }
                // ...and on it the spikes: the same draw for the same cell, whatever chunk asks
                RandomSource r = RandomSource.create(Mth.getSeed(x, 808, z));
                DripstoneThickness[] stack = r.nextFloat() < 0.5F ? STACKS[new int[]{0, 0, 1, 1, 2}[r.nextInt(5)]]
                        : STACKS[0];
                for (int k = 0; k < stack.length && foot + k < floorY - 1; k++) {
                    level.setBlock(p.set(x, foot + k, z), FFBlocks.FROST_ICICLE.get().defaultBlockState()
                            .setValue(FrostIcicleBlock.TIP_DIRECTION, Direction.UP)
                            .setValue(FrostIcicleBlock.THICKNESS, stack[k]), 2);
                }
            }
        }
    }
}