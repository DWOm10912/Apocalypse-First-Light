package com.antaurora.apofirstlight.compat.jei;

import com.antaurora.apofirstlight.client.ClientProcessingMachineBalanceData;
import com.antaurora.apofirstlight.recipe.ChemicalReactingRecipe;
import com.antaurora.apofirstlight.registry.AflBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import java.math.BigDecimal;
import java.util.Locale;

public final class ChemicalReactorRecipeCategory implements IRecipeCategory<ChemicalReactingRecipe> {
    private static final int WIDTH = 148;
    private static final int HEIGHT = 70;
    private static final int ITEM_INPUT_X = 4;
    private static final int FLUID_INPUT_X = 26;
    private static final int ARROW_X = 52;
    private static final int ITEM_OUTPUT_X = 90;
    private static final int WASTE_OUTPUT_X = 112;
    private static final int SLOT_Y = 17;
    private static final int PROCESSING_TIME_Y = 49;
    private static final int TOTAL_ENERGY_Y = 59;

    private final IDrawable icon;
    private final IDrawable arrowBackground;
    private final IDrawable animatedArrow;

    public ChemicalReactorRecipeCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemLike(AflBlocks.CHEMICAL_REACTOR.get());
        arrowBackground = guiHelper.getRecipeArrow();
        animatedArrow = guiHelper.createAnimatedRecipeArrow(100);
    }

    @Override
    public RecipeType<ChemicalReactingRecipe> getRecipeType() {
        return AflJeiPlugin.CHEMICAL_REACTING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.apocalypse_firstlight.category.chemical_reacting");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ChemicalReactingRecipe recipe,
                          IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, ITEM_INPUT_X, SLOT_Y)
                .setStandardSlotBackground()
                .addIngredients(recipe.itemInput());
        FluidStack fluidInput = recipe.fluidInput();
        builder.addSlot(RecipeIngredientRole.INPUT, FLUID_INPUT_X, SLOT_Y)
                .setStandardSlotBackground()
                .setFluidRenderer(fluidInput.getAmount(), false, 16, 16)
                .addFluidStack(fluidInput.getFluid(), fluidInput.getAmount());
        builder.addSlot(RecipeIngredientRole.OUTPUT, ITEM_OUTPUT_X, SLOT_Y)
                .setStandardSlotBackground()
                .addItemStack(recipe.itemOutput());
        FluidStack wasteOutput = recipe.wasteOutput();
        builder.addSlot(RecipeIngredientRole.OUTPUT, WASTE_OUTPUT_X, SLOT_Y)
                .setStandardSlotBackground()
                .setFluidRenderer(wasteOutput.getAmount(), false, 16, 16)
                .addFluidStack(wasteOutput.getFluid(), wasteOutput.getAmount());
    }

    @Override
    public void draw(ChemicalReactingRecipe recipe,
                     mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        arrowBackground.draw(graphics, ARROW_X, SLOT_Y);
        animatedArrow.draw(graphics, ARROW_X, SLOT_Y);
        Component processingTime = Component.translatable(
                "jei.apocalypse_firstlight.processing_time", formatSeconds(recipe.processingTime()));
        graphics.drawString(Minecraft.getInstance().font, processingTime,
                8, PROCESSING_TIME_Y, 0xFF404040, false);
        int workFePerTick = ClientProcessingMachineBalanceData.chemicalReactorWorkFePerTick();
        if (workFePerTick > 0) {
            Component totalEnergy = Component.translatable("jei.apocalypse_firstlight.total_energy",
                    String.format(Locale.ROOT, "%,d", (long) workFePerTick * recipe.processingTime()));
            graphics.drawString(Minecraft.getInstance().font, totalEnergy,
                    8, TOTAL_ENERGY_Y, 0xFF404040, false);
        }
    }

    @Override
    public ResourceLocation getRegistryName(ChemicalReactingRecipe recipe) {
        return recipe.getId();
    }

    private static String formatSeconds(int ticks) {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20))
                .stripTrailingZeros().toPlainString();
    }
}
