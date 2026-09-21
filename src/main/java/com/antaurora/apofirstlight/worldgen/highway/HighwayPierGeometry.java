package com.antaurora.apofirstlight.worldgen.highway;

import java.util.ArrayList;
import java.util.List;

/** Finite, world-space station support geometry; chunk ownership is applied afterwards. */
public final class HighwayPierGeometry {
    public static final int SPACING = 32;
    private HighwayPierGeometry() {}
    public record Column(int x, int z) {}
    public record Pose(int x, int z, HighwayPlan.Tangent tangent) {}

    public static Pose at(HighwayPlan plan, double globalStation) {
        double local = plan.localDistance(globalStation);
        var point = plan.sample(local);
        return new Pose((int) Math.round(point.x()), (int) Math.round(point.z()), plan.tangent(local));
    }

    /** Inverse rasterization avoids the holes caused by rotating already-rounded local pixels. */
    public static List<Column> rectangle(int x, int z, HighwayPlan.Tangent t,
                                        int lateralMin, int lateralMax, int alongMin, int alongMax) {
        int radius = (int) Math.ceil(Math.hypot(Math.max(Math.abs(lateralMin), Math.abs(lateralMax)) + .5,
                Math.max(Math.abs(alongMin), Math.abs(alongMax)) + .5));
        List<Column> result = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            double along = dx * t.x() + dz * t.z();
            double lateral = -dx * t.z() + dz * t.x();
            if (along >= alongMin - .5 && along < alongMax + .5
                    && lateral >= lateralMin - .5 && lateral < lateralMax + .5)
                result.add(new Column(x + dx, z + dz));
        }
        return List.copyOf(result);
    }
}
