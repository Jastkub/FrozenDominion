package com.jastkub.frozenfortress.entity.effect;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A chunk of floor thrown into the air. Purely something to look at.
 *
 * <p>Vanilla's FallingBlockEntity cannot be used for this: {@code fall()}
 * deletes the source block, and 1.20.1 exposes no way to cancel what a
 * falling block does when it lands, so a slam in the throne room would
 * quietly chew holes in a hand-built arena. Mowzie's Mobs hit the same wall
 * and solved it the same way - their Ferrous Wroughnaut throws its own
 * decorative entity rather than a real falling block.
 *
 * <p>So this owns nothing in the world: it renders a block state, falls,
 * and disappears when its timer runs out. It never places, never drops,
 * never damages.
 */
public class FallingDebrisEntity extends Entity {

    private static final EntityDataAccessor<Integer> BLOCK_ID =
            SynchedEntityData.defineId(FallingDebrisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFETIME =
            SynchedEntityData.defineId(FallingDebrisEntity.class, EntityDataSerializers.INT);

    // Matched to Cataclysm's Cm_Falling_Block_Entity. At 0.055 a block was up
    // and back down inside half a second, which is too quick to register as a
    // floor being lifted; 0.04 gives it the hang time the effect lives on.
    private static final double GRAVITY = 0.04D;
    private static final double DRAG = 0.98D;

    /**
     * Where it started, so it can be put back there.
     *
     * <p>THE @OnlyIn THAT USED TO SIT ON THIS FIELD CRASHED DEDICATED
     * SERVERS, and it was never meant to be here. It was written for the spin
     * fields below - the javadoc that went with it says so, "the renderer
     * spins each shard on its own axis" - and a second javadoc was then
     * inserted between the annotation and the field it was aimed at. An
     * annotation binds to whatever declaration follows it, so it landed on
     * restY instead, with nothing about the source reading as wrong.
     *
     * <p>Forge's RuntimeDistCleaner strips @OnlyIn members from mod classes
     * exactly as it does from vanilla's, so on a dedicated server this field
     * did not exist - while tick() below reads and writes it on both sides,
     * including inside a ServerLevel branch. The first lump of debris to tick
     * server-side threw NoSuchFieldError, and debris comes off floorBurst,
     * which nearly every heavy attack in the fight calls.
     *
     * <p>The annotation is gone rather than moved down. On the spin fields it
     * would be just as wrong: they are referenced from tick(), which is a
     * common method, and a common method referencing a member that is not
     * there on this side is the same crash with a different field name. The
     * isClientSide guard already does the job it was reaching for, and it
     * does it without removing anything from the class.
     */
    private double restY = Double.MIN_VALUE;
    public float spinYaw, spinPitch, prevSpinYaw, prevSpinPitch;

    public FallingDebrisEntity(EntityType<? extends FallingDebrisEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = false;
        this.noPhysics = true;      // it is scenery; it must never shove anything
    }

    public FallingDebrisEntity(Level level, BlockState state, double x, double y, double z,
                               double vx, double vy, double vz, int lifetime) {
        this(FFEntities.FALLING_DEBRIS.get(), level);
        setPos(x, y, z);
        setDeltaMovement(vx, vy, vz);
        setBlockState(state);
        setLifetime(lifetime);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(BLOCK_ID, Block.getId(Blocks.STONE.defaultBlockState()));
        entityData.define(LIFETIME, 40);
    }

    public BlockState getBlockState() {
        return Block.stateById(entityData.get(BLOCK_ID));
    }

    public void setBlockState(BlockState state) {
        entityData.set(BLOCK_ID, Block.getId(state));
    }

    public int getLifetime() {
        return entityData.get(LIFETIME);
    }

    public void setLifetime(int ticks) {
        entityData.set(LIFETIME, ticks);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            prevSpinYaw = spinYaw;
            prevSpinPitch = spinPitch;
            spinYaw += 11.0F;
            spinPitch += 7.0F;
        }
        if (tickCount > getLifetime()) {
            discard();
            return;
        }
        setDeltaMovement(getDeltaMovement().multiply(DRAG, 1.0D, DRAG).subtract(0.0D, GRAVITY, 0.0D));
        setPos(getX() + getDeltaMovement().x,
               getY() + getDeltaMovement().y,
               getZ() + getDeltaMovement().z);

        // AND IT COMES BACK DOWN ONTO THE FLOOR IT LEFT.
        //
        // noPhysics is on - it has to be, or a hundred lumps of stone would
        // shove the player around - but that also meant nothing stopped them
        // falling THROUGH the ground and winking out somewhere under the
        // room. The floor erupted and then simply forgot about it.
        //
        // So the launch height is remembered and the fall ends there: the
        // block settles back into the hole it came out of, throws a little
        // dust, and goes. That return is the half of the effect that was
        // missing - ground that is thrown up and never lands reads as
        // decoration, ground that comes back reads as a blow the room took.
        if (restY == Double.MIN_VALUE) {
            restY = getY();
        } else if (getDeltaMovement().y < 0.0D && getY() <= restY) {
            setPos(getX(), restY, getZ());
            if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                                net.minecraft.core.particles.ParticleTypes.BLOCK, getBlockState()),
                        getX(), restY + 0.1D, getZ(), 6, 0.22D, 0.05D, 0.22D, 0.02D);
            }
            discard();
        }
    }

    /** Rendered from any distance a boss fight happens at. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(BLOCK_ID, tag.getInt("BlockId"));
        entityData.set(LIFETIME, tag.getInt("Lifetime"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("BlockId", entityData.get(BLOCK_ID));
        tag.putInt("Lifetime", entityData.get(LIFETIME));
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }
}
