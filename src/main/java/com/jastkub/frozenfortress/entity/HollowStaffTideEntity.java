package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * THE BLACK TIDE (Czarny Przyplyw, 07.10.2026) - the Drowned Lady's black water drawn up round the wielder
 * (tools/gen_hollow_staff.py, fx_hollow_staff_tide). It WELLS at his feet (to {@link #WELL}), runs out as a ring
 * crested with ice from {@link #R0} to {@link #R1} blocks (to {@link #OUT}) - the crest strikes what it crosses
 * ({@link #DMG}, slowed, frostbitten) and CATCHES it - stands (to {@link #STAND}), and then is DRAGGED BACK to
 * {@link #RPULL} (to {@link #PULL}), and everything it caught comes back with it. At the end a MARKED one is HELD
 * where it was left for {@link #HOLD_T} ticks (HollowStaffMagic#hold) - and a bell dropped on a held one strikes
 * half again as hard.
 *
 * <p>Its clip and {@link #radius} keep the same straight lines (tools/gen_hollow_staff.py tide_radius).
 */
public class HollowStaffTideEntity extends Entity implements GeoEntity {

    public static final int WELL = 4, OUT = 14, STAND = 18, PULL = 28, LIFE = 34;
    public static final double R0 = 1.5D, R1 = 7.5D, RPULL = 1.0D;
    static final double BAND = 0.9D;
    public static final float DMG = 7.0F;
    public static final int HOLD_T = 30, SLOW_T = 60;

    private static final RawAnimation PLAY = RawAnimation.begin().thenPlayAndHold("animation.fx_hollow_staff_tide.play");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    /** What the crest caught: dragged back with it. */
    private final Map<UUID, Integer> caught = new LinkedHashMap<>();

    public HollowStaffTideEntity(EntityType<? extends HollowStaffTideEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public HollowStaffTideEntity(Level level, LivingEntity owner) {
        this(FFEntities.HOLLOW_STAFF_TIDE.get(), level);
        this.ownerId = owner.getUUID();
        Vec3 floor = HollowStaffBellEntity.floorUnder(level, owner.position().add(0.0D, 0.2D, 0.0D));
        moveTo(owner.getX(), floor.y, owner.getZ(), 0.0F, 0.0F);
    }

    /** Where the crest is at tick t, in blocks from the middle (-1 before it runs). */
    public static double radius(double t) {
        if (t < WELL) {
            return -1.0D;
        }
        if (t <= OUT) {
            return R0 + (R1 - R0) * (t - WELL) / (OUT - WELL);
        }
        if (t <= STAND) {
            return R1;
        }
        if (t <= PULL) {
            return R1 + (RPULL - R1) * (t - STAND) / (PULL - STAND);
        }
        return RPULL;
    }

    @Nullable
    private LivingEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        int t = tickCount;
        LivingEntity owner = owner();
        if (t == WELL) {
            s.playSound(null, getX(), getY(), getZ(), FFSounds.HOLLOW_STAFF_TIDE.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
        }
        if (t >= WELL && t <= OUT) {
            crest(owner, radius(t));
        }
        if (t == STAND) {
            s.playSound(null, getX(), getY(), getZ(), FFSounds.HOLLOW_STAFF_TIDE_PULL.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
        }
        if (t > STAND && t <= PULL) {
            drag(radius(t));
        }
        if (t == PULL) {
            release(owner);
        }
        if (t >= LIFE) {
            discard();
        }
    }

    private void crest(@Nullable LivingEntity owner, double r) {
        AABB reach = getBoundingBox().inflate(R1 + 1.0D, 3.0D, R1 + 1.0D);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, reach,
                e -> HollowStaffMagic.inTheFight(owner, e))) {
            if (caught.containsKey(v.getUUID())) {
                continue;
            }
            double d = Math.hypot(v.getX() - getX(), v.getZ() - getZ());
            double feet = v.getY() - getY();
            if (d > r + BAND || feet < -1.5D || feet > 2.5D) {
                continue;                                   // not reached yet (inside the ring it was struck already)
            }
            caught.put(v.getUUID(), v.getId());
            HollowStaffMagic.strike(v, this, owner, DMG);
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_T, 2), owner);
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, SLOW_T, 0), owner);
        }
    }

    /** Back to the middle with the water: what is outside the ring now is pulled in to it. */
    private void drag(double r) {
        for (int id : caught.values()) {
            Entity e = level().getEntity(id);
            if (!(e instanceof LivingEntity v) || !v.isAlive()) {
                continue;
            }
            double dx = getX() - v.getX(), dz = getZ() - v.getZ();
            double d = Math.hypot(dx, dz);
            if (d < 0.6D || d < r - 0.4D) {
                continue;
            }
            double pull = Math.min(0.8D, (d - r) * 0.55D + 0.35D);
            Vec3 m = v.getDeltaMovement();
            v.setDeltaMovement(dx / d * pull, Math.max(m.y, 0.06D), dz / d * pull);
            v.hurtMarked = true;
        }
    }

    private void release(@Nullable LivingEntity owner) {
        for (int id : caught.values()) {
            Entity e = level().getEntity(id);
            if (!(e instanceof LivingEntity v) || !v.isAlive()) {
                continue;
            }
            if (HollowStaffMagic.marked(v)) {
                HollowStaffMagic.hold(v, HOLD_T);                 // the marked are held where the water left them
            } else {
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), owner);
            }
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
        controllers.add(new AnimationController<>(this, "tide", 0, state -> state.setAndContinue(PLAY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
