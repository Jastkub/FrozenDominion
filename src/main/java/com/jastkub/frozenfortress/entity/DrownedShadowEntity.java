package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * HER SHAPE UNDER THE ICE (the Drowned Lady's GRASP FROM BELOW). She has gone down through the floor and this is
 * what is left to see of her: a dark figure, flat as a stain, arms out ahead, hair streaming behind, two pale eyes -
 * gliding at whoever she chose. It is slower than a
 * running player and it cannot pass under a rune plate, so it can be outrun or stood out; when it is on you, or has
 * hunted long enough, her hands come up through the ice where it is (DrownedHandsEntity) - and the burst has its own
 * tell before it grabs.
 *
 * <p>While she sleeps it lies over the place she drowned, still: the first thing anyone sees of her.
 */
public class DrownedShadowEntity extends Entity implements GeoEntity {

    public static final int HUNT = 0, DORMANT = 1, SURFACING = 2;
    /** How long it hunts before the hands come up regardless (her second half: shorter, and it is quicker). */
    static final int HUNT_MAX = 60, HUNT_MAX_TWO = 46;
    /** Blocks a tick: a sprint (0.28) outruns it, a walk does not for long. */
    static final double SPEED = 0.19D, SPEED_TWO = 0.23D;
    /** Close enough to come up under them. */
    static final double REACH = 0.8D;
    static final int SURFACE_T = 10;

    private static final EntityDataAccessor<Integer> MODE =
            SynchedEntityData.defineId(DrownedShadowEntity.class, EntityDataSerializers.INT);

    private static final String P = "animation.drowned_shadow.";
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop(P + "glide");
    private static final RawAnimation STILL = RawAnimation.begin().thenLoop(P + "dormant");
    private static final RawAnimation SURFACE = RawAnimation.begin().thenPlayAndHold(P + "surface");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    @Nullable
    private UUID targetId;
    private int age;

    public DrownedShadowEntity(EntityType<? extends DrownedShadowEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    private DrownedShadowEntity(Level level, DrownedLadyEntity owner, double x, double y, double z, int mode) {
        this(FFEntities.DROWNED_SHADOW.get(), level);
        this.ownerId = owner.getUUID();
        moveTo(x, y, z, owner.getYRot(), 0.0F);
        entityData.set(MODE, mode);
    }

    /** She has gone under: her shape sets off after `target`. */
    public static DrownedShadowEntity hunt(Level level, DrownedLadyEntity owner, LivingEntity target) {
        DrownedShadowEntity s = new DrownedShadowEntity(level, owner, owner.getX(), owner.floorTop(), owner.getZ(), HUNT);
        s.targetId = target.getUUID();
        s.face(target.position().subtract(owner.position()));
        level.addFreshEntity(s);
        level.playSound(null, owner.blockPosition(), FFSounds.DROWNED_LADY_GRASP_TELL.get(), SoundSource.HOSTILE,
                1.6F, 1.0F);
        return s;
    }

    /** Asleep: her shape lies still over the place she drowned. */
    public static DrownedShadowEntity dormant(Level level, DrownedLadyEntity owner, BlockPos home) {
        DrownedShadowEntity s = new DrownedShadowEntity(level, owner, home.getX() + 0.5D, home.getY(),
                home.getZ() + 0.5D, DORMANT);
        level.addFreshEntity(s);
        return s;
    }

    public int mode() {
        return entityData.get(MODE);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        DrownedLadyEntity owner = owner();
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        age++;
        switch (mode()) {
            case DORMANT -> {
                if (!owner.isDormant() && !owner.lurking()) {           // (lurking, she lies still too)
                    entityData.set(MODE, SURFACING);
                    age = 0;
                }
            }
            case SURFACING -> {
                if (age >= SURFACE_T) {
                    discard();
                }
            }
            default -> hunting(owner);
        }
    }

    private void hunting(DrownedLadyEntity owner) {
        LivingEntity target = targetId != null && level() instanceof ServerLevel s
                && s.getEntity(targetId) instanceof LivingEntity le && le.isAlive() ? le : null;
        if (target == null) {
            burst(owner);
            return;
        }
        Vec3 to = new Vec3(target.getX() - getX(), 0.0D, target.getZ() - getZ());
        double d = to.length();
        boolean underThem = d < REACH && owner.onIceFloor(target) && DrownedIcePlateEntity.runeUnder(target) == null;
        if (underThem || age >= (owner.inSecondHalf() ? HUNT_MAX_TWO : HUNT_MAX)) {
            burst(owner);
            return;
        }
        if (d > 1.0E-3D) {
            double step = Math.min(owner.inSecondHalf() ? SPEED_TWO : SPEED, d);
            Vec3 v = to.scale(step / d);
            // under the ice it goes where the ice goes: never under a rune plate, never into a pier or a wall -
            // blocked one way, it slides along the other
            if (!passable(getX() + v.x, getZ() + v.z)) {
                if (passable(getX() + v.x, getZ())) {
                    v = new Vec3(v.x, 0.0D, 0.0D);
                } else if (passable(getX(), getZ() + v.z)) {
                    v = new Vec3(0.0D, 0.0D, v.z);
                } else {
                    v = Vec3.ZERO;
                }
            }
            setPos(getX() + v.x, getY(), getZ() + v.z);
            face(v.lengthSqr() > 1.0E-6D ? v : to);
        }
        if (age % 16 == 8) {                                   // the ice over her creaks as she passes under it
            playSound(FFSounds.DROWNED_LADY_ICE_CRACK.get(), 0.45F, 1.5F + random.nextFloat() * 0.2F);
        }
    }

    private boolean passable(double x, double z) {
        if (DrownedIcePlateEntity.runeAt(level(), x, getY(), z, 0.4D) != null) {
            return false;
        }
        BlockPos at = BlockPos.containing(x, getY() + 0.1D, z);
        return !level().getBlockState(at).isSolidRender(level(), at);
    }

    /** It turns the way it glides (its model's head is its -z, like every creature's). */
    private void face(Vec3 dir) {
        if (dir.horizontalDistanceSqr() < 1.0E-6D) {
            return;
        }
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        yRotO = yaw;
    }

    /** It is on them (or has hunted long enough): her hands come up through the ice here. */
    private void burst(DrownedLadyEntity owner) {
        DrownedHandsEntity.spawn(level(), owner, new Vec3(getX(), getY(), getZ()), getYRot());
        entityData.set(MODE, SURFACING);
        age = 0;
    }

    @Nullable
    private DrownedLadyEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof DrownedLadyEntity d) {
            return d;
        }
        return null;
    }

    public boolean ownedBy(Entity e) {
        return ownerId != null && ownerId.equals(e.getUUID());
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(MODE, HUNT);
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
        return distance < 64.0D * 64.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "shadow", 0, state -> state.setAndContinue(switch (mode()) {
            case DORMANT -> STILL;
            case SURFACING -> SURFACE;
            default -> GLIDE;
        })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
