package com.antaurora.apofirstlight.worldgen.terrain.v2;

/**
 * Terrain V2 Phase 2b (2026-10-10, docs/worldgen/terrain_v2_phase2b_rivers_v1.md): what the river water does to one
 * block column, given the top the noise fill generated there. A pure function of the column (the plan, the river
 * network and that column's own generated top), so two chunks always agree about a column on their border and nothing
 * depends on generation order. RiverWaterFill applies it to the chunk right after the noise fill (before surface rules,
 * carvers and features); the offline audit (tools/.../RiverAudit) runs the same rule on the planned surface.
 * <ul>
 *   <li>RIVER (inside a river's width, on land or marsh): the bed sits {@code depth} blocks under the water level, or at
 *   the generated top if that is lower; water from the bed up to the level; air above it up to the old top (the
 *   channel cut into the floodplain); the two blocks under the bed are sealed against caves.</li>
 *   <li>BANK (within {@link RiverNetwork#BANK_REACH} of the water edge): the ground stands at least one block above the
 *   highest water level in reach, so no water is ever exposed sideways, and at most one block higher per block of
 *   distance from the edge (graded banks instead of a trench where a meander meets a valley side); cave air under a
 *   bank is sealed down to 6 below the lowest level in reach.</li>
 *   <li>POOL (tidal marsh, TerrainPlanSurface.poolAt, only where the generated top is the marsh flat Y63): water at the
 *   sea level Y62 over a bed at Y61.</li>
 *   <li>LAKE (federal plans, C2): a column inside a lake's cells (any share of a lake cell among the four round it)
 *   whose generated top lies under the lake's top water block holds water up to it, so the shore follows the
 *   ground's contour; it comes before the river rules (a river through a lake runs at the lake's level, and its banks
 *   must not stand up inside the water); the two blocks under the bed are sealed.</li>
 *   <li>RIM (federal lakes, after the river rules, so an outlet river keeps its channel): a column just outside a
 *   lake's cells, beside one inside them, whose generated top lies under that lake's top water block is raised to it,
 *   so no lake water stands beside air (the offline audit found one-block faces where the cell share ends).</li>
 *   <li>Open sea and estuary water (generated ground under Y62) is left to its own sea-level fill
 *   (NoiseChunkMacroWaterMixin); dry ground inside a sea or estuary cell takes the rules above (Phase 2b fix: the river
 *   used to stop at the estuary cell's edge, leaving a straight wall across its mouth).</li>
 * </ul>
 */
public final class RiverCarver {
    private RiverCarver() {
    }

    public static final int NONE = 0, RIVER = 1, BANK = 2, POOL = 3, LAKE = 4, RIM = 5;
    public static final int MARSH_TOP = 63, POOL_WATER = 62;

    /** One column's edit. */
    public static final class Edit {
        public int kind;
        /** The ground's top block after the edit. */
        public int ground;
        /** The top water block, Integer.MIN_VALUE for none. */
        public int water;
        /** Cave air from the ground top down to this Y (inclusive) becomes stone. */
        public int sealTo;
        /** The column's river or lake level (RIVER, LAKE; for the riffle check between neighbours). */
        public int level;
        /** Scratch for the lake share (TerrainPlanSurface.lakeTopAt). */
        final double[] share = new double[1];
    }

    /**
     * The lowest Y a carver must leave alone in this column because of river water: 8 below the lowest water level
     * within reach (beds are at most 4 deep), 4 below a marsh pool's water, 10 below the deepest lake bed within two
     * cells (federal lakes); Integer.MAX_VALUE where no water is near.
     * Cached per column for the carving thread (carvers ask for every block they cut).
     */
    public static int carveCeiling(TerrainPlanSurface s, int x, int z) {
        CarveCache c = CARVE.get();
        int i = (x & 15) | ((z & 15) << 4);
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        if (c.owner[i] != s || c.key[i] != key) {
            int ceiling = Integer.MAX_VALUE;
            if (s.rivers != null) {
                s.rivers.column(x, z, c.col);
                if (c.col.kind != RiverNetwork.Column.NONE) ceiling = c.col.lowLevel - 8;
            }
            if (ceiling == Integer.MAX_VALUE && s.poolAt(x, z)) ceiling = POOL_WATER - 4;
            if (s.layers != null) {
                double floor = s.lakeFloorNear(x + 0.5, z + 0.5);
                if (floor != Double.POSITIVE_INFINITY) ceiling = Math.min(ceiling, (int) Math.floor(floor) - 10);
            }
            c.ceiling[i] = ceiling;
            c.key[i] = key;
            c.owner[i] = s;
        }
        return c.ceiling[i];
    }

    private static final ThreadLocal<CarveCache> CARVE = ThreadLocal.withInitial(CarveCache::new);

    private static final class CarveCache {
        final long[] key = new long[256];
        final int[] ceiling = new int[256];
        final TerrainPlanSurface[] owner = new TerrainPlanSurface[256];
        final RiverNetwork.Column col = new RiverNetwork.Column();
    }

    /** RIM: the highest lake top among the four neighbours inside a lake's cells, if it stands over this column's top. */
    private static boolean rim(TerrainPlanSurface s, int x, int z, int top, Edit e) {
        int near = s.lakeNear(x + 0.5, z + 0.5);
        if (near == Integer.MIN_VALUE || top >= near) return false;
        int wall = Integer.MIN_VALUE;
        for (int d = 0; d < 4; d++) {
            int lake = s.lakeTopAt(x + 0.5 + (d == 0 ? 1 : d == 1 ? -1 : 0), z + 0.5 + (d == 2 ? 1 : d == 3 ? -1 : 0), e.share);
            if (lake != Integer.MIN_VALUE && e.share[0] > 0 && lake > top) wall = Math.max(wall, lake);
        }
        if (wall == Integer.MIN_VALUE) return false;
        e.kind = RIM;
        e.ground = wall;
        e.sealTo = top - 2;
        return true;
    }

    public static void plan(TerrainPlanSurface s, int x, int z, int top, RiverNetwork.Column col, Edit e) {
        e.kind = NONE;
        e.ground = top;
        e.water = Integer.MIN_VALUE;
        e.sealTo = Integer.MAX_VALUE;
        e.level = Integer.MIN_VALUE;
        int wc = s.waterClass(x, z);
        // open water (ground under the sea's top water block) keeps its own fill; dry ground inside a sea or estuary
        // cell takes the river rules, so a river is cut all the way to the water instead of stopping at the cell edge
        if ((wc == 1 || wc == 2) && top < RiverNetwork.SEA_WATER_TOP) return;
        if (s.layers != null) {
            int lake = s.lakeTopAt(x + 0.5, z + 0.5, e.share);
            if (lake != Integer.MIN_VALUE && e.share[0] > 0 && top < lake) {
                e.kind = LAKE;
                e.water = lake;
                e.level = lake;
                e.sealTo = top - 2;
                return;
            }
        }
        if (s.rivers != null) s.rivers.column(x, z, col);
        else col.kind = RiverNetwork.Column.NONE;
        if (col.kind == RiverNetwork.Column.RIVER) {
            int bed = Math.min(top, col.level - col.depth);
            e.kind = RIVER;
            e.ground = bed;
            e.water = col.level;
            e.level = col.level;
            e.sealTo = bed - 2;
            return;
        }
        if (col.kind == RiverNetwork.Column.BANK) {
            e.kind = BANK;
            e.ground = Math.max(col.level + 1, Math.min(top, col.maxTop));
            e.sealTo = col.lowLevel - 6;
            if (col.level > POOL_WATER) return;      // a pool here would sit below the river beside it
        }
        if (top == MARSH_TOP && s.poolAt(x, z)) {
            e.kind = POOL;
            e.ground = POOL_WATER - 1;
            e.water = POOL_WATER;
            e.sealTo = POOL_WATER - 3;
        }
        // federal lakes: dry ground outside the lake's cells, beside water inside them, stands at least at its top
        if (e.kind == NONE && s.layers != null && e.share[0] == 0) rim(s, x, z, top, e);
    }
}
