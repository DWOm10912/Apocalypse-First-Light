package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.menu.ThermalGeneratorMenu;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayout;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayouts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public final class ThermalGeneratorScreen extends AbstractContainerScreen<ThermalGeneratorMenu> {
    private static final MachineGuiLayout LAYOUT = MachineGuiLayouts.thermalGenerator();
    private static final ResourceLocation FURNACE_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/furnace.png");
    private static final ResourceLocation ENERGY_BAR_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/energy_bar.png");
    private static final ResourceLocation ENERGY_FILL_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/energy_fill_green_tile.png");

    public ThermalGeneratorScreen(ThermalGeneratorMenu menu, Inventory inventory, Component title) {
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
        var fluidBar = LAYOUT.element("input_fluid_bar");
        if (isHovering(fluidBar.x(), fluidBar.y(), fluidBar.width(), fluidBar.height(), mouseX, mouseY)) {
            MachineFluidBarRenderer.renderFluidTooltip(graphics, font, mouseX, mouseY,
                    "gui.apocalypse_firstlight.thermal_generator.fuel_tank",
                    menu.getInputFluid(), menu.getInputFluidAmount(), menu.getInputFluidCapacity());
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
        MachineFluidBarRenderer.drawFluidBar(graphics, LAYOUT, left, top,
                "input_fluid_bar", "input_fluid_fill", menu.getInputFluid(),
                menu.getInputFluidAmount(), menu.getInputFluidCapacity());
        MachineGuiLayout.Element fuelSlot = LAYOUT.element("fuel_slot");
        MachineGuiRenderHelper.drawSlotFrame(graphics, left + fuelSlot.x(), top + fuelSlot.y());
        MachineGuiRenderHelper.drawGridSlotFrames(graphics, left, top, LAYOUT.playerInventory());
        MachineGuiRenderHelper.drawGridSlotFrames(graphics, left, top, LAYOUT.hotbar());

        MachineGuiLayout.Element fireIcon = LAYOUT.element("fire_icon");
        MachineGuiLayout.Element progressArrow = LAYOUT.element("progress_arrow");
        MachineGuiLayout.Element energyBar = LAYOUT.element("energy_bar");
        MachineGuiLayout.Element energyFill = LAYOUT.element("energy_fill");

        graphics.blit(FURNACE_TEXTURE,
                left + fireIcon.x(), top + fireIcon.y(),
                56, 36, 14, 14);
        graphics.blit(FURNACE_TEXTURE,
                left + progressArrow.x(), top + progressArrow.y(),
                79, 34, 24, 17);

        int fireProgress = menu.getFireProgress();
        if (fireProgress > 0) {
            graphics.blit(FURNACE_TEXTURE,
                    left + fireIcon.x(),
                    top + fireIcon.y() + 12 - fireProgress,
                    176,
                    12 - fireProgress,
                    14,
                    fireProgress + 1);
        }

        int arrowProgress = menu.getArrowProgress();
        if (arrowProgress > 0) {
            graphics.blit(FURNACE_TEXTURE,
                    left + progressArrow.x(), top + progressArrow.y(),
                    176, 14,
                    arrowProgress, 16);
        }

        graphics.blit(ENERGY_BAR_TEXTURE,
                left + energyBar.x(), top + energyBar.y(),
                0, 0,
                energyBar.width(), energyBar.height(),
                energyBar.width(), energyBar.height());
        long gameTime = minecraft == null || minecraft.level == null
                ? 0L
                : minecraft.level.getGameTime();
        MachineGuiRenderHelper.drawAnimatedEnergyFill(graphics, ENERGY_FILL_TEXTURE,
                left, top, energyFill, menu.getStoredEnergy(), menu.getEnergyCapacity(), gameTime);
    }
}
