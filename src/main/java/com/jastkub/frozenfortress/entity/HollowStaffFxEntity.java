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
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * THE PICTURES OF THE STAFF OF THE HOLLOW KING (07.10.2026) - each a model of its own, geo/entity/fx_hollow_staff_&lt;kind&gt;
 * (tools/gen_hollow_staff.py), drawn by HollowStaffRenderers.Fx, placed, sized and gone after its life; a picture may
 * RIDE on an entity (the renderer draws it at that entity's interpolated position):
 *
 * <pre>
 *   burst    a rune breaking on what it struck                       10 t
 *   requiem  Requiem beginning: spikes of ice, a column of light      30 t, at his feet
 *   halo     the crown of light over his head while Requiem lasts     rides him, loops
 *   rise     the pool of dark a marked dead thing rises from          20 t
 *   stun     the toll's knock-down: little bells round a head         rides it, loops; roots it
 *   hold     the Tide's grip round the feet of a marked one           rides it; roots it
 *   sigil    the circle of the chant under his feet                   rides him, loops; ends when he stops chanting
 *   mark     the King's mark, a crown of violet ice over a head       rides it, loops; ends with the mark
 * </pre>
 *
 * Particles never stand in for any of these.
 */
public class HollowStaffFxEntity extends Entity implements GeoEntity {

    public static final String BURST = "burst", REQUIEM = "requiem", HALO = "halo", RISE = "rise", STUN = "stun",
            HOLD = "hold", SIGIL = "sigil", MARK_KIND = "mark";
    private static final Set<String> LOOPED = Set.of(HALO, STUN, SIGIL);

    private static final EntityDataAccessor<String> KIND =
            SynchedEntityData.defineId(HollowStaffFxEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(HollowStaffFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE =
            SynchedEntityData.defineId(HollowStaffFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FOLLOW =
            SynchedEntityData.defineId(HollowStaffFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> YOFF =
            SynchedEntityData.defineId(HollowStaffFxEntity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public HollowStaffFxEntity(EntityType<? extends HollowStaffFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** A picture of `kind` at `at`, turned to `yaw`, `size` times its model, gone after `life` ticks. */
    public static HollowStaffFxEntity spawn(Level level, String kind, Vec3 at, float yaw, float size, int life) {
        HollowStaffFxEntity fx = new HollowStaffFxEntity(FFEntities.HOLLOW_STAFF_FX.get(), level);
        fx.entityData.set(KIND, kind);
        fx.entityData.set(LIFE, life);
        fx.entityData.set(SIZE, size);
        fx.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        fx.setYRot(yaw);
        if (!level.isClientSide) {
            level.addFreshEntity(fx);
        }
        return fx;
    }

    /** It rides on `e`, `yOff` blocks over its feet. */
    public HollowStaffFxEntity follow(Entity e, float yOff) {
        entityData.set(FOLLOW, e.getId());
        entityData.set(YOFF, yOff);
        setPos(e.getX(), e.getY() + yOff, e.getZ());
        return this;
    }

    /** Lives at least `ticks` more from now (a mark refreshed). */
    public void extend(int ticks) {
        entityData.set(LIFE, Math.max(entityData.get(LIFE), tickCount + ticks));
    }

    public String kind() {
        return entityData.get(KIND);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public int followed() {
        return entityData.get(FOLLOW);
    }

    public float yOff() {
        return entityData.get(YOFF);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, BURST);
        entityData.define(LIFE, 10);
        entityData.define(SIZE, 1.0F);
        entityData.define(FOLLOW, -1);
        entityData.define(YOFF, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        int f = entityData.get(FOLLOW);
        if (f >= 0) {
            Entity on = level().getEntity(f);
            if (on == null || !on.isAlive()) {
                if (!level().isClientSide) {
                    discard();
                }
                return;
            }
            setPos(on.getX(), on.getY() + entityData.get(YOFF), on.getZ());
            if (!level().isClientSide) {
                String k = kind();
                if ((HOLD.equals(k) || STUN.equals(k))) {
                    HollowStaffMagic.root(on);
                } else if (SIGIL.equals(k) && !(on instanceof LivingEntity le && com.jastkub.frozenfortress.item.HollowStaffItem.chanting(le))) {
                    discard();
                    return;
                } else if (MARK_KIND.equals(k) && !(on instanceof LivingEntity le && HollowStaffMagic.marked(le))) {
                    discard();
                    return;
                }
            }
        }
        if (!level().isClientSide && tickCount >= entityData.get(LIFE)) {
            discard();
        }
    }

    /** The sigil lives while he chants: every tick of the chant pushes its end on. */
    public void keepAlive(int ticks) {
        extend(ticks);
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
        discard();                                                // a picture does not outlast a reload
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "fx", 0, state -> {
            String k = kind();
            String p = "animation.fx_hollow_staff_" + k + ".";
            RawAnimation clip = MARK_KIND.equals(k) ? RawAnimation.begin().thenPlay(p + "spawn").thenLoop(p + "loop")
                    : LOOPED.contains(k) ? RawAnimation.begin().thenLoop(p + "play")
                    : RawAnimation.begin().thenPlayAndHold(p + "play");
            return state.setAndContinue(clip);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Where it should be drawn this frame (the entity it rides, between ticks), or null if it rides nothing. */
    @Nullable
    public Vec3 riderPosition(float partialTick) {
        int f = entityData.get(FOLLOW);
        if (f < 0) {
            return null;
        }
        Entity on = level().getEntity(f);
        return on == null ? null : on.getPosition(partialTick).add(0.0D, entityData.get(YOFF), 0.0D);
    }
}
