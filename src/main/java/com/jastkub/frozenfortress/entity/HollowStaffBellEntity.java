package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
 * THE BELL OF PENANCE (Dzwon Pokuty, 07.10.2026) - the Shade Shepherd's bell grown three blocks across, its bronze gone
 * to spectral ice, a crown for its canons (tools/gen_hollow_staff.py, fx_hollow_staff_bell).
 *
 * <p>The entity stands on the floor where the staff pointed; its one clip carries the bell: HUNG {@link #HEIGHT}
 * blocks up and swaying to {@link #HANG}, its SHADOW spreading on the floor under it (the tell: it is the area), then
 * DROPPED - it lands on {@link #IMPACT}. The blow: {@link #DMG} to every foe within {@link #RADIUS}, DARKNESS on
 * them, half again on anything the Black Tide still HOLDS (the combo). The toll that follows: every MARKED foe within
 * {@link #TOLL_RADIUS} is KNOCKED DOWN for {@link #STUN_T} ticks. Then it rings on, and sinks.
 *
 * <p>While it hangs and while it rings (to {@link #FADE}) its shadow is on the floor: a Litany rune striking a foe
 * under it splits in two (HollowStaffRuneEntity).
 */
public class HollowStaffBellEntity extends Entity implements GeoEntity {

    // ---- the beats of its clip (tools/gen_hollow_staff.py BELL_* - change one, change both)
    public static final int HANG = 6, IMPACT = 16, FADE = 40, LIFE = 48;
    public static final double HEIGHT = 8.0D, RADIUS = 3.5D, TOLL_RADIUS = 6.0D;
    public static final float DMG = 16.0F, HELD_MULT = 1.5F;
    public static final int DARK_T = 80, STUN_T = 20;

    private static final RawAnimation DROP = RawAnimation.begin().thenPlayAndHold("animation.fx_hollow_staff_bell.drop");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;

    public HollowStaffBellEntity(EntityType<? extends HollowStaffBellEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;                  // eight blocks of bell over a box a block across
    }

    public HollowStaffBellEntity(Level level, LivingEntity owner, Vec3 floor) {
        this(FFEntities.HOLLOW_STAFF_BELL.get(), level);
        this.ownerId = owner.getUUID();
        moveTo(floor.x, floor.y, floor.z, 0.0F, 0.0F);
    }

    /** The floor under the point the staff names: straight down from it, up to 16 blocks. */
    public static Vec3 floorUnder(Level level, Vec3 at) {
        BlockPos p = BlockPos.containing(at);
        for (int dy = 0; dy < 16; dy++) {
            BlockPos q = p.below(dy);
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) {
                double top = q.getY() + level.getBlockState(q).getCollisionShape(level, q).max(net.minecraft.core.Direction.Axis.Y);
                return new Vec3(at.x, top, at.z);
            }
        }
        return at;
    }

    @Nullable
    private LivingEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    public boolean ownedBy(Entity e) {
        return ownerId != null && ownerId.equals(e.getUUID());
    }

    /** Is `e` under this bell's shadow now? */
    public boolean shadows(Entity e) {
        if (tickCount > FADE) {
            return false;
        }
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        return dx * dx + dz * dz <= RADIUS * RADIUS && e.getY() > getY() - 2.0D && e.getY() < getY() + HEIGHT;
    }

    /** One of `owner`'s bells whose shadow `v` stands in, if there is one. */
    @Nullable
    public static HollowStaffBellEntity shadowOver(LivingEntity v, LivingEntity owner) {
        for (HollowStaffBellEntity b : v.level().getEntitiesOfClass(HollowStaffBellEntity.class,
                v.getBoundingBox().inflate(RADIUS + 1.0D, HEIGHT, RADIUS + 1.0D), b -> b.ownedBy(owner))) {
            if (b.shadows(v)) {
                return b;
            }
        }
        return null;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        int t = tickCount;
        if (t == 1) {
            s.playSound(null, getX(), getY() + HEIGHT, getZ(), FFSounds.HOLLOW_STAFF_BELL_SUMMON.get(), SoundSource.PLAYERS,
                    2.0F, 1.0F);
        }
        if (t == HANG) {
            s.playSound(null, getX(), getY() + HEIGHT * 0.5D, getZ(), FFSounds.HOLLOW_STAFF_BELL_FALL.get(),
                    SoundSource.PLAYERS, 1.8F, 1.0F);
        }
        if (t == IMPACT) {
            impact(s);
        }
        if (t >= LIFE) {
            discard();
        }
    }

    private void impact(ServerLevel s) {
        LivingEntity owner = owner();
        s.playSound(null, getX(), getY(), getZ(), FFSounds.HOLLOW_STAFF_BELL_TOLL.get(), SoundSource.PLAYERS, 4.0F, 1.0F);
        for (LivingEntity v : s.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(TOLL_RADIUS, 3.0D, TOLL_RADIUS), e -> HollowStaffMagic.inTheFight(owner, e))) {
            double dx = v.getX() - getX(), dz = v.getZ() - getZ();
            double d2 = dx * dx + dz * dz;
            double dy = v.getY() - getY();
            if (dy < -1.5D || dy > 3.5D) {
                continue;
            }
            if (d2 <= RADIUS * RADIUS) {
                float dmg = HollowStaffMagic.held(v) ? DMG * HELD_MULT : DMG;
                HollowStaffMagic.strike(v, this, owner, dmg);
                v.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARK_T, 0), owner);
                double d = Math.sqrt(d2);
                if (d > 0.2D && !HollowStaffMagic.held(v)) {
                    v.setDeltaMovement(v.getDeltaMovement().add(dx / d * 0.45D, 0.2D, dz / d * 0.45D));
                    v.hurtMarked = true;
                }
            }
            if (d2 <= TOLL_RADIUS * TOLL_RADIUS && HollowStaffMagic.marked(v)) {
                HollowStaffMagic.stun(v, STUN_T);         // the toll: the marked are knocked down
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
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0D * 128.0D;
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
        controllers.add(new AnimationController<>(this, "bell", 0, state -> state.setAndContinue(DROP)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
