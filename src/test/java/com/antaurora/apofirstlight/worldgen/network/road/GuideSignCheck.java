package com.antaurora.apofirstlight.worldgen.network.road;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Offline check of the interchange guide signs (C3.3b group 2 package B; docs/worldgen/federal_interchanges_c3_3b.md
 * 10, 11) over built networks: every guide sign RoadSigns places (RoadGuides; on the designs, no ground), its legend,
 * place and facing; per interchange its exit number and how many of each sign stood (the advance and the direction sign
 * and the gore sign per exit ramp off an interstate, two entrance guides per entrance ramp of a diamond, two split signs
 * per system interchange, C3.3b group 3), the missing ones reported; each sign's panel
 * over the higher foot by its height (GuideSigns.BOTTOM, BOTTOM_LOW), its posts off every paved band (a median's turf is
 * clear), its cells inside its
 * panel; its cells off every other road sign's (guide, junction, terminal); and the feet as the game finds them (RoadGuides#foot: the turf as
 * drawn, else the density's surface) over a stand-in natural ground 6 m over the roads (cut) and 6 m under them (fill):
 * each on the ground the world draws at its post (FOOT_TOL), the direction and gore signs still all standing; how far
 * that drawn ground lies off the works' exact surface is printed (the turf's 1 m corners round a gore's V ditch off).
 * Pure Java. Arguments: cached network files; none: every cached network of the checkout (RoadSurfaceCheck.CACHES).
 */
public final class GuideSignCheck {
    private GuideSignCheck() {}

    /** The stand-in natural ground over (cut) and under (fill) the roads' datums, m. */
    static final double GROUND_OFF = 6;
    /** How far a foot may sit off the ground drawn at its post, m (JunctionCheck#FOOT_TOL). */
    static final double FOOT_TOL = JunctionCheck.FOOT_TOL;

    public static void main(String[] args) throws Exception {
        List<Path> files = new ArrayList<>();
        for (String a : args) files.add(Path.of(a));
        if (files.isEmpty()) {
            // one per file name (the same seed and plan cached in two game directories is the same network)
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
            System.out.println("GUIDE SIGN CHECK: no cached road network (open a federal world once, or pass -ProadNetwork=<file;file>)");
            System.exit(2);
        }
        boolean all = true;
        for (Path f : files) {
            System.out.println("== " + f);
            all &= check(f);
        }
        System.out.println(all ? "GUIDE SIGN CHECK PASS (" + files.size() + " networks)" : "GUIDE SIGN CHECK FAIL");
        if (!all) System.exit(1);
    }

    static boolean check(Path file) throws Exception {
        RoadNetworkBuild.Result built = RoadNetworkBuild.build(Files.readString(file));
        List<RoadDesign> roads = RoadNetworkJson.designs(built.json());
        RoadWorks works = new RoadWorks(roads, RoadWorks.Water.NONE);
        List<TunnelMesh.Placement> all = RoadSigns.all(works, null), signs = new ArrayList<>();
        for (TunnelMesh.Placement p : all) if ("guide_sign".equals(p.mesh())) signs.add(p);
        List<String> fails = new ArrayList<>();
        // the interchanges: their exit ramps off an interstate, a diamond's entrance ramps; the system interchanges
        Map<String, int[]> ramps = new TreeMap<>();
        for (RoadDesign d : roads) {
            if (d.attach == null) continue;
            String key = d.id.substring(0, d.id.lastIndexOf(':'));
            int[] n = ramps.computeIfAbsent(key, k -> new int[2]);
            RoadDesign host = works.attachedHost(d);
            if (!d.attach.atEnd() && host != null && host.section.divided) n[0]++;
            if (d.attach.atEnd() && !key.startsWith("system:")) n[1]++;
        }
        int systems = 0;
        for (String k : ramps.keySet()) if (k.startsWith("system:")) systems++;
        Map<String, int[]> types = new TreeMap<>();
        java.util.Set<String> overlapSeen = new java.util.HashSet<>();
        for (TunnelMesh.Placement p : signs) {
            GuideSigns.Spec s = GuideSigns.Spec.parse(p.bone());
            GuideSigns.Layout l = GuideSigns.layout(s);
            double[] c = {p.x(), p.z()};
            System.out.printf(Locale.ROOT, "  %-4s at %8.1f %6.1f %8.1f facing %5.0f deg  %s   [%s]%n", s.type().key, c[0], p.y(), c[1],
                    Math.toDegrees(Math.atan2(-p.ez()[0], p.ez()[2])), GuideSigns.describe(p.bone()), p.bone());
            String key = s.type() == GuideSigns.Type.ENTRANCE ? "ent" : s.exit();
            types.computeIfAbsent(s.type().key, k -> new int[1])[0]++;
            // the panel over the higher foot
            double bottom = l.panel()[1] - Math.max(l.feet()[0], l.feet()[1]);
            double want = s.type() == GuideSigns.Type.ENTRANCE ? GuideSigns.BOTTOM_LOW : GuideSigns.BOTTOM;
            if (Math.abs(bottom - want) > 1e-6) fails.add("panel bottom " + bottom + " at " + key);
            // the cells inside the panel (or the tab), clear of the border
            for (GuideSigns.Piece q : l.pieces()) {
                double[] r = l.tab() != null && q.y0() >= l.panel()[3] - 1e-9 ? l.tab() : l.panel();
                double m = GuideSigns.BORDER_INSET + GuideSigns.BORDER;
                if (q.x0() < r[0] + m - 1e-9 || q.x1() > r[2] - m + 1e-9 || q.y0() < r[1] + m - 1e-9 || q.y1() > r[3] - m + 1e-9)
                    fails.add("piece '" + q.ch() + (q.tile() == null ? "" : q.tile()) + "' over the border of " + p.bone());
            }
            // the client's mesh: no faces drawn over each other (SignOverlap, 2026-10-17)
            if (overlapSeen.add(p.bone()))
                for (String finding : com.antaurora.apofirstlight.client.road.SignOverlap.check(p.bone())) fails.add("faces overlap in " + p.bone() + ": " + finding);
            // the posts off every paved band
            double exx = p.ex()[0], exz = p.ex()[2];
            for (double px : l.posts()) {
                double x = p.x() + exx * px, z = p.z() + exz * px;
                RoadDesign[] near = works.roadsNear(x, z);
                if (near != null)
                    for (RoadDesign q : near) {
                        double[] lq = q.local(x, z, q.section.edge + 3);
                        if (lq != null && lq[0] >= 0 && lq[0] <= q.length && Math.abs(lq[1]) < q.section.edge + 0.5
                                && !(q.section.divided && Math.abs(lq[1]) <= q.section.halfMedian - 0.5))
                            fails.add(String.format(Locale.ROOT, "post on %s at %.1f %.1f", q.id, x, z));
                    }
                if (works.pad(x, z) != null) fails.add(String.format(Locale.ROOT, "post on a pad at %.1f %.1f", x, z));
            }
        }
        // a guide sign sharing a cell with another road sign (each pair once)
        for (TunnelMesh.Placement p : signs)
            for (TunnelMesh.Placement q : all) {
                if (q == p || "guide_sign".equals(q.mesh()) && signs.indexOf(q) < signs.indexOf(p)) continue;
                TunnelFixtures.Part a = TunnelFixtures.Part.of(p), b = TunnelFixtures.Part.of(q);
                double[] ba = a.bounds(), bb = b.bounds();
                if (ba[0] > bb[3] || bb[0] > ba[3] || ba[2] > bb[5] || bb[2] > ba[5]) continue;
                shared:
                for (long c : a.cells()) for (long d : b.cells()) if (c == d) { fails.add("signs share a cell: " + p.bone() + " / " + q.mesh() + " " + q.bone()); break shared; }
            }
        int exits = 0, entries = 0;
        for (int[] n : ramps.values()) { exits += n[0]; entries += n[1]; }
        int adv = types.getOrDefault("adv", new int[1])[0], dir = types.getOrDefault("dir", new int[1])[0], gore = types.getOrDefault("gore", new int[1])[0],
                ent = types.getOrDefault("ent", new int[1])[0], split = types.getOrDefault("split", new int[1])[0];
        System.out.printf(Locale.ROOT, "  %d interchanges (%d system), %d exit ramps, %d entrance ramps: advance %d, direction %d, gore %d, entrance %d (of %d), split %d (of %d)%n",
                ramps.size(), systems, exits, entries, adv, dir, gore, ent, 2 * entries, split, 2 * systems);
        if (dir < exits) fails.add((exits - dir) + " exit direction signs missing");
        if (gore < exits) fails.add((exits - gore) + " gore signs missing");
        if (split < 2 * systems) fails.add((2 * systems - split) + " split signs missing");
        for (double off : new double[]{GROUND_OFF, -GROUND_OFF}) feet(works, off, exits, systems, fails);
        if (!fails.isEmpty()) System.out.println("  FAIL: " + String.join("; ", fails.subList(0, Math.min(20, fails.size()))) + (fails.size() > 20 ? " ..." : ""));
        return fails.isEmpty();
    }

    /**
     * The guide signs RoadSigns places over the stand-in ground off m over the roads: their feet against the ground drawn at
     * their posts (the turf, else the density's surface) and that against the works' exact surface, and how many of each stood.
     */
    private static void feet(RoadWorks works, double off, int exits, int systems, List<String> fails) {
        RoadWorks.Ground ground = standIn(works, off);
        String c = off > 0 ? "cut" : "fill";
        double worst = 0, over = 0, under = 0;
        String worstAt = "";
        Map<String, int[]> types = new TreeMap<>();
        for (TunnelMesh.Placement p : RoadSigns.all(works, ground)) {
            if (!"guide_sign".equals(p.mesh())) continue;
            GuideSigns.Spec s = GuideSigns.Spec.parse(p.bone());
            GuideSigns.Layout l = GuideSigns.layout(s);
            types.computeIfAbsent(s.type().key, k -> new int[1])[0]++;
            for (int i = 0; i < 2; i++) {
                double x = p.x() + p.ex()[0] * l.posts()[i], z = p.z() + p.ex()[2] * l.posts()[i], y = p.y() + l.feet()[i];
                double t = works.turfTop(x, z, ground);
                double drawn = Double.isNaN(t) ? works.densityHeight((int) Math.floor(x), (int) Math.floor(z), ground) : t;
                double exact = works.surface(x, z, ground.at(x, z));
                String at = String.format(Locale.ROOT, "%s %s foot %d at %.1f %.1f", s.type().key, s.exit(), i, x, z);
                if (Boolean.getBoolean("afl.debugGuides") && Math.abs(drawn - exact) > 0.1)
                    System.out.printf(Locale.ROOT, "    DEBUG %s %s: foot %.3f drawn %.3f (%s) exact %.3f%n", c, at, y, drawn, Double.isNaN(t) ? "density" : "turf", exact);
                double e = Double.isNaN(drawn) ? Double.POSITIVE_INFINITY : Math.abs(y - drawn);
                if (e > worst) { worst = e; worstAt = at; }
                if (!Double.isNaN(exact)) { over = Math.max(over, drawn - exact); under = Math.max(under, exact - drawn); }
            }
        }
        int dir = types.getOrDefault("dir", new int[1])[0], gore = types.getOrDefault("gore", new int[1])[0], split = types.getOrDefault("split", new int[1])[0];
        System.out.printf(Locale.ROOT, "  on ground in %s: advance %d, direction %d, gore %d, entrance %d, split %d; feet off the drawn ground %.4f "
                        + "(the drawn ground over the exact surface up to %.3f, under it up to %.3f)%n",
                c, types.getOrDefault("adv", new int[1])[0], dir, gore, types.getOrDefault("ent", new int[1])[0], split, worst, over, under);
        if (split < 2 * systems) fails.add("in " + c + " " + (2 * systems - split) + " split signs missing");
        if (worst > FOOT_TOL) fails.add(String.format(Locale.ROOT, "in %s a foot %.3f off the drawn ground, %s", c, worst, worstAt));
        if (dir < exits) fails.add("in " + c + " " + (exits - dir) + " exit direction signs missing");
        if (gore < exits) fails.add("in " + c + " " + (exits - gore) + " gore signs missing");
    }

    /**
     * A stand-in natural ground off m over the roads: the datums of the roads near the point (each at its nearest station,
     * weighted by its nearness), else the network's mean datum; smooth enough for the works to cut and fill it as a world's.
     */
    private static RoadWorks.Ground standIn(RoadWorks works, double off) {
        double sum = 0;
        int n = 0;
        for (RoadDesign d : works.roads()) { sum += d.datum(d.length / 2); n++; }
        double mean = n == 0 ? 64 : sum / n;
        return (x, z) -> {
            RoadDesign[] near = works.roadsNear(x, z);
            double w = 0, h = 0;
            if (near != null)
                for (RoadDesign q : near) {
                    double[] l = q.local(x, z, 400);
                    if (l == null) continue;
                    double k = 1 / Math.pow(1 + Math.abs(l[1]), 2);
                    w += k;
                    h += k * q.datum(Math.max(0, Math.min(q.length, l[0])));
                }
            return (w > 0 ? h / w : mean) + off;
        };
    }
}
