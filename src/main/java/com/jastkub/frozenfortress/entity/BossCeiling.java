package com.jastkub.frozenfortress.entity;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * THE MINIBOSSES' CEILING: the most one blow, and one second, may take off a miniboss - so that no weapon simply
 * outruns its fight - lifted whole while its mechanic has it open (stunned, spent, on its knees, its core bare). Done
 * the Monstrosity's way, on the damage that actually lands (getDamageAfterMagicAbsorb, after armour), and grown with
 * a party as its health is (PartyScaling).
 */
public final class BossCeiling {
    public static final float HIT = 16.0F, SECOND = 24.0F;

    /** The ceilings as configured (FFConfig "hardness"; the defaults above when it is not loaded yet). */
    private static float hit() {
        return com.jastkub.frozenfortress.config.FFConfig.loaded
                ? com.jastkub.frozenfortress.config.FFConfig.COMMON.bossHitCap.get().floatValue() : HIT;
    }

    private static float second() {
        return com.jastkub.frozenfortress.config.FFConfig.loaded
                ? com.jastkub.frozenfortress.config.FFConfig.COMMON.bossSecondCap.get().floatValue() : SECOND;
    }
    private static final int WINDOW = 20;

    private long at;
    private float spent;

    /** What of {@code landed} comes off it; {@code open}: its window, no ceiling at all. */
    public float apply(LivingEntity boss, DamageSource source, float landed, boolean open) {
        if (boss.level().isClientSide || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return landed;
        }
        long now = boss.level().getGameTime();
        if (now - at >= WINDOW) {
            at = now;
            spent = 0.0F;
        }
        if (!open) {
            float party = com.jastkub.frozenfortress.event.PartyScaling.healthFactor(boss);
            landed = Math.min(Math.min(landed, hit() * party), Math.max(0.0F, second() * party - spent));
        }
        spent += landed;
        return landed;
    }
}
