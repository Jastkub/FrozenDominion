package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public final class FFTags {

    /**
     * Masonry that will not tolerate standing water. Every block in this tag
     * boils away any water that touches it, which is what keeps the cellars
     * dry no matter what the aquifers underneath try to do.
     */
    public static final TagKey<Block> WARDING =
            TagKey.create(Registries.BLOCK, FrozenFortress.id("warding"));

    /** Everything that counts against the fortress population cap. */
    public static final TagKey<EntityType<?>> GARRISON =
            TagKey.create(Registries.ENTITY_TYPE, FrozenFortress.id("garrison"));

    private FFTags() {
    }
}
