package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.entity.boss.StormEyeArena;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A FLOE OF THE EYE OF THE STORM (Oko Burzy, Velkhar's last phase - StormEyeArena builds and melts them). Since the
 * floes of the rings learned to move (07.10.2026) they are bodies (StormEyeFloeEntity, drawn from this block's own
 * models); the block is the central floe's, which never moves and never melts.
 *
 * <p>Five looks, and the order is the whole read of the arena:
 * <ul>
 *   <li>{@code stage=0} FRESH - clear blue ice;</li>
 *   <li>{@code stage=1} MARKED - this one goes next: hairline cracks, a dull bloom in it;</li>
 *   <li>{@code stage=2} CRACKED - split right through, the cracks lit from inside, groaning;</li>
 *   <li>{@code stage=3} SLUSH - grey and wet, dripping into the storm: still holds you, not for long;</li>
 *   <li>{@code stage=4} FORMING - new ice freezing out of the cloud: a frost outline, nothing to stand on yet.</li>
 * </ul>
 * After SLUSH it is gone (air). The arena drives every change; the block itself only keeps its look and, as a safety
 * net, takes itself away once no arena claims it (a world saved mid-fight and loaded without the king).
 *
 * <p>Nothing breaks it by hand and it drops nothing.
 */
public class StormEyeFloeBlock extends Block {

    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 4);
    public static final int FRESH = 0, MARKED = 1, CRACKED = 2, SLUSH = 3, FORMING = 4;

    public StormEyeFloeBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(-1.0F, 3600000.0F)
                .sound(SoundType.GLASS).noOcclusion().noLootTable().randomTicks()
                .lightLevel(s -> switch (s.getValue(STAGE)) {
                    case CRACKED -> 9;              // the cracks shine: the one you must get off is the brightest
                    case SLUSH -> 5;
                    case FORMING -> 6;
                    case MARKED -> 4;
                    default -> 3;
                })
                .isValidSpawn((s, l, p, e) -> false).isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false));
        registerDefaultState(stateDefinition.any().setValue(STAGE, FRESH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(STAGE) == FORMING ? Shapes.empty() : Shapes.block();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(STAGE) == FORMING ? Shapes.empty() : Shapes.block();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Ice against ice draws no face between them (as vanilla ice) - but a forming ghost hides nothing. */
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        if (adjacent.is(this)) {
            boolean ghost = state.getValue(STAGE) == FORMING;
            boolean otherGhost = adjacent.getValue(STAGE) == FORMING;
            if (ghost == otherGhost) {
                return true;
            }
        }
        return super.skipRendering(state, adjacent, side);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    /** Drips under slush, a spark in a crack, frost rising off ice being made - the accompaniment, not the read. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int stage = state.getValue(STAGE);
        if (stage == SLUSH && random.nextInt(3) == 0 && level.getBlockState(pos.below()).isAir()) {
            level.addParticle(ParticleTypes.DRIPPING_WATER, pos.getX() + random.nextDouble(), pos.getY() - 0.05D,
                    pos.getZ() + random.nextDouble(), 0.0D, 0.0D, 0.0D);
        } else if (stage == CRACKED && random.nextInt(9) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + 1.02D,
                    pos.getZ() + random.nextDouble(), 0.0D, 0.02D, 0.0D);
        } else if (stage == FORMING && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), 0.0D, 0.03D, 0.0D);
        }
    }

    /**
     * No arena claims this block (a world saved mid-fight and loaded without its king, a crash): the ice lets go - but
     * only after asking twice, thirty seconds apart, because after a load the floes' chunk can tick before the king in
     * it has had his first tick and claimed them again.
     */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getServer().getTickCount() > StormEyeArena.ORPHAN_GRACE && !StormEyeArena.claims(level, pos)
                && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 600);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!StormEyeArena.claims(level, pos)) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
