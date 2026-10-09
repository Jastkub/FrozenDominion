package com.jastkub.frozenfortress.worldgen;

import com.jastkub.frozenfortress.registry.FFStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * One tile of the citadel, placed as it was cut: no rotation, no mirror.
 * The building has one orientation - its approach faces north - because it
 * is designed, not assembled, and the tiles only line up the way they were cut.
 */
public class CitadelPiece extends TemplateStructurePiece {

    public CitadelPiece(StructureTemplateManager manager, ResourceLocation template, BlockPos pos) {
        super(FFStructures.CITADEL_TILE.get(), 0, manager, template, template.toString(), settings(), pos);
    }

    /** Read back from a saved chunk. */
    public CitadelPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(FFStructures.CITADEL_TILE.get(), tag, manager, location -> settings());
    }

    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                // the template carves its own air: the land's water must not
                // be kept inside the walls it replaces
                .setKeepLiquids(false);
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
                                    RandomSource random, BoundingBox box) {
        // the citadel carries no data markers
    }
}
