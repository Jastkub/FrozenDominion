package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.registry.FFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The throne, as a block entity.
 *
 * <p>It carries no state and never ticks. It exists purely so the throne can
 * be drawn by a GeoBlockRenderer instead of by the block model pipeline -
 * which is the whole point, because a block model is clamped to its own cube
 * and cannot carry a rotated face. The seat is five blocks across, eight
 * tall, and half its cubes are angled; none of that is expressible in a
 * blockstate JSON.
 */
public class FrozenThroneBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final RawAnimation IDLE =
            RawAnimation.begin().thenLoop("animation.frozen_throne.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public FrozenThroneBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.FROZEN_THRONE.get(), pos, state);
    }

    /**
     * What the game culls this against.
     *
     * <p>A block entity is culled on its own single cube by default, so a
     * model reaching two and a half blocks either side and eight up would
     * vanish the moment that one cube left the frustum - which, on a throne
     * you walk up to, is most of the time you are looking at it.
     */
    /** (1.21) the renderer asks for this: its getRenderBoundingBox(be). */
    public net.minecraft.world.phys.AABB renderBox() {
        return new net.minecraft.world.phys.AABB(getBlockPos())
                .inflate(4.0D, 0.0D, 4.0D)
                .expandTowards(0.0D, 9.0D, 0.0D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, this::idle));
    }

    private PlayState idle(AnimationState<FrozenThroneBlockEntity> state) {
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
