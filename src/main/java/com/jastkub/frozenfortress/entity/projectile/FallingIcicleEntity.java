package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * THE AVALANCHE (the Monstrosity's second phase): an icicle shaken loose from
 * the prison's dome. Its shadow comes first - a ring of frost on the floor where
 * it will land, tightening for a second and a half - then it falls, fast, and
 * bursts. Whoever is still in the ring takes it.
 */
public class FallingIcicleEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Float> FLOOR =
            SynchedEntityData.defineId(FallingIcicleEntity.class, EntityDataSerializers.FLOAT);
    /** Where it hung (it falls from there by a formula, the same on every client). */
    private static final EntityDataAccessor<Float> HUNG =
            SynchedEntityData.defineId(FallingIcicleEntity.class, EntityDataSerializers.FLOAT);
    /** Blocks long, when it is one of the dome's own (0: the old one shaken out of the dark). */
    private static final EntityDataAccessor<Integer> LENGTH =
            SynchedEntityData.defineId(FallingIcicleEntity.class, EntityDataSerializers.INT);
    /** Ticks its shadow shows before it drops (it shudders where it hangs). */
    public static final int WARN = 30;
    public static final float RADIUS = 1.9F;
    /** It falls, it does not glide: gaining speed to a cap. */
    private static final double GRAVITY = 0.09D, TERMINAL = 2.4D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;

    public FallingIcicleEntity(EntityType<? extends FallingIcicleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public FallingIcicleEntity(Level level, @Nullable Entity owner, double x, double floorY, double z, double height) {
        this(FFEntities.FALLING_ICICLE.get(), level);
        this.ownerId = owner == null ? null : owner.getUUID();
        hang(x, floorY, z, height);
    }

    /** Hung `height` over the floor at floorY, at (x, z): it falls from there. */
    protected void hang(double x, double floorY, double z, double height) {
        entityData.set(FLOOR, (float) floorY);
        entityData.set(HUNG, (float) (floorY + height));
        setPos(x, floorY + height, z);
    }

    /** One of the dome's icicles, n blocks long: drawn its length. */
    public FallingIcicleEntity length(int n) {
        entityData.set(LENGTH, n);
        return this;
    }

    /** The model (four and a third blocks of javelin) at the icicle's length. */
    public float modelScale() {
        int n = entityData.get(LENGTH);
        return n <= 0 ? 0.72F : n / 4.375F * 1.15F;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(FLOOR, 0.0F);
        builder.define(HUNG, 0.0F);
        builder.define(LENGTH, 0);
    }

    /** How far it has dropped k ticks after letting go. */
    private static double dropped(int k) {
        double d = 0.0D, v = 0.0D;
        for (int i = 0; i < k; i++) {
            v = Math.min(TERMINAL, v + GRAVITY);
            d += v;
        }
        return d;
    }

    /** The server's word on where it is counts only once it has landed; in the air it is the formula. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
    }

    /** The floor it will land on (its shadow is drawn there). */
    public float floorY() {
        return entityData.get(FLOOR);
    }

    /** 0..1: how near it is to dropping (the shadow tightens to its true size). */
    public float warn(float partialTick) {
        return Math.min(1.0F, (tickCount + partialTick) / WARN);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount <= WARN) {
            if (!level().isClientSide && tickCount % 8 == 0) {
                playSound(FFSounds.ICE_CRACK.get(), 1.4F, 1.4F + tickCount * 0.01F);
            }
            return;
        }
        if (tickCount == WARN + 1 && !level().isClientSide) {
            playSound(SoundEvents.GLASS_BREAK, 1.8F, 1.4F);
        }
        double y = entityData.get(HUNG) - dropped(tickCount - WARN);
        if (!level().isClientSide && onTheColossus(y)) {
            return;
        }
        if (y <= floorY()) {
            if (!level().isClientSide) {
                burst();
            } else {
                setPos(getX(), floorY(), getZ());
            }
            return;
        }
        setPos(getX(), y, getZ());
    }

    /** 0 while it hangs; shuddering harder the nearer it is to letting go. */
    public float shudder(float partialTick) {
        float t = tickCount + partialTick;
        return t >= WARN ? 0.0F : t / WARN;
    }

    /** Come down on a Monstrosity's back: it breaks there, and it is on its knee (HollowGolemEntity#icicleStruck). */
    private boolean onTheColossus(double y) {
        for (HollowGolemEntity g : level().getEntitiesOfClass(HollowGolemEntity.class,
                new net.minecraft.world.phys.AABB(getX() - 0.4D, y - 0.5D, getZ() - 0.4D, getX() + 0.4D, y + 1.0D,
                        getZ() + 0.4D), e -> e.isAlive() && !e.isTamed())) {
            if (level() instanceof ServerLevel s) {
                s.playSound(null, BlockPos.containing(getX(), y, getZ()), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE,
                        2.6F, 0.5F);
                s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), y, getZ(), 60, 0.9D, 0.4D, 0.9D, 0.3D);
            }
            g.icicleStruck();
            discard();
            return true;
        }
        return false;
    }

    protected void burst() {
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        double fy = floorY();
        s.playSound(null, BlockPos.containing(getX(), fy, getZ()), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.2F, 0.5F);
        s.playSound(null, BlockPos.containing(getX(), fy, getZ()), FFSounds.ICE_SHATTER.get(), SoundSource.HOSTILE, 2.4F, 0.7F);
        s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), fy + 0.4D, getZ(), 50, 0.9D, 0.3D, 0.9D, 0.28D);
        Entity owner = ownerId != null ? s.getEntity(ownerId) : null;
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(getX() - RADIUS, fy - 1.0D, getZ() - RADIUS,
                        getX() + RADIUS, fy + 3.0D, getZ() + RADIUS),
                e -> e.isAlive() && !(e instanceof FrostServantEntity) && !(e instanceof HollowGolemEntity))) {
            if (v.distanceToSqr(getX(), v.getY(), getZ()) <= RADIUS * RADIUS) {
                v.hurt(damageSources().indirectMagic(this, owner), 20.0F);
                v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 100, 1));
                v.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 9216.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
