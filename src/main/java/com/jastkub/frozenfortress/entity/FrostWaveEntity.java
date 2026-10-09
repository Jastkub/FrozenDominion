package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A WAVE OF FROST, AS A THING - a crest of ice running out over the floor in a ring from
 * where it was loosed, lower the further it goes, gone at its full reach.
 * FrostWaveRenderer draws it: a wall of ice that rises to its crest and
 * breaks, teeth of rime along the top.
 *
 * <p>Whoever the crest reaches is taken once (damage, slowed, frostbitten,
 * frozen) - unless they are over it: the crest is low by the end, and a jump
 * clears it there. The servants of winter it passes through.
 */
public class FrostWaveEntity extends Entity {

    private static final EntityDataAccessor<Float> REACH =
            SynchedEntityData.defineId(FrostWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SPAN =
            SynchedEntityData.defineId(FrostWaveEntity.class, EntityDataSerializers.INT);
    /** How much of the ring runs, in degrees either side of DIR (180: all of it). */
    private static final EntityDataAccessor<Float> ARC =
            SynchedEntityData.defineId(FrostWaveEntity.class, EntityDataSerializers.FLOAT);
    /** Which way its arc runs (degrees, the way entity yaw is measured). */
    private static final EntityDataAccessor<Float> DIR =
            SynchedEntityData.defineId(FrostWaveEntity.class, EntityDataSerializers.FLOAT);

    /** Its crest at the start, in blocks; it falls to a third of that by the end. */
    public static final float CREST = 1.35F;

    @Nullable
    private UUID ownerId;
    private float damage = 6.0F;
    /** Heavy: slowed hard, frostbitten, frozen. Light: slowed a little, nothing more. */
    private boolean heavy = true;
    private final Set<UUID> struck = new HashSet<>();

    public FrostWaveEntity(EntityType<? extends FrostWaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public FrostWaveEntity(Level level, @Nullable Entity owner, double x, double y, double z,
                           float reach, int span, float damage) {
        this(FFEntities.FROST_WAVE.get(), level);
        this.ownerId = owner == null ? null : owner.getUUID();
        this.damage = damage;
        entityData.set(REACH, reach);
        entityData.set(SPAN, span);
        setPos(x, y, z);
    }

    /** Only to look at (a frost maw's bomb's burst - MawBombEntity): it touches nobody. */
    public FrostWaveEntity harmless() {
        this.harmless = true;
        return this;
    }

    private boolean harmless;

    /** Only slows (the bell's ring of cold), not the full frost. */
    public FrostWaveEntity light() {
        this.heavy = false;
        return this;
    }

    /** Only an arc of it runs: halfAngle degrees either side of the yaw (a blow's cone, not a ring). */
    public FrostWaveEntity arc(float yaw, float halfAngle) {
        entityData.set(DIR, yaw);
        entityData.set(ARC, Math.min(180.0F, halfAngle));
        return this;
    }

    public float arcHalf() {
        return entityData.get(ARC);
    }

    public float arcYaw() {
        return entityData.get(DIR);
    }

    /** Is that point inside the arc (seen from its middle)? */
    public boolean inArc(double x, double z) {
        float half = arcHalf();
        if (half >= 179.9F) {
            return true;
        }
        float to = (float) (Math.atan2(z - getZ(), x - getX()) * (180.0D / Math.PI)) - 90.0F;
        return Math.abs(net.minecraft.util.Mth.wrapDegrees(to - arcYaw())) <= half;
    }

    /** Already taken by what loosed it: the wave passes them. */
    public FrostWaveEntity spare(Set<UUID> who) {
        struck.addAll(who);
        return this;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(REACH, 6.0F);
        builder.define(SPAN, 16);
        builder.define(ARC, 180.0F);
        builder.define(DIR, 0.0F);
    }

    /** How far it runs, in blocks. */
    public float reach() {
        return entityData.get(REACH);
    }

    /** How long it runs, in ticks. */
    public int span() {
        return entityData.get(SPAN);
    }

    /** How far through its run it is, 0..1 (eased: it starts fast and slows). */
    public float progress(float partialTick) {
        float t = Mth.clamp((tickCount + partialTick) / span(), 0.0F, 1.0F);
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    /** Its crest's radius at that point. */
    public float radius(float partialTick) {
        return 0.5F + (reach() - 0.5F) * progress(partialTick);
    }

    /** Its crest's height then. */
    public float crest(float partialTick) {
        float t = Mth.clamp((tickCount + partialTick) / span(), 0.0F, 1.0F);
        return CREST * (1.0F - 0.66F * t);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        if (tickCount > span()) {
            discard();
            return;
        }
        float r = radius(0.0F);
        float h = crest(0.0F);
        Entity owner = ownerId == null ? null : s.getEntity(ownerId);
        for (LivingEntity who : harmless ? java.util.List.<LivingEntity>of()
                : s.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r + 1.0D, 3.0D, r + 1.0D),
                e -> e.isAlive() && !(e instanceof FrostServantEntity) && !struck.contains(e.getUUID())
                        && !(owner instanceof HollowGolemEntity g && g.mySide(e)))) {
            double d = Math.sqrt(who.distanceToSqr(getX(), who.getY(), getZ()));
            if (d > r + 0.4D) {
                continue;                                       // not reached yet
            }
            if (!inArc(who.getX(), who.getZ())) {
                continue;                                       // off the side of its arc
            }
            struck.add(who.getUUID());                          // reached: now or never
            if (who.getY() > getY() + h * 0.8D || who.getY() < getY() - 1.5D) {
                continue;                                       // over the crest (or a floor below it)
            }
            who.hurt(damageSources().indirectMagic(this, owner != null ? owner : this), damage);
            if (heavy) {
                who.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                who.addEffect(new MobEffectInstance(FFEffects.FROSTBITE, 80, 1));
                who.setTicksFrozen(Math.max(who.getTicksFrozen(), 160));
            } else {
                who.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
            s.playSound(null, who.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.HOSTILE, 1.0F, 0.9F);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();                                              // a wave does not outlast a reload
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
