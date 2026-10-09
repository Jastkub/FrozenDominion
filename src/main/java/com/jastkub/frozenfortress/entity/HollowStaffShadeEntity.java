package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * HIS SHADE (07.10.2026): what rises when a thing that carries the King's MARK dies (HollowStaffEvents) - a hooded
 * wraith of the Hollow court, a crown of ice on its hood, long frosted claws, no legs (tools/gen_hollow_staff.py,
 * hollow_staff_shade). It is the wielder's for {@link #LIFE} ticks: it RISES out of a pool of the dark
 * ({@link #RISE_T}), goes for what he goes for - what he last struck, else what last struck him, else a marked foe,
 * else the nearest hostile thing - and claws it ({@link #CLAW_DMG}, frostbite); with nothing to fight it keeps at his
 * side. Then it FADES ({@link #FADE_T}) and is gone. Never more than {@link #MAX_PER_OWNER} at once: a new one sends
 * the oldest away. It takes no hurt from him or his, nor from falling.
 */
public class HollowStaffShadeEntity extends PathfinderMob implements GeoEntity {

    // ---- the beats of its clips (tools/gen_hollow_staff.py SHADE_* - change one, change both)
    public static final int LIFE = 200, RISE_T = 16, FADE_T = 16, ATTACK_T = 12, HIT_AT = 6, COOLDOWN = 20;
    public static final float CLAW_DMG = 6.0F;
    public static final int MAX_PER_OWNER = 4;
    static final double REACH = 2.4D, LEASH = 24.0D;

    private static final EntityDataAccessor<Boolean> FADING =
            SynchedEntityData.defineId(HollowStaffShadeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ATTACKING =
            SynchedEntityData.defineId(HollowStaffShadeEntity.class, EntityDataSerializers.BOOLEAN);

    private static final String P = "animation.hollow_staff_shade.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation MOVE = RawAnimation.begin().thenLoop(P + "move");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay(P + "attack");
    private static final RawAnimation RISE = RawAnimation.begin().thenPlayAndHold(P + "rise");
    private static final RawAnimation FADE = RawAnimation.begin().thenPlayAndHold(P + "fade");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private int life = LIFE;
    private int attackTick = -1;
    private int cooldown;
    @Nullable
    private LivingEntity victim;
    /** Client: the tick it began to fade, for the renderer's alpha. */
    public int fadeStart = -1;

    public HollowStaffShadeEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.ATTACK_DAMAGE, CLAW_DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }

    /** A marked foe has died: its shade rises for `owner`. */
    public static HollowStaffShadeEntity rise(ServerLevel level, LivingEntity owner, LivingEntity dead) {
        List<HollowStaffShadeEntity> mine = level.getEntitiesOfClass(HollowStaffShadeEntity.class,
                owner.getBoundingBox().inflate(64.0D), s -> s.ownedBy(owner) && !s.fading());
        if (mine.size() >= MAX_PER_OWNER) {
            mine.sort(Comparator.comparingInt(s -> s.life));
            mine.get(0).life = Math.min(mine.get(0).life, FADE_T);
        }
        HollowStaffShadeEntity s = new HollowStaffShadeEntity(FFEntities.HOLLOW_STAFF_SHADE.get(), level);
        s.ownerId = owner.getUUID();
        s.moveTo(dead.getX(), dead.getY(), dead.getZ(), owner.getYRot(), 0.0F);
        s.setYHeadRot(owner.getYRot());
        level.addFreshEntity(s);
        HollowStaffFxEntity.spawn(level, HollowStaffFxEntity.RISE, dead.position(), 0.0F,
                Math.max(1.0F, dead.getBbWidth() / 0.8F), 20);
        level.playSound(null, s.getX(), s.getY(), s.getZ(), FFSounds.HOLLOW_STAFF_SHADE_RISE.get(), SoundSource.PLAYERS,
                1.2F, 0.9F + level.random.nextFloat() * 0.2F);
        return s;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FADING, false);
        entityData.define(ATTACKING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public boolean ownedBy(Entity e) {
        return ownerId != null && ownerId.equals(e.getUUID());
    }

    public boolean fading() {
        return entityData.get(FADING);
    }

    @Nullable
    public LivingEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof LivingEntity le
                && le.isAlive()) {
            return le;
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------ its time
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && fading() && fadeStart < 0) {
            fadeStart = tickCount;
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        life--;
        LivingEntity owner = owner();
        if (!fading() && (life <= FADE_T || owner == null)) {
            entityData.set(FADING, true);
            entityData.set(ATTACKING, false);
            life = Math.min(life, FADE_T);
            playSound(FFSounds.HOLLOW_STAFF_SHADE_FADE.get(), 1.0F, 1.0F);
        }
        if (fading()) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.5D, 1.0D, 0.5D));
            if (life <= 0) {
                HollowStaffFxEntity.spawn(level(), HollowStaffFxEntity.BURST, position().add(0.0D, 1.0D, 0.0D), getYRot(),
                        1.4F, 10);
                discard();
            }
            return;
        }
        if (tickCount < RISE_T) {
            getNavigation().stop();
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (attackTick >= 0) {                                    // in the middle of a claw
            int t = tickCount - attackTick;
            if (victim != null) {
                getLookControl().setLookAt(victim, 60.0F, 60.0F);
            }
            if (t == HIT_AT && victim != null && victim.isAlive() && distanceToSqr(victim) < (REACH + 1.2D) * (REACH + 1.2D)) {
                HollowStaffMagic.strike(victim, this, owner, CLAW_DMG);
                victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 60, 0), owner);
            }
            if (t >= ATTACK_T) {
                attackTick = -1;
                entityData.set(ATTACKING, false);
            }
            return;
        }
        if (victim == null || !victim.isAlive() || tickCount % 10 == 0) {
            victim = pick(owner);
        }
        if (victim != null) {
            if (tickCount % 5 == 0 || getNavigation().isDone()) {
                getNavigation().moveTo(victim, 1.3D);                // (a path every few ticks, not every tick)
            }
            getLookControl().setLookAt(victim, 30.0F, 30.0F);
            if (cooldown == 0 && distanceToSqr(victim) < REACH * REACH) {
                attackTick = tickCount;
                cooldown = COOLDOWN;
                entityData.set(ATTACKING, true);
                Vec3 to = victim.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
                if (to.lengthSqr() > 1.0E-4) {
                    setDeltaMovement(to.normalize().scale(0.45D).add(0.0D, 0.12D, 0.0D));
                }
                playSound(FFSounds.HOLLOW_STAFF_SHADE_ATTACK.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
            }
        } else if (distanceToSqr(owner) > 16.0D) {
            if (tickCount % 5 == 0 || getNavigation().isDone()) {
                getNavigation().moveTo(owner, 1.2D);
            }
        } else {
            getNavigation().stop();
            getLookControl().setLookAt(owner, 10.0F, 10.0F);
        }
        if (distanceToSqr(owner) > LEASH * LEASH * 4.0D) {
            moveTo(owner.getX(), owner.getY(), owner.getZ());            // left far behind: it comes back through the dark
        }
    }

    /** What he struck, else what struck him, else a foe he marked, else the nearest hostile thing. */
    @Nullable
    private LivingEntity pick(LivingEntity owner) {
        LivingEntity a = owner.getLastHurtMob();
        if (fit(owner, a) && owner.tickCount - owner.getLastHurtMobTimestamp() < 200) {
            return a;
        }
        LivingEntity b = owner.getLastHurtByMob();
        if (fit(owner, b)) {
            return b;
        }
        List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12.0D),
                e -> fit(owner, e) && (e instanceof Enemy || (HollowStaffMagic.marked(e)
                        && owner.getUUID().equals(HollowStaffMagic.markedBy(e)))
                        || (e instanceof Mob m && m.getTarget() == owner)));
        near.sort(Comparator.comparingDouble(e -> (HollowStaffMagic.marked(e) ? 0.0D : 64.0D) + e.distanceToSqr(this)));
        return near.isEmpty() ? null : near.get(0);
    }

    private boolean fit(LivingEntity owner, @Nullable LivingEntity e) {
        return e != null && e != this && e.isAlive() && HollowStaffMagic.isFoe(owner, e)
                && e.distanceToSqr(owner) < LEASH * LEASH;
    }

    // ------------------------------------------------------------------------------------------------ its body
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity by = source.getEntity();
        if (by != null && (ownedBy(by) || (by instanceof HollowStaffShadeEntity s && ownerId != null && s.ownerId != null
                && ownerId.equals(s.ownerId)))) {
            return false;                                        // not from him, nor his
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isAlliedTo(Entity e) {
        if (ownedBy(e) || (e instanceof HollowStaffShadeEntity s && ownerId != null && ownerId.equals(s.ownerId))) {
            return true;
        }
        return super.isAlliedTo(e);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (deathTime >= FADE_T && !level().isClientSide && !isRemoved()) {
            HollowStaffFxEntity.spawn(level(), HollowStaffFxEntity.BURST, position().add(0.0D, 1.0D, 0.0D), getYRot(),
                    1.4F, 10);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        entityData.set(FADING, true);
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        discard();                                               // ten seconds do not outlast a reload
    }

    // ------------------------------------------------------------------------------------------------ its clips
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "shade", 3, state -> {
            if (fading() || isDeadOrDying()) {
                return state.setAndContinue(FADE);
            }
            if (tickCount < RISE_T) {
                return state.setAndContinue(RISE);
            }
            if (entityData.get(ATTACKING)) {
                return state.setAndContinue(ATTACK);
            }
            return state.setAndContinue(FrostServantEntity.going(state) ? MOVE : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
