package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The charge running through one patch of fog before a stone comes out of it.
 *
 * <p>This was particles thrown upward, and it read as exactly that: a handful
 * of motes going up. What "current, energy, electricity in the fog" needs is
 * FILAMENTS - bright jagged lines struck between two points, holding for a
 * few frames and then struck somewhere else - and a line is geometry.
 *
 * <p>The entity carries almost nothing: where it is, how far up it reaches,
 * and how hard it is running. The arcs themselves are generated in the
 * renderer from the entity's id and the world clock, so they cost no network
 * traffic at all and every client sees the same flicker.
 */
public class SpotArcEntity extends Entity {

    /** How far up the filaments reach, in blocks. */
    private static final EntityDataAccessor<Float> REACH =
            SynchedEntityData.defineId(SpotArcEntity.class, EntityDataSerializers.FLOAT);
    /** 0 to 1 - it builds, and the renderer draws more and brighter. */
    private static final EntityDataAccessor<Float> CHARGE =
            SynchedEntityData.defineId(SpotArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(SpotArcEntity.class, EntityDataSerializers.INT);

    public SpotArcEntity(EntityType<? extends SpotArcEntity> type, Level level) {
        super(type, level);
        // DRAWN FAR OUTSIDE ITS OWN HITBOX, so it must not be frustum culled.
        // The renderer paints filaments metres tall from an entity whose type
        // declares half a block; the game culls against the declared box, so
        // the effect vanished whenever its centre point left the screen -
        // which, for something lying on the floor, is most of a boss fight.
        this.noCulling = true;
        this.noPhysics = true;
    }

    public SpotArcEntity(Level level, double x, double y, double z,
                         float reach, int life) {
        this(FFEntities.SPOT_ARC.get(), level);
        setPos(x, y, z);
        entityData.set(REACH, reach);
        entityData.set(LIFE, life);
    }

    public float reach() {
        return entityData.get(REACH);
    }

    public float charge() {
        return entityData.get(CHARGE);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        int life = entityData.get(LIFE);
        entityData.set(CHARGE, Math.min(1.0F, tickCount / (float) Math.max(1, life)));
        if (tickCount > life) {
            discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 9216.0D;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(REACH, 3.0F);
        builder.define(CHARGE, 0.0F);
        builder.define(LIFE, 10);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(REACH, tag.getFloat("Reach"));
        entityData.set(LIFE, tag.getInt("Life"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Reach", reach());
        tag.putInt("Life", entityData.get(LIFE));
    }

}
