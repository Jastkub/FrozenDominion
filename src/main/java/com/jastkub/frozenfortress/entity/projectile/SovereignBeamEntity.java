package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Hollow Winter beam, in a mortal's hands.
 *
 * <p>Unlike an ordinary projectile this does not travel: it is a line that
 * exists for as long as the wielder keeps breathing out, anchored to their eye
 * and sweeping wherever they look. The entity carries only the bookkeeping -
 * how long the line reaches before it meets stone, and who is owed the kills.
 */
public class SovereignBeamEntity extends Entity {

    private static final EntityDataAccessor<Integer> OWNER_ID =
            SynchedEntityData.defineId(SovereignBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LENGTH =
            SynchedEntityData.defineId(SovereignBeamEntity.class, EntityDataSerializers.FLOAT);

    /** Ticks of gathering cold before the beam actually bites. */
    public static final int CHARGE = 12;
    /** Ticks the beam burns for once it opens. */
    public static final int DURATION = 60;

    private static final double RANGE = 42.0D;
    private static final double RADIUS = 1.1D;
    private static final int HIT_INTERVAL = 5;
    private static final float DAMAGE = 17.25F;

    /** Per-victim grace so a swept target is not hit every single tick. */
    private final Map<UUID, Integer> hitCooldowns = new HashMap<>();

    public SovereignBeamEntity(EntityType<? extends SovereignBeamEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public SovereignBeamEntity(Level level, Player owner) {
        this(FFEntities.SOVEREIGN_BEAM.get(), level);
        setOwnerId(owner.getId());
        Vec3 eye = owner.getEyePosition();
        setPos(eye.x, eye.y, eye.z);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER_ID, -1);
        entityData.define(LENGTH, 0.0F);
    }

    public void setOwnerId(int id) {
        entityData.set(OWNER_ID, id);
    }

    @Nullable
    public Player getOwner() {
        int id = entityData.get(OWNER_ID);
        return id < 0 || !(level().getEntity(id) instanceof Player player) ? null : player;
    }

    public float getLength() {
        return entityData.get(LENGTH);
    }

    /** 0 while gathering, ramping to 1 as the beam opens. */
    public float getOpenness(float partialTick) {
        float t = tickCount + partialTick;
        if (t < CHARGE) {
            return 0.0F;
        }
        float since = t - CHARGE;
        float fadeIn = Math.min(1.0F, since / 4.0F);
        float left = CHARGE + DURATION - t;
        float fadeOut = Math.min(1.0F, Math.max(0.0F, left / 6.0F));
        return fadeIn * fadeOut;
    }

    @Override
    public void tick() {
        super.tick();

        Player owner = getOwner();
        if (owner == null || !owner.isAlive() || tickCount > CHARGE + DURATION) {
            if (!level().isClientSide) {
                discard();
            }
            return;
        }

        // The beam lives at the wielder's eye, on both sides, so the drawn
        // line and the damaging one start from exactly the same place.
        Vec3 eye = owner.getEyePosition();
        setPos(eye.x, eye.y, eye.z);

        Vec3 look = owner.getLookAngle();

        if (level().isClientSide) {
            if (tickCount < CHARGE) {
                spawnGatherParticles(eye, look);
            }
            return;
        }

        double reach = measureReach(owner, eye, look);
        entityData.set(LENGTH, (float) reach);

        hitCooldowns.replaceAll((id, ticks) -> ticks - 1);
        hitCooldowns.entrySet().removeIf(e -> e.getValue() <= 0);

        if (tickCount >= CHARGE) {
            burn(owner, eye, look, reach);
        }
    }

    /** How far the beam gets before stone stops it. */
    private double measureReach(Player owner, Vec3 eye, Vec3 look) {
        Vec3 end = eye.add(look.scale(RANGE));
        HitResult hit = level().clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS ? RANGE : hit.getLocation().distanceTo(eye);
    }

    private void burn(Player owner, Vec3 eye, Vec3 look, double reach) {
        Vec3 end = eye.add(look.scale(reach));
        AABB sweep = new AABB(eye, end).inflate(RADIUS + 0.5D);

        for (Entity entity : level().getEntities(this, sweep)) {
            if (!(entity instanceof LivingEntity victim) || victim == owner || !victim.isAlive()) {
                continue;
            }
            if (hitCooldowns.containsKey(victim.getUUID())) {
                continue;
            }
            // distance from the victim's centre to the beam's axis
            Vec3 toVictim = victim.getBoundingBox().getCenter().subtract(eye);
            double along = Math.max(0.0D, Math.min(reach, toVictim.dot(look)));
            double off = toVictim.subtract(look.scale(along)).length();
            if (off > RADIUS + victim.getBbWidth() * 0.5D) {
                continue;
            }

            hitCooldowns.put(victim.getUUID(), HIT_INTERVAL);
            victim.hurt(level().damageSources().indirectMagic(this, owner), DAMAGE);
            victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 120, 0));
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            victim.setTicksFrozen(Math.min(victim.getTicksRequiredToFreeze() + 60,
                    victim.getTicksFrozen() + 80));
        }

        if (level() instanceof ServerLevel server) {
            Vec3 tip = eye.add(look.scale(Math.max(0.0D, reach - 0.5D)));
            server.sendParticles(ParticleTypes.SNOWFLAKE, tip.x, tip.y, tip.z,
                    6, 0.35D, 0.35D, 0.35D, 0.05D);
            for (double d = 1.0D; d < reach; d += 3.0D) {
                Vec3 p = eye.add(look.scale(d));
                server.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z,
                        1, 0.2D, 0.2D, 0.2D, 0.01D);
            }
        }
    }

    private void spawnGatherParticles(Vec3 eye, Vec3 look) {
        Vec3 mouth = eye.add(look.scale(1.0D));
        for (int i = 0; i < 4; i++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double r = 1.6D * (1.0D - tickCount / (double) CHARGE) + 0.4D;
            double px = mouth.x + Math.cos(a) * r;
            double py = mouth.y + (random.nextDouble() - 0.5D) * 2.0D * r;
            double pz = mouth.z + Math.sin(a) * r;
            level().addParticle(ParticleTypes.SNOWFLAKE, px, py, pz,
                    (mouth.x - px) * 0.25D, (mouth.y - py) * 0.25D, (mouth.z - pz) * 0.25D);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
