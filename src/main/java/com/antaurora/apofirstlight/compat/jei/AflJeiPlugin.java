package com.antaurora.apofirstlight.compat.jei;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.CrusherScreen;
import com.antaurora.apofirstlight.client.CompressorScreen;
import com.antaurora.apofirstlight.client.AlloyFurnaceScreen;
import com.antaurora.apofirstlight.client.ChemicalReactorScreen;
import com.antaurora.apofirstlight.client.IndustrialFurnaceScreen;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayout;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayouts;
import com.antaurora.apofirstlight.recipe.CrushingRecipe;
import com.antaurora.apofirstlight.recipe.CompressingRecipe;
import com.antaurora.apofirstlight.recipe.AlloyingRecipe;
import com.antaurora.apofirstlight.recipe.ChemicalReactingRecipe;
import com.antaurora.apofirstlight.recipe.IndustrialSmeltingRecipe;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@JeiPlugin
public final class AflJeiPlugin implements IModPlugin {
    public static final RecipeType<CrushingRecipe> CRUSHING =
            RecipeType.create(ApocalypseFirstLight.MOD_ID, "crushing", CrushingRecipe.class);
    public static final RecipeType<CompressingRecipe> COMPRESSING =
            RecipeType.create(ApocalypseFirstLight.MOD_ID, "compressing", CompressingRecipe.class);
    public static final RecipeType<AlloyingRecipe> ALLOYING =
            RecipeType.create(ApocalypseFirstLight.MOD_ID, "alloying", AlloyingRecipe.class);
    public static final RecipeType<ChemicalReactingRecipe> CHEMICAL_REACTING =
            RecipeType.create(ApocalypseFirstLight.MOD_ID, "chemical_reacting", ChemicalReactingRecipe.class);
    public static final RecipeType<IndustrialSmeltingRecipe> INDUSTRIAL_SMELTING =
            RecipeType.create(ApocalypseFirstLight.MOD_ID, "industrial_smelting", IndustrialSmeltingRecipe.class);

    private static final ResourceLocation PLUGIN_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new CrusherRecipeCategory(
                registration.getJeiHelpers().getGuiHelper()),
                new CompressorRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new AlloyFurnaceRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new ChemicalReactorRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new IndustrialSmeltingRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        List<CrushingRecipe> recipes = level.getRecipeManager()
                .getAllRecipesFor(AflRecipes.CRUSHING_TYPE.get());
        registration.addRecipes(CRUSHING, recipes);
        List<CompressingRecipe> compressingRecipes = level.getRecipeManager()
                .getAllRecipesFor(AflRecipes.COMPRESSING_TYPE.get());
        registration.addRecipes(COMPRESSING, compressingRecipes);
        List<AlloyingRecipe> alloyingRecipes = level.getRecipeManager()
                .getAllRecipesFor(AflRecipes.ALLOYING_TYPE.get());
        registration.addRecipes(ALLOYING, alloyingRecipes);
        registration.addRecipes(CHEMICAL_REACTING, level.getRecipeManager()
                .getAllRecipesFor(AflRecipes.CHEMICAL_REACTING_TYPE.get()));
        registration.addRecipes(INDUSTRIAL_SMELTING, level.getRecipeManager()
                .getAllRecipesFor(AflRecipes.INDUSTRIAL_SMELTING_TYPE.get()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(AflBlocks.CRUSHER.get(), CRUSHING);
        registration.addRecipeCatalyst(AflBlocks.COMPRESSOR.get(), COMPRESSING);
        registration.addRecipeCatalyst(AflBlocks.ALLOY_FURNACE.get(), ALLOYING);
        registration.addRecipeCatalyst(AflBlocks.CHEMICAL_REACTOR.get(), CHEMICAL_REACTING);
        registration.addRecipeCatalyst(AflBlocks.INDUSTRIAL_FURNACE.get(),
                INDUSTRIAL_SMELTING, RecipeTypes.SMELTING, RecipeTypes.BLASTING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        MachineGuiLayout.Element arrow = MachineGuiLayouts.crusher().element("progress_arrow");
        registration.addRecipeClickArea(CrusherScreen.class,
                arrow.x(), arrow.y(), arrow.width(), arrow.height(), CRUSHING);
        MachineGuiLayout.Element compressorArrow = MachineGuiLayouts.compressor().element("progress_arrow");
        registration.addRecipeClickArea(CompressorScreen.class,
                compressorArrow.x(), compressorArrow.y(), compressorArrow.width(), compressorArrow.height(),
                COMPRESSING);
        MachineGuiLayout.Element alloyArrow = MachineGuiLayouts.alloyFurnace().element("progress_arrow");
        registration.addRecipeClickArea(AlloyFurnaceScreen.class,
                alloyArrow.x(), alloyArrow.y(), alloyArrow.width(), alloyArrow.height(), ALLOYING);
        MachineGuiLayout.Element chemicalArrow = MachineGuiLayouts.chemicalReactor().element("progress_arrow");
        registration.addRecipeClickArea(ChemicalReactorScreen.class,
                chemicalArrow.x(), chemicalArrow.y(), chemicalArrow.width(), chemicalArrow.height(),
                CHEMICAL_REACTING);
        for (int lane = 0; lane < 3; lane++) {
            MachineGuiLayout.Element industrialArrow = MachineGuiLayouts.industrialFurnace()
                    .element("progress_arrow_" + lane);
            registration.addRecipeClickArea(IndustrialFurnaceScreen.class,
                    industrialArrow.x(), industrialArrow.y(), industrialArrow.width(), industrialArrow.height(),
                    INDUSTRIAL_SMELTING, RecipeTypes.BLASTING, RecipeTypes.SMELTING);
        }
    }
}
