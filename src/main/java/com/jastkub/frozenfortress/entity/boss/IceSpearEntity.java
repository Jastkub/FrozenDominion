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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.Mth;
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
 * A spear out of the cloud he hangs over their head.
 *
 * <p>The point is the entity's origin and the model stands above it, which is
 * what makes the landing work: the spear stops where its tip stopped, sinks a
 * quarter of a block, and stands there. It has to be VISIBLY stuck in the
 * floor for a while afterwards - a rain of spears that vanishes on contact
 * reads as particles, and the whole ask was for something that lands.
 */
public class IceSpearEntity extends Entity implements GeoEntity {

    /**
     * Drawn as a crossbow bolt rather than as a thrown spear.
     *
     * <p>Same object, same flight, same trail - a different thing threw it.
     * A whole parallel entity, registry entry, renderer and spawn packet to
     * change two resource paths is a lot of surface for one decision; the
     * renderer can answer this per entity. Same call made for the colossus's
     * shard, for the same reason.
     */
    private static final EntityDataAccessor<Boolean> QUARREL =
            SynchedEntityData.defineId(IceSpearEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> PLANTED =
            SynchedEntityData.defineId(IceSpearEntity.class, EntityDataSerializers.BOOLEAN);

    /** Hearts of intent, not raw points - see VelkharEntity.strikeFor.
     *  Lower than the orb and the bolt because the sky throws a lot of
     *  these and every one of them is dodgeable by walking. */
    private static final float DAMAGE = 2.6F;
    /**
     * How hard the crossbow's bolt shoves, and how much of that is lift.
     *
     * <p>Sized against the drag a player carries in the air - 0.91 a tick, so
     * a shove of v travels roughly v/0.09 while they are off the ground.
     * Around half a block a tick puts them two or three back: enough that a
     * volley walks you across the arena and not so much that one bolt throws
     * you out of it. The lift is only there to break ground friction, which
     * otherwise eats most of a horizontal push.
     */
    private static final double QUARREL_KNOCK = 0.52D;
    private static final double QUARREL_LIFT = 0.21D;
    /** How long it stands in the floor before it goes. */
    private static final int PLANTED_TICKS = 70;
    private static final int MAX_FLIGHT = 140;
    /** Terminal velocity, and how deep the tip buries itself. */
    private static final double MAX_FALL = 1.25D;
    private static final double SINK = 0.26D;

    private UUID ownerUUID;
    private int plantedFor;

    public IceSpearEntity(EntityType<? extends IceSpearEntity> type, Level level) {
        super(type, level);
        // DRAWN FAR OUTSIDE ITS OWN HITBOX, so it must not be frustum culled.
        // The renderer paints a ribbon trailing several blocks behind a
        // projectile whose box is a fraction of a block; the game culls
        // against the declared box, so the trail vanished whenever the head
        // left the screen - and a trail is the thing you look at BEHIND you.
        this.noCulling = true;
        this.noPhysics = true;
    }

    /**
     * Who it is looking for, and how hard it is allowed to look.
     *
     * <p>THEY DO NOT FALL ANY MORE. A shard dropped straight down is beaten by
     * one sidestep and then ignored, which is why there had to be thirty of
     * them - the attack was a volume problem. Four that STEER are a different
     * question: each one is a thing you have to keep moving away from, and
     * four of those is more pressure than thirty of the other kind.
     *
     * <p>The steering runs out. A spear that corrects all the way in cannot be
     * beaten at all, so the counterplay is the window AFTER it commits: break
     * the line late and it goes past you.
     */
    private java.util.UUID chasing;
    private int steerLeft;
    /**
     * A HUNTER out of the cloud:
     * launched fast and all but weightless, so it flies AT them on a slant. With the weather's full pull a spear
     * leaving at a quarter of a block a tick was falling within a few ticks - steering only turns a velocity, it
     * never adds to it, and the drop won every time.
     */
    private boolean hunting;

    public IceSpearEntity hunting() {
        this.hunting = true;
        return this;
    }
    private static final double STEER = 0.11D;

    public void chase(LivingEntity mark, int ticks) {
        this.chasing = mark == null ? null : mark.getUUID();
        this.steerLeft = ticks;
    }

    public IceSpearEntity(Level level, LivingEntity owner, Vec3 at, Vec3 motion) {
        this(FFEntities.ICE_SPEAR.get(), level);
        this.ownerUUID = owner.getUUID();
        setPos(at.x, at.y, at.z);
        setDeltaMovement(motion);
        setYRot(level.getRandom().nextFloat() * 360.0F);
        this.yRotO = getYRot();
    }

    /** Draw this one as the crossbow's bolt. */
    public void setQuarrel(boolean quarrel) {
        entityData.set(QUARREL, quarrel);
    }

    public boolean isQuarrel() {
        return entityData.get(QUARREL);
    }

    public boolean isPlanted() {
        return entityData.get(PLANTED);
    }

    /** How long it has been standing there, for the renderer's fade-out. */
    public int plantedFor() {
        return plantedFor;
    }

    public static int plantedLifetime() {
        return PLANTED_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        // ================================================================
        // A BOLT POINTS WHERE IT IS GOING.
        //
        // The constructor gives every one of these a RANDOM yaw, which is
        // exactly right for the weather's spears - they tumble out of a cloud
        // and no two should face the same way. A crossbow bolt is the other
        // thing entirely: it is a fletched shaft on a flat trajectory, and
        // spawning it pointing somewhere arbitrary is why they were flying
        // sideways.
        //
        // Taken off the velocity every tick rather than set once at the
        // muzzle, because these steer - see chase(). A bolt that keeps its
        // launch heading while curving onto a target is a bolt flying crabwise
        // for the second half of its flight.
        // ================================================================
        if (isQuarrel() && !isPlanted()) {
            // ---- THE ENTITY'S OWN ROTATION IS HELD AT ZERO, deliberately.
            //
            // Setting it from the velocity looked right and did not work, and
            // the reason is that the entity's yaw is not what aims the model:
            // GeckoLib turns the whole thing by that yaw with its own sign
            // convention, and the two geometries this entity carries are built
            // along DIFFERENT AXES - the weather's spear runs up Y, the bolt
            // runs along Z. One renderer cannot aim both from one number.
            //
            // So the bolt is aimed in the renderer instead, from the velocity
            // directly, and this stays at zero so GeckoLib's rotation is the
            // identity and cannot fight it. See IceSpearRenderer.
            yRotO = 0.0F;
            setYRot(0.0F);
            xRotO = 0.0F;
            setXRot(0.0F);
        }
        if (isPlanted()) {
            plantedFor++;
            if (level().isClientSide) {
                if (plantedFor % 6 == 0) {
                    level().addParticle(FFParticles.BLIZZARD_FLAKE.get(),
                            getX() + (random.nextDouble() - 0.5D) * 0.5D, getY() + 0.1D,
                            getZ() + (random.nextDouble() - 0.5D) * 0.5D, 0.0D, 0.01D, 0.0D);
                }
            } else if (plantedFor > PLANTED_TICKS) {
                melt();
            }
            return;
        }

        if (level().isClientSide) {
            level().addParticle(FFParticles.SOUL_FROST.get(),
                    getX(), getY() + 0.5D, getZ(), 0.0D, 0.0D, 0.0D);
            return;
        }

        // ---- IT STEERS, while it still has steering left
        if (steerLeft > 0 && chasing != null
                && level() instanceof net.minecraft.server.level.ServerLevel sl
                && sl.getEntity(chasing) instanceof LivingEntity mark && mark.isAlive()) {
            steerLeft--;
            Vec3 have = getDeltaMovement();
            double speed = have.length();
            if (speed > 1.0E-4D) {
                // at the chest (08.10.2026: it was the knees - 0.7 - and from a cloud overhead a line laid at the knees
                // meets the floor in front of them; see throwSpear)
                Vec3 want = new Vec3(mark.getX(), mark.getY(0.55D), mark.getZ())
                        .subtract(position()).normalize().scale(speed);
                setDeltaMovement(have.scale(1.0D - STEER).add(want.scale(STEER))
                        .normalize().scale(speed));
            }
        }

        Vec3 motion = getDeltaMovement();
        // ================================================================
        // A BOLT DOES NOT ARC LIKE A FALLING SPEAR.
        //
        // Every one of these took the same 0.085 a tick of gravity. On the
        // weather's spears that is the whole point - they fall out of a cloud.
        // On a crossbow bolt fired flat across twenty blocks it is half a
        // block of drop by the time it arrives, which is why they were landing
        // at the player's feet instead of in them, and why the ones fired from
        // the top of the spire fell shortest of all: the longer the flight,
        // the further under the aim it ends up.
        //
        // A quarrel keeps a tenth of it - enough that a very long shot still
        // settles rather than flying dead straight forever, not enough to miss
        // with.
        // ================================================================
        // DEAD STRAIGHT. Even a tenth of the drop showed up as a dip over
        // twenty blocks, and a bolt from a machine on top of a spire has no
        // business sagging - the shot is the flat one in his kit.
        // A HUNTER FLIES ON ITS LINE: the hundredth of a block a tick it still fell, and the drag on
        // its level speed alone, both bent it down once the steering stopped - from a cloud overhead, into the floor
        // a step short of them. It keeps the line its steering left it on now; leaving that line late still lets it by.
        double pull = isQuarrel() || hunting ? 0.0D : 0.085D;
        double drag = hunting ? 1.0D : 0.99D;
        motion = new Vec3(motion.x * drag, Math.max(-MAX_FALL, motion.y - pull),
                motion.z * drag);
        setDeltaMovement(motion);

        Vec3 from = position();
        Vec3 to = from.add(motion);
        // Clipped rather than tested after the fact: at better than a block a
        // tick, a spear that only notices the floor once it is inside it ends
        // up buried somewhere between one and two blocks deep.
        BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        boolean blocked = hit.getType() != HitResult.Type.MISS;
        Vec3 end = blocked ? hit.getLocation() : to;

        LivingEntity owner = ownerUUID != null && level() instanceof ServerLevel sl
                && sl.getEntity(ownerUUID) instanceof LivingEntity le ? le : null;
        // WHO IT MEETS ON THE WAY, before the floor: the whole of this tick's path, not only where it ends - at three
        // quarters of a block a tick the end of a tick can be past a player the path went through, and a path that
        // ran through them and on into the floor behind them used to plant there
        LivingEntity victim = struckOn(from, end, owner);
        if (victim == null && blocked) {
            plant(hit.getLocation());
            return;
        }
        setPos(end.x, end.y, end.z);

        if (victim != null) {
            com.jastkub.frozenfortress.entity.boss.VelkharEntity
                    .strikeFor(owner, victim, DAMAGE);
            victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 0), owner);
            // ---- AND A BOLT HITS LIKE A BOLT.
            //
            // Only the crossbow's. The weather's spears fall on you and should
            // not shove, but a quarrel is a thing fired from a machine and it
            // ought to move you off the spot you were standing on. Along the
            // line of flight, so being hit pushes you the way the shot was
            // going rather than away from the shooter.
            //
            // AFTER strikeFor, never before. hurt() applies vanilla's own
            // knockback and it overwrites anything written first - the same
            // trap that made the spire's shove do nothing.
            if (isQuarrel()) {
                Vec3 go = getDeltaMovement();
                if (go.lengthSqr() > 1.0E-6D) {
                    Vec3 push = go.normalize().scale(QUARREL_KNOCK);
                    victim.setDeltaMovement(victim.getDeltaMovement()
                            .add(push.x, QUARREL_LIFT, push.z));
                    victim.hurtMarked = true;
                    victim.hasImpulse = true;
                    if (victim instanceof net.minecraft.server.level.ServerPlayer sp) {
                        sp.connection.send(new net.minecraft.network.protocol.game
                                .ClientboundSetEntityMotionPacket(sp));
                    }
                }
            }
            shatter();
            return;
        }
        if (tickCount > MAX_FLIGHT) {
            shatter();
        }
    }

    /** The first thing alive (not his) this tick's path from `from` to `end` runs through, or null. */
    @javax.annotation.Nullable
    private LivingEntity struckOn(Vec3 from, Vec3 end, @javax.annotation.Nullable LivingEntity owner) {
        LivingEntity first = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(from, end).inflate(1.0D),
                e -> e.isAlive() && !(e instanceof FrostServantEntity)
                        && !(e instanceof VelkharEntity) && !(e instanceof VelkharCloneEntity
                        && !com.jastkub.frozenfortress.entity.FFAllies.spares(owner, e)))) {
            net.minecraft.world.phys.AABB box = e.getBoundingBox().inflate(0.35D);
            Vec3 at = box.contains(from) ? from : box.clip(from, end).orElse(null);
            if (at != null && at.distanceToSqr(from) < best) {
                best = at.distanceToSqr(from);
                first = e;
            }
        }
        return first;
    }

    private void plant(Vec3 where) {
        setPos(where.x, where.y - SINK, where.z);
        setDeltaMovement(Vec3.ZERO);
        entityData.set(PLANTED, true);
        plantedFor = 0;
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), where.y + 0.05D, getZ(), 12, 0.25D, 0.05D, 0.25D, 0.18D);
            serverLevel.playSound(null, blockPosition(), FFSounds.ICE_SPIKE_EMERGE.get(),
                    SoundSource.HOSTILE, 0.8F, 1.3F + random.nextFloat() * 0.2F);
        }
    }

    private void melt() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                    getX(), getY() + 0.5D, getZ(), 14, 0.2D, 0.5D, 0.2D, 0.05D);
        }
        discard();
    }

    private void shatter() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY() + 0.4D, getZ(), 16, 0.2D, 0.3D, 0.2D, 0.25D);
            serverLevel.playSound(null, blockPosition(), FFSounds.FROST_BOLT_HIT.get(),
                    SoundSource.HOSTILE, 0.9F, 1.1F);
        }
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(QUARREL, false);
        builder.define(PLANTED, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(PLANTED, tag.getBoolean("Planted"));
        plantedFor = tag.getInt("PlantedFor");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Planted", isPlanted());
        tag.putInt("PlantedFor", plantedFor);
    }


    private static final RawAnimation STILL = RawAnimation.begin().thenLoop("animation.still");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "still", 0, this::stillAnim));
    }

    private <E extends GeoEntity> PlayState stillAnim(AnimationState<E> state) {
        return state.setAndContinue(STILL);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
