package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.List;

/** Bounded asymmetric V1 layouts, entirely chosen before building placement. */
public final class RuralRoadPlanner {
    private RuralRoadPlanner() { }
    public static List<RuralPlan.Road> plan(BlockPos center, Direction forward, RuralScaleTier tier, long seed) {
        var roads = new ArrayList<RuralPlan.Road>();
        RuralRoadType main = switch (tier) {
            case ISOLATED_HOMESTEAD -> RuralRoadType.FARM_TRACK;
            case FARMSTEAD -> RuralRoadType.SIDE;
            default -> RuralRoadType.MAIN;
        };
        int half = tier.roadLength() / 2;
        roads.add(new RuralRoadSegment(center.relative(forward, -half), center.relative(forward, half - 1), main).road(false));
        if (tier == RuralScaleTier.ISOLATED_HOMESTEAD || tier == RuralScaleTier.FARMSTEAD) return List.copyOf(roads);
        Direction side = forward.getClockWise();
        int depth = tier == RuralScaleTier.FULL_RURAL ? 28 : 20;
        int bend = 8 + (int)Math.floorMod(seed ^ center.asLong() ^ 0x524F4144L, 5);
        BlockPos turn = center.relative(side, depth);
        roads.add(new RuralRoadSegment(center, turn, RuralRoadType.SIDE).road(true));
        roads.add(new RuralRoadSegment(turn, turn.relative(forward, -bend), RuralRoadType.FARM_TRACK).road(true));
        if (tier == RuralScaleTier.FULL_RURAL) {
            BlockPos junction = center.relative(forward, -24);
            roads.add(new RuralRoadSegment(junction, junction.relative(side, -16), RuralRoadType.SIDE).road(true));
        }
        return List.copyOf(roads);
    }
}
