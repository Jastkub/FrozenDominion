package com.jastkub.frozenfortress.registry;

import com.google.common.base.Suppliers;
import com.jastkub.frozenfortress.FrozenFortress;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** The mod's global loot modifiers. */
public final class FFLootModifiers {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, FrozenFortress.MODID);

    public static final RegistryObject<Codec<UniqueCurios>> UNIQUE_CURIOS =
            SERIALIZERS.register("unique_curios", UniqueCurios.CODEC);

    private FFLootModifiers() {
    }

    /**
     * EVERY CURIO ONCE. A curio rolled in one of the mod's chests for a player who has already had that curio out of one is
     * replaced - by a book of one of the citadel's own enchantments, or a handful of Everfrost Shards. What each player
     * has had is kept on the player (persisted through death), so it holds across every chest and every citadel.
     */
    public static class UniqueCurios extends LootModifier {
        public static final Supplier<Codec<UniqueCurios>> CODEC = Suppliers.memoize(() ->
                RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, UniqueCurios::new)));
        private static final String KEY = "FrozenDominionCurios";

        public UniqueCurios(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
            ResourceLocation table = ctx.getQueriedLootTableId();
            // (not the king's own hoard: his crown is his crown, however many the player already has)
            if (!FrozenFortress.MODID.equals(table.getNamespace()) || !table.getPath().startsWith("chests/")
                    || table.getPath().equals("chests/velkhar_hoard")) {
                return loot;
            }
            Entity who = ctx.getParamOrNull(LootContextParams.THIS_ENTITY);
            if (!(who instanceof Player player)) {
                return loot;
            }
            CompoundTag kept = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            ListTag had = kept.getList(KEY, Tag.TAG_STRING);
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < had.size(); i++) {
                seen.add(had.getString(i));
            }
            boolean changed = false;
            for (int i = 0; i < loot.size(); i++) {
                ItemStack stack = loot.get(i);
                if (!isCurio(stack)) {
                    continue;
                }
                String id = String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
                if (seen.contains(id)) {
                    loot.set(i, instead(ctx));
                } else {
                    seen.add(id);
                    had.add(StringTag.valueOf(id));
                    changed = true;
                }
            }
            if (changed) {
                kept.put(KEY, had);
                player.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
            }
            return loot;
        }

        /** A curio: anything in one of Curios' slot tags. */
        private static boolean isCurio(ItemStack stack) {
            return !stack.isEmpty() && stack.getTags().anyMatch(t -> "curios".equals(t.location().getNamespace()));
        }

        /** What comes instead of a curio already had. */
        private static ItemStack instead(LootContext ctx) {
            if (ctx.getRandom().nextBoolean()) {
                List<RegistryObject<Enchantment>> all = List.of(FFEnchantments.SHATTERING, FFEnchantments.FROSTBITE_EDGE,
                        FFEnchantments.HEARTH_EMBER, FFEnchantments.SURE_FOOTING, FFEnchantments.KINGS_ECHO);
                Enchantment e = all.get(ctx.getRandom().nextInt(all.size())).get();
                return EnchantedBookItem.createForEnchantment(
                        new EnchantmentInstance(e, 1 + ctx.getRandom().nextInt(e.getMaxLevel())));
            }
            return new ItemStack(FFItems.EVERFROST_SHARD.get(), 8 + ctx.getRandom().nextInt(9));
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC.get();
        }
    }
}
