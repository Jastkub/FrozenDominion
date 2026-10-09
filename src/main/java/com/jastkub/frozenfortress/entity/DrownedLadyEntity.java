package com.jastkub.frozenfortress.entity;

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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * TOPIELICA - THE DROWNED LADY, the miniboss of the Frozen Cisterns (Zamarzniete Cysterny). A noblewoman of the
 * frozen court who went under the cisterns' ice: half water-wraith, half ice, the hem of her gown frozen into the
 * floor. She glides, half sunk, and she can go down through the ice and come up anywhere. The fight is ON ICE OVER
 * DARK WATER, and the floor is her weapon.
 *
 * <p>Until someone comes she lies under the ice where she drowned (her shape lies over the spot,
 * DrownedShadowEntity) - and before she ever wakes, the floor already shows where it will hold: thick plates with
 * runes cut in them (DrownedIcePlateEntity, rune), which nothing of hers breaks or comes up through.
 *
 * <p>Her attacks - each told before it lands, each a thing with a body:
 * <ol>
 *   <li>LASH (Smagniecie Wlosami) - close in: she coils, her frozen hair lifting and lengthening off her back (13
 *   ticks, a chime of ice), then a whole turn - the strands swept round her like a blade, everything within four
 *   blocks of her, all round. Splinters fly off it (fx_drowned_lash).</li>
 *   <li>BREAK (Kruszenie Lodu) - she rises over the ice and drives her hands into it: plates of the floor round
 *   whoever she hunts start to crack, and thirty ticks later they BREAK - open freezing water (DrownedIcePlateEntity).
 *   Holes heal over in eleven seconds; no more than eight at a time.</li>
 *   <li>GRASP (Uscisk z Glebiny) - she goes down through the ice; her dark shape glides at you under it
 *   (DrownedShadowEntity), slower than a run and unable to cross a rune; where it stops the ice heaves and her hands
 *   burst up and drag you down into a hole (DrownedHandsEntity) - three blows on her hands break the hold, and then
 *   she comes up reeling, open to a beating.</li>
 *   <li>TIDE (Czarny Przyplyw) - she lifts the black water out of the cistern: a ring of it, crested with ice, runs out
 *   twelve blocks over the floor (DrownedTideEntity). A rune plate, a jump over the crest or distance - nothing else.</li>
 * </ol>
 * Below half her health she wails and the fight quickens: more plates go at once, her shape is faster, her tide
 * comes sooner.
 */
public class DrownedLadyEntity extends FrostServantEntity implements GateKeeper {

    private static final EntityDataAccessor<Boolean> DORMANT =
            SynchedEntityData.defineId(DrownedLadyEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int AWAKEN = 1, LASH = 2, BREAK = 3, SINK = 4, SUBMERGED = 5, RISE = 6, TIDE = 7, STUNNED = 8,
            INTRO = 9;
    /** HER ENTRANCE (tools/gen_drowned_lady.py INTRO_*: the "intro" clip - change one, change both; BossScenes cuts its
     *  shots to them): the ice groans and cracks, her hands burst up through it, she draws herself up out of the
     *  water, looks up - and holds a hand out to whoever came. */
    public static final int INTRO_T = 152, INTRO_GROAN = 4, INTRO_CRACK = 14, INTRO_BURST = 22, INTRO_RISE = 34,
            INTRO_RISEN = 60, INTRO_LOOK = 64, INTRO_INVITE = 96;
    /** HER DEATH (tools/gen_drowned_lady.py DEATH_*: the "death" clip - change one, change both; BossScenes' death film
     *  is cut to them): she shrieks and claws at the air, the ice groans (GROAN), she reaches out to whoever killed her
     *  (REACH) - and the ice gives under her (CRACK, its burst): she sinks, her head goes under and her crown is left
     *  floating (UNDER), the last of her hand goes under (GONE). The clip runs DEATH_LAG behind her deathTime: the
     *  controller blends into it first. */
    public static final int DEATH_T = 76, DEATH_GROAN = 12, DEATH_REACH = 18, DEATH_CRACK = 24, DEATH_UNDER = 52,
            DEATH_GONE = 62, DEATH_LAG = 4;

    // ---- the beats of her clips (tools/gen_drowned_lady.py - change one, change both)
    static final int AWAKEN_T = 40, AWAKEN_BURST = 8, AWAKEN_WAIL = 28;
    static final int LASH_T = 30, LASH_SPIN = 13, LASH_HIT = 15;
    static final int BREAK_T = 28, BREAK_CLAW = 10;
    static final int SINK_T = 20, SINK_GONE = 18;
    static final int RISE_T = 24, RISE_HIT = 8;
    /** The tide is cast on 6: its water wells for 24 and surges as her arms come down on 30. */
    static final int TIDE_T = 52, TIDE_CAST = 6;
    static final int STUN_T = 50;

    // ---- the numbers of her fight
    static final float LASH_DMG = 9.0F, RISE_DMG = 5.0F;
    static final double LASH_R = 4.2D, LASH_REACH = 3.6D;
    /** Her hands broken off you: what it costs her. */
    static final float HANDS_WOUND = 14.0F;
    /** Holes (and plates still cracking) open at once, at most. */
    static final int MAX_HOLES = 8;
    /** Her second half: the great break leaves half the hall open, and her small breaks still come on top of it. */
    static final int MAX_HOLES_TWO = 40;
    /** The rune plates she has the floor show before she wakes. */
    static final int RUNES = 6;
    /** How near you may come over her before she rises - nobody sees her down there to be seen back. */
    static final double WAKE = 11.0D;
    static final double LEASH = 20.0D;

    private static final String P = "animation.drowned_lady.";
    private static final RawAnimation DORMANT_ANIM = RawAnimation.begin().thenLoop(P + "dormant");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop(P + "glide");
    private static final RawAnimation AWAKEN_ANIM = RawAnimation.begin().thenPlay(P + "awaken");
    private static final RawAnimation INTRO_ANIM = RawAnimation.begin().thenPlay(P + "intro");
    private static final RawAnimation LASH_ANIM = RawAnimation.begin().thenPlay(P + "lash");
    private static final RawAnimation BREAK_ANIM = RawAnimation.begin().thenPlay(P + "break");
    private static final RawAnimation SINK_ANIM = RawAnimation.begin().thenPlay(P + "sink");
    private static final RawAnimation SUBMERGED_ANIM = RawAnimation.begin().thenLoop(P + "submerged");
    private static final RawAnimation RISE_ANIM = RawAnimation.begin().thenPlay(P + "rise");
    private static final RawAnimation TIDE_ANIM = RawAnimation.begin().thenPlay(P + "tide");
    private static final RawAnimation STUNNED_ANIM = RawAnimation.begin().thenPlay(P + "stunned");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay(P + "hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay(P + "death");

    /** Her second half's haste. */
    private static final UUID FURY = UUID.fromString("5d7f0c2e-8a1b-4c3e-9f2a-6b1d4e7a9c31");
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.frozen_dominion.drowned_lady"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);

    /** Where she drowned: the middle of her floor; its y is the face of the ice. */
    @Nullable
    private BlockPos home;
    private boolean runesLaid;
    /** Her rune plates (safe ground): taken up once (07.10.2026), back the same day - the water made worse instead. */
    private static final boolean LAY_RUNES = true;
    /** A hall laid out while there were no runes is given them again, once. */
    private boolean runesBack;
    /** Her second half's great break: when it may come again (its small breaks come between). */
    private int greatAt;
    /** No ice here a rune plate can be cut from (she was put somewhere else): she stops looking. */
    private boolean bareFloor;
    private boolean secondHalf;
    /** She is to throw her tide as soon as she can (the wail at half health). */
    boolean tideNow;
    private boolean stunAfterRise;
    /** A blow of her broken hands: it reaches her even under the ice. */
    private boolean woundBlow;
    @Nullable
    private UUID shadowId;
    @Nullable
    private LivingEntity graspTarget;
    private int hiddenTicks;
    /** Under the ice waiting for someone to come down onto it (nobody on her floor); and for how long nobody was. */
    private boolean lurking;
    private int offIce;
    /** Ticks she waits after an attack before the next; the attack goal counts them down. */
    int rest;

    public DrownedLadyEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        setPathfindingMalus(BlockPathTypes.WATER, 6.0F);       // her own holes she goes round, not through
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 220.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DORMANT, true);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new DrownedAttackGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ------------------------------------------------------------------------------------------------ what she is
    public boolean isDormant() {
        return entityData.get(DORMANT);
    }

    /** Down under her ice waiting for someone to step onto it. */
    public boolean lurking() {
        return lurking;
    }

    /** Down under the ice, out of sight: not drawn, not to be struck, not to be bumped into. */
    public boolean hidden() {
        return isDormant() || getAttackState() == SUBMERGED;
    }

    public boolean inSecondHalf() {
        return secondHalf;
    }

    /** The face of her floor's ice (where she first stood). */
    public int floorTop() {
        return home != null ? home.getY() : (int) Math.floor(getY());
    }

    /** The nearest one standing on her ice (in her reach of home), or nobody. */
    @Nullable
    private Player nearestOnIce() {
        BlockPos c = home != null ? home : blockPosition();
        Player best = null;
        double bd = (LEASH + 4.0D) * (LEASH + 4.0D);
        for (Player p : level().players()) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator() || !onIceFloor(p)) {
                continue;
            }
            double d = p.distanceToSqr(c.getX() + 0.5D, p.getY(), c.getZ() + 0.5D);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** Is `e` down on her ice (or in its water) - and not up on the walkway or the stair. */
    public boolean onIceFloor(Entity e) {
        double dy = e.getY() - floorTop();
        return dy > -1.3D && dy < 1.2D;
    }

    @Override
    public boolean isPickable() {
        return !hidden() && super.isPickable();
    }

    @Override
    public boolean isPushable() {
        return !hidden() && super.isPushable();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;                                         // she does not fall: she glides
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
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

    // ------------------------------------------------------------------------------------------------ her time
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (home == null) {
            home = blockPosition();
        }
        if (!runesBack) {
            runesBack = true;
            runesLaid = false;                                 // a hall laid out with none: its runes come back
        }
        if (!runesLaid) {
            layRunes();
        }
        if (isDormant()) {
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            ensureDormantShadow();
            Player near = level().getNearestPlayer(getX(), getY(), getZ(), WAKE,
                    e -> e instanceof Player p && !p.isCreative() && !p.isSpectator()
                            && Math.abs(p.getY() - getY()) < 6.0D && com.jastkub.frozenfortress.BossCutscenes.witness(this, p));
            if (near != null && !com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.holdsBack(this)) {
                awaken();
                setTarget(near);
            }
        } else {
            // NOT FOR THOSE WHO KEEP OFF HER ICE: with nobody down on her floor - all of them up
            // on the walkway or the stair - she goes under it and waits there, where nothing from above reaches her,
            // and comes up after whoever steps down onto it
            if (nearestOnIce() == null) {
                if (++offIce > 40 && getAttackState() == 0 && !hidden()) {
                    lurking = true;
                    graspTarget = null;
                    setAttackState(SINK);
                }
            } else {
                offIce = 0;
            }
            beats();
            // she glides over her own black water, never down into it: come up over an open hole, she rides on it
            if (!hidden() && isInWater() && getY() < floorTop() + 0.1D) {
                setDeltaMovement(getDeltaMovement().x, 0.12D, getDeltaMovement().z);
            }
            if (!isAttacking() && home != null && distanceToSqr(Vec3.atBottomCenterOf(home)) > LEASH * LEASH) {
                setTarget(null);
                getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.0D);
            }
            if (tickCount % 40 == 0 && runesLaid && !bareFloor && runeCount() == 0) {
                layRunes();                                   // the floor always shows where it holds
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
        // her entrance, once for each who sees it (BossCutscenes), shot as a film: up out of the ice, slowly - or, when everyone here has seen it, the short rising
        introYaw = com.jastkub.frozenfortress.BossCutscenes.postYaw(this);
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        setAttackState(com.jastkub.frozenfortress.BossCutscenes.intro(this, INTRO_T) ? INTRO : AWAKEN);
    }

    /** Which way she faces through her entrance: her post's. */
    private float introYaw;

    /** Her entrance's beats (the clip's), and her kept where she drowned through it. */
    private void introBeats(int t) {
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        if (t == INTRO_GROAN) {
            playSound(FFSounds.DROWNED_LADY_ICE_GROAN.get(), 1.6F, 0.9F);
        }
        if (t == INTRO_CRACK) {
            playSound(FFSounds.DROWNED_LADY_ICE_CRACK.get(), 1.7F, 0.9F);
        }
        if (t == INTRO_BURST) {
            burst(1.1F);                                       // her hands up through the ice
            playSound(FFSounds.DROWNED_LADY_ICE_BREAK.get(), 1.9F, 0.85F);
        }
        if (t == INTRO_RISE + 2) {
            playSound(FFSounds.DROWNED_LADY_SPLASH.get(), 1.5F, 0.8F);   // the black water off her as she comes up
        }
        if (t == INTRO_RISEN - 4) {
            playSound(FFSounds.DROWNED_LADY_SPLASH.get(), 1.0F, 1.0F);
        }
        if (t >= INTRO_T) {
            finish(20);
        }
    }

    /** Every beat of every attack, told before it lands - in her body, in sound, and on the floor. */
    private void beats() {
        int st = getAttackState();
        int t = attackTicks;
        LivingEntity target = getTarget();
        switch (st) {
            case INTRO -> introBeats(t);
            case AWAKEN -> {
                if (t == AWAKEN_BURST) {
                    burst(1.3F);
                    playSound(FFSounds.DROWNED_LADY_ICE_BREAK.get(), 1.8F, 0.8F);
                }
                if (t == AWAKEN_WAIL) {
                    playSound(FFSounds.DROWNED_LADY_WAIL.get(), 2.4F, 1.0F);
                }
                if (t >= AWAKEN_T) {
                    finish(20);
                }
            }
            case LASH -> {
                if (t == 2) {
                    playSound(FFSounds.DROWNED_LADY_LASH_TELL.get(), 1.6F, 1.0F);      // the hair lifting, ringing
                }
                if (t == LASH_SPIN) {
                    playSound(FFSounds.DROWNED_LADY_LASH.get(), 1.8F, 1.0F);
                    AttackFxEntity.spawn(level(), "drowned_lash", position(), yBodyRot, 1.0F, 12, this);
                }
                if (t == LASH_HIT) {
                    lash();
                }
                if (t >= LASH_T) {
                    finish(14);
                }
            }
            case BREAK -> {
                if (t == 2) {
                    playSound(FFSounds.DROWNED_LADY_ICE_GROAN.get(), 1.8F, 1.0F);     // the ice groaning under her
                }
                if (t == BREAK_CLAW) {
                    breakIce(target);
                }
                if (t >= BREAK_T) {
                    finish(16);
                }
            }
            case SINK -> {
                if (t == 2) {
                    burst(0.9F);
                    playSound(FFSounds.DROWNED_LADY_SPLASH.get(), 1.6F, 0.8F);
                }
                if (t >= SINK_GONE) {
                    goUnder();
                }
            }
            case SUBMERGED -> underTheIce();
            case RISE -> {
                if (t == 1) {
                    burst(1.0F);
                    playSound(FFSounds.DROWNED_LADY_SPLASH.get(), 1.8F, 0.9F);
                }
                if (t == RISE_HIT) {
                    riseBlow();
                }
                if (t >= RISE_T) {
                    if (stunAfterRise) {
                        stunAfterRise = false;
                        setAttackState(STUNNED);
                        playSound(FFSounds.DROWNED_LADY_HURT.get(), 1.6F, 0.8F);
                    } else {
                        finish(18);
                    }
                }
            }
            case TIDE -> {
                if (t == 1) {
                    playSound(FFSounds.DROWNED_LADY_TIDE_TELL.get(), 2.0F, 1.0F);
                }
                if (t == TIDE_CAST) {
                    level().addFreshEntity(new DrownedTideEntity(level(), this));
                }
                if (t >= TIDE_T) {
                    finish(18);
                }
            }
            case STUNNED -> {
                getNavigation().stop();
                if (t >= STUN_T) {
                    finish(10);
                }
            }
            default -> {
            }
        }
    }

    private void finish(int after) {
        setAttackState(0);
        rest = secondHalf ? Math.max(4, after * 3 / 5) : after;   // her second half: barely a breath between
    }

    /** The ice bursting where she goes down or comes up - a burst of geometry (fx_drowned_burst), never a puff. It is
     *  thrown at yaw 0: AttackFxRenderer turns its glow layer twice, and this one is round anyway. */
    private void burst(float size) {
        AttackFxEntity.spawn(level(), "drowned_burst", new Vec3(getX(), floorTop(), getZ()), 0.0F, size, 24, this);
    }

    // ------------------------------------------------------------------------------------------------ LASH
    void lash() {
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(LASH_R, 1.5D, LASH_R),
                this::foe)) {
            double flat = Math.hypot(v.getX() - getX(), v.getZ() - getZ());
            if (flat > LASH_R || Math.abs(v.getY() - getY()) > 2.2D) {
                continue;
            }
            v.hurt(damageSources().mobAttack(this), LASH_DMG);
            Vec3 away = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
            v.knockback(1.0D, -away.x, -away.z);
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 60, 0), this);
        }
    }

    // ------------------------------------------------------------------------------------------------ BREAK
    /** Plates round her quarry start to crack: the one under them if it can go, and more about them. */
    void breakIce(@Nullable LivingEntity target) {
        if (home == null) {
            return;
        }
        // her hands go into the ice: whatever else, the ice round her bursts and whoever is close is struck
        burst(1.1F);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5D, 1.5D, 3.5D),
                this::foe)) {
            if (Math.hypot(v.getX() - getX(), v.getZ() - getZ()) <= 3.5D) {
                v.hurt(damageSources().mobAttack(this), 6.0F);
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
            }
        }
        if (secondHalf && tickCount >= greatAt) {
            greatAt = tickCount + 300;                         // the great break every fifteen seconds at most
            greatBreak();
            return;
        }
        int open = openPlates();
        int want = Math.min(secondHalf ? 5 : 3, (secondHalf ? MAX_HOLES_TWO : MAX_HOLES) - open);
        if (want <= 0) {
            return;
        }
        // round her quarry even off the floor (up on a plinth by the wall: the ice round it goes, so coming down costs)
        Entity about = target != null && Math.hypot(target.getX() - home.getX(), target.getZ() - home.getZ()) < LEASH + 2.0D
                ? target : this;
        int y = floorTop() - 1;
        List<BlockPos> chosen = new ArrayList<>();
        BlockPos under = new BlockPos((int) Math.floor(about.getX()), y, (int) Math.floor(about.getZ()));
        if (about == target && onIceFloor(target) && DrownedIcePlateEntity.runeUnder(target) == null
                && plateFits(under, chosen)) {
            chosen.add(under);
        }
        for (int tries = 0; tries < 40 && chosen.size() < want; tries++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double d = 2.5D + random.nextDouble() * 3.5D;
            BlockPos c = new BlockPos((int) Math.floor(about.getX() + Math.cos(a) * d), y,
                    (int) Math.floor(about.getZ() + Math.sin(a) * d));
            if (plateFits(c, chosen)) {
                chosen.add(c);
            }
        }
        for (BlockPos c : chosen) {
            DrownedIcePlateEntity.crack(level(), this, c);
        }
    }

    /**
     * HER SECOND HALF'S BREAK: plates crack all over her hall - about half of its ice, in broad drifts (three waves of sines
     * laid over each other, a new lie each time) so there are islands and causeways left between the water, never a
     * chessboard to step across. The same thirty ticks of cracking first: time to get to ice that will hold.
     */
    void greatBreak() {
        int y = floorTop() - 1;
        double a = random.nextDouble() * Math.PI * 2.0D, b = random.nextDouble() * Math.PI * 2.0D,
                c = random.nextDouble() * Math.PI * 2.0D;
        int r = (int) LEASH;
        for (int dx = -r; dx <= r; dx += 3) {
            for (int dz = -r; dz <= r; dz += 3) {
                BlockPos p = new BlockPos(home.getX() + dx, y, home.getZ() + dz);
                double v = Math.sin(p.getX() * 0.33D + a) + Math.sin(p.getZ() * 0.29D + b)
                        + 0.7D * Math.sin((p.getX() - p.getZ()) * 0.21D + c);
                if (v < 0.0D || !DrownedIcePlateEntity.canPlate(level(), p)) {
                    continue;
                }
                DrownedIcePlateEntity.crack(level(), this, p);
            }
        }
    }

    private boolean plateFits(BlockPos c, List<BlockPos> chosen) {
        for (BlockPos o : chosen) {
            if (Math.abs(o.getX() - c.getX()) < 3 && Math.abs(o.getZ() - c.getZ()) < 3) {
                return false;
            }
        }
        return DrownedIcePlateEntity.canPlate(level(), c);
    }

    /** Her plates still cracking or open. */
    int openPlates() {
        int n = 0;
        for (DrownedIcePlateEntity p : myPlates()) {
            if (p.isBreaking()) {
                n++;
            }
        }
        return n;
    }

    private int runeCount() {
        int n = 0;
        for (DrownedIcePlateEntity p : myPlates()) {
            if (p.isRune()) {
                n++;
            }
        }
        return n;
    }

    private List<DrownedIcePlateEntity> myPlates() {
        BlockPos c = home != null ? home : blockPosition();
        return level().getEntitiesOfClass(DrownedIcePlateEntity.class, new AABB(c).inflate(LEASH + 4.0D, 6.0D, LEASH + 4.0D),
                p -> p.ownedBy(this));
    }

    /**
     * THE RUNES IN THE FLOOR, laid before she ever wakes: six thick plates spread as wide over her ice as it allows
     * (each next one as far from the others as it can be), so that from anywhere on the floor one is a few strides
     * away. Found on the ice itself, not at fixed places - the citadel puts her room wherever the world puts it.
     */
    private void layRunes() {
        runesLaid = true;
        //. Without LAY_RUNES any rune an older world has is taken up
        if (home != null && !LAY_RUNES) {
            for (DrownedIcePlateEntity p : level().getEntitiesOfClass(DrownedIcePlateEntity.class,
                    new AABB(home).inflate(20.0D, 6.0D, 20.0D), DrownedIcePlateEntity::isRune)) {
                p.discard();
            }
        }
        if (!LAY_RUNES || home == null || runeCount() > 0) {
            return;
        }
        int y = floorTop() - 1;
        List<BlockPos> free = new ArrayList<>();
        for (int dx = -14; dx <= 14; dx++) {
            for (int dz = -14; dz <= 14; dz++) {
                BlockPos c = new BlockPos(home.getX() + dx, y, home.getZ() + dz);
                if (DrownedIcePlateEntity.canPlate(level(), c)) {
                    free.add(c);
                }
            }
        }
        List<BlockPos> laid = new ArrayList<>();
        BlockPos start = new BlockPos(home.getX(), y, home.getZ());
        while (laid.size() < RUNES && !free.isEmpty()) {
            BlockPos best = null;
            double bestD = -1.0D;
            for (BlockPos c : free) {
                double d = Double.MAX_VALUE;
                for (BlockPos o : laid) {
                    d = Math.min(d, Math.max(Math.abs(o.getX() - c.getX()), Math.abs(o.getZ() - c.getZ())));
                }
                // the first is the one farthest from where she lies; none right on top of her
                double fromHome = Math.max(Math.abs(start.getX() - c.getX()), Math.abs(start.getZ() - c.getZ()));
                if (fromHome < 4.0D) {
                    continue;
                }
                d = laid.isEmpty() ? fromHome : d;
                if (d > bestD) {
                    bestD = d;
                    best = c;
                }
            }
            if (best == null || (!laid.isEmpty() && bestD < 6.0D)) {
                break;
            }
            laid.add(best);
            free.remove(best);
        }
        bareFloor = laid.isEmpty();
        for (BlockPos c : laid) {
            DrownedIcePlateEntity.rune(level(), this, c);
        }
    }

    // ------------------------------------------------------------------------------------------------ GRASP
    /** She goes down through the ice after `target` (the goal chose it). */
    void startGrasp(LivingEntity target) {
        graspTarget = target;
        setAttackState(SINK);
    }

    private void goUnder() {
        LivingEntity target = graspTarget != null && graspTarget.isAlive() ? graspTarget : getTarget();
        setAttackState(SUBMERGED);
        hiddenTicks = 0;
        this.noPhysics = true;
        setNoGravity(true);
        getNavigation().stop();
        if (lurking) {                                         // only her shape, lying still where she went down
            shadowId = DrownedShadowEntity.dormant(level(), this, blockPosition()).getUUID();
            return;
        }
        if (target != null) {
            shadowId = DrownedShadowEntity.hunt(level(), this, target).getUUID();
        }
    }

    /** Under the ice she goes with her shape; lost (her shape gone and no hands up), she comes up where she is. */
    private void underTheIce() {
        hiddenTicks++;
        setDeltaMovement(Vec3.ZERO);
        if (lurking) {
            Player down = nearestOnIce();
            if (down == null) {
                return;                                        // nobody on her ice: she waits
            }
            // someone came down: her waiting shape fades (DrownedShadowEntity) and the hunting one sets out
            lurking = false;
            hiddenTicks = 0;
            graspTarget = down;
            setTarget(down);
            shadowId = DrownedShadowEntity.hunt(level(), this, down).getUUID();
            return;
        }
        Entity sh = shadowId != null && level() instanceof ServerLevel s ? s.getEntity(shadowId) : null;
        if (sh instanceof DrownedShadowEntity shadow && shadow.mode() == DrownedShadowEntity.HUNT) {
            setPos(shadow.getX(), floorTop(), shadow.getZ());
            return;
        }
        boolean handsUp = !level().getEntitiesOfClass(DrownedHandsEntity.class, getBoundingBox().inflate(4.0D)).isEmpty();
        if ((!handsUp && hiddenTicks > 20) || hiddenTicks > 240) {
            surfaceAt(position(), false);
        }
    }

    /** Her hands are done - she comes up out of the floor where they were (reeling, and wounded, if they were
     *  broken off whoever they held). */
    public void handsDone(Vec3 at, boolean broken, @Nullable Entity by) {
        if (broken) {
            woundBlow = true;
            try {
                hurt(by instanceof LivingEntity le ? damageSources().mobAttack(le) : damageSources().magic(), HANDS_WOUND);
            } finally {
                woundBlow = false;
            }
        }
        if (isAlive()) {
            surfaceAt(at, broken);
        }
    }

    private void surfaceAt(Vec3 at, boolean reeling) {
        this.noPhysics = false;
        setNoGravity(false);
        shadowId = null;
        moveTo(at.x, floorTop(), at.z, getYRot(), 0.0F);
        stunAfterRise = reeling;
        setAttackState(RISE);
    }

    /** Coming up through the ice she throws off whoever stands where she breaks out. */
    private void riseBlow() {
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.6D, 0.5D, 1.6D),
                this::foe)) {
            v.hurt(damageSources().mobAttack(this), RISE_DMG);
            Vec3 away = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
            v.knockback(0.9D, -away.x, -away.z);
            v.setDeltaMovement(v.getDeltaMovement().add(0.0D, 0.45D, 0.0D));
            v.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------------------------------------ asleep
    private void ensureDormantShadow() {
        if (home == null || tickCount % 20 != 0) {
            return;
        }
        Entity sh = shadowId != null && level() instanceof ServerLevel s ? s.getEntity(shadowId) : null;
        if (!(sh instanceof DrownedShadowEntity) || !sh.isAlive()) {
            shadowId = DrownedShadowEntity.dormant(level(), this, home).getUUID();
        }
    }

    // ------------------------------------------------------------------------------------------------ hurt, death
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!woundBlow && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (isDormant()) {
                if (!level().isClientSide && source.getEntity() instanceof Player) {
                    awaken();
                }
                return false;
            }
            int st = getAttackState();
            // under the ice nothing reaches her - from the moment she is mostly gone until she is mostly back
            if (st == SUBMERGED || (st == SINK && attackTicks >= 12) || (st == RISE && attackTicks < 6)
                    || (st == AWAKEN && attackTicks < 16) || st == INTRO) {    // (her entrance: a scene is not a fight)
                return false;
            }
            if (st == AWAKEN) {
                amount *= 0.5F;                                // the ice still on her as she comes up
            } else if (st == STUNNED) {
                amount *= 1.4F;                                // reeling, her hands broken: open
            }
        }
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && !secondHalf && isAlive() && getHealth() < getMaxHealth() * 0.5F) {
            // HER SECOND HALF: she wails, the tide comes at once, and everything after it comes harder
            secondHalf = true;
            tideNow = true;
            greatAt = 0;                                       // and the great break comes as soon as the tide is out
            playSound(FFSounds.DROWNED_LADY_WAIL.get(), 2.4F, 1.05F);
            AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && speed.getModifier(FURY) == null) {
                speed.addPermanentModifier(new AttributeModifier(FURY, "drowned lady's fury", 0.35D,
                        AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        bossEvent.removeAllPlayers();
        super.die(source);
        if (!level().isClientSide) {
            // her way out north rises (its exit gate)
            com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.keeperFell(level(), this);
            // the floor is hers no longer: the holes skin over, the runes sink back, her shape and hands are gone
            for (DrownedIcePlateEntity p : myPlates()) {
                p.ladyDied();
            }
            for (DrownedShadowEntity s : level().getEntitiesOfClass(DrownedShadowEntity.class, getBoundingBox().inflate(32.0D),
                    s -> s.ownedBy(this))) {
                s.discard();
            }
            this.noPhysics = false;
            setNoGravity(false);
        }
    }

    /** Her body (under the ice by then, but for her crown) is kept until her death scene goes to black (BossCutscenes:
     *  the clip, a breath, the card). */
    @Override
    protected int getDeathDuration() {
        return DEATH_T + 36;
    }

    /** Her death's sounds and the ice's bursts, on its clip's beats: the ice groaning under her, breaking open as she
     *  drops into it (a burst of ice, geometry), the water closing over the last of her hand. */
    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (level().isClientSide) {
            return;
        }
        int t = deathTime - DEATH_LAG;
        if (t == DEATH_GROAN) {
            playSound(FFSounds.DROWNED_LADY_ICE_GROAN.get(), 1.6F, 0.8F);
        }
        if (t == DEATH_CRACK) {
            burst(1.5F);
            playSound(FFSounds.DROWNED_LADY_ICE_BREAK.get(), 2.0F, 0.85F);
            playSound(FFSounds.DROWNED_LADY_SPLASH.get(), 1.6F, 0.7F);
        }
        if (t == DEATH_UNDER) {
            playSound(FFSounds.DROWNED_LADY_SPLASH.get(), 0.9F, 1.1F);
        }
        if (t == DEATH_GONE) {
            burst(0.7F);
            playSound(FFSounds.DROWNED_LADY_SPLASH.get(), 1.0F, 1.3F);
            playSound(FFSounds.ICE_CRACK.get(), 0.8F, 1.2F);
        }
    }

    // ------------------------------------------------------------------------------------------------ saved
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Dormant", isDormant());
        tag.putBoolean("RunesLaid", runesLaid);
        tag.putBoolean("SecondHalf", secondHalf);
        tag.putBoolean("RunesBack", runesBack);
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
        runesLaid = tag.getBoolean("RunesLaid");
        secondHalf = tag.getBoolean("SecondHalf");
        runesBack = tag.getBoolean("RunesBack");
        int[] h = tag.getIntArray("Home");
        if (h.length == 3) {
            home = new BlockPos(h[0], h[1], h[2]);
        }
        // saved while under the ice (her state is not kept): she is back on her floor, solid again
        setNoGravity(false);
        this.noPhysics = false;
    }

    // ------------------------------------------------------------------------------------------------ looks, sounds
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            // into the entrance at once (its first frame is the sleep it wakes from): its clip runs on the very
            // ticks the scene's cuts and sounds are timed to (BossScenes) - and out of it as from any other
            state.getController().setTransitionLength(getAttackState() == INTRO ? 0 : 4);
            if (isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (isDormant()) {
                return state.setAndContinue(DORMANT_ANIM);
            }
            switch (getAttackState()) {
                case AWAKEN: return state.setAndContinue(AWAKEN_ANIM);
                case INTRO: return state.setAndContinue(INTRO_ANIM);
                case LASH: return state.setAndContinue(LASH_ANIM);
                case BREAK: return state.setAndContinue(BREAK_ANIM);
                case SINK: return state.setAndContinue(SINK_ANIM);
                case SUBMERGED: return state.setAndContinue(SUBMERGED_ANIM);
                case RISE: return state.setAndContinue(RISE_ANIM);
                case TIDE: return state.setAndContinue(TIDE_ANIM);
                case STUNNED: return state.setAndContinue(STUNNED_ANIM);
                default: break;
            }
            return state.setAndContinue(FrostServantEntity.going(state) ? GLIDE : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && !hidden() && hurtTime > 0 && !isAttacking()
                        ? state.setAndContinue(HURT) : PlayState.STOP));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return hidden() ? null : FFSounds.DROWNED_LADY_IDLE.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.DROWNED_LADY_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.DROWNED_LADY_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // she has no feet to step with
    }

    private boolean foe(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof FrostServantEntity) && !(e instanceof DrownedHandsEntity)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ------------------------------------------------------------------------------------------------ the fight
    /**
     * Chooses what she does next and walks her to it; the beats of what she does are hers (aiStep), so a lost target
     * never leaves her half under the ice.
     */
    static class DrownedAttackGoal extends Goal {
        private final DrownedLadyEntity mob;
        private final int[] perAttack = new int[9];

        DrownedAttackGoal(DrownedLadyEntity mob) {
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
            for (int i = 0; i < perAttack.length; i++) {
                if (perAttack[i] > 0) {
                    perAttack[i]--;
                }
            }
            int state = mob.getAttackState();
            int t = mob.attackTicks;
            if (state != 0) {
                mob.getNavigation().stop();
                // she keeps her eyes on them through each tell - but not through the turn of the lash, which is
                // drawn about the way she faced when it began
                if ((state == LASH && t < LASH_SPIN - 2) || (state == BREAK && t < BREAK_CLAW)
                        || (state == TIDE && t < 30) || state == RISE) {
                    mob.getLookControl().setLookAt(target, 20.0F, 20.0F);
                }
                return;
            }
            double dist = Math.hypot(target.getX() - mob.getX(), target.getZ() - mob.getZ());
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (dist > 2.4D) {
                mob.getNavigation().moveTo(target, 1.0D);
            } else {
                mob.getNavigation().stop();
            }
            if (mob.rest > 0) {
                mob.rest--;
                return;
            }
            boolean two = mob.inSecondHalf();
            boolean floor = mob.onIceFloor(target);
            boolean onRune = DrownedIcePlateEntity.runeUnder(target) != null;
            int chosen = 0;
            if (mob.tideNow) {
                mob.tideNow = false;
                chosen = TIDE;
                // her line in the fight, once, as the black water of her second half rises (BossVoice): "Breathe
                // deeply. This will be your last breath."
                BossVoice.fightLine(mob, "drowned_lady");
            } else if (dist < LASH_REACH && Math.abs(target.getY() - mob.getY()) < 2.2D && perAttack[LASH] <= 0) {
                chosen = LASH;
            } else if (floor) {
                // weighed, so no fight goes the same way twice; each one only when its time has come round again
                int wBreak = perAttack[BREAK] <= 0 && mob.openPlates() < (two ? MAX_HOLES_TWO : MAX_HOLES) - 1 ? 3 : 0;
                int wGrasp = perAttack[SINK] <= 0 && !onRune && dist > 3.0D ? 2 : 0;
                int wTide = perAttack[TIDE] <= 0 && dist < DrownedTideEntity.R1 - 1.0D ? (mob.openPlates() >= 2 ? 3 : 2) : 0;
                int sum = wBreak + wGrasp + wTide;
                if (sum > 0) {
                    int r = mob.random.nextInt(sum);
                    chosen = r < wBreak ? BREAK : r < wBreak + wGrasp ? SINK : TIDE;
                }
            } else if (perAttack[BREAK] <= 0 && dist < 14.0D) {
                chosen = BREAK;                                // off her floor (up by a wall): the ice round you goes
            }
            if (chosen == 0) {
                return;
            }
            perAttack[chosen] = switch (chosen) {
                case LASH -> 30;
                case BREAK -> two ? 110 : 140;                 // (second half: a small break between the great ones)
                case SINK -> two ? 200 : 260;
                case TIDE -> two ? 240 : 320;
                default -> 0;
            };
            mob.getNavigation().stop();
            if (chosen == SINK) {
                mob.startGrasp(target);
            } else {
                mob.setAttackState(chosen);
            }
        }
    }
}
