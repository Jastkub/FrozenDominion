package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * WLADCA KOSCI - THE BONE LORD, the miniboss of the Chasm of Bones (Otchlan Kosci) in the depths
 * before the Ice Prison: a hall over a chasm whose floor of ice spikes the fog hides, crossed only by bridges three
 * wide between pillars - and the Lord is the chase across them.
 *
 * <p>A COLOSSUS: thirteen blocks of hunched skeleton in an ushanka (tools/gen_bone_lord.py), drawn twice his geometry
 * (BoneLordRenderer). His hitbox is a column narrow enough for the bridges (2.4 wide, 12 tall); his ribcage and his
 * skull are parts of their own ({@link BoneLordPart}), carried where the clip has them, so an arrow at his head - or
 * at his body lying out along the bridge after a fall - lands.
 *
 * <ul>
 *   <li>DORMANT: a heap of bones on the hall's middle pillar. Somebody in his hall (its Arena box, from the citadel's
 *   generator) and the heap pulls itself together - ASSEMBLE, the scene: it sits up, kneels, picks its ushanka up off
 *   the heap and puts it on, stands, leans down to you and says it: "run" (BossScenes says it on
 *   the 94th tick). Nothing hurts it while it does.</li>
 *   <li>THE CHASE: after whoever is nearest, a little slower than a sprinting player with Speed I; now and then a
 *   burst (SPRINT) to a fifth over that. Every footfall is felt (ScreenShake, through BoneLordClient). Cobwebs do not
 *   hold it; it is too big for the holes in the bridges to take its feet.</li>
 *   <li>IT TRIPS: on the run, now and then of itself - more often over a hole, when an arrow takes it in the legs, or
 *   on a hard turn at a burst - and goes down on its knees and fists for a while, a tenth of its health gone with it,
 *   and open (it takes half again what it would).</li>
 *   <li>THE STOMP: anyone close before him - a knee up, and the foot comes down: the bridge round it jumps, whoever
 *   is near is thrown off their feet (and off the bridge, if that is the way the blow throws them), the floor shakes
 *   for everyone in the hall.</li>
 *   <li>THE GRAB: its hand sweeps in and shuts on you, lifts you to its face, roars, and dashes you down - the end of
 *   you (a totem is a second life, and nothing else is).</li>
 *   <li>THE THROW: it stoops to the bridge and comes up with one of the dead - one already up, one of the corpses on
 *   the bridge, or one it tears out of the bones of its own heap - and hurls it at you, screaming as it flies: a hit
 *   knocks you back, and a fall is the spikes.</li>
 *   <li>ITS END: it goes over backwards into the chasm (death_fall; BossCutscenes' death scene).</li>
 * </ul>
 */
public class BoneLordEntity extends FrostServantEntity implements GateKeeper {

    public static final int ASSEMBLE = 1, TRIP = 2, DOWN = 3, GETUP = 4, GRAB = 5, SLAM = 6, THROW = 7, STOMP = 8;
    // THE BEATS: tools/gen_bone_lord.py writes the clips to these (and BoneLordFrames out of the clips)
    static final int ASSEMBLE_T = 112;
    static final int TRIP_T = 24, TRIP_HIT = 15, DOWN_T = 56, GETUP_T = 36;
    static final int GRAB_T = 24, GRAB_CATCH = 9;
    static final int SLAM_T = 40, SLAM_HIT = 26;
    static final int THROW_T = 36, THROW_PICK = 10, THROW_LET = 24;
    static final int STOMP_T = 32, STOMP_HIT = 17;
    /** GeckoLib's blend into a new clip (the main controller's transition, 3 ticks): a clip's tick k is drawn LAG ticks
     *  after its state began. The grab's catch, the fist that holds you and the slam that ends it wait for it. */
    static final int LAG = 3;
    /**
     * Its speed: the attribute, and a burst over it. A mob goes
     * 0.98 * speed^2 / 0.454 blocks a tick on stone (Mob.setSpeed is its forward input as well as its speed), so the
     * 0.14 and 0.155 of before were a crawl of 0.04-0.05 - a block a second - under clips striding as if at 0.65. Now
     * ~0.29 (5.8 blocks a second: a hair over a sprinting player, under one with Speed I) and on a burst ~0.36; the run
     * and sprint clips stride exactly that (tools/gen_bone_lord.py RUN_LEN/RUN_REACH).
     */
    static final double SPEED = 0.365D, BURST = 1.12D;
    /** THE STOMP: who is within this of the foot is struck and thrown; who is within FELT is shaken off balance. */
    static final double STOMP_REACH = 4.2D, STOMP_FELT = 9.0D;
    static final float STOMP_DAMAGE = 9.0F;
    /** How many of the dead may be about in his hall before he stops tearing new ones out of his heap. */
    static final int MAX_DEAD = 10;
    /**
     * THE DEAD COME UP THE PILLARS: every CLIMB_EVERY ticks or so, while his hall is not yet full of them,
     * one comes up out of the mist a pillar's face near whoever he hunts - CLIMB_FROM under the bridges.
     */
    static final int CLIMB_EVERY = 24, CLIMB_FROM = 12;
    /** How many CLIMBERS may be about whoever he hunts (within CLIMB_NEAR) before no more come up near them. Only the
     *  ones that climbed count (CLIMBER tag): the hall's own MAX_DEAD, and then this cap over every skeleton near, were
     *  both filled by the corpses risen along the bridges - five of those are about you all the chase long - and nothing
     *  ever climbed. */
    static final int CLIMB_CAP = 30;
    /** A MASS OF THEM COMING UP OUT OF THE MIST: up to this many on the pillars' faces at once, a new one every one and a
     *  half to two seconds, from twelve under the bridges - deep in the mist, seven seconds of climbing in sight - and
     *  CLIMB_CAP (in the hall, climbed and still about) keeps the
     *  bridges from filling with them - and no new one comes up within CLIMB_APART of another. */
    static final int CLIMB_AT_ONCE = 6;
    static final double CLIMB_APART = 7.0D;
    /** The persistent tag a climber carries, so the cap counts climbers alone. */
    static final String CLIMBER = "ffBoneClimber";
    static final double CLIMB_NEAR = 24.0D;
    /** BACK TO THE MIDDLE: this long with nobody in his hall, and he is back on his own pillar. */
    static final int ALONE_HOME = 30;

    private static final EntityDataAccessor<Boolean> DORMANT =
            SynchedEntityData.defineId(BoneLordEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SPRINTING =
            SynchedEntityData.defineId(BoneLordEntity.class, EntityDataSerializers.BOOLEAN);
    /** Its hall, for the client too (the fog that hides the chasm - BoneLordFog). */
    private static final EntityDataAccessor<BlockPos> ARENA_MIN =
            SynchedEntityData.defineId(BoneLordEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos> ARENA_MAX =
            SynchedEntityData.defineId(BoneLordEntity.class, EntityDataSerializers.BLOCK_POS);
    /**
     * THE FLOOR SHAKES: a blow that lands (the stomp, the slam, his own fall) bumps this - a count in the high bits,
     * how hard in the low byte - and every client near feels it once (BoneLordClient, ScreenShake). One int synced
     * per blow, nothing per tick.
     */
    private static final EntityDataAccessor<Integer> IMPACT =
            SynchedEntityData.defineId(BoneLordEntity.class, EntityDataSerializers.INT);
    /** Which way he faces through a blow (yaw, degrees): the clips' hand and foot are laid out from it. */
    private static final EntityDataAccessor<Float> ATTACK_YAW =
            SynchedEntityData.defineId(BoneLordEntity.class, EntityDataSerializers.FLOAT);

    private static final String P = "animation.bone_lord.";
    private static final RawAnimation DORMANT_ANIM = RawAnimation.begin().thenLoop(P + "dormant");
    private static final RawAnimation ASSEMBLE_ANIM = RawAnimation.begin().thenPlayAndHold(P + "assemble");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop(P + "run");
    private static final RawAnimation SPRINT_ANIM = RawAnimation.begin().thenLoop(P + "sprint");
    private static final RawAnimation TRIP_ANIM = RawAnimation.begin().thenPlayAndHold(P + "trip");
    private static final RawAnimation DOWN_ANIM = RawAnimation.begin().thenLoop(P + "down");
    private static final RawAnimation GETUP_ANIM = RawAnimation.begin().thenPlayAndHold(P + "getup");
    private static final RawAnimation GRAB_ANIM = RawAnimation.begin().thenPlayAndHold(P + "grab");
    private static final RawAnimation SLAM_ANIM = RawAnimation.begin().thenPlayAndHold(P + "slam");
    private static final RawAnimation THROW_ANIM = RawAnimation.begin().thenPlayAndHold(P + "throw");
    private static final RawAnimation STOMP_ANIM = RawAnimation.begin().thenPlayAndHold(P + "stomp");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(P + "death_fall");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay(P + "hurt");

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.frozen_dominion.bone_lord"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);

    /** His ribcage and his skull, hit where the clip has them. */
    private final BoneLordPart body;
    private final BoneLordPart head;
    private final BoneLordPart[] parts;

    @Nullable
    private BlockPos home;
    /** Its hall, relative to where it lay (far corner exclusive) - from the citadel's generator; the default is the
     *  generator's own (citadel6 CHASM about BONE_HOME), for a fresh one KeeperReset puts back without it. */
    private int[] arena = {-36, -26, -23, 36, 26, 23};
    private int burstLeft, burstCd = 260, throwCd = 120, grabCd, stompCd = 40, climbCd = 100;
    /** Ticks with nobody in his hall to hunt (awake): at ALONE_HOME he is back on his own pillar. */
    private int alone;
    @Nullable
    private UUID held;
    /** His fist opening of itself (letGo): the one time a rider of his may get off. */
    private boolean releasing;
    @Nullable
    private UUID minion;
    private Vec3 lastHeading = Vec3.ZERO;
    private boolean overHole;
    private int impactSeq;
    // ---- the client's side of it (BoneLordClient reads these)
    /** A footfall the clip has just put down (GeckoLib's sound keyframes): 1 his +x foot, 2 the other; 0 none. */
    private int stepFlag;
    /** When the clip last put a foot down (tickCount) - if it has not for a while he is not being drawn. */
    private int lastClipStep = -1000;
    private int clientState = -1;
    private int clientStateTicks;

    public BoneLordEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 160;
        setMaxUpStep(1.1F);
        this.body = new BoneLordPart(this, "body", 4.4F, 4.4F);
        this.head = new BoneLordPart(this, "head", 3.2F, 3.2F);
        this.parts = new BoneLordPart[]{body, head};
        // THE WHOLE CHASM IS HIS: he always knew who was in his hall, but no path came of it - the search gives up
        // at sixteen nodes a block of FOLLOW_RANGE, and the way over the bridges round the pillars to the far end of a
        // hall a hundred and twenty long is more than that. Four times the search, and the range the whole hall.
        getNavigation().setMaxVisitedNodesMultiplier(4.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 340.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 160.0D);
    }

    @Override
    protected void registerGoals() {
        // (no HurtByTargetGoal)
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DORMANT, true);
        entityData.define(SPRINTING, false);
        entityData.define(ARENA_MIN, BlockPos.ZERO);
        entityData.define(ARENA_MAX, BlockPos.ZERO);
        entityData.define(IMPACT, 0);
        entityData.define(ATTACK_YAW, 0.0F);
    }

    public boolean isDormant() {
        return entityData.get(DORMANT);
    }

    public boolean isBursting() {
        return entityData.get(SPRINTING);
    }

    /** The last blow that landed: (count << 8) | strength 1..255. */
    public int impactPacked() {
        return entityData.get(IMPACT);
    }

    /** Where his heap lay: the top of his own pillar, at the hall's heart (null until he has stood anywhere). */
    @Nullable
    public BlockPos home() {
        return home;
    }

    /** Its hall in the world (client too); null before it knows it. */
    @Nullable
    public AABB arenaBox() {
        BlockPos a = entityData.get(ARENA_MIN), b = entityData.get(ARENA_MAX);
        return a.equals(b) ? null : new AABB(a, b);
    }

    // ------------------------------------------------------------------------------------------------ his parts
    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (parts != null) {                                      // (not yet made, if anything asks that early)
            for (int i = 0; i < parts.length; i++) {
                parts[i].setId(id + i + 1);
            }
        }
    }

    /** Can his parts be struck now (not while he is bones, gathering, or dying)? */
    boolean partsLive() {
        return isAlive() && !isDeadOrDying() && !isDormant() && getAttackState() != ASSEMBLE;
    }

    /** A blow to one of his parts is a blow to him - to the skull a quarter harder. */
    boolean hurtByPart(BoneLordPart part, DamageSource source, float amount) {
        return hurt(source, part == head ? amount * 1.25F : amount);
    }

    /** Ticks into the state he is in: the server's own count, and the client's (counted from when it saw it). */
    public int stateTicks() {
        return level().isClientSide ? clientStateTicks : attackTicks;
    }

    /** The yaw his blows are laid out from (the body's otherwise). */
    public float attackYaw() {
        return getAttackState() == 0 ? yBodyRot : entityData.get(ATTACK_YAW);
    }

    private double[][] bodyTable() {
        return switch (getAttackState()) {
            case TRIP -> BoneLordFrames.BODY_TRIP;
            case DOWN -> BoneLordFrames.BODY_DOWN;
            case GETUP -> BoneLordFrames.BODY_GETUP;
            case GRAB -> BoneLordFrames.BODY_GRAB;
            case SLAM -> BoneLordFrames.BODY_SLAM;
            case THROW -> BoneLordFrames.BODY_THROW;
            case STOMP -> BoneLordFrames.BODY_STOMP;
            default -> BoneLordFrames.BODY_STAND;
        };
    }

    private void placeParts() {
        if (parts == null) {
            return;
        }
        double[] r = BoneLordFrames.row(bodyTable(), stateTicks());
        float yaw = attackYaw();
        Vec3 b = BoneLordFrames.at(this, yaw, r, 0);
        Vec3 h = BoneLordFrames.at(this, yaw, r, 3);
        movePart(body, b.x, b.y - body.getBbHeight() * 0.5D, b.z);
        movePart(head, h.x, h.y - head.getBbHeight() * 0.5D, h.z);
    }

    private static void movePart(BoneLordPart part, double x, double y, double z) {
        part.xo = part.getX();
        part.yo = part.getY();
        part.zo = part.getZ();
        part.xOld = part.xo;
        part.yOld = part.yo;
        part.zOld = part.zo;
        part.setPos(x, y, z);
    }

    /** Drawn as long as any of him is in sight: his arms reach six blocks out, his body lies out as far after a fall
     *  (the column of his hitbox alone would let him blink out of view while his fist was still in front of you). */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(6.5D, 2.0D, 6.5D);
    }

    // ------------------------------------------------------------------------------------------------ the boss bar
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
        AABB box = arenaBox();
        if (box != null && level() instanceof ServerLevel s) {
            for (ServerPlayer p : s.players()) {
                if (box.inflate(4.0D).contains(p.position())) {
                    bossEvent.addPlayer(p);
                }
            }
            for (ServerPlayer p : List.copyOf(bossEvent.getPlayers())) {
                if (!box.inflate(12.0D).contains(p.position())) {
                    bossEvent.removePlayer(p);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the client
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            int st = getAttackState();
            if (st != clientState) {
                clientState = st;
                clientStateTicks = 0;
            } else {
                clientStateTicks++;
            }
        }
        placeParts();
    }

    /** A footfall put down by the clip since this was last asked (0: none; 1 his +x foot, 2 the other). Client. */
    public int takeStep() {
        int s = stepFlag;
        stepFlag = 0;
        return s;
    }

    /** Ticks since the clip last put a foot down (a large number: he is not being drawn). Client. */
    public int ticksSinceClipStep() {
        return tickCount - lastClipStep;
    }

    // ------------------------------------------------------------------------------------------------ its hunt
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !(level() instanceof ServerLevel s)) {
            return;
        }
        if (home == null) {
            home = blockPosition();
        }
        // (a Lord saved before 09.10.2026 brought his old range of 72 back with him: his attributes are saved with him)
        var range = getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null && range.getBaseValue() < 160.0D) {
            range.setBaseValue(160.0D);
        }
        if (arenaBox() == null) {
            entityData.set(ARENA_MIN, home.offset(arena[0], arena[1], arena[2]));
            entityData.set(ARENA_MAX, home.offset(arena[3], arena[4], arena[5]));
        }
        tickBossBar();
        if (isDormant()) {
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            Player in = fighterInHall(s);
            if (in != null && !com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.holdsBack(this)) {
                assemble(in);
            }
            return;
        }
        // HIS LINE IN THE FIGHT, AT HALF HIS HEALTH: "You have earned the honor of dying before me." - once (BossVoice); it was
        // said as he got up from his first fall, which came long before half
        if (getHealth() <= getMaxHealth() * 0.5F && !isDeadOrDying()) {
            BossVoice.fightLine(this, "bone_lord");
        }
        // (fallen off - it should not, but a chasm is a chasm): back on its pillar
        if (home != null && getY() < home.getY() - 8) {
            teleportTo(home.getX() + 0.5D, home.getY() + 0.5D, home.getZ() + 0.5D);
            setDeltaMovement(Vec3.ZERO);
        }
        int st = getAttackState();
        if (st != SLAM && isVehicle()) {
            letGo();                                              // (nobody rides him but in the slam's fist)
        }
        switch (st) {
            case ASSEMBLE -> {
                stand();
                if (attackTicks >= ASSEMBLE_T) {
                    setAttackState(0);
                    playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 3.0F, 0.45F);
                }
            }
            case TRIP -> {
                stand();
                face();
                if (attackTicks == 4) {
                    playSound(SoundEvents.SKELETON_HURT, 3.0F, 0.4F);
                }
                if (attackTicks == TRIP_HIT) {                    // his fists and his chest on the bridge
                    playSound(FFSounds.GOLEM_SLAM.get(), 3.5F, 0.55F);
                    playSound(SoundEvents.BONE_BLOCK_BREAK, 3.0F, 0.5F);
                    thump(0.75F);
                    dust(s, BoneLordFrames.at(this, attackYaw(), BoneLordFrames.row(BoneLordFrames.BODY_TRIP, TRIP_HIT), 0),
                            3.0D);
                    // (no tenth of his health off it any more)
                }
                if (attackTicks >= TRIP_T) {
                    setAttackState(DOWN);
                }
            }
            case DOWN -> {
                stand();
                face();
                if (attackTicks % 14 == 3) {
                    playSound(SoundEvents.SKELETON_STEP, 2.0F, 0.4F);
                }
                if (attackTicks >= DOWN_T) {
                    setAttackState(GETUP);
                }
            }
            case GETUP -> {
                stand();
                face();
                if (attackTicks >= GETUP_T) {
                    setAttackState(0);
                }
            }
            case GRAB -> tickGrab(s);
            case SLAM -> tickSlam(s);
            case THROW -> tickThrow(s);
            case STOMP -> tickStomp(s);
            default -> chase(s);
        }
        tickThrown(s);
        trample(s);
        if (st != ASSEMBLE && --climbCd <= 0) {
            climbCd = CLIMB_EVERY + random.nextInt(CLIMB_EVERY / 2);
            sendClimber(s);
        }
        // nobody left in his hall (they fell, or rose at their shrine): back to his own pillar, not waiting at the door
        alone = fighterInHall(s) == null ? alone + 1 : 0;
        if (alone == ALONE_HOME && home != null && st != SLAM && distanceToSqr(Vec3.atBottomCenterOf(home)) > 9.0D) {
            goHome(s);
        }
    }

    /** Gone from where he stood in a burst of bone dust and back on his heap's pillar, standing, facing as he lay. */
    private void goHome(ServerLevel s) {
        letGo();
        setAttackState(0);
        getNavigation().stop();
        setTarget(null);
        burstLeft = 0;
        entityData.set(SPRINTING, false);
        s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.BONE_BLOCK
                .defaultBlockState()), getX(), getY() + 4.0D, getZ(), 60, 1.5D, 3.0D, 1.5D, 0.1D);
        Vec3 to = Vec3.atBottomCenterOf(home);
        teleportTo(to.x, to.y, to.z);
        setDeltaMovement(Vec3.ZERO);
        s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.BONE_BLOCK
                .defaultBlockState()), to.x, to.y + 4.0D, to.z, 60, 1.5D, 3.0D, 1.5D, 0.1D);
        playSound(SoundEvents.BONE_BLOCK_BREAK, 3.0F, 0.5F);
    }

    /** How many of the dead are about in his hall. */
    private int deadAbout(ServerLevel s) {
        AABB hall = arenaBox();
        return hall == null ? 0 : s.getEntitiesOfClass(FrostSkeletonEntity.class, hall, FrostSkeletonEntity::isAlive).size();
    }

    /**
     * One of the dead up a pillar's face near whoever he hunts: a cell on a pillar's top (stone under it four deep, not a
     * bridge's single course), free to stand in, five to eighteen blocks from them, with an open drop beside it for
     * CLIMB_FROM and more - its face is what the dead climbs.
     */
    private void sendClimber(ServerLevel s) {
        LivingEntity t = getTarget();
        AABB hall = arenaBox();
        if (t == null || !t.isAlive() || hall == null || home == null) {
            return;
        }
        java.util.List<FrostSkeletonEntity> climbers = s.getEntitiesOfClass(FrostSkeletonEntity.class, hall,
                m -> m.isAlive() && m.getPersistentData().getBoolean(CLIMBER));
        if (climbers.size() >= CLIMB_CAP
                || climbers.stream().filter(FrostSkeletonEntity::isClimbing).count() >= CLIMB_AT_ONCE) {
            return;
        }
        // IN FRONT OF THEM, where they will see it come up: the first tries are
        // within sixty degrees of where they look; only then anywhere round them
        double look = Math.atan2(t.getLookAngle().z, t.getLookAngle().x);
        net.minecraft.core.Direction[] sides = {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST};
        for (int tries = 0; tries < 40; tries++) {
            double a = tries < 28 ? look + (random.nextDouble() - 0.5D) * Math.toRadians(120.0D)
                    : random.nextDouble() * Math.PI * 2.0D;
            double r = 6.0D + random.nextDouble() * 14.0D;
            BlockPos ledge = BlockPos.containing(t.getX() + Math.cos(a) * r, home.getY(), t.getZ() + Math.sin(a) * r);
            if (climbers.stream().anyMatch(m -> {
                double dx = m.getX() - (ledge.getX() + 0.5D), dz = m.getZ() - (ledge.getZ() + 0.5D);
                return dx * dx + dz * dz < CLIMB_APART * CLIMB_APART;
            })) {
                continue;                                         // (apart from the others: one to a stretch of hall)
            }
            if (!hall.contains(Vec3.atCenterOf(ledge)) || !s.isEmptyBlock(ledge) || !s.isEmptyBlock(ledge.above())
                    || !s.getBlockState(ledge.below()).isFaceSturdy(s, ledge.below(), net.minecraft.core.Direction.UP)) {
                continue;
            }
            boolean pillar = true;
            for (int k = 2; k <= 5 && pillar; k++) {
                pillar = s.getBlockState(ledge.below(k)).isSolidRender(s, ledge.below(k));
            }
            if (!pillar) {
                continue;
            }
            int first = random.nextInt(4);
            for (int i = 0; i < 4; i++) {
                net.minecraft.core.Direction out = sides[(first + i) % 4];
                BlockPos col = ledge.relative(out);
                boolean open = s.isEmptyBlock(col) && s.isEmptyBlock(col.above());
                for (int k = 1; open && k <= CLIMB_FROM + 1; k++) {
                    open = s.isEmptyBlock(col.below(k));
                }
                if (!open) {
                    continue;
                }
                FrostSkeletonEntity m = FrostSkeletonEntity.climbUp(s, ledge, out, CLIMB_FROM, random.nextInt(5));
                if (m != null) {
                    m.getPersistentData().putBoolean(CLIMBER, true);
                    m.setTarget(t);
                }
                return;
            }
        }
    }

    /** WEBS UNDERFOOT:
     *  what it runs into it tears through - every cobweb in its stride is gone, with the sound of it. */
    private void trample(ServerLevel s) {
        if (isDormant() || getAttackState() == ASSEMBLE || getDeltaMovement().horizontalDistanceSqr() < 1.0E-3D) {
            return;
        }
        AABB box = getBoundingBox().inflate(0.8D, 0.0D, 0.8D).expandTowards(getDeltaMovement().multiply(3.0D, 0.0D, 3.0D));
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, Math.min(box.maxY, getY() + 3.0D), box.maxZ))) {
            BlockState st = s.getBlockState(p);
            if (st.is(net.minecraft.world.level.block.Blocks.COBWEB)) {
                s.destroyBlock(p, false, this);
            } else if (st.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock
                    && st.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
                // AND THE FIRES: his foot on
                // a fire puts it out - the hall the darker for every pillar he has crossed
                s.levelEvent(null, 1009, p, 0);                  // (the hiss of a fire put out)
                net.minecraft.world.level.block.CampfireBlock.dowse(this, s, p, st);
                s.setBlock(p, st.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false), 11);
                s.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D,
                        12, 0.3D, 0.2D, 0.3D, 0.02D);
            }
        }
    }

    /** A player who can be in a fight, in its hall. */
    @Nullable
    private Player fighterInHall(ServerLevel s) {
        AABB box = arenaBox();
        if (box == null) {
            return null;
        }
        Player best = null;
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : s.players()) {
            if (com.jastkub.frozenfortress.entity.boss.VelkharEntity.inTheFight(p) && box.contains(p.position())) {
                double d = p.distanceToSqr(this);
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
        }
        return best;
    }

    /** Its gate down a second (BossGateBlockEntity): it gathers itself now, for whoever is in its hall. */
    @Override
    public void gateShut() {
        if (!isDormant() || !isAlive() || !(level() instanceof ServerLevel s)) {
            return;
        }
        Player p = fighterInHall(s);
        if (p == null) {
            p = GateKeeper.shutInWith(this);
        }
        if (p != null) {
            assemble(p);
        }
    }

    private void assemble(Player who) {
        entityData.set(DORMANT, false);
        setAttackState(ASSEMBLE);
        setTarget(who);
        com.jastkub.frozenfortress.BossCutscenes.play(this, com.jastkub.frozenfortress.BossCutscenes.INTRO, ASSEMBLE_T + 8);
        playSound(SoundEvents.BONE_BLOCK_PLACE, 3.0F, 0.5F);
        playSound(SoundEvents.SKELETON_AMBIENT, 3.0F, 0.35F);
    }

    private void stand() {
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
    }

    /** Turned to a blow's yaw (and held there: tickHeadTurn). */
    private void lockYaw(float yaw) {
        entityData.set(ATTACK_YAW, yaw);
        face();
    }

    private void face() {
        float yaw = entityData.get(ATTACK_YAW);
        setYRot(yaw);
        yRotO = yaw;
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private float yawTo(LivingEntity t) {
        return (float) (Mth.atan2(t.getZ() - getZ(), t.getX() - getX()) * (180.0D / Math.PI)) - 90.0F;
    }

    /** Through a blow (and on the floor) his body stays where the blow faces - BodyRotationControl would turn it after
     *  his head (memory: geckolib-trigger-hold-pulapka). On the client too, from the synced yaw. */
    @Override
    protected float tickHeadTurn(float yRot, float amount) {
        int st = getAttackState();
        if (st != 0 && st != ASSEMBLE && !isDormant()) {
            float yaw = entityData.get(ATTACK_YAW);
            yBodyRot = yaw;
            yHeadRot = yaw;
            return amount;
        }
        return super.tickHeadTurn(yRot, amount);
    }

    /**
     * HE DOES NOT GO OFF THE BRIDGES: two and a half blocks wide on bridges three wide, his path cut the corners
     * of the crossings over the drop, and a blow's knockback could carry him over too. Whatever moves him, a step that
     * would put the middle of him over nothing is taken back - or only the part of it along the edge is kept, so he
     * slides round the corner instead of off it. A hole in a bridge is no edge: the bridge is round it.
     */
    @Override
    public void move(net.minecraft.world.entity.MoverType type, Vec3 by) {
        if (!level().isClientSide && onGround() && !isDeadOrDying() && by.horizontalDistanceSqr() > 1.0E-6D
                && footed(position()) && !footed(position().add(by.x, 0.0D, by.z))) {
            // THE NEAREST WAY THAT STAYS ON THE BRIDGE (with the
            // step and both its halves over the drop he was stopped dead, and his path, which runs a big thing round a
            // corner by its outside edge, pushed him the same way every tick: he stood): the step turned by fifteen
            // degrees, thirty, ... to ninety either way, the least turn that keeps his middle over the deck - round the
            // corner along its edge. Nothing at all only when there is no such way.
            Vec3 flat = new Vec3(by.x, 0.0D, by.z);
            Vec3 way = null;
            for (int k = 1; k <= 6 && way == null; k++) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    Vec3 turned = flat.yRot((float) Math.toRadians(15.0D * k * sign));
                    if (footed(position().add(turned.x, 0.0D, turned.z))) {
                        way = turned;
                        break;
                    }
                }
            }
            Vec3 v = getDeltaMovement();
            if (way != null) {
                by = new Vec3(way.x, by.y, way.z);
                setDeltaMovement(way.x, v.y, way.z);
            } else {
                by = new Vec3(0.0D, by.y, 0.0D);
                setDeltaMovement(0.0D, v.y, 0.0D);
                edgeStuck++;
            }
        }
        super.move(type, by);
    }

    /** Ticks in a row the edge has held him with nowhere to go: at forty, his path is laid again (chase). */
    private int edgeStuck;

    /** Is there something to stand on under the middle of him at `at` (the four cells round a half-block square)? */
    private boolean footed(Vec3 at) {
        for (double ox = -0.5D; ox <= 0.5D; ox += 1.0D) {
            for (double oz = -0.5D; oz <= 0.5D; oz += 1.0D) {
                BlockPos c = BlockPos.containing(at.x + ox, at.y - 0.5D, at.z + oz);
                if (!level().getBlockState(c).getCollisionShape(level(), c).isEmpty()
                        || !level().getBlockState(c.below()).getCollisionShape(level(), c.below()).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void chase(ServerLevel s) {
        LivingEntity t = getTarget();
        if (!(t instanceof Player) || !t.isAlive() || !com.jastkub.frozenfortress.entity.boss.VelkharEntity.inTheFight(t)
                || (arenaBox() != null && !arenaBox().inflate(3.0D).contains(t.position()))) {
            Player p = fighterInHall(s);
            setTarget(p);
            t = p;
        }
        if (t == null) {
            entityData.set(SPRINTING, false);
            if (home != null && distanceToSqr(Vec3.atCenterOf(home)) > 9.0D && tickCount % 20 == 0) {
                getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.0D);
            }
            return;
        }
        // the bursts: three seconds at a fifth over a sprinting player, every quarter minute or so
        if (burstLeft > 0) {
            if (--burstLeft == 0) {
                entityData.set(SPRINTING, false);
                burstCd = 240 + random.nextInt(140);
            }
        } else if (--burstCd <= 0) {
            burstLeft = 60;
            entityData.set(SPRINTING, true);
            playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 1.2F);
        }
        double mod = burstLeft > 0 ? BURST : 1.0D;
        if (edgeStuck >= 40) {
            edgeStuck = 0;                                         // held at an edge two seconds: a new path
            getNavigation().stop();
            getNavigation().recomputePath();
        }
        if (getDeltaMovement().horizontalDistanceSqr() > 0.01D) {
            edgeStuck = 0;
        }
        if (tickCount % 10 == 0 || getNavigation().isDone()) {
            boolean pathed = getNavigation().moveTo(t, mod);   // (every half second: a long search is not free)
            // NO WAY FOUND, AND THEY ARE RIGHT THERE: straight at them (the edge keeps him on the bridge - move)
            if (!pathed && distanceToSqr(t) < 12.0D * 12.0D) {
                getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), mod);
            }
        }
        getLookControl().setLookAt(t, 30.0F, 30.0F);
        // (his footfalls are the clients' own - BoneLordClient: the sound and the shake on the clip's foot-plant)
        double dx = t.getX() - getX(), dz = t.getZ() - getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        Vec3 fwd = Vec3.directionFromRotation(0.0F, yBodyRot);
        double ahead = flat < 1.0E-3D ? 1.0D : (dx * fwd.x + dz * fwd.z) / flat;
        boolean level = Math.abs(t.getY() - getY()) < 3.5D;
        --grabCd;
        --stompCd;
        // ---- close before him: the grab - or, as often, the foot
        if (level && flat < 5.4D && ahead > 0.3D) {
            boolean canGrab = grabCd <= 0 && flat > 2.2D;
            boolean canStomp = stompCd <= 0;
            if (canGrab && (!canStomp || random.nextFloat() < 0.55F)) {
                lockYaw(yawTo(t));
                setAttackState(GRAB);
                playSound(SoundEvents.SKELETON_AMBIENT, 3.0F, 0.35F);
                return;
            }
            if (canStomp) {
                lockYaw(yawTo(t));
                setAttackState(STOMP);
                stompCd = 70 + random.nextInt(50);
                playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 3.0F, 0.4F);
                return;
            }
        }
        // ---- the throw: you not too near, not too far
        if (--throwCd <= 0 && level && flat > 7.0D && flat < 30.0D) {
            lockYaw(yawTo(t));
            minion = null;
            setAttackState(THROW);
            throwCd = 140 + random.nextInt(90);
            return;
        }
        tripChance(s);
    }

    /**
     * IT TRIPS - when it is MADE to: no more of
     * itself at a run (a chance every tick had it down on the bridge every half minute, six seconds each - the chase
     * kept stopping), only now and then over a hole, on a hard turn at a burst, and when an arrow takes it in the legs
     * (hurt) - the falls the player makes.
     */
    private void tripChance(ServerLevel s) {
        Vec3 v = getDeltaMovement();
        double sp = v.horizontalDistanceSqr();
        if (sp < 0.03D) {
            overHole = false;
            return;
        }
        float chance = 0.0F;
        BlockPos under = BlockPos.containing(getX(), getY() - 0.5D, getZ());
        boolean hole = s.getBlockState(under).isAir() && s.getBlockState(under.below()).isAir();
        if (hole && !overHole) {
            chance += 0.15F;                                     // a hole under its stride: its foot goes in
        }
        overHole = hole;
        Vec3 heading = new Vec3(v.x, 0.0D, v.z).normalize();
        if (burstLeft > 0 && lastHeading.lengthSqr() > 0.0D && heading.dot(lastHeading) < 0.5D) {
            chance += 0.35F;                                     // a hard turn at a burst
        }
        if (tickCount % 10 == 0) {
            lastHeading = heading;
        }
        if (random.nextFloat() < chance) {
            trip();
        }
    }

    private void trip() {
        entityData.set(SPRINTING, false);
        burstLeft = 0;
        burstCd = 200;
        entityData.set(ATTACK_YAW, yBodyRot);
        setAttackState(TRIP);
    }

    /** A blow felt by every client near: power 0..1 (the stomp 1, the slam 0.9, his fall 0.75). */
    private void thump(float power) {
        impactSeq = (impactSeq + 1) & 0x7FFFFF;
        int p = Mth.clamp(Math.round(power * 255.0F), 1, 255);
        entityData.set(IMPACT, (impactSeq << 8) | p);
    }

    /** What the blow knocks up off the bridge: a garnish of its own stone (the shake and the blow are the point). */
    private void dust(ServerLevel s, Vec3 at, double spread) {
        BlockState floor = s.getBlockState(BlockPos.containing(at.x, getY() - 0.5D, at.z));
        if (floor.isAir()) {
            floor = s.getBlockState(blockPosition().below());
        }
        if (!floor.isAir()) {
            s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), at.x, getY() + 0.2D, at.z,
                    40, spread * 0.4D, 0.1D, spread * 0.4D, 0.25D);
        }
    }

    /** The bridge round a blow jumps: blocks of it thrown straight up and dropping back into their places. */
    private void heave(ServerLevel s, Vec3 centre, double radius, double force) {
        int placed = 0;
        int span = (int) Math.ceil(radius);
        for (int dx = -span; dx <= span && placed < 18; dx++) {
            for (int dz = -span; dz <= span && placed < 18; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius || random.nextFloat() > 0.55F) {
                    continue;
                }
                BlockPos at = BlockPos.containing(centre.x + dx, getY() - 0.5D, centre.z + dz);
                BlockState state = s.getBlockState(at);
                if (state.isAir() || !s.getBlockState(at.above()).isAir()) {
                    continue;
                }
                double falloff = 1.0D - (d / Math.max(1.0D, radius)) * 0.65D;
                s.addFreshEntity(new com.jastkub.frozenfortress.entity.effect.FallingDebrisEntity(s, state,
                        at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D,
                        0.0D, (0.30D + random.nextDouble() * 0.2D) * falloff * force, 0.0D, 26 + random.nextInt(14)));
                placed++;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the stomp
    private void tickStomp(ServerLevel s) {
        stand();
        face();
        if (attackTicks == 6) {
            playSound(SoundEvents.SKELETON_AMBIENT, 2.6F, 0.3F);
        }
        if (attackTicks == STOMP_HIT) {
            Vec3 foot = BoneLordFrames.at(this, attackYaw(), BoneLordFrames.STOMP_FOOT[0], 0);
            foot = new Vec3(foot.x, getY(), foot.z);
            playSound(FFSounds.GOLEM_STOMP.get(), 4.0F, 0.6F);
            playSound(FFSounds.GOLEM_SLAM.get(), 3.0F, 0.5F);
            playSound(SoundEvents.BONE_BLOCK_BREAK, 3.0F, 0.45F);
            thump(1.0F);
            heave(s, foot, 3.2D, 1.0D);
            dust(s, foot, 4.0D);
            s.addFreshEntity(new FrostWaveEntity(s, this, foot.x, getY(), foot.z, 5.5F, 12, 0.0F).harmless());
            for (Player p : s.getEntitiesOfClass(Player.class, new AABB(foot, foot).inflate(STOMP_FELT, 3.0D, STOMP_FELT),
                    com.jastkub.frozenfortress.entity.boss.VelkharEntity::inTheFight)) {
                if (!p.onGround() && p.getY() > getY() + 1.2D) {
                    continue;                                     // in the air over it: the floor jumps under nothing
                }
                Vec3 away = new Vec3(p.getX() - foot.x, 0.0D, p.getZ() - foot.z);
                double d = away.length();
                away = d < 1.0E-3D ? Vec3.directionFromRotation(0.0F, attackYaw()) : away.scale(1.0D / d);
                if (d <= STOMP_REACH) {
                    // struck and thrown: away from the foot, off the bridge if that is the way
                    float k = (float) (1.0D - d / STOMP_REACH * 0.5D);
                    p.hurt(damageSources().mobAttack(this), STOMP_DAMAGE * k);
                    // (the throw takes Pewny Krok and the Glacier Ring - 09.10.2026: it went round them both)
                    double st = com.jastkub.frozenfortress.registry.FFEnchantments.steady(p);
                    p.setDeltaMovement(away.x * 1.15D * k * st, 0.55D * st, away.z * 1.15D * k * st);
                    p.hurtMarked = true;
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1), this);
                } else if (d <= STOMP_FELT) {
                    // the bridge jumps under them: a stumble, nothing worse
                    double st = com.jastkub.frozenfortress.registry.FFEnchantments.steady(p);
                    p.setDeltaMovement(p.getDeltaMovement().x * 0.3D + away.x * 0.15D * st, 0.32D * st,
                            p.getDeltaMovement().z * 0.3D + away.z * 0.15D * st);
                    p.hurtMarked = true;
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0), this);
                }
            }
        }
        if (attackTicks >= STOMP_T) {
            setAttackState(0);
        }
    }

    // ------------------------------------------------------------------------------------------------ the grab
    private void tickGrab(ServerLevel s) {
        stand();
        face();
        LivingEntity t = getTarget();
        // (on the tick the clip's hand is on the bridge as it is DRAWN: LAG after the clip's own)
        if (attackTicks == GRAB_CATCH + LAG && t != null && t.isAlive() && held == null && !t.isPassenger()
                && com.jastkub.frozenfortress.entity.boss.VelkharEntity.inTheFight(t)) {
            Vec3 hand = BoneLordFrames.at(this, attackYaw(), BoneLordFrames.row(BoneLordFrames.GRAB_HAND, GRAB_CATCH), 0);
            Vec3 mid = t.position().add(0.0D, t.getBbHeight() * 0.5D, 0.0D);
            if (mid.distanceToSqr(hand) < 2.6D * 2.6D) {
                held = t.getUUID();
                playSound(SoundEvents.BONE_BLOCK_BREAK, 3.0F, 0.5F);
                setAttackState(SLAM);
                t.startRiding(this, true);                        // in his fist: positionRider has them from here
                t.fallDistance = 0.0F;
                return;
            }
        }
        if (attackTicks == 13 + LAG) {                            // missed: the hand slaps the bridge
            playSound(SoundEvents.BONE_BLOCK_HIT, 3.0F, 0.5F);
        }
        if (attackTicks >= GRAB_T + LAG) {
            grabCd = 40;
            setAttackState(0);
        }
    }

    /**
     * THE FIST THAT HOLDS YOU, on this side's tick of the slam - the server's count, the client's own. Whoever
     * he holds RIDES him (startRiding) and is put here every tick on both sides, the client drawing them between its
     * ticks as it draws any rider - no more a teleport a tick from the server, a jolt every twentieth of a second and a
     * fist three ticks ahead of the one drawn. Through GeckoLib's blend into the slam (LAG) the hand closes from where it
     * caught to where the clip begins; after it, the clip's own, LAG behind.
     */
    private Vec3 fist() {
        int k = stateTicks();
        double[] r;
        if (k >= LAG) {
            r = BoneLordFrames.row(BoneLordFrames.SLAM_HAND, k - LAG);
        } else {
            double[] a = BoneLordFrames.row(BoneLordFrames.GRAB_HAND, GRAB_CATCH), b = BoneLordFrames.row(BoneLordFrames.SLAM_HAND, 0);
            double f = k / (double) LAG;
            r = new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
        }
        return BoneLordFrames.at(this, attackYaw(), r, 0);
    }

    @Override
    protected void positionRider(net.minecraft.world.entity.Entity rider, net.minecraft.world.entity.Entity.MoveFunction move) {
        if (getAttackState() != SLAM) {
            super.positionRider(rider, move);
            return;
        }
        Vec3 at = fist();                                         // (the middle of them in the middle of it)
        move.accept(rider, at.x, Math.max(getY(), at.y - rider.getBbHeight() * 0.5D), at.z);
        rider.fallDistance = 0.0F;
    }

    /** Held, they hang in his fist - not sat in a saddle. */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** Nobody steers him from his fist. */
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return null;
    }

    /** Let go, they are where his fist left them (not stood off to one side of him). */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        return rider.position();
    }

    /** Is this one in his fist, and to stay there (BoneLordGrip refuses them a Shift off him)? */
    public boolean holds(net.minecraft.world.entity.Entity e) {
        return !releasing && isAlive() && !isDeadOrDying() && getAttackState() == SLAM && e.isAlive()
                && held != null && held.equals(e.getUUID());
    }

    /** His fist opens: whoever he holds is off him (the only way off, but for death - his or theirs). */
    public void letGo() {
        releasing = true;
        try {
            ejectPassengers();
        } finally {
            releasing = false;
        }
        held = null;
    }

    private void tickSlam(ServerLevel s) {
        stand();
        face();
        if (!(held != null && s.getEntity(held) instanceof LivingEntity v) || !v.isAlive() || v.getVehicle() != this) {
            letGo();
            if (attackTicks > 4 && attackTicks < SLAM_HIT + LAG) {
                setAttackState(0);
            } else if (attackTicks >= SLAM_T + LAG) {
                grabCd = 60;
                setAttackState(0);
            }
            return;
        }
        if (attackTicks == 13 + LAG) {                            // to his face: the roar
            playSound(SoundEvents.RAVAGER_ROAR, 4.0F, 0.5F);
            playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 3.0F, 0.35F);
        }
        if (attackTicks == SLAM_HIT + LAG) {
            letGo();                                              // (down where the fist met the bridge)
            v.fallDistance = 0.0F;
            // DASHED DOWN: the end of you - unless a totem is a second life (a blow, not /kill: the totem may answer)
            Vec3 at = BoneLordFrames.at(this, attackYaw(), BoneLordFrames.row(BoneLordFrames.SLAM_HAND, SLAM_HIT), 0);
            playSound(FFSounds.GOLEM_SLAM.get(), 4.5F, 0.55F);
            playSound(SoundEvents.ANVIL_LAND, 3.0F, 0.5F);
            playSound(SoundEvents.SKELETON_DEATH, 2.4F, 0.4F);
            thump(0.9F);
            heave(s, at, 2.4D, 0.8D);
            dust(s, at, 3.0D);
            v.invulnerableTime = 0;
            v.hurt(com.jastkub.frozenfortress.FFDamage.boneLordFist(level(), this), 1000.0F);   // (armour spared)
            if (v.isAlive()) {
                Vec3 away = Vec3.directionFromRotation(0.0F, attackYaw()).scale(1.2D);
                v.setDeltaMovement(away.x, 0.5D, away.z);
                v.hurtMarked = true;
                v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 100, 0), this);
            }
            held = null;
        }
    }

    // ------------------------------------------------------------------------------------------------ the throw
    /**
     * WHAT HE THROWS. It used to
     * need one of the risen dead already within seven blocks of him - and the dead get up only where a player has
     * passed, and walk off after him, so there almost never was one, and the throw never came. Now his hand, down on
     * the bridge, takes whatever is there: one already up (near his hand), else one of the corpses lying on the bridge
     * (it wakes in his fist), else he tears one out of the bones of his own heap - as long as his hall does not already
     * crawl with them (MAX_DEAD).
     */
    @Nullable
    private FrostSkeletonEntity takeMinion(ServerLevel s, Vec3 hand) {
        FrostSkeletonEntity best = null;
        double bd = 6.0D * 6.0D;
        for (FrostSkeletonEntity m : s.getEntitiesOfClass(FrostSkeletonEntity.class, new AABB(hand, hand).inflate(6.0D),
                FrostSkeletonEntity::isAlive)) {
            double d = m.distanceToSqr(hand);
            if (d < bd && !m.getUUID().equals(thrown)) {
                bd = d;
                best = m;
            }
        }
        if (best != null) {
            return best;
        }
        // one of the dead on the bridge, woken in his hand
        BlockPos c = BlockPos.containing(hand);
        double sd = Double.MAX_VALUE;
        com.jastkub.frozenfortress.block.entity.CitadelStatueBlockEntity corpse = null;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-6, -3, -6), c.offset(6, 2, 6))) {
            if (s.getBlockEntity(q) instanceof com.jastkub.frozenfortress.block.entity.CitadelStatueBlockEntity st
                    && st.isRemains() && !st.isRemoved()) {
                double d = q.distSqr(c);
                if (d < sd) {
                    sd = d;
                    corpse = st;
                }
            }
        }
        if (corpse != null) {
            BlockPos at = corpse.getBlockPos().immutable();
            if (corpse.awakenNow(s)) {
                for (FrostSkeletonEntity m : s.getEntitiesOfClass(FrostSkeletonEntity.class, new AABB(at).inflate(1.5D),
                        FrostSkeletonEntity::isAlive)) {
                    return m;
                }
            }
        }
        // torn out of his own heap
        if (deadAbout(s) >= MAX_DEAD) {
            return null;
        }
        FrostSkeletonEntity m = FFEntities.FROST_SKELETON.get().create(s);
        if (m == null) {
            return null;
        }
        m.moveTo(hand.x, Math.max(getY(), hand.y - 0.9D), hand.z, random.nextFloat() * 360.0F, 0.0F);
        m.finalizeSpawn(s, s.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
        s.addFreshEntity(m);
        playSound(SoundEvents.BONE_BLOCK_BREAK, 2.6F, 0.7F);
        return m;
    }

    private int thrownAge = -1;
    @Nullable
    private UUID thrown;

    private void tickThrow(ServerLevel s) {
        stand();
        face();
        LivingEntity t = getTarget();
        if (attackTicks == THROW_PICK + LAG) {
            Vec3 hand = BoneLordFrames.at(this, attackYaw(), BoneLordFrames.row(BoneLordFrames.THROW_HAND, THROW_PICK), 0);
            FrostSkeletonEntity m = takeMinion(s, hand);
            if (m == null) {
                throwCd = 60;
                setAttackState(0);
                return;
            }
            minion = m.getUUID();
            playSound(SoundEvents.SKELETON_HURT, 2.4F, 0.7F);
        }
        if (attackTicks < THROW_PICK + LAG) {
            return;
        }
        if (!(minion != null && s.getEntity(minion) instanceof FrostSkeletonEntity m) || !m.isAlive()) {
            minion = null;
            if (attackTicks >= THROW_T + LAG) {
                setAttackState(0);
            }
            return;
        }
        if (attackTicks < THROW_LET + LAG) {                             // in his fist: up from the bridge, overhead, back
            Vec3 hand = BoneLordFrames.at(this, attackYaw(), BoneLordFrames.row(BoneLordFrames.THROW_HAND, attackTicks - LAG), 0);
            m.setPos(hand.x, Math.max(getY(), hand.y - m.getBbHeight() * 0.5D), hand.z);
            m.setDeltaMovement(Vec3.ZERO);
            m.fallDistance = 0.0F;
            m.hurtMarked = true;
        }
        if (attackTicks == THROW_LET + LAG) {
            Vec3 from = BoneLordFrames.at(this, attackYaw(), BoneLordFrames.row(BoneLordFrames.THROW_HAND, THROW_LET), 0);
            Vec3 to = t != null && t.isAlive() ? t.position().add(t.getDeltaMovement().scale(10.0D)).add(0.0D, 0.6D, 0.0D)
                    : from.add(Vec3.directionFromRotation(0.0F, attackYaw()).scale(16.0D)).add(0.0D, -from.y + getY(), 0.0D);
            Vec3 start = new Vec3(from.x, from.y - m.getBbHeight() * 0.5D, from.z);
            m.trip();                                             // (tumbling: nothing of its own in the air)
            m.setPos(start.x, start.y, start.z);
            m.setDeltaMovement(arc(start, to));
            m.hurtMarked = true;
            m.fallDistance = 0.0F;
            thrown = m.getUUID();
            thrownAge = 0;
            minion = null;
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.4F);
            // and it screams all the way (the sound goes with it)
            s.playSound((Player) null, m, FFSounds.BONE_LORD_THROWN_SCREAM.get(), SoundSource.HOSTILE, 2.2F,
                    0.92F + random.nextFloat() * 0.16F);
        }
        if (attackTicks >= THROW_T + LAG) {
            setAttackState(0);
        }
    }

    /**
     * The velocity that carries a thrown body from `from` to `to` under the game's own fall: each tick it moves, then
     * falls 0.08 and keeps 0.98 of its rise and 0.91 of its going (a living thing in the air). The flight takes longer
     * the further it goes - a high arc.
     */
    static Vec3 arc(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z;
        double flat = Math.max(0.5D, Math.sqrt(dx * dx + dz * dz));
        int n = Mth.clamp((int) Math.round(12.0D + flat * 0.55D), 12, 32);
        double reach = (1.0D - Math.pow(0.91D, n)) / 0.09D;
        double v = flat / reach;
        double a = 0.0D, b = 0.0D, ak = 1.0D, bk = 0.0D;
        for (int k = 0; k < n; k++) {
            a += ak;
            b += bk;
            ak *= 0.98D;
            bk = (bk - 0.08D) * 0.98D;
        }
        double vy = (to.y - from.y - b) / a;
        return new Vec3(dx / flat * v, Mth.clamp(vy, -1.5D, 2.2D), dz / flat * v);
    }

    /** One of its own in the air: whoever it hits is knocked back (and over the edge, if that is where they stood). */
    private void tickThrown(ServerLevel s) {
        if (thrown == null || thrownAge < 0) {
            return;
        }
        if (++thrownAge > 60 || !(s.getEntity(thrown) instanceof FrostSkeletonEntity m) || !m.isAlive()) {
            thrown = null;
            thrownAge = -1;
            return;
        }
        if (thrownAge > 2 && m.onGround()) {
            m.playSound(SoundEvents.BONE_BLOCK_BREAK, 2.0F, 0.8F);
            thrown = null;
            thrownAge = -1;
            return;
        }
        Vec3 v = m.getDeltaMovement();
        AABB swept = m.getBoundingBox().expandTowards(-v.x, -v.y, -v.z).inflate(0.5D);
        for (Player p : s.getEntitiesOfClass(Player.class, swept,
                com.jastkub.frozenfortress.entity.boss.VelkharEntity::inTheFight)) {
            Vec3 push = new Vec3(v.x, 0.0D, v.z);
            push = push.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0.0F, attackYaw()) : push.normalize();
            p.hurt(damageSources().mobProjectile(m, this), 6.0F);
            p.setDeltaMovement(push.x * 1.3D, 0.45D, push.z * 1.3D);
            p.hurtMarked = true;
            playSound(SoundEvents.BONE_BLOCK_BREAK, 2.4F, 0.9F);
            m.setDeltaMovement(v.scale(-0.2D).add(0.0D, 0.2D, 0.0D));
            thrown = null;
            thrownAge = -1;
            return;
        }
    }

    // ------------------------------------------------------------------------------------------------ damage
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isDormant() || getAttackState() == ASSEMBLE) {
            return false;                                         // still bones, still gathering
        }
        int st = getAttackState();
        if (st == DOWN || st == TRIP || st == GETUP) {
            amount *= 1.5F;                                       // open on the floor
        } else if (st == 0 && !level().isClientSide && source.getDirectEntity() instanceof Projectile
                && getDeltaMovement().horizontalDistanceSqr() > 0.03D && random.nextFloat() < 0.4F) {
            trip();                                               // an arrow in the legs, at a run
        }
        return super.hurt(source, amount);
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motion) {
        // cobwebs, powder snow: nothing holds it
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public void die(DamageSource source) {
        bossEvent.removeAllPlayers();
        letGo();                                                  // (not down into the chasm with him)
        super.die(source);
        if (level() instanceof ServerLevel s) {
            com.jastkub.frozenfortress.block.entity.FrostHeartBlockEntity.keeperFell(level(), position());
            // and his way out rises
            com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.keeperFell(level(), this, 8);
            playSound(SoundEvents.WITHER_SKELETON_DEATH, 4.0F, 0.4F);
        }
    }

    /** ITS END: over backwards, off the bridge, down into the chasm, screaming. */
    @Override
    protected void tickDeath() {
        deathTime++;
        setNoGravity(false);
        Vec3 back = Vec3.directionFromRotation(0.0F, getYRot()).scale(-1.0D);
        if (deathTime < 18) {
            setDeltaMovement(back.x * 0.12D, getDeltaMovement().y, back.z * 0.12D);
        } else {
            noPhysics = true;                                     // nothing of the bridge holds it now
            setDeltaMovement(back.x * 0.08D, -0.06D * (deathTime - 17), back.z * 0.08D);
            if (deathTime == 18) {
                playSound(SoundEvents.WITHER_SKELETON_HURT, 4.0F, 0.35F);
                playSound(SoundEvents.PHANTOM_DEATH, 4.0F, 0.4F);
            }
        }
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        if (deathTime >= getDeathDuration() && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected int getDeathDuration() {
        return 70;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return isDormant() ? null : SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 0.4F;
    }

    @Override
    protected float getSoundVolume() {
        return 2.5F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    // ------------------------------------------------------------------------------------------------ saving
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Dormant", isDormant());
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
        if (tag.contains("Arena") && tag.getIntArray("Arena").length == 6) {
            arena = tag.getIntArray("Arena");
        }
        if (tag.contains("Home") && tag.getIntArray("Home").length == 3) {
            int[] h = tag.getIntArray("Home");
            home = new BlockPos(h[0], h[1], h[2]);
        }
    }

    // ------------------------------------------------------------------------------------------------ animation
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<BoneLordEntity> main = new AnimationController<>(this, "main", 3, state -> {
            if (isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (isDormant()) {
                return state.setAndContinue(DORMANT_ANIM);
            }
            switch (getAttackState()) {
                case ASSEMBLE: return state.setAndContinue(ASSEMBLE_ANIM);
                case TRIP: return state.setAndContinue(TRIP_ANIM);
                case DOWN: return state.setAndContinue(DOWN_ANIM);
                case GETUP: return state.setAndContinue(GETUP_ANIM);
                case GRAB: return state.setAndContinue(GRAB_ANIM);
                case SLAM: return state.setAndContinue(SLAM_ANIM);
                case THROW: return state.setAndContinue(THROW_ANIM);
                case STOMP: return state.setAndContinue(STOMP_ANIM);
                default: break;
            }
            if (state.isMoving()) {
                return state.setAndContinue(isBursting() ? SPRINT_ANIM : RUN);
            }
            return state.setAndContinue(IDLE);
        });
        // HIS FOOTFALLS: the clips mark each foot coming down (sound_effects "step_r" / "step_l"); BoneLordClient turns
        // them into the thud and the shake - on the very frame the foot lands, at any speed
        main.setSoundKeyframeHandler(event -> {
            stepFlag = event.getKeyframeData().getSound().endsWith("_l") ? 2 : 1;
            lastClipStep = tickCount;
        });
        controllers.add(main);
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && !isDormant() && hurtTime > 0 && !isAttacking()
                        ? state.setAndContinue(HURT) : PlayState.STOP));
    }
}
