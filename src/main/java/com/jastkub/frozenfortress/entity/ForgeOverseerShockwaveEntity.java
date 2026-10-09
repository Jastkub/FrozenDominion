package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * THE RING OFF THE SLAM: where the Forge Overseer's hammer strikes the floor, the floor heaves up under a crest of frost
 * and the heave runs out in a ring, from one block to seven in fourteen ticks (geo/entity/fx_overseer_shockwave, its clip
 * play - linear keys on the same line as radius() here). In his second half it is drawn and run 9/7 the size.
 *
 * <p>The crest takes whoever it reaches on the floor - damage, slowed, thrown up and out - and a JUMP clears it: feet
 * more than 0.9 over the floor as it passes go over it. If his hammer was quenched when it struck, whoever the crest
 * takes is frozen too (ForgeOverseerRimeEntity).
 */
public class ForgeOverseerShockwaveEntity extends Entity implements GeoEntity {

    public static final int RUN = 14, LIFE = 26;
    public static final double R0 = 1.0D, R1 = 7.0D;
    static final double BAND = 0.8D, CLEAR = 0.9D;
    static final float DMG = 8.0F;

    private static final EntityDataAccessor<Float> SIZE =
            SynchedEntityData.defineId(ForgeOverseerShockwaveEntity.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation PLAY = RawAnimation.begin().thenPlayAndHold("animation.fx_overseer_shockwave.play");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private boolean quenched;
    private final Set<UUID> struck = new HashSet<>();

    public ForgeOverseerShockwaveEntity(EntityType<? extends ForgeOverseerShockwaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;                  // seven blocks of ring from a block's box
    }

    public ForgeOverseerShockwaveEntity(Level level, ForgeOverseerEntity owner, Vec3 at, float size, boolean quenched) {
        this(FFEntities.FORGE_OVERSEER_SHOCKWAVE.get(), level);
        this.ownerId = owner.getUUID();
        this.quenched = quenched;
        entityData.set(SIZE, size);
        moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    /** Where the crest is at tick t, in blocks from the middle. */
    public double radius(float t) {
        return (R0 + (R1 - R0) * Math.min(1.0D, t / (double) RUN)) * size();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SIZE, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (tickCount == 1) {
            playSound(FFSounds.FORGE_OVERSEER_SHOCKWAVE.get(), 2.4F, 1.0F);
        }
        if (tickCount <= RUN) {
            run(radius(tickCount));
        }
        if (tickCount >= LIFE) {
            discard();
        }
    }

    private void run(double r) {
        ForgeOverseerEntity owner = ownerId != null && level() instanceof ServerLevel s
                && s.getEntity(ownerId) instanceof ForgeOverseerEntity o ? o : null;
        double reach = R1 * size() + 1.0D;
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(reach, 3.0D,
                reach), this::foe)) {
            if (struck.contains(v.getUUID())) {
                continue;
            }
            double d = Math.hypot(v.getX() - getX(), v.getZ() - getZ());
            if (Math.abs(d - r) > BAND) {
                continue;
            }
            double feet = v.getY() - getY();
            if (feet > CLEAR || feet < -1.2D) {
                continue;                                     // over it, or far under it
            }
            struck.add(v.getUUID());
            boolean hit = v.hurt(owner != null ? damageSources().indirectMagic(this, owner) : damageSources().magic(), DMG);
            if (!hit) {
                continue;
            }
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 80, 0), owner);
            Vec3 out = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
            out = out.lengthSqr() > 1.0E-4D ? out.normalize().scale(0.5D) : Vec3.ZERO;
            v.setDeltaMovement(v.getDeltaMovement().add(out.x, 0.38D, out.z));
            v.hurtMarked = true;
            if (quenched && owner != null) {
                owner.quenchedBlow(v);
            }
        }
    }

    private boolean foe(LivingEntity e) {
        return e.isAlive() && !(e instanceof FrostServantEntity)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
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
        return distance < 80.0D * 80.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ring", 0, state -> state.setAndContinue(PLAY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
