package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.entity.FrostWaveEntity;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A GLACIER JAVELIN: an icicle as
 * long as a house, torn off the colossus's back and hurled in an arc. Where it
 * comes down it hurts and throws a short wave of frost - and then it STANDS, a
 * pillar of ice driven into the floor, for eight seconds.
 *
 * <p>The pillar is the fight's lesson. It is a wall (you cannot walk through
 * it; you can hide behind it), it takes three blows to break - and if the
 * colossus's charge runs into one, the charge breaks on it and the colossus
 * goes down on one knee with its heart open (HollowGolemEntity.STAGGER).
 *
 * <p>Its position is its TIP: in flight the tip leads, planted the tip is in
 * the floor and the rest of it leans back up along the line it came down.
 */
public class IceJavelinEntity extends BallisticEntity {

    private static final EntityDataAccessor<Boolean> PLANTED =
            SynchedEntityData.defineId(IceJavelinEntity.class, EntityDataSerializers.BOOLEAN);
    /** Where it went in (the client is told, so it stands where the server planted it). */
    private static final EntityDataAccessor<org.joml.Vector3f> PLANT_AT =
            SynchedEntityData.defineId(IceJavelinEntity.class, EntityDataSerializers.VECTOR3);
    /** How long it stands once planted. */
    public static final int STAND = 160;
    private static final int HITS = 3;
    private static final double GRAVITY = 0.045D;

    @Nullable
    private UUID ownerId;
    private int standing;
    private int hits;
    private final java.util.Set<UUID> struck = new java.util.HashSet<>();

    public IceJavelinEntity(EntityType<? extends IceJavelinEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    /** Thrown from `from` to come down on `at` after `flight` ticks. */
    public IceJavelinEntity(Level level, Entity owner, Vec3 from, Vec3 at, int flight) {
        this(FFEntities.ICE_JAVELIN.get(), level);
        this.ownerId = owner.getUUID();
        launch(from, at, flight);
        aim(velAt(0));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PLANTED, false);
        builder.define(PLANT_AT, new org.joml.Vector3f());
    }

    @Override
    protected double gravity() {
        return GRAVITY;
    }

    @Override
    protected boolean inFlight() {
        return !isPlanted();
    }

    public boolean isPlanted() {
        return entityData.get(PLANTED);
    }

    /** Points it along a direction (its tip leads); last tick's aim is kept to draw between them. */
    private void aim(Vec3 v) {
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        yRotO = getYRot();
        xRotO = getXRot();
        setYRot((float) (Math.atan2(v.z, v.x) * (180.0D / Math.PI)) - 90.0F);
        setXRot((float) -(Math.atan2(v.y, h) * (180.0D / Math.PI)));
    }

    @Nullable
    private Entity owner() {
        return ownerId != null && level() instanceof ServerLevel s ? s.getEntity(ownerId) : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (isPlanted()) {
            setDeltaMovement(Vec3.ZERO);
            yRotO = getYRot();
            xRotO = getXRot();
            if (level().isClientSide) {
                org.joml.Vector3f p = entityData.get(PLANT_AT);
                if (position().distanceToSqr(p.x, p.y, p.z) > 1.0E-4D) {
                    setPos(p.x, p.y, p.z);
                    xo = p.x;
                    yo = p.y;
                    zo = p.z;
                }
            }
            if (!level().isClientSide && ++standing > STAND) {
                shatter(false);
            }
            return;
        }
        Vec3 next = nextPos();
        Vec3 v = next.subtract(position());
        if (!level().isClientSide) {
            // on the way: it takes whoever its flight CROSSES this tick - before it can plant
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().expandTowards(v).inflate(0.5D),
                    e -> e.isAlive() && !(e instanceof FrostServantEntity) && !(e instanceof HollowGolemEntity)
                            && !(e instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator()))
                            && struck.add(e.getUUID()))) {
                skewer(e);
            }
            // down on the floor (or into a wall): it plants where it is
            BlockPos below = BlockPos.containing(next.x, next.y - 0.05D, next.z);
            boolean ground = !level().getBlockState(below).getCollisionShape(level(), below).isEmpty();
            boolean wall = !level().noCollision(this, getBoundingBox().move(v.x, 0.0D, v.z).deflate(0.3D));
            if (ground || wall || tickCount > 80) {
                // its tip (the entity's position) goes in a little under the floor
                double y = ground ? below.getY() + 1.0D - 0.6D : next.y;
                Vec3 at = new Vec3(next.x, y, next.z);
                // and whoever it comes down on, or right beside, is skewered by it
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(at, at).inflate(1.3D, 2.2D, 1.3D),
                        e -> e.isAlive() && !(e instanceof FrostServantEntity) && !(e instanceof HollowGolemEntity)
                                && !(e instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator()))
                                && struck.add(e.getUUID()))) {
                    skewer(e);
                }
                plant(at);
                return;
            }
        }
        if (advance()) {
            aim(velAt(flown));
        }
        if (level().isClientSide && tickCount % 2 == 0) {
            level().addParticle(FFParticles.SOUL_FROST.get(), getX(), getY() + 0.5D, getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    /** A hit: ten, and a shove along its flight. */
    private void skewer(LivingEntity e) {
        Entity o = owner();
        if (e.hurt(damageSources().mobProjectile(this, o instanceof LivingEntity l ? l : null), 10.0F)) {
            Vec3 along = getDeltaMovement();
            Vec3 flat = new Vec3(along.x, 0.0D, along.z);
            if (flat.lengthSqr() > 1.0E-4D) {
                flat = flat.normalize().scale(0.6D * com.jastkub.frozenfortress.registry.FFEnchantments.steady(e));
                e.setDeltaMovement(e.getDeltaMovement().add(flat.x, 0.25D, flat.z));
                e.hurtMarked = true;
            }
        }
    }

    private void plant(Vec3 at) {
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        entityData.set(PLANT_AT, new org.joml.Vector3f((float) at.x, (float) at.y, (float) at.z));
        entityData.set(PLANTED, true);
        standing = 0;
        if (level() instanceof ServerLevel s) {
            s.playSound(null, BlockPos.containing(at), FFSounds.GOLEM_SLAM.get(), SoundSource.HOSTILE, 3.0F, 1.3F);
            s.playSound(null, BlockPos.containing(at), FFSounds.ICE_IMPACT.get(), SoundSource.HOSTILE, 2.6F, 0.8F);
            s.sendParticles(FFParticles.ICE_SHARD.get(), at.x, at.y + 0.5D, at.z, 40, 0.8D, 0.4D, 0.8D, 0.25D);
            Entity o = owner();
            // where it lands, a short wave of frost (a crest, not dust)
            s.addFreshEntity(new FrostWaveEntity(level(), o, at.x, Math.floor(at.y + 0.5D), at.z, 3.6F, 10, 6.0F).light());
        }
    }

    /** Broken (or melted away): to splinters. */
    public void shatter(boolean loud) {
        if (level() instanceof ServerLevel s) {
            s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 1.6D, getZ(), loud ? 80 : 40,
                    0.6D, 1.4D, 0.6D, loud ? 0.3D : 0.1D);
            s.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, loud ? 2.4F : 1.2F, 0.6F);
        }
        discard();
    }

    /** A wall while it stands. */
    @Override
    public boolean canBeCollidedWith() {
        return isPlanted();
    }

    @Override
    public boolean isPickable() {
        return isPlanted();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !isPlanted() || isRemoved()) {
            return false;
        }
        Entity by = source.getEntity();
        if (!(by instanceof Player) && !(by instanceof LivingEntity) || by instanceof FrostServantEntity
                || by instanceof HollowGolemEntity) {
            return false;
        }
        hits++;
        playSound(SoundEvents.GLASS_HIT, 1.4F, 0.6F + hits * 0.15F);
        if (hits >= HITS) {
            shatter(true);
        }
        return true;
    }

}
