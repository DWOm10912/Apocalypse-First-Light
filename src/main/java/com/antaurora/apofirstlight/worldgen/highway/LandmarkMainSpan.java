package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import java.util.List;

/** V1.1A: a bounded, derived structure layer. Stations here are LOCAL to the existing bridge plan. */
public record LandmarkMainSpan(boolean eligible, int pylonA, int pylonB, String reason) {
    public static final int MIN_BRIDGE_LENGTH = 576;
    public static final int MAIN_SPAN = 256;
    public static final int MIN_APPROACH = 144;
    public static final int ZONE_HALF = 8;
    public static final int TOWER_HEIGHT = 64;
    public static final int FOOTING_HALF = 5;
    public static final List<Integer> ANCHOR_HEIGHTS = List.of(24, 28, 32, 36, 40, 44, 56, 59, 62);

    public static LandmarkMainSpan of(SeaBridgeGeometry bridge) {
        int length = (int) Math.floor(bridge.plan().length());
        if (length < MIN_BRIDGE_LENGTH) return new LandmarkMainSpan(false, 0, 0, "BRIDGE_TOO_SHORT");
        int a = (length - MAIN_SPAN) / 2, b = a + MAIN_SPAN;
        if (a - bridge.abutmentLength(true) < MIN_APPROACH
                || length - b - bridge.abutmentLength(false) < MIN_APPROACH)
            return new LandmarkMainSpan(false, a, b, "APPROACH_TOO_SHORT");
        return new LandmarkMainSpan(true, a, b, "NONE");
    }

    public List<Integer> pylons() { return List.of(pylonA, pylonB); }
    public boolean inZone(double s) {
        return eligible && pylons().stream().anyMatch(p -> Math.abs(s - p) <= ZONE_HALF);
    }

    /** Include the full ordinary footing (station +/-3), not only its center. */
    public boolean suppresses(HighwayPlan plan, long globalStation) {
        double s = plan.localDistance(globalStation);
        return eligible && s + 3 >= pylonA - FOOTING_HALF && s - 3 <= pylonB + FOOTING_HALF;
    }

    public static BlockPos position(HighwayPlan plan, double station, int lateral, int y) {
        var p = plan.sample(station); var t = plan.tangent(station);
        return new BlockPos((int) Math.round(p.x() - t.z() * lateral), y,
                (int) Math.round(p.z() + t.x() * lateral));
    }

    public String description(HighwayPlan plan) {
        if (!eligible) return "landmarkEligible=false eligibilityReason=" + reason;
        return "landmarkEligible=true mainSpanStart=" + plan.globalStation(pylonA)
                + " mainSpanEnd=" + plan.globalStation(pylonB)
                + " pylonAStation=" + plan.globalStation(pylonA) + " pylonBStation=" + plan.globalStation(pylonB)
                + " localPylonStations=" + pylons() + " pylonZoneIntervals="
                + pylons().stream().map(p -> "[" + plan.globalStation(p - ZONE_HALF) + ","
                    + plan.globalStation(p + ZONE_HALF) + "]").toList()
                + " futureCableTowerAnchors=L:+/-13,S:P+/-4,Y:D+" + ANCHOR_HEIGHTS
                + " futureCableDeckAnchors=L:+/-13,S:P+/-(24+12*i),i:0..8,Y:roadY(S)+1";
    }

    public String offlineDescription(SeaBridgeGeometry bridge) {
        return description(bridge.plan())
                + " landmarkEnabled=" + (eligible ? "UNKNOWN" : "false")
                + " landmarkStatus=" + (eligible ? "ENGINEERING_NOT_SAMPLED" : "DISABLED")
                + " pylonFoundationStatus=NOT_SAMPLED pylonBaseY=UNKNOWN pylonTopY=UNKNOWN"
                + " fallbackReason=" + (eligible ? "NOT_EVALUATED" : reason)
                + " suppressedPierStations=" + (eligible ? "UNKNOWN" : "[]")
                + " retainedPierStations=" + (eligible ? "UNKNOWN" : bridge.pierStations().toString());
    }
}
