package com.antaurora.apofirstlight.client.inventory;

import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.client.ui.SearchMagnifier;
import com.antaurora.apofirstlight.containersearch.AflContainerGrid;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchLayout;
import com.antaurora.apofirstlight.containersearch.AflContainerSearchMenu;
import com.antaurora.apofirstlight.inventory.grid.GridActions;
import com.antaurora.apofirstlight.inventory.grid.GridStorage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The search page (docs/ui/afl_container_search_page_v1.md): a searchable container or a body opens the inventory page with
 * the container at the head of the right column, in crafting's place. The menu is AflContainerSearchMenu: the container's
 * slots, the player's main rows and hotbar, then the gear (armour, the AFL equipment slots), so everything on the left
 * works as on the inventory page: a backpack goes from a body onto the back directly. Only the 2x2 crafting and the off
 * hand are not in it (an off-hand stack shows under overflow, view only). Creative keeps the old screen
 * (AflContainerSearchScreen).
 * <p>Since 2026-10-27 (section 12, user: "容器分区改成网格，未搜出的东西显示暗色轮廓") the container is drawn as its grid
 * sections (AflContainerSearchLayout), 18 px cells like the player's grids, each section under its name: a searched
 * item is a grid entry of its footprint, one not searched yet a dark block of its footprint, the one being searched the
 * magnifier on its block. Items are taken, put, turned and dragged like grid entries (GridActions.CONTAINER_TARGET).
 */
public final class AflSearchScreen extends AflPageScreen<AflContainerSearchMenu> {
    private static final float PAD = 6, LINE_Y = C_Y + 15.5F, GRID_Y = C_Y + 21, TAKE_H = 11;
    /** A named section: its name, then its cells 7 below; 4 between sections. */
    private static final float NAME_H = 7, SECTION_GAP = 4;
    private static final int REVEAL_FLASH = 0xD8DEE4;
    private static final float REVEAL_FLASH_TICKS = 6;

    private float takeX, takeY, takeW;
    private boolean takeEnabled;

    public AflSearchScreen(AflContainerSearchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory.player, title);
    }

    @Override
    Slot pageSlot(int role) {
        int container = menu.searchSlotCount();
        if (role >= InventoryMenu.INV_SLOT_START && role < InventoryMenu.INV_SLOT_END)
            return menu.getSlot(container + role - InventoryMenu.INV_SLOT_START);
        if (role >= InventoryMenu.USE_ROW_SLOT_START && role < InventoryMenu.USE_ROW_SLOT_END)
            return menu.getSlot(container + 27 + role - InventoryMenu.USE_ROW_SLOT_START);
        int gear = menu.gearSlot(role);
        return gear >= 0 ? menu.getSlot(gear) : player.inventoryMenu.getSlot(role);
    }

    @Override
    GridActions.Kind shiftGridKind() {
        return GridActions.Kind.TO_CONTAINER;
    }

    @Override
    boolean shiftSlotToGrids() {
        return false;
    }

    /**
     * The container's grid as the menu has it: searched items with their stacks, the others locked with only their
     * footprint (an item the menu says is there but whose stack has not arrived yet waits locked too).
     */
    private GridStorage storage(AflContainerSearchLayout layout) {
        var storage = new GridStorage(layout.grid(), true);
        for (int slot = 0; slot < layout.size(); slot++) {
            int footprint = menu.footprint(slot);
            if (footprint == 0) continue;
            ItemStack stack = menu.isSlotRevealed(slot) ? menu.getSlot(slot).getItem() : ItemStack.EMPTY;
            storage.place(stack, layout.sectionOf(slot), layout.cellX(slot), layout.cellY(slot), (footprint & AflContainerGrid.TURNED) != 0,
                    AflContainerGrid.size(footprint), stack.isEmpty());
        }
        return storage;
    }

    /** The container: name and search state, the progress line, the sections, "take all"; the overflow and nearby panels after it. */
    @Override
    float rightHead(GuiGraphics g, float mx, float my, float partialTick) {
        var layout = menu.layout();
        var storage = storage(layout);
        int count = menu.itemCount(), revealed = menu.revealedItemCount();
        float x0 = C_X + PAD, right = C_X + C_W - PAD;

        // the sections' places first: the panel goes under them
        int sections = layout.sections().size();
        float[] sectionY = new float[sections];
        float y = GRID_Y - 1;
        for (int i = 0; i < sections; i++) {
            var section = layout.section(i);
            if (i > 0) y += SECTION_GAP;
            if (!section.name().isEmpty()) y += NAME_H;
            sectionY[i] = y;
            y += section.h() * CELL;
        }
        float gridBottom = y, lastW = layout.section(sections - 1).w() * CELL;
        Component take = Component.translatable("screen.apocalypse_firstlight.search.take_all");
        takeW = width(take, 6) + 10;
        boolean beside = lastW + 6 + takeW <= right - x0;
        takeX = right - takeW;
        takeY = beside ? gridBottom - TAKE_H : gridBottom + 4;
        float bottom = (beside ? gridBottom : takeY + TAKE_H) + 6;
        panel(g, C_X, C_Y, C_W, bottom - C_Y);

        boolean done = menu.isSearchComplete() || revealed >= count;
        Component status = done ? Component.translatable("screen.apocalypse_firstlight.search.done")
                : Component.translatable("screen.apocalypse_firstlight.search.searching", revealed, count);
        float statusW = width(status, 5.6F);
        String name = font.plainSubstrByWidth(title.getString(), (int) ((right - x0 - statusW - 6) * font.lineHeight / 7.5F));
        text(g, Component.literal(name), x0, C_Y + 5, 7.5F, AflUiStyle.TEXT, 1);
        text(g, status, right - statusW, C_Y + 6.5F, 5.6F, AflUiStyle.TEXT_DIM, 1);
        float slotProgress = menu.isSearching() ? menu.currentSlotProgress(partialTick) : 0;
        float progress = done || count == 0 ? 1 : Math.min(1, (revealed + slotProgress) / count);
        AflUiShapes.fill(g, x0, LINE_Y, right - x0, 1, 0.5F, AflUiStyle.LINE, 0.12F);
        AflUiShapes.fill(g, x0, LINE_Y, (right - x0) * progress, 1, 0.5F, AflUiStyle.LINE, 0.6F);

        int current = menu.isSearching() ? menu.currentSearchSlot() : -1;
        takeEnabled = false;
        for (int i = 0; i < sections; i++) {
            var section = layout.section(i);
            float sy = sectionY[i];
            if (!section.name().isEmpty()) {
                Component label = section.title();
                text(g, label, x0, sy - NAME_H, LABEL, AflUiStyle.TEXT_DIM, 1);
                float start = x0 + width(label, LABEL) + 3;
                AflUiShapes.fill(g, start, sy - NAME_H + 2.6F, right - start, 0.35F, 0, AflUiStyle.LINE, 0.10F);
            }
            GridDrawing.cells(g, section.w(), section.h(), x0, sy);
            hits.add(new CellHit(GridActions.CONTAINER_TARGET, storage, i, x0, sy, section.w() * CELL, section.h() * CELL));
            for (var entry : storage.entries()) {
                if (entry.section() != i) continue;
                var size = storage.footprint(entry);
                float ex = x0 + entry.x() * CELL, ey = sy + entry.y() * CELL, w = size.w() * CELL, h = size.h() * CELL;
                int slot = layout.slot(i, entry.x(), entry.y());
                if (slot == current) {
                    AflUiShapes.box(g, ex + 0.5F, ey + 0.5F, w - 1, h - 1, 2, AflUiStyle.BACK_HOVER, 0.80F, AflUiStyle.LINE, 0.34F, 0, 0);
                    AflUiShapes.fill(g, ex + 2, ey + h - 2.5F, w - 4, 0.6F, 0.3F, AflUiStyle.LINE, 0.15F);
                    AflUiShapes.fill(g, ex + 2, ey + h - 2.5F, (w - 4) * slotProgress, 0.6F, 0.3F, AflUiStyle.LINE, 0.6F);
                    SearchMagnifier.draw(g, ex + (w - CELL) / 2, ey + (h - CELL) / 2, CELL);
                    continue;
                }
                if (entry.locked()) {
                    // not searched yet: an opaque dark block of its footprint, its edge just visible
                    AflUiShapes.box(g, ex + 0.5F, ey + 0.5F, w - 1, h - 1, 2, AflUiStyle.BACK_DOWN, 1, AflUiStyle.LINE, 0.34F, 0, 0);
                    continue;
                }
                GridDrawing.entry(g, font, entry.stack(), ex, ey, size, entry.rotated(), false);
                takeEnabled |= menu.getSlot(slot).mayPickup(player);
                float age = menu.revealAge(slot, partialTick);
                if (age < REVEAL_FLASH_TICKS)
                    AflUiShapes.fill(g, ex + 0.5F, ey + 0.5F, w - 1, h - 1, 2, REVEAL_FLASH, 0.56F * (1 - age / REVEAL_FLASH_TICKS));
            }
        }

        takeEnabled &= menu.getCarried().isEmpty();
        boolean hover = takeEnabled && in(mx, my, takeX, takeY, takeW, TAKE_H);
        AflUiDraw.button(g, takeX, takeY, takeW, TAKE_H, hover, false, takeEnabled, 1);
        text(g, take, takeX + 5, takeY + 2.8F, 6, takeEnabled ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1);
        return bottom + 6;
    }

    @Override
    boolean headClicked(float mx, float my) {
        if (!takeEnabled || !in(mx, my, takeX, takeY, takeW, TAKE_H)) return false;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AflContainerSearchMenu.TAKE_ALL);
        return true;
    }
}
