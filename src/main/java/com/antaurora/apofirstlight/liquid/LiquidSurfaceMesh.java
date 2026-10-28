package com.antaurora.apofirstlight.liquid;

/**
 * One continuous water surface (docs/gameplay/water_mesh_parcels_v1_design.md A2): the heights, fade, flow and foam of the
 * drawn surface's vertices, 3 x 3 a cell (corners, edge middles, centre), from the cells the server sends. Shared by the
 * client's surface (ClientLiquid) and the offline check (WaterMeshCheck); nothing in it is the client's.
 * <ul>
 *   <li>A vertex's value comes only from the cells around it, gathered the same way whichever of them asks, and added up
 *   in a fixed order: two chunks building the vertex they share get the same number, so there is no crack.</li>
 *   <li>Still and deep water is level: a vertex is the mean of the levels of the cells around it that are one body of
 *   water with each other (the server's joined sides; a corner reaches its diagonal cell through a side one).</li>
 *   <li>Running water ({@link Cell#drapes()}) lies on the ground: a vertex is the higher of that mean and the drawn ground
 *   there plus the film's thickness (its mean depth, at least {@link #MIN_FILM}), so on a slope it is a sheet on the ground
 *   that flattens into the pool it runs into. Two running cells, or a running one and a pool, are one surface across a
 *   step under {@link #STEP} too (the engine joins no sides across a step or a slope steeper than the film: their levels
 *   lie apart) where the ground goes on across it (a smoothed slope): the surface bends down into the water below. Across
 *   a wall (a block's face, ground jumping more than {@link #GROUND_JUMP}) they stay apart: water does not climb it.
 *   Taller steps are falls.</li>
 *   <li>A pool's shore is not running water: a thin cell at its pool's level, joined to it, stays level and the ground cuts
 *   it ({@link #classify}; M1 in game 2026-10-26, design 11.7).</li>
 *   <li>Where running water ends against dry ground it fades out (the share of the cells round a vertex that are wet).</li>
 *   <li>A side the server joins but whose neighbour sent nothing near this level (the water runs on into a film not
 *   sent) comes down to the ground there (LiquidSurfaceEdge, falling water 21).</li>
 * </ul>
 * Known limit (falling water 21): a cell has one level, so a crest inside a cell holds no water back.
 */
public final class LiquidSurfaceMesh {
    /** Two neighbours' water are one surface when their levels are this close (m) and their water overlaps. */
    public static final float JOIN = 0.6F;
    /** Running water across a step lower than this (m) is one bent surface; a taller one is a fall. */
    public static final float STEP = 0.5F;
    /** A film is drawn at least this far above the ground (m): no flicker against it. */
    public static final float MIN_FILM = 0.004F;
    /** Water this deep or more (m) drapes only when it moves at least DRAPE_SPEED (m/s): a pool's shallow shore does not climb its bank. */
    public static final float THIN = 0.008F, DRAPE_SPEED = 0.1F;
    /** Running water deeper than this on average (m) is a stream or a pool filling, never a film on the ground: it stays level. */
    public static final float FILM_MAX = 0.05F;
    /** Ground on the two sides of an edge further apart than this (m) is a wall, not a slope: no surface across it. */
    public static final float GROUND_JUMP = 0.15F;
    /** A thin cell within this of a joined pool's level (m) is that pool's shore. */
    public static final float SHORE = 0.03F;
    /** A level may stand this far (m, one slab of the engine's ground) under the drawn ground at its own low edge. */
    public static final float SLAB = 1 / 16F;
    /** Ground this far above a cell's level (m) at an edge is above its water (fedFromAbove). */
    public static final float FED = 0.01F;
    /** Points this far inside a cell (m) stand for its side of a shared edge. */
    private static final double IN = 0.02;

    private LiquidSurfaceMesh() {
    }

    /** One cell as sent (LiquidPackets.Surface). */
    public abstract static class Cell {
        public final int x, z, floorSlab;
        /** LiquidCell#joinedSides. */
        public final int sides;
        public final boolean boundary, still, running;
        /** Velocity east and south, m/s; a running cell's mean depth, m (0: not running). */
        public float vx, vz, film;
        /** Drawn on the ground ({@link #classify}). */
        public boolean drape;
        /** The highest a vertex drawn on the ground may stand: the water that feeds it, plus the film ({@link #classify}). */
        public float drapeTop;

        protected Cell(int x, int z, int floorSlab, int sides, boolean boundary, boolean still, boolean running) {
            this.x = x;
            this.z = z;
            this.floorSlab = floorSlab;
            this.sides = sides;
            this.boundary = boundary;
            this.still = still;
            this.running = running;
        }

        /** The level drawn (m). */
        public abstract float level();

        public float floor() {
            return floorSlab / 16F;
        }

        /**
         * A film by itself: running, its mean depth under {@link #FILM_MAX} (a pool filling fast is not), and thin or moving
         * (a pool filling slowly stays level to its shore).
         */
        public boolean filmLike() {
            return running && !boundary && film < FILM_MAX
                    && (level() - floor() < THIN || vx * vx + vz * vz >= DRAPE_SPEED * DRAPE_SPEED);
        }

        /** Drawn on the ground: a film and not a pool's shore ({@link #classify}, set when the cells round it come). */
        public boolean drapes() {
            return drape;
        }
    }

    /** What the surface reads around it. */
    public interface Source {
        /** The cells of column (x, z), null or empty: none. */
        Cell[] column(int x, int z);

        /** The ground as drawn at (x, z), searched about level; NaN: none. */
        double ground(double x, double z, float level);
    }

    /** One vertex of the surface. */
    public static final class Vertex {
        /** Height (m); how far it is shown (0-1); the flow there (m/s); white water (0-1). */
        public float height, fade, vx, vz, foam;
        /** On running water (a film's look). */
        public boolean draped;
    }

    /**
     * Sets c's {@link Cell#drape}: a film ({@link Cell#filmLike}) that is not a pool's shore, i.e. not joined (the server's
     * sides) to a neighbour that is no film whose level is within {@link #SHORE} of c's, nor a pocket a pool fills from
     * below ({@link #filledFromPool}) unless water also comes in from above ({@link #fedFromAbove}). Asked again whenever
     * c's neighbours change (they come in other chunks' packets).
     */
    public static void classify(Source s, Cell c) {
        c.drape = c.filmLike() && !shore(s, c) && !(filledFromPool(s, c) && !fedFromAbove(s, c));
        c.drapeTop = feedLevel(s, c) + Math.max(MIN_FILM, c.film);
    }

    /**
     * The highest water that runs onto c: its own level, or a neighbour's that comes in over c's higher ground (as
     * {@link #fedFromAbove}). A film is drawn on the ground no higher than this: a cell holding 8 mm whose smoothed ground
     * rises to the wall's top at its edges (a block dug beside a pond) was drawn up to the top, 1 m above its water, and
     * lifted the pond's edge with it (M1 in game round 5, 2026-10-26: "很白的水面往上覆盖", design 11.10).
     */
    private static float feedLevel(Source s, Cell c) {
        float level = c.level(), top = level;
        for (int bit : new int[]{LiquidCell.EAST, LiquidCell.WEST, LiquidCell.SOUTH, LiquidCell.NORTH}) {
            Cell[] at = s.column(c.x + dx(bit), c.z + dz(bit));
            if (at == null) continue;
            double mx = dx(bit) == 0 ? c.x + 0.5 : dx(bit) > 0 ? c.x + 1 - IN : c.x + IN;
            double mz = dz(bit) == 0 ? c.z + 0.5 : dz(bit) > 0 ? c.z + 1 - IN : c.z + IN;
            for (Cell o : at) {
                float lo = o.level();
                if (lo <= Math.max(level + FED, top)) continue;
                double g = s.ground(mx, mz, lo);
                if (!Double.isNaN(g) && g > level + FED && g < lo + SLAB) top = (float) Math.max(lo, g);
            }
        }
        return top;
    }

    /**
     * Is c filling from a pool beside it, the pool's water coming in at c's own water (not over its higher ground)? Joined
     * to a neighbour that is no film, standing no lower than SHORE under c's level, across an edge where c's ground is under
     * c's level. The engine keeps every cell's water as a puddle at its lowest (its level): water coming in like this stays
     * in the bottom of the cell, drawn level, the ground cutting it. A pond running into a block dug at its waterline, whose
     * smoothed slope rises out of the water, was drawn as a film over the whole slope (M1 in game round 4, 2026-10-26: "挖掉
     * 方块，水会往上覆盖", design 11.9).
     */
    private static boolean filledFromPool(Source s, Cell c) {
        float level = c.level();
        for (int bit : new int[]{LiquidCell.EAST, LiquidCell.WEST, LiquidCell.SOUTH, LiquidCell.NORTH}) {
            if ((c.sides & bit) == 0) continue;
            Cell[] at = s.column(c.x + dx(bit), c.z + dz(bit));
            if (at == null) continue;
            double mx = dx(bit) == 0 ? c.x + 0.5 : dx(bit) > 0 ? c.x + 1 - IN : c.x + IN;
            double mz = dz(bit) == 0 ? c.z + 0.5 : dz(bit) > 0 ? c.z + 1 - IN : c.z + IN;
            for (Cell o : at) {
                float lo = o.level();
                if (lo <= c.floor() || level <= o.floor() || o.filmLike() || lo < level - SHORE) continue;
                double g = s.ground(mx, mz, level);
                if (!Double.isNaN(g) && g <= level + FED) return true;
            }
        }
        return false;
    }

    /**
     * Does water come into c over its higher ground, so that it runs down across the whole cell? A neighbour's water stands
     * above c's ground just inside their shared edge, and that ground is above c's level.
     */
    private static boolean fedFromAbove(Source s, Cell c) {
        float level = c.level();
        for (int bit : new int[]{LiquidCell.EAST, LiquidCell.WEST, LiquidCell.SOUTH, LiquidCell.NORTH}) {
            Cell[] at = s.column(c.x + dx(bit), c.z + dz(bit));
            if (at == null) continue;
            double mx = dx(bit) == 0 ? c.x + 0.5 : dx(bit) > 0 ? c.x + 1 - IN : c.x + IN;
            double mz = dz(bit) == 0 ? c.z + 0.5 : dz(bit) > 0 ? c.z + 1 - IN : c.z + IN;
            for (Cell o : at) {
                float lo = o.level();
                if (lo <= level + FED) continue;
                double g = s.ground(mx, mz, lo);
                if (!Double.isNaN(g) && g > level + FED && g < lo + SLAB) return true;
            }
        }
        return false;
    }

    private static boolean shore(Source s, Cell c) {
        for (int bit : new int[]{LiquidCell.EAST, LiquidCell.WEST, LiquidCell.SOUTH, LiquidCell.NORTH}) {
            if ((c.sides & bit) == 0) continue;
            Cell[] at = s.column(c.x + dx(bit), c.z + dz(bit));
            if (at == null) continue;
            float level = c.level();
            for (Cell o : at) {
                float lo = o.level();
                if (lo <= c.floor() || level <= o.floor()) continue;
                if (!o.filmLike() && Math.abs(lo - level) < SHORE) return true;
            }
        }
        return false;
    }

    private static int dx(int bit) {
        return bit == LiquidCell.EAST ? 1 : bit == LiquidCell.WEST ? -1 : 0;
    }

    private static int dz(int bit) {
        return bit == LiquidCell.SOUTH ? 1 : bit == LiquidCell.NORTH ? -1 : 0;
    }

    /** The cell of column (x, z) that is the same body of water as c, or null. */
    public static Cell pick(Source s, int x, int z, Cell c) {
        Cell[] at = s.column(x, z);
        if (at == null) return null;
        float level = c.level();
        Cell best = null;
        float bestD = Float.MAX_VALUE;
        for (Cell o : at) {
            float lo = o.level(), d = Math.abs(lo - level);
            if (d >= bestD) continue;
            // their water overlaps (each one's surface above the other's floor); running water also across a step
            boolean one = d < JOIN && lo > c.floor() && level > o.floor();
            boolean step = (c.drapes() || o.drapes()) && d < STEP && lo > c.floor() - STEP && level > o.floor() - STEP;
            if (one || step) {
                best = o;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * Is c's water one surface with o's across c's side {@code bit} (LiquidCell.EAST ...)? The same asked from o: joined
     * by the server, or running across a step under {@link #STEP} where the ground goes on (no wall between them).
     */
    public static boolean linked(Source s, Cell c, int bit, Cell o) {
        if (o == null) return false;
        if ((c.sides & bit) != 0) return true;
        if (!(c.drapes() || o.drapes()) || Math.abs(c.level() - o.level()) >= STEP) return false;
        // the ground just inside each of them at the edge's middle, each about its own level
        double mx = dx(bit) == 0 ? c.x + 0.5 : dx(bit) > 0 ? c.x + 1 : c.x, mz = dz(bit) == 0 ? c.z + 0.5 : dz(bit) > 0 ? c.z + 1 : c.z;
        double gc = s.ground(mx - dx(bit) * IN, mz - dz(bit) * IN, c.level()), go = s.ground(mx + dx(bit) * IN, mz + dz(bit) * IN, o.level());
        return !Double.isNaN(gc) && !Double.isNaN(go) && Math.abs(gc - go) < GROUND_JUMP;
    }

    /** c's neighbour across side bit when they are one surface, or null. */
    public static Cell across(Source s, Cell c, int bit) {
        Cell o = pick(s, c.x + dx(bit), c.z + dz(bit), c);
        return linked(s, c, bit, o) ? o : null;
    }

    private static int opposite(int bit) {
        return bit == LiquidCell.EAST ? LiquidCell.WEST : bit == LiquidCell.WEST ? LiquidCell.EAST : bit == LiquidCell.SOUTH ? LiquidCell.NORTH : LiquidCell.SOUTH;
    }

    /**
     * Vertex (i, j) of cell c (i east, j south, 0-2: x + i / 2, z + j / 2) into out. members holds 4 cells' scratch (the
     * caller's, reused).
     */
    public static void vertex(Source s, Cell c, int i, int j, Cell[] members, Vertex out) {
        java.util.Arrays.fill(members, null);
        double px = c.x + i * 0.5, pz = c.z + j * 0.5;
        int around;
        if (i == 1 && j == 1) {
            members[0] = c;
            around = 1;
        } else if (i == 1 || j == 1) {
            // an edge's middle: c and the neighbour across it, west / north first
            int bit = i == 0 ? LiquidCell.WEST : i == 2 ? LiquidCell.EAST : j == 0 ? LiquidCell.NORTH : LiquidCell.SOUTH;
            Cell o = across(s, c, bit);
            boolean first = bit == LiquidCell.EAST || bit == LiquidCell.SOUTH;
            members[first ? 0 : 1] = c;
            members[first ? 1 : 0] = o;
            around = 2;
        } else {
            corner(s, c, i == 0 ? c.x : c.x + 1, j == 0 ? c.z : c.z + 1, members);
            around = 4;
        }
        evaluate(s, members, around, px, pz, out);
    }

    /**
     * The body of water round the corner (cx, cz) that c belongs to: the cells of the four columns round it reached from c
     * through linked sides (the diagonal one through either side one), slot k = column (cx - 1 + (k & 1), cz - 1 + (k >> 1)).
     */
    private static void corner(Source s, Cell c, int cx, int cz, Cell[] m) {
        int k0 = (c.x - (cx - 1)) + 2 * (c.z - (cz - 1));
        m[k0] = c;
        // at most three steps out from c: breadth first over the square's four sides
        for (int round = 0; round < 3; round++) {
            boolean grew = false;
            for (int k = 0; k < 4; k++) {
                Cell a = m[k];
                if (a == null) continue;
                // the x neighbour in the square (k ^ 1) and the z one (k ^ 2)
                int kx = k ^ 1, kz = k ^ 2;
                if (m[kx] == null) {
                    int bit = (k & 1) == 0 ? LiquidCell.EAST : LiquidCell.WEST;
                    Cell o = pick(s, a.x + ((k & 1) == 0 ? 1 : -1), a.z, a);
                    if (linked(s, a, bit, o)) { m[kx] = o; grew = true; }
                }
                if (m[kz] == null) {
                    int bit = (k & 2) == 0 ? LiquidCell.SOUTH : LiquidCell.NORTH;
                    Cell o = pick(s, a.x, a.z + ((k & 2) == 0 ? 1 : -1), a);
                    if (linked(s, a, bit, o)) { m[kz] = o; grew = true; }
                }
            }
            if (!grew) break;
        }
    }

    private static void evaluate(Source s, Cell[] m, int around, double px, double pz, Vertex out) {
        float sum = 0, svx = 0, svz = 0, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE, drapeSpeed = 0;
        int n = 0;
        boolean drape = false, level = false;
        for (Cell c : m) {
            if (c == null) continue;
            float l = c.level();
            sum += l;
            svx += c.vx;
            svz += c.vz;
            lo = Math.min(lo, l);
            hi = Math.max(hi, l);
            n++;
            if (c.drapes()) {
                drape = true;
                drapeSpeed = Math.max(drapeSpeed, (float) Math.sqrt(c.vx * c.vx + c.vz * c.vz));
            } else level = true;
        }
        float h = sum / n;
        if (drape) {
            // each running cell's own film on the ground under it, from its own side of the point
            for (Cell c : m) {
                if (c == null || !c.drapes()) continue;
                double g = s.ground(clamp(px, c.x + IN, c.x + 1 - IN), clamp(pz, c.z + IN, c.z + 1 - IN), c.level());
                if (!Double.isNaN(g)) h = (float) Math.max(h, Math.min(g + Math.max(MIN_FILM, c.film), c.drapeTop));
            }
        }
        // a joined side whose neighbour sent nothing near this level: the water runs on as a film not sent, down to the
        // ground there (still water's cells only; running ones lie on the ground already)
        for (Cell c : m) {
            if (c == null || c.drapes()) continue;
            h = runoff(s, c, m, px, pz, h);
        }
        out.height = h;
        out.draped = drape;
        out.fade = drape ? n / (float) around : 1;
        out.vx = svx / n;
        out.vz = svz / n;
        // white water: fast water; running water meeting standing water (into a pool, or a pool spilling over); a break
        // in the slope (levels round the vertex far apart)
        float speed = (float) Math.sqrt(out.vx * out.vx + out.vz * out.vz);
        float foam = smooth((speed - 0.6F) / 1.4F);
        if (drape && level) foam = Math.max(foam, 0.6F * smooth(drapeSpeed / 0.8F));
        if (drape) foam = Math.max(foam, 0.5F * smooth((hi - lo - 0.15F) / 0.25F));
        out.foam = foam;
    }

    /** LiquidSurfaceEdge#runoff at the point for c's joined sides through it whose neighbour is not among the members. */
    private static float runoff(Source s, Cell c, Cell[] m, double px, double pz, float h) {
        for (int bit : new int[]{LiquidCell.EAST, LiquidCell.WEST, LiquidCell.SOUTH, LiquidCell.NORTH}) {
            if ((c.sides & bit) == 0) continue;
            boolean onEdge = bit == LiquidCell.EAST ? px == c.x + 1 : bit == LiquidCell.WEST ? px == c.x : bit == LiquidCell.SOUTH ? pz == c.z + 1 : pz == c.z;
            if (!onEdge) continue;
            int nx = c.x + (bit == LiquidCell.EAST ? 1 : bit == LiquidCell.WEST ? -1 : 0), nz = c.z + (bit == LiquidCell.SOUTH ? 1 : bit == LiquidCell.NORTH ? -1 : 0);
            boolean there = false;
            for (Cell o : m) there |= o != null && o.x == nx && o.z == nz;
            if (there) continue;
            // just inside the neighbour across the edge, and inside c along it
            double gx = bit == LiquidCell.EAST ? px + IN : bit == LiquidCell.WEST ? px - IN : clamp(px, c.x + IN, c.x + 1 - IN);
            double gz = bit == LiquidCell.SOUTH ? pz + IN : bit == LiquidCell.NORTH ? pz - IN : clamp(pz, c.z + IN, c.z + 1 - IN);
            h = LiquidSurfaceEdge.runoff(h, c.floor(), s.ground(gx, gz, c.level()));
        }
        return h;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static float smooth(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
