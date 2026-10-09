package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
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
import net.minecraftforge.registries.ForgeRegistries;
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
 * RZUT WROTAMI - HIS TOWER SHIELD, THROWN (Velkhar's first phase).
 *
 * <p>It leaves his shield arm spinning flat like a discus and flies OUT along an arc that bows to one side and runs on
 * three blocks past where the player stood - then turns and comes BACK to his hand, so it has to be dodged twice. It
 * hits on both passes (his ordinary blow, which does not pierce armour, and a shove the way it is going). If it meets
 * a wall or a pillar on the way out it rings off it and comes back early.
 *
 * <p>While it is away HIS FRONT IS OPEN: no shield on his arm (the bone is hidden - SHIELD_GONE), no guard reduction,
 * nothing turned aside, and the blow that lands is allowed as much as a blow to his back. That is what the attack
 * pays out. He catches it and the guard is back.
 *
 * <p>Model: his own shield, copied out of his model and laid flat (tools/gen_velkhar_attacks.py, velkhar_gate_throw),
 * drawn with his sheet; the spin is the renderer's (VelkharGateThrowRenderer).
 */
public class VelkharGateThrowEntity extends Entity implements GeoEntity {

    /** Out at this many blocks a tick; back from BACK0 up to BACK1; the hit's reach round its middle. */
    public static final double OUT_SPEED = 0.85D, BACK0 = 0.8D, BACK1 = 1.35D, DISC_R = 1.7D;
    /** How far past the player it flies, and the most it ever flies out. */
    public static final double PAST = 3.0D, MAX_OUT = 18.0D;
    /** It is never away longer than this (ticks). */
    public static final int MAX_LIFE = 170;
    /** The disc's middle over the entity's feet (blocks): GATE_LIFT 8 units in gen_velkhar_attacks.py. */
    public static final double LIFT = 0.5D;

    private static final EntityDataAccessor<Integer> KING =
            SynchedEntityData.defineId(VelkharGateThrowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BACK =
            SynchedEntityData.defineId(VelkharGateThrowEntity.class, EntityDataSerializers.BOOLEAN);

    private Vec3 from = Vec3.ZERO, bend = Vec3.ZERO, to = Vec3.ZERO;
    private int outTicks = 1;
    private int age;
    private int backAge;
    private Vec3 vel = Vec3.ZERO;
    private float hearts = 3.2F;
    private final Set<UUID> struckOut = new HashSet<>();
    private final Set<UUID> struckBack = new HashSet<>();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public VelkharGateThrowEntity(EntityType<? extends VelkharGateThrowEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.blocksBuilding = false;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    static EntityType<VelkharGateThrowEntity> type() {
        ResourceLocation id = FrozenFortress.id("velkhar_gate_throw");
        return ForgeRegistries.ENTITY_TYPES.containsKey(id)
                ? (EntityType<VelkharGateThrowEntity>) ForgeRegistries.ENTITY_TYPES.getValue(id) : null;
    }

    public static boolean available() {
        return type() != null;
    }

    /**
     * Thrown from his shield hand at `mark`: the arc bows to a random side, by about a fifth of its length, and ends
     * three blocks past them - close enough to them on the way that standing still is hit.
     */
    @Nullable
    public static VelkharGateThrowEntity hurl(ServerLevel level, VelkharEntity king, LivingEntity mark, float hearts) {
        EntityType<VelkharGateThrowEntity> type = type();
        if (type == null) {
            return null;
        }
        VelkharGateThrowEntity g = new VelkharGateThrowEntity(type, level);
        Vec3 s = king.gateHand();
        Vec3 t = mark.position().add(0.0D, mark.getBbHeight() * 0.5D, 0.0D);
        Vec3 d = t.subtract(s);
        double len = Math.min(MAX_OUT, d.length() + PAST);
        Vec3 dir = d.lengthSqr() > 1.0E-4D ? d.normalize() : king.getViewVector(1.0F);
        Vec3 o = s.add(dir.scale(len));
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
        side = side.lengthSqr() > 1.0E-4D ? side.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
        double sign = level.random.nextBoolean() ? 1.0D : -1.0D;
        g.from = s;
        g.to = o;
        g.bend = s.add(o).scale(0.5D).add(side.scale(sign * 0.18D * len)).add(0.0D, 0.6D, 0.0D);
        g.outTicks = Math.max(6, (int) Math.ceil(len / OUT_SPEED));
        g.hearts = hearts;
        g.setPos(s.x, s.y - LIFT, s.z);
        g.entityData.set(KING, king.getId());
        level.addFreshEntity(g);
        return g;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KING, -1);
        entityData.define(BACK, false);
    }

    public boolean back() {
        return entityData.get(BACK);
    }

    private Vec3 along(double u) {
        double a = (1 - u) * (1 - u), b = 2 * (1 - u) * u, c = u * u;
        return from.scale(a).add(bend.scale(b)).add(to.scale(c));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        VelkharEntity king = sl.getEntity(entityData.get(KING)) instanceof VelkharEntity k ? k : null;
        if (king == null || !king.isAlive() || king.getAttackState() != VelkharEntity.GATE_THROW) {
            // his throw was cut short: it goes to frost and is his again (finish() put it back on his arm)
            sl.sendParticles(FFParticles.SOUL_FROST.get(), getX(), getY() + LIFT, getZ(), 30, 0.8D, 0.2D, 0.8D, 0.06D);
            discard();
            return;
        }
        age++;
        Vec3 here = position().add(0.0D, LIFT, 0.0D);
        Vec3 next;
        if (!back()) {
            double u = Math.min(1.0D, age / (double) outTicks);
            next = along(u);
            vel = next.subtract(here);
            AABB box = getBoundingBox().inflate(0.45D, 0.1D, 0.45D).move(vel);
            if (!sl.noCollision(this, box)) {
                // A WALL, A PILLAR: it rings off it and comes back early
                next = here;
                vel = vel.scale(-0.6D);
                entityData.set(BACK, true);
                sl.playSound(null, getX(), getY(), getZ(), FFSounds.VELKHAR_SHIELD_HIT.get(), SoundSource.HOSTILE,
                        3.0F, 0.75F);
                sl.sendParticles(FFParticles.ICE_SHARD.get(), here.x, here.y, here.z, 20, 0.4D, 0.4D, 0.4D, 0.2D);
                king.thump(0.25F);
            } else if (u >= 1.0D) {
                entityData.set(BACK, true);
            }
        } else {
            backAge++;
            Vec3 hand = king.gateHand();
            Vec3 d = hand.subtract(here);
            if (d.length() < 1.6D || age > MAX_LIFE) {
                king.onGateCaught();
                discard();
                return;
            }
            double speed = Math.min(BACK1, BACK0 + backAge * 0.03D);
            Vec3 want = d.normalize().scale(speed);
            // it does not turn on a coin: three quarters of its old way each tick, so the way back is an arc too
            vel = vel.scale(0.72D).add(want.scale(0.28D));
            vel = vel.lengthSqr() > 1.0E-6D ? vel.normalize().scale(speed) : want;
            next = here.add(vel);
        }
        setPos(next.x, next.y - LIFT, next.z);
        if (age % 5 == 0) {
            sl.playSound(null, getX(), getY(), getZ(), FFSounds.VELKHAR_AIRCUT.get(), SoundSource.HOSTILE, 1.6F,
                    1.35F + (back() ? 0.15F : 0.0F));
        }
        strike(sl, king, next);
    }

    /** Whoever the spinning disc passes through, once a pass. */
    private void strike(ServerLevel sl, VelkharEntity king, Vec3 at) {
        Set<UUID> struck = back() ? struckBack : struckOut;
        AABB box = new AABB(at.x - DISC_R - 1.0D, at.y - 2.6D, at.z - DISC_R - 1.0D,
                at.x + DISC_R + 1.0D, at.y + 1.2D, at.z + DISC_R + 1.0D);
        for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, box, StormEyeFxEntity::foe)) {
            double dx = v.getX() - at.x, dz = v.getZ() - at.z;
            double reach = DISC_R + v.getBbWidth() * 0.5D;
            if (dx * dx + dz * dz > reach * reach || at.y < v.getY() - 0.4D || at.y > v.getY() + v.getBbHeight() + 0.4D
                    || struck.contains(v.getUUID()) || com.jastkub.frozenfortress.event.FFRoll.rolling(v)) {
                continue;
            }
            struck.add(v.getUUID());
            king.strikeFromAfar(v, hearts);
            Vec3 push = new Vec3(vel.x, 0.0D, vel.z);
            push = push.lengthSqr() > 1.0E-4D ? push.normalize().scale(0.7D) : Vec3.ZERO;
            v.setDeltaMovement(v.getDeltaMovement().add(push.x, 0.3D, push.z));
            v.hurtMarked = true;
            if (v instanceof ServerPlayer sp) {
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
            }
            sl.playSound(null, v.getX(), v.getY(), v.getZ(), FFSounds.VELKHAR_SHIELD_HIT.get(), SoundSource.HOSTILE,
                    2.6F, 1.0F);
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

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.velkhar_gate_throw.idle");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "gate", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
