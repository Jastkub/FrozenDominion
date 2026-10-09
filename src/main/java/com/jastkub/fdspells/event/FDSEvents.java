package com.jastkub.fdspells.event;

import com.jastkub.fdspells.FDSpells;
import com.jastkub.fdspells.entity.LitanyRuneEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

/** The Rune Litany's trigger: whenever its caster hurts something, one rune of the ring goes after it. */
@Mod.EventBusSubscriber(modid = FDSpells.MODID)
public final class FDSEvents {

    private FDSEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getAmount() <= 0.0F) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof LitanyRuneEntity || !(event.getSource().getEntity() instanceof LivingEntity caster)
                || caster == victim) {
            return;                                                // a rune's own blow sends no other rune
        }
        List<LitanyRuneEntity> ring = victim.level().getEntitiesOfClass(LitanyRuneEntity.class,
                caster.getBoundingBox().inflate(4.0D), r -> r.orbiting() && r.ownedBy(caster));
        if (ring.isEmpty() || !LitanyRuneEntity.isFoe(caster, victim)) {
            return;
        }
        ring.stream().min(Comparator.comparingDouble(r -> r.distanceToSqr(victim))).ifPresent(r -> r.seek(victim));
    }
}
