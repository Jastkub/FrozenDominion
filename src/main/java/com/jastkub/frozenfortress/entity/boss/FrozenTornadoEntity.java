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
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A roaming pillar of screaming wind and razor ice. Drifts across the arena,
 * drags victims in, shreds them, and hurls them skyward.
 */
public class FrozenTornadoEntity extends Entity {

    public static final int LIFETIME = 220;
    /**
     * How far the suction reaches, and where the core starts.
     *
     * <p>Both pulled in hard: at the old numbers the grip peaked near 0.44
     * per tick, which is faster than a sprint, so once it had you there was
     * no walking out of it at all. A tornado should cost you your footing,
     * not your agency - it drags, it does not arrest.
     */
    private static final double REACH = 6.0D;
    private static final double CORE = 2.0D;
    /** A second and a half of being thrown around, then out. */
    private static final int HOLD_TICKS = 30;
    /** Ticks before ANY funnel is allowed to grab them again. */
    private static final int THROW_IMMUNITY = 40;

    /** Ticks each victim has spent caught by THIS funnel. */
    private final java.util.Map<Integer, Integer> held = new java.util.HashMap<>();

    /**
     * When each victim stops being immune, by entity id and game time.
     *
     * <p>Static on purpose. Two funnels each kept their own book, so the
     * moment one threw a player the other counted them as a fresh catch and
     * started its own clock - they simply passed the player back and forth
     * and the "second and a half" never applied to the pair. A throw has to
     * be respected by every funnel in the room, not just the one that did it.
     */
    private static final java.util.Map<Integer, Long> IMMUNE_UNTIL = new java.util.HashMap<>();

    @Nullable
    private UUID ownerUUID;
    /** Where it currently is around him, and where it started. */
    private double orbitAngle;
    private double orbitPhase;
    private double orbitRadius = 7.0D;
    private LivingEntity ownerCache;
    private Vec3 wanderDir = Vec3.ZERO;

    public FrozenTornadoEntity(EntityType<? extends FrozenTornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public FrozenTornadoEntity(Level level, @Nullable LivingEntity owner, double x, double y, double z) {
        this(FFEntities.FROZEN_TORNADO.get(), level);
        setPos(x, y, z);
        if (owner != null) {
            this.ownerUUID = owner.getUUID();
            this.ownerCache = owner;
            // Its starting angle is taken from where it was SPAWNED relative to
            // him, not from a counter - so a pair summoned left and right of
            // his shoulders keeps that spacing instead of snapping onto a grid.
            Vec3 off = new Vec3(x, 0.0D, z).subtract(
                    new Vec3(owner.getX(), 0.0D, owner.getZ()));
            if (off.lengthSqr() > 1.0E-4D) {
                this.orbitPhase = Math.atan2(off.z, off.x);
                this.orbitRadius = Math.max(5.0D, Math.min(9.0D, off.length()));
            } else {
                this.orbitPhase = level.random.nextDouble() * Math.PI * 2.0D;
            }
        }
    }

    /** The boss it belongs to, if he is still here. */
    @Nullable
    private LivingEntity ownerEntity() {
        if (ownerCache != null && ownerCache.isAlive()) {
            return ownerCache;
        }
        if (ownerUUID != null && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            net.minecraft.world.entity.Entity found = sl.getEntity(ownerUUID);
            if (found instanceof LivingEntity living) {
                ownerCache = living;
                return living;
            }
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            // Client builds the visual: a spiralling column of flakes and shards.
            for (int i = 0; i < 6; i++) {
                double heightFrac = random.nextDouble();
                double angle = (tickCount * 0.45D) + heightFrac * 12.0D + i;
                double radius = 0.4D + heightFrac * 1.8D;
                level().addParticle(FFParticles.BLIZZARD_FLAKE.get(),
                        getX() + Math.cos(angle) * radius,
                        getY() + heightFrac * 5.0D,
                        getZ() + Math.sin(angle) * radius,
                        -Math.sin(angle) * 0.3D, 0.05D, Math.cos(angle) * 0.3D);
            }
            if (random.nextInt(3) == 0) {
                level().addParticle(FFParticles.ICE_SHARD.get(),
                        getX(), getY() + random.nextDouble() * 4.0D, getZ(),
                        (random.nextDouble() - 0.5D) * 0.4D, 0.1D, (random.nextDouble() - 0.5D) * 0.4D);
            }
            return;
        }

        if (tickCount >= LIFETIME) {
            discard();
            return;
        }

        // THEY ORBIT HIM. They used to drift at the player with a bit of
        // randomness, which made them free-roaming hazards - and a hazard that
        // wanders is a hazard you fight instead of the boss. Circling Velkhar
        // makes them his weather: the space near him costs something, and the
        // decision they force is about approach rather than about them.
        LivingEntity owner = ownerEntity();
        if (owner != null && owner.isAlive()) {
            orbitAngle += 0.026D;
            // Breathing radius, out of phase with the turn, so the ring is
            // never a fixed circle - a rigid orbit reads as machinery.
            double reach = orbitRadius + Math.sin(tickCount * 0.021D + orbitPhase) * 1.6D;
            Vec3 want = owner.position().add(Math.cos(orbitAngle + orbitPhase) * reach,
                                             0.0D,
                                             Math.sin(orbitAngle + orbitPhase) * reach);
            // Steered toward the point with a capped speed rather than placed
            // on it. THIS IS THE WHOLE LOOK: when he moves they lag, swing
            // wide and catch up, so they trail in his wake instead of being
            // bolted to him at a fixed radius.
            Vec3 lead = want.subtract(position());
            double gap = lead.horizontalDistance();
            if (gap > 0.05D) {
                double step = Math.min(0.30D, gap * 0.16D);
                lead = new Vec3(lead.x, 0.0D, lead.z).normalize().scale(step);
                setDeltaMovement(lead.x, -0.08D, lead.z);
            } else {
                setDeltaMovement(0.0D, -0.08D, 0.0D);
            }
        } else {
            // No owner left: they go back to wandering, because a ring around
            // nothing is stranger than weather that has come loose.
            if (tickCount % 20 == 0) {
                Player nearest = level().getNearestPlayer(this, 24.0D);
                Vec3 bias = nearest != null
                        ? nearest.position().subtract(position()).normalize().scale(0.6D)
                        : Vec3.ZERO;
                wanderDir = bias.add((random.nextDouble() - 0.5D) * 0.8D, 0.0D,
                                     (random.nextDouble() - 0.5D) * 0.8D).normalize();
            }
            setDeltaMovement(wanderDir.x * 0.14D, -0.08D, wanderDir.z * 0.14D);
        }
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());

        if (tickCount % 24 == 0) {
            playSound(FFSounds.BLIZZARD_LOOP.get(), 1.2F, 1.1F);
        }

        // Suction, then the ride, then the throw.
        //
        // The pull is deliberately hard to walk out of: it grows the closer
        // you get, and it is set rather than added so knockback resistance
        // cannot ignore it. What it costs you is position, not much health -
        // being thrown across the hall in the middle of his other attacks is
        // the actual danger.
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(REACH, 4.0D, REACH),
                e -> e.isAlive() && !(e instanceof FrostServantEntity) && !(e instanceof VelkharCloneEntity)
                        && !e.getUUID().equals(ownerUUID)
                        // ---- CREATIVE AND SPECTATOR WALK THROUGH IT, by
                        //      default. The grip SETS velocity instead of
                        //      adding it, precisely so knockback resistance
                        //      cannot shrug it off, and the price of that is
                        //      that it also overrides someone who is only
                        //      there to look at the fight. See FFConfig.
                        && !(com.jastkub.frozenfortress.config.FFConfig.tornadoSparesCreative()
                             && e instanceof net.minecraft.world.entity.player.Player p
                             && (p.isCreative() || p.isSpectator()))
                        && !com.jastkub.frozenfortress.entity.FFAllies.spares(owner, e))) {
            double d = Math.max(0.4D, distanceTo(victim));
            Vec3 flat = new Vec3(getX() - victim.getX(), 0.0D, getZ() - victim.getZ());
            if (flat.lengthSqr() < 1.0E-4D) {
                flat = new Vec3(0.01D, 0.0D, 0.0D);
            }
            Vec3 inward = flat.normalize();
            // tangent, so they are carried AROUND the funnel rather than
            // simply stuck to the middle of it
            Vec3 around = new Vec3(-inward.z, 0.0D, inward.x);

            long now = level().getGameTime();
            Long until = IMMUNE_UNTIL.get(victim.getId());
            if (until != null && now < until) {
                continue;       // still flying from the last throw
            }

            // The clock runs from being CAUGHT, not from reaching the middle.
            // Orbiting in the outer draw could otherwise go on forever, which
            // is exactly what being handed between two funnels looked like.
            int since = held.merge(victim.getId(), 1, Integer::sum);
            if (since >= HOLD_TICKS) {
                Vec3 out = inward.reverse().scale(0.95D);
                victim.setDeltaMovement(out.x, 1.4D, out.z);
                victim.hurtMarked = true;
                victim.hurt(damageSources().freeze(), 5.75F);
                if (victim instanceof ServerPlayer sp) {
                    sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                }
                held.remove(victim.getId());
                IMMUNE_UNTIL.put(victim.getId(), now + THROW_IMMUNITY);
                continue;
            }

            if (d > CORE) {
                double grip = 0.07D + (REACH - Math.min(d, REACH)) * 0.022D;
                // Added to what they are already doing rather than replacing
                // it, so running out of the funnel is slow but possible.
                Vec3 drag = inward.scale(grip).add(around.scale(grip * 0.7D));
                Vec3 now3 = victim.getDeltaMovement();
                victim.setDeltaMovement(now3.x * 0.8D + drag.x,
                        Math.max(now3.y, 0.02D), now3.z * 0.8D + drag.z);
                victim.hurtMarked = true;
                if (victim instanceof ServerPlayer sp) {
                    sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                }
            } else {
                // In the core: spun, lifted, and chipped at.
                Vec3 spin = around.scale(0.30D);
                victim.setDeltaMovement(spin.x, 0.26D, spin.z);
                victim.hurtMarked = true;
                if (victim instanceof ServerPlayer sp) {
                    sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                }
                if (tickCount % 12 == 0) {
                    victim.hurt(damageSources().freeze(), 3.45F);
                    victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 1));
                }
            }

        }

        // Old entries would otherwise accumulate for the life of the server.
        if (tickCount % 100 == 0) {
            long now = level().getGameTime();
            IMMUNE_UNTIL.values().removeIf(t -> t < now);
        }
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUUID = tag.getUUID("Owner");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) {
            tag.putUUID("Owner", ownerUUID);
        }
    }

}
