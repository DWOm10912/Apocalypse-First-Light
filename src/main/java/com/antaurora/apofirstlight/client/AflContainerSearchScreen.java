package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.containersearch.AflContainerSearchLayout;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchMenu;
import com.antaurora.apofirstlight.client.ui.SearchMagnifier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Creative's screen of a searchable container (Survival and Adventure get the search page, AflSearchScreen): the vanilla
 * chest look with the container's grid sections one under another, a plain slot per cell (AflContainerSearchLayout), plus
 * a DEVELOPMENT PLACEHOLDER search overlay (plain fills, no textures): hidden slots are masked, only the slot being
 * searched shows a small circling magnifier and a faint progress line. Items lie on their anchor slots as plain stacks
 * (the grid footprints only show on the search page); a stack goes onto a slot only where the grid has room for it.
 * All state comes from {@link AflContainerSearchMenu}; the screen never decides when a slot is revealed.
 */
public final class AflContainerSearchScreen extends AbstractContainerScreen<AflContainerSearchMenu> {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("textures/gui/container/generic_54.png");
    private static final int HIDDEN_FILL = 0xE01A1D21;
    private static final int REVEAL_FLASH_RGB = 0xD8DEE4;
    private static final float REVEAL_FLASH_TICKS = 6.0F;
    /** Restrained progress: a faint 1 px line along the slot's bottom edge, under the magnifier. */
    private static final int PROGRESS_LINE = 0x70C8CED4;

    private final AflContainerSearchLayout layout;
    private float partialTick;

    public AflContainerSearchScreen(AflContainerSearchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.layout = menu.layout();
        this.imageHeight = layout.inventoryY() + 83;
        this.inventoryLabelY = layout.inventoryY() - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.partialTick = partialTick;
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /** The chest panel's title bar, its plain middle stretched over the sections, the inventory part; a chest slot frame per cell. */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        int inventoryTop = layout.inventoryY() - 14;
        graphics.blit(BACKGROUND, x, y, 0, 0, imageWidth, 17);
        graphics.blit(BACKGROUND, x, y + 17, imageWidth, inventoryTop - 17, 0, 5, imageWidth, 1, 256, 256);
        graphics.blit(BACKGROUND, x, y + inventoryTop, 0, 126, imageWidth, 96);
        for (int index = 0; index < menu.searchSlotCount(); index++) {
            Slot slot = menu.slots.get(index);
            graphics.blit(BACKGROUND, x + slot.x - 1, y + slot.y - 1, 7, 17, 18, 18);
        }
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
            String count = menu.revealedItemCount() + "/" + menu.itemCount();
            graphics.drawString(font, count, imageWidth - 8 - font.width(count), titleLabelY, 0x404040, false);
        }
    }

    /** The round magnifier (SearchMagnifier, shared with the search page), above the slot's items. */
    private void renderMagnifier(GuiGraphics graphics, int slotX, int slotY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        SearchMagnifier.draw(graphics, slotX, slotY, 16.0F);
        graphics.pose().popPose();
    }
}
