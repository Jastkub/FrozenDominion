package com.jastkub.frozenfortress.mixin;

import com.jastkub.frozenfortress.worldgen.OurGround;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * NOTHING OF THE WORLD'S GROWS INTO OURS. A feature - a geode, a vein of ore, a lake of lava, a tree - is placed from
 * its own chunk, and reaches over into the next one, which may have been finished already: the citadel stood there,
 * and the geode carved itself into one of its halls. So while the world is decorated, no block a feature sets lands
 * within a piece of ours (OurGround.boxesAbout: the citadel, the watchtower, the camp) - only what the structures
 * themselves place (OurGround.placingStructure), and the winter's own top layer (snow and ice, which freeze on the
 * citadel's roofs as on anything).
 */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {

    @Shadow
    @Final
    private StructureManager structureManager;

    @Shadow
    @Final
    private ChunkAccess center;

    /** Our pieces about this region's chunk: worked out the first time a block is set in it. */
    @Unique
    private List<BoundingBox> frozenDominion$ours;

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void frozenDominion$keepOut(BlockPos pos, BlockState state, int flags, int depth,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (OurGround.placingStructure()) {
            return;
        }
        List<BoundingBox> ours = frozenDominion$ours;
        if (ours == null) {
            ours = frozenDominion$ours = OurGround.boxesAbout((WorldGenRegion) (Object) this, structureManager,
                    center.getPos());
        }
        if (ours.isEmpty() || state.is(Blocks.SNOW) || state.is(Blocks.ICE)) {
            return;
        }
        for (BoundingBox b : ours) {
            if (b.isInside(pos)) {
                OurGround.keptOut();
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
