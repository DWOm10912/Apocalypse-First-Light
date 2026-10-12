package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Species;
import com.antaurora.apofirstlight.worldgen.terrain.v2.EcologyBiomes;
import com.antaurora.apofirstlight.worldgen.terrain.v2.PlanNoise;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mesh Trees P1 (docs/rendering/mesh_trees_p1_forest.md, C/D): where the natural white oaks and white pines of an
 * {@code AFL_TREE_*} test world stand. Pure Java over the Terrain V2 plan (biome, planned ground, water, rivers), never
 * a block: every chunk computes the same trees for any position, in any generation order, without loading anything.
 * <ul>
 * <li>Candidates: one per {@value #GRID} x {@value #GRID} block cell at a hashed spot; it is kept with the stand's
 * density (trees per chunk, modulated by a grove noise so stands have gaps and thickets), and it has to be a mesh
 * forest site: a covered biome at its quart (the game's own biome rule), no open water, a gentle planned slope, no
 * river within {@value #BANK} blocks, no biome that keeps its vanilla trees within {@value #BUFFER} blocks, no
 * registered exclusion.</li>
 * <li>Spacing (Matern type II, deterministic): a kept candidate stands only if no kept candidate with a higher priority
 * (a hash) is closer than the pair's spacing, {@code k x (crown radius A + crown radius B)} with the stands' mean k, at
 * least {@value #MIN_TRUNK} m. The rule only reads candidates, never whether a neighbour itself stands, so it needs no
 * recursion and two chunks always agree.</li>
 * <li>Species from the stand's pine share (a species-patch noise); variant 0..15 and facing from the hash.</li>
 * </ul>
 * The feature (ForestFeature) asks for the trees rooted in one chunk and then checks the real ground.
 */
public final class ForestPlan {
    public static final int GRID = 4;
    /**
     * Trees reach at most this far (blocks) sideways from their root (TreeIndex.REACH; the offline check holds every
     * layout to it): a tree rooted in a chunk writes only into that chunk and its eight neighbours, which is what the
     * FEATURES stage lets a chunk write (WorldGenRegion write radius 1).
     */
    public static final int REACH = 12;
    /** Blocks between a mesh tree and the nearest column of a biome that keeps its vanilla trees. */
    public static final int BUFFER = 18;
    /** Blocks between a trunk and river water. */
    public static final int BANK = 4;
    public static final double MIN_TRUNK = 3.5, MAX_PLAN_SLOPE = 0.45;
    static final int SALT = 0x6D65, NEIGHBOURS = 6;

    /**
     * A mesh forest stand. density: kept candidates per chunk before spacing; target: trees per chunk after spacing
     * (what the offline check measures); pine share; spacing factor k; grove: 0 = forest (noise thins and thickens, mean
     * one), above 0 = open land where only the noise peaks carry groves (the threshold).
     */
    public record Stand(String name, double density, double target, double pineShare, double spacing, double grove) {}

    /** By EcologyBiomes index; null: not a mesh forest biome. */
    static final Stand[] STANDS = new Stand[EcologyBiomes.IDS.length];
    /** Biomes that keep their vanilla trees (mesh trees keep BUFFER blocks away from them). */
    static final boolean[] VANILLA_TREES = new boolean[EcologyBiomes.IDS.length];

    static {
        // the sample trees are mature with wide crowns (oak radius 7-10 m, pine 6-8.4 m): a closed stand is about one
        // tree per chunk (40 / ha), so the crown cover, not the stem count, sets the targets (offline check, 2026-10-11)
        STANDS[EcologyBiomes.GRASSLAND] = new Stand("open white oak (grassland groves)", 3.0, 0.10, 0.0, 0.75, 0.6);
        STANDS[EcologyBiomes.WOODLAND] = new Stand("white oak woodland", 6.0, 0.85, 0.08, 0.55, 0);
        STANDS[EcologyBiomes.RIDGE_FOREST] = new Stand("ridge oak, some pine", 4.0, 0.65, 0.25, 0.6, 0);
        STANDS[EcologyBiomes.HEMLOCK_HOLLOW] = new Stand("white pine forest", 8.0, 1.1, 0.88, 0.5, 0);
        STANDS[EcologyBiomes.COASTAL_PINE] = new Stand("mixed pine-oak", 7.0, 1.0, 0.55, 0.55, 0);
        STANDS[EcologyBiomes.COASTAL_CLEARING] = new Stand("sandy clearing, scattered", 2.0, 0.12, 0.55, 0.8, 0.6);
        VANILLA_TREES[EcologyBiomes.SLOPE_FOREST] = true;
        VANILLA_TREES[EcologyBiomes.RIPARIAN_FOREST] = true;
        VANILLA_TREES[EcologyBiomes.SWAMP_FOREST] = true;
    }

    /** The biomes whose vanilla tree features the dev biome modifier replaces (EcologyBiomes ids). */
    public static List<String> coveredBiomes() {
        var out = new ArrayList<String>();
        for (int b = 0; b < STANDS.length; b++) if (STANDS[b] != null) out.add(EcologyBiomes.IDS[b]);
        return out;
    }

    public static Stand stand(int biome) { return biome >= 0 && biome < STANDS.length ? STANDS[biome] : null; }

    /** A planned tree: root column, species, variant, quarter turns, priority (higher wins), biome. */
    public record Tree(int x, int z, Species species, int variant, int turns, long priority, int biome, float radius) {}

    /**
     * An area where no natural mesh tree may stand (future roads, highway corridors, sites). Pure: it must answer from
     * the seed and coordinates only, so every chunk agrees. None is registered in P1.
     */
    public interface Exclusion {
        boolean excludes(long seed, int x, int z, int radius);
    }

    private static final List<Exclusion> EXCLUSIONS = new CopyOnWriteArrayList<>();

    public static void exclude(Exclusion e) { EXCLUSIONS.add(e); }

    public static int exclusions() { return EXCLUSIONS.size(); }

    // ------------------------------------------------------------------------------------------- shared cells

    /** Cell precedence where trees meet: root 4, wood 3, crown cell carrying leaves 2, volume-only crown 1. */
    public static int rank(TreeShape.Cell cell) {
        if (cell.x == 0 && cell.y == 0 && cell.z == 0) return 4;
        return cell.kind == TreeShape.WOOD ? 3 : cell.carries() ? 2 : 1;
    }

    /**
     * Whether a cell (rank a, species sa) replaces what another tree wrote (rank b, species sb): the higher rank, and
     * between crowns of equal rank the lower species ordinal. A maximum, so the final block of a shared cell is the same
     * in any generation order. Two roots never meet (spacing), equal wood is the same block.
     */
    public static boolean wins(int a, int sa, int b, int sb) {
        return a != b ? a > b : a <= 2 && sa < sb;
    }

    // ------------------------------------------------------------------------------------------------- crowns

    private static volatile float[][] crowns;

    /** Crown radius (m) of a species variant (from its layout, built once). */
    public static float crown(Species s, int variant) {
        float[][] c = crowns;
        if (c == null) {
            c = new float[Species.values().length][TreeShape.VARIANTS];
            for (Species sp : Species.values()) for (int v = 0; v < TreeShape.VARIANTS; v++) c[sp.ordinal()][v] = TreeShape.layout(sp, v, 0).crownRadius;
            crowns = c;
        }
        return c[s.ordinal()][variant];
    }

    // -------------------------------------------------------------------------------------------------- plan

    public final TerrainPlanSurface surface;
    public final long seed;
    private final long noiseSeed;

    public ForestPlan(TerrainPlanSurface surface, long seed) {
        this.surface = surface;
        this.seed = seed;
        this.noiseSeed = PlanNoise.mix(seed ^ 0x4D65736846726EL);
    }

    /** The game's surface biome rule: the ecology of the column's quart (x, z rounded down to multiples of 4). */
    public int biome(int x, int z) {
        int qx = x >> 2, qz = z >> 2;
        Caches c = CACHES.get();
        int i = (qx & 63) | (qz & 63) << 6;
        long key = (long) qx << 32 ^ (qz & 0xFFFFFFFFL);
        if (c.biomeOwner[i] == this && c.biomeKey[i] == key) return c.biome[i];
        int b = EcologyBiomes.biomeAt(surface, qx << 2, qz << 2);
        c.biomeOwner[i] = this;
        c.biomeKey[i] = key;
        c.biome[i] = b;
        return b;
    }

    /** Stand density factor at a point: forests vary about one, open land is zero outside the grove peaks. */
    double grove(Stand st, int x, int z) {
        double n = PlanNoise.fbm(noiseSeed, x, z, st.grove > 0 ? 70 : 56, 2, 0.5, 11);
        if (st.grove > 0) {
            double t = Math.max(0, Math.min(1, (n - st.grove) / 0.7));
            return 4.5 * t * t * (3 - 2 * t);
        }
        return Math.max(0.05, Math.min(1.8, 1 + 0.6 * n));
    }

    double pineShare(Stand st, int x, int z) {
        if (st.pineShare <= 0 || st.pineShare >= 1) return st.pineShare;
        return Math.max(0, Math.min(1, st.pineShare + 0.3 * PlanNoise.fbm(noiseSeed, x, z, 90, 2, 0.5, 12)));
    }

    /** Why a kept candidate is not a mesh forest site (pure), or null. */
    String site(int x, int z, int biome) {
        TerrainPlanSurface s = surface;
        double h = s.heightAt(x, z);
        if (s.waterAt(x, z) || s.seaFloodAt(x, z, h) || h < 63.5) return "plan water";
        if (s.slopeAt(x, z) > MAX_PLAN_SLOPE) return "plan slope";
        if (s.rivers != null) {
            double[] w = new double[1];
            if (s.rivers.nearestEdge(x, z, BANK + 1, w) < BANK) return "river bank";
        }
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            for (int r = BUFFER / 2; r <= BUFFER; r += BUFFER / 2) {
                int b = biome((int) Math.floor(x + r * Math.cos(a)), (int) Math.floor(z + r * Math.sin(a)));
                if (VANILLA_TREES[b]) return "vanilla tree biome near";
            }
        }
        for (Exclusion e : EXCLUSIONS) if (e.excludes(seed, x, z, REACH)) return "exclusion";
        return null;
    }

    /** The planned ground's first open y at a column (the generated terrain follows the plan; offline check N.4). */
    public int top(int x, int z) {
        return (int) Math.round(surface.heightAt(x + 0.5, z + 0.5));
    }

    private static final java.util.Map<TreeShape.Layout, int[]> FLOORS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Per layout: (dx, dz, lowest wood dy) of every column a branch passes, the trunk column left out. */
    static int[] woodFloor(TreeShape.Layout l) {
        int[] done = FLOORS.get(l);
        if (done != null) return done;
        var low = new java.util.HashMap<Integer, Integer>();
        for (var c : l.cells.values()) {
            if (c.kind != TreeShape.WOOD || (c.x == 0 && c.z == 0)) continue;
            low.merge((c.x + 64) << 8 | (c.z + 64), c.y, Math::min);
        }
        int[] out = new int[low.size() * 3];
        int i = 0;
        for (var e : new java.util.TreeMap<>(low).entrySet()) {
            out[i++] = (e.getKey() >> 8) - 64; out[i++] = (e.getKey() & 255) - 64; out[i++] = e.getValue();
        }
        FLOORS.put(l, out);
        return out;
    }

    /** A branch would run into the planned ground (a hillside): the feature would refuse the tree, so the plan does. */
    boolean hillside(int x, int z, Species sp, int variant, int turns) {
        int y = top(x, z);
        int[] f = woodFloor(TreeShape.layout(sp, variant, turns));
        for (int i = 0; i < f.length; i += 3) if (y + f[i + 2] < top(x + f[i], z + f[i + 1])) return true;
        return false;
    }

    private static final Tree NONE = new Tree(0, 0, Species.WHITE_OAK, 0, 0, 0, -1, 0);

    long hash(int gx, int gz, int salt) {
        return PlanNoise.mix(noiseSeed ^ PlanNoise.mix(gx * 0x9E3779B97F4A7C15L ^ gz * 0xC2B2AE3D27D4EB4FL ^ (long) salt << 48 ^ SALT));
    }

    static double unit(long h) { return (h >>> 11) * 0x1.0p-53; }

    /** The kept candidate of a grid cell that is a mesh forest site, or null. */
    public Tree candidate(int gx, int gz) {
        Caches c = CACHES.get();
        int i = (gx & 63) | (gz & 63) << 6;
        long key = (long) gx << 32 ^ (gz & 0xFFFFFFFFL);
        if (c.candOwner[i] == this && c.candKey[i] == key) return c.cand[i] == NONE ? null : c.cand[i];
        Tree t = evaluate(gx, gz, null);
        c.candOwner[i] = this;
        c.candKey[i] = key;
        c.cand[i] = t == null ? NONE : t;
        return t;
    }

    /** Evaluates a grid cell; why (if given) gets the reason a candidate was dropped at [0]. */
    Tree evaluate(int gx, int gz, String[] why) {
        long h = hash(gx, gz, 0);
        int x = gx * GRID + (int) (h & 3), z = gz * GRID + (int) (h >>> 2 & 3);
        int b = biome(x, z);
        Stand st = STANDS[b];
        if (st == null) { if (why != null) why[0] = "not a mesh forest biome"; return null; }
        double keep = st.density * grove(st, x, z) * GRID * GRID / 256.0;
        if (unit(hash(gx, gz, 1)) >= keep) { if (why != null) why[0] = "thinned"; return null; }
        String bad = site(x, z, b);
        if (bad != null) { if (why != null) why[0] = bad; return null; }
        Species sp = unit(hash(gx, gz, 2)) < pineShare(st, x, z) ? Species.WHITE_PINE : Species.WHITE_OAK;
        long v = hash(gx, gz, 3);
        int variant = (int) Math.floorMod(v, TreeShape.VARIANTS), turns = (int) (v >>> 8 & 3);
        if (hillside(x, z, sp, variant, turns)) { if (why != null) why[0] = "branch in the hillside"; return null; }
        return new Tree(x, z, sp, variant, turns, hash(gx, gz, 4), b, crown(sp, variant));
    }

    /** The pair's spacing: the stands' mean factor times the two crown radii, at least MIN_TRUNK. */
    static double spacing(Tree a, Tree b) {
        double k = 0.5 * (STANDS[a.biome].spacing + STANDS[b.biome].spacing);
        return Math.max(MIN_TRUNK, k * (a.radius + b.radius));
    }

    static boolean beats(Tree a, Tree b) {
        if (a.priority != b.priority) return a.priority > b.priority;
        return a.x != b.x ? a.x > b.x : a.z > b.z;
    }

    /** The candidate of this grid cell stands (kept, a site, nothing stronger too close); returns it or null. */
    public Tree standing(int gx, int gz) {
        Tree t = candidate(gx, gz);
        if (t == null) return null;
        for (int dz = -NEIGHBOURS; dz <= NEIGHBOURS; dz++) for (int dx = -NEIGHBOURS; dx <= NEIGHBOURS; dx++) {
            if (dx == 0 && dz == 0) continue;
            Tree o = candidate(gx + dx, gz + dz);
            if (o == null || !beats(o, t)) continue;
            double ddx = o.x - t.x, ddz = o.z - t.z, d = spacing(t, o);
            if (ddx * ddx + ddz * ddz < d * d) return null;
        }
        return t;
    }

    /** The trees rooted in a chunk (its 4 x 4 grid cells). */
    public List<Tree> trees(int cx, int cz) {
        var out = new ArrayList<Tree>(4);
        for (int gz = cz * 4; gz < cz * 4 + 4; gz++) for (int gx = cx * 4; gx < cx * 4 + 4; gx++) {
            Tree t = standing(gx, gz);
            if (t != null) out.add(t);
        }
        return out;
    }

    /** The trees rooted in the chunks of a square (block coordinates, whole chunks). */
    public List<Tree> window(int x0, int z0, int size) {
        var all = new ArrayList<Tree>();
        for (int cz = z0 >> 4; cz < (z0 + size) >> 4; cz++) for (int cx = x0 >> 4; cx < (x0 + size) >> 4; cx++) all.addAll(trees(cx, cz));
        return all;
    }

    // ---------------------------------------------------------------------------------------------- test spots

    /** Test scenarios: open white oak (grassland groves), white pine forest, mixed pine-oak forest. */
    public static final String[] SCENARIOS = {"open_oak", "white_pine", "mixed"};

    /** A test spot: the scenario's biome fills the disc round it, with trees of the kind (offline check and command). */
    public boolean scenario(int k, int x, int z) {
        int want = k == 0 ? EcologyBiomes.GRASSLAND : k == 1 ? EcologyBiomes.HEMLOCK_HOLLOW : EcologyBiomes.COASTAL_PINE;
        int r = k == 1 ? 40 : 64, in = 0, n = 0;
        for (int dz = -r; dz <= r; dz += 16) for (int dx = -r; dx <= r; dx += 16) { n++; if (biome(x + dx, z + dz) == want) in++; }
        if (in < n * (k == 1 ? 0.75 : 0.9)) return false;
        var trees = window(x - r, z - r, 2 * r);
        int pines = 0;
        for (var t : trees) if (t.species == Species.WHITE_PINE) pines++;
        if (k == 0) return trees.size() >= 5;
        if (k == 1) return trees.size() >= 12 && pines >= 0.75 * trees.size();
        return trees.size() >= 30 && pines >= 0.3 * trees.size() && pines <= 0.7 * trees.size();
    }

    /** The nearest test spot of a scenario on a 32 m lattice, square rings out to maxRing (32 m each), or null. */
    public int[] find(int k, int x0, int z0, int maxRing) {
        for (int ring = 0; ring <= maxRing; ring++)
            for (int j = -ring; j <= ring; j++) for (int i = -ring; i <= ring; i++) {
                if (Math.max(Math.abs(i), Math.abs(j)) != ring) continue;
                int x = x0 + i * 32, z = z0 + j * 32;
                if (scenario(k, x, z)) return new int[]{x, z};
            }
        return null;
    }

    /** Per thread: biome per quart and candidate per grid cell, 64 x 64 windows (neighbouring chunks share them). */
    private static final class Caches {
        final Object[] biomeOwner = new Object[4096], candOwner = new Object[4096];
        final long[] biomeKey = new long[4096], candKey = new long[4096];
        final int[] biome = new int[4096];
        final Tree[] cand = new Tree[4096];
    }

    private static final ThreadLocal<Caches> CACHES = ThreadLocal.withInitial(Caches::new);
}
