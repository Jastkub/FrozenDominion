package com.jastkub.frozenfortress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The inside of a statue: solid, unseen, unbreakable. The generator fills the
 * volume of each figure with these so that what you walk into is the shape you
 * see. Light passes through - a statue standing in a lit hall must not throw a
 * block-shaped shadow of its own collision onto the floor.
 */
public class StatueCoreBlock extends Block {

    public StatueCoreBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE)
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.GLASS)
                .noOcclusion()
                .noLootTable()
                .isValidSpawn((s, l, p, e) -> false)
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false)
                // a chest under a statue's hand must still open (a chest is shut by a conductor over it)
                .isRedstoneConductor((s, l, p) -> false)
                .pushReaction(PushReaction.BLOCK));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    /** A pickaxe blow on any part of a figure counts against its statue. */
    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                       net.minecraft.world.entity.player.Player player) {
        super.attack(state, level, pos, player);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && !player.isCreative()
                && player.getMainHandItem().canPerformAction(net.neoforged.neoforge.common.ItemAbilities.PICKAXE_DIG)) {
            com.jastkub.frozenfortress.block.entity.CitadelStatueBlockEntity statue =
                    com.jastkub.frozenfortress.block.entity.CitadelStatueBlockEntity.owner(level, pos);
            if (statue != null) {
                statue.strike(serverLevel, pos);
            }
        }
    }
}
