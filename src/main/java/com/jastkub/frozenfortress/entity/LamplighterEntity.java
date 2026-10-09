package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFBlocks;
import com.jastkub.frozenfortress.registry.FFEffects;
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
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;

import java.util.EnumSet;

/**
 * LATARNIK - THE LAMPLIGHTER, keeper of the watchtower's great lantern and of the
 * chained chest under it (the Crownbreaker, the Key of Descent). He fights in the dome, and the dome
 * has ONE RULE, which is the fight:
 *
 * <p><b>Light shields him and hurts you; shadow shields you and opens him.</b> The great lantern hangs
 * in the middle of the vault and its light reaches everything a straight line from it reaches - the
 * four piers throw the only shadows. While its light is on him he takes a quarter of every blow, and a
 * shell of light round him shows it. Lure him into a pier's shadow and he is a man again: full damage,
 * and the first blow there drops him to one knee.
 *
 * <p>And the lantern turns. Its BEAM sweeps the dome like a lighthouse's; whoever it finds out of the
 * shadow freezes, fast. At half his health he strikes the floor - THE SIGNAL - and a second beam opens
 * opposite the first, both turning faster; at a quarter, THE LAST WATCH: the beam stops sweeping and
 * hunts you, slowly. His own attacks: the crook swept round at your legs (it drags you in), the pole
 * driven at you, and THE SHUTTER - his own lantern held up and thrown open, a cone of blinding cold
 * (a pier between you and it is the answer to that too).
 *
 * <p>HIS OWN LANTERN: the shadow is a moment, not a place to stand. Kept in a pier's shadow
 * {@link #HAND_AFTER} ticks (his fall to one knee among them), he lifts the lantern he carries and throws it open
 * (OPEN: the shutter's flash, then it stays open) - for {@link #HAND_T} ticks he is lit by his own light, the
 * great lantern's shield back on him, and whoever is within {@link #HAND_R} of him in sight of it is in the light as in
 * the beam: the cold, and in a second the beam's verdict. Then it gutters, and the shadow is his undoing again.
 */
public class LamplighterEntity extends FrostServantEntity implements GateKeeper {

    /** His states. The staff: SWEEP, THRUST3, WHIRL, VAULT, SMASH. */
    public static final int WAKE = 1, SWEEP = 2, THRUST3 = 3, FLASH = 4, SIGNAL = 5, STAGGER = 6, WATCH = 7,
            WHIRL = 8, VAULT = 9, SMASH = 10, JUDGE = 11, OPEN = 12, INTRO = 13;
    /** HIS ENTRANCE (tools/gen_lamplighter.py INTRO_*: the "intro" clip - change one, change both; BossScenes cuts its
     *  shots to them): the flame of his face guttering and catching, the lantern raised and its shutters thrown open,
     *  the pole's foot struck on the floor, an open hand held up at you. */
    public static final int INTRO_T = 152, INTRO_KINDLE = 20, INTRO_SHUTTERS = 48, INTRO_SHUT = 56, INTRO_STRIKE = 68,
            INTRO_PALM = 88;
    public static final int[] INTRO_SPUTTERS = {8, 14};
    public static final int BEAM_OFF = 0, BEAM_ONE = 1, BEAM_TWO = 2, BEAM_HUNT = 3;
    /** Half the width of a beam, degrees. */
    public static final float BEAM_HALF = 12.0F;
    /** How far a blow of his light goes: the dome. */
    public static final double REACH = 24.0D;
    private static final float LIT_TAKES = 0.25F;
    private static final int FLASH_AT = 24;
    /** His own lantern: opened after this long in a pier's shadow, open this long, its light this far round him. */
    public static final int HAND_AFTER = 80, HAND_T = 160;
    public static final double HAND_R = 6.0D;

    private static final EntityDataAccessor<Boolean> DORMANT =
            SynchedEntityData.defineId(LamplighterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHADED =
            SynchedEntityData.defineId(LamplighterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> BEAM_MODE =
            SynchedEntityData.defineId(LamplighterEntity.class, EntityDataSerializers.INT);
    /** The beam's angle (degrees about the vertical, 0 = +z) at game time BEAM_T0, and its turn per tick. */
    private static final EntityDataAccessor<Float> BEAM_A0 =
            SynchedEntityData.defineId(LamplighterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> BEAM_T0 =
            SynchedEntityData.defineId(LamplighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BEAM_SPEED =
            SynchedEntityData.defineId(LamplighterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> LANTERN =
            SynchedEntityData.defineId(LamplighterEntity.class, EntityDataSerializers.VECTOR3);
    /** Until when (game time) his own lantern is open; 0 - shut. */
    private static final EntityDataAccessor<Integer> HAND_UNTIL =
            SynchedEntityData.defineId(LamplighterEntity.class, EntityDataSerializers.INT);

    private static final String P = "animation.lamplighter.";
    private static final RawAnimation DORMANT_ANIM = RawAnimation.begin().thenLoop(P + "dormant");
    private static final RawAnimation WAKE_ANIM = RawAnimation.begin().thenPlayAndHold(P + "wake");
    private static final RawAnimation INTRO_ANIM = RawAnimation.begin().thenPlayAndHold(P + "intro");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation SWEEP_ANIM = RawAnimation.begin().thenPlayAndHold(P + "sweep");
    private static final RawAnimation THRUST3_ANIM = RawAnimation.begin().thenPlayAndHold(P + "thrust3");
    private static final RawAnimation WHIRL_ANIM = RawAnimation.begin().thenPlayAndHold(P + "whirl");
    private static final RawAnimation VAULT_ANIM = RawAnimation.begin().thenPlayAndHold(P + "vault");
    private static final RawAnimation SMASH_ANIM = RawAnimation.begin().thenPlayAndHold(P + "smash");
    private static final RawAnimation FLASH_ANIM = RawAnimation.begin().thenPlayAndHold(P + "flash");
    private static final RawAnimation SIGNAL_ANIM = RawAnimation.begin().thenPlayAndHold(P + "signal");
    private static final RawAnimation STAGGER_ANIM = RawAnimation.begin().thenLoop(P + "stagger");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay(P + "hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(P + "death");

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.frozen_dominion.lamplighter"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);

    private BlockPos home;
    private boolean signalled, watching;
    /** A gate keeps his dome (found near): then he wakes when it is down, never sooner (gateShut). */
    private boolean gated;
    private int gateLooks;
    /** He has been dropped to his knee in this stretch of shadow (once each time the shadow takes him). */
    private boolean knelt;
    private int staggerTicks;
    /** Client: when his last shutter-flash went off (for the cone of light drawn after it). */
    public long flashAt = -1000L;
    private int lastState;
    /** His own lantern: how long he has been in shadow without it, and whether it was open last tick. */
    private int shadeTicks;
    private boolean darkHere, wasHand;
    /** How long each player has stood in the beam (ticks; below zero: the grace after it froze them). */
    private final java.util.Map<java.util.UUID, Integer> exposure = new java.util.HashMap<>();
    /**
     * Ticks of a beam's light before it passes its VERDICT on you (see verdict()). A sweeping beam is over a man
     * standing in its way for about ten ticks (eight with two beams), so it judges after six: whoever it crosses out
     * of the shadow is judged.
     * The hunting beam of the last watch stays on you as long as it likes; it takes a second.
     */
    private static final int JUDGE_SWEEP = 6, JUDGE_HUNT = 20;
    /** The verdicts of the beam: his leap and blow, the light in your eyes, the cold that holds you. */
    private static final int V_JUDGE = 0, V_BLIND = 1, V_FREEZE = 2;
    private int lastVerdict = -1;
    /** Whom the beam has judged - the one his leap is for. */
    @javax.annotation.Nullable
    private java.util.UUID judged;

    public LamplighterEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 150;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 280.0D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DORMANT, true);
        builder.define(SHADED, false);
        builder.define(BEAM_MODE, BEAM_OFF);
        builder.define(BEAM_A0, 0.0F);
        builder.define(BEAM_T0, 0);
        builder.define(BEAM_SPEED, 0.0F);
        builder.define(LANTERN, new Vector3f());
        builder.define(HAND_UNTIL, 0);
    }

    public boolean isDormant() {
        return entityData.get(DORMANT);
    }

    /** Is he out of the great lantern's light (in a pier's shadow) - open to every blow? */
    public boolean isShaded() {
        return entityData.get(SHADED);
    }

    /** Is his own lantern open (he is lit by it, wherever he stands)? */
    public boolean handLit() {
        return entityData.get(HAND_UNTIL) > level().getGameTime();
    }

    /** Ticks (and the partial one) its light has left; 0 when shut. */
    public float handLeft(float partialTick) {
        return Math.max(0.0F, entityData.get(HAND_UNTIL) - (level().getGameTime() + partialTick));
    }

    /** Is `e` in his own lantern's light: near him, and in its sight? */
    public boolean inHand(LivingEntity e) {
        if (!handLit()) {
            return false;
        }
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        return dx * dx + dz * dz < HAND_R * HAND_R && Math.abs(e.getY() - getY()) < 4.0D && hasLineOfSight(e);
    }

    public int beamMode() {
        return entityData.get(BEAM_MODE);
    }

    public Vec3 lantern() {
        Vector3f v = entityData.get(LANTERN);
        return new Vec3(v.x, v.y, v.z);
    }

    /** The beam's angle now (degrees about the vertical; 0 is +z, as Minecraft's yaw is). */
    public float beamAngle(float partialTick) {
        float t = (level().getGameTime() + partialTick) - entityData.get(BEAM_T0);
        return entityData.get(BEAM_A0) + entityData.get(BEAM_SPEED) * t;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new LampGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 14.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isDormant() || getAttackState() == STAGGER || getAttackState() == WAKE
                || getAttackState() == SIGNAL || getAttackState() == WATCH || getAttackState() == OPEN
                || getAttackState() == INTRO;
    }

    // ------------------------------------------------------------------ light and shadow
    /** Is the straight line from the great lantern to this point broken by a block? */
    public boolean shadowed(Vec3 at) {
        Vec3 l = lantern();
        if (l.lengthSqr() < 1.0E-4D) {
            return false;
        }
        // from just outside the lantern itself (a ray that starts inside a solid block stops there)
        Vec3 to = at.subtract(l);
        if (to.lengthSqr() < 1.0D) {
            return false;
        }
        Vec3 from = l.add(to.normalize().scale(0.9D));
        HitResult hit = level().clip(new ClipContext(from, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(at) > 0.36D;
    }

    /** In shadow: neither its middle nor its head can see the lantern. */
    public boolean inShadow(LivingEntity e) {
        return shadowed(e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D)) && shadowed(e.getEyePosition());
    }

    /** Does a beam (at this moment) lie across this point - and nothing between it and the lantern? */
    public boolean inBeam(LivingEntity e) {
        int mode = beamMode();
        if (mode == BEAM_OFF) {
            return false;
        }
        Vec3 l = lantern();
        double dx = e.getX() - l.x, dz = e.getZ() - l.z;
        if (dx * dx + dz * dz < 1.0D || dx * dx + dz * dz > REACH * REACH) {
            return false;
        }
        float at = (float) (Mth.atan2(-dx, dz) * (180.0D / Math.PI));      // yaw-convention angle of the point
        float a = beamAngle(0.0F);
        boolean hit = Math.abs(Mth.wrapDegrees(at - a)) < BEAM_HALF
                || (mode == BEAM_TWO && Math.abs(Mth.wrapDegrees(at - a - 180.0F)) < BEAM_HALF);
        return hit && !inShadow(e);
    }

    private void setBeam(int mode, float speed) {
        float now = beamAngle(0.0F);
        entityData.set(BEAM_A0, Mth.wrapDegrees(now));
        entityData.set(BEAM_T0, (int) level().getGameTime());
        entityData.set(BEAM_SPEED, speed);
        entityData.set(BEAM_MODE, mode);
    }

    /** The great lantern: the heart of sovereign ice hung over the middle of the dome (found above him). */
    private void findLantern() {
        BlockPos c = home != null ? home : blockPosition();
        for (int r = 0; r <= 6; r++) {
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, 2, -r), c.offset(r, 22, r))) {
                if (level().getBlockState(p).is(FFBlocks.GREAT_LANTERN.get())
                        || level().getBlockState(p).is(FFBlocks.SOVEREIGN_ICE.get())
                        || level().getBlockState(p).is(FFBlocks.SEALED_SOVEREIGN_ICE.get())) {
                    entityData.set(LANTERN, new Vector3f(p.getX() + 0.5F, p.getY() + 0.5F, p.getZ() + 0.5F));
                    return;
                }
            }
        }
        entityData.set(LANTERN, new Vector3f((float) getX(), (float) getY() + 9.0F, (float) getZ()));
    }

    // ------------------------------------------------------------------ the bar
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
        if (level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer p : serverLevel.getPlayers(p -> p.distanceToSqr(this) < 40.0D * 40.0D)) {
                bossEvent.addPlayer(p);
            }
            for (ServerPlayer p : java.util.List.copyOf(bossEvent.getPlayers())) {
                if (p.distanceToSqr(this) >= 56.0D * 56.0D || p.level() != level()) {
                    bossEvent.removePlayer(p);
                }
            }
        }
    }

    // ------------------------------------------------------------------ his watch
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            int st = getAttackState();
            if ((st == FLASH || st == OPEN) && lastState != st) {
                flashAt = level().getGameTime() + FLASH_AT;        // the flash, when the clip reaches it
            } else if (st == INTRO && lastState != st) {
                flashAt = level().getGameTime() + INTRO_SHUTTERS;  // his entrance's: the shutters thrown open
            }
            lastState = st;
            return;
        }
        if (home == null) {
            home = blockPosition();
            findLantern();
        }
        if (isDormant()) {
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            // HIS SCENE AFTER THE GATE: in his dome he wakes when its gate is down behind whoever came in
            // (gateShut) - not when he is seen from the threshold. Only where no gate keeps his room (one set
            // down by hand) does nearness still wake him, once the look for a gate has had a few goes.
            if (!gated && (gateLooks == 0 || tickCount % 40 == 0)) {
                gateLooks++;
                gated = gateNear();
            }
            if (!gated && gateLooks >= 3) {
                Player near = level().getNearestPlayer(this, 14.0D);
                if (near != null && !near.isCreative() && !near.isSpectator() && hasLineOfSight(near)) {
                    awaken();
                }
            }
            tickBossBar();
            return;
        }
        int st = getAttackState();
        if (st == INTRO) {
            introBeats(attackTicks);
        }
        if (st == WAKE) {
            if (attackTicks == 28) {
                setBeam(BEAM_ONE, 2.25F);                          // a turn in eight seconds
                playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 0.7F);
                playSound(SoundEvents.ANVIL_LAND, 1.2F, 0.6F);
            }
            if (attackTicks > 40) {
                setAttackState(0);
            }
        }
        // his light, or his shadow
        if (tickCount % 2 == 0) {
            darkHere = inShadow(this);
        }
        boolean shaded = darkHere && !handLit();
        if (shaded != isShaded()) {
            entityData.set(SHADED, shaded);
            if (!shaded) {
                knelt = false;
            }
            playSound(shaded ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F,
                    shaded ? 1.6F : 0.6F);
        }
        handLantern(st);
        // the beam: the hunt turns it toward its quarry, slowly
        if (beamMode() == BEAM_HUNT && getTarget() != null) {
            Vec3 l = lantern();
            float want = (float) (Mth.atan2(-(getTarget().getX() - l.x), getTarget().getZ() - l.z) * (180.0D / Math.PI));
            float now = beamAngle(0.0F);
            float step = Mth.clamp(Mth.wrapDegrees(want - now), -1.3F, 1.3F);
            entityData.set(BEAM_A0, Mth.wrapDegrees(now + step));
            entityData.set(BEAM_T0, (int) level().getGameTime());
        }
        if (beamMode() != BEAM_OFF && level() instanceof ServerLevel sl) {
            java.util.Set<java.util.UUID> seen = new java.util.HashSet<>();
            for (ServerPlayer p : sl.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                    && p.distanceToSqr(lantern()) < REACH * REACH)) {
                seen.add(p.getUUID());
                int ex = exposure.getOrDefault(p.getUUID(), 0);
                boolean beam = inBeam(p);
                if (beam || inHand(p)) {
                    // CAUGHT IN IT - watched every tick, for a sweeping beam is soon past: the cold on you while it
                    // lasts, and the verdict if it has you long enough (JUDGE_SWEEP, JUDGE_HUNT). Below zero is the
                    // grace after a verdict, running out in the light as out of it.
                    ex++;
                    if (tickCount % 5 == 0) {
                        p.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 1), this);
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2), this);
                        p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 60, p.getTicksFrozen() + 35));
                    }
                    if (tickCount % 10 == 0) {
                        float burn = 2.0F + Math.min(6.0F, Math.max(0, ex) / 10.0F * 2.0F);
                        p.hurt(damageSources().indirectMagic(this, this), burn);
                        sl.playSound(null, p.blockPosition(), SoundEvents.GLASS_HIT, SoundSource.HOSTILE, 0.8F, 1.4F + ex * 0.01F);
                    }
                    // (his own lantern's light stays on you as the hunting beam does: it takes a second)
                    if (ex >= (beamMode() == BEAM_HUNT || !beam ? JUDGE_HUNT : JUDGE_SWEEP)) {
                        verdict(sl, p);
                        ex = -80;                                  // four seconds before it can judge again
                    }
                } else if (ex > 0) {
                    ex--;                                          // out of it, it lets go of you slowly
                } else if (ex < 0) {
                    ex++;
                }
                exposure.put(p.getUUID(), ex);
            }
            exposure.keySet().retainAll(seen);
        }
        if (tickCount % 90 == 0 && beamMode() != BEAM_OFF) {
            level().playSound(null, BlockPos.containing(lantern()), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        // the two turns of the fight
        if (!signalled && getHealth() < getMaxHealth() * 0.5F && getAttackState() == 0) {
            signalled = true;
            setAttackState(SIGNAL);
            // his line in the fight, once, at THE SIGNAL - the turn of his fight, the second beam about to open
            // (BossVoice): "Let the darkness consume what remains of you."
            BossVoice.fightLine(this, "lamplighter");
        }
        if (!watching && getHealth() < getMaxHealth() * 0.25F && getAttackState() == 0) {
            watching = true;
            setAttackState(WATCH);
        }
        if (st == SIGNAL || st == WATCH) {
            if (attackTicks == 4) {
                playSound(SoundEvents.CHAIN_STEP, 1.4F, 0.6F);
            }
            if (attackTicks == 22) {
                playSound(SoundEvents.ANVIL_LAND, 1.6F, 0.5F);
                playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, st == SIGNAL ? 0.8F : 0.5F);
                if (st == SIGNAL) {
                    setBeam(BEAM_TWO, 3.0F);                       // two beams, a turn in six seconds
                } else {
                    setBeam(BEAM_HUNT, 0.0F);                      // the last watch: it hunts
                }
            }
            if (attackTicks > 40) {
                setAttackState(0);
            }
        }
        if (st == STAGGER && --staggerTicks <= 0) {
            setAttackState(0);
        }
        // he keeps to his dome
        if (home != null && distanceToSqr(Vec3.atCenterOf(home)) > 18.0D * 18.0D && !isAttacking()) {
            getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.0D);
        }
        tickBossBar();
    }

    /**
     * HIS OWN LANTERN, opened in the shadow (see the class's doc): counted while he stands in shadow with it shut, opened
     * (OPEN: the shutter's clip and flash - and it stays open) once he has been there {@link #HAND_AFTER} ticks and is
     * free to, shut again {@link #HAND_T} ticks after.
     */
    private void handLantern(int st) {
        boolean lit = handLit();
        if (wasHand && !lit) {
            shadeTicks = 0;                                        // it gutters: the shadow counts afresh
            playSound(SoundEvents.FIRE_EXTINGUISH, 1.2F, 1.3F);
            playSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.6F, 1.5F);
        }
        wasHand = lit;
        if (st == OPEN) {
            if (attackTicks == 4) {
                playSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.6F, 1.8F);         // the shutters, creaking
            }
            if (attackTicks == 14 || attackTicks == 18 || attackTicks == 21) {
                playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.8F + attackTicks * 0.03F);
            }
            if (attackTicks == FLASH_AT) {
                flash();
                entityData.set(HAND_UNTIL, (int) level().getGameTime() + HAND_T);
                playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.6F);
            }
            if (attackTicks > 40) {
                setAttackState(0);
            }
            return;
        }
        if (lit) {
            return;
        }
        shadeTicks = darkHere ? shadeTicks + 1 : 0;
        if (shadeTicks >= HAND_AFTER && st == 0 && getTarget() != null) {
            shadeTicks = 0;
            setAttackState(OPEN);
        }
    }

    @Override
    public void gateShut() {
        if (isDormant() && isAlive()) {
            awaken();
        }
    }

    /** Is there a gate keeping his dome - a boss gate of his, in reach of where he stands? */
    private boolean gateNear() {
        BlockPos c = home != null ? home : blockPosition();
        int cx = c.getX() >> 4, cz = c.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!level().hasChunk(cx + dx, cz + dz)) {
                    continue;
                }
                for (net.minecraft.world.level.block.entity.BlockEntity be
                        : level().getChunk(cx + dx, cz + dz).getBlockEntities().values()) {
                    if (be instanceof com.jastkub.frozenfortress.block.entity.BossGateBlockEntity g
                            && "frozen_dominion:lamplighter".equals(g.keeper())
                            && be.getBlockPos().distSqr(c) < 32 * 32) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void awaken() {
        entityData.set(DORMANT, false);
        // his entrance, once for each who sees it (BossCutscenes), shot as a film: the flame of his face catching, the
        // shutters thrown open, the pole struck down - or, when everyone here has seen it, the short waking as before
        introYaw = com.jastkub.frozenfortress.BossCutscenes.postYaw(this);
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        if (com.jastkub.frozenfortress.BossCutscenes.intro(this, INTRO_T)) {
            setAttackState(INTRO);
            return;
        }
        setAttackState(WAKE);
        playSound(SoundEvents.LANTERN_PLACE, 2.0F, 0.6F);
        playSound(SoundEvents.CHAIN_PLACE, 1.6F, 0.5F);
    }

    /** Which way he faces through his entrance: his post's. */
    private float introYaw;

    /** His entrance's beats (the clip's), him kept at his post; the great lantern's beam set turning at its end
     *  (not before: its light judges whoever it finds, and a scene is not a fight). */
    private void introBeats(int t) {
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        for (int s : INTRO_SPUTTERS) {
            if (t == s) {
                playSound(SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.6F);    // the flame of his face guttering
            }
        }
        if (t == INTRO_KINDLE) {
            playSound(FFSounds.LAMPLIGHTER_KINDLE.get(), 1.6F, 1.0F);
        }
        if (t == 30) {
            playSound(SoundEvents.LANTERN_PLACE, 1.4F, 0.6F);           // the pole lifted, the lantern swinging
            playSound(SoundEvents.CHAIN_PLACE, 1.2F, 0.5F);
        }
        if (t == 42 || t == 45) {
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 0.8F + (t - 40) * 0.08F);   // the light filling it
        }
        if (t == INTRO_SHUTTERS) {
            playSound(SoundEvents.IRON_TRAPDOOR_OPEN, 1.6F, 1.5F);
            playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.6F);
        }
        if (t == INTRO_SHUT) {
            playSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.6F, 1.8F);
        }
        if (t == INTRO_STRIKE) {
            playSound(FFSounds.LAMP_STAFF_HIT.get(), 2.4F, 0.8F);
            playSound(SoundEvents.ANVIL_LAND, 1.2F, 0.6F);
        }
        if (t >= INTRO_T) {
            setBeam(BEAM_ONE, 2.25F);                                  // a turn in eight seconds (as his waking)
            playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 0.7F);
            setAttackState(0);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return false;
        }
        if (isDormant() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (gated) {
                return false;                                       // asleep behind his gate: nothing from the threshold
            }
            if (source.getEntity() != null) {
                awaken();
            }
        }
        if (getAttackState() == INTRO && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;                                           // his entrance: a scene is not a fight
        }
        if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (!isShaded()) {
                amount *= LIT_TAKES;                                // the light takes the blow
                playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.4F, 0.6F);
            } else if (getAttackState() == STAGGER) {
                amount *= 1.5F;
            } else if (!knelt && source.getEntity() instanceof Player) {
                knelt = true;                                       // caught in the dark: down he goes
                staggerTicks = 60;
                setAttackState(STAGGER);
                playSound(SoundEvents.GLASS_BREAK, 1.2F, 1.4F);
                playSound(SoundEvents.BEACON_DEACTIVATE, 1.6F, 1.2F);
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        bossEvent.removeAllPlayers();
        entityData.set(BEAM_MODE, BEAM_OFF);
        super.die(source);
        if (!level().isClientSide) {
            playSound(SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.5F);
            playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.6F);
        }
    }

    @Override
    protected int getDeathDuration() {
        return 60;
    }

    // ------------------------------------------------------------------ his blows
    /** The living within `radius` of `at` (flat), and within `half` degrees of `dir` (180: all round). */
    private java.util.List<LivingEntity> struck(Vec3 at, Vec3 dir, double radius, double half) {
        java.util.List<LivingEntity> out = new java.util.ArrayList<>();
        double cos = Math.cos(Math.toRadians(half));
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(at, at).inflate(radius + 1.0D, 3.0D, radius + 1.0D),
                e -> e != this && e.isAlive() && !FFAllies.ofTheKing(e))) {
            Vec3 to = v.position().subtract(at);
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            if (flat.length() > radius + v.getBbWidth() * 0.5D || Math.abs(to.y) > 3.0D) {
                continue;
            }
            if (half < 180.0D && flat.lengthSqr() > 1.0E-4D && flat.normalize().dot(dir) < cos) {
                continue;
            }
            out.add(v);
        }
        return out;
    }

    private Vec3 fwd() {
        return Vec3.directionFromRotation(0.0F, getYRot());
    }

    private void whoosh(float pitch) {
        playSound(FFSounds.LAMP_WHOOSH.get(), 1.6F, pitch + random.nextFloat() * 0.1F);
    }

    /** A blow of the staff on whoever it finds; true if it found anyone. */
    private boolean staff(java.util.List<LivingEntity> victims, float damage, double push, double lift) {
        boolean any = false;
        Vec3 f = fwd();
        for (LivingEntity v : victims) {
            if (v.hurt(damageSources().mobAttack(this), damage)) {
                any = true;
                Vec3 away = v.position().subtract(position());
                away = new Vec3(away.x, 0.0D, away.z);
                away = away.lengthSqr() > 1.0E-4D ? away.normalize() : f;
                v.setDeltaMovement(v.getDeltaMovement().add(away.x * push, lift, away.z * push));
                v.hurtMarked = true;
            }
        }
        if (any) {
            playSound(FFSounds.LAMP_STAFF_HIT.get(), 1.5F, 0.9F + random.nextFloat() * 0.2F);
        }
        return any;
    }

    /** PODCIECIE: the pole round him at shin height. Whoever is in the air goes over it. */
    void sweepHit() {
        java.util.List<LivingEntity> v = struck(position(), fwd(), 4.6D, 180.0D);
        v.removeIf(e -> !e.onGround());                         // jumped it
        staff(v, 7.0F, 0.2D, 0.42D);
        for (LivingEntity e : v) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), this);   // off their feet
        }
    }

    /** One of the three thrusts: a line before him `reach` long. */
    void jab(double reach, float damage, double push) {
        Vec3 f = fwd();
        Vec3 from = position().add(0.0D, 1.6D, 0.0D);
        java.util.List<LivingEntity> v = new java.util.ArrayList<>();
        for (LivingEntity e : struck(position(), f, reach + 0.5D, 30.0D)) {
            Vec3 to = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).subtract(from);
            double along = to.dot(f);
            if (along >= 0.0D && along <= reach && to.subtract(f.scale(along)).length() <= 1.2D + e.getBbWidth() * 0.5D) {
                v.add(e);
            }
        }
        staff(v, damage, push, 0.1D);
    }

    /** WIATRAK: the wheel before him catches what is in front. */
    void wheelHit() {
        staff(struck(position(), fwd(), 3.8D, 75.0D), 4.0F, 0.5D, 0.15D);
    }

    /** CIOS Z GORY: the lantern on the floor three blocks before him - and its light bursting there. */
    void smashHit() {
        Vec3 at = position().add(fwd().scale(3.0D));
        java.util.List<LivingEntity> v = struck(at, fwd(), 2.6D, 180.0D);
        staff(v, 11.0F, 0.6D, 0.3D);
        for (LivingEntity e : v) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), this);
            e.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 100, 1), this);
        }
        playSound(FFSounds.LAMP_SLAM.get(), 2.4F, 0.95F + random.nextFloat() * 0.1F);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(com.jastkub.frozenfortress.registry.FFParticles.ICE_SHARD.get(), at.x, getY() + 0.2D, at.z,
                    24, 0.8D, 0.1D, 0.8D, 0.12D);
        }
    }

    /** SKOK: off the pole's foot and through the air at his quarry, landing short of it. */
    void vaultLeap(LivingEntity target) {
        Vec3 to = target.position().subtract(position());
        Vec3 flat = new Vec3(to.x, 0.0D, to.z);
        double dist = Math.max(0.0D, flat.length() - 1.8D);
        Vec3 dir = flat.lengthSqr() > 1.0E-4D ? flat.normalize() : fwd();
        int t = 16;
        double h = dist * (1.0D - 0.91D) / (1.0D - Math.pow(0.91D, t));   // what the air's drag leaves of it
        setDeltaMovement(dir.x * h, 0.08D * t / 2.0D + 0.06D, dir.z * h);
        hurtMarked = true;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        setYBodyRot(yaw);
        playSound(FFSounds.LAMP_WHOOSH.get(), 2.0F, 0.6F);
    }

    /** ...and down: the lantern on the floor all round where he lands. */
    void vaultLand() {
        java.util.List<LivingEntity> v = struck(position(), fwd(), 3.6D, 180.0D);
        staff(v, 8.0F, 0.7D, 0.45D);
        for (LivingEntity e : v) {
            e.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 0), this);
        }
        playSound(FFSounds.LAMP_SLAM.get(), 2.6F, 0.85F);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(com.jastkub.frozenfortress.registry.FFParticles.ICE_SHARD.get(), getX(), getY() + 0.2D, getZ(),
                    30, 1.2D, 0.1D, 1.2D, 0.14D);
        }
    }

    /**
     * THE BEAM'S VERDICT. A second
     * in the light and one of three falls on you - never the same one twice running:
     *   WYROK      he drops whatever he was doing, leaps at you and brings the pole down; no dodging it
     *   OSLEPIENIE the light in your eyes, five seconds
     *   ZAMROZENIE the cold holds you where you stand, three and a half seconds
     * Knelt in his own shadow he cannot leap: then it is one of the other two.
     */
    private void verdict(ServerLevel sl, ServerPlayer p) {
        java.util.List<Integer> can = new java.util.ArrayList<>();
        boolean free = getAttackState() != STAGGER && getAttackState() != WAKE && !isDormant()
                && distanceToSqr(p) < 28.0D * 28.0D;
        if (free) {
            can.add(V_JUDGE);
        }
        can.add(V_BLIND);
        can.add(V_FREEZE);
        if (can.size() > 1) {
            can.remove(Integer.valueOf(lastVerdict));
        }
        int v = can.get(random.nextInt(can.size()));
        lastVerdict = v;
        switch (v) {
            case V_JUDGE -> {
                judged = p.getUUID();
                setTarget(p);
                getNavigation().stop();
                setAttackState(JUDGE);
                sl.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0F, 0.5F);
                p.displayClientMessage(Component.translatable("entity.frozen_dominion.lamplighter.judged")
                        .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            }
            case V_BLIND -> {
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0), this);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
                p.hurt(damageSources().indirectMagic(this, this), 3.0F);
                sl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 1.6F);
                sl.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.6F, 1.8F);
                p.displayClientMessage(Component.translatable("entity.frozen_dominion.lamplighter.blinded")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            }
            default -> {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 6), this);
                p.addEffect(new MobEffectInstance(MobEffects.JUMP, 70, -10), this);
                p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 70, 2), this);
                p.setTicksFrozen(Math.max(p.getTicksFrozen(), p.getTicksRequiredToFreeze() + 140));
                p.hurt(damageSources().indirectMagic(this, this), 4.0F);
                sl.playSound(null, p.blockPosition(), FFSounds.LAMP_FREEZE.get(), SoundSource.HOSTILE, 1.6F, 1.0F);
                p.displayClientMessage(Component.translatable("entity.frozen_dominion.lamplighter.frozen")
                        .withStyle(net.minecraft.ChatFormatting.AQUA), true);
            }
        }
    }

    /** Whom the beam judged, if they are still about. */
    @javax.annotation.Nullable
    LivingEntity judgedOne() {
        if (judged == null || !(level() instanceof ServerLevel sl)) {
            return null;
        }
        return sl.getEntity(judged) instanceof LivingEntity e && e.isAlive() ? e : null;
    }

    /** WYROK, in the air: he steers onto them as they run - the leap was never going to miss. */
    void judgeSteer(LivingEntity target, int ticksLeft) {
        Vec3 to = target.position().subtract(position());
        Vec3 flat = new Vec3(to.x, 0.0D, to.z);
        double dist = Math.max(0.0D, flat.length() - 1.4D);
        if (flat.lengthSqr() < 1.0E-4D) {
            return;
        }
        Vec3 dir = flat.normalize();
        double h = Math.min(1.4D, dist / Math.max(1, ticksLeft) / 0.91D);
        setDeltaMovement(dir.x * h, getDeltaMovement().y, dir.z * h);
        hurtMarked = true;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        setYBodyRot(yaw);
    }

    /**
     * ...and down on them. Wherever they got to, he lands at their side (a last bound if they ran far), and the
     * blow is theirs: twelve, through armour and through a raised shield - it is the light's verdict, not a swing
     * to be blocked - thrown off their feet, the cold in them.
     */
    void judgeHit(LivingEntity target) {
        if (distanceToSqr(target) > 3.2D * 3.2D) {
            Vec3 back = position().subtract(target.position());
            Vec3 flat = new Vec3(back.x, 0.0D, back.z);
            Vec3 dir = flat.lengthSqr() > 1.0E-4D ? flat.normalize() : fwd().scale(-1.0D);
            Vec3 spot = target.position().add(dir.scale(1.6D));
            if (level().noCollision(this, getBoundingBox().move(spot.subtract(position())))) {
                teleportTo(spot.x, spot.y, spot.z);
            }
        }
        Vec3 to = target.position().subtract(position());
        float yaw = (float) (Mth.atan2(to.z, to.x) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        setYBodyRot(yaw);
        // magic with no source position: no shield faces it (indirectMagic carries his position, and a raised
        // shield turned toward him would have taken the verdict)
        target.invulnerableTime = 0;
        target.hurt(damageSources().magic(), 12.0F);
        Vec3 away = new Vec3(to.x, 0.0D, to.z);
        away = away.lengthSqr() > 1.0E-4D ? away.normalize() : fwd();
        target.setDeltaMovement(away.x * 1.3D, 0.55D, away.z * 1.3D);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
        }
        target.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 120, 1), this);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), this);
        playSound(FFSounds.LAMP_SLAM.get(), 3.0F, 0.8F);
        playSound(FFSounds.LAMP_STAFF_HIT.get(), 2.4F, 0.8F);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(com.jastkub.frozenfortress.registry.FFParticles.ICE_SHARD.get(), target.getX(), target.getY() + 0.3D,
                    target.getZ(), 40, 1.0D, 0.2D, 1.0D, 0.18D);
        }
        judged = null;
    }

    /** Where his own lantern hangs as he holds it up (the PRZESLONA's source). */
    public Vec3 handLantern() {
        return position().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(1.6D)).add(0.0D, 4.0D, 0.0D);
    }

    /**
     * THE SHUTTER (PRZESLONA): his lantern's four shutters thrown open at whoever is before him - a cone
     * of blinding cold, out to twelve blocks. What has a pier (or any wall) between it and his lantern
     * is spared.
     */
    void flash() {
        Vec3 src = handLantern();
        Vec3 fwd = Vec3.directionFromRotation(0.0F, getYRot());
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(13.0D),
                e -> e != this && e.isAlive() && !FFAllies.ofTheKing(e))) {
            Vec3 to = v.getEyePosition().subtract(src);
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            if (flat.length() > 12.0D || flat.lengthSqr() < 1.0E-4D || flat.normalize().dot(fwd) < Math.cos(Math.toRadians(36.0D))) {
                continue;
            }
            HitResult hit = level().clip(new ClipContext(src, v.getEyePosition(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                continue;                                           // behind a pier: spared
            }
            v.hurt(damageSources().indirectMagic(this, this), 6.0F);
            v.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), this);
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 0), this);
        }
        playSound(SoundEvents.IRON_TRAPDOOR_OPEN, 2.0F, 1.5F);
        playSound(SoundEvents.BEACON_POWER_SELECT, 2.6F, 1.6F);
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 1.2F);
    }

    /**
     * THE STAFF'S FORMS, chosen by how far you are, never the same twice running: at his feet the sweep,
     * the three thrusts and the wheel; at the pole's length the thrusts, the wheel and the overhead; out
     * of reach the vault - up the pole and down on you - or the shutter.
     */
    static class LampGoal extends Goal {
        private final LamplighterEntity mob;
        private int cooldown = 20;
        private final int[] per = new int[12];
        private int last;

        LampGoal(LamplighterEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !mob.isDormant() && mob.getTarget() != null && mob.getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            int st = mob.getAttackState();
            if (st == SWEEP || st == THRUST3 || st == FLASH || st == WHIRL || st == VAULT || st == SMASH || st == JUDGE) {
                mob.setAttackState(0);
            }
        }

        private void consider(java.util.List<Integer> pool, int move, boolean ok) {
            if (ok && per[move] <= 0 && move != last) {
                pool.add(move);
            }
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
            double d = mob.distanceTo(target);
            int st = mob.getAttackState();
            if (st == 0) {
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                mob.getNavigation().moveTo(target, 1.0D);
                if (cooldown > 0) {
                    cooldown--;
                    return;
                }
                boolean sighted = mob.hasLineOfSight(target);
                java.util.List<Integer> pool = new java.util.ArrayList<>();
                consider(pool, SWEEP, d < 3.4D);
                consider(pool, THRUST3, d < 6.0D && sighted);
                consider(pool, WHIRL, d < 5.5D);
                consider(pool, SMASH, d >= 2.5D && d < 6.5D && sighted);
                consider(pool, VAULT, d >= 6.5D && d < 15.0D && sighted);
                consider(pool, FLASH, d >= 5.0D && d < 12.0D && sighted);
                if (pool.isEmpty()) {
                    return;
                }
                int chosen = pool.get(mob.random.nextInt(pool.size()));
                per[chosen] = switch (chosen) {
                    case SWEEP -> 90;
                    case THRUST3 -> 60;
                    case WHIRL -> 120;
                    case SMASH -> 90;
                    case VAULT -> 160;
                    case FLASH -> 180;
                    default -> 40;
                };
                last = chosen;
                mob.getNavigation().stop();
                float yaw = (float) (Mth.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX()) * (180.0D / Math.PI)) - 90.0F;
                mob.setYRot(yaw);
                mob.setYBodyRot(yaw);
                mob.setYHeadRot(yaw);
                mob.setAttackState(chosen);
                return;
            }
            if (st != WHIRL) {
                mob.getNavigation().stop();
            }
            int t = mob.attackTicks;
            switch (st) {
                case SWEEP -> {
                    if (t == 4) {
                        mob.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.2F, 0.7F);       // down into the crouch
                    }
                    if (t == 10) {
                        mob.whoosh(0.7F);
                    }
                    if (t == 15) {
                        mob.sweepHit();
                    }
                    if (t > 32) {
                        finish(16);
                    }
                }
                case THRUST3 -> {
                    mob.getLookControl().setLookAt(target, 20.0F, 20.0F);
                    if (t == 6 || t == 14) {
                        mob.whoosh(1.3F);
                    }
                    if (t == 8) {
                        mob.jab(4.6D, 5.0F, 0.3D);
                    }
                    if (t == 16) {
                        mob.jab(5.0D, 5.0F, 0.4D);
                    }
                    if (t == 23) {
                        mob.whoosh(0.95F);
                    }
                    if (t == 26) {
                        mob.jab(6.5D, 9.0F, 1.0D);
                    }
                    if (t > 36) {
                        finish(14);
                    }
                }
                case WHIRL -> {
                    mob.getLookControl().setLookAt(target, 10.0F, 10.0F);
                    if (t >= 8 && t <= 40) {
                        mob.getNavigation().moveTo(target, 0.55D);   // pressing in behind the wheel
                        if (t % 6 == 2) {
                            mob.whoosh(0.85F + (t - 8) * 0.008F);
                        }
                    }
                    if (t == 14 || t == 22 || t == 30 || t == 38) {
                        mob.wheelHit();
                    }
                    if (t > 50) {
                        mob.getNavigation().stop();
                        finish(20);
                    }
                }
                case VAULT -> {
                    if (t == 6) {
                        mob.playSound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.4F, 0.6F);        // the pole's foot planted
                    }
                    if (t == 10) {
                        mob.vaultLeap(target);
                    }
                    if (t == 28 || (t > 18 && t < 28 && mob.onGround())) {
                        if (t < 28) {
                            mob.attackTicks = 28;                         // down early: land now
                        }
                        mob.vaultLand();
                    }
                    if (t > 40) {
                        finish(20);
                    }
                }
                case SMASH -> {
                    mob.getLookControl().setLookAt(target, 6.0F, 6.0F);
                    if (t == 4) {
                        mob.playSound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.4F, 0.5F);
                    }
                    if (t == 15) {
                        mob.whoosh(0.6F);
                    }
                    if (t == 20) {
                        mob.smashHit();
                    }
                    if (t > 38) {
                        finish(18);
                    }
                }
                case JUDGE -> {
                    // THE BEAM'S VERDICT: the pole's foot planted, up, steered onto them, down at their side
                    LivingEntity judged = mob.judgedOne();
                    if (judged == null) {
                        finish(10);
                        return;
                    }
                    mob.getLookControl().setLookAt(judged, 30.0F, 30.0F);
                    if (t == 4) {
                        mob.playSound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.6F, 0.5F);
                    }
                    if (t == 10) {
                        mob.vaultLeap(judged);
                        mob.playSound(FFSounds.LAMP_WHOOSH.get(), 2.4F, 0.5F);
                    }
                    if (t > 10 && t < 28) {
                        mob.judgeSteer(judged, 28 - t);
                    }
                    if (t == 28 || (t > 16 && t < 28 && mob.onGround())) {
                        if (t < 28) {
                            mob.attackTicks = 28;
                        }
                        mob.judgeHit(judged);
                    }
                    if (t > 40) {
                        finish(24);
                    }
                }
                case FLASH -> {
                    mob.getLookControl().setLookAt(target, 6.0F, 6.0F);
                    if (t == 4) {
                        mob.playSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.6F, 1.8F);     // the shutters, creaking
                    }
                    if (t == 14 || t == 18 || t == 21) {
                        mob.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.8F + t * 0.03F);
                    }
                    if (t == FLASH_AT) {
                        mob.flash();
                    }
                    if (t > 44) {
                        finish(20);
                    }
                }
                default -> {
                }
            }
        }

        private void finish(int cd) {
            mob.setAttackState(0);
            cooldown = cd;
        }
    }

    // ------------------------------------------------------------------ animation, sound, save
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            // into the entrance at once (its first frame is the sleep it wakes from): its clip runs on the very
            // ticks the scene's cuts and sounds are timed to (BossScenes) - and out of it as from any other
            state.getController().transitionLength(getAttackState() == INTRO ? 0 : 3);
            if (isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (isDormant()) {
                return state.setAndContinue(DORMANT_ANIM);
            }
            switch (getAttackState()) {
                case WAKE: return state.setAndContinue(WAKE_ANIM);
                case INTRO: return state.setAndContinue(INTRO_ANIM);
                case SWEEP: return state.setAndContinue(SWEEP_ANIM);
                case THRUST3: return state.setAndContinue(THRUST3_ANIM);
                case WHIRL: return state.setAndContinue(WHIRL_ANIM);
                case VAULT:
                case JUDGE: return state.setAndContinue(VAULT_ANIM);
                case SMASH: return state.setAndContinue(SMASH_ANIM);
                case FLASH:
                case OPEN: return state.setAndContinue(FLASH_ANIM);
                case SIGNAL:
                case WATCH: return state.setAndContinue(SIGNAL_ANIM);
                case STAGGER: return state.setAndContinue(STAGGER_ANIM);
                default: break;
            }
            return state.setAndContinue(FrostServantEntity.going(state) ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && !isDormant() && hurtTime > 0 && getAttackState() == 0
                        ? state.setAndContinue(HURT) : PlayState.STOP));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isDormant() ? null : SoundEvents.CHAIN_STEP;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GLASS_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BEACON_DEACTIVATE;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Dormant", isDormant());
        tag.putBoolean("Signalled", signalled);
        tag.putBoolean("Watching", watching);
        tag.putBoolean("Gated", gated);
        if (home != null) {
            tag.putLong("Home", home.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Dormant")) {
            entityData.set(DORMANT, tag.getBoolean("Dormant"));
        }
        signalled = tag.getBoolean("Signalled");
        watching = tag.getBoolean("Watching");
        gated = tag.getBoolean("Gated");
        if (tag.contains("Home")) {
            home = BlockPos.of(tag.getLong("Home"));
            findLantern();
            if (!isDormant()) {
                setBeam(watching ? BEAM_HUNT : signalled ? BEAM_TWO : BEAM_ONE, watching ? 0.0F : signalled ? 3.0F : 2.25F);
            }
        }
    }
}
