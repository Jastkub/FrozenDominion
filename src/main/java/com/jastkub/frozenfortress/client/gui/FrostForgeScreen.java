package com.jastkub.frozenfortress.client.gui;

import com.jastkub.frozenfortress.FrozenFortress;
import com.jastkub.frozenfortress.menu.FrostForgeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * THE FROST ANVIL'S WINDOW, drawn (textures/gui/frost_forge.png, tools/gen_forge_gui.py): a frame of frosted stone,
 * the grid on the left, the core in its rimed ring - breathing a pale light while it is empty - the arrow, and the
 * result in its larger frame.
 */
public class FrostForgeScreen extends AbstractContainerScreen<FrostForgeMenu> {

    private static final ResourceLocation BG = FrozenFortress.id("textures/gui/frost_forge.png");

    public FrostForgeScreen(FrostForgeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
        titleLabelX = 8;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // (1.21: super.render draws the darkened background itself, before the window)
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        // the core's ring glows, slowly, while nothing is in it
        if (!menu.getSlot(com.jastkub.frozenfortress.recipe.FrostForgingRecipe.CORE).hasItem() && minecraft != null
                && minecraft.player != null) {
            float t = minecraft.player.tickCount + partial;
            float a = 0.35F + 0.25F * Mth.sin(t * 0.08F);
            g.setColor(1.0F, 1.0F, 1.0F, a);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.blit(BG, x + FrostForgeMenu.CORE_X - 8, y + FrostForgeMenu.CORE_Y - 8, 176, 0, 34, 34, 256, 256);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        }
    }
}
