package com.antaurora.apofirstlight.containersearch;

/**
 * Slot grid of a search menu, matching the vanilla screen it imitates: the container grid (columns x rows, top-left
 * slot) and the top of the player inventory (the hotbar sits 58 px lower). Only the vanilla layouts exist: the chest
 * grid 9 x 1..6 ({@code ChestMenu}) and the 3 x 3 dispenser grid ({@code DispenserMenu}).
 */
public final class AflContainerSearchLayout {
    public static final AflContainerSearchLayout GRID_3X3 = new AflContainerSearchLayout(3, 3, 62, 17, 84);
    private static final AflContainerSearchLayout[] CHEST = new AflContainerSearchLayout[6];

    static {
        for (int rows = 1; rows <= 6; rows++) {
            CHEST[rows - 1] = new AflContainerSearchLayout(9, rows, 8, 18, 103 + (rows - 4) * 18);
        }
    }

    private final int columns;
    private final int rows;
    private final int slotX;
    private final int slotY;
    private final int inventoryY;

    private AflContainerSearchLayout(int columns, int rows, int slotX, int slotY, int inventoryY) {
        this.columns = columns;
        this.rows = rows;
        this.slotX = slotX;
        this.slotY = slotY;
        this.inventoryY = inventoryY;
    }

    /** Chest grid with 1..6 rows of 9. */
    public static AflContainerSearchLayout chest(int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("chest grid needs 1..6 rows, got " + rows);
        }
        return CHEST[rows - 1];
    }

    public boolean isChest() {
        return this != GRID_3X3;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return columns * rows;
    }

    public int slotX(int column) {
        return slotX + column * 18;
    }

    public int slotY(int row) {
        return slotY + row * 18;
    }

    public int inventoryY() {
        return inventoryY;
    }

    @Override
    public String toString() {
        return columns + "x" + rows;
    }
}
