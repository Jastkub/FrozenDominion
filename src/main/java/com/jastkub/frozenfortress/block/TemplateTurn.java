package com.jastkub.frozenfortress.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * HOW FAR A STRUCTURE WAS TURNED WHEN IT WAS PLACED. Worldgen turns a jigsaw piece (the watchtower,
 * the camp) any of four ways, and a template turns its blocks and their block states with it - but
 * not what its block entities keep in their own NBT (a gate's facing and the boxes it shuts, a
 * statue's yaw). Seen (05.10.2026): the watchtower's gate hung across the passage
 * instead of across the doorway.
 *
 * <p>So the blocks that keep a direction of their own carry {@link #FACING} too: written NORTH by
 * the generator, it is turned with the template, and what their block entity finds in it when it
 * is first loaded is how many quarter turns to apply to its NBT (once - it saves "Turned").
 */
public final class TemplateTurn {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private TemplateTurn() {
    }

    /** Quarter turns clockwise the template was placed with (0 when not turned, or not one of these). */
    public static int quarters(BlockState state) {
        if (!state.hasProperty(FACING)) {
            return 0;
        }
        return switch (state.getValue(FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    public static BlockState rotate(BlockState state, Rotation rotation) {
        return state.hasProperty(FACING) ? state.setValue(FACING, rotation.rotate(state.getValue(FACING))) : state;
    }

    /** (x, z) turned as a template turns a block: a clockwise quarter takes (x, z) to (-z, x). */
    public static int[] turn(int x, int z, int k) {
        for (int i = 0; i < (k & 3); i++) {
            int t = x;
            x = -z;
            z = t;
        }
        return new int[]{x, z};
    }

    /** A box of cells {x0, y0, z0, x1, y1, z1} turned; `exclusive`: its far corner is one past its last cell. */
    public static int[] box(int[] b, int k, boolean exclusive) {
        if (b == null || b.length < 6 || (k & 3) == 0) {
            return b;
        }
        int e = exclusive ? 1 : 0;
        int[] a = turn(b[0], b[2], k);
        int[] c = turn(b[3] - e, b[5] - e, k);
        return new int[]{Math.min(a[0], c[0]), b[1], Math.min(a[1], c[1]),
                Math.max(a[0], c[0]) + e, b[4], Math.max(a[1], c[1]) + e};
    }

    public static Direction turn(Direction d, int k) {
        if (d.getAxis().isVertical()) {
            return d;
        }
        for (int i = 0; i < (k & 3); i++) {
            d = d.getClockWise();
        }
        return d;
    }
}
