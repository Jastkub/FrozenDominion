package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.entity.projectile.IceArrowEntity;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.EnumSet;

/**
 * STILLBOW - what remains of the court's huntsmen, made over.
 *
 * <p>It never stands still to be reached: it keeps its distance, steps aside along the edge of it while it looks
 * for the shot, hops back from anyone who closes, and when caught close it folds into the cold and is elsewhere -
 * but only once in a while, so it CAN be run down. Its arrows:
 * <ul>
 *   <li><b>THE AIMED SHOT</b>: drawn, a glint of the point (14), loosed at where you stood (20) - a step aside
 *   in the half second after the glint and it misses;</li>
 *   <li><b>THE PINNING SHOT</b>: drawn longer, heavier; what it strikes is nailed in place for a second and a
 *   half;</li>
 *   <li><b>THE RAIN</b>: loosed at the sky (16); a ring of frost marks where it will fall, and a second later
 *   it falls there. Leave the ring.</li>
 * </ul>
 * Lighter than before (22 health, and its arrows hit about half as hard).
 */
public class StillbowEntity extends FrostServantEntity {

    public static final int SHOOT = 1, PIN = 2, RAIN = 3, BLINK = 4, BACKSTEP = 5;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.stillbow.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.stillbow.walk");
    private static final RawAnimation STROLL = RawAnimation.begin().thenLoop("animation.stillbow.stroll");
    private static final RawAnimation IDLE_SCAN = RawAnimation.begin().thenPlay("animation.stillbow.idle_scan");
    private static final RawAnimation SHOOT_ANIM = RawAnimation.begin().thenPlay("animation.stillbow.shoot");
    private static final RawAnimation PIN_ANIM = RawAnimation.begin().thenPlay("animation.stillbow.pin");
    private static final RawAnimation RAIN_ANIM = RawAnimation.begin().thenPlay("animation.stillbow.rain");
    private static final RawAnimation BLINK_ANIM = RawAnimation.begin().thenPlay("animation.stillbow.blink");
    private static final RawAnimation BACKSTEP_ANIM = RawAnimation.begin().thenPlay("animation.stillbow.backstep");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.stillbow.hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("animation.stillbow.death");

    int blinkCooldown;
    int backstepCooldown;
    /** Where the rain will come down, and how long until it is done (it outlives the shot that called it). */
    private Vec3 rainAt;
    private int rainLeft;
    private static final double RAIN_RADIUS = 3.2D;
    private static final int RAIN_WARN = 16;
    private static final int RAIN_FALL = 14;
    /** The volley: how many arrows, what it does to whoever is in the ring as they land (one strike), and when. */
    private static final int VOLLEY_ARROWS = 16;
    private static final float VOLLEY_DAMAGE = 6.5F;
    private int volleyLands = -1;

    public StillbowEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 22.0D)       // (26 before 06.10.2026; 35 before 04.10)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new HuntsmanGoal(this));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected int shatterTick() {
        return 30;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (blinkCooldown > 0) {
            blinkCooldown--;
        }
        if (backstepCooldown > 0) {
            backstepCooldown--;
        }
        tickRain();
    }

    // ------------------------------------------------------------------ the arrows
    /** Loosed at where they stand now - no lead: the step aside after the glint is the answer. */
    void loose(LivingEntity target, double baseDamage, float speed, boolean pin) {
        IceArrowEntity arrow = new IceArrowEntity(level(), this);
        if (pin) {
            arrow.pinning();
        }
        arrow.setBaseDamage(baseDamage);
        arrow.setPos(getX(), getEyeY() - 0.1D, getZ());
        double dx = target.getX() - getX();
        double dy = target.getY(0.5D) - arrow.getY();
        double dz = target.getZ() - getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.08D, dz, speed, 1.0F);
        level().addFreshEntity(arrow);
        playSound(FFSounds.STILLBOW_SHOOT.get(), 1.0F, (pin ? 0.75F : 0.95F) + random.nextFloat() * 0.2F);
    }

    void glint() {
        playSound(FFSounds.CRYSTAL_CHIME.get(), 0.9F, 1.6F);
        // the glint on the arrow's head: a star of light, a thing with a shape (AttackFxEntity)
        Vec3 tip = position().add(getViewVector(1.0F).scale(1.1D)).add(0.0D, 1.45D, 0.0D);
        AttackFxEntity.spawn(level(), AttackFxEntity.GLINT, tip, 0.0F, 0.7F, 8, this);
    }

    /**
     * The shot at the sky - and the ring it will come down in.
     * The shot up is a streak of cold light now, up to whatever is overhead; the rain falls from under the roof.
     */
    void callRain(LivingEntity target) {
        playSound(FFSounds.STILLBOW_SHOOT.get(), 1.2F, 0.7F);
        // a real arrow up, that strikes nothing and is gone at the vault (IceArrowEntity.signal)
        IceArrowEntity up = new IceArrowEntity(level(), this).signal();
        up.setPos(getX(), getEyeY(), getZ());
        up.shoot(0.0D, 1.0D, 0.0D, 2.4F, 0.0F);
        level().addFreshEntity(up);
        rainAt = target.position();
        rainLeft = RAIN_WARN + RAIN_FALL;
        // and where it will come down, marked on the floor: a ring, eight arrowheads pointing in
        AttackFxEntity.spawn(level(), AttackFxEntity.VOLLEY_MARK, rainAt, random.nextFloat() * 360.0F, 1.0F,
                RAIN_WARN + RAIN_FALL, this);
    }

    /** How high is open over (x, from, z), up to `reach`: the underside of the first block in the way, a little below. */
    private double ceiling(double x, double from, double z, double reach) {
        Vec3 a = new Vec3(x, from, z);
        Vec3 b = new Vec3(x, from + reach, z);
        net.minecraft.world.phys.HitResult hit = level().clip(new net.minecraft.world.level.ClipContext(a, b,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? b.y : Math.max(from, hit.getLocation().y - 0.7D);
    }

    private void tickRain() {
        if (rainLeft <= 0 || rainAt == null || !(level() instanceof ServerLevel sl)) {
            return;
        }
        rainLeft--;
        int age = RAIN_WARN + RAIN_FALL - rainLeft;
        if (age == RAIN_WARN - 4) {
            sl.playSound(null, rainAt.x, rainAt.y, rainAt.z, FFSounds.STILLBOW_RAIN.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 1.0F);
        }
        // A VOLLEY: the
        // arrows all loosed together and coming down together, and the ring they fill struck once, as one - not
        // fourteen stragglers two at a time, each finding a body a sixth of the time and the rest lost to its
        // moment of invulnerability
        if (age == RAIN_WARN + 1) {
            double drop = 0.0D;
            for (int k = 0; k < VOLLEY_ARROWS; k++) {
                double a = Math.PI * 2.0D * k / VOLLEY_ARROWS + random.nextDouble() * 0.4D;
                double r = Math.sqrt((k + 0.5D) / VOLLEY_ARROWS) * RAIN_RADIUS;
                IceArrowEntity arrow = new IceArrowEntity(level(), this).volley();
                double ax = rainAt.x + Math.cos(a) * r, az = rainAt.z + Math.sin(a) * r;
                // from under whatever roof is over that spot (at most eleven up), never from inside it
                double top = ceiling(ax, rainAt.y + 1.0D, az, 10.0D);
                drop = Math.max(drop, top - rainAt.y);
                arrow.setPos(ax, top, az);
                arrow.shoot((random.nextDouble() - 0.5D) * 0.04D, -1.0D, (random.nextDouble() - 0.5D) * 0.04D, 2.2F, 0.0F);
                level().addFreshEntity(arrow);
            }
            volleyLands = age + Math.max(1, (int) Math.ceil(drop / 2.2D));
        }
        if (age == volleyLands) {
            sl.playSound(null, rainAt.x, rainAt.y, rainAt.z, net.minecraft.sounds.SoundEvents.ARROW_HIT,
                    net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.7F);
            for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(rainAt, rainAt).inflate(RAIN_RADIUS, 2.5D, RAIN_RADIUS),
                    e -> e.isAlive() && !(e instanceof FrostServantEntity)
                            && e.distanceToSqr(rainAt.x, e.getY(), rainAt.z) <= RAIN_RADIUS * RAIN_RADIUS)) {
                if (v.hurt(damageSources().mobProjectile(this, this), VOLLEY_DAMAGE)) {
                    v.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            com.jastkub.frozenfortress.registry.FFEffects.FROSTBITE.get(), 60, 0), this);
                    v.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                }
            }
        }
        if (rainLeft == 0) {
            rainAt = null;
        }
    }

    /** Out of the cold and elsewhere: seven to twelve blocks away from the threat. */
    void performBlink(LivingEntity threat) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // folded into the cold: a ring of shards closing on where he stood (AttackFxEntity), and on where he comes out
        Vec3 from = position();
        for (int attempt = 0; attempt < 12; attempt++) {
            Vec3 away = position().subtract(threat.position()).normalize();
            double angle = (random.nextDouble() - 0.5D) * Math.PI * 0.9D;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            Vec3 dir = new Vec3(away.x * cos - away.z * sin, 0.0D, away.x * sin + away.z * cos);
            double range = 7.0D + random.nextDouble() * 5.0D;
            if (randomTeleport(getX() + dir.x * range, getY() + 1.0D, getZ() + dir.z * range, false)) {
                playSound(FFSounds.STILLBOW_BLINK.get(), 1.0F, 1.0F);
                AttackFxEntity.spawn(serverLevel, AttackFxEntity.BLINK_VEIL, from, getYRot(), 1.0F, 12, this);
                AttackFxEntity.spawn(serverLevel, AttackFxEntity.BLINK_VEIL, position(), getYRot(), 1.0F, 12, this);
                blinkCooldown = 220;                                 // it can be run down
                return;
            }
        }
        blinkCooldown = 60;
    }

    /** A hop back, still facing them. */
    void hopBack(LivingEntity threat) {
        Vec3 away = new Vec3(getX() - threat.getX(), 0.0D, getZ() - threat.getZ());
        if (away.lengthSqr() < 1.0E-4D) {
            return;
        }
        away = away.normalize();
        setDeltaMovement(away.x * 0.7D, 0.32D, away.z * 0.7D);
        hasImpulse = true;
        backstepCooldown = 70;
    }

    static class HuntsmanGoal extends Goal {
        private final StillbowEntity mob;
        private int cooldown = 20;
        private int pinCooldown;
        private int rainCooldown = 60;
        private int strafeFor;
        private boolean strafeLeft;

        HuntsmanGoal(StillbowEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() != null && mob.getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            mob.setAttackState(0);
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            if (pinCooldown > 0) {
                pinCooldown--;
            }
            if (rainCooldown > 0) {
                rainCooldown--;
            }
            double dist = mob.distanceTo(target);
            int state = mob.getAttackState();
            boolean sighted = mob.hasLineOfSight(target);
            if (state == 0) {
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                if (dist < 3.5D && mob.blinkCooldown <= 0) {
                    mob.setAttackState(BLINK);
                    return;
                }
                if (dist < 5.5D && mob.backstepCooldown <= 0 && mob.onGround()) {
                    mob.setAttackState(BACKSTEP);
                    return;
                }
                keepDistance(target, dist, sighted);
                if (cooldown > 0) {
                    cooldown--;
                    return;
                }
                if (sighted && dist <= 24.0D) {
                    float roll = mob.random.nextFloat();
                    if (roll < 0.25F && rainCooldown <= 0 && dist >= 7.0D) {
                        rainCooldown = 220;
                        begin(RAIN, target);
                    } else if (roll < 0.5F && pinCooldown <= 0) {
                        pinCooldown = 140;
                        begin(PIN, target);
                    } else {
                        begin(SHOOT, target);
                    }
                }
                return;
            }

            mob.getNavigation().stop();
            int t = mob.attackTicks;
            switch (state) {
                case SHOOT -> {
                    if (t < 17) {
                        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    }
                    if (t == 4) {
                        mob.playSound(FFSounds.STILLBOW_DRAW.get(), 1.0F, 1.0F);
                    }
                    if (t == 14) {
                        mob.glint();
                    }
                    if (t == 20) {
                        mob.loose(target, 2.0D, 2.4F, false);
                    }
                    if (t > 26) {
                        finish(26);
                    }
                }
                case PIN -> {
                    if (t < 21) {
                        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    }
                    if (t == 6) {
                        mob.playSound(FFSounds.STILLBOW_DRAW.get(), 1.1F, 0.8F);
                    }
                    if (t == 18) {
                        mob.glint();
                    }
                    if (t == 24) {
                        mob.loose(target, 1.6D, 2.6F, true);
                    }
                    if (t > 30) {
                        finish(30);
                    }
                }
                case RAIN -> {
                    if (t == 6) {
                        mob.playSound(FFSounds.STILLBOW_DRAW.get(), 1.0F, 0.9F);
                    }
                    if (t == 16) {
                        mob.callRain(target);
                    }
                    if (t > 34) {
                        finish(30);
                    }
                }
                case BLINK -> {
                    if (t == 6) {
                        mob.performBlink(target);
                    }
                    if (t > 12) {
                        finish(8);
                    }
                }
                case BACKSTEP -> {
                    mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    if (t == 3) {
                        mob.hopBack(target);
                    }
                    if (t > 12) {
                        finish(4);
                    }
                }
                default -> finish(10);
            }
        }

        /** Out of reach and in sight: back off from the near, close on the far, and sidestep in between. */
        private void keepDistance(LivingEntity target, double dist, boolean sighted) {
            if (dist > 16.0D || !sighted) {
                mob.getNavigation().moveTo(target, 1.0D);
                return;
            }
            if (dist < 9.0D) {
                Vec3 away = mob.position().subtract(target.position()).normalize();
                Vec3 to = mob.position().add(away.scale(6.0D));
                mob.getNavigation().moveTo(to.x, to.y, to.z, 1.1D);
                return;
            }
            if (--strafeFor <= 0) {
                strafeFor = 40 + mob.random.nextInt(40);
                strafeLeft = mob.random.nextBoolean();
            }
            Vec3 to = target.position().subtract(mob.position());
            Vec3 side = new Vec3(-to.z, 0.0D, to.x).normalize().scale(strafeLeft ? 3.0D : -3.0D);
            Vec3 goal = mob.position().add(side);
            mob.getNavigation().moveTo(goal.x, goal.y, goal.z, 0.8D);
        }

        private void begin(int state, LivingEntity target) {
            mob.getNavigation().stop();
            float yaw = (float) (Math.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX()) * (180.0D / Math.PI)) - 90.0F;
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setAttackState(state);
        }

        private void finish(int cd) {
            mob.setAttackState(0);
            cooldown = cd;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            if (isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            switch (getAttackState()) {
                case SHOOT: return state.setAndContinue(SHOOT_ANIM);
                case PIN: return state.setAndContinue(PIN_ANIM);
                case RAIN: return state.setAndContinue(RAIN_ANIM);
                case BLINK: return state.setAndContinue(BLINK_ANIM);
                case BACKSTEP: return state.setAndContinue(BACKSTEP_ANIM);
                default: break;
            }
            // the wander (speed x 0.6) swings his limbs about 0.15, the strafe and the chase 0.27 and more
            return state.setAndContinue(locomotion(state, IDLE, IDLE_SCAN, STROLL, WALK, 0.21F));
        }));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && hurtTime > 0 && getAttackState() == 0 ? state.setAndContinue(HURT) : PlayState.STOP));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return FFSounds.STILLBOW_IDLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.STILLBOW_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.STILLBOW_DEATH.get();
    }
}
