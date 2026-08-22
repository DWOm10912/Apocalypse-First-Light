package com.antaurora.apofirstlight.worldgen.highway;

import java.util.ArrayList;
import java.util.List;

/** Resolves deterministic, continuous bridge intervals from raw profile samples. */
public final class HighwayBridgeSpanResolver {
    public static final int MAX_BRIDGE_GAP_SAMPLES = 2;
    public static final int MIN_BRIDGE_SPAN_LENGTH = 24;
    public static final int BRIDGE_APPROACH_PADDING = 8;

    private HighwayBridgeSpanResolver() {}

    public static Resolution resolve(List<HighwayProfile.Sample> rawSamples) {
        boolean[] candidates = new boolean[rawSamples.size()];
        int rawViaductSamples = 0;
        double rawViaductLength = 0.0;
        for (int i = 0; i < rawSamples.size(); i++) {
            candidates[i] = rawSamples.get(i).rawMode() == HighwayTerrainMode.VIADUCT;
            if (candidates[i]) rawViaductSamples++;
        }
        for (int i = 0; i < candidates.length; i++) {
            if (!candidates[i]) continue;
            int end = i;
            while (end + 1 < candidates.length && candidates[end + 1]) end++;
            rawViaductLength += rawSamples.get(end).distance() - rawSamples.get(i).distance();
            i = end;
        }

        int bridgeGapClosures = 0;
        for (int i = 1; i < candidates.length - 1;) {
            if (candidates[i]) {
                i++;
                continue;
            }
            int gapStart = i;
            while (i < candidates.length && !candidates[i]) i++;
            int gapEnd = i - 1;
            int gapLength = gapEnd - gapStart + 1;
            if (gapLength <= MAX_BRIDGE_GAP_SAMPLES && gapStart > 0 && i < candidates.length
                    && candidates[gapStart - 1] && candidates[i]) {
                for (int gap = gapStart; gap <= gapEnd; gap++) candidates[gap] = true;
                bridgeGapClosures += gapLength;
            }
        }

        boolean[] resolved = new boolean[candidates.length];
        int shortBridgeCandidatesRejected = 0;
        for (int i = 0; i < candidates.length;) {
            if (!candidates[i]) {
                i++;
                continue;
            }
            int end = i;
            while (end + 1 < candidates.length && candidates[end + 1]) end++;
            double length = rawSamples.get(end).distance() - rawSamples.get(i).distance();
            if (length < MIN_BRIDGE_SPAN_LENGTH) {
                shortBridgeCandidatesRejected++;
                i = end + 1;
                continue;
            }

            double start = Math.max(0.0, rawSamples.get(i).distance() - BRIDGE_APPROACH_PADDING);
            double finish = Math.min(rawSamples.get(rawSamples.size() - 1).distance(),
                    rawSamples.get(end).distance() + BRIDGE_APPROACH_PADDING);
            int resolvedStart = i;
            while (resolvedStart > 0 && rawSamples.get(resolvedStart - 1).distance() >= start) resolvedStart--;
            int resolvedEnd = end;
            while (resolvedEnd + 1 < rawSamples.size()
                    && rawSamples.get(resolvedEnd + 1).distance() <= finish) resolvedEnd++;
            for (int sample = resolvedStart; sample <= resolvedEnd; sample++) resolved[sample] = true;
            i = end + 1;
        }

        List<HighwayProfile.Sample> resultSamples = new ArrayList<>(rawSamples.size());
        for (int i = 0; i < rawSamples.size(); i++) {
            resultSamples.add(rawSamples.get(i).withMode(
                    resolved[i] ? HighwayTerrainMode.VIADUCT : rawSamples.get(i).rawMode()));
        }

        List<Span> spans = new ArrayList<>();
        int resolvedViaductStations = 0;
        for (int i = 0; i < resolved.length;) {
            if (!resolved[i]) {
                i++;
                continue;
            }
            int end = i;
            while (end + 1 < resolved.length && resolved[end + 1]) end++;
            resolvedViaductStations += end - i + 1;
            spans.add(new Span(rawSamples.get(i).distance(), rawSamples.get(end).distance()));
            i = end + 1;
        }

        return new Resolution(resultSamples, spans, rawViaductSamples, rawViaductLength,
                resolvedViaductStations, bridgeGapClosures, shortBridgeCandidatesRejected);
    }

    public record Span(double startStation, double endStation) {
        public boolean contains(double station) {
            return station >= startStation && station <= endStation;
        }
    }

    public record Resolution(List<HighwayProfile.Sample> samples, List<Span> spans,
                             int rawViaductSamples, double rawViaductLength,
                             int resolvedViaductStations, int bridgeGapClosures,
                             int shortBridgeCandidatesRejected) {}
}
