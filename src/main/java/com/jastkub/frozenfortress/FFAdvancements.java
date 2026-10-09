package com.jastkub.frozenfortress;

import net.minecraft.advancements.Advancement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * A few milestones cannot be expressed as a vanilla trigger - "you broke the
 * Stormcrown" is a block dying on its own clock, with nobody's hand on it at
 * the moment it goes. Those advancements use the impossible trigger and are
 * handed out from here instead.
 */
public final class FFAdvancements {

    /**
     * Awards an advancement of this mod to one player, by criterion name - after the boss's scene, if one holds him
     *.
     */
    public static void grant(ServerPlayer player, String advancement, String criterion) {
        long until = BossCutscenes.heldUntil(player);
        if (until > player.level().getGameTime()) {
            LATER.add(new Later(player.getUUID(), advancement, criterion, until + 10L));
            return;
        }
        award(player, advancement, criterion);
    }

    private static void award(ServerPlayer player, String advancement, String criterion) {
        Advancement entry = player.server.getAdvancements().getAdvancement(FrozenFortress.id(advancement));
        if (entry != null && !player.getAdvancements().getOrStartProgress(entry).isDone()) {
            player.getAdvancements().award(entry, criterion);
        }
    }

    private record Later(java.util.UUID player, String advancement, String criterion, long due) {
    }

    private static final java.util.List<Later> LATER = new java.util.ArrayList<>();

    /** (ServerTickEvent) The ones held back for a scene, once it is over. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (LATER.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        java.util.Iterator<Later> it = LATER.iterator();
        while (it.hasNext()) {
            Later l = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(l.player());
            if (p == null) {
                it.remove();
            } else if (p.level().getGameTime() >= l.due() || now >= l.due()) {
                it.remove();
                award(p, l.advancement(), l.criterion());
            }
        }
    }

    /** Awards it to everyone close enough to have seen it happen. */
    public static void grantNearby(ServerLevel level, BlockPos pos, double radius,
                                   String advancement, String criterion) {
        List<ServerPlayer> witnesses = level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(pos).inflate(radius));
        for (ServerPlayer player : witnesses) {
            grant(player, advancement, criterion);
        }
    }

    private FFAdvancements() {
    }
}
