package com.jastkub.frozenfortress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Frozen Throne.
 *
 * One block that renders as a whole seat - the model reaches well outside its
 * own cube, which is why nothing else may occupy the space around it in the
 * fortress. Velkhar sits here while dormant.
 *
 * It is deliberately impossible to take: indestructible in survival, no loot
 * table, immovable by pistons and absent from every creative tab. It exists
 * only where the fortress generator puts it. A throne the player could pocket
 * and re-place in their base would undercut the one room the whole structure
 * is built around.
 */
public class FrozenThroneBlock extends HorizontalDirectionalBlock
        implements net.minecraft.world.level.block.EntityBlock {

    /**
     * What you can actually stand on and walk into.
     *
     * <p>Sized to the new model rather than to the old one: the seat is now
     * five blocks across and eight tall, and a collision box still shaped
     * like the old chair would let a player walk through the dais they can
     * plainly see. Kept to the SOLID masses only - the spires and the
     * icicles are not collided with, because being stopped by an ornament
     * two blocks over your head reads as a bug however correct it is.
     */
    private static final VoxelShape SHAPE = Shapes.or(
            Shapes.box(-1.5D, 0.0D, -1.75D, 2.5D, 0.25D, 1.25D),        // lower dais
            Shapes.box(-1.125D, 0.25D, -1.4375D, 2.125D, 0.4375D, 1.1875D),
            Shapes.box(-0.75D, 0.4375D, -1.125D, 1.75D, 0.6875D, 1.0D),  // top step
            Shapes.box(-0.375D, 0.6875D, -1.0D, 1.375D, 1.375D, 0.9375D), // seat block
            Shapes.box(-0.4375D, 1.3125D, 0.5D, 1.4375D, 5.625D, 1.0D),   // the back
            Shapes.box(1.125D, 0.6875D, -1.0D, 1.875D, 2.25D, 0.9375D),   // right arm
            Shapes.box(-0.875D, 0.6875D, -1.0D, -0.125D, 2.25D, 0.9375D));// left arm

    /**
     * Nothing is drawn by the block pipeline. The model is a GeckoLib one on
     * the block entity - eight blocks tall and half of it angled, neither of
     * which a blockstate JSON can express - so the vanilla path would only
     * ever draw a second, worse throne inside the real one.
     */
    @Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.INVISIBLE;
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            BlockPos pos, BlockState state) {
        return new com.jastkub.frozenfortress.block.entity.FrozenThroneBlockEntity(pos, state);
    }

    public FrozenThroneBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE)
                // Bedrock's numbers: unbreakable by hand, tool or explosion.
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.DEEPSLATE_TILES)
                .noLootTable()
                .noOcclusion()
                .lightLevel(s -> 6)
                .pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos,
                                  net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }
}
