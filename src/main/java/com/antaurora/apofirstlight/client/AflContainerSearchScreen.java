package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.containersearch.AflContainerSearchMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Vanilla chest layout plus a DEVELOPMENT PLACEHOLDER search overlay (plain fills, no textures): hidden slots are
 * masked, only the slot being searched shows a small circling magnifier and a faint progress line.
 * All state comes from {@link AflContainerSearchMenu}; the screen never decides when a slot is revealed.
 */
public final class AflContainerSearchScreen extends AbstractContainerScreen<AflContainerSearchMenu> {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("textures/gui/container/generic_54.png");
    private static final int HIDDEN_FILL = 0xE01A1D21;
    private static final int REVEAL_FLASH_RGB = 0xD8DEE4;
    private static final float REVEAL_FLASH_TICKS = 6.0F;
    /** 7x7 magnifier: R = rim, G = glass, H = handle. Its lens center (2.5, 2.5) follows the orbit. */
    private static final String[] MAGNIFIER = {
            ".RRR...",
            "RGGGR..",
            "RGGGR..",
            "RGGGR..",
            ".RRRH..",
            ".....H.",
            "......H",
    };
    private static final float LENS_CENTER = 2.5F;
    private static final float ORBIT_RADIUS = 2.0F;
    private static final long ORBIT_PERIOD_MS = 1600L;
    /** Restrained progress: a faint 1 px line along the slot's bottom edge, under the magnifier. */
    private static final int PROGRESS_LINE = 0x70C8CED4;

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
                    int width = Math.round(16.0F * menu.currentSlotProgress(partialTick));
                    graphics.fill(RenderType.guiOverlay(), x, y + 15, x + width, y + 16, PROGRESS_LINE);
                    renderMagnifier(graphics, x, y);
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

    /** Small magnifier slowly circling the slot centre (1.6 s per turn); sub-pixel translation keeps the path smooth. */
    private void renderMagnifier(GuiGraphics graphics, int slotX, int slotY) {
        double angle = (Util.getMillis() % ORBIT_PERIOD_MS) / (double) ORBIT_PERIOD_MS * Math.PI * 2.0;
        float left = slotX + 8.0F + ORBIT_RADIUS * (float) Math.cos(angle) - LENS_CENTER;
        float top = slotY + 8.0F + ORBIT_RADIUS * (float) Math.sin(angle) - LENS_CENTER;
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0F);
        for (int row = 0; row < MAGNIFIER.length; row++) {
            for (int column = 0; column < MAGNIFIER[row].length(); column++) {
                int color = switch (MAGNIFIER[row].charAt(column)) {
                    case 'R' -> 0xFFD0D5DA;
                    case 'G' -> 0x5590B4D0;
                    case 'H' -> 0xFF9AA0A6;
                    default -> 0;
                };
                if (color != 0) {
                    graphics.fill(RenderType.guiOverlay(), column, row, column + 1, row + 1, color);
                }
            }
        }
        graphics.pose().popPose();
    }
}
