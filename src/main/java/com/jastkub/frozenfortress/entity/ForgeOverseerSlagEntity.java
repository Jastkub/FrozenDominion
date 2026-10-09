package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A SPARK OF FROZEN SLAG - what the Forge Overseer's hammer strikes off the lump in his tongs (ForgeOverseerEntity,
 * the slag): a jagged lump of glassy black slag with the cold showing in its cracks, spinning, lobbed in a low arc. It
 * bites where it lands: some damage, a slowing, frostbite. Its model is its own (geo/entity/forge_overseer_slag).
 *
 * <p>Not pickable and not a target for other projectiles (other mods' bombs cast whatever they hit to LivingEntity).
 */
public class ForgeOverseerSlagEntity extends ThrowableProjectile implements GeoEntity {

    static final float DMG = 5.0F;
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.forge_overseer_slag.spin");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ForgeOverseerSlagEntity(EntityType<? extends ForgeOverseerSlagEntity> type, Level level) {
        super(type, level);
    }

    public ForgeOverseerSlagEntity(Level level, LivingEntity owner) {
        super(FFEntities.FORGE_OVERSEER_SLAG.get(), owner, level);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return 0.035F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 100) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && !(e instanceof FrostServantEntity) && !(e instanceof ForgeOverseerSlagEntity)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide && result.getEntity() instanceof LivingEntity v) {
            LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;
            v.hurt(damageSources().mobProjectile(this, owner), DMG);
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 60, 0), owner);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel s) {
            s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 0.1D, getZ(), 8, 0.15D, 0.1D, 0.15D, 0.12D);
            playSound(FFSounds.FORGE_OVERSEER_SLAG_HIT.get(), 1.1F, 0.9F + random.nextFloat() * 0.3F);
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64.0D * 64.0D;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spin", 0, state -> state.setAndContinue(SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
