package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.BossCutscenes;
import com.jastkub.frozenfortress.FrozenFortress;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * WHAT YOU HAVE MET (07.10.2026, for the Chronicle - the Patchouli guide): the first time a player SEES one of the
 * citadel's creatures - in reach and with nothing solid between (BossCutscenes.witness) - they earn the hidden
 * advancement frozen_dominion:met/<its id>, and the Chronicle's page on it opens. No toast, no chat: the
 * advancements have no display; the book is where it shows.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FrozenFortress.MODID)
public final class FFDiscovery {

    /** Whose pages open on sight. */
    public static final Set<String> MET = Set.of("frostbound_sentinel", "rimeweaver", "stillbow", "frostmaw",
            "frost_skeleton", "shade", "vault_warden", "turnkey", "lamplighter", "shade_shepherd", "drowned_lady",
            "rime_priestess", "ice_aurochs", "forge_overseer", "ice_monstrosity", "velkhar");
    private static final double SIGHT = 28.0D;

    private FFDiscovery() {
    }

    @SubscribeEvent
    public static void look(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 40 != 7
                || p.isSpectator()) {
            return;
        }
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SIGHT),
                e -> e.isAlive() && isMet(e))) {
            ResourceLocation type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            if (type == null) {
                continue;
            }
            AdvancementHolder adv = p.server.getAdvancements().get(FrozenFortress.id("met/" + type.getPath()));
            if (adv == null) {
                continue;
            }
            AdvancementProgress progress = p.getAdvancements().getOrStartProgress(adv);
            if (progress.isDone() || !BossCutscenes.witness(e, p)) {
                continue;
            }
            for (String c : progress.getRemainingCriteria()) {
                p.getAdvancements().award(adv, c);
            }
        }
    }

    private static boolean isMet(LivingEntity e) {
        ResourceLocation type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return type != null && FrozenFortress.MODID.equals(type.getNamespace()) && MET.contains(type.getPath());
    }
}
