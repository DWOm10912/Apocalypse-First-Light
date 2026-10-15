package com.antaurora.apofirstlight.client.inventory;

import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.inventory.grid.GridActions;
import com.antaurora.apofirstlight.inventory.grid.GridStorage;
import com.antaurora.apofirstlight.inventory.grid.ItemSizes;
import com.antaurora.apofirstlight.inventory.grid.Loadout;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Grid drawing and placement shared by the inventory page (AflInventoryScreen) and the grid side panel of container
 * screens (ContainerGridPanel), docs/ui/afl_inventory_grid_v0_plan.md 5, 13. Sizes are in the caller's canvas units.
 */
final class GridDrawing {
    static final float CELL = 18;
    static final int FREE = 0x7FC27A, BLOCKED = 0xD06050, SWAP = 0xE0A84A;

    private GridDrawing() {}

    /** The empty cells of one section from (x, y). */
    static void cells(GuiGraphics g, int w, int h, float x, float y) {
        for (int cy = 0; cy < h; cy++)
            for (int cx = 0; cx < w; cx++)
                AflUiShapes.box(g, x + cx * CELL, y + cy * CELL, CELL, CELL, 1.5F, AflUiStyle.BACK, 0.45F, AflUiStyle.LINE, 0.14F, 0, 0);
    }

    /** The reserved cells of a section (the hand's origin), outlined. */
    static void reserved(GuiGraphics g, GridStorage storage, int section, float x, float y) {
        var reserved = storage.reserved();
        if (reserved != null && reserved.section() == section)
            AflUiShapes.box(g, x + reserved.x() * CELL + 1, y + reserved.y() * CELL + 1, reserved.w() * CELL - 2,
                    reserved.h() * CELL - 2, 2, AflUiStyle.BACK, 0, AflUiStyle.LINE, 0.45F, 0, 0);
    }

    /** One grid entry: its tile and icon. */
    static void entry(GuiGraphics g, Font font, ItemStack stack, float x, float y, ItemSizes.Size size, boolean turned, boolean hover) {
        float w = size.w() * CELL, h = size.h() * CELL;
        AflUiShapes.box(g, x + 0.5F, y + 0.5F, w - 1, h - 1, 2, 0x2A2D30, 0.88F, AflUiStyle.LINE,
                hover ? AflUiStyle.LINE_HOVER_ALPHA : 0.30F, 0, 0);
        item(g, font, stack, x, y, w, h, turned);
    }

    /** An item icon fitted into a box (scaled by whole steps of the shorter side), turned a quarter for rotated items. */
    static void item(GuiGraphics g, Font font, ItemStack stack, float x, float y, float w, float h, boolean turned) {
        float icon = Math.max(1, (float) Math.floor(Math.min(w, h) / CELL)) * 16;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + w / 2, y + h / 2, 0);
        if (turned) pose.mulPose(Axis.ZP.rotationDegrees(90));
        pose.scale(icon / 16, icon / 16, 1);
        g.renderItem(stack, -8, -8);
        pose.popPose();
        pose.pushPose();
        pose.translate(x + w - 17, y + h - 17, 0);
        g.renderItemDecorations(font, stack, 0, 0);
        pose.popPose();
    }

    /** The amber key number on a stack whose kind is bound to 4-9, in the top-right corner ending at (right, top). */
    static void bindBadge(GuiGraphics g, Font font, ItemStack stack, float right, float top) {
        var binds = Loadout.client().binds();
        for (int i = 0; i < binds.length; i++) {
            if (binds[i] != stack.getItem()) continue;
            var pose = g.pose();
            pose.pushPose();
            pose.translate(0, 0, 220);
            AflUiShapes.fill(g, right - 6.5F, top + 1, 5.5F, 5.5F, 1.5F, AflUiStyle.ACCENT, 1);
            text(g, font, Component.literal(Integer.toString(4 + i)), right - 5.2F, top + 1.6F, 4.6F, AflUiStyle.KEY_TEXT, 1);
            pose.popPose();
            return;
        }
    }

    /** Text with its line height scaled to {@code size} canvas units. */
    static void text(GuiGraphics g, Font font, Component text, float x, float y, float size, int rgb, float alpha) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float s = size / font.lineHeight;
        pose.scale(s, s, 1);
        g.drawString(font, text, 0, 0, AflUiStyle.argb(rgb, alpha), false);
        pose.popPose();
    }

    static float width(Font font, Component text, float size) {
        return font.width(text) * size / font.lineHeight;
    }

    /** What a click with the carried stack would do in a section drawn at (cellX, cellY): merge, or place / swap at (x, y). */
    record Plan(GridActions.Kind kind, int x, int y, boolean free, boolean swap) {}

    @Nullable
    static GridStorage.Entry entryAt(GridStorage storage, int section, float cellX, float cellY, float mx, float my) {
        return storage.at(section, (int) Math.floor((mx - cellX) / CELL), (int) Math.floor((my - cellY) / CELL));
    }

    static Plan plan(GridStorage storage, int sectionIndex, float cellX, float cellY, ItemStack carried, boolean rotated, float mx, float my) {
        var under = entryAt(storage, sectionIndex, cellX, cellY, mx, my);
        if (under != null && ItemStack.isSameItemSameTags(under.stack(), carried)
                && under.stack().getCount() < Math.min(under.stack().getMaxStackSize(), 64))
            return new Plan(GridActions.Kind.MERGE, under.x(), under.y(), true, false);
        var size = ItemSizes.of(carried, true).turned(rotated);
        var section = storage.layout().section(sectionIndex);
        int x = Math.round((mx - cellX) / CELL - size.w() / 2F), y = Math.round((my - cellY) / CELL - size.h() / 2F);
        x = Math.max(0, Math.min(x, section.w() - size.w()));
        y = Math.max(0, Math.min(y, section.h() - size.h()));
        if (!PlayerGrids.allowedInGrid(carried)) return new Plan(GridActions.Kind.PLACE, x, y, false, false);
        if (storage.fits(carried, sectionIndex, x, y, rotated, null)) return new Plan(GridActions.Kind.PLACE, x, y, true, false);
        GridStorage.Entry covered = null;
        boolean single = true;
        for (int cy = y; cy < y + size.h(); cy++)
            for (int cx = x; cx < x + size.w(); cx++) {
                var e = storage.at(sectionIndex, cx, cy);
                if (e == null || e == covered) continue;
                if (covered != null) single = false;
                covered = e;
            }
        boolean swap = single && covered != null && storage.fits(carried, sectionIndex, x, y, rotated, covered);
        return new Plan(GridActions.Kind.PLACE, x, y, false, swap);
    }
}
