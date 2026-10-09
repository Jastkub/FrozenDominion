package com.jastkub.frozenfortress.entity.boss;

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
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * A mirror of Velkhar woven from blizzard-light. It walks like him, swings
 * like him - but one clean hit reveals the lie in a burst of snow.
 */
public class VelkharCloneEntity extends Monster implements GeoEntity {

    // A copy woven in the second phase stands and moves like the second-phase
    // king, not like the phase-one tank. It used to loop the phase-one idle -
    // upright, braced, square - so beside a hunched Velkhar it read as a
    // different, stiffer man standing to attention. Same coiled arc
    // and same run he uses now.
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.velkhar.idle_duel");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.velkhar.run2");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.velkhar.run2");
    private static final RawAnimation SLASH = RawAnimation.begin().thenPlay("animation.velkhar.combo1");
    private static final RawAnimation LUNGE =
            RawAnimation.begin().thenPlay("animation.velkhar.lunge_strike");
    private static final RawAnimation SWEEP =
            RawAnimation.begin().thenPlay("animation.velkhar.pivot_sweep");
    private static final RawAnimation PHANTOM_LEAP =
            RawAnimation.begin().thenPlay("animation.velkhar.phantom_leap");

    /**
     * Where in Velkhar's set piece this copy is, or -1 when it is its own mob
     * again.
     *
     * <p>SYNCED, because everything it drives is drawn: which clip is playing
     * and how much of the ice greatsword exists. Driven from HIS attack tick
     * rather than counted here, so the three of them cannot drift apart - a
     * copy running its own counter is a copy that swings a frame late, and
     * three silhouettes arriving abreast is the entire picture the attack
     * exists for.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> RITE_TICK =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    VelkharCloneEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);
    /**
     * THE MOVE IT IS IN, FOR THE CLIENT. The move was a field on the server only, and the animation controller - which runs on the client -
     * read that field there, where it was always CLONE_NONE: the blow landed on its tick and the copy went on walking.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> MOVE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    VelkharCloneEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);

    /**
     * The blade's life on his clock, mirrored here exactly.
     *
     * <p>EDGE_SNAP is the frame the copies step out of him, and the sword is
     * simply THERE on it - full size, no ramp. It used to grow from EDGE_FORM
     * to EDGE_FULL, which meant the copies ran the whole approach empty-handed
     * and only became armed once everyone had stopped moving. EDGE_FORM still
     * matters, but only as the frame the clip changes from the run to the
     * leap.
     */
    private static final int EDGE_SNAP = 18;
    private static final int EDGE_FORM = 44, EDGE_GONE = 84;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private int lifetime = 240;

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(SHADE, true);
        super.defineSynchedData(builder);
        builder.define(RITE_TICK, -1);
        builder.define(MOVE, 0);
        builder.define(STORM_RUSH, false);
    }

    // ================================================================
    // THE WHITEOUT'S COPIES. In his blizzard a copy is no fighter: it is one run out of the
    // white at one player - the king's own run in every way but two. Its feet are air (whiteout_ghost, where his are
    // iron: whiteout_rush), and close to, it shimmers (VelkharCloneRenderer). Struck by a player, it bursts and the
    // storm bites back (punishStriker); left alone, it cuts through them as nothing and is gone.
    // ================================================================
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> STORM_RUSH =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    VelkharCloneEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** The run: as fast as his, turning as little (VelkharEntity.RUSH_SPEED, RUSH_TURN). */
    private static final double RUSH_SPEED = 0.42D;
    private static final double RUSH_TURN = Math.toRadians(5.0D);
    private static final double RUSH_REACH = 2.4D;
    private static final int RUSH_MAX = 60;
    @javax.annotation.Nullable
    private java.util.UUID rushAt;
    private net.minecraft.world.phys.Vec3 rushDir = net.minecraft.world.phys.Vec3.ZERO;
    private int rushTicks;
    private int rushCut = -1;

    /** True while this copy is one of the whiteout's runs. */
    public boolean isStormRush() {
        return entityData.get(STORM_RUSH);
    }

    /** Send this copy running at `target` out of the storm, from where it stands. */
    public void stormRush(net.minecraft.world.entity.player.Player target) {
        entityData.set(STORM_RUSH, true);
        reveal();
        setRiteTick(-1);
        rushAt = target.getUUID();
        rushTicks = 0;
        rushCut = -1;
        net.minecraft.world.phys.Vec3 to = target.position().subtract(position());
        rushDir = new net.minecraft.world.phys.Vec3(to.x, 0.0D, to.z).normalize();
        faceRush();
        lifetime = RUSH_MAX + 30;
        level().playSound(null, this, FFSounds.WHITEOUT_GHOST.get(), net.minecraft.sounds.SoundSource.HOSTILE, 2.4F,
                0.95F + random.nextFloat() * 0.1F);
    }

    private void faceRush() {
        float aim = (float) (Math.toDegrees(Math.atan2(rushDir.z, rushDir.x)) - 90.0D);
        setYRot(aim);
        yBodyRot = aim;
        yHeadRot = aim;
    }

    /** One tick of the run (server): on at its player, turning a little after them; a cut at reach, then gone. */
    private void tickStormRush() {
        rushTicks++;
        getNavigation().stop();
        net.minecraft.world.entity.player.Player target = rushAt == null ? null : level().getPlayerByUUID(rushAt);
        if (rushCut >= 0) {
            // through them as nothing: it carries on a few steps and is gone
            setDeltaMovement(rushDir.x * RUSH_SPEED * 0.6D, getDeltaMovement().y, rushDir.z * RUSH_SPEED * 0.6D);
            if (rushTicks - rushCut >= 9) {
                dissolve(false);
            }
            return;
        }
        if (target != null && target.isAlive()) {
            net.minecraft.world.phys.Vec3 to = target.position().subtract(position());
            net.minecraft.world.phys.Vec3 want = new net.minecraft.world.phys.Vec3(to.x, 0.0D, to.z);
            if (want.lengthSqr() > 1.0E-4D) {
                want = want.normalize();
                double have = Math.atan2(rushDir.z, rushDir.x);
                double need = Math.atan2(want.z, want.x);
                double turn = net.minecraft.util.Mth.wrapDegrees(Math.toDegrees(need - have));
                double step = Math.max(-Math.toDegrees(RUSH_TURN), Math.min(Math.toDegrees(RUSH_TURN), turn));
                double ang = have + Math.toRadians(step);
                rushDir = new net.minecraft.world.phys.Vec3(Math.cos(ang), 0.0D, Math.sin(ang));
            }
            if (to.horizontalDistance() < RUSH_REACH) {
                rushCut = rushTicks;
                beginMove(CLONE_SLASH);                       // the cut that is nothing
            }
        }
        faceRush();
        setDeltaMovement(rushDir.x * RUSH_SPEED, getDeltaMovement().y, rushDir.z * RUSH_SPEED);
        if (rushTicks > RUSH_MAX) {
            dissolve(false);
        }
    }

    /** Gone into the storm: a puff of snow (a thing with a body - AttackFxEntity), and a burst if it was struck. */
    private void dissolve(boolean struck) {
        if (level() instanceof ServerLevel serverLevel) {
            com.jastkub.frozenfortress.entity.AttackFxEntity.spawn(serverLevel,
                    com.jastkub.frozenfortress.entity.AttackFxEntity.SNOW_BURST, position(), getYRot(),
                    struck ? 1.5F : 1.0F, 20, null);
            if (struck) {
                playSound(FFSounds.WHITEOUT_SHATTER.get(), 2.2F, 0.95F + random.nextFloat() * 0.1F);
                serverLevel.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY(1.0D), getZ(), 24,
                        0.4D, 0.9D, 0.4D, 0.18D);
            }
        }
        discard();
    }

    /**
     * THE STORM'S DUE, from whoever struck a copy: slowed, the strength gone out of the arm, the cold in - and a bite.
     * Long enough to cost the next run (the real one may well be behind it), short enough to live through.
     */
    private void punishStriker(net.minecraft.world.entity.player.Player striker) {
        striker.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
        striker.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.WEAKNESS, 100, 0), this);
        striker.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.jastkub.frozenfortress.registry.FFEffects.FROSTBITE, 120, 0), this);
        striker.hurt(damageSources().mobAttack(this), 4.0F);
        striker.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.frozen_dominion.whiteout_illusion").withStyle(net.minecraft.ChatFormatting.AQUA), true);
    }

    /** Hand this copy his clock, or -1 to give it back its own AI. */
    public void setRiteTick(int tick) {
        entityData.set(RITE_TICK, tick);
        riteFed = 0;
    }

    /**
     * Ticks since he last handed this copy its clock.
     *
     * <p>THE COPY OWNS ITS OWN RELEASE NOW, and that is the fix for copies
     * that walk up to you and then stand there for the rest of the fight.
     *
     * <p>The set piece drives them: every tick of PHANTOM_EDGE he pushes his
     * attack tick into each copy, and at tick 60 he pushes -1 to cut them
     * loose. That works perfectly as long as the attack RUNS TO THE END - and
     * it does not have to. finish() can be called from the watchdog, from a
     * phase change, from anything that interrupts him. The moment it is, the
     * loop that would have released them stops running, and they are left
     * holding a rite tick nobody will ever clear.
     *
     * <p>Past that point the melee goal takes its early branch on every tick:
     * it has arrived, it is not launched, so it stops the navigation and
     * waits for a beat that is never coming. Walk up, stand, stare - exactly
     * as reported, and it survives until the copy dies.
     *
     * <p>So the copy times him out. If he has not spoken for half a second it
     * assumes the set piece is over, whatever the reason, and takes itself
     * back. The same rule cannot be got round by any new early exit, which is
     * the point: this was a promise one object made about another object's
     * lifetime, and those are the ones that break.
     */
    private int riteFed;
    private static final int RITE_SILENCE = 10;

    public int riteTick() {
        return entityData.get(RITE_TICK);
    }

    /** True while it is dancing to his tune rather than hunting. */
    public boolean inRite() {
        return riteTick() >= 0;
    }

    public VelkharCloneEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.1D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    public void setLifetime(int ticks) {
        this.lifetime = ticks;
    }

    // ================================================================
    // THE COPIES CAN FIGHT NOW.
    //
    // They had exactly one attack and it was not really an attack: walk into
    // range, call doHurtTarget on a twenty-four tick timer, repeat. No
    // wind-up, no beat, no reach - damage simply appeared when the distance
    // was under 3.6. From the receiving end that reads as a thing that walks
    // at you and occasionally hurts, which is what it was.
    //
    // THREE MOVES, taken off HIS second phase rather than invented, because
    // the whole point of a copy is that it fights like him. Each one is a real
    // little timeline - a wind-up you can read, one tick where the damage
    // lands, and a recovery you can punish - which is the same contract every
    // one of his own attacks keeps.
    //
    //   SLASH   close, quick, cheap. The bread and butter.
    //   LUNGE   it closes the gap itself, so backing off is not a free out.
    //   SWEEP   a wide arc that catches everything in front, so three copies
    //           standing in a line cannot all be answered by sidestepping one.
    //
    // They are DELIBERATELY WEAKER AND SLOWER THAN HIS. A copy that hits as
    // hard as the king makes the king redundant; these are here to crowd and
    // to punish standing still, and the damage numbers say so.
    // ================================================================
    private static final int CLONE_NONE = 0;
    private static final int CLONE_SLASH = 1;
    private static final int CLONE_LUNGE = 2;
    private static final int CLONE_SWEEP = 3;

    /** (wind-up, the tick damage lands, total length) per move. */
    private static final int[][] CLONE_BEATS = {
            {0, 0, 0},
            {6, 10, 22},      // slash
            {8, 16, 30},      // lunge
            {10, 18, 34},     // sweep
    };

    private int move;
    private int moveTick;
    private int moveCooldown;

    /** Which clip the controller should play, or 0 for none. */
    public int cloneMove() {
        return level().isClientSide ? entityData.get(MOVE) : move;
    }

    private void beginMove(int which) {
        move = which;
        moveTick = 0;
        entityData.set(MOVE, which);
    }

    /** One move's worth of work. Returns true while it owns the entity. */
    private boolean tickMove(LivingEntity target) {
        if (move == CLONE_NONE) {
            return false;
        }
        moveTick++;
        int[] beat = CLONE_BEATS[move];
        getNavigation().stop();

        if (move == CLONE_LUNGE && moveTick == beat[0]) {
            // IT COVERS THE GROUND ITSELF. A lunge that does not travel is a
            // slash with a longer wind-up, and the reason this move exists is
            // that walking away from a copy should not be free.
            Vec3 to = target.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            if (flat.lengthSqr() > 1.0E-4D) {
                flat = flat.normalize().scale(Math.min(1.15D, flat.length() * 0.22D));
                setDeltaMovement(flat.x, getDeltaMovement().y, flat.z);
                hasImpulse = true;
            }
            playSound(FFSounds.VELKHAR_AIRCUT.get(), 1.6F, 1.35F);
        }

        if (moveTick == beat[1]) {
            double reach = move == CLONE_SWEEP ? 3.8D : 3.2D;
            double arc = move == CLONE_SWEEP ? -0.1D : 0.45D;
            Vec3 look = getViewVector(1.0F);
            for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(reach),
                    e -> e.isAlive() && e != this
                            && !com.jastkub.frozenfortress.entity.FFAllies.ofTheKing(e)
                            && !(e instanceof net.minecraft.world.entity.player.Player pl
                                 && (pl.isCreative() || pl.isSpectator())))) {
                Vec3 at = victim.position().subtract(position());
                Vec3 flat = new Vec3(at.x, 0.0D, at.z);
                if (flat.lengthSqr() > 1.0E-4D && look.dot(flat.normalize()) < arc) {
                    continue;          // in front of it, not all round it
                }
                victim.hurt(damageSources().mobAttack(this),
                            move == CLONE_SWEEP ? CLONE_SWEEP_DAMAGE : CLONE_HIT_DAMAGE);
            }
            playSound(FFSounds.VELKHAR_CLEAVE.get(), 1.8F,
                      move == CLONE_SWEEP ? 1.15F : 1.3F);
        }

        if (moveTick >= beat[2]) {
            move = CLONE_NONE;
            entityData.set(MOVE, CLONE_NONE);
            moveCooldown = CLONE_REST;
            return false;
        }
        return true;
    }

    /** Weaker than his, on purpose - see the note above. */
    // ================================================================
    // GLASS CANNONS. They hit hard and they die to one blow.
    //
    // A copy already has ONE point of health, so anything kills it - that half
    // was always right. The other half was not: five damage is two and a half
    // hearts before armour, so three of them could stand on a player and lose
    // the fight. A thing that dies to a single hit has to be worth killing
    // quickly, or ignoring it is simply correct play.
    //
    // Raw damage, not hearts - these go straight to hurt() and always have,
    // which is right here because a copy is not him and does not draw on his
    // lifesteal, his floor or his phase scaling.
    // ================================================================
    // (07.10.2026) a copy lasts three blows now, so it hits for less: it was 15 and 19
    private static final float CLONE_HIT_DAMAGE = 11.0F;
    private static final float CLONE_SWEEP_DAMAGE = 14.0F;
    /** And slower, so three of them is pressure rather than a blender. */
    private static final int CLONE_REST = 26;

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new CloneMeleeGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    /**
     * How many ticks this copy is committed to a leap it did not choose.
     *
     * <p>Setting a velocity on a clone does nothing on its own: its navigation
     * runs every tick and overwrites the movement immediately, which is why
     * telling the copies to jump with Velkhar had no visible effect at all.
     * They have to be told to STOP STEERING for the length of the jump - a
     * body in the air is not walking anywhere, and its own pathing is exactly
     * what has to be silenced for that to be true.
     */
    private int launchTicks;

    /**
     * Has this copy finished the jump it was made for?
     *
     * <p>UNTOUCHABLE UNTIL IT LANDS. A copy that can be swatted out of the air
     * on the way in is a copy the attack never happens with - they are woven,
     * sent, and killed before anybody has to decide which of the three is
     * real. So they are immune for the whole approach and the whole leap, and
     * the instant they come down they are glass: one hit from anything.
     *
     * <p>That is the trade the attack is built on. The player cannot pre-empt
     * it, and does not have to grind it down either.
     */
    private boolean landed;

    /**
     * IS IT STILL A SHADOW? Synced, because the renderer decides a texture off
     * it.
     *
     * <p>They are not copies of him until they have arrived. While this is
     * true they wear velkhar_shade - nearly black, with only his cold left in
     * it - and they are peeling out of his sides; the tick they come down on
     * somebody they become the mirror everybody has to tell apart from the
     * real thing. That order is the whole trick of the attack: you watch two
     * shadows leave a man, and only when they land do they turn out to be him.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> SHADE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    VelkharCloneEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    /** True while it is still a shadow rather than a copy. */
    public boolean isShade() {
        return entityData.get(SHADE);
    }

    /**
     * How far out of him it is, 0 to 1, for the renderer's fade-in.
     *
     * <p>Derived from its own age rather than synced: the emergence is a fixed
     * length and both sides can count, so this costs no network traffic and
     * cannot arrive a tick late the way a synced float would - which on a
     * thirty-tick fade is a visible step.
     */
    public float shadeForm() {
        return Math.min(1.0F, tickCount / 30.0F);
    }

    /** Throw this copy along a velocity and take its feet away from it. */
    public void launch(net.minecraft.world.phys.Vec3 velocity, int ticks) {
        setDeltaMovement(velocity);
        hasImpulse = true;
        launchTicks = ticks;
        getNavigation().stop();
        float aim = (float) (Math.toDegrees(Math.atan2(velocity.z, velocity.x)) - 90.0D);
        setYRot(aim);
        yBodyRot = aim;
        yHeadRot = aim;
    }

    /** True while the copy is mid-leap and its own AI must not steer. */
    public boolean isLaunched() {
        return launchTicks > 0;
    }

    /**
     * Where this copy should run to while it is escorting him in.
     *
     * <p>RUNNING AT THE PLAYER WAS WRONG. A copy moves at 0.32 and he moves at
     * 0.3, so given the same order the copies arrive first and stand on top of
     * the player while he is still crossing the floor. Then the jump fires and
     * the gap they have left to cover is nothing, so they hop on the spot while
     * he sails in - three silhouettes arriving abreast turns into one arriving
     * and two already standing there.
     *
     * <p>They escort a point BESIDE HIM instead, so the formation holds and all
     * three still have the same distance to jump when the moment comes.
     */
    private double escortX, escortZ;
    private boolean escorting;

    public void escortTo(double x, double z) {
        escortX = x;
        escortZ = z;
        escorting = true;
    }


    @Override
    public void aiStep() {
        // ---- TAKE ITSELF BACK IF HE HAS STOPPED TALKING. See riteFed.
        if (!level().isClientSide && inRite()) {
            if (++riteFed > RITE_SILENCE) {
                setRiteTick(-1);
            }
        }
        // The leap is honoured BEFORE the AI runs, because the AI is exactly
        // what would overwrite it.
        if (launchTicks > 0) {
            launchTicks--;
            getNavigation().stop();
            if (launchTicks == 0) {
                // down, and mortal from this frame on - and solid, which is
                // the beat the whole attack is built to deliver
                landed = true;
                entityData.set(SHADE, false);
            }
        }
        if (!level().isClientSide && isStormRush()) {
            tickStormRush();
        }
        super.aiStep();
        if (!level().isClientSide && --lifetime <= 0) {
            if (isStormRush()) {
                dissolve(false);
                return;
            }
            shatter();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return super.hurt(source, amount);
        }
        // A mirror is dispelled by the person it is lying to - and by nobody
        // else. It used to shatter on *any* damage with an owner attached,
        // which meant the king's own spike rings and his servants' stray shots
        // wiped out his illusions seconds after he cast them.
        // IN THE AIR, NOTHING TOUCHES IT. Immune until the leap has landed -
        // see `landed`. This is checked before the attacker is even looked at,
        // because it holds against everything: the player, his own king, a
        // stray arrow from across the hall.
        // A COPY THAT NEVER LEAPT IS NOT IN THE AIR. `landed` starts false and
        // is only ever set by a launch finishing, so a copy spawned without one
        // was immune for its whole life - which is not "untouchable mid-leap",
        // it is untouchable full stop.
        if (!landed && isLaunched()) {
            return false;
        }
        net.minecraft.world.entity.Entity attacker = source.getEntity();
        // a whiteout's run: struck by a player it bursts, and the storm bites them; nothing else touches it
        if (isStormRush()) {
            if (attacker instanceof net.minecraft.world.entity.player.Player striker && isAlive()) {
                punishStriker(striker);
                dissolve(true);
                return true;
            }
            return false;
        }
        if (attacker instanceof VelkharEntity || attacker instanceof VelkharCloneEntity
                || attacker instanceof com.jastkub.frozenfortress.entity.FrostServantEntity) {
            return false;
        }
        if (attacker == null) {
            // environmental - a fall, a freeze - leaves an illusion untouched
            return false;
        }
        // a copy on its own
        // is a fighter, not a balloon: three blows break it, each one cracking it - and a sweep that touches it twice in
        // one swing counts once
        if (tickCount - lastCracked < 8) {
            return false;
        }
        lastCracked = tickCount;
        if (++cracks >= HITS_TO_BREAK) {
            shatter();
            return true;
        }
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY(1.0D), getZ(), 18, 0.35D, 0.8D, 0.35D, 0.12D);
        }
        playSound(FFSounds.ICE_CRACK.get(), 1.4F, 0.9F + cracks * 0.2F);
        net.minecraft.world.phys.Vec3 back = position().subtract(attacker.position());
        if (back.horizontalDistanceSqr() > 1.0E-4D) {
            back = new net.minecraft.world.phys.Vec3(back.x, 0.0D, back.z).normalize().scale(0.35D);
            setDeltaMovement(back.x, 0.15D, back.z);
            hasImpulse = true;
        }
        moveCooldown = Math.max(moveCooldown, 10);          // staggered a breath
        return true;
    }

    /** Blows it has taken (it breaks at HITS_TO_BREAK), and the tick of the last. */
    private int cracks;
    private int lastCracked = -100;
    private static final int HITS_TO_BREAK = 3;

    /** Cut loose from his set piece: its own fight, for `ticks` at most. */
    public void release(int ticks) {
        setRiteTick(-1);
        lifetime = Math.min(lifetime, ticks);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.SOUL_FROST.get(), getX(), getY(1.0D), getZ(), 24, 0.4D, 1.0D, 0.4D, 0.06D);
        }
    }

    /** Stepped out of the air as a copy at once (the Mirror Storm's): no shadow, no leap - mortal and real-looking. */
    public void reveal() {
        landed = true;
        entityData.set(SHADE, false);
    }

    /** Its own blow, now, at whoever it is facing (his set pieces time it with his). */
    public void swingNow(boolean wide) {
        beginMove(wide ? CLONE_SWEEP : CLONE_SLASH);
        moveCooldown = 0;
    }

    /** Illusions are weightless and do not drown, burn or fall to their deaths. */
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    public void shatter() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.BLIZZARD_FLAKE.get(),
                    getX(), getY(1.0D), getZ(), 40, 0.5D, 1.5D, 0.5D, 0.08D);
            serverLevel.sendParticles(FFParticles.FROST_SWIRL.get(),
                    getX(), getY(1.0D), getZ(), 20, 0.4D, 1.2D, 0.4D, 0.05D);
            playSound(FFSounds.FROST_RELEASE.get(), 1.2F, 1.3F);
        }
        discard();
    }

    static class CloneMeleeGoal extends Goal {
        private final VelkharCloneEntity mob;

        /** How long this goal has been standing down for his set piece. */
        private int heldTicks;
        /** Ticks since its path was last asked for. */
        private int repath;
        private static final int RITE_PATIENCE = 100;

        CloneMeleeGoal(VelkharCloneEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !mob.isStormRush() && mob.getTarget() != null && mob.getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            // WHILE HIS SET PIECE IS RUNNING, THE COPY IS NOT HUNTING.
            //
            // It runs in beside him and then it stops and swings when he
            // swings - a copy that broke off to throw its own melee mid-charge
            // would land a hit out of nowhere during an animation that has its
            // own blow scheduled, and would break the abreast picture. It
            // takes its aggression back the tick the blades go out; nothing
            // else about it changes, which is why there is no second AI here.
            // ================================================================
            // A SECOND GUARD, IN THE GOAL, AND IT IS NOT REDUNDANT.
            //
            // The entity already times him out (see riteFed) and the copies
            // were STILL walking up and standing there. Whatever is holding
            // them, one object trusting another object to release it is the
            // shape of the bug, and adding a second promise of the same kind
            // would be making the same mistake twice.
            //
            // So this one is unconditional and local: the set piece is 76
            // ticks long, the goal honours at most a hundred, and past that it
            // simply stops asking. Nothing outside this class can extend it.
            // ================================================================
            if (mob.inRite() && ++heldTicks > RITE_PATIENCE) {
                mob.setRiteTick(-1);
                heldTicks = 0;
            }
            if (!mob.inRite()) {
                heldTicks = 0;
            }
            if (mob.inRite()) {
                if (mob.riteTick() < EDGE_FORM) {
                    // beside him, not at the player - see escortTo()
                    if (mob.escorting) {
                        mob.getNavigation().moveTo(mob.escortX, mob.getY(),
                                                   mob.escortZ, 1.45D);
                    } else {
                        mob.getNavigation().moveTo(target, 1.45D);
                    }
                } else if (!mob.isLaunched()) {
                    mob.getNavigation().stop();
                }
                return;
            }
            mob.escorting = false;
            // a move owns the copy while it runs; it does not walk out of one
            if (mob.tickMove(target)) {
                return;
            }
            // (07.10.2026: "naprawilbym raz na zawsze") TO ITS MARK, PATH OR NO PATH. A path asked for every tick was
            // thrown away every tick, and among the hall's pillars a path that will not come leaves it standing there:
            // the path is asked for twice a second, and whenever there is none it simply steers straight at them
            if (++repath >= 10 || mob.getNavigation().isDone()) {
                repath = 0;
                boolean went = mob.getNavigation().moveTo(target, 1.05D);
                if (!went || mob.getNavigation().isDone()) {
                    mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.05D);
                }
            }
            if (mob.moveCooldown > 0) {
                mob.moveCooldown--;
                return;
            }
            double d = mob.distanceTo(target);
            if (d < 3.4D) {
                // in close it alternates, so two copies on the same target are
                // not two copies playing the same animation on the same frame
                mob.beginMove(mob.getRandom().nextInt(3) == 0
                        ? CLONE_SWEEP : CLONE_SLASH);
            } else if (d < 8.0D) {
                mob.beginMove(CLONE_LUNGE);
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            // HIS SET PIECE OWNS THE COPY WHILE IT IS RUNNING.
            //
            // Split on the same tick boundary his controller uses, so all
            // three switch clips on the same frame. Two separate one-shot
            // animations rather than one long one for the same reason he has
            // them: a run cannot be looped inside a one-shot, and faking it
            // with a velocity is what made the whole attack a slide.
            int rite = riteTick();
            if (rite >= 0) {
                return state.setAndContinue(rite < EDGE_FORM ? RUN : PHANTOM_LEAP);
            }
            switch (cloneMove()) {
                case CLONE_SLASH: return state.setAndContinue(SLASH);
                case CLONE_LUNGE: return state.setAndContinue(LUNGE);
                case CLONE_SWEEP: return state.setAndContinue(SWEEP);
                default: break;
            }
            if (swinging) {
                return state.setAndContinue(SLASH);
            }
            // Movement read from the actual position delta, not state.isMoving().
            // The copy is AI-driven, so its velocity is not simulated on the
            // client - it slides in the idle pose exactly the way HE did until
            // the same fix. xo/zo -> position is the real on-screen travel.
            double mdx = getX() - xo;
            double mdz = getZ() - zo;
            if (state.isMoving() || mdx * mdx + mdz * mdz > 0.0004D) {
                return state.setAndContinue(WALK);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.ICE_CRACK.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.ICE_SHATTER.get();
    }

    /** A whiteout's copy runs on air: not a step out of it (his own are iron - VelkharEntity.playStepSound). */
    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (!isStormRush()) {
            super.playStepSound(pos, state);
        }
    }

    /** Clones drop nothing and grant nothing. */
    @Override
    protected boolean shouldDropLoot() {
        return false;
    }
}
