package com.antaurora.apofirstlight.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A row of item cells in the overlay style (docs/ui/afl_overlay_ui_style_v1.md 4.1): the hotbar, the field / bench
 * attachment candidates and the bench's own hotbar row. Cell i is the 20 x 20 GUI px square at (x0 + 20 i, y), as the
 * vanilla hotbar grid; its tile is the 18 x 18 inside, its item at (+2, +2).
 * <ul>
 *   <li>The active cell (selected slot, or the hovered one where nothing is selected) lifts its item like a Windows 11
 *   taskbar icon: up to 4 GUI px in 60 ms, settling at 3 in the next 60 ms; a cell that stops being active drops back in
 *   120 ms, each on its own clock, so fast scrolling never queues.</li>
 *   <li>An amber pill under the active cell slides to the next one: its leading end moves first (90 ms), the trailing
 *   end follows (160 ms), so it stretches and then shrinks.</li>
 *   <li>Tiles brighten while active or hovered (80 ms); {@link #appear} staggers the cells in (20 ms apart, 120 ms each,
 *   rising 4 px).</li>
 * </ul>
 * Offsets are snapped to screen pixels and items are never scaled.
 */
public final class AflItemStrip {
    /** Draws cell i's contents (item, decorations) with its top-left at (x, y) GUI px, already lifted. */
    @FunctionalInterface
    public interface Cell {
        void draw(GuiGraphics g, int i, int x, int y);
    }

    private static final int MAX = 16;
    private final AflUiTween[] drop = new AflUiTween[MAX], glow = new AflUiTween[MAX];
    private final AflUiTween lead = new AflUiTween(0), trail = new AflUiTween(0), pill = new AflUiTween(0);
    private int active = -1;
    private long activeSince, appearStart = Long.MIN_VALUE;
    private boolean placed;

    public AflItemStrip() {
        for (int i = 0; i < MAX; i++) {
            drop[i] = new AflUiTween(0);
            glow[i] = new AflUiTween(0);
        }
    }

    /** Stagger the cells in from now (a candidate row opening). */
    public void appear() {
        appearStart = System.nanoTime();
    }

    /**
     * Draws the strip. active: the selected / hovered cell or -1; hovered: the cell under the mouse or -1; backing: the
     * long panel behind the cells (the hotbar); alpha fades the shapes (items appear once a cell is half visible).
     */
    public void render(GuiGraphics g, float x0, float y, int count, int active, int hovered, boolean backing, float alpha, Cell cell) {
        long now = System.nanoTime();
        count = Math.min(count, MAX);
        if (active != this.active) {
            if (this.active >= 0 && this.active < MAX) drop[this.active].snapTo(lift(now - activeSince)).to(0, 120, AflUiTween.Ease.OUT, now);
            this.active = active;
            activeSince = now;
            if (active >= 0) {
                float centre = x0 + active * 20 + 10;
                if (!placed) {
                    lead.snapTo(centre);
                    trail.snapTo(centre);
                    placed = true;
                } else {
                    lead.to(centre, 90, AflUiTween.Ease.OUT, now);
                    trail.to(centre, 160, AflUiTween.Ease.IN_OUT, now);
                }
            }
        }
        pill.to(active >= 0 ? 1 : 0, 120, AflUiTween.Ease.OUT, now);
        if (backing) AflUiShapes.box(g, x0 - 1, y - 1, count * 20 + 2, 22, AflUiStyle.RADIUS_PANEL, AflUiStyle.BACK,
                AflUiStyle.BACK_ALPHA * alpha, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA * alpha, AflUiStyle.SHADOW_ALPHA * alpha, 0);
        for (int i = 0; i < count; i++) {
            float shown = appearStart == Long.MIN_VALUE ? 1 : AflUiTween.clamp01((now - appearStart - i * 20_000_000L) / 120_000_000f);
            shown = AflUiTween.Ease.OUT.apply(shown);
            float rise = AflUiTween.snap((1 - shown) * 4), a = alpha * shown;
            glow[i].to(i == active || i == hovered ? 1 : 0, 80, AflUiTween.Ease.LINEAR, now);
            float lit = glow[i].value(now);
            float tx = x0 + i * 20 + 1, ty = y + 1 + rise;
            // free-standing tiles (no long backing) carry their own backing, hairline and shadow; then a light tint that
            // brightens while active / hovered (on the hotbar the hairline appears only then)
            if (!backing) AflUiShapes.box(g, tx, ty, 18, 18, AflUiStyle.RADIUS_TILE, AflUiStyle.BACK, AflUiStyle.BACK_ALPHA * a,
                    AflUiStyle.LINE, (AflUiStyle.LINE_ALPHA + (AflUiStyle.LINE_HOVER_ALPHA - AflUiStyle.LINE_ALPHA) * lit) * a,
                    AflUiStyle.SHADOW_ALPHA * a, 0);
            AflUiShapes.box(g, tx, ty, 18, 18, AflUiStyle.RADIUS_TILE, AflUiStyle.LINE, (0.035F + 0.085F * lit) * a,
                    AflUiStyle.LINE, backing ? 0.45F * lit * a : 0, 0, 0);
            if (shown < 0.5F) continue;
            float up = i == active ? lift(now - activeSince) : drop[i].value(now);
            g.pose().pushPose();
            g.pose().translate(0, rise - AflUiTween.snap(up), 0);
            cell.draw(g, i, (int) (x0 + i * 20) + 2, (int) y + 2);
            g.pose().popPose();
        }
        float p = pill.value(now) * alpha;
        if (p > 0.01F) {
            float a = lead.value(now), b = trail.value(now), left = Math.min(a, b) - 4, right = Math.max(a, b) + 4;
            AflUiShapes.fill(g, left, y + 17.5F, right - left, 1.5F, 0.75F, AflUiStyle.ACCENT, 0.95F * p);
        }
    }

    /** The active item's lift, GUI px, t ns after it became active: up to 4 in 60 ms, settling at 3 by 120 ms. */
    private static float lift(long t) {
        float ms = t / 1_000_000f;
        if (ms < 60) return 4 * AflUiTween.Ease.OUT.apply(ms / 60);
        if (ms < 120) return 4 - AflUiTween.Ease.IN_OUT.apply((ms - 60) / 60);
        return 3;
    }
}
