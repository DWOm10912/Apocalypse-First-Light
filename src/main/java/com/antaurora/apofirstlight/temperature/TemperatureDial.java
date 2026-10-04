package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.SurvivalHudLayout;
import com.antaurora.apofirstlight.client.AflRingIcon;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The temperature dial, the centre of the survival cluster (flat colours like the stamina / thirst rings, anti-aliased
 * real pixels):
 * - a dark face with a 270° gauge band (open at the bottom): its left half tinted cold, its right half warm, a notch at
 *   the top for normal; a marker shows the core (cold to the left, heat to the right) and the band fills from the top to it;
 * - a target frame: a thin, faint hollow frame on the band where the core is heading in this place and clothing (the
 *   core marker chases it); hidden while the two coincide; the core marker always stays the main thing;
 * - a glyph: a neutral dot that shrinks away while a snowflake (cold) or a flame (heat) grows in;
 * - a trend: three chevrons flowing through the bottom gap, down and ice blue while cooling, up and orange while warming,
 *   faster the stronger the trend;
 * - normal: the glyph breathes very slightly; off normal: a soft halo; extremes: a stronger, wider halo that pulses
 *   slowly, a thin ripple ring spreading out and fading once per pulse, and the glyph pulsing a little.
 * - air temperature (while a wrist thermometer is worn, TemperatureReadout): a thin arc over the top, -20 °C on the left to
 *   +40 °C on the right (10 °C straight up), tinted ice blue .. neutral .. orange, with a small light triangle outside it
 *   pointing in at the temperature; the number above it is drawn by ClientTemperatureHud;
 * - while a core reading shows (clinical thermometer), the glyph steps aside for the number (ClientTemperatureHud).
 * Colours blend in OKLab. Drawn per pixel into one texture per GUI scale and blitted once a frame.
 */
final class TemperatureDial {
    /** GUI pixels before SurvivalHudLayout.SCALE: face radius, gauge band, halo reach. */
    static final double RADIUS = 9.5, BAND = 1.5, HALO = 2.5;
    /** Survival HUD V1: the dark metal bezel round the face (GUI px before SurvivalHudLayout.SCALE), lit from above. */
    static final double BEZEL = 1.15;
    private static final double[] BEZEL_TOP = rgb(0x5A5E64), BEZEL_BOTTOM = rgb(0x2A2C2F), BEZEL_RIM = rgb(0x1A1B1D),
            OUTLINE = rgb(SurvivalHudLayout.OUTLINE);
    /** Air temperature arc (GUI pixels / degrees): radius, band, half span, the marker's tip and base radii. */
    static final double AMBIENT_RADIUS = 12.5, AMBIENT_BAND = 1.0, AMBIENT_TIP = 13.4, AMBIENT_BASE = 15.8;
    private static final double AMBIENT_SPAN = Math.toRadians(60);
    private static final double[] AMBIENT_MARK = rgb(0xF0EEE6);
    private static final double SPAN = Math.PI * 0.75, TAU = Math.PI * 2;
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/temperature_dial");
    private static final int NEUTRAL = 0xC8C6BE, ICE = 0x8ECAF0, HEAT = 0xE8843F;
    private static final double[] LAB_NEUTRAL = oklab(NEUTRAL), LAB_ICE = oklab(ICE), LAB_HEAT = oklab(HEAT);
    private static final double[] FACE = {0x10 / 255.0, 0x10 / 255.0, 0x10 / 255.0};
    private static final double[] COLD_SCALE = mix(oklab(0x5A6670), LAB_ICE, 0.35), HEAT_SCALE = mix(oklab(0x6A5E56), LAB_HEAT, 0.35);
    private static final double[] ICE_RGB = rgb(ICE), HEAT_RGB = rgb(HEAT);
    private static DynamicTexture texture;
    private static NativeImage image;
    private static int textureScale = -1, size;
    /** Redrawn at most this often (the dial's motion is slow: breathing, flowing chevrons, eased values). */
    private static final long REDRAW_NANOS = 1_000_000_000L / 30;
    private static long lastRedraw;
    private static boolean dirty = true;
    // per-pixel geometry, fixed for a texture size and centre: offsets, radius, angle (0 at the top, clockwise), bezel light
    private static float[] gx, gy, gr, gang, glit;
    private static double geoCx = Double.NaN, geoCy = Double.NaN;
    private static int geoSize = -1;
    // glyph coverage (dot / snowflake / flame), fixed while its shape holds
    private static float[] glyphCover;
    private static long glyphKey = Long.MIN_VALUE;
    private TemperatureDial() {}

    /**
     * cold / heat / danger 0..1, trend −1 (cooling) .. +1 (warming) and target −1 .. +1 (the core's target on the same
     * scale as heat − cold), all eased by the caller; ambient: the air temperature in °C for the arc, NaN for none;
     * hideGlyph: the dial's centre is left free for the core reading; phase: the chevrons'
     * running position (cycles); seconds: breathing and pulse.
     */
    static void render(GuiGraphics graphics, double guiCx, double guiCy, double cold, double heat, double danger, double trend,
                       double target, double ambient, boolean hideGlyph, double phase, double seconds) {
        int s = (int)Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        double k = s * SurvivalHudLayout.SCALE;   // the cluster's size factor: geometry in real pixels
        if (texture == null || textureScale != s) {
            size = (int)Math.ceil(2 * (AMBIENT_BASE + 1.5) * k) + 2;
            image = new NativeImage(NativeImage.Format.RGBA, size, size, true);
            texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(TEXTURE, texture); // closes the previous one
            textureScale = s;
            dirty = true;
        }
        int x0 = (int)Math.round(guiCx * s - size / 2.0), y0 = (int)Math.round(guiCy * s - size / 2.0);
        long now = System.nanoTime();
        if (dirty || now - lastRedraw >= REDRAW_NANOS) {   // capped: the picture changes every frame while anything moves
            fill(k, guiCx * s - x0, guiCy * s - y0, cold, heat, danger, trend, target, ambient, hideGlyph, phase, seconds);
            texture.upload();
            lastRedraw = now;
            dirty = false;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.pose().pushPose();
        graphics.pose().scale(1.0f / s, 1.0f / s, 1.0f);
        graphics.blit(TEXTURE, x0, y0, 0, 0, size, size, size, size);
        graphics.pose().popPose();
    }

    private static void fill(double s, double cx, double cy, double cold, double heat, double danger, double trend, double target,
                             double ambient, boolean hideGlyph, double phase, double seconds) {
        double r0 = RADIUS * s, band = BAND * s, rIn = r0 - band, halo = HALO * s;
        double dev = Math.max(cold, heat), bias = heat - cold;
        boolean coldSide = cold > heat;
        double[] tone = coldSide ? LAB_ICE : LAB_HEAT;
        double pulse = 0.5 + 0.5 * Math.sin(seconds * TAU * 0.75), breath = 1 + 0.05 * Math.sin(seconds * TAU / 5);
        double[] glyphColour = mix(LAB_NEUTRAL, tone, Math.min(1, dev * 1.4));
        double glow = breath * (1 + 0.06 * danger * (pulse * 2 - 1));
        for (int k = 0; k < 3; k++) glyphColour[k] = clamp(glyphColour[k] * glow);
        double[] fillColour = mix(LAB_NEUTRAL, tone, 0.8 + 0.2 * dev), haloColour = mix(LAB_NEUTRAL, tone, 0.8);
        double[] markerColour = mix(LAB_NEUTRAL, tone, dev);
        for (int k = 0; k < 3; k++) markerColour[k] = clamp(markerColour[k] * 1.12);
        double haloAlpha = 0.30 * smooth(0.1, 0.8, dev) * (1 - 0.3 * danger) + danger * (0.30 + 0.18 * pulse);
        double haloReach = halo * (1 + 0.6 * danger);
        // extremes: a thin ring spreading out from the face and fading, once per pulse
        double ripple = ((seconds * 0.75) % 1 + 1) % 1, rippleR = r0 + 0.4 * s + ripple * 4.2 * s;
        double rippleAlpha = 0.42 * danger * Math.pow(1 - ripple, 1.6);
        // target frame: faint, thin, hollow; gone while it sits on the core marker
        double targetBias = Math.max(-1, Math.min(1, target)), targetAngle = targetBias * SPAN;
        double tx = Math.sin(targetAngle), ty = -Math.cos(targetAngle);
        double targetAlpha = 0.45 * smooth(0.03, 0.10, Math.abs(targetBias - bias));
        double[] targetColour = mix(LAB_NEUTRAL, targetBias < 0 ? LAB_ICE : LAB_HEAT, Math.min(1, Math.abs(targetBias) * 1.3));
        for (int k = 0; k < 3; k++) targetColour[k] = clamp(targetColour[k] * 1.1);
        double frameLength = band / 2 + 1.1 * s, frameWidth = 0.95 * s, frameStroke = Math.max(0.2 * s, 0.55);
        double marker = Math.max(-1, Math.min(1, bias)) * SPAN, mx = Math.sin(marker), my = -Math.cos(marker);
        double lo = Math.min(0, marker), hi = Math.max(0, marker);
        // glyph: the dot shrinks away while the snowflake / flame grows in
        double grow = smooth(0.08, 0.45, dev), glyphScale = 0.6 + 0.4 * grow, box = rIn * 2 * 0.62;
        double boxX = cx - box / 2, boxY = cy - box / 2 - 0.3 * s;
        // trend chevrons
        double trendWeight = smooth(0.05, 0.35, Math.abs(trend));
        boolean warming = trend > 0;
        double[] chevronColour = warming ? HEAT_RGB : ICE_RGB;
        double yA = 5.3 * s, yB = 12.2 * s, hw = 1.45 * s, hh = 0.8 * s, stroke = 0.4 * s;
        // air temperature arc and its marker
        boolean showAmbient = !Double.isNaN(ambient);
        double ar = AMBIENT_RADIUS * s, aband = AMBIENT_BAND * s, aTip = AMBIENT_TIP * s, aBase = AMBIENT_BASE * s;
        double aAngle = showAmbient ? ambientAngle(ambient) : 0, ax = Math.sin(aAngle), ay = -Math.cos(aAngle);
        double bezelIn = r0 + 0.15 * s, bezelOut = r0 + BEZEL * s, rim = 0.22 * s, outline = SurvivalHudLayout.OUTLINE_WIDTH * s;
        geometry(cx, cy);
        if (!hideGlyph) glyph(s, rIn, grow, glyphScale, coldSide, box, boxX, boxY);
        double[] p = new double[4];
        for (int py = 0, pi = 0; py < size; py++) for (int px = 0; px < size; px++, pi++) {
            double x = gx[pi], y = gy[pi], r = gr[pi], ang = gang[pi]; // ang: 0 at the top, clockwise +
            p[0] = p[1] = p[2] = p[3] = 0;
            if (r > r0 - 0.5 && haloAlpha > 0.003) {
                double g = clamp(1 - (r - r0) / haloReach);
                if (g > 0) over(p, haloColour, haloAlpha * Math.pow(g, 1.6));
            }
            if (rippleAlpha > 0.003) over(p, haloColour, rippleAlpha * clamp(0.5 - (Math.abs(r - rippleR) - 0.3 * s)));
            if (r > bezelIn - 1 && r < bezelOut + outline + 1) {
                double bez = clamp(0.5 - (Math.abs(r - (bezelIn + bezelOut) / 2) - (bezelOut - bezelIn) / 2));
                if (bez > 0) {
                    double lit = glit[pi];
                    double[] metal = {BEZEL_BOTTOM[0] + (BEZEL_TOP[0] - BEZEL_BOTTOM[0]) * lit, BEZEL_BOTTOM[1] + (BEZEL_TOP[1] - BEZEL_BOTTOM[1]) * lit,
                            BEZEL_BOTTOM[2] + (BEZEL_TOP[2] - BEZEL_BOTTOM[2]) * lit};
                    over(p, metal, bez);
                    over(p, BEZEL_RIM, clamp(0.5 - (Math.abs(r - (bezelOut - rim / 2)) - rim / 2)));
                }
                over(p, OUTLINE, SurvivalHudLayout.OUTLINE_ALPHA * clamp(0.5 - (Math.abs(r - (bezelOut + outline / 2)) - outline / 2)));
            }
            if (showAmbient && r > ar - aband - 1 && r < aBase + 2) {
                double onArc = clamp(0.5 - (Math.abs(r - ar) - aband / 2)) * clamp(0.5 + (AMBIENT_SPAN - Math.abs(ang)) * r);
                if (onArc > 0) over(p, ambientColour(10 + ang / AMBIENT_SPAN * 30), 0.6 * onArc);
                double along = x * ax + y * ay, across = x * ay - y * ax;
                if (along > aTip - 1 && along < aBase + 1) {
                    double half = Math.max(0, (along - aTip) / (aBase - aTip)) * 1.5 * s;
                    over(p, AMBIENT_MARK, clamp(0.5 - (Math.abs(across) - half)) * clamp(0.5 - (aTip - along)) * clamp(0.5 - (along - aBase)));
                }
            }
            double face = clamp(0.5 - (r - r0));
            if (face > 0) over(p, FACE, 0.56 * face);
            double onBand = clamp(0.5 - (Math.abs(r - (r0 + rIn) / 2) - band / 2 + 0.35 * s));
            double inSpan = clamp(0.5 + (SPAN - Math.abs(ang)) * r);
            if (onBand > 0 && inSpan > 0) {
                double a = onBand * inSpan, notch = clamp(Math.abs(x) - 0.35 * s + 0.5);
                over(p, ang < 0 ? COLD_SCALE : HEAT_SCALE, a * 0.85 * notch);
                if (Math.abs(marker) > 0.01) over(p, fillColour, a * clamp(0.5 + Math.min(ang - lo, hi - ang) * r));
            }
            if (targetAlpha > 0.003) {
                double ta = x * tx + y * ty, tc = Math.abs(x * ty - y * tx);
                double frame = Math.max(Math.abs(ta - (r0 + rIn) / 2) - frameLength, tc - frameWidth);
                over(p, targetColour, targetAlpha * clamp(0.5 - (Math.abs(frame) - frameStroke)));
            }
            double along = x * mx + y * my, across = Math.abs(x * my - y * mx);
            double m = clamp(0.5 - (across - 0.55 * s)) * clamp(0.5 - Math.abs(along - (r0 + rIn) / 2) + band / 2 + 0.45 * s);
            if (m > 0) over(p, markerColour, m);
            if (!hideGlyph && glyphCover[pi] > 0) over(p, glyphColour, glyphCover[pi]);
            if (trendWeight > 0.002 && Math.abs(x) < 2.4 * s && y > 4.6 * s) {
                double cover = 0;
                for (int i = 0; i < 3; i++) {
                    double t = ((phase + i / 3.0) % 1 + 1) % 1, cyI = warming ? yB - t * (yB - yA) : yA + t * (yB - yA);
                    double fade = Math.pow(Math.sin(Math.PI * t), 1.3), uy = (y - cyI) * (warming ? -1 : 1);
                    double d = Math.min(AflRingIcon.segment(x, uy, -hw, -hh / 2, 0, hh / 2), AflRingIcon.segment(x, uy, 0, hh / 2, hw, -hh / 2)) - stroke;
                    cover = Math.max(cover, clamp(0.5 - d) * fade);
                }
                over(p, chevronColour, cover * trendWeight);
            }
            int a = (int)Math.round(clamp(p[3]) * 255);
            if (a == 0) { image.setPixelRGBA(px, py, 0); continue; }
            int rr = (int)Math.round(clamp(p[0] / p[3]) * 255), gg = (int)Math.round(clamp(p[1] / p[3]) * 255),
                    bb = (int)Math.round(clamp(p[2] / p[3]) * 255);
            image.setPixelRGBA(px, py, a << 24 | bb << 16 | gg << 8 | rr); // NativeImage packs ABGR
        }
    }

    /** -20 °C .. +40 °C over the arc's span, 10 °C at the top (clockwise positive). */
    static double ambientAngle(double celsius) { return Math.max(-1, Math.min(1, (celsius - 10) / 30)) * AMBIENT_SPAN; }
    private static double[] ambientColour(double celsius) {
        return celsius < 10 ? mix(LAB_ICE, LAB_NEUTRAL, clamp((celsius + 20) / 30)) : mix(LAB_NEUTRAL, LAB_HEAT, clamp((celsius - 10) / 30));
    }

    /** Six arms with a pair of side branches each (unit box, y down; signed distance). */
    /** Per-pixel offsets, radius, angle and bezel light for this texture size and centre (recomputed only when they change). */
    private static void geometry(double cx, double cy) {
        if (geoSize == size && cx == geoCx && cy == geoCy) return;
        int n = size * size;
        gx = new float[n]; gy = new float[n]; gr = new float[n]; gang = new float[n]; glit = new float[n];
        for (int py = 0, i = 0; py < size; py++) for (int px = 0; px < size; px++, i++) {
            double x = px + 0.5 - cx, y = py + 0.5 - cy, r = Math.hypot(x, y);
            gx[i] = (float)x; gy[i] = (float)y; gr[i] = (float)r; gang[i] = (float)Math.atan2(x, -y);
            glit[i] = (float)Math.pow(clamp(0.5 - 0.5 * y / Math.max(r, 1e-3)), 1.3);
        }
        geoSize = size; geoCx = cx; geoCy = cy;
        glyphKey = Long.MIN_VALUE;
    }

    /** Coverage of the centre glyph (the dot shrinking while the snowflake / flame grows in), recomputed only when its
     *  shape changes (growth in 1/64 steps, cold or hot side, size). */
    private static void glyph(double s, double rIn, double grow, double glyphScale, boolean coldSide, double box, double boxX, double boxY) {
        long key = Math.round(grow * 64) * 4 + (coldSide ? 1 : 0) + 2L * Math.round(s * 100) * 1000;
        if (key == glyphKey && glyphCover != null) return;
        double g = Math.round(grow * 64) / 64.0, scale = 0.6 + 0.4 * g;
        glyphCover = new float[size * size];
        for (int py = 0, i = 0; py < size; py++) for (int px = 0; px < size; px++, i++) {
            if (gr[i] >= rIn - 0.5 * s) continue;
            double u = (px + 0.5 - boxX) / box, v = (py + 0.5 - boxY) / box;
            double d = Math.hypot(u - 0.5, v - 0.5) - 0.17 * (1 - g);
            if (g >= 0.02) {
                double su = 0.5 + (u - 0.5) / scale, sv = 0.5 + (v - 0.5) / scale;
                d = Math.min(d, (coldSide ? snowflake(su, sv) : flame(su, sv)) * scale);
            }
            glyphCover[i] = (float)clamp(0.5 - d * box);
        }
        glyphKey = key;
    }

    /** Snowflake arms: 6 directions (from straight up), each with a pair of side branches at 45 degrees. */
    private static final double[] ARM_X = new double[6], ARM_Y = new double[6], BRANCH_X = new double[12], BRANCH_Y = new double[12];
    static {
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 - Math.PI / 2;
            ARM_X[k] = Math.cos(a); ARM_Y[k] = Math.sin(a);
            for (int side = 0; side < 2; side++) {
                double b = a + (side * 2 - 1) * Math.PI / 4;
                BRANCH_X[k * 2 + side] = Math.cos(b); BRANCH_Y[k * 2 + side] = Math.sin(b);
            }
        }
    }
    private static double snowflake(double x, double y) {
        double d = Double.MAX_VALUE;
        for (int k = 0; k < 6; k++) {
            double ux = ARM_X[k], uy = ARM_Y[k];
            d = Math.min(d, AflRingIcon.segment(x, y, 0.5, 0.5, 0.5 + ux * 0.40, 0.5 + uy * 0.40) - 0.048);
            double bx = 0.5 + ux * 0.25, by = 0.5 + uy * 0.25;
            for (int side = 0; side < 2; side++)
                d = Math.min(d, AflRingIcon.segment(x, y, bx, by, bx + BRANCH_X[k * 2 + side] * 0.13, by + BRANCH_Y[k * 2 + side] * 0.13) - 0.042);
        }
        return d;
    }
    /** A flame: a body with its tip leaning right, a smaller tongue on the left, a hollow inner flame. */
    private static double flame(double x, double y) {
        double outer = Math.min(Math.min(Math.hypot(x - 0.5, y - 0.66) - 0.24, AflRingIcon.convex(x, y, 0.58, 0.08, 0.735, 0.60, 0.265, 0.60)),
                Math.min(Math.hypot(x - 0.36, y - 0.62) - 0.13, AflRingIcon.convex(x, y, 0.30, 0.30, 0.47, 0.62, 0.24, 0.62)));
        double inner = Math.min(Math.hypot(x - 0.5, y - 0.74) - 0.095, AflRingIcon.convex(x, y, 0.52, 0.47, 0.595, 0.73, 0.405, 0.73));
        return Math.max(outer, -inner);
    }

    /** Composite a colour with coverage a over the pixel (premultiplied, over transparent). */
    private static void over(double[] p, double[] c, double a) {
        if (a <= 0) return;
        a = Math.min(1, a);
        p[0] = p[0] * (1 - a) + c[0] * a; p[1] = p[1] * (1 - a) + c[1] * a; p[2] = p[2] * (1 - a) + c[2] * a;
        p[3] = p[3] + (1 - p[3]) * a;
    }
    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    private static double smooth(double a, double b, double x) { double t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); }
    private static double[] rgb(int hex) { return new double[]{((hex >> 16) & 255) / 255.0, ((hex >> 8) & 255) / 255.0, (hex & 255) / 255.0}; }
    private static double[] mix(double[] a, double[] b, double t) {
        return fromOklab(new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t});
    }

    // OKLab (Björn Ottosson) for even colour blends
    private static double toLinear(double c) { return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
    private static double toSrgb(double c) { return c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055; }
    private static double[] oklab(int hex) {
        double r = toLinear(((hex >> 16) & 255) / 255.0), g = toLinear(((hex >> 8) & 255) / 255.0), b = toLinear((hex & 255) / 255.0);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[]{0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s, 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
    }
    private static double[] fromOklab(double[] lab) {
        double l = Math.pow(lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2], 3);
        double m = Math.pow(lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2], 3);
        double s = Math.pow(lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2], 3);
        return new double[]{clamp(toSrgb(clamp(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s))),
                clamp(toSrgb(clamp(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s))),
                clamp(toSrgb(clamp(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)))};
    }
}
