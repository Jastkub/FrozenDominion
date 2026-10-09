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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * KOTWICA BURZY - A STORM ANCHOR. A pylon of black ice that comes up out of an outer floe with a storm crystal held
 * in three prongs at its top, and a beam of lightning from that crystal to the Hollow Magus. While any stands, he flies
 * - and NOTHING OF YOURS REACHES HIM. Break them all and he falls onto the central floe, stunned: the one time in the
 * phase he is in reach of a blade.
 *
 * <p>A LIVING body on purpose, not an Entity like the old wards: an arrow has to be able to hit it (the counsel says
 * "take up a bow"), and a living body is the one shape every mod's projectile code already knows how to strike. Forty
 * health, never more than twelve off it in one blow - three good arrows, or a jump across to its floe and a few
 * swings.
 *
 * <p>Drawn by StormEyeRenderers.Anchor (GeckoLib fx_storm_eye_anchor + the beam as geometry).
 */
public class StormEyeAnchorEntity extends LivingEntity implements GeoEntity {

    public static final float HEALTH = 40.0F, HIT_CAP = 12.0F;
    /** Ticks it takes to come up out of the floe, and the crystal's height over its feet. */
    public static final int RISE = 28;
    public static final double CRYSTAL_Y = 2.75D;
    /** Ticks its pieces fly before it is gone. */
    public static final int SHATTER = 16;

    private static final EntityDataAccessor<Integer> KING =
            SynchedEntityData.defineId(StormEyeAnchorEntity.class, EntityDataSerializers.INT);
    /** The age it starts to rise at (it waits for new ice to freeze under it). */
    private static final EntityDataAccessor<Integer> RISE_AT =
            SynchedEntityData.defineId(StormEyeAnchorEntity.class, EntityDataSerializers.INT);
    /**
     * THE FLOE IT STANDS ON (entity id, -1 none) and the way it faced when it came up. The floes move (StormEyeFloeEntity):
     * it rides its floe and turns with it - put there every tick on BOTH sides from the floe's own place, so on the
     * client it never lags a packet behind the ice it stands in.
     */
    private static final EntityDataAccessor<Integer> FLOE =
            SynchedEntityData.defineId(StormEyeAnchorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> YAW0 =
            SynchedEntityData.defineId(StormEyeAnchorEntity.class, EntityDataSerializers.FLOAT);

    private int slot = -1;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public StormEyeAnchorEntity(EntityType<? extends StormEyeAnchorEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;                              // its beam runs far outside its box
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 0.0D);
    }

    /** spot = {x, y, z, ticks to wait, slot} as StormEyeArena.anchorSpots gives it. */
    public static StormEyeAnchorEntity raise(ServerLevel level, VelkharEntity king, double[] spot) {
        StormEyeAnchorEntity a = new StormEyeAnchorEntity(FFEntities.STORM_EYE_ANCHOR.get(), level);
        float yaw = level.random.nextFloat() * 360.0F;
        a.moveTo(spot[0], spot[1], spot[2], yaw, 0.0F);
        a.entityData.set(KING, king.getId());
        a.entityData.set(RISE_AT, (int) spot[3]);
        a.slot = (int) spot[4];
        StormEyeFloeEntity floe = king.stormEye() != null ? king.stormEye().floeEntity(a.slot) : null;
        if (floe != null) {
            a.entityData.set(FLOE, floe.getId());
            // the way it faces, counted against its floe's turn (so it faces `yaw` now and turns with the ice after)
            a.entityData.set(YAW0, yaw - (float) Math.toDegrees(floe.spinNow()));
        }
        a.setHealth(HEALTH);
        level.addFreshEntity(a);
        return a;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(KING, -1);
        entityData.define(RISE_AT, 0);
        entityData.define(FLOE, -1);
        entityData.define(YAW0, 0.0F);
    }

    /** On its floe: where the floe is, turned as the floe is. The client's floe has already stepped this tick. */
    private void ride() {
        int id = entityData.get(FLOE);
        if (id < 0 || !(level().getEntity(id) instanceof StormEyeFloeEntity f) || f.isRemoved()) {
            return;
        }
        setPos(f.getX(), f.getY(), f.getZ());
        float yaw = entityData.get(YAW0) + (float) Math.toDegrees(f.spinNow());
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    public int kingId() {
        return entityData.get(KING);
    }

    /** 0 while it waits under the ice, 1 once it stands. */
    public float risen(float partialTick) {
        return Math.max(0.0F, Math.min(1.0F, (tickCount + partialTick - entityData.get(RISE_AT)) / RISE));
    }

    public boolean standing() {
        return risen(0.0F) >= 1.0F && isAlive();
    }

    /** Where its crystal is, in the world. */
    public Vec3 crystal() {
        return position().add(0.0D, CRYSTAL_Y, 0.0D);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        ride();
        if (level().isClientSide || !isAlive()) {
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        int t = tickCount - entityData.get(RISE_AT);
        if (t == 0) {
            sl.playSound(null, getX(), getY(), getZ(), FFSounds.STORM_EYE_ANCHOR_RISE.get(), SoundSource.HOSTILE, 2.6F, 1.0F);
        }
        VelkharEntity king = StormEyeFxEntity.kingById(sl, kingId());
        if (king == null || !king.isAlive() || king.stormEye() == null) {
            if (tickCount > 40) {
                discard();
            }
            return;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !isAlive() || !standing()) {
            return false;
        }
        if (StormEyeFxEntity.kingsWork(source.getEntity()) || StormEyeFxEntity.kingsWork(source.getDirectEntity())) {
            return false;
        }
        if (source.getEntity() == null && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;                                    // the weather does not break the weather's own pylons
        }
        return super.hurt(source, Math.min(amount, HIT_CAP));
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            sl.playSound(null, getX(), getY(), getZ(), FFSounds.STORM_EYE_ANCHOR_BREAK.get(), SoundSource.HOSTILE,
                    3.0F, 1.0F);
            sl.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 1.5D, getZ(), 50, 0.5D, 1.0D, 0.5D, 0.35D);
            VelkharEntity king = StormEyeFxEntity.kingById(sl, kingId());
            // its last spit of lightning goes back up the beam
            if (king != null) {
                StormEyeBoltEntity.arc(sl, crystal(), king.position().add(0.0D, VelkharEntity.BEAM_ORIGIN_HEIGHT, 0.0D),
                        8, 0.45F, StormEyeBoltEntity.COLD);
                if (king.stormEye() != null) {
                    king.stormEye().anchorGone(slot);
                }
                king.onStormAnchorBroken();
            }
        }
    }

    /** It comes apart over SHATTER ticks (the renderer plays it), then it is gone - no corpse falling over. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= SHATTER && !level().isClientSide()) {
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
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
    protected SoundEvent getHurtSound(DamageSource source) {
        return FFSounds.ICE_CRACK.get();
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
    public boolean shouldBeSaved() {
        return false;                                         // the arena raises them again; a save never keeps one
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    // ---- GeckoLib ---------------------------------------------------------------------------------------------
    private static final RawAnimation UNDER = RawAnimation.begin().thenLoop("animation.fx_storm_eye_anchor.under");
    private static final RawAnimation RISE_ANIM = RawAnimation.begin().thenPlay("animation.fx_storm_eye_anchor.rise")
            .thenLoop("animation.fx_storm_eye_anchor.idle");
    private static final RawAnimation BREAK = RawAnimation.begin().thenPlayAndHold("animation.fx_storm_eye_anchor.shatter");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "anchor", 0, state -> {
            if (isDeadOrDying()) {
                return state.setAndContinue(BREAK);
            }
            return state.setAndContinue(risen(0.0F) <= 0.0F ? UNDER : RISE_ANIM);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
