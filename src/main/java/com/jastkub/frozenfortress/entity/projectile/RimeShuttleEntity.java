package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * THE RIMEWEAVER'S BOLT, a thing of its own: a weaver's shuttle of ice, a bobbin of cold light through it, rolling as it flies and paying out a
 * thread of frost behind (RimeShuttleRenderer). It flies and follows like the frost bolt it is made from.
 */
public class RimeShuttleEntity extends FrostBoltEntity {

    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.rime_shuttle.spin");

    public RimeShuttleEntity(EntityType<? extends RimeShuttleEntity> type, Level level) {
        super(type, level);
    }

    public RimeShuttleEntity(Level level, LivingEntity shooter, double dx, double dy, double dz) {
        super(FFEntities.RIME_SHUTTLE.get(), level, shooter, dx, dy, dz);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spin", 0, state -> state.setAndContinue(SPIN)));
    }
}
