package com.jastkub.fdspells;

import com.jastkub.fdspells.entity.IceSentinelEntity;
import com.jastkub.fdspells.registry.FDSRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * IRON'S SPELLBOOKS: FROZEN DOMINION. Eight spells of the Ice school, each of them drawn as a thing in the
 * world - a model, not a cloud of particles: Avalanche, Icicle Rain, Frost Javelin, Frost Shackles, Frost Heart,
 * Rune Litany, Ice Sentinel, Frost Shell.
 *
 * <p>Iron's Spells is required; Frozen Dominion is not - with it installed the scrolls turn up in the citadel's chests
 * (data/frozen_dominion_spells/loot_modifiers), without it nothing here refers to it.
 */
@Mod(FDSpells.MODID)
public class FDSpells {

    public static final String MODID = "frozen_dominion_spells";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public FDSpells(IEventBus bus) {
        FDSRegistry.register(bus);
        bus.addListener(this::attributes);
        bus.addListener(this::tabs);
    }

    /** The breviary on Iron's Spells' own equipment tab, by its books. */
    private void tabs(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == io.redspace.ironsspellbooks.registries.CreativeTabRegistry.EQUIPMENT_TAB.getKey()) {
            event.accept(FDSRegistry.RIME_BREVIARY.get());
        }
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(FDSRegistry.ICE_SENTINEL.get(), IceSentinelEntity.createAttributes().build());
    }
}
