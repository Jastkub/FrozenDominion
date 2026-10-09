package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.block.entity.FrostCannonBlockEntity;
import com.jastkub.frozenfortress.block.entity.FrostHeartBlockEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** The trap rooms' blocks (built 06.10.2026): the frost's heart, the frost maw, the crumbling ice. */
public final class TrapBlocks {

    private TrapBlocks() {
    }

    /** SERCE MROZU on its pedestal (FrostHeartBlockEntity): struck, or a hand laid on it. Nothing breaks it else. */
    public static class FrostHeart extends BaseEntityBlock {
        private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 4, 14), Block.box(3, 7, 3, 13, 24, 13));
        private static final VoxelShape FOOT = Block.box(2, 0, 2, 14, 4, 14);

        public FrostHeart() {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(-1.0F, 3600000.0F)
                    .sound(SoundType.GLASS).noOcclusion().lightLevel(s -> 9).noLootTable());
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return RenderShape.ENTITYBLOCK_ANIMATED;
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return FOOT;
        }

        @Override
        public void attack(BlockState state, Level level, BlockPos pos, Player player) {
            if (level.getBlockEntity(pos) instanceof FrostHeartBlockEntity heart) {
                heart.strike(player);
            }
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                     BlockHitResult hit) {
            // with the Crownbreaker in that
            // hand the click is the wedge's (CrownbreakerItem.useOn) - before, this took it and only told you to use one
            if (player.getItemInHand(hand).getItem() instanceof com.jastkub.frozenfortress.item.CrownbreakerItem) {
                return InteractionResult.PASS;
            }
            if (level.getBlockEntity(pos) instanceof FrostHeartBlockEntity heart) {
                if (!level.isClientSide) {
                    heart.putOut(player);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new FrostHeartBlockEntity(pos, state);
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide ? null
                    : createTickerHelper(type, FFBlockEntities.FROST_HEART.get(), FrostHeartBlockEntity::serverTick);
        }
    }

    /** A FROST MAW set in a wall, its throat facing out (FrostCannonBlockEntity). */
    public static class FrostCannon extends BaseEntityBlock {
        public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
                HorizontalDirectionalBlock.FACING;

        public FrostCannon() {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(-1.0F, 3600000.0F)
                    .sound(SoundType.DEEPSLATE_BRICKS).noOcclusion().noLootTable());
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }

        @Override
        public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rot) {
            return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return RenderShape.ENTITYBLOCK_ANIMATED;
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new FrostCannonBlockEntity(pos, state);
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide ? null
                    : createTickerHelper(type, FFBlockEntities.FROST_CANNON.get(), FrostCannonBlockEntity::serverTick);
        }
    }

    /**
     * CRUMBLING ICE (the Rift's second run): a shelf of ice that holds you for a moment - it cracks the moment you
     * step on it (stage 1, a crack you see and hear) and is gone under you a heartbeat later (stage 2: nothing to
     * stand on, nothing drawn); five seconds on it has frozen back. Nothing breaks it by hand. (Should its tick ever be
     * lost, a random tick brings it back all the same)
     */
    public static class CrumblingIce extends Block {
        public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);

        public CrumblingIce() {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(-1.0F, 3600000.0F)
                    .sound(SoundType.GLASS).noOcclusion().noLootTable()
                    .isValidSpawn((s, l, p, e) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
            registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(STAGE);
        }

        @Override
        public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (!level.isClientSide && state.getValue(STAGE) == 0 && entity instanceof Player p && !p.isSpectator()) {
                level.setBlock(pos, state.setValue(STAGE, 1), 3);
                level.scheduleTick(pos, this, 14);
                level.playSound(null, pos, FFSounds.ICE_CRACK.get(), SoundSource.BLOCKS, 1.2F, 1.4F);
            }
            super.stepOn(level, pos, state, entity);
        }

        @Override
        public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            int stage = state.getValue(STAGE);
            if (stage == 1) {
                level.setBlock(pos, state.setValue(STAGE, 2), 3);
                level.scheduleTick(pos, this, 100);
                level.playSound(null, pos, FFSounds.ICE_SHATTER.get(), SoundSource.BLOCKS, 1.4F, 1.2F);
            } else if (stage == 2) {
                level.setBlock(pos, state.setValue(STAGE, 0), 3);
                level.playSound(null, pos, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
            }
        }

        @Override
        public boolean isRandomlyTicking(BlockState state) {
            return state.getValue(STAGE) == 2;
        }

        @Override
        public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
                tick(state, level, pos, random);                      // a stage 2 nothing is waiting on
            }
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return state.getValue(STAGE) == 2 ? Shapes.empty() : Shapes.block();
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return state.getValue(STAGE) == 2 ? Shapes.empty() : Shapes.block();
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return state.getValue(STAGE) == 2 ? RenderShape.INVISIBLE : RenderShape.MODEL;
        }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
            return true;
        }
    }
}
