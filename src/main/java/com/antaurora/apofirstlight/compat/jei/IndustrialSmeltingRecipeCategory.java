package com.antaurora.apofirstlight.compat.jei;

import com.antaurora.apofirstlight.client.ClientProcessingMachineBalanceData;
import com.antaurora.apofirstlight.recipe.IndustrialSmeltingRecipe;
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

import java.math.BigDecimal;
import java.util.Locale;

public final class IndustrialSmeltingRecipeCategory implements IRecipeCategory<IndustrialSmeltingRecipe> {
    private static final int WIDTH = 148;
    private static final int HEIGHT = 70;
    private static final int INPUT_X = 8;
    private static final int INPUT_Y = 17;
    private static final int ARROW_X = 38;
    private static final int ARROW_Y = 17;
    private static final int OUTPUT_X = 76;
    private static final int OUTPUT_Y = 17;
    private static final int PROCESSING_TIME_X = 8;
    private static final int PROCESSING_TIME_Y = 49;
    private static final int TOTAL_ENERGY_X = 8;
    private static final int TOTAL_ENERGY_Y = 59;

    private final IDrawable icon;
    private final IDrawable arrowBackground;
    private final IDrawable animatedArrow;

    public IndustrialSmeltingRecipeCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemLike(AflBlocks.INDUSTRIAL_FURNACE.get());
        arrowBackground = guiHelper.getRecipeArrow();
        animatedArrow = guiHelper.createAnimatedRecipeArrow(100);
    }

    @Override
    public RecipeType<IndustrialSmeltingRecipe> getRecipeType() {
        return AflJeiPlugin.INDUSTRIAL_SMELTING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.apocalypse_firstlight.category.industrial_smelting");
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
    public void setRecipe(IRecipeLayoutBuilder builder, IndustrialSmeltingRecipe recipe,
                          IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, INPUT_Y)
                .setStandardSlotBackground()
                .addIngredients(recipe.ingredient());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setStandardSlotBackground()
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(IndustrialSmeltingRecipe recipe,
                     mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        arrowBackground.draw(graphics, ARROW_X, ARROW_Y);
        animatedArrow.draw(graphics, ARROW_X, ARROW_Y);
        Component processingTime = Component.translatable(
                "jei.apocalypse_firstlight.processing_time", formatSeconds(recipe.processingTime()));
        graphics.drawString(Minecraft.getInstance().font, processingTime,
                PROCESSING_TIME_X, PROCESSING_TIME_Y, 0xFF404040, false);
        int workFePerTick = ClientProcessingMachineBalanceData.industrialFurnaceWorkFePerTickPerLane();
        if (workFePerTick > 0) {
            Component totalEnergy = Component.translatable("jei.apocalypse_firstlight.total_energy",
                    String.format(Locale.ROOT, "%,d", (long) workFePerTick * recipe.processingTime()));
            graphics.drawString(Minecraft.getInstance().font, totalEnergy,
                    TOTAL_ENERGY_X, TOTAL_ENERGY_Y, 0xFF404040, false);
        }
    }

    @Override
    public ResourceLocation getRegistryName(IndustrialSmeltingRecipe recipe) {
        return recipe.getId();
    }

    private static String formatSeconds(int ticks) {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20))
                .stripTrailingZeros().toPlainString();
    }
}
