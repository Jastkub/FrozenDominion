package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.item.ThroneBaneSkills;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ZMORA TRONU'S CRESCENTS, as things (ThroneBaneWaveRenderer draws them: geo/entity/fx_throne_bane_crescent and
 * fx_throne_bane_slash, tools/gen_throne_bane.py):
 * <ul>
 * <li>CRESCENT (kind 0) - Gniew Tura's, off every swing in the charged form: a smile of ice three blocks across at
 *     waist height, tilted with the swing, out to 6.4 blocks in 8 ticks; it goes through everyone in its way (12,
 *     frostbite).</li>
 * <li>SLASH (kind 1) - TRONOBOJCA: six blocks across, slow - 0.48 a tick, twelve blocks in 25 ticks - from the floor
 *     to two blocks up, through armour (indirect magic, the wielder's kill), half again on the stunned (and the stun
 *     is spent: the throne-slayer's combo).</li>
 * </ul>
 * Both move the same on both sides (a straight line at their own speed); a wall stops them.
 */
public class ThroneBaneWaveEntity extends Entity implements GeoEntity {

    public static final int CRESCENT = 0, SLASH = 1;
    /** Blocks a tick, life in ticks, half-width, the band it cuts (from its feet), half its depth. */
    static final double[] SPEED = {0.8D, 0.48D}, HALF_W = {1.7D, 3.0D}, BOTTOM = {-0.45D, -0.3D}, TOP = {1.35D, 2.4D},
            HALF_D = {0.6D, 0.8D};
    static final int[] LIFE = {9, 26};

    private static final EntityDataAccessor<Byte> KIND =
            SynchedEntityData.defineId(ThroneBaneWaveEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> YAW =
            SynchedEntityData.defineId(ThroneBaneWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ROLL =
            SynchedEntityData.defineId(ThroneBaneWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> EMPOWERED =
            SynchedEntityData.defineId(ThroneBaneWaveEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation[] FLY = {
            RawAnimation.begin().thenLoop("animation.fx_throne_bane_crescent.fly"),
            RawAnimation.begin().thenLoop("animation.fx_throne_bane_slash.fly")};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<UUID> struck = new HashSet<>();
    @Nullable
    private UUID ownerId;
    private float damage = 12.0F;
    private boolean fed;

    public ThroneBaneWaveEntity(EntityType<? extends ThroneBaneWaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;                      // six blocks of crescent from a block's box
    }

    private static ThroneBaneWaveEntity make(Level level, LivingEntity owner, Vec3 at, float yaw, int kind) {
        ThroneBaneWaveEntity w = new ThroneBaneWaveEntity(FFEntities.THRONE_BANE_WAVE.get(), level);
        w.ownerId = owner.getUUID();
        w.entityData.set(KIND, (byte) kind);
        w.entityData.set(YAW, yaw);
        w.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        return w;
    }

    /** Gniew Tura's crescent; `flip` tilts it the other way (the swings alternate). */
    public static ThroneBaneWaveEntity crescent(Level level, LivingEntity owner, Vec3 at, float yaw, boolean flip) {
        ThroneBaneWaveEntity w = make(level, owner, at, yaw, CRESCENT);
        w.damage = ThroneBaneSkills.CRESCENT_DMG;
        w.entityData.set(ROLL, flip ? 24.0F : -24.0F);
        return w;
    }

    /** Tronobojca. */
    public static ThroneBaneWaveEntity slash(Level level, LivingEntity owner, Vec3 at, float yaw, float damage,
                                             boolean empowered) {
        ThroneBaneWaveEntity w = make(level, owner, at, yaw, SLASH);
        w.damage = damage;
        w.entityData.set(EMPOWERED, empowered);
        w.entityData.set(ROLL, -6.0F);
        return w;
    }

    public int kind() {
        return entityData.get(KIND);
    }

    public float waveYaw() {
        return entityData.get(YAW);
    }

    public float roll() {
        return entityData.get(ROLL);
    }

    public boolean empowered() {
        return entityData.get(EMPOWERED);
    }

    public int life() {
        return LIFE[kind()];
    }

    private Vec3 dir() {
        Vec3 d = Vec3.directionFromRotation(0.0F, waveYaw());
        return new Vec3(d.x, 0.0D, d.z).normalize();
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(KIND, (byte) CRESCENT);
        builder.define(YAW, 0.0F);
        builder.define(ROLL, 0.0F);
        builder.define(EMPOWERED, false);
    }

    @Override
    public void tick() {
        super.tick();
        int k = kind();
        Vec3 d = dir();
        Vec3 from = position();
        Vec3 to = from.add(d.scale(SPEED[k]));
        setPos(to.x, to.y, to.z);
        if (level().isClientSide) {
            return;
        }
        if (tickCount > LIFE[k]) {
            discard();
            return;
        }
        // a wall stops it (its middle, a block up)
        BlockPos bp = BlockPos.containing(to.x, to.y + 1.0D, to.z);
        if (!level().getBlockState(bp).getCollisionShape(level(), bp).isEmpty()) {
            ((ServerLevel) level()).playSound(null, to.x, to.y + 1.0D, to.z, FFSounds.ICE_SHATTER.get(),
                    SoundSource.PLAYERS, 1.0F, k == SLASH ? 0.7F : 1.1F);
            AttackFxEntity.spawn(level(), "throne_bane_hit", to.add(0.0D, 1.0D, 0.0D), waveYaw(),
                    k == SLASH ? 2.5F : 1.4F, 10, owner());
            discard();
            return;
        }
        cut(k, from, to, d);
    }

    private void cut(int k, Vec3 from, Vec3 to, Vec3 d) {
        LivingEntity owner = owner();
        Vec3 side = new Vec3(-d.z, 0.0D, d.x);
        AABB box = new AABB(from, to).inflate(HALF_W[k] + 0.5D, 0.0D, HALF_W[k] + 0.5D)
                .setMinY(to.y + BOTTOM[k] - 0.5D).setMaxY(to.y + TOP[k]);
        for (LivingEntity v : level().getEntitiesOfClass(LivingEntity.class, box,
                e -> !struck.contains(e.getUUID()) && ThroneBaneSkills.isFoe(owner, e))) {
            Vec3 rel = v.position().subtract(to);
            double along = rel.dot(d), across = Math.abs(rel.dot(side));
            if (along > HALF_D[k] + v.getBbWidth() * 0.5D || along < -SPEED[k] - HALF_D[k] - v.getBbWidth() * 0.5D
                    || across > HALF_W[k] + v.getBbWidth() * 0.5D) {
                continue;
            }
            if (v.getY() > to.y + TOP[k] || v.getY() + v.getBbHeight() < to.y + BOTTOM[k]) {
                continue;
            }
            struck.add(v.getUUID());
            float dmg = damage;
            DamageSource src;
            Vec3 push;
            if (k == SLASH) {
                src = damageSources().indirectMagic(this, owner != null ? owner : this);     // through armour
                push = d.scale(1.1D).add(0.0D, 0.4D, 0.0D);
                if (ThroneBaneSkills.isStunned(v)) {
                    dmg *= ThroneBaneSkills.STUNNED_BONUS;
                    ThroneBaneSkills.breakStun(v);
                    level().playSound(null, v.getX(), v.getEyeY(), v.getZ(), FFSounds.THRONE_BANE_COMBO.get(),
                            SoundSource.PLAYERS, 1.4F, 0.9F);
                    if (owner instanceof Player p) {
                        p.displayClientMessage(Component.translatable("item.frozen_dominion.throne_bane.combo_stun")
                                .withStyle(ChatFormatting.AQUA), true);
                    }
                }
            } else {
                src = owner instanceof Player p ? damageSources().playerAttack(p)
                        : damageSources().indirectMagic(this, owner != null ? owner : this);
                push = d.scale(0.55D).add(0.0D, 0.2D, 0.0D);
            }
            if (ThroneBaneSkills.strike(owner != null ? owner : this, v, src, dmg, push) && !fed
                    && owner instanceof Player p) {
                fed = true;
                com.jastkub.frozenfortress.item.ThroneBaneItem.feedFrom(p);
            }
        }
    }

    @Nullable
    private LivingEntity owner() {
        if (ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof LivingEntity le) {
            return le;
        }
        return null;
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
        return distance < 96.0D * 96.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();                                  // a cut does not outlast a reload
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "fly", 0, state -> state.setAndContinue(FLY[kind() & 1])));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
