package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.block.entity.FrostHeartBlockEntity;
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
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * LODOWY TUR - THE ICE AUROCHS, the miniboss of Serce Mrozu, the Frost Heart hall. A great wild ox of ice: black-blue
 * hide, a hump over the shoulders, a ridge of crystals grown out of his back, two great horns of clear ice.
 *
 * <p>THE HALL. The heart in its middle (FrostHeartBlockEntity, mode "fires") freezes whoever is not by one of its
 * four hearths; all four burning lay it open to be broken. The Tur is its keeper: HE HATES FIRE.
 *
 * <p>His fight - every blow told before it lands, every blow and every effect a thing with a body (no particles
 * carry anything; they only ride along):
 * <ol>
 *   <li>CHARGE (Szarza), his signature: the tell is unmistakable - he scrapes the floor with a forehoof twice
 *   (fx_aurochs_scrape), drops his horns level and snorts a blast of frost (fx_aurochs_snort); the line is fixed four
 *   ticks after the snort and six later he goes. He charges LIT HEARTHS and tramples them out (the campfire's LIT
 *   goes false, a hiss, steam and rime boiling off it: fx_aurochs_steam) - anyone warming himself there is in the
 *   way - and he charges people. Whoever is in his path takes a heavy blow and is thrown aside.</li>
 *   <li>THE PILLAR: a charge that runs into one of the hall's free-standing pillars after a real run-up breaks on it.
 *   He crashes (fx_aurochs_crash), goes down on his knees, dazed, stars of ice wheeling over his head
 *   (fx_aurochs_dizzy) - four seconds in which every blow on him lands half again as hard. A wall only jars him.</li>
 *   <li>HORN TOSS (Rzut Rogami): close in front - head down and swung to his right (the tell), then hooked up and
 *   left: flung into the air (fx_aurochs_toss).</li>
 *   <li>STOMP (Tapniecie): he rears up on his hind legs, forehooves pawing the air (the tell), and brings them down:
 *   a ring of ice spikes runs out over the floor from his hooves (IceAurochsRingEntity) - jump it, outrun it, or put
 *   a pillar between.</li>
 *   <li>BACK-KICK (Kopniecie): whoever hugs his flank or his rump - his weight goes onto the forehand, the head down,
 *   the hind legs drawn under him (the tell), then both hind hooves out behind (fx_aurochs_kick).</li>
 * </ol>
 * Below half his health he bellows and the rage-ice grows out of his back: he charges sooner and faster, his tell is
 * shortened to one scrape and the snort, his stomp sends two rings.
 *
 * <p>HE NEVER CHARGES THE HEART: no line of his passes within three and a half blocks of it, and a charge that finds
 * it ahead pulls up short. He does not need it to be broken to die, nor does it need him dead to break: the hall's
 * gates wait for both. Once the heart is broken, the hearths are nothing to him any more.
 *
 * <p>He sleeps until somebody comes into his room (Dormant, set by the citadel), his first block is his home and the
 * room is his tether; with nobody left alive in there he goes back to his place and sleeps, whole again.
 */
public class IceAurochsEntity extends FrostServantEntity implements GateKeeper {

    private static final EntityDataAccessor<Boolean> DORMANT =
            SynchedEntityData.defineId(IceAurochsEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RAGE =
            SynchedEntityData.defineId(IceAurochsEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int AWAKEN = 1, PAW = 2, PAW_QUICK = 3, CHARGE = 4, SKID = 5, CRASH = 6, STUNNED = 7,
            RECOVER = 8, BONK = 9, TOSS = 10, STOMP = 11, KICK = 12, BELLOW = 13, INTRO = 14;
    /** HIS ENTRANCE (tools/gen_ice_aurochs.py INTRO_*: the "intro" clip - change one, change both; BossScenes cuts its
     *  shots to them): up off the frost, the rime shaken off, two scrapes of the hoof, a snort, a stamp - the bellow. */
    public static final int INTRO_T = 132, INTRO_EYES = 6, INTRO_SHED = 34, INTRO_SNORT = 64, INTRO_STAMP = 84,
            INTRO_BELLOW = 88;
    public static final int[] INTRO_PAWS = {46, 56};

    // ---- the beats of his clips (tools/gen_ice_aurochs.py - change one, change both)
    static final int AWAKEN_T = 50, AWAKEN_SHED = 32, AWAKEN_BELLOW = 38;
    static final int PAW_T = 34, PAW_SCRAPE_1 = 4, PAW_SCRAPE_2 = 14, PAW_SNORT = 24, PAW_AIM = 28;
    static final int PAWQ_T = 20, PAWQ_SCRAPE = 3, PAWQ_SNORT = 11, PAWQ_AIM = 15;
    static final int SKID_T = 16, CRASH_T = 14, STUN_T = 80, RECOVER_T = 20, BONK_T = 20;
    static final int TOSS_T = 30, TOSS_HIT = 14;
    static final int KICK_T = 26, KICK_HIT = 12;
    static final int STOMP_T = 50, STOMP_SLAM = 24, STOMP_SECOND = 36;
    static final int BELLOW_T = 40, BELLOW_ROAR = 12;
    /** HIS DEATH (tools/gen_ice_aurochs.py DEATH_*: the "death" clip - change one, change both; BossScenes' death film
     *  is cut to them): reared up, a scrape of the hoof as if to charge (SCRAPE), down on his knees (KNEEL), a last
     *  bellow (BELLOW), over onto his side (FALL), the light gone out of his eyes (EYES) - and the ice of him breaking
     *  apart (SHATTER: fx_aurochs_shatter). The clip runs DEATH_LAG behind his deathTime: the controller blends into
     *  it first. */
    public static final int DEATH_T = 84, DEATH_SHATTER = 76, DEATH_SCRAPE = 14, DEATH_KNEEL = 24, DEATH_BELLOW = 32,
            DEATH_FALL = 56, DEATH_EYES = 66, DEATH_LAG = 4;

    // ---- the numbers of his fight
    static final float CHARGE_DMG = 13.0F, TOSS_DMG = 10.0F, KICK_DMG = 11.0F;
    /** Blocks a tick: a charge crosses twenty blocks in a second and a quarter (a second, enraged). */
    static final double CHARGE_SPEED = 0.78D, CHARGE_SPEED_RAGE = 0.92D;
    /** The longest a charge runs, in ticks. */
    static final int CHARGE_MAX = 34;
    /** How far past the spot he aimed at he runs before he pulls up (at a person; at a hearth he stops on it). */
    static final double OVERRUN = 4.0D;
    /** The run-up a pillar needs to break him: less, and he only shoulders it. (Six, not five) */
    static final double CHARGE_MIN_RUN = 6.0D;
    /** How far ahead of his middle the horns and the brow reach while he charges (the model: 3.1 blocks). */
    static final double NOSE = 3.0D;
    /** No line of his passes nearer the heart than this; a charge that finds it this near ahead pulls up. */
    static final double HEART_CLEAR = 3.5D;
    /** Nearer than this to his forehooves (1.8 blocks before his middle), a burning hearth is trampled out. */
    static final double DOUSE_R = 1.3D;
    static final double TOSS_R = 5.0D, KICK_R = 4.6D;
    static final double WAKE = 14.0D, LEASH = 22.0D;
    /** With nobody in his room this long, he goes back to his place to sleep. */
    static final int GIVE_UP = 300;
    /** A pillar once broke his charge: this long he will not charge anyone down a line with a pillar on it. */
    static final int WARY = 120;            // (07.10.2026: was 300 - with the hall's eight pillars he stood fifteen seconds at one, every line barred to him)
    /** Damage while he is down on his knees. */
    static final float STUN_TAKEN = 1.5F;

    private static final String P = "animation.ice_aurochs.";
    private static final RawAnimation DORMANT_ANIM = RawAnimation.begin().thenLoop(P + "dormant");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation AWAKEN_ANIM = RawAnimation.begin().thenPlay(P + "awaken");
    private static final RawAnimation INTRO_ANIM = RawAnimation.begin().thenPlay(P + "intro");
    private static final RawAnimation PAW_ANIM = RawAnimation.begin().thenPlay(P + "paw");
    private static final RawAnimation PAWQ_ANIM = RawAnimation.begin().thenPlay(P + "paw_quick");
    private static final RawAnimation CHARGE_ANIM = RawAnimation.begin().thenLoop(P + "charge");
    private static final RawAnimation SKID_ANIM = RawAnimation.begin().thenPlay(P + "skid");
    private static final RawAnimation CRASH_ANIM = RawAnimation.begin().thenPlay(P + "crash");
    private static final RawAnimation STUNNED_ANIM = RawAnimation.begin().thenLoop(P + "stunned");
    private static final RawAnimation RECOVER_ANIM = RawAnimation.begin().thenPlay(P + "recover");
    private static final RawAnimation BONK_ANIM = RawAnimation.begin().thenPlay(P + "bonk");
    private static final RawAnimation TOSS_ANIM = RawAnimation.begin().thenPlay(P + "toss");
    private static final RawAnimation STOMP_ANIM = RawAnimation.begin().thenPlay(P + "stomp");
    private static final RawAnimation KICK_ANIM = RawAnimation.begin().thenPlay(P + "kick");
    private static final RawAnimation BELLOW_ANIM = RawAnimation.begin().thenPlay(P + "bellow");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay(P + "hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay(P + "death");

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.frozen_dominion.ice_aurochs"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);

    /** His first block: where he sleeps and goes back to. */
    @Nullable
    private BlockPos home;
    /** The heart of his hall, once found (FrostHeartBlockEntity.SERVER); its room is his. */
    @Nullable
    private BlockPos heartPos;
    @Nullable
    private AABB room;
    private final List<BlockPos> hearths = new ArrayList<>();
    private boolean secondHalf;
    private boolean bellowNow;
    // ---- the charge in hand
    /** What the tell is aimed at: a person (aimEntity) or a hearth (aimFire), its point updated until the line is fixed. */
    @Nullable
    private LivingEntity aimEntity;
    @Nullable
    private BlockPos aimFire;
    private Vec3 aimAt = Vec3.ZERO;
    private Vec3 line = new Vec3(0.0D, 0.0D, 1.0D);
    private Vec3 chargeFrom = Vec3.ZERO;
    /** Where he pulls up: past the person, on the hearth. */
    private Vec3 chargeEnd = Vec3.ZERO;
    private final Set<UUID> struck = new HashSet<>();
    private Vec3 impact = Vec3.ZERO;
    // ---- the walk to a place he can charge a hearth from
    @Nullable
    private Vec3 launch;
    @Nullable
    private BlockPos launchFire;
    private int launchTicks;
    /** Ticks he waits after an attack before the next; the attack goal counts them down. */
    int rest;
    int wary;
    private int emptyRoom;

    public IceAurochsEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 140;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.STEP_HEIGHT, 1.0D)   // (1.21: was setMaxUpStep) over a hearth, a plinth, a step - he is huge
                .add(Attributes.MAX_HEALTH, 280.0D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 3.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DORMANT, true);
        builder.define(RAGE, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new AurochsAttackGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                p -> inRoom(p.position())));
    }

    // ------------------------------------------------------------------------------------------------ what he is
    public boolean isDormant() {
        return entityData.get(DORMANT);
    }

    /** His second half: the rage-ice is out of his back (IceAurochsRenderer shows the "rage" bone). */
    public boolean isEnraged() {
        return entityData.get(RAGE);
    }

    /** Down on his knees after a pillar broke his charge (the window). */
    public boolean isStunned() {
        int st = getAttackState();
        return st == CRASH || st == STUNNED;
    }

    @Override
    public boolean isPushable() {
        return false;                                          // a wall of ice and hide: nobody shoves him
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    // ------------------------------------------------------------------------------------------------ his hall
    /**
     * Finds the heart whose room he is in, and the hearths of that room (looked for again, cheaply, until found -
     * a heart only joins FrostHeartBlockEntity.SERVER once its chunk has ticked it).
     */
    private void findHall() {
        if (home == null) {
            return;
        }
        if (heartPos == null && tickCount % 20 == 0) {
            Vec3 h = Vec3.atBottomCenterOf(home).add(0.0D, 0.5D, 0.0D);
            for (FrostHeartBlockEntity heart : List.copyOf(FrostHeartBlockEntity.SERVER)) {
                if (!heart.isRemoved() && heart.getLevel() == level() && heart.room().contains(h)) {
                    heartPos = heart.getBlockPos();
                    room = heart.room();
                    scanHearths();
                    break;
                }
            }
        }
        if (room == null) {
            room = new AABB(home).inflate(LEASH, 4.0D, LEASH).expandTowards(0.0D, 6.0D, 0.0D);
            scanHearths();
        }
    }

    @Nullable
    private FrostHeartBlockEntity heart() {
        return heartPos != null && level().getBlockEntity(heartPos) instanceof FrostHeartBlockEntity h ? h : null;
    }

    /** Is the heart still beating - and so are the hearths still worth putting out? */
    private boolean heartBeats() {
        FrostHeartBlockEntity h = heart();
        return h != null && h.active();
    }

    private void scanHearths() {
        hearths.clear();
        if (room == null || home == null) {
            return;
        }
        int y0 = home.getY() - 1, y1 = home.getY() + 2;
        for (BlockPos q : BlockPos.betweenClosed((int) Math.floor(room.minX), y0, (int) Math.floor(room.minZ),
                (int) Math.floor(room.maxX) - 1, y1, (int) Math.floor(room.maxZ) - 1)) {
            if (level().getBlockState(q).getBlock() instanceof CampfireBlock) {
                hearths.add(q.immutable());
            }
        }
    }

    public boolean inRoom(Vec3 p) {
        return room == null || room.contains(p.x, Mth.clamp(p.y, room.minY + 0.1D, room.maxY - 0.1D), p.z);
    }

    private boolean lit(BlockPos p) {
        BlockState s = level().getBlockState(p);
        return s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT);
    }

    private List<BlockPos> litHearths() {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : hearths) {
            if (lit(p)) {
                out.add(p);
            }
        }
        return out;
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
        findHall();
        if (tickCount % 200 == 0) {
            scanHearths();                                    // (a hearth broken or placed since)
        }
        if (isDormant()) {
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            if (tickCount % 5 == 0) {
                Player near = nearestFoe(WAKE);
                if (near != null && !com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.holdsBack(this)) {
                    awaken(near);
                }
            }
        } else {
            if (wary > 0) {
                wary--;
            }
            beats();
            tether();
        }
        tickBossBar();
    }

    @Nullable
    private Player nearestFoe(double reach) {
        Player best = null;
        double bd = Double.MAX_VALUE;
        for (Player p : level().players()) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) {
                continue;
            }
            boolean in = (heartPos != null ? inRoom(p.position())
                    : p.distanceToSqr(this) < reach * reach && Math.abs(p.getY() - getY()) < 6.0D)
                    && (!isDormant() || com.jastkub.frozenfortress.BossCutscenes.witness(this, p));          // (woken only by one it can see)
            double d = p.distanceToSqr(this);
            if (in && d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }


    /** Its gate down a second (BossGateBlockEntity): it wakes now, for whoever it shut in with it. */
    @Override
    public void gateShut() {
        if (!isDormant() || !isAlive()) {
            return;
        }
        net.minecraft.world.entity.player.Player p = GateKeeper.shutInWith(this);
        awaken(p);
    }

    private void awaken(@Nullable LivingEntity by) {
        entityData.set(DORMANT, false);
        // his entrance, once for each who sees it (BossCutscenes), shot as a film: up off the frost, pawing, the
        // bellow - or, when everyone here has seen it (and every time he wakes again), the short waking as before
        introYaw = com.jastkub.frozenfortress.BossCutscenes.postYaw(this);
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        setAttackState(com.jastkub.frozenfortress.BossCutscenes.intro(this, INTRO_T) ? INTRO : AWAKEN);
        emptyRoom = 0;
        rest = 20;
        if (by != null) {
            setTarget(by);
        }
    }

    /** Which way he faces through his entrance: his post's. */
    private float introYaw;

    /** His entrance's beats (the clip's: the scrapes and the snort where the paw's are), him kept in his place. */
    private void introBeats(int t) {
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        if (t == INTRO_EYES) {
            playSound(FFSounds.ICE_AUROCHS_SNORT.get(), 0.8F, 0.75F);       // a slow breath as the eyes open
        }
        if (t == 16 || t == 28) {
            playSound(FFSounds.ICE_AUROCHS_STEP.get(), 1.4F, 0.8F);         // up behind, then before
        }
        if (t == INTRO_SHED) {
            fx("aurochs_shed", position(), 20);
            playSound(FFSounds.ICE_AUROCHS_SCRAPE.get(), 1.2F, 1.3F);
            playSound(FFSounds.ICE_SHATTER.get(), 0.9F, 1.2F);
        }
        for (int pw : INTRO_PAWS) {
            if (t == pw) {
                playSound(FFSounds.ICE_AUROCHS_SCRAPE.get(), 1.8F, 0.95F + random.nextFloat() * 0.1F);
                fx("aurochs_scrape", position(), 16);
            }
        }
        if (t == INTRO_SNORT) {
            playSound(FFSounds.ICE_AUROCHS_SNORT.get(), 2.2F, 1.0F);
            fx("aurochs_snort", position(), 18);
        }
        if (t == INTRO_STAMP) {
            playSound(FFSounds.ICE_AUROCHS_STEP.get(), 2.2F, 0.7F);
        }
        if (t == INTRO_BELLOW) {
            playSound(FFSounds.ICE_AUROCHS_BELLOW.get(), 3.0F, 1.0F);
        }
        if (t >= INTRO_T) {
            finish(10);
        }
    }

    /**
     * HIS ROOM IS HIS TETHER. Nobody alive in it for fifteen seconds: he walks back to his place, lies down and
     * sleeps, whole again - the room as it was for whoever comes next. Out of it (thrown, pushed): he walks back in.
     */
    private void tether() {
        if (home == null || isAttacking()) {
            return;
        }
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || !inRoom(t.position()))) {
            setTarget(null);
            t = null;
        }
        if (nearestFoe(LEASH) == null) {
            emptyRoom++;
        } else {
            emptyRoom = 0;
        }
        Vec3 h = Vec3.atBottomCenterOf(home);
        if (emptyRoom > GIVE_UP) {
            setTarget(null);
            if (distanceToSqr(h) > 2.5D * 2.5D && emptyRoom < GIVE_UP + 400) {
                getNavigation().moveTo(h.x, h.y, h.z, 1.0D);
            } else {
                sleep();
            }
        } else if (!inRoom(position()) || heartPos == null && distanceToSqr(h) > (LEASH + 8.0D) * (LEASH + 8.0D)) {
            //
            setTarget(null);
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
        bellowNow = false;
        entityData.set(RAGE, false);
        entityData.set(DORMANT, true);
        setAttackState(0);
        emptyRoom = 0;
        wary = 0;
    }

    /** Every beat of every attack, told before it lands - in his body, in sound, on the floor. */
    private void beats() {
        int st = getAttackState();
        int t = attackTicks;
        switch (st) {
            case INTRO -> introBeats(t);
            case AWAKEN -> {
                holdStill();
                if (t == AWAKEN_SHED) {
                    fx("aurochs_shed", position(), 20);
                    playSound(FFSounds.ICE_AUROCHS_SCRAPE.get(), 1.2F, 1.3F);
                }
                if (t == AWAKEN_BELLOW) {
                    playSound(FFSounds.ICE_AUROCHS_BELLOW.get(), 3.0F, 1.0F);
                }
                if (t >= AWAKEN_T) {
                    finish(10);
                }
            }
            case PAW, PAW_QUICK -> tell(st == PAW_QUICK, t);
            case CHARGE -> charge(t);
            case SKID -> {
                holdStill();
                double k = Math.max(0.0D, 1.0D - t / 8.0D);
                double s = speed() * 0.8D * k;
                setDeltaMovement(line.x * s, getDeltaMovement().y, line.z * s);
                if (t == 1) {
                    playSound(FFSounds.ICE_AUROCHS_SCRAPE.get(), 1.6F, 0.8F);
                    fx("aurochs_scrape", position(), 16);
                }
                if (t < 6) {
                    gore();                                   // his weight still coming on
                    trample();
                }
                if (t >= SKID_T) {
                    LivingEntity next = chainLeft > 0 && aimEntity != null ? nextInChain() : null;
                    if (next != null) {
                        chainLeft--;                          // a party: round on the next of them (the quick tell)
                        aimEntity = next;
                        aimFire = null;
                        aimAt = next.position();
                        setAttackState(PAW_QUICK);
                    } else {
                        finish(secondHalf ? 12 : 18);
                    }
                }
            }
            case CRASH -> {
                holdStill();
                setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
                if (t >= CRASH_T) {
                    setAttackState(STUNNED);
                    // the stars ride on him for as long as he is down
                    AttackFxEntity.spawn(level(), "aurochs_dizzy", position(), yBodyRot, 1.0F, STUN_T, this).follow(this);
                }
            }
            case STUNNED -> {
                holdStill();
                setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
                if (t % 12 == 4) {
                    playSound(FFSounds.ICE_AUROCHS_DIZZY.get(), 1.4F, 0.95F + random.nextFloat() * 0.1F);
                }
                if (t >= STUN_T) {
                    setAttackState(RECOVER);
                }
            }
            case RECOVER -> {
                holdStill();
                if (t == 14) {
                    playSound(FFSounds.ICE_AUROCHS_SNORT.get(), 1.6F, 1.0F);
                }
                if (t >= RECOVER_T) {
                    finish(10);
                }
            }
            case BONK -> {
                holdStill();
                setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
                if (t >= BONK_T) {
                    finish(14);
                }
            }
            case TOSS -> {
                holdStill();
                LivingEntity target = getTarget();
                if (target != null && t < TOSS_HIT - 3) {
                    turnToward(target.position(), 9.0F);
                }
                if (t == 2) {
                    playSound(FFSounds.ICE_AUROCHS_SNORT.get(), 1.2F, 1.15F);
                }
                if (t == TOSS_HIT - 2) {
                    playSound(FFSounds.ICE_AUROCHS_TOSS.get(), 2.0F, 1.0F);
                    fx("aurochs_toss", position(), 14);
                }
                if (t == TOSS_HIT) {
                    toss();
                }
                if (t >= TOSS_T) {
                    finish(14);
                }
            }
            case STOMP -> {
                holdStill();
                LivingEntity target = getTarget();
                if (target != null && t < 16) {
                    turnToward(target.position(), 6.0F);
                }
                if (t == 3) {
                    playSound(FFSounds.ICE_AUROCHS_REAR.get(), 2.4F, 1.0F);
                }
                if (t == STOMP_SLAM) {
                    playSound(FFSounds.ICE_AUROCHS_STOMP.get(), 3.0F, 1.0F);
                    level().addFreshEntity(new IceAurochsRingEntity(level(), this, hooves()));
                }
                if (t == STOMP_SECOND && secondHalf) {
                    level().addFreshEntity(new IceAurochsRingEntity(level(), this, hooves()));
                }
                if (t >= STOMP_T) {
                    finish(18);
                }
            }
            case KICK -> {
                holdStill();
                if (t == 2) {
                    playSound(FFSounds.ICE_AUROCHS_SNORT.get(), 1.4F, 0.9F);
                }
                if (t == KICK_HIT) {
                    playSound(FFSounds.ICE_AUROCHS_KICK.get(), 2.2F, 1.0F);
                    fx("aurochs_kick", position(), 14);
                    kick();
                }
                if (t >= KICK_T) {
                    finish(12);
                }
            }
            case BELLOW -> {
                holdStill();
                if (t == BELLOW_ROAR) {
                    playSound(FFSounds.ICE_AUROCHS_BELLOW.get(), 3.2F, 0.92F);
                    fx("aurochs_shed", position(), 20);
                }
                if (t >= BELLOW_T) {
                    finish(8);
                }
            }
            default -> {
            }
        }
    }

    private void finish(int after) {
        setAttackState(0);
        rest = after;
        aimEntity = null;
        aimFire = null;
    }

    /** Nothing of the walking machinery pushes him while he does something else. */
    private void holdStill() {
        getNavigation().stop();
        setZza(0.0F);
        setXxa(0.0F);
    }

    private double speed() {
        return secondHalf ? CHARGE_SPEED_RAGE : CHARGE_SPEED;
    }

    /** His front hooves on the floor: where the stomp comes down. */
    private Vec3 hooves() {
        Vec3 f = forward();
        return new Vec3(getX() + f.x, getY(), getZ() + f.z);
    }

    private Vec3 forward() {
        float a = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(a), 0.0D, Mth.cos(a));
    }

    private void turnToward(Vec3 at, float max) {
        double dx = at.x - getX(), dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(yBodyRot, want, max);
        face(yaw);
    }

    private void face(float yaw) {
        setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    /** One of his things with a body (AttackFxEntity: geo/entity/fx_&lt;kind&gt;), laid at `at` the way he faces. */
    private void fx(String kind, Vec3 at, int life) {
        AttackFxEntity.spawn(level(), kind, at, yBodyRot, 1.0F, life, this);
    }

    // ------------------------------------------------------------------------------------------------ THE CHARGE
    /** The goal chose: charge this person. */
    void beginCharge(LivingEntity target) {
        aimEntity = target;
        aimFire = null;
        aimAt = target.position();
        chainLeft = com.jastkub.frozenfortress.event.PartyScaling.extra(this);
        setAttackState(secondHalf ? PAW_QUICK : PAW);
    }

    /** A PARTY: charges left to run one into the next, straight out of the skid - one for each fighter past the
     *  first (PartyScaling). A pillar or a wall ends it. */
    private int chainLeft;

    /** The next of them, out of the skid: whoever else he can run at, nearest first. */
    @Nullable
    private LivingEntity nextInChain() {
        for (net.minecraft.world.entity.player.Player p : com.jastkub.frozenfortress.event.PartyScaling.others(this, aimEntity, 8)) {
            if (distanceTo(p) >= 6.0D && chargeable(position(), p.position(), OVERRUN, wary > 0)) {
                return p;
            }
        }
        return null;
    }

    /** The goal chose: put out this hearth. */
    void beginFireCharge(BlockPos fire) {
        aimEntity = null;
        chainLeft = 0;
        aimFire = fire;
        aimAt = Vec3.atBottomCenterOf(fire);
        setAttackState(secondHalf ? PAW_QUICK : PAW);
    }

    /**
     * THE TELL: two scrapes of a forehoof (one, enraged), the horns down, the snort - turning to his mark all the
     * while - and the line fixed four ticks after the snort: whoever moves aside after that is not where he runs.
     */
    private void tell(boolean quick, int t) {
        holdStill();
        int aim = quick ? PAWQ_AIM : PAW_AIM;
        int snortAt = quick ? PAWQ_SNORT : PAW_SNORT;
        int end = quick ? PAWQ_T : PAW_T;
        if (t < aim) {
            if (aimEntity != null) {
                if (!aimEntity.isAlive()) {
                    finish(10);
                    return;
                }
                aimAt = aimEntity.position();
            } else if (aimFire != null && !lit(aimFire)) {
                finish(6);                                    // somebody else's doing: it is out already
                return;
            }
            turnToward(aimAt, quick ? 14.0F : 10.0F);
        }
        if (quick ? t == PAWQ_SCRAPE : (t == PAW_SCRAPE_1 || t == PAW_SCRAPE_2)) {
            playSound(FFSounds.ICE_AUROCHS_SCRAPE.get(), 1.8F, 0.95F + random.nextFloat() * 0.1F);
            fx("aurochs_scrape", position(), 16);
        }
        if (t == snortAt) {
            playSound(FFSounds.ICE_AUROCHS_SNORT.get(), 2.2F, 1.0F);
            fx("aurochs_snort", position(), 18);
        }
        if (t == aim) {
            Vec3 to = new Vec3(aimAt.x - getX(), 0.0D, aimAt.z - getZ());
            double dist = to.length();
            line = dist < 1.0E-3D ? forward() : to.scale(1.0D / dist);
            face((float) (Mth.atan2(line.z, line.x) * Mth.RAD_TO_DEG) - 90.0F);
            // AT A HEARTH he stops on it; AT A PERSON he runs on past where they stood
            chargeEnd = aimFire != null ? aimAt : position().add(line.scale(dist + OVERRUN));
            if (!lineClearOfHeart(position(), chargeEnd)) {
                finish(10);                                   // never at the heart: he will not run that way
            }
        }
        if (t >= end) {
            chargeFrom = position();
            struck.clear();
            setAttackState(CHARGE);
            playSound(FFSounds.ICE_AUROCHS_GALLOP.get(), 2.6F, 0.9F);
        }
    }

    private void charge(int t) {
        holdStill();
        face((float) (Mth.atan2(line.z, line.x) * Mth.RAD_TO_DEG) - 90.0F);
        double s = speed();
        // ---- the heart, ahead: he pulls up short of it (he never runs at it - this is only if it is in the way)
        if (heartPos != null) {
            Vec3 hc = Vec3.atBottomCenterOf(heartPos);
            Vec3 rel = new Vec3(hc.x - getX(), 0.0D, hc.z - getZ());
            double ahead = rel.dot(line);
            double across = Math.abs(rel.x * line.z - rel.z * line.x);
            if (ahead > 0.0D && ahead < NOSE + 2.0D + s && across < 2.4D) {
                pullUp();
                return;
            }
        }
        // ---- what his horns are about to meet: a pillar breaks him, anything else jars him
        double[] hitAt = new double[1];
        BlockPos hit = obstacleAhead(line, 1.2D, NOSE + s, hitAt);
        if (hit != null) {
            double run = Math.sqrt(position().subtract(chargeFrom).horizontalDistanceSqr());
            double gap = Math.max(0.0D, hitAt[0] - NOSE);
            if (gap > 0.0D) {
                setPos(getX() + line.x * gap, getY(), getZ() + line.z * gap);    // the horns to the stone
            }
            impact = new Vec3(getX() + line.x * (NOSE + 0.1D), getY(), getZ() + line.z * (NOSE + 0.1D));
            if (heartPos != null && hit.distSqr(heartPos) < 4.0D) {
                pullUp();
            } else if (run >= CHARGE_MIN_RUN && freeStanding(hit)) {
                crash();
            } else {
                bonk();
            }
            return;
        }
        setDeltaMovement(line.x * s, getDeltaMovement().y, line.z * s);
        hurtMarked = true;
        if (t % 5 == 0) {
            playSound(FFSounds.ICE_AUROCHS_GALLOP.get(), 2.6F, 0.9F + random.nextFloat() * 0.15F);
        }
        if (t % 4 == 2) {
            fx("aurochs_scrape", position(), 16);                  // the floor torn up behind his forehoof
        }
        gore();
        trample();
        if (getAttackState() != CHARGE) {
            return;                                            // he stands on the hearth he went for
        }
        Vec3 left = chargeEnd.subtract(position());
        boolean past = left.x * line.x + left.z * line.z <= 0.0D;
        if (t >= CHARGE_MAX || past || !inRoom(position().add(line.scale(NOSE)))) {
            pullUp();
        } else if (horizontalCollision && t > 1) {
            impact = position().add(line.scale(NOSE));
            bonk();                                            // something he did not see coming (a door, a block)
        }
    }

    private void pullUp() {
        if (getAttackState() != SKID) {
            setAttackState(SKID);
        }
    }

    /** Into a pillar after a real run: horns into the stone, down on his knees. */
    private void crash() {
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        setAttackState(CRASH);
        wary = WARY;
        playSound(FFSounds.ICE_AUROCHS_CRASH.get(), 3.2F, 0.9F);
        AttackFxEntity.spawn(level(), "aurochs_crash", impact, yBodyRot, 1.0F, 24, this);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(com.jastkub.frozenfortress.registry.FFParticles.ICE_SHARD.get(), impact.x, impact.y + 1.5D,
                    impact.z, 18, 0.6D, 0.6D, 0.6D, 0.15D);           // (only riding along with the chunks)
        }
    }

    /** Into a wall, or a pillar without the run to break him: he jars to a stop and shakes it off. */
    private void bonk() {
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        setAttackState(BONK);
        playSound(FFSounds.ICE_AUROCHS_CRASH.get(), 2.2F, 1.2F);
    }

    /**
     * The first thing solid his horns would meet within [from, to] blocks ahead of his middle, across his brow and
     * from his knees to his hump; `at[0]` gets how far. Things he steps over (a hearth, a slab) are not in it.
     */
    @Nullable
    private BlockPos obstacleAhead(Vec3 dir, double from, double to, double[] at) {
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (double d = from; d <= to + 1.0E-3D; d += 0.25D) {
            for (double o : new double[]{0.0D, -0.9D, 0.9D}) {
                for (double up : new double[]{0.9D, 1.7D, 2.5D}) {
                    double x = getX() + dir.x * d + side.x * o, y = getY() + up, z = getZ() + dir.z * d + side.z * o;
                    p.set(x, y, z);
                    if (solidAt(p, y)) {
                        at[0] = d;
                        return p.immutable();
                    }
                }
            }
        }
        return null;
    }

    /** Is the point at height y inside something solid in block p (not only its footprint - a hearth is low)? */
    private boolean solidAt(BlockPos p, double y) {
        BlockState st = level().getBlockState(p);
        VoxelShape sh = st.getCollisionShape(level(), p);
        if (sh.isEmpty()) {
            return false;
        }
        double lo = p.getY() + sh.min(net.minecraft.core.Direction.Axis.Y);
        double hi = p.getY() + sh.max(net.minecraft.core.Direction.Axis.Y);
        return y > lo && y < hi;
    }

    /**
     * A PILLAR, NOT A WALL: the block he hit stands free - the ring of blocks three out round it, at his height, is
     * open air almost all the way round. (The hall's pillars are two blocks square and five from any wall; a wall
     * fills its side of that ring.)
     */
    private boolean freeStanding(BlockPos hit) {
        int solid = 0, cells = 0;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != 3) {
                        continue;
                    }
                    cells++;
                    q.set(hit.getX() + dx, hit.getY() + dy, hit.getZ() + dz);
                    if (!level().getBlockState(q).getCollisionShape(level(), q).isEmpty()) {
                        solid++;
                    }
                }
            }
        }
        return solid <= cells / 6;
    }

    /** Burning hearths under his horns are trampled out - any hearth, not only the one he went for. */
    private void trample() {
        Vec3 nose = position().add(line.scale(NOSE - 1.2D));
        for (BlockPos p : hearths) {
            if (!lit(p)) {
                continue;
            }
            Vec3 c = Vec3.atBottomCenterOf(p);
            double dn = Math.hypot(c.x - nose.x, c.z - nose.z);
            double db = Math.hypot(c.x - getX(), c.z - getZ());
            if ((dn < DOUSE_R || db < 1.6D) && Math.abs(c.y - getY()) < 1.5D) {
                douse(p);
                if (p.equals(aimFire)) {
                    pullUp();                                 // done: he stands on its ashes
                }
            }
        }
    }

    /** THE HEARTH TRAMPLED OUT: its fire gone (LIT false), a hiss, steam and rime boiling off it. */
    private void douse(BlockPos p) {
        BlockState s = level().getBlockState(p);
        if (!(s.getBlock() instanceof CampfireBlock) || !s.getValue(CampfireBlock.LIT)) {
            return;
        }
        CampfireBlock.dowse(this, level(), p, s);
        level().setBlock(p, s.setValue(CampfireBlock.LIT, false), 11);
        level().playSound(null, p, FFSounds.ICE_AUROCHS_DOUSE.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
        AttackFxEntity.spawn(level(), "aurochs_steam", Vec3.atBottomCenterOf(p), 0.0F, 1.0F, 40, this);
    }

    /** Whoever is in his path: a heavy blow and thrown aside - once a charge. */
    private void gore() {
        AABB box = getBoundingBox().inflate(0.4D, 0.0D, 0.4D).expandTowards(line.scale(NOSE - 1.0D));
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, box, this::foe)) {
            Vec3 rel = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
            if (rel.lengthSqr() > 1.0E-4D && rel.normalize().dot(line) < 0.1D) {
                continue;                                     // beside or behind him, not in front
            }
            if (!struck.add(v.getUUID())) {
                continue;
            }
            v.hurt(damageSources().mobAttack(this), CHARGE_DMG);
            double sideSign = Math.signum(rel.x * line.z - rel.z * line.x);
            if (sideSign == 0.0D) {
                sideSign = random.nextBoolean() ? 1.0D : -1.0D;
            }
            Vec3 side = new Vec3(line.z, 0.0D, -line.x).scale(sideSign);
            v.setDeltaMovement(line.x * 1.1D + side.x * 0.75D, 0.55D, line.z * 1.1D + side.z * 0.75D);
            v.hurtMarked = true;
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
            playSound(FFSounds.ICE_IMPACT.get(), 2.4F, 0.7F);
        }
    }

    // ------------------------------------------------------------------------------------------------ the others
    /** The hook of the horns: whoever is close in front, flung up and away. */
    private void toss() {
        Vec3 f = forward();
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(TOSS_R, 2.0D, TOSS_R),
                this::foe)) {
            Vec3 rel = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
            double d = rel.length();
            if (d > TOSS_R || Math.abs(v.getY() - getY()) > 3.0D || (d > 0.5D && rel.scale(1.0D / d).dot(f) < 0.34D)) {
                continue;                                     // (within seventy degrees of his nose)
            }
            v.hurt(damageSources().mobAttack(this), TOSS_DMG);
            Vec3 away = d > 0.5D ? rel.scale(1.0D / d) : f;
            v.setDeltaMovement(away.x * 0.6D, 0.95D, away.z * 0.6D);
            v.hurtMarked = true;
        }
    }

    /** Both hind hooves out behind him: whoever was at his rump or close on his flank, thrown far back. */
    private void kick() {
        Vec3 f = forward();
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(KICK_R, 2.0D, KICK_R),
                this::foe)) {
            Vec3 rel = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
            double d = rel.length();
            if (d > KICK_R || Math.abs(v.getY() - getY()) > 2.5D || d < 0.3D || rel.scale(1.0D / d).dot(f) > -0.34D) {
                continue;                                     // (more than a hundred and ten degrees off his nose)
            }
            v.hurt(damageSources().mobAttack(this), KICK_DMG);
            Vec3 away = rel.scale(1.0D / d);
            v.setDeltaMovement(away.x * 1.5D, 0.5D, away.z * 1.5D);
            v.hurtMarked = true;
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 60, 0), this);
        }
    }

    boolean foe(LivingEntity e) {
        return e != this && e.isAlive() && !FFAllies.ofTheKing(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ------------------------------------------------------------------------------------------------ lines
    /** Does the line from `a` to `b` keep clear of the heart (no nearer than HEART_CLEAR)? */
    boolean lineClearOfHeart(Vec3 a, Vec3 b) {
        if (heartPos == null) {
            return true;
        }
        Vec3 h = Vec3.atBottomCenterOf(heartPos);
        double abx = b.x - a.x, abz = b.z - a.z;
        double len2 = abx * abx + abz * abz;
        double u = len2 < 1.0E-6D ? 0.0D : Mth.clamp(((h.x - a.x) * abx + (h.z - a.z) * abz) / len2, 0.0D, 1.0D);
        double px = a.x + abx * u - h.x, pz = a.z + abz * u - h.z;
        return px * px + pz * pz > HEART_CLEAR * HEART_CLEAR;
    }

    /** Anything solid across his whole width, knee to hump, between `d0` and `d1` blocks along `dir` from `from`. */
    boolean blockedAlong(Vec3 from, Vec3 dir, double d0, double d1) {
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (double d = d0; d <= d1; d += 0.5D) {
            for (double o = -1.4D; o <= 1.41D; o += 0.7D) {
                for (double up : new double[]{0.9D, 1.7D, 2.5D}) {
                    double x = from.x + dir.x * d + side.x * o, y = from.y + up, z = from.z + dir.z * d + side.z * o;
                    p.set(x, y, z);
                    if (solidAt(p, y)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Could he charge from `from` at `to`: inside his room with room to spare, the line clear of the heart (and of
     * `overrun` blocks past the mark), and open for the first five blocks - he needs his run-up. With `noPillar`, open
     * all the way (a pillar broke him not long ago).
     */
    boolean chargeable(Vec3 from, Vec3 to, double overrun, boolean noPillar) {
        Vec3 d = new Vec3(to.x - from.x, 0.0D, to.z - from.z);
        double dist = d.length();
        if (dist < CHARGE_MIN_RUN || dist > 22.0D) {
            return false;                                         // (nearer than his run-up: no charge at all)
        }
        if (room != null && !room.deflate(1.6D, 0.0D, 1.6D).contains(from.x, Mth.clamp(from.y, room.minY + 0.1D,
                room.maxY - 0.1D), from.z)) {
            return false;
        }
        Vec3 dir = d.scale(1.0D / dist);
        if (!lineClearOfHeart(from, to.add(dir.scale(overrun)))) {
            return false;
        }
        Vec3 base = new Vec3(from.x, from.y, from.z);
        return !blockedAlong(base, dir, 1.0D, Math.min(CHARGE_MIN_RUN + 0.5D, dist - 1.0D))
                && !(noPillar && blockedAlong(base, dir, 1.0D, dist));
    }

    /** A place to charge `fire` from: as near him as can be found, eight to thirteen blocks off it, the line clear. */
    @Nullable
    Vec3 launchFor(BlockPos fire) {
        Vec3 f = Vec3.atBottomCenterOf(fire);
        Vec3 best = null;
        double bd = Double.MAX_VALUE;
        for (int ring = 0; ring < 3; ring++) {
            double r = 8.0D + ring * 2.5D;
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8.0D;
                Vec3 c = new Vec3(f.x + Math.cos(a) * r, getY(), f.z + Math.sin(a) * r);
                if (!chargeable(c, f, 1.0D, false)) {
                    continue;
                }
                double d = c.distanceToSqr(position());
                if (d < bd) {
                    bd = d;
                    best = c;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------------ hurt, death
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
                return false;                                  // his entrance: a scene is not a fight
            }
            if (st == AWAKEN) {
                amount *= 0.5F;                                // still shaking the rime off
            } else if (st == CRASH || st == STUNNED) {
                amount *= STUN_TAKEN;                          // down on his knees: the window
            }
        }
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && !secondHalf && isAlive() && getHealth() < getMaxHealth() * 0.5F) {
            secondHalf = true;
            bellowNow = true;                                 // (he bellows as soon as what he does is done)
        }
        return hit;
    }

    /** The goal asks: is it time for the bellow of his second half? (Then the rage-ice is out of his back.) */
    boolean takeBellow() {
        if (!bellowNow) {
            return false;
        }
        bellowNow = false;
        entityData.set(RAGE, true);
        return true;
    }

    @Override
    public void die(DamageSource source) {
        bossEvent.removeAllPlayers();
        super.die(source);
        // the heart of his hall dies with him
        if (!level().isClientSide) {
            com.jastkub.frozenfortress.block.entity.FrostHeartBlockEntity.keeperFell(level(), position());
        }
    }

    /** What is left of him (nothing, once he has shattered) is kept until his death scene goes to black. */
    @Override
    protected int getDeathDuration() {
        return DEATH_T + 36;
    }

    @Override
    protected int shatterTick() {
        return DEATH_SHATTER + DEATH_LAG;                     // (its particles only ride along with the model)
    }

    /** His death's sounds, on its clip's beats - and the ice of him breaking apart where he lies (geometry). */
    @Override
    protected void tickDeath() {
        if (!level().isClientSide && deathTime + 1 == DEATH_SHATTER + DEATH_LAG) {
            AttackFxEntity.spawn(level(), "aurochs_shatter", position(), yBodyRot, 1.0F, 30, null);
        }
        super.tickDeath();
        if (level().isClientSide) {
            return;
        }
        int t = deathTime - DEATH_LAG;
        if (t == DEATH_SCRAPE) {
            playSound(FFSounds.ICE_AUROCHS_SCRAPE.get(), 1.4F, 0.8F);
        }
        if (t == DEATH_KNEEL) {
            playSound(FFSounds.ICE_AUROCHS_CRASH.get(), 1.2F, 0.7F);
        }
        if (t == DEATH_BELLOW) {
            playSound(FFSounds.ICE_AUROCHS_BELLOW.get(), 2.6F, 0.8F);    // the last of him: lower, longer
        }
        if (t == DEATH_FALL) {
            playSound(FFSounds.ICE_AUROCHS_CRASH.get(), 1.8F, 0.55F);
            playSound(FFSounds.ICE_AUROCHS_STEP.get(), 1.6F, 0.5F);
        }
        if (t == DEATH_EYES) {
            playSound(FFSounds.ICE_AUROCHS_SNORT.get(), 0.8F, 0.6F);      // the breath going out of him
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
        entityData.set(RAGE, secondHalf);
        int[] h = tag.getIntArray("Home");
        if (h.length == 3) {
            home = new BlockPos(h[0], h[1], h[2]);
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
                case PAW: return state.setAndContinue(PAW_ANIM);
                case PAW_QUICK: return state.setAndContinue(PAWQ_ANIM);
                case CHARGE: return state.setAndContinue(CHARGE_ANIM);
                case SKID: return state.setAndContinue(SKID_ANIM);
                case CRASH: return state.setAndContinue(CRASH_ANIM);
                case STUNNED: return state.setAndContinue(STUNNED_ANIM);
                case RECOVER: return state.setAndContinue(RECOVER_ANIM);
                case BONK: return state.setAndContinue(BONK_ANIM);
                case TOSS: return state.setAndContinue(TOSS_ANIM);
                case STOMP: return state.setAndContinue(STOMP_ANIM);
                case KICK: return state.setAndContinue(KICK_ANIM);
                case BELLOW: return state.setAndContinue(BELLOW_ANIM);
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
        return isDormant() ? null : FFSounds.ICE_AUROCHS_IDLE.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 140;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.ICE_AUROCHS_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.ICE_AUROCHS_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(FFSounds.ICE_AUROCHS_STEP.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
    }

    // ------------------------------------------------------------------------------------------------ the fight
    /**
     * Chooses what he does next and walks him to it; the beats of what he does are his own (aiStep), so a lost
     * target never leaves him half way through a charge.
     */
    static class AurochsAttackGoal extends Goal {
        private final IceAurochsEntity mob;
        private int chargeCd = 60, fireCd = 100, tossCd, kickCd, stompCd = 80;

        AurochsAttackGoal(IceAurochsEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !mob.isDormant();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
            mob.launch = null;
        }

        @Override
        public void tick() {
            chargeCd = Math.max(0, chargeCd - 1);
            fireCd = Math.max(0, fireCd - 1);
            tossCd = Math.max(0, tossCd - 1);
            kickCd = Math.max(0, kickCd - 1);
            stompCd = Math.max(0, stompCd - 1);
            if (mob.isAttacking()) {
                mob.launch = null;
                return;
            }
            if (mob.takeBellow()) {
                mob.getNavigation().stop();
                mob.setAttackState(BELLOW);
                return;
            }
            boolean two = mob.secondHalf;
            LivingEntity target = mob.getTarget();
            // ---- on his way to a place to charge a hearth from
            if (mob.launch != null) {
                if (mob.launchFire == null || !mob.lit(mob.launchFire) || --mob.launchTicks <= 0 || !mob.heartBeats()) {
                    mob.launch = null;
                } else if (mob.position().distanceToSqr(mob.launch.x, mob.getY(), mob.launch.z) < 2.0D * 2.0D
                        || mob.chargeable(mob.position(), Vec3.atBottomCenterOf(mob.launchFire), 1.0D, false)
                        && mob.position().distanceToSqr(Vec3.atBottomCenterOf(mob.launchFire)) > 6.0D * 6.0D) {
                    mob.getNavigation().stop();
                    BlockPos fire = mob.launchFire;
                    mob.launch = null;
                    fireCd = fireCooldown(two);
                    mob.beginFireCharge(fire);
                    return;
                } else {
                    if (mob.getNavigation().isDone()) {
                        mob.getNavigation().moveTo(mob.launch.x, mob.launch.y, mob.launch.z, two ? 1.3D : 1.15D);
                    }
                    return;
                }
            }
            if (target != null && target.isAlive()) {
                double dist = Math.hypot(target.getX() - mob.getX(), target.getZ() - mob.getZ());
                mob.getLookControl().setLookAt(target, 20.0F, 20.0F);
                if (mob.rest > 0) {
                    mob.rest--;
                    approach(target, dist, two);
                    return;
                }
                Vec3 f = mob.forward();
                // ---- THE BACK-KICK: anyone at his rump or close on his flank behind his shoulder
                if (kickCd <= 0) {
                    for (LivingEntity v : mob.level().getEntitiesOfClass(LivingEntity.class,
                            mob.getBoundingBox().inflate(KICK_R - 0.6D, 2.0D, KICK_R - 0.6D), mob::foe)) {
                        Vec3 rel = new Vec3(v.getX() - mob.getX(), 0.0D, v.getZ() - mob.getZ());
                        double d = rel.length();
                        if (d > 0.3D && d < KICK_R - 0.6D && rel.scale(1.0D / d).dot(f) < -0.34D) {
                            kickCd = two ? 40 : 60;
                            mob.getNavigation().stop();
                            mob.setAttackState(KICK);
                            return;
                        }
                    }
                }
                // ---- THE HORN TOSS: close, before him
                Vec3 rel = new Vec3(target.getX() - mob.getX(), 0.0D, target.getZ() - mob.getZ());
                boolean before = dist > 0.3D && rel.scale(1.0D / dist).dot(f) > 0.5D;
                if (tossCd <= 0 && dist < TOSS_R - 0.4D && before && Math.abs(target.getY() - mob.getY()) < 2.5D) {
                    tossCd = two ? 30 : 44;
                    mob.getNavigation().stop();
                    mob.setAttackState(TOSS);
                    return;
                }
                // ---- A HEARTH BURNS: he goes to put it out (more urgent, the more of them burn)
                if (fireCd <= 0 && mob.heartBeats()) {
                    BlockPos fire = pickFire();
                    if (fire != null) {
                        Vec3 fc = Vec3.atBottomCenterOf(fire);
                        if (mob.chargeable(mob.position(), fc, 1.0D, false)) {
                            mob.getNavigation().stop();
                            fireCd = fireCooldown(two);
                            mob.beginFireCharge(fire);
                            return;
                        }
                        Vec3 spot = mob.launchFor(fire);
                        if (spot != null) {
                            mob.launch = spot;
                            mob.launchFire = fire;
                            mob.launchTicks = 120;
                            mob.getNavigation().moveTo(spot.x, spot.y, spot.z, two ? 1.3D : 1.15D);
                            return;
                        }
                        fireCd = 40;                          // no line to it now: look again in a while
                    }
                }
                // ---- THE CHARGE, at them - seen or not (he hears and smells them in his own hall): so whoever hides
                // behind a pillar draws him into it - unless a pillar broke him a little while ago (wary)
                if (chargeCd <= 0 && dist >= 6.0D
                        && mob.chargeable(mob.position(), target.position(), OVERRUN, mob.wary > 0)) {
                    chargeCd = two ? 90 : 130;
                    mob.getNavigation().stop();
                    mob.beginCharge(target);
                    return;
                }
                // ---- THE STOMP: near enough for the ring to matter, or more than one about him
                if (stompCd <= 0 && (dist < 8.0D || crowd() >= 2)) {
                    stompCd = two ? 110 : 160;
                    mob.getNavigation().stop();
                    mob.setAttackState(STOMP);
                    return;
                }
                approach(target, dist, two);
            } else if (fireCd <= 0 && mob.heartBeats()) {
                // nobody to hunt, but a hearth burns: it will not burn long
                BlockPos fire = pickFire();
                if (fire != null) {
                    Vec3 fc = Vec3.atBottomCenterOf(fire);
                    if (mob.chargeable(mob.position(), fc, 1.0D, false)) {
                        fireCd = fireCooldown(two);
                        mob.beginFireCharge(fire);
                    } else {
                        Vec3 spot = mob.launchFor(fire);
                        if (spot != null) {
                            mob.launch = spot;
                            mob.launchFire = fire;
                            mob.launchTicks = 120;
                            mob.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.15D);
                        } else {
                            fireCd = 40;
                        }
                    }
                }
            }
        }

        private Vec3 lastAt;
        private int stuck;

        private void approach(LivingEntity target, double dist, boolean two) {
            if (dist > 3.6D) {
                if (mob.getNavigation().isDone() || mob.tickCount % 10 == 0) {
                    mob.getNavigation().moveTo(target, two ? 1.2D : 1.0D);
                }
            } else {
                mob.getNavigation().stop();
                // close: he turns his bulk to them, so his horns can find them (07.10.2026: at a pillar he stood side on)
                float want = (float) (Math.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX()) * (180.0D / Math.PI))
                        - 90.0F;
                float yaw = net.minecraft.util.Mth.approachDegrees(mob.getYRot(), want, 12.0F);
                mob.setYRot(yaw);
                mob.yBodyRot = yaw;
            }
            // STUCK (at a pillar, his bulk wedged): two seconds without getting anywhere and he stamps
            if (lastAt != null && mob.position().distanceToSqr(lastAt) < 0.3D * 0.3D && dist > 3.6D) {
                if (++stuck >= 40 && dist < 10.0D) {
                    stuck = 0;
                    stompCd = two ? 110 : 160;
                    mob.getNavigation().stop();
                    mob.setAttackState(STOMP);
                }
            } else {
                stuck = 0;
            }
            lastAt = mob.position();
        }

        /** Quicker the more hearths burn - with all four alight (the heart open) he cannot bear it at all. */
        private int fireCooldown(boolean two) {
            int lit = mob.litHearths().size();
            int base = two ? 110 : 160;
            return lit >= 4 ? 50 : lit == 3 ? base / 2 : base;
        }

        /** The burning hearth to go for: the one most people warm themselves at, then the nearest. */
        @Nullable
        private BlockPos pickFire() {
            BlockPos best = null;
            double bestScore = -Double.MAX_VALUE;
            for (BlockPos p : mob.litHearths()) {
                Vec3 c = Vec3.atBottomCenterOf(p);
                int warm = mob.level().getEntitiesOfClass(Player.class, new AABB(p).inflate(4.5D), mob::foe).size();
                double score = warm * 20.0D - Math.sqrt(c.distanceToSqr(mob.position()));
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
            return best;
        }

        private int crowd() {
            return mob.level().getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(8.0D, 3.0D, 8.0D),
                    mob::foe).size();
        }
    }
}
