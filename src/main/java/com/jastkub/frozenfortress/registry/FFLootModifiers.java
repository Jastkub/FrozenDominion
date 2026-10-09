package com.jastkub.frozenfortress.registry;

import com.jastkub.frozenfortress.FrozenFortress;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
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
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The mod's global loot modifiers. */
public final class FFLootModifiers {

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, FrozenFortress.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<UniqueCurios>> UNIQUE_CURIOS =
            SERIALIZERS.register("unique_curios", () -> UniqueCurios.CODEC);

    private FFLootModifiers() {
    }

    /**
     * EVERY CURIO ONCE. A curio rolled in one of the mod's chests for a player who has already had that curio out of one is
     * replaced - by a book of one of the citadel's own enchantments, or a handful of Everfrost Shards. What each player
     * has had is kept on the player (persisted through death), so it holds across every chest and every citadel.
     */
    public static class UniqueCurios extends LootModifier {
        public static final MapCodec<UniqueCurios> CODEC =
                RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, UniqueCurios::new));
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
                String id = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
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
                List<ResourceKey<Enchantment>> all = List.of(FFEnchantments.SHATTERING, FFEnchantments.FROSTBITE_EDGE,
                        FFEnchantments.HEARTH_EMBER, FFEnchantments.SURE_FOOTING, FFEnchantments.KINGS_ECHO);
                Holder<Enchantment> e = FFEnchantments.holder(all.get(ctx.getRandom().nextInt(all.size())), ctx.getLevel());
                if (e != null) {   // (null only if a datapack took the enchantment away: the shards, then)
                    return EnchantedBookItem.createForEnchantment(
                            new EnchantmentInstance(e, 1 + ctx.getRandom().nextInt(e.value().getMaxLevel())));
                }
            }
            return new ItemStack(FFItems.EVERFROST_SHARD.get(), 8 + ctx.getRandom().nextInt(9));
        }

        @Override
        public MapCodec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }
}
