package com.antaurora.apofirstlight.liquid.ground;

import net.minecraft.core.Direction;

/**
 * The ground geometry the liquid framework reads: a copy of the signature of C's GroundView V0 (C's
 * docs/worldgen/terrain_rework_v1_design.md 6.2 / 6.3, C notices 2026-10-24), kept on B's side while C's
 * com.antaurora.apofirstlight.ground.GroundView is not in master. When it is, this copy goes and the framework reads C's
 * through one adapter with the same calls. The offline check runs a synthetic implementation (test SyntheticGround).
 * <ul>
 *   <li>Heights in 1/16 m slabs, areas in 1/256 m2, widths in 1/16 m. A slab's value is its integral mean (the open volume
 *   in the slab over the slab's height; for an edge, its open area over the slab's height), rounded cumulatively from y0
 *   up, so any run of slabs is off by less than one unit.</li>
 *   <li>Solid: natural ground (H skin, V sub-cells) and any other block's collision shape. Air, plants, AFL liquid and
 *   vanilla fluids are open: the liquid framework decides itself what a vanilla fluid block means to it.</li>
 *   <li>openEdge: a point of the edge is open when the room on both sides of it is; asked from either side, bit for bit
 *   the same answer.</li>
 *   <li>Not loaded or outside the world: UNKNOWN in every slab, a wall.</li>
 * </ul>
 */
public interface GroundViewV0 {
    int SLABS = 16;
    int AREA = 256;
    int UNKNOWN = -1;
    /** surfaceGrid: the column is not loaded. */
    int UNKNOWN_HEIGHT = Integer.MIN_VALUE + 1;
    /** surfaceGrid: no ground down to the world's bottom (EarthGrid.NONE). */
    int NO_GROUND = Integer.MIN_VALUE;

    /** Open area of column (x, z) in every slab from block y0 (inclusive) to y1 (exclusive), out[(y - y0) * 16 + s]. */
    void openArea(int x, int z, int y0, int y1, short[] out);

    /** Open width of the edge column (x, z) shares with its neighbour toward dir (horizontal), per slab, 0..16. */
    void openEdge(int x, int z, Direction dir, int y0, int y1, byte[] out);

    /** The ground surface at (px, pz) at or below y, in blocks; NaN when there is none or the column is not loaded. */
    double surfaceBelow(double px, double y, double pz);

    /**
     * Whether the drawn ground runs on as one slope from column (x0, z0), surface block y0, to the column beside it (x1, z1),
     * surface block y1: a smoothed terrain's ramp, stepped only in its collision. Water going down it runs, it does not
     * fall (docs/gameplay/water_falling_v1_design.md 15). False where it is not known.
     */
    default boolean ramp(int x0, int z0, int y0, int x1, int z1, int y1) {
        return false;
    }

    /** Ground material at the block, for infiltration and runoff; GroundMaterialV0.NONE where it is not natural ground. */
    GroundMaterialV0 materialAt(int x, int y, int z);

    /**
     * The first ground at or below yFrom at the 65 x 65 vertices (0.25 m apart) of chunk (chunkX, chunkZ), in 1/256 m;
     * the east and south rows are the neighbours' first rows. NO_GROUND, UNKNOWN_HEIGHT as above.
     */
    void surfaceGrid(int chunkX, int chunkZ, int yFrom, int[] out);
}
