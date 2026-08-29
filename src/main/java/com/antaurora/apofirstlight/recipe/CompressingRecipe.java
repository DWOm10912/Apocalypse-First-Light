package com.antaurora.apofirstlight.recipe;

import com.antaurora.apofirstlight.registry.AflRecipes;
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

public final class CompressingRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final ItemStack result;
    private final int processingTime;

    private CompressingRecipe(ResourceLocation id, Ingredient ingredient, ItemStack result, int processingTime) {
        this.id = id;
        this.ingredient = ingredient;
        this.result = result.copy();
        this.processingTime = processingTime;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return container.getContainerSize() > 0 && ingredient.test(container.getItem(0));
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result.copy();
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
        return AflRecipes.COMPRESSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return AflRecipes.COMPRESSING_TYPE.get();
    }

    public int processingTime() {
        return processingTime;
    }

    public ItemStack result() {
        return result.copy();
    }

    public static final class Serializer implements RecipeSerializer<CompressingRecipe> {
        @Override
        public CompressingRecipe fromJson(ResourceLocation id, JsonObject root) {
            Ingredient ingredient = Ingredient.fromJson(root.get("ingredient"));
            if (ingredient.isEmpty()) {
                throw new JsonParseException("Compressing recipe " + id + " ingredient must not be empty");
            }
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(root, "result"));
            if (result.isEmpty() || result.getCount() <= 0) {
                throw new JsonParseException("Compressing recipe " + id + " result must not be empty");
            }
            int processingTime = GsonHelper.getAsInt(root, "processing_time");
            if (processingTime <= 0) {
                throw new JsonParseException("Compressing recipe " + id + " processing_time must be > 0");
            }
            return new CompressingRecipe(id, ingredient, result, processingTime);
        }

        @Override
        public CompressingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            ItemStack result = buffer.readItem();
            int processingTime = buffer.readVarInt();
            if (ingredient.isEmpty() || result.isEmpty() || processingTime <= 0) {
                throw new IllegalArgumentException("Invalid compressing recipe " + id);
            }
            return new CompressingRecipe(id, ingredient, result, processingTime);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, CompressingRecipe recipe) {
            recipe.ingredient.toNetwork(buffer);
            buffer.writeItem(recipe.result);
            buffer.writeVarInt(recipe.processingTime);
        }
    }
}
