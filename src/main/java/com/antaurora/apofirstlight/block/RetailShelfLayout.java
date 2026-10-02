package com.antaurora.apofirstlight.block;

import net.minecraft.world.phys.Vec3;

/**
 * Canonical north-facing layout, measured from the production shelf model (tools/build-retail-shelf-v3.mjs, built from the
 * V2 cube source): 5 decks x 3 columns = 15 cells. Since 2026-10-01 the cells only place the goods (one library product
 * each, RetailShelfSingleBlockEntityRenderer) and the aim test (RetailShelfSingleBlock#getClickedCell); the contents are a
 * plain 9-slot searchable container. The two depth ranks remain as aim-test geometry.
 */
public final class RetailShelfLayout {
    public static final int ROWS = 5;
    public static final int COLUMNS = 3;
    public static final int CELLS = ROWS * COLUMNS;
    public static final int FRONT = 0;
    public static final int BACK = 1;
    public static final int DEPTHS = 2;

    public static final float ITEM_SCALE = 0.24F;
    // Usable deck depth (source units, front toward -Z): from behind the price-tag lip to the back stop.
    public static final double DECK_FRONT_Z = (8.0D - 1.446D) / 16.0D;
    private static final double DECK_BACK_Z = (8.0D + 7.084D) / 16.0D;
    /** Middle of the usable deck depth: the boundary between the front and the back rank. */
    public static final double DISPLAY_Z = (DECK_FRONT_Z + DECK_BACK_Z) / 2.0D;
    public static final double FRONT_Z = (8.0D - 2.096D) / 16.0D;
    // Half extents of a cell's target volume around the item centre (x, y); in depth it spans FRONT_Z..DECK_BACK_Z.
    public static final double X_HIT_TOLERANCE = 0.12D;
    public static final double Y_HIT_TOLERANCE = 0.155D;

    // Slot 0 was on the positive-X side in existing worlds; do not reverse this order.
    private static final double[] COLUMN_X = {0.76D, 0.50D, 0.24D};
    private static final double[] DECK_TOP_UNITS = {2.82D, 8.57D, 14.32D, 20.07D, 25.82D};
    // Rank centres: the quarter points of the usable depth (each rank is 0.267 block deep, items are 0.24).
    private static final double[] DEPTH_Z = {(3.0D * DECK_FRONT_Z + DECK_BACK_Z) / 4.0D, (DECK_FRONT_Z + 3.0D * DECK_BACK_Z) / 4.0D};

    private RetailShelfLayout() {
    }

    public static double columnX(int column) {
        return COLUMN_X[column];
    }

    public static double rowY(int row) {
        return DECK_TOP_UNITS[row] / 16.0D + ITEM_SCALE / 2.0D;
    }

    public static double depthZ(int depth) {
        return DEPTH_Z[depth];
    }

    public static double deckTopUnits(int row) {
        return DECK_TOP_UNITS[row];
    }

    /**
     * Where the ray {@code origin + t * direction} (canonical) enters the target volume of a cell, or +infinity when it
     * misses it; 0 when the origin is already inside.
     */
    public static double cellEntry(int cell, Vec3 origin, Vec3 direction) {
        int row = cell / COLUMNS;
        int column = cell % COLUMNS;
        double[] min = {columnX(column) - X_HIT_TOLERANCE, rowY(row) - Y_HIT_TOLERANCE, FRONT_Z};
        double[] max = {columnX(column) + X_HIT_TOLERANCE, rowY(row) + Y_HIT_TOLERANCE, DECK_BACK_Z};
        double[] from = {origin.x, origin.y, origin.z};
        double[] along = {direction.x, direction.y, direction.z};
        double enter = 0.0D;
        double leave = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(along[axis]) < 1.0E-12D) {
                if (from[axis] < min[axis] || from[axis] > max[axis]) return Double.POSITIVE_INFINITY;
                continue;
            }
            double a = (min[axis] - from[axis]) / along[axis];
            double b = (max[axis] - from[axis]) / along[axis];
            enter = Math.max(enter, Math.min(a, b));
            leave = Math.min(leave, Math.max(a, b));
            if (enter > leave) return Double.POSITIVE_INFINITY;
        }
        return enter;
    }
}
