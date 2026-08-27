package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.menu.EnergyCellMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public final class EnergyCellScreen extends AbstractContainerScreen<EnergyCellMenu> {
    private static final ResourceLocation ENERGY_BAR_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/energy_cell_bar.png");
    private static final ResourceLocation ENERGY_FILL_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/energy_fill_green_tile.png");

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    private static final int ENERGY_BAR_X = 42;
    private static final int ENERGY_BAR_Y = 15;
    private static final int ENERGY_BAR_WIDTH = 92;
    private static final int ENERGY_BAR_HEIGHT = 54;
    private static final int ENERGY_FILL_X = ENERGY_BAR_X + 2;
    private static final int ENERGY_FILL_Y = ENERGY_BAR_Y + 3;
    private static final int ENERGY_FILL_WIDTH = 88;
    private static final int ENERGY_FILL_HEIGHT = 48;

    public EnergyCellScreen(EnergyCellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = 72;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        if (isHovering(ENERGY_BAR_X, ENERGY_BAR_Y, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, mouseX, mouseY)) {
            String energy = String.format(Locale.ROOT, "%,d FE / %,d FE",
                    menu.getStoredEnergy(), menu.getEnergyCapacity());
            graphics.renderTooltip(font, Component.literal(energy), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;

        drawVanillaStylePanel(graphics, left, top);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlotFrame(graphics,
                        left + EnergyCellMenu.PLAYER_INVENTORY_X + column * 18,
                        top + EnergyCellMenu.PLAYER_INVENTORY_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlotFrame(graphics,
                    left + EnergyCellMenu.PLAYER_INVENTORY_X + column * 18,
                    top + EnergyCellMenu.HOTBAR_Y);
        }

        graphics.blit(ENERGY_BAR_TEXTURE,
                left + ENERGY_BAR_X, top + ENERGY_BAR_Y,
                0, 0,
                ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT,
                ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT);
        drawEnergyFill(graphics, left, top);
    }

    private void drawEnergyFill(GuiGraphics graphics, int left, int top) {
        int capacity = menu.getEnergyCapacity();
        int stored = Mth.clamp(menu.getStoredEnergy(), 0, Math.max(capacity, 0));
        double ratio = capacity <= 0 ? 0.0 : Mth.clamp((double) stored / capacity, 0.0, 1.0);
        double fillPixels = ENERGY_FILL_HEIGHT * ratio;
        int fullRows = Mth.floor(fillPixels);
        float fractionalRowAlpha = (float) (fillPixels - fullRows);
        int cursorY = ENERGY_FILL_Y + ENERGY_FILL_HEIGHT;
        int remaining = fullRows;

        while (remaining > 0) {
            int tileHeight = Math.min(8, remaining);
            cursorY -= tileHeight;
            for (int tileX = 0; tileX < ENERGY_FILL_WIDTH; tileX += 8) {
                graphics.blit(ENERGY_FILL_TEXTURE,
                        left + ENERGY_FILL_X + tileX, top + cursorY,
                        0, 8 - tileHeight,
                        8, tileHeight,
                        8, 8);
            }
            remaining -= tileHeight;
        }

        if (fractionalRowAlpha > 0.0F && fullRows < ENERGY_FILL_HEIGHT) {
            int rowY = ENERGY_FILL_Y + ENERGY_FILL_HEIGHT - fullRows - 1;
            int tileV = 7 - fullRows % 8;
            try {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, fractionalRowAlpha);
                for (int tileX = 0; tileX < ENERGY_FILL_WIDTH; tileX += 8) {
                    graphics.blit(ENERGY_FILL_TEXTURE,
                            left + ENERGY_FILL_X + tileX, top + rowY,
                            0, tileV,
                            8, 1,
                            8, 8);
                }
            } finally {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    private void drawVanillaStylePanel(GuiGraphics graphics, int left, int top) {
        graphics.fill(left, top, left + imageWidth, top + imageHeight, 0xFF373737);
        graphics.fill(left + 1, top + 1, left + imageWidth - 1, top + imageHeight - 1, 0xFFFFFFFF);
        graphics.fill(left + 2, top + 2, left + imageWidth - 2, top + imageHeight - 2, 0xFFC6C6C6);
    }

    private static void drawSlotFrame(GuiGraphics graphics, int itemX, int itemY) {
        graphics.fill(itemX - 1, itemY - 1, itemX + 17, itemY + 17, 0xFF555555);
        graphics.fill(itemX, itemY, itemX + 17, itemY + 17, 0xFFFFFFFF);
        graphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0xFF8B8B8B);
    }
}
