package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
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
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * What he leaves standing where he was.
 *
 * <p>A blink that simply moves the model is over before the eye registers
 * which direction it went, so the fight loses the one piece of information
 * that makes teleporting readable: where he WAS. This holds his silhouette in
 * place for three quarters of a second and lets it go out, and the phase it
 * was cast in travels with it so the ghost is wearing the right armour.
 */
public class VelkharAfterimageEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Byte> PHASE =
            SynchedEntityData.defineId(VelkharAfterimageEntity.class, EntityDataSerializers.BYTE);

    /**
     * Long enough to be seen, short enough never to be mistaken for him.
     *
     * <p>Was fifteen ticks, which is three quarters of a second - and a blink
     * is a moment when the camera is somewhere else, so most of that was spent
     * off screen. At twenty-six the player has time to look back at where he
     * was, which is the entire point of leaving something there.
     */
    public static final int LIFETIME = 26;

    public VelkharAfterimageEntity(EntityType<? extends VelkharAfterimageEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public VelkharAfterimageEntity(Level level, Vec3 where, float yaw, int phase) {
        this(FFEntities.VELKHAR_AFTERIMAGE.get(), level);
        setPos(where.x, where.y, where.z);
        setYRot(yaw);
        this.yRotO = yaw;
        entityData.set(PHASE, (byte) phase);
    }

    public int getPhase() {
        return entityData.get(PHASE);
    }

    /** 1 the instant it is left, 0 as it goes out. */
    public float fade(float partialTick) {
        float age = Math.min(LIFETIME, tickCount + partialTick);
        return 1.0F - age / LIFETIME;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 2 == 0) {
                level().addParticle(FFParticles.SOUL_FROST.get(),
                        getX() + (random.nextDouble() - 0.5D) * 1.2D,
                        getY() + random.nextDouble() * 3.4D,
                        getZ() + (random.nextDouble() - 0.5D) * 1.2D, 0.0D, 0.02D, 0.0D);
            }
        } else if (tickCount > LIFETIME) {
            discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(PHASE, (byte) 1);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(PHASE, tag.getByte("Phase"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("Phase", entityData.get(PHASE));
    }


    private static final RawAnimation GHOST =
            RawAnimation.begin().thenLoop("animation.velkhar.afterimage");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ghost", 0, this::ghostAnim));
    }

    private <E extends GeoEntity> PlayState ghostAnim(AnimationState<E> state) {
        return state.setAndContinue(GHOST);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
