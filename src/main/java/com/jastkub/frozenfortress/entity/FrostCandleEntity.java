package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A candle of ice the Priestess raises out of the chapel floor. While it
 * burns she is warded (each one takes a quarter off every blow she takes) and
 * the cold comes off it in a ring every two seconds. Two or three blows put
 * it out. Her fight's lesson: put the candles out first.
 *
 * <p>Living (six points of health, no AI) so an arrow may put it out safely.
 */
public class FrostCandleEntity extends Mob implements GeoEntity {

    private static final EntityDataAccessor<Boolean> SNUFFED =
            SynchedEntityData.defineId(FrostCandleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation RISE = RawAnimation.begin().thenPlay("animation.frost_candle.rise")
            .thenLoop("animation.frost_candle.burn");
    private static final RawAnimation SNUFF = RawAnimation.begin().thenPlayAndHold("animation.frost_candle.snuff");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;

    public FrostCandleEntity(EntityType<? extends FrostCandleEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public FrostCandleEntity(Level level, RimePriestessEntity owner, Vec3 at) {
        this(FFEntities.FROST_CANDLE.get(), level);
        this.ownerId = owner.getUUID();
        moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SNUFFED, false);
    }

    @Override
    protected void registerGoals() {
    }

    public boolean burning() {
        return isAlive() && !entityData.get(SNUFFED);
    }

    @Nullable
    private RimePriestessEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof RimePriestessEntity p
                && p.isAlive()) {
            return p;
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (burning() && random.nextInt(3) == 0) {
                level().addParticle(FFParticles.SOUL_FROST.get(), getX(), getY() + 1.9D, getZ(), 0.0D, 0.03D, 0.0D);
            }
            return;
        }
        if (owner() == null && burning()) {
            snuff();                                             // she is gone: so is the rite
            return;
        }
        if (burning() && tickCount > 16 && tickCount % 40 == 0 && level() instanceof ServerLevel s) {
            // the cold off it, in a ring
            s.sendParticles(FFParticles.FROST_SWIRL.get(), getX(), getY() + 0.3D, getZ(), 30, 2.0D, 0.1D, 2.0D, 0.02D);
            for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.0D, 1.0D, 3.0D),
                    e -> !(e instanceof FrostServantEntity) && !(e instanceof FrostCandleEntity) && e.isAlive())) {
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
                v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 60, 0));
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof FrostServantEntity || entityData.get(SNUFFED)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        snuff();
        super.die(source);
    }

    private void snuff() {
        if (entityData.get(SNUFFED)) {
            return;
        }
        entityData.set(SNUFFED, true);
        level().playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.0F, 0.6F);
        level().playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.8F, 1.2F);
        if (level() instanceof ServerLevel s) {
            s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 1.2D, getZ(), 20, 0.3D, 0.8D, 0.3D, 0.08D);
        }
        RimePriestessEntity p = owner();
        if (p != null) {
            p.candleOut(this);
        }
        if (isAlive()) {
            setHealth(0.0F);
        }
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= 14 && !level().isClientSide() && !isRemoved()) {
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "candle", 0,
                s -> s.setAndContinue(entityData.get(SNUFFED) || !isAlive() ? SNUFF : RISE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
}
