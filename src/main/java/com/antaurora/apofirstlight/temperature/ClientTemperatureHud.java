package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.AflHudText;
import com.antaurora.apofirstlight.client.SurvivalHudLayout;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Locale;

/**
 * Temperature HUD V1: the core-temperature dial (TemperatureDial), the centre of the survival cluster
 * (client/SurvivalHudLayout) between the stamina ring (left) and the thirst ring (right), scaled with the cluster.
 * It shows the player's core body temperature, not the air. The server sends how cold / hot / extreme the core is and
 * where it is heading and its target (its own numbers); the dial eases toward them so every change is a soft blend, and the trend
 * chevrons keep flowing at a speed that follows the trend. Numbers only with a thermometer (TemperatureReadout): the
 * air temperature above the dial's arc while a wrist thermometer is worn, the core reading inside the dial for 10 s after
 * a clinical thermometer measurement; numbers and the unit only, no words. Survival / Adventure only.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientTemperatureHud {
    /** Time constant of the ease toward the server's state (s): slow enough to read as a soft transition. */
    private static final double EASE_SECONDS = 0.8;
    /** The target frame moves quickly but never jumps (s). */
    private static final double TARGET_EASE_SECONDS = 0.2;
    /** Chevron speed in cycles per second: base + per unit of trend. */
    private static final double FLOW_BASE = 0.25, FLOW_PER_TREND = 0.35;
    /** The air temperature marker follows the once-a-second value smoothly (s). */
    private static final double AMBIENT_EASE_SECONDS = 0.3;
    /** Number colours: cold, normal, hot. */
    private static final int TEXT_COLD = 0xA9D8F2, TEXT_NORMAL = 0xE6E4DC, TEXT_HOT = 0xF0A060;
    private static double shownCold = Double.NaN, shownHeat, shownDanger, shownTrend, shownTarget, shownAmbient = Double.NaN, phase;
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
        boolean ambientShown = TemperatureReadout.ambientShown(mc.player);
        if (!ambientShown) shownAmbient = Double.NaN;
        else if (Double.isNaN(shownAmbient)) shownAmbient = state.ambient();
        else shownAmbient += (state.ambient() - shownAmbient) * (1 - Math.exp(-dt / AMBIENT_EASE_SECONDS));
        double core = TemperatureReadout.coreShown(mc.player, state);
        boolean measuring = TemperatureReadout.measuring(mc.player);
        TemperatureDial.render(graphics, cx, cy, shownCold, shownHeat, shownDanger, shownTrend, shownTarget,
                ambientShown ? shownAmbient : Double.NaN, measuring || !Double.isNaN(core), phase, now / 1e9);
        // numbers only, half-size vanilla font: the air temperature above its arc, the core reading inside the dial
        if (ambientShown) {
            double air = state.ambient();
            AflHudText.half(graphics, mc.font, Math.round(air) + "°C", cx, cy - (TemperatureDial.AMBIENT_BASE + 1.0) * SurvivalHudLayout.SCALE - 4.0,
                    air < 2 ? TEXT_COLD : air > 28 ? TEXT_HOT : TEXT_NORMAL);
        }
        if (measuring) {   // the reading is on its way: a dim blinking placeholder
            double blink = 0.55 + 0.45 * Math.sin(now / 1e9 * Math.PI * 2);
            int grey = (int)Math.round(0x9A * blink + 0x40 * (1 - blink));
            AflHudText.half(graphics, mc.font, "--.-°", cx, cy - 1.75, grey << 16 | grey << 8 | grey);
        } else if (!Double.isNaN(core))
            AflHudText.half(graphics, mc.font, String.format(Locale.ROOT, "%.1f°", core), cx, cy - 1.75,
                    core < 36.0 ? TEXT_COLD : core > 38.0 ? TEXT_HOT : TEXT_NORMAL);
    };
}
