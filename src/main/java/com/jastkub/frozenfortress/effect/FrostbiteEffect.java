package com.jastkub.frozenfortress.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Frostbite (Ukaszenie Mrozu): what the creatures' and Velkhar's frost leaves in you - it slows the victim, bites
 * for periodic damage, thins their armour and STOPS THEM MENDING. Nothing to do with a trap room's heart's cold
 * (Odmrozenie, FrostHeartBlockEntity).
 *
 * <p>It used to be a slow and a small tick of damage, which is a debuff you
 * ignore: neither half changes what you do, and the damage is smaller than
 * what regeneration was already putting back. So it was a coloured icon.
 *
 * <p>Two additions turn it into a clock. The armour thins - a little, and by
 * a flat amount so it bites hardest on the lightly armoured - and while it is
 * on you, YOU DO NOT HEAL. Not a reduction: healing is refused outright, which
 * is the only version that actually changes behaviour, because a fight where
 * you can still top up at half rate is a fight you play the same way and win
 * more slowly. This one says leave, or finish it now.
 *
 * <p>That also makes the tick damage matter for the first time. One and a half
 * hearts over a few seconds is nothing next to a golden apple; the same damage
 * with no way to answer it is a countdown.
 */
public class FrostbiteEffect extends MobEffect {

    public FrostbiteEffect() {
        super(MobEffectCategory.HARMFUL, 0x9FE8FF);
        addAttributeModifier(Attributes.MOVEMENT_SPEED,
                "e1b7c9a2-4f3d-4a6e-9c2b-8f5d1e0a7b3c", -0.15D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        addAttributeModifier(Attributes.ATTACK_SPEED,
                "b4a2d8e1-7c5f-4b9a-8e3d-2f6c0a9d5e1b", -0.10D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        // FLAT, not proportional. Two points off a full diamond set is a tenth
        // of it and off leather it is a quarter, which is the right way round:
        // the plate is supposed to still be worth wearing, and the player in
        // cloth is supposed to feel the cold. Vanilla scales an attribute
        // modifier by the amplifier, so this is -2, -4, -6 across the three
        // levels the fight actually applies.
        addAttributeModifier(Attributes.ARMOR,
                "c7f3b5d9-2e84-4c17-b6a0-3d9e1f7c4a28", -2.0D,
                AttributeModifier.Operation.ADDITION);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            // (it no longer fills the game's freezing)
            entity.hurt(entity.damageSources().freeze(), 1.0F + amplifier * 0.5F);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        int interval = Math.max(10, 50 - amplifier * 10);
        return duration % interval == 0;
    }
}
