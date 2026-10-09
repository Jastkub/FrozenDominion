package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.RawAnimation;

import javax.annotation.Nullable;

/**
 * THE RIMEWEAVER'S THORNS: a knot of rime drawn on the floor where each will come (the warning - a thing you see,
 * not a haze), then three crystal thorns burst up out of it. Struck and timed as the ice spike they are made from.
 */
public class RimeThornEntity extends IceSpikeEntity {

    private static final RawAnimation WARN = RawAnimation.begin().thenLoop("animation.rime_thorn.warn");
    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlay("animation.rime_thorn.emerge");

    public RimeThornEntity(EntityType<? extends RimeThornEntity> type, Level level) {
        super(type, level);
    }

    public RimeThornEntity(Level level, @Nullable LivingEntity owner, double x, double y, double z, float damage,
                           int delayTicks) {
        super(FFEntities.RIME_THORN.get(), level, owner, x, y, z, damage, delayTicks);
    }

    @Override
    protected boolean warnParticles() {
        return false;
    }

    @Override
    protected RawAnimation emergeAnim() {
        return EMERGE;
    }

    @Override
    @Nullable
    protected RawAnimation warnAnim() {
        return WARN;
    }
}
