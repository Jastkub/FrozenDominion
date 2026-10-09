package com.jastkub.frozenfortress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The crust a bomb leaves, and the colossus's fuel.
 *
 * <p>This is the piece that turns three separate attacks into one system. The
 * bombs put it down, the drain takes it back up, and taking it back up is what
 * heals the thing and reloads the bombs. So the question the player is being
 * asked by the ranged game is not "how do I dodge this" - it is "how much of
 * the floor am I willing to leave white".
 *
 * <p><b>It is breakable, and that is the whole point.</b> The floor crack next
 * to it is unbreakable on purpose: it is decoration with a two-second life and
 * letting anybody mine it only added a way to leave holes in an arena. This is
 * the opposite - it is a resource, it persists, and clearing it has to be
 * something a player SPENDS TIME on. Instant to break, so clearing is a
 * decision about where to stand rather than a mining minigame.
 *
 * <p>No collision and no occlusion. It hurts nobody and blocks nothing; a
 * crust that also slowed you down would be a damage-and-control tool and the
 * attack it belongs to already does both of those.
 */
public class RimeSheetBlock extends Block {

    /** Only what the outline highlights. Nothing may trip over frost. */
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 0.05D, 16.0D);

    public RimeSheetBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.SNOW)
                .instabreak()
                .noCollission()
                .noOcclusion()
                .noLootTable()
                .sound(SoundType.GLASS)
                .pushReaction(PushReaction.DESTROY)
                // Replaceable, so nothing it lands on can ever be in the way:
                // build over it, walk through it, put a block where it is.
                .replaceable());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level,
                              BlockPos pos) {
        // It lies ON something. Break the floor and the frost goes with it,
        // which is also a way for a player to clear a patch in one swing.
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below,
                net.minecraft.core.Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, net.minecraft.core.Direction dir,
                                  BlockState neighbour,
                                  net.minecraft.world.level.LevelAccessor level,
                                  BlockPos pos, BlockPos neighbourPos) {
        return canSurvive(state, level, pos)
                ? state
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }
}
