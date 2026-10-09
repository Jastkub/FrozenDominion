package com.jastkub.fdspells.event;

import com.jastkub.fdspells.FDSpells;
import com.jastkub.fdspells.registry.FDSRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FROZEN DOMINION'S WEAPONS, IMBUED. With
 * Frozen Dominion installed its weapons carry spells of this mod the way Iron's Spells' own imbued weapons do - cast
 * from the hand they are held in. Put on a weapon in a player's inventory (crafted, forged, looted alike); a weapon
 * forged up from another (the anvil carries the old one's tag over) gets its own spells in place of the old ones.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FDSpells.MODID)
public final class FDSImbue {

    private static final String MARK = "fdspells_imbued";

    /** Item id -> its spells and their levels. Nothing here refers to Frozen Dominion's classes: absent, nothing. */
    private static final Map<String, List<Imbue>> WEAPONS = new LinkedHashMap<>();

    static {
        put("everfrost_sword", new Imbue(FDSRegistry.FROST_JAVELIN, 2));
        put("kingsrime_sword", new Imbue(FDSRegistry.AVALANCHE, 3), new Imbue(FDSRegistry.FROST_JAVELIN, 3));
        put("sovereigns_lament", new Imbue(FDSRegistry.FROST_HEART, 3), new Imbue(FDSRegistry.FROST_SHELL, 2));
        put("throne_bane", new Imbue(FDSRegistry.FROST_SHACKLES, 3), new Imbue(FDSRegistry.AVALANCHE, 4));
        put("hollow_kings_staff", new Imbue(FDSRegistry.RUNE_LITANY, 3), new Imbue(FDSRegistry.ICICLE_RAIN, 3),
                new Imbue(FDSRegistry.ICE_SENTINEL_SPELL, 2));
        put("everfrost_bow", new Imbue(FDSRegistry.ICICLE_RAIN, 1));
        put("kingsrime_bow", new Imbue(FDSRegistry.ICICLE_RAIN, 2));
        put("last_watch_bow", new Imbue(FDSRegistry.ICICLE_RAIN, 2), new Imbue(FDSRegistry.FROST_SHACKLES, 2));
    }

    private record Imbue(DeferredHolder<AbstractSpell, AbstractSpell> spell, int level) {
    }

    private FDSImbue() {
    }

    private static void put(String item, Imbue... spells) {
        WEAPONS.put("frozen_dominion:" + item, List.of(spells));
    }

    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide || p.tickCount % 40 != 0) {
            return;
        }
        for (ItemStack stack : p.getInventory().items) {
            imbue(stack);
        }
        for (ItemStack stack : p.getInventory().offhand) {
            imbue(stack);
        }
    }

    /** Puts the weapon's spells on it, unless they are on it already (and its own, not its forebear's). */
    public static void imbue(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        List<Imbue> spells = id == null ? null : WEAPONS.get(id.toString());
        if (spells == null) {
            return;
        }
        // (1.21) the mark is in the stack's custom data: item NBT is gone
        String mark = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getString(MARK);
        if (mark.equals(id.toString()) && ISpellContainer.isSpellContainer(stack)) {
            return;
        }
        ISpellContainerMutable c = ISpellContainer.create(spells.size(), true, false).mutableCopy();
        for (Imbue i : spells) {
            c.addSpell(i.spell().get(), i.level(), true);
        }
        ISpellContainer.set(stack, c.toImmutable());
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                stack, t -> t.putString(MARK, id.toString()));
    }

    /** For the curious: does this mod imbue that item. */
    public static boolean imbues(Item item) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        return id != null && WEAPONS.containsKey(id.toString());
    }
}
