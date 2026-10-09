package com.jastkub.frozenfortress.recipe;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Frost Anvil recipe reads (the 1.21.1 port: recipes no longer read a Container): slots 0-8 the grid, 9 the core
 * (FrostForgingRecipe.CORE). The stacks are the window's own, not copies, as when the recipe read the container itself.
 */
public record FrostForgingInput(List<ItemStack> items) implements RecipeInput {

    public static FrostForgingInput of(Container c) {
        List<ItemStack> items = new ArrayList<>(c.getContainerSize());
        for (int i = 0; i < c.getContainerSize(); i++) {
            items.add(c.getItem(i));
        }
        return new FrostForgingInput(items);
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return items.size();
    }
}
