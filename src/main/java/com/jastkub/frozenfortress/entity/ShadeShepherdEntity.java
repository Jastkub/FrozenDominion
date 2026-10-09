package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * PASTERZ CIENI - THE SHADE SHEPHERD, the miniboss of the Lightless Chambers (Komory Bez Swiatla): a cave hall with
 * no light in it at all but five campfires, unlit, by its columns - and a flint and steel at the door.
 *
 * <p><b>The fire is the fight.</b> His herd, the SHADES (ShadeEntity), are seen only in a lit fire's light; he puts
 * the fires out; you keep lighting them. He himself is seen in the dark only by what lights itself on him: the pale
 * ram's-skull mask and the slits of its eyes, the clapper of his bell, and a ring of small cold lights round his
 * shoulders - ONE FOR EACH SHADE OF HIS THAT LIVES (ShadeShepherdRenderer hides the rest). So you can always find him,
 * and you can always read how strong he is.
 *
 * <p><b>The herd is his shield.</b> Every shade of his alive takes {@link #SHIELD_PER} of every blow off him, up to
 * {@link #SHIELD_MAX} - thin the herd in the firelight, then go for him. He herds them: he CALLS them (his bell tolled
 * three times - the herd gathers round him, and new ones rise out of the floor's dark where the light is not), and he
 * SENDS them at you (a herdsman's call, the crook levelled at you).
 *
 * <p>His attacks, each told before it lands:
 * <ol>
 *   <li>HOOK (Hak Pasterza) - the crook wound back over his shoulder, the bell jangling ({@link #HOOK_TELL} ticks):
 *   then swept round and down, three blocks out before him - what it catches is dragged in to his feet (and
 *   two of his herd sent at it).</li>
 *   <li>DASH (Smuga Cienia) - from six to
 *   eighteen blocks off: he gathers, low, the bell jangling (0.3 s), and glides at you in eight ticks however far,
 *   leaving his shadow behind him at every one (ShadeAfterimageEntity) - to where you stood, not after you - and ends
 *   it in his hook. Step aside while he glides and the hook finds nothing.</li>
 *   <li>SNUFF (Zdmuchniecie) - turned to the fire you rely on, he draws a long breath (heard all the way, 1.1 s) and
 *   blows: his breath goes out to the fire as a thing of its own (ShadeShepherdGustEntity, fx_shade_gust) and puts it
 *   out with a hiss. It can be stopped: step into its path (it chills you instead) or strike it from the air.</li>
 *   <li>CALL and SEND - the herd, above, in waves.</li>
 *   <li>PULSE (Puls Mroku; his second half only) - the crook raised over his head, the bell tolled twice, slow, and
 *   the dark gathering at his feet; then the crook driven into the floor and a ring of shadow runs out from him to
 *   the far walls (ShadePulseEntity): every fire of his chamber goes out, and whoever does not jump it is struck and
 *   blinded.</li>
 * </ol>
 * <b>The dark feeds him</b>: the more of his chamber's fires are out ({@link #darkness}), the shorter his rests and his waits
 * between attacks, the faster he walks, the harder his hook, the more often he sends his herd and the more of it rises -
 * and the harder and the more often his herd springs (ShadeEntity). A fire he puts out smoulders a while and will not
 * take a spark (ShadeLight.smouldering).
 * <b>Below half his health</b> (his second half) he draws the GREAT BREATH: every fire in the chambers goes out at
 * once - and he tears a DECOY off himself (ShadeShepherdDecoyEntity), a shade wearing his shape: its mask has no light
 * in its eyes, it carries no herd-lights, and in firelight it goes see-through. Every so often after that he tears off
 * another (SPLIT). His herd grows larger, his breath goes to two fires at once, his hook hits harder.
 *
 * <p>He sleeps on one knee over his crook until someone comes into his chambers (Dormant:1b, as every keeper sleeps),
 * keeps to them (his home is the block he was put on), and is the keeper the chambers' door waits on
 * (frozen_dominion:shade_shepherd).
 */
public class ShadeShepherdEntity extends FrostServantEntity implements GateKeeper {

    /** Client only: how far into his shadow-form he is drawn (0 lit by the fires, 1 a shadow) - ShadeShepherdRenderer. */
    public float shadowFade;


    public static final int WAKE = 1, HOOK = 2, SNUFF = 3, CALL = 4, SEND = 5, SPLIT = 6, GREAT = 7, PULSE = 8, DASH = 9,
            INTRO = 10;
    /** HIS ENTRANCE (tools/gen_shade_shepherd.py INTRO_*: the "intro" clip - change one, change both; BossScenes cuts
     *  its shots to them): up off his knee, the bell tolled three times (one sound of three tolls, from BELL), a finger
     *  to his mask - hush - and the hand held out, beckoning. */
    public static final int INTRO_T = 156, INTRO_STIR = 8, INTRO_BELL = 34, INTRO_HUSH = 66, INTRO_BECKON = 98;
    public static final int[] INTRO_TOLLS = {36, 44, 52};
    /** HIS DEATH (tools/gen_shade_shepherd.py DEATH_*: the "death" clip - change one, change both; BossScenes' death
     *  film is cut to them): down on his knees (KNEEL), the herd-lights flung out and gone (MOTES), the mask fallen
     *  (MASK to MASK_LAND), the crook let go, on the stones CROOK_FALL later, its bell ringing (CROOK), and he sinks into
     *  the floor's dark (SINK), gone by SUNK. The clip runs DEATH_LAG behind his deathTime: the controller blends into
     *  it first (three ticks, his). */
    public static final int DEATH_T = 84, DEATH_KNEEL = 20, DEATH_MOTES = 30, DEATH_MASK = 36, DEATH_MASK_LAND = 45,
            DEATH_CROOK = 50, DEATH_CROOK_FALL = 6, DEATH_SINK = 54, DEATH_SUNK = 78, DEATH_LAG = 3;

    // ---- the beats of his clips (tools/gen_shade_shepherd.py - change one, change both)
    static final int WAKE_T = 40, WAKE_BELL = 30;
    static final int HOOK_T = 34, HOOK_TELL = 12, HOOK_SWING = 13, HOOK_HIT = 16, HOOK_PULL = 24;
    static final int SNUFF_T = 44, SNUFF_BLOW = 24;
    /** The bell's sound is three tolls 0.4 s apart from 0.1 s in: played on 10 they fall on 12, 20 and 28. */
    static final int CALL_T = 46, CALL_BELL = 10, CALL_RISE = 30;
    static final int SEND_T = 30, SEND_CALL = 4, SEND_GO = 14;
    static final int SPLIT_T = 36, SPLIT_TEAR = 18;
    static final int GREAT_T = 56, GREAT_BLOW = 28, GREAT_TEAR = 40;
    /** The pulse: gathered from 2 (the bell tolled on 8 and 18), the crook driven down on 30. */
    static final int PULSE_T = 50, PULSE_SLAM = 30, PULSE_TOLL_1 = 8, PULSE_TOLL_2 = 18;
    /** The dash: gathered by 6, the glide 6-14, then his hook's own beats from its tell on, four ticks later. */
    static final int DASH_TELL = 6, DASH_ARRIVE = 14, DASH_SWING = 17, DASH_HIT = 20, DASH_PULL = 28, DASH_T = 38;

    // ---- the numbers of his fight
    static final float HOOK_DMG = 7.0F, HOOK_DMG_TWO = 9.0F;
    /** The hook lands 2.9 blocks out (measured on the clip at its hit, gen_shade_shepherd.py); + a body's half-width. */
    static final double HOOK_REACH = 3.3D, HOOK_CONE = 45.0D;
    /** Hooked, they are dragged to this far before him. */
    static final double HOOK_TO = 1.8D;
    /** The dash: from this far to this far (flat), and it ends this far short of where you stood. */
    static final double DASH_MIN = 6.0D, DASH_MAX = 18.0D, DASH_STOP = 2.2D;
    static final float SHIELD_PER = 0.12F, SHIELD_MAX = 0.48F;
    static final int HERD_CAP = 5, HERD_CAP_TWO = 7;
    /** How far he wakes to (flat), and how far he keeps from where he was put. */
    static final double WAKE_R = 16.0D;
    public static final double LEASH = 34.0D;
    /** The fires of his chambers are looked for this far from home (and kept to his heart's room, if it has one). */
    static final double ROOM = 40.0D;
    /** What his chamber all dark does at most: his rests shortened by half, his waits between attacks by two fifths,
     *  his walk and his hook four tenths up - and his herd's bite six tenths up (ShadeEntity). */
    static final float DARK_REST = 0.5F, DARK_WAIT = 0.4F, DARK_SPEED = 0.4F, DARK_HOOK = 0.4F;
    public static final float DARK_HERD = 0.6F;
    private static final java.util.UUID DARK_SPEED_ID = java.util.UUID.fromString("6d2f5f0e-8f3a-4c1b-9b77-2a51c0d4e913");

    private static final EntityDataAccessor<Boolean> DORMANT =
            SynchedEntityData.defineId(ShadeShepherdEntity.class, EntityDataSerializers.BOOLEAN);
    /** How many of his herd live (for the herd-lights round him). */
    private static final EntityDataAccessor<Integer> HERD =
            SynchedEntityData.defineId(ShadeShepherdEntity.class, EntityDataSerializers.INT);

    private static final String P = "animation.shade_shepherd.";
    private static final RawAnimation DORMANT_ANIM = RawAnimation.begin().thenLoop(P + "dormant");
    private static final RawAnimation WAKE_ANIM = RawAnimation.begin().thenPlayAndHold(P + "wake");
    private static final RawAnimation INTRO_ANIM = RawAnimation.begin().thenPlayAndHold(P + "intro");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation HOOK_ANIM = RawAnimation.begin().thenPlayAndHold(P + "hook");
    private static final RawAnimation SNUFF_ANIM = RawAnimation.begin().thenPlayAndHold(P + "snuff");
    private static final RawAnimation CALL_ANIM = RawAnimation.begin().thenPlayAndHold(P + "call");
    private static final RawAnimation SEND_ANIM = RawAnimation.begin().thenPlayAndHold(P + "send");
    private static final RawAnimation SPLIT_ANIM = RawAnimation.begin().thenPlayAndHold(P + "split");
    private static final RawAnimation GREAT_ANIM = RawAnimation.begin().thenPlayAndHold(P + "great_breath");
    private static final RawAnimation PULSE_ANIM = RawAnimation.begin().thenPlayAndHold(P + "pulse");
    private static final RawAnimation DASH_ANIM = RawAnimation.begin().thenPlayAndHold(P + "dash");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay(P + "hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(P + "death");

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.frozen_dominion.shade_shepherd"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);

    @Nullable
    private BlockPos home;
    private boolean secondHalf;
    /** The great breath is owed (his health has just gone under half). */
    boolean greatNow;
    /** Every campfire of his chambers, lit or not (refreshed now and then). */
    private final List<BlockPos> fires = new ArrayList<>();
    private int firesAge = 1000;
    /** How dark his chamber is: the share of its fires out (0 all burning .. 1 all out). */
    private float dark;
    /** The fires his breath is going to this time. */
    private final List<BlockPos> breathFor = new ArrayList<>();
    private final List<LivingEntity> hooked = new ArrayList<>();
    /** Where his dash ends (set as he leaves). */
    @Nullable
    private Vec3 dashTo;
    private int herdCount;
    private long shieldToldAt = -1000L;
    /** Ticks he waits after an attack before the next (the goal counts them). */
    int rest;

    public ShadeShepherdEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 150;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 240.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DORMANT, true);
        entityData.define(HERD, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new ShepherdGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 14.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));
    }

    // ------------------------------------------------------------------------------------------------ what he is
    public boolean isDormant() {
        return entityData.get(DORMANT);
    }

    /** How many of his herd live (synced; the renderer shows as many herd-lights, up to six). */
    public int herd() {
        return entityData.get(HERD);
    }

    public boolean inSecondHalf() {
        return secondHalf;
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    /** How dark his chamber is now: the share of its fires out, 0 .. 1 (0 if it has none). */
    public float darkness() {
        return dark;
    }

    @Override
    protected boolean isImmobile() {
        int st = getAttackState();
        return super.isImmobile() || isDormant() || st == WAKE || st == INTRO || st == SNUFF || st == CALL
                || st == SPLIT || st == GREAT || st == PULSE;
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
        bossEvent.setColor(secondHalf ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.WHITE);
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
            return;
        }
        if (home == null) {
            home = blockPosition();
        }
        if (++firesAge > 100) {
            firesAge = 0;
            fires.clear();
            fires.addAll(ShadeLight.campfires(level(), Vec3.atCenterOf(home), ROOM));
            // only his own chamber's (his heart's room): not the hearths of the rooms next to it
            AABB room = com.jastkub.frozenfortress.block.entity.FrostHeartBlockEntity.keeperRoom(level(), home);
            if (room != null) {
                fires.removeIf(f -> !room.contains(Vec3.atCenterOf(f)));
            }
        }
        if (tickCount % 10 == 0) {
            dark = fires.isEmpty() ? 0.0F : 1.0F - litFires() / (float) fires.size();
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(DARK_SPEED_ID);
                if (dark > 0.01F && !isDormant()) {
                    speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(DARK_SPEED_ID,
                            "shepherd_dark", DARK_SPEED * dark, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
                }
            }
        }
        if (tickCount % 10 == 0) {
            countHerd();
        }
        if (isDormant()) {
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            Player near = level().getNearestPlayer(getX(), getY(), getZ(), WAKE_R,
                    e -> e instanceof Player p && !p.isCreative() && !p.isSpectator()
                            && Math.abs(p.getY() - getY()) < 6.0D && com.jastkub.frozenfortress.BossCutscenes.witness(this, p));
            if (near != null && !com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.holdsBack(this)) {
                awaken();
                setTarget(near);
            }
        } else {
            beats();
            if (!isAttacking() && distanceToSqr(Vec3.atBottomCenterOf(home)) > LEASH * LEASH) {
                LivingEntity t = getTarget();
                if (t != null && t.distanceToSqr(Vec3.atBottomCenterOf(home)) > (LEASH + 8.0D) * (LEASH + 8.0D)) {
                    setTarget(null);
                }
                getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.0D);
            }
        }
        tickBossBar();
    }


    /** Its gate down a second (BossGateBlockEntity): it wakes now, for whoever it shut in with it. */
    @Override
    public void gateShut() {
        if (!isDormant() || !isAlive()) {
            return;
        }
        net.minecraft.world.entity.player.Player p = GateKeeper.shutInWith(this);
        awaken();
        if (p != null) {
            setTarget(p);
        }
    }

    private void awaken() {
        entityData.set(DORMANT, false);
        rest = 20;
        // his entrance, once for each who sees it (BossCutscenes), shot as a film: the bell tolled, hush, come - or,
        // when everyone here has seen it, the short waking as before
        introYaw = com.jastkub.frozenfortress.BossCutscenes.postYaw(this);
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        setAttackState(com.jastkub.frozenfortress.BossCutscenes.intro(this, INTRO_T) ? INTRO : WAKE);
    }

    /** Which way he faces through his entrance: his post's. */
    private float introYaw;

    /** His entrance's beats (the clip's), and him kept where he slept through it. */
    private void introBeats(int t) {
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        if (t == INTRO_STIR) {
            playSound(FFSounds.SHADE_SHEPHERD_IDLE.get(), 1.2F, 0.9F);
        }
        if (t == INTRO_BELL) {
            playSound(FFSounds.SHADE_SHEPHERD_BELL.get(), 2.4F, 1.0F);      // three tolls: on 36, 44 and 52
            adoptHerd();
        }
        if (t >= INTRO_T) {
            finish(20);
        }
    }

    /** His herd: the shades that are his, alive, about his chambers. */
    List<ShadeEntity> herdList() {
        BlockPos c = home != null ? home : blockPosition();
        return level().getEntitiesOfClass(ShadeEntity.class, new AABB(c).inflate(ROOM + 8.0D, 12.0D, ROOM + 8.0D),
                s -> s.isAlive() && s.ownedBy(this));
    }

    private void countHerd() {
        herdCount = herdList().size();
        entityData.set(HERD, Math.min(herdCount, 6));
    }

    /** Every shade of the chambers with no shepherd becomes his (on waking). */
    private void adoptHerd() {
        BlockPos c = home != null ? home : blockPosition();
        for (ShadeEntity s : level().getEntitiesOfClass(ShadeEntity.class,
                new AABB(c).inflate(ROOM + 8.0D, 12.0D, ROOM + 8.0D), s -> s.isAlive() && !s.hasOwner()
                        && s.roomHolds(c))) {                       // (only his own room's: 07.10.2026)
            s.adopt(this);
        }
        countHerd();
    }

    /** Where a dash at `target` ends: on the line to where it stands now, {@link #DASH_STOP} short of it. */
    private Vec3 dashEnd(@Nullable LivingEntity target) {
        if (target == null) {
            return position();
        }
        Vec3 flat = new Vec3(target.getX() - getX(), 0.0D, target.getZ() - getZ());
        double d = flat.length();
        return d <= DASH_STOP ? position() : position().add(flat.scale((d - DASH_STOP) / d));
    }

    /** One tick of the glide: the rest of the way shared over the `left` ticks still to go (walls stop him). */
    private void glide(int left) {
        Vec3 to = dashTo.subtract(position());
        Vec3 flat = new Vec3(to.x, 0.0D, to.z);
        if (flat.lengthSqr() < 0.0025D) {
            return;
        }
        faceTo(dashTo);
        move(net.minecraft.world.entity.MoverType.SELF, flat.scale(1.0D / Math.max(1, left)));
        setDeltaMovement(0.0D, Math.min(0.0D, getDeltaMovement().y), 0.0D);
    }

    private Vec3 fwd() {
        return Vec3.directionFromRotation(0.0F, getYRot());
    }

    private void faceTo(Vec3 at) {
        double dx = at.x - getX(), dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    private void finish(int after) {
        setAttackState(0);
        rest = Math.round(after * (1.0F - DARK_REST * dark));      // in the dark he hardly stops
    }

    /** Every beat of every attack, told before it lands - in his body, in sound, and in the things he sends. */
    private void beats() {
        int st = getAttackState();
        int t = attackTicks;
        LivingEntity target = getTarget();
        switch (st) {
            case INTRO -> introBeats(t);
            case WAKE -> {
                if (t == WAKE_BELL) {
                    playSound(FFSounds.SHADE_SHEPHERD_WAKE.get(), 2.4F, 1.0F);
                    adoptHerd();
                }
                if (t >= WAKE_T) {
                    finish(20);
                }
            }
            case HOOK -> {
                if (t == 2) {
                    playSound(FFSounds.SHADE_SHEPHERD_HOOK_TELL.get(), 1.8F, 1.0F);
                }
                if (t == HOOK_SWING) {
                    playSound(FFSounds.SHADE_SHEPHERD_HOOK.get(), 2.0F, 0.95F + random.nextFloat() * 0.1F);
                }
                if (t == HOOK_HIT) {
                    hookHit();
                }
                if (t > HOOK_HIT && t <= HOOK_PULL) {
                    drag();
                }
                if (t == HOOK_PULL && !hooked.isEmpty()) {
                    sendNearest(hooked.get(0), 2);
                }
                if (t >= HOOK_T) {
                    hooked.clear();
                    finish(14);
                }
            }
            case DASH -> {
                if (t == 1) {
                    playSound(FFSounds.SHADE_SHEPHERD_HOOK_TELL.get(), 1.8F, 0.8F);     // the bell, lower: he comes
                }
                if (t < DASH_TELL && target != null) {
                    faceTo(target.position());
                }
                if (t == DASH_TELL) {
                    dashTo = dashEnd(target);
                    playSound(FFSounds.SHADE_SHEPHERD_GUST.get(), 2.4F, 0.55F);
                }
                if (t > DASH_TELL && t <= DASH_ARRIVE && dashTo != null) {
                    ShadeAfterimageEntity.cast(level(), this);           // his shadow where he was
                    glide(DASH_ARRIVE - t + 1);
                }
                if (t >= DASH_ARRIVE && t < DASH_SWING && target != null) {
                    faceTo(target.position());
                }
                if (t == DASH_SWING) {
                    playSound(FFSounds.SHADE_SHEPHERD_HOOK.get(), 2.0F, 0.95F + random.nextFloat() * 0.1F);
                }
                if (t == DASH_HIT) {
                    hookHit();
                }
                if (t > DASH_HIT && t <= DASH_PULL) {
                    drag();
                }
                if (t == DASH_PULL && !hooked.isEmpty()) {
                    sendNearest(hooked.get(0), 2);
                }
                if (t >= DASH_T) {
                    hooked.clear();
                    dashTo = null;
                    finish(16);
                }
            }
            case SNUFF -> {
                if (t == 1) {
                    chooseBreath(target, secondHalf ? 2 : 1);
                }
                if (!breathFor.isEmpty() && t <= SNUFF_BLOW) {
                    faceTo(Vec3.atCenterOf(breathFor.get(0)));
                }
                if (t == 2) {
                    playSound(FFSounds.SHADE_SHEPHERD_INHALE.get(), 2.2F, 1.0F);
                }
                if (t == SNUFF_BLOW && !breathFor.isEmpty()) {
                    blow(breathFor.get(0));
                    playSound(FFSounds.SHADE_SHEPHERD_EXHALE.get(), 2.2F, 1.0F);
                }
                if (t == SNUFF_BLOW + 4 && breathFor.size() > 1) {
                    blow(breathFor.get(1));
                }
                if (t >= SNUFF_T) {
                    breathFor.clear();
                    finish(16);
                }
            }
            case CALL -> {
                if (t == CALL_BELL) {
                    playSound(FFSounds.SHADE_SHEPHERD_BELL.get(), 3.0F, 1.0F);
                    for (ShadeEntity s : herdList()) {
                        s.gather(this, 80);
                    }
                }
                if (t == CALL_RISE) {
                    raiseHerd();
                }
                if (t >= CALL_T) {
                    finish(16);
                }
            }
            case SEND -> {
                if (target != null) {
                    getLookControl().setLookAt(target, 30.0F, 30.0F);
                }
                if (t == SEND_CALL) {
                    playSound(FFSounds.SHADE_SHEPHERD_WHISTLE.get(), 2.6F, 1.0F);
                }
                if (t == SEND_GO && target != null) {
                    for (ShadeEntity s : herdList()) {
                        s.send(target);
                    }
                }
                if (t >= SEND_T) {
                    finish(14);
                }
            }
            case SPLIT -> {
                if (t == SPLIT_TEAR - 4) {
                    playSound(FFSounds.SHADE_SHEPHERD_SPLIT.get(), 2.2F, 1.0F);
                }
                if (t == SPLIT_TEAR) {
                    tearDecoy();
                }
                if (t >= SPLIT_T) {
                    finish(18);
                }
            }
            case PULSE -> {
                if (t == 2 && level() instanceof ServerLevel sl) {
                    // out to the farthest fire of his chamber (and never short of the room round him)
                    float reach = 14.0F;
                    for (BlockPos f : fires) {
                        reach = Math.max(reach, (float) Math.hypot(f.getX() + 0.5D - getX(), f.getZ() + 0.5D - getZ()) + 1.5F);
                    }
                    ShadePulseEntity.gather(sl, this, PULSE_SLAM - 2, Math.min(44.0F, reach), fires);
                }
                if (t == PULSE_TOLL_1 || t == PULSE_TOLL_2) {
                    playSound(FFSounds.SHADE_SHEPHERD_BELL.get(), 3.0F, 0.55F);       // slow and deep: the tell
                }
                if (t == PULSE_SLAM) {
                    playSound(FFSounds.SHADE_SHEPHERD_EXHALE.get(), 2.6F, 0.6F);
                }
                if (t >= PULSE_T) {
                    finish(18);
                }
            }
            case GREAT -> {
                if (t == 1) {
                    playSound(FFSounds.SHADE_SHEPHERD_INHALE.get(), 3.0F, 0.75F);     // a longer breath, deeper
                }
                if (t == GREAT_BLOW) {
                    playSound(FFSounds.SHADE_SHEPHERD_EXHALE.get(), 3.0F, 0.8F);
                    for (BlockPos f : ShadeLight.lit(level(), fires)) {
                        blow(f);                                   // every fire in the chambers
                    }
                }
                if (t == GREAT_BLOW + 2) {
                    // his line in the fight, once, as every fire has gone out (BossVoice): "Run if you wish. The
                    // darkness always catches its prey."
                    BossVoice.fightLine(this, "shade_shepherd");
                }
                if (t == GREAT_TEAR - 4) {
                    playSound(FFSounds.SHADE_SHEPHERD_SPLIT.get(), 2.4F, 0.9F);
                }
                if (t == GREAT_TEAR) {
                    tearDecoy();
                }
                if (t >= GREAT_T) {
                    finish(20);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ HOOK
    /** The hook swept round before him: whoever stands within its reach and its sweep is caught. */
    private void hookHit() {
        hooked.clear();
        Vec3 f = fwd();
        double cos = Math.cos(Math.toRadians(HOOK_CONE));
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(HOOK_REACH + 1.0D, 2.0D,
                HOOK_REACH + 1.0D), this::foe)) {
            Vec3 to = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
            double d = to.length();
            if (d > HOOK_REACH + v.getBbWidth() * 0.5D || Math.abs(v.getY() - getY()) > 2.5D) {
                continue;
            }
            if (d > 0.8D && to.scale(1.0D / d).dot(f) < cos) {
                continue;
            }
            if (v.hurt(damageSources().mobAttack(this), (secondHalf ? HOOK_DMG_TWO : HOOK_DMG) * (1.0F + DARK_HOOK * dark))) {
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), this);
                v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 60, 0), this);
            }
            hooked.add(v);
        }
        if (!hooked.isEmpty()) {
            playSound(FFSounds.SHADE_SHEPHERD_HOOK_HIT.get(), 2.0F, 0.95F + random.nextFloat() * 0.1F);
        }
    }

    /** ...and dragged in, to his feet. */
    private void drag() {
        Vec3 dest = position().add(fwd().scale(HOOK_TO));
        for (LivingEntity v : hooked) {
            if (!v.isAlive()) {
                continue;
            }
            Vec3 to = new Vec3(dest.x - v.getX(), 0.0D, dest.z - v.getZ());
            double d = to.length();
            if (d < 0.4D) {
                v.setDeltaMovement(v.getDeltaMovement().multiply(0.3D, 1.0D, 0.3D));
            } else {
                Vec3 pull = to.scale(Math.min(0.85D, d * 0.35D) / d);
                v.setDeltaMovement(pull.x, Math.max(v.getDeltaMovement().y, 0.08D), pull.z);
            }
            v.hurtMarked = true;
        }
    }

    /** The nearest `n` of his herd sent at `at`. */
    private void sendNearest(LivingEntity at, int n) {
        List<ShadeEntity> h = herdList();
        h.sort(Comparator.comparingDouble(s -> s.distanceToSqr(at)));
        for (int i = 0; i < Math.min(n, h.size()); i++) {
            h.get(i).send(at);
        }
    }

    // ------------------------------------------------------------------------------------------------ SNUFF
    /** The fires he will blow out: the lit ones nearest whoever he hunts (the light they are using). */
    private void chooseBreath(@Nullable LivingEntity target, int n) {
        breathFor.clear();
        List<BlockPos> lit = ShadeLight.lit(level(), fires);
        Vec3 by = target != null ? target.position() : position();
        lit.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(by.x, by.y, by.z)));
        for (int i = 0; i < Math.min(n, lit.size()); i++) {
            breathFor.add(lit.get(i));
        }
    }

    /** Where his breath leaves him: the slit of the mask's mouth. */
    Vec3 mouth() {
        return position().add(0.0D, 2.55D, 0.0D).add(fwd().scale(0.6D));
    }

    private void blow(BlockPos fire) {
        ShadeShepherdGustEntity.send(level(), this, mouth(), fire);
    }

    /** Lit campfires in his chambers now. */
    int litFires() {
        return ShadeLight.lit(level(), fires).size();
    }

    // ------------------------------------------------------------------------------------------------ CALL
    /** New shades up out of the floor round him - only where no fire's light is - up to the herd's size. */
    private void raiseHerd() {
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        int cap = herdCap();
        int want = Math.min(cap - herdCount, (secondHalf ? 3 : 2) + (herdCount == 0 ? 1 : 0) + (dark >= 0.75F ? 1 : 0)
                + com.jastkub.frozenfortress.event.PartyScaling.extra(this));
        int made = 0;
        for (int tries = 0; tries < 40 && made < want; tries++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double d = 3.5D + random.nextDouble() * 6.5D;
            BlockPos p = standable(getX() + Math.cos(a) * d, getY(), getZ() + Math.sin(a) * d);
            if (p == null || ShadeLight.litNear(level(), Vec3.atCenterOf(p), ShadeLight.SIGHT)
                    || level().getNearestPlayer(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D, 4.0D, false) != null) {
                continue;
            }
            ShadeEntity s = ShadeEntity.rise(sl, this, p);
            s.gather(this, 60);
            made++;
        }
        countHerd();
    }

    /** How big his herd may grow: two more for every fighter past the first (PartyScaling). */
    int herdCap() {
        return (secondHalf ? HERD_CAP_TWO : HERD_CAP) + 2 * com.jastkub.frozenfortress.event.PartyScaling.extra(this);
    }

    @Nullable
    private BlockPos standable(double x, double y, double z) {
        for (int dy : new int[]{0, 1, -1, 2, -2}) {
            BlockPos p = BlockPos.containing(x, y + dy, z);
            BlockPos below = p.below();
            if (level().getBlockState(below).isFaceSturdy(level(), below, Direction.UP)
                    && level().getBlockState(p).getCollisionShape(level(), p).isEmpty()
                    && level().getBlockState(p.above()).getCollisionShape(level(), p.above()).isEmpty()) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------ the decoy
    /** Is a decoy of his about? */
    boolean decoyAlive() {
        return !level().getEntitiesOfClass(ShadeShepherdDecoyEntity.class, getBoundingBox().inflate(ROOM),
                d -> d.isAlive() && d.ownedBy(this)).isEmpty();
    }

    /** His decoy torn off him: it steps out to one side of him, a burst of shadow where it came away. */
    private void tearDecoy() {
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        Vec3 side = new Vec3(-fwd().z, 0.0D, fwd().x).scale(random.nextBoolean() ? 2.5D : -2.5D);
        BlockPos p = standable(getX() + side.x, getY(), getZ() + side.z);
        if (p == null) {
            p = standable(getX() - side.x, getY(), getZ() - side.z);
        }
        if (p == null) {
            p = blockPosition();
        }
        ShadeShepherdDecoyEntity.tear(sl, this, Vec3.atBottomCenterOf(p));
        AttackFxEntity.spawn(level(), "shade_split", position(), 0.0F, 1.0F, 24, this);
    }

    // ------------------------------------------------------------------------------------------------ hurt, death
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return false;
        }
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (isDormant()) {
                if (source.getEntity() instanceof Player) {
                    awaken();                                       // a blow wakes him; it does not reach him
                }
                return false;
            }
            if (getAttackState() == INTRO) {
                return false;                                       // his entrance: a scene is not a fight
            }
            if (getAttackState() == WAKE && attackTicks < 20) {
                amount *= 0.5F;                                     // still rising off his knee
            }
            // THE HERD IS HIS SHIELD
            float shield = Math.min(SHIELD_MAX, SHIELD_PER * herdCount);
            if (shield > 0.0F) {
                amount *= 1.0F - shield;
                if (source.getEntity() instanceof ServerPlayer sp && herdCount >= 2
                        && level().getGameTime() - shieldToldAt > 300L) {
                    shieldToldAt = level().getGameTime();
                    sp.displayClientMessage(Component.translatable("entity.frozen_dominion.shade_shepherd.shielded")
                            .withStyle(ChatFormatting.GRAY), true);
                }
            }
        }
        boolean hit = super.hurt(source, amount);
        if (hit && !secondHalf && isAlive() && getHealth() < getMaxHealth() * 0.5F) {
            secondHalf = true;
            greatNow = true;
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        bossEvent.removeAllPlayers();
        super.die(source);
        if (!level().isClientSide) {
            // with him gone the herd comes apart, and so does whatever of him he tore off
            for (ShadeEntity s : herdList()) {
                s.dissolve();
            }
            for (ShadeShepherdDecoyEntity d : level().getEntitiesOfClass(ShadeShepherdDecoyEntity.class,
                    getBoundingBox().inflate(ROOM + 8.0D), d -> d.ownedBy(this))) {
                d.dissolve();
            }
            // and the heart on his altar that beat for him breaks: the cold lets go of the room
            com.jastkub.frozenfortress.block.entity.FrostHeartBlockEntity.keeperFell(level(), position());
        }
    }

    /** What is left of him (his mask and his crook on the floor) is kept until his death scene goes to black. */
    @Override
    protected int getDeathDuration() {
        return DEATH_T + 36;
    }

    /** His death's sounds, on its clip's beats: his knees, the herd-lights snuffed, the mask on the stones, the crook's
     *  fall and the last of its bell, the dark taking him. */
    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (level().isClientSide) {
            return;
        }
        int t = deathTime - DEATH_LAG;
        if (t == DEATH_KNEEL) {
            playSound(FFSounds.SHADE_SHEPHERD_STEP.get(), 1.4F, 0.6F);
        }
        if (t == DEATH_MOTES + 6) {
            playSound(FFSounds.SHADE_SHEPHERD_SNUFF.get(), 1.4F, 0.8F);
        }
        if (t == DEATH_MASK_LAND) {
            playSound(net.minecraft.sounds.SoundEvents.SKELETON_STEP, 1.2F, 0.6F);
        }
        if (t == DEATH_CROOK + DEATH_CROOK_FALL) {
            playSound(net.minecraft.sounds.SoundEvents.WOOD_FALL, 1.6F, 0.7F);
            playSound(FFSounds.SHADE_SHEPHERD_BELL.get(), 1.0F, 0.7F);
        }
        if (t == DEATH_SINK) {
            playSound(FFSounds.SHADE_DEATH.get(), 1.6F, 0.6F);
        }
    }

    // ------------------------------------------------------------------------------------------------ saved
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Dormant", isDormant());
        tag.putBoolean("SecondHalf", secondHalf);
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
        int[] h = tag.getIntArray("Home");
        if (h.length == 3) {
            home = new BlockPos(h[0], h[1], h[2]);
        }
    }

    // ------------------------------------------------------------------------------------------------ looks, sounds
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            // into the entrance at once (its first frame is the sleep it wakes from): its clip runs on the very
            // ticks the scene's cuts and sounds are timed to (BossScenes) - and out of it as from any other
            state.getController().setTransitionLength(getAttackState() == INTRO ? 0 : 3);
            if (isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (isDormant()) {
                return state.setAndContinue(DORMANT_ANIM);
            }
            switch (getAttackState()) {
                case WAKE: return state.setAndContinue(WAKE_ANIM);
                case INTRO: return state.setAndContinue(INTRO_ANIM);
                case HOOK: return state.setAndContinue(HOOK_ANIM);
                case SNUFF: return state.setAndContinue(SNUFF_ANIM);
                case CALL: return state.setAndContinue(CALL_ANIM);
                case SEND: return state.setAndContinue(SEND_ANIM);
                case SPLIT: return state.setAndContinue(SPLIT_ANIM);
                case GREAT: return state.setAndContinue(GREAT_ANIM);
                case PULSE: return state.setAndContinue(PULSE_ANIM);
                case DASH: return state.setAndContinue(DASH_ANIM);
                default: break;
            }
            return state.setAndContinue(FrostServantEntity.going(state) ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && !isDormant() && hurtTime > 0 && !isAttacking()
                        ? state.setAndContinue(HURT) : PlayState.STOP));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isDormant() ? null : FFSounds.SHADE_SHEPHERD_IDLE.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 140;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.SHADE_SHEPHERD_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.SHADE_SHEPHERD_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(FFSounds.SHADE_SHEPHERD_STEP.get(), 0.7F, 0.9F + random.nextFloat() * 0.2F);
    }

    boolean foe(LivingEntity e) {
        return e != this && e.isAlive() && !FFAllies.ofTheKing(e)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ------------------------------------------------------------------------------------------------ the fight
    /**
     * Chooses what he does next and walks him to it; the beats of each are his own (aiStep). Weighed, so no fight
     * goes the same way twice, and each only when its time has come round again.
     */
    static class ShepherdGoal extends Goal {
        private final ShadeShepherdEntity mob;
        private final int[] per = new int[10];
        private int last;

        ShepherdGoal(ShadeShepherdEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return !mob.isDormant() && t != null && t.isAlive();
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
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            for (int i = 0; i < per.length; i++) {
                if (per[i] > 0) {
                    per[i]--;
                }
            }
            int state = mob.getAttackState();
            if (state != 0) {
                mob.getNavigation().stop();
                if ((state == HOOK && mob.attackTicks < HOOK_HIT - 1) || state == SEND
                        || (state == DASH && mob.attackTicks < DASH_SWING)) {
                    mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    if (state == HOOK && mob.attackTicks < HOOK_TELL) {
                        mob.faceTo(target.position());
                    }
                }
                return;
            }
            double dist = Math.hypot(target.getX() - mob.getX(), target.getZ() - mob.getZ());
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            // he walks in to his crook's length; no closer
            if (dist > 2.6D) {
                mob.getNavigation().moveTo(target, 1.0D);
            } else {
                mob.getNavigation().stop();
            }
            if (mob.rest > 0) {
                mob.rest--;
                return;
            }
            boolean two = mob.inSecondHalf();
            int chosen = 0;
            if (mob.greatNow) {
                mob.greatNow = false;
                chosen = GREAT;
            } else {
                int herd = mob.herdCount;
                boolean nearFire = ShadeLight.litNear(mob.level(), target.position(), ShadeLight.SIGHT + 1.0D);
                int lit = mob.litFires();
                int[] w = new int[10];
                w[HOOK] = per[HOOK] <= 0 && dist < HOOK_REACH - 0.1D && Math.abs(target.getY() - mob.getY()) < 2.5D ? 5 : 0;
                w[DASH] = per[DASH] <= 0 && dist >= DASH_MIN && dist <= DASH_MAX
                        && Math.abs(target.getY() - mob.getY()) < 2.0D && mob.hasLineOfSight(target) ? (two ? 4 : 3) : 0;
                w[SNUFF] = per[SNUFF] <= 0 && lit > 0 && dist < 26.0D ? (nearFire ? 4 : 1) : 0;
                w[CALL] = per[CALL] <= 0 && herd < mob.herdCap() ? (herd == 0 ? 4 : 2) : 0;
                w[SEND] = per[SEND] <= 0 && herd >= 1 && dist < 22.0D ? (mob.dark >= 0.5F ? 4 : 2) : 0;
                w[SPLIT] = two && per[SPLIT] <= 0 && !mob.decoyAlive() ? 3 : 0;
                w[PULSE] = two && per[PULSE] <= 0 && lit >= 1 && dist < 30.0D ? (lit >= 2 ? 4 : 2) : 0;
                if (last > 0 && last < w.length && w[last] > 0 && last != HOOK) {
                    w[last] = Math.max(0, w[last] - 2);          // seldom the same twice running
                }
                int sum = 0;
                for (int v : w) {
                    sum += v;
                }
                if (sum > 0) {
                    int r = mob.random.nextInt(sum);
                    for (int i = 0; i < w.length; i++) {
                        r -= w[i];
                        if (r < 0) {
                            chosen = i;
                            break;
                        }
                    }
                }
            }
            if (chosen == 0) {
                return;
            }
            int wait = switch (chosen) {
                case HOOK -> 50;
                case DASH -> two ? 140 : 200;
                case SNUFF -> two ? 110 : 160;
                case CALL -> two ? 220 : 300;
                case SEND -> two ? 120 : 160;
                case SPLIT -> 600;
                case PULSE -> 480;
                case GREAT -> 0;
                default -> 0;
            };
            per[chosen] = Math.round(wait * (1.0F - DARK_WAIT * mob.dark));    // the dark hurries him
            if (chosen == GREAT) {
                per[SPLIT] = 600;                                 // the great breath tears his first decoy off him
                per[PULSE] = 300;                                 // (and has just put every fire out)
            }
            last = chosen;
            mob.getNavigation().stop();
            mob.faceTo(target.position());
            mob.setAttackState(chosen);
        }
    }
}
