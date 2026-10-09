package com.jastkub.frozenfortress.entity.projectile;

import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * SOMETHING THROWN IN AN ARC, flown the same on both sides.
 *
 * <p>It shook because two things were moving it. The client flew it on its own
 * from the speed it was spawned with, and the server sent its own position and
 * - worse - its own rotation, rounded to 1.4 degrees, every tick; each packet
 * pulled it back a little and twisted it a little, and a thing as long as a
 * house shows every one of those.
 *
 * <p>So the arc is not simulated, it is a FORMULA. Where it was thrown from and
 * how fast are synced once; after n ticks it is at from + n*v - g*n(n-1)/2, on
 * the server and on every client alike, and the client ignores the server's
 * corrections while it flies. Nothing to drift, nothing to snap back. (The
 * client stops short if the next step would put it in a block, and waits for
 * the server to say where it landed.)
 */
public abstract class BallisticEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Vector3f> FROM =
            SynchedEntityData.defineId(BallisticEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> VEL =
            SynchedEntityData.defineId(BallisticEntity.class, EntityDataSerializers.VECTOR3);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Ticks flown, on this side. */
    protected int flown;
    /** Client: the next step would be inside a block - it waits there for the server. */
    private boolean stalled;

    protected BallisticEntity(EntityType<? extends BallisticEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** Its gravity, in blocks per tick per tick. */
    protected abstract double gravity();

    /** While this is true it flies by the formula (and ignores the server's corrections). */
    protected boolean inFlight() {
        return true;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(FROM, new Vector3f());
        builder.define(VEL, new Vector3f());
    }

    /** Thrown from `from` to come down on `at` after `flight` ticks. */
    protected void launch(Vec3 from, Vec3 at, int flight) {
        double t = Math.max(6, flight);
        Vec3 d = at.subtract(from);
        // after n ticks: n*v - g*n(n-1)/2  ->  v = d/t + g(t-1)/2 upward
        throwFrom(from, new Vec3(d.x / t, d.y / t + gravity() * (t - 1.0D) * 0.5D, d.z / t));
    }

    /** Thrown from `from` with velocity `v` (blocks per tick). */
    protected void throwFrom(Vec3 from, Vec3 v) {
        entityData.set(FROM, new Vector3f((float) from.x, (float) from.y, (float) from.z));
        entityData.set(VEL, new Vector3f((float) v.x, (float) v.y, (float) v.z));
        flown = 0;
        Vec3 f = from();
        setPos(f.x, f.y, f.z);
        setDeltaMovement(velAt(0));
    }

    protected Vec3 from() {
        Vector3f f = entityData.get(FROM);
        return new Vec3(f.x, f.y, f.z);
    }

    protected boolean launched() {
        return entityData.get(VEL).lengthSquared() > 1.0E-8F;
    }

    /** Where it is n ticks into its flight. */
    public Vec3 posAt(double n) {
        Vector3f v = entityData.get(VEL);
        Vec3 f = from();
        return new Vec3(f.x + v.x * n, f.y + v.y * n - gravity() * n * (n - 1.0D) * 0.5D, f.z + v.z * n);
    }

    /** Its velocity on the step after tick s (fractional s for the renderer). */
    public Vec3 velAt(double s) {
        Vector3f v = entityData.get(VEL);
        return new Vec3(v.x, v.y - gravity() * s, v.z);
    }

    /** Where it will be after this tick, if it flies on. */
    protected Vec3 nextPos() {
        return posAt(flown + 1);
    }

    /**
     * One tick of flight. Returns false on the client while it waits (its next
     * step would be in a block): the server decides where it ends.
     */
    protected boolean advance() {
        if (!launched()) {
            return false;
        }
        Vec3 next = nextPos();
        if (level().isClientSide) {
            if (stalled) {
                return false;
            }
            // the step itself against what is really
            // there - as the server tests it - not the whole cell it ends in: a chain, a lantern, a bar fills only a
            // sliver of its cell, and a bomb that passed by one waited in the air for a server that flew it on
            HitResult hit = level().clip(new ClipContext(position(), next, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                stalled = true;
                return false;
            }
        }
        flown++;
        setPos(next.x, next.y, next.z);
        setDeltaMovement(velAt(flown));
        return true;
    }

    /** The server moves it only when it lands or breaks; in the air the formula moves it. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (inFlight() && launched()) {
            if (stalled) {
                resume(new Vec3(x, y, z));
            }
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    /**
     * (Client) Stopped short, and the server says it is further on along the arc: it was not stopped after all - find
     * how far along it is and fly on from there by the formula.
     */
    private void resume(Vec3 at) {
        Vector3f v = entityData.get(VEL);
        Vec3 f = from();
        double n;
        if (Math.abs(v.x) >= Math.abs(v.z) && Math.abs(v.x) > 1.0E-3D) {
            n = (at.x - f.x) / v.x;
        } else if (Math.abs(v.z) > 1.0E-3D) {
            n = (at.z - f.z) / v.z;
        } else {
            return;                                                     // straight up or down: nothing to read it by
        }
        if (!Double.isFinite(n) || n <= flown + 0.5D || posAt(n).distanceToSqr(at) > 1.0D) {
            return;
        }
        flown = (int) Math.round(n);
        Vec3 p = posAt(flown);
        setPos(p.x, p.y, p.z);
        setDeltaMovement(velAt(flown));
        stalled = false;
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        if (inFlight() && launched()) {
            return;
        }
        super.lerpMotion(x, y, z);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 9216.0D;
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        discard();                                              // it does not outlast a reload
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
