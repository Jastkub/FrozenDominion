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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * WICHURA - THE GALE. A wall of wind a third of the arena wide runs out from the middle toward the cloud wall, and
 * whoever it passes is thrown outward - toward the edge, the gaps between the floes and the storm under them.
 *
 * <p>It has ONE GAP, three and a half blocks of still air with its edges torn bright, at a place that is not where
 * he aimed: standing in the gap when it passes is the clean answer, being on a floe outside its sweep the other one,
 * and leaning into it (moving toward the middle as it arrives) the third. Four and a half blocks tall: nobody jumps it.
 *
 * <p>TWO MORE ANSWERS: a DODGE ROLL goes through it - while the roll's i-frames last
 * (FFRoll.rolling) the wind neither shoves nor hurts, and the roller is not marked as struck, so a roll that ends still
 * inside the band can still be caught; and a RAISED SHIELD braces against it - half the shove (the blow itself goes
 * through stormHit as ever, and the shield takes it the way it takes any blow it can).
 *
 * <p>It travels; its shove is short (about four blocks if you take all of it) and lands once a pass, not a carry.
 * Drawn by StormEyeRenderers.Gale.
 */
public class StormEyeGaleEntity extends StormEyeFxEntity {

    /** Where it starts from the middle, how fast it runs, how tall it stands, how wide its gap. */
    public static final float R0 = 3.0F, SPEED = 0.42F, HEIGHT = 4.5F, GAP_W = 3.6F;
    /** Half its width, as an angle round the middle (radians): 55 degrees either side. */
    public static final float HALF = (float) Math.toRadians(55.0D);
    /** The band around its front that is "in it". */
    static final double BAND_IN = 1.1D, BAND_OUT = 0.7D;
    static final float DAMAGE = 10.0F;
    static final double SHOVE = 0.4D;

    /** Direction of the middle of its sweep and where its gap sits from that (radians). */
    private static final EntityDataAccessor<Float> DIR =
            SynchedEntityData.defineId(StormEyeGaleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> GAP =
            SynchedEntityData.defineId(StormEyeGaleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> KING =
            SynchedEntityData.defineId(StormEyeGaleEntity.class, EntityDataSerializers.INT);

    private final Set<UUID> struck = new HashSet<>();

    public StormEyeGaleEntity(EntityType<? extends StormEyeGaleEntity> type, Level level) {
        super(type, level);
    }

    /** Out from the middle through `toward`, its gap well off that line. */
    public static StormEyeGaleEntity loose(ServerLevel level, VelkharEntity king, Vec3 centre, Vec3 toward) {
        StormEyeGaleEntity g = new StormEyeGaleEntity(FFEntities.STORM_EYE_GALE.get(), level);
        g.setPos(centre.x, centre.y, centre.z);
        double dir = Math.atan2(toward.z - centre.z, toward.x - centre.x);
        // the gap: somewhere in the outer two thirds of either half, never on the line he aimed down
        double side = level.random.nextBoolean() ? 1.0D : -1.0D;
        double gap = side * HALF * (0.35D + level.random.nextDouble() * 0.45D);
        g.entityData.set(DIR, (float) dir);
        g.entityData.set(GAP, (float) gap);
        g.entityData.set(KING, king.getId());
        level.addFreshEntity(g);
        level.playSound(null, centre.x, centre.y + 2.0D, centre.z, FFSounds.STORM_EYE_GALE.get(), SoundSource.HOSTILE,
                3.0F, 1.0F);
        return g;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(DIR, 0.0F);
        builder.define(GAP, 0.3F);
        builder.define(KING, -1);
    }

    public float dir() {
        return entityData.get(DIR);
    }

    public float gap() {
        return entityData.get(GAP);
    }

    /** Its front's distance from the middle at age t (fractional for the renderer). */
    public static float radius(float t) {
        return R0 + SPEED * t;
    }

    /** Ticks until it has reached the cloud wall. */
    public static int life() {
        return Mth.ceil((float) (StormEyeArena.WALL_R - 0.5D - R0) / SPEED);
    }

    /** Half the gap's width as an angle at this radius. */
    public static float gapHalf(float r) {
        return (GAP_W * 0.5F) / Math.max(1.0F, r);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (tickCount > life() + 6) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        VelkharEntity king = kingById(sl, entityData.get(KING));
        float r = radius(tickCount);
        double reach = r + 2.0D;
        AABB box = new AABB(getX() - reach, getY() - 1.5D, getZ() - reach, getX() + reach, getY() + HEIGHT + 1.0D,
                getZ() + reach);
        for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, box, StormEyeFxEntity::foe)) {
            double dx = v.getX() - getX(), dz = v.getZ() - getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < r - BAND_IN || d > r + BAND_OUT || v.getY() < getY() - 1.5D || v.getY() > getY() + HEIGHT) {
                continue;
            }
            double ang = Math.atan2(dz, dx);
            if (Math.abs(Mth.wrapDegrees(Math.toDegrees(ang - dir()))) > Math.toDegrees(HALF)) {
                continue;
            }
            if (Math.abs(Mth.wrapDegrees(Math.toDegrees(ang - (dir() + gap())))) <= Math.toDegrees(gapHalf(r))) {
                continue;                                       // standing in the gap: it goes round you
            }
            if (com.jastkub.frozenfortress.event.FFRoll.rolling(v)) {
                continue;                                       // rolled through it - and not marked, see above
            }
            Vec3 out = d > 1.0E-3D ? new Vec3(dx / d, 0.0D, dz / d) : new Vec3(Math.cos(dir()), 0.0D, Math.sin(dir()));
            double k = SHOVE * com.jastkub.frozenfortress.registry.FFEnchantments.steady(v);
            if (v.isBlocking()) {
                k *= 0.5D;                                      // braced behind a shield: half the shove
            }
            Vec3 m = v.getDeltaMovement();
            // a shove, not a carry: at most this much outward speed, however long they stay in the band
            double along = m.x * out.x + m.z * out.z;
            double add = Math.max(0.0D, k - Math.max(0.0D, along));
            v.setDeltaMovement(m.x + out.x * add, Math.max(m.y, 0.12D), m.z + out.z * add);
            v.hurtMarked = true;
            if (v instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
            }
            if (struck.add(v.getUUID())) {
                stormHit(sl, king, v, DAMAGE);
            }
        }
    }
}
