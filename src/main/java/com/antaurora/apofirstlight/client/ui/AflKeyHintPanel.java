package com.antaurora.apofirstlight.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/**
 * The key hint panel (docs/ui/afl_overlay_ui_style_v1.md), PUBG style: top right, one row per action, right aligned,
 * "key cap + what it does", on a dark backing that fades out to the left. The owner hands over this frame's rows; the
 * panel slides in from the right on first show (150 ms), cross-fades and resizes when the rows change (120 ms), fades
 * out when hidden (100 ms), and each cap reacts to its key (AflKeyHint). Reusable: field attachment view, maintenance
 * bench, later vehicles and machines.
 */
public final class AflKeyHintPanel {
    public record Row(AflKeyHint key, Component label) {}

    private static final float MARGIN = 8, ROW = 14, PAD_Y = 4, PAD_RIGHT = 6, GAP = 5, FADE_ZONE = 26;
    private final AflUiTween shown = new AflUiTween(0), height = new AflUiTween(0), width = new AflUiTween(0);
    private List<Row> rows = List.of(), previous = List.of();
    private long changed;

    /** Draws the panel for this frame's rows; visible = false fades it out. */
    public void render(GuiGraphics g, int screenWidth, List<Row> next, boolean visible) {
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
            return;
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
    }

    private static void drawRows(GuiGraphics g, Font font, List<Row> rows, float right, float top, float alpha, long now) {
        if (alpha <= 0.02F) return;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float y = top + PAD_Y + i * ROW, labelX = right - PAD_RIGHT - font.width(row.label());
            AflUiDraw.text(g, font, row.label(), labelX, y + 3.5F, AflUiStyle.TEXT, alpha);
            float cap = row.key().width(font);
            row.key().draw(g, font, AflUiTween.snap(labelX - GAP - cap), y + 1.5F, alpha, now);
        }
    }

    private static float contentWidth(Font font, List<Row> rows) {
        float w = 0;
        for (Row row : rows) w = Math.max(w, row.key().width(font) + GAP + font.width(row.label()));
        return w;
    }

    private static boolean same(List<Row> a, List<Row> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (a.get(i).key() != b.get(i).key() || !Objects.equals(a.get(i).label().getString(), b.get(i).label().getString())) return false;
        return true;
    }
}
