package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Samples a cross-section-aware, deliberately low-frequency road profile. */
public final class HighwayProfile {
    public static final int SAMPLE_SPACING = 8;
    public static final int MAX_CUT_DEPTH = 20;
    public static final int MAX_FILL_DEPTH = 7;
    public static final int ROAD_HALF_WIDTH = HighwayPlan.MAIN_WIDTH / 2;
    public static final int CROSS_SLOPE_EXTREME_THRESHOLD = 12;
    public static final double WATER_COVERAGE_BRIDGE_THRESHOLD = 0.30;

    private final HighwayPlan plan;
    private final List<Sample> samples;
    private final List<HighwayBridgeSpanResolver.Span> bridgeSpans;
    private final int rawViaductSamples;
    private final double rawViaductLength;
    private final int resolvedViaductStations;
    private final int bridgeGapClosures;
    private final int shortBridgeCandidatesRejected;
    private final int maxCrossSlopeObserved;
    private final double maxWaterCoverageObserved;
    private final boolean extremeCrossSectionEncountered;
    private final HighwayNodeConstraints nodeConstraints;

    private HighwayProfile(HighwayPlan plan, HighwayBridgeSpanResolver.Resolution resolution,
                           int maxCrossSlopeObserved, double maxWaterCoverageObserved,
                           boolean extremeCrossSectionEncountered,
                           HighwayNodeConstraints nodeConstraints) {
        this.plan = plan;
        this.samples = List.copyOf(resolution.samples());
        this.bridgeSpans = List.copyOf(resolution.spans());
        this.rawViaductSamples = resolution.rawViaductSamples();
        this.rawViaductLength = resolution.rawViaductLength();
        this.resolvedViaductStations = resolution.resolvedViaductStations();
        this.bridgeGapClosures = resolution.bridgeGapClosures();
        this.shortBridgeCandidatesRejected = resolution.shortBridgeCandidatesRejected();
        this.maxCrossSlopeObserved = maxCrossSlopeObserved;
        this.maxWaterCoverageObserved = maxWaterCoverageObserved;
        this.extremeCrossSectionEncountered = extremeCrossSectionEncountered;
        this.nodeConstraints = nodeConstraints;
    }

    public static HighwayProfile sample(ServerLevel level, HighwayPlan plan) {
        int count = Math.max(2, (int) Math.ceil(plan.length() / SAMPLE_SPACING) + 1);
        int[] terrainMedian = new int[count];
        int[] terrainMin = new int[count];
        int[] terrainMax = new int[count];
        int[] centerTerrain = new int[count];
        int[] waterColumns = new int[count];
        int[] solidColumns = new int[count];
        double[] distances = new double[count];
        int maxCrossSlope = 0;
        double maxWaterCoverage = 0.0;
        boolean extremeCrossSection = false;

        for (int i = 0; i < count; i++) {
            double distance = i == count - 1 ? plan.length() : Math.min(plan.length(), (double) i * SAMPLE_SPACING);
            HighwayPlan.Point point = plan.sample(distance);
            HighwayPlan.Tangent tangent = plan.tangent(distance);
            double rightX = -tangent.z();
            double rightZ = tangent.x();
            int[] columns = new int[HighwayPlan.MAIN_WIDTH];
            int water = 0;
            int solid = 0;
            for (int lateral = -ROAD_HALF_WIDTH; lateral <= ROAD_HALF_WIDTH; lateral++) {
                int x = (int) Math.round(point.x() + rightX * lateral);
                int z = (int) Math.round(point.z() + rightZ * lateral);
                int columnIndex = lateral + ROAD_HALF_WIDTH;
                int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                columns[columnIndex] = height;
                BlockPos top = new BlockPos(x, height - 1, z);
                if (!level.getFluidState(top).isEmpty()) water++;
                else if (!level.getBlockState(top).isAir()) solid++;
                if (lateral == 0) centerTerrain[i] = height;
            }
            int[] sorted = columns.clone();
            Arrays.sort(sorted);
            terrainMedian[i] = sorted[sorted.length / 2];
            terrainMin[i] = sorted[0];
            terrainMax[i] = sorted[sorted.length - 1];
            waterColumns[i] = water;
            solidColumns[i] = solid;
            distances[i] = distance;
            int crossSlope = terrainMax[i] - terrainMin[i];
            maxCrossSlope = Math.max(maxCrossSlope, crossSlope);
            double waterCoverage = water / (double) columns.length;
            maxWaterCoverage = Math.max(maxWaterCoverage, waterCoverage);
            extremeCrossSection |= crossSlope >= CROSS_SLOPE_EXTREME_THRESHOLD;
        }

        double[] smooth = new double[count];
        for (int i = 0; i < count; i++) {
            double weighted = 0.0;
            double weights = 0.0;
            for (int j = Math.max(0, i - 4); j <= Math.min(count - 1, i + 4); j++) {
                double weight = 5.0 - Math.abs(i - j);
                weighted += terrainMedian[j] * weight;
                weights += weight;
            }
            smooth[i] = weighted / weights;
        }

        int[] road = new int[count];
        road[0] = (int) Math.round(smooth[0]);
        for (int i = 1; i < count; i++) road[i] = clampAdjacent(road[i - 1], (int) Math.round(smooth[i]));
        for (int i = count - 2; i >= 0; i--) road[i] = clampAdjacent(road[i + 1], road[i]);

        List<Sample> rawSamples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            HighwayPlan.Point point = plan.sample(distances[i]);
            HighwayPlan.Tangent tangent = plan.tangent(distances[i]);
            double waterCoverage = waterColumns[i] / (double) HighwayPlan.MAIN_WIDTH;
            int cutDepthMax = Math.max(0, terrainMax[i] - road[i]);
            int fillDepthMax = Math.max(0, road[i] - terrainMin[i]);
            HighwayTerrainMode mode = mode(road[i] - terrainMedian[i], waterCoverage,
                    terrainMax[i] - terrainMin[i], cutDepthMax, fillDepthMax);
            rawSamples.add(new Sample(distances[i], point.x(), point.z(), tangent.x(), tangent.z(),
                    terrainMedian[i], road[i], mode, mode, waterColumns[i] > 0, terrainMin[i], terrainMax[i],
                    terrainMedian[i], centerTerrain[i], waterColumns[i], solidColumns[i],
                    terrainMax[i] - terrainMin[i], waterCoverage, cutDepthMax, fillDepthMax));
        }

        HighwayBridgeSpanResolver.Resolution resolution = HighwayBridgeSpanResolver.resolve(rawSamples);
        return new HighwayProfile(plan, resolution, maxCrossSlope, maxWaterCoverage,
                extremeCrossSection, HighwayNodeConstraints.NONE);
    }

    /** Builds a random-access global profile from pre-decoration terrain rather than chunk-local state. */
    public static HighwayProfile sampleNatural(HighwayPlan plan,
                                               PrimaryHighwayNetwork.Corridor corridor,
                                               HighwayTerrainSampler terrain,
                                               HighwayNodeConstraints nodeConstraints) {
        NaturalHighwayRuntimeStats.profileBuildCall();
        long profileStarted = System.nanoTime();
        int count = Math.max(2, (int) Math.ceil(plan.length() / SAMPLE_SPACING) + 1);
        List<Sample> rawSamples = new ArrayList<>(count);
        int maxCrossSlope = 0;
        double maxWaterCoverage = 0.0;
        boolean extremeCrossSection = false;
        boolean bridgeCandidate = false;
        for (int i = 0; i < count; i++) {
            double distance = i == count - 1 ? plan.length()
                    : Math.min(plan.length(), (double) i * SAMPLE_SPACING);
            double globalStation = plan.globalStation(distance);
            int roadY = terrain.globalRoadY(corridor, globalStation);
            roadY = nodeConstraints.adjustRoadY(globalStation, roadY);
            HighwayTerrainSampler.CrossSection cross = terrain.crossSection(corridor, globalStation);
            int crossSlope = cross.maxY() - cross.minY();
            double waterCoverage = cross.waterColumns() / (double) HighwayPlan.MAIN_WIDTH;
            int cutDepthMax = Math.max(0, cross.maxY() - roadY);
            int fillDepthMax = Math.max(0, roadY - cross.minY());
            HighwayTerrainMode rawMode = mode(roadY - cross.medianY(), waterCoverage,
                    crossSlope, cutDepthMax, fillDepthMax);
            rawMode = nodeConstraints.overrideMode(globalStation, rawMode);
            bridgeCandidate |= rawMode == HighwayTerrainMode.VIADUCT;
            HighwayPlan.Point point = plan.sample(distance);
            HighwayPlan.Tangent tangent = plan.tangent(distance);
            rawSamples.add(new Sample(distance, point.x(), point.z(), tangent.x(), tangent.z(),
                    cross.medianY(), roadY, rawMode, rawMode, cross.waterColumns() > 0,
                    cross.minY(), cross.maxY(), cross.medianY(), cross.centerY(),
                    cross.waterColumns(), cross.solidColumns(), crossSlope, waterCoverage,
                    cutDepthMax, fillDepthMax));
            maxCrossSlope = Math.max(maxCrossSlope, crossSlope);
            maxWaterCoverage = Math.max(maxWaterCoverage, waterCoverage);
            extremeCrossSection |= crossSlope >= CROSS_SLOPE_EXTREME_THRESHOLD;
        }
        HighwayBridgeSpanResolver.Resolution resolution = bridgeCandidate
                ? HighwayBridgeSpanResolver.resolve(rawSamples)
                : HighwayBridgeSpanResolver.noCandidates(rawSamples);
        HighwayProfile result = new HighwayProfile(plan, resolution, maxCrossSlope, maxWaterCoverage,
                extremeCrossSection, nodeConstraints);
        NaturalHighwayRuntimeStats.profileBuild(System.nanoTime() - profileStarted);
        return result;
    }

    private static int clampAdjacent(int previous, int target) {
        return Math.max(previous - 1, Math.min(previous + 1, target));
    }

    private static HighwayTerrainMode mode(int delta, double waterCoverage, int crossSlope,
                                           int cutDepthMax, int fillDepthMax) {
        if (waterCoverage >= WATER_COVERAGE_BRIDGE_THRESHOLD
                || delta >= 8
                || (crossSlope >= CROSS_SLOPE_EXTREME_THRESHOLD && Math.max(cutDepthMax, fillDepthMax) >= 8)) {
            return HighwayTerrainMode.VIADUCT;
        }
        if (delta >= 3) return HighwayTerrainMode.FILL;
        if (delta <= -3) return HighwayTerrainMode.CUT;
        return HighwayTerrainMode.GROUND;
    }

    public HighwayPlan plan() { return plan; }
    public List<Sample> samples() { return samples; }
    public List<HighwayBridgeSpanResolver.Span> bridgeSpans() { return bridgeSpans; }
    public int rawViaductSamples() { return rawViaductSamples; }
    public double rawViaductLength() { return rawViaductLength; }
    public int resolvedViaductStations() { return resolvedViaductStations; }
    public int bridgeGapClosures() { return bridgeGapClosures; }
    public int shortBridgeCandidatesRejected() { return shortBridgeCandidatesRejected; }
    public int maxCrossSlopeObserved() { return maxCrossSlopeObserved; }
    public double maxWaterCoverageObserved() { return maxWaterCoverageObserved; }
    public boolean extremeCrossSectionEncountered() { return extremeCrossSectionEncountered; }
    public boolean tunnelAllowed(double localDistance) {
        return nodeConstraints.tunnelAllowed(plan.globalStation(localDistance));
    }
    public boolean pierAllowed(double localDistance) {
        return nodeConstraints.pierAllowed(plan.globalStation(localDistance));
    }

    public Sample sampleAt(double distance) {
        if (distance <= samples.get(0).distance()) return samples.get(0);
        if (distance >= samples.get(samples.size() - 1).distance()) return samples.get(samples.size() - 1);
        int high = 1;
        while (high < samples.size() && samples.get(high).distance() < distance) high++;
        Sample a = samples.get(high - 1);
        Sample b = samples.get(high);
        double fraction = (distance - a.distance()) / (b.distance() - a.distance());
        HighwayPlan.Point point = plan.sample(distance);
        HighwayPlan.Tangent tangent = plan.tangent(distance);
        return new Sample(distance, point.x(), point.z(), tangent.x(), tangent.z(),
                interpolate(a.terrainY(), b.terrainY(), fraction), interpolate(a.roadY(), b.roadY(), fraction),
                a.rawMode() == HighwayTerrainMode.VIADUCT || b.rawMode() == HighwayTerrainMode.VIADUCT
                        ? HighwayTerrainMode.VIADUCT : a.rawMode(),
                a.mode() == HighwayTerrainMode.VIADUCT || b.mode() == HighwayTerrainMode.VIADUCT
                        ? HighwayTerrainMode.VIADUCT : a.mode(),
                a.water() || b.water(), interpolate(a.terrainMinY(), b.terrainMinY(), fraction),
                interpolate(a.terrainMaxY(), b.terrainMaxY(), fraction),
                interpolate(a.terrainMedianY(), b.terrainMedianY(), fraction),
                interpolate(a.centerTerrainY(), b.centerTerrainY(), fraction),
                Math.max(a.waterColumnCount(), b.waterColumnCount()),
                Math.max(a.solidColumnCount(), b.solidColumnCount()),
                interpolate(a.crossSlope(), b.crossSlope(), fraction),
                a.waterCoverage() + (b.waterCoverage() - a.waterCoverage()) * fraction,
                interpolate(a.cutDepthMax(), b.cutDepthMax(), fraction),
                interpolate(a.fillDepthMax(), b.fillDepthMax(), fraction));
    }

    public boolean isWithinResolvedBridgeSpan(double distance) {
        return bridgeSpans.stream().anyMatch(span -> span.contains(distance));
    }

    public HighwayBridgeSpanResolver.Span spanAt(double distance) {
        return bridgeSpans.stream().filter(span -> span.contains(distance)).findFirst().orElse(null);
    }

    private static int interpolate(int a, int b, double fraction) {
        return (int) Math.round(a + (b - a) * fraction);
    }

    public int minRoadY() { return samples.stream().mapToInt(Sample::roadY).min().orElse(0); }
    public int maxRoadY() { return samples.stream().mapToInt(Sample::roadY).max().orElse(0); }
    public int observedMaxGrade() {
        int max = 0;
        for (int i = 1; i < samples.size(); i++) max = Math.max(max, Math.abs(samples.get(i).roadY() - samples.get(i - 1).roadY()));
        return max;
    }

    public record Sample(double distance, double x, double z, double tangentX, double tangentZ,
                         int terrainY, int roadY, HighwayTerrainMode rawMode, HighwayTerrainMode mode,
                         boolean water, int terrainMinY, int terrainMaxY, int terrainMedianY,
                         int centerTerrainY, int waterColumnCount, int solidColumnCount, int crossSlope,
                         double waterCoverage, int cutDepthMax, int fillDepthMax) {
        public Sample withMode(HighwayTerrainMode resolvedMode) {
            return new Sample(distance, x, z, tangentX, tangentZ, terrainY, roadY, rawMode, resolvedMode,
                    water, terrainMinY, terrainMaxY, terrainMedianY, centerTerrainY, waterColumnCount,
                    solidColumnCount, crossSlope, waterCoverage, cutDepthMax, fillDepthMax);
        }
    }
}
