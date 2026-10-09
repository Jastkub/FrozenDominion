package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * THE MARK OF THE LANTERN, seen: a little caged lantern with an eye of light in it, floating over a marked foe
 * (fx_last_watch_mark). It opens when the mark is laid (appear) - and again each time it is topped up - turns and
 * bobs while it holds (idle), blinks out its last second (expire), and snaps shut and bursts when the Turnkey's
 * chains spend it (consume). The mark itself is on the foe (LastWatchCombat); this only follows him.
 */
public class LastWatchMarkEntity extends Entity implements GeoEntity {

    public static final byte LIVE = 0, EXPIRING = 1, CONSUMED = 2;
    /** The last ticks of a mark blink (the expire clip). */
    static final int BLINK = 20;
    /** tools/gen_last_watch.py MARK_CONSUME. */
    static final int CONSUME_TICKS = 5;
    /** How high over the foe's head it floats, blocks. */
    public static final double ABOVE = 0.55D;

    private static final EntityDataAccessor<Integer> FOLLOW =
            SynchedEntityData.defineId(LastWatchMarkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> STATE =
            SynchedEntityData.defineId(LastWatchMarkEntity.class, EntityDataSerializers.BYTE);
    private static final RawAnimation OPEN = RawAnimation.begin().thenPlay("animation.fx_last_watch_mark.appear")
            .thenLoop("animation.fx_last_watch_mark.idle");
    private static final RawAnimation BLINKING = RawAnimation.begin().thenLoop("animation.fx_last_watch_mark.expire");
    private static final RawAnimation SPENT = RawAnimation.begin().thenPlayAndHold("animation.fx_last_watch_mark.consume");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Server: the tick it lapses, and the tick it was spent. */
    private int until;
    private int spentAt = -1;

    public LastWatchMarkEntity(EntityType<? extends LastWatchMarkEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public LastWatchMarkEntity(Level level, LivingEntity on, int ticks) {
        this(FFEntities.LAST_WATCH_MARK.get(), level);
        entityData.set(FOLLOW, on.getId());
        this.until = ticks;
        moveTo(on.getX(), on.getY() + on.getBbHeight() + ABOVE, on.getZ(), 0.0F, 0.0F);
    }

    /** The mark over `e`, if it has one. */
    @Nullable
    public static LastWatchMarkEntity on(LivingEntity e) {
        for (LastWatchMarkEntity m : e.level().getEntitiesOfClass(LastWatchMarkEntity.class,
                e.getBoundingBox().inflate(1.0D, 3.0D, 1.0D), m -> m.entityData.get(FOLLOW) == e.getId())) {
            if (m.state() != CONSUMED) {
                return m;
            }
        }
        return null;
    }

    public void refresh(int ticks) {
        until = tickCount + ticks;
        entityData.set(STATE, LIVE);
    }

    public void consume() {
        if (state() != CONSUMED) {
            entityData.set(STATE, CONSUMED);
            spentAt = tickCount;
        }
    }

    public byte state() {
        return entityData.get(STATE);
    }

    @Nullable
    public LivingEntity target() {
        return level().getEntity(entityData.get(FOLLOW)) instanceof LivingEntity le ? le : null;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(FOLLOW, -1);
        builder.define(STATE, LIVE);
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity on = target();
        if (on != null) {
            setPos(on.getX(), on.getY() + on.getBbHeight() + ABOVE, on.getZ());
        }
        if (level().isClientSide) {
            return;
        }
        if (state() == CONSUMED) {
            if (tickCount - spentAt >= CONSUME_TICKS) {
                discard();
            }
            return;
        }
        if (on == null || !on.isAlive() || tickCount >= until) {
            discard();
            return;
        }
        byte want = until - tickCount <= BLINK ? EXPIRING : LIVE;
        if (want != state()) {
            entityData.set(STATE, want);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 80.0D * 80.0D;
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
        controllers.add(new AnimationController<>(this, "mark", 2, state -> state.setAndContinue(
                state() == CONSUMED ? SPENT : state() == EXPIRING ? BLINKING : OPEN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
