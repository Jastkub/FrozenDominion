package com.jastkub.frozenfortress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * An icicle, built the way a stalactite is: one to three blocks of it, thick
 * where it hangs from the vault and drawn out to a point. Each block is one
 * section - base, middle, frustum, tip - so a long icicle is a column of
 * them, exactly like pointed dripstone, but of ice and never falling.
 */
public class FrostIcicleBlock extends Block {

    public static final DirectionProperty TIP_DIRECTION = BlockStateProperties.VERTICAL_DIRECTION;
    public static final EnumProperty<DripstoneThickness> THICKNESS = BlockStateProperties.DRIPSTONE_THICKNESS;

    private static final VoxelShape TIP_DOWN = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 16.0D, 11.0D);
    private static final VoxelShape TIP_UP = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 11.0D, 11.0D);
    private static final VoxelShape TIP_MERGE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);
    private static final VoxelShape FRUSTUM = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);
    private static final VoxelShape MIDDLE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);
    private static final VoxelShape BASE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);

    public FrostIcicleBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE)
                .strength(0.6F)
                .sound(SoundType.GLASS)
                .lightLevel(state -> 3)
                .noOcclusion()
                .dynamicShape()
                .pushReaction(PushReaction.DESTROY));
        registerDefaultState(stateDefinition.any()
                .setValue(TIP_DIRECTION, Direction.DOWN)
                .setValue(THICKNESS, DripstoneThickness.TIP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIP_DIRECTION, THICKNESS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(THICKNESS)) {
            case TIP_MERGE -> TIP_MERGE;
            case TIP -> state.getValue(TIP_DIRECTION) == Direction.DOWN ? TIP_DOWN : TIP_UP;
            case FRUSTUM -> FRUSTUM;
            case MIDDLE -> MIDDLE;
            default -> BASE;
        };
    }

    /**
     * Its point, standing up from a floor (the Rift's spikes): a fall onto it hurts half again as much, and counts
     * as a stalagmite's. (Pointed dripstone's is twice; a fall into the Rift is long enough as it is.)
     */
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (state.getValue(TIP_DIRECTION) == Direction.UP && state.getValue(THICKNESS) == DripstoneThickness.TIP) {
            entity.causeFallDamage(fallDistance + 2.0F, 1.5F, level.damageSources().stalagmite());
            if (entity instanceof net.minecraft.server.level.ServerPlayer sp && sp.isAlive() && fallDistance > 4.0F) {
                com.jastkub.frozenfortress.FFAdvancements.grant(sp, "acupuncture", "survived");
            }
        } else {
            super.fallOn(level, state, pos, entity, fallDistance);
        }
    }

    /** Placed by hand: a tip, pointing away from whatever it was put against. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction tip = face == Direction.UP ? Direction.UP : Direction.DOWN;
        return defaultBlockState().setValue(TIP_DIRECTION, tip).setValue(THICKNESS, DripstoneThickness.TIP);
    }
}
