package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.menu.CrusherMenu;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayout;
import com.antaurora.apofirstlight.menu.layout.MachineGuiLayouts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public final class CrusherScreen extends AbstractContainerScreen<CrusherMenu> {
    private static final MachineGuiLayout LAYOUT = MachineGuiLayouts.crusher();
    private static final ResourceLocation FURNACE_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/furnace.png");
    private static final ResourceLocation ENERGY_BAR_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/energy_bar.png");
    private static final ResourceLocation ENERGY_FILL_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/energy_fill_green_tile.png");

    public CrusherScreen(CrusherMenu menu, Inventory inventory, Component title) {
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

        MachineGuiLayout.Element inputSlot = LAYOUT.element("input_slot");
        MachineGuiRenderHelper.drawSlotFrame(graphics, left + inputSlot.x(), top + inputSlot.y());
        for (MachineGuiLayout.Element outputSlot : LAYOUT.outputSlots()) {
            MachineGuiRenderHelper.drawSlotFrame(graphics, left + outputSlot.x(), top + outputSlot.y());
        }
        MachineGuiRenderHelper.drawGridSlotFrames(graphics, left, top, LAYOUT.playerInventory());
        MachineGuiRenderHelper.drawGridSlotFrames(graphics, left, top, LAYOUT.hotbar());

        MachineGuiLayout.Element progressArrow = LAYOUT.element("progress_arrow");
        graphics.blit(FURNACE_TEXTURE,
                left + progressArrow.x(), top + progressArrow.y(),
                79, 34, 24, 17);
        int arrowProgress = menu.getArrowProgress();
        if (arrowProgress > 0) {
            graphics.blit(FURNACE_TEXTURE,
                    left + progressArrow.x(), top + progressArrow.y(),
                    176, 14, arrowProgress, 16);
        }

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
