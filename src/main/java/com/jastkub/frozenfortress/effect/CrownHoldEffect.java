package com.jastkub.frozenfortress.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * THE CROWN'S HOLD ON THE STONE: while the Stormcrown burns, whoever is in its reach cannot break a block of the
 * citadel (CommonEvents: the break speed is nought, the break refused). It was Mining Fatigue, which also slowed every
 * swing of a weapon - this does nothing else. Laid on hidden (no particles, no icon), renewed by the
 * crown's pulse, taken off when the crown is taken; an effect so the client knows of it too and draws no cracks.
 */
public class CrownHoldEffect extends MobEffect {

    public CrownHoldEffect() {
        super(MobEffectCategory.NEUTRAL, 0x6A86B8);
    }
}
