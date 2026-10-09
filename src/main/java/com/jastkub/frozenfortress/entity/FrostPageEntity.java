package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A leaf torn from the Priestess's book, the hymn on it lit. It circles its
 * mark for a breath - then falls on them. One blow (one arrow) and it is
 * paper and frost on the floor: the leaves are there to be swatted.
 *
 * <p>A living thing of one point of health for the same reason as the
 * Turnkey's keys: what a player shoots at must be able to take a hit safely.
 */
public class FrostPageEntity extends Mob implements GeoEntity {

    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.frost_page.fly");
    private static final int CIRCLE = 34;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId, targetId;
    private double angle;
    private float damage = 4.0F;
    private int life;

    public FrostPageEntity(EntityType<? extends FrostPageEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        this.noPhysics = true;
        this.xpReward = 0;
    }

    public FrostPageEntity(Level level, LivingEntity owner, LivingEntity target, Vec3 from, double angle, float damage) {
        this(FFEntities.FROST_PAGE.get(), level);
        this.ownerId = owner.getUUID();
        this.targetId = target.getUUID();
        this.angle = angle;
        this.damage = damage;
        moveTo(from.x, from.y, from.z, level.random.nextFloat() * 360.0F, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
    }

    @Nullable
    private Entity byId(@Nullable UUID id) {
        return id != null && level() instanceof ServerLevel s ? s.getEntity(id) : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                level().addParticle(FFParticles.SOUL_FROST.get(), getX(), getY() + 0.1D, getZ(), 0.0D, 0.0D, 0.0D);
            }
            return;
        }
        life++;
        Entity t = byId(targetId);
        if (!(t instanceof LivingEntity target) || !target.isAlive() || life > 140 || byId(ownerId) == null) {
            tear();
            return;
        }
        Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D);
        Vec3 goal;
        if (life < CIRCLE) {
            // round the mark, closing in, for a breath
            angle += 0.16D;
            double r = 3.2D - life * 0.03D;
            goal = aim.add(Math.cos(angle) * r, 0.6D + Math.sin(life * 0.3D) * 0.3D, Math.sin(angle) * r);
        } else {
            goal = aim;
        }
        if (life == CIRCLE) {                                    // it turns and dives: heard, so it can be met
            level().playSound(null, blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 0.8F, 1.7F);
        }
        Vec3 to = goal.subtract(position());
        double speed = life < CIRCLE ? 0.32D : 0.42D;
        Vec3 v = to.length() > speed ? to.normalize().scale(speed) : to;
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        if (life >= CIRCLE && position().distanceTo(aim) < 0.9D) {
            Entity owner = byId(ownerId);
            target.hurt(damageSources().indirectMagic(this, owner), damage);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            target.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 60, 0));
            level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.HOSTILE, 1.0F, 1.2F);
            tear();
        }
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.getEntity() instanceof FrostServantEntity) {
            return false;
        }
        if (source.getEntity() instanceof Player || source.getDirectEntity() instanceof Projectile) {
            tear();
            return true;
        }
        return false;
    }

    /** Torn: a scatter of frost and paper. */
    private void tear() {
        if (level() instanceof ServerLevel s) {
            s.playSound(null, blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 1.0F, 1.6F);
            s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 0.2D, getZ(), 8, 0.2D, 0.2D, 0.2D, 0.06D);
            s.sendParticles(FFParticles.BLIZZARD_FLAKE.get(), getX(), getY() + 0.2D, getZ(), 10, 0.3D, 0.3D, 0.3D, 0.02D);
        }
        discard();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "fly", 0, s -> s.setAndContinue(FLY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
