package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The burst of a thrown flask of warmth (ThrownWarmthEntity), as a thing to see and nothing more: drawn by
 * WarmthSplashRenderer - a low dome of heat swelling out to the warmth's reach, the front of it running over the
 * floor, little flames standing up along it - gone in under a second. The warmth itself was given as it burst.
 */
public class WarmthSplashEntity extends Entity {

    /** How long it is seen, in ticks. */
    public static final int LIFE = 16;

    public WarmthSplashEntity(EntityType<? extends WarmthSplashEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public WarmthSplashEntity(Level level, double x, double y, double z) {
        this(FFEntities.WARMTH_SPLASH.get(), level);
        setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData() {
    }

    /** How far through its life it is, 0..1. */
    public float progress(float partialTick) {
        return Mth.clamp((tickCount + partialTick) / LIFE, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LIFE) {
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();                                              // a burst does not outlast a reload
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
