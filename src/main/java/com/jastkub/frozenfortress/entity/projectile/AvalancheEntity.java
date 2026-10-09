package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.entity.FFAllies;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * THE AVALANCHE: what the Monstrosity's fists send along the floor - a wall of tumbling snow and ice
 * six blocks wide and as high as a man and a half, rolling straight on at its quarry. Whoever it
 * reaches it bowls over (once each); a pier, or any wall, breaks it - so a pier is shelter, and so is
 * a step aside. Drawn as geometry, blocks of snow and ice churning (AvalancheRenderer).
 *
 * <p>Its path is a formula on both sides (where it started, which way, how long ago), so it glides
 * rather than stutters between the server's corrections.
 */
public class AvalancheEntity extends Entity {

    public static final double SPEED = 0.56D;
    // BIGGER: eleven across and over five high
    public static final double HALF_WIDTH = 5.5D;
    /** How far out from its line it takes you: its snow reaches HALF_WIDTH, its weight not quite. */
    public static final double HIT_HALF_WIDTH = 4.7D;
    public static final double HEIGHT = 5.2D;
    public static final int LIFE = 50;
    /** Ticks it takes to collapse once it is stopped (the renderer sinks it). */
    public static final int COLLAPSE = 8;
    /**
     * IT GATHERS AS IT GOES: it leaves his fists at SMALL of its full size and reaches the
     * whole of it FULL_AT blocks on - width, height, its reach and the weight of its blow all by that one number
     * (size()). So close to him it is a slide you can step round and that bruises; far out it is the wall.
     */
    public static final double SMALL = 0.35D, FULL_AT = 16.0D;

    private static final EntityDataAccessor<Vector3f> FROM =
            SynchedEntityData.defineId(AvalancheEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DIR =
            SynchedEntityData.defineId(AvalancheEntity.class, EntityDataSerializers.VECTOR3);
    /** The tick it stopped at (-1: still rolling). */
    private static final EntityDataAccessor<Integer> STOPPED =
            SynchedEntityData.defineId(AvalancheEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID ownerId;
    private final Set<UUID> struck = new HashSet<>();
    /** Who it has caught and carries before it, and how long. */
    private final java.util.Map<UUID, Integer> carried = new java.util.HashMap<>();
    /** The longest it carries anyone before it spits them out. */
    private static final int CARRY_MAX = 44;

    public AvalancheEntity(EntityType<? extends AvalancheEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public AvalancheEntity(Level level, Entity owner, Vec3 from, Vec3 dir) {
        this(FFEntities.AVALANCHE.get(), level);
        ownerId = owner.getUUID();
        Vec3 d = new Vec3(dir.x, 0.0D, dir.z).normalize();
        entityData.set(FROM, new Vector3f((float) from.x, (float) from.y, (float) from.z));
        entityData.set(DIR, new Vector3f((float) d.x, 0.0F, (float) d.z));
        setPos(from);
        setYRot((float) (Math.atan2(d.z, d.x) * (180.0D / Math.PI)) - 90.0F);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(FROM, new Vector3f());
        entityData.define(DIR, new Vector3f(0.0F, 0.0F, 1.0F));
        entityData.define(STOPPED, -1);
    }

    public Vec3 dir() {
        Vector3f d = entityData.get(DIR);
        return new Vec3(d.x, d.y, d.z);
    }

    /** -1 while it rolls, else how many ticks ago it was stopped (as a float, for drawing). */
    public float stoppedFor(float partialTick) {
        int at = entityData.get(STOPPED);
        return at < 0 ? -1.0F : tickCount - at + partialTick;
    }

    /** How big it has grown (SMALL .. 1) by `ticks` - by how far it has come; once stopped, as big as it got. */
    public float size(float ticks) {
        int stop = entityData.get(STOPPED);
        float t = stop >= 0 ? Math.min(ticks, stop) : ticks;
        double come = Mth.clamp(SPEED * t / FULL_AT, 0.0D, 1.0D);
        return (float) (SMALL + (1.0D - SMALL) * come);
    }

    private Vec3 at(float ticks) {
        Vector3f f = entityData.get(FROM);
        int stop = entityData.get(STOPPED);
        float t = stop >= 0 ? Math.min(ticks, stop) : ticks;
        return new Vec3(f.x, f.y, f.z).add(dir().scale(SPEED * t));
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 p = at(tickCount);
        setPos(p);
        if (level().isClientSide) {
            return;
        }
        int stop = entityData.get(STOPPED);
        if (stop >= 0) {
            if (tickCount - stop >= COLLAPSE) {
                discard();
            }
            return;
        }
        // a wall or a pier before it: it breaks there
        Vec3 d = dir();
        float size = size(tickCount);
        double halfWidth = HALF_WIDTH * size;
        Vec3 ahead = p.add(d.scale(1.0D));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int blocked = 0;
        for (double side = -halfWidth + 0.5D; side <= halfWidth - 0.5D; side += 1.0D) {
            m.set(ahead.x - d.z * side, p.y + 1.0D, ahead.z + d.x * side);
            if (!level().getBlockState(m).getCollisionShape(level(), m).isEmpty()) {
                blocked++;
            }
        }
        if (blocked >= 2 || tickCount >= LIFE) {
            entityData.set(STOPPED, tickCount);
            // whoever it carries it drives into what stopped it - or, run out, throws down in its spill
            if (level() instanceof ServerLevel sl0) {
                Entity owner0 = ownerId != null ? sl0.getEntity(ownerId) : null;
                for (UUID id : carried.keySet()) {
                    if (sl0.getEntity(id) instanceof LivingEntity v && v.isAlive()) {
                        if (blocked >= 2) {
                            if (owner0 instanceof HollowGolemEntity golem) {
                                golem.avalancheStrike(v, size);
                            } else {
                                v.hurt(damageSources().magic(), 6.0F * size);
                            }
                        }
                        release(v, d, blocked >= 2 ? -0.4D : 0.5D);
                    }
                }
            }
            carried.clear();
            level().playSound(null, BlockPos.containing(p), SoundEvents.SNOW_BREAK, SoundSource.HOSTILE, 2.4F, 0.6F);
            level().playSound(null, BlockPos.containing(p), SoundEvents.POWDER_SNOW_BREAK, SoundSource.HOSTILE, 2.0F, 0.7F);
            return;
        }
        if (tickCount % 10 == 0) {
            level().playSound(null, BlockPos.containing(p), SoundEvents.POWDER_SNOW_STEP, SoundSource.HOSTILE, 1.6F, 0.6F);
        }
        // who it reaches: its box, turned along its way
        Entity owner = ownerId != null && level() instanceof ServerLevel sl ? sl.getEntity(ownerId) : null;
        AABB box = new AABB(p, p).inflate(halfWidth + 1.0D, 0.0D, halfWidth + 1.0D).expandTowards(0.0D, HEIGHT * size, 0.0D);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != owner && !FFAllies.ofTheKing(e) && !struck.contains(e.getUUID())
                        // (a spectator or a creative player is not in it: it neither strikes nor carries them)
                        && com.jastkub.frozenfortress.entity.boss.VelkharEntity.inTheFight(e))) {
            Vec3 to = v.position().subtract(p);
            double along = to.x * d.x + to.z * d.z;
            double across = -to.x * d.z + to.z * d.x;
            // its front a
            // little thinner and its flanks a little in from its snow; and a roll through it is a roll through it
            if (along < -0.6D || along > 0.9D || Math.abs(across) > HIT_HALF_WIDTH * size + v.getBbWidth() * 0.5D
                    || to.y > HEIGHT * size
                    || com.jastkub.frozenfortress.event.FFRoll.rolling(v)) {
                continue;
            }
            struck.add(v.getUUID());
            if (owner instanceof HollowGolemEntity golem) {
                golem.avalancheStrike(v, size);
            } else {
                v.hurt(damageSources().magic(), 6.0F * size);
            }
            carried.put(v.getUUID(), 0);                      // caught: it carries them now
            level().playSound(null, v.blockPosition(), FFSounds.GOLEM_STOMP.get(), SoundSource.HOSTILE, 1.2F, 1.4F);
        }
        // THE CARRIED: held at its front and swept along, tumbled - a blow every half second - until it lets go
        if (level() instanceof ServerLevel sl) {
            java.util.Iterator<java.util.Map.Entry<UUID, Integer>> it = carried.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<UUID, Integer> e = it.next();
                if (!(sl.getEntity(e.getKey()) instanceof LivingEntity v) || !v.isAlive()) {
                    it.remove();
                    continue;
                }
                int held = e.getValue() + 1;
                e.setValue(held);
                if (held > CARRY_MAX) {
                    release(v, d, 0.5D);
                    it.remove();
                    continue;
                }
                Vec3 to = v.position().subtract(p);
                double along = to.x * d.x + to.z * d.z;
                double pull = Mth.clamp((1.1D - along) * 0.35D, -0.3D, 0.3D);     // kept at its front
                Vec3 sweep = d.scale(SPEED + pull);
                v.setDeltaMovement(sweep.x, Math.max(0.04D, v.getDeltaMovement().y * 0.5D), sweep.z);
                v.fallDistance = 0.0F;
                v.hurtMarked = true;
                if (v instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
                }
                if (held % 10 == 0) {
                    if (owner instanceof HollowGolemEntity golem) {
                        golem.avalancheTumble(v, size);
                    } else {
                        v.hurt(damageSources().magic(), 3.0F * size);
                    }
                    level().playSound(null, v.blockPosition(), SoundEvents.SNOW_BREAK, SoundSource.HOSTILE, 1.4F, 0.7F);
                }
            }
        }
    }

    /** Let go of someone it carried: thrown on (or, against a wall, back off it) and up. */
    private void release(LivingEntity v, Vec3 d, double on) {
        v.setDeltaMovement(d.x * on, 0.45D, d.z * on);
        v.hurtMarked = true;
        if (v instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0D * 96.0D;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        // its path is its formula (tick)
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();                                  // a wave does not outlast a reload
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
