package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * PULS MROKU - the Shade Shepherd's attack of his second half.
 *
 * <p>TOLD for a second and a half: the crook raised over his head, the bell tolled twice, slow and deep, and the dark
 * GATHERING at his feet - a pool of it spreading, its rim lifting into flames of shadow (this, before it runs). Then he
 * drives the crook into the floor and it RUNS: a ring of shadow a block high, crested with pale fire, out from him to
 * the farthest fire of his chamber - and every fire it reaches goes out (ShadeLight.snuff), whatever anyone does.
 *
 * <p>Whoever it reaches takes it - a blow, the dark (the game's Darkness), a stagger outward - unless they are OVER it: it
 * is a block high, and a jump timed to it clears it, as the Drowned Lady's tide. Struck down or killed while he gathers
 * it, it sinks back into the floor. Drawn as geometry, built fresh each frame at its radius (ShadePulseRenderer).
 */
public class ShadePulseEntity extends Entity {

    /** Blocks a tick its ring runs, and where it starts from. */
    public static final float SPEED = 0.75F, R0 = 1.2F;
    /** The pool's rim at the end of its gathering. */
    public static final float POOL = 2.2F;
    /** Ticks it takes to fade once it has run all the way. */
    public static final int FADE = 8;
    /** How deep its crest is, either side of its line; feet this far over the floor go over it. */
    static final double BAND = 0.8D, CLEAR = 0.9D;
    static final float DMG = 5.0F;

    private static final EntityDataAccessor<Float> REACH =
            SynchedEntityData.defineId(ShadePulseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> GATHER =
            SynchedEntityData.defineId(ShadePulseEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID ownerId;
    private final Set<UUID> struck = new HashSet<>();
    /** The fires of his chamber it has yet to reach (lit or not: one lit while he gathers it goes out too). */
    private final List<BlockPos> fires = new ArrayList<>();

    public ShadePulseEntity(EntityType<? extends ShadePulseEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;                              // tens of blocks of ring from a block's box
    }

    /** Gathered at `owner`'s feet for `gather` ticks, then run out to `reach` - putting out `fires` as it gets to them. */
    public static ShadePulseEntity gather(ServerLevel level, ShadeShepherdEntity owner, int gather, float reach,
                                          List<BlockPos> fires) {
        ShadePulseEntity p = new ShadePulseEntity(FFEntities.SHADE_PULSE.get(), level);
        p.ownerId = owner.getUUID();
        p.moveTo(owner.getX(), owner.getY(), owner.getZ(), 0.0F, 0.0F);
        p.entityData.set(GATHER, gather);
        p.entityData.set(REACH, reach);
        p.fires.addAll(fires);
        level.addFreshEntity(p);
        return p;
    }

    public int gatherTicks() {
        return entityData.get(GATHER);
    }

    public float reach() {
        return entityData.get(REACH);
    }

    /** Ticks its ring runs, from R0 out to its reach. */
    public int runTicks() {
        return Mth.ceil(Math.max(0.0F, reach() - R0) / SPEED);
    }

    public int life() {
        return gatherTicks() + runTicks() + FADE;
    }

    /** Its ring's radius `t` ticks after it came (fractional for the renderer): -1 while it gathers. */
    public float radius(float t) {
        float run = t - gatherTicks();
        if (run < 0.0F) {
            return -1.0F;
        }
        return Math.min(reach(), R0 + SPEED * run);
    }

    /** Its pool's rim while it gathers. */
    public float pool(float t) {
        return POOL * Mth.clamp(t / Math.max(1, gatherTicks()), 0.0F, 1.0F);
    }

    @Nullable
    private ShadeShepherdEntity owner() {
        return ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof ShadeShepherdEntity o
                ? o : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        int t = tickCount;
        ShadeShepherdEntity owner = owner();
        if (t < gatherTicks()) {
            // he must still be drawing it up: struck out of it or killed, it sinks back
            if (owner == null || !owner.isAlive() || owner.getAttackState() != ShadeShepherdEntity.PULSE) {
                discard();
            }
            return;
        }
        if (t == gatherTicks()) {
            playSound(FFSounds.SHOCKWAVE.get(), 3.0F, 0.55F);
        }
        float r = radius(t);
        if (r >= 0.0F && t <= gatherTicks() + runTicks()) {
            run(r, owner);
        }
        if (t >= life()) {
            discard();
        }
    }

    private void run(float r, @Nullable ShadeShepherdEntity owner) {
        ServerLevel s = (ServerLevel) level();
        for (Iterator<BlockPos> it = fires.iterator(); it.hasNext(); ) {
            BlockPos f = it.next();
            if (Math.hypot(f.getX() + 0.5D - getX(), f.getZ() + 0.5D - getZ()) <= r) {
                ShadeLight.snuff(s, f, this);
                it.remove();
            }
        }
        AABB reach = getBoundingBox().inflate(r + 1.5D, 3.0D, r + 1.5D);
        for (LivingEntity v : s.getEntitiesOfClass(LivingEntity.class, reach, this::foe)) {
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
            v.hurt(owner != null ? damageSources().indirectMagic(this, owner) : damageSources().magic(), DMG);
            v.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), owner);
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), owner);
            // the
            // ringing in the ears after a blast, in their own head - played to them alone, where they stand
            if (v instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.playNotifySound(com.jastkub.frozenfortress.registry.FFSounds.SHADE_DEAFEN.get(),
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            Vec3 out = new Vec3(v.getX() - getX(), 0.0D, v.getZ() - getZ());
            if (out.lengthSqr() > 1.0E-4D) {
                out = out.normalize().scale(0.5D * com.jastkub.frozenfortress.registry.FFEnchantments.steady(v));
                v.setDeltaMovement(v.getDeltaMovement().add(out.x, 0.2D, out.z));
                v.hurtMarked = true;
            }
        }
    }

    private boolean foe(LivingEntity e) {
        return e.isAlive() && !FFAllies.ofTheKing(e) && !(e instanceof ShadeEntity)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(REACH, 12.0F);
        entityData.define(GATHER, 28);
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
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
