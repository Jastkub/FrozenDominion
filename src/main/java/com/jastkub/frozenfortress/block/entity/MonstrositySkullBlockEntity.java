package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.registry.FFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** The skull, as a block entity: no state, no ticking - only so GeckoLib can draw the head out of its own model. */
public class MonstrositySkullBlockEntity extends BlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MonstrositySkullBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.MONSTROSITY_SKULL.get(), pos, state);
    }

    /** The horns reach most of a block past it either side, and it stands a block and a half. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(1.0D, 0.0D, 1.0D).expandTowards(0.0D, 1.0D, 0.0D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
