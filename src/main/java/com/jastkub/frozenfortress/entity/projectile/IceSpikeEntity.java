package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A spear of ice that erupts from the ground after a short, readable warning.
 * Used by Velkhar, the Rimeweaver and the Sovereign's Lament.
 */
public class IceSpikeEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Integer> DELAY =
            SynchedEntityData.defineId(IceSpikeEntity.class, EntityDataSerializers.INT);

    /**
     * How big this one comes up, as a multiple of the base spike.
     *
     * <p>Synced because it is drawn. A run of these travelling away from him
     * used to be twelve identical spikes appearing in a line, which reads as a
     * repeating tile - and says nothing about where the run is dangerous.
     * Growing them along the line gives the attack a direction you can see
     * from the side, and the damage rides the same number so the shape is not
     * a lie: being clipped by the far end genuinely costs more than being
     * clipped at his feet.
     */
    private static final EntityDataAccessor<Float> SCALE =
            SynchedEntityData.defineId(IceSpikeEntity.class, EntityDataSerializers.FLOAT);

    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlay("animation.ice_spike.emerge");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final Set<UUID> hitEntities = new HashSet<>();

    /** Three hearts through Ignitium; the spike fan is a zoning tool. */
    private float damage = 150.0F;
    @Nullable
    private UUID ownerUUID;

    public IceSpikeEntity(EntityType<? extends IceSpikeEntity> type, Level level) {
        super(type, level);
    }

    public IceSpikeEntity(Level level, @Nullable LivingEntity owner, double x,
                          double y, double z, float damage, int delayTicks,
                          float scale) {
        this(level, owner, x, y, z, damage * scale, delayTicks);
        entityData.set(SCALE, scale);
        // the hitbox follows the model, or a spike twice the size is twice as
        // easy to see and exactly as easy to walk through
        refreshDimensions();
    }

    /** How many times the base size this one is. 1 unless asked otherwise. */
    public float scale() {
        return entityData.get(SCALE);
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(
            net.minecraft.world.entity.Pose pose) {
        return super.getDimensions(pose).scale(scale());
    }

    public IceSpikeEntity(Level level, @Nullable LivingEntity owner, double x, double y, double z, float damage, int delayTicks) {
        this(FFEntities.ICE_SPIKE.get(), level, owner, x, y, z, damage, delayTicks);
    }

    /** One of another shape, coming up the same way (the Rimeweaver's thorns). */
    protected IceSpikeEntity(EntityType<? extends IceSpikeEntity> type, Level level, @Nullable LivingEntity owner,
                             double x, double y, double z, float damage, int delayTicks) {
        this(type, level);
        // Snap to ground so the spike always erupts from the floor.
        BlockPos ground = BlockPos.containing(x, y + 1.0D, z);
        while (ground.getY() > level.getMinBuildHeight() && level.getBlockState(ground.below()).isAir()) {
            ground = ground.below();
        }
        setPos(x, ground.getY(), z);
        this.damage = damage;
        entityData.set(DELAY, delayTicks);
        if (owner != null) {
            this.ownerUUID = owner.getUUID();
        }
        setYRot(level.random.nextFloat() * 360.0F);
    }

    public int getDelay() {
        return entityData.get(DELAY);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DELAY, 5);
        entityData.define(SCALE, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        int delay = getDelay();

        if (level().isClientSide) {
            if (tickCount <= delay && warnParticles()) {
                for (int i = 0; i < 2; i++) {
                    level().addParticle(FFParticles.FROST_SWIRL.get(),
                            getX() + (random.nextDouble() - 0.5D) * 0.8D, getY() + 0.1D,
                            getZ() + (random.nextDouble() - 0.5D) * 0.8D, 0.0D, 0.05D, 0.0D);
                }
            }
            return;
        }

        if (tickCount == delay) {
            playSound(FFSounds.ICE_SPIKE_EMERGE.get(), 1.0F, 0.9F + random.nextFloat() * 0.3F);
            ((ServerLevel) level()).sendParticles(FFParticles.ICE_SHARD.get(),
                    getX(), getY() + 0.4D, getZ(), 14, 0.3D, 0.3D, 0.3D, 0.15D);
        }

        // Active (damaging) window: eruption plus a short linger.
        if (tickCount >= delay && tickCount <= delay + 8) {
            for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(0.35D, 0.6D, 0.35D))) {
                if (hitEntities.contains(target.getUUID()) || target.getUUID().equals(ownerUUID)) {
                    continue;
                }
                LivingEntity owner = getOwner();
                // NOT HIS OWN. See FFAllies - the spikes come up under
                // everything in the ring, and his colossus stands in it.
                if (com.jastkub.frozenfortress.entity.FFAllies.spares(owner, target)) {
                    continue;
                }
                if (owner != null && (target == owner || target.isAlliedTo(owner))) {
                    continue;
                }
                if (owner == null && target instanceof FrostServantAlly) {
                    continue;
                }
                hitEntities.add(target.getUUID());
                target.hurt(owner != null
                        ? damageSources().indirectMagic(this, owner)
                        : damageSources().magic(), damage);
                target.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 60, 0));
                target.push(0.0D, 0.45D, 0.0D);
            }
        }

        // THIRTY-FOUR, NOT TWENTY-SIX, because that is how long its own
        // animation is. The emerge clip rises over three ticks, holds for
        // twenty-three and then SINKS from twenty-six to thirty-four - and the
        // entity was being discarded on the exact tick the sink began, so the
        // spike vanished instead of retracting. Eight ticks of it were never
        // once seen, and the disappearance is what made them hard to notice at
        // all: a thing that pops out of existence does not register as having
        // been there.
        if (tickCount > delay + 34) {
            discard();
        }
    }

    /** Its warning a haze of frost on the floor (the old spikes); a thing with its own warning shape says no. */
    protected boolean warnParticles() {
        return true;
    }

    protected RawAnimation emergeAnim() {
        return EMERGE;
    }

    /** What it shows while it waits to come up, or nothing at all. */
    @Nullable
    protected RawAnimation warnAnim() {
        return null;
    }

    /** Marker for entities that ice spikes must never harm. */
    public interface FrostServantAlly {
    }

    @Nullable
    private LivingEntity getOwner() {
        if (ownerUUID != null && level() instanceof ServerLevel serverLevel
                && serverLevel.getEntity(ownerUUID) instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        damage = tag.getFloat("Damage");
        if (tag.hasUUID("Owner")) {
            ownerUUID = tag.getUUID("Owner");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Damage", damage);
        if (ownerUUID != null) {
            tag.putUUID("Owner", ownerUUID);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            if (tickCount >= getDelay()) {
                return state.setAndContinue(emergeAnim());
            }
            RawAnimation warn = warnAnim();
            return warn != null ? state.setAndContinue(warn) : PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
