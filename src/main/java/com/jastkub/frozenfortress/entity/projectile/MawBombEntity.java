package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.entity.FrostWaveEntity;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A FROST MAW'S BOMB (FrostCannonBlockEntity) - its own, apart from the Monstrosity's.
 *
 * <p>The same crystal in the same arc, and the same burst to look at and hear - the crest of frost, the shell flying
 * apart - but it lays nothing on anybody and leaves no frozen pool. It HURTS a little and THROWS a little: on a pillar of the Rift that little is a lot. And a
 * hearth burning near where it comes down goes out.
 */
public class MawBombEntity extends IceBombEntity {

    /** A burning hearth this near where it bursts goes out. */
    private static final double DOUSE = 3.0D;
    /** Its blast: how far, how much, and how hard it throws (outward, and a little up). */
    private static final double BLAST = 2.6D;
    private static final float DAMAGE = 4.0F;
    private static final double THROW = 0.45D, LIFT = 0.28D;

    public MawBombEntity(EntityType<? extends MawBombEntity> type, Level level) {
        super(type, level);
    }

    /** Spat from `from` to come down on `at` after `flight` ticks. */
    public MawBombEntity(Level level, Vec3 from, Vec3 at, int flight) {
        this(FFEntities.MAW_BOMB.get(), level);
        launch(from, at, flight);
    }

    @Override
    protected void burst() {
        if (!(level() instanceof ServerLevel s) || isRemoved()) {
            return;
        }
        Vec3 at = position();
        BlockPos c = BlockPos.containing(at);
        s.playSound(null, c, FFSounds.ICE_SHATTER.get(), SoundSource.HOSTILE, 3.0F, 0.7F);
        s.playSound(null, c, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.4F, 0.6F);
        s.playSound(null, c, FFSounds.SHOCKWAVE.get(), SoundSource.HOSTILE, 1.8F, 1.3F);
        s.sendParticles(FFParticles.ICE_SHARD.get(), at.x, at.y + 0.3D, at.z, 16, 0.5D, 0.3D, 0.5D, 0.2D);
        // a little hurt and a little throw for whoever is near it (nothing laid on them: no frost, no slowness)
        for (net.minecraft.world.entity.LivingEntity v : s.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                getBoundingBox().inflate(BLAST), e -> e.isAlive() && !(e instanceof com.jastkub.frozenfortress.entity.FrostServantEntity)
                        && !(e instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())))) {
            double d = v.position().distanceTo(at);
            if (d > BLAST) {
                continue;
            }
            v.hurt(damageSources().explosion(this, null), DAMAGE);
            Vec3 out = new Vec3(v.getX() - at.x, 0.0D, v.getZ() - at.z);
            out = out.lengthSqr() > 1.0E-4D ? out.normalize() : new Vec3(random.nextDouble() - 0.5D, 0.0D, random.nextDouble() - 0.5D).normalize();
            double k = THROW * (1.0D - 0.5D * d / BLAST) * com.jastkub.frozenfortress.registry.FFEnchantments.steady(v);
            v.setDeltaMovement(v.getDeltaMovement().add(out.x * k, LIFT, out.z * k));
            v.hurtMarked = true;
        }
        // and the hearths near it go out
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, -2, -3), c.offset(3, 2, 3))) {
            BlockState st = s.getBlockState(p);
            if (st.getBlock() instanceof CampfireBlock && st.getValue(CampfireBlock.LIT)
                    && Vec3.atCenterOf(p).distanceToSqr(at) <= DOUSE * DOUSE) {
                BlockPos q = p.immutable();
                CampfireBlock.dowse(this, s, q, st);
                s.setBlock(q, st.setValue(CampfireBlock.LIT, false), 11);
                s.playSound(null, q, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.2F, 0.9F);
                s.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, q.getX() + 0.5D, q.getY() + 0.6D, q.getZ() + 0.5D,
                        5, 0.2D, 0.1D, 0.2D, 0.01D);
            }
        }
        // the burst to look at: the crest of frost (harmless) and the shell flying apart (no pools)
        double floor = Math.floor(at.y + 0.4D);
        s.addFreshEntity(new FrostWaveEntity(level(), null, at.x, floor, at.z, 3.6F, 10, 0.0F).harmless());
        double turn = random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < 4; i++) {
            double a = turn + Math.PI * 2.0D * i / 4.0D;
            double out = 0.22D + random.nextDouble() * 0.12D;
            s.addFreshEntity(new IceFragmentEntity(level(), null, at.add(0.0D, 0.4D, 0.0D),
                    new Vec3(Math.cos(a) * out, 0.48D + random.nextDouble() * 0.18D, Math.sin(a) * out)).dud());
        }
        discard();
    }
}
