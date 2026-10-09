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
 * The split the floor takes when something lands on it.
 *
 * <p>Deliberately laid ON the floor rather than swapped into it. Swapping
 * only works on blocks we are willing to replace, and the throne room is
 * sealed masonry that must never be touched - so a swap-based scar was
 * invisible in the one arena where it matters most. A block placed on top
 * shows up over anything, ice included, and cannot weaken what is under it.
 *
 * <p>It is no longer a flat decal. The model
 * ({@code tools/gen_models_scar.py}) is four different splits at four turns
 * each, with ice thrown up off the floor at angles and crossing the block
 * boundary, so a hundred of them read as one broken floor rather than as a
 * hundred copies of the same square.
 *
 * <p><b>Unbreakable, on purpose.</b> It has a two-second life and removes
 * itself; letting a player mine it added nothing except a way to leave holes
 * in an arena and a way to hear a mining sound in the middle of a boss fight.
 * Anything may still be built over it - it is replaceable - so it can never
 * be in the way either.
 *
 * <p>Placed and removed by {@link com.jastkub.frozenfortress.event.FloorScarHandler}.
 */
public class FloorCrackBlock extends Block {

    /** No collision at all: the shards stand off the floor and nothing may
     *  trip over them. The shape is only what the outline highlights. */
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 0.05D, 16.0D);

    public FloorCrackBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE)
                .strength(-1.0F, 3600000.0F)
                .noCollission()
                .noOcclusion()
                .noLootTable()
                // NO lightLevel. The neon comes from the model's own
                // fullbright faces, which cost nothing; a real light level
                // would queue a lighting update for every one of the several
                // hundred of these a single fight puts down and take them all
                // back out again two seconds later.
                .sound(SoundType.GLASS)
                .pushReaction(PushReaction.DESTROY)
                .replaceable());
    }

    /**
     * How long one crack lasts if nothing else removes it, in ticks.
     *
     * <p>Longer than FloorScarHandler's own timer on purpose: that one is the
     * normal path and this is the backstop, so it should not be racing it.
     */
    private static final int SELF_CLEAR = 200;

    /**
     * ================================================================
     * IT CLEANS ITSELF UP, because the handler's memory does not survive.
     *
     * FloorScarHandler keeps its pending list in a static queue and sweeps it
     * on the level tick. That works perfectly inside one session and not at
     * all across a restart: the moment the game is closed, every crack
     * currently on the floor is orphaned - the blocks are saved with the
     * chunk, the queue that was going to remove them is not. Nothing ever
     * takes them out again.
     *
     * That is why the arena silts up. It is not a fight leaving too much
     * behind, it is many fights leaving a little behind each time and never
     * being cleaned - and restarting to try a new build is exactly what
     * triggers it, so it got worse the more it was tested.
     *
     * A scheduled tick is stored in the chunk with the block, so it survives
     * the world being closed and reopened. The handler still does the normal
     * clearing; this is what catches the ones it forgot about.
     * ================================================================
     */
    @Override
    public void onPlace(BlockState state, net.minecraft.world.level.Level level,
                        BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, SELF_CLEAR);
        }
    }

    @Override
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level,
                     BlockPos pos, net.minecraft.util.RandomSource random) {
        level.removeBlock(pos, false);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** Nothing should ever be standing on it, and nothing should suffocate. */
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public boolean canBeReplaced(BlockState state, net.minecraft.world.item.context.BlockPlaceContext ctx) {
        return true;
    }
}
