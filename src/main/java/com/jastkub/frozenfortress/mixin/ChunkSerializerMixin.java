package com.jastkub.frozenfortress.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * THE CITADEL UNDER ITS NEW NAME. A chunk saved before keeps the old name in its
 * structure starts and references; read under it they would be unknown and dropped - the citadels already built would
 * stop being the citadel (no garrison, no ban on building, no locating). So as a chunk is read, the old name is
 * written over with the new one, in its starts, their own "id", and its references.
 */
@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerMixin {

    @Unique
    private static final String OLD = "frozen_dominion:frozen_fortress", NEW = "frozen_dominion:frozen_citadel";

    @Inject(method = "unpackStructureStart", at = @At("HEAD"))
    private static void frozenDominion$renameStarts(StructurePieceSerializationContext context, CompoundTag tag, long seed,
                                                    CallbackInfoReturnable<?> cir) {
        frozenDominion$rename(tag);
    }

    @Inject(method = "unpackStructureReferences", at = @At("HEAD"))
    private static void frozenDominion$renameReferences(RegistryAccess access, ChunkPos pos, CompoundTag tag,
                                                        CallbackInfoReturnable<?> cir) {
        frozenDominion$rename(tag);
    }

    @Unique
    private static void frozenDominion$rename(CompoundTag structures) {
        CompoundTag starts = structures.getCompound("starts");
        if (starts.contains(OLD)) {
            CompoundTag start = starts.getCompound(OLD);
            starts.remove(OLD);
            if (OLD.equals(start.getString("id"))) {
                start.putString("id", NEW);
            }
            starts.put(NEW, start);
        }
        CompoundTag refs = structures.getCompound("References");
        if (refs.contains(OLD)) {
            refs.put(NEW, refs.get(OLD));
            refs.remove(OLD);
        }
    }
}
