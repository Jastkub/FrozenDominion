package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.entity.projectile.FrostBoltEntity;
import com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import java.util.List;

/**
 * RIMEWEAVER - a priest of the dead court, made over.
 *
 * <p>The one to reach first. It keeps behind the others and works for them: a WARD of rime laid on every
 * servant near it (a shell that soaks a blow or two) and their wounds closed - and against you, three slow bolts
 * that follow you a little, a line of spikes out of the floor, and a RING of cold laid where you stand: when it
 * closes it mends its own kind inside it and drags you down with the heaviest slowness (it binds no more).
 *
 * <p>But a spell is a thing it has to finish. Strike it while it is weaving - bolts, ward or ring - and the spell
 * breaks in its hands, and it reels for a second and a half: that is the answer to it, and the reason to go for
 * it before the knights. Lighter than before (26 health).
 */
public class RimeweaverEntity extends FrostServantEntity {

    public static final int CAST = 1, RITE = 2, SPIKES = 3, CIRCLE = 4, INTERRUPTED = 5;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rimeweaver.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.rimeweaver.walk");
    private static final RawAnimation IDLE_MUTTER = RawAnimation.begin().thenPlay("animation.rimeweaver.idle_mutter");
    private static final RawAnimation CAST_ANIM = RawAnimation.begin().thenPlay("animation.rimeweaver.cast");
    private static final RawAnimation RITE_ANIM = RawAnimation.begin().thenPlay("animation.rimeweaver.rite");
    private static final RawAnimation SPIKES_ANIM = RawAnimation.begin().thenPlay("animation.rimeweaver.spikes");
    private static final RawAnimation CIRCLE_ANIM = RawAnimation.begin().thenPlay("animation.rimeweaver.circle");
    private static final RawAnimation BROKEN_ANIM = RawAnimation.begin().thenPlay("animation.rimeweaver.interrupted");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.rimeweaver.hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("animation.rimeweaver.death");


    public RimeweaverEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 18;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 26.0D)       // (30 before 06.10.2026; 40 before 04.10)
                .add(Attributes.ATTACK_DAMAGE, 3.6D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new WeaverGoal(this));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.5D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected int shatterTick() {
        return 30;
    }

    /** Is it in the middle of weaving something that a blow can break? */
    private boolean weaving() {
        int st = getAttackState();
        int t = attackTicks;
        return (st == CAST && t < 20) || (st == RITE && t < 18) || (st == CIRCLE && t < 14);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean took = super.hurt(source, amount);
        if (took && !level().isClientSide && weaving() && amount >= 2.0F && source.getEntity() instanceof LivingEntity) {
            // THE SPELL BREAKS in its hands
            setAttackState(INTERRUPTED);
            playSound(FFSounds.ICE_SHATTER.get(), 1.0F, 1.5F);
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 1.8D, getZ(), 20, 0.4D, 0.4D, 0.4D, 0.15D);
            }
        }
        return took;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            if (getAttackState() == INTERRUPTED && attackTicks > 30) {
                setAttackState(0);
            }
        }
    }

    // ------------------------------------------------------------------ its spells
    // ALL OF IT HAS A BODY OF ITS OWN:
    // the shuttle (RimeShuttleEntity), the thorns (RimeThornEntity), the ward's plates and the circle
    // (AttackFxEntity) and the binding the circle weaves (RimeBindEntity) - tools/gen_defender_fx.py

    /** A slow shuttle of ice from the stone of the staff, following them a little for the first second. */
    void bolt(LivingEntity target) {
        Vec3 from = position().add(getViewVector(1.0F).scale(1.0D)).add(0.0D, 2.4D, 0.0D);
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(from);
        com.jastkub.frozenfortress.entity.projectile.RimeShuttleEntity b =
                new com.jastkub.frozenfortress.entity.projectile.RimeShuttleEntity(level(), this, to.x, to.y, to.z);
        b.setPos(from.x, from.y, from.z);
        b.setDamage(5.0F);
        b.setSpeed(0.55D);
        b.home(target, 24);
        level().addFreshEntity(b);
        playSound(FFSounds.FROST_BOLT_FIRE.get(), 1.0F, 1.0F + random.nextFloat() * 0.3F);
    }

    void spikeLine(LivingEntity target) {
        Vec3 dir = new Vec3(target.getX() - getX(), 0.0D, target.getZ() - getZ());
        if (dir.lengthSqr() < 1.0E-4D) {
            return;
        }
        dir = dir.normalize();
        for (int i = 2; i <= 9; i++) {
            level().addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.RimeThornEntity(level(), this,
                    getX() + dir.x * i, getY(), getZ() + dir.z * i, 6.0F, 3 + i));
        }
        playSound(FFSounds.ICE_IMPACT.get(), 1.1F, 1.2F);
    }

    /** The ward and the mending, on every servant of the court within ten blocks. */
    void rite() {
        List<FrostServantEntity> court = level().getEntitiesOfClass(FrostServantEntity.class,
                getBoundingBox().inflate(10.0D), e -> e.isAlive());
        for (FrostServantEntity ally : court) {
            if (ally != this) {
                ally.heal(6.0F);
            }
            ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0));     // a shell of rime: two hearts
            // and you see it close on them: six plates of ice turning in round them
            AttackFxEntity.spawn(level(), AttackFxEntity.RIME_WARD, ally.position(), ally.getYRot(),
                    ally.getBbHeight() / 1.9F, 40, this).follow(ally);
        }
        playSound(FFSounds.RIMEWEAVER_WARD.get(), 1.3F, 1.0F);
    }

    boolean courtNeedsIt() {
        return !level().getEntitiesOfClass(FrostServantEntity.class, getBoundingBox().inflate(10.0D),
                e -> e != this && e.isAlive() && (e.getHealth() < e.getMaxHealth() * 0.75F
                        || !e.hasEffect(MobEffects.ABSORPTION))).isEmpty();
    }

    /**
     * THE CIRCLE OF COLD: drawn on the floor round where they stand - a second and a half to get out of it - and
     * then it closes: the hostile ones in it are mended, whoever else is in it is slowed hard for three seconds
     * (AttackFxEntity.snapCircle: the circle is the spell, and goes on even if the weaver falls).
     */
    void layRing(LivingEntity target) {
        AttackFxEntity.spawn(level(), AttackFxEntity.RIME_CIRCLE, target.position(), 0.0F, 1.0F,
                AttackFxEntity.CIRCLE_LIFE, this);
        playSound(FFSounds.RIMEWEAVER_CIRCLE.get(), 1.4F, 1.0F);
    }

    static class WeaverGoal extends Goal {
        private final RimeweaverEntity mob;
        private int cooldown = 30;
        private int riteCooldown = 60;
        private int ringCooldown = 80;

        WeaverGoal(RimeweaverEntity mob) {
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
            if (riteCooldown > 0) {
                riteCooldown--;
            }
            if (ringCooldown > 0) {
                ringCooldown--;
            }
            double dist = mob.distanceTo(target);
            int state = mob.getAttackState();
            if (state == INTERRUPTED) {
                mob.getNavigation().stop();
                return;
            }
            if (state == 0) {
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                // it keeps behind the fight: back from anyone close, in only to cast
                if (dist < 6.0D) {
                    Vec3 away = mob.position().subtract(target.position()).normalize();
                    Vec3 to = mob.position().add(away.scale(6.0D));
                    mob.getNavigation().moveTo(to.x, to.y, to.z, 1.15D);
                } else if (dist > 14.0D || !mob.hasLineOfSight(target)) {
                    mob.getNavigation().moveTo(target, 0.9D);
                } else {
                    mob.getNavigation().stop();
                }
                if (cooldown > 0) {
                    cooldown--;
                    return;
                }
                if (riteCooldown <= 0 && mob.courtNeedsIt()) {
                    riteCooldown = 300;
                    begin(RITE, target);
                } else if (ringCooldown <= 0 && dist < 16.0D && mob.hasLineOfSight(target)) {
                    ringCooldown = 200;
                    begin(CIRCLE, target);
                } else if (dist < 9.0D && mob.random.nextFloat() < 0.4F) {
                    begin(SPIKES, target);
                } else if (mob.hasLineOfSight(target)) {
                    begin(CAST, target);
                }
                return;
            }

            mob.getNavigation().stop();
            int t = mob.attackTicks;
            switch (state) {
                case CAST -> {
                    mob.getLookControl().setLookAt(target, 20.0F, 20.0F);
                    if (t == 4) {
                        mob.playSound(FFSounds.RIMEWEAVER_CAST.get(), 1.0F, 1.0F + mob.random.nextFloat() * 0.2F);
                    }
                    if (t == 12 || t == 16 || t == 20) {
                        mob.bolt(target);
                    }
                    if (t > 28) {
                        finish(40);
                    }
                }
                case RITE -> {
                    if (t == 6) {
                        mob.playSound(FFSounds.RIMEWEAVER_CAST.get(), 1.0F, 0.8F);
                    }
                    if (t == 18) {
                        mob.rite();
                    }
                    if (t > 30) {
                        finish(30);
                    }
                }
                case SPIKES -> {
                    mob.getLookControl().setLookAt(target, 20.0F, 20.0F);
                    if (t == 12) {
                        mob.spikeLine(target);
                    }
                    if (t > 24) {
                        finish(50);
                    }
                }
                case CIRCLE -> {
                    mob.getLookControl().setLookAt(target, 20.0F, 20.0F);
                    if (t == 14) {
                        mob.layRing(target);
                    }
                    if (t > 30) {
                        finish(30);
                    }
                }
                default -> finish(10);
            }
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
                case CAST: return state.setAndContinue(CAST_ANIM);
                case RITE: return state.setAndContinue(RITE_ANIM);
                case SPIKES: return state.setAndContinue(SPIKES_ANIM);
                case CIRCLE: return state.setAndContinue(CIRCLE_ANIM);
                case INTERRUPTED: return state.setAndContinue(BROKEN_ANIM);
                default: break;
            }
            // (no stroll: the robe hides his feet - but the walk now plays at the wander too, where he used to
            // glide along in his idle)
            return state.setAndContinue(locomotion(state, IDLE, IDLE_MUTTER, null, WALK, 0.0F));
        }));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && hurtTime > 0 && getAttackState() == 0 ? state.setAndContinue(HURT) : PlayState.STOP));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return FFSounds.RIMEWEAVER_IDLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.RIMEWEAVER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.RIMEWEAVER_DEATH.get();
    }
}
