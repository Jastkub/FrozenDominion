package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.RawAnimation;

import javax.annotation.Nullable;

/**
 * WOVEN INTO RIME - what the Rimeweaver's circle does to whoever does not get out of it before it closes: rings of rime threads woven up round
 * them from the feet, held two and a half seconds. It is the ice prison's rules - pinned where they stand, and
 * three blows (theirs or a friend's) break them out sooner - in the weaver's own shape.
 */
public class RimeBindEntity extends IcePrisonEntity {

    private static final RawAnimation WEAVE = RawAnimation.begin().thenPlayAndHold("animation.rime_bind.weave");

    public RimeBindEntity(EntityType<? extends RimeBindEntity> type, Level level) {
        super(type, level);
    }

    public RimeBindEntity(Level level, @Nullable LivingEntity owner, LivingEntity held) {
        super(FFEntities.RIME_BIND.get(), level, owner, held);
    }

    @Override
    protected int maxTicks() {
        return 50;
    }

    @Override
    protected RawAnimation idleAnimation() {
        return WEAVE;
    }
}
