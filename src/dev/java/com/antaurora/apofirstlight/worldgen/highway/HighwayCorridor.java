package com.antaurora.apofirstlight.worldgen.highway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one authoritative V1A.1 footprint. Clearance, earthwork and deck
 * rendering all consume the same cells; none of them re-rasterizes the route.
 */
public final class HighwayCorridor {
    public static final int ROW_MARGIN = 3;

    private final HighwayPlan plan;
    private final List<Cell> cells;
    private final List<Column> rowEnvelope;
    private final List<CenterCell> centerline;
    private final Set<SurfaceKey> surfacePositions;

    private HighwayCorridor(HighwayPlan plan, List<Cell> cells, List<Column> rowEnvelope,
                            List<CenterCell> centerline, Set<SurfaceKey> surfacePositions) {
        this.plan = plan;
        this.cells = List.copyOf(cells);
        this.rowEnvelope = List.copyOf(rowEnvelope);
        this.centerline = List.copyOf(centerline);
        this.surfacePositions = Set.copyOf(surfacePositions);
    }

    public static HighwayCorridor build(HighwayPlan plan, HighwayProfile profile) {
        HighwayPlan.Tangent tangent = plan.tangent(0.0);
        double rightX = -tangent.z();
        double rightZ = tangent.x();
        Map<Key, Double> centerDistances = new LinkedHashMap<>();
        int longitudinalSteps = Math.max(1, (int) Math.ceil(plan.length()));
        HighwayPlan.Point previous = plan.sample(0.0);
        int previousX = (int) Math.round(previous.x());
        int previousZ = (int) Math.round(previous.z());
        centerDistances.put(new Key(previousX, previousZ), 0.0);
        for (int step = 1; step <= longitudinalSteps; step++) {
            double distance = Math.min(plan.length(), step);
            HighwayPlan.Point point = plan.sample(distance);
            int x = (int) Math.round(point.x());
            int z = (int) Math.round(point.z());
            addSupercover(centerDistances, previousX, previousZ, x, z, distance - 1.0, distance);
            previousX = x;
            previousZ = z;
        }

        List<CenterCell> centerline = new ArrayList<>(centerDistances.size());
        for (Map.Entry<Key, Double> entry : centerDistances.entrySet()) {
            double distance = entry.getValue();
            centerline.add(new CenterCell(entry.getKey().x(), entry.getKey().z(), distance));
        }

        Map<Key, Cell> unique = new LinkedHashMap<>();
        for (CenterCell center : centerline) {
            HighwayProfile.Sample sample = profile.sampleAt(center.distance());
            int half = plan.width() / 2;
            for (int lateral = -half; lateral <= half; lateral++) {
                int x = (int) Math.round(center.x() + rightX * lateral);
                int z = (int) Math.round(center.z() + rightZ * lateral);
                Key key = new Key(x, z);
                unique.putIfAbsent(key, new Cell(x, z, center.distance(), lateral,
                        role(plan.width(), lateral), sample.roadY(), sample.terrainY(), sample.mode()));
            }
        }

        Map<Key, Column> row = new LinkedHashMap<>();
        for (Cell cell : unique.values()) {
            for (int dx = -ROW_MARGIN; dx <= ROW_MARGIN; dx++) {
                for (int dz = -ROW_MARGIN; dz <= ROW_MARGIN; dz++) {
                    Key key = new Key(cell.x() + dx, cell.z() + dz);
                    row.putIfAbsent(key, new Column(key.x(), key.z(), cell.roadY()));
                }
            }
        }
        Set<SurfaceKey> surfacePositions = new LinkedHashSet<>();
        for (Cell cell : unique.values()) surfacePositions.add(new SurfaceKey(cell.x(), cell.roadY(), cell.z()));
        return new HighwayCorridor(plan, new ArrayList<>(unique.values()), new ArrayList<>(row.values()), centerline,
                surfacePositions);
    }

    private static void addSupercover(Map<Key, Double> cells, int x0, int z0, int x1, int z1,
                                      double previousDistance, double currentDistance) {
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int error = dx - dz;
        int x = x0;
        int z = z0;
        int count = Math.max(1, dx + dz);
        int index = 0;
        while (true) {
            double distance = previousDistance + (currentDistance - previousDistance) * index / (double) count;
            cells.putIfAbsent(new Key(x, z), distance);
            if (x == x1 && z == z1) break;
            int oldX = x;
            int oldZ = z;
            int doubled = error * 2;
            boolean movedX = false;
            boolean movedZ = false;
            if (doubled > -dz) { error -= dz; x += sx; movedX = true; }
            if (doubled < dx) { error += dx; z += sz; movedZ = true; }
            if (movedX && movedZ) {
                cells.putIfAbsent(new Key(oldX, z), distance);
                cells.putIfAbsent(new Key(x, oldZ), distance);
            }
            index++;
        }
    }

    private static Role role(int width, int lateral) {
        if (width == HighwayPlan.MAIN_WIDTH) {
            if (lateral <= -10) return Role.OUTER_SHOULDER;
            if (lateral <= -3) return Role.CARRIAGEWAY_LEFT;
            if (lateral == -2) return Role.INNER_SHOULDER_LEFT;
            if (lateral <= 1) return Role.MEDIAN;
            if (lateral == 2) return Role.INNER_SHOULDER_RIGHT;
            if (lateral <= 9) return Role.CARRIAGEWAY_RIGHT;
            return Role.OUTER_SHOULDER;
        }
        return Role.CARRIAGEWAY_LEFT;
    }

    public HighwayPlan plan() { return plan; }
    public List<Cell> cells() { return cells; }
    public List<Column> rowEnvelope() { return rowEnvelope; }
    public List<CenterCell> centerline() { return centerline; }
    public int expectedSurfaceCells() { return cells.size(); }
    public boolean isExpectedSurface(int x, int y, int z) { return surfacePositions.contains(new SurfaceKey(x, y, z)); }

    public record Cell(int x, int z, double distance, int lateral, Role role, int roadY, int terrainY,
                       HighwayTerrainMode mode) {}
    public record Column(int x, int z, int roadY) {}
    public record CenterCell(int x, int z, double distance) {}
    private record Key(int x, int z) {}
    private record SurfaceKey(int x, int y, int z) {}

    public enum Role {
        OUTER_SHOULDER,
        CARRIAGEWAY_LEFT,
        INNER_SHOULDER_LEFT,
        MEDIAN,
        INNER_SHOULDER_RIGHT,
        CARRIAGEWAY_RIGHT
    }
}
