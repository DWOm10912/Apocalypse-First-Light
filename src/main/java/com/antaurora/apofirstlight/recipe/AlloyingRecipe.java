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
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class AlloyingRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final List<CountedIngredient> ingredients;
    private final ItemStack result;
    private final int processingTime;
    private final int energyFePerTick;

    private AlloyingRecipe(ResourceLocation id, List<CountedIngredient> ingredients, ItemStack result,
                           int processingTime, int energyFePerTick) {
        this.id = id;
        this.ingredients = List.copyOf(ingredients);
        this.result = result.copy();
        this.processingTime = processingTime;
        this.energyFePerTick = energyFePerTick;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return match(container) != null;
    }

    @Nullable
    public Match match(Container container) {
        if (container.getContainerSize() < 2) {
            return null;
        }
        ItemStack firstSlot = container.getItem(0);
        ItemStack secondSlot = container.getItem(1);
        if (ingredients.size() == 1) {
            CountedIngredient ingredient = ingredients.get(0);
            if (!firstSlot.isEmpty() && secondSlot.isEmpty() && ingredient.matches(firstSlot)) {
                return new Match(ingredient.count(), 0);
            }
            if (firstSlot.isEmpty() && !secondSlot.isEmpty() && ingredient.matches(secondSlot)) {
                return new Match(0, ingredient.count());
            }
            return null;
        }

        if (firstSlot.isEmpty() || secondSlot.isEmpty()) {
            return null;
        }
        CountedIngredient firstIngredient = ingredients.get(0);
        CountedIngredient secondIngredient = ingredients.get(1);
        if (firstIngredient.matches(firstSlot) && secondIngredient.matches(secondSlot)) {
            return new Match(firstIngredient.count(), secondIngredient.count());
        }
        if (firstIngredient.matches(secondSlot) && secondIngredient.matches(firstSlot)) {
            return new Match(secondIngredient.count(), firstIngredient.count());
        }
        return null;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= ingredients.size();
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> result = NonNullList.create();
        ingredients.forEach(entry -> result.add(entry.ingredient()));
        return result;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AflRecipes.ALLOYING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return AflRecipes.ALLOYING_TYPE.get();
    }

    public List<CountedIngredient> countedIngredients() {
        return ingredients;
    }

    public ItemStack result() {
        return result.copy();
    }

    public int processingTime() {
        return processingTime;
    }

    public int energyFePerTick() {
        return energyFePerTick;
    }

    public record CountedIngredient(Ingredient ingredient, int count) {
        public boolean matches(ItemStack stack) {
            return ingredient.test(stack) && stack.getCount() >= count;
        }
    }

    public record Match(int firstSlotCount, int secondSlotCount) {
    }

    public static final class Serializer implements RecipeSerializer<AlloyingRecipe> {
        @Override
        public AlloyingRecipe fromJson(ResourceLocation id, JsonObject root) {
            JsonArray array = GsonHelper.getAsJsonArray(root, "ingredients");
            if (array.size() < 1 || array.size() > 2) {
                throw new JsonParseException("Alloying recipe " + id + " must define 1 or 2 ingredients");
            }
            List<CountedIngredient> ingredients = new java.util.ArrayList<>(array.size());
            for (int index = 0; index < array.size(); index++) {
                JsonObject entry = GsonHelper.convertToJsonObject(array.get(index), "ingredients[" + index + "]");
                Ingredient ingredient = Ingredient.fromJson(entry.get("ingredient"));
                int count = GsonHelper.getAsInt(entry, "count");
                if (ingredient.isEmpty() || count <= 0) {
                    throw new JsonParseException("Alloying recipe " + id
                            + " ingredients[" + index + "] must have a non-empty ingredient and positive count");
                }
                ingredients.add(new CountedIngredient(ingredient, count));
            }

            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(root, "result"));
            int processingTime = GsonHelper.getAsInt(root, "processing_time");
            int energyFePerTick = GsonHelper.getAsInt(root, "energy_fe_per_tick");
            if (result.isEmpty() || result.getCount() <= 0) {
                throw new JsonParseException("Alloying recipe " + id + " result must not be empty");
            }
            if (processingTime <= 0 || energyFePerTick <= 0) {
                throw new JsonParseException("Alloying recipe " + id
                        + " processing_time and energy_fe_per_tick must be > 0");
            }
            return new AlloyingRecipe(id, ingredients, result, processingTime, energyFePerTick);
        }

        @Override
        public AlloyingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            int ingredientCount = buffer.readVarInt();
            if (ingredientCount < 1 || ingredientCount > 2) {
                throw new IllegalArgumentException("Invalid ingredient count for alloying recipe " + id);
            }
            List<CountedIngredient> ingredients = new java.util.ArrayList<>(ingredientCount);
            for (int index = 0; index < ingredientCount; index++) {
                Ingredient ingredient = Ingredient.fromNetwork(buffer);
                int count = buffer.readVarInt();
                if (ingredient.isEmpty() || count <= 0) {
                    throw new IllegalArgumentException("Invalid ingredient in alloying recipe " + id);
                }
                ingredients.add(new CountedIngredient(ingredient, count));
            }
            ItemStack result = buffer.readItem();
            int processingTime = buffer.readVarInt();
            int energyFePerTick = buffer.readVarInt();
            if (result.isEmpty() || processingTime <= 0 || energyFePerTick <= 0) {
                throw new IllegalArgumentException("Invalid alloying recipe " + id);
            }
            return new AlloyingRecipe(id, ingredients, result, processingTime, energyFePerTick);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, AlloyingRecipe recipe) {
            buffer.writeVarInt(recipe.ingredients.size());
            for (CountedIngredient ingredient : recipe.ingredients) {
                ingredient.ingredient().toNetwork(buffer);
                buffer.writeVarInt(ingredient.count());
            }
            buffer.writeItem(recipe.result);
            buffer.writeVarInt(recipe.processingTime);
            buffer.writeVarInt(recipe.energyFePerTick);
        }
    }
}
