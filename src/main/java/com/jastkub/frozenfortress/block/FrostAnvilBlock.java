package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.registry.FFItems;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * THE FROST ANVIL (Kowadło Szronu): beside the imprisoned smith, the anvil Velkhar's armour was
 * forged on. It is iron, as hard as any anvil - and while the Stormcrown burns its Mining Fatigue III keeps any pick
 * off it, so the player comes back for it once the beacon is taken. On it:
 * It is THE FORGE: its window (FrostForgeMenu)
 * forges what the frozen court made - the everfrost ingot from four crystals and four of gold, the everfrost gear,
 * and (in time) the Kingsrime and the legendary arms, whose cores go in its heart (FrostForgingRecipe).
 * (The Seal Core is made on any crafting table now)
 */
public class FrostAnvilBlock extends HorizontalDirectionalBlock {

    /** (1.21) a block with a facing names its codec. */
    public static final com.mojang.serialization.MapCodec<FrostAnvilBlock> CODEC = com.mojang.serialization.MapCodec.unit(FrostAnvilBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }


    private static final VoxelShape BASE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 4.0D, 14.0D);
    private static final VoxelShape Z_AXIS = Shapes.or(BASE, Block.box(4.0D, 4.0D, 3.0D, 12.0D, 5.0D, 13.0D),
            Block.box(6.0D, 5.0D, 4.0D, 10.0D, 10.0D, 12.0D), Block.box(3.0D, 10.0D, 0.0D, 13.0D, 16.0D, 16.0D));
    private static final VoxelShape X_AXIS = Shapes.or(BASE, Block.box(3.0D, 4.0D, 4.0D, 13.0D, 5.0D, 12.0D),
            Block.box(4.0D, 5.0D, 6.0D, 12.0D, 10.0D, 10.0D), Block.box(0.0D, 10.0D, 3.0D, 16.0D, 16.0D, 13.0D));

    public FrostAnvilBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops()
                .strength(5.0F, 1200.0F).sound(SoundType.ANVIL).noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getClockWise());   // as an anvil
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_AXIS : Z_AXIS;
    }

    /** Its window (FrostForgeMenu): the grid, the core, what they make. */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack heldStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, who) -> new com.jastkub.frozenfortress.menu.FrostForgeMenu(id, inv,
                            net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos)),
                    Component.translatable("container.frozen_dominion.frost_forge")), pos);
        }
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }
}
