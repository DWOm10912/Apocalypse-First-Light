package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/**
 * The cold screen effect's frost, vanilla pixel style: pixel snowflakes (8 arms with V branches, 6 sizes, 2 variants)
 * packed along the screen border in clusters, larger near the edge, densest and deepest in the corners, with a thin
 * dusting of rime right on the frame. Every flake has a birth (outer and corner flakes first), so as the cold deepens
 * the frost grows inward flake by flake instead of fading in. 3 flat tones of pale ice cyan. One texel is about 5 real
 * pixels; the layout is deterministic for a screen size and rebuilt only when the size or the (quantised) intensity
 * changes. Procedural, no image asset.
 */
final class TemperatureFrost {
    static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/temperature_frost");
    /** Reference layout: 400 texels across. */
    private static final double REFERENCE_WIDTH = 400;
    /** ABGR (NativeImage) of the three tones: rim / branch / bright core. */
    private static final int[] TONES = {0, abgr(0x86C9E4, 0.50), abgr(0xB9E5F5, 0.70), abgr(0xEAF8FD, 0.85)};
    private record Sprite(int n, int r, byte[] g) {}
    private record Flake(int x, int y, Sprite sprite, double birth) {}
    private static final Sprite[][] SPRITES = new Sprite[6][2];
    static {
        int[] radii = {2, 3, 5, 7, 9, 12};
        for (int i = 0; i < radii.length; i++) { SPRITES[i][0] = sprite(radii[i], false); SPRITES[i][1] = sprite(radii[i], true); }
    }
    private static List<Flake> flakes = List.of();
    private static int gw, gh, builtStep = -1;
    private static DynamicTexture texture;
    private static NativeImage image;
    private TemperatureFrost() {}

    static int width() { return gw; }
    static int height() { return gh; }

    /** Make sure the frost texture fits a texel grid of w × h and shows the given intensity 0..1; false if nothing to draw. */
    static boolean prepare(int w, int h, double intensity) {
        int step = (int)Math.round(Math.max(0, Math.min(1, intensity)) * 200);
        if (w != gw || h != gh || texture == null) {
            gw = w; gh = h;
            layout();
            image = new NativeImage(NativeImage.Format.RGBA, gw, gh, true);
            texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(TEXTURE, texture); // closes the previous one
            builtStep = -1;
        }
        if (step != builtStep) { paint(step / 200.0); texture.upload(); builtStep = step; }
        return step > 0;
    }

    private static void layout() {
        List<Flake> list = new ArrayList<>();
        double scale = gw / REFERENCE_WIDTH;
        int candidates = (int)Math.round(9000 * (gw * (double)gh) / (REFERENCE_WIDTH * 225));
        for (int n = 0; n < candidates; n++) {
            int x = (int)(hash(n, 1) * gw), y = (int)(hash(n, 2) * gh);
            double corner = corner(x, y), reach = (18 + 95 * corner) * scale, depth = edgeDistance(x, y) / reach;
            double clump = 0.35 + 1.3 * noise(x / (14 * scale), y / (14 * scale));
            if (depth > 1 || hash(n, 3) > Math.pow(1 - depth, 1.1) * (0.40 + 0.9 * corner) * clump) continue;
            int size = (int)Math.min(5, Math.max(0, Math.floor((1 - depth) * 4.2 + hash(n, 4) * 2.2 - 0.6)));
            double birth = clamp(depth * 0.78 + (1 - corner) * 0.22 + hash(n, 6) * 0.08);
            list.add(new Flake(x, y, SPRITES[size][hash(n, 5) < 0.5 ? 0 : 1], birth));
        }
        flakes = list;
    }

    private static void paint(double intensity) {
        byte[] tone = new byte[gw * gh];
        if (intensity > 0) {
            for (var f : flakes) {
                if (f.birth > intensity) continue;
                var s = f.sprite;
                for (int j = 0; j < s.n; j++) for (int i = 0; i < s.n; i++) {
                    byte v = s.g[j * s.n + i];
                    if (v == 0) continue;
                    int x = f.x + i - s.r, y = f.y + j - s.r;
                    if (x < 0 || y < 0 || x >= gw || y >= gh) continue;
                    if (v > tone[y * gw + x]) tone[y * gw + x] = v;
                }
            }
            // rime dusting right on the frame, wider in the corners
            for (int y = 0; y < gh; y++) for (int x = 0; x < gw; x++)
                if (tone[y * gw + x] == 0 && edgeDistance(x, y) < (1 + 3 * corner(x, y)) * intensity && hash(x * 977 + y, 7) < 0.55)
                    tone[y * gw + x] = 1;
        }
        for (int y = 0; y < gh; y++) for (int x = 0; x < gw; x++) image.setPixelRGBA(x, y, TONES[tone[y * gw + x]]);
    }

    /** A pixel snowflake of radius r: 4 straight and 4 shorter diagonal arms, V branches outward, a small hollow centre. */
    private static Sprite sprite(int r, boolean variant) {
        int n = 2 * r + 1;
        byte[] g = new byte[n * n];
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int k = 0; k < dirs.length; k++) {
            int dx = dirs[k][0], dy = dirs[k][1];
            boolean diag = k >= 4;
            int len = diag ? (int)Math.round(r * 0.72) : r;
            for (int i = 1; i <= len; i++) set(g, n, r + dx * i, r + dy * i, i <= len - 1 ? 2 : 1);
            if (r < 3) continue;
            for (double at : variant ? new double[]{0.5} : new double[]{0.45, 0.75}) {
                int b = (int)Math.round(len * at), bl = Math.max(1, (int)Math.round(len * (1 - at) * 0.6));
                for (int j = 1; j <= bl; j++) for (int s = -1; s <= 1; s += 2) {
                    int px, py;
                    if (!diag) { px = r + dx * (b + j) - dy * s * j; py = r + dy * (b + j) + dx * s * j; }
                    else { px = r + dx * b + (s < 0 ? dx * j : 0); py = r + dy * b + (s > 0 ? dy * j : 0); }
                    set(g, n, px, py, j < bl ? 2 : 1);
                }
            }
        }
        set(g, n, r, r, 1);
        set(g, n, r + 1, r, 3); set(g, n, r - 1, r, 3); set(g, n, r, r + 1, 3); set(g, n, r, r - 1, 3);
        return new Sprite(n, r, g);
    }
    private static void set(byte[] g, int n, int x, int y, int v) {
        if (x >= 0 && y >= 0 && x < n && y < n && g[y * n + x] < v) g[y * n + x] = (byte)v;
    }

    private static int edgeDistance(int x, int y) { return Math.min(Math.min(x, gw - 1 - x), Math.min(y, gh - 1 - y)); }
    /** 0 along the middle of the sides .. 1 in the corners (elliptical distance from the centre). */
    private static double corner(int x, int y) {
        double e = Math.hypot((x + 0.5 - gw / 2.0) / (gw / 2.0), (y + 0.5 - gh / 2.0) / (gh / 2.0));
        return smooth(1.15, 1.40, e);
    }
    static double hash(int n, int s) {
        int h = n * 374761393 + s * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFFFFL) / 4294967296.0;
    }
    /** Smooth value noise (cells of 1), 0..1. */
    static double noise(double x, double y) {
        int xi = (int)Math.floor(x), yi = (int)Math.floor(y);
        double fx = x - xi, fy = y - yi, sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
        double a = hash(xi * 7919 + yi, 11), b = hash((xi + 1) * 7919 + yi, 11), c = hash(xi * 7919 + yi + 1, 11), d = hash((xi + 1) * 7919 + yi + 1, 11);
        return (a * (1 - sx) + b * sx) * (1 - sy) + (c * (1 - sx) + d * sx) * sy;
    }
    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    private static double smooth(double a, double b, double x) { double t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); }
    private static int abgr(int rgb, double alpha) {
        int a = (int)Math.round(alpha * 255);
        return a << 24 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
    }
}
