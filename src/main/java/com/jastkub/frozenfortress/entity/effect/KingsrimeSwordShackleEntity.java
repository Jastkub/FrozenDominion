package com.jastkub.frozenfortress.entity.effect;

import com.jastkub.frozenfortress.item.KingsrimeSwordItem;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The FROST THAT HOLDS what the Koronacja Mrozu struck (KingsrimeSwordCrownEntity): a ring of ice crystals grown up
 * round its feet, leaning in on it - and for as long as they stand (a second) it stays where it was caught, its
 * steps and its pathing undone every tick. Then they shatter outward and it is free.
 *
 * <p>Something too big to hold (a boss, KingsrimeSwordItem#unrootable) is only slowed hard, the ice round it all
 * the same. Drawn on its holder (KingsrimeSwordShackleRenderer), sized to it.
 */
public class KingsrimeSwordShackleEntity extends Entity {

    /** Ticks it takes to shatter, at the end of its life. */
    public static final int BREAK = 4;

    private static final EntityDataAccessor<Integer> HOLDER =
            SynchedEntityData.defineId(KingsrimeSwordShackleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(KingsrimeSwordShackleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WIDTH =
            SynchedEntityData.defineId(KingsrimeSwordShackleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT =
            SynchedEntityData.defineId(KingsrimeSwordShackleEntity.class, EntityDataSerializers.FLOAT);

    @Nullable
    private Vec3 anchor;
    private boolean rooted;

    public KingsrimeSwordShackleEntity(EntityType<? extends KingsrimeSwordShackleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static void bind(ServerLevel level, LivingEntity v, int ticks) {
        KingsrimeSwordShackleEntity s = new KingsrimeSwordShackleEntity(FFEntities.KINGSRIME_SHACKLE.get(), level);
        s.moveTo(v.getX(), v.getY(), v.getZ(), 0.0F, 0.0F);
        s.entityData.set(HOLDER, v.getId());
        s.entityData.set(LIFE, ticks + BREAK);
        s.entityData.set(WIDTH, v.getBbWidth());
        s.entityData.set(HEIGHT, v.getBbHeight());
        s.anchor = v.position();
        s.rooted = !KingsrimeSwordItem.unrootable(v);
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, s.rooted ? 9 : 3, false, false));
        level.addFreshEntity(s);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public float holderWidth() {
        return entityData.get(WIDTH);
    }

    public float holderHeight() {
        return entityData.get(HEIGHT);
    }

    @Nullable
    public LivingEntity holder() {
        return level().getEntity(entityData.get(HOLDER)) instanceof LivingEntity l ? l : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        LivingEntity v = holder();
        int hold = life() - BREAK;
        if (tickCount == hold) {
            playSound(FFSounds.ICE_SHATTER.get(), 0.7F, 1.35F);
            s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 0.3D, getZ(), 12,
                    holderWidth() * 0.5D, 0.3D, holderWidth() * 0.5D, 0.12D);
        }
        if (v == null || !v.isAlive() || tickCount > life()) {
            discard();
            return;
        }
        if (tickCount < hold && rooted && anchor != null) {
            // held: no step it takes stays taken (a fall still falls)
            Vec3 m = v.getDeltaMovement();
            v.setDeltaMovement(0.0D, Math.min(0.0D, m.y), 0.0D);
            if (Math.hypot(v.getX() - anchor.x, v.getZ() - anchor.z) > 0.05D) {
                v.teleportTo(anchor.x, v.getY(), anchor.z);
            }
            if (v instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            v.hurtMarked = true;
        }
        setPos(v.getX(), v.getY(), v.getZ());
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(HOLDER, -1);
        builder.define(LIFE, 24);
        builder.define(WIDTH, 0.6F);
        builder.define(HEIGHT, 1.8F);
    }

    @Override
    public boolean isPickable() {
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
        return distance < 64.0D * 64.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
