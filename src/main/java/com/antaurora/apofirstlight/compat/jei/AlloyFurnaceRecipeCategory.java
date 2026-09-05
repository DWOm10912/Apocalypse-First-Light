package com.antaurora.apofirstlight.compat.jei;

import com.antaurora.apofirstlight.client.ClientAlloyFurnaceBalanceData;
import com.antaurora.apofirstlight.recipe.AlloyingRecipe;
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
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class AlloyFurnaceRecipeCategory implements IRecipeCategory<AlloyingRecipe> {
    private static final int WIDTH = 148;
    private static final int HEIGHT = 70;
    private static final int INPUT_A_X = 18;
    private static final int INPUT_B_X = 42;
    private static final int INPUT_Y = 18;
    private static final int ARROW_X = 70;
    private static final int ARROW_Y = 18;
    private static final int OUTPUT_X = 108;
    private static final int OUTPUT_Y = 18;
    private static final int PROCESSING_TIME_X = 8;
    private static final int PROCESSING_TIME_Y = 49;
    private static final int TOTAL_ENERGY_X = 8;
    private static final int TOTAL_ENERGY_Y = 59;

    private final IDrawable icon;
    private final IDrawable arrowBackground;
    private final IDrawable animatedArrow;

    public AlloyFurnaceRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(AflBlocks.ALLOY_FURNACE.get());
        this.arrowBackground = guiHelper.getRecipeArrow();
        this.animatedArrow = guiHelper.createAnimatedRecipeArrow(100);
    }

    @Override
    public RecipeType<AlloyingRecipe> getRecipeType() {
        return AflJeiPlugin.ALLOYING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.apocalypse_firstlight.category.alloying");
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
    public void setRecipe(IRecipeLayoutBuilder builder, AlloyingRecipe recipe, IFocusGroup focuses) {
        List<AlloyingRecipe.CountedIngredient> ingredients = recipe.countedIngredients();
        if (!ingredients.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_A_X, INPUT_Y)
                    .setStandardSlotBackground()
                    .addItemStacks(displayStacks(ingredients.get(0)));
        }
        if (ingredients.size() > 1) {
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_B_X, INPUT_Y)
                    .setStandardSlotBackground()
                    .addItemStacks(displayStacks(ingredients.get(1)));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setStandardSlotBackground()
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(AlloyingRecipe recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        arrowBackground.draw(graphics, ARROW_X, ARROW_Y);
        animatedArrow.draw(graphics, ARROW_X, ARROW_Y);

        Component processingTime = Component.translatable(
                "jei.apocalypse_firstlight.processing_time", formatSeconds(recipe.processingTime()));
        graphics.drawString(Minecraft.getInstance().font, processingTime,
                PROCESSING_TIME_X, PROCESSING_TIME_Y, 0xFF404040, false);

        int workFePerTick = ClientAlloyFurnaceBalanceData.workFePerTick();
        if (workFePerTick > 0) {
            long totalEnergyFe = (long) recipe.processingTime() * workFePerTick;
            Component totalEnergy = Component.translatable(
                    "jei.apocalypse_firstlight.total_energy",
                    String.format(Locale.ROOT, "%,d", totalEnergyFe));
            graphics.drawString(Minecraft.getInstance().font, totalEnergy,
                    TOTAL_ENERGY_X, TOTAL_ENERGY_Y, 0xFF404040, false);
        }
    }

    @Override
    public ResourceLocation getRegistryName(AlloyingRecipe recipe) {
        return recipe.getId();
    }

    private static List<ItemStack> displayStacks(AlloyingRecipe.CountedIngredient ingredient) {
        return Arrays.stream(ingredient.ingredient().getItems())
                .map(stack -> {
                    ItemStack display = stack.copy();
                    display.setCount(ingredient.count());
                    return display;
                })
                .toList();
    }

    private static String formatSeconds(int ticks) {
        return BigDecimal.valueOf(ticks)
                .divide(BigDecimal.valueOf(20))
                .stripTrailingZeros()
                .toPlainString();
    }
}
