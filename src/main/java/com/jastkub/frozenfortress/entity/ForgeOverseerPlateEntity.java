package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A PLATE OFF THE FORGE OVERSEER: one of his six (ForgeOverseerEntity.PLATE_BONES), torn off by the blows that wore it
 * through. It is put where the plate was on him, turned as he was, and given a shove away from him; it tumbles as it
 * falls (fall), rings on the floor and lies flat there (land), and some ten seconds later sinks away (fade). Its model
 * is the very plate he wore, re-centred: geo/entity/forge_overseer_plate_&lt;bone&gt; (tools/gen_forge_overseer.py).
 *
 * <p>Only a picture with weight: it hurts nobody and nothing hits it.
 */
public class ForgeOverseerPlateEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(ForgeOverseerPlateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> LANDED =
            SynchedEntityData.defineId(ForgeOverseerPlateEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FADING =
            SynchedEntityData.defineId(ForgeOverseerPlateEntity.class, EntityDataSerializers.BOOLEAN);

    static final int LIE = 220, LIFE = 250;

    private static final RawAnimation FALL = RawAnimation.begin().thenLoop("animation.forge_overseer_plate.fall");
    private static final RawAnimation LAND = RawAnimation.begin().thenPlayAndHold("animation.forge_overseer_plate.land");
    private static final RawAnimation FADE = RawAnimation.begin().thenPlayAndHold("animation.forge_overseer_plate.fade");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ForgeOverseerPlateEntity(EntityType<? extends ForgeOverseerPlateEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public ForgeOverseerPlateEntity(Level level, int kind, Vec3 at, float yaw, Vec3 shove) {
        this(FFEntities.FORGE_OVERSEER_PLATE.get(), level);
        entityData.set(KIND, kind);
        moveTo(at.x, at.y, at.z, yaw, 0.0F);
        setYRot(yaw);
        setDeltaMovement(shove);
    }

    /** Which of his plates it is (an index into ForgeOverseerEntity.PLATE_BONES). */
    public int kind() {
        return Math.floorMod(entityData.get(KIND), ForgeOverseerEntity.PLATE_BONES.length);
    }

    public String bone() {
        return ForgeOverseerEntity.PLATE_BONES[kind()];
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(KIND, 0);
        builder.define(LANDED, false);
        builder.define(FADING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!entityData.get(LANDED)) {
            Vec3 v = getDeltaMovement().add(0.0D, -0.06D, 0.0D);
            move(MoverType.SELF, v);
            setDeltaMovement(v.multiply(0.98D, 0.98D, 0.98D));
            if (onGround() && !level().isClientSide) {
                entityData.set(LANDED, true);
                setDeltaMovement(Vec3.ZERO);
                playSound(FFSounds.FORGE_OVERSEER_PLATE_LAND.get(), 1.6F, 0.85F + random.nextFloat() * 0.3F);
            }
        }
        if (level().isClientSide) {
            return;
        }
        if (tickCount > 80 && !entityData.get(LANDED)) {
            entityData.set(LANDED, true);                             // fell somewhere it cannot land: lie where it is
        }
        if (tickCount == LIE) {
            entityData.set(FADING, true);
        }
        if (tickCount >= LIFE) {
            discard();
        }
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
        controllers.add(new AnimationController<>(this, "plate", 3, state -> state.setAndContinue(
                entityData.get(FADING) ? FADE : entityData.get(LANDED) ? LAND : FALL)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
