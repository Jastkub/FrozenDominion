package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Velkhar's greatsword, hurled spinning across the arena. It flies out,
 * hangs for a heartbeat, then returns to its master's hand - cutting
 * everything on both passes.
 */
public class ThrownBladeEntity extends Projectile implements GeoEntity {

    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.thrown_blade.spin");
    private static final RawAnimation STUCK = RawAnimation.begin().thenLoop("animation.thrown_blade.stuck");

    /** Flying, or standing in the floor. Synced: the renderer needs it. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> PLANTED =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    ThrownBladeEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    /** How long it stands there before it goes back to being cold air. */
    public static final int STUCK_TICKS = 34;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Integer> hitCooldowns = new HashMap<>();

    /**
     * HEARTS OF INTENT, the same currency every other weapon of his uses.
     *
     * <p>This used to be 200 and the comment beside it read "four hearts
     * through a full set" - a raw-points figure, back when the hit was dealt
     * with a bare hurt(). It goes through VelkharEntity.strikeFor now, so the
     * number means what DMG_BLADE_THROW meant all along. The default only ever
     * applies to a blade restored from NBT with no Damage tag.
     */
    private float damage = 2.8F;
    /**
     * How much the throw sags per tick, in blocks.
     *
     * <p>A third of real gravity. The flight is twenty-six ticks; at 0.08 the
     * blade ends about two and a half blocks below the line it left on, which
     * reads as a heavy thing thrown hard. At full gravity it would land at his
     * feet, and at nothing it is a laser.
     */
    private static final double ARC_DROP = 0.028D;
    private int outboundTicks = 14;
    private int plantedAt = -1;

    public ThrownBladeEntity(EntityType<? extends ThrownBladeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public ThrownBladeEntity(Level level, LivingEntity owner, Vec3 direction, float damage, int outboundTicks) {
        this(FFEntities.THROWN_BLADE.get(), level);
        setOwner(owner);
        setPos(owner.getX(), owner.getEyeY() - 0.4D, owner.getZ());
        setDeltaMovement(direction.normalize().scale(1.1D));
        this.damage = damage;
        this.outboundTicks = outboundTicks;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            if (isPlanted()) {
                // frost coming off it while it waits, and nothing else
                if (tickCount % 3 == 0) {
                    level().addParticle(FFParticles.SOUL_FROST.get(),
                            getX() + (random.nextDouble() - 0.5D) * 0.6D,
                            getY() + random.nextDouble() * 2.6D,
                            getZ() + (random.nextDouble() - 0.5D) * 0.6D,
                            0.0D, 0.01D, 0.0D);
                }
            } else {
                level().addParticle(FFParticles.FROST_SWIRL.get(),
                        getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            }
            return;
        }

        if (isPlanted()) {
            // STANDING IN THE FLOOR. It is not a threat any more; it is a
            // thing the player can look at while its owner reaches for a
            // replacement, and then it goes.
            if (tickCount - plantedAt > STUCK_TICKS) {
                if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    sl.sendParticles(FFParticles.ICE_SHARD.get(),
                            getX(), getY() + 1.4D, getZ(), 40, 0.3D, 1.2D, 0.3D, 0.25D);
                    sl.playSound(null, blockPosition(), FFSounds.ICE_SHATTER.get(),
                            net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 1.3F);
                }
                discard();
            }
            return;
        }

        // It turns over the whole way out, so the whirl repeats rather than
        // playing once at the throw.
        if (tickCount % 7 == 0) {
            playSound(FFSounds.VELKHAR_BLADE_WHIRL.get(), 1.1F, 1.0F);
        }

        Entity owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }

        // --- IT LANDS. Either it has run out of flight or it has met a wall.
        net.minecraft.core.BlockPos ahead = net.minecraft.core.BlockPos.containing(
                position().add(getDeltaMovement()));
        boolean blocked = !level().getBlockState(ahead)
                .getCollisionShape(level(), ahead).isEmpty();
        if (tickCount > outboundTicks || blocked) {
            plant();
            return;
        }

        // IT IS THROWN, NOT FIRED. A two-block greatsword travelling in a dead
        // straight line at a fixed height is a laser with a sword texture on
        // it - there is no weight in the path, and weight is the only thing
        // this attack has to sell. So it arcs: flat and fast out of his hand,
        // sagging as it runs out, which also makes the range readable. Stand
        // far enough back and you watch it drop short in front of you instead
        // of guessing whether it will reach.
        //
        // Deliberately gentler than real gravity - 0.08 a tick against 0.08
        // per tick squared - because the flight is only twenty-six ticks and
        // anything heavier turns a throw into a lob.
        setDeltaMovement(getDeltaMovement().add(0.0D, -ARC_DROP, 0.0D));
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        // and it tips as it falls, so the blade follows the path it is on
        setXRot(getXRot() - (float) (getDeltaMovement().y * 14.0D));

        hitCooldowns.replaceAll((id, cd) -> cd - 1);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(1.2D),
                e -> e != owner && e.isAlive() && !(e instanceof FrostServantEntity)
                        && !(e instanceof VelkharCloneEntity
                        && !com.jastkub.frozenfortress.entity.FFAllies.spares(owner, e)))) {
            if (hitCooldowns.getOrDefault(victim.getUUID(), 0) > 0) {
                continue;
            }
            hitCooldowns.put(victim.getUUID(), 20);
            // HEARTS, NOT RAW POINTS - and it was raw points.
            //
            // The caller hands this DMG_BLADE_THROW, which lives in the block
            // of constants beside DMG_COMBO and DMG_CHARGE, and every one of
            // those is hearts of intent: VelkharEntity.strikeFor multiplies by
            // sixteen and then guarantees a floor. Feeding 2.8 straight into
            // hurt() instead meant a two-block greatsword flung the length of
            // the hall landed 2.8 RAW - about six tenths of a point through
            // endgame plate. The heaviest-looking thing he does was the
            // weakest thing in the fight by an order of magnitude.
            //
            // Through strikeFor it is worth what its constant always said:
            // 5.6 points, the same as a shield bash.
            com.jastkub.frozenfortress.entity.boss.VelkharEntity.strikeFor(
                    owner instanceof LivingEntity le2 ? le2 : null, victim, damage);
            victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 80, 0));
            playSound(FFSounds.VELKHAR_IMPACT.get(), 1.0F, 1.2F);

            // IT SHOULD MOVE THEM. A greatsword flung across a hall that
            // deals damage and leaves its victim standing exactly where they
            // were reads as a scripted tick of health loss, not as an object
            // with mass hitting a person. The shove is along the blade's OWN
            // travel, not away from Velkhar - it is the sword doing this, and
            // he may be nowhere near.
            Vec3 flight = getDeltaMovement();
            double speed = flight.horizontalDistance();
            if (speed > 1.0E-3D) {
                Vec3 shove = flight.normalize().scale(0.62D);
                victim.setDeltaMovement(victim.getDeltaMovement()
                        .add(shove.x, 0.34D, shove.z));
                victim.hurtMarked = true;
                if (victim instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.connection.send(new net.minecraft.network.protocol.game
                            .ClientboundSetEntityMotionPacket(sp));
                }
            }
            // AND IT SHOULD SHATTER. The blade is ice; hitting something is
            // the one moment it is allowed to come apart. Thrown along the
            // flight so the spray goes THROUGH the victim rather than
            // blooming symmetrically around them, which is what makes it read
            // as a strike instead of an explosion.
            if (level() instanceof net.minecraft.server.level.ServerLevel sl3) {
                Vec3 at = victim.position().add(0.0D, victim.getBbHeight() * 0.55D, 0.0D);
                Vec3 dir = speed > 1.0E-3D ? flight.normalize() : new Vec3(0, 0, 1);
                sl3.sendParticles(FFParticles.ICE_SHARD.get(),
                        at.x, at.y, at.z, 26, 0.30D, 0.34D, 0.30D, 0.34D);
                for (int i = 0; i < 14; i++) {
                    double spread = (i / 13.0D - 0.5D) * 0.8D;
                    sl3.sendParticles(FFParticles.FROST_SWIRL.get(),
                            at.x + dir.x * 0.7D + spread, at.y + spread * 0.5D,
                            at.z + dir.z * 0.7D + spread, 1, 0.10D, 0.10D, 0.10D, 0.14D);
                }
                playSound(FFSounds.ICE_SHATTER.get(), 2.2F, 1.05F);
            }
        }

        if (tickCount % 8 == 0) {
            playSound(FFSounds.VELKHAR_SWING.get(), 0.8F, 1.4F);
        }
    }

    /**
     * Drives it point-first into whatever is under it.
     *
     * <p>Snapped down to the floor rather than left where it stopped: a
     * greatsword hanging in mid-air at chest height reads as a bug, and the
     * whole point of it landing is that the player can walk over and stand
     * next to it.
     */
    private void plant() {
        net.minecraft.core.BlockPos.MutableBlockPos probe =
                new net.minecraft.core.BlockPos.MutableBlockPos(
                        getBlockX(), getBlockY(), getBlockZ());
        double floor = getY();
        for (int drop = 0; drop < 24; drop++) {
            probe.setY(getBlockY() - drop);
            if (!level().getBlockState(probe).getCollisionShape(level(), probe).isEmpty()) {
                floor = probe.getY() + 1.0D;
                break;
            }
        }
        setPos(getX(), floor, getZ());
        setDeltaMovement(Vec3.ZERO);
        entityData.set(PLANTED, true);
        plantedAt = tickCount;
        playSound(FFSounds.VELKHAR_IMPACT.get(), 3.0F, 0.75F);
        playSound(FFSounds.ICE_IMPACT.get(), 2.4F, 0.9F);
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY() + 0.2D, getZ(), 34, 0.6D, 0.2D, 0.6D, 0.28D);
            sl.sendParticles(FFParticles.SHOCKWAVE.get(),
                    getX(), getY() + 0.15D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            com.jastkub.frozenfortress.event.FloorScarHandler.tear(
                    sl, position(), 1.8D, 0.8D);
        }
    }

    public boolean isPlanted() {
        return entityData.get(PLANTED);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PLANTED, false);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        entityData.set(PLANTED, tag.getBoolean("Planted"));
        plantedAt = tag.getInt("PlantedAt");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putBoolean("Planted", isPlanted());
        tag.putInt("PlantedAt", plantedAt);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0,
                state -> state.setAndContinue(isPlanted() ? STUCK : SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
