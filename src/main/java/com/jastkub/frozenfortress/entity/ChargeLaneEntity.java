package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * THE LANE OF THE MONSTROSITY'S CHARGE: the moment it fixes its line, a lane of frost runs out over the floor before it - as wide as it is,
 * as far as the charge will carry it (or to the first wall) - chevrons crawling along it the way it will run, brighter
 * as the launch comes. Only to be read: it touches nobody. Drawn as geometry (ChargeLaneRenderer).
 */
public class ChargeLaneEntity extends Entity {

    private static final EntityDataAccessor<Float> YAW =
            SynchedEntityData.defineId(ChargeLaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LENGTH =
            SynchedEntityData.defineId(ChargeLaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH =
            SynchedEntityData.defineId(ChargeLaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(ChargeLaneEntity.class, EntityDataSerializers.INT);
    /** The charge's own reach: 1.42 a tick for its 26 ticks. */
    public static final double REACH = 37.0D;

    public ChargeLaneEntity(EntityType<? extends ChargeLaneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** Laid before `who`, along `line`, for `life` ticks: out to the charge's reach or the first wall. */
    public static void lay(Entity who, Vec3 line, int life) {
        Level level = who.level();
        ChargeLaneEntity lane = new ChargeLaneEntity(FFEntities.CHARGE_LANE.get(), level);
        Vec3 from = who.position().add(0.0D, 1.0D, 0.0D);
        Vec3 to = from.add(line.scale(REACH));
        HitResult hit = level.clip(new ClipContext(from.add(line.scale(who.getBbWidth() * 0.5D)), to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, who));
        double len = hit.getType() == HitResult.Type.MISS ? REACH : hit.getLocation().distanceTo(from);
        float yaw = (float) (Math.atan2(line.z, line.x) * (180.0D / Math.PI)) - 90.0F;
        lane.moveTo(who.getX(), who.getY(), who.getZ(), yaw, 0.0F);
        lane.entityData.set(YAW, yaw);
        lane.entityData.set(LENGTH, (float) Math.max(4.0D, len));
        lane.entityData.set(WIDTH, (float) Math.max(2.0D, who.getBbWidth() * 0.6D));
        lane.entityData.set(LIFE, life);
        level.addFreshEntity(lane);
    }

    public float laneYaw() {
        return entityData.get(YAW);
    }

    public float laneLength() {
        return entityData.get(LENGTH);
    }

    public float laneWidth() {
        return entityData.get(WIDTH);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= life()) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(YAW, 0.0F);
        builder.define(LENGTH, 20.0F);
        builder.define(WIDTH, 4.0F);
        builder.define(LIFE, 50);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0D * 96.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
