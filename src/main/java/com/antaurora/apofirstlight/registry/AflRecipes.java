package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.recipe.CrushingRecipe;
import com.antaurora.apofirstlight.recipe.CompressingRecipe;
import com.antaurora.apofirstlight.recipe.AlloyingRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflRecipes {
    private static final ResourceLocation CRUSHING_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "crushing");
    private static final ResourceLocation COMPRESSING_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "compressing");
    private static final ResourceLocation ALLOYING_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "alloying");

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, ApocalypseFirstLight.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<RecipeType<CrushingRecipe>> CRUSHING_TYPE =
            RECIPE_TYPES.register("crushing", () -> RecipeType.simple(CRUSHING_ID));
    public static final RegistryObject<RecipeSerializer<CrushingRecipe>> CRUSHING_SERIALIZER =
            RECIPE_SERIALIZERS.register("crushing", CrushingRecipe.Serializer::new);
    public static final RegistryObject<RecipeType<CompressingRecipe>> COMPRESSING_TYPE =
            RECIPE_TYPES.register("compressing", () -> RecipeType.simple(COMPRESSING_ID));
    public static final RegistryObject<RecipeSerializer<CompressingRecipe>> COMPRESSING_SERIALIZER =
            RECIPE_SERIALIZERS.register("compressing", CompressingRecipe.Serializer::new);
    public static final RegistryObject<RecipeType<AlloyingRecipe>> ALLOYING_TYPE =
            RECIPE_TYPES.register("alloying", () -> RecipeType.simple(ALLOYING_ID));
    public static final RegistryObject<RecipeSerializer<AlloyingRecipe>> ALLOYING_SERIALIZER =
            RECIPE_SERIALIZERS.register("alloying", AlloyingRecipe.Serializer::new);

    private AflRecipes() {
    }
}
