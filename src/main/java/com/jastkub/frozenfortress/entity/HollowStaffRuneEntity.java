package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.item.HollowStaffItem;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A RUNE OF THE LITANY (Litania, 07.10.2026) - a lozenge of the Priestess's ice with one of the court's glyphs on it
 * (tools/gen_hollow_staff.py, fx_hollow_staff_rune).
 *
 * <p><b>ORBIT</b> while its caster chants: one of five slots round him at shoulder height, face turned out, the ring
 * turning. The client works the orbit out itself from the caster it is told about (its position packets are ignored
 * while it orbits), so the ring turns smoothly. If he stops chanting without letting go - another item, a blow that
 * breaks the use - it breaks where it is.
 *
 * <p><b>FLY</b> on the release, LAUNCH_GAP ticks after the one before it: out and round on a curve and HOMING on its
 * target (or straight down his look if there is none). The hit: {@link #DMG}, FROSTBITE for a moment, the King's MARK
 * for {@link #MARK_T} ticks. A rune that strikes a target UNDER THE SHADOW OF HIS BELL splits in two
 * (HollowStaffBellEntity#shadowOver), each half finding the nearest other foe for {@link #SPLIT_DMG}.
 */
public class HollowStaffRuneEntity extends Entity implements GeoEntity {

    public static final int ORBIT = 0, FLY = 1;
    public static final double ORBIT_R = 1.3D, ORBIT_Y = 1.35D, SPEED = 1.15D;
    public static final int LIFE = 50, LAUNCH_GAP = 3, MARK_T = 120, FROST_T = 60;
    public static final float DMG = 6.0F, SPLIT_DMG = 3.5F;

    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(HollowStaffRuneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER =
            SynchedEntityData.defineId(HollowStaffRuneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLOT =
            SynchedEntityData.defineId(HollowStaffRuneEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation ORBITING = RawAnimation.begin().thenPlay("animation.fx_hollow_staff_rune.spawn")
            .thenLoop("animation.fx_hollow_staff_rune.orbit");
    private static final RawAnimation FLYING = RawAnimation.begin().thenLoop("animation.fx_hollow_staff_rune.fly");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    @Nullable
    private UUID targetId;
    private int launchAt = -1;
    private int flown;
    private boolean child;
    private float damage = DMG;
    private Vec3 aim = Vec3.ZERO;

    public HollowStaffRuneEntity(EntityType<? extends HollowStaffRuneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** A rune taking slot `slot` round its chanting caster. */
    public HollowStaffRuneEntity(Level level, LivingEntity owner, int slot) {
        this(FFEntities.HOLLOW_STAFF_RUNE.get(), level);
        this.ownerId = owner.getUUID();
        entityData.set(OWNER, owner.getId());
        entityData.set(SLOT, slot);
        Vec3 p = orbitPoint(owner, slot, level.getGameTime(), 1.0F);
        moveTo(p.x, p.y, p.z, 0.0F, 0.0F);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(STATE, ORBIT);
        entityData.define(OWNER, -1);
        entityData.define(SLOT, 0);
    }

    public int state() {
        return entityData.get(STATE);
    }

    public int slot() {
        return entityData.get(SLOT);
    }

    @Nullable
    public Entity ownerEntity() {
        return level().getEntity(entityData.get(OWNER));
    }

    @Nullable
    private LivingEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof LivingEntity le) {
            return le;
        }
        return ownerEntity() instanceof LivingEntity le ? le : null;
    }

    public boolean ownedBy(Entity e) {
        return ownerId != null && ownerId.equals(e.getUUID());
    }

    /** Where slot `slot` is round `owner` at game time `time` (the ring turns 9 degrees a tick). */
    public static Vec3 orbitPoint(Entity owner, int slot, long time, float partialTick) {
        double a = Math.toRadians(slot * 72.0D + (time + partialTick) * 9.0D);
        Vec3 o = owner.getPosition(partialTick);
        double bob = 0.12D * Math.sin((time + partialTick) * 0.25D + slot * 1.3D);
        return new Vec3(o.x + Math.cos(a) * ORBIT_R, o.y + ORBIT_Y + bob, o.z + Math.sin(a) * ORBIT_R);
    }

    /** Every rune `owner` has orbiting him, by slot. */
    public static List<HollowStaffRuneEntity> orbiting(LivingEntity owner) {
        List<HollowStaffRuneEntity> out = new ArrayList<>(owner.level().getEntitiesOfClass(HollowStaffRuneEntity.class,
                owner.getBoundingBox().inflate(4.0D), r -> r.state() == ORBIT && r.launchAt < 0 && r.ownedBy(owner)));
        out.sort(Comparator.comparingInt(HollowStaffRuneEntity::slot));
        return out;
    }

    /** Let fly `delay` ticks from now at `target` (or, with none, down `look`). */
    public void launch(@Nullable LivingEntity target, Vec3 look, int delay) {
        this.targetId = target == null ? null : target.getUUID();
        this.aim = look.normalize();
        this.launchAt = tickCount + Math.max(0, delay);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (entityData.get(STATE) == ORBIT) {
            return;                                              // the client keeps its own ring
        }
        super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
    }

    @Override
    public void tick() {
        super.tick();
        if (state() == ORBIT) {
            Entity o = ownerEntity();
            if (o == null) {
                if (!level().isClientSide) {
                    discard();
                }
                return;
            }
            Vec3 p = orbitPoint(o, slot(), level().getGameTime(), 1.0F);
            setPos(p.x, p.y, p.z);
            if (level().isClientSide) {
                return;
            }
            if (launchAt >= 0) {
                if (tickCount >= launchAt) {
                    takeOff(o);
                }
                return;
            }
            if (!(o instanceof LivingEntity le) || !le.isAlive() || !HollowStaffItem.chanting(le)) {
                breakUp(position(), 0.6F);
            }
            return;
        }
        if (level().isClientSide) {
            return;
        }
        fly();
    }

    private void takeOff(Entity o) {
        entityData.set(STATE, FLY);
        LivingEntity target = target();
        Vec3 out = position().subtract(o.position().add(0.0D, ORBIT_Y, 0.0D));
        out = out.lengthSqr() < 1.0E-4 ? aim : out.normalize();
        if (target != null) {                                    // out and round on a curve, homing in
            Vec3 to = target.getBoundingBox().getCenter().subtract(position()).normalize();
            setDeltaMovement(to.scale(0.65D).add(out.scale(0.5D)).add(0.0D, 0.25D, 0.0D).normalize().scale(SPEED));
        } else {                                                 // nobody before him: straight down his look
            setDeltaMovement(aim.scale(SPEED));
        }
        if (level() instanceof ServerLevel s) {
            s.playSound(null, getX(), getY(), getZ(), FFSounds.HOLLOW_STAFF_RUNE_FIRE.get(), SoundSource.PLAYERS, 0.9F,
                    0.9F + 0.08F * slot());
        }
    }

    @Nullable
    private LivingEntity target() {
        if (targetId != null && level() instanceof ServerLevel s && s.getEntity(targetId) instanceof LivingEntity le
                && le.isAlive()) {
            return le;
        }
        return null;
    }

    private void fly() {
        LivingEntity owner = owner();
        Vec3 vel = getDeltaMovement();
        LivingEntity target = target();
        if (target != null) {
            Vec3 want = target.getBoundingBox().getCenter().subtract(position()).normalize().scale(SPEED);
            vel = vel.scale(0.72D).add(want.scale(0.28D));
            vel = vel.lengthSqr() < 1.0E-4 ? want : vel.normalize().scale(SPEED);
        }
        Vec3 from = position();
        Vec3 to = from.add(vel);
        BlockHitResult wall = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) {
            to = wall.getLocation();
        }
        AABB sweep = getBoundingBox().expandTowards(vel).inflate(0.6D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level(), this, from, to, sweep,
                e -> HollowStaffMagic.isFoe(owner, e));
        setDeltaMovement(vel);
        setPos(to.x, to.y, to.z);
        if (hit != null && hit.getEntity() instanceof LivingEntity v) {
            strike(v, owner);
            return;
        }
        if (wall.getType() != HitResult.Type.MISS || ++flown > LIFE) {
            breakUp(position(), 0.8F);
        }
    }

    private void strike(LivingEntity v, @Nullable LivingEntity owner) {
        HollowStaffMagic.strike(v, this, owner, damage);
        v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), FROST_T, 0), owner);
        if (owner != null) {
            HollowStaffMagic.mark(v, owner, MARK_T);
        }
        Vec3 at = v.getBoundingBox().getCenter();
        HollowStaffFxEntity.spawn(level(), HollowStaffFxEntity.BURST, at, getYRot(), child ? 0.8F : 1.2F, 10);
        level().playSound(null, at.x, at.y, at.z, FFSounds.HOLLOW_STAFF_RUNE_HIT.get(), SoundSource.PLAYERS, 1.0F,
                child ? 1.35F : 0.95F + random.nextFloat() * 0.15F);
        if (!child && owner != null && HollowStaffBellEntity.shadowOver(v, owner) != null) {
            split(v, owner, at);
        }
        discard();
    }

    /** Under his bell's shadow it breaks in two: each half finds the nearest other foe (or the same one). */
    private void split(LivingEntity struck, LivingEntity owner, Vec3 at) {
        List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, struck.getBoundingBox().inflate(8.0D),
                e -> e != struck && HollowStaffMagic.inTheFight(owner, e));
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(struck)));
        Vec3 side = new Vec3(-getDeltaMovement().z, 0.0D, getDeltaMovement().x);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        for (int i = 0; i < 2; i++) {
            LivingEntity t = near.size() > i ? near.get(i) : struck;
            HollowStaffRuneEntity half = new HollowStaffRuneEntity(FFEntities.HOLLOW_STAFF_RUNE.get(), level());
            half.ownerId = owner.getUUID();
            half.entityData.set(OWNER, owner.getId());
            half.entityData.set(SLOT, i);
            half.entityData.set(STATE, FLY);
            half.child = true;
            half.damage = SPLIT_DMG;
            half.targetId = t.getUUID();
            half.aim = getDeltaMovement().normalize();
            Vec3 start = at.add(side.scale(i == 0 ? 0.8D : -0.8D)).add(0.0D, 0.4D, 0.0D);
            half.moveTo(start.x, start.y, start.z, getYRot(), 0.0F);
            half.setDeltaMovement(side.scale(i == 0 ? 0.6D : -0.6D).add(0.0D, 0.55D, 0.0D));
            level().addFreshEntity(half);
        }
        level().playSound(null, at.x, at.y, at.z, FFSounds.HOLLOW_STAFF_RUNE_FORM.get(), SoundSource.PLAYERS, 1.0F, 1.6F);
    }

    private void breakUp(Vec3 at, float size) {
        HollowStaffFxEntity.spawn(level(), HollowStaffFxEntity.BURST, at.add(0.0D, 0.25D, 0.0D), getYRot(), size, 10);
        level().playSound(null, at.x, at.y, at.z, FFSounds.HOLLOW_STAFF_RUNE_HIT.get(), SoundSource.PLAYERS, 0.5F, 1.5F);
        discard();
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
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "rune", 2,
                state -> state.setAndContinue(state() == ORBIT ? ORBITING : FLYING)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
