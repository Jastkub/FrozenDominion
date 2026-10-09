package com.jastkub.frozenfortress.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * A block position in NBT the way 1.20.1 wrote it - a compound of X, Y and Z - both ways (the 1.21.1 port). Vanilla's
 * NbtUtils now writes an int array and reads back an Optional, so the mod keeps its own form: one shape in every save,
 * whichever version wrote it.
 */
public final class FFNbt {

    private FFNbt() {
    }

    public static CompoundTag pos(BlockPos p) {
        CompoundTag t = new CompoundTag();
        t.putInt("X", p.getX());
        t.putInt("Y", p.getY());
        t.putInt("Z", p.getZ());
        return t;
    }

    public static BlockPos pos(CompoundTag t) {
        return new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z"));
    }
}
