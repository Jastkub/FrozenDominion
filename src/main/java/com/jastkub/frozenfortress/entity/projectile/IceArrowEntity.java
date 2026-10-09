package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An arrow of black ice loosed by the Stillbow. Slows what it does not kill.
 */
public class IceArrowEntity extends AbstractArrow {

    public IceArrowEntity(EntityType<? extends IceArrowEntity> type, Level level) {
        super(type, level);
        setBaseDamage(5.0D);
    }

    public IceArrowEntity(Level level, LivingEntity shooter) {
        super(FFEntities.ICE_ARROW.get(), shooter, level);
        setBaseDamage(5.0D);
    }

    /** The shot at the sky that calls the rain: it goes up out of sight and is gone - it strikes nothing, and
     *  under a vault it does not stay stuck in the stone. */
    private boolean signal;

    public IceArrowEntity signal() {
        this.signal = true;
        setNoGravity(true);
        return this;
    }

    /** One of a Stillbow's volley: the volley strikes as one (StillbowEntity#tickRain), the arrow only falls. */
    private boolean volley;

    public IceArrowEntity volley() {
        this.volley = true;
        return this;
    }

    @Override
    protected boolean canHitEntity(net.minecraft.world.entity.Entity target) {
        return !signal && !volley && super.canHitEntity(target);
    }

    @Override
    protected void onHitBlock(net.minecraft.world.phys.BlockHitResult hit) {
        if (signal) {
            discard();
            return;
        }
        super.onHitBlock(hit);
    }

    /** The Stillbow's heavy shot: what it strikes is nailed where it stands for a second and a half. */
    private boolean pinning;

    public IceArrowEntity pinning() {
        this.pinning = true;
        return this;
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        target.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 60, 0), getOwner() instanceof LivingEntity le ? le : null);
        if (pinning) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 6));
            target.addEffect(new MobEffectInstance(MobEffects.JUMP, 30, -10));        // (below zero: no jump at all)
            if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.sendParticles(com.jastkub.frozenfortress.registry.FFParticles.ICE_SHARD.get(), target.getX(),
                        target.getY() + 0.2D, target.getZ(), 16, 0.4D, 0.1D, 0.4D, 0.06D);
            }
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && !inGround) {
            level().addParticle(FFParticles.FROST_SWIRL.get(),
                    getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
        }
        if (tickCount > 300 || (signal && tickCount > 14)) {
            discard();
        }
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }
}
