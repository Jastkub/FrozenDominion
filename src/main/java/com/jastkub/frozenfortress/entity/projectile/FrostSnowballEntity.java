package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.entity.FFAllies;
import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * THE FROST RIDER'S SNOWBALL (FrostRiderEntity): snow packed hard as ice and thrown from the saddle. A hit stings (3),
 * knocks back a little, slows for two seconds and leaves some of its cold in you (freezing ticks). A thing with a body,
 * drawn as one (FrostSnowballRenderer) - it bursts into snow where it lands.
 */
public class FrostSnowballEntity extends ThrowableProjectile {

    /** Blocks a tick it leaves the hand at. */
    public static final double SPEED = 0.95D;
    private static final float DAMAGE = 3.0F;
    private static final int SLOW_TICKS = 40, COLD_TICKS = 70;

    public FrostSnowballEntity(EntityType<? extends FrostSnowballEntity> type, Level level) {
        super(type, level);
    }

    public FrostSnowballEntity(Level level, LivingEntity thrower, Vec3 from) {
        this(FFEntities.FROST_SNOWBALL.get(), level);
        setOwner(thrower);
        setPos(from.x, from.y, from.z);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    /** Through the king's own: it is not thrown at them. */
    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && !FFAllies.ofTheKing(e);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(hit.getEntity() instanceof LivingEntity v) || level().isClientSide) {
            return;
        }
        Entity owner = getOwner();
        if (v.hurt(damageSources().thrown(this, owner == null ? this : owner), DAMAGE)) {
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 0), owner);
            if (v.canFreeze()) {
                v.setTicksFrozen(Math.min(v.getTicksRequiredToFreeze() + 20, v.getTicksFrozen() + COLD_TICKS));
            }
            Vec3 push = getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
            if (push.lengthSqr() > 1.0E-4D) {
                v.knockback(0.35D, -push.x, -push.z);
            }
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level() instanceof ServerLevel sl) {
            Vec3 at = hit.getLocation();
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()),
                    at.x, at.y, at.z, 14, 0.15D, 0.15D, 0.15D, 0.12D);
            sl.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 6, 0.2D, 0.2D, 0.2D, 0.04D);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.SNOW_BREAK, SoundSource.HOSTILE, 1.0F,
                    0.8F + random.nextFloat() * 0.3F);
            discard();
        }
    }
}
