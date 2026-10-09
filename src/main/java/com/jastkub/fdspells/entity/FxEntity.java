package com.jastkub.fdspells.entity;

import com.jastkub.fdspells.registry.FDSRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A thing a spell puts into the world: no physics of its own, not saved, never hit, drawn by its own model. It
 * remembers who cast it (allies are spared, the blow is theirs) and with what power, and goes when its time is up.
 */
public abstract class FxEntity extends Entity implements GeoEntity, Fx {

    /** Its time and one number of its own (a wave's width, a ring's reach), for the client to draw by. */
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(FxEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> PARAM = SynchedEntityData.defineId(FxEntity.class,
            EntityDataSerializers.FLOAT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    @Nullable
    private LivingEntity ownerCache;
    protected int life = 100;
    protected float damage;
    protected int level = 1;

    protected FxEntity(EntityType<?> type, Level world) {
        super(type, world);
        noPhysics = true;
    }

    public void setup(LivingEntity caster, float damage, int spellLevel, int life) {
        this.ownerId = caster.getUUID();
        this.ownerCache = caster;
        this.damage = damage;
        this.level = spellLevel;
        this.life = life;
        entityData.set(LIFE, life);
    }

    /** Its whole time, on either side. */
    public int lifeTicks() {
        return entityData.get(LIFE);
    }

    protected void setParam(float v) {
        entityData.set(PARAM, v);
    }

    public float param() {
        return entityData.get(PARAM);
    }

    @Nullable
    public LivingEntity owner() {
        if ((ownerCache == null || ownerCache.isRemoved()) && ownerId != null && level() instanceof ServerLevel sl
                && sl.getEntity(ownerId) instanceof LivingEntity le) {
            ownerCache = le;
        }
        return ownerCache;
    }

    public boolean ownedBy(Entity e) {
        return ownerId != null && e != null && ownerId.equals(e.getUUID());
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > life) {
            expire();
        }
    }

    /** Its time is up (or it is broken): goes. */
    protected void expire() {
        discard();
    }

    /** Whom this hurts: the living, not its caster, nor the caster's allies, summons or pets. */
    public boolean foe(Entity e) {
        return isFoe(owner(), e);
    }

    public static boolean isFoe(@Nullable LivingEntity owner, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isSpectator() || e == owner) {
            return false;
        }
        if (le instanceof Player p && p.isCreative()) {
            return false;
        }
        if (owner == null) {
            return true;
        }
        if (owner.isAlliedTo(e) || e.isAlliedTo(owner)) {
            return false;
        }
        if (e instanceof IMagicSummon s && s.getSummoner() == owner) {
            return false;
        }
        return !(e instanceof OwnableEntity o && o.getOwnerUUID() != null && o.getOwnerUUID().equals(owner.getUUID()));
    }

    /** A blow of `spell`, its caster's: what ISS counts as spell damage (spell power, resistances, friendly fire). */
    protected boolean strike(LivingEntity target, float amount, AbstractSpell spell) {
        LivingEntity o = owner();
        return DamageSources.applyDamage(target, amount, spell.getDamageSource(this, o != null ? o : this));
    }

    protected void sound(SoundEvent s, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), s, SoundSource.PLAYERS, volume, pitch);
    }

    protected void shatterSound() {
        sound(FDSRegistry.ICE_SHATTER.get(), 0.9F, 0.9F + random.nextFloat() * 0.3F);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(LIFE, 100);
        builder.define(PARAM, 1.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 64.0D * 64.0D;
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation loop = RawAnimation.begin().thenLoop("animation." + kind() + ".loop");
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(loop)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
