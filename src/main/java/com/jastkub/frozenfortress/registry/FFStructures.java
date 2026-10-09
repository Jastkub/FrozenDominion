package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.worldgen.CitadelPiece;
import com.jastkub.frozenfortress.worldgen.CitadelStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The citadel's structure type and the type of its tiles. */
public final class FFStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, FrozenFortress.MODID);
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, FrozenFortress.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<CitadelStructure>> CITADEL =
            STRUCTURE_TYPES.register("citadel", FFStructures::citadelType);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> CITADEL_TILE =
            PIECE_TYPES.register("citadel_tile", FFStructures::tileType);

    /** The Chasm of Bones' shaft down to the bedrock (CitadelShaftPiece). */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> CITADEL_SHAFT =
            PIECE_TYPES.register("citadel_shaft", FFStructures::shaftType);

    private static StructureType<CitadelStructure> citadelType() {
        return () -> asMap(CitadelStructure.CODEC);
    }

    // (1.21.1: a structure type hands out a MapCodec; these take the structure's codec whichever it is)
    private static <S> com.mojang.serialization.MapCodec<S> asMap(com.mojang.serialization.Codec<S> codec) {
        return com.mojang.serialization.MapCodec.assumeMapUnsafe(codec);
    }

    private static <S> com.mojang.serialization.MapCodec<S> asMap(com.mojang.serialization.MapCodec<S> codec) {
        return codec;
    }

    private static StructurePieceType tileType() {
        return (StructurePieceType.StructureTemplateType) CitadelPiece::new;
    }

    private static StructurePieceType shaftType() {
        return com.jastkub.frozenfortress.worldgen.CitadelShaftPiece::new;
    }

    private FFStructures() {
    }
}
