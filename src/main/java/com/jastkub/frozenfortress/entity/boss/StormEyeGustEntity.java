package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A GUST of the storm, in two shapes (StormEyeRenderers.Gust):
 * <ul>
 *   <li>RESCUE - the updraft that catches somebody falling into the vortex the first time and throws them back onto
 *       the nearest solid floe: a spinning column of wind round them that carries them up an arc and sets them down.
 *       It drives their motion for CARRY ticks, then puts them exactly on the ice.</li>
 *   <li>SQUALL - a short whirl of snow where a mirror of him steps out of the blizzard (Zamiec Lustrzana).</li>
 * </ul>
 */
public class StormEyeGustEntity extends StormEyeFxEntity {

    public static final int RESCUE = 0, SQUALL = 1;
    /** Ticks the updraft carries somebody, and the tail it fades over after. */
    public static final int CARRY = 30, TAIL = 10, SQUALL_LIFE = 30;
    /** How high the carry's arc bows over the straight line. */
    private static final double ARC_UP = 9.0D;

    private static final EntityDataAccessor<Integer> MODE =
            SynchedEntityData.defineId(StormEyeGustEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RIDER =
            SynchedEntityData.defineId(StormEyeGustEntity.class, EntityDataSerializers.INT);

    private UUID riderId;
    private Vec3 from = Vec3.ZERO, to = Vec3.ZERO;
    private StormEyeArena arena;
    /** The floe it sets them down on and the spot off its middle: the floes move, so `to` is asked for every tick. */
    private int landSlot = -1;
    private double landX, landZ;

    public StormEyeGustEntity(EntityType<? extends StormEyeGustEntity> type, Level level) {
        super(type, level);
    }

    /** Catch `rider` and set them down on the arena's floe `slot`, (ox, oz) off its middle - where it is by then. */
    public static StormEyeGustEntity rescue(ServerLevel level, ServerPlayer rider, int slot, double ox, double oz,
                                            StormEyeArena arena) {
        StormEyeGustEntity g = new StormEyeGustEntity(FFEntities.STORM_EYE_GUST.get(), level);
        g.setPos(rider.getX(), rider.getY(), rider.getZ());
        g.entityData.set(MODE, RESCUE);
        g.entityData.set(RIDER, rider.getId());
        g.riderId = rider.getUUID();
        g.from = rider.position();
        g.landSlot = slot;
        g.landX = ox;
        g.landZ = oz;
        g.to = arena.landingPoint(slot, ox, oz, CARRY);
        g.arena = arena;
        level.addFreshEntity(g);
        level.playSound(null, rider.getX(), rider.getY(), rider.getZ(), FFSounds.STORM_EYE_GUST.get(),
                SoundSource.HOSTILE, 2.4F, 1.0F);
        return g;
    }

    public static StormEyeGustEntity squall(ServerLevel level, Vec3 at) {
        StormEyeGustEntity g = new StormEyeGustEntity(FFEntities.STORM_EYE_GUST.get(), level);
        g.setPos(at.x, at.y, at.z);
        g.entityData.set(MODE, SQUALL);
        level.addFreshEntity(g);
        return g;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(MODE, SQUALL);
        entityData.define(RIDER, -1);
    }

    public int mode() {
        return entityData.get(MODE);
    }

    /** 0..1 how much of it is there (in at the start, out at the end). */
    public float strength(float partialTick) {
        float t = tickCount + partialTick;
        int life = mode() == RESCUE ? CARRY + TAIL : SQUALL_LIFE;
        return Math.min(1.0F, Math.min(t / 4.0F, (life - t) / 8.0F));
    }

    /** Where on the arc the rider should be at u (0..1). */
    private Vec3 along(double u) {
        Vec3 mid = from.add(to).scale(0.5D).add(0.0D, ARC_UP, 0.0D);
        double a = (1 - u) * (1 - u), b = 2 * (1 - u) * u, c = u * u;
        return from.scale(a).add(mid.scale(b)).add(to.scale(c));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraft.world.entity.Entity r = mode() == RESCUE ? level().getEntity(entityData.get(RIDER)) : null;
            if (r != null) {
                setPos(r.getX(), r.getY(), r.getZ());        // the column rides with them on this side too
            }
            return;
        }
        if (mode() == SQUALL) {
            if (tickCount > SQUALL_LIFE) {
                discard();
            }
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        ServerPlayer rider = riderId != null && sl.getPlayerByUUID(riderId) instanceof ServerPlayer sp ? sp : null;
        if (rider == null || !rider.isAlive() || from == Vec3.ZERO) {
            finish(rider);
            return;
        }
        if (arena != null && landSlot >= 0 && tickCount <= CARRY) {
            // the floe has gone on: the arc ends where it will be when they come down
            to = arena.landingPoint(landSlot, landX, landZ, Math.max(0, CARRY - tickCount));
        }
        if (tickCount < CARRY) {
            StormEyeArena.airborne(rider);
            StormEyeArena.shield(rider, 30);
            Vec3 want = along((tickCount + 1) / (double) CARRY);
            Vec3 go = want.subtract(rider.position());
            if (go.length() > 3.0D) {
                go = go.normalize().scale(3.0D);
            }
            rider.setDeltaMovement(go);
            rider.hurtMarked = true;
            rider.connection.send(new ClientboundSetEntityMotionPacket(rider));
            rider.fallDistance = 0.0F;
            setPos(rider.getX(), rider.getY(), rider.getZ());
        } else if (tickCount == CARRY) {
            rider.teleportTo(sl, to.x, to.y, to.z, rider.getYRot(), rider.getXRot());
            rider.setDeltaMovement(Vec3.ZERO);
            rider.hurtMarked = true;
            rider.fallDistance = 0.0F;
            setPos(to.x, to.y, to.z);
            if (arena != null) {
                arena.delivered(riderId);
            }
        } else if (tickCount > CARRY + TAIL) {
            finish(rider);
        }
    }

    private void finish(ServerPlayer rider) {
        if (arena != null && riderId != null) {
            arena.delivered(riderId);
        }
        discard();
    }
}
