package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * ONE ARC OF LIGHTNING between two points, for a few ticks: the cloud wall biting somebody who touched it, a storm orb
 * licking at whoever drifts too near, an anchor's pylon spitting as it breaks. Geometry, not particles - a jagged
 * ribbon with a hot core and a few forks, re-struck every other tick (StormEyeRenderers.Bolt, StormEyeBolts).
 *
 * <p>It does nothing; whoever spawned it has already dealt the blow.
 */
public class StormEyeBoltEntity extends StormEyeFxEntity {

    public static final int COLD = 0, VIOLET = 1;

    private static final EntityDataAccessor<Float> TX =
            SynchedEntityData.defineId(StormEyeBoltEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TY =
            SynchedEntityData.defineId(StormEyeBoltEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TZ =
            SynchedEntityData.defineId(StormEyeBoltEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(StormEyeBoltEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WIDTH =
            SynchedEntityData.defineId(StormEyeBoltEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TINT =
            SynchedEntityData.defineId(StormEyeBoltEntity.class, EntityDataSerializers.INT);

    public StormEyeBoltEntity(EntityType<? extends StormEyeBoltEntity> type, Level level) {
        super(type, level);
    }

    /** An arc from `from` to `to`, `life` ticks, `width` blocks across its glow. */
    public static StormEyeBoltEntity arc(ServerLevel level, Vec3 from, Vec3 to, int life, float width, int tint) {
        StormEyeBoltEntity b = new StormEyeBoltEntity(FFEntities.STORM_EYE_BOLT.get(), level);
        b.setPos(from.x, from.y, from.z);
        b.entityData.set(TX, (float) (to.x - from.x));
        b.entityData.set(TY, (float) (to.y - from.y));
        b.entityData.set(TZ, (float) (to.z - from.z));
        b.entityData.set(LIFE, life);
        b.entityData.set(WIDTH, width);
        b.entityData.set(TINT, tint);
        level.addFreshEntity(b);
        return b;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TX, 0.0F);
        entityData.define(TY, 1.0F);
        entityData.define(TZ, 0.0F);
        entityData.define(LIFE, 8);
        entityData.define(WIDTH, 0.25F);
        entityData.define(TINT, COLD);
    }

    /** Where it ends, from where it starts. */
    public Vec3 span() {
        return new Vec3(entityData.get(TX), entityData.get(TY), entityData.get(TZ));
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public float width() {
        return entityData.get(WIDTH);
    }

    public int tint() {
        return entityData.get(TINT);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > life()) {
            discard();
        }
    }
}
