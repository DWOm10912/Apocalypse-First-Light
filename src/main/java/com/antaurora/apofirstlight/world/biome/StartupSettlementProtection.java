package com.antaurora.apofirstlight.world.biome;

/** Settlement-only protection split; this does not alter Startup ecology zones. */
public final class StartupSettlementProtection {
    public static final int STARTUP_SETTLEMENT_FALLOUT_PROTECTION_DEPTH = 96;

    public enum ProtectionClass {
        PLAINS_CORE,
        PLAINS_FRINGE,
        INNER_FALLOUT_PROTECTION,
        NONE
    }

    private StartupSettlementProtection() {
    }

    public static ProtectionClass protectionAt(int x, int z, long seed) {
        StartupPlainsEnclave.Zone zone = StartupPlainsEnclave.zoneAt(x, z, seed);
        if (zone == StartupPlainsEnclave.Zone.CORE_PLAINS) return ProtectionClass.PLAINS_CORE;
        if (zone == StartupPlainsEnclave.Zone.FRINGE_PLAINS) return ProtectionClass.PLAINS_FRINGE;
        if (zone == StartupPlainsEnclave.Zone.FALLOUT_BUFFER
                && distanceFromCenter(x, z) <= settlementProtectionBoundary(x, z, seed)) {
            return ProtectionClass.INNER_FALLOUT_PROTECTION;
        }
        return ProtectionClass.NONE;
    }

    public static boolean isProtected(int x, int z, long seed) {
        return protectionAt(x, z, seed) != ProtectionClass.NONE;
    }

    public static int settlementProtectionBoundary(int x, int z, long seed) {
        return Math.min(StartupPlainsEnclave.falloutOuterBoundary(x, z, seed),
                StartupPlainsEnclave.plainsBoundary(x, z, seed)
                        + STARTUP_SETTLEMENT_FALLOUT_PROTECTION_DEPTH);
    }

    public static int eligibleFalloutWidth(int x, int z, long seed) {
        return Math.max(0, StartupPlainsEnclave.falloutOuterBoundary(x, z, seed)
                - settlementProtectionBoundary(x, z, seed));
    }

    public static double distanceFromCenter(int x, int z) {
        return Math.sqrt((double) x * x + (double) z * z);
    }
}
