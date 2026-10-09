package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.entity.projectile.LastWatchArrowEntity;
import com.jastkub.frozenfortress.item.LastWatchCombat;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
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
 * OSTATNIA WARTA - THE FROZEN WATCHMAN (fx_last_watch_sentinel): what the backstep leaves where the archer stood. He
 * comes up out of a mound of ice (rise), stands his watch for ten seconds with an arrow on the string (idle) and shoots
 * (shoot - the bolt leaves on its SENTINEL_RELEASE'th tick), then cracks and falls in (shatter).
 *
 * <p>Whom he shoots: the archer's MARKED foes first, the nearest of them; else the nearest monster, or whatever is
 * after the archer - in his sight, within REACH. His bolts are the archer's (the kill is his) and MARK what they hit,
 * so the watchman feeds the chains. He turns to his mark and leans to it (the renderer pitches his upper body by his
 * xRot). Not a creature: nothing targets him, nothing hurts him, he is not saved.
 */
public class LastWatchSentinelEntity extends Entity implements GeoEntity {

    public static final int RISE = 10;        // tools/gen_last_watch.py SENTINEL_RISE
    public static final int LIFE = 200;       // ten seconds of watch
    public static final int SHOOT_EVERY = 16;
    public static final int RELEASE = 8;      // SENTINEL_RELEASE
    public static final int END = 12;         // SENTINEL_END
    public static final double REACH = 22.0D;
    public static final float BOLT_DAMAGE = 8.0F;
    public static final float BOLT_SPEED = 3.0F;
    public static final byte WATCH = 0, SHATTER = 1;

    private static final EntityDataAccessor<Byte> STATE =
            SynchedEntityData.defineId(LastWatchSentinelEntity.class, EntityDataSerializers.BYTE);
    private static final RawAnimation STAND = RawAnimation.begin().thenPlay("animation.fx_last_watch_sentinel.rise")
            .thenLoop("animation.fx_last_watch_sentinel.idle");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.fx_last_watch_sentinel.idle");
    /** Triggered (server: triggerAnim) - when it is done the controller goes back to IDLE by itself. */
    private static final RawAnimation SHOOT = RawAnimation.begin().then("animation.fx_last_watch_sentinel.shoot",
            software.bernie.geckolib.animation.Animation.LoopType.PLAY_ONCE);                                           // (held, it never ended: one shot shown)
    private static final RawAnimation FALL = RawAnimation.begin().thenPlayAndHold("animation.fx_last_watch_sentinel.shatter");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    @Nullable
    private UUID aimAt;
    private int releaseAt = -1;
    private int endAt = -1;

    public LastWatchSentinelEntity(EntityType<? extends LastWatchSentinelEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public LastWatchSentinelEntity(Level level, Player owner, Vec3 at, float yaw) {
        this(FFEntities.LAST_WATCH_SENTINEL.get(), level);
        this.ownerId = owner.getUUID();
        moveTo(at.x, at.y, at.z, yaw, 0.0F);
        setYRot(yaw);
    }

    public boolean isOwnedBy(Player p) {
        return p.getUUID().equals(ownerId);
    }

    @Nullable
    private Player owner() {
        return ownerId == null ? null : level().getPlayerByUUID(ownerId);
    }

    public void shatter() {
        if (entityData.get(STATE) != SHATTER) {
            entityData.set(STATE, SHATTER);
            endAt = tickCount + END;
            level().playSound(null, getX(), getY() + 1.0D, getZ(), FFSounds.LAST_WATCH_SENTINEL_END.get(),
                    SoundSource.PLAYERS, 1.2F, 1.0F);
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 1.2D, getZ(), 30, 0.5D, 0.8D, 0.5D, 0.1D);
            }
        }
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(STATE, WATCH);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (tickCount == 1) {
            playSound(FFSounds.LAST_WATCH_SENTINEL_RISE.get(), 1.3F, 1.0F);
        }
        if (entityData.get(STATE) == SHATTER) {
            if (tickCount >= endAt) {
                discard();
            }
            return;
        }
        Player owner = owner();
        if (owner == null || !owner.isAlive() || tickCount >= LIFE || owner.distanceToSqr(this) > 96.0D * 96.0D) {
            shatter();
            return;
        }
        if (tickCount < RISE) {
            return;
        }
        LivingEntity aim = aimAt != null && level() instanceof ServerLevel sl
                && sl.getEntity(aimAt) instanceof LivingEntity le && le.isAlive() ? le : null;
        if (aim != null) {
            face(aim, 25.0F);
        }
        if (releaseAt == tickCount) {
            if (aim != null && sees(aim)) {
                loose(owner, aim);
            }
            releaseAt = -1;
        }
        if (releaseAt < 0 && (tickCount - RISE) % SHOOT_EVERY == 2) {
            LivingEntity pick = pick(owner);
            if (pick != null) {
                aimAt = pick.getUUID();
                face(pick, 360.0F);
                triggerAnim("main", "shoot");
                releaseAt = tickCount + RELEASE;
            }
        }
    }

    /** Marked foes first (the nearest), then the nearest monster or whatever is after the archer - seen, in reach. */
    @Nullable
    private LivingEntity pick(Player owner) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(REACH),
                e -> LastWatchCombat.isHostile(owner, e))) {
            double d = e.distanceToSqr(this);
            if (d > REACH * REACH || !sees(e)) {
                continue;
            }
            double score = d - (LastWatchCombat.isMarkedBy(e, owner) ? 10000.0D : 0.0D);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private Vec3 eye() {
        return position().add(0.0D, 1.6D, 0.0D);
    }

    private boolean sees(LivingEntity e) {
        Vec3 to = new Vec3(e.getX(), e.getY(0.6D), e.getZ());
        return level().clip(new ClipContext(eye(), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    private void face(LivingEntity e, float maxTurn) {
        Vec3 d = new Vec3(e.getX(), e.getY(0.6D), e.getZ()).subtract(eye());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        setYRot(Mth.approachDegrees(getYRot(), yaw, maxTurn));
        setXRot(Mth.clamp(pitch, -50.0F, 50.0F));
    }

    /** The bolt: from his bow, led onto where the foe will be, straight (it does not fall). */
    private void loose(Player owner, LivingEntity aim) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 from = eye().add(fwd.scale(0.9D)).add(0.0D, -0.05D, 0.0D);
        Vec3 target = new Vec3(aim.getX(), aim.getY(0.55D), aim.getZ());
        double t = from.distanceTo(target) / BOLT_SPEED;
        target = target.add(aim.getDeltaMovement().multiply(t, 0.0D, t));
        Vec3 d = target.subtract(from);
        LastWatchArrowEntity bolt = new LastWatchArrowEntity(level(), owner, LastWatchArrowEntity.SENTINEL);
        bolt.setPos(from.x, from.y, from.z);
        bolt.setNoGravity(true);
        bolt.shoot(d.x, d.y, d.z, BOLT_SPEED, 0.5F);
        bolt.setBaseDamage(BOLT_DAMAGE / BOLT_SPEED);
        bolt.pickup = AbstractArrow.Pickup.DISALLOWED;
        level().addFreshEntity(bolt);
        level().playSound(null, from.x, from.y, from.z, FFSounds.LAST_WATCH_SENTINEL_SHOT.get(), SoundSource.PLAYERS,
                1.0F, 0.95F + random.nextFloat() * 0.1F);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0D * 96.0D;
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
        // (rise runs into idle; once he is up the state is plain IDLE, so a finished shot never replays the rise)
        controllers.add(new AnimationController<>(this, "main", 2,
                state -> state.setAndContinue(entityData.get(STATE) == SHATTER ? FALL : tickCount < RISE + 2 ? STAND : IDLE))
                .triggerableAnim("shoot", SHOOT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
