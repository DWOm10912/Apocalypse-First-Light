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
import net.minecraft.util.RandomSource;
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
    public static final int MAX_RESULTS = 6;

    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final List<Result> results;
    private final int processingTime;

    private CrushingRecipe(ResourceLocation id, Ingredient ingredient, List<Result> results, int processingTime) {
        this.id = id;
        this.ingredient = ingredient;
        this.results = List.copyOf(results);
        this.processingTime = processingTime;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return container.getContainerSize() > 0 && ingredient.test(container.getItem(0));
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return results.get(0).create(results.get(0).minCount);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return results.get(0).create(results.get(0).minCount);
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

    public List<ItemStack> maximumResults() {
        return results.stream().map(result -> result.create(result.maxCount)).toList();
    }

    public List<Result> results() {
        return results;
    }

    public List<ItemStack> rollResults(RandomSource random) {
        return results.stream().map(result -> result.roll(random)).toList();
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
            List<Result> results = new ArrayList<>(resultArray.size());
            for (int index = 0; index < resultArray.size(); index++) {
                JsonObject resultObject = GsonHelper.convertToJsonObject(
                        resultArray.get(index), "results[" + index + "]");
                results.add(parseResult(id, index, resultObject));
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
            List<Result> results = new ArrayList<>(resultCount);
            for (int index = 0; index < resultCount; index++) {
                ItemStack template = buffer.readItem();
                int minCount = buffer.readVarInt();
                int maxCount = buffer.readVarInt();
                results.add(new Result(template, minCount, maxCount));
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
            for (Result result : recipe.results) {
                buffer.writeItem(result.template);
                buffer.writeVarInt(result.minCount);
                buffer.writeVarInt(result.maxCount);
            }
            buffer.writeVarInt(recipe.processingTime);
        }

        private static Result parseResult(ResourceLocation recipeId, int index, JsonObject object) {
            boolean hasCount = object.has("count");
            boolean hasMinCount = object.has("min_count");
            boolean hasMaxCount = object.has("max_count");
            String context = "Crushing recipe " + recipeId + " result " + index;

            if (hasCount && (hasMinCount || hasMaxCount)) {
                throw new JsonParseException(context
                        + " cannot declare count together with min_count or max_count");
            }
            if (hasMinCount != hasMaxCount) {
                throw new JsonParseException(context
                        + " must declare min_count and max_count together");
            }
            if (!hasCount && !hasMinCount) {
                throw new JsonParseException(context
                        + " must declare either count or min_count and max_count");
            }

            int minCount;
            int maxCount;
            JsonObject stackObject = object.deepCopy();
            if (hasCount) {
                minCount = GsonHelper.getAsInt(object, "count");
                maxCount = minCount;
            } else {
                minCount = GsonHelper.getAsInt(object, "min_count");
                maxCount = GsonHelper.getAsInt(object, "max_count");
                stackObject.remove("min_count");
                stackObject.remove("max_count");
                stackObject.addProperty("count", 1);
            }
            if (minCount <= 0) {
                throw new JsonParseException(context + " minimum count must be > 0");
            }
            if (maxCount < minCount) {
                throw new JsonParseException(context + " max_count must be >= min_count");
            }

            ItemStack template = ShapedRecipe.itemStackFromJson(stackObject);
            if (template.isEmpty()) {
                throw new JsonParseException(context + " item must not be empty");
            }
            template.setCount(1);
            return new Result(template, minCount, maxCount);
        }
    }

    public static final class Result {
        private final ItemStack template;
        private final int minCount;
        private final int maxCount;

        private Result(ItemStack template, int minCount, int maxCount) {
            if (template.isEmpty()) {
                throw new IllegalArgumentException("Crushing result item must not be empty");
            }
            if (minCount <= 0 || maxCount < minCount) {
                throw new IllegalArgumentException("Invalid crushing result range "
                        + minCount + ".." + maxCount);
            }
            this.template = template.copy();
            this.template.setCount(1);
            this.minCount = minCount;
            this.maxCount = maxCount;
        }

        private ItemStack roll(RandomSource random) {
            int count = minCount == maxCount
                    ? minCount
                    : random.nextIntBetweenInclusive(minCount, maxCount);
            return create(count);
        }

        private ItemStack create(int count) {
            ItemStack stack = template.copy();
            stack.setCount(count);
            return stack;
        }

        public ItemStack displayStack() {
            return create(minCount == maxCount ? minCount : 1);
        }

        public int minCount() {
            return minCount;
        }

        public int maxCount() {
            return maxCount;
        }

        public boolean hasVariableCount() {
            return minCount != maxCount;
        }
    }
}
