package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.block.TurnkeyLockBlock;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * A key off the Turnkey's ring, flung: it flies, turning about its shaft, to
 * one of the locks set in the pillars of his hall. If it gets there it turns
 * in the lock - and the cells answer him, he is mended. Struck on the way
 * (one blow, one arrow) it breaks, and that hurts him; break every key of a
 * throw and he staggers. That is what his fight teaches: when he holds the
 * ring up, watch the keys, not him.
 *
 * <p>A living thing (two points of health, no AI) and not a bare entity:
 * other mods' projectiles cast whatever they hit to LivingEntity, and a key
 * is exactly what a player shoots at.
 */
public class TurnkeyKeyEntity extends Mob implements GeoEntity {

    private static final EntityDataAccessor<Boolean> TURNING =
            SynchedEntityData.defineId(TurnkeyKeyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.turnkey_key.fly");
    private static final RawAnimation TURN = RawAnimation.begin().thenPlayAndHold("animation.turnkey_key.turn");
    /** Blocks a tick: slow enough to be caught, quick enough to need catching. */
    private static final double SPEED = 0.08D;                 // (halved)
    private static final int TURN_TICKS = 12;
    /** Ticks it spends bursting off the ring to its own place in the fan before it makes for its lock. */
    private static final int SCATTER_TICKS = 14;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    /** Where it is going: a lock in his hall - or, with none there, back to him. */
    @Nullable
    private BlockPos lock;
    private int turning;
    private int life;
    /** Where it bursts to first - its own point on a wide fan round him; null: none. */
    @Nullable
    private Vec3 scatter;

    public TurnkeyKeyEntity(EntityType<? extends TurnkeyKeyEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        this.noPhysics = true;
        this.xpReward = 0;
    }

    public TurnkeyKeyEntity(Level level, TurnkeyEntity owner, @Nullable BlockPos lock, Vec3 from) {
        this(FFEntities.TURNKEY_KEY.get(), level);
        this.ownerId = owner.getUUID();
        this.lock = lock;
        moveTo(from.x, from.y, from.z, level.random.nextFloat() * 360.0F, 0.0F);
    }

    public TurnkeyKeyEntity(Level level, TurnkeyEntity owner, @Nullable BlockPos lock, Vec3 from, Vec3 scatter) {
        this(level, owner, lock, from);
        this.scatter = scatter;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 2.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TURNING, false);
    }

    @Override
    protected void registerGoals() {
        // nothing: it goes where it was sent
    }

    @Nullable
    private TurnkeyEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof TurnkeyEntity t
                && t.isAlive()) {
            return t;
        }
        return null;
    }

    /** The point in front of its lock's plate where the key goes in. */
    private Vec3 goal(TurnkeyEntity owner) {
        if (lock == null || !(level().getBlockState(lock).getBlock() instanceof TurnkeyLockBlock)) {
            return owner.position().add(0.0D, 2.4D, 0.0D);
        }
        Direction face = level().getBlockState(lock).getValue(TurnkeyLockBlock.FACING);
        // the plate is on the wall's side of its cell, its face looking out
        return Vec3.atCenterOf(lock).add(-face.getStepX() * 0.3D, -0.3D, -face.getStepZ() * 0.3D);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (!entityData.get(TURNING) && random.nextInt(2) == 0) {
                level().addParticle(FFParticles.SOUL_FROST.get(), getX(), getY() + 0.3D, getZ(), 0.0D, 0.0D, 0.0D);
            }
            return;
        }
        TurnkeyEntity o = owner();
        if (o == null) {
            shatter();
            return;
        }
        life++;
        if (turning > 0) {
            if (turning % 5 == 0) {                              // the lock ticking round: strike it now
                level().playSound(null, blockPosition(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.HOSTILE, 1.1F, 0.5F);
            }
            if (--turning == 0) {
                arrive(o);
            }
            return;
        }
        if (scatter != null && life <= SCATTER_TICKS) {
            // off the ring and out to its own place: fast at first, settling as it gets there
            Vec3 v = scatter.subtract(position()).scale(0.22D);
            setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
            if (v.horizontalDistanceSqr() > 1.0E-4D) {
                float yaw = (float) (Mth.atan2(v.z, v.x) * (180.0D / Math.PI)) - 90.0F;
                setYRot(yaw);
                setYBodyRot(yaw);
                setYHeadRot(yaw);
            }
            return;
        }
        Vec3 goal = goal(o);
        Vec3 to = goal.subtract(position());
        double d = to.length();
        if (d < 0.6D || life > 480) {
            turning = TURN_TICKS;
            entityData.set(TURNING, true);
            setPos(goal.x, goal.y, goal.z);
            level().playSound(null, blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.HOSTILE, 1.2F, 1.4F);
            return;
        }
        if ((life + getId()) % 14 == 0) {                        // it jingles as it flies: it can be found by ear
            level().playSound(null, blockPosition(), SoundEvents.CHAIN_STEP, SoundSource.HOSTILE, 0.7F, 1.9F);
        }
        // on toward the lock, swaying across its line in an arc that dies away near it
        Vec3 dir = to.normalize();
        double sway = Math.sin(life * 0.14D + getId()) * 0.07D * Math.min(1.0D, d / 6.0D);
        Vec3 v = dir.scale(SPEED).add(-dir.z * sway, Math.sin(life * 0.21D) * 0.025D, dir.x * sway);
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        float yaw = (float) (Mth.atan2(v.z, v.x) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    @Override
    public void travel(Vec3 input) {
        // it moves itself (tick), not by walking or by the wind
    }

    /** It turned in its lock: the cells answer him. */
    private void arrive(TurnkeyEntity owner) {
        owner.keyTurned(this, lock);
        if (lock != null) {
            TurnkeyLockBlock.turn(level(), lock);
        }
        if (level() instanceof ServerLevel s) {
            s.playSound(null, blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.HOSTILE, 1.4F, 1.3F);
            s.sendParticles(FFParticles.SOUL_FROST.get(), getX(), getY(), getZ(), 12, 0.2D, 0.2D, 0.2D, 0.04D);
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || turning > 0) {
            return false;
        }
        Entity by = source.getEntity();
        if (by instanceof FrostServantEntity) {
            return false;
        }
        if (by instanceof Player || source.getDirectEntity() instanceof Projectile) {
            TurnkeyEntity o = owner();
            if (o != null) {
                o.keyBroken(this, by);
            }
            shatter();
            return true;
        }
        return false;
    }

    /** Struck down: the brass rings on the floor and the ice in its bow bursts. */
    private void shatter() {
        if (level() instanceof ServerLevel s) {
            s.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6F, 1.8F);
            s.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.9F, 1.4F);
            s.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_INGOT)),
                    getX(), getY() + 0.2D, getZ(), 14, 0.2D, 0.2D, 0.2D, 0.08D);
            s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 0.2D, getZ(), 10, 0.2D, 0.2D, 0.2D, 0.1D);
        }
        discard();
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
        controllers.add(new AnimationController<>(this, "key", 0,
                s -> s.setAndContinue(entityData.get(TURNING) ? TURN : FLY)));
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
        if (lock != null) {
            tag.put("Lock", com.jastkub.frozenfortress.util.FFNbt.pos(lock));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        lock = tag.contains("Lock") ? com.jastkub.frozenfortress.util.FFNbt.pos(tag.getCompound("Lock")) : null;
    }
}
