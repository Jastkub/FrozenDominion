package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * PROMIEN LATARNI, the picture of it: the beam of the Last Watch from the bow to the first wall (fx_last_watch_beam -
 * a muzzle flare, fifteen two-block segments the renderer trims to LENGTH, rings running out, a flare at the end).
 * Its hits are struck at once by LastWatchCombat.fireBeam; this only shines for LIFE ticks and goes.
 *
 * <p>The way it points is synced exactly (DIR), not through the entity's rotation: the spawn packet rounds a rotation
 * to 1.4 degrees, which at thirty blocks is the beam drawn most of a block off the line it struck along.
 */
public class LastWatchBeamEntity extends Entity implements GeoEntity {

    /** tools/gen_last_watch.py BEAM_LIFE. */
    public static final int LIFE = 16;

    private static final EntityDataAccessor<Vector3f> DIR =
            SynchedEntityData.defineId(LastWatchBeamEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> LENGTH =
            SynchedEntityData.defineId(LastWatchBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> SPLIT =
            SynchedEntityData.defineId(LastWatchBeamEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation PLAY = RawAnimation.begin().thenPlayAndHold("animation.fx_last_watch_beam.play");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LastWatchBeamEntity(EntityType<? extends LastWatchBeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;                       // thirty blocks of light from a speck of a box
    }

    public LastWatchBeamEntity(Level level, Vec3 origin, Vec3 dir, float length, boolean split) {
        this(FFEntities.LAST_WATCH_BEAM.get(), level);
        Vec3 d = dir.normalize();
        moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
        entityData.set(DIR, new Vector3f((float) d.x, (float) d.y, (float) d.z));
        entityData.set(LENGTH, length);
        entityData.set(SPLIT, split);
    }

    public Vector3f dir() {
        return entityData.get(DIR);
    }

    /** Blocks from the bow to where it ends. */
    public float length() {
        return entityData.get(LENGTH);
    }

    /** One of the three of a full Czuwanie (drawn gold). */
    public boolean split() {
        return entityData.get(SPLIT);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DIR, new Vector3f(0.0F, 0.0F, 1.0F));
        entityData.define(LENGTH, 1.0F);
        entityData.define(SPLIT, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= LIFE) {
            discard();
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        Vec3 o = position();
        Vector3f d = dir();
        float l = length();
        return new AABB(o, o.add(d.x() * l, d.y() * l, d.z() * l)).inflate(1.0D);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0D * 128.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "beam", 0, state -> state.setAndContinue(PLAY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
