package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.menu.ThermalGeneratorMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public final class ThermalGeneratorScreen extends AbstractContainerScreen<ThermalGeneratorMenu> {
    private static final ResourceLocation FURNACE_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/furnace.png");
    private static final ResourceLocation ENERGY_BAR_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/energy_bar.png");
    private static final ResourceLocation ENERGY_FILL_TEXTURE =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/common/energy_fill_green_tile.png");

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    private static final int BURN_ICON_X = 67;
    private static final int BURN_ICON_Y = 36;
    private static final int ARROW_X = 91;
    private static final int ARROW_Y = 35;
    private static final int ENERGY_BAR_X = 150;
    private static final int ENERGY_BAR_Y = 18;
    private static final int ENERGY_BAR_WIDTH = 12;
    private static final int ENERGY_BAR_HEIGHT = 54;
    private static final int ENERGY_FILL_X = ENERGY_BAR_X + 2;
    private static final int ENERGY_FILL_Y = ENERGY_BAR_Y + 3;
    private static final int ENERGY_FILL_WIDTH = 8;
    private static final int ENERGY_FILL_HEIGHT = 48;
    private static final int ENERGY_FILL_FRAME_SIZE = 8;
    private static final int ENERGY_FILL_FRAME_COUNT = 6;
    private static final int ENERGY_FILL_FRAME_TICKS = 8;
    private static final int ENERGY_FILL_TEXTURE_WIDTH = 8;
    private static final int ENERGY_FILL_TEXTURE_HEIGHT = 48;

    public ThermalGeneratorScreen(ThermalGeneratorMenu menu, Inventory inventory, Component title) {
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
        drawSlotFrame(graphics, left + ThermalGeneratorMenu.FUEL_SLOT_X,
                top + ThermalGeneratorMenu.FUEL_SLOT_Y);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlotFrame(graphics,
                        left + ThermalGeneratorMenu.PLAYER_INVENTORY_X + column * 18,
                        top + ThermalGeneratorMenu.PLAYER_INVENTORY_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlotFrame(graphics,
                    left + ThermalGeneratorMenu.PLAYER_INVENTORY_X + column * 18,
                    top + ThermalGeneratorMenu.HOTBAR_Y);
        }

        graphics.blit(FURNACE_TEXTURE,
                left + BURN_ICON_X, top + BURN_ICON_Y,
                56, 36, 14, 14);
        graphics.blit(FURNACE_TEXTURE,
                left + ARROW_X, top + ARROW_Y,
                79, 34, 24, 17);

        int fireProgress = menu.getFireProgress();
        if (fireProgress > 0) {
            graphics.blit(FURNACE_TEXTURE,
                    left + BURN_ICON_X,
                    top + BURN_ICON_Y + 12 - fireProgress,
                    176,
                    12 - fireProgress,
                    14,
                    fireProgress + 1);
        }

        int arrowProgress = menu.getArrowProgress();
        if (arrowProgress > 0) {
            graphics.blit(FURNACE_TEXTURE,
                    left + ARROW_X, top + ARROW_Y,
                    176, 14,
                    arrowProgress, 16);
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
        long gameTime = minecraft == null || minecraft.level == null
                ? 0L
                : minecraft.level.getGameTime();
        int frameIndex = (int) ((gameTime / ENERGY_FILL_FRAME_TICKS) % ENERGY_FILL_FRAME_COUNT);
        int frameV = frameIndex * ENERGY_FILL_FRAME_SIZE;
        int cursorY = ENERGY_FILL_Y + ENERGY_FILL_HEIGHT;
        int remaining = fullRows;

        while (remaining > 0) {
            int tileHeight = Math.min(ENERGY_FILL_FRAME_SIZE, remaining);
            cursorY -= tileHeight;
            graphics.blit(ENERGY_FILL_TEXTURE,
                    left + ENERGY_FILL_X, top + cursorY,
                    0, frameV + ENERGY_FILL_FRAME_SIZE - tileHeight,
                    ENERGY_FILL_WIDTH, tileHeight,
                    ENERGY_FILL_TEXTURE_WIDTH, ENERGY_FILL_TEXTURE_HEIGHT);
            remaining -= tileHeight;
        }

        if (fractionalRowAlpha > 0.0F && fullRows < ENERGY_FILL_HEIGHT) {
            int rowY = ENERGY_FILL_Y + ENERGY_FILL_HEIGHT - fullRows - 1;
            int tileV = frameV + ENERGY_FILL_FRAME_SIZE - 1 - fullRows % ENERGY_FILL_FRAME_SIZE;
            try {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, fractionalRowAlpha);
                graphics.blit(ENERGY_FILL_TEXTURE,
                        left + ENERGY_FILL_X, top + rowY,
                        0, tileV,
                        ENERGY_FILL_WIDTH, 1,
                        ENERGY_FILL_TEXTURE_WIDTH, ENERGY_FILL_TEXTURE_HEIGHT);
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
