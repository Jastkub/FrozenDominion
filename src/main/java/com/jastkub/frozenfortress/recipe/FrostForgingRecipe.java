package com.jastkub.frozenfortress.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.jastkub.frozenfortress.registry.FFRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A RECIPE OF THE FROST ANVIL: its grid of three
 * by three - shaped (a pattern, mirrorable, anywhere in the grid) or shapeless - and its CORE, the slot of its heart,
 * where what makes a piece more than metal goes (a seal, a shard of the crown, a miniboss's part); a recipe with no
 * core wants that slot empty.
 *
 * <p>JSON (data/frozen_dominion/recipes, "type": "frozen_dominion:frost_forging"): "pattern" + "key" (shaped) or
 * "ingredients" (shapeless); "core" (optional, an ingredient); "base" (optional: the grid's piece being remade - its
 * enchantments, its wear and its name go onto the result, as a smithing table's upgrade keeps them: the Kingsrime);
 * "result". The container it reads: slots 0-8 the grid, 9 the core (FrostForgeMenu).
 */
public class FrostForgingRecipe implements Recipe<Container> {

    public static final int CORE = 9;

    private final ResourceLocation id;
    private final int width;
    private final int height;
    private final boolean shapeless;
    private final NonNullList<Ingredient> grid;
    private final Ingredient core;
    private final Ingredient base;
    private final ItemStack result;

    public FrostForgingRecipe(ResourceLocation id, int width, int height, boolean shapeless, NonNullList<Ingredient> grid,
                              Ingredient core, Ingredient base, ItemStack result) {
        this.id = id;
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
    public boolean matches(Container c, Level level) {
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
            return in.size() == grid.size() && net.minecraftforge.common.util.RecipeMatcher.findMatches(in, grid) != null;
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

    private boolean fits(Container c, int x0, int y0, boolean mirrored) {
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
    public ItemStack assemble(Container c, RegistryAccess access) {
        ItemStack out = result.copy();
        if (!base.isEmpty()) {
            for (int i = 0; i < 9; i++) {
                ItemStack in = c.getItem(i);
                if (!in.isEmpty() && base.test(in) && in.hasTag()) {
                    // its enchantments, its wear, its name - and over them whatever the recipe lays on (a temper)
                    net.minecraft.nbt.CompoundTag tag = in.getTag().copy();
                    if (result.hasTag()) {
                        tag.merge(result.getTag());
                    }
                    out.setTag(tag);
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
    public ItemStack getResultItem(RegistryAccess access) {
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
    public ResourceLocation getId() {
        return id;
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
    public static class Serializer implements RecipeSerializer<FrostForgingRecipe> {

        @Override
        public FrostForgingRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient core = json.has("core") ? Ingredient.fromJson(json.get("core")) : Ingredient.EMPTY;
            Ingredient base = json.has("base") ? Ingredient.fromJson(json.get("base")) : Ingredient.EMPTY;
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            if (json.has("ingredients")) {
                NonNullList<Ingredient> list = NonNullList.create();
                for (JsonElement e : GsonHelper.getAsJsonArray(json, "ingredients")) {
                    list.add(Ingredient.fromJson(e));
                }
                if (list.isEmpty() || list.size() > 9) {
                    throw new JsonParseException("frost_forging " + id + ": 1 to 9 ingredients");
                }
                return new FrostForgingRecipe(id, 0, 0, true, list, core, base, result);
            }
            Map<Character, Ingredient> key = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(json, "key").entrySet()) {
                if (e.getKey().length() != 1 || e.getKey().equals(" ")) {
                    throw new JsonParseException("frost_forging " + id + ": bad key '" + e.getKey() + "'");
                }
                key.put(e.getKey().charAt(0), Ingredient.fromJson(e.getValue()));
            }
            JsonArray rows = GsonHelper.getAsJsonArray(json, "pattern");
            int h = rows.size(), w = 0;
            for (JsonElement r : rows) {
                w = Math.max(w, r.getAsString().length());
            }
            if (h < 1 || h > 3 || w < 1 || w > 3) {
                throw new JsonParseException("frost_forging " + id + ": the pattern is at most 3 by 3");
            }
            NonNullList<Ingredient> grid = NonNullList.withSize(w * h, Ingredient.EMPTY);
            for (int y = 0; y < h; y++) {
                String row = rows.get(y).getAsString();
                for (int x = 0; x < row.length(); x++) {
                    char ch = row.charAt(x);
                    if (ch != ' ') {
                        Ingredient in = key.get(ch);
                        if (in == null) {
                            throw new JsonParseException("frost_forging " + id + ": '" + ch + "' not in the key");
                        }
                        grid.set(x + y * w, in);
                    }
                }
            }
            return new FrostForgingRecipe(id, w, h, false, grid, core, base, result);
        }

        @Override
        public FrostForgingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            boolean shapeless = buf.readBoolean();
            int w = buf.readVarInt(), h = buf.readVarInt(), n = buf.readVarInt();
            NonNullList<Ingredient> grid = NonNullList.withSize(n, Ingredient.EMPTY);
            for (int i = 0; i < n; i++) {
                grid.set(i, Ingredient.fromNetwork(buf));
            }
            Ingredient core = Ingredient.fromNetwork(buf);
            Ingredient base = Ingredient.fromNetwork(buf);
            ItemStack result = buf.readItem();
            return new FrostForgingRecipe(id, w, h, shapeless, grid, core, base, result);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, FrostForgingRecipe r) {
            buf.writeBoolean(r.shapeless);
            buf.writeVarInt(r.width);
            buf.writeVarInt(r.height);
            buf.writeVarInt(r.grid.size());
            for (Ingredient in : r.grid) {
                in.toNetwork(buf);
            }
            r.core.toNetwork(buf);
            r.base.toNetwork(buf);
            buf.writeItem(r.result);
        }
    }
}
