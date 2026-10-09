package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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

import java.util.UUID;

/**
 * One of the orbs that circle the king before he throws them.
 *
 * <p>Two lives in one entity: while {@code launched} is false it holds a slot
 * on a ring around its owner, and afterwards it flies. Keeping both in one
 * class is what lets the ring stay whole while one leaves it - the others do
 * not care, they just keep their angle.
 */
public class IceOrbEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Boolean> LAUNCHED =
            SynchedEntityData.defineId(IceOrbEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Whether this one is a jagged SPLINTER rather than a smooth orb.
     *
     * <p>The two rings he builds in the third phase are made of the same
     * machinery as the four-orb barrage - hold an angle, then launch - and
     * duplicating all of that to change a model would be three hundred lines
     * for one texture swap. The renderer reads this and picks the geometry;
     * everything else about the entity is identical.
     */
    private static final EntityDataAccessor<Boolean> SPLINTER =
            SynchedEntityData.defineId(IceOrbEntity.class, EntityDataSerializers.BOOLEAN);

    /** Hearts of intent, not raw points - see VelkharEntity.strikeFor. */
    // Raised with the third phase generally - these are its rings, and in
    // that phase strikeFor's floor is the whole blow, so what is written
    // here is what lands.
    private static final float DAMAGE = 0.8F;
    /**
     * What THIS one hits for, so a ring of forty does not hit like forty orbs.
     *
     * <p>This is the bug that deleted a boss outright. The shard rings reuse
     * this entity, and every splinter inherited the four-orb barrage's 3.4
     * hearts - which is 6.8 guaranteed points EACH, applied by strikeFor's
     * floor, times forty. Two hundred and seventy points from one cast.
     *
     * <p>A ring is a shotgun: individually each piece has to be nearly
     * nothing, because the shape of the attack is that a lot of them arrive.
     */
    private float damage = DAMAGE;

    public IceOrbEntity withDamage(float hearts) {
        this.damage = hearts;
        return this;
    }
    private static final double ORBIT_RADIUS = 2.9D;
    private static final double ORBIT_SPEED = 0.075D;
    /**
     * How fast it travels once thrown.
     *
     * <p>Down from 1.45. At that speed the gap between leaving the ring and
     * arriving was under half a second at duelling range, which is less time
     * than it takes to see it start - the orb read as appearing on top of you.
     * Slower makes the homing legible as homing, and the counterplay in the
     * note below (break the line LATE) only exists if there is a line long
     * enough to break.
     */
    private static final double SPEED = 1.05D;
    /**
     * How hard it bends toward its target each tick, and for how long.
     *
     * <p>A dead-straight orb is dodged by walking sideways once and then
     * ignoring it, and a gentle 0.16 curve over 26 ticks turned out to be
     * barely different - it still lost a walking player. It corners properly
     * now, and the counterplay is the window AFTER the steering stops rather
     * than simply moving at all: break the line late and it commits past you.
     */
    private static final double HOMING = 0.34D;
    private static final int HOMING_TICKS = 44;

    private UUID ownerUUID;
    private double angle;
    /**
     * How far the ring's plane is tipped off horizontal, in radians.
     *
     * <p>A ring lying flat around a floating figure reads as a halo, which is
     * decoration. Tipped, it reads as something in ORBIT - the whole Saturn
     * idea - and it also puts part of the ring above the player's eyeline and
     * part below it, so the thing is legible from any angle instead of
     * vanishing edge-on whenever the camera drops to the floor.
     */
    private double tilt;
    /** Per-ring, so one boss can carry two rings of different sizes. */
    private double radius = ORBIT_RADIUS;
    private Vec3 flight = Vec3.ZERO;
    private int flightTicks;
    private UUID chasing;

    public IceOrbEntity(EntityType<? extends IceOrbEntity> type, Level level) {
        super(type, level);
        // DRAWN FAR OUTSIDE ITS OWN HITBOX, so it must not be frustum culled.
        // The renderer paints a ribbon trailing several blocks behind a
        // projectile whose box is a fraction of a block; the game culls
        // against the declared box, so the trail vanished whenever the head
        // left the screen - and a trail is the thing you look at BEHIND you.
        this.noCulling = true;
        this.noPhysics = true;
    }

    public IceOrbEntity(Level level, LivingEntity owner, double startAngle) {
        this(FFEntities.ICE_ORB.get(), level);
        this.ownerUUID = owner.getUUID();
        this.angle = startAngle;
        setPos(owner.getX(), owner.getY(1.4D), owner.getZ());
    }

    /** A ring that is tipped over, and as wide as it likes. */
    public IceOrbEntity(Level level, LivingEntity owner, double startAngle,
                        double tilt, double radius) {
        this(level, owner, startAngle);
        this.tilt = tilt;
        this.radius = radius;
    }

    public boolean isLaunched() {
        return entityData.get(LAUNCHED);
    }

    public boolean isSplinter() {
        return entityData.get(SPLINTER);
    }

    /**
     * 0 to 1 across its first half second, for the renderer's growth.
     *
     * <p>"An animation of them growing and multiplying" - so they are not
     * placed, they GROW: each one comes up from nothing over ten ticks, and
     * because the attack spawns them a few at a time the ring assembles in
     * front of the player rather than appearing complete.
     */
    public float form() {
        return Math.min(1.0F, tickCount / 10.0F);
    }

    /** Builds this one as a splinter on a tipped ring. */
    public IceOrbEntity asSplinter(double tilt, double radius) {
        this.tilt = tilt;
        this.radius = radius;
        entityData.set(SPLINTER, true);
        return this;
    }

    /** Breaks it out of the ring and sends it after someone. */
    public void launchAt(Vec3 target, LivingEntity chase) {
        this.flight = target.subtract(position()).normalize().scale(SPEED);
        this.chasing = chase == null ? null : chase.getUUID();
        this.flightTicks = 0;
        entityData.set(LAUNCHED, true);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, blockPosition(), FFSounds.FROST_BOLT_FIRE.get(),
                    SoundSource.HOSTILE, 1.4F, 1.15F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // ---- THE TRAIL DOES THE WORK ONCE IT IS FLYING.
            //
            // A splinter left a puff of frost every tick whether it was
            // orbiting or travelling, and in flight that reads as a smear of
            // particles with a crystal somewhere inside it - which is what
            // "the particles look mediocre" is. The ribbon already draws the
            // motion, and it draws it far better, so the puff is for the
            // holding pattern only: while the ring turns, the particles are
            // the only thing saying these things are cold.
            if (!isLaunched()) {
                level().addParticle(FFParticles.SOUL_FROST.get(),
                        getX() + (random.nextDouble() - 0.5D) * 0.6D,
                        getY() + (random.nextDouble() - 0.5D) * 0.6D,
                        getZ() + (random.nextDouble() - 0.5D) * 0.6D,
                        0.0D, 0.0D, 0.0D);
            }
            return;
        }

        LivingEntity owner = level() instanceof ServerLevel sl && ownerUUID != null
                && sl.getEntity(ownerUUID) instanceof LivingEntity le ? le : null;

        if (!isLaunched()) {
            // The ring dies with its owner; a stray orbit around nothing
            // would hang in the room for the rest of the fight.
            if (owner == null || !owner.isAlive()) {
                shatter();
                return;
            }
            angle += ORBIT_SPEED;
            if (tilt == 0.0D) {
                setPos(owner.getX() + Math.cos(angle) * radius,
                        owner.getY(1.45D) + Math.sin(angle * 2.0D) * 0.35D,
                        owner.getZ() + Math.sin(angle) * radius);
                return;
            }
            // The tipped ring: the circle is built flat and then rolled about
            // the X axis, so half of it rides above his shoulders and half
            // passes below his knees. One rotation, applied to the whole ring,
            // which is why every orb in a ring must be given the same tilt -
            // mixed tilts are a swarm, not a ring.
            double cx = Math.cos(angle) * radius;
            double cz = Math.sin(angle) * radius;
            setPos(owner.getX() + cx,
                    owner.getY(1.45D) + cz * Math.sin(tilt),
                    owner.getZ() + cz * Math.cos(tilt));
            return;
        }

        // Curve toward them for the first stretch, then commit to the line.
        flightTicks++;
        if (flightTicks <= HOMING_TICKS && chasing != null
                && level() instanceof ServerLevel sl2
                && sl2.getEntity(chasing) instanceof LivingEntity mark && mark.isAlive()) {
            Vec3 want = mark.position().add(0.0D, 0.9D, 0.0D)
                    .subtract(position()).normalize().scale(SPEED);
            flight = flight.scale(1.0D - HOMING).add(want.scale(HOMING))
                    .normalize().scale(SPEED);
        }
        // The tail is GEOMETRY now, drawn by the renderer - see
        // ProjectileTrail. What stood here was four particles laid along each
        // step, which is a dotted line pretending to be a streak.
        setPos(getX() + flight.x, getY() + flight.y, getZ() + flight.z);
        if (tickCount > 160 || !level().noCollision(this, getBoundingBox().inflate(0.05D))) {
            shatter();
            return;
        }
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(1.25D),
                e -> e.isAlive() && !(e instanceof FrostServantEntity)
                        && !(e instanceof VelkharCloneEntity)
                        && !e.getUUID().equals(ownerUUID)
                        && !com.jastkub.frozenfortress.entity.FFAllies.spares(owner, e))) {
            com.jastkub.frozenfortress.entity.boss.VelkharEntity
                    .strikeFor(owner, victim,
                            damage * com.jastkub.frozenfortress.config.FFConfig.mul(com.jastkub.frozenfortress.config.FFConfig.COMMON.iceOrb));
            victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 90, 0), owner);
            // Same pin as the boulder - an orb to the chest roots you long
            // enough for him to reach for the blade.
            victim.addEffect(new MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 3), owner);
            shatter();
            return;
        }
    }

    /** Who it belongs to, so one king cannot sweep another's ring. */
    @org.jetbrains.annotations.Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    /**
     * The ring is called off: it breaks where it hangs.
     *
     * <p>Deliberately NOT a launch. An interrupted barrage used to throw
     * everything it had left in a single tick, and four orbs leaving the same
     * ring on the same frame with the same homing converge inside half a
     * second - they arrive as one object. That is the "he only summons one
     * orb but the animation fires four" report: the four were there, they
     * were just stacked. Breaking them reads as the attack being taken away
     * from him, which is what actually happened.
     */
    public void dismiss() {
        shatter();
    }

    /** It bursts into the shards it is made of. */
    private void shatter() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY(), getZ(), 28, 0.3D, 0.3D, 0.3D, 0.35D);
            serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                    getX(), getY(), getZ(), 12, 0.25D, 0.25D, 0.25D, 0.12D);
            serverLevel.playSound(null, blockPosition(), FFSounds.FROST_BOLT_HIT.get(),
                    SoundSource.HOSTILE, 1.2F, 1.0F);
        }
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(LAUNCHED, false);
        builder.define(SPLINTER, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        damage = tag.contains("Damage") ? tag.getFloat("Damage") : DAMAGE;
        angle = tag.getDouble("Angle");
        entityData.set(LAUNCHED, tag.getBoolean("Launched"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Damage", damage);
        tag.putDouble("Angle", angle);
        tag.putBoolean("Launched", entityData.get(LAUNCHED));
    }


    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.still");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, this::idleAnim));
    }

    private <E extends GeoEntity> PlayState idleAnim(AnimationState<E> state) {
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
