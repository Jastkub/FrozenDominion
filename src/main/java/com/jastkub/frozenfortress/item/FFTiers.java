package com.jastkub.frozenfortress.item;

import com.jastkub.frozenfortress.registry.FFItems;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

public final class FFTiers {

    /** Tier of weapons forged from the Silent Winter itself. Above netherite. */
    public static final Tier EVERFROST = new Tier() {
        @Override
        public int getUses() {
            return 2731;
        }

        @Override
        public float getSpeed() {
            return 9.5F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 5.0F;
        }

        @Override
        public net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> getIncorrectBlocksForDrops() {
            return net.minecraft.tags.BlockTags.INCORRECT_FOR_NETHERITE_TOOL;   // (it was level 5: above netherite)
        }

        @Override
        public int getEnchantmentValue() {
            return 18;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(FFItems.EVERFROST_SHARD.get());
        }
    };

    private FFTiers() {
    }
}
