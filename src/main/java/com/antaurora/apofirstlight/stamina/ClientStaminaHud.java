package com.antaurora.apofirstlight.stamina;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Temporary Stamina V1 HUD until the survival HUD is designed (耐力.md §12): a ring left of the load bar (the vanilla
 * experience bar's place, weight/ClientWeightHud), filling clockwise from the top with the stamina, a running figure in
 * it. Muted light while fine, amber below 30, red while winded. It fades in when stamina drops and out 2 s after it is
 * full again; the fill eases like the load bar. Drawn in real pixels with anti-aliased edges (distance fields), in the
 * same style as the load bar. Survival / Adventure only.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientStaminaHud {
    /** Ring size in GUI pixels: outer radius, band width; centre left of the load bar, clear of the off-hand slot below. */
    private static final double RADIUS = 5.5, BAND = 1.5;
    private static final int LOW = 30;
    private static final double EASE_SECONDS = 0.12, FADE_SECONDS = 0.25, HOLD_FULL_SECONDS = 2.0;
    private static double shown = Double.NaN, alpha;
    private static long lastNanos, fullSince = Long.MIN_VALUE;
    private ClientStaminaHud() {}

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "stamina_ring", OVERLAY);
    }

    /** Running figure: capsule strokes in a unit box (y down): head, torso, arms, legs. */
    private static final double[][] RUNNER = {
            {0.56, 0.30, 0.46, 0.58},
            {0.55, 0.34, 0.70, 0.47}, {0.70, 0.47, 0.84, 0.38},
            {0.55, 0.34, 0.40, 0.41}, {0.40, 0.41, 0.30, 0.53},
            {0.46, 0.58, 0.63, 0.71}, {0.63, 0.71, 0.58, 0.92},
            {0.46, 0.58, 0.36, 0.76}, {0.36, 0.76, 0.17, 0.80}};
    private static final double HEAD_X = 0.62, HEAD_Y = 0.15, HEAD_R = 0.11, STROKE = 0.075;

    static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        var mc = Minecraft.getInstance();
        var state = ClientStamina.state();
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (state == null || state.max() <= 0 || mc.player == null) { shown = Double.NaN; alpha = 0; return; }
        if (mc.options.hideGui || !gui.shouldDrawSurvivalElements() || FieldAttachmentViewState.isActive()) return;
        double fraction = Math.max(0, Math.min(1, state.value() / state.max()));
        shown = Double.isNaN(shown) ? fraction : shown + (fraction - shown) * (1 - Math.exp(-dt / EASE_SECONDS));
        boolean full = fraction >= 0.999 && !state.winded();
        if (!full) fullSince = Long.MIN_VALUE; else if (fullSince == Long.MIN_VALUE) fullSince = now;
        boolean visible = !full || (now - fullSince) / 1e9 < HOLD_FULL_SECONDS;
        alpha += ((visible ? 1 : 0) - alpha) * (1 - Math.exp(-dt / FADE_SECONDS));
        if (alpha < 0.01) return;

        int colour = state.winded() ? 0xBE443A : state.value() < LOW ? 0xD6983A : 0xC8C6BE;
        int s = (int)Math.max(1, Math.round(mc.getWindow().getGuiScale()));
        double cx = (screenWidth / 2.0 - 91 - 9) * s, cy = (screenHeight - 29.5) * s;
        double outer = RADIUS * s, inner = (RADIUS - BAND) * s, sweep = shown * Math.PI * 2;
        double glyph = inner * 2 * 0.8, gx = cx - glyph / 2, gy = cy - glyph / 2;
        double a = alpha;

        graphics.pose().pushPose();
        graphics.pose().scale(1.0f / s, 1.0f / s, 1.0f);
        graphics.drawManaged(() -> {
            int x0 = (int)Math.floor(cx - outer - 1), x1 = (int)Math.ceil(cx + outer + 1);
            int y0 = (int)Math.floor(cy - outer - 1), y1 = (int)Math.ceil(cy + outer + 1);
            for (int py = y0; py < y1; py++) for (int px = x0; px < x1; px++) {
                double x = px + 0.5 - cx, y = py + 0.5 - cy, r = Math.hypot(x, y);
                double ring = clamp(0.5 - (Math.abs(r - (outer + inner) / 2) - (outer - inner) / 2));
                if (ring > 0) {
                    fill(graphics, px, py, 0x10101010, 0x90 / 255.0 * ring * a);
                    double fill = sweep >= Math.PI * 2 - 1e-3 ? 1 : sweep <= 0 ? 0 : arc(Math.atan2(x, -y), sweep, r);
                    if (fill > 0) fill(graphics, px, py, colour, ring * fill * a);
                } else if (r < inner) {
                    double d = runner((px + 0.5 - gx) / glyph, (py + 0.5 - gy) / glyph) * glyph;
                    double cover = clamp(0.5 - d);
                    if (cover > 0) fill(graphics, px, py, colour, cover * a);
                }
            }
        });
        graphics.pose().popPose();
    };

    /** Coverage of a pixel by the clockwise arc from the top over `sweep` (anti-aliased at both ends, in pixels at radius r). */
    private static double arc(double angle, double sweep, double r) {
        double tau = Math.PI * 2, theta = angle < 0 ? angle + tau : angle; // 0..2π clockwise from the top
        // signed angular distance into the interval [0, sweep] (negative outside, nearest end), times the radius
        double inside = theta <= sweep ? Math.min(theta, sweep - theta) : -Math.min(theta - sweep, tau - theta);
        return clamp(0.5 + inside * r);
    }

    /** Signed distance (unit box) to the running figure. */
    private static double runner(double x, double y) {
        double d = Math.hypot(x - HEAD_X, y - HEAD_Y) - HEAD_R;
        for (var seg : RUNNER) d = Math.min(d, segment(x, y, seg[0], seg[1], seg[2], seg[3]) - STROKE);
        return d;
    }
    private static double segment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(px - ax - t * dx, py - ay - t * dy);
    }
    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    private static void fill(GuiGraphics graphics, int x, int y, int rgb, double alpha) {
        int a = (int)Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        if (a > 0) graphics.fill(x, y, x + 1, y + 1, a << 24 | (rgb & 0xFFFFFF));
    }
}
