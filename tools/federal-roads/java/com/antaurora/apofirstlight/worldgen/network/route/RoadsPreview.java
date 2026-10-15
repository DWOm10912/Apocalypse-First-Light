package com.antaurora.apofirstlight.worldgen.network.route;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.RoadClass;
import com.antaurora.apofirstlight.worldgen.federal.NationalRoadPlan;
import com.antaurora.apofirstlight.worldgen.network.road.RoadProfile;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalTerrainPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanMaps;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.zip.InflaterInputStream;

/**
 * C3.1 offline route recomputation (docs/worldgen/federal_highways_c3.md, 3): loads a cached federal C2 plan, builds the
 * national road network plan twice (determinism), compares it with the C1 corridors on the same terrain, and writes the
 * report, the JSON and maps (national map, the S1 / S2 sample window, a profile sheet per route).
 * <pre>
 *   RoadsPreview SEED PLAN_CACHE OUT
 *   RoadsPreview SEED PLAN_CACHE OUT window X Z HALF MPP   (a map window with the routes and 10 m contours)
 *   RoadsPreview unsigned SEED        (the seed as the plan cache's file name spells it)
 * </pre>
 */
public final class RoadsPreview {
    private RoadsPreview() {}

    public static void main(String[] a) throws Exception {
        if (a[0].equals("unsigned")) { System.out.print(Long.toUnsignedString(Long.parseLong(a[1]))); return; }
        long seed = Long.parseLong(a[0]);
        File cache = new File(a[1]), out = new File(a[2]);
        out.mkdirs();
        PrintStream log = new PrintStream(new FileOutputStream(new File(out, "c3_routes.txt")), true, StandardCharsets.UTF_8);
        PrintStream both = new PrintStream(new OutputStream() {
            @Override public void write(int b) { System.out.write(b); log.write(b); }
            @Override public void write(byte[] b, int o, int l) { System.out.write(b, o, l); log.write(b, o, l); }
        }, true, StandardCharsets.UTF_8);
        long t0 = System.nanoTime();
        TerrainPlanSurface s;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new FileInputStream(cache)), 1 << 16))) {
            s = TerrainPlanSurface.read(in, seed, FederalTerrainPlan.VERSION, FederalTerrainPlan.GRID_N, FederalTerrainPlan.GRID_ORIGIN);
        }
        if (s == null) throw new IllegalStateException("stale or foreign plan cache " + cache);
        FederalMap map = FederalMap.forSeed(seed);
        RouteTerrain t = new RouteTerrain(s, map);
        long t1 = System.nanoTime();
        RoadNetworkPlan plan = RoadNetworkPlan.build(map, t);
        long t2 = System.nanoTime();
        if (a.length >= 8 && a[3].equals("window")) {
            double x = Double.parseDouble(a[4]), z = Double.parseDouble(a[5]), half = Double.parseDouble(a[6]), mp = Double.parseDouble(a[7]);
            int n = (int) Math.ceil(2 * half / mp);
            BufferedImage img = TerrainPlanMaps.render(s, x - half, z - half, n, n, mp);
            contours(s, img, x - half, z - half, mp);
            draw(img, plan, x - half, z - half, mp, 1f, true);
            File f = new File(out, String.format(Locale.ROOT, "window_%.0f_%.0f_%.0f.png", x, z, half));
            ImageIO.write(img, "png", f);
            System.out.println("window: " + f);
            return;
        }
        both.printf(Locale.ROOT, "seed %d  plan cache %.1f s  network build %.1f s%n", seed, (t1 - t0) / 1e9, (t2 - t1) / 1e9);
        String json = plan.toJson();
        Files.writeString(new File(out, "c3_routes.json").toPath(), json, StandardCharsets.UTF_8);
        String d1 = plan.digest();
        String d2 = RoadNetworkPlan.build(map, new RouteTerrain(s, map)).digest();
        both.printf(Locale.ROOT, "route digest %s, second build %s  %s%n", d1, d2, d1.equals(d2) ? "identical" : "DIFFERENT");
        plan.report(both);
        c1Compare(map, t, plan, both);
        maps(s, map, plan, out);
        zooms(s, plan, out);
        profiles(plan, out);
        both.println("output: " + out);
        log.close();
    }

    // ------------------------------------------------------------------ C1 comparison
    /**
     * The C1 corridors and the C3 routes on the same C2 terrain: the grade a road would have if it followed the ground
     * (the ground along the line, Gaussian-smoothed with the class's sigma, sampled every 10 m), as length above the
     * class's design grade and above its mountain grade; for C3 also the designed profile (always within the limits) and
     * what it costs (cut, fill, structures).
     */
    static void c1Compare(FederalMap map, RouteTerrain t, RoadNetworkPlan plan, PrintStream out) {
        NationalRoadPlan c1 = NationalRoadPlan.build(map, new NationalRoadPlan.Terrain() {
            @Override public double height(double x, double z) { return t.height(x, z); }
            @Override public boolean water(double x, double z) { int w = t.water(x, z); return w != RouteTerrain.DRY && w != RouteTerrain.MARSH; }
        });
        out.println();
        out.println("C1 corridors vs C3 routes on the C2 terrain (ground-following grade, 10 m steps):");
        out.printf(Locale.ROOT, "%-46s %8s %7s %9s %9s | %8s %7s %9s %9s | %7s%n", "leg", "C1 len", "C1 max", "C1 >G m", "C1 >Gm m",
                "C3 len", "C3 max", "C3 >G m", "C3 >Gm m", "C3 dsgn");
        for (NationalRoadPlan.Corridor c : c1.corridors()) {
            if (c.roadClass() == RoadClass.ISLAND_BRIDGE) continue;
            RoadClassSpec k = RoadNetworkPlan.classOf(c.roadClass());
            double[] g1 = groundGrades(t, c.xs(), c.zs(), k);
            RoadNetworkPlan.Route r = plan.route(c.id());
            double[] g3 = r == null ? new double[4] : groundGrades(t, r.curve().controlX(), r.curve().controlZ(), k);
            out.printf(Locale.ROOT, "%-46s %8.0f %6.1f%% %9.0f %9.0f | %8.0f %6.1f%% %9.0f %9.0f | %6.1f%%%n", c.id(), g1[0], 100 * g1[1], g1[2], g1[3],
                    g3[0], 100 * g3[1], g3[2], g3[3], r == null ? Double.NaN : 100 * r.design().stats().maxGrade());
        }
    }

    /** {length, max grade, length above the design grade, length above the mountain grade} of the smoothed ground. */
    static double[] groundGrades(RouteTerrain t, double[] xs, double[] zs, RoadClassSpec k) {
        double len = RoutePolyline.length(xs, zs);
        int n = (int) Math.floor(len / 10) + 1;
        double[] h = new double[n];
        for (int i = 0; i < n; i++) {
            double[] p = RoutePolyline.at(xs, zs, i * 10.0);
            h[i] = t.height(p[0], p[1]);
        }
        double sig = k.sigma / 10;
        int rr = (int) Math.ceil(3 * sig);
        double[] sm = new double[n];
        for (int i = 0; i < n; i++) {
            double acc = 0, ws = 0;
            for (int q = -rr; q <= rr; q++) {
                int j = Math.max(0, Math.min(n - 1, i + q));
                double w = Math.exp(-0.5 * q * q / (sig * sig));
                acc += w * h[j];
                ws += w;
            }
            sm[i] = acc / ws;
        }
        double max = 0, overG = 0, overM = 0;
        for (int i = 1; i < n; i++) {
            double g = Math.abs(sm[i] - sm[i - 1]) / 10;
            max = Math.max(max, g);
            if (g > k.grade) overG += 10;
            if (g > k.gradeMountain) overM += 10;
        }
        return new double[]{len, max, overG, overM};
    }

    // ------------------------------------------------------------------ maps
    static final Color INTERSTATE = new Color(0xC8102E), STATE = new Color(0xF2A900), COUNTY = new Color(0xFFFFFF);

    static void maps(TerrainPlanSurface s, FederalMap map, RoadNetworkPlan plan, File out) throws Exception {
        FederalMap.Bounds b = map.landBounds();
        double mpp = 16, x0 = Math.floor((b.minX() - 256) / 16) * 16, z0 = Math.floor((b.minZ() - 256) / 16) * 16;
        int w = (int) Math.ceil((b.maxX() + 256 - x0) / mpp), h = (int) Math.ceil((b.maxZ() + 256 - z0) / mpp);
        BufferedImage img = TerrainPlanMaps.render(s, x0, z0, w, h, mpp);
        draw(img, plan, x0, z0, mpp, 3f, true);
        ImageIO.write(img, "png", new File(out, "c3_national_routes.png"));
        // the S1 / S2 sample window (interstate_ew county_seat - valley_town) at 4 m/px
        RoadNetworkPlan.Route r = plan.route("interstate_ew:county_seat-valley_town");
        if (r != null) {
            double[] xs = r.curve().controlX(), zs = r.curve().controlZ();
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (int i = 0; i < xs.length; i++) { minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]); minZ = Math.min(minZ, zs[i]); maxZ = Math.max(maxZ, zs[i]); }
            double mp = 4, sx = Math.floor((minX - 400) / 16) * 16, sz = Math.floor((minZ - 400) / 16) * 16;
            int sw = (int) Math.ceil((maxX + 400 - sx) / mp), sh = (int) Math.ceil((maxZ + 400 - sz) / mp);
            BufferedImage win = TerrainPlanMaps.render(s, sx, sz, sw, sh, mp);
            draw(win, plan, sx, sz, mp, 1f, false);
            Graphics2D g = win.createGraphics();
            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.drawString(String.format(Locale.ROOT, "C3.2 sample %s  x %.0f..%.0f  z %.0f..%.0f  4 m/px", r.id(), sx, sx + sw * mp, sz, sz + sh * mp), 8, 20);
            g.dispose();
            ImageIO.write(win, "png", new File(out, "c3_sample_s1_s2.png"));
        }
    }

    /**
     * A window round every route that still needs a viaduct, a tunnel, a deep cut or a bridge over 150 m (the cases to
     * look at), at 2-8 m/px so the window is at most 1600 px.
     */
    static void zooms(TerrainPlanSurface s, RoadNetworkPlan plan, File out) throws Exception {
        File dir = new File(out, "zoom");
        dir.mkdirs();
        for (File f : dir.listFiles()) f.delete();
        for (RoadNetworkPlan.Route r : plan.routes()) {
            RouteDesigner.Structure worst = null;
            for (RouteDesigner.Structure st : r.design().structures()) {
                boolean bad = st.kind() == RouteDesigner.Kind.VIADUCT || st.kind() == RouteDesigner.Kind.TUNNEL
                        || st.kind() == RouteDesigner.Kind.DEEP_CUT || (st.kind() == RouteDesigner.Kind.BRIDGE && st.length() > 150);
                if (bad && (worst == null || st.length() > worst.length())) worst = st;
            }
            if (worst == null) continue;
            double[] f = new double[5];
            r.curve().frame(0.5 * (worst.s0() + worst.s1()), f);
            double half = Math.max(800, 0.5 * worst.length() + 600), mp = Math.max(2, Math.ceil(2 * half / 1600));
            double x0 = Math.floor((f[0] - half) / 16) * 16, z0 = Math.floor((f[1] - half) / 16) * 16;
            int n = (int) Math.ceil(2 * half / mp);
            BufferedImage img = TerrainPlanMaps.render(s, x0, z0, n, n, mp);
            contours(s, img, x0, z0, mp);
            draw(img, plan, x0, z0, mp, 1f, false);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.drawString(String.format(Locale.ROOT, "%s  %s %.0f m at %.0f..%.0f  (%.0f m/px, 10 m contours)", r.id(), worst.kind(), worst.length(),
                    worst.s0(), worst.s1(), mp), 8, 20);
            g.dispose();
            ImageIO.write(img, "png", new File(dir, r.id().replace(':', '_') + ".png"));
        }
    }

    /** 10 m contour lines of the planned ground (thin, translucent black; every 50 m darker). */
    static void contours(TerrainPlanSurface s, BufferedImage img, double x0, double z0, double mpp) {
        int w = img.getWidth(), h = img.getHeight();
        double[] hh = new double[w * h];
        for (int py = 0; py < h; py++) for (int px = 0; px < w; px++) hh[py * w + px] = s.heightAt(x0 + (px + 0.5) * mpp, z0 + (py + 0.5) * mpp);
        for (int py = 0; py + 1 < h; py++) for (int px = 0; px + 1 < w; px++) {
            double a = hh[py * w + px], b = hh[py * w + px + 1], c = hh[(py + 1) * w + px];
            int la = (int) Math.floor(a / 10), lb = (int) Math.floor(b / 10), lc = (int) Math.floor(c / 10);
            if (la == lb && la == lc) continue;
            int lv = Math.max(la, Math.max(lb, lc));
            int rgb = img.getRGB(px, py);
            double k = lv % 5 == 0 ? 0.55 : 0.75;
            int r = (int) (((rgb >> 16) & 255) * k), gg = (int) (((rgb >> 8) & 255) * k), bb = (int) ((rgb & 255) * k);
            img.setRGB(px, py, 0xFF000000 | r << 16 | gg << 8 | bb);
        }
    }

    /** Routes drawn true to width (at least `minPx`), structures marked: bridges blue, viaducts violet, tunnels black. */
    static void draw(BufferedImage img, RoadNetworkPlan plan, double x0, double z0, double mpp, float minPx, boolean labels) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double[] f = new double[5];
        for (RoadNetworkPlan.Route r : plan.routes()) {
            Path2D.Double p = new Path2D.Double();
            for (double st = 0; st <= r.curve().length; st += Math.max(2, mpp)) {
                r.curve().frame(st, f);
                double px = (f[0] - x0) / mpp, py = (f[1] - z0) / mpp;
                if (st == 0) p.moveTo(px, py); else p.lineTo(px, py);
            }
            float wpx = (float) Math.max(minPx, 2 * r.roadClass().edge / mpp);
            g.setStroke(new BasicStroke(wpx + 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0, 0, 0, 160));
            g.draw(p);
            g.setStroke(new BasicStroke(wpx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(r.roadClass() == RoadClassSpec.INTERSTATE ? INTERSTATE : r.roadClass() == RoadClassSpec.STATE ? STATE : COUNTY);
            g.draw(p);
            for (RouteDesigner.Structure s : r.design().structures()) {
                Color c = switch (s.kind()) {
                    case BRIDGE -> new Color(0x1E5BFF);
                    case CULVERT -> new Color(0x00C8C8);
                    case VIADUCT -> new Color(0x9B30FF);
                    case TUNNEL -> Color.BLACK;
                    case DEEP_CUT -> new Color(0x7A4A00);
                };
                Path2D.Double q = new Path2D.Double();
                for (double st = s.s0(); st <= s.s1() + 1e-9; st += Math.max(1, mpp / 2)) {
                    r.curve().frame(st, f);
                    double px = (f[0] - x0) / mpp, py = (f[1] - z0) / mpp;
                    if (st == s.s0()) q.moveTo(px, py); else q.lineTo(px, py);
                }
                g.setStroke(new BasicStroke(wpx + 3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                g.setColor(c);
                g.draw(q);
            }
        }
        if (labels) {
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            for (RoadNetworkPlan.Node n : plan.nodes()) {
                double px = (n.x() - x0) / mpp, py = (n.z() - z0) / mpp;
                Color c = switch (n.kind()) {
                    case CITY, INTERCHANGE, JUNCTION -> Color.WHITE;
                    case BORDER_CROSSING -> new Color(0xFF66CC);
                    case PASS -> new Color(0xFFFF66);
                    case BRIDGE -> new Color(0x66AAFF);
                    case TUNNEL -> Color.BLACK;
                };
                g.setColor(Color.BLACK);
                g.fillOval((int) px - 5, (int) py - 5, 10, 10);
                g.setColor(c);
                g.fillOval((int) px - 4, (int) py - 4, 8, 8);
                if (n.route() == null) {
                    g.setColor(Color.BLACK);
                    g.drawString(n.id(), (int) px + 7, (int) py + 5);
                    g.setColor(Color.WHITE);
                    g.drawString(n.id(), (int) px + 6, (int) py + 4);
                }
            }
        }
        g.dispose();
    }

    // ------------------------------------------------------------------ profiles
    /** One sheet per route: ground (trimmed mean across the formation), design profile, structures, grade. */
    static void profiles(RoadNetworkPlan plan, File out) throws Exception {
        File dir = new File(out, "profiles");
        dir.mkdirs();
        for (RoadNetworkPlan.Route r : plan.routes()) {
            double[] e = r.profile().samples(), gr = r.design().ground();
            byte[] wet = r.design().wet();
            int W = 1600, H = 420, top = 34, bottom = 60, left = 50;
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int i = 0; i < e.length; i++) { lo = Math.min(lo, Math.min(e[i], gr[i])); hi = Math.max(hi, Math.max(e[i], gr[i])); }
            lo = Math.floor(lo / 10) * 10 - 5;
            hi = Math.ceil(hi / 10) * 10 + 5;
            double len = r.profile().length, sx = (W - left - 10) / len, sy = (H - top - bottom) / (hi - lo);
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, W, H);
            for (RouteDesigner.Structure s : r.design().structures()) {
                Color c = switch (s.kind()) {
                    case BRIDGE -> new Color(0xCCDDFF);
                    case CULVERT -> new Color(0xCCF5F5);
                    case VIADUCT -> new Color(0xE6CCFF);
                    case TUNNEL -> new Color(0xCCCCCC);
                    case DEEP_CUT -> new Color(0xF0DEC0);
                };
                g.setColor(c);
                g.fillRect(left + (int) (s.s0() * sx), top, Math.max(2, (int) (s.length() * sx)), H - top - bottom);
            }
            g.setColor(new Color(0xEEEEEE));
            for (double y = lo; y <= hi; y += 10) g.drawLine(left, (int) (H - bottom - (y - lo) * sy), W - 10, (int) (H - bottom - (y - lo) * sy));
            g.setColor(Color.GRAY);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            for (double y = lo; y <= hi; y += 20) g.drawString(String.format(Locale.ROOT, "%.0f", y), 6, (int) (H - bottom - (y - lo) * sy) + 4);
            for (double st = 0; st <= len; st += 1000) g.drawString(String.format(Locale.ROOT, "%.0f km", st / 1000), left + (int) (st * sx) - 10, H - bottom + 14);
            Path2D.Double pg = new Path2D.Double(), pe = new Path2D.Double();
            for (int i = 0; i < e.length; i++) {
                double px = left + i * RoadProfile.DS * sx;
                if (i == 0) { pg.moveTo(px, H - bottom - (gr[i] - lo) * sy); pe.moveTo(px, H - bottom - (e[i] - lo) * sy); }
                else { pg.lineTo(px, H - bottom - (gr[i] - lo) * sy); pe.lineTo(px, H - bottom - (e[i] - lo) * sy); }
            }
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(new Color(0x6B4F2A));
            g.draw(pg);
            g.setStroke(new BasicStroke(2f));
            g.setColor(r.roadClass() == RoadClassSpec.INTERSTATE ? INTERSTATE : new Color(0xC08000));
            g.draw(pe);
            for (int i = 0; i < wet.length; i++)
                if (wet[i] != RouteTerrain.DRY) {
                    g.setColor(wet[i] == RouteTerrain.MARSH ? new Color(0x5F8F7A) : new Color(0x2E7BC8));
                    g.fillRect(left + (int) (i * RoadProfile.DS * sx), H - bottom + 18, Math.max(1, (int) (RoadProfile.DS * sx) + 1), 6);
                }
            // grade strip
            int gy = H - bottom + 30;
            for (int i = 1; i < e.length; i++) {
                double gd = Math.abs(e[i] - e[i - 1]) / RoadProfile.DS;
                Color c = gd <= r.roadClass().grade * 0.5 ? new Color(0x9CD69C) : gd <= r.roadClass().grade + 1e-9 ? new Color(0xF2D16B)
                        : new Color(0xE8743B);
                g.setColor(c);
                g.fillRect(left + (int) (i * RoadProfile.DS * sx), gy, Math.max(1, (int) (RoadProfile.DS * sx) + 1), 8);
            }
            RouteDesigner.Stats st = r.design().stats();
            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.drawString(String.format(Locale.ROOT, "%s  (%s, %.0f m)  design grade max %.1f%% (limit %.0f%% / mountain %.0f%%)  cut max %.1f  fill max %.1f  bridges %d  viaducts %d  tunnels %d  deep cuts %d",
                    r.id(), r.roadClass().id, len, 100 * st.maxGrade(), 100 * r.roadClass().grade, 100 * r.roadClass().gradeMountain, st.maxCut(),
                    st.maxFill(), st.bridges(), st.viaducts(), st.tunnels(), st.deepCuts()), 8, 20);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString("brown: ground (trimmed mean across the formation)  red/orange: design profile  bands: bridge blue, culvert cyan, viaduct violet, tunnel grey, deep cut tan  strip: water / grade (green <= half limit, yellow <= limit, orange mountain grade)",
                    8, H - 8);
            g.dispose();
            ImageIO.write(img, "png", new File(dir, r.id().replace(':', '_') + ".png"));
        }
    }
}
