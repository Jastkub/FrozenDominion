package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.menu.FrostForgeMenu;
import com.jastkub.frozenfortress.recipe.FrostForgingRecipe;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The Frost Anvil's forging (07.10.2026): its recipe type, its recipes' serializer, its window. */
public final class FFRecipes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.RECIPE_TYPE, FrozenFortress.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.RECIPE_SERIALIZER, FrozenFortress.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, FrozenFortress.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<FrostForgingRecipe>> FROST_FORGING =
            RECIPE_TYPES.register("frost_forging", () -> RecipeType.simple(FrozenFortress.id("frost_forging")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FrostForgingRecipe>> FROST_FORGING_SERIALIZER =
            SERIALIZERS.register("frost_forging", FrostForgingRecipe.Serializer::new);
    public static final DeferredHolder<MenuType<?>, MenuType<FrostForgeMenu>> FROST_FORGE =
            MENUS.register("frost_forge", () -> IMenuTypeExtension.create((id, inv, buf) -> new FrostForgeMenu(id, inv)));

    private FFRecipes() {
    }
}
