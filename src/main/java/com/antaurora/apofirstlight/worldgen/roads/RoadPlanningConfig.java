package com.antaurora.apofirstlight.worldgen.roads;

/** Explicit, bounded inputs included in plan identity. No hidden mutable global configuration. */
public record RoadPlanningConfig(int candidateSize, int networkSize, int attempts, int maxEdges,
                                 int maxLots, int terrainBudget, int sampleStep, int maxCutFill) {
    public static final RoadPlanningConfig DEFAULT = new RoadPlanningConfig(512,384,4,64,64,8192,8,3);
    public RoadPlanningConfig {
        if (candidateSize < 512 || candidateSize > 1024 || candidateSize % 2 != 0
                || networkSize < 336 || networkSize > candidateSize || networkSize % 2 != 0
                || attempts < 1 || attempts > 4 || maxEdges < 1 || maxEdges > 64
                || maxLots < 1 || maxLots > 64 || terrainBudget < 1 || terrainBudget > 8192
                || sampleStep < 1 || sampleStep > 8 || maxCutFill < 0 || maxCutFill > 3)
            throw new IllegalArgumentException("Road V1-A budget outside supported limits");
    }
}
