package com.antaurora.apofirstlight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A small HUD gauge: a ring filling clockwise from the top, with a glyph inside, drawn in real pixels with
 * anti-aliased edges from distance fields (temporary survival HUD: stamina, thirst). Positions and sizes are given in
 * GUI pixels.
 */
public final class AflRingIcon {
    /** Signed distance to a glyph in a unit box (x right, y down), negative inside. */
    @FunctionalInterface public interface Glyph { double distance(double x, double y); }

    private AflRingIcon() {}

    public static void draw(GuiGraphics graphics, double guiCx, double guiCy, double radius, double band, double fraction,
                            int rgb, double alpha, Glyph glyph) {
        int s = (int)Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        double cx = guiCx * s, cy = guiCy * s, outer = radius * s, inner = (radius - band) * s;
        double sweep = Math.max(0, Math.min(1, fraction)) * Math.PI * 2;
        double size = inner * 2 * 0.8, gx = cx - size / 2, gy = cy - size / 2;
        graphics.pose().pushPose();
        graphics.pose().scale(1.0f / s, 1.0f / s, 1.0f);
        graphics.drawManaged(() -> {
            int x0 = (int)Math.floor(cx - outer - 1), x1 = (int)Math.ceil(cx + outer + 1);
            int y0 = (int)Math.floor(cy - outer - 1), y1 = (int)Math.ceil(cy + outer + 1);
            for (int py = y0; py < y1; py++) for (int px = x0; px < x1; px++) {
                double x = px + 0.5 - cx, y = py + 0.5 - cy, r = Math.hypot(x, y);
                double ring = clamp(0.5 - (Math.abs(r - (outer + inner) / 2) - (outer - inner) / 2));
                if (ring > 0) {
                    pixel(graphics, px, py, 0x101010, 0x90 / 255.0 * ring * alpha);
                    double fill = sweep >= Math.PI * 2 - 1e-3 ? 1 : sweep <= 0 ? 0 : arc(Math.atan2(x, -y), sweep, r);
                    if (fill > 0) pixel(graphics, px, py, rgb, ring * fill * alpha);
                } else if (r < inner) {
                    double cover = clamp(0.5 - glyph.distance((px + 0.5 - gx) / size, (py + 0.5 - gy) / size) * size);
                    if (cover > 0) pixel(graphics, px, py, rgb, cover * alpha);
                }
            }
        });
        graphics.pose().popPose();
    }

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

    /** Coverage of a pixel by the clockwise arc from the top over `sweep`, anti-aliased at both ends (pixels at radius r). */
    private static double arc(double angle, double sweep, double r) {
        double tau = Math.PI * 2, theta = angle < 0 ? angle + tau : angle; // 0..2π clockwise from the top
        // signed angular distance into [0, sweep] (negative outside, to the nearer end), times the radius
        double inside = theta <= sweep ? Math.min(theta, sweep - theta) : -Math.min(theta - sweep, tau - theta);
        return clamp(0.5 + inside * r);
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
