package com.antaurora.apofirstlight.worldgen.rural;

/**
 * Explicit layout presets retained for inactive road and agricultural algorithms.
 * No weighted natural-candidate selection or old building-composition recipe remains.
 */
public enum RuralScaleTier {
    ISOLATED_HOMESTEAD(18),
    FARMSTEAD(32),
    RURAL_CLUSTER(56),
    FULL_RURAL(80);

    private final int roadLength;

    RuralScaleTier(int roadLength) {
        this.roadLength = roadLength;
    }

    public int roadLength() { return roadLength; }
}
