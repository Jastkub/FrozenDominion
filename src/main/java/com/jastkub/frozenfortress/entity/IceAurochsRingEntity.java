package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * THE RING OF THE STOMP - the Ice Aurochs reared up and brought both forehooves down (IceAurochsEntity, STOMP): the
 * floor breaks in a crater under them, and a ring of ice spikes bursts up out of it and runs outward over the floor,
 * from two blocks to eleven in a little over a second (0.41 a tick) - a model of its own (ice_aurochs_ring,
 * tools/gen_ice_aurochs.py; its clip and this class keep the same straight line: RING_RISE, RING_END, RING_R0, RING_R1).
 *
 * <p>The crest takes whoever it reaches on the floor - hurt, slowed, frostbitten, thrown outward. Three answers, each
 * a thing to learn: be OVER it (under a block high: a jump timed to it clears it), be out of its reach, or be behind
 * one of the hall's pillars - it runs along the floor, and stone stops it.
 */
public class IceAurochsRingEntity extends Entity implements GeoEntity {

    public static final int RISE = 3, END = 25, LIFE = 34;
    public static final double R0 = 2.0D, R1 = 11.0D;
    /** How deep the crest is, either side of its line. */
    static final double BAND = 0.8D;
    /** Feet this far over the floor go over the crest. */
    static final double CLEAR = 0.9D;
    static final float DMG = 8.0F;

    private static final RawAnimation PLAY = RawAnimation.begin().thenPlayAndHold("animation.ice_aurochs_ring.play");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private final Set<UUID> struck = new HashSet<>();

    public IceAurochsRingEntity(EntityType<? extends IceAurochsRingEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;                  // eleven blocks of ring from a block's box
    }

    public IceAurochsRingEntity(Level level, IceAurochsEntity owner, Vec3 at) {
        this(FFEntities.ICE_AUROCHS_RING.get(), level);
        this.ownerId = owner.getUUID();
        moveTo(at.x, owner.getY(), at.z, 0.0F, 0.0F);
    }

    /** Where the crest is at tick t, in blocks from the middle (-1 before it rises). */
    public static double radius(float t) {
        if (t < RISE) {
            return -1.0D;
        }
        return R0 + (R1 - R0) * Math.min(1.0D, (t - RISE) / (double) (END - RISE));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        int t = tickCount;
        if (t == RISE) {
            playSound(FFSounds.ICE_AUROCHS_RING.get(), 2.6F, 1.0F);
        }
        if (t >= RISE && t <= END) {
            run(radius(t));
        }
        if (t >= LIFE) {
            discard();
        }
    }

    private void run(double r) {
        LivingEntity owner = ownerId != null && level() instanceof ServerLevel s
                && s.getEntity(ownerId) instanceof LivingEntity le ? le : null;
        AABB reach = getBoundingBox().inflate(R1 + 1.0D, 3.0D, R1 + 1.0D);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, reach, this::foe)) {
            if (struck.contains(v.getUUID())) {
                continue;
            }
            double d = Math.hypot(v.getX() - getX(), v.getZ() - getZ());
            if (Math.abs(d - r) > BAND) {
                continue;
            }
            double feet = v.getY() - getY();
            if (feet > CLEAR || feet < -1.6D) {
                continue;                                     // over it - or far under it
            }
            struck.add(v.getUUID());
            if (sheltered(v)) {
                continue;                                     // a pillar (or a wall) between: it broke on the stone
            }
            v.hurt(owner != null ? damageSources().indirectMagic(this, owner) : damageSources().magic(), DMG);
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 0), owner);
            Vec3 out = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
            if (out.lengthSqr() > 1.0E-4D) {
                out = out.normalize().scale(0.6D);
                v.setDeltaMovement(v.getDeltaMovement().add(out.x, 0.35D, out.z));
                v.hurtMarked = true;
            }
        }
    }

    /** Is there stone along the floor between the crater and them (the crest runs low, at shin height)? */
    private boolean sheltered(LivingEntity v) {
        Vec3 from = new Vec3(getX(), getY() + 0.5D, getZ());
        Vec3 to = new Vec3(v.getX(), getY() + 0.5D, v.getZ());
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() != HitResult.Type.MISS;
    }

    private boolean foe(LivingEntity e) {
        return e.isAlive() && !FFAllies.ofTheKing(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
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
        controllers.add(new AnimationController<>(this, "ring", 0, state -> state.setAndContinue(PLAY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
