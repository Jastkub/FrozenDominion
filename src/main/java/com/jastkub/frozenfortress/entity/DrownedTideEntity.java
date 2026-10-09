package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEffects;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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
 * THE BLACK TIDE (Czarny Przyplyw) - the Drowned Lady's fourth attack, of my design for her. The water of the cistern drawn up through the ice: first it WELLS round her -
 * a black pool spreading, boiling (the tell, with her arms lifting it, 24 ticks) - then a RING of it runs out over
 * the floor, crested with ice, from two blocks to twelve in a second and a fifth (0.42 a tick).
 *
 * <p>The crest takes whoever it reaches - damage, slowed, frostbitten, thrown outward - but there are three answers
 * to it, and each is a thing to learn: be on a RUNE PLATE when it comes (it parts round them and the rune flares),
 * be OVER it (it is a block high: a jump timed to it clears it), or be out of its reach. Up on the walkway it does not
 * reach at all. Its model's clip and this class keep the same straight line (tools/gen_drowned_lady.py: TIDE_WELL,
 * TIDE_END, TIDE_R0, TIDE_R1).
 */
public class DrownedTideEntity extends Entity implements GeoEntity {

    public static final int WELL = 24, END = 48, LIFE = 58;
    public static final double R0 = 2.0D, R1 = 12.0D;
    /** How deep the crest is, either side of its line. */
    static final double BAND = 0.8D;
    /** Feet this far over the floor go over the crest. */
    static final double CLEAR = 0.9D;
    static final float DMG = 10.0F;

    private static final RawAnimation PLAY = RawAnimation.begin().thenPlayAndHold("animation.drowned_tide.play");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private final Set<UUID> struck = new HashSet<>();
    private final Set<UUID> flared = new HashSet<>();

    public DrownedTideEntity(EntityType<? extends DrownedTideEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;                  // twelve blocks of ring from a block's box
    }

    public DrownedTideEntity(Level level, DrownedLadyEntity owner) {
        this(FFEntities.DROWNED_TIDE.get(), level);
        this.ownerId = owner.getUUID();
        moveTo(owner.getX(), owner.floorTop(), owner.getZ(), 0.0F, 0.0F);
    }

    /** Where the crest is at tick t, in blocks from the middle (-1 before it runs). */
    public static double radius(float t) {
        if (t < WELL) {
            return -1.0D;
        }
        return R0 + (R1 - R0) * Math.min(1.0D, (t - WELL) / (double) (END - WELL));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        int t = tickCount;
        if (t == WELL) {
            playSound(FFSounds.DROWNED_LADY_TIDE.get(), 2.4F, 1.0F);
        }
        if (t >= WELL && t <= END) {
            run(radius(t));
        }
        if (t >= LIFE) {
            discard();
        }
    }

    private void run(double r) {
        LivingEntity owner = ownerId != null && level() instanceof ServerLevel s
                && s.getEntity(ownerId) instanceof LivingEntity le ? le : null;
        AABB reach = getBoundingBox().inflate(R1 + 1.0D, 3.0D, R1 + 1.0D);
        // the runes it passes over flare, whether anyone stands on them or not: it is how the floor shows you
        for (DrownedIcePlateEntity p : level().getEntitiesOfClass(DrownedIcePlateEntity.class, reach,
                DrownedIcePlateEntity::isRune)) {
            double d = Math.hypot(p.getX() - getX(), p.getZ() - getZ());
            if (Math.abs(d - r) < 0.6D && flared.add(p.getUUID())) {
                p.flare();
            }
        }
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, reach, this::foe)) {
            if (struck.contains(v.getUUID())) {
                continue;
            }
            double d = Math.hypot(v.getX() - getX(), v.getZ() - getZ());
            if (Math.abs(d - r) > BAND) {
                continue;
            }
            double feet = v.getY() - getY();
            if (feet > CLEAR || feet < -1.6D) {
                continue;                                     // over it - or far under it
            }
            struck.add(v.getUUID());
            DrownedIcePlateEntity rune = DrownedIcePlateEntity.runeUnder(v);
            if (rune != null) {
                rune.flare();                                 // it parts round the rune
                continue;
            }
            v.hurt(owner != null ? damageSources().indirectMagic(this, owner) : damageSources().magic(), DMG);
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            v.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 100, 0), owner);
            Vec3 out = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
            if (out.lengthSqr() > 1.0E-4D) {
                out = out.normalize().scale(0.7D * com.jastkub.frozenfortress.registry.FFEnchantments.steady(v));
                v.setDeltaMovement(v.getDeltaMovement().add(out.x, 0.25D, out.z));
                v.hurtMarked = true;
            }
        }
    }

    private boolean foe(LivingEntity e) {
        return e.isAlive() && !(e instanceof FrostServantEntity) && !(e instanceof DrownedHandsEntity)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    protected void defineSynchedData() {
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
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 80.0D * 80.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "tide", 0, state -> state.setAndContinue(PLAY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
