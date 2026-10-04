package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.SurvivalHudLayout;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Locale;

/**
 * Temperature HUD V1: the core-temperature dial (TemperatureDial), the centre of the survival cluster
 * (client/SurvivalHudLayout) above the hearts and food, between the stamina ring (left) and the thirst ring (right).
 * It shows the player's core body temperature, not the air. The server sends how cold / hot / extreme the core is and
 * where it is heading and its target (its own numbers); the dial eases toward them so every change is a soft blend, and the trend
 * chevrons keep flowing at a speed that follows the trend. No numbers by default: TemperatureReadout shows them only
 * when a source (a future thermometer / survival monitor) allows. Survival / Adventure only.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientTemperatureHud {
    /** Time constant of the ease toward the server's state (s): slow enough to read as a soft transition. */
    private static final double EASE_SECONDS = 0.8;
    /** The target frame moves quickly but never jumps (s). */
    private static final double TARGET_EASE_SECONDS = 0.2;
    /** Chevron speed in cycles per second: base + per unit of trend. */
    private static final double FLOW_BASE = 0.25, FLOW_PER_TREND = 0.35;
    private static double shownCold = Double.NaN, shownHeat, shownDanger, shownTrend, shownTarget, phase;
    private static long lastNanos;
    private ClientTemperatureHud() {}

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "temperature_dial", OVERLAY);
    }

    static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        var mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (now - lastNanos) / 1e9);
        lastNanos = now;
        var state = ClientTemperature.state();
        if (state == null || mc.player == null) { shownCold = Double.NaN; return; }
        if (mc.options.hideGui || !gui.shouldDrawSurvivalElements() || FieldAttachmentViewState.isActive()) return;
        if (Double.isNaN(shownCold)) {
            shownCold = ClientTemperature.coldness(); shownHeat = ClientTemperature.heat();
            shownDanger = ClientTemperature.danger(); shownTrend = ClientTemperature.trend(); shownTarget = ClientTemperature.target();
        } else {
            double k = 1 - Math.exp(-dt / EASE_SECONDS);
            shownCold += (ClientTemperature.coldness() - shownCold) * k;
            shownHeat += (ClientTemperature.heat() - shownHeat) * k;
            shownDanger += (ClientTemperature.danger() - shownDanger) * k;
            shownTrend += (ClientTemperature.trend() - shownTrend) * k;
            shownTarget += (ClientTemperature.target() - shownTarget) * (1 - Math.exp(-dt / TARGET_EASE_SECONDS));
        }
        phase = (phase + dt * (FLOW_BASE + FLOW_PER_TREND * Math.abs(shownTrend))) % 1;
        double cx = screenWidth / 2.0, cy = screenHeight - SurvivalHudLayout.CENTRE_Y;
        TemperatureDial.render(graphics, cx, cy, shownCold, shownHeat, shownDanger, shownTrend, shownTarget, phase, now / 1e9);
        TemperatureReadout.render(graphics, mc.font, cx, cy - TemperatureDial.RADIUS - TemperatureDial.HALO, state);
    };

    /** "36.8°C" style text for the readout. */
    static String celsius(double value) { return String.format(Locale.ROOT, "%.1f°C", value); }
}
