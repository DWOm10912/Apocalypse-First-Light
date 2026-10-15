package com.antaurora.apofirstlight.worldgen.federal;

import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.RoadClass;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Offline checks of the federal map prototype (C1; tools/federal-worldgen/run.sh checks [seed...]). For each seed:
 * determinism (two independent builds and the cached one agree bit for bit, road plan included), state coverage and
 * contiguity, one mainland with one connected sea (no mask lakes), a continuous coast, island separation, city
 * candidates, the start region, corridor connectivity and geometry, the smoothness of the physical layer (no seams at
 * the borders) and the query speed. Without arguments: the user's seed and 15 more.
 */
public final class FederalChecks {
    static final double STEP = 50, HALF = 13000;
    static final int G = (int) (2 * HALF / STEP);
    /** Farthest land from the origin over all checked seeds (sets the plan grid every seed fits in). */
    static double farthest;

    public static void main(String[] args) throws Exception {
        List<Long> seeds = new ArrayList<>();
        for (String a : args) seeds.add(Long.parseLong(a));
        if (seeds.isEmpty()) {
            seeds.add(-295378578869149513L);
            SplittableRandom r = new SplittableRandom(20261014L);
            for (int k = 0; k < 15; k++) seeds.add(r.nextLong());
        }
        File out = new File("build/federal-worldgen/checks.txt");
        out.getParentFile().mkdirs();
        try (PrintStream file = new PrintStream(new FileOutputStream(out), true, StandardCharsets.UTF_8)) {
            int failed = 0;
            for (int k = 0; k < seeds.size(); k++) {
                List<String> fails = new ArrayList<>();
                String line = check(seeds.get(k), k == 0, fails);
                String verdict = fails.isEmpty() ? "PASS" : "FAIL " + fails;
                System.out.println(line + "  " + verdict);
                file.println(line + "  " + verdict);
                if (!fails.isEmpty()) failed++;
            }
            // r1 keeps 2280 m of open ocean between its farthest land and the grid edge (PlanScaleBench size)
            int n = (int) Math.ceil(2 * (farthest + 2280) / 16 / 32) * 32;
            String sum = String.format(Locale.ROOT, "%d seeds, %d failed; farthest land %.0f m -> plan grid N=%d (%.1f km) fits every checked seed",
                    seeds.size(), failed, farthest, n, n * 16 / 1000.0);
            System.out.println(sum);
            file.println(sum);
            if (failed > 0) System.exit(1);
        }
    }

    static String check(long seed, boolean detail, List<String> fails) throws Exception {
        FederalMap a = FederalMap.fresh(seed, 1), b = FederalMap.fresh(seed, 1), c = FederalMap.forSeed(seed);
        // 1. determinism
        String da = digest(a), db = digest(b), dc = digest(c);
        if (!da.equals(db) || !da.equals(dc)) fails.add("determinism");
        NationalRoadPlan ra = NationalRoadPlan.build(a, null), rb = NationalRoadPlan.build(b, null);
        if (!ra.toJson().equals(rb.toJson())) fails.add("road plan determinism");
        // lattice of states / landmasses
        byte[] st = new byte[G * G], mass = new byte[G * G];
        long[] area = new long[7];
        for (int r = 0; r < G; r++) for (int q = 0; q < G; q++) {
            double x = -HALF + (q + 0.5) * STEP, z = -HALF + (r + 0.5) * STEP;
            MacroGeographySample s = a.sample((int) Math.floor(x), (int) Math.floor(z));
            int i = r * G + q;
            mass[i] = (byte) s.landmassId();
            FederalStateId id = s.isLand() ? a.stateAt(Math.floor(x), Math.floor(z)) : null;
            if (s.isLand() && id == null) fails.add("land without state");
            st[i] = (byte) (id == null ? 0 : id.index());
            area[st[i]]++;
        }
        double land = 0;
        for (int k = 1; k <= 6; k++) land += area[k];
        // 2. coverage and contiguity (mainland part of each state)
        StringBuilder states = new StringBuilder();
        double minShare = 1, maxShare = 0;
        for (int k = 1; k <= 6; k++) {
            double km2 = area[k] * STEP * STEP / 1e6, share = area[k] / land;
            minShare = Math.min(minShare, share);
            maxShare = Math.max(maxShare, share);
            final int kk = k;
            List<Integer> comps = components(i -> st[i] == kk && mass[i] == 0);
            int big = 0;
            for (int n : comps) if (n * STEP * STEP > 0.25e6) big++;
            if (km2 < 15) fails.add("s" + k + " under 15 km2");
            if (big != 1) fails.add("s" + k + " in " + big + " parts");
            states.append(String.format(Locale.ROOT, " s%d %.0f", k, km2));
        }
        // 3. one mainland, one sea, three islands
        List<Integer> mainland = components(i -> mass[i] == 0);
        List<Integer> sea = components(i -> mass[i] < 0);
        List<Integer> islands = components(i -> mass[i] > 0);
        if (mainland.size() != 1) fails.add("mainland in " + mainland.size() + " parts");
        if (sea.size() != 1) fails.add("sea in " + sea.size() + " parts (mask lakes)");
        if (islands.size() != 3) fails.add(islands.size() + " island components");
        FederalMap.Bounds lb = a.landBounds();
        double far = Math.max(Math.max(-lb.minX(), lb.maxX()), Math.max(-lb.minZ(), lb.maxZ()));
        farthest = Math.max(farthest, far);
        // 4. coast continuity: no jump in the coast radius anywhere (the angle seam included)
        double jump = 0;
        for (int k = 0; k < 200000; k++) {
            double ang = -Math.PI + k * (2 * Math.PI / 200000);
            jump = Math.max(jump, Math.abs(a.coastRadius(ang + 2 * Math.PI / 200000) - a.coastRadius(ang)));
        }
        if (jump > 20) fails.add(String.format(Locale.ROOT, "coast jump %.0f m", jump));
        // 5. islands: off the mainland by at least 80 % of the gap, apart from each other, inside the land bounds
        double minGap = Double.MAX_VALUE;
        for (FederalMap.Island is : a.islands()) {
            for (int k = 0; k < 720; k++) {
                double t = k * Math.PI / 360, lo = 0, hi = 4 * is.major();
                for (int it = 0; it < 40; it++) { double mid = (lo + hi) / 2; if (is.distance(is.x() + Math.cos(t) * mid, is.z() + Math.sin(t) * mid) > 0) lo = mid; else hi = mid; }
                double x = is.x() + Math.cos(t) * lo, z = is.z() + Math.sin(t) * lo;
                minGap = Math.min(minGap, -a.mainlandDistance(x, z));
            }
            for (FederalMap.Island o : a.islands())
                if (o.id() > is.id() && Math.hypot(o.x() - is.x(), o.z() - is.z()) < 1.2 * (o.major() + is.major())) fails.add("islands " + is.id() + "/" + o.id() + " touch");
        }
        if (minGap < 0.8 * FederalTemplate.ISLAND_GAP) fails.add(String.format(Locale.ROOT, "island gap %.0f m", minGap));
        // 6. cities
        int jittered = 0;
        double minSpacing = Double.MAX_VALUE;
        for (FederalMap.City city : a.cities()) {
            if (city.jittered()) jittered++;
            if (a.mainlandDistance(city.x(), city.z()) < 400) fails.add(city.id() + " near coast");
            if (a.stateAt(city.x(), city.z()) != city.state()) fails.add(city.id() + " outside its state");
            for (FederalMap.City o : a.cities()) if (o != city) minSpacing = Math.min(minSpacing, Math.hypot(o.x() - city.x(), o.z() - city.z()));
        }
        if (minSpacing < 1500) fails.add(String.format(Locale.ROOT, "cities %.0f m apart", minSpacing));
        // 7. start
        FederalMap.Start start = a.start();
        if (start.state() != FederalStateId.S1 || a.mainlandDistance(start.x(), start.z()) < 1000) fails.add("start");
        // 8. corridors: connectivity, on land, radius
        Map<String, List<String>> graph = new HashMap<>();
        double minRadius = Double.MAX_VALUE;
        boolean[] reached = new boolean[7];
        for (NationalRoadPlan.Corridor co : ra.corridors()) {
            graph.computeIfAbsent(co.from(), x -> new ArrayList<>()).add(co.to());
            graph.computeIfAbsent(co.to(), x -> new ArrayList<>()).add(co.from());
            if (co.roadClass() != RoadClass.ISLAND_BRIDGE) {
                minRadius = Math.min(minRadius, co.stats().minRadius());
                for (int k = 0; k < co.xs().length; k++) {
                    if (a.mainlandDistance(co.xs()[k], co.zs()[k]) < 0) { fails.add(co.id() + " leaves the land"); break; }
                    FederalStateId s = a.stateAt(co.xs()[k], co.zs()[k]);
                    if (s != null) reached[s.index()] = true;
                }
            }
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        ArrayDeque<String> todo = new ArrayDeque<>(List.of("valley_town"));
        while (!todo.isEmpty()) { String v = todo.pop(); if (seen.add(v)) todo.addAll(graph.getOrDefault(v, List.of())); }
        if (seen.size() != a.cities().size() + a.islands().size()) fails.add("road graph reaches " + seen.size());
        for (int k = 1; k <= 6; k++) if (!reached[k]) fails.add("no road in s" + k);
        if (minRadius < 300) fails.add(String.format(Locale.ROOT, "corridor radius %.0f m", minRadius));
        // 9. the physical layer is smooth everywhere (so there is no seam at any border)
        double[] w0 = new double[6], w1 = new double[6];
        double maxStep = 0, borderStep = 0;
        SplittableRandom rnd = new SplittableRandom(seed);
        for (int k = 0; k < 100000; k++) {
            double x = -10000 + 20000 * rnd.nextDouble(), z = -8000 + 16000 * rnd.nextDouble(), ang = rnd.nextDouble() * Math.PI * 2;
            double x1 = x + 25 * Math.cos(ang), z1 = z + 25 * Math.sin(ang);
            a.physiography(x, z, w0);
            a.physiography(x1, z1, w1);
            double d = 0;
            for (int j = 0; j < 6; j++) d = Math.max(d, Math.abs(w1[j] - w0[j]));
            maxStep = Math.max(maxStep, d);
            if (a.governingStateAt(x, z) != a.governingStateAt(x1, z1)) borderStep = Math.max(borderStep, d);
        }
        if (maxStep > 0.08) fails.add(String.format(Locale.ROOT, "province step %.3f per 25 m", maxStep));
        // 10. query speed
        String speed = "";
        if (detail) {
            double sink = 0;
            long t0 = System.nanoTime();
            for (int k = 0; k < 2_000_000; k++) { FederalStateId s = a.stateAt(-11000 + (k % 2000) * 11.0, -9000 + (k / 2000) * 18.0); sink += s == null ? 0 : s.index(); }
            long t1 = System.nanoTime();
            for (int k = 0; k < 500_000; k++) { a.physiography(-11000 + (k % 1000) * 22.0, -9000 + (k / 1000) * 36.0, w0); sink += w0[0]; }
            long t2 = System.nanoTime();
            for (int k = 0; k < 500_000; k++) sink += a.sample(-11000 + (k % 1000) * 22, -9000 + (k / 1000) * 36).surfaceHeight();
            long t3 = System.nanoTime();
            speed = String.format(Locale.ROOT, "  query ns: stateAt %.0f, physiography %.0f, sample %.0f (%s)", (t1 - t0) / 2e6, (t2 - t1) / 5e5, (t3 - t2) / 5e5, sink > 0 ? "ok" : "");
        }
        return String.format(Locale.ROOT, "seed %d: land %.1f km2 (states km2%s; share %.0f-%.0f%%), islands gap >= %.0f m, cities %d/%d jittered,"
                        + " min spacing %.0f m, corridor min radius %.0f m, province step %.3f / 25 m (at borders %.3f), coast step %.1f m, digest %s%s",
                seed, land * STEP * STEP / 1e6, states, 100 * minShare, 100 * maxShare, minGap, jittered, a.cities().size(), minSpacing, minRadius,
                maxStep, borderStep, jump, da.substring(0, 12), speed);
    }

    interface Cell { boolean in(int i); }

    /** 4-connected components of the lattice cells where {@code in} holds: their sizes. */
    static List<Integer> components(Cell in) {
        boolean[] seen = new boolean[G * G];
        List<Integer> sizes = new ArrayList<>();
        int[] stack = new int[G * G];
        for (int s = 0; s < G * G; s++) {
            if (seen[s] || !in.in(s)) continue;
            int top = 0, n = 0;
            stack[top++] = s;
            seen[s] = true;
            while (top > 0) {
                int i = stack[--top];
                n++;
                int q = i % G, r = i / G;
                int[] nb = {q > 0 ? i - 1 : -1, q < G - 1 ? i + 1 : -1, r > 0 ? i - G : -1, r < G - 1 ? i + G : -1};
                for (int j : nb) if (j >= 0 && !seen[j] && in.in(j)) { seen[j] = true; stack[top++] = j; }
            }
            sizes.add(n);
        }
        return sizes;
    }

    /** SHA-256 over every public query on a 65 m lattice plus the candidates (16 hex). */
    static String digest(FederalMap m) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(64);
        double[] w = new double[6];
        for (int r = 0; r < 400; r++) for (int q = 0; q < 400; q++) {
            double x = -HALF + q * 65.0, z = -HALF + r * 65.0;
            MacroGeographySample s = m.sample((int) x, (int) z);
            FederalStateId id = m.stateAt(x, z);
            m.physiography(x, z, w);
            bb.clear();
            bb.putInt(s.landmassId()).putInt(s.waterClass().ordinal()).putLong(Double.doubleToLongBits(s.surfaceHeight()))
                    .putInt(id == null ? 0 : id.index()).putInt(m.governingStateAt(x, z).index());
            for (double v : w) bb.putFloat((float) v);
            bb.putFloat((float) m.temperature(x, z)).putFloat((float) m.moisture(x, z));
            md.update(bb.array(), 0, bb.position());
        }
        StringBuilder sb = new StringBuilder();
        for (FederalMap.City c : m.cities()) sb.append(c).append(';');
        for (FederalMap.Island i : m.islands()) sb.append(i).append(';');
        for (FederalMap.Crossing c : m.crossings()) sb.append(c).append(';');
        sb.append(m.start()).append(m.landBounds());
        md.update(sb.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte x : md.digest()) hex.append(String.format("%02x", x));
        return hex.substring(0, 16);
    }

    private FederalChecks() {}

}
