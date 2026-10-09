package com.jastkub.frozenfortress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The furnishings of the dead court: glass that still holds the light of a
 * lost kingdom, lanterns of cold fire, icicles that never fall, and the
 * spires that give the fortress its skyline.
 */
public final class FFDecoBlocks {

    private static BlockBehaviour.Properties glassProps(int light) {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE)
                .strength(0.4F)
                .sound(SoundType.GLASS)
                .lightLevel(state -> light)
                .noOcclusion()
                .isValidSpawn((s, l, p, e) -> false)
                .isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false);
    }

    /** Gothic window glass - deep blue, lit from within. */
    public static class GlacialGlass extends net.minecraft.world.level.block.HalfTransparentBlock {
        public GlacialGlass() {
            super(glassProps(7));
        }
    }

    /** The same glass drawn out into cathedral tracery. */
    public static class GlacialGlassPane extends IronBarsBlock {
        public GlacialGlassPane() {
            super(glassProps(7));
        }
    }

    /**
     * A lantern of cold fire. Stands on the floor or hangs from a vault,
     * exactly like its iron cousin - but it burns white.
     */
    public static class FrostLantern extends Block implements SimpleWaterloggedBlock {

        public static final BooleanProperty HANGING = BlockStateProperties.HANGING;
        public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

        private static final VoxelShape STANDING = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 9.0D, 11.0D);
        private static final VoxelShape HANGING_SHAPE = Block.box(5.0D, 1.0D, 5.0D, 11.0D, 10.0D, 11.0D);

        public FrostLantern() {
            super(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(1.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 15)
                    .noOcclusion());
            registerDefaultState(stateDefinition.any()
                    .setValue(HANGING, false)
                    .setValue(WATERLOGGED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(HANGING, WATERLOGGED);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return state.getValue(HANGING) ? HANGING_SHAPE : STANDING;
        }

        @Nullable
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            boolean water = ctx.getLevel().getFluidState(ctx.getClickedPos()).getType() == Fluids.WATER;
            for (Direction dir : ctx.getNearestLookingDirections()) {
                if (dir.getAxis() == Direction.Axis.Y) {
                    BlockState state = defaultBlockState()
                            .setValue(HANGING, dir == Direction.UP)
                            .setValue(WATERLOGGED, water);
                    if (state.canSurvive(ctx.getLevel(), ctx.getClickedPos())) {
                        return state;
                    }
                }
            }
            return null;
        }

        @Override
        public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
            Direction attach = state.getValue(HANGING) ? Direction.UP : Direction.DOWN;
            return Block.canSupportCenter(level, pos.relative(attach), attach.getOpposite());
        }

        @Override
        public FluidState getFluidState(BlockState state) {
            return state.getValue(WATERLOGGED)
                    ? Fluids.WATER.getSource(false)
                    : super.getFluidState(state);
        }
    }

    /**
     * Icicles that grew in the halls and never melted. Point down from a
     * ceiling or up from a floor, whichever they were placed against.
     */
    public static class IcicleCluster extends Block implements SimpleWaterloggedBlock {

        public static final DirectionProperty FACING = BlockStateProperties.VERTICAL_DIRECTION;
        public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

        private static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);

        public IcicleCluster() {
            super(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(0.6F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 4)
                    .noOcclusion());
            registerDefaultState(stateDefinition.any()
                    .setValue(FACING, Direction.DOWN)
                    .setValue(WATERLOGGED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, WATERLOGGED);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Nullable
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            Direction clicked = ctx.getClickedFace();
            Direction facing = clicked.getAxis() == Direction.Axis.Y ? clicked.getOpposite() : Direction.DOWN;
            return defaultBlockState()
                    .setValue(FACING, facing)
                    .setValue(WATERLOGGED,
                            ctx.getLevel().getFluidState(ctx.getClickedPos()).getType() == Fluids.WATER);
        }

        @Override
        public FluidState getFluidState(BlockState state) {
            return state.getValue(WATERLOGGED)
                    ? Fluids.WATER.getSource(false)
                    : super.getFluidState(state);
        }
    }


    /** A candelabra two blocks high: the body you bump is its stand. */
    public static class FrostCandelabra extends Block {
        private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
                Block.box(5.0D, 0.0D, 5.0D, 11.0D, 3.0D, 11.0D),
                Block.box(7.0D, 3.0D, 7.0D, 9.0D, 16.0D, 9.0D));

        public FrostCandelabra() {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F)
                    .sound(SoundType.LANTERN).lightLevel(state -> 14).noOcclusion());
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }
    }

    /** Three candles on a short stand. */
    /**
     * Three candles on a short stand. ITS ARMS RUN ALONG A WALL: AXIS is the way its arms go - set by the citadel's builder along the wall
     * it stands at (kit.candlestick), and across the way you look when you set one down yourself.
     */
    public static class FrostCandlestick extends Block {
        public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction.Axis> AXIS =
                BlockStateProperties.HORIZONTAL_AXIS;
        private static final VoxelShape SHAPE_X = Block.box(3.0D, 0.0D, 6.0D, 13.0D, 12.0D, 10.0D);
        private static final VoxelShape SHAPE_Z = Block.box(6.0D, 0.0D, 3.0D, 10.0D, 12.0D, 13.0D);

        public FrostCandlestick() {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8F)
                    .sound(SoundType.LANTERN).lightLevel(state -> 11).noOcclusion());
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(AXIS);
        }

        @Nullable
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            // its arms across the way you look: set against the wall before you, they run along it
            return defaultBlockState().setValue(AXIS,
                    ctx.getHorizontalDirection().getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z);
        }

        @Override
        public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
            if (rotation == net.minecraft.world.level.block.Rotation.CLOCKWISE_90
                    || rotation == net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90) {
                return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            }
            return state;
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return state.getValue(AXIS) == Direction.Axis.Z ? SHAPE_Z : SHAPE_X;
        }
    }

    /**
     * A chandelier of frozen light. Hangs from a vault or a chain and reads as
     * one piece with the chain above it - no floating lamps.
     */
    public static class FrostChandelier extends Block {
        // the hub and the stem: the wheel round them (three blocks across, two
        // high) is drawing only, so it never catches a head or a sword
        private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
                Block.box(5.5D, 0.0D, 5.5D, 10.5D, 8.0D, 10.5D),
                Block.box(7.0D, 8.0D, 7.0D, 9.0D, 16.0D, 9.0D));

        public FrostChandelier() {
            super(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(1.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 15)
                    .noOcclusion());
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }
    }



    private FFDecoBlocks() {
    }
}
