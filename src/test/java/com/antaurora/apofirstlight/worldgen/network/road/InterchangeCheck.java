package com.antaurora.apofirstlight.worldgen.network.road;

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
 * Offline check of the C3.3b group 2 diamond interchanges (docs/worldgen/federal_interchanges_c3_3b.md 11) over built
 * networks:
 * <ul>
 *   <li>the build's interchange report (sites built and skipped, each ramp's radius, grade and K), the overpass clearance,
 *   the remaining conflicts;</li>
 *   <li>the works (RoadWorks) of every interchange on a stand-in ground (the interstate's datum less GROUND_DROP; at a system
 *   interchange the nearer of H's and the ending interstate's): under
 *   each overpass the columns over the interstate's pavement paved with the deck's cells over them (DECK_GAP free blocks at
 *   least), along each attached ramp the ramp's pavement meeting the interstate's paved edge (SEAM), the columns of the
 *   ramp's and the interstate's pavement paved, the interstate's pavement winning on its band (the hit, the ground
 *   query); the finished surface around the interchange on a GRID m grid: its steepest step between neighbours off the
 *   pavement and away from the overpass's abutments (reported, STEEP fails);</li>
 *   <li>the meshes along each attached ramp (RoadMesh with RoadWorks.RampMarks) on a SAMPLE m grid: the interstate's and
 *   the ramp's asphalt cover the union of their paved bands exactly once (no hole, no overlap, MESH_TOL off the design),
 *   the ramp's asphalt never on the interstate's band, its yellow never on the interstate's lanes, the gore's chevrons
 *   drawn, every line on its asphalt (MESH_TOL); along the interstate's edge line beside the ramp: solid outside the
 *   ramp's gap and dotted stretch, none in the gap, DOT / DOT_PERIOD of the dotted stretch painted.</li>
 * </ul>
 * Pure Java. Arguments: cached network files; none: every cached network of the checkout (RoadSurfaceCheck.CACHES).
 */
public final class InterchangeCheck {
    private InterchangeCheck() {}

    static final double GROUND_DROP = 0.5, SEAM = 0.02, QUERY_TOL = 0.005, GRID = 2, STEEP = 1.5;
    /** Mesh sampling: the grid, the band kept off the paved edges, the height tolerance, the dotted line's painted share tolerance, m. */
    static final double SAMPLE = 0.25, EDGE_SKIP = 0.03, MESH_TOL = 0.01, DOT_TOL = 0.06;
    static final int DECK_GAP = 4;
    /** Mesh heights this far from a sample's design height belong to another level (a system interchange's deck). */
    static final double LEVEL = 3;

    public static void main(String[] args) throws Exception {
        List<Path> files = new ArrayList<>();
        for (String a : args) files.add(Path.of(a));
        if (files.isEmpty()) {
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
            System.out.println("INTERCHANGE CHECK: no cached road network (pass -ProadNetwork=<file>)");
            System.exit(2);
        }
        boolean all = true;
        for (Path file : files) {
            System.out.println("== " + file);
            all &= check(file);
        }
        System.out.println(all ? "INTERCHANGE CHECK PASS (" + files.size() + " networks)" : "INTERCHANGE CHECK FAIL");
        if (!all) System.exit(1);
    }

    static boolean check(Path file) throws Exception {
        long t0 = System.nanoTime();
        RoadNetworkBuild.Result built = RoadNetworkBuild.build(Files.readString(file));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        boolean ok = true;
        for (String l : built.report())
            if (l.startsWith("interchange") || l.startsWith("  ramp:") || l.startsWith("CONFLICT") || l.startsWith("junction dropped")) System.out.println("  " + l);
        System.out.printf(Locale.ROOT, "  build %d ms, conflicts %d%n", ms, built.conflicts());
        if (Boolean.getBoolean("afl.noInterchanges")) return ok;
        List<RoadDesign> roads = RoadNetworkJson.designs(built.json());
        RoadWorks works = new RoadWorks(roads, RoadWorks.Water.NONE);
        // the interchanges: their ramps by site (the id up to the last ':')
        Map<String, List<RoadDesign>> sites = new TreeMap<>();
        for (RoadDesign d : roads) if (d.attach != null) sites.computeIfAbsent(d.id.substring(0, d.id.lastIndexOf(':')), k -> new ArrayList<>()).add(d);
        for (Map.Entry<String, List<RoadDesign>> e : sites.entrySet()) ok &= site(e.getKey(), e.getValue(), roads, works);
        return ok;
    }

    /**
     * The stand-in ground: the interstate's datum at the point's station less GROUND_DROP (the interstate in a low fill),
     * level across; off its reach a plane through station sc at its grade.
     */
    private static RoadWorks.Ground ground(RoadDesign host, double sc, RoadDesign ending) {
        double[] p = host.point(sc, 0), q = host.point(Math.min(host.length, sc + 1), 0);
        double len = Math.hypot(q[0] - p[0], q[1] - p[1]), tx = (q[0] - p[0]) / len, tz = (q[1] - p[1]) / len;
        double s0 = Math.max(0, sc - 10), s1 = Math.min(host.length, sc + 10), g = (host.datum(s1) - host.datum(s0)) / (s1 - s0), y = host.datum(sc) - GROUND_DROP;
        return (x, z) -> {
            double[] l = host.local(x, z, 400);
            // a system interchange: nearer the ending interstate (Q) than H, Q's datum (Q's end may lie metres off H's level;
            // H's level there would put Q in a deep cut no real ground gives it)
            double[] lq = ending == null ? null : ending.local(x, z, 400);
            if (lq != null && (l == null || Math.abs(lq[1]) < Math.abs(l[1])))
                return ending.datum(Math.max(0, Math.min(ending.length, lq[0]))) - GROUND_DROP;
            if (l != null) return host.datum(Math.max(0, Math.min(host.length, l[0]))) - GROUND_DROP;
            return y + g * ((x - p[0]) * tx + (z - p[1]) * tz);
        };
    }

    /** A system interchange's ending interstate (Q): the road other than H and the site's ramps that a ramp's open end lies on. */
    private static RoadDesign ending(String key, RoadDesign host, List<RoadDesign> roads) {
        for (RoadDesign r : roads) {
            if (!r.id.startsWith(key + ":")) continue;
            for (int e = 0; e < 2; e++) {
                if (!(e == 0 ? r.openStart : r.openEnd)) continue;
                double[] p = r.point(e == 0 ? 0 : r.length, 0);
                for (RoadDesign d : roads) {
                    if (d == host || d.id.startsWith("system:") || d.attach != null) continue;
                    double[] l = d.local(p[0], p[1], 30);
                    if (l != null && (l[0] < 1 || l[0] > d.length - 1)) return d;
                }
            }
        }
        return null;
    }

    private static boolean site(String key, List<RoadDesign> ramps, List<RoadDesign> roads, RoadWorks works) {
        List<String> fails = new ArrayList<>();
        // the interstate: the ramps' host that is not one of them (a system interchange's direct ramps split off and merge
        // into its left-turn ramps, C3.3b group 3)
        RoadDesign host = null;
        for (RoadDesign r : ramps) {
            RoadDesign h = works.attachedHost(r);
            if (h != null && !h.id.startsWith(key + ":")) host = h;
        }
        if (host == null) {
            System.out.println("  " + key + ": FAIL: the ramps' interstate is not in the network");
            return false;
        }
        // ---------------------------------------------------------------- the overpasses: a diamond's crossroad's OVERPASS
        // span nearest the ramps; a system interchange's own decks (its left-turn ramps')
        List<RoadDesign> crosses = new ArrayList<>();
        List<RoadDesign.Span> overs = new ArrayList<>();
        if (key.startsWith("system:")) {
            for (RoadDesign r : roads)
                if (r.id.startsWith(key + ":"))
                    for (RoadDesign.Span sp : r.spans)
                    if (sp.over()) { crosses.add(r); overs.add(sp); }
        } else {
            double[] c0 = ramps.get(0).point(0, 0);
            RoadDesign cross = null;
            RoadDesign.Span over = null;
            double bestD = Double.POSITIVE_INFINITY;
            for (RoadDesign d : roads)
                for (RoadDesign.Span sp : d.spans) {
                    if (!sp.over()) continue;
                    double[] m = d.point((sp.s0() + sp.s1()) / 2, 0);
                    double dist = Math.hypot(m[0] - c0[0], m[1] - c0[1]);
                    if (dist < bestD) { bestD = dist; cross = d; over = sp; }
                }
            if (cross != null && bestD <= 1500) { crosses.add(cross); overs.add(over); }
        }
        int twoLevel = 0, deckOnly = 0, underPaved = 0, underUnpaved = 0, minGap = Integer.MAX_VALUE;
        String gapAt = "";
        RoadWorks.Ground ground;
        RoadDesign ending = key.startsWith("system:") ? ending(key, host, roads) : null;
        if (key.startsWith("system:") && ending == null) fails.add("no ending interstate");
        if (crosses.isEmpty()) {
            fails.add("no overpass");
            ground = ground(host, 0, ending);
        } else {
            double[] m = crosses.get(0).point((overs.get(0).s0() + overs.get(0).s1()) / 2, 0), lm = host.local(m[0], m[1], 50);
            ground = ground(host, lm == null ? 0 : Math.max(0, Math.min(host.length, lm[0])), ending);
        }
        for (int k = 0; k < crosses.size(); k++) {
            RoadDesign cross = crosses.get(k);
            RoadDesign.Span over = overs.get(k);
            double reach = cross.section.edge + BridgeMesh.CELL_OUT;
            double[] b = bounds(cross, over.s0(), over.s1(), reach);
            for (int x = (int) Math.floor(b[0]); x <= b[2]; x++)
                for (int z = (int) Math.floor(b[1]); z <= b[3]; z++) {
                    RoadWorks.Hit oh = works.overHit(x + 0.5, z + 0.5);
                    if (oh == null || oh.road() != cross) continue;
                    RoadWorks.Column c = works.column(x, z, ground);
                    RoadWorks.Column deck = c.deck() != null ? c.deck() : c;
                    double[] lt = cross.local(x + 0.5, z + 0.5, reach + 1);
                    double t = lt == null ? 0 : Math.min(lt[0] - over.s0(), over.s1() - lt[0]);
                    if (c.deck() != null) {
                        twoLevel++;
                        // the traffic's clearance: over a paved finish (the deck's own cells, not a bent's columns)
                        int gap = deck.kind() == RoadWorks.Kind.ABUTMENT || c.kind() != RoadWorks.Kind.PAVED ? Integer.MAX_VALUE : deck.bottom() - c.top() - 1;
                        if (gap < minGap) {
                            minGap = gap;
                            double[] lc = cross.local(x + 0.5, z + 0.5, reach + 1);
                            gapAt = String.format(Locale.ROOT, "%d %d (%s %s, deck %s bottom %d; t %.1f, v %.1f)", x, z, c.kind(), c.top(), deck.kind(), deck.bottom(),
                                    lc == null ? Double.NaN : Math.min(lc[0] - over.s0(), over.s1() - lc[0]), lc == null ? Double.NaN : Math.abs(lc[1]));
                        }
                    } else if (deck.kind() == RoadWorks.Kind.DECK || deck.kind() == RoadWorks.Kind.ABUTMENT || deck.kind() == RoadWorks.Kind.BARRIER) deckOnly++;
                    // over the interstate's paved band: paved under the deck
                    double[] l = host.local(x + 0.5, z + 0.5, host.section.edge + 1);
                    boolean band = l != null && l[0] >= 0 && l[0] <= host.length && host.section.paved(Math.abs(l[1]));
                    if (band && RoadWorks.pavedRange(host, x, z) != null) {
                        if (c.kind() == RoadWorks.Kind.PAVED && c.deck() != null) underPaved++;
                        else underUnpaved++;
                    }
                }
        }
        if (underUnpaved > 0) fails.add(underUnpaved + " interstate columns under the deck not paved two-level");
        if (twoLevel > 0 && minGap < DECK_GAP) fails.add("deck gap " + minGap + " blocks at " + gapAt);
        if (!crosses.isEmpty() && twoLevel == 0) fails.add("no two-level column");
        // ---------------------------------------------------------------- the attached ramps
        double seam = 0, queryErr = 0;
        int hitMiss = 0, unpaved = 0, columns = 0, noQuery = 0;
        String seamAt = "";
        RoadSurfaceQuery.Out o = new RoadSurfaceQuery.Out();
        for (RoadDesign r : ramps) {
            RoadDesign.Attach at = r.attach;
            RoadDesign rh = works.attachedHost(r);
            if (rh == null) { fails.add(r.id.substring(r.id.lastIndexOf(':') + 1) + " has no host"); continue; }
            double a0 = at.atEnd() ? Math.max(0, at.nose()) : 0, a1 = at.atEnd() ? r.length : Math.min(r.length, at.nose());
            for (double s = a0; s <= a1; s += 0.5) {
                // the seam: the ramp's pavement where it crosses the interstate's paved edge
                for (double u = -r.section.edge; u <= r.section.edge + 1e-9; u += 0.1) {
                    double[] p = r.point(s, u), l = rh.local(p[0], p[1], rh.section.edge + 4);
                    if (l == null || l[0] < 0 || l[0] > rh.length) continue;
                    double v = Math.abs(l[1]);
                    if (Math.abs(v - rh.section.edge) > 0.05) continue;
                    double e = Math.abs(r.pavementY(s, u) - rh.pavementY(l[0], Math.copySign(Math.min(v, rh.section.edge), l[1])));
                    if (e > seam) { seam = e; seamAt = String.format(Locale.ROOT, "%s s %.1f", r.id.substring(r.id.lastIndexOf(':') + 1), s); }
                }
                // a point on the interstate's band inside the ramp: the interstate's (hit, ground query)
                double[] p = r.point(s, 0), l = rh.local(p[0], p[1], rh.section.edge + 4);
                if (l != null && rh.section.paved(Math.abs(l[1])) && l[0] >= 0 && l[0] <= rh.length) {
                    RoadWorks.Hit h = works.hit(p[0], p[1], RoadWorks.REACH);
                    if (h == null || h.road() != rh) hitMiss++;
                    double y = rh.pavementY(l[0], l[1]);
                    if (!RoadSurfaceQuery.pavement(works, p[0], p[1], y + 0.3, o)) noQuery++;
                    else queryErr = Math.max(queryErr, Math.abs(o.height - y));
                }
            }
            // the columns wholly on the union of the ramp's and the interstate's bands, along the attached stretch
            double[] b = bounds(r, a0, a1, r.section.edge);
            for (int x = (int) Math.floor(b[0]); x <= b[2]; x++)
                for (int z = (int) Math.floor(b[1]); z <= b[3]; z++) {
                    boolean inside = true;
                    for (int i = 0; i <= 2 && inside; i++)
                        for (int j = 0; j <= 2; j++) {
                            double px = x + i * 0.5, pz = z + j * 0.5;
                            double[] lr = r.local(px, pz, r.section.edge + 1), lh = rh.local(px, pz, rh.section.edge + 1);
                            boolean onR = lr != null && lr[0] >= a0 && lr[0] <= a1 && r.section.paved(Math.abs(lr[1]));
                            boolean onH = lh != null && lh[0] >= 0 && lh[0] <= rh.length && rh.section.paved(Math.abs(lh[1]));
                            if (!onR && !onH || !onR && !nearRamp(r, px, pz, a0, a1)) { inside = false; break; }
                        }
                    if (!inside) continue;
                    columns++;
                    if (works.column(x, z, ground).kind() != RoadWorks.Kind.PAVED) unpaved++;
                }
        }
        if (seam > SEAM) fails.add(String.format(Locale.ROOT, "seam %.3f at %s", seam, seamAt));
        if (hitMiss > 0) fails.add(hitMiss + " ramp points on the interstate's band not the interstate's");
        if (noQuery > 0) fails.add("query misses " + noQuery);
        if (queryErr > QUERY_TOL) fails.add(String.format(Locale.ROOT, "query off %.4f", queryErr));
        if (unpaved > 0) fails.add(unpaved + " of " + columns + " ramp columns unpaved");
        String mesh = meshes(ramps, works, fails);
        // -Dafl.debugLine=x0,z0,x1,z1: the finished surface along a line (debug)
        String dl = System.getProperty("afl.debugLine");
        if (dl != null) {
            String[] v = dl.split(",");
            double lx0 = Double.parseDouble(v[0]), lz0 = Double.parseDouble(v[1]), lx1 = Double.parseDouble(v[2]), lz1 = Double.parseDouble(v[3]);
            int k = (int) Math.ceil(Math.hypot(lx1 - lx0, lz1 - lz0) / 0.5);
            for (int i = 0; i <= k; i++) {
                double x = lx0 + (lx1 - lx0) * i / k, z = lz0 + (lz1 - lz0) * i / k;
                System.out.println("LINE " + where(works, ground, x, z));
            }
        }
        // ---------------------------------------------------------------- the finished surface around the interchange
        double x0 = Double.POSITIVE_INFINITY, z0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, z1 = Double.NEGATIVE_INFINITY;
        for (RoadDesign r : ramps) {
            double[] b = bounds(r, 0, r.length, 30);
            x0 = Math.min(x0, b[0]); z0 = Math.min(z0, b[1]); x1 = Math.max(x1, b[2]); z1 = Math.max(z1, b[3]);
        }
        int nx = (int) Math.ceil((x1 - x0) / GRID) + 1, nz = (int) Math.ceil((z1 - z0) / GRID) + 1;
        double[] f = new double[nx * nz], gr = new double[nx * nz];
        boolean[] skip = new boolean[nx * nz];
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                double x = x0 + i * GRID, z = z0 + j * GRID;
                f[i + j * nx] = works.finished(x, z, ground);
                gr[i + j * nx] = ground.at(x, z);
                // the pavement and its bevels, the overpass's ends (its abutment walls), a pad
                RoadWorks.Hit h = works.hit(x, z, RoadWorks.REACH);
                boolean s = h != null && !RoadWorks.elevated(h) && h.beyond() == 0 && h.v() <= h.road().section.edge + 1 || works.pad(x, z) != null;
                // a system interchange: only where its own roads' works reach (its box takes in Q's and H's own works further
                // off, over the stand-in ground)
                if (!s && key.startsWith("system:")) {
                    boolean own = false;
                    RoadDesign[] nr = works.roadsNear(x, z);
                    if (nr != null)
                        for (RoadDesign d : nr) {
                            if (!d.id.startsWith(key + ":")) continue;
                            double[] l = d.local(x, z, RoadWorks.REACH);
                            if (l != null && l[0] >= (d.openStart ? 0 : -RoadDesign.END) && l[0] <= d.length + (d.openEnd ? 0 : RoadDesign.END)) own = true;
                        }
                    if (!own) s = true;
                }
                for (int k = 0; k < crosses.size() && !s; k++) {
                    RoadDesign cross = crosses.get(k);
                    RoadDesign.Span over = overs.get(k);
                    double[] l = cross.local(x, z, RoadWorks.REACH);
                    if (l != null && Math.min(Math.abs(l[0] - over.s0()), Math.abs(l[0] - over.s1())) < 2 && Math.abs(l[1]) < cross.section.edge + 4) s = true;
                    // a system interchange's decks: under them the spill-through slopes (counted as two-level columns above)
                    if (l != null && key.startsWith("system:") && l[0] >= over.s0() && l[0] <= over.s1() && Math.abs(l[1]) < cross.section.edge + RoadWorks.SPILL_FADE) s = true;
                }
                skip[i + j * nx] = s;
            }
        double steep = 0;
        String steepAt = "";
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int k = i + j * nx;
                if (skip[k]) continue;
                for (int[] dd : new int[][]{{1, 0}, {0, 1}}) {
                    int ii = i + dd[0], jj = j + dd[1];
                    // not where the stand-in ground itself jumps (its projection onto the interstate changes branch)
                    if (ii >= nx || jj >= nz || skip[ii + jj * nx] || Math.abs(gr[ii + jj * nx] - gr[k]) > 0.5) continue;
                    double e = Math.abs(f[ii + jj * nx] - f[k]);
                    if (e > steep) { steep = e; steepAt = String.format(Locale.ROOT, "%.0f %.0f", x0 + i * GRID, z0 + j * GRID); }
                    if (e > STEEP && Boolean.getBoolean("afl.debugInterchanges"))
                        System.out.printf(Locale.ROOT, "DEBUG step %.2f: %s | %s%n", e, where(works, ground, x0 + i * GRID, z0 + j * GRID), where(works, ground, x0 + ii * GRID, z0 + jj * GRID));
                }
            }
        if (steep > STEEP) fails.add(String.format(Locale.ROOT, "surface step %.2f per %.0f m at %s", steep, GRID, steepAt));
        System.out.printf(Locale.ROOT, "  %s: overpass %s two-level %d (gap >= %d), deck only %d, under-deck paved %d; ramps seam %.4f, query %.4f, columns %d; surface step %.2f at %s; %s%s%n",
                key, crosses.isEmpty() ? "-" : crosses.size() > 1 ? crosses.size() + " decks" : crosses.get(0).id, twoLevel, minGap == Integer.MAX_VALUE ? -1 : minGap, deckOnly, underPaved, seam, queryErr, columns, steep, steepAt,
                mesh, fails.isEmpty() ? "" : "  FAIL: " + String.join(", ", fails));
        return fails.isEmpty();
    }

    // ---------------------------------------------------------------- meshes

    /** A chunk's triangles of one road in world coordinates, bucketed by 1 m cell (their plan bounds). */
    private static final class Mesh {
        final List<RoadMesh.Triangle> tris = new ArrayList<>();
        final List<List<Integer>> cells = new ArrayList<>(256);

        Mesh(List<RoadMesh.Triangle> rel, int cx, int cz) {
            for (int i = 0; i < 256; i++) cells.add(new ArrayList<>(4));
            double ox = cx * 16, oz = cz * 16;
            for (RoadMesh.Triangle t : rel) {
                RoadMesh.Triangle w = new RoadMesh.Triangle(t.material(), shift(t.a(), ox, oz), shift(t.b(), ox, oz), shift(t.c(), ox, oz), t.nx(), t.ny(), t.nz());
                int k = tris.size();
                tris.add(w);
                int x0 = cell(Math.min(t.a().x(), Math.min(t.b().x(), t.c().x()))), x1 = cell(Math.max(t.a().x(), Math.max(t.b().x(), t.c().x())));
                int z0 = cell(Math.min(t.a().z(), Math.min(t.b().z(), t.c().z()))), z1 = cell(Math.max(t.a().z(), Math.max(t.b().z(), t.c().z())));
                for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) cells.get(x | z << 4).add(k);
            }
        }

        private static int cell(double v) { return Math.max(0, Math.min(15, (int) Math.floor(v))); }

        private static RoadMesh.Vertex shift(RoadMesh.Vertex v, double ox, double oz) { return new RoadMesh.Vertex(v.x() + ox, v.y(), v.z() + oz, v.u(), v.v()); }

        /** The heights of the triangles of material m (asphalt: tops only) covering world point (x, z). */
        List<Double> at(double x, double z, RoadMesh.Material m) {
            List<Double> out = new ArrayList<>(2);
            int lx = (int) Math.floor(x - Math.floor(x / 16) * 16), lz = (int) Math.floor(z - Math.floor(z / 16) * 16);
            for (int k : cells.get(Math.max(0, Math.min(15, lx)) | Math.max(0, Math.min(15, lz)) << 4)) {
                RoadMesh.Triangle t = tris.get(k);
                if (t.material() != m || m == RoadMesh.Material.ASPHALT && t.ny() < 0.95) continue;
                double y = inside(t, x, z);
                if (!Double.isNaN(y)) out.add(y);
            }
            return out;
        }
    }

    /** The triangle's height at (x, z) where it covers it in plan (edges included), else NaN. */
    private static double inside(RoadMesh.Triangle t, double x, double z) {
        double ax = t.a().x(), az = t.a().z(), bx = t.b().x(), bz = t.b().z(), cx = t.c().x(), cz = t.c().z();
        double d = (bz - cz) * (ax - cx) + (cx - bx) * (az - cz);
        if (Math.abs(d) < 1e-12) return Double.NaN;
        double l1 = ((bz - cz) * (x - cx) + (cx - bx) * (z - cz)) / d, l2 = ((cz - az) * (x - cx) + (ax - cx) * (z - cz)) / d, l3 = 1 - l1 - l2;
        if (l1 < -1e-9 || l2 < -1e-9 || l3 < -1e-9) return Double.NaN;
        return l1 * t.a().y() + l2 * t.b().y() + l3 * t.c().y();
    }

    /**
     * The meshes along each attached ramp of a site (see the class comment); fails added, the summary returned.
     */
    private static String meshes(List<RoadDesign> ramps, RoadWorks works, List<String> fails) {
        Map<RoadDesign, Map<Long, Mesh>> meshesByHost = new HashMap<>();
        int holes = 0, overlaps = 0, inside = 0, yellowIn = 0, chevrons = 0, samples = 0, solidMiss = 0, gapPaint = 0;
        double meshErr = 0, lineErr = 0, dotShare = 1;
        String holeAt = "", overAt = "", lineAt = "", missAt = "";
        for (RoadDesign r : ramps) {
            RoadDesign host = works.attachedHost(r);
            if (host == null) continue;
            Map<Long, Mesh> hostMeshes = meshesByHost.computeIfAbsent(host, k -> new HashMap<>());
            double vLine = host.section.lanesOut + RoadMesh.LINE / 2;
            RoadWorks.RampMarks mk = null;
            for (RoadWorks.RampMarks m : works.marksOf(r)) if (m.ramp() == r) mk = m;
            if (mk == null) { fails.add(r.id.substring(r.id.lastIndexOf(':') + 1) + " has no marks"); continue; }
            Map<Long, Mesh> rampMeshes = new HashMap<>();
            RoadDesign.Attach at = r.attach;
            double a0 = at.atEnd() ? Math.max(0, at.nose() - 10) : 0, a1 = at.atEnd() ? r.length : Math.min(r.length, at.nose() + 10);
            double g0 = Math.min(at.paint(), at.nose()), g1 = Math.max(at.paint(), at.nose());
            double[] b = bounds(r, a0, a1, r.section.edge + 8);
            for (double x = Math.floor(b[0]); x <= b[2]; x += SAMPLE)
                for (double z = Math.floor(b[1]); z <= b[3]; z += SAMPLE) {
                    double px = x + SAMPLE / 2, pz = z + SAMPLE / 2;
                    int cx = Math.floorDiv((int) Math.floor(px), 16), cz = Math.floorDiv((int) Math.floor(pz), 16);
                    long key = RoadWorks.key(cx, cz);
                    Mesh hm = hostMeshes.computeIfAbsent(key, k -> new Mesh(RoadMesh.chunk(host, cx, cz, works.junctionsOf(host), works.marksOf(host)), cx, cz));
                    Mesh rm = rampMeshes.computeIfAbsent(key, k -> new Mesh(RoadMesh.chunk(r, cx, cz, works.junctionsOf(r), works.marksOf(r)), cx, cz));
                    double[] lh = host.local(px, pz, host.section.edge + 2), lr = r.local(px, pz, 12);
                    if (lr == null || lr[0] < a0 || lr[0] > a1) continue;
                    double vh = lh == null || lh[0] < 0 || lh[0] > host.length ? Double.POSITIVE_INFINITY : Math.abs(lh[1]);
                    boolean onHost = vh <= host.section.edge - EDGE_SKIP && vh >= host.section.halfMedian + EDGE_SKIP, onRamp = vh >= host.section.edge + EDGE_SKIP && Math.abs(lr[1]) <= r.section.edge - EDGE_SKIP;
                    // the asphalt: exactly one top over the union of the paved bands
                    if (onHost || onRamp) {
                        samples++;
                        double want = onHost ? host.pavementY(lh[0], lh[1]) : r.pavementY(lr[0], lr[1]);
                        List<Double> ys = level(hm.at(px, pz, RoadMesh.Material.ASPHALT), want);
                        ys.addAll(level(rm.at(px, pz, RoadMesh.Material.ASPHALT), want));
                        if (ys.isEmpty()) { holes++; holeAt = String.format(Locale.ROOT, "%.2f %.2f", px, pz); }
                        if (ys.size() > 1) { overlaps++; overAt = String.format(Locale.ROOT, "%.2f %.2f", px, pz); }
                        for (double y : ys) meshErr = Math.max(meshErr, Math.abs(y - want));
                        if (onHost && !level(rm.at(px, pz, RoadMesh.Material.ASPHALT), want).isEmpty()) inside++;
                    }
                    // the lines: on their asphalt; the ramp's yellow never on the interstate's lanes; the chevrons
                    for (RoadMesh.Material m : new RoadMesh.Material[]{RoadMesh.Material.WHITE, RoadMesh.Material.YELLOW}) {
                        List<Double> lines = new ArrayList<>(hm.at(px, pz, m));
                        List<Double> rampLines = rm.at(px, pz, m);
                        lines.addAll(rampLines);
                        if (lines.isEmpty()) continue;
                        List<Double> ys = new ArrayList<>(hm.at(px, pz, RoadMesh.Material.ASPHALT));
                        ys.addAll(rm.at(px, pz, RoadMesh.Material.ASPHALT));
                        for (double y : lines) {
                            double e = Double.POSITIVE_INFINITY;
                            for (double a : ys) e = Math.min(e, Math.abs(y - a));
                            if (!ys.isEmpty() && e > lineErr) { lineErr = e; lineAt = String.format(Locale.ROOT, "%s %.2f %.2f", m, px, pz); }
                        }
                        if (m == RoadMesh.Material.YELLOW && !rampLines.isEmpty() && vh < vLine) yellowIn++;
                        // a chevron: the ramp's white in the gore, clear of the gore's two lines
                        double o = mk.outward() * lr[1];
                        if (m == RoadMesh.Material.WHITE && !rampLines.isEmpty() && lr[0] > g0 && lr[0] < g1 && vh > vLine + 0.05
                                && o < -(r.section.lanesOut + RoadMesh.LINE / 2) - 0.05) chevrons++;
                    }
                }
            // the interstate's edge line beside the ramp
            int dotSamples = 0, dotPainted = 0;
            double lo = Math.min(mk.gap0(), mk.dot0()) - 30, hi = Math.max(mk.gap1(), mk.dot1()) + 30;
            for (double sh = Math.max(0, lo); sh <= Math.min(host.length, hi); sh += SAMPLE / 2) {
                double[] pt = host.point(sh, mk.side() * host.section.lanesOut);
                int cx = Math.floorDiv((int) Math.floor(pt[0]), 16), cz = Math.floorDiv((int) Math.floor(pt[1]), 16);
                Mesh hm = hostMeshes.computeIfAbsent(RoadWorks.key(cx, cz), k -> new Mesh(RoadMesh.chunk(host, cx, cz, works.junctionsOf(host), works.marksOf(host)), cx, cz));
                boolean painted = !hm.at(pt[0], pt[1], RoadMesh.Material.WHITE).isEmpty();
                boolean inGap = sh > mk.gap0() + 0.05 && sh < mk.gap1() - 0.05, inDot = sh > mk.dot0() + 0.05 && sh < mk.dot1() - 0.05;
                boolean outside = (sh < mk.gap0() - 0.05 || sh > mk.gap1() + 0.05) && (sh < mk.dot0() - 0.05 || sh > mk.dot1() + 0.05);
                if (inGap && painted) gapPaint++;
                if (outside && !painted) { solidMiss++; missAt = String.format(Locale.ROOT, "%s s %.1f", host.id.substring(host.id.lastIndexOf(':') + 1), sh); }
                if (inDot) { dotSamples++; if (painted) dotPainted++; }
            }
            if (dotSamples > 0) {
                double share = (double) dotPainted / dotSamples;
                if (Math.abs(share - RoadMesh.DOT / RoadMesh.DOT_PERIOD) > Math.abs(dotShare - RoadMesh.DOT / RoadMesh.DOT_PERIOD) || dotShare == 1) dotShare = share;
            } else if (!host.section.oneWay) fails.add(r.id.substring(r.id.lastIndexOf(':') + 1) + " has no dotted line");
        }
        if (holes > 0) fails.add(holes + " asphalt holes at " + holeAt);
        if (overlaps > 0) fails.add(overlaps + " asphalt overlaps at " + overAt);
        if (inside > 0) fails.add(inside + " ramp asphalt samples on the interstate's band");
        if (meshErr > MESH_TOL) fails.add(String.format(Locale.ROOT, "asphalt off the design %.4f", meshErr));
        if (lineErr > MESH_TOL) fails.add(String.format(Locale.ROOT, "line off its asphalt %.4f at %s", lineErr, lineAt));
        if (yellowIn > 0) fails.add(yellowIn + " ramp yellow samples on the interstate's lanes");
        if (chevrons == 0) fails.add("no gore chevrons");
        if (solidMiss > 0) fails.add(solidMiss + " edge line samples unpainted (" + missAt + ")");
        if (gapPaint > 0) fails.add(gapPaint + " edge line samples painted in the gap");
        if (Math.abs(dotShare - RoadMesh.DOT / RoadMesh.DOT_PERIOD) > DOT_TOL) fails.add(String.format(Locale.ROOT, "dotted share %.3f", dotShare));
        return String.format(Locale.ROOT, "mesh %d samples, holes %d, overlaps %d, off %.4f, lines off %.4f, chevron samples %d, dotted share %.3f",
                samples, holes, overlaps, meshErr, lineErr, chevrons, dotShare);
    }

    /** The heights of ys on the level of want (within LEVEL: a deck over the attached stretch is another level). */
    private static List<Double> level(List<Double> ys, double want) {
        List<Double> out = new ArrayList<>(ys.size());
        for (double y : ys) if (Math.abs(y - want) < LEVEL) out.add(y);
        return out;
    }

    /** A point's finished surface and every road reaching it (debug). */
    private static String where(RoadWorks works, RoadWorks.Ground ground, double x, double z) {
        StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "%.0f %.0f f %.2f g %.2f", x, z, works.finished(x, z, ground), ground.at(x, z)));
        RoadDesign[] near = works.roadsNear(x, z);
        if (near != null)
            for (RoadDesign d : near) {
                double[] l = d.local(x, z, RoadWorks.REACH);
                if (l == null) continue;
                double f = works.surface(new RoadWorks.Hit(d, l[0], l[1]), x, z, ground.at(x, z));
                b.append(String.format(Locale.ROOT, "; %s s %.1f u %.1f -> %.2f", d.id.length() > 40 ? d.id.substring(d.id.length() - 40) : d.id, l[0], l[1], f));
            }
        return b.toString();
    }

    /** Whether (x, z) is within the ramp's attached stretch's reach (its station range, 12 m of its centreline). */
    private static boolean nearRamp(RoadDesign r, double x, double z, double a0, double a1) {
        double[] l = r.local(x, z, 12);
        return l != null && l[0] >= a0 && l[0] <= a1;
    }

    /** The plan bounds of road d's stations s0 .. s1 within w of its centreline: {x0, z0, x1, z1}. */
    private static double[] bounds(RoadDesign d, double s0, double s1, double w) {
        double x0 = Double.POSITIVE_INFINITY, z0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, z1 = Double.NEGATIVE_INFINITY;
        for (double s = s0; s <= s1 + 1e-9; s += 2)
            for (double u : new double[]{-w, w}) {
                double[] p = d.point(Math.min(s, s1), u);
                x0 = Math.min(x0, p[0]); z0 = Math.min(z0, p[1]); x1 = Math.max(x1, p[0]); z1 = Math.max(z1, p[1]);
            }
        return new double[]{x0, z0, x1, z1};
    }
}
