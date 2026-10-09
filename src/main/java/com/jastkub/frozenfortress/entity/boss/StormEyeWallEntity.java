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
 * THE WALL OF ELECTRIFIED CLOUD all round the Eye of the Storm (StormEyeRenderers.Wall draws it: three billowing
 * shells of storm cloud turning against each other, veins of lightning crawling through them, the top curling in).
 *
 * <p>Purely its body: what it DOES - the shock, the throw back in, the lid - is StormEyeArena's tick, which knows who
 * is up here. Radius and reach are the arena's constants, so the drawing and the rule are one number.
 */
public class StormEyeWallEntity extends StormEyeFxEntity {

    private static final EntityDataAccessor<Float> FADE =
            SynchedEntityData.defineId(StormEyeWallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> KING =
            SynchedEntityData.defineId(StormEyeWallEntity.class, EntityDataSerializers.INT);

    public StormEyeWallEntity(EntityType<? extends StormEyeWallEntity> type, Level level) {
        super(type, level);
    }

    public static StormEyeWallEntity ring(ServerLevel level, Vec3 centre, VelkharEntity king) {
        StormEyeWallEntity w = new StormEyeWallEntity(FFEntities.STORM_EYE_WALL.get(), level);
        w.setPos(centre.x, centre.y, centre.z);
        w.entityData.set(KING, king.getId());
        level.addFreshEntity(w);
        return w;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(FADE, 0.0F);
        builder.define(KING, -1);
    }

    public float fade() {
        return entityData.get(FADE);
    }

    public void setFade(float f) {
        if (Math.abs(entityData.get(FADE) - f) > 0.004F) {
            entityData.set(FADE, f);
        }
    }

    /** 0 -> 1 over its first two seconds: the cloud closes round them as they arrive. */
    public float formed(float partialTick) {
        return Math.min(1.0F, (tickCount + partialTick) / 40.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 40 == 0 && tickCount > 200
                && kingById(level(), entityData.get(KING)) == null) {
            discard();
        }
    }
}
