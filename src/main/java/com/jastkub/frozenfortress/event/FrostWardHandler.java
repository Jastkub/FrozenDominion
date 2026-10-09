package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.registry.FFTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The fortress does not drink.
 *
 * <p>The undercroft is carved out far below the courtyard, and at that depth
 * the surrounding world carries aquifers. Sealing the shell helps, but water
 * that generates <em>inside</em> the walls has nothing to seep through - it is
 * simply already there. So rather than fight worldgen, the masonry itself is
 * made hostile to water: anything touching it boils off.
 *
 * <p>Three nets catch it, in order of how quickly they react:
 * <ol>
 *   <li>a block update next to warded stone - instant, catches water flowing
 *       in from anywhere;</li>
 *   <li>a pass over every chunk as it loads - catches water that generated in
 *       place and is sitting perfectly still, before the player ever sees it;</li>
 *   <li>a slow sweep around players standing in the fortress - the backstop.</li>
 * </ol>
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class FrostWardHandler {

    /** How far from warded stone a fluid still gets dealt with. Keeps the
     *  effect to the fortress instead of draining the lake it stands near. */
    private static final int WARD_REACH = 2;

    /** What lava leaves behind once the cold has had it. Water simply goes;
     *  lava has to become something, or the floor it was sitting in turns
     *  into a hole for the player to fall through. */
    private static final BlockState QUENCHED = Blocks.OBSIDIAN.defaultBlockState();

    /** Chunks waiting for their first dry pass, oldest first. */
    private static final ArrayDeque<PendingChunk> PENDING = new ArrayDeque<>();

    /** Chunks drained per tick. The palette check below makes non-fortress
     *  chunks nearly free, so this only ever does real work near a fortress. */
    private static final int CHUNKS_PER_TICK = 4;

    private static final int SWEEP_INTERVAL = 20;
    private static final int SWEEP_RADIUS = 8;

    private static int sweepTimer;

    private record PendingChunk(ServerLevel level, ChunkPos pos) {
    }

    // ------------------------------------------------------------------
    // 1. Reactive: water that flows in
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        if (!isFluid(event.getState())) {
            return;
        }
        BlockPos pos = event.getPos();
        if (nearWard(level, pos)) {
            evaporate(level, pos, true);
        }
    }

    // ------------------------------------------------------------------
    // 2. On load: water that generated in place
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            PENDING.addLast(new PendingChunk(level, chunk.getPos()));
        }
    }

    @SubscribeEvent
    public static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        for (int i = 0; i < CHUNKS_PER_TICK && !PENDING.isEmpty(); i++) {
            PendingChunk next = PENDING.pollFirst();
            if (next.level() == level) {
                dryChunk(next.level(), next.pos());
            } else if (!next.level().isClientSide) {
                // another dimension's turn will come round; keep it queued
                PENDING.addLast(next);
            }
        }

        if (++sweepTimer >= SWEEP_INTERVAL) {
            sweepTimer = 0;
            for (ServerPlayer player : level.players()) {
                sweepAround(level, player.blockPosition());
            }
        }
    }

    /**
     * Boils off every drop of water in one chunk. The palette check makes this
     * free for the overwhelming majority of chunks: a section that contains no
     * fortress masonry is skipped without reading a single block.
     */
    private static void dryChunk(ServerLevel level, ChunkPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) {
            return;
        }
        LevelChunkSection[] sections = chunk.getSections();
        List<BlockPos> doomed = new ArrayList<>();

        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(FFTags.WARDING))) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (isFluid(section.getBlockState(x, y, z))) {
                            doomed.add(new BlockPos(pos.getMinBlockX() + x, baseY + y, pos.getMinBlockZ() + z));
                        }
                    }
                }
            }
        }

        for (BlockPos wet : doomed) {
            if (nearWard(level, wet)) {
                evaporate(level, wet, false);
            }
        }
    }

    /** The backstop: dries a small bubble around a player standing inside. */
    private static void sweepAround(ServerLevel level, BlockPos centre) {
        if (!nearWard(level, centre, 5)) {
            return;   // not in the fortress; nothing to do
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -SWEEP_RADIUS; dy <= SWEEP_RADIUS; dy++) {
            for (int dz = -SWEEP_RADIUS; dz <= SWEEP_RADIUS; dz++) {
                for (int dx = -SWEEP_RADIUS; dx <= SWEEP_RADIUS; dx++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (isFluid(level.getBlockState(cursor)) && nearWard(level, cursor)) {
                        evaporate(level, cursor.immutable(), true);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // shared
    // ------------------------------------------------------------------

    /** Water or lava - anything the fortress refuses to have standing in it. */
    private static boolean isFluid(BlockState state) {
        FluidState fluid = state.getFluidState();
        return fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA);
    }

    private static boolean nearWard(Level level, BlockPos pos) {
        return nearWard(level, pos, WARD_REACH);
    }

    /** True when warded masonry stands within {@code reach} blocks. */
    private static boolean nearWard(Level level, BlockPos pos, int reach) {
        for (int dy = -reach; dy <= reach; dy++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    if (level.getBlockState(pos.offset(dx, dy, dz)).is(FFTags.WARDING)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Removes the water and, if asked, sells it as steam. Neighbours are
     * deliberately <em>not</em> notified: that stops one puddle from
     * recursing through a whole flooded cellar in a single tick, and it also
     * means the water beside it never learns there is room to flow into.
     */
    private static void evaporate(Level level, BlockPos pos, boolean showEffect) {
        BlockState state = level.getBlockState(pos);
        BlockState dried;

        if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED)) {
            dried = state.setValue(BlockStateProperties.WATERLOGGED, false);
        } else if (!state.getCollisionShape(level, pos).isEmpty()) {
            return;   // a solid block that merely happens to be damp
        } else if (state.getFluidState().is(FluidTags.LAVA)) {
            dried = QUENCHED;
        } else if (state.getFluidState().is(FluidTags.WATER)) {
            dried = Blocks.AIR.defaultBlockState();
        } else {
            return;
        }

        level.setBlock(pos, dried, Block.UPDATE_CLIENTS);

        if (showEffect && level instanceof ServerLevel server && level.random.nextInt(6) == 0) {
            server.sendParticles(ParticleTypes.CLOUD,
                    pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D,
                    3, 0.25D, 0.2D, 0.25D, 0.01D);
            if (level.random.nextInt(4) == 0) {
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25F,
                        1.6F + level.random.nextFloat() * 0.4F);
            }
        }
    }

    /** Dropped worlds must not keep a level alive through the queue. */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (PENDING.size() > 4096) {
            PENDING.clear();
        }
    }

    private FrostWardHandler() {
    }
}
