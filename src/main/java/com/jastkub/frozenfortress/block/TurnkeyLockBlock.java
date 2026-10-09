package com.jastkub.frozenfortress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * A lock set in a pillar of the Turnkey's hall: an iron plate with a keyhole
 * lit blue. When one of his flung keys reaches it the key turns and the plate
 * turns with it (TURNED, for a second and a half) - and he is mended.
 * FACING is the way the plate looks, out of the wall it is set in.
 */
public class TurnkeyLockBlock extends HorizontalDirectionalBlock {

    public static final BooleanProperty TURNED = BooleanProperty.create("turned");

    private static final VoxelShape NORTH = Block.box(3, 2, 14, 13, 14, 16);
    private static final VoxelShape SOUTH = Block.box(3, 2, 0, 13, 14, 2);
    private static final VoxelShape WEST = Block.box(14, 2, 3, 16, 14, 13);
    private static final VoxelShape EAST = Block.box(0, 2, 3, 2, 14, 13);

    public TurnkeyLockBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.METAL)
                .lightLevel(s -> s.getValue(TURNED) ? 12 : 6)
                .noOcclusion()
                .noLootTable());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TURNED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TURNED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        return face.getAxis().isVertical() ? null : defaultBlockState().setValue(FACING, face);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    /** A key has turned in it: it shows for a breath, then sets back. */
    public static void turn(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof TurnkeyLockBlock && level instanceof ServerLevel s) {
            s.setBlock(pos, st.setValue(TURNED, true), 3);
            s.scheduleTick(pos, st.getBlock(), 30);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(TURNED)) {
            level.setBlock(pos, state.setValue(TURNED, false), 3);
        }
    }
}
