package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Królewski Krok's moment of being untouchable (KingsrimeSwordItem#stepping): as the dodge roll's (FFRoll) - what
 * is aimed at the one stepping does not land; the world's own harms (a fall, the void, drowning, fire, the cold)
 * still do. Registers itself (an event subscriber), nothing to wire.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class KingsrimeSwordEvents {

    private KingsrimeSwordEvents() {
    }

    @SubscribeEvent
    public static void onSteppedThrough(LivingAttackEvent event) {
        LivingEntity v = event.getEntity();
        if (v.level().isClientSide || !KingsrimeSwordItem.stepping(v)) {
            return;
        }
        DamageSource s = event.getSource();
        if (s.is(DamageTypes.FALL) || s.is(DamageTypes.FELL_OUT_OF_WORLD) || s.is(DamageTypes.DROWN)
                || s.is(DamageTypes.FREEZE) || s.is(DamageTypes.STARVE) || s.is(DamageTypes.IN_WALL)
                || s.is(DamageTypes.LAVA) || s.is(DamageTypes.IN_FIRE) || s.is(DamageTypes.ON_FIRE)
                || s.is(DamageTypes.GENERIC_KILL)) {
            return;
        }
        event.setCanceled(true);
    }
}
