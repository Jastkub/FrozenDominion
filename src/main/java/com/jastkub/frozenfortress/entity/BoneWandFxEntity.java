package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * THE PICTURES OF THE WAND OF THE DEAD (07.10.2026) - each a model of its own, geo/entity/fx_bone_wand_&lt;kind&gt;
 * (tools/gen_bone_wand.py), drawn by BoneWandRenderers.Fx, placed, sized and gone after its life; a picture may RIDE
 * an entity (drawn at that entity's interpolated position, gone when it dies):
 *
 * <pre>
 *   mark     the COMMAND's mark over the foe the servants were sent at: a skull of green frost, four fangs of bone
 *            pointing down at it                                           rides the foe, loops; COMMAND_T
 *   spur     round each servant the command drove on: a ring of cold green fire at its feet, its flames streaming
 *            back from where it is going                                   rides the servant, loops; while driven
 *   crumble  a servant going to dust: its bones thrown out, splinters of its frost, a ring of rime across the floor
 *                                                                          CRUMBLE_LIFE, where it fell
 * </pre>
 *
 * Particles never stand in for any of these (the grave's ring is its own entity, BoneWandRingEntity).
 */
public class BoneWandFxEntity extends Entity implements GeoEntity {

    public static final String MARK = "mark", SPUR = "spur", CRUMBLE = "crumble";
    /** (tools/gen_bone_wand.py CRUMBLE_T) */
    public static final int CRUMBLE_LIFE = 18;
    /** A life for a picture that ends with what it rides (a spur ends with its servant's command). */
    public static final int UNTIL_GONE = 20 * 60;
    /** Round a servant's feet (a crest over its skull would float off it: its head swings with every clip). */
    public static final float SPUR_Y = 0.04F;
    /** Over a foe's head. */
    public static final float MARK_GAP = 0.55F;

    private static final EntityDataAccessor<String> KIND =
            SynchedEntityData.defineId(BoneWandFxEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(BoneWandFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE =
            SynchedEntityData.defineId(BoneWandFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FOLLOW =
            SynchedEntityData.defineId(BoneWandFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> YOFF =
            SynchedEntityData.defineId(BoneWandFxEntity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BoneWandFxEntity(EntityType<? extends BoneWandFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** A picture of `kind` at `at`, turned to `yaw`, `size` times its model, gone after `life` ticks. */
    public static BoneWandFxEntity spawn(Level level, String kind, Vec3 at, float yaw, float size, int life) {
        BoneWandFxEntity fx = new BoneWandFxEntity(FFEntities.BONE_WAND_FX.get(), level);
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

    /** It rides `e`, `yOff` blocks over its feet. */
    public BoneWandFxEntity follow(Entity e, float yOff) {
        entityData.set(FOLLOW, e.getId());
        entityData.set(YOFF, yOff);
        setPos(e.getX(), e.getY() + yOff, e.getZ());
        return this;
    }

    /** Lives at least `ticks` more from now (a command given again on the same foe). */
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

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(KIND, CRUMBLE);
        builder.define(LIFE, CRUMBLE_LIFE);
        builder.define(SIZE, 1.0F);
        builder.define(FOLLOW, -1);
        builder.define(YOFF, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        int f = entityData.get(FOLLOW);
        if (f >= 0) {
            Entity on = level().getEntity(f);
            boolean gone = on == null || !on.isAlive()
                    || (SPUR.equals(kind()) && on instanceof BoneWandSkeletonEntity s && (s.crumbling() || !s.frenzied()));
            if (gone) {
                if (!level().isClientSide) {
                    discard();
                }
                return;
            }
            setPos(on.getX(), on.getY() + entityData.get(YOFF), on.getZ());
        }
        if (!level().isClientSide && tickCount >= entityData.get(LIFE)) {
            discard();
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
        return distance < 80.0D * 80.0D;
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
            String p = "animation.fx_bone_wand_" + k + ".";
            RawAnimation clip = CRUMBLE.equals(k) ? RawAnimation.begin().thenPlayAndHold(p + "play")
                    : RawAnimation.begin().thenPlay(p + "spawn").thenLoop(p + "loop");
            return state.setAndContinue(clip);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** What it rides, if anything (and it is there). */
    @Nullable
    public Entity rider() {
        int f = entityData.get(FOLLOW);
        return f < 0 ? null : level().getEntity(f);
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
