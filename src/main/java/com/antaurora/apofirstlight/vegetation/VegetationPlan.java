package com.antaurora.apofirstlight.vegetation;

import com.antaurora.apofirstlight.tree.ForestPlan;
import com.antaurora.apofirstlight.worldgen.home.ForestHomeRuntime;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import com.antaurora.apofirstlight.worldgen.terrain.v2.HydroQuery;
import com.antaurora.apofirstlight.worldgen.terrain.v2.PlanNoise;
import com.antaurora.apofirstlight.worldgen.terrain.v2.RiverChannels;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AFL vegetation P2a (docs/gameplay/vegetation_v1_design.md 14; C reviewed 2026-10-24): which mesh plant grows on which
 * ground column of an AFL overworld. Pure, like ForestPlan: it reads the Terrain V2 plan (water, rivers, slope), the mesh
 * forest's crowns (ForestPlan, its own pure plan) and the roads' and towns' works (pure too), never a block, so every chunk
 * computes the same plants in any generation order. The feature (VegetationFeature) then checks the real ground.
 * <ul>
 * <li>Community: the biome's cover (the share of ground columns with a plant) and species weights (14.4).</li>
 * <li>Whether a column has a plant: the cover times a 12 m cover noise (bare spots and thick tufts, their edges thinning
 * out), dithered by the column's hash.</li>
 * <li>Which one: each weight times the species' own 30 m patch noise (it grows in drifts) times its site factor (water
 * within {@value #WET_REACH} blocks, the crowns' shade, a steep slope, the road's edge), drawn by a second hash. The
 * flowering forbs ({@link Plant#forb}) also need the shared 48 m flower patch noise ({@link #FORB_PATCH}): between the
 * patches the ground is grass only (v4, doc 22).</li>
 * <li>Excluded: a town's limit (it lays its own lawns later) and the forest home's plot. Roads, driveways, rock cuts and
 * buildings are written before any feature (C's chunk passes): the feature finds no soil there.</li>
 * </ul>
 * P2a had three species blocks and the other eleven stood in as the nearest of them ({@link Plant#standIn}), at the
 * final cover; P2b gives them their own blocks batch by batch (batch 1: the bluestems, the sedge, bracken; batch 2:
 * plantain, dandelion, purple coneflower, black-eyed Susan; batch 3: goldenrod, common reed, blackberry: every plant
 * has its own block now). P4a batch 1 (doc 21) adds switchgrass, smooth cordgrass, the Christmas and cinnamon ferns to
 * the communities ({@link #extend}: the others' weights scaled down, the cover unchanged); P4a batch 2 (doc 23) common
 * mullein, wild mint, common milkweed and poison ivy; batch 3 (doc 24) lowbush blueberry, American elderberry, big
 * sagebrush and American beachgrass.
 */
public final class VegetationPlan {
    /**
     * The plan's generation version (docs/worldgen/worldgen_version_lock_v1.md): a change that moves plants raises it; a
     * world would then keep the version it grows (as ForestPlan.VERSION). 1: P2a. 2: covers times 0.6, a stronger cover
     * noise (user 2026-10-24: one tuft-sized block on two columns of three made a carpet). 3: P4a batch 1's four species.
     * 4: covers times {@value #COVER_V4}, the forbs in shared flower patches and none under the crowns (user 2026-10-24: too
     * dense, flowers everywhere; doc 22). 5: P4a batch 2's four species (doc 23). 6: batch 3's four (doc 24). 7: AFL rivers' wider channel (river R2) counts as
     * water, and under its water only the wetland plants at the shallow margin (Area#riverAllows; user 2026-10-28: plants
     * on the bed of a pool).
     */
    public static final int VERSION = 7;
    /** v4: every community's cover times this (doc 22.3). */
    static final double COVER_V4 = 0.75;
    /**
     * v4 flower patches (doc 22.3): a forb's weight times FORB_PATCH times clamp((n - FORB_FROM) / 0.6, 0, 1) of a shared
     * 48 m noise n, so a third of the ground has none and the forbs are about 15% of temperate grassland's plants (real
     * tallgrass prairie: forbs 10-20% of the biomass, in patches).
     */
    static final double FORB_PATCH = 3.5, FORB_FROM = 0.4;
    /** Wetland plants grow within this many blocks of planned water (and in water a block deep). */
    static final int WET_REACH = 6;
    /**
     * AFL rivers (v7): a plant's base under less water than RIVER_DRY (m) stands dry; a wetland plant under at most
     * RIVER_WET_DEPTH, within RIVER_MARGIN (m) of the water's edge (cattail and reed stands: shallow, slow margins).
     */
    public static final double RIVER_DRY = 0.05, RIVER_WET_DEPTH = 0.5, RIVER_MARGIN = 2;
    /** Planned slope beyond which only the plants that hold on steep ground stay, and fewer. */
    static final double STEEP = 0.45;
    /** Crowns: a column within this share of a tree's crown radius is under it, within EDGE at its edge. */
    static final double UNDER = 0.8, EDGE = 1.2;
    /** How strongly the 12 m cover noise swings the cover (v2: 1.0, thick patches and wide bare ground; v1 0.7). */
    static final double COVER_NOISE = 1.0;
    /** Road edges within this many metres bring the trampled-ground weeds. */
    static final double ROADSIDE = 10;

    /** TIDAL: the low marsh's edge, within 3 blocks of water and in water a block deep, most at the water (cordgrass). */
    private static final int WATER = 0, MOIST = 1, DRY = 2, WET = 3, TIDAL = 4;
    private static final int OPEN = 0, SHADE = 1, BORDER = 2;

    /**
     * The first batch (docs/gameplay/vegetation_v1_design.md 3, 14.3): water need (any but not in water, moist, dry,
     * wet: within WET_REACH of water and in water a block deep), light (open ground, shade, a crown's edge), whether it
     * holds on steep ground, whether trampled ground suits it; the block it stands in as until its own exists.
     */
    public enum Plant {
        BLUEGRASS(WATER, OPEN, false, false, MeshPlantBlock.Species.BLUEGRASS),
        BIG_BLUESTEM(MOIST, OPEN, false, false, MeshPlantBlock.Species.BIG_BLUESTEM),
        LITTLE_BLUESTEM(DRY, OPEN, true, false, MeshPlantBlock.Species.LITTLE_BLUESTEM),
        PENNSYLVANIA_SEDGE(WATER, SHADE, true, false, MeshPlantBlock.Species.PENNSYLVANIA_SEDGE),
        BRACKEN_FERN(WATER, SHADE, false, false, MeshPlantBlock.Species.BRACKEN_FERN),
        CATTAIL(WET, OPEN, false, false, MeshPlantBlock.Species.CATTAIL),
        COMMON_REED(WET, OPEN, false, false, MeshPlantBlock.Species.COMMON_REED),
        YARROW(DRY, OPEN, false, false, MeshPlantBlock.Species.YARROW),
        PLANTAIN(WATER, OPEN, false, true, MeshPlantBlock.Species.PLANTAIN),
        DANDELION(WATER, OPEN, false, true, MeshPlantBlock.Species.DANDELION),
        CONEFLOWER(WATER, OPEN, false, false, MeshPlantBlock.Species.PURPLE_CONEFLOWER),
        BLACK_EYED_SUSAN(WATER, OPEN, false, false, MeshPlantBlock.Species.BLACK_EYED_SUSAN),
        GOLDENROD(MOIST, OPEN, false, false, MeshPlantBlock.Species.CANADA_GOLDENROD),
        BLACKBERRY(WATER, BORDER, false, false, MeshPlantBlock.Species.ALLEGHENY_BLACKBERRY),
        // P4a batch 1 (doc 20.2, 21)
        SWITCHGRASS(MOIST, OPEN, false, false, MeshPlantBlock.Species.SWITCHGRASS),
        SMOOTH_CORDGRASS(TIDAL, OPEN, false, false, MeshPlantBlock.Species.SMOOTH_CORDGRASS),
        CHRISTMAS_FERN(WATER, SHADE, true, false, MeshPlantBlock.Species.CHRISTMAS_FERN),
        CINNAMON_FERN(MOIST, SHADE, false, false, MeshPlantBlock.Species.CINNAMON_FERN),
        // P4a batch 2 (doc 20.2, 23): the mullein of dry, trodden ground (roadsides, gravel), the mint of moist ground, the
        // poison ivy at the crowns' edges as the blackberry
        COMMON_MULLEIN(DRY, OPEN, false, true, MeshPlantBlock.Species.COMMON_MULLEIN),
        WILD_MINT(MOIST, OPEN, false, false, MeshPlantBlock.Species.WILD_MINT),
        COMMON_MILKWEED(WATER, OPEN, false, false, MeshPlantBlock.Species.COMMON_MILKWEED),
        POISON_IVY(WATER, BORDER, false, false, MeshPlantBlock.Species.POISON_IVY),
        // P4a batch 3 (doc 20.2, 24): the blueberry of dry ledges under open crowns, the elderberry of moist wood edges, the
        // sagebrush of dry open slopes, the beachgrass of the dunes (on sand too: MeshPlantBlock#mayPlaceOn)
        LOWBUSH_BLUEBERRY(DRY, BORDER, true, false, MeshPlantBlock.Species.LOWBUSH_BLUEBERRY),
        AMERICAN_ELDERBERRY(MOIST, BORDER, false, false, MeshPlantBlock.Species.AMERICAN_ELDERBERRY),
        BIG_SAGEBRUSH(DRY, OPEN, true, false, MeshPlantBlock.Species.BIG_SAGEBRUSH),
        AMERICAN_BEACHGRASS(DRY, OPEN, false, false, MeshPlantBlock.Species.AMERICAN_BEACHGRASS);

        final int water, light;
        final boolean steep, trampled;
        /** The block that grows for it in P2a (its own block once it has one). */
        public final MeshPlantBlock.Species standIn;

        Plant(int water, int light, boolean steep, boolean trampled, MeshPlantBlock.Species standIn) {
            this.water = water;
            this.light = light;
            this.steep = steep;
            this.trampled = trampled;
            this.standIn = standIn;
        }

        public boolean wet() { return water == WET || water == TIDAL; }

        /** A flowering forb (not a grass, sedge, fern or shrub): grows only in the flower patches, never under crowns (v4). */
        public boolean forb() {
            return switch (this) {
                case YARROW, PLANTAIN, DANDELION, CONEFLOWER, BLACK_EYED_SUSAN, GOLDENROD, COMMON_MULLEIN, WILD_MINT, COMMON_MILKWEED -> true;
                default -> false;
            };
        }
    }

    /** A biome's community: cover (share of ground columns with a plant) and weights in Plant order. */
    public record Community(double cover, double[] weights) {
        boolean trampled() {
            for (Plant p : Plant.values()) if (p.trampled && weights[p.ordinal()] > 0) return true;
            return false;
        }
    }

    private static final Map<String, Community> COMMUNITIES = new HashMap<>();

    private static void community(String biome, double cover, Object... weights) {
        double[] w = new double[Plant.values().length];
        for (int i = 0; i < weights.length; i += 2) w[((Plant) weights[i]).ordinal()] = ((Number) weights[i + 1]).doubleValue();
        COMMUNITIES.put(biome, new Community(cover * COVER_V4, w));
    }

    /** Adds species to a biome's community (doc 20.2): the weights already there scaled down so all still sum to 1. */
    private static void extend(String biome, Object... weights) {
        Community c = COMMUNITIES.get(biome);
        double added = 0;
        for (int i = 1; i < weights.length; i += 2) added += ((Number) weights[i]).doubleValue();
        double[] w = c.weights.clone();
        for (int i = 0; i < w.length; i++) w[i] *= 1 - added;
        for (int i = 0; i < weights.length; i += 2) w[((Plant) weights[i]).ordinal()] = ((Number) weights[i + 1]).doubleValue();
        COMMUNITIES.put(biome, new Community(c.cover, w));
    }

    static {
        // docs/gameplay/vegetation_v1_design.md 14.4 (biome ids are apocalypse_firstlight's); v2 covers are v1's times 0.6
        community("temperate_grassland", 0.45, Plant.BIG_BLUESTEM, 0.25, Plant.BLUEGRASS, 0.25, Plant.LITTLE_BLUESTEM, 0.15,
                Plant.GOLDENROD, 0.08, Plant.BLACK_EYED_SUSAN, 0.07, Plant.YARROW, 0.07, Plant.CONEFLOWER, 0.05, Plant.DANDELION, 0.04, Plant.PLANTAIN, 0.04);
        community("wet_prairie", 0.48, Plant.BIG_BLUESTEM, 0.30, Plant.CATTAIL, 0.20, Plant.BLUEGRASS, 0.15, Plant.COMMON_REED, 0.10,
                Plant.GOLDENROD, 0.10, Plant.CONEFLOWER, 0.08, Plant.YARROW, 0.07);
        community("oak_hickory_woodland", 0.27, Plant.PENNSYLVANIA_SEDGE, 0.40, Plant.BLUEGRASS, 0.15, Plant.BRACKEN_FERN, 0.15,
                Plant.BLACKBERRY, 0.10, Plant.GOLDENROD, 0.07, Plant.BLACK_EYED_SUSAN, 0.05, Plant.CONEFLOWER, 0.04, Plant.YARROW, 0.04);
        community("mixed_mesophytic_forest", 0.21, Plant.PENNSYLVANIA_SEDGE, 0.45, Plant.BRACKEN_FERN, 0.25, Plant.BLACKBERRY, 0.15,
                Plant.GOLDENROD, 0.10, Plant.PLANTAIN, 0.05);
        community("ridge_oak_forest", 0.24, Plant.PENNSYLVANIA_SEDGE, 0.45, Plant.LITTLE_BLUESTEM, 0.20, Plant.BRACKEN_FERN, 0.20,
                Plant.BLACKBERRY, 0.10, Plant.YARROW, 0.05);
        community("hemlock_hollow", 0.09, Plant.PENNSYLVANIA_SEDGE, 0.60, Plant.BRACKEN_FERN, 0.40);
        community("riparian_forest", 0.27, Plant.PENNSYLVANIA_SEDGE, 0.30, Plant.CATTAIL, 0.15, Plant.COMMON_REED, 0.15,
                Plant.BLACKBERRY, 0.15, Plant.GOLDENROD, 0.15, Plant.BLUEGRASS, 0.10);
        community("coastal_pine_oak_forest", 0.24, Plant.BRACKEN_FERN, 0.35, Plant.PENNSYLVANIA_SEDGE, 0.30, Plant.LITTLE_BLUESTEM, 0.20,
                Plant.BLACKBERRY, 0.15);
        community("coastal_sandy_clearing", 0.21, Plant.LITTLE_BLUESTEM, 0.55, Plant.GOLDENROD, 0.15, Plant.BLACK_EYED_SUSAN, 0.15, Plant.YARROW, 0.15);
        community("coastal_swamp_forest", 0.24, Plant.PENNSYLVANIA_SEDGE, 0.35, Plant.CATTAIL, 0.30, Plant.COMMON_REED, 0.20, Plant.BRACKEN_FERN, 0.15);
        community("tidal_marsh", 0.42, Plant.COMMON_REED, 0.65, Plant.CATTAIL, 0.35);
        community("lakeshore_mixed_forest", 0.24, Plant.PENNSYLVANIA_SEDGE, 0.40, Plant.BRACKEN_FERN, 0.25, Plant.BLACKBERRY, 0.15,
                Plant.GOLDENROD, 0.10, Plant.BLUEGRASS, 0.10);
        community("lake_fen", 0.45, Plant.CATTAIL, 0.35, Plant.COMMON_REED, 0.25, Plant.PENNSYLVANIA_SEDGE, 0.25, Plant.GOLDENROD, 0.15);
        community("montane_conifer_forest", 0.18, Plant.PENNSYLVANIA_SEDGE, 0.50, Plant.BRACKEN_FERN, 0.30, Plant.BLACKBERRY, 0.20);
        community("alpine_meadow", 0.36, Plant.BLUEGRASS, 0.35, Plant.PENNSYLVANIA_SEDGE, 0.25, Plant.YARROW, 0.25, Plant.DANDELION, 0.10, Plant.GOLDENROD, 0.05);
        community("alpine_peaks", 0.03, Plant.PENNSYLVANIA_SEDGE, 0.60, Plant.YARROW, 0.40);
        community("plateau_steppe", 0.27, Plant.LITTLE_BLUESTEM, 0.55, Plant.BIG_BLUESTEM, 0.15, Plant.YARROW, 0.15, Plant.GOLDENROD, 0.15);
        community("canyon_badlands", 0.05, Plant.LITTLE_BLUESTEM, 0.70, Plant.GOLDENROD, 0.30);
        // no fallout_barrens: a Terrain V2 world (the only kind with a plan) never makes it (doc 23.5)
        // P4a batch 1 (doc 20.2; cordgrass takes the low marsh at the water, the reed and cattail the higher ground)
        extend("temperate_grassland", Plant.SWITCHGRASS, 0.08);
        extend("wet_prairie", Plant.SWITCHGRASS, 0.12);
        extend("riparian_forest", Plant.SWITCHGRASS, 0.05);
        extend("tidal_marsh", Plant.SMOOTH_CORDGRASS, 0.45);
        extend("oak_hickory_woodland", Plant.CHRISTMAS_FERN, 0.08);
        extend("mixed_mesophytic_forest", Plant.CHRISTMAS_FERN, 0.15);
        extend("hemlock_hollow", Plant.CHRISTMAS_FERN, 0.25, Plant.CINNAMON_FERN, 0.25);
        extend("lakeshore_mixed_forest", Plant.CHRISTMAS_FERN, 0.08);
        extend("coastal_swamp_forest", Plant.CINNAMON_FERN, 0.15);
        extend("lake_fen", Plant.CINNAMON_FERN, 0.10);
        // P4a batch 2 (doc 20.2, 23)
        extend("temperate_grassland", Plant.COMMON_MILKWEED, 0.05, Plant.COMMON_MULLEIN, 0.02);
        extend("wet_prairie", Plant.WILD_MINT, 0.06, Plant.COMMON_MILKWEED, 0.04);
        extend("oak_hickory_woodland", Plant.POISON_IVY, 0.05);
        extend("mixed_mesophytic_forest", Plant.POISON_IVY, 0.05);
        extend("riparian_forest", Plant.WILD_MINT, 0.06, Plant.POISON_IVY, 0.05);
        extend("coastal_pine_oak_forest", Plant.POISON_IVY, 0.05);
        extend("coastal_sandy_clearing", Plant.COMMON_MULLEIN, 0.05);
        extend("coastal_swamp_forest", Plant.POISON_IVY, 0.05);
        extend("lake_fen", Plant.WILD_MINT, 0.08);
        // P4a batch 3 (doc 20.2, 24)
        extend("ridge_oak_forest", Plant.LOWBUSH_BLUEBERRY, 0.15);
        extend("coastal_pine_oak_forest", Plant.LOWBUSH_BLUEBERRY, 0.15);
        extend("lakeshore_mixed_forest", Plant.LOWBUSH_BLUEBERRY, 0.06);
        extend("montane_conifer_forest", Plant.LOWBUSH_BLUEBERRY, 0.20);
        extend("alpine_meadow", Plant.LOWBUSH_BLUEBERRY, 0.10);
        extend("riparian_forest", Plant.AMERICAN_ELDERBERRY, 0.08);
        extend("plateau_steppe", Plant.BIG_SAGEBRUSH, 0.30);
        extend("canyon_badlands", Plant.BIG_SAGEBRUSH, 0.35);
        extend("coastal_sandy_clearing", Plant.AMERICAN_BEACHGRASS, 0.25);
    }

    /** The community of an apocalypse_firstlight biome (its path), or null: nothing grows there (sea, beach, lake ...). */
    public static Community community(String biome) { return biome == null ? null : COMMUNITIES.get(biome); }

    /** The biomes the feature is hung on (the biome modifier's list). */
    public static List<String> biomes() { return COMMUNITIES.keySet().stream().sorted().toList(); }

    // -------------------------------------------------------------------------------------------------------- plan

    public final TerrainPlanSurface surface;
    public final long seed;
    /** The mesh forest's plan when it grows (crown shade), else null. */
    final ForestPlan forest;
    /** The roads' and towns' works of the world, or null (no road network). */
    final RoadWorks works;
    private final long noiseSeed;

    public VegetationPlan(TerrainPlanSurface surface, long seed, ForestPlan forest, RoadWorks works) {
        this.surface = surface;
        this.seed = seed;
        this.forest = forest;
        this.works = works;
        this.noiseSeed = PlanNoise.mix(seed ^ 0x56656765746174L);
    }

    /**
     * Whether the roads keep a column of their graded turf bare (RoadWorks#bare: under the pavement's edge, a junction's
     * sight triangles, a pad; C's road guard refuses plants there). Dev statistics only.
     */
    public boolean roadBare(int x, int z) {
        return works != null && works.bare(x, z);
    }

    /** Planned water at a column: the mesh forest's test (ForestPlan#site); a swamp is not water. */
    boolean water(int x, int z) {
        double h = surface.heightAt(x, z);
        return surface.waterAt(x, z) || surface.seaFloodAt(x, z, h) || h < 63.5;
    }

    long hash(int x, int z, int salt) {
        return PlanNoise.mix(noiseSeed ^ PlanNoise.mix(x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ (long) salt << 48));
    }

    static double unit(long h) { return (h >>> 11) * 0x1.0p-53; }

    /** A chunk's columns ready to choose: planned water around it, the crowns over it, the excluded ground. */
    public Area area(int cx, int cz) { return new Area(this, cx, cz); }

    /**
     * One chunk's site data (the feature's and the dev statistics' unit): planned water over the chunk and
     * {@value #WET_REACH} blocks round it, each column's light under the mesh forest's crowns (the trees rooted in this
     * chunk and its eight neighbours: crowns reach ForestPlan.REACH at most).
     */
    public static final class Area {
        static final int M = WET_REACH, SIDE = 16 + 2 * M;
        final VegetationPlan plan;
        final int x0, z0;
        final boolean[] water = new boolean[SIDE * SIDE];
        final byte[] light = new byte[256];
        final ForestHomeRuntime.Active home;
        /** The world's wider river channel (RiverChannels; null without AFL rivers) and a point for its queries. */
        final HydroQuery hydro;
        private final HydroQuery.Point point = new HydroQuery.Point();

        Area(VegetationPlan plan, int cx, int cz) {
            this.plan = plan;
            x0 = cx << 4;
            z0 = cz << 4;
            for (int i = 0; i < SIDE; i++)
                for (int j = 0; j < SIDE; j++) water[i * SIDE + j] = plan.water(x0 - M + i, z0 - M + j);
            if (plan.forest != null) {
                for (int dcx = -1; dcx <= 1; dcx++)
                    for (int dcz = -1; dcz <= 1; dcz++)
                        for (ForestPlan.Tree t : plan.forest.trees(cx + dcx, cz + dcz)) {
                            double r = Math.max(1, t.radius());
                            for (int i = 0; i < 16; i++)
                                for (int j = 0; j < 16; j++) {
                                    double d = Math.hypot(x0 + i + 0.5 - (t.x() + 0.5), z0 + j + 0.5 - (t.z() + 0.5)) / r;
                                    int k = d < UNDER ? SHADE : d < EDGE ? BORDER : OPEN;
                                    // under beats the edge beats open
                                    if (k == SHADE || k == BORDER && light[i * 16 + j] == OPEN) light[i * 16 + j] = (byte) k;
                                }
                        }
            }
            hydro = RiverChannels.active(plan.seed);
            ForestHomeRuntime.Active a = ForestHomeRuntime.active();
            home = a != null && a.seed() == plan.seed ? a : null;
        }

        /** Planned water at a column of the chunk or WET_REACH round it. */
        boolean waterAt(int x, int z) {
            int i = x - x0 + M, j = z - z0 + M;
            return i >= 0 && j >= 0 && i < SIDE && j < SIDE ? water[i * SIDE + j] : plan.water(x, z);
        }

        /** Blocks to the nearest planned water (0 on it), WET_REACH + 1 beyond the reach; rivers by their edge. */
        double waterDistance(int x, int z) {
            if (waterAt(x, z)) return 0;
            double best = WET_REACH + 1;
            for (int dx = -WET_REACH; dx <= WET_REACH; dx++)
                for (int dz = -WET_REACH; dz <= WET_REACH; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d < best && waterAt(x + dx, z + dz)) best = d;
                }
            if (plan.surface.rivers != null) {
                double e = plan.surface.rivers.nearestEdge(x + 0.5, z + 0.5, WET_REACH + 1, new double[1]);
                if (e < best) best = Math.max(0, e);
            }
            // river R2: in a world with AFL rivers the channel is wider than the plan's, and its water open air in the
            // chunk (the AFL water fills it live), so the wider edge counts: no dry ground's plants on its bed
            if (hydro != null && hydro.near(x + 0.5, z + 0.5, WET_REACH + 1, point) && point.edge < best) best = Math.max(0, point.edge);
            return best;
        }

        /**
         * Whether plant p may stand at (x, y, z) under an AFL river's water. The channel is open air when the feature runs
         * (the AFL water fills it live), so the feature's own rule for vanilla water (a wetland plant in water a block deep)
         * never sees it: above the water any plant; under it only a wetland plant, in water at most {@link #RIVER_WET_DEPTH}
         * deep within {@link #RIVER_MARGIN} of the edge (the slow margin: never mid-stream, never in a pool).
         */
        public boolean riverAllows(Plant p, int x, int y, int z) {
            if (hydro == null || !hydro.near(x + 0.5, z + 0.5, 0, point)) return true;
            double under = point.eta - y;
            if (under < RIVER_DRY) return true;
            return p.wet() && under <= RIVER_WET_DEPTH && point.edge >= -RIVER_MARGIN;
        }

        /** A town's limit (its own lawns later) or the forest home's plot (C, 2026-10-24: the plot itself, not its tree clearance). */
        public boolean excluded(int x, int z) {
            return inTown(x, z) || inHome(x, z);
        }

        /** Inside a town's limit. */
        public boolean inTown(int x, int z) {
            return plan.works != null && plan.works.towns().clear(x + 0.5, z + 0.5, 0);
        }

        /** On the forest home's plot. */
        public boolean inHome(int x, int z) {
            return home != null && home.plot().distance(x + 0.5, z + 0.5) < 0;
        }

        /** Metres from the nearest road's edge (motorways, county roads, ramps; RoadWorks' roadside formula). */
        double roadDistance(int x, int z) {
            if (plan.works == null) return Double.POSITIVE_INFINITY;
            RoadWorks.Hit h = plan.works.hit(x + 0.5, z + 0.5, ROADSIDE + 12);
            return h == null ? Double.POSITIVE_INFINITY : Math.hypot(Math.max(0, h.v() - h.road().section.edge), h.beyond());
        }

        /** The plant of a column of this chunk in a biome (its apocalypse_firstlight path), or null. */
        public Plant choose(int x, int z, String biome) {
            Community c = community(biome);
            if (c == null || c.cover <= 0) return null;
            boolean steep = plan.surface.slopeAt(x, z) > STEEP;
            double n = PlanNoise.fbm(plan.noiseSeed, x, z, 12, 2, 0.5, 1);
            double p = c.cover * (1 + COVER_NOISE * Math.max(-1.4, Math.min(1.4, n)));
            if (steep) p *= 0.4;
            if (unit(plan.hash(x, z, 1)) >= p) return null;
            double dw = waterDistance(x, z);
            int lit = light[(x - x0) * 16 + (z - z0)];
            double road = c.trampled() ? roadDistance(x, z) : Double.POSITIVE_INFINITY;
            Plant[] all = Plant.values();
            double[] w = new double[all.length];
            double total = 0, flowers = -1;
            for (Plant s : all) {
                double base = c.weights[s.ordinal()];
                if (base <= 0) continue;
                double f = site(s, dw, lit, steep, road);
                if (f > 0 && s.forb()) {
                    if (flowers < 0) flowers = FORB_PATCH * Math.max(0, Math.min(1, (PlanNoise.fbm(plan.noiseSeed, x, z, 48, 2, 0.5, 9) - FORB_FROM) / 0.6));
                    f *= flowers;
                }
                if (f <= 0) continue;
                w[s.ordinal()] = base * f * Math.max(0.05, 1 + 0.9 * PlanNoise.fbm(plan.noiseSeed, x, z, 30, 2, 0.5, 10 + s.ordinal()));
                total += w[s.ordinal()];
            }
            if (total <= 0) return null;
            double u = unit(plan.hash(x, z, 2)) * total;
            for (Plant s : all) {
                if (w[s.ordinal()] <= 0) continue;
                u -= w[s.ordinal()];
                if (u < 0) return s;
            }
            return null;
        }

        /** A species' site factor: water within reach, crown light (a forb: none under the crowns), a steep slope, the road's edge. */
        static double site(Plant s, double water, int light, boolean steep, double road) {
            double f;
            double near = Math.max(0, 1 - water / (WET_REACH + 1));
            if (s.water == WET) f = water > WET_REACH ? 0 : water == 0 ? 1.5 : 2 * near;
            else if (s.water == TIDAL) f = water > 3 ? 0 : water == 0 ? 2.5 : 2.5 * (1 - water / 4);
            else if (water == 0) return 0;
            else if (s.water == MOIST) f = 1 + 0.6 * near;
            else if (s.water == DRY) f = water > WET_REACH ? 1 : 0.3 + 0.7 * (1 - near);
            else f = 1;
            f *= switch (s.light) {
                case SHADE -> light == SHADE ? 2 : light == BORDER ? 1.3 : 0.6;
                case BORDER -> light == SHADE ? 0.4 : light == BORDER ? 2.5 : 0.8;
                default -> light == SHADE ? (s.forb() ? 0 : 0.15) : light == BORDER ? 0.6 : 1;
            };
            if (steep && !s.steep) return 0;
            if (s.trampled) f *= 1 + 3 * Math.max(0, 1 - road / ROADSIDE);
            return f;
        }
    }
}
