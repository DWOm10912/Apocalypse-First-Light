package com.antaurora.apofirstlight.worldgen.network.road;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The geometry of a tunnel's equipment as standalone fixtures (Tunnel parts removable V1.1, 2026-10-16;
 * docs/worldgen/federal_tunnels_v1.md 7): each part (TunnelMesh.Placement: x along ex, y along ey, z along ez) has boxes
 * in its own frame, placed as oriented boxes in the world; the cells they reach hold the part's fixture block
 * (block/TunnelFixtureBlock), each cell's collision the boxes on 1/8 block columns (as road_structure's). The first cell
 * (the one holding the main box's centre) is the master: it drops the part.
 * <p>
 * Every part has boxes, so every part has cells: a plate flush on a wall or a luminaire on the arch has a box from the
 * wall's face out to MIN (to be aimed at and taken down), never into the wall, so it hangs on any wall a player puts it
 * on. A box is at least MIN thick across where it matters, so the shape's 1/8 block columns always meet it (a thin post
 * or boom is a little wider than it is drawn). The thin wall parts (#collides false) only have an outline: walking
 * past a sign does not bump into it. Replaces TunnelPartSolids (Tunnel V1, whose boxes were road_structure solids).
 */
public final class TunnelFixtures {
    private TunnelFixtures() {}

    /** Least thickness of a box across (over the diagonal of the collision's 1/8 block columns), m. */
    static final double MIN = 0.24;
    /** The collision's columns per block side. */
    public static final int GRID = 8;
    /** A box reaching less than this into a cell's height does not take the cell, blocks. */
    static final double REACH = 1 / 16.0;

    /** The handrail segment's length from its bone (TunnelMesh#handrails: the length in metres), default 2.4. */
    public static double railLength(String bone) {
        if (bone == null) return TunnelMesh.SPAN;
        try {
            double l = Double.parseDouble(bone);
            return l > 0.05 && l < 16 ? l : TunnelMesh.SPAN;
        } catch (NumberFormatException e) {
            return TunnelMesh.SPAN;
        }
    }

    /**
     * A handrail segment of length len in its frame (x along it, centred; y up from the walkway; z out of the wall), as
     * boxes {x0, y0, z0, x1, y1, z1}: the tube (TunnelMesh.RAIL_H high, RAIL_X off the wall, 2 RAIL square), then an arm
     * from the wall under the tube's back half BRACKET_IN in from each end (one in the middle of a short piece). The client
     * draws them (client/road/TunnelParts), the hit mesh is them (meshhit/TunnelFixtureHits).
     */
    public static double[][] handrail(double len) {
        double h = len / 2, r = TunnelMesh.RAIL, y = TunnelMesh.RAIL_H, z = TunnelMesh.RAIL_X;
        double[] at = len >= 2 * TunnelMesh.BRACKET_IN + 0.2 ? new double[]{-h + TunnelMesh.BRACKET_IN, h - TunnelMesh.BRACKET_IN} : new double[]{0};
        double[][] out = new double[1 + at.length][];
        out[0] = new double[]{-h, y - r, z - r, h, y + r, z + r};
        for (int i = 0; i < at.length; i++) out[1 + i] = new double[]{at[i] - 0.03, y - r - 0.06, 0, at[i] + 0.03, y - r, z};
        return out;
    }

    /** Whether a part collides (the thin ones on a wall only have an outline: plates, signs, luminaires, cameras). */
    public static boolean collides(String mesh) {
        return switch (mesh) {
            case "luminaire", "cctv", "clearance_plate", "egress_sign", "name_plate" -> false;
            default -> true;
        };
    }

    /** The boxes of a part (mesh, bone: its variant), part-local {x0, y0, z0, x1, y1, z1}; the first is the main one. */
    public static double[][] boxes(String mesh, String bone) {
        boolean r2 = "r2".equals(bone);
        return switch (mesh) {
            // the cabinet; the boom standing up (open) from the hub
            case "closure_gate" -> new double[][]{{-0.25, 0, -0.25, 0.25, 1.0, 0.25}, {-0.43, 0.8, -0.12, -0.19, 9.0, 0.12}};
            // the sign with the posts' tops; two posts under it
            case "headlight_sign" -> new double[][]{{-0.8, 2.1, -0.07, 0.8, 3.3, 0.17}, {-0.67, 0, -0.12, -0.43, 2.1, 0.12},
                    {0.43, 0, -0.12, 0.67, 2.1, 0.12}};
            // pole, arm over the lanes, the heads hung from it, the head on the pole
            case "portal_signal" -> r2
                    ? new double[][]{{-0.12, 0, -0.12, 0.12, 6.6, 0.12}, {-6.0, 6.3, -0.12, -0.12, 6.45, 0.12},
                    {-4.23, 5.25, -0.02, -3.57, 6.3, 0.26}, {-0.83, 2.95, -0.02, -0.12, 3.95, 0.26}}
                    : new double[][]{{-0.12, 0, -0.12, 0.12, 6.6, 0.12}, {-11.0, 6.3, -0.12, -0.12, 6.45, 0.12},
                    {-6.13, 5.25, -0.02, -5.47, 6.3, 0.26}, {-9.83, 5.25, -0.02, -9.17, 6.3, 0.26}, {-0.83, 2.95, -0.02, -0.12, 3.95, 0.26}};
            // pole, the truss's top and bottom chords, the sign hung under the top chord
            case "cms_gantry" -> r2
                    ? new double[][]{{-0.18, 0, -0.18, 0.18, 7.7, 0.18}, {-6.2, 7.45, -0.12, -0.18, 7.6, 0.12},
                    {-2.5, 6.6, -0.12, -0.18, 6.72, 0.12}, {-6.0, 5.5, -0.35, -2.4, 7.45, 0.35}}
                    : new double[][]{{-0.18, 0, -0.18, 0.18, 7.7, 0.18}, {-10.4, 7.45, -0.12, -0.18, 7.6, 0.12},
                    {-4.3, 6.6, -0.12, -0.18, 6.72, 0.12}, {-10.2, 5.5, -0.35, -4.2, 7.45, 0.35}};
            // the head (the hanger rod over it has none)
            case "lane_signal" -> new double[][]{{-0.3, 0, -0.12, 0.3, 0.6, 0.12}};
            // casing and silencers
            case "jet_fan" -> new double[][]{{-0.64, -1.56, -2.6, 0.64, -0.28, 2.6}};
            case "emergency_cabinet" -> new double[][]{{-0.45, 0.9, 0, 0.45, 2.0, 0.27}};
            // the head under the passage's top; each side: the jamb and the open leaf against the passage's wall (into it)
            case "fire_door" -> new double[][]{{-1.12, 2.12, -0.22, 1.12, 2.4, 0.02}, {-1.4, 0, -1.24, -1.07, 2.2, 0.02},
                    {1.07, 0, -1.24, 1.4, 2.2, 0.02}};
            // plates and signs on a wall or a headwall, the luminaire on the arch: from the wall's face (z 0) out, at least MIN
            case "luminaire" -> new double[][]{{-0.6, -0.15, 0, 0.6, 0.15, MIN}};
            case "cctv" -> new double[][]{{-0.14, -0.19, 0, 0.26, 0.08, 0.44}};
            case "clearance_plate" -> new double[][]{{-1.2, 0, 0, 1.2, 0.53, MIN}};
            case "egress_sign" -> new double[][]{{-0.6, 0, 0, 0.6, 0.3, MIN}};
            case "name_plate" -> new double[][]{{-3.2, -0.4, 0, 3.2, 0.4, MIN}};
            // road signs (C3.3b, RoadSigns): the face from its bottom at RoadSigns.BOTTOM, the post under it
            // a town's STOP higher (bone town), its ALL WAY plaque under it (bone all_way; Town Framework V1)
            case "stop_sign" -> {
                double b = "town".equals(bone) || "all_way".equals(bone) ? RoadSigns.TOWN_BOTTOM : RoadSigns.BOTTOM;
                double[] face = {-RoadSigns.STOP / 2, b, -0.07, RoadSigns.STOP / 2, b + RoadSigns.STOP, 0.17};
                if (!"all_way".equals(bone)) yield new double[][]{face, {-0.12, 0, -0.12, 0.12, b, 0.12}};
                double p0 = b - RoadSigns.ALL_WAY_GAP - RoadSigns.ALL_WAY_H;
                yield new double[][]{face, {-RoadSigns.ALL_WAY_W / 2, p0, -0.07, RoadSigns.ALL_WAY_W / 2, b - RoadSigns.ALL_WAY_GAP, 0.17}, {-0.12, 0, -0.12, 0.12, p0, 0.12}};
            }
            case "speed_limit_sign" -> new double[][]{{-RoadSigns.SPEED_W / 2, RoadSigns.BOTTOM, -0.07, RoadSigns.SPEED_W / 2, RoadSigns.BOTTOM + RoadSigns.SPEED_H, 0.17},
                    {-0.12, 0, -0.12, 0.12, RoadSigns.BOTTOM, 0.12}};
            case "side_road_sign" -> new double[][]{{-RoadSigns.DIAMOND / 2, RoadSigns.BOTTOM, -0.07, RoadSigns.DIAMOND / 2, RoadSigns.BOTTOM + RoadSigns.DIAMOND, 0.17},
                    {-0.12, 0, -0.12, 0.12, RoadSigns.BOTTOM, 0.12}};
            case "do_not_enter_sign" -> new double[][]{{-RoadSigns.DO_NOT_ENTER / 2, RoadSigns.BOTTOM, -0.07, RoadSigns.DO_NOT_ENTER / 2,
                    RoadSigns.BOTTOM + RoadSigns.DO_NOT_ENTER, 0.17}, {-0.12, 0, -0.12, 0.12, RoadSigns.BOTTOM, 0.12}};
            case "wrong_way_sign" -> new double[][]{{-RoadSigns.WRONG_WAY_W / 2, RoadSigns.BOTTOM, -0.07, RoadSigns.WRONG_WAY_W / 2,
                    RoadSigns.BOTTOM + RoadSigns.WRONG_WAY_H, 0.17}, {-0.12, 0, -0.12, 0.12, RoadSigns.BOTTOM, 0.12}};
            case "curve_sign" -> new double[][]{{-RoadSigns.DIAMOND / 2, RoadSigns.CURVE_BOTTOM, -0.07, RoadSigns.DIAMOND / 2, RoadSigns.CURVE_BOTTOM + RoadSigns.DIAMOND, 0.17},
                    {-RoadSigns.PLAQUE / 2, RoadSigns.BOTTOM, -0.07, RoadSigns.PLAQUE / 2, RoadSigns.BOTTOM + RoadSigns.PLAQUE, 0.17},
                    {-0.12, 0, -0.12, 0.12, RoadSigns.BOTTOM, 0.12}};
            // interchange guide signs (C3.3b group 2): the panel as wide as its legend (GuideSigns, bone: the legend)
            case "guide_sign" -> GuideSigns.boxes(bone);
            // the rail with its brackets, from the wall (TunnelMesh.RAIL_H, RAIL_X, RAIL)
            case "handrail" -> {
                double h = railLength(bone) / 2;
                yield new double[][]{{-h, TunnelMesh.RAIL_H - TunnelMesh.RAIL - 0.06, -0.1, h, TunnelMesh.RAIL_H + TunnelMesh.RAIL,
                        TunnelMesh.RAIL_X + TunnelMesh.RAIL + 0.04}};
            }
            default -> new double[][]{{-0.25, 0, -0.25, 0.25, 0.5, 0.25}};
        };
    }

    /** An oriented box: world origin, the part's unit axes, its part-local extent. */
    public record Obb(double ox, double oy, double oz, double[] ex, double[] ey, double[] ez,
                      double x0, double y0, double z0, double x1, double y1, double z1) {
        /** The heights {lo, hi} the vertical line at world (x, z) runs inside the box; null: it misses it. */
        double[] interval(double x, double z) {
            double lo = Double.NEGATIVE_INFINITY, hi = Double.POSITIVE_INFINITY;
            double[][] axes = {ex, ey, ez};
            double[] min = {x0, y0, z0}, max = {x1, y1, z1};
            for (int k = 0; k < 3; k++) {
                double[] a = axes[k];
                // the local coordinate along a at height y: c + y * a[1]
                double c = (x - ox) * a[0] - oy * a[1] + (z - oz) * a[2];
                if (Math.abs(a[1]) < 1e-9) {
                    if (c < min[k] || c > max[k]) return null;
                    continue;
                }
                double t0 = (min[k] - c) / a[1], t1 = (max[k] - c) / a[1];
                lo = Math.max(lo, Math.min(t0, t1));
                hi = Math.min(hi, Math.max(t0, t1));
            }
            return lo < hi ? new double[]{lo, hi} : null;
        }

        /** The world point of part-local (x, y, z). */
        double[] world(double x, double y, double z) {
            return new double[]{ox + ex[0] * x + ey[0] * y + ez[0] * z, oy + ex[1] * x + ey[1] * y + ez[1] * z, oz + ex[2] * x + ey[2] * y + ez[2] * z};
        }

        /** The ray's entry distance (in units of d) into the box from world point o along d; +inf: it misses. */
        double ray(double[] o, double[] d) {
            double[][] axes = {ex, ey, ez};
            double[] min = {x0, y0, z0}, max = {x1, y1, z1};
            double lo = 0, hi = Double.POSITIVE_INFINITY;
            for (int k = 0; k < 3; k++) {
                double[] a = axes[k];
                double c = (o[0] - ox) * a[0] + (o[1] - oy) * a[1] + (o[2] - oz) * a[2], v = d[0] * a[0] + d[1] * a[1] + d[2] * a[2];
                if (Math.abs(v) < 1e-12) {
                    if (c < min[k] || c > max[k]) return Double.POSITIVE_INFINITY;
                    continue;
                }
                double t0 = (min[k] - c) / v, t1 = (max[k] - c) / v;
                lo = Math.max(lo, Math.min(t0, t1));
                hi = Math.min(hi, Math.max(t0, t1));
            }
            return lo <= hi ? lo : Double.POSITIVE_INFINITY;
        }

        /** The distance from world point p to the box (0 inside). */
        double distance(double[] p) {
            double[][] axes = {ex, ey, ez};
            double[] min = {x0, y0, z0}, max = {x1, y1, z1};
            double s = 0;
            for (int k = 0; k < 3; k++) {
                double[] a = axes[k];
                double c = (p[0] - ox) * a[0] + (p[1] - oy) * a[1] + (p[2] - oz) * a[2];
                double out = c < min[k] ? min[k] - c : c > max[k] ? c - max[k] : 0;
                s += out * out;
            }
            return Math.sqrt(s);
        }

        /** The world bounds {x0, y0, z0, x1, y1, z1}. */
        double[] bounds() {
            double[] out = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (double lx : new double[]{x0, x1}) for (double ly : new double[]{y0, y1}) for (double lz : new double[]{z0, z1}) {
                double[] w = world(lx, ly, lz);
                for (int k = 0; k < 3; k++) {
                    out[k] = Math.min(out[k], w[k]);
                    out[k + 3] = Math.max(out[k + 3], w[k]);
                }
            }
            return out;
        }
    }

    /** A placed part: its placement, its boxes in the world, their bounds; its cells (master first), built on first use. */
    public static final class Part {
        public final TunnelMesh.Placement placement;
        final Obb[] boxes;
        final double[] bounds;
        private volatile long[] cells;

        private Part(TunnelMesh.Placement p) {
            placement = p;
            double[][] bs = boxes(p.mesh(), p.bone());
            boxes = new Obb[bs.length];
            double[] b = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (int i = 0; i < bs.length; i++) {
                double[] l = bs[i];
                boxes[i] = new Obb(p.x(), p.y(), p.z(), p.ex(), p.ey(), p.ez(), l[0], l[1], l[2], l[3], l[4], l[5]);
                double[] w = boxes[i].bounds();
                for (int k = 0; k < 3; k++) {
                    b[k] = Math.min(b[k], w[k]);
                    b[k + 3] = Math.max(b[k + 3], w[k + 3]);
                }
            }
            bounds = b;
        }

        public static Part of(TunnelMesh.Placement p) { return new Part(p); }

        /** Whether cell (x, y, z) is one of the part's: a box holds one of its columns over more than REACH of its height. */
        public boolean reaches(int x, int y, int z) {
            if (x + 1 <= bounds[0] || x >= bounds[3] || y + 1 <= bounds[1] || y >= bounds[4] || z + 1 <= bounds[2] || z >= bounds[5]) return false;
            for (Obb b : boxes)
                for (int j = 0; j < GRID; j++)
                    for (int i = 0; i < GRID; i++) {
                        double[] t = b.interval(x + (i + 0.5) / GRID, z + (j + 0.5) / GRID);
                        if (t != null && Math.min(t[1], y + 1) - Math.max(t[0], y) > REACH) return true;
                    }
            return false;
        }

        /** The cell holding the main box's centre. */
        private long centre() {
            Obb b = boxes[0];
            double[] c = b.world((b.x0() + b.x1()) / 2, (b.y0() + b.y1()) / 2, (b.z0() + b.z1()) / 2);
            return pack((int) Math.floor(c[0]), (int) Math.floor(c[1]), (int) Math.floor(c[2]));
        }

        /**
         * The part's cells (packed, BlockPos.asLong layout via #pack): the master first (the main box's centre cell, else
         * the first other), then by y, z, x; at least the centre cell (a part too thin for any column).
         */
        public long[] cells() {
            long[] c = cells;
            if (c != null) return c;
            long centre = centre();
            List<Long> out = new ArrayList<>();
            boolean hasCentre = false;
            for (int y = (int) Math.floor(bounds[1]); y <= (int) Math.floor(bounds[4]); y++)
                for (int z = (int) Math.floor(bounds[2]); z <= (int) Math.floor(bounds[5]); z++)
                    for (int x = (int) Math.floor(bounds[0]); x <= (int) Math.floor(bounds[3]); x++) {
                        if (!reaches(x, y, z)) continue;
                        long k = pack(x, y, z);
                        if (k == centre) hasCentre = true;
                        else out.add(k);
                    }
            if (hasCentre || out.isEmpty()) out.add(0, centre);
            c = new long[out.size()];
            for (int i = 0; i < c.length; i++) c[i] = out.get(i);
            return cells = c;
        }

        /**
         * The part's collision in cell (x, y, z), block-local {x0, y0, z0, x1, y1, z1}: per 1/8 column the heights its boxes
         * run inside the cell, merged; where none reaches a column centre (the master of a part too thin), the boxes'
         * world bounds clipped to the cell.
         */
        public List<double[]> cellBoxes(int x, int y, int z) {
            List<double[]> out = new ArrayList<>();
            double[] ts = new double[2 * boxes.length];
            for (int j = 0; j < GRID; j++)
                for (int i = 0; i < GRID; i++) {
                    int n = 0;
                    for (Obb b : boxes) {
                        double[] t = b.interval(x + (i + 0.5) / GRID, z + (j + 0.5) / GRID);
                        if (t == null) continue;
                        double lo = Math.max(t[0], y) - y, hi = Math.min(t[1], y + 1) - y;
                        if (hi - lo < 1e-4) continue;
                        ts[n++] = lo;
                        ts[n++] = hi;
                    }
                    for (double[] iv : merge(ts, n)) out.add(new double[]{(double) i / GRID, iv[0], (double) j / GRID, (double) (i + 1) / GRID, iv[1], (double) (j + 1) / GRID});
                }
            if (out.isEmpty()) {
                double x0 = Math.max(bounds[0] - x, 0), y0 = Math.max(bounds[1] - y, 0), z0 = Math.max(bounds[2] - z, 0),
                        x1 = Math.min(bounds[3] - x, 1), y1 = Math.min(bounds[4] - y, 1), z1 = Math.min(bounds[5] - z, 1);
                if (x1 - x0 > 1e-3 && y1 - y0 > 1e-3 && z1 - z0 > 1e-3) out.add(new double[]{x0, y0, z0, x1, y1, z1});
                else out.add(new double[]{0.375, 0.375, 0.375, 0.625, 0.625, 0.625});
            }
            return out;
        }

        /** The world bounds {x0, y0, z0, x1, y1, z1} of the part's boxes. */
        public double[] bounds() { return bounds.clone(); }

        /** Where a ray from (ox, oy, oz) along (dx, dy, dz) first enters one of the part's boxes (units of d); +inf: never. */
        public double ray(double ox, double oy, double oz, double dx, double dy, double dz) {
            double best = Double.POSITIVE_INFINITY;
            double[] o = {ox, oy, oz}, d = {dx, dy, dz};
            for (Obb b : boxes) best = Math.min(best, b.ray(o, d));
            return best;
        }

        /** The distance from (x, y, z) to the part's nearest box (0 inside one). */
        public double distance(double x, double y, double z) {
            double best = Double.POSITIVE_INFINITY;
            double[] p = {x, y, z};
            for (Obb b : boxes) best = Math.min(best, b.distance(p));
            return best;
        }
    }

    /** Intervals {lo, hi} (n values) merged where they overlap or touch. */
    private static List<double[]> merge(double[] ts, int n) {
        List<double[]> out = new ArrayList<>();
        if (n == 0) return out;
        double[][] iv = new double[n / 2][];
        for (int k = 0; k < n / 2; k++) iv[k] = new double[]{ts[2 * k], ts[2 * k + 1]};
        java.util.Arrays.sort(iv, (a, b) -> Double.compare(a[0], b[0]));
        double[] cur = iv[0].clone();
        for (int k = 1; k < iv.length; k++) {
            if (iv[k][0] <= cur[1] + 1e-9) cur[1] = Math.max(cur[1], iv[k][1]);
            else {
                out.add(cur);
                cur = iv[k].clone();
            }
        }
        out.add(cur);
        return out;
    }

    /** BlockPos.asLong's layout (x 26 bits, z 26 bits, y 12 bits), so the fixture block can unpack it with BlockPos.of. */
    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }

    /** Every tunnel part and road sign of a road network, by the block columns its boxes reach. */
    public static final class Index {
        private final Map<Long, Part[]> columns;
        private final int parts;

        private Index(Map<Long, Part[]> columns, int parts) {
            this.columns = columns;
            this.parts = parts;
        }

        static Index of(List<RoadDesign> roads) { return of(roads, List.of()); }

        /** Every tunnel part of the roads and the other parts given (the road signs, C3.3b). */
        static Index of(List<RoadDesign> roads, List<TunnelMesh.Placement> others) {
            List<TunnelMesh.Placement> all = new ArrayList<>(others);
            for (RoadDesign d : roads) for (RoadDesign.Tunnel tn : d.tunnels) all.addAll(TunnelMesh.all(d, tn));
            Map<Long, List<Part>> m = new HashMap<>();
            int parts = 0;
            for (TunnelMesh.Placement p : all) {
                Part part = new Part(p);
                parts++;
                double[] r = part.bounds;
                for (int z = (int) Math.floor(r[2]); z <= (int) Math.floor(r[5]); z++)
                    for (int x = (int) Math.floor(r[0]); x <= (int) Math.floor(r[3]); x++)
                        m.computeIfAbsent(key(x, z), k -> new ArrayList<>()).add(part);
            }
            Map<Long, Part[]> out = new HashMap<>();
            for (Map.Entry<Long, List<Part>> e : m.entrySet()) out.put(e.getKey(), e.getValue().toArray(new Part[0]));
            return new Index(out, parts);
        }

        private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

        /** Every part of the network (the offline checks). */
        public int partCount() { return parts; }

        /** The parts whose boxes' bounds reach column (x, z); empty: none. */
        public Part[] at(int x, int z) {
            Part[] ps = columns.get(key(x, z));
            return ps == null ? new Part[0] : ps;
        }
    }
}
