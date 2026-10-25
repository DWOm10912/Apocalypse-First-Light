package com.antaurora.apofirstlight.worldgen.underground;

import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate;
import com.antaurora.apofirstlight.worldgen.terrain.v2.PlanNoise;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;

import java.util.ArrayList;
import java.util.List;

/**
 * The U1 geology (docs/worldgen/underground_geology_caves_v0_design.md 6 and 19): a crystalline basement under one
 * national sedimentary column whose unit thicknesses blend with the physiography weights (FederalMap.physiography, never
 * the state borders), folded in the belt, cut by a few straight normal faults, under loose ground whose kind and
 * thickness follow the landform. Read-only and deterministic from the seed and the terrain plan: two models of the same
 * seed answer every point the same, in any order. Nothing here places a block (U2 decides how the density uses it).
 * <p>
 * Depths of the four layers (5.2) are measured from Hs, the plan surface low-passed over about 256 m; the strata hang off
 * Reg, a 1 km low-pass, so a canyon or a ridge cuts through them instead of carrying them along.
 */
public final class GeologyModel {
    /** The coarse grid the smoothed surfaces and the province weights live on, m. */
    public static final double COARSE = 64;
    /** Physiography order (FederalTemplate.Physiography). */
    public static final int MOUNTAIN = 0, PLATEAU = 1, PLAINS = 2, LAKE = 3, HILLS = 4, COAST = 5;
    public static final int PROVINCES = 6;

    /** The national column, bottom to top: unit k sits on unit k - 1, unit 0 on the basement. */
    public static final RockType[] UNIT = {RockType.SANDSTONE, RockType.DOLOMITE, RockType.SHALE, RockType.ROCK_SALT,
            RockType.LIMESTONE, RockType.SHALE, RockType.SANDSTONE, RockType.LIMESTONE, RockType.MARL};
    public static final String[] UNIT_NAME = {"basal_sandstone", "lower_dolomite", "lower_shale", "rock_salt",
            "main_limestone", "coal_measures", "upper_sandstone", "upper_limestone", "coastal_marl"};
    public static final int COAL_MEASURES = 5, MAIN_LIMESTONE = 4;
    /** Unit thickness by province (MOUNTAIN, PLATEAU, PLAINS, LAKE, HILLS, COAST), m, before the thickness noise. */
    static final double[][] THICK = {
            {0, 40, 50, 40, 30, 40},
            {0, 60, 90, 90, 80, 60},
            {0, 50, 60, 70, 50, 60},
            {0, 0, 0, 45, 0, 0},
            {0, 130, 90, 50, 110, 80},
            {0, 40, 80, 30, 90, 0},
            {0, 90, 40, 20, 70, 0},
            {0, 40, 40, 40, 20, 300},
            {0, 0, 0, 0, 0, 80}};
    /** Basement top below Reg by province, m (6.1: S3 core at the surface, S5 below the deep world's floor). */
    static final double[] BASEMENT_DEPTH = {0, 300, 330, 280, 230, 650};
    /**
     * The shield edge north of the lake basin (U1 design 10: "北侧边缘基底较浅"; U3b design 14.9): from SHIELD_FROM m
     * north of the basin's axis the basement rises over SHIELD_RAMP m to SHIELD_DEPTH m below Reg and the cover thins
     * out onto it (edge wobble SHIELD_WOBBLE m).
     */
    static final double SHIELD_FROM = 500, SHIELD_RAMP = 900, SHIELD_DEPTH = 15, SHIELD_WOBBLE = 350;
    /** Loose ground and weathered rock by province, m (T1 = their sum, 6..15). */
    static final double[] SOIL = {1.5, 1.5, 7, 6, 3, 9}, WEATHERED = {5, 4.5, 3, 3, 5, 4};
    /** Water table below Hs by province, m (13.9: S5 high, S6 dry). */
    static final double[] WATER_TABLE = {140, 400, 60, 25, 90, 10};
    /** The fold belt: amplitude, wavelength, and the strike of the hills core (FederalTemplate HILLS anchor). */
    static final double FOLD_AMP = 90, FOLD_WAVE = 1400;
    /** The stack reaches about this far above Reg (rescaled within 0.6..1.6), so a ridge is never above the strata. */
    static final double STACK_HEADROOM = 120;
    /** Layer bounds (5.2), m below Hs, and the noise on them. */
    public static final double L2_TOP_MIN = 6, L3_TOP = 80, L4_TOP = 180, LAYER_NOISE = 15, FLOOR_KEEP = 30;
    /** Coal seams: their place in the coal measures (share of its thickness) and the province weight they need. */
    static final double[] SEAMS = {0.2, 0.45, 0.7, 0.9};

    public record Fault(int id, int province, double ax, double az, double bx, double bz, double throwM, double dip) {
        public double length() { return Math.hypot(bx - ax, bz - az); }
        public double strike() { return Math.atan2(bz - az, bx - ax); }
    }

    /** One column's answers (the per-y work stays in {@link #rockAt(Column, double)}). */
    public static final class Column {
        public double x, z, h, hs, reg, soil, t1, waterTable, karstCover;
        /** The shield edge's weight (0 off it, 1 where the basement is at the surface; {@link #shield}). */
        public double shield;
        public RockType soilType;
        public boolean loess;
        public final double[] w = new double[PROVINCES];
        /** tops[0] basement top, tops[k + 1] the top of unit k, absolute Y, before the faults. */
        public final double[] tops = new double[UNIT.length + 1];
        double coalW, l2, l3, n2, n3;
    }

    public final long seed;
    final long noiseSeed;
    public final TerrainPlanSurface surface;
    public final FederalMap map;
    /** The deep world's floor (3.3: -256 recommended, -192 or -64 compared in U2). */
    public final int minY;
    final int n;
    final double origin;
    final float[] hs, reg, fold, phys;
    final List<Fault> faults;
    final double strike;

    public GeologyModel(long seed, TerrainPlanSurface surface, FederalMap map, int minY) {
        this.seed = seed;
        this.noiseSeed = PlanNoise.mix(seed ^ 0x55E7_A11C_0DE5L);
        this.surface = surface;
        this.map = map;
        this.minY = minY;
        this.origin = surface.gridOrigin;
        this.n = (int) Math.ceil(surface.gridN * TerrainPlanSurface.CELL / COARSE);
        float[] h = new float[n * n];
        phys = new float[n * n * PROVINCES];
        fold = new float[n * n];
        FederalTemplate.Anchor hills = FederalTemplate.ANCHORS.get(FederalTemplate.Physiography.HILLS.ordinal());
        strike = Math.atan2(hills.bz() - hills.az(), hills.bx() - hills.ax());
        double[] w = new double[PROVINCES];
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) {
            double x = origin + (c + 0.5) * COARSE, z = origin + (r + 0.5) * COARSE;
            int i = r * n + c;
            h[i] = (float) surface.heightAt(x, z);
            map.physiography(x, z, w);
            for (int k = 0; k < PROVINCES; k++) phys[i * PROVINCES + k] = (float) w[k];
            // the fold belt: the plan's belt weight where it has one, else the hills' own share
            double wf = Math.max(surface.beltWeight(x, z), 0.5 * w[HILLS]) * (1 - w[COAST]) * (1 - 0.7 * w[MOUNTAIN]);
            double ang = strike + 0.26 * PlanNoise.fbm(noiseSeed, x, z, 6000, 2, 0.5, 711);
            double u = -Math.sin(ang) * x + Math.cos(ang) * z + 260 * PlanNoise.fbm(noiseSeed, x, z, 2500, 2, 0.5, 712);
            fold[i] = (float) (FOLD_AMP * Math.min(1, wf * 1.4) * Math.sin(2 * Math.PI * u / FOLD_WAVE));
        }
        hs = blur(blur(h, 4), 4);
        reg = blur(blur(h, 8), 8);
        faults = faults();
    }

    // ------------------------------------------------------------------ grids

    float[] blur(float[] a, int r) {
        float[] t = new float[a.length], o = new float[a.length];
        for (int row = 0; row < n; row++) for (int c = 0; c < n; c++) {
            double s = 0;
            for (int d = -r; d <= r; d++) s += a[row * n + Math.max(0, Math.min(n - 1, c + d))];
            t[row * n + c] = (float) (s / (2 * r + 1));
        }
        for (int row = 0; row < n; row++) for (int c = 0; c < n; c++) {
            double s = 0;
            for (int d = -r; d <= r; d++) s += t[Math.max(0, Math.min(n - 1, row + d)) * n + c];
            o[row * n + c] = (float) (s / (2 * r + 1));
        }
        return o;
    }

    double bilinear(float[] a, int stride, int k, double x, double z) {
        double gx = (x - origin) / COARSE - 0.5, gz = (z - origin) / COARSE - 0.5;
        int c0 = (int) Math.floor(gx), r0 = (int) Math.floor(gz);
        double fx = gx - c0, fz = gz - r0;
        int c1 = Math.max(0, Math.min(n - 1, c0 + 1)), r1 = Math.max(0, Math.min(n - 1, r0 + 1));
        c0 = Math.max(0, Math.min(n - 1, c0));
        r0 = Math.max(0, Math.min(n - 1, r0));
        double a00 = a[(r0 * n + c0) * stride + k], a10 = a[(r0 * n + c1) * stride + k];
        double a01 = a[(r1 * n + c0) * stride + k], a11 = a[(r1 * n + c1) * stride + k];
        return (a00 + (a10 - a00) * fx) + ((a01 + (a11 - a01) * fx) - (a00 + (a10 - a00) * fx)) * fz;
    }

    /** The plan surface low-passed over about 256 m: the layers' reference (5.1). */
    public double hs(double x, double z) { return bilinear(hs, 1, 0, x, z); }

    /** The plan surface low-passed over about 1 km: the strata's reference. */
    public double reg(double x, double z) { return bilinear(reg, 1, 0, x, z); }

    /** Physiography weights into out[6], bilinear on the coarse grid (sum about 1). */
    public void province(double x, double z, double[] out) {
        for (int k = 0; k < PROVINCES; k++) out[k] = bilinear(phys, PROVINCES, k, x, z);
    }

    /** The shield edge's weight at (x, z) for a lake weight (SHIELD_FROM ..). */
    public double shield(double x, double z, double wLake) {
        if (wLake <= 0) return 0;
        FederalTemplate.Anchor a = FederalTemplate.ANCHORS.get(LAKE);
        double ux = a.bx() - a.ax(), uz = a.bz() - a.az(), len = Math.hypot(ux, uz);
        ux /= len;
        uz /= len;
        double t = Math.max(0, Math.min(len, (x - a.ax()) * ux + (z - a.az()) * uz));
        double px = x - a.ax() - ux * t, pz = z - a.az() - uz * t;
        // distance from the axis, north (-z) positive; south of it no shield
        double side = px * uz - pz * ux;
        if (side <= 0) return 0;
        double off = Math.hypot(px, pz) + SHIELD_WOBBLE * PlanNoise.fbm(noiseSeed, x, z, 2500, 2, 0.5, 760);
        double s = Math.max(0, Math.min(1, (off - SHIELD_FROM) / SHIELD_RAMP));
        return s * s * (3 - 2 * s) * Math.min(1, wLake * 1.5);
    }

    public int dominantProvince(double x, double z) {
        double[] w = new double[PROVINCES];
        province(x, z, w);
        int b = 0;
        for (int k = 1; k < PROVINCES; k++) if (w[k] > w[b]) b = k;
        return b;
    }

    /** The strike caves follow (7.3): the fold belt's in the hills, the range's axis, else a slow regional joint set. */
    public double strikeAt(double x, double z) {
        int d = dominantProvince(x, z);
        if (d == HILLS) return strike + 0.26 * PlanNoise.fbm(noiseSeed, x, z, 6000, 2, 0.5, 711);
        if (d == MOUNTAIN) {
            FederalTemplate.Anchor a = FederalTemplate.ANCHORS.get(MOUNTAIN);
            return Math.atan2(a.bz() - a.az(), a.bx() - a.ax());
        }
        return Math.PI * (0.5 + 0.5 * PlanNoise.fbm(noiseSeed, x, z, 8000, 1, 0.5, 750));
    }

    // ------------------------------------------------------------------ faults

    /** A few straight normal faults in the range, the fold belt, the tableland and the lake basin's rim (6.1). */
    List<Fault> faults() {
        List<Fault> out = new ArrayList<>();
        int[][] plan = {{MOUNTAIN, 4}, {HILLS, 3}, {PLATEAU, 3}, {LAKE, 1}};
        int id = 0;
        for (int[] p : plan) {
            FederalTemplate.Anchor a = FederalTemplate.ANCHORS.get(p[0]);
            double ang0 = Math.atan2(a.bz() - a.az(), a.bx() - a.ax());
            for (int k = 0; k < p[1]; k++) {
                long key = 9100L + p[0] * 100L + k;
                double t = PlanNoise.hash01(noiseSeed, key), off = (PlanNoise.hash01(noiseSeed, key + 7) * 2 - 1) * a.core() * 0.8;
                double cx = a.ax() + (a.bx() - a.ax()) * t - Math.sin(ang0) * off;
                double cz = a.az() + (a.bz() - a.az()) * t + Math.cos(ang0) * off;
                double spread = p[0] == PLATEAU ? Math.PI : p[0] == HILLS ? 0.26 : 0.44;
                double ang = (p[0] == PLATEAU ? Math.PI / 2 : ang0) + (PlanNoise.hash01(noiseSeed, key + 11) * 2 - 1) * spread / 2;
                double len = 2500 + 4500 * PlanNoise.hash01(noiseSeed, key + 13);
                double thr = 25 + 65 * PlanNoise.hash01(noiseSeed, key + 17);
                double dip = Math.toRadians(60 + 20 * PlanNoise.hash01(noiseSeed, key + 19));
                double dx = Math.cos(ang) * len / 2, dz = Math.sin(ang) * len / 2;
                out.add(new Fault(id++, p[0], cx - dx, cz - dz, cx + dx, cz + dz, thr, dip));
            }
        }
        return out;
    }

    public List<Fault> faultList() { return faults; }

    /** Signed horizontal distance from the fault plane at Y (+ = hanging wall) and the along share t, into out[2]. */
    static void faultFrame(Fault f, double x, double z, double y, double reg, double[] out) {
        double ux = f.bx() - f.ax(), uz = f.bz() - f.az(), len = Math.hypot(ux, uz);
        ux /= len;
        uz /= len;
        double px = x - f.ax(), pz = z - f.az();
        double t = (px * ux + pz * uz) / len, d0 = -px * uz + pz * ux;
        out[0] = d0 - Math.max(0, reg - y) / Math.tan(f.dip());
        out[1] = t;
    }

    /** The fault's throw at an along share (zero beyond the tips, full in the middle). */
    static double taper(Fault f, double t) {
        if (t <= 0 || t >= 1) return 0;
        double e = 2 * t - 1;
        return f.throwM() * (1 - e * e);
    }

    /** The nearest fault trace within maxD of (x, z), or null; dist[0] gets the distance. */
    public Fault nearestFault(double x, double z, double maxD, double[] dist) {
        Fault best = null;
        double bd = maxD;
        for (Fault f : faults) {
            double ux = f.bx() - f.ax(), uz = f.bz() - f.az(), l2 = ux * ux + uz * uz;
            double t = Math.max(0, Math.min(1, ((x - f.ax()) * ux + (z - f.az()) * uz) / l2));
            double d = Math.hypot(x - f.ax() - ux * t, z - f.az() - uz * t);
            if (d < bd) { bd = d; best = f; }
        }
        if (dist != null) dist[0] = bd;
        return best;
    }

    // ------------------------------------------------------------------ columns

    public Column column(double x, double z) {
        Column c = new Column();
        column(x, z, c);
        return c;
    }

    public void column(double x, double z, Column c) {
        c.x = x;
        c.z = z;
        c.h = surface.heightAt(x, z);
        c.hs = hs(x, z);
        c.reg = reg(x, z);
        province(x, z, c.w);
        double soil = 0, weather = 0, wt = 0, depthB = 0;
        for (int k = 0; k < PROVINCES; k++) {
            soil += c.w[k] * SOIL[k];
            weather += c.w[k] * WEATHERED[k];
            wt += c.w[k] * WATER_TABLE[k];
            depthB += c.w[k] * BASEMENT_DEPTH[k];
        }
        c.shield = shield(x, z, c.w[LAKE]);
        depthB += (SHIELD_DEPTH - depthB) * c.shield;
        double slope = surface.slopeAt(x, z);
        soil *= Math.max(0.25, Math.min(1, 1 - slope * 1.5)) * (1 + 0.25 * PlanNoise.fbm(noiseSeed, x, z, 900, 2, 0.5, 721));
        c.soil = Math.max(0.5, soil);
        c.t1 = Math.max(L2_TOP_MIN, Math.min(15, c.soil + weather));
        c.waterTable = c.hs - wt;
        double river = surface.rivers == null ? 1e9 : surface.rivers.edgeDistance(x, z);
        int dom = 0;
        for (int k = 1; k < PROVINCES; k++) if (c.w[k] > c.w[dom]) dom = k;
        c.soilType = river < 80 ? RockType.ALLUVIUM : switch (dom) {
            case MOUNTAIN -> RockType.SCREE;
            case PLATEAU -> RockType.RED_SOIL;
            case LAKE -> RockType.LAKE_CLAY;
            case COAST -> RockType.SAND;
            default -> RockType.TILL;
        };
        c.loess = dom == PLAINS && PlanNoise.fbm(noiseSeed, x, z, 3000, 2, 0.5, 722) > 0.3;
        // karst cover: thick till hides the karst from the surface (few entrances), 1 = bare
        c.karstCover = Math.max(0, Math.min(1, 1 - (c.soil - 3) / 6));
        // the column: basement top, then each unit's thickness (noisy, rescaled to reach the headroom), the fold on
        // every unit above the lower dolomite (thin-skinned: the basement is not folded)
        double depthNoise = 40 * PlanNoise.fbm(noiseSeed, x, z, 5000, 2, 0.5, 723);
        double b = c.reg - Math.max(0, depthB + depthNoise * Math.min(1, depthB / 100));
        double[] t = new double[UNIT.length];
        double sum = 0;
        for (int u = 0; u < UNIT.length; u++) {
            double th = 0;
            for (int k = 0; k < PROVINCES; k++) th += c.w[k] * THICK[u][k];
            th *= 1 + 0.25 * PlanNoise.fbm(noiseSeed, x, z, 3000, 2, 0.5, 730 + u);
            t[u] = Math.max(0, th);
            sum += t[u];
        }
        double scale = sum < 1 ? 1 : Math.max(0.6, Math.min(1.6, (c.reg - b + STACK_HEADROOM) / sum));
        double f = bilinear(fold, 1, 0, x, z);
        c.tops[0] = b;
        double acc = b;
        for (int u = 0; u < UNIT.length; u++) {
            // the cover thins out onto the shield
            acc += t[u] * scale * (1 - c.shield);
            double g = u == 0 ? 0.5 : 1;
            c.tops[u + 1] = Math.max(c.tops[u], acc + f * g);
        }
        c.coalW = c.w[HILLS] + c.w[PLAINS];
        c.n2 = LAYER_NOISE * PlanNoise.fbm(noiseSeed, x, z, 2000, 2, 0.5, 701);
        c.n3 = LAYER_NOISE * PlanNoise.fbm(noiseSeed, x, z, 2000, 2, 0.5, 702);
        c.l2 = c.hs - Math.max(c.t1, 0);
        c.l3 = c.hs - (L3_TOP + c.n2);
    }

    /** Basement top and unit tops at Y after the faults (hanging-wall side lowered by the throw), into out. */
    public void tops(Column c, double y, double[] out) {
        double off = 0;
        double[] fr = new double[2];
        for (Fault f : faults) {
            faultFrame(f, c.x, c.z, y, c.reg, fr);
            if (fr[0] > 0) off += taper(f, fr[1]);
        }
        for (int i = 0; i < out.length; i++) out[i] = c.tops[i] - off;
    }

    /** Whether Y lies in a fault zone (breccia within a few metres of the plane). */
    public boolean faultZone(Column c, double y) {
        double[] fr = new double[2];
        for (Fault f : faults) {
            faultFrame(f, c.x, c.z, y, c.reg, fr);
            if (fr[1] > 0 && fr[1] < 1 && Math.abs(fr[0]) < 3 + f.throwM() / 30) return true;
        }
        return false;
    }

    /** The rock or loose ground at Y in the column; null above the plan surface. */
    public RockType rockAt(Column c, double y) {
        if (y > c.h) return null;
        double d = c.h - y;
        if (d < c.soil) return c.loess && d < 1.5 ? RockType.LOESS : c.soilType;
        double[] tp = new double[UNIT.length + 1];
        tops(c, y, tp);
        if (y < tp[0]) {
            double below = tp[0] - y;
            if (below > 250) return RockType.GNEISS;
            return PlanNoise.fbm(noiseSeed, c.x, c.z, 1500, 2, 0.5, 740) > 0.35 ? RockType.SCHIST : RockType.GRANITE;
        }
        for (int u = 0; u < UNIT.length; u++) {
            if (y < tp[u + 1]) {
                if (u == COAL_MEASURES && c.coalW > 0.3) {
                    double th = tp[u + 1] - tp[u], share = (y - tp[u]) / Math.max(1e-6, th);
                    double seam = (0.6 + 1.9 * (0.5 + 0.5 * PlanNoise.fbm(noiseSeed, c.x, c.z, 2500, 2, 0.5, 741))) * Math.min(1, (c.coalW - 0.3) * 2);
                    for (double s : SEAMS) if (Math.abs(share - s) * th < seam / 2) return RockType.COAL_SEAM;
                }
                return UNIT[u];
            }
        }
        // above the stack (a peak higher than the headroom): the top unit present, or the basement where there is none
        for (int u = UNIT.length - 1; u >= 0; u--) if (tp[u + 1] - tp[u] > 2) return UNIT[u];
        return RockType.GRANITE;
    }

    public RockType rockAt(double x, double y, double z) { return rockAt(column(x, z), y); }

    /**
     * The rock of every block of the column from y0 to y1 (out[y - y0] = {@link #rockAt(Column, double)} at the block's
     * middle, y + 0.5, exactly: the same arithmetic in the same order), for the chunk pass (U3a GeologyPass): the faults'
     * frames and the column's noises once per column instead of once per block.
     */
    public void rocks(Column c, int y0, int y1, RockType[] out) {
        int nf = faults.size(), live = 0;
        double[] d0 = new double[nf], tap = new double[nf], tan = new double[nf];
        for (Fault f : faults) {
            double ux = f.bx() - f.ax(), uz = f.bz() - f.az(), len = Math.hypot(ux, uz);
            ux /= len;
            uz /= len;
            double px = c.x - f.ax(), pz = c.z - f.az();
            double t = (px * ux + pz * uz) / len, d = -px * uz + pz * ux;
            // a fault adds its throw only where the plane is crossed (d0 > the dip's run), never with d0 <= 0
            if (d <= 0) continue;
            d0[live] = d;
            tap[live] = taper(f, t);
            tan[live] = Math.tan(f.dip());
            live++;
        }
        double schist = PlanNoise.fbm(noiseSeed, c.x, c.z, 1500, 2, 0.5, 740);
        double seam = c.coalW > 0.3 ? (0.6 + 1.9 * (0.5 + 0.5 * PlanNoise.fbm(noiseSeed, c.x, c.z, 2500, 2, 0.5, 741))) * Math.min(1, (c.coalW - 0.3) * 2) : 0;
        double[] tp = new double[UNIT.length + 1];
        for (int yi = y0; yi <= y1; yi++) {
            double y = yi + 0.5;
            RockType r;
            if (y > c.h) r = null;
            else if (c.h - y < c.soil) r = c.loess && c.h - y < 1.5 ? RockType.LOESS : c.soilType;
            else {
                double off = 0;
                for (int k = 0; k < live; k++) if (d0[k] - Math.max(0, c.reg - y) / tan[k] > 0) off += tap[k];
                for (int i = 0; i < tp.length; i++) tp[i] = c.tops[i] - off;
                r = stackRock(c, y, tp, schist, seam);
            }
            out[yi - y0] = r;
        }
    }

    /** rockAt below the loose ground, the tops after the faults given (the column's two noises given). */
    private static RockType stackRock(Column c, double y, double[] tp, double schist, double seam) {
        if (y < tp[0]) {
            double below = tp[0] - y;
            if (below > 250) return RockType.GNEISS;
            return schist > 0.35 ? RockType.SCHIST : RockType.GRANITE;
        }
        for (int u = 0; u < UNIT.length; u++) {
            if (y < tp[u + 1]) {
                if (u == COAL_MEASURES && c.coalW > 0.3) {
                    double th = tp[u + 1] - tp[u], share = (y - tp[u]) / Math.max(1e-6, th);
                    for (double s : SEAMS) if (Math.abs(share - s) * th < seam / 2) return RockType.COAL_SEAM;
                }
                return UNIT[u];
            }
        }
        for (int u = UNIT.length - 1; u >= 0; u--) if (tp[u + 1] - tp[u] > 2) return UNIT[u];
        return RockType.GRANITE;
    }

    /** The first rock under the loose ground (the bedrock map). */
    public RockType bedrock(Column c) { return rockAt(c, c.h - c.soil - 0.5); }

    /** The unit index at Y (-1 basement, UNIT.length above the stack), after the faults. */
    public int unitAt(Column c, double y) {
        double[] tp = new double[UNIT.length + 1];
        tops(c, y, tp);
        if (y < tp[0]) return -1;
        for (int u = 0; u < UNIT.length; u++) if (y < tp[u + 1]) return u;
        return UNIT.length;
    }

    // ------------------------------------------------------------------ layers

    /** Layer bottoms in the column, absolute Y: {Hs, L1 bottom, L2 bottom, L3 bottom, L4 bottom (the kept floor)}. */
    public double[] bands(Column c) {
        return new double[]{c.hs, c.hs - c.t1, c.hs - (L3_TOP + c.n2), c.hs - (L4_TOP + c.n3), minY + FLOOR_KEEP};
    }

    /** The layer at Y: 0 above the surface, 1..4, 5 in the floor's kept rock. */
    public int layer(Column c, double y) {
        if (y > c.h) return 0;
        double[] b = bands(c);
        double depth = Math.max(c.hs - y, c.h - y);
        if (depth < c.t1) return 1;
        if (y >= b[2]) return 2;
        if (y >= b[3]) return 3;
        if (y >= b[4]) return 4;
        return 5;
    }

    /** How soluble the rock at Y is (0..1): the rock's karst share, less in the coal-measure shales. */
    public double karst(Column c, double y) {
        RockType r = rockAt(c, y);
        return r == null || r.soil ? 0 : r.karst;
    }

    /** The share of karst rock in [y0, y1] of the column, sampled every 4 m. */
    public double karstShare(Column c, double y0, double y1) {
        double s = 0;
        int k = 0;
        for (double y = y0; y <= y1; y += 4, k++) s += karst(c, y);
        return k == 0 ? 0 : s / k;
    }

    /** The water table, absolute Y (a cave below it is flooded). */
    public double waterTable(Column c) { return c.waterTable; }
}
