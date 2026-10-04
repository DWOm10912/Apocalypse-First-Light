package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
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
 * Colours blend in OKLab. Drawn per pixel into one texture per GUI scale and blitted once a frame.
 */
final class TemperatureDial {
    /** GUI pixels: face radius, gauge band, halo reach. */
    static final double RADIUS = 9.5, BAND = 1.5, HALO = 2.5;
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
    private TemperatureDial() {}

    /**
     * cold / heat / danger 0..1, trend −1 (cooling) .. +1 (warming) and target −1 .. +1 (the core's target on the same
     * scale as heat − cold), all eased by the caller; phase: the chevrons'
     * running position (cycles); seconds: breathing and pulse.
     */
    static void render(GuiGraphics graphics, double guiCx, double guiCy, double cold, double heat, double danger, double trend,
                       double target, double phase, double seconds) {
        int s = (int)Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        if (texture == null || textureScale != s) {
            size = (int)Math.ceil(2 * (RADIUS + HALO + 4) * s) + 2;
            image = new NativeImage(NativeImage.Format.RGBA, size, size, true);
            texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(TEXTURE, texture); // closes the previous one
            textureScale = s;
        }
        int x0 = (int)Math.round(guiCx * s - size / 2.0), y0 = (int)Math.round(guiCy * s - size / 2.0);
        fill(s, guiCx * s - x0, guiCy * s - y0, cold, heat, danger, trend, target, phase, seconds);
        texture.upload();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.pose().pushPose();
        graphics.pose().scale(1.0f / s, 1.0f / s, 1.0f);
        graphics.blit(TEXTURE, x0, y0, 0, 0, size, size, size, size);
        graphics.pose().popPose();
    }

    private static void fill(int s, double cx, double cy, double cold, double heat, double danger, double trend, double target,
                             double phase, double seconds) {
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
        double gx = cx - box / 2, gy = cy - box / 2 - 0.3 * s;
        // trend chevrons
        double trendWeight = smooth(0.05, 0.35, Math.abs(trend));
        boolean warming = trend > 0;
        double[] chevronColour = warming ? HEAT_RGB : ICE_RGB;
        double yA = 5.3 * s, yB = 12.2 * s, hw = 1.45 * s, hh = 0.8 * s, stroke = 0.4 * s;
        double[] p = new double[4];
        for (int py = 0; py < size; py++) for (int px = 0; px < size; px++) {
            double x = px + 0.5 - cx, y = py + 0.5 - cy, r = Math.hypot(x, y), ang = Math.atan2(x, -y); // 0 at the top, clockwise +
            p[0] = p[1] = p[2] = p[3] = 0;
            if (r > r0 - 0.5 && haloAlpha > 0.003) {
                double g = clamp(1 - (r - r0) / haloReach);
                over(p, haloColour, haloAlpha * Math.pow(g, 1.6));
            }
            if (rippleAlpha > 0.003) over(p, haloColour, rippleAlpha * clamp(0.5 - (Math.abs(r - rippleR) - 0.3 * s)));
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
            if (r < rIn - 0.5 * s) {
                double u = (px + 0.5 - gx) / box, v = (py + 0.5 - gy) / box;
                double d = Math.hypot(u - 0.5, v - 0.5) - 0.17 * (1 - grow);
                if (grow >= 0.02) {
                    double su = 0.5 + (u - 0.5) / glyphScale, sv = 0.5 + (v - 0.5) / glyphScale;
                    d = Math.min(d, (coldSide ? snowflake(su, sv) : flame(su, sv)) * glyphScale);
                }
                over(p, glyphColour, clamp(0.5 - d * box));
            }
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

    /** Six arms with a pair of side branches each (unit box, y down; signed distance). */
    private static double snowflake(double x, double y) {
        double d = Double.MAX_VALUE;
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 - Math.PI / 2, ux = Math.cos(a), uy = Math.sin(a);
            d = Math.min(d, AflRingIcon.segment(x, y, 0.5, 0.5, 0.5 + ux * 0.40, 0.5 + uy * 0.40) - 0.048);
            double bx = 0.5 + ux * 0.25, by = 0.5 + uy * 0.25;
            for (int side = -1; side <= 1; side += 2) {
                double b = a + side * Math.PI / 4;
                d = Math.min(d, AflRingIcon.segment(x, y, bx, by, bx + Math.cos(b) * 0.13, by + Math.sin(b) * 0.13) - 0.042);
            }
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
