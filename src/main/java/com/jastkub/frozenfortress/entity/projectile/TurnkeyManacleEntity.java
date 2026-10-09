package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.entity.TurnkeyEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Turnkey's manacle, thrown on its chain: a real thing flying, so it can
 * be seen coming and side-stepped. On someone it CLOSES: for two and a half
 * seconds they are in irons on the end of his chain - slowed, unable to jump
 * clear, hauled back if they get more than seven blocks from him, and yanked
 * in twice. On stone it rings off and drops.
 *
 * <p>The chain itself is drawn by the renderer, from his right hand to this.
 */
public class TurnkeyManacleEntity extends Projectile implements GeoEntity {

    private static final EntityDataAccessor<Integer> HELD =
            SynchedEntityData.defineId(TurnkeyManacleEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.turnkey_manacle.spin");
    private static final RawAnimation SHUT = RawAnimation.begin().thenLoop("animation.turnkey_manacle.held");

    private static final double SPEED = 1.15D;
    private static final int HOLD = 50;
    private static final double TETHER = 7.0D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int held;
    private int dropped;
    /** When the irons open of themselves: the tick the lantern lands (-1: the full hold). */
    private int releaseAt = -1;

    public TurnkeyManacleEntity(EntityType<? extends TurnkeyManacleEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public TurnkeyManacleEntity(Level level, TurnkeyEntity owner, Vec3 from, Vec3 at) {
        this(FFEntities.TURNKEY_MANACLE.get(), level);
        setOwner(owner);
        setPos(from.x, from.y, from.z);
        Vec3 v = at.subtract(from).normalize().scale(SPEED);
        setDeltaMovement(v.x, v.y + 0.05D, v.z);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(HELD, -1);
    }

    /** The one in its irons, or -1. */
    public int heldId() {
        return entityData.get(HELD);
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = getOwner();
        if (!level().isClientSide && (!(owner instanceof TurnkeyEntity t) || !t.isAlive())) {
            discard();
            return;
        }
        if (heldId() >= 0) {
            tickHolding((TurnkeyEntity) owner);
            return;
        }
        if (dropped > 0) {
            setDeltaMovement(getDeltaMovement().multiply(0.5D, 1.0D, 0.5D).add(0.0D, -0.08D, 0.0D));
            setPos(getX() + getDeltaMovement().x, getY() + getDeltaMovement().y, getZ() + getDeltaMovement().z);
            if (!level().isClientSide && --dropped == 0) {
                discard();
            }
            return;
        }
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
            }
            if (tickCount > 24) {
                drop();
            }
        }
        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        setDeltaMovement(v.x * 0.99D, v.y - 0.02D, v.z * 0.99D);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && !(e instanceof FrostServantEntity) && e instanceof LivingEntity;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(result.getEntity() instanceof LivingEntity victim) || !(getOwner() instanceof TurnkeyEntity owner)) {
            return;
        }
        victim.hurt(damageSources().mobProjectile(this, owner), 6.0F);
        entityData.set(HELD, victim.getId());
        held = 0;
        level().playSound(null, blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 2.0F, 0.5F);
        level().playSound(null, blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.HOSTILE, 1.6F, 0.6F);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        level().playSound(null, blockPosition(), SoundEvents.CHAIN_FALL, SoundSource.HOSTILE, 1.6F, 0.7F);
        drop();
    }

    private void drop() {
        dropped = 12;
        setDeltaMovement(getDeltaMovement().scale(0.2D));
    }

    private void tickHolding(TurnkeyEntity owner) {
        Entity e = level().getEntity(heldId());
        if (!(e instanceof LivingEntity victim) || !victim.isAlive()) {
            if (!level().isClientSide) {
                discard();
            }
            return;
        }
        setPos(victim.getX(), victim.getY() + 0.35D, victim.getZ());
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            return;
        }
        held++;
        if (held % 10 == 0) {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
            victim.addEffect(new MobEffectInstance(MobEffects.JUMP, 20, 128));
            victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 40, 0), owner);
        }
        // the chain is seven blocks long: no further from him than that
        Vec3 pull = owner.position().subtract(victim.position());
        double len = pull.length();
        if (len > TETHER) {
            Vec3 v = pull.normalize().scale(Math.min(0.8D, (len - TETHER) * 0.4D));
            victim.setDeltaMovement(victim.getDeltaMovement().add(v.x, 0.05D, v.z));
            victim.hurtMarked = true;
        }
        // he hauls on it - and the first haul brings them to the lantern: he swings it the
        // moment they come, and the irons let go as it lands. Busy with something else, he hauls twice as before.
        if (held == 12) {
            owner.yank(victim);
            if (owner.lanternFor(victim)) {
                releaseAt = held + 14;
            }
        }
        if (held == 32 && releaseAt < 0) {
            owner.yank(victim);
        }
        if (releaseAt > 0 && held >= releaseAt) {
            level().playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.6F, 0.9F);
            discard();
            return;
        }
        if (held >= HOLD) {
            level().playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.6F, 0.9F);
            discard();
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
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "manacle", 0,
                s -> s.setAndContinue(heldId() >= 0 ? SHUT : SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}
