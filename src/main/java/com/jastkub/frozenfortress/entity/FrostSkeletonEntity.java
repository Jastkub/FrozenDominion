package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.EnumSet;

/**
 * MROZNY SZKIELET - one of the dead lying in the citadel, got up again
 *. Eight hearts' worth of nothing on its own: slow, it
 * trips over its own feet, a lunge leaves it on its face. What makes it
 * matter is that it is never alone - its statue brings up one or two more out
 * of the floor round it - and that a blow of its iced hand slows you, so the
 * rest of the band catches up.
 *
 * <p>And it does not stay dead. Beaten, it FALLS APART into a pile of bones
 * that lies twitching for a few seconds and then pulls itself back together,
 * at half its strength - unless you hit the pile, which ends it. It does that
 * twice; the third beating,
 * or fire, or a blast, ends it outright.
 *
 * <p>It rises out of the pose its statue lay in ({@link #rise}): the clips
 * start on exactly the statue's frame, so the swap costs nothing to the eye.
 */
public class FrostSkeletonEntity extends FrostServantEntity {

    public static final int ST_RISE = 1, ST_SWIPE = 2, ST_LUNGE = 3, ST_TRIP = 4, ST_DOWN = 5, ST_GETUP = 6,
            ST_PILE = 7, ST_REFORM = 8, ST_CLING = 9, ST_CLIMB = 10, ST_CLAMBER = 11;
    /**
     * UP THE CHASM'S PILLARS: hand over hand up a pillar's face out of the
     * mist (the clip "climb", a stroke CLIMB_TICKS long, each hand seven pixels over the other - so it rises that much
     * a half-stroke), to where it hangs by both hands from the edge, HANG under the top; then over it ("clamber"):
     * its body's way up and in keyed to the clip's poses (tools/gen_frost_skeleton.py CLAMBER_KEYS).
     */
    private static final int CLIMB_TICKS = 12;
    private static final double CLIMB_RISE = 7.0D / 16.0D / (CLIMB_TICKS / 2.0D), HANG = 2.2D;
    private static final int[] CLAMBER_AT = {0, 6, 12, 18, 26};
    private static final double[] CLAMBER_UP = {0.0D, 0.45D, 1.75D, 2.2D, 2.2D};
    private static final double[] CLAMBER_IN = {0.0D, 0.05D, 0.4D, 0.85D, 1.0D};
    private static final RawAnimation CLIMB = RawAnimation.begin().thenLoop("animation.frost_skeleton.climb");
    private static final RawAnimation CLAMBER = RawAnimation.begin().thenPlayAndHold("animation.frost_skeleton.clamber");
    /** How long it can hang on to your leg before its grip gives. */
    private static final int CLING_MAX = 80;
    /** How it gets up: out of its statue's pose, or (its band) clawing up out of the floor. */
    public static final String[] RISES = {"slumped", "lying", "kneeling", "curled", "dig"};
    public static final int DIG = 4;
    /** The clips' lengths in ticks (tools/gen_frost_skeleton.py). */
    private static final int[] RISE_TICKS = {51, 61, 46, 52, 65};
    private static final int SWIPE_HIT = 11, SWIPE_END = 24;
    private static final int LUNGE_LEAP = 8, LUNGE_END = 18;
    private static final int TRIP_END = 16, GETUP_END = 26, REFORM_END = 37;
    /** How long the pile lies there before it pulls itself together. */
    private static final int PILE_TICKS = 90;

    /**
     * WHAT IT STILL WEARS: 0 the bare bones, 1 a man of the guard (kettle hat, pauldron), 2 hooded and cloaked, 3 of
     * the court (circlet, tabard), 4 a prisoner in his irons. The gear is bones of the rig (tools/gen_remains.py,
     * VARIANTS); the statue it lay as carries the same number, so what lay there is what gets up.
     */
    private static final EntityDataAccessor<Byte> VARIANT =
            SynchedEntityData.defineId(FrostSkeletonEntity.class, EntityDataSerializers.BYTE);
    public static final String[][] VARIANT_BONES = {
            {}, {"v_helm", "v_pauldron"}, {"v_hood", "v_cloak"}, {"v_circlet", "v_tabard"},
            {"v_shackle_r", "v_shackle_l"}};
    public static final String[] ALL_VARIANT_BONES = {"v_helm", "v_pauldron", "v_hood", "v_cloak", "v_circlet",
            "v_tabard", "v_shackle_r", "v_shackle_l"};

    /** Does this variant wear this bone of gear? (Bones not of the gear: always.) */
    public static boolean wears(int variant, String bone) {
        if (!bone.startsWith("v_")) {
            return true;
        }
        if (variant <= 0 || variant >= VARIANT_BONES.length) {
            return false;
        }
        for (String b : VARIANT_BONES[variant]) {
            if (b.equals(bone)) {
                return true;
            }
        }
        return false;
    }

    public int variant() {
        return entityData.get(VARIANT);
    }

    protected void setVariant(int variant) {
        entityData.set(VARIANT, (byte) Mth.clamp(variant, 0, VARIANT_BONES.length - 1));
    }

    private static final EntityDataAccessor<Byte> RISE =
            SynchedEntityData.defineId(FrostSkeletonEntity.class, EntityDataSerializers.BYTE);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.frost_skeleton.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.frost_skeleton.walk");
    private static final RawAnimation SWIPE = RawAnimation.begin().thenPlay("animation.frost_skeleton.swipe");
    // (08.10.2026) a clip queued AFTER a one-shot needs the one-shot played PLAY_ONCE: the generators write every
    // one-shot hold_on_last_frame, which GeckoLib holds on its last frame for good - so the pile's twitching after
    // the collapse (70 ticks of it) and the scrabble after a lunge or a trip never came on
    private static final RawAnimation LUNGE = RawAnimation.begin()
            .then("animation.frost_skeleton.lunge", software.bernie.geckolib.core.animation.Animation.LoopType.PLAY_ONCE)
            .thenLoop("animation.frost_skeleton.down");
    private static final RawAnimation TRIP = RawAnimation.begin()
            .then("animation.frost_skeleton.trip", software.bernie.geckolib.core.animation.Animation.LoopType.PLAY_ONCE)
            .thenLoop("animation.frost_skeleton.down");
    private static final RawAnimation DOWN = RawAnimation.begin().thenLoop("animation.frost_skeleton.down");
    private static final RawAnimation GETUP = RawAnimation.begin().thenPlay("animation.frost_skeleton.getup");
    private static final RawAnimation COLLAPSE = RawAnimation.begin()
            .then("animation.frost_skeleton.collapse", software.bernie.geckolib.core.animation.Animation.LoopType.PLAY_ONCE)
            .thenLoop("animation.frost_skeleton.pile");
    private static final RawAnimation PILE = RawAnimation.begin().thenLoop("animation.frost_skeleton.pile");
    private static final RawAnimation REFORM = RawAnimation.begin().thenPlay("animation.frost_skeleton.reform");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("animation.frost_skeleton.death");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.frost_skeleton.hurt");
    private static final RawAnimation CLING = RawAnimation.begin().thenLoop("animation.frost_skeleton.cling");
    private static final RawAnimation[] RISE_ANIMS = new RawAnimation[RISES.length];

    static {
        for (int i = 0; i < RISES.length; i++) {
            RISE_ANIMS[i] = RawAnimation.begin().thenPlay("animation.frost_skeleton.rise_" + RISES[i]);
        }
    }

    /** How many more times it can pull itself back together: two lives in all. */
    public static final int REFORMS = 1;
    private int reformsLeft = REFORMS;
    /** Broken up as a pile: no fall, no lingering - it is gone. */
    private boolean scattered;
    /** Set alight: it goes up at once, for good. */
    private boolean burning;
    private int cooldown;
    private int tripCooldown = 100;
    private int downFor;
    private Vec3 lungeDir = Vec3.ZERO;
    private boolean lungeHit;
    /** Whose leg it has hold of. */
    @javax.annotation.Nullable
    private java.util.UUID clung;
    /** On a pillar's face (ST_CLIMB, ST_CLAMBER): no gravity, through the stone; where it goes over the edge to and
     *  where the clamber began. */
    private boolean climbing;
    private Vec3 climbTo = Vec3.ZERO, clamberFrom = Vec3.ZERO;
    private float climbYaw;

    public FrostSkeletonEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.19D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    /**
     * Gets one up: out of its statue's pose (0-3, as {@link #RISES}) or out of
     * the floor ({@link #DIG}), facing as the statue faced.
     */
    public static FrostSkeletonEntity rise(ServerLevel level, double x, double y, double z, float yaw, int how) {
        return rise(level, x, y, z, yaw, how, 0);
    }

    public static FrostSkeletonEntity rise(ServerLevel level, double x, double y, double z, float yaw, int how,
                                           int variant) {
        FrostSkeletonEntity s = FFEntities.FROST_SKELETON.get().create(level);
        if (s == null) {
            return null;
        }
        s.entityData.set(VARIANT, (byte) Mth.clamp(variant, 0, VARIANT_BONES.length - 1));
        s.moveTo(x, y, z, yaw, 0.0F);
        s.setYBodyRot(yaw);
        s.setYHeadRot(yaw);
        s.yBodyRotO = yaw;
        s.yHeadRotO = yaw;
        s.entityData.set(RISE, (byte) Mth.clamp(how, 0, RISES.length - 1));
        s.setAttackState(ST_RISE);
        // straight to the mob's own (no spawn event: nothing is to dress or promote the dead)
        s.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), MobSpawnType.MOB_SUMMONED,
                null, null);
        level.addFreshEntity(s);
        return s;
    }

    /**
     * One of the dead coming up out of the chasm: on the face of the pillar under `ledge` (the cell on its top it climbs
     * onto), in the open column on its `out` side, `below` blocks down, facing the stone.
     */
    @javax.annotation.Nullable
    public static FrostSkeletonEntity climbUp(ServerLevel level, BlockPos ledge, net.minecraft.core.Direction out,
                                              int below, int variant) {
        FrostSkeletonEntity s = FFEntities.FROST_SKELETON.get().create(level);
        if (s == null) {
            return null;
        }
        float yaw = out.getOpposite().toYRot();
        Vec3 stand = Vec3.atBottomCenterOf(ledge);
        double x = stand.x + out.getStepX() * 0.7D, z = stand.z + out.getStepZ() * 0.7D;
        s.entityData.set(VARIANT, (byte) Mth.clamp(variant, 0, VARIANT_BONES.length - 1));
        s.moveTo(x, ledge.getY() - below, z, yaw, 0.0F);
        s.setYBodyRot(yaw);
        s.setYHeadRot(yaw);
        s.yBodyRotO = yaw;
        s.yHeadRotO = yaw;
        s.climbing = true;
        s.climbTo = stand;
        s.climbYaw = yaw;
        s.noPhysics = true;
        s.setNoGravity(true);
        s.setAttackState(ST_CLIMB);
        s.finalizeSpawn(level, level.getCurrentDifficultyAt(ledge), MobSpawnType.MOB_SUMMONED, null, null);
        level.addFreshEntity(s);
        return s;
    }

    public boolean isClimbing() {
        return climbing;
    }

    private void endClimb() {
        climbing = false;
        noPhysics = false;
        setNoGravity(false);
    }

    private void holdClimbYaw() {
        setYRot(climbYaw);
        yRotO = climbYaw;
        setYBodyRot(climbYaw);
        setYHeadRot(climbYaw);
    }

    /** Hand over hand: up the face at the clip's pace, to where it hangs from the edge. */
    private void climb() {
        holdClimbYaw();
        setDeltaMovement(Vec3.ZERO);
        double y = getY() + CLIMB_RISE;
        if (attackTicks % (CLIMB_TICKS / 2) == 1) {
            playSound(FFSounds.FROST_SKELETON_STEP.get(), 0.6F, 1.15F + random.nextFloat() * 0.25F);
        }
        if (y >= climbTo.y - HANG) {
            y = climbTo.y - HANG;
            setPos(getX(), y, getZ());
            clamberFrom = position();
            setAttackState(ST_CLAMBER);
            return;
        }
        setPos(getX(), y, getZ());
    }

    /** Over the edge: the body's way up and in, keyed to the clip's poses. */
    private void clamber() {
        holdClimbYaw();
        setDeltaMovement(Vec3.ZERO);
        int k = attackTicks;
        if (k >= CLAMBER_AT[CLAMBER_AT.length - 1]) {
            setPos(climbTo.x, climbTo.y, climbTo.z);
            endClimb();
            setAttackState(0);
            cooldown = 6;
            return;
        }
        int i = 0;
        while (i < CLAMBER_AT.length - 2 && k >= CLAMBER_AT[i + 1]) {
            i++;
        }
        double f = (k - CLAMBER_AT[i]) / (double) (CLAMBER_AT[i + 1] - CLAMBER_AT[i]);
        f = f * f * (3.0D - 2.0D * f);
        double up = CLAMBER_UP[i] + (CLAMBER_UP[i + 1] - CLAMBER_UP[i]) * f;
        double in = CLAMBER_IN[i] + (CLAMBER_IN[i + 1] - CLAMBER_IN[i]) * f;
        setPos(clamberFrom.x + (climbTo.x - clamberFrom.x) * in, clamberFrom.y + up,
                clamberFrom.z + (climbTo.z - clamberFrom.z) * in);
        if (k == CLAMBER_AT[1] || k == CLAMBER_AT[3]) {
            playSound(FFSounds.FROST_SKELETON_STEP.get(), 0.8F, 0.8F);
        }
        if (k == CLAMBER_AT[2]) {
            playSound(FFSounds.FROST_SKELETON_RISE.get(), 0.9F, 1.1F);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(RISE, (byte) 0);
        entityData.define(VARIANT, (byte) 0);
    }

    public int riseIndex() {
        return entityData.get(RISE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new ShambleGoal(this));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.55D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Off its feet, getting up or swinging, it goes nowhere and turns to nothing. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || getAttackState() != 0;
    }

    @Override
    public boolean isPushable() {
        return getAttackState() != ST_PILE && super.isPushable();
    }

    // ------------------------------------------------------------------ the state machine
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;                               // (no particles to its rising)
        }
        if (isOnFire() && isAlive() && !burning) {
            burnUp();
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (tripCooldown > 0) {
            tripCooldown--;
        }
        if (climbing && getAttackState() != ST_CLIMB && getAttackState() != ST_CLAMBER) {
            endClimb();                                   // (knocked off the face: it falls)
        }
        switch (getAttackState()) {
            case ST_CLIMB -> climb();
            case ST_CLAMBER -> clamber();
            case ST_RISE -> {
                if (attackTicks == 1) {
                    playSound(FFSounds.FROST_SKELETON_RISE.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
                }
                if (attackTicks >= RISE_TICKS[riseIndex()]) {
                    setAttackState(0);
                    cooldown = 10;
                }
            }
            case ST_SWIPE -> swipe();
            case ST_LUNGE -> lunge();
            case ST_CLING -> cling();
            case ST_TRIP -> {
                if (attackTicks == 11) {
                    thud(0.6F);
                }
                if (attackTicks >= TRIP_END) {
                    down(18 + random.nextInt(16));
                }
            }
            case ST_DOWN -> {
                if (attackTicks >= downFor) {
                    setAttackState(ST_GETUP);
                }
            }
            case ST_GETUP -> {
                if (attackTicks >= GETUP_END) {
                    setAttackState(0);
                    cooldown = Math.max(cooldown, 8);
                }
            }
            case ST_PILE -> {
                if (attackTicks > PILE_TICKS - 30 && attackTicks % 8 == 0) {
                    playSound(FFSounds.FROST_SKELETON_IDLE.get(), 0.5F, 1.3F + random.nextFloat() * 0.3F);
                }
                if (attackTicks >= PILE_TICKS) {
                    setAttackState(ST_REFORM);
                    playSound(FFSounds.FROST_SKELETON_REFORM.get(), 1.0F, 1.0F);
                }
            }
            case ST_REFORM -> {
                if (attackTicks >= REFORM_END) {
                    reformsLeft--;
                    setHealth(getMaxHealth() * 0.5F);
                    setAttackState(0);
                    cooldown = 12;
                }
            }
            default -> {
            }
        }
    }

    /** The iced right hand, brought down over its head - and its whole weight after it. */
    private void swipe() {
        if (attackTicks == 5) {
            playSound(FFSounds.FROST_SKELETON_SWING.get(), 0.9F, 0.85F + random.nextFloat() * 0.3F);
        }
        if (attackTicks == SWIPE_HIT) {
            Vec3 fwd = Vec3.directionFromRotation(0.0F, getYRot());
            for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.2D, 0.5D, 2.2D),
                    e -> e != this && e.isAlive() && !FFAllies.ofTheKing(e))) {
                Vec3 to = v.position().subtract(position());
                Vec3 flat = new Vec3(to.x, 0.0D, to.z);
                if (flat.length() > 2.7D || (flat.lengthSqr() > 1.0E-4D && flat.normalize().dot(fwd) < 0.25D)) {
                    continue;
                }
                if (v.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE))) {
                    // the cold of it: a step slower, and the rest of them closer
                    v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0), this);
                    v.knockback(0.25D, -fwd.x, -fwd.z);
                    playSound(FFSounds.FROST_SKELETON_HURT.get(), 0.6F, 1.4F);
                }
            }
        }
        if (attackTicks >= SWIPE_END) {
            setAttackState(0);
            cooldown = 22 + random.nextInt(18) - 6 * Math.min(3, bandAround());
        }
    }

    /** A leap at you that ends on its face whether it found you or not. */
    private void lunge() {
        if (attackTicks == 1) {
            playSound(FFSounds.FROST_SKELETON_IDLE.get(), 1.0F, 0.7F);
        }
        if (attackTicks == LUNGE_LEAP) {
            setDeltaMovement(lungeDir.x * 0.62D, 0.32D, lungeDir.z * 0.62D);
            hurtMarked = true;
            playSound(FFSounds.FROST_SKELETON_SWING.get(), 1.0F, 0.7F);
        }
        if (attackTicks > LUNGE_LEAP && attackTicks <= LUNGE_END - 2 && !lungeHit) {
            for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.45D),
                    e -> e != this && e.isAlive() && !FFAllies.ofTheKing(e))) {
                if (v.hurt(damageSources().mobAttack(this), 4.0F)) {
                    v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 0), this);
                    lungeHit = true;
                    if (v instanceof Player) {
                        grab(v);
                        return;
                    }
                }
            }
        }
        if (attackTicks == LUNGE_END - 4) {
            thud(0.9F);
        }
        if (attackTicks >= LUNGE_END) {
            down(24 + random.nextInt(14));
        }
    }

    /** It lands at their feet with their leg in both hands: they are going nowhere until they hit it. */
    private void grab(LivingEntity v) {
        clung = v.getUUID();
        Vec3 to = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
        Vec3 dir = to.lengthSqr() > 1.0E-4D ? to.normalize() : Vec3.directionFromRotation(0.0F, getYRot());
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        Vec3 at = v.position().subtract(dir.scale(1.1D));
        setPos(at.x, v.getY(), at.z);
        setDeltaMovement(Vec3.ZERO);
        setAttackState(ST_CLING);
        playSound(FFSounds.FROST_SKELETON_HURT.get(), 1.0F, 0.6F);
        playSound(FFSounds.FROST_SKELETON_IDLE.get(), 1.0F, 0.8F);
    }

    @javax.annotation.Nullable
    private LivingEntity clungTo() {
        return clung != null && level() instanceof ServerLevel sl && sl.getEntity(clung) instanceof LivingEntity l
                && l.isAlive() ? l : null;
    }

    /** It holds on: the leg pinned - no step, no jump - and it stays glued to their feet. */
    private void cling() {
        LivingEntity v = clungTo();
        if (v == null || attackTicks > CLING_MAX || v.distanceToSqr(this) > 9.0D
                || (v instanceof Player pl && (pl.isCreative() || pl.isSpectator()))) {
            letGo();
            return;
        }
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 6, false, false, true), this);
        v.addEffect(new MobEffectInstance(MobEffects.JUMP, 6, -10, false, false, false), this);
        Vec3 to = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
        if (to.lengthSqr() > 1.6D) {
            Vec3 at = v.position().subtract(to.normalize().scale(1.1D));
            setPos(at.x, getY(), at.z);
        }
        if (attackTicks % 16 == 0) {
            playSound(FFSounds.FROST_SKELETON_IDLE.get(), 0.7F, 1.2F);
        }
    }

    /** It lets go (struck off, or its grip gave): flat on its face, as after any leap. */
    private void letGo() {
        clung = null;
        down(20 + random.nextInt(12));
    }

    private void down(int ticks) {
        downFor = ticks;
        setAttackState(ST_DOWN);
    }

    /** It catches its own foot and goes down. */
    public void trip() {
        if (getAttackState() == 0 && isAlive() && !isPassenger()) {
            setAttackState(ST_TRIP);
            tripCooldown = 160 + random.nextInt(160);
            playSound(FFSounds.FROST_SKELETON_IDLE.get(), 0.8F, 1.5F);
        }
    }

    private void thud(float volume) {
        playSound(FFSounds.FROST_SKELETON_HURT.get(), volume, 0.7F);
        if (level() instanceof ServerLevel sl) {
            BlockState under = level().getBlockState(blockPosition().below());
            if (!under.isAir()) {
                Vec3 f = Vec3.directionFromRotation(0.0F, getYRot());
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), getX() + f.x, getY() + 0.1D,
                        getZ() + f.z, 10, 0.5D, 0.05D, 0.5D, 0.1D);
            }
        }
    }

    /** How many of its band are on the same quarry, near it - each one makes the others bolder. */
    int bandAround() {
        LivingEntity t = getTarget();
        if (t == null) {
            return 0;
        }
        return level().getEntitiesOfClass(FrostSkeletonEntity.class, t.getBoundingBox().inflate(5.0D),
                s -> s != this && s.isAlive() && s.getTarget() == t).size();
    }

    // ------------------------------------------------------------------ falling apart
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // GETTING UP, OR PULLING ITSELF TOGETHER, IT IS UNTOUCHABLE: only fire (which ends it at any time) and what kills outright get through
        if (!level().isClientSide && (getAttackState() == ST_RISE || getAttackState() == ST_REFORM) && isAlive()
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.IS_FIRE)) {
            if (source.getEntity() != null) {
                playSound(FFSounds.FROST_SKELETON_STEP.get(), 0.8F, 0.5F);
            }
            return false;
        }
        if (!level().isClientSide && getAttackState() == ST_PILE && isAlive()) {
            // the pile: nothing breaks it;
            // only what kills outright (the void, /kill) ends it there
            if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !(burning && source.is(DamageTypeTags.IS_FIRE))) {
                if (source.getEntity() != null) {
                    playSound(FFSounds.FROST_SKELETON_STEP.get(), 0.8F, 0.6F);
                }
                return false;
            }
            scattered = true;
            return super.hurt(source, Math.max(amount, 100.0F));
        }
        if (!level().isClientSide && getAttackState() == ST_CLING && source.getEntity() != null
                && source.getEntity().getUUID().equals(clung)) {
            boolean r = super.hurt(source, amount);
            if (isAlive() && getAttackState() == ST_CLING) {
                letGo();                                 // kicked off the leg it held
            }
            return r;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            amount *= 2.0F;                              // brittle with frost: fire takes it apart
        }
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive() && getAttackState() == 0 && tripCooldown <= 0 && !isPassenger()
                && source.getEntity() != null && random.nextFloat() < 0.15F) {
            trip();                                       // knocked off balance
        }
        return hit;
    }

    /**
     * Beaten, it does not die: it falls apart, and lies as a pile - twice.
     * Fire, a blast, the void - or a third beating - and it is done.
     */
    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && reformsLeft > 0 && !scattered && getAttackState() != ST_PILE
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.IS_FIRE)
                && !source.is(DamageTypeTags.IS_EXPLOSION)) {
            setHealth(1.0F);
            setAttackState(ST_PILE);
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            bones(14);
            return;
        }
        super.die(source);
    }

    @Override
    protected int getDeathDuration() {
        return scattered ? 3 : 30;
    }

    @Override
    protected void tickDeath() {
        if (!level().isClientSide && deathTime == getDeathDuration() - 1) {
            playSound(FFSounds.FROST_SKELETON_SHATTER.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
            bones(scattered ? 26 : 20);
        }
        super.tickDeath();
    }

    /**
     * FIRE ENDS IT: frost holds these bones together, and a flame put to them - a
     * flint and steel, a fire charge, a blade with Fire Aspect, a fire on the floor - takes the frost
     * out of them at a stroke: no pile, no getting up again, standing or lying.
     */
    private void burnUp() {
        burning = true;
        scattered = true;
        playSound(net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.7F + random.nextFloat() * 0.2F);
        playSound(FFSounds.FROST_SKELETON_SHATTER.get(), 1.0F, 0.75F);
        hurt(damageSources().onFire(), 1000.0F);
        if (isAlive()) {
            kill();
        }
    }

    /** A flint and steel (or a fire charge) put to it - standing, or lying as a pile. */
    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean charge = stack.is(Items.FIRE_CHARGE);
        if (!stack.is(Items.FLINT_AND_STEEL) && !charge) {
            return super.mobInteract(player, hand);
        }
        level().playSound(player, getX(), getY(), getZ(), charge ? net.minecraft.sounds.SoundEvents.FIRECHARGE_USE
                : net.minecraft.sounds.SoundEvents.FLINTANDSTEEL_USE, getSoundSource(), 1.0F, random.nextFloat() * 0.4F + 0.8F);
        if (!level().isClientSide) {
            setLastHurtByPlayer(player);
            setSecondsOnFire(8);
            if (charge) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            } else {
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
    }

    private void bones(int n) {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE)), getX(), getY() + 0.3D,
                    getZ(), n, 0.4D, 0.2D, 0.4D, 0.12D);
            sl.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 0.4D, getZ(), n / 2, 0.4D, 0.3D, 0.4D, 0.08D);
        }
    }

    public boolean isPile() {
        return getAttackState() == ST_PILE;
    }

    // ------------------------------------------------------------------ the brain
    /** Shamble at them; swing when close, now and then throw itself at them; trip. */
    static class ShambleGoal extends Goal {
        private final FrostSkeletonEntity mob;
        private int repath;

        ShambleGoal(FrostSkeletonEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            // never from a saddle: tripping, lunging and grabbing are things done on foot
            return t != null && t.isAlive() && mob.getAttackState() == 0 && !mob.isPassenger();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null || mob.getAttackState() != 0) {
                return;
            }
            mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d = mob.distanceTo(t);
            if (--repath <= 0) {
                repath = 8 + mob.random.nextInt(6);
                mob.getNavigation().moveTo(t, 0.85D);
            }
            boolean walking = mob.getDeltaMovement().horizontalDistanceSqr() > 0.0009D;
            if (walking && mob.tripCooldown <= 0 && mob.random.nextInt(240) == 0) {
                mob.trip();
                return;
            }
            if (mob.cooldown > 0) {
                return;
            }
            if (d < 2.3D) {
                face(t);
                mob.setAttackState(ST_SWIPE);
            } else if (d > 3.0D && d < 5.5D && mob.onGround() && mob.random.nextInt(30) == 0 && mob.hasLineOfSight(t)) {
                face(t);
                Vec3 to = t.position().subtract(mob.position());
                mob.lungeDir = new Vec3(to.x, 0.0D, to.z).normalize();
                mob.lungeHit = false;
                mob.setAttackState(ST_LUNGE);
            }
        }

        private void face(LivingEntity t) {
            float yaw = (float) (Mth.atan2(t.getZ() - mob.getZ(), t.getX() - mob.getX()) * (180.0D / Math.PI)) - 90.0F;
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
            mob.getNavigation().stop();
        }
    }

    // ------------------------------------------------------------------ animation
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            int st = getAttackState();
            // the rise starts ON its statue's frame and the reform on the pile's: no blend into them - nor into the
            // climb (it comes up the face so) or the clamber (its body's way over the edge runs on the clip's ticks)
            state.getController().setTransitionLength(st == ST_RISE || st == ST_REFORM || st == ST_CLIMB
                    || st == ST_CLAMBER ? 0 : 3);
            if (isDeadOrDying()) {
                return state.setAndContinue(scattered ? PILE : DEATH);
            }
            RawAnimation riding = ridingAnim();
            if (riding != null) {
                return state.setAndContinue(riding);
            }
            switch (st) {
                case ST_RISE: return state.setAndContinue(RISE_ANIMS[Mth.clamp(riseIndex(), 0, RISES.length - 1)]);
                case ST_SWIPE: return state.setAndContinue(SWIPE);
                case ST_LUNGE: return state.setAndContinue(LUNGE);
                case ST_TRIP: return state.setAndContinue(TRIP);
                case ST_DOWN: return state.setAndContinue(DOWN);
                case ST_GETUP: return state.setAndContinue(GETUP);
                case ST_PILE: return state.setAndContinue(COLLAPSE);
                case ST_CLING: return state.setAndContinue(CLING);
                case ST_REFORM: return state.setAndContinue(REFORM);
                case ST_CLIMB: return state.setAndContinue(CLIMB);
                case ST_CLAMBER: return state.setAndContinue(CLAMBER);
                default: break;
            }
            return state.setAndContinue(FrostServantEntity.going(state) ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && hurtTime > 0 && getAttackState() == 0 && ridingAnim() == null
                        ? state.setAndContinue(HURT)
                        : software.bernie.geckolib.core.object.PlayState.STOP));
    }

    /** In a saddle (FrostRiderEntity): the clip it sits in there, in place of all its own; null on its feet. */
    @javax.annotation.Nullable
    protected RawAnimation ridingAnim() {
        return null;
    }

    // ------------------------------------------------------------------ sounds, save
    @Override
    protected SoundEvent getAmbientSound() {
        return getAttackState() == 0 ? FFSounds.FROST_SKELETON_IDLE.get() : null;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 140;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.FROST_SKELETON_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.FROST_SKELETON_COLLAPSE.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        playSound(FFSounds.FROST_SKELETON_STEP.get(), 0.45F, 0.85F + random.nextFloat() * 0.3F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Reforms", reformsLeft);
        tag.putByte("Rise", entityData.get(RISE));
        tag.putByte("Variant", entityData.get(VARIANT));
        if (climbing) {
            tag.putBoolean("Climbing", true);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        reformsLeft = tag.contains("Reforms") ? tag.getInt("Reforms") : REFORMS;
        entityData.set(RISE, tag.getByte("Rise"));
        entityData.set(VARIANT, tag.getByte("Variant"));
        if (tag.getBoolean("Climbing")) {
            setNoGravity(false);                         // (saved on a pillar's face: it lets go)
        }
        if (getHealth() <= 1.0F && reformsLeft > 0) {
            reformsLeft--;                               // saved as a pile: it got up while nobody watched
            setHealth(getMaxHealth() * 0.5F);
        }
    }
}
