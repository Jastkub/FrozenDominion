package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * THE VORTEX - two shapes of one storm (StormEyeRenderers.Vortex draws both).
 *
 * <ul>
 *   <li>ARENA: the great funnel turning under the floes - its rim just below the ice, wide as the arena, its eye
 *       forty-six blocks down, lit by the lightning inside it. Rises while the eye closes (RISE), fades while he dies
 *       (FADE). Falling in is death - that is StormEyeArena, not this.</li>
 *   <li>LIFT: the column in the throne room under the hole in the dome, reaching up through it to the ice (HEIGHT).
 *       While the fight is up in the sky it stays, quiet (CALM): a fallen player who stands in it is carried back up
 *       it, every block of the way.</li>
 *   <li>FOLLOW: the vortex he calls up round himself when he seizes them (07.10.2026): it goes where he goes - the
 *       renderer draws it on his own interpolated position, so it never lags a tick behind him - and trails TRAIL
 *       blocks under him, the length of the column the others are carried up behind him.</li>
 * </ul>
 */
public class StormEyeVortexEntity extends StormEyeFxEntity {

    public static final int ARENA = 0, LIFT = 1, FOLLOW = 2;

    private static final EntityDataAccessor<Integer> MODE =
            SynchedEntityData.defineId(StormEyeVortexEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RISE =
            SynchedEntityData.defineId(StormEyeVortexEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FADE =
            SynchedEntityData.defineId(StormEyeVortexEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> CALM =
            SynchedEntityData.defineId(StormEyeVortexEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> KING =
            SynchedEntityData.defineId(StormEyeVortexEntity.class, EntityDataSerializers.INT);
    /** LIFT: how tall it stands. FOLLOW: how far it trails under him. */
    private static final EntityDataAccessor<Float> HEIGHT =
            SynchedEntityData.defineId(StormEyeVortexEntity.class, EntityDataSerializers.FLOAT);
    /** Ticks the column round him takes to come up. */
    public static final int FOLLOW_OPEN = 14;

    /** How tall the hall's column stands, and how wide. */
    public static final float LIFT_HEIGHT = 26.0F, LIFT_R = 3.6F;
    /** Ticks the column takes to tear open. */
    public static final int LIFT_OPEN = 40;

    public StormEyeVortexEntity(EntityType<? extends StormEyeVortexEntity> type, Level level) {
        super(type, level);
    }

    public static StormEyeVortexEntity arena(ServerLevel level, Vec3 centre, VelkharEntity king) {
        StormEyeVortexEntity v = new StormEyeVortexEntity(FFEntities.STORM_EYE_VORTEX.get(), level);
        v.setPos(centre.x, centre.y, centre.z);
        v.entityData.set(MODE, ARENA);
        v.entityData.set(KING, king.getId());
        level.addFreshEntity(v);
        return v;
    }

    public static StormEyeVortexEntity lift(ServerLevel level, Vec3 floor, VelkharEntity king) {
        StormEyeVortexEntity v = new StormEyeVortexEntity(FFEntities.STORM_EYE_VORTEX.get(), level);
        v.setPos(floor.x, floor.y, floor.z);
        v.entityData.set(MODE, LIFT);
        v.entityData.set(KING, king.getId());
        level.addFreshEntity(v);
        return v;
    }

    /** The vortex round him: it goes where he goes. */
    public static StormEyeVortexEntity follow(ServerLevel level, VelkharEntity king) {
        StormEyeVortexEntity v = new StormEyeVortexEntity(FFEntities.STORM_EYE_VORTEX.get(), level);
        v.setPos(king.getX(), king.getY(), king.getZ());
        v.entityData.set(MODE, FOLLOW);
        v.entityData.set(KING, king.getId());
        v.entityData.set(HEIGHT, 6.0F);
        level.addFreshEntity(v);
        return v;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(HEIGHT, LIFT_HEIGHT);
        entityData.define(MODE, ARENA);
        entityData.define(RISE, 0.0F);
        entityData.define(FADE, 0.0F);
        entityData.define(CALM, false);
        entityData.define(KING, -1);
    }

    public int mode() {
        return entityData.get(MODE);
    }

    public float rise() {
        return entityData.get(RISE);
    }

    public void setRise(float r) {
        if (Math.abs(entityData.get(RISE) - r) > 0.004F) {
            entityData.set(RISE, r);
        }
    }

    public float fade() {
        return entityData.get(FADE);
    }

    public void setFade(float f) {
        if (Math.abs(entityData.get(FADE) - f) > 0.004F) {
            entityData.set(FADE, f);
        }
    }

    public boolean calm() {
        return entityData.get(CALM);
    }

    public void setCalm(boolean calm) {
        entityData.set(CALM, calm);
    }

    /** 0 -> 1 as the column tears open, off its own age on both sides. */
    public float opened(float partialTick) {
        return Math.min(1.0F, (tickCount + partialTick) / (mode() == FOLLOW ? FOLLOW_OPEN : LIFT_OPEN));
    }

    public float height() {
        return entityData.get(HEIGHT);
    }

    public void setHeight(float h) {
        if (Math.abs(entityData.get(HEIGHT) - h) > 0.25F) {
            entityData.set(HEIGHT, h);
        }
    }

    /** The hall's column reaches the ice, a hundred and more blocks up: it is drawn from anywhere in the fight. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 320.0D * 320.0D;
    }

    /** The king's entity id (FOLLOW draws on him). */
    public int kingId() {
        return entityData.get(KING);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (mode() == FOLLOW) {
            VelkharEntity king = kingById(level(), entityData.get(KING));
            if (king != null) {
                setPos(king.getX(), king.getY(), king.getZ());     // kept by him, for tracking; it is DRAWN on him
            }
            if (fade() >= 0.999F) {
                discard();
                return;
            }
        }
        // THE LIFT ROARS while it is tearing the hall open, and keeps a low voice after - the arena's own wind is
        // played by StormEyeArena at each ear
        if (mode() == LIFT && tickCount % (calm() ? 120 : 50) == 0) {
            level().playSound(null, getX(), getY() + 3.0D, getZ(), FFSounds.STORM_EYE_WIND.get(), SoundSource.HOSTILE,
                    calm() ? 1.2F : 2.6F, calm() ? 0.7F : 0.9F);
        }
        // a stray one with no king (a world loaded without him) does not hang about
        if (tickCount % 40 == 0 && kingById(level(), entityData.get(KING)) == null && tickCount > 200) {
            discard();
        }
    }
}
