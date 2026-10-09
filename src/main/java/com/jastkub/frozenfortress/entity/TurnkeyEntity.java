package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
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
import net.minecraft.world.BossEvent;
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
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;

import java.util.EnumSet;

/**
 * KLUCZNIK - THE TURNKEY. The gaoler of the citadel's prisons, asleep on his
 * feet at his post in the hall at the bottom of the cells, the keys of every
 * door on the floor at his hip. Wake him and he walks his rounds one more
 * time - with you as the prisoner.
 *
 * <p>His part in the citadel: he carries the Crypt Key and the Ancestors' Key,
 * two of the three that open the first floor's hall, and drops them when he
 * falls. He does not leave his hall: past sixteen blocks from where he slept
 * he goes back.
 *
 * <p>Attack states:
 * <ol>
 *   <li>LASH - the chain swung round in front of him, an arc of five and a half</li>
 *   <li>SHACKLE - the manacle thrown at whoever keeps their distance; a hit
 *       holds them in irons a second and a half and the chain YANKS them in</li>
 *   <li>SLAM - both fists into the floor, a line of ice bursting toward the target</li>
 *   <li>AWAKEN - played once</li>
 *   <li>LOCKDOWN - once, at half his health: he holds the key ring up and
 *       shakes it, the cells lock - an ice prison round his target - and two
 *       prisoners walk out of them</li>
 * </ol>
 */
public class TurnkeyEntity extends FrostServantEntity implements GateKeeper {

    private static final EntityDataAccessor<Boolean> DORMANT =
            SynchedEntityData.defineId(TurnkeyEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int LASH = 1, SHACKLE = 2, SLAM = 3, AWAKEN = 4, LOCKDOWN = 5, KEYRING = 6, STAGGER = 7, CAGE = 8,
            LANTERN = 9, INTRO = 10;
    /** HIS ENTRANCE (tools/gen_turnkey.py INTRO_*: the "intro" clip - change one, change both; BossScenes cuts its shots
     *  to them): startled awake, the lantern searching his hall, three shakes of the keys, leaning in at you. */
    public static final int INTRO_T = 160, INTRO_STIR = 12, INTRO_LEAN = 66, INTRO_RATTLE = 124;
    public static final int[] INTRO_KEYS = {40, 48, 56};
    /** HIS DEATH (tools/gen_turnkey.py DEATH_*: the "death" clip - change one, change both; BossScenes' death film is
     *  cut to them): thrown back, down on his knees (KNEEL), the keys slipping off the ring one by one (KEYS, each on
     *  the floor KEY_FALL later), the lantern's cold flame guttering out (GUTTER), the lantern let fall (DROP), over onto
     *  his face (FALL). The clip runs DEATH_LAG behind his deathTime: the controller blends into it first. */
    public static final int DEATH_T = 80, DEATH_KNEEL = 24, DEATH_GUTTER = 54, DEATH_DROP = 58, DEATH_FALL = 68;
    public static final int[] DEATH_KEYS = {34, 40, 46};
    public static final int DEATH_KEY_FALL = 5, DEATH_LANTERN_FALL = 6, DEATH_LAG = 4;

    private static final String P = "animation.turnkey.";
    private static final RawAnimation DORMANT_ANIM = RawAnimation.begin().thenLoop(P + "dormant");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation AWAKEN_ANIM = RawAnimation.begin().thenPlay(P + "awaken");
    private static final RawAnimation INTRO_ANIM = RawAnimation.begin().thenPlay(P + "intro");
    private static final RawAnimation LASH_ANIM = RawAnimation.begin().thenPlay(P + "lash");
    private static final RawAnimation SHACKLE_ANIM = RawAnimation.begin().thenPlay(P + "shackle");
    private static final RawAnimation SLAM_ANIM = RawAnimation.begin().thenPlay(P + "slam");
    private static final RawAnimation LOCKDOWN_ANIM = RawAnimation.begin().thenPlay(P + "lockdown");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay(P + "hurt");
    private static final RawAnimation KEYRING_ANIM = RawAnimation.begin().thenPlay(P + "keyring");
    private static final RawAnimation LANTERN_ANIM = RawAnimation.begin().thenPlay(P + "lantern");
    private static final RawAnimation STAGGER_ANIM = RawAnimation.begin().thenLoop(P + "hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay(P + "death");

    /** How far from where he slept he will go before he turns back. */
    private static final double LEASH = 16.0D;
    /** How near you can come, in his sight, before he wakes. */
    private static final double WAKE_SEEN = 16.0D;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.frozen_dominion.turnkey"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);

    private BlockPos home;
    private boolean lockedDown;
    /** The locks in the pillars of his hall, found when he wakes: where his keys fly. */
    private final java.util.List<BlockPos> locks = new java.util.ArrayList<>();
    /** How many throws of the ring he has made (at 80, 55 and 30 per cent). */
    private int keyrings;
    private int keysOut;
    private int keysBroken;
    private int keysThrown;
    private int staggerTicks;

    public TurnkeyEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 120;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 260.0D)
                .add(Attributes.ATTACK_DAMAGE, 11.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85D)
                .add(Attributes.FOLLOW_RANGE, 28.0D);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DORMANT, true);
    }

    public boolean isDormant() {
        return entityData.get(DORMANT);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new TurnkeyAttackGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
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
        bossEvent.setColor(lockedDown ? BossEvent.BossBarColor.WHITE : BossEvent.BossBarColor.BLUE);
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

    @Override
    public void die(DamageSource source) {
        bossEvent.removeAllPlayers();
        super.die(source);
        if (!level().isClientSide) {
            level().playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
    }

    // ------------------------------------------------------------------ his round
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (home == null) {
            home = blockPosition();
        }
        if (isDormant()) {
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            //
            // sixteen now, still only in his sight (nobody wakes him through the wall of his hall) - and it is
            // you he gets up for
            Player near = level().getNearestPlayer(this, WAKE_SEEN);
            if (near != null && !near.isCreative() && !near.isSpectator() && hasLineOfSight(near) && !com.jastkub.frozenfortress.block.entity.BossGateBlockEntity.holdsBack(this)) {
                awaken();
                setTarget(near);
            }
        } else {
            if (getAttackState() == AWAKEN && attackTicks > 46) {
                setAttackState(0);
            }
            if (getAttackState() == INTRO) {
                introBeats(attackTicks);
            }
            // he does not leave his hall: too far, and he walks back to it
            if (home != null && distanceToSqr(Vec3.atCenterOf(home)) > LEASH * LEASH && !isAttacking()) {
                setTarget(null);
                getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.0D);
            }
            // the keys: a rattle now and then, so you hear him before you see him
            if (tickCount % 47 == 0 && random.nextFloat() < 0.6F) {
                playSound(SoundEvents.CHAIN_STEP, 0.9F, 0.55F + random.nextFloat() * 0.2F);
            }
        }
        if (getAttackState() == STAGGER && --staggerTicks <= 0) {
            setAttackState(0);
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
        findLocks();
        entityData.set(DORMANT, false);
        // his entrance, once for each who sees it (BossCutscenes), shot as a film: startled awake, the lantern searching
        // his hall, the keys shaken, leaning in at you - or, when everyone here has seen it, the short waking as before
        introYaw = com.jastkub.frozenfortress.BossCutscenes.postYaw(this);
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        if (com.jastkub.frozenfortress.BossCutscenes.intro(this, INTRO_T)) {
            setAttackState(INTRO);
            return;
        }
        setAttackState(AWAKEN);
        level().playSound(null, blockPosition(), FFSounds.VAULT_WARDEN_AWAKEN.get(), SoundSource.HOSTILE, 1.6F, 0.7F);
        level().playSound(null, blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    /** Which way he faces through his entrance: his post's. */
    private float introYaw;

    /** His entrance's beats (the clip's), and him kept at his post through it. */
    private void introBeats(int t) {
        com.jastkub.frozenfortress.BossCutscenes.holdPose(this, introYaw);
        if (t == INTRO_STIR) {
            playSound(FFSounds.VAULT_WARDEN_AWAKEN.get(), 1.4F, 0.7F);
            playSound(SoundEvents.CHAIN_STEP, 1.2F, 0.6F);
        }
        if (t == 22) {
            playSound(SoundEvents.LANTERN_PLACE, 1.2F, 0.7F);            // the lantern up
        }
        for (int k = 0; k < INTRO_KEYS.length; k++) {
            if (t == INTRO_KEYS[k]) {
                playSound(FFSounds.TURNKEY_KEYS.get(), 1.5F, 1.0F - 0.05F * k);
            }
        }
        if (t == INTRO_LEAN - 4) {
            playSound(FFSounds.TURNKEY_LOCK.get(), 1.6F, 0.9F);         // somewhere in his hall, a lock thrown
        }
        if (t == INTRO_RATTLE) {
            playSound(FFSounds.TURNKEY_KEYS.get(), 0.7F, 0.85F);
        }
        if (t >= INTRO_T) {
            setAttackState(0);
        }
    }

    /** THE LAST RING: his last fifty points of health come
     *  off him only through his keys - any other blow does one point at most,
     *  a struck key does ten, a key that reaches its lock gives him fifteen back (07.10.2026; ten before). */
    public static final float LAST_RING = 50.0F;
    static final float KEY_WORTH = 10.0F;
    /** What a key that reaches its lock mends him by. */
    static final float KEY_HEAL = 15.0F;
    private boolean keyBlow;
    private int lastKeyring = -1000;

    boolean inLastRing() {
        return getHealth() <= LAST_RING;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isDormant() && source.getEntity() != null) {
            awaken();
        }
        if (getAttackState() == INTRO && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;                                      // his entrance: a scene is not a fight
        }
        if (getAttackState() == AWAKEN || getAttackState() == LOCKDOWN) {
            amount *= 0.5F;                                    // braced while he wakes, and while he locks the cells
        }
        if (getAttackState() == STAGGER) {
            amount *= 1.3F;                                    // staggered: his guard is down
        }
        if (!keyBlow && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && inLastRing()) {
            // THE WARD: under the last ring nothing gets through but his keys
            if (!level().isClientSide) {
                playSound(SoundEvents.ANVIL_PLACE, 0.6F, 1.8F);
                playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.4F, 0.5F);
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    /**
     * A blow ends at the last ring, never through it - cut here, after his armour, not before it: a blow trimmed to what was left
     * above the ring lost a part of that again to his armour, so every blow only halved the way to fifty and he
     * never reached it - no ward, no keys, ever.
     */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        float before = getHealth();
        super.actuallyHurt(source, amount);
        if (!keyBlow && before > LAST_RING && getHealth() < LAST_RING
                && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            setHealth(LAST_RING);
        }
    }

    // ------------------------------------------------------------------ the attacks
    /**
     * THE LANTERN: whoever
     * the manacle has hauled in is his - he swings the lantern up over his head on its chain and brings it down on
     * them. Called by the manacle on its first haul; false if he is in the middle of something he will not leave.
     */
    public boolean lanternFor(LivingEntity shackled) {
        int st = getAttackState();
        if (isDormant() || isDeadOrDying() || shackled == null || !shackled.isAlive()
                || st == AWAKEN || st == INTRO || st == LOCKDOWN || st == KEYRING || st == CAGE || st == STAGGER
                || st == LANTERN) {
            return false;
        }
        setTarget(shackled);
        getNavigation().stop();
        setAttackState(LANTERN);
        return true;
    }

    /** The lantern comes down before him: hard, and the cold in it bursts out over the floor (FrostWaveEntity). */
    void lanternSmash(LivingEntity target) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 at = position().add(fwd.scale(1.8D));
        boolean hit = false;
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4.5D),
                e -> e != this && !(e instanceof FrostServantEntity) && e.isAlive())) {
            if (v.position().distanceTo(at) > 2.6D) {
                continue;
            }
            v.hurt(damageSources().mobAttack(this), 14.0F);
            Vec3 away = v.position().subtract(position());
            v.knockback(1.2D, -away.x, -away.z);
            v.setDeltaMovement(v.getDeltaMovement().add(0.0D, 0.35D, 0.0D));
            v.hurtMarked = true;
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 100, 0), this);
            hit = true;
        }
        playSound(SoundEvents.ANVIL_LAND, 1.6F, 0.55F);
        playSound(FFSounds.ICE_IMPACT.get(), 1.8F, 0.8F);
        playSound(SoundEvents.GLASS_BREAK, 1.2F, hit ? 0.6F : 0.8F);
        level().addFreshEntity(new FrostWaveEntity(level(), this, at.x, getY(), at.z, 3.4F, 12, 3.0F).light());
    }

    void lash() {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, getYRot());
        boolean hit = false;
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5.5D),
                e -> e != this && !(e instanceof FrostServantEntity) && e.isAlive())) {
            Vec3 to = v.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            if (flat.length() > 5.6D || flat.normalize().dot(fwd) < 0.35D) {
                continue;
            }
            v.hurt(damageSources().mobAttack(this), 10.0F);
            v.knockback(0.9D, -flat.x, -flat.z);
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 0), this);
            hit = true;
        }
        playSound(SoundEvents.CHAIN_HIT, 1.8F, hit ? 0.7F : 0.9F);
        if (level() instanceof ServerLevel s) {
            for (int i = -3; i <= 3; i++) {
                Vec3 d = Vec3.directionFromRotation(0.0F, getYRot() + i * 20.0F).scale(4.0D);
                s.sendParticles(FFParticles.ICE_SHARD.get(), getX() + d.x, getY() + 1.0D, getZ() + d.z,
                        2, 0.2D, 0.2D, 0.2D, 0.02D);
            }
        }
    }

    /** The manacle thrown on its chain - a thing in the air, to be side-stepped (TurnkeyManacleEntity). */
    void throwShackle(LivingEntity target) {
        Vec3 from = handPosition();
        Vec3 at = target.getEyePosition().add(target.getDeltaMovement().scale(6.0D)).add(0.0D, -0.4D, 0.0D);
        level().addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.TurnkeyManacleEntity(level(), this, from, at));
        playSound(SoundEvents.CHAIN_PLACE, 2.0F, 0.6F);
    }

    /** His right hand, where the chain comes from. */
    public Vec3 handPosition() {
        Vec3 right = Vec3.directionFromRotation(0.0F, yBodyRot + 90.0F);
        return position().add(right.scale(0.75D)).add(0.0D, 1.6D, 0.0D);
    }

    /** He hauls on the chain: whoever is in the irons comes to him. */
    public void yank(LivingEntity shackled) {
        if (shackled == null || !shackled.isAlive()) {
            return;
        }
        Vec3 pull = position().subtract(shackled.position());
        double len = pull.length();
        if (len > 2.5D) {
            Vec3 v = pull.normalize().scale(Math.min(1.5D, len * 0.2D));
            shackled.setDeltaMovement(v.x, 0.35D, v.z);
            shackled.hurtMarked = true;
        }
        playSound(SoundEvents.CHAIN_BREAK, 1.6F, 0.6F);
    }

    /** Both fists into the floor: a line of ice spikes bursts out toward the target, one after another. */
    void slam(LivingEntity target) {
        playSound(FFSounds.VAULT_WARDEN_SLAM.get(), 1.8F, 0.8F);
        Vec3 dir = target.position().subtract(position());
        dir = new Vec3(dir.x, 0.0D, dir.z).normalize();
        if (dir.lengthSqr() < 1.0E-4) {
            dir = Vec3.directionFromRotation(0.0F, getYRot());
        }
        for (int i = 1; i <= 10; i++) {
            Vec3 p = position().add(dir.scale(i * 1.1D));
            level().addFreshEntity(new com.jastkub.frozenfortress.entity.projectile.IceSpikeEntity(level(), this,
                    p.x, getY(), p.z, 11.0F, 2 + i * 2));
        }
        playSound(FFSounds.FROST_RELEASE.get(), 2.0F, 0.6F);
    }

    void lockdown(LivingEntity target) {
        lockedDown = true;
        // his line in the fight, once, as the cells lock and their prisoners walk out (BossVoice): "You have opened the
        // door. Now face what lies beyond."
        BossVoice.fightLine(this, "turnkey");
        level().playSound(null, blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.HOSTILE, 2.0F, 0.5F);
        level().playSound(null, blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 2.0F, 0.4F);
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        cage(target);
        // and the prisoners out of theirs
        for (int k = 0; k < 2; k++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            BlockPos at = BlockPos.containing(getX() + Math.cos(a) * 4.0D, getY(), getZ() + Math.sin(a) * 4.0D);
            FrostboundSentinelEntity prisoner = FFEntities.FROSTBOUND_SENTINEL.get().create(s);
            if (prisoner == null) {
                continue;
            }
            prisoner.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            if (!s.noCollision(prisoner)) {
                continue;
            }
            prisoner.finalizeSpawn(s, s.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null);
            prisoner.setTarget(target);
            s.addFreshEntity(prisoner);
            s.sendParticles(FFParticles.ICE_SHARD.get(), at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D,
                    40, 0.5D, 1.0D, 0.5D, 0.1D);
        }
    }

    /** When his last cage came down: never two at once. */
    private int lastCage = -10000;
    static final int CAGE_GAP = 400;

    boolean cageReady() {
        return tickCount - lastCage > CAGE_GAP && level().getEntitiesOfClass(PortcullisEntity.class,
                getBoundingBox().inflate(24.0D), PortcullisEntity::isAlive).isEmpty();
    }

    /** Four grates out of the vault round the target: a cell. */
    void cage(LivingEntity target) {
        if (!cageReady()) {
            return;
        }
        lastCage = tickCount;
        double x = target.getX(), y = Math.floor(target.getY()), z = target.getZ();
        level().playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 2.0F, 0.4F);
        level().addFreshEntity(new PortcullisEntity(level(), this, x, y, z - 2.0D, true));
        level().addFreshEntity(new PortcullisEntity(level(), this, x, y, z + 2.0D, true));
        level().addFreshEntity(new PortcullisEntity(level(), this, x - 2.0D, y, z, false));
        level().addFreshEntity(new PortcullisEntity(level(), this, x + 2.0D, y, z, false));
    }

    // ------------------------------------------------------------------ the key ring
    /** The locks in his hall's pillars (TurnkeyLockBlock), within twenty of where he slept. */
    private void findLocks() {
        locks.clear();
        BlockPos c = home != null ? home : blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-20, -3, -20), c.offset(20, 8, 20))) {
            if (level().getBlockState(p).getBlock() instanceof com.jastkub.frozenfortress.block.TurnkeyLockBlock) {
                locks.add(p.immutable());
            }
        }
    }

    /** The ring held up and flung: three keys, then four, then five, each to a lock. */
    void keyring() {
        if (locks.isEmpty()) {
            findLocks();
        }
        int n = inLastRing() ? 5 : 3 + keyrings;
        keyrings++;
        lastKeyring = tickCount;
        keysOut = n;
        keysThrown = n;
        keysBroken = 0;
        // they burst off the ring at his hip (it never leaves his belt), up and out
        Vec3 from = position().add(Vec3.directionFromRotation(0.0F, yBodyRot + 90.0F).scale(0.6D)).add(0.0D, 1.4D, 0.0D);
        // ...and fan out wide before they make for their locks: a ring of
        // radius 3.6 round him, every other one higher - at five keys more than four blocks
        // apart, so no single sweep takes two of them
        int start = random.nextInt(Math.max(1, locks.size()));
        double turn = random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < n; i++) {
            BlockPos lock = locks.isEmpty() ? null : locks.get((start + i) % locks.size());
            double a = turn + Math.PI * 2.0D * i / n;
            Vec3 at = from.add(Math.cos(a) * 0.5D, 0.0D, Math.sin(a) * 0.5D);
            Vec3 out = position().add(Math.cos(a) * 3.6D, i % 2 == 0 ? 1.3D : 2.7D, Math.sin(a) * 3.6D);
            level().addFreshEntity(new TurnkeyKeyEntity(level(), this, lock, at, out));
        }
        playSound(SoundEvents.CHAIN_BREAK, 2.0F, 1.2F);
        playSound(SoundEvents.ANVIL_PLACE, 0.8F, 1.6F);
    }

    /** A key turned in its lock: the cells answer him - he is mended. */
    public void keyTurned(TurnkeyKeyEntity key, @javax.annotation.Nullable BlockPos lock) {
        keysOut = Math.max(0, keysOut - 1);
        heal(KEY_HEAL);
        if (level() instanceof ServerLevel s) {
            Vec3 from = key.position();
            Vec3 to = position().add(0.0D, 2.0D, 0.0D);
            for (int i = 0; i <= 12; i++) {
                Vec3 p = from.add(to.subtract(from).scale(i / 12.0D));
                s.sendParticles(FFParticles.SOUL_FROST.get(), p.x, p.y, p.z, 2, 0.05D, 0.05D, 0.05D, 0.0D);
            }
        }
        playSound(SoundEvents.CHAIN_HIT, 1.4F, 1.5F);
    }

    /** A key struck down: it costs him - and every key of a throw struck down staggers him. */
    public void keyBroken(TurnkeyKeyEntity key, @javax.annotation.Nullable net.minecraft.world.entity.Entity by) {
        keysOut = Math.max(0, keysOut - 1);
        keysBroken++;
        keyBlow = true;
        try {
            hurt(damageSources().indirectMagic(key, by), KEY_WORTH);
        } finally {
            keyBlow = false;
        }
        if (keysOut == 0 && keysBroken >= keysThrown && isAlive()) {
            staggerTicks = 60;
            setAttackState(STAGGER);
            getNavigation().stop();
            level().playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.6F, 0.5F);
            if (level() instanceof ServerLevel s) {
                s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 2.0D, getZ(), 40, 0.6D, 1.0D, 0.6D, 0.1D);
            }
        }
    }

    boolean keyringDue() {
        if (inLastRing()) {
            // in the last ring he throws again three seconds after the last throw is spent, and after twelve
            // regardless
            return (keysOut == 0 && tickCount - lastKeyring > 60) || tickCount - lastKeyring > 240;
        }
        return keyrings < 3 && getHealth() < getMaxHealth() * (0.80F - 0.25F * keyrings);
    }

    boolean hasLockedDown() {
        return lockedDown;
    }

    // ------------------------------------------------------------------ saved
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Dormant", isDormant());
        tag.putBoolean("LockedDown", lockedDown);
        tag.putInt("Keyrings", keyrings);
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
        lockedDown = tag.getBoolean("LockedDown");
        keyrings = tag.getInt("Keyrings");
        int[] h = tag.getIntArray("Home");
        if (h.length == 3) {
            home = new BlockPos(h[0], h[1], h[2]);
        }
    }

    // ------------------------------------------------------------------ looks and sounds
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
                case LASH: return state.setAndContinue(LASH_ANIM);
                case SHACKLE: return state.setAndContinue(SHACKLE_ANIM);
                case SLAM: return state.setAndContinue(SLAM_ANIM);
                case LANTERN: return state.setAndContinue(LANTERN_ANIM);
                case AWAKEN: return state.setAndContinue(AWAKEN_ANIM);
                case INTRO: return state.setAndContinue(INTRO_ANIM);
                case LOCKDOWN: return state.setAndContinue(LOCKDOWN_ANIM);
                case CAGE: return state.setAndContinue(LOCKDOWN_ANIM);
                case KEYRING: return state.setAndContinue(KEYRING_ANIM);
                case STAGGER: return state.setAndContinue(STAGGER_ANIM);
                default: break;
            }
            return state.setAndContinue(FrostServantEntity.going(state) ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && !isDormant() && hurtTime > 0 && !isAttacking()
                        ? state.setAndContinue(HURT) : PlayState.STOP));
    }

    /** His body lies until his death scene goes to black (BossCutscenes: the clip, a breath, the card). */
    @Override
    protected int getDeathDuration() {
        return DEATH_T + 36;
    }

    /** His death's sounds, on its clip's beats: the knees, each key ringing on the floor (the last throws every lock in
     *  the hall: "the locks... they open"), the flame going out, the lantern on the stones, the fall. */
    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (level().isClientSide) {
            return;
        }
        int t = deathTime - DEATH_LAG;
        if (t == DEATH_KNEEL) {
            playSound(SoundEvents.ANVIL_LAND, 0.5F, 0.5F);
            playSound(SoundEvents.CHAIN_FALL, 1.4F, 0.7F);
        }
        for (int k = 0; k < DEATH_KEYS.length; k++) {
            if (t == DEATH_KEYS[k] + DEATH_KEY_FALL) {
                playSound(FFSounds.TURNKEY_KEYS.get(), 0.7F, 1.35F + 0.12F * k);
                if (k == DEATH_KEYS.length - 1) {
                    playSound(FFSounds.TURNKEY_LOCK.get(), 1.4F, 0.8F);
                }
            }
        }
        if (t == DEATH_GUTTER) {
            playSound(SoundEvents.FIRE_EXTINGUISH, 0.9F, 0.7F);
        }
        if (t == DEATH_DROP + DEATH_LANTERN_FALL) {
            playSound(SoundEvents.LANTERN_FALL, 1.6F, 0.75F);
            playSound(SoundEvents.CHAIN_STEP, 1.2F, 0.6F);
        }
        if (t == DEATH_FALL) {
            playSound(SoundEvents.ANVIL_LAND, 0.7F, 0.42F);
            playSound(SoundEvents.IRON_GOLEM_DAMAGE, 1.2F, 0.5F);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isDormant() ? null : SoundEvents.IRON_GOLEM_STEP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (!isDormant()) {
            playSound(FFSounds.VAULT_WARDEN_STEP.get(), 0.6F, 0.8F);
            playSound(SoundEvents.CHAIN_STEP, 0.6F, 0.7F);       // the chain dragging after him
        }
    }

    // ------------------------------------------------------------------ the fight
    static class TurnkeyAttackGoal extends Goal {
        private final TurnkeyEntity mob;
        private int cooldown;
        private final int[] perAttack = new int[9];

        TurnkeyAttackGoal(TurnkeyEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !mob.isDormant() && mob.getTarget() != null && mob.getTarget().isAlive()
                    && mob.getAttackState() != AWAKEN && mob.getAttackState() != INTRO && mob.getAttackState() != STAGGER;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            if (mob.getAttackState() != AWAKEN && mob.getAttackState() != INTRO && mob.getAttackState() != STAGGER) {
                mob.setAttackState(0);
            }
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
            double dist = mob.distanceTo(target);
            int state = mob.getAttackState();
            if (state == 0) {
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                mob.getNavigation().moveTo(target, 1.0D);
                // (in the last ring his keys do not wait out the rest after another blow - they are all there is to fight)
                if (cooldown > 0 && !(mob.inLastRing() && mob.keyringDue())) {
                    cooldown--;
                    return;
                }
                boolean sighted = mob.hasLineOfSight(target);
                int chosen = 0;
                if (mob.keyringDue()) {
                    chosen = KEYRING;
                } else if (!mob.hasLockedDown() && mob.getHealth() < mob.getMaxHealth() * 0.45F) {
                    chosen = LOCKDOWN;
                } else if (mob.hasLockedDown() && dist < 10.0D && sighted && perAttack[CAGE] <= 0 && mob.cageReady()) {
                    chosen = CAGE;
                } else if (dist < 5.0D && perAttack[LASH] <= 0) {
                    chosen = mob.random.nextFloat() < 0.3F && perAttack[SLAM] <= 0 ? SLAM : LASH;
                } else if (dist >= 5.0D && dist < 14.0D && sighted && perAttack[SHACKLE] <= 0) {
                    chosen = SHACKLE;
                } else if (dist < 9.0D && sighted && perAttack[SLAM] <= 0) {
                    chosen = SLAM;
                }
                if (chosen != 0) {
                    perAttack[chosen] = switch (chosen) {
                        case CAGE -> 320;
                        case SHACKLE -> 140;
                        case SLAM -> 120;
                        case LASH -> 30;
                        default -> 0;
                    };
                    mob.setAttackState(chosen);
                }
                return;
            }
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(target, 14.0F, 14.0F);
            // EVERY BLOW IS HEARD COMING:
            // each attack has its wind-up in sound as well as in motion, then its strike
            int t = mob.attackTicks;
            switch (state) {
                case LASH -> {
                    if (t == 3) {
                        mob.playSound(SoundEvents.CHAIN_STEP, 1.4F, 1.1F);                 // the chain gathered up
                    }
                    if (t == 9) {
                        mob.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.3F, 0.6F);        // and swung
                    }
                    if (mob.attackTicks == 12) {
                        mob.lash();
                    }
                    if (mob.attackTicks > 26) {
                        finish(10);
                    }
                }
                case SHACKLE -> {
                    if (t == 2 || t == 5 || t == 8 || t == 11) {
                        mob.playSound(SoundEvents.CHAIN_STEP, 1.3F, 0.8F + t * 0.04F);    // whirled overhead, faster
                    }
                    if (mob.attackTicks == 13) {
                        mob.throwShackle(target);
                    }
                    if (mob.attackTicks > 34) {
                        finish(14);
                    }
                }
                case KEYRING -> {
                    if (t == 2 || t == 5 || t == 8) {
                        mob.playSound(SoundEvents.CHAIN_STEP, 1.1F, 1.6F + t * 0.05F);    // the keys jingling up
                    }
                    if (mob.attackTicks == 10 || mob.attackTicks == 14) {
                        mob.playSound(SoundEvents.CHAIN_HIT, 1.8F, 1.3F);
                    }
                    if (mob.attackTicks == 18) {
                        mob.keyring();
                    }
                    if (mob.attackTicks > 30) {
                        finish(16);
                    }
                }
                case CAGE -> {
                    if (t == 4) {
                        mob.playSound(SoundEvents.IRON_DOOR_OPEN, 1.6F, 0.5F);            // a cage door creaks
                    }
                    if (t == 14) {
                        mob.playSound(SoundEvents.CHAIN_STEP, 1.4F, 0.6F);
                    }
                    if (mob.attackTicks == 20) {
                        mob.cage(target);
                    }
                    if (mob.attackTicks > 40) {
                        finish(10);
                    }
                }
                case LANTERN -> {
                    if (t == 3) {
                        mob.playSound(SoundEvents.CHAIN_STEP, 1.6F, 0.6F);                 // the lantern's chain taken up
                    }
                    if (t == 6) {
                        mob.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.4F, 0.5F);          // the heave over his head
                    }
                    if (t == 10) {
                        mob.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.4F);        // and down
                    }
                    if (mob.attackTicks == 13) {
                        mob.lanternSmash(target);
                    }
                    if (mob.attackTicks > 28) {
                        finish(14);
                    }
                }
                case SLAM -> {
                    if (t == 4) {
                        mob.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.4F, 0.55F);       // the heave
                    }
                    if (t == 11) {
                        mob.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.45F);     // the arms coming down
                    }
                    if (mob.attackTicks == 15) {
                        mob.slam(target);
                    }
                    if (mob.attackTicks > 32) {
                        finish(16);
                    }
                }
                case LOCKDOWN -> {
                    if (t == 3) {
                        mob.playSound(SoundEvents.IRON_DOOR_OPEN, 2.0F, 0.4F);
                    }
                    if (mob.attackTicks == 14 || mob.attackTicks == 22) {
                        mob.playSound(SoundEvents.CHAIN_HIT, 2.0F, 0.8F);
                    }
                    if (mob.attackTicks == 30) {
                        mob.lockdown(target);
                    }
                    if (mob.attackTicks > 50) {
                        finish(20);
                    }
                }
                default -> finish(10);
            }
        }

        private void finish(int rest) {
            mob.setAttackState(0);
            cooldown = rest;
        }
    }
}
