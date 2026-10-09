package com.jastkub.frozenfortress.entity.projectile;

import com.jastkub.frozenfortress.entity.AttackFxEntity;
import com.jastkub.frozenfortress.entity.LastWatchSentinelEntity;
import com.jastkub.frozenfortress.item.LastWatchCombat;
import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * THE LIGHT-ARROW of the Last Watch (fx_last_watch_arrow: an ice shaft lit from within, a lens-crystal head, a streak
 * of light behind). Three kinds:
 * <ul>
 *   <li>NORMAL - a partial draw. Struck: one more Czuwanie; and if the foe was MARKED, the Turnkey's chains for every
 *       marked foe (LastWatchCombat.pinAllMarked). Struck nothing: the Czuwanie is broken.</li>
 *   <li>RAIN - one of Deszcz's five. On a marked foe it tops the mark up.</li>
 *   <li>SENTINEL - the watchman's bolt: straight, short-lived, and it MARKS what it hits.</li>
 * </ul>
 * It does not stick: on stone it breaks into light (a small impact picture) and is gone. Never picked up.
 */
public class LastWatchArrowEntity extends AbstractArrow implements GeoEntity {

    public static final byte NORMAL = 0, RAIN = 1, SENTINEL = 2;

    private static final EntityDataAccessor<Byte> KIND =
            SynchedEntityData.defineId(LastWatchArrowEntity.class, EntityDataSerializers.BYTE);
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.fx_last_watch_arrow.fly");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean struck;

    public LastWatchArrowEntity(EntityType<? extends LastWatchArrowEntity> type, Level level) {
        super(type, level);
    }

    public LastWatchArrowEntity(Level level, LivingEntity shooter, byte kind) {
        super(FFEntities.LAST_WATCH_ARROW.get(), shooter, level);
        entityData.set(KIND, kind);
    }

    public byte kind() {
        return entityData.get(KIND);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(KIND, NORMAL);
    }

    @Nullable
    private Player archer() {
        return getOwner() instanceof Player p ? p : null;
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (!super.canHitEntity(e) || e == getOwner() || e instanceof LastWatchSentinelEntity) {
            return false;
        }
        Player p = archer();
        return p == null || !(e instanceof LivingEntity le) || LastWatchCombat.isFoe(p, le);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Entity e = hit.getEntity();
        Player p = archer();
        boolean marked = p != null && e instanceof LivingEntity le && LastWatchCombat.isMarkedBy(le, p);
        super.onHitEntity(hit);
        if (level().isClientSide || p == null || !(e instanceof LivingEntity le)) {
            return;
        }
        struck = true;
        switch (kind()) {
            case NORMAL -> {
                LastWatchCombat.vigilHit(p);
                if (marked) {
                    LastWatchCombat.pinAllMarked(p, le);
                }
            }
            case RAIN -> LastWatchCombat.refreshMark(le, p);
            default -> {
                if (le.isAlive()) {
                    LastWatchCombat.mark(le, p);
                }
            }
        }
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        target.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 40, 0), getOwner());
    }

    /** On stone it breaks into light; a plain arrow that struck nothing breaks the Czuwanie. */
    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (level().isClientSide) {
            return;
        }
        missed();
        Vec3 at = hit.getLocation();
        AttackFxEntity.spawn(level(), "last_watch_impact", at.add(0.0D, -0.2D, 0.0D), getYRot(), 0.35F, 8,
                getOwner() instanceof LivingEntity le ? le : null);
        level().playSound(null, at.x, at.y, at.z, FFSounds.LAST_WATCH_SENTINEL_SHOT.get(), SoundSource.PLAYERS, 0.4F, 1.8F);
        discard();
    }

    private void missed() {
        Player p = archer();
        if (kind() == NORMAL && !struck && p != null) {
            LastWatchCombat.vigilMiss(p);
        }
        struck = true;                                      // (counted once)
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (!inGround && tickCount % 2 == 0) {
                level().addParticle(FFParticles.FROST_SWIRL.get(), getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            }
            return;
        }
        int life = kind() == SENTINEL ? 40 : 100;
        if (tickCount > life || inGround) {
            missed();
            discard();
        }
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("LWKind", kind());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(KIND, tag.getByte("LWKind"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "fly", 0, state -> state.setAndContinue(FLY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
