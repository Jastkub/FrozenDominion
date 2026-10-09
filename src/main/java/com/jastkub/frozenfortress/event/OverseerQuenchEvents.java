package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.entity.ForgeOverseerEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * THE QUENCHED HAMMER HITS HARDER: while
 * the Forge Overseer's hammer is rimed, everything he does - the hammer, its ring, the slag, the tongs' blow - lands
 * QUENCHED_DAMAGE times as hard, on top of the freezing. One place for all his blows, his shockwaves and his slag.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class OverseerQuenchEvents {

    private OverseerQuenchEvents() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ForgeOverseerEntity o && o.isQuenched()) {
            event.setAmount(event.getAmount() * ForgeOverseerEntity.QUENCHED_DAMAGE);
        }
    }
}
