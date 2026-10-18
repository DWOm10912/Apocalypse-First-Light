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
 * Offline check of the C3.3b group 3 system interchanges (docs/worldgen/federal_systems_c3_3b.md) over planned networks:
 * the build's system interchange report (sites built and skipped, each ramp's radius, grade and K, the overpasses'
 * clearance), the trims and splits of their roads, the diamonds kept off them, the remaining conflicts. Every system
 * interchange's ramps are then designed and worked (RoadWorks, its marks on the hosts).
 * Pure Java. Arguments: plan files; none: the newest plan per seed in build/federal-systems/plans (SystemPlans).
 * -Dafl.systemsPng=<dir>: every attempt's plan as a PNG (its ramps' paved bands over H's and Q's).
 */
public final class SystemCheck {
    private SystemCheck() {}

    static final String PLANS = "build/federal-systems/plans";

    public static void main(String[] args) throws Exception {
        List<Path> files = new ArrayList<>();
        for (String a : args) files.add(Path.of(a));
        if (files.isEmpty()) {
            // the newest plan of each seed
            Map<String, Path> bySeed = new TreeMap<>();
            Path d = Path.of(PLANS);
            if (Files.isDirectory(d))
                try (Stream<Path> list = Files.list(d)) {
                    for (Path p : (Iterable<Path>) list.filter(p -> p.getFileName().toString().matches(com.antaurora.apofirstlight.worldgen.network.route.RoadNetworkPlan.VERSION + "_\\d+_[0-9a-f]+\\.json"))::iterator) {
                        String n = p.getFileName().toString(), seed = n.substring(0, n.lastIndexOf('_'));
                        Path old = bySeed.get(seed);
                        if (old == null || Files.getLastModifiedTime(p).compareTo(Files.getLastModifiedTime(old)) > 0) bySeed.put(seed, p);
                    }
                }
            files.addAll(bySeed.values());
        }
        if (files.isEmpty()) {
            System.out.println("SYSTEM CHECK: no plan (run systemPlans, or pass -ProadNetwork=<file>)");
            System.exit(2);
        }
        String png = System.getProperty("afl.systemsPng");
        String[] seed = {""};
        if (png != null) {
            Files.createDirectories(Path.of(png));
            RoadSystems.debugSink = (label, ds) -> {
                try {
                    draw(ds, Path.of(png, seed[0] + "_" + label + ".png"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }
        int conflicts = 0, built = 0, onHost = 0;
        for (Path file : files) {
            System.out.println("== " + file.getFileName());
            String fn = file.getFileName().toString();
            seed[0] = fn.replaceAll(com.antaurora.apofirstlight.worldgen.network.route.RoadNetworkPlan.VERSION + "_(\\d+)_.*", "$1");
            long t0 = System.nanoTime();
            RoadNetworkBuild.Result r = RoadNetworkBuild.build(Files.readString(file));
            long ms = (System.nanoTime() - t0) / 1_000_000;
            for (String l : r.report())
                if (l.startsWith("system") || l.startsWith("  system:") || l.startsWith("CONFLICT") || l.startsWith("interchanges:")
                        || l.contains("system interchange at") || (l.startsWith("trim:") || l.startsWith("split:") || l.startsWith("left out:")) && l.contains("system:"))
                    System.out.println("  " + l);
            System.out.printf(Locale.ROOT, "  build %d ms, conflicts %d%n", ms, r.conflicts());
            conflicts += r.conflicts();
            // the works and the marks of every system ramp
            List<RoadDesign> roads = RoadNetworkJson.designs(r.json());
            RoadWorks works = new RoadWorks(roads, RoadWorks.Water.NONE);
            for (RoadDesign d : roads) {
                if (!d.id.startsWith("system:")) continue;
                if (d.id.endsWith(":right-in~1")) built++;
                RoadDesign host = works.attachedHost(d);
                StringBuilder mb = new StringBuilder();
                for (RoadWorks.RampMarks m : works.marksOf(d))
                    if (m.ramp() == d) mb.append(String.format(Locale.ROOT, "marks side %d end %.0f paint %.0f nose %.0f dot %.0f..%.0f gap %.0f..%.0f", m.side(), m.end(), m.paint(), m.nose(), m.dot0(), m.dot1(), m.gap0(), m.gap1()));
                if (mb.length() == 0 && host != null && d.attach != null) {
                    // why none: the end, the painted and the physical nose on the host (RoadWorks#marks)
                    double lane = d.section.lanesOut;
                    for (double[] su : new double[][]{{d.attach.atEnd() ? d.length : 0, lane}, {d.attach.atEnd() ? d.length : 0, -lane}, {d.attach.paint(), lane}, {d.attach.paint(), -lane},
                            {d.attach.nose(), d.section.edge}, {d.attach.nose(), -d.section.edge}}) {
                        double[] pp = d.point(su[0], su[1]), l = host.local(pp[0], pp[1], RoadWorks.REACH);
                        mb.append(String.format(Locale.ROOT, "[s %.1f u %.1f: %s] ", su[0], su[1], l == null ? "off" : String.format(Locale.ROOT, "%.2f of %.2f, v %.2f", l[0], host.length, l[1])));
                    }
                    mb.insert(0, "no marks ");
                }
                String marks = mb.length() == 0 ? "no marks" : mb.toString();
                System.out.printf(Locale.ROOT, "    %s: %.0f m, open %s%s, host %s, %s%n", d.id, d.length, d.openStart ? "start" : "-", d.openEnd ? " end" : "",
                        host == null ? "NONE" : host.id, marks);
            }
            // every system ramp keeps its paved band GAP (1 m) off its interstate's at its level (not on a deck over it): a ramp
            // attached to it from where it first gets that far off (walked away from its attached end), any other everywhere
            java.util.Map<String, RoadDesign> hosts = new java.util.HashMap<>();
            for (RoadDesign d : roads) {
                if (!d.id.startsWith("system:")) continue;
                RoadDesign h = works.attachedHost(d);
                if (h != null && h.section.divided) hosts.put(d.id.substring(0, d.id.lastIndexOf(':')), h);
            }
            for (RoadDesign d : roads) {
                RoadDesign h = d.id.startsWith("system:") ? hosts.get(d.id.substring(0, d.id.lastIndexOf(':'))) : null;
                if (h == null) continue;
                boolean onH = works.attachedHost(d) == h, back = onH && d.attach.atEnd(), apart = !onH;
                double worst = Double.POSITIVE_INFINITY, worstAt = 0;
                for (double i = 0; i <= d.length + 1e-9; i += 1) {
                    double t = back ? d.length - i : i;
                    double[] p = d.point(t, 0), l = h.local(p[0], p[1], h.section.edge + d.section.edge + 20);
                    double gap = l == null || l[0] < 0 || l[0] > h.length ? Double.POSITIVE_INFINITY : Math.abs(l[1]) - h.section.edge - d.section.edge;
                    if (gap >= 1) { apart = true; continue; }
                    if (!apart) continue;
                    double hu = Math.copySign(Math.max(h.section.halfMedian, Math.min(Math.abs(l[1]), h.section.edge)), l[1]);
                    if (d.pavementY(t, 0) - h.pavementY(l[0], hu) < RoadInterchanges.CLEARANCE && gap < worst) { worst = gap; worstAt = t; }
                }
                if (worst < Double.POSITIVE_INFINITY) {
                    onHost++;
                    System.out.printf(Locale.ROOT, "    ON ITS INTERSTATE: %s at s %.0f, paved bands %.2f m apart%n", d.id, worstAt, worst);
                }
            }
            // every open end: where its end line's points (centreline and paved edges) lie on the other open roads (beyond
            // their own open ends: a crack the works leave natural; within: an overlap)
            for (RoadDesign d : roads)
                for (boolean end : new boolean[]{false, true}) {
                    if (!(end ? d.openEnd : d.openStart)) continue;
                    double worst = 0;
                    String at2 = "";
                    for (double u = -d.section.edge; u <= d.section.edge + 1e-9; u += d.section.edge / 4) {
                        double[] pp = d.point(end ? d.length : 0, u);
                        for (RoadDesign o : roads) {
                            if (o == d || !(o.openStart || o.openEnd)) continue;
                            double[] l = o.local(pp[0], pp[1], o.section.edge);
                            if (l == null || !o.section.paved(Math.abs(l[1]))) continue;
                            // the other's open end it continues: its station past (+) or short of (-) that end
                            double past = o.openStart && l[0] < o.length / 2 ? -l[0] : o.openEnd && l[0] > o.length / 2 ? l[0] - o.length : Double.NaN;
                            if (Double.isNaN(past) || Math.abs(past) > 5) continue;
                            // a crack: d's end short of o's open end (both give way between them)
                            if (Math.abs(past) > Math.abs(worst)) { worst = past; at2 = o.id.substring(o.id.lastIndexOf(':') + 1); }
                        }
                    }
                    if (d.id.startsWith("system:") || Math.abs(worst) > 0)
                        System.out.printf(Locale.ROOT, "    open %s %s: on %s %.3f m past its open end%n", d.id.length() > 40 ? d.id.substring(d.id.length() - 40) : d.id, end ? "end" : "start", at2, worst);
                }
            // -Dafl.systemsEdge=<ramp id suffix>: along that ramp's attached stretch, its lane edges' offsets from its host's edge line
            String edge = System.getProperty("afl.systemsEdge");
            if (edge != null)
                for (RoadDesign d : roads) {
                    if (!d.id.endsWith(":" + edge) || d.attach == null) continue;
                    RoadDesign h = works.attachedHost(d);
                    if (h == null) continue;
                    double line = h.section.lanesOut, lane = d.section.lanesOut;
                    for (double t = 0; t <= d.length; t += 5) {
                        double[] a = d.point(t, -lane), b = d.point(t, lane), c = d.point(t, 0), la = h.local(a[0], a[1], 60), lb = h.local(b[0], b[1], 60), lc = h.local(c[0], c[1], 60);
                        if (la == null || lb == null || lc == null) continue;
                        System.out.printf(Locale.ROOT, "    edge %s s %.0f: host s %.1f, centre %.3f, lane edges %.3f %.3f (host line %.3f) at %.1f %.1f, y %.2f, host y %.2f%n", d.id.substring(d.id.lastIndexOf('@') + 1), t, lc[0], lc[1], la[1], lb[1], line,
                                c[0], c[1], d.pavementY(t, 0), h.pavementY(Math.max(0, Math.min(h.length, lc[0])), Math.copySign(Math.max(h.section.halfMedian, Math.min(Math.abs(lc[1]), h.section.edge)), lc[1])));
                    }
                }
            // -Dafl.systemsAt=x,z;x,z: every road whose paved band reaches each point, its station, offset and pavement height
            String at = System.getProperty("afl.systemsAt");
            if (at != null)
                for (String pt : at.split(";")) {
                    String[] xz = pt.split(",");
                    double x = Double.parseDouble(xz[0]), z = Double.parseDouble(xz[1]);
                    for (RoadDesign d : roads) {
                        double[] l = d.local(x, z, 60);
                        if (l == null || l[0] < 0 || l[0] > d.length) continue;
                        RoadDesign.Span sp = d.span(l[0]);
                        double y = d.section.paved(Math.abs(l[1])) || !d.section.divided ? d.pavementY(l[0], Math.copySign(Math.min(Math.abs(l[1]), d.section.edge), l[1])) : Double.NaN;
                        System.out.printf(Locale.ROOT, "    at %.1f %.1f: %s s %.1f of %.1f u %.2f y %.2f%s%s%n", x, z, d.id, l[0], d.length, l[1], y,
                                sp == null ? "" : String.format(Locale.ROOT, " span %.0f..%.0f%s", sp.s0(), sp.s1(), sp.over() ? " over" : ""),
                                d.attach == null ? "" : String.format(Locale.ROOT, " (attached %s, nose %.0f, paint %.0f)", d.attach.atEnd() ? "end" : "start", d.attach.nose(), d.attach.paint()));
                        for (RoadDesign.Tunnel tn : d.tunnels) if (Math.abs(tn.s0() - l[0]) < 60 || Math.abs(tn.s1() - l[0]) < 60) System.out.printf(Locale.ROOT, "      tunnel %.0f..%.0f of %.0f m%n", tn.s0(), tn.s1(), d.length);
                    }
                }
        }
        System.out.printf(Locale.ROOT, "SYSTEM CHECK: %d networks, %d system interchanges, %d conflict stations, %d ramps on their interstate%n", files.size(), built, conflicts, onHost);
        if (conflicts > 0 || onHost > 0) System.exit(1);
    }

    /** The paved bands of a site's roads (the ramps' bounding box and MARGIN): H and Q grey, the ramps coloured, their decks darker. */
    static void draw(List<RoadDesign> ds, Path out) throws Exception {
        final double MARGIN = 120;
        double x0 = Double.POSITIVE_INFINITY, z0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, z1 = Double.NEGATIVE_INFINITY;
        for (RoadDesign d : ds) {
            if (!d.id.startsWith("system:")) continue;
            for (double t = 0; t <= d.length; t += 5) {
                double[] p = d.point(t, 0);
                x0 = Math.min(x0, p[0]); x1 = Math.max(x1, p[0]); z0 = Math.min(z0, p[1]); z1 = Math.max(z1, p[1]);
            }
        }
        x0 -= MARGIN; z0 -= MARGIN; x1 += MARGIN; z1 += MARGIN;
        int size = 1400;
        double sc = size / Math.max(x1 - x0, z1 - z0);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(238, 236, 228));
        g.fillRect(0, 0, size, size);
        // a 100 m grid
        g.setColor(new java.awt.Color(222, 220, 210));
        for (double x = Math.ceil(x0 / 100) * 100; x <= x1; x += 100) g.drawLine((int) ((x - x0) * sc), 0, (int) ((x - x0) * sc), size);
        for (double z = Math.ceil(z0 / 100) * 100; z <= z1; z += 100) g.drawLine(0, (int) ((z - z0) * sc), size, (int) ((z - z0) * sc));
        for (int pass = 0; pass < 2; pass++)
            for (RoadDesign d : ds) {
                boolean ramp = d.id.startsWith("system:");
                if (ramp != (pass == 1)) continue;
                String n = ramp ? d.id.substring(d.id.lastIndexOf(':') + 1) : "";
                java.awt.Color c = !ramp ? new java.awt.Color(150, 150, 150) : n.startsWith("left-in") ? new java.awt.Color(220, 60, 50)
                        : n.startsWith("left-out") ? new java.awt.Color(40, 90, 220) : n.startsWith("right-in") ? new java.awt.Color(240, 150, 30) : new java.awt.Color(40, 160, 70);
                double from = 0, to = d.length;
                for (double t = from; t < to; t += 2) {
                    double t2 = Math.min(to, t + 2.5);
                    double[] a = d.point(t, -d.section.edge), b = d.point(t, d.section.edge), e = d.point(t2, d.section.edge), f = d.point(t2, -d.section.edge);
                    if (Math.max(Math.max(a[0], b[0]), e[0]) < x0 || Math.min(Math.min(a[0], b[0]), e[0]) > x1
                            || Math.max(Math.max(a[1], b[1]), e[1]) < z0 || Math.min(Math.min(a[1], b[1]), e[1]) > z1) continue;
                    RoadDesign.Span sp = d.span(Math.min(d.length, t));
                    g.setColor(sp != null && sp.over() ? c.darker() : c);
                    int[] xs = {(int) ((a[0] - x0) * sc), (int) ((b[0] - x0) * sc), (int) ((e[0] - x0) * sc), (int) ((f[0] - x0) * sc)};
                    int[] zs = {(int) ((a[1] - z0) * sc), (int) ((b[1] - z0) * sc), (int) ((e[1] - z0) * sc), (int) ((f[1] - z0) * sc)};
                    g.fillPolygon(xs, zs, 4);
                }
                if (ramp) {
                    double[] m = d.point(d.length * 0.5, 0);
                    g.setColor(java.awt.Color.BLACK);
                    g.drawString(n, (int) ((m[0] - x0) * sc) + 6, (int) ((m[1] - z0) * sc));
                }
            }
        g.setColor(java.awt.Color.BLACK);
        g.drawString(out.getFileName().toString() + "  (grid 100 m, x right, z down)", 8, 16);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out.toFile());
    }
}
