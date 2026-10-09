package com.jastkub.frozenfortress.event;

import com.jastkub.frozenfortress.FrozenFortress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Once, a rune of the prison's seal could come off one of the garrison (a
 * nest's creatures carried the rune of their part of the citadel). No longer
 * each rune lies in one chest, always
 * the same - three in the hidden rooms, the depths' in the cisterns' chained
 * coffer - and no creature carries one. The handler is kept unsubscribed so
 * the tags a nest of an older world still writes come to nothing.
 */
@Mod.EventBusSubscriber(modid = FrozenFortress.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CitadelEvents {

    public static final String RUNE = "CitadelRune";
    public static final String RUNE_CHANCE = "CitadelRuneChance";

    public static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        CompoundTag data = dead.getPersistentData();
        if (!data.contains(RUNE) || !(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        if (dead.getRandom().nextFloat() >= data.getFloat(RUNE_CHANCE)) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(data.getString(RUNE));
        Item rune = id != null ? ForgeRegistries.ITEMS.getValue(id) : null;
        if (rune == null || player.getInventory().hasAnyOf(java.util.Set.of(rune))) {
            return;                                             // one of each is all anybody needs
        }
        event.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY() + 0.5D, dead.getZ(), new ItemStack(rune)));
    }

    private CitadelEvents() {
    }
}
