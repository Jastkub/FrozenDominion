package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * SLUGA ROZDZKI - A SERVANT OF THE WAND OF THE DEAD (07.10.2026).
 * One of the citadel's Frost Skeletons, called up out of the floor by BoneWandItem - the same bones, the same clips
 * (geo/animations of frost_skeleton), its frost turned the wand's grave-green so it is told from the hostile ones at a
 * glance (textures/entity/bone_wand_skeleton, tools/gen_bone_wand.py).
 *
 * <p><b>Why not a subclass of FrostSkeletonEntity.</b> That one is a {@link FrostServantEntity}, and some fifty
 * places in the mod read "instanceof FrostServantEntity" as "the King's side": every court foe's setTarget refuses
 * it, its hurt() drops every blow from the court (and every blow it deals to the court is dropped by theirs), and
 * every trap, boss attack and area spares it. A servant built on it could neither hurt the citadel nor be hurt or
 * even looked at by it - no distraction at all. So it is its own PathfinderMob (not an Enemy: no golem hunts it, it
 * does not stop its master sleeping) that plays the skeleton's clips and keeps its tricks.
 *
 * <p><b>What it does.</b> It CLAWS UP out of the rune ring ({@link BoneWandRingEntity}, clip rise_dig at
 * {@link #RISE_SPEED}x - untouchable while it rises), serves {@link #LIFE} ticks, then CRUMBLES to dust. It goes for
 * what its master is fighting (see {@link #pick}); with nothing to fight it keeps at his heel, and left far behind it
 * sinks and claws up again beside him. It shambles, swipes with its iced hand (slowness, as the hostile ones), now
 * and then throws itself at a foe and lands on its face - and trips over its own feet. Beaten, it FALLS APART into a
 * pile and gets up once more at half its strength (fire, a blast or the void end it outright). Its master's
 * COMMAND ({@link #command}) sends it at one foe, faster and fiercer, for {@link #COMMAND_T} ticks - its first move a
 * leap at it. Its blows are its own (what it strikes turns on it - that is the distraction) but its kills are its
 * master's (XP, and loot that wants a player's kill). Never more than {@link #MAX_PER_OWNER} per master: a new one
 * sends the oldest to dust. It does not outlast a reload.
 *
 * <p>It never strikes, nor is struck by, its master, his other servants, his shades, his pets, a tamed Monstrosity;
 * other players only where PvP is on.
 */
public class BoneWandSkeletonEntity extends PathfinderMob implements GeoEntity, OwnableEntity {

    // ---- the beats (tools/gen_bone_wand.py mirrors the ones the pictures need - change one, change both)
    /** Its service: 40 s. */
    public static final int LIFE = 800;
    public static final int MAX_PER_OWNER = 4;
    /** The frost skeleton's own clips, played faster where a servant should not dawdle. */
    public static final float RISE_SPEED = 1.6F, GETUP_SPEED = 1.5F, REFORM_SPEED = 1.25F, CRUMBLE_SPEED = 1.5F;
    /** rise_dig 65 t, getup 26 t, reform 37 t, death 30 t (tools/gen_frost_skeleton.py), at the speeds above. */
    public static final int RISE_T = 41, GETUP_T = 18, REFORM_T = 30, CRUMBLE_T = 20, CRUMBLE_FX = 13;
    static final int SWIPE_SWISH = 5, SWIPE_HIT = 11, SWIPE_END = 24;
    static final int LUNGE_LEAP = 8, LUNGE_END = 18, TRIP_END = 16;
    /** How long the pile lies before it pulls itself together (the hostile one's: 90). */
    public static final int PILE_T = 60;
    /** The master's command: how long it lasts, and how much quicker it makes them. */
    public static final int COMMAND_T = 100;
    static final double FRENZY_SPEED = 0.5D;
    public static final float SWIPE_DMG = 3.0F, LUNGE_DMG = 4.0F;
    /** How far from its master it fights, follows, and is called back. */
    static final double LEASH = 24.0D, HEEL = 5.0D, CALL_BACK = 24.0D, LOST = 48.0D, SEEK = 10.0D;

    public static final int ST_RISE = 1, ST_SWIPE = 2, ST_LUNGE = 3, ST_TRIP = 4, ST_DOWN = 5, ST_GETUP = 6,
            ST_PILE = 7, ST_REFORM = 8, ST_CRUMBLE = 10;
    private static final UUID FRENZY_ID = UUID.fromString("6d0b2c1a-4e7f-4b8a-9c3d-2f1e0a5b7c91");

    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(BoneWandSkeletonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> VARIANT =
            SynchedEntityData.defineId(BoneWandSkeletonEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> FRENZY =
            SynchedEntityData.defineId(BoneWandSkeletonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> EXPIRING =
            SynchedEntityData.defineId(BoneWandSkeletonEntity.class, EntityDataSerializers.BOOLEAN);
    /** It went as a pile (burnt there, or its time ran out there): it goes to dust as it lies, not standing up. */
    private static final EntityDataAccessor<Boolean> SCATTERED =
            SynchedEntityData.defineId(BoneWandSkeletonEntity.class, EntityDataSerializers.BOOLEAN);

    private static final String P = "animation.frost_skeleton.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation RISE = RawAnimation.begin().thenPlay(P + "rise_dig");
    private static final RawAnimation SWIPE = RawAnimation.begin().thenPlay(P + "swipe");
    private static final RawAnimation LUNGE = RawAnimation.begin().thenPlay(P + "lunge").thenLoop(P + "down");
    private static final RawAnimation TRIP = RawAnimation.begin().thenPlay(P + "trip").thenLoop(P + "down");
    private static final RawAnimation DOWN = RawAnimation.begin().thenLoop(P + "down");
    private static final RawAnimation GETUP = RawAnimation.begin().thenPlay(P + "getup");
    private static final RawAnimation COLLAPSE = RawAnimation.begin().thenPlay(P + "collapse").thenLoop(P + "pile");
    private static final RawAnimation PILE = RawAnimation.begin().thenLoop(P + "pile");
    private static final RawAnimation REFORM = RawAnimation.begin().thenPlay(P + "reform");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay(P + "death");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay(P + "hurt");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private int life = LIFE;
    private int stateTicks;
    private int reformsLeft = 1;
    private int cooldown;
    private int tripCooldown = 200;
    private int downFor;
    private int frenzy;
    private boolean leapOnCommand;
    private int callBackCooldown;
    private int orphaned;
    private Vec3 lungeDir = Vec3.ZERO;
    private boolean lungeHit;
    @Nullable
    private LivingEntity commanded;

    public BoneWandSkeletonEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.ATTACK_DAMAGE, SWIPE_DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    // ================================================================================================ calling it up
    /**
     * One servant for `owner`, clawing up out of the floor at (x, y, z), facing `yaw`. Makes room first: if he
     * already has {@link #MAX_PER_OWNER}, the oldest crumbles.
     */
    @Nullable
    public static BoneWandSkeletonEntity rise(ServerLevel level, LivingEntity owner, double x, double y, double z,
                                              float yaw) {
        List<BoneWandSkeletonEntity> mine = servants(level, owner);
        if (mine.size() >= MAX_PER_OWNER) {
            mine.sort(Comparator.comparingInt(s -> s.life));
            for (int i = 0; i <= mine.size() - MAX_PER_OWNER; i++) {
                mine.get(i).crumble();
            }
        }
        BoneWandSkeletonEntity s = FFEntities.BONE_WAND_SKELETON.get().create(level);
        if (s == null) {
            return null;
        }
        s.ownerId = owner.getUUID();
        s.entityData.set(VARIANT, (byte) level.random.nextInt(FrostSkeletonEntity.VARIANT_BONES.length));
        s.moveTo(x, y, z, yaw, 0.0F);
        s.setYBodyRot(yaw);
        s.setYHeadRot(yaw);
        s.yBodyRotO = yaw;
        s.yHeadRotO = yaw;
        s.setState(ST_RISE);
        level.addFreshEntity(s);
        return s;
    }

    /** His servants still standing (not crumbling), anywhere near him. */
    public static List<BoneWandSkeletonEntity> servants(Level level, Entity owner) {
        return level.getEntitiesOfClass(BoneWandSkeletonEntity.class, owner.getBoundingBox().inflate(128.0D),
                s -> s.ownedBy(owner) && s.isAlive() && !s.crumbling());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STATE, 0);
        entityData.define(VARIANT, (byte) 0);
        entityData.define(FRENZY, false);
        entityData.define(EXPIRING, false);
        entityData.define(SCATTERED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ServeGoal(this));
    }

    // ================================================================================================ its state
    public int state() {
        return entityData.get(STATE);
    }

    void setState(int st) {
        entityData.set(STATE, st);
        stateTicks = 0;
    }

    public int variant() {
        return entityData.get(VARIANT);
    }

    public boolean frenzied() {
        return entityData.get(FRENZY);
    }

    /** Its last three seconds: the frost in its sockets gutters (the renderer). */
    public boolean expiring() {
        return entityData.get(EXPIRING);
    }

    public boolean crumbling() {
        return state() == ST_CRUMBLE;
    }

    public boolean isPile() {
        return state() == ST_PILE;
    }

    public boolean ownedBy(@Nullable Entity e) {
        return e != null && ownerId != null && ownerId.equals(e.getUUID());
    }

    @Override
    @Nullable
    public UUID getOwnerUUID() {
        return ownerId;
    }

    @Nullable
    public LivingEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof LivingEntity le
                && le.isAlive()) {
            return le;
        }
        return null;
    }

    /** Down, getting up, swinging or falling apart, it goes nowhere and turns to nothing. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || state() != 0;
    }

    @Override
    public boolean isPushable() {
        return state() != ST_PILE && state() != ST_CRUMBLE && super.isPushable();
    }

    // ================================================================================================ every tick
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        stateTicks++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (tripCooldown > 0) {
            tripCooldown--;
        }
        if (callBackCooldown > 0) {
            callBackCooldown--;
        }
        if (state() == ST_CRUMBLE) {
            tickCrumble();
            return;
        }
        LivingEntity owner = owner();
        orphaned = owner == null ? orphaned + 1 : 0;
        if (--life <= 0 || orphaned > 20) {
            crumble();                                    // its time is up, or there is nobody left to serve
            return;
        }
        if (life == 60) {
            entityData.set(EXPIRING, true);
        }
        if (frenzy > 0 && --frenzy == 0) {
            endFrenzy();
        }
        if (tickCount % 10 == 0 && owner != null) {
            retarget(owner);
        }
        switch (state()) {
            case ST_RISE -> {
                if (stateTicks == 2) {
                    playSound(FFSounds.FROST_SKELETON_RISE.get(), 0.9F, 1.05F + random.nextFloat() * 0.2F);
                }
                if (stateTicks >= RISE_T) {
                    setState(0);
                    cooldown = 6;
                }
            }
            case ST_SWIPE -> swipe(owner);
            case ST_LUNGE -> lunge(owner);
            case ST_TRIP -> {
                if (stateTicks == 11) {
                    thud(0.5F);
                }
                if (stateTicks >= TRIP_END) {
                    down(12 + random.nextInt(8));
                }
            }
            case ST_DOWN -> {
                if (stateTicks >= downFor) {
                    setState(ST_GETUP);
                }
            }
            case ST_GETUP -> {
                if (stateTicks >= GETUP_T) {
                    setState(0);
                    cooldown = Math.max(cooldown, 4);
                }
            }
            case ST_PILE -> {
                if (stateTicks > PILE_T - 24 && stateTicks % 8 == 0) {
                    playSound(FFSounds.FROST_SKELETON_IDLE.get(), 0.45F, 1.4F + random.nextFloat() * 0.3F);
                }
                if (stateTicks >= PILE_T) {
                    setState(ST_REFORM);
                    playSound(FFSounds.FROST_SKELETON_REFORM.get(), 1.0F, 1.1F);
                }
            }
            case ST_REFORM -> {
                if (stateTicks >= REFORM_T) {
                    reformsLeft--;
                    setHealth(getMaxHealth() * 0.5F);
                    setState(0);
                    cooldown = 8;
                }
            }
            default -> {
            }
        }
    }

    // ================================================================================================ whom it fights
    /**
     * What it goes for, in order: what its master COMMANDED (while the command holds); what he struck in the last
     * five seconds; what struck him; what struck it; what it is already fighting; else the nearest thing hostile to
     * him, or after him or his, within {@link #SEEK} blocks it can see.
     */
    private void retarget(LivingEntity owner) {
        LivingEntity now = getTarget();
        LivingEntity want = pick(owner, now);
        if (want != now) {
            setTarget(want);
        }
    }

    @Nullable
    private LivingEntity pick(LivingEntity owner, @Nullable LivingEntity now) {
        if (commanded != null) {
            if (fit(owner, commanded, LEASH + 8.0D) && frenzy > 0) {
                return commanded;
            }
            commanded = null;
        }
        LivingEntity struck = owner.getLastHurtMob();
        if (fit(owner, struck, LEASH) && owner.tickCount - owner.getLastHurtMobTimestamp() < 100) {
            return struck;
        }
        LivingEntity striker = owner.getLastHurtByMob();
        if (fit(owner, striker, LEASH)) {
            return striker;
        }
        LivingEntity mine = getLastHurtByMob();
        if (fit(owner, mine, LEASH)) {
            return mine;
        }
        if (fit(owner, now, LEASH)) {
            return now;
        }
        List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SEEK),
                e -> fit(owner, e, LEASH * 0.66D) && worthAttacking(owner, e) && hasLineOfSight(e));
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(this)));
        return near.isEmpty() ? null : near.get(0);
    }

    private boolean fit(LivingEntity owner, @Nullable LivingEntity e, double leash) {
        return e != null && e.isAlive() && isFoe(owner, e) && e.distanceToSqr(owner) < leash * leash;
    }

    /** Hostile to its master of its own accord (not one that minds its own business), or already after him or his. */
    private boolean worthAttacking(LivingEntity owner, LivingEntity v) {
        if (v instanceof Mob m && m.getTarget() != null
                && (m.getTarget() == owner || m.getTarget() == this || allyOf(owner, m.getTarget()))) {
            return true;
        }
        return v instanceof Enemy && !(v instanceof NeutralMob) && !(v instanceof Creeper);
    }

    /**
     * Is `e` anything its master's servants may strike? Never him, his servants, his shades, his pets or a tamed
     * Monstrosity, his team, a player in creative or spectating - nor another player, or another player's servant,
     * unless PvP is on.
     */
    public static boolean isFoe(@Nullable LivingEntity owner, Entity e) {
        if (!(e instanceof LivingEntity v) || !v.isAlive() || v == owner || v instanceof ArmorStand || owner == null) {
            return false;
        }
        if (v instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return false;
        }
        if (allyOf(owner, v) || v.isAlliedTo(owner) || owner.isAlliedTo(v)) {
            return false;
        }
        Player other = v instanceof Player p ? p
                : v instanceof BoneWandSkeletonEntity s && s.owner() instanceof Player op ? op : null;
        if (other != null) {
            return owner instanceof Player me && me.canHarmPlayer(other) && v.level().getServer() != null
                    && v.level().getServer().isPvpAllowed();
        }
        return true;
    }

    /** Of its master's side: his servants, his shades, his pets, a tamed Monstrosity. */
    static boolean allyOf(LivingEntity owner, @Nullable Entity e) {
        if (e == null) {
            return false;
        }
        if (e == owner) {
            return true;
        }
        if (e instanceof BoneWandSkeletonEntity s) {
            return s.ownedBy(owner);
        }
        if (e instanceof HollowStaffShadeEntity s) {
            return s.ownedBy(owner);
        }
        if (e instanceof HollowGolemEntity g) {
            return g.isTamed();
        }
        return e instanceof OwnableEntity o && owner.getUUID().equals(o.getOwnerUUID());
    }

    @Override
    public boolean isAlliedTo(Entity e) {
        LivingEntity owner = ownerId == null ? null : (level().isClientSide ? null : owner());
        if (ownedBy(e) || (owner != null && allyOf(owner, e))) {
            return true;
        }
        if (e instanceof BoneWandSkeletonEntity s && ownerId != null && ownerId.equals(s.ownerId)) {
            return true;
        }
        return super.isAlliedTo(e);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && !level().isClientSide && !isFoe(owner(), target)) {
            return;                                        // never at its own side, whatever told it to
        }
        super.setTarget(target);
    }

    // ================================================================================================ its master's command
    /** Sent at `target`: it drops what it was doing and goes for it, quicker, for `ticks` - leaping first if it can. */
    public void command(LivingEntity target, int ticks) {
        boolean already = frenzied();
        commanded = target;
        frenzy = ticks;
        leapOnCommand = true;
        entityData.set(FRENZY, true);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(FRENZY_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(FRENZY_ID, "Bone wand command", FRENZY_SPEED,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }
        if (state() == 0 || state() == ST_SWIPE) {
            cooldown = Math.min(cooldown, 2);
        }
        setTarget(target);
        playSound(FFSounds.FROST_SKELETON_IDLE.get(), 0.9F, 1.35F + random.nextFloat() * 0.25F);
        if (!already) {                                   // (its crest lives as long as the command does)
            BoneWandFxEntity.spawn(level(), BoneWandFxEntity.SPUR, position(), getYRot(), 1.0F, BoneWandFxEntity.UNTIL_GONE)
                    .follow(this, BoneWandFxEntity.SPUR_Y);
        }
    }

    private void endFrenzy() {
        entityData.set(FRENZY, false);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(FRENZY_ID);
        }
        leapOnCommand = false;
    }

    // ================================================================================================ its blows
    /** The iced hand brought down over its head - on its foe, and on any foe of its master's in the swing. */
    private void swipe(@Nullable LivingEntity owner) {
        if (stateTicks == SWIPE_SWISH) {
            playSound(FFSounds.FROST_SKELETON_SWING.get(), 0.8F, 1.0F + random.nextFloat() * 0.3F);
        }
        if (stateTicks == SWIPE_HIT && owner != null) {
            Vec3 fwd = Vec3.directionFromRotation(0.0F, getYRot());
            LivingEntity target = getTarget();
            for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.6D, 0.6D, 2.6D),
                    e -> e != this && (e == target || (isFoe(owner, e) && worthAttacking(owner, e))))) {
                Vec3 to = v.position().subtract(position());
                Vec3 flat = new Vec3(to.x, 0.0D, to.z);
                double reach = 2.4D + v.getBbWidth() * 0.5D;
                if (flat.length() > reach || (flat.lengthSqr() > 1.0E-4D && flat.normalize().dot(fwd) < 0.2D)) {
                    continue;
                }
                if (strike(v, owner, (float) getAttributeValue(Attributes.ATTACK_DAMAGE), 50)) {
                    v.knockback(0.25D, -fwd.x, -fwd.z);
                }
            }
        }
        if (stateTicks >= SWIPE_END) {
            setState(0);
            int rest = 18 + random.nextInt(14);
            cooldown = frenzy > 0 ? rest / 2 : rest;
        }
    }

    /** A leap at its foe that ends on its face whether it found it or not. */
    private void lunge(@Nullable LivingEntity owner) {
        if (stateTicks == 1) {
            playSound(FFSounds.FROST_SKELETON_IDLE.get(), 1.0F, 0.85F);
        }
        if (stateTicks == LUNGE_LEAP) {
            double push = frenzy > 0 ? 0.74D : 0.62D;
            setDeltaMovement(lungeDir.x * push, 0.33D, lungeDir.z * push);
            hurtMarked = true;
            playSound(FFSounds.FROST_SKELETON_SWING.get(), 1.0F, 0.75F);
        }
        if (stateTicks > LUNGE_LEAP && stateTicks <= LUNGE_END - 2 && !lungeHit && owner != null) {
            LivingEntity target = getTarget();
            for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.5D),
                    e -> e != this && (e == target || (isFoe(owner, e) && worthAttacking(owner, e))))) {
                if (strike(v, owner, LUNGE_DMG, 70)) {
                    lungeHit = true;
                    break;
                }
            }
        }
        if (stateTicks == LUNGE_END - 4) {
            thud(0.8F);
        }
        if (stateTicks >= LUNGE_END) {
            down(frenzy > 0 ? 8 + random.nextInt(6) : 12 + random.nextInt(8));
        }
    }

    /**
     * One blow of its own: its iced hand slows what it lands on (as the hostile ones' does). The blow is the
     * servant's - what it struck turns on it - but the kill is its master's.
     */
    private boolean strike(LivingEntity v, LivingEntity owner, float dmg, int slowTicks) {
        if (!v.hurt(damageSources().mobAttack(this), dmg)) {
            return false;
        }
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, 0), this);
        if (owner instanceof Player p) {
            v.setLastHurtByPlayer(p);
        }
        playSound(FFSounds.FROST_SKELETON_HURT.get(), 0.5F, 1.5F);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(FFParticles.ICE_SHARD.get(), v.getX(), v.getY() + v.getBbHeight() * 0.6D, v.getZ(), 6,
                    0.25D, 0.25D, 0.25D, 0.05D);
        }
        return true;
    }

    private void down(int ticks) {
        downFor = ticks;
        setState(ST_DOWN);
    }

    /** It catches its own foot and goes down (not while commanded: the command holds it together). */
    void trip() {
        if (state() == 0 && isAlive() && frenzy == 0) {
            setState(ST_TRIP);
            tripCooldown = 300 + random.nextInt(300);
            playSound(FFSounds.FROST_SKELETON_IDLE.get(), 0.7F, 1.6F);
        }
    }

    private void thud(float volume) {
        playSound(FFSounds.FROST_SKELETON_HURT.get(), volume, 0.75F);
        if (level() instanceof ServerLevel sl) {
            BlockState under = level().getBlockState(blockPosition().below());
            if (!under.isAir()) {
                Vec3 f = Vec3.directionFromRotation(0.0F, getYRot());
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), getX() + f.x, getY() + 0.1D,
                        getZ() + f.z, 8, 0.5D, 0.05D, 0.5D, 0.1D);
            }
        }
    }

    // ================================================================================================ called back
    /** Left far behind: it sinks where it stands and claws up again out of the floor a few steps behind him. */
    void callBack(LivingEntity owner) {
        callBackCooldown = 60;
        Vec3 back = owner.getLookAngle().multiply(-1.0D, 0.0D, -1.0D);
        back = back.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : back.normalize();
        Vec3 side = new Vec3(-back.z, 0.0D, back.x).scale((random.nextFloat() - 0.5F) * 3.0D);
        Vec3 at = owner.position().add(back.scale(2.5D)).add(side);
        if (!level().noCollision(this, getType().getAABB(at.x, at.y, at.z))
                || level().getBlockState(BlockPos.containing(at.x, at.y - 0.5D, at.z)).isAir()) {
            at = owner.position();
        }
        BoneWandFxEntity.spawn(level(), BoneWandFxEntity.CRUMBLE, position(), getYRot(), 0.7F, BoneWandFxEntity.CRUMBLE_LIFE);
        float yaw = owner.getYRot();
        moveTo(at.x, at.y, at.z, yaw, 0.0F);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        getNavigation().stop();
        setTarget(null);
        setState(ST_RISE);
        level().addFreshEntity(new BoneWandRingEntity(level(), owner, at, yaw, 0, BoneWandRingEntity.SMALL));
    }

    // ================================================================================================ falling apart
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return super.hurt(source, amount);
        }
        LivingEntity owner = owner();
        Entity by = source.getEntity();
        if (by != null && (ownedBy(by) || (owner != null && allyOf(owner, by)))) {
            return false;                                    // not from him, nor his
        }
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        int st = state();
        if ((st == ST_RISE || st == ST_REFORM || st == ST_CRUMBLE) && !bypass) {
            if (by != null && st != ST_CRUMBLE) {
                playSound(FFSounds.FROST_SKELETON_STEP.get(), 0.7F, 0.6F);
            }
            return false;                                    // getting up, pulling together, going: untouchable
        }
        if (st == ST_PILE && isAlive()) {
            if (!bypass && !source.is(DamageTypeTags.IS_FIRE)) {
                if (by != null) {
                    playSound(FFSounds.FROST_SKELETON_STEP.get(), 0.7F, 0.7F);
                }
                return false;                                // the pile gets up again whatever is done to it
            }
            reformsLeft = 0;
            entityData.set(SCATTERED, true);
            return super.hurt(source, Math.max(amount, 100.0F));
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            amount *= 2.0F;                                  // brittle with frost: fire takes it apart
        }
        boolean hit = super.hurt(source, amount);
        if (hit && isAlive() && st == 0 && tripCooldown <= 0 && by != null && random.nextFloat() < 0.1F) {
            trip();                                          // knocked off balance
        }
        return hit;
    }

    /** Beaten, it falls apart into a pile and gets up once more; fire, a blast, the void - or a second beating - end it. */
    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && reformsLeft > 0 && state() != ST_PILE && state() != ST_CRUMBLE
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.IS_FIRE)
                && !source.is(DamageTypeTags.IS_EXPLOSION)) {
            setHealth(1.0F);
            setState(ST_PILE);
            setTarget(null);
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            playSound(FFSounds.FROST_SKELETON_COLLAPSE.get(), 1.0F, 1.1F);
            bones(12);
            return;
        }
        super.die(source);
    }

    /** Its time up, its place wanted, its master gone: it falls apart for good and goes to dust. */
    public void crumble() {
        if (level().isClientSide || state() == ST_CRUMBLE || !isAlive()) {
            return;
        }
        if (frenzy > 0) {
            frenzy = 0;
            endFrenzy();
        }
        if (state() == ST_PILE) {
            entityData.set(SCATTERED, true);
        }
        setState(ST_CRUMBLE);
        setTarget(null);
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        playSound(FFSounds.FROST_SKELETON_COLLAPSE.get(), 0.8F, 1.25F);
    }

    private void tickCrumble() {
        getNavigation().stop();
        if (stateTicks == CRUMBLE_FX) {
            dust();
        }
        if (stateTicks >= CRUMBLE_T) {
            discard();
        }
    }

    private void dust() {
        BoneWandFxEntity.spawn(level(), BoneWandFxEntity.CRUMBLE, position(), getYRot(), 1.0F, BoneWandFxEntity.CRUMBLE_LIFE);
        playSound(FFSounds.BONE_WAND_CRUMBLE.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        bones(10);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 0.5D, getZ(), 4, 0.3D, 0.3D, 0.3D, 0.02D);
        }
    }

    /** Beaten for good: the same fall, the same dust. */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (!level().isClientSide && deathTime == CRUMBLE_FX) {
            dust();
        }
        if (deathTime >= CRUMBLE_T && !level().isClientSide && !isRemoved()) {
            remove(RemovalReason.KILLED);
        }
    }

    private void bones(int n) {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE)), getX(), getY() + 0.3D,
                    getZ(), n, 0.4D, 0.2D, 0.4D, 0.12D);
            sl.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 0.4D, getZ(), n / 2, 0.4D, 0.3D, 0.4D, 0.08D);
        }
    }

    // ================================================================================================ its body
    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    /** Its master's hand on it does nothing: his wand's use goes on through it. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return state() == 0 ? FFSounds.FROST_SKELETON_IDLE.get() : null;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.08F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.FROST_SKELETON_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.FROST_SKELETON_COLLAPSE.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        playSound(FFSounds.FROST_SKELETON_STEP.get(), 0.35F, 0.95F + random.nextFloat() * 0.3F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        discard();                                          // forty seconds do not outlast a reload
    }

    // ================================================================================================ its clips
    /** How fast its clips run: the frost skeleton's, quickened where a servant should not dawdle. */
    double animSpeed() {
        if (isDeadOrDying()) {
            return CRUMBLE_SPEED;
        }
        return switch (state()) {
            case ST_RISE -> RISE_SPEED;
            case ST_GETUP -> GETUP_SPEED;
            case ST_REFORM -> REFORM_SPEED;
            case ST_CRUMBLE -> CRUMBLE_SPEED;
            case 0 -> frenzied() ? 1.4D : 1.0D;
            default -> 1.0D;
        };
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            int st = state();
            // the rise starts buried and the reform on the pile's frame: no blend into them
            state.getController().setTransitionLength(st == ST_RISE || st == ST_REFORM ? 0 : 3);
            if (isDeadOrDying() || st == ST_CRUMBLE) {
                return state.setAndContinue(entityData.get(SCATTERED) ? PILE : DEATH);
            }
            switch (st) {
                case ST_RISE: return state.setAndContinue(RISE);
                case ST_SWIPE: return state.setAndContinue(SWIPE);
                case ST_LUNGE: return state.setAndContinue(LUNGE);
                case ST_TRIP: return state.setAndContinue(TRIP);
                case ST_DOWN: return state.setAndContinue(DOWN);
                case ST_GETUP: return state.setAndContinue(GETUP);
                case ST_PILE: return state.setAndContinue(COLLAPSE);
                case ST_REFORM: return state.setAndContinue(REFORM);
                default: break;
            }
            return state.setAndContinue(FrostServantEntity.going(state) ? WALK : IDLE);
        }).setAnimationSpeedHandler(BoneWandSkeletonEntity::animSpeed));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && hurtTime > 0 && state() == 0 ? state.setAndContinue(HURT) : PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ================================================================================================ the brain
    /** Fight what it has to fight - shamble up, swipe, now and then leap - else keep at its master's heel. */
    static class ServeGoal extends Goal {
        private final BoneWandSkeletonEntity mob;
        private int repath;

        ServeGoal(BoneWandSkeletonEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return mob.state() == 0 && mob.isAlive();
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
        public void stop() {
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (mob.state() != 0) {
                return;
            }
            LivingEntity owner = mob.owner();
            if (owner == null) {
                mob.getNavigation().stop();
                return;
            }
            LivingEntity t = mob.getTarget();
            if (t != null && t.isAlive()) {
                fight(t, owner);
            } else {
                heel(owner);
            }
        }

        private void fight(LivingEntity t, LivingEntity owner) {
            if (mob.distanceToSqr(owner) > LOST * LOST && mob.callBackCooldown <= 0 && owner.onGround()) {
                mob.callBack(owner);
                return;
            }
            mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
            Vec3 to = t.position().subtract(mob.position());
            double flat = Math.sqrt(to.x * to.x + to.z * to.z) - t.getBbWidth() * 0.5D;
            if (--repath <= 0) {
                repath = 6 + mob.random.nextInt(5);
                mob.getNavigation().moveTo(t, 1.0D);
            }
            boolean walking = mob.getDeltaMovement().horizontalDistanceSqr() > 0.0009D;
            if (walking && mob.tripCooldown <= 0 && mob.random.nextInt(600) == 0) {
                mob.trip();
                return;
            }
            if (mob.cooldown > 0) {
                return;
            }
            boolean reachY = Math.abs(t.getY() - mob.getY()) < 2.0D;
            if (flat < 1.9D && reachY) {
                face(t);
                mob.leapOnCommand = false;
                mob.setState(ST_SWIPE);
            } else if (mob.onGround() && reachY && mob.hasLineOfSight(t)
                    && ((mob.leapOnCommand && flat > 2.2D && flat < 6.5D)
                    || (flat > 2.8D && flat < 5.5D && mob.random.nextInt(40) == 0))) {
                face(t);
                mob.leapOnCommand = false;
                mob.lungeDir = new Vec3(to.x, 0.0D, to.z).normalize();
                mob.lungeHit = false;
                mob.setState(ST_LUNGE);
            }
        }

        private void heel(LivingEntity owner) {
            double d = mob.distanceTo(owner);
            if (d > CALL_BACK && mob.callBackCooldown <= 0 && owner.onGround()) {
                mob.callBack(owner);
                return;
            }
            if (d > HEEL) {
                if (--repath <= 0 || mob.getNavigation().isDone()) {
                    repath = 10;
                    mob.getNavigation().moveTo(owner, d > 10.0D ? 1.25D : 1.05D);
                }
            } else if (d < 3.0D) {
                mob.getNavigation().stop();
                if (mob.random.nextInt(40) == 0) {
                    mob.getLookControl().setLookAt(owner, 10.0F, 10.0F);
                }
            }
        }

        private void face(LivingEntity t) {
            float yaw = (float) (Mth.atan2(t.getZ() - mob.getZ(), t.getX() - mob.getX()) * (180.0D / Math.PI)) - 90.0F;
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
            mob.getNavigation().stop();
        }
    }
}
