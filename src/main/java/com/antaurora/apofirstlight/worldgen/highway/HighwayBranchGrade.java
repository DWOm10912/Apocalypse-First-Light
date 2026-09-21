package com.antaurora.apofirstlight.worldgen.highway;

/** Immutable shared node grade blended into an axial branch; no routing decisions. */
public final class HighwayBranchGrade {
    public static final double HOLD_LENGTH = 64;
    public static final double BLEND_END = 256;

    private final HighwayRouteGraph.Edge corridor;
    private final HighwayRouteGraph.Node node;
    private final HighwayRouteGraph.Edge parent;
    private final HighwayProfile profile;
    private final Integer constantY;

    /** Retained for the original polyline contract fixtures, whose station starts at the junction. */
    public HighwayBranchGrade(HighwayRouteGraph.Edge parent, HighwayProfile profile) {
        this(null, null, parent, profile, null);
    }

    private HighwayBranchGrade(HighwayRouteGraph.Edge corridor, HighwayRouteGraph.Node node,
                               HighwayRouteGraph.Edge parent, HighwayProfile profile, Integer constantY) {
        this.corridor = corridor;
        this.node = node;
        this.parent = parent;
        this.profile = profile;
        this.constantY = constantY;
    }

    public static HighwayBranchGrade junction(HighwayRouteGraph.Edge corridor,
                                               HighwayRouteGraph.Edge parent,
                                               HighwayProfile parentProfile) {
        return new HighwayBranchGrade(corridor, corridor.startNode(), parent, parentProfile, null);
    }

    public static HighwayBranchGrade turn(HighwayRouteGraph.Edge corridor,
                                           HighwayRouteGraph.Node turnNode, int turnNodeGrade) {
        return new HighwayBranchGrade(corridor, turnNode, null, null, turnNodeGrade);
    }

    public double weight(double station) {
        double t = Math.max(0, Math.min(1, (distance(station) - HOLD_LENGTH) / (BLEND_END - HOLD_LENGTH)));
        return 1 - t * t * (3 - 2 * t);
    }

    public boolean holds(double station) { return distance(station) <= HOLD_LENGTH; }

    private double distance(double station) {
        if (corridor == null) return Math.max(0, station);
        return Math.abs(station - corridor.globalStation(node.x(), node.z()));
    }

    private double parentStation(double x, double z) {
        return parent.orientation() == HighwayRouteGraph.Orientation.NORTH_SOUTH ? z : x;
    }

    public int parentY(double x, double z) {
        if (constantY != null) return constantY;
        return profile.sampleAt(profile.plan().localDistance(parentStation(x, z))).roadY();
    }

    public int adjust(double station, double x, double z, int ownY) {
        return (int) Math.round(ownY + weight(station) * (parentY(x, z) - ownY));
    }

    public boolean parentViaduct(double station, double x, double z) {
        return profile != null && holds(station) && profile.sampleAt(
                profile.plan().localDistance(parentStation(x, z))).mode() == HighwayTerrainMode.VIADUCT;
    }
}
