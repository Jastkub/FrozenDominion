package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * A SHADE (Cien) - one of the Shade Shepherd's herd, and the ordinary dweller of the Lightless Chambers: a gaunt
 * shadow-ram on long thin legs, its fleece a mass of hanging tatters, a narrow skull for a face (tools/
 * gen_shade_shepherd.py, "shade").
 *
 * <p><b>It is seen only in firelight.</b> The client draws it only while a lit campfire is within
 * {@link ShadeLight#SIGHT} blocks of it, fading in and out over a few ticks (ShadeRenderer, {@link #sight}). Out of the
 * light nothing of it is drawn at all - but it is there: it is heard (its own footsteps, its breath), it can be struck
 * if you find it, and it strikes you.
 *
 * <p><b>It lives in the dark.</b> It keeps out of a fire's light, lurks in the dark round whoever it hunts and springs
 * at them OUT of the dark: a hiss first (its tell, {@link #TELL_T} ticks before it leaves the floor), then the lunge.
 * Caught in the light it is exposed - it takes half again of every blow - and it makes for the dark.
 *
 * <p><b>Its shepherd</b> (ShadeShepherdEntity), when it has one, calls it to him ({@link #gather}) and sends it at you
 * ({@link #send}); while he lives the herd is his shield. Without him it is only a dweller of the dark.
 */
public class ShadeEntity extends FrostServantEntity {

    public static final int RISE = 1, TELL = 2, LUNGE = 3;
    // ---- the beats of its clips (tools/gen_shade_shepherd.py SHADE_* - change one, change both)
    static final int TELL_T = 10, LUNGE_T = 14, RISE_T = 20, DEATH_T = 24;
    /** The ticks of the lunge in which a body in its way is struck. */
    static final int BITE_FROM = 1, BITE_TO = 10;

    static final float LUNGE_DMG = 5.0F, SENT_DMG = 6.0F;
    /** Caught in a fire's light: what a blow does to it. */
    static final float LIT_TAKES = 1.5F;
    /** It springs from this far, and no nearer than this (it does not spring at someone it is standing on). */
    static final double SPRING_MAX = 6.5D, SPRING_MIN = 1.6D;
    static final double LEASH = 22.0D;

    /** What it is about (server only). */
    static final int WANDER = 0, STALK = 1, HERD = 2, SENT = 3;

    private static final String P = "animation.shade.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation TELL_ANIM = RawAnimation.begin().thenPlayAndHold(P + "tell");
    private static final RawAnimation LUNGE_ANIM = RawAnimation.begin().thenPlayAndHold(P + "lunge");
    private static final RawAnimation RISE_ANIM = RawAnimation.begin().thenPlayAndHold(P + "rise");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay(P + "hurt");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold(P + "death");

    @Nullable
    private UUID ownerId;
    @Nullable
    private BlockPos home;
    /**
     * ITS ROOM: the citadel puts it down with its
     * room's box (RoomBox, relative to where it stands; Room once it knows where that is) - it never goes out of it, nor
     * springs at anyone outside it. A shade without one keeps only its leash.
     */
    @Nullable
    private int[] roomRel;
    @Nullable
    private int[] room;
    int mode = WANDER;
    private int modeTicks;
    /** Ticks before it may spring again. */
    private int cooldown = 20;
    /** Ticks it spends making for the dark after a lunge. */
    private int retreat;
    @Nullable
    private Vec3 spot;
    private int spotTicks;
    @Nullable
    private LivingEntity victim;
    private boolean bitten;
    private boolean inLight;

    /** Client: how much of it the light shows (0 none - 1 all), and last tick's. */
    private float sight, sightO;

    public ShadeEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 4;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new ShadeGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                p -> !p.isSpectator() && !(p instanceof Player pl && pl.isCreative())));
    }

    // ------------------------------------------------------------------------------------------------ seen, or not
    /** Client: how much of it is drawn (the renderer's alpha before its own dimming), between ticks. */
    public float sight(float partialTick) {
        return Mth.lerp(partialTick, sightO, sight);
    }

    /** Is a lit campfire's light on it? (Both sides, refreshed every few ticks.) */
    public boolean inLight() {
        return inLight;
    }

    @Override
    public void tick() {
        super.tick();
        if ((tickCount + getId()) % 3 == 0) {
            inLight = ShadeLight.litNear(level(), position().add(0.0D, 0.5D, 0.0D), ShadeLight.SIGHT);
        }
        if (level().isClientSide) {
            sightO = sight;
            float want = inLight ? 1.0F : 0.0F;
            sight += Mth.clamp(want - sight, -0.18F, 0.18F);             // in or out over five or six ticks
        }
    }

    // ------------------------------------------------------------------------------------------------ the herd
    public boolean ownedBy(LivingEntity e) {
        return ownerId != null && ownerId.equals(e.getUUID());
    }

    public boolean hasOwner() {
        return ownerId != null;
    }

    @Nullable
    /** How dark its shepherd's chamber is (0 if it has no shepherd). */
    float shepherdDark() {
        ShadeShepherdEntity o = owner();
        return o != null ? o.darkness() : 0.0F;
    }

    ShadeShepherdEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof ShadeShepherdEntity sh
                && sh.isAlive()) {
            return sh;
        }
        return null;
    }

    /** Taken into his herd. */
    public void adopt(ShadeShepherdEntity shepherd) {
        ownerId = shepherd.getUUID();
    }

    /** Called to him: it comes and circles him, guarding him, for `ticks`. */
    public void gather(ShadeShepherdEntity shepherd, int ticks) {
        ownerId = shepherd.getUUID();
        if (getAttackState() != LUNGE) {
            mode = HERD;
            modeTicks = ticks;
            spot = null;
        }
    }

    /** Sent at `target`: straight at them, and it springs as soon as it can. */
    public void send(LivingEntity target) {
        setTarget(target);
        mode = SENT;
        modeTicks = 140;
        cooldown = Math.min(cooldown, 6 + random.nextInt(14));         // not all of them in the same tick
        spot = null;
    }

    /** Up out of the floor's dark where his bell called it. */
    public static ShadeEntity rise(ServerLevel level, ShadeShepherdEntity shepherd, BlockPos at) {
        ShadeEntity s = new ShadeEntity(com.jastkub.frozenfortress.registry.FFEntities.SHADE.get(), level);
        s.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        s.adopt(shepherd);
        s.home = at;
        s.setAttackState(RISE);
        level.addFreshEntity(s);
        s.playSound(FFSounds.SHADE_RISE.get(), 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
        return s;
    }

    /** Its shepherd is gone: it unravels. */
    public void dissolve() {
        if (isAlive()) {
            hurt(damageSources().genericKill(), 1000.0F);
        }
    }

    // ------------------------------------------------------------------------------------------------ its time
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (home == null) {
            home = blockPosition();
        }
        if (room == null && roomRel != null && roomRel.length == 6) {
            room = new int[]{home.getX() + roomRel[0], home.getY() + roomRel[1], home.getZ() + roomRel[2],
                    home.getX() + roomRel[3], home.getY() + roomRel[4], home.getZ() + roomRel[5]};
            roomRel = null;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        int st = getAttackState();
        int t = attackTicks;
        switch (st) {
            case RISE -> {
                getNavigation().stop();
                setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
                if (t >= RISE_T) {
                    setAttackState(0);
                }
            }
            case TELL -> {
                getNavigation().stop();
                LivingEntity v = victim;
                if (v == null || !v.isAlive()) {
                    setAttackState(0);
                    break;
                }
                getLookControl().setLookAt(v, 40.0F, 40.0F);
                face(v.position());
                if (t >= TELL_T) {
                    spring(v);
                }
            }
            case LUNGE -> {
                if (!bitten && t >= BITE_FROM && t <= BITE_TO) {
                    bite();
                }
                if (t >= LUNGE_T) {
                    setAttackState(0);
                    cooldown = Math.round((50 + random.nextInt(40)) * (1.0F - 0.4F * shepherdDark()));
                    retreat = 30 + random.nextInt(25);
                    if (mode == SENT || mode == HERD) {
                        mode = STALK;
                    }
                    spot = null;
                }
            }
            default -> {
            }
        }
    }

    /** THE TELL: a hiss, and it gathers itself - the spring comes TELL_T ticks later. */
    /** Is `p` in its room (anywhere, if it has none)? */
    boolean inRoom(Vec3 p) {
        return room == null || (p.x >= room[0] && p.x < room[3] && p.y >= room[1] - 1.0D && p.y < room[4]
                && p.z >= room[2] && p.z < room[5]);
    }

    /** `p` brought inside its room, a block and a half clear of its walls. */
    Vec3 keepIn(Vec3 p) {
        if (room == null) {
            return p;
        }
        return new Vec3(Mth.clamp(p.x, room[0] + 1.5D, room[3] - 1.5D), p.y, Mth.clamp(p.z, room[2] + 1.5D, room[5] - 1.5D));
    }

    /** Does its room hold `at` (a shade with no room: yes)? - so a shepherd takes in only the shades of his own. */
    public boolean roomHolds(BlockPos at) {
        if (room == null && roomRel != null && roomRel.length == 6) {
            BlockPos h = home != null ? home : blockPosition();
            return at.getX() >= h.getX() + roomRel[0] && at.getX() < h.getX() + roomRel[3]
                    && at.getZ() >= h.getZ() + roomRel[2] && at.getZ() < h.getZ() + roomRel[5];
        }
        return inRoom(Vec3.atBottomCenterOf(at));
    }

    void startTell(LivingEntity v) {
        if (!inRoom(v.position())) {
            return;                                   // (nobody outside its room is its)
        }
        victim = v;
        getNavigation().stop();
        setAttackState(TELL);
        playSound(FFSounds.SHADE_HISS.get(), 1.4F, 0.9F + random.nextFloat() * 0.25F);
    }

    /** THE LUNGE: it leaves the floor at them - far enough to reach where they stand, never further than a bound. */
    private void spring(LivingEntity v) {
        Vec3 to = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
        double d = to.length();
        Vec3 dir = d > 1.0E-4D ? to.scale(1.0D / d) : Vec3.directionFromRotation(0.0F, getYRot());
        double h = Mth.clamp(0.28D + d * 0.115D, 0.45D, 1.05D);
        setDeltaMovement(dir.x * h, 0.34D, dir.z * h);
        hasImpulse = true;
        bitten = false;
        setAttackState(LUNGE);
        playSound(FFSounds.SHADE_LUNGE.get(), 1.3F, 0.9F + random.nextFloat() * 0.25F);
    }

    /** What it lands on in its spring is bitten - once a lunge. */
    private void bite() {
        // its shepherd's chamber gone dark feeds its bite
        float dmg = (mode == SENT ? SENT_DMG : LUNGE_DMG) * (1.0F + ShadeShepherdEntity.DARK_HERD * shepherdDark());
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.45D, 0.2D, 0.45D),
                e -> e != this && e.isAlive() && !FFAllies.ofTheKing(e)
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                Vec3 push = new Vec3(e.getX() - getX(), 0.0D, e.getZ() - getZ());
                if (push.lengthSqr() > 1.0E-4D) {
                    e.knockback(0.4D, -push.x, -push.z);
                }
                e.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 40, 0), this);
            }
            bitten = true;
            setDeltaMovement(getDeltaMovement().multiply(0.2D, 1.0D, 0.2D));
            return;
        }
    }

    private void face(Vec3 at) {
        double dx = at.x - getX(), dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    // ------------------------------------------------------------------------------------------------ the dark
    /** Where it may stand: air for its body over something to stand on (near `y`). */
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

    /**
     * A DARK place `rMin`..`rMax` from `around` - out of every fire's light, within its leash, as near to it as will
     * do - and if `awayFrom` is given, as far from that as it can find. Null if there is none to be found.
     */
    @Nullable
    Vec3 darkSpot(Vec3 around, double rMin, double rMax, @Nullable Vec3 awayFrom) {
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 leashAt = leashCentre();
        double leash = leashRadius();
        for (int i = 0; i < 12; i++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double d = rMin + random.nextDouble() * (rMax - rMin);
            BlockPos p = standable(around.x + Math.cos(a) * d, getY(), around.z + Math.sin(a) * d);
            if (p == null) {
                continue;
            }
            Vec3 c = Vec3.atBottomCenterOf(p);
            boolean lit = ShadeLight.litNear(level(), c.add(0.0D, 0.5D, 0.0D), ShadeLight.SIGHT);
            double score = (lit ? 1000.0D : 0.0D) + c.distanceTo(position()) * 0.15D;
            if (awayFrom != null) {
                score -= c.distanceTo(awayFrom) * 0.4D;
            }
            if (leashAt != null && c.distanceTo(leashAt) > leash) {
                score += 500.0D;
            }
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return bestScore < 500.0D ? best : null;
    }

    @Nullable
    private Vec3 leashCentre() {
        ShadeShepherdEntity o = owner();
        if (o != null && o.home() != null) {
            return Vec3.atBottomCenterOf(o.home());
        }
        return home != null ? Vec3.atBottomCenterOf(home) : null;
    }

    private double leashRadius() {
        return owner() != null ? ShadeShepherdEntity.LEASH + 4.0D : LEASH;
    }

    // ------------------------------------------------------------------------------------------------ blows
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && getAttackState() == RISE && attackTicks < 8 && !source.is(
                net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;                                              // still coming up out of the floor
        }
        if (inLight && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            amount *= LIT_TAKES;                                       // caught in the light: exposed
        }
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive() && inLight && getAttackState() == 0) {
            retreat = Math.max(retreat, 20);                           // and it makes for the dark
            spot = null;
        }
        return hit;
    }

    @Override
    protected int getDeathDuration() {
        return DEATH_T + 2;
    }

    // ------------------------------------------------------------------------------------------------ heard
    @Override
    protected SoundEvent getAmbientSound() {
        return FFSounds.SHADE_BREATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 90;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.SHADE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FFSounds.SHADE_DEATH.get();
    }

    /** Its own feet: a click and a scuff on stone - the dark is full of them. */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(FFSounds.SHADE_STEP.get(), 0.55F, 0.9F + random.nextFloat() * 0.3F);
    }

    @Override
    protected float getSoundVolume() {
        return 0.9F;
    }

    // ------------------------------------------------------------------------------------------------ saved
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) {
            tag.putUUID("Shepherd", ownerId);
        }
        if (home != null) {
            tag.putIntArray("Home", new int[]{home.getX(), home.getY(), home.getZ()});
        }
        if (room != null) {
            tag.putIntArray("Room", room);
        } else if (roomRel != null) {
            tag.putIntArray("RoomBox", roomRel);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("Shepherd") ? tag.getUUID("Shepherd") : null;
        int[] h = tag.getIntArray("Home");
        if (h.length == 3) {
            home = new BlockPos(h[0], h[1], h[2]);
        }
        int[] r = tag.getIntArray("Room");
        room = r.length == 6 ? r : null;
        int[] rr = tag.getIntArray("RoomBox");
        roomRel = rr.length == 6 ? rr : null;
    }

    // ------------------------------------------------------------------------------------------------ looks
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2, state -> {
            if (isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            switch (getAttackState()) {
                case RISE: return state.setAndContinue(RISE_ANIM);
                case TELL: return state.setAndContinue(TELL_ANIM);
                case LUNGE: return state.setAndContinue(LUNGE_ANIM);
                default: break;
            }
            return state.setAndContinue(FrostServantEntity.going(state) ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "flinch", 1, state ->
                !isDeadOrDying() && hurtTime > 0 && !isAttacking() ? state.setAndContinue(HURT) : PlayState.STOP));
    }

    // ------------------------------------------------------------------------------------------------ the hunt
    /**
     * Where it goes and when it springs. It keeps to the dark: it lurks on a ring round whoever it hunts, out of the
     * light, and springs from there; after a spring it makes for the dark again; caught in the light it leaves it.
     * Called (HERD) it circles its shepherd and springs at whoever comes at him; sent (SENT) it runs straight in.
     */
    static class ShadeGoal extends Goal {
        private final ShadeEntity mob;
        private int repath;

        ShadeGoal(ShadeEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return mob.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private void go(Vec3 at, double speed) {
            at = mob.keepIn(at);                       // (never out of its room)
            if (--repath <= 0 || mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(at.x, at.y, at.z, speed);
                repath = 10;
            }
        }

        @Override
        public void tick() {
            if (mob.getAttackState() != 0) {
                return;
            }
            LivingEntity target = mob.getTarget();
            if (target != null && (!target.isAlive() || target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
                mob.setTarget(null);
                target = null;
            }
            ShadeShepherdEntity owner = mob.owner();
            if (mob.modeTicks > 0) {
                mob.modeTicks--;
            } else if (mob.mode == HERD || mob.mode == SENT) {
                mob.mode = target != null ? STALK : WANDER;
            }
            if (owner == null && mob.mode == HERD) {
                mob.mode = target != null ? STALK : WANDER;
            }
            if (mob.mode == WANDER && target != null) {
                mob.mode = STALK;
            }
            switch (mob.mode) {
                case HERD -> herd(owner, target);
                case SENT -> sent(target);
                case STALK -> stalk(target);
                default -> wander();
            }
        }

        /** Circling its shepherd, a few blocks out, each on its own bearing - and at anyone who comes at him. */
        private void herd(ShadeShepherdEntity owner, @Nullable LivingEntity target) {
            double a = mob.getId() * 1.7D + mob.tickCount * 0.035D;
            Vec3 at = owner.position().add(Math.cos(a) * 3.4D, 0.0D, Math.sin(a) * 3.4D);
            go(at, 1.1D);
            LivingEntity near = owner.getTarget();
            if (near != null && near.distanceToSqr(owner) < 5.0D * 5.0D && mob.cooldown <= 0) {
                double d = mob.distanceTo(near);
                if (d < SPRING_MAX && d > SPRING_MIN && mob.hasLineOfSight(near)) {
                    mob.startTell(near);
                }
            } else if (target != null) {
                mob.getLookControl().setLookAt(target, 20.0F, 20.0F);
            }
        }

        /** Sent: straight in, light or no light, and the spring as soon as it is close. */
        private void sent(@Nullable LivingEntity target) {
            if (target == null) {
                mob.mode = WANDER;
                return;
            }
            go(target.position(), 1.35D);
            double d = mob.distanceTo(target);
            if (d < 4.8D && d > SPRING_MIN && mob.cooldown <= 0 && mob.hasLineOfSight(target)) {
                mob.startTell(target);
            }
        }

        /** Lurking in the dark round them: out of the light, on a ring, springing when it can. */
        private void stalk(@Nullable LivingEntity target) {
            if (target == null) {
                mob.mode = WANDER;
                return;
            }
            double d = mob.distanceTo(target);
            if (mob.retreat > 0) {
                mob.retreat--;
                if (mob.spot == null) {
                    mob.spot = mob.darkSpot(target.position(), 7.0D, 10.0D, target.position());
                }
                if (mob.spot != null) {
                    go(mob.spot, 1.25D);
                }
                return;
            }
            if (!mob.inLight() && d <= SPRING_MAX && d >= SPRING_MIN && mob.cooldown <= 0 && mob.hasLineOfSight(target)) {
                mob.startTell(target);                                 // out of the dark, at them
                return;
            }
            if (mob.inLight()) {                                       // the light is on it: out of it
                BlockPos fire = ShadeLight.nearestLit(mob.level(), mob.position(), ShadeLight.SIGHT + 1.0D);
                if (mob.spot == null || mob.spotTicks-- <= 0) {
                    mob.spot = mob.darkSpot(mob.position(), 4.0D, 9.0D, fire != null ? Vec3.atCenterOf(fire) : null);
                    mob.spotTicks = 20;
                }
                if (mob.spot != null) {
                    go(mob.spot, 1.3D);
                }
                return;
            }
            if (mob.spot == null || mob.spotTicks-- <= 0 || mob.spot.distanceTo(target.position()) > 7.5D) {
                mob.spot = mob.darkSpot(target.position(), 3.8D, 6.2D, null);
                mob.spotTicks = 30 + mob.random.nextInt(30);
            }
            if (mob.spot != null && mob.position().distanceToSqr(mob.spot) > 1.0D) {
                go(mob.spot, 1.0D);
            } else {
                mob.getNavigation().stop();
                mob.getLookControl().setLookAt(target, 20.0F, 20.0F);
            }
        }

        /** Nobody to hunt: it drifts about its dark, near where it was put. */
        private void wander() {
            if (mob.spot == null || mob.spotTicks-- <= 0) {
                Vec3 c = mob.leashCentre();
                mob.spot = mob.darkSpot(c != null ? c : mob.position(), 2.0D, 8.0D, null);
                mob.spotTicks = 80 + mob.random.nextInt(80);
            }
            if (mob.spot != null && mob.position().distanceToSqr(mob.spot) > 1.0D) {
                go(mob.spot, 0.7D);
            }
        }
    }
}
