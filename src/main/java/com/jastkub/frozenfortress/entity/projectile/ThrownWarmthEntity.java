package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.entity.WarmthSplashEntity;
import com.jastkub.frozenfortress.item.WarmthPotionItem;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * THE FLASK OF WARMTH, THROWN: it flies as itself
 * (ThrownItemRenderer - the flask, its fire glowing) and bursts where it lands. Everyone within four blocks of the
 * burst gets the full minute of Warmth - the thrower too, so a flask thrown at your own feet warms you as a draught
 * did. Only players: the cold of the citadel's servants is not ours to lift. The burst itself is a thing to see
 * (WarmthSplashEntity: a dome of heat and the front of it running over the floor); the flask's glass and a few
 * flames fly off with it, only as an addition.
 */
public class ThrownWarmthEntity extends ThrowableItemProjectile {

    /** How far from the burst its warmth reaches, in blocks. */
    public static final double RADIUS = 4.0D;

    public ThrownWarmthEntity(EntityType<? extends ThrownWarmthEntity> type, Level level) {
        super(type, level);
    }

    public ThrownWarmthEntity(Level level, LivingEntity thrower) {
        super(FFEntities.THROWN_WARMTH.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return FFItems.WARMTH_POTION.get();
    }

    /** A splash potion's arc. */
    @Override
    protected float getGravity() {
        return 0.05F;
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        Vec3 at = position();
        for (Player p : s.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(RADIUS, 2.5D, RADIUS),
                who -> who.isAlive() && !who.isSpectator())) {
            if (p.distanceToSqr(at) <= RADIUS * RADIUS) {
                p.addEffect(new MobEffectInstance(FFEffects.WARMTH.get(), WarmthPotionItem.DURATION), getOwner());
            }
        }
        s.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()), at.x, at.y + 0.1D, at.z,
                14, 0.15D, 0.15D, 0.15D, 0.12D);
        s.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y + 0.2D, at.z, 8, 0.6D, 0.2D, 0.6D, 0.02D);
        s.playSound(null, at.x, at.y, at.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.NEUTRAL, 1.0F,
                0.9F + random.nextFloat() * 0.2F);
        s.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.5F, 1.4F);
        s.addFreshEntity(new WarmthSplashEntity(s, at.x, floor(s, at), at.z));
        discard();
    }

    /** The floor under the burst (a flask that bursts on a wall or on someone spreads its warmth over the floor). */
    private static double floor(ServerLevel s, Vec3 at) {
        BlockPos p = BlockPos.containing(at);
        for (int i = 0; i < 4; i++, p = p.below()) {
            BlockPos under = p.below();
            if (s.getBlockState(under).isFaceSturdy(s, under, Direction.UP)) {
                return Math.min(at.y, p.getY());
            }
        }
        return at.y;
    }
}
