package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules.Cells;

import static com.antaurora.apofirstlight.terrainsmooth.SmoothRules.NATURAL;
import static com.antaurora.apofirstlight.terrainsmooth.SmoothRules.ROCK;

/**
 * Terrain rework R1 (docs/worldgen/terrain_rework_v1_design.md 3.1, 11): the one decision which geometry a natural cell
 * takes, in place of the four that grew up apart (Terrain Smooth's natural top, the soil wall, RockRelief's worn rock,
 * the earthwork's free columns, which read the first two through SmoothRules#column). Pure: cells and their
 * neighbours only, the same on both sides. Behaviour unchanged (R1): every answer is the one the rules gave before,
 * checked cell by cell against the frozen copy (GroundClassCheck).
 * <ul>
 *   <li>{@link #H} surface: natural loose ground (NATURAL) that is its column's natural surface, open above (air or a
 *   cover: a plant, snow, a boulder) and no soil wall. Terrain Smooth slopes it; the earthwork edits it.</li>
 *   <li>{@link #V} volume: natural rock (ROCK, any face), and natural loose ground that is a {@link #soilWall}. RockRelief
 *   wears its open faces (air only) in 1/8 m sub-cells; the soil around slopes onto it.</li>
 *   <li>{@link #C} cube: everything else (ground under the surface or under a roof, built blocks, water, plants).</li>
 * </ul>
 */
public final class GroundClass {
    private GroundClass() {}

    public static final int C = 0, H = 1, V = 2;

    /** The class of cell (x, y, z). */
    public static int of(Cells c, int x, int y, int z) {
        return of(c, c.cell(x, y, z), c.cell(x, y + 1, z), x, y, z);
    }

    /** The same with the cell and the one above it already read (SmoothRules#column reads them anyway). */
    public static int of(Cells c, int cell, int above, int x, int y, int z) {
        if (cell == ROCK) return V;
        if (cell != NATURAL) return C;
        if (soilWall(c, x, y, z)) return V;
        return SmoothRules.open(above) && c.surface(x, z) == y ? H : C;
    }

    /**
     * Whether cell (x, y, z) is H: the same answer as {@link #of} == H, asked in the order that costs least where most cells
     * are not (the column's cached surface first, the soil wall's neighbours last).
     */
    public static boolean surface(Cells c, int x, int y, int z) {
        return c.surface(x, z) == y && c.cell(x, y, z) == NATURAL && SmoothRules.open(c.cell(x, y + 1, z)) && !soilWall(c, x, y, z);
    }

    /**
     * A soil wall (U6 V3, user 2026-10-24: soil cubes did not join the worn rock): natural ground with an open face Terrain
     * Smooth cannot slope: open below (an overhang's underside), open beside while buried (a cut or a cave mouth's wall),
     * or open beside with the column there open a level lower too (the rim of a drop of two or more). RockRelief wears it
     * like rock, so it is V: never sloped, the soil around pinned to it or sloping down onto it. Reads the four columns
     * beside it, one level down to one up: the reason a change re-shapes tops two columns away (SmoothRules#REACH).
     */
    public static boolean soilWall(Cells c, int x, int y, int z) {
        if (c.cell(x, y, z) != NATURAL) return false;
        if (SmoothRules.open(c.cell(x, y - 1, z))) return true;
        boolean buried = !SmoothRules.open(c.cell(x, y + 1, z));
        for (int d = 0; d < 4; d++) {
            int nx = x + (d == 0 ? -1 : d == 1 ? 1 : 0), nz = z + (d == 2 ? -1 : d == 3 ? 1 : 0);
            if (SmoothRules.open(c.cell(nx, y, nz)) && (buried || SmoothRules.open(c.cell(nx, y - 1, nz)))) return true;
        }
        return false;
    }
}
