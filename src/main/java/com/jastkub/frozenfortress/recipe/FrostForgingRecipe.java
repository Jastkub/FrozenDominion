package com.jastkub.frozenfortress.recipe;

import com.jastkub.frozenfortress.registry.FFRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A RECIPE OF THE FROST ANVIL: its grid of three
 * by three - shaped (a pattern, mirrorable, anywhere in the grid) or shapeless - and its CORE, the slot of its heart,
 * where what makes a piece more than metal goes (a seal, a shard of the crown, a miniboss's part); a recipe with no
 * core wants that slot empty.
 *
 * <p>JSON (data/frozen_dominion/recipe, "type": "frozen_dominion:frost_forging"): "pattern" + "key" (shaped) or
 * "ingredients" (shapeless); "core" (optional, an ingredient); "base" (optional: the grid's piece being remade - its
 * enchantments, its wear and its name go onto the result, as a smithing table's upgrade keeps them: the Kingsrime);
 * "result" (an item stack: "id", "count", "components"). The input it reads (FrostForgingInput): slots 0-8 the grid,
 * 9 the core (FrostForgeMenu).
 */
public class FrostForgingRecipe implements Recipe<FrostForgingInput> {

    public static final int CORE = 9;

    private final int width;
    private final int height;
    private final boolean shapeless;
    private final NonNullList<Ingredient> grid;
    private final Ingredient core;
    private final Ingredient base;
    private final ItemStack result;

    public FrostForgingRecipe(int width, int height, boolean shapeless, NonNullList<Ingredient> grid,
                              Ingredient core, Ingredient base, ItemStack result) {
        this.width = width;
        this.height = height;
        this.shapeless = shapeless;
        this.grid = grid;
        this.core = core;
        this.base = base;
        this.result = result;
    }

    // ------------------------------------------------------------------------------------------------ matching
    @Override
    public boolean matches(FrostForgingInput c, Level level) {
        ItemStack inCore = c.getItem(CORE);
        if (core.isEmpty() ? !inCore.isEmpty() : !core.test(inCore)) {
            return false;
        }
        if (shapeless) {
            List<ItemStack> in = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                if (!c.getItem(i).isEmpty()) {
                    in.add(c.getItem(i));
                }
            }
            return in.size() == grid.size() && net.neoforged.neoforge.common.util.RecipeMatcher.findMatches(in, grid) != null;
        }
        for (int x0 = 0; x0 <= 3 - width; x0++) {
            for (int y0 = 0; y0 <= 3 - height; y0++) {
                if (fits(c, x0, y0, false) || fits(c, x0, y0, true)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean fits(FrostForgingInput c, int x0, int y0, boolean mirrored) {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                int u = x - x0, v = y - y0;
                Ingredient want = Ingredient.EMPTY;
                if (u >= 0 && v >= 0 && u < width && v < height) {
                    want = grid.get(mirrored ? width - u - 1 + v * width : u + v * width);
                }
                if (!want.test(c.getItem(x + y * 3))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(FrostForgingInput c, HolderLookup.Provider access) {
        ItemStack out = result.copy();
        if (!base.isEmpty()) {
            for (int i = 0; i < 9; i++) {
                ItemStack in = c.getItem(i);
                if (!in.isEmpty() && base.test(in) && !in.getComponentsPatch().isEmpty()) {
                    // its enchantments, its wear, its name - and over them whatever the recipe lays on (a temper).
                    // (1.20.1 merged the two NBT tags; what was one tag is now components: the base's all go over,
                    // the result's own over those, and the two custom_data tags merge as the tags did)
                    CompoundTag data = in.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    data.merge(result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
                    out = new ItemStack(result.getItemHolder(), result.getCount(), in.getComponentsPatch());
                    out.applyComponents(result.getComponentsPatch());
                    CustomData.set(DataComponents.CUSTOM_DATA, out, data);
                    break;
                }
            }
        }
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= grid.size();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider access) {
        return result;
    }

    // ------------------------------------------------------------------------------------------------ for its window and JEI
    public int width() {
        return shapeless ? 3 : width;
    }

    public int height() {
        return shapeless ? 3 : height;
    }

    public boolean shapeless() {
        return shapeless;
    }

    /** The grid's ingredients (shaped: width by height, row by row; shapeless: as listed). */
    public NonNullList<Ingredient> grid() {
        return grid;
    }

    public Ingredient core() {
        return core;
    }

    public ItemStack result() {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> all = NonNullList.create();
        all.addAll(grid);
        if (!core.isEmpty()) {
            all.add(core);
        }
        return all;
    }

    @Override
    public boolean isSpecial() {
        return true;                                  // (not in the vanilla recipe book)
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return FFRecipes.FROST_FORGING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return FFRecipes.FROST_FORGING.get();
    }

    // ------------------------------------------------------------------------------------------------ its JSON
    /**
     * The JSON as written: either "pattern" + "key" or "ingredients", the optional "core" and "base", the "result".
     * Read into this first and checked as 1.20.1's fromJson checked it (rows of any length up to three, a key of single
     * characters, every pattern character keyed, one to nine ingredients).
     */
    private record Json(Optional<List<String>> pattern, Optional<Map<String, Ingredient>> key,
                        Optional<List<Ingredient>> ingredients, Optional<Ingredient> core, Optional<Ingredient> base,
                        ItemStack result) {

        static final MapCodec<Json> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("pattern").forGetter(Json::pattern),
                Codec.unboundedMap(Codec.STRING, Ingredient.CODEC_NONEMPTY).optionalFieldOf("key").forGetter(Json::key),
                Ingredient.CODEC_NONEMPTY.listOf().optionalFieldOf("ingredients").forGetter(Json::ingredients),
                Ingredient.CODEC_NONEMPTY.optionalFieldOf("core").forGetter(Json::core),
                Ingredient.CODEC_NONEMPTY.optionalFieldOf("base").forGetter(Json::base),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(Json::result)
        ).apply(i, Json::new));

        DataResult<FrostForgingRecipe> toRecipe() {
            Ingredient c = core.orElse(Ingredient.EMPTY);
            Ingredient b = base.orElse(Ingredient.EMPTY);
            if (ingredients.isPresent()) {
                List<Ingredient> list = ingredients.get();
                if (list.isEmpty() || list.size() > 9) {
                    return DataResult.error(() -> "frost_forging: 1 to 9 ingredients");
                }
                NonNullList<Ingredient> grid = NonNullList.create();
                grid.addAll(list);
                return DataResult.success(new FrostForgingRecipe(0, 0, true, grid, c, b, result));
            }
            if (pattern.isEmpty() || key.isEmpty()) {
                return DataResult.error(() -> "frost_forging: \"pattern\" and \"key\", or \"ingredients\"");
            }
            Map<Character, Ingredient> keys = new LinkedHashMap<>();
            for (Map.Entry<String, Ingredient> e : key.get().entrySet()) {
                if (e.getKey().length() != 1 || e.getKey().equals(" ")) {
                    return DataResult.error(() -> "frost_forging: bad key '" + e.getKey() + "'");
                }
                keys.put(e.getKey().charAt(0), e.getValue());
            }
            List<String> rows = pattern.get();
            int h = rows.size(), w = 0;
            for (String r : rows) {
                w = Math.max(w, r.length());
            }
            if (h < 1 || h > 3 || w < 1 || w > 3) {
                return DataResult.error(() -> "frost_forging: the pattern is at most 3 by 3");
            }
            NonNullList<Ingredient> grid = NonNullList.withSize(w * h, Ingredient.EMPTY);
            for (int y = 0; y < h; y++) {
                String row = rows.get(y);
                for (int x = 0; x < row.length(); x++) {
                    char ch = row.charAt(x);
                    if (ch != ' ') {
                        Ingredient in = keys.get(ch);
                        if (in == null) {
                            return DataResult.error(() -> "frost_forging: '" + ch + "' not in the key");
                        }
                        grid.set(x + y * w, in);
                    }
                }
            }
            return DataResult.success(new FrostForgingRecipe(w, h, false, grid, c, b, result));
        }

        /** Back to JSON (a shaped grid gets a key of its own: a letter for each filled cell). */
        static Json of(FrostForgingRecipe r) {
            Optional<Ingredient> c = r.core.isEmpty() ? Optional.empty() : Optional.of(r.core);
            Optional<Ingredient> b = r.base.isEmpty() ? Optional.empty() : Optional.of(r.base);
            if (r.shapeless) {
                return new Json(Optional.empty(), Optional.empty(), Optional.of(List.copyOf(r.grid)), c, b, r.result);
            }
            List<String> rows = new ArrayList<>();
            Map<String, Ingredient> key = new LinkedHashMap<>();
            for (int y = 0; y < r.height; y++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < r.width; x++) {
                    Ingredient in = r.grid.get(x + y * r.width);
                    if (in.isEmpty()) {
                        row.append(' ');
                    } else {
                        String k = String.valueOf((char) ('A' + x + y * r.width));
                        key.put(k, in);
                        row.append(k);
                    }
                }
                rows.add(row.toString());
            }
            return new Json(Optional.of(rows), Optional.of(key), Optional.empty(), c, b, r.result);
        }
    }

    public static class Serializer implements RecipeSerializer<FrostForgingRecipe> {

        public static final MapCodec<FrostForgingRecipe> CODEC = Json.CODEC.flatXmap(Json::toRecipe,
                r -> DataResult.success(Json.of(r)));

        public static final StreamCodec<RegistryFriendlyByteBuf, FrostForgingRecipe> STREAM_CODEC =
                StreamCodec.of(Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<FrostForgingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FrostForgingRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static FrostForgingRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
            boolean shapeless = buf.readBoolean();
            int w = buf.readVarInt(), h = buf.readVarInt(), n = buf.readVarInt();
            NonNullList<Ingredient> grid = NonNullList.withSize(n, Ingredient.EMPTY);
            for (int i = 0; i < n; i++) {
                grid.set(i, Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
            }
            Ingredient core = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            Ingredient base = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
            return new FrostForgingRecipe(w, h, shapeless, grid, core, base, result);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buf, FrostForgingRecipe r) {
            buf.writeBoolean(r.shapeless);
            buf.writeVarInt(r.width);
            buf.writeVarInt(r.height);
            buf.writeVarInt(r.grid.size());
            for (Ingredient in : r.grid) {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, in);
            }
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.core);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.base);
            ItemStack.STREAM_CODEC.encode(buf, r.result);
        }
    }
}
