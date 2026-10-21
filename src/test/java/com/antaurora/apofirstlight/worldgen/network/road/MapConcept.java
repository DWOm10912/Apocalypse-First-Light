package com.antaurora.apofirstlight.worldgen.network.road;

import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.network.town.TownBuildings;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.network.town.TownWorks;
import com.antaurora.apofirstlight.worldgen.terrain.v2.EcologyBiomes;
import com.antaurora.apofirstlight.worldgen.terrain.v2.FederalEcologyBiomes;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Concept pictures of AFL's own map (docs/ui/afl_map_v1_plan.md; TownCheck -PmapConcept=dir): the world map screen,
 * the minimap, the stepped block-colour map against the vector road map, a town zoomed in. Drawn from a real test
 * world's terrain plan, ecology, road network and town plans, so the pictures show what the client already knows.
 */
final class MapConcept {
    private MapConcept() {}

    static final int WATER = 0x6FA3CF, STREET = 0xF7F4EC, WALK = 0xD9D3C7, HWY = 0xF0B54A, HWY_EDGE = 0x8E6420, ROAD = 0xFBF8F0,
            ROAD_EDGE = 0x8A857C, BUILDING = 0xCDC2B0, BUILDING_EDGE = 0x8A806F, APRON = 0xE4DFD4, FOG = 0x1D2228, PAPER = 0xEDE8DA;
    static final Color PANEL = new Color(15, 19, 24, 214), PANEL_EDGE = new Color(255, 255, 255, 38), TEXT = new Color(0xF2F4F6),
            DIM = new Color(0xA7B0BA), ACCENT = new Color(0xE3A43A);
    static final double REVEAL = 72;

    static final Map<String, String> ZH = Map.ofEntries(
            Map.entry("sea", "海"), Map.entry("sandy_beach", "沙滩"), Map.entry("temperate_grassland", "温带草地"),
            Map.entry("oak_hickory_woodland", "栎-山核桃林地"), Map.entry("mixed_mesophytic_forest", "坡地混合阔叶林"),
            Map.entry("ridge_oak_forest", "山脊栎林"), Map.entry("hemlock_hollow", "谷地铁杉-白松林"), Map.entry("riparian_forest", "河岸林"),
            Map.entry("coastal_pine_oak_forest", "海岸松栎林"), Map.entry("coastal_sandy_clearing", "海岸沙地疏林草地"),
            Map.entry("coastal_swamp_forest", "海岸沼泽林"), Map.entry("tidal_marsh", "潮汐湿地"), Map.entry("wet_prairie", "湿草甸"),
            Map.entry("montane_conifer_forest", "山地针叶林"), Map.entry("alpine_meadow", "高山草甸"), Map.entry("alpine_peaks", "高山岩峰"),
            Map.entry("lakeshore_mixed_forest", "湖岸针阔混交林"), Map.entry("lake_fen", "湖滨沼泽"), Map.entry("freshwater_lake", "淡水湖"),
            Map.entry("plateau_steppe", "高原干草原"), Map.entry("canyon_badlands", "峡谷劣地"));

    record Ctx(TerrainPlanSurface s, RouteTerrain terrain, List<RoadDesign> roads, RoadWorks works, TownPlan town,
               List<TownBuildings.Site> sites, Set<Long> explored, double[] player) {}

    static Font cn(int style, float size) { return new Font("Microsoft YaHei", style, 1).deriveFont(size); }

    static void draw(TerrainPlanSurface s, RouteTerrain terrain, List<RoadDesign> roads, RoadWorks works, List<TownPlan> towns, File dir) throws Exception {
        TownPlan town = null;
        for (TownPlan p : towns) if (p.town.equals("valley_town")) town = p;
        if (town == null) { System.out.println("  map concept: no valley_town"); return; }
        List<TownBuildings.Site> sites = TownBuildings.sites(town, works.towns().fuelStop(town));
        // where the user stood in the fuel stop's forecourt in the test world (2026-10-20 screenshot)
        double[] player = {3767.5, 13.5};
        // what a player driving in on F-40 and around the town has seen: chunks within REVEAL of the path
        Set<Long> explored = new HashSet<>();
        RoadDesign host = null, cross = null;
        for (RoadDesign d : roads) {
            if (d.id.startsWith("town_") && cross == null) cross = d;
            RoadNames.Route r = route(d);
            if (r != null && r.number() == 40 && d.section == CrossSection.R4 && d.local(player[0], player[1], 3000) != null) host = d;
        }
        if (host != null) {
            double[] l = host.local(player[0], player[1], 3000);
            for (double t = Math.max(0, l[0] - 2200); t <= Math.min(host.length, l[0] + 500); t += 12) reveal(explored, host.point(t, 0));
        }
        if (cross != null) for (double t = 0; t <= cross.length; t += 12) reveal(explored, cross.point(t, 0));
        for (TownPlan.Street st : town.streets) {
            TownPlan.Node a = town.nodes.get(st.a()), b = town.nodes.get(st.b());
            double[] wa = town.world(a.s(), a.v()), wb = town.world(b.s(), b.v());
            if (Math.hypot((wa[0] + wb[0]) / 2 - player[0], (wa[1] + wb[1]) / 2 - player[1]) > 520) continue;
            double len = Math.hypot(wb[0] - wa[0], wb[1] - wa[1]);
            for (double t = 0; t <= len; t += 12) reveal(explored, new double[]{wa[0] + (wb[0] - wa[0]) * t / len, wa[1] + (wb[1] - wa[1]) * t / len});
        }
        Ctx c = new Ctx(s, terrain, roads, works, town, sites, explored, player);
        dir.mkdirs();
        worldMap(c, host, new File(dir, "map_world_concept.png"));
        townMap(c, new File(dir, "map_town_concept.png"));
        compare(c, cross, new File(dir, "map_steps_vs_vector.png"));
        minimap(c, new File(dir, "minimap_concept.png"));
        raster(c, 0, 6, 4, new File(dir, "raster_check_level0.png"));
        raster(c, 2, 3, 2, new File(dir, "raster_check_level2.png"));
        System.out.println("  map concept pictures in " + dir);
    }

    static RoadNames.Route route(RoadDesign d) { return RoadNames.route(d.id); }

    static void reveal(Set<Long> explored, double[] p) {
        int r = (int) Math.ceil(REVEAL / 16) + 1, cx = Math.floorDiv((int) Math.floor(p[0]), 16), cz = Math.floorDiv((int) Math.floor(p[1]), 16);
        for (int i = -r; i <= r; i++)
            for (int j = -r; j <= r; j++) {
                double x = (cx + i) * 16 + 8, z = (cz + j) * 16 + 8;
                if (Math.hypot(x - p[0], z - p[1]) <= REVEAL) explored.add(key(cx + i, cz + j));
            }
    }

    static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    /** How much of (x, z) is known: the explored chunks bilinear between their middles (a soft 16 m edge). */
    static double known(Set<Long> explored, double x, double z) {
        double fx = (x - 8) / 16, fz = (z - 8) / 16;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        double a = explored.contains(key(ix, iz)) ? 1 : 0, b = explored.contains(key(ix + 1, iz)) ? 1 : 0;
        double c = explored.contains(key(ix, iz + 1)) ? 1 : 0, d = explored.contains(key(ix + 1, iz + 1)) ? 1 : 0;
        double v = (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
        return v * v * (3 - 2 * v);
    }

    // ------------------------------------------------------------------ ground and roads

    static int biome(Ctx c, double x, double z) { return EcologyBiomes.biomeAt(c.s, x, z); }

    static String biomeId(int b) { return b < EcologyBiomes.IDS.length ? EcologyBiomes.IDS[b] : FederalEcologyBiomes.IDS[b - EcologyBiomes.IDS.length]; }

    static int biomeColour(int b) { return b < EcologyBiomes.COLOURS.length ? EcologyBiomes.COLOURS[b] : FederalEcologyBiomes.COLOURS[b - EcologyBiomes.COLOURS.length]; }

    /** The ground's map colour: biome colour toward paper, NW hill shade, optional 10 m contours, water. */
    static int ground(Ctx c, double x, double z, double e, boolean contours, Map<Integer, Integer> count) {
        int w = c.terrain.water(x, z);
        if (w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA) return WATER;
        int b = biome(c, x, z);
        if (b == EcologyBiomes.SEA) return WATER;
        if (count != null) count.merge(b, 1, Integer::sum);
        int col = mix(biomeColour(b), PAPER, 0.18);
        double hw = c.terrain.height(x - e, z), he = c.terrain.height(x + e, z), hn = c.terrain.height(x, z - e), hs = c.terrain.height(x, z + e);
        double shade = Math.max(0.72, Math.min(1.18, 1 + 0.55 * ((he - hw) + (hs - hn)) / (2 * e)));
        col = scale(col, shade);
        if (contours && (Math.floor(hw / 10) != Math.floor(he / 10) || Math.floor(hn / 10) != Math.floor(hs / 10))) col = scale(col, 0.9);
        return col;
    }

    /** A town or road feature at (x, z): its colour, or -1 for the ground. */
    static int feature(Ctx c, double x, double z) {
        TownWorks.Spot sp = c.works.towns().spot(x, z);
        if (sp != null) {
            switch (sp.part()) {
                case ASPHALT: return STREET;
                case CURB: return ROAD_EDGE;
                case SIDEWALK: case DRIVEWAY: return WALK;
                default: break;
            }
        }
        RoadDesign[] near = c.works.roadsNear(x, z);
        if (near != null)
            for (RoadDesign d : near) {
                double[] l = d.local(x, z, d.section.edge + 2);
                if (l == null || l[0] < -1 || l[0] > d.length + 1) continue;
                double u = Math.abs(l[1]);
                boolean big = d.section == CrossSection.R4 || d.section == CrossSection.RAMP;
                if (d.section.paved(u)) return big ? HWY : ROAD;
                if (u > d.section.edge && u <= d.section.edge + 0.9) return big ? HWY_EDGE : ROAD_EDGE;
                if (d.section.halfMedian > 0.5 && u < d.section.halfMedian && u >= d.section.halfMedian - 0.7) return big ? HWY_EDGE : ROAD_EDGE;
            }
        return -1;
    }

    /** A detailed map tile: ground plus every town and road feature per pixel (ss x ss samples). */
    static BufferedImage detail(Ctx c, double x0, double z0, double mpp, int W, int H, int ss, Map<Integer, Integer> count) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < H; py++)
            for (int px = 0; px < W; px++) {
                double r = 0, g = 0, b = 0;
                int gcol = ground(c, x0 + (px + 0.5) * mpp, z0 + (py + 0.5) * mpp, Math.max(1, mpp), false, count);
                for (int i = 0; i < ss; i++)
                    for (int j = 0; j < ss; j++) {
                        int f = feature(c, x0 + (px + (i + 0.5) / ss) * mpp, z0 + (py + (j + 0.5) / ss) * mpp);
                        int col = f < 0 ? gcol : f;
                        r += (col >> 16) & 255; g += (col >> 8) & 255; b += col & 255;
                    }
                int n = ss * ss;
                img.setRGB(px, py, ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n));
            }
        return img;
    }

    /** Buildings (white-box sites) and the fuel stop's apron and icon over a map. */
    static void buildings(Graphics2D g, Ctx c, double x0, double z0, double mpp, boolean icons) {
        for (TownBuildings.Site st : c.sites) {
            Rectangle2D.Double r = new Rectangle2D.Double((st.x0() - x0) / mpp, (st.z0() - z0) / mpp, (st.x1() - st.x0() + 1) / mpp, (st.z1() - st.z0() + 1) / mpp);
            if (st.kind().startsWith("fuel_stop")) {
                g.setColor(new Color(APRON));
                g.fill(r);
                // the shop at the back, the canopy over the pumps (sketched from gas_station_01's layout)
                double w = r.width, h = r.height;
                g.setColor(new Color(BUILDING));
                Rectangle2D.Double shop = new Rectangle2D.Double(r.x + w * 0.52, r.y + h * 0.62, w * 0.42, h * 0.3);
                g.fill(shop);
                g.setColor(new Color(BUILDING_EDGE));
                g.setStroke(new BasicStroke((float) Math.max(0.6, 0.5 / mpp)));
                g.draw(shop);
                g.setColor(new Color(0xC2C7CC));
                Rectangle2D.Double canopy = new Rectangle2D.Double(r.x + w * 0.18, r.y + h * 0.22, w * 0.5, h * 0.26);
                g.fill(canopy);
                g.setColor(new Color(0x8F969D));
                g.draw(canopy);
                if (icons) fuelIcon(g, shop.getCenterX(), shop.getCenterY(), Math.max(9, Math.min(16, 6 / mpp)));
            } else {
                g.setColor(new Color(BUILDING));
                g.fill(r);
                g.setColor(new Color(BUILDING_EDGE));
                g.setStroke(new BasicStroke((float) Math.max(0.6, 0.5 / mpp)));
                g.draw(r);
            }
        }
    }

    /** Vector roads (world map zoom): casings first, then fills; town streets as lines. */
    static void vectorRoads(Graphics2D g, Ctx c, double x0, double z0, double mpp, int W, int H) {
        for (int pass = 0; pass < 2; pass++) {
            // town streets under the roads
            for (TownPlan.Street st : c.town.streets) {
                TownPlan.Node a = c.town.nodes.get(st.a()), b = c.town.nodes.get(st.b());
                double[] wa = c.town.world(a.s(), a.v()), wb = c.town.world(b.s(), b.v());
                float w = (float) Math.max(2.2, 9 / mpp);
                g.setStroke(new BasicStroke(pass == 0 ? w + 2 : w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(pass == 0 ? ROAD_EDGE : STREET));
                g.draw(new java.awt.geom.Line2D.Double((wa[0] - x0) / mpp, (wa[1] - z0) / mpp, (wb[0] - x0) / mpp, (wb[1] - z0) / mpp));
            }
            for (int big = 0; big < 2; big++)
                for (RoadDesign d : c.roads) {
                    boolean isBig = d.section == CrossSection.R4 || d.section == CrossSection.RAMP;
                    if (isBig != (big == 1)) continue;
                    float w = (float) Math.max(d.section == CrossSection.R4 ? 5 : isBig ? 3 : 3.2, 2 * d.section.edge / mpp);
                    g.setStroke(new BasicStroke(pass == 0 ? w + 2.4f : w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(new Color(pass == 0 ? (isBig ? HWY_EDGE : ROAD_EDGE) : (isBig ? HWY : ROAD)));
                    Path2D.Double path = new Path2D.Double();
                    boolean any = false;
                    for (double t = 0; t <= d.length + 1e-9; t += Math.min(6, Math.max(1, d.length / 4))) {
                        double[] p = d.point(Math.min(t, d.length), 0);
                        double qx = (p[0] - x0) / mpp, qy = (p[1] - z0) / mpp;
                        if (qx < -300 || qy < -300 || qx > W + 300 || qy > H + 300) { any = false; continue; }
                        if (!any) path.moveTo(qx, qy); else path.lineTo(qx, qy);
                        any = true;
                    }
                    g.draw(path);
                }
        }
    }

    // ------------------------------------------------------------------ 1: the world map screen

    static void worldMap(Ctx c, RoadDesign host, File out) throws Exception {
        int W = 1600, H = 1000;
        double mpp = 2.0, cx = c.player[0] - 520, cz = c.player[1] - 330;
        double x0 = cx - W / 2.0 * mpp, z0 = cz - H / 2.0 * mpp;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Map<Integer, Integer> count = new HashMap<>();
        for (int py = 0; py < H; py++)
            for (int px = 0; px < W; px++) {
                double x = x0 + (px + 0.5) * mpp, z = z0 + (py + 0.5) * mpp;
                img.setRGB(px, py, known(c.explored, x, z) > 0 ? ground(c, x, z, mpp, true, count) : FOG);
            }
        Graphics2D g = gfx(img);
        vectorRoads(g, c, x0, z0, mpp, W, H);
        buildings(g, c, x0, z0, mpp, false);
        g.dispose();
        fog(img, c, x0, z0, mpp);
        g = gfx(img);
        // labels over the known map: town, route shields, the fuel stop, a marker, the player
        double lx = 0, lz = 0;
        for (TownPlan.Lot l : c.town.lots) { double[] w = c.town.world((l.s0() + l.s1()) / 2, (l.v0() + l.v1()) / 2); lx += w[0]; lz += w[1]; }
        lx /= Math.max(1, c.town.lots.size()); lz /= Math.max(1, c.town.lots.size());
        townCard(g, RoadNames.town(c.town.town), "林登", null, (lx - x0) / mpp - 150, (lz - z0) / mpp + 120);
        label(g, RoadNames.town(c.town.town), (lx - x0) / mpp, (lz - z0) / mpp, cn(Font.BOLD, 26), new Color(0x2B2A27), new Color(255, 255, 255, 220));
        if (host != null) {
            double[] l = host.local(c.player[0], c.player[1], 3000);
            for (double t : new double[]{l[0] - 1500, l[0] - 450}) {
                double[] p = host.point(t, 0);
                shield(g, "F-40", (p[0] - x0) / mpp, (p[1] - z0) / mpp - 22);
            }
            double[] wp = host.point(l[0] - 1350, 30);
            marker(g, (wp[0] - x0) / mpp, (wp[1] - z0) / mpp, "标记 1");
            double[] hv = host.point(l[0] - 950, 60);
            tooltip(g, c, hv, (hv[0] - x0) / mpp, (hv[1] - z0) / mpp);
        }
        for (TownBuildings.Site st : c.sites)
            if (st.kind().startsWith("fuel_stop")) fuelIcon(g, (st.x1() - x0) / mpp + 6, (st.z1() - z0) / mpp + 4, 11);
        arrow(g, (c.player[0] - x0) / mpp, (c.player[1] - z0) / mpp, Math.toRadians(60), 13);
        // the screen's frame: top bar, legend, scale, keys
        topBar(g, c, W, "世界地图");
        legend(g, count, 24, 92);
        scaleBar(g, W - (int) Math.round(500 / mpp) - 110, H - 46, mpp, 500);
        compass(g, W - 60, 110);
        keys(g, W, H, new String[]{"M  关闭", "滚轮  缩放", "右键  放标记", "空格  回到自己"});
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    /** Unknown chunks fade to the fog colour with a faint 64 m grid. */
    static void fog(BufferedImage img, Ctx c, double x0, double z0, double mpp) {
        int W = img.getWidth(), H = img.getHeight();
        for (int py = 0; py < H; py++)
            for (int px = 0; px < W; px++) {
                double x = x0 + (px + 0.5) * mpp, z = z0 + (py + 0.5) * mpp;
                double k = known(c.explored, x, z);
                if (k >= 1) continue;
                int f = FOG;
                if (Math.floorMod((int) Math.floor(x), 64) < mpp || Math.floorMod((int) Math.floor(z), 64) < mpp) f = 0x262C33;
                img.setRGB(px, py, mix(f, img.getRGB(px, py) & 0xffffff, k));
            }
    }

    // ------------------------------------------------------------------ 2: a town zoomed in

    static void townMap(Ctx c, File out) throws Exception {
        int W = 1500, H = 940;
        double mpp = 0.55;
        double cx = c.player[0] - 230, cz = c.player[1] + 110;
        double x0 = cx - W / 2.0 * mpp, z0 = cz - H / 2.0 * mpp;
        BufferedImage img = detail(c, x0, z0, mpp, W, H, 1, null);
        Graphics2D g = gfx(img);
        buildings(g, c, x0, z0, mpp, true);
        streetNames(g, c, x0, z0, mpp);
        arrow(g, (c.player[0] - x0) / mpp, (c.player[1] - z0) / mpp, Math.toRadians(60), 15);
        g.dispose();
        fog(img, c, x0, z0, mpp);
        g = gfx(img);
        topBar(g, c, W, "世界地图");
        zoomSlider(g, W - 52, 120, 5, 5);
        scaleBar(g, W - (int) Math.round(100 / mpp) - 110, H - 46, mpp, 100);
        keys(g, W, H, new String[]{"M  关闭", "滚轮  缩放", "右键  放标记", "空格  回到自己"});
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    static void streetNames(Graphics2D g, Ctx c, double x0, double z0, double mpp) {
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        Set<String> named = new HashSet<>();
        TownPlan p = c.town;
        for (TownPlan.Street st : p.streets) {
            TownPlan.Node a = p.nodes.get(st.a()), b = p.nodes.get(st.b());
            String key = st.name() + (Math.abs(a.s() - b.s()) < 1 ? "@" + Math.round(a.s()) : "@" + Math.round(a.v()));
            double len = Math.hypot(a.s() - b.s(), a.v() - b.v());
            if (len < 60 || !named.add(key)) continue;
            double[] wa = p.world(a.s(), a.v()), wb = p.world(b.s(), b.v());
            double qx = ((wa[0] + wb[0]) / 2 - x0) / mpp, qy = ((wa[1] + wb[1]) / 2 - z0) / mpp;
            double ang = Math.atan2(wb[1] - wa[1], wb[0] - wa[0]);
            if (ang > Math.PI / 2) ang -= Math.PI; else if (ang < -Math.PI / 2) ang += Math.PI;
            AffineTransform old = g.getTransform();
            g.translate(qx, qy);
            g.rotate(ang);
            label(g, st.name(), 0, 0, g.getFont(), new Color(0x5B5650), new Color(255, 255, 255, 200));
            g.setTransform(old);
        }
    }

    // ------------------------------------------------------------------ 3: stepped blocks against vector roads

    static void compare(Ctx c, RoadDesign cross, File out) throws Exception {
        // a ramp of the exit interchange: curved, so the block map shows its steps
        RoadDesign ramp = null;
        double best = Double.POSITIVE_INFINITY;
        double[] at = cross == null ? c.player : cross.point(0, 0);
        for (RoadDesign d : c.roads) {
            if (d.section != CrossSection.RAMP) continue;
            double[] m = d.point(d.length / 2, 0);
            double dist = Math.hypot(m[0] - at[0], m[1] - at[1]);
            if (dist < best) { best = dist; ramp = d; }
        }
        double[] m = ramp == null ? at : ramp.point(ramp.length * 0.5, 0);
        int PW = 760, PH = 480, gap = 40, top = 90, W = PW * 2 + gap + 80, H = top + PH + 90;
        double mpp = 0.25, x0 = Math.floor(m[0] - PW / 2.0 * mpp), z0 = Math.floor(m[1] - PH / 2.0 * mpp);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(img);
        g.setPaint(new GradientPaint(0, 0, new Color(0x1A1F25), 0, H, new Color(0x11151A)));
        g.fillRect(0, 0, W, H);
        // left: one colour per block, the way a block-scanning map draws it
        BufferedImage steps = new BufferedImage(PW, PH, BufferedImage.TYPE_INT_RGB);
        int bw = (int) Math.ceil(PW * mpp), bh = (int) Math.ceil(PH * mpp);
        for (int bz = 0; bz < bh; bz++)
            for (int bx = 0; bx < bw; bx++) {
                double x = x0 + bx + 0.5, z = z0 + bz + 0.5;
                int f = feature(c, x, z), col = f < 0 ? ground(c, x, z, 1, false, null) : f;
                for (int j = 0; j < 4; j++)
                    for (int i = 0; i < 4; i++) {
                        int px = bx * 4 + i, py = bz * 4 + j;
                        if (px < PW && py < PH) steps.setRGB(px, py, col);
                    }
            }
        BufferedImage smooth = detail(c, x0, z0, mpp, PW, PH, 3, null);
        int lx = 40, rx = 40 + PW + gap;
        g.drawImage(steps, lx, top, null);
        g.drawImage(smooth, rx, top, null);
        g.setColor(PANEL_EDGE);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(lx, top, PW, PH, 6, 6));
        g.draw(new RoundRectangle2D.Double(rx, top, PW, PH, 6, 6));
        g.setFont(cn(Font.BOLD, 24));
        g.setColor(TEXT);
        g.drawString("现在：一格一个颜色（Xaero 的做法）", lx, 52);
        g.drawString("以后：按路网的曲线画", rx, 52);
        g.setFont(cn(Font.PLAIN, 17));
        g.setColor(DIM);
        g.drawString("斜着走的匝道变成台阶，路边一格一格", lx, top + PH + 36);
        g.drawString("客户端已经有整张路网（登录时收到），直接画平滑的路面和边线", rx, top + PH + 36);
        g.drawString(String.format(Locale.ROOT, "同一块地方：匝道 %s 附近，%d × %d m", ramp == null ? "-" : "F-40 出口", bw, bh), lx, top + PH + 64);
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    // ------------------------------------------------------------------ 4: the minimap

    static final double HEADING = Math.toRadians(60);

    static void minimap(Ctx c, File out) throws Exception {
        int W = 1600, H = 900, S = 320;
        double mpp = 0.75;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(img);
        g.setPaint(new GradientPaint(0, 0, new Color(0x1A1F25), 0, H, new Color(0x101317)));
        g.fillRect(0, 0, W, H);
        // the minimap turns with the view (user 2026-10-20): a bigger tile drawn turned by -heading, facing up
        int B = (int) Math.ceil(S * 1.45);
        double x0 = c.player[0] - B / 2.0 * mpp, z0 = c.player[1] - B / 2.0 * mpp;
        BufferedImage big = detail(c, x0, z0, mpp, B, B, 2, null);
        Graphics2D bg = gfx(big);
        buildings(bg, c, x0, z0, mpp, false);
        bg.dispose();
        BufferedImage tile = new BufferedImage(S, S, BufferedImage.TYPE_INT_RGB);
        Graphics2D tg = gfx(tile);
        tg.translate(S / 2.0, S / 2.0);
        tg.rotate(-HEADING);
        tg.drawImage(big, -B / 2, -B / 2, null);
        tg.rotate(HEADING);
        for (TownBuildings.Site st : c.sites)
            if (st.kind().startsWith("fuel_stop")) {
                // icons stay upright: the shop's middle turned into the view
                double dx = ((st.x0() + st.x1()) / 2.0 + 14 - c.player[0]) / mpp, dz = ((st.z0() + st.z1()) / 2.0 + 18 - c.player[1]) / mpp;
                double ca = Math.cos(-HEADING), sa = Math.sin(-HEADING);
                fuelIcon(tg, dx * ca - dz * sa, dx * sa + dz * ca, 11);
            }
        tg.dispose();
        // without the map: only a live circle of 64 m, nothing remembered
        BufferedImage live = new BufferedImage(S, S, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                double d = Math.hypot(x - S / 2.0, y - S / 2.0) * mpp;
                double k = Math.max(0, Math.min(1, (64 - d) / 18));
                live.setRGB(x, y, mix(FOG, tile.getRGB(x, y) & 0xffffff, k));
            }
        g.setFont(cn(Font.BOLD, 30));
        g.setColor(TEXT);
        g.drawString("小地图", 60, 74);
        g.setFont(cn(Font.PLAIN, 18));
        g.setColor(DIM);
        g.drawString("屏幕右上角，跟着视角转（前方朝上），N 指着北；下面一行是坐标和所在群系", 60, 108);
        int ax = 120, bx = 120 + S + 170, my = 170;
        minimapFrame(g, c, tile, ax, my, S, true);
        minimapFrame(g, c, live, bx, my, S, false);
        g.setFont(cn(Font.BOLD, 21));
        g.setColor(TEXT);
        g.drawString("带着地图", ax, my + S + 96);
        g.drawString("没带地图", bx, my + S + 96);
        g.setFont(cn(Font.PLAIN, 17));
        g.setColor(DIM);
        g.drawString("走过的地方记下来，小地图和世界地图都看得到", ax, my + S + 126);
        g.drawString("只显示身边 64 m 实时的一圈，什么都不记，打不开世界地图", bx, my + S + 126);
        // where it sits on the screen
        int sx = 1080, sy = 170, sw = 440, sh = 248;
        g.setPaint(new GradientPaint(sx, sy, new Color(0x5E7C8F), sx, sy + sh, new Color(0x3F5A3A)));
        g.fill(new RoundRectangle2D.Double(sx, sy, sw, sh, 8, 8));
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(sx + sw * 0.31, sy + sh - 26, sw * 0.38, 16, 4, 4));   // hotbar
        g.fill(new RoundRectangle2D.Double(sx + 10, sy + sh - 52, sw * 0.22, 42, 4, 4));          // survival HUD
        g.drawImage(tile.getScaledInstance(64, 64, java.awt.Image.SCALE_SMOOTH), sx + sw - 74, sy + 10, null);
        g.setColor(PANEL_EDGE);
        g.draw(new RoundRectangle2D.Double(sx + sw - 74, sy + 10, 64, 64, 6, 6));
        g.setColor(new Color(0, 0, 0, 120));
        g.fill(new RoundRectangle2D.Double(sx + sw - 74, sy + 77, 64, 9, 3, 3));
        g.setFont(cn(Font.PLAIN, 16));
        g.setColor(DIM);
        g.drawString("在屏幕上的位置（右上角，左下是生存 HUD）", sx, sy + sh + 32);
        g.drawString("大小、透明度在设置里调，也可以改成北朝上", sx, sy + sh + 58);
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    static void minimapFrame(Graphics2D g, Ctx c, BufferedImage tile, int x, int y, int S, boolean full) {
        Shape clip = new RoundRectangle2D.Double(x, y, S, S, 22, 22);
        g.setColor(new Color(0, 0, 0, 120));
        g.fill(new RoundRectangle2D.Double(x - 6, y - 6, S + 12, S + 12, 28, 28));
        Shape old = g.getClip();
        g.setClip(clip);
        g.drawImage(tile, x, y, null);
        g.setClip(old);
        g.setColor(new Color(255, 255, 255, 70));
        g.setStroke(new BasicStroke(2f));
        g.draw(clip);
        // N on the frame
        // N on the frame where north is, the view turned by -heading
        double nx = Math.sin(-HEADING), ny = -Math.cos(-HEADING), k = (S / 2.0) / Math.max(Math.abs(nx), Math.abs(ny));
        double ncx = x + S / 2.0 + nx * k, ncy = y + S / 2.0 + ny * k;
        g.setColor(ACCENT);
        g.fill(new Ellipse2D.Double(ncx - 13, ncy - 13, 26, 26));
        g.setColor(new Color(0x15181C));
        g.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g.drawString("N", (float) ncx - 5, (float) ncy + 6);
        arrow(g, x + S / 2.0, y + S / 2.0, 0, 14);
        // the line under it: coordinates and biome
        int b = biome(c, c.player[0], c.player[1]);
        g.setColor(PANEL);
        g.fill(new RoundRectangle2D.Double(x, y + S + 12, S, 38, 12, 12));
        g.setColor(TEXT);
        g.setFont(new Font("Segoe UI", Font.BOLD, 16));
        String xyz = String.format(Locale.ROOT, "%d  %d  %d", (int) Math.floor(c.player[0]), 122, (int) Math.floor(c.player[1]));
        g.drawString(xyz, x + 14, y + S + 37);
        int tw = g.getFontMetrics().stringWidth(xyz);
        g.setColor(new Color(biomeColour(b)));
        g.fill(new RoundRectangle2D.Double(x + 26 + tw, y + S + 23, 14, 14, 4, 4));
        g.setColor(TEXT);
        g.setFont(cn(Font.PLAIN, 16));
        g.drawString(ZH.getOrDefault(biomeId(b), biomeId(b)), x + 48 + tw, y + S + 37);
        if (!full) {
            g.setColor(DIM);
            g.setFont(cn(Font.PLAIN, 14));
            g.drawString("没有地图", x + S - 76, y + S - 14);
        }
    }

    // ------------------------------------------------------------------ the game's own raster (MapRaster) on the plan

    /**
     * The tiles the game draws (com.antaurora.apofirstlight.map.MapRaster), from a ground recorded off the terrain plan
     * over the explored chunks (what the client would have sampled walking there), stitched over the fog: a check of the
     * raster before it is seen in game, and how long a tile takes.
     */
    static void raster(Ctx c, int level, int nx, int nz, File out) throws Exception {
        java.util.Map<Long, int[]> chunks = new java.util.concurrent.ConcurrentHashMap<>();
        com.antaurora.apofirstlight.map.MapGround ground = (cx, cz) -> {
            if (!c.explored.contains(key(cx, cz))) return null;
            return chunks.computeIfAbsent(key(cx, cz), q -> {
                int[] a = new int[64];
                for (int j = 0; j < 8; j++)
                    for (int i = 0; i < 8; i++) {
                        double x = cx * 16 + i * 2 + 1, z = cz * 16 + j * 2 + 1;
                        int w = c.terrain.water(x, z);
                        boolean water = w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA;
                        double h = water ? c.terrain.waterSurface(x, z) : c.terrain.height(x, z);
                        a[j * 8 + i] = com.antaurora.apofirstlight.map.MapGround.pack((int) Math.floor(h), biome(c, x, z), water);
                    }
                return a;
            });
        };
        com.antaurora.apofirstlight.map.MapTowns towns = com.antaurora.apofirstlight.map.MapTowns.of(c.works, null);
        int T = com.antaurora.apofirstlight.map.MapRaster.TILE;
        double span = com.antaurora.apofirstlight.map.MapRaster.span(level);
        int tx0 = (int) Math.floor(c.player[0] / span) - nx / 2, tz0 = (int) Math.floor(c.player[1] / span) - nz / 2;
        BufferedImage img = new BufferedImage(nx * T, nz * T, BufferedImage.TYPE_INT_RGB);
        int[] px = new int[T * T];
        long worst = 0, total = 0;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                long t0 = System.nanoTime();
                com.antaurora.apofirstlight.map.MapRaster.tile(ground, c.works, towns, level, tx0 + i, tz0 + j, px);
                long dt = System.nanoTime() - t0;
                worst = Math.max(worst, dt);
                total += dt;
                for (int y = 0; y < T; y++)
                    for (int x = 0; x < T; x++) {
                        int a = px[y * T + x];
                        double wx = (tx0 + i) * span + (x + 0.5) * span / T, wz = (tz0 + j) * span + (y + 0.5) * span / T;
                        int fog = Math.floorMod((int) Math.floor(wx), 64) < span / T || Math.floorMod((int) Math.floor(wz), 64) < span / T
                                ? com.antaurora.apofirstlight.map.MapStyle.FOG_GRID : com.antaurora.apofirstlight.map.MapStyle.FOG;
                        img.setRGB(i * T + x, j * T + y, mix(fog, a & 0xFFFFFF, (a >>> 24) / 255.0));
                    }
            }
        Graphics2D g = gfx(img);
        double mpp = com.antaurora.apofirstlight.map.MapRaster.mpp(level);
        arrow(g, (c.player[0] - tx0 * span) / mpp, (c.player[1] - tz0 * span) / mpp, Math.toRadians(60), 13);
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.printf(Locale.ROOT, "  map raster level %d: %d tiles, %.0f ms each, worst %.0f ms%n", level, nx * nz,
                total / 1e6 / (nx * nz), worst / 1e6);
    }

    // ------------------------------------------------------------------ UI pieces

    static Graphics2D gfx(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g;
    }

    static void panel(Graphics2D g, double x, double y, double w, double h) {
        RoundRectangle2D.Double r = new RoundRectangle2D.Double(x, y, w, h, 14, 14);
        g.setColor(PANEL);
        g.fill(r);
        g.setColor(PANEL_EDGE);
        g.setStroke(new BasicStroke(1f));
        g.draw(r);
    }

    static void topBar(Graphics2D g, Ctx c, int W, String title) {
        panel(g, 24, 20, W - 48, 50);
        g.setColor(TEXT);
        g.setFont(cn(Font.BOLD, 20));
        g.drawString(title, 44, 53);
        int b = biome(c, c.player[0], c.player[1]);
        g.setFont(new Font("Segoe UI", Font.BOLD, 18));
        String xyz = String.format(Locale.ROOT, "X %d   Y %d   Z %d", (int) Math.floor(c.player[0]), 122, (int) Math.floor(c.player[1]));
        int x = W / 2 - 220;
        g.drawString(xyz, x, 52);
        x += g.getFontMetrics().stringWidth(xyz) + 26;
        g.setColor(new Color(biomeColour(b)));
        g.fill(new RoundRectangle2D.Double(x, 36, 16, 16, 4, 4));
        g.setColor(TEXT);
        g.setFont(cn(Font.PLAIN, 18));
        g.drawString(ZH.getOrDefault(biomeId(b), biomeId(b)), x + 24, 52);
        g.setColor(DIM);
        g.setFont(cn(Font.PLAIN, 16));
        String where = RoadNames.town(c.town.town) + " 镇内";
        g.drawString(where, W - 44 - g.getFontMetrics().stringWidth(where), 52);
    }

    static void legend(Graphics2D g, Map<Integer, Integer> count, int x, int y) {
        List<Map.Entry<Integer, Integer>> es = new ArrayList<>(count.entrySet());
        es.sort((a, b) -> b.getValue() - a.getValue());
        int rows = Math.min(7, es.size());
        int h = 54 + rows * 28 + 40 + 4 * 28 - 10;
        panel(g, x, y, 250, h);
        g.setColor(DIM);
        g.setFont(cn(Font.BOLD, 15));
        g.drawString("群系", x + 18, y + 30);
        int yy = y + 54;
        g.setFont(cn(Font.PLAIN, 16));
        for (int i = 0; i < rows; i++) {
            int b = es.get(i).getKey();
            g.setColor(new Color(mix(biomeColour(b), PAPER, 0.18)));
            g.fill(new RoundRectangle2D.Double(x + 18, yy - 14, 18, 18, 4, 4));
            g.setColor(TEXT);
            g.drawString(ZH.getOrDefault(biomeId(b), biomeId(b)), x + 46, yy);
            yy += 28;
        }
        yy += 18;
        g.setColor(DIM);
        g.setFont(cn(Font.BOLD, 15));
        g.drawString("道路和建筑", x + 18, yy - 6);
        yy += 22;
        g.setFont(cn(Font.PLAIN, 16));
        int[][] sw = {{HWY, HWY_EDGE}, {ROAD, ROAD_EDGE}, {BUILDING, BUILDING_EDGE}, {-1, -1}};
        String[] nm = {"高速公路和匝道", "公路、镇里的街", "建筑", "加油站"};
        for (int i = 0; i < 4; i++) {
            if (sw[i][0] < 0) fuelIcon(g, x + 27, yy - 5, 10);
            else {
                g.setColor(new Color(sw[i][0]));
                g.fill(new RoundRectangle2D.Double(x + 18, yy - 12, 18, 14, 3, 3));
                g.setColor(new Color(sw[i][1]));
                g.draw(new RoundRectangle2D.Double(x + 18, yy - 12, 18, 14, 3, 3));
            }
            g.setColor(TEXT);
            g.drawString(nm[i], x + 46, yy);
            yy += 28;
        }
    }

    static void keys(Graphics2D g, int W, int H, String[] keys) {
        g.setFont(cn(Font.PLAIN, 15));
        int w = 30;
        for (String k : keys) w += g.getFontMetrics().stringWidth(k) + 30;
        panel(g, 24, H - 66, w, 42);
        int x = 44;
        for (String k : keys) {
            g.setColor(TEXT);
            g.drawString(k, x, H - 39);
            x += g.getFontMetrics().stringWidth(k) + 30;
        }
    }

    static void scaleBar(Graphics2D g, int x, int y, double mpp, int metres) {
        int len = (int) Math.round(metres / mpp);
        panel(g, x - 16, y - 30, len + 90, 46);
        g.setColor(TEXT);
        g.setStroke(new BasicStroke(3f));
        g.drawLine(x, y, x + len, y);
        g.drawLine(x, y - 6, x, y + 1);
        g.drawLine(x + len, y - 6, x + len, y + 1);
        g.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g.drawString(metres + " m", x + len + 12, y + 5);
    }

    static void compass(Graphics2D g, int x, int y) {
        g.setColor(PANEL);
        g.fill(new Ellipse2D.Double(x - 24, y - 24, 48, 48));
        Path2D.Double n = new Path2D.Double();
        n.moveTo(x, y - 17); n.lineTo(x + 7, y + 2); n.lineTo(x - 7, y + 2); n.closePath();
        g.setColor(ACCENT);
        g.fill(n);
        g.setColor(TEXT);
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g.drawString("N", x - 4, y + 17);
    }

    static void zoomSlider(Graphics2D g, int x, int y, int steps, int at) {
        panel(g, x - 18, y - 18, 36, steps * 34 + 18);
        for (int i = 0; i < steps; i++) {
            int yy = y + i * 34;
            boolean on = steps - i <= at && steps - i == at;
            g.setColor(on ? ACCENT : new Color(255, 255, 255, 80));
            g.fill(new Ellipse2D.Double(x - (on ? 7 : 4), yy + 8 - (on ? 7 : 4), on ? 14 : 8, on ? 14 : 8));
        }
    }

    static void label(Graphics2D g, String s, double cx, double cy, Font f, Color fill, Color halo) {
        if (s == null) return;
        TextLayout tl = new TextLayout(s, f, g.getFontRenderContext());
        Rectangle2D b = tl.getBounds();
        Shape sh = tl.getOutline(AffineTransform.getTranslateInstance(cx - b.getWidth() / 2 - b.getX(), cy + b.getHeight() / 2));
        g.setColor(halo);
        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(sh);
        g.setColor(fill);
        g.fill(sh);
    }

    static void shield(Graphics2D g, String text, double cx, double cy) {
        g.setFont(new Font("Segoe UI", Font.BOLD, 14));
        int tw = g.getFontMetrics().stringWidth(text);
        double w = tw + 16, h = 24;
        Path2D.Double p = new Path2D.Double();
        p.moveTo(cx - w / 2, cy - h / 2);
        p.lineTo(cx + w / 2, cy - h / 2);
        p.lineTo(cx + w / 2, cy + h / 4);
        p.quadTo(cx + w / 2, cy + h / 2, cx, cy + h / 2 + 5);
        p.quadTo(cx - w / 2, cy + h / 2, cx - w / 2, cy + h / 4);
        p.closePath();
        g.setColor(new Color(0x1F4E8C));
        g.fill(p);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.draw(p);
        g.drawString(text, (float) (cx - tw / 2.0), (float) (cy + 5));
    }

    static void fuelIcon(Graphics2D g, double cx, double cy, double r) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Double(cx - r - 1, cy - r + 1, 2 * r + 2, 2 * r + 2));
        g.setColor(ACCENT);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke((float) Math.max(1.2, r / 7)));
        double u = r / 10;
        g.fill(new RoundRectangle2D.Double(cx - 5 * u, cy - 5 * u, 6 * u, 10 * u, 2 * u, 2 * u));
        g.setColor(ACCENT);
        g.fill(new Rectangle2D.Double(cx - 4 * u, cy - 4 * u, 4 * u, 3 * u));
        g.setColor(Color.WHITE);
        Path2D.Double hose = new Path2D.Double();
        hose.moveTo(cx + 1 * u, cy - 3 * u);
        hose.lineTo(cx + 4 * u, cy - 1 * u);
        hose.lineTo(cx + 4 * u, cy + 4 * u);
        g.draw(hose);
    }

    static void marker(Graphics2D g, double x, double y, String name) {
        Path2D.Double pin = new Path2D.Double();
        pin.moveTo(x, y);
        pin.curveTo(x - 4, y - 8, x - 11, y - 13, x - 11, y - 21);
        pin.curveTo(x - 11, y - 33, x + 11, y - 33, x + 11, y - 21);
        pin.curveTo(x + 11, y - 13, x + 4, y - 8, x, y);
        g.setColor(new Color(0x2F8FDB));
        g.fill(pin);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.draw(pin);
        g.fill(new Ellipse2D.Double(x - 4, y - 25, 8, 8));
        label(g, name, x, y - 46, cn(Font.BOLD, 15), new Color(0x1E2A36), new Color(255, 255, 255, 220));
    }

    static void arrow(Graphics2D g, double x, double y, double heading, double r) {
        Path2D.Double a = new Path2D.Double();
        a.moveTo(0, -r);
        a.lineTo(r * 0.72, r * 0.8);
        a.lineTo(0, r * 0.38);
        a.lineTo(-r * 0.72, r * 0.8);
        a.closePath();
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(heading);
        g.setColor(new Color(0, 0, 0, 110));
        g.fill(AffineTransform.getTranslateInstance(1, 2).createTransformedShape(a));
        g.setColor(Color.WHITE);
        g.fill(a);
        g.setColor(new Color(0x15181C));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(a);
        g.setTransform(old);
    }

    /** The card over a town under the mouse: its English name as on the signs, the game language's name under it
     *  (left out when it is the same, as in English), the kind of place. */
    static void townCard(Graphics2D g, String name, String local, String kind, double x, double y) {
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.6f));
        g.drawLine((int) x - 9, (int) y, (int) x + 9, (int) y);
        g.drawLine((int) x, (int) y - 9, (int) x, (int) y + 9);
        panel(g, x + 16, y + 12, 130, 74);
        g.setColor(TEXT);
        g.setFont(new Font("Segoe UI", Font.BOLD, 19));
        g.drawString(name, (float) x + 30, (float) y + 40);
        g.setColor(DIM);
        g.setFont(cn(Font.PLAIN, 15));
        g.drawString(local, (float) x + 30, (float) y + 68);
    }

    static void tooltip(Graphics2D g, Ctx c, double[] w, double x, double y) {
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.6f));
        g.drawLine((int) x - 9, (int) y, (int) x + 9, (int) y);
        g.drawLine((int) x, (int) y - 9, (int) x, (int) y + 9);
        int b = biome(c, w[0], w[1]);
        String a = String.format(Locale.ROOT, "X %d   Z %d", (int) Math.floor(w[0]), (int) Math.floor(w[1]));
        String n = ZH.getOrDefault(biomeId(b), biomeId(b));
        panel(g, x + 16, y + 12, 190, 62);
        g.setColor(TEXT);
        g.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g.drawString(a, (float) x + 30, (float) y + 36);
        g.setColor(new Color(biomeColour(b)));
        g.fill(new RoundRectangle2D.Double(x + 30, y + 47, 13, 13, 3, 3));
        g.setColor(TEXT);
        g.setFont(cn(Font.PLAIN, 15));
        g.drawString(n, (float) x + 50, (float) y + 59);
    }

    // ------------------------------------------------------------------ colour

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int scale(int c, double k) {
        int r = (int) Math.min(255, ((c >> 16) & 255) * k), g = (int) Math.min(255, ((c >> 8) & 255) * k), b = (int) Math.min(255, (c & 255) * k);
        return (r << 16) | (g << 8) | b;
    }
}
