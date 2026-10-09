package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.registry.FFBlockEntities;
import com.jastkub.frozenfortress.registry.FFBlocks;

/**
 * WHAT WAS, AND WHAT IT IS NOW, in worlds saved before a change.
 *
 * <p>The Reliquary of the Fallen became the Frost Shrine's casket (07/08.10.2026: one station before every boss's
 * door - FrostShrineBlock). A world saved with a reliquary in it would lose it, and whatever it kept for somebody: so
 * the old id stands for the station now, block and block entity both. They keep their things in the same NBT (Room,
 * Fallen), so what an old reliquary held is still there - in a station that stands where it stood.
 *
 * <p>(1.21) NeoForge has no missing-mappings event: the old ids are aliases in the registries, set before they fill
 * (FrozenFortress's constructor).
 */
public final class LegacyIds {

    private LegacyIds() {
    }

    public static void register() {
        FFBlocks.BLOCKS.addAlias(FrozenFortress.id("reliquary"), FrozenFortress.id("frost_shrine"));
        FFBlockEntities.BLOCK_ENTITIES.addAlias(FrozenFortress.id("reliquary"), FrozenFortress.id("frost_shrine"));
        // (08.10.2026) the Gale Talisman is a Gale Feather now
        com.jastkub.frozenfortress.registry.FFItems.ITEMS.addAlias(FrozenFortress.id("gale_talisman"),
                FrozenFortress.id("gale_feather"));
    }
}
