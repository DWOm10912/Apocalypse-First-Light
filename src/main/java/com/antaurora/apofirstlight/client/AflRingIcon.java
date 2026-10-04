package com.antaurora.apofirstlight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Glyphs for the survival HUD (signed distance fields in a unit box, drawn with anti-aliased edges) and their helpers;
 * a glyph alone (e.g. a tooltip icon). The ring gauges themselves are client/AflGauge. Positions and sizes in GUI pixels.
 */
public final class AflRingIcon {
    /** Signed distance to a glyph in a unit box (x right, y down), negative inside. */
    @FunctionalInterface public interface Glyph { double distance(double x, double y); }

    private AflRingIcon() {}

    /** A glyph alone, in a square of `size` GUI pixels with its top-left corner at (guiX, guiY), e.g. a tooltip icon. */
    public static void glyph(GuiGraphics graphics, double guiX, double guiY, double size, int rgb, double alpha, Glyph glyph) {
        int s = (int)Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        double x0 = guiX * s, y0 = guiY * s, box = size * s;
        graphics.pose().pushPose();
        graphics.pose().scale(1.0f / s, 1.0f / s, 1.0f);
        graphics.drawManaged(() -> {
            for (int py = (int)Math.floor(y0); py < Math.ceil(y0 + box); py++)
                for (int px = (int)Math.floor(x0); px < Math.ceil(x0 + box); px++) {
                    double cover = clamp(0.5 - glyph.distance((px + 0.5 - x0) / box, (py + 0.5 - y0) / box) * box);
                    if (cover > 0) pixel(graphics, px, py, rgb, cover * alpha);
                }
        });
        graphics.pose().popPose();
    }

    /** Distance from a point to a segment (glyph strokes). */
    public static double segment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(px - ax - t * dx, py - ay - t * dy);
    }
    /** Signed distance to a convex polygon listed clockwise on screen (x right, y down: top, right, left); sharp corners. */
    public static double convex(double px, double py, double... xy) {
        double d = -Double.MAX_VALUE;
        for (int i = 0; i < xy.length; i += 2) {
            double ax = xy[i], ay = xy[i + 1], bx = xy[(i + 2) % xy.length], by = xy[(i + 3) % xy.length];
            double ex = bx - ax, ey = by - ay, len = Math.hypot(ex, ey);
            d = Math.max(d, ((px - ax) * ey - (py - ay) * ex) / len); // outward normal (ey, -ex)
        }
        return d;
    }
    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    private static void pixel(GuiGraphics graphics, int x, int y, int rgb, double alpha) {
        int a = (int)Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        if (a > 0) graphics.fill(x, y, x + 1, y + 1, a << 24 | (rgb & 0xFFFFFF));
    }
}
