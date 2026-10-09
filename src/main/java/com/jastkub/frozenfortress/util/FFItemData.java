package com.jastkub.frozenfortress.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;

/**
 * An item's own state (the 1.21.1 port). 1.20.1 kept it in the stack's NBT tag; 1.21.1 has no such tag, so it lives in
 * the minecraft:custom_data component under the very same keys - which is also where the world upgrade puts an old
 * stack's tag, so pieces made before the port keep what they had.
 *
 * <p>Reads hand back a COPY: a change to it does not reach the stack. Every change goes through update(), which writes
 * the tag back (and drops the component if nothing is left in it).
 */
public final class FFItemData {

    private FFItemData() {
    }

    /** Whether the stack carries any data of its own (the old hasTag()). */
    public static boolean has(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_DATA);
    }

    /** A copy of the stack's data - an empty tag if it has none. */
    public static CompoundTag read(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** A copy of one compound inside it (the old getTagElement / getOrCreateTagElement read) - empty if absent. */
    public static CompoundTag element(ItemStack stack, String key) {
        return read(stack).getCompound(key);
    }

    /** Changes the stack's data in place. */
    public static void update(ItemStack stack, Consumer<CompoundTag> edit) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, edit);
    }

    /** Changes one compound inside it, creating it if absent (the old getOrCreateTagElement, written back). */
    public static void updateElement(ItemStack stack, String key, Consumer<CompoundTag> edit) {
        update(stack, t -> {
            CompoundTag e = t.getCompound(key);
            edit.accept(e);
            t.put(key, e);
        });
    }

    /** Replaces the stack's data whole (the old setTag; an empty tag clears it). */
    public static void set(ItemStack stack, CompoundTag tag) {
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }
}
