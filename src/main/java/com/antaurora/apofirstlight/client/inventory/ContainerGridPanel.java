package com.antaurora.apofirstlight.client.inventory;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
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
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The grid side panel of container screens (docs/ui/afl_inventory_grid_v0_plan.md 13), Survival / Adventure: every
 * container screen whose menu shows the player's inventory (chests, furnaces, AFL machines, searchable containers,
 * villagers...) gets the pockets, the worn rig's and the worn backpack's grids in a panel left of the window (left of an
 * open recipe book), without touching the container's own layout. Clicks work as on the inventory page: a click picks
 * up and the next puts down, pressing on an item and letting go elsewhere drags it (onto a container slot too), R turns
 * the carried item, Q drops, 1-3 move a weapon to its position, 4-9 bind (pockets and rig); shift-click moves a grid
 * entry into the container by the menu's own shift-click rules. The server does every action (GridActions) with the
 * open menu's cursor. Drawn behind the container's slots, so the carried item stays on top; JEI keeps out of it
 * (AflJeiPlugin) and a click on it never counts as outside the window (AbstractContainerScreenGridMixin).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ContainerGridPanel {
    private static final float CELL = GridDrawing.CELL, PAD = 6, GAP = 14, TOP = 11, LABEL = 5.2F;
    /** The panel's width at least (the hiking backpack, 6 cells, sets it). */
    private static final float MIN_W = 6 * CELL + 2 * PAD;
    /** Gap to the container window (or the open recipe book) and to the screen's edges, GUI pixels. */
    private static final float MARGIN = 6, EDGE = 4;
    /** Smallest scale the panel shrinks to on narrow windows; below that it overlaps. */
    private static final float MIN_SCALE = 0.5F;
    /** Recipe book: its width and how far left of centre it sits when open beside the window (RecipeBookComponent). */
    private static final int BOOK_W = 147, BOOK_SHIFT = 86;
    private static final float DRAG = 4;
    private static final long DRAG_WAIT_NANOS = 600_000_000L;

    private record CellHit(PlayerGrids.Target target, GridStorage storage, int section, float x, float y, float w, float h) {}
    /** Where the panel is (GUI pixels), its scale, its size and its content height (panel units). */
    private record Layout(float x, float y, float s, float w, float h, float content) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + w * s && mouseY < y + h * s;
        }
        float vx(double mouseX) { return (float) ((mouseX - x) / s); }
        float vy(double mouseY) { return (float) ((mouseY - y) / s); }
    }

    private static final List<CellHit> HITS = new ArrayList<>();
    private static float scroll;
    private static boolean rotated;
    /** A press on the panel (its release is the panel's too); whether it picked something up, for a drag. */
    private static boolean pressOurs, pressPicked;
    /** A press on a container slot that picked something up: a release over the panel puts it down there. */
    private static boolean slotPressPicked;
    private static double pressX, pressY;
    /** A drop waiting for the dragged stack to arrive on the cursor. */
    private static long pendingUntil;
    private static double pendingX, pendingY;

    private ContainerGridPanel() {}

    // ---------------------------------------------------------------- where

    /** The screen gets the panel: a container screen showing the player's inventory, Survival / Adventure. */
    public static boolean active(@Nullable Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container) || screen instanceof AflInventoryScreen
                || screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) return false;
        var player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator() || PlayerStorageCapacity.unrestricted(player)) return false;
        for (Slot slot : container.getMenu().slots)
            if (slot.container == player.getInventory() && slot.x > -500 && slot.y > -500) return true;   // not parked off screen
        return false;
    }

    @Nullable
    private static Layout layout(Screen screen) {
        if (!active(screen)) return null;
        var container = (AbstractContainerScreen<?>) screen;
        var view = PlayerGrids.view(Minecraft.getInstance().player);
        float w = MIN_W, content = TOP;
        for (var target : PlayerGrids.Target.values()) {
            var storage = view.get(target);
            if (storage == null) continue;
            float sw = 0, sh = 0;
            for (int i = 0; i < storage.layout().size(); i++) {
                var section = storage.layout().section(i);
                sw += section.w() * CELL + (i > 0 ? 2 : 0);
                sh = Math.max(sh, section.h() * CELL);
            }
            w = Math.max(w, sw + 2 * PAD);
            content += sh + GAP;
        }
        content -= GAP - PAD;
        float right = container.getGuiLeft();
        if (screen instanceof RecipeUpdateListener book && book.getRecipeBookComponent().isVisible()
                && container.getGuiLeft() > (screen.width - container.getXSize()) / 2)
            right = Math.min(right, (screen.width - BOOK_W) / 2F - BOOK_SHIFT);
        right -= MARGIN;
        float s = Math.max(MIN_SCALE, Math.min(1, (right - EDGE) / w));
        float h = Math.min(content, (screen.height - 2 * EDGE) / s);
        float y = Math.max(EDGE, Math.min(container.getGuiTop(), screen.height - EDGE - h * s));
        return new Layout(Math.max(EDGE, right - w * s), y, s, w, h, content);
    }

    /** JEI's exclusion areas for the screen: the panel (AflJeiPlugin). */
    public static List<Rect2i> extraAreas(Screen screen) {
        var layout = layout(screen);
        if (layout == null) return List.of();
        return List.of(new Rect2i((int) Math.floor(layout.x()), (int) Math.floor(layout.y()),
                (int) Math.ceil(layout.w() * layout.s()) + 1, (int) Math.ceil(layout.h() * layout.s()) + 1));
    }

    /** A click here is on the panel, not outside the window (AbstractContainerScreenGridMixin). */
    public static boolean contains(Screen screen, double mouseX, double mouseY) {
        var layout = layout(screen);
        return layout != null && layout.contains(mouseX, mouseY);
    }

    // ---------------------------------------------------------------- drawing

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        scroll = 0;
        rotated = false;
        pressOurs = pressPicked = slotPressPicked = false;
        pendingUntil = 0;
    }

    /** Behind the slots and the carried item: the panel, its grids and what the mouse is over. */
    @SubscribeEvent
    public static void background(ContainerScreenEvent.Render.Background event) {
        var screen = event.getContainerScreen();
        var layout = layout(screen);
        HITS.clear();
        if (layout == null) return;
        var mc = Minecraft.getInstance();
        var carried = screen.getMenu().getCarried();
        if (pendingUntil != 0) {
            if (!carried.isEmpty()) {
                pendingUntil = 0;
                dropAt(screen, layout, pendingX, pendingY);
            } else if (System.nanoTime() > pendingUntil) pendingUntil = 0;
        }
        if (carried.isEmpty()) rotated = false;
        var g = event.getGuiGraphics();
        var font = mc.font;
        float mx = layout.vx(event.getMouseX()), my = layout.vy(event.getMouseY());
        var pose = g.pose();
        pose.pushPose();
        pose.translate(layout.x(), layout.y(), 0);
        pose.scale(layout.s(), layout.s(), 1);
        AflUiShapes.box(g, 0, 0, layout.w(), layout.h(), AflUiStyle.RADIUS_PANEL, AflUiStyle.BACK, AflUiStyle.BACK_ALPHA,
                AflUiStyle.LINE, AflUiStyle.LINE_ALPHA, AflUiStyle.SHADOW_ALPHA, 0);
        float scrollMax = Math.max(0, layout.content() - layout.h());
        scroll = Math.max(0, Math.min(scroll, scrollMax));
        scissor(g, layout);
        var view = PlayerGrids.view(mc.player);
        float y = TOP - scroll;
        for (var target : PlayerGrids.Target.values()) {
            var storage = view.get(target);
            if (storage == null) continue;
            GridDrawing.text(g, font, Component.translatable("screen.apocalypse_firstlight.inventory." + target.name().toLowerCase(java.util.Locale.ROOT)),
                    PAD, y - 6.2F, LABEL, AflUiStyle.TEXT_DIM, 1);
            float sx = PAD, height = 0;
            for (int i = 0; i < storage.layout().size(); i++) {
                var section = storage.layout().section(i);
                GridDrawing.cells(g, section.w(), section.h(), sx, y);
                HITS.add(new CellHit(target, storage, i, sx, y, section.w() * CELL, section.h() * CELL));
                GridDrawing.reserved(g, storage, i, sx, y);
                for (var entry : storage.entries()) {
                    if (entry.section() != i) continue;
                    var size = storage.footprint(entry);
                    GridDrawing.entry(g, font, entry.stack(), sx + entry.x() * CELL, y + entry.y() * CELL, size, entry.rotated(), false);
                    if (target != PlayerGrids.Target.BACKPACK)
                        GridDrawing.bindBadge(g, font, entry.stack(), sx + (entry.x() + size.w()) * CELL, y + entry.y() * CELL);
                }
                height = Math.max(height, section.h() * CELL);
                sx += section.w() * CELL + 2;
            }
            y += height + GAP;
        }
        var hover = layout.contains(event.getMouseX(), event.getMouseY()) ? hitAt(layout, mx, my) : null;
        if (hover != null) hover(g, font, hover, carried, mx, my);
        g.disableScissor();
        if (scrollMax > 0) {
            float track = layout.h() - 8, thumb = Math.max(12, track * layout.h() / layout.content());
            AflUiShapes.fill(g, layout.w() - 4, 4 + (track - thumb) * (scroll / scrollMax), 2, thumb, 1, AflUiStyle.LINE, 0.35F);
        }
        pose.popPose();
    }

    private static void scissor(GuiGraphics g, Layout layout) {
        g.enableScissor(Math.round(layout.x()), Math.round(layout.y() + layout.s()),
                Math.round(layout.x() + layout.w() * layout.s()), Math.round(layout.y() + (layout.h() - 1) * layout.s()));
    }

    /** What the mouse is over: the entry (empty cursor), or where the carried item would go, green / amber / red. */
    private static void hover(GuiGraphics g, net.minecraft.client.gui.Font font, CellHit cell, ItemStack carried, float mx, float my) {
        var entry = GridDrawing.entryAt(cell.storage(), cell.section(), cell.x(), cell.y(), mx, my);
        if (carried.isEmpty()) {
            if (entry != null) GridDrawing.entry(g, font, entry.stack(), cell.x() + entry.x() * CELL, cell.y() + entry.y() * CELL,
                    cell.storage().footprint(entry), entry.rotated(), true);
            else AflUiShapes.box(g, cell.x() + (float) Math.floor((mx - cell.x()) / CELL) * CELL, cell.y() + (float) Math.floor((my - cell.y()) / CELL) * CELL,
                    CELL, CELL, 1.5F, AflUiStyle.BACK_HOVER, 0.6F, AflUiStyle.LINE, AflUiStyle.LINE_HOVER_ALPHA, 0, 0);
            return;
        }
        if (GunSling.isSling(carried)) {
            if (entry == null) return;
            int colour = GunSling.canFit(entry.stack()) ? GridDrawing.FREE : GridDrawing.BLOCKED;
            var size = cell.storage().footprint(entry);
            AflUiShapes.box(g, cell.x() + entry.x() * CELL, cell.y() + entry.y() * CELL, size.w() * CELL, size.h() * CELL, 2, colour, 0.28F, colour, 0.7F, 0, 0);
            return;
        }
        var plan = GridDrawing.plan(cell.storage(), cell.section(), cell.x(), cell.y(), carried, rotated, mx, my);
        int colour = plan.kind() == GridActions.Kind.MERGE || plan.free() ? GridDrawing.FREE : plan.swap() ? GridDrawing.SWAP : GridDrawing.BLOCKED;
        var size = ItemSizes.of(carried, true).turned(rotated);
        float x = cell.x() + plan.x() * CELL, y = cell.y() + plan.y() * CELL;
        if (plan.kind() == GridActions.Kind.MERGE && entry != null) size = cell.storage().footprint(entry);
        AflUiShapes.box(g, x, y, size.w() * CELL, size.h() * CELL, 2, colour, 0.28F, colour, 0.7F, 0, 0);
    }

    /** The entry's tooltip, above everything (the panel is drawn behind the screen). */
    @SubscribeEvent
    public static void tooltip(ScreenEvent.Render.Post event) {
        var screen = event.getScreen();
        var layout = layout(screen);
        if (layout == null || !((AbstractContainerScreen<?>) screen).getMenu().getCarried().isEmpty()) return;
        if (!layout.contains(event.getMouseX(), event.getMouseY())) return;
        float mx = layout.vx(event.getMouseX()), my = layout.vy(event.getMouseY());
        var cell = hitAt(layout, mx, my);
        if (cell == null) return;
        var entry = GridDrawing.entryAt(cell.storage(), cell.section(), cell.x(), cell.y(), mx, my);
        if (entry != null) event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font, entry.stack(), event.getMouseX(), event.getMouseY());
    }

    @Nullable
    private static CellHit hitAt(Layout layout, float mx, float my) {
        if (my < 1 || my >= layout.h() - 1) return null;   // scrolled out of the panel
        for (var hit : HITS)
            if (mx >= hit.x() && my >= hit.y() && mx < hit.x() + hit.w() && my < hit.y() + hit.h()) return hit;
        return null;
    }

    // ---------------------------------------------------------------- input

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void pressed(ScreenEvent.MouseButtonPressed.Pre event) {
        var screen = event.getScreen();
        var layout = layout(screen);
        if (layout == null) return;
        var container = (AbstractContainerScreen<?>) screen;
        pressX = event.getMouseX();
        pressY = event.getMouseY();
        pendingUntil = 0;
        if (!layout.contains(event.getMouseX(), event.getMouseY())) {
            pressOurs = pressPicked = false;
            var slot = container.getSlotUnderMouse();
            slotPressPicked = container.getMenu().getCarried().isEmpty() && !Screen.hasShiftDown() && slot != null && slot.hasItem()
                    && (event.getButton() == 0 || event.getButton() == 1);
            return;
        }
        event.setCanceled(true);
        slotPressPicked = false;
        pressOurs = true;
        pressPicked = false;
        int button = event.getButton();
        if (button != 0 && button != 1) return;
        float mx = layout.vx(event.getMouseX()), my = layout.vy(event.getMouseY());
        var cell = hitAt(layout, mx, my);
        var carried = container.getMenu().getCarried();
        if (cell == null) return;
        var entry = GridDrawing.entryAt(cell.storage(), cell.section(), cell.x(), cell.y(), mx, my);
        int target = cell.target().ordinal();
        if (GunSling.isSling(carried) && entry != null && GunSling.canFit(entry.stack())) {
            send(GridActions.Kind.SLING, target, cell.section(), entry.x(), entry.y(), false, 0);
            return;
        }
        if (carried.isEmpty()) {
            if (entry == null) return;
            if (Screen.hasShiftDown()) {
                send(GridActions.Kind.TO_CONTAINER, target, cell.section(), entry.x(), entry.y(), false, 0);
                return;
            }
            rotated = entry.rotated();
            pressPicked = true;
            send(GridActions.Kind.PICK, target, cell.section(), entry.x(), entry.y(), false, button);
            return;
        }
        place(cell, carried, mx, my, button);
    }

    private static void place(CellHit cell, ItemStack carried, float mx, float my, int button) {
        var plan = GridDrawing.plan(cell.storage(), cell.section(), cell.x(), cell.y(), carried, rotated, mx, my);
        if (plan.kind() == GridActions.Kind.MERGE || plan.free() || plan.swap() && button == 0)
            send(plan.kind(), cell.target().ordinal(), cell.section(), plan.x(), plan.y(), rotated, button);
    }

    /**
     * The panel's own presses end here (vanilla never saw them, and would read the release with a stack on the cursor as
     * a click); a drag from the panel or from a container slot puts the stack down where it is let go.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void released(ScreenEvent.MouseButtonReleased.Pre event) {
        var screen = event.getScreen();
        var layout = layout(screen);
        if (layout == null) return;
        var carried = ((AbstractContainerScreen<?>) screen).getMenu().getCarried();
        boolean dragged = Math.hypot(event.getMouseX() - pressX, event.getMouseY() - pressY) >= DRAG * layout.s();
        if (pressOurs) {
            event.setCanceled(true);
            pressOurs = false;
            if (!pressPicked || !dragged) return;
            pressPicked = false;
            if (!carried.isEmpty()) dropAt(screen, layout, event.getMouseX(), event.getMouseY());
            else {
                pendingUntil = System.nanoTime() + DRAG_WAIT_NANOS;
                pendingX = event.getMouseX();
                pendingY = event.getMouseY();
            }
            return;
        }
        // from a container slot onto the panel: vanilla skips this release (its press picked up), the panel places
        if (slotPressPicked && dragged && !carried.isEmpty() && layout.contains(event.getMouseX(), event.getMouseY()))
            dropAt(screen, layout, event.getMouseX(), event.getMouseY());
        slotPressPicked = false;
    }

    /** The end of a drag: onto the panel's grids, or into the container slot under the mouse; elsewhere it stays carried. */
    private static void dropAt(Screen screen, Layout layout, double mouseX, double mouseY) {
        var container = (AbstractContainerScreen<?>) screen;
        var carried = container.getMenu().getCarried();
        if (carried.isEmpty()) return;
        if (layout.contains(mouseX, mouseY)) {
            float mx = layout.vx(mouseX), my = layout.vy(mouseY);
            var cell = hitAt(layout, mx, my);
            if (cell != null) place(cell, carried, mx, my, 0);
            return;
        }
        var slot = container.getSlotUnderMouse();
        var mc = Minecraft.getInstance();
        if (slot != null && mc.gameMode != null && mc.player != null)
            mc.gameMode.handleInventoryMouseClick(container.getMenu().containerId, slot.index, 0, ClickType.PICKUP, mc.player);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void scrolled(ScreenEvent.MouseScrolled.Pre event) {
        var layout = layout(event.getScreen());
        if (layout == null || !layout.contains(event.getMouseX(), event.getMouseY())) return;
        event.setCanceled(true);
        scroll = Math.max(0, Math.min(layout.content() - layout.h(), scroll - (float) event.getScrollDelta() * CELL));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void key(ScreenEvent.KeyPressed.Pre event) {
        var screen = event.getScreen();
        var layout = layout(screen);
        if (layout == null) return;
        var mc = Minecraft.getInstance();
        var key = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        var carried = ((AbstractContainerScreen<?>) screen).getMenu().getCarried();
        if (GridClient.ROTATE.isActiveAndMatches(key) && PlayerGrids.allowedInGrid(carried)) {
            rotated = !rotated;
            event.setCanceled(true);
            return;
        }
        var mouse = mc.mouseHandler;
        double mouseX = mouse.xpos() * screen.width / mc.getWindow().getScreenWidth();
        double mouseY = mouse.ypos() * screen.height / mc.getWindow().getScreenHeight();
        if (!carried.isEmpty() || !layout.contains(mouseX, mouseY)) return;
        float mx = layout.vx(mouseX), my = layout.vy(mouseY);
        var cell = hitAt(layout, mx, my);
        var entry = cell == null ? null : GridDrawing.entryAt(cell.storage(), cell.section(), cell.x(), cell.y(), mx, my);
        if (entry == null) return;
        int number = -1;
        for (int i = 0; i < Inventory.getSelectionSize(); i++)
            if (mc.options.keyHotbarSlots[i].isActiveAndMatches(key)) number = i;
        int target = cell.target().ordinal();
        if (number >= 0 && number < HotbarRoles.HAND_SLOT) {
            send(GridActions.Kind.HOTBAR_KEY, target, cell.section(), entry.x(), entry.y(), false, number);
        } else if (number >= HotbarRoles.HAND_SLOT) {
            if (cell.target() != PlayerGrids.Target.BACKPACK)
                send(GridActions.Kind.BIND, target, cell.section(), entry.x(), entry.y(), false, number - HotbarRoles.HAND_SLOT);
        } else if (mc.options.keyDrop.isActiveAndMatches(key)) {
            send(GridActions.Kind.DROP, target, cell.section(), entry.x(), entry.y(), false, Screen.hasControlDown() ? 1 : 0);
        } else return;
        event.setCanceled(true);
    }

    private static void send(GridActions.Kind kind, int target, int section, int x, int y, boolean turned, int extra) {
        GridPackets.send(new GridPackets.Action(kind.ordinal(), target, section, x, y, turned, extra));
    }
}
