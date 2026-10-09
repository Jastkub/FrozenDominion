package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A portcullis of the cells dropped out of the vault: three wide, four high,
 * a solid wall while it stands (you walk into it, not through it). Four of
 * them round somebody make a cell, and the Turnkey takes his time with them.
 * Four blows break one (his own do not); it lifts and goes on its own after
 * four and a half seconds.
 *
 * <p>A bare entity, not a living one, so it takes no other mod's projectiles
 * (see IcePrisonEntity): it is broken by hand.
 */
public class PortcullisEntity extends Entity implements GeoEntity {

    /** 0 north/south (the grate lies along x), 1 east/west (along z). */
    private static final EntityDataAccessor<Integer> AXIS =
            SynchedEntityData.defineId(PortcullisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RISING =
            SynchedEntityData.defineId(PortcullisEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation DROP = RawAnimation.begin().thenPlay("animation.portcullis.drop")
            .thenLoop("animation.portcullis.hold");
    private static final RawAnimation RISE = RawAnimation.begin().thenPlayAndHold("animation.portcullis.rise");

    private static final int LANDS = 8;
    private static final int LIFE = 90;
    private static final int RISE_TICKS = 16;
    private static final int HITS = 4;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private int hits;
    private int rising;

    public PortcullisEntity(EntityType<? extends PortcullisEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public PortcullisEntity(Level level, @Nullable Entity owner, double x, double y, double z, boolean alongX) {
        this(FFEntities.PORTCULLIS.get(), level);
        this.ownerId = owner == null ? null : owner.getUUID();
        entityData.set(AXIS, alongX ? 0 : 1);
        setPos(x, y, z);
    }

    public boolean alongX() {
        return entityData.get(AXIS) == 0;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(AXIS, 0);
        entityData.define(RISING, false);
    }

    /** Four high, a hand's breadth thick, turned with the grate - and closed at the corners
     *  the north and south grates reach out over the corners, the east
     *  and west ones in to meet them, so the four walls of the cage leave no gap. */
    @Override
    protected AABB makeBoundingBox() {
        double x = getX(), y = getY(), z = getZ();
        return alongX() ? new AABB(x - 2.2D, y, z - 0.2D, x + 2.2D, y + 4.0D, z + 0.2D)
                : new AABB(x - 0.2D, y, z - 1.8D, x + 0.2D, y + 4.0D, z + 1.8D);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (AXIS.equals(key)) {
            setBoundingBox(makeBoundingBox());
        }
    }

    @Override
    public void tick() {
        super.tick();
        setBoundingBox(makeBoundingBox());
        if (level().isClientSide) {
            return;
        }
        if (tickCount == LANDS) {
            // down: whoever is under it is caught
            level().playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4F, 0.5F);
            level().playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
            if (level() instanceof ServerLevel s) {
                s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BARS.defaultBlockState()),
                        getX(), getY() + 0.2D, getZ(), 30, 1.2D, 0.1D, 1.2D, 0.1D);
                s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 0.3D, getZ(), 16, 1.2D, 0.2D, 1.2D, 0.1D);
            }
            for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.1D),
                    e -> !(e instanceof FrostServantEntity) && e.isAlive())) {
                v.hurt(damageSources().fallingBlock(this), 7.0F);
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
                v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 60, 0));
            }
        }
        if (rising > 0) {
            if (--rising == 0) {
                discard();
            }
            return;
        }
        if (tickCount > LIFE) {
            lift();
        }
    }

    private void lift() {
        rising = RISE_TICKS;
        entityData.set(RISING, true);
        level().playSound(null, blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.4F, 0.6F);
    }

    /** A wall while it stands (after it has come down), nothing while it lifts. */
    @Override
    public boolean canBeCollidedWith() {
        return tickCount >= LANDS && rising == 0;
    }

    @Override
    public boolean isPickable() {
        return rising == 0;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || rising > 0) {
            return false;
        }
        Entity by = source.getEntity();
        if (by == null || by instanceof FrostServantEntity || (ownerId != null && ownerId.equals(by.getUUID()))) {
            return false;
        }
        if (!(by instanceof Player) && !(by instanceof LivingEntity)) {
            return false;
        }
        hits++;
        level().playSound(null, blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 1.4F, 0.6F + hits * 0.15F);
        if (level() instanceof ServerLevel s) {
            s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BARS.defaultBlockState()),
                    getX(), getY() + 1.5D, getZ(), 12, 0.6D, 0.6D, 0.6D, 0.1D);
        }
        if (hits >= HITS) {
            level().playSound(null, blockPosition(), SoundEvents.ANVIL_BREAK, SoundSource.HOSTILE, 1.2F, 0.7F);
            discard();
        }
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 6400.0D;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "grate", 0,
                s -> s.setAndContinue(entityData.get(RISING) ? RISE : DROP)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        entityData.set(AXIS, tag.getInt("Axis"));
        hits = tag.getInt("Hits");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putInt("Axis", entityData.get(AXIS));
        tag.putInt("Hits", hits);
    }
}
