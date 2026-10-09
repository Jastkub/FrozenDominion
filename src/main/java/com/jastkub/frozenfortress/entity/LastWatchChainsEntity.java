package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * THE TURNKEY'S CHAINS (fx_last_watch_chains): four of them torn up out of the ground round a pinned foe on their
 * rune-stone anchors, onto an iron collar at his waist (burst, then hold) - and, when the pin runs out, flung off him
 * and pulled back into the ground (break). Sized to the foe it holds (SIZE).
 *
 * <p>While it holds, he is HELD: wherever the game or his own legs would take him, he is put back where the chains
 * caught him, and he cannot jump (LastWatchCombat.pin also slows him to nothing and takes his jump). A heavy foe - a
 * boss - is only slowed: the chains are on him, but no fight is ended by one arrow.
 */
public class LastWatchChainsEntity extends Entity implements GeoEntity {

    public static final byte HOLD = 0, BREAK = 1;
    /** tools/gen_last_watch.py CHAINS_BREAK. */
    static final int BREAK_TICKS = 8;

    private static final EntityDataAccessor<Integer> FOLLOW =
            SynchedEntityData.defineId(LastWatchChainsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE =
            SynchedEntityData.defineId(LastWatchChainsEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> STATE =
            SynchedEntityData.defineId(LastWatchChainsEntity.class, EntityDataSerializers.BYTE);
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("animation.fx_last_watch_chains.burst")
            .thenLoop("animation.fx_last_watch_chains.hold");
    private static final RawAnimation LET_GO = RawAnimation.begin().thenPlayAndHold("animation.fx_last_watch_chains.break");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int until;
    private int brokeAt = -1;
    private boolean heavy;
    private Vec3 anchor = Vec3.ZERO;

    public LastWatchChainsEntity(EntityType<? extends LastWatchChainsEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public LastWatchChainsEntity(Level level, LivingEntity on, @Nullable Player owner, int ticks, boolean heavy) {
        this(FFEntities.LAST_WATCH_CHAINS.get(), level);
        this.until = ticks;
        this.heavy = heavy;
        this.anchor = on.position();
        entityData.set(FOLLOW, on.getId());
        entityData.set(SIZE, Mth.clamp(Math.max(on.getBbWidth() / 0.7F, on.getBbHeight() / 1.9F), 0.6F, 3.5F));
        moveTo(on.getX(), on.getY(), on.getZ(), level.random.nextFloat() * 360.0F, 0.0F);
    }

    /** The chains on `e`, if they still hold him. */
    @Nullable
    public static LastWatchChainsEntity on(LivingEntity e) {
        for (LastWatchChainsEntity c : e.level().getEntitiesOfClass(LastWatchChainsEntity.class,
                e.getBoundingBox().inflate(2.0D), c -> c.entityData.get(FOLLOW) == e.getId())) {
            if (c.state() == HOLD) {
                return c;
            }
        }
        return null;
    }

    public void extend(int ticks) {
        until = Math.max(until, tickCount + ticks);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public byte state() {
        return entityData.get(STATE);
    }

    @Nullable
    public LivingEntity target() {
        return level().getEntity(entityData.get(FOLLOW)) instanceof LivingEntity le ? le : null;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(FOLLOW, -1);
        entityData.define(SIZE, 1.0F);
        entityData.define(STATE, HOLD);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        LivingEntity on = target();
        if (state() == BREAK) {
            if (tickCount - brokeAt >= BREAK_TICKS) {
                discard();
            }
            return;
        }
        if (on == null || !on.isAlive() || tickCount >= until) {
            entityData.set(STATE, BREAK);
            brokeAt = tickCount;
            level().playSound(null, getX(), getY(), getZ(), FFSounds.LAST_WATCH_CHAINS_BREAK.get(), SoundSource.PLAYERS,
                    1.1F, 1.0F);
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 0.8D, getZ(), 14, 0.6D, 0.5D, 0.6D, 0.08D);
            }
            return;
        }
        hold(on);
    }

    /** He stays where the chains caught him: no step, no jump, no shove carries him off it. */
    private void hold(LivingEntity on) {
        Vec3 v = on.getDeltaMovement();
        if (heavy) {
            on.setDeltaMovement(v.x * 0.3D, Math.min(v.y, 0.0D), v.z * 0.3D);
        } else {
            on.setDeltaMovement(0.0D, Math.min(v.y, 0.0D), 0.0D);
            Vec3 at = on.position();
            if (Math.hypot(at.x - anchor.x, at.z - anchor.z) > 0.08D || at.y > anchor.y + 0.1D) {
                on.teleportTo(anchor.x, Math.min(at.y, anchor.y), anchor.z);
            }
            if (on instanceof Mob m) {
                m.getNavigation().stop();
            }
        }
        if (on instanceof Player) {
            on.hurtMarked = true;
        }
        setPos(on.getX(), on.getY(), on.getZ());
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
        controllers.add(new AnimationController<>(this, "chains", 0,
                state -> state.setAndContinue(state() == BREAK ? LET_GO : BITE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
