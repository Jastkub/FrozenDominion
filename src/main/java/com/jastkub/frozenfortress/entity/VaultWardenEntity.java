package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.EnumSet;

/**
 * Vault Warden - a colossus of glacial brick and black iron, built to guard
 * the treasure vaults. Awake from the start: he comes for whoever he sees.
 *
 * Attack states:
 *  1 = double swing
 *  2 = ground slam (ring shockwave)
 *  3 = frost beam (line attack)
 *  4 = awakening (played once when the statue stirs)
 *  5 = spin attack (when surrounded)
 *  6 = shard volley  (a fan of ice spears for anyone keeping their distance)
 *  7 = ground rupture (a line of spikes tearing toward the target)
 *  8 = warding stance (arms crossed; shrugs off blows and bats away arrows)
 * 14 = the bending of the air: his core flares and a wave of bent space goes out from it - one, or
 *      two in a row - striking whoever it reaches in his sight; a pillar between you and his core is the answer.
 *
 * <p>HIS LOOK: nothing he does is drawn in particles
 * any more - his blows leave arcs of frost, his slams craters and crests, his beam is a beam
 * (LastWatchBeamEntity), his vent steams, his stance wears a ward; the frost beam and the core vent, taken out of his
 * pool for being only particles (05.10.2026), are back in it with bodies.
 */
public class VaultWardenEntity extends FrostServantEntity {

    private static final EntityDataAccessor<Boolean> DORMANT =
            SynchedEntityData.defineId(VaultWardenEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.vault_warden.idle");
    private static final RawAnimation DORMANT_ANIM = RawAnimation.begin().thenLoop("animation.vault_warden.dormant");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.vault_warden.walk");
    private static final RawAnimation SWING = RawAnimation.begin().thenPlay("animation.vault_warden.swing");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("animation.vault_warden.slam");
    private static final RawAnimation BEAM = RawAnimation.begin().thenPlay("animation.vault_warden.beam");
    private static final RawAnimation AWAKEN = RawAnimation.begin().thenPlay("animation.vault_warden.awaken");
    private static final RawAnimation SPIN = RawAnimation.begin().thenPlay("animation.vault_warden.spin");
    private static final RawAnimation RUPTURE = RawAnimation.begin().thenPlay("animation.vault_warden.rupture");
    private static final RawAnimation WARD = RawAnimation.begin().thenLoop("animation.vault_warden.ward");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.vault_warden.hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.vault_warden.death");
    private static final RawAnimation SHATTER = RawAnimation.begin().thenPlay("animation.vault_warden.shatter");
    private static final RawAnimation STOMP = RawAnimation.begin().thenPlay("animation.vault_warden.stomp");
    private static final RawAnimation CORE_VENT = RawAnimation.begin().thenPlay("animation.vault_warden.core_vent");
    //
    private static final RawAnimation COMBO = RawAnimation.begin().thenPlay("animation.vault_warden.combo");
    private static final RawAnimation LOCK = RawAnimation.begin().thenPlay("animation.vault_warden.lock");
    /** The vault locks: grates dropped round you, and the charge after (VaultBarEntity). */
    public static final int VAULT_LOCK = 13;
    /** The wave of bent space (RoarWarpEntity's, the Monstrosity's): how far it goes, what it does. */
    public static final int TIME_WARP = 14;
    static final float WARP_REACH = 12.0F, WARP_DMG = 9.0F;

    /** Shown only while he is actually awake and fighting - a statue in an
     *  empty vault should not put a health bar on anyone's screen. */
    private final net.minecraft.server.level.ServerBossEvent bossEvent =
            new net.minecraft.server.level.ServerBossEvent(
                    net.minecraft.network.chat.Component.translatable("entity.frozen_dominion.vault_warden"),
                    net.minecraft.world.BossEvent.BossBarColor.BLUE,
                    net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_6);

    public VaultWardenEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 60;
    }

    @Override
    public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
        super.startSeenByPlayer(player);
        // no bar: a Warden is one of the citadel's guards now, not a miniboss
    }

    @Override
    public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    private void tickBossBar() {
        if (isDormant() || isDeadOrDying()) {
            if (!bossEvent.getPlayers().isEmpty()) {
                bossEvent.removeAllPlayers();
            }
            return;
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());
        // The bar turns as he does, so the second half of the fight announces
        // itself without needing a message.
        bossEvent.setColor(enraged
                ? net.minecraft.world.BossEvent.BossBarColor.WHITE
                : net.minecraft.world.BossEvent.BossBarColor.BLUE);
        // The bar carries the plating state. A fight where the player cannot
        // see WHY their damage stopped landing is just a bad fight, so what it
        // has adapted to - and how close it is to blowing - is written on it.
        String title = "Vault Warden";
        if (overloadTicks > 0) {
            title = "Vault Warden §c[CORE EXPOSED]";
            bossEvent.setColor(net.minecraft.world.BossEvent.BossBarColor.RED);
        } else if (adaptedSchool >= 0) {
            int pips = Math.round(getOverloadFraction() * 5.0F);
            title = "Vault Warden §b[" + SCHOOL_NAMES[adaptedSchool] + " warded] §e"
                    + "|".repeat(pips) + "§8" + "|".repeat(5 - pips);
            bossEvent.setColor(net.minecraft.world.BossEvent.BossBarColor.YELLOW);
        }
        bossEvent.setName(net.minecraft.network.chat.Component.literal(title));
        if (level() instanceof ServerLevel serverLevel) {
            for (net.minecraft.server.level.ServerPlayer player :
                    serverLevel.getPlayers(p -> p.distanceToSqr(this) < 48.0D * 48.0D)) {
                bossEvent.addPlayer(player);
            }
            for (net.minecraft.server.level.ServerPlayer player :
                    java.util.List.copyOf(bossEvent.getPlayers())) {
                if (player.distanceToSqr(this) >= 64.0D * 64.0D || player.level() != level()) {
                    bossEvent.removePlayer(player);
                }
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        bossEvent.removeAllPlayers();
        super.die(source);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 160.0D)
                .add(Attributes.ATTACK_DAMAGE, 13.6D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ARMOR, 14.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DORMANT, false);              // (never set again: no statue - see the class's note)
    }

    public boolean isDormant() {
        return entityData.get(DORMANT);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new VaultWardenAttackGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (isDormant()) {
            // A statue does not wander.
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            Player nearest = level().getNearestPlayer(this, 6.0D);
            if (nearest != null && !nearest.isCreative() && !nearest.isSpectator() && hasLineOfSight(nearest)) {
                awaken();
            }
        }
        // Awakening animation lock.
        if (getAttackState() == 4 && attackTicks > 50) {
            setAttackState(0);
        }
        if (staggerTicks > 0) {
            staggerTicks--;
            getNavigation().stop();
            if (staggerTicks % 5 == 0 && level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                        getX(), getY(2.0D), getZ(), 3, 0.5D, 0.4D, 0.5D, 0.02D);
            }
        }
        checkEnrage();
        tickPlating();
        tickWarps();
    }

    private void awaken() {
        entityData.set(DORMANT, false);
        setAttackState(4);
        level().playSound(null, blockPosition(), FFSounds.VAULT_WARDEN_AWAKEN.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
        AttackFxEntity.spawn(level(), AttackFxEntity.SNOW_BURST, position().add(0.0D, 1.6D, 0.0D), yBodyRot, 2.4F, 20, this);
        AttackFxEntity.spawn(level(), AttackFxEntity.GLINT, corePoint(), yBodyRot, 2.2F, 14, this);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isDormant() && source.getEntity() != null) {
            awaken();
        }
        // Stone shell: heavily resistant while dormant and while waking.
        if (isDormant() || getAttackState() == 4) {
            amount *= 0.2F;
        }
        // THE CORE IS HIS WEAK POINT: the glowing core on his chest, struck from in front - a blade aimed at it, an
        // arrow that lands in it - takes the blow at three times its weight, and the plating cannot learn it; laid open (bent over his fists
        // after the third blow, or reeling from his charge into a wall) at four and a half (was three). It flashes and
        // rings, so the lesson is seen and heard. (Those two openings' own bonuses are what it replaces: the core is
        // where they pay.)
        boolean core = !level().isClientSide && !isDormant() && getAttackState() != 4 && coreHit(source);
        if (core) {
            boolean open = comboOpen() || staggerTicks > 0;
            amount *= open ? 4.5F : 3.0F;
            level().broadcastEntityEvent(this, CORE_STRUCK);
            playSound(FFSounds.CRYSTAL_CHIME.get(), 1.8F, open ? 0.6F : 0.85F);
            playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 0.7F);
        } else {
            // Caught out of position: this is the payoff for making him miss.
            if (staggerTicks > 0) {
                amount *= 1.6F;
            }
            // bent over his fists after the third blow
            if (comboOpen()) {
                amount *= 1.35F;
            }
        }
        // A vented core is armour deliberately taken off. Standing in the
        // burn to hit it is the trade the attack is built around.
        if (isCoreOpen()) {
            amount *= 1.5F;
        }

        // Warding stance: braced behind his own arms. Arrows simply glance off.
        if (getAttackState() == 8) {
            if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile) {
                playSound(FFSounds.SENTINEL_SHIELD.get(), 1.0F, 1.4F);
                return false;
            }
            amount *= 0.3F;
        }
        // The plating reads the hit LAST, so it works on what actually got
        // through the stance and the stagger rather than on the raw number.
        if (!level().isClientSide && !isDormant() && getAttackState() != 4 && !core) {
            amount = applyAdaptivePlating(source, amount);
        }
        return super.hurt(source, amount);
    }

    /** The entity event that tells the clients his core was struck (it flashes - VaultWardenRenderer). */
    private static final byte CORE_STRUCK = 81;
    /** Client: until when the core flashes. */
    private long coreFlashUntil;

    @Override
    public void handleEntityEvent(byte id) {
        if (id == CORE_STRUCK) {
            coreFlashUntil = tickCount + 8;
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** 0..1: the flash of a struck core, fading. Client. */
    public float coreFlash(float partialTick) {
        float left = coreFlashUntil - (tickCount + partialTick);
        return left <= 0.0F ? 0.0F : Math.min(1.0F, left / 8.0F);
    }

    /** Where his core is: on his chest, before him (the model's core, at about two thirds of his height). */
    Vec3 corePoint() {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, yBodyRot);
        return position().add(fwd.scale(0.6D)).add(0.0D, getBbHeight() * 0.67D, 0.0D);
    }

    /** Was this blow on the core: from in front of him, at the core? An arrow where it landed; a blade by where
     *  its wielder was looking. */
    private boolean coreHit(DamageSource source) {
        net.minecraft.world.entity.Entity direct = source.getDirectEntity();
        net.minecraft.world.entity.Entity by = source.getEntity();
        Vec3 fwd = Vec3.directionFromRotation(0.0F, yBodyRot);
        Vec3 core = corePoint();
        if (direct instanceof net.minecraft.world.entity.projectile.Projectile p) {
            Vec3 at = p.position();
            Vec3 flat = new Vec3(at.x - getX(), 0.0D, at.z - getZ());
            return flat.lengthSqr() > 1.0E-4D && flat.normalize().dot(fwd) > 0.35D && Math.abs(at.y - core.y) < 0.7D;
        }
        if (by instanceof LivingEntity le && direct == by) {
            Vec3 flat = new Vec3(le.getX() - getX(), 0.0D, le.getZ() - getZ());
            if (flat.lengthSqr() < 1.0E-4D || flat.normalize().dot(fwd) < 0.35D) {
                return false;
            }
            Vec3 eye = le.getEyePosition();
            Vec3 look = le.getViewVector(1.0F);
            double t = core.subtract(eye).dot(look);
            return t > 0.0D && eye.add(look.scale(t)).distanceTo(core) < 0.8D;
        }
        return false;
    }

    static class VaultWardenAttackGoal extends Goal {
        private final VaultWardenEntity mob;
        private int attackCooldown;

        /** Per-attack cooldowns. The single shared timer let him repeat the
         *  same heavy attack back to back, which is how a boss ends up feeling
         *  like a slot machine rather than an opponent. */
        private final int[] perAttack = new int[16];

        private static int cooldownFor(int state) {
            return switch (state) {
                case 8 -> 400;   // warding stance
                case TIME_WARP -> 300;
                case VAULT_LOCK -> 300;
                case 12 -> 380;  // core vent
                case 11 -> 260;  // seismic step
                case 10 -> 300;  // vault shatter
                case 3 -> 260;   // sweeping beam
                case 7 -> 240;   // ground rupture
                case 9 -> 220;   // charge
                case 5 -> 200;   // spin
                default -> 30;   // swings stay available
            };
        }

        private boolean ready(int state) {
            return perAttack[state] <= 0;
        }

        private final java.util.List<Integer> options = new java.util.ArrayList<>();

        private void offer(int state, boolean allowed, int weight) {
            if (allowed && ready(state)) {
                for (int i = 0; i < weight; i++) {
                    options.add(state);
                }
            }
        }

        private int chooseOffered(int fallback) {
            int chosen = options.isEmpty() ? fallback : options.get(mob.random.nextInt(options.size()));
            options.clear();
            return chosen;
        }

        VaultWardenAttackGoal(VaultWardenEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !mob.isDormant() && mob.getTarget() != null && mob.getTarget().isAlive()
                    && mob.getAttackState() != 4 && !mob.isStaggered();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            if (mob.getAttackState() != 4) {
                mob.setAttackState(0);
            }
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            double dist = mob.distanceTo(target);
            int state = mob.getAttackState();

            for (int i = 0; i < perAttack.length; i++) {
                if (perAttack[i] > 0) {
                    perAttack[i]--;
                }
            }

            if (state == 0) {
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                mob.getNavigation().moveTo(target, 1.0D);
                if (attackCooldown > 0) {
                    attackCooldown--;
                    return;
                }
                long nearby = mob.level().getEntitiesOfClass(Player.class,
                        mob.getBoundingBox().inflate(4.0D), p -> !p.isCreative() && !p.isSpectator()).size();
                boolean sighted = mob.hasLineOfSight(target);
                // Everything he could legally do right now, weighted - then one
                // is drawn from it. Anything on cooldown simply is not offered,
                // so he cannot open with the same heavy attack twice running,
                // and unlike a chain of thresholds this can never fall through
                // and pick nothing at all.
                offer(8, mob.getHealth() < mob.getMaxHealth() * 0.4F, 3);   // brace
                offer(5, nearby >= 1, 4);                                   // spin
                offer(1, dist < 4.0D, 6);                                   // three blows
                // (the slam is gone into the seismic step: one attack out of the floor where there were two alike)
                offer(9, dist > 5.0D && dist < 15.0D && sighted, 4);        // charge
                // (the volley of shards is gone: the vault locks on you instead)
                offer(VAULT_LOCK, dist > 4.0D && dist < 16.0D && sighted, 4);
                offer(7, dist > 4.0D && dist < 20.0D && sighted, 4);        // rupture
                // - the frost beam and
                // the core vent were out of his pool for it; drawn with bodies now (12.10.2026), they are back
                offer(3, dist > 5.0D && dist < 16.0D && sighted, 3);        // the sweeping beam
                offer(12, nearby >= 1 && mob.getHealth() < mob.getMaxHealth() * 0.75F, 2);   // the core vent
                offer(TIME_WARP, dist < WARP_REACH && sighted, mob.isEnraged() ? 4 : 3);    // the bent air
                // nothing of his
                // ice comes at you through a wall: every attack that raises it wants you in his sight
                offer(10, dist < 12.0D && sighted, mob.isEnraged() ? 5 : 3);   // ring shatter
                offer(11, dist < 8.0D && sighted, 4);                          // seismic step
                int chosen = chooseOffered(dist < 4.0D ? 1 : sighted ? 7 : 0);
                if (chosen == 0) {
                    return;                                             // out of sight: he comes round to you
                }
                // Past half health he presses: the same attacks come round
                // again roughly a third sooner.
                perAttack[chosen] = mob.isEnraged()
                        ? (int) (cooldownFor(chosen) * 0.65F)
                        : cooldownFor(chosen);
                mob.setAttackState(chosen);
                return;
            }

            mob.getNavigation().stop();
            if (state == 3) {
                // Slow enough that the sweep can be outrun round him, which is
                // what makes the beam a puzzle rather than a dice roll.
                mob.getLookControl().setLookAt(target, 4.0F, 4.0F);
            } else if (state != 5 && state != 9) {
                mob.getLookControl().setLookAt(target, 12.0F, 12.0F);
            }

            switch (state) {
                case 1 -> { // THREE BLOWS: the swing (8), the backhand (18), a held beat with both fists high, and
                    // both down (32) - then they stay in the floor and he stays bent over them till 58: the opening
                    if (mob.attackTicks == 8 || mob.attackTicks == 18) {
                        mob.swingArc(mob.attackTicks == 18);
                        if (dist < 4.6D) {
                            mob.doHurtTarget(target);
                        }
                    }
                    if (mob.attackTicks == 24) {
                        mob.playSound(FFSounds.VAULT_WARDEN_AWAKEN.get(), 1.4F, 1.2F);   // the beat: you hear it held
                    }
                    if (mob.attackTicks == 32) {
                        mob.comboSmash();
                    }
                    if (mob.attackTicks > 62) {
                        finish(14);
                    }
                }
                case VAULT_LOCK -> { // the vault locks: arms up calling it, fists down (12), the grates drop (land 22)
                    if (mob.attackTicks == 4) {
                        mob.playSound(FFSounds.FROST_CHARGE.get(), 1.8F, 0.5F);
                    }
                    if (mob.attackTicks == 12) {
                        mob.lockVault(target);
                    }
                    // and he comes for you while they hold
                    if (mob.attackTicks == 12 + VaultBarEntity.LANDS + 2) {
                        mob.setAttackState(9);
                    }
                    if (mob.attackTicks > 40) {
                        finish(20);
                    }
                }
                case TIME_WARP -> { // arms up calling it, the core flaring (4-12), the wave out on 12 - and a second
                    // on 30 when he is roused (enraged, or one time in three)
                    if (mob.attackTicks == 4) {
                        mob.playSound(FFSounds.FROST_CHARGE.get(), 2.0F, 0.4F);
                        AttackFxEntity.spawn(mob.level(), AttackFxEntity.GLINT, mob.corePoint(), mob.yBodyRot, 2.6F, 10,
                                mob);
                    }
                    if (mob.attackTicks == 12) {
                        mob.timeWarp();
                        mob.warpTwice = mob.isEnraged() || mob.random.nextInt(3) == 0;
                    }
                    if (mob.attackTicks == 30 && mob.warpTwice) {
                        mob.timeWarp();
                    }
                    if (mob.attackTicks > (mob.warpTwice ? 48 : 32)) {
                        finish(24);
                    }
                }
                case 3 -> { // frost beam: a sweep, not a snapshot
                    if (mob.attackTicks == 6) {
                        mob.playSound(FFSounds.FROST_CHARGE.get(), 1.8F, 0.5F);
                    }
                    if (mob.attackTicks >= 16 && mob.attackTicks <= 40 && mob.attackTicks % 3 == 0) {
                        mob.frostBeam();
                        if (mob.attackTicks % 9 == 0) {
                            mob.playSound(FFSounds.FROST_BOLT_FIRE.get(), 1.4F, 0.5F);
                        }
                    }
                    if (mob.attackTicks > 48) {
                        finish(22);
                    }
                }
                case 5 -> { // spin: damage pulses ticks 10-30
                    if (mob.attackTicks >= 10 && mob.attackTicks <= 30 && mob.attackTicks % 5 == 0) {
                        // an arc of frost torn round him with each turn (his body comes round 72 degrees a pulse)
                        AttackFxEntity.spawn(mob.level(), "overseer_sweep", mob.position(),
                                mob.yBodyRot + 72.0F * (mob.attackTicks - 10) / 5, 1.5F, 12, mob);
                        for (LivingEntity victim : mob.level().getEntitiesOfClass(LivingEntity.class,
                                mob.getBoundingBox().inflate(3.4D),
                                e -> e != mob && !(e instanceof FrostServantEntity))) {
                            victim.hurt(mob.damageSources().mobAttack(mob), 8.0F);
                            Vec3 push = victim.position().subtract(mob.position()).normalize().scale(1.2D);
                            victim.push(push.x, 0.3D, push.z);
                        }
                    }
                    if (mob.attackTicks > 36) {
                        finish(20);
                    }
                }
                case 7 -> { // ground rupture: spikes tearing along the floor
                    if (mob.attackTicks == 18) {
                        mob.groundRupture(target);
                    }
                    if (mob.attackTicks > 36) {
                        finish(26);
                    }
                }
                case 8 -> { // warding stance: hunkered down, hard to shift
                    mob.getNavigation().stop();
                    if (mob.attackTicks == 4) {
                        mob.playSound(FFSounds.SENTINEL_SHIELD.get(), 1.6F, 0.6F);
                        // braced: a ward of rime closes round him for as long as he holds it
                        AttackFxEntity.spawn(mob.level(), AttackFxEntity.RIME_WARD, mob.position(), mob.yBodyRot,
                                2.6F, 62, mob).follow(mob);
                    }
                    // Everything he soaked up comes back out when he stands.
                    // Without this the whole stance reads as a long animation
                    // that does nothing at all, because the only thing it did
                    // was quietly reduce damage he was taking.
                    if (mob.attackTicks == 66) {
                        mob.wardBreak();
                    }
                    if (mob.attackTicks > 70) {
                        finish(45);
                    }
                }
                case 9 -> { // charge: he closes the gap himself
                    if (mob.attackTicks == 4) {
                        mob.playSound(FFSounds.VAULT_WARDEN_AWAKEN.get(), 1.6F, 1.4F);
                        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    }
                    if (mob.attackTicks == 16) {
                        // committed: the direction is locked in here, so
                        // sidestepping the launch is the counter-play
                        Vec3 dir = target.position().subtract(mob.position()).normalize();
                        mob.setDeltaMovement(dir.x * 1.45D, 0.42D, dir.z * 1.45D);
                        mob.hasImpulse = true;
                        mob.playSound(FFSounds.SENTINEL_SWING.get(), 1.8F, 0.6F);
                        mob.charging = true;
                    }
                    if (mob.charging && mob.attackTicks > 16 && mob.attackTicks < 34) {
                        for (LivingEntity victim : mob.level().getEntitiesOfClass(LivingEntity.class,
                                mob.getBoundingBox().inflate(1.4D),
                                e -> e != mob && !(e instanceof FrostServantEntity))) {
                            victim.hurt(mob.damageSources().mobAttack(mob), 10.0F);
                            Vec3 fling = victim.position().subtract(mob.position()).normalize().scale(1.4D);
                            victim.push(fling.x, 0.5D, fling.z);
                            mob.charging = false;   // one connection per charge
                            mob.chargeImpact();
                            break;
                        }
                        if (mob.charging && mob.horizontalCollision) {
                            mob.charging = false;
                            mob.chargeImpact();     // slammed into a wall instead
                            // Missing costs him: bait the charge into stone and
                            // you get a free window on him.
                            mob.enterStagger(mob.isEnraged() ? 30 : 45);
                        }
                    }
                    if (mob.attackTicks > 40) {
                        mob.charging = false;
                        finish(26);
                    }
                }
                case 10 -> { // vault shatter: expanding rings of ice
                    if (mob.attackTicks == 6) {
                        mob.playSound(FFSounds.FROST_CHARGE.get(), 2.0F, 0.6F);
                    }
                    if (mob.attackTicks == 20) {
                        mob.vaultShatter();
                    }
                    if (mob.attackTicks > 40) {
                        finish(28);
                    }
                }
                case 11 -> { // seismic step: one foot, twice
                    mob.getNavigation().stop();
                    if (mob.attackTicks == 10) {
                        mob.playSound(FFSounds.VAULT_WARDEN_STEP.get(), 1.4F, 0.6F);
                    }
                    if (mob.attackTicks == 22) {
                        mob.seismicStomp(6.5D, 12.0F);
                    }
                    // the aftershock: a second, wider ring for anyone who
                    // stepped back exactly far enough to dodge the first
                    if (mob.attackTicks == 34) {
                        mob.seismicStomp(10.0D, 8.0F);
                    }
                    if (mob.attackTicks > 46) {
                        finish(24);
                    }
                }
                case 12 -> { // core vent: he opens himself up and burns
                    mob.getNavigation().stop();
                    if (mob.attackTicks == 20) {
                        mob.playSound(FFSounds.VAULT_WARDEN_CORE.get(), 2.6F, 1.0F);
                    }
                    // While it is open he is dangerous to stand near AND
                    // easier to hurt - the core is out. Trading a damage
                    // window for a threat window is the whole point.
                    if (mob.attackTicks >= 22 && mob.attackTicks <= 46) {
                        if (mob.attackTicks % 6 == 0) {
                            mob.coreVentPulse();
                        }
                    }
                    if (mob.attackTicks > 54) {
                        finish(30);
                    }
                }
                default -> finish(6);
            }
        }

        private void finish(int cooldown) {
            mob.setAttackState(0);
            attackCooldown = cooldown;
        }
    }

    /** The arc of frost one of his swings tears through the air before him (the backhand turned the other way). */
    void swingArc(boolean backhand) {
        playSound(FFSounds.VAULT_WARDEN_SWIPE.get(), 1.6F, backhand ? 0.8F : 0.95F);
        AttackFxEntity.spawn(level(), "overseer_sweep", position(), yBodyRot + (backhand ? 180.0F : 0.0F), 1.5F, 12, this);
    }

    /** Set while the bent air comes twice this time (TIME_WARP). */
    boolean warpTwice;

    /** One wave of bent space out of his core: the client draws it (RoarWarpEntity, the Monstrosity's), this strikes
     *  as its rim passes - whoever it reaches with his core in their sight, once. */
    private final java.util.List<Warp> warps = new java.util.ArrayList<>();

    private static final class Warp {
        final Vec3 centre;
        final long start;
        final java.util.Set<java.util.UUID> struck = new java.util.HashSet<>();

        Warp(Vec3 centre, long start) {
            this.centre = centre;
            this.start = start;
        }
    }

    void timeWarp() {
        // where RoarWarpFx draws a warp of any owner but the Monstrosity: before him at three fifths of his height
        Vec3 fwd = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 centre = position().add(fwd.scale(2.6D)).add(0.0D, getBbHeight() * 0.6D, 0.0D);
        com.jastkub.frozenfortress.entity.RoarWarpEntity.roar(this, com.jastkub.frozenfortress.entity.RoarWarpEntity.RING_LIFE
                + 12, 0.85F, WARP_REACH, 1);
        warps.add(new Warp(centre, level().getGameTime()));
        playSound(FFSounds.VAULT_WARDEN_CORE.get(), 2.6F, 0.6F);
        playSound(FFSounds.SHOCKWAVE.get(), 2.2F, 0.5F);
        AttackFxEntity.spawn(level(), AttackFxEntity.GLINT, corePoint(), yBodyRot, 3.0F, 10, this);
    }

    /** The rims going out: their radius as RoarWarpFx draws it (0.6 + reach * (1 - (1 - f)^1.6) over RING_LIFE). */
    private void tickWarps() {
        if (warps.isEmpty()) {
            return;
        }
        long now = level().getGameTime();
        int life = com.jastkub.frozenfortress.entity.RoarWarpEntity.RING_LIFE;
        warps.removeIf(w -> {
            float f = (now - w.start) / (float) life;
            if (f > 1.0F) {
                return true;
            }
            float r = 0.6F + WARP_REACH * (1.0F - (float) Math.pow(1.0F - f, 1.6D));
            for (Player p : level().players()) {
                if (!p.isAlive() || p.isCreative() || p.isSpectator() || w.struck.contains(p.getUUID())) {
                    continue;
                }
                double d = p.position().add(0.0D, 1.0D, 0.0D).distanceTo(w.centre);
                if (d > r || d < r - 2.2D) {
                    continue;
                }
                // a pillar between: the bent air breaks on the stone
                if (level().clip(new net.minecraft.world.level.ClipContext(w.centre, p.getEyePosition(),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType()
                        != net.minecraft.world.phys.HitResult.Type.MISS) {
                    continue;
                }
                w.struck.add(p.getUUID());
                p.hurt(damageSources().indirectMagic(this, this), WARP_DMG);
                // time thick round them for a breath: slowed hard, their swings slowed with it
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2), this);
                p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 50, 1), this);
                Vec3 out = p.position().subtract(w.centre);
                Vec3 flat = new Vec3(out.x, 0.0D, out.z);
                flat = flat.lengthSqr() < 1.0E-4D ? fwdOf(this) : flat.normalize();
                p.setDeltaMovement(flat.x * 0.9D, 0.35D, flat.z * 0.9D);
                p.hurtMarked = true;
            }
            return false;
        });
    }

    private static Vec3 fwdOf(VaultWardenEntity w) {
        return Vec3.directionFromRotation(0.0F, w.getYRot());
    }

    /** Set while a charge is in flight, cleared the moment it connects. */
    private boolean charging;

    public boolean isCharging() {
        return charging;
    }

    /** He goes through his own grates (they burst on him - VaultBarEntity), never stopped by them. */
    @Override
    public boolean canCollideWith(net.minecraft.world.entity.Entity other) {
        return !(other instanceof VaultBarEntity) && super.canCollideWith(other);
    }

    /** The third blow: both fists down before him - hard, and the floor bursts round them in a ring of frost. */
    void comboSmash() {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 at = position().add(fwd.scale(2.6D));
        playSound(FFSounds.VAULT_WARDEN_SLAM.get(), 2.0F, 0.8F);
        AttackFxEntity.spawn(level(), "aurochs_crash", at, yBodyRot, 1.5F, 24, this);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5.0D),
                e -> e != this && !(e instanceof FrostServantEntity) && e.isAlive())) {
            if (v.position().distanceTo(at) > 2.8D) {
                continue;
            }
            v.hurt(damageSources().mobAttack(this), 15.0F);
            v.push(0.0D, 0.6D, 0.0D);
            v.hurtMarked = true;
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 0), this);
        }
        level().addFreshEntity(new FrostWaveEntity(level(), this, at.x, getY(), at.z, 3.2F, 8, 3.0F).light());
    }

    /** Is he bent over his fists after the third blow - the opening the delay bought you. */
    private boolean comboOpen() {
        return getAttackState() == 1 && attackTicks >= 32 && attackTicks <= 58;
    }

    /** The vault locks on them: eight grates round where they stand, falling from overhead (VaultBarEntity). */
    void lockVault(LivingEntity target) {
        double r = 1.9D;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0D * i / 8.0D;
            float yaw = (float) Math.toDegrees(a) - 90.0F;           // its face turned out from the middle
            level().addFreshEntity(new VaultBarEntity(level(), this, target.getX() + Math.cos(a) * r, target.getY(),
                    target.getZ() + Math.sin(a) * r, yaw));
        }
        playSound(FFSounds.VAULT_WARDEN_SLAM.get(), 1.6F, 1.1F);
        level().playSound(null, target.blockPosition(), FFSounds.FROST_RELEASE.get(),
                net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 0.8F);
    }

    /**
     * Ticks of exposed recovery. He earns these by missing: a charge that
     * ends in a wall instead of a player leaves him planted and slow, and
     * everything hurts more while he is. It is the one window in the fight
     * that the player creates rather than waits for, which is what turns
     * dodging his charge from survival into an opening.
     */
    private int staggerTicks;

    /** Past half health the vault stops being patient with you. */
    private boolean enraged;

    public boolean isStaggered() {
        return staggerTicks > 0;
    }

    public boolean isEnraged() {
        return enraged;
    }

    private void enterStagger(int ticks) {
        staggerTicks = ticks;
        playSound(FFSounds.ICE_CRACK.get(), 1.6F, 0.7F);
        AttackFxEntity.spawn(level(), AttackFxEntity.SNOW_BURST, position().add(0.0D, getBbHeight() * 0.6D, 0.0D), yBodyRot, 1.8F, 20, this);
    }

    private void checkEnrage() {
        if (enraged || getHealth() > getMaxHealth() * 0.5F || isDormant()) {
            return;
        }
        enraged = true;
        playSound(FFSounds.VAULT_WARDEN_AWAKEN.get(), 2.4F, 0.75F);
        playSound(FFSounds.SHOCKWAVE.get(), 2.0F, 0.6F);
        // roused: the air round him bends once, and the floor rings
        com.jastkub.frozenfortress.entity.RoarWarpEntity.roar(this, 30, 0.6F, 8.0F, 1);
        AttackFxEntity.spawn(level(), "overseer_shockwave", position(), yBodyRot, 2.4F, 20, this);
        AttackFxEntity.spawn(level(), AttackFxEntity.SNOW_BURST, position().add(0.0D, getBbHeight() * 0.5D, 0.0D), yBodyRot, 3.0F, 20, this);
        // He stops nursing the wound and simply comes at you.
        net.minecraft.world.entity.ai.attributes.AttributeInstance speed =
                getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * 1.25D);
        }
    }

    /**
     * The vault answers: rings of ice erupting outward in waves. Unlike the
     * rupture, which is a line you step off, this comes at you from every
     * direction at once - the gaps are between the waves, so it is read by
     * timing rather than by position.
     */
    void vaultShatter() {
        playSound(FFSounds.VAULT_WARDEN_SLAM.get(), 2.0F, 0.85F);
        AttackFxEntity.spawn(level(), "overseer_shockwave", position(), yBodyRot, 2.2F, 20, this);
        AttackFxEntity.spawn(level(), "aurochs_crash", position(), yBodyRot, 1.8F, 24, this);
        int rings = enraged ? 4 : 3;
        for (int ring = 1; ring <= rings; ring++) {
            double radius = 2.5D + ring * 2.2D;
            int count = 6 + ring * 3;
            double spin = ring * 0.4D;   // offset each ring so gaps do not line up
            for (int i = 0; i < count; i++) {
                double ang = spin + Math.PI * 2.0D * i / count;
                double x = getX() + Math.cos(ang) * radius, z = getZ() + Math.sin(ang) * radius;
                if (!openTo(x, z)) {
                    continue;                                       // (not through his vault's walls - 08.10.2026)
                }
                level().addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity(
                        level(), this, x, getY(), z, 7.0F, 10 + ring * 7));
            }
        }
    }

    /** Nothing solid between him and this point of the floor (a spike of his ice is never raised behind a wall). */
    private boolean openTo(double x, double z) {
        Vec3 from = new Vec3(getX(), getY() + 0.6D, getZ());
        return level().clip(new net.minecraft.world.level.ClipContext(from, new Vec3(x, getY() + 0.6D, z),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                this)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** Where a charge ends: he plants and the floor answers. */
    void chargeImpact() {
        playSound(FFSounds.VAULT_WARDEN_SLAM.get(), 1.9F, 1.15F);
        setDeltaMovement(getDeltaMovement().multiply(0.15D, 1.0D, 0.15D));
        AttackFxEntity.spawn(level(), "aurochs_crash", position().add(fwdOf(this).scale(1.2D)), yBodyRot, 1.4F, 22, this);
        AttackFxEntity.spawn(level(), AttackFxEntity.SNOW_BURST, position().add(0.0D, 1.0D, 0.0D), yBodyRot, 1.8F, 20, this);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(3.2D), e -> e != this && !(e instanceof FrostServantEntity))) {
            victim.hurt(damageSources().mobAttack(this), 7.0F);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
        }
    }

    /**
     * One foot down, hard. Damage falls off with distance and the ring is
     * generous, so it is a spacing check rather than a reaction check - and
     * the aftershock catches the player who backed off by exactly one step.
     */
    /**
     * ONE ATTACK OUT OF THE FLOOR WHERE THERE WERE TWO ALIKE: the foot comes down and a wave of frost runs out over the floor from him - a real crest of ice
     * (FrostWaveEntity), not a ring of particles - and the first one splits the floor under whoever he is after
     * as the slam did; the second, wider and lower, is the aftershock. A crest low enough at its end is jumped.
     */
    void seismicStomp(double radius, float damage) {
        playSound(FFSounds.VAULT_WARDEN_STOMP.get(), 2.2F, 1.0F);
        level().addFreshEntity(new FrostWaveEntity(level(), this, getX(), getY(), getZ(), (float) radius,
                (int) Math.round(radius * 2.2D), damage).light());
        LivingEntity mark = getTarget();
        if (mark != null && radius < 8.0D && hasLineOfSight(mark)) {
            for (int i = 0; i < 3; i++) {
                double ang = random.nextDouble() * Math.PI * 2.0D;
                double r = 1.5D + random.nextDouble() * 2.5D;
                level().addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity(
                        level(), this, mark.getX() + Math.cos(ang) * r, mark.getY(),
                        mark.getZ() + Math.sin(ang) * r, 7.0F, 12 + i * 6));
            }
        }
    }

    /** The core burning in the open: short reach, relentless, and it costs
     *  him - see hurt, where an open core takes extra. */
    void coreVentPulse() {
        // the cold out of his opened core: a plume of it before him, and round his feet
        AttackFxEntity.spawn(level(), "overseer_steam", corePoint(), yBodyRot, 1.8F, 22, this);
        AttackFxEntity.spawn(level(), "aurochs_steam", position(), yBodyRot, 1.6F, 22, this);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(4.5D), e -> e != this && !(e instanceof FrostServantEntity))) {
            victim.hurt(damageSources().mobAttack(this), 5.0F);
            victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 1), this);
        }
    }

    // ================================================================
    // ADAPTIVE PLATING
    //
    // The Warden is not a beast - it is the vault's security, and security
    // learns. Everything you hit it with is logged. Hit it enough times with
    // one kind of harm and the plating RECONFIGURES: that school of damage is
    // then largely wasted on it, and you are expected to change your answer.
    //
    // But stubbornness is also an answer. Keep hammering the school it has
    // already adapted to and the plating cannot dump the energy fast enough -
    // it OVERLOADS, blows its core open, and stands there wide open with the
    // adaptation wiped. So there are two honest ways to fight it:
    //
    //   swap schools  -> steady damage, never punished, never rewarded
    //   commit to one -> long dry spell, then a huge free window
    //
    // Neither is correct. That is the point.
    // ================================================================

    /** Damage schools the plating can reconfigure against. */
    public static final int SCHOOL_PHYSICAL = 0;
    public static final int SCHOOL_PROJECTILE = 1;
    public static final int SCHOOL_FIRE = 2;
    public static final int SCHOOL_MAGIC = 3;
    public static final int SCHOOL_EXPLOSION = 4;
    private static final int SCHOOLS = 5;

    private static final String[] SCHOOL_NAMES =
            {"Steel", "Volley", "Flame", "Arcana", "Blast"};

    /** Damage of each school taken since the last reconfiguration. */
    private final float[] exposure = new float[SCHOOLS];
    /** Which school the plating currently answers, or -1 for none. */
    private int adaptedSchool = -1;
    /** Ticks the current adaptation still holds. */
    private int adaptTicks;
    /** Energy dumped into an adaptation that cannot vent it. */
    private float overload;
    /** Ticks the core stays blown open after an overload. */
    private int overloadTicks;

    /** How much of one school it takes to force a reconfiguration. */
    private static final float ADAPT_THRESHOLD = 55.0F;
    /** How much wasted damage on an adapted school blows the core. */
    private static final float OVERLOAD_THRESHOLD = 90.0F;

    private static int schoolOf(DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            return SCHOOL_EXPLOSION;
        }
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            return SCHOOL_FIRE;
        }
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            return SCHOOL_PROJECTILE;
        }
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)
                || source.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)) {
            return SCHOOL_MAGIC;
        }
        return SCHOOL_PHYSICAL;
    }

    public int getAdaptedSchool() {
        return adaptedSchool;
    }

    /** 0..1 - how close the plating is to blowing itself open. */
    public float getOverloadFraction() {
        return Math.min(1.0F, overload / OVERLOAD_THRESHOLD);
    }

    /**
     * Runs the plating against one incoming hit and returns the damage that
     * actually gets through.
     */
    private float applyAdaptivePlating(DamageSource source, float amount) {
        int school = schoolOf(source);

        if (school == adaptedSchool && adaptTicks > 0) {
            // Already answered. Most of it is wasted - and the waste is what
            // builds the overload, so this is never fully pointless.
            overload += amount;
            if (overload >= OVERLOAD_THRESHOLD) {
                blowCore();
            }
            return amount * 0.35F;
        }

        // A school it has not answered. Log it; enough of the same and the
        // plating turns over.
        exposure[school] += amount;
        if (exposure[school] >= ADAPT_THRESHOLD) {
            adaptTo(school);
        }
        return amount;
    }

    private void adaptTo(int school) {
        adaptedSchool = school;
        adaptTicks = 500;               // 25 s, then it relaxes on its own
        overload = 0.0F;
        java.util.Arrays.fill(exposure, 0.0F);
        playSound(FFSounds.VAULT_WARDEN_AWAKEN.get(), 2.0F, 1.5F);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                    getX(), getY(1.4D), getZ(), 60, 0.9D, 1.2D, 0.9D, 0.08D);
        }
        announce("§bVault Warden reconfigures: §f" + SCHOOL_NAMES[school] + " §7warded");
    }

    /** The plating gives up and dumps everything it was holding. */
    private void blowCore() {
        adaptedSchool = -1;
        adaptTicks = 0;
        overload = 0.0F;
        overloadTicks = 140;            // 7 s of open core
        java.util.Arrays.fill(exposure, 0.0F);
        staggerTicks = Math.max(staggerTicks, 40);
        playSound(FFSounds.VAULT_WARDEN_AWAKEN.get(), 2.4F, 0.6F);
        AttackFxEntity.spawn(level(), "crush_burst", corePoint(), yBodyRot, 2.0F, 20, this);
        AttackFxEntity.spawn(level(), "overseer_shockwave", position(), yBodyRot, 2.0F, 20, this);
        announce("§cThe plating overloads - the core is exposed!");
    }

    private void announce(String msg) {
        for (net.minecraft.server.level.ServerPlayer p : bossEvent.getPlayers()) {
            p.displayClientMessage(net.minecraft.network.chat.Component.literal(msg), true);
        }
    }

    /** Decay: adaptations relax, and unused exposure bleeds away. */
    private void tickPlating() {
        if (adaptTicks > 0 && --adaptTicks == 0) {
            adaptedSchool = -1;
            overload = 0.0F;
        }
        if (overloadTicks > 0) {
            overloadTicks--;
            if (overloadTicks % 4 == 0 && level() instanceof ServerLevel sl) {
                sl.sendParticles(FFParticles.SOUL_FROST.get(),
                        getX(), getY(1.2D), getZ(), 4, 0.4D, 0.4D, 0.4D, 0.06D);
            }
            // The window is not free. A blown core is a wound that is venting,
            // and standing in the vent to hit it costs you - so the reward for
            // committing to one damage school is a knife fight, not a free
            // seven seconds of swinging at a statue.
            if (overloadTicks % 20 == 0 && level() instanceof ServerLevel sl2) {
                sl2.sendParticles(FFParticles.FROST_SWIRL.get(),
                        getX(), getY(1.0D), getZ(), 30, 1.6D, 0.6D, 1.6D, 0.12D);
                playSound(FFSounds.VAULT_WARDEN_AWAKEN.get(), 1.2F, 1.8F);
                for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                        getBoundingBox().inflate(3.2D),
                        e -> e instanceof Player && e.isAlive())) {
                    victim.hurt(damageSources().freeze(), 4.0F);
                    victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            FFEffects.FROSTBITE, 60, 0), this);
                }
            }
        }
        // Slow bleed, so a fight that wanders never leaves stale readings.
        if (tickCount % 40 == 0) {
            for (int i = 0; i < SCHOOLS; i++) {
                exposure[i] = Math.max(0.0F, exposure[i] - 2.0F);
            }
        }
    }

    /** True while the core is exposed - by the vent attack, or by an overload. */
    public boolean isCoreOpen() {
        return overloadTicks > 0
                || (getAttackState() == 12 && attackTicks >= 20 && attackTicks <= 48);
    }

    /** The warding stance breaking: everything he braced against comes back
     *  out as a ring of vault ice. */
    void wardBreak() {
        playSound(FFSounds.VAULT_WARDEN_SWIPE.get(), 2.0F, 1.1F);
        AttackFxEntity.spawn(level(), "overseer_shockwave", position(), yBodyRot, 2.4F, 20, this);
        AttackFxEntity.spawn(level(), AttackFxEntity.SNOW_BURST, position().add(0.0D, 1.4D, 0.0D), yBodyRot, 2.6F, 20, this);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(5.5D), e -> e != this && !(e instanceof FrostServantEntity))) {
            victim.hurt(damageSources().mobAttack(this), 11.0F);
            victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 0), this);
            Vec3 push = victim.position().subtract(position()).normalize().scale(1.1D);
            victim.push(push.x, 0.45D, push.z);
        }
    }

    /** The floor splits: a line of spikes tearing outward toward the target. */
    void groundRupture(LivingEntity target) {
        playSound(FFSounds.VAULT_WARDEN_SLAM.get(), 1.8F, 0.8F);
        Vec3 dir = target.position().subtract(position()).normalize();
        for (int i = 2; i <= 14; i++) {
            double x = getX() + dir.x * i;
            double z = getZ() + dir.z * i;
            net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(x, getY() + 0.5D, z);
            if (!level().getBlockState(at).getCollisionShape(level(), at).isEmpty()) {
                break;                                              // a wall: the fissure ends against it
            }
            level().addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity(
                    level(), this, x, getY(), z, 9.0F, 2 + i));
            // a second, staggered rank so it reads as a widening fissure
            if (i % 3 == 0) {
                double px = -dir.z, pz = dir.x;
                for (double s : new double[]{-1.4D, 1.4D}) {
                    level().addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity(
                            level(), this, x + px * s, getY(), z + pz * s, 7.0F, 3 + i));
                }
            }
        }
        AttackFxEntity.spawn(level(), "aurochs_crash", position().add(dir.scale(2.2D)), yBodyRot, 1.4F, 22, this);
    }

    /**
     * A lance of vault cold along whatever he is currently facing.
     *
     * <p>It deliberately does not snap to the target. Fired once, dead on
     * aim, it was a two-second wind-up for a single hit anyone could step out
     * of - all cost, no threat. Pulsed along his own facing while his head
     * turns slowly, it becomes a beam that sweeps the room: you outrun it or
     * you put a pillar between you and him, and standing still is fatal.
     */
    void frostBeam() {
        Vec3 start = getEyePosition();
        Vec3 dir = getLookAngle().normalize();
        // a beam with a body (the Last Watch's: a flare, the lance, rings running out it, a flare where it ends),
        // as far as the first stone - each pulse a fresh one along where he looks now, so it sweeps
        double reach = 16.0D;
        net.minecraft.world.phys.HitResult wall = level().clip(new net.minecraft.world.level.ClipContext(start,
                start.add(dir.scale(reach)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        if (wall.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            reach = wall.getLocation().distanceTo(start);
        }
        level().addFreshEntity(new LastWatchBeamEntity(level(), start, dir, (float) reach, false).lasting(4));
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(16.0D), e -> e != this && !(e instanceof FrostServantEntity))) {
            Vec3 to = victim.getEyePosition().subtract(start);
            double along = to.dot(dir);
            if (along > 0.0D && along < reach && to.subtract(dir.scale(along)).length() < 1.2D
                    && hasLineOfSight(victim)) {
                victim.hurt(damageSources().mobAttack(this), 12.0F);
                victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 100, 1), this);
            }
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** A construct this big takes its time coming down: on his knees by 18, over his fists by 30 (his death clip). */
    @Override
    protected int getDeathDuration() {
        return 70;
    }

    /** ...and he fades there, kneeling - no puff of smoke (12.10.2026). */
    @Override
    public int fadeTicks() {
        return 20;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (isDormant()) {
                return state.setAndContinue(DORMANT_ANIM);
            }
            // Reeling takes priority over whatever he was about to do, so the
            // opening is visible and not just a number.
            if (staggerTicks > 0) {
                return state.setAndContinue(HURT);
            }
            switch (getAttackState()) {
                case 1: return state.setAndContinue(COMBO);
                case 3: return state.setAndContinue(BEAM);
                case 4: return state.setAndContinue(AWAKEN);
                case 5: return state.setAndContinue(SPIN);
                case 7: return state.setAndContinue(RUPTURE);
                case 8: return state.setAndContinue(WARD);
                case 9: return state.setAndContinue(SLAM);
                case 10: return state.setAndContinue(SHATTER);
                case 11: return state.setAndContinue(STOMP);
                case 12: return state.setAndContinue(CORE_VENT);
                case VAULT_LOCK: return state.setAndContinue(LOCK);
                case TIME_WARP: return state.setAndContinue(LOCK);
            }
            if (state.isMoving()) {
                return state.setAndContinue(WALK);
            }
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && !isDormant() && hurtTime > 0 ? state.setAndContinue(HURT)
                        : software.bernie.geckolib.animation.PlayState.STOP));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isDormant() ? null : FFSounds.SENTINEL_IDLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.VAULT_WARDEN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.VAULT_WARDEN_DEATH.get();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (!isDormant()) {
            playSound(FFSounds.VAULT_WARDEN_STEP.get(), 0.8F, 1.0F);
        }
    }
}
