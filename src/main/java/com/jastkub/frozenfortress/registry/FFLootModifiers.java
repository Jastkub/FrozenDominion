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
     * EVERY CURIO ONCE IN EACH STRUCTURE. A curio rolled in one of the mod's chests for a player who has already had
     * that curio out of another chest of the SAME structure (the same citadel, camp or watchtower) is replaced - by a
     * book of one of the citadel's own enchantments, or a handful of Everfrost Shards. A new citadel starts the count
     * again. What each player has had is kept on the player, per structure (persisted through death).
     */
    public static class UniqueCurios extends LootModifier {
        public static final MapCodec<UniqueCurios> CODEC =
                RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, UniqueCurios::new));
        private static final String KEY = "FrozenDominionCurios";
        /** Per structure: its key (structureKey) to the curios this player has had out of its chests. */
        private static final String KEY_BY = "FrozenDominionCuriosBy";

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
            // ONCE IN EACH STRUCTURE, not once in the world: what a player has had is counted per citadel (or camp,
            // or watchtower), so a second citadel's chests are as full as the first one's were
            String where = structureKey(ctx);
            if (where == null) {
                return loot;
            }
            CompoundTag kept = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            CompoundTag by = kept.getCompound(KEY_BY);
            ListTag had = by.getList(where, Tag.TAG_STRING);
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < had.size(); i++) {
                seen.add(had.getString(i));
            }
            boolean changed = false;
            for (int i = 0; i < loot.size(); i++) {
                ItemStack stack = loot.get(i);
                // NOT THE HEARTH AMULET. It is the key to the citadel's cold, not a find: the expedition camp's chest
                // has to give it every time, to a player who lost theirs as well as to one who never had it - a camp
                // chest that rolled a book in its place left them with no way to stand the Chill
                if (!isCurio(stack) || stack.is(FFItems.HEARTH_AMULET.get())) {
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
                by.put(where, had);
                kept.put(KEY_BY, by);
                kept.remove(KEY);          // the old count, kept for the whole world
                player.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
            }
            return loot;
        }

        /** Which of the mod's structures the chest stands in (its kind, where it starts, its dimension); null outside. */
        private static String structureKey(LootContext ctx) {
            net.minecraft.world.phys.Vec3 at = ctx.getParamOrNull(LootContextParams.ORIGIN);
            if (at == null) {
                return null;
            }
            net.minecraft.server.level.ServerLevel level = ctx.getLevel();
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(at);
            var structures = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            for (var e : level.structureManager().getAllStructuresAt(pos).entrySet()) {
                ResourceLocation id = structures.getKey(e.getKey());
                if (id == null || !FrozenFortress.MODID.equals(id.getNamespace()) || e.getValue().isEmpty()) {
                    continue;
                }
                return id + "@" + e.getValue().iterator().nextLong() + "@" + level.dimension().location();
            }
            return null;
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
