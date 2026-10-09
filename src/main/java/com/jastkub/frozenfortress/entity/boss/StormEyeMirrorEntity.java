package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * ZAMIEC LUSTRZANA - A MIRROR OF THE HOLLOW MAGUS. He goes into a whirl of snow and comes out of it three times over
 * neighbouring floes: two of the three are this. A mirror floats and banks exactly as he does, wears his face, carries
 * his wand - and throws storm orbs as he does (a mirror's orb hits for six tenths).
 *
 * <p>THE TELL: the Storm Anchors' beams run to HIM and to nobody else. Find the one the lightning holds up.
 *
 * <p>One blow of anything breaks a mirror (an arrow included - that is why it is a living body); twelve seconds and it
 * goes by itself. Drawn by StormEyeMirrorRenderer from his own model.
 */
public class StormEyeMirrorEntity extends LivingEntity implements GeoEntity {

    public static final int LIFE = 240, CAST_EVERY = 70, CAST_SHOW = 26, FADE = 8;
    /** How high over its floe it hangs, how much it bobs. */
    static final double HANG = 5.6D, BOB = 0.7D;

    private static final EntityDataAccessor<Integer> KING =
            SynchedEntityData.defineId(StormEyeMirrorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CAST_AT =
            SynchedEntityData.defineId(StormEyeMirrorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BROKEN_AT =
            SynchedEntityData.defineId(StormEyeMirrorEntity.class, EntityDataSerializers.INT);

    private Vec3 floe = Vec3.ZERO;
    /** The moving floe it hangs over (a ring floe's body), or null: it follows it round (server; its moves are synced). */
    @javax.annotation.Nullable
    private StormEyeFloeEntity floeBody;
    private double phase;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StormEyeMirrorEntity(EntityType<? extends StormEyeMirrorEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    public static StormEyeMirrorEntity weave(ServerLevel level, VelkharEntity king, Vec3 floe, float yaw) {
        StormEyeMirrorEntity m = new StormEyeMirrorEntity(FFEntities.STORM_EYE_MIRROR.get(), level);
        m.floe = floe;
        m.floeBody = king.stormEye() != null ? king.stormEye().floeNear(floe) : null;
        m.phase = level.random.nextDouble() * Math.PI * 2.0D;
        m.moveTo(floe.x, floe.y + HANG, floe.z, yaw, 0.0F);
        m.setYHeadRot(yaw);
        m.yBodyRot = yaw;
        m.entityData.set(KING, king.getId());
        level.addFreshEntity(m);
        return m;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KING, -1);
        builder.define(CAST_AT, -100);
        builder.define(BROKEN_AT, -1);
    }

    public boolean casting() {
        return tickCount - entityData.get(CAST_AT) < CAST_SHOW;
    }

    /** 1 whole, falling to 0 as it comes apart. */
    public float whole(float partialTick) {
        int at = entityData.get(BROKEN_AT);
        if (at < 0) {
            return Math.min(1.0F, (tickCount + partialTick) / 10.0F);
        }
        return Math.max(0.0F, 1.0F - (tickCount + partialTick - at) / FADE);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        int broken = entityData.get(BROKEN_AT);
        if (broken >= 0) {
            if (tickCount - broken > FADE) {
                discard();
            }
            return;
        }
        VelkharEntity king = StormEyeFxEntity.kingById(sl, entityData.get(KING));
        if (king == null || !king.isAlive() || king.stormEye() == null || tickCount > LIFE) {
            shatter(sl);
            return;
        }
        // ---- it banks over its floe as he does: a slow circle, a breath up and down - and the floe goes round, so do it
        Vec3 drift = Vec3.ZERO;
        if (floeBody != null && !floeBody.isRemoved()) {
            double gt = sl.getGameTime();
            floe = floeBody.motion().at(gt);
            drift = floeBody.motion().at(gt + 1.0D).subtract(floe);     // carried at the floe's own speed, no lag
        }
        double t = tickCount * 0.035D + phase;
        Vec3 want = floe.add(Math.cos(t) * 1.4D, HANG + Math.sin(tickCount * 0.07D + phase) * BOB, Math.sin(t) * 1.4D);
        Vec3 here = position();
        Vec3 step = want.subtract(here).scale(0.12D).add(drift);
        setPos(here.x + step.x, here.y + step.y, here.z + step.z);
        setDeltaMovement(Vec3.ZERO);
        // ---- it watches the nearest of them
        Player mark = sl.getNearestPlayer(getX(), getY(), getZ(), 48.0D,
                p -> p instanceof Player pl && !pl.isCreative() && !pl.isSpectator());
        if (mark != null) {
            float yaw = (float) (Mth.atan2(mark.getZ() - getZ(), mark.getX() - getX()) * (180.0D / Math.PI)) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
            setYHeadRot(yaw);
            // ---- and throws at them
            if (tickCount > 20 && (tickCount + getId() * 7) % CAST_EVERY == 0) {
                entityData.set(CAST_AT, tickCount);
                Vec3 from = position().add(0.0D, VelkharEntity.BEAM_ORIGIN_HEIGHT + 0.6D, 0.0D);
                StormEyeOrbEntity.throwAt(sl, king, from, mark, 0.6F);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || entityData.get(BROKEN_AT) >= 0) {
            return false;
        }
        if (StormEyeFxEntity.kingsWork(source.getEntity()) || StormEyeFxEntity.kingsWork(source.getDirectEntity())
                || source.getEntity() == null) {
            return false;
        }
        shatter((ServerLevel) level());
        return true;
    }

    private void shatter(ServerLevel sl) {
        if (entityData.get(BROKEN_AT) >= 0) {
            return;
        }
        entityData.set(BROKEN_AT, tickCount);
        sl.playSound(null, getX(), getY() + 2.0D, getZ(), FFSounds.STORM_EYE_MIRROR.get(), SoundSource.HOSTILE,
                2.2F, 1.3F);
        sl.sendParticles(FFParticles.BLIZZARD_FLAKE.get(), getX(), getY() + 2.0D, getZ(), 60, 0.6D, 1.6D, 0.6D, 0.1D);
        sl.sendParticles(FFParticles.SOUL_FROST.get(), getX(), getY() + 2.0D, getZ(), 30, 0.5D, 1.4D, 0.5D, 0.06D);
        StormEyeGustEntity.squall(sl, position());
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean addEffect(net.minecraft.world.effect.MobEffectInstance effect, @javax.annotation.Nullable net.minecraft.world.entity.Entity source) {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0D * 128.0D;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    // ---- GeckoLib: his own clips --------------------------------------------------------------------------------
    private static final RawAnimation HOVER = RawAnimation.begin().thenLoop("animation.velkhar.hover");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.velkhar.cast");
    private static final RawAnimation LEGS = RawAnimation.begin().thenLoop("animation.velkhar.legs_tuck");
    private static final RawAnimation CROWN = RawAnimation.begin().thenLoop("animation.velkhar.crown_float");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            state.getController().setAnimationSpeed(1.2D);
            return state.setAndContinue(casting() ? CAST : HOVER);
        }));
        controllers.add(new AnimationController<>(this, "legs", 4, state -> state.setAndContinue(LEGS)));
        controllers.add(new AnimationController<>(this, "crown", 5, state -> state.setAndContinue(CROWN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
