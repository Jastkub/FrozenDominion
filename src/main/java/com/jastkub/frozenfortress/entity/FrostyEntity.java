package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FROSTY - THE ALPHA OF THE KENNELS, the miniboss of Legowisko (the Den), past the Drowned Lady's cistern
 *. The
 * frostmaws' sire: the hound's rig 2.2 times its size (tools/gen_frosty.py - its beats are these).
 *
 * <p>NO CHARGE ("wilki nie szarzuja"). He stalks round you, low, and he reads you:
 * <ul>
 *   <li>COMBO - snap, snap, and the third LATE: reared back and held, the jaws wide, turning with you - whoever dodged
 *       on the rhythm of the first two is caught by it.</li>
 *   <li>SWEEP - stand behind him and the hind quarters come round, the tail's ice through where you stood.</li>
 *   <li>BREATH - a gust of frost; it, and his bites, put RIME in you. Rime full ({@link #RIME_MAX}) and you are
 *       frozen where you stand - and he leaps at you at once.</li>
 *   <li>LEAP - down on his haunches (the eyes flare, the growl), and he comes down jaws first where you stood. Hit,
 *       it is the hardest blow he has. MISSED, his jaws are driven into the ice and held there ({@link #STUCK}):
 *       the one window in his fight - no ceiling (BossCeiling), the blows doubled half again - until he tears free
 *       and throws off whoever is at his head.</li>
 *   <li>CLAW - up off his fore paws, and they rake: right, then left, two wide arcs before him - quick where the
 *       combo is slow, and wide where it is narrow.</li>
 *   <li>RIME PATH - reared up, the roar, and both paws down: the floor splits from them in a line at you (three lines,
 *       in his second half), and the ice comes up along it from his paws outward. Out of the line, not back along it.</li>
 *   <li>MANE - he shakes himself like a hound out of water, and the ice of his mane goes up off him: rings close on the
 *       floor - on you, where you are going, round about - and the shards come down into them.</li>
 *   <li>HOWL - his pack comes out of the dark: as he wakes, and at four fifths, three fifths, two and one; the howl drives the pack on.</li>
 *   <li>BLIZZARD - at half (and again every half minute after): up on his hind legs, the howl, and the storm comes
 *       down on the den - a white wall ten blocks off (FrostyStormFog) - and in it he circles where you cannot see
 *       him, and leaps out of it: listen for the growl.</li>
 * </ul>
 */
public class FrostyEntity extends FrostServantEntity implements GateKeeper, BreathingMaw {

    private static final EntityDataAccessor<Boolean> DORMANT =
            SynchedEntityData.defineId(FrostyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STORM =
            SynchedEntityData.defineId(FrostyEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int AWAKEN = 1, COMBO = 2, SWEEP = 3, LEAP = 4, STUCK = 5, UNSTUCK = 6, BREATH = 7, HOWL = 8,
            BLIZZARD = 9, INTRO = 10, CLAW = 11, RIME_PATH = 12, MANE = 13;

    // ---- the beats (tools/gen_frosty.py - change one, change both)
    public static final int INTRO_T = 120, INTRO_EYES = 20, INTRO_RISE = 40, INTRO_SHAKE = 64, INTRO_HOWL = 86;
    static final int AWAKEN_T = 36;
    static final int COMBO_T = 56;
    static final int[] COMBO_HITS = {10, 20, 46};
    static final int SWEEP_T = 30, SWEEP_HIT = 13;
    static final int LEAP_T = 44, LEAP_OFF = 16, LEAP_LAND = 34;
    static final int UNSTUCK_T = 20, UNSTUCK_THROW = 8;
    static final int BREATH_T = 46;
    static final int HOWL_T = 40, HOWL_CALL = 16;
    static final int BLIZZARD_T = 64, BLIZZARD_STORM = 26;
    static final int CLAW_T = 34;
    static final int[] CLAW_HITS = {12, 21};
    static final int PATH_T = 40, PATH_SLAM = 18;
    static final int MANE_T = 46, MANE_THROW = 18;
    public static final int DEATH_T = 76, DEATH_STILL = 70, DEATH_FALL = 40, DEATH_BREATH = 52, DEATH_LAG = 4;

    // ---- the numbers
    static final float BITE_DMG = 9.0F, LATE_BITE_DMG = 14.0F, SWEEP_DMG = 10.0F, LEAP_DMG = 17.0F, SLAM_DMG = 6.0F,
            BREATH_DMG = 2.5F, CLAW_DMG = 8.0F, PATH_DMG = 9.0F, SHARD_DMG = 8.0F;
    /** The rime path: how far it runs, how long after the paws come down its first ice comes up (a block a tick on). */
    static final int PATH_LEN = 16, PATH_WARN = 10;
    /** A shard of his mane strikes this long after its ring shows (fx_frosty_shard: 1.0 s - tools/gen_frosty_fx.py). */
    static final int SHARD_FALL = 20;
    static final double SHARD_R = 1.5D;
    /** His jaws in the ice: ticks (fewer in his second half), and what the blows do to him there. */
    static final int STUCK_T = 70, STUCK_T_TWO = 56;
    static final float STUCK_TAKEN = 1.5F;
    /** Rime in one: a bite one, a gust of breath one every eight ticks in it; full, frozen through for FREEZE_T. */
    static final int RIME_MAX = 6, FREEZE_T = 40, RIME_FADE = 60;
    /** The storm: how long it lasts; how long between storms in his second half. */
    static final int STORM_T = 400, STORM_EVERY = 600;
    /** His pack: called at these fractions of his health (the first as he wakes), never more than PACK_MAX alive. */
    static final float[] CALL_AT = {1.0F, 0.8F, 0.6F, 0.4F, 0.2F};
    static final int PACK_MAX = 5;
    static final double WAKE = 18.0D, LEASH = 24.0D, STALK_R = 6.5D, STORM_R = 14.0D;
    static final int GIVE_UP = 300;

    private static final String P = "animation.frosty.";
    private static final RawAnimation DORMANT_ANIM = RawAnimation.begin().thenLoop(P + "dormant");
    private static final RawAnimation INTRO_ANIM = RawAnimation.begin().thenPlay(P + "intro");
    private static final RawAnimation AWAKEN_ANIM = RawAnimation.begin().thenPlay(P + "awaken");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop(P + "run");
    private static final RawAnimation PROWL = RawAnimation.begin().thenLoop(P + "prowl");
    private static final RawAnimation COMBO_ANIM = RawAnimation.begin().thenPlay(P + "combo");
    private static final RawAnimation SWEEP_ANIM = RawAnimation.begin().thenPlay(P + "sweep");
    private static final RawAnimation LEAP_ANIM = RawAnimation.begin().thenPlayAndHold(P + "leap");
    private static final RawAnimation STUCK_ANIM = RawAnimation.begin().thenLoop(P + "stuck");
    private static final RawAnimation UNSTUCK_ANIM = RawAnimation.begin().thenPlay(P + "unstuck");
    private static final RawAnimation BREATH_ANIM = RawAnimation.begin().thenPlay(P + "breath");
    private static final RawAnimation HOWL_ANIM = RawAnimation.begin().thenPlay(P + "howl");
    private static final RawAnimation BLIZZARD_ANIM = RawAnimation.begin().thenPlay(P + "blizzard");
    private static final RawAnimation CLAW_ANIM = RawAnimation.begin().thenPlay(P + "claw");
    private static final RawAnimation PATH_ANIM = RawAnimation.begin().thenPlay(P + "rime_path");
    private static final RawAnimation MANE_ANIM = RawAnimation.begin().thenPlay(P + "mane");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay(P + "hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(P + "death");
    /** The hound's gait drawn 2.2 times its size covers 2.2 times the ground a stride: played as much slower, and
     *  quickened again for his own pace against the hound's (0.30 against 0.33). */
    private static final double GAIT = 0.30D / 0.33D / 2.2D * 1.15D;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.frozen_dominion.frosty"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
    private final BossCeiling ceiling = new BossCeiling();

    @Nullable
    private BlockPos home;
    /** His den, relative to home (the citadel's generator writes it: "Arena"). */
    private int[] arena = {-16, -2, -16, 16, 14, 16};
    private float introYaw;
    private boolean secondHalf;
    private int calls;
    int rest;
    private int emptyRoom;
    private int stormLeft, stormNext;
    // the leap in hand
    private Vec3 leapFrom = Vec3.ZERO, leapTo = Vec3.ZERO;
    private boolean leapHit;
    @Nullable
    private UUID leapAt;
    private int stuckFor;
    private int breathClient;
    /** Rime in each of them, and the tick it was last put in. */
    private final Map<UUID, Integer> rime = new HashMap<>();
    private final Map<UUID, Long> rimeAt = new HashMap<>();
    /** Somebody frozen through by his rime: leap at them now. */
    @Nullable
    private UUID pounceOn;
    /** The shards of his mane on their way down. */
    private final List<Shard> shards = new java.util.ArrayList<>();

    private static final class Shard {
        final Vec3 at;
        final long start;
        boolean shown;

        Shard(Vec3 at, long start) {
            this.at = at;
            this.start = start;
        }
    }

    public FrostyEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 180;
        setMaxUpStep(1.5F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 420.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 3.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DORMANT, true);
        entityData.define(STORM, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new FrostyGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                p -> inDen(p.position())));
    }

    // ------------------------------------------------------------------------------------------------ what he is
    public boolean isDormant() {
        return entityData.get(DORMANT);
    }

    /** The blizzard is down on his den (FrostyStormFog draws it for whoever is near). */
    public boolean stormy() {
        return entityData.get(STORM);
    }

    public boolean isStuck() {
        return getAttackState() == STUCK;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    public AABB den() {
        BlockPos h = home != null ? home : blockPosition();
        return new AABB(h.getX() + arena[0], h.getY() + arena[1], h.getZ() + arena[2],
                h.getX() + arena[3] + 1, h.getY() + arena[4] + 1, h.getZ() + arena[5] + 1);
    }

    public boolean inDen(Vec3 p) {
        return den().contains(p);
    }

    boolean foe(LivingEntity e) {
        return e != this && e.isAlive() && !FFAllies.ofTheKing(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ------------------------------------------------------------------------------------------------ the bar
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!isDormant() && !isDeadOrDying()) {
            bossEvent.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    private void tickBossBar() {
        if (isDormant() || isDeadOrDying()) {
            bossEvent.removeAllPlayers();
            return;
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());
        bossEvent.setColor(secondHalf ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.WHITE);
        if (level() instanceof ServerLevel s) {
            for (ServerPlayer p : s.getPlayers(p -> p.distanceToSqr(this) < 40.0D * 40.0D)) {
                bossEvent.addPlayer(p);
            }
            for (ServerPlayer p : List.copyOf(bossEvent.getPlayers())) {
                if (p.distanceToSqr(this) >= 56.0D * 56.0D || p.level() != level()) {
                    bossEvent.removePlayer(p);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ his time
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            breathClient = getAttackState() == BREATH ? breathClient + 1 : 0;
            return;
        }
        if (home == null) {
            home = blockPosition();
        }
        if (isDormant()) {
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            if (tickCount % 5 == 0) {
                Player near = nearestFoe();
                if (near != null && !com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.holdsBack(this)) {
                    awaken(near);
                }
            }
        } else {
            beats();
            shoveOut();
            storm();
            fallShards();
            fadeRime();
            tether();
        }
        tickBossBar();
    }

    /** Nobody stands inside him (he cannot be pushed; they can): whoever is under his body is shouldered out. */
    private void shoveOut() {
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().deflate(0.3D, 0.0D, 0.3D),
                this::foe)) {
            Vec3 d = v.position().subtract(position());
            Vec3 flat = new Vec3(d.x, 0.0D, d.z);
            flat = flat.lengthSqr() < 1.0E-4D ? forward() : flat.normalize();
            v.setDeltaMovement(v.getDeltaMovement().add(flat.x * 0.35D, 0.0D, flat.z * 0.35D));
            v.hurtMarked = true;
        }
    }

    @Nullable
    private Player nearestFoe() {
        Player best = null;
        double bd = Double.MAX_VALUE;
        for (Player p : level().players()) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator() || !inDen(p.position())) {
                continue;
            }
            if (isDormant() && (p.distanceToSqr(this) > WAKE * WAKE || !com.jastkub.frozenfortress.BossCutscenes.witness(this, p))) {
                continue;
            }
            double d = p.distanceToSqr(this);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    @Override
    public void gateShut() {
        if (!isDormant() || !isAlive()) {
            return;
        }
        awaken(GateKeeper.shutInWith(this));
    }

    private void awaken(@Nullable LivingEntity by) {
        entityData.set(DORMANT, false);
        introYaw = com.jastkub.frozenfortress.BossCutscenes.postYaw(this);
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        setAttackState(com.jastkub.frozenfortress.BossCutscenes.intro(this, INTRO_T) ? INTRO : AWAKEN);
        emptyRoom = 0;
        rest = 30;
        shards.clear();
        if (by != null) {
            setTarget(by);
        }
    }

    /** Nobody in his den for fifteen seconds: back to his place, curled up again, whole. */
    private void tether() {
        if (home == null || isAttacking()) {
            return;
        }
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || !inDen(t.position()))) {
            setTarget(null);
        }
        emptyRoom = nearestFoe() == null ? emptyRoom + 1 : 0;
        Vec3 h = Vec3.atBottomCenterOf(home);
        if (emptyRoom > GIVE_UP) {
            setTarget(null);
            if (distanceToSqr(h) > 2.5D * 2.5D && emptyRoom < GIVE_UP + 400) {
                getNavigation().moveTo(h.x, h.y, h.z, 1.0D);
            } else {
                sleep();
            }
        } else if (!inDen(position())) {
            getNavigation().moveTo(h.x, h.y, h.z, 1.1D);
        }
    }

    private void sleep() {
        getNavigation().stop();
        if (home != null) {
            moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, getYRot(), 0.0F);
        }
        setHealth(getMaxHealth());
        secondHalf = false;
        calls = 0;
        stormLeft = 0;
        stormNext = 0;
        entityData.set(STORM, false);
        entityData.set(DORMANT, true);
        setNoGravity(false);
        setAttackState(0);
        emptyRoom = 0;
        rime.clear();
    }

    // ------------------------------------------------------------------------------------------------ the beats
    private void beats() {
        int st = getAttackState();
        int t = attackTicks;
        LivingEntity target = getTarget();
        switch (st) {
            case INTRO -> introBeats(t);
            case AWAKEN -> {
                holdStill();
                if (t == 26) {
                    playSound(FFSounds.FROSTMAW_ROAR.get(), 2.6F, 0.77F);
                }
                if (t >= AWAKEN_T) {
                    finish(16);
                }
            }
            case COMBO -> {
                holdStill();
                // he turns with you up to the late one's last moment - it is aimed where you went, not where you were
                if (target != null && t < 42) {
                    face(target, t < 26 ? 30.0F : 14.0F);
                }
                if (t == 4 || t == 15) {
                    playSound(FFSounds.FROSTMAW_GROWL.get(), 2.0F, 0.82F);
                }
                if (t == 28) {
                    playSound(FFSounds.FROSTMAW_ROAR.get(), 2.2F, 0.90F);        // reared back: the third is coming
                }
                for (int k = 0; k < COMBO_HITS.length; k++) {
                    if (t == COMBO_HITS[k]) {
                        bite(k == 2);
                    }
                }
                if (t >= COMBO_T) {
                    finish(secondHalf ? 16 : 24);
                }
            }
            case SWEEP -> {
                holdStill();
                if (t == 4) {
                    playSound(FFSounds.FROSTMAW_GROWL.get(), 1.8F, 0.90F);
                }
                if (t == SWEEP_HIT) {
                    sweep();
                }
                if (t >= SWEEP_T) {
                    finish(14);
                }
            }
            case LEAP -> leap(t, target);
            case STUCK -> {
                holdStill();
                if (t % 10 == 0) {
                    playSound(FFSounds.FROSTMAW_GROWL.get(), 1.6F, 0.72F + random.nextFloat() * 0.1F);
                    playSound(SoundEvents.GLASS_HIT, 1.2F, 0.6F);
                }
                if (t >= stuckFor) {
                    setAttackState(UNSTUCK);
                }
            }
            case UNSTUCK -> {
                holdStill();
                if (t == UNSTUCK_THROW) {
                    // torn free - the ice off his muzzle, and whoever was at his head thrown back
                    playSound(FFSounds.ICE_SHATTER.get(), 2.0F, 0.7F);
                    playSound(FFSounds.FROSTMAW_ROAR.get(), 2.4F, 0.82F);
                    fx("aurochs_crash", head(2.0D), 1.6F, 20);
                    for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5D),
                            this::foe)) {
                        v.hurt(damageSources().mobAttack(this), SLAM_DMG);
                        throwOff(v, 1.3D, 0.5D);
                    }
                }
                if (t >= UNSTUCK_T) {
                    finish(12);
                }
            }
            case BREATH -> {
                holdStill();
                if (target != null) {
                    face(target, 6.0F);
                }
                if (t == 4) {
                    playSound(FFSounds.FROSTMAW_GROWL.get(), 2.0F, 0.82F);
                }
                if (t == 10) {
                    playSound(FFSounds.FROSTMAW_BREATH.get(), 2.6F, 0.82F);
                }
                if (t >= 10 && t <= 40 && t % 4 == 0) {
                    breathe(t % 8 == 0);
                }
                if (t >= BREATH_T) {
                    finish(18);
                }
            }
            case HOWL -> {
                holdStill();
                if (t == HOWL_CALL) {
                    playSound(FFSounds.FROSTMAW_HOWL.get(), 3.0F, 0.77F);
                    callPack(target);
                }
                if (t >= HOWL_T) {
                    finish(10);
                }
            }
            case CLAW -> {
                holdStill();
                if (target != null && t < CLAW_HITS[1] - 3) {
                    face(target, t < CLAW_HITS[0] ? 20.0F : 12.0F);
                }
                if (t == 3) {
                    playSound(FFSounds.FROSTMAW_GROWL.get(), 2.0F, 0.85F);
                }
                if (t == CLAW_HITS[0] || t == CLAW_HITS[1]) {
                    claw(t == CLAW_HITS[0]);
                }
                if (t >= CLAW_T) {
                    finish(secondHalf ? 14 : 20);
                }
            }
            case RIME_PATH -> {
                holdStill();
                if (target != null && t < PATH_SLAM - 3) {
                    face(target, 10.0F);
                }
                if (t == 8) {
                    playSound(FFSounds.FROSTMAW_ROAR.get(), 2.6F, 0.80F);
                }
                if (t == PATH_SLAM) {
                    rimePath(target);
                }
                if (t >= PATH_T) {
                    finish(secondHalf ? 14 : 22);
                }
            }
            case MANE -> {
                holdStill();
                if (t == 6) {
                    playSound(SoundEvents.WOLF_SHAKE, 2.6F, 0.45F);
                    playSound(FFSounds.FROSTMAW_GROWL.get(), 1.8F, 0.75F);
                }
                if (t == MANE_THROW) {
                    throwMane();
                }
                if (t == 28) {
                    playSound(FFSounds.FROSTMAW_HOWL.get(), 2.4F, 0.95F);
                }
                if (t >= MANE_T) {
                    finish(16);
                }
            }
            case BLIZZARD -> {
                holdStill();
                if (t == 22) {
                    playSound(FFSounds.FROSTMAW_HOWL.get(), 3.5F, 0.67F);
                }
                if (t == BLIZZARD_STORM) {
                    stormLeft = STORM_T;
                    entityData.set(STORM, true);
                    playSound(SoundEvents.ELYTRA_FLYING, 2.0F, 0.5F);
                }
                if (t >= BLIZZARD_T) {
                    finish(10);
                }
            }
            default -> {
            }
        }
    }

    private void introBeats(int t) {
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        if (t == INTRO_EYES) {
            playSound(FFSounds.FROSTMAW_GROWL.get(), 1.6F, 0.72F);
        }
        if (t == INTRO_RISE + 8 || t == INTRO_RISE + 18) {
            playSound(FFSounds.ICE_AUROCHS_STEP.get(), 1.6F, 0.7F);
        }
        if (t == INTRO_SHAKE) {
            fx("snow_burst", position().add(0.0D, 1.8D, 0.0D), 2.2F, 20);
            playSound(SoundEvents.SNOW_BREAK, 2.0F, 0.6F);
        }
        if (t == INTRO_HOWL + 6) {
            playSound(FFSounds.FROSTMAW_HOWL.get(), 3.5F, 0.72F);
            if (calls == 0) {
                calls = 1;                                     // his howl as he wakes is the first call
                callPack(getTarget());
            }
        }
        if (t >= INTRO_T) {
            finish(16);
        }
    }

    void finish(int after) {
        setAttackState(0);
        setNoGravity(false);
        rest = after;
    }

    private void holdStill() {
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
    }

    private void face(LivingEntity target, float step) {
        float want = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(getYRot(), want, step);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private Vec3 forward() {
        return Vec3.directionFromRotation(0.0F, getYRot());
    }

    /** A point before his muzzle (he is 2.2 hounds: the jaws come 2.8 blocks ahead of his middle). */
    private Vec3 head(double ahead) {
        return position().add(forward().scale(ahead));
    }

    private void fx(String kind, Vec3 at, float size, int life) {
        AttackFxEntity.spawn(level(), kind, at, yBodyRot, size, life, this);
    }

    private void throwOff(LivingEntity v, double h, double up) {
        Vec3 d = v.position().subtract(position());
        Vec3 flat = new Vec3(d.x, 0.0D, d.z);
        flat = flat.lengthSqr() < 1.0E-4D ? forward() : flat.normalize();
        v.setDeltaMovement(flat.x * h, up, flat.z * h);
        v.hurtMarked = true;
    }

    // ------------------------------------------------------------------------------------------------ his blows
    /** The jaws: a bite in front of him, out to five blocks in a cone of seventy degrees. */
    private void bite(boolean late) {
        playSound(FFSounds.FROSTMAW_BITE.get(), 2.4F, late ? 0.5F : 0.65F);
        Vec3 f = forward();
        Vec3 c = position().add(0.0D, 1.2D, 0.0D);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4.5D, 1.0D, 4.5D),
                this::foe)) {
            Vec3 d = v.position().add(0.0D, v.getBbHeight() * 0.5D, 0.0D).subtract(c);
            double dist = Math.hypot(d.x, d.z);
            if (dist > (late ? 6.0D : 5.0D) || dist < 0.01D) {
                continue;
            }
            double cos = (d.x * f.x + d.z * f.z) / dist;
            if (cos < Math.cos(Math.toRadians(35.0D)) && dist > 2.2D) {
                continue;
            }
            if (v.hurt(damageSources().mobAttack(this), late ? LATE_BITE_DMG : BITE_DMG)) {
                rime(v, 1);
                if (late) {
                    throwOff(v, 0.9D, 0.35D);
                    v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2), this);
                }
            }
        }
        if (late) {
            fx("overseer_sweep", head(2.4D), 1.6F, 12);
        }
    }

    /** The hind quarters slung round: whoever is at his sides and behind him, thrown out of it. */
    private void sweep() {
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.2F, 0.5F);
        fx("overseer_sweep", position().subtract(forward().scale(1.5D)), 2.0F, 12);
        Vec3 f = forward();
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4.0D, 1.0D, 4.0D),
                this::foe)) {
            Vec3 d = v.position().subtract(position());
            double dist = Math.hypot(d.x, d.z);
            if (dist > 6.0D) {
                continue;
            }
            if (dist > 1.0E-3D && (d.x * f.x + d.z * f.z) / dist > 0.5D) {
                continue;                                         // before his face: the jaws' business, not this
            }
            if (v.hurt(damageSources().mobAttack(this), SWEEP_DMG)) {
                throwOff(v, 1.5D, 0.45D);
            }
        }
    }

    /** The breath: a gust of frost out to eight blocks before him, about fifty degrees wide. */
    private void breathe(boolean rimes) {
        Vec3 f = forward();
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8.5D, 2.0D, 8.5D),
                this::foe)) {
            Vec3 d = v.position().subtract(position());
            double dist = Math.hypot(d.x, d.z);
            if (dist > 9.0D || dist < 0.01D || (d.x * f.x + d.z * f.z) / dist < Math.cos(Math.toRadians(27.0D))) {
                continue;
            }
            if (v.hurt(damageSources().freeze(), BREATH_DMG)) {
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), this);
                if (rimes) {
                    rime(v, 1);
                }
            }
        }
    }

    /** His right paw's rake (from his right across to his left), or his left's: wide before him, out to five. */
    private void claw(boolean right) {
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.2F, 0.55F);
        playSound(FFSounds.FROSTMAW_BITE.get(), 1.8F, 0.95F);
        fx(right ? "frosty_claw_r" : "frosty_claw_l", position(), 1.5F, 12);
        Vec3 f = forward();
        // raked aside: the right paw throws to his left, the left to his right
        Vec3 aside = right ? new Vec3(f.z, 0.0D, -f.x) : new Vec3(-f.z, 0.0D, f.x);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4.5D, 1.0D, 4.5D),
                this::foe)) {
            Vec3 d = v.position().subtract(position());
            double dist = Math.hypot(d.x, d.z);
            if (dist > 5.2D || dist < 0.01D) {
                continue;
            }
            if ((d.x * f.x + d.z * f.z) / dist < Math.cos(Math.toRadians(62.0D)) && dist > 2.2D) {
                continue;
            }
            if (v.hurt(damageSources().mobAttack(this), CLAW_DMG)) {
                rime(v, 1);
                v.setDeltaMovement(aside.x * 0.9D + f.x * 0.3D, 0.3D, aside.z * 0.9D + f.z * 0.3D);
                v.hurtMarked = true;
            }
        }
    }

    /** Both paws down: the floor splits from them at `target` (and either side of it, in his second half), and the
     *  ice comes up along each split from his paws outward, a block a tick, growing as it goes (IceSpikeEntity). */
    private void rimePath(@Nullable LivingEntity target) {
        playSound(FFSounds.ICE_AUROCHS_CRASH.get(), 2.6F, 0.7F);
        playSound(FFSounds.ICE_SHATTER.get(), 2.0F, 0.5F);
        fx("overseer_shockwave", head(2.6D), 1.0F, 16);
        float base = getYRot();
        if (target != null) {
            base = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        }
        float[] fan = secondHalf ? new float[]{-26.0F, 0.0F, 26.0F} : new float[]{0.0F};
        for (float off : fan) {
            float yaw = base + off;
            Vec3 dir = Vec3.directionFromRotation(0.0F, yaw);
            Vec3 from = position().add(dir.scale(2.6D));
            for (int k = 0; k < PATH_LEN; k++) {
                Vec3 at = from.add(dir.scale(k));
                double y = floorY(at);
                if (Double.isNaN(y) || !inDen(at)) {
                    break;                                     // a wall, a drop, the den's end: the split stops there
                }
                int delay = PATH_WARN + k;
                AttackFxEntity.spawn(level(), "frosty_crack", new Vec3(at.x, y + 0.01D, at.z), yaw, 1.0F, delay + 6, this);
                level().addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity(level(), this,
                        at.x, y, at.z, PATH_DMG, delay, 0.8F + 0.6F * k / PATH_LEN));
            }
        }
    }

    /** The floor at `p` near his own height: the first open cell with something to stand on, two up to three down
     *  (NaN: none - a wall in the way, or a drop). */
    private double floorY(Vec3 p) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos b = BlockPos.containing(p.x, getY() + dy, p.z);
            if (level().getBlockState(b).getCollisionShape(level(), b).isEmpty()
                    && !level().getBlockState(b.below()).getCollisionShape(level(), b.below()).isEmpty()) {
                return b.getY();
            }
        }
        return Double.NaN;
    }

    /** The ice of his mane up off him: rings on the floor - on each of them, where each is going, and round about -
     *  the shards coming down into them a few at a time. */
    private void throwMane() {
        playSound(FFSounds.ICE_SHATTER.get(), 2.4F, 0.8F);
        playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.6F);
        fx("snow_burst", position().add(0.0D, 3.0D, 0.0D), 2.4F, 20);
        List<Vec3> at = new java.util.ArrayList<>();
        for (Player p : level().players()) {
            if (foe(p) && inDen(p.position())) {
                at.add(p.position());
                at.add(p.position().add(p.getDeltaMovement().multiply(14.0D, 0.0D, 14.0D)));
            }
        }
        int round = secondHalf ? 7 : 4;
        for (int k = 0; k < round; k++) {
            double a = random.nextDouble() * Math.PI * 2.0D, r = 3.0D + random.nextDouble() * 7.0D;
            at.add(position().add(Math.cos(a) * r, 0.0D, Math.sin(a) * r));
        }
        long now = level().getGameTime();
        for (int k = 0; k < at.size(); k++) {
            Vec3 p = at.get(k);
            double y = floorY(p);
            if (Double.isNaN(y) || !inDen(p)) {
                continue;
            }
            shards.add(new Shard(new Vec3(p.x, y, p.z), now + (k % 3) * 5L));
        }
    }

    private void fallShards() {
        if (shards.isEmpty()) {
            return;
        }
        long now = level().getGameTime();
        shards.removeIf(sh -> {
            if (!sh.shown && now >= sh.start) {
                sh.shown = true;
                AttackFxEntity.spawn(level(), "frosty_shard", sh.at, random.nextFloat() * 360.0F, 1.0F, 30, this);
            }
            if (now < sh.start + SHARD_FALL) {
                return false;
            }
            level().playSound(null, sh.at.x, sh.at.y, sh.at.z, FFSounds.ICE_SHATTER.get(), getSoundSource(), 1.4F,
                    0.9F + random.nextFloat() * 0.3F);
            for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(sh.at.x - SHARD_R, sh.at.y - 1.0D, sh.at.z - SHARD_R, sh.at.x + SHARD_R, sh.at.y + 2.5D,
                            sh.at.z + SHARD_R), this::foe)) {
                if (Math.hypot(v.getX() - sh.at.x, v.getZ() - sh.at.z) <= SHARD_R
                        && v.hurt(damageSources().mobAttack(this), SHARD_DMG)) {
                    rime(v, 1);
                    v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), this);
                }
            }
            return true;
        });
    }

    // ------------------------------------------------------------------------------------------------ the rime
    private void rime(LivingEntity v, int n) {
        if (!(v instanceof Player p)) {
            return;
        }
        UUID id = p.getUUID();
        int r = Math.min(RIME_MAX, rime.getOrDefault(id, 0) + n);
        rime.put(id, r);
        rimeAt.put(id, level().getGameTime());
        p.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 100, Math.min(3, r / 2)), this);
        if (r >= RIME_MAX) {
            // FROZEN THROUGH: fixed where they stand - and he comes for them
            rime.put(id, 0);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FREEZE_T, 9), this);
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, FREEZE_T, 128), this);
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, FREEZE_T, 3), this);
            p.setTicksFrozen(Math.max(p.getTicksFrozen(), p.getTicksRequiredToFreeze() + FREEZE_T));
            playSound(FFSounds.ICE_SHATTER.get(), 2.0F, 1.4F);
            if (level() instanceof ServerLevel s) {
                s.sendParticles(com.jastkub.frozenfortress.registry.FFParticles.ICE_SHARD.get(), p.getX(), p.getY() + 1.0D,
                        p.getZ(), 30, 0.4D, 0.8D, 0.4D, 0.1D);
            }
            pounceOn = id;
        }
    }

    private void fadeRime() {
        long now = level().getGameTime();
        rime.entrySet().removeIf(e -> {
            Long at = rimeAt.get(e.getKey());
            if (at != null && now - at > RIME_FADE) {
                rimeAt.put(e.getKey(), now);
                e.setValue(e.getValue() - 1);
            }
            return e.getValue() <= 0;
        });
    }

    // ------------------------------------------------------------------------------------------------ the leap
    void beginLeap(LivingEntity target) {
        leapAt = target.getUUID();
        leapHit = false;
        setAttackState(LEAP);
    }

    private void leap(int t, @Nullable LivingEntity target) {
        if (t < LEAP_OFF) {
            holdStill();
            if (target != null) {
                face(target, 24.0F);
            }
            if (t == 2) {
                playSound(FFSounds.FROSTMAW_GROWL.get(), 2.6F, 0.67F);   // THE TELL: the growl, low
            }
            if (t == 6) {
                fx("glint", position().add(forward().scale(2.6D)).add(0.0D, 2.4D, 0.0D), 2.0F, 10);    // the eyes flare
            }
            if (t == LEAP_OFF - 1) {
                // where you stood a moment ago, and a little on where you were going - not where you will be
                Vec3 to = target != null ? target.position().add(target.getDeltaMovement().multiply(4.0D, 0.0D, 4.0D))
                        : head(6.0D);
                Vec3 back = to.subtract(position());
                double len = Math.hypot(back.x, back.z);
                // he lands with his jaws (2.6 ahead of his middle) where they stood
                Vec3 dir = len > 0.01D ? new Vec3(back.x / len, 0.0D, back.z / len) : forward();
                leapFrom = position();
                leapTo = new Vec3(to.x, to.y, to.z).subtract(dir.scale(Math.min(2.6D, len)));
                if (!inDen(leapTo)) {
                    AABB d = den().deflate(2.0D);
                    leapTo = new Vec3(Mth.clamp(leapTo.x, d.minX, d.maxX), leapTo.y, Mth.clamp(leapTo.z, d.minZ, d.maxZ));
                }
            }
            return;
        }
        if (t == LEAP_OFF) {
            playSound(FFSounds.FROSTMAW_ROAR.get(), 3.0F, 0.77F);
            setNoGravity(true);
        }
        if (t < LEAP_LAND) {
            // the arc: from where he crouched to where he lands, up over the middle
            double f = (t + 1 - LEAP_OFF) / (double) (LEAP_LAND - LEAP_OFF);
            double h = 3.0D + 0.12D * leapFrom.distanceTo(leapTo);
            Vec3 want = leapFrom.add(leapTo.subtract(leapFrom).scale(f)).add(0.0D, 4.0D * h * f * (1.0D - f), 0.0D);
            setDeltaMovement(Vec3.ZERO);
            move(MoverType.SELF, want.subtract(position()));
            Vec3 d = leapTo.subtract(leapFrom);
            if (d.horizontalDistanceSqr() > 0.01D) {
                float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
                setYRot(yaw);
                yBodyRot = yaw;
                yHeadRot = yaw;
            }
            return;
        }
        if (t == LEAP_LAND) {
            setNoGravity(false);
            playSound(FFSounds.ICE_AUROCHS_CRASH.get(), 2.6F, 0.6F);
            playSound(FFSounds.ICE_SHATTER.get(), 2.2F, 0.6F);
            Vec3 jaws = head(2.6D);
            fx("overseer_shockwave", position(), 1.5F, 20);
            for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5.0D, 1.5D, 5.0D),
                    this::foe)) {
                double dj = Math.hypot(v.getX() - jaws.x, v.getZ() - jaws.z);
                double dm = Math.hypot(v.getX() - getX(), v.getZ() - getZ());
                if (dj < 2.2D) {
                    if (v.hurt(damageSources().mobAttack(this), LEAP_DMG)) {
                        leapHit = true;
                        rime(v, 2);
                        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3), this);
                    }
                } else if (dm < 5.0D) {
                    if (v.hurt(damageSources().mobAttack(this), SLAM_DMG)) {
                        throwOff(v, 0.8D, 0.4D);
                    }
                }
                if (dm < 2.4D) {
                    throwOff(v, 1.1D, 0.45D);                  // nobody is left standing under him
                }
            }
            if (!leapHit) {
                // MISSED: his jaws in the ice - the window
                fx("aurochs_crash", jaws, 1.8F, 24);
                playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.5F);
                stuckFor = secondHalf ? STUCK_T_TWO : STUCK_T;
                setAttackState(STUCK);
            }
            return;
        }
        holdStill();
        if (t >= LEAP_T) {
            finish(secondHalf ? 14 : 22);
        }
    }

    // ------------------------------------------------------------------------------------------------ the pack, the storm
    private void callPack(@Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        AABB d = den();
        // the howl drives on those already out
        List<FrostmawEntity> out = s.getEntitiesOfClass(FrostmawEntity.class, d, FrostmawEntity::isAlive);
        for (FrostmawEntity m : out) {
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
            m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
            if (target != null) {
                m.setTarget(target);
            }
        }
        int alive = out.size();
        for (int k = 0; k < (secondHalf ? 3 : 2) && alive < PACK_MAX; k++) {
            FrostmawEntity m = FFEntities.FROSTMAW.get().create(s);
            if (m == null) {
                continue;
            }
            // out of the dark at the den's far edges
            double a = random.nextDouble() * Math.PI * 2.0D;
            double rx = (d.maxX - d.minX) * 0.5D - 2.5D, rz = (d.maxZ - d.minZ) * 0.5D - 2.5D;
            Vec3 c = d.getCenter();
            double x = c.x + Math.cos(a) * rx, z = c.z + Math.sin(a) * rz;
            m.moveTo(x, getY(), z, random.nextFloat() * 360.0F, 0.0F);
            if (!s.noCollision(m)) {
                m.moveTo(getX() + Math.cos(a) * 5.0D, getY(), getZ() + Math.sin(a) * 5.0D, 0.0F, 0.0F);
            }
            m.setPersistenceRequired();
            if (target != null) {
                m.setTarget(target);
            }
            s.addFreshEntity(m);
            AttackFxEntity.spawn(s, AttackFxEntity.SNOW_BURST, m.position().add(0.0D, 0.4D, 0.0D),
                    random.nextFloat() * 360.0F, 1.6F, 20, this);
            s.sendParticles(com.jastkub.frozenfortress.registry.FFParticles.ICE_SHARD.get(), m.getX(), m.getY() + 1.0D,
                    m.getZ(), 24, 0.6D, 0.6D, 0.6D, 0.08D);
            alive++;
        }
    }

    private void storm() {
        if (stormLeft > 0) {
            stormLeft--;
            if (stormLeft == 0) {
                entityData.set(STORM, false);
                stormNext = STORM_EVERY;
            }
            if (stormLeft % 50 == 0 && level() instanceof ServerLevel s) {
                // the storm's own voice, from all round the den
                AABB d = den();
                s.playSound(null, d.getCenter().x + (random.nextDouble() - 0.5D) * 20.0D, getY() + 4.0D,
                        d.getCenter().z + (random.nextDouble() - 0.5D) * 20.0D, SoundEvents.ELYTRA_FLYING,
                        getSoundSource(), 1.4F, 0.4F + random.nextFloat() * 0.2F);
            }
        } else if (secondHalf && stormNext > 0) {
            stormNext--;
        }
    }

    boolean stormDue() {
        return secondHalf && stormLeft <= 0 && stormNext <= 0;
    }

    boolean callDue() {
        return calls < CALL_AT.length && getHealth() <= getMaxHealth() * CALL_AT[calls];
    }

    // ------------------------------------------------------------------------------------------------ hurt, death
    @Override
    protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
        return ceiling.apply(this, source, super.getDamageAfterMagicAbsorb(source, amount), getAttackState() == STUCK);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (isDormant()) {
                if (!level().isClientSide && source.getEntity() instanceof Player p && !p.isCreative()) {
                    awaken(p);
                }
                return false;
            }
            int st = getAttackState();
            if (st == INTRO) {
                return false;
            }
            if (st == AWAKEN) {
                amount *= 0.5F;
            } else if (st == STUCK) {
                amount *= STUCK_TAKEN;                         // his jaws in the ice: open
            }
        }
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && !secondHalf && isAlive() && getHealth() < getMaxHealth() * 0.5F) {
            secondHalf = true;
            stormNext = 0;                                     // the first storm at once, when what he does is done
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        bossEvent.removeAllPlayers();
        entityData.set(STORM, false);
        setNoGravity(false);
        super.die(source);
        if (!level().isClientSide) {
            // the way on out of his den rises (its exit gate)
            com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.keeperFell(level(), this);
        }
    }

    @Override
    protected int getDeathDuration() {
        return DEATH_T + 36;
    }

    /** He lies where he fell and fades over his last ticks (11.10.2026: no breaking into ice). */
    @Override
    public int fadeTicks() {
        return 30;
    }

    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (level().isClientSide) {
            return;
        }
        int t = deathTime - DEATH_LAG;
        if (t == 8) {
            playSound(FFSounds.FROSTMAW_DEATH.get(), 2.6F, 0.72F);
        }
        if (t == DEATH_FALL) {
            playSound(FFSounds.ICE_AUROCHS_CRASH.get(), 2.0F, 0.5F);
        }
        if (t == DEATH_BREATH) {
            playSound(FFSounds.FROSTMAW_HOWL.get(), 1.4F, 0.62F);      // the last of him: a howl gone to a breath
        }
    }

    // ------------------------------------------------------------------------------------------------ the breath (drawn)
    @Override
    public float breathPower(float partialTick) {
        float t = breathClient + partialTick;
        if (getAttackState() != BREATH || t < 9.0F) {
            return 0.0F;
        }
        return Mth.clamp((t - 9.0F) / 4.0F, 0.0F, 1.0F) * Mth.clamp((45.0F - t) / 5.0F, 0.0F, 1.0F);
    }

    @Override
    public float breathReach(float partialTick) {
        return Mth.clamp((breathClient + partialTick - 9.0F) / 6.0F, 0.0F, 1.0F);
    }

    // ------------------------------------------------------------------------------------------------ saved
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Dormant", isDormant());
        tag.putBoolean("SecondHalf", secondHalf);
        tag.putInt("Calls", calls);
        tag.putIntArray("Arena", arena);
        if (home != null) {
            tag.putIntArray("Home", new int[]{home.getX(), home.getY(), home.getZ()});
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Dormant")) {
            entityData.set(DORMANT, tag.getBoolean("Dormant"));
        }
        secondHalf = tag.getBoolean("SecondHalf");
        calls = tag.getInt("Calls");
        if (tag.getIntArray("Arena").length == 6) {
            arena = tag.getIntArray("Arena");
        }
        int[] h = tag.getIntArray("Home");
        if (h.length == 3) {
            home = new BlockPos(h[0], h[1], h[2]);
        }
    }

    // ------------------------------------------------------------------------------------------------ looks, sounds
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            state.getController().setTransitionLength(getAttackState() == INTRO ? 0 : 4);
            boolean gait = !isDeadOrDying() && !isDormant() && getAttackState() == 0;
            state.getController().setAnimationSpeed(gait ? GAIT : 1.0D);
            if (isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (isDormant()) {
                return state.setAndContinue(DORMANT_ANIM);
            }
            switch (getAttackState()) {
                case INTRO: return state.setAndContinue(INTRO_ANIM);
                case AWAKEN: return state.setAndContinue(AWAKEN_ANIM);
                case COMBO: return state.setAndContinue(COMBO_ANIM);
                case SWEEP: return state.setAndContinue(SWEEP_ANIM);
                case LEAP: return state.setAndContinue(LEAP_ANIM);
                case STUCK: return state.setAndContinue(STUCK_ANIM);
                case UNSTUCK: return state.setAndContinue(UNSTUCK_ANIM);
                case BREATH: return state.setAndContinue(BREATH_ANIM);
                case HOWL: return state.setAndContinue(HOWL_ANIM);
                case BLIZZARD: return state.setAndContinue(BLIZZARD_ANIM);
                case CLAW: return state.setAndContinue(CLAW_ANIM);
                case RIME_PATH: return state.setAndContinue(PATH_ANIM);
                case MANE: return state.setAndContinue(MANE_ANIM);
                default: break;
            }
            if (!FrostServantEntity.going(state)) {
                return state.setAndContinue(IDLE);
            }
            double v = getDeltaMovement().horizontalDistance();
            return state.setAndContinue(v > 0.22D ? RUN : stalking() ? PROWL : WALK);
        }));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && !isDormant() && hurtTime > 0 && !isAttacking()
                        ? state.setAndContinue(HURT) : PlayState.STOP));
    }

    /** Circling someone close, low (the prowl), rather than going somewhere. */
    private boolean stalking() {
        LivingEntity t = getTarget();
        return t != null && t.distanceToSqr(this) < 12.0D * 12.0D;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isDormant() ? null : FFSounds.FROSTMAW_GROWL.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    public float getVoicePitch() {
        return 0.75F + random.nextFloat() * 0.1F;     // (a real wolf's voice, a size down)
    }

    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.FROSTMAW_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.FROSTMAW_DEATH.get();
    }

    // ================================================================================================ the brain
    /** Stalks round them at arm's length (wider, out of sight, in the storm), and picks his blow. */
    static class FrostyGoal extends Goal {
        private final FrostyEntity mob;
        private int comboCd, sweepCd, leapCd, breathCd, clawCd, pathCd = 60, maneCd = 120, repath;
        private int last;
        private float orbit = 1.0F;

        FrostyGoal(FrostyEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !mob.isDormant() && mob.getTarget() != null && mob.getTarget().isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            comboCd = Math.max(0, comboCd - 1);
            sweepCd = Math.max(0, sweepCd - 1);
            leapCd = Math.max(0, leapCd - 1);
            breathCd = Math.max(0, breathCd - 1);
            clawCd = Math.max(0, clawCd - 1);
            pathCd = Math.max(0, pathCd - 1);
            maneCd = Math.max(0, maneCd - 1);
            if (mob.isAttacking()) {
                return;
            }
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            // the pack, the storm: as soon as what he does is done
            if (mob.callDue()) {
                mob.calls++;
                mob.getNavigation().stop();
                mob.setAttackState(HOWL);
                return;
            }
            if (mob.stormDue()) {
                mob.getNavigation().stop();
                mob.setAttackState(BLIZZARD);
                return;
            }
            // frozen through by his rime: on them now
            if (mob.pounceOn != null) {
                UUID id = mob.pounceOn;
                mob.pounceOn = null;
                if (mob.level() instanceof ServerLevel s && s.getEntity(id) instanceof LivingEntity v && v.isAlive()
                        && mob.inDen(v.position())) {
                    mob.setTarget(v);
                    mob.beginLeap(v);
                    leapCd = 60;
                    return;
                }
            }
            double dist = Math.hypot(target.getX() - mob.getX(), target.getZ() - mob.getZ());
            boolean storm = mob.stormy();
            if (mob.rest > 0) {
                mob.rest -= mob.secondHalf ? 2 : 1;
                stalk(target, dist, storm);
                return;
            }
            Vec3 f = mob.forward();
            Vec3 d = target.position().subtract(mob.position());
            double cos = dist > 0.01D ? (d.x * f.x + d.z * f.z) / dist : 1.0D;
            boolean sighted = mob.hasLineOfSight(target);
            // someone behind him
            if (sweepCd <= 0 && behind(5.5D)) {
                mob.setAttackState(SWEEP);
                sweepCd = 70;
                return;
            }
            if (storm) {
                // in the storm he only comes out of it to leap - or sends his mane down out of the white
                if (leapCd <= 0 && sighted && dist > 5.0D) {
                    mob.beginLeap(target);
                    leapCd = mob.secondHalf ? 50 : 70;
                    return;
                }
                if (maneCd <= 0 && mob.random.nextFloat() < 0.02F) {
                    mob.setAttackState(MANE);
                    maneCd = 260;
                    return;
                }
                stalk(target, dist, true);
                return;
            }
            // what he could do from here - and one of them, by weight, not the one he did last if there is another
            //
            List<int[]> can = new java.util.ArrayList<>();
            if (comboCd <= 0 && dist < 4.8D && cos > 0.3D) {
                can.add(new int[]{COMBO, 3});
            }
            if (clawCd <= 0 && dist < 4.6D && cos > 0.2D) {
                can.add(new int[]{CLAW, 3});
            }
            if (breathCd <= 0 && sighted && dist > 4.0D && dist < 8.5D && cos > 0.6D) {
                can.add(new int[]{BREATH, 2});
            }
            if (pathCd <= 0 && sighted && dist > 5.0D && dist < 16.0D) {
                can.add(new int[]{RIME_PATH, 2});
            }
            if (leapCd <= 0 && sighted && dist > 7.0D && dist < 20.0D) {
                can.add(new int[]{LEAP, 2});
            }
            if (maneCd <= 0 && dist < 14.0D) {
                can.add(new int[]{MANE, 1});
            }
            if (can.size() > 1) {
                can.removeIf(c -> c[0] == last);
            }
            if (can.isEmpty()) {
                stalk(target, dist, false);
                return;
            }
            int sum = 0;
            for (int[] c : can) {
                sum += c[1];
            }
            int roll = mob.random.nextInt(sum);
            int pick = can.get(0)[0];
            for (int[] c : can) {
                roll -= c[1];
                if (roll < 0) {
                    pick = c[0];
                    break;
                }
            }
            last = pick;
            switch (pick) {
                case COMBO -> comboCd = 50;
                case CLAW -> clawCd = 60;
                case BREATH -> breathCd = 140;
                case RIME_PATH -> pathCd = mob.secondHalf ? 100 : 140;
                case MANE -> maneCd = mob.secondHalf ? 180 : 240;
                default -> leapCd = mob.secondHalf ? 80 : 110;
            }
            if (pick == LEAP) {
                mob.beginLeap(target);
            } else {
                mob.getNavigation().stop();
                mob.setAttackState(pick);
            }
        }

        private boolean behind(double r) {
            Vec3 f = mob.forward();
            for (LivingEntity v : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(r),
                    mob::foe)) {
                Vec3 d = v.position().subtract(mob.position());
                double dist = Math.hypot(d.x, d.z);
                if (dist < r && dist > 0.01D && (d.x * f.x + d.z * f.z) / dist < -0.35D) {
                    return true;
                }
            }
            return false;
        }

        /** Round them: to a point on a ring about them, a little further round each time; closer when the combo is
         *  ready, out at the storm's edge in the storm. */
        private void stalk(LivingEntity target, double dist, boolean storm) {
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--repath > 0 && !mob.getNavigation().isDone()) {
                return;
            }
            repath = 8;
            double r = storm ? STORM_R : comboCd <= 0 ? 3.5D : STALK_R;
            if (mob.random.nextFloat() < 0.02F) {
                orbit = -orbit;                                   // and now and then the other way round
            }
            double a = Math.atan2(mob.getZ() - target.getZ(), mob.getX() - target.getX()) + orbit * 0.6D;
            Vec3 to = new Vec3(target.getX() + Math.cos(a) * r, target.getY(), target.getZ() + Math.sin(a) * r);
            if (!mob.inDen(to)) {
                to = target.position();
            }
            double speed = storm ? 1.2D : dist > 12.0D ? 1.35D : 1.0D;
            mob.getNavigation().moveTo(to.x, to.y, to.z, speed);
        }
    }
}
