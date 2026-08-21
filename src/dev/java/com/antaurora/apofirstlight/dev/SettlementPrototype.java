package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.registry.AflBiomes;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import com.antaurora.apofirstlight.world.biome.StartupSettlementProtection;
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
    public static final String VALIDATOR_VERSION = "V1_3_SURFACE_VALIDATION";
    public static final int GLOBAL_SAMPLE_SPACING = 16;
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
    private static final int MAX_EFFECTIVE_RELIEF = 12;
    private static final double MAX_OUTLIER_RATIO = 0.15D;
    private static final int MAX_ROAD_LOCAL_DELTA = 2;
    private static final int MAX_RESIDENTIAL_LOT_RELIEF = 5;
    private static final int MAX_COMMERCIAL_LOT_RELIEF = 4;
    private static final int MAX_INVALID_SURFACE_SAMPLES = 5;
    private static final int MAX_MINOR_FLUID_SAMPLES = 2;
    private static final int MAX_MINOR_FLUID_RUN = 1;

    private SettlementPrototype() {}

    public static Result generateHere(ServerLevel level, BlockPos playerPos) {
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD)
            return Result.failure("OVERWORLD_REQUIRED");
        if (level.getBiome(playerPos).is(AflBiomes.IRRADIATED_WOODLAND) == false)
            return Result.failure("PLAYER_NOT_IN_IRRADIATED_WOODLAND");
        BlockPos anchor = new BlockPos(playerPos.getX(),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, playerPos.getX(), playerPos.getZ()), playerPos.getZ());
        Plan plan = createPlan(level, anchor);
        if (!plan.valid) return Result.failure(plan.reason, plan);
        StartupIntersection intersection = startupIntersection(level.getSeed(), plan.bounds());
        if (intersection != null) {
            String detail = String.format("legacyRadiusUsed=false buffer=0 footprintMinX=%d footprintMaxX=%d footprintMinZ=%d footprintMaxZ=%d sampleCount=%d firstIntersectX=%d firstIntersectZ=%d firstIntersectZone=%s protectionClass=%s firstIntersectDistance=%.1f plainsBoundary=%d woodlandBoundary=%d",
                    (int) plan.bounds().minX, (int) plan.bounds().maxX, (int) plan.bounds().minZ, (int) plan.bounds().maxZ,
                    intersection.sampleCount(), intersection.x(), intersection.z(), intersection.zone(), intersection.protection(), intersection.distance(),
                    intersection.plainsBoundary(), intersection.woodlandBoundary());
            return Result.failure("STARTUP_ECOLOGY_INTERSECTION", plan.withStage("PRECHECK"), detail);
        }
        String validation = validateCandidate(level, plan);
        if (validation != null) return Result.failure(validation, plan.withStage("CANDIDATE_VALIDATION"));
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
        SettlementSurfaceSampler.ensureDevChunksReady(level, (int) bounds.minX, (int) bounds.maxX, (int) bounds.minZ, (int) bounds.maxZ);
        TerrainStats global = terrainStats(level, bounds);
        if (global.invalidSamples() > MAX_INVALID_SURFACE_SAMPLES || global.validSamples() == 0)
            return new Plan(false, "INSUFFICIENT_VALID_SURFACE_DATA", "GLOBAL_ROBUST", anchor, northSouth, roads, List.of(), bounds, global, null, LotSummary.empty());
        if (global.effectiveRelief() > MAX_EFFECTIVE_RELIEF)
            return new Plan(false, "RELIEF_TOO_HIGH", "GLOBAL_ROBUST", anchor, northSouth, roads, List.of(), bounds, global, null, LotSummary.empty());
        if (global.outlierRatio() > MAX_OUTLIER_RATIO)
            return new Plan(false, "OUTLIER_RATIO_TOO_HIGH", "GLOBAL_ROBUST", anchor, northSouth, roads, List.of(), bounds, global, null, LotSummary.empty());
        for (Segment road : roads) clearing.add(road.withMargin(road.kind == RoadClass.MAIN ? MAIN_CLEAR_MARGIN : LOCAL_CLEAR_MARGIN));
        for (Segment road : roads) {
            if (road.kind != RoadClass.MAIN) continue;
            RoadStats roadStats = roadStats(level, road);
            if (!roadStats.valid())
                return new Plan(false, "MAIN_ROAD_TERRAIN", "ROAD_CORRIDOR", anchor, northSouth, roads, List.of(), bounds, global, roadStats, LotSummary.empty());
        }
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
        int plannedResidential = 0, usableResidential = 0, plannedCommercial = 0, usableCommercial = 0;
        for (Lot lot : lots) {
            if (lot.type == LotType.RESIDENTIAL) plannedResidential++; else plannedCommercial++;
            boolean usable = lotStats(level, lot).effectiveRelief() <= (lot.type == LotType.RESIDENTIAL ? MAX_RESIDENTIAL_LOT_RELIEF : MAX_COMMERCIAL_LOT_RELIEF);
            if (!usable) continue;
            usableLots.add(lot);
            if (lot.type == LotType.RESIDENTIAL) usableResidential++; else usableCommercial++;
        }
        LotSummary lotSummary = new LotSummary(plannedResidential, usableResidential, plannedCommercial, usableCommercial);
        for (Lot lot : usableLots) clearing.add(lot.segment().withMargin(LOT_CLEAR_MARGIN));
        long residential = usableLots.stream().filter(l -> l.type == LotType.RESIDENTIAL).count();
        long commercial = usableLots.stream().filter(l -> l.type == LotType.COMMERCIAL).count();
        if (residential < 8 || commercial < 1) return new Plan(false, "TOO_FEW_USABLE_LOTS", "LOT_FILTER", anchor, northSouth, roads, usableLots, bounds, global, null, lotSummary);
        return new Plan(true, "OK", "COMPLETE", anchor, northSouth, roads, usableLots, bounds, global, null, lotSummary);
    }

    private static Result apply(ServerLevel level, Plan plan) {
        Set<BlockPos> roadCells = new HashSet<>();
        int logs = 0, leaves = 0, other = 0;
        for (Segment segment : plan.roads) {
            for (BlockPos pos : segment.cells()) {
                SettlementSurfaceSampler.SurfaceSample sample = SettlementSurfaceSampler.sample(level, pos.getX(), pos.getZ());
                int y = sample.y();
                BlockPos ground = new BlockPos(pos.getX(), y - 1, pos.getZ());
                roadCells.add(ground);
                level.setBlock(ground, Blocks.GRAY_CONCRETE.defaultBlockState(), 3);
            }
        }
        for (Segment segment : plan.clearance()) {
            for (BlockPos pos : segment.cells()) {
                SettlementSurfaceSampler.SurfaceSample sample = SettlementSurfaceSampler.sample(level, pos.getX(), pos.getZ());
                int y = sample.y();
                for (int dy = 0; dy < 6; dy++) {
                    BlockPos p = new BlockPos(pos.getX(), y + dy, pos.getZ());
                if (roadCells.contains(new BlockPos(pos.getX(), y - 1, pos.getZ()))) continue;
                    BlockState state = level.getBlockState(p);
                    if (state.is(BlockTags.LOGS)) { level.setBlock(p, Blocks.AIR.defaultBlockState(), 3); logs++; }
                    else if (state.is(BlockTags.LEAVES)) { level.setBlock(p, Blocks.AIR.defaultBlockState(), 3); leaves++; }
                    else if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)) { level.setBlock(p, Blocks.AIR.defaultBlockState(), 3); other++; }
                }
            }
        }
        return new Result(true, "OK", plan, logs, leaves, other, null);
    }

    private static String validateCandidate(ServerLevel level, Plan plan) {
        int total = 0, woodland = 0;
        for (int x = (int) plan.bounds.minX; x <= plan.bounds.maxX; x += 16) {
            for (int z = (int) plan.bounds.minZ; z <= plan.bounds.maxZ; z += 16) {
                SettlementSurfaceSampler.SurfaceSample sample = SettlementSurfaceSampler.sample(level, x, z);
                if (!sample.valid()) return "CANDIDATE_SURFACE_DATA_INVALID";
                total++;
                if (level.getBiome(new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z)).is(AflBiomes.IRRADIATED_WOODLAND)) woodland++;
                int surface = sample.y();
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

    private static StartupIntersection startupIntersection(long seed, AABB bounds) {
        int minX = (int) Math.floor(bounds.minX);
        int maxX = (int) Math.ceil(bounds.maxX);
        int minZ = (int) Math.floor(bounds.minZ);
        int maxZ = (int) Math.ceil(bounds.maxZ);
        List<int[]> samples = new ArrayList<>();
        samples.add(new int[]{(minX + maxX) / 2, (minZ + maxZ) / 2});
        for (int x = minX; x <= maxX; x += 32) {
            samples.add(new int[]{x, minZ});
            samples.add(new int[]{x, maxZ});
        }
        for (int z = minZ; z <= maxZ; z += 32) {
            samples.add(new int[]{minX, z});
            samples.add(new int[]{maxX, z});
        }
        samples.add(new int[]{maxX, minZ});
        samples.add(new int[]{maxX, maxZ});
        samples.add(new int[]{minX, maxZ});
        int sampleCount = samples.size();
        for (int[] sample : samples) {
            StartupSettlementProtection.ProtectionClass protection =
                    StartupSettlementProtection.protectionAt(sample[0], sample[1], seed);
            if (protection != StartupSettlementProtection.ProtectionClass.NONE) {
                StartupPlainsEnclave.Zone zone = StartupPlainsEnclave.zoneAt(sample[0], sample[1], seed);
                int boundary = StartupPlainsEnclave.woodlandOuterBoundary(sample[0], sample[1], seed);
                return new StartupIntersection(sample[0], sample[1], zone, protection,
                        Math.sqrt((double) sample[0] * sample[0] + (double) sample[1] * sample[1]),
                        StartupPlainsEnclave.plainsBoundary(sample[0], sample[1], seed), boundary, sampleCount);
            }
        }
        return null;
    }

    private static TerrainStats terrainStats(ServerLevel level, AABB bounds) {
        List<Integer> values = new ArrayList<>();
        for (int x = (int) bounds.minX; x <= bounds.maxX; x += GLOBAL_SAMPLE_SPACING) for (int z = (int) bounds.minZ; z <= bounds.maxZ; z += GLOBAL_SAMPLE_SPACING) {
            SettlementSurfaceSampler.SurfaceSample sample = SettlementSurfaceSampler.sample(level, x, z);
            values.add(sample.valid() ? sample.y() : level.getMinBuildHeight());
        }
        return stats(values);
    }

    private static TerrainStats lotStats(ServerLevel level, Lot lot) {
        List<Integer> values = new ArrayList<>();
        for (int x = lot.origin.getX(); x <= lot.origin.getX() + lot.width; x += 8) for (int z = lot.origin.getZ(); z <= lot.origin.getZ() + lot.depth; z += 8) {
                SettlementSurfaceSampler.SurfaceSample sample = SettlementSurfaceSampler.sample(level, x, z);
                values.add(sample.valid() ? sample.y() : level.getMinBuildHeight());
        }
        return stats(values);
    }

    private static RoadStats roadStats(ServerLevel level, Segment segment) {
        List<Integer> values = new ArrayList<>();
        int fluidCount = 0, fluidRuns = 0, maxRun = 0, currentRun = 0, longestRunBlocks = 0;
        int length = Math.max(Math.abs(segment.x2 - segment.x1), Math.abs(segment.z2 - segment.z1));
        for (int i = 0; i <= length; i += 8) {
            int x = segment.x1 + (segment.x2 - segment.x1) * i / Math.max(1, length);
            int z = segment.z1 + (segment.z2 - segment.z1) * i / Math.max(1, length);
            SettlementSurfaceSampler.SurfaceSample sample = SettlementSurfaceSampler.sample(level, x, z);
            if (!sample.valid()) return RoadStats.invalid(values.size() + 1);
            int y = sample.y();
            boolean fluid = !level.getFluidState(sample.ground()).isEmpty();
            if (fluid) { fluidCount++; currentRun++; maxRun = Math.max(maxRun, currentRun); }
            else if (currentRun > 0) { fluidRuns++; longestRunBlocks = Math.max(longestRunBlocks, currentRun * 8); currentRun = 0; }
            values.add(y);
        }
        if (currentRun > 0) { fluidRuns++; longestRunBlocks = Math.max(longestRunBlocks, currentRun * 8); }
        int maxAdjacentDelta = 0;
        for (int i = 1; i < values.size(); i++) {
            maxAdjacentDelta = Math.max(maxAdjacentDelta, Math.abs(values.get(i) - values.get(i - 1)));
        }
        boolean minorFluid = fluidCount <= MAX_MINOR_FLUID_SAMPLES && maxRun <= MAX_MINOR_FLUID_RUN;
        return new RoadStats(maxAdjacentDelta <= MAX_ROAD_LOCAL_DELTA && minorFluid, values.size(), values.size(), 0,
                values.stream().mapToInt(Integer::intValue).min().orElse(0), values.stream().mapToInt(Integer::intValue).max().orElse(0),
                maxAdjacentDelta, fluidCount, fluidRuns, maxRun, longestRunBlocks, minorFluid);
    }

    private static TerrainStats stats(List<Integer> values) {
        values.sort(Integer::compareTo);
        int invalid = 0;
        for (int y : values) if (y <= -64) invalid++;
        values.removeIf(y -> y <= -64);
        int n = values.size();
        if (n == 0) return new TerrainStats(n + invalid, 0, invalid, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0D);
        int min = values.get(0), max = values.get(n - 1), p10 = percentile(values, .10), p25 = percentile(values, .25), median = percentile(values, .50), p75 = percentile(values, .75), p90 = percentile(values, .90), outliers = 0;
        for (int y : values) if (Math.abs(y - median) > 8) outliers++;
        return new TerrainStats(n + invalid, n, invalid, min, p10, p25, median, p75, p90, max, p90 - p10, outliers, (double) outliers / n);
    }

    private static int percentile(List<Integer> values, double percentile) { return values.get((int) Math.round((values.size() - 1) * percentile)); }

    public record TerrainStats(int sampleCount, int validSamples, int invalidSamples, int minY, int p10, int p25, int median, int p75, int p90, int maxY, int effectiveRelief, int outlierCount, double outlierRatio) {}
    public static String rejectDiagnostic(Result result) {
        Plan plan = result.plan();
        if (plan == null) return "validatorVersion=" + VALIDATOR_VERSION + " stage=PRECHECK reason=" + result.reason();
        TerrainStats t = plan.terrain();
        String base = String.format("validatorVersion=%s stage=%s reason=%s anchor=%s sampleSpacing=%d samples=%d validSamples=%d invalidSamples=%d invalidRatio=%.3f minY=%d p10=%d p25=%d median=%d p75=%d p90=%d maxY=%d effectiveRelief=%d maxEffectiveRelief=%d outliers=%d outlierRatio=%.3f maxOutlierRatio=%.3f heightmapType=MOTION_BLOCKING_NO_LEAVES percentilesBasedOn=VALID_ONLY",
                VALIDATOR_VERSION, plan.stage(), result.reason(), plan.anchor().toShortString(), GLOBAL_SAMPLE_SPACING,
                t.sampleCount(), t.validSamples(), t.invalidSamples(), t.sampleCount() == 0 ? 0.0D : (double) t.invalidSamples() / t.sampleCount(),
                t.minY(), t.p10(), t.p25(), t.median(), t.p75(), t.p90(), t.maxY(), t.effectiveRelief(), MAX_EFFECTIVE_RELIEF,
                t.outlierCount(), t.outlierRatio(), MAX_OUTLIER_RATIO);
        if (plan.roadStats() != null) {
            RoadStats r = plan.roadStats();
            base += String.format(" roadSampleCount=%d validRoadSamples=%d invalidRoadSamples=%d roadMinY=%d roadMaxY=%d maxAdjacentDelta=%d maxAllowedDelta=%d surfaceFluidCount=%d fluidRuns=%d maxConsecutiveFluidSamples=%d longestFluidRunBlocks=%d minorFluidToleranceApplied=%s",
                    r.sampleCount(), r.validSamples(), r.invalidSamples(), r.minY(), r.maxY(), r.maxAdjacentDelta(), MAX_ROAD_LOCAL_DELTA,
                    r.surfaceFluidCount(), r.fluidRuns(), r.maxConsecutiveFluidSamples(), r.longestFluidRunBlocks(), r.minorFluidToleranceApplied());
        }
        LotSummary lots = plan.lotSummary();
        return base + (result.detail() == null ? "" : " " + result.detail()) + String.format(" plannedResidential=%d usableResidential=%d rejectedResidential=%d plannedCommercial=%d usableCommercial=%d rejectedCommercial=%d",
                lots.plannedResidential(), lots.usableResidential(), lots.rejectedResidential(), lots.plannedCommercial(), lots.usableCommercial(), lots.rejectedCommercial());
    }

    public static TerrainStats terrainCheckHere(ServerLevel level, BlockPos pos) {
        return terrainStats(level, new AABB(pos).inflate(240, 0, 240));
    }

    public static boolean passesGlobalTerrain(TerrainStats stats) {
        return stats.effectiveRelief() <= MAX_EFFECTIVE_RELIEF && stats.outlierRatio() <= MAX_OUTLIER_RATIO;
    }

    public record RoadStats(boolean valid, int sampleCount, int validSamples, int invalidSamples, int minY, int maxY, int maxAdjacentDelta,
                            int surfaceFluidCount, int fluidRuns, int maxConsecutiveFluidSamples, int longestFluidRunBlocks,
                            boolean minorFluidToleranceApplied) {
        private static RoadStats invalid(int samples) {
            return new RoadStats(false, samples, samples - 1, 1, 0, 0, 0, 0, 0, 0, 0, false);
        }
    }

    private static long mix(long seed, int x, int z) { long h = seed ^ ((long)x * 341873128712L) ^ ((long)z * 132897987541L); h ^= h >>> 33; return h; }

    public record Result(boolean success, String reason, Plan plan, int logsCleared, int leavesCleared, int otherVegetationCleared, String detail) {
        static Result failure(String reason) { return failure(reason, null, null); }
        static Result failure(String reason, Plan plan) { return failure(reason, plan, null); }
        static Result failure(String reason, Plan plan, String detail) { return new Result(false, reason, plan, 0, 0, 0, detail); }
    }
    public record Plan(boolean valid, String reason, String stage, BlockPos anchor, boolean northSouth, List<Segment> roads, List<Lot> lots, AABB bounds, TerrainStats terrain, RoadStats roadStats, LotSummary lotSummary) {
        private List<Segment> clearance() { return roads.stream().map(r -> r.withMargin(r.kind == RoadClass.MAIN ? MAIN_CLEAR_MARGIN : LOCAL_CLEAR_MARGIN)).toList(); }
        private Plan withStage(String replacementStage) { return new Plan(valid, reason, replacementStage, anchor, northSouth, roads, lots, bounds, terrain, roadStats, lotSummary); }
    }
    private record StartupIntersection(int x, int z, StartupPlainsEnclave.Zone zone,
                                       StartupSettlementProtection.ProtectionClass protection, double distance,
                                       int plainsBoundary, int woodlandBoundary, int sampleCount) {}
    public record LotSummary(int plannedResidential, int usableResidential, int plannedCommercial, int usableCommercial) {
        static LotSummary empty() { return new LotSummary(0, 0, 0, 0); }
        int rejectedResidential() { return plannedResidential - usableResidential; }
        int rejectedCommercial() { return plannedCommercial - usableCommercial; }
    }
    public record Segment(int x1, int z1, int x2, int z2, int width, RoadClass kind) {
        Segment withMargin(int margin) { return new Segment(x1, z1, x2, z2, width + margin * 2, kind); }
        List<BlockPos> cells() { List<BlockPos> out = new ArrayList<>(); int minX=Math.min(x1,x2)-width/2,maxX=Math.max(x1,x2)+width/2,minZ=Math.min(z1,z2)-width/2,maxZ=Math.max(z1,z2)+width/2; for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(x1==x2?x==x1:z1==z2?z==z1:true)out.add(new BlockPos(x,0,z)); return out; }
    }
    public record Lot(BlockPos origin, int width, int depth, Direction facing, LotType type) { Segment segment(){ return new Segment(origin.getX(),origin.getZ(),origin.getX()+width,origin.getZ()+depth,1,RoadClass.LOT); } }
    public enum LotType { RESIDENTIAL, COMMERCIAL }
    public enum RoadClass { MAIN, LOCAL, REGIONAL_STUB, LOT }
}
