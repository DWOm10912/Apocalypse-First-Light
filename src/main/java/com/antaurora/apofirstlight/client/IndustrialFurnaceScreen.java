package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.IndustrialFurnaceBlockEntity;
import com.antaurora.apofirstlight.menu.IndustrialFurnaceMenu;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayout;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayouts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public final class IndustrialFurnaceScreen extends AbstractContainerScreen<IndustrialFurnaceMenu> {
    private static final MachineGuiLayout LAYOUT = MachineGuiLayouts.industrialFurnace();
    private static final ResourceLocation FURNACE_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/furnace.png");
    private static final ResourceLocation ENERGY_BAR_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/energy_bar.png");
    private static final ResourceLocation ENERGY_FILL_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/energy_fill_green_tile.png");
    private static final ResourceLocation AUTO_BALANCE_BUTTON_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID,
                    "textures/gui/industrial_furnace_auto_balance_button.png");

    private ImageButton autoBalanceButton;

    public IndustrialFurnaceScreen(IndustrialFurnaceMenu menu, Inventory inventory, Component title) {
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
        MachineGuiLayout.Element button = LAYOUT.element("auto_balance_button");
        autoBalanceButton = addRenderableWidget(new ImageButton(
                leftPos + button.x(), topPos + button.y(),
                button.width(), button.height(),
                0, 0, 0,
                AUTO_BALANCE_BUTTON_TEXTURE,
                button.width(), button.height(),
                pressed -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(
                                menu.containerId, IndustrialFurnaceMenu.AUTO_BALANCE_BUTTON_ID);
                    }
                },
                Component.translatable(
                        "gui.apocalypse_firstlight.industrial_furnace.auto_balance.disabled")));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (autoBalanceButton != null) {
            autoBalanceButton.setMessage(Component.translatable(menu.isAutoBalanceEnabled()
                    ? "gui.apocalypse_firstlight.industrial_furnace.auto_balance.enabled"
                    : "gui.apocalypse_firstlight.industrial_furnace.auto_balance.disabled"));
            if (menu.isAutoBalanceEnabled()) {
                graphics.fill(autoBalanceButton.getX() + 14, autoBalanceButton.getY() + 2,
                        autoBalanceButton.getX() + 16, autoBalanceButton.getY() + 4,
                        0xFF55E88A);
            }
        }
        renderTooltip(graphics, mouseX, mouseY);

        MachineGuiLayout.Element energyBar = LAYOUT.element("energy_bar");
        if (isHovering(energyBar.x(), energyBar.y(), energyBar.width(), energyBar.height(), mouseX, mouseY)) {
            String energy = String.format(Locale.ROOT, "%,d FE / %,d FE",
                    menu.getStoredEnergy(), menu.getEnergyCapacity());
            graphics.renderTooltip(font, Component.literal(energy), mouseX, mouseY);
        }
        if (autoBalanceButton != null && autoBalanceButton.isMouseOver(mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, java.util.List.of(
                    Component.translatable(menu.isAutoBalanceEnabled()
                            ? "gui.apocalypse_firstlight.industrial_furnace.auto_balance.enabled"
                            : "gui.apocalypse_firstlight.industrial_furnace.auto_balance.disabled"),
                    Component.translatable(
                            "gui.apocalypse_firstlight.industrial_furnace.auto_balance.description")),
                    mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        MachineGuiRenderHelper.drawVanillaStylePanel(graphics, left, top, imageWidth, imageHeight);

        for (int lane = 0; lane < IndustrialFurnaceBlockEntity.LANE_COUNT; lane++) {
            MachineGuiLayout.Element inputSlot = LAYOUT.element("input_slot_" + lane);
            MachineGuiRenderHelper.drawSlotFrame(graphics, left + inputSlot.x(), top + inputSlot.y());
            MachineGuiLayout.Element outputSlot = LAYOUT.outputSlots().get(lane);
            MachineGuiRenderHelper.drawSlotFrame(graphics, left + outputSlot.x(), top + outputSlot.y());

            MachineGuiLayout.Element arrow = LAYOUT.element("progress_arrow_" + lane);
            graphics.blit(FURNACE_TEXTURE, left + arrow.x(), top + arrow.y(), 79, 34, 24, 17);
            int arrowProgress = menu.getArrowProgress(lane);
            if (arrowProgress > 0) {
                graphics.blit(FURNACE_TEXTURE,
                        left + arrow.x(), top + arrow.y(),
                        176, 14, arrowProgress, 16);
            }
        }

        MachineGuiRenderHelper.drawGridSlotFrames(graphics, left, top, LAYOUT.playerInventory());
        MachineGuiRenderHelper.drawGridSlotFrames(graphics, left, top, LAYOUT.hotbar());

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
}
