package com.antaurora.apofirstlight.radiation;

public record RadiationSample(
        double baseField,
        RadiationZone zone,
        double worldAmbientRadiation,
        double localRadiation,
        double finalRadiation,
        boolean spawnSafeCore,
        double spawnSuppression,
        long safeChunkX,
        long safeChunkZ
) {
    public static RadiationSample safe(long safeChunkX, long safeChunkZ) {
        return new RadiationSample(0.0, RadiationZone.SAFE, 0.0, 0.0, 0.0,
                false, 1.0, safeChunkX, safeChunkZ);
    }
}
