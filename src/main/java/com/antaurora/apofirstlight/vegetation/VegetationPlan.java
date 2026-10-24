package com.antaurora.apofirstlight.vegetation;

import com.antaurora.apofirstlight.tree.ForestPlan;
import com.antaurora.apofirstlight.worldgen.home.ForestHomeRuntime;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import com.antaurora.apofirstlight.worldgen.terrain.v2.PlanNoise;
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
 * within {@value #WET_REACH} blocks, the crowns' shade, a steep slope, the road's edge), drawn by a second hash.</li>
 * <li>Excluded: a town's limit (it lays its own lawns later) and the forest home's plot. Roads, driveways, rock cuts and
 * buildings are written before any feature (C's chunk passes): the feature finds no soil there.</li>
 * </ul>
 * P2a had three species blocks and the other eleven stood in as the nearest of them ({@link Plant#standIn}), at the
 * final cover; P2b gives them their own blocks batch by batch (batch 1: the bluestems, the sedge, bracken; batch 2:
 * plantain, dandelion, purple coneflower, black-eyed Susan; batch 3: goldenrod, common reed, blackberry: every plant
 * has its own block now).
 */
public final class VegetationPlan {
    /**
     * The plan's generation version (docs/worldgen/worldgen_version_lock_v1.md): a change that moves plants raises it; a
     * world would then keep the version it grows (as ForestPlan.VERSION). 1: P2a. 2: covers times 0.6, a stronger cover
     * noise (user 2026-10-24: one tuft-sized block on two columns of three made a carpet).
     */
    public static final int VERSION = 2;
    /** Wetland plants grow within this many blocks of planned water (and in water a block deep). */
    static final int WET_REACH = 6;
    /** Planned slope beyond which only the plants that hold on steep ground stay, and fewer. */
    static final double STEEP = 0.45;
    /** Crowns: a column within this share of a tree's crown radius is under it, within EDGE at its edge. */
    static final double UNDER = 0.8, EDGE = 1.2;
    /** How strongly the 12 m cover noise swings the cover (v2: 1.0, thick patches and wide bare ground; v1 0.7). */
    static final double COVER_NOISE = 1.0;
    /** Road edges within this many metres bring the trampled-ground weeds. */
    static final double ROADSIDE = 10;

    private static final int WATER = 0, MOIST = 1, DRY = 2, WET = 3;
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
        BLACKBERRY(WATER, BORDER, false, false, MeshPlantBlock.Species.ALLEGHENY_BLACKBERRY);

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

        public boolean wet() { return water == WET; }
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
        COMMUNITIES.put(biome, new Community(cover, w));
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
        community("fallout_barrens", 0.07, Plant.PLANTAIN, 0.30, Plant.DANDELION, 0.30, Plant.GOLDENROD, 0.40);
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
            return best;
        }

        /** A town's limit (its own lawns later) or the forest home's plot (C, 2026-10-24: the plot itself, not its tree clearance). */
        public boolean excluded(int x, int z) {
            if (plan.works != null && plan.works.towns().clear(x + 0.5, z + 0.5, 0)) return true;
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
            double total = 0;
            for (Plant s : all) {
                double base = c.weights[s.ordinal()];
                if (base <= 0) continue;
                double f = site(s, dw, lit, steep, road);
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

        /** A species' site factor: water within reach, crown light, a steep slope, the road's edge. */
        static double site(Plant s, double water, int light, boolean steep, double road) {
            double f;
            double near = Math.max(0, 1 - water / (WET_REACH + 1));
            if (s.water == WET) f = water > WET_REACH ? 0 : water == 0 ? 1.5 : 2 * near;
            else if (water == 0) return 0;
            else if (s.water == MOIST) f = 1 + 0.6 * near;
            else if (s.water == DRY) f = water > WET_REACH ? 1 : 0.3 + 0.7 * (1 - near);
            else f = 1;
            f *= switch (s.light) {
                case SHADE -> light == SHADE ? 2 : light == BORDER ? 1.3 : 0.6;
                case BORDER -> light == SHADE ? 0.4 : light == BORDER ? 2.5 : 0.8;
                default -> light == SHADE ? 0.15 : light == BORDER ? 0.6 : 1;
            };
            if (steep && !s.steep) return 0;
            if (s.trampled) f *= 1 + 3 * Math.max(0, 1 - road / ROADSIDE);
            return f;
        }
    }
}
