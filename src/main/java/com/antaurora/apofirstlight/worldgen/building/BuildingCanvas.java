package com.antaurora.apofirstlight.worldgen.building;

import com.antaurora.apofirstlight.worldgen.network.town.TownBuildings;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A building's cells in its own frame (Town buildings V1, docs/worldgen/town_buildings_v1.md): u along the front from the
 * left as one looks at the building from its street, w from the front face into the lot, y from the floor (the first air
 * layer inside; -1 the floor block). A kit draws it as if the building faced north (its block states too); the canvas
 * turns it to the site's facing. Cells never set are left as the world has them.
 */
public final class BuildingCanvas {
    public final TownBuildings.Site site;
    public final int width, depth, height;
    /** The lowest y a kit may set (the floor block and the footing under it). */
    public static final int BELOW = 2;
    private final BlockState[] cells;
    private final Rotation rotation;
    // world of (u, w): x = cx + rx u + dx w, z = cz + rz u + dz w
    private final int cx, cz, rx, rz, dx, dz;

    public BuildingCanvas(TownBuildings.Site site, int height) {
        this.site = site;
        this.width = site.width();
        this.depth = site.depth();
        this.height = height;
        this.cells = new BlockState[width * depth * (height + BELOW)];
        // into the lot: against the front; u: to the right of someone facing the building ((-dz, dx) of the way in)
        dx = -site.fx();
        dz = -site.fz();
        rx = -dz;
        rz = dx;
        // the front left corner: the footprint's corner least along both (on each world axis exactly one of them runs)
        cx = rx + dx > 0 ? site.x0() : site.x1();
        cz = rz + dz > 0 ? site.z0() : site.z1();
        rotation = site.fz() < 0 ? Rotation.NONE : site.fx() > 0 ? Rotation.CLOCKWISE_90 : site.fz() > 0 ? Rotation.CLOCKWISE_180 : Rotation.COUNTERCLOCKWISE_90;
    }

    /** Sets a cell (a north-facing state, turned with the building); outside the canvas: ignored. */
    public void set(int u, int y, int w, BlockState state) {
        int i = index(u, y, w);
        if (i >= 0) cells[i] = state.rotate(rotation);
    }

    /** Fills a box of cells, both corners inclusive. */
    public void fill(int u0, int y0, int w0, int u1, int y1, int w1, BlockState state) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
            for (int w = Math.min(w0, w1); w <= Math.max(w0, w1); w++)
                for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++) set(u, y, w, state);
    }

    /** The cell's state (already turned), or null when never set or outside. */
    public BlockState get(int u, int y, int w) {
        int i = index(u, y, w);
        return i < 0 ? null : cells[i];
    }

    private int index(int u, int y, int w) {
        if (u < 0 || u >= width || w < 0 || w >= depth || y < -BELOW || y >= height) return -1;
        return ((y + BELOW) * depth + w) * width + u;
    }

    /** World x and z of cell (u, w). */
    public int x(int u, int w) { return cx + rx * u + dx * w; }

    public int z(int u, int w) { return cz + rz * u + dz * w; }

    /** World Y of a canvas y. */
    public int worldY(int y) { return site.floorY() + y; }
}
