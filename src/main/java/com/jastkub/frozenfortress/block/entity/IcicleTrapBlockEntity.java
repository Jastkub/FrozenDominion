package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.entity.projectile.TrapIcicleEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * AN ICICLE TRAP: hidden under a cracked plate of the
 * floor, it keeps the icicles hanging over it. Step on the plate and they let go: each a TrapIcicleEntity - its shadow
 * on the floor for a second and a half, a shudder where it hangs, then the fall. Half a minute later they have grown
 * back and it is set again.
 *
 * <p>NBT (from the citadel's generator): Trigger - the plate, a box relative to this block (far corner exclusive);
 * Icicles - per icicle its tip's cell relative to this block and its length (x, y, z, n, ...); Floor - the floor's face,
 * relative y.
 */
public class IcicleTrapBlockEntity extends BlockEntity {

    /** Ticks until the icicles have grown back. */
    private static final int REARM = 600;

    private int[] trigger = {0, 0, 0, 0, 0, 0};
    private int[] icicles = new int[0];
    private int floor;
    private int rearm;
    /** What it took down, to put back: each {Pos, State}. */
    private final List<CompoundTag> taken = new ArrayList<>();

    public IcicleTrapBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.ICICLE_TRAP.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IcicleTrapBlockEntity trap) {
        if (!(level instanceof ServerLevel s) || level.getGameTime() % 2 != 0) {
            return;
        }
        if (trap.rearm > 0) {
            trap.rearm -= 2;
            if (trap.rearm <= 0) {
                trap.rearm = 0;
                trap.grow(s);
            }
            trap.setChanged();
            return;
        }
        AABB plate = new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(pos.offset(trap.trigger[0], trap.trigger[1], trap.trigger[2])), net.minecraft.world.phys.Vec3.atLowerCornerOf(pos.offset(trap.trigger[3], trap.trigger[4], trap.trigger[5])));
        for (Player p : s.players()) {
            if (p.isAlive() && !p.isCreative() && !p.isSpectator() && plate.contains(p.position())) {
                trap.fire(s);
                return;
            }
        }
    }

    /** The plate gives under a foot: the icicles over it let go. */
    private void fire(ServerLevel s) {
        BlockPos pos = worldPosition;
        s.playSound(null, pos.above(2), FFSounds.ICE_CRACK.get(), SoundSource.BLOCKS, 1.2F, 0.6F);
        double floorY = pos.getY() + floor;
        for (int i = 0; i + 3 < icicles.length; i += 4) {
            BlockPos tip = pos.offset(icicles[i], icicles[i + 1], icicles[i + 2]);
            int n = Math.max(1, icicles[i + 3]);
            boolean any = false;
            for (int k = 0; k < n; k++) {
                BlockPos q = tip.above(k);
                BlockState st = s.getBlockState(q);
                if (!st.isAir()) {
                    CompoundTag t = new CompoundTag();
                    t.put("Pos", com.jastkub.frozenfortress.util.FFNbt.pos(q));
                    t.put("State", NbtUtils.writeBlockState(st));
                    taken.add(t);
                    s.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
                    any = true;
                }
            }
            if (any) {
                s.addFreshEntity(new TrapIcicleEntity(s, tip.getX() + 0.5D, floorY, tip.getZ() + 0.5D,
                        tip.getY() + n - floorY).length(n));
            }
        }
        rearm = REARM;
        setChanged();
    }

    /** Grown back: the icicles hang again where they were (where nothing has been put in their place). */
    private void grow(ServerLevel s) {
        for (CompoundTag t : taken) {
            BlockPos q = com.jastkub.frozenfortress.util.FFNbt.pos(t.getCompound("Pos"));
            if (s.getBlockState(q).isAir()) {
                s.setBlock(q, NbtUtils.readBlockState(s.holderLookup(Registries.BLOCK), t.getCompound("State")), 3);
            }
        }
        taken.clear();
        setChanged();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Trigger")) trigger = tag.getIntArray("Trigger");
        icicles = tag.getIntArray("Icicles");
        floor = tag.getInt("Floor");
        rearm = tag.getInt("Rearm");
        taken.clear();
        for (Tag t : tag.getList("Taken", Tag.TAG_COMPOUND)) {
            taken.add((CompoundTag) t);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray("Trigger", trigger);
        tag.putIntArray("Icicles", icicles);
        tag.putInt("Floor", floor);
        tag.putInt("Rearm", rearm);
        ListTag list = new ListTag();
        list.addAll(taken);
        tag.put("Taken", list);
    }
}
