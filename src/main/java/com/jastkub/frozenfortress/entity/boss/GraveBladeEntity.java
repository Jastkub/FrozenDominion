package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A blade of shadow that comes up out of the floor under the player.
 *
 * <p>THE WARNING IS THE ATTACK. It is planted at a SPOT, not at a person: for
 * fifteen ticks there is nothing here but a tightening ring on the ground and a
 * sound, and anyone who leaves beats it outright. Then it comes up fast and
 * whatever is still standing over it goes up with it.
 *
 * <p>That shape is the same one the skyhook uses, and it is deliberate. An
 * attack that reaches out and finds the player wherever they went is not a
 * threat they can answer, it is a tax. An attack committed to a PLACE turns into
 * a question about where to stand, which is the only kind of question a boss
 * fight can really ask.
 *
 * <p>It carries its own damage instead of being a decoration the boss hits
 * through, because it outlives the tick it was spawned on - by the time it
 * erupts the boss has usually moved on, and a hazard whose owner has to still be
 * paying attention to it stops working the moment the fight gets busy.
 */
public class GraveBladeEntity extends Entity {

    /** How far out of the ground it has come, 0 to 1. */
    private static final EntityDataAccessor<Float> RISE =
            SynchedEntityData.defineId(GraveBladeEntity.class, EntityDataSerializers.FLOAT);

    /** Ticks of mark-on-the-floor before it moves at all. */
    public static final int TELL = 15;
    private static final int SURGE = 4;
    private static final int LINGER = 24;
    private static final int LIFETIME = TELL + SURGE + LINGER;

    private static final double CATCH_RADIUS = 1.9D;
    // TWENTY-SIX. It was twelve against a boulder's thirty-two and a thrown
    // sword's thirty-four, for a move with a longer wind-up than either - the
    // odd one out by a factor of nearly three, and reported as hitting for
    // nothing.
    private static final float DAMAGE = 26.0F;

    @Nullable
    private UUID ownerUUID;
    private boolean erupted;

    public GraveBladeEntity(EntityType<? extends GraveBladeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public GraveBladeEntity(Level level, @Nullable LivingEntity owner, Vec3 at) {
        this(FFEntities.GRAVE_BLADE.get(), level);
        setPos(at.x, at.y, at.z);
        if (owner != null) {
            this.ownerUUID = owner.getUUID();
        }
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(RISE, 0.0F);
    }

    /** How far up it is, for the renderer. */
    public float rise() {
        return entityData.get(RISE);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount >= LIFETIME) {
            discard();
            return;
        }
        if (!(level() instanceof ServerLevel server)) {
            return;
        }

        if (tickCount <= TELL) {
            // A RUNE, NOT A CLOUD. A ring of particles says "something here"
            // and nothing else - it has no edge, so the player cannot tell
            // where the safe ground starts, and it looks like every other
            // effect in the fight. The sigil is a drawn shape with a boundary
            // you can stand outside of, which is the whole information the
            // attack owes the player.
            if (tickCount == 1) {
                playSound(FFSounds.VELKHAR_CAST.get(), 2.2F, 0.55F);
                server.addFreshEntity(
                        new com.jastkub.frozenfortress.entity.effect.FloorSigilEntity(
                                level(), getX(), getY() + 0.02D, getZ(),
                                // SIGIL, not RIFT. RIFT is the golem rite's
                                // torn hole in the world - I passed the raw 1
                                // and got a portal, which is exactly what came
                                // back as "a portal instead of a rune". The
                                // named constant says which is which.
                                (float) CATCH_RADIUS * 1.6F, TELL + 6,
                                com.jastkub.frozenfortress.entity.effect
                                        .FloorSigilEntity.SIGIL));
            }
            // a little frost lifting out of it as the clock runs down, so the
            // rune reads as charging rather than as a decal
            if (tickCount % 3 == 0) {
                float close = tickCount / (float) TELL;
                server.sendParticles(FFParticles.SOUL_FROST.get(),
                        getX(), getY() + 0.12D, getZ(), 2 + (int) (close * 5),
                        CATCH_RADIUS * 0.6D, 0.05D, CATCH_RADIUS * 0.6D,
                        0.01D + 0.03D * close);
            }
            if (tickCount == TELL - 4) {
                playSound(FFSounds.CRYSTAL_CHIME.get(), 2.6F, 1.35F);
                server.sendParticles(FFParticles.FROST_SWIRL.get(),
                        getX(), getY() + 0.2D, getZ(), 18, 0.7D, 0.05D, 0.7D, 0.05D);
            }
            return;
        }

        int since = tickCount - TELL;
        if (since <= SURGE) {
            entityData.set(RISE, Math.min(1.0F, since / (float) SURGE));
        }

        if (!erupted) {
            erupted = true;
            playSound(FFSounds.VELKHAR_CLEAVE.get(), 3.8F, 0.7F);
            playSound(FFSounds.VELKHAR_IMPACT.get(), 3.0F, 0.9F);
            server.sendParticles(FFParticles.SHOCKWAVE.get(),
                    getX(), getY() + 0.15D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            server.sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY() + 0.6D, getZ(), 70, 0.5D, 1.0D, 0.5D, 0.6D);
            server.sendParticles(FFParticles.SOUL_FROST.get(),
                    getX(), getY() + 1.8D, getZ(), 50, 0.4D, 1.6D, 0.4D, 0.25D);

            // IT THROWS THEM UP, NOT AWAY. Everything else in the fight pushes
            // outward; this one takes the floor out from under you, which is a
            // different problem and briefly a different game - you cannot roll
            // while you are in the air.
            LivingEntity owner = resolveOwner();
            for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(getX() - CATCH_RADIUS, getY() - 1.0D, getZ() - CATCH_RADIUS,
                             getX() + CATCH_RADIUS, getY() + 3.0D, getZ() + CATCH_RADIUS),
                    e -> e.isAlive() && !(e instanceof VelkharEntity)
                            && !(e instanceof VelkharCloneEntity
                        && !com.jastkub.frozenfortress.entity.FFAllies.spares(owner, e)))) {
                if (victim instanceof Player p && (p.isCreative() || p.isSpectator())) {
                    continue;
                }
                if (ownerUUID != null && victim.getUUID().equals(ownerUUID)) {
                    continue;
                }
                victim.hurt(owner != null
                        ? damageSources().mobAttack(owner)
                        : damageSources().magic(),
                        DAMAGE * com.jastkub.frozenfortress.config.FFConfig.mul(com.jastkub.frozenfortress.config.FFConfig.COMMON.graveBlade));
                victim.setDeltaMovement(victim.getDeltaMovement().x * 0.3D, 1.1D,
                                        victim.getDeltaMovement().z * 0.3D);
                victim.hurtMarked = true;
                victim.fallDistance = 0.0F;
                if (victim instanceof ServerPlayer sp) {
                    sp.connection.send(new net.minecraft.network.protocol.game
                            .ClientboundSetEntityMotionPacket(sp));
                }
            }
        }

        // it sinks back rather than blinking out
        int left = LIFETIME - tickCount;
        if (left < 8) {
            entityData.set(RISE, Math.max(0.0F, left / 8.0F));
        }
    }

    @Nullable
    private LivingEntity resolveOwner() {
        if (ownerUUID != null && level() instanceof ServerLevel server
                && server.getEntity(ownerUUID) instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 4096.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUUID = tag.getUUID("Owner");
        }
        erupted = tag.getBoolean("Erupted");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) {
            tag.putUUID("Owner", ownerUUID);
        }
        tag.putBoolean("Erupted", erupted);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
            getAddEntityPacket(net.minecraft.server.level.ServerEntity serverEntity) {
        return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this, serverEntity);
    }
}
