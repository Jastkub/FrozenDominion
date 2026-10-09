package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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

import java.util.UUID;

/**
 * A slab of the floor torn up and thrown.
 *
 * <p>It hangs in the air while he wrenches it loose, and that hover is the
 * whole tell. Once thrown it breaks on the first thing it touches, so any
 * pillar in the hall will eat it - which is the counterplay.
 */
public class IceBoulderEntity extends Entity implements GeoEntity {

    /** Ticks it hangs before he throws it. */
    public static final int HOLD_TICKS = 22;
    /**
     * How long this one may hang before it gives up, in ticks.
     *
     * <p>IT USED TO BE A CONSTANT, and the constant was wrong for the attack
     * that actually uses this. The four-stone volley tears each boulder out of
     * the floor, holds ALL FOUR up so the player can count them, and only then
     * throws them one at a time - so the first stone waits out its own raise,
     * three more raises, and three throws before its turn comes. That is a
     * hundred and forty ticks for the last one against a sixty-six tick
     * timeout, so every stone in the volley destroyed itself on the spot and
     * the attack looked like four boulders exploding for no reason.
     *
     * <p>The caller knows how long it wants the thing held. The boulder does
     * not, and should not have to guess.
     */
    private int heldLimit = HOLD_TICKS * 3;
    /**
     * Ticks since it was thrown - NOT since it was created.
     *
     * <p>The second half of the same bug, and the one that survives fixing the
     * first. The flight timeout read tickCount, which still counts every tick
     * the stone spent hanging in the air waiting its turn; the last boulder of
     * a volley was already past the limit at the moment it was thrown, so it
     * shattered on the frame it started moving. Exactly "pekaja od razu
     * zamiast leciec".
     */
    private int flightAge;
    /** Bigger stone, bigger bill. It costs him most of two
  * seconds to get it out of the ground, and a hit that
  * does not reflect that makes the wind-up a formality. */
    private static final float DAMAGE = 44.0F;
    private static final double RADIUS = 1.7D;

    private UUID ownerUUID;
    private Vec3 flight = Vec3.ZERO;
    private boolean thrown;

    public IceBoulderEntity(EntityType<? extends IceBoulderEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public IceBoulderEntity(Level level, LivingEntity owner, Vec3 where) {
        this(FFEntities.ICE_BOULDER.get(), level);
        setPos(where.x, where.y, where.z);
        this.ownerUUID = owner.getUUID();
    }

    /** Sends it on its way. Called by Velkhar on the frame he punches it. */
    /** Blocks a tick once thrown. See the note in hurlAt. */
    // 0.66 (0.52 until 06.10.2026): still a rock, not a bullet
    private static final double HURL_SPEED = 0.66D;

    /**
     * Thrown, and deliberately SLOW.
     *
     * <p>Halved from 1.05. A two-block rock crossing twenty blocks in under a
     * second is a bullet, and a bullet made of rock is a contradiction the eye
     * notices even if nobody can name it - weight is the only thing this
     * attack is selling. At half the speed the flight is long enough to see
     * the thing turning, long enough to step out of, and long enough for the
     * four of them to be in the air together and read as four.
     */
    public void hurlAt(Vec3 target) {
        this.flight = target.subtract(position()).normalize().scale(HURL_SPEED);
        this.thrown = true;
        this.flightAge = 0;
    }

    /** How long the caller intends to hold this one before throwing it. */
    public void holdFor(int ticks) {
        this.heldLimit = Math.max(HOLD_TICKS, ticks);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            level().addParticle(FFParticles.ICE_SHARD.get(),
                    getX() + (random.nextDouble() - 0.5D) * 2.0D,
                    getY() + (random.nextDouble() - 0.5D) * 2.0D,
                    getZ() + (random.nextDouble() - 0.5D) * 2.0D, 0.0D, 0.0D, 0.0D);
            return;
        }

        if (!thrown) {
            // Shuddering in place while it is pulled out of the ground.
            if (tickCount > heldLimit) {
                shatter();
                return;
            }
            setPos(getX(), getY() + Math.sin(tickCount * 0.4D) * 0.02D, getZ());
            return;
        }

        setPos(getX() + flight.x, getY() + flight.y, getZ() + flight.z);
        flightAge++;
        if (flightAge > 140 || !level().noCollision(this, getBoundingBox().inflate(0.1D))) {
            shatter();
            return;
        }

        LivingEntity owner = level() instanceof ServerLevel sl && ownerUUID != null
                && sl.getEntity(ownerUUID) instanceof LivingEntity le ? le : null;
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(RADIUS),
                e -> e.isAlive() && !(e instanceof FrostServantEntity)
                        && !(e instanceof VelkharCloneEntity)
                        && !e.getUUID().equals(ownerUUID)
                        && !com.jastkub.frozenfortress.entity.FFAllies.spares(owner, e))) {
            victim.hurt(damageSources().mobAttack(owner),
                    DAMAGE * com.jastkub.frozenfortress.config.FFConfig.mul(com.jastkub.frozenfortress.config.FFConfig.COMMON.iceBoulder));
            victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 120, 1), owner);
            // Being crushed takes your footing: Slowness IV, and in phase three
            // that is the tell that the executioner's blade may follow.
            victim.addEffect(new MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 3), owner);
            Vec3 shove = flight.normalize().scale(1.2D);
            victim.push(shove.x, 0.5D, shove.z);
            victim.hurtMarked = true;
            shatter();
            return;
        }
    }

    /** It comes apart into the shards it was made of. */
    private void shatter() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY(), getZ(), 60, 0.8D, 0.8D, 0.8D, 0.45D);
            serverLevel.sendParticles(FFParticles.FROST_SWIRL.get(),
                    getX(), getY(), getZ(), 24, 0.7D, 0.7D, 0.7D, 0.2D);
            serverLevel.playSound(null, blockPosition(), FFSounds.ICE_SHATTER.get(),
                    SoundSource.HOSTILE, 2.2F, 0.8F);
        }
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        thrown = tag.getBoolean("Thrown");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Thrown", thrown);
    }


    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.still");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, this::idleAnim));
    }

    private <E extends GeoEntity> PlayState idleAnim(AnimationState<E> state) {
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
