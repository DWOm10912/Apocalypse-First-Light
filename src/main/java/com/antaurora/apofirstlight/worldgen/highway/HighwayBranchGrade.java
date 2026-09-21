package com.antaurora.apofirstlight.worldgen.highway;

/** Immutable parent engineering plane at a mainland attachment; no routing decisions. */
public record HighwayBranchGrade(HighwayRouteGraph.Edge parent, HighwayProfile profile) {
    public static final double HOLD_LENGTH = 64;
    public static final double BLEND_END = 256;

    public double weight(double station) {
        double t = Math.max(0, Math.min(1, (station - HOLD_LENGTH) / (BLEND_END - HOLD_LENGTH)));
        return 1 - t * t * (3 - 2 * t);
    }

    private double parentStation(double x, double z) {
        return parent.orientation() == HighwayRouteGraph.Orientation.NORTH_SOUTH ? z : x;
    }

    public int parentY(double x, double z) {
        return profile.sampleAt(profile.plan().localDistance(parentStation(x, z))).roadY();
    }

    public int adjust(double station, double x, double z, int ownY) {
        return (int) Math.round(ownY + weight(station) * (parentY(x, z) - ownY));
    }

    public boolean parentViaduct(double station, double x, double z) {
        return station <= HOLD_LENGTH && profile.sampleAt(
                profile.plan().localDistance(parentStation(x, z))).mode() == HighwayTerrainMode.VIADUCT;
    }
}
