package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * THE PICTURE OF AN ATTACK, as a thing with a body. The defenders' rings, wards, marks, glints and bursts are
 * each a model of their own - geo/entity/fx_&lt;kind&gt;, its clip animation.fx_&lt;kind&gt;.play, its sheet -
 * drawn by AttackFxRenderer, placed, turned and sized by whoever casts it, and gone when its clip is done.
 * Particles may ride along with one; they are never the attack.
 *
 * <p>Most are only pictures. The Rimeweaver's circle is more: it is the spell itself - at the snap (CIRCLE_SNAP)
 * it mends the dead court's own inside it and drags at whoever else is (snapCircle), so it goes on to the end even
 * if the weaver that laid it is cut down meanwhile.
 */
public class AttackFxEntity extends Entity implements GeoEntity {

    public static final String RIME_WARD = "rime_ward", RIME_CIRCLE = "rime_circle", VOLLEY_MARK = "volley_mark",
            GLINT = "glint", BLINK_VEIL = "blink_veil", CRUSH_BURST = "crush_burst", SNOW_BURST = "snow_burst",
            GALE_WEDGE = "gale_wedge";

    /** The circle of cold closes on this tick (its clip snaps at 1.5 s - tools/gen_defender_fx.py). */
    public static final int CIRCLE_SNAP = 30;
    public static final int CIRCLE_LIFE = 50;
    public static final double CIRCLE_RADIUS = 3.2D;

    private static final EntityDataAccessor<String> KIND =
            SynchedEntityData.defineId(AttackFxEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(AttackFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE =
            SynchedEntityData.defineId(AttackFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FOLLOW =
            SynchedEntityData.defineId(AttackFxEntity.class, EntityDataSerializers.INT);

    private static final Map<String, RawAnimation> CLIPS = new HashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;

    public AttackFxEntity(EntityType<? extends AttackFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;                  // drawn far outside its little box (a ring six blocks across)
    }

    /** A picture of an attack at `at`, facing `yaw`, `size` times its model, gone after `life` ticks. */
    public static AttackFxEntity spawn(Level level, String kind, Vec3 at, float yaw, float size, int life,
                                       @Nullable LivingEntity owner) {
        AttackFxEntity fx = new AttackFxEntity(FFEntities.ATTACK_FX.get(), level);
        fx.entityData.set(KIND, kind);
        fx.entityData.set(LIFE, life);
        fx.entityData.set(SIZE, size);
        fx.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        fx.setYRot(yaw);
        fx.ownerId = owner == null ? null : owner.getUUID();
        level.addFreshEntity(fx);
        return fx;
    }

    /** It rides on this one (a ward round one of the court). */
    public AttackFxEntity follow(Entity e) {
        entityData.set(FOLLOW, e.getId());
        return this;
    }

    public String kind() {
        return entityData.get(KIND);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, GLINT);
        entityData.define(LIFE, 20);
        entityData.define(SIZE, 1.0F);
        entityData.define(FOLLOW, -1);
    }

    @Override
    public void tick() {
        super.tick();
        int f = entityData.get(FOLLOW);
        if (f >= 0) {
            Entity on = level().getEntity(f);
            if (on != null && on.isAlive()) {
                setPos(on.getX(), on.getY(), on.getZ());
            } else if (!level().isClientSide) {
                discard();
                return;
            }
        }
        if (level().isClientSide) {
            return;
        }
        if (RIME_CIRCLE.equals(kind()) && tickCount == CIRCLE_SNAP) {
            snapCircle();
        }
        if (tickCount >= life()) {
            discard();
        }
    }

    /**
     * THE CIRCLE CLOSES. It no longer binds: the hostile ones inside it have
     * their wounds closed - eight health at once and a few seconds' mending after - and, since 07.10.2026, two and a half
     * seconds in which nothing hurts them and eight in which they hit harder; everyone else inside is dragged down by
     * the cold: three seconds of the heaviest slowness, and the frost's bite. (It used to slow the hostile ones too.)
     */
    private void snapCircle() {
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        LivingEntity owner = ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity le ? le : null;
        sl.playSound(null, getX(), getY(), getZ(), FFSounds.FROST_RELEASE.get(), SoundSource.HOSTILE, 1.8F, 1.1F);
        sl.playSound(null, getX(), getY(), getZ(), FFSounds.ICE_IMPACT.get(), SoundSource.HOSTILE, 1.4F, 1.3F);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(CIRCLE_RADIUS, 2.5D, CIRCLE_RADIUS),
                e -> e.isAlive() && !(e instanceof FrostServantEntity)
                        && !(e instanceof net.minecraft.world.entity.monster.Enemy)          // (its own side is healed below)
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            if (Math.hypot(v.getX() - getX(), v.getZ() - getZ()) <= CIRCLE_RADIUS && Math.abs(v.getY() - getY()) < 2.5D) {
                v.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 3), owner);
                v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 60, 0), owner);
            }
        }
        for (LivingEntity m : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(CIRCLE_RADIUS, 2.5D, CIRCLE_RADIUS),
                e -> e.isAlive() && (e instanceof FrostServantEntity || e instanceof net.minecraft.world.entity.monster.Enemy))) {
            if (Math.hypot(m.getX() - getX(), m.getZ() - getZ()) <= CIRCLE_RADIUS && Math.abs(m.getY() - getY()) < 2.5D) {
                m.heal(8.0F);
                m.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 80, 1), owner);
                // - for two and a half seconds nothing hurts them (Resistance V), and for eight
                // they hit harder (Strength II)
                m.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 50, 4), owner);
                m.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 160, 1), owner);
            }
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
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0D * 96.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "fx", 0, state -> state.setAndContinue(
                CLIPS.computeIfAbsent(kind(), k -> RawAnimation.begin().thenPlayAndHold("animation.fx_" + k + ".play")))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
