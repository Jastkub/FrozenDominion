package com.jastkub.frozenfortress.worldgen;

import com.jastkub.frozenfortress.registry.FFStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The Frozen Citadel: one building cut into tiles, every tile placed at its
 * own fixed offset from a single origin.
 *
 * <p>NOT A JIGSAW. A jigsaw assembles a structure out of parts it chooses and
 * turns; the citadel is one designed thing, read as a whole from its approach
 * stair to the arena under it, and the only job here is to put each piece of
 * it back exactly where it was cut from. The tiles exist because a single
 * template of this size would be walked in full for every chunk it touches.
 *
 * <p>THE HEIGHT. The citadel's own terrain level (the foot of its plinth) is
 * put a little under the land's median height, sampled on a seven-by-seven grid
 * over its footprint - so one hummock or one hollow under the middle cannot lift
 * the whole hill-fort into the air or bury its courtyard. And a place it cannot
 * stand on (a valley under part of it, a mountain, water) is refused outright.
 */
public class CitadelStructure extends Structure {

    /** One tile: its template and where its corner sits in the citadel. */
    public record Tile(String name, int x, int z) {
        public static final Codec<Tile> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Tile::name),
                Codec.INT.fieldOf("x").forGetter(Tile::x),
                Codec.INT.fieldOf("z").forGetter(Tile::z)
        ).apply(i, Tile::new));
    }

    /** A hall whose floor goes on down to the bedrock (CitadelShaftPiece): its interior, [x0, x1) x [z0, z1) in the
     *  citadel, and the y of its floor. */
    public record Shaft(int x0, int z0, int x1, int z1, int floor) {
        public static final Codec<Shaft> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x0").forGetter(Shaft::x0),
                Codec.INT.fieldOf("z0").forGetter(Shaft::z0),
                Codec.INT.fieldOf("x1").forGetter(Shaft::x1),
                Codec.INT.fieldOf("z1").forGetter(Shaft::z1),
                Codec.INT.fieldOf("floor").forGetter(Shaft::floor)
        ).apply(i, Shaft::new));
    }

    public static final Codec<CitadelStructure> CODEC = RecordCodecBuilder.create(i -> i.group(
            settingsCodec(i),
            Codec.INT.fieldOf("size_x").forGetter(s -> s.sizeX),
            Codec.INT.fieldOf("size_z").forGetter(s -> s.sizeZ),
            Codec.INT.fieldOf("terrain_level").forGetter(s -> s.terrainLevel),
            Tile.CODEC.listOf().fieldOf("tiles").forGetter(s -> s.tiles),
            Shaft.CODEC.listOf().optionalFieldOf("shafts", List.of()).forGetter(s -> s.shafts)
    ).apply(i, CitadelStructure::new));

    /** How far the land under it may fall (its lowest tenth) or rise (its highest tenth) from the middle. */
    // loosened
    // from 10 and 35, and water from a sixth to a quarter - with its regions closer together (structure_set: 72/48)
    private static final int MAX_DIP = 14, MAX_RISE = 40;

    private final int sizeX;
    private final int sizeZ;
    /** The template's y where the land meets the foot of the plinth. */
    private final int terrainLevel;
    private final List<Tile> tiles;
    private final List<Shaft> shafts;

    public CitadelStructure(StructureSettings settings, int sizeX, int sizeZ, int terrainLevel, List<Tile> tiles,
                            List<Shaft> shafts) {
        super(settings);
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.terrainLevel = terrainLevel;
        this.tiles = tiles;
        this.shafts = shafts;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos cp = ctx.chunkPos();
        int cx = cp.getMiddleBlockX();
        int cz = cp.getMiddleBlockZ();
        int x0 = cx - sizeX / 2;
        int z0 = cz - sizeZ / 2;

        // ITS BIOME FIRST (07.10.2026: /locate took 8 to 62 seconds). The world asks for the biome only after this
        // method has answered - so every candidate anywhere, desert and ocean alike, paid for the ninety-eight height
        // samples below before being turned down for not being snowy plains. One sample and one biome look settle most.
        int mid = ctx.chunkGenerator().getBaseHeight(cx, cz, Heightmap.Types.WORLD_SURFACE_WG,
                ctx.heightAccessor(), ctx.randomState());
        if (!ctx.validBiome().test(ctx.biomeSource().getNoiseBiome(QuartPos.fromBlock(cx), QuartPos.fromBlock(mid),
                QuartPos.fromBlock(cz), ctx.randomState().sampler()))) {
            return Optional.empty();
        }

        // THE LAND UNDER IT, sampled on a seven-by-seven grid over the whole footprint - and the sea floor beside it
        int n = 7;
        int[] heights = new int[n * n];
        int wet = 0;
        int k = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int x = x0 + 8 + i * (sizeX - 16) / (n - 1);
                int z = z0 + 24 + j * (sizeZ - 40) / (n - 1);
                int top = ctx.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                        ctx.heightAccessor(), ctx.randomState());
                int bed = ctx.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                        ctx.heightAccessor(), ctx.randomState());
                if (top - bed >= 2) {
                    wet++;                                     // water standing over the ground here
                }
                heights[k++] = Math.min(top, bed + 1);
            }
        }
        Arrays.sort(heights);
        int median = heights[heights.length / 2];
        int low = heights[heights.length / 10];               // the lowest tenth
        int high = heights[heights.length - 1 - heights.length / 10];
        // ONLY WHERE IT CAN STAND: over a valley, a gorge, a lake or a sea its plinth hangs in the air and its
        // underground opens to the sky - so such a place is turned down and the world tries the next: a tenth of the
        // footprint more than MAX_DIP under the middle, a mountain more than MAX_RISE over it, or water over a quarter
        if (median - low > MAX_DIP || high - median > MAX_RISE || wet > heights.length / 4) {
            return Optional.empty();
        }
        // and a little low rather than at the middle: a hummock buries a step of the plinth, a hollow shows none of it
        int surface = heights[heights.length * 2 / 5];
        int y0 = surface - terrainLevel;
        // NOT INTO THE BEDROCK. The prison sits a hundred blocks down; on low
        // land that would put its floor in the bedrock layers, where a block
        // replaced is a hole to the void. The whole citadel rises instead -
        // a few blocks of plinth showing are better than a breach.
        int floor = ctx.heightAccessor().getMinBuildHeight() + 8;
        if (y0 < floor) {
            y0 = floor;
        }
        BlockPos origin = new BlockPos(x0, y0, z0);
        int bottom = ctx.heightAccessor().getMinBuildHeight();
        return Optional.of(new GenerationStub(new BlockPos(cx, surface, cz), builder -> {
            for (Tile t : tiles) {
                builder.addPiece(new CitadelPiece(ctx.structureTemplateManager(),
                        ResourceLocation.parse(t.name()), origin.offset(t.x(), 0, t.z())));
            }
            // (after the tiles: a shaft reads the hall's floor they have laid - pillar or open)
            for (Shaft s : shafts) {
                builder.addPiece(new CitadelShaftPiece(origin, s, bottom));
            }
        }));
    }

    @Override
    public StructureType<?> type() {
        return FFStructures.CITADEL.get();
    }
}
