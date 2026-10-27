package com.antaurora.apofirstlight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Crosshair V1 (docs/ui/crosshair_v1.md, user 2026-10-26): vanilla's cross and its attack indicator are gone (the cooldown
 * comes back with the melee rework). In first person a faint dot is always there, brightening while what the player looks
 * at takes a click (a world prompt is up: {@link WorldInteractionHint#aimed}); a gun's hip-fire crosshair is its own
 * (weapon/client/NativeDynamicCrosshair: the same dot and four diagonal arms), the orbit camera's centre the same dot.
 * Drawn as smooth shapes in framebuffer pixels (user 2026-10-27: no pixel staircases): every edge fades over one pixel,
 * so a round dot and diagonal lines stay clean at every GUI scale and screen size.
 */
public final class AflCrosshair {
    public static final int COLOR = 0xE8E8E2, SHADE = 0x101010;
    /** The dot's opacity: idle, and while the thing looked at takes a click; the fade between them (seconds). */
    public static final float FAINT = 0.3F, AIMED = 0.9F;
    /** The dot's radius in {@link #unit}s; the dark ring round the dot and the arms, one unit wide. */
    public static final float DOT_RADIUS = 1.75F, RING = 1;
    private static final float FADE_SECONDS = 0.12F;
    private static float aim;
    private static long last = System.nanoTime();

    private AflCrosshair() {}

    /** Gui#renderCrosshair's replacement (mixin client/GuiCrosshairMixin; the F3 axes stay vanilla's). */
    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float step = Math.min(0.2F, (now - last) / 1e9F) / FADE_SECONDS;
        last = now;
        aim = Mth.clamp(aim + (WorldInteractionHint.aimed() ? step : -step), 0, 1);
        if (mc.player == null || mc.player.isSpectator() || !mc.options.getCameraType().isFirstPerson()) return;
        begin(g);
        try {
            dot(g, Mth.lerp(aim, FAINT, AIMED), COLOR);
        } finally {
            end(g);
        }
    }

    /** Framebuffer pixels from here to {@link #end}, the origin the screen's centre. */
    public static void begin(GuiGraphics g) {
        var w = Minecraft.getInstance().getWindow();
        float s = (float) (1 / w.getGuiScale());
        g.flush();
        g.pose().pushPose();
        g.pose().scale(s, s, 1);
        g.pose().translate(w.getWidth() / 2F, w.getHeight() / 2F, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
    }

    public static void end(GuiGraphics g) {
        g.pose().popPose();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** The crosshair's size unit in framebuffer pixels: 1 at 1080 lines, 2 at 2160 (a big screen gets the same look). */
    public static float unit() {
        return Math.max(0.75F, Minecraft.getInstance().getWindow().getHeight() / 1080F);
    }

    /** A screen pixel's size in GUI pixels' terms: the GUI scale (GUI-pixel lengths times this are framebuffer pixels). */
    public static double guiScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    /** The dot: a disc on the centre over a dark ring (it reads on the sky and on snow). */
    public static void dot(GuiGraphics g, float alpha, int rgb) {
        float u = unit(), r = DOT_RADIUS * u;
        capsule(g, 0, 0, 0, 0, r + RING * u, alpha * 0.45F, SHADE);
        capsule(g, 0, 0, 0, 0, r, alpha, rgb);
    }

    /**
     * Four diagonal arms (an X) round the centre, each a line with round ends from {@code from} to {@code from + len}
     * pixels out along its diagonal, {@code half} pixels half-wide, over a dark ring.
     */
    public static void arms(GuiGraphics g, float from, float len, float half, float alpha, int rgb) {
        float u = unit(), d = (float) Math.sqrt(0.5);
        // the ends are round: the spine stops half a width short of each end
        float a = from + half, b = Math.max(a, from + len - half);
        for (int pass = 0; pass < 2; pass++)
            for (int sx = -1; sx <= 1; sx += 2)
                for (int sy = -1; sy <= 1; sy += 2) {
                    float x = sx * d, y = sy * d;
                    if (pass == 0) capsule(g, x * a, y * a, x * b, y * b, half + RING * u, alpha * 0.35F, SHADE);
                    else capsule(g, x * a, y * a, x * b, y * b, half, alpha, rgb);
                }
    }

    /**
     * A line from (ax, ay) to (bx, by) with round ends, {@code half} pixels from the line to the edge (a disc when the ends
     * meet). Solid to half a pixel inside the edge, fading to nothing half a pixel outside: the edge's pixel coverage.
     */
    public static void capsule(GuiGraphics g, float ax, float ay, float bx, float by, float half, float alpha, int rgb) {
        half = Math.max(0.5F, half);
        float dx = bx - ax, dy = by - ay, l = Mth.sqrt(dx * dx + dy * dy);
        if (l < 1e-4F) {
            dx = 1;
            dy = 0;
        } else {
            dx /= l;
            dy /= l;
        }
        int n = Mth.clamp(Mth.ceil(half * 2) + 4, 6, 24), count = 2 * (n + 1);
        float[] ox = new float[count], oy = new float[count], cx = new float[count], cy = new float[count];
        for (int i = 0; i < count; i++) {
            // half a circle round b (from one side of the line to the other), then half a circle round a
            boolean end = i <= n;
            double t = Math.PI * ((end ? i : i - n - 1) / (double) n - 0.5);
            float c = (float) Math.cos(t), s = (float) Math.sin(t);
            float ux = end ? c * dx - s * dy : -c * dx + s * dy, uy = end ? c * dy + s * dx : -c * dy - s * dx;
            ox[i] = ux;
            oy[i] = uy;
            cx[i] = end ? bx : ax;
            cy[i] = end ? by : ay;
        }
        float r = (rgb >> 16 & 255) / 255F, gr = (rgb >> 8 & 255) / 255F, bl = (rgb & 255) / 255F, a = Mth.clamp(alpha, 0, 1);
        float in = half - 0.5F, out = half + 0.5F, mx = (ax + bx) / 2, my = (ay + by) / 2;
        Matrix4f m = g.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            float ix = cx[i] + ox[i] * in, iy = cy[i] + oy[i] * in, jx = cx[j] + ox[j] * in, jy = cy[j] + oy[j] * in;
            float px = cx[i] + ox[i] * out, py = cy[i] + oy[i] * out, qx = cx[j] + ox[j] * out, qy = cy[j] + oy[j] * out;
            // the solid inside (a fan from the middle), then the one-pixel fade round it
            buf.vertex(m, mx, my, 0).color(r, gr, bl, a).endVertex();
            buf.vertex(m, jx, jy, 0).color(r, gr, bl, a).endVertex();
            buf.vertex(m, ix, iy, 0).color(r, gr, bl, a).endVertex();
            buf.vertex(m, ix, iy, 0).color(r, gr, bl, a).endVertex();
            buf.vertex(m, qx, qy, 0).color(r, gr, bl, 0).endVertex();
            buf.vertex(m, px, py, 0).color(r, gr, bl, 0).endVertex();
            buf.vertex(m, ix, iy, 0).color(r, gr, bl, a).endVertex();
            buf.vertex(m, jx, jy, 0).color(r, gr, bl, a).endVertex();
            buf.vertex(m, qx, qy, 0).color(r, gr, bl, 0).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
    }
}
