package com.jastkub.frozenfortress.integration.jei;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.recipe.FrostForgingRecipe;
import com.jastkub.frozenfortress.registry.FFItems;
import com.jastkub.frozenfortress.registry.FFRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * THE FROST ANVIL IN JEI: a tab of its own, its picture the window's
 * (textures/gui/frost_forge.png at 0, 170) - the grid, the core in its ring, the result; the anvil its catalyst.
 */
@JeiPlugin
public class FFJeiPlugin implements IModPlugin {

    public static final RecipeType<FrostForgingRecipe> FORGING =
            RecipeType.create(FrozenFortress.MODID, "frost_forging", FrostForgingRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return FrozenFortress.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration reg) {
        reg.addRecipeCategories(new Forging(reg.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            reg.addRecipes(FORGING, mc.level.getRecipeManager().getAllRecipesFor(FFRecipes.FROST_FORGING.get()));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration reg) {
        reg.addRecipeCatalyst(new ItemStack(FFItems.FROST_ANVIL.get()), FORGING);
    }

    /** The tab: the grid's items at (5 + 18c, 4 + 18r), the core at (72, 22), the result at (129, 22). */
    static class Forging implements IRecipeCategory<FrostForgingRecipe> {

        private static final ResourceLocation TEX = FrozenFortress.id("textures/gui/frost_forge.png");
        private static final int W = 154, H = 62;
        private final IDrawable background;
        private final IDrawable icon;

        Forging(IGuiHelper gui) {
            background = gui.createDrawable(TEX, 0, 170, W, H);
            icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(FFItems.FROST_ANVIL.get()));
        }

        @Override
        public RecipeType<FrostForgingRecipe> getRecipeType() {
            return FORGING;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("container.frozen_dominion.frost_forge");
        }

        @Override
        public int getWidth() {
            return W;
        }

        @Override
        public int getHeight() {
            return H;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, FrostForgingRecipe r, IFocusGroup focuses) {
            List<Ingredient> grid = r.grid();
            if (r.shapeless()) {
                b.setShapeless();
                for (int i = 0; i < grid.size(); i++) {
                    b.addSlot(RecipeIngredientRole.INPUT, 5 + (i % 3) * 18, 4 + (i / 3) * 18).addIngredients(grid.get(i));
                }
            } else {
                for (int y = 0; y < r.height(); y++) {
                    for (int x = 0; x < r.width(); x++) {
                        Ingredient in = grid.get(x + y * r.width());
                        if (!in.isEmpty()) {
                            b.addSlot(RecipeIngredientRole.INPUT, 5 + x * 18, 4 + y * 18).addIngredients(in);
                        }
                    }
                }
            }
            if (!r.core().isEmpty()) {
                b.addSlot(RecipeIngredientRole.INPUT, 72, 22).addIngredients(r.core());
            }
            b.addSlot(RecipeIngredientRole.OUTPUT, 129, 22).addItemStack(r.result());
        }

        @Override
        public void draw(FrostForgingRecipe r, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
            background.draw(g);
        }
    }
}
