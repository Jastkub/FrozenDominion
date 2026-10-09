package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.registry.FFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays rime on the floor, and takes it back off.
 *
 * <p>Both halves live here rather than in the entity because they are two
 * views of one rule about what counts as floor: a sturdy top face with a free
 * slot above it. Written twice, they drift, and the drift is invisible - the
 * colossus quietly failing to drain a patch it put down itself reads as the
 * heal being broken, not as two functions disagreeing about a ledge.
 */
public final class RimeHandler {

    private RimeHandler() {
    }

    /** How far below a point to look for something to lie on. */
    private static final int REACH_DOWN = 3;

    /**
     * Frosts the ground around {@code centre}.
     *
     * <p>Grown from the middle outward rather than stamped as a disc, and
     * thinned with distance, so a patch has a soft edge and two overlapping
     * patches read as one field instead of as two circles.
     *
     * @return how many blocks were actually laid
     */
    public static int freeze(ServerLevel level, Vec3 centre, double radius) {
        int laid = 0;
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius) {
                    continue;
                }
                // thinner toward the rim: one in five at the edge, all of it
                // in the middle
                if (level.random.nextDouble() > 1.05D - d / radius) {
                    continue;
                }
                if (lay(level, BlockPos.containing(centre.x + dx, centre.y, centre.z + dz))) {
                    laid++;
                }
            }
        }
        return laid;
    }

    /** One column: find the floor under it and put frost on top. */
    private static boolean lay(ServerLevel level, BlockPos from) {
        for (int dy = 1; dy >= -REACH_DOWN; dy--) {
            BlockPos slot = from.offset(0, dy, 0);
            BlockState here = level.getBlockState(slot);
            if (!here.isAir() && here.getBlock() != FFBlocks.RIME_SHEET.get()) {
                continue;
            }
            if (here.getBlock() == FFBlocks.RIME_SHEET.get()) {
                return false;                       // already frosted
            }
            BlockPos below = slot.below();
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                continue;
            }
            level.setBlock(slot, FFBlocks.RIME_SHEET.get().defaultBlockState(),
                    Block.UPDATE_CLIENTS);
            return true;
        }
        return false;
    }

    /**
     * Takes rime back up, nearest first, and says how much it got.
     *
     * <p>NEAREST FIRST is not tidiness. It is what makes the drain readable:
     * the white retreats toward the colossus from the outside in, so a player
     * watching it can see how much longer it has left to feed and decide
     * whether to interrupt or to spend the time clearing the far side.
     *
     * @param max the most it may take this call - the drain is a rate, not an
     *            instant, or the whole field vanishes on one frame
     */
    public static int drain(ServerLevel level, Vec3 centre, double radius, int max) {
        int r = (int) Math.ceil(radius);
        List<BlockPos> found = new ArrayList<>();
        BlockPos mid = BlockPos.containing(centre);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -REACH_DOWN; dy <= 2; dy++) {
                    BlockPos at = mid.offset(dx, dy, dz);
                    if (level.getBlockState(at).getBlock() == FFBlocks.RIME_SHEET.get()) {
                        found.add(at);
                    }
                }
            }
        }
        found.sort((a, b) -> Double.compare(a.distToCenterSqr(centre),
                                            b.distToCenterSqr(centre)));
        int took = 0;
        for (BlockPos at : found) {
            if (took >= max) {
                break;
            }
            level.removeBlock(at, false);
            took++;
        }
        return took;
    }

    /** How much rime is standing within reach, without touching any of it. */
    public static int count(ServerLevel level, Vec3 centre, double radius) {
        int r = (int) Math.ceil(radius);
        BlockPos mid = BlockPos.containing(centre);
        int n = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -REACH_DOWN; dy <= 2; dy++) {
                    if (level.getBlockState(mid.offset(dx, dy, dz)).getBlock()
                            == FFBlocks.RIME_SHEET.get()) {
                        n++;
                    }
                }
            }
        }
        return n;
    }
}
