package com.antaurora.apofirstlight.compat.jei;

import com.antaurora.apofirstlight.energy.ThermalFuelDefinitions.DisplayFuel;
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

public final class ThermalGenerationCategory implements IRecipeCategory<DisplayFuel> {
    private final IDrawable icon, arrow;
    public ThermalGenerationCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemLike(AflBlocks.THERMAL_GENERATOR.get());
        arrow = helper.getRecipeArrow();
    }
    @Override public RecipeType<DisplayFuel> getRecipeType() { return AflJeiPlugin.THERMAL_GENERATION; }
    @Override public Component getTitle() { return Component.translatable("jei.apocalypse_firstlight.category.thermal_generation"); }
    @Override public int getWidth() { return 144; }
    @Override public int getHeight() { return 38; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public ResourceLocation getRegistryName(DisplayFuel fuel) { return fuel.id(); }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, DisplayFuel fuel, IFocusGroup focuses) {
        var slot = builder.addSlot(RecipeIngredientRole.INPUT, 5, 10).setStandardSlotBackground();
        if (!fuel.item().isEmpty()) slot.addItemStack(fuel.item());
        else slot.setFluidRenderer(fuel.fluid().getAmount(), false, 16, 16)
                .addFluidStack(fuel.fluid().getFluid(), fuel.fluid().getAmount());
    }
    @Override public void draw(DisplayFuel fuel, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                               GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 32, 10);
        graphics.drawString(Minecraft.getInstance().font,
                String.format(java.util.Locale.ROOT, "%,d FE", fuel.energyFe()), 65, 14, 0xFF404040, false);
    }
}
