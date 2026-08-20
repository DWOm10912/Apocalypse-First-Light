package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.radiation.RadiationZone;
import net.minecraft.client.Minecraft;

public final class ClientGeigerData {
    private static double currentRate;
    private static double cumulativeDose;
    private static RadiationZone zone = RadiationZone.SAFE;
    private static long lastUpdateTick = Long.MIN_VALUE;

    private ClientGeigerData() {}

    public static void update(double rate, double dose, RadiationZone newZone) {
        currentRate = Math.max(0.0, rate);
        cumulativeDose = Math.max(0.0, dose);
        zone = newZone;
        if (Minecraft.getInstance().level != null) lastUpdateTick = Minecraft.getInstance().level.getGameTime();
    }

    public static Snapshot snapshot() {
        if (Minecraft.getInstance().level == null
                || Minecraft.getInstance().level.getGameTime() - lastUpdateTick > 40) {
            return new Snapshot(0.0, 0.0, RadiationZone.SAFE, true);
        }
        return new Snapshot(currentRate, cumulativeDose, zone, false);
    }

    public record Snapshot(double currentRate, double cumulativeDose, RadiationZone zone, boolean stale) {}
}
