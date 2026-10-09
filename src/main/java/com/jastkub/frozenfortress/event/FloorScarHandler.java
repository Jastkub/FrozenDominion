package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.registry.FFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Cracks laid over the floor where something heavy landed, cleared again on
 * a timer.
 *
 * <p>The timed store-and-restore is the pattern Mowzie's Mobs uses for the
 * same job. What is different here is that nothing gets swapped: the crack
 * is a paper-thin decal block placed <em>on top</em> of the floor. Swapping
 * the floor itself only worked on blocks we were willing to replace, and
 * the throne room is sealed masonry that must never be touched - which
 * meant the scar was invisible in the one arena that matters. A decal shows
 * up over anything, including ice, and can never weaken what is under it.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class FloorScarHandler {

    /** How long a crack stays before it fades. */
    private static final int SCAR_TICKS = 110;
    /** A ceiling, so a long fight cannot queue unbounded work. */
    private static final int MAX_PENDING = 3000;

    private record Scar(ServerLevel level, BlockPos pos, long restoreAt) {
    }

    private static final Deque<Scar> PENDING = new ArrayDeque<>();
    private static final Set<BlockPos> MARKED = new HashSet<>();

    /**
     * Cracks the ground around {@code centre}.
     *
     * @param density 0..1 of the disc that actually breaks; below 1 it frays
     *                at the edges instead of stamping a perfect circle.
     */
    public static void tear(ServerLevel level, Vec3 centre, double radius, double density) {
        // Splits radiating outward, not a filled disc.
        //
        // Filling the circle looked like a stain: every block cracked, no
        // shape to it. A struck floor throws long fingers out from the point
        // of impact and forks as they go, so that is what gets drawn - and
        // it is what makes the whole thing read as one web rather than a
        // grid of identical tiles.
        mark(level, BlockPos.containing(centre));
        int spokes = Math.max(5, (int) Math.round(radius * 2.2D * density));
        double spin = level.random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < spokes; i++) {
            double ang = spin + Math.PI * 2.0D * i / spokes
                    + (level.random.nextDouble() - 0.5D) * 0.5D;
            double reach = radius * (0.55D + level.random.nextDouble() * 0.45D);
            walk(level, centre, ang, reach, 2);
        }
    }

    /** One finger of the break, wobbling outward and occasionally forking. */
    private static void walk(ServerLevel level, Vec3 from, double angle,
                             double reach, int depth) {
        double x = from.x, z = from.z;
        double dx = Math.cos(angle), dz = Math.sin(angle);
        int steps = (int) Math.ceil(reach);
        for (int i = 0; i < steps; i++) {
            x += dx;
            z += dz;
            mark(level, BlockPos.containing(x, from.y, z));
            // drift, so no two fingers look alike
            double turn = (level.random.nextDouble() - 0.5D) * 0.55D;
            double ndx = dx * Math.cos(turn) - dz * Math.sin(turn);
            double ndz = dx * Math.sin(turn) + dz * Math.cos(turn);
            dx = ndx;
            dz = ndz;
            if (depth > 0 && i > 0 && level.random.nextDouble() < 0.3D) {
                double fork = angle + (level.random.nextBoolean() ? 0.9D : -0.9D);
                walk(level, new Vec3(x, from.y, z), fork, reach * 0.45D, depth - 1);
            }
        }
    }

    /**
     * Cracks one column. {@code at} is around foot height; the crack is laid
     * on whichever solid surface is directly under it.
     */
    public static void mark(ServerLevel level, BlockPos at) {
        if (PENDING.size() >= MAX_PENDING) {
            return;
        }
        // Look a little up and down, so this works on stairs and slabs and
        // still finds the floor when called from chest height.
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos slot = at.offset(0, dy, 0);
            BlockPos below = slot.below();
            if (!level.isLoaded(slot)) {
                return;
            }
            BlockState here = level.getBlockState(slot);
            BlockState floor = level.getBlockState(below);
            boolean freeSlot = here.isAir()
                    || here.getBlock() == FFBlocks.FLOOR_CRACK.get();
            if (!freeSlot || !floor.isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) {
                continue;
            }
            if (here.getBlock() == FFBlocks.FLOOR_CRACK.get() || MARKED.contains(slot)) {
                return;     // already cracked; leave its original timer alone
            }
            level.setBlock(slot, FFBlocks.FLOOR_CRACK.get().defaultBlockState(),
                    Block.UPDATE_CLIENTS);
            MARKED.add(slot.immutable());
            PENDING.add(new Scar(level, slot.immutable(), level.getGameTime() + SCAR_TICKS));
            return;
        }
    }

    /** Draws a crack along a line - used by the blade being dragged. */
    public static void trail(ServerLevel level, Vec3 from, Vec3 to, double spread) {
        // A continuous line with short ribs coming off it - the furrow a
        // dragged blade leaves, rather than a dotted row of stamps.
        double length = from.distanceTo(to);
        int steps = (int) Math.ceil(length * 1.6D);
        Vec3 dir = to.subtract(from);
        double ang = Math.atan2(dir.z, dir.x);
        for (int i = 0; i <= steps; i++) {
            Vec3 at = from.lerp(to, steps == 0 ? 0.0D : (double) i / steps);
            mark(level, BlockPos.containing(at));
            if (spread > 0.0D && level.random.nextDouble() < 0.35D) {
                double side = ang + (level.random.nextBoolean() ? 1.35D : -1.35D);
                walk(level, at, side, 1.0D + level.random.nextDouble() * spread * 2.0D, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || PENDING.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        while (!PENDING.isEmpty()) {
            Scar scar = PENDING.peek();
            if (scar.level() != level || scar.restoreAt() > now) {
                break;
            }
            PENDING.poll();
            MARKED.remove(scar.pos());
            if (level.isLoaded(scar.pos())
                    && level.getBlockState(scar.pos()).getBlock() == FFBlocks.FLOOR_CRACK.get()) {
                level.removeBlock(scar.pos(), false);
            }
        }
    }

    private FloorScarHandler() {
    }
}
