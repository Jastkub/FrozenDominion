package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * THE SHEPHERD'S BREATH, on its way to a fire (SNUFF, and every fire at once in his GREAT BREATH): three ribbons of
 * dark wind wound round a pale cold core (fx_shade_gust), flying from the slit of his mask straight to the campfire
 * he chose. Where it reaches the fire, the fire goes out (ShadeLight.snuff - LIT false, a hiss, fx_shade_snuff).
 *
 * <p>IT CAN BE STOPPED, and that is the counterplay: it is slow enough to see coming in the firelight and to step
 * into - whoever it meets takes the chill instead (a little cold damage, slowed) and the fire is saved - and it can be
 * struck from the air, by a blade or an arrow. Either way it bursts (its "break" clip) and is gone.
 */
public class ShadeShepherdGustEntity extends Entity implements GeoEntity {

    /** Blocks a tick: a walking pace and a half - seen coming. */
    static final double SPEED = 0.36D;
    static final int LIFE = 200, BREAK_T = 10;
    static final float CHILL = 3.0F;

    private static final EntityDataAccessor<Boolean> BROKEN =
            SynchedEntityData.defineId(ShadeShepherdGustEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.fx_shade_gust.fly");
    private static final RawAnimation BREAK = RawAnimation.begin().thenPlayAndHold("animation.fx_shade_gust.break");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    @Nullable
    private BlockPos fire;
    private int age;
    private int brokenAt = -1;

    public ShadeShepherdGustEntity(EntityType<? extends ShadeShepherdGustEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** His breath sent from `from` to the campfire at `fire`. */
    public static ShadeShepherdGustEntity send(Level level, LivingEntity owner, Vec3 from, BlockPos fire) {
        ShadeShepherdGustEntity g = new ShadeShepherdGustEntity(FFEntities.SHADE_SHEPHERD_GUST.get(), level);
        g.ownerId = owner.getUUID();
        g.fire = fire.immutable();
        g.moveTo(from.x, from.y, from.z, owner.getYRot(), 0.0F);
        g.face(Vec3.atCenterOf(fire).subtract(from));
        level.addFreshEntity(g);
        g.playSound(FFSounds.SHADE_SHEPHERD_GUST.get(), 1.4F, 0.9F + level.random.nextFloat() * 0.2F);
        return g;
    }

    public boolean broken() {
        return entityData.get(BROKEN);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        age++;
        if (broken()) {
            if (age - brokenAt >= BREAK_T) {
                discard();
            }
            return;
        }
        if (fire == null || age > LIFE) {
            burst(null);
            return;
        }
        Vec3 dest = new Vec3(fire.getX() + 0.5D, fire.getY() + 0.45D, fire.getZ() + 0.5D);
        Vec3 to = dest.subtract(position());
        double d = to.length();
        if (d < 0.6D) {                                          // there: the fire goes out
            if (level() instanceof ServerLevel sl) {
                ShadeLight.snuff(sl, fire, owner());
            }
            discard();
            return;
        }
        Vec3 dir = to.scale(1.0D / d);
        // it does not fly a ruled line: it weaves a little as it goes, and less as it nears the fire
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
        double weave = Math.sin(age * 0.35D) * 0.07D * Math.min(1.0D, d / 4.0D);
        Vec3 step = dir.scale(Math.min(SPEED, d)).add(side.scale(weave)).add(0.0D, Math.cos(age * 0.27D) * 0.03D, 0.0D);
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
        face(dir);
        if (age % 16 == 0) {
            playSound(FFSounds.SHADE_SHEPHERD_GUST.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        }
        // whoever stands in its way takes it instead of the fire
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.25D),
                e -> e.isAlive() && !FFAllies.ofTheKing(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            Entity owner = owner();
            v.hurt(owner != null ? damageSources().indirectMagic(this, owner) : damageSources().magic(), CHILL);
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), owner);
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 60, 0), owner);
            burst(v);
            return;
        }
    }

    /** It bursts and is gone (caught on a body, struck, or lost). */
    private void burst(@Nullable Entity on) {
        if (broken()) {
            return;
        }
        entityData.set(BROKEN, true);
        brokenAt = age;
        playSound(FFSounds.SHADE_SHEPHERD_GUST_BREAK.get(), 1.4F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** It turns the way it flies (its model's front is -z; the renderer turns it by yaw and pitch). */
    private void face(Vec3 dir) {
        double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (flat > 1.0E-4D) {
            float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
            setYRot(yaw);
            yRotO = getYRot();
        }
        float pitch = (float) (-Mth.atan2(dir.y, flat) * (180.0D / Math.PI));
        setXRot(pitch);
        xRotO = getXRot();
    }

    @Nullable
    private Entity owner() {
        return ownerId != null && level() instanceof ServerLevel s ? s.getEntity(ownerId) : null;
    }

    // ------------------------------------------------------------------------------------------------ struck
    @Override
    public boolean isPickable() {
        return !broken();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return !broken();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || broken()) {
            return false;
        }
        if (!level().isClientSide && source.getEntity() instanceof LivingEntity le && !FFAllies.ofTheKing(le)) {
            burst(le);                                           // struck from the air: the fire is spared
            return true;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64.0D * 64.0D;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(BROKEN, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "gust", 0, state ->
                state.setAndContinue(broken() ? BREAK : FLY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
