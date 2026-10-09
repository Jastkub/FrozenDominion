package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.entity.FrostServantEntity;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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
 * One of the three seekers he cuts out of the air, and the ONE attack in the
 * fight whose answer is not a dodge.
 *
 * <p>THE DESIGN PROBLEM THIS IS BUILT AROUND. A homing thing the player is
 * meant to outrun resolves on movement statistics, not on play: faster than
 * them and escape is impossible, slower and the attack is a walk in a circle.
 * In a pack this size a player's speed spans Speed II, soul speed and an
 * elytra, so the same numbers are unbeatable for one build and free for
 * another. Running therefore buys TIME and nothing else - it is relentless and
 * it will arrive - and the actual counter is that a seeker can be destroyed.
 * One hit each. That is an answer every build owns.
 *
 * <p>AND ONCE IT IS ON YOU, IT IS ON YOU. No shaking it off, by design: the
 * cost of ignoring three of these has to be real or nobody turns to fight
 * them. What the player gets instead is the thirty ticks it spends going from
 * blue to white before it opens, which is a warning to their allies and a last
 * chance to kill it off themselves.
 */
public class HunterOrbEntity extends Entity implements GeoEntity {

    /** 0 while it hunts, then 0..1 across the fuse once it has attached. */
    private static final EntityDataAccessor<Float> CHARGE =
            SynchedEntityData.defineId(HunterOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> ATTACHED =
            SynchedEntityData.defineId(HunterOrbEntity.class, EntityDataSerializers.BOOLEAN);

    /** Ticks of bobbing before they set off - the beat that says "these are aimed". */
    private static final int HOVER_TICKS = 24;
    /** Four seconds of hunting, agreed rather than assumed - see VelkharEntity. */
    private static final int HUNT_TICKS = 80;
    /** A second and a half from contact to detonation. */
    private static final int FUSE_TICKS = 30;
    /**
     * Blocks a tick. Deliberately under a sprinting player (about 0.28) so
     * running is worth doing, and deliberately not much under, so it is worth
     * doing for four seconds rather than forever.
     */
    // 0.265 (0.235 until 06.10.2026): still a hair under a sprint, so running buys time and
    // breaking them is the answer
    private static final double SPEED = 0.265D;
    private static final float BLAST_DAMAGE = 1.6F;

    private UUID ownerUUID;
    private UUID markUUID;
    private int life;
    private int fuse;
    private double bobPhase;

    public HunterOrbEntity(EntityType<? extends HunterOrbEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public HunterOrbEntity(Level level, LivingEntity owner, LivingEntity mark, Vec3 where,
                           double phase) {
        this(FFEntities.HUNTER_ORB.get(), level);
        this.ownerUUID = owner.getUUID();
        this.markUUID = mark == null ? null : mark.getUUID();
        this.bobPhase = phase;
        setPos(where.x, where.y, where.z);
    }

    public float charge() {
        return entityData.get(CHARGE);
    }

    /**
     * 0 to 1 across the first half second, for the renderer's emergence.
     *
     * <p>They used to simply BE there on the tick the wand finished its sweep,
     * which throws away the one beat the gesture was for. Now they are pushed
     * out of it: nothing, then a point, then a seeker - over ten ticks, from
     * the same clock on both sides so it costs nothing on the wire.
     */
    public float birth() {
        return Math.min(1.0F, tickCount / 10.0F);
    }

    public boolean isAttached() {
        return entityData.get(ATTACHED);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // the colour ramp is sold by the particles as much as by the tint
            float c = charge();
            level().addParticle(c > 0.55F ? FFParticles.ICE_SHARD.get()
                            : FFParticles.SOUL_FROST.get(),
                    getX() + (random.nextDouble() - 0.5D) * 0.7D,
                    getY() + (random.nextDouble() - 0.5D) * 0.7D,
                    getZ() + (random.nextDouble() - 0.5D) * 0.7D, 0.0D, 0.0D, 0.0D);
            return;
        }

        // ---- AND IT STILL DIES TO AN ARROW. Dropping out of everyone's
        //      projectile hit scan (see canBeHitByProjectile) would otherwise
        //      quietly delete the cheapest answer to this attack, so the orb
        //      does the looking itself: anything thrown that comes through its
        //      space pops it, exactly as a direct hit used to. Nobody else's
        //      code is involved, so nobody else's code can crash on it.
        if (sweepForProjectiles()) {
            return;
        }

        life++;
        LivingEntity mark = markUUID != null && level() instanceof ServerLevel sl
                && sl.getEntity(markUUID) instanceof LivingEntity le && le.isAlive() ? le : null;

        if (isAttached()) {
            if (mark == null) {
                discard();
                return;
            }
            // RIDES THEM. Position is taken from the mark every tick rather
            // than steered toward it, so there is no gap to run through and
            // nothing for a speed effect to exploit.
            Vec3 on = mark.position().add(0.0D, mark.getBbHeight() * 0.55D, 0.0D);
            double a = bobPhase + life * 0.22D;
            setPos(on.x + Math.cos(a) * 0.55D, on.y + Math.sin(a * 1.7D) * 0.2D,
                   on.z + Math.sin(a) * 0.55D);
            fuse++;
            entityData.set(CHARGE, Math.min(1.0F, fuse / (float) FUSE_TICKS));
            // it ticks faster as it fills, so the last half second is audible
            int every = fuse < FUSE_TICKS / 2 ? 5 : 2;
            if (fuse % every == 0 && level() instanceof ServerLevel s2) {
                s2.playSound(null, blockPosition(), FFSounds.CRYSTAL_CHIME.get(),
                        SoundSource.HOSTILE, 0.9F, 1.1F + fuse / (float) FUSE_TICKS * 0.7F);
            }
            if (fuse >= FUSE_TICKS) {
                detonate(mark);
            }
            return;
        }

        // ---- the hover: they hang and bob, so "three things are aimed at
        //      you" lands before anything moves
        if (life <= HOVER_TICKS) {
            double a = bobPhase + life * 0.18D;
            setPos(getX(), getY() + Math.sin(a) * 0.035D, getZ());
            // THE RIFT THEY CAME OUT OF, closing behind them. Shards fall
            // INWARD to the point they were pushed through, so the first half
            // second reads as a hole in the air being filled rather than as an
            // object fading in.
            if (life <= 10 && level() instanceof ServerLevel born) {
                double shut = 1.0D - life / 10.0D;
                for (int i = 0; i < 4; i++) {
                    double ang = random.nextDouble() * Math.PI * 2.0D;
                    double r = 0.35D + shut * 1.5D;
                    Vec3 at = position().add(Math.cos(ang) * r,
                            (random.nextDouble() - 0.5D) * r, Math.sin(ang) * r);
                    Vec3 pull = position().subtract(at).normalize().scale(0.25D);
                    born.sendParticles(FFParticles.ICE_SHARD.get(),
                            at.x, at.y, at.z, 0, pull.x, pull.y, pull.z, 1.0D);
                }
                born.sendParticles(FFParticles.SOUL_FROST.get(),
                        getX(), getY(), getZ(), 2, 0.12D, 0.12D, 0.12D, 0.03D);
            }
            if (life == HOVER_TICKS && level() instanceof ServerLevel s3) {
                s3.playSound(null, blockPosition(), FFSounds.FROST_BOLT_FIRE.get(),
                        SoundSource.HOSTILE, 1.5F, 0.85F);
            }
            return;
        }

        if (mark == null || life > HOVER_TICKS + HUNT_TICKS) {
            shatter();
            return;
        }

        // ---- the hunt. Straight at them, no arc and no prediction: the
        //      threat has to be legible from behind, and a seeker that curves
        //      cleverly is one the player cannot read over their shoulder.
        Vec3 to = mark.position().add(0.0D, mark.getBbHeight() * 0.55D, 0.0D)
                .subtract(position());
        double gap = to.length();
        if (gap < 0.9D) {
            attach(mark);
            return;
        }
        Vec3 step = to.scale(SPEED / Math.max(0.001D, gap));
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);

        // the rising note, because they chase from BEHIND the camera and a
        // threat the player cannot see needs a distance they can hear
        if (life % 6 == 0 && level() instanceof ServerLevel s4) {
            float near = (float) Math.max(0.0D, 1.0D - gap / 16.0D);
            s4.playSound(null, blockPosition(), FFSounds.FROST_BOLT_FIRE.get(),
                    SoundSource.HOSTILE, 0.5F + near * 0.7F, 1.0F + near * 0.6F);
        }
    }

    private void attach(LivingEntity mark) {
        entityData.set(ATTACHED, true);
        fuse = 0;
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, blockPosition(), FFSounds.ICE_PRISON.get(),
                    SoundSource.HOSTILE, 1.4F, 1.3F);
            serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                    getX(), getY(), getZ(), 24, 0.3D, 0.3D, 0.3D, 0.06D);
        }
    }

    /**
     * It opens, and takes the rest of the flight with it.
     *
     * <p>One detonation ending the others was asked for and it is the thing
     * that keeps this attack honest: three seekers are not three prisons, they
     * are three chances at one. It also means a player who kills two and eats
     * the third is punished exactly as hard as one who ignored all three,
     * which is the wrong lesson - so the two they killed are the two seconds
     * of fuse they bought, and that is where the payment is.
     */
    private void detonate(LivingEntity mark) {
        if (level() instanceof ServerLevel serverLevel) {
            Entity owner = ownerUUID == null ? null : serverLevel.getEntity(ownerUUID);
            VelkharEntity.strikeFor(owner instanceof LivingEntity le ? le : null,
                    mark, BLAST_DAMAGE);
            // the established freeze kit - slowness, no jump, and the vanilla
            // frost overlay - plus the block of ice that carries the timer
            mark.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 6));
            mark.addEffect(new MobEffectInstance(MobEffects.JUMP, 80, 128));
            mark.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 120, 1));
            mark.setTicksFrozen(260);
            IcePrisonEntity prison = new IcePrisonEntity(level(),
                    owner instanceof LivingEntity le2 ? le2 : null, mark);
            serverLevel.addFreshEntity(prison);

            serverLevel.playSound(null, blockPosition(), FFSounds.ICE_PRISON.get(),
                    SoundSource.HOSTILE, 2.4F, 0.8F);
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY(), getZ(), 90, 0.5D, 0.7D, 0.5D, 0.5D);
            serverLevel.sendParticles(FFParticles.SHOCKWAVE.get(),
                    getX(), getY(), getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            // and the others go with it
            for (HunterOrbEntity other : serverLevel.getEntitiesOfClass(HunterOrbEntity.class,
                    getBoundingBox().inflate(48.0D),
                    o -> o != this && o.isAlive()
                            && java.util.Objects.equals(o.ownerUUID, ownerUUID))) {
                other.shatter();
            }
        }
        discard();
    }

    /** Killed, or out of time. Either way it breaks rather than blinking out. */
    public void shatter() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY(), getZ(), 30, 0.3D, 0.3D, 0.3D, 0.32D);
            serverLevel.playSound(null, blockPosition(), FFSounds.FROST_BOLT_HIT.get(),
                    SoundSource.HOSTILE, 1.1F, 1.25F);
        }
        discard();
    }

    // ---- ONE HIT AND IT IS GONE. This is the whole counterplay, so it is
    //      deliberately generous: any damage at all, from anyone, including
    //      the splash off something else.
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
     * second costs nothing here - any damage at all still pops it, and a projectile passing
     * through is caught by the sweep in tick() instead - and it takes this
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
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.getEntity() != null
                && source.getEntity().getUUID().equals(ownerUUID)) {
            return false;       // he cannot pop his own
        }
        shatter();
        return true;
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
        entityData.define(CHARGE, 0.0F);
        entityData.define(ATTACHED, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        life = tag.getInt("Life");
        fuse = tag.getInt("Fuse");
        bobPhase = tag.getDouble("Phase");
        entityData.set(ATTACHED, tag.getBoolean("Attached"));
        entityData.set(CHARGE, tag.getFloat("Charge"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Life", life);
        tag.putInt("Fuse", fuse);
        tag.putDouble("Phase", bobPhase);
        tag.putBoolean("Attached", entityData.get(ATTACHED));
        tag.putFloat("Charge", entityData.get(CHARGE));
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
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

    /** True if something thrown passed through it and broke it. */
    private boolean sweepForProjectiles() {
        for (Entity near : level().getEntities(this, getBoundingBox().inflate(0.35D))) {
            if (!(near instanceof net.minecraft.world.entity.projectile.Projectile shot)) {
                continue;
            }
            Entity thrower = shot.getOwner();
            // his own barrage does not clear his own trap
            if (thrower != null && thrower.getUUID().equals(ownerUUID)) {
                continue;
            }
            if (thrower instanceof VelkharEntity || thrower instanceof VelkharCloneEntity) {
                continue;
            }
            shatter();
            return true;
        }
        return false;
    }
}
