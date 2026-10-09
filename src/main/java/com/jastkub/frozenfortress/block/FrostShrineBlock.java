package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.block.entity.FrostShrineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * KAPLICZKA SZRONU - the station before every boss's door.
 *
 * <p>Two things at once, for whoever is about to go through that door:
 * <ul>
 *   <li>A CHECKPOINT: a hand laid on it KINDLES it (stage 2), and whoever did it comes back to life in front of it
 *   from then on - until they kindle another, or sleep in a bed. It heals nothing.</li>
 *   <li>A RELIQUARY: whoever dies in the boss's hall behind the door (or, before the throne hall, in the Eye of the
 *   Storm) finds everything they carried kept in the casket in its plinth (FrostShrineBlockEntity) - lit (FULL) while
 *   it keeps anybody's things. The same hand laid on it gives them back.</li>
 * </ul>
 *
 * <p>Vanilla's respawn anchor would not do: outside the Nether it blows up in your face. The respawn here is a FORCED
 * one onto the cell before it (ServerPlayer.setRespawnPosition(.., forced)), which needs nothing there but room to
 * stand.
 */
public class FrostShrineBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** 1 its embers stirring (ready), 2 kindled. (0 was "cold, its keeper lives" - it is ready as 1 is.) */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);
    public static final int STIRRING = 1, KINDLED = 2;
    /** It keeps somebody's things. */
    public static final BooleanProperty FULL = BooleanProperty.create("full");

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);

    public FrostShrineBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE)
                .strength(-1.0F, 3600000.0F)                     // part of the citadel: nothing breaks it
                .sound(SoundType.STONE)
                .lightLevel(s -> s.getValue(STAGE) == KINDLED ? 13 : s.getValue(FULL) ? 9 : 5)
                .noOcclusion()
                .noLootTable()
                .pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(STAGE, STIRRING)
                .setValue(FULL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE, FULL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /** Turned with the structure it is in (the watchtower is a jigsaw piece, turned any of four ways): its front with
     *  it - and its block entity turns its Room to match (FrostShrineBlockEntity.load). */
    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FrostShrineBlockEntity(pos, state);
    }

    /** (1.21) a hand laid on it, whatever is in it: an item in the hand passes through useItemOn to here. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        // one hand on it, both of its offices: what it keeps for you back, and your return here
        boolean gave = level.getBlockEntity(pos) instanceof FrostShrineBlockEntity be && be.giveBack(sp);
        return kindle(level.getBlockState(pos), level, pos, sp, gave);
    }

    /** It kindles (if it has not been) and takes this one's return. `quiet`: something else was just said. */
    public static InteractionResult kindle(BlockState state, Level level, BlockPos pos, ServerPlayer sp, boolean quiet) {
        Direction facing = state.getValue(FACING);
        BlockPos stand = pos.relative(facing);
        if (level.dimension().equals(sp.getRespawnDimension()) && stand.equals(sp.getRespawnPosition())) {
            if (!quiet) {
                sp.displayClientMessage(Component.translatable("message.frozen_dominion.shrine_already"), true);
            }
            return InteractionResult.CONSUME;
        }
        if (state.getValue(STAGE) != KINDLED) {
            level.setBlock(pos, state.setValue(STAGE, KINDLED), Block.UPDATE_ALL);      // (its kindling plays)
        } else if (level.getBlockEntity(pos) instanceof FrostShrineBlockEntity be) {
            // burning already: it takes you in all the same
            be.triggerAnim("remember", "remember");
        }
        // back on your feet before it, looking out into the room before the door
        sp.setRespawnPosition(level.dimension(), stand, facing.toYRot(), true, false);
        sp.displayClientMessage(Component.translatable("message.frozen_dominion.shrine_kindled"), true);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0F, 1.25F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2F, 0.7F);
        return InteractionResult.CONSUME;
    }
}
