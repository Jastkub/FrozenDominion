package com.jastkub.frozenfortress.entity.effect;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A mark burned onto the floor. Purely something to look at.
 *
 * <p>Two things use it and they want the same machinery: the sigil that
 * scribes itself under the executioner's blade before it falls, and the rift
 * the colossus climbs out of. Both are a flat textured disc lying on the
 * ground that turns, breathes and expires - so they are one entity with a
 * variant rather than two nearly identical ones.
 *
 * <p>It owns nothing, damages nothing and collides with nothing. Everything
 * that actually happens is driven by whatever spawned it; this is the part
 * the player looks at while it happens.
 */
public class FloorSigilEntity extends Entity {

    private static final EntityDataAccessor<Integer> LIFETIME =
            SynchedEntityData.defineId(FloorSigilEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(FloorSigilEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(FloorSigilEntity.class, EntityDataSerializers.INT);

    /** The scribed dial: rings, ticks and a glyph. */
    public static final int SIGIL = 0;
    /** The vortex the ground opens into. */
    public static final int RIFT = 1;

    public FloorSigilEntity(EntityType<? extends FloorSigilEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    public FloorSigilEntity(Level level, double x, double y, double z,
                            float radius, int lifetime, int variant) {
        this(FFEntities.FLOOR_SIGIL.get(), level);
        setPos(x, y, z);
        entityData.set(RADIUS, radius);
        entityData.set(LIFETIME, lifetime);
        entityData.set(VARIANT, variant);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(LIFETIME, 60);
        entityData.define(RADIUS, 3.0F);
        entityData.define(VARIANT, SIGIL);
    }

    public int getLifetime() {
        return entityData.get(LIFETIME);
    }

    public float getRadius() {
        return entityData.get(RADIUS);
    }

    public int getVariant() {
        return entityData.get(VARIANT);
    }

    /** Grows the mark while whatever is opening it is still opening it. */
    public void setRadius(float radius) {
        entityData.set(RADIUS, radius);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > getLifetime()) {
            discard();
        }
    }

    /** Rendered from the far side of a boss arena. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 6400.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(LIFETIME, tag.getInt("Lifetime"));
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(VARIANT, tag.getInt("Variant"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Lifetime", getLifetime());
        tag.putFloat("Radius", getRadius());
        tag.putInt("Variant", getVariant());
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }
}
