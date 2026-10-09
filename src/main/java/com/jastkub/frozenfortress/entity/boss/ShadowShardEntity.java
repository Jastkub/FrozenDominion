package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One of the shards that come out of the king's chest when it opens.
 *
 * <p>Deliberately not a homing orb: a barrage that all corners hard is a
 * barrage nobody can read. These lead their target very slightly for the
 * first second and then commit, so the answer is to move across the stream
 * rather than to out-turn any single shard.
 *
 * <p>The tail is drawn from a path the client keeps for itself. Nothing about
 * it is synced - it is a record of where this entity has been on screen, which
 * is exactly what a motion trail should follow, and it costs the server
 * nothing.
 */
public class ShadowShardEntity extends Entity {

    private static final float DAMAGE = 8.05F;
    /**
     * Horizontal speed, held constant; the arc comes out of the vertical.
     *
     * <p>These are thrown, not fired. The launch angle is solved so the shard
     * lands on the target rather than picked and hoped for - given a fixed
     * horizontal speed the flight time is known, and the vertical velocity that
     * covers the height difference in that time is one line of algebra
     * (see {@link #ballistic}). That is what makes a barrage of arcs converge
     * instead of scattering, and it is why the arc can be this pronounced
     * without the attack becoming a miss.
     */
    private static final double SPEED = 0.72D;
    /** Blocks per tick per tick. Same order as a thrown snowball. */
    private static final double GRAVITY = 0.032D;
    /**
     * Shortest flight allowed, in ticks.
     *
     * <p>Without this the arc disappears at close range: at a fixed horizontal
     * speed a six-block throw is over in nine ticks and gravity has time to lift
     * it barely a quarter of a block. Below the floor the shard is lobbed
     * instead - it travels slower so the flight lasts long enough to be a curve.
     * A whole volley crossing a room in under a second would also be undodgeable,
     * which is the other half of the reason.
     */
    private static final double MIN_FLIGHT = 16.0D;
    private static final int LIFETIME = 100;
    /** How many past positions the tail is drawn through. */
    public static final int TRAIL_LENGTH = 11;

    private UUID ownerUUID;
    private Vec3 flight = Vec3.ZERO;
    private final List<Vec3> trail = new ArrayList<>();

    public ShadowShardEntity(EntityType<? extends ShadowShardEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * @param from  where it leaves his chest
     * @param at    the point it should come down on
     * @param spray sideways scatter, in blocks at the target
     */
    public ShadowShardEntity(Level level, LivingEntity owner, Vec3 from, Vec3 at, double spray) {
        this(FFEntities.SHADOW_SHARD.get(), level);
        this.ownerUUID = owner.getUUID();
        setPos(from.x, from.y, from.z);
        this.flight = ballistic(from, at, spray, level.getRandom());
    }

    /**
     * The launch vector that puts a shard on {@code at}, arcing.
     *
     * <p>Horizontal speed is fixed, so the flight time in ticks is known up
     * front: {@code n = distance / SPEED}. The integrator applies gravity
     * before it moves, which makes the height after n steps
     * {@code n*v0 - g*n*(n+1)/2} - so the launch velocity that lands it exactly
     * is {@code v0 = dy/n + g*(n+1)/2}. No fudge factor: the arc is whatever
     * gravity does over that flight, which is about a block of loft up close
     * and three or four across the hall. An earlier version added a constant to
     * v0 to "make it arc more" and overshot by two and a half blocks, because a
     * constant added to a VELOCITY grows with flight time.
     */
    private static Vec3 ballistic(Vec3 from, Vec3 at, double spray,
                                  net.minecraft.util.RandomSource random) {
        Vec3 to = at.subtract(from);
        Vec3 flat = new Vec3(to.x, 0.0D, to.z);
        double dist = flat.length();
        if (dist < 0.05D) {
            return new Vec3(0.0D, SPEED, 0.0D);
        }
        Vec3 dir = flat.scale(1.0D / dist);
        // scatter across the line of fire, not along it, so the spread stays
        // readable as a fan rather than as a queue
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x)
                .scale((random.nextDouble() - 0.5D) * spray);
        Vec3 aim = flat.add(side);
        double aimDist = aim.length();
        double ticks = Math.max(MIN_FLIGHT, aimDist / SPEED);
        double vy = to.y / ticks + GRAVITY * (ticks + 1.0D) / 2.0D;
        return aim.scale(1.0D / ticks).add(0.0D, vy, 0.0D);
    }

    /** Client-side only: where it has been, oldest last. */
    public List<Vec3> trail() {
        return trail;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            trail.add(0, position());
            while (trail.size() > TRAIL_LENGTH) {
                trail.remove(trail.size() - 1);
            }
            if (tickCount % 2 == 0) {
                level().addParticle(FFParticles.SOUL_FROST.get(),
                        getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            }
            return;
        }

        LivingEntity owner = ownerUUID != null && level() instanceof ServerLevel sl
                && sl.getEntity(ownerUUID) instanceof LivingEntity le ? le : null;

        // Falls, and does not steer. A thrown thing that also homes reads as
        // neither, and the arc is the whole tell: you dodge these by watching
        // where they are going to come down.
        flight = new Vec3(flight.x, flight.y - GRAVITY, flight.z);
        setPos(getX() + flight.x, getY() + flight.y, getZ() + flight.z);

        if (tickCount > LIFETIME || !level().noCollision(this, getBoundingBox().inflate(0.02D))) {
            shatter();
            return;
        }
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(0.55D),
                e -> e.isAlive() && !(e instanceof FrostServantEntity)
                        && !(e instanceof VelkharEntity) && !(e instanceof VelkharCloneEntity
                        && !com.jastkub.frozenfortress.entity.FFAllies.spares(owner, e)))) {
            victim.hurt(damageSources().mobAttack(owner),
                    DAMAGE * com.jastkub.frozenfortress.config.FFConfig.mul(com.jastkub.frozenfortress.config.FFConfig.COMMON.shadowShard));
            victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 70, 0), owner);
            shatter();
            return;
        }
    }

    private void shatter() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY(), getZ(), 10, 0.2D, 0.2D, 0.2D, 0.22D);
            serverLevel.playSound(null, blockPosition(), FFSounds.FROST_BOLT_HIT.get(),
                    SoundSource.HOSTILE, 0.7F, 1.35F);
        }
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

}
