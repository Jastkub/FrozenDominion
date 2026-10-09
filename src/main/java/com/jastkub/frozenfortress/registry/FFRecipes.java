package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.menu.FrostForgeMenu;
import com.jastkub.frozenfortress.recipe.FrostForgingRecipe;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The Frost Anvil's forging (07.10.2026): its recipe type, its recipes' serializer, its window. */
public final class FFRecipes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, FrozenFortress.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, FrozenFortress.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, FrozenFortress.MODID);

    public static final RegistryObject<RecipeType<FrostForgingRecipe>> FROST_FORGING =
            RECIPE_TYPES.register("frost_forging", () -> RecipeType.simple(FrozenFortress.id("frost_forging")));
    public static final RegistryObject<RecipeSerializer<FrostForgingRecipe>> FROST_FORGING_SERIALIZER =
            SERIALIZERS.register("frost_forging", FrostForgingRecipe.Serializer::new);
    public static final RegistryObject<MenuType<FrostForgeMenu>> FROST_FORGE =
            MENUS.register("frost_forge", () -> IForgeMenuType.create((id, inv, buf) -> new FrostForgeMenu(id, inv)));

    private FFRecipes() {
    }
}
