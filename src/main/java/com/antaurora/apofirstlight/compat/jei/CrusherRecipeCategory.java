package com.antaurora.apofirstlight.compat.jei;

import com.antaurora.apofirstlight.client.ClientCrusherBalanceData;
import com.antaurora.apofirstlight.recipe.CrushingRecipe;
import com.antaurora.apofirstlight.registry.AflBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
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

public final class CrusherRecipeCategory implements IRecipeCategory<CrushingRecipe> {
    private static final int WIDTH = 148;
    private static final int HEIGHT = 70;
    private static final int INPUT_X = 8;
    private static final int INPUT_Y = 17;
    private static final int ARROW_X = 38;
    private static final int ARROW_Y = 17;
    private static final int OUTPUT_X = 76;
    private static final int OUTPUT_Y = 8;
    private static final int SLOT_SPACING = 18;
    private static final int PROCESSING_TIME_X = 8;
    private static final int PROCESSING_TIME_Y = 49;
    private static final int TOTAL_ENERGY_X = 8;
    private static final int TOTAL_ENERGY_Y = 59;

    private final IDrawable icon;
    private final IDrawable arrowBackground;
    private final IDrawable animatedArrow;

    public CrusherRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(AflBlocks.CRUSHER.get());
        this.arrowBackground = guiHelper.getRecipeArrow();
        this.animatedArrow = guiHelper.createAnimatedRecipeArrow(100);
    }

    @Override
    public RecipeType<CrushingRecipe> getRecipeType() {
        return AflJeiPlugin.CRUSHING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.apocalypse_firstlight.category.crushing");
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
    public void setRecipe(IRecipeLayoutBuilder builder, CrushingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, INPUT_Y)
                .setStandardSlotBackground()
                .addIngredients(recipe.getIngredients().get(0));

        for (int index = 0; index < recipe.results().size(); index++) {
            CrushingRecipe.Result result = recipe.results().get(index);
            int x = OUTPUT_X + index % 3 * SLOT_SPACING;
            int y = OUTPUT_Y + index / 3 * SLOT_SPACING;
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                    .setStandardSlotBackground()
                    .addItemStack(result.displayStack());
            if (result.hasVariableCount()) {
                slot.addRichTooltipCallback((slotView, tooltip) -> tooltip.add(Component.translatable(
                        "jei.apocalypse_firstlight.output_range", result.minCount(), result.maxCount())));
            }
        }
    }

    @Override
    public void draw(CrushingRecipe recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        arrowBackground.draw(graphics, ARROW_X, ARROW_Y);
        animatedArrow.draw(graphics, ARROW_X, ARROW_Y);
        Component processingTime = Component.translatable(
                "jei.apocalypse_firstlight.processing_time", formatSeconds(recipe.processingTime()));
        graphics.drawString(Minecraft.getInstance().font, processingTime,
                PROCESSING_TIME_X, PROCESSING_TIME_Y, 0xFF404040, false);

        int workFePerTick = ClientCrusherBalanceData.workFePerTick();
        if (workFePerTick > 0) {
            long totalEnergyFe = (long) workFePerTick * recipe.processingTime();
            Component totalEnergy = Component.translatable(
                    "jei.apocalypse_firstlight.total_energy",
                    String.format(Locale.ROOT, "%,d", totalEnergyFe));
            graphics.drawString(Minecraft.getInstance().font, totalEnergy,
                    TOTAL_ENERGY_X, TOTAL_ENERGY_Y, 0xFF404040, false);
        }
    }

    @Override
    public ResourceLocation getRegistryName(CrushingRecipe recipe) {
        return recipe.getId();
    }

    private static String formatSeconds(int ticks) {
        return BigDecimal.valueOf(ticks)
                .divide(BigDecimal.valueOf(20))
                .stripTrailingZeros()
                .toPlainString();
    }
}
