package com.antaurora.apofirstlight.thirst;

import com.antaurora.apofirstlight.contamination.ItemContamination;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.registry.AflRecipes;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

/**
 * Boiling dirty water in a furnace, smoker or campfire (Thirst V1): vanilla cooking recipes whose result depends on the
 * input's radiation contamination, because boiling kills germs but cannot remove radioactive contamination. Clean water
 * becomes the recipe's result (purified water); radioactive water becomes boiled water carrying the same level.
 * Recipe JSON types apocalypse_firstlight:boiling_{smelting,smoking,campfire_cooking}, same fields as vanilla; the
 * recipe book and JEI show the clean case.
 */
public final class WaterBoilingRecipes {
    private WaterBoilingRecipes() {}

    public interface Factory<T extends AbstractCookingRecipe> {
        T create(ResourceLocation id, String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float xp, int time);
    }
    /** Reads and writes the same JSON and network form as vanilla's cooking serializer (whose factory is not public). */
    public static final class Serializer<T extends AbstractCookingRecipe> implements RecipeSerializer<T> {
        private final Factory<T> factory;
        private final int defaultTime;
        public Serializer(Factory<T> factory, int defaultTime) { this.factory = factory; this.defaultTime = defaultTime; }

        @Override public T fromJson(ResourceLocation id, JsonObject json) {
            String group = GsonHelper.getAsString(json, "group", "");
            var category = CookingBookCategory.CODEC.byName(GsonHelper.getAsString(json, "category", null), CookingBookCategory.MISC);
            var ingredient = Ingredient.fromJson(GsonHelper.isArrayNode(json, "ingredient")
                    ? GsonHelper.getAsJsonArray(json, "ingredient") : GsonHelper.getAsJsonObject(json, "ingredient"), false);
            if (!json.has("result")) throw new JsonSyntaxException("Missing result, expected to find a string or object");
            ItemStack result;
            if (json.get("result").isJsonObject()) result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            else {
                var item = new ResourceLocation(GsonHelper.getAsString(json, "result"));
                result = new ItemStack(BuiltInRegistries.ITEM.getOptional(item).orElseThrow(() -> new IllegalStateException("Item: " + item + " does not exist")));
            }
            return factory.create(id, group, category, ingredient, result, GsonHelper.getAsFloat(json, "experience", 0.0F),
                    GsonHelper.getAsInt(json, "cookingtime", defaultTime));
        }
        @Override public T fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            var category = buffer.readEnum(CookingBookCategory.class);
            var ingredient = Ingredient.fromNetwork(buffer);
            var result = buffer.readItem();
            return factory.create(id, group, category, ingredient, result, buffer.readFloat(), buffer.readVarInt());
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, T recipe) {
            buffer.writeUtf(recipe.getGroup());
            buffer.writeEnum(recipe.category());
            recipe.getIngredients().get(0).toNetwork(buffer);
            buffer.writeItem(recipe.getResultItem(RegistryAccess.EMPTY));
            buffer.writeFloat(recipe.getExperience());
            buffer.writeVarInt(recipe.getCookingTime());
        }
    }

    /** Purified (the recipe's result) when the water in slot 0 is clean; boiled water with its level otherwise. */
    static ItemStack boil(Container container, ItemStack result) {
        var level = container.isEmpty() ? ItemContamination.Level.CLEAN : ItemContamination.getLevel(container.getItem(0));
        if (level == ItemContamination.Level.CLEAN) return result;
        ItemStack boiled = new ItemStack(AflItems.BOILED_WATER_BOTTLE.get(), result.getCount());
        ItemContamination.setLevel(boiled, level);
        return boiled;
    }

    public static final class Smelting extends SmeltingRecipe {
        public Smelting(ResourceLocation id, String group, CookingBookCategory category, Ingredient in, ItemStack out, float xp, int time) {
            super(id, group, category, in, out, xp, time);
        }
        @Override public ItemStack assemble(Container container, RegistryAccess access) { return boil(container, super.assemble(container, access)); }
        @Override public RecipeSerializer<?> getSerializer() { return AflRecipes.BOILING_SMELTING_SERIALIZER.get(); }
    }

    public static final class Smoking extends SmokingRecipe {
        public Smoking(ResourceLocation id, String group, CookingBookCategory category, Ingredient in, ItemStack out, float xp, int time) {
            super(id, group, category, in, out, xp, time);
        }
        @Override public ItemStack assemble(Container container, RegistryAccess access) { return boil(container, super.assemble(container, access)); }
        @Override public RecipeSerializer<?> getSerializer() { return AflRecipes.BOILING_SMOKING_SERIALIZER.get(); }
    }

    public static final class Campfire extends CampfireCookingRecipe {
        public Campfire(ResourceLocation id, String group, CookingBookCategory category, Ingredient in, ItemStack out, float xp, int time) {
            super(id, group, category, in, out, xp, time);
        }
        @Override public ItemStack assemble(Container container, RegistryAccess access) { return boil(container, super.assemble(container, access)); }
        @Override public RecipeSerializer<?> getSerializer() { return AflRecipes.BOILING_CAMPFIRE_SERIALIZER.get(); }
    }
}
