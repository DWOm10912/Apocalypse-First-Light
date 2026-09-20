package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import java.util.*;

/** Immutable world-space ribbon. Integer XZ denotes block centres (the common +0.5 cancels).
 * Only 8-direction control legs and 45-degree turns are accepted. No terrain or seed input. */
public final class HighwayGeometry {
    public static final double ROAD_HALF_WIDTH = 11.5; // 23 blocks, not 23 diagonal steps
    public static final int TRANSITION_LENGTH = 32;
    public static final int MAX_ENVELOPE = 32;
    private static final int TILE = 32;
    private static final double EPS = 1.0e-8;
    private static final int[] SIGNS = {-1, 1};
    private static final int[] PAINT_BANDS = {-10, -6, -2, 2, 6, 10};
    public enum Kind { AXIAL_STRAIGHT, DIAGONAL_45, AXIAL_TO_DIAGONAL_TRANSITION, DIAGONAL_TO_AXIAL_TRANSITION }
    public record Point(double x, double z) {
        Point add(Point other) { return new Point(x + other.x, z + other.z); }
        Point mul(double scale) { return new Point(x * scale, z * scale); }
        double dot(Point other) { return x * other.x + z * other.z; }
        Point normal() { return new Point(-z, x); }
    }
    public record Sample(double station, double lateral, double tangentX, double tangentZ) {
        public int band() { return Math.max(-11, Math.min(11, (int) Math.floor(lateral + .5))); }
    }
    public record Cell(int x, int z, Sample sample) {}
    public record Detail(int band, int connections) {}
    private record Leg(Point a, Point direction, double start, double length) {}
    private record Patch(Point a, Point direction, Point normalA, Point normalB,
                         double station, double length, Kind kind) {
        Sample project(double x, double z) {
            Point offset = new Point(x - a.x, z - a.z);
            double lateral = offset.dot(direction.normal());
            double k0 = normalA.dot(direction), k1 = normalB.dot(direction);
            double local = (offset.dot(direction) - lateral * k0)
                    / (1 + lateral * (k1 - k0) / length);
            if (local < -EPS || local > length + EPS) return null;
            return new Sample(station + Math.max(0, Math.min(length, local)), lateral, direction.x, direction.z);
        }
        Point end() { return a.add(direction.mul(length)); }
    }
    private final List<Point> points;
    private final List<Leg> legs;
    private final List<Patch> patches;
    private final Map<Long, List<Patch>> tiles;
    private final double length;

    public HighwayGeometry(List<Point> controlPoints) {
        points = List.copyOf(controlPoints);
        if (points.size() < 2 || points.size() > 256) throw new IllegalArgumentException("2..256 control points required");
        for (Point p : points) {
            if (!Double.isFinite(p.x) || !Double.isFinite(p.z) || p.x != Math.rint(p.x) || p.z != Math.rint(p.z)
                    || Math.abs(p.x) > 29_999_000 || Math.abs(p.z) > 29_999_000)
                throw new IllegalArgumentException("Finite integer world coordinates required");
        }
        List<Leg> built = new ArrayList<>();
        double total = 0;
        for (int i = 0; i + 1 < points.size(); i++) {
            Point a = points.get(i), b = points.get(i + 1);
            double dx = b.x - a.x, dz = b.z - a.z, distance = Math.hypot(dx, dz);
            if (distance < TRANSITION_LENGTH || (dx != 0 && dz != 0 && Math.abs(dx) != Math.abs(dz)))
                throw new IllegalArgumentException("Leg must be >=32 blocks and axial or exactly 45 degrees");
            Point direction = new Point(dx / distance, dz / distance);
            if (!built.isEmpty()) {
                double dot = built.get(built.size() - 1).direction.dot(direction);
                if (Math.abs(dot - Math.sqrt(.5)) > EPS)
                    throw new IllegalArgumentException("Only 45-degree turns; merge collinear control legs");
            }
            built.add(new Leg(a, direction, total, distance));
            total += distance;
        }
        length = total;
        legs = List.copyOf(built);
        List<Patch> ribbons = new ArrayList<>();
        for (int i = 0; i < legs.size(); i++) {
            Leg leg = legs.get(i);
            Point normal = leg.direction.normal();
            Point from = i == 0 ? normal : miter(legs.get(i - 1).direction.normal(), normal);
            Point to = i + 1 == legs.size() ? normal : miter(normal, legs.get(i + 1).direction.normal());
            double head = i == 0 ? 0 : TRANSITION_LENGTH / 2.0;
            double tail = i + 1 == legs.size() ? 0 : TRANSITION_LENGTH / 2.0;
            Kind straight = axial(leg.direction) ? Kind.AXIAL_STRAIGHT : Kind.DIAGONAL_45;
            if (head > 0) addPatches(ribbons, leg, 0, head, from, normal,
                    axial(leg.direction) ? Kind.DIAGONAL_TO_AXIAL_TRANSITION : Kind.AXIAL_TO_DIAGONAL_TRANSITION);
            if (leg.length > head + tail) addPatches(ribbons, leg, head, leg.length - tail, normal, normal, straight);
            if (tail > 0) addPatches(ribbons, leg, leg.length - tail, leg.length, normal, to,
                    axial(leg.direction) ? Kind.AXIAL_TO_DIAGONAL_TRANSITION : Kind.DIAGONAL_TO_AXIAL_TRANSITION);
        }
        patches = List.copyOf(ribbons);
        Map<Long, List<Patch>> index = new HashMap<>();
        for (Patch patch : patches) {
            BoundsXZ box = patchBounds(patch, MAX_ENVELOPE);
            for (int tx = Math.floorDiv(box.minX(), TILE); tx <= Math.floorDiv(box.maxXExclusive() - 1, TILE); tx++)
                for (int tz = Math.floorDiv(box.minZ(), TILE); tz <= Math.floorDiv(box.maxZExclusive() - 1, TILE); tz++)
                    index.computeIfAbsent(key(tx, tz), ignored -> new ArrayList<>()).add(patch);
        }
        Map<Long, List<Patch>> frozen = new HashMap<>();
        index.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        tiles = Map.copyOf(frozen);
    }
    private static void addPatches(List<Patch> result, Leg leg, double from, double to, Point n0, Point n1, Kind kind) {
        int count = Math.max(1, (int) Math.ceil((to - from) / TILE));
        for (int i = 0; i < count; i++) {
            double f0 = i / (double) count, f1 = (i + 1.0) / count;
            double start = from + (to - from) * f0;
            result.add(new Patch(leg.a.add(leg.direction.mul(start)), leg.direction,
                    lerp(n0, n1, f0), lerp(n0, n1, f1), leg.start + start, (to - from) / count, kind));
        }
    }
    private static Point lerp(Point a, Point b, double t) { return a.mul(1 - t).add(b.mul(t)); }
    private static Point miter(Point a, Point b) { return a.add(b).mul(1 / (1 + a.dot(b))); }
    private static boolean axial(Point direction) { return direction.x == 0 || direction.z == 0; }
    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    public double length() { return length; }
    public List<Point> points() { return points; }
    public Set<Kind> kinds() { return patches.stream().map(Patch::kind).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    public Point point(double station) {
        Leg leg = legAt(station);
        return leg.a.add(leg.direction.mul(Math.max(0, Math.min(leg.length, station - leg.start))));
    }
    public Point tangent(double station) { return legAt(station).direction; }
    /** Used once per chunk/edge, never for rasterizing individual blocks. */
    public double nearestStation(double x, double z) {
        double best = Double.POSITIVE_INFINITY, station = 0;
        for (Leg leg : legs) {
            Point offset = new Point(x - leg.a.x, z - leg.a.z);
            double local = Math.max(0, Math.min(leg.length, offset.dot(leg.direction)));
            Point p = leg.a.add(leg.direction.mul(local));
            double distance = Math.hypot(x - p.x, z - p.z);
            if (distance < best) { best = distance; station = leg.start + local; }
        }
        return station;
    }
    private Leg legAt(double station) {
        int low = 0, high = legs.size() - 1;
        while (low < high) { int mid = (low + high + 1) >>> 1; if (legs.get(mid).start <= station) low = mid; else high = mid - 1; }
        return legs.get(low);
    }
    /** Only local pre-indexed ribbon patches are visited, never all route segments per block. */
    public Sample query(int x, int z, double halfWidth) {
        if (halfWidth < 0 || halfWidth > MAX_ENVELOPE) throw new IllegalArgumentException("Envelope 0..32 required");
        Sample best = null;
        for (Patch patch : tiles.getOrDefault(key(Math.floorDiv(x, TILE), Math.floorDiv(z, TILE)), List.of())) {
            Sample candidate = patch.project(x, z);
            if (candidate == null || Math.abs(candidate.lateral) > halfWidth + EPS) continue;
            if (best == null || Math.abs(candidate.lateral) < Math.abs(best.lateral) - EPS
                    || (Math.abs(Math.abs(candidate.lateral) - Math.abs(best.lateral)) < EPS && candidate.station < best.station)) best = candidate;
        }
        return best;
    }
    public List<Cell> raster(BoundsXZ window, double halfWidth) {
        BoundsXZ area = window.intersection(bounds(halfWidth));
        List<Cell> result = new ArrayList<>();
        for (long tile : nearbyTiles(area)) {
            int tx = (int) (tile >> 32), tz = (int) tile;
            BoundsXZ clipped = area.intersection(new BoundsXZ(tx * TILE, tz * TILE, tx * TILE + TILE, tz * TILE + TILE));
            for (int x = clipped.minX(); x < clipped.maxXExclusive(); x++) for (int z = clipped.minZ(); z < clipped.maxZExclusive(); z++) {
                Sample sample = query(x, z, halfWidth);
                if (sample != null) result.add(new Cell(x, z, sample));
            }
        }
        result.sort(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z));
        return List.copyOf(result);
    }
    private List<Long> nearbyTiles(BoundsXZ area) {
        if (area.isEmpty()) return List.of();
        int x0 = Math.floorDiv(area.minX(), TILE), x1 = Math.floorDiv(area.maxXExclusive() - 1, TILE);
        int z0 = Math.floorDiv(area.minZ(), TILE), z1 = Math.floorDiv(area.maxZExclusive() - 1, TILE);
        List<Long> result = new ArrayList<>();
        if ((long) (x1 - x0 + 1) * (z1 - z0 + 1) <= tiles.size()) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) if (tiles.containsKey(key(x,z))) result.add(key(x,z));
        } else for (long key : tiles.keySet()) {
            int x = (int) (key >> 32), z = (int) key;
            if (x >= x0 && x <= x1 && z >= z0 && z <= z1) result.add(key);
        }
        return result;
    }
    public boolean intersects(BoundsXZ area, double expansion) {
        for (long tile : nearbyTiles(area)) for (Patch patch : tiles.get(tile))
            if (patchBounds(patch, expansion).intersects(area)) return true;
        return false;
    }
    public BoundsXZ bounds(double expansion) {
        return bounds(0, length, expansion);
    }
    public BoundsXZ bounds(double fromStation, double toStation, double expansion) {
        if (expansion < 0 || expansion > MAX_ENVELOPE) throw new IllegalArgumentException("Envelope 0..32 required");
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Patch patch : patches) {
            if (patch.station + patch.length < fromStation || patch.station > toStation) continue;
            BoundsXZ b = patchBounds(patch, expansion);
            minX = Math.min(minX, b.minX()); minZ = Math.min(minZ, b.minZ());
            maxX = Math.max(maxX, b.maxXExclusive()); maxZ = Math.max(maxZ, b.maxZExclusive());
        }
        return new BoundsXZ(minX, minZ, maxX, maxZ);
    }
    private static BoundsXZ patchBounds(Patch patch, double expansion) {
        Point[] corners = {patch.a.add(patch.normalA.mul(expansion)), patch.a.add(patch.normalA.mul(-expansion)),
                patch.end().add(patch.normalB.mul(expansion)), patch.end().add(patch.normalB.mul(-expansion))};
        double minX = Double.POSITIVE_INFINITY, minZ = minX, maxX = -minX, maxZ = -minX;
        for (Point p : corners) { minX = Math.min(minX, p.x); minZ = Math.min(minZ, p.z); maxX = Math.max(maxX, p.x); maxZ = Math.max(maxZ, p.z); }
        return new BoundsXZ((int) Math.floor(minX), (int) Math.floor(minZ), (int) Math.ceil(maxX) + 1, (int) Math.ceil(maxZ) + 1);
    }
    private int rawBand(int x, int z) {
        Sample sample = query(x, z, ROAD_HALF_WIDTH);
        return sample == null ? 100 : sample.band();
    }
    /** Thin ribbons get one deterministic orthogonal connector at diagonal steps. */
    public boolean inDetailBand(int x, int z, int band) {
        if (rawBand(x, z) == band) return true;
        Sample initial = query(x, z, ROAD_HALF_WIDTH);
        if (initial == null || Math.abs(initial.lateral - band) > 1.5) return false;
        for (int dx : SIGNS) for (int dz : SIGNS) {
            if (rawBand(x + dx, z) != band || rawBand(x, z + dz) != band) continue;
            Sample here = query(x, z, ROAD_HALF_WIDTH), other = query(x + dx, z + dz, ROAD_HALF_WIDTH);
            double a = Math.abs(here.lateral - band), b = other == null ? Double.POSITIVE_INFINITY : Math.abs(other.lateral - band);
            if (a < b - EPS || (Math.abs(a - b) <= EPS && (x < x + dx || (dx == 0 && z < z + dz)))) return true;
        }
        return false;
    }
    public static boolean dash(double station) { return Math.floorMod((long) Math.floor(station), 9L) < 3; }
    public Detail marking(int x, int z) {
        Sample sample = query(x, z, ROAD_HALF_WIDTH);
        if (sample == null) return null;
        for (int band : PAINT_BANDS) {
            if (!painted(x, z, band)) continue;
            int mask = 0;
            if (painted(x, z - 1, band)) mask |= 1;
            if (painted(x + 1, z, band)) mask |= 2;
            if (painted(x, z + 1, band)) mask |= 4;
            if (painted(x - 1, z, band)) mask |= 8;
            return new Detail(band, mask == 0 ? 16 : mask);
        }
        return null;
    }
    private boolean painted(int x, int z, int band) {
        Sample sample = query(x, z, ROAD_HALF_WIDTH);
        return sample != null && (Math.abs(band) != 6 || dash(sample.station)) && inDetailBand(x, z, band);
    }
    /** Final footprint boundary, excluding end caps, not independently offset guardrail lines. */
    public boolean outerEdge(int x, int z) {
        Sample sample = query(x, z, ROAD_HALF_WIDTH);
        return sample != null && Math.abs(sample.lateral) >= 10 &&
                (query(x - 1, z, ROAD_HALF_WIDTH) == null || query(x + 1, z, ROAD_HALF_WIDTH) == null
                 || query(x, z - 1, ROAD_HALF_WIDTH) == null || query(x, z + 1, ROAD_HALF_WIDTH) == null);
    }
}
