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
        addAttributeModifier(Attributes.MOVEMENT_SPEED, ID, -0.15D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_SPEED, ID, -0.10D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        // FLAT, not proportional. Two points off a full diamond set is a tenth
        // of it and off leather it is a quarter, which is the right way round:
        // the plate is supposed to still be worth wearing, and the player in
        // cloth is supposed to feel the cold. Vanilla scales an attribute
        // modifier by the amplifier, so this is -2, -4, -6 across the three
        // levels the fight actually applies.
        addAttributeModifier(Attributes.ARMOR, ID, -2.0D,
                AttributeModifier.Operation.ADD_VALUE);
    }

    /** (1.21.1: an effect's modifiers carry an id, not a UUID - one for all three, as vanilla's effects do) */
    private static final net.minecraft.resources.ResourceLocation ID =
            com.jastkub.frozenfortress.FrozenFortress.id("effect.frostbite");

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            // (it no longer fills the game's freezing)
            entity.hurt(entity.damageSources().freeze(), 1.0F + amplifier * 0.5F);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = Math.max(10, 50 - amplifier * 10);
        return duration % interval == 0;
    }
}
