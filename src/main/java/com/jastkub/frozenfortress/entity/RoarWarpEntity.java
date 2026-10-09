package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * THE AIR BENT BY A ROAR: what the Ice Monstrosity's bellow does to the air in front of it - a stream of
 * churned air out of its maw, and rings of it thrown out round it, through which the room is seen bent.
 *
 * <p>The thing itself is only its owner, its life and its strength; it is drawn by the client
 * (client.RoarWarpFx: its shells and its stream are meshes, and what is behind them is seen through them displaced -
 * the frame so far, sampled bent). It rides its owner's maw, which only the client knows (the head bone, this frame).
 */
public class RoarWarpEntity extends Entity {

    private static final EntityDataAccessor<Integer> OWNER =
            SynchedEntityData.defineId(RoarWarpEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(RoarWarpEntity.class, EntityDataSerializers.INT);
    /** 1: the great roar (its greeting, its ROAR, the fracture); less for a grunt with a blow. */
    private static final EntityDataAccessor<Float> POWER =
            SynchedEntityData.defineId(RoarWarpEntity.class, EntityDataSerializers.FLOAT);
    /** How far out its rings go, in blocks. */
    private static final EntityDataAccessor<Float> REACH =
            SynchedEntityData.defineId(RoarWarpEntity.class, EntityDataSerializers.FLOAT);
    /** How many rings it throws out (a ring every {@link #RING_EVERY} ticks). */
    private static final EntityDataAccessor<Integer> RINGS =
            SynchedEntityData.defineId(RoarWarpEntity.class, EntityDataSerializers.INT);

    public static final int RING_EVERY = 6;
    /** Ticks a ring takes to go out to its reach and die. */
    public static final int RING_LIFE = 18;

    public RoarWarpEntity(EntityType<? extends RoarWarpEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;                  // its rings go out fifteen blocks from a point
    }

    /**
     * Its owner roars: the air before it bends for `life` ticks, `rings` rings going out to `reach`, at `power`.
     */
    public static RoarWarpEntity roar(Entity owner, int life, float power, float reach, int rings) {
        RoarWarpEntity w = new RoarWarpEntity(FFEntities.ROAR_WARP.get(), owner.level());
        w.entityData.set(OWNER, owner.getId());
        w.entityData.set(LIFE, life);
        w.entityData.set(POWER, power);
        w.entityData.set(REACH, reach);
        w.entityData.set(RINGS, rings);
        w.moveTo(owner.getX(), owner.getY() + owner.getBbHeight() * 0.6D, owner.getZ(), 0.0F, 0.0F);
        owner.level().addFreshEntity(w);
        return w;
    }

    @Nullable
    public Entity owner() {
        return level().getEntity(entityData.get(OWNER));
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public float power() {
        return entityData.get(POWER);
    }

    public float reach() {
        return entityData.get(REACH);
    }

    public int rings() {
        return entityData.get(RINGS);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1);
        builder.define(LIFE, 40);
        builder.define(POWER, 1.0F);
        builder.define(REACH, 12.0F);
        builder.define(RINGS, 3);
    }

    @Override
    public void tick() {
        super.tick();
        Entity o = owner();
        if (o != null) {
            // (its dying squeak is roared too, so it keeps to the body until the body is gone, not until it is dead)
            setPos(o.getX(), o.getY() + o.getBbHeight() * 0.6D, o.getZ());
        }
        if (!level().isClientSide && (tickCount >= life() || o == null || o.isRemoved())) {
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
