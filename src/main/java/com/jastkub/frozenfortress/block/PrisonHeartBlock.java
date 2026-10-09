package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.block.entity.PrisonHeartBlockEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

/**
 * Set under the floor of the Ice Prison. It watches for the first living
 * player to come into the prison, breaks the statue standing over it, lets
 * the Ice Monstrosity out, and - when the Monstrosity dies - raises the
 * reliquary with the Throne Key where the structure told it to.
 */
public class PrisonHeartBlock extends BaseEntityBlock {

    /** (1.21) an entity block names its codec. */
    public static final com.mojang.serialization.MapCodec<PrisonHeartBlock> CODEC = com.mojang.serialization.MapCodec.unit(PrisonHeartBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public PrisonHeartBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.DEEPSLATE)
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.DEEPSLATE_TILES)
                .noLootTable());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PrisonHeartBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, FFBlockEntities.PRISON_HEART.get(), PrisonHeartBlockEntity::serverTick);
    }
}
