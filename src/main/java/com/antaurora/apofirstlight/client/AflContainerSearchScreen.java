package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.containersearch.AflContainerSearchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Vanilla chest layout plus a DEVELOPMENT PLACEHOLDER search overlay (plain fills and a "?" glyph, no textures).
 * All state comes from {@link AflContainerSearchMenu}; the screen never decides when a slot is revealed.
 */
public final class AflContainerSearchScreen extends AbstractContainerScreen<AflContainerSearchMenu> {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("textures/gui/container/generic_54.png");
    private static final int HIDDEN_FILL = 0xE01A1D21;
    private static final int PROGRESS_FILL = 0x90A8B0B8;
    private static final int HIDDEN_MARK = 0xFF6E757C;
    private static final int REVEAL_FLASH_RGB = 0xD8DEE4;
    private static final float REVEAL_FLASH_TICKS = 6.0F;

    private final int rows;
    private float partialTick;

    public AflContainerSearchScreen(AflContainerSearchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.rows = menu.getRowCount();
        this.imageHeight = 114 + rows * 18;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.partialTick = partialTick;
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        graphics.blit(BACKGROUND, x, y, 0, 0, imageWidth, rows * 18 + 17);
        graphics.blit(BACKGROUND, x, y + rows * 18 + 17, 0, 126, imageWidth, 96);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        int current = menu.isSearching() ? menu.currentSearchSlot() : -1;
        for (int index = 0; index < menu.searchSlotCount(); index++) {
            Slot slot = menu.slots.get(index);
            int x = slot.x;
            int y = slot.y;
            if (!menu.isSlotRevealed(index)) {
                graphics.fill(RenderType.guiOverlay(), x, y, x + 16, y + 16, HIDDEN_FILL);
                if (index == current) {
                    int height = Math.round(16.0F * menu.currentSlotProgress(partialTick));
                    graphics.fill(RenderType.guiOverlay(), x, y + 16 - height, x + 16, y + 16, PROGRESS_FILL);
                } else {
                    graphics.drawString(font, "?", x + 5, y + 4, HIDDEN_MARK, false);
                }
            } else {
                float age = menu.revealAge(index, partialTick);
                if (age < REVEAL_FLASH_TICKS) {
                    int alpha = (int) (0x90 * (1.0F - age / REVEAL_FLASH_TICKS));
                    graphics.fill(RenderType.guiOverlay(), x, y, x + 16, y + 16, alpha << 24 | REVEAL_FLASH_RGB);
                }
            }
        }
        if (!menu.isSearchComplete()) {
            String count = menu.revealedCount() + "/" + menu.searchSlotCount();
            graphics.drawString(font, count, imageWidth - 8 - font.width(count), titleLabelY, 0x404040, false);
        }
    }
}
