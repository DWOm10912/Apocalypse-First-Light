package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.route.RoadClassSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Offline check of the C3.3b junctions (docs/worldgen/federal_junctions_c3_3b.md 6) over built networks:
 * <ul>
 *   <li>every T junction pad: the step to the host's paved edge between the two T1 and to the minor's end line
 *   (STEP), the landing grade on the minor's last Junction.LANDING before the host's edge and on the pad's centre line
 *   (Junction.LANDING_GRADE), the fillet radius (Junction.RADIUS), the triangulation's area, no height step inside the
 *   pad; its chunk mesh (JunctionMesh) covering the pad at the pad's height; the ground query (RoadSurfaceQuery)
 *   giving the pad's height; the works paving its whole columns; its sight triangles bare;</li>
 *   <li>every town corner (RoadDesign#corners): the smallest radius within CORNER_SPAN and the steepest grade within
 *   RoadNetworkBuild.REGRADE_REACH plus a margin;</li>
 *   <li>the signs (RoadSigns): a STOP sign at every junction, the side road and curve warnings counted (one left out
 *   where no place is clear is reported, not failed), every sign's foot on the works' finished ground in cut and in
 *   fill (FOOT_TOL).</li>
 * </ul>
 * Pure Java. Argument: one cached network file; none: every cached network of the checkout (RoadSurfaceCheck.CACHES).
 */
public final class JunctionCheck {
    private JunctionCheck() {}

    static final double STEP = 0.02, GRADE_TOL = 0.0015, HEIGHT_TOL = 0.01, QUERY_TOL = 0.005, CORNER_SPAN = 150, FOOT_TOL = 0.05;

    public static void main(String[] args) throws Exception {
        List<Path> files = new ArrayList<>();
        if (args.length > 0) files.add(Path.of(args[0]));
        else {
            TreeMap<String, Path> found = new TreeMap<>();
            for (String dir : RoadSurfaceCheck.CACHES) {
                Path d = Path.of(dir);
                if (!Files.isDirectory(d)) continue;
                try (Stream<Path> list = Files.list(d)) {
                    list.filter(p -> p.getFileName().toString().matches(RoadSurfaceCheck.PATTERN)).forEach(p -> found.putIfAbsent(p.getFileName().toString(), p));
                }
            }
            files.addAll(found.values());
        }
        if (files.isEmpty()) {
            System.out.println("JUNCTION CHECK: no cached road network (pass -ProadNetwork=<file>)");
            System.exit(2);
        }
        boolean all = true;
        for (Path file : files) {
            System.out.println("== " + file);
            all &= check(file);
        }
        System.out.println(all ? "JUNCTION CHECK PASS (" + files.size() + " networks)" : "JUNCTION CHECK FAIL");
        if (!all) System.exit(1);
    }

    static boolean check(Path file) throws Exception {
        RoadNetworkBuild.Result built = RoadNetworkBuild.build(Files.readString(file));
        List<RoadDesign> roads = RoadNetworkJson.designs(built.json());
        RoadWorks works = new RoadWorks(roads, RoadWorks.Water.NONE);
        boolean ok = true;
        int named = 0;
        for (RoadDesign d : roads) {
            if (d.hostStart != null) named++;
            if (d.hostEnd != null) named++;
        }
        System.out.printf(Locale.ROOT, "roads %d, junctions named %d, built %d, conflicts %d%n", roads.size(), named, works.junctions().size(), built.conflicts());
        if (named != works.junctions().size()) { System.out.println("  FAIL: a named junction could not be built"); ok = false; }
        for (Junction j : works.junctions()) ok &= pad(j, works);
        for (RoadDesign d : roads) for (double c : d.corners) ok &= corner(d, c);
        ok &= signs(works);
        return ok;
    }

    // ------------------------------------------------------------------ signs

    private static boolean signs(RoadWorks works) {
        List<TunnelMesh.Placement> all = RoadSigns.all(works, null);
        Map<String, Integer> count = new TreeMap<>();
        double foot = 0;
        String worst = "";
        for (TunnelMesh.Placement p : all) {
            count.merge(p.mesh(), 1, Integer::sum);
            // the interchange guide signs stand beyond the ditch's hinge, where the finished ground follows the natural one:
            // their feet are checked on a stand-in ground (GuideSignCheck)
            if ("guide_sign".equals(p.mesh())) continue;
            // the ground the works finish under the foot, over natural ground well above (cut) and well below (fill)
            for (double g : new double[]{p.y() + 6, p.y() - 6}) {
                double f = works.surface(p.x(), p.z(), g);
                double e = Double.isNaN(f) ? Double.POSITIVE_INFINITY : Math.abs(f - p.y());
                if (e > foot) { foot = e; worst = String.format(Locale.ROOT, "%s at %.1f %.1f (%s)", p.mesh(), p.x(), p.z(), g > p.y() ? "cut" : "fill"); }
            }
        }
        // C3.3b group 2: an exit ramp's terminal has its STOP, DO NOT ENTER and (where clear) WRONG WAY, an entrance's none,
        // neither a side road warning
        int junctions = 0, exits = 0, corners = 0;
        for (Junction j : works.junctions()) {
            if (!j.minor.section.oneWay) junctions++;
            else if (j.atEnd) exits++;
        }
        for (RoadDesign d : works.roads()) corners += d.corners.length;
        int stops = count.getOrDefault("stop_sign", 0), side = count.getOrDefault("side_road_sign", 0), curve = count.getOrDefault("curve_sign", 0);
        int dne = count.getOrDefault("do_not_enter_sign", 0), ww = count.getOrDefault("wrong_way_sign", 0);
        List<String> fails = new ArrayList<>();
        if (stops != junctions + exits) fails.add("STOP signs " + stops + " for " + junctions + " junctions and " + exits + " exit ramps");
        if (dne != exits) fails.add("DO NOT ENTER signs " + dne + " for " + exits + " exit ramps");
        if (side > 2 * junctions) fails.add("side road signs " + side + " for " + junctions + " junctions");
        if (foot > FOOT_TOL) fails.add(String.format(Locale.ROOT, "foot off the ground %.3f, %s", foot, worst));
        System.out.printf(Locale.ROOT, "  signs: STOP %d/%d, side road %d/%d, curve %d/%d, DO NOT ENTER %d/%d, WRONG WAY %d/%d, foot %.4f%s%n", stops,
                junctions + exits, side, 2 * junctions, curve, 2 * corners, dne, exits, ww, exits, all.isEmpty() ? 0 : foot,
                fails.isEmpty() ? "" : "  FAIL: " + String.join(", ", fails));
        return fails.isEmpty();
    }

    // ------------------------------------------------------------------ pads

    private static boolean pad(Junction j, RoadWorks works) {
        RoadDesign h = j.host, m = j.minor;
        double eh = h.section.edge, em = m.section.edge;
        List<String> fails = new ArrayList<>();
        // the host's edge between the two T1
        double hostStep = 0;
        for (double s = j.mouthFrom(); s <= j.mouthTo() + 1e-9; s += 0.25) {
            double[] p = h.point(s, j.side * eh);
            hostStep = Math.max(hostStep, Math.abs(j.height(p[0], p[1]) - h.pavementY(s, j.side * eh)));
        }
        if (hostStep > STEP) fails.add(String.format(Locale.ROOT, "host edge step %.3f", hostStep));
        // the minor's end line
        double endStep = 0;
        for (double u = -em; u <= em + 1e-9; u += 0.25) {
            double[] p = m.point(j.sEnd, u);
            endStep = Math.max(endStep, Math.abs(j.height(p[0], p[1]) - m.pavementY(j.sEnd, u)));
        }
        if (endStep > STEP) fails.add(String.format(Locale.ROOT, "end line step %.3f", endStep));
        // landing: the minor's last LANDING before the host's edge (on the minor), then the pad's centre line
        double landing = 0, landingAt = 0;
        boolean landingOnPad = false;
        for (double q = 0; q < Junction.LANDING; q += 1) {
            double s0 = j.sEdge - j.dir * (q + 1), s1 = j.sEdge - j.dir * q, g;
            boolean onPad = j.dir * (s1 - j.sEnd) > 0;
            if (!onPad) {
                if (s0 < 0 || s0 > m.length || s1 < 0 || s1 > m.length) continue;
                g = Math.abs(m.pavementY(s1, 0) - m.pavementY(s0, 0));
            } else {
                double[] a = m.point(s0, 0), b = m.point(s1, 0);
                g = Math.abs(j.height(b[0], b[1]) - j.height(a[0], a[1]));
            }
            if (g > landing) { landing = g; landingAt = q; landingOnPad = onPad; }
        }
        if (landing > Junction.LANDING_GRADE + GRADE_TOL)
            fails.add(String.format(Locale.ROOT, "landing grade %.2f %% %.0f m before the host's edge (%s)", landing * 100, landingAt, landingOnPad ? "pad" : "minor"));
        // fillets: circumradius of consecutive free outline points on the arcs
        double[] px = j.outlineX(), pz = j.outlineZ();
        int n = px.length;
        double rMin = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            int a = (i + n - 1) % n, c = (i + 1) % n;
            if (!j.free(a) || !j.free(i)) continue;
            double r = circumradius(px[a], pz[a], px[i], pz[i], px[c], pz[c]);
            if (r < 1000) rMin = Math.min(rMin, r);
        }
        if (rMin < Junction.RADIUS - 0.5) fails.add(String.format(Locale.ROOT, "fillet radius %.1f", rMin));
        // triangulation area
        double area = 0, triArea = 0;
        for (int i = 0; i < n; i++) { int k = (i + 1) % n; area += px[i] * pz[k] - px[k] * pz[i]; }
        area = Math.abs(area) / 2;
        int[] t = j.triangles();
        for (int k = 0; k + 2 < t.length; k += 3)
            triArea += Math.abs((px[t[k + 1]] - px[t[k]]) * (pz[t[k + 2]] - pz[t[k]]) - (pz[t[k + 1]] - pz[t[k]]) * (px[t[k + 2]] - px[t[k]])) / 2;
        if (Math.abs(triArea - area) > 0.001 * area + 0.01) fails.add(String.format(Locale.ROOT, "triangles %.2f m2 of %.2f", triArea, area));
        // inside: no step between neighbours 0.25 m apart; the mesh, the query, the works
        double[] b = j.bounds();
        double innerStep = 0, meshErr = 0, queryErr = 0;
        int samples = 0, uncovered = 0, noQuery = 0, unpaved = 0, columns = 0;
        Map<Long, List<RoadMesh.Triangle>> meshes = new HashMap<>();
        RoadSurfaceQuery.Out o = new RoadSurfaceQuery.Out();
        for (double x = Math.floor(b[0]) + 0.13; x < b[2]; x += 0.25)
            for (double z = Math.floor(b[1]) + 0.07; z < b[3]; z += 0.25) {
                if (!j.contains(x, z)) continue;
                double y = j.height(x, z);
                if (j.contains(x + 0.25, z)) innerStep = Math.max(innerStep, Math.abs(j.height(x + 0.25, z) - y));
                if (j.contains(x, z + 0.25)) innerStep = Math.max(innerStep, Math.abs(j.height(x, z + 0.25) - y));
                // a sixteenth of the points for the slower checks
                if (((int) Math.floor(x * 4) + (int) Math.floor(z * 4)) % 4 != 0) continue;
                samples++;
                int cx = Math.floorDiv((int) Math.floor(x), 16), cz = Math.floorDiv((int) Math.floor(z), 16);
                List<RoadMesh.Triangle> mesh = meshes.computeIfAbsent(RoadWorks.key(cx, cz), k -> JunctionMesh.chunk(j, cx, cz));
                double my = meshY(mesh, x - cx * 16, z - cz * 16);
                if (Double.isNaN(my)) uncovered++;
                else meshErr = Math.max(meshErr, Math.abs(my - y));
                if (!RoadSurfaceQuery.pavement(works, x, z, y + 0.3, o)) noQuery++;
                else queryErr = Math.max(queryErr, Math.abs(o.height - y));
            }
        for (int x = (int) Math.floor(b[0]); x <= b[2]; x++)
            for (int z = (int) Math.floor(b[1]); z <= b[3]; z++) {
                boolean inside = true;
                for (int a = 0; a <= 2 && inside; a++) for (int c = 0; c <= 2; c++) if (!j.contains(x + a * 0.5, z + c * 0.5)) { inside = false; break; }
                if (!inside) continue;
                columns++;
                if (works.pavedRange(x, z) == null) unpaved++;
            }
        if (innerStep > 0.03) fails.add(String.format(Locale.ROOT, "inner step %.3f per 0.25 m", innerStep));
        if (uncovered > 0) fails.add("mesh misses " + uncovered + " of " + samples);
        if (meshErr > HEIGHT_TOL) fails.add(String.format(Locale.ROOT, "mesh height off %.3f", meshErr));
        if (noQuery > 0) fails.add("query misses " + noQuery + " of " + samples);
        if (queryErr > QUERY_TOL) fails.add(String.format(Locale.ROOT, "query height off %.4f", queryErr));
        if (unpaved > 0) fails.add("works leave " + unpaved + " of " + columns + " pad columns unpaved");
        // sight triangles: the eye's side of the mouth, a point a third of the way along each
        int bareMiss = 0;
        double[] e = m.point(j.stationAt(j.dir * m.section.lane / 2, Junction.STOP_FAR + Junction.SIGHT_BACK), j.dir * m.section.lane / 2);
        for (int k = -1; k <= 1; k += 2) {
            double[] f = h.point(Math.max(0, Math.min(h.length, j.sMouth + k * Junction.SIGHT_ALONG / 3)), 0);
            double[] mid = h.point(j.sMouth, 0);
            double x = (e[0] + f[0] + mid[0]) / 3, z = (e[1] + f[1] + mid[1]) / 3;
            if (j.inSight(x, z) && !works.bare((int) Math.floor(x), (int) Math.floor(z))) bareMiss++;
        }
        if (bareMiss > 0) fails.add("sight triangle not bare");
        System.out.printf(Locale.ROOT, "  %s %s %s at %.0f: angle side %d, pad %.0f m2, grade %.2f %%, steps host %.4f end %.4f inner %.3f, landing %.2f %%, fillet R %.1f, mesh %.4f (%d), query %.4f, columns %d%s%n",
                m.id, j.atEnd ? "end" : "start", h.id, j.sMouth, j.side, area, j.grade() * 100, hostStep, endStep, innerStep, landing * 100, rMin, meshErr, samples, queryErr, columns,
                fails.isEmpty() ? "" : "  FAIL: " + String.join(", ", fails));
        return fails.isEmpty();
    }

    private static double circumradius(double ax, double az, double bx, double bz, double cx, double cz) {
        double a = Math.hypot(bx - cx, bz - cz), b = Math.hypot(ax - cx, az - cz), c = Math.hypot(ax - bx, az - bz);
        double cr = Math.abs((bx - ax) * (cz - az) - (bz - az) * (cx - ax));
        return cr < 1e-12 ? Double.POSITIVE_INFINITY : a * b * c / (2 * cr);
    }

    /** The height of the mesh's asphalt at a chunk-relative point (plan), NaN where none covers it. */
    private static double meshY(List<RoadMesh.Triangle> mesh, double x, double z) {
        for (RoadMesh.Triangle t : mesh) {
            if (t.material() != RoadMesh.Material.ASPHALT) continue;
            RoadMesh.Vertex a = t.a(), b = t.b(), c = t.c();
            double d = (b.z() - c.z()) * (a.x() - c.x()) + (c.x() - b.x()) * (a.z() - c.z());
            if (Math.abs(d) < 1e-12) continue;
            double l1 = ((b.z() - c.z()) * (x - c.x()) + (c.x() - b.x()) * (z - c.z())) / d;
            double l2 = ((c.z() - a.z()) * (x - c.x()) + (a.x() - c.x()) * (z - c.z())) / d, l3 = 1 - l1 - l2;
            if (l1 < -1e-9 || l2 < -1e-9 || l3 < -1e-9) continue;
            return l1 * a.y() + l2 * b.y() + l3 * c.y();
        }
        return Double.NaN;
    }

    // ------------------------------------------------------------------ town corners

    private static boolean corner(RoadDesign d, double c) {
        double kMax = 0;
        for (double s = Math.max(0, c - CORNER_SPAN); s <= Math.min(d.length, c + CORNER_SPAN); s += 1) kMax = Math.max(kMax, Math.abs(d.curvature(s)));
        double r = kMax > 0 ? 1 / kMax : Double.POSITIVE_INFINITY;
        double reach = RoadNetworkBuild.REGRADE_REACH + 200, g = 0;
        for (double s = Math.max(0, c - reach); s + 2 <= Math.min(d.length, c + reach); s += 2)
            g = Math.max(g, Math.abs(d.datum(s + 2) - d.datum(s)) / 2);
        List<String> fails = new ArrayList<>();
        if (r < RoadNetworkBuild.TOWN_RADIUS * 0.95) fails.add(String.format(Locale.ROOT, "radius %.0f", r));
        if (g > RoadClassSpec.STATE.gradeMountain + GRADE_TOL) fails.add(String.format(Locale.ROOT, "grade %.2f %%", g * 100));
        System.out.printf(Locale.ROOT, "  corner %s at %.0f: R %.0f, grade %.2f %%%s%n", d.id, c, r, g * 100, fails.isEmpty() ? "" : "  FAIL: " + String.join(", ", fails));
        return fails.isEmpty();
    }
}
