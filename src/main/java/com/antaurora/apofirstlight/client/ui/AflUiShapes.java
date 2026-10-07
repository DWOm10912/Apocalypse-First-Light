package com.antaurora.apofirstlight.client.ui;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Smooth rounded boxes for the overlay UI (docs/ui/afl_overlay_ui_style_v1.md): a soft shadow, a fill and an inner
 * hairline, anti-aliased per real screen pixel from one signed distance field, as the survival HUD's AflGauge draws
 * its rings. Drawn nine-slice: per (corner radius, hairline width, shadow spread) in screen pixels one small texture
 * holds the three layers' corners and a one-texel straight edge; the edges and the middle stretch that texel, so a box of
 * any size costs one texture and one draw call (up to 27 quads, coloured per vertex). The textures are rebuilt when the
 * GUI scale changes.
 */
public final class AflUiShapes {
    private static final int SHADOW = 0, FILL = 1, LINE = 2;

    private record Key(int radius, int line, int spread) {}

    private static final class Tile {
        final ResourceLocation id;
        /** Corner extent inside the box (= radius), margin outside it, corner tile side, layer tile side. */
        final int k, m, c, t;

        Tile(ResourceLocation id, int k, int m) {
            this.id = id;
            this.k = k;
            this.m = m;
            this.c = m + k;
            this.t = 2 * c + 1;
        }
    }

    private static final Map<Key, Tile> TILES = new HashMap<>();
    private static int tileScale = -1, made;

    private AflUiShapes() {}

    /**
     * A rounded box at GUI coordinates. Alphas of 0 skip a layer. fadeTo &gt; 0 fades every layer in from the left edge
     * (alpha 0) to full at that fraction of the width (the key hint panel's open left side).
     */
    public static void box(GuiGraphics g, float x, float y, float w, float h, float radius,
                           int fillRgb, float fillAlpha, int lineRgb, float lineAlpha, float shadowAlpha, float fadeTo) {
        int s = Math.round(AflUiTween.scale());
        int x0 = Math.round(x * s), y0 = Math.round(y * s), x1 = Math.round((x + w) * s), y1 = Math.round((y + h) * s);
        int bw = x1 - x0, bh = y1 - y0;
        if (bw <= 0 || bh <= 0 || fillAlpha <= 0 && lineAlpha <= 0 && shadowAlpha <= 0) return;
        int r = Math.max(0, Math.min(Math.round(radius * s), Math.min(bw, bh) / 2));
        int line = Math.max(1, Math.round(s * 0.3F)), spread = shadowAlpha > 0 ? Math.max(1, Math.round(AflUiStyle.SHADOW_SPREAD * s)) : 0;
        Tile tile = tile(s, r, line, spread);

        g.flush();
        RenderSystem.setShaderTexture(0, tile.id);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().scale(1.0F / s, 1.0F / s, 1.0F);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        if (shadowAlpha > 0) {
            int drop = Math.round(AflUiStyle.SHADOW_DROP * s);
            nine(b, m, tile, SHADOW, x0, y0 + drop, bw, bh, 0x000000, shadowAlpha, fadeTo);
        }
        if (fillAlpha > 0) nine(b, m, tile, FILL, x0, y0, bw, bh, fillRgb, fillAlpha, fadeTo);
        if (lineAlpha > 0) nine(b, m, tile, LINE, x0, y0, bw, bh, lineRgb, lineAlpha, fadeTo);
        BufferUploader.drawWithShader(b.end());
        g.pose().popPose();
    }

    /** A plain rounded box: fill only. */
    public static void fill(GuiGraphics g, float x, float y, float w, float h, float radius, int rgb, float alpha) {
        box(g, x, y, w, h, radius, rgb, alpha, 0, 0, 0, 0);
    }

    private static void nine(BufferBuilder b, Matrix4f m, Tile tile, int layer, int x0, int y0, int w, int h,
                             int rgb, float alpha, float fadeTo) {
        int k = tile.k, c = tile.c, t = tile.t;
        float texW = 3f * t, texH = t, u0 = layer * t;
        // columns: screen x and texture u; the middle column (one stretched texel) is split at the fade's knee
        int knee = fadeTo > 0 ? x0 + Math.round(fadeTo * w) : Integer.MIN_VALUE;
        int[] xs;
        if (knee > x0 + k && knee < x0 + w - k) xs = new int[]{x0 - tile.m, x0 + k, knee, x0 + w - k, x0 + w + tile.m};
        else xs = new int[]{x0 - tile.m, x0 + k, x0 + w - k, x0 + w + tile.m};
        int[] ys = {y0 - tile.m, y0 + k, y0 + h - k, y0 + h + tile.m};
        float[] vs = {0, c, c + 1, t};
        float red = (rgb >> 16 & 255) / 255f, green = (rgb >> 8 & 255) / 255f, blue = (rgb & 255) / 255f;
        for (int col = 0; col + 1 < xs.length; col++) {
            if (xs[col + 1] <= xs[col]) continue;
            boolean first = col == 0, last = col + 2 == xs.length;
            float ua = first ? 0 : last ? c + 1 : c, ub = first ? c : last ? t : c + 1;
            float aa = alpha * fade(xs[col], x0, w, fadeTo), ab = alpha * fade(xs[col + 1], x0, w, fadeTo);
            for (int row = 0; row < 3; row++) {
                if (ys[row + 1] <= ys[row]) continue;
                float va = vs[row], vb = vs[row + 1];
                b.vertex(m, xs[col], ys[row], 0).uv((u0 + ua) / texW, va / texH).color(red, green, blue, aa).endVertex();
                b.vertex(m, xs[col], ys[row + 1], 0).uv((u0 + ua) / texW, vb / texH).color(red, green, blue, aa).endVertex();
                b.vertex(m, xs[col + 1], ys[row + 1], 0).uv((u0 + ub) / texW, vb / texH).color(red, green, blue, ab).endVertex();
                b.vertex(m, xs[col + 1], ys[row], 0).uv((u0 + ub) / texW, va / texH).color(red, green, blue, ab).endVertex();
            }
        }
    }

    private static float fade(int x, int x0, int w, float fadeTo) {
        return fadeTo <= 0 ? 1 : AflUiTween.clamp01((x - x0) / (fadeTo * w));
    }

    private static Tile tile(int scale, int radius, int line, int spread) {
        if (scale != tileScale) {
            for (Tile old : TILES.values()) Minecraft.getInstance().getTextureManager().release(old.id);
            TILES.clear();
            tileScale = scale;
        }
        return TILES.computeIfAbsent(new Key(radius, line, spread), key -> {
            Tile tile = new Tile(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/ui_box_" + made++), radius, spread + 2);
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, 3 * tile.t, tile.t, true);
            raster(image, tile, line, spread);
            Minecraft.getInstance().getTextureManager().register(tile.id, new DynamicTexture(image));
            return tile;
        });
    }

    /** The three layers of a box 2k+1 pixels wide, centred in a tile of side t: white, alpha = coverage. */
    private static void raster(NativeImage image, Tile tile, int line, int spread) {
        double centre = tile.m + (2 * tile.k + 1) / 2.0, inner = (2 * tile.k + 1) / 2.0 - tile.k;
        for (int py = 0; py < tile.t; py++) for (int px = 0; px < tile.t; px++) {
            double qx = Math.abs(px + 0.5 - centre) - inner, qy = Math.abs(py + 0.5 - centre) - inner;
            double d = Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - tile.k;
            double fill = clamp(0.5 - d);
            double ring = fill * clamp(0.5 + d + line);
            double shadow = 0;
            if (spread > 0 && d > -0.5) {
                double f = 1 - clamp(d / spread);
                shadow = clamp(0.5 + d) * f * f;
            }
            set(image, SHADOW * tile.t + px, py, shadow);
            set(image, FILL * tile.t + px, py, fill);
            set(image, LINE * tile.t + px, py, ring);
        }
    }

    private static void set(NativeImage image, int x, int y, double alpha) {
        int a = (int) Math.round(clamp(alpha) * 255);
        image.setPixelRGBA(x, y, a == 0 ? 0 : a << 24 | 0xFFFFFF); // ABGR: white
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
