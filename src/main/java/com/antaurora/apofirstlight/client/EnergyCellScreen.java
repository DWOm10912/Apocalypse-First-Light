package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.energy.EnergyCellMode;
import com.antaurora.apofirstlight.menu.EnergyCellMenu;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayout;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayouts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;
import java.util.List;

public final class EnergyCellScreen extends AbstractContainerScreen<EnergyCellMenu> {
    private static final MachineGuiLayout LAYOUT = MachineGuiLayouts.energyCell();
    private static final ResourceLocation ENERGY_BAR_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/energy_cell_bar.png");
    private static final ResourceLocation ENERGY_FILL_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/energy_fill_green_tile.png");
    private static final ResourceLocation CHARGE_MODE_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/energy_cell_mode_charge_18.png");
    private static final ResourceLocation DISCHARGE_MODE_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/energy_cell_mode_discharge_18.png");

    private ImageButton chargeModeButton;
    private ImageButton dischargeModeButton;

    public EnergyCellScreen(EnergyCellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = LAYOUT.gui().width();
        imageHeight = LAYOUT.gui().height();
        titleLabelX = LAYOUT.title().x();
        titleLabelY = LAYOUT.title().y();
        inventoryLabelX = LAYOUT.inventoryLabel().x();
        inventoryLabelY = LAYOUT.inventoryLabel().y();
    }

    @Override
    protected void init() {
        super.init();
        MachineGuiLayout.Element button = LAYOUT.element("mode_button");
        chargeModeButton = addModeButton(button, CHARGE_MODE_TEXTURE,
                "gui.apocalypse_firstlight.energy_cell.mode.charge.title");
        dischargeModeButton = addModeButton(button, DISCHARGE_MODE_TEXTURE,
                "gui.apocalypse_firstlight.energy_cell.mode.discharge.title");
        updateModeButtons();
    }

    private ImageButton addModeButton(MachineGuiLayout.Element button, ResourceLocation texture,
                                      String narrationKey) {
        return addRenderableWidget(new ImageButton(
                leftPos + button.x(), topPos + button.y(),
                button.width(), button.height(),
                0, 0, 0,
                texture, button.width(), button.height(),
                pressed -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(
                                menu.containerId, EnergyCellMenu.MODE_BUTTON_ID);
                    }
                },
                Component.translatable(narrationKey)));
    }

    private void updateModeButtons() {
        boolean charge = menu.getMode() == EnergyCellMode.CHARGE;
        if (chargeModeButton != null) {
            chargeModeButton.visible = charge;
        }
        if (dischargeModeButton != null) {
            dischargeModeButton.visible = !charge;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateModeButtons();
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        MachineGuiLayout.Element storageBar = LAYOUT.element("storage_bar");
        if (isHovering(storageBar.x(), storageBar.y(), storageBar.width(), storageBar.height(), mouseX, mouseY)) {
            String energy = String.format(Locale.ROOT, "%,d FE / %,d FE",
                    menu.getStoredEnergy(), menu.getEnergyCapacity());
            graphics.renderTooltip(font, Component.literal(energy), mouseX, mouseY);
        }

        ImageButton activeModeButton = menu.getMode() == EnergyCellMode.CHARGE
                ? chargeModeButton : dischargeModeButton;
        if (activeModeButton != null && activeModeButton.isMouseOver(mouseX, mouseY)) {
            boolean charge = menu.getMode() == EnergyCellMode.CHARGE;
            String keyRoot = charge
                    ? "gui.apocalypse_firstlight.energy_cell.mode.charge"
                    : "gui.apocalypse_firstlight.energy_cell.mode.discharge";
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable(keyRoot + ".title"),
                    Component.translatable(keyRoot + ".description")), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;

        MachineGuiRenderHelper.drawVanillaStylePanel(graphics, left, top, imageWidth, imageHeight);
        MachineGuiRenderHelper.drawGridSlotFrames(graphics, left, top, LAYOUT.playerInventory());
        MachineGuiRenderHelper.drawGridSlotFrames(graphics, left, top, LAYOUT.hotbar());

        MachineGuiLayout.Element storageBar = LAYOUT.element("storage_bar");
        MachineGuiLayout.Element storageFill = LAYOUT.element("storage_fill");

        graphics.blit(ENERGY_BAR_TEXTURE,
                left + storageBar.x(), top + storageBar.y(),
                0, 0,
                storageBar.width(), storageBar.height(),
                storageBar.width(), storageBar.height());
        long gameTime = minecraft == null || minecraft.level == null
                ? 0L
                : minecraft.level.getGameTime();
        MachineGuiRenderHelper.drawAnimatedEnergyFill(graphics, ENERGY_FILL_TEXTURE,
                left, top, storageFill, menu.getStoredEnergy(), menu.getEnergyCapacity(), gameTime);
    }
}
