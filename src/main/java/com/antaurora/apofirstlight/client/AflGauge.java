package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Survival HUD gauge: concentric ring bands (full circles or arcs such as the crescents, continuous or split into
 * segments), discs and glyphs, drawn per real pixel with anti-aliased edges (distance fields) into one dynamic texture
 * per gauge and blitted once a frame. The texture is only redrawn when what it shows changes (an animation step, a
 * colour), so a steady gauge costs one blit. Sizes and positions in GUI pixels; angles in degrees clockwise from the top.
 */
public final class AflGauge {
    /** The empty part of a band: dark, translucent (the HUD's ring backing). */
    public static final int BACK = 0x101010;
    public static final double BACK_ALPHA = 0x90 / 255.0;

    /**
     * One band: outer radius and width (GUI px; width = outer makes a disc), present from `start` over `span` degrees
     * (360: a full circle); segments (0 = continuous) separated by gaps of gapGui GUI pixels along the band; the part from
     * `from` to `to` (fractions of the span, from its start) in rgb; the rest of the band in backRgb at backAlpha (0:
     * none); alpha fades the whole band.
     */
    public record Band(double outer, double width, double start, double span, int segments, double gapGui,
                       double from, double to, int rgb, double alpha, int backRgb, double backAlpha) {
        /** A full ring gauge filled clockwise from the top. */
        public static Band gauge(double outer, double width, int segments, double gapGui, double fill, int rgb, double alpha) {
            return new Band(outer, width, 0, 360, segments, gapGui, 0, fill, rgb, alpha, BACK, BACK_ALPHA);
        }
        /** Only the part from..to of a full ring, no backing (a ghost of health just lost, absorption, tinted segments). */
        public static Band overlay(double outer, double width, int segments, double gapGui, double from, double to, int rgb, double alpha) {
            return new Band(outer, width, 0, 360, segments, gapGui, from, to, rgb, alpha, BACK, 0);
        }
        /** An arc gauge from `start` over `span`, filled over from..to of it, with the dark backing. */
        public static Band arc(double outer, double width, double start, double span, int segments, double gapGui,
                               double from, double to, int rgb, double alpha) {
            return new Band(outer, width, start, span, segments, gapGui, from, to, rgb, alpha, BACK, BACK_ALPHA);
        }
        /** A plain line along a circle or an arc (outlines). */
        public static Band line(double outer, double width, double start, double span, int rgb, double alpha) {
            return new Band(outer, width, start, span, 0, 0, 0, 1, rgb, alpha, BACK, 0);
        }
        /** A filled disc (a face behind a ring, a backing behind an icon). */
        public static Band disc(double radius, int rgb, double alpha) {
            return new Band(radius, radius, 0, 360, 0, 0, 0, 1, rgb, alpha, BACK, 0);
        }
    }

    /** A glyph (signed distance in a unit box, y down) in a square of `size` GUI px centred offsetY GUI px below the centre. */
    public record Glyph(AflRingIcon.Glyph shape, double size, int rgb, double alpha, double offsetY) {
        public Glyph(AflRingIcon.Glyph shape, double size, int rgb, double alpha) { this(shape, size, rgb, alpha, 0); }
    }

    private static final class Sprite {
        final ResourceLocation id;
        DynamicTexture texture;
        NativeImage image;
        int size = -1;
        long key;
        boolean drawn;
        Sprite(ResourceLocation id) { this.id = id; }
    }
    private static final Map<String, Sprite> SPRITES = new HashMap<>();

    private AflGauge() {}

    /** reach: how far the gauge extends from its centre plus room for anti-aliasing (GUI px); name: one texture per gauge. */
    public static void draw(GuiGraphics graphics, String name, double guiCx, double guiCy, double reach, List<Band> bands,
                            @Nullable Glyph glyph) {
        int s = (int)Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        int size = (int)Math.ceil(2 * reach * s) + 2;
        Sprite sprite = SPRITES.computeIfAbsent(name, n -> new Sprite(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/gauge_" + n)));
        if (sprite.texture == null || sprite.size != size) {
            sprite.image = new NativeImage(NativeImage.Format.RGBA, size, size, true);
            sprite.texture = new DynamicTexture(sprite.image);
            Minecraft.getInstance().getTextureManager().register(sprite.id, sprite.texture); // closes the previous one
            sprite.size = size;
            sprite.drawn = false;
        }
        int x0 = (int)Math.round(guiCx * s - size / 2.0), y0 = (int)Math.round(guiCy * s - size / 2.0);
        double cx = guiCx * s - x0, cy = guiCy * s - y0;
        long key = key(s, cx, cy, bands, glyph);
        if (!sprite.drawn || key != sprite.key) {
            raster(sprite.image, size, s, cx, cy, bands, glyph);
            sprite.texture.upload();
            sprite.key = key;
            sprite.drawn = true;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.pose().pushPose();
        graphics.pose().scale(1.0f / s, 1.0f / s, 1.0f);
        graphics.blit(sprite.id, x0, y0, 0, 0, size, size, size, size);
        graphics.pose().popPose();
    }

    /** Quantised picture state: fills to 1/2000 of the span, alphas to 1/255; anything else changing redraws. */
    private static long key(int s, double cx, double cy, List<Band> bands, @Nullable Glyph glyph) {
        long h = 17;
        h = h * 31 + s;
        h = h * 31 + Math.round(cx * 64);
        h = h * 31 + Math.round(cy * 64);
        for (Band b : bands) {
            h = h * 31 + Double.hashCode(b.outer());
            h = h * 31 + Double.hashCode(b.width());
            h = h * 31 + Double.hashCode(b.start());
            h = h * 31 + Double.hashCode(b.span());
            h = h * 31 + b.segments();
            h = h * 31 + Double.hashCode(b.gapGui());
            h = h * 31 + q(b.from(), 2000);
            h = h * 31 + q(b.to(), 2000);
            h = h * 31 + b.rgb();
            h = h * 31 + q(b.alpha(), 255);
            h = h * 31 + b.backRgb();
            h = h * 31 + q(b.backAlpha(), 255);
        }
        if (glyph != null) {
            h = h * 31 + System.identityHashCode(glyph.shape());
            h = h * 31 + Double.hashCode(glyph.size());
            h = h * 31 + Double.hashCode(glyph.offsetY());
            h = h * 31 + glyph.rgb();
            h = h * 31 + q(glyph.alpha(), 255);
        }
        return h;
    }
    private static long q(double v, int steps) { return Math.round(v * steps); }

    private static void raster(NativeImage image, int size, int s, double cx, double cy, List<Band> bands, @Nullable Glyph glyph) {
        double[] p = new double[4];
        double box = glyph == null ? 0 : glyph.size() * s, gx = cx - box / 2, gy = cy + (glyph == null ? 0 : glyph.offsetY() * s) - box / 2;
        for (int py = 0; py < size; py++) for (int px = 0; px < size; px++) {
            double x = px + 0.5 - cx, y = py + 0.5 - cy, r = Math.hypot(x, y);
            double deg = Math.toDegrees(Math.atan2(x, -y));
            if (deg < 0) deg += 360;
            p[0] = p[1] = p[2] = p[3] = 0;
            for (Band b : bands) {
                if (b.alpha() <= 0) continue;
                double ro = b.outer() * s, w = b.width() * s;
                double radial = clamp(0.5 - (Math.abs(r - (ro - w / 2)) - w / 2));
                if (radial <= 0) continue;
                double perDeg = Math.max(r, 1e-3) * Math.PI / 180, span = b.span();
                boolean full = span >= 360 - 1e-6;
                double rel = ((deg - b.start()) % 360 + 360) % 360;
                double cover = radial * b.alpha();
                if (!full) cover *= clamp(0.5 + (rel <= span ? Math.min(rel, span - rel) : -Math.min(rel - span, 360 - rel)) * perDeg);
                if (b.segments() > 0 && cover > 0) {
                    double seg = span / b.segments(), local = Math.min(rel, span) % seg, gap = Math.toDegrees(b.gapGui() * s / Math.max(r, 1e-3));
                    cover *= clamp(0.5 + Math.min(local - gap / 2, seg - gap / 2 - local) * perDeg);
                }
                if (cover <= 0) continue;
                over(p, b.backRgb(), cover * b.backAlpha());
                double fill = full ? between(rel, b.from() * 360, b.to() * 360, perDeg)
                        : b.to() <= b.from() ? 0 : clamp(0.5 + Math.min(rel - b.from() * span, b.to() * span - rel) * perDeg);
                over(p, b.rgb(), cover * fill);
            }
            if (glyph != null && glyph.alpha() > 0 && box > 0) {
                double d = glyph.shape().distance((px + 0.5 - gx) / box, (py + 0.5 - gy) / box) * box;
                over(p, glyph.rgb(), clamp(0.5 - d) * glyph.alpha());
            }
            int a = (int)Math.round(clamp(p[3]) * 255);
            if (a == 0) { image.setPixelRGBA(px, py, 0); continue; }
            int rr = (int)Math.round(clamp(p[0] / p[3]) * 255), gg = (int)Math.round(clamp(p[1] / p[3]) * 255),
                    bb = (int)Math.round(clamp(p[2] / p[3]) * 255);
            image.setPixelRGBA(px, py, a << 24 | bb << 16 | gg << 8 | rr); // NativeImage packs ABGR
        }
    }

    /** Coverage of a pixel at `deg` by the clockwise interval from..to on a full circle (degrees), anti-aliased at both ends. */
    private static double between(double deg, double from, double to, double perDeg) {
        if (to - from >= 360 - 1e-6) return 1;
        if (to <= from) return 0;
        double inside = deg >= from && deg <= to ? Math.min(deg - from, to - deg)
                : -Math.min(circular(deg, from), circular(deg, to));
        return clamp(0.5 + inside * perDeg);
    }
    private static double circular(double a, double b) { double d = Math.abs(a - b) % 360; return Math.min(d, 360 - d); }

    /** Composite a colour with coverage a over the pixel (premultiplied). */
    private static void over(double[] p, int rgb, double a) {
        if (a <= 0) return;
        a = Math.min(1, a);
        p[0] = p[0] * (1 - a) + ((rgb >> 16) & 255) / 255.0 * a;
        p[1] = p[1] * (1 - a) + ((rgb >> 8) & 255) / 255.0 * a;
        p[2] = p[2] * (1 - a) + (rgb & 255) / 255.0 * a;
        p[3] = p[3] + (1 - p[3]) * a;
    }
    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
}
