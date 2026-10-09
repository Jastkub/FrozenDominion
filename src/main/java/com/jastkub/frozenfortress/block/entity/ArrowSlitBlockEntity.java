package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.block.ArrowSlitBlock;
import com.jastkub.frozenfortress.entity.projectile.TrapArrowEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * AN ARROW SLIT: in a wall, it shoots a bolt of ice straight
 * across the passage on its beat - every Period ticks, Phase ticks into the beat - while somebody is near. The slits of
 * one passage share a beat, each a little later than the last: read the rhythm and go in its gaps. A click just before
 * it shoots is its tell.
 */
public class ArrowSlitBlockEntity extends BlockEntity {

    private int period = 40;
    private int phase;
    /** Shoots only while somebody is this near (and not when nobody would see it). */
    private static final double NEAR = 16.0D;
    private static final int TELL = 8;
    private static final float SPEED = 1.6F;

    public ArrowSlitBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.ARROW_SLIT.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ArrowSlitBlockEntity slit) {
        if (!(level instanceof ServerLevel s)) {
            return;
        }
        int beat = (int) Math.floorMod(level.getGameTime() - slit.phase, (long) Math.max(10, slit.period));
        if (beat != 0 && beat != slit.period - TELL) {
            return;
        }
        Player near = s.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, NEAR,
                e -> e instanceof Player p && !p.isCreative() && !p.isSpectator());
        if (near == null) {
            return;
        }
        if (beat != 0) {
            s.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.9F, 1.6F);      // its tell
            return;
        }
        Direction out = state.getValue(ArrowSlitBlock.FACING);
        Vec3 dir = new Vec3(out.getStepX(), 0.0D, out.getStepZ());
        Vec3 from = Vec3.atCenterOf(pos).add(dir.scale(0.7D));
        s.addFreshEntity(new TrapArrowEntity(s, from, dir, SPEED));
        s.playSound(null, pos, SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 1.2F, 1.3F);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Period")) period = tag.getInt("Period");
        phase = tag.getInt("Phase");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Period", period);
        tag.putInt("Phase", phase);
    }
}
