package com.jastkub.frozenfortress.effect;

import com.jastkub.frozenfortress.registry.FFEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * WARMTH - what the Potion of Warmth gives.
 *
 * <p>While it lasts Frostbite does not take: CommonEvents refuses it as it is laid on, and whatever was on before
 * the potion is thawed out here, with the freezing it brought. So the potion is the one answer to a cold that
 * stops you healing - and it is only ever found, never brewed, so it is spent with thought.
 *
 * <p>It is not the Hearth Amulet: the Stormcrown's Chill still bites through it (its own damage), only its
 * frostbite is kept off.
 */
public class WarmthEffect extends MobEffect {

    public WarmthEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF9A3C);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) {
            return;
        }
        if (entity.hasEffect(FFEffects.FROSTBITE.get())) {
            entity.removeEffect(FFEffects.FROSTBITE.get());
        }
        // (a trap room's heart: its cold the potion does not touch - only a hearth does; HeartFrostEffect)
        if (entity.getTicksFrozen() > 0 && !entity.isInPowderSnow && !entity.hasEffect(FFEffects.HEART_FROST.get())) {
            entity.setTicksFrozen(Math.max(0, entity.getTicksFrozen() - 20));
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 10 == 0;
    }
}
