package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;

import java.util.EnumSet;
import java.util.List;

/**
 * STRAZNIK - the Frostbound Sentinel, made over.
 *
 * <p>A knight behind a tower shield. What makes him a different fight from a sword with legs:
 * <ul>
 *   <li><b>THE SHIELD.</b> From the front, while he is not swinging, it takes most of a blow and every arrow.
 *   Each blow it takes fills his guard; fill it and the guard BREAKS - the shield flung wide, two seconds of him
 *   reeling and open. Or go round him: from the side and behind he is plate, nothing more.</li>
 *   <li><b>THE PARRY</b> (08.10.2026): a shield of your own raised just as one of his blows comes turns it aside
 *   whole, and he is STUNNED: dazed for STUN_TICKS, the stars going round over his helm.</li>
 *   <li><b>THE BLOWS</b>, every one told before it lands: two cuts (8, 16), the shield driven out (9), the long
 *   lunge - crouched, the point on you for half a second, then three blocks in a straight line, which a sidestep
 *   or a roll leaves behind (15) - and the sword brought down two-handed through the floor (14).</li>
 *   <li><b>THE LINE</b>: two or more of them after one quarry form up
 *   abreast, shields raised, and advance at a walk; the line breaks into blows a few blocks off.</li>
 * </ul>
 * Lighter than before (30 health, 7 a blow): the shield is the difficulty now, not the numbers.
 */
public class FrostboundSentinelEntity extends FrostServantEntity {

    public static final int SLASH = 1, BASH = 2, GUARD = 3, CRUSH = 4, LUNGE = 5, STAGGERED = 6, STUNNED = 7;

    /**
     * THE STUN: a blow of his parried leaves him dazed this long - no blows, no shield, the ring of stars
     * going round over his helm (the stun_stars bone of his model). A guard worn through by blows only staggers him.
     */
    public static final int STUN_TICKS = 36;
    /**
     * THE PARRY: the blow lands on a shield raised no more than this many ticks before - the moment of his swing (his
     * cuts are heard five ticks before they land, his lunge glints, his crush and his bash are wound up). A shield
     * held up from long before only blocks, as any shield does.
     */
    public static final int PARRY_TICKS = 12;
    /** Client: the stars stay drawn this many ticks past the stun, while the blend out of it shrinks them away. */
    private static final int STARS_LINGER = 5;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.sentinel.idle");
    private static final RawAnimation IDLE_LOOK = RawAnimation.begin().thenPlay("animation.sentinel.idle_look");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.sentinel.walk");
    private static final RawAnimation STROLL = RawAnimation.begin().thenLoop("animation.sentinel.stroll");
    // the blow once - PLAY_ONCE, or its hold_on_last_frame would hold it there for good - then the daze, round and round
    private static final RawAnimation STUN_ANIM = RawAnimation.begin()
            .then("animation.sentinel.stun_hit", software.bernie.geckolib.animation.Animation.LoopType.PLAY_ONCE)
            .thenLoop("animation.sentinel.stunned");
    private static final RawAnimation STARS = RawAnimation.begin().thenLoop("animation.sentinel.stunned_stars");
    private static final RawAnimation MARCH_ANIM = RawAnimation.begin().thenLoop("animation.sentinel.march");
    private static final RawAnimation SLASH_ANIM = RawAnimation.begin().thenPlay("animation.sentinel.slash");
    private static final RawAnimation BASH_ANIM = RawAnimation.begin().thenPlay("animation.sentinel.bash");
    private static final RawAnimation GUARD_ANIM = RawAnimation.begin().thenLoop("animation.sentinel.guard");
    private static final RawAnimation CRUSH_ANIM = RawAnimation.begin().thenPlay("animation.sentinel.crush");
    private static final RawAnimation LUNGE_ANIM = RawAnimation.begin().thenPlay("animation.sentinel.lunge");
    private static final RawAnimation STAGGER_ANIM = RawAnimation.begin().thenPlay("animation.sentinel.staggered");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.sentinel.hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("animation.sentinel.death");

    /** In the line: shield up, walking - the animation's and the shield's business both. */
    private static final EntityDataAccessor<Boolean> MARCHING =
            SynchedEntityData.defineId(FrostboundSentinelEntity.class, EntityDataSerializers.BOOLEAN);

    /** What the shield has taken since it last had a rest; at GUARD_BREAK it gives. */
    private float guardLoad;
    private static final float GUARD_BREAK = 14.0F;
    private int sinceBlock;
    /** Client: ticks the stun's stars are still to be drawn (STARS_LINGER past the stun itself). */
    private int starsFor;

    public FrostboundSentinelEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)       // (34 before 06.10.2026; 48 before 04.10)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)     // (8.5)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 1.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MARCHING, false);
    }

    public boolean isMarching() {
        return entityData.get(MARCHING);
    }

    void setMarching(boolean on) {
        if (on != isMarching()) {
            entityData.set(MARCHING, on);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new SentinelGoal(this));
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

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            // the guard recovers when nothing has struck it for two seconds
            if (++sinceBlock > 40 && guardLoad > 0.0F) {
                guardLoad = Math.max(0.0F, guardLoad - 0.25F);
            }
            if (getAttackState() == GUARD && attackTicks > 40) {
                setAttackState(0);
            }
            if (getAttackState() == STAGGERED && attackTicks > 40) {
                setAttackState(0);
            }
            if (getAttackState() == STUNNED && attackTicks > STUN_TICKS) {
                setAttackState(0);
            }
        } else {
            starsFor = getAttackState() == STUNNED ? STARS_LINGER : Math.max(0, starsFor - 1);
        }
    }

    /** Client: whether the ring of stars over his helm is drawn (FrostboundSentinelRenderer, and its controller). */
    public boolean showsStars() {
        return starsFor > 0 || getAttackState() == STUNNED;
    }

    /** The shield is up: not mid-swing, not reeling. */
    private boolean shieldReady() {
        int st = getAttackState();
        return st == 0 || st == GUARD || st == BASH;
    }

    /** Is this point before his face - inside the shield's arc? */
    private boolean inFront(Vec3 from) {
        Vec3 look = Vec3.directionFromRotation(0.0F, yBodyRot);
        Vec3 to = new Vec3(from.x - getX(), 0.0D, from.z - getZ());
        return to.lengthSqr() > 1.0E-4D && look.dot(to.normalize()) > 0.26D;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && shieldReady() && source.getSourcePosition() != null
                && inFront(source.getSourcePosition()) && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)) {
            Entity direct = source.getDirectEntity();
            guardLoad += amount;
            sinceBlock = 0;
            if (guardLoad >= GUARD_BREAK) {
                breakGuard();
                return super.hurt(source, amount);         // the blow that broke it lands whole
            }
            blockFeedback(source.getSourcePosition());
            if (direct instanceof net.minecraft.world.entity.projectile.Projectile) {
                return false;                               // turned aside by the shield
            }
            return super.hurt(source, amount * 0.2F);
        }
        return super.hurt(source, amount);
    }

    private void blockFeedback(Vec3 from) {
        playSound(FFSounds.SENTINEL_BLOCK.get(), 1.2F, 0.9F + random.nextFloat() * 0.2F);
        if (level() instanceof ServerLevel sl) {
            Vec3 at = position().add(from.subtract(position()).normalize().scale(0.9D)).add(0.0D, 1.3D, 0.0D);
            sl.sendParticles(FFParticles.ICE_SHARD.get(), at.x, at.y, at.z, 8, 0.2D, 0.3D, 0.2D, 0.12D);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.2D, 0.3D, 0.2D, 0.2D);
        }
    }

    /** The guard gives: he reels. */
    private void breakGuard() {
        guardLoad = 0.0F;
        setAttackState(STAGGERED);
        setMarching(false);
        getNavigation().stop();
        playSound(FFSounds.SENTINEL_GUARD_BREAK.get(), 1.5F, 1.0F);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 1.4D, getZ(), 24, 0.5D, 0.5D, 0.5D, 0.2D);
        }
    }

    /**
     * Is this blow of his parried by `v`: a shield of theirs raised within PARRY_TICKS, and turned toward him (the
     * game's own test of a shield's arc)?
     */
    private boolean parried(LivingEntity v) {
        if (!(v instanceof Player p) || !p.isUsingItem() || p.getTicksUsingItem() > PARRY_TICKS
                || !p.getUseItem().canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK)) {
            return false;
        }
        Vec3 view = p.getViewVector(1.0F);
        Vec3 toMe = position().vectorTo(p.position()).normalize();
        return new Vec3(toMe.x, 0.0D, toMe.z).dot(view) < 0.0D;
    }

    /**
     * Parried: the blow is turned aside whole - the ring of the shield, the bell in his helm and the stars coming
     * out - and he is stunned. False if `v` did not parry it (it lands as it would).
     */
    private boolean parry(LivingEntity v) {
        if (!parried(v)) {
            return false;
        }
        guardLoad = 0.0F;
        setAttackState(STUNNED);
        setMarching(false);
        getNavigation().stop();
        v.playSound(net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, 1.2F, 0.8F + random.nextFloat() * 0.2F);
        playSound(FFSounds.SENTINEL_GUARD_BREAK.get(), 1.5F, 1.1F);
        playSound(net.minecraft.sounds.SoundEvents.BELL_BLOCK, 0.8F, 1.9F);
        playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 1.5F);
        Vec3 away = v.position().vectorTo(position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() > 1.0E-4D) {
            away = away.normalize().scale(0.5D);
            push(away.x, 0.1D, away.z);                                    // thrown back a step by it
            hurtMarked = true;
        }
        if (level() instanceof ServerLevel sl) {
            Vec3 at = v.position().add(0.0D, 1.2D, 0.0D).add(away.scale(1.2D));
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.2D, 0.3D, 0.2D, 0.3D);
        }
        return true;
    }

    // ------------------------------------------------------------------ the blows
    private List<LivingEntity> foes(double r) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r),
                e -> e != this && e.isAlive() && !(e instanceof FrostServantEntity) && !(e instanceof Player p && p.isSpectator()));
    }

    /** Who is before him, within reach, inside the cone - the swing's arc. */
    void swing(double reach, double cosArc, float damage) {
        Vec3 look = getViewVector(1.0F);
        for (LivingEntity v : foes(reach)) {
            Vec3 to = v.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            if (flat.length() <= reach && (flat.lengthSqr() < 0.5D || look.dot(flat.normalize()) > cosArc)) {
                if (parry(v)) {
                    return;
                }
                v.hurt(damageSources().mobAttack(this), damage);
            }
        }
    }

    void bashHit() {
        Vec3 look = getViewVector(1.0F);
        for (LivingEntity v : foes(3.0D)) {
            Vec3 to = v.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            if (flat.length() <= 3.0D && look.dot(flat.normalize()) > 0.4D) {
                if (parry(v)) {
                    return;
                }
                v.hurt(damageSources().mobAttack(this), 4.0F);
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 3), this);
                Vec3 push = flat.normalize().scale(1.4D);
                v.push(push.x, 0.35D, push.z);
                v.hurtMarked = true;
            }
        }
        playSound(FFSounds.SENTINEL_SHIELD.get(), 1.3F, 0.85F);
    }

    /** The lunge's point: a narrow line before him - three blocks long, the width of a man to either side. */
    void lungeHit() {
        Vec3 look = getViewVector(1.0F);
        Vec3 flatLook = new Vec3(look.x, 0.0D, look.z).normalize();
        for (LivingEntity v : foes(3.6D)) {
            Vec3 to = v.position().subtract(position());
            double along = to.x * flatLook.x + to.z * flatLook.z;
            double aside = Math.abs(to.x * flatLook.z - to.z * flatLook.x);
            if (along > -0.3D && along < 3.2D && aside < 0.9D) {
                if (parry(v)) {
                    setDeltaMovement(Vec3.ZERO);                       // the lunge stops dead on it
                    return;
                }
                v.hurt(damageSources().mobAttack(this), 8.0F);
                v.push(flatLook.x * 0.8D, 0.25D, flatLook.z * 0.8D);
                v.hurtMarked = true;
            }
        }
    }

    void crushHit() {
        Vec3 look = getViewVector(1.0F);
        Vec3 at = position().add(look.x * 2.2D, 0.0D, look.z * 2.2D);
        for (LivingEntity v : foes(4.6D)) {
            if (v.position().distanceTo(at) < 2.6D) {
                if (parry(v)) {
                    return;
                }
                v.hurt(damageSources().mobAttack(this), 9.5F);
                v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 60, 0), this);
            }
        }
        playSound(FFSounds.ICE_IMPACT.get(), 1.4F, 0.9F);
        // where the sword went in, the floor bursts: slabs of ice up in a fan along his line (AttackFxEntity)
        AttackFxEntity.spawn(level(), AttackFxEntity.CRUSH_BURST, position().add(look.x * 0.8D, 0.0D, look.z * 0.8D),
                getYRot(), 1.3F, 30, this);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(FFParticles.ICE_SHARD.get(), at.x, getY() + 0.2D, at.z, 12, 0.9D, 0.1D, 0.9D, 0.16D);
        }
    }

    /** The others of his kind on the same quarry, near enough to stand in his line. */
    List<FrostboundSentinelEntity> line(LivingEntity target) {
        return level().getEntitiesOfClass(FrostboundSentinelEntity.class, getBoundingBox().inflate(12.0D),
                s -> s != this && s.isAlive() && s.getTarget() == target && s.getAttackState() != STAGGERED
                        && s.getAttackState() != STUNNED);
    }

    /** The line goes: shields up and abreast, toward them. */
    static class SentinelGoal extends Goal {
        private final FrostboundSentinelEntity mob;
        private int cooldown = 10;
        private int lungeCooldown;
        private int last;

        SentinelGoal(FrostboundSentinelEntity mob) {
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
            mob.setMarching(false);
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            if (lungeCooldown > 0) {
                lungeCooldown--;
            }
            double dist = mob.distanceTo(target);
            int state = mob.getAttackState();

            if (state == STAGGERED || state == STUNNED) {
                mob.getNavigation().stop();
                return;
            }
            if (state == GUARD) {
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                mob.getNavigation().moveTo(target, 0.5D);
                return;
            }
            if (state == 0) {
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                advance(target, dist);
                if (cooldown > 0) {
                    cooldown--;
                    return;
                }
                if (dist < 3.3D) {
                    float roll = mob.random.nextFloat();
                    int pick = roll < 0.45F ? SLASH : (roll < 0.72F ? BASH : CRUSH);
                    if (pick == last && pick == CRUSH) {
                        pick = SLASH;
                    }
                    begin(pick, target);
                } else if (dist > 4.5D && dist < 8.0D && lungeCooldown <= 0 && mob.hasLineOfSight(target)) {
                    lungeCooldown = 100;
                    begin(LUNGE, target);
                }
                return;
            }

            mob.getNavigation().stop();
            int t = mob.attackTicks;
            switch (state) {
                case SLASH -> {
                    mob.getLookControl().setLookAt(target, 12.0F, 12.0F);
                    if (t == 3 || t == 12) {
                        mob.playSound(FFSounds.SENTINEL_SWING.get(), 1.0F, t == 3 ? 0.95F : 1.1F);
                    }
                    if (t == 8 || t == 16) {
                        mob.swing(3.6D, 0.3D, (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE));
                    }
                    if (t > 26) {
                        finish(14);
                    }
                }
                case BASH -> {
                    mob.getLookControl().setLookAt(target, 20.0F, 20.0F);
                    if (t == 9) {
                        mob.bashHit();
                    }
                    if (t > 18) {
                        finish(22);
                    }
                }
                case LUNGE -> {
                    // the tell: still, the point on them; the turn locks at 10, and what is not in the line at 15 lives
                    if (t < 10) {
                        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    }
                    if (t == 6) {
                        mob.playSound(FFSounds.CRYSTAL_CHIME.get(), 1.0F, 1.5F);
                        // the glint on the point: a star of light (AttackFxEntity), not a puff of motes
                        Vec3 tip = mob.position().add(mob.getViewVector(1.0F).scale(1.6D)).add(0.0D, 1.3D, 0.0D);
                        AttackFxEntity.spawn(mob.level(), AttackFxEntity.GLINT, tip, 0.0F, 0.8F, 8, mob);
                    }
                    if (t >= 13 && t <= 16) {
                        Vec3 look = mob.getViewVector(1.0F);
                        mob.setDeltaMovement(look.x * 0.85D, mob.getDeltaMovement().y, look.z * 0.85D);
                        mob.hasImpulse = true;
                    }
                    if (t == 13) {
                        mob.playSound(FFSounds.SENTINEL_SWING.get(), 1.2F, 0.75F);
                    }
                    if (t == 15) {
                        mob.lungeHit();
                    }
                    if (t > 30) {
                        finish(18);
                    }
                }
                case CRUSH -> {
                    if (t < 10) {
                        mob.getLookControl().setLookAt(target, 20.0F, 20.0F);
                    }
                    if (t == 4) {
                        mob.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(), 1.2F, 0.6F);
                    }
                    // (the cold gathering on the raised blade is the model's: its rime grows on the sword - the
                    // renderer shows the sword_rime bone only in this attack)
                    if (t == 14) {
                        mob.crushHit();
                    }
                    if (t > 30) {
                        finish(30);
                    }
                }
                default -> finish(10);
            }
        }

        /** Toward them - alone, straight in; with others of the line, abreast and shields up. */
        private void advance(LivingEntity target, double dist) {
            List<FrostboundSentinelEntity> line = mob.line(target);
            if (line.isEmpty() || dist < 4.5D) {
                mob.setMarching(false);
                mob.getNavigation().moveTo(target, 1.0D);
                return;
            }
            mob.setMarching(true);
            // the line stands square to the way to them, about its own middle; each takes his place in it
            Vec3 mid = mob.position();
            for (FrostboundSentinelEntity s : line) {
                mid = mid.add(s.position());
            }
            mid = mid.scale(1.0D / (line.size() + 1));
            Vec3 dir = new Vec3(target.getX() - mid.x, 0.0D, target.getZ() - mid.z);
            if (dir.lengthSqr() < 1.0E-4D) {
                mob.getNavigation().moveTo(target, 0.7D);
                return;
            }
            dir = dir.normalize();
            Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
            int rank = 0;
            for (FrostboundSentinelEntity s : line) {
                double a = (s.getX() - mid.x) * side.x + (s.getZ() - mid.z) * side.z;
                double me = (mob.getX() - mid.x) * side.x + (mob.getZ() - mid.z) * side.z;
                if (a < me || (a == me && s.getId() < mob.getId())) {
                    rank++;
                }
            }
            double offset = (rank - line.size() / 2.0D) * 2.2D;
            double ahead = (mob.getX() - mid.x) * dir.x + (mob.getZ() - mid.z) * dir.z;
            // no one runs out ahead of the line
            double speed = ahead > 1.5D ? 0.35D : 0.75D;
            Vec3 goal = target.position().add(side.scale(offset)).subtract(dir.scale(2.5D));
            mob.getNavigation().moveTo(goal.x, goal.y, goal.z, speed);
        }

        private void begin(int state, LivingEntity target) {
            last = state;
            mob.setMarching(false);
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
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            switch (getAttackState()) {
                case SLASH: return state.setAndContinue(SLASH_ANIM);
                case BASH: return state.setAndContinue(BASH_ANIM);
                case GUARD: return state.setAndContinue(GUARD_ANIM);
                case CRUSH: return state.setAndContinue(CRUSH_ANIM);
                case LUNGE: return state.setAndContinue(LUNGE_ANIM);
                case STAGGERED: return state.setAndContinue(STAGGER_ANIM);
                case STUNNED: return state.setAndContinue(STUN_ANIM);
                default: break;
            }
            if (isMarching()) {
                return state.setAndContinue(MARCH_ANIM);
            }
            // the wander (speed x 0.5) swings his limbs about 0.07, the chase about 0.3
            return state.setAndContinue(locomotion(state, IDLE, IDLE_LOOK, STROLL, WALK, 0.17F));
        }));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && hurtTime > 0 && getAttackState() == 0 ? state.setAndContinue(HURT) : PlayState.STOP));
        // the stars go round on a controller of their own, so their round does not stop at the seam between the
        // blow and the daze (the main controller's clips only show and hide them, by scale)
        controllers.add(new AnimationController<>(this, "stars", 0, state ->
                !isDeadOrDying() && showsStars() ? state.setAndContinue(STARS) : PlayState.STOP));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("GuardLoad", guardLoad);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        guardLoad = tag.getFloat("GuardLoad");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return FFSounds.SENTINEL_IDLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.SENTINEL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.SENTINEL_DEATH.get();
    }
}
