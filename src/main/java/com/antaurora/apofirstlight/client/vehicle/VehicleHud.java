package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.AflGauge;
import com.antaurora.apofirstlight.client.AflHudText;
import com.antaurora.apofirstlight.client.AflRingIcon;
import com.antaurora.apofirstlight.client.SurvivalHudLayout;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Driving readings in the survival cluster (design doc section 4 "HUD", 8.5): right of the thirst crescent, the fuel ring
 * (the same ring as health and hunger, a pump in it) and beside it the speed (km/h) over the gear (R, N, 1..5; the gear
 * it is going to while shifting, dimmed) and 4H. Only in the driver's seat; the gauges themselves are on the dash.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VehicleHud {
    private static final int FUEL = 0xD8A94A, LOW = 0xD9583B, TEXT = 0xEDEAE4, DIM = 0x8A8780;
    /** Fuel pump: the body with its window cut out, the hose to the nozzle on the right. */
    private static final AflRingIcon.Glyph PUMP = (x, y) -> Math.min(
            Math.max(AflRingIcon.convex(x, y, 0.18, 0.16, 0.6, 0.16, 0.6, 0.86, 0.18, 0.86), -AflRingIcon.convex(x, y, 0.27, 0.26, 0.51, 0.26, 0.51, 0.42, 0.27, 0.42)),
            Math.min(AflRingIcon.segment(x, y, 0.6, 0.5, 0.78, 0.62) - 0.05, AflRingIcon.segment(x, y, 0.78, 0.62, 0.78, 0.3) - 0.05));

    private VehicleHud() {
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(), "vehicle_readings", OVERLAY);
    }

    static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof PickupTruckEntity truck) || truck.getControllingPassenger() != mc.player)
            return;
        draw(graphics, truck, partialTick, width / 2.0 + SurvivalHudLayout.CRESCENT_OFFSET_X + SurvivalHudLayout.CRESCENT_RADIUS + 3.5 + SurvivalHudLayout.VITAL_RADIUS,
                height - SurvivalHudLayout.CENTRE_Y);
    };

    private static void draw(GuiGraphics graphics, PickupTruckEntity truck, float pt, double x, double y) {
        // the tank as it is (the dash gauge creeps and needs the key on)
        double r = SurvivalHudLayout.VITAL_RADIUS, band = SurvivalHudLayout.VITAL_BAND;
        double fuel = truck.fuel() / (double) PickupTruckEntity.TANK;
        int colour = fuel < 0.12 ? LOW : FUEL;
        var bands = List.of(
                AflGauge.Band.disc(r - band, AflGauge.BACK, SurvivalHudLayout.FACE_ALPHA),
                AflGauge.Band.gauge(r, band, 10, SurvivalHudLayout.SEGMENT_GAP, fuel, colour, 1),
                AflGauge.Band.line(r + SurvivalHudLayout.OUTLINE_WIDTH, SurvivalHudLayout.OUTLINE_WIDTH, 0, 360, SurvivalHudLayout.OUTLINE, SurvivalHudLayout.OUTLINE_ALPHA));
        AflGauge.draw(graphics, "vehicle_fuel", x, y, r + SurvivalHudLayout.OUTLINE_WIDTH + 0.5, bands,
                new AflGauge.Glyph(PUMP, (r - band) * 2 * 0.63, colour, 1));
        // speed over gear, left aligned beside the ring
        var font = Minecraft.getInstance().font;
        String speed = String.valueOf(Math.round(truck.speedometer(pt)));
        double tx = x + r + 4;
        graphics.drawString(font, speed, (int) Math.round(tx), (int) Math.round(y - 8), 0xFF000000 | TEXT, true);
        AflHudText.half(graphics, font, "km/h", tx + font.width(speed) + 6, y - 5, DIM);
        int gear = truck.shiftTarget();
        boolean shifting = truck.shiftProgress() < 1;
        String g = gear < 0 ? "R" : gear == 0 ? "N" : String.valueOf(gear);
        AflHudText.half(graphics, font, g, tx + 2, y + 3, shifting ? DIM : TEXT);
        if (truck.fourWheel()) AflHudText.half(graphics, font, "4H", tx + 12, y + 3, TEXT);
    }
}
