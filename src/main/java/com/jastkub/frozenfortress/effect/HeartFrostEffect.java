package com.jastkub.frozenfortress.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * HEART'S FROST (Odmrozenie) - what a trap room's beating heart lays
 * on whoever is in its room and away from a burning hearth (FrostHeartBlockEntity). Its frost is the heart's own,
 * apart from the game's freezing and the creatures' frostbite (07.10.2026; the rime over the screen, ColdOverlay's
 * mist and heartbeat follow it - HeartColdPacket); this is its name and its
 * icon in the effect list: level I while it creeps, level II once frozen through and it bites - a bite that doubles
 * every two and a half seconds (FrostHeartBlockEntity.bite) - and level III once that bite kills. It lapses two
 * seconds after you reach a fire.
 *
 * <p>While it is on, a Potion of Warmth does not thaw you (WarmthEffect): only a hearth answers this cold.
 */
public class HeartFrostEffect extends MobEffect {

    public HeartFrostEffect() {
        super(MobEffectCategory.HARMFUL, 0x7FD0FF);
    }
}
