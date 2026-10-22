package com.antaurora.apofirstlight.worldgen.underground;

import com.antaurora.apofirstlight.worldgen.network.road.RoadCounty;
import com.antaurora.apofirstlight.worldgen.network.road.RoadDesign;
import com.antaurora.apofirstlight.worldgen.network.town.TownPlan;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The engineering protection the underground plan keeps clear (docs/worldgen/underground_geology_caves_v0_design.md
 * 12.2, U1 form 19.8): road works (with bridges, tunnels and the home's driveway), towns, the forest home's start
 * area, open water and river beds, and the research facility's boxes. Built from the C plans the world actually has
 * (RoadNetworkJson designs, TownPlan, RoadCounty.Home); rasters on the terrain plan's 16 m grid with a chamfer distance
 * to the nearest road work and town limit. Generation only: it never stops a player digging (10.3).
 */
public final class GeoProtect {
    public enum Zone { NONE, ROAD, DRIVE, BRIDGE, TUNNEL, TOWN, START_AREA, WATER, FACILITY }

    /** Horizontal clearance from each road work, m (12.2). */
    public static double clearance(Zone z) {
        return switch (z) {
            case BRIDGE -> 16;
            case TUNNEL -> 20;
            default -> 12;
        };
    }

    /** Roof depth under a road work by layer (L3 40 m, L4 120 m; L2 none at all). */
    public static final double ROAD_ROOF_L3 = 40, ROAD_ROOF_L4 = 120;
    public static final double TOWN_MARGIN = 50, START_RADIUS = 300, TOWN_ROOF_L4 = 150, BED_ROOF_L2 = 20;
    public static final double ENTRANCE_ROAD = 150, PIT_ROAD = 60, ENTRANCE_TOWN = 150, ENTRANCE_HOME = 300, ENTRANCE_RIVER = 30;
    /** The facility's boxes grow by this before caves keep out (19.5). */
    public static final double FACILITY_MARGIN = 20;

    final TerrainPlanSurface s;
    final int n;
    final double origin, cell;
    final float[] roadD, roadY, townD;
    final byte[] roadKind;
    final boolean[] wet;
    final double homeX, homeZ;
    final boolean home;
    final List<UndergroundPlan.Box> facility = new ArrayList<>();
    /** Roads' sampled centrelines {x, z, y} per design (the check draws them). */
    public final List<double[]> roadLines = new ArrayList<>();
    public final List<TownPlan> towns;

    public GeoProtect(TerrainPlanSurface s, List<RoadDesign> roads, List<TownPlan> towns, RoadCounty.Home h) {
        this.s = s;
        this.towns = towns == null ? List.of() : towns;
        n = s.gridN;
        origin = s.gridOrigin;
        cell = TerrainPlanSurface.CELL;
        int m = n * n;
        roadD = new float[m];
        roadY = new float[m];
        townD = new float[m];
        roadKind = new byte[m];
        wet = new boolean[m];
        Arrays.fill(roadD, Float.MAX_VALUE);
        Arrays.fill(roadY, Float.MAX_VALUE);
        Arrays.fill(townD, Float.MAX_VALUE);
        if (roads != null) for (RoadDesign r : roads) {
            double hw = r.section.edge + 6;
            double[] line = new double[(int) (r.length / 8) * 3 + 6];
            int k = 0;
            for (double t = 0; t <= r.length; t += 4) {
                Zone z = Zone.ROAD;
                for (RoadDesign.Tunnel tn : r.tunnels) if (tn.contains(t)) z = Zone.TUNNEL;
                if (z == Zone.ROAD) for (RoadDesign.Span sp : r.spans) if (sp.contains(t)) z = Zone.BRIDGE;
                double y = r.formationY(t, 0);
                for (double u = -hw; u <= hw; u += 6) {
                    double[] p = r.point(t, u);
                    mark(p[0], p[1], y, z);
                }
                if (((int) t) % 8 == 0 && k + 3 <= line.length) {
                    double[] p = r.point(t, 0);
                    line[k++] = p[0];
                    line[k++] = p[1];
                    line[k++] = y;
                }
            }
            roadLines.add(Arrays.copyOf(line, k));
        }
        if (h != null && h.drive() != null) {
            double[] d = h.drive();
            for (int i = 0; i + 5 < d.length; i += 3) {
                double len = Math.hypot(d[i + 3] - d[i], d[i + 5] - d[i + 2]);
                for (double t = 0; t <= len; t += 3) {
                    double f = len < 1e-6 ? 0 : t / len;
                    double x = d[i] + (d[i + 3] - d[i]) * f, y = d[i + 1] + (d[i + 4] - d[i + 1]) * f, z = d[i + 2] + (d[i + 5] - d[i + 2]) * f;
                    for (double u = -4; u <= 4; u += 4) mark(x + u, z, y, Zone.DRIVE);
                }
            }
        }
        for (TownPlan t : this.towns) {
            double[][] cs = {t.world(t.limit[0], t.limit[2]), t.world(t.limit[1], t.limit[2]), t.world(t.limit[0], t.limit[3]), t.world(t.limit[1], t.limit[3])};
            double x0 = 1e18, x1 = -1e18, z0 = 1e18, z1 = -1e18;
            for (double[] c : cs) { x0 = Math.min(x0, c[0]); x1 = Math.max(x1, c[0]); z0 = Math.min(z0, c[1]); z1 = Math.max(z1, c[1]); }
            for (int r = idx(z0); r <= idx(z1); r++) for (int c = idx(x0); c <= idx(x1); c++) {
                double[] l = t.local(centre(c), centre(r));
                if (t.inside(l[0], l[1], 0)) townD[r * n + c] = 0;
            }
        }
        chamfer(roadD, roadY, roadKind);
        chamfer(townD, null, null);
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) {
            double x = centre(c), z = centre(r);
            int k = s.waterClass(x, z);
            boolean w = k != 0 && k != 3;
            if (!w && s.rivers != null) w = s.rivers.nearestEdge(x, z, BED_ROOF_L2 + 1, new double[1]) < BED_ROOF_L2;
            wet[r * n + c] = w;
        }
        home = h != null;
        homeX = h == null ? 0 : h.x();
        homeZ = h == null ? 0 : h.z();
    }

    int idx(double v) { return Math.max(0, Math.min(n - 1, (int) Math.floor((v - origin) / cell))); }

    double centre(int i) { return origin + (i + 0.5) * cell; }

    int cellOf(double x, double z) { return idx(z) * n + idx(x); }

    void mark(double x, double z, double y, Zone k) {
        int i = cellOf(x, z);
        roadD[i] = 0;
        roadY[i] = (float) Math.min(roadY[i], y);
        if (k.ordinal() > (roadKind[i] & 0xFF) || roadKind[i] == 0) roadKind[i] = (byte) k.ordinal();
    }

    /** Two-pass chamfer distance (m) on the grid, carrying the source's Y and kind with it. */
    void chamfer(float[] d, float[] y, byte[] kind) {
        float o = (float) cell, dg = (float) (cell * Math.sqrt(2));
        int[] dr1 = {-1, -1, -1, 0}, dc1 = {-1, 0, 1, -1};
        float[] w1 = {dg, o, dg, o};
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) {
            int i = r * n + c;
            for (int k = 0; k < 4; k++) {
                int rr = r + dr1[k], cc = c + dc1[k];
                if (rr < 0 || cc < 0 || cc >= n) continue;
                int j = rr * n + cc;
                if (d[j] + w1[k] < d[i]) { d[i] = d[j] + w1[k]; if (y != null) { y[i] = y[j]; kind[i] = kind[j]; } }
            }
        }
        for (int r = n - 1; r >= 0; r--) for (int c = n - 1; c >= 0; c--) {
            int i = r * n + c;
            for (int k = 0; k < 4; k++) {
                int rr = r - dr1[k], cc = c - dc1[k];
                if (rr >= n || cc < 0 || cc >= n) continue;
                int j = rr * n + cc;
                if (d[j] + w1[k] < d[i]) { d[i] = d[j] + w1[k]; if (y != null) { y[i] = y[j]; kind[i] = kind[j]; } }
            }
        }
    }

    // ------------------------------------------------------------------ queries

    public double roadDistance(double x, double z) { return roadD[cellOf(x, z)]; }

    public double townDistance(double x, double z) { return townD[cellOf(x, z)]; }

    public double homeDistance(double x, double z) { return home ? Math.hypot(x - homeX, z - homeZ) : Double.MAX_VALUE; }

    public boolean wet(double x, double z) { return wet[cellOf(x, z)]; }

    /** The road work kind nearest (x, z) and its formation Y (NONE when no road is known). */
    public Zone roadKind(double x, double z) {
        int i = cellOf(x, z);
        return roadD[i] == Float.MAX_VALUE ? Zone.NONE : Zone.values()[roadKind[i] == 0 ? Zone.ROAD.ordinal() : roadKind[i]];
    }

    public double roadY(double x, double z) { return roadY[cellOf(x, z)]; }

    /** What protects the column (the first that applies, roads before towns). */
    public Zone zone(double x, double z) {
        int i = cellOf(x, z);
        Zone rk = roadKind(x, z);
        if (rk != Zone.NONE && roadD[i] <= clearance(rk)) return rk;
        if (townD[i] <= TOWN_MARGIN) return Zone.TOWN;
        if (homeDistance(x, z) <= START_RADIUS) return Zone.START_AREA;
        for (UndergroundPlan.Box b : facility) {
            double[] bb = b.bounds();
            if (x >= bb[0] && x <= bb[3] && z >= bb[2] && z <= bb[5]) return Zone.FACILITY;
        }
        if (wet[i]) return Zone.WATER;
        return Zone.NONE;
    }

    /**
     * The highest roof Y a natural cave object of the layer may have in this column (12.2): -inf where none may be,
     * +inf where nothing protects it.
     */
    public double roofLimit(int layer, double x, double z) {
        int i = cellOf(x, z);
        double lim = Double.POSITIVE_INFINITY;
        Zone rk = roadKind(x, z);
        if (rk != Zone.NONE && roadD[i] <= clearance(rk)) {
            if (layer <= 2) return Double.NEGATIVE_INFINITY;
            lim = Math.min(lim, roadY[i] - (layer == 3 ? ROAD_ROOF_L3 : ROAD_ROOF_L4));
        }
        if (townD[i] <= TOWN_MARGIN || homeDistance(x, z) <= START_RADIUS) {
            if (layer <= 3) return Double.NEGATIVE_INFINITY;
            lim = Math.min(lim, s.heightAt(x, z) - TOWN_ROOF_L4);
        }
        if (layer == 2 && wet[i]) lim = Math.min(lim, s.heightAt(x, z) - BED_ROOF_L2);
        return lim;
    }

    /** Whether a natural entrance of the kind may open at (x, z) (9.3). */
    public boolean entranceAllowed(UndergroundPlan.EntranceKind k, double x, double z) {
        int i = cellOf(x, z);
        double road = k == UndergroundPlan.EntranceKind.COLLAPSE_PIT ? PIT_ROAD : ENTRANCE_ROAD;
        if (roadD[i] < road || townD[i] < ENTRANCE_TOWN || homeDistance(x, z) < ENTRANCE_HOME || wet[i]) return false;
        if (s.rivers != null && s.rivers.nearestEdge(x, z, ENTRANCE_RIVER + 1, new double[1]) < ENTRANCE_RIVER) return false;
        for (UndergroundPlan.Box b : facility) {
            double[] bb = b.bounds();
            if (x >= bb[0] - 60 && x <= bb[3] + 60 && z >= bb[2] - 60 && z <= bb[5] + 60) return false;
        }
        return true;
    }

    public void addFacility(List<UndergroundPlan.Box> boxes) { facility.addAll(boxes); }

    public List<UndergroundPlan.Box> facilityBoxes() { return facility; }

    /** Whether a shape's bounds reach into a facility protection box. */
    public boolean facilityHit(UndergroundPlan.Shape sh) {
        double[] a = sh.bounds();
        for (UndergroundPlan.Box b : facility) {
            double[] c = b.bounds();
            if (a[0] < c[3] && a[3] > c[0] && a[1] < c[4] && a[4] > c[1] && a[2] < c[5] && a[5] > c[2]) return true;
        }
        return false;
    }

    /**
     * Whether a cave shape of the layer keeps every protection rule: its roof under each footprint sample (8 m apart,
     * on its centreline and edges) at or below the column's limit, and clear of the facility.
     */
    public boolean allows(UndergroundPlan.Shape sh, int layer) { return violation(sh, layer) == null; }

    /** The first rule a shape breaks, or null (the check's report and the planner's filter). */
    public String violation(UndergroundPlan.Shape sh, int layer) {
        if (facilityHit(sh)) return "facility";
        double[] b = sh.bounds();
        double top = b[4];
        if (sh instanceof UndergroundPlan.Capsule c) {
            double len = Math.hypot(c.bx() - c.ax(), c.bz() - c.az());
            int k = Math.max(1, (int) Math.ceil(len / 6));
            double nx = len < 1e-6 ? 1 : -(c.bz() - c.az()) / len, nz = len < 1e-6 ? 0 : (c.bx() - c.ax()) / len;
            for (int i = 0; i <= k; i++) {
                double f = (double) i / k;
                double x = c.ax() + (c.bx() - c.ax()) * f, z = c.az() + (c.bz() - c.az()) * f, y = c.ay() + (c.by() - c.ay()) * f + c.rv();
                for (int sd = -1; sd <= 1; sd++) {
                    String v = check(layer, x + nx * sd * c.rh(), z + nz * sd * c.rh(), y);
                    if (v != null) return v;
                }
            }
            return null;
        }
        for (double x = b[0]; x <= b[3] + 1e-6; x += Math.max(4, Math.min(8, (b[3] - b[0]) / 2))) {
            for (double z = b[2]; z <= b[5] + 1e-6; z += Math.max(4, Math.min(8, (b[5] - b[2]) / 2))) {
                double y = top;
                if (sh instanceof UndergroundPlan.Ellipsoid e) {
                    double r = e.roofAt(x, z);
                    if (Double.isNaN(r)) continue;
                    y = r;
                }
                String v = check(layer, x, z, y);
                if (v != null) return v;
            }
        }
        return null;
    }

    String check(int layer, double x, double z, double roof) {
        double lim = roofLimit(layer, x, z);
        if (roof <= lim) return null;
        return zone(x, z).name().toLowerCase(java.util.Locale.ROOT) + String.format(java.util.Locale.ROOT, " (roof %.0f > %.0f at %.0f, %.0f)", roof, lim, x, z);
    }
}
