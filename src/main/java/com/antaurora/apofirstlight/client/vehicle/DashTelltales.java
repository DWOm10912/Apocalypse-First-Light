package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.client.AflGauge;
import com.antaurora.apofirstlight.client.AflRingIcon;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Set;

/**
 * The dashboard's telltales (design doc 8.12): the strip at the top of the screen, in the ISO 2575 colours (green / blue:
 * on, amber: take care, red: warning; muted to the overlay style). Each vehicle has its own set (only what it is built
 * with: 4H only with a part-time 4WD transfer case); the strip keeps every place, unlit lamps ghosted, the turn signal
 * arrows at its ends. Icons are signed distance glyphs in a unit box (AflRingIcon), each its own cached AflGauge sprite.
 */
final class DashTelltales {
    static final int GREEN = 0x7CC47A, BLUE = 0x5C9BE8, AMBER = AflUiStyle.ACCENT, RED = 0xCC4A42, COLD = 0x6FA3E0;
    /** Unlit lamps: the outline colour, this faint. */
    private static final float GHOST = 0.16F;
    /** The strip's size against its first make (user 2026-10-17: a little too big). */
    private static final double K = 0.86;
    /** Strip: centre height, icon spacing, extra gap between groups, icon size (GUI px). */
    private static final double Y = 12 * K, SPACING = 12.5 * K, GROUP_GAP = 5 * K, SIZE = 8.2 * K, ARROW = 9.5 * K;

    /** Stroke half width (unit box). */
    private static final double W = 0.045;

    /** The lamps, in strip order; group: the strip's sections (beams | engine | body | drive and fuel). */
    enum Telltale {
        LOW(0, (x, y) -> lamp(x - 0.5, y - 0.5, true)),
        HIGH(0, (x, y) -> lamp(x - 0.5, y - 0.5, false)),
        TEMP(1, (x, y) -> coolant(x - 0.5, y - 0.5)),
        ENGINE(1, (x, y) -> engine(x - 0.5, y - 0.5)),
        GLOW(1, (x, y) -> glowPlug(x - 0.5, y - 0.5)),
        OIL(1, (x, y) -> oil(x - 0.5, y - 0.5)),
        BATTERY(1, (x, y) -> battery(x - 0.5, y - 0.5)),
        BRAKE(2, (x, y) -> brake(x - 0.5, y - 0.5)),
        DOOR(2, (x, y) -> door(x - 0.5, y - 0.5)),
        FOUR(3, (x, y) -> fourHigh(x - 0.5, y - 0.5)),
        FOUR_LOW(3, (x, y) -> fourLow(x - 0.5, y - 0.5)),
        FUEL(3, (x, y) -> pump(x - 0.5, y - 0.5));

        final int group;
        final AflRingIcon.Glyph glyph;

        Telltale(int group, AflRingIcon.Glyph glyph) {
            this.group = group;
            this.glyph = glyph;
        }
    }

    static final AflRingIcon.Glyph TURN_LEFT = (x, y) -> arrow(x - 0.5, y - 0.5);
    static final AflRingIcon.Glyph TURN_RIGHT = (x, y) -> arrow(0.5 - x, y - 0.5);
    /** The shift-up hint: a small filled triangle pointing up. */
    static final AflRingIcon.Glyph SHIFT_UP = (x, y) -> AflRingIcon.convex(x, y, 0.5, 0.12, 0.9, 0.82, 0.1, 0.82);

    private DashTelltales() {
    }

    /**
     * The strip: has = the vehicle's lamps, colour[ordinal] = a lit lamp's colour (-1 unlit); the turn arrows lit this
     * frame. pill: a rounded panel with group separators (dashboard RING); else a band fading out at both ends (BAND).
     */
    static void strip(GuiGraphics g, int width, Set<Telltale> has, int[] colour, boolean signals, boolean left, boolean right, boolean pill) {
        double cx = width / 2.0, total = 0;
        Telltale last = null;
        for (Telltale t : Telltale.values()) {
            if (!has.contains(t)) continue;
            if (last != null) total += SPACING + (t.group != last.group ? GROUP_GAP : 0);
            last = t;
        }
        double x0 = cx - total / 2;
        if (pill) {
            AflUiShapes.box(g, (float) (x0 - 9 * K), (float) (Y - 7.5 * K), (float) (total + 18 * K), (float) (15 * K), (float) (7.5 * K), AflUiStyle.BACK, 0.58F,
                    AflUiStyle.LINE, AflUiStyle.LINE_ALPHA, AflUiStyle.SHADOW_ALPHA, 0);
        } else {
            DashDraw.hGradient(g, x0 - 40 * K, Y - 8 * K, x0 + total + 40 * K, Y + 8 * K, AflUiStyle.BACK, new double[]{0, 0.18, 0.82, 1},
                    new float[]{0, 0.6F, 0.6F, 0});
            DashDraw.hGradient(g, x0 - 40 * K, Y + 8 * K, x0 + total + 40 * K, Y + 8 * K + 1.0 / DashDraw.scale(), AflUiStyle.LINE, new double[]{0, 0.5, 1},
                    new float[]{0, AflUiStyle.LINE_ALPHA, 0});
        }
        double x = x0;
        last = null;
        for (Telltale t : Telltale.values()) {
            if (!has.contains(t)) continue;
            if (last != null) {
                boolean split = t.group != last.group;
                x += SPACING + (split ? GROUP_GAP : 0);
                if (split && pill) AflUiShapes.fill(g, (float) (x - (SPACING + GROUP_GAP) / 2), (float) (Y - 4.5 * K), 1.0F / DashDraw.scale(), (float) (9 * K), 0,
                        AflUiStyle.LINE, GHOST);
            }
            last = t;
            int c = colour[t.ordinal()];
            icon(g, "tt_" + t.name().toLowerCase(java.util.Locale.ROOT), t.glyph, x, Y, SIZE, c < 0 ? AflUiStyle.LINE : c, c < 0 ? GHOST : 1);
        }
        if (signals) {
            double ax = total / 2 + (pill ? 21 : 34) * K;
            icon(g, "tt_turn_left", TURN_LEFT, cx - ax, Y, ARROW, left ? GREEN : AflUiStyle.LINE, left ? 1 : GHOST);
            icon(g, "tt_turn_right", TURN_RIGHT, cx + ax, Y, ARROW, right ? GREEN : AflUiStyle.LINE, right ? 1 : GHOST);
        }
    }

    /** A glyph centred at (x, y), size GUI px, as its own cached sprite (redrawn only when its colour or alpha changes). */
    static void icon(GuiGraphics g, String name, AflRingIcon.Glyph glyph, double x, double y, double size, int rgb, float alpha) {
        AflGauge.draw(g, "dash_" + name, x, y, size / 2 + 1, List.of(), new AflGauge.Glyph(glyph, size, rgb, alpha));
    }

    // ---- glyphs: u, v from the box centre (-0.5..0.5), y down; strokes W either side ----

    private static double seg(double u, double v, double ax, double ay, double bx, double by) {
        return AflRingIcon.segment(u, v, ax, ay, bx, by) - W;
    }

    /** A polyline through x0, y0, x1, y1, ... (closed: back to the start). */
    private static double poly(double u, double v, boolean closed, double... p) {
        double d = Double.MAX_VALUE;
        int n = p.length / 2;
        for (int i = 0; i < (closed ? n : n - 1); i++) {
            int j = (i + 1) % n;
            d = Math.min(d, AflRingIcon.segment(u, v, p[2 * i], p[2 * i + 1], p[2 * j], p[2 * j + 1]));
        }
        return d - W;
    }

    private static double circle(double u, double v, double cx, double cy, double r) {
        return Math.abs(Math.hypot(u - cx, v - cy) - r) - W;
    }

    private static double disc(double u, double v, double cx, double cy, double r) {
        return Math.hypot(u - cx, v - cy) - r;
    }

    /** An arc stroke from a0 to a1 degrees (0 = +x, clockwise on screen). */
    private static double arc(double u, double v, double cx, double cy, double r, double a0, double a1) {
        double a = Math.toDegrees(Math.atan2(v - cy, u - cx));
        double rel = ((a - a0) % 360 + 360) % 360;
        if (rel <= a1 - a0) return Math.abs(Math.hypot(u - cx, v - cy) - r) - W;
        double e0 = Math.toRadians(a0), e1 = Math.toRadians(a1);
        return Math.min(Math.hypot(u - cx - r * Math.cos(e0), v - cy - r * Math.sin(e0)),
                Math.hypot(u - cx - r * Math.cos(e1), v - cy - r * Math.sin(e1))) - W;
    }

    /** A filled box. */
    private static double box(double u, double v, double x0, double y0, double x1, double y1) {
        double qx = Math.abs(u - (x0 + x1) / 2) - (x1 - x0) / 2, qy = Math.abs(v - (y0 + y1) / 2) - (y1 - y0) / 2;
        return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0);
    }

    /** A rounded box's outline. */
    private static double roundBox(double u, double v, double x0, double y0, double x1, double y1, double r) {
        double qx = Math.abs(u - (x0 + x1) / 2) - (x1 - x0) / 2 + r, qy = Math.abs(v - (y0 + y1) / 2) - (y1 - y0) / 2 + r;
        double d = Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
        return Math.abs(d) - W;
    }

    /** Turn signal arrow pointing left (filled). */
    private static double arrow(double u, double v) {
        double head = AflRingIcon.convex(u, v, -0.04, -0.42, -0.04, 0.42, -0.5, 0);
        double shaft = AflRingIcon.convex(u, v, -0.05, -0.17, 0.48, -0.17, 0.48, 0.17, -0.05, 0.17);
        return Math.min(head, shaft);
    }

    /** Headlamp: a D lens facing left, four rays (dipped for the low beam, level for the high). */
    private static double lamp(double u, double v, boolean dip) {
        double lx = u + 0.02, d;
        if (lx >= 0) d = Math.abs(Math.hypot(lx / 0.44, v / 0.32) - 1) * 0.32 - W;
        else d = Math.min(Math.hypot(lx, v - 0.32), Math.hypot(lx, v + 0.32)) - W;
        d = Math.min(d, seg(u, v, -0.02, -0.32, -0.02, 0.32));
        for (double k : new double[]{-0.22, -0.075, 0.075, 0.22}) d = Math.min(d, seg(u, v, -0.14, k, -0.48, k + (dip ? 0.15 : 0)));
        return d;
    }

    /** Coolant temperature: a thermometer standing in two waves. */
    private static double coolant(double u, double v) {
        double d = Math.min(seg(u, v, 0, -0.44, 0, 0.04), disc(u, v, 0, 0.1, 0.1));
        for (double k : new double[]{-0.34, -0.21, -0.08}) d = Math.min(d, seg(u, v, 0.07, k, 0.2, k));
        for (double base : new double[]{0.27, 0.41}) {
            double[] p = new double[18];
            for (int i = 0; i <= 8; i++) {
                p[2 * i] = -0.44 + 0.88 * i / 8;
                p[2 * i + 1] = base + Math.sin(i / 8.0 * Math.PI * 3) * 0.035;
            }
            d = Math.min(d, poly(u, v, false, p));
        }
        return d;
    }

    /** Engine (the malfunction lamp's outline). */
    private static double engine(double u, double v) {
        return poly(u, v, true, -0.3, -0.12, -0.12, -0.12, -0.12, -0.24, -0.26, -0.24, -0.26, -0.32, 0.12, -0.32, 0.12, -0.24, -0.02, -0.24,
                -0.02, -0.12, 0.22, -0.12, 0.3, -0.04, 0.42, -0.04, 0.42, -0.2, 0.47, -0.2, 0.47, 0.24, 0.42, 0.24, 0.42, 0.1, 0.3, 0.1,
                0.18, 0.28, -0.24, 0.28, -0.3, 0.18, -0.4, 0.18, -0.4, 0.3, -0.46, 0.3, -0.46, -0.04, -0.4, -0.04, -0.4, 0.06, -0.3, 0.06);
    }

    /** Diesel glow plug: a coil of three loops on a lead, a bar over it. */
    private static double glowPlug(double u, double v) {
        double d = seg(u, v, -0.4, -0.3, 0.4, -0.3);
        double r = 0.11;
        for (int k = 0; k < 3; k++) d = Math.min(d, arc(u, v, -0.24 + 0.24 * k, 0.02, r, 135, 405));
        double c = Math.cos(Math.toRadians(45)) * r, s = Math.sin(Math.toRadians(45)) * r;
        d = Math.min(d, seg(u, v, -0.47, 0.2, -0.24 - c, 0.02 + s));
        for (int k = 0; k < 2; k++) d = Math.min(d, seg(u, v, -0.24 + 0.24 * k + c, 0.02 + s, 0.24 * k - c, 0.02 + s));
        return Math.min(d, seg(u, v, 0.24 + c, 0.02 + s, 0.47, 0.2));
    }

    /** Oil pressure: the oil can and its drop. */
    private static double oil(double u, double v) {
        double d = poly(u, v, true, -0.44, -0.06, -0.2, -0.06, -0.2, -0.16, 0.02, -0.16, 0.02, -0.06, 0.12, -0.06, 0.4, -0.2, 0.2, 0.22,
                -0.36, 0.22, -0.36, 0.04, -0.44, 0.0);
        return Math.min(d, disc(u, v, 0.42, 0.16, 0.055));
    }

    /** Charging: a battery with its terminals, minus and plus. */
    private static double battery(double u, double v) {
        double d = roundBox(u, v, -0.44, -0.2, 0.44, 0.28, 0.03);
        d = Math.min(d, Math.min(box(u, v, -0.32, -0.31, -0.16, -0.2), box(u, v, 0.16, -0.31, 0.32, -0.2)));
        d = Math.min(d, seg(u, v, -0.31, 0.04, -0.15, 0.04));
        return Math.min(d, Math.min(seg(u, v, 0.15, 0.04, 0.31, 0.04), seg(u, v, 0.23, -0.04, 0.23, 0.12)));
    }

    /** Parking brake: a P in a circle between two arcs. */
    private static double brake(double u, double v) {
        double d = Math.min(circle(u, v, 0, 0, 0.3), Math.min(arc(u, v, 0, 0, 0.44, 130, 230), arc(u, v, 0, 0, 0.44, -50, 50)));
        d = Math.min(d, seg(u, v, -0.08, -0.15, -0.08, 0.16));
        d = Math.min(d, Math.min(seg(u, v, -0.08, -0.15, 0.0, -0.15), seg(u, v, -0.08, 0.03, 0.0, 0.03)));
        return Math.min(d, arc(u, v, 0.0, -0.06, 0.09, -90, 90));
    }

    /** Door open: the car from above with a door swung out. */
    private static double door(double u, double v) {
        double d = roundBox(u, v, -0.16, -0.44, 0.16, 0.44, 0.1);
        return Math.min(d, Math.min(seg(u, v, -0.16, -0.08, -0.42, 0.14), seg(u, v, -0.16, -0.16, 0.16, -0.16)));
    }

    /** 4H: the letters in a rounded box. */
    private static double fourHigh(double u, double v) {
        double d = roundBox(u, v, -0.47, -0.3, 0.47, 0.3, 0.12);
        d = Math.min(d, Math.min(seg(u, v, -0.13, -0.16, -0.3, 0.06), seg(u, v, -0.3, 0.06, -0.04, 0.06)));
        d = Math.min(d, seg(u, v, -0.11, -0.16, -0.11, 0.16));
        d = Math.min(d, Math.min(seg(u, v, 0.07, -0.16, 0.07, 0.16), seg(u, v, 0.28, -0.16, 0.28, 0.16)));
        return Math.min(d, seg(u, v, 0.07, 0, 0.28, 0));
    }

    /** 4L (the low range): the letters in a rounded box. */
    private static double fourLow(double u, double v) {
        double d = roundBox(u, v, -0.47, -0.3, 0.47, 0.3, 0.12);
        d = Math.min(d, Math.min(seg(u, v, -0.13, -0.16, -0.3, 0.06), seg(u, v, -0.3, 0.06, -0.04, 0.06)));
        d = Math.min(d, seg(u, v, -0.11, -0.16, -0.11, 0.16));
        return Math.min(d, Math.min(seg(u, v, 0.09, -0.16, 0.09, 0.16), seg(u, v, 0.09, 0.16, 0.29, 0.16)));
    }

    /** Fuel: a pump with its window and the hose. */
    private static double pump(double u, double v) {
        double d = poly(u, v, true, -0.34, -0.4, 0.12, -0.4, 0.12, 0.42, -0.34, 0.42);
        d = Math.min(d, box(u, v, -0.25, -0.3, 0.03, -0.1));
        return Math.min(d, poly(u, v, false, 0.12, 0.0, 0.3, 0.0, 0.3, 0.28, 0.4, 0.28, 0.4, -0.22, 0.26, -0.36));
    }
}
