package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.client.ui.AflUiTween;
import com.antaurora.apofirstlight.menu.DistributionPanelMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import static com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity.*;

/**
 * Distribution Panel screen (Building Power V1, docs/models/building_power_v1.md "界面"; style docs/ui/afl_overlay_ui_style_v1.md):
 * a dark translucent panel with a hairline over the dimmed world. Readings only, panelboard terms, no explanations (the
 * user's wiki explains; user 2026-10-09: "说明性太强……打算让玩家查wiki"):
 * <ul>
 *   <li>header: the title and one status word (正常 / 负载偏高 / 过载 N s / 过载脱扣 / 主断路器分闸 / 无进线 / 未识别建筑 /
 *   重复配电盘), coloured;</li>
 *   <li>the load ratio in large type, load / capacity FE/t, the storage as a battery;</li>
 *   <li>one segmented bar: the whole bar is the capacity (what the source network could give), the red mark at its end the
 *   trip line, a coloured segment per branch, the empty rest the headroom;</li>
 *   <li>the main breaker (合闸 / 分闸 / 脱扣), then the branches in use, each with a small bar and its share of the capacity,
 *   the spares as one count.</li>
 * </ul>
 * Readings are the panel's averages over a 6 s window (a whole number of every device's period, so they stand still); the
 * numbers here change twice a second, the bars ease toward them; 负载偏高 comes on at 80 % and goes off under 75 %.
 * The content under the header scrolls with the mouse wheel when the screen is too short. Each switch sends a menu
 * button click; the server owns the state.
 */
public final class DistributionPanelScreen extends AbstractContainerScreen<DistributionPanelMenu> {
    private static final int W = 300, HEADER = 28, MARGIN = 8, ROW_H = 17, ROW_GAP = 2, PAD = 12;
    private static final int GREEN = 0x7CC46E, AMBER = AflUiStyle.ACCENT, RED = 0xE06A5A, TRACK = 0xE8E4DC;
    /** The empty bar (the headroom) must read against the translucent panel (user 2026-10-09: at 0.08 it vanished). */
    private static final float TRACK_ALPHA = 0.16F;
    /** Branch colours by circuit kind (lighting, outlets, the feeder; device branches cycle the rest). */
    private static final int[] KIND_COLOUR = {0x8A867E, 0xE8C35E, 0x6FB0D2, 0xBD8BD8, 0x8FC98A, 0xE38E6B, 0x7DC9C0, 0xD9A0B8};
    /** Content y of the parts (under the header); the branch rows start at ROWS. */
    private static final int MAIN_Y = 62, MAIN_H = 24, BRANCHES_Y = 92, ROWS = 104;
    private float scroll;
    // what is shown: numbers held for half a second, bars easing toward them
    private int shownLoad, shownCapacity, shownStored;
    private final int[] shownSlot = new int[SLOTS];
    private final float[] barSlot = new float[SLOTS];
    private float barStored;
    private int sampleTicks, openTicks;
    private boolean high;
    private boolean sampled;
    private long lastFrame = Util.getMillis();
    private int laidOutRows = -1;

    public DistributionPanelScreen(DistributionPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = HEADER + contentHeight() + 6;
    }

    @Override
    protected void init() {
        laidOutRows = used().length;
        imageHeight = Math.min(HEADER + contentHeight() + 6, height - 2 * MARGIN);
        super.init();
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    /** The branch list comes with the first data sync, after init: fit the panel to it once it is known (or changes). */
    private void relayout() {
        int rows = used().length;
        if (rows == laidOutRows) return;
        laidOutRows = rows;
        imageHeight = Math.min(HEADER + contentHeight() + 6, height - 2 * MARGIN);
        topPos = (height - imageHeight) / 2;
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private static Component tr(String key, Object... args) { return Component.translatable("screen.apocalypse_firstlight.distribution_panel." + key, args); }
    private int flags() { return menu.get(DATA_FLAGS); }
    private boolean main() { return (flags() & FLAG_MAIN) != 0; }
    private boolean tripped() { return (flags() & FLAG_TRIPPED) != 0; }
    private int kind(int s) { return menu.get(DATA_SLOTS + s * 3); }
    private boolean off(int s) { return menu.get(DATA_SLOTS + s * 3 + 1) != 0; }
    private static int colour(int kind) { return KIND_COLOUR[Math.floorMod(kind, KIND_COLOUR.length)]; }

    /** The slots in use, in slot order (the panelboard's own order). */
    private int[] used() {
        int n = 0;
        int[] out = new int[SLOTS];
        for (int s = 0; s < SLOTS; s++) if (kind(s) != KIND_NONE) out[n++] = s;
        return java.util.Arrays.copyOf(out, n);
    }
    private int spares() { return SLOTS - used().length; }
    private int contentHeight() { return ROWS + used().length * (ROW_H + ROW_GAP) + 16; }

    // the scrolling area, in screen coordinates; content y 0 sits at its top
    private float areaTop() { return topPos + HEADER; }
    private float areaHeight() { return imageHeight - HEADER - 6; }
    private float maxScroll() { return Math.max(0, contentHeight() - areaHeight()); }
    private float cy(float contentY) { return areaTop() + contentY - scroll; }
    private boolean inArea(double mx, double my) { return mx >= leftPos && mx < leftPos + W && my >= areaTop() && my < areaTop() + areaHeight(); }

    // ---- readings ----

    @Override
    protected void containerTick() {
        super.containerTick();
        relayout();
        // every tick while the first data syncs in, then twice a second
        if (++openTicks <= 10 || ++sampleTicks >= 10) { sampleTicks = 0; sample(); }
    }

    private void sample() {
        shownLoad = menu.get(DATA_LOAD);
        shownCapacity = menu.get(DATA_CAPACITY);
        shownStored = menu.get(DATA_STORED);
        for (int s = 0; s < SLOTS; s++) shownSlot[s] = kind(s) == KIND_NONE || off(s) ? 0 : menu.get(DATA_SLOTS + s * 3 + 2);
        // 负载偏高 with a little hysteresis, so a load right at the line does not flicker
        high = shownCapacity > 0 && shownLoad >= (high ? 0.75F : 0.8F) * shownCapacity;
        if (!sampled) { for (int s = 0; s < SLOTS; s++) barSlot[s] = shownSlot[s]; barStored = shownStored; sampled = true; }
    }

    /** The status word and its colour, most urgent first. */
    private Component status(int[] colour) {
        int f = flags();
        if (tripped()) { colour[0] = RED; return tr("status_tripped"); }
        if (!main()) { colour[0] = AflUiStyle.TEXT_DIM; return tr("status_main_open"); }
        int overload = menu.get(DATA_OVERLOAD);
        if (overload > 0) { colour[0] = RED; return tr("status_overload", Math.max(1, Mth.ceil((TRIP_TICKS - overload) / 20F))); }
        if (shownCapacity <= 0) { colour[0] = AflUiStyle.TEXT_DIM; return tr("status_no_supply"); }
        if ((f & FLAG_DUPLICATE) != 0) { colour[0] = AMBER; return tr("status_duplicate"); }
        if ((f & FLAG_ZONE_OPEN) != 0) { colour[0] = AMBER; return tr("status_no_building"); }
        if (high || (f & FLAG_FALLING) != 0) { colour[0] = AMBER; return tr("status_high"); }
        colour[0] = GREEN;
        return tr("status_normal");
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!sampled) sample();
        long now = Util.getMillis();
        float ease = Mth.clamp((now - lastFrame) / 1000F * 6F, 0, 1);
        lastFrame = now;
        for (int s = 0; s < SLOTS; s++) barSlot[s] += (shownSlot[s] - barSlot[s]) * ease;
        barStored += (shownStored - barStored) * ease;

        renderBackground(g);
        float x = leftPos, y = topPos;
        AflUiDraw.panel(g, x, y, W, imageHeight, 1);
        // header (fixed): title, status
        AflUiDraw.text(g, font, tr("title"), x + PAD, y + 10, AflUiStyle.TEXT, 1);
        int[] sc = new int[1];
        Component status = status(sc);
        float pulse = sc[0] == RED && !tripped() ? 0.55F + 0.45F * (float) Math.abs(Math.sin(now / 260.0)) : 1;
        float sw = font.width(status);
        AflUiDraw.text(g, font, status, x + W - PAD - sw, y + 10, sc[0], pulse);
        AflUiShapes.fill(g, x + W - PAD - sw - 9, y + 11.5F, 5, 5, 2.5F, sc[0], pulse);
        AflUiShapes.fill(g, x + 10, y + 24, W - 20, 0.6F, 0, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA);

        int sx0 = Math.round(x), sy0 = Math.round(areaTop()), sx1 = Math.round(x + W), sy1 = Math.round(areaTop() + areaHeight());
        g.enableScissor(sx0, sy0, sx1, sy1);
        boolean hovering = inArea(mouseX, mouseY);
        boolean live = main() && !tripped() && shownCapacity > 0;
        int[] used = used();
        // the load ratio, load / capacity, the storage
        AflUiDraw.text(g, font, tr("load_ratio"), x + PAD, cy(6), AflUiStyle.TEXT_DIM, 1);
        if (live) {
            Component pct = Component.literal(Math.round(100F * shownLoad / shownCapacity) + "%");
            big(g, pct, x + PAD, cy(16), sc[0]);
            AflUiDraw.text(g, font, tr("load_of", shownLoad, shownCapacity), x + PAD + font.width(pct) * 2 + 6, cy(23), AflUiStyle.TEXT_DIM, 1);
        } else {
            AflUiShapes.fill(g, x + PAD, cy(23), 14, 1.5F, 0.75F, AflUiStyle.TEXT_DISABLED, 1);
        }
        Component store = tr("storage", shownStored);
        float stx = x + W - PAD - font.width(store);
        AflUiDraw.text(g, font, store, stx, cy(23), AflUiStyle.TEXT_DIM, 1);
        battery(g, stx - 20, cy(23), barStored / 100F);
        // the capacity bar: a segment per branch, the trip mark at the end
        float bx = x + PAD, bw = W - 2 * PAD, by = cy(36);
        AflUiShapes.fill(g, bx, by, bw, 8, 2, TRACK, TRACK_ALPHA);
        if (live) {
            // every segment rounded like the track, a hairline gap between them (user 2026-10-09: only the first was rounded)
            float at = 0;
            for (int s : used) {
                float w = Math.min(bw - at, bw * barSlot[s] / shownCapacity);
                if (w <= 0.05F) continue;
                AflUiShapes.fill(g, bx + at, by, Math.max(2, w - 1), 8, Math.min(2, w / 2), colour(kind(s)), 0.92F);
                at += w;
            }
        }
        AflUiShapes.fill(g, bx + bw - 1, by - 2.5F, 1.5F, 13, 0.6F, RED, 0.9F);
        AflUiDraw.text(g, font, Component.literal("0"), bx, cy(48), AflUiStyle.TEXT_DISABLED, 1);
        Component cap = tr("capacity", shownCapacity);
        AflUiDraw.text(g, font, cap, bx + bw - font.width(cap), cy(48), AflUiStyle.TEXT_DISABLED, 1);
        // the main breaker
        boolean hoverMain = hovering && inside(mouseX, mouseY, x + 10, cy(MAIN_Y), W - 20, MAIN_H);
        AflUiShapes.box(g, x + 10, cy(MAIN_Y), W - 20, MAIN_H, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, 0.5F, AflUiStyle.LINE,
                hoverMain ? AflUiStyle.LINE_HOVER_ALPHA : AflUiStyle.LINE_ALPHA, 0, 0);
        toggle(g, x + 18, cy(MAIN_Y + 5), 28, 14, main() && !tripped(), tripped());
        AflUiDraw.text(g, font, tr("main"), x + 54, cy(MAIN_Y + 8), AflUiStyle.TEXT, 1);
        Component mainState = tripped() ? tr("main_tripped") : main() ? tr("main_closed") : tr("main_open");
        AflUiDraw.text(g, font, mainState, x + W - 18 - font.width(mainState), cy(MAIN_Y + 8), tripped() ? RED : main() ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, 1);
        // the branches in use, then the spares as one count
        AflUiDraw.text(g, font, tr("branches"), x + PAD, cy(BRANCHES_Y), AflUiStyle.TEXT_DIM, 1);
        for (int i = 0; i < used.length; i++) {
            int s = used[i];
            float ry = cy(ROWS + i * (ROW_H + ROW_GAP)), rx = x + 10;
            boolean on = !off(s), hover = hovering && inside(mouseX, mouseY, rx, ry, W - 20, ROW_H);
            if (hover) AflUiShapes.box(g, rx, ry, W - 20, ROW_H, AflUiStyle.RADIUS_TILE, TRACK, 0.04F, AflUiStyle.LINE, AflUiStyle.LINE_HOVER_ALPHA, 0, 0);
            toggle(g, rx + 4, ry + 4, 18, 9, on, false);
            AflUiDraw.text(g, font, tr("kind_" + kind(s)), rx + 28, ry + 5, on ? AflUiStyle.TEXT : AflUiStyle.TEXT_DIM, 1);
            float mx0 = rx + 100, mw = W - 20 - 100 - 40;
            AflUiShapes.fill(g, mx0, ry + 7, mw, 3, 1.5F, TRACK, TRACK_ALPHA);
            if (live && on && barSlot[s] > 0.05F) AflUiShapes.fill(g, mx0, ry + 7, Math.max(3, Math.min(mw, mw * barSlot[s] / shownCapacity)), 3, 1.5F, colour(kind(s)), 0.92F);
            Component v = !on ? tr("branch_open") : live ? Component.literal(Math.round(100F * shownSlot[s] / shownCapacity) + "%") : Component.literal("—");
            AflUiDraw.text(g, font, v, rx + W - 20 - 6 - font.width(v), ry + 5, on && live ? AflUiStyle.TEXT : AflUiStyle.TEXT_DISABLED, 1);
        }
        if (spares() > 0) AflUiDraw.text(g, font, tr("spare", spares()), x + PAD, cy(ROWS + used.length * (ROW_H + ROW_GAP) + 3), AflUiStyle.TEXT_DISABLED, 1);
        g.disableScissor();
        // scroll bar, only when the content does not fit
        float max = maxScroll();
        if (max > 0) {
            float track = areaHeight() - 4, thumb = Math.max(12, track * areaHeight() / contentHeight()), at = (track - thumb) * scroll / max;
            AflUiShapes.fill(g, x + W - 5, areaTop() + 2, 2, track, 1, TRACK, 0.08F);
            AflUiShapes.fill(g, x + W - 5, areaTop() + 2 + at, 2, thumb, 1, TRACK, 0.45F);
        }
    }

    /** Text at twice the size (the load ratio). */
    private void big(GuiGraphics g, Component text, float x, float y, int rgb) {
        g.pose().pushPose();
        g.pose().translate(AflUiTween.snap(x), AflUiTween.snap(y), 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, text, 0, 0, AflUiStyle.argb(rgb, 1), false);
        g.pose().popPose();
    }

    /** A small battery: outline, terminal, the fill coloured by level. */
    private static void battery(GuiGraphics g, float x, float y, float level) {
        float w = 15, h = 8;
        AflUiShapes.box(g, x, y - 0.5F, w, h, 1.2F, TRACK, 0, AflUiStyle.TEXT_DIM, 0.9F, 0, 0);
        AflUiShapes.fill(g, x + w + 0.5F, y + 2, 1.5F, h - 5, 0.5F, AflUiStyle.TEXT_DIM, 0.9F);
        float f = Mth.clamp(level, 0, 1);
        if (f > 0.01F) AflUiShapes.fill(g, x + 1.5F, y + 1, (w - 3) * f, h - 3, 0.6F, f > 0.5F ? GREEN : f > 0.15F ? AMBER : RED, 0.95F);
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    /** A switch: amber knob right when closed; a tripped handle sits in the middle, red. */
    private static void toggle(GuiGraphics g, float x, float y, float w, float h, boolean on, boolean tripped) {
        int back = tripped ? RED : on ? AflUiStyle.ACCENT : TRACK;
        AflUiShapes.box(g, x, y, w, h, h / 2, back, tripped ? 0.25F : on ? 0.9F : 0.12F,
                tripped ? RED : on ? AflUiStyle.ACCENT : AflUiStyle.LINE, tripped ? 0.8F : on ? 1F : AflUiStyle.LINE_ALPHA, 0, 0);
        float d = h - 4, kx = tripped ? x + (w - d) / 2 : on ? x + w - 2 - d : x + 2;
        AflUiShapes.fill(g, kx, y + 2, d, d, d / 2, tripped ? RED : on ? 0x16181A : TRACK, 1);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maxScroll() > 0) { scroll = Mth.clamp(scroll - (float) delta * 14, 0, maxScroll()); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && inArea(mx, my)) {
            if (inside(mx, my, leftPos + 10, cy(MAIN_Y), W - 20, MAIN_H)) { press(DistributionPanelMenu.MAIN_BUTTON); return true; }
            int[] used = used();
            for (int i = 0; i < used.length; i++) {
                if (inside(mx, my, leftPos + 10, cy(ROWS + i * (ROW_H + ROW_GAP)), W - 20, ROW_H)) { press(DistributionPanelMenu.BRANCH_BUTTON + used[i]); return true; }
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
