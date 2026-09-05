package com.antaurora.apofirstlight.recipe;

import com.antaurora.apofirstlight.registry.AflRecipes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

public final class ChemicalReactingRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final Ingredient itemInput;
    private final FluidStack fluidInput;
    private final ItemStack itemOutput;
    private final FluidStack wasteOutput;
    private final int processingTime;

    private ChemicalReactingRecipe(ResourceLocation id, Ingredient itemInput, FluidStack fluidInput,
                                   ItemStack itemOutput, FluidStack wasteOutput, int processingTime) {
        this.id = id;
        this.itemInput = itemInput;
        this.fluidInput = fluidInput.copy();
        this.itemOutput = itemOutput.copy();
        this.wasteOutput = wasteOutput.copy();
        this.processingTime = processingTime;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return container.getContainerSize() > 0 && itemInput.test(container.getItem(0));
    }

    public boolean matches(ItemStack item, FluidStack fluid) {
        return itemInput.test(item) && !fluid.isEmpty()
                && fluid.getFluid() == fluidInput.getFluid()
                && fluid.getAmount() >= fluidInput.getAmount();
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return itemOutput.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return itemOutput.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, itemInput);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AflRecipes.CHEMICAL_REACTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return AflRecipes.CHEMICAL_REACTING_TYPE.get();
    }

    public Ingredient itemInput() {
        return itemInput;
    }

    public FluidStack fluidInput() {
        return fluidInput.copy();
    }

    public ItemStack itemOutput() {
        return itemOutput.copy();
    }

    public FluidStack wasteOutput() {
        return wasteOutput.copy();
    }

    public int processingTime() {
        return processingTime;
    }

    private static FluidStack readFluid(JsonObject root, String field, ResourceLocation recipeId) {
        JsonObject fluidObject = GsonHelper.getAsJsonObject(root, field);
        ResourceLocation fluidId = ResourceLocation.tryParse(GsonHelper.getAsString(fluidObject, "fluid"));
        if (fluidId == null) {
            throw new JsonParseException("Chemical reacting recipe " + recipeId
                    + " has invalid fluid id in " + field);
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        int amount = GsonHelper.getAsInt(fluidObject, "amount");
        if (fluid == Fluids.EMPTY || !BuiltInRegistries.FLUID.containsKey(fluidId) || amount <= 0) {
            throw new JsonParseException("Chemical reacting recipe " + recipeId + " has invalid " + field);
        }
        return new FluidStack(fluid, amount);
    }

    private static void writeFluid(FriendlyByteBuf buffer, FluidStack stack) {
        buffer.writeResourceLocation(BuiltInRegistries.FLUID.getKey(stack.getFluid()));
        buffer.writeVarInt(stack.getAmount());
    }

    private static FluidStack readFluid(FriendlyByteBuf buffer, ResourceLocation recipeId) {
        ResourceLocation fluidId = buffer.readResourceLocation();
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        int amount = buffer.readVarInt();
        if (fluid == Fluids.EMPTY || !BuiltInRegistries.FLUID.containsKey(fluidId) || amount <= 0) {
            throw new IllegalArgumentException("Invalid fluid in chemical reacting recipe " + recipeId);
        }
        return new FluidStack(fluid, amount);
    }

    public static final class Serializer implements RecipeSerializer<ChemicalReactingRecipe> {
        @Override
        public ChemicalReactingRecipe fromJson(ResourceLocation id, JsonObject root) {
            Ingredient itemInput = Ingredient.fromJson(root.get("item_input"));
            ItemStack itemOutput = ShapedRecipe.itemStackFromJson(
                    GsonHelper.getAsJsonObject(root, "item_output"));
            FluidStack fluidInput = readFluid(root, "fluid_input", id);
            FluidStack wasteOutput = readFluid(root, "waste_output", id);
            int processingTime = GsonHelper.getAsInt(root, "processing_time");
            if (itemInput.isEmpty() || itemOutput.isEmpty() || itemOutput.getCount() <= 0
                    || processingTime <= 0) {
                throw new JsonParseException("Chemical reacting recipe " + id + " has invalid item data or time");
            }
            return new ChemicalReactingRecipe(id, itemInput, fluidInput, itemOutput, wasteOutput,
                    processingTime);
        }

        @Override
        public ChemicalReactingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            Ingredient itemInput = Ingredient.fromNetwork(buffer);
            FluidStack fluidInput = readFluid(buffer, id);
            ItemStack itemOutput = buffer.readItem();
            FluidStack wasteOutput = readFluid(buffer, id);
            int processingTime = buffer.readVarInt();
            if (itemInput.isEmpty() || itemOutput.isEmpty() || processingTime <= 0) {
                throw new IllegalArgumentException("Invalid chemical reacting recipe " + id);
            }
            return new ChemicalReactingRecipe(id, itemInput, fluidInput, itemOutput, wasteOutput,
                    processingTime);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ChemicalReactingRecipe recipe) {
            recipe.itemInput.toNetwork(buffer);
            writeFluid(buffer, recipe.fluidInput);
            buffer.writeItem(recipe.itemOutput);
            writeFluid(buffer, recipe.wasteOutput);
            buffer.writeVarInt(recipe.processingTime);
        }
    }
}
