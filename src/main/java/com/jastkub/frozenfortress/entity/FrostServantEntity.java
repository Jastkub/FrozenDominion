package com.jastkub.frozenfortress.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base class for all inhabitants of the Frozen Fortress.
 * Provides the synced attack-state machine that drives both server-side
 * attack timing and client-side GeckoLib animation selection.
 */
public abstract class FrostServantEntity extends Monster implements software.bernie.geckolib.animatable.GeoEntity,
        com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity.FrostServantAlly {

    protected static final EntityDataAccessor<Integer> ATTACK_STATE =
            SynchedEntityData.defineId(FrostServantEntity.class, EntityDataSerializers.INT);
    /** Which of its deaths it is dying (deathVariants): chosen on the server as it dies, the clip picked by it. */
    private static final EntityDataAccessor<Integer> DEATH_VARIANT =
            SynchedEntityData.defineId(FrostServantEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** Server-side ticks spent in the current attack state. */
    protected int attackTicks;

    protected FrostServantEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ATTACK_STATE, 0);
        entityData.define(DEATH_VARIANT, 0);
    }

    public int getAttackState() {
        return entityData.get(ATTACK_STATE);
    }

    public void setAttackState(int state) {
        entityData.set(ATTACK_STATE, state);
        attackTicks = 0;
        if (BEATS_LOGGED && state != 0 && !level().isClientSide) {
            com.mojang.logging.LogUtils.getLogger().info("[beat] {} {}", getType().getDescriptionId(), state);
        }
    }

    /** For tests on a dev server (tools/server_frosty_test.py): -Dfrozen_dominion.logBeats=true logs every beat. */
    private static final boolean BEATS_LOGGED = Boolean.getBoolean("frozen_dominion.logBeats");

    public boolean isAttacking() {
        return getAttackState() != 0;
    }

    /**
     * The court never turns on itself, and never on the Sovereign - not even
     * when a stray arrow or a slam from the throne catches one of them.
     */
    @Override
    public void setTarget(@javax.annotation.Nullable net.minecraft.world.entity.LivingEntity target) {
        if (target instanceof FrostServantEntity
                || target instanceof com.jastkub.frozenfortress.entity.boss.VelkharEntity
                || target instanceof com.jastkub.frozenfortress.entity.boss.VelkharCloneEntity) {
            return;
        }
        super.setTarget(target);
    }

    /** Friendly fire from the court passes straight through. */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (source.getEntity() instanceof FrostServantEntity
                || source.getEntity() instanceof com.jastkub.frozenfortress.entity.boss.VelkharEntity
                || source.getEntity() instanceof com.jastkub.frozenfortress.entity.boss.VelkharCloneEntity) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && isAttacking()) {
            attackTicks++;
        }
    }

    /** Servants of the Silent Winter cannot freeze. */
    @Override
    public boolean canFreeze() {
        return false;
    }

    // ------------------------------------------------------------------ more than one way to die (12.10.2026)
    /**
     * How many deaths it has: its death clips
     * death, death_2, ... - one of them, at random, as it dies. 1, the default: its one death.
     */
    protected int deathVariants() {
        return 1;
    }

    /** The death it is dying (0 for the first): synced, so the clip is the same for everyone watching. */
    public int deathVariant() {
        return entityData.get(DEATH_VARIANT);
    }

    /** Its clip for the death it is dying, of `clips` (death, death_2, ...). */
    protected software.bernie.geckolib.core.animation.RawAnimation deathAnim(
            software.bernie.geckolib.core.animation.RawAnimation[] clips) {
        return clips[Math.max(0, Math.min(clips.length - 1, deathVariant()))];
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        // chosen in the tick its health went to nothing: the choice goes out in the same update as its death
        if (!level().isClientSide && deathVariants() > 1 && !isRemoved() && !dead) {
            entityData.set(DEATH_VARIANT, random.nextInt(deathVariants()));
        }
        super.die(source);
    }

    /** How long the body lingers so its death animation can finish. */
    protected int getDeathDuration() {
        return 40;
    }

    /**
     * THE BODY FADES: those that die by falling and lying - no shattering into ice - go
     * from the world over these last ticks of their dying, drawn ever fainter (FrostGeoRenderer), and leave no puff of
     * smoke behind. 0, the default: the body stays whole to its end and goes with the game's own puff.
     */
    public int fadeTicks() {
        return 0;
    }

    /** The death's duration, readable by the renderer (it fades the body over its end - fadeTicks). */
    public int deathDuration() {
        return getDeathDuration();
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= getDeathDuration() && !level().isClientSide() && !isRemoved()) {
            if (fadeTicks() <= 0) {
                level().broadcastEntityEvent(this, (byte) 60);
            }
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // ------------------------------------------------------------------ standing and going (08.10.2026)
    /** Now and then, in place of the idle, its fidget: this many ticks (the fidget clips' length)... */
    protected static final int FIDGET_TICKS = 60;
    /** ...in every this many, on a beat of its own so that a hall of them is not in step. */
    protected static final int FIDGET_EVERY = 440;

    /** Client: on the full stride rather than the stroll - held a little past its threshold either way. */
    private boolean striding;

    /**
     * Which of its standing and going clips plays now: the idle, now and then its fidget instead, the
     * stroll at a wander's pace and the walk at a chase's. Chosen on how far it really goes - the limb swing the game
     * keeps for every living thing, four times its speed in blocks a tick, eased - and not on GeckoLib's "moving"
     * alone, which stays false at a wander: a garrison strolling at half speed slid along the floor in its idle.
     *
     * @param fidget      the idle's occasional variant, or null
     * @param stroll      the slow walk, or null (then the walk at any pace)
     * @param strollBelow the limb swing under which it strolls - between its wander's and its chase's
     */
    protected software.bernie.geckolib.core.animation.RawAnimation locomotion(
            software.bernie.geckolib.core.animation.AnimationState<?> state,
            software.bernie.geckolib.core.animation.RawAnimation idle,
            @javax.annotation.Nullable software.bernie.geckolib.core.animation.RawAnimation fidget,
            @javax.annotation.Nullable software.bernie.geckolib.core.animation.RawAnimation stroll,
            software.bernie.geckolib.core.animation.RawAnimation walk, float strollBelow) {
        float swing = state.getLimbSwingAmount();
        if (!state.isMoving() && swing < 0.05F) {
            striding = false;
            return fidget != null && fidgeting() ? fidget : idle;
        }
        if (stroll == null) {
            return walk;
        }
        striding = swing > (striding ? strollBelow * 0.8F : strollBelow);
        return striding ? walk : stroll;
    }

    /**
     * Going, not standing - for any of the mod's creatures with a walk of its own: GeckoLib's "moving" (a limb swing past 0.15) or any limb swing past 0.05, as
     * {@link #locomotion} takes it. GeckoLib's alone stays false at a wander's or a stalk's pace, and whatever went
     * slowly slid along the floor in its idle.
     */
    public static boolean going(software.bernie.geckolib.core.animation.AnimationState<?> state) {
        return state.isMoving() || state.getLimbSwingAmount() >= 0.05F;
    }

    /** In its fidget's window: FIDGET_TICKS of every FIDGET_EVERY. */
    protected boolean fidgeting() {
        return Math.floorMod(tickCount + getId() * 97, FIDGET_EVERY) < FIDGET_TICKS;
    }
}
