package com.jastkub.frozenfortress.entity;

import net.minecraft.world.entity.Entity;
import javax.annotation.Nullable;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Hollow Golem: an outline of a thing, and nothing inside it.
 *
 * <p>Two speeds, and the gap between them is the whole design. It walks at a
 * crawl - slower than a player can back away from, so it is never a chase -
 * and then the charge comes out at four times that, from a windup you get
 * roughly a second to read. A monster that is always slow is scenery; one
 * that is always fast is a chase. One that is slow until it is not is a
 * clock the player has to keep an eye on while they deal with Velkhar, which
 * is the entire reason it is summoned.
 *
 * <p>It is deliberately unkillable-by-attrition rather than unkillable: it
 * has real health, but it also expires. Killing it early is a choice worth
 * making and not making it is survivable.
 */
public class HollowGolemEntity extends Monster implements GeoEntity,
        net.minecraft.world.entity.PlayerRideableJumping {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.ice_monstrosity.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.ice_monstrosity.walk");
    private static final RawAnimation WINDUP = RawAnimation.begin().thenPlay("animation.ice_monstrosity.windup");
    private static final RawAnimation CHARGE = RawAnimation.begin().thenPlay("animation.ice_monstrosity.charge");
    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlay("animation.ice_monstrosity.emerge");
    /**
     * THE HEAVY ATTACKS HOLD THEIR LAST POSE, and the ones that do not, do not.
     *
     * <p>{@code thenPlay} returns the controller to idle the moment the clip
     * runs out, so a swing that finishes in fifty-eight ticks is over in
     * fifty-eight ticks - the colossus snaps upright and is immediately
     * dangerous again. That is why nothing it did could be punished: there was
     * no moment where it was visibly out of position.
     *
     * <p>{@code thenPlayAndHold} freezes the final frame instead, which lets
     * the pose and the lock-out be tuned separately. Combined with the
     * RECOVERY table below it gives the one thing the fight was missing: a
     * window where the animation says, unmistakably, that it cannot answer.
     */
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlayAndHold("animation.ice_monstrosity.slam");
    private static final RawAnimation SWEEP = RawAnimation.begin().thenPlay("animation.ice_monstrosity.sweep");
    private static final RawAnimation STOMP = RawAnimation.begin().thenPlayAndHold("animation.ice_monstrosity.stomp");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.ice_monstrosity.roar");
    private static final RawAnimation FISSURE = RawAnimation.begin().thenPlayAndHold("animation.ice_monstrosity.fissure");
    private static final RawAnimation LEAP = RawAnimation.begin().thenPlayAndHold("animation.ice_monstrosity.leap");
    private static final RawAnimation SHARDS = RawAnimation.begin().thenPlayAndHold("animation.ice_monstrosity.shards");
    private static final RawAnimation SWIPE = RawAnimation.begin().thenPlay("animation.ice_monstrosity.swipe");
    private static final RawAnimation DRAIN = RawAnimation.begin().thenPlayAndHold("animation.ice_monstrosity.drain");
    private static final RawAnimation BREATH_CLIP =
            RawAnimation.begin().thenPlayAndHold("animation.ice_monstrosity.breath");

    // ================================================================
    // THE SIX. Each one owns a different distance and a different answer,
    // which is the only thing that stops a big slow thing being one attack
    // repeated: SLAM punishes standing in front, SWEEP punishes circling,
    // STOMP punishes standing anywhere near, HURL punishes running away,
    // CHARGE punishes the long retreat and ROAR punishes waiting it out.
    // ================================================================
    /**
     * HOW LONG IT STAYS DOWN, per attack.
     *
     * <p>This is the shape of the reference's whole melee game and it was the
     * one thing not reproduced here. Its heaviest move animates for eight
     * ticks and holds the state for seventy-five; its charge animates for
     * twenty-two and holds seventy; even the quick shot is fourteen ticks of
     * motion against forty-four of commitment. The motion is a SNAP and the
     * rest is the price.
     *
     * <p>Ours ran the other way: every clip played out at full length and
     * ended standing, so the punish window was whatever the cooldown happened
     * to be - and during a cooldown it walks, turns and blocks like normal.
     * A cooldown is not a recovery. A recovery is time spent in a pose you can
     * see, unable to answer.
     *
     * <p>So the windows move into the attack itself. Heavier costs more: the
     * double slam is the biggest commitment it makes and pays forty-eight
     * ticks for it, while the swipe - which exists to reposition and does half
     * a heart - pays almost nothing. The cooldowns come down to match, because
     * the wait is now something the player can watch and use rather than
     * something that happens off screen.
     */
    private static final int REC_SLAM = 48;
    private static final int REC_STOMP = 34;
    private static final int REC_SHARDS = 30;
    private static final int REC_LEAP = 26;
    private static final int REC_FISSURE = 22;

    /**
     * HOW FULL THE BALL IN ITS FIST IS, 0 to 1.
     *
     * <p>Synced because the thing that draws it lives on the client and has no
     * other way of knowing: GolemOrbLayer is handed a bone and a partial tick,
     * and everything else it needs has to arrive over the wire.
     */
    private static final EntityDataAccessor<Float> ORB =
            SynchedEntityData.defineId(HollowGolemEntity.class, EntityDataSerializers.FLOAT);

    public float orb() {
        return entityData.get(ORB);
    }

    /**
     * Ticks into the breath, or 0 when the maw is shut.
     *
     * <p>An int rather than a flag so the client can shape the cone without
     * being told anything else: it grows out of the lips over the first six
     * ticks and fades over the last eight, both worked out from this number.
     */
    private static final EntityDataAccessor<Integer> BREATH_T =
            SynchedEntityData.defineId(HollowGolemEntity.class, EntityDataSerializers.INT);

    /** 0 to 1: how much breath there is this frame. See GolemBreathLayer. */
    public float breathPower(float partialTick) {
        int at = entityData.get(BREATH_T);
        if (at <= 0) {
            return 0.0F;
        }
        float tt = at + partialTick;
        return Math.min(1.0F, tt / 5.0F)
                * net.minecraft.util.Mth.clamp((BREATH_LEN + 1 - tt) / 8.0F, 0.0F, 1.0F);
    }

    /** 0 to 1: how far out of the maw it has reached yet. */
    public float breathReach(float partialTick) {
        int at = entityData.get(BREATH_T);
        return at <= 0 ? 0.0F : Math.min(1.0F, (at + partialTick) / 6.0F);
    }

    private static final int ATK_NONE = 0;
    private static final int ATK_SLAM = 1;
    private static final int ATK_SWEEP = 2;
    private static final int ATK_STOMP = 3;
    private static final int ATK_ROAR = 5;
    // ATK_BREATH IS GONE. It was thirty-two ticks of seven particles a tick
    // played over the roar - arms up, maw open, and a scatter of flakes so
    // thin that what it read as was the golem miming an attack. The damage
    // was real and nobody could tell, which is the worst of both: a cone that
    // punishes standing in front of it and gives no reason to move.
    //
    // Deleted rather than thickened. The maw earns its keep on the roar and
    // on the bite at the end of a slam; it does not need a beam attack that
    // only works if somebody explains it.

    /**
     * THE FISSURE. One paw into the floor, and the floor carries it.
     *
     * <p>Every other thing it does happens where IT is - a slam under its
     * fists, a cone out of its mouth, a rock thrown on a line you can watch.
     * This one happens where YOU are, and nothing travels through the air to
     * warn you: the warning is a crack running across the floor, and the only
     * counter is to not be standing at the end of it when it arrives.
     *
     * <p>AIMED WHERE THEY WERE, NOT WHERE THEY ARE. The line is fixed on the
     * tick the paw lands and never corrects, which is what makes reading it
     * worth anything - a crack that follows you is an unavoidable hit with a
     * long animation in front of it.
     */
    private static final int ATK_FISSURE = 8;

    /**
     * THE LEAP. It jumps, and that is the point.
     *
     * <p>Nothing that shape should be able to leave the floor - the legs are
     * a quarter of its height under a body two thirds of its width - so the
     * jump itself is the event. Everything else it does is weight moving
     * along the ground; this is weight ARRIVING.
     *
     * <p>It lands where they are standing when it takes off, and the flight
     * is long enough to walk out of. That is the trade: the most damaging
     * thing it has, telegraphed by the slowest and most obvious wind-up it
     * has, aimed at a spot the player watched it choose.
     */
    private static final int ATK_LEAP = 9;


    /**
     * THE BOMBS. It opens a hand and three of them go out.
     *
     * <p>THREE, AND THEY ARE BIG. A spray of small fast shards is a shotgun,
     * and a shotgun is answered once - step aside and the whole thing is
     * spent. Three heavy ones leaving a trail are three separate problems
     * arriving on three separate beats, each one worth watching, and the
     * ground they land on is the attack rather than the things themselves.
     *
     * <p>SLOW ENOUGH TO SEE. They travel at two thirds the speed of the
     * shards this replaced, which is what makes the trail read as a trail and
     * not as a smear - and what gives the player the whole flight to decide
     * where not to be standing.
     *
     * <p>They BURST. A projectile that pricks one target is a worse version
     * of the boulder; these open a circle where they land, so the answer is
     * the floor rather than the line.
     */
    private static final int ATK_SHARDS = 11;

    /**
     * THE SWIPE. It moves you, and that is all it is for.
     *
     * <p>Everything else in the roster answers a question about damage. This
     * one answers a question about SPACE: somebody stood under its chin, where
     * the slam and the sweep both overshoot and where the charge and the bombs
     * cannot be aimed at all. A creature this size having no answer to that is
     * why hugging its legs was the safest place on the floor.
     *
     * <p>So it costs almost nothing in health and everything in position - a
     * backhand that throws you most of the way out of its own reach. Short
     * wind-up, because it is not a commitment: the punishment for being close
     * is that you stop being close, and then the attacks that need range work
     * again.
     */
    private static final int ATK_SWIPE = 12;

    /**
     * THE DRAIN, and it is the only attack here that is not an attack.
     *
     * <p>Everything else in the roster asks the player a question about
     * dodging. This one asks a question about HOUSEKEEPING, which is the one
     * idea in the reference worth taking whole: its bombs leave lava, its
     * drain eats the lava to heal, and so the ranged game is not "survive the
     * shot" but "how much of the floor are you willing to leave covered".
     *
     * <p>Three things are wired to the same crust and that is what makes it a
     * system rather than three attacks. The bombs put it down. The drain takes
     * it up. Taking it up heals the colossus AND reloads the bombs - so a
     * player who ignores the floor is fighting something that tops itself up
     * and never runs out of ammunition, and a player who clears it is fighting
     * something that eventually has nothing to shoot.
     *
     * <p>It will not start without fuel and it will not start at melee range,
     * so it is never a free heal: it has to choose a moment where it is not
     * being hit, and choosing wrong is the punish.
     */
    private static final int ATK_DRAIN = 13;

    /** How long it kneels, and how fast it feeds. */
    private static final int DRAIN_SETTLE = 16;
    private static final int DRAIN_END = 86;

    // ================================================================
    // THE BREATH. It is back, and the note further up about why it was
    // deleted is the brief for why this one is built the way it is: the old
    // one was a scatter of particles with no edge, and a cone you cannot see
    // the edge of cannot teach you where to stand. This one is drawn as
    // geometry with its own sheet (GolemBreathLayer, gen_frost_breath.py),
    // and its cone here is the same size and tilt as the one on screen.
    //
    //   0-22    the draw: it rears back and pulls the air in, jaw nearly shut.
    //           This is the tell, and it is long on purpose.
    //   22-102  four seconds of breath, jaw wide. It follows you round at the
    //           body's own turn rate, so a player walking sideways outpaces
    //           it and a player standing still does not.
    //   102-120 the maw closes and it recovers, locked in place.
    //
    // What it does is CHILL, and chill is a clock per victim: every tick in
    // the cone adds one, every tick out of it takes two away. Slowness rises
    // with it. Two and a half seconds of it without a break and you are
    // frozen solid - the hunter orbs' freeze, prison and all. And when the
    // breath stops, anyone still carrying more than a second of it freezes
    // then, which is the "and finally" of the attack: getting out late is
    // not the same as getting out.
    // ================================================================
    private static final int ATK_BREATH = 14;
    private static final int BREATH_DRAW = 22;
    private static final int BREATH_LEN = 80;
    private static final int BREATH_END = BREATH_DRAW + BREATH_LEN;
    private static final int BREATH_DONE = 120;
    /** Degrees a tick it comes round after them while it breathes. */
    private static final float BREATH_TURN = 2.4F;
    /** How far it carries, and the half-angle of the cone, in degrees. */
    // SIXTEEN (nine until 06.10.2026); the gust is drawn
    // to match it in GolemBreathLayer
    private static final double BREATH_REACH = 16.0D;
    private static final double BREATH_HALF = 31.0D;          // (as wide as its gust is drawn now)
    /** Degrees below level: matches the head the animation poses. */
    private static final double BREATH_TILT = 22.0D;
    /** Ticks of unbroken chill that freeze a victim during the breath... */
    private static final int BREATH_FREEZE_AT = 50;
    /** ...and the chill still carried at the end that freezes them then. */
    private static final int BREATH_FINAL_FREEZE = 24;
    /** Hearts every ten ticks in the cone; the freeze is the threat, not this. */
    private static final float BREATH_DAMAGE = 0.35F;
    /** Game ticks between breaths, so it stays an event. */
    private static final long BREATH_EVERY = 520L;
    private final java.util.Map<Integer, Integer> chill = new java.util.HashMap<>();
    private final java.util.Set<Integer> frozenByBreath = new java.util.HashSet<>();
    private long breathReadyAt;
    private static final double DRAIN_REACH = 11.0D;
    /** Blocks taken per feeding tick, and health per block. */
    private static final int DRAIN_RATE = 3;
    private static final float DRAIN_HEAL = 1.4F;
    /** It will not bother for less than this much on the floor. */
    private static final int DRAIN_WORTH = 14;

    /**
     * BOMBS LEFT BEFORE IT HAS TO FEED.
     *
     * <p>An ammunition count is the only thing that makes the crust matter. A
     * heal alone can be out-damaged and then ignored; a heal that is also the
     * reload cannot, because ignoring it means the ranged attack never stops.
     */
    private static final int SHARD_MAG = 3;
    private int magazine = SHARD_MAG;

    /** The tick the backhand lands, and how far along the arm it reaches. */
    private static final int SWIPE_HIT = 12;
    private static final double SWIPE_REACH = 5.0D;

    /** Where the crack started, which way it runs, and how far. */
    private Vec3 fissureFrom;
    private Vec3 fissureDir;
    private double fissureLen;
    /** How far it will reach, and how many ticks it takes to get there. */
    private static final double FISSURE_REACH = 22.0D;
    private static final int FISSURE_STEPS = 16;

    /**
     * Where the bombs leave from: the right palm, held out in front.
     *
     * <p>Fired from the HAND rather than from the entity's own position, which
     * is at its feet and inside its own hitbox.
     */
    /**
     * WHERE THE RIGHT HAND ACTUALLY IS, solved off the rig instead of guessed.
     *
     * <p>Both of these were one static offset, and that is a bug with two
     * halves. The shoulder sits 3.27 blocks outboard of the centreline - AX is
     * 52 model units on a model that measures 15.9 units to the block - and
     * the offset used 1.4, so the orb charged somewhere out in front of the
     * creature's chest rather than in the raised fist. And there was only ONE
     * position for an attack whose whole point is that the arm MOVES: the clip
     * snaps the shoulder from -118 degrees overhead to -48 forward on the tick
     * it fires, so the bombs were spawning where the hand had been for the
     * last second and a half, not where it was when they left.
     *
     * <p>Hand hangs 3.1 blocks below a shoulder 5.2 up, so the rest is
     * trigonometry on the clip's own angles: overhead at -104 puts it 5.9 up
     * and 4.1 forward, thrown out at -48 puts it 3.1 up and 3.4 forward.
     *
     * <p>{@code (-out.z, 0, out.x)} is the creature's RIGHT: facing -Z, right
     * is forward cross up, which is +X, and that expression returns +X for a
     * forward of -Z. It is the right arm the clip animates.
     *
     * @param thrown false while it charges, true on the tick it lets go
     */
    private Vec3 shardPalm(boolean thrown) {
        Vec3 out = getForward();
        // RE-SOLVED for the simplified rig. The shoulder bone now sits at 88
        // units - 5.5 blocks - and the hand box centres at 23, so the hand
        // hangs 4.06 blocks under the shoulder; the rest is the clip's own
        // angles. Overhead at -104 puts it 6.5 up and 3.9 forward, thrown out
        // at -48 puts it 2.8 up and 3.0 forward, and the arm bone is 52 units
        // outboard, which is 3.25.
        double fwd = thrown ? 3.0D : 3.9D;
        double up = thrown ? 2.8D : 6.5D;
        return position()
                .add(out.scale(fwd))
                .add(-out.z * 3.25D, 0.0D, out.x * 3.25D)
                .add(0.0D, up, 0.0D);
    }

    private Vec3 shardPalm() {
        return shardPalm(false);
    }

    /**
     * WHERE THE BOMBS LEAVE: the middle of its right hand (hand_r at Bedrock 59, 12, 0) in the
     * "shards" clip's held pose, measured with tools/pose_model.py. In blocks from its feet:
     * to its right, up, forward along its facing. The client draws the orb on the tracked hand
     * bone itself (HollowGolemRenderer), so it is in the hand whatever frame the arm is in.
     */
    //  re-measured on the reworked model:
    // tools/pose_model.py, "shards" at 1.9 s (the orb held, the tick before the throw), hand_r's pivot + the orb layer's
    // palm offset (0, -4.5, -2) px - so the bombs leave exactly where the orb was drawn. It was -3.18, 5.09, 5.57.
    public static final double PALM_RIGHT = -2.52D, PALM_UP = 4.59D, PALM_FWD = 5.81D;
    //

    /** Where the javelin leaves the right hand (the "javelin" clip at 1.0 s), and the tick it does. */
    private static final double JAVELIN_RIGHT = -4.59D, JAVELIN_UP = 7.51D, JAVELIN_FWD = 4.63D;
    private static final int JAVELIN_LET_GO = 20;

    /** A point on its body, as palmAt: to its right (negative: its left - GeckoLib mirrors x), up, forward. */
    public static Vec3 atBody(Vec3 feet, float yaw, double right, double up, double fwd) {
        Vec3 f = Vec3.directionFromRotation(0.0F, yaw);
        return feet.add(f.scale(fwd)).add(-f.z * right, up, f.x * right);
    }

    public static Vec3 palmAt(Vec3 feet, float yaw) {
        Vec3 f = Vec3.directionFromRotation(0.0F, yaw);
        return feet.add(f.scale(PALM_FWD)).add(-f.z * PALM_RIGHT, PALM_UP, f.x * PALM_RIGHT);
    }

    /** Where it last saw what it is throwing at (a throw still lands if they slip out of sight). */
    private Vec3 lastAim;

    /**
     * When it throws, how many, how fast and how hard they fall.
     *
     * <p>The wind-up is long on purpose - forty ticks of a raised arm with a
     * ball growing in the hand. It is the only attack it has that can be read
     * from across the room before anything leaves, and that reading is what
     * pays for how far they reach.
     */
    private static final int SHARD_FIRE = 40;
    private static final int SHARD_COUNT = 3;

    /**
     * THE DROP PER TICK - and this number is why the attack did not work.
     *
     * <p>FrostBoltEntity overrides {@code getInertia()} to return 1. That one
     * line changes what every other number on it means: vanilla's projectile
     * tick does {@code delta = (delta + power) * inertia}, so with no drag the
     * thrust is not a speed, it is an ACCELERATION that keeps being added for
     * the whole flight. The working call site - Velkhar's heart barrage - is
     * tuned to that: speed 0.34, arc 0.0016.
     *
     * <p>This one was written as though the thrust were a velocity: speed
     * about 1.16 and an arc of 0.045, which is twenty-eight times the barrage.
     * The arithmetic is not close. {@code yPower} loses 0.045 every tick and
     * the delta accumulates it, so the downward speed after ten ticks is about
     * one and three quarter blocks a tick: the bombs left the hand, turned
     * over almost immediately and detonated at the colossus's own feet. The
     * report was describing three
     * projectiles nose-diving into the floor in front of it.
     *
     * <p>Back into the convention the rest of the mod uses, and the flight
     * time is solved against the real integrator below rather than guessed.
     */
    private static final double SHARD_ARC = 0.0021D;
    /** The circle each one opens where it lands. */
    private static final float SHARD_BLAST = 3.2F;


    /** True between the launch and the landing, so the landing fires once. */
    private boolean leapArmed;
    /** How hard it goes up, and how far it will chase across the floor. */
    private static final double LEAP_RISE = 1.05D;
    private static final double LEAP_REACH = 16.0D;

    // ================================================================
    // THE BOULDERS ARE GONE. All three of them.
    //
    // It had four ways of throwing rock: one heavy stone torn up and hurled,
    // a fan of five, and one held up on nothing and dropped. Between them
    // they were most of its roster, and they were all the same sentence -
    // "something large travels from it to you" - told at different sizes.
    // The ice bombs already tell that sentence better, because they open a
    // circle where they land and the answer becomes the floor rather than
    // the line.
    //
    // What is left is what makes it a colossus rather than a catapult: the
    // fists, the charge behind a raised arm, the leap, the bombs, and the
    // crack that runs across the floor and comes up under you. Five attacks
    // that ask five different questions.
    // ================================================================
    private int attack = ATK_NONE;
    private int attackTicks;
    private int attackCooldown = 60;
    /** Set once, when it first drops under half - the roar is a phase, not a pick. */
    private boolean hasRoared;

    /** How long it stands before it stops being anyone's problem. */
    public static final int LIFETIME = 900;

    /**
     * HOW FAST IT MAY TURN, and it is nearly not at all.
     *
     * <p>The same idea the first phase of the fight is built on: a thing this
     * heavy does not pivot, so its BACK is the answer to it. That only works
     * if getting behind it is worth something, and it is worth nothing if it
     * can come round as fast as somebody can run.
     *
     * <p>Two rates, and the gap between them is the whole design. Everything
     * it does is sluggish - a degree and a half a tick, which is four seconds
     * for a half turn - EXCEPT lining up a charge, where it is allowed to come
     * round properly. So the charge is the one time it can answer somebody who
     * has got behind it, and a player who sees the wind-up knows that is what
     * is being answered.
     */
    private static final float TURN_SLOW = 1.5F;
    // SIXTEEN, up from nine. The wind-up is what buys the charge its aim, and
    // at nine degrees a tick a player who circled during it was simply gone
    // before the thing had come round.
    private static final float TURN_CHARGE = 16.0F;
    /** How far off its nose a thing may be before it walks at it at all. */
    private static final float WALK_ARC = 40.0F;
    /** And how far off before a charge goes down its nose instead. */
    private static final float CHARGE_ARC = 32.0F;

    /**
     * Ticks of windup the player gets to read before the charge lands - and the tick of it (counting down) at which its
     * line is fixed and drawn on the floor before it (ChargeLaneEntity): from there it does not turn, and the lane says
     * where it will run for a second and a fifth before it goes. It was 22, the line fixed at its launch.
     */
    private static final int WINDUP_TICKS = 36, CHARGE_LOCK = 24;
    /** Everything the charge deals, a tenth down. */
    private static final float CHARGE_SOFTER = 0.9F;
    /** Its line fixed (the lane drawn) for the charge now winding up. */
    private boolean laneLocked;
    /** And how long the run itself lasts once it starts. */
    private static final int CHARGE_TICKS = 26;

    /**
     * How long it spends climbing out before it is anyone's problem.
     *
     * <p>It is untouchable and motionless for all of it, and that is the
     * point: the summon is a moment the player is supposed to WATCH, and a
     * thing that can be hit while it arrives gets killed during its own
     * entrance.
     */
    /** Eighty-four, not seventy-two: the climb ends at 66 with its feet
     *  planted, and the extra eighteen are it STANDING UP to full height.
     *  That rise is new - the old rig finished the emergence by dropping onto
     *  its knuckles, because it was built as a knuckle-walker. */
    private static final int EMERGE_TICKS = 84;
    /** The tick its feet hit the floor, partway through, while it is still
     *  folded over them. The shockwave belongs here rather than at the end. */
    private static final int EMERGE_PLANT = 66;

    /**
     * Its own bar, and deliberately its own EVENT rather than a second
     * element drawn inside the king's.
     *
     * <p>Forge walks the boss events in order and hands each one the Y the
     * previous finished at, so a miniboss that owns a real ServerBossEvent
     * lands under the king's bar for free and can never overlap it - however
     * tall his grows. Painting it into his bar would have hard-coded that
     * relationship in two places instead.
     */
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.frozen_dominion.ice_monstrosity"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);

    private int emerging = EMERGE_TICKS;
    /** THE GREETING. It hauls itself out of the floor and then ROARS at whoever
     *  is standing there, and nothing else runs until it has. Long enough to
     *  cover the roar clip, so the bellow is a held beat the player has to
     *  stand through rather than a sound over a walk cycle. */
    private static final int GREET_TICKS = 56;
    /** The prison's waking roar: owed, and how far into the greeting it comes (the roar clip's jaw opens at 0.5 s). */
    private boolean greetVoice;
    private static final int GREET_VOICE = 10;

    private int greeting;
    private int slamming;
    private int chargeCooldown = 80;
    private int windup;
    private int charging;
    private int impactSeq;
    private Vec3 chargeLine = Vec3.ZERO;
    /** Where the run started: a pillar only breaks a charge that has had room to get going. */
    private Vec3 chargeFrom = Vec3.ZERO;
    /**
     * How far it has to have run before a pillar can break it. A shorter bump only stops it.
     */
    private static final double CHARGE_MIN_RUN = 6.0D;
    /** Everyone currently pinned to the front of the charging arm. */
    private final java.util.Set<java.util.UUID> carried = new java.util.HashSet<>();

    /** Everything already hit by the attack that is running. */
    private final java.util.Set<java.util.UUID> struck = new java.util.HashSet<>();
    /** How far he actually moved last tick. Synced - the renderer needs it. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> STRIDE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    HollowGolemEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    /**
     * WHICH ANIMATION IS PLAYING, decided by the server.
     *
     * <p>The client cannot work this out for itself: everything that drives
     * it lives in fields that only the server ticks. Sending the answer is
     * one byte and removes the entire class of bug rather than the instance
     * of it - there is now exactly one place that knows what he is doing.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> ANIM =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    HollowGolemEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);

    /** Counter plus amplitude of the last blow, for the camera. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> IMPACT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    HollowGolemEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);

    // ================================================================
    // THE REWORK. The charge and the bombs stay;
    // around them, eight new ones and one thing to learn:
    //
    //   HAMMER     both fists overhead, a wave of frost out in a cone and spikes down the line
    //   COMBO      a sweep and an uppercut (and in its second phase both fists down after them)
    //   STOMP2     the left foot, then the right: two rings - for whoever is at its legs or behind
    //   JAVELIN    an icicle torn off its back and thrown: it STANDS where it lands
    //   GEYSER     its trough erupts: pools of freezing water come down round the player
    //   AVALANCHE  (second phase) it bellows at the dome and icicles fall on their shadows
    //   GRAB       a hand that closes on whoever is in front, lifts them and smashes them down -
    //              nothing breaks its grip (07.10.2026)
    //   STAGGER    THE LESSON: a charge that runs into a javelin's pillar breaks on it, and it
    //              goes down on one knee with its heart open - everything takes 1.6 times, 5 s
    //   FRACTURE   at half its health, once: it tears its pauldrons off and the fight speeds up
    // ================================================================
    private static final int ATK_HAMMER = 20, ATK_COMBO = 21, ATK_STOMP2 = 22, ATK_JAVELIN = 23,
            ATK_GEYSER = 24, ATK_AVALANCHE = 25, ATK_GRAB = 26, ATK_STAGGER = 27, ATK_FRACTURE = 28;
    private static final int A_HAMMER = 20, A_COMBO = 21, A_COMBO3 = 22, A_STOMP2 = 23, A_JAVELIN = 24,
            A_GEYSER = 25, A_AVALANCHE = 26, A_GRAB = 27, A_STAGGER = 28, A_FRACTURE = 29;
    private static final String CLIP = "animation.ice_monstrosity.";
    private static final RawAnimation HAMMER = RawAnimation.begin().thenPlayAndHold(CLIP + "hammer");
    private static final RawAnimation COMBO = RawAnimation.begin().thenPlayAndHold(CLIP + "combo");
    private static final RawAnimation COMBO3 = RawAnimation.begin().thenPlayAndHold(CLIP + "combo3");
    private static final RawAnimation STOMP2 = RawAnimation.begin().thenPlayAndHold(CLIP + "stomp2");
    private static final RawAnimation JAVELIN = RawAnimation.begin().thenPlayAndHold(CLIP + "javelin");
    private static final RawAnimation GEYSER = RawAnimation.begin().thenPlayAndHold(CLIP + "geyser");
    private static final RawAnimation AVALANCHE = RawAnimation.begin().thenPlayAndHold(CLIP + "avalanche");
    private static final RawAnimation GRAB = RawAnimation.begin().thenPlayAndHold(CLIP + "grab");
    private static final RawAnimation STAGGER = RawAnimation.begin().thenLoop(CLIP + "stagger");
    private static final RawAnimation FRACTURE = RawAnimation.begin().thenPlayAndHold(CLIP + "fracture");
    /** Its death - tools/monstrosity_attacks.py death(). */
    private static final RawAnimation DEATH_ANIM = RawAnimation.begin().thenPlayAndHold(CLIP + "death");
    private static final int A_DEATH = 30;
    /** As long as the clip; then what is left of it bursts. */
    private static final int DEATH_TICKS = 124;
    /** How long it stays on its knee after its charge breaks on a pillar. */
    private static final int STAGGER_TICKS = 100;
    /** Its second phase: the pauldrons are off (synced - the renderer hides them). */
    private static final EntityDataAccessor<Boolean> FRACTURED =
            SynchedEntityData.defineId(HollowGolemEntity.class, EntityDataSerializers.BOOLEAN);
    private boolean fractureDone;
    private int staggerLen = STAGGER_TICKS;
    private int grabCooldown = 200, geyserCooldown = 140, javelinCooldown = 40, avalancheCooldown = 0;

    // ================================================================
    // TAMED. Woken out of a statue of Sovereign Ice by the
    // Heart of the Silent Winter (HeartOfWinterItem.useOn), it is its waker's: no bar, no loot, no dome to bring down;
    // what it does lands on nobody of its owner's side (players, and itself); it goes for whatever hurts its owner or
    // whatever its owner strikes, and otherwise walks after them (or stays, told so); a Saddle goes on its back and its
    // owner rides it, steering it - and what the rider strikes from up there, it strikes.
    // ================================================================
    private static final EntityDataAccessor<Boolean> TAMED =
            SynchedEntityData.defineId(HollowGolemEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SADDLED =
            SynchedEntityData.defineId(HollowGolemEntity.class, EntityDataSerializers.BOOLEAN);
    @Nullable
    private java.util.UUID tamer;
    private boolean staying;
    /** How far from its owner it lets itself fall behind, and from how far it simply comes to them. */
    private static final double HEEL = 10.0D, RECALL = 40.0D;

    public boolean isTamed() {
        return entityData.get(TAMED);
    }

    public boolean isSaddled() {
        return entityData.get(SADDLED);
    }

    /** Its owner, if about. */
    @Nullable
    public Player tamer() {
        return tamer == null ? null : level().getPlayerByUUID(tamer);
    }

    /** Is `e` of its side - nothing of its lands on them? (Tamed: every player, and its own kind that is tamed.) */
    public boolean mySide(Entity e) {
        return isTamed() && (e instanceof Player || (e instanceof HollowGolemEntity g && g.isTamed()));
    }

    /** Woken by `by`: theirs from now on. */
    public void tameTo(Player by) {
        tamer = by.getUUID();
        entityData.set(TAMED, true);
        setPersistenceRequired();
        bossEvent.removeAllPlayers();
        setTarget(null);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && mySide(target)) {
            return;                                    // never at its own side
        }
        super.setTarget(target);
    }

    /** Ridden: its rider, if its saddle is on. */
    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return isSaddled() && getFirstPassenger() instanceof Player p ? p : null;
    }

    private boolean ridden() {
        return getControllingPassenger() != null;
    }

    @Override
    protected void tickRidden(Player rider, Vec3 travel) {
        super.tickRidden(rider, travel);
        if (isWindingUp() || isCharging()) {
            yBodyRot = getYRot();                       // (its charge goes down its nose: no turning in it)
            yHeadRot = getYRot();
            return;
        }
        float want = rider.getYRot();
        float turn = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(want - getYRot()), -10.0F, 10.0F);
        setYRot(getYRot() + turn);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        setXRot(0.0F);
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 travel) {
        if (attack != ATK_NONE || isWindingUp()) {
            return Vec3.ZERO;                          // it plants itself to strike (and gathers itself to charge)
        }
        if (isCharging()) {
            return new Vec3(0.0D, 0.0D, 1.0D);         // the charge: flat out, straight ahead
        }
        float f = rider.zza;
        return new Vec3(0.0D, 0.0D, f < 0.0F ? f * 0.4F : f);
    }

    @Override
    protected float getRiddenSpeed(Player rider) {
        if (isCharging()) {
            return RIDER_CHARGE_SPEED;
        }
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.7F;
    }

    /**
     * ON THE SADDLE: at 0.8 of its height its rider sat 4.8 blocks up,
     * inside its chest - the saddle is on the trough of its back, 6.42-6.75 up while it stands and walks (pose_model,
     * "idle" and "walk"), its seat 0.4 over that. A rider's hips are 0.75 over their feet and Minecraft lowers them a
     * further 0.35 (Player#getMyRidingOffset): so 6.55.
     *
     * <p>(1.21: riders hang from attachment points - the vehicle's passenger point minus the rider's own vehicle point,
     * 0.6 for a player, where it was the vehicle's offset plus the rider's -0.35. The same seat: 6.55 - 0.35 + 0.6.)
     */
    @Override
    protected net.minecraft.world.phys.Vec3 getPassengerAttachmentPoint(net.minecraft.world.entity.Entity passenger,
            net.minecraft.world.entity.EntityDimensions dimensions, float scale) {
        return new net.minecraft.world.phys.Vec3(0.0D, 6.8D, 0.0D);
    }

    // ---- FROM THE SADDLE: the jump key charges a stomp - the horse's own bar - and the attack key sweeps its arm across the front
    private int riderCooldown;
    private static final int RIDER_STOMP_REST = 70, RIDER_SWEEP_REST = 36;
    /**
     * ITS CHARGE, FROM THE SADDLE: the jump bar held to the full - the horse's own bar - and released: half a second of gathering, then its
     * charge straight down its nose, for its whole run, scooping what is not on its side onto its arm as it does wild.
     * Less than a full bar is the stomp it always was. The run is its rider's own movement (a ridden thing is moved by
     * its rider's game): RIDER_CHARGE_SPEED is the ridden speed that comes to about its wild charge's block and a third
     * a tick.
     */
    private static final int RIDER_CHARGE_POWER = 90, RIDER_CHARGE_WINDUP = 10, RIDER_CHARGE_REST = 120;
    private static final float RIDER_CHARGE_SPEED = 0.6F;

    private boolean riderFree() {
        return ridden() && attack == ATK_NONE && riderCooldown <= 0 && emerging <= 0 && greeting <= 0;
    }

    @Override
    public void onPlayerJump(int power) {
    }

    @Override
    public boolean canJump() {
        return isSaddled() && isTamed();
    }

    /** Released the jump key (the server's hearing of it): a full bar is its charge, less the stomp, none nothing. */
    @Override
    public void handleStartJump(int power) {
        if (level().isClientSide || power < 10 || !riderFree() || windup > 0 || charging > 0) {
            return;
        }
        if (power >= RIDER_CHARGE_POWER) {
            riderCooldown = RIDER_CHARGE_REST;
            Vec3 nose = getForward();
            Vec3 flat = new Vec3(nose.x, 0.0D, nose.z);
            chargeLine = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
            laneLocked = true;                                 // (the line is its nose: no target to aim it at)
            windup = RIDER_CHARGE_WINDUP;
            struck.clear();
            carried.clear();
            ChargeLaneEntity.lay(this, chargeLine, RIDER_CHARGE_WINDUP + CHARGE_TICKS);
            playSound(FFSounds.GOLEM_STOMP.get(), 2.8F, 1.12F);
            return;
        }
        riderCooldown = RIDER_STOMP_REST;
        begin(ATK_STOMP);
    }

    @Override
    public void handleStopJump() {
    }

    /** Its rider swung (RiderStrikePacket): the backhand across its front. */
    public void riderSweep(Player who) {
        if (getControllingPassenger() != who || !riderFree()) {
            return;
        }
        riderCooldown = RIDER_SWEEP_REST;
        begin(ATK_SWEEP);
    }

    /** Its rider struck `v` from its back: it strikes there too (if it is free and they are in its reach). */
    public void riderStruck(LivingEntity v) {
        if (!isTamed() || mySide(v) || attack != ATK_NONE || emerging > 0 || greeting > 0 || distanceTo(v) > 8.0D) {
            return;
        }
        super.setTarget(v);
        begin(random.nextInt(3) == 0 ? ATK_HAMMER : (random.nextBoolean() ? ATK_COMBO : ATK_STOMP2));
    }

    @Override
    public net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (!isTamed() || tamer == null || !tamer.equals(player.getUUID())) {
            return super.mobInteract(player, hand);
        }
        net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
        boolean server = !level().isClientSide;
        if (held.is(com.jastkub.frozenfortress.registry.FFItems.MONSTROSITY_SADDLE.get()) && !isSaddled()) {
            if (server) {
                entityData.set(SADDLED, true);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                playSound(net.minecraft.sounds.SoundEvents.HORSE_SADDLE, 2.0F, 0.6F);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.frozen_dominion.monstrosity_saddled"), true);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(!server);
        }
        if (held.is(com.jastkub.frozenfortress.registry.FFItems.EVERFROST_CRYSTAL.get()) && getHealth() < getMaxHealth()) {
            if (server) {
                heal(40.0F);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                playSound(FFSounds.FROST_CHARGE.get(), 1.6F, 1.2F);
                ((ServerLevel) level()).sendParticles(FFParticles.SOUL_FROST.get(), getX(), getY() + 4.0D, getZ(),
                        30, 1.5D, 2.0D, 1.5D, 0.05D);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(!server);
        }
        if (held.isEmpty() && player.isShiftKeyDown()) {
            if (server) {
                staying = !staying;
                getNavigation().stop();
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(staying
                        ? "message.frozen_dominion.monstrosity_stay" : "message.frozen_dominion.monstrosity_follow"), true);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(!server);
        }
        if (held.isEmpty() && isSaddled() && !isVehicle() && emerging <= 0) {
            if (server) {
                player.startRiding(this);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(!server);
        }
        return super.mobInteract(player, held.isEmpty() ? hand : hand);
    }

    @Override
    protected boolean shouldDropLoot() {
        return !isTamed() && super.shouldDropLoot();          // a tamed one is not a kill
    }

    /** (Tamed) whom it goes for, and walking after its owner. Every ten ticks; the walking every tick. */
    private void tickTamed() {
        Player owner = tamer();
        if (riderCooldown > 0) {
            riderCooldown--;
        }
        if (tickCount % 10 == 0) {
            LivingEntity t = getTarget();
            if (t != null && (!t.isAlive() || mySide(t) || (owner != null && t.distanceToSqr(owner) > 40.0D * 40.0D))) {
                super.setTarget(null);
            }
            if (owner != null && getTarget() == null) {
                LivingEntity threat = owner.getLastHurtByMob();
                LivingEntity prey = owner.getLastHurtMob();
                if (threat != null && threat.isAlive() && !mySide(threat)
                        && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200 && threat.distanceToSqr(this) < 32.0D * 32.0D) {
                    super.setTarget(threat);
                } else if (prey != null && prey.isAlive() && !mySide(prey)
                        && owner.tickCount - owner.getLastHurtMobTimestamp() < 200 && prey.distanceToSqr(this) < 32.0D * 32.0D) {
                    super.setTarget(prey);
                }
            }
        }
        if (staying && !ridden() && getTarget() == null && attack == ATK_NONE) {
            getNavigation().stop();                      // told to stay: it does not wander off either
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        }
        if (owner == null || staying || ridden() || getTarget() != null || attack != ATK_NONE || emerging > 0
                || greeting > 0 || charging > 0 || windup > 0) {
            return;
        }
        double d = distanceTo(owner);
        if (d > RECALL && owner.onGround()) {
            // too far behind: it comes to them as it came out of the statue - out of the floor beside them
            Vec3 back = owner.getLookAngle().multiply(-1.0D, 0.0D, -1.0D).normalize().scale(6.0D);
            teleportTo(owner.getX() + back.x, owner.getY(), owner.getZ() + back.z);
            playSound(FFSounds.GOLEM_STOMP.get(), 2.0F, 0.9F);
            return;
        }
        if (d > HEEL) {
            Vec3 to = owner.position().subtract(position());
            float want = (float) (Math.atan2(to.z, to.x) * (180.0D / Math.PI)) - 90.0F;
            float turn = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(want - getYRot()), -5.0F, 5.0F);
            setYRot(getYRot() + turn);
            yBodyRot = getYRot();
            if (Math.abs(net.minecraft.util.Mth.wrapDegrees(want - getYRot())) < WALK_ARC) {
                double speed = getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.6D;
                Vec3 nose = getForward();
                setDeltaMovement(nose.x * speed, getDeltaMovement().y, nose.z * speed);
                if (horizontalCollision && onGround()) {
                    setDeltaMovement(getDeltaMovement().x, 0.44D, getDeltaMovement().z);
                }
            }
        }
    }
    /**
     * THE ROAR THAT BRINGS THE DOME DOWN: reared up and bellowing, it shakes the prison's vault, and for three seconds
     * its icicles come down all over the arena - each on its own shadow (FallingIcicleEntity), a third of them on the
     * players. Its waits between two, and the rain itself.
     */
    private int roarCooldown = 300;
    private static final int ROAR_EVERY = 560, ROAR_VOICE = 8, ROAR_AT = 18, RAIN_FROM = 22, RAIN_TO = 82, ROAR_DONE = 100;
    /** The middle of its hall (where it woke) and how far the roar's rain reaches from it: the whole arena. */
    @Nullable
    private Vec3 arenaCentre;
    private static final double ARENA_R = 26.0D;
    /** The geyser's gouts still to be thrown, one every other tick: where each comes down. */
    private final java.util.List<Vec3> geyserShots = new java.util.ArrayList<>();
    /**
     * THE DOME'S OWN ICICLES: what the avalanche shook down, kept so it grows back - (where, what) per block.
     */
    private final java.util.List<Object[]> fallen = new java.util.ArrayList<>();
    /** How long a shaken-down icicle takes to grow back. */
    private static final int REGROW = 900;
    @javax.annotation.Nullable
    private java.util.UUID grabbed;

    /** On its knee, its heart open (the client asks so the ice can blaze). */
    public boolean isStaggered() {
        return level().isClientSide ? entityData.get(ANIM) == A_STAGGER : attack == ATK_STAGGER;
    }

    /** Its flank: the perpendicular to its facing. */
    private Vec3 flank() {
        Vec3 f = getForward();
        return new Vec3(-f.z, 0.0D, f.x);
    }

    /** The planted javelins near it. */
    private java.util.List<com.jastkub.frozenfortress.entity.projectile.IceJavelinEntity> pillars(double r) {
        return level().getEntitiesOfClass(com.jastkub.frozenfortress.entity.projectile.IceJavelinEntity.class,
                getBoundingBox().inflate(r), com.jastkub.frozenfortress.entity.projectile.IceJavelinEntity::isPlanted);
    }

    /** The floor under a point: down from a little above it to a few blocks below. */
    private double floorAt(double x, double y, double z) {
        net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int dy = 3; dy >= -6; dy--) {
            p.set(x, y + dy, z);
            if (!level().getBlockState(p).getCollisionShape(level(), p).isEmpty()
                    && level().getBlockState(p.above()).getCollisionShape(level(), p.above()).isEmpty()) {
                return p.getY() + 1.0D;
            }
        }
        return y;
    }

    /** Is it up against a pillar of ice (packed or blue) - not a wall of stone? */
    private boolean hitPillar() {
        net.minecraft.world.phys.AABB box = getBoundingBox().inflate(0.9D, 0.0D, 0.9D).expandTowards(chargeLine.scale(0.8D));
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(
                net.minecraft.core.BlockPos.containing(box.minX, getY() + 0.5D, box.minZ),
                net.minecraft.core.BlockPos.containing(box.maxX, getY() + 4.0D, box.maxZ))) {
            net.minecraft.world.level.block.state.BlockState st = level().getBlockState(p);
            if (st.is(net.minecraft.world.level.block.Blocks.PACKED_ICE) || st.is(net.minecraft.world.level.block.Blocks.BLUE_ICE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * IMPALED: a charge that reaches a pillar with somebody on its arm breaks
     * on THEM - they are driven into the ice, harder than being dropped, and it keeps its feet.
     */
    private void impale() {
        charging = 0;
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        if (level() instanceof ServerLevel sl) {
            for (java.util.UUID id : carried) {
                if (sl.getEntity(id) instanceof LivingEntity rider && rider.isAlive()) {
                    strike(rider, 4.4F * CHARGE_SOFTER);      // (dropped off the arm: 2.4)
                    rider.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 2), this);
                    // pinned there a moment, then it falls off the ice
                    rider.setDeltaMovement(-chargeLine.x * 0.25D, 0.15D, -chargeLine.z * 0.25D);
                    rider.hurtMarked = true;
                    if (rider instanceof net.minecraft.server.level.ServerPlayer sp) {
                        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
                    }
                    sl.sendParticles(FFParticles.ICE_SHARD.get(), rider.getX(), rider.getY() + 1.0D, rider.getZ(),
                            40, 0.5D, 0.7D, 0.5D, 0.3D);
                }
            }
        }
        carried.clear();
        playSound(FFSounds.GOLEM_SLAM.get(), 4.8F, 0.55F);
        playSound(FFSounds.ICE_IMPACT.get(), 4.0F, 0.6F);
        playSound(net.minecraft.sounds.SoundEvents.GLASS_BREAK, 2.6F, 0.5F);
        thump(1.0F);
    }

    /**
     * Is there a pillar of the arena across the first few blocks of a charge's line? It would
     * only shoulder into it - so it does not charge that way at all, it walks round.
     */
    private int detourTicks, detourSide = 1, stuckTicks;
    private Vec3 detour = Vec3.ZERO, lastStride;

    /**
     * Its way to them, if the straight one is shut: null while the straight way is clear (or nothing
     * better is found), else the smallest turn either side - the side it last chose first - along which
     * its whole width passes, held a second and a half so it does not dither at the pillar's corner.
     * Pressing on something and getting nowhere for a second and a half counts as shut, too.
     */
    @javax.annotation.Nullable
    private Vec3 steer(Vec3 to) {
        Vec3 toward = new Vec3(to.x, 0.0D, to.z).normalize();
        if (lastStride != null && position().distanceToSqr(lastStride) < 0.0009D) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lastStride = position();
        if (detourTicks > 0) {
            detourTicks--;
            if (!blockedAhead(detour, 2.5D)) {
                return detour;
            }
            detourTicks = 0;
        }
        if (!blockedAhead(toward, 4.0D) && stuckTicks < 30) {
            return null;
        }
        stuckTicks = 0;
        for (int k = 1; k <= 7; k++) {
            for (int side : new int[]{detourSide, -detourSide}) {
                double a = Math.toRadians(side * k * 22.5D);
                Vec3 d = new Vec3(toward.x * Math.cos(a) - toward.z * Math.sin(a), 0.0D,
                        toward.x * Math.sin(a) + toward.z * Math.cos(a));
                if (!blockedAhead(d, 4.0D)) {
                    detourSide = side;
                    detour = d;
                    detourTicks = 30;
                    return d;
                }
            }
        }
        return null;
    }

    /** Anything solid in the way of its whole width, knee to shoulder, within `reach` of its front. */
    private boolean blockedAhead(Vec3 dir, double reach) {
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
        double half = getBbWidth() * 0.5D;
        net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (double d = 0.5D; d <= reach; d += 0.75D) {
            for (double o = -half; o <= half + 1.0E-3D; o += half / 2.0D) {
                for (double up = 1.2D; up <= 4.8D; up += 1.2D) {
                    p.set(getX() + dir.x * (d + half) + side.x * o, getY() + up, getZ() + dir.z * (d + half) + side.z * o);
                    if (!level().getBlockState(p).getCollisionShape(level(), p).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Can it see them over (or past) whatever stands between - a throw from its chest would get there? */
    private boolean clearShot(LivingEntity target) {
        Vec3 from = position().add(0.0D, 3.5D, 0.0D);
        return level().clip(new net.minecraft.world.level.ClipContext(from, target.getEyePosition(),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                this)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** Tumbled along inside the avalanche: a lesser blow, every half second it carries them - as heavy as the
     *  avalanche has grown (`size`, AvalancheEntity.size). */
    public void avalancheTumble(LivingEntity v, float size) {
        strike(v, 1.4F * size);
        v.setTicksFrozen(Math.min(v.getTicksRequiredToFreeze() + 40, v.getTicksFrozen() + 30));
    }

    /** The avalanche's blow (AvalancheEntity): bowled over, chilled - as hard as it has grown by then. */
    public void avalancheStrike(LivingEntity v, float size) {
        strike(v, 3.6F * size);
        v.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 2), this);
        v.setTicksFrozen(Math.min(v.getTicksRequiredToFreeze() + 40, v.getTicksFrozen() + 80));
    }

    /**
     * THE CEILING SHEDS ITS ICICLES THE WHOLE FIGHT: every few seconds one comes down over each of its foes - a real one off the vault where
     * one hangs (it shudders first, its shadow on the floor), more often in its fury.
     */
    private int icicleTimer = 100;

    private void tickIcicles(ServerLevel sl) {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || emerging > 0 || isDeadOrDying()) {
            return;
        }
        if (--icicleTimer > 0) {
            return;
        }
        icicleTimer = berserk() ? 50 + random.nextInt(30) : 80 + random.nextInt(40);
        java.util.Set<net.minecraft.core.BlockPos> taken = new java.util.HashSet<>();
        for (LivingEntity v : victims(30.0D)) {
            if (!(v instanceof Player)) {
                continue;
            }
            Vec3 p = v.position().add(random.nextGaussian() * 1.6D, 0.0D, random.nextGaussian() * 1.6D);
            double floor = floorAt(p.x, p.y, p.z);
            if (!dropIcicle(sl, p, floor, taken) && random.nextBoolean()) {
                sl.addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.FallingIcicleEntity(
                        level(), this, p.x, floor, p.z, 14.0D + random.nextDouble() * 4.0D));
            }
        }
    }

    private boolean pillarAhead(Vec3 dir, double reach) {
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
        double half = getBbWidth() * 0.5D;
        net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (double d = 1.0D; d <= reach; d += 0.5D) {
            for (double o = -half; o <= half + 1.0E-3D; o += Math.max(0.5D, half)) {
                for (double up = 0.5D; up <= 3.5D; up += 1.5D) {
                    p.set(getX() + dir.x * (d + half) + side.x * o, getY() + up, getZ() + dir.z * (d + half) + side.z * o);
                    net.minecraft.world.level.block.state.BlockState st = level().getBlockState(p);
                    if (st.is(net.minecraft.world.level.block.Blocks.PACKED_ICE)
                            || st.is(net.minecraft.world.level.block.Blocks.BLUE_ICE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Shakes down the icicle hanging nearest over a spot (within a few blocks of it, anywhere up
     * to the dome): its blocks come off the ceiling and it falls from where it hung, tip first,
     * its shadow on the floor under it. False if none hangs there.
     */
    private boolean dropIcicle(ServerLevel sl, Vec3 spot, double floor, java.util.Set<net.minecraft.core.BlockPos> taken) {
        net.minecraft.world.level.block.Block icicle = com.jastkub.frozenfortress.registry.FFBlocks.FROST_ICICLE.get();
        net.minecraft.core.BlockPos best = null;
        double bd = 99.0D;
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
        int fy = (int) Math.floor(floor);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double h = dx * dx + dz * dz;
                if (h > 12.5D || h >= bd) {
                    continue;
                }
                for (int y = fy + 3; y <= fy + 46; y++) {
                    m.set(Math.floor(spot.x) + dx, y, Math.floor(spot.z) + dz);
                    net.minecraft.world.level.block.state.BlockState st = sl.getBlockState(m);
                    if (!st.isAir()) {
                        // the first thing above the floor: is it the tip of an icicle hanging down?
                        if (st.is(icicle) && sl.getBlockState(m.below()).isAir() && !taken.contains(m)
                                && st.getValue(com.jastkub.frozenfortress.block.FrostIcicleBlock.TIP_DIRECTION)
                                        == net.minecraft.core.Direction.DOWN) {
                            best = m.immutable();
                            bd = h;
                        }
                        break;
                    }
                }
            }
        }
        if (best == null) {
            return false;
        }
        // the whole icicle, tip to root
        int n = 0;
        net.minecraft.core.BlockPos b = best;
        while (n < 4 && sl.getBlockState(b).is(icicle)) {
            fallen.add(new Object[]{b, sl.getBlockState(b), tickCount + REGROW});
            taken.add(b);
            sl.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            b = b.above();
            n++;
        }
        sl.levelEvent(2001, best, net.minecraft.world.level.block.Block.getId(
                net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState()));
        sl.addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.FallingIcicleEntity(
                level(), this, best.getX() + 0.5D, floor, best.getZ() + 0.5D, best.getY() + 0.05D - floor).length(n));
        return true;
    }

    /** The icicles of the roar's rain already brought down (no two from one place). */
    private final java.util.Set<net.minecraft.core.BlockPos> rainTaken = new java.util.HashSet<>();

    /** One beat of the roar's rain: an icicle on a player now and then, the rest anywhere in its hall. */
    private void rain(ServerLevel sl, LivingEntity target) {
        java.util.List<LivingEntity> players = new java.util.ArrayList<>();
        for (LivingEntity v : victims(30.0D)) {
            if (v instanceof Player) {
                players.add(v);
            }
        }
        if (arenaCentre == null) {
            arenaCentre = position();
        }
        for (int k = 0; k < 3; k++) {
            Vec3 p;
            if (!players.isEmpty() && random.nextInt(4) == 0) {
                LivingEntity v = players.get(random.nextInt(players.size()));
                p = v.position().add(random.nextGaussian() * 2.2D, 0.0D, random.nextGaussian() * 2.2D);
            } else {
                //
                // evenly over the whole hall, from its middle out to its walls - not round wherever it stands
                double ang = random.nextDouble() * Math.PI * 2.0D;
                double r = ARENA_R * Math.sqrt(random.nextDouble());
                p = arenaCentre.add(Math.cos(ang) * r, 0.0D, Math.sin(ang) * r);
            }
            if (p.distanceToSqr(position()) < 9.0D) {
                continue;                                     // not on its own head
            }
            double floor = floorAt(p.x, p.y, p.z);
            if (!dropIcicle(sl, p, floor, rainTaken)) {
                sl.addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.FallingIcicleEntity(
                        level(), this, p.x, floor, p.z, 14.0D + random.nextDouble() * 4.0D));
            }
        }
    }

    /** The icicles it shook down grow back, a while after (and all at once when it is gone). */
    private void regrow(boolean all) {
        if (fallen.isEmpty() || !(level() instanceof ServerLevel sl)) {
            return;
        }
        // root first, then down to the tip (each was put in tip-first): nothing hangs off nothing
        java.util.ListIterator<Object[]> it = fallen.listIterator(fallen.size());
        while (it.hasPrevious()) {
            Object[] f = it.previous();
            if (!all && tickCount < (Integer) f[2]) {
                continue;
            }
            net.minecraft.core.BlockPos p = (net.minecraft.core.BlockPos) f[0];
            if (sl.isLoaded(p) && sl.getBlockState(p).isAir()) {
                sl.setBlock(p, (net.minecraft.world.level.block.state.BlockState) f[1], 3);
            }
            it.remove();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            regrow(true);
        }
        super.remove(reason);
    }

    /**
     * AN ICICLE ON ITS HEAD: down on
     * its knee, as a pillar puts it - and then a while it shrugs them off, so one rain is one chance, not a lock.
     * Its own rain falls wide of where it stands (rain()); the icicles that find it are the ones aimed at a player
     * it was drawn under.
     */
    public boolean icicleStruck() {
        if (level().isClientSide || isTamed() || emerging > 0 || greeting > 0 || attack == ATK_STAGGER
                || icicleProof > tickCount || isDeadOrDying()) {
            return false;
        }
        icicleProof = tickCount + ICICLE_PROOF;
        playSound(FFSounds.ICE_SHATTER.get(), 4.0F, 0.6F);
        playSound(FFSounds.GOLEM_ROAR.get(), 4.0F, 1.35F);
        stagger(STAGGER_TICKS);
        return true;
    }

    private int icicleProof;
    private static final int ICICLE_PROOF = 300;

    /**
     * THE PILLAR IT BROKE ITSELF ON SHAKES THE VAULT: a handful of icicles come down round where it struck - each on its own shadow,
     * as the roar's rain does - so the pillar that dropped it is no place to stand and beat it for long.
     */
    private void pillarShaken() {
        if (!(level() instanceof ServerLevel sl) || chargeLine == null) {
            return;
        }
        Vec3 line = new Vec3(chargeLine.x, 0.0D, chargeLine.z);
        if (line.lengthSqr() < 1.0E-4D) {
            return;
        }
        Vec3 hit = position().add(line.normalize().scale(getBbWidth() * 0.5D + 1.0D));
        playSound(FFSounds.ICE_CRACK.get(), 4.0F, 0.55F);
        // FROM THE VAULT, NOT OUT OF THE AIR:
        // they were made twelve to sixteen blocks over the floor wherever that was - in the open air under a higher
        // vault. Now each is one of the vault's own icicles shaken loose where one hangs near (dropIcicle), and where
        // none does, a shard torn out of the ceiling right over its spot, the ceiling cracking where it came from.
        java.util.Set<net.minecraft.core.BlockPos> taken = new java.util.HashSet<>();
        for (int k = 0; k < PILLAR_ICICLES; k++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double r = 1.5D + random.nextDouble() * 3.5D;
            Vec3 p = hit.add(Math.cos(a) * r, 0.0D, Math.sin(a) * r);
            double floor = floorAt(p.x, p.y, p.z);
            if (dropIcicle(sl, p, floor, taken)) {
                continue;
            }
            net.minecraft.core.BlockPos roof = ceilingOver(sl, p, floor);
            // (the model is drawn up from its tip, 1.15 blocks a block of length: three blocks long, its root
            // against the vault)
            double height = roof != null ? Math.max(3.0D, roof.getY() - 3.3D - floor) : 12.0D + random.nextDouble() * 4.0D;
            if (roof != null) {
                sl.levelEvent(2001, roof, net.minecraft.world.level.block.Block.getId(
                        net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState()));
            }
            sl.addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.FallingIcicleEntity(
                    level(), this, p.x, floor, p.z, height).length(3));
        }
    }

    /** The first block of the vault over a spot (up to 46 over its floor), or null under open sky. */
    @javax.annotation.Nullable
    private net.minecraft.core.BlockPos ceilingOver(ServerLevel sl, Vec3 spot, double floor) {
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
        int fy = (int) Math.floor(floor);
        for (int y = fy + 3; y <= fy + 46; y++) {
            m.set(Math.floor(spot.x), y, Math.floor(spot.z));
            if (!sl.getBlockState(m).isAir()) {
                return m.immutable();
            }
        }
        return null;
    }

    private static final int PILLAR_ICICLES = 7;

    /** Down on one knee: the charge broke on a pillar, or a held player tore free. */
    private void stagger(int ticks) {
        charging = 0;
        windup = 0;
        laneLocked = false;
        dropRiders();
        grabbed = null;
        staggerLen = ticks;
        begin(ATK_STAGGER);
    }

    private static final int A_IDLE = 0, A_WALK = 1, A_EMERGE = 2, A_SLAM = 3,
            A_SWEEP = 4, A_STOMP = 5, A_ROAR = 7, A_WINDUP = 8,
            A_CHARGE = 9, A_FISSURE = 10, A_LEAP = 11, A_SHARDS = 12,
            A_SWIPE = 13, A_DRAIN = 14, A_BREATH = 15;

    public HollowGolemEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;              // it is a hazard, not a reward
        setPersistenceRequired();
        // THE HORNS ARE OUTSIDE THE BOX. They finish about a block above the
        // top of the hitbox, which stays where it is because the hitbox has a
        // second job - the colossus has to walk through its own fortress, and
        // a box grown to cover a pair of spikes is a box that no longer fits
        // the doorways. Minecraft culls against the declared box, so without
        // this the crown pops out of existence the moment the box leaves the
        // frustum - looking up at it from underneath, which is most of the
        // fight.
        this.noCulling = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                // The Ice Monstrosity is a wall of ice - three hundred and fifty
                // of health behind heavy armour, the number the fight is tuned to.
                .add(Attributes.MAX_HEALTH, 350.0D)
                // A CRAWL, and slower than it was. The charge is what makes
                // this safe: taking the walk down to two thirds of a player's
                // pace would be pure frustration on its own - you would simply
                // leave - but it is now the thing that BUYS the charge, and
                // the charge is what says leaving is not an answer. Slow
                // enough that the fight is about spacing; fast enough over
                // twenty-six ticks that the spacing can be taken away.
                .add(Attributes.MOVEMENT_SPEED, 0.072D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                // ARMOUR, and a lot of it. Ten put it at leather: a player
                // with a decent weapon deleted it inside its own lifetime and
                // never had to solve it, which made the summon a spectacle
                // with no teeth behind it. Twenty-four with toughness behind
                // it means a big hit still lands for something but chipping is
                // not a plan - you have to survive it rather than race it.
                // 30/12 took four fifths off
                // every blow - a twelve landed as two and a half and the guardian's 800
                // took three hundred hits. 16/6 still eats a third to a half of a hit.
                .add(Attributes.ARMOR, 16.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new ChargeGoal());
        // no MeleeAttackGoal: the six run off one clock in tickAttacks, and a
        // vanilla melee goal steering navigation in the same tick is what turns
        // a committed swing into a shuffle
        // NO navigation goal - see tickStride(). A six-block-wide mob cannot
        // path anywhere indoors, and one that tries just stands still.
        goalSelector.addGoal(6, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(
                this, 0.8D, 1.0F));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 24.0F));
        goalSelector.addGoal(8, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        float beforeYaw = getYRot();
        super.tick();
        if (!level().isClientSide && pendingWarp != null) {
            float[] w = pendingWarp;
            pendingWarp = null;
            warp(w[0], w[1], (int) w[2], (int) w[3]);
        }
        if (!level().isClientSide) {
            if (arenaCentre == null && emerging <= 0) {
                arenaCentre = position();                // where it woke: the middle of its hall
            }
            bossEvent.setProgress(getHealth() / getMaxHealth());
            vent(); 
            // ============================================================
            // WHATEVER TURNED IT, IT IS UNDONE HERE.
            //
            // Clamping at the one place that aims it is not enough, and this
            // class proved it: the walk turned politely at three and a half
            // degrees while the wind-up wrote the yaw outright, so the thing
            // crept round when it was walking and snapped a hundred and eighty
            // the moment it wanted to charge. Add the vanilla look control,
            // knockback and anything a future attack does, and "how fast can
            // it turn" has as many answers as there are writers.
            //
            // So it has ONE answer, applied last. Every other piece of code
            // may ask for whatever facing it likes; this decides how much of
            // that it gets this tick.
            // ============================================================
            // ---- AND WHILE IT IS DOWN, IT IS DOWN. A recovery the boss can
            //      turn through is not a recovery: it stays pointed at you the
            //      whole time and the window is only worth the damage you can
            //      land from where you already stood. Pinned, it can be walked
            //      around - which is the actual reward for reading the tell.
            float cap = recovering() ? 0.0F : turnCap();
            float moved = net.minecraft.util.Mth.degreesDifference(beforeYaw, getYRot());
            if (Math.abs(moved) > cap) {
                float capped = net.minecraft.util.Mth.wrapDegrees(
                        beforeYaw + Math.signum(moved) * cap);
                setYRot(capped);
                yBodyRot = capped;
                // the head is left alone on purpose: it is allowed to lead the
                // body, and pinning it would stop it watching anybody move -
                // which is most of what makes a slow thing look attentive
                // rather than asleep
            }
            if (attack == ATK_BREATH && attackTicks >= 14) {
                setYHeadRot(getYRot());                       // ...but not while the gust comes out of it
            }
        }
        if (level().isClientSide) {
            // it sheds cold constantly, so the outline is never a flat shape
            if (tickCount % 2 == 0) {
                level().addParticle(FFParticles.SOUL_FROST.get(),
                        getX() + (random.nextDouble() - 0.5D) * getBbWidth(),
                        getY() + random.nextDouble() * getBbHeight(),
                        getZ() + (random.nextDouble() - 0.5D) * getBbWidth(),
                        0.0D, 0.01D, 0.0D);
            }
            return;
        }
        publishAnimation();
        // --- climbing out. Nothing else runs until it is standing.
        if (emerging > 0) {
            emerging--;
            getNavigation().stop();
            setDeltaMovement(0.0D, 0.0D, 0.0D);
            if (level() instanceof ServerLevel serverLevel) {
                int done = EMERGE_TICKS - emerging;
                if (done == 1) {
                    serverLevel.playSound(null, blockPosition(), FFSounds.GOLEM_EMERGE.get(),
                            SoundSource.HOSTILE, 4.5F, 1.0F);
                }
                if (done % 4 == 0) {
                    serverLevel.playSound(null, blockPosition(), FFSounds.ICE_GRIND.get(),
                            SoundSource.HOSTILE, 2.2F, 0.55F + done * 0.004F);
                }
                // the floor coming up around it, all the way through
                if (done % 2 == 0) {
                    double ang = random.nextDouble() * Math.PI * 2.0D;
                    double r = 1.4D + random.nextDouble() * 2.2D;
                    serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                            getX() + Math.cos(ang) * r, getY() + 0.15D,
                            getZ() + Math.sin(ang) * r, 6, 0.3D, 0.15D, 0.3D, 0.22D);
                }
                // THE FEET LAND, and the floor answers - eighteen ticks before
                // it is done, because it still has to stand up afterwards.
                if (done == EMERGE_PLANT) {
                    serverLevel.playSound(null, blockPosition(), FFSounds.GOLEM_STOMP.get(),
                            SoundSource.HOSTILE, 4.0F, 0.72F);
                    serverLevel.sendParticles(FFParticles.SHOCKWAVE.get(),
                            getX(), getY() + 0.2D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    shake(serverLevel, 30);
                    thump(0.8F);
                }
                if (done == EMERGE_TICKS) {
                    serverLevel.playSound(null, blockPosition(), FFSounds.GOLEM_ROAR.get(),
                            SoundSource.HOSTILE, 4.5F, 1.0F);
                    warp(1.0F, 16.0F, 4, GREET_TICKS);
                    // and straight into the bellow - it is out, and the first
                    // thing it does is tell the player so
                    greeting = GREET_TICKS;
                }
            }
            return;
        }

        // --- THE GREETING ROAR. Out of the floor and planted, head up, nothing
        //     else running until it has finished telling the player what just
        //     arrived. It turns to face them for it, because a bellow aimed at
        //     the far wall is a sound effect rather than a threat.
        if (greeting > 0) {
            greeting--;
            if (greetVoice && greeting == GREET_TICKS - GREET_VOICE) {
                greetVoice = false;
                playSound(FFSounds.GOLEM_ROAR.get(), 6.0F, 1.0F);
            }
            getNavigation().stop();
            setDeltaMovement(0.0D, 0.0D, 0.0D);
            LivingEntity who = getTarget();
            if (who == null) {
                who = level().getNearestPlayer(this, 40.0D);
            }
            if (who != null) {
                getLookControl().setLookAt(who, 30.0F, 30.0F);
                double dx = who.getX() - getX();
                double dz = who.getZ() - getZ();
                if (dx * dx + dz * dz > 1.0E-4D) {
                    // ASKED FOR, NOT TAKEN. This wrote the facing outright,
                    // which is how something that turns at a degree and a half
                    // a tick could still come round instantly - it simply did
                    // not go through the turn at all. The ceiling in tick()
                    // decides how much of this arrives; during a wind-up that
                    // is the charge rate, which is fast but is still a turn
                    // the player can watch happen.
                    float yaw = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
                    setYRot(yaw);
                    yBodyRot = yaw;
                }
            }
            if (level() instanceof ServerLevel sl) {
                int into = GREET_TICKS - greeting;
                if (into == 10 || into == 30) {
                    shake(sl, 14);
                }
                if (into % 3 == 0) {
                    Vec3 maw = position().add(getViewVector(1.0F).scale(2.6D))
                            .add(0.0D, 4.2D, 0.0D);
                    sl.sendParticles(FFParticles.BLIZZARD_FLAKE.get(),
                            maw.x, maw.y, maw.z, 10, 0.7D, 0.5D, 0.7D, 0.14D);
                }
            }
            return;
        }
        if (slamming > 0) {
            slamming--;
        }
        tickAttacks();
        tickStride();
        if (!level().isClientSide && tickCount % 20 == 0) {
            regrow(false);
        }
        // NO LIFETIME. He used to dissolve after forty-five seconds, which
        // made him a timer the player could stand behind a pillar and wait
        // out - and a miniboss you can wait out is a delay, not a fight. He
        // stays until something kills him.
        if (chargeCooldown > 0) {
            chargeCooldown--;
        }
        if (windup > 0 && !level().isClientSide) {
            LivingEntity lockOn = getTarget();
            if (windup == CHARGE_LOCK && lockOn != null) {
                Vec3 flat = new Vec3(lockOn.getX() - getX(), 0.0D, lockOn.getZ() - getZ());
                chargeLine = flat.lengthSqr() < 1.0E-4D ? getForward() : flat.normalize();
                laneLocked = true;
                ChargeLaneEntity.lay(this, chargeLine, CHARGE_LOCK + CHARGE_TICKS);
                playSound(FFSounds.ICE_CRACK.get(), 3.4F, 0.6F);
                playSound(FFSounds.GOLEM_STEP.get(), 3.4F, 0.55F);     // a forefoot scraped back
            }
            if (laneLocked) {
                float aim = (float) (Math.atan2(chargeLine.z, chargeLine.x) * (180.0D / Math.PI)) - 90.0F;
                setYRot(aim);
                this.yBodyRot = aim;
                this.yHeadRot = aim;
            }
        }
        if (windup > 0 && --windup == 0) {
            // the line was fixed at CHARGE_LOCK and drawn on the floor; without it (it lost its target then), here
            LivingEntity target = getTarget();
            if (target != null || laneLocked) {
                Vec3 flat = laneLocked ? chargeLine : target.position().subtract(position());
                laneLocked = false;
                // AND IT CANNOT CHARGE BACKWARDS. The wind-up turns at the
                // charge rate, which is fast, but fast is not instant - and if
                // it still has not come round when the clock runs out, the
                // line was being set to a direction it is not pointing in, so
                // three hundred tonnes set off sideways.
                //
                // Past the arc it runs down its own nose instead. The charge
                // still happens, it just goes where the thing is aimed, and a
                // player who stayed behind it watches it thunder off the wrong
                // way - which is the correct reward for having stayed behind
                // it, and reads as a miss rather than as a bug.
                // ---- IT GOES AT YOU. FULL STOP.
                //
                // This used to fall back to its own nose whenever the wind-up
                // had not finished turning, which was defensible on paper and
                // read as a bug in play: three hundred tonnes announcing a
                // charge and then thundering off across the room. The reward
                // for standing behind it is that it has to TURN - that is
                // already a second of nothing - not that the attack is thrown
                // away.
                //
                // Two changes make it honest rather than just forced: the
                // wind-up turns much faster now (TURN_CHARGE), so it genuinely
                // is pointing at you by the time it launches, and the hull is
                // squared onto the line at the moment of launch, so what it
                // faces and what it does are the same thing. Without that
                // second part, aiming the line at the target alone would just
                // move the wrongness from the direction to the model.
                chargeLine = flat.lengthSqr() < 1.0E-4D
                        ? getForward()
                        : flat.normalize();
                float aim = (float) (Math.atan2(chargeLine.z, chargeLine.x)
                        * (180.0D / Math.PI)) - 90.0F;
                setYRot(aim);
                this.yBodyRot = aim;
                this.yHeadRot = aim;
                charging = CHARGE_TICKS;
                chargeFrom = position();
                playSound(FFSounds.GOLEM_ROAR.get(), 3.6F, 1.06F);
                // THE LAUNCH. Three hundred tonnes deciding to move is the
                // loudest thing in the fight, and it starts by breaking the
                // floor it pushed off.
                playSound(FFSounds.GOLEM_SLAM.get(), 4.4F, 0.66F);
                thump(0.85F);
                if (level() instanceof ServerLevel sl) {
                    heave(sl, position(), 3.4D, 1.0D);
                }
            }
        }
        // THE LESSON: a charge that runs into one of the arena's PILLARS of ice
        // breaks on it, and the colossus goes down - its own javelins it simply smashes through
        if (charging > 0 && !level().isClientSide) {
            for (com.jastkub.frozenfortress.entity.projectile.IceJavelinEntity p : level().getEntitiesOfClass(
                    com.jastkub.frozenfortress.entity.projectile.IceJavelinEntity.class,
                    getBoundingBox().inflate(0.6D).expandTowards(chargeLine.scale(1.8D)),
                    com.jastkub.frozenfortress.entity.projectile.IceJavelinEntity::isPlanted)) {
                p.shatter(true);
            }
            if (horizontalCollision && hitPillar()) {
                double run = Math.sqrt(position().subtract(chargeFrom).horizontalDistanceSqr());
                if (!carried.isEmpty()) {
                    impale();               // it had you on its arm: you take the pillar, it does not
                } else if (run >= CHARGE_MIN_RUN) {
                    stagger(STAGGER_TICKS);
                    pillarShaken();
                } else {
                    // no run-up, no break: it only shoulders into it and pulls up
                    charging = 0;
                    setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
                    playSound(FFSounds.GOLEM_STEP.get(), 3.0F, 0.6F);
                    thump(0.4F);
                }
            }
        }
        if (charging > 0) {
            charging--;
            if (charging == 0) {
                dropRiders();
            }
            // FAST. It was 0.86, which is a jog - against a walk that is now
            // two thirds of a player's pace, the charge has to be the thing
            // that makes the slowness a trap rather than an exit. This
            // crosses twenty blocks in its twenty-six ticks.
            setDeltaMovement(chargeLine.x * 1.42D, getDeltaMovement().y, chargeLine.z * 1.42D);
            hurtMarked = true;
            if (level() instanceof ServerLevel serverLevel) {
                // THE GROUND COMES UP UNDER EVERY OTHER FOOTFALL. Real floor
                // blocks, off the grid, thrown straight up and dropping back -
                // the run leaves a wake, so the charge is visible from behind
                // and from the side and not only from in front of it.
                if (charging % 4 == 0) {
                    heave(serverLevel, position(), 2.2D, 0.72D);
                    playSound(FFSounds.GOLEM_STEP.get(), 3.6F, 0.62F);
                    thump(0.42F);
                }
                if (tickCount % 2 == 0) {
                    serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                            getX(), getY() + 0.2D, getZ(), 14, 0.9D, 0.2D, 0.9D, 0.16D);
                    serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                            getX(), getY() + 1.4D, getZ(), 6, 0.8D, 0.9D, 0.8D, 0.04D);
                }
            }
            // ================================================================
            // WHAT IT CATCHES, IT TAKES WITH IT.
            //
            // Anything it ran through used to be knocked clear on the tick of
            // contact - which is the ordinary way to write a charge and throws
            // away the only thing that makes THIS one different. It is running
            // behind a raised arm. An arm held across the front of something
            // moving at a block and a half a tick does not bat you aside, it
            // SCOOPS you, and you go where it is going until it stops.
            //
            // So a hit pins them to the front of the arm and they are carried.
            // The distance is the punishment, not the damage: they arrive
            // wherever the charge ended, which is across the arena and usually
            // into a wall, and they spend the whole trip unable to do anything
            // about it.
            //
            // Held by POSITION rather than by velocity, because velocity gets
            // eaten by their own friction, their own movement and the server's
            // knockback resistance - and a scoop that leaks is a scoop the
            // player slides out of halfway.
            // ================================================================
            // only what is close in front of the arm
            for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(0.35D, 0.0D, 0.35D),
                    e -> e != this && e.isAlive() && !(e instanceof HollowGolemEntity) && !mySide(e)
                            && chargeLine.dot(new Vec3(e.getX() - getX(), 0.0D, e.getZ() - getZ()).normalize()) > 0.35D)) {
                if (com.jastkub.frozenfortress.entity.FFAllies.ofTheKing(victim)) {
                    continue;                      // it does not trample its own
                }
                if (!struck.add(victim.getUUID())) {
                    continue;           // ONCE per charge, not once per tick
                }
                strike(victim, 3.0F * CHARGE_SOFTER);
                carried.add(victim.getUUID());
                victim.hurtMarked = true;
                thump(1.0F);
                playSound(FFSounds.GOLEM_SLAM.get(), 4.6F, 0.72F);
                if (level() instanceof ServerLevel sl) {
                    heave(sl, victim.position(), 3.0D, 1.1D);
                }
            }

            // ---- and everything on the arm rides along
            if (!carried.isEmpty() && level() instanceof ServerLevel sl) {
                Vec3 hook = position()
                        .add(chargeLine.scale(2.6D))
                        .add(0.0D, 1.1D, 0.0D);
                java.util.Iterator<java.util.UUID> it = carried.iterator();
                while (it.hasNext()) {
                    net.minecraft.world.entity.Entity e = sl.getEntity(it.next());
                    if (!(e instanceof LivingEntity rider) || !rider.isAlive()) {
                        it.remove();
                        continue;
                    }
                    // spread them across the arm so two riders are not inside
                    // each other
                    double spread = (rider.getId() % 3 - 1) * 1.3D;
                    Vec3 side = new Vec3(-chargeLine.z, 0.0D, chargeLine.x).scale(spread);
                    rider.setPos(hook.x + side.x, hook.y, hook.z + side.z);
                    rider.setDeltaMovement(chargeLine.x * 1.42D, 0.0D, chargeLine.z * 1.42D);
                    rider.hurtMarked = true;
                    rider.fallDistance = 0.0F;
                    if (charging % 5 == 0) {
                        strike(rider, 0.8F);
                        sl.sendParticles(FFParticles.ICE_SHARD.get(),
                                rider.getX(), rider.getY() + 0.9D, rider.getZ(),
                                14, 0.5D, 0.5D, 0.5D, 0.2D);
                    }
                    if (rider instanceof net.minecraft.server.level.ServerPlayer sp) {
                        sp.connection.send(new net.minecraft.network.protocol.game
                                .ClientboundSetEntityMotionPacket(sp));
                    }
                }
            }
        }
    }

    /**
     * Picks and runs one of the six.
     *
     * <p>Deliberately NOT a goal. A goal that owns navigation fights the melee
     * goal for the same tick and the result is a golem that shuffles instead
     * of committing; the charge already had to be pulled out for that reason.
     * One switch, one clock, and navigation is stopped for the duration of
     * anything that is not the charge.
     */
    private void tickAttacks() {
        LivingEntity target = getTarget();
        grabCooldown = Math.max(0, grabCooldown - 1);
        roarCooldown = Math.max(0, roarCooldown - 1);
        geyserCooldown = Math.max(0, geyserCooldown - 1);
        javelinCooldown = Math.max(0, javelinCooldown - 1);
        avalancheCooldown = Math.max(0, avalancheCooldown - 1);
        if (level() instanceof ServerLevel icl && !isTamed()) {
            tickIcicles(icl);                            // (the prison's dome: not a tamed one's)
        }
        if (isTamed()) {
            tickTamed();
            if (ridden() && attack == ATK_NONE) {
                return;                                  // its rider chooses (riderStruck)
            }
        }
        // the bombs reload on their own now (the drain that fed them is gone)
        if (magazine < SHARD_MAG && tickCount % 200 == 0) {
            magazine++;
        }
        if (attack == ATK_NONE) {
            if (attackCooldown > 0) {
                attackCooldown--;
                return;
            }
            if (windup > 0 || charging > 0) {
                return;
            }
            if (target == null || !target.isAlive()) {
                // NOTHING TO FIGHT. He does not simply switch off - he
                // bellows at the empty room every so often, which is both
                // the only thing a thing like this would do and the proof
                // to anyone watching that he is not a statue.
                if (random.nextInt(200) == 0 && !isTamed()) {
                    begin(ATK_ROAR);
                }
                return;
            }
            // THE FRACTURE, once, the moment it drops under half: its second phase
            if (!fractureDone && getHealth() < getMaxHealth() * 0.5F) {
                fractureDone = true;
                begin(ATK_FRACTURE);
                return;
            }
            double d = distanceTo(target);
            Vec3 to = target.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            boolean behind = flat.lengthSqr() > 1.0E-4D && getForward().dot(flat.normalize()) < -0.2D;
            // a pillar between them: no throwing at it - it walks round (tickStride)
            boolean sighted = clearShot(target);
            java.util.List<Integer> pool = new java.util.ArrayList<>();
            // at its legs or behind it: the stamping
            if (d < 7.0D && (behind || d < 3.5D)) {
                pool.add(ATK_STOMP2);
                pool.add(ATK_STOMP2);
                pool.add(ATK_STOMP2);
            }
            // in front and in reach: the fists
            if (d < 6.5D && !behind) {
                pool.add(ATK_COMBO);
                pool.add(ATK_COMBO);
                pool.add(ATK_HAMMER);
                if (grabCooldown <= 0 && d < 5.8D) {
                    pool.add(ATK_GRAB);
                }
            }
            // the hammer's wave reaches past its fists
            if (d >= 6.5D && d < 10.0D && !behind && sighted) {
                pool.add(ATK_HAMMER);
            }
            // THE BOMBS (kept), at the band a thrown arc is worth more than a fist
            if (d >= 6.0D && d < 26.0D && magazine > 0 && sighted && lastAttack != ATK_SHARDS) {
                pool.add(ATK_SHARDS);
                pool.add(ATK_SHARDS);
            }
            // the javelins: never more than three standing at once
            if (d >= 7.0D && d < 30.0D && javelinCooldown <= 0 && pillars(40.0D).size() < 3 && sighted
                    && lastAttack != ATK_JAVELIN) {
                pool.add(ATK_JAVELIN);
                pool.add(ATK_JAVELIN);
            }
            if (d >= 5.0D && d < 24.0D && geyserCooldown <= 0 && sighted && lastAttack != ATK_GEYSER) {
                pool.add(ATK_GEYSER);
            }
            // the avalanche along the floor at them (any time now: the icicles fall on their own)
            if (avalancheCooldown <= 0 && d >= 5.0D && d < 26.0D && sighted && lastAttack != ATK_AVALANCHE) {
                pool.add(ATK_AVALANCHE);
                pool.add(ATK_AVALANCHE);
            }
            // THE ROAR (07.10.2026): the dome brought down on them - from anywhere in its hall
            if (roarCooldown <= 0 && d >= 4.0D && lastAttack != ATK_ROAR) {
                pool.add(ATK_ROAR);
                pool.add(ATK_ROAR);
            }
            // THE FROST BREATH, back - a gust of ice, not a cone (GolemBreathLayer)
            if (d < 9.0D && !behind && sighted && level().getGameTime() >= breathReadyAt && lastAttack != ATK_BREATH) {
                pool.add(ATK_BREATH);
                pool.add(ATK_BREATH);
            }
            if (isTamed()) {
                // a tamed one keeps to its fists, its feet, its breath and its grip: nothing it throws or rains down
                // knows whose side anyone is on
                pool.removeIf(a -> a == ATK_SHARDS || a == ATK_JAVELIN || a == ATK_GEYSER || a == ATK_AVALANCHE
                        || a == ATK_ROAR);
            }
            if (!pool.isEmpty()) {
                begin(pool.get(random.nextInt(pool.size())));
            }
            return;
        }

        attackTicks++;
        // (06.10.2026) NO SKIPPED TICKS IN ITS FURY. It used to run an extra tick every fourth one to
        // shorten every window - which jumped over every tick divisible by four, and every attack
        // that lets go on one (the bombs at 40, the javelin at 24, the geyser at 20, the avalanche at
        // 20 and 32, the hammer at 24, the grab's smash at 44) wound up and did nothing. Its fury
        // shortens the rests between attacks instead (end()).
        if (attack != ATK_ROAR) {
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        }
        if (target != null && attackTicks < 16) {
            getLookControl().setLookAt(target, 20.0F, 20.0F);
        }
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }

        switch (attack) {
            case ATK_HAMMER -> {
                if (attackTicks == 2) {
                    playSound(FFSounds.GOLEM_CREAK.get(), 3.0F, 0.6F);
                }
                if (attackTicks == 14) {
                    playSound(FFSounds.GOLEM_ROAR.get(), 2.4F, 1.3F);          // the heave
                }
                if (attackTicks == 24) {
                    Vec3 at = position().add(getForward().scale(3.6D));
                    playSound(FFSounds.GOLEM_SLAM.get(), 4.8F, 0.7F);
                    playSound(FFSounds.GOLEM_STOMP.get(), 4.4F, 0.55F);
                    thump(1.0F);
                    shake(sl, 30);
                    com.jastkub.frozenfortress.event.FloorScarHandler.tear(sl, at, 3.6D, 1.0D);
                    heave(sl, at, 4.0D, 1.0D);
                    for (LivingEntity v : victims(7.0D)) {                     // under the fists
                        if (v.distanceToSqr(at) < 3.2D * 3.2D && struck.add(v.getUUID())) {
                            strike(v, 3.0F);
                            v.setDeltaMovement(v.getDeltaMovement().x * 0.3D, 0.9D, v.getDeltaMovement().z * 0.3D);
                            v.hurtMarked = true;
                            kick(v);
                        }
                    }
                    // and a wave of frost out in a cone before it, spikes down its middle
                    sl.addFreshEntity(new FrostWaveEntity(level(), this, at.x, getY(), at.z, 9.0F, 14, 9.0F)
                            .arc(getYRot(), 60.0F).spare(struck));
                    for (int i = 0; i < 4; i++) {
                        Vec3 p = position().add(getForward().scale(5.6D + i * 1.8D));
                        level().addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity(
                                level(), this, p.x, getY(), p.z, 10.0F, 4 + i * 3));
                    }
                }
                if (attackTicks > 54) {
                    end(30);
                }
            }
            case ATK_COMBO -> {
                boolean three = berserk();
                if (attackTicks == 3) {
                    playSound(FFSounds.GOLEM_CREAK.get(), 2.6F, 1.1F);
                }
                if (attackTicks == 14) {                                       // the sweep, across its whole front
                    playSound(FFSounds.GOLEM_SWEEP.get(), 4.2F, 0.9F);
                    thump(0.45F);
                    Vec3 out = getForward();
                    for (LivingEntity v : victims(6.5D)) {
                        Vec3 fl = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
                        if (fl.lengthSqr() > 1.0E-4D && out.dot(fl.normalize()) < -0.15D || !struck.add(v.getUUID())) {
                            continue;
                        }
                        strike(v, 2.0F);
                        Vec3 side = flank().scale(-1.0D);
                        v.setDeltaMovement(side.x * 1.1D + out.x * 0.4D, 0.45D, side.z * 1.1D + out.z * 0.4D);
                        v.hurtMarked = true;
                        kick(v);
                    }
                    shake(sl, 14);
                }
                if (attackTicks == 24 || attackTicks == 40) {
                    struck.clear();                                            // the next blow may land again
                }
                if (attackTicks == 30) {                                       // the uppercut, straight ahead
                    playSound(FFSounds.GOLEM_SWEEP.get(), 4.2F, 1.25F);
                    Vec3 out = getForward();
                    for (LivingEntity v : victims(5.8D)) {
                        Vec3 fl = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
                        if (fl.lengthSqr() > 1.0E-4D && out.dot(fl.normalize()) < 0.55D || !struck.add(v.getUUID())) {
                            continue;
                        }
                        strike(v, 2.4F);
                        v.setDeltaMovement(out.x * 0.3D, 1.15D, out.z * 0.3D);
                        v.hurtMarked = true;
                        kick(v);
                    }
                    thump(0.6F);
                }
                if (three && attackTicks == 48) {                              // and both fists, down
                    Vec3 at = position().add(getForward().scale(3.4D));
                    boom(sl, at, 4.2D, 3.0F, 2.6F, 0.6D);
                    playSound(FFSounds.GOLEM_SLAM.get(), 4.6F, 0.8F);
                    thump(0.9F);
                    com.jastkub.frozenfortress.event.FloorScarHandler.tear(sl, at, 3.0D, 0.8D);
                }
                if (attackTicks > (three ? 70 : 50)) {
                    end(three ? 26 : 34);
                }
            }
            case ATK_STOMP2 -> {
                if (attackTicks == 12 || attackTicks == 28) {
                    boolean left = attackTicks == 12;
                    Vec3 foot = position().add(flank().scale(left ? -1.4D : 1.4D));
                    playSound(FFSounds.GOLEM_STOMP.get(), 4.4F, left ? 0.95F : 0.78F);
                    thump(0.8F);
                    shake(sl, 18);
                    heave(sl, foot, 2.6D, 0.9D);
                    struck.clear();
                    for (LivingEntity v : victims(4.0D)) {                     // right under it
                        if (v.distanceToSqr(foot) < 3.0D * 3.0D && struck.add(v.getUUID())) {
                            strike(v, 1.6F);
                            Vec3 away = v.position().subtract(foot).normalize();
                            v.setDeltaMovement(away.x * 0.8D, 0.5D, away.z * 0.8D);
                            v.hurtMarked = true;
                            kick(v);
                        }
                    }
                    sl.addFreshEntity(new FrostWaveEntity(level(), this, foot.x, getY(), foot.z,
                            left ? 7.0F : 8.5F, 12, 8.0F).light().spare(struck));
                }
                if (attackTicks > 46) {
                    end(30);
                }
            }
            case ATK_JAVELIN -> {
                if (target != null && attackTicks < JAVELIN_LET_GO) {
                    faceTowards(target);                                       // turned to them before it throws
                }
                if (attackTicks == 14) {                                       // torn off its back
                    playSound(FFSounds.ICE_CRACK.get(), 3.2F, 0.5F);
                    playSound(FFSounds.GOLEM_CREAK.get(), 2.6F, 0.8F);
                    Vec3 back = position().add(getForward().scale(-1.0D)).add(0.0D, 7.0D, 0.0D);
                    sl.sendParticles(FFParticles.ICE_SHARD.get(), back.x, back.y, back.z, 30, 0.8D, 0.6D, 0.8D, 0.2D);
                }
                if (attackTicks == JAVELIN_LET_GO && target != null) {         // and thrown
                    // (07.10.2026) from the hand, on the tick the clip lets it go - measured (pose_model, "javelin" at
                    // 1.0 s): it was thrown from six blocks up beside its head four ticks after the arm had come down
                    Vec3 hand = atBody(position(), getYRot(), JAVELIN_RIGHT, JAVELIN_UP, JAVELIN_FWD);
                    // at the body, not the feet: a throw at the feet planted itself in the floor under them unhurt
                    Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                            .add(target.getDeltaMovement().scale(10.0D));
                    int flight = 12 + (int) (Math.sqrt(hand.distanceToSqr(aim)) / 2.4D);
                    sl.addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.IceJavelinEntity(
                            level(), this, hand, aim, flight));
                    playSound(FFSounds.GOLEM_SWEEP.get(), 4.0F, 0.7F);
                    playSound(net.minecraft.sounds.SoundEvents.PHANTOM_SWOOP, 3.0F, 0.5F);
                    javelinCooldown = 90;
                }
                if (attackTicks > 42) {
                    end(24);
                }
            }
            case ATK_GEYSER -> {
                if (attackTicks == 4) {
                    playSound(FFSounds.GOLEM_CREAK.get(), 3.4F, 0.5F);
                }
                if (attackTicks == 20) {
                    Vec3 trough = position().add(0.0D, 6.6D, 0.0D);
                    playSound(net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, 4.0F, 0.5F);
                    playSound(FFSounds.SHOCKWAVE.get(), 2.6F, 1.4F);
                    shake(sl, 16);
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, trough.x, trough.y + 1.0D,
                            trough.z, 120, 0.8D, 2.4D, 0.8D, 0.6D);
                    sl.sendParticles(FFParticles.FROST_SWIRL.get(), trough.x, trough.y + 2.0D, trough.z,
                            40, 0.6D, 2.0D, 0.6D, 0.12D);
                    java.util.List<Vec3> spots = new java.util.ArrayList<>();
                    for (LivingEntity v : victims(24.0D)) {
                        if (v instanceof Player) {
                            spots.add(v.position());                           // one under everybody
                        }
                    }
                    Vec3 c = target != null ? target.position() : position();
                    int n = berserk() ? 7 : 5;
                    for (int k = 0; spots.size() < n && k < 30; k++) {
                        double ang = random.nextDouble() * Math.PI * 2.0D;
                        double r = 3.0D + random.nextDouble() * 7.0D;
                        Vec3 p = c.add(Math.cos(ang) * r, 0.0D, Math.sin(ang) * r);
                        if (p.distanceToSqr(position()) > 16.0D) {
                            spots.add(p);
                        }
                    }
                    // out of the trough
                    // they go, high, one after another, and each comes down where its pool will be
                    geyserShots.clear();
                    for (Vec3 p : spots) {
                        geyserShots.add(new Vec3(p.x, floorAt(p.x, p.y, p.z), p.z));
                    }
                    geyserCooldown = 260;
                }
                if (attackTicks > 20 && !geyserShots.isEmpty() && attackTicks % 2 == 0) {
                    Vec3 at = geyserShots.remove(0);
                    Vec3 trough = position().add(random.nextGaussian() * 0.6D, 6.6D, random.nextGaussian() * 0.6D);
                    int flight = 22 + (int) (Math.sqrt(trough.distanceToSqr(at)) * 0.7D);
                    sl.addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.FrostGlobEntity(
                            level(), this, trough, at, flight));
                    playSound(net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, 2.6F, 0.6F + random.nextFloat() * 0.3F);
                }
                if (attackTicks > 52) {
                    geyserShots.clear();
                    end(30);
                }
            }
            case ATK_AVALANCHE -> {
                // THE AVALANCHE: both fists into the floor twice, then over its head and
                // down with all of it - and the floor sends a wave of snow and ice at them
                if (attackTicks == 12 || attackTicks == 22) {
                    thump(0.55F);
                    shake(sl, 12);
                    playSound(FFSounds.GOLEM_SLAM.get(), 3.6F, 0.9F + attackTicks * 0.01F);
                }
                if (attackTicks == 30) {
                    playSound(FFSounds.GOLEM_ROAR.get(), 4.2F, 1.0F);
                }
                if (attackTicks == 38) {
                    thump(1.0F);
                    shake(sl, 30);
                    playSound(FFSounds.GOLEM_SLAM.get(), 5.0F, 0.7F);
                    playSound(FFSounds.GOLEM_AVALANCHE.get(), 5.0F, 1.0F);
                    Vec3 aim = target != null ? target.position().subtract(position()) : getForward();
                    Vec3 dir = new Vec3(aim.x, 0.0D, aim.z);
                    dir = dir.lengthSqr() > 1.0E-4D ? dir.normalize() : getForward();
                    Vec3 from = position().add(dir.scale(3.4D));
                    sl.addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.AvalancheEntity(level(), this, from, dir));
                    if (berserk()) {                                   // in its fury, a fan of three
                        for (double a : new double[]{-0.45D, 0.45D}) {
                            Vec3 dd = new Vec3(dir.x * Math.cos(a) - dir.z * Math.sin(a), 0.0D,
                                    dir.x * Math.sin(a) + dir.z * Math.cos(a));
                            sl.addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.AvalancheEntity(
                                    level(), this, position().add(dd.scale(3.4D)), dd));
                        }
                    }
                    // a party: one more wave at every other fighter not already in the path of the first (PartyScaling)
                    for (net.minecraft.world.entity.player.Player other : com.jastkub.frozenfortress.event.PartyScaling.others(this, target,
                            com.jastkub.frozenfortress.event.PartyScaling.extra(this))) {
                        Vec3 o = new Vec3(other.getX() - getX(), 0.0D, other.getZ() - getZ());
                        if (o.lengthSqr() < 1.0E-4D || o.normalize().dot(dir) > 0.94D) {
                            continue;
                        }
                        Vec3 od = o.normalize();
                        sl.addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.AvalancheEntity(
                                level(), this, position().add(od.scale(3.4D)), od));
                    }
                }
                if (attackTicks > 60) {
                    avalancheCooldown = berserk() ? 260 : 360;
                    end(30);
                }
            }
            case ATK_GRAB -> {
                if (attackTicks == 1) {
                    grabCooldown = 320;
                    grabbed = null;
                }
                if (attackTicks == 4) {
                    playSound(FFSounds.GOLEM_CREAK.get(), 3.0F, 1.2F);
                }
                if (attackTicks == 18) {                                       // the hand closes
                    Vec3 out = getForward();
                    LivingEntity best = null;
                    double bd = 99.0D;
                    for (LivingEntity v : victims(6.0D)) {
                        Vec3 fl = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
                        double dist = fl.length();
                        if (dist < 7.5D && dist > 1.0E-3D && out.dot(fl.normalize()) > 0.55D && dist < bd) {
                            best = v;
                            bd = dist;
                        }
                    }
                    if (best == null) {
                        playSound(FFSounds.GOLEM_SWEEP.get(), 3.0F, 1.4F);
                        end(40);                                               // missed: it straightens up
                        break;
                    }
                    grabbed = best.getUUID();
                    playSound(FFSounds.GOLEM_SLAM.get(), 3.0F, 1.4F);
                    playSound(FFSounds.ICE_IMPACT.get(), 2.4F, 1.2F);
                }
                if (grabbed != null && attackTicks > 18) {
                    if (!(sl.getEntity(grabbed) instanceof LivingEntity held) || !held.isAlive()) {
                        grabbed = null;
                        end(30);
                        break;
                    }
                    // where the fist is: up from the chest to over its head, then down
                    double up = attackTicks < 30 ? 3.6D + (attackTicks - 18) * 0.3D : attackTicks < 42 ? 7.2D : 1.5D;
                    Vec3 at = position().add(getForward().scale(attackTicks < 42 ? 3.0D : 3.8D)).add(0.0D, up, 0.0D);
                    if (held instanceof net.minecraft.server.level.ServerPlayer sp) {
                        sp.teleportTo(at.x, at.y, at.z);
                    } else {
                        held.setPos(at.x, at.y, at.z);
                    }
                    held.setDeltaMovement(Vec3.ZERO);
                    held.fallDistance = 0.0F;
                    held.hurtMarked = true;
                    kick(held);
                    if (attackTicks == 44) {                                   // and smashed down
                        double fy = floorAt(at.x, at.y, at.z);
                        if (held instanceof net.minecraft.server.level.ServerPlayer sp) {
                            sp.teleportTo(at.x, fy, at.z);
                        } else {
                            held.setPos(at.x, fy, at.z);
                        }
                        struck.add(held.getUUID());
                        strike(held, 2.6F);
                        held.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                com.jastkub.frozenfortress.registry.FFEffects.FROSTBITE, 100, 0), this);
                        playSound(FFSounds.GOLEM_SLAM.get(), 4.8F, 0.75F);
                        thump(1.0F);
                        shake(sl, 26);
                        heave(sl, new Vec3(at.x, fy, at.z), 3.0D, 0.9D);
                        sl.addFreshEntity(new FrostWaveEntity(level(), this, at.x, fy, at.z, 4.5F, 10, 6.0F)
                                .light().spare(struck));
                        grabbed = null;
                    }
                }
                if (attackTicks > 70) {
                    end(30);
                }
            }
            case ATK_STAGGER -> {
                if (attackTicks == 1) {
                    playSound(FFSounds.GOLEM_SLAM.get(), 4.6F, 0.5F);
                    playSound(FFSounds.ICE_IMPACT.get(), 4.0F, 0.6F);
                    thump(1.0F);
                    shake(sl, 30);
                    heave(sl, position(), 3.0D, 0.8D);
                }
                if (attackTicks % 20 == 10) {
                    playSound(FFSounds.GOLEM_CREAK.get(), 2.2F, 0.7F);       // it groans, trying to rise
                }
                if (attackTicks % 12 == 6) {                                   // the stars over it, tinkling
                    playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 1.5F + random.nextFloat() * 0.4F);
                }
                if (attackTicks % 4 == 0) {                                    // its heart, open
                    Vec3 heart = position().add(getForward().scale(-0.6D)).add(0.0D, 5.4D, 0.0D);
                    sl.sendParticles(FFParticles.SOUL_FROST.get(), heart.x, heart.y, heart.z, 4, 0.5D, 0.4D, 0.5D, 0.02D);
                }
                if (attackTicks > staggerLen) {
                    end(20);
                }
            }
            case ATK_FRACTURE -> {
                if (attackTicks == 2) {
                    playSound(FFSounds.GOLEM_ROAR.get(), 5.0F, 1.0F);
                }
                if (attackTicks == 24) {
                    entityData.set(FRACTURED, true);
                    playSound(FFSounds.ICE_CRACK.get(), 5.0F, 0.45F);
                    playSound(FFSounds.SHOCKWAVE.get(), 4.0F, 0.6F);
                    shake(sl, 50);
                    thump(1.0F);
                    for (int side = -1; side <= 1; side += 2) {
                        Vec3 sh = position().add(flank().scale(side * 3.6D)).add(0.0D, 6.0D, 0.0D);
                        sl.sendParticles(FFParticles.ICE_SHARD.get(), sh.x, sh.y, sh.z, 80, 1.2D, 1.2D, 1.2D, 0.35D);
                    }
                    var speed = getAttribute(Attributes.MOVEMENT_SPEED);
                    if (speed != null) {
                        speed.setBaseValue(speed.getBaseValue() * 1.25D);
                    }
                    sl.addFreshEntity(new FrostWaveEntity(level(), this, getX(), getY(), getZ(), 9.0F, 16, 6.0F).light());
                }
                if (attackTicks > 52) {
                    end(20);
                }
            }
            case ATK_SLAM -> {
                if (attackTicks == 22) {
                    Vec3 at = position().add(getForward().scale(3.4D));
                    boom(sl, at, 5.0D, 3.4F, 4.0F, 0.5D);
                    playSound(FFSounds.GOLEM_SLAM.get(), 4.6F, 0.82F);
                    // ---- THE ROOM TAKES IT. boom() carries knockback for
                    //      whoever was standing in the blast; this is for
                    //      everyone who was not. Both fists, from over the
                    //      head, into stone - it should be felt from the far
                    //      wall, not only by the person it landed on.
                    playSound(FFSounds.GOLEM_STOMP.get(), 4.2F, 0.6F);
                    thump(1.0F);
                    shake(sl, 26);
                    // ---- AND THE FLOOR OPENS. It was a shockwave particle
                    //      and nothing else: the one attack whose entire
                    //      premise is two fists driven into the ground left
                    //      the ground exactly as it found it.
                    com.jastkub.frozenfortress.event.FloorScarHandler.tear(
                            sl, at, 4.2D, 1.0D);
                    heave(sl, at, 4.6D, 0.95D);
                    sl.sendParticles(FFParticles.FROST_SWIRL.get(),
                            at.x, at.y + 0.5D, at.z, 40, 2.2D, 0.6D, 2.2D, 0.22D);
                }
                if (attackTicks > 38 + REC_SLAM) {
                    end(26);
                }
            }
            case ATK_SWEEP -> {
                // A backhand across the whole front - it is the answer to
                // strafing, so it takes a wide arc rather than a cone.
                if (attackTicks == 20) {
                    playSound(FFSounds.GOLEM_SWEEP.get(), 4.0F, 1.0F);
                    Vec3 out = getForward();
                    for (LivingEntity v : victims(8.0D)) {
                        Vec3 to = v.position().subtract(position()).normalize();
                        if (out.dot(to) < -0.25D) {
                            continue;
                        }
                        if (!struck.add(v.getUUID())) {
                            continue;
                        }
                        strike(v, 3.2F);
                        Vec3 away = v.position().subtract(position()).normalize();
                        v.setDeltaMovement(away.x * 0.9D, 0.52D, away.z * 0.9D);
                        v.hurtMarked = true;
                        kick(v);
                    }
                    sl.sendParticles(FFParticles.ICE_SHARD.get(),
                            getX(), getY() + 3.2D, getZ(), 60, 3.4D, 0.9D, 3.4D, 0.24D);
                    shake(sl, 16);
                }
                if (attackTicks > 34) {
                    end(46);
                }
            }
            case ATK_STOMP -> {
                if (attackTicks == 22) {
                    playSound(FFSounds.GOLEM_STOMP.get(), 4.4F, 1.0F);
                    boom(sl, position(), 7.5D, 2.6F, 3.0F, 0.62D);
                    // and a ring of spikes coming up through the floor
                    for (int i = 0; i < 14; i++) {
                        double ang = Math.PI * 2.0D * i / 14.0D;
                        level().addFreshEntity(new com.jastkub.frozenfortress.entity.projectile
                                .IceSpikeEntity(level(), this,
                                getX() + Math.cos(ang) * 6.0D, getY(),
                                getZ() + Math.sin(ang) * 6.0D, 8.0F, i % 3));
                    }
                }
                if (attackTicks > 40 + REC_STOMP) {
                    end(38);
                }
            }
            case ATK_DRAIN -> {
                if (attackTicks == 4) {
                    playSound(FFSounds.GOLEM_CREAK.get(), 3.4F, 0.7F);
                }
                if (attackTicks == DRAIN_SETTLE) {
                    playSound(FFSounds.GOLEM_STOMP.get(), 3.2F, 1.4F);
                    shake(sl, 8);
                }
                if (attackTicks > DRAIN_SETTLE && attackTicks < DRAIN_END
                        && attackTicks % 3 == 0) {
                    int took = com.jastkub.frozenfortress.event.RimeHandler
                            .drain(sl, position(), DRAIN_REACH, DRAIN_RATE);
                    if (took == 0) {
                        end(60);        // the floor is clean; nothing to feed on
                        break;
                    }
                    heal(DRAIN_HEAL * took);
                    // the reload is the point, not a side effect
                    if (magazine < SHARD_MAG && random.nextInt(4) == 0) {
                        magazine++;
                    }
                    // ONE puff per feeding tick, at the hands. The white
                    // retreating across the floor is the effect; a cloud of
                    // particles on top of it would only hide the thing the
                    // player is supposed to be reading.
                    Vec3 at = position().add(getForward().scale(3.0D)).add(0.0D, 0.6D, 0.0D);
                    sl.sendParticles(FFParticles.FROST_SWIRL.get(),
                            at.x, at.y, at.z, 3, 0.8D, 0.3D, 0.8D, 0.04D);
                    if (attackTicks % 12 == 0) {
                        playSound(FFSounds.GOLEM_IDLE.get(), 2.6F, 1.5F);
                    }
                }
                if (attackTicks > DRAIN_END) {
                    end(200);
                }
            }
            case ATK_BREATH -> {
                if (attackTicks == 1) {
                    chill.clear();
                    frozenByBreath.clear();
                    playSound(FFSounds.GOLEM_CREAK.get(), 3.4F, 0.55F);
                }
                if (attackTicks == 8) {
                    // the draw: air going the WRONG way first
                    playSound(FFSounds.GOLEM_BREATH.get(), 2.6F, 0.6F);
                }
                // ---- IT KEEPS LOOKING AT YOU. The shared preamble only
                //      tracks for the first sixteen ticks of any attack, which
                //      would freeze this one's aim before the breath had even
                //      started - a static cone you step out of once. The turn
                //      itself is still capped at the body's rate, so this is a
                //      sweep, not a lock-on.
                if (target != null && attackTicks < 14) {
                    faceTowards(target);
                }
                // ...and from then on it FOLLOWS: the whole
                // body comes round after them at BREATH_TURN a tick, the head pinned to it (the gust is drawn from the
                // head, and a head leading the body made the stream writhe - 06.10.2026: tick() pins it after the cap).
                // A walk sideways close in is caught; a run round it, or a pillar, is not.
                if (attackTicks >= 14) {
                    getNavigation().stop();
                    if (target != null) {
                        faceTowards(target);
                    }
                }
                if (attackTicks == BREATH_DRAW) {
                    playSound(FFSounds.GOLEM_ROAR.get(), 4.0F, 1.0F);
                    shake(sl, 10);
                }
                if (attackTicks >= BREATH_DRAW && attackTicks < BREATH_END) {
                    int at = attackTicks - BREATH_DRAW + 1;
                    entityData.set(BREATH_T, at);
                    if (at % 24 == 1) {
                        playSound(FFSounds.GOLEM_BREATH.get(), 4.0F, 0.95F + random.nextFloat() * 0.1F);
                    }
                    breathe(sl);
                }
                if (attackTicks == BREATH_END) {
                    entityData.set(BREATH_T, 0);
                    // AND FINALLY: whoever is still carrying it freezes now
                    for (java.util.Map.Entry<Integer, Integer> e : chill.entrySet()) {
                        if (e.getValue() >= BREATH_FINAL_FREEZE
                                && sl.getEntity(e.getKey()) instanceof LivingEntity v
                                && v.isAlive() && frozenByBreath.add(v.getId())) {
                            freezeSolid(sl, v);
                        }
                    }
                    playSound(FFSounds.FROST_CHARGE.get(), 3.0F, 0.6F);
                }
                if (attackTicks > BREATH_DONE) {
                    breathReadyAt = level().getGameTime() + BREATH_EVERY;
                    end(80);
                }
            }
            case ATK_SWIPE -> {
                if (attackTicks == 4) {
                    playSound(FFSounds.GOLEM_CREAK.get(), 2.6F, 1.5F);
                }
                if (attackTicks == SWIPE_HIT) {
                    playSound(FFSounds.GOLEM_SWEEP.get(), 4.0F, 1.2F);
                    Vec3 out = getForward();
                    Vec3 arm = position().add(out.scale(3.0D)).add(0.0D, 3.4D, 0.0D);
                    sl.sendParticles(FFParticles.ICE_SHARD.get(),
                            arm.x, arm.y, arm.z, 40, 2.0D, 0.9D, 2.0D, 0.28D);
                    for (LivingEntity v : victims(SWIPE_REACH)) {
                        Vec3 to = v.position().subtract(position());
                        Vec3 flat = new Vec3(to.x, 0.0D, to.z);
                        if (flat.lengthSqr() > 1.0E-4D
                                && out.dot(flat.normalize()) < 0.1D) {
                            continue;              // a backhand, not a ring
                        }
                        // ALL DISPLACEMENT. Half a heart and a long way: the
                        // point is where they end up, and a swipe that also
                        // hurt would make standing close a damage problem
                        // rather than a positioning one.
                        strike(v, 1.0F);
                        Vec3 go = flat.lengthSqr() > 1.0E-4D
                                ? flat.normalize() : out;
                        v.setDeltaMovement(go.x * 2.2D, 0.78D, go.z * 2.2D);
                        v.hurtMarked = true;
                        if (v instanceof net.minecraft.server.level.ServerPlayer sp) {
                            sp.connection.send(new net.minecraft.network.protocol.game
                                    .ClientboundSetEntityMotionPacket(sp));
                        }
                    }
                    thump(0.5F);
                    shake(sl, 10);
                }
                if (attackTicks > SWIPE_HIT + 14) {
                    end(70);
                }
            }
            case ATK_SHARDS -> {
                // ---- 12-40: THE ORB. It builds a ball in its hand, and the
                //      ball is the attack's entire telegraph.
                //
                // This was a cloud of particles sprayed from a point with a
                // random spread, which is what "an orb" looks like when it is
                // written as a particle count: a fog that gets denser. A ball
                // needs a SURFACE. These are placed, not sprayed - latitude
                // bands of motes sitting at exact points on a sphere, emitted
                // with zero velocity so they stay where they are put, and the
                // bands counter-rotate so the thing reads as turning rather
                // than as flickering. The radius rides the square of the
                // charge, so it creeps and then swells.
                // ---- 0-8: it squares up to them (turnCap lets it come round fast), then plants
                if (attackTicks < 9 && target != null) {
                    float want = (float) (Math.atan2(target.getZ() - getZ(), target.getX() - getX()) * (180.0D / Math.PI)) - 90.0F;
                    float step = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(want - getYRot()), -6.0F, 6.0F);
                    setYRot(getYRot() + step);
                    yBodyRot = getYRot();
                    yHeadRot = getYRot();
                }
                if (attackTicks == 9) {
                    thump(0.2F);                                 // the stance taken: its foot set down
                    playSound(FFSounds.GOLEM_STEP.get(), 3.0F, 0.85F);
                }
                if (attackTicks == 12) {
                    // its own sound now, one breath of it to the shot
                    playSound(FFSounds.GOLEM_BOMB_CHARGE.get(), 3.2F, 1.0F);
                }
                if (attackTicks >= 12 && attackTicks < SHARD_FIRE) {
                    float charge = (attackTicks - 12) / (float) (SHARD_FIRE - 12);
                    // the orb: light, gathering before its palm (HollowGolemRenderer draws it from this)
                    entityData.set(ORB, charge);
                }

                // ---- THE SALVO (06.10.2026): three bombs of its own, out of the orb before its palm -
                //      the first at them, the others where they would run - and it is set back a step
                //      by the throw.
                if (target != null) {
                    lastAim = target.position();
                }
                if (attackTicks == SHARD_FIRE && lastAim != null) {
                    entityData.set(ORB, 0.0F);
                    Vec3 palm = palmAt(position(), getYRot());
                    Vec3 aim = target != null ? target.position().add(target.getDeltaMovement().scale(6.0D)) : lastAim;
                    Vec3 flat = new Vec3(aim.x - palm.x, 0.0D, aim.z - palm.z);
                    Vec3 fwd = flat.lengthSqr() > 1.0E-4D ? flat.normalize() : getForward();
                    Vec3 side = new Vec3(-fwd.z, 0.0D, fwd.x);
                    for (int i = 0; i < SHARD_COUNT; i++) {
                        Vec3 at = i == 0 ? aim : aim.add(side.scale((i == 1 ? -1 : 1) * (2.0D + random.nextDouble() * 1.5D)))
                                .add(fwd.scale(random.nextDouble() * 2.0D - 1.0D));
                        int flight = 12 + (int) (Math.sqrt(palm.distanceToSqr(at)) * 0.55D);
                        sl.addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.IceBombEntity(
                                level(), this, palm, at, flight));
                    }
                    magazine--;
                    // THE SHOT: its own boom
                    playSound(FFSounds.GOLEM_BOMB_FIRE.get(), 5.0F, 0.95F + random.nextFloat() * 0.1F);
                    // THE RECOIL: what it threw pushes back on it
                    Vec3 back = Vec3.directionFromRotation(0.0F, getYRot()).scale(-0.55D);
                    setDeltaMovement(back.x, getDeltaMovement().y, back.z);
                    hurtMarked = true;
                    thump(0.35F);
                }
                if (attackTicks > SHARD_FIRE + 26 + REC_SHARDS) {
                    end(96);
                }
            }
            case ATK_LEAP -> {
                // ---- 0-16 the coil. It does not move, and the spot it is
                //      about to land on is chosen at the END of it, so the
                //      wind-up is a real read rather than a formality.
                if (attackTicks < 16) {
                    setDeltaMovement(getDeltaMovement().x * 0.5D,
                                     getDeltaMovement().y,
                                     getDeltaMovement().z * 0.5D);
                    if (attackTicks == 4) {
                        playSound(FFSounds.GOLEM_CREAK.get(), 3.0F, 0.6F);
                    }
                    if (attackTicks % 3 == 0) {
                        sl.sendParticles(FFParticles.ICE_SHARD.get(),
                                getX(), getY() + 0.1D, getZ(), 6, 1.6D, 0.05D, 1.6D, 0.02D);
                    }
                }
                // ---- 16: IT GOES. The arc is solved rather than guessed: the
                //      horizontal speed is the distance divided by how long
                //      the fall actually takes at this launch speed, so it
                //      comes down ON them instead of short or past.
                if (attackTicks == 16 && target != null) {
                    Vec3 to = target.position().subtract(position());
                    Vec3 flat = new Vec3(to.x, 0.0D, to.z);
                    double dist = Math.min(LEAP_REACH, flat.length());
                    double air = 2.0D * LEAP_RISE / 0.08D;     // up and back down
                    double push = Math.min(1.25D, dist / Math.max(1.0D, air));
                    Vec3 go = flat.lengthSqr() > 1.0E-4D
                            ? flat.normalize().scale(push) : Vec3.ZERO;
                    setDeltaMovement(go.x, LEAP_RISE, go.z);
                    hasImpulse = true;
                    leapArmed = true;
                    playSound(FFSounds.GOLEM_STOMP.get(), 3.8F, 1.25F);
                    playSound(FFSounds.GOLEM_ROAR.get(), 3.4F, 1.1F);
                    boom(sl, position(), 3.0D, 0.6F, 0.8F, 0.1D);
                }
                // ---- and it comes down. Armed at launch so the landing
                //      cannot fire on the tick it left the floor.
                if (leapArmed && attackTicks > 22 && (onGround() || verticalCollision)) {
                    leapArmed = false;
                    playSound(FFSounds.GOLEM_SLAM.get(), 5.0F, 0.5F);
                    playSound(FFSounds.GOLEM_STOMP.get(), 4.6F, 0.72F);
                    boom(sl, position(), 9.0D, 3.2F, 3.6F, 0.78D);
                    sl.sendParticles(FFParticles.SHOCKWAVE.get(),
                            getX(), getY() + 0.2D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    com.jastkub.frozenfortress.event.FloorScarHandler.tear(
                            sl, position(), 6.0D, 1.0D);
                    // TWO RINGS, and the outer one is late. A single ring is a
                    // circle appearing; two at different radii and different
                    // delays is something spreading OUT from where it landed.
                    for (int ring = 0; ring < 2; ring++) {
                        int count = 9 + ring * 5;
                        double rad = 4.0D + ring * 3.6D;
                        for (int i = 0; i < count; i++) {
                            double ang = Math.PI * 2.0D * i / count
                                    + ring * 0.4D;
                            level().addFreshEntity(new com.jastkub.frozenfortress.entity
                                    .projectile.IceSpikeEntity(level(), this,
                                    getX() + Math.cos(ang) * rad, getY(),
                                    getZ() + Math.sin(ang) * rad,
                                    12.0F, ring * 6 + (i % 3)));
                        }
                    }
                }
                if (attackTicks > 70 + REC_FISSURE) {
                    leapArmed = false;
                    end(124);
                }
            }
            case ATK_FISSURE -> {
                // ---- 21: THE PAW LANDS, and the line is fixed here. See the
                //      note on ATK_FISSURE: it is aimed at where they stood on
                //      this tick and it does not correct afterwards.
                if (attackTicks == 21 && target != null) {
                    playSound(FFSounds.GOLEM_SLAM.get(), 4.2F, 0.72F);
                    playSound(FFSounds.GOLEM_STOMP.get(), 3.4F, 0.85F);
                    boom(sl, position().add(getForward().scale(2.4D)), 3.4D,
                         1.2F, 1.4F, 0.2D);
                    Vec3 here = position();
                    Vec3 aim = target.position();
                    fissureFrom = new Vec3(here.x, here.y, here.z);
                    Vec3 flat = new Vec3(aim.x - here.x, 0.0D, aim.z - here.z);
                    fissureLen = Math.min(FISSURE_REACH, flat.length());
                    fissureDir = flat.lengthSqr() > 1.0E-4D
                            ? flat.normalize() : getForward();
                }

                // ---- THE CRACK RUNS. One step every other tick, so it is
                //      fast enough to be frightening and slow enough to be
                //      outrun - about four blocks a second against a sprint
                //      of five and a half.
                if (fissureDir != null && attackTicks > 21
                        && attackTicks <= 21 + FISSURE_STEPS && attackTicks % 2 == 1) {
                    int step = (attackTicks - 21 + 1) / 2;
                    double along = fissureLen * step / (double) (FISSURE_STEPS / 2);
                    Vec3 at = fissureFrom.add(fissureDir.scale(along));
                    double floor = at.y;
                    // the floor opening, as real blocks rather than motes
                    com.jastkub.frozenfortress.event.FloorScarHandler.tear(
                            sl, new Vec3(at.x, floor, at.z), 1.6D, 0.8D);
                    sl.sendParticles(FFParticles.ICE_SHARD.get(),
                            at.x, floor + 0.2D, at.z, 14, 0.5D, 0.1D, 0.5D, 0.22D);
                    sl.sendParticles(FFParticles.FROST_SWIRL.get(),
                            at.x, floor + 0.4D, at.z, 6, 0.4D, 0.2D, 0.4D, 0.08D);
                    playSound(FFSounds.ICE_CRACK.get(), 2.0F,
                              0.9F + step * 0.06F);
                    // anything standing ON the crack as it passes is clipped -
                    // not the payload, just a reason not to stand on it
                    for (LivingEntity v : victims(2.0D)) {
                        if (v.distanceToSqr(at) < 4.0D && struck.add(v.getUUID())) {
                            v.hurt(damageSources().mobAttack(this), 4.0F);
                        }
                    }
                }

                // ---- THE END OF IT. A cluster comes up under them and throws
                //      them, which is the whole payload - the crack was only
                //      ever the delivery.
                if (fissureDir != null && attackTicks == 21 + FISSURE_STEPS + 2) {
                    Vec3 at = fissureFrom.add(fissureDir.scale(fissureLen));
                    playSound(FFSounds.GOLEM_SLAM.get(), 4.6F, 0.55F);
                    sl.sendParticles(FFParticles.SHOCKWAVE.get(),
                            at.x, at.y + 0.2D, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    // three, at the point and just short of it, so the burst
                    // has width instead of being one spike on one square
                    for (int i = 0; i < 3; i++) {
                        double off = (i - 1) * 1.7D;
                        Vec3 side = new Vec3(-fissureDir.z, 0.0D, fissureDir.x)
                                .scale(off);
                        level().addFreshEntity(new com.jastkub.frozenfortress.entity
                                .projectile.IceSpikeEntity(level(), this,
                                at.x + side.x, at.y, at.z + side.z, 16.0F, i));
                    }
                    for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class,
                            new net.minecraft.world.phys.AABB(at.x - 3.2D, at.y - 2.0D,
                                    at.z - 3.2D, at.x + 3.2D, at.y + 5.0D, at.z + 3.2D),
                            e -> e.isAlive() && e != this && !(e instanceof HollowGolemEntity) && !mySide(e))) {
                        v.hurt(damageSources().mobAttack(this), 12.0F);
                        // THROWN UP, not away. The spikes come from underneath,
                        // so the force is vertical - and being put in the air
                        // is worse than being pushed, because it costs the
                        // player their next second rather than their footing.
                        v.setDeltaMovement(v.getDeltaMovement().x * 0.4D, 1.05D,
                                           v.getDeltaMovement().z * 0.4D);
                        v.hurtMarked = true;
                        if (v instanceof net.minecraft.server.level.ServerPlayer sp) {
                            sp.connection.send(new net.minecraft.network.protocol.game
                                    .ClientboundSetEntityMotionPacket(sp));
                        }
                    }
                }
                if (attackTicks > 21 + FISSURE_STEPS + 18) {
                    fissureDir = null;
                    end(110);
                }
            }
            case ATK_ROAR -> {
                getNavigation().stop();
                setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
                if (attackTicks == 1 && target != null) {
                    roarCooldown = ROAR_EVERY;
                }
                if (attackTicks == ROAR_VOICE) {
                    // with the jaw, not after it
                    playSound(FFSounds.GOLEM_ROAR.get(), 6.0F, 1.0F);
                }
                if (attackTicks == ROAR_AT) {
                    playSound(FFSounds.ICE_CRACK.get(), 4.0F, 0.5F);           // the vault answers
                    shake(sl, 40);
                    thump(0.8F);
                }
                // THE DOME COMES DOWN: some thirty icicles in three seconds, on their shadows
                if (target != null && attackTicks >= RAIN_FROM && attackTicks <= RAIN_TO && attackTicks % 2 == 0) {
                    rain(sl, target);
                }
                if (attackTicks >= 20 && attackTicks <= 34 && attackTicks % 2 == 0) {
                    double r = (attackTicks - 18) * 0.9D;
                    for (int i = 0; i < 24; i++) {
                        double ang = Math.PI * 2.0D * i / 24.0D;
                        sl.sendParticles(FFParticles.SOUL_FROST.get(),
                                getX() + Math.cos(ang) * r, getY() + 1.0D,
                                getZ() + Math.sin(ang) * r, 1, 0.0D, 0.05D, 0.0D, 0.02D);
                    }
                    shake(sl, 8);
                }
                if (attackTicks > (target != null ? ROAR_DONE : 56 + REC_LEAP)) {
                    rainTaken.clear();
                    end(92);
                }
            }
            default -> end(40);
        }
    }

    /**
     * Walks it at whatever it is angry with, without a path.
     *
     * <p>Straight-line steering, capped at its own speed attribute, and only
     * while it is not committed to something else. It also turns at a fixed
     * rate rather than snapping, because the one thing a thing this heavy
     * must never do is pivot.
     */
    @Nullable
    private Vec3 strideFrom;

    private void tickStride() {
        // Measured first and unconditionally: whatever moved him - the stride
        // below, a charge, a shove - the animation has to know about it.
        if (!level().isClientSide) {
            // from where it stood last tick, not
            // from xo: a ridden one is moved by its rider's packets between ticks, after xo is taken, so x - xo
            // was always nothing and it glided along under them
            if (strideFrom == null) {
                strideFrom = position();
            }
            entityData.set(STRIDE, (float) Math.hypot(getX() - strideFrom.x, getZ() - strideFrom.z));
            strideFrom = position();
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || attack != ATK_NONE
                || charging > 0 || windup > 0 || emerging > 0 || greeting > 0 || ridden()) {
            walkClock = 0;
            return;
        }
        Vec3 to = target.position().subtract(position());
        double flat = Math.hypot(to.x, to.z);
        // It turns first and moves second, so it is always walking at where
        // it is looking - a mob that strafes sideways at a fixed facing is
        // the single loudest tell that something is on rails.
        float want = (float) (Math.atan2(to.z, to.x) * (180.0D / Math.PI)) - 90.0F;
        // the ceiling in tick() has the last word; this only has to ask for
        // less than the ceiling allows, or the two would fight each tick
        float turn = net.minecraft.util.Mth.clamp(
                net.minecraft.util.Mth.wrapDegrees(want - getYRot()),
                -TURN_SLOW, TURN_SLOW);
        setYRot(getYRot() + turn);
        yBodyRot = getYRot();
        getLookControl().setLookAt(target, 20.0F, 20.0F);

        if (flat < 4.5D) {
            walkClock = 0;
            return;                     // close enough; the attacks take over
        }
        // IT TURNS FIRST AND WALKS SECOND. Past this arc it is not facing
        // them enough to be walking at them, so it stands and comes round -
        // see facingOff. Forty degrees is wide enough that it does not stutter
        // to a halt every time they circle a little, and tight enough that it
        // is never travelling at something over its own shoulder.
        // ROUND THE PILLAR, NOT INTO IT: a
        // way round is found and walked - sidelong if need be, still turning to them as it goes
        Vec3 detourDir = steer(to);
        if (detourDir == null && facingOff(to) > WALK_ARC) {
            setDeltaMovement(getDeltaMovement().x * 0.5D, getDeltaMovement().y,
                             getDeltaMovement().z * 0.5D);
            walkClock = 0;
            return;
        }
        double speed = getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.6D;
        // ALONG ITS OWN NOSE, not along the line to the target. Those are the
        // same thing once it is facing them, and while it is still coming
        // round the difference is exactly the sideways shuffle this is here to
        // stop.
        Vec3 nose = detourDir != null ? detourDir : getForward();
        Vec3 step = new Vec3(nose.x * speed, getDeltaMovement().y, nose.z * speed);
        setDeltaMovement(step);
        // and it climbs whatever is in the way rather than stopping at a step
        if (horizontalCollision && onGround()) {
            setDeltaMovement(getDeltaMovement().x, 0.44D, getDeltaMovement().z);
        }

        // ================================================================
        // THE STEP LANDS WHEN THE FOOT LANDS.
        //
        // This used to fire on DISTANCE TRAVELLED - a sound every 2.6 blocks
        // of movement. That is not wrong so much as unrelated: the walk clip
        // runs on its own fixed 48-tick loop whatever speed he is moving at,
        // so the two clocks drift against each other continuously and the
        // thud lands wherever it happens to. Slowed, blocked, shoved or
        // pathing round a corner, it separates completely.
        //
        // A footfall is an ANIMATION event, so it is counted in animation
        // ticks. The clip plants the right foot at its own tick 12 and the
        // left at 36 - those are the two frames where sin() carries a leg to
        // full forward extension - so those are the two frames that make a
        // noise. Weight landing and the sound of weight landing now cannot
        // come apart, at any speed, which is the whole effect.
        // ================================================================
        walkClock++;
        int beat = walkClock % WALK_CYCLE;
        if (beat == WALK_PLANT_R || beat == WALK_PLANT_L) {
            playSound(FFSounds.GOLEM_STEP.get(), 3.2F, 0.78F + random.nextFloat() * 0.14F);
            // and about one step in three, the ice complains about carrying
            // him. Not every step: a sound on every footfall becomes a
            // rhythm, and a rhythm stops being a texture.
            if (random.nextInt(3) == 0) {
                playSound(FFSounds.GOLEM_CREAK.get(), 1.9F,
                        0.88F + random.nextFloat() * 0.22F);
            }
            if (level() instanceof ServerLevel sl) {
                // thrown from under the foot that actually landed, not from
                // between them - it is one foot's worth of weight, not two
                double side = beat == WALK_PLANT_R ? 0.8D : -0.8D;
                Vec3 out = new Vec3(-getLookAngle().z, 0.0D, getLookAngle().x)
                        .scale(side);
                sl.sendParticles(FFParticles.ICE_SHARD.get(),
                        getX() + out.x, getY() + 0.1D, getZ() + out.z,
                        10, 0.8D, 0.05D, 0.8D, 0.05D);
            }
            // ================================================================
            // AND THE FLOOR ANSWERS EVERY ONE OF THEM.
            //
            // Six blocks of ice walking across a stone room and the camera
            // sat perfectly still between blows - so its weight only existed
            // while it was attacking, and the rest of the time it read as a
            // large thing gliding. A horizon that does not move is the
            // loudest thing on screen; it has to move for the footsteps too.
            //
            // SMALL, and that is the whole design of it. The slam packs 1.0
            // and the charge's footfalls 0.42; a walking step is 0.11, which
            // the client's own falloff turns into a knock you feel under the
            // fight rather than something that interrupts aiming. It already
            // decays over IMPACT_TICKS and attenuates to nothing at 44
            // blocks, so distance does the rest.
            //
            // On the same frame as the sound, because they are one event: it
            // rides the clip's own foot-plant rather than a distance counter,
            // which is what keeps them together at any walking speed.
            thump(WALK_SHAKE);
        }
    }

    /**
     * How hard one walking footfall knocks the camera.
     *
     * <p>A ninth of a slam. See the note at the call site for why it is this
     * small and why it fires where it does.
     */
    // near the
    // charge's footfalls now, still falling away with distance as before
    private static final float WALK_SHAKE = 0.3F;

    /**
     * The walk clip's own clock, in ticks. See the footfall block above.
     *
     * <p>Reset rather than left running when he stops, so the first step of a
     * new walk is a step rather than whatever phase the counter happened to
     * be left on - a giant that starts moving silently and thumps half a
     * stride later is worse than one that never thumps at all.
     */
    private int walkClock;
    /** Must match animation.ice_monstrosity.walk: 2.4 seconds at 20 ticks. */
    private static final int WALK_CYCLE = 48;
    /** The two frames a foot reaches full forward extension and plants. */
    private static final int WALK_PLANT_R = 12;
    private static final int WALK_PLANT_L = 36;

    /**
     * THE RUN ENDS AND THEY KEEP GOING.
     *
     * <p>Let go rather than set down: everything it was carrying leaves along
     * the line of the charge with the speed it had. A scoop that simply
     * releases you at a standstill has taken you somewhere, which is half the
     * effect; one that throws you the rest of the way is the whole of it, and
     * it is also what three hundred tons stopping would actually do to
     * anything resting on its arm.
     */
    private void dropRiders() {
        if (carried.isEmpty() || !(level() instanceof ServerLevel sl)) {
            carried.clear();
            return;
        }
        for (java.util.UUID id : carried) {
            if (sl.getEntity(id) instanceof LivingEntity rider && rider.isAlive()) {
                rider.setDeltaMovement(chargeLine.x * 1.15D, 0.62D, chargeLine.z * 1.15D);
                rider.hurtMarked = true;
                strike(rider, 2.4F * CHARGE_SOFTER);
                if (rider instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.connection.send(new net.minecraft.network.protocol.game
                            .ClientboundSetEntityMotionPacket(sp));
                }
            }
        }
        carried.clear();
        playSound(FFSounds.GOLEM_SLAM.get(), 4.2F, 0.6F);
    }

    /** What it did last (no ranged attack twice running). */
    private int lastAttack = -1;

    private void begin(int which) {
        attack = which;
        if (which != ATK_ROAR) {
            lastAttack = which;
        }
        attackTicks = 0;
        struck.clear();
    }

    private void end(int cooldown) {
        // Anything that ends the attack clears the ball. Set in one branch and
        // cleared in another is how a boss ends up walking round the arena
        // with a crystal welded to its fist.
        if (!level().isClientSide) {
            entityData.set(ORB, 0.0F);
            // and the breath, for the same reason as the ball: an attack cut
            // short must not leave a cone pouring out of a golem that has
            // gone back to walking
            entityData.set(BREATH_T, 0);
        }
        attack = ATK_NONE;
        attackTicks = 0;
        attackCooldown = berserk() ? cooldown * 3 / 4 : cooldown;
    }

    /**
     * How many degrees off its own facing something is.
     *
     * <p>THE ONLY DIRECTION IT HAS IS FORWARD. Turning was slowed to a degree
     * and a half a tick and the stride was left aiming at the target, so for
     * the four seconds it takes to come round it walked SIDEWAYS - and at
     * worst backwards - at a thing behind it. Nothing that weighs this much
     * moves in a direction it is not pointing; it turns, and then it goes.
     *
     * <p>So travel is gated on this: face it first, then walk. The wait is not
     * dead time either - four seconds of a giant slowly bringing itself round
     * while you stand behind it is the reward for getting there.
     */
    private float facingOff(net.minecraft.world.phys.Vec3 toward) {
        if (toward.x * toward.x + toward.z * toward.z < 1.0E-6D) {
            return 0.0F;
        }
        float want = (float) (Math.atan2(toward.z, toward.x) * (180.0D / Math.PI)) - 90.0F;
        return Math.abs(net.minecraft.util.Mth.degreesDifference(getYRot(), want));
    }

    /**
     * Is it presently stuck in the last frame of something heavy?
     *
     * <p>The tick each attack's motion is finished on, per REC_*. Past that
     * point the animation is holding its final pose and the entity is doing
     * nothing except counting down, so this is the window the whole melee
     * exchange is built around.
     */
    private boolean recovering() {
        return switch (attack) {
            case ATK_SLAM -> attackTicks > 38;
            case ATK_STOMP -> attackTicks > 40;
            case ATK_SHARDS -> attackTicks > SHARD_FIRE + 26;
            case ATK_LEAP -> attackTicks > 56;
            case ATK_FISSURE -> attackTicks > 70;
            case ATK_BREATH -> attackTicks >= BREATH_END;
            case ATK_HAMMER -> attackTicks > 26;
            case ATK_STAGGER -> true;
            default -> false;
        };
    }

    /** Asks to face them square (the turn cap decides how much of that it gets this tick). */
    private void faceTowards(LivingEntity target) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        float yaw = (float) (net.minecraft.util.Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        setYHeadRot(yaw);
    }

    /** How far it may come round this tick. See TURN_SLOW. */
    private float turnCap() {
        if (ridden()) {
            return 10.0F;
        }
        if (isTamed() && getTarget() == null && attack == ATK_NONE) {
            return 5.0F;                                 // walking after its owner, not stalking a fight
        }
        if (attack == ATK_BREATH) {
            // square up while it draws - then it comes
            // round after them, slowly and all of a piece (07.10.2026: the breath follows the player)
            return attackTicks < 14 ? 8.0F : BREATH_TURN;
        }
        if (attack == ATK_JAVELIN) {
            // it comes round to them -
            // fast - before the arm goes over, and throws facing them
            return attackTicks < JAVELIN_LET_GO ? 14.0F : 0.0F;
        }
        if (attack == ATK_SHARDS) {
            // the bomb: it squares up to them first (quickly), then stands fast in its stance to the shot
            //
            return attackTicks < 9 ? 6.0F : 0.0F;
        }
        return (windup > 0 || charging > 0) ? TURN_CHARGE : TURN_SLOW;
    }

    /**
     * EVERY POINT OF DAMAGE IT DEALS GOES THROUGH HERE.
     *
     * <p>It went through six separate calls to VelkharEntity.strikeFor before
     * this, which was fine while damage was only damage. It stops being fine
     * the moment anything has to happen on EVERY hit - and the berserk state
     * is exactly that. Six call sites is six chances to forget one, and the
     * one you forget is the attack nobody reports because it looks the same.
     */
    /**
     * One tick of breath: who is in the cone, and what the cold does to them.
     *
     * <p>The cone starts at the lip line - two and a half blocks out and three
     * and a half up, off the model - and points along the facing tilted down
     * BREATH_TILT, the same angle the head is posed at so the geometry on
     * screen and the volume that hits are the same shape. Tested against the
     * middle of each victim, so a player is in it when the cone covers their
     * body, not only when it clips a boot. Right under the maw the angle test
     * stops meaning anything, so anybody in front within two blocks counts.
     */
    private void breathe(ServerLevel sl) {
        Vec3 look = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 flat = new Vec3(look.x, 0.0D, look.z).normalize();
        Vec3 mouth = position().add(flat.scale(2.6D)).add(0.0D, 3.5D, 0.0D);
        double tilt = Math.toRadians(BREATH_TILT);
        Vec3 axis = new Vec3(flat.x * Math.cos(tilt), -Math.sin(tilt), flat.z * Math.cos(tilt));
        double cosHalf = Math.cos(Math.toRadians(BREATH_HALF));
        java.util.Set<Integer> inside = new java.util.HashSet<>();
        for (LivingEntity v : victims(BREATH_REACH + 3.0D)) {
            Vec3 to = v.position().add(0.0D, v.getBbHeight() * 0.5D, 0.0D).subtract(mouth);
            double dist = to.length();
            if (dist > BREATH_REACH) {
                continue;
            }
            boolean in = dist < 2.2D
                    ? flat.x * to.x + flat.z * to.z > 0.0D
                    : to.normalize().dot(axis) >= cosHalf;
            if (!in) {
                continue;
            }
            inside.add(v.getId());
            int c = chill.merge(v.getId(), 1, Integer::sum);
            v.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30,
                    Math.min(4, c / 12)), this);
            v.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    com.jastkub.frozenfortress.registry.FFEffects.FROSTBITE, 60, 0), this);
            // vanilla's own freezing: the frost creeps in round the screen
            // edge as this climbs, which is the player's gauge for the clock
            v.setTicksFrozen(Math.min(240, v.getTicksFrozen() + 6));
            if (c % 10 == 0) {
                strike(v, BREATH_DAMAGE);
            }
            if (c >= BREATH_FREEZE_AT && frozenByBreath.add(v.getId())) {
                freezeSolid(sl, v);
            }
        }
        // out of it, it thaws - twice as fast as it gathered
        chill.replaceAll((id, c) -> inside.contains(id) ? c : Math.max(0, c - 2));
    }

    /**
     * Frozen solid: the hunter orbs' detonation, word for word, because the
     * brief was that this mechanic already exists and it should be the same
     * one - slowness VI, no jumping, frostbite, fully frozen, and a prison of
     * ice round them to break out of.
     */
    private void freezeSolid(ServerLevel sl, LivingEntity v) {
        v.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 80, 6), this);
        v.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.JUMP, 80, 128), this);
        v.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.jastkub.frozenfortress.registry.FFEffects.FROSTBITE, 120, 1), this);
        v.setTicksFrozen(260);
        sl.addFreshEntity(new com.jastkub.frozenfortress.entity.boss.IcePrisonEntity(
                level(), this, v));
        sl.playSound(null, v.blockPosition(), FFSounds.ICE_PRISON.get(),
                net.minecraft.sounds.SoundSource.HOSTILE, 2.4F, 0.8F);
    }

    /** EVERY BLOW AT HALF. The numbers at
     *  the call sites are the shape of the fight - which blow is heavy, which is a graze - and
     *  this is its weight; the king's funnel turns each "heart" into sixteen raw. */
    private static final float BLOW_SCALE = 0.5F;

    private void strike(LivingEntity victim, float damage) {
        damage *= BLOW_SCALE;
        com.jastkub.frozenfortress.entity.boss.VelkharEntity
                .strikeFor(this, victim, berserk() ? damage * 1.25F : damage);
        if (berserk()) {
            // THE ICE ANSWER TO CATCHING FIRE. The reference's phase two sets
            // its attacks alight; a thing made of glacier does the opposite
            // and the effect is already in the mod. Six seconds, stacking
            // toward nothing - it is pressure, not a kill timer.
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    com.jastkub.frozenfortress.registry.FFEffects.FROSTBITE,
                    120, 0), this);
        }
    }

    /**
     * BERSERK, at a quarter of its health.
     *
     * <p>Read straight off the health rather than kept in a flag: there is
     * nothing to desynchronise, nothing to forget to clear, and it comes back
     * on its own if anything ever heals it past the line - which the drain
     * loop will do.
     */
    public boolean berserk() {
        // the second phase begins when the pauldrons come off (FRACTURE, at half its health)
        return entityData.get(FRACTURED);
    }

    /**
     * WHAT COMES OFF THE TROUGH.
     *
     * <p>There is a basin of something freezing sunk into the top of it, and a
     * basin that does nothing is scenery. This is the cheapest possible answer
     * and the right one: cold air is HEAVY, so the vapour coming off it does
     * not plume upward like steam - it lifts barely at all and then spills
     * over the rim and falls down the creature's back. A few motes a second,
     * drifting sideways and sinking.
     *
     * <p>Every fifth tick, and never more than three at once. This runs for as
     * long as the colossus is alive and on screen; a per-tick emitter here is
     * a thousand particles a minute for something nobody is looking directly
     * at, and the fight already spends its particle budget on the attacks.
     */
    private void vent() {
        if (tickCount % 3 != 0 || !(level() instanceof ServerLevel sl)) {
            return;
        }
        // the basin sits at 100 of 104 model units - call it 6.2 blocks - and
        // it runs most of the width of the torso
        Vec3 out = getForward();
        Vec3 side = new Vec3(-out.z, 0.0D, out.x);
        for (int i = 0; i < 3 + random.nextInt(3); i++) {
            double across = (random.nextDouble() - 0.5D) * 3.0D;
            double along = (random.nextDouble() - 0.5D) * 1.2D;
            Vec3 at = position()
                    .add(side.scale(across))
                    .add(out.scale(along))
                    .add(0.0D, 6.2D, 0.0D);
            sl.sendParticles(FFParticles.SOUL_FROST.get(),
                    at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        // and one that has already spilled over and is falling
        if (random.nextInt(3) == 0) {
            Vec3 at = position().add(side.scale((random.nextDouble() - 0.5D) * 3.4D))
                    .add(out.scale(-1.2D)).add(0.0D, 5.6D, 0.0D);
            sl.sendParticles(FFParticles.FROST_SWIRL.get(),
                    at.x, at.y, at.z, 0, 0.0D, -0.04D, 0.0D, 1.0D);
        }
    }

    /** Everything within a radius, that is not it and not its maker. */
    private java.util.List<LivingEntity> victims(double radius) {
        return level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(radius),
                e -> e != this && e.isAlive() && !(e instanceof HollowGolemEntity) && !mySide(e)
                        && !(isTamed() && e.isPassengerOfSameVehicle(this))
                        && !(e instanceof com.jastkub.frozenfortress.entity.boss.VelkharEntity));
    }

    private void kick(LivingEntity v) {
        if (v instanceof ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game
                    .ClientboundSetEntityMotionPacket(sp));
        }
    }

    private void shake(ServerLevel sl, int strength) {
        sl.sendParticles(FFParticles.ICE_SHARD.get(),
                getX(), getY() + 0.2D, getZ(), strength, 3.5D, 0.2D, 3.5D, 0.06D);
    }

    /** One impact: floor thrown up, everything near it hurt and launched. */
    private void boom(ServerLevel sl, Vec3 at, double radius, float shakeR,
                      float hearts, double lift) {
        sl.sendParticles(FFParticles.SHOCKWAVE.get(), at.x, at.y + 0.2D, at.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        sl.sendParticles(FFParticles.ICE_SHARD.get(), at.x, at.y + 0.3D, at.z,
                70, radius * 0.4D, 0.4D, radius * 0.4D, 0.3D);
        for (LivingEntity v : victims(radius)) {
            if (v.distanceToSqr(at) > radius * radius) {
                continue;
            }
            if (!struck.add(v.getUUID())) {
                continue;
            }
            strike(v, hearts);
            Vec3 away = v.position().subtract(at).normalize();
            // SET, never push. push() adds to whatever they were already
            // doing, which is how a shove becomes a launch when two of these
            // land in the same second.
            v.setDeltaMovement(away.x * 0.75D, lift, away.z * 0.75D);
            v.hurtMarked = true;
            kick(v);
        }
        shake(sl, (int) shakeR * 6);

        // AND THE WEIGHT, ON EVERY BLOW IT LANDS.
        //
        // This is the one place all six attacks pass through, so putting the
        // heaviness here rather than in each handler means the slam, the
        // sweep, the stomp and the hurl all get it and cannot drift apart
        // later. Two halves, and they are different things: the FLOOR comes
        // up off the grid, which is what the player sees, and the CAMERA gets
        // hit once and decays, which is what the player feels. It had neither
        // - it threw particles into a still screen, so the biggest thing in
        // the fight landed lighter than a zombie.
        //
        // Scaled off the reach of the blow, so a stomp shakes the room and a
        // sweep taps it. Nothing is scaled off the damage: how hard something
        // hits the FLOOR is not the same question as how hard it hits you,
        // and tying them together is what makes big slow attacks feel weak
        // whenever they are balanced down.
        heave(sl, at, Math.min(6.0D, radius * 0.8D), 0.8D + radius * 0.06D);
        thump(net.minecraft.util.Mth.clamp(0.45F + (float) radius * 0.08F, 0.45F, 1.0F));
    }

    /**
     * IT DIES SLOWLY, AND A LITTLE FOOLISHLY: the clip plays to its end - the squeak of a roar, the sit
     * on its backside, the fall like a tree - with the floor shaking where it lands; then it bursts.
     */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        if (deathTime == 1) {
            entityData.set(ANIM, A_DEATH);
        }
        switch (deathTime) {
            case 4 -> playSound(FFSounds.GOLEM_HURT.get(), 4.0F, 0.6F);
            case 30 -> {
                playSound(FFSounds.GOLEM_ROAR.get(), 2.6F, 1.95F);          // the roar comes out a squeak
            }
            case 48 -> playSound(FFSounds.GOLEM_CREAK.get(), 2.0F, 1.6F);
            case 63 -> {                                                          // down on its backside
                playSound(FFSounds.GOLEM_SLAM.get(), 4.6F, 0.6F);
                thump(0.9F);
                heave(sl, position(), 4.0D, 0.9D);
            }
            case 82 -> playSound(FFSounds.GOLEM_CREAK.get(), 2.2F, 1.3F);
            case 98 -> {                                                          // over, like a felled tree
                playSound(FFSounds.GOLEM_SLAM.get(), 5.0F, 0.45F);
                playSound(FFSounds.ICE_IMPACT.get(), 4.0F, 0.5F);
                thump(1.0F);
                heave(sl, position().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(-2.0D)), 5.0D, 1.1D);
            }
            case 116 -> {                                                         // the hand flops
                playSound(FFSounds.GOLEM_STEP.get(), 3.0F, 0.9F);
                thump(0.35F);
            }
            default -> {
            }
        }
        if (deathTime >= DEATH_TICKS && !isRemoved()) {
            sl.playSound(null, blockPosition(), FFSounds.GOLEM_DEATH.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
            sl.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 4.0F, 0.5F);
            sl.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 1.4D, getZ(), 220, 2.4D, 1.0D, 2.4D, 0.35D);
            // and its ice flies apart (the statues' splinters, without the sting)
            for (int i = 0; i < 18; i++) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                sl.addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.IceBombEntity.IceFragmentEntity(sl, null,
                        position().add(Math.cos(a) * 1.5D, 1.2D + random.nextDouble() * 1.5D, Math.sin(a) * 1.5D),
                        new Vec3(Math.cos(a) * 0.4D, 0.35D + random.nextDouble() * 0.3D, Math.sin(a) * 0.4D))
                        .shrapnel(0.0001F));
            }
            sl.broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    private void collapse() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY() + getBbHeight() * 0.5D, getZ(),
                    120, 1.2D, 2.0D, 1.2D, 0.35D);
            serverLevel.playSound(null, blockPosition(), FFSounds.GOLEM_DEATH.get(),
                    SoundSource.HOSTILE, 3.6F, 1.0F);
        }
        discard();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return random.nextInt(3) == 0
                ? FFSounds.GOLEM_IDLE.get() : FFSounds.GOLEM_CREAK.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        // Half the time it is the shell cracking rather than the thing
        // inside it reacting - which is the more honest of the two, since
        // there is nothing inside it.
        return random.nextBoolean()
                ? FFSounds.GOLEM_SPLIT.get() : FFSounds.GOLEM_HURT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return FFSounds.GOLEM_DEATH.get();
    }

    /** Rarely, and quietly. A grinding slab is atmosphere, not a monster
     *  announcing itself every four seconds. */
    @Override
    public int getAmbientSoundInterval() {
        return 180;
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }

    /** Never pushed, never dragged - six blocks of ice does not get shoved. */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity other) {
    }

    /** The prison it guards, when it is the prison's and not the king's. */
    private net.minecraft.core.BlockPos prisonHeart;

    /**
     * THE AIR BENDS WHEN IT ROARS: a stream of churned air out of its maw and `rings` rings thrown out to `reach`,
     * for `life` ticks, at `power` (1: the great roars - its greeting, its ROAR, its fracture). RoarWarpEntity.
     */
    /** its greeting's
     *  alone now - out of the statue, or out of the floor. */
    private void warp(float power, float reach, int rings, int life) {
        if (level().isClientSide) {
            return;
        }
        if (!isAddedToLevel()) {
            pendingWarp = new float[]{power, reach, rings, life};    // (out of its statue: before it is in the world)
            return;
        }
        com.jastkub.frozenfortress.entity.RoarWarpEntity.roar(this, life, power, reach, rings);
    }

    @javax.annotation.Nullable
    private float[] pendingWarp;

    // ---- where its maw is, this frame (the client's: HollowGolemRenderer from the head bone; RoarWarpFx reads it)
    public net.minecraft.world.phys.Vec3 clientMaw;
    public net.minecraft.world.phys.Vec3 clientMawDir;
    public int clientMawTick = -1;

    public void setPrisonHeart(net.minecraft.core.BlockPos pos) {
        this.prisonHeart = pos;
    }

    /**
     * OUT OF THE STATUE, NOT OUT OF THE FLOOR. The prison's guardian was
     * standing there all along under its ice; when the ice breaks there is no
     * climb to watch - it is already up, and the first thing it does is roar.
     * Call before it is added to the world, so the client is told ROAR, not
     * EMERGE, from its very first frame.
     */
    // the roar's file is pitched where it is heard; every call here used
    // to drop it another 2-9 semitones (pitch 0.6-0.9), back down into a bass nobody's speakers carry
    public void wakeFromStatue() {
        emerging = 0;
        greeting = GREET_TICKS;
        entityData.set(ANIM, A_ROAR);
        //
        // it roared in the same tick as the shell burst - under the shatter, the shockwave and the glass, three loud
        // sounds over one - and before its jaw had opened. It roars with the jaw now (greetVoice), on its own.
        greetVoice = true;
        if (level() instanceof ServerLevel serverLevel) {
            shake(serverLevel, 40);
            warp(1.0F, 16.0F, 4, GREET_TICKS);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (prisonHeart != null && level() instanceof ServerLevel heartLevel
                && heartLevel.getBlockEntity(prisonHeart)
                        instanceof com.jastkub.frozenfortress.block.entity.PrisonHeartBlockEntity heart) {
            heart.onGuardianDeath(heartLevel);
        }
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY() + getBbHeight() * 0.5D, getZ(),
                    140, 1.2D, 2.2D, 1.2D, 0.4D);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        if (arenaCentre != null) {
            tag.putDouble("ArenaX", arenaCentre.x);
            tag.putDouble("ArenaY", arenaCentre.y);
            tag.putDouble("ArenaZ", arenaCentre.z);
        }
        if (isTamed() && tamer != null) {
            tag.putUUID("Tamer", tamer);
            tag.putBoolean("Saddled", isSaddled());
            tag.putBoolean("Staying", staying);
        }
        super.addAdditionalSaveData(tag);
        tag.putInt("Emerging", emerging);
        tag.putInt("Greeting", greeting);
        tag.putInt("Windup", windup);
        tag.putInt("Charging", charging);
        tag.putBoolean("Fractured", entityData.get(FRACTURED));
        tag.putBoolean("FractureDone", fractureDone);
        if (prisonHeart != null) {
            tag.putLong("PrisonHeart", prisonHeart.asLong());
        }
        net.minecraft.nbt.ListTag fl = new net.minecraft.nbt.ListTag();
        for (Object[] f : fallen) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", ((net.minecraft.core.BlockPos) f[0]).asLong());
            c.put("State", net.minecraft.nbt.NbtUtils.writeBlockState((net.minecraft.world.level.block.state.BlockState) f[1]));
            fl.add(c);
        }
        tag.put("FallenIcicles", fl);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("ArenaX")) {
            arenaCentre = new Vec3(tag.getDouble("ArenaX"), tag.getDouble("ArenaY"), tag.getDouble("ArenaZ"));
        }
        if (tag.hasUUID("Tamer")) {
            tamer = tag.getUUID("Tamer");
            entityData.set(TAMED, true);
            entityData.set(SADDLED, tag.getBoolean("Saddled"));
            staying = tag.getBoolean("Staying");
        }
        super.readAdditionalSaveData(tag);
        emerging = tag.getInt("Emerging");
        greeting = tag.getInt("Greeting");
        windup = tag.getInt("Windup");
        charging = tag.getInt("Charging");
        entityData.set(FRACTURED, tag.getBoolean("Fractured"));
        fractureDone = tag.getBoolean("FractureDone");
        if (tag.contains("PrisonHeart")) {
            prisonHeart = net.minecraft.core.BlockPos.of(tag.getLong("PrisonHeart"));
        }
        fallen.clear();
        for (net.minecraft.nbt.Tag t : tag.getList("FallenIcicles", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            fallen.add(new Object[]{net.minecraft.core.BlockPos.of(c.getLong("Pos")),
                    net.minecraft.nbt.NbtUtils.readBlockState(level().holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                            c.getCompound("State")), 0});
        }
    }

    public boolean isWindingUp() {
        // SIDE-AWARE, because `windup` is a server field and the client used
        // to read the value it was born with. The server keeps its exact
        // count; the client asks the synced state.
        return level().isClientSide ? entityData.get(ANIM) == A_WINDUP : windup > 0;
    }

    public boolean isCharging() {
        return level().isClientSide ? entityData.get(ANIM) == A_CHARGE : charging > 0;
    }

    /** True while it is still hauling itself out OR bellowing the greeting -
     *  the whole arrival, which is what the summoning cutscene holds for. */
    public boolean isArriving() {
        return level().isClientSide
                ? (entityData.get(ANIM) == A_EMERGE || entityData.get(ANIM) == A_ROAR)
                : (emerging > 0 || greeting > 0);
    }

    public boolean isEmerging() {
        // the boss bar reads this one, and it was permanently true on the
        // client - so the bar announced him as still climbing out for the
        // whole fight
        return level().isClientSide ? entityData.get(ANIM) == A_EMERGE : emerging > 0;
    }

    /** Client-side: is he covering ground? Drives the walk animation. */
    public boolean isStriding() {
        return entityData.get(STRIDE) > 0.012F;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ORB, 0.0F);
        builder.define(BREATH_T, 0);
        builder.define(STRIDE, 0.0F);
        builder.define(ANIM, A_EMERGE);
        builder.define(IMPACT, 0);
        builder.define(FRACTURED, false);
        builder.define(TAMED, false);
        builder.define(SADDLED, false);
    }

    /**
     * The last blow it landed, packed as a counter and an amplitude.
     *
     * <p>Same trick the king uses, and it is here for the same reason: the
     * colossus had no camera shake AT ALL. Every one of its attacks threw
     * particles and played a heavy sound into a screen that sat perfectly
     * still, so a six-block thing putting both fists through the floor read
     * exactly as light as a zombie swinging - the report that you cannot feel
     * its weight is a report about the camera, not about the animation.
     *
     * <p>The counter is what makes it an EVENT: the client watches for the
     * number to change and decays the shake itself, so one hit is one crack
     * rather than a rumble that lasts as long as the state does.
     */
    public int impactPacked() {
        return entityData.get(IMPACT);
    }

    /** One blow landed, power 0..1. Server only. */
    private void thump(float power) {
        if (level().isClientSide) {
            return;
        }
        impactSeq = (impactSeq + 1) & 0x7FFFFF;
        int p = net.minecraft.util.Mth.clamp(Math.round(power * 255.0F), 1, 255);
        entityData.set(IMPACT, (impactSeq << 8) | p);
    }

    /**
     * The floor thrown STRAIGHT UP off the world grid.
     *
     * <p>Deliberately vertical and deliberately on block centres. Debris
     * kicked outward in a cone reads as an explosion happening on top of the
     * ground; a grid of real floor cubes rising together and dropping back
     * into their own holes reads as the GROUND ITSELF being hit, which is the
     * difference between a big attack and a heavy one.
     */
    private void heave(ServerLevel sl, Vec3 centre, double radius, double force) {
        net.minecraft.core.BlockPos under =
                net.minecraft.core.BlockPos.containing(centre).below();
        net.minecraft.world.level.block.state.BlockState floor = level().getBlockState(under);
        if (floor.isAir()) {
            return;
        }
        int placed = 0;
        int span = (int) Math.ceil(radius);
        for (int dx = -span; dx <= span && placed < 26; dx++) {
            for (int dz = -span; dz <= span && placed < 26; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius || random.nextFloat() > 0.5F) {
                    continue;
                }
                net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(
                        centre.x + dx, centre.y - 0.2D, centre.z + dz);
                net.minecraft.world.level.block.state.BlockState state = level().getBlockState(at);
                if (state.isAir()) {
                    continue;
                }
                // Falls off from the impact, so the ring nearest the fist
                // jumps highest and the wave has a crest.
                double falloff = 1.0D - (d / Math.max(1.0D, radius)) * 0.65D;
                level().addFreshEntity(new com.jastkub.frozenfortress.entity.effect.FallingDebrisEntity(
                        level(), state,
                        at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D,
                        0.0D, (0.34D + random.nextDouble() * 0.2D) * falloff * force, 0.0D,
                        30 + random.nextInt(18)));
                placed++;
            }
        }
        sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.BLOCK, floor),
                centre.x, centre.y + 0.2D, centre.z, 34, 1.0D, 0.1D, 1.0D, 0.7D);
    }

    /** Works out what he is doing and tells the client. Server only. */
    private void publishAnimation() {
        int want;
        if (isDeadOrDying()) {
            want = A_DEATH;
        } else if (emerging > 0) {
            want = A_EMERGE;
        } else if (greeting > 0) {
            want = A_ROAR;
        } else if (charging > 0) {
            want = A_CHARGE;
        } else if (windup > 0) {
            want = A_WINDUP;
        } else if (slamming > 0) {
            want = A_SLAM;
        } else {
            want = switch (attack) {
                case ATK_SLAM -> A_SLAM;
                case ATK_SWEEP -> A_SWEEP;
                case ATK_STOMP -> A_STOMP;
                case ATK_FISSURE -> A_FISSURE;
                case ATK_LEAP -> A_LEAP;
                case ATK_SHARDS -> A_SHARDS;
                case ATK_SWIPE -> A_SWIPE;
                case ATK_DRAIN -> A_DRAIN;
                case ATK_BREATH -> A_BREATH;
                // the volley is the same throwing motion, run five times over;
                // the breath is the roar's open maw, which is exactly the pose
                // a cone of cold should come out of
                case ATK_ROAR -> A_ROAR;
                case ATK_HAMMER -> A_HAMMER;
                case ATK_COMBO -> berserk() ? A_COMBO3 : A_COMBO;
                case ATK_STOMP2 -> A_STOMP2;
                case ATK_JAVELIN -> A_JAVELIN;
                case ATK_GEYSER -> A_GEYSER;
                case ATK_AVALANCHE -> A_AVALANCHE;
                case ATK_GRAB -> A_GRAB;
                case ATK_STAGGER -> A_STAGGER;
                case ATK_FRACTURE -> A_FRACTURE;
                default -> isStriding() ? A_WALK : A_IDLE;
            };
        }
        if (entityData.get(ANIM) != want) {
            entityData.set(ANIM, want);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!isTamed()) {
            bossEvent.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    /** Untouchable while it is still coming out of the floor - and by the
     *  king who called it up, permanently. */
    // ================================================================
    // THE CEILINGS. Same idea as the king's, and for the same reason: with
    // armour 30 and toughness 12 he already eats most of a hit, so a limit
    // applied BEFORE that formula would multiply with it and land somewhere
    // nobody chose. These are measured in damage that actually lands.
    // ================================================================
    /** Most one blow may take off him. */
    private static final float HIT_CAP = 18.0F;
    /** And the most any one second may, however it arrives. */
    private static final float DPS_CAP = 26.0F;
    private static final int DPS_WINDOW = 20;

    private long capWindowAt;
    private float capWindowDamage;

    /**
     * THE CEILING, APPLIED WHERE THE DAMAGE IS FINAL.
     *
     * <p>Capping inside hurt() does not work, and neither does back-solving
     * for an amount that will survive armour: the vanilla formula is not
     * linear, so the fraction that gets through an eight-hundred point hit
     * (76% against armour 30) is nothing like the fraction that gets through
     * the twenty-point hit computed from it (20%). Both attempts landed on
     * four damage, which is exactly what was reported.
     *
     * <p>This is the last thing vanilla does to a hit before it comes off the
     * health bar, so fifteen means fifteen.
     */
    @Override
    protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
        float landed = super.getDamageAfterMagicAbsorb(source, amount);
        if (level().isClientSide
                || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return landed;
        }
        long now = level().getGameTime();
        if (now - capWindowAt >= DPS_WINDOW) {
            capWindowAt = now;
            capWindowDamage = 0.0F;
        }
        // ON ITS KNEE, ITS HEART OPEN: everything takes double, and the ceilings with it
        float open = attack == ATK_STAGGER ? 1.6F : 1.0F;      // (it was 2)
        landed *= open;
        // (a party has its health grown, and its second grown the same - PartyScaling)
        float allowed = Math.min(HIT_CAP * open, Math.max(0.0F,
                DPS_CAP * open * com.jastkub.frozenfortress.event.PartyScaling.healthFactor(this) - capWindowDamage));
        landed = Math.min(landed, allowed);
        capWindowDamage += landed;
        // nothing done from inside its fist breaks its grip
        return landed;
    }


    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (isTamed() && source.getEntity() instanceof Player p && (tamer == null || tamer.equals(p.getUUID()) || isPassengerOfSameVehicle(p))) {
            return true;                                 // its owner's blows (and its rider's) do not reach it
        }
        if (emerging > 0) {
            return true;
        }
        if (isKingsWork(source.getEntity()) || isKingsWork(source.getDirectEntity())) {
            return true;
        }
        // AND COLD CANNOT TOUCH IT AT ALL.
        //
        // isKingsWork asks who dealt the damage, and half of what the king
        // puts in the air does not answer that question: the tornado hurts
        // with a plain freeze that carries no attacker, frostbite ticks from
        // the EFFECT rather than from him, and every owner-less projectile
        // falls back to magic. So the colossus was being ground down by its
        // own summoner through three separate doors that all looked like
        // "nobody did this", and the report is exactly that - he kills it
        // with beams and other attacks.
        //
        // Blanket freeze immunity closes all of them at once and is the only
        // reading that makes sense anyway: it is a mountain of ice that was
        // called up out of a frozen floor by the king of winter. Cold is what
        // it is made of.
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    /** Frostbite is his, and it is cold. Neither half of that applies here. */
    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        if (effect.is(com.jastkub.frozenfortress.registry.FFEffects.FROSTBITE)) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    /** Velkhar, anything wearing his face, and anything he threw. */
    private static boolean isKingsWork(net.minecraft.world.entity.Entity who) {
        if (who == null) {
            return false;
        }
        if (who instanceof com.jastkub.frozenfortress.entity.boss.VelkharEntity
                || who instanceof com.jastkub.frozenfortress.entity.boss.VelkharCloneEntity
                || who instanceof FrostServantEntity
                || who instanceof HollowGolemEntity) {
            return true;
        }
        if (who instanceof net.minecraft.world.entity.projectile.Projectile p) {
            return isKingsWork(p.getOwner());
        }
        return false;
    }

    /** The melee is a two-fisted slam, and it needs its own animation. */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        slamming = 30;
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY() + 0.2D, getZ(), 30, 1.2D, 0.2D, 1.2D, 0.2D);
            serverLevel.playSound(null, blockPosition(), FFSounds.GOLEM_SLAM.get(),
                    SoundSource.HOSTILE, 3.2F, 1.1F);
        }
        return super.doHurtTarget(target);
    }

    /**
     * Decides when to charge, and does nothing else.
     *
     * <p>Its own goal rather than a branch in tick() so the melee goal stops
     * steering while it runs - two things writing navigation in the same tick
     * is how a charge turns into a shuffle.
     */
    private class ChargeGoal extends Goal {
        @Override
        public boolean canUse() {
            if (ridden() || (isTamed() && getTarget() == null)) {
                return false;
            }
            LivingEntity target = getTarget();
            if (target == null || emerging > 0 || chargeCooldown > 0
                    || windup > 0 || charging > 0 || attack != ATK_NONE) {
                return false;
            }
            double d = distanceTo(target);
            if (!(d > 5.0D && d < 22.0D && hasLineOfSight(target))) {
                return false;
            }
            Vec3 to = new Vec3(target.getX() - getX(), 0.0D, target.getZ() - getZ());
            return to.lengthSqr() < 1.0E-4D || !pillarAhead(to.normalize(), Math.min(d, CHARGE_MIN_RUN + 2.0D));
        }

        @Override
        public boolean canContinueToUse() {
            return windup > 0 || charging > 0;
        }

        @Override
        public void start() {
            windup = WINDUP_TICKS;
            chargeCooldown = 200;
            getNavigation().stop();
            playSound(FFSounds.GOLEM_STOMP.get(), 2.8F, 1.12F);
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                        getX(), getY() + getBbHeight() * 0.6D, getZ(),
                        40, 0.9D, 1.2D, 0.9D, 0.03D);
            }
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (windup > 0) {
                getNavigation().stop();
                setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
                if (target != null) {
                    getLookControl().setLookAt(target, 30.0F, 30.0F);
                }
            }
        }
    }

    // ---- GeckoLib -------------------------------------------------------
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::mainAnim));
    }

    /**
     * HOW FREELY ITS HEAD FOLLOWS ITS EYES: the model turns the head toward where it
     * looks, on top of whatever the clip does with it (HollowGolemRenderer.GolemModel) - all the way while it walks or
     * stands, a little in its blows (the clip has the head then), not at all while its maw breathes or roars (the
     * breath and the bent air leave from where the maw is), while it comes up out of the ice, tears its pauldrons off,
     * reels or dies, or while someone rides it.
     */
    public float headFreedom() {
        if (isVehicle()) {
            return 0.0F;
        }
        return switch (entityData.get(ANIM)) {
            case A_IDLE, A_WALK -> 1.0F;
            case A_BREATH, A_ROAR, A_EMERGE, A_FRACTURE, A_STAGGER, A_DEATH, A_DRAIN -> 0.0F;
            default -> 0.35F;
        };
    }

    private PlayState mainAnim(AnimationState<HollowGolemEntity> state) {
        // ONE READ, and it is of a value the server actually maintains. The
        // previous version branched on five fields that only exist on the
        // server, so the client held the first branch forever.
        return state.setAndContinue(switch (entityData.get(ANIM)) {
            case A_EMERGE -> EMERGE;
            case A_CHARGE -> CHARGE;
            case A_WINDUP -> WINDUP;
            case A_SLAM -> SLAM;
            case A_SWEEP -> SWEEP;
            case A_STOMP -> STOMP;
            case A_FISSURE -> FISSURE;
            case A_LEAP -> LEAP;
            case A_SHARDS -> SHARDS;
            case A_SWIPE -> SWIPE;
            case A_DRAIN -> DRAIN;
            case A_BREATH -> BREATH_CLIP;
            case A_ROAR -> ROAR;
            case A_HAMMER -> HAMMER;
            case A_COMBO -> COMBO;
            case A_COMBO3 -> COMBO3;
            case A_STOMP2 -> STOMP2;
            case A_JAVELIN -> JAVELIN;
            case A_GEYSER -> GEYSER;
            case A_AVALANCHE -> AVALANCHE;
            case A_GRAB -> GRAB;
            case A_STAGGER -> STAGGER;
            case A_FRACTURE -> FRACTURE;
            case A_DEATH -> DEATH_ANIM;
            case A_WALK -> WALK;
            default -> IDLE;
        });
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
