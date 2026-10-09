package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * The block of ice a seeker leaves the player standing in.
 *
 * <p>IT IS BREAKABLE, AND THAT IS THE POINT. A hard stun with no counterplay
 * is not a punishment, it is a verdict: the player watches, and a boss with
 * free rein for four seconds decides the fight without them. So the ice has
 * its own health and takes hits from anybody - the prisoner included, because
 * flailing at the walls is the one thing a frozen person would actually do -
 * and it has a hard ceiling on top of that, so the worst case is bounded even
 * when nobody swings.
 *
 * <p>It carries the freeze rather than the seeker doing it: effects applied
 * once and then left would outlive the block if it were broken early, and
 * "I smashed out and I still cannot move" is worse than not being able to
 * smash out at all. The effects are topped up every tick this exists, and stop
 * the moment it does not.
 */
public class IcePrisonEntity extends Entity implements GeoEntity {

    /** 0..1, how far gone the shell is - the renderer cracks it. */
    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(IcePrisonEntity.class, EntityDataSerializers.FLOAT);

    /**
     * How wide and how tall the shell has to be to actually contain somebody.
     *
     * <p>The model is cut for a player, and a player is not what this fight is
     * full of. Dropped on an iron golem the block came up to its knees, which
     * reads as the golem standing in a bucket rather than as a golem frozen -
     * and a prison that does not look like it contains its prisoner is not
     * doing the one job it has.
     *
     * <p>Synced rather than derived on the client, because the victim can be
     * out of tracking range of somebody who can see the block.
     */
    private static final EntityDataAccessor<Float> FIT_W =
            SynchedEntityData.defineId(IcePrisonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FIT_H =
            SynchedEntityData.defineId(IcePrisonEntity.class, EntityDataSerializers.FLOAT);

    /** What the geometry was drawn around: a player, near enough. */
    private static final float MODEL_W = 0.62F;
    private static final float MODEL_H = 1.85F;

    /** Hits to break out. Three is about a second of swinging. */
    private static final float SHELL_HEALTH = 3.0F;
    /** And it never holds longer than this, however badly the fight is going. */
    private static final int MAX_TICKS = 70;

    private UUID ownerUUID;
    private UUID heldUUID;
    private float broken;

    public IcePrisonEntity(EntityType<? extends IcePrisonEntity> type, Level level) {
        super(type, level);
        // DRAWN FAR OUTSIDE ITS OWN HITBOX, so it must not be frustum culled.
        // The renderer paints a shell scaled to its victim from an entity whose type
        // declares half a block; the game culls against the declared box, so
        // the effect vanished whenever its centre point left the screen -
        // which, for something lying on the floor, is most of a boss fight.
        this.noCulling = true;
        this.noPhysics = true;
    }

    public IcePrisonEntity(Level level, LivingEntity owner, LivingEntity held) {
        this(FFEntities.ICE_PRISON.get(), level, owner, held);
    }

    /** One of another shape holding the same way (the Rimeweaver's binding). */
    protected IcePrisonEntity(EntityType<? extends IcePrisonEntity> type, Level level, LivingEntity owner,
                              LivingEntity held) {
        this(type, level);
        this.ownerUUID = owner == null ? null : owner.getUUID();
        this.heldUUID = held.getUUID();
        setPos(held.getX(), held.getY(), held.getZ());
        // a little larger than the prisoner in both directions, so they are
        // INSIDE it rather than pressed against the glass - and floored at one,
        // because a shell smaller than the model it was drawn for looks like a
        // mistake even on something tiny
        entityData.set(FIT_W, Math.max(1.0F, held.getBbWidth() / MODEL_W * 1.12F));
        entityData.set(FIT_H, Math.max(1.0F, held.getBbHeight() / MODEL_H * 1.08F));
        refreshDimensions();
    }

    public float shellDamage() {
        return entityData.get(DAMAGE);
    }

    public float fitWidth() {
        return entityData.get(FIT_W);
    }

    public float fitHeight() {
        return entityData.get(FIT_H);
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(
            net.minecraft.world.entity.Pose pose) {
        // the hitbox grows with the shell, or a big prisoner's block could not
        // be hit where it visibly is
        return super.getDimensions(pose).scale(fitWidth(), fitHeight());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(3) == 0) {
                level().addParticle(FFParticles.SOUL_FROST.get(),
                        getX() + (random.nextDouble() - 0.5D) * 1.2D,
                        getY() + random.nextDouble() * 1.8D,
                        getZ() + (random.nextDouble() - 0.5D) * 1.2D, 0.0D, 0.0D, 0.0D);
            }
            return;
        }

        LivingEntity held = heldUUID != null && level() instanceof ServerLevel sl
                && sl.getEntity(heldUUID) instanceof LivingEntity le && le.isAlive() ? le : null;
        if (held == null || tickCount > maxTicks()) {
            crack();
            return;
        }

        // sits on them, and PINS them: the effects alone leave a sprint-jump
        // that carries a player most of a block, which reads as the freeze
        // not working rather than as a clever escape
        setPos(held.getX(), held.getY(), held.getZ());
        held.setDeltaMovement(0.0D, Math.min(0.0D, held.getDeltaMovement().y), 0.0D);
        held.hurtMarked = true;
        if (tickCount % 10 == 0) {
            held.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 6));
            held.addEffect(new MobEffectInstance(MobEffects.JUMP, 20, 128));
            held.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 40, 1));
        }
        if (tickCount % 6 == 0 && level() instanceof ServerLevel s2) {
            s2.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY() + 0.9D, getZ(), 3, 0.5D, 0.8D, 0.5D, 0.02D);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        // his own blows do not free anybody
        if (source.getEntity() != null && ownerUUID != null
                && source.getEntity().getUUID().equals(ownerUUID)) {
            return false;
        }
        broken += 1.0F;
        entityData.set(DAMAGE, Math.min(1.0F, broken / SHELL_HEALTH));
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, blockPosition(), FFSounds.ICE_CRACK.get(),
                    SoundSource.HOSTILE, 1.6F, 0.9F + broken * 0.25F);
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY() + 1.0D, getZ(), 18, 0.4D, 0.6D, 0.4D, 0.24D);
        }
        if (broken >= SHELL_HEALTH) {
            crack();
        }
        return true;
    }

    /** It comes apart, and whoever was in it is released on the same tick. */
    private void crack() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, blockPosition(), FFSounds.ICE_SHATTER.get(),
                    SoundSource.HOSTILE, 2.2F, 0.7F);
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY() + 0.9D, getZ(), 70, 0.6D, 0.9D, 0.6D, 0.45D);
            if (heldUUID != null && serverLevel.getEntity(heldUUID) instanceof LivingEntity held) {
                held.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                held.removeEffect(MobEffects.JUMP);
                held.setTicksFrozen(0);
            }
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    /**
     * VISIBLE TO A SWORD AND TO A CLICK, INVISIBLE TO A PROJECTILE.
     *
     * <p>Forge splits the two questions that vanilla's isPickable answers at
     * once: isPickable still decides whether a player can look at this and hit
     * it, and canBeHitByProjectile decides whether somebody else's arrow, bolt
     * or bomb may select it as the thing it just struck. Saying no to the
     * second costs nothing here - it is broken by hitting it - and it takes this
     * entity out of every other mod's projectile hit scan.
     *
     * <p>Which is the point. LegendaryMonsters' annihilation bomb casts
     * whatever it hits straight to LivingEntity with no instanceof, so hitting
     * anything pickable that is not alive takes the server down - a vanilla
     * ghast fireball would do it too. That cast is theirs to fix and cannot be
     * fixed from here, so instead nothing of mine is left lying in its path.
     */
    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }


    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 6400.0D;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DAMAGE, 0.0F);
        entityData.define(FIT_W, 1.0F);
        entityData.define(FIT_H, 1.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        broken = tag.getFloat("Broken");
        if (tag.contains("FitW")) {
            entityData.set(FIT_W, tag.getFloat("FitW"));
            entityData.set(FIT_H, tag.getFloat("FitH"));
        }
        entityData.set(DAMAGE, Math.min(1.0F, broken / SHELL_HEALTH));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Broken", broken);
        tag.putFloat("FitW", entityData.get(FIT_W));
        tag.putFloat("FitH", entityData.get(FIT_H));
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.still");

    /** The longest it holds anyone. */
    protected int maxTicks() {
        return MAX_TICKS;
    }

    protected RawAnimation idleAnimation() {
        return IDLE;
    }
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, this::idleAnim));
    }

    private <E extends GeoEntity> PlayState idleAnim(AnimationState<E> state) {
        return state.setAndContinue(idleAnimation());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
