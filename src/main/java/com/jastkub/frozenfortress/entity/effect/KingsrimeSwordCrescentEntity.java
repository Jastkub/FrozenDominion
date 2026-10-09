package com.jastkub.frozenfortress.entity.effect;

import com.jastkub.frozenfortress.item.KingsrimeSwordItem;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * SZRONOWE CIĘCIE (Rime Crescent) - the Kingsrime sword's tap of the use key: a crescent of frost loosed off the cut,
 * flying out nine blocks along the wielder's look and cutting through everything on its way (each once). A wall breaks
 * it. Its middle leads and its horns trail (KingsrimeSwordCrescentRenderer draws it: a blade of ice bent into a
 * crescent, a hard white edge, a wake of frost behind). At its end, or at a wall, it shatters - the arc breaking into
 * pieces flung outward - and is gone a third of a second later.
 *
 * <p>Both sides fly it the same way (its line and speed are synced data), so the client draws it smoothly between the
 * server's updates. SCALE 2 is the Krok's combo (the crescent doubled); WRATH is one of the Królewski Gniew's fan of
 * three (a deeper, royal blue).
 */
public class KingsrimeSwordCrescentEntity extends Entity {

    /** Blocks a tick, its reach (scale 1), the extra reach of the doubled one, and the ticks it takes to shatter. */
    public static final double SPEED = 1.3D, RANGE = 9.0D, RANGE_BIG = 11.0D;
    public static final int SHATTER = 6;
    /** Its shape at scale 1 (KingsrimeSwordCrescentRenderer, tools/gen_kingsrime_skills.py): the arc's radius, half
     *  its sweep in degrees, how deep its blade is. Hits reach this far either side of its line. */
    public static final float R = 2.0F, SWEEP = 70.0F, DEPTH = 0.55F;

    private static final EntityDataAccessor<Float> YAW =
            SynchedEntityData.defineId(KingsrimeSwordCrescentEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH =
            SynchedEntityData.defineId(KingsrimeSwordCrescentEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ROLL =
            SynchedEntityData.defineId(KingsrimeSwordCrescentEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SCALE =
            SynchedEntityData.defineId(KingsrimeSwordCrescentEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> WRATH =
            SynchedEntityData.defineId(KingsrimeSwordCrescentEntity.class, EntityDataSerializers.BOOLEAN);
    /** The tick it stopped flying (its reach, or a wall): the shatter runs from it. */
    private static final EntityDataAccessor<Integer> END =
            SynchedEntityData.defineId(KingsrimeSwordCrescentEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID ownerId;
    private float damage = KingsrimeSwordItem.CRESCENT_DMG;
    private final Set<UUID> struck = new HashSet<>();

    public KingsrimeSwordCrescentEntity(EntityType<? extends KingsrimeSwordCrescentEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;                                       // seven blocks of arc from a half-block box
    }

    /** Loosed by `p` along yaw/pitch, at chest height a step before him. */
    public static KingsrimeSwordCrescentEntity fire(ServerLevel level, Player p, float yaw, float pitch, float roll,
                                                    float scale, float damage, boolean wrath) {
        KingsrimeSwordCrescentEntity c = new KingsrimeSwordCrescentEntity(FFEntities.KINGSRIME_CRESCENT.get(), level);
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 at = new Vec3(p.getX(), p.getY() + p.getBbHeight() * 0.62D, p.getZ()).add(dir.scale(0.7D));
        c.ownerId = p.getUUID();
        c.damage = damage;
        c.moveTo(at.x, at.y, at.z, yaw, pitch);
        c.entityData.set(YAW, yaw);
        c.entityData.set(PITCH, pitch);
        c.entityData.set(ROLL, roll);
        c.entityData.set(SCALE, scale);
        c.entityData.set(WRATH, wrath);
        c.entityData.set(END, Mth.ceil((scale > 1.5F ? RANGE_BIG : RANGE) / SPEED));
        level.addFreshEntity(c);
        return c;
    }

    public float flightYaw() {
        return entityData.get(YAW);
    }

    public float flightPitch() {
        return entityData.get(PITCH);
    }

    public float roll() {
        return entityData.get(ROLL);
    }

    public float scale() {
        return entityData.get(SCALE);
    }

    public boolean wrath() {
        return entityData.get(WRATH);
    }

    public int endTick() {
        return entityData.get(END);
    }

    public Vec3 dir() {
        return Vec3.directionFromRotation(flightPitch(), flightYaw());
    }

    @Override
    public void tick() {
        super.tick();
        int t = tickCount;
        if (t <= endTick()) {
            Vec3 step = dir().scale(SPEED);
            Vec3 from = position();
            Vec3 to = from.add(step);
            if (!level().isClientSide) {
                HitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                        this));
                if (hit.getType() != HitResult.Type.MISS) {
                    to = hit.getLocation().subtract(step.normalize().scale(0.2D));
                    entityData.set(END, t);                          // a wall: it breaks here
                    playSound(FFSounds.ICE_SHATTER.get(), 0.9F, 1.3F);
                }
                setPos(to.x, to.y, to.z);
                cutAlong(from.distanceTo(to) + 0.4D);
            } else {
                setPos(to.x, to.y, to.z);
            }
        }
        if (!level().isClientSide && t == endTick() + 1) {
            ((ServerLevel) level()).sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY(), getZ(),
                    10, 0.8D * scale(), 0.2D, 0.8D * scale(), 0.08D);
        }
        if (!level().isClientSide && t > endTick() + SHATTER) {
            discard();
        }
    }

    /**
     * Everyone the arc has swept since the last tick: in its frame (forward along its line, across, up), the arc at
     * across-offset x stands at forward R(cos a - 1) - and whoever is within its depth of that, behind it by the tick's
     * travel, at a height it passes, is cut.
     */
    private void cutAlong(double travelled) {
        Player owner = ownerId != null ? level().getPlayerByUUID(ownerId) : null;
        if (owner == null) {
            return;
        }
        float s = scale();
        double halfSpan = R * s * Math.sin(Math.toRadians(SWEEP));
        Vec3 fwd = dir();
        Vec3 side = new Vec3(-fwd.z, 0.0D, fwd.x);
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();
        Vec3 up = side.cross(fwd).normalize();
        AABB box = getBoundingBox().inflate(halfSpan + 1.5D, 1.5D + 0.5D * s, halfSpan + 1.5D)
                .expandTowards(fwd.scale(-travelled));
        boolean sounded = false;
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, box, e -> KingsrimeSwordItem.foe(owner, e))) {
            if (struck.contains(v.getUUID())) {
                continue;
            }
            Vec3 rel = v.position().add(0.0D, v.getBbHeight() * 0.5D, 0.0D).subtract(position());
            double x = rel.dot(side), y = rel.dot(up), z = rel.dot(fwd);
            double w = v.getBbWidth() * 0.5D;
            if (Math.abs(x) > halfSpan + w + 0.2D) {
                continue;
            }
            double ax = Mth.clamp(Math.abs(x) / (R * s), 0.0D, 1.0D);
            double arcZ = R * s * (Math.sqrt(1.0D - ax * ax) - 1.0D);
            if (z > arcZ + DEPTH * s + w || z < arcZ - travelled - DEPTH * s - w) {
                continue;
            }
            if (Math.abs(y) > v.getBbHeight() * 0.5D + 0.45D * s + 0.35D) {
                continue;
            }
            struck.add(v.getUUID());
            boolean wrath = wrath();
            KingsrimeSwordItem.cut(owner, v, damage, 100, wrath ? 1 : 0);
            Vec3 push = new Vec3(fwd.x, 0.0D, fwd.z);
            if (push.lengthSqr() > 1.0E-4D) {
                push = push.normalize().scale(0.35D * com.jastkub.frozenfortress.registry.FFEnchantments.steady(v));
                v.setDeltaMovement(v.getDeltaMovement().add(push.x, 0.12D, push.z));
                v.hurtMarked = true;
            }
            ServerLevel sl = (ServerLevel) level();
            sl.sendParticles(FFParticles.ICE_SHARD.get(), v.getX(), v.getY(0.55D), v.getZ(), 10, 0.25D, 0.35D, 0.25D, 0.12D);
            if (!sounded) {
                sounded = true;
                sl.playSound(null, v.getX(), v.getY(0.5D), v.getZ(), FFSounds.KINGSRIME_CRESCENT_HIT.get(),
                        SoundSource.PLAYERS, 1.0F, 0.92F + random.nextFloat() * 0.16F);
            }
        }
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(YAW, 0.0F);
        entityData.define(PITCH, 0.0F);
        entityData.define(ROLL, 0.0F);
        entityData.define(SCALE, 1.0F);
        entityData.define(WRATH, false);
        entityData.define(END, 7);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0D * 96.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
