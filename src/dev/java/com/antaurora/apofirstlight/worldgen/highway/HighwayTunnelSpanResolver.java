package com.antaurora.apofirstlight.worldgen.highway;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoublePredicate;

/** Resolves conservative, continuous solid-mountain tunnel spans from the pre-construction profile. */
public final class HighwayTunnelSpanResolver {
    public static final int TUNNEL_INTERIOR_HEIGHT = 8;
    public static final int TUNNEL_ROOF_LINING = 1;
    public static final int MIN_TUNNEL_ROCK_COVER = 8;
    public static final int MIN_TUNNEL_LENGTH = 40;
    public static final int PORTAL_DEPTH = 4;
    public static final int MIN_TRUE_TUNNEL_INTERIOR = 24;
    public static final int TUNNEL_GAP_CLOSE_MAX_SAMPLES = 0;

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
        boolean[] candidates = new boolean[samples.size()];
        int candidateStations = 0;
        int lowCoverRejected = 0;
        int sideExposureRejected = 0;
        int waterRejected = 0;
        int viaductRejected = 0;
        double[] cover = new double[samples.size()];

        for (int i = 0; i < samples.size(); i++) {
            HighwayProfile.Sample sample = samples.get(i);
            cover[i] = sample.terrainMinY() - 1.0 - tunnelOuterCrownY(sample.roadY());
            if (!stationAllowed.test(sample.distance())) {
                continue;
            }
            if (sample.mode() == HighwayTerrainMode.VIADUCT) {
                viaductRejected++;
                continue;
            }
            if (sample.water() || sample.waterColumnCount() > 0) {
                waterRejected++;
                continue;
            }
            if (cover[i] < 0.0) {
                sideExposureRejected++;
                continue;
            }
            if (cover[i] < MIN_TUNNEL_ROCK_COVER) {
                lowCoverRejected++;
                continue;
            }
            candidates[i] = true;
            candidateStations++;
        }

        List<IndexSpan> rawSpans = collectSpans(candidates);
        List<TunnelSpan> resolvedSpans = new ArrayList<>();
        int rejectedShortSpanStations = 0;
        int qualifiedStations = 0;
        double minLength = Double.POSITIVE_INFINITY;
        double maxLength = 0.0;
        double totalLength = 0.0;
        double minCover = Double.POSITIVE_INFINITY;
        double maxCover = 0.0;
        double totalCover = 0.0;

        for (IndexSpan raw : rawSpans) {
            double length = samples.get(raw.end()).distance() - samples.get(raw.start()).distance();
            double interiorLength = length - 2.0 * PORTAL_DEPTH;
            if (length < MIN_TUNNEL_LENGTH || interiorLength < MIN_TRUE_TUNNEL_INTERIOR) {
                rejectedShortSpanStations += raw.end() - raw.start() + 1;
                continue;
            }

            double spanMinCover = Double.POSITIVE_INFINITY;
            double spanMaxCover = 0.0;
            double spanTotalCover = 0.0;
            for (int i = raw.start(); i <= raw.end(); i++) {
                spanMinCover = Math.min(spanMinCover, cover[i]);
                spanMaxCover = Math.max(spanMaxCover, cover[i]);
                spanTotalCover += cover[i];
            }
            TunnelSpan span = new TunnelSpan(samples.get(raw.start()).distance(),
                    samples.get(raw.end()).distance(), spanMinCover, spanMaxCover,
                    spanTotalCover / (raw.end() - raw.start() + 1));
            resolvedSpans.add(span);
            qualifiedStations += raw.end() - raw.start() + 1;
            minLength = Math.min(minLength, length);
            maxLength = Math.max(maxLength, length);
            totalLength += length;
            minCover = Math.min(minCover, spanMinCover);
            maxCover = Math.max(maxCover, spanMaxCover);
            totalCover += spanTotalCover / (raw.end() - raw.start() + 1);
        }

        int resolvedCount = resolvedSpans.size();
        int candidateSpanStations = rawSpans.stream()
                .mapToInt(raw -> raw.end() - raw.start() + 1).sum();
        Resolution result = new Resolution(List.copyOf(resolvedSpans), candidateStations,
                candidateSpanStations, qualifiedStations,
                rejectedShortSpanStations, lowCoverRejected, sideExposureRejected,
                waterRejected, viaductRejected, rawSpans.size(), resolvedCount, 0,
                resolvedCount == 0 ? 0.0 : minLength,
                resolvedCount == 0 ? 0.0 : maxLength,
                resolvedCount == 0 ? 0.0 : totalLength / resolvedCount,
                resolvedCount == 0 ? 0.0 : minCover,
                resolvedCount == 0 ? 0.0 : maxCover,
                resolvedCount == 0 ? 0.0 : totalCover / resolvedCount);
        NaturalHighwayRuntimeStats.tunnelResolver(System.nanoTime() - started);
        return result;
    }

    /** Cheap semantic-equivalent reject before allocating the exact resolver arrays. */
    public static boolean mightContainTunnel(List<HighwayProfile.Sample> samples,
                                             DoublePredicate stationAllowed) {
        for (HighwayProfile.Sample sample : samples) {
            if (!stationAllowed.test(sample.distance())) continue;
            if (sample.mode() == HighwayTerrainMode.VIADUCT) continue;
            if (sample.water() || sample.waterColumnCount() > 0) continue;
            double cover = sample.terrainMinY() - 1.0 - tunnelOuterCrownY(sample.roadY());
            if (cover >= MIN_TUNNEL_ROCK_COVER) return true;
        }
        return false;
    }

    public static Resolution empty() {
        return new Resolution(List.of(), 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
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

    public record TunnelSpan(double startStation, double endStation,
                             double minRockCover, double maxRockCover,
                             double averageRockCover) {
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
                             double averageRockCoverObserved) {
        public boolean isTunnel(double distance) {
            return spans.stream().anyMatch(span -> span.contains(distance));
        }

        public TunnelSpan spanAt(double distance) {
            return spans.stream().filter(span -> span.contains(distance)).findFirst().orElse(null);
        }
    }

    private record IndexSpan(int start, int end) {}
}
