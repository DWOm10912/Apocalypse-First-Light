package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;

/** Deterministic natural-generation scale tiers. Weights are intentionally centralized for V1 tuning. */
public enum RuralScaleTier {
    ISOLATED_HOMESTEAD(40, 1, 1, 1, 1, 18, 3, "DRIVEWAY"),
    FARMSTEAD(30, 2, 4, 1, 2, 32, 3, "FARM_LANE"),
    RURAL_CLUSTER(22, 4, 6, 1, 3, 56, 5, "RURAL_ROAD"),
    FULL_RURAL(8, 6, 8, 1, 3, RuralGenerator.ROAD_LENGTH, RuralGenerator.ROAD_WIDTH, "MAIN_T_BRANCH");

    private final int weight;
    private final int minBuildings;
    private final int maxBuildings;
    private final int minFarms;
    private final int maxFarms;
    private final int roadLength;
    private final int roadWidth;
    private final String roadLayout;

    RuralScaleTier(int weight, int minBuildings, int maxBuildings, int minFarms, int maxFarms,
                   int roadLength, int roadWidth, String roadLayout) {
        this.weight = weight;
        this.minBuildings = minBuildings;
        this.maxBuildings = maxBuildings;
        this.minFarms = minFarms;
        this.maxFarms = maxFarms;
        this.roadLength = roadLength;
        this.roadWidth = roadWidth;
        this.roadLayout = roadLayout;
    }

    public int weight() { return weight; }
    public int minBuildings() { return minBuildings; }
    public int maxBuildings() { return maxBuildings; }
    public int minFarms() { return minFarms; }
    public int maxFarms() { return maxFarms; }
    public int roadLength() { return roadLength; }
    public int roadWidth() { return roadWidth; }
    public String roadLayout() { return roadLayout; }

    public int targetBuildings(long seed, BlockPos center) {
        return minBuildings + (int) Math.floorMod(seed ^ center.asLong() ^ 0x525552414C544945L,
                (long) (maxBuildings - minBuildings + 1));
    }

    public int targetFarms(long seed, BlockPos center) {
        return minFarms + (int) Math.floorMod(seed ^ center.asLong() ^ 0x4641524D544945L,
                (long) (maxFarms - minFarms + 1));
    }

    public static RuralScaleTier choose(long seed, BlockPos center) {
        int total = 0;
        for (RuralScaleTier tier : values()) total += tier.weight;
        int roll = (int) Math.floorMod(seed ^ center.asLong() ^ 0x525552414C534341L, (long) total);
        for (RuralScaleTier tier : values()) {
            roll -= tier.weight;
            if (roll < 0) return tier;
        }
        return FULL_RURAL;
    }
}
