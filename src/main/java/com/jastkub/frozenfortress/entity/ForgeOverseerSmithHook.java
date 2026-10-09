package com.jastkub.frozenfortress.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.Event;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * THE SMITH'S FREEDOM - the hook a future smith NPC hangs on. When the Forge Overseer dies, {@link #free} does three things:
 * <ol>
 *   <li>remembers it for good in the level's saved data ({@code data/frozen_dominion_forge_smith.dat}): the home block of
 *   every overseer that has fallen - so a smith that loads later, or a chunk reloaded, still knows
 *   ({@link #isSmithFreed(ServerLevel, BlockPos, int)}, {@link #anyFreed(ServerLevel)}, {@link #freedForges});</li>
 *   <li>posts {@link SmithFreedEvent} on {@code NeoForge.EVENT_BUS} (server thread), with the level, the forge's
 *   home block, the overseer and whoever killed him (null if no player) - a smith entity in its cell can listen for it
 *   and walk out;</li>
 *   <li>tells everyone in the hall, on the action bar ({@code entity.frozen_dominion.forge_overseer.smith_freed}).</li>
 * </ol>
 * The citadel's Forge (x 140-197, z 64-97, floor 60) has the smith's cell off its north side (x 144-160, z 46-62).
 */
public final class ForgeOverseerSmithHook {

    private static final String DATA = "frozen_dominion_forge_smith";

    private ForgeOverseerSmithHook() {
    }

    /** Posted when a Forge Overseer dies: the smith he kept is free. Not cancellable. */
    public static class SmithFreedEvent extends Event {
        private final ServerLevel level;
        private final BlockPos forge;
        private final ForgeOverseerEntity overseer;
        @Nullable
        private final Player liberator;

        public SmithFreedEvent(ServerLevel level, BlockPos forge, ForgeOverseerEntity overseer, @Nullable Player liberator) {
            this.level = level;
            this.forge = forge;
            this.overseer = overseer;
            this.liberator = liberator;
        }

        public ServerLevel getLevel() {
            return level;
        }

        /** The overseer's home: the block he stood his post on, in the middle of the forge hall. */
        public BlockPos getForge() {
            return forge;
        }

        public ForgeOverseerEntity getOverseer() {
            return overseer;
        }

        @Nullable
        public Player getLiberator() {
            return liberator;
        }
    }

    /** The overseer of the forge at `home` is dead: remember it, tell the smith's listeners, tell the players. */
    public static void free(ServerLevel level, BlockPos home, ForgeOverseerEntity overseer, @Nullable Player by) {
        data(level).add(home);
        NeoForge.EVENT_BUS.post(new SmithFreedEvent(level, home.immutable(), overseer, by));
        for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(overseer) < 48.0D * 48.0D)) {
            p.displayClientMessage(Component.translatable("entity.frozen_dominion.forge_overseer.smith_freed"), true);
        }
    }

    /** Has the overseer of a forge within `radius` blocks of `near` fallen? (for a smith in his cell) */
    public static boolean isSmithFreed(ServerLevel level, BlockPos near, int radius) {
        for (BlockPos p : data(level).homes) {
            if (p.distManhattan(near) <= radius * 3 && p.closerThan(near, radius)) {
                return true;
            }
        }
        return false;
    }

    /** Has any Forge Overseer in this level fallen? */
    public static boolean anyFreed(ServerLevel level) {
        return !data(level).homes.isEmpty();
    }

    /** Every forge whose overseer has fallen (their home blocks). */
    public static List<BlockPos> freedForges(ServerLevel level) {
        return Collections.unmodifiableList(data(level).homes);
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), DATA);
    }

    /** The level's memory of fallen overseers. */
    static final class Data extends SavedData {
        final List<BlockPos> homes = new ArrayList<>();

        static Data load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            Data d = new Data();
            for (long l : tag.getLongArray("Freed")) {
                d.homes.add(BlockPos.of(l));
            }
            return d;
        }

        void add(BlockPos p) {
            if (!homes.contains(p)) {
                homes.add(p.immutable());
                setDirty();
            }
        }

        @Override
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            long[] out = new long[homes.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = homes.get(i).asLong();
            }
            tag.putLongArray("Freed", out);
            return tag;
        }
    }
}
