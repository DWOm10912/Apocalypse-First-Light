package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.radiation.RadiationZone;
import net.minecraft.client.Minecraft;

public final class ClientGeigerData {
    public static final double GEIGER_MAX_RATE = 20.0D;

    private static double measuredRate;
    private static double cumulativeDose;
    private static double residualRadiationRate;
    private static RadiationZone zone = RadiationZone.SAFE;
    private static long lastUpdateTick = Long.MIN_VALUE;

    private ClientGeigerData() {}

    public static void update(double rate, double dose, double residual, RadiationZone newZone) {
        measuredRate = Math.max(0.0, rate);
        cumulativeDose = Math.max(0.0, dose);
        residualRadiationRate = Math.max(0.0, residual);
        zone = newZone;
        if (Minecraft.getInstance().level != null) lastUpdateTick = Minecraft.getInstance().level.getGameTime();
    }

    public static Snapshot snapshot() {
        if (Minecraft.getInstance().level == null
                || Minecraft.getInstance().level.getGameTime() - lastUpdateTick > 40) {
            return new Snapshot(0.0, 0.0, 0.0, RadiationZone.SAFE, true);
        }
        return new Snapshot(measuredRate, cumulativeDose, residualRadiationRate, zone, false);
    }

    public record Snapshot(double measuredRate, double cumulativeDose, double residualRadiationRate,
                           RadiationZone zone, boolean stale) {}
}
