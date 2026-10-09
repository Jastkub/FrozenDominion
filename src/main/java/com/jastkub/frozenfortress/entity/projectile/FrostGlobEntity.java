package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.entity.FrostPuddleEntity;
import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.entity.HollowGolemEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A GOUT OF THE TROUGH'S WATER:
 * the colossus's trough erupts and these go up out of it, high, one after another, and come down
 * in arcs - each with its shadow on the floor where it will land, and where it lands the water
 * freezes into the pool. Drawn as a lumpy blob trailing its drops (FrostGlobRenderer).
 */
public class FrostGlobEntity extends BallisticEntity {

    private static final EntityDataAccessor<Vector3f> TARGET =
            SynchedEntityData.defineId(FrostGlobEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> FLIGHT =
            SynchedEntityData.defineId(FrostGlobEntity.class, EntityDataSerializers.INT);
    public static final double GRAVITY = 0.08D;

    @Nullable
    private UUID ownerId;

    public FrostGlobEntity(EntityType<? extends FrostGlobEntity> type, Level level) {
        super(type, level);
    }

    public FrostGlobEntity(Level level, Entity owner, Vec3 from, Vec3 at, int flight) {
        this(FFEntities.FROST_GLOB.get(), level);
        this.ownerId = owner.getUUID();
        entityData.set(TARGET, new Vector3f((float) at.x, (float) at.y, (float) at.z));
        entityData.set(FLIGHT, Math.max(6, flight));
        launch(from, at, flight);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TARGET, new Vector3f());
        builder.define(FLIGHT, 20);
    }

    @Override
    protected double gravity() {
        return GRAVITY;
    }

    /** Where it will come down (its shadow is drawn there). */
    public Vec3 target() {
        Vector3f t = entityData.get(TARGET);
        return new Vec3(t.x, t.y, t.z);
    }

    /** 0..1 of its flight, for the shadow tightening as it comes down. */
    public float progress(float partialTick) {
        return Math.min(1.0F, (flown + partialTick) / entityData.get(FLIGHT));
    }

    /** The way it is going this frame. */
    public Vec3 heading(float partialTick) {
        return velAt(Math.max(0.0D, flown - 1.0D + partialTick));
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 from = position();
        Vec3 to = nextPos();
        if (!level().isClientSide) {
            HitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS || tickCount > 120) {
                land(hit.getType() != HitResult.Type.MISS ? hit.getLocation() : from);
                return;
            }
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().expandTowards(to.subtract(from)).inflate(0.3D),
                    e -> e.isAlive() && !(e instanceof FrostServantEntity) && !(e instanceof HollowGolemEntity))) {
                Entity o = ownerId != null && level() instanceof ServerLevel s ? s.getEntity(ownerId) : null;
                e.hurt(damageSources().indirectMagic(this, o), 4.0F);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                e.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 60, 0));
                land(e.position());
                return;
            }
        }
        advance();
    }

    /** Down: a splash, and the water freezes into its pool. */
    private void land(Vec3 at) {
        if (!(level() instanceof ServerLevel s) || isRemoved()) {
            return;
        }
        Entity owner = ownerId != null ? s.getEntity(ownerId) : null;
        Vec3 t = target();
        // it freezes where it was meant to come down, or on the floor under where it struck
        double y = Math.abs(at.y - t.y) < 1.5D ? t.y : Math.floor(at.y + 0.3D);
        s.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.8F, 0.7F);
        s.playSound(null, BlockPos.containing(at), SoundEvents.GLASS_HIT, SoundSource.HOSTILE, 1.4F, 0.5F);
        s.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.2D, at.z, 30, 0.6D, 0.2D, 0.6D, 0.2D);
        s.addFreshEntity(new FrostPuddleEntity(level(), owner, at.x, y, at.z).quick());
        discard();
    }
}
