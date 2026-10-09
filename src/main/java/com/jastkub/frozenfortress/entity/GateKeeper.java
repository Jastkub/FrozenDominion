package com.jastkub.frozenfortress.entity;

/**
 * A miniboss whose room has a gate that drops behind whoever walks in (BossGateBlockEntity): the gate tells it when
 * it is down, so its entrance can wait for that.
 */
public interface GateKeeper {

    /** The gate of its room is down, with someone shut in. */
    void gateShut();

    /** The one it wakes for: the nearest who can be in a fight, near it. */
    @javax.annotation.Nullable
    static net.minecraft.world.entity.player.Player shutInWith(net.minecraft.world.entity.LivingEntity keeper) {
        return keeper.level().getNearestPlayer(keeper.getX(), keeper.getY(), keeper.getZ(), 48.0D,
                e -> e instanceof net.minecraft.world.entity.player.Player p && p.isAlive() && !p.isCreative()
                        && !p.isSpectator());
    }
}
