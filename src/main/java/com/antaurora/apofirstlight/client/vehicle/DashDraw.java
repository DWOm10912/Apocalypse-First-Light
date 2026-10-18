package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Drawing pieces of the driving dashboard (VehicleHud; design doc 8.12), in GUI pixels:
 * - text at any size, its origin on the screen pixel grid (the AFL font rasterises scaled text at its real size);
 * - a {@link Mask}: a shape's anti-aliased coverage rastered once per GUI scale into its own texture, then drawn tinted
 *   through polygons that cut it (a ring sector of a segmented band, a slanted cut of the rpm ramp), so a gauge that
 *   moves every frame costs a few triangles instead of a new raster;
 * - horizontal gradients (the panels that fade out to the left, the top strip that fades at both ends).
 */
final class DashDraw {
    private DashDraw() {
    }

    static int scale() {
        return (int) Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
    }

    static double snap(double gui) {
        int s = scale();
        return Math.round(gui * s) / (double) s;
    }

    /**
     * Text of size k (1 = the font's own size) with its top at y: align -1 left of x, 0 centred on it, 1 right of it.
     * The baseline sits 7 k below the top.
     */
    static void text(GuiGraphics g, Font font, String s, double x, double y, float k, int rgb, float alpha, int align, boolean shadow) {
        if (alpha <= 0.02F) return;
        double w = font.width(s) * k, x0 = align < 0 ? x : align == 0 ? x - w / 2 : x - w;
        g.pose().pushPose();
        g.pose().translate(snap(x0), snap(y), 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, s, 0, 0, Math.round(alpha * 255) << 24 | rgb & 0xFFFFFF, shadow);
        g.pose().popPose();
    }

    /** Coverage of a mask at a point (GUI px from the mask's top-left), 0..1; px: GUI px per screen pixel (1 / scale). */
    @FunctionalInterface
    interface Coverage {
        double at(double x, double y, double px);
    }

    /** A shape rastered once per GUI scale (white, alpha = coverage), drawn tinted through cut polygons. */
    static final class Mask {
        private final ResourceLocation id;
        private final double w, h;
        private final Coverage coverage;
        private DynamicTexture texture;
        private int builtScale = -1, pw, ph;

        Mask(String name, double w, double h, Coverage coverage) {
            this.id = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/dash_" + name);
            this.w = w;
            this.h = h;
            this.coverage = coverage;
        }

        private void build() {
            int s = scale();
            if (texture != null && builtScale == s) return;
            pw = (int) Math.ceil(w * s);
            ph = (int) Math.ceil(h * s);
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, pw, ph, true);
            for (int y = 0; y < ph; y++)
                for (int x = 0; x < pw; x++) {
                    double c = Math.max(0, Math.min(1, coverage.at((x + 0.5) / s, (y + 0.5) / s, 1.0 / s)));
                    int a = (int) Math.round(c * 255);
                    image.setPixelRGBA(x, y, a == 0 ? 0 : a << 24 | 0xFFFFFF);
                }
            texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(id, texture);   // closes the previous one
            builtScale = s;
        }

        /**
         * The mask with its top-left at (ox, oy), tinted, only inside the triangles (GUI px from the mask's top-left, three
         * corners each: x0, y0, x1, y1, x2, y2, ...).
         */
        void draw(GuiGraphics g, double ox, double oy, double[] triangles, int rgb, float alpha) {
            if (alpha <= 0 || triangles.length < 6) return;
            build();
            int s = builtScale;
            double x0 = Math.round(ox * s), y0 = Math.round(oy * s);
            g.flush();
            RenderSystem.setShaderTexture(0, id);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // either winding: a ring sector's fan runs clockwise on screen, which back-face culling dropped (the ring's lit
            // rpm cells never showed; user 2026-10-17)
            RenderSystem.disableCull();
            g.pose().pushPose();
            g.pose().scale(1.0F / s, 1.0F / s, 1.0F);
            Matrix4f m = g.pose().last().pose();
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            int r = rgb >> 16 & 255, gr = rgb >> 8 & 255, bl = rgb & 255, a = Math.round(Math.min(1, alpha) * 255);
            for (int i = 0; i + 1 < triangles.length; i += 2) {
                double px = Math.max(0, Math.min(pw, triangles[i] * s)), py = Math.max(0, Math.min(ph, triangles[i + 1] * s));
                b.vertex(m, (float) (x0 + px), (float) (y0 + py), 0).uv((float) (px / pw), (float) (py / ph)).color(r, gr, bl, a).endVertex();
            }
            BufferUploader.drawWithShader(b.end());
            RenderSystem.enableCull();
            g.pose().popPose();
        }

        /** The whole mask, tinted. */
        void draw(GuiGraphics g, double ox, double oy, int rgb, float alpha) {
            draw(g, ox, oy, new double[]{0, 0, 0, h, w, h, 0, 0, w, h, w, 0}, rgb, alpha);
        }
    }

    /**
     * Triangles of the sector of a circle centred at (cx, cy) from `from` to `to` degrees (clockwise from the top), out to
     * radius r: a fan from the centre in steps of at most 4 degrees.
     */
    static double[] sector(double cx, double cy, double r, double from, double to) {
        if (to <= from) return new double[0];
        int n = (int) Math.ceil((to - from) / 4.0);
        double[] t = new double[n * 6];
        for (int i = 0; i < n; i++) {
            double a0 = Math.toRadians(from + (to - from) * i / n), a1 = Math.toRadians(from + (to - from) * (i + 1) / n);
            t[i * 6] = cx;
            t[i * 6 + 1] = cy;
            t[i * 6 + 2] = cx + Math.sin(a0) * r;
            t[i * 6 + 3] = cy - Math.cos(a0) * r;
            t[i * 6 + 4] = cx + Math.sin(a1) * r;
            t[i * 6 + 5] = cy - Math.cos(a1) * r;
        }
        return t;
    }

    /** A quad (two triangles) through four corners in order. */
    static double[] quad(double x0, double y0, double x1, double y1, double x2, double y2, double x3, double y3) {
        return new double[]{x0, y0, x1, y1, x2, y2, x0, y0, x2, y2, x3, y3};
    }

    /** A horizontal gradient from x0 to x1: colours at the given stops (fractions, rising), alphas per stop. */
    static void hGradient(GuiGraphics g, double x0, double y0, double x1, double y1, int rgb, double[] stops, float[] alphas) {
        g.flush();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int r = rgb >> 16 & 255, gr = rgb >> 8 & 255, bl = rgb & 255;
        for (int i = 0; i + 1 < stops.length; i++) {
            float xa = (float) (x0 + (x1 - x0) * stops[i]), xb = (float) (x0 + (x1 - x0) * stops[i + 1]);
            int aa = Math.round(alphas[i] * 255), ab = Math.round(alphas[i + 1] * 255);
            b.vertex(m, xa, (float) y0, 0).color(r, gr, bl, aa).endVertex();
            b.vertex(m, xa, (float) y1, 0).color(r, gr, bl, aa).endVertex();
            b.vertex(m, xb, (float) y1, 0).color(r, gr, bl, ab).endVertex();
            b.vertex(m, xb, (float) y0, 0).color(r, gr, bl, ab).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
    }
}
