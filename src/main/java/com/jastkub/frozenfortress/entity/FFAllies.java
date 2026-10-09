package com.jastkub.frozenfortress.entity;

import net.minecraft.world.entity.Entity;

/**
 * WHO IS ON THE KING'S SIDE.
 *
 * <p>Velkhar's own melee already spared his colossus, his copies and his
 * servants - the predicate in {@code nearbyVictims} names all three. Every one
 * of his ELEVEN projectiles and area effects had its own list, written at a
 * different time, and between them they named a different subset each: the
 * boulder spared copies and servants, the spear spared copies and servants and
 * him, the doom blade spared him alone, the tornado spared nobody. So the
 * colossus he summons - the one thing on the floor he actually owns - was
 * being shot, crushed, frozen and dropped on by its own summoner, because it
 * was added to the fight long after most of those lists were written.
 *
 * <p>Eleven separate edits would fix today's eleven. The twelfth projectile
 * would not inherit any of them, which is exactly how this happened the first
 * time. So there is one predicate, and everything he throws asks it.
 *
 * <p>It is deliberately about SIDES rather than about types. "Is the victim
 * one of mine" is the question every one of those lists was trying to ask, and
 * asking it directly means a new ally is added here once rather than in every
 * attack that might catch it.
 */
public final class FFAllies {

    private FFAllies() {
    }

    /** True for anything that fights on Velkhar's side. */
    public static boolean ofTheKing(Entity e) {
        return e instanceof com.jastkub.frozenfortress.entity.boss.VelkharEntity
                || e instanceof com.jastkub.frozenfortress.entity.boss.VelkharCloneEntity
                || (e instanceof HollowGolemEntity g && !g.isTamed())
                || e instanceof FrostServantEntity;
    }

    /**
     * Should this attack pass over this victim?
     *
     * <p>Only when the attacker is his: a projectile with no owner, or one
     * thrown by a player at the colossus, is nobody's friendly fire and must
     * still land. That is the difference between "the king does not hit his
     * own" and "the colossus is invulnerable", and only the first is wanted.
     */
    public static boolean spares(Entity owner, Entity victim) {
        if (owner instanceof HollowGolemEntity g && g.isTamed()) {
            return g.mySide(victim);                     // a tamed Monstrosity's: its owner's side
        }
        return owner != null && ofTheKing(owner) && ofTheKing(victim);
    }
}
