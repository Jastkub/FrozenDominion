package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The spire he rides out of the floor when the greatsword comes apart.
 *
 * <p>WHY IT IS AN ENTITY AND NOT BLOCKS. Building it out of ice blocks would
 * put a permanent eight-block column in the middle of somebody's arena, leave
 * a hole in the floor when it went, and hand the player a ladder - and it
 * could not grow, which is the only part of it that matters. An entity can
 * rise out of the ground in a second and a half, hold, and come apart.
 *
 * <p>It carries its own clock and nothing else: it rises, it stands for as
 * long as it was told to, and it shatters. The boss riding it is the boss's
 * business - this thing does not know he is there, which means an interrupt
 * that kills him mid-sequence leaves a spire that tidies itself up rather than
 * a spire waiting for a signal that is never coming.
 */
public class IceTowerEntity extends Entity implements GeoEntity {

    /** 0..1 - how far out of the floor it is. */
    private static final EntityDataAccessor<Float> RISEN =
            SynchedEntityData.defineId(IceTowerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(IceTowerEntity.class, EntityDataSerializers.INT);
    /**
     * How much of its full height this one is allowed, 0 to 1.
     *
     * <p>THE ROOM DECIDES. The spire is eight and a half blocks and the king
     * standing on it is another four, so it wants thirteen blocks of clear
     * air - and this fight happens INDOORS. Under a lower ceiling the whole
     * top of the sequence went inside the stonework: his head in the roof, and
     * every bolt he fired spawning inside a block, where a spear plants on the
     * tick it is created. That is the "he pretends to shoot" exactly - the
     * bolts were real, they just never got to be in the air.
     *
     * <p>So the tower takes what the room gives it. Short is a worse picture
     * than tall; short is a much better picture than clipped through the
     * ceiling with the attack silently not happening.
     */
    private static final EntityDataAccessor<Float> HEIGHT =
            SynchedEntityData.defineId(IceTowerEntity.class, EntityDataSerializers.FLOAT);

    /** Ticks to come up, and to sink back. */
    public static final int RISE = 30;
    public static final int SINK = 22;
    /** The platform's height above the base, in blocks - see the model. */
    public static final double TOP = 8.4D;

    public IceTowerEntity(EntityType<? extends IceTowerEntity> type, Level level) {
        super(type, level);
        // DRAWN FAR OUTSIDE ITS OWN HITBOX, so it must not be frustum culled.
        // The renderer paints a spire that grows past its own box from an entity whose type
        // declares half a block; the game culls against the declared box, so
        // the effect vanished whenever its centre point left the screen -
        // which, for something lying on the floor, is most of a boss fight.
        this.noCulling = true;
        this.noPhysics = true;
    }

    public IceTowerEntity(Level level, double x, double y, double z, int life) {
        this(level, x, y, z, life, 1.0F);
    }

    public IceTowerEntity(Level level, double x, double y, double z, int life,
                          float height) {
        this(FFEntities.ICE_TOWER.get(), level);
        setPos(x, y, z);
        entityData.set(LIFE, life);
        entityData.set(HEIGHT, height);
    }

    /** How much of its full height this one gets. See HEIGHT. */
    public float height() {
        return entityData.get(HEIGHT);
    }

    public float risen() {
        return entityData.get(RISEN);
    }

    /** Where the platform is right now, so the rider can be put on it. */
    public double platformY() {
        return getY() + TOP * risen() * height();
    }

    /** (client) the rise of the last two ticks, so it is drawn smooth between the server's steps. */
    private float risenO, risenNow;

    /** (client) the rise as drawn this frame - the spire's scale and the platform under the man on it both read it. */
    public float risen(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, risenO, risenNow);
    }

    /** (client) the platform where it is drawn this frame. See VelkharRenderer.onTower. */
    public double platformY(float partialTick) {
        return getY() + TOP * risen(partialTick) * height();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            risenO = tickCount <= 1 ? risen() : risenNow;
            risenNow = risen();
            if (tickCount < RISE && random.nextInt(2) == 0) {
                level().addParticle(FFParticles.ICE_SHARD.get(),
                        getX() + (random.nextDouble() - 0.5D) * 2.4D,
                        getY() + random.nextDouble() * 1.0D,
                        getZ() + (random.nextDouble() - 0.5D) * 2.4D,
                        0.0D, 0.06D, 0.0D);
            }
            return;
        }
        int life = entityData.get(LIFE);
        float f;
        if (tickCount <= RISE) {
            f = tickCount / (float) RISE;
            f = f * f * (3.0F - 2.0F * f);
        } else if (tickCount > life - SINK) {
            f = Math.max(0.0F, (life - tickCount) / (float) SINK);
        } else {
            f = 1.0F;
        }
        entityData.set(RISEN, f);

        if (level() instanceof ServerLevel serverLevel) {
            if (tickCount == 1) {
                serverLevel.playSound(null, blockPosition(), FFSounds.ICE_GRIND.get(),
                        SoundSource.HOSTILE, 4.0F, 0.45F);
                serverLevel.playSound(null, blockPosition(), FFSounds.VELKHAR_SWORD_PULL.get(),
                        SoundSource.HOSTILE, 4.0F, 0.42F);
            }
            if (tickCount <= RISE && tickCount % 3 == 0) {
                serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                        getX(), getY() + 0.2D, getZ(), 14, 2.0D, 0.15D, 2.0D, 0.22D);
            }
            if (tickCount == life - SINK) {
                serverLevel.playSound(null, blockPosition(), FFSounds.ICE_GRIND.get(),
                        SoundSource.HOSTILE, 4.4F, 0.6F);
                for (int i = 0; i < 6; i++) {
                    serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                            getX(), getY() + TOP * (i / 6.0D), getZ(),
                            26, 1.4D, 0.5D, 1.4D, 0.4D);
                }
            }
        }
        if (tickCount > life) {
            discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 65536.0D;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(RISEN, 0.0F);
        builder.define(LIFE, 200);
        builder.define(HEIGHT, 1.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(HEIGHT, tag.contains("Height") ? tag.getFloat("Height") : 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Life", entityData.get(LIFE));
        tag.putFloat("Height", height());
    }


    private static final RawAnimation STILL = RawAnimation.begin().thenLoop("animation.still");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "still", 0, this::still));
    }

    private <E extends GeoEntity> PlayState still(AnimationState<E> state) {
        return state.setAndContinue(STILL);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
