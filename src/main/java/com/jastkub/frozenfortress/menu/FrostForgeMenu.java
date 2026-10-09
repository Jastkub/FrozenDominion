package com.jastkub.frozenfortress.menu;

import com.jastkub.frozenfortress.recipe.FrostForgingInput;
import com.jastkub.frozenfortress.recipe.FrostForgingRecipe;
import com.jastkub.frozenfortress.registry.FFBlocks;
import com.jastkub.frozenfortress.registry.FFParticles;
import com.jastkub.frozenfortress.registry.FFRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * THE FROST ANVIL'S WINDOW (FrostAnvilBlock): a
 * grid of three by three, the CORE beside it (FrostForgingRecipe), and what they make. Like a crafting table: what is
 * left in it goes back to whoever closes it; shift on the result forges as many as the grid allows. Every piece taken
 * is struck - the hammer heard and the sparks seen at the anvil.
 */
public class FrostForgeMenu extends AbstractContainerMenu {

    public static final int RESULT = 10, INV_FROM = 11, INV_TO = 47;
    // where its slots are drawn (textures/gui/frost_forge.png)
    public static final int GRID_X = 22, GRID_Y = 17, CORE_X = 86, CORE_Y = 35, RESULT_X = 142, RESULT_Y = 35;

    private final ContainerLevelAccess access;
    private final Player player;
    private final SimpleContainer input = new SimpleContainer(10) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final ResultContainer result = new ResultContainer();

    /** (the client's: its anvil is the server's business) */
    public FrostForgeMenu(int id, Inventory inv) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    public FrostForgeMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(FFRecipes.FROST_FORGE.get(), id);
        this.access = access;
        this.player = inv.player;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                addSlot(new Slot(input, c + r * 3, GRID_X + c * 18, GRID_Y + r * 18));
            }
        }
        addSlot(new Slot(input, FrostForgingRecipe.CORE, CORE_X, CORE_Y));
        addSlot(new ForgedSlot(RESULT_X, RESULT_Y));
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, 8 + c * 18, 142));
        }
    }

    private Optional<FrostForgingRecipe> recipe(Level level) {
        return level.getRecipeManager().getRecipeFor(FFRecipes.FROST_FORGING.get(), FrostForgingInput.of(input), level)
                .map(RecipeHolder::value);
    }

    @Override
    public void slotsChanged(Container c) {
        Level level = player.level();
        if (!level.isClientSide) {
            result.setItem(0, recipe(level).map(r -> r.assemble(FrostForgingInput.of(input), level.registryAccess())).orElse(ItemStack.EMPTY));
            broadcastChanges();
        }
    }

    /** The piece is taken: what it was made of goes (leaving what a recipe leaves), and the anvil rings. */
    private void forged(ItemStack made) {
        Level level = player.level();
        NonNullList<ItemStack> left = recipe(level).map(r -> r.getRemainingItems(FrostForgingInput.of(input)))
                .orElseGet(() -> NonNullList.withSize(10, ItemStack.EMPTY));
        for (int i = 0; i < 10; i++) {
            ItemStack in = input.getItem(i);
            if (!in.isEmpty()) {
                input.removeItemNoUpdate(i);
                in.shrink(1);
                input.setItem(i, in.isEmpty() ? ItemStack.EMPTY : in);
            }
            ItemStack rest = left.get(i);
            if (!rest.isEmpty()) {
                if (input.getItem(i).isEmpty()) {
                    input.setItem(i, rest);
                } else if (!player.getInventory().add(rest)) {
                    player.drop(rest, false);
                }
            }
        }
        made.onCraftedBy(level, player, made.getCount());
        access.execute((lvl, pos) -> {
            lvl.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 0.9F + lvl.random.nextFloat() * 0.2F);
            lvl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.9F, 0.7F);
            if (lvl instanceof ServerLevel s) {
                s.sendParticles(FFParticles.ICE_SHARD.get(), pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D,
                        8, 0.25D, 0.05D, 0.25D, 0.08D);
                s.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5D,
                        pos.getY() + 1.05D, pos.getZ() + 0.5D, 6, 0.2D, 0.05D, 0.2D, 0.15D);
            }
        });
        slotsChanged(input);
    }

    private class ForgedSlot extends Slot {
        ForgedSlot(int x, int y) {
            super(result, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player who, ItemStack stack) {
            if (!who.level().isClientSide) {
                forged(stack);
            }
            super.onTake(who, stack);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack in = slot.getItem();
        ItemStack copy = in.copy();
        if (index == RESULT) {
            // forged straight into the pack: the game asks again while the same piece comes out
            if (!moveItemStackTo(in, INV_FROM, INV_TO, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(in, copy);
        } else if (index < RESULT) {
            if (!moveItemStackTo(in, INV_FROM, INV_TO, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(in, 0, 9, false)) {             // from the pack into the grid
            if (index < INV_TO - 9 ? !moveItemStackTo(in, INV_TO - 9, INV_TO, false)
                    : !moveItemStackTo(in, INV_FROM, INV_TO - 9, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (in.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (in.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(who, in);
        return copy;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void removed(Player who) {
        super.removed(who);
        access.execute((lvl, pos) -> clearContainer(who, input));
        if (access == ContainerLevelAccess.NULL && !who.level().isClientSide) {
            clearContainer(who, input);
        }
    }

    @Override
    public boolean stillValid(Player who) {
        return stillValid(access, who, FFBlocks.FROST_ANVIL.get());
    }
}
