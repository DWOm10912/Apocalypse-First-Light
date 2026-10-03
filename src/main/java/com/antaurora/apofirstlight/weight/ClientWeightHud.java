package com.antaurora.apofirstlight.weight;

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
import java.util.Locale;

/**
 * The load bar in the vanilla experience bar's place (AFL hides the experience bar, AflHudEvents): 182 × 5 above the
 * hotbar, a rounded track with a rounded fill, full at the severe ratio (60 kg), ticks at the comfort and the tier
 * thresholds (30 / 40 / 50 kg), filled in the tier's colour, the load in kg at its right end. The fill, the number and
 * the colour follow the load smoothly. Drawn in real pixels (anti-aliased ends), not GUI pixels.
 * Survival / Adventure only, like the other survival bars; hidden while a jumping mount's bar takes the spot and in the
 * field attachment view.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientWeightHud {
    private static final int WIDTH = 182, HEIGHT = 5;
    /** Time constant of the bar's ease toward the load (s). */
    private static final double EASE_SECONDS = 0.12;
    private static double shownRatio = Double.NaN;
    private static long lastNanos;
    private ClientWeightHud() {}

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "weight_bar", OVERLAY);
    }

    /** Muted fills per tier (no bright white): grey, yellow, amber, orange, red. */
    private static int colour(EncumbranceState.Tier tier) {
        return switch (tier) {
            case LIGHT -> 0xFFB4B2A8;
            case BURDENED -> 0xFFD6BE5E;
            case HEAVY -> 0xFFD6983A;
            case HAULING -> 0xFFCE6A2C;
            case EXTREME -> 0xFFBE443A;
        };
    }

    /** The tier at a load ratio, as EncumbranceState decides it, so the colour changes as the eased fill crosses a tick. */
    private static EncumbranceState.Tier tier(double ratio, double onset, double severe) {
        if (ratio <= onset) return EncumbranceState.Tier.LIGHT;
        double severity = (ratio - onset) / (severe - onset);
        return severity < 1 / 3.0 ? EncumbranceState.Tier.BURDENED : severity < 2 / 3.0 ? EncumbranceState.Tier.HEAVY
                : severity < 1 ? EncumbranceState.Tier.HAULING : EncumbranceState.Tier.EXTREME;
    }

    /** Exponential ease toward the load ratio, frame-rate independent; the first frame starts at the load. */
    private static double ease(double target) {
        long now = System.nanoTime();
        if (Double.isNaN(shownRatio)) shownRatio = target;
        else {
            double dt = Math.min(0.25, (now - lastNanos) / 1e9);
            shownRatio += (target - shownRatio) * (1 - Math.exp(-dt / EASE_SECONDS));
            if (Math.abs(target - shownRatio) < 1e-4) shownRatio = target;
        }
        lastNanos = now;
        return shownRatio;
    }

    static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var state = ClientWeightState.state();
        if (state == null || !state.penaltiesEnabled() || state.comfortCapacityGrams() <= 0) {
            shownRatio = Double.NaN;
            return;
        }
        if (player == null || mc.options.hideGui || !gui.shouldDrawSurvivalElements() || player.jumpableVehicle() != null
                || FieldAttachmentViewState.isActive()) return;
        var policy = ClientWeightState.policy();
        double onset = policy == null ? 1.0 : policy.policy().onset(), severe = policy == null ? 2.0 : policy.policy().severe();
        double fullRatio = Math.max(severe, 0.01);
        double ratio = ease(state.encumbranceRatio());
        int colour = colour(tier(ratio, onset, severe));

        int guiX = screenWidth / 2 - WIDTH / 2, guiY = screenHeight - 29; // vanilla experience bar
        double scale = mc.getWindow().getGuiScale();
        int s = (int)Math.max(1, Math.round(scale));
        int x = guiX * s, y = guiY * s, w = WIDTH * s, h = HEIGHT * s;
        int inset = Math.max(1, Math.round(s * 0.5f)), tickWidth = inset;
        double innerW = w - 2 * inset, fillW = Math.min(1.0, ratio / fullRatio) * innerW;
        int innerH = h - 2 * inset;

        graphics.pose().pushPose();
        graphics.pose().scale(1.0f / s, 1.0f / s, 1.0f);
        graphics.drawManaged(() -> {
            roundedRect(graphics, x, y, w, h, h / 2.0, 0x90101010);
            roundedRect(graphics, x + inset, y + inset, fillW, innerH, innerH / 2.0, colour);
            for (int i = 0; i < 3; i++) {
                double tickRatio = onset + (severe - onset) * i / 3.0;
                if (tickRatio <= 0 || tickRatio >= fullRatio) continue;
                int tick = x + inset + (int)Math.round(tickRatio / fullRatio * innerW) - tickWidth / 2;
                // dark over the fill, light over the empty track
                graphics.fill(tick, y + inset, tick + tickWidth, y + h - inset,
                        tick + tickWidth / 2.0 < x + inset + fillW ? 0xB0000000 : 0x90D8D8D0);
            }
        });
        graphics.pose().popPose();

        String text = String.format(Locale.ROOT, "%.1f kg", ratio * state.comfortCapacityGrams() / 1000.0);
        graphics.drawString(mc.font, text, guiX + WIDTH + 4, guiY - 2, colour, true);
    };

    /**
     * Anti-aliased rounded rectangle in the current (real-pixel) space: x and width may be fractional (the eased fill).
     * Fully covered middle columns are one quad; the rounded ends are per-pixel coverage from the rounded-box distance.
     */
    private static void roundedRect(GuiGraphics graphics, double x0, int y0, double width, int height, double radius, int argb) {
        if (width <= 0 || height <= 0) return;
        double x1 = x0 + width, r = Math.min(radius, Math.min(width, height) / 2.0);
        double cx = (x0 + x1) / 2, cy = y0 + height / 2.0, bx = width / 2 - r, by = height / 2.0 - r;
        int left = (int)Math.floor(x0), right = (int)Math.ceil(x1);
        int midLeft = (int)Math.ceil(x0 + r), midRight = (int)Math.floor(x1 - r);
        boolean middle = midLeft < midRight;
        if (middle) graphics.fill(midLeft, y0, midRight, y0 + height, argb);
        int alpha = argb >>> 24, rgb = argb & 0xFFFFFF;
        for (int px = left; px < right; px++) {
            if (middle && px >= midLeft && px < midRight) continue;
            for (int py = y0; py < y0 + height; py++) {
                double qx = Math.abs(px + 0.5 - cx) - bx, qy = Math.abs(py + 0.5 - cy) - by;
                double d = Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
                double coverage = Math.max(0, Math.min(1, 0.5 - d));
                if (coverage > 0) graphics.fill(px, py, px + 1, py + 1, (int)Math.round(alpha * coverage) << 24 | rgb);
            }
        }
    }
}
