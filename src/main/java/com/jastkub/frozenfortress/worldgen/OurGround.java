package com.jastkub.frozenfortress.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Map;
import java.util.Optional;

/**
 * OUR GROUND: the citadel, the
 * watchtower and the expedition camp come first. Minecraft keeps a set of structures apart from itself and from at
 * most one other set; every mod rolls its places on its own, so another mod's ruin could stand in our camp.
 *
 * <p>Where ours will stand is known before any of it is built - it follows from the world's seed, whichever chunk
 * comes first. So whenever any other structure is about to be started (ChunkGeneratorMixin), the places of ours near
 * it are worked out (the same placement, the same generation, kept once worked out) and if its box would come within
 * {@link #MARGIN} blocks of one of ours, it is not started at all - whole, not cut. Among ours: the citadel, then
 * the watchtower, then the camp. Ours come as often as before; only what would stand in them goes.
 */
public final class OurGround {

    /** Ours, first first: their structures and their sets. */
    private static final ResourceLocation[] OURS = {
            new ResourceLocation("frozen_dominion", "frozen_citadel"),
            new ResourceLocation("frozen_dominion", "frozen_watchtower"),
            new ResourceLocation("frozen_dominion", "expedition_camp")};
    /** How far (chunks) each reaches from its start chunk at most: the citadel 240 across, the others under 48. */
    private static final int[] REACH = {9, 4, 4};
    /** The room kept round ours, blocks. */
    public static final int MARGIN = 8;

    /** The structure state of the chunk being started on this thread (set round createStructures). */
    public static final ThreadLocal<ChunkGeneratorStructureState> STATE = new ThreadLocal<>();
    /** How deep in a structure's placing this thread is (StructureStartMixin): its blocks are let through. */
    private static final ThreadLocal<int[]> PLACING = ThreadLocal.withInitial(() -> new int[1]);

    public static void placing(boolean in) {
        int[] d = PLACING.get();
        d[0] = Math.max(0, d[0] + (in ? 1 : -1));
    }

    /** Is a structure (any: ours, or one let in before ours) placing its blocks on this thread now? */
    public static boolean placingStructure() {
        return PLACING.get()[0] > 0;
    }

    /** The feature being placed on this thread (FeatureMixin): how deep, and whether a block of it was kept out. */
    private static final ThreadLocal<int[]> FEATURE = ThreadLocal.withInitial(() -> new int[2]);

    /** Is a feature being placed on this thread (the outermost one guarded by FeatureMixin)? */
    public static boolean inFeature() {
        return FEATURE.get()[0] > 0;
    }

    public static void feature(boolean in) {
        int[] f = FEATURE.get();
        if (in) {
            if (f[0]++ == 0) {
                f[1] = 0;
            }
        } else {
            f[0] = Math.max(0, f[0] - 1);
        }
    }

    /** WorldGenRegionMixin kept a block of the feature being placed out of ours. */
    public static void keptOut() {
        FEATURE.get()[1] = 1;
    }

    /** Was any block of the feature being placed kept out of ours (so it may have broken on what it expected)? */
    public static boolean keptOutAny() {
        return FEATURE.get()[1] > 0;
    }

    /**
     * The pieces of ours that reach into this chunk or the eight round it (all a feature placed from it can touch) -
     * WorldGenRegionMixin keeps the world's features out of them. None if the chunks know of no start of ours yet.
     */
    public static java.util.List<BoundingBox> boxesAbout(net.minecraft.world.level.WorldGenLevel level,
                                                         net.minecraft.world.level.StructureManager manager,
                                                         ChunkPos at) {
        java.util.List<BoundingBox> out = new java.util.ArrayList<>();
        try {
            Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            java.util.Set<StructureStart> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (StructureStart s : manager.startsForStructure(new ChunkPos(at.x + dx, at.z + dz),
                            st -> rank(structures.getKey(st)) < OURS.length)) {
                        if (s.isValid() && seen.add(s)) {
                            for (net.minecraft.world.level.levelgen.structure.StructurePiece p : s.getPieces()) {
                                out.add(p.getBoundingBox());
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            return java.util.List.of();                       // (chunks not that far along: nothing kept out)
        }
        return out;
    }

    private OurGround() {
    }

    /** 0, 1, 2 for ours (the citadel first); OURS.length for anything else. */
    public static int rank(ResourceLocation id) {
        for (int i = 0; i < OURS.length; i++) {
            if (OURS[i].equals(id)) {
                return i;
            }
        }
        return OURS.length;
    }

    /**
     * Must this start give way - would it come within MARGIN of one of ours that comes before it?
     *
     * @param kept the generator's own record of where ours stand (or do not), by set and chunk
     */
    public static boolean mustGiveWay(ChunkGenerator gen, Map<Long, Optional<BoundingBox>> kept, Structure structure,
                                      StructureStart start, RegistryAccess access, RandomState random,
                                      StructureTemplateManager templates, long seed, LevelHeightAccessor height) {
        Registry<Structure> structures = access.registryOrThrow(Registries.STRUCTURE);
        int rank = rank(structures.getKey(structure));
        if (rank == 0) {
            return false;                                     // the citadel gives way to nothing
        }
        Registry<StructureSet> sets = access.registryOrThrow(Registries.STRUCTURE_SET);
        ChunkGeneratorStructureState state = STATE.get();
        BoundingBox box = start.getBoundingBox();
        for (int i = 0; i < rank; i++) {
            Holder<StructureSet> set = sets.getHolder(ResourceKey.create(Registries.STRUCTURE_SET, OURS[i])).orElse(null);
            Structure ours = structures.get(OURS[i]);
            if (set == null || ours == null || !(set.value().placement() instanceof RandomSpreadStructurePlacement spread)) {
                continue;
            }
            if (state != null && !state.possibleStructureSets().contains(set)) {
                continue;                                     // not in this world's kind of land (the Nether, the End)
            }
            int r = REACH[i];
            int cx0 = SectionPos.blockToSectionCoord(box.minX() - MARGIN) - r;
            int cx1 = SectionPos.blockToSectionCoord(box.maxX() + MARGIN) + r;
            int cz0 = SectionPos.blockToSectionCoord(box.minZ() - MARGIN) - r;
            int cz1 = SectionPos.blockToSectionCoord(box.maxZ() + MARGIN) + r;
            int sp = spread.spacing();
            for (int rx = Math.floorDiv(cx0, sp); rx <= Math.floorDiv(cx1, sp); rx++) {
                for (int rz = Math.floorDiv(cz0, sp); rz <= Math.floorDiv(cz1, sp); rz++) {
                    ChunkPos c = spread.getPotentialStructureChunk(seed, rx * sp, rz * sp);
                    if (c.x < cx0 || c.x > cx1 || c.z < cz0 || c.z > cz1) {
                        continue;
                    }
                    if (state != null && !spread.isStructureChunk(state, c.x, c.z)) {
                        continue;                             // its own spread or exclusion rules it out there
                    }
                    long key = (c.toLong() << 2) | i;
                    Optional<BoundingBox> at = kept.get(key);
                    if (at == null) {
                        at = where(gen, ours, access, random, templates, seed, c, height);
                        Optional<BoundingBox> was = kept.putIfAbsent(key, at);
                        at = was != null ? was : at;
                    }
                    if (at.isPresent() && at.get().inflatedBy(MARGIN).intersects(box)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Where one of ours stands if it is started at `c` - the very generation the world will run there. */
    private static Optional<BoundingBox> where(ChunkGenerator gen, Structure ours, RegistryAccess access,
                                               RandomState random, StructureTemplateManager templates, long seed,
                                               ChunkPos c, LevelHeightAccessor height) {
        try {
            StructureStart s = ours.generate(access, gen, gen.getBiomeSource(), random, templates, seed, c, 0, height,
                    ours.biomes()::contains);
            return s.isValid() ? Optional.of(s.getBoundingBox()) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
