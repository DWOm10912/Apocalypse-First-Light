package com.antaurora.apofirstlight.map;

/**
 * What a map knows of the ground (AFL Map V1, docs/ui/afl_map_v1_plan.md 5.2): per chunk 8 x 8 cells of 2 m, each a
 * packed sample of its surface height, whether it is water and its biome (MapStyle index); a chunk not seen is null.
 * A chunk's array is never changed once handed out (a new sample replaces it), so a raster job may read it on a worker.
 */
public interface MapGround {
    /** Cell size (m) and cells along a chunk. */
    int CELL = 2, CELLS = 8;

    /** The chunk's 64 samples (index z * 8 + x), or null when it was not seen. */
    int[] chunk(int cx, int cz);

    /** A sample: height + 128 in bits 0..11 (never 0 above y -128), water bit 12, biome + 1 in bits 13..20. */
    static int pack(int y, int biome, boolean water) {
        return Math.max(1, Math.min(4095, y + 128)) | (water ? 1 << 12 : 0) | ((biome + 1) & 0xFF) << 13;
    }

    static int y(int s) { return (s & 4095) - 128; }

    static boolean water(int s) { return (s >> 12 & 1) != 0; }

    /** The biome index, -1 when none was recorded. */
    static int biome(int s) { return (s >>> 13 & 0xFF) - 1; }

    MapGround NONE = (cx, cz) -> null;
}
