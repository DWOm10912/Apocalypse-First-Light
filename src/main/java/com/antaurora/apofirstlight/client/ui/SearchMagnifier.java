package com.antaurora.apofirstlight.client.ui;

import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The magnifier over the slot being searched (docs/ui/afl_container_search_page_v1.md 3): a round lens with a pale rim, a
 * glint and a short handle, circling slowly. Drawn with AflUiShapes, so the edges stay smooth at any scale (it replaces the
 * old pixel pattern; user 2026-10-26: "做的圆润一点"). Sizes are fractions of the cell.
 */
public final class SearchMagnifier {
    private static final int RIM = 0xD0D5DA, INSIDE = 0x1E2124, GLASS = 0x90B4D0, GLINT = 0xF2F4F6, HANDLE = 0x9AA0A6;
    private static final long PERIOD_NANOS = 1_600_000_000L;

    private SearchMagnifier() {}

    /** Over the cell at (x, y), {@code cell} wide, in the current pose's units. */
    public static void draw(GuiGraphics g, float x, float y, float cell) {
        double turn = (double) (System.nanoTime() % PERIOD_NANOS) / PERIOD_NANOS * Math.PI * 2;
        float r = cell * 0.17F, rim = cell * 0.06F, orbit = cell * 0.09F, offset = cell * 0.07F;
        float cx = x + cell / 2 - offset + (float) Math.cos(turn) * orbit;
        float cy = y + cell / 2 - offset + (float) Math.sin(turn) * orbit;
        float handleW = cell * 0.085F, handleL = cell * 0.21F, outer = r + rim;

        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(45));   // +x now points down-right: the handle leaves the rim there
        AflUiShapes.fill(g, outer - rim * 0.5F, -handleW / 2, handleL + rim * 0.5F, handleW, handleW / 2, HANDLE, 1);
        pose.popPose();

        AflUiShapes.fill(g, cx - outer, cy - outer, outer * 2, outer * 2, outer, RIM, 1);
        AflUiShapes.fill(g, cx - r, cy - r, r * 2, r * 2, r, INSIDE, 1);
        AflUiShapes.fill(g, cx - r, cy - r, r * 2, r * 2, r, GLASS, 0.30F);
        float glint = r * 0.55F;
        AflUiShapes.fill(g, cx - r * 0.62F, cy - r * 0.62F, glint, glint, glint / 2, GLINT, 0.55F);
    }
}
