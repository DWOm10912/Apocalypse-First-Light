package com.antaurora.apofirstlight.worldgen.federal;

import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.Physiography;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Federal six-state map, offline preview (C1; docs/worldgen/federal_six_state_c1.md). Draws the political map (states,
 * coast, islands, city candidates, start region, corridors when a road plan is given) and the physical map (province
 * blend with the borders on top, to show they are independent), and prints the state statistics.
 *   java FederalPreview SEED OUT_DIR [ROADS_JSON_IGNORED]
 * Output: OUT_DIR/political_SEED.png, physical_SEED.png, climate_SEED.png, stats_SEED.txt
 */
public final class FederalPreview {
    static final double HALF = 13000;      // drawn extent, m
    static final int PX = 1040;            // 25 m per pixel

    static final Color[] STATE = {new Color(0x6E9A57), new Color(0xD8C77A), new Color(0x7F93A6), new Color(0x8D7FA8),
            new Color(0x5E9E8E), new Color(0xC99263)};
    static final Color[] PHYS = {new Color(0x8C8F9A), new Color(0xC98A55), new Color(0xD9CF8C), new Color(0x6FA3B8),
            new Color(0x4F7F4A), new Color(0x7FB89A)};
    static final String[] THEME_ZH = {"温带森林与河谷", "农业平原", "寒冷山地与针叶林", "工业湖区与混交林", "南部湿地与海岸平原", "西部荒漠高原与峡谷"};
    static final String[] PHYS_ZH = {"山地", "高原/峡谷", "平原", "湖区低地", "丘陵河谷", "海岸低地"};

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : -295378578869149513L;
        File out = new File(args.length > 1 ? args[1] : "build/federal-worldgen/preview");
        out.mkdirs();
        FederalMap m = FederalMap.forSeed(seed);
        NationalRoadPlan roads = args.length > 2 && args[2].equals("roads") ? NationalRoadPlan.build(m, null) : null;
        draw(m, roads, new File(out, "political_" + seed + ".png"), false);
        draw(m, roads, new File(out, "physical_" + seed + ".png"), true);
        climate(m, new File(out, "climate_" + seed + ".png"));
        try (PrintStream ps = new PrintStream(new File(out, "stats_" + seed + ".txt"), StandardCharsets.UTF_8)) {
            stats(m, ps);
            if (roads != null) roads.report(ps);
        }
        if (roads != null) java.nio.file.Files.writeString(new File(out, "national_roads_" + seed + ".json").toPath(), roads.toJson());
        stats(m, System.out);
        if (roads != null) roads.report(System.out);
    }

    /** Label centre for a state: inside the state, clear of every city and its name, as near the centroid as possible. */
    static int[] labelSpot(FederalMap m, FederalStateId[] st, FederalStateId id, int cx, int cz) {
        int[] best = {cx, cz};
        double bestD = Double.MAX_VALUE;
        for (int y = 40; y < PX - 20; y += 6) for (int x = 80; x < PX - 80; x += 6) {
            boolean inside = true;
            for (int dy = -30; dy <= 8 && inside; dy += 19) for (int dx = -70; dx <= 70 && inside; dx += 35)
                inside = st[(y + dy) * PX + x + dx] == id;
            if (!inside) continue;
            boolean clear = true;
            for (FederalMap.City c : m.cities()) {
                int qx = p(c.x()), qz = p(c.z());
                if (qx + 130 > x - 74 && qx - 10 < x + 74 && qz + 10 > y - 34 && qz - 12 < y + 12) { clear = false; break; }
            }
            if (!clear) continue;
            double d = Math.hypot(x - cx, y - cz);
            if (d < bestD) { bestD = d; best = new int[]{x, y}; }
        }
        return best;
    }

    static double wx(int px) { return -HALF + (px + 0.5) * (2 * HALF / PX); }

    static void draw(FederalMap m, NationalRoadPlan roads, File file, boolean physical) throws Exception {
        BufferedImage img = new BufferedImage(PX, PX, BufferedImage.TYPE_INT_RGB);
        FederalStateId[] st = new FederalStateId[PX * PX];
        double[] w = new double[Physiography.values().length];
        for (int py = 0; py < PX; py++) for (int px = 0; px < PX; px++) {
            double x = Math.floor(wx(px)), z = Math.floor(wx(py));
            var s = m.sample((int) x, (int) z);
            FederalStateId id = m.stateAt(x, z);
            st[py * PX + px] = id;
            int rgb;
            if (!s.isLand()) {
                double depth = Math.max(0, FederalMap.SEA_LEVEL - s.surfaceHeight());
                double t = Math.min(1, depth / 60);
                rgb = lerp(new Color(0x9CC3DA), new Color(0x1F3F66), t).getRGB();
            } else if (physical) {
                m.physiography(x, z, w);
                double r = 0, g = 0, b = 0;
                for (int k = 0; k < w.length; k++) { r += w[k] * PHYS[k].getRed(); g += w[k] * PHYS[k].getGreen(); b += w[k] * PHYS[k].getBlue(); }
                rgb = new Color((int) r, (int) g, (int) b).getRGB();
            } else {
                rgb = STATE[id.ordinal()].getRGB();
            }
            img.setRGB(px, py, rgb);
        }
        // borders and coast
        for (int py = 1; py < PX - 1; py++) for (int px = 1; px < PX - 1; px++) {
            FederalStateId a = st[py * PX + px];
            if (a == null) continue;
            FederalStateId r = st[py * PX + px + 1], d = st[(py + 1) * PX + px];
            if ((r != null && r != a) || (d != null && d != a)) img.setRGB(px, py, physical ? 0x202020 : 0x3A3A3A);
            if (r == null || d == null || st[py * PX + px - 1] == null || st[(py - 1) * PX + px] == null) img.setRGB(px, py, 0x1A2A33);
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font zh = new Font("Microsoft YaHei", Font.BOLD, 15), small = new Font("Microsoft YaHei", Font.PLAIN, 11);
        if (roads != null) drawRoads(g, roads);
        // crossings
        g.setStroke(new BasicStroke(2.2f));
        g.setColor(new Color(0x404040));
        for (FederalMap.Crossing c : m.crossings()) g.drawLine(p(c.fromX()), p(c.fromZ()), p(c.toX()), p(c.toZ()));
        // start
        FederalMap.Start s = m.start();
        g.setColor(new Color(0xB03030));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{6, 5}, 0));
        int rr = (int) Math.round(s.radius() * PX / (2 * HALF));
        g.drawOval(p(s.x()) - rr, p(s.z()) - rr, 2 * rr, 2 * rr);
        g.fillOval(p(s.x()) - 4, p(s.z()) - 4, 8, 8);
        g.setFont(small);
        g.drawString("开局区（森林老宅）", p(s.x()) + 6, p(s.z()) - 6);
        // cities
        for (FederalMap.City c : m.cities()) {
            int r = c.rank() == 1 ? 7 : c.rank() == 2 ? 5 : 3;
            g.setColor(Color.WHITE);
            g.fillOval(p(c.x()) - r - 1, p(c.z()) - r - 1, 2 * r + 2, 2 * r + 2);
            g.setColor(new Color(0x202020));
            g.fillOval(p(c.x()) - r, p(c.z()) - r, 2 * r, 2 * r);
            g.setFont(small);
            g.drawString(c.id(), p(c.x()) + r + 2, p(c.z()) + 4);
        }
        // islands
        g.setFont(small);
        for (FederalMap.Island is : m.islands()) {
            g.setColor(new Color(0x202020));
            g.drawString("卫星岛 " + is.id() + " " + is.role(), p(is.x()) - 50, p(is.z() + is.major() * 1.2) + 12);
        }
        // state labels at the land centroid of each state
        if (!physical) {
            g.setFont(zh);
            for (FederalStateId id : FederalStateId.values()) {
                double sx = 0, sz = 0, n = 0;
                for (int i = 0; i < st.length; i++) if (st[i] == id) { sx += i % PX; sz += i / PX; n++; }
                if (n == 0) continue;
                int cx = (int) (sx / n), cz = (int) (sz / n);
                int[] at = labelSpot(m, st, id, cx, cz);
                cx = at[0];
                cz = at[1];
                String a = id.key().toUpperCase(), b = THEME_ZH[FederalTemplate.theme(id).ordinal()];
                g.setColor(new Color(255, 255, 255, 190));
                g.fillRoundRect(cx - 70, cz - 30, 140, 38, 8, 8);
                g.setColor(new Color(0x1E1E1E));
                g.drawString(a, cx - g.getFontMetrics().stringWidth(a) / 2, cz - 12);
                g.setFont(small);
                g.drawString(b, cx - g.getFontMetrics().stringWidth(b) / 2, cz + 4);
                g.setFont(zh);
            }
        } else {
            g.setFont(small);
            int y = 20;
            for (Physiography ph : Physiography.values()) {
                g.setColor(PHYS[ph.ordinal()]);
                g.fillRect(12, y - 10, 14, 12);
                g.setColor(new Color(0x101010));
                g.drawString(PHYS_ZH[ph.ordinal()] + " " + ph, 32, y);
                y += 17;
            }
            g.drawString("黑线 = 州界（不决定地形或群系）", 12, y + 4);
        }
        // scale bar: 5 km
        g.setColor(new Color(0x101010));
        g.setStroke(new BasicStroke(3));
        int bar = (int) Math.round(5000.0 * PX / (2 * HALF));
        g.drawLine(PX - 30 - bar, PX - 24, PX - 30, PX - 24);
        g.setFont(small);
        g.drawString("5 km", PX - 30 - bar / 2 - 12, PX - 30);
        g.drawString("N ↑   seed " + m.seed + "   " + FederalTemplate.VERSION, 12, PX - 12);
        g.dispose();
        ImageIO.write(img, "png", file);
    }

    static void drawRoads(Graphics2D g, NationalRoadPlan roads) {
        for (NationalRoadPlan.Corridor c : roads.corridors()) {
            boolean inter = c.roadClass() == FederalTemplate.RoadClass.INTERSTATE;
            g.setStroke(new BasicStroke(inter ? 4.2f : 2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(inter ? new Color(0xB8322B) : new Color(0x3A3A3A));
            double[] xs = c.xs(), zs = c.zs();
            for (int i = 1; i < xs.length; i++) g.drawLine(p(xs[i - 1]), p(zs[i - 1]), p(xs[i]), p(zs[i]));
        }
        g.setColor(new Color(0x101010));
        for (NationalRoadPlan.Node n : roads.nodes()) {
            if (n.kind() == NationalRoadPlan.NodeKind.INTERCHANGE) { g.fillRect(p(n.x()) - 5, p(n.z()) - 5, 10, 10); }
            if (n.kind() == NationalRoadPlan.NodeKind.BORDER_CROSSING) { g.drawOval(p(n.x()) - 4, p(n.z()) - 4, 8, 8); }
            if (n.kind() == NationalRoadPlan.NodeKind.PASS) { g.fillPolygon(new int[]{p(n.x()) - 6, p(n.x()) + 6, p(n.x())}, new int[]{p(n.z()) + 5, p(n.z()) + 5, p(n.z()) - 7}, 3); }
            if (n.kind() == NationalRoadPlan.NodeKind.BRIDGE) { g.drawRect(p(n.x()) - 4, p(n.z()) - 4, 8, 8); }
        }
    }

    static void climate(FederalMap m, File file) throws Exception {
        int px2 = PX / 2;
        BufferedImage img = new BufferedImage(px2 * 2, px2, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < px2; py++) for (int px = 0; px < px2; px++) {
            double x = -HALF + (px + 0.5) * (2 * HALF / px2), z = -HALF + (py + 0.5) * (2 * HALF / px2);
            boolean land = m.isLand(x, z);
            int t = land ? lerp(new Color(0x3B6BB5), new Color(0xD8553A), m.temperature(x, z)).getRGB() : 0xE8EEF2;
            int w = land ? lerp(new Color(0xD9B26A), new Color(0x2E7D5B), m.moisture(x, z)).getRGB() : 0xE8EEF2;
            img.setRGB(px, py, t);
            img.setRGB(px + px2, py, w);
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        g.drawString("温度（蓝冷 → 红暖）", 10, 20);
        g.drawString("湿度（黄干 → 绿湿）", px2 + 10, 20);
        g.dispose();
        ImageIO.write(img, "png", file);
    }

    static void stats(FederalMap m, PrintStream out) {
        double cell = 25;
        Map<FederalStateId, Double> area = new EnumMap<>(FederalStateId.class);
        Map<FederalStateId, double[]> phys = new EnumMap<>(FederalStateId.class);
        double[] w = new double[Physiography.values().length];
        double land = 0;
        int mainCells = 0;
        for (double z = -HALF; z < HALF; z += cell) for (double x = -HALF; x < HALF; x += cell) {
            FederalStateId s = m.stateAt(x + cell / 2, z + cell / 2);
            if (s == null) continue;
            land += cell * cell;
            area.merge(s, cell * cell, Double::sum);
            m.physiography(x + cell / 2, z + cell / 2, w);
            double[] acc = phys.computeIfAbsent(s, k -> new double[w.length]);
            for (int k = 0; k < w.length; k++) acc[k] += w[k];
            if (m.mainlandDistance(x + cell / 2, z + cell / 2) >= 0) mainCells++;
        }
        var b = m.landBounds();
        out.printf("template %s  seed %d  scale %.2f%n", FederalTemplate.VERSION, m.seed, m.scale);
        out.printf("land %.1f km2 (mainland %.1f km2)  bounds x %.0f..%.0f  z %.0f..%.0f  (%.1f x %.1f km)%n", land / 1e6,
                mainCells * cell * cell / 1e6, b.minX(), b.maxX(), b.minZ(), b.maxZ(), (b.maxX() - b.minX()) / 1000, (b.maxZ() - b.minZ()) / 1000);
        for (FederalStateId s : FederalStateId.values()) {
            double[] acc = phys.get(s);
            double a = area.getOrDefault(s, 0.0);
            StringBuilder p = new StringBuilder();
            if (acc != null) {
                double sum = 0;
                for (double v : acc) sum += v;
                for (Physiography ph : Physiography.values()) {
                    double share = acc[ph.ordinal()] / sum;
                    if (share >= 0.05) p.append(String.format(" %s %.0f%%", ph, 100 * share));
                }
            }
            out.printf("  %s %-30s %5.1f km2 %s%n", s.key(), FederalTemplate.theme(s), a / 1e6, p);
        }
        for (FederalMap.Island is : m.islands())
            out.printf("  island %d %-13s centre %.0f %.0f  state %s%n", is.id(), is.role(), is.x(), is.z(), m.stateAt(is.x(), is.z()));
        for (FederalMap.City c : m.cities())
            out.printf("  city %-16s %s %-10s rank %d at %.0f %.0f %s%n", c.id(), c.state().key(), c.role(), c.rank(), c.x(), c.z(),
                    c.jittered() ? "" : "(template point)");
        var s = m.start();
        out.printf("  start %s at %.0f %.0f r %.0f  dominant province %s%n", s.state().key(), s.x(), s.z(), s.radius(), m.dominantPhysiography(s.x(), s.z()));
    }

    static int p(double world) { return (int) Math.round((world + HALF) * PX / (2 * HALF)); }

    static Color lerp(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
