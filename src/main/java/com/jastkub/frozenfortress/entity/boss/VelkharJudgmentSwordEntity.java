package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.FrostWaveEntity;
import com.jastkub.frozenfortress.event.FloorScarHandler;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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

/**
 * LODOWY SAD - ONE OF THE THREE SPECTRAL SWORDS (Velkhar's first phase).
 *
 * <p>It forms over the spot the player stood on when he called it (half a second), hangs there point-down (the circle
 * on the floor under it - VelkharJudgmentMarkEntity - counting down), falls, and shatters on the floor: everyone
 * within RADIUS of the point, up to REACH_UP over the floor, takes the blow (his ordinary blow: it does not pierce
 * armour) and is thrown a little out of it. Three come one after another, each over where the player is at that
 * moment, so the answer is to keep moving.
 *
 * <p>The entity sits ON THE FLOOR at the point of impact; the sword is drawn the height it still has to fall above it
 * (VelkharJudgmentRenderers.Sword), worked out from the game clock on both sides so client and server agree to the
 * tick without a packet. Model: tools/gen_velkhar_attacks.py (velkhar_judgment_sword).
 */
public class VelkharJudgmentSwordEntity extends Entity implements GeoEntity {

    /** The sword's length, point to pommel (blocks) - mirrors SWORD_LEN in gen_velkhar_attacks.py. */
    public static final float LENGTH = 76.0F / 16.0F;
    /** Ticks after it appears: the form clip ends, the fall begins. */
    public static final int FORM = 10, HANG = 18;
    /** Its fall: blocks per tick squared; the highest it hangs. */
    public static final double GRAVITY = 0.55D, MAX_HEIGHT = 9.0D;
    /** The blow: how far round the point, how high over the floor. */
    public static final double RADIUS = 2.6D, REACH_UP = 2.5D;
    /** Ticks its pieces fly before it is gone. */
    public static final int SHATTER = 10;

    private static final EntityDataAccessor<Integer> START =
            SynchedEntityData.defineId(VelkharJudgmentSwordEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEIGHT =
            SynchedEntityData.defineId(VelkharJudgmentSwordEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> KING =
            SynchedEntityData.defineId(VelkharJudgmentSwordEntity.class, EntityDataSerializers.INT);

    private float hearts = 2.2F;
    private boolean landed;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public VelkharJudgmentSwordEntity(EntityType<? extends VelkharJudgmentSwordEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.blocksBuilding = false;
    }

    /** Looked up by name, so this compiles - and the attack is simply not offered - until the type is registered. */
    @SuppressWarnings("unchecked")
    @Nullable
    static EntityType<VelkharJudgmentSwordEntity> type() {
        ResourceLocation id = FrozenFortress.id("velkhar_judgment_sword");
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                ? (EntityType<VelkharJudgmentSwordEntity>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(id) : null;
    }

    public static boolean available() {
        return type() != null && VelkharJudgmentMarkEntity.type() != null;
    }

    /** The floor under `who` (where their feet are, or the first solid block under them within a dozen). */
    public static Vec3 floorUnder(Level level, LivingEntity who) {
        if (who.onGround()) {
            return who.position();
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < 12; dy++) {
            p.set(who.getBlockX(), (int) Math.floor(who.getY()) - dy, who.getBlockZ());
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                return new Vec3(who.getX(), p.getY() + 1.0D, who.getZ());
            }
        }
        return who.position();
    }

    /**
     * A sword over `floor`, and its circle under it. It hangs as high as the room lets it (never more than
     * MAX_HEIGHT, never into the ceiling). Returns null when the types are not registered.
     */
    @Nullable
    public static VelkharJudgmentSwordEntity summon(ServerLevel level, VelkharEntity king, Vec3 floor, float hearts) {
        EntityType<VelkharJudgmentSwordEntity> type = type();
        if (type == null) {
            return null;
        }
        double room = MAX_HEIGHT + LENGTH + 1.0D;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= (int) room; dy++) {
            p.set((int) Math.floor(floor.x), (int) Math.floor(floor.y) + dy, (int) Math.floor(floor.z));
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                room = dy;
                break;
            }
        }
        float h = (float) Math.max(1.5D, Math.min(MAX_HEIGHT, room - LENGTH - 0.6D));
        VelkharJudgmentSwordEntity s = new VelkharJudgmentSwordEntity(type, level);
        s.setPos(floor.x, floor.y, floor.z);
        int now = (int) level.getGameTime();
        s.entityData.set(START, now);
        s.entityData.set(HEIGHT, h);
        s.entityData.set(KING, king.getId());
        s.hearts = hearts;
        level.addFreshEntity(s);
        VelkharJudgmentMarkEntity.under(level, floor, now, s.impactTick(), (float) RADIUS);
        level.playSound(null, floor.x, floor.y + h, floor.z, FFSounds.CRYSTAL_CHIME.get(), SoundSource.HOSTILE,
                2.4F, 1.25F);
        return s;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(START, 0);
        builder.define(HEIGHT, 8.0F);
        builder.define(KING, -1);
    }

    /** Ticks since it appeared (fractional for the renderer), off the game clock both sides share. */
    public float age(float partialTick) {
        return (float) (level().getGameTime() - (long) entityData.get(START)) + partialTick;
    }

    public float hangHeight() {
        return entityData.get(HEIGHT);
    }

    /** Ticks after it appears that the point meets the floor. */
    public int impactTick() {
        return HANG + (int) Math.ceil(Math.sqrt(2.0D * hangHeight() / GRAVITY));
    }

    /** How high over the floor its point is at `age`. */
    public float height(float age) {
        if (age < HANG) {
            return hangHeight();
        }
        float s = age - HANG;
        return (float) Math.max(0.0D, hangHeight() - 0.5D * GRAVITY * s * s);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        float age = age(0.0F);
        if ((int) age == HANG) {
            playSound(FFSounds.VELKHAR_AIRCUT.get(), 2.4F, 1.5F);
        }
        if (!landed && age >= impactTick()) {
            landed = true;
            impact((ServerLevel) level());
        }
        if (age >= impactTick() + SHATTER || age > 200) {
            discard();
        }
    }

    private void impact(ServerLevel sl) {
        playSound(FFSounds.ICE_SHATTER.get(), 3.2F, 0.85F);
        playSound(FFSounds.VELKHAR_IMPACT.get(), 2.6F, 1.15F);
        VelkharEntity king = level().getEntity(entityData.get(KING)) instanceof VelkharEntity k && k.isAlive() ? k : null;
        AABB box = new AABB(getX() - RADIUS - 1.0D, getY() - 0.5D, getZ() - RADIUS - 1.0D,
                getX() + RADIUS + 1.0D, getY() + REACH_UP, getZ() + RADIUS + 1.0D);
        // his spells go out with him: a sword still falling when he dies shatters harmlessly
        for (LivingEntity v : king == null ? java.util.List.<LivingEntity>of()
                : sl.getEntitiesOfClass(LivingEntity.class, box, StormEyeFxEntity::foe)) {
            double dx = v.getX() - getX(), dz = v.getZ() - getZ();
            if (dx * dx + dz * dz > (RADIUS + v.getBbWidth() * 0.5D) * (RADIUS + v.getBbWidth() * 0.5D)
                    || com.jastkub.frozenfortress.event.FFRoll.rolling(v)) {
                continue;
            }
            king.strikeFromAfar(v, hearts);
            Vec3 out = new Vec3(dx, 0.0D, dz);
            out = out.lengthSqr() > 1.0E-4D ? out.normalize().scale(0.45D) : Vec3.ZERO;
            v.setDeltaMovement(v.getDeltaMovement().add(out.x, 0.32D, out.z));
            v.hurtMarked = true;
            if (v instanceof ServerPlayer sp) {
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
            }
        }
        // the floor answers: a low ring of frost out from the point, the floor split under it
        sl.addFreshEntity(new FrostWaveEntity(sl, king, getX(), getY(), getZ(), (float) RADIUS + 0.8F, 8, 0.0F)
                .harmless());
        FloorScarHandler.tear(sl, position(), RADIUS, 0.8D);
        sl.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 0.4D, getZ(), 40, 0.8D, 0.3D, 0.8D, 0.25D);
        sl.sendParticles(FFParticles.SOUL_FROST.get(), getX(), getY() + 0.6D, getZ(), 20, 0.6D, 0.4D, 0.6D, 0.06D);
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
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0D * 96.0D;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }


    // ---- GeckoLib: form, hang, fall, shatter - chosen off its own clock ------------------------------------------
    private static final RawAnimation FORM_ANIM = RawAnimation.begin().thenPlay("animation.velkhar_judgment_sword.form");
    private static final RawAnimation HANG_ANIM = RawAnimation.begin().thenLoop("animation.velkhar_judgment_sword.hang");
    private static final RawAnimation FALL_ANIM = RawAnimation.begin().thenPlayAndHold("animation.velkhar_judgment_sword.fall");
    private static final RawAnimation SHATTER_ANIM =
            RawAnimation.begin().thenPlayAndHold("animation.velkhar_judgment_sword.shatter");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "sword", 1, state -> {
            float age = age(0.0F);
            if (age >= impactTick()) {
                return state.setAndContinue(SHATTER_ANIM);
            }
            if (age >= HANG) {
                return state.setAndContinue(FALL_ANIM);
            }
            return state.setAndContinue(age < FORM ? FORM_ANIM : HANG_ANIM);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
