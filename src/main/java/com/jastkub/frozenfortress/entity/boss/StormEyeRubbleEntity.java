package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A PIECE OF THE ROOF (07.10.2026): what the ascent tears out of the dome falls as bodies - each one the block it was
 * (stone, ice, glass, a lantern), turning as it drops into the hall, and it breaks where it lands (the dust of it is
 * only the accompaniment). Drawn by StormEyeRenderers.Rubble.
 *
 * <p>The fall is the same arithmetic on both sides (gravity and drag, no randomness in the tick), so the client moves
 * its own copy smoothly from the velocity it was spawned with and the server's rare corrections are not seen. It
 * touches nobody: it is scenery, and the roof is not a weapon.
 */
public class StormEyeRubbleEntity extends StormEyeFxEntity {

    public static final int LIFE = 140;
    static final double GRAVITY = 0.045D, DRAG = 0.985D;

    private static final EntityDataAccessor<Integer> BLOCK =
            SynchedEntityData.defineId(StormEyeRubbleEntity.class, EntityDataSerializers.INT);

    public float spin, spinO;

    public StormEyeRubbleEntity(EntityType<? extends StormEyeRubbleEntity> type, Level level) {
        super(type, level);
    }

    /**
     * Its type, looked up by name rather than by a registry field, so this class compiles (and the roof simply drops
     * no pieces) until storm_eye_rubble is registered.
     */
    @SuppressWarnings("unchecked")
    @javax.annotation.Nullable
    static EntityType<StormEyeRubbleEntity> type() {
        net.minecraft.resources.ResourceLocation id = com.jastkub.frozenfortress.FrozenFortress.id("storm_eye_rubble");
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                ? (EntityType<StormEyeRubbleEntity>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(id)
                : null;
    }

    @javax.annotation.Nullable
    public static StormEyeRubbleEntity drop(ServerLevel level, BlockState state, double x, double y, double z,
                                            double vx, double vy, double vz) {
        EntityType<StormEyeRubbleEntity> type = type();
        if (type == null) {
            return null;
        }
        StormEyeRubbleEntity r = new StormEyeRubbleEntity(type, level);
        r.setPos(x, y, z);
        r.setDeltaMovement(vx, vy, vz);
        r.entityData.set(BLOCK, Block.getId(state));
        level.addFreshEntity(r);
        return r;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(BLOCK, Block.getId(Blocks.STONE.defaultBlockState()));
    }

    public BlockState block() {
        return Block.stateById(entityData.get(BLOCK));
    }

    @Override
    public void tick() {
        super.tick();
        spinO = spin;
        spin += 9.0F + (getId() % 7);
        Vec3 v = getDeltaMovement();
        v = new Vec3(v.x * DRAG, v.y * DRAG - GRAVITY, v.z * DRAG);
        setDeltaMovement(v);
        Vec3 next = position().add(v);
        BlockPos at = BlockPos.containing(next);
        boolean solid = tickCount > 1 && !level().getBlockState(at).getCollisionShape(level(), at).isEmpty();
        if (solid) {
            if (!level().isClientSide) {
                land((ServerLevel) level());
            } else {
                setDeltaMovement(Vec3.ZERO);
            }
            return;
        }
        setPos(next.x, next.y, next.z);
        if (!level().isClientSide && tickCount > LIFE) {
            discard();
        }
    }

    private void land(ServerLevel sl) {
        BlockState st = block();
        if (!st.isAir()) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), getX(), getY() + 0.2D, getZ(),
                    10, 0.25D, 0.1D, 0.25D, 0.06D);
        }
        if ((getId() & 3) == 0) {
            sl.playSound(null, getX(), getY(), getZ(), FFSounds.ICE_CRACK.get(), SoundSource.BLOCKS, 0.7F,
                    0.7F + (getId() % 5) * 0.08F);
        }
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0D * 96.0D;
    }
}
