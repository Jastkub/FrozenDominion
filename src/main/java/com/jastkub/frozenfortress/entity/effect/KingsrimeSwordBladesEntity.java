package com.jastkub.frozenfortress.entity.effect;

import com.jastkub.frozenfortress.item.KingsrimeSwordItem;
import com.jastkub.frozenfortress.registry.FFEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * KRÓLEWSKI KROK (Royal Step) - what the Kingsrime sword's sneak + use leaves behind: the wielder carried six blocks
 * in three ticks (this entity walks him through the points the item worked out, KingsrimeSwordItem#stepPath), and
 * along the way he went a line of ice blades left hanging in the air, each where he passed it. They hang, trembling
 * and brightening, half a second after he is through - and BURST: a line of light cut along the whole path, the blades
 * flung apart in shards (KingsrimeSwordBladesRenderer draws all of it).
 *
 * <p>The burst cuts everyone within reach of the path, and everyone the step went through wherever they have got to
 * by then (they were marked as he passed).
 */
public class KingsrimeSwordBladesEntity extends Entity {

    /** The burst's tick (from the step), its reach either side of the path, and how long its shards fly. */
    public static final int BURST = KingsrimeSwordItem.STEP_T + 10, AFTER = 10;
    public static final double REACH = 1.6D;

    private static final EntityDataAccessor<Float> YAW =
            SynchedEntityData.defineId(KingsrimeSwordBladesEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LENGTH =
            SynchedEntityData.defineId(KingsrimeSwordBladesEntity.class, EntityDataSerializers.FLOAT);
    /** How far the path climbed by its end (steps, a block). */
    private static final EntityDataAccessor<Float> RISE =
            SynchedEntityData.defineId(KingsrimeSwordBladesEntity.class, EntityDataSerializers.FLOAT);

    @Nullable
    private UUID ownerId;
    private final List<Vec3> path = new ArrayList<>();
    private final Set<UUID> marked = new HashSet<>();

    public KingsrimeSwordBladesEntity(EntityType<? extends KingsrimeSwordBladesEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static KingsrimeSwordBladesEntity lay(ServerLevel level, ServerPlayer p, Vec3 from, List<Vec3> path,
                                                 List<LivingEntity> passed) {
        KingsrimeSwordBladesEntity b = new KingsrimeSwordBladesEntity(FFEntities.KINGSRIME_BLADES.get(), level);
        Vec3 to = path.get(path.size() - 1);
        Vec3 flat = new Vec3(to.x - from.x, 0.0D, to.z - from.z);
        float yaw = (float) (Mth.atan2(flat.z, flat.x) * (180.0D / Math.PI)) - 90.0F;
        b.ownerId = p.getUUID();
        b.path.addAll(path);
        for (LivingEntity v : passed) {
            b.marked.add(v.getUUID());
        }
        b.moveTo(from.x, from.y, from.z, yaw, 0.0F);
        b.entityData.set(YAW, yaw);
        b.entityData.set(LENGTH, (float) flat.length());
        b.entityData.set(RISE, (float) (to.y - from.y));
        level.addFreshEntity(b);
        return b;
    }

    public float pathYaw() {
        return entityData.get(YAW);
    }

    public float pathLength() {
        return entityData.get(LENGTH);
    }

    public float rise() {
        return entityData.get(RISE);
    }

    /** How many blades it hangs: about one to the block and a third. */
    public int count() {
        return Math.max(3, Math.round(pathLength() * 0.75F) + 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel s)) {
            return;
        }
        int t = tickCount;
        ServerPlayer owner = ownerId != null && s.getPlayerByUUID(ownerId) instanceof ServerPlayer sp ? sp : null;
        // the step: one of its points a tick, his look left as it is
        if (owner != null && t >= 1 && t <= path.size() && owner.isAlive() && owner.level() == s) {
            Vec3 at = path.get(t - 1);
            owner.connection.teleport(at.x, at.y, at.z, 0.0F, 0.0F, EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
            owner.fallDistance = 0.0F;
            if (t == path.size()) {
                Vec3 dir = Vec3.directionFromRotation(0.0F, pathYaw());
                owner.setDeltaMovement(dir.x * 0.35D, 0.0D, dir.z * 0.35D);   // a little of the step carried out of it
                owner.hurtMarked = true;
            }
        }
        if (t == BURST) {
            burst(s, owner);
        }
        if (t > BURST + AFTER) {
            discard();
        }
    }

    private void burst(ServerLevel s, @Nullable ServerPlayer owner) {
        Vec3 a = position().add(0.0D, 1.0D, 0.0D);
        Vec3 dir = Vec3.directionFromRotation(0.0F, pathYaw());
        Vec3 b = a.add(dir.scale(pathLength())).add(0.0D, rise(), 0.0D);
        Vec3 mid = a.add(b).scale(0.5D);
        s.playSound(null, mid.x, mid.y, mid.z, FFSounds.KINGSRIME_BLADES_BURST.get(), SoundSource.PLAYERS, 1.3F,
                0.95F + random.nextFloat() * 0.1F);
        for (int i = 0; i <= 6; i++) {
            Vec3 p = a.add(b.subtract(a).scale(i / 6.0D));
            s.sendParticles(FFParticles.ICE_SHARD.get(), p.x, p.y + 0.2D, p.z, 4, 0.3D, 0.3D, 0.3D, 0.1D);
        }
        if (owner == null) {
            return;
        }
        AABB box = new AABB(a, b).inflate(REACH + 1.0D, 1.6D, REACH + 1.0D);
        AABB wide = new AABB(a, b).inflate(10.0D, 4.0D, 10.0D);
        for (LivingEntity v : s.getEntitiesOfClass(LivingEntity.class, wide, e -> KingsrimeSwordItem.foe(owner, e))) {
            boolean in = false;
            if (box.intersects(v.getBoundingBox())) {
                Vec3 c = v.position().add(0.0D, v.getBbHeight() * 0.5D, 0.0D);
                double d = segmentDistance(a, b, c);
                in = d <= REACH + v.getBbWidth() * 0.5D + Math.max(0.0D, v.getBbHeight() - 2.0D) * 0.5D;
            }
            if (!in && !marked.contains(v.getUUID())) {
                continue;
            }
            KingsrimeSwordItem.cut(owner, v, KingsrimeSwordItem.STEP_DMG, 100, 1);
            Vec3 out = new Vec3(v.getX() - mid.x, 0.0D, v.getZ() - mid.z);
            if (out.lengthSqr() > 1.0E-4D) {
                out = out.normalize().scale(0.2D * com.jastkub.frozenfortress.registry.FFEnchantments.steady(v));
            }
            v.setDeltaMovement(v.getDeltaMovement().add(out.x, 0.3D, out.z));
            v.hurtMarked = true;
            s.sendParticles(FFParticles.ICE_SHARD.get(), v.getX(), v.getY(0.55D), v.getZ(), 12, 0.3D, 0.4D, 0.3D, 0.14D);
        }
    }

    private static double segmentDistance(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double t = len < 1.0E-6D ? 0.0D : Mth.clamp(p.subtract(a).dot(ab) / len, 0.0D, 1.0D);
        return a.add(ab.scale(t)).distanceTo(p);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(YAW, 0.0F);
        builder.define(LENGTH, 6.0F);
        builder.define(RISE, 0.0F);
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
}
