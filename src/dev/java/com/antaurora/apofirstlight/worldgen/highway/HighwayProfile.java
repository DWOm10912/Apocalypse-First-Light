package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/** Samples terrain and creates a deliberately low-frequency road vertical profile. */
public final class HighwayProfile {
    public static final int SAMPLE_SPACING = 8;
    public static final int MAX_CUT_DEPTH = 20;
    public static final int MAX_FILL_DEPTH = 7;

    private final HighwayPlan plan;
    private final List<Sample> samples;

    private HighwayProfile(HighwayPlan plan, List<Sample> samples) {
        this.plan = plan;
        this.samples = List.copyOf(samples);
    }

    public static HighwayProfile sample(ServerLevel level, HighwayPlan plan) {
        int count = Math.max(2, (int) Math.ceil(plan.length() / SAMPLE_SPACING) + 1);
        int[] terrain = new int[count];
        boolean[] water = new boolean[count];
        double[] distances = new double[count];
        for (int i = 0; i < count; i++) {
            double distance = i == count - 1 ? plan.length() : Math.min(plan.length(), (double) i * SAMPLE_SPACING);
            HighwayPlan.Point point = plan.sample(distance);
            int x = (int) Math.round(point.x());
            int z = (int) Math.round(point.z());
            int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            terrain[i] = height;
            water[i] = !level.getFluidState(new BlockPos(x, height - 1, z)).isEmpty();
            distances[i] = distance;
        }
        double[] smooth = new double[count];
        for (int i = 0; i < count; i++) {
            double weighted = 0.0;
            double weights = 0.0;
            for (int j = Math.max(0, i - 4); j <= Math.min(count - 1, i + 4); j++) {
                double weight = 5.0 - Math.abs(i - j);
                weighted += terrain[j] * weight;
                weights += weight;
            }
            smooth[i] = weighted / weights;
        }
        int[] road = new int[count];
        road[0] = (int) Math.round(smooth[0]);
        for (int i = 1; i < count; i++) road[i] = clampAdjacent(road[i - 1], (int) Math.round(smooth[i]));
        for (int i = count - 2; i >= 0; i--) road[i] = clampAdjacent(road[i + 1], road[i]);

        List<Sample> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            HighwayPlan.Point point = plan.sample(distances[i]);
            HighwayPlan.Tangent tangent = plan.tangent(distances[i]);
            int delta = road[i] - terrain[i];
            HighwayTerrainMode mode = mode(delta, water[i]);
            result.add(new Sample(distances[i], point.x(), point.z(), tangent.x(), tangent.z(), terrain[i], road[i], mode, water[i]));
        }
        return new HighwayProfile(plan, result);
    }

    private static int clampAdjacent(int previous, int target) { return Math.max(previous - 1, Math.min(previous + 1, target)); }
    private static HighwayTerrainMode mode(int delta, boolean water) {
        if (water || delta >= 8) return HighwayTerrainMode.VIADUCT;
        if (delta >= 3) return HighwayTerrainMode.FILL;
        if (delta <= -3) return HighwayTerrainMode.CUT;
        return HighwayTerrainMode.GROUND;
    }

    public HighwayPlan plan() { return plan; }
    public List<Sample> samples() { return samples; }
    public Sample sampleAt(double distance) {
        if (distance <= samples.get(0).distance()) return samples.get(0);
        if (distance >= samples.get(samples.size() - 1).distance()) return samples.get(samples.size() - 1);
        int high = 1;
        while (high < samples.size() && samples.get(high).distance() < distance) high++;
        Sample a = samples.get(high - 1);
        Sample b = samples.get(high);
        double fraction = (distance - a.distance()) / (b.distance() - a.distance());
        return new Sample(distance, plan.sample(distance).x(), plan.sample(distance).z(),
                plan.tangent(distance).x(), plan.tangent(distance).z(),
                (int) Math.round(a.terrainY() + (b.terrainY() - a.terrainY()) * fraction),
                (int) Math.round(a.roadY() + (b.roadY() - a.roadY()) * fraction),
                a.mode() == HighwayTerrainMode.VIADUCT || b.mode() == HighwayTerrainMode.VIADUCT
                        ? HighwayTerrainMode.VIADUCT : a.mode(), a.water() || b.water());
    }
    public int minRoadY() { return samples.stream().mapToInt(Sample::roadY).min().orElse(0); }
    public int maxRoadY() { return samples.stream().mapToInt(Sample::roadY).max().orElse(0); }
    public int observedMaxGrade() {
        int max = 0;
        for (int i = 1; i < samples.size(); i++) max = Math.max(max, Math.abs(samples.get(i).roadY() - samples.get(i - 1).roadY()));
        return max;
    }

    public record Sample(double distance, double x, double z, double tangentX, double tangentZ,
                         int terrainY, int roadY, HighwayTerrainMode mode, boolean water) {}
}
