package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
import java.util.UUID;

/**
 * KULA BURZY - A STORM ORB. A slow ball of caged lightning thrown off the wand (or off a mirror's) that drifts across
 * the arena along the line it was thrown down, bending a little after whoever it was thrown at. Touch it and it bursts
 * on you; drift near it and it licks you with an arc every second or so. It does not stop for gaps between floes: it
 * crosses the air, so it drives people off the ice they are on.
 *
 * <p>Not destroyable (nothing in the storm can be shot out of the air but the anchors and the mirrors): the answer
 * is to read its line and step off it. It fizzles at the cloud wall or after thirteen seconds.
 *
 * <p>GeckoLib model fx_storm_eye_orb (tools/gen_storm_eye.py): a white core in a cage of black-iron ice shards, two
 * rings turning on it; its arcs are StormEyeRenderers.Orb's own geometry.
 */
public class StormEyeOrbEntity extends StormEyeFxEntity implements GeoEntity {

    /** Blocks a tick, how much it bends after its mark, how long it lives. */
    public static final double SPEED = 0.13D, STEER = 0.0035D;
    public static final int LIFE = 260, BURST = 8;
    /** Contact, and the reach of its arcs. */
    static final double TOUCH = 1.35D, ARC_REACH = 3.2D;
    static final float TOUCH_DAMAGE = 26.0F, ARC_DAMAGE = 7.0F;
    static final int ARC_EVERY = 24;

    private static final EntityDataAccessor<Integer> KING =
            SynchedEntityData.defineId(StormEyeOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BURST_AT =
            SynchedEntityData.defineId(StormEyeOrbEntity.class, EntityDataSerializers.INT);

    private Vec3 heading = Vec3.ZERO;
    @Nullable
    private UUID markId;
    /** A mirror's orb hits for less - the copies are a lie, their storm half a lie. */
    private float scale = 1.0F;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StormEyeOrbEntity(EntityType<? extends StormEyeOrbEntity> type, Level level) {
        super(type, level);
    }

    public static StormEyeOrbEntity throwAt(ServerLevel level, VelkharEntity king, Vec3 from, LivingEntity mark,
                                            float scale) {
        StormEyeOrbEntity o = new StormEyeOrbEntity(FFEntities.STORM_EYE_ORB.get(), level);
        o.setPos(from.x, from.y, from.z);
        Vec3 aim = mark.position().add(0.0D, mark.getBbHeight() * 0.55D, 0.0D).subtract(from);
        // level-ish: it drifts ACROSS the arena, it does not dive into the floe
        aim = new Vec3(aim.x, aim.y * 0.35D, aim.z);
        o.heading = aim.lengthSqr() > 1.0E-4D ? aim.normalize().scale(SPEED) : new Vec3(SPEED, 0.0D, 0.0D);
        o.markId = mark.getUUID();
        o.scale = scale;
        o.entityData.set(KING, king.getId());
        level.addFreshEntity(o);
        level.playSound(null, from.x, from.y, from.z, FFSounds.STORM_EYE_ORB.get(), SoundSource.HOSTILE, 2.0F,
                0.9F + level.random.nextFloat() * 0.2F);
        return o;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(KING, -1);
        builder.define(BURST_AT, -1);
    }

    public boolean bursting() {
        return entityData.get(BURST_AT) >= 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (!bursting() && random.nextInt(3) == 0) {
                level().addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        getX() + (random.nextDouble() - 0.5D) * 0.9D, getY() + 0.4D + (random.nextDouble() - 0.5D) * 0.9D,
                        getZ() + (random.nextDouble() - 0.5D) * 0.9D, 0.0D, 0.0D, 0.0D);
            }
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        int burstAt = entityData.get(BURST_AT);
        if (burstAt >= 0) {
            if (tickCount - burstAt > BURST) {
                discard();
            }
            return;
        }
        VelkharEntity king = kingById(sl, entityData.get(KING));
        StormEyeArena arena = king != null ? king.stormEye() : null;
        // ---- it bends a little after its mark, never turns round
        LivingEntity mark = markId != null && sl.getEntity(markId) instanceof LivingEntity l && l.isAlive() ? l : null;
        if (mark != null && tickCount > 10) {
            Vec3 want = mark.position().add(0.0D, mark.getBbHeight() * 0.55D, 0.0D).subtract(position());
            want = new Vec3(want.x, want.y * 0.35D, want.z);
            if (want.lengthSqr() > 1.0E-3D) {
                Vec3 bent = heading.add(want.normalize().scale(STEER));
                if (bent.dot(heading) > 0.0D) {
                    heading = bent.normalize().scale(SPEED);
                }
            }
        }
        if (heading.lengthSqr() < 1.0E-6D) {
            heading = new Vec3(SPEED, 0.0D, 0.0D);
        }
        setPos(getX() + heading.x, getY() + heading.y, getZ() + heading.z);
        // ---- touch
        AABB near = getBoundingBox().inflate(ARC_REACH + 0.5D);
        LivingEntity closest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, near, StormEyeFxEntity::foe)) {
            double d = v.position().add(0.0D, v.getBbHeight() * 0.5D, 0.0D).distanceTo(position().add(0.0D, 0.4D, 0.0D));
            if (d < TOUCH) {
                stormHit(sl, king, v, TOUCH_DAMAGE * scale);
                Vec3 push = v.position().subtract(position());
                push = new Vec3(push.x, 0.0D, push.z);
                push = push.lengthSqr() > 1.0E-4D ? push.normalize().scale(0.45D) : Vec3.ZERO;
                v.setDeltaMovement(v.getDeltaMovement().add(push.x, 0.25D, push.z));
                v.hurtMarked = true;
                burst(sl);
                return;
            }
            if (d < best) {
                best = d;
                closest = v;
            }
        }
        // ---- an arc at whoever drifts too near
        if (closest != null && best < ARC_REACH && (tickCount + getId()) % ARC_EVERY == 0) {
            StormEyeBoltEntity.arc(sl, position().add(0.0D, 0.4D, 0.0D),
                    closest.position().add(0.0D, closest.getBbHeight() * 0.55D, 0.0D), 6, 0.16F, StormEyeBoltEntity.COLD);
            stormHit(sl, king, closest, ARC_DAMAGE * scale);
            sl.playSound(null, getX(), getY(), getZ(), FFSounds.STORM_EYE_SHOCK.get(), SoundSource.HOSTILE, 1.0F, 1.4F);
        }
        // ---- the end of its run
        boolean outside = arena != null
                && position().subtract(arena.centre()).horizontalDistance() > StormEyeArena.WALL_R - 0.8D;
        if (tickCount > LIFE || outside || king == null || !king.isAlive()) {
            burst(sl);
        }
    }

    private void burst(ServerLevel sl) {
        entityData.set(BURST_AT, tickCount);
        sl.playSound(null, getX(), getY(), getZ(), FFSounds.STORM_EYE_ORB_BURST.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
        sl.sendParticles(FFParticles.SOUL_FROST.get(), getX(), getY() + 0.4D, getZ(), 24, 0.4D, 0.4D, 0.4D, 0.12D);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.4D, getZ(),
                20, 0.5D, 0.5D, 0.5D, 0.3D);
    }

    // ---- GeckoLib ---------------------------------------------------------------------------------------------
    private static final RawAnimation SPAWN = RawAnimation.begin().thenPlay("animation.fx_storm_eye_orb.spawn")
            .thenLoop("animation.fx_storm_eye_orb.spin");
    private static final RawAnimation POP = RawAnimation.begin().thenPlayAndHold("animation.fx_storm_eye_orb.burst");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "orb", 0, state ->
                state.setAndContinue(bursting() ? POP : SPAWN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
