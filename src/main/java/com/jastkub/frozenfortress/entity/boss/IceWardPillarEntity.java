package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A ward the king hides behind.
 *
 * <p>While any of these stand he takes no damage at all, so the answer is
 * never to keep swinging at him - it is to look around the room and run.
 * One hit breaks each; they bob slightly so they read as held up by
 * something rather than placed on the floor.
 */
public class IceWardPillarEntity extends Entity implements GeoEntity {

    /** Long enough that ignoring them costs more than chasing them. */
    public static final int LIFETIME = 400;

    /**
     * Who this ward is pouring into.
     *
     * <p>Synched because the beam is drawn client-side and the client has to
     * know where the far end of it is. It could have looked the nearest boss
     * up instead, but a ward that guesses its own owner would draw a line to
     * the wrong one the moment there are two of him on the floor - which the
     * clone summon makes a real case rather than a hypothetical.
     */
    private static final EntityDataAccessor<Integer> FEEDS =
            SynchedEntityData.defineId(IceWardPillarEntity.class, EntityDataSerializers.INT);

    private double baseY;
    /**
     * Radians per tick the ring turns: 0.015, a lap in about twenty seconds (0.035 until 06.10.2026).
     */
    private static final double ORBIT_SPEED = 0.015D;
    private double orbitRadius;
    private double orbitAngle;
    /**
     * THE RING, SYNCED. Both sides used to
     * turn their own copy - the client off its own tick count, from its own first sight of the ward - and every
     * other tick the server's position packet snapped it to the server's copy: two rings a few degrees apart,
     * swapped every tenth of a second. Now the server fixes the ring once (radius, the angle it started from and
     * the game time it started at) and both sides read the same clock; the client takes no position from the
     * server at all (lerpTo).
     */
    private static final EntityDataAccessor<Float> RING_R =
            SynchedEntityData.defineId(IceWardPillarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RING_A0 =
            SynchedEntityData.defineId(IceWardPillarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> RING_T0 =
            SynchedEntityData.defineId(IceWardPillarEntity.class, EntityDataSerializers.INT);
    /** How often each ward spits a shard of ice at the king's quarry. */
    private static final int SHOOT_EVERY = 45;

    public IceWardPillarEntity(EntityType<? extends IceWardPillarEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public IceWardPillarEntity(Level level, double x, double y, double z) {
        this(FFEntities.ICE_WARD_PILLAR.get(), level);
        setPos(x, y, z);
        this.baseY = y;
    }

    @Override
    public void tick() {
        super.tick();
        if (baseY == 0.0D) {
            baseY = getY();
        }

        // ================================================================
        // THEY ORBIT HIM.
        //
        // They used to be driven into the floor where he cast them and stay
        // there for their whole life, which had two problems and the second
        // one is the real one. The small problem is that a static ring is
        // scenery. The big one is that he walks away from it: he is a duellist
        // who crosses the room constantly, so within a few seconds the wards
        // keeping him alive were somewhere behind him with no visible
        // relationship to the man they were feeding, and the beams read as
        // decoration rather than as plumbing.
        //
        // Circling the owner fixes both at once. The ring goes with him, so
        // the link is always legible; it turns, so the gap you have to step
        // through to reach one keeps moving; and a ward that is behind him
        // now will be beside him in three seconds, which means waiting for
        // one to come to you is a real option instead of a wasted run.
        //
        // The radius and the starting angle are taken from wherever the cast
        // put it, so the ring does not snap into a new shape on its first
        // tick - it simply starts turning from where it already was.
        // ================================================================
        Entity owner = fed();
        long now = level().getGameTime();
        // the renderer lerps from the old position: give it one, every tick
        this.xo = getX();
        this.yo = getY();
        this.zo = getZ();
        this.xOld = getX();
        this.yOld = getY();
        this.zOld = getZ();
        double bob = Math.sin(now * 0.08D) * 0.35D;
        if (owner != null && owner.isAlive()) {
            if (!level().isClientSide && entityData.get(RING_R) <= 0.0F) {
                double dx = getX() - owner.getX();
                double dz = getZ() - owner.getZ();
                orbitRadius = Math.max(2.5D, Math.hypot(dx, dz));
                orbitAngle = Math.atan2(dz, dx);
                entityData.set(RING_R, (float) orbitRadius);
                entityData.set(RING_A0, (float) orbitAngle);
                entityData.set(RING_T0, (int) now);
            }
            float r = entityData.get(RING_R);
            if (r > 0.0F) {
                double ang = entityData.get(RING_A0) + (now - entityData.get(RING_T0)) * ORBIT_SPEED;
                orbitAngle = ang;
                double x = owner.getX() + Math.cos(ang) * r;
                double z = owner.getZ() + Math.sin(ang) * r;
                // Height is taken from HIM as well, so a ring cast on the floor
                // does not stay at floor level when he leaves the ground in the
                // third phase and drag the beams down through the dais with it.
                setPos(x, owner.getY() + bob, z);
            }
        } else {
            setPos(getX(), baseY + bob, getZ());
        }

        if (level().isClientSide) {
            if (tickCount % 3 == 0) {
                level().addParticle(FFParticles.SOUL_FROST.get(),
                        getX() + (random.nextDouble() - 0.5D) * 1.2D,
                        getY() + random.nextDouble() * 2.4D,
                        getZ() + (random.nextDouble() - 0.5D) * 1.2D, 0.0D, 0.02D, 0.0D);
            }
            // motes running UP the line. The ribbon says there is a link; the
            // motes say which way it is flowing, and which way it flows is the
            // whole reason the player has to go and break the thing.
            net.minecraft.world.entity.Entity target = fed();
            if (target != null && tickCount % 2 == 0) {
                double t = (tickCount % 24) / 24.0D;
                double sx = getX();
                double sy = getY() + 2.2D;
                double sz = getZ();
                level().addParticle(FFParticles.SOUL_FROST.get(),
                        sx + (target.getX() - sx) * t + (random.nextDouble() - 0.5D) * 0.35D,
                        sy + (target.getY() + target.getBbHeight() * 0.55D - sy) * t
                                + (random.nextDouble() - 0.5D) * 0.35D,
                        sz + (target.getZ() - sz) * t + (random.nextDouble() - 0.5D) * 0.35D,
                        0.0D, 0.01D, 0.0D);
            }
            return;
        }
        if (caughtAShot()) {
            return;
        }
        // THEY FIGHT, THEY DO NOT ONLY FEED. Each ward is a thing that flies
        // around him and spits a shard of ice at whatever he is fighting -
        // fired on its own offset off the entity id, so the ring picks at the
        // target in a scatter rather than volleying in unison. The bolt is
        // owned by the KING so it will not turn on him or on another ward; the
        // ward only positions it.
        if (owner instanceof com.jastkub.frozenfortress.entity.boss.VelkharEntity king
                && king.getTarget() != null && king.getTarget().isAlive()
                && tickCount % SHOOT_EVERY == Math.floorMod(getId() * 13, SHOOT_EVERY)) {
            net.minecraft.world.entity.LivingEntity quarry = king.getTarget();
            double fx = getX(), fy = getY() + 1.1D, fz = getZ();
            double dx = quarry.getX() - fx;
            double dy = quarry.getY(0.5D) - fy;
            double dz = quarry.getZ() - fz;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 1.0E-4D) {
                com.jastkub.frozenfortress.entity.projectile.FrostBoltEntity bolt =
                        new com.jastkub.frozenfortress.entity.projectile.FrostBoltEntity(
                                level(), king, dx / len, dy / len, dz / len);
                bolt.setPos(fx, fy, fz);
                level().addFreshEntity(bolt);
                if (level() instanceof ServerLevel sl) {
                    sl.sendParticles(FFParticles.ICE_SHARD.get(), fx, fy, fz,
                            8, 0.2D, 0.2D, 0.2D, 0.02D);
                }
                playSound(FFSounds.VELKHAR_CAST.get(), 0.7F, 1.5F);
            }
        }

        if (tickCount > LIFETIME) {
            shatter(false);
        }
    }

    /**
     * ANYTHING that is not the king's own may break one.
     *
     * <p>It used to be `instanceof Player` and nothing else, which quietly
     * made the wards invincible in the only fight where that matters. Drop
     * Velkhar into an arena with other bosses and they cannot touch these at
     * all - their attacks land, the ward ignores every one of them, and the
     * king stands behind an untouchable wall mending himself while the thing
     * fighting him has no legal way to interfere. The single word "Player"
     * was doing that.
     *
     * <p>Now the test is inverted: the ward breaks unless the damage came
     * from the king's own side. That covers another boss's melee, its
     * projectiles, its area attacks and plain explosions in one rule, and it
     * cannot be out-grown by whatever gets added next - a new source is
     * hostile to the ward by default rather than ignored by default.
     *
     * <p>One caveat that is not fixable from here: this is an Entity and not
     * a LivingEntity, so hostile AI will never deliberately AIM at a ward. It
     * can be caught in a sweep, run through by a projectile or clipped by a
     * blast - which is most of what a boss does - but nothing will walk over
     * and choose to hit one.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (isKingsWork(source.getEntity()) || isKingsWork(source.getDirectEntity())) {
            return false;
        }
        shatter(true);
        return true;
    }

    /**
     * A PLAYER'S ARROW BREAKS IT: the
     * game's own hit scan never sees a ward (canBeHitByProjectile, and the reason it has to stay that way is there), so
     * the ward looks for them itself - any projectile a player let go whose path this tick runs through it. The shot is
     * spent on it. Only a player's: his own bolts and his servants' pass through as they always did.
     */
    private boolean caughtAShot() {
        net.minecraft.world.phys.AABB box = getBoundingBox().inflate(0.35D);
        for (net.minecraft.world.entity.projectile.Projectile shot : level().getEntitiesOfClass(
                net.minecraft.world.entity.projectile.Projectile.class, box.inflate(6.0D),
                p -> p.isAlive() && p.getOwner() instanceof Player)) {
            net.minecraft.world.phys.Vec3 v = shot.getDeltaMovement();
            if (v.lengthSqr() < 0.04D) {
                continue;                                  // lying in the ground, or spent
            }
            net.minecraft.world.phys.Vec3 from = shot.position();
            if (box.contains(from) || box.clip(from, from.add(v)).isPresent()) {
                shot.discard();
                shatter(true);
                return true;
            }
        }
        return false;
    }

    /** The king, anything wearing his face, and anything he threw. */
    private static boolean isKingsWork(Entity who) {
        if (who == null) {
            return false;
        }
        if (who instanceof VelkharEntity || who instanceof VelkharCloneEntity
                || who instanceof com.jastkub.frozenfortress.entity.FrostServantEntity
                || who instanceof IceWardPillarEntity) {
            return true;
        }
        if (who instanceof net.minecraft.world.entity.projectile.Projectile p) {
            return isKingsWork(p.getOwner());
        }
        return false;
    }

    private void shatter(boolean struck) {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY() + 1.2D, getZ(), 50, 0.5D, 1.2D, 0.5D, 0.4D);
            serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                    getX(), getY() + 1.2D, getZ(), 20, 0.4D, 1.0D, 0.4D, 0.15D);
            serverLevel.playSound(null, blockPosition(), FFSounds.ICE_SHATTER.get(),
                    SoundSource.HOSTILE, 1.8F, struck ? 1.2F : 0.8F);
        }
        discard();
    }

    /** It has to be clickable, or it could never be broken. */
    @Override
    public boolean isPickable() {
        return true;
    }

    /**
     * VISIBLE TO A SWORD AND TO A CLICK, INVISIBLE TO A PROJECTILE.
     *
     * <p>Forge splits the two questions that vanilla's isPickable answers at
     * once: isPickable still decides whether a player can look at this and hit
     * it, and canBeHitByProjectile decides whether somebody else's arrow, bolt
     * or bomb may select it as the thing it just struck. Saying no to the
     * second costs nothing here - it is broken by hitting it - and it takes this
     * entity out of every other mod's projectile hit scan.
     *
     * <p>Which is the point. LegendaryMonsters' annihilation bomb casts
     * whatever it hits straight to LivingEntity with no instanceof, so hitting
     * anything pickable that is not alive takes the server down - a vanilla
     * ghast fireball would do it too. That cast is theirs to fix and cannot be
     * fixed from here, so instead nothing of mine is left lying in its path.
     */
    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }


    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 9216.0D;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(FEEDS, -1);
        entityData.define(RING_R, 0.0F);
        entityData.define(RING_A0, 0.0F);
        entityData.define(RING_T0, 0);
    }

    /** The client turns the ring itself, off the synced ring and the clock: the server's positions are ignored. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (entityData.get(RING_R) <= 0.0F) {
            super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
        }
    }

    public void setFeeds(int entityId) {
        entityData.set(FEEDS, entityId);
    }

    public int feeds() {
        return entityData.get(FEEDS);
    }

    /** Whoever it is pouring into, if they are still on this client. */
    public net.minecraft.world.entity.Entity fed() {
        int id = feeds();
        return id < 0 ? null : level().getEntity(id);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        baseY = tag.getDouble("BaseY");
        setFeeds(tag.getInt("Feeds"));
        orbitRadius = tag.getDouble("OrbitR");
        orbitAngle = tag.getDouble("OrbitA");
        if (orbitRadius > 0.0D) {
            // the ring goes on from where it was: its start is put back so that "now" is the saved angle
            entityData.set(RING_R, (float) orbitRadius);
            entityData.set(RING_A0, (float) orbitAngle);
            entityData.set(RING_T0, level() != null ? (int) level().getGameTime() : 0);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("BaseY", baseY);
        tag.putInt("Feeds", feeds());
        tag.putDouble("OrbitR", orbitRadius);
        tag.putDouble("OrbitA", orbitAngle);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.still");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, this::idleAnim));
    }

    private <E extends GeoEntity> PlayState idleAnim(AnimationState<E> state) {
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
