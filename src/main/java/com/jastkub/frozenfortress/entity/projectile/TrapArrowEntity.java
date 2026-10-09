package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * AN ARROW SLIT'S SHOT (ArrowSlitBlockEntity) - its own, apart from the Stillbow's (IceArrowEntity):
 * a bolt of ice straight across the passage, a little hurt and nothing more - no frostbite, no slowing - shattering on
 * the far wall rather than staying stuck in it.
 */
public class TrapArrowEntity extends IceArrowEntity {

    public TrapArrowEntity(EntityType<? extends TrapArrowEntity> type, Level level) {
        super(type, level);
        setBaseDamage(4.0D);
        pickup = Pickup.DISALLOWED;
    }

    /** Shot from `from` along `dir` at `speed` blocks a tick, without dropping. */
    public TrapArrowEntity(Level level, Vec3 from, Vec3 dir, float speed) {
        this(FFEntities.TRAP_ARROW.get(), level);
        setPos(from.x, from.y, from.z);
        setNoGravity(true);
        shoot(dir.x, dir.y, dir.z, speed, 0.0F);
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        // (nothing laid on whoever it strikes: the trap's hurt is the blow alone)
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!level().isClientSide) {
            playSound(SoundEvents.GLASS_HIT, 0.8F, 1.5F);
            discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > 60) {
            discard();
        }
    }
}
