package com.antaurora.apofirstlight.client.inventory;

import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.grid.ExternalGridMenu;
import com.antaurora.apofirstlight.inventory.grid.GridActions;
import com.antaurora.apofirstlight.inventory.grid.GridPackets;
import com.antaurora.apofirstlight.inventory.grid.GridStorage;
import com.antaurora.apofirstlight.inventory.grid.GunSling;
import com.antaurora.apofirstlight.inventory.grid.ItemSizes;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The panel of an external grid (the pickup's cargo bed), docs/ui/afl_inventory_grid_v0_plan.md 15: its grid, and the
 * player's weapon positions and hand below. The player's own grids are ContainerGridPanel's side panel, as on any
 * container. The grid clicks are the inventory page's (GridActions with target {@link GridActions#EXTERNAL_TARGET}):
 * click takes / places / merges, right-click half / one, shift-click moves to the player, Q drops, 1-3 swap with a
 * weapon position, R turns the carried item; a drag ends on the panel, the grid or a slot.
 */
public final class ExternalGridScreen extends AbstractContainerScreen<ExternalGridMenu> implements ContainerGridPanel.GridDropTarget {
    private static final float CELL = GridDrawing.CELL, DRAG = 4, LABEL = 5.2F;
    private static final long DRAG_WAIT_NANOS = 600_000_000L;

    private boolean pressOurs, pressPicked, slotPressPicked;
    private double pressX, pressY;
    private long pendingUntil;
    private double pendingX, pendingY;

    public ExternalGridScreen(ExternalGridMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = menu.width();
        imageHeight = menu.height();
        titleLabelX = ExternalGridMenu.PAD;
        titleLabelY = 7;
    }

    /** Client: the grid as the server has it now (GridPackets.ExternalSync). */
    public static void receive(int containerId, ListTag items) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof ExternalGridMenu menu && menu.containerId == containerId) menu.receive(items);
    }

    // ---------------------------------------------------------------- where

    private record Cell(int section, float x, float y, int w, int h) {}

    @Nullable
    private Cell cellAt(double mouseX, double mouseY) {
        var layout = menu.layout();
        float top = topPos + ExternalGridMenu.TOP;
        for (int i = 0; i < layout.size(); i++) {
            var section = layout.section(i);
            float x = leftPos + menu.sectionX(i);
            if (mouseX >= x && mouseY >= top && mouseX < x + section.w() * CELL && mouseY < top + section.h() * CELL)
                return new Cell(i, x, top, section.w(), section.h());
        }
        return null;
    }

    @Nullable
    private GridStorage.Entry entryAt(Cell cell, double mouseX, double mouseY) {
        return GridDrawing.entryAt(menu.grid(), cell.section(), cell.x(), cell.y(), (float) mouseX, (float) mouseY);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var carried = menu.getCarried();
        if (pendingUntil != 0) {
            if (!carried.isEmpty()) {
                pendingUntil = 0;
                dropHere(pendingX, pendingY);
            } else if (System.nanoTime() > pendingUntil) pendingUntil = 0;
        }
        if (carried.isEmpty() && !ContainerGridPanel.active(this)) ContainerGridPanel.rotated(false);
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (carried.isEmpty()) {
            var cell = cellAt(mouseX, mouseY);
            var entry = cell == null ? null : entryAt(cell, mouseX, mouseY);
            if (entry != null) g.renderTooltip(font, entry.stack(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        AflUiShapes.box(g, leftPos, topPos, imageWidth, imageHeight, AflUiStyle.RADIUS_PANEL, AflUiStyle.BACK, AflUiStyle.BACK_ALPHA,
                AflUiStyle.LINE, AflUiStyle.LINE_ALPHA, AflUiStyle.SHADOW_ALPHA, 0);
        var grid = menu.grid();
        var layout = menu.layout();
        float top = topPos + ExternalGridMenu.TOP;
        for (int i = 0; i < layout.size(); i++) {
            var section = layout.section(i);
            float sx = leftPos + menu.sectionX(i);
            GridDrawing.cells(g, section.w(), section.h(), sx, top);
            for (var entry : grid.entries()) {
                if (entry.section() != i) continue;
                GridDrawing.entry(g, font, entry.stack(), sx + entry.x() * CELL, top + entry.y() * CELL, grid.footprint(entry), entry.rotated(), false);
            }
        }
        var cell = cellAt(mouseX, mouseY);
        if (cell != null) hover(g, cell, menu.getCarried(), mouseX, mouseY);
        float slotY = topPos + menu.slotY();
        GridDrawing.text(g, font, Component.translatable("screen.apocalypse_firstlight.external_grid.carried"),
                leftPos + ExternalGridMenu.PAD, slotY - 6.2F, LABEL, AflUiStyle.TEXT_DIM, 1);
        for (int i = 0; i < HotbarRoles.USED; i++)
            AflUiShapes.box(g, leftPos + ExternalGridMenu.slotX(i), slotY, CELL, CELL, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.45F,
                    AflUiStyle.LINE, 0.22F, 0, 0);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, AflUiStyle.argb(AflUiStyle.TEXT, 1), false);
    }

    /** What the mouse is over: the entry (empty cursor), or where the carried item would go, green / amber / red. */
    private void hover(GuiGraphics g, Cell cell, ItemStack carried, double mouseX, double mouseY) {
        var grid = menu.grid();
        float mx = (float) mouseX, my = (float) mouseY;
        var entry = entryAt(cell, mouseX, mouseY);
        if (carried.isEmpty()) {
            if (entry != null) GridDrawing.entry(g, font, entry.stack(), cell.x() + entry.x() * CELL, cell.y() + entry.y() * CELL,
                    grid.footprint(entry), entry.rotated(), true);
            else AflUiShapes.box(g, cell.x() + (float) Math.floor((mx - cell.x()) / CELL) * CELL, cell.y() + (float) Math.floor((my - cell.y()) / CELL) * CELL,
                    CELL, CELL, 1.5F, AflUiStyle.BACK_HOVER, 0.6F, AflUiStyle.LINE, AflUiStyle.LINE_HOVER_ALPHA, 0, 0);
            return;
        }
        if (GunSling.isSling(carried)) {
            if (entry == null) return;
            int colour = GunSling.canFit(entry.stack()) ? GridDrawing.FREE : GridDrawing.BLOCKED;
            var size = grid.footprint(entry);
            AflUiShapes.box(g, cell.x() + entry.x() * CELL, cell.y() + entry.y() * CELL, size.w() * CELL, size.h() * CELL, 2, colour, 0.28F, colour, 0.7F, 0, 0);
            return;
        }
        boolean turned = ContainerGridPanel.rotated();
        var plan = GridDrawing.plan(grid, cell.section(), cell.x(), cell.y(), carried, turned, mx, my);
        int colour = plan.kind() == GridActions.Kind.MERGE || plan.free() ? GridDrawing.FREE : plan.swap() ? GridDrawing.SWAP : GridDrawing.BLOCKED;
        var size = ItemSizes.of(carried, true).turned(turned);
        if (plan.kind() == GridActions.Kind.MERGE && entry != null) size = grid.footprint(entry);
        AflUiShapes.box(g, cell.x() + plan.x() * CELL, cell.y() + plan.y() * CELL, size.w() * CELL, size.h() * CELL, 2, colour, 0.28F, colour, 0.7F, 0, 0);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        pressX = mouseX;
        pressY = mouseY;
        pendingUntil = 0;
        var cell = cellAt(mouseX, mouseY);
        if (cell == null) {
            pressOurs = pressPicked = false;
            var slot = getSlotUnderMouse();
            slotPressPicked = menu.getCarried().isEmpty() && !Screen.hasShiftDown() && slot != null && slot.hasItem() && (button == 0 || button == 1);
            return super.mouseClicked(mouseX, mouseY, button);
        }
        slotPressPicked = false;
        pressOurs = true;
        pressPicked = false;
        if (button != 0 && button != 1) return true;
        var carried = menu.getCarried();
        var entry = entryAt(cell, mouseX, mouseY);
        if (GunSling.isSling(carried) && entry != null && GunSling.canFit(entry.stack())) {
            send(GridActions.Kind.SLING, entry.section(), entry.x(), entry.y(), false, 0);
            return true;
        }
        if (carried.isEmpty()) {
            if (entry == null) return true;
            if (Screen.hasShiftDown()) {
                send(GridActions.Kind.QUICK, entry.section(), entry.x(), entry.y(), false, 0);
                return true;
            }
            ContainerGridPanel.rotated(entry.rotated());
            pressPicked = true;
            send(GridActions.Kind.PICK, entry.section(), entry.x(), entry.y(), false, button);
            return true;
        }
        place(cell, carried, mouseX, mouseY, button);
        return true;
    }

    private void place(Cell cell, ItemStack carried, double mouseX, double mouseY, int button) {
        boolean turned = ContainerGridPanel.rotated();
        var plan = GridDrawing.plan(menu.grid(), cell.section(), cell.x(), cell.y(), carried, turned, (float) mouseX, (float) mouseY);
        if (plan.kind() == GridActions.Kind.MERGE || plan.free() || plan.swap() && button == 0)
            send(plan.kind(), cell.section(), plan.x(), plan.y(), turned, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        var carried = menu.getCarried();
        boolean dragged = Math.hypot(mouseX - pressX, mouseY - pressY) >= DRAG;
        if (pressOurs) {
            pressOurs = false;
            if (!pressPicked || !dragged) return true;
            pressPicked = false;
            if (!carried.isEmpty()) dropHere(mouseX, mouseY);
            else {
                pendingUntil = System.nanoTime() + DRAG_WAIT_NANOS;
                pendingX = mouseX;
                pendingY = mouseY;
            }
            return true;
        }
        // from a weapon position or the hand onto the grid: the press picked up, the grid places
        if (slotPressPicked && dragged && !carried.isEmpty() && cellAt(mouseX, mouseY) != null) {
            slotPressPicked = false;
            dropAt(mouseX, mouseY);
            return true;
        }
        slotPressPicked = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** The end of a drag from the grid: onto the grid, the side panel or a slot; elsewhere it stays carried. */
    private void dropHere(double mouseX, double mouseY) {
        if (dropAt(mouseX, mouseY) || ContainerGridPanel.dropOnPanel(this, mouseX, mouseY)) return;
        var slot = getSlotUnderMouse();
        var mc = Minecraft.getInstance();
        if (slot != null && mc.gameMode != null && mc.player != null)
            mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.PICKUP, mc.player);
    }

    /** A drag (from here or from the side panel) let go over the grid places the carried stack there. */
    @Override
    public boolean dropAt(double mouseX, double mouseY) {
        var cell = cellAt(mouseX, mouseY);
        var carried = menu.getCarried();
        if (cell == null || carried.isEmpty()) return false;
        place(cell, carried, mouseX, mouseY, 0);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        var key = InputConstants.getKey(keyCode, scanCode);
        var carried = menu.getCarried();
        // with the side panel showing, its key handler turns the item (and the key works there too)
        if (GridClient.ROTATE.isActiveAndMatches(key) && PlayerGrids.allowedInGrid(carried)) {
            ContainerGridPanel.rotated(!ContainerGridPanel.rotated());
            return true;
        }
        var mc = Minecraft.getInstance();
        var mouse = mc.mouseHandler;
        double mouseX = mouse.xpos() * width / mc.getWindow().getScreenWidth();
        double mouseY = mouse.ypos() * height / mc.getWindow().getScreenHeight();
        var cell = carried.isEmpty() ? cellAt(mouseX, mouseY) : null;
        var entry = cell == null ? null : entryAt(cell, mouseX, mouseY);
        if (entry != null) {
            int number = -1;
            for (int i = 0; i < Inventory.getSelectionSize(); i++)
                if (mc.options.keyHotbarSlots[i].isActiveAndMatches(key)) number = i;
            if (number >= 0 && number < HotbarRoles.HAND_SLOT) {
                send(GridActions.Kind.HOTBAR_KEY, entry.section(), entry.x(), entry.y(), false, number);
                return true;
            }
            if (mc.options.keyDrop.isActiveAndMatches(key)) {
                send(GridActions.Kind.DROP, entry.section(), entry.x(), entry.y(), false, Screen.hasControlDown() ? 1 : 0);
                return true;
            }
            if (number >= 0) return true;   // keys 4-9 bind only the player's own pockets and rig
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static void send(GridActions.Kind kind, int section, int x, int y, boolean turned, int extra) {
        GridPackets.send(new GridPackets.Action(kind.ordinal(), GridActions.EXTERNAL_TARGET, section, x, y, turned, extra));
    }
}
