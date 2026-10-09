package com.jastkub.frozenfortress.block;

import com.jastkub.frozenfortress.block.entity.StormcrownBeaconBlockEntity;
import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * The Stormcrown - a shard of the swallowed winter, set on the highest roof
 * of the keep. While it burns for the court, every servant in the citadel
 * fights harder and its Chill lies on every player; drive the Crownbreaker
 * into it and it burns for the players instead (StormcrownBeaconBlockEntity).
 */
public class StormcrownBeaconBlock extends BaseEntityBlock {

    public StormcrownBeaconBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE)
                // No tool touches it while the Sovereign's weight lies on the
                // fortress - the expedition's wedge is the only way in.
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.AMETHYST)
                .noLootTable()
                .lightLevel(state -> 15)
                .noOcclusion());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;              // the crown is a GeckoLib model (StormcrownBeaconRenderer)
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StormcrownBeaconBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return createTickerHelper(type, FFBlockEntities.STORMCROWN_BEACON.get(),
                level.isClientSide
                        ? StormcrownBeaconBlockEntity::clientTick
                        : StormcrownBeaconBlockEntity::serverTick);
    }

    /** When the crown falls, the storm leaves the court at once. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            AABB range = new AABB(pos).inflate(StormcrownBeaconBlockEntity.RADIUS);
            for (FrostServantEntity servant : serverLevel.getEntitiesOfClass(FrostServantEntity.class, range)) {
                servant.removeEffect(MobEffects.DAMAGE_BOOST);
                servant.removeEffect(MobEffects.MOVEMENT_SPEED);
                servant.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            }
            serverLevel.playSound(null, pos, FFSounds.ICE_SHATTER.get(), SoundSource.BLOCKS, 2.5F, 0.6F);
            serverLevel.playSound(null, pos, FFSounds.VELKHAR_WHISPER.get(), SoundSource.BLOCKS, 1.8F, 0.8F);
            serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                    pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D, 80, 1.0D, 1.5D, 1.0D, 0.15D);
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 60, 0.6D, 0.6D, 0.6D, 0.25D);
            // THE STORM SEAL BREAKS (if the crown is ever removed rather than taken)
            StormcrownBeaconBlockEntity.breakStormSeal(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        for (int i = 0; i < 2; i++) {
            level.addParticle(FFParticles.FROST_SWIRL.get(),
                    pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 1.4D,
                    pos.getY() + 1.0D + random.nextDouble(),
                    pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 1.4D,
                    0.0D, 0.04D, 0.0D);
        }
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
