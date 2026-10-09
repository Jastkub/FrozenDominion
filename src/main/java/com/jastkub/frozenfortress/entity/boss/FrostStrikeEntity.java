package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * A marked circle on the floor that a column of cold comes up through.
 *
 * <p>Built as its own entity rather than as particles inside the boss, which
 * is how Mowzie's Mobs does the same thing for Umvuthi: each one owns its own
 * timer and its own patch of ground, so six of them can be in the air at once
 * chasing a player who is moving, and each still fires exactly where it was
 * drawn. Doing it from the boss tick would have tied every ring to one shared
 * clock and one shared position.
 *
 * <p>Three beats: the ring draws (readable), the column fires (brief), then
 * it fades. Only the middle one hurts.
 */
public class FrostStrikeEntity extends Entity {

    /** How long the warning circle is on the floor before it goes off. */
    public static final int DRAW_TICKS = 26;
    /** How long the column stands. */
    public static final int STRIKE_TICKS = 12;
    /** And how long the scorch hangs around afterwards. */
    public static final int LINGER_TICKS = 14;

    private static final double RADIUS = 1.9D;
    /** Hearts of intent, not raw points - see VelkharEntity.strikeFor. */
    private static final float DAMAGE = 3.8F;

    private UUID ownerUUID;
    private boolean fired;

    public FrostStrikeEntity(EntityType<? extends FrostStrikeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public FrostStrikeEntity(Level level, LivingEntity owner, double x, double y, double z) {
        this(FFEntities.FROST_STRIKE.get(), level);
        this.ownerUUID = owner.getUUID();
        setPos(x, y, z);
        snapToGround();
    }

    /** Drops onto whatever floor is under the spawn point. */
    private void snapToGround() {
        BlockPos probe = blockPosition();
        for (int i = 0; i < 8; i++) {
            if (!level().getBlockState(probe.below()).isAir()) {
                break;
            }
            probe = probe.below();
        }
        setPos(getX(), probe.getY(), getZ());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (tickCount <= DRAW_TICKS) {
            // The read. The ring tightens as the clock runs out, so how long
            // is left is visible at a glance rather than something to count.
            // The ring is geometry too; this is just the frost creeping in
            // under it so the marked ground looks affected.
            if (tickCount % 3 == 0) {
                double t = (double) tickCount / DRAW_TICKS;
                double r = RADIUS * (1.35D - t * 0.35D);
                for (int i = 0; i < 10; i++) {
                    double ang = Math.PI * 2.0D * i / 10 + t * 1.2D;
                    serverLevel.sendParticles(FFParticles.BLIZZARD_FLAKE.get(),
                            getX() + Math.cos(ang) * r, getY() + 0.08D,
                            getZ() + Math.sin(ang) * r, 1, 0.05D, 0.02D, 0.05D, 0.01D);
                }
            }
            if (tickCount == 1) {
                serverLevel.playSound(null, blockPosition(), FFSounds.CRYSTAL_CHIME.get(),
                        SoundSource.HOSTILE, 1.1F, 1.6F);
            }
            return;
        }

        int since = tickCount - DRAW_TICKS;
        if (since <= STRIKE_TICKS) {
            if (!fired) {
                fired = true;
                serverLevel.playSound(null, blockPosition(), FFSounds.SHOCKWAVE.get(),
                        SoundSource.HOSTILE, 2.6F, 1.15F);
                strike(serverLevel);
            }
            // The column itself is geometry now (FrostStrikeRenderer). These
            // are only the shards it throws off, so they read as debris in
            // the beam rather than as the beam.
            for (double h = 0.0D; h < 6.0D; h += 1.2D) {
                serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                        getX(), getY() + h, getZ(), 2, RADIUS * 0.5D, 0.2D, RADIUS * 0.5D, 0.12D);
            }
            return;
        }

        if (since > STRIKE_TICKS + LINGER_TICKS) {
            discard();
            return;
        }
        serverLevel.sendParticles(FFParticles.BLIZZARD_FLAKE.get(),
                getX(), getY() + 0.2D, getZ(), 2, RADIUS * 0.6D, 0.1D, RADIUS * 0.6D, 0.02D);
    }

    private void strike(ServerLevel serverLevel) {
        LivingEntity owner = ownerUUID != null
                && serverLevel.getEntity(ownerUUID) instanceof LivingEntity le ? le : null;
        serverLevel.sendParticles(FFParticles.SHOCKWAVE.get(),
                getX(), getY() + 0.2D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                new AABB(getX() - RADIUS, getY() - 1.0D, getZ() - RADIUS,
                        getX() + RADIUS, getY() + 8.0D, getZ() + RADIUS),
                e -> e.isAlive() && !(e instanceof FrostServantEntity)
                        && !(e instanceof VelkharCloneEntity)
                        && !e.getUUID().equals(ownerUUID)
                        && !com.jastkub.frozenfortress.entity.FFAllies.spares(owner, e))) {
            com.jastkub.frozenfortress.entity.boss.VelkharEntity
                    .strikeFor(owner, victim, DAMAGE);
            victim.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 110, 1), owner);
            victim.push(0.0D, 0.72D, 0.0D);
            victim.hurtMarked = true;
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 9216.0D;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        fired = tag.getBoolean("Fired");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Fired", fired);
    }

}
