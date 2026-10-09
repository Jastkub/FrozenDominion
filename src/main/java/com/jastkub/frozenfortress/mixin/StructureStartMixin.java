package com.jastkub.frozenfortress.mixin;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * OTHER STRUCTURES GIVE WAY TO THE CITADEL. Minecraft keeps the pieces of one structure apart from each other,
 * and a set of structures apart from one other set - nothing more: any other mod's dungeon, a
 * mineshaft, can be laid into the same chunks as the citadel.
 *
 * <p>So when any structure that is not ours places its pieces in a chunk the Frozen Fortress
 * occupies (the fortress's start is referenced there and its box meets the other's), that chunk's
 * share of it is not placed. Outside the citadel's chunks it generates as it always did.
 *
 * <p>(06.10.2026) Now the second line: ChunkGeneratorMixin stops such a structure before it is started at all, for
 * all three of ours (OurGround). This stays for the starts made before that - worlds already begun - and covers the
 * watchtower and the camp too, in the same order: the citadel, the watchtower, the camp.
 */
@Mixin(StructureStart.class)
public abstract class StructureStartMixin {

    @Inject(method = "placeInChunk", at = @At("HEAD"), cancellable = true)
    private void frozenDominion$giveWay(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                        RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, CallbackInfo ci) {
        StructureStart self = (StructureStart) (Object) this;
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        int rank = com.jastkub.frozenfortress.worldgen.OurGround.rank(structures.getKey(self.getStructure()));
        if (rank != 0) {
            for (StructureStart ours : manager.startsForStructure(chunkPos,
                    s -> com.jastkub.frozenfortress.worldgen.OurGround.rank(structures.getKey(s)) < rank)) {
                if (ours != self && ours.isValid() && ours.getBoundingBox().intersects(self.getBoundingBox())) {
                    ci.cancel();
                    return;
                }
            }
        }
        // (08.10.2026) a structure placing: its blocks go where they go - only features are kept out of ours
        // (WorldGenRegionMixin)
        com.jastkub.frozenfortress.worldgen.OurGround.placing(true);
    }

    @Inject(method = "placeInChunk", at = @At("RETURN"))
    private void frozenDominion$placed(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                       RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, CallbackInfo ci) {
        com.jastkub.frozenfortress.worldgen.OurGround.placing(false);
    }
}
