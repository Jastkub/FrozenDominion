package com.jastkub.frozenfortress.mixin;

import com.jastkub.frozenfortress.worldgen.OurGround;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTHER STRUCTURES GIVE WAY TO OURS BEFORE THEY ARE EVER STARTED: a structure
 * just generated for a chunk, not ours (or ours but after one of ours in rank), whose box would come into one of
 * ours, is turned into no start at all - so it is never built, not even in part.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    /** Where ours stand (or do not), worked out once per set and chunk for this world's generator. */
    @Unique
    private final Map<Long, Optional<BoundingBox>> frozenDominion$ours = new ConcurrentHashMap<>();

    @Inject(method = "createStructures", at = @At("HEAD"))
    private void frozenDominion$stateIn(RegistryAccess access, ChunkGeneratorStructureState state, StructureManager manager,
                                        ChunkAccess chunk, StructureTemplateManager templates, CallbackInfo ci) {
        OurGround.STATE.set(state);
    }

    @Inject(method = "createStructures", at = @At("RETURN"))
    private void frozenDominion$stateOut(RegistryAccess access, ChunkGeneratorStructureState state, StructureManager manager,
                                         ChunkAccess chunk, StructureTemplateManager templates, CallbackInfo ci) {
        OurGround.STATE.remove();
    }

    @ModifyVariable(method = "tryGenerateStructure", at = @At("STORE"), ordinal = 0)
    private StructureStart frozenDominion$giveWay(StructureStart start, StructureSet.StructureSelectionEntry entry,
                                                  StructureManager manager, RegistryAccess access, RandomState random,
                                                  StructureTemplateManager templates, long seed, ChunkAccess chunk,
                                                  ChunkPos pos, SectionPos section) {
        if (!start.isValid()) {
            return start;
        }
        try {
            if (OurGround.mustGiveWay((ChunkGenerator) (Object) this, frozenDominion$ours, entry.structure().value(), start,
                    access, random, templates, seed, chunk)) {
                return StructureStart.INVALID_START;
            }
        } catch (RuntimeException e) {
            // never let the check itself break a world's generation: the structure stands as it would have
        }
        return start;
    }
}
