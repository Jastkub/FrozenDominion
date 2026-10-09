package com.jastkub.frozenfortress.entity.boss;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.block.StormEyeFloeBlock;
import com.jastkub.frozenfortress.registry.FFBlocks;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * OKO BURZY - THE EYE OF THE STORM. Velkhar's last phase is fought above the clouds.
 *
 * <p>This is the arena's whole body on the server: built when the Hollow Magus rises (VelkharEntity STORM_ASCENT),
 * owned by him, ticked from his aiStep, and taken down when the fight ends either way.
 *
 * <pre>
 *  centre C = the throne (homePos), floe tops at FLOOR (= max(throne + 110, 215), raised until the air is clear)
 *
 *            outer slot (r 3) x8 at 18.6 blocks, from 22.5 + 45k deg - the Storm Anchors stand here
 *         inner floe (r 3) x8 at 11 blocks, from 45k deg              - both rings turn, opposite ways
 *      central floe (r 5) - blocks; never moves, never melts: he falls onto it when his anchors break
 *   electrified cloud wall at 25 blocks, FLOOR-14 .. FLOOR+18     - touch it and it throws you back in, hurt
 *   the vortex: rim at FLOOR-3 (r 26) down to its eye at FLOOR-46 - fall in and you die (the first fall is spared)
 * </pre>
 *
 * <p>THE ICE MELTS IN CYCLES and it is always readable which floe goes next: a floe is MARKED (hairline cracks) for
 * two and a half seconds before it CRACKS (lit cracks, a groan) for under two, turns to SLUSH (dripping) for one and
 * is gone - edge first, so the middle is the last thing to stand on.
 *
 * <p>AND THE STORM TURNS. The storm's FRONT - an angle going round the eye once in thirty seconds (twenty while it
 * closes) - is where the ice melts, and the ice comes back behind it, so the floor goes round like a hand on a dial and
 * staying alive is keeping ahead of it. The melt runs some two and a half times as often as it did. New ice FREEZES
 * elsewhere: a frost outline for two seconds or so, then solid.
 *
 * <p>AND THE ICE ITSELF MOVES. The sixteen floes of the two rings are bodies (StormEyeFloeEntity), not
 * blocks: the inner ring turns one way once in 56 seconds, the outer the other way once in 68, so they slide past each
 * other once every half a minute; each floe turns slowly about its own middle and breathes a little up and down; and
 * every few seconds one or two of them are eased a half step or a step up or down (a grinding of ice). Where a floe is,
 * is a function of the game time alone (StormEyeFloeEntity.Motion) - the arena plans with the same function the body
 * moves by. Players stand on them on their own client; the server knows who stands where by position.
 *
 * <p>THE SCHEDULE NEVER STRANDS ANYBODY - for long. Ice may only melt, freeze or move if, at every offset the two rings
 * will pass through, every floe that will still stand is joined to the centre by gaps of a sprint jump or less, none a
 * climb of more than UP_REACH - or, where the rings leave one cut off, only until they bring the next floe round
 * (CUT_OFF_MAX, about four seconds: one missing floe of the inner ring, never two side by side).
 */
public final class StormEyeArena {

    // ================================================================================================ the geometry
    /** The central floe's radius; it never melts. */
    public static final int CENTRE_R = 5;
    public static final double INNER_RING = 11.0D;
    public static final int INNER_R = 3;
    /**
     * 18.6, not 18 as it was while the floes stood still: the rings now slide past each other, and at 18 two floes
     * meeting square on (7 apart, each reaching 3.8 at a corner of its cells) would grind through each other. At 18.6
     * they only ever kiss; the widest gap to the nearest inner floe (half-way between two of them) is 9.4 between
     * middles - 3.4 by JUMP_GAP's measure, 2.6 of open air between the discs that hold a body up.
     */
    public static final double OUTER_RING = 18.6D;
    public static final int OUTER_R = 3;
    /** The electrified cloud wall: its radius, and how far it reaches below and above the floe tops. */
    public static final double WALL_R = 25.0D;
    public static final double WALL_BELOW = 14.0D, WALL_ABOVE = 18.0D;
    /** The vortex: rim radius and how deep its eye lies under the floe tops. */
    public static final double VORTEX_R = 26.0D, VORTEX_DEPTH = 46.0D;
    /** Where the floes are built: this high over the throne, and never under the clouds. */
    public static final int RISE_ABOVE_HOME = 110, MIN_FLOOR_Y = 215;
    /** Below this far under the floe tops a player has fallen into the storm. */
    public static final double FALL_LINE = 6.0D;
    /** Above this far over the floe tops the cloud lid shocks them back down. */
    public static final double CEILING = 19.0D;
    /** Two floes are neighbours (a sprint jump) if the open air between their rims is this or less. */
    private static final double JUMP_GAP = 3.9D;

    // ================================================================================================ the motion
    /**
     * The rings turn: the inner one way once in INNER_TURN ticks (1.2 blocks a second at its floes), the outer the
     * other way once in OUTER_TURN (1.7 a second) - quick enough to read, slow enough to walk against.
     */
    static final int INNER_TURN = 1120, OUTER_TURN = 1360;
    /** Ticks the two rings take to bring every offset between them round once (they turn opposite ways): ~614. */
    static final double RELATIVE_TURN = 1.0D / (1.0D / INNER_TURN + 1.0D / OUTER_TURN);
    /** A floe's own turn about its middle: once in this many ticks, either way (the player on it turns with it). */
    static final int SPIN_MIN = 900, SPIN_MAX = 1400;
    /** Its breath up and down (StormEyeFloeEntity.BOB high): once in this many ticks. */
    static final int BREATH_MIN = 100, BREATH_MAX = 160;
    /**
     * RISE AND SINK: the rests a floe is eased between (LIFT_STEP apart, never more than LIFT_MAX from the centre's
     * level), the ticks a step takes, and the climb a jump is always good for. A jump gains 1.25; two floes breathing
     * against each other can take 2 x BOB of that, so two floes count as joined only if their rests differ by
     * UP_REACH or less (one half step). Down is free.
     */
    static final double LIFT_STEP = 0.5D, LIFT_MAX = 1.0D, UP_REACH = 0.6D;
    static final int LIFT_TICKS = 30;
    /**
     * How long the rings may leave a standing floe cut off before they bring the next one round to it. One missing
     * inner floe cuts an outer one off for ~69 ticks as it passes the hole; two side by side for ~146 - not allowed.
     */
    static final int CUT_OFF_MAX = 80;
    /** Offsets of the rings sampled over one RELATIVE_TURN (one every five degrees of their slide). */
    static final int JOIN_SAMPLES = 72;
    /** How far from its floe (as the server has it) a player standing on it may be: the client's clock is its ping late. */
    static final double LAG = 1.0D;

    /** Slot states. */
    static final int SOLID = 0, MARKED = 1, CRACKED = 2, SLUSH = 3, GONE = 4, FORMING = 5;
    private static final int AIR = 9;
    /** Ticks a state spreads over a floe (edge first when it goes, centre first when it comes). */
    private static final int SPREAD = 8;

    /** Every arena standing on this server - the guards in StormEyeEvents ask it. */
    public static final List<StormEyeArena> ACTIVE = new CopyOnWriteArrayList<>();
    /** Ticks after a server starts before a floe no arena claims takes itself away (StormEyeFloeBlock). */
    public static final int ORPHAN_GRACE = 2400;

    /** The storm's own kind of hurt (data/frozen_dominion/damage_type/storm_eye.json). */
    public static final ResourceKey<DamageType> STORM_EYE =
            ResourceKey.create(Registries.DAMAGE_TYPE, FrozenFortress.id("storm_eye"));

    public static DamageSource stormDamage(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(STORM_EYE));
    }

    // ================================================================================================ shields
    /**
     * Players the storm is carrying - held in his hand, drawn up the column, gliding onto a floe - and until when (game
     * time). Nothing hurts them and they take no fall while it lasts (StormEyeEvents). Server-side, every arena's.
     */
    public static final Map<UUID, Long> SHIELD_UNTIL = new ConcurrentHashMap<>();
    /** The one he holds in his hand on the way up: they cannot get down (StormEyeEvents' mount guard). */
    public static final Set<UUID> HELD = ConcurrentHashMap.newKeySet();

    public static void shield(Entity e, int ticks) {
        SHIELD_UNTIL.merge(e.getUUID(), e.level().getGameTime() + ticks, Math::max);
    }

    public static boolean shielded(Entity e) {
        if (HELD.contains(e.getUUID())) {
            return true;
        }
        Long until = SHIELD_UNTIL.get(e.getUUID());
        if (until == null) {
            return false;
        }
        if (e.level().getGameTime() > until) {
            SHIELD_UNTIL.remove(e.getUUID());
            return false;
        }
        return true;
    }

    /**
     * Carried through the air for longer than a dedicated server lets anyone float (it kicks for "flying" after four
     * seconds off the ground): a hidden levitation says to it that the flight is not theirs. Refreshed every tick of
     * the carry, so it runs out a breath after it ends.
     */
    static void airborne(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 6, 0, false, false, false));
        p.fallDistance = 0.0F;
    }

    /**
     * HELD UP BY A MOVING FLOE the server cannot see (StormEyeFloeEntity is solid only on the client). The server must
     * not take standing on it for flying: a dedicated server kicks anybody with no block about their feet for four
     * seconds. The same hidden levitation as airborne() - but put straight into the server's own list and never sent:
     * on the client it would float them up off the ice. The server only asks whether they have it. Put in only when
     * they have none (never over a real one, never over one the client was sent - that one must run out and be taken
     * off the client); it lasts ten ticks and is put in again when it has gone.
     */
    static void afloat(ServerPlayer p) {
        if (!p.hasEffect(MobEffects.LEVITATION)) {
            p.getActiveEffectsMap().put(MobEffects.LEVITATION,
                    new MobEffectInstance(MobEffects.LEVITATION, 10, 0, false, false, false));
        }
        p.fallDistance = 0.0F;
    }

    // ================================================================================================ the state
    private final VelkharEntity king;
    private final ServerLevel level;
    /** The block the arena is centred on (x, z) - over the dome of the throne room - and the floe tops' block y. */
    public final int cx, cz, floorY;
    /** The throne (where he sat). */
    public final BlockPos home;
    /** The foot of the column in the hall, under the dome: where the fallen climb back up, where they are set down. */
    private final Vec3 hallFloor;
    /** EVERY BLOCK THE ASCENT TORE OUT OF THE ROOF, with what it was (state, block entity) - put back on dissolve. */
    private final Map<Long, CompoundTag> hole = new LinkedHashMap<>();
    /** How many more pieces of the roof may still fall as bodies (the rest just go). */
    private int rubbleLeft = 90;
    /** Arena age each floe of the first layout starts to freeze at (the arrival), -1 when it is not waiting. */
    private final int[] formAt = new int[17];
    /** Players drawn up the column or gliding to their floe (see Carry). */
    private final Map<UUID, Carry> carries = new LinkedHashMap<>();
    private Vec3 prevKing;
    private final Slot[] slots;
    private final Set<Long> claimed = new HashSet<>();
    private final Set<UUID> participants = new HashSet<>();
    private final Map<UUID, Integer> falls = new HashMap<>();
    private final Map<UUID, Integer> shockClock = new HashMap<>();
    private final Map<UUID, Integer> liftClock = new HashMap<>();
    private final Map<UUID, Integer> flightNag = new HashMap<>();
    /** Players a rescue gust is carrying right now (and since when): not fallen again while it does. */
    private final Map<UUID, Integer> carried = new HashMap<>();
    private final List<UUID> anchors = new ArrayList<>();
    @Nullable
    private UUID vortexId, wallId, liftId;
    private boolean built;
    /** Players have arrived: from now on an empty arena means the fight was lost. */
    private boolean armed;
    private int emptyFor;
    private int age;
    private int nextMelt = 200;
    private int nextFreeze = 40;
    private int nextLift = 120;
    /** The game time the rings' angles are counted from (saved: a reload brings the floes back where they were). */
    private final long epoch;
    /** The storm front's angle (radians, round the eye); see the class note. Not saved - a reload starts it anew. */
    private double front;
    /** One turn of the front, in ticks: as it is, and while the eye closes. */
    private static final int FRONT_TURN = 600, FRONT_TURN_CLOSING = 400;
    /** Oko sie zamyka: the vortex rises and the ice goes twice as fast. */
    private boolean closing;
    private float rise;
    /** The king is dying: nothing more melts away, nobody more dies, the storm fades. */
    private int dyingAt = -1;
    private float fade;
    private boolean dissolved;
    /** Loaded from a save mid-fight: the floes that are still in the world are adopted, not rebuilt under feet. */
    private boolean fromSave;
    /** Loaded from a save: which ring slots stood (bit per slot), -1 for an older save that did not say. */
    private int savedFloes = -1;

    /** A player the storm is moving. */
    private static final class Carry {
        /** BEHIND: up the column behind him (the ascent). RISE: up the hall's column on its own (climbing back up).
         *  LAND: off the column onto a floe. */
        static final int BEHIND = 0, RISE = 1, LAND = 2;
        int mode;
        final int index;
        int slot = -1;
        int since;
        double vy;
        /** Where they were last tick, and for how many ticks they have not been going where they are pushed. */
        Vec3 last;
        int stuck;
        /** LAND: risen through the gap and over the ice yet (then it glides out to its floe). */
        boolean over;

        Carry(int mode, int index) {
            this.mode = mode;
            this.index = index;
        }
    }

    private static final class Slot {
        final int index, ring, r;
        /** THE CENTRE ONLY: its blocks (the ring floes are bodies - see motion and floe). */
        final List<BlockPos> blocks = new ArrayList<>();
        final byte[] edgeDelay;
        final byte[] shown;
        /** THE RINGS ONLY: where the floe is as a function of the time, and the body that is it. */
        @Nullable
        final StormEyeFloeEntity.Motion motion;
        @Nullable
        UUID floeId;
        @Nullable
        StormEyeFloeEntity floe;
        /** The rest it was eased to (a LIFT_STEP); the slush's sinking and a step under way do not change it. */
        double restLift;
        int state = GONE;
        int prevStage = AIR;
        int t;
        boolean anchored;
        int goneAt = -10000;

        /** The central floe: blocks, laid out round (cx, floorY, cz). */
        Slot(int r, int cx, int cz, int floorY) {
            this.index = 0;
            this.ring = 0;
            this.r = r;
            this.motion = null;
            StormEyeFloeEntity.Layout lay = StormEyeFloeEntity.layout(r);
            for (int[] c : lay.cells) {
                blocks.add(new BlockPos(cx + c[0], floorY - c[1], cz + c[2]));
            }
            edgeDelay = lay.delay.clone();
            shown = new byte[blocks.size()];
            java.util.Arrays.fill(shown, (byte) AIR);
        }

        /** A floe of a ring: a body moving as `motion` says. */
        Slot(int index, int ring, int r, StormEyeFloeEntity.Motion motion) {
            this.index = index;
            this.ring = ring;
            this.r = r;
            this.motion = motion;
            this.edgeDelay = new byte[0];
            this.shown = new byte[0];
        }

        /** Will it be standing in a few seconds? (what the schedule may lean on) */
        boolean lasting() {
            return state == SOLID || state == FORMING || anchored;
        }

        boolean standable() {
            return state == SOLID || state == MARKED || state == CRACKED || state == SLUSH;
        }
    }

    private StormEyeArena(VelkharEntity king, ServerLevel level, int cx, int cz, int floorY, BlockPos home,
                          Vec3 hallFloor, long epoch) {
        this.king = king;
        this.level = level;
        this.cx = cx;
        this.cz = cz;
        this.floorY = floorY;
        this.home = home;
        this.hallFloor = hallFloor;
        this.epoch = epoch;
        this.prevKing = king.position();
        java.util.Arrays.fill(formAt, -1);
        this.slots = new Slot[17];
        slots[0] = new Slot(CENTRE_R, cx, cz, floorY);
        // every floe's turn and breath from a seed of the arena's own: a reload (the epoch is saved) brings them back
        // exactly where and how they were
        net.minecraft.util.RandomSource rnd = net.minecraft.util.RandomSource.create(epoch * 31L + cx * 7919L + cz);
        for (int k = 0; k < 8; k++) {
            slots[1 + k] = new Slot(1 + k, 1, INNER_R, motion(rnd, INNER_RING, 45.0D * k, INNER_TURN));
            slots[9 + k] = new Slot(9 + k, 2, OUTER_R, motion(rnd, OUTER_RING, 22.5D + 45.0D * k, -OUTER_TURN));
        }
        for (Slot s : slots) {
            for (BlockPos p : s.blocks) {
                claimed.add(p.asLong());
            }
        }
    }

    /** A ring floe's motion: on `orbit`, from `deg` at the epoch, once round in |turn| ticks (the sign is the way). */
    private StormEyeFloeEntity.Motion motion(net.minecraft.util.RandomSource rnd, double orbit, double deg, int turn) {
        StormEyeFloeEntity.Motion m = new StormEyeFloeEntity.Motion();
        m.cx = cx + 0.5D;
        m.cz = cz + 0.5D;
        m.baseY = floorY + 1.0D;
        m.orbit = (float) orbit;
        m.a0 = (float) Math.toRadians(deg);
        m.w = (float) (Math.PI * 2.0D / turn);
        m.s0 = (float) (rnd.nextDouble() * Math.PI * 2.0D);
        m.sw = (float) ((rnd.nextBoolean() ? 1.0D : -1.0D) * Math.PI * 2.0D
                / (SPIN_MIN + rnd.nextInt(SPIN_MAX - SPIN_MIN + 1)));
        m.bp = (float) (rnd.nextDouble() * Math.PI * 2.0D);
        m.bw = (float) (Math.PI * 2.0D / (BREATH_MIN + rnd.nextInt(BREATH_MAX - BREATH_MIN + 1)));
        m.epoch = epoch;
        m.liftLen = 1;
        return m;
    }

    /**
     * A new arena over the throne, at the first height where the sky is clear - or null if there is none (a world
     * with a low ceiling, mountains to the build limit): the last phase is then fought in the hall, as it always was.
     */
    @Nullable
    public static StormEyeArena create(ServerLevel level, VelkharEntity king, BlockPos home) {
        // OVER THE DOME, NOT THE THRONE (07.10.2026: he goes up through the roof of the rotunda, so the sky is laid
        // out straight over it and the column is one straight line from the hall floor to the ice)
        double[] dome = findDome(level, home);
        int ccx = dome != null ? (int) Math.floor(dome[0]) : home.getX();
        int ccz = dome != null ? (int) Math.floor(dome[1]) : home.getZ();
        Vec3 hall = dome != null ? new Vec3(ccx + 0.5D, dome[2], ccz + 0.5D)
                : new Vec3(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D);
        BlockPos centre = new BlockPos(ccx, home.getY(), ccz);
        int top = level.getMaxBuildHeight() - (int) WALL_ABOVE - 6;
        int y = Math.max(home.getY() + RISE_ABOVE_HOME, MIN_FLOOR_Y);
        if (y > top) {
            y = Math.max(home.getY() + 60, top);              // a low world: as high as it lets us
        }
        for (int tries = 0; tries < 8 && y <= top; tries++, y += 8) {
            if (skyClear(level, centre, y)) {
                return new StormEyeArena(king, level, ccx, ccz, y, home, hall, level.getGameTime());
            }
        }
        return null;
    }

    /**
     * WHERE THE DOME IS. Walk the throne room's floor out from the throne - a step up or down at a time, two blocks of
     * headroom, so columns and walls stop the walk - look up from every cell, and take the middle of the cells under
     * the highest ceiling: the lantern of the rotunda. Returns {x, z, standing y there}, or null for a hall it cannot
     * read (the arena is then centred on the throne).
     */
    @Nullable
    static double[] findDome(ServerLevel level, BlockPos home) {
        final int r = 36, n = 2 * r + 1;
        final int unseen = Integer.MIN_VALUE, wall = Integer.MIN_VALUE + 1;
        int[] floor = new int[n * n];
        java.util.Arrays.fill(floor, unseen);
        ArrayDeque<int[]> open = new ArrayDeque<>();
        for (int[] o : new int[][]{{0, 0}, {0, 3}, {0, -3}, {3, 0}, {-3, 0}, {3, 3}, {-3, -3}, {3, -3}, {-3, 3}}) {
            Integer f = standY(level, home.getX() + o[0], home.getZ() + o[1], home.getY() + 2, 6);
            if (f != null) {
                floor[(o[0] + r) * n + (o[1] + r)] = f;
                open.add(o);
                break;
            }
        }
        List<int[]> cells = new ArrayList<>();
        int maxCeil = Integer.MIN_VALUE;
        while (!open.isEmpty()) {
            int[] c = open.poll();
            int fy = floor[(c[0] + r) * n + (c[1] + r)];
            int ceil = ceiling(level, home.getX() + c[0], home.getZ() + c[1], fy, 90);
            if (ceil > 0) {
                cells.add(new int[]{c[0], c[1], fy, ceil});
                maxCeil = Math.max(maxCeil, ceil);
            }
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (Math.abs(nx) > r || Math.abs(nz) > r || floor[(nx + r) * n + (nz + r)] != unseen) {
                    continue;
                }
                Integer f = standY(level, home.getX() + nx, home.getZ() + nz, fy + 2, 4);
                if (f != null && Math.abs(f - fy) <= 1) {
                    floor[(nx + r) * n + (nz + r)] = f;
                    open.add(new int[]{nx, nz});
                } else {
                    floor[(nx + r) * n + (nz + r)] = wall;
                }
            }
        }
        if (cells.size() < 30) {
            return null;
        }
        double sx = 0.0D, sz = 0.0D;
        int k = 0;
        for (int[] c : cells) {
            if (c[3] >= maxCeil - 2) {
                sx += c[0];
                sz += c[1];
                k++;
            }
        }
        double mx = sx / k, mz = sz / k;
        int[] best = cells.get(0);
        double bd = Double.MAX_VALUE;
        for (int[] c : cells) {
            double d = (c[0] - mx) * (c[0] - mx) + (c[1] - mz) * (c[1] - mz);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return new double[]{home.getX() + best[0] + 0.5D, home.getZ() + best[1] + 0.5D, best[2]};
    }

    /** The standing height at (x, z): a block with collision under two empty ones, searched down from `from`. */
    @Nullable
    private static Integer standY(ServerLevel level, int x, int z, int from, int depth) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = from; y >= from - depth; y--) {
            p.set(x, y, z);
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                continue;
            }
            p.set(x, y + 1, z);
            boolean a = level.getBlockState(p).getCollisionShape(level, p).isEmpty();
            p.set(x, y + 2, z);
            boolean b = level.getBlockState(p).getCollisionShape(level, p).isEmpty();
            return a && b ? y + 1 : null;
        }
        return null;
    }

    /** The first block overhead (absolute y), or -1 for open sky within `max`. */
    private static int ceiling(ServerLevel level, int x, int z, int standY, int max) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = standY + 2; y <= standY + max; y++) {
            p.set(x, y, z);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                return y;
            }
        }
        return -1;
    }

    /** Nothing but air (or old floes) where the floes and the first storeys over them will be. */
    private static boolean skyClear(ServerLevel level, BlockPos home, int y) {
        int span = (int) (OUTER_RING + OUTER_R + 1);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -4; dy <= 12; dy++) {
            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    if (dx * dx + dz * dz > span * span) {
                        continue;
                    }
                    p.set(home.getX() + dx, y + dy, home.getZ() + dz);
                    BlockState st = level.getBlockState(p);
                    if (!st.isAir() && !(st.getBlock() instanceof StormEyeFloeBlock)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ================================================================================================ queries
    /** The middle of the arena at the height a player stands on the floes. */
    public Vec3 centre() {
        return new Vec3(cx + 0.5D, floorY + 1.0D, cz + 0.5D);
    }

    public double surfaceY() {
        return floorY + 1.0D;
    }

    public ServerLevel level() {
        return level;
    }

    public boolean isClosing() {
        return closing;
    }

    public boolean isArmed() {
        return armed;
    }

    public boolean isDissolved() {
        return dissolved;
    }

    /** Inside the arena's whole volume - the floes, the air over them, the storm under them, up to the walls. */
    public boolean contains(double x, double y, double z) {
        double dx = x - (cx + 0.5D), dz = z - (cz + 0.5D);
        return dx * dx + dz * dz <= (WALL_R + 6.0D) * (WALL_R + 6.0D)
                && y >= floorY - VORTEX_DEPTH - 24.0D && y <= floorY + WALL_ABOVE + 12.0D;
    }

    public boolean contains(Vec3 v) {
        return contains(v.x, v.y, v.z);
    }

    /** Does a standing arena of this level own this block? (the central floe's orphan check - its blocks only now) */
    public static boolean claims(Level level, BlockPos pos) {
        for (StormEyeArena a : ACTIVE) {
            if (a.level == level && !a.dissolved && a.claimed.contains(pos.asLong())) {
                return true;
            }
        }
        return false;
    }

    /** The standing arena whose volume holds this point, if any. */
    @Nullable
    public static StormEyeArena at(Level level, Vec3 v) {
        for (StormEyeArena a : ACTIVE) {
            if (a.level == level && !a.dissolved && a.contains(v)) {
                return a;
            }
        }
        return null;
    }

    /** The game time, as the floes' motion reads it. */
    private double now() {
        return level.getGameTime();
    }

    /** A slot's centre at standing height, now. */
    private Vec3 top(Slot s) {
        return topAt(s, now());
    }

    /** ...and at game time t: the centre's is fixed, a ring floe's is its motion's. */
    private Vec3 topAt(Slot s, double t) {
        return s.motion == null ? new Vec3(cx + 0.5D, floorY + 1.0D, cz + 0.5D) : s.motion.at(t);
    }

    /** The floe a body stands on (or over), or null. A ring floe is allowed LAG for the client's late clock. */
    @Nullable
    private Slot slotUnder(double x, double y, double z) {
        for (Slot s : slots) {
            Vec3 c = top(s);
            if (y < c.y - 2.0D || y > c.y + 4.0D) {
                continue;
            }
            double ddx = x - c.x, ddz = z - c.z;
            double reach = s.r + 0.7D + (s.motion != null ? LAG : 0.0D);
            if (ddx * ddx + ddz * ddz <= reach * reach) {
                return s;
            }
        }
        return null;
    }

    /**
     * The RING floe this player stands on - feet at its top, give or take its breath and the client's late clock -
     * or null. The server never collides them with it (StormEyeFloeEntity): this is how it knows.
     */
    @Nullable
    private Slot ringFloeUnderFeet(Entity e) {
        for (Slot s : slots) {
            if (s.motion == null || !s.standable()) {
                continue;
            }
            Vec3 c = top(s);
            double dy = e.getY() - c.y;
            if (dy < -0.9D || dy > 1.4D) {
                continue;
            }
            double ddx = e.getX() - c.x, ddz = e.getZ() - c.z;
            double reach = s.r + 0.4D + e.getBbWidth() * 0.72D + LAG;
            if (ddx * ddx + ddz * ddz <= reach * reach) {
                return s;
            }
        }
        return null;
    }

    /** The body of a ring floe, by slot (null for the centre, or while it is not in the world). */
    @Nullable
    public StormEyeFloeEntity floeEntity(int slotIndex) {
        if (slotIndex <= 0 || slotIndex >= slots.length) {
            return null;
        }
        return floe(slots[slotIndex]);
    }

    /** The body of the ring floe nearest this point (the mirrors hover over one and follow it), or null. */
    @Nullable
    public StormEyeFloeEntity floeNear(Vec3 v) {
        Slot best = null;
        double bd = 9.0D;
        for (Slot s : slots) {
            if (s.motion == null) {
                continue;
            }
            Vec3 c = top(s);
            double d = (c.x - v.x) * (c.x - v.x) + (c.z - v.z) * (c.z - v.z);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best != null ? floe(best) : null;
    }

    /** Is this body the floe of one of this arena's slots? (the floes' orphan check) */
    boolean owns(StormEyeFloeEntity f) {
        if (dissolved || !ACTIVE.contains(this)) {
            return false;
        }
        int i = f.slot();
        return i > 0 && i < slots.length && slots[i].floe == f;
    }

    @Nullable
    private StormEyeFloeEntity floe(Slot s) {
        if (s.floe != null && !s.floe.isRemoved()) {
            return s.floe;
        }
        if (s.floeId != null && level.getEntity(s.floeId) instanceof StormEyeFloeEntity f && !f.isRemoved()) {
            s.floe = f;
            return f;
        }
        return null;
    }

    /**
     * Where a body is to be set down on a slot `ahead` ticks from now, (ox, oz) off its middle: on a ring floe a little
     * over its top, so a client whose floe is a breath behind or above the server's lands on it, not inside it.
     */
    public Vec3 landingPoint(int slotIndex, double ox, double oz, int ahead) {
        Slot s = slots[Mth.clamp(slotIndex, 0, slots.length - 1)];
        return topAt(s, now() + ahead).add(ox, s.motion != null ? 0.45D : 0.0D, oz);
    }

    /** The floe under this body: its centre at standing height and its radius - for the thunder rune. */
    @Nullable
    public double[] floeUnder(Entity e) {
        Slot s = slotUnder(e.getX(), e.getY(), e.getZ());
        if (s == null || !s.standable()) {
            s = nearestStanding(e.position(), false);
        }
        if (s == null) {
            return null;
        }
        Vec3 c = top(s);
        return new double[]{c.x, c.y, c.z, s.r + 0.5D, s.index};
    }

    /** Is this floe the centre one (never struck to pieces)? */
    public boolean isCentreSlot(int index) {
        return index == 0;
    }

    @Nullable
    private Slot nearestStanding(Vec3 from, boolean lastingOnly) {
        Slot best = null;
        double bd = Double.MAX_VALUE;
        for (Slot s : slots) {
            if (!(lastingOnly ? s.state == SOLID || (s.anchored && s.standable()) : s.standable())) {
                continue;
            }
            double d = top(s).distanceToSqr(from);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    /** Somewhere safe to set a body down: the nearest solid floe, a little way in from its rim. */
    public Vec3 safeLanding(Vec3 from) {
        double[] l = landingFor(from);
        return landingPoint((int) l[0], l[1], l[2], 0);
    }

    /**
     * ...as {slot, x off its middle, z off its middle}: the floes move, so whoever sets a body down asks for the
     * point again as it goes (landingPoint) - it follows the floe.
     */
    private double[] landingFor(Vec3 from) {
        Slot s = nearestStanding(from, true);
        if (s == null) {
            s = slots[0];
        }
        Vec3 c = top(s);
        Vec3 toward = new Vec3(from.x - c.x, 0.0D, from.z - c.z);
        double off = Math.min(s.r - 1.5D, toward.horizontalDistance());
        Vec3 o = Vec3.ZERO;
        if (toward.lengthSqr() > 1.0E-4D && off > 0.0D) {
            o = toward.normalize().scale(off);
        }
        return new double[]{s.index, o.x, o.z};
    }

    /** Slot centres the mirrors may hover over: standing floes near the given point, not the one under it. */
    public List<Vec3> mirrorFloes(Vec3 near, int count) {
        List<Slot> pool = new ArrayList<>();
        for (Slot s : slots) {
            if (s.state == SOLID || s.state == MARKED) {
                pool.add(s);
            }
        }
        pool.sort((a, b) -> Double.compare(top(a).distanceToSqr(near), top(b).distanceToSqr(near)));
        List<Vec3> out = new ArrayList<>();
        for (int i = 1; i < pool.size() && out.size() < count; i++) {
            out.add(top(pool.get(i)));
        }
        while (out.size() < count) {
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            out.add(centre().add(Math.cos(a) * INNER_RING, 0.0D, Math.sin(a) * INNER_RING));
        }
        return out;
    }

    // ================================================================================================ building
    /** The first layout: the centre, all eight inner floes and every other outer one. */
    public void build() {
        if (built) {
            return;
        }
        built = true;
        dissolved = false;
        if (!ACTIVE.contains(this)) {
            ACTIVE.add(this);
        }
        // floe blocks from before: all of them for a new arena; for one loaded from a save, all but the centre's (an
        // arena saved by an older build left its ring floes as blocks - they are bodies now)
        clearStale(fromSave);
        for (Slot s : slots) {
            boolean start;
            if (fromSave) {
                // the centre is always there; a ring floe stood if the save says it did (an older save says nothing:
                // the first layout)
                start = s.index == 0 || (savedFloes >= 0 ? (savedFloes & (1 << s.index)) != 0
                        : s.ring == 1 || s.index % 2 == 1);
            } else {
                // A NEW ARENA LAYS NOTHING: the floes freeze out of the cloud as they arrive (beginForming)
                start = false;
            }
            if (start) {
                s.state = SOLID;
                s.prevStage = AIR;
                s.t = SPREAD + 12;                          // no spread on the first frame: it is simply there
                for (int i = 0; i < s.blocks.size(); i++) {
                    put(s, i, StormEyeFloeBlock.FRESH);
                }
            }
        }
        ensureFloes();
        ensureFx();
    }

    /**
     * EVERY RING FLOE HAS ITS BODY: the one this arena made, else one already in the sky for this slot that no living
     * arena owns (the king's chunk went and came back while the floes' stayed), else a new one - hidden while its
     * slot is GONE. Its motion and look are put on it whichever it is.
     */
    private void ensureFloes() {
        List<StormEyeFloeEntity> loose = null;
        BlockPos key = new BlockPos(cx, floorY, cz);
        for (Slot s : slots) {
            if (s.motion == null || floe(s) != null) {
                continue;
            }
            if (loose == null) {
                AABB sky = new AABB(cx - WALL_R, floorY - 12, cz - WALL_R, cx + WALL_R + 1, floorY + 12, cz + WALL_R + 1);
                loose = level.getEntitiesOfClass(StormEyeFloeEntity.class, sky, f -> f.isAlive() && key.equals(f.centre())
                        && (f.owner() == null || f.owner() == this || !ACTIVE.contains(f.owner())));
            }
            StormEyeFloeEntity f = null;
            for (StormEyeFloeEntity o : loose) {
                if (o.slot() == s.index) {
                    f = o;
                    break;
                }
            }
            if (f == null) {
                f = StormEyeFloeEntity.make(level, this, s.index, s.r, cx, floorY, cz, s.motion);
                level.addFreshEntity(f);
            } else {
                f.applyMotion(s.motion);
            }
            f.claim(this);
            s.floe = f;
            s.floeId = f.getUUID();
            f.setLook(s.state, s.state, level.getGameTime() - 1000L, lookLength(s.state));   // no spread: it just is
        }
    }

    /** The arena's look of a ring slot onto its body: the state, the one before, when it began (s.t ago), its length. */
    private void pushLook(Slot s, int prevState) {
        StormEyeFloeEntity f = s.motion != null ? floe(s) : null;
        if (f != null) {
            f.setLook(s.state, prevState, level.getGameTime() - s.t, lookLength(s.state));
        }
    }

    /** Ticks a state lasts, for the body's look (0: until the schedule says). */
    private int lookLength(int state) {
        int len = duration(state);
        return len == Integer.MAX_VALUE ? 0 : len;
    }

    /**
     * THE ICE FREEZES UNDER THEM AS THEY ARRIVE: the first layout, the centre first, then the inner ring a floe at a
     * time round it, then every other outer one - each a frost outline (FORMING) that sets from its middle out.
     */
    public void beginForming() {
        for (Slot s : slots) {
            boolean start = s.ring < 2 || (s.index % 2 == 1);
            if (!start || s.state != GONE || formAt[s.index] >= 0) {
                continue;
            }
            formAt[s.index] = age + (s.ring == 0 ? 0 : s.ring == 1 ? 6 + 3 * (s.index - 1) : 26 + 3 * (s.index - 9));
        }
    }

    /** The middle floe stands (he lets go over it). */
    public boolean centreReady() {
        return slots[0].state == SOLID && slots[0].t > SPREAD + 2;
    }

    /** Where the hall's column stands and the storm sets people down in the hall. */
    public Vec3 hallFloor() {
        return hallFloor;
    }

    /** The column's axis at height y. */
    public Vec3 axis(double y) {
        return new Vec3(cx + 0.5D, y, cz + 0.5D);
    }

    /**
     * THE ROOF GOES. Every block in a seven-wide shaft round (x, z), from `fromY` to `toY`, is taken out and written
     * down - what it was and what was in it - so dissolve can put the roof back exactly. Never the hall floor, never
     * anything at the floes' height, never liquid, never bedrock or a barrier. The pieces fall as bodies
     * (StormEyeRubbleEntity), a capped number; a container is emptied into the record first, so nothing spills.
     *
     * @return how many blocks went
     */
    public int carve(double x, double z, double fromY, double toY) {
        int n = 0;
        int y0 = Math.max((int) Math.floor(fromY), (int) Math.floor(hallFloor.y) + 5);
        int y1 = Math.min((int) Math.floor(toY), floorY - 5);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int thisTick = 0;
        for (int y = y0; y <= y1; y++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    double ddx = bx + dx + 0.5D - x, ddz = bz + dz + 0.5D - z;
                    if (ddx * ddx + ddz * ddz > HOLE_R * HOLE_R) {
                        continue;
                    }
                    p.set(bx + dx, y, bz + dz);
                    BlockState st = level.getBlockState(p);
                    if (st.isAir() || st.getBlock() instanceof StormEyeFloeBlock
                            || st.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock
                            || st.is(Blocks.BEDROCK) || st.is(Blocks.BARRIER)
                            || st.getBlock() instanceof net.minecraft.world.level.block.GameMasterBlock) {
                        continue;
                    }
                    long key = p.asLong();
                    if (!hole.containsKey(key)) {
                        CompoundTag rec = new CompoundTag();
                        rec.put("S", net.minecraft.nbt.NbtUtils.writeBlockState(st));
                        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(p);
                        if (be != null) {
                            rec.put("BE", be.saveWithFullMetadata(level.registryAccess()));
                            net.minecraft.world.Clearable.tryClear(be);      // nothing spills: it is all in the record
                        }
                        hole.put(key, rec);
                    }
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    n++;
                    if (rubbleLeft > 0 && thisTick < 14) {
                        rubbleLeft--;
                        thisTick++;
                        Vec3 out = new Vec3(ddx, 0.0D, ddz);
                        out = out.lengthSqr() > 1.0E-4D ? out.normalize() : Vec3.ZERO;
                        double kick = 0.06D + level.random.nextDouble() * 0.12D;
                        StormEyeRubbleEntity.drop(level, st, p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D,
                                out.x * kick, 0.05D + level.random.nextDouble() * 0.18D, out.z * kick);
                    }
                }
            }
        }
        return n;
    }

    /**
     * THE GAP between the middle floe (its rim 5.4 out) and the inner ring (its nearest edge 7.6 out): everyone the
     * storm carries up comes up through it, never under the ice - the middle floe stands right on the column's axis.
     */
    static final double GAP_R = 6.5D;

    /** The radius a carry rides the column at, at height y: tight to the axis through the roof, out to the gap as the
     *  ice nears. */
    private double carryRadius(double y, double tight) {
        double k = Mth.clamp((y - (floorY - 30.0D)) / 18.0D, 0.0D, 1.0D);
        return tight + (GAP_R - tight) * k;
    }

    /** How wide the shaft through the roof is: seven blocks across. */
    public static final double HOLE_R = 3.3D;

    /** The roof goes back as it was, block entities and all; whoever is standing where it goes is set down below. */
    private void restoreHole() {
        if (hole.isEmpty()) {
            return;
        }
        net.minecraft.core.HolderGetter<Block> blocks = level.holderLookup(Registries.BLOCK);
        List<AABB> boxes = new ArrayList<>();
        for (Map.Entry<Long, CompoundTag> e : hole.entrySet()) {
            BlockPos p = BlockPos.of(e.getKey());
            CompoundTag rec = e.getValue();
            BlockState st = net.minecraft.nbt.NbtUtils.readBlockState(blocks, rec.getCompound("S"));
            level.setBlock(p, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (rec.contains("BE")) {
                net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(p);
                if (be != null) {
                    be.loadWithComponents(rec.getCompound("BE"), level.registryAccess());
                    be.setChanged();
                }
            }
            boxes.add(new AABB(p));
        }
        for (ServerPlayer pl : level.getPlayers(pl -> !pl.isSpectator())) {
            for (AABB b : boxes) {
                if (b.intersects(pl.getBoundingBox())) {
                    pl.teleportTo(level, hallFloor.x, hallFloor.y + 0.1D, hallFloor.z, pl.getYRot(), pl.getXRot());
                    pl.fallDistance = 0.0F;
                    break;
                }
            }
        }
        hole.clear();
    }

    /** Draw this player up the column behind him (the ascent): `index` orders them, nearest him first. */
    public void carryBehind(ServerPlayer p, int index) {
        carries.put(p.getUUID(), new Carry(Carry.BEHIND, index));
        shield(p, 60);
    }

    public boolean isCarried(UUID id) {
        return carries.containsKey(id);
    }

    /** Everybody he drew up behind him glides off the column onto a floe of their own. */
    public void landAll() {
        for (Map.Entry<UUID, Carry> e : carries.entrySet()) {
            Carry c = e.getValue();
            if (c.mode == Carry.BEHIND) {
                c.mode = Carry.LAND;
                c.since = 0;
                c.slot = landingSlot(c.index);
            }
        }
    }

    /** The inner floes in turn round the middle (never the middle: that is where he sets the one he holds). */
    /** A ring floe that stands, the nearest to him: whoever comes up the corridor is set down by the fight, not far
     *  off from it. (None standing: the usual order.) */
    private int landingNearKing(int fallback) {
        Slot best = null;
        double bd = Double.MAX_VALUE;
        Vec3 k = king.position();
        for (Slot s : slots) {
            if (s.index == 0 || !s.standable()) {
                continue;
            }
            Vec3 tp = top(s);
            double d = (tp.x - k.x) * (tp.x - k.x) + (tp.z - k.z) * (tp.z - k.z);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best != null ? best.index : landingSlot(fallback);
    }

    private int landingSlot(int index) {
        int[] order = {1, 5, 3, 7, 2, 6, 4, 8};
        return order[Math.floorMod(index, order.length)];
    }

    /** Someone has reached the ice: the fight counts them from now on. */
    public void arrive(ServerPlayer p) {
        participants.add(p.getUUID());
        markUp(p);
        armed = true;
    }

    /**
     * THE CARRIES, a tick. Players cannot be set into place - only pushed - so each is given the velocity that takes it
     * a share of the way to where it should be, every tick, with the king's own speed added (so nobody lags behind a
     * column that is climbing two blocks a tick): the client moves itself smoothly by it.
     */
    private void tickCarries() {
        Vec3 kp = king.position();
        Vec3 kv = kp.subtract(prevKing);
        prevKing = kp;
        if (kv.lengthSqr() > 16.0D) {
            kv = Vec3.ZERO;                                    // he blinked: no feed-forward off a jump
        }
        java.util.Iterator<Map.Entry<UUID, Carry>> it = carries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Carry> e = it.next();
            Carry c = e.getValue();
            c.since++;
            ServerPlayer p = level.getPlayerByUUID(e.getKey()) instanceof ServerPlayer sp ? sp : null;
            if (p == null || !p.isAlive() || p.isSpectator()) {
                it.remove();
                continue;
            }
            shield(p, 40);
            airborne(p);
            Vec3 want;
            double gain, cap;
            Vec3 feed = Vec3.ZERO;
            switch (c.mode) {
                case Carry.BEHIND -> {
                    // a slow spiral round the column's axis under him, a little further down for each of them
                    double a = c.index * 2.1D + age * 0.16D;
                    double depth = 4.0D + 3.0D * c.index;
                    double y = Math.max(kp.y - depth, hallFloor.y + 0.4D + Math.min(c.since * 0.06D, 2.5D));
                    double rr = carryRadius(y, 1.3D);
                    want = new Vec3(kp.x + Math.cos(a) * rr, y, kp.z + Math.sin(a) * rr);
                    gain = 0.24D;
                    cap = Math.min(2.8D, 0.6D + c.since * 0.06D);      // drawn in, not snatched
                    feed = kv;
                }
                case Carry.RISE -> {
                    // up the hall's column on their own, faster and faster, to the ice (quicker since 08.10.2026: the
                    // corridor is the way back into the fight, and the fight is waiting)
                    c.vy = Math.min(2.4D, 0.35D + c.since * 0.1D);
                    double a = age * 0.2D;
                    double rr = carryRadius(p.getY(), 0.9D);
                    c.vy = Math.min(c.vy, Math.max(0.3D, (floorY - 6.0D - p.getY()) * 0.12D));
                    want = new Vec3(cx + 0.5D + Math.cos(a) * rr, p.getY() + c.vy, cz + 0.5D + Math.sin(a) * rr);
                    gain = 1.0D;
                    cap = 2.2D;
                    if (p.getY() >= floorY - 10.0D) {
                        c.mode = Carry.LAND;
                        c.since = 0;
                        c.slot = landingNearKing(level.random.nextInt(8));
                    }
                }
                default -> {
                    // up through the gap first, then off the column onto the floe: there, and hold over it until it
                    // stands, then down softly
                    Slot s = slots[Math.max(0, c.slot)];
                    if (!c.over && p.getY() < floorY + 3.0D) {
                        double a = Math.atan2(p.getZ() - (cz + 0.5D), p.getX() - (cx + 0.5D));
                        want = new Vec3(cx + 0.5D + Math.cos(a) * GAP_R, floorY + 3.6D, cz + 0.5D + Math.sin(a) * GAP_R);
                    } else {
                        // over the floe - which moves: held a little ahead of it, where it will be when they have
                        // drifted down onto it, and carried along at its own speed (no lagging behind it)
                        c.over = true;
                        want = topAt(s, now() + 14.0D).add(0.0D, 1.6D, 0.0D);
                        feed = topAt(s, now() + 1.0D).subtract(top(s));
                    }
                    gain = 0.16D;
                    cap = 0.9D;
                    boolean ready = s.standable() || c.since > 120;
                    if (ready && p.position().distanceTo(want) < 1.0D) {
                        it.remove();
                        p.removeEffect(MobEffects.LEVITATION);
                        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 50, 0, false, false, true));
                        shove(p, 0.0D, -0.05D, 0.0D);
                        shield(p, 60);
                        arrive(p);
                        continue;
                    }
                }
            }
            // A COLUMN IN THE WAY: someone pushed at a pillar goes nowhere, however hard. Ten ticks of that and the wind
            // lifts them past it - the nearest free spot a step or two along the way they are going, or over it.
            Vec3 pos = p.position();
            if (c.last != null && pos.distanceToSqr(c.last) < 0.01D && want.distanceToSqr(pos) > 1.0D) {
                if (++c.stuck >= 10) {
                    c.stuck = 0;
                    Vec3 dir = want.subtract(pos).normalize();
                    for (Vec3 step : new Vec3[]{dir.scale(1.2D), dir.scale(2.2D), new Vec3(0.0D, 1.5D, 0.0D),
                            dir.scale(1.2D).add(0.0D, 1.5D, 0.0D), new Vec3(0.0D, 3.0D, 0.0D)}) {
                        if (level.noCollision(p, p.getBoundingBox().move(step))) {
                            Vec3 to = pos.add(step);
                            p.teleportTo(level, to.x, to.y, to.z, p.getYRot(), p.getXRot());
                            break;
                        }
                    }
                }
            } else {
                c.stuck = 0;
            }
            c.last = pos;
            Vec3 go = want.subtract(pos).scale(gain).add(feed);
            if (go.length() > cap) {
                go = go.normalize().scale(cap);
            }
            shove(p, go.x, go.y, go.z);
        }
    }

    /**
     * Floe blocks left from an earlier arena here (a save mid-fight) are cleared before the new ones go in - all but
     * the centre's own when `keepCentre` (an arena loaded from its save keeps its centre as it stands).
     */
    private void clearStale(boolean keepCentre) {
        int span = (int) (OUTER_RING + OUTER_R + 2);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -4; dy <= 0; dy++) {
            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    p.set(cx + dx, floorY + dy, cz + dz);
                    if (keepCentre && claimed.contains(p.asLong())) {
                        continue;
                    }
                    if (level.getBlockState(p).getBlock() instanceof StormEyeFloeBlock) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    private void put(Slot s, int i, int stage) {
        if (s.shown[i] == stage) {
            return;
        }
        BlockPos pos = s.blocks.get(i);
        BlockState here = level.getBlockState(pos);
        if (!here.isAir() && !(here.getBlock() instanceof StormEyeFloeBlock)) {
            return;                                           // never over anything that is not ours
        }
        BlockState want = stage == AIR ? Blocks.AIR.defaultBlockState()
                : FFBlocks.STORM_EYE_FLOE.get().defaultBlockState().setValue(StormEyeFloeBlock.STAGE, stage);
        level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        s.shown[i] = (byte) stage;
    }

    private static int stageOf(int state) {
        return switch (state) {
            case SOLID -> StormEyeFloeBlock.FRESH;
            case MARKED -> StormEyeFloeBlock.MARKED;
            case CRACKED -> StormEyeFloeBlock.CRACKED;
            case SLUSH -> StormEyeFloeBlock.SLUSH;
            case FORMING -> StormEyeFloeBlock.FORMING;
            default -> AIR;
        };
    }

    private void enter(Slot s, int state) {
        int was = s.state;
        s.prevStage = stageOf(s.state);
        s.state = state;
        s.t = 0;
        Vec3 c = top(s);
        switch (state) {
            case MARKED -> sound(c, FFSounds.STORM_EYE_CRACK.get(), 0.9F, 1.35F);
            case CRACKED -> sound(c, FFSounds.STORM_EYE_CRACK.get(), 2.0F, 0.85F);
            case SLUSH -> {
                sound(c, FFSounds.STORM_EYE_MELT.get(), 1.6F, 1.1F);
                // slush settles a little into the storm (a lift, so the body and every client sink it alike)
                if (s.motion != null) {
                    lift(s, s.restLift - 0.25D, duration(SLUSH), false);
                }
            }
            case GONE -> {
                sound(c, FFSounds.STORM_EYE_MELT.get(), 2.2F, 0.7F);
                s.goneAt = age;
                debris(s);
            }
            case FORMING -> {
                sound(c, FFSounds.STORM_EYE_FREEZE.get(), 1.6F, 1.0F);
                // new ice freezes at a rest that leaves it joined to the rest (the ghost drifts there as it sets)
                if (s.motion != null) {
                    s.restLift = formingLift(s);
                    lift(s, s.restLift, duration(FORMING), false);
                }
            }
            case SOLID -> {
                // held back from the storm (an anchor on it): up out of the slush, back to its rest
                if (s.motion != null && Math.abs(s.motion.liftTo - s.restLift) > 1.0E-3D) {
                    lift(s, s.restLift, 20, false);
                }
            }
            default -> {
            }
        }
        pushLook(s, was);
    }

    /** Chunks of the floe going down into the storm - real tumbling blocks, a handful. */
    private void debris(Slot s) {
        BlockState slush = FFBlocks.STORM_EYE_FLOE.get().defaultBlockState()
                .setValue(StormEyeFloeBlock.STAGE, StormEyeFloeBlock.SLUSH);
        StormEyeFloeEntity.Layout lay = StormEyeFloeEntity.layout(s.r);
        for (int k = 0; k < 5; k++) {
            Vec3 p;
            if (s.motion == null) {
                BlockPos b = s.blocks.get(level.random.nextInt(s.blocks.size()));
                p = new Vec3(b.getX() + 0.5D, b.getY() + 0.2D, b.getZ() + 0.5D);
            } else {
                int[] cell = lay.cells[level.random.nextInt(lay.cells.length)];
                p = s.motion.local(cell[0], -0.8D - cell[1], cell[2], now());     // where that cell is right now
            }
            level.addFreshEntity(new com.jastkub.frozenfortress.entity.effect.FallingDebrisEntity(level, slush,
                    p.x, p.y, p.z,
                    (level.random.nextDouble() - 0.5D) * 0.08D, -0.05D - level.random.nextDouble() * 0.1D,
                    (level.random.nextDouble() - 0.5D) * 0.08D, 46));
        }
    }

    /**
     * EASE A RING FLOE to the lift `to` over `len` ticks, from wherever it is now (smoothstep; StormEyeFloeEntity
     * .Motion.lift). The body is told once; both sides ease it alike. `grind`: the ice groans as it goes.
     */
    private void lift(Slot s, double to, int len, boolean grind) {
        StormEyeFloeEntity.Motion m = s.motion;
        if (m == null) {
            return;
        }
        double t = now();
        double from = m.lift(t);
        m.liftFrom = (float) from;
        m.liftTo = (float) to;
        m.liftAt = (long) t;
        m.liftLen = Math.max(1, len);
        StormEyeFloeEntity f = floe(s);
        if (f != null) {
            f.applyLift(m);
        }
        if (grind) {
            sound(top(s), FFSounds.ICE_GRIND.get(), 1.5F, to > from ? 0.8F : 0.62F);
            sound(top(s), FFSounds.STORM_EYE_CRACK.get(), 0.6F, 0.6F);
        }
    }

    private int duration(int state) {
        boolean fast = closing;
        return switch (state) {
            case MARKED -> fast ? 36 : 50;
            case CRACKED -> fast ? 26 : 36;
            case SLUSH -> fast ? 16 : 24;
            case FORMING -> !armed ? 30 : fast ? 30 : 44;           // quick while they arrive
            default -> Integer.MAX_VALUE;
        };
    }

    private void tickSlots() {
        for (Slot s : slots) {
            if (formAt[s.index] >= 0 && age >= formAt[s.index]) {
                formAt[s.index] = -1;
                if (s.state == GONE) {
                    enter(s, FORMING);
                }
            }
            s.t++;
            // the look, spread over the floe
            if (s.t <= SPREAD + 12) {
                int stage = stageOf(s.state);
                boolean centreFirst = s.state == FORMING || (s.state == SOLID && s.prevStage == StormEyeFloeBlock.FORMING);
                for (int i = 0; i < s.blocks.size(); i++) {
                    int d = centreFirst ? SPREAD + 9 - s.edgeDelay[i] : s.edgeDelay[i];
                    put(s, i, s.t >= d ? stage : (s.prevStage == AIR && s.state == FORMING ? AIR : s.prevStage));
                }
            }
            // the clock
            if (s.anchored && s.state != FORMING) {
                if (s.state != SOLID) {
                    enter(s, SOLID);                     // an anchor holds its floe frozen
                }
                continue;
            }
            if (dyingAt >= 0 && (s.state == SLUSH || s.state == CRACKED || s.state == MARKED)) {
                continue;                                 // the king is dying: nothing more goes into the storm
            }
            switch (s.state) {
                case MARKED -> {
                    if (s.t >= duration(MARKED)) {
                        enter(s, CRACKED);
                    }
                }
                case CRACKED -> {
                    if (s.t >= duration(CRACKED)) {
                        enter(s, SLUSH);
                    }
                }
                case SLUSH -> {
                    if (s.t >= duration(SLUSH)) {
                        enter(s, GONE);
                    }
                }
                case FORMING -> {
                    if (s.t >= duration(FORMING)) {
                        if (occupied(s)) {
                            s.t = duration(FORMING) - 10;  // somebody is in the ghost: wait for them to leave it
                        } else {
                            enter(s, SOLID);
                            sound(top(s), FFSounds.CRYSTAL_CHIME.get(), 1.4F, 1.3F);
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    /** Somebody in the ghost of new ice - it does not set round them (they would be inside it, and fall through). */
    private boolean occupied(Slot s) {
        Vec3 c = top(s);
        double r = s.r + 0.6D + (s.motion != null ? LAG * 0.5D : 0.0D);
        AABB box = new AABB(c.x - r, c.y - 4.0D, c.z - r, c.x + r, c.y, c.z + r);
        return !level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive()
                && !(e instanceof StormEyeAnchorEntity) && !(e instanceof StormEyeMirrorEntity)).isEmpty();
    }

    // ================================================================================================ the schedule
    /** The rest a floe is planned at (the centre lies at 0): what the joins are weighed with. */
    private static double restOf(Slot s) {
        return s.motion == null ? 0.0D : s.restLift;
    }

    /**
     * HOW LONG EACH FLOE THAT WILL STAND IS CUT OFF from the centre, over one whole slide of the rings past each other
     * (every offset between them comes round once in RELATIVE_TURN ticks; JOIN_SAMPLES of them are looked at): the
     * longest run, in ticks, of offsets at which it cannot be reached by jumps across JUMP_GAP or less, none a climb of
     * more than UP_REACH between rests. 0 = joined at every offset. The change being weighed: `without` taken as gone,
     * `plus` as standing, `lifted` as resting at `liftTo`.
     */
    private int[] cutOff(@Nullable Slot without, @Nullable Slot plus, @Nullable Slot lifted, double liftTo) {
        int n = slots.length;
        boolean[] up = new boolean[n];
        double[] rest = new double[n];
        for (Slot s : slots) {
            up[s.index] = s.index == 0 || (s != without && (s == plus || s.lasting()));
            rest[s.index] = s == lifted ? liftTo : restOf(s);
        }
        double step = RELATIVE_TURN / JOIN_SAMPLES;
        double t0 = now();
        boolean[][] cut = new boolean[JOIN_SAMPLES][n];
        double[] px = new double[n], pz = new double[n];
        boolean[] seen = new boolean[n];
        int[] queue = new int[n];
        for (int j = 0; j < JOIN_SAMPLES; j++) {
            double t = t0 + j * step;
            for (Slot s : slots) {
                px[s.index] = s.motion == null ? cx + 0.5D : s.motion.x(t);
                pz[s.index] = s.motion == null ? cz + 0.5D : s.motion.z(t);
            }
            java.util.Arrays.fill(seen, false);
            seen[0] = true;
            queue[0] = 0;
            int head = 0, tail = 1;
            while (head < tail) {
                int a = queue[head++];
                for (int b = 1; b < n; b++) {
                    if (seen[b] || !up[b] || Math.abs(rest[a] - rest[b]) > UP_REACH + 1.0E-6D) {
                        continue;
                    }
                    double dx = px[a] - px[b], dz = pz[a] - pz[b];
                    if (Math.sqrt(dx * dx + dz * dz) - slots[a].r - slots[b].r <= JUMP_GAP) {
                        seen[b] = true;
                        queue[tail++] = b;
                    }
                }
            }
            for (int i = 1; i < n; i++) {
                cut[j][i] = up[i] && !seen[i];
            }
        }
        int[] out = new int[n];
        for (int i = 1; i < n; i++) {
            if (!up[i]) {
                continue;
            }
            int run = 0, best = 0;
            for (int j = 0; j < 2 * JOIN_SAMPLES; j++) {         // twice round: a run may wrap past the start
                run = cut[j % JOIN_SAMPLES][i] ? run + 1 : 0;
                best = Math.max(best, Math.min(run, JOIN_SAMPLES));
            }
            out[i] = (int) Math.ceil(best * step);
        }
        return out;
    }

    /**
     * May the change go ahead? Nobody that will stand is left cut off longer than CUT_OFF_MAX - or, where something
     * the schedule does not choose (an anchor's floe frozen where it must be) has already left one so, longer than
     * it already is. So a bad moment is never made worse, and never locks the schedule either.
     */
    private static boolean accepts(int[] before, int[] after) {
        for (int i = 1; i < after.length; i++) {
            if (after[i] > Math.max(CUT_OFF_MAX, before[i])) {
                return false;
            }
        }
        return true;
    }

    /** Would every lasting floe still reach the centre - in time, as the rings turn - without `without`? */
    private boolean staysJoined(@Nullable Slot without) {
        return accepts(cutOff(null, null, null, 0.0D), cutOff(without, null, null, 0.0D));
    }

    /**
     * The rest new ice freezes at: the one it last had, else the centre's level, else a step either way - the first
     * that leaves it (and everybody) joined; failing all, the least cut off. All level for the first layout.
     */
    private double formingLift(Slot s) {
        if (!armed) {
            return 0.0D;
        }
        int[] before = cutOff(s, null, null, 0.0D);
        double best = 0.0D;
        int bestRun = Integer.MAX_VALUE;
        for (double l : new double[]{s.restLift, 0.0D, LIFT_STEP, -LIFT_STEP, 2.0D * LIFT_STEP, -2.0D * LIFT_STEP}) {
            if (Math.abs(l) > LIFT_MAX + 1.0E-6D) {
                continue;
            }
            int[] after = cutOff(null, s, s, l);
            if (after[s.index] <= CUT_OFF_MAX && accepts(before, after)) {
                return l;
            }
            if (after[s.index] < bestRun) {
                bestRun = after[s.index];
                best = l;
            }
        }
        return best;
    }

    private int lastingCount() {
        int n = 0;
        for (Slot s : slots) {
            if (s.index != 0 && s.lasting()) {
                n++;
            }
        }
        return n;
    }

    private void tickSchedule() {
        if (dyingAt >= 0 || !armed) {
            return;                                      // nothing melts before anybody is up here to see it
        }
        // ---- the front goes round (clockwise seen from above)
        front = (front + Math.PI * 2.0D / (closing ? FRONT_TURN_CLOSING : FRONT_TURN)) % (Math.PI * 2.0D);
        // ---- a floe starts to go
        if (--nextMelt <= 0) {
            nextMelt = (closing ? 40 : 60) + level.random.nextInt(closing ? 15 : 25);
            int floor = closing ? 5 : 6;
            if (lastingCount() > floor) {
                Slot pick = pickMelt();
                if (pick != null) {
                    enter(pick, MARKED);
                }
            }
        }
        // ---- new ice
        if (--nextFreeze <= 0) {
            nextFreeze = closing ? 25 : 35;
            int want = closing ? 8 : 10;
            if (lastingCount() < want) {
                Slot pick = pickFreeze();
                if (pick != null) {
                    enter(pick, FORMING);
                }
            }
        }
        // ---- a floe or two rises or sinks
        if (--nextLift <= 0) {
            nextLift = (closing ? 50 : 70) + level.random.nextInt(50);
            liftSome(level.random.nextInt(3) == 0 ? 2 : 1);
        }
    }

    /**
     * RISE AND SINK: `count` floes eased a step or a half
     * step up or down over a second and a half, grinding. Never the centre, never one going or forming, never one an
     * anchor stands on, never one already moving - and only to a rest that keeps everybody joined (accepts).
     */
    private void liftSome(int count) {
        List<Slot> pool = new ArrayList<>();
        double t = now();
        for (Slot s : slots) {
            if (s.motion != null && s.state == SOLID && !s.anchored && s.t > SPREAD + 12 && !s.motion.lifting(t)) {
                pool.add(s);
            }
        }
        java.util.Collections.shuffle(pool, new java.util.Random(level.random.nextLong()));
        int[] before = cutOff(null, null, null, 0.0D);
        for (Slot s : pool) {
            if (count <= 0) {
                return;
            }
            double cur = s.restLift;
            double dir = cur >= LIFT_MAX - 1.0E-6D ? -1.0D : cur <= -LIFT_MAX + 1.0E-6D ? 1.0D
                    : level.random.nextBoolean() ? 1.0D : -1.0D;
            for (double l : new double[]{cur + dir * 2.0D * LIFT_STEP, cur + dir * LIFT_STEP,
                    cur - dir * 2.0D * LIFT_STEP, cur - dir * LIFT_STEP}) {
                if (Math.abs(l) > LIFT_MAX + 1.0E-6D || !accepts(before, cutOff(null, null, s, l))) {
                    continue;
                }
                s.restLift = l;
                lift(s, l, LIFT_TICKS, true);
                before = cutOff(null, null, null, 0.0D);
                count--;
                break;
            }
        }
    }

    /** The floe somebody is standing on, more often than not - the ice is what keeps you moving. */
    @Nullable
    private Slot pickMelt() {
        List<Slot> pool = new ArrayList<>();
        Set<Integer> underFeet = new HashSet<>();
        for (ServerPlayer p : players()) {
            Slot s = slotUnder(p.getX(), p.getY(), p.getZ());
            if (s != null) {
                underFeet.add(s.index);
            }
        }
        int[] before = null;
        for (Slot s : slots) {
            if (s.index == 0 || s.state != SOLID || s.anchored || age - s.goneAt < 60) {
                continue;
            }
            if (closing && s.ring == 1 && level.random.nextInt(3) != 0) {
                continue;                                  // closing eats the outside first
            }
            if (before == null) {
                before = cutOff(null, null, null, 0.0D);
            }
            if (!accepts(before, cutOff(s, null, null, 0.0D))) {
                continue;                                  // it would leave somebody's floe out of reach
            }
            // the front first, then what is under their feet
            double ahead = aheadOfFront(s);
            int weight = (ahead < Math.toRadians(30) ? 10 : ahead < Math.toRadians(60) ? 3 : 0)
                    + (underFeet.contains(s.index) ? 2 : 0) + (s.ring == 2 ? 1 : 0);
            for (int w = 0; w < Math.max(1, weight); w++) {
                pool.add(s);
            }
        }
        return pool.isEmpty() ? null : pool.get(level.random.nextInt(pool.size()));
    }

    /** New ice: where the front passed long ago, and only where it will be joined to the rest as the rings turn. */
    @Nullable
    private Slot pickFreeze() {
        List<Slot> pool = new ArrayList<>();
        int[] before = null;
        for (Slot s : slots) {
            if (s.state != GONE || age - s.goneAt < 60 || (closing && s.ring == 2)) {
                continue;
            }
            if (before == null) {
                before = cutOff(null, null, null, 0.0D);
            }
            boolean joined = false;
            for (double l : new double[]{s.restLift, 0.0D}) {        // as formingLift will try them
                int[] after = cutOff(null, s, s, l);
                if (after[s.index] <= CUT_OFF_MAX && accepts(before, after)) {
                    joined = true;
                    break;
                }
            }
            if (joined) {
                // behind the front: what it passed long ago comes back first
                double behind = Math.PI * 2.0D - aheadOfFront(s);
                int weight = behind > Math.toRadians(120) ? 4 : behind > Math.toRadians(60) ? 2 : 1;
                for (int w = 0; w < weight; w++) {
                    pool.add(s);
                }
            }
        }
        return pool.isEmpty() ? null : pool.get(level.random.nextInt(pool.size()));
    }

    /** How far round from the front a floe lies now, the way the front is going (0 .. 2 pi; small = about to be hit). */
    private double aheadOfFront(Slot s) {
        Vec3 c = top(s);
        double a = Math.atan2(c.z - (cz + 0.5D), c.x - (cx + 0.5D));
        double d = (a - front) % (Math.PI * 2.0D);
        return d < 0 ? d + Math.PI * 2.0D : d;
    }

    /** The thunder rune landed on this floe: it starts to go (never the centre, never one an anchor holds). */
    public void strike(int slotIndex) {
        if (slotIndex <= 0 || slotIndex >= slots.length) {
            return;
        }
        Slot s = slots[slotIndex];
        if (s.state == SOLID && !s.anchored && staysJoined(s) && dyingAt < 0) {
            enter(s, MARKED);
            s.t = duration(MARKED) - 30;
            pushLook(s, SOLID);
        }
    }

    // ================================================================================================ anchors
    /**
     * Where the Storm Anchors go up: outer floes spread round the ring (3, or 4 for a group). A slot with no ice is
     * frozen first. Returns {x, y, z, ticks until the floe stands} per anchor.
     */
    public List<double[]> anchorSpots(int count) {
        int[] picks = count >= 4 ? new int[]{0, 2, 4, 6} : new int[]{0, 3, 5};
        int k0 = level.random.nextInt(8);
        List<double[]> out = new ArrayList<>();
        for (int p : picks) {
            Slot s = slots[9 + (k0 + p) % 8];
            int wait = 0;
            if (!s.standable()) {
                if (s.state != FORMING) {
                    enter(s, FORMING);
                }
                s.t = Math.max(s.t, duration(FORMING) - 26);
                pushLook(s, GONE);
                wait = Math.max(0, duration(FORMING) - s.t) + 2;
            } else if (s.state != SOLID) {
                enter(s, SOLID);
            }
            s.anchored = true;
            Vec3 c = top(s);
            out.add(new double[]{c.x, c.y, c.z, wait, s.index});
        }
        return out;
    }

    public void addAnchor(StormEyeAnchorEntity anchor) {
        anchors.add(anchor.getUUID());
    }

    /** An anchor broke: its floe is free to melt again. */
    public void anchorGone(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < slots.length) {
            slots[slotIndex].anchored = false;
        }
    }

    public int anchorsStanding() {
        int n = 0;
        for (UUID id : anchors) {
            if (level.getEntity(id) instanceof StormEyeAnchorEntity a && a.isAlive()) {
                n++;
            }
        }
        if (n == 0) {
            anchors.clear();
        }
        return n;
    }

    // ================================================================================================ the closing
    /** OKO SIE ZAMYKA: the vortex climbs, every outer floe the anchors do not hold goes, the ice runs twice as fast. */
    public void beginClosing() {
        if (closing) {
            return;
        }
        closing = true;
        int k = 0;
        for (Slot s : slots) {
            if (s.ring == 2 && s.state == SOLID && !s.anchored) {
                enter(s, MARKED);
                s.t = Math.min(duration(MARKED) - 1, duration(MARKED) - 24 - k * 14);
                pushLook(s, SOLID);
                k++;
            }
        }
        nextMelt = 60;
    }

    // ================================================================================================ the fx bodies
    private void ensureFx() {
        if (vortexId == null || !(level.getEntity(vortexId) instanceof StormEyeVortexEntity)) {
            StormEyeVortexEntity v = StormEyeVortexEntity.arena(level, centre(), king);
            vortexId = v.getUUID();
        }
        if (wallId == null || !(level.getEntity(wallId) instanceof StormEyeWallEntity)) {
            StormEyeWallEntity w = StormEyeWallEntity.ring(level, centre(), king);
            wallId = w.getUUID();
        }
        // the hall's column: only once they are up (until then the column is the one round HIM), and it reaches all
        // the way up through the hole in the roof to the ice - the way back up, for whoever fell
        if (armed && (liftId == null || !(level.getEntity(liftId) instanceof StormEyeVortexEntity))) {
            StormEyeVortexEntity l = StormEyeVortexEntity.lift(level, liftFloor(), king);
            l.setCalm(true);
            l.setHeight((float) (floorY + 1.0D - hallFloor.y));
            liftId = l.getUUID();
        }
    }

    /** The hall's column stands on the floor under the dome (straight under the hole and the ice). */
    public Vec3 liftFloor() {
        return hallFloor;
    }

    /** The column the ascent opens in the hall (it stays, quieter, so the fallen can climb back up). */
    @Nullable
    public StormEyeVortexEntity liftColumn() {
        return liftId != null && level.getEntity(liftId) instanceof StormEyeVortexEntity v ? v : null;
    }

    // ================================================================================================ the people
    private List<ServerPlayer> players() {
        return level.getPlayers(p -> !p.isSpectator() && p.isAlive() && contains(p.position()));
    }

    /** The people up here he can fight: alive, in the arena, not watching in creative. */
    public List<ServerPlayer> fighters() {
        return level.getPlayers(p -> !p.isSpectator() && !p.isCreative() && p.isAlive() && contains(p.position()));
    }

    /** Put a player into the arena from the hall: the cloud closes over them and opens on a floe. */
    public void bringIn(ServerPlayer p, int index) {
        int[] order = {0, 1, 3, 5, 7, 2, 4, 6, 8};
        Slot s = slots[order[index % order.length]];
        if (!s.standable()) {
            s = nearestStanding(centre(), true);
            if (s == null) {
                s = slots[0];
            }
        }
        // where the floe will be by the time they have floated down onto it (a ring floe moves: from lower, sooner)
        boolean ring = s.motion != null;
        Vec3 c = topAt(s, now() + (ring ? 24.0D : 0.0D));
        double a = index * 2.4D;
        double off = s.index == 0 ? 2.5D : 0.8D;
        Vec3 at = c.add(Math.cos(a) * off, ring ? 1.6D : 5.0D, Math.sin(a) * off);
        p.stopRiding();
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 70, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 14, 0, false, false));
        float yaw = (float) (Math.atan2(cz + 0.5D - at.z, cx + 0.5D - at.x) * (180.0D / Math.PI)) - 90.0F;
        p.teleportTo(level, at.x, at.y, at.z, yaw, 10.0F);
        p.fallDistance = 0.0F;
        markUp(p);
        participants.add(p.getUUID());
        armed = true;
    }

    /** Everybody still up here goes back to the hall: a gust takes them down and sets them on the floor. */
    public void releaseAll() {
        Vec3 floor = liftFloor();
        int i = 0;
        for (ServerPlayer p : players()) {
            double a = i * 1.9D + 0.6D;
            double r = 2.5D + (i % 3);
            Vec3 at = floor.add(Math.cos(a) * r, 0.2D, Math.sin(a) * r);
            BlockPos feet = BlockPos.containing(at);
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                at = floor.add(0.0D, 0.2D, 0.0D);
            }
            HELD.remove(p.getUUID());
            carries.remove(p.getUUID());
            p.removeEffect(MobEffects.LEVITATION);
            if (p.isPassenger()) {
                p.stopRiding();
            }
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
            p.teleportTo(level, at.x, at.y, at.z, p.getYRot(), p.getXRot());
            p.fallDistance = 0.0F;
            p.getPersistentData().remove(HOME_TAG);
            sound(at, FFSounds.STORM_EYE_GUST.get(), 1.2F, 0.8F);
            i++;
        }
    }

    // ================================================================================================ the tick
    /**
     * One tick of the arena. Returns true when the fight up here has been LOST: every player who came up is dead or
     * gone for two seconds - the king takes it down and goes home.
     */
    public boolean tick() {
        if (dissolved) {
            return false;
        }
        age++;
        if (!built) {
            build();
        }
        if (age % 20 == 0) {
            ensureFx();
            ensureFloes();
        }
        tickSlots();
        tickSchedule();
        tickCarries();
        // the vortex climbs while the eye closes; everything fades while he dies
        if (closing && rise < 1.0F) {
            rise = Math.min(1.0F, rise + 1.0F / 80.0F);
        }
        if (dyingAt >= 0) {
            fade = Math.min(1.0F, fade + 1.0F / 120.0F);
        }
        if (vortexId != null && level.getEntity(vortexId) instanceof StormEyeVortexEntity v) {
            v.setRise(rise);
            v.setFade(fade);
        }
        if (wallId != null && level.getEntity(wallId) instanceof StormEyeWallEntity w) {
            w.setFade(fade);
        }
        tickHall();
        int here = 0;
        for (ServerPlayer p : players()) {
            here++;
            participants.add(p.getUUID());
            tickPlayer(p);
        }
        if (armed && dyingAt < 0) {
            emptyFor = here == 0 ? emptyFor + 1 : 0;
            return emptyFor > 40;
        }
        return false;
    }

    /** Persistent-data key: where (in the hall) a player went up from - see StormEyeEvents' log-in check. */
    public static final String HOME_TAG = "frozen_dominion_storm_eye_home";

    private void markUp(ServerPlayer p) {
        Vec3 f = liftFloor();
        p.getPersistentData().putLong(HOME_TAG, BlockPos.containing(f.x, f.y + 0.2D, f.z).asLong());
    }

    private void tickPlayer(ServerPlayer p) {
        UUID id = p.getUUID();
        if (carries.containsKey(id) || HELD.contains(id)) {
            return;                                          // the storm has them: none of its rules apply
        }
        if (age % 100 == 0 && !p.isCreative()) {
            markUp(p);
        }
        boolean creative = p.isCreative();
        Vec3 c = centre();
        double dx = p.getX() - c.x, dz = p.getZ() - c.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        // ---- NO WINGS UP HERE
        if (p.isFallFlying()) {
            p.stopFallFlying();
            if (flightNag.getOrDefault(id, -100) + 60 < age) {
                flightNag.put(id, age);
                p.displayClientMessage(Component.translatable("message.frozen_dominion.storm_eye_no_flight")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
        // ---- INTO THE STORM
        Integer since = carried.get(id);
        boolean beingCarried = since != null && age - since < 90;
        if (p.getY() < floorY - FALL_LINE && !beingCarried && !creative) {
            fall(p);
            return;
        }
        // ---- ON MOVING ICE: the server never collides them with a ring floe (it is the client's to stand on), so it
        // is told they are held up - or a dedicated server would kick them for flying after four seconds on one
        if (ringFloeUnderFeet(p) != null) {
            afloat(p);
        }
        // ---- THE WALL AND THE LID
        int clock = shockClock.getOrDefault(id, -100);
        if (age - clock >= 12 && fade < 0.6F) {
            if (flat > WALL_R - 0.9D && p.getY() > floorY - WALL_BELOW && !creative) {
                shockClock.put(id, age);
                Vec3 in = new Vec3(-dx, 0.0D, -dz).normalize();
                Vec3 wallPt = new Vec3(c.x - in.x * WALL_R, p.getEyeY(), c.z - in.z * WALL_R);
                StormEyeBoltEntity.arc(level, wallPt, p.position().add(0.0D, p.getBbHeight() * 0.6D, 0.0D), 10, 0.22F, 0);
                shove(p, in.x * 1.25D, 0.62D, in.z * 1.25D);
                hurtByStorm(p, 14.0F);
                sound(p.position(), FFSounds.STORM_EYE_SHOCK.get(), 1.6F, 1.0F);
            } else if (p.getY() > floorY + CEILING && !creative) {
                shockClock.put(id, age);
                StormEyeBoltEntity.arc(level, p.position().add(0.0D, 6.0D, 0.0D),
                        p.position().add(0.0D, p.getBbHeight() * 0.6D, 0.0D), 10, 0.22F, 0);
                shove(p, p.getDeltaMovement().x * 0.3D, -0.9D, p.getDeltaMovement().z * 0.3D);
                hurtByStorm(p, 10.0F);
                sound(p.position(), FFSounds.STORM_EYE_SHOCK.get(), 1.6F, 0.8F);
            }
        }
        // ---- THE STORM'S VOICE: a gust past each ear now and then, never twice at once
        if ((age + (id.hashCode() & 63)) % 110 == 0) {
            p.playNotifySound(FFSounds.STORM_EYE_WIND.get(), SoundSource.AMBIENT, 0.55F,
                    0.85F + level.random.nextFloat() * 0.3F);
        }
    }

    private void hurtByStorm(ServerPlayer p, float raw) {
        if (king.isAlive()) {
            king.stormStrikeRaw(p, raw);
        } else {
            p.hurt(level.damageSources().magic(), raw * 0.3F);
        }
    }

    private static void shove(ServerPlayer p, double x, double y, double z) {
        p.setDeltaMovement(x, y, z);
        p.hurtMarked = true;
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
    }

    /**
     * FALLEN INTO THE STORM. The first time in a fight a gust throws them back onto the nearest floe and it costs half
     * of what health they have; after that each fall eats a Gale Feather from the pack (GaleFeatherItem), at the same
     * cost - and with none left it is death. While the king dies nobody dies: the storm lets go.
     */
    private void fall(ServerPlayer p) {
        UUID id = p.getUUID();
        if (dyingAt >= 0) {
            rescue(p, false);                              // he is dying: the storm lets go of everybody, free
            return;
        }
        if (fromSave && age < 200) {
            // just loaded: the ring floes are bodies, not saved blocks, and come back a moment after the player does -
            // a fall in that moment is the world's, not theirs
            rescue(p, false);
            return;
        }
        int n = falls.merge(id, 1, Integer::sum);
        if (n <= 1) {
            rescue(p, true);
            p.displayClientMessage(Component.translatable("message.frozen_dominion.storm_eye_spared")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), false);
            return;
        }
        // A GALE FEATHER eaten: the gust again, for one from the pack - and it is counted, so that
        // if this fight is lost the feather comes back with the rest at the shrine (FrostShrineBlockEntity)
        int slot = featherSlot(p);
        if (slot >= 0) {
            p.getInventory().removeItem(slot, 1);
            CompoundTag kept = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            kept.putInt(FEATHERS_SPENT, kept.getInt(FEATHERS_SPENT) + 1);
            p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, kept);
            rescue(p, true);
            int left = p.getInventory().countItem(com.jastkub.frozenfortress.registry.FFItems.GALE_FEATHER.get());
            p.displayClientMessage(Component.translatable("message.frozen_dominion.storm_eye_spared_feather", left)
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), false);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK,
                    SoundSource.PLAYERS, 1.2F, 1.4F);
            return;
        }
        p.hurt(stormDamage(level), Float.MAX_VALUE);
        if (p.isAlive()) {
            rescue(p, false);                              // a charm that cheats death: the storm honours it
        }
    }

    /** Gale Feathers eaten in the fight going on (in the player's persisted data): given back if it is lost. */
    public static final String FEATHERS_SPENT = "ffGaleFeathersSpent";

    private static int featherSlot(ServerPlayer p) {
        net.minecraft.world.entity.player.Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(com.jastkub.frozenfortress.registry.FFItems.GALE_FEATHER.get())) {
                return i;
            }
        }
        return -1;
    }

    private void rescue(ServerPlayer p, boolean costs) {
        double[] to = landingFor(p.position());          // a floe and a spot on it: the gust follows it as it moves
        if (costs) {
            p.setHealth(Math.max(1.0F, p.getHealth() * 0.5F));
            p.hurtMarked = true;
            level.playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_HURT,
                    SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        carried.put(p.getUUID(), age);
        StormEyeGustEntity.rescue(level, p, (int) to[0], to[1], to[2], this);
    }

    /** The gust has set them down. */
    public void delivered(UUID id) {
        carried.remove(id);
    }

    /**
     * THE AIR CORRIDOR: the king stays up in his storm, and whoever walks into the hall's column - back from the
     * shrine, or new to the fight - is taken up by it on their own: a breath of pull to its middle (PULL_IN ticks),
     * then the rise, through the hole to the ice, onto a floe beside him (landingNearKing).
     */
    /** How long the corridor draws someone in before it takes them up (it was sixteen ticks of standing in it). */
    private static final int PULL_IN = 6;

    private void tickHall() {
        if (dyingAt >= 0 || !armed) {
            return;
        }
        Vec3 col = liftFloor();
        for (ServerPlayer p : level.getPlayers(pl -> !pl.isSpectator() && pl.isAlive()
                && Math.abs(pl.getY() - col.y) < 6.0D
                && pl.distanceToSqr(col.x, pl.getY(), col.z) < 3.6D * 3.6D)) {
            int t = liftClock.merge(p.getUUID(), 1, Integer::sum);
            if (t == 1) {
                p.displayClientMessage(Component.translatable("message.frozen_dominion.storm_eye_pulled")
                        .withStyle(ChatFormatting.AQUA), true);
            }
            if (carries.containsKey(p.getUUID())) {
                continue;
            }
            if (t < PULL_IN) {
                shove(p, (col.x - p.getX()) * 0.16D, 0.12D + t * 0.02D, (col.z - p.getZ()) * 0.16D);
            } else {
                // UP THE COLUMN, through the hole, to the ice - every block of it seen
                liftClock.remove(p.getUUID());
                sound(p.position(), FFSounds.STORM_EYE_GUST.get(), 1.6F, 1.1F);
                carries.put(p.getUUID(), new Carry(Carry.RISE, 0));
                shield(p, 60);
            }
        }
        if (age % 40 == 0) {
            liftClock.keySet().removeIf(id -> !(level.getPlayerByUUID(id) instanceof ServerPlayer sp)
                    || sp.distanceToSqr(col.x, sp.getY(), col.z) > 4.0D * 4.0D);
        }
    }

    // ================================================================================================ endings
    /** The king has died up here: from now on nothing melts away and nobody dies; the storm fades over the scene. */
    public void kingDying() {
        if (dyingAt < 0) {
            for (ServerPlayer p : level.players()) {                 // (the fight is won: the feathers are spent)
                CompoundTag kept = p.getPersistentData().getCompound(
                        net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
                if (kept.contains(FEATHERS_SPENT)) {
                    kept.remove(FEATHERS_SPENT);
                    p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, kept);
                }
            }
            dyingAt = age;
            silence();
        }
    }

    /** His spells die with him: the runes, the gale, the orbs, the mirrors and the anchors go at once. */
    private void silence() {
        AABB sky = new AABB(cx - WALL_R - 8, floorY - VORTEX_DEPTH - 24, cz - WALL_R - 8,
                cx + WALL_R + 8, floorY + WALL_ABOVE + 14, cz + WALL_R + 8);
        for (Entity e : level.getEntitiesOfClass(Entity.class, sky, e -> e instanceof StormEyeRuneEntity
                || e instanceof StormEyeGaleEntity || e instanceof StormEyeOrbEntity || e instanceof StormEyeMirrorEntity
                || e instanceof StormEyeAnchorEntity)) {
            e.discard();
        }
        anchors.clear();
        for (Slot s : slots) {
            s.anchored = false;
        }
    }

    /** Take it all down: the floes, the storm, the walls, the column in the hall, everything the fight conjured. */
    public void dissolve() {
        if (dissolved) {
            return;
        }
        dissolved = true;
        for (UUID id : carries.keySet()) {
            HELD.remove(id);
        }
        carries.clear();
        restoreHole();
        for (Slot s : slots) {
            for (int i = 0; i < s.blocks.size(); i++) {
                BlockPos pos = s.blocks.get(i);
                if (level.getBlockState(pos).getBlock() instanceof StormEyeFloeBlock) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                s.shown[i] = (byte) AIR;
            }
            s.state = GONE;
            s.anchored = false;
            StormEyeFloeEntity f = s.motion != null ? floe(s) : null;
            if (f != null) {
                f.discard();                               // the ring floes go with the sky (any stray: their orphan rule)
            }
            s.floe = null;
            s.floeId = null;
        }
        AABB sky = new AABB(cx - WALL_R - 8, floorY - VORTEX_DEPTH - 24, cz - WALL_R - 8,
                cx + WALL_R + 8, floorY + WALL_ABOVE + 14, cz + WALL_R + 8);
        AABB hall = new AABB(BlockPos.containing(hallFloor)).inflate(56.0D, 40.0D, 56.0D);
        for (AABB box : new AABB[]{sky, hall}) {
            for (Entity e : level.getEntitiesOfClass(Entity.class, box, StormEyeArena::isStormEyeThing)) {
                e.discard();
            }
        }
        anchors.clear();
        carried.clear();
        ACTIVE.remove(this);
    }

    /** His chunk went away with him (a save, a shutdown): the arena stops being claimed but stays in the world. */
    public void forget() {
        ACTIVE.remove(this);
    }

    private static boolean isStormEyeThing(Entity e) {
        return e instanceof StormEyeVortexEntity || e instanceof StormEyeWallEntity || e instanceof StormEyeAnchorEntity
                || e instanceof StormEyeRuneEntity || e instanceof StormEyeGaleEntity || e instanceof StormEyeOrbEntity
                || e instanceof StormEyeBoltEntity || e instanceof StormEyeGustEntity || e instanceof StormEyeMirrorEntity
                || e instanceof StormEyeRubbleEntity || e instanceof StormEyeFloeEntity;
    }

    private void sound(Vec3 at, SoundEvent ev, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, ev, SoundSource.HOSTILE, vol, pitch);
    }

    // ================================================================================================ saving
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", cx);
        tag.putInt("Z", cz);
        tag.putInt("Floor", floorY);
        tag.putLong("Home", home.asLong());
        tag.putBoolean("Closing", closing);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> e : falls.entrySet()) {
            CompoundTag f = new CompoundTag();
            f.putUUID("Id", e.getKey());
            f.putInt("N", e.getValue());
            list.add(f);
        }
        tag.put("Falls", list);
        tag.putDouble("HallX", hallFloor.x);
        tag.putDouble("HallY", hallFloor.y);
        tag.putDouble("HallZ", hallFloor.z);
        // THE HOLE IN THE ROOF IS SAVED: a world closed mid-fight still gets its dome back
        ListTag holeTag = new ListTag();
        for (Map.Entry<Long, CompoundTag> e : hole.entrySet()) {
            CompoundTag h = e.getValue().copy();
            h.putLong("P", e.getKey());
            holeTag.add(h);
        }
        tag.put("Hole", holeTag);
        // THE RING FLOES ARE BODIES, NEVER SAVED: which of them stood and at what rest, and the clock they turn by -
        // the load makes them again where the turning has brought them
        tag.putLong("Epoch", epoch);
        int stood = 0;
        int[] rests = new int[slots.length];
        for (Slot s : slots) {
            if (s.motion != null && (s.standable() || s.state == FORMING)) {
                stood |= 1 << s.index;
            }
            rests[s.index] = (int) Math.round(s.restLift / LIFT_STEP);
        }
        tag.putInt("Floes", stood);
        tag.putIntArray("Rests", rests);
        return tag;
    }

    /** An arena saved mid-fight comes back whole: the floes are laid out afresh, the falls remembered. */
    public static StormEyeArena load(ServerLevel level, VelkharEntity king, CompoundTag tag) {
        BlockPos home = BlockPos.of(tag.getLong("Home"));
        Vec3 hall = tag.contains("HallY") ? new Vec3(tag.getDouble("HallX"), tag.getDouble("HallY"), tag.getDouble("HallZ"))
                : new Vec3(tag.getInt("X") + 0.5D, home.getY(), tag.getInt("Z") + 0.5D);
        long epoch = tag.contains("Epoch") ? tag.getLong("Epoch") : level.getGameTime();
        StormEyeArena a = new StormEyeArena(king, level, tag.getInt("X"), tag.getInt("Z"), tag.getInt("Floor"),
                home, hall, epoch);
        if (tag.contains("Floes")) {
            a.savedFloes = tag.getInt("Floes");
        }
        int[] rests = tag.getIntArray("Rests");
        for (Slot s : a.slots) {
            if (s.motion != null && s.index < rests.length) {
                s.restLift = Mth.clamp(rests[s.index] * LIFT_STEP, -LIFT_MAX, LIFT_MAX);
                s.motion.liftFrom = (float) s.restLift;      // there already: no easing in
                s.motion.liftTo = (float) s.restLift;
            }
        }
        for (Tag t : tag.getList("Hole", Tag.TAG_COMPOUND)) {
            CompoundTag h = ((CompoundTag) t).copy();
            long key = h.getLong("P");
            h.remove("P");
            a.hole.put(key, h);
        }
        a.closing = tag.getBoolean("Closing");
        a.rise = a.closing ? 1.0F : 0.0F;
        for (Tag t : tag.getList("Falls", Tag.TAG_COMPOUND)) {
            CompoundTag f = (CompoundTag) t;
            a.falls.put(f.getUUID("Id"), f.getInt("N"));
        }
        a.armed = true;
        a.fromSave = true;
        a.emptyFor = -200;                                  // ten seconds' grace for the players to load back in
        return a;
    }
}
