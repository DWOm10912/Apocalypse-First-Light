package com.antaurora.apofirstlight.worldgen.terrain;

/** Pure height conversion; no dimension-specific build limits or unknown sentinels. */
public final class TerrainHeights {
    private TerrainHeights() {}

    /**
     * Converts a top block's Y to the first block position above that surface.
     * Examples: solid block 63 -> surface 64; fluid block 62 -> surface 63;
     * block -60 -> surface -59. Unknown input must remain OptionalInt.empty()
     * in the sample, not be passed here as a magic integer.
     *
     * @throws ArithmeticException if adding one would overflow an int
     */
    public static int surfaceAboveBlockY(int blockY) {
        return Math.addExact(blockY, 1);
    }
}
