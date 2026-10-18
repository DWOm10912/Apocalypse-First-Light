package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.network.town.TownStreets;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Debug picture of a framework town (TownCheck -PtownPng=dir): the planned ground shaded, water, the built roads' paved
 * bands, the town's blocks by zone with their lots, its streets (asphalt and right of way) and names, the limit. North up,
 * PX metres per pixel.
 */
final class TownPng {
    private TownPng() {}

    static final double PX = 1.0, MARGIN = 250;

    static final Map<String, Color> ZONES = Map.of(
            "down", new Color(0xc98a5e), "hwy", new Color(0xe0b04f), "res", new Color(0xefe3c4), "mobile", new Color(0xe6cfae),
            "civic", new Color(0x86a6c8), "school", new Color(0xa7bfd9), "park", new Color(0xa9cf92), "ind", new Color(0xaca3b8),
            "park_lot", new Color(0xd8d6cf));

    static void draw(TownPlan p, List<RoadDesign> roads, RouteTerrain terrain, File out) throws Exception {
        // bounds: the limit's corners in the world
        double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, z0 = Double.POSITIVE_INFINITY, z1 = Double.NEGATIVE_INFINITY;
        for (double s : new double[]{p.limit[0], p.limit[1]})
            for (double v : new double[]{p.limit[2], p.limit[3]}) {
                double[] w = p.world(s, v);
                x0 = Math.min(x0, w[0]); x1 = Math.max(x1, w[0]); z0 = Math.min(z0, w[1]); z1 = Math.max(z1, w[1]);
            }
        x0 -= MARGIN; x1 += MARGIN; z0 -= MARGIN; z1 += MARGIN;
        int W = (int) Math.ceil((x1 - x0) / PX), H = (int) Math.ceil((z1 - z0) / PX);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        // ground: height tint and hill shade
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        double[][] g = new double[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                g[y][x] = terrain.height(x0 + x * PX, z0 + y * PX);
                lo = Math.min(lo, g[y][x]);
                hi = Math.max(hi, g[y][x]);
            }
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                double wx = x0 + x * PX, wz = z0 + y * PX;
                if (terrain.water(wx, wz) != RouteTerrain.DRY) { img.setRGB(x, y, 0x6f9fc8); continue; }
                double t = (g[y][x] - lo) / Math.max(1, hi - lo);
                double dx = g[y][Math.min(W - 1, x + 1)] - g[y][Math.max(0, x - 1)], dz = g[Math.min(H - 1, y + 1)][x] - g[Math.max(0, y - 1)][x];
                double shade = Math.max(0.55, Math.min(1.15, 1 - 0.35 * (dx - dz) / (2 * PX)));
                int r = (int) Math.min(255, (150 + 70 * t) * shade), gg = (int) Math.min(255, (165 + 50 * t) * shade), b = (int) Math.min(255, (130 + 60 * t) * shade);
                // contour every 5 m
                if (Math.floor(g[y][x] / 5) != Math.floor(g[y][Math.min(W - 1, x + 1)] / 5) || Math.floor(g[y][x] / 5) != Math.floor(g[Math.min(H - 1, y + 1)][x] / 5)) {
                    r = r * 4 / 5; gg = gg * 4 / 5; b = b * 4 / 5;
                }
                img.setRGB(x, y, (r << 16) | (gg << 8) | b);
            }
        Graphics2D gr = img.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gr.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        final double fx0 = x0, fz0 = z0;
        java.util.function.BiFunction<Double, Double, double[]> px = (wx, wz) -> new double[]{(wx - fx0) / PX, (wz - fz0) / PX};
        // the built roads: paved band
        for (RoadDesign d : roads) {
            Color c = d.section == CrossSection.R4 ? new Color(0x4a4d52) : d.section == CrossSection.RAMP ? new Color(0x5d6066) : new Color(0x6a6d72);
            gr.setColor(c);
            gr.setStroke(new BasicStroke((float) (2 * d.section.edge / PX), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            Path2D.Double path = new Path2D.Double();
            boolean any = false;
            for (double s = 0; s <= d.length; s += 4) {
                double[] w = d.point(s, 0), q = px.apply(w[0], w[1]);
                if (q[0] < -200 || q[1] < -200 || q[0] > W + 200 || q[1] > H + 200) { any = false; continue; }
                if (!any) path.moveTo(q[0], q[1]); else path.lineTo(q[0], q[1]);
                any = true;
            }
            gr.draw(path);
        }
        // the town: blocks and lots
        for (TownPlan.Lot l : p.lots) {
            Path2D.Double poly = quad(p, px, l.s0(), l.s1(), l.v0(), l.v1());
            gr.setColor(ZONES.getOrDefault(l.zone(), Color.MAGENTA));
            gr.fill(poly);
            gr.setColor(new Color(0x7a6f5f));
            gr.setStroke(new BasicStroke(0.6f));
            gr.draw(poly);
        }
        for (TownPlan.Block b : p.blocks) {
            gr.setColor(new Color(0x3a3a3a));
            gr.setStroke(new BasicStroke(1.2f));
            gr.draw(quad(p, px, b.s0(), b.s1(), b.v0(), b.v1()));
        }
        // streets: right of way (light) and asphalt (dark)
        for (boolean asphalt : new boolean[]{false, true})
            for (TownPlan.Street st : p.streets) {
                TownPlan.Node a = p.nodes.get(st.a()), b = p.nodes.get(st.b());
                TownStreets.Section sec = TownStreets.of(st.cls());
                double[] wa = p.world(a.s(), a.v()), wb = p.world(b.s(), b.v()), qa = px.apply(wa[0], wa[1]), qb = px.apply(wb[0], wb[1]);
                gr.setColor(asphalt ? new Color(0x55585d) : new Color(0xd9d7d0));
                gr.setStroke(new BasicStroke((float) ((asphalt ? sec.asphalt() : sec.row()) / PX), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
                gr.draw(new java.awt.geom.Line2D.Double(qa[0], qa[1], qb[0], qb[1]));
            }
        // street names (once per name and line, at the longest segment's middle) and the node heights
        gr.setFont(new Font("SansSerif", Font.BOLD, 11));
        java.util.Set<String> named = new java.util.HashSet<>();
        for (TownPlan.Street st : p.streets) {
            TownPlan.Node a = p.nodes.get(st.a()), b = p.nodes.get(st.b());
            String key = st.name() + (Math.abs(a.s() - b.s()) < 1 ? "@" + Math.round(a.s()) : "@" + Math.round(a.v()));
            if (!named.add(key)) continue;
            double[] wa = p.world(a.s(), a.v()), wb = p.world(b.s(), b.v()), qa = px.apply(wa[0], wa[1]), qb = px.apply(wb[0], wb[1]);
            double ang = Math.atan2(qb[1] - qa[1], qb[0] - qa[0]);
            if (ang > Math.PI / 2) ang -= Math.PI; else if (ang < -Math.PI / 2) ang += Math.PI;
            AffineTransform old = gr.getTransform();
            gr.translate((qa[0] + qb[0]) / 2, (qa[1] + qb[1]) / 2);
            gr.rotate(ang);
            gr.setColor(Color.WHITE);
            int tw = gr.getFontMetrics().stringWidth(st.name());
            gr.drawString(st.name(), -tw / 2f, 4);
            gr.setTransform(old);
        }
        gr.setFont(new Font("SansSerif", Font.PLAIN, 10));
        for (TownPlan.Node n : p.nodes) {
            double[] w = p.world(n.s(), n.v()), q = px.apply(w[0], w[1]);
            gr.setColor(new Color(0x202020));
            gr.drawString(String.format(Locale.ROOT, "%.1f", n.h()), (float) q[0] + 3, (float) q[1] - 3);
        }
        // the limit
        gr.setColor(new Color(0x2a6f2a));
        gr.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{12, 8}, 0));
        gr.draw(quad(p, px, p.limit[0], p.limit[1], p.limit[2], p.limit[3]));
        gr.setColor(Color.BLACK);
        gr.setFont(new Font("SansSerif", Font.BOLD, 18));
        gr.drawString(p.name + " (" + p.town + ", " + p.state + ")  " + p.blocks.size() + " blocks, " + p.lots.size() + " lots", 12, 24);
        gr.dispose();
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
    }

    private static Path2D.Double quad(TownPlan p, java.util.function.BiFunction<Double, Double, double[]> px, double s0, double s1, double v0, double v1) {
        Path2D.Double path = new Path2D.Double();
        double[][] c = {{s0, v0}, {s1, v0}, {s1, v1}, {s0, v1}};
        for (int i = 0; i < 4; i++) {
            double[] w = p.world(c[i][0], c[i][1]), q = px.apply(w[0], w[1]);
            if (i == 0) path.moveTo(q[0], q[1]); else path.lineTo(q[0], q[1]);
        }
        path.closePath();
        return path;
    }
}
