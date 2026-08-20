package com.antaurora.apofirstlight.radiation;

public record RadiationSample(
        double rawWorldField,
        double baseField,
        RadiationZone zone,
        double worldAmbientRadiation,
        double localRadiation,
        double finalRadiation,
        double shelterTransmission,
        int shieldingRaysHit,
        int shieldingBlocksCounted,
        boolean spawnSafeCore,
        double spawnSuppression,
        int safeAnchorX,
        int safeAnchorZ,
        String safeAnchorSource
) {
    public static RadiationSample safe(long safeChunkX, long safeChunkZ) {
        return new RadiationSample(0.0, 0.0, RadiationZone.SAFE, 0.0, 0.0, 0.0, 1.0, 0, 0,
                false, 1.0, (int) (safeChunkX * 16L + 8L), (int) (safeChunkZ * 16L + 8L), "FALLBACK");
    }
}
