package com.antaurora.apofirstlight.map;

import com.antaurora.apofirstlight.worldgen.network.road.CrossSection;
import com.antaurora.apofirstlight.worldgen.network.road.Driveway;
import com.antaurora.apofirstlight.worldgen.network.road.RoadCurve;
import com.antaurora.apofirstlight.worldgen.network.road.RoadDesign;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import com.antaurora.apofirstlight.worldgen.network.town.TownBuildings;
import com.antaurora.apofirstlight.worldgen.network.town.TownWorks;

/**
 * One map tile (AFL Map V1, docs/ui/afl_map_v1_plan.md 4): TILE x TILE pixels of a zoom level, ARGB, alpha = how much
 * of it is known. The ground is the recorded cells (biome colour toward paper, blended between cells, a north-west hill
 * shade, contours when zoomed out, water); the roads, the towns' streets and sidewalks and the buildings come from the
 * road network the client already has, per pixel along their real curves, so nothing is stepped by the block grid.
 * Known fades over a chunk between the middles of seen and unseen chunks (a soft 16 m edge). Pure: no game state, safe on
 * a worker (the road network's lookups are the worldgen's, which run on the chunk workers).
 */
public final class MapRaster {
    private MapRaster() {}

    public static final int TILE = 256, LEVELS = 5;

    /** Metres per pixel of a level: 0.5, 1, 2, 4, 8. */
    public static double mpp(int level) { return 0.5 * (1 << level); }

    /** Metres along a tile's side at a level. */
    public static double span(int level) { return TILE * mpp(level); }

    /** The tile at (tx, tz) of a level into out (TILE * TILE, row by row, north up). */
    public static void tile(MapGround ground, RoadWorks works, MapTowns towns, int level, int tx, int tz, int[] out) {
        double mpp = mpp(level), x0 = tx * span(level), z0 = tz * span(level);
        Window w = new Window(ground, x0, z0, span(level));
        RoadCurve.Scratch k = new RoadCurve.Scratch();
        int ss = level <= 1 ? 2 : 1;
        double e = Math.max(MapGround.CELL, mpp);
        double contour = mpp >= 8 ? 20 : mpp >= 2 ? 10 : 0;
        for (int py = 0; py < TILE; py++)
            for (int px = 0; px < TILE; px++) {
                double x = x0 + (px + 0.5) * mpp, z = z0 + (py + 0.5) * mpp;
                double known = w.known(x, z);
                if (known <= 0) { out[py * TILE + px] = 0; continue; }
                int col = w.colour(x, z, mpp);
                if (col != MapStyle.WATER) {
                    double hw = w.height(x - e, z), he = w.height(x + e, z), hn = w.height(x, z - e), hs = w.height(x, z + e);
                    double shade = Math.max(0.72, Math.min(1.18, 1 + 0.55 * ((he - hw) + (hs - hn)) / (2 * e)));
                    col = MapStyle.scale(col, shade);
                    if (contour > 0) {
                        double c0 = w.height(x - mpp / 2, z), c1 = w.height(x + mpp / 2, z), c2 = w.height(x, z - mpp / 2), c3 = w.height(x, z + mpp / 2);
                        if (Math.floor(c0 / contour) != Math.floor(c1 / contour) || Math.floor(c2 / contour) != Math.floor(c3 / contour))
                            col = MapStyle.scale(col, 0.9);
                    }
                }
                int r = 0, g = 0, b = 0;
                for (int i = 0; i < ss; i++)
                    for (int j = 0; j < ss; j++) {
                        int f = feature(works, towns, x0 + (px + (i + 0.5) / ss) * mpp, z0 + (py + (j + 0.5) / ss) * mpp, mpp, k);
                        int c = f < 0 ? col : f;
                        r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
                    }
                int n = ss * ss;
                out[py * TILE + px] = (int) Math.round(known * 255) << 24 | (r / n) << 16 | (g / n) << 8 | (b / n);
            }
        buildings(towns, x0, z0, mpp, out);
    }

    /** A road or street at (x, z): its colour, or -1 for the ground. Roads get a pixel or so of width when zoomed out. */
    static int feature(RoadWorks works, MapTowns towns, double x, double z, double mpp, RoadCurve.Scratch k) {
        if (works == null) return -1;
        if (mpp <= 1) {
            TownWorks.Spot sp = works.towns().spot(x, z);
            if (sp != null) {
                switch (sp.part()) {
                    case ASPHALT: return MapStyle.STREET;
                    case CURB: return MapStyle.CURB;
                    case SIDEWALK: case DRIVEWAY: return MapStyle.WALK;
                    default: break;
                }
            }
        } else {
            for (MapTowns.Town t : towns.towns) {
                if (x < t.x0() || x > t.x1() || z < t.z0() || z > t.z1()) continue;
                for (MapTowns.Street st : t.streets()) {
                    double half = Math.max(st.asphalt() / 2, mpp * 0.6), d = segment(x, z, st);
                    if (d <= half) return MapStyle.STREET;
                    if (d <= half + mpp * 0.7) return MapStyle.ROAD_EDGE;
                }
            }
        }
        RoadDesign[] near = works.roadsNear(x, z);
        int found = -1;
        if (near != null)
        for (RoadDesign d : near) {
            CrossSection sec = d.section;
            boolean big = sec == CrossSection.R4 || sec == CrossSection.RAMP;
            double edge = Math.max(sec.edge, mpp * (big ? 1.2 : 0.8)), casing = Math.max(0.9, mpp * 0.8);
            if (!d.curve.local(x, z, edge + casing, RoadDesign.END, k)) continue;
            if (k.s < -1 || k.s > d.length + 1) continue;
            double u = Math.abs(k.u);
            boolean wide = edge > sec.edge;
            if (wide ? u <= edge : sec.paved(u)) return big ? MapStyle.HWY : MapStyle.ROAD;
            if (u > edge && u <= edge + casing) found = big ? MapStyle.HWY_EDGE : MapStyle.ROAD_EDGE;
            else if (!wide && sec.halfMedian > 0.5 && u < sec.halfMedian && u >= sec.halfMedian - 0.7) found = big ? MapStyle.HWY_EDGE : MapStyle.ROAD_EDGE;
        }
        // the forest home's gravel driveway (County road G3): under the county road where they meet
        Driveway dr = works.drive();
        if (dr != null && !dr.isEmpty() && dr.touches(Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16))) {
            double[] n = dr.near(x, z);
            if (n != null) {
                double half = Math.max(n[2], mpp * 0.6);
                if (n[0] <= half) return MapStyle.DRIVE;
                if (found < 0 && n[0] <= half + Math.max(0.6, mpp * 0.7)) found = MapStyle.DRIVE_EDGE;
            }
        }
        return found;
    }

    static double segment(double x, double z, MapTowns.Street s) {
        double dx = s.bx() - s.ax(), dz = s.bz() - s.az(), l2 = dx * dx + dz * dz;
        double t = l2 <= 0 ? 0 : Math.max(0, Math.min(1, ((x - s.ax()) * dx + (z - s.az()) * dz) / l2));
        return Math.hypot(x - s.ax() - t * dx, z - s.az() - t * dz);
    }

    /**
     * The building sites over the tile, by coverage: fill, an outline when zoomed in; Fuel Stop A1 as its paved apron. Then
     * the buildings outside the towns (the forest home's roofs).
     */
    static void buildings(MapTowns towns, double x0, double z0, double mpp, int[] out) {
        double x1 = x0 + TILE * mpp, z1 = z0 + TILE * mpp;
        for (double[] b : towns.buildings)
            if (b[2] >= x0 && b[0] <= x1 && b[3] >= z0 && b[1] <= z1) box(b[0], b[1], b[2], b[3], MapStyle.BUILDING, mpp > 2 ? 0 : Math.max(0.35, mpp * 0.6), x0, z0, mpp, out);
        for (MapTowns.Town t : towns.towns) {
            if (t.x1() < x0 || t.x0() > x1 || t.z1() < z0 || t.z0() > z1) continue;
            for (TownBuildings.Site s : t.sites()) {
                double sx0 = s.x0(), sx1 = s.x1() + 1, sz0 = s.z0(), sz1 = s.z1() + 1;
                if (sx1 < x0 || sx0 > x1 || sz1 < z0 || sz0 > z1) continue;
                boolean apron = TownBuildings.FUEL_STOP.equals(s.kind());
                box(sx0, sz0, sx1, sz1, apron ? MapStyle.APRON : MapStyle.BUILDING, apron || mpp > 2 ? 0 : Math.max(0.35, mpp * 0.6), x0, z0, mpp, out);
            }
        }
    }

    /** One box (world sx0..sx1, sz0..sz1) by coverage: the fill, an outline line metres wide (0: none). */
    static void box(double sx0, double sz0, double sx1, double sz1, int fill, double line, double x0, double z0, double mpp, int[] out) {
        int px0 = Math.max(0, (int) Math.floor((sx0 - x0) / mpp)), px1 = Math.min(TILE - 1, (int) Math.floor((sx1 - x0) / mpp));
        int py0 = Math.max(0, (int) Math.floor((sz0 - z0) / mpp)), py1 = Math.min(TILE - 1, (int) Math.floor((sz1 - z0) / mpp));
        for (int py = py0; py <= py1; py++)
            for (int px = px0; px <= px1; px++) {
                double ax = x0 + px * mpp, az = z0 + py * mpp;
                double cov = overlap(ax, ax + mpp, sx0, sx1) * overlap(az, az + mpp, sz0, sz1) / (mpp * mpp);
                if (cov <= 0) continue;
                double in = line > 0 ? overlap(ax, ax + mpp, sx0 + line, sx1 - line) * overlap(az, az + mpp, sz0 + line, sz1 - line) / (mpp * mpp) : cov;
                int i = py * TILE + px, under = out[i];
                out[i] = (under & 0xFF000000) | blend(under, 1 - cov, MapStyle.BUILDING_EDGE, cov - in, fill, in);
            }
    }

    static int blend(int a, double wa, int b, double wb, int c, double wc) {
        int r = (int) Math.round(((a >> 16) & 255) * wa + ((b >> 16) & 255) * wb + ((c >> 16) & 255) * wc);
        int g = (int) Math.round(((a >> 8) & 255) * wa + ((b >> 8) & 255) * wb + ((c >> 8) & 255) * wc);
        int bl = (int) Math.round((a & 255) * wa + (b & 255) * wb + (c & 255) * wc);
        return Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, bl);
    }

    static double overlap(double a0, double a1, double b0, double b1) { return Math.max(0, Math.min(a1, b1) - Math.max(a0, b0)); }

    /** The recorded cells over a tile and a margin, copied once from the chunks, and their chunks' known flags. */
    static final class Window {
        final int gx0, gz0, n, cx0, cz0, cn;
        /** The recorded cells, grown MapGround.CELLS cells out into unseen ground (the soft edge needs a colour there). */
        final int[] cells;
        final boolean[] seen;
        /** Heights averaged over 3 x 3 cells (block tops are whole metres: unsmoothed, the hill shade draws their steps). */
        final float[] smooth;
        /** The seen flags averaged over 3 x 3 chunks: the known edge rounds off instead of following the chunk grid. */
        final float[] seenBlur;

        Window(MapGround g, double x0, double z0, double span) {
            int margin = 24;
            cx0 = Math.floorDiv((int) Math.floor(x0) - margin, 16);
            cz0 = Math.floorDiv((int) Math.floor(z0) - margin, 16);
            int cx1 = Math.floorDiv((int) Math.ceil(x0 + span) + margin, 16), cz1 = Math.floorDiv((int) Math.ceil(z0 + span) + margin, 16);
            cn = Math.max(cx1 - cx0, cz1 - cz0) + 1;
            n = cn * MapGround.CELLS;
            gx0 = cx0 * MapGround.CELLS;
            gz0 = cz0 * MapGround.CELLS;
            cells = new int[n * n];
            seen = new boolean[cn * cn];
            for (int j = 0; j < cn; j++)
                for (int i = 0; i < cn; i++) {
                    int[] c = g.chunk(cx0 + i, cz0 + j);
                    if (c == null) continue;
                    seen[j * cn + i] = true;
                    for (int b = 0; b < MapGround.CELLS; b++)
                        System.arraycopy(c, b * MapGround.CELLS, cells, (j * MapGround.CELLS + b) * n + i * MapGround.CELLS, MapGround.CELLS);
                }
            // the known edge fades half a chunk into unseen ground: give that ground the nearest known cell's height and colour
            for (int pass = 0; pass < MapGround.CELLS; pass++) {
                int[] prev = cells.clone();
                boolean grew = false;
                for (int j = 0; j < n; j++)
                    for (int i = 0; i < n; i++) {
                        if (prev[j * n + i] != 0) continue;
                        int v = i > 0 ? prev[j * n + i - 1] : 0;
                        if (v == 0 && i + 1 < n) v = prev[j * n + i + 1];
                        if (v == 0 && j > 0) v = prev[(j - 1) * n + i];
                        if (v == 0 && j + 1 < n) v = prev[(j + 1) * n + i];
                        if (v != 0) { cells[j * n + i] = v; grew = true; }
                    }
                if (!grew) break;
            }
            seenBlur = new float[cn * cn];
            for (int j = 0; j < cn; j++)
                for (int i = 0; i < cn; i++) {
                    float sum = 0;
                    int cnt = 0;
                    for (int dj = -1; dj <= 1; dj++)
                        for (int di = -1; di <= 1; di++) {
                            int a = i + di, b = j + dj;
                            if (a < 0 || b < 0 || a >= cn || b >= cn) continue;
                            cnt++;
                            if (seen[b * cn + a]) sum += (di == 0 && dj == 0) ? 2 : 1;
                        }
                    seenBlur[j * cn + i] = sum / (cnt + 1);
                }
            smooth = new float[n * n];
            float[] row = new float[n * n];
            int[] rowN = new int[n * n];
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    float sum = 0;
                    int cnt = 0;
                    for (int d = -1; d <= 1; d++) {
                        int a = i + d;
                        if (a < 0 || a >= n) continue;
                        int sv = cells[j * n + a];
                        if (sv == 0) continue;
                        sum += MapGround.y(sv);
                        cnt++;
                    }
                    row[j * n + i] = sum;
                    rowN[j * n + i] = cnt;
                }
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    float sum = 0;
                    int cnt = 0;
                    for (int d = -1; d <= 1; d++) {
                        int b = j + d;
                        if (b < 0 || b >= n) continue;
                        sum += row[b * n + i];
                        cnt += rowN[b * n + i];
                    }
                    smooth[j * n + i] = cnt == 0 ? 0 : sum / cnt;
                }
        }

        float blur(int cx, int cz) {
            int i = cx - cx0, j = cz - cz0;
            return i >= 0 && j >= 0 && i < cn && j < cn ? seenBlur[j * cn + i] : 0;
        }

        int at(int gx, int gz) {
            int i = gx - gx0, j = gz - gz0;
            return i < 0 || j < 0 || i >= n || j >= n ? 0 : cells[j * n + i];
        }

        boolean seen(int cx, int cz) {
            int i = cx - cx0, j = cz - cz0;
            return i >= 0 && j >= 0 && i < cn && j < cn && seen[j * cn + i];
        }

        /** Known 0..1: the seen flags of the chunks bilinear between their middles, smoothed. */
        double known(double x, double z) {
            double fx = (x - 8) / 16, fz = (z - 8) / 16;
            int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
            double tx = fx - ix, tz = fz - iz;
            double a = blur(ix, iz), b = blur(ix + 1, iz), c = blur(ix, iz + 1), d = blur(ix + 1, iz + 1);
            double v = (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
            v = Math.max(0, Math.min(1, (v - 0.3) / 0.4));
            return v * v * (3 - 2 * v);
        }

        /** Cubic B-spline weights of the four cells round a fraction t (smooth, no corners between cells). */
        private static void spline(double t, double[] w) {
            double t2 = t * t, t3 = t2 * t;
            w[0] = (1 - t) * (1 - t) * (1 - t) / 6;
            w[1] = (3 * t3 - 6 * t2 + 4) / 6;
            w[2] = (-3 * t3 + 3 * t2 + 3 * t + 1) / 6;
            w[3] = t3 / 6;
        }

        private final double[] wx = new double[4], wz = new double[4], classW = new double[16];
        private final int[] classes = new int[16];

        /**
         * The ground colour at (x, z): the biome (or water) with the most weight among the 4 x 4 cells round it, spline
         * weighted, so a border is a sharp smooth curve rather than a blur along the 4 m biome grid; the border gets one
         * pixel (mpp metres) of anti-aliasing. FOG where no cell round it is known.
         */
        int colour(double x, double z, double mpp) {
            double fx = x / MapGround.CELL - 0.5, fz = z / MapGround.CELL - 0.5;
            int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
            spline(fx - ix, wx);
            spline(fz - iz, wz);
            int count = 0;
            double wsum = 0;
            for (int j = 0; j < 4; j++)
                for (int i = 0; i < 4; i++) {
                    int s = at(ix - 1 + i, iz - 1 + j);
                    if (s == 0) continue;
                    double wt = wx[i] * wz[j];
                    int c = MapGround.water(s) ? -1 : MapGround.biome(s);
                    int q = 0;
                    while (q < count && classes[q] != c) q++;
                    if (q == count) { classes[count] = c; classW[count] = 0; count++; }
                    classW[q] += wt;
                    wsum += wt;
                }
            if (count == 0 || wsum <= 0) return MapStyle.FOG;
            int best = 0, second = -1;
            for (int q = 1; q < count; q++) {
                if (classW[q] > classW[best]) { second = best; best = q; }
                else if (second < 0 || classW[q] > classW[second]) second = q;
            }
            int c1 = classColour(classes[best]);
            if (second < 0) return c1;
            // across a border the weight difference changes by about 0.5 per metre
            double m = Math.min(1, 0.5 + (classW[best] - classW[second]) / wsum / Math.max(0.05, mpp * 0.5) * 0.5);
            return MapStyle.mix(classColour(classes[second]), c1, m);
        }

        private static int classColour(int c) { return c < 0 ? MapStyle.WATER : MapStyle.mapColour(c); }

        private final double[] hx = new double[4], hz = new double[4];

        /** The surface height, spline weighted over the 4 x 4 known (smoothed) cells round (x, z). */
        double height(double x, double z) {
            double fx = x / MapGround.CELL - 0.5, fz = z / MapGround.CELL - 0.5;
            int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
            spline(fx - ix, hx);
            spline(fz - iz, hz);
            double h = 0, wsum = 0;
            for (int j = 0; j < 4; j++)
                for (int i = 0; i < 4; i++) {
                    int gx = ix - 1 + i, gz = iz - 1 + j;
                    if (at(gx, gz) == 0) continue;
                    double wt = hx[i] * hz[j];
                    h += smooth[(gz - gz0) * n + (gx - gx0)] * wt;
                    wsum += wt;
                }
            return wsum <= 0 ? 0 : h / wsum;
        }
    }
}
