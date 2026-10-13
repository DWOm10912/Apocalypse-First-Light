package com.antaurora.apofirstlight.worldgen.terrain.v2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Forest start home B site research (M0, 2026-10-12; docs/worldgen/forest_home_b_whitebox_m0.md section 6): an offline
 * search of the Terrain V2 plan with the P1 conditions of docs/worldgen/forest_start_home_b_v1_plan.md, to see whether
 * they find sites near the origin and which one binds, before M4 writes the real search. Plan data only (seed + plan,
 * like naturalSpawn): nothing reads a world, nothing is wired into the game.
 * <p>
 * Paper frame = the M0 v2 white-box (tools/afl_minecraft_mcp/forest_home_b_whitebox.mjs, enlarged 2026-10-12): X east,
 * Z south, house front +Z, rotation pivot (12, 4.5) = the footprint centre. Four rotations; the stream must lie on the paper east side (the yard / carport side, concept section 4).
 * <ul>
 *   <li>stream: the nearest river water edge from the built footprint (X -1..25, Z -5..14) is 12..40 m away, narrower
 *   than 8 m, and nearest on the east side (X >= 18, the sheds and carport);</li>
 *   <li>biome at the house centre (8.5, 5): mixed mesophytic forest or oak-hickory woodland (riparian forest counted separately);</li>
 *   <li>pad (42 x 31 m, X -8..33, Z -11..19): dry land (no sea, drowned valley or marsh cell), planned ground >= Y64;</li>
 *   <li>pad height = the main house's mean planned ground, at least 2 m over the stream's water surface;</li>
 *   <li>flat: planned ground range under the house (X 0..17, Z 0..13, porch included) <= HOUSE_RANGE, under the built footprint <= BUILD_RANGE;</li>
 *   <li>driveway proxy: from the yard exit (22, 15), a path on an 8 m grid that never enters river water or the graded
 *   bank and never climbs more than 1 in 8 per step reaches, within 250 m of path, a gentle cell (plan slope <= 1/20)
 *   at least 120 m from the house (where the county road segment could run).</li>
 * </ul>
 * <pre>java -cp CLASSES com.antaurora.apofirstlight.worldgen.terrain.v2.ForestHomeSiteSim SEED [RADIUS] [STEP]</pre>
 */
public final class ForestHomeSiteSim {
    static final double AX = 12, AZ = 4.5, EAST_X = 18;
    static final double[] BUILD = {-1, 25, -5, 14}, HOUSE = {0, 17, 0, 13}, PAD = {-8, 33, -11, 19};
    static final double STREAM_MIN = 12, STREAM_MAX = 40, STREAM_WIDTH = 8, ABOVE_STREAM = 2;
    static final double HOUSE_RANGE = 2.0, BUILD_RANGE = 4.0;
    static final double DRIVE_LEN = 250, DRIVE_GRADE = 1.0 / 8, ROAD_SLOPE = 1.0 / 20, ROAD_REACH = 120;
    static final String[] FACING = {"south", "west", "north", "east"};

    final TerrainPlanSurface s;
    final RiverNetwork r;
    final int bucket = 32, bx0, bz0, nb;
    final int[][] buckets;

    ForestHomeSiteSim(TerrainPlanSurface s, int radius) {
        this.s = s;
        this.r = s.rivers;
        int reach = radius + 400;
        bx0 = (int) Math.floor(-reach / (double) bucket);
        bz0 = bx0;
        nb = 2 * (-bx0) + 1;
        List<List<Integer>> lists = new ArrayList<>();
        for (int k = 0; k < nb * nb; k++) lists.add(new ArrayList<>());
        for (int line = 0; line < r.lines(); line++) for (int i = r.start[line]; i < r.start[line + 1] - 1; i++) {
            float w = Math.max(r.vw[i], r.vw[i + 1]) / 2 + 60;
            int a0 = (int) Math.floor((Math.min(r.vx[i], r.vx[i + 1]) - w) / bucket) - bx0, a1 = (int) Math.floor((Math.max(r.vx[i], r.vx[i + 1]) + w) / bucket) - bx0;
            int c0 = (int) Math.floor((Math.min(r.vz[i], r.vz[i + 1]) - w) / bucket) - bz0, c1 = (int) Math.floor((Math.max(r.vz[i], r.vz[i + 1]) + w) / bucket) - bz0;
            for (int c = Math.max(0, c0); c <= Math.min(nb - 1, c1); c++) for (int a = Math.max(0, a0); a <= Math.min(nb - 1, a1); a++) lists.get(c * nb + a).add(i);
        }
        buckets = new int[nb * nb][];
        for (int k = 0; k < nb * nb; k++) buckets[k] = lists.get(k).stream().mapToInt(Integer::intValue).toArray();
    }

    /** {edge distance m (negative in the water), width m, water top block Y} of the nearest river within 60 m; null if none. */
    double[] nearest(double x, double z) {
        int a = (int) Math.floor(x / bucket) - bx0, c = (int) Math.floor(z / bucket) - bz0;
        if (a < 0 || c < 0 || a >= nb || c >= nb) return null;
        double best = Double.MAX_VALUE, w = 0, lvl = 0;
        for (int i : buckets[c * nb + a]) {
            double ax = r.vx[i], az = r.vz[i], dx = r.vx[i + 1] - ax, dz = r.vz[i + 1] - az, l2 = dx * dx + dz * dz;
            double t = l2 > 1e-12 ? Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2)) : 0;
            double ex = ax + dx * t - x, ez = az + dz * t - z;
            double ww = r.vw[i] + (r.vw[i + 1] - r.vw[i]) * t, e = Math.sqrt(ex * ex + ez * ez) - ww / 2;
            if (e < best) { best = e; w = ww; lvl = t < 0.5 ? r.vl[i] : r.vl[i + 1]; }
        }
        return best > 60 ? null : new double[]{best, w, lvl};
    }

    static double[] world(double cx, double cz, int rot, double X, double Z) {
        double dx = X - AX, dz = Z - AZ;
        return switch (rot) {
            case 0 -> new double[]{cx + dx, cz + dz};
            case 1 -> new double[]{cx - dz, cz + dx};
            case 2 -> new double[]{cx - dx, cz - dz};
            default -> new double[]{cx + dz, cz - dx};
        };
    }

    static final class Site {
        int x, z, rot, biome;
        double dist, streamEdge, streamWidth, streamLevel, pad, houseRange, buildRange, padRange, cutFill, drive = -1;
        boolean riparian;
    }

    // the funnel: how many (centre, rotation) pairs survive each condition in order
    final String[] stage = {"centre + rotation", "stream 12..40 m, < 8 m, east", "forest biome (mixed / oak-hickory)",
            "pad dry, >= Y64", ">= 2 m over the stream", "house range <= 2 m", "footprint range <= 4 m"};
    final long[] funnel = new long[stage.length];
    long riparianPass;

    Site test(int cx, int cz, int rot) {
        funnel[0]++;
        // stream from the footprint boundary, 2 m steps
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
        // nothing of the river inside the footprint itself (a meander can pass between two boundary samples)
        for (double X = BUILD[0]; X <= BUILD[1]; X += 4) for (double Z = BUILD[2]; Z <= BUILD[3]; Z += 4) {
            double[] p = world(cx, cz, rot, X, Z), n = nearest(p[0], p[1]);
            if (n != null && n[0] < STREAM_MIN) return null;
        }
        funnel[1]++;
        double[] hc = world(cx, cz, rot, 8.5, 5);
        int biome = EcologyBiomes.biomeAt(s, hc[0], hc[1]);
        boolean forest = biome == EcologyBiomes.WOODLAND || biome == EcologyBiomes.SLOPE_FOREST;
        boolean riparian = biome == EcologyBiomes.RIPARIAN_FOREST;
        if (!forest && !riparian) return null;
        if (forest) funnel[2]++;
        // pad: dry, high enough; heights
        double sumH = 0, nH = 0, hMin = 1e9, hMax = -1e9, bMin = 1e9, bMax = -1e9, pMin = 1e9, pMax = -1e9;
        List<double[]> buildH = new ArrayList<>();
        for (double X = PAD[0]; X <= PAD[1]; X += 2) for (double Z = PAD[2]; Z <= PAD[3]; Z += 2) {
            double[] p = world(cx, cz, rot, X, Z);
            if (s.waterClass(p[0], p[1]) != 0) return null;
            double h = s.heightAt(p[0], p[1]);
            if (h < 64) return null;
            pMin = Math.min(pMin, h); pMax = Math.max(pMax, h);
            if (X >= BUILD[0] && X <= BUILD[1] && Z >= BUILD[2] && Z <= BUILD[3]) { bMin = Math.min(bMin, h); bMax = Math.max(bMax, h); buildH.add(new double[]{h}); }
            if (X >= HOUSE[0] && X <= HOUSE[1] && Z >= HOUSE[2] && Z <= HOUSE[3]) { sumH += h; nH++; hMin = Math.min(hMin, h); hMax = Math.max(hMax, h); }
        }
        if (forest) funnel[3]++;
        double pad = Math.round(sumH / nH);
        if (pad - (lvl + 1) < ABOVE_STREAM) return null;
        if (forest) funnel[4]++;
        if (hMax - hMin > HOUSE_RANGE) return null;
        if (forest) funnel[5]++;
        if (bMax - bMin > BUILD_RANGE) return null;
        if (forest) funnel[6]++; else riparianPass++;
        Site o = new Site();
        o.x = cx; o.z = cz; o.rot = rot; o.biome = biome; o.riparian = riparian;
        o.dist = Math.hypot(cx, cz); o.streamEdge = minE; o.streamWidth = w; o.streamLevel = lvl; o.pad = pad;
        o.houseRange = hMax - hMin; o.buildRange = bMax - bMin; o.padRange = pMax - pMin;
        double cf = 0;
        for (double[] h : buildH) cf += Math.abs(h[0] - pad) * 4;
        o.cutFill = cf;
        return o;
    }

    /** Driveway proxy: the farthest gentle cell (from the house anchor) reachable within DRIVE_LEN of path; -1 if none. */
    double driveway(Site o) {
        double[] start = world(o.x, o.z, o.rot, 22, 15);
        int g = 8, n = 2 * (int) Math.ceil((DRIVE_LEN + 16) / g) + 1, half = n / 2;
        double[] cost = new double[n * n];
        Arrays.fill(cost, Double.MAX_VALUE);
        double[] hh = new double[n * n];
        byte[] ok = new byte[n * n];   // 0 unknown, 1 passable, 2 blocked
        PriorityQueue<double[]> q = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        int s0 = half * n + half;
        cost[s0] = 0;
        q.add(new double[]{0, s0});
        double best = -1;
        while (!q.isEmpty()) {
            double[] e = q.poll();
            int i = (int) e[1];
            if (e[0] > cost[i]) continue;
            double x = start[0] + (i % n - half) * g, z = start[1] + (i / n - half) * g;
            if (!passable(i, x, z, ok, hh)) continue;
            if (s.slopeAt(x, z) <= ROAD_SLOPE) best = Math.max(best, Math.hypot(x - o.x, z - o.z));
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                int a = i % n + dx, c = i / n + dz;
                if (a < 0 || c < 0 || a >= n || c >= n) continue;
                int j = c * n + a;
                double len = g * Math.hypot(dx, dz), nc = cost[i] + len;
                if (nc > DRIVE_LEN || nc >= cost[j]) continue;
                double x2 = x + dx * g, z2 = z + dz * g;
                if (!passable(j, x2, z2, ok, hh) || Math.abs(hh[j] - hh[i]) / len > DRIVE_GRADE) continue;
                boolean wet = false;   // the step itself must not cross the stream between the two grid points
                for (int k = 1; k <= 3 && !wet; k++) {
                    double[] m = nearest(x + dx * g * k / 4.0, z + dz * g * k / 4.0);
                    wet = m != null && m[0] < RiverNetwork.BANK_REACH;
                }
                if (wet) continue;
                cost[j] = nc;
                q.add(new double[]{nc, j});
            }
        }
        return best;
    }

    boolean passable(int i, double x, double z, byte[] ok, double[] hh) {
        if (ok[i] == 0) {
            double[] m = nearest(x, z);
            boolean dry = s.waterClass(x, z) == 0 && (m == null || m[0] >= RiverNetwork.BANK_REACH);
            hh[i] = s.heightAt(x, z);
            ok[i] = (byte) (dry && hh[i] >= 63.5 ? 1 : 2);
        }
        return ok[i] == 1;
    }

    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        int radius = args.length > 1 ? Integer.parseInt(args[1]) : 2000, step = args.length > 2 ? Integer.parseInt(args[2]) : 8;
        long t0 = System.nanoTime();
        TerrainPlanSurface s = new TerrainPlanV2(seed).toSurface();
        long t1 = System.nanoTime();
        ForestHomeSiteSim sim = new ForestHomeSiteSim(s, radius);
        List<Site> pass = new ArrayList<>(), rip = new ArrayList<>();
        for (int cz = -radius; cz <= radius; cz += step) for (int cx = -radius; cx <= radius; cx += step) {
            if ((double) cx * cx + (double) cz * cz > (double) radius * radius) continue;
            double[] c = sim.nearest(cx, cz);
            if (c == null || c[0] < 10 || c[0] > 75) { sim.funnel[0] += 4; continue; }   // no stream close enough for any rotation
            for (int rot = 0; rot < 4; rot++) {
                Site o = sim.test(cx, cz, rot);
                if (o != null) (o.riparian ? rip : pass).add(o);
            }
        }
        long t2 = System.nanoTime();
        List<Site> distinct = distinct(pass, 120), distinctRip = distinct(rip, 120);
        int driveOk = 0;
        for (Site o : distinct) { o.drive = sim.driveway(o); if (o.drive >= ROAD_REACH) driveOk++; }
        long t3 = System.nanoTime();
        int[] spawn = s.naturalSpawn(4096);
        System.out.printf("seed %d, radius %d m, step %d m; plan %.1f s, search %.1f s, driveway %.1f s%n", seed, radius, step,
                (t1 - t0) / 1e9, (t2 - t1) / 1e9, (t3 - t2) / 1e9);
        System.out.printf("natural spawn %s%n", spawn == null ? "none" : spawn[0] + " " + spawn[1]);
        System.out.println("funnel (centre x rotation pairs, forest biome path):");
        for (int k = 0; k < sim.stage.length; k++) System.out.printf("  %-40s %,10d%n", sim.stage[k], sim.funnel[k]);
        System.out.printf("passing pairs %d -> distinct sites (120 m apart) %d, of them driveway proxy ok %d%n", pass.size(), distinct.size(), driveOk);
        System.out.printf("riparian-forest house sites passing the rest: pairs %d, distinct %d (not counted above)%n", rip.size(), distinctRip.size());
        int[] near = {0, 0, 0};
        for (Site o : distinct) if (o.drive >= ROAD_REACH) { if (o.dist <= 500) near[0]++; if (o.dist <= 1000) near[1]++; if (o.dist <= 2000) near[2]++; }
        System.out.printf("full pass (incl. driveway) within 500 / 1000 / 2000 m: %d / %d / %d%n", near[0], near[1], near[2]);
        System.out.println("nearest distinct sites:");
        System.out.println("     x      z  face   dist  biome                     stream m / w / Y   pad  >str  house  foot   pad40  cut+fill m3  drive m");
        int shown = 0;
        for (Site o : distinct) {
            if (shown++ >= 15) break;
            System.out.printf("%6d %6d  %-5s %5.0f  %-24s %5.1f / %3.1f / %3.0f  %4.0f  %4.1f  %5.1f  %4.1f  %5.1f  %10.0f  %7.0f%n", o.x, o.z, FACING[o.rot], o.dist,
                    EcologyBiomes.IDS[o.biome], o.streamEdge, o.streamWidth, o.streamLevel, o.pad, o.pad - (o.streamLevel + 1), o.houseRange, o.buildRange,
                    o.padRange, o.cutFill, o.drive);
        }
        if (!distinct.isEmpty()) {
            double[] hr = distinct.stream().mapToDouble(o -> o.houseRange).sorted().toArray(), cf = distinct.stream().mapToDouble(o -> o.cutFill).sorted().toArray();
            System.out.printf("distinct sites: house range median %.2f m, cut+fill median %.0f m3 (footprint %d m2)%n", hr[hr.length / 2], cf[cf.length / 2],
                    (int) ((BUILD[1] - BUILD[0] + 1) * (BUILD[3] - BUILD[2] + 1)));
        }
    }

    /** Greedy by distance to the origin: keep a site when no kept site is within d m; per kept site the flattest pair nearby. */
    static List<Site> distinct(List<Site> all, double d) {
        List<Site> sorted = new ArrayList<>(all), out = new ArrayList<>();
        sorted.sort((a, b) -> Double.compare(a.dist, b.dist));
        for (Site o : sorted) {
            Site keep = null;
            for (Site k : out) if (Math.hypot(k.x - o.x, k.z - o.z) < d) { keep = k; break; }
            if (keep == null) out.add(o);
            else if (o.houseRange + o.buildRange < keep.houseRange + keep.buildRange && Math.hypot(o.x - keep.x, o.z - keep.z) < d / 3) out.set(out.indexOf(keep), o);
        }
        return out;
    }
}
