package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.entity.FrostPuddleEntity;
import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.entity.FrostWaveEntity;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * THE MONSTROSITY'S BOMB, its own: a crystal of ice wrapped in its own light,
 * thrown in an arc from the orb it gathered before its palm, faster than the old ones.
 *
 * <p>Where it lands it BURSTS - a wave of frost runs out round it (a crest of ice, drawn) and four
 * shards of its shell fly off in arcs (IceFragmentEntity), each leaving a frozen pool where it
 * comes down. Light on particles: what is seen is geometry.
 */
public class IceBombEntity extends BallisticEntity {

    private static final EntityDataAccessor<Float> SPIN =
            SynchedEntityData.defineId(IceBombEntity.class, EntityDataSerializers.FLOAT);
    public static final double GRAVITY = 0.05D;
    private static final float BLAST = 3.2F;
    private static final float DAMAGE = 9.0F;

    @Nullable
    private UUID ownerId;

    public IceBombEntity(EntityType<? extends IceBombEntity> type, Level level) {
        super(type, level);
    }

    /** Thrown from `from` to come down on `at` after `flight` ticks (the same arc on every client). (A frost maw's bomb
     *  is its own since 07.10.2026: MawBombEntity.) */
    public IceBombEntity(Level level, Entity owner, Vec3 from, Vec3 at, int flight) {
        this(FFEntities.ICE_BOMB.get(), level);
        this.ownerId = owner.getUUID();
        launch(from, at, flight);
        entityData.set(SPIN, level.random.nextFloat() * 360.0F);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SPIN, 0.0F);
    }

    @Override
    protected double gravity() {
        return GRAVITY;
    }

    /** Where it started turning (the renderer spins it from there). */
    public float spin() {
        return entityData.get(SPIN);
    }

    @Nullable
    private Entity owner() {
        return ownerId != null && level() instanceof ServerLevel s ? s.getEntity(ownerId) : null;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 from = position();
        Vec3 to = nextPos();
        Vec3 v = to.subtract(from);
        if (!level().isClientSide) {
            HitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
                burst();
                return;
            }
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().expandTowards(v).inflate(0.4D),
                    e -> e.isAlive() && !(e instanceof FrostServantEntity) && !(e instanceof HollowGolemEntity))) {
                burst();
                return;
            }
            if (tickCount > 100) {
                burst();
                return;
            }
        }
        advance();
    }

    protected void burst() {
        if (!(level() instanceof ServerLevel s) || isRemoved()) {
            return;
        }
        Vec3 at = position();
        Entity owner = owner();
        s.playSound(null, BlockPos.containing(at), FFSounds.ICE_SHATTER.get(), SoundSource.HOSTILE, 3.0F, 0.7F);
        s.playSound(null, BlockPos.containing(at), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.4F, 0.6F);
        s.playSound(null, BlockPos.containing(at), FFSounds.SHOCKWAVE.get(), SoundSource.HOSTILE, 1.8F, 1.3F);
        s.sendParticles(FFParticles.ICE_SHARD.get(), at.x, at.y + 0.3D, at.z, 16, 0.5D, 0.3D, 0.5D, 0.2D);
        for (LivingEntity v : s.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(BLAST),
                e -> e.isAlive() && !(e instanceof FrostServantEntity) && !(e instanceof HollowGolemEntity))) {
            if (v.distanceToSqr(at) <= BLAST * BLAST) {
                v.hurt(damageSources().indirectMagic(this, owner), DAMAGE);
                v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 0));
            }
        }
        double floor = Math.floor(at.y + 0.4D);
        // the burst, as a crest of frost
        s.addFreshEntity(new FrostWaveEntity(level(), owner, at.x, floor, at.z, 3.6F, 10, 4.0F).light());
        // and the shell, flying apart: each shard leaves a pool where it comes down
        double turn = random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < 4; i++) {
            double a = turn + Math.PI * 2.0D * i / 4.0D;
            double out = 0.22D + random.nextDouble() * 0.12D;
            s.addFreshEntity(new IceFragmentEntity(level(), owner, at.add(0.0D, 0.4D, 0.0D),
                    new Vec3(Math.cos(a) * out, 0.48D + random.nextDouble() * 0.18D, Math.sin(a) * out)));
        }
        discard();
    }

    /**
     * A shard of a burst bomb's shell, in an arc; where it lands, a frozen pool. Or (shrapnel) a
     * splinter of a statue's shell as it breaks: it flies off, and whoever it
     * hits takes it and the frostbite with it - no pool.
     */
    public static class IceFragmentEntity extends Entity implements GeoEntity {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        @Nullable
        private UUID ownerId;
        /** Shrapnel: what it does to whoever it hits (0: a bomb's shard, which leaves a pool). */
        private float shrapnel;

        public IceFragmentEntity(EntityType<? extends IceFragmentEntity> type, Level level) {
            super(type, level);
            this.noPhysics = true;
            this.noCulling = true;
        }

        public IceFragmentEntity(Level level, @Nullable Entity owner, Vec3 from, Vec3 velocity) {
            this(FFEntities.ICE_FRAGMENT.get(), level);
            this.ownerId = owner == null ? null : owner.getUUID();
            setPos(from.x, from.y, from.z);
            setDeltaMovement(velocity);
        }

        @Override
        protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        }

        /** A statue's splinter: hurts whoever it hits (and frostbites them), leaves no pool. */
        public IceFragmentEntity shrapnel(float damage) {
            this.shrapnel = damage;
            return this;
        }

        /** A frost maw's bomb's (MawBombEntity): it only flies and rings - no pool where it lands. */
        public IceFragmentEntity dud() {
            this.dud = true;
            return this;
        }

        private boolean dud;

        @Override
        public void tick() {
            super.tick();
            Vec3 v = getDeltaMovement();
            Vec3 to = position().add(v);
            if (!level().isClientSide && shrapnel > 0.0F) {
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class,
                        getBoundingBox().expandTowards(v).inflate(0.25D),
                        e -> e.isAlive() && !(e instanceof FrostServantEntity) && !(e instanceof HollowGolemEntity))) {
                    if (shrapnel >= 0.5F) {                    // (a corpse's splinters do not sting)
                        e.hurt(damageSources().generic(), shrapnel);
                        e.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 0));
                    }
                    playSound(SoundEvents.GLASS_HIT, 1.0F, 1.4F);
                    discard();
                    return;
                }
            }
            if (!level().isClientSide) {
                HitResult hit = level().clip(new ClipContext(position(), to, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, this));
                if (hit.getType() != HitResult.Type.MISS && (shrapnel > 0.0F || dud)) {
                    playSound(SoundEvents.GLASS_HIT, 0.6F, 1.6F);
                    discard();
                    return;
                }
                if (dud && tickCount > 60) {
                    discard();
                    return;
                }
                if (hit.getType() != HitResult.Type.MISS || tickCount > 60) {
                    Vec3 at = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : position();
                    if (level() instanceof ServerLevel s) {
                        s.playSound(null, BlockPos.containing(at), SoundEvents.GLASS_HIT, SoundSource.HOSTILE, 1.0F, 1.2F);
                        Entity owner = ownerId != null ? s.getEntity(ownerId) : null;
                        s.addFreshEntity(new FrostPuddleEntity(level(), owner, at.x, Math.floor(at.y + 0.3D), at.z)
                                .radius(1.6F).quick());
                    }
                    discard();
                    return;
                }
            }
            setPos(to.x, to.y, to.z);
            setDeltaMovement(v.x * 0.98D, v.y - 0.06D, v.z * 0.98D);
        }

        @Override
        public boolean isPickable() {
            return false;
        }

        @Override
        protected void readAdditionalSaveData(CompoundTag tag) {
            discard();
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag tag) {
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }
    }
}
