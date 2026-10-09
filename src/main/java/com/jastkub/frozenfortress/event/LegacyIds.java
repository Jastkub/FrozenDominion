package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFBlocks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

/**
 * WHAT WAS, AND WHAT IT IS NOW, in worlds saved before a change.
 *
 * <p>The Reliquary of the Fallen became the Frost Shrine's casket (07/08.10.2026: one station before every boss's
 * door - FrostShrineBlock). A world saved with a reliquary in it would lose it, and whatever it kept for somebody: so
 * the old id stands for the station now, block and block entity both. They keep their things in the same NBT (Room,
 * Fallen), so what an old reliquary held is still there - in a station that stands where it stood.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyIds {

    private LegacyIds() {
    }

    @SubscribeEvent
    public static void onMissing(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<net.minecraft.world.level.block.Block> m
                : event.getMappings(ForgeRegistries.Keys.BLOCKS, FrozenFortress.MODID)) {
            if ("reliquary".equals(m.getKey().getPath())) {
                m.remap(FFBlocks.FROST_SHRINE.get());
            }
        }
        // (08.10.2026) the Gale Talisman is a Gale Feather now
        for (MissingMappingsEvent.Mapping<net.minecraft.world.item.Item> m
                : event.getMappings(ForgeRegistries.Keys.ITEMS, FrozenFortress.MODID)) {
            if ("gale_talisman".equals(m.getKey().getPath())) {
                m.remap(com.jastkub.frozenfortress.registry.FFItems.GALE_FEATHER.get());
            }
        }
        for (MissingMappingsEvent.Mapping<net.minecraft.world.level.block.entity.BlockEntityType<?>> m
                : event.getMappings(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, FrozenFortress.MODID)) {
            if ("reliquary".equals(m.getKey().getPath())) {
                m.remap(FFBlockEntities.FROST_SHRINE.get());
            }
        }
    }
}
