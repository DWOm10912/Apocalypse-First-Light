package com.antaurora.apofirstlight.worldgen.home;

import com.antaurora.apofirstlight.worldgen.network.contract.Shape;
import com.antaurora.apofirstlight.worldgen.network.contract.StructureReservation;
import com.antaurora.apofirstlight.worldgen.terrain.v2.EcologyBiomes;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalEcologyBiomes;
import com.antaurora.apofirstlight.worldgen.terrain.v2.RiverNetwork;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import java.util.ArrayList;
import java.util.List;

/**
 * G2 of the world plan (Main ↔ C3 contract section 2; M4 P1, docs/worldgen/forest_home_b_m4_placement.md): where the forest
 * start home stands. A pure function of the seed, the federal plan surface, the start region and G1's corridor zones; it
 * reads no world and loads no chunk, so the trees, the chunk pass and the spawn all get the same answer.
 * <p>
 * The site conditions are the M0 research's (docs/worldgen/forest_home_b_whitebox_m0.md section 8, tools/terrain-v2-research
 * ForestHomeSiteSim), in the paper frame (pivot (12, 4.5), four rotations):
 * <ul>
 *   <li>a stream 12..40 m from the built footprint (paper X -1..25, Z -5..14), narrower than 8 m, nearest on the paper east
 *   side (X >= 18: the sheds and the carport); none of it inside the plot;</li>
 *   <li>the house centre (8.5, 5) in oak-hickory woodland or mixed mesophytic forest (riparian forest only when the region
 *   has no woodland site);</li>
 *   <li>the pad (42 x 31 m, X -8..33, Z -11..19) dry (no sea, lake, marsh cell), planned ground >= Y64;</li>
 *   <li>the house's mean planned ground at least 2 m over the stream's water surface;</li>
 *   <li>planned ground range <= 2 m under the house (X 0..17, Z 0..13), <= 4 m under the built footprint;</li>
 *   <li>the plot grown by the graded ring clear of every hard corridor (G1), and of the soft zones too when possible.</li>
 * </ul>
 * Sites closer than {@link #SPACING} merge (the flattest stays). The order is random per seed (the user, 2026-10-14: a
 * random site in the start region, not the one nearest its centre): each site's rank is a hash of the seed and its
 * position, so it is the same on every machine. Up to {@link #CANDIDATES} come back, best first; G3 tries them in order.
 */
public final class StartAreaPlanner {
    private StartAreaPlanner() {}

    public static final int CANDIDATES = 8, STEP = 8;
    public static final double SPACING = 120;
    static final double[] BUILD = {-1, 25, -5, 14}, HOUSE = {0, 17, 0, 13}, PAD = {-8, 33, -11, 19};
    static final double EAST_X = 18, STREAM_MIN = 12, STREAM_MAX = 40, STREAM_WIDTH = 8, ABOVE_STREAM = 2, HOUSE_RANGE = 2, BUILD_RANGE = 4;
    static final double MIN_GROUND = 64;

    /**
     * The inputs (contract G2): the seed, the plan surface (a federal one), the start region (centre, radius) and G1's
     * zones. {@code hard} / {@code soft}: corridor shapes; empty until C3.1's routes are in Main (no stand-in is used:
     * Main worlds have no roads yet).
     */
    public record Context(long seed, TerrainPlanSurface surface, double centreX, double centreZ, double radius, List<Shape> hard, List<Shape> soft) {}

    /** A passing site: the pivot's world position, the rotation, the house's ground Y and the numbers that ranked it. */
    public record Site(int x, int z, int rotation, int groundY, boolean riparian, double streamEdge, double streamWidth,
                       double houseRange, double buildRange, boolean softClear, long rank) {}

    /** What {@link #plan} found: the candidates (best first) and how many sites passed. */
    public record Result(List<Site> sites, List<StructureReservation> reservations, int woodland, int riparian, long millis) {}

    public static Result plan(Context ctx) {
        long t0 = System.nanoTime();
        Search s = new Search(ctx);
        List<Site> wood = new ArrayList<>(), rip = new ArrayList<>();
        int r = (int) ctx.radius;
        for (int dz = -r; dz <= r; dz += STEP) for (int dx = -r; dx <= r; dx += STEP) {
            if ((double) dx * dx + (double) dz * dz > (double) r * r) continue;
            int cx = (int) Math.round(ctx.centreX) + dx, cz = (int) Math.round(ctx.centreZ) + dz;
            double[] c = s.nearest(cx, cz);
            if (c == null || c[0] < 10 || c[0] > 75) continue;   // no stream close enough for any rotation
            for (int rot = 0; rot < 4; rot++) {
                Site o = s.test(cx, cz, rot);
                if (o != null) (o.riparian ? rip : wood).add(o);
            }
        }
        List<Site> pool = distinct(wood.isEmpty() ? rip : wood);
        // soft-zone-clear sites first, then the per-seed random rank
        pool.sort((a, b) -> a.softClear != b.softClear ? (a.softClear ? -1 : 1) : Long.compare(a.rank, b.rank));
        List<Site> top = pool.subList(0, Math.min(CANDIDATES, pool.size()));
        List<StructureReservation> res = new ArrayList<>();
        for (Site o : top) {
            int[] origin = ForestHomeLayout.origin(o.x, o.z, o.rotation);
            res.add(ForestHomeLayout.reservation(origin[0], o.groundY, origin[1], o.rotation));
        }
        return new Result(List.copyOf(top), List.copyOf(res), distinct(wood).size(), distinct(rip).size(), (System.nanoTime() - t0) / 1_000_000);
    }

    /** Keeps a site when no kept one is within SPACING (scanned in a fixed order); a flatter one close by replaces it. */
    static List<Site> distinct(List<Site> all) {
        List<Site> sorted = new ArrayList<>(all), out = new ArrayList<>();
        sorted.sort((a, b) -> a.rank != b.rank ? Long.compare(a.rank, b.rank) : Integer.compare(a.rotation, b.rotation));
        for (Site o : sorted) {
            int keep = -1;
            for (int k = 0; k < out.size(); k++) if (Math.hypot(out.get(k).x - o.x, out.get(k).z - o.z) < SPACING) { keep = k; break; }
            if (keep < 0) out.add(o);
            else {
                Site k = out.get(keep);
                if (o.houseRange + o.buildRange < k.houseRange + k.buildRange && Math.hypot(o.x - k.x, o.z - k.z) < SPACING / 3) out.set(keep, o);
            }
        }
        return out;
    }

    /** A per-seed random rank of a position (SplitMix64 of the seed and the position). */
    static long rank(long seed, int x, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ 0x466F72657374486FL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /** The search over one surface: the river segments bucketed for "nearest stream" lookups. */
    static final class Search {
        static final int BUCKET = 32, REACH = 60;
        final Context ctx;
        final TerrainPlanSurface s;
        final RiverNetwork rivers;
        final int bx0, bz0, nb;
        final int[][] buckets;

        Search(Context ctx) {
            this.ctx = ctx;
            this.s = ctx.surface;
            this.rivers = s.rivers;
            int reach = (int) ctx.radius + 400;
            bx0 = (int) Math.floor((ctx.centreX - reach) / BUCKET);
            bz0 = (int) Math.floor((ctx.centreZ - reach) / BUCKET);
            nb = 2 * (int) Math.ceil(reach / (double) BUCKET) + 2;
            List<List<Integer>> lists = new ArrayList<>(nb * nb);
            for (int k = 0; k < nb * nb; k++) lists.add(new ArrayList<>());
            if (rivers != null) for (int line = 0; line < rivers.lines(); line++) for (int i = rivers.lineStart(line); i < rivers.lineEnd(line) - 1; i++) {
                float x0 = rivers.vertexX(i), x1 = rivers.vertexX(i + 1), z0 = rivers.vertexZ(i), z1 = rivers.vertexZ(i + 1);
                float w = Math.max(rivers.vertexWidth(i), rivers.vertexWidth(i + 1)) / 2 + REACH;
                int a0 = (int) Math.floor((Math.min(x0, x1) - w) / BUCKET) - bx0, a1 = (int) Math.floor((Math.max(x0, x1) + w) / BUCKET) - bx0;
                int c0 = (int) Math.floor((Math.min(z0, z1) - w) / BUCKET) - bz0, c1 = (int) Math.floor((Math.max(z0, z1) + w) / BUCKET) - bz0;
                for (int c = Math.max(0, c0); c <= Math.min(nb - 1, c1); c++) for (int a = Math.max(0, a0); a <= Math.min(nb - 1, a1); a++) lists.get(c * nb + a).add(i);
            }
            buckets = new int[nb * nb][];
            for (int k = 0; k < nb * nb; k++) buckets[k] = lists.get(k).stream().mapToInt(Integer::intValue).toArray();
        }

        /** {edge distance m (negative in the water), width m, water top block Y} of the nearest river within REACH; null if none. */
        double[] nearest(double x, double z) {
            int a = (int) Math.floor(x / BUCKET) - bx0, c = (int) Math.floor(z / BUCKET) - bz0;
            if (a < 0 || c < 0 || a >= nb || c >= nb) return null;
            double best = Double.MAX_VALUE, w = 0, lvl = 0;
            for (int i : buckets[c * nb + a]) {
                double ax = rivers.vertexX(i), az = rivers.vertexZ(i), dx = rivers.vertexX(i + 1) - ax, dz = rivers.vertexZ(i + 1) - az, l2 = dx * dx + dz * dz;
                double t = l2 > 1e-12 ? Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2)) : 0;
                double ex = ax + dx * t - x, ez = az + dz * t - z;
                double ww = rivers.vertexWidth(i) + (rivers.vertexWidth(i + 1) - rivers.vertexWidth(i)) * t, e = Math.sqrt(ex * ex + ez * ez) - ww / 2;
                if (e < best) { best = e; w = ww; lvl = t < 0.5 ? rivers.vertexLevel(i) : rivers.vertexLevel(i + 1); }
            }
            return best > REACH ? null : new double[]{best, w, lvl};
        }

        /** World point of a paper point for pivot (cx, cz), rotation rot (the M0 sim's frame; = ForestHomeLayout's turns). */
        static double[] world(double cx, double cz, int rot, double X, double Z) {
            double dx = X - 12, dz = Z - 4.5;
            return switch (rot) {
                case 0 -> new double[]{cx + dx, cz + dz};
                case 1 -> new double[]{cx - dz, cz + dx};
                case 2 -> new double[]{cx - dx, cz - dz};
                default -> new double[]{cx + dz, cz - dx};
            };
        }

        Site test(int cx, int cz, int rot) {
            // stream from the built footprint's boundary, 2 m steps
            double minE = Double.MAX_VALUE, w = 0, lvl = 0, sideX = 0;
            for (double X = BUILD[0]; X <= BUILD[1]; X += 2) for (double Z : new double[]{BUILD[2], BUILD[3]}) {
                double[] p = world(cx, cz, rot, X, Z), n = nearest(p[0], p[1]);
                if (n != null && n[0] < minE) { minE = n[0]; w = n[1]; lvl = n[2]; sideX = X; }
            }
            for (double Z = BUILD[2]; Z <= BUILD[3]; Z += 2) for (double X : new double[]{BUILD[0], BUILD[1]}) {
                double[] p = world(cx, cz, rot, X, Z), n = nearest(p[0], p[1]);
                if (n != null && n[0] < minE) { minE = n[0]; w = n[1]; lvl = n[2]; sideX = X; }
            }
            if (minE < STREAM_MIN || minE > STREAM_MAX || w >= STREAM_WIDTH || sideX < EAST_X) return null;
            // nothing of the river inside the plot (paper X -10..26, Z -10..15; a meander can pass between two samples)
            for (double X = -10; X <= 26; X += 4) for (double Z = -10; Z <= 15; Z += 4) {
                double[] p = world(cx, cz, rot, X, Z), n = nearest(p[0], p[1]);
                if (n != null && n[0] < STREAM_MIN) return null;
            }
            double[] hc = world(cx, cz, rot, 8.5, 5);
            int biome = FederalEcologyBiomes.biomeAt(s, s.ecology, hc[0], hc[1]);
            boolean forest = biome == EcologyBiomes.WOODLAND || biome == EcologyBiomes.SLOPE_FOREST;
            boolean riparian = biome == EcologyBiomes.RIPARIAN_FOREST;
            if (!forest && !riparian) return null;
            double sumH = 0, nH = 0, hMin = 1e9, hMax = -1e9, bMin = 1e9, bMax = -1e9;
            for (double X = PAD[0]; X <= PAD[1]; X += 2) for (double Z = PAD[2]; Z <= PAD[3]; Z += 2) {
                double[] p = world(cx, cz, rot, X, Z);
                if (s.waterClass(p[0], p[1]) != 0 || s.waterAt(p[0], p[1])) return null;
                double h = s.heightAt(p[0], p[1]);
                if (h < MIN_GROUND) return null;
                if (X >= BUILD[0] && X <= BUILD[1] && Z >= BUILD[2] && Z <= BUILD[3]) { bMin = Math.min(bMin, h); bMax = Math.max(bMax, h); }
                if (X >= HOUSE[0] && X <= HOUSE[1] && Z >= HOUSE[2] && Z <= HOUSE[3]) { sumH += h; nH++; hMin = Math.min(hMin, h); hMax = Math.max(hMax, h); }
            }
            double mean = sumH / nH;
            if (mean - (lvl + 1) < ABOVE_STREAM) return null;
            if (hMax - hMin > HOUSE_RANGE || bMax - bMin > BUILD_RANGE) return null;
            // G1: the plot plus its graded ring clear of every hard corridor; soft zones only rank
            int[] o = ForestHomeLayout.origin(cx, cz, rot);
            var keep = ForestHomeLayout.plot(o[0], o[1], rot).grow(ForestHomeLayout.GRADE_MARGIN);
            if (touches(keep, ctx.hard)) return null;
            boolean softClear = !touches(keep, ctx.soft);
            // the first air layer over the grade: the natural ground's top block is ceil(h) - 1
            int groundY = (int) Math.ceil(mean);
            return new Site(cx, cz, rot, groundY, riparian, minE, w, hMax - hMin, bMax - bMin, softClear, rank(ctx.seed, cx, cz));
        }

        /** Whether a corridor reaches the rectangle (its corners, edge midpoints and centre within the shape, or the shape's
         *  points inside: sampled every 4 m along the rectangle's outline). */
        static boolean touches(com.antaurora.apofirstlight.worldgen.network.contract.RotatedRect r, List<Shape> zones) {
            if (zones.isEmpty()) return false;
            double[] b = r.bounds();
            for (Shape z : zones) {
                double[] zb = z.bounds();
                if (zb[2] < b[0] || zb[0] > b[2] || zb[3] < b[1] || zb[1] > b[3]) continue;
                for (double x = b[0]; x <= b[2]; x += 4) for (double zz = b[1]; zz <= b[3]; zz += 4)
                    if (r.contains(x, zz) && z.contains(x, zz)) return true;
            }
            return false;
        }
    }
}
