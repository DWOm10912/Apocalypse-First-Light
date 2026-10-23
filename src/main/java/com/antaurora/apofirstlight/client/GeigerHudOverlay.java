package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.config.GeigerHudConfig;
import com.antaurora.apofirstlight.client.config.GeigerHudConfigManager;
import com.antaurora.apofirstlight.client.map.MinimapHud;
import com.antaurora.apofirstlight.item.GeigerCounterItem;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Geiger counter V2 HUD (2026-10-15, docs/models/geiger_counter_v2_design.md): a close-up of the held counter's face in
 * the survival HUD's metal bezel, bottom right. The LCD window holds only the reading, four seven-segment digits and the
 * decimal point (GeigerReadout: the same smoothed value and auto range as the counter's own LCD); the unit, the count
 * LED and the alarm bell sit in their own column right of the window. Drawn at screen resolution into one dynamic
 * texture (distance-field shapes, anti-aliased); redrawn only when the digits, LED, alarm, GUI scale or size change.
 * <p>Item inspect V1 (docs/gameplay/item_inspect_v1_design.md 5.3, user 2026-10-22): the window is 4 GUI px wider for
 * the battery icon right of the digits (the reading and the charge); shown only while the counter is on with charge,
 * every segment and the whole icon lit for its 1 s self-test.
 */
public final class GeigerHudOverlay {
    /** Panel size (GUI px at hudScale 1), the margin to the screen corner. */
    public static final int WIDTH = 66, HEIGHT = 24, MARGIN = 8;
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/geiger_hud");
    private static final double RADIUS = 3.0, BEZEL = 1.3;
    /** LCD window and the digit grid (panel GUI px). */
    private static final double LCD_X0 = 3.6, LCD_Y0 = 3.6, LCD_X1 = 48.6, LCD_Y1 = 20.4, LCD_R = 1.4;
    private static final double DIGIT_X0 = 7.0, DIGIT_TOP = 6.3, DIGIT_H = 11.4, DIGIT_W = 6.8, PITCH = 9.2, BAR = 1.7, GAP = 0.25;
    /** Right column: its centre line, the unit's top, the LED and the bell centres. */
    private static final double COLUMN_X = 56.6, UNIT_TOP = 4.4, LED_Y = 13.0, LED_R = 1.35, BELL_Y = 18.0;
    private static final int BEZEL_TOP = 0x5A5E64, BEZEL_BOTTOM = 0x2A2C2F, BEZEL_RIM = 0x1A1B1D, OUTLINE = SurvivalHudLayout.OUTLINE,
            FACE = 0x141517, LCD = 0x9FA892, LCD_SHADE = 0x8A937E, INK = 0x1D2219, LED_ON = 0xE5483A, LED_OFF = 0x3A2220,
            AMBER = 0xE2A24A, TEXT = 0xF0ECE4;
    /** The battery icon: frame (left, top, width, height, line), the bars' gap. */
    private static final double ICON_X = 44.4, ICON_Y = 6.0, ICON_W = 2.6, ICON_H = 11.8, ICON_T = 0.45, ICON_GAP = 0.5;
    /** Unlit segments show faintly, as on a real LCD. */
    private static final double GHOST = 0.03;

    private static DynamicTexture texture;
    private static NativeImage image;
    private static int[] base;
    private static int size = -1, imgW, imgH;
    private static double baseScale = -1;
    private static long drawnKey = Long.MIN_VALUE;

    private GeigerHudOverlay() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> render(graphics, width, height);

    private static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || MinimapHud.inspecting()) return;
        var stack = GeigerCounterItem.inUse(minecraft.player, AflItems.GEIGER_COUNTER.get());
        long now = GeigerCounterItem.now();
        if (stack.isEmpty() || !GeigerCounterItem.powered(stack, now)) return;
        GeigerHudConfig config = GeigerHudConfigManager.get();
        if (!config.enabled()) return;
        boolean test = GeigerCounterItem.selfTest(stack, now);
        preview(graphics, screenWidth, screenHeight, config, test ? 3 : GeigerCounterItemRenderer.batteryBars(stack, now), test);
    }

    /** Top-left corner of the panel (GUI px). */
    public static float[] origin(int screenWidth, int screenHeight, GeigerHudConfig config) {
        return new float[]{Math.round(screenWidth - WIDTH * config.hudScale() - MARGIN - config.offsetX()),
                Math.round(screenHeight - HEIGHT * config.hudScale() - MARGIN - config.offsetY())};
    }

    /** The layout editor's preview: a full battery, a reading. */
    public static void preview(GuiGraphics graphics, int screenWidth, int screenHeight, GeigerHudConfig config) {
        preview(graphics, screenWidth, screenHeight, config, 3, false);
    }

    /** The HUD as it is drawn: battery bars (0: the frame alone, blinking), whether it is in the switch-on self-test. */
    public static void preview(GuiGraphics graphics, int screenWidth, int screenHeight, GeigerHudConfig config, int bars, boolean selfTest) {
        Minecraft minecraft = Minecraft.getInstance();
        float[] o = origin(screenWidth, screenHeight, config);
        double gui = minecraft.getWindow().getGuiScale(), k = config.hudScale(), s = gui * k;   // screen px per panel px
        GeigerReadout.Display shown = GeigerReadout.display();
        boolean led = GeigerReadout.ledLit(), alarm = GeigerReadout.alarm() && (System.nanoTime() / 500_000_000L) % 2 == 0;
        int x0 = (int)Math.round(o[0] * gui), y0 = (int)Math.round(o[1] * gui);
        draw(s, shown, led && !selfTest, alarm && !selfTest, bars > 0 ? bars : GeigerCounterItemRenderer.lowBlink() ? 4 : 5, selfTest);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.pose().pushPose();
        graphics.pose().scale((float)(1 / gui), (float)(1 / gui), 1);
        graphics.blit(TEXTURE, x0, y0, 0, 0, imgW, imgH, imgW, imgH);
        graphics.pose().popPose();
        // the unit, outside the LCD (vanilla font at half size, on the screen pixel grid)
        AflHudText.half(graphics, minecraft.font, Component.translatable("hud.apocalypse_firstlight.geiger.unit").getString(),
                x0 / gui + COLUMN_X * k, y0 / gui + UNIT_TOP * k, TEXT);
    }

    // ---- raster ----
    /** icon: 1..3 bars, 4 the frame alone, 5 nothing (the low-battery blink's dark half). */
    private static void draw(double s, GeigerReadout.Display shown, boolean led, boolean alarm, int icon, boolean selfTest) {
        int w = (int)Math.ceil(WIDTH * s) + 2, h = (int)Math.ceil(HEIGHT * s) + 2;
        if (texture == null || imgW != w || imgH != h) {
            image = new NativeImage(NativeImage.Format.RGBA, w, h, true);
            texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);   // closes the previous one
            imgW = w; imgH = h; baseScale = -1; drawnKey = Long.MIN_VALUE;
        }
        if (baseScale != s) { base = background(s, w, h); baseScale = s; drawnKey = Long.MIN_VALUE; }
        long key = ((shown.key() * 4 + (led ? 2 : 0) + (alarm ? 1 : 0)) * 8 + icon) * 2 + (selfTest ? 1 : 0);
        if (key == drawnKey) return;
        float[] px = new float[w * h * 4];
        for (int i = 0; i < w * h; i++) {
            int c = base[i];
            px[i * 4] = (c >> 16 & 255) / 255f; px[i * 4 + 1] = (c >> 8 & 255) / 255f; px[i * 4 + 2] = (c & 255) / 255f; px[i * 4 + 3] = (c >>> 24) / 255f;
        }
        // digits: lit segments in ink, the rest as faint ghosts
        for (int cell = 0; cell < 4; cell++) {
            double cx0 = DIGIT_X0 + cell * PITCH;
            double[][][] segs = segments(cx0);
            for (int bit = 0; bit < 7; bit++) shape(px, w, h, s, segs[bit], INK, selfTest || shown.segment(cell, bit) ? 1 : GHOST);
            if (cell < 3) shape(px, w, h, s, point(cx0), INK, selfTest || shown.dp() == cell ? 1 : GHOST);
        }
        batteryIcon(px, w, h, s, icon);
        disc(px, w, h, s, COLUMN_X, LED_Y, LED_R, led ? LED_ON : LED_OFF, 1);
        if (led) disc(px, w, h, s, COLUMN_X, LED_Y, LED_R + 1.2, LED_ON, 0.18);
        if (alarm) bell(px, w, h, s);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = (y * w + x) * 4;
            float a = px[i + 3];
            int r = Math.round(px[i] * 255), g = Math.round(px[i + 1] * 255), b = Math.round(px[i + 2] * 255), A = Math.round(a * 255);
            image.setPixelRGBA(x, y, A << 24 | b << 16 | g << 8 | r);   // NativeImage is ABGR
        }
        texture.upload();
        drawnKey = key;
    }

    /** Bezel, face and the empty LCD (ARGB per pixel), drawn once per scale. */
    private static int[] background(double s, int w, int h) {
        float[] px = new float[w * h * 4];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double u = (x + 0.5) / s, v = (y + 0.5) / s, aa = 1 / s;
            double d = roundRect(u, v, 0, 0, WIDTH, HEIGHT, RADIUS);
            int i = (y * w + x) * 4;
            // faint light outline round the outside, as the survival rings
            put(px, i, OUTLINE, SurvivalHudLayout.OUTLINE_ALPHA * cover(Math.abs(d + 0.15) - 0.15, aa));
            double inside = cover(d + 0.3, aa);
            if (inside <= 0) continue;
            double t = Math.max(0, Math.min(1, v / HEIGHT));
            put(px, i, mix(BEZEL_TOP, BEZEL_BOTTOM, t), inside);
            put(px, i, BEZEL_RIM, 0.9 * inside * cover(-(d + 0.3) - 0.45, aa));   // dark rim along the outer edge
            double face = roundRect(u, v, BEZEL, BEZEL, WIDTH - BEZEL, HEIGHT - BEZEL, RADIUS - BEZEL * 0.6);
            put(px, i, FACE, cover(face, aa));
            double lcd = roundRect(u, v, LCD_X0, LCD_Y0, LCD_X1, LCD_Y1, LCD_R);
            double c = cover(lcd, aa);
            if (c > 0) {
                // the window's top edge in shadow: a slightly darker band under the bezel lip
                double shade = Math.max(0, 1 - (v - LCD_Y0) / 1.6);
                put(px, i, mix(LCD, LCD_SHADE, shade), c);
            }
        }
        int[] out = new int[w * h];
        for (int k = 0; k < w * h; k++) {
            int r = Math.round(px[k * 4] * 255), g = Math.round(px[k * 4 + 1] * 255), b = Math.round(px[k * 4 + 2] * 255), a = Math.round(px[k * 4 + 3] * 255);
            out[k] = a << 24 | r << 16 | g << 8 | b;
        }
        return out;
    }

    /** The seven segments a..g of the digit whose left edge is x0, each a convex hexagon (pointed ends). */
    private static double[][][] segments(double x0) {
        double hb = BAR / 2, l = x0 + hb, r = x0 + DIGIT_W - hb, t = DIGIT_TOP + hb, b = DIGIT_TOP + DIGIT_H - hb, m = (t + b) / 2;
        return new double[][][]{hBar(l, r, t), vBar(r, t, m), vBar(r, m, b), hBar(l, r, b), vBar(l, m, b), vBar(l, t, m), hBar(l, r, m)};
    }

    private static double[][] hBar(double x0, double x1, double y) {
        double h = BAR / 2;
        return new double[][]{{x0 + GAP, y}, {x0 + h + GAP, y - h}, {x1 - h - GAP, y - h}, {x1 - GAP, y}, {x1 - h - GAP, y + h}, {x0 + h + GAP, y + h}};
    }

    private static double[][] vBar(double x, double y0, double y1) {
        double h = BAR / 2;
        return new double[][]{{x, y0 + GAP}, {x + h, y0 + h + GAP}, {x + h, y1 - h - GAP}, {x, y1 - GAP}, {x - h, y1 - h - GAP}, {x - h, y0 + h + GAP}};
    }

    /** The decimal point in the gap after the digit at x0, on the baseline. */
    private static double[][] point(double x0) {
        double cx = x0 + (DIGIT_W + PITCH) / 2, y1 = DIGIT_TOP + DIGIT_H, d = BAR;
        return new double[][]{{cx - d / 2, y1 - d}, {cx + d / 2, y1 - d}, {cx + d / 2, y1}, {cx - d / 2, y1}};
    }

    /** The LCD's battery icon right of the digits: a frame with its + nub on top, three bars filling from the bottom. */
    private static void batteryIcon(float[] px, int w, int h, double s, int icon) {
        double x0 = ICON_X, y0 = ICON_Y, x1 = x0 + ICON_W, y1 = y0 + ICON_H, t = ICON_T, frame = icon <= 4 ? 1 : GHOST;
        for (double[][] r : new double[][][]{rect(x0, y0, x1, y0 + t), rect(x0, y1 - t, x1, y1), rect(x0, y0 + t, x0 + t, y1 - t),
                rect(x1 - t, y0 + t, x1, y1 - t), rect(x0 + 0.7, y0 - 0.9, x1 - 0.7, y0)}) shape(px, w, h, s, r, INK, frame);
        double ih = (ICON_H - 2 * t - 0.6 - 2 * ICON_GAP) / 3, bx0 = x0 + t + 0.35, bx1 = x1 - t - 0.35;
        for (int i = 0; i < 3; i++) {
            double top = y1 - t - 0.3 - (i + 1) * ih - i * ICON_GAP;
            shape(px, w, h, s, rect(bx0, top, bx1, top + ih), INK, icon <= 3 && i < icon ? 1 : GHOST);
        }
    }

    private static double[][] rect(double x0, double y0, double x1, double y1) {
        return new double[][]{{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}};
    }

    /** Alarm bell in the right column: a dome on a rim with the clapper under it. */
    private static void bell(float[] px, int w, int h, double s) {
        double cx = COLUMN_X, cy = BELL_Y, aa = 1 / s;
        int xa = (int)Math.floor((cx - 3) * s), xb = (int)Math.ceil((cx + 3) * s), ya = (int)Math.floor((cy - 3) * s), yb = (int)Math.ceil((cy + 3) * s);
        for (int y = Math.max(0, ya); y < Math.min(h, yb); y++) for (int x = Math.max(0, xa); x < Math.min(w, xb); x++) {
            double u = (x + 0.5) / s - cx, v = (y + 0.5) / s - cy;
            double dome = Math.max((Math.hypot(u / 1.6, (v + 0.2) / 2.0) - 1) * 1.6, v - 1.0);
            double rim = roundRect(u, v, -2.1, 0.8, 2.1, 1.5, 0.3);
            double clapper = Math.hypot(u, v - 2.0) - 0.55;
            put(px, (y * w + x) * 4, AMBER, cover(Math.min(Math.min(dome, rim), clapper), aa));
        }
    }

    private static void disc(float[] px, int w, int h, double s, double cx, double cy, double r, int rgb, double alpha) {
        double aa = 1 / s;
        int xa = (int)Math.floor((cx - r - 1) * s), xb = (int)Math.ceil((cx + r + 1) * s), ya = (int)Math.floor((cy - r - 1) * s), yb = (int)Math.ceil((cy + r + 1) * s);
        for (int y = Math.max(0, ya); y < Math.min(h, yb); y++) for (int x = Math.max(0, xa); x < Math.min(w, xb); x++) {
            double d = Math.hypot((x + 0.5) / s - cx, (y + 0.5) / s - cy) - r;
            put(px, (y * w + x) * 4, rgb, alpha * cover(d, aa));
        }
    }

    /** A convex polygon (panel px, either winding) composited over the pixels in its bounds. */
    private static void shape(float[] px, int w, int h, double s, double[][] poly, int rgb, double alpha) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, area = 0;
        for (int k = 0; k < poly.length; k++) {
            double[] p = poly[k], q = poly[(k + 1) % poly.length];
            minX = Math.min(minX, p[0]); minY = Math.min(minY, p[1]); maxX = Math.max(maxX, p[0]); maxY = Math.max(maxY, p[1]);
            area += p[0] * q[1] - q[0] * p[1];
        }
        double sign = area > 0 ? 1 : -1, aa = 1 / s;
        int xa = (int)Math.floor(minX * s) - 1, xb = (int)Math.ceil(maxX * s) + 1, ya = (int)Math.floor(minY * s) - 1, yb = (int)Math.ceil(maxY * s) + 1;
        for (int y = Math.max(0, ya); y < Math.min(h, yb); y++) for (int x = Math.max(0, xa); x < Math.min(w, xb); x++) {
            double u = (x + 0.5) / s, v = (y + 0.5) / s, d = -Double.MAX_VALUE;
            for (int k = 0; k < poly.length; k++) {
                double[] p = poly[k], q = poly[(k + 1) % poly.length];
                double ex = q[0] - p[0], ey = q[1] - p[1], len = Math.hypot(ex, ey);
                if (len < 1e-9) continue;
                // outward distance from the edge line (y down: a positive-area loop runs clockwise on screen)
                d = Math.max(d, sign * ((u - p[0]) * ey - (v - p[1]) * ex) / len);
            }
            put(px, (y * w + x) * 4, rgb, alpha * cover(d, aa));
        }
    }

    /** Signed distance (panel px) to a rounded rectangle, negative inside. */
    private static double roundRect(double u, double v, double x0, double y0, double x1, double y1, double r) {
        double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, hx = (x1 - x0) / 2 - r, hy = (y1 - y0) / 2 - r;
        double qx = Math.abs(u - cx) - hx, qy = Math.abs(v - cy) - hy;
        return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
    }

    /** Coverage of a pixel at signed distance d, aa = one screen pixel in panel px. */
    private static double cover(double d, double aa) { return Math.max(0, Math.min(1, 0.5 - d / aa)); }

    /** "Over" compositing of rgb at alpha onto the straight-alpha float pixel at i. */
    private static void put(float[] px, int i, int rgb, double alpha) {
        if (alpha <= 0) return;
        float a = (float)Math.min(1, alpha), r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        float da = px[i + 3], oa = a + da * (1 - a);
        if (oa <= 0) return;
        px[i] = (r * a + px[i] * da * (1 - a)) / oa;
        px[i + 1] = (g * a + px[i + 1] * da * (1 - a)) / oa;
        px[i + 2] = (b * a + px[i + 2] * da * (1 - a)) / oa;
        px[i + 3] = oa;
    }

    private static int mix(int a, int b, double t) {
        int r = (int)Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = (int)Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int bl = (int)Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }
}
