package com.antaurora.apofirstlight.containersearch;

/**
 * Slot grid of a search menu, matching the vanilla screen it imitates: the container grid (columns x rows, top-left
 * slot) and the top of the player inventory (the hotbar sits 58 px lower). The vanilla layouts: the chest grid 9 x 1..6
 * ({@code ChestMenu}) and the 3 x 3 dispenser grid ({@code DispenserMenu}); and AFL's own 6 x 3 grid (chest freezer,
 * 2026-10-01), centred on the 3-row chest panel, and its 3 x 4 grid (back bar shelf, 2026-10-04), centred on the 4-row chest
 * panel; neither has a vanilla menu or screen.
 */
public final class AflContainerSearchLayout {
    public static final AflContainerSearchLayout GRID_3X3 = new AflContainerSearchLayout(3, 3, 62, 17, 84);
    /** 6 x 3 on the 3-row chest panel: columns centred (27 px in from the 9-wide grid), the chest's rows and inventory. */
    public static final AflContainerSearchLayout GRID_6X3 = new AflContainerSearchLayout(6, 3, 35, 18, 85);
    /** 3 x 4 on the 4-row chest panel: the three columns centred (54 px in from the 9-wide grid), the chest's rows and inventory. */
    public static final AflContainerSearchLayout GRID_3X4 = new AflContainerSearchLayout(3, 4, 62, 18, 103);
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

    /** The vanilla 9-wide chest grid. */
    public boolean isChest() {
        return this != GRID_3X3 && this != GRID_6X3 && this != GRID_3X4;
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
