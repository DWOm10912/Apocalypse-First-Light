package com.antaurora.apofirstlight.world.biome;

import net.minecraft.core.BlockPos;

/**
 * Deterministic, compact surface-biome pocket for the startup bunker.
 * Coordinates are evaluated in block space; MultiNoiseBiomeSource receives
 * quart coordinates and converts before calling this policy.
 */
public final class StartupPlainsEnclave {
    public static final int CENTER_X = 0;
    public static final int CENTER_Z = 0;
    public static final int CORE_RADIUS_BLOCKS = 192;
    public static final int OUTER_RADIUS_BLOCKS = 224;

    private static final long CORE_RADIUS_SQUARED = (long) CORE_RADIUS_BLOCKS * CORE_RADIUS_BLOCKS;

    private StartupPlainsEnclave() {
    }

    public static BlockPos referenceCenter(int y) {
        return new BlockPos(CENTER_X, y, CENTER_Z);
    }

    public static boolean containsBlock(int x, int z) {
        long dx = (long) x - CENTER_X;
        long dz = (long) z - CENTER_Z;
        return dx * dx + dz * dz <= CORE_RADIUS_SQUARED;
    }

    public static boolean containsQuart(int quartX, int quartZ) {
        return containsBlock(quartX << 2, quartZ << 2);
    }
}
