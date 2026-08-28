package com.antaurora.apofirstlight.recipe;

import com.antaurora.apofirstlight.registry.AflRecipes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
import java.util.List;

public final class CrushingRecipe implements Recipe<Container> {
    private static final int MAX_RESULTS = 6;

    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final List<ItemStack> results;
    private final int processingTime;

    public CrushingRecipe(ResourceLocation id, Ingredient ingredient, List<ItemStack> results, int processingTime) {
        this.id = id;
        this.ingredient = ingredient;
        this.results = results.stream().map(ItemStack::copy).toList();
        this.processingTime = processingTime;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return container.getContainerSize() > 0 && ingredient.test(container.getItem(0));
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return results.get(0).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return results.get(0).copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, ingredient);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AflRecipes.CRUSHING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return AflRecipes.CRUSHING_TYPE.get();
    }

    public List<ItemStack> results() {
        return results.stream().map(ItemStack::copy).toList();
    }

    public int processingTime() {
        return processingTime;
    }

    public static final class Serializer implements RecipeSerializer<CrushingRecipe> {
        @Override
        public CrushingRecipe fromJson(ResourceLocation id, JsonObject root) {
            Ingredient ingredient = Ingredient.fromJson(root.get("ingredient"));
            if (ingredient.isEmpty()) {
                throw new JsonParseException("Crushing recipe " + id + " ingredient must not be empty");
            }

            JsonArray resultArray = GsonHelper.getAsJsonArray(root, "results");
            if (resultArray.size() < 1 || resultArray.size() > MAX_RESULTS) {
                throw new JsonParseException("Crushing recipe " + id + " results must contain 1 to "
                        + MAX_RESULTS + " entries");
            }
            List<ItemStack> results = new ArrayList<>(resultArray.size());
            for (int index = 0; index < resultArray.size(); index++) {
                JsonObject resultObject = GsonHelper.convertToJsonObject(
                        resultArray.get(index), "results[" + index + "]");
                ItemStack result = ShapedRecipe.itemStackFromJson(resultObject);
                if (result.isEmpty() || result.getCount() <= 0) {
                    throw new JsonParseException("Crushing recipe " + id
                            + " result " + index + " must have count > 0");
                }
                results.add(result);
            }

            int processingTime = GsonHelper.getAsInt(root, "processing_time");
            if (processingTime <= 0) {
                throw new JsonParseException("Crushing recipe " + id + " processing_time must be > 0");
            }
            return new CrushingRecipe(id, ingredient, results, processingTime);
        }

        @Override
        public CrushingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            int resultCount = buffer.readVarInt();
            if (resultCount < 1 || resultCount > MAX_RESULTS) {
                throw new IllegalArgumentException("Invalid crushing result count " + resultCount);
            }
            List<ItemStack> results = new ArrayList<>(resultCount);
            for (int index = 0; index < resultCount; index++) {
                results.add(buffer.readItem());
            }
            int processingTime = buffer.readVarInt();
            if (processingTime <= 0) {
                throw new IllegalArgumentException("Invalid crushing processing time " + processingTime);
            }
            return new CrushingRecipe(id, ingredient, results, processingTime);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, CrushingRecipe recipe) {
            recipe.ingredient.toNetwork(buffer);
            buffer.writeVarInt(recipe.results.size());
            recipe.results.forEach(buffer::writeItem);
            buffer.writeVarInt(recipe.processingTime);
        }
    }
}
