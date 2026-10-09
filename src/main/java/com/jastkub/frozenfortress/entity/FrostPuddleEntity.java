package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A POOL OF THE TROUGH'S WATER (the Monstrosity's geyser): where a gout of it
 * comes down, a ring marks the spot for a second; then the water lands and
 * freezes into a disc of black ice for eight seconds. Whoever stands in it is
 * slowed nearly to a stop, frostbitten, and stiffens with cold - it shapes the
 * arena, it does not chase anybody. FrostPuddleRenderer draws it as geometry.
 */
public class FrostPuddleEntity extends Entity {

    /** Ticks its ring shows before the water lands. */
    public static final int WARN = 22;
    /** And how long it lies frozen. */
    public static final int LIE = 160;
    public static final float RADIUS = 2.3F;

    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> SIZE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(FrostPuddleEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> WARN_T =
            net.minecraft.network.syncher.SynchedEntityData.defineId(FrostPuddleEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);

    @Nullable
    private UUID ownerId;

    public FrostPuddleEntity(EntityType<? extends FrostPuddleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public FrostPuddleEntity(Level level, @Nullable Entity owner, double x, double y, double z) {
        this(FFEntities.FROST_PUDDLE.get(), level);
        this.ownerId = owner == null ? null : owner.getUUID();
        setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(SIZE, RADIUS);
        builder.define(WARN_T, WARN);
    }

    /** A smaller pool (a bomb's shard leaves one). */
    public FrostPuddleEntity radius(float r) {
        entityData.set(SIZE, r);
        return this;
    }

    /** No warning ring: what made it was its own warning (a shard seen falling). */
    public FrostPuddleEntity quick() {
        entityData.set(WARN_T, 2);
        return this;
    }

    public float radius() {
        return entityData.get(SIZE);
    }

    public int warnTicks() {
        return entityData.get(WARN_T);
    }

    /** Has the water landed yet? */
    public boolean landed(float partialTick) {
        return tickCount + partialTick >= warnTicks();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        ServerLevel s = (ServerLevel) level();
        if (tickCount == warnTicks()) {
            s.playSound(null, blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.4F, 0.6F);
            s.playSound(null, blockPosition(), SoundEvents.GLASS_HIT, SoundSource.HOSTILE, 1.2F, 0.5F);
            s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 0.2D, getZ(), 24, 1.2D, 0.1D, 1.2D, 0.15D);
        }
        if (tickCount > warnTicks() + LIE) {
            discard();
            return;
        }
        if (tickCount >= warnTicks() && tickCount % 10 == 0) {
            Entity owner = ownerId != null ? s.getEntity(ownerId) : null;
            for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius(), 1.0D, radius()),
                    e -> e.isAlive() && !(e instanceof FrostServantEntity) && !(e instanceof HollowGolemEntity))) {
                if (v.distanceToSqr(getX(), v.getY(), getZ()) > radius() * radius() || Math.abs(v.getY() - getY()) > 1.2D) {
                    continue;
                }
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3));
                v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 60, 0));
                v.setTicksFrozen(Math.min(300, v.getTicksFrozen() + 40));
                if (tickCount % 20 == 0) {
                    v.hurt(damageSources().indirectMagic(this, owner), 1.5F);
                }
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 6400.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
