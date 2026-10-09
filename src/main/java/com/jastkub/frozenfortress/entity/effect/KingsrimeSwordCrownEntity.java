package com.jastkub.frozenfortress.entity.effect;

import com.jastkub.frozenfortress.item.KingsrimeSwordItem;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * KORONACJA MROZU (Coronation of Frost) - the Kingsrime sword held on the use key: a crown of ice blades rising out of
 * the floor round the wielder, one after another, until the ring is whole (the tell, and the charge meter: its blades
 * up is how full it is). Let go then and it BURSTS - the blades flung outward and a ring of ice running out over the
 * floor to five and a half blocks; whoever it reaches is cut and frozen where they stand for a second
 * (KingsrimeSwordShackleEntity). Let go before it is whole, and it sinks back into the floor.
 *
 * <p>While it gathers it stands at the wielder's feet and goes where he goes (KingsrimeSwordCrownRenderer draws it on
 * him, by OWNER, so it never lags him).
 */
public class KingsrimeSwordCrownEntity extends Entity {

    public static final int GATHER = 0, BURST = 1, SINK = 2;
    /** The ring's blades, their circle, the burst's reach and the ticks it takes to run there. */
    public static final int BLADES = 10;
    public static final float RADIUS = 1.7F, REACH = 5.5F;
    public static final int RUN = 5, BURST_LIFE = 14, SINK_LIFE = 8;
    public static final int ROOT = 20;

    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(KingsrimeSwordCrownEntity.class, EntityDataSerializers.INT);
    /** The tick its state began. */
    private static final EntityDataAccessor<Integer> SINCE =
            SynchedEntityData.defineId(KingsrimeSwordCrownEntity.class, EntityDataSerializers.INT);
    /** Ticks from its coming to its being whole. */
    private static final EntityDataAccessor<Integer> FULL =
            SynchedEntityData.defineId(KingsrimeSwordCrownEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER =
            SynchedEntityData.defineId(KingsrimeSwordCrownEntity.class, EntityDataSerializers.INT);
    /** How far it had gathered when it was let go (it sinks from there). */
    private static final EntityDataAccessor<Float> HELD =
            SynchedEntityData.defineId(KingsrimeSwordCrownEntity.class, EntityDataSerializers.FLOAT);

    @Nullable
    private UUID ownerId;
    private final Set<UUID> struck = new HashSet<>();

    public KingsrimeSwordCrownEntity(EntityType<? extends KingsrimeSwordCrownEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static KingsrimeSwordCrownEntity raise(ServerLevel level, ServerPlayer p, int fullTicks) {
        KingsrimeSwordCrownEntity c = new KingsrimeSwordCrownEntity(FFEntities.KINGSRIME_CROWN.get(), level);
        c.ownerId = p.getUUID();
        c.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0F);
        c.entityData.set(FULL, Math.max(1, fullTicks));
        c.entityData.set(OWNER, p.getId());
        level.addFreshEntity(c);
        return c;
    }

    public int state() {
        return entityData.get(STATE);
    }

    public int since() {
        return entityData.get(SINCE);
    }

    public int fullTicks() {
        return entityData.get(FULL);
    }

    public int ownerId() {
        return entityData.get(OWNER);
    }

    public float held() {
        return entityData.get(HELD);
    }

    /** How whole it is, 0..1, at `t` (ticks of its life, fractional). */
    public float gathered(float t) {
        if (state() != GATHER) {
            return held();
        }
        return Mth.clamp(t / fullTicks(), 0.0F, 1.0F);
    }

    /** The burst's ring, `t` ticks into its life: from its own circle out to REACH, fast then slowing. */
    public float ring(float t) {
        float f = Mth.clamp((t - since()) / RUN, 0.0F, 1.0F);
        return RADIUS + (REACH - RADIUS) * (1.0F - (1.0F - f) * (1.0F - f));
    }

    public void burst() {
        if (state() == GATHER) {
            entityData.set(HELD, 1.0F);
            entityData.set(STATE, BURST);
            entityData.set(SINCE, tickCount);
        }
    }

    public void sink() {
        if (state() == GATHER) {
            entityData.set(HELD, gathered(tickCount));
            entityData.set(STATE, SINK);
            entityData.set(SINCE, tickCount);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        Player owner = ownerId != null ? s.getPlayerByUUID(ownerId) : null;
        switch (state()) {
            case GATHER -> {
                // it is only gathered while he holds it: anything else and it sinks (a death, a switched slot...)
                if (owner == null || !owner.isAlive() || !owner.isUsingItem()
                        || !(owner.getUseItem().getItem() instanceof KingsrimeSwordItem) || owner.level() != s) {
                    sink();
                    return;
                }
                setPos(owner.getX(), owner.getY(), owner.getZ());
            }
            case BURST -> {
                int run = tickCount - since();
                if (run <= RUN && owner != null) {
                    run(s, owner, ring(tickCount));
                }
                if (run == 0) {
                    s.sendParticles(FFParticles.ICE_SHARD.get(), getX(), getY() + 0.6D, getZ(), 40, RADIUS, 0.4D, RADIUS,
                            0.2D);
                }
                if (run > BURST_LIFE) {
                    discard();
                }
            }
            default -> {
                if (tickCount - since() > SINK_LIFE) {
                    discard();
                }
            }
        }
    }

    private void run(ServerLevel s, Player owner, float r) {
        for (LivingEntity v : s.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r + 1.5D, 3.0D, r + 1.5D),
                e -> KingsrimeSwordItem.foe(owner, e))) {
            if (struck.contains(v.getUUID())) {
                continue;
            }
            double d = Math.hypot(v.getX() - getX(), v.getZ() - getZ());
            if (d > r + 0.3D + v.getBbWidth() * 0.5D || Math.abs(v.getY() - getY()) > 2.5D) {
                continue;
            }
            struck.add(v.getUUID());
            KingsrimeSwordItem.cut(owner, v, KingsrimeSwordItem.CROWN_DMG, 120, 1);
            if (v.isAlive()) {
                KingsrimeSwordShackleEntity.bind(s, v, ROOT);
            }
            s.sendParticles(FFParticles.ICE_SHARD.get(), v.getX(), v.getY(0.5D), v.getZ(), 14, 0.3D, 0.4D, 0.3D, 0.15D);
        }
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(STATE, GATHER);
        entityData.define(SINCE, 0);
        entityData.define(FULL, 18);
        entityData.define(OWNER, -1);
        entityData.define(HELD, 0.0F);
    }

    @Override
    public boolean isPickable() {
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
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    /** Where it stands for the renderer: on its owner while it gathers. */
    @Nullable
    public Entity owner() {
        int id = ownerId();
        return id >= 0 ? level().getEntity(id) : null;
    }
}
