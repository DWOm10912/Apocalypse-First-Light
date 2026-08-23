package com.antaurora.apofirstlight.worldgen.highway;

import java.util.List;

/** Validates cached segment overlap without forcing construction of a neighbor. */
public final class NaturalHighwaySeamValidator {
    private NaturalHighwaySeamValidator() {}

    public static void validate(CorridorEngineeringSegment left, CorridorEngineeringSegment right) {
        if (!left.corridor().equals(right.corridor())
                || left.segmentIndex() + 1 != right.segmentIndex()) return;
        boolean anyMismatch = false;
        for (double globalStation : new double[] {left.coreEndStation(), right.coreStartStation()}) {
            double leftDistance = left.localDistance(globalStation);
            double rightDistance = right.localDistance(globalStation);
            if (Math.abs(left.plan().globalStation(leftDistance) - globalStation) > 0.0001
                    || Math.abs(right.plan().globalStation(rightDistance) - globalStation) > 0.0001) {
                NaturalHighwayRuntimeStats.stationMismatch();
                anyMismatch = true;
            }
            HighwayProfile.Sample a = left.profile().sampleAt(leftDistance);
            HighwayProfile.Sample b = right.profile().sampleAt(rightDistance);
            if (a.roadY() != b.roadY() || a.mode() != b.mode()) {
                NaturalHighwayRuntimeStats.profileMismatch();
                anyMismatch = true;
            }
            double leftGlobal = left.plan().globalStation(leftDistance);
            double rightGlobal = right.plan().globalStation(rightDistance);
            if (HighwayCorridor.laneDividerPhase(leftGlobal)
                    != HighwayCorridor.laneDividerPhase(rightGlobal)) {
                NaturalHighwayRuntimeStats.markingMismatch();
                anyMismatch = true;
            }
            if (left.profile().isWithinResolvedBridgeSpan(leftDistance)
                    != right.profile().isWithinResolvedBridgeSpan(rightDistance)) {
                NaturalHighwayRuntimeStats.bridgeMismatch();
                anyMismatch = true;
            }
            if (left.engineeredCorridor().isTunnelStation(leftDistance)
                    != right.engineeredCorridor().isTunnelStation(rightDistance)) {
                NaturalHighwayRuntimeStats.tunnelMismatch();
                anyMismatch = true;
            }
            if (!affectingNodeIds(left, globalStation).equals(affectingNodeIds(right, globalStation))) {
                anyMismatch = true;
            }
        }
        if (anyMismatch) NaturalHighwayRuntimeStats.engineeringSegmentBoundaryMismatch();
    }

    private static List<String> affectingNodeIds(CorridorEngineeringSegment segment,
                                                  double globalStation) {
        return segment.nodes().stream()
                .filter(node -> node.affects(segment.corridor().orientation(), globalStation))
                .map(InterstateInterchangeNode::id)
                .sorted()
                .toList();
    }
}
