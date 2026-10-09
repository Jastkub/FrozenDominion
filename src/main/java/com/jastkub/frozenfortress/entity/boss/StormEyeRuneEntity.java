package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ZNAK GROMU - THE THUNDER RUNE. A sigil burns up through the floe under somebody - the whole floe, rim to rim - a knot
 * of cloud gathers over it, and a second and a half later the lightning comes down the middle of it.
 *
 * <p>The answer is to be on another floe when it lands: jumping on the spot is still on it (anything up to three
 * blocks over the ice is struck). The strike also starts that floe melting (StormEyeArena.strike) - never the centre,
 * never one an anchor holds - so he reshapes the ice as he fights. The ring floes move (StormEyeFloeEntity): the rune
 * rides the one it marked, so the sigil and the strike stay on that ice.
 *
 * <p>Drawn by StormEyeRenderers.Rune: the sigil (turning, brightening as the strike nears), the cloud knot fourteen
 * blocks over it, then the bolt and its forks for STRIKE_SHOW ticks.
 */
public class StormEyeRuneEntity extends StormEyeFxEntity {

    /** Ticks from the sigil to the strike: a second and a half. */
    public static final int DELAY = 30;
    /** Ticks the bolt is drawn for after it lands, and the whole life. */
    public static final int STRIKE_SHOW = 10, LIFE = DELAY + STRIKE_SHOW + 8;
    /** How high over the ice the cloud knot hangs. */
    public static final float CLOUD_H = 14.0F;
    /** What the bolt does to anybody on the floe: raw, through his damage funnel. */
    static final float DAMAGE = 40.0F;

    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(StormEyeRuneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> KING =
            SynchedEntityData.defineId(StormEyeRuneEntity.class, EntityDataSerializers.INT);
    /** The moving floe it burns on (StormEyeFloeEntity id, -1 for the centre's blocks): it rides it, both sides. */
    private static final EntityDataAccessor<Integer> FLOE =
            SynchedEntityData.defineId(StormEyeRuneEntity.class, EntityDataSerializers.INT);

    private int slot = -1;

    public StormEyeRuneEntity(EntityType<? extends StormEyeRuneEntity> type, Level level) {
        super(type, level);
    }

    /** floe = {x, y, z, radius, slot} as StormEyeArena.floeUnder gives it. */
    public static StormEyeRuneEntity mark(ServerLevel level, VelkharEntity king, double[] floe) {
        StormEyeRuneEntity r = new StormEyeRuneEntity(FFEntities.STORM_EYE_RUNE.get(), level);
        r.setPos(floe[0], floe[1], floe[2]);
        r.entityData.set(RADIUS, (float) floe[3]);
        r.entityData.set(KING, king.getId());
        r.slot = (int) floe[4];
        StormEyeFloeEntity body = king.stormEye() != null ? king.stormEye().floeEntity(r.slot) : null;
        if (body != null) {
            r.entityData.set(FLOE, body.getId());
        }
        level.addFreshEntity(r);
        level.playSound(null, floe[0], floe[1], floe[2], FFSounds.STORM_EYE_RUNE.get(), SoundSource.HOSTILE, 2.2F, 1.0F);
        return r;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(RADIUS, 3.5F);
        entityData.define(KING, -1);
        entityData.define(FLOE, -1);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    /** On a moving floe: where its top is now (the client's floe stepped at the start of this tick). */
    private void ride() {
        int id = entityData.get(FLOE);
        if (id >= 0 && level().getEntity(id) instanceof StormEyeFloeEntity f && !f.isRemoved()) {
            setPos(f.getX(), f.getY(), f.getZ());
        }
    }

    /** Riding a floe, its place is the floe's - the server's packets would drag it a tick behind the ice. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (entityData.get(FLOE) < 0) {
            super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
        }
    }

    @Override
    public void tick() {
        super.tick();
        ride();
        if (level().isClientSide) {
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (tickCount == DELAY) {
            strike(sl);
        }
        if (tickCount >= LIFE) {
            discard();
        }
    }

    private void strike(ServerLevel sl) {
        VelkharEntity king = kingById(sl, entityData.get(KING));
        float r = radius();
        sl.playSound(null, getX(), getY() + 4.0D, getZ(), FFSounds.STORM_EYE_THUNDER.get(), SoundSource.HOSTILE,
                4.5F, 0.9F + random.nextFloat() * 0.2F);
        AABB box = new AABB(getX() - r - 0.5D, getY() - 1.5D, getZ() - r - 0.5D,
                getX() + r + 0.5D, getY() + 3.2D, getZ() + r + 0.5D);
        for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, box, StormEyeFxEntity::foe)) {
            double dx = v.getX() - getX(), dz = v.getZ() - getZ();
            if (dx * dx + dz * dz > (r + 0.3D) * (r + 0.3D)) {
                continue;
            }
            stormHit(sl, king, v, DAMAGE);
            Vec3 out = new Vec3(dx, 0.0D, dz);
            out = out.lengthSqr() > 1.0E-4D ? out.normalize().scale(0.25D) : Vec3.ZERO;
            v.setDeltaMovement(v.getDeltaMovement().add(out.x, 0.32D, out.z));
            v.hurtMarked = true;
        }
        sl.sendParticles(FFParticles.SOUL_FROST.get(), getX(), getY() + 0.3D, getZ(), 50, r * 0.5D, 0.2D, r * 0.5D, 0.12D);
        if (king != null && king.stormEye() != null) {
            king.stormEye().strike(slot);
        }
    }
}
