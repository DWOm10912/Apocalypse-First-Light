package com.antaurora.apofirstlight.client.map;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.FirstPersonBodyConfig;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
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
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The compass bar at the top centre (AFL Map V1, docs/ui/afl_map_v1_plan.md 6.1; user 2026-10-21: "头顶的那种方位坐标，
 * 就是FPS游戏常见的那种"): a strip of headings {@link #SPAN} degrees wide round where the camera looks, a tick every 5 degrees,
 * a number every 15, N NE E SE S SW W NW every 45 (N in the accent), fading out to both ends; under the middle a mark and
 * the heading in degrees (0 north, 90 east). Below the boss bars when there are any. Only with a compass in the hand or
 * on a clip (user 2026-10-21; AflEquipmentSlots#inUse), in every dimension; the settings hide it; not with F1 or F3.
 * Registered with the minimap's overlays (MinimapHud#register).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class CompassHud {
    private CompassHud() {}

    /** Degrees across the bar. */
    static final float SPAN = 150;
    static final float TOP = 5, BAND = 13, TEXT_CARDINAL = 0.8F, TEXT_HALF = 0.65F, TEXT_NUMBER = 0.55F, TEXT_HEADING = 0.75F;
    private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    /** The lowest boss bar this frame (its bar's top + room), 0 without boss bars. */
    private static float bossBottom;

    @SubscribeEvent
    public static void boss(CustomizeGuiOverlayEvent.BossEventProgress event) {
        bossBottom = Math.max(bossBottom, event.getY() + 8);
    }

    static final IGuiOverlay OVERLAY = (gui, g, partialTick, width, height) -> {
        float boss = bossBottom;
        bossBottom = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.options.renderDebug || !FirstPersonBodyConfig.compass()
                || !com.antaurora.apofirstlight.equipment.AflEquipmentSlots.inUse(mc.player, com.antaurora.apofirstlight.registry.AflItems.COMPASS.get())
                || MinimapHud.inspecting()) return;
        draw(g, mc, width, Math.max(TOP, boss + 2));
    };

    /** 1 in the middle, easing to 0 over the outer quarter of each half. */
    static float fade(float dx, float half) {
        float t = Mth.clamp((1 - Math.abs(dx) / half) / 0.25F, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static void draw(GuiGraphics g, Minecraft mc, int width, float top) {
        float w = Mth.clamp(Math.round(width * 0.34F), 160, 300), half = w / 2, cx = width / 2F, x0 = cx - half;
        float heading = Mth.positiveModulo(mc.gameRenderer.getMainCamera().getYRot() + 180, 360);
        float perDeg = w / SPAN, bottom = top + BAND;

        // the band and the ticks: one batch of quads with their own alpha
        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int slices = 40;
        for (int i = 0; i < slices; i++) {
            float sx0 = x0 + w * i / slices, sx1 = x0 + w * (i + 1) / slices;
            float a0 = 0.5F * fade(sx0 - cx, half), a1 = 0.5F * fade(sx1 - cx, half);
            quad(b, m, sx0, top, sx1, bottom, AflUiStyle.BACK, a0, a1);
        }
        int first = (int) Math.ceil((heading - SPAN / 2) / 5) * 5;
        for (int deg = first; deg <= heading + SPAN / 2; deg += 5) {
            int hd = Math.floorMod(deg, 360);
            if (hd % 45 == 0) continue;
            float sx = cx + (deg - heading) * perDeg, a = fade(sx - cx, half);
            if (a < 0.04F) continue;
            float len = hd % 15 == 0 ? 3.5F : 2F;
            quad(b, m, sx - 0.375F, bottom - len, sx + 0.375F, bottom, AflUiStyle.TEXT, a * (hd % 15 == 0 ? 0.9F : 0.55F), a * (hd % 15 == 0 ? 0.9F : 0.55F));
        }
        BufferUploader.drawWithShader(b.end());

        // the labels
        Font font = mc.font;
        for (int deg = first; deg <= heading + SPAN / 2; deg += 5) {
            int hd = Math.floorMod(deg, 360);
            if (hd % 15 != 0) continue;
            float sx = cx + (deg - heading) * perDeg, a = fade(sx - cx, half);
            if (a < 0.05F) continue;
            if (hd % 45 == 0) {
                boolean cardinal = hd % 90 == 0;
                float scale = cardinal ? TEXT_CARDINAL : TEXT_HALF;
                text(g, font, POINTS[hd / 45], sx, bottom - 1.5F - font.lineHeight * scale, scale, hd == 0 ? AflUiStyle.ACCENT : AflUiStyle.TEXT, a);
            } else {
                text(g, font, Integer.toString(hd), sx, bottom - 4.5F - font.lineHeight * TEXT_NUMBER, TEXT_NUMBER, AflUiStyle.TEXT_DIM, a);
            }
        }
        g.flush();

        // the mark under the middle and the heading
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        vertex(b, m, cx, bottom - 2.5F, AflUiStyle.ACCENT, 1);
        vertex(b, m, cx - 3, bottom + 1.5F, AflUiStyle.ACCENT, 1);
        vertex(b, m, cx + 3, bottom + 1.5F, AflUiStyle.ACCENT, 1);
        BufferUploader.drawWithShader(b.end());
        String deg = Integer.toString(Math.round(heading) % 360);
        float bw = font.width("000") * TEXT_HEADING + 7, bh = font.lineHeight * TEXT_HEADING + 3, by = bottom + 3;
        AflUiShapes.box(g, cx - bw / 2, by, bw, bh, AflUiStyle.RADIUS_KEY, AflUiStyle.BACK, AflUiStyle.BACK_ALPHA, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA, 0, 0);
        text(g, font, deg, cx, by + 1.7F, TEXT_HEADING, AflUiStyle.TEXT, 1);
    }

    private static void quad(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, int rgb, float a0, float a1) {
        vertex(b, m, x0, y0, rgb, a0);
        vertex(b, m, x0, y1, rgb, a0);
        vertex(b, m, x1, y1, rgb, a1);
        vertex(b, m, x1, y0, rgb, a1);
    }

    private static void vertex(BufferBuilder b, Matrix4f m, float x, float y, int rgb, float a) {
        b.vertex(m, x, y, 0).color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, Math.round(Mth.clamp(a, 0, 1) * 255)).endVertex();
    }

    /** Text centred on x, its top at y. */
    private static void text(GuiGraphics g, Font font, String s, float x, float y, float scale, int rgb, float a) {
        g.pose().pushPose();
        g.pose().translate(x - font.width(s) * scale / 2, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, s, 0, 0, AflUiStyle.argb(rgb, a), false);
        g.pose().popPose();
    }
}
