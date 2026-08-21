package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.registry.AflBiomes;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import com.antaurora.apofirstlight.world.bunker.BunkerSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** DEV-only, plan-before-apply settlement road skeleton. No structures are placed. */
public final class SettlementPrototype {
    public static final int MAIN_ROAD_WIDTH = 13;
    public static final int LOCAL_ROAD_WIDTH = 9;
    public static final int RESIDENTIAL_LOT_WIDTH = 28;
    public static final int RESIDENTIAL_LOT_DEPTH = 32;
    public static final int COMMERCIAL_LOT_WIDTH = 32;
    public static final int COMMERCIAL_LOT_DEPTH = 48;
    public static final int MAIN_CLEAR_MARGIN = 3;
    public static final int LOCAL_CLEAR_MARGIN = 2;
    public static final int LOT_CLEAR_MARGIN = 2;
    public static final int MAX_SETTLEMENT_RELIEF = 10;
    private static final int MAIN_HALF_LENGTH = 112;
    private static final int STUB_LENGTH = 80;
    private static final int MIN_STARTUP_DISTANCE = 600;
    private static final int MAX_EFFECTIVE_RELIEF = 12;
    private static final double MAX_OUTLIER_RATIO = 0.15D;
    private static final int MAX_ROAD_LOCAL_DELTA = 2;
    private static final int MAX_RESIDENTIAL_LOT_RELIEF = 5;
    private static final int MAX_COMMERCIAL_LOT_RELIEF = 4;

    private SettlementPrototype() {}

    public static Result generateHere(ServerLevel level, BlockPos playerPos) {
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD)
            return Result.failure("OVERWORLD_REQUIRED");
        if (level.getBiome(playerPos).is(AflBiomes.IRRADIATED_WOODLAND) == false)
            return Result.failure("PLAYER_NOT_IN_IRRADIATED_WOODLAND");
        BlockPos anchor = new BlockPos(playerPos.getX(),
                level.getHeight(Heightmap.Types.WORLD_SURFACE, playerPos.getX(), playerPos.getZ()), playerPos.getZ());
        double dx = anchor.getX() - level.getSharedSpawnPos().getX();
        double dz = anchor.getZ() - level.getSharedSpawnPos().getZ();
        if (dx * dx + dz * dz < (double) MIN_STARTUP_DISTANCE * MIN_STARTUP_DISTANCE)
            return Result.failure("STARTUP_EXCLUSION");
        Plan plan = createPlan(level, anchor);
        if (!plan.valid) return Result.failure(plan.reason);
        String validation = validateCandidate(level, plan);
        if (validation != null) return Result.failure(validation);
        return apply(level, plan);
    }

    private static Plan createPlan(ServerLevel level, BlockPos anchor) {
        boolean northSouth = mix(level.getSeed(), anchor.getX(), anchor.getZ()) % 2 == 0;
        List<Segment> roads = new ArrayList<>();
        List<Segment> clearing = new ArrayList<>();
        if (northSouth) {
            roads.add(new Segment(anchor.getX(), anchor.getZ() - MAIN_HALF_LENGTH - STUB_LENGTH,
                    anchor.getX(), anchor.getZ() + MAIN_HALF_LENGTH + STUB_LENGTH, MAIN_ROAD_WIDTH, RoadClass.MAIN));
            int[] offsets = {-72, -16, 48, 96};
            for (int i = 0; i < offsets.length; i++) {
                int length = i == 1 ? 64 : 48;
                roads.add(new Segment(anchor.getX() - length, anchor.getZ() + offsets[i],
                        anchor.getX(), anchor.getZ() + offsets[i], LOCAL_ROAD_WIDTH, RoadClass.LOCAL));
            }
        } else {
            roads.add(new Segment(anchor.getX() - MAIN_HALF_LENGTH - STUB_LENGTH, anchor.getZ(),
                    anchor.getX() + MAIN_HALF_LENGTH + STUB_LENGTH, anchor.getZ(), MAIN_ROAD_WIDTH, RoadClass.MAIN));
            int[] offsets = {-72, -16, 48, 96};
            for (int i = 0; i < offsets.length; i++) {
                int length = i == 1 ? 64 : 48;
                roads.add(new Segment(anchor.getX() + offsets[i], anchor.getZ() - length,
                        anchor.getX() + offsets[i], anchor.getZ(), LOCAL_ROAD_WIDTH, RoadClass.LOCAL));
            }
        }
        AABB bounds = new AABB(anchor).inflate(240, 0, 240);
        TerrainStats global = terrainStats(level, bounds);
        if (global.effectiveRelief() > MAX_EFFECTIVE_RELIEF)
            return new Plan(false, "RELIEF_TOO_HIGH", anchor, northSouth, roads, List.of(), bounds, global);
        if (global.outlierRatio() > MAX_OUTLIER_RATIO)
            return new Plan(false, "OUTLIER_RATIO_TOO_HIGH", anchor, northSouth, roads, List.of(), bounds, global);
        for (Segment road : roads) clearing.add(road.withMargin(road.kind == RoadClass.MAIN ? MAIN_CLEAR_MARGIN : LOCAL_CLEAR_MARGIN));
        List<Lot> lots = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            int side = northSouth ? (i % 2 == 0 ? -1 : 1) : (i % 2 == 0 ? -1 : 1);
            int along = -88 + i * 56;
            BlockPos o = northSouth ? new BlockPos(anchor.getX() + side * 26, anchor.getY(), anchor.getZ() + along)
                    : new BlockPos(anchor.getX() + along, anchor.getY(), anchor.getZ() + side * 26);
            lots.add(new Lot(o, RESIDENTIAL_LOT_WIDTH, RESIDENTIAL_LOT_DEPTH, side < 0 ? Direction.EAST : Direction.WEST, LotType.RESIDENTIAL));
        }
        for (int i = 0; i < 2; i++) {
            int along = -36 + i * 72;
            BlockPos o = northSouth ? new BlockPos(anchor.getX() + (i == 0 ? -34 : 2), anchor.getY(), anchor.getZ() + along)
                    : new BlockPos(anchor.getX() + along, anchor.getY(), anchor.getZ() + (i == 0 ? -34 : 2));
            lots.add(new Lot(o, COMMERCIAL_LOT_WIDTH, COMMERCIAL_LOT_DEPTH, northSouth ? Direction.EAST : Direction.SOUTH, LotType.COMMERCIAL));
        }
        List<Lot> usableLots = new ArrayList<>();
        for (Lot lot : lots) if (lotStats(level, lot).effectiveRelief() <= (lot.type == LotType.RESIDENTIAL ? MAX_RESIDENTIAL_LOT_RELIEF : MAX_COMMERCIAL_LOT_RELIEF)) usableLots.add(lot);
        for (Lot lot : usableLots) clearing.add(lot.segment().withMargin(LOT_CLEAR_MARGIN));
        long residential = usableLots.stream().filter(l -> l.type == LotType.RESIDENTIAL).count();
        long commercial = usableLots.stream().filter(l -> l.type == LotType.COMMERCIAL).count();
        if (residential < 8 || commercial < 1) return new Plan(false, "TOO_FEW_USABLE_LOTS", anchor, northSouth, roads, usableLots, bounds, global);
        for (Segment road : roads) if (road.kind == RoadClass.MAIN && !roadStats(level, road).valid()) return new Plan(false, "MAIN_ROAD_TERRAIN", anchor, northSouth, roads, usableLots, bounds, global);
        return new Plan(true, "OK", anchor, northSouth, roads, usableLots, bounds, global);
    }

    private static Result apply(ServerLevel level, Plan plan) {
        Set<BlockPos> roadCells = new HashSet<>();
        int logs = 0, leaves = 0, other = 0;
        for (Segment segment : plan.roads) {
            for (BlockPos pos : segment.cells()) {
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
                BlockPos ground = new BlockPos(pos.getX(), y, pos.getZ());
                BlockState below = level.getBlockState(ground.below());
                roadCells.add(ground);
                level.setBlock(ground, Blocks.GRAY_CONCRETE.defaultBlockState(), 3);
            }
        }
        for (Segment segment : plan.clearance()) {
            for (BlockPos pos : segment.cells()) {
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
                for (int dy = 0; dy < 6; dy++) {
                    BlockPos p = new BlockPos(pos.getX(), y + dy, pos.getZ());
                    if (roadCells.contains(new BlockPos(pos.getX(), y, pos.getZ()))) continue;
                    BlockState state = level.getBlockState(p);
                    if (state.is(BlockTags.LOGS)) { level.setBlock(p, Blocks.AIR.defaultBlockState(), 3); logs++; }
                    else if (state.is(BlockTags.LEAVES)) { level.setBlock(p, Blocks.AIR.defaultBlockState(), 3); leaves++; }
                    else if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)) { level.setBlock(p, Blocks.AIR.defaultBlockState(), 3); other++; }
                }
            }
        }
        return new Result(true, "OK", plan, logs, leaves, other);
    }

    private static String validateCandidate(ServerLevel level, Plan plan) {
        int total = 0, woodland = 0;
        for (int x = (int) plan.bounds.minX; x <= plan.bounds.maxX; x += 16) {
            for (int z = (int) plan.bounds.minZ; z <= plan.bounds.maxZ; z += 16) {
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) return "CANDIDATE_NOT_LOADED";
                total++;
                if (level.getBiome(new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z)).is(AflBiomes.IRRADIATED_WOODLAND)) woodland++;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                if (!level.getFluidState(new BlockPos(x, surface - 1, z)).isEmpty()) return "SURFACE_FLUID";
            }
        }
        if (woodland * 100 < total * 70) return "BIOME_PURITY_BELOW_70_PERCENT";
        BunkerSavedData bunker = level.getDataStorage().computeIfAbsent(BunkerSavedData::load, BunkerSavedData::new, BunkerSavedData.ID);
        if (bunker.isGenerated()) {
            double dx = bunker.getOrigin().getX() - plan.anchor.getX(), dz = bunker.getOrigin().getZ() - plan.anchor.getZ();
            if (dx * dx + dz * dz < 128.0D * 128.0D) return "BUNKER_OVERLAP_RISK";
        }
        return null;
    }

    private static TerrainStats terrainStats(ServerLevel level, AABB bounds) {
        List<Integer> values = new ArrayList<>();
        for (int x = (int) bounds.minX; x <= bounds.maxX; x += 16) for (int z = (int) bounds.minZ; z <= bounds.maxZ; z += 16)
            values.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
        return stats(values);
    }

    private static TerrainStats lotStats(ServerLevel level, Lot lot) {
        List<Integer> values = new ArrayList<>();
        for (int x = lot.origin.getX(); x <= lot.origin.getX() + lot.width; x += 8)
            for (int z = lot.origin.getZ(); z <= lot.origin.getZ() + lot.depth; z += 8)
                values.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
        return stats(values);
    }

    private static RoadStats roadStats(ServerLevel level, Segment segment) {
        List<Integer> values = new ArrayList<>();
        int length = Math.max(Math.abs(segment.x2 - segment.x1), Math.abs(segment.z2 - segment.z1));
        for (int i = 0; i <= length; i += 8) {
            int x = segment.x1 + (segment.x2 - segment.x1) * i / Math.max(1, length);
            int z = segment.z1 + (segment.z2 - segment.z1) * i / Math.max(1, length);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) return new RoadStats(false);
            values.add(y);
        }
        for (int i = 1; i < values.size(); i++) if (Math.abs(values.get(i) - values.get(i - 1)) > MAX_ROAD_LOCAL_DELTA) return new RoadStats(false);
        return new RoadStats(true);
    }

    private static TerrainStats stats(List<Integer> values) {
        values.sort(Integer::compareTo);
        int n = values.size(), min = values.get(0), max = values.get(n - 1), p10 = percentile(values, .10), p25 = percentile(values, .25), median = percentile(values, .50), p75 = percentile(values, .75), p90 = percentile(values, .90), outliers = 0;
        for (int y : values) if (Math.abs(y - median) > 8) outliers++;
        return new TerrainStats(n, min, p10, p25, median, p75, p90, max, p90 - p10, outliers, (double) outliers / n);
    }

    private static int percentile(List<Integer> values, double percentile) { return values.get((int) Math.round((values.size() - 1) * percentile)); }

    public record TerrainStats(int sampleCount, int minY, int p10, int p25, int median, int p75, int p90, int maxY, int effectiveRelief, int outlierCount, double outlierRatio) {}
    private record RoadStats(boolean valid) {}

    private static long mix(long seed, int x, int z) { long h = seed ^ ((long)x * 341873128712L) ^ ((long)z * 132897987541L); h ^= h >>> 33; return h; }

    public record Result(boolean success, String reason, Plan plan, int logsCleared, int leavesCleared, int otherVegetationCleared) {
        static Result failure(String reason) { return new Result(false, reason, null, 0, 0, 0); }
    }
    public record Plan(boolean valid, String reason, BlockPos anchor, boolean northSouth, List<Segment> roads, List<Lot> lots, AABB bounds, TerrainStats terrain) {
        private List<Segment> clearance() { return roads.stream().map(r -> r.withMargin(r.kind == RoadClass.MAIN ? MAIN_CLEAR_MARGIN : LOCAL_CLEAR_MARGIN)).toList(); }
    }
    public record Segment(int x1, int z1, int x2, int z2, int width, RoadClass kind) {
        Segment withMargin(int margin) { return new Segment(x1, z1, x2, z2, width + margin * 2, kind); }
        List<BlockPos> cells() { List<BlockPos> out = new ArrayList<>(); int minX=Math.min(x1,x2)-width/2,maxX=Math.max(x1,x2)+width/2,minZ=Math.min(z1,z2)-width/2,maxZ=Math.max(z1,z2)+width/2; for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(x1==x2?x==x1:z1==z2?z==z1:true)out.add(new BlockPos(x,0,z)); return out; }
    }
    public record Lot(BlockPos origin, int width, int depth, Direction facing, LotType type) { Segment segment(){ return new Segment(origin.getX(),origin.getZ(),origin.getX()+width,origin.getZ()+depth,1,RoadClass.LOT); } }
    public enum LotType { RESIDENTIAL, COMMERCIAL }
    public enum RoadClass { MAIN, LOCAL, REGIONAL_STUB, LOT }
}
