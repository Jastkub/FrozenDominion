package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.registry.FFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KRA BURZY - A FLOE OF THE EYE OF THE STORM THAT MOVES. The sixteen floes of the two rings are bodies,
 * not blocks: blocks cannot glide. The central floe stays blocks (StormEyeFloeBlock) - it never moves, never melts.
 *
 * <p>WHERE IT IS, IS A PURE FUNCTION OF THE CLOCK ({@link Motion}): its ring turns at a steady rate (the inner ring one
 * way, the outer the other), it turns slowly about its own middle, it breathes up and down, and now and then the arena
 * eases it a step up or down (the lift: from, to, when, how long). Every number of that is synced once, when it
 * changes; both sides put the floe where the same function says for the same time, so where it is never has to cross
 * the network and nobody sees it stutter. Position packets are ignored on the client ({@link #lerpTo}). The client
 * reads its own smoothed clock ({@link #clientClock}), so the server's time packet, once a second, never jerks the ice.
 *
 * <p>STANDING ON IT is the client's business. On the client the floe is solid - a round of seven boxes, its
 * {@link Part}s (Forge multipart, the way the dragon's body is hit) - and client/StormEyeFloeClient carries the player
 * with it. On the server it is not solid at all: the server checks every move a player makes against its own world,
 * and its floe, a breath ahead of the client's, would pull them back ("moved wrongly"). The arena knows who stands on
 * which floe by where they are.
 *
 * <p>ITS LOOK is the arena's state machine (SOLID, MARKED, CRACKED, SLUSH, GONE, FORMING), synced with the game time
 * the state began and how long it lasts. StormEyeFloeRenderer lays the floe block's own models out cell by cell - the
 * same lens as the old block floes - edge first when it goes, middle first when it comes, with vanilla's breaking
 * cracks over it as it melts and the ghost of new ice see-through until it sets.
 *
 * <p>Never saved: the arena makes them again from its own save (the clock goes on, so they come back where they were).
 * A floe no arena owns takes itself away ({@link #ORPHAN_TICKS}).
 */
public class StormEyeFloeEntity extends StormEyeFxEntity {

    /** How far a floe breathes up and down, round its rest. */
    public static final double BOB = 0.3D;
    /** The arena's states (StormEyeArena.SOLID ..), mirrored so the client reads them without the arena. */
    public static final int SOLID = 0, MARKED = 1, CRACKED = 2, SLUSH = 3, GONE = 4, FORMING = 5;
    /** Ticks a look spreads over the floe (StormEyeArena.SPREAD): edge first when it goes, middle first when it comes. */
    public static final int SPREAD = 8;
    /** Ticks a floe nobody owns lasts before it takes itself away. */
    static final int ORPHAN_TICKS = 100;
    /** The boxes the client stands a body on: corners round the disc, so together they make it round. */
    public static final int PARTS = 7;

    private static final EntityDataAccessor<BlockPos> CENTRE =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> SLOT =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RADIUS =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ORBIT =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ANGLE0 =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OMEGA =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPIN0 =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPIN_W =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BOB_PHASE =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BOB_W =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> EPOCH =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> LIFT_FROM =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LIFT_TO =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> LIFT_AT =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> LIFT_LEN =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PREV_STATE =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> STATE_AT =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> STATE_LEN =
            SynchedEntityData.defineId(StormEyeFloeEntity.class, EntityDataSerializers.INT);

    private final Part[] parts;
    private final Motion motion = new Motion();
    private boolean motionDirty = true;
    /** The arena that owns it (server; set when the arena makes or adopts it). */
    @Nullable
    private StormEyeArena arena;
    private int orphan;
    /** CLIENT: the clock values of the last two steps, and where it was and is - the carry and the drawing read them. */
    private double stepPrevT, stepT;
    private Vec3 stepFrom = Vec3.ZERO, stepTo = Vec3.ZERO;
    private double spinFrom, spinTo;
    private int stepGen = -1;

    public StormEyeFloeEntity(EntityType<? extends StormEyeFloeEntity> type, Level level) {
        super(type, level);
        this.noCulling = false;                                // a real box round it: culled like any body
        setNoGravity(true);
        this.parts = new Part[PARTS];
        for (int k = 0; k < PARTS; k++) {
            double a = Math.toRadians((k + 0.5D) * 90.0D / PARTS);
            parts[k] = new Part(this, Math.cos(a), Math.sin(a));
        }
        // the parts' ids follow the floe's (as the dragon's do): reserved here, so no other body is given them
        setId(ENTITY_COUNTER.getAndAdd(PARTS + 1) + 1);
    }

    /** A new floe for a slot of the arena centred on (cx, floorY, cz), moving as `m` says. */
    public static StormEyeFloeEntity make(ServerLevel level, StormEyeArena arena, int slot, int radius, int cx, int floorY,
                                          int cz, Motion m) {
        StormEyeFloeEntity f = new StormEyeFloeEntity(FFEntities.STORM_EYE_FLOE.get(), level);
        f.entityData.set(CENTRE, new BlockPos(cx, floorY, cz));
        f.entityData.set(SLOT, slot);
        f.entityData.set(RADIUS, radius);
        f.applyMotion(m);
        f.arena = arena;
        Vec3 at = m.at(level.getGameTime());
        f.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return f;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(CENTRE, BlockPos.ZERO);
        builder.define(SLOT, -1);
        builder.define(RADIUS, 3);
        builder.define(ORBIT, 0.0F);
        builder.define(ANGLE0, 0.0F);
        builder.define(OMEGA, 0.0F);
        builder.define(SPIN0, 0.0F);
        builder.define(SPIN_W, 0.0F);
        builder.define(BOB_PHASE, 0.0F);
        builder.define(BOB_W, 0.0F);
        builder.define(EPOCH, 0L);
        builder.define(LIFT_FROM, 0.0F);
        builder.define(LIFT_TO, 0.0F);
        builder.define(LIFT_AT, 0L);
        builder.define(LIFT_LEN, 1);
        builder.define(STATE, GONE);
        builder.define(PREV_STATE, GONE);
        builder.define(STATE_AT, -1000L);
        builder.define(STATE_LEN, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        motionDirty = true;
    }

    // ================================================================================================ the motion
    /**
     * WHERE A FLOE IS, as a function of the game time t - nothing else. Shared with StormEyeArena, which plans with it
     * (its numbers are floats on both sides, so the server and the client compute bit for bit the same place).
     */
    public static final class Motion {
        /** The arena's axis (a block's middle) and the floe top's resting height. */
        public double cx, cz, baseY;
        /** Ring radius, its angle at the epoch, radians a tick (the angle as atan2(z, x): + turns from +X to +Z). */
        public float orbit, a0, w;
        /** Its own slow turn about its middle: the angle at the epoch, radians a tick. */
        public float s0, sw;
        /** Its breath: phase at the epoch, radians a tick. */
        public float bp, bw;
        /** The game time every angle above is counted from (the arena's). */
        public long epoch;
        /** The lift the arena eases it through: from, to, the game time it starts, ticks it takes. */
        public float liftFrom, liftTo;
        public long liftAt;
        public int liftLen = 1;

        public double angle(double t) {
            return a0 + (double) w * (t - epoch);
        }

        public double spin(double t) {
            return s0 + (double) sw * (t - epoch);
        }

        public double bob(double t) {
            return BOB * Math.sin(bp + (double) bw * (t - epoch));
        }

        /** The lift at t: eased in and out (smoothstep), so a step up starts and stops softly. */
        public double lift(double t) {
            double k = Mth.clamp((t - liftAt) / Math.max(1, liftLen), 0.0D, 1.0D);
            k = k * k * (3.0D - 2.0D * k);
            return liftFrom + (liftTo - liftFrom) * k;
        }

        public boolean lifting(double t) {
            return t < liftAt + liftLen;
        }

        public double x(double t) {
            return cx + Math.cos(angle(t)) * orbit;
        }

        public double z(double t) {
            return cz + Math.sin(angle(t)) * orbit;
        }

        /** The top of the ice (where feet stand) at t. */
        public double y(double t) {
            return baseY + bob(t) + lift(t);
        }

        public Vec3 at(double t) {
            return new Vec3(x(t), y(t), z(t));
        }

        /** A point given in the floe's own frame (as its cells are laid), in the world at t. */
        public Vec3 local(double lx, double ly, double lz, double t) {
            double s = spin(t), c = Math.cos(s), n = Math.sin(s);
            return new Vec3(x(t) + lx * c - lz * n, y(t) + ly, z(t) + lx * n + lz * c);
        }

        public void copyFrom(Motion o) {
            cx = o.cx;
            cz = o.cz;
            baseY = o.baseY;
            orbit = o.orbit;
            a0 = o.a0;
            w = o.w;
            s0 = o.s0;
            sw = o.sw;
            bp = o.bp;
            bw = o.bw;
            epoch = o.epoch;
            liftFrom = o.liftFrom;
            liftTo = o.liftTo;
            liftAt = o.liftAt;
            liftLen = o.liftLen;
        }
    }

    /** The arena's numbers onto this body (and so to every client, once). */
    public void applyMotion(Motion m) {
        entityData.set(ORBIT, m.orbit);
        entityData.set(ANGLE0, m.a0);
        entityData.set(OMEGA, m.w);
        entityData.set(SPIN0, m.s0);
        entityData.set(SPIN_W, m.sw);
        entityData.set(BOB_PHASE, m.bp);
        entityData.set(BOB_W, m.bw);
        entityData.set(EPOCH, m.epoch);
        applyLift(m);
    }

    public void applyLift(Motion m) {
        entityData.set(LIFT_FROM, m.liftFrom);
        entityData.set(LIFT_TO, m.liftTo);
        entityData.set(LIFT_AT, m.liftAt);
        entityData.set(LIFT_LEN, Math.max(1, m.liftLen));
        motionDirty = true;
    }

    /** This body's motion, as the synced numbers say. */
    public Motion motion() {
        if (motionDirty) {
            motionDirty = false;
            BlockPos c = entityData.get(CENTRE);
            motion.cx = c.getX() + 0.5D;
            motion.cz = c.getZ() + 0.5D;
            motion.baseY = c.getY() + 1.0D;
            motion.orbit = entityData.get(ORBIT);
            motion.a0 = entityData.get(ANGLE0);
            motion.w = entityData.get(OMEGA);
            motion.s0 = entityData.get(SPIN0);
            motion.sw = entityData.get(SPIN_W);
            motion.bp = entityData.get(BOB_PHASE);
            motion.bw = entityData.get(BOB_W);
            motion.epoch = entityData.get(EPOCH);
            motion.liftFrom = entityData.get(LIFT_FROM);
            motion.liftTo = entityData.get(LIFT_TO);
            motion.liftAt = entityData.get(LIFT_AT);
            motion.liftLen = entityData.get(LIFT_LEN);
        }
        return motion;
    }

    // ================================================================================================ the look
    /** The arena's state onto the body: the state, the one before it, the game time it began, ticks it lasts. */
    public void setLook(int state, int prev, long since, int length) {
        entityData.set(STATE, state);
        entityData.set(PREV_STATE, prev);
        entityData.set(STATE_AT, since);
        entityData.set(STATE_LEN, Math.max(0, length));
    }

    public int state() {
        return entityData.get(STATE);
    }

    public int prevState() {
        return entityData.get(PREV_STATE);
    }

    public long stateAt() {
        return entityData.get(STATE_AT);
    }

    /** Ticks the state lasts (0: until the arena says otherwise). */
    public int stateLength() {
        return entityData.get(STATE_LEN);
    }

    public int slot() {
        return entityData.get(SLOT);
    }

    public BlockPos centre() {
        return entityData.get(CENTRE);
    }

    /** The radius the floe was laid out with (cells), and the disc that holds a body up. */
    public int cellRadius() {
        return entityData.get(RADIUS);
    }

    public double disc() {
        return cellRadius() + 0.4D;
    }

    public static boolean standable(int state) {
        return state == SOLID || state == MARKED || state == CRACKED || state == SLUSH;
    }

    /**
     * The disc that holds a body up at time t: whole while it stands, shrinking from the edge in while it goes (the
     * cells go edge first, SPREAD ticks for the rim to reach the middle), none while it is gone or still forming.
     */
    public double holdingDisc(double t) {
        int st = state();
        if (standable(st)) {
            return disc();
        }
        if (st == GONE) {
            double since = t - stateAt();
            double k = 1.0D - since / SPREAD;
            return k > 0.15D ? disc() * k : 0.0D;
        }
        return 0.0D;
    }

    /** Drawn at all (its cells take SPREAD ticks and the lower layer a few more to go, after it is gone). */
    public boolean shown(double t) {
        return state() != GONE || t - stateAt() < SPREAD + 12;
    }

    // ================================================================================================ the cells
    /** One floe's layout, cached per radius. */
    public static final class Layout {
        /** {x, down, z} per cell in the floe's own frame (down 0 = the top layer), and when a look reaches it. */
        public final int[][] cells;
        public final byte[] delay;
        /** Per cell, per Direction (DOWN, UP, NORTH, SOUTH, WEST, EAST), the neighbour cell's index or -1. */
        public final int[][] next;

        Layout(int r) {
            List<int[]> list = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            double rim = r + 0.4D;
            // the lens: a full top, a narrower layer under it, and on a big floe a keel and a point under that
            // (the same layout StormEyeArena gave its block floes)
            double[][] layers = r >= 5 ? new double[][]{{0, rim}, {1, r - 0.9D}, {2, r - 2.4D}, {3, 0.8D}}
                                       : new double[][]{{0, rim}, {1, r - 1.1D}};
            for (double[] layer : layers) {
                int down = (int) layer[0];
                double lim = layer[1];
                int span = (int) Math.ceil(lim);
                for (int x = -span; x <= span; x++) {
                    for (int z = -span; z <= span; z++) {
                        double d = Math.sqrt(x * x + z * z);
                        if (d > lim) {
                            continue;
                        }
                        list.add(new int[]{x, down, z});
                        int dl = (int) Math.round((1.0D - d / rim) * SPREAD) + down * 3;
                        delays.add(Mth.clamp(dl, 0, SPREAD + 9));
                    }
                }
            }
            cells = list.toArray(new int[0][]);
            delay = new byte[cells.length];
            for (int i = 0; i < delay.length; i++) {
                delay[i] = delays.get(i).byteValue();
            }
            Map<Long, Integer> at = new HashMap<>();
            for (int i = 0; i < cells.length; i++) {
                at.put(key(cells[i][0], cells[i][1], cells[i][2]), i);
            }
            int[][] steps = {{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};  // down = +1 below
            next = new int[cells.length][6];
            for (int i = 0; i < cells.length; i++) {
                for (int d = 0; d < 6; d++) {
                    Integer j = at.get(key(cells[i][0] + steps[d][0], cells[i][1] + steps[d][1], cells[i][2] + steps[d][2]));
                    next[i][d] = j == null ? -1 : j;
                }
            }
        }

        private static long key(int x, int down, int z) {
            return ((long) (x + 64) << 20) | ((long) (down + 64) << 10) | (z + 64);
        }
    }

    private static final Map<Integer, Layout> LAYOUTS = new java.util.concurrent.ConcurrentHashMap<>();

    public static Layout layout(int r) {
        return LAYOUTS.computeIfAbsent(r, Layout::new);
    }

    /** Looks of a cell: the floe block's stages (StormEyeFloeBlock), or AIR. */
    public static final int LOOK_FRESH = 0, LOOK_MARKED = 1, LOOK_CRACKED = 2, LOOK_SLUSH = 3, LOOK_FORMING = 4,
            LOOK_AIR = 9;

    public static int lookOf(int state) {
        return switch (state) {
            case SOLID -> LOOK_FRESH;
            case MARKED -> LOOK_MARKED;
            case CRACKED -> LOOK_CRACKED;
            case SLUSH -> LOOK_SLUSH;
            case FORMING -> LOOK_FORMING;
            default -> LOOK_AIR;
        };
    }

    /**
     * Every cell's look at time t, into `out` - the spread StormEyeArena used to put block by block: a new look
     * reaches the rim first when the ice goes and the middle first when it comes; a floe freezing out of nothing shows
     * nothing where the ghost has not reached yet.
     */
    public void looks(double t, int[] out) {
        Layout lay = layout(cellRadius());
        int st = state(), prev = prevState();
        int look = lookOf(st), prevLook = lookOf(prev);
        double since = t - stateAt();
        boolean centreFirst = st == FORMING || (st == SOLID && prev == FORMING);
        for (int i = 0; i < lay.cells.length && i < out.length; i++) {
            int d = centreFirst ? SPREAD + 9 - lay.delay[i] : lay.delay[i];
            out[i] = since >= d ? look : (prevLook == LOOK_AIR && st == FORMING ? LOOK_AIR : prevLook);
        }
    }

    // ================================================================================================ the clock
    /** The client's clock: game time, smoothed (see advanceClientClock). Client-side state; never read on a server. */
    private static double clockNow = Double.NaN;
    private static int clockGen;
    private static WeakReference<Level> clockLevel = new WeakReference<>(null);

    /**
     * ONCE A CLIENT TICK, before anything moves (StormEyeFloeClient): the clock goes on a tick, and is drawn a little
     * towards the level's game time - which the server's time packet resets once a second, a tick or two either way.
     * A jump of more than two seconds (a join, a lag spike) is taken at once.
     */
    public static void advanceClientClock(Level level, boolean paused) {
        long g = level.getGameTime();
        if (clockLevel.get() != level || Double.isNaN(clockNow) || Math.abs(clockNow - g) > 40.0D) {
            clockLevel = new WeakReference<>(level);
            clockNow = g;
            clockGen++;
            return;
        }
        if (paused) {
            return;
        }
        double drift = g - (clockNow + 1.0D);
        clockNow += 1.0D + Mth.clamp(drift * 0.08D, -0.12D, 0.12D);
        clockGen++;
    }

    public static double clientClock(Level level) {
        return clockLevel.get() == level && !Double.isNaN(clockNow) ? clockNow : level.getGameTime();
    }

    /**
     * THE CLIENT'S STEP, made at the start of the tick before anything moves: last place kept (for the drawing and for
     * carrying whoever stands on it), the new one taken, the parts moved under it. The tick then only restores the
     * old place the level overwrote ({@link #tick}).
     */
    public void clientStep(double t) {
        Motion m = motion();
        // the first step, or the clock jumped (a join, a long stall): no carrying anybody across the jump
        boolean first = stepGen < 0 || Math.abs(t - stepT - 1.0D) > 3.0D;
        stepPrevT = first ? t - 1.0D : stepT;
        stepT = t;
        stepFrom = first ? m.at(stepPrevT) : stepTo;
        stepTo = m.at(t);
        spinFrom = first ? m.spin(stepPrevT) : spinTo;
        spinTo = m.spin(t);
        stepGen = clockGen;
        setPos(stepTo.x, stepTo.y, stepTo.z);
        placeParts(stepTo, holdingDisc(t));
    }

    /** Where it was at the last step and where it is now (client), and how far it turned between them. */
    public Vec3 stepFrom() {
        return stepFrom;
    }

    public Vec3 stepTo() {
        return stepTo;
    }

    public double stepTurn() {
        return spinTo - spinFrom;
    }

    /** The spin to draw at this frame. */
    public double renderSpin(float partialTick) {
        return Mth.lerp(partialTick, spinFrom, spinTo);
    }

    /** The clock to draw at this frame (between the last two steps). */
    public double renderTime(float partialTick) {
        return stepGen < 0 ? clientClock(level()) : Mth.lerp(partialTick, stepPrevT, stepT);
    }

    /** Its turn now (the anchor standing on it turns with it): the client's last step, the server's own clock. */
    public double spinNow() {
        return level().isClientSide ? spinTo : motion().spin(level().getGameTime());
    }

    /**
     * CLIENT: is this body standing on the floe as it stood at the last step - feet on its top, inside its disc? (The
     * carry asks before the body moves; afterwards the floe has already gone on.)
     */
    public boolean carries(Entity e) {
        if (holdingDisc(stepPrevT) <= 0.0D) {
            return false;
        }
        double dy = e.getY() - stepFrom.y;
        if (dy < -0.3D || dy > 0.3D) {
            return false;
        }
        double dx = e.getX() - stepFrom.x, dz = e.getZ() - stepFrom.z;
        double reach = holdingDisc(stepPrevT) + e.getBbWidth() * 0.72D;
        return dx * dx + dz * dz <= reach * reach;
    }

    /**
     * CLIENT: the top this floe holds `e` up at now - NaN if it holds nobody there (not standable, or `e` not over its
     * disc: the same boxes as its parts, their corners round the disc at seven angles over a quarter turn - a square
     * of the disc's width turned through them is what they cover, near enough the disc itself).
     */
    public double floorUnder(Entity e) {
        if (!holdsNow()) {
            return Double.NaN;
        }
        double disc = holdingDisc(stepT);
        double dx = e.getX() - stepTo.x, dz = e.getZ() - stepTo.z;
        double reach = disc + e.getBbWidth() * 0.5D;
        return dx * dx + dz * dz <= reach * reach ? stepTo.y : Double.NaN;
    }

    // ================================================================================================ the tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (stepGen != clockGen) {
                clientStep(clientClock(level()));              // came in after this tick's start: step now
            }
            // the level set the old place to the new one before this tick: the drawing must lerp from the last step
            xo = xOld = stepFrom.x;
            yo = yOld = stepFrom.y;
            zo = zOld = stepFrom.z;
            garnish();
            return;
        }
        Vec3 at = motion().at(level().getGameTime());
        setPos(at.x, at.y, at.z);
        if (arena == null || !arena.owns(this)) {
            if (++orphan > ORPHAN_TICKS) {
                discard();
            }
        } else {
            orphan = 0;
        }
    }

    /** The arena takes this floe as its own (made or adopted after a reload). */
    void claim(StormEyeArena a) {
        this.arena = a;
        this.orphan = 0;
    }

    @Nullable
    StormEyeArena owner() {
        return arena;
    }

    /** Sparks in a lit crack, drips under slush, frost off new ice - the accompaniment, never the read. */
    private void garnish() {
        int st = state();
        if (st != CRACKED && st != SLUSH && st != FORMING) {
            return;
        }
        Layout lay = layout(cellRadius());
        int[] c = lay.cells[random.nextInt(lay.cells.length)];
        double t = stepT;
        if (st == CRACKED && random.nextInt(3) == 0 && c[1] == 0) {
            Vec3 p = motion().local(c[0] + random.nextDouble() - 0.5D, 0.05D, c[2] + random.nextDouble() - 0.5D, t);
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0.0D, 0.03D, 0.0D);
        } else if (st == SLUSH && random.nextInt(2) == 0) {
            Vec3 p = motion().local(c[0] + random.nextDouble() - 0.5D, -1.05D - c[1], c[2] + random.nextDouble() - 0.5D, t);
            level().addParticle(ParticleTypes.DRIPPING_WATER, p.x, p.y, p.z, 0.0D, 0.0D, 0.0D);
        } else if (st == FORMING && random.nextInt(2) == 0) {
            Vec3 p = motion().local(c[0] + random.nextDouble() - 0.5D, -random.nextDouble() - c[1],
                    c[2] + random.nextDouble() - 0.5D, t);
            level().addParticle(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 0.0D, 0.03D, 0.0D);
        }
    }

    /** Where it is is the clock's to say, not the server's position packets (they would drag it a tick behind). */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
    }

    /** The box the world sees (culling, searches): the whole lens and a little round it. Never collided with itself. */
    @Override
    protected AABB makeBoundingBox() {
        double e = entityData.get(RADIUS) + 0.95D;
        Vec3 p = position();
        return new AABB(p.x - e, p.y - 2.3D, p.z - e, p.x + e, p.y + 0.2D, p.z + e);
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
        return true;
    }

    @Override
    public boolean canChangeDimensions(net.minecraft.world.level.Level from, net.minecraft.world.level.Level to) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160.0D * 160.0D;
    }

    // ================================================================================================ the parts
    /** Its parts stand bodies up on the CLIENT only (see the class note); the server never registers them. */
    @Override
    public boolean isMultipartEntity() {
        return level().isClientSide();
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                parts[i].setId(id + i + 1);
            }
        }
    }

    private void placeParts(Vec3 top, double disc) {
        for (Part p : parts) {
            p.place(top, disc);
        }
    }

    /** Solid on the client, once it has taken its first step (until then its boxes lie where they were made). */
    boolean holdsNow() {
        return level().isClientSide() && stepGen >= 0 && holdingDisc(stepT) > 0.0D;
    }

    /**
     * ONE BOX OF THE DISC: its corners on a circle of the floe's radius at (cos a, sin a) - seven of them at a's spread
     * over a quarter turn make a round floor (never less than 0.88 of the radius out, anywhere round it) that does not
     * care which way the ice has turned. A block deep under the top.
     */
    public static final class Part extends PartEntity<StormEyeFloeEntity> {
        private final double fx, fz;

        Part(StormEyeFloeEntity parent, double fx, double fz) {
            super(parent);
            this.fx = fx;
            this.fz = fz;
            this.noPhysics = true;
            this.blocksBuilding = false;
        }

        void place(Vec3 top, double disc) {
            setPosRaw(top.x, top.y, top.z);
            double hx = Math.max(0.01D, disc * fx), hz = Math.max(0.01D, disc * fz);
            setBoundingBox(new AABB(top.x - hx, top.y - 1.0D, top.z - hz, top.x + hx, top.y, top.z + hz));
        }

        @Override
        public boolean canBeCollidedWith() {
            return getParent().holdsNow();
        }

        @Override
        public boolean isPickable() {
            return false;
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        public boolean canBeHitByProjectile() {
            return false;
        }

        @Override
        public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
            return false;
        }

        @Override
        public boolean is(Entity other) {
            return this == other || getParent() == other;
        }

        @Override
        public boolean shouldBeSaved() {
            return false;
        }

        @Override
        protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        }

        @Override
        protected void readAdditionalSaveData(CompoundTag tag) {
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag tag) {
        }
    }
}
