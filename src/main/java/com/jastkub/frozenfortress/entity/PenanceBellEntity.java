package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * THE BELL OF PENANCE. It hangs where the Priestess's crook pointed, high, a
 * ring of frost on the floor under it for a second - then drops. Under it,
 * the blow; round it, a ring of cold running out across the floor that a
 * standing man takes and a jumping one does not. Then it tolls once and
 * breaks. No living thing - nothing can be aimed at it.
 */
public class PenanceBellEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Boolean> LANDED =
            SynchedEntityData.defineId(PenanceBellEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation HANG = RawAnimation.begin().thenLoop("animation.penance_bell.hang");
    private static final RawAnimation TOLL = RawAnimation.begin().thenPlayAndHold("animation.penance_bell.toll");
    private static final int HOLD = 22;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private double floorY;
    private int landedAt = -1;
    private final Set<UUID> rung = new HashSet<>();

    public PenanceBellEntity(EntityType<? extends PenanceBellEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public PenanceBellEntity(Level level, LivingEntity owner, Vec3 at) {
        this(FFEntities.PENANCE_BELL.get(), level);
        this.ownerId = owner.getUUID();
        BlockPos p = BlockPos.containing(at);
        int y = p.getY();
        for (int dy = 0; dy < 6 && level.getBlockState(p.below(dy + 1)).getCollisionShape(level, p.below(dy + 1)).isEmpty(); dy++) {
            y--;
        }
        this.floorY = y;
        double top = y + 7.0D;
        for (int dy = 1; dy <= 7; dy++) {
            BlockPos q = new BlockPos(p.getX(), y + dy, p.getZ());
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) {
                top = y + dy - 2.2D;                            // under a low vault it hangs lower
                break;
            }
        }
        moveTo(at.x, Math.max(y + 2.5D, top), at.z, 0.0F, 0.0F);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(LANDED, false);
    }

    public boolean landed() {
        return entityData.get(LANDED);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        if (tickCount < HOLD) {
            // its shadow: a ring of frost on the floor where it will fall
            if (tickCount % 2 == 0) {
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2.0D * i / 16.0D;
                    s.sendParticles(FFParticles.SOUL_FROST.get(), getX() + Math.cos(a) * 2.2D, floorY + 0.1D,
                            getZ() + Math.sin(a) * 2.2D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
            if (tickCount == 1) {
                s.playSound(null, blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1.4F, 1.4F);
            }
            return;
        }
        if (tickCount == HOLD) {                                 // it lets go: a deep rush of air before the blow
            s.playSound(null, blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 1.4F, 0.5F);
        }
        if (landedAt < 0) {
            double y = getY() - 0.2D - (tickCount - HOLD) * 0.14D;
            if (y <= floorY) {
                y = floorY;
                land(s);
            }
            setPos(getX(), y, getZ());
            return;
        }
        int since = tickCount - landedAt;
        if (since > 24) {
            s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 1.0D, getZ(), 50, 0.8D, 0.8D, 0.8D, 0.15D);
            s.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.6F, 0.6F);
            discard();
        }
    }

    private void land(ServerLevel s) {
        landedAt = tickCount;
        entityData.set(LANDED, true);
        s.playSound(null, blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0F, 0.5F);
        s.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4F, 0.5F);
        Entity owner = ownerId != null ? s.getEntity(ownerId) : null;
        for (LivingEntity v : s.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.4D, 1.0D, 2.4D),
                e -> e.isAlive() && !(e instanceof FrostServantEntity))) {
            if (v.distanceToSqr(getX(), v.getY(), getZ()) <= 2.4D * 2.4D) {
                v.hurt(damageSources().indirectMagic(this, owner), 12.0F);
                v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 100, 1));
                rung.add(v.getUUID());
            }
        }
        s.sendParticles(FFParticles.SHOCKWAVE.get(), getX(), floorY + 0.1D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        // the ring of cold, out across the floor: a crest of ice (a FrostWaveEntity), jumped or taken
        s.addFreshEntity(new FrostWaveEntity(level(), owner, getX(), floorY, getZ(), 7.5F, 14, 5.0F)
                .light().spare(rung));
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
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();                                              // a bell does not outlast a reload
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "bell", 0, s -> s.setAndContinue(landed() ? TOLL : HANG)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
