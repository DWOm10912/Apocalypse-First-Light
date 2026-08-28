package com.antaurora.apofirstlight.compat.jei;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.CrusherScreen;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayout;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayouts;
import com.antaurora.apofirstlight.recipe.CrushingRecipe;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
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

    private static final ResourceLocation PLUGIN_ID =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new CrusherRecipeCategory(
                registration.getJeiHelpers().getGuiHelper()));
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
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(AflBlocks.CRUSHER.get(), CRUSHING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        MachineGuiLayout.Element arrow = MachineGuiLayouts.crusher().element("progress_arrow");
        registration.addRecipeClickArea(CrusherScreen.class,
                arrow.x(), arrow.y(), arrow.width(), arrow.height(), CRUSHING);
    }
}
