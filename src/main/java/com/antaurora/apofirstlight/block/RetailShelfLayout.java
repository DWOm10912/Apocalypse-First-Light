package com.antaurora.apofirstlight.block;

/** Canonical north-facing layout, measured from the production shelf bbmodel. */
public final class RetailShelfLayout {
    public static final int ROWS = 5;
    public static final int COLUMNS = 3;
    public static final int SLOTS = ROWS * COLUMNS;
    public static final int MAX_STACK_PER_SLOT = 1;

    public static final float ITEM_SCALE = 0.24F;
    public static final double DISPLAY_Z = ((8.0D - 1.446D) + (8.0D + 7.084D)) / 32.0D;
    public static final double FRONT_Z = (8.0D - 2.096D) / 16.0D;
    public static final double X_HIT_TOLERANCE = 0.12D;
    public static final double Y_HIT_TOLERANCE = 0.155D;
    public static final double MAX_PROJECTION_BEHIND_HIT = 0.35D;

    // Slot 0 was on the positive-X side in existing worlds; do not reverse this order.
    private static final double[] COLUMN_X = {0.76D, 0.50D, 0.24D};
    private static final double[] DECK_TOP_UNITS = {2.82D, 8.57D, 14.32D, 20.07D, 25.82D};

    private RetailShelfLayout() {
    }

    public static double columnX(int column) {
        return COLUMN_X[column];
    }

    public static double rowY(int row) {
        return DECK_TOP_UNITS[row] / 16.0D + ITEM_SCALE / 2.0D;
    }

    public static double deckTopUnits(int row) {
        return DECK_TOP_UNITS[row];
    }

    public static int nearestColumn(double x) {
        int nearest = 0;
        for (int column = 1; column < COLUMNS; column++) {
            if (Math.abs(x - columnX(column)) < Math.abs(x - columnX(nearest))) {
                nearest = column;
            }
        }
        return nearest;
    }

    public static int nearestRow(double y) {
        int nearest = 0;
        for (int row = 1; row < ROWS; row++) {
            if (Math.abs(y - rowY(row)) < Math.abs(y - rowY(nearest))) {
                nearest = row;
            }
        }
        return nearest;
    }
}
