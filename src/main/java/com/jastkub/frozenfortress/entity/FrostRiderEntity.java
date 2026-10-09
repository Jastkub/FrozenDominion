package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.entity.projectile.FrostSnowballEntity;
import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.RawAnimation;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * JEZDZIEC SZRONU - THE FROST RIDER: one of the
 * guard's dead, kettle hat and pauldron, in the saddle of a Frostmaw. The hound leads - round its quarry at a throw's
 * length, biting only what comes close (FrostmawEntity.PackGoal) - and the rider throws: snowballs packed hard as
 * ice, VOLLEY of them at a time, each wound up overhead and let go at RELEASE (FrostSnowballEntity).
 *
 * <p>APART: the hound killed, its rider is thrown off and fights on foot like any of the dead (it is one - pile,
 * reform and all); the rider beaten, it falls off, and the hound goes mad (FrostmawEntity.enrage).
 *
 * <p>Where it comes from: FrostmawEntity.finalizeSpawn - out of a Frostmaw's statue or nest, one in
 * RIDDEN_CHANCE; laid in a structure as a Frostmaw with "Ridden", always.
 */
public class FrostRiderEntity extends FrostSkeletonEntity {

    /** Its own number, clear of every state of the dead it is (FrostSkeletonEntity uses 1 to 11): it was 10, which
     *  is the skeleton's ST_CLIMB - every throw ran the climb in FrostSkeletonEntity.aiStep, which set the rider's
     *  position up a wall that is not there, and the saddle put it back the next tick. */
    public static final int ST_THROW = 20;
    /** Into the throw: the let-go (gen_frost_skeleton RELEASE, 0.35 s), and its end (the clip's 0.6 s). */
    public static final int RELEASE = 7, THROW_END = 12;
    /** Throws a volley, and between volleys this long and a little more. */
    private static final int VOLLEY = 3, VOLLEY_REST = 45;
    /** How far it throws: no closer (its hound's bite is for that), no further. */
    private static final double NEAR = 2.5D, FAR = 22.0D;

    private static final RawAnimation RIDE = RawAnimation.begin().thenLoop("animation.frost_skeleton.ride");
    private static final RawAnimation RIDE_THROW = RawAnimation.begin().thenPlay("animation.frost_skeleton.ride_throw");

    private int volleyLeft = VOLLEY;
    private int throwCooldown = 30;
    /** Client: ticks into the throw (the snowball shows in its hand until the let-go). */
    private int throwClient;

    public FrostRiderEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 8;
        setVariant(1);                                            // the guard's: kettle hat and pauldron
    }

    public static AttributeSupplier.Builder createAttributes() {
        return FrostSkeletonEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    /** A rider made for `maw` and put in its saddle (it is added to the world with it - addFreshEntityWithPassengers). */
    public static void mount(ServerLevelAccessor level, FrostmawEntity maw) {
        FrostRiderEntity r = FFEntities.FROST_RIDER.get().create(level.getLevel());
        if (r == null) {
            return;
        }
        r.moveTo(maw.getX(), maw.getY(), maw.getZ(), maw.getYRot(), 0.0F);
        r.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(maw.position())),
                MobSpawnType.JOCKEY, null, null);
        r.setPersistenceRequired();
        r.startRiding(maw, true);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new SaddleGoal(this));
    }

    /** Sat in its saddle: the hound's hips are its own. */
    @Override
    public double getMyRidingOffset() {
        return -11.0D / 16.0D;                                    // (the bottom of its pelvis on the seat)
    }

    @Nullable
    @Override
    protected RawAnimation ridingAnim() {
        if (!isPassenger()) {
            return null;
        }
        return getAttackState() == ST_THROW ? RIDE_THROW : RIDE;
    }

    /** Client: the snowball is in its hand - from the wind-up to the let-go. */
    public boolean holdsSnowball() {
        return getAttackState() == ST_THROW && throwClient <= RELEASE;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            throwClient = getAttackState() == ST_THROW ? throwClient + 1 : 0;
            return;
        }
        if (throwCooldown > 0) {
            throwCooldown--;
        }
        // Seated it cannot fall over: any fall it was in when it was put in the saddle (or one
        // something else knocked it into) ends here, before it can play on top of the hound.
        int st = getAttackState();
        if (isPassenger() && (st == ST_TRIP || st == ST_DOWN || st == ST_GETUP || st == ST_LUNGE || st == ST_CLING)) {
            setAttackState(0);
            return;
        }
        if (getAttackState() != ST_THROW) {
            return;
        }
        if (!isPassenger()) {
            setAttackState(0);                                    // thrown off mid-throw
            return;
        }
        if (attackTicks == 2) {
            playSound(SoundEvents.SNOW_STEP, 0.8F, 0.6F);         // packing it in the hand
        }
        if (attackTicks == RELEASE) {
            LivingEntity t = getTarget();
            if (t != null && t.isAlive()) {
                throwAt(t);
            }
        }
        if (attackTicks >= THROW_END) {
            setAttackState(0);
            if (--volleyLeft > 0) {
                throwCooldown = 3;
            } else {
                volleyLeft = VOLLEY;
                throwCooldown = VOLLEY_REST + random.nextInt(20);
            }
        }
    }

    /** One snowball, from its hand at where they will be: led by their going, lifted for the drop of its arc. */
    private void throwAt(LivingEntity t) {
        Vec3 hand = position().add(0.0D, getBbHeight() * 0.85D, 0.0D);
        FrostSnowballEntity ball = new FrostSnowballEntity(level(), this, hand);
        double flight = Math.max(4.0D, hand.distanceTo(t.getEyePosition())) / FrostSnowballEntity.SPEED;
        Vec3 aim = t.position().add(t.getDeltaMovement().multiply(flight, 0.0D, flight))
                .add(0.0D, t.getBbHeight() * 0.55D, 0.0D);
        Vec3 d = aim.subtract(hand);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        // lifted by exactly what gravity (0.03 a tick) takes over its time in the air, so it drops onto them
        double tt = flat / FrostSnowballEntity.SPEED;
        ball.shoot(d.x, d.y + 0.015D * tt * tt, d.z, (float) FrostSnowballEntity.SPEED, 3.0F);
        level().addFreshEntity(ball);
        playSound(SoundEvents.SNOWBALL_THROW, 1.0F, 0.55F + random.nextFloat() * 0.15F);
    }

    /** Beaten in the saddle: it falls off, and its hound goes mad. */
    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && getVehicle() instanceof FrostmawEntity maw) {
            stopRiding();
            maw.enrage();
        }
        super.die(source);
    }

    /** In the saddle: it throws (and the walking ways of its kind wait - they share its MOVE and LOOK). */
    static class SaddleGoal extends Goal {
        private final FrostRiderEntity mob;

        SaddleGoal(FrostRiderEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return mob.isPassenger() && mob.getVehicle() instanceof FrostmawEntity && t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) {
                return;
            }
            mob.getLookControl().setLookAt(t, 40.0F, 40.0F);
            // the hound goes after what its rider has its eye on
            if (mob.getVehicle() instanceof FrostmawEntity maw && maw.getTarget() != t) {
                maw.setTarget(t);
            }
            if (mob.getAttackState() != 0 || mob.throwCooldown > 0) {
                return;
            }
            double d = mob.distanceTo(t);
            if (d >= NEAR && d <= FAR && mob.hasLineOfSight(t)) {
                mob.setAttackState(ST_THROW);
            }
        }
    }
}
