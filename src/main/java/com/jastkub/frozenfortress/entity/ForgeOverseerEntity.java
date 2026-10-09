package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * NADZORCA KUZNI - THE FORGE OVERSEER, the miniboss of the Forge (Kuznia) where King Velkhar's armour was made. A huge armoured taskmaster still at his post: a great hammer across his right fist,
 * smith's tongs across his left, the forge's cold fire behind the grate of his helm and the grate in his chest, and his
 * own quench tub slung on his back. He keeps the smith locked in the cell off the hall's north side: when he falls the
 * smith is free (ForgeOverseerSmithHook).
 *
 * <p>His attacks - each told before it lands, each a thing with a body (tools/gen_forge_overseer.py):
 * <ol>
 *   <li>SLAM (Cios Mlotem) - the hammer up over his shoulder and back over his head (16 ticks of tell), then down on the
 *   floor 2.4 blocks before him on 19: there he crushes, and a ring of heaved floor under a crest of frost runs out from
 *   it to seven blocks (ForgeOverseerShockwaveEntity; nine in his second half). Jump the crest.</li>
 *   <li>SWEEP (Zamach) - he turns away to his left and the hammer goes round before him in a flat arc on 15: everything
 *   in front of him and to his sides within four and a half blocks is struck aside. Behind him is safe.</li>
 *   <li>TONGS (Chwyt Kleszczami) - the tongs up high, the jaws clacking open twice; they come down and SNAP shut on 14
 *   on whoever is before him within 3.6 blocks. Caught, you are hauled to his anvil point and the hammer comes down on you
 *   on 32 - unless you hurt him by ten while he holds you: then he lets go and reels. If they snap on air he stumbles
 *   (grab_miss, 30 ticks open to a beating).</li>
 *   <li>SLAG (Zuzel) - he scoops a lump of frozen slag off the floor with the tongs and strikes it on 22: a fan of slag
 *   shards flies at you (ForgeOverseerSlagEntity).</li>
 * </ol>
 * <b>THE QUENCH (Hartowanie).</b> Every twenty seconds or so he quenches the hammer: he walks to a trough of the hall (a
 * water cauldron within sixteen blocks of his home), stands over it and drives the hammer head into the water on 16 -
 * steam bursts up (ForgeOverseerSteamEntity) and the trough loses a level of water. With no trough he takes his own tub
 * off his back with the tongs, sets it down before him and quenches in that (quench_carried, a longer opening). For ten
 * seconds after, his hammer is rimed and every blow of it - the slam, its ring, the sweep, the blow on whoever the tongs
 * hold - FREEZES whoever it lands on (ForgeOverseerRimeEntity, a crust of quench-frost that three blows break). The
 * quench is the window: he stands bent over the water for over a second.
 *
 * <p><b>HIS PLATE COMES OFF.</b> Six plates - two pauldrons, the breastplate's two halves, two greaves - each wears under
 * the blows that land on it (a heavy blow, seven or more, wears it whole; a light one at less than half) and goes when
 * it has taken twenty: the bone is hidden and the plate falls to the floor (ForgeOverseerPlateEntity). Where a blow lands
 * is where it came from: his left or right side, and high (over 2.3 blocks: a jumping blow, an arrow up high) for a
 * pauldron, low (under 1.35: a crouching blow, an arrow at the legs) for a greave, the breastplate between - and if
 * that plate is gone, the nearest one left on that side, then the other side. Every plate he still wears takes 7% off
 * what reaches him; with both halves of the breastplate gone the grate in his chest is bare and he takes a quarter more.
 * But every plate he loses also makes him lighter and quicker - 6% faster on his feet and his rests 6% shorter - and
 * with all six gone he is UNBOUND, wilder still.
 *
 * <p>Below half his health he bellows (roar), quenches at once and comes on harder: a wider ring, more slag, shorter
 * rests, more often at the water.
 *
 * <p>Asleep at his post until someone comes in (Dormant:1b, as the court's other keepers). His first block is his home;
 * he never goes farther than 26 blocks from it. His bar is a vanilla ServerBossEvent named
 * entity.frozen_dominion.forge_overseer.
 */
public class ForgeOverseerEntity extends FrostServantEntity implements GateKeeper {

    private static final EntityDataAccessor<Boolean> DORMANT =
            SynchedEntityData.defineId(ForgeOverseerEntity.class, EntityDataSerializers.BOOLEAN);
    /** Bit i set: plate i is gone (PLATE_BONES). */
    private static final EntityDataAccessor<Integer> PLATES_LOST =
            SynchedEntityData.defineId(ForgeOverseerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> QUENCHED =
            SynchedEntityData.defineId(ForgeOverseerEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int AWAKEN = 1, SLAM = 2, SWEEP = 3, GRAB = 4, GRAB_MISS = 5, SLAG = 6, QUENCH = 7,
            QUENCH_CARRIED = 8, ROAR = 9, STAGGER = 10, INTRO = 11;
    /** HIS ENTRANCE, at work over his bench (tools/gen_forge_overseer.py INTRO_*: the "intro" clip - change one, change
     *  both; BossScenes cuts its shots to them): the lump taken up, three blows on it, he looks up, the lump let fall. */
    public static final int INTRO_T = 144, INTRO_GRIP = 12, INTRO_LOOK = 66, INTRO_DROP = 96;
    public static final int[] INTRO_BLOWS = {24, 40, 56};
    /** HIS DEATH (tools/gen_forge_overseer.py DEATH_*: the "death" clip - change one, change both; BossScenes' death
     *  film is cut to them): the tongs let fall (TONGS), the hammer's head driven down on the floor before him (PLANT),
     *  down on his knees over it (KNEEL), the cold fire flaring one last time (FLARE) and going out (OUT), the haft
     *  slipping (SLIP) and down on his face across his hammer (FALL). The clip runs DEATH_LAG behind his deathTime: the
     *  controller blends into it first. */
    public static final int DEATH_T = 84, DEATH_TONGS = 14, DEATH_PLANT = 20, DEATH_KNEEL = 32, DEATH_FLARE = 38,
            DEATH_OUT = 58, DEATH_SLIP = 60, DEATH_FALL = 70, DEATH_LAG = 4;

    // ---- the beats of his clips (tools/gen_forge_overseer.py - change one, change both)
    static final int AWAKEN_T = 44, AWAKEN_FLARE = 12, AWAKEN_STRIKE = 30;
    static final int SLAM_T = 40, SLAM_HIT = 19;
    static final int SWEEP_T = 32, SWEEP_HIT = 15;
    static final int GRAB_T = 46, GRAB_SNAP = 14, GRAB_HOLD = 24, GRAB_PUNCH = 32, GRAB_LET_GO = 34;
    static final int GRAB_MISS_T = 30;
    static final int SLAG_T = 40, SLAG_GRIP = 8, SLAG_HIT = 22;
    static final int QUENCH_T = 38, QUENCH_PLUNGE = 16;
    /** The tub: taken by the bail (8), set down (22), the hammer in (30), the bail taken again (48), hung back (60). */
    static final int QC_T = 64, QC_GRIP = 8, QC_SET = 22, QC_PLUNGE = 30, QC_LIFT = 48, QC_HOOK = 60;
    static final int ROAR_T = 40, ROAR_CRY = 8;
    static final int STAGGER_T = 16;

    // ---- where his props work, in blocks before him (+) and to his right (+): the clips' points / 16
    /** The hammer head on the floor at the slam (gen: target (7, 8.2, -38)). */
    static final double SLAM_AHEAD = 2.4D, SLAM_RIGHT = 0.44D;
    /** The anvil point the tongs hold a thing at (WORK = (2, 15, -36)). */
    static final double WORK_AHEAD = 2.25D, WORK_RIGHT = 0.125D, WORK_UP = 0.94D;
    /** The middle of the water the hammer goes into (PLUNGE = (3, 11.2, -40)). */
    public static final double PLUNGE_AHEAD = 2.5D, PLUNGE_RIGHT = 0.19D;

    // ---- the numbers of his fight
    static final float SLAM_DMG = 15.0F, SWEEP_DMG = 11.0F, PUNCH_DMG = 13.0F, ROAR_PUSH = 1.0F;
    static final double SLAM_CRUSH_R = 1.9D, SWEEP_R = 4.6D, GRAB_REACH = 3.6D, GRAB_CONE = 35.0D, ROAR_R = 5.0D;
    /** Damage he must take while he holds you in the tongs for him to let go. */
    static final float GRAB_BREAK = 10.0F;
    static final int QUENCH_TIME = 240;
    /** His quenched hammer hits this much harder too. */
    public static final float QUENCHED_DAMAGE = 1.6F;
    /** Ticks to the first quench after he wakes, then between quenches (first half, second half). */
    static final int QUENCH_FIRST = 160, QUENCH_EVERY = 400, QUENCH_EVERY_2 = 280;
    static final int TROUGH_RANGE = 16, SEEK_LIMIT = 140;
    static final double WAKE = 12.0D, LEASH = 26.0D;

    // ---- the plates (tools/gen_forge_overseer.py PLATES: bone, its centre in model units)
    public static final String[] PLATE_BONES = {"pauldron_r", "pauldron_l", "breast_r", "breast_l", "greave_r",
            "greave_l"};
    /** x (his right), y, z (his back) of each plate's centre, model units. */
    static final double[][] PLATE_AT = {{15.6, 41.6, 0.0}, {-15.6, 41.6, 0.0}, {5.6, 38.4, -7.7}, {-5.6, 38.4, -7.7},
            {5.6, 9.4, -3.8}, {-5.6, 9.4, -3.8}};
    /** Model units its plate model is drawn over the plate entity while it falls (PLATE_FALL_LIFT). */
    static final double PLATE_LIFT = 7.0D;
    static final float PLATE_HP = 20.0F, HEAVY_BLOW = 7.0F, LIGHT_WEAR = 0.45F, PLATE_GUARD = 0.07F, BARE_CORE = 1.25F;
    private static final net.minecraft.resources.ResourceLocation LIGHTER = com.jastkub.frozenfortress.FrozenFortress.id("forge_overseer_plates_lost");

    private static final String P = "animation.forge_overseer.";
    private static final RawAnimation DORMANT_ANIM = RawAnimation.begin().thenLoop(P + "dormant");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation AWAKEN_ANIM = RawAnimation.begin().thenPlay(P + "awaken");
    private static final RawAnimation INTRO_ANIM = RawAnimation.begin().thenPlay(P + "intro");
    private static final RawAnimation SLAM_ANIM = RawAnimation.begin().thenPlay(P + "slam");
    private static final RawAnimation SWEEP_ANIM = RawAnimation.begin().thenPlay(P + "sweep");
    private static final RawAnimation GRAB_ANIM = RawAnimation.begin().thenPlay(P + "grab");
    private static final RawAnimation GRAB_MISS_ANIM = RawAnimation.begin().thenPlay(P + "grab_miss");
    private static final RawAnimation SLAG_ANIM = RawAnimation.begin().thenPlay(P + "slag");
    private static final RawAnimation QUENCH_ANIM = RawAnimation.begin().thenPlay(P + "quench");
    private static final RawAnimation QUENCH_CARRIED_ANIM = RawAnimation.begin().thenPlay(P + "quench_carried");
    private static final RawAnimation ROAR_ANIM = RawAnimation.begin().thenPlay(P + "roar");
    private static final RawAnimation STAGGER_ANIM = RawAnimation.begin().thenPlay(P + "stagger");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay(P + "hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay(P + "death");

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.frozen_dominion.forge_overseer"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);

    /** His post: the block he was put on. */
    @Nullable
    private BlockPos home;
    private boolean secondHalf;
    boolean roarNow;
    private final float[] plateWear = new float[6];
    private int quenchLeft;
    /** Ticks until he wants the water again (counts while he is awake and not quenched). */
    int quenchDue = QUENCH_FIRST;
    /** The troughs of his hall (water cauldrons near his home), looked for every ten seconds. */
    private final List<BlockPos> troughs = new ArrayList<>();
    private int troughScan;
    /** His hall has troughs at all (full or emptied): only a hall with none sends him to his own tub. */
    private boolean hallHasTroughs;
    /** The trough he is going to, and how long he has been at it. */
    @Nullable
    BlockPos seeking;
    int seekTicks;
    /** The trough he is quenching at (QUENCH). */
    @Nullable
    private BlockPos quenchAt;
    /** Who the tongs hold, how far out they were caught, and what has been done to him meanwhile. */
    @Nullable
    private LivingEntity held;
    private double heldFrom;
    private float heldHurt;
    int rest;

    public ForgeOverseerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 140;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.STEP_HEIGHT, 1.1D)   // (1.21: was setMaxUpStep)
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.21D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DORMANT, true);
        builder.define(PLATES_LOST, 0);
        builder.define(QUENCHED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new OverseerAttackGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 14.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ------------------------------------------------------------------------------------------------ what he is
    public boolean isDormant() {
        return entityData.get(DORMANT);
    }

    public boolean isQuenched() {
        return entityData.get(QUENCHED);
    }

    public boolean plateLost(int i) {
        return (entityData.get(PLATES_LOST) & (1 << i)) != 0;
    }

    public int platesLeft() {
        return 6 - Integer.bitCount(entityData.get(PLATES_LOST));
    }

    /** Both halves of the breastplate gone: the grate in his chest is bare. */
    public boolean coreBare() {
        return plateLost(2) && plateLost(3);
    }

    public boolean inSecondHalf() {
        return secondHalf;
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    /** How much shorter his rests are for every plate he has lost (and wilder with none). */
    float restFactor() {
        float f = 1.0F - 0.06F * (6 - platesLeft());
        if (platesLeft() == 0) {
            f *= 0.8F;
        }
        return secondHalf ? f * 0.75F : f;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    // ------------------------------------------------------------------------------------------------ his frame
    /** His facing on the floor (the way his body, and so his model, faces). */
    Vec3 ahead() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    /** His right hand's side. */
    Vec3 right() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(yaw), 0.0D, -Mth.sin(yaw));
    }

    /** A point of his model (units: x his right, y up, z his back) in the world, as he stands now. */
    Vec3 modelPoint(double mx, double my, double mz) {
        return position().add(right().scale(mx / 16.0D)).add(ahead().scale(-mz / 16.0D)).add(0.0D, my / 16.0D, 0.0D);
    }

    Vec3 before(double ahead, double rightOf, double up) {
        return position().add(ahead().scale(ahead)).add(right().scale(rightOf)).add(0.0D, up, 0.0D);
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
        bossEvent.setColor(secondHalf ? BossEvent.BossBarColor.WHITE : BossEvent.BossBarColor.BLUE);
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

    /** A line on the action bar of everyone fighting him (what just changed in the fight). */
    void tell(String key) {
        if (level() instanceof ServerLevel s) {
            for (ServerPlayer p : s.getPlayers(p -> p.distanceToSqr(this) < 32.0D * 32.0D)) {
                p.displayClientMessage(Component.translatable("entity.frozen_dominion.forge_overseer." + key), true);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ his forge's breath
    /** His forge's hearths (blast furnaces): found once round where he slept. */
    private final java.util.List<BlockPos> hearths = new java.util.ArrayList<>();
    private boolean hearthsFound;
    private int hearthNext = 40;

    /**
     * FROST OFF THE HEARTHS: while he
     * lives, every few seconds one of his forge's hearths (the furnaces under their hoods along its east wall - citadel6
     * _court_forge_dress) breathes a puff of frosted steam out of its mouth, with a hiss: a small pale-blue one
     * (ForgeOverseerSteamEntity.puff), never the white billow off his quench that tells his blows now freeze. Only with
     * someone near enough to see it. When he falls, his forge goes still.
     */
    private void hearthBreath() {
        if (--hearthNext > 0 || home == null) {
            return;
        }
        hearthNext = 45 + random.nextInt(55);
        if (level().getNearestPlayer(this, 40.0D) == null) {
            return;
        }
        if (!hearthsFound) {
            hearthsFound = true;
            for (BlockPos p : BlockPos.betweenClosed(home.offset(-24, -2, -16), home.offset(24, 4, 16))) {
                if (level().getBlockState(p).is(net.minecraft.world.level.block.Blocks.BLAST_FURNACE)) {
                    hearths.add(p.immutable());
                }
            }
        }
        if (hearths.isEmpty()) {
            return;
        }
        BlockPos h = hearths.get(random.nextInt(hearths.size()));
        BlockState s = level().getBlockState(h);
        if (!s.is(net.minecraft.world.level.block.Blocks.BLAST_FURNACE)) {
            hearths.remove(h);                                   // (broken: one fewer to breathe)
            return;
        }
        net.minecraft.core.Direction face = s.getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING);
        Vec3 at = Vec3.atBottomCenterOf(h).add(face.getStepX() * 0.7D, 0.15D, face.getStepZ() * 0.7D);
        level().addFreshEntity(new ForgeOverseerSteamEntity(level(), at).puff());
        level().playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.35F, 0.55F + random.nextFloat() * 0.2F);
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
        hearthBreath();
        if (isDormant()) {
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            Player near = level().getNearestPlayer(getX(), getY(), getZ(), WAKE,
                    e -> e instanceof Player p && !p.isCreative() && !p.isSpectator()
                            && Math.abs(p.getY() - getY()) < 5.0D && com.jastkub.frozenfortress.BossCutscenes.witness(this, p));
            if (near != null && !com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.holdsBack(this)) {
                awaken();
                setTarget(near);
            }
        } else {
            if (--troughScan <= 0) {
                scanTroughs();
            }
            if (quenchLeft > 0 && --quenchLeft == 0) {
                entityData.set(QUENCHED, false);
                playSound(FFSounds.FORGE_OVERSEER_FROST_FADE.get(), 1.4F, 1.0F);
            }
            if (!isQuenched() && !isAttacking() && quenchDue > 0) {
                quenchDue--;
            }
            beats();
            if (!isAttacking() && home != null && distanceToSqr(Vec3.atBottomCenterOf(home)) > LEASH * LEASH) {
                setTarget(null);
                seeking = null;
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
        rest = 16;
        // his entrance, once per player (BossCutscenes; his death scene is CommonEvents', 72 ticks): at work over his
        // bench, shot as a film - or, when everyone here has seen it, the short waking as before
        introYaw = com.jastkub.frozenfortress.BossCutscenes.postYaw(this);
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        if (com.jastkub.frozenfortress.BossCutscenes.intro(this, INTRO_T)) {
            setAttackState(INTRO);
            return;
        }
        setAttackState(AWAKEN);
        playSound(FFSounds.FORGE_OVERSEER_AWAKEN.get(), 2.4F, 1.0F);
    }

    /** Which way he faces through his entrance: his post's (his bench before him). */
    private float introYaw;

    /** His entrance's beats (the clip's), and him kept at his bench through it. */
    private void introBeats(int t) {
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        if (t == INTRO_GRIP) {
            playSound(FFSounds.FORGE_OVERSEER_TONGS.get(), 1.3F, 1.0F);
        }
        for (int k = 0; k < INTRO_BLOWS.length; k++) {
            int b = INTRO_BLOWS[k];
            if (t == b - 5) {
                playSound(FFSounds.FORGE_OVERSEER_SWING.get(), 0.8F + 0.3F * k, 1.15F - 0.08F * k);
            }
            if (t == b) {
                // the clank of the hammer on the lump, the last blow the hardest
                playSound(FFSounds.FORGE_OVERSEER_ANVIL.get(), 1.8F + 0.3F * k, 1.06F - 0.07F * k);
                if (k == INTRO_BLOWS.length - 1) {
                    playSound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, 1.0F, 0.75F);
                }
                if (level() instanceof ServerLevel s) {                  // (chips of cold slag: only an extra)
                    Vec3 at = modelPoint(-1.0D, 21.0D, -34.0D);
                    s.sendParticles(com.jastkub.frozenfortress.registry.FFParticles.ICE_SHARD.get(), at.x, at.y, at.z,
                            4 + 3 * k, 0.15D, 0.05D, 0.15D, 0.12D);
                }
            }
        }
        if (t == INTRO_LOOK) {
            playSound(FFSounds.FORGE_OVERSEER_IDLE.get(), 1.2F, 0.9F);    // the fire behind his grates, waking
        }
        if (t == INTRO_DROP) {
            playSound(FFSounds.FORGE_OVERSEER_TONGS.get(), 0.9F, 0.9F);
        }
        if (t == INTRO_DROP + 5) {
            playSound(FFSounds.FORGE_OVERSEER_SLAG_HIT.get(), 0.8F, 0.8F);  // the lump on the floor
        }
        if (t >= INTRO_T) {
            finish(10);
        }
    }

    /** Every beat of every attack, told before it lands. */
    private void beats() {
        int st = getAttackState();
        int t = attackTicks;
        LivingEntity target = getTarget();
        switch (st) {
            case INTRO -> introBeats(t);
            case AWAKEN -> {
                if (t == AWAKEN_STRIKE) {
                    AttackFxEntity.spawn(level(), "overseer_impact", modelPoint(17.0D, 0.0D, -33.0D), 0.0F, 0.6F, 24, this);
                    playSound(FFSounds.FORGE_OVERSEER_SLAM.get(), 1.6F, 1.15F);
                }
                if (t >= AWAKEN_T) {
                    finish(16);
                }
            }
            case SLAM -> {
                if (t == 9) {
                    playSound(FFSounds.FORGE_OVERSEER_SWING.get(), 1.6F, 0.8F);       // the hammer going up and back
                }
                if (t == SLAM_HIT - 2) {
                    playSound(FFSounds.FORGE_OVERSEER_SWING.get(), 2.0F, 0.65F);
                }
                if (t == SLAM_HIT) {
                    slam();
                }
                if (t >= SLAM_T) {
                    finish(24);
                }
            }
            case SWEEP -> {
                if (t == 3) {
                    playSound(FFSounds.FORGE_OVERSEER_TONGS.get(), 1.2F, 0.6F);       // iron grinding on his plate
                }
                if (t == SWEEP_HIT - 3) {
                    playSound(FFSounds.FORGE_OVERSEER_SWING.get(), 2.0F, 0.9F);
                }
                if (t == SWEEP_HIT) {
                    sweep();
                }
                if (t >= SWEEP_T) {
                    finish(16);
                }
            }
            case GRAB -> {
                if (t == 4 || t == 9) {
                    playSound(FFSounds.FORGE_OVERSEER_TONGS.get(), 1.6F, t == 4 ? 1.0F : 1.1F);   // the jaws clack open
                }
                if (t == GRAB_SNAP) {
                    snap();
                }
                if (t > GRAB_SNAP && t <= GRAB_LET_GO) {
                    hold(t);
                }
                if (t == 28 && held != null) {
                    playSound(FFSounds.FORGE_OVERSEER_SWING.get(), 1.8F, 0.8F);
                }
                if (t == GRAB_PUNCH) {
                    punch();
                }
                if (t >= GRAB_T) {
                    finish(18);
                }
            }
            case GRAB_MISS -> {
                getNavigation().stop();
                if (t >= GRAB_MISS_T) {
                    finish(14);
                }
            }
            case SLAG -> {
                if (t == 4) {
                    playSound(FFSounds.FORGE_OVERSEER_TONGS.get(), 1.4F, 0.8F);       // scraped off the floor
                }
                if (t == 16) {
                    playSound(FFSounds.FORGE_OVERSEER_SWING.get(), 1.6F, 0.85F);
                }
                if (t == SLAG_HIT) {
                    slag(target);
                }
                if (t >= SLAG_T) {
                    finish(22);
                }
            }
            case QUENCH -> {
                faceTrough();
                if (t == 6) {
                    playSound(FFSounds.FORGE_OVERSEER_SWING.get(), 1.4F, 0.75F);
                }
                if (t == QUENCH_PLUNGE) {
                    plunge(quenchAt);
                }
                if (t >= QUENCH_T) {
                    quenchAt = null;
                    finish(14);
                }
            }
            case QUENCH_CARRIED -> {
                getNavigation().stop();
                if (t == QC_GRIP || t == QC_LIFT || t == QC_HOOK) {
                    playSound(FFSounds.FORGE_OVERSEER_TONGS.get(), 1.4F, 0.7F);
                }
                if (t == QC_SET) {
                    playSound(FFSounds.FORGE_OVERSEER_PLATE_LAND.get(), 1.4F, 0.6F);   // the tub set down
                }
                if (t == QC_PLUNGE) {
                    plunge(null);
                }
                if (t >= QC_T) {
                    finish(14);
                }
            }
            case ROAR -> {
                getNavigation().stop();
                if (t == 2) {
                    playSound(FFSounds.FORGE_OVERSEER_ROAR.get(), 3.0F, 1.0F);
                }
                if (t == ROAR_CRY) {
                    bellow();
                }
                if (t >= ROAR_T) {
                    // his line in the fight, once, as the bellow of his second half dies away (BossVoice): "Now I
                    // forge your end."
                    BossVoice.fightLine(this, "forge_overseer");
                    quenchDue = 0;                                    // and at once to the water
                    finish(6);
                }
            }
            case STAGGER -> {
                getNavigation().stop();
                if (t >= STAGGER_T) {
                    finish(10);
                }
            }
            default -> {
            }
        }
    }

    private void finish(int after) {
        setAttackState(0);
        rest = Math.round(after * restFactor());
    }

    // ------------------------------------------------------------------------------------------------ SLAM
    void slam() {
        Vec3 at = before(SLAM_AHEAD, SLAM_RIGHT, 0.0D);
        playSound(FFSounds.FORGE_OVERSEER_SLAM.get(), 2.6F, 0.9F);
        AttackFxEntity.spawn(level(), "overseer_impact", at, 0.0F, 1.0F, 24, this);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().move(at.subtract(position())).inflate(SLAM_CRUSH_R, 1.0D, SLAM_CRUSH_R), this::foe)) {
            if (Math.hypot(v.getX() - at.x, v.getZ() - at.z) > SLAM_CRUSH_R || Math.abs(v.getY() - getY()) > 2.0D) {
                continue;
            }
            if (v.hurt(damageSources().mobAttack(this), SLAM_DMG)) {
                v.setDeltaMovement(v.getDeltaMovement().add(0.0D, 0.5D, 0.0D));
                v.hurtMarked = true;
                quenchedBlow(v);
            }
        }
        float size = secondHalf ? 9.0F / 7.0F : 1.0F;
        level().addFreshEntity(new ForgeOverseerShockwaveEntity(level(), this, new Vec3(at.x, getY(), at.z), size,
                isQuenched()));
    }

    // ------------------------------------------------------------------------------------------------ SWEEP
    void sweep() {
        AttackFxEntity.spawn(level(), "overseer_sweep", position(), yBodyRot, 1.0F, 10, this);
        Vec3 f = ahead();
        Vec3 r = right();
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SWEEP_R, 1.5D, SWEEP_R),
                this::foe)) {
            Vec3 d = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
            double dist = d.length();
            if (dist > SWEEP_R || Math.abs(v.getY() - getY()) > 2.6D || dist < 1.0E-3D) {
                continue;
            }
            // the head goes round from his left-back to his right: all of him but his back
            double ang = Math.toDegrees(Math.atan2(d.dot(r), d.dot(f)));
            if (ang < -118.0D || ang > 98.0D) {
                continue;
            }
            if (v.hurt(damageSources().mobAttack(this), SWEEP_DMG)) {
                Vec3 push = d.normalize().add(r.scale(0.8D)).normalize().scale(1.3D);   // flung round the way it goes
                v.setDeltaMovement(v.getDeltaMovement().add(push.x, 0.35D, push.z));
                v.hurtMarked = true;
                quenchedBlow(v);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ TONGS
    void snap() {
        playSound(FFSounds.FORGE_OVERSEER_GRAB.get(), 1.8F, 1.0F);
        Vec3 f = ahead();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(GRAB_REACH + 1.0D, 2.0D,
                GRAB_REACH + 1.0D), this::foe)) {
            Vec3 d = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
            double dist = d.length();
            if (dist < 0.8D || dist > GRAB_REACH || Math.abs(v.getY() - getY()) > 2.0D) {
                continue;
            }
            if (Math.toDegrees(Math.acos(Mth.clamp(d.normalize().dot(f), -1.0D, 1.0D))) > GRAB_CONE) {
                continue;
            }
            double toJaws = Math.abs(dist - 3.0D);
            if (toJaws < bestD) {
                bestD = toJaws;
                best = v;
            }
        }
        if (best == null) {
            setAttackState(GRAB_MISS);                                // the jaws shut on air: he stumbles
            return;
        }
        held = best;
        heldFrom = Math.hypot(best.getX() - getX(), best.getZ() - getZ());
        heldHurt = 0.0F;
        best.hurt(damageSources().mobAttack(this), 3.0F);
        best.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 60, 0), this);
    }

    /** Whoever the tongs hold is hauled in to his anvil point and held there. */
    void hold(int t) {
        if (held == null) {
            return;
        }
        if (!held.isAlive() || held.distanceToSqr(this) > 7.0D * 7.0D) {
            held = null;
            return;
        }
        if (t >= GRAB_LET_GO) {
            held = null;
            return;
        }
        double u = Mth.clamp((t - GRAB_SNAP) / (double) (GRAB_HOLD - GRAB_SNAP), 0.0D, 1.0D);
        double dist = Mth.lerp(u, heldFrom, WORK_AHEAD);
        Vec3 want = before(dist, WORK_RIGHT, 0.0D);
        Vec3 move = new Vec3(want.x - held.getX(), 0.0D, want.z - held.getZ());
        held.setDeltaMovement(move.scale(0.5D).add(0.0D, Math.min(0.0D, held.getDeltaMovement().y), 0.0D));
        held.hurtMarked = true;
        held.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 4, false, false));
        held.addEffect(new MobEffectInstance(MobEffects.JUMP, 6, 128, false, false));
    }

    void punch() {
        if (held == null) {
            return;
        }
        LivingEntity v = held;
        playSound(FFSounds.FORGE_OVERSEER_SLAM.get(), 2.0F, 1.2F);
        AttackFxEntity.spawn(level(), "overseer_impact", before(WORK_AHEAD, WORK_RIGHT, 0.0D), 0.0F, 0.5F, 24, this);
        if (v.hurt(damageSources().mobAttack(this), PUNCH_DMG)) {
            Vec3 away = ahead().scale(1.2D);
            v.setDeltaMovement(v.getDeltaMovement().add(away.x, 0.35D, away.z));
            v.hurtMarked = true;
            quenchedBlow(v);
        }
    }

    /** Hurt hard enough while he holds you: he lets go and reels. */
    private void breakHold() {
        held = null;
        setAttackState(STAGGER);
        playSound(FFSounds.FORGE_OVERSEER_TONGS.get(), 1.8F, 0.5F);
    }

    // ------------------------------------------------------------------------------------------------ SLAG
    void slag(@Nullable LivingEntity target) {
        Vec3 from = before(WORK_AHEAD, WORK_RIGHT, WORK_UP);
        playSound(FFSounds.FORGE_OVERSEER_SLAG_STRIKE.get(), 2.2F, 1.0F);
        AttackFxEntity.spawn(level(), "overseer_impact", from.subtract(0.0D, WORK_UP, 0.0D), 0.0F, 0.45F, 24, this);
        Vec3 aim = target != null ? new Vec3(target.getX() - from.x, 0.0D, target.getZ() - from.z) : ahead();
        if (aim.lengthSqr() < 1.0E-4D) {
            aim = ahead();
        }
        double dist = Math.min(14.0D, aim.length());
        aim = aim.normalize();
        int n = secondHalf ? 7 : 5;
        double fan = secondHalf ? 34.0D : 26.0D;
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(-fan + 2.0D * fan * i / (n - 1) + (random.nextDouble() - 0.5D) * 4.0D);
            Vec3 d = new Vec3(aim.x * Math.cos(a) - aim.z * Math.sin(a), 0.0D, aim.x * Math.sin(a) + aim.z * Math.cos(a));
            double speed = 0.75D + 0.04D * dist;
            double lift = 0.12D + 0.012D * dist + (i % 2 == 0 ? 0.0D : 0.06D);
            ForgeOverseerSlagEntity s = new ForgeOverseerSlagEntity(level(), this);
            s.moveTo(from.x, from.y, from.z, random.nextFloat() * 360.0F, 0.0F);
            s.setDeltaMovement(d.x * speed, lift, d.z * speed);
            level().addFreshEntity(s);
        }
    }

    // ------------------------------------------------------------------------------------------------ THE QUENCH
    /** Water cauldrons near his home: the hall's troughs. */
    private void scanTroughs() {
        troughScan = 200;
        troughs.clear();
        hallHasTroughs = false;
        if (home == null) {
            return;
        }
        for (int dx = -TROUGH_RANGE; dx <= TROUGH_RANGE; dx++) {
            for (int dz = -TROUGH_RANGE; dz <= TROUGH_RANGE; dz++) {
                if (dx * dx + dz * dz > TROUGH_RANGE * TROUGH_RANGE) {
                    continue;
                }
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos p = home.offset(dx, dy, dz);
                    BlockState bs = level().getBlockState(p);
                    if (isTrough(bs)) {
                        troughs.add(p);
                    }
                    if (bs.getBlock() instanceof net.minecraft.world.level.block.AbstractCauldronBlock) {
                        hallHasTroughs = true;
                    }
                }
            }
        }
    }

    static boolean isTrough(BlockState s) {
        return s.is(Blocks.WATER_CAULDRON) && s.getValue(LayeredCauldronBlock.LEVEL) > 0;
    }

    /** The trough he would quench at now: the nearest one there is still water in; none - his own tub. */
    @Nullable
    BlockPos nearestTrough() {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : troughs) {
            if (!isTrough(level().getBlockState(p))) {
                continue;
            }
            double d = distanceToSqr(Vec3.atBottomCenterOf(p));
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** Where he must stand to drive the hammer into `trough`: PLUNGE before him and a little to his right. */
    Vec3 standFor(BlockPos trough) {
        Vec3 c = Vec3.atBottomCenterOf(trough);
        Vec3 from = new Vec3(getX() - c.x, 0.0D, getZ() - c.z);
        if (from.lengthSqr() < 1.0E-3D) {
            from = ahead().scale(-1.0D);
        }
        Vec3 back = from.normalize();                                 // from the trough toward him
        Vec3 rightOfHim = new Vec3(back.z, 0.0D, -back.x);            // his right, facing the trough
        return c.add(back.scale(PLUNGE_AHEAD)).subtract(rightOfHim.scale(PLUNGE_RIGHT));
    }

    /** At his trough: turned square to it, his body with his eyes. */
    void faceTrough() {
        getNavigation().stop();
        if (quenchAt == null) {
            return;
        }
        Vec3 c = Vec3.atBottomCenterOf(quenchAt);
        Vec3 p = c.subtract(right().scale(PLUNGE_RIGHT));
        float yaw = (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        yBodyRotO = yaw;
        yRotO = yaw;
    }

    /**
     * THE WATER TAKEN FROM HIM: the trough he was going to has been emptied - by a bucket, by anyone. He bellows
     * and reels (an opening), goes without his quench, and wants the water again only in a while. His own tub is for a
     * hall that has no troughs at all, not for one whose troughs have been taken from him.
     */
    void waterDenied() {
        seeking = null;
        getNavigation().stop();
        quenchDue = (secondHalf ? QUENCH_EVERY_2 : QUENCH_EVERY) / 2;
        tell("denied");
        playSound(FFSounds.FORGE_OVERSEER_SLAM.get(), 1.4F, 0.6F);
        setAttackState(STAGGER);
    }

    void startQuench(@Nullable BlockPos trough) {
        seeking = null;
        quenchAt = trough;
        getNavigation().stop();
        if (trough != null) {
            setAttackState(QUENCH);
            faceTrough();
        } else {
            setAttackState(QUENCH_CARRIED);
        }
    }

    /** The hammer into the water: steam bursts up and for ten seconds every blow of it freezes. A trough loses a
     *  level of water to it; a trough emptied meanwhile (a bucket is an answer) gives him nothing. */
    private void plunge(@Nullable BlockPos trough) {
        Vec3 at;
        if (trough != null) {
            BlockState s = level().getBlockState(trough);
            if (!isTrough(s)) {
                playSound(FFSounds.FORGE_OVERSEER_SLAM.get(), 1.2F, 1.5F);   // iron on dry iron: nothing
                quenchDue = (secondHalf ? QUENCH_EVERY_2 : QUENCH_EVERY) / 2;
                tell("denied");
                return;
            }
            LayeredCauldronBlock.lowerFillLevel(s, level(), trough);
            at = new Vec3(trough.getX() + 0.5D, trough.getY() + 0.9D, trough.getZ() + 0.5D);
        } else {
            at = before(PLUNGE_AHEAD, PLUNGE_RIGHT, 0.45D);
        }
        level().addFreshEntity(new ForgeOverseerSteamEntity(level(), at));
        playSound(FFSounds.FORGE_OVERSEER_QUENCH.get(), 2.6F, 1.0F);
        entityData.set(QUENCHED, true);
        quenchLeft = QUENCH_TIME;
        quenchDue = secondHalf ? QUENCH_EVERY_2 : QUENCH_EVERY;
        tell("quenched");
    }

    /** A blow of the quenched hammer: whoever it lands on is frozen where they stand. */
    void quenchedBlow(LivingEntity v) {
        if (!isQuenched() || !v.isAlive()) {
            return;
        }
        if (!level().getEntitiesOfClass(ForgeOverseerRimeEntity.class, v.getBoundingBox().inflate(0.5D),
                r -> r.holds(v)).isEmpty()) {
            return;
        }
        level().addFreshEntity(new ForgeOverseerRimeEntity(level(), this, v));
        playSound(FFSounds.ICE_PRISON.get(), 1.4F, 1.2F);
    }

    // ------------------------------------------------------------------------------------------------ ROAR
    private void bellow() {
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(ROAR_R, 2.0D, ROAR_R),
                this::foe)) {
            Vec3 d = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
            if (d.length() > ROAR_R) {
                continue;
            }
            Vec3 push = d.lengthSqr() > 1.0E-4D ? d.normalize().scale(ROAR_PUSH) : ahead().scale(ROAR_PUSH);
            v.setDeltaMovement(v.getDeltaMovement().add(push.x, 0.3D, push.z));
            v.hurtMarked = true;
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 0), this);
        }
    }

    // ------------------------------------------------------------------------------------------------ THE PLATES
    /** Which plate a blow from `source` lands on: his side it came from, the height it came at. */
    private int plateStruck(DamageSource source) {
        Entity by = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        if (by == null) {
            return -1;
        }
        Vec3 d = new Vec3(by.getX() - getX(), 0.0D, by.getZ() - getZ());
        boolean rightSide = d.dot(right()) >= 0.0D;
        double h = (by instanceof Projectile ? by.getY() : by.getEyeY()) - getY();
        int zone = h > 2.3D ? 0 : h < 1.35D ? 2 : 1;                 // pauldron, breast, greave
        int[] order = switch (zone) {
            case 0 -> new int[]{0, 1, 2};
            case 2 -> new int[]{2, 1, 0};
            default -> new int[]{1, 0, 2};
        };
        for (int pass = 0; pass < 2; pass++) {
            boolean r = pass == 0 ? rightSide : !rightSide;
            for (int z : order) {
                int i = z * 2 + (r ? 0 : 1);
                if (!plateLost(i)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void wear(int i, float amount) {
        plateWear[i] += amount >= HEAVY_BLOW ? amount : amount * LIGHT_WEAR;
        if (plateWear[i] >= PLATE_HP) {
            breakPlate(i);
        }
    }

    private void breakPlate(int i) {
        if (plateLost(i)) {
            return;
        }
        entityData.set(PLATES_LOST, entityData.get(PLATES_LOST) | (1 << i));
        double[] m = PLATE_AT[i];
        // (its model is drawn PLATE_LIFT over its box while it falls - tools/gen_forge_overseer.py PLATE_FALL_LIFT)
        Vec3 at = modelPoint(m[0], m[1] - PLATE_LIFT, m[2]);
        Vec3 out = (i < 2 || i >= 4) ? right().scale(m[0] > 0 ? 1.0D : -1.0D) : ahead();
        ForgeOverseerPlateEntity plate = new ForgeOverseerPlateEntity(level(), i, at, yBodyRot,
                out.scale(0.14D).add((random.nextDouble() - 0.5D) * 0.06D, 0.22D, (random.nextDouble() - 0.5D) * 0.06D));
        level().addFreshEntity(plate);
        playSound(FFSounds.FORGE_OVERSEER_PLATE_BREAK.get(), 2.2F, 0.9F + random.nextFloat() * 0.2F);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(LIGHTER);
            speed.addPermanentModifier(new AttributeModifier(LIGHTER,
                    0.06D * (6 - platesLeft()), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        if (platesLeft() == 0) {
            tell("unbound");
            playSound(FFSounds.FORGE_OVERSEER_ROAR.get(), 2.4F, 1.2F);
        } else if ((i == 2 || i == 3) && coreBare()) {
            tell("core_bare");
        } else {
            tell("plate");
        }
        if (!isAttacking()) {
            setAttackState(STAGGER);
        }
    }

    // ------------------------------------------------------------------------------------------------ hurt, death
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (isDormant()) {
                if (!level().isClientSide && source.getEntity() instanceof Player) {
                    awaken();
                }
                return false;
            }
            if (getAttackState() == INTRO) {
                return false;                                         // his entrance: a scene is not a fight
            }
            if (getAttackState() == AWAKEN && attackTicks < 14) {
                amount *= 0.5F;                                       // still shaking off the cold
            }
            if (!level().isClientSide && source.getEntity() instanceof LivingEntity && !(source.getEntity() instanceof FrostServantEntity)) {
                int i = plateStruck(source);
                if (i >= 0) {
                    wear(i, amount);
                }
            }
            amount *= 1.0F - PLATE_GUARD * platesLeft();
            if (coreBare()) {
                amount *= BARE_CORE;
            }
        }
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide) {
            if (held != null && getAttackState() == GRAB) {
                heldHurt += amount;
                if (heldHurt >= GRAB_BREAK) {
                    breakHold();
                }
            }
            if (!secondHalf && isAlive() && getHealth() < getMaxHealth() * 0.5F) {
                secondHalf = true;
                roarNow = true;
            }
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        bossEvent.removeAllPlayers();
        super.die(source);
        if (level() instanceof ServerLevel s) {
            held = null;
            Player by = source.getEntity() instanceof Player p ? p : getKillCredit() instanceof Player p2 ? p2 : null;
            ForgeOverseerSmithHook.free(s, home != null ? home : blockPosition(), this, by);
            // and the bars of the smith's cell rise (an exit gate, its keeper him - 07.10.2026)
            com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.keeperFell(s, this);
        }
    }

    /** His body lies until his death scene goes to black (BossCutscenes: the clip, a breath, the card). */
    @Override
    protected int getDeathDuration() {
        return DEATH_T + 36;
    }

    /** His death's sounds, on its clip's beats: the hammer's head on the stones (the tongs with it), his knees, the
     *  last flare of the cold fire, its going out, and the fall onto his hammer. */
    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (level().isClientSide) {
            return;
        }
        int t = deathTime - DEATH_LAG;
        if (t == DEATH_PLANT) {
            playSound(FFSounds.FORGE_OVERSEER_SLAM.get(), 1.4F, 0.8F);
            playSound(FFSounds.FORGE_OVERSEER_TONGS.get(), 1.0F, 0.7F);
        }
        if (t == DEATH_KNEEL) {
            playSound(FFSounds.FORGE_OVERSEER_STEP.get(), 1.6F, 0.6F);
            playSound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.4F, 0.5F);
        }
        if (t == DEATH_FLARE) {
            playSound(FFSounds.FORGE_OVERSEER_ROAR.get(), 1.6F, 0.55F);
        }
        if (t == DEATH_OUT) {
            playSound(net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, 1.4F, 0.5F);
            playSound(FFSounds.FORGE_OVERSEER_FROST_FADE.get(), 1.2F, 0.8F);
        }
        if (t == DEATH_FALL) {
            playSound(FFSounds.FORGE_OVERSEER_SLAM.get(), 1.8F, 0.6F);
            playSound(FFSounds.FORGE_OVERSEER_ANVIL.get(), 1.4F, 0.5F);
            playSound(FFSounds.FORGE_OVERSEER_PLATE_LAND.get(), 1.2F, 0.7F);
        }
    }

    // ------------------------------------------------------------------------------------------------ saved
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Dormant", isDormant());
        tag.putBoolean("SecondHalf", secondHalf);
        tag.putInt("PlatesLost", entityData.get(PLATES_LOST));
        tag.putInt("QuenchLeft", quenchLeft);
        tag.putInt("QuenchDue", quenchDue);
        ListTag wearTag = new ListTag();
        for (float w : plateWear) {
            wearTag.add(FloatTag.valueOf(w));
        }
        tag.put("PlateWear", wearTag);
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
        entityData.set(PLATES_LOST, tag.getInt("PlatesLost") & 63);
        quenchLeft = tag.getInt("QuenchLeft");
        entityData.set(QUENCHED, quenchLeft > 0);
        if (tag.contains("QuenchDue")) {
            quenchDue = tag.getInt("QuenchDue");
        }
        ListTag wearTag = tag.getList("PlateWear", Tag.TAG_FLOAT);
        for (int i = 0; i < Math.min(6, wearTag.size()); i++) {
            plateWear[i] = wearTag.getFloat(i);
        }
        int[] h = tag.getIntArray("Home");
        if (h.length == 3) {
            home = new BlockPos(h[0], h[1], h[2]);
        }
        int lost = 6 - platesLeft();
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && lost > 0) {
            speed.removeModifier(LIGHTER);
            speed.addPermanentModifier(new AttributeModifier(LIGHTER, 0.06D * lost,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    // ------------------------------------------------------------------------------------------------ looks, sounds
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            // into the entrance at once (its first frame is the sleep it wakes from): its clip runs on the very
            // ticks the scene's cuts and sounds are timed to (BossScenes) - and out of it as from any other
            state.getController().transitionLength(getAttackState() == INTRO ? 0 : 4);
            if (isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (isDormant()) {
                return state.setAndContinue(DORMANT_ANIM);
            }
            switch (getAttackState()) {
                case AWAKEN: return state.setAndContinue(AWAKEN_ANIM);
                case INTRO: return state.setAndContinue(INTRO_ANIM);
                case SLAM: return state.setAndContinue(SLAM_ANIM);
                case SWEEP: return state.setAndContinue(SWEEP_ANIM);
                case GRAB: return state.setAndContinue(GRAB_ANIM);
                case GRAB_MISS: return state.setAndContinue(GRAB_MISS_ANIM);
                case SLAG: return state.setAndContinue(SLAG_ANIM);
                case QUENCH: return state.setAndContinue(QUENCH_ANIM);
                case QUENCH_CARRIED: return state.setAndContinue(QUENCH_CARRIED_ANIM);
                case ROAR: return state.setAndContinue(ROAR_ANIM);
                case STAGGER: return state.setAndContinue(STAGGER_ANIM);
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
        return isDormant() ? null : FFSounds.FORGE_OVERSEER_IDLE.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 150;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.FORGE_OVERSEER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.FORGE_OVERSEER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(FFSounds.FORGE_OVERSEER_STEP.get(), 0.7F, 0.9F + random.nextFloat() * 0.15F);
    }

    boolean foe(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof FrostServantEntity)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ------------------------------------------------------------------------------------------------ the fight
    /**
     * Chooses what he does next and walks him to it - or to the water when the hammer wants quenching; the beats of
     * what he does are his own (aiStep), so a lost target never leaves him with a victim in the tongs.
     */
    static class OverseerAttackGoal extends Goal {
        private final ForgeOverseerEntity mob;
        private final int[] perAttack = new int[11];

        OverseerAttackGoal(ForgeOverseerEntity mob) {
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
            mob.seeking = null;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            for (int i = 0; i < perAttack.length; i++) {
                if (perAttack[i] > 0) {
                    perAttack[i]--;
                }
            }
            int state = mob.getAttackState();
            int t = mob.attackTicks;
            if (state != 0) {
                mob.getNavigation().stop();
                // eyes on them through each tell; not through a blow already falling, nor while at the water
                if ((state == SLAM && t < SLAM_HIT - 3) || (state == SWEEP && t < 9) || (state == GRAB && t < GRAB_SNAP - 2)
                        || (state == SLAG && t < SLAG_HIT - 2) || state == ROAR) {
                    mob.getLookControl().setLookAt(target, 20.0F, 20.0F);
                }
                return;
            }
            double dist = Math.hypot(target.getX() - mob.getX(), target.getZ() - mob.getZ());
            boolean two = mob.inSecondHalf();
            // ---- the bellow at half his health comes before anything
            if (mob.roarNow) {
                mob.roarNow = false;
                mob.setAttackState(ROAR);
                return;
            }
            // ---- to the water
            if (mob.quenchDue <= 0 && !mob.isQuenched()) {
                if (mob.seeking == null) {
                    mob.seeking = mob.nearestTrough();
                    mob.seekTicks = 0;
                    if (mob.seeking == null) {
                        if (mob.hallHasTroughs) {
                            mob.waterDenied();                        // every trough emptied: no quench for him
                        } else {
                            mob.startQuench(null);                    // a hall with none: his own tub
                        }
                        return;
                    }
                    mob.tell("seeks");                                // (the tell: there is time to take it from him)
                }
                if (!isTrough(mob.level().getBlockState(mob.seeking))) {
                    mob.waterDenied();                                // emptied under his nose
                    return;
                }
                // somebody right on him while he goes: the sweep first
                if (dist < 3.4D && perAttack[SWEEP] <= 0 && mob.rest <= 0) {
                    perAttack[SWEEP] = 50;
                    mob.setAttackState(SWEEP);
                    return;
                }
                Vec3 stand = mob.standFor(mob.seeking);
                double off = Math.hypot(stand.x - mob.getX(), stand.z - mob.getZ());
                if (off < 0.7D) {
                    mob.startQuench(mob.seeking);
                } else if (++mob.seekTicks > SEEK_LIMIT) {
                    mob.startQuench(null);                            // cannot get to it: his own tub
                } else {
                    mob.getNavigation().moveTo(stand.x, stand.y, stand.z, 1.1D);
                    Vec3 c = Vec3.atCenterOf(mob.seeking);
                    mob.getLookControl().setLookAt(c.x, c.y, c.z, 20.0F, 20.0F);
                }
                return;
            }
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (dist > 2.8D) {
                mob.getNavigation().moveTo(target, 1.0D);
            } else {
                mob.getNavigation().stop();
            }
            if (mob.rest > 0) {
                mob.rest--;
                return;
            }
            boolean level = Math.abs(target.getY() - mob.getY()) < 2.5D;
            int wSweep = dist < 4.2D && level && perAttack[SWEEP] <= 0 ? 4 : 0;
            int wSlam = dist > 1.6D && dist < 7.5D && level && perAttack[SLAM] <= 0 ? 3 : 0;
            int wGrab = dist > 1.6D && dist < GRAB_REACH - 0.2D && level && perAttack[GRAB] <= 0 ? 3 : 0;
            int wSlag = dist > 4.5D && dist < 16.0D && perAttack[SLAG] <= 0 ? (dist > 8.0D ? 5 : 2) : 0;
            int sum = wSweep + wSlam + wGrab + wSlag;
            if (sum <= 0) {
                return;
            }
            int r = mob.random.nextInt(sum);
            int chosen = r < wSweep ? SWEEP : r < wSweep + wSlam ? SLAM : r < wSweep + wSlam + wGrab ? GRAB : SLAG;
            float hurry = mob.restFactor();
            perAttack[chosen] = Math.round(switch (chosen) {
                case SWEEP -> 50;
                case SLAM -> two ? 70 : 90;
                case GRAB -> two ? 100 : 120;
                case SLAG -> two ? 80 : 110;
                default -> 0;
            } * hurry);
            mob.getNavigation().stop();
            mob.setAttackState(chosen);
        }
    }
}
