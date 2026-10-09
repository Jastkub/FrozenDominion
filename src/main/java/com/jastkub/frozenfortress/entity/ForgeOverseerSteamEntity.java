package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * THE STEAM OFF THE QUENCH: the Forge Overseer has driven his hammer into the water (ForgeOverseerEntity.plunge) and a
 * cloud with a body bursts up out of it - a hiss of light at the water's face, a column of steam, then puffs boiling up
 * three blocks and rolling outward as they thin (geo/entity/fx_overseer_steam, its clip billow, 60 ticks). It is the
 * tell that his blows freeze from now on. It hurts nobody; ForgeOverseerFxRenderers.Steam draws it see-through, fading.
 */
public class ForgeOverseerSteamEntity extends net.minecraft.world.entity.Entity implements GeoEntity {

    public static final int LIFE = 60;
    private static final RawAnimation BILLOW = RawAnimation.begin().thenPlayAndHold("animation.fx_overseer_steam.billow");
    /** A HEARTH'S BREATH, not the quench (ForgeOverseerEntity.hearthBreath): half the size, frost-blue, fainter - so
     *  that it is never taken for the white billow that tells his blows now freeze. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> PUFF =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ForgeOverseerSteamEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ForgeOverseerSteamEntity(EntityType<? extends ForgeOverseerSteamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public ForgeOverseerSteamEntity(Level level, Vec3 at) {
        this(FFEntities.FORGE_OVERSEER_STEAM.get(), level);
        moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
    }

    /** Make it a hearth's breath. */
    public ForgeOverseerSteamEntity puff() {
        entityData.set(PUFF, true);
        return this;
    }

    public boolean isPuff() {
        return entityData.get(PUFF);
    }

    /** 1 while it boils, thinning to 0 over its last half. */
    public float thickness(float partialTick) {
        float age = tickCount + partialTick;
        return age < LIFE * 0.5F ? 1.0F : Math.max(0.0F, 1.0F - (age - LIFE * 0.5F) / (LIFE * 0.5F));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= LIFE) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(PUFF, false);
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
        return distance < 96.0D * 96.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "steam", 0, state -> state.setAndContinue(BILLOW)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
