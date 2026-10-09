package com.jastkub.frozenfortress.entity;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A block of the cisterns' stair torn out as it goes down into the water (StairWardBlockEntity): it falls the way a
 * falling block does, drawn as the block it was - and never lands as one. On the ice or in the water it shatters
 * (the block's own breaking, heard and seen) and is gone.
 */
public class StairDebrisEntity extends FallingBlockEntity {

    public StairDebrisEntity(EntityType<? extends StairDebrisEntity> type, Level level) {
        super(type, level);
    }

    /** The block at `pos` (already taken out of the world by the caller) goes down, given a push. */
    public static void fall(Level level, BlockPos pos, BlockState state, Vec3 push) {
        StairDebrisEntity d = new StairDebrisEntity(FFEntities.STAIR_DEBRIS.get(), level);
        CompoundTag t = new CompoundTag();
        t.put("BlockState", NbtUtils.writeBlockState(state));
        t.putInt("Time", 1);
        t.putBoolean("DropItem", false);
        t.putBoolean("CancelDrop", true);
        d.readAdditionalSaveData(t);
        d.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        d.xo = d.getX();
        d.yo = d.getY();
        d.zo = d.getZ();
        d.setDeltaMovement(push);
        d.setStartPos(pos);
        level.addFreshEntity(d);
    }

    @Override
    public void tick() {
        if (getBlockState().isAir()) {
            discard();
            return;
        }
        setDeltaMovement(getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().multiply(0.9D, 0.98D, 0.9D));
        if (!level().isClientSide && (onGround() || tickCount > 80
                || level().getFluidState(blockPosition()).is(FluidTags.WATER))) {
            level().levelEvent(2001, blockPosition(), Block.getId(getBlockState()));
            discard();
        }
    }
}
