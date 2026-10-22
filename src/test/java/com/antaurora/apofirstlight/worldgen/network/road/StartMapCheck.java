package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.map.MapStore;
import com.antaurora.apofirstlight.map.MapStyle;
import com.antaurora.apofirstlight.map.StartMap;
import com.antaurora.apofirstlight.worldgen.terrain.v2.EcologyBiomes;
import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalTerrainPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.InflaterInputStream;

/**
 * Start map check (docs/ui/afl_map_v1_plan.md 5.4, 2026-10-21): per seed the full network build (as CountyCheck), then
 * StartMap's plan on the roads the server builds (towns and the county road in the JSON) and its ground off the plan:
 * <ul>
 *   <li>a way is found, from H through the driveway, ending within StartMap.TOWN_REACH (plus a station) of Linden's limit;</li>
 *   <li>the shown chunks hold every point of the way, the house, and every corner of every Linden lot and of its limit
 *   (all of Linden, user 2026-10-21);</li>
 *   <li>the ground has every shown chunk; the plan's and the ground's times (the worker's share of picking the map up).</li>
 * </ul>
 * Writes build/start_map_&lt;seed&gt;.png (2 m a pixel): the shown chunks in the map's biome colours, the way and the circle.
 * <pre>
 *   StartMapCheck [terrain-plan.bin ...]     (none: SystemPlans.DEFAULTS)
 * </pre>
 */
public final class StartMapCheck {
    private StartMapCheck() {}

    public static void main(String[] args) throws Exception {
        List<String> bins = new ArrayList<>(List.of(args.length > 0 ? args : SystemPlans.DEFAULTS));
        Map<String, Path> plans = TownCheck.newestPlans();
        int fails = 0, seeds = 0;
        List<String> summary = new ArrayList<>();
        for (String f : bins) {
            String name = Path.of(f).getFileName().toString();
            String unsigned = name.substring(name.lastIndexOf('_') + 1, name.length() - ".bin".length());
            Path planFile = plans.get(unsigned);
            if (planFile == null) { System.out.println("== seed " + unsigned + ": no plan (run systemPlans)"); continue; }
            long seed = Long.parseUnsignedLong(unsigned);
            TerrainPlanSurface s;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(f)), 1 << 16))) {
                s = TerrainPlanSurface.read(in, seed, FederalTerrainPlan.VERSION, FederalTerrainPlan.GRID_N, FederalTerrainPlan.GRID_ORIGIN);
            }
            if (s == null) { System.out.println("== seed " + unsigned + ": STALE terrain plan " + f); continue; }
            seeds++;
            System.out.println("== seed " + unsigned);
            RouteTerrain terrain = new RouteTerrain(s, FederalMap.forSeed(seed));
            String[] kept = new String[1];
            RoadCounty.Memo memo = new RoadCounty.Memo() {
                @Override public String load() { return kept[0]; }
                @Override public void store(String site) { kept[0] = site; }
            };
            RoadNetworkBuild.Result r = RoadNetworkBuild.build(Files.readString(planFile), terrain, memo);
            List<String> bad = new ArrayList<>();
            RoadCounty.Home h = r.home();
            if (h == null) {
                bad.add("no home");
            } else {
                List<RoadDesign> roads = RoadNetworkJson.designs(RoadCounty.append(TownPlan.append(r.json(), r.towns()), h));
                long t0 = System.nanoTime();
                StartMap.Plan p = StartMap.plan(roads, r.towns(), h);
                long t1 = System.nanoTime();
                Map<Long, int[]> ground = StartMap.ground(p.chunks(), terrain);
                long t2 = System.nanoTime();
                System.out.println("  " + p.report());
                TownPlan town = null;
                for (TownPlan t : r.towns()) if (StartMap.TOWN.equals(t.town)) town = t;
                double[] w = p.route();
                if (town == null) bad.add("no Linden");
                if (!p.report().startsWith("way found")) diag(roads, h, town);
                if (w.length < 4) bad.add("no way");
                else {
                    if (h.node() != null && Math.hypot(w[0] - h.node()[0], w[1] - h.node()[2]) > 0.01) bad.add("the way does not start at H");
                    if (town != null) {
                        double[] l = town.local(w[w.length - 2], w[w.length - 1]);
                        if (!town.inside(l[0], l[1], StartMap.TOWN_REACH + 16)) bad.add(String.format(Locale.ROOT, "the way ends off Linden (%.0f, %.0f)", w[w.length - 2], w[w.length - 1]));
                    }
                    for (int i = 0; i + 1 < w.length; i += 2) if (!shown(p.chunks(), w[i], w[i + 1])) { bad.add("a point of the way not shown"); break; }
                }
                if (!shown(p.chunks(), h.x(), h.z())) bad.add("the house not shown");
                int lots = 0;
                if (town != null) {
                    List<double[]> corners = new ArrayList<>();
                    for (TownPlan.Lot lot : town.lots) {
                        lots++;
                        corners.add(new double[]{lot.s0(), lot.v0()}); corners.add(new double[]{lot.s1(), lot.v0()});
                        corners.add(new double[]{lot.s0(), lot.v1()}); corners.add(new double[]{lot.s1(), lot.v1()});
                    }
                    corners.add(new double[]{town.limit[0], town.limit[2]}); corners.add(new double[]{town.limit[1], town.limit[2]});
                    corners.add(new double[]{town.limit[0], town.limit[3]}); corners.add(new double[]{town.limit[1], town.limit[3]});
                    int missing = 0;
                    for (double[] c : corners) { double[] x = town.world(c[0], c[1]); if (!shown(p.chunks(), x[0], x[1])) missing++; }
                    if (missing > 0) bad.add(missing + " Linden lot / limit corners not shown");
                }
                if (ground.size() != p.chunks().size()) bad.add("ground has " + ground.size() + " of " + p.chunks().size() + " chunks");
                String line = String.format(Locale.ROOT, "%s; Linden %d lots; plan %d ms, ground %d ms", p.report(), lots, (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000);
                System.out.println("  " + line);
                summary.add(unsigned + ": " + line + (bad.isEmpty() ? "" : " FAIL"));
                png(new File("build/start_map_" + unsigned + ".png"), p, terrain, s);
            }
            for (String b : bad) System.out.println("  FAIL " + b);
            if (h == null) summary.add(unsigned + ": no home FAIL");
            fails += bad.isEmpty() ? 0 : 1;
        }
        System.out.println("== summary");
        for (String l : summary) System.out.println("  " + l);
        System.out.println(fails == 0 ? "START MAP CHECK PASS (" + seeds + " seeds)" : "START MAP CHECK FAIL (" + fails + " of " + seeds + " seeds)");
        if (fails > 0) System.exit(1);
    }

    /** Why no way was found: the county road, its ends and what lies near them, Linden's distance. */
    private static void diag(List<RoadDesign> roads, RoadCounty.Home h, TownPlan town) {
        System.out.println("  diag: home road " + h.road() + ", drive " + (h.drive() == null ? "null" : h.drive().length / 3 + " points") + ", link " + h.link() + ", beyond " + h.beyond());
        for (RoadDesign r : roads) {
            if (!r.id.equals(h.road())) continue;
            for (double s : new double[]{0, r.length}) {
                double[] e = r.point(s, 0);
                double best = 1e9, dy = 0; String other = "-";
                for (RoadDesign o : roads) {
                    if (o == r) continue;
                    for (double t = 0; t <= o.length; t += 4) {
                        double[] q = o.point(t, 0);
                        double d = Math.hypot(q[0] - e[0], q[1] - e[1]);
                        if (d < best) { best = d; dy = o.datum(t) - r.datum(s); other = o.id; }
                    }
                }
                double[] l = town == null ? new double[]{0, 0} : town.local(e[0], e[1]);
                System.out.printf(Locale.ROOT, "  diag: county %s end s=%.0f at (%.0f, %.0f): nearest %s %.1f m dy %.1f; Linden local (%.0f, %.0f) limit %s%n",
                        r.id, s, e[0], e[1], other, best, dy, l[0], l[1], town == null ? "-" : java.util.Arrays.toString(town.limit));
            }
        }
    }

    private static boolean shown(Set<Long> chunks, double x, double z) {
        return chunks.contains(MapStore.key(Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16)));
    }

    /** The shown chunks at 2 m a pixel in the map's biome colours (water blue), the way and the circle in pencil. */
    private static void png(File file, StartMap.Plan p, RouteTerrain t, TerrainPlanSurface s) throws Exception {
        int c0 = Integer.MAX_VALUE, c1 = Integer.MIN_VALUE, d0 = Integer.MAX_VALUE, d1 = Integer.MIN_VALUE;
        for (long k : p.chunks()) { int cx = (int) (k >> 32), cz = (int) k; c0 = Math.min(c0, cx); c1 = Math.max(c1, cx); d0 = Math.min(d0, cz); d1 = Math.max(d1, cz); }
        if (c0 > c1) return;
        int x0 = c0 * 16, z0 = d0 * 16, wpx = (c1 - c0 + 1) * 8, hpx = (d1 - d0 + 1) * 8;
        BufferedImage img = new BufferedImage(wpx, hpx, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < hpx; j++)
            for (int i = 0; i < wpx; i++) {
                double x = x0 + i * 2 + 1, z = z0 + j * 2 + 1;
                int rgb = 0xE8E2D2;
                if (shown(p.chunks(), x, z)) {
                    int w = t.water(x, z);
                    rgb = w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA ? 0x7FA8C8 : MapStyle.mapColour(EcologyBiomes.biomeAt(s, x, z));
                }
                img.setRGB(i, j, rgb);
            }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x3F3E43));
        g.setStroke(new BasicStroke(2.5F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double[] w = p.route();
        if (w.length >= 4) {
            Path2D path = new Path2D.Double();
            path.moveTo((w[0] - x0) / 2, (w[1] - z0) / 2);
            for (int i = 2; i + 1 < w.length; i += 2) path.lineTo((w[i] - x0) / 2, (w[i + 1] - z0) / 2);
            g.draw(path);
        }
        double[] c = p.circle();
        if (c != null) g.draw(new Ellipse2D.Double((c[0] - c[2] - x0) / 2, (c[1] - c[3] - z0) / 2, c[2], c[3]));
        g.dispose();
        file.getParentFile().mkdirs();
        ImageIO.write(img, "png", file);
        System.out.println("  wrote " + file.getPath() + " (" + wpx + " x " + hpx + ")");
    }
}
