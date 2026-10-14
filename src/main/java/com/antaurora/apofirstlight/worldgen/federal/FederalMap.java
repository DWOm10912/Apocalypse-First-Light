package com.antaurora.apofirstlight.worldgen.federal;

import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.Anchor;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.CitySite;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.CoastFeature;
import com.antaurora.apofirstlight.worldgen.federal.FederalTemplate.Physiography;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample.LandmassRole;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample.NationId;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample.SurfaceClass;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample.WaterClass;
import com.antaurora.apofirstlight.worldgen.geography.SatelliteIslandPolicy;
import com.antaurora.apofirstlight.worldgen.terrain.v2.PlanNoise;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * One seed's federal national map, PROTOTYPE (C1, 2026-10-14; docs/worldgen/federal_six_state_c1.md): the
 * {@link FederalTemplate} layout with its per-seed variation. Immutable, thread-safe, pure Java, deterministic (the
 * same seed and scale give the same map on every machine: only arithmetic, sin / cos / exp / pow / atan2 / hypot of
 * the JDK, no hash-order iteration). NOT used by world generation yet.
 *
 * <p>Queries:
 * <ul>
 *   <li>{@link #stateAt}: the state a land point (mainland or satellite island) belongs to, null at sea;
 *   {@link #governingStateAt}: the same rule anywhere (territorial waters);</li>
 *   <li>{@link #sample}: a {@link MacroGeographySample} (land / sea, landmass, sea-floor height), the shape the Terrain V2
 *   planner reads its mask from, so the planner can run on this map unchanged;</li>
 *   <li>{@link #physiography}: the six province weights at a point (sum 1, smooth, independent of the borders);
 *   {@link #temperature} / {@link #moisture}: the climate gradients;</li>
 *   <li>{@link #cities}, {@link #islands}, {@link #crossings}, {@link #start}: the candidates later stages build on.</li>
 * </ul>
 * {@code scale} stretches the whole template (1 = the 20 x 16 km candidate); it exists for the size experiments.
 */
public final class FederalMap {
    public static final int SEA_LEVEL = 63;
    static final int COAST_WIDTH = 48;

    public record Island(int id, SatelliteIslandPolicy.FacilityRole role, double x, double z, double major, double minor,
                         double angle, double phase) {
        /** Signed distance proxy, m: positive inside (the r1 island outline: an ellipse with a star-shaped modulation). */
        public double distance(double px, double pz) {
            double dx = px - x, dz = pz - z;
            double a = Math.atan2(dz, dx) - angle;
            double c = Math.cos(a), s = Math.sin(a);
            double ellipse = 1 / Math.sqrt(c * c / (major * major) + s * s / (minor * minor));
            double modulation = 1 + s * s * (.06 * Math.sin(3 * a + phase) + .04 * Math.cos(5 * a - phase));
            return ellipse * modulation - Math.hypot(dx, dz);
        }
    }

    /** A bridge candidate from the mainland shore to an island (topology only; engineering is the road plan's). */
    public record Crossing(int islandId, double fromX, double fromZ, double toX, double toZ, double span) {}

    public record City(String id, FederalStateId state, FederalTemplate.CityRole role, int rank, double x, double z, boolean jittered) {}

    public record Start(FederalStateId state, double x, double z, double radius) {}

    public record Bounds(double minX, double minZ, double maxX, double maxZ) {}

    private static final Map<String, FederalMap> CACHE = new LinkedHashMap<>(8, .75f, true);

    public final long seed;
    public final double scale;
    final long noiseSeed;
    private final double wMer, wPar, nPar, nStep, nParE, cMer, sLine, sPh1, sPh2;
    private final double[] wobAmp = new double[7], wobPh = new double[7];
    private final double[] roughPh = new double[49];
    private final double[] featAngle, featWidth, featDepth;
    private final boolean[] featBay;
    private final List<Island> islands;
    private final List<Crossing> crossings;
    private final List<City> cities;

    public static synchronized FederalMap of(long seed, double scale) {
        String key = seed + "@" + scale;
        FederalMap m = CACHE.get(key);
        if (m == null) {
            m = new FederalMap(seed, scale);
            CACHE.put(key, m);
            if (CACHE.size() > 8) CACHE.remove(CACHE.keySet().iterator().next());
        }
        return m;
    }

    public static FederalMap forSeed(long seed) { return of(seed, 1.0); }

    /** A new instance outside the cache (determinism checks: two independent builds must agree). */
    static FederalMap fresh(long seed, double scale) { return new FederalMap(seed, scale); }

    private FederalMap(long seed, double scale) {
        if (!(scale > 0.25 && scale < 8)) throw new IllegalArgumentException("scale " + scale);
        this.seed = seed;
        this.scale = scale;
        this.noiseSeed = PlanNoise.mix(seed ^ 0x46454452414CL);          // "FEDRAL"
        SplittableRandom rnd = new SplittableRandom(PlanNoise.mix(seed ^ 0x5354415445L));   // "STATE"
        double j = FederalTemplate.BORDER_JITTER;
        wMer = FederalTemplate.WEST_MERIDIAN + j * (2 * rnd.nextDouble() - 1);
        wPar = FederalTemplate.WEST_PARALLEL + j * (2 * rnd.nextDouble() - 1);
        nPar = FederalTemplate.NORTH_PARALLEL + j * (2 * rnd.nextDouble() - 1);
        nStep = FederalTemplate.NORTH_STEP_X + 1.6 * j * (2 * rnd.nextDouble() - 1);
        nParE = FederalTemplate.NORTH_PARALLEL_EAST + j * (2 * rnd.nextDouble() - 1);
        cMer = FederalTemplate.CENTRE_MERIDIAN + j * (2 * rnd.nextDouble() - 1);
        sLine = FederalTemplate.SOUTH_LINE + j * (2 * rnd.nextDouble() - 1);
        sPh1 = rnd.nextDouble() * Math.PI * 2;
        sPh2 = rnd.nextDouble() * Math.PI * 2;
        SplittableRandom coast = new SplittableRandom(PlanNoise.mix(seed ^ 0x434F415354L));  // "COAST"
        for (int k = 2; k <= 6; k++) { wobAmp[k] = 0.010 + 0.014 * coast.nextDouble(); wobPh[k] = coast.nextDouble() * Math.PI * 2; }
        for (int k = 8; k <= 48; k++) roughPh[k] = coast.nextDouble() * Math.PI * 2;
        int nf = FederalTemplate.COAST.size();
        featAngle = new double[nf];
        featWidth = new double[nf];
        featDepth = new double[nf];
        featBay = new boolean[nf];
        for (int i = 0; i < nf; i++) {
            CoastFeature f = FederalTemplate.COAST.get(i);
            featAngle[i] = Math.toRadians(f.angleDeg() + 3 * (2 * coast.nextDouble() - 1));
            featWidth[i] = f.width() * (0.9 + 0.2 * coast.nextDouble());
            featDepth[i] = f.depth() * (0.85 + 0.3 * coast.nextDouble());
            featBay[i] = f.depth() > 0;
        }
        islands = planIslands(coast);
        crossings = planCrossings();
        cities = planCities(new SplittableRandom(PlanNoise.mix(seed ^ 0x43495459L)));     // "CITY"
    }

    // ------------------------------------------------------------------ coast
    /** Distance from the centre to the mainland coast in direction {@code angle} (rad), world metres. */
    public double coastRadius(double angle) {
        double r = coastRadiusWithoutBays(angle);
        for (int i = 0; i < featAngle.length; i++) {
            double t = delta(angle, featAngle[i]) / featWidth[i];
            r -= scale * featDepth[i] * Math.exp(-t * t);
        }
        return Math.max(scale * 3000, r);
    }

    private double coastRadiusWithoutBays(double angle) {
        double c = Math.abs(Math.cos(angle)), s = Math.abs(Math.sin(angle)), p = FederalTemplate.SHAPE_P;
        double base = 1 / Math.pow(Math.pow(c / FederalTemplate.HALF_X, p) + Math.pow(s / FederalTemplate.HALF_Z, p), 1 / p);
        double wob = 1;
        for (int k = 2; k <= 6; k++) wob += wobAmp[k] * Math.sin(k * angle + wobPh[k]);
        double rough = 0, rr = roughness(angle);
        for (int k = 8; k <= 48; k++) rough += 0.02 * (8.0 / k) * Math.sin(k * angle + roughPh[k]);
        return scale * base * (wob + rr * rough);
    }

    static double roughness(double angle) {
        double deg = Math.toDegrees(angle);
        double[][] t = FederalTemplate.ROUGHNESS;
        for (int i = 0; i < t.length - 1; i++) {
            if (deg >= t[i][0] && deg <= t[i + 1][0]) {
                double f = (deg - t[i][0]) / (t[i + 1][0] - t[i][0]);
                f = f * f * (3 - 2 * f);
                return t[i][1] + (t[i + 1][1] - t[i][1]) * f;
            }
        }
        return t[0][1];
    }

    /** Signed radial distance to the mainland coast (positive on land), m. */
    public double mainlandDistance(double x, double z) {
        return coastRadius(Math.atan2(z, x)) - Math.hypot(x, z);
    }

    // ------------------------------------------------------------------ islands
    private List<Island> planIslands(SplittableRandom rnd) {
        List<SatelliteIslandPolicy.Slot> slots = SatelliteIslandPolicy.slots(seed);
        List<Island> out = new ArrayList<>();
        for (int i = 0; i < SatelliteIslandPolicy.COUNT; i++) {
            double normal = Math.toRadians(FederalTemplate.ISLAND_SLOT_DEG[i] + 6 * (2 * rnd.nextDouble() - 1));
            double major = scale * FederalTemplate.ISLAND_MAJOR * (0.85 + 0.3 * rnd.nextDouble());
            double minor = scale * FederalTemplate.ISLAND_MINOR * (0.85 + 0.3 * rnd.nextDouble());
            double gap = scale * FederalTemplate.ISLAND_GAP * (0.9 + 0.2 * rnd.nextDouble());
            double phase = rnd.nextDouble() * Math.PI * 2;
            double[] shore = supportingShore(normal);
            double nx = Math.cos(normal), nz = Math.sin(normal);
            out.add(new Island(i + 1, slots.get(i).facilityRole(), shore[0] + nx * (gap + major * 1.12),
                    shore[1] + nz * (gap + major * 1.12), major, minor, normal, phase));
        }
        return List.copyOf(out);
    }

    /** The mainland shore point with the largest projection on an outward direction (so an island beyond it never overlaps). */
    private double[] supportingShore(double normal) {
        double step = Math.PI * 2 / 2048, bestA = normal, best = -Double.MAX_VALUE;
        for (int i = 0; i < 2048; i++) {
            double a = -Math.PI + i * step, p = coastRadius(a) * Math.cos(a - normal);
            if (p > best) { best = p; bestA = a; }
        }
        double lo = bestA - step, hi = bestA + step;
        for (int i = 0; i < 40; i++) {
            double a = lo + (hi - lo) / 3, b = hi - (hi - lo) / 3;
            if (coastRadius(a) * Math.cos(a - normal) < coastRadius(b) * Math.cos(b - normal)) lo = a; else hi = b;
        }
        double a = (lo + hi) / 2, r = coastRadius(a);
        return new double[]{Math.cos(a) * r, Math.sin(a) * r};
    }

    private List<Crossing> planCrossings() {
        List<Crossing> out = new ArrayList<>();
        for (Island is : islands) {
            double nx = Math.cos(is.angle()), nz = Math.sin(is.angle());
            // walk from the island centre toward the mainland: the island's shore, then the mainland's
            double tx = is.x(), tz = is.z();
            for (int k = 0; k < 8192 && is.distance(tx, tz) > 0; k++) { tx -= nx * 4; tz -= nz * 4; }
            double fx = tx, fz = tz;
            for (int k = 0; k < 8192 && mainlandDistance(fx, fz) < 0; k++) { fx -= nx * 4; fz -= nz * 4; }
            out.add(new Crossing(is.id(), fx, fz, tx, tz, Math.hypot(tx - fx, tz - fz)));
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ land / sea sample (the planner's mask)
    /** Signed distance proxy to the nearest coast of any landmass (positive on land), and which landmass: {d, id}. */
    double[] landDistance(double x, double z) {
        double d = mainlandDistance(x, z);
        int id = 0;
        for (Island is : islands) {
            double di = is.distance(x, z);
            if (di > d) { d = di; id = is.id(); }
        }
        return new double[]{d, id};
    }

    public boolean isLand(double x, double z) { return landDistance(x, z)[0] >= 0; }

    /** The sample the Terrain V2 planner reads (TerrainPlanV2#sampleMask): land / sea, landmass, sea-floor height. */
    public MacroGeographySample sample(int x, int z) {
        double[] ld = landDistance(x, z);
        double d = ld[0];
        int landmass = (int) ld[1];
        if (d >= 0) {
            LandmassRole role = landmass == 0 ? LandmassRole.MAINLAND : LandmassRole.SATELLITE_ISLAND;
            return new MacroGeographySample(d < COAST_WIDTH ? SurfaceClass.COAST : SurfaceClass.LAND, NationId.MAIN_NATION, 1,
                    landmass, role, -1, WaterClass.NONE, d, SEA_LEVEL + 2);
        }
        double angle = Math.atan2(z, x), radius = Math.hypot(x, z);
        WaterClass water = WaterClass.OPEN_OCEAN;
        int body = 0;
        for (int i = 0; i < featAngle.length; i++) {
            if (featBay[i] && Math.abs(delta(angle, featAngle[i])) < featWidth[i] * 1.7 && radius < coastRadiusWithoutBays(angle) + 100 * scale) {
                water = WaterClass.BAY;
                body = 10 + i;
                break;
            }
        }
        if (water == WaterClass.OPEN_OCEAN) {
            for (Crossing c : crossings) if (onCrossing(c, x, z)) { water = WaterClass.STRAIT; body = 100 + c.islandId(); break; }
        }
        if (water == WaterClass.OPEN_OCEAN && d >= -256) water = WaterClass.COASTAL_WATER;
        SurfaceClass surface = water == WaterClass.COASTAL_WATER ? SurfaceClass.COAST : water == WaterClass.OPEN_OCEAN
                ? SurfaceClass.OPEN_OCEAN : SurfaceClass.INLAND_WATER;
        return new MacroGeographySample(surface, NationId.NONE, 0, -1, LandmassRole.NONE, body, water, d, seaFloor(x, z, -d, water));
    }

    private static boolean onCrossing(Crossing c, double x, double z) {
        double dx = c.toX() - c.fromX(), dz = c.toZ() - c.fromZ();
        double t = ((x - c.fromX()) * dx + (z - c.fromZ()) * dz) / (dx * dx + dz * dz);
        return t >= 0 && t <= 1 && Math.hypot(x - c.fromX() - t * dx, z - c.fromZ() - t * dz) <= 96;
    }

    /** The r1 MacroGeography sea floor: shelf, slope, deep water and basins by distance from the shore. */
    private double seaFloor(double x, double z, double depth, WaterClass water) {
        boolean enclosed = water == WaterClass.BAY || water == WaterClass.STRAIT;
        double shelf = 12 * smooth(depth / 220), ocean = 23 * smooth((depth - 180) / 700), deep = 19 * smooth((depth - 850) / 1800);
        double basin = enclosed ? 0 : 26 * smooth((PlanNoise.fbm(noiseSeed, x, z, 2400, 2, 0.5, 205) * 0.3 + 0.5 - .45) / .50)
                * smooth((depth - 2000) / 1600);
        double detail = 3 * PlanNoise.fbm(noiseSeed, x, z, 170, 2, 0.5, 206) * 0.3 * smooth(depth / 300);
        return SEA_LEVEL - 1 - shelf - ocean - (enclosed ? 0 : deep) - basin + detail;
    }

    // ------------------------------------------------------------------ political layer
    /** The state a point belongs to by the border rules alone, anywhere (land or territorial water). */
    public FederalStateId governingStateAt(double x, double z) {
        double tx = x / scale, tz = z / scale;
        if (tx < wMer) return tz < wPar ? FederalStateId.S3 : FederalStateId.S6;
        if (tz < (tx < nStep ? nPar : nParE)) return FederalStateId.S4;
        if (tx < cMer) return FederalStateId.S2;
        return tz < southLine(tx) ? FederalStateId.S1 : FederalStateId.S5;
    }

    /** z (template m) of the natural S1 / S5 line at template x: a meander standing in for the river it will follow. */
    double southLine(double tx) {
        return sLine + FederalTemplate.SOUTH_LINE_MEANDER * (0.6 * Math.sin(tx / 2300 + sPh1) + 0.4 * Math.sin(tx / 900 + sPh2));
    }

    /** The state of a land point (mainland or satellite island); null at sea. */
    public FederalStateId stateAt(double x, double z) {
        double[] ld = landDistance(x, z);
        if (ld[0] < 0) return null;
        if (ld[1] > 0) {
            Island is = islands.get((int) ld[1] - 1);
            return governingStateAt(is.x(), is.z());      // an island belongs to the state off whose coast it lies
        }
        return governingStateAt(x, z);
    }

    // ------------------------------------------------------------------ physical layer
    /** Province weights at a point into out[6] (Physiography order), sum 1. Never reads the borders. */
    public void physiography(double x, double z, double[] out) {
        double tx = x / scale, tz = z / scale;
        double wx = tx + FederalTemplate.WARP_AMP * 0.3 * PlanNoise.fbm(noiseSeed, tx, tz, FederalTemplate.WARP_WAVE, 3, 0.5, 301);
        double wz = tz + FederalTemplate.WARP_AMP * 0.3 * PlanNoise.fbm(noiseSeed, tx, tz, FederalTemplate.WARP_WAVE, 3, 0.5, 302);
        java.util.Arrays.fill(out, 0);
        for (Anchor a : FederalTemplate.ANCHORS) {
            double d = capsule(wx, wz, a);
            double m = 1 - smooth((d - a.core()) / a.fade());
            int k = a.kind().ordinal();
            out[k] = Math.max(out[k], m);
        }
        double sum = 0;
        for (int k = 0; k < out.length; k++) { out[k] = out[k] * out[k]; sum += out[k]; }
        out[Physiography.PLAINS.ordinal()] += 0.05 + Math.max(0, 1 - sum) * 0.6;    // plains are the matrix between cores
        sum = 0;
        for (double v : out) sum += v;
        for (int k = 0; k < out.length; k++) out[k] /= sum;
    }

    public Physiography dominantPhysiography(double x, double z) {
        double[] w = new double[Physiography.values().length];
        physiography(x, z, w);
        int best = 0;
        for (int k = 1; k < w.length; k++) if (w[k] > w[best]) best = k;
        return Physiography.values()[best];
    }

    /** 0 (coldest) .. 1 (warmest): south is warmer, the range and the tableland are colder. */
    public double temperature(double x, double z) {
        double[] w = new double[Physiography.values().length];
        physiography(x, z, w);
        double t = 0.5 + 0.38 * (z / scale) / FederalTemplate.HALF_Z - 0.32 * w[Physiography.MOUNTAIN.ordinal()]
                - 0.08 * w[Physiography.PLATEAU.ordinal()] + 0.04 * PlanNoise.fbm(noiseSeed, x / scale, z / scale, 5000, 2, 0.5, 311);
        return clamp01(t);
    }

    /** 0 (driest) .. 1 (wettest): the east and the coasts are wetter, the tableland in the range's lee is dry. */
    public double moisture(double x, double z) {
        double[] w = new double[Physiography.values().length];
        physiography(x, z, w);
        double m = 0.5 + 0.30 * (x / scale) / FederalTemplate.HALF_X - 0.38 * w[Physiography.PLATEAU.ordinal()]
                - 0.06 * w[Physiography.PLAINS.ordinal()] + 0.22 * w[Physiography.COASTAL_LOWLAND.ordinal()]
                + 0.12 * w[Physiography.LAKE_LOWLAND.ordinal()] + 0.10 * w[Physiography.MOUNTAIN.ordinal()]
                + 0.04 * PlanNoise.fbm(noiseSeed, x / scale, z / scale, 5000, 2, 0.5, 312);
        return clamp01(m);
    }

    // ------------------------------------------------------------------ settlements, start, bounds
    /** Each candidate: up to 12 jittered tries (a fixed number of draws per city, so one city never shifts another's);
     *  failing those, the template point, walked toward the centre of its state's template cities until it is valid
     *  (400 m inland, inside its state). */
    private List<City> planCities(SplittableRandom rnd) {
        List<City> out = new ArrayList<>();
        for (CitySite c : FederalTemplate.CITIES) {
            double[] draws = new double[24];
            for (int k = 0; k < draws.length; k++) draws[k] = rnd.nextDouble();
            double x = Double.NaN, z = Double.NaN;
            boolean jittered = false;
            for (int k = 0; k < 12 && !jittered; k++) {
                double ang = draws[2 * k] * Math.PI * 2, rad = FederalTemplate.CITY_JITTER * Math.sqrt(draws[2 * k + 1]);
                x = scale * (c.x() + Math.cos(ang) * rad);
                z = scale * (c.z() + Math.sin(ang) * rad);
                jittered = cityValid(c, x, z);
            }
            if (!jittered) {
                double cx = 0, cz = 0;
                int n = 0;
                for (CitySite o : FederalTemplate.CITIES) if (o.state() == c.state()) { cx += o.x(); cz += o.z(); n++; }
                cx = scale * cx / n;
                cz = scale * cz / n;
                x = scale * c.x();
                z = scale * c.z();
                double d = Math.hypot(cx - x, cz - z);
                for (int k = 0; k < 60 && !cityValid(c, x, z) && d > 1; k++) {
                    double step = Math.min(100 * scale, d);
                    x += (cx - x) / d * step;
                    z += (cz - z) / d * step;
                    d = Math.hypot(cx - x, cz - z);
                }
            }
            out.add(new City(c.id(), c.state(), c.role(), c.rank(), x, z, jittered));
        }
        return List.copyOf(out);
    }

    private boolean cityValid(CitySite c, double x, double z) {
        return mainlandDistance(x, z) >= 400 * scale && governingStateAt(x, z) == c.state();
    }

    public List<City> cities() { return cities; }

    public City city(String id) {
        for (City c : cities) if (c.id().equals(id)) return c;
        throw new IllegalArgumentException("no city candidate " + id);
    }

    public List<Island> islands() { return islands; }

    public List<Crossing> crossings() { return crossings; }

    /** The forest-house start region (M4 integration point): the house planner searches its site inside it. */
    public Start start() {
        double x = scale * FederalTemplate.START_X, z = scale * FederalTemplate.START_Z;
        return new Start(governingStateAt(x, z), x, z, scale * FederalTemplate.START_RADIUS);
    }

    /** Bounding box of all land (mainland and islands), m. */
    public Bounds landBounds() {
        double minX = 0, minZ = 0, maxX = 0, maxZ = 0;
        for (int i = 0; i < 4096; i++) {
            double a = -Math.PI + i * Math.PI * 2 / 4096, r = coastRadius(a);
            minX = Math.min(minX, r * Math.cos(a)); maxX = Math.max(maxX, r * Math.cos(a));
            minZ = Math.min(minZ, r * Math.sin(a)); maxZ = Math.max(maxZ, r * Math.sin(a));
        }
        for (Island is : islands) {
            double e = is.major() * 1.12;
            minX = Math.min(minX, is.x() - e); maxX = Math.max(maxX, is.x() + e);
            minZ = Math.min(minZ, is.z() - e); maxZ = Math.max(maxZ, is.z() + e);
        }
        return new Bounds(minX, minZ, maxX, maxZ);
    }

    // ------------------------------------------------------------------ helpers
    static double capsule(double x, double z, Anchor a) {
        double dx = a.bx() - a.ax(), dz = a.bz() - a.az(), l2 = dx * dx + dz * dz;
        double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - a.ax()) * dx + (z - a.az()) * dz) / l2));
        return Math.hypot(x - a.ax() - t * dx, z - a.az() - t * dz);
    }

    static double delta(double a, double b) { return Math.atan2(Math.sin(a - b), Math.cos(a - b)); }

    static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }
}
