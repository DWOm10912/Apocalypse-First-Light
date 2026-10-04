package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Temperature screen effects V1: what the body feels, on the screen edges only (the centre and the crosshair stay
 * clear; the HUD is drawn on top). Driven by the core temperature (the server's coldness / heat / danger), never by
 * the air, so standing next to lava shows nothing until the body has actually heated up; eased (about 1 s in, 2 s out)
 * so frost recedes a little slower than it grows.
 * - Cold: a cold tint at the edges (multiplied toward ice blue), then vanilla-style pixel frost (TemperatureFrost)
 *   growing in from the corners and sides as the cold deepens, an icy glare (the heat glare's wash in pale ice blue) and
 *   frost glints (pixel pluses flashing on the shown flakes' bright cores); extremes pulse slowly. Shivering is a camera
 *   effect (ClientShiver).
 * - Heat: a warm tint at the edges, a sun-glare wash (screen-blended pale amber, no darkening, no red) and, when hot,
 *   heat haze: the screen edges ripple (a copy of the frame drawn through a displaced mesh); extremes pulse slowly.
 * Layers in separate passes, so each can be tuned on its own.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientTemperatureScreenEffects {
    private static final double EASE_IN = 1.0, EASE_OUT = 2.0, PULSE_PERIOD = 4.5;
    private static final float[] COLD_TINT = {0.72f, 0.86f, 1.0f}, HEAT_TINT = {1.0f, 0.90f, 0.74f},
            HEAT_GLARE = {1.0f, 0.76f, 0.48f}, ICE_GLARE = {0.72f, 0.86f, 1.0f};
    /** Frost glints: about this many at once at full frost, each lasting GLINT_MIN..GLINT_MAX seconds. */
    private static final double GLINTS = 5, GLINT_MIN = 0.45, GLINT_MAX = 0.7;
    private static final int[][] ARMS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private record Glint(int x, int y, double start, double life) {}
    private static final List<Glint> glints = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static double nextGlint = Double.NEGATIVE_INFINITY;
    private static int glintGrid;
    /** Heat haze: displacement at the edges in real pixels per 2000 px of screen width, and the mesh resolution. */
    private static final double HAZE_PIXELS = 4.0;
    private static final int HAZE_COLUMNS = 48;
    private static final ResourceLocation EDGE_MASK = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/temperature_edge_mask"),
            GLARE_MASK = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "dynamic/temperature_glare_mask");
    private static double cold, heat, danger;
    private static long lastNanos;
    private static boolean masksReady, hazeFailed;
    private static TextureTarget frameCopy;
    private ClientTemperatureScreenEffects() {}

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        // under the HUD and the crosshair, above vanilla's own frost (powder snow)
        event.registerAbove(VanillaGuiOverlay.FROSTBITE.id(), "temperature_screen", OVERLAY);
    }

    static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        var mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (now - lastNanos) / 1e9);
        lastNanos = now;
        boolean active = mc.player != null && ClientTemperature.state() != null && !mc.player.isSpectator();
        cold = ease(cold, active ? ClientTemperature.coldness() : 0, dt);
        heat = ease(heat, active ? ClientTemperature.heat() : 0, dt);
        danger = ease(danger, active ? ClientTemperature.danger() : 0, dt);
        if (cold < 0.003 && heat < 0.003) return;
        double seconds = now / 1e9, pulse = 0.5 + 0.5 * Math.sin(seconds * Math.PI * 2 / PULSE_PERIOD);
        float p = (float)(1 + 0.15 * danger * (pulse - 0.5) * 2);
        ensureMasks();
        graphics.flush();
        RenderSystem.enableBlend();
        if (cold > 0.003) {
            multiply(graphics, EDGE_MASK, COLD_TINT, (float)(0.65 * cold) * p, screenWidth, screenHeight);
            double frostLevel = clamp((cold - 0.3) / 0.7);
            int texel = frost(graphics, mc, frostLevel, p);
            glare(graphics, ICE_GLARE, (float)(0.45 * smooth(0.3, 1, cold)) * p, screenWidth, screenHeight);
            if (texel > 0) glints(graphics, mc, frostLevel, texel, Math.min(1, p), seconds);
        }
        if (heat > 0.003) {
            multiply(graphics, EDGE_MASK, HEAT_TINT, (float)(0.55 * smooth(0, 0.7, heat)), screenWidth, screenHeight);
            glare(graphics, HEAT_GLARE, (float)(0.55 * smooth(0.3, 1, heat)) * p, screenWidth, screenHeight);
            double amp = HAZE_PIXELS * smooth(0.35, 1, heat);
            if (amp > 0.05 && !hazeFailed) haze(graphics, mc, amp, seconds);
        }
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    };

    private static double ease(double shown, double target, double dt) {
        double tau = target > shown ? EASE_IN : EASE_OUT;
        return shown + (target - shown) * (1 - Math.exp(-dt / tau));
    }

    /** dst × (1 − mask × alpha × (1 − tint)): the edges pulled toward the tint, the centre untouched. */
    private static void multiply(GuiGraphics graphics, ResourceLocation mask, float[] tint, float alpha, int w, int h) {
        if (alpha <= 0.002f) return;
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        graphics.setColor(alpha * (1 - tint[0]), alpha * (1 - tint[1]), alpha * (1 - tint[2]), 1);
        graphics.blit(mask, 0, 0, w, h, 0, 0, MASK_W, MASK_H, MASK_W, MASK_H);
    }
    /** Screen blend toward a pale colour (amber for heat, ice blue for cold): brighter and paler at the edges, never darker. */
    private static void glare(GuiGraphics graphics, float[] rgb, float alpha, int w, int h) {
        if (alpha <= 0.002f) return;
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        graphics.setColor(rgb[0] * alpha, rgb[1] * alpha, rgb[2] * alpha, 1);
        graphics.blit(GLARE_MASK, 0, 0, w, h, 0, 0, MASK_W, MASK_H, MASK_W, MASK_H);
    }
    /** The pixel frost, about 5 real pixels per texel, drawn in real pixels; returns the texel size (0: nothing drawn). */
    private static int frost(GuiGraphics graphics, Minecraft mc, double intensity, float p) {
        if (intensity <= 0.003) return 0;
        var window = mc.getWindow();
        int pw = window.getWidth(), ph = window.getHeight(), texel = Math.max(2, (int)Math.round(pw / 400.0));
        int gw = (pw + texel - 1) / texel, gh = (ph + texel - 1) / texel;
        if (!TemperatureFrost.prepare(gw, gh, intensity)) return 0;
        float scale = (float)window.getGuiScale();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1, 1, 1, Math.min(1, p));
        graphics.pose().pushPose();
        graphics.pose().scale(1 / scale, 1 / scale, 1);
        graphics.blit(TemperatureFrost.TEXTURE, 0, 0, gw * texel, gh * texel, 0, 0, gw, gh, gw, gh);
        graphics.pose().popPose();
        return texel;
    }

    /**
     * Frost glints: pixel pluses on the frost's texel grid, each centred on a shown flake (white centre, arms over the
     * flake's bright core at 0.55, outer arms only near the peak), flashing in and out over 0.45..0.7 s; new ones arrive
     * at random (Poisson), about GLINTS at once at full frost, fewer as the frost thins.
     */
    private static void glints(GuiGraphics graphics, Minecraft mc, double intensity, int texel, float alpha, double seconds) {
        int grid = TemperatureFrost.width() << 16 | TemperatureFrost.height();
        if (grid != glintGrid) { glints.clear(); glintGrid = grid; }
        glints.removeIf(g -> seconds >= g.start + g.life);
        double rate = GLINTS * intensity / ((GLINT_MIN + GLINT_MAX) / 2);   // new glints per second
        if (nextGlint < seconds - 1) nextGlint = seconds;                     // no catch-up after a frost-free spell
        while (nextGlint <= seconds) {
            int spot = TemperatureFrost.glintSpot(RANDOM);
            if (spot >= 0) glints.add(new Glint(spot & 0xFFFF, spot >>> 16, nextGlint, GLINT_MIN + (GLINT_MAX - GLINT_MIN) * RANDOM.nextDouble()));
            nextGlint += -Math.log(1 - RANDOM.nextDouble()) / rate;
        }
        if (glints.isEmpty()) return;
        float scale = (float)mc.getWindow().getGuiScale();
        graphics.setColor(1, 1, 1, 1);
        graphics.pose().pushPose();
        graphics.pose().scale(1 / scale, 1 / scale, 1);
        for (var g : glints) {
            double a = Math.sin(Math.PI * clamp((seconds - g.start) / g.life)) * alpha;
            if (a <= 0.004) continue;
            glintCell(graphics, g.x, g.y, texel, a);
            for (int[] d : ARMS) glintCell(graphics, g.x + d[0], g.y + d[1], texel, 0.55 * a);
            if (a > 0.7) for (int[] d : ARMS) glintCell(graphics, g.x + 2 * d[0], g.y + 2 * d[1], texel, a - 0.7);
        }
        graphics.flush();
        graphics.pose().popPose();
        RenderSystem.enableBlend();   // the GUI render type turns blending off after its batch
    }
    private static void glintCell(GuiGraphics graphics, int x, int y, int texel, double alpha) {
        graphics.fill(x * texel, y * texel, (x + 1) * texel, (y + 1) * texel, (int)Math.round(alpha * 255) << 24 | 0xFFFFFF);
    }

    /** Heat haze: copy the frame, draw the edge cells of a grid back with rippling texture coordinates. */
    private static void haze(GuiGraphics graphics, Minecraft mc, double amp, double seconds) {
        try {
            var main = mc.getMainRenderTarget();
            int w = main.width, h = main.height;
            if (frameCopy == null || frameCopy.width != w || frameCopy.height != h) {
                if (frameCopy != null) frameCopy.destroyBuffers();
                frameCopy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
                frameCopy.setFilterMode(GL11.GL_LINEAR);
            }
            graphics.flush();
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, frameCopy.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            main.bindWrite(false);

            double s = 2000.0 / w, ampPx = amp / s;  // the wave is laid out on a 2000 px wide reference
            int cols = HAZE_COLUMNS, rows = Math.max(8, (int)Math.round(cols * h / (double)w));
            float[] u = new float[(cols + 1) * (rows + 1)], v = new float[u.length], m = new float[u.length];
            for (int j = 0; j <= rows; j++) for (int i = 0; i <= cols; i++) {
                double x = w * i / (double)cols, y = h * j / (double)rows, xs = x * s, ys = y * s;
                double e = Math.hypot((x - w / 2.0) / (w / 2.0), (y - h / 2.0) / (h / 2.0)), mask = smooth(0.55, 1.15, e);
                double ph = ys / 26 + seconds * 2.4 + TemperatureFrost.noise(xs / 70, ys / 120 + seconds * 0.5) * 6;
                double ox = Math.sin(ph) * ampPx * mask, oy = Math.cos(ph * 0.7 + xs / 45) * ampPx * 0.6 * mask;
                int k = j * (cols + 1) + i;
                u[k] = (float)((x + ox) / w); v[k] = (float)(1 - (y + oy) / h); m[k] = (float)mask;
            }
            var window = mc.getWindow();
            float scale = (float)window.getGuiScale(), sx = window.getWidth() / (float)w, sy = window.getHeight() / (float)h;
            graphics.pose().pushPose();
            graphics.pose().scale(1 / scale, 1 / scale, 1);
            var matrix = graphics.pose().last().pose();
            RenderSystem.disableBlend();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, frameCopy.getColorTextureId());
            RenderSystem.setShaderColor(1, 1, 1, 1);
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            for (int j = 0; j < rows; j++) for (int i = 0; i < cols; i++) {
                int a = j * (cols + 1) + i, b = a + 1, c = a + cols + 1, d = c + 1;
                if (m[a] <= 0 && m[b] <= 0 && m[c] <= 0 && m[d] <= 0) continue;  // the centre is never touched
                float x0 = w * i / (float)cols * sx, x1 = w * (i + 1) / (float)cols * sx, y0 = h * j / (float)rows * sy, y1 = h * (j + 1) / (float)rows * sy;
                buffer.vertex(matrix, x0, y1, 0).uv(u[c], v[c]).endVertex();
                buffer.vertex(matrix, x1, y1, 0).uv(u[d], v[d]).endVertex();
                buffer.vertex(matrix, x1, y0, 0).uv(u[b], v[b]).endVertex();
                buffer.vertex(matrix, x0, y0, 0).uv(u[a], v[a]).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
            graphics.pose().popPose();
            RenderSystem.enableBlend();
        } catch (RuntimeException ex) {
            hazeFailed = true; // keep the tint and glare, drop the ripple for this session
            ApocalypseFirstLight.LOGGER.warn("[AFL TEMPERATURE] Heat haze disabled: {}", ex.toString());
        }
    }

    // ---- edge masks (white = full effect), smooth, built once

    private static final int MASK_W = 320, MASK_H = 180;
    private static void ensureMasks() {
        if (masksReady) return;
        mask(EDGE_MASK, 0.45, 1.3, 1.3);
        mask(GLARE_MASK, 0.45, 1.25, 1.4);
        masksReady = true;
    }
    private static void mask(ResourceLocation id, double inner, double outer, double power) {
        var image = new NativeImage(NativeImage.Format.RGBA, MASK_W, MASK_H, false);
        for (int y = 0; y < MASK_H; y++) for (int x = 0; x < MASK_W; x++) {
            double e = Math.hypot((x + 0.5 - MASK_W / 2.0) / (MASK_W / 2.0), (y + 0.5 - MASK_H / 2.0) / (MASK_H / 2.0));
            int g = (int)Math.round(Math.pow(smooth(inner, outer, e), power) * 255);
            image.setPixelRGBA(x, y, 0xFF000000 | g << 16 | g << 8 | g);
        }
        var texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        texture.setFilter(true, false);
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    private static double smooth(double a, double b, double x) { double t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); }
}
