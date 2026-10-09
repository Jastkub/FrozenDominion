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
 * His weather. A storm cloud, whether it is lying on his floor or hanging over
 * his head.
 *
 * <p>IT WAS PARTICLES, AND PARTICLES CANNOT BE WEATHER. A few dozen flakes
 * drifting near the ground is a few dozen flakes drifting near the ground -
 * the eye counts them. So this is a real object with real geometry, drawn by
 * GroundMistRenderer as a couple of dozen overlapping lumps with lightning
 * inside them.
 *
 * <p>TWO FORMS, ONE OBJECT. The stones come out from under a bank of storm
 * lying flat on the floor; the spears come out of one massed overhead. They
 * are the same weather seen from two sides, and the only difference worth
 * carrying is the SHAPE of the volume its lumps are scattered through - flat
 * and wide down there, domed and deep up here. Splitting that into two
 * entities would have duplicated the lightning, the fade and the culling fix
 * to change one number.
 *
 * <p>It carries nothing but its size, its form and its age; it has no
 * collision, no damage and no opinion about the attack that spawned it. It
 * gathers, it breathes, it goes.
 */
public class GroundMistEntity extends Entity {

    /** How wide the cloud is, in blocks. Synced so the renderer can size it. */
    private static final EntityDataAccessor<Float> SPREAD =
            SynchedEntityData.defineId(GroundMistEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(GroundMistEntity.class, EntityDataSerializers.INT);
    /** True when it hangs in the air; false when it lies on the floor. */
    private static final EntityDataAccessor<Boolean> AIRBORNE =
            SynchedEntityData.defineId(GroundMistEntity.class, EntityDataSerializers.BOOLEAN);

    /** Ticks to come in and to go out, so it never pops. */
    public static final int FADE_IN = 22;
    public static final int FADE_OUT = 30;

    public GroundMistEntity(EntityType<? extends GroundMistEntity> type, Level level) {
        super(type, level);
        // DRAWN FAR OUTSIDE ITS OWN HITBOX, so it must not be frustum culled.
        // The renderer paints a disc several blocks across from an entity whose type
        // declares half a block; the game culls against the declared box, so
        // the effect vanished whenever its centre point left the screen -
        // which, for something lying on the floor, is most of a boss fight.
        this.noCulling = true;
        this.noPhysics = true;
    }

    public GroundMistEntity(Level level, double x, double y, double z,
                            float spread, int life) {
        this(level, x, y, z, spread, life, false);
    }

    public GroundMistEntity(Level level, double x, double y, double z,
                            float spread, int life, boolean airborne) {
        this(FFEntities.GROUND_MIST.get(), level);
        setPos(x, y, z);
        entityData.set(SPREAD, spread);
        entityData.set(LIFE, life);
        entityData.set(AIRBORNE, airborne);
    }

    public float spread() {
        return entityData.get(SPREAD);
    }

    /** Hanging in the air (domed and deep) rather than lying on the floor. */
    public boolean isAirborne() {
        return entityData.get(AIRBORNE);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    /** 0 to 1 and back down, so the renderer has one number to fade on. */
    public float density(float partialTick) {
        float age = tickCount + partialTick;
        int life = life();
        if (age < FADE_IN) {
            float f = age / FADE_IN;
            return f * f * (3.0F - 2.0F * f);
        }
        if (age > life - FADE_OUT) {
            float f = Math.max(0.0F, (life - age) / FADE_OUT);
            return f * f * (3.0F - 2.0F * f);
        }
        return 1.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > life()) {
            discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 16384.0D;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(SPREAD, 6.0F);
        builder.define(LIFE, 200);
        builder.define(AIRBORNE, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(SPREAD, tag.getFloat("Spread"));
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(AIRBORNE, tag.getBoolean("Airborne"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Spread", spread());
        tag.putInt("Life", life());
        tag.putBoolean("Airborne", isAirborne());
    }

}
