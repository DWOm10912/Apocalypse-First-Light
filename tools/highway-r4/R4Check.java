package com.antaurora.apofirstlight.dev.highway.r4;

import com.antaurora.apofirstlight.dev.highwaymesh.ChunkMeshGeometry;
import com.antaurora.apofirstlight.dev.highwaymesh.ChunkMeshGeometry.Triangle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline checks of the pure R4 kernel (docs/worldgen/highway_r4_sample_v1.md, M): cross-section ledger and slopes,
 * profile, alignment queries, pavement mesh coverage / heights / UVs / chunk tiling, and the earthworks on synthetic
 * terrains (turf corner sharing, turf under the asphalt, no gap under the bevels, collision against the mesh, refusals,
 * determinism, negative coordinates). No Minecraft classes: run by tools/highway-r4/check.mjs. Deterministic output
 * (no timings) in tools/highway-r4/check-results.json.
 */
final class R4Check {
    private static final Map<String, Object> RESULTS = new LinkedHashMap<>();
    private static int failures, debug;

    static void require(boolean ok, String what) {
        if (!ok) { failures++; System.out.println("FAIL " + what); }
    }

    static String f(double v) { return String.format(Locale.ROOT, "%.4f", v); }

    public static void main(String[] args) throws Exception {
        section();
        profile();
        List<Object[]> designs = new ArrayList<>();
        for (var r : List.of(recipe(1000.5, 2000.5, 0, 384, 0), recipe(1000.5, 2000.5, 37, 512, 8), recipe(-3000.5, -1500.5, 120, 256, -12),
                recipe(16.5, -16.5, 270, 128, 6))) {
            var d = r.withProfile(R4Profile.design(R4Earthworks.groundProfile(flat(r), ground(t -> 70)), r.length()).samples()).canonical().design();
            designs.add(new Object[]{r, d});
            alignment(r, d);
            mesh(r, d);
        }
        earthworks();
        recipeDeterminism();
        bendLimits();
        journal();
        RESULTS.put("failures", failures);
        StringBuilder out = new StringBuilder("{\n");
        int i = 0;
        for (var e : RESULTS.entrySet()) out.append("  \"").append(e.getKey()).append("\": ").append(e.getValue() instanceof String s ? "\"" + s + "\"" : e.getValue()).append(++i < RESULTS.size() ? ",\n" : "\n");
        out.append("}\n");
        if (args.length > 0) Files.writeString(Path.of(args[0]), out.toString(), StandardCharsets.UTF_8);
        System.out.print(out);
        System.out.println(failures == 0 ? "R4 CHECK OK" : "R4 CHECK FAILED: " + failures);
        if (failures != 0) System.exit(1);
    }

    static R4Recipe recipe(double x, double z, double heading, double length, double bend) {
        return new R4Recipe(R4Recipe.SCHEMA, x, z, heading, length, bend, R4Recipe.defaultLeg(length), null);
    }

    static R4Design flat(R4Recipe r) {
        return new R4Design(r.alignment(), R4Profile.of(new double[(int) Math.round(r.length() / R4Profile.DS) + 1], r.length()));
    }

    // ---- A: cross-section ledger and slopes ----
    static void section() {
        double visible = 2 * R4Section.EDGE, lanes = 4 * R4Section.LANE, shoulders = 2 * (R4Section.INNER + R4Section.OUTER);
        require(Math.abs(visible - 44) < 1e-12 && Math.abs(lanes + shoulders + R4Section.MEDIAN - 44) < 1e-12, "44 m ledger");
        RESULTS.put("section_visible_m", f(visible));
        RESULTS.put("section_with_bevels_m", f(visible + 2 * R4Section.BEVEL_RUN));
        var c0 = R4Section.cross(0);
        require(Math.abs(R4Section.pavement(c0, 11)) < 1e-12 && Math.abs(R4Section.pavement(c0, -11)) < 1e-12, "crown at datum");
        require(Math.abs(R4Section.pavement(c0, 18.5) + 0.15) < 1e-12 && Math.abs(R4Section.pavement(c0, 22) + 0.29) < 1e-12, "tangent lane / shoulder fall");
        require(Math.abs(R4Section.pavement(c0, 9.5) + 0.06) < 1e-12, "inner shoulder 4 % to the median");
        double maxRoll = 0, maxE = 0, step = 0;
        for (double k = -1 / 250.0; k <= 1 / 250.0 + 1e-12; k += 1 / 25000.0) {
            var c = R4Section.cross(k);
            for (int side : new int[]{-1, 1}) {
                maxRoll = Math.max(maxRoll, Math.max(Math.abs(c.inner(side) + c.lane(side)), Math.abs(c.outer(side) - c.lane(side))));
                // continuity at the breaks
                double v1 = side * R4Section.CROWN, v2 = side * R4Section.LANES_OUT;
                step = Math.max(step, Math.abs(R4Section.pavement(c, v1 - side * 1e-9) - R4Section.pavement(c, v1 + side * 1e-9)));
                step = Math.max(step, Math.abs(R4Section.pavement(c, v2 - side * 1e-9) - R4Section.pavement(c, v2 + side * 1e-9)));
                double edge = side * R4Section.HALF_MEDIAN;
                require(Math.abs(R4Section.median(c, edge) - (R4Section.pavement(c, edge) - R4Section.TURF_DROP)) < 1e-9, "median meets the inner edge");
            }
            maxE = Math.max(maxE, c.superelevation());
            double mid = (R4Section.median(c, -R4Section.HALF_MEDIAN) + R4Section.median(c, R4Section.HALF_MEDIAN)) / 2;
            require(Math.abs(R4Section.median(c, 0) - (mid - R4Section.MEDIAN_DEPTH)) < 1e-9, "median swale depth");
        }
        require(maxRoll <= R4Section.MAX_ROLLOVER + 1e-12, "rollover <= 8 %");
        require(step < 1e-7, "pavement continuous across breaks");
        require(Math.abs(maxE - R4Section.E_MAX) < 1e-12, "emax reached at Rmin");
        RESULTS.put("section_max_rollover", f(maxRoll));
        // roadside: hinge exactly on the edge, continuous in d and g, fill and cut templates at the extremes
        double hinge = 0, jump = 0;
        for (double g = -6; g <= 6; g += 0.05) {
            hinge = Math.max(hinge, Math.abs(R4Section.roadside(0, g)));
            for (double d = 0; d < 30; d += 0.05) {
                jump = Math.max(jump, Math.abs(R4Section.roadside(d + 0.001, g) - R4Section.roadside(d, g)));
                jump = Math.max(jump, Math.abs(R4Section.roadside(d, g + 0.001) - R4Section.roadside(d, g)));
            }
        }
        require(hinge < 1e-12, "roadside hinge on the edge");
        require(jump < 0.002, "roadside continuous (max step per mm " + jump + ")");
        require(Math.abs(R4Section.roadside(4, -50) + 1.0) < 1e-9 && Math.abs(R4Section.roadside(10, -50) + 1.5 + 4.0 / 3) < 1e-9, "fill template 1:4 then 1:3");
        require(Math.abs(R4Section.roadside(4.6, 50) + 1.0) < 1e-9, "ditch bottom 1.0 m in deep cut");
        RESULTS.put("roadside_max_step_per_mm", f(jump));
    }

    // ---- B: profile ----
    static void profile() {
        double L = 384;
        int n = (int) (L / R4Profile.DS) + 1;
        Map<String, java.util.function.DoubleUnaryOperator> grounds = new LinkedHashMap<>();
        grounds.put("flat", s -> 70);
        grounds.put("ramp2", s -> 70 + 0.02 * s);
        grounds.put("ramp5", s -> 70 + 0.05 * s);
        grounds.put("hill", s -> 70 + 3 * Math.sin(2 * Math.PI * s / 300));
        grounds.put("step", s -> s < 190 ? 70 : 72);
        for (var e : grounds.entrySet()) {
            double[] g = new double[n];
            for (int i = 0; i < n; i++) g[i] = e.getValue().applyAsDouble(i * R4Profile.DS);
            var p = R4Profile.design(g, L);
            var q = R4Profile.design(g, L);
            require(java.util.Arrays.equals(p.samples(), q.samples()), "profile deterministic " + e.getKey());
            require(p.maxGrade() <= R4Profile.MAX_GRADE + 1e-9, "grade <= 3 % " + e.getKey());
            double dev = 0;
            for (int i = 0; i < n; i++) dev = Math.max(dev, Math.abs(p.samples()[i] - g[i] - R4Profile.RAISE));
            RESULTS.put("profile_" + e.getKey(), "max grade " + f(p.maxGrade() * 100) + " %, min K " + (Double.isInfinite(p.minK()) ? "inf" : f(p.minK())) + ", max |E - ground - raise| " + f(dev) + " m");
        }
        double[] line = new double[n];
        for (int i = 0; i < n; i++) line[i] = 3 + 0.01 * i;
        double[] sm = R4Profile.gauss(line, 20);
        double err = 0;
        for (int i = 0; i < n; i++) err = Math.max(err, Math.abs(sm[i] - line[i]));
        require(err < 1e-9, "smoothing keeps a straight grade");
    }

    // ---- C: alignment queries ----
    static void alignment(R4Recipe r, R4Design d) {
        double err = 0, maxK = 0;
        for (double s = -R4Design.END + 0.5; s <= d.length + R4Design.END - 0.5; s += 3.7)
            for (double u = -40; u <= 40; u += 1.3) {
                double[] p = d.point(s, u);
                var l = d.local(p[0], p[1]);
                require(l.isPresent(), "local present " + r.describe() + " s " + s + " u " + u);
                if (l.isEmpty()) continue;
                err = Math.max(err, Math.max(Math.abs(l.get()[0] - s), Math.abs(l.get()[1] - u)));
            }
        for (double s = 0; s <= d.length; s += 1) maxK = Math.max(maxK, Math.abs(d.curvature(s)));
        require(err < 1e-6, "local inverts point (" + err + ")");
        require(maxK <= 1 / 300.0 + 1e-9, "radius >= 300 m");
        if (r.bend() > 0) require(d.curvature(d.length / 2) > 0, "right bend = positive curvature");
        if (r.bend() > 0) {
            var c = d.cross(d.length / 2);
            require(c.laneRight() > 0 && c.laneLeft() < c.laneRight(), "superelevation falls to the inside (right)");
        }
        // superelevation runoff: lane slopes change at most RUNOFF_RATE per metre; the full target is reached where needed
        double rate = 0, shortfall = 0;
        for (double s = 0; s + 0.5 <= d.length; s += 0.5) {
            var a = d.cross(s);
            var b = d.cross(s + 0.5);
            rate = Math.max(rate, Math.max(Math.abs(b.laneLeft() - a.laneLeft()), Math.abs(b.laneRight() - a.laneRight())) / 0.5);
            var t = R4Section.cross(d.curvature(s));
            shortfall = Math.max(shortfall, Math.max(Math.abs(t.laneLeft() - 0.02) - Math.abs(a.laneLeft() - 0.02), Math.abs(t.laneRight() - 0.02) - Math.abs(a.laneRight() - 0.02)));
        }
        require(rate <= R4Section.RUNOFF_RATE + 1e-9, "runoff rate (" + rate + ")");
        require(shortfall < 0.002, "superelevation reached (" + shortfall + ")");
        RESULTS.put("runoff_" + key(r), "max rate " + String.format(Locale.ROOT, "%.6f", rate) + " /m (limit " + String.format(Locale.ROOT, "%.6f", R4Section.RUNOFF_RATE)
                + "), max e " + f(java.util.stream.IntStream.rangeClosed(0, (int) d.length).mapToDouble(i -> d.cross(i).superelevation()).max().orElse(0)) + ", start lanes " + f(d.cross(0).laneLeft()) + "/" + f(d.cross(0).laneRight()));
        var sf = d.surface(d.point(d.length / 2, 15)[0], d.point(d.length / 2, 15)[1]).orElseThrow();
        require(sf.paved() && sf.zone() == R4Section.Zone.LANE && sf.ny() > 0.99 && Math.abs(Math.hypot(sf.tx(), sf.tz()) - 1) < 1e-9, "surface query");
        RESULTS.put("alignment_" + key(r), "length " + f(d.length) + ", local error " + String.format(Locale.ROOT, "%.2e", err) + ", min radius " + (maxK == 0 ? "inf" : f(1 / maxK)));
    }

    static String key(R4Recipe r) { return String.format(Locale.ROOT, "h%.0f_l%.0f_b%.0f", r.heading(), r.length(), r.bend()); }

    // ---- D: mesh ----
    static void mesh(R4Recipe r, R4Design d) {
        int[] b = R4Earthworks.bounds(d);
        int ox = Math.floorDiv(b[0], 16) * 16, oz = Math.floorDiv(b[1], 16) * 16;
        var tris = R4Mesh.triangles(d, ox, oz);
        require(tris.equals(R4Mesh.triangles(d, ox, oz)), "mesh deterministic");
        double minNy = 1;
        for (var t : tris) {
            for (var v : List.of(t.a(), t.b(), t.c())) require(v.u() >= -1e-9 && v.u() <= 1 + 1e-9 && v.v() >= -1e-9 && v.v() <= 1 + 1e-9, "UV in 0..1");
            minNy = Math.min(minNy, t.sourceNormal().y());
        }
        require(minNy > 0.5, "every face points up (" + minNy + ")");
        // coverage and heights: one asphalt face under every paved point, at the design height
        Map<Long, List<Triangle>> grid = new HashMap<>();
        for (var t : tris) {
            double x0 = Math.min(t.a().x(), Math.min(t.b().x(), t.c().x())), x1 = Math.max(t.a().x(), Math.max(t.b().x(), t.c().x()));
            double z0 = Math.min(t.a().z(), Math.min(t.b().z(), t.c().z())), z1 = Math.max(t.a().z(), Math.max(t.b().z(), t.c().z()));
            for (int i = (int) Math.floor(x0); i <= (int) Math.floor(x1); i++) for (int j = (int) Math.floor(z0); j <= (int) Math.floor(z1); j++)
                grid.computeIfAbsent((long) i << 32 | (j & 0xFFFFFFFFL), k -> new ArrayList<>()).add(t);
        }
        double hErr = 0;
        int samples = 0, holes = 0, overlaps = 0, lineMisses = 0;
        for (double s = 0.1173; s < d.length - 0.1; s += 0.3711) for (double v = R4Section.HALF_MEDIAN + 0.0137; v < R4Section.EDGE - 0.01; v += 0.2917)
            for (int side : new int[]{-1, 1}) {
                double u = side * v;
                double[] p = d.point(s, u);
                double x = p[0] - ox, z = p[1] - oz;
                int asphalt = 0, interior = 0;
                double y = Double.NaN;
                for (var t : grid.getOrDefault((long) (int) Math.floor(x) << 32 | ((int) Math.floor(z) & 0xFFFFFFFFL), List.of())) {
                    if (!t.pavement()) continue;
                    var bc = t.barycentric(x, z);
                    if (bc == null) continue;
                    asphalt++;
                    if (Math.min(bc[0], Math.min(bc[1], bc[2])) > 1e-7) interior++;
                    y = bc[0] * t.a().y() + bc[1] * t.b().y() + bc[2] * t.c().y();
                }
                samples++;
                if (asphalt == 0) holes++;
                if (interior > 1) overlaps++;
                if (asphalt > 0) hErr = Math.max(hErr, Math.abs(y - d.pavementY(s, u)));
                if ((interior > 1 || asphalt > 0 && Math.abs(y - d.pavementY(s, u)) > 0.01) && debug++ < 6)
                    System.out.println("DEBUG " + key(r) + " s " + f(s) + " u " + f(u) + " faces " + asphalt + " mesh " + f(y) + " design " + f(d.pavementY(s, u)));
            }
        // markings where they belong
        for (double s = 0.5; s < d.length - 0.5; s += 1.0) for (int side : new int[]{-1, 1}) {
            for (double[] line : new double[][]{{R4Section.CROWN, 1}, {R4Section.LANES_OUT, 0}, {(R4Section.CROWN + R4Section.LANES_OUT) / 2, 2}}) {
                if (line[1] == 2 && (s % R4Mesh.DASH_PERIOD) >= R4Mesh.DASH) continue;
                double[] p = d.point(s, side * line[0]);
                double x = p[0] - ox, z = p[1] - oz;
                String want = line[1] == 1 ? "yellow" : "white";
                boolean hit = false;
                for (var t : grid.getOrDefault((long) (int) Math.floor(x) << 32 | ((int) Math.floor(z) & 0xFFFFFFFFL), List.of()))
                    if (t.material().equals(want) && t.barycentric(x, z) != null) hit = true;
                if (!hit) lineMisses++;
            }
        }
        require(holes == 0, "no holes in the pavement (" + holes + " of " + samples + ")");
        require(overlaps == 0, "no stacked asphalt faces");
        require(hErr < 0.01, "mesh height = design (" + hErr + ")");
        require(lineMisses == 0, "markings in place (" + lineMisses + " misses)");
        // chunk tiles: clipping keeps the whole area, every tile within the asset budget
        var tiles = ChunkMeshGeometry.tiles(tris);
        double area = tris.stream().mapToDouble(Triangle::areaXZ).sum(), clipped = tiles.values().stream().flatMap(List::stream).mapToDouble(Triangle::areaXZ).sum();
        require(Math.abs(area - clipped) < 1e-6 * area, "tiling keeps the area");
        int tileTris = tiles.values().stream().mapToInt(List::size).sum();
        require(tris.size() <= 50000 && tiles.size() <= 512, "asset budget");
        RESULTS.put("mesh_" + key(r), "triangles " + tris.size() + ", tiles " + tiles.size() + ", tile triangles " + tileTris + ", max height error " + f(hErr)
                + " m, samples " + samples + ", holes " + holes + ", min normal y " + f(minNy));
    }

    // ---- E: earthworks on synthetic terrain ----
    interface Terrain { double at(double along, double across); }

    static R4Earthworks.Ground ground(java.util.function.DoubleUnaryOperator h) { return ground(h, null, 0, 0, 0); }

    /** Natural surface Y = floor(height), plants on a fixed pattern, all grassy. */
    static R4Earthworks.Ground ground(java.util.function.DoubleUnaryOperator flat, Terrain t, double x0, double z0, double heading) {
        double c = Math.cos(Math.toRadians(heading)), s = Math.sin(Math.toRadians(heading));
        return new R4Earthworks.Ground() {
            @Override public int top(int x, int z) {
                double px = x + 0.5 - x0, pz = z + 0.5 - z0;
                double h = t == null ? flat.applyAsDouble(0) : t.at(px * c + pz * s, -px * s + pz * c);
                return (int) Math.floor(h) - 1;
            }
            @Override public int plants(int x, int z) { return ((x * 31 + z * 17) & 7) == 0 ? 1 : 0; }
            @Override public boolean grassy(int x, int z) { return true; }
        };
    }

    record Case(String name, Terrain terrain, boolean buildable) {}

    static void earthworks() {
        var cases = List.of(
                new Case("flat", (a, c) -> 70.4, true),
                new Case("ramp_2pc", (a, c) -> 70 + 0.02 * a, true),
                new Case("cross_3pc", (a, c) -> 70 + 0.03 * c, true),
                new Case("rolling", (a, c) -> 70 + 2.5 * Math.sin(2 * Math.PI * a / 260) + 0.01 * c, true),
                new Case("rough", (a, c) -> 70 + 0.9 * Math.sin(a / 23.0) * Math.cos(c / 31.0) + 0.5 * Math.sin(a / 7.3 + c / 11.0), true),
                new Case("cross_10pc", (a, c) -> 70 + 0.10 * c, false),
                new Case("cliff", (a, c) -> c > 8 ? 80 : 70, false));
        for (var heading : new double[]{0, 33}) for (var cs : cases) {
            double x0 = heading == 0 ? 512.5 : -2048.5, z0 = heading == 0 ? 256.5 : -777.5;
            var r = recipe(x0, z0, heading, 256, heading == 0 ? 0 : 9);
            var g = ground(null, cs.terrain, x0, z0, heading);
            var fl = flat(r);
            var rr = r.withProfile(R4Profile.design(R4Earthworks.groundProfile(fl, g), r.length()).samples()).canonical();
            var d = rr.design();
            int[] b = R4Earthworks.bounds(d);
            var plan = R4Earthworks.plan(d, g, b[0], b[1], b[2], b[3]);
            var again = R4Earthworks.plan(d, g, b[0], b[1], b[2], b[3]);
            require(plan.writes().equals(again.writes()), "earthworks deterministic " + cs.name);
            String name = cs.name + (heading == 0 ? "" : "_h33");
            if (!cs.buildable) {
                require(!plan.ok(), "refused " + name);
                RESULTS.put("earthworks_" + name, "refused: " + (plan.problems().isEmpty() ? "-" : plan.problems().get(0).code()) + " (" + plan.problems().size() + " problems)");
                continue;
            }
            require(plan.ok(), "buildable " + name + (plan.ok() ? "" : ": " + plan.problems().get(0)));
            RESULTS.put("earthworks_" + name, audit(d, g, plan, name));
        }
    }

    /** Turf corners shared, turf under the asphalt, no gap under the bevels, collision against the mesh, write order. */
    static String audit(R4Design d, R4Earthworks.Ground g, R4Earthworks.Plan plan, String name) {
        Map<Long, int[]> turf = new HashMap<>();   // column -> y, shift, nw, ne, sw, se
        Map<Long, double[]> paved = new HashMap<>();
        Map<Long, Integer> collisionTop = new HashMap<>();
        Map<Long, Integer> asphaltY = new HashMap<>();
        for (var w : plan.writes()) {
            long k = (long) w.x() << 32 | (w.z() & 0xFFFFFFFFL);
            if (w.kind() == R4Earthworks.Kind.TURF) {
                int data = w.data();
                turf.put(k, new int[]{w.y(), data >> 16 & 1, data & 15, data >> 4 & 15, data >> 8 & 15, data >> 12 & 15});
            }
            if (w.kind() == R4Earthworks.Kind.COLLISION) collisionTop.merge(k, w.y() * 32 + w.data(), Math::max);
            if (w.kind() == R4Earthworks.Kind.ASPHALT) asphaltY.put(k, w.y());
        }
        for (var c : plan.columns()) if (c.type() == R4Earthworks.ColumnType.PAVED) {
            var range = R4Earthworks.pavedRange(d, c.x(), c.z());
            paved.put((long) c.x() << 32 | (c.z() & 0xFFFFFFFFL), range);
        }
        // shared corners: the east edge of a block against the west edge of its neighbour, south against north
        int mismatches = 0, turfAbove = 0, gaps = 0, edgeChecks = 0;
        for (var e : turf.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey().intValue();
            double[] c = corners(e.getValue());
            var east = turf.get((long) (x + 1) << 32 | (z & 0xFFFFFFFFL));
            if (east != null) { double[] o = corners(east); if (Math.abs(c[1] - o[0]) > 1e-9 || Math.abs(c[3] - o[2]) > 1e-9) mismatches++; }
            var south = turf.get((long) x << 32 | ((z + 1) & 0xFFFFFFFFL));
            if (south != null) { double[] o = corners(south); if (Math.abs(c[2] - o[0]) > 1e-9 || Math.abs(c[3] - o[1]) > 1e-9) mismatches++; }
            // the turf never rises through the asphalt
            for (int q = 0; q < 4; q++) {
                double cx = x + (q & 1), cz = z + (q >> 1);
                var l = d.local(cx, cz);
                if (l.isEmpty()) continue;
                double s = l.get()[0], u = l.get()[1], v = Math.abs(u);
                if (s >= 0 && s <= d.length && v >= R4Section.HALF_MEDIAN && v <= R4Section.EDGE && c[q] > d.pavementY(s, u) - R4Section.TURF_DROP + 1e-9) turfAbove++;
            }
        }
        // the bevel bottom lies under the turf next to it (no slit under the pavement edge)
        for (double s = 0.5; s < d.length - 0.5; s += 0.5) for (int side : new int[]{-1, 1}) for (double v : new double[]{R4Section.EDGE + R4Section.BEVEL_RUN, R4Section.HALF_MEDIAN - R4Section.BEVEL_RUN}) {
            double[] p = d.point(s, side * v);
            double t = turfAt(turf, p[0], p[1]);
            if (Double.isNaN(t)) continue;
            edgeChecks++;
            double bevel = d.pavementY(s, side * (v > R4Section.EDGE ? R4Section.EDGE : R4Section.HALF_MEDIAN)) - R4Section.BEVEL_DROP;
            if (t < bevel - 1e-9) gaps++;
        }
        // collision columns against the mesh and each other
        double colErr = 0, colStep = 0, asphaltClear = Double.POSITIVE_INFINITY;
        for (var e : paved.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = e.getKey().intValue();
            double top = collisionTop.get(e.getKey()) / 32.0, lo = e.getValue()[0], hi = e.getValue()[1];
            colErr = Math.max(colErr, Math.max(top - hi, lo - top));
            asphaltClear = Math.min(asphaltClear, lo - (asphaltY.get(e.getKey()) + 1));
            var east = collisionTop.get((long) (x + 1) << 32 | (z & 0xFFFFFFFFL));
            if (east != null && paved.containsKey((long) (x + 1) << 32 | (z & 0xFFFFFFFFL))) colStep = Math.max(colStep, Math.abs(east / 32.0 - top));
            var south = collisionTop.get((long) x << 32 | ((z + 1) & 0xFFFFFFFFL));
            if (south != null && paved.containsKey((long) x << 32 | ((z + 1) & 0xFFFFFFFFL))) colStep = Math.max(colStep, Math.abs(south / 32.0 - top));
        }
        // write order: removals top-down first, placements bottom-up after
        boolean placing = false;
        int lastY = Integer.MAX_VALUE, order = 0;
        for (var w : plan.writes()) {
            boolean air = w.kind() == R4Earthworks.Kind.AIR;
            if (!air && !placing) { placing = true; lastY = Integer.MIN_VALUE; }
            if (air && placing) order++;
            if (air && w.y() > lastY) order++;
            if (!air && w.y() < lastY) order++;
            lastY = w.y();
        }
        require(mismatches == 0, name + ": turf corners shared (" + mismatches + " mismatches)");
        require(turfAbove == 0, name + ": turf under the asphalt (" + turfAbove + " corners above)");
        require(gaps == 0, name + ": bevels reach under the turf (" + gaps + " of " + edgeChecks + ")");
        require(colErr <= 1 / 64.0 + 1e-9, name + ": collision within the column's pavement range (" + colErr + ")");
        require(asphaltClear >= 0.02 - 1e-9, name + ": asphalt blocks under the mesh (" + asphaltClear + ")");
        require(colStep < 0.25, name + ": collision steps (" + colStep + ")");
        require(order == 0, name + ": write order");
        require(plan.stats().maxHalfWidth() <= R4Earthworks.MAX_HALF_WIDTH, name + ": envelope");
        var st = plan.stats();
        return String.format(Locale.ROOT, "graded %d, paved %d, removed %d, placed %d, max cut %.2f m, max fill %.2f m, half width %.1f m, clamped turf %d, lifted corners %d, "
                        + "collision outside range %.3f m, collision step %.3f m, asphalt clearance %.3f m, bevel edge checks %d",
                st.graded(), st.paved(), st.removed(), st.placed(), st.maxCut(), st.maxFill(), st.maxHalfWidth(), st.clampedTurf(), st.liftedCorners(), Math.max(0, colErr), colStep, asphaltClear, edgeChecks);
    }

    /** Absolute corner heights nw, ne, sw, se of a turf block. */
    static double[] corners(int[] t) {
        double base = t[0] + (t[1] != 0 ? 0.5 : 0);
        return new double[]{base + t[2] / 8.0, base + t[3] / 8.0, base + t[4] / 8.0, base + t[5] / 8.0};
    }

    /** Turf top at (x, z) as GradedTurfModel draws it: two triangles split on the nw-se diagonal. NaN when no turf there. */
    static double turfAt(Map<Long, int[]> turf, double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        var t = turf.get((long) bx << 32 | (bz & 0xFFFFFFFFL));
        if (t == null) return Double.NaN;
        double[] c = corners(t);
        double fx = x - bx, fz = z - bz;
        // (0,0)=nw (1,0)=ne (0,1)=sw (1,1)=se
        if (fz >= fx) return c[0] + (c[3] - c[2]) * fx + (c[2] - c[0]) * fz;
        return c[0] + (c[1] - c[0]) * fx + (c[3] - c[1]) * fz;
    }

    /** The largest bend each length allows (300 m minimum radius), by bisection; sharper bends must be refused clearly. */
    static void bendLimits() {
        StringBuilder sb = new StringBuilder();
        for (int len : new int[]{128, 192, 256, 384, 512}) {
            double lo = 0, hi = 15;
            if (feasible(len, 15)) lo = 15;
            else for (int it = 0; it < 30; it++) { double m = (lo + hi) / 2; if (feasible(len, m)) lo = m; else hi = m; }
            sb.append(sb.length() == 0 ? "" : ", ").append(len).append(" m: ").append(String.format(Locale.ROOT, "%.1f", lo)).append(" deg");
        }
        try {
            recipe(0.5, 0.5, 0, 128, 15).alignment();
            require(false, "128 m / 15 deg refused");
        } catch (IllegalArgumentException e) {
            require(e.getMessage().startsWith("R4_BEND_TOO_SHARP"), "bend refusal message (" + e.getMessage() + ")");
        }
        RESULTS.put("max_bend", sb.toString());
    }

    static boolean feasible(int len, double bend) {
        try { recipe(0.5, 0.5, 0, len, bend).alignment(); return true; } catch (IllegalArgumentException e) { return false; }
    }

    // ---- G: journal rules (R4Journal) on a string world built like R4Works.prepare does ----
    static final R4Journal.States<String> NAMES = new R4Journal.States<>() {
        @Override public boolean natural(String was, String now) {
            boolean soil = (was.equals("dirt") || was.equals("grass_block")) && (now.equals("dirt") || now.equals("grass_block"));
            return !was.equals(now) && (soil || now.equals("air") && (was.equals("grass") || was.equals("leaves")) || was.equals("air") && now.equals("snow"));
        }
        @Override public String name(String s) { return s; }
    };

    static long pack(int x, int y, int z) { return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF); }

    static String unpack(long p) { return (int) (p >> 38) + "," + (int) (p << 52 >> 52) + "," + (int) (p << 26 >> 38); }

    /** A synthetic world: natural column tops, one plant on some, grass or bare dirt on top; plus edits. */
    static final class World implements R4Earthworks.Ground {
        final R4Earthworks.Ground base;
        final Map<Long, String> edits = new HashMap<>();
        final Map<Long, Boolean> bare = new HashMap<>();
        World(R4Earthworks.Ground base) { this.base = base; }
        @Override public int top(int x, int z) { return base.top(x, z); }
        @Override public int plants(int x, int z) { return state(x, base.top(x, z) + 1, z).equals("grass") ? 1 : 0; }
        @Override public boolean grassy(int x, int z) { return state(x, base.top(x, z), z).equals("grass_block"); }
        String state(int x, int y, int z) {
            String e = edits.get(pack(x, y, z));
            if (e != null) return e;
            int t = base.top(x, z);
            if (y > t + 1) return "air";
            if (y == t + 1) return base.plants(x, z) > 0 ? "grass" : "air";
            if (y == t) return bare.getOrDefault((long) x << 32 | (z & 0xFFFFFFFFL), false) ? "dirt" : "grass_block";
            return y >= t - 2 ? "dirt" : "stone";
        }
    }

    record Journal(String recipe, long[] pos, String[] before, String[] after) {}

    /** The journal R4Works.prepare would record for this world (writes where the state differs from the target). */
    static Journal journal(World w, R4Recipe request) {
        var fl = flat(request);
        var r = request.withProfile(R4Profile.design(R4Earthworks.groundProfile(fl, w), request.length()).samples()).canonical();
        var d = r.design();
        int[] b = R4Earthworks.bounds(d);
        var plan = R4Earthworks.plan(d, w, b[0], b[1], b[2], b[3]);
        List<Long> pos = new ArrayList<>();
        List<String> bs = new ArrayList<>(), as = new ArrayList<>();
        for (var wr : plan.writes()) {
            String cur = w.state(wr.x(), wr.y(), wr.z()), target = wr.kind() == R4Earthworks.Kind.AIR ? "air" : wr.kind() == R4Earthworks.Kind.TURF ? "turf" + wr.data() : wr.kind().name().toLowerCase(Locale.ROOT) + (wr.kind() == R4Earthworks.Kind.COLLISION ? wr.data() : "");
            if (cur.equals(target)) continue;
            pos.add(pack(wr.x(), wr.y(), wr.z()));
            bs.add(cur);
            as.add(target);
        }
        return new Journal(r.version(), pos.stream().mapToLong(Long::longValue).toArray(), bs.toArray(new String[0]), as.toArray(new String[0]));
    }

    static R4Journal.Diff diff(Journal a, Journal b) {
        return R4Journal.diff(a.recipe, a.pos, a.before, a.after, b.recipe, b.pos, b.before, b.after, NAMES, 4, R4Check::unpack);
    }

    static void journal() {
        double x0 = 300.5, z0 = -200.5, heading = 20;
        var r = recipe(x0, z0, heading, 128, 4);
        Terrain t = (a, c) -> 70 + 1.5 * Math.sin(a / 40.0) + 0.02 * c;
        var w = new World(ground(null, t, x0, z0, heading));
        var j1 = journal(w, r);
        var j2 = journal(w, r);
        require(R4Journal.unique(j1.pos), "journal positions unique");
        require(!R4Journal.unique(new long[]{1, 2, 1}), "duplicate detection");
        var same = diff(j1, j2);
        require(same.acceptable() && same.naturalChanges() == 0 && same.examples().isEmpty(), "plan then check on an unchanged world: identical");
        // find a fill column top (grass_block -> dirt write) and a planned plant removal, and a solid write
        int soil = -1, plantRemoval = -1, solid = -1;
        for (int i = 0; i < j1.pos.length; i++) {
            if (soil < 0 && j1.before[i].equals("grass_block") && j1.after[i].startsWith("turf")) soil = i;
            if (plantRemoval < 0 && j1.before[i].equals("grass") && j1.after[i].equals("air")) plantRemoval = i;
            if (solid < 0 && j1.before[i].equals("stone") && !j1.after[i].equals("stone")) solid = i;
        }
        require(soil >= 0 && plantRemoval >= 0, "test journal has a grass top under turf and a plant removal");
        // 1. grass spread back onto dirt / eaten to dirt at a top under turf: natural, absorbed
        long sp = j1.pos[soil];
        int sx = (int) (sp >> 38), sz = (int) (sp << 26 >> 38);
        w.bare.put((long) sx << 32 | (sz & 0xFFFFFFFFL), true);
        var natural = diff(j1, journal(w, r));
        require(natural.acceptable() && natural.naturalChanges() >= 1, "grass_block -> dirt at a turf position is absorbed (" + natural.summary() + ")");
        w.bare.clear();
        // 2. a plant planned for removal is eaten: the newer journal has nothing to write there; natural
        long pp = j1.pos[plantRemoval];
        w.edits.put(pp, "air");
        var eaten = diff(j1, journal(w, r));
        require(eaten.acceptable() && eaten.naturalChanges() == 1 && eaten.onlyOld() == 0, "eaten plant is absorbed (" + eaten.summary() + ")");
        w.edits.clear();
        // 3. a real change: someone put cobblestone where the works expect natural ground
        long cp = j1.pos[solid >= 0 ? solid : soil];
        w.edits.put(cp, "cobblestone");
        var real = diff(j1, journal(w, r));
        require(!real.acceptable() && real.otherChanges() >= 1, "a cobblestone in the works fails closed (" + real.summary() + ")");
        w.edits.clear();
        // 4. a ground change that moves the design (a column top one block higher): works differ, fails closed
        var raised = new World(new R4Earthworks.Ground() {
            final R4Earthworks.Ground g = ground(null, t, x0, z0, heading);
            @Override public int top(int x, int z) { return g.top(x, z) + (x == sx && z == sz ? 1 : 0); }
            @Override public int plants(int x, int z) { return g.plants(x, z); }
            @Override public boolean grassy(int x, int z) { return true; }
        });
        var moved = diff(j1, journal(raised, r));
        require(!moved.acceptable(), "a raised column fails closed (" + moved.summary() + ")");
        // 5. build then remove on the string world: natural change during the build is recorded, a later player edit is kept
        Map<Long, String> world = new HashMap<>();
        for (int i = 0; i < j1.pos.length; i++) world.put(j1.pos[i], j1.before[i]);
        String[] before = j1.before.clone();
        world.put(j1.pos[soil], "dirt");                 // grass eaten after the preflight, before its batch
        int natural5 = 0, conflicts = 0;
        for (int i = 0; i < j1.pos.length; i++) {
            String cur = world.get(j1.pos[i]);
            switch (R4Journal.build(cur, before[i], j1.after[i], NAMES)) {
                case WRITE -> world.put(j1.pos[i], j1.after[i]);
                case WRITE_NATURAL -> { before[i] = cur; natural5++; world.put(j1.pos[i], j1.after[i]); }
                case CONFLICT -> conflicts++;
                case ALREADY_DONE -> {}
            }
        }
        boolean built = true;
        for (int i = 0; i < j1.pos.length; i++) built &= world.get(j1.pos[i]).equals(j1.after[i]);
        require(built && natural5 == 1 && conflicts == 0, "build writes every entry, absorbs the natural change");
        world.put(j1.pos[plantRemoval], "torch");        // the player places something after the build
        int preserved = 0;
        for (int i = j1.pos.length - 1; i >= 0; i--) {
            switch (R4Journal.remove(world.get(j1.pos[i]), before[i], j1.after[i])) {
                case RESTORE -> world.put(j1.pos[i], before[i]);
                case PRESERVE -> preserved++;
                case ORIGINAL -> {}
            }
        }
        boolean restored = true;
        for (int i = 0; i < j1.pos.length; i++) if (i != plantRemoval) restored &= world.get(j1.pos[i]).equals(i == soil ? "dirt" : j1.before[i]);
        require(restored && preserved == 1 && world.get(j1.pos[plantRemoval]).equals("torch"), "remove restores the recorded originals and keeps the player's block");
        // 6. a block that is neither original nor target during the build is left alone (conflict)
        require(R4Journal.build("cobblestone", "dirt", "turf1", NAMES) == R4Journal.Step.CONFLICT, "unknown block during build is not overwritten");
        RESULTS.put("journal", "writes " + j1.pos.length + ", natural top change absorbed " + natural.naturalChanges() + ", eaten plant absorbed " + eaten.naturalChanges()
                + ", cobblestone refused (" + real.otherChanges() + " other), raised column refused (positions " + moved.onlyOld() + "/" + moved.onlyNew() + ", targets " + moved.targetChanged() + ")");
    }

    // ---- F: recipe ----
    static void recipeDeterminism() {
        var r = recipe(123.5, -456.5, 15, 300, 6);
        var g = ground(null, (a, c) -> 64 + 0.01 * a, 123.5, -456.5, 15);
        var d1 = r.withProfile(R4Profile.design(R4Earthworks.groundProfile(flat(r), g), 300).samples()).canonical();
        var d2 = R4Recipe.decode(d1.json());
        require(d1.json().equals(d2.json()) && d1.version().equals(d2.version()), "recipe round trip");
        require(d1.version().matches("[a-f0-9]{64}"), "recipe digest");
        RESULTS.put("recipe_json_bytes", d1.json().length());
    }
}
