package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.menu.DistributionPanelMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import static com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity.*;

/**
 * Distribution Panel screen (Building Power V1, docs/models/building_power_v1.md; style docs/ui/afl_overlay_ui_style_v1.md):
 * a dark translucent panel with a hairline over the dimmed world. A fixed-width panel no taller than the screen: the
 * header stays, everything under it (supply and load, the main breaker, the zone, the twelve branch breakers in two
 * columns as in the panelboard, slot 0..5 left, 6..11 right) scrolls with the mouse wheel when it does not fit (user
 * 2026-10-07: at a large GUI scale the first version ran off the screen). Each switch sends a menu button click; the
 * server owns the state.
 */
public final class DistributionPanelScreen extends AbstractContainerScreen<DistributionPanelMenu> {
    private static final int W = 300, HEADER = 28, MARGIN = 8, ROW_H = 17, ROW_GAP = 2, GRID_Y = 102, CONTENT_H = GRID_Y + 6 * (ROW_H + ROW_GAP) + 2;
    private static final int RED = 0xE06A5A;
    private float scroll;

    public DistributionPanelScreen(DistributionPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = HEADER + CONTENT_H + 6;
    }

    @Override
    protected void init() {
        imageHeight = Math.min(HEADER + CONTENT_H + 6, height - 2 * MARGIN);
        super.init();
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private static Component tr(String key, Object... args) { return Component.translatable("screen.apocalypse_firstlight.distribution_panel." + key, args); }
    private int flags() { return menu.get(DATA_FLAGS); }
    private boolean main() { return (flags() & FLAG_MAIN) != 0; }
    private boolean tripped() { return (flags() & FLAG_TRIPPED) != 0; }
    private int kind(int s) { return menu.get(DATA_SLOTS + s * 3); }
    private boolean off(int s) { return menu.get(DATA_SLOTS + s * 3 + 1) != 0; }
    private int load(int s) { return menu.get(DATA_SLOTS + s * 3 + 2); }

    // the scrolling area, in screen coordinates; content y 0 sits at its top
    private float areaTop() { return topPos + HEADER; }
    private float areaHeight() { return imageHeight - HEADER - 6; }
    private float maxScroll() { return Math.max(0, CONTENT_H - areaHeight()); }
    private float cy(float contentY) { return areaTop() + contentY - scroll; }
    private float rowX(int s) { return leftPos + 10 + (s < 6 ? 0 : (W - 20 + 6) / 2F); }
    private float rowContentY(int s) { return GRID_Y + (s % 6) * (ROW_H + ROW_GAP); }
    private float rowW() { return (W - 20 - 6) / 2F; }
    private boolean inArea(double mx, double my) { return mx >= leftPos && mx < leftPos + W && my >= areaTop() && my < areaTop() + areaHeight(); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        float x = leftPos, y = topPos;
        AflUiDraw.panel(g, x, y, W, imageHeight, 1);
        // header (fixed)
        AflUiDraw.text(g, font, tr("title"), x + 12, y + 10, AflUiStyle.TEXT, 1);
        Component state = tripped() ? tr("state_tripped") : main() ? tr("state_on") : tr("state_off");
        int stateColor = tripped() ? RED : main() ? AflUiStyle.ACCENT : AflUiStyle.TEXT_DIM;
        AflUiDraw.text(g, font, state, x + W - 12 - font.width(state), y + 10, stateColor, 1);
        AflUiShapes.fill(g, x + 10, y + 24, W - 20, 0.6F, 0, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA);
        // the scrolling content, clipped to the area
        int sx0 = Math.round(x), sy0 = Math.round(areaTop()), sx1 = Math.round(x + W), sy1 = Math.round(areaTop() + areaHeight());
        g.enableScissor(sx0, sy0, sx1, sy1);
        boolean hovering = inArea(mouseX, mouseY);
        int supply = menu.get(DATA_SUPPLY), load = menu.get(DATA_LOAD), stored = menu.get(DATA_STORED);
        AflUiDraw.text(g, font, tr("supply"), x + 12, cy(6), AflUiStyle.TEXT_DIM, 1);
        AflUiDraw.text(g, font, tr("fe_per_tick", supply), x + 48, cy(6), AflUiStyle.TEXT, 1);
        Component buf = tr("buffer", stored);
        AflUiDraw.text(g, font, buf, x + W - 12 - font.width(buf), cy(6), AflUiStyle.TEXT_DIM, 1);
        bar(g, x + 48, cy(17), W - 60, stored / 100F, AflUiStyle.TEXT, 0.55F);
        AflUiDraw.text(g, font, tr("load"), x + 12, cy(26), AflUiStyle.TEXT_DIM, 1);
        AflUiDraw.text(g, font, tr("fe_per_tick", load), x + 48, cy(26), AflUiStyle.TEXT, 1);
        Component hint = tr("trip_rule");
        AflUiDraw.text(g, font, hint, x + W - 12 - font.width(hint), cy(26), AflUiStyle.TEXT_DISABLED, 1);
        bar(g, x + 48, cy(37), W - 60, supply <= 0 ? (load > 0 ? 1 : 0) : Math.min(1, load / (float) supply), AflUiStyle.ACCENT, 0.9F);
        // the main breaker
        boolean hoverMain = hovering && inside(mouseX, mouseY, x + 10, cy(46), W - 20, 26);
        AflUiShapes.box(g, x + 10, cy(46), W - 20, 26, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.5F, AflUiStyle.LINE,
                hoverMain ? AflUiStyle.LINE_HOVER_ALPHA : AflUiStyle.LINE_ALPHA, 0, 0);
        toggle(g, x + 18, cy(52), 28, 14, main() && !tripped(), false);
        AflUiDraw.text(g, font, tr("main"), x + 54, cy(55), AflUiStyle.TEXT, 1);
        Component mainNote = tripped() ? tr("main_tripped") : tr("main_note");
        AflUiDraw.text(g, font, mainNote, x + 54 + font.width(tr("main")) + 8, cy(55), tripped() ? RED : AflUiStyle.TEXT_DIM, 1);
        // the zone
        Component zone = (flags() & FLAG_ZONE_OK) != 0 ? tr("zone_ok") : (flags() & FLAG_DUPLICATE) != 0 ? tr("zone_duplicate")
                : (flags() & FLAG_ZONE_OPEN) != 0 ? tr("zone_open") : tr("zone_pending");
        AflUiDraw.text(g, font, zone, x + 12, cy(78), AflUiStyle.TEXT_DISABLED, 1);
        // branch breakers
        AflUiDraw.text(g, font, tr("branches"), x + 12, cy(90), AflUiStyle.TEXT_DIM, 1);
        for (int s = 0; s < SLOTS; s++) {
            float rx = rowX(s), ry = cy(rowContentY(s)), rw = rowW();
            int k = kind(s);
            boolean spare = k == KIND_NONE, hover = !spare && hovering && inside(mouseX, mouseY, rx, ry, rw, ROW_H), on = !spare && !off(s);
            AflUiShapes.box(g, rx, ry, rw, ROW_H, AflUiStyle.RADIUS_TILE, 0xE8E4DC, spare ? 0.02F : 0.05F, AflUiStyle.LINE,
                    hover ? AflUiStyle.LINE_HOVER_ALPHA : spare ? 0.1F : 0.18F, 0, 0);
            toggle(g, rx + 5, ry + 4, 18, 9, on, spare);
            Component name = spare ? tr("spare") : tr("kind_" + k);
            AflUiDraw.text(g, font, name, rx + 28, ry + 5, spare ? AflUiStyle.TEXT_DISABLED : on ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, 1);
            if (!spare) {
                Component fe = on ? tr("fe_per_tick", load(s)) : tr("branch_off");
                AflUiDraw.text(g, font, fe, rx + rw - 5 - font.width(fe), ry + 5, on ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1);
            }
        }
        g.disableScissor();
        // scroll bar, only when the content does not fit
        float max = maxScroll();
        if (max > 0) {
            float track = areaHeight() - 4, thumb = Math.max(12, track * areaHeight() / CONTENT_H), at = (track - thumb) * scroll / max;
            AflUiShapes.fill(g, x + W - 5, areaTop() + 2, 2, track, 1, 0xE8E4DC, 0.08F);
            AflUiShapes.fill(g, x + W - 5, areaTop() + 2 + at, 2, thumb, 1, 0xE8E4DC, 0.45F);
        }
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private static void bar(GuiGraphics g, float x, float y, float w, float f, int rgb, float alpha) {
        AflUiShapes.fill(g, x, y, w, 3, 1.5F, 0xE8E4DC, 0.1F);
        if (f > 0) AflUiShapes.fill(g, x, y, Math.max(3, w * Math.min(1, f)), 3, 1.5F, rgb, alpha);
    }

    private static void toggle(GuiGraphics g, float x, float y, float w, float h, boolean on, boolean disabled) {
        AflUiShapes.box(g, x, y, w, h, h / 2, disabled ? 0x6C6A66 : on ? AflUiStyle.ACCENT : 0xE8E4DC, disabled ? 0.2F : on ? 0.9F : 0.12F,
                disabled ? 0x6C6A66 : on ? AflUiStyle.ACCENT : AflUiStyle.LINE, disabled ? 0.35F : on ? 1F : AflUiStyle.LINE_ALPHA, 0, 0);
        float d = h - 4, kx = on ? x + w - 2 - d : x + 2;
        AflUiShapes.fill(g, kx, y + 2, d, d, d / 2, disabled ? 0x55534F : on ? 0x16181A : 0xE8E4DC, 1);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maxScroll() > 0) { scroll = Mth.clamp(scroll - (float) delta * 14, 0, maxScroll()); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && inArea(mx, my)) {
            if (inside(mx, my, leftPos + 10, cy(46), W - 20, 26)) { press(DistributionPanelMenu.MAIN_BUTTON); return true; }
            for (int s = 0; s < SLOTS; s++) {
                if (kind(s) != KIND_NONE && inside(mx, my, rowX(s), cy(rowContentY(s)), rowW(), ROW_H)) { press(DistributionPanelMenu.BRANCH_BUTTON + s); return true; }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void press(int id) {
        if (minecraft == null || minecraft.gameMode == null) return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.35F));
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {}
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}
    @Override public boolean isPauseScreen() { return false; }
}
