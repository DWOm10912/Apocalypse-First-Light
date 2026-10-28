package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.terrainsmooth.SmoothRules;
import com.antaurora.apofirstlight.terrainsmooth.SmoothRules.Cells;

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
 *   wears the rock's open faces (air only) in 1/8 m sub-cells; a soil wall stays a plain cube, a straight cut (dig R1: no
 *   longer worn); the soil around slopes onto either.</li>
 *   <li>{@link #C} cube: everything else (ground under the surface or under a roof, built blocks, water, plants).</li>
 * </ul>
 * Wet ground (SmoothRules#WET, river / lake V1 R0) is natural loose ground like NATURAL, and the water over it is open to it.
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
        if (!SmoothRules.natural(cell)) return C;
        if (soilWall(c, x, y, z)) return V;
        return SmoothRules.openOver(cell, above) && c.surface(x, z) == y ? H : C;
    }

    /**
     * Whether cell (x, y, z) is H: the same answer as {@link #of} == H, asked in the order that costs least where most cells
     * are not (the column's cached surface first, the soil wall's neighbours last).
     */
    public static boolean surface(Cells c, int x, int y, int z) {
        if (c.surface(x, z) != y) return false;
        int cell = c.cell(x, y, z);
        return SmoothRules.natural(cell) && SmoothRules.openOver(cell, c.cell(x, y + 1, z)) && !soilWall(c, x, y, z);
    }

    /**
     * A soil wall (U6 V3, user 2026-10-24: soil cubes did not join the worn rock): natural ground with an open face Terrain
     * Smooth cannot slope: open below (an overhang's underside), open beside while buried (a cut or a cave mouth's wall),
     * or open beside with the column there open two levels lower too (the rim of a drop of three or more). It is V:
     * never sloped (a plain cube since dig R1, no longer worn by RockRelief), the soil around pinned to it or sloping down onto it. The rim of a drop of two
     * is H since dig R1 (user 2026-10-26: "挖下去就是一个很奇怪的坑，而不是平滑的"; it was worn into fins): Terrain Smooth
     * slopes it down into the hole (SmoothRules#corner). Only when it is its column's surface: a ledge under a roof (in a
     * cave) is not sloped, so it stays a soil wall. Nor is the block under such a rim, or the foot of a mound two high,
     * where the slope beside covers its side ({@link #covered}). Reads the four columns beside it, two levels down to one up: the
     * reason a change re-shapes tops two columns away (SmoothRules#REACH).
     */
    public static boolean soilWall(Cells c, int x, int y, int z) {
        int cell = c.cell(x, y, z);
        if (!SmoothRules.natural(cell)) return false;
        if (SmoothRules.open(c.cell(x, y - 1, z))) return true;
        boolean buried = !SmoothRules.openOver(cell, c.cell(x, y + 1, z));
        for (int d = 0; d < 4; d++) {
            int nx = x + (d == 0 ? -1 : d == 1 ? 1 : 0), nz = z + (d == 2 ? -1 : d == 3 ? 1 : 0);
            if (!SmoothRules.open(c.cell(nx, y, nz))) continue;
            if (buried ? !covered(c, x, y, z, nx, nz)
                    : SmoothRules.open(c.cell(nx, y - 1, nz)) && (SmoothRules.open(c.cell(nx, y - 2, nz)) || c.surface(x, z) != y)) return true;
        }
        return false;
    }

    /**
     * Dig R1: whether buried cell (x, y, z) is hidden on its side toward column (nx, nz) by that column's slope: the block
     * just under a hole's rim, the foot of a mound two high (user 2026-10-26: worn into rock shapes with the sky showing
     * through, "怎么还是有岩石形状的泥土"). Its column's top is two over the neighbour's and both corners of their shared
     * edge are at this cell's top, so the neighbour's top covers its side. Only the surfaces of the two columns and the
     * neighbour's top (whose column kinds read open-topped cells, never this branch) are read.
     */
    private static boolean covered(Cells c, int x, int y, int z, int nx, int nz) {
        if (c.surface(x, z) != y + 1 || c.surface(nx, nz) != y - 1) return false;
        long g = SmoothRules.geometry(c, SmoothRules.MODE_B, nx, y - 1, nz);   // spread two: the same corner in either mode
        if (g < 0) return false;
        float[] k = SmoothRules.corners(g);   // nw, ne, sw, se above the neighbour's floor (y - 1)
        int a = nx < x ? 1 : nx > x ? 0 : nz < z ? 2 : 0, b = nx < x ? 3 : nx > x ? 2 : nz < z ? 3 : 1;
        return k[a] >= 2 - 1e-4F && k[b] >= 2 - 1e-4F;
    }
}
