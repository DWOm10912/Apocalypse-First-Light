package com.antaurora.apofirstlight.worldgen.highway;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoublePredicate;

/** Resolves continuous solid-mountain tunnel spans from the pre-construction profile. */
public final class HighwayTunnelSpanResolver {
    public static final int TUNNEL_INTERIOR_HEIGHT = 8;
    public static final int TUNNEL_ROOF_LINING = 1;
    public static final int MIN_TUNNEL_ROCK_COVER = 8;
    /** Existing, conservative normal-tunnel policy. */
    public static final int MIN_TUNNEL_LENGTH = 40;
    public static final int PORTAL_DEPTH = 4;
    public static final int MIN_TRUE_TUNNEL_INTERIOR = 24;
    public static final int TUNNEL_GAP_CLOSE_MAX_SAMPLES = 0;

    // Deep-cut promotion is deliberately a separate policy. It does not lower
    // the normal tunnel threshold and still requires cross-section enclosure.
    public static final int DEEP_CUT_BOTH_SIDES_MIN = 11;
    public static final int DEEP_CUT_BOTH_SIDES_LENGTH = 24;
    public static final int DEEP_CUT_ASYMMETRIC_SIDE_MIN = 17;
    public static final int DEEP_CUT_ASYMMETRIC_OTHER_SIDE_MIN = 7;
    public static final int DEEP_CUT_ASYMMETRIC_LENGTH = 24;
    public static final int DEEP_CUT_STRONG_CROWN_COVER = 9;
    public static final int DEEP_CUT_SIDE_CONTACT_MIN = 6;
    public static final int DEEP_CUT_MIN_CROWN_COVER = 4;
    public static final int DEEP_CUT_MIN_TRUE_TUNNEL_INTERIOR = 16;
    public static final int DEEP_CUT_GAP_CLOSE_MAX_BLOCKS = 8;
    public static final int PORTAL_APPROACH_LENGTH = 8;

    private HighwayTunnelSpanResolver() {}

    public static int tunnelOuterCrownY(int roadY) {
        return roadY + TUNNEL_INTERIOR_HEIGHT + TUNNEL_ROOF_LINING;
    }

    public static Resolution resolve(List<HighwayProfile.Sample> samples) {
        return resolve(samples, ignored -> true);
    }

    public static Resolution resolve(List<HighwayProfile.Sample> samples,
                                     DoublePredicate stationAllowed) {
        NaturalHighwayRuntimeStats.tunnelResolverCall();
        long started = System.nanoTime();
        boolean[] normalCandidates = new boolean[samples.size()];
        boolean[] promotedCandidates = new boolean[samples.size()];
        boolean[] candidates = new boolean[samples.size()];
        int normalCandidateStations = 0;
        int promotedCandidateStations = 0;
        int deepCutStationsEvaluated = 0;
        int deepCutPromotionCandidates = 0;
        int deepCutRejectedTooOpen = 0;
        int deepCutRejectedLowCover = 0;
        int lowCoverRejected = 0;
        int sideExposureRejected = 0;
        int waterRejected = 0;
        int viaductRejected = 0;
        double[] normalCover = new double[samples.size()];
        double[] promotionCover = new double[samples.size()];

        for (int i = 0; i < samples.size(); i++) {
            HighwayProfile.Sample sample = samples.get(i);
            normalCover[i] = normalRockCover(sample);
            promotionCover[i] = deepCutCrownCover(sample);
            if (!stationAllowed.test(sample.distance())) continue;
            if (sample.mode() == HighwayTerrainMode.VIADUCT) {
                viaductRejected++;
                continue;
            }
            if (sample.water() || sample.waterColumnCount() > 0
                    || sample.leftSideWater() || sample.rightSideWater()) {
                waterRejected++;
                continue;
            }

            if (normalCover[i] < 0.0) {
                sideExposureRejected++;
            } else if (normalCover[i] < MIN_TUNNEL_ROCK_COVER) {
                lowCoverRejected++;
            } else {
                normalCandidates[i] = true;
                normalCandidateStations++;
            }
            candidates[i] = normalCandidates[i];

            if (sample.mode() != HighwayTerrainMode.CUT) continue;
            deepCutStationsEvaluated++;
            DeepCutSignal signal = deepCutSignal(sample, promotionCover[i]);
            if (signal.tooOpen()) {
                deepCutRejectedTooOpen++;
            } else if (signal.lowCover()) {
                deepCutRejectedLowCover++;
            } else if (signal.candidate() && !normalCandidates[i]) {
                promotedCandidates[i] = true;
                promotedCandidateStations++;
                deepCutPromotionCandidates++;
            }
            candidates[i] = normalCandidates[i] || promotedCandidates[i];
        }

        int deepCutGapClosures = closeDeepCutGaps(samples, candidates, promotedCandidates,
                stationAllowed, promotionCover);
        for (int i = 0; i < candidates.length; i++) {
            if (candidates[i] && !normalCandidates[i] && !promotedCandidates[i]) {
                promotedCandidates[i] = true;
                promotedCandidateStations++;
            }
        }

        List<IndexSpan> rawSpans = collectSpans(candidates);
        List<TunnelSpan> resolvedSpans = new ArrayList<>();
        int rejectedShortSpanStations = 0;
        int deepCutRejectedTooShort = 0;
        int qualifiedStations = 0;
        int deepCutPromotedStations = 0;
        int deepCutPromotedSpans = 0;
        int deepCutPortalAdjustments = 0;
        double minLength = Double.POSITIVE_INFINITY;
        double maxLength = 0.0;
        double totalLength = 0.0;
        double minCover = Double.POSITIVE_INFINITY;
        double maxCover = 0.0;
        double totalCover = 0.0;

        for (IndexSpan raw : rawSpans) {
            boolean promotedSpan = false;
            for (int i = raw.start(); i <= raw.end(); i++) promotedSpan |= promotedCandidates[i];
            double length = samples.get(raw.end()).distance() - samples.get(raw.start()).distance();
            double minimumLength = promotedSpan
                    ? Math.max(DEEP_CUT_BOTH_SIDES_LENGTH, DEEP_CUT_ASYMMETRIC_LENGTH)
                    : MIN_TUNNEL_LENGTH;
            double minimumInterior = promotedSpan
                    ? DEEP_CUT_MIN_TRUE_TUNNEL_INTERIOR : MIN_TRUE_TUNNEL_INTERIOR;
            double interiorLength = length - 2.0 * PORTAL_DEPTH;
            if (length < minimumLength || interiorLength < minimumInterior) {
                int rejected = raw.end() - raw.start() + 1;
                rejectedShortSpanStations += rejected;
                if (promotedSpan) deepCutRejectedTooShort += rejected;
                continue;
            }

            int resolvedStart = raw.start();
            int resolvedEnd = raw.end();
            // Move a portal at most one profile sample toward the open-cut
            // transition, but only when the first actual tunnel sample already
            // has the hard normal roof invariant.  This never creates a
            // shallow interior station or crosses water/node/viaduct space.
            if (promotedSpan && raw.start() > 0
                    && samples.get(raw.start()).distance() - samples.get(raw.start() - 1).distance()
                    <= PORTAL_APPROACH_LENGTH
                    && normalCover[raw.start()] >= MIN_TUNNEL_ROCK_COVER
                    && isPortalApproach(samples.get(raw.start() - 1), promotionCover[raw.start() - 1],
                    stationAllowed)) {
                resolvedStart--;
                deepCutPortalAdjustments++;
            }
            if (promotedSpan && raw.end() + 1 < samples.size()
                    && samples.get(raw.end() + 1).distance() - samples.get(raw.end()).distance()
                    <= PORTAL_APPROACH_LENGTH
                    && normalCover[raw.end()] >= MIN_TUNNEL_ROCK_COVER
                    && isPortalApproach(samples.get(raw.end() + 1), promotionCover[raw.end() + 1],
                    stationAllowed)) {
                resolvedEnd++;
                deepCutPortalAdjustments++;
            }

            double spanMinCover = Double.POSITIVE_INFINITY;
            double spanMaxCover = 0.0;
            double spanTotalCover = 0.0;
            for (int i = resolvedStart; i <= resolvedEnd; i++) {
                spanMinCover = Math.min(spanMinCover, normalCover[i]);
                spanMaxCover = Math.max(spanMaxCover, normalCover[i]);
                spanTotalCover += normalCover[i];
            }
            TunnelSpan span = new TunnelSpan(samples.get(resolvedStart).distance(),
                    samples.get(resolvedEnd).distance(), spanMinCover, spanMaxCover,
                    spanTotalCover / (resolvedEnd - resolvedStart + 1), promotedSpan);
            resolvedSpans.add(span);
            int qualified = resolvedEnd - resolvedStart + 1;
            qualifiedStations += qualified;
            if (promotedSpan) {
                deepCutPromotedSpans++;
                deepCutPromotedStations += qualified;
            }
            minLength = Math.min(minLength, span.length());
            maxLength = Math.max(maxLength, span.length());
            totalLength += span.length();
            minCover = Math.min(minCover, spanMinCover);
            maxCover = Math.max(maxCover, spanMaxCover);
            totalCover += span.averageRockCover();
        }

        int resolvedCount = resolvedSpans.size();
        int candidateSpanStations = rawSpans.stream()
                .mapToInt(raw -> raw.end() - raw.start() + 1).sum();
        Resolution result = new Resolution(List.copyOf(resolvedSpans),
                normalCandidateStations + promotedCandidateStations, candidateSpanStations,
                qualifiedStations, rejectedShortSpanStations, lowCoverRejected,
                sideExposureRejected, waterRejected, viaductRejected, rawSpans.size(),
                resolvedCount, deepCutGapClosures,
                resolvedCount == 0 ? 0.0 : minLength,
                resolvedCount == 0 ? 0.0 : maxLength,
                resolvedCount == 0 ? 0.0 : totalLength / resolvedCount,
                resolvedCount == 0 ? 0.0 : minCover,
                resolvedCount == 0 ? 0.0 : maxCover,
                resolvedCount == 0 ? 0.0 : totalCover / resolvedCount,
                normalCandidateStations, promotedCandidateStations, deepCutStationsEvaluated,
                deepCutPromotionCandidates, deepCutPromotedStations, deepCutPromotedSpans,
                deepCutRejectedTooShort, deepCutRejectedTooOpen, deepCutRejectedLowCover,
                deepCutGapClosures, deepCutPortalAdjustments, System.nanoTime() - started);
        NaturalHighwayRuntimeStats.tunnelResolver(System.nanoTime() - started);
        return result;
    }

    /** Cheap semantic-equivalent reject before allocating the exact resolver arrays. */
    public static boolean mightContainTunnel(List<HighwayProfile.Sample> samples,
                                             DoublePredicate stationAllowed) {
        for (HighwayProfile.Sample sample : samples) {
            if (!stationAllowed.test(sample.distance())) continue;
            if (sample.mode() == HighwayTerrainMode.VIADUCT) continue;
            if (sample.water() || sample.waterColumnCount() > 0
                    || sample.leftSideWater() || sample.rightSideWater()) continue;
            if (normalRockCover(sample) >= MIN_TUNNEL_ROCK_COVER
                    || (sample.mode() == HighwayTerrainMode.CUT
                    && deepCutSignal(sample, deepCutCrownCover(sample)).candidate())) return true;
        }
        return false;
    }

    public static Resolution empty() {
        return new Resolution(List.of(), 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L);
    }

    private static double normalRockCover(HighwayProfile.Sample sample) {
        return sample.terrainMinY() - 1.0 - tunnelOuterCrownY(sample.roadY());
    }

    private static double deepCutCrownCover(HighwayProfile.Sample sample) {
        // The minimum of the center and cross-section median avoids treating a
        // high single side wall as a roof, while not letting one low road edge
        // erase an otherwise enclosed mountain cut.
        int meaningfulRoofTop = Math.min(sample.centerTerrainY(), sample.terrainMedianY());
        return meaningfulRoofTop - 1.0 - tunnelOuterCrownY(sample.roadY());
    }

    private static DeepCutSignal deepCutSignal(HighwayProfile.Sample sample, double crownCover) {
        int leftDepth = Math.max(0, sample.leftTerrainY() - sample.roadY());
        int rightDepth = Math.max(0, sample.rightTerrainY() - sample.roadY());
        int shallow = Math.min(leftDepth, rightDepth);
        int deep = Math.max(leftDepth, rightDepth);
        boolean sideContact = shallow >= DEEP_CUT_SIDE_CONTACT_MIN;
        boolean bilateral = leftDepth >= DEEP_CUT_BOTH_SIDES_MIN
                && rightDepth >= DEEP_CUT_BOTH_SIDES_MIN;
        boolean asymmetric = deep >= DEEP_CUT_ASYMMETRIC_SIDE_MIN
                && shallow >= DEEP_CUT_ASYMMETRIC_OTHER_SIDE_MIN;
        boolean strongCrown = crownCover >= DEEP_CUT_STRONG_CROWN_COVER && sideContact;
        boolean geometry = sideContact && (bilateral || asymmetric || strongCrown);
        return new DeepCutSignal(geometry && crownCover >= DEEP_CUT_MIN_CROWN_COVER,
                !geometry, geometry && crownCover < DEEP_CUT_MIN_CROWN_COVER);
    }

    private static boolean isPortalApproach(HighwayProfile.Sample sample, double crownCover,
                                            DoublePredicate stationAllowed) {
        if (!stationAllowed.test(sample.distance()) || sample.mode() == HighwayTerrainMode.VIADUCT
                || sample.water() || sample.waterColumnCount() > 0
                || sample.leftSideWater() || sample.rightSideWater()) return false;
        int leftDepth = Math.max(0, sample.leftTerrainY() - sample.roadY());
        int rightDepth = Math.max(0, sample.rightTerrainY() - sample.roadY());
        return crownCover >= 0.0 && leftDepth >= 4 && rightDepth >= 4;
    }

    private static int closeDeepCutGaps(List<HighwayProfile.Sample> samples,
                                        boolean[] candidates, boolean[] promoted,
                                        DoublePredicate stationAllowed, double[] promotionCover) {
        int closures = 0;
        for (int start = 0; start < candidates.length;) {
            if (!candidates[start]) {
                start++;
                continue;
            }
            int end = start;
            while (end + 1 < candidates.length && candidates[end + 1]) end++;
            int next = end + 1;
            while (next < candidates.length && !candidates[next]) next++;
            if (next < candidates.length
                    && samples.get(next).distance() - samples.get(end).distance()
                    <= DEEP_CUT_GAP_CLOSE_MAX_BLOCKS + HighwayProfile.SAMPLE_SPACING
                    && next > end + 1
                    && gapIsEnclosed(samples, end + 1, next - 1, stationAllowed, promotionCover)) {
                for (int i = end + 1; i < next; i++) {
                    candidates[i] = true;
                    promoted[i] = true;
                }
                closures++;
                end = next;
            }
            start = end + 1;
        }
        return closures;
    }

    private static boolean gapIsEnclosed(List<HighwayProfile.Sample> samples, int start, int end,
                                         DoublePredicate stationAllowed, double[] promotionCover) {
        for (int i = start; i <= end; i++) {
            HighwayProfile.Sample sample = samples.get(i);
            if (!stationAllowed.test(sample.distance()) || sample.mode() == HighwayTerrainMode.VIADUCT
                    || sample.water() || sample.waterColumnCount() > 0
                    || sample.leftSideWater() || sample.rightSideWater()) return false;
            int left = Math.max(0, sample.leftTerrainY() - sample.roadY());
            int right = Math.max(0, sample.rightTerrainY() - sample.roadY());
            if (Math.min(left, right) < DEEP_CUT_SIDE_CONTACT_MIN
                    || promotionCover[i] < 0.0) return false;
        }
        return true;
    }

    private static List<IndexSpan> collectSpans(boolean[] candidates) {
        List<IndexSpan> spans = new ArrayList<>();
        for (int i = 0; i < candidates.length;) {
            if (!candidates[i]) {
                i++;
                continue;
            }
            int end = i;
            while (end + 1 < candidates.length && candidates[end + 1]) end++;
            spans.add(new IndexSpan(i, end));
            i = end + 1;
        }
        return spans;
    }

    private record DeepCutSignal(boolean candidate, boolean tooOpen, boolean lowCover) {}
    private record IndexSpan(int start, int end) {}

    public record TunnelSpan(double startStation, double endStation,
                             double minRockCover, double maxRockCover,
                             double averageRockCover, boolean promoted) {
        public boolean contains(double distance) {
            return distance >= startStation && distance <= endStation;
        }

        public boolean isPortal(double distance) {
            return distance <= startStation + PORTAL_DEPTH
                    || distance >= endStation - PORTAL_DEPTH;
        }

        public boolean isInterior(double distance) {
            return contains(distance) && !isPortal(distance);
        }

        public double length() {
            return endStation - startStation;
        }
    }

    public record Resolution(List<TunnelSpan> spans, int tunnelCandidateStations,
                             int tunnelCandidateSpanStations, int tunnelQualifiedStations,
                             int tunnelRejectedShortSpanStations,
                             int tunnelRejectedLowCoverStations,
                             int tunnelRejectedSideExposureStations,
                             int tunnelRejectedWaterStations,
                             int tunnelRejectedViaductStations,
                             int rawTunnelSpanCount, int resolvedTunnelSpanCount,
                             int tunnelGapClosures, double minTunnelLength,
                             double maxTunnelLength, double averageTunnelLength,
                             double minRockCoverObserved, double maxRockCoverObserved,
                             double averageRockCoverObserved,
                             int normalTunnelCandidateStations,
                             int promotedTunnelCandidateStations,
                             int deepCutStationsEvaluated,
                             int deepCutPromotionCandidates,
                             int deepCutPromotedStations,
                             int deepCutPromotedSpans,
                             int deepCutRejectedTooShort,
                             int deepCutRejectedTooOpen,
                             int deepCutRejectedLowCover,
                             int deepCutGapClosures,
                             int deepCutPortalAdjustments,
                             long deepCutEvaluationNanos) {
        public boolean isTunnel(double distance) {
            return spans.stream().anyMatch(span -> span.contains(distance));
        }

        public TunnelSpan spanAt(double distance) {
            return spans.stream().filter(span -> span.contains(distance)).findFirst().orElse(null);
        }
    }
}
