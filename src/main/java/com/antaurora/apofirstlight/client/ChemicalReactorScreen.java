package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.menu.ChemicalReactorMenu;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayout;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayouts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.Locale;

public final class ChemicalReactorScreen extends AbstractContainerScreen<ChemicalReactorMenu> {
    private static final MachineGuiLayout LAYOUT = MachineGuiLayouts.chemicalReactor();
    private static final ResourceLocation ENERGY_BAR_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/energy_bar.png");
    private static final ResourceLocation ENERGY_FILL_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/energy_fill_green_tile.png");
    private static final ResourceLocation FLUID_BAR_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/fluid_bar.png");
    private static final int FLUID_BAR_TICK_U = 8;
    private static final int FLUID_BAR_TICK_WIDTH = 3;
    private static final int[] FLUID_BAR_TICK_ROWS = {8, 14, 20, 26, 32, 38, 44};

    public ChemicalReactorScreen(ChemicalReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = LAYOUT.gui().width();
        imageHeight = LAYOUT.gui().height();
        titleLabelX = LAYOUT.title().x();
        titleLabelY = LAYOUT.title().y();
        inventoryLabelX = LAYOUT.inventoryLabel().x();
        inventoryLabelY = LAYOUT.inventoryLabel().y();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        MachineGuiLayout.Element inputBar = LAYOUT.element("input_fluid_bar");
        if (isHovering(inputBar.x(), inputBar.y(), inputBar.width(), inputBar.height(), mouseX, mouseY)) {
            renderFluidTooltip(graphics, mouseX, mouseY,
                    "gui.apocalypse_firstlight.chemical_reactor.input_tank",
                    menu.getInputFluid(), menu.getInputFluidAmount(), menu.getInputFluidCapacity());
            return;
        }
        MachineGuiLayout.Element wasteBar = LAYOUT.element("waste_fluid_bar");
        if (isHovering(wasteBar.x(), wasteBar.y(), wasteBar.width(), wasteBar.height(), mouseX, mouseY)) {
            renderFluidTooltip(graphics, mouseX, mouseY,
                    "gui.apocalypse_firstlight.chemical_reactor.waste_tank",
                    menu.getWasteFluid(), menu.getWasteFluidAmount(), menu.getWasteFluidCapacity());
            return;
        }
        MachineGuiLayout.Element energyBar = LAYOUT.element("energy_bar");
        if (isHovering(energyBar.x(), energyBar.y(), energyBar.width(), energyBar.height(), mouseX, mouseY)) {
            String energy = String.format(Locale.ROOT, "%,d FE / %,d FE",
                    menu.getStoredEnergy(), menu.getEnergyCapacity());
            graphics.renderTooltip(font, Component.literal(energy), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        MachineGuiRenderHelper.drawVanillaStylePanel(graphics, left, top, imageWidth, imageHeight);
        MachineGuiRenderHelper.drawGridSlotFrames(graphics, left, top, LAYOUT.playerInventory());
        MachineGuiRenderHelper.drawGridSlotFrames(graphics, left, top, LAYOUT.hotbar());

        drawFluidBar(graphics, left, top, "input_fluid_bar", "input_fluid_fill",
                menu.getInputFluid(), menu.getInputFluidAmount(), menu.getInputFluidCapacity());
        drawFluidBar(graphics, left, top, "waste_fluid_bar", "waste_fluid_fill",
                menu.getWasteFluid(), menu.getWasteFluidAmount(), menu.getWasteFluidCapacity());

        MachineGuiLayout.Element energyBar = LAYOUT.element("energy_bar");
        graphics.blit(ENERGY_BAR_TEXTURE,
                left + energyBar.x(), top + energyBar.y(),
                0, 0, energyBar.width(), energyBar.height(),
                energyBar.width(), energyBar.height());
        MachineGuiLayout.Element energyFill = LAYOUT.element("energy_fill");
        long gameTime = minecraft == null || minecraft.level == null
                ? 0L
                : minecraft.level.getGameTime();
        MachineGuiRenderHelper.drawAnimatedEnergyFill(graphics, ENERGY_FILL_TEXTURE,
                left, top, energyFill, menu.getStoredEnergy(), menu.getEnergyCapacity(), gameTime);
    }

    private void drawFluidBar(GuiGraphics graphics, int left, int top,
                              String barName, String fillName, FluidStack fluid,
                              int amount, int capacity) {
        MachineGuiLayout.Element bar = LAYOUT.element(barName);
        MachineGuiLayout.Element fill = LAYOUT.element(fillName);
        graphics.blit(FLUID_BAR_TEXTURE,
                left + bar.x(), top + bar.y(),
                0, 0, bar.width(), bar.height(), bar.width(), bar.height());
        MachineGuiRenderHelper.drawFluidFill(graphics, left, top, fill, fluid, amount, capacity);
        for (int tickRow : FLUID_BAR_TICK_ROWS) {
            graphics.blit(FLUID_BAR_TEXTURE,
                    left + bar.x() + FLUID_BAR_TICK_U, top + bar.y() + tickRow,
                    FLUID_BAR_TICK_U, tickRow, FLUID_BAR_TICK_WIDTH, 1,
                    bar.width(), bar.height());
        }
    }

    private void renderFluidTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                                    String titleKey, FluidStack fluid, int amount, int capacity) {
        Component fluidName = fluid.isEmpty()
                ? Component.translatable("gui.apocalypse_firstlight.chemical_reactor.empty_fluid")
                : fluid.getDisplayName();
        graphics.renderComponentTooltip(font, List.of(
                Component.translatable(titleKey),
                Component.translatable("tooltip.apocalypse_firstlight.stored_fluid", fluidName),
                Component.translatable("tooltip.apocalypse_firstlight.stored_fluid_amount",
                        String.format(Locale.ROOT, "%,d", amount),
                        String.format(Locale.ROOT, "%,d", capacity))), mouseX, mouseY);
    }
}
