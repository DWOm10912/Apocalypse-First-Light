package com.antaurora.apofirstlight.worldgen.geography;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.antaurora.apofirstlight.worldgen.geography.MacroGeographySample.*;

/** Immutable, chunk-independent V1.1 geography. Content assignment belongs to later planners. */
public final class MacroGeography {
    public static final int VERSION = 2; // V1.1 planning revision; mainland parameters retain their original salts.
    public static final int SEA_LEVEL = 63;
    public static final int STARTUP_MAINLAND_RESERVE = 384;
    public static final int MAINLAND_CORE_RADIUS = 3800;
    public static final int COAST_WIDTH = 48;
    public static final int RURAL_COAST_BUFFER = 128;
    private static final Map<Long, MacroGeography> PLANS = new LinkedHashMap<>(16, .75f, true);
    private final long seed;
    private final double majorRadius, minorRadius, rotation, phase;
    private final List<Bay> bays;
    private final List<Island> islands;
    private final List<CrossingCandidate> crossings;
    private final InlandSea inlandSea;

    public static synchronized MacroGeography forSeed(long seed) {
        MacroGeography plan = PLANS.get(seed);
        if (plan == null) {
            plan = new MacroGeography(seed);
            PLANS.put(seed, plan);
            if (PLANS.size() > 16) PLANS.remove(PLANS.keySet().iterator().next());
        }
        return plan;
    }

    private MacroGeography(long seed) {
        this.seed = seed;
        majorRadius = 7300 + unit(1) * 900;
        minorRadius = 5700 + unit(2) * 600;
        rotation = unit(3) * Math.PI * 2;
        phase = unit(4) * Math.PI * 2;
        List<Bay> bayPlan = new ArrayList<>();
        int bayCount = 1 + (int) (unit(5) * 3);
        for (int i = 0; i < bayCount; i++) {
            bayPlan.add(new Bay(10 + i, rotation + unit(10 + i) * Math.PI * 2,
                    .10 + unit(20 + i) * .07, 400 + unit(30 + i) * 200));
        }
        bays = List.copyOf(bayPlan);
        // Enclosed water sits on the major-axis shoulder, never inside the protected core.
        double seaAngle = rotation + .25 * (unit(40) - .5);
        double seaRadius = coastRadius(seaAngle) - 1050;
        InlandSea sea = new InlandSea(1, Math.cos(seaAngle) * seaRadius,
                Math.sin(seaAngle) * seaRadius, 420, 620, seaAngle);
        inlandSea = unit(41) < .65 && seaRadius - 620 > MAINLAND_CORE_RADIUS + 256
                && enclosedSeaFits(sea) ? sea : null;
        List<Island> islandPlan = new ArrayList<>();
        List<CrossingCandidate> crossingPlan = new ArrayList<>();
        int count = 1 + (int) (unit(50) * 3);
        for (int i = 0; i < count; i++) {
            double angle = rotation + .6 + i * Math.PI * 2 / 3 + (unit(60 + i) - .5) * .25;
            IslandPlacement placement = planIsland(i, angle);
            islandPlan.add(placement.island());
            crossingPlan.add(placement.crossing());
        }
        islands = List.copyOf(islandPlan);
        crossings = List.copyOf(crossingPlan);
    }

    public long seed() { return seed; }
    public double majorAxis() { return majorRadius * 2; }
    public double minorAxis() { return minorRadius * 2; }
    public int inlandSeaCount() { return inlandSea == null ? 0 : 1; }
    public int bayCount() { return bays.size(); }
    public List<Island> islands() { return islands; }
    public List<CrossingCandidate> crossingCandidates() { return crossings; }

    /** Beyond this radius all finite mainland/island shelves are OPEN_OCEAN. */
    public int outerOceanRadius() {
        double bound = majorRadius * 1.058 + 260;
        for (Island island : islands) {
            bound = Math.max(bound, Math.hypot(island.x(), island.z()) + island.majorRadius() * 1.10);
        }
        return (int) Math.ceil(bound + 320);
    }

    private IslandPlacement planIsland(int index, double slotAngle) {
        double major = 550 + unit(70 + index) * 200;
        double minor = 400 + unit(90 + index) * 100;
        double gap = 400 + unit(80 + index) * 100;
        double outlinePhase = unit(100 + index) * Math.PI * 2;
        // Each stable slot owns a narrow angular sector; no random retries or load-order inputs.
        for (int attempt = 0; attempt < 33; attempt++) {
            double angle = slotAngle + (attempt % 2 == 0 ? -1 : 1) * ((attempt + 1) / 2) * .02;
            double nx = Math.cos(angle), nz = Math.sin(angle);
            Shore shore = supportingShore(angle);
            Island island = new Island(index + 1, LandmassRole.SATELLITE_ISLAND,
                    shore.x() + nx * (gap + major), shore.z() + nz * (gap + major),
                    major, minor, angle, outlinePhase, gap);
            for (int inset = 96; inset <= 192; inset += 32) {
                double ax = shore.x() - nx * inset, az = shore.z() - nz * inset;
                double bx = shore.x() + nx * (gap + inset), bz = shore.z() + nz * (gap + inset);
                if (bankFits(ax, az, nx, nz, null) && bankFits(bx, bz, nx, nz, island)) {
                    return new IslandPlacement(island, new CrossingCandidate(100 + index, 0, index + 1,
                            ax, az, bx, bz, gap));
                }
            }
        }
        // Never silently publish a zero-island or unvalidated bridge topology.
        throw new IllegalStateException("No supported satellite bank for seed " + seed + ", slot " + index);
    }

    private boolean bankFits(double x, double z, double nx, double nz, Island island) {
        // 64 x 128 blocks of bank footprint, sampled every 16 blocks; slope remains an engineering check.
        for (int forward = -32; forward <= 32; forward += 16) {
            for (int side = -64; side <= 64; side += 16) {
                double px = x + nx * forward - nz * side, pz = z + nz * forward + nx * side;
                double distance = island == null ? coastRadius(Math.atan2(pz, px)) - Math.hypot(px, pz)
                        : island.distance(px, pz);
                if (island == null && inlandSea != null) distance = Math.min(distance, inlandSea.distance(px, pz));
                if (distance < 8) return false;
            }
        }
        return true;
    }

    /** Maximum projection of the existing outer coastline onto an outward normal. */
    private Shore supportingShore(double normal) {
        double step = Math.PI * 2 / 2048, bestAngle = normal, best = -Double.MAX_VALUE;
        for (int i = 0; i < 2048; i++) {
            double angle = i * step;
            double projection = coastRadius(angle) * Math.cos(angle - normal);
            if (projection > best) { best = projection; bestAngle = angle; }
        }
        double lo = bestAngle - step, hi = bestAngle + step;
        for (int i = 0; i < 40; i++) {
            double a = lo + (hi - lo) / 3, b = hi - (hi - lo) / 3;
            if (coastRadius(a) * Math.cos(a - normal) < coastRadius(b) * Math.cos(b - normal)) lo = a;
            else hi = b;
        }
        double angle = (lo + hi) / 2, radius = coastRadius(angle);
        return new Shore(Math.cos(angle) * radius, Math.sin(angle) * radius);
    }

    private boolean enclosedSeaFits(InlandSea sea) {
        // Analytic planning only: keep a land ring even when a nearby bay bends sharply inland.
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64;
            double forward = sea.radialRadius() * Math.cos(a), side = sea.lateralRadius() * Math.sin(a);
            double x = sea.x() + forward * Math.cos(sea.angle()) - side * Math.sin(sea.angle());
            double z = sea.z() + forward * Math.sin(sea.angle()) + side * Math.cos(sea.angle());
            if (coastRadius(Math.atan2(z, x)) - Math.hypot(x, z) < 256) return false;
        }
        return true;
    }

    public MacroGeographySample sample(int x, int z) {
        double radius = Math.hypot(x, z);
        double angle = Math.atan2(z, x);
        double mainDistance = coastRadius(angle) - radius;
        double distance = mainDistance;
        int landmass = 0;
        LandmassRole role = LandmassRole.MAINLAND;
        int waterbody = 0;
        WaterClass water = WaterClass.OPEN_OCEAN;
        if (inlandSea != null) {
            double seaDistance = inlandSea.distance(x, z);
            if (seaDistance < distance) {
                distance = seaDistance;
                waterbody = inlandSea.id();
                water = WaterClass.INLAND_SEA;
            }
        }
        for (Island island : islands) {
            // Supporting-plane placement separates the full connected outline without clipping it.
            double islandDistance = island.distance(x, z);
            if (islandDistance > distance) {
                distance = islandDistance;
                landmass = island.id();
                role = island.role();
                waterbody = 0;
                water = WaterClass.OPEN_OCEAN;
            }
        }
        if (distance < 0 && water != WaterClass.INLAND_SEA) {
            for (Bay bay : bays) {
                if (Math.abs(angleDelta(angle, bay.angle())) < bay.width() * 1.7
                        && radius < coastRadiusWithoutBays(angle) + 100) {
                    water = WaterClass.BAY;
                    waterbody = bay.id();
                    break;
                }
            }
            for (CrossingCandidate crossing : crossings) {
                if (crossing.containsWaterPoint(x, z)) {
                    water = WaterClass.STRAIT;
                    waterbody = crossing.waterbodyId();
                    break;
                }
            }
            if (water == WaterClass.OPEN_OCEAN && distance >= -256) water = WaterClass.COASTAL_WATER;
        }
        double height = height(x, z, distance, radius, water, landmass);
        if (distance >= 0) {
            return new MacroGeographySample(distance < COAST_WIDTH ? SurfaceClass.COAST : SurfaceClass.LAND,
                    NationId.MAIN_NATION, 1, landmass, role, -1, WaterClass.NONE, distance, height);
        }
        SurfaceClass surface = water == WaterClass.COASTAL_WATER ? SurfaceClass.COAST
                : water == WaterClass.OPEN_OCEAN ? SurfaceClass.OPEN_OCEAN : SurfaceClass.INLAND_WATER;
        return new MacroGeographySample(surface, NationId.NONE, 0, -1, LandmassRole.NONE,
                waterbody, water, distance, height);
    }

    /** Bounded 16-block probes over the complete reservation; planners and lots stay unchanged. */
    public boolean allowsRural(int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; ; x = Math.min(maxX, x + 16)) {
            for (int z = minZ; ; z = Math.min(maxZ, z + 16)) {
                MacroGeographySample sample = sample(x, z);
                if (sample.landmassRole() != LandmassRole.MAINLAND
                        || sample.coastDistance() < RURAL_COAST_BUFFER) return false;
                if (z == maxZ) break;
            }
            if (x == maxX) break;
        }
        return true;
    }

    private double height(int x, int z, double distance, double radius, WaterClass water, int landmass) {
        // All detail amplitudes fade to zero at the same zero contour used for classification.
        if (distance >= 0) {
            double shore = smooth(distance / 320);
            double relief = 9 + 6 * noise(x, z, 600, 200) + 2 * noise(x, z, 80, 201);
            double hills = 14 * Math.pow(Math.max(0, noise(x, z, 1300, 202)), 2);
            double mountain = landmass == 0 ? 40 * smooth((noise(x, z, 1800, 203) - .60) / .35) : 0;
            double natural = SEA_LEVEL - 1 + shore * (relief + hills + mountain);
            double startup = 76 + 1.5 * noise(x, z, 220, 204);
            return lerp(startup, natural, smooth((radius - STARTUP_MAINLAND_RESERVE) / 384));
        }
        double depth = -distance;
        double shelf = 12 * smooth(depth / 220);
        double ocean = 23 * smooth((depth - 180) / 700);
        double deep = 19 * smooth((depth - 850) / 1800);
        boolean enclosed = water == WaterClass.INLAND_SEA || water == WaterClass.BAY || water == WaterClass.STRAIT;
        double basin = enclosed ? 0 : 26 * smooth((noise(x, z, 2400, 205) - .45) / .50)
                * smooth((depth - 2000) / 1600);
        double detail = 3 * noise(x, z, 170, 206) * smooth(depth / 300);
        return SEA_LEVEL - 1 - shelf - ocean - (enclosed ? 0 : deep) - basin + detail;
    }

    private double coastRadius(double angle) {
        double result = coastRadiusWithoutBays(angle);
        for (Bay bay : bays) {
            double t = angleDelta(angle, bay.angle()) / bay.width();
            result -= bay.depth() * Math.exp(-t * t);
        }
        // Analytic lower bound keeps the connected radial mainland and spawn-to-open-sea distance.
        return Math.max(4800, result);
    }

    private double coastRadiusWithoutBays(double angle) {
        double a = angle - rotation;
        double ellipse = 1 / Math.sqrt(Math.pow(Math.cos(a) / majorRadius, 2)
                + Math.pow(Math.sin(a) / minorRadius, 2));
        double irregular = 1 + .030 * Math.sin(3 * a + phase) + .018 * Math.sin(5 * a - phase)
                + .010 * Math.sin(9 * a + phase * 2);
        return ellipse * irregular + 260 * Math.pow(Math.max(0, Math.cos(2 * a - phase)), 4);
    }

    private double noise(double x, double z, double scale, long salt) {
        double px = x / scale, pz = z / scale;
        long ix = (long) Math.floor(px), iz = (long) Math.floor(pz);
        double tx = smooth(px - ix), tz = smooth(pz - iz);
        return lerp(lerp(lattice(ix, iz, salt), lattice(ix + 1, iz, salt), tx),
                lerp(lattice(ix, iz + 1, salt), lattice(ix + 1, iz + 1, salt), tx), tz);
    }

    private double lattice(long x, long z, long salt) {
        return toUnit(mix(seed ^ salt ^ x * 0x9e3779b97f4a7c15L ^ z * 0xc2b2ae3d27d4eb4fL)) * 2 - 1;
    }
    private double unit(long salt) { return toUnit(mix(seed ^ salt * 0x9e3779b97f4a7c15L)); }
    private static double toUnit(long value) { return (value >>> 11) * 0x1.0p-53; }
    private static long mix(long v) {
        v = (v ^ (v >>> 30)) * 0xbf58476d1ce4e5b9L;
        v = (v ^ (v >>> 27)) * 0x94d049bb133111ebL;
        return v ^ (v >>> 31);
    }
    private static double angleDelta(double a, double b) { return Math.atan2(Math.sin(a - b), Math.cos(a - b)); }
    private static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private record Bay(int id, double angle, double width, double depth) {}
    private record InlandSea(int id, double x, double z, double radialRadius, double lateralRadius, double angle) {
        double distance(double px, double pz) {
            double dx = px - x, dz = pz - z;
            double forward = dx * Math.cos(angle) + dz * Math.sin(angle);
            double side = -dx * Math.sin(angle) + dz * Math.cos(angle);
            return (Math.hypot(forward / radialRadius, side / lateralRadius) - 1) * radialRadius;
        }
    }
    private record Shore(double x, double z) {}
    private record IslandPlacement(Island island, CrossingCandidate crossing) {}

    public record Island(int id, LandmassRole role, double x, double z, double majorRadius,
                         double minorRadius, double angle, double outlinePhase, double gap) {
        double distance(double px, double pz) {
            double dx = px - x, dz = pz - z;
            double a = Math.atan2(dz, dx) - angle;
            double cosine = Math.cos(a), sine = Math.sin(a);
            double ellipse = 1 / Math.sqrt(cosine * cosine / (majorRadius * majorRadius)
                    + sine * sine / (minorRadius * minorRadius));
            // Positive star-shaped contour: never fragments. At +/- major axis both value and
            // derivative of the modulation vanish, preserving a broad supporting shore for bridges.
            double modulation = 1 + sine * sine * (.06 * Math.sin(3 * a + outlinePhase)
                    + .04 * Math.cos(5 * a - outlinePhase));
            return ellipse * modulation - Math.hypot(dx, dz);
        }
    }
    /** A topology candidate, not permission to build; engineering/approach validation belongs to Highway V2. */
    public record CrossingCandidate(int waterbodyId, int fromLandmassId, int toLandmassId,
                                    double fromX, double fromZ, double toX, double toZ, double waterSpan) {
        boolean containsWaterPoint(double x, double z) {
            double dx = toX - fromX, dz = toZ - fromZ;
            double t = ((x - fromX) * dx + (z - fromZ) * dz) / (dx * dx + dz * dz);
            return t >= 0 && t <= 1 && Math.hypot(x - fromX - t * dx, z - fromZ - t * dz) <= 96;
        }
    }
}
