package com.antaurora.apofirstlight.ground;

/**
 * What GroundGeometry reads (terrain rework R0, docs/worldgen/terrain_rework_v1_design.md 6): the ground's four current
 * geometries by block, without Minecraft types, so the offline check (GroundCheck) can build a world of its own. The game's
 * is LevelGround. Coordinates are world blocks.
 */
public interface GroundSource {
    /** Whether column (x, z) is loaded (its chunk is in hand). */
    boolean loaded(int x, int z);

    /** The world's lowest block y. */
    int minY();

    /** The highest block y of column (x, z) holding anything but air (cells above are open); a hint, never too low. */
    int top(int x, int z);

    /** The 25 absolute vertex heights (quanta, EarthGrid) of edited column (x, z) into h; false when it is not edited. */
    boolean earth(int x, int z, int[] h);

    /** The gone sub-cells (CarveChunk layout) of a carved or worn natural block, null when it is whole. */
    long[] gone(int x, int y, int z);

    /** The geometry key of a sloped natural top (SmoothRules#geometry), -1 when the block is not one. */
    long smooth(int x, int y, int z);

    /** The drawn top of sloped natural block (x, y, z) whose key is k (#smooth >= 0): SmoothTop#top (R2b), above its floor. */
    default float[] smoothTop(int x, int y, int z, long k) {
        return com.antaurora.apofirstlight.terrainsmooth.SmoothRules.corners(k);
    }

    /**
     * A drawn top that is not the block's collision (road drainage D1, docs/worldgen/road_drainage_v1_design.md 3): a road's
     * pavement at its design height, graded turf as its triangles. rel: the 25 vertex heights 0.25 m apart (EarthGrid
     * layout, blocks above the floor y, up to GroundGeometry.RISE); false when the block is not one (its boxes stand).
     */
    default boolean drawn(int x, int y, int z, double[] rel) { return false; }

    /**
     * Any other block's solid boxes relative to its cell, 6 per box (minX, minY, minZ, maxX, maxY, maxZ); empty when it is
     * open (air, plants, fluids).
     */
    double[] boxes(int x, int y, int z);
}
