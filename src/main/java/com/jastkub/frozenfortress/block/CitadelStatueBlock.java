package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.block.entity.CitadelStatueBlockEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import javax.annotation.Nullable;

/**
 * A statue of the citadel: one block at its feet that draws a whole model -
 * the king in one of his phases, the Monstrosity, any of the garrison - in a
 * hide of ice. What it is, how it stands, how big and which way it looks all
 * live on the block entity, so one block serves every statue in the place.
 *
 * <p>The body you bump into is not this block. The generator fills the
 * statue's volume with {@link StatueCoreBlock}s, invisible and solid, so the
 * collision follows the figure rather than a box round it.
 */
public class CitadelStatueBlock extends Block implements EntityBlock {

    public CitadelStatueBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE)
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.GLASS)
                .noOcclusion()
                .noLootTable()
                .pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(TemplateTurn.FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TemplateTurn.FACING);
    }

    /** Turned with the structure it is in: its block entity reads how far (TemplateTurn). */
    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return TemplateTurn.rotate(state, rotation);
    }

    /**
     * The dead lying in the citadel are only bone: nothing to bump into, a
     * low outline to look at. Every other statue is solid at its feet.
     */
    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext ctx) {
        if (level.getBlockEntity(pos) instanceof CitadelStatueBlockEntity s && s.model().startsWith("remains")) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        return super.getCollisionShape(state, level, pos, ctx);
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext ctx) {
        if (level.getBlockEntity(pos) instanceof CitadelStatueBlockEntity s && s.model().startsWith("remains")) {
            return Block.box(2.0D, 0.0D, 2.0D, 14.0D, 5.0D, 14.0D);
        }
        return super.getShape(state, level, pos, ctx);
    }

    /** Nothing from the block pipeline: the model is the block entity's. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CitadelStatueBlockEntity(pos, state);
    }

    /** The shudder before it breaks is sent as a block event. */
    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != FFBlockEntities.CITADEL_STATUE.get()) {
            return null;
        }
        return level.isClientSide
                ? (l, p, s, be) -> CitadelStatueBlockEntity.clientTick(l, p, (CitadelStatueBlockEntity) be)
                : (l, p, s, be) -> CitadelStatueBlockEntity.serverTick(l, p, (CitadelStatueBlockEntity) be);
    }

    /**
     * A pickaxe blow on any part of a figure counts against its statue. The
     * dead are only bone: any blow at all breaks them up.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                       net.minecraft.world.entity.player.Player player) {
        super.attack(state, level, pos, player);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && !player.isCreative()) {
            com.jastkub.frozenfortress.block.entity.CitadelStatueBlockEntity statue =
                    com.jastkub.frozenfortress.block.entity.CitadelStatueBlockEntity.owner(level, pos);
            if (statue != null && (statue.isRemains()
                    || player.getMainHandItem().canPerformAction(net.neoforged.neoforge.common.ItemAbilities.PICKAXE_DIG))) {
                statue.strike(serverLevel, pos);
            }
        }
    }
}
