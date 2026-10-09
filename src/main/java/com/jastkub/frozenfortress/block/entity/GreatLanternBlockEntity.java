package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.registry.FFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The great lantern's block entity: only what its renderer needs to remember between frames - the
 * angle its lens was last turned to (it stays there when its keeper is dead) and whether it shows
 * one beam or two. The beam itself is the Lamplighter's (LamplighterEntity: angle, mode).
 */
public class GreatLanternBlockEntity extends BlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Client: the lens's angle (degrees, yaw convention: 0 looks +z), and its rear shutter's state. */
    public float lensAngle;
    public boolean twoBeams;

    public GreatLanternBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.GREAT_LANTERN.get(), pos, state);
    }

    /** Its beam crosses the whole dome. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(26.0D, 12.0D, 26.0D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
