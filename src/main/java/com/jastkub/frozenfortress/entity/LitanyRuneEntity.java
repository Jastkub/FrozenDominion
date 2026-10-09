package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * One tile of the Priestess's Litany: three blocks of the chapel's floor with a
 * rune of light on it, one of four kinds, brighter at every verse. Nothing but
 * a picture - the Priestess judges who stands where at the Amen. Not to be
 * touched or struck (it is no living thing, so nothing can be aimed at it).
 */
public class LitanyRuneEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Integer> RUNE =
            SynchedEntityData.defineId(LitanyRuneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VERSE =
            SynchedEntityData.defineId(LitanyRuneEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;

    public LitanyRuneEntity(EntityType<? extends LitanyRuneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public LitanyRuneEntity(Level level, RimePriestessEntity owner, int rune, Vec3 at) {
        this(FFEntities.LITANY_RUNE.get(), level);
        this.ownerId = owner.getUUID();
        entityData.set(RUNE, rune);
        entityData.set(VERSE, 1);
        moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(RUNE, 0);
        builder.define(VERSE, 1);
    }

    public int rune() {
        return entityData.get(RUNE);
    }

    public int verse() {
        return entityData.get(VERSE);
    }

    void setVerse(int v) {
        entityData.set(VERSE, v);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel s && (tickCount > 600 || ownerId == null
                || !(s.getEntity(ownerId) instanceof RimePriestessEntity p) || !p.isAlive())) {
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
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0D * 96.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(RUNE, tag.getInt("Rune"));
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Rune", rune());
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
