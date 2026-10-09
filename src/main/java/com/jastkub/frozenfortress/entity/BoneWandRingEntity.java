package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * THE GRAVE'S RING (07.10.2026) - what the Wand of the Dead opens on the floor where its master looks: a pit of
 * grave-dark rimmed with the wand's green frost, the court's runes round it, fangs of bone and ice up out of the
 * floor, cold green fire coming up off it (geo/entity/fx_bone_wand_ring, tools/gen_bone_wand.py, clip "play").
 *
 * <p>It opens by {@link #OPEN}; its servants claw up out of it one by one, at {@link #SUMMON_AT}; it closes from
 * {@link #CLOSE} and is gone at {@link #LIFE}. A small one ({@link #SMALL}, no servants of its own) is what a servant
 * that was left behind claws up out of beside its master (BoneWandSkeletonEntity#callBack).
 */
public class BoneWandRingEntity extends Entity implements GeoEntity {

    // ---- the beats of its clip (tools/gen_bone_wand.py RING_* - change one, change both)
    public static final int LIFE = 56, OPEN = 6, CLOSE = 44;
    public static final int[] SUMMON_AT = {5, 11};
    /** Where the servants come up: this many blocks either side of the middle, across its master's look. */
    public static final double SPREAD = 0.75D;
    public static final float FULL = 1.0F, SMALL = 0.55F;

    private static final EntityDataAccessor<Float> SIZE =
            SynchedEntityData.defineId(BoneWandRingEntity.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation PLAY = RawAnimation.begin().thenPlayAndHold("animation.fx_bone_wand_ring.play");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private int count;

    public BoneWandRingEntity(EntityType<? extends BoneWandRingEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** A ring for `owner` at `at` (a floor), turned to `yaw`, bringing up `count` servants (0: a picture only). */
    public BoneWandRingEntity(Level level, LivingEntity owner, Vec3 at, float yaw, int count, float size) {
        this(FFEntities.BONE_WAND_RING.get(), level);
        this.ownerId = owner.getUUID();
        this.count = Mth.clamp(count, 0, SUMMON_AT.length);
        entityData.set(SIZE, size);
        moveTo(at.x, at.y, at.z, yaw, 0.0F);
        setYRot(yaw);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(SIZE, FULL);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        float s = size();
        if (tickCount <= OPEN && tickCount % 2 == 1) {          // (particles only go with the ring: the cold coming up)
            sl.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 0.2D, getZ(), 3, 0.9D * s, 0.05D, 0.9D * s, 0.02D);
        }
        for (int i = 0; i < count; i++) {
            if (tickCount == SUMMON_AT[i]) {
                bringUp(sl, i);
            }
        }
        if (tickCount >= LIFE) {
            discard();
        }
    }

    /** The i-th servant, up out of the floor across the middle - or in the middle, if that spot is walled. */
    private void bringUp(ServerLevel sl, int i) {
        if (ownerId == null || !(sl.getEntity(ownerId) instanceof LivingEntity owner) || !owner.isAlive()) {
            return;
        }
        Vec3 side = Vec3.directionFromRotation(0.0F, getYRot() + 90.0F).scale(count == 1 ? 0.0D : (i == 0 ? SPREAD : -SPREAD));
        Vec3 at = position().add(side);
        if (!sl.noCollision(FFEntities.BONE_WAND_SKELETON.get().getSpawnAABB(at.x, at.y, at.z))) {
            at = position();
        }
        BoneWandSkeletonEntity.rise(sl, owner, at.x, at.y, at.z, getYRot() + (i == 0 ? -8.0F : 8.0F));
        BlockState under = sl.getBlockState(BlockPos.containing(at.x, at.y - 0.5D, at.z));
        if (!under.isAir()) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), at.x, at.y + 0.1D, at.z, 14,
                    0.35D, 0.05D, 0.35D, 0.12D);
        }
        sl.sendParticles(FFParticles.ICE_SHARD.get(), at.x, at.y + 0.2D, at.z, 8, 0.3D, 0.1D, 0.3D, 0.06D);
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
        discard();                                                // a ring does not outlast a reload
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

    /** (Entity's own: nothing rides it, nothing pushes it.) */
    @Override
    public boolean canCollideWith(Entity other) {
        return false;
    }
}
