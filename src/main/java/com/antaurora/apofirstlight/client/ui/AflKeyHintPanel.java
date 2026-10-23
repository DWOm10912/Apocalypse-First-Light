package com.antaurora.apofirstlight.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The key hint panel (docs/ui/afl_overlay_ui_style_v1.md), PUBG style: top right, one row per action, right aligned,
 * "key cap + what it does", on a dark backing that fades out to the left. The owner hands over this frame's rows; the
 * panel slides in from the right on first show (150 ms), cross-fades and resizes when the rows change (120 ms), fades
 * out when hidden (100 ms), and each cap reacts to its key (AflKeyHint). Reusable: field attachment view, maintenance
 * bench, held tools (wiring wrench), later vehicles and machines. A row may put a modifier cap before its key (sneak +
 * scroll), or have no key at all: a status line (the wrench's circuit).
 */
public final class AflKeyHintPanel {
    public record Row(@Nullable AflKeyHint modifier, @Nullable AflKeyHint key, Component label) {
        public Row(AflKeyHint key, Component label) {
            this(null, key, label);
        }
    }

    private static final float MARGIN = 8, ROW = 14, PAD_Y = 4, PAD_RIGHT = 6, GAP = 5, MOD_GAP = 2, FADE_ZONE = 26;
    private final AflUiTween shown = new AflUiTween(0), height = new AflUiTween(0), width = new AflUiTween(0);
    private List<Row> rows = List.of(), previous = List.of();
    private long changed;
    /** The panel's bottom as last drawn, in GUI pixels from the screen's top (its top margin when nothing is shown). */
    private float bottom;

    /** Where the panel ended last frame: what sits under it (the item inspect view's name and description) goes there. */
    public float bottom() { return bottom; }

    /**
     * The panel's size everywhere (user 2026-10-17: at full size ten rows filled the top right while driving; user
     * 2026-10-22: the field attachment view's keys at full size were far too big), so every owner draws it the same.
     */
    public static final float SCALE = 0.62F;

    /** Draws the panel for this frame's rows at SCALE; visible = false fades it out. */
    public void render(GuiGraphics g, int screenWidth, List<Row> next, boolean visible) {
        render(g, screenWidth, next, visible, SCALE);
    }

    /**
     * Where the panel's top goes, in GUI pixels from the screen's top, when something is shown above it in the top right:
     * the bottom of the minimap and its position / biome box plus a gap (AFL Map V1, docs/ui/afl_map_v1_plan.md 6; set by
     * MinimapHud every frame, 0 when no minimap).
     */
    public static float topInset;

    /** As above, the whole panel (margins too) scaled by k about the screen's top right corner. */
    public void render(GuiGraphics g, int screenWidth, List<Row> next, boolean visible, float k) {
        g.pose().pushPose();
        // the panel's own top margin scales with it: under the minimap the top lands on topInset itself
        g.pose().translate(k == 1 ? 0 : screenWidth, topInset > 0 ? AflUiTween.snap(topInset - MARGIN * k) : 0, 0);
        if (k != 1) g.pose().scale(k, k, 1);
        float drawn = draw(g, k == 1 ? screenWidth : 0, next, visible);
        g.pose().popPose();
        bottom = (topInset > 0 ? AflUiTween.snap(topInset - MARGIN * k) : 0) + (MARGIN + drawn) * k;
    }

    /** Returns the panel's drawn height (0 when hidden). */
    private float draw(GuiGraphics g, int screenWidth, List<Row> next, boolean visible) {
        long now = System.nanoTime();
        Font font = Minecraft.getInstance().font;
        if (!same(next, rows)) {
            if (rows.isEmpty() || shown.value(now) <= 0.01F) {
                height.snapTo(next.size() * ROW);
                width.snapTo(contentWidth(font, next));
            } else {
                previous = rows;
                changed = now;
                height.to(next.size() * ROW, 120, AflUiTween.Ease.IN_OUT, now);
                width.to(contentWidth(font, next), 120, AflUiTween.Ease.IN_OUT, now);
            }
            rows = List.copyOf(next);
        }
        shown.to(visible && !rows.isEmpty() ? 1 : 0, visible ? 150 : 100, AflUiTween.Ease.OUT, now);
        float alpha = shown.value(now);
        if (alpha <= 0.01F) {
            previous = List.of();
            return 0;
        }
        float slide = AflUiTween.snap((1 - alpha) * 8);
        float w = width.value(now) + FADE_ZONE + PAD_RIGHT, h = height.value(now) + 2 * PAD_Y;
        float right = screenWidth - MARGIN + slide, top = MARGIN;
        AflUiShapes.box(g, right - w, top, w, h, AflUiStyle.RADIUS_PANEL, AflUiStyle.BACK, AflUiStyle.BACK_ALPHA * alpha,
                AflUiStyle.LINE, AflUiStyle.LINE_ALPHA * alpha, 0, FADE_ZONE / w);
        float cross = previous.isEmpty() ? 1 : AflUiTween.clamp01((now - changed) / 120_000_000f);
        if (cross < 1) drawRows(g, font, previous, right, top, alpha * (1 - cross), now);
        else previous = List.of();
        drawRows(g, font, rows, right, top, alpha * cross, now);
        return h;
    }

    private static void drawRows(GuiGraphics g, Font font, List<Row> rows, float right, float top, float alpha, long now) {
        if (alpha <= 0.02F) return;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float y = top + PAD_Y + i * ROW, labelX = right - PAD_RIGHT - font.width(row.label());
            AflUiDraw.text(g, font, row.label(), labelX, y + 3.5F, AflUiStyle.TEXT, alpha);
            if (row.key() == null) continue;
            float cap = row.key().width(font), keyX = AflUiTween.snap(labelX - GAP - cap);
            row.key().draw(g, font, keyX, y + 1.5F, alpha, now);
            if (row.modifier() != null)
                row.modifier().draw(g, font, AflUiTween.snap(keyX - MOD_GAP - row.modifier().width(font)), y + 1.5F, alpha, now);
        }
    }

    private static float contentWidth(Font font, List<Row> rows) {
        float w = 0;
        for (Row row : rows) w = Math.max(w, caps(font, row) + font.width(row.label()));
        return w;
    }

    private static float caps(Font font, Row row) {
        if (row.key() == null) return 0;
        return row.key().width(font) + GAP + (row.modifier() == null ? 0 : row.modifier().width(font) + MOD_GAP);
    }

    private static boolean same(List<Row> a, List<Row> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (a.get(i).key() != b.get(i).key() || a.get(i).modifier() != b.get(i).modifier() || !Objects.equals(a.get(i).label().getString(), b.get(i).label().getString())) return false;
        return true;
    }
}
