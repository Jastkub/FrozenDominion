package com.jastkub.frozenfortress.block.entity;

import com.jastkub.frozenfortress.block.CitadelLockBlock;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFBlocks;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CitadelLockBlockEntity extends BlockEntity {

    /** How far a gate may reach from its lock, and how large one may be. */
    private static final int REACH = 24;
    private static final int MAX_GATE = 1200;
    /** Ticks between two courses of the gate going away - a secret's masonry slower, so it is seen to give. */
    private static final int COURSE_TICKS = 3, SECRET_COURSE_TICKS = 6;
    /** A SECRET OPENING, SEEN: runes of frost-light run from the
     *  worked catch to the masonry it moves, one every TRAIL_TICKS, TRAIL_STEP apart (tools/gen_secret_fx.py:
     *  fx_secret_trail) - the masonry starts to give as the last of them reaches it, each block of it splitting and
     *  sinking (fx_secret_crumble). No camera of its own (12.10.2026: the first secret's catch stands beside its masonry
     *  - BY_THE_GATE in tools/citadel/citadel6.py - so the wall going is in front of whoever worked it). */
    private static final int TRAIL_TICKS = 1;
    private static final double TRAIL_STEP = 0.7D;

    private final List<ResourceLocation> keys = new ArrayList<>();
    private final Set<ResourceLocation> inserted = new LinkedHashSet<>();
    private boolean opened;
    /** For a one-way shortcut: the side the mechanism can be worked from. */
    private Direction side;
    /** Optional: the box (from this block) the gate stands in, for a switch that does not touch it. */
    private int[] gateBox;
    /** The gate still standing while it opens, lowest course first. */
    private final List<BlockPos> pending = new ArrayList<>();
    private int courseClock;
    /** The secret's runes still to run to its masonry (not saved: a reload finishes the opening without them). */
    private final List<net.minecraft.world.phys.Vec3> trail = new ArrayList<>();
    private boolean secret;

    public CitadelLockBlockEntity(BlockPos pos, BlockState state) {
        super(FFBlockEntities.CITADEL_LOCK.get(), pos, state);
    }

    public void tryOpen(Player player, ItemStack held) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (opened) {
            // a gate the chunk unloaded under, half gone: finish it
            if (pending.isEmpty()) {
                collectGate();
            }
            return;
        }
        if (side != null) {
            double along = (player.getX() - (worldPosition.getX() + 0.5D)) * side.getStepX()
                    + (player.getZ() - (worldPosition.getZ() + 0.5D)) * side.getStepZ();
            // beside the post counts as this side; behind the gate does not
            if (along < -0.5D) {
                player.displayClientMessage(Component.translatable("block.frozen_dominion.citadel_lock.other_side"), true);
                return;
            }
        }
        if (keys.isEmpty()) {
            open(serverLevel);
            return;
        }
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        if (id != null && keys.contains(id) && !inserted.contains(id)) {
            inserted.add(id);
            setChanged();
            serverLevel.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.4F, 0.6F);
            serverLevel.sendParticles(FFParticles.SOUL_FROST.get(),
                    worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D,
                    16, 0.3D, 0.3D, 0.3D, 0.04D);
            if (inserted.containsAll(keys)) {
                open(serverLevel);
            } else {
                player.displayClientMessage(Component.translatable("block.frozen_dominion.citadel_lock.fits",
                        inserted.size(), keys.size()), true);
            }
            return;
        }
        MutableComponent missing = Component.empty();
        boolean first = true;
        for (ResourceLocation key : keys) {
            if (inserted.contains(key)) {
                continue;
            }
            Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key);
            if (!first) {
                missing.append(", ");
            }
            missing.append(item != null ? item.getDescription() : Component.literal(key.toString()));
            first = false;
        }
        player.displayClientMessage(Component.translatable("block.frozen_dominion.citadel_lock.locked", missing), true);
    }

    private void open(ServerLevel serverLevel) {
        opened = true;
        setChanged();
        serverLevel.setBlock(worldPosition, getBlockState().setValue(CitadelLockBlock.OPEN, true), 3);
        serverLevel.playSound(null, worldPosition, FFSounds.VELKHAR_SWORD_PULL.get(), SoundSource.BLOCKS, 2.6F, 0.5F);
        serverLevel.playSound(null, worldPosition, FFSounds.ICE_GRIND.get(), SoundSource.BLOCKS, 2.0F, 0.6F);
        collectGate();
        secret = pending.stream().anyMatch(p -> {
            BlockState st = level.getBlockState(p);
            return st.is(FFBlocks.SECRET_STONE_BRICKS.get()) || st.is(FFBlocks.SECRET_DEEPSLATE_BRICKS.get());
        });
        if (!secret || pending.isEmpty()) {
            return;
        }
        // the runes' way: from the catch to the middle of the masonry's face on the catch's side
        net.minecraft.world.phys.Vec3 from = net.minecraft.world.phys.Vec3.atCenterOf(worldPosition);
        net.minecraft.world.phys.Vec3 to = nearestFace(from);
        trail.clear();
        double len = from.distanceTo(to);
        int n = Math.max(2, (int) Math.ceil(len / TRAIL_STEP));
        for (int i = 1; i <= n; i++) {
            trail.add(from.lerp(to, i / (double) n));
        }
        courseClock = -trail.size() * TRAIL_TICKS - 4;          // the masonry waits for its runes
    }

    /** The middle of the waiting masonry, half a block out of it on the side `from` is on (along the larger of x, z). */
    private net.minecraft.world.phys.Vec3 nearestFace(net.minecraft.world.phys.Vec3 from) {
        double sx = 0, sy = 0, sz = 0;
        for (BlockPos p : pending) {
            sx += p.getX() + 0.5D;
            sy += p.getY() + 0.5D;
            sz += p.getZ() + 0.5D;
        }
        net.minecraft.world.phys.Vec3 mid = new net.minecraft.world.phys.Vec3(sx / pending.size(), sy / pending.size(),
                sz / pending.size());
        net.minecraft.world.phys.Vec3 toward = from.subtract(mid);
        return Math.abs(toward.x) >= Math.abs(toward.z)
                ? mid.add(Math.signum(toward.x) * 0.6D, 0.0D, 0.0D) : mid.add(0.0D, 0.0D, Math.signum(toward.z) * 0.6D);
    }

    /** Every gate block joined to this lock face to face, lowest first. */
    private static boolean isGate(BlockState s) {
        return s.is(FFBlocks.CITADEL_GATE.get()) || s.is(FFBlocks.SECRET_STONE_BRICKS.get())
                || s.is(FFBlocks.SECRET_DEEPSLATE_BRICKS.get());
    }

    private void collectGate() {
        pending.clear();
        if (gateBox != null && gateBox.length == 6) {
            BlockPos a = worldPosition.offset(gateBox[0], gateBox[1], gateBox[2]);
            BlockPos b = worldPosition.offset(gateBox[3], gateBox[4], gateBox[5]);
            for (BlockPos p : BlockPos.betweenClosed(a, b)) {
                if (isGate(level.getBlockState(p))) {
                    pending.add(p.immutable());
                }
            }
            pending.sort(Comparator.comparingInt(BlockPos::getY));
            courseClock = 0;
            return;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction d : Direction.values()) {
            queue.add(worldPosition.relative(d));
        }
        while (!queue.isEmpty() && pending.size() < MAX_GATE) {
            BlockPos p = queue.poll();
            if (!seen.add(p) || p.distManhattan(worldPosition) > REACH * 2) {
                continue;
            }
            if (!isGate(level.getBlockState(p))) {
                continue;
            }
            pending.add(p.immutable());
            for (Direction d : Direction.values()) {
                queue.add(p.relative(d));
            }
        }
        pending.sort(Comparator.comparingInt(BlockPos::getY));
        courseClock = 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CitadelLockBlockEntity lock) {
        if (lock.pending.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!lock.trail.isEmpty() && Math.floorMod(lock.courseClock, TRAIL_TICKS) == 0) {
            net.minecraft.world.phys.Vec3 at = lock.trail.remove(0);
            com.jastkub.frozenfortress.entity.AttackFxEntity.spawn(serverLevel, "secret_trail", at,
                    serverLevel.random.nextFloat() * 360.0F, 1.0F, 28, null);
            if (lock.trail.size() % 3 == 0) {
                serverLevel.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                        0.7F, 1.2F + 0.4F * serverLevel.random.nextFloat());
            }
        }
        if (++lock.courseClock < (lock.secret ? SECRET_COURSE_TICKS : COURSE_TICKS)) {
            return;
        }
        lock.courseClock = 0;
        // one course of the gate goes - the lowest still standing
        int y = lock.pending.get(0).getY();
        BlockPos last = null;
        while (!lock.pending.isEmpty() && lock.pending.get(0).getY() == y) {
            BlockPos p = lock.pending.remove(0);
            if (isGate(level.getBlockState(p))) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                if (lock.secret) {
                    // the block splitting along a seam of light, its chunks sinking into a breath of frost
                    com.jastkub.frozenfortress.entity.AttackFxEntity.spawn(serverLevel, "secret_crumble",
                            net.minecraft.world.phys.Vec3.atBottomCenterOf(p), 90.0F * serverLevel.random.nextInt(4),
                            1.0F, 26, null);
                } else {
                    serverLevel.sendParticles(FFParticles.ICE_SHARD.get(),
                            p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D, 3, 0.3D, 0.3D, 0.3D, 0.05D);
                }
                last = p;
            }
        }
        serverLevel.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0F, 0.5F);
        if (lock.secret && last != null) {
            serverLevel.playSound(null, last, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 1.6F, 0.6F);
        }
        if (lock.pending.isEmpty()) {
            serverLevel.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2.0F, 0.5F);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        keys.clear();
        inserted.clear();
        for (Tag t : tag.getList("Keys", Tag.TAG_STRING)) {
            ResourceLocation rl = ResourceLocation.tryParse(t.getAsString());
            if (rl != null) {
                keys.add(rl);
            }
        }
        for (Tag t : tag.getList("Inserted", Tag.TAG_STRING)) {
            ResourceLocation rl = ResourceLocation.tryParse(t.getAsString());
            if (rl != null) {
                inserted.add(rl);
            }
        }
        opened = tag.getBoolean("Opened");
        side = tag.contains("Side") ? Direction.byName(tag.getString("Side")) : null;
        gateBox = tag.contains("GateBox") ? tag.getIntArray("GateBox") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag k = new ListTag();
        for (ResourceLocation rl : keys) {
            k.add(StringTag.valueOf(rl.toString()));
        }
        tag.put("Keys", k);
        ListTag in = new ListTag();
        for (ResourceLocation rl : inserted) {
            in.add(StringTag.valueOf(rl.toString()));
        }
        tag.put("Inserted", in);
        tag.putBoolean("Opened", opened);
        if (side != null) {
            tag.putString("Side", side.getName());
        }
        if (gateBox != null) {
            tag.putIntArray("GateBox", gateBox);
        }
    }
}
