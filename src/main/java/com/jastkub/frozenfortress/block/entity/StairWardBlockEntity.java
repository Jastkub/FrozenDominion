package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.entity.DrownedLadyEntity;
import com.jastkub.frozenfortress.entity.StairDebrisEntity;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * THE STAIR THAT GOES DOWN INTO THE WATER: the Frozen Cisterns' stair from the walkway down to the ice. Hidden in the floor under it, this block
 * watches the fight. When its keeper (the Drowned Lady) is awake and someone stands down on her ice, the ice under
 * the stair cracks, opens, and the stair goes down into the black water a step at a time, from its foot to its head -
 * every block of it torn out and falling (StairDebrisEntity), shattering on the ice or in the water. The hole skins
 * over again. Nobody climbs back up to the walkway to fight her from above.
 *
 * <p>When the fight is over - she is dead, or nobody is left in the room (a death, a flight) - the water freezes
 * back into the stair as it stood, from its foot up: the walkway's hidden room (its rune) is reached from up there,
 * and whoever comes back needs the way down again. A block is not put back where somebody stands; it waits.
 */
public class StairWardBlockEntity extends BlockEntity {

    public static final int STANDING = 0, FALLING = 1, GONE = 2, RISING = 3;
    /** The crack before it goes; a row of the stair every few ticks after; the hole open a while. */
    private static final int CRACK_TICKS = 16, ROW_TICKS = 4, SKIN_AFTER = 60;

    /** The stair, from this block: x0 y0 z0 x1 y1 z1, inclusive. */
    private int[] stair = {0, 0, 0, 0, 0, 0};
    /** The room it keeps, from this block: x0 y0 z0 x1 y1 z1, the far corner exclusive. */
    private int[] room = {0, 0, 0, 0, 0, 0};
    /** The ice's face, from this block (the feet of whoever stands on it). */
    private int floorY;
    private String keeper = "";
    private int state = STANDING;
    private int clock;
    private boolean holeOpen;
    /** The stair as it stood, kept from the moment it went: rows from its foot to its head. */
    private final List<List<Saved>> rows = new ArrayList<>();
    /** The ice under it, as it was (where the hole opens). */
    private final List<Saved> under = new ArrayList<>();

    private record Saved(BlockPos rel, BlockState state) {
    }

    public StairWardBlockEntity(BlockPos pos, BlockState blockState) {
        super(FFBlockEntities.STAIR_WARD.get(), pos, blockState);
    }

    private AABB roomBox() {
        return new AABB(worldPosition.offset(room[0], room[1], room[2]), worldPosition.offset(room[3], room[4], room[5]));
    }

    private static boolean counts(Player p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator();
    }

    /** 0: no keeper alive; 1: alive, asleep; 2: alive and awake. */
    private int keeperState(ServerLevel s) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(keeper));
        if (type == null) {
            return 0;
        }
        int best = 0;
        for (Entity e : s.getEntities((Entity) null, roomBox().inflate(3.0D), e -> e.getType() == type)) {
            if (e instanceof LivingEntity l && l.isAlive()) {
                best = Math.max(best, e instanceof DrownedLadyEntity d && d.isDormant() ? 1 : 2);
            }
        }
        return best;
    }

    /** Someone down on the ice (not on the stair, not up on the walkway)? */
    private boolean someoneOnIce(ServerLevel s) {
        AABB r = roomBox();
        double face = worldPosition.getY() + floorY;
        for (Player p : s.players()) {
            if (counts(p) && r.contains(p.position()) && p.getY() > face - 1.3D && p.getY() < face + 0.45D) {
                return true;
            }
        }
        return false;
    }

    private boolean anyoneInside(ServerLevel s) {
        AABB r = roomBox().inflate(1.0D);
        for (Player p : s.players()) {
            if (counts(p) && r.contains(p.position())) {
                return true;
            }
        }
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState bs, StairWardBlockEntity w) {
        if (!(level instanceof ServerLevel s)) {
            return;
        }
        w.clock++;
        switch (w.state) {
            case STANDING -> {
                if (w.clock % 5 == 0 && w.keeperState(s) == 2 && w.someoneOnIce(s)) {
                    w.startFall(s);
                }
            }
            case FALLING -> w.falling(s);
            case GONE -> {
                if (w.holeOpen && w.clock >= SKIN_AFTER && w.clock % 5 == 0) {
                    w.skinOver(s);
                }
                if (w.clock % 10 == 0 && (w.keeperState(s) == 0 || !w.anyoneInside(s))) {
                    w.set(RISING);
                }
            }
            case RISING -> w.rising(s);
            default -> w.set(STANDING);
        }
    }

    // ------------------------------------------------------------------------------------------------ going down
    private void startFall(ServerLevel s) {
        rows.clear();
        under.clear();
        BlockPos a = worldPosition.offset(stair[0], stair[1], stair[2]);
        BlockPos b = worldPosition.offset(stair[3], stair[4], stair[5]);
        // the stair runs along its longer side; a row is one step across it, all the way down
        boolean alongZ = Math.abs(stair[5] - stair[2]) >= Math.abs(stair[3] - stair[0]);
        Map<Integer, List<Saved>> byRow = new TreeMap<>();
        for (BlockPos p : BlockPos.betweenClosed(a, b)) {
            BlockState st = s.getBlockState(p);
            // a column that goes on up over the stair is no part of it: a pier the stair leans on stays
            BlockState over = s.getBlockState(new BlockPos(p.getX(), b.getY() + 1, p.getZ()));
            if (!st.isAir() && (over.isAir() || over.canBeReplaced())) {
                byRow.computeIfAbsent(alongZ ? p.getZ() : p.getX(), k -> new ArrayList<>())
                        .add(new Saved(p.immutable().subtract(worldPosition), st));
            }
        }
        // its foot first: the row whose top is lowest
        List<List<Saved>> ordered = new ArrayList<>(byRow.values());
        ordered.sort(Comparator.comparingInt(r -> r.stream().mapToInt(x -> x.rel().getY()).max().orElse(0)));
        rows.addAll(ordered);
        // and the ice under it, where the hole opens - under the stair's own columns only (not under a pier)
        java.util.Set<Long> cols = new java.util.HashSet<>();
        for (List<Saved> row : rows) {
            for (Saved x : row) {
                cols.add(BlockPos.asLong(x.rel().getX(), 0, x.rel().getZ()));
            }
        }
        int fy = floorY - 1;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(stair[0], fy, stair[2]),
                worldPosition.offset(stair[3], fy, stair[5]))) {
            BlockPos rel = p.immutable().subtract(worldPosition);
            if (cols.contains(BlockPos.asLong(rel.getX(), 0, rel.getZ()))) {
                under.add(new Saved(rel, s.getBlockState(p)));
            }
        }
        Vec3 foot = footCentre();
        s.playSound(null, foot.x, foot.y, foot.z, FFSounds.DROWNED_LADY_ICE_CRACK.get(), SoundSource.BLOCKS, 2.4F, 0.7F);
        set(FALLING);
    }

    private Vec3 footCentre() {
        return new Vec3(worldPosition.getX() + (stair[0] + stair[3]) / 2.0D + 0.5D, worldPosition.getY() + floorY,
                worldPosition.getZ() + (stair[2] + stair[5]) / 2.0D + 0.5D);
    }

    private void falling(ServerLevel s) {
        Vec3 c = footCentre();
        if (clock % 4 == 0 && clock < CRACK_TICKS) {                // the ice under it splitting
            s.sendParticles(FFParticles.ICE_SHARD.get(), c.x, c.y + 0.1D, c.z, 10, 1.6D, 0.05D, 2.6D, 0.08D);
        }
        if (clock == CRACK_TICKS) {                                  // and open: black water
            for (Saved u : under) {
                BlockPos p = worldPosition.offset(u.rel());
                if (s.getBlockState(p).is(u.state().getBlock())) {
                    s.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
                }
            }
            holeOpen = true;
            s.playSound(null, c.x, c.y, c.z, FFSounds.DROWNED_LADY_ICE_BREAK.get(), SoundSource.BLOCKS, 2.4F, 0.8F);
            s.playSound(null, c.x, c.y, c.z, FFSounds.DROWNED_LADY_SPLASH.get(), SoundSource.BLOCKS, 2.0F, 0.7F);
        }
        int k = (clock - CRACK_TICKS) / ROW_TICKS;
        if (clock >= CRACK_TICKS && (clock - CRACK_TICKS) % ROW_TICKS == 0 && k < rows.size()) {
            // a step torn out: every block of it falls, drawn a little out toward the room
            Vec3 out = outward();
            for (Saved b : rows.get(k)) {
                BlockPos p = worldPosition.offset(b.rel());
                BlockState now = s.getBlockState(p);
                if (now.isAir()) {
                    continue;
                }
                s.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                StairDebrisEntity.fall(s, p, now, out.scale(0.04D + s.random.nextDouble() * 0.05D)
                        .add(0.0D, -0.05D, 0.0D));
            }
            BlockPos at = worldPosition.offset(rows.get(k).get(0).rel());
            s.playSound(null, at, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 2.0F, 0.6F);
            s.playSound(null, at, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.9F, 0.4F);
        }
        if (k >= rows.size() && clock >= CRACK_TICKS) {
            set(GONE);
        }
    }

    /** From the stair's head toward its foot, flat: where the steps tumble. */
    private Vec3 outward() {
        if (rows.size() < 2) {
            return Vec3.ZERO;
        }
        BlockPos foot = rows.get(0).get(0).rel(), head = rows.get(rows.size() - 1).get(0).rel();
        Vec3 d = new Vec3(foot.getX() - head.getX(), 0.0D, foot.getZ() - head.getZ());
        return d.lengthSqr() < 1.0E-4D ? Vec3.ZERO : d.normalize();
    }

    /** The hole skins over: its ice back, block by block - none where somebody is in the water. */
    private void skinOver(ServerLevel s) {
        boolean all = true;
        for (Saved u : under) {
            BlockPos p = worldPosition.offset(u.rel());
            BlockState now = s.getBlockState(p);
            if (now.is(u.state().getBlock()) || !(now.isAir() || now.is(Blocks.WATER))) {
                continue;                                            // its ice, or something else there now
            }
            if (occupied(s, p)) {
                all = false;
                continue;
            }
            s.setBlock(p, u.state(), 3);
        }
        if (all) {
            holeOpen = false;
            Vec3 c = footCentre();
            s.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.6F, 0.6F);
        }
    }

    private static boolean occupied(ServerLevel s, BlockPos p) {
        return !s.getEntitiesOfClass(Entity.class, new AABB(p), e -> !(e instanceof StairDebrisEntity) && e.isAlive()
                && !e.isSpectator()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------ coming back
    private void rising(ServerLevel s) {
        if (holeOpen) {                                              // the stair waits for its floor
            if (clock % 5 == 0) {
                skinOver(s);
            }
            return;
        }
        if (clock % ROW_TICKS != 0) {
            return;
        }
        // the lowest row not yet whole, frozen back; what somebody stands in waits for the next round
        for (List<Saved> row : rows) {
            boolean placed = false, waiting = false;
            for (Saved b : row) {
                BlockPos p = worldPosition.offset(b.rel());
                BlockState now = s.getBlockState(p);
                if (now == b.state()) {
                    continue;
                }
                if (!(now.isAir() || now.is(Blocks.WATER) || now.canBeReplaced()) || occupied(s, p)) {
                    waiting = true;
                    continue;
                }
                s.setBlock(p, b.state(), 3);
                placed = true;
            }
            if (placed) {
                BlockPos at = worldPosition.offset(row.get(0).rel());
                s.playSound(null, at, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.4F, 0.5F + s.random.nextFloat() * 0.2F);
                s.playSound(null, at, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
                Vec3 m = Vec3.atCenterOf(at);
                s.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, m.x, m.y, m.z, 8, 1.6D, 0.4D, 0.4D, 0.02D);
                return;
            }
            if (waiting) {
                return;
            }
        }
        rows.clear();
        under.clear();
        set(STANDING);
    }

    private void set(int st) {
        state = st;
        clock = 0;
        setChanged();
    }

    // ------------------------------------------------------------------------------------------------ saved
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Stair")) {
            stair = tag.getIntArray("Stair");
        }
        if (tag.contains("Room")) {
            room = tag.getIntArray("Room");
        }
        floorY = tag.getInt("FloorY");
        keeper = tag.getString("Keeper");
        state = tag.getInt("State");
        holeOpen = tag.getBoolean("HoleOpen");
        rows.clear();
        for (Tag r : tag.getList("Rows", Tag.TAG_LIST)) {
            List<Saved> row = new ArrayList<>();
            for (Tag e : (ListTag) r) {
                row.add(read((CompoundTag) e));
            }
            rows.add(row);
        }
        under.clear();
        for (Tag e : tag.getList("Under", Tag.TAG_COMPOUND)) {
            under.add(read((CompoundTag) e));
        }
        if (state != STANDING && rows.isEmpty()) {
            state = STANDING;                                        // nothing kept to put back: as it is
        }
    }

    private static Saved read(CompoundTag t) {
        return new Saved(BlockPos.of(t.getLong("P")), NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),
                t.getCompound("S")));
    }

    private static CompoundTag write(Saved s) {
        CompoundTag t = new CompoundTag();
        t.putLong("P", s.rel().asLong());
        t.put("S", NbtUtils.writeBlockState(s.state()));
        return t;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putIntArray("Stair", stair);
        tag.putIntArray("Room", room);
        tag.putInt("FloorY", floorY);
        tag.putString("Keeper", keeper);
        tag.putInt("State", state);
        tag.putBoolean("HoleOpen", holeOpen);
        ListTag rs = new ListTag();
        for (List<Saved> row : rows) {
            ListTag l = new ListTag();
            for (Saved b : row) {
                l.add(write(b));
            }
            rs.add(l);
        }
        tag.put("Rows", rs);
        ListTag us = new ListTag();
        for (Saved u : under) {
            us.add(write(u));
        }
        tag.put("Under", us);
    }
}
