package com.antaurora.apofirstlight.client.map;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.map.MapRaster;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Drawing pieces shared by the minimap and the world map screen (AFL Map V1). */
final class MapDraw {
    private MapDraw() {}

    static final ResourceLocation FUEL = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/gui/map/fuel_stop.png");

    /** The zoom level whose pixels are nearest the screen's (real pixels per metre given as metres per real pixel). */
    static int level(double metresPerRealPixel) {
        int l = (int) Math.floor(Math.log(Math.max(1e-6, metresPerRealPixel) / 0.5) / Math.log(2) + 0.35);
        return Math.max(0, Math.min(MapRaster.LEVELS - 1, l));
    }

    /**
     * The tiles round world (cx, cz) at mPerGui metres per GUI pixel, its centre at GUI (sx, sy), turned by rotDeg
     * (clockwise), reach GUI pixels round the centre; the next coarser level first under any tile still on its way.
     */
    static void tiles(GuiGraphics g, MapTiles tiles, double cx, double cz, double mPerGui, float sx, float sy, float rotDeg,
                      float reach, MapProgram.Look look) {
        if (!MapProgram.ready()) return;
        double real = mPerGui / Minecraft.getInstance().getWindow().getGuiScale();
        int level = level(real);
        g.flush();
        g.pose().pushPose();
        g.pose().translate(sx, sy, 0);
        if (rotDeg != 0) g.pose().mulPose(Axis.ZP.rotationDegrees(rotDeg));
        Matrix4f m = g.pose().last().pose();
        double r = reach * mPerGui;
        for (int l = Math.min(MapRaster.LEVELS - 1, level + 1); l >= level; l--) {
            double span = MapRaster.span(l);
            int tx0 = (int) Math.floor((cx - r) / span), tx1 = (int) Math.floor((cx + r) / span);
            int tz0 = (int) Math.floor((cz - r) / span), tz1 = (int) Math.floor((cz + r) / span);
            for (int tz = tz0; tz <= tz1; tz++)
                for (int tx = tx0; tx <= tx1; tx++) {
                    int tex = tiles.texture(l, tx, tz);
                    if (tex < 0) continue;
                    float x0 = (float) ((tx * span - cx) / mPerGui), z0 = (float) ((tz * span - cz) / mPerGui);
                    float x1 = (float) (((tx + 1) * span - cx) / mPerGui), z1 = (float) (((tz + 1) * span - cz) / mPerGui);
                    MapProgram.tile(m, x0, z0, x1, z1, tex, look);
                }
        }
        g.pose().popPose();
    }

    /** The player's arrow: white with a dark rim, pointing up turned by rotDeg (clockwise), r GUI pixels to its tip. */
    static void arrow(GuiGraphics g, float x, float y, float rotDeg, float r) {
        g.flush();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(rotDeg));
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        arrowShape(b, m, r * 1.32F, 0, 0.4F, 0x15181C, 0.85F);
        arrowShape(b, m, r * 1.32F, 0, 0.0F, 0x15181C, 1.0F);
        arrowShape(b, m, r, 0, 0, 0xFFFFFF, 1.0F);
        BufferUploader.drawWithShader(b.end());
        g.pose().popPose();
    }

    private static void arrowShape(BufferBuilder b, Matrix4f m, float r, float dx, float dy, int rgb, float a) {
        int cr = rgb >> 16 & 255, cg = rgb >> 8 & 255, cb = rgb & 255, ca = Math.round(a * 255);
        float[] tip = {dx, dy - r}, right = {dx + r * 0.72F, dy + r * 0.8F}, notch = {dx, dy + r * 0.36F}, left = {dx - r * 0.72F, dy + r * 0.8F};
        float[][] tris = {tip, notch, right, tip, left, notch};
        for (float[] p : tris) b.vertex(m, p[0], p[1], 0).color(cr, cg, cb, ca).endVertex();
    }

    /** A round dot (rounded box with half its size as the radius). */
    static void disc(GuiGraphics g, float x, float y, float r, int rgb, float a) {
        AflUiShapes.fill(g, x - r, y - r, 2 * r, 2 * r, r, rgb, a);
    }

    private static boolean filtered;

    /** A map icon (a texture with its own alpha), size GUI pixels, centred on (x, y), smoothly filtered. */
    static void icon(GuiGraphics g, ResourceLocation tex, float x, float y, float size) {
        g.flush();
        Minecraft mc = Minecraft.getInstance();
        AbstractTexture t = mc.getTextureManager().getTexture(tex);
        if (!filtered) {
            t.setFilter(true, false);
            filtered = true;
        }
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, t.getId());
        RenderSystem.setShaderColor(1, 1, 1, 1);
        float h = size / 2;
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(m, x - h, y - h, 0).uv(0, 0).endVertex();
        b.vertex(m, x - h, y + h, 0).uv(0, 1).endVertex();
        b.vertex(m, x + h, y + h, 0).uv(1, 1).endVertex();
        b.vertex(m, x + h, y - h, 0).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(b.end());
    }
}
