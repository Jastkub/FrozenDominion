package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * FROSTMAW - the kennels' hounds grown wolf-sized and more, made over.
 *
 * <p>ONE AT A TIME, AND THE REST CIRCLE. A pack on one quarry gives the attack to one of its number - the turn
 * passes every few seconds - and the others prowl round you at five or six blocks, low, waiting. Turn your back
 * on one of the circle and it may take the chance: a pounce from the flank. One of them HOWLS once in a while
 * and the whole pack quickens. A badly hurt one breaks off and runs back to the others before it comes again.
 *
 * <p>Its blows: the bite (6), frostbite that deepens bite on bite; the pounce - told by the crouch and the growl
 * for half a second, the leap aimed where you were when it left the ground, so a step aside lets it sail by;
 * and the breath, a short cone of cold. Lighter than before (34 health, 6.5 a bite).
 */
public class FrostmawEntity extends FrostServantEntity {

    public static final int BITE = 1, POUNCE = 2, BREATH = 3, HOWL = 4;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.frostmaw.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.frostmaw.walk");
    private static final RawAnimation STROLL = RawAnimation.begin().thenLoop("animation.frostmaw.stroll");
    private static final RawAnimation IDLE_SNIFF = RawAnimation.begin().thenPlay("animation.frostmaw.idle_sniff");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.frostmaw.run");
    private static final RawAnimation PROWL = RawAnimation.begin().thenLoop("animation.frostmaw.prowl");
    private static final RawAnimation BITE_ANIM = RawAnimation.begin().thenPlay("animation.frostmaw.bite");
    private static final RawAnimation POUNCE_ANIM = RawAnimation.begin().thenPlay("animation.frostmaw.pounce");
    private static final RawAnimation BREATH_ANIM = RawAnimation.begin().thenPlay("animation.frostmaw.breath");
    private static final RawAnimation HOWL_ANIM = RawAnimation.begin().thenPlay("animation.frostmaw.howl");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.frostmaw.hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("animation.frostmaw.death");

    /** 0 walking, 1 prowling the circle, 2 running (the chase, the retreat). */
    private static final EntityDataAccessor<Integer> GAIT =
            SynchedEntityData.defineId(FrostmawEntity.class, EntityDataSerializers.INT);

    private int retreatFor;
    private boolean retreated;
    long howledAt = -10000L;
    /** Its rider thrown from it (FrostRiderEntity): faster and harder from then on. */
    private boolean enraged;
    private static final net.minecraft.resources.ResourceLocation RAGE_SPEED =
            com.jastkub.frozenfortress.FrozenFortress.id("frostmaw_rage_speed");
    private static final net.minecraft.resources.ResourceLocation RAGE_BITE =
            com.jastkub.frozenfortress.FrozenFortress.id("frostmaw_rage_bite");
    /** Laid in a structure with "Ridden" (1.21: finalizeSpawn has no tag - the entity's own, read before it). */
    private boolean riddenTag;

    public FrostmawEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 34.0D)       // (40 before 06.10.2026; 55 before 04.10)
                .add(Attributes.ATTACK_DAMAGE, 6.5D)     // (7.6)
                .add(Attributes.MOVEMENT_SPEED, 0.33D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(GAIT, 0);
    }

    int gait() {
        return entityData.get(GAIT);
    }

    void setGait(int g) {
        if (g != gait()) {
            entityData.set(GAIT, g);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new PackGoal(this));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected int shatterTick() {
        return 30;
    }

    // ------------------------------------------------------------------ the Frost Rider
    /** Its rider's: in the saddle, Frostmaw-borne (FrostRiderEntity). */
    @javax.annotation.Nullable
    public FrostRiderEntity rider() {
        return getFirstPassenger() instanceof FrostRiderEntity r ? r : null;
    }

    /** The hound leads, not its rider: its own pack's ways, kept at a throwing distance (PackGoal). */
    @Override
    @javax.annotation.Nullable
    public LivingEntity getControllingPassenger() {
        return null;
    }

    /** The seat of its saddle (gen_defenders: the pad's top, 19.8 units up). */
    @Override
    protected net.minecraft.world.phys.Vec3 getPassengerAttachmentPoint(net.minecraft.world.entity.Entity passenger,
                                                                       net.minecraft.world.entity.EntityDimensions dims,
                                                                       float scale) {
        return new net.minecraft.world.phys.Vec3(0.0D, 19.8D / 16.0D * scale, 0.0D);
    }

    /**
     * WHERE IT COMES FROM WITH A RIDER: out of a statue or a nest, one in RIDDEN_CHANCE; laid in a structure with the tag
     * "Ridden", always.
     */
    public static final float RIDDEN_CHANCE = 0.15F;

    @Override
    @javax.annotation.Nullable
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
                                                                  net.minecraft.world.DifficultyInstance difficulty,
                                                                  net.minecraft.world.entity.MobSpawnType reason,
                                                                  @javax.annotation.Nullable net.minecraft.world.entity.SpawnGroupData data) {
        data = super.finalizeSpawn(level, difficulty, reason, data);
        boolean laid = reason == net.minecraft.world.entity.MobSpawnType.STRUCTURE && riddenTag;
        boolean rolled = (reason == net.minecraft.world.entity.MobSpawnType.TRIGGERED
                || reason == net.minecraft.world.entity.MobSpawnType.SPAWNER) && random.nextFloat() < RIDDEN_CHANCE;
        if ((laid || rolled) && !isVehicle()) {
            FrostRiderEntity.mount(level, this);
        }
        return data;
    }

    /** Down: its rider off its back at once, not riding the dying beast till it shatters. */
    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            ejectPassengers();
        }
        super.die(source);
    }

    /** Its rider thrown: it goes mad - faster, its bite harder, and a howl to say so. */
    public void enrage() {
        if (enraged || level().isClientSide || !isAlive()) {
            return;
        }
        enraged = true;
        net.minecraft.world.entity.ai.attributes.AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(RAGE_SPEED) == null) {
            speed.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(RAGE_SPEED,
                    0.35D, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance bite = getAttribute(Attributes.ATTACK_DAMAGE);
        if (bite != null && bite.getModifier(RAGE_BITE) == null) {
            bite.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(RAGE_BITE,
                    2.5D, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
        retreatFor = 0;
        retreated = true;                                         // no more running back to the pack
        playSound(FFSounds.FROSTMAW_HOWL.get(), 2.2F, 0.8F);
        playSound(FFSounds.FROSTMAW_GROWL.get(), 1.6F, 0.7F);
    }

    public boolean isEnraged() {
        return enraged;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Enraged", enraged);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        enraged = tag.getBoolean("Enraged");
        riddenTag = tag.getBoolean("Ridden");
    }

    /** The pack on one quarry: itself and the others hunting it within sixteen blocks. */
    List<FrostmawEntity> pack(LivingEntity target) {
        List<FrostmawEntity> out = level().getEntitiesOfClass(FrostmawEntity.class, getBoundingBox().inflate(16.0D),
                m -> m.isAlive() && m.getTarget() == target);
        if (!out.contains(this)) {
            out.add(this);
        }
        return out;
    }

    /** Whose turn it is to go in: they take it in turns, five seconds each; a retreating one gives it up. */
    boolean myTurn(LivingEntity target) {
        List<FrostmawEntity> pack = pack(target);
        pack.removeIf(m -> m.retreatFor > 0);
        if (pack.size() <= 1) {
            return true;
        }
        pack.sort(Comparator.comparingInt(m -> m.getId()));
        int turn = (int) ((level().getGameTime() / 100L) % pack.size());
        return pack.get(turn) == this;
    }

    void bite(LivingEntity target) {
        if (distanceTo(target) < 3.0D) {
            doHurtTarget(target);
            MobEffectInstance had = target.getEffect(FFEffects.FROSTBITE);
            int amp = had == null ? 0 : Math.min(1, had.getAmplifier() + 1);         // it deepens
            target.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, amp), this);
        }
        playSound(FFSounds.FROSTMAW_BITE.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** The leap: aimed where they stand as it leaves the ground, and no steering after. */
    void leap(LivingEntity target) {
        Vec3 to = target.position().subtract(position());
        Vec3 flat = new Vec3(to.x, 0.0D, to.z);
        double d = flat.length();
        if (d < 1.0E-3D) {
            return;
        }
        double power = Math.min(1.5D, 0.45D + d * 0.11D);
        Vec3 dir = flat.normalize();
        setDeltaMovement(dir.x * power, 0.5D, dir.z * power);
        hasImpulse = true;
    }

    void breathe() {
        // (what you see of it is geometry, drawn off its head - FrostmawBreathLayer; this is only what it does)
        Vec3 look = getViewVector(1.0F);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4.5D),
                e -> e != this && !(e instanceof FrostServantEntity))) {
            Vec3 to = victim.position().subtract(position()).normalize();
            if (look.dot(to) > 0.6D && distanceTo(victim) < 5.5D) {
                victim.hurt(damageSources().mobAttack(this), 2.5F);
                victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 0), this);
            }
        }
    }

    /** Client: ticks into its breath - FrostmawBreathLayer draws it, and needs the time. */
    private int breathClient;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            breathClient = getAttackState() == BREATH ? breathClient + 1 : 0;
        }
    }

    /** 0..1: how much of the breath is coming out (the clip breathes from 10 to 42). */
    public float breathPower(float partialTick) {
        float t = breathClient + partialTick;
        if (getAttackState() != BREATH || t < 9.0F) {
            return 0.0F;
        }
        return net.minecraft.util.Mth.clamp((t - 9.0F) / 4.0F, 0.0F, 1.0F)
                * net.minecraft.util.Mth.clamp((45.0F - t) / 5.0F, 0.0F, 1.0F);
    }

    /** 0..1: how far out it has reached yet. */
    public float breathReach(float partialTick) {
        return net.minecraft.util.Mth.clamp((breathClient + partialTick - 9.0F) / 6.0F, 0.0F, 1.0F);
    }

    void howl(LivingEntity target) {
        howledAt = level().getGameTime();
        for (FrostmawEntity m : pack(target)) {
            m.howledAt = howledAt;
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, 0));
        }
        playSound(FFSounds.FROSTMAW_HOWL.get(), 2.0F, 0.95F + random.nextFloat() * 0.1F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean took = super.hurt(source, amount);
        // badly hurt, once: it breaks off and runs to the pack
        if (took && !level().isClientSide && !retreated && isAlive() && getHealth() < getMaxHealth() * 0.3F) {
            retreated = true;
            retreatFor = 70;
            setAttackState(0);
        }
        return took;
    }

    static class PackGoal extends Goal {
        private final FrostmawEntity mob;
        private int cooldown = 10;
        private int pounceCooldown = 40;
        private int breathCooldown = 120;
        private boolean launched;
        private double orbit;

        PackGoal(FrostmawEntity mob) {
            this.mob = mob;
            this.orbit = mob.random.nextDouble() * Math.PI * 2.0D;
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
            mob.setGait(0);
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            if (pounceCooldown > 0) {
                pounceCooldown--;
            }
            if (breathCooldown > 0) {
                breathCooldown--;
            }
            double dist = mob.distanceTo(target);
            int state = mob.getAttackState();

            if (state == 0 && mob.rider() != null) {
                // ---- A RIDER ON ITS BACK: it keeps them at a throw - round them, seven or eight blocks off -
                // and only bites what comes in close
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                if (dist < 3.4D && cooldown <= 0) {
                    begin(BITE, target);
                    return;
                }
                if (cooldown > 0) {
                    cooldown--;
                }
                orbit += dist < 5.0D ? 0.02D : 0.035D;
                double r = 7.5D;
                Vec3 spot = target.position().add(Math.cos(orbit) * r, 0.0D, Math.sin(orbit) * r);
                boolean far = dist > 11.0D;
                mob.setGait(far ? 2 : 1);
                mob.getNavigation().moveTo(spot.x, spot.y, spot.z, far ? 1.25D : (dist < 5.0D ? 1.15D : 0.95D));
                return;
            }
            if (state == 0) {
                // ---- RUNNING BACK TO THE PACK
                if (mob.retreatFor > 0) {
                    mob.retreatFor--;
                    mob.setGait(2);
                    Vec3 away = mob.position().subtract(target.position());
                    away = new Vec3(away.x, 0.0D, away.z);
                    Vec3 to = mob.position().add(away.lengthSqr() > 1.0E-4D ? away.normalize().scale(8.0D) : Vec3.ZERO);
                    mob.getNavigation().moveTo(to.x, to.y, to.z, 1.4D);
                    return;
                }
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                List<FrostmawEntity> pack = mob.pack(target);
                // ---- THE HOWL, now and then, when there is a pack to hear it
                if (pack.size() >= 2 && mob.level().getGameTime() - mob.howledAt > 600 && mob.random.nextInt(80) == 0) {
                    begin(HOWL, target);
                    return;
                }
                boolean turn = mob.myTurn(target);
                if (!turn && dist < 12.0D) {
                    // ---- THE CIRCLE: low, round them, five or six blocks off
                    mob.setGait(1);
                    orbit += 0.045D;
                    double r = 5.5D;
                    Vec3 spot = target.position().add(Math.cos(orbit) * r, 0.0D, Math.sin(orbit) * r);
                    mob.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0D);
                    // ...and the flank: a back turned on it is a chance
                    Vec3 look = target.getViewVector(1.0F);
                    Vec3 toMe = mob.position().subtract(target.position()).normalize();
                    if (pounceCooldown <= 0 && dist > 3.5D && dist < 8.0D && look.dot(toMe) < -0.3D && mob.onGround()
                            && mob.random.nextInt(30) == 0) {
                        begin(POUNCE, target);
                    }
                    return;
                }
                // ---- ITS TURN: in
                mob.setGait(dist > 8.0D ? 2 : 0);
                mob.getNavigation().moveTo(target, dist > 8.0D ? 1.25D : 1.1D);
                if (cooldown > 0) {
                    cooldown--;
                    return;
                }
                if (dist < 2.8D) {
                    if (breathCooldown <= 0 && mob.random.nextFloat() < 0.25F) {
                        breathCooldown = 160;
                        begin(BREATH, target);
                    } else {
                        begin(BITE, target);
                    }
                } else if (dist > 4.0D && dist < 9.0D && pounceCooldown <= 0 && mob.onGround() && mob.hasLineOfSight(target)) {
                    begin(POUNCE, target);
                }
                return;
            }

            int t = mob.attackTicks;
            switch (state) {
                case BITE -> {
                    mob.getNavigation().stop();
                    mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    if (t == 6) {
                        mob.bite(target);
                    }
                    if (t > 12) {
                        finish(14);
                    }
                }
                case POUNCE -> {
                    // the tell: the crouch and the growl (0-12), the turn held; then the leap, aimed once
                    if (t < 12) {
                        mob.getNavigation().stop();
                        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    }
                    if (t == 2) {
                        mob.playSound(FFSounds.FROSTMAW_GROWL.get(), 1.2F, 0.9F + mob.random.nextFloat() * 0.2F);
                    }
                    if (t == 12) {
                        mob.leap(target);
                        launched = true;
                    }
                    if (launched && t > 13 && dist < 1.9D) {
                        target.hurt(mob.damageSources().mobAttack(mob), 8.0F);
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2), mob);
                        target.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 60, 0), mob);
                        mob.playSound(FFSounds.FROSTMAW_BITE.get(), 1.2F, 0.8F);
                        launched = false;
                        pounceCooldown = 120;
                        finish(24);
                    } else if (launched && t > 16 && mob.onGround()) {
                        launched = false;
                        pounceCooldown = 100;
                        finish(20);
                    } else if (t > 40) {
                        launched = false;
                        pounceCooldown = 100;
                        finish(20);
                    }
                }
                case BREATH -> {
                    mob.getNavigation().stop();
                    mob.getLookControl().setLookAt(target, 12.0F, 12.0F);
                    if (t == 10) {
                        mob.playSound(FFSounds.FROSTMAW_BREATH.get(), 1.4F, 1.0F);
                    }
                    if (t >= 10 && t <= 40 && t % 5 == 0) {
                        mob.breathe();
                    }
                    if (t > 46) {
                        finish(40);
                    }
                }
                case HOWL -> {
                    mob.getNavigation().stop();
                    if (t == 16) {
                        mob.howl(target);
                    }
                    if (t > 40) {
                        finish(20);
                    }
                }
                default -> finish(10);
            }
        }

        private void begin(int state, LivingEntity target) {
            mob.getNavigation().stop();
            mob.setGait(0);
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
                case BITE: return state.setAndContinue(BITE_ANIM);
                case POUNCE: return state.setAndContinue(POUNCE_ANIM);
                case BREATH: return state.setAndContinue(BREATH_ANIM);
                case HOWL: return state.setAndContinue(HOWL_ANIM);
                default: break;
            }
            if (state.isMoving() && gait() != 0) {
                return state.setAndContinue(gait() == 1 ? PROWL : RUN);
            }
            // the wander (speed x 0.6) swings its legs about 0.19, the trot in (x 1.1) about 0.6
            return state.setAndContinue(locomotion(state, IDLE, IDLE_SNIFF, STROLL, WALK, 0.35F));
        }));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && hurtTime > 0 && getAttackState() == 0 ? state.setAndContinue(HURT) : PlayState.STOP));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return FFSounds.FROSTMAW_IDLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.FROSTMAW_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.FROSTMAW_DEATH.get();
    }
}
