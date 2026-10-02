package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Beverage Cooler shelf layout, measured from the V2 mesh (tools/build-beverage-cooler-v2.mjs). Coordinates are the
 * cooler's source units (px): x runs along FACING's clockwise side from the right outer wall (-8) to the left outer wall
 * (24), the two doors meet at x = 8; y up from the floor of the master cell; z toward the back, the door glass at about
 * -7. Five shelves x 6 columns (columns 0-2 behind the right door, 3-5 behind the left door), cell = shelf * 6 + column.
 * Since 2026-10-01 the cells only place the goods (BeverageCoolerRenderer) and the aim test (#targetCell); the contents
 * are a plain 18-slot searchable container.
 */
public final class BeverageCoolerLayout {
    public static final int SHELVES = 5;
    public static final int COLUMNS = 6;
    public static final int CELLS = SHELVES * COLUMNS;

    private static final double[] SHELF_TOPS = {4.16D, 9.01D, 13.86D, 18.71D, 23.56D};
    // wire deck between the side rails, split at the meeting stiles; rails front / back
    private static final double DECK_X0 = -6.15D;
    private static final double DECK_X1 = 22.15D;
    private static final double MID_X = 8.0D;
    public static final double DECK_Z0 = -4.4D;
    private static final double DECK_Z1 = 4.5D;
    private static final double LABEL_Z = -5.1D;
    // target volume of a cell: 0.5 px below the deck top (the rails) to 4.3 px above it (under the next shelf's rails)
    private static final double BELOW_TOP = 0.5D;
    private static final double ABOVE_TOP = 4.3D;

    private BeverageCoolerLayout() {
    }

    /** Columns 3-5 are behind the left door (the master side), 0-2 behind the right door. */
    public static boolean behindLeftDoor(int column) {
        return column >= COLUMNS / 2;
    }

    private static double columnWidth() {
        return (MID_X - DECK_X0) / (COLUMNS / 2.0D);
    }

    public static double columnX(int column) {
        int half = COLUMNS / 2;
        return column < half ? DECK_X0 + (column + 0.5D) * columnWidth() : MID_X + (column - half + 0.5D) * (DECK_X1 - MID_X) / half;
    }

    public static double shelfTop(int shelf) {
        return SHELF_TOPS[shelf];
    }

    /** World position -> source units of the cooler whose master (lower-left) cell is at {@code master}. */
    public static Vec3 toSource(Vec3 world, BlockPos master, Direction facing) {
        Direction leftward = facing.getClockWise();
        double midX = master.getX() + 0.5D - leftward.getStepX() * 0.5D;
        double midZ = master.getZ() + 0.5D - leftward.getStepZ() * 0.5D;
        double dx = world.x - midX;
        double dz = world.z - midZ;
        return new Vec3(MID_X + 16.0D * (dx * leftward.getStepX() + dz * leftward.getStepZ()),
                (world.y - master.getY()) * 16.0D,
                -16.0D * (dx * facing.getStepX() + dz * facing.getStepZ()));
    }

    /**
     * The cell (0..29) the eye ray points at: goods are not solid, so the ray runs to the cooler surface it hit
     * (a deck, the back wall, a side wall); the cell is the first target volume (one column on one shelf, the deck's depth)
     * the ray enters on the way. Only cells behind an open door count. -1 when none.
     */
    public static int targetCell(Vec3 eye, Vec3 hit, boolean leftOpen, boolean rightOpen) {
        if (eye.z >= DECK_Z1) return -1;
        Vec3 ray = hit.subtract(eye);
        int nearest = -1;
        double nearestT = 1.0D + 1.0E-4D;
        for (int cell = 0; cell < CELLS; cell++) {
            int column = cell % COLUMNS;
            if (!(behindLeftDoor(column) ? leftOpen : rightOpen)) continue;
            double t = cellEntry(cell, eye, ray);
            if (t < nearestT) {
                nearestT = t;
                nearest = cell;
            }
        }
        return nearest;
    }

    private static double cellEntry(int cell, Vec3 origin, Vec3 direction) {
        int shelf = cell / COLUMNS;
        int column = cell % COLUMNS;
        double halfWidth = columnWidth() / 2.0D;
        double[] min = {columnX(column) - halfWidth, SHELF_TOPS[shelf] - BELOW_TOP, LABEL_Z};
        double[] max = {columnX(column) + halfWidth, SHELF_TOPS[shelf] + ABOVE_TOP, DECK_Z1};
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
