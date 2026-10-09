package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.block.entity.GreatLanternBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * WIELKA LATARNIA - the watchtower's great lantern. A cage of iron hung on its chain from the vault, its lens turning to
 * the Lamplighter's beam (GreatLanternRenderer, which also draws the beam out of it).
 *
 * <p>Nothing breaks it, and nothing collides with it: the light's straight lines start in it, and
 * a solid block there would put the whole dome in its shadow.
 */
public class GreatLanternBlock extends Block implements EntityBlock {

    private static final VoxelShape OUTLINE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);

    public GreatLanternBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.LANTERN)
                .lightLevel(state -> 15)
                .noOcclusion()
                .noCollission()
                .noLootTable()
                .pushReaction(PushReaction.BLOCK));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return OUTLINE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GreatLanternBlockEntity(pos, state);
    }
}
