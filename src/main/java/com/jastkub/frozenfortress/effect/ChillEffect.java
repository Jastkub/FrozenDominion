package com.jastkub.frozenfortress.effect;

import com.jastkub.frozenfortress.registry.FFEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * CHILL - the Stormcrown's cold, laid on every player within its reach while
 * it burns hostile.
 *
 * <p>It keeps Frostbite on its bearer (so no healing, the slow, the thinned
 * armour) and bites on its own every three seconds besides. Nothing drinks it
 * off: no milk, no potion, no fire - the only thing that keeps it away is the
 * Hearth Amulet from the watchtower, and it ends for good when the crown is
 * taken.
 */
public class ChillEffect extends MobEffect {

    public ChillEffect() {
        super(MobEffectCategory.HARMFUL, 0x6FA8E8);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) {
            return;
        }
        if (!entity.hasEffect(FFEffects.FROSTBITE.get())) {
            entity.addEffect(new MobEffectInstance(FFEffects.FROSTBITE.get(), 80, 0, true, true, true));
        }
        MobEffectInstance self = entity.getEffect(this);
        if (self != null && self.getDuration() % 60 == 0) {
            entity.hurt(entity.damageSources().freeze(), 1.0F + amplifier);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    /** Nothing cures it: the cold comes from the crown, not from the body. */
    @Override
    public List<ItemStack> getCurativeItems() {
        return new ArrayList<>();
    }
}
