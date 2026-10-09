package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.entity.FrostServantEntity;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * AN ICICLE OF A TRAP'S CEILING (IcicleTrapBlockEntity) - its own, apart from the Monstrosity's
 * avalanche (FallingIcicleEntity): the same shadow on the floor first, the same shudder and fall, but a trap's hurt and
 * nothing more - a blow of a falling stalactite, no frost laid on you.
 */
public class TrapIcicleEntity extends FallingIcicleEntity {

    private static final float DAMAGE = 8.0F;

    public TrapIcicleEntity(EntityType<? extends TrapIcicleEntity> type, Level level) {
        super(type, level);
    }

    public TrapIcicleEntity(Level level, double x, double floorY, double z, double height) {
        this(FFEntities.TRAP_ICICLE.get(), level);
        hang(x, floorY, z, height);
    }

    @Override
    protected void burst() {
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        double fy = floorY();
        BlockPos at = BlockPos.containing(getX(), fy, getZ());
        s.playSound(null, at, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 0.6F);
        s.playSound(null, at, FFSounds.ICE_SHATTER.get(), SoundSource.HOSTILE, 1.8F, 0.9F);
        s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), fy + 0.4D, getZ(), 40, 0.8D, 0.3D, 0.8D, 0.25D);
        for (LivingEntity v : s.getEntitiesOfClass(LivingEntity.class,
                new AABB(getX() - RADIUS, fy - 1.0D, getZ() - RADIUS, getX() + RADIUS, fy + 3.0D, getZ() + RADIUS),
                e -> e.isAlive() && !(e instanceof FrostServantEntity))) {
            if (v.distanceToSqr(getX(), v.getY(), getZ()) <= RADIUS * RADIUS) {
                v.hurt(damageSources().fallingStalactite(this), DAMAGE);
            }
        }
        discard();
    }
}
