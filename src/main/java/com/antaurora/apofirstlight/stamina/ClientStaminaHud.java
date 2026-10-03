package com.antaurora.apofirstlight.stamina;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.AflRingIcon;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Temporary Stamina V1 HUD until the survival HUD is designed (耐力.md §12): a ring left of the thirst ring, which sits
 * left of the load bar (the vanilla experience bar's place), filling clockwise from the top with the stamina, a running
 * figure in it (client/AflRingIcon). Muted light while fine, amber below 30, red while winded. It fades in when stamina
 * drops and out 2 s after it is full again; the fill eases like the load bar. Survival / Adventure only.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientStaminaHud {
    /** Ring in GUI pixels: outer radius, band width; centre 22 left of the load bar, clear of the off-hand slot below. */
    private static final double RADIUS = 5.5, BAND = 1.5, OFFSET_X = 22;
    private static final int LOW = 30;
    private static final double EASE_SECONDS = 0.12, FADE_SECONDS = 0.25, HOLD_FULL_SECONDS = 2.0;
    private static double shown = Double.NaN, alpha;
    private static long lastNanos, fullSince = Long.MIN_VALUE;
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
        AflRingIcon.draw(graphics, screenWidth / 2.0 - 91 - OFFSET_X, screenHeight - 29.5, RADIUS, BAND, shown, colour, alpha,
                ClientStaminaHud::runner);
    };
}
