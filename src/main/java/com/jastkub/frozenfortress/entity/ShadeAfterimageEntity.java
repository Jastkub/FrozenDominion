package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A SHADOW HE LEAVES BEHIND HIM IN HIS DASH:
 * his own shape in the glide's pose (his "dash_glide" clip), cast in shadow where he was a tick ago, turned the way he
 * went - a string of them is the streak he draws across the hall. Each fades out in {@link #LIFE} ticks
 * (ShadeShepherdFxRenderers.Afterimage). Only something to see: it touches nothing, is never saved.
 */
public class ShadeAfterimageEntity extends Entity implements GeoEntity {

    public static final int LIFE = 12;

    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.shade_shepherd.dash_glide");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ShadeAfterimageEntity(EntityType<? extends ShadeAfterimageEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** One left where `who` is now, facing as it faces. */
    public static void cast(Level level, Entity who) {
        ShadeAfterimageEntity a = new ShadeAfterimageEntity(FFEntities.SHADE_AFTERIMAGE.get(), level);
        a.moveTo(who.getX(), who.getY(), who.getZ(), who.getYRot(), 0.0F);
        a.yRotO = a.getYRot();
        level.addFreshEntity(a);
    }

    /** How far gone it is, 0 just cast to 1 gone (client: its own tick count, from when it came in). */
    public float faded(float partialTick) {
        return Math.min(1.0F, (tickCount + partialTick) / LIFE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= LIFE) {
            discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64.0D * 64.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "glide", 0, state -> state.setAndContinue(GLIDE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
