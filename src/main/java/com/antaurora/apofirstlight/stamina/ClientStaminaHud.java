package com.antaurora.apofirstlight.stamina;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.AflRingIcon;
import com.antaurora.apofirstlight.client.SurvivalHudLayout;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Stamina V1 HUD (耐力.md §12): a crescent "(" at the left end in the survival cluster (client/SurvivalHudLayout),
 * filling clockwise from the top with the stamina, a running figure in it (client/AflGauge). Muted light while fine, amber below 30,
 * red while winded. Always shown (Survival HUD V1, 2026-10-03: it used to fade out when full); the fill eases like the load
 * bar. The air lane round it is SurvivalVitalsHud. Survival / Adventure only.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientStaminaHud {
    private static final int LOW = 30;
    private static final double EASE_SECONDS = 0.12;
    private static double shown = Double.NaN;
    private static long lastNanos;
    private ClientStaminaHud() {}

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "stamina_ring", OVERLAY);
    }

    /** Running figure: capsule strokes in a unit box (y down): torso, arms, legs; and a round head. */
    private static final double[][] RUNNER = {
            {0.56, 0.30, 0.46, 0.58},
            {0.55, 0.34, 0.70, 0.47}, {0.70, 0.47, 0.84, 0.38},
            {0.55, 0.34, 0.40, 0.41}, {0.40, 0.41, 0.30, 0.53},
            {0.46, 0.58, 0.63, 0.71}, {0.63, 0.71, 0.58, 0.92},
            {0.46, 0.58, 0.36, 0.76}, {0.36, 0.76, 0.17, 0.80}};
    private static final AflRingIcon.Glyph RUNNER_GLYPH = ClientStaminaHud::runner;
    private static double runner(double x, double y) {
        double d = Math.hypot(x - 0.62, y - 0.15) - 0.11;
        for (var seg : RUNNER) d = Math.min(d, AflRingIcon.segment(x, y, seg[0], seg[1], seg[2], seg[3]) - 0.075);
        return d;
    }

    static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        var mc = Minecraft.getInstance();
        var state = ClientStamina.state();
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (state == null || state.max() <= 0 || mc.player == null) { shown = Double.NaN; return; }
        if (mc.options.hideGui || !gui.shouldDrawSurvivalElements() || FieldAttachmentViewState.isActive()) return;
        double fraction = Math.max(0, Math.min(1, state.value() / state.max()));
        shown = Double.isNaN(shown) ? fraction : shown + (fraction - shown) * (1 - Math.exp(-dt / EASE_SECONDS));
        int colour = state.winded() ? 0xBE443A : state.value() < LOW ? 0xD6983A : 0xC8C6BE;
        SurvivalHudLayout.crescent(graphics, "stamina", screenWidth / 2.0 - SurvivalHudLayout.CRESCENT_OFFSET_X,
                screenHeight - SurvivalHudLayout.CENTRE_Y, -1, shown, colour, RUNNER_GLYPH);
    };
}
