package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * THE VAULT LOCKS: one of eight grates of ice that drop round whoever
 * he calls them on and close the vault on them, three seconds. It falls from overhead - half a second to get out
 * from under the ring before it lands - and then it is a wall: two blows break one and open a way out. He does not
 * wait: the moment they are down he charges, and goes straight through his own grates (they burst on him), so
 * staying in the cage means taking the charge with nowhere to step.
 *
 * <p>A bare entity like the Turnkey's portcullis (it takes no other mod's projectiles - see IcePrisonEntity).
 */
public class VaultBarEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Boolean> BROKEN =
            SynchedEntityData.defineId(VaultBarEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation DROP = RawAnimation.begin().thenPlay("animation.vault_bar.drop")
            .thenLoop("animation.vault_bar.hold");
    private static final RawAnimation BURST = RawAnimation.begin().thenPlayAndHold("animation.vault_bar.shatter");

    /** It lands on this tick (its clip falls in ten). */
    public static final int LANDS = 10;
    /** And stands this long. */
    private static final int HOLDS = 60;
    private static final int HITS = 2;
    private static final int BURST_TICKS = 8;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private int hits;
    private int bursting;

    public VaultBarEntity(EntityType<? extends VaultBarEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /** A grate falling at (x, y, z), its face turned to `yaw`. */
    public VaultBarEntity(Level level, @Nullable Entity owner, double x, double y, double z, float yaw) {
        this(FFEntities.VAULT_BAR.get(), level);
        this.ownerId = owner == null ? null : owner.getUUID();
        // stood on the floor, whatever the one it was called on was doing in the air
        BlockPos at = BlockPos.containing(x, y + 0.5D, z);
        for (int i = 0; i < 4 && at.getY() > level.getMinBuildHeight() && level.getBlockState(at.below()).isAir(); i++) {
            at = at.below();
        }
        moveTo(x, at.getY(), z, yaw, 0.0F);
        setYRot(yaw);
    }

    public boolean broken() {
        return entityData.get(BROKEN);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(BROKEN, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (bursting > 0) {
            if (--bursting == 0) {
                discard();
            }
            return;
        }
        if (tickCount == LANDS) {
            level().playSound(null, blockPosition(), FFSounds.ICE_IMPACT.get(), SoundSource.HOSTILE, 1.4F, 0.7F);
            level().playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.0F, 0.5F);
            for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.1D),
                    e -> !(e instanceof FrostServantEntity) && e.isAlive())) {
                v.hurt(damageSources().fallingBlock(this), 5.0F);
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
                v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 60, 0));
            }
        }
        // he goes through his own grates: they burst on him
        if (tickCount >= LANDS && !level().getEntitiesOfClass(VaultWardenEntity.class, getBoundingBox().inflate(0.4D),
                VaultWardenEntity::isCharging).isEmpty()) {
            burst();
            return;
        }
        if (tickCount > LANDS + HOLDS) {
            burst();
        }
    }

    private void burst() {
        if (bursting > 0) {
            return;
        }
        bursting = BURST_TICKS;
        entityData.set(BROKEN, true);
        level().playSound(null, blockPosition(), FFSounds.ICE_SHATTER.get(), SoundSource.HOSTILE, 1.4F, 0.9F);
    }

    /** A wall once it is down, until it bursts. */
    @Override
    public boolean canBeCollidedWith() {
        return tickCount >= LANDS && !broken();
    }

    @Override
    public boolean isPickable() {
        return !broken();
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
        if (level().isClientSide || isRemoved() || broken()) {
            return false;
        }
        Entity by = source.getEntity();
        if (!(by instanceof LivingEntity) || by instanceof FrostServantEntity
                || (ownerId != null && ownerId.equals(by.getUUID()))) {
            return false;
        }
        hits++;
        level().playSound(null, blockPosition(), FFSounds.ICE_CRACK.get(), SoundSource.HOSTILE, 1.3F, 0.8F + hits * 0.2F);
        if (hits >= HITS) {
            burst();
        }
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 6400.0D;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "bar", 0, s -> s.setAndContinue(broken() ? BURST : DROP)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
