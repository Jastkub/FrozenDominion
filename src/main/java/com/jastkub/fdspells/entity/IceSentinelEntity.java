package com.jastkub.fdspells.entity;

import com.jastkub.fdspells.registry.FDSRegistry;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * LODOWY STRAŻNIK: a sentinel of ice with a tower shield and a sword, raised for a while by its summoner. It keeps by
 * them, goes for whatever they fight (or whatever fights them), and takes blows from the front on its shield - a
 * third of the damage gets through. When its time is up it breaks into pieces.
 */
public class IceSentinelEntity extends PathfinderMob implements GeoEntity, IMagicSummon {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.ice_sentinel.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.ice_sentinel.walk");
    private static final RawAnimation SWING = RawAnimation.begin().thenPlay("animation.ice_sentinel.swing");
    private static final RawAnimation RISE = RawAnimation.begin().thenPlay("animation.ice_sentinel.rise");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    @Nullable
    private LivingEntity ownerCache;
    private int lifeLeft = 1200;
    private boolean wasSwinging;

    public IceSentinelEntity(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7D)
                .add(Attributes.STEP_HEIGHT, 1.0D);           // (1.21: the step is an attribute)
    }

    public void summon(LivingEntity caster, float health, float damage, int ticks) {
        ownerId = caster.getUUID();
        ownerCache = caster;
        lifeLeft = ticks;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage);
        setHealth(health);
    }

    @Override
    public Entity getSummoner() {
        if ((ownerCache == null || ownerCache.isRemoved()) && ownerId != null && level() instanceof ServerLevel sl
                && sl.getEntity(ownerId) instanceof LivingEntity le) {
            ownerCache = le;
        }
        return ownerCache;
    }

    @Nullable
    private LivingEntity owner() {
        return getSummoner() instanceof LivingEntity le ? le : null;
    }

    @Override
    public void onUnSummon() {
        if (!level().isClientSide) {
            playSound(FDSRegistry.ICE_SHATTER.get(), 1.2F, 0.8F);
            discard();
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25D, true));
        goalSelector.addGoal(2, new KeepByOwner());
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnersFight());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        LivingEntity o = owner();
        if (--lifeLeft <= 0 || o == null || !o.isAlive()) {
            onUnSummon();
            return;
        }
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || !FxEntity.isFoe(o, t))) {
            setTarget(null);
        }
    }

    /** The tower shield in front: what comes at its face is mostly turned. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity by = source.getEntity();
        if (by != null && (by == getSummoner() || shouldIgnoreDamage(source))) {
            return false;
        }
        Vec3 from = source.getSourcePosition();
        if (from != null && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            Vec3 look = Vec3.directionFromRotation(0.0F, yBodyRot);
            Vec3 to = from.subtract(position()).multiply(1.0D, 0.0D, 1.0D).normalize();
            if (look.dot(to) > 0.45D) {
                amount *= 0.33F;
                playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + random.nextFloat() * 0.3F);
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return super.isAlliedTo(other) || isAlliedHelper(other);
    }

    @Override
    public void die(DamageSource source) {
        onDeathHelper();
        super.die(source);
    }

    @Override
    public void onRemovedFromLevel() {
        onRemovedHelper(this);
        super.onRemovedFromLevel();
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FDSRegistry.ICE_IMPACT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FDSRegistry.ICE_SHATTER.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LifeLeft", lifeLeft);
        if (ownerId != null) {
            tag.putUUID("Summoner", ownerId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lifeLeft = tag.getInt("LifeLeft");
        if (tag.hasUUID("Summoner")) {
            ownerId = tag.getUUID("Summoner");
        }
    }

    // ------------------------------------------------------------------ AI
    /** Back to its summoner when it strays (and with them at once when it is far behind). */
    private final class KeepByOwner extends Goal {
        KeepByOwner() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity o = owner();
            return o != null && getTarget() == null && distanceToSqr(o) > 36.0D;
        }

        @Override
        public void tick() {
            LivingEntity o = owner();
            if (o == null) {
                return;
            }
            if (distanceToSqr(o) > 24.0D * 24.0D) {
                teleportTo(o.getX(), o.getY(), o.getZ());
            } else {
                getNavigation().moveTo(o, 1.15D);
            }
        }
    }

    /** What it fights: what hurt its summoner, what its summoner hurt, else the nearest hostile near them. */
    private final class OwnersFight extends Goal {
        @Override
        public boolean canUse() {
            if (tickCount % 10 != 0) {
                return false;
            }
            LivingEntity o = owner();
            if (o == null) {
                return false;
            }
            LivingEntity pick = null;
            for (LivingEntity c : new LivingEntity[]{o.getLastHurtByMob(), o.getLastHurtMob()}) {
                if (c != null && c.isAlive() && c != IceSentinelEntity.this && FxEntity.isFoe(o, c)
                        && c.distanceToSqr(o) < 24.0D * 24.0D) {
                    pick = c;
                    break;
                }
            }
            if (pick == null) {
                Mob m = level().getNearestEntity(Mob.class,
                        net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat().range(12.0D)
                                .selector(e -> e instanceof Enemy && FxEntity.isFoe(o, e)),
                        IceSentinelEntity.this, getX(), getY(), getZ(), getBoundingBox().inflate(12.0D, 4.0D, 12.0D));
                pick = m;
            }
            if (pick != null && pick != getTarget()) {
                setTarget(pick);
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 4, state -> {
            if (tickCount < 20) {
                return state.setAndContinue(RISE);
            }
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "swing", 0, state -> {
            boolean start = swinging && !wasSwinging;
            wasSwinging = swinging;
            if (start) {
                state.getController().forceAnimationReset();
                return state.setAndContinue(SWING);
            }
            return state.getController().hasAnimationFinished() ? PlayState.STOP : PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
