package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBiomes;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import com.antaurora.apofirstlight.world.biome.StartupSettlementProtection;
import com.antaurora.apofirstlight.world.bunker.BunkerSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** DEV-only settlement road prototype. Roads are evaluated before earthwork is applied. */
public final class SettlementPrototype {
    public static final String VALIDATOR_VERSION = "V2_ROAD_CONSTRUCTION_AWARE";
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
    private static final int MAX_RESIDENTIAL_LOT_RELIEF = 5;
    private static final int MAX_COMMERCIAL_LOT_RELIEF = 4;
    private static final int MAX_INVALID_SURFACE_SAMPLES = 5;

    private static final int ROAD_SAMPLE_STEP = 4;
    private static final int MAX_NORMAL_CUT_FILL_DEPTH = 2;
    private static final int MAX_ALLOWED_CUT_FILL_DEPTH = 3;
    private static final int MAX_CONSECUTIVE_DEEP_STATIONS = 4;
    private static final int MAX_CONSECUTIVE_WATER_STATIONS = 4;
    private static final double MAX_WATER_STATION_RATIO = 0.25D;
    private static final int INTERSECTION_OVERLAP_DISTANCE = MAIN_ROAD_WIDTH / 2;
    private static final int INTERSECTION_TRANSITION_DISTANCE = 16;
    private static final int ROAD_CLEARANCE_HEIGHT = 4;
    private static final int ROAD_ROW_MARGIN = 2;
    private static final int CLEARANCE_SCAN_HEIGHT = 16;
    private static final int REMAINING_CLEARANCE_HEIGHT = 8;
    private static final int TREE_HORIZONTAL_RADIUS = 8;
    private static final int TREE_SCAN_UP = 16;
    private static final int TREE_SCAN_DOWN = 4;
    private static final int MAX_EDGE_CLOSURE_DEPTH = 4;
    private static final int MAX_CONSTRUCTION_SURFACE_SCAN = 16;
    private static final int MAX_ISOLATED_FOUNDATION_DEPTH = 6;
    private static final int MAIN_MAX_ISOLATED_UNSUPPORTED_COLUMNS = 8;
    private static final double MAIN_MAX_UNSUPPORTED_RATIO = 0.002D;
    private static final int MAIN_MAX_UNSUPPORTED_RUN = 3;
    private static final int MAIN_MAX_UNSUPPORTED_ACROSS_SECTION = 2;
    private static final int LOCAL_MAX_ISOLATED_UNSUPPORTED_COLUMNS = 4;
    private static final double LOCAL_MAX_UNSUPPORTED_RATIO = 0.005D;
    private static final int LOCAL_MAX_UNSUPPORTED_RUN = 2;
    private static final int LOCAL_MAX_UNSUPPORTED_ACROSS_SECTION = 1;
    private static final double FOUNDATION_REPAIR_COST = 8.0D;
    private static final double CUT_COST = 1.0D;
    private static final double FILL_COST = 1.25D;
    private static final double WATER_COST = 3.0D;
    private static final int[] MAIN_OFFSETS = {0, 8, -8, 16, -16};
    private static final int[] LOCAL_OFFSETS = {0, 4, -4};
    private static final int[] BRANCH_ALONG = {-72, -16, 48, 96};

    private SettlementPrototype() {}

    public static Result generateHere(ServerLevel level, BlockPos playerPos) {
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return Result.failure("OVERWORLD_REQUIRED");
        BlockPos anchor = new BlockPos(playerPos.getX(),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, playerPos.getX(), playerPos.getZ()), playerPos.getZ());
        long seed = level.getSeed();
        StartupPlainsEnclave.Zone anchorZone = StartupPlainsEnclave.zoneAt(anchor.getX(), anchor.getZ(), seed);
        StartupSettlementProtection.ProtectionClass anchorProtection =
                StartupSettlementProtection.protectionAt(anchor.getX(), anchor.getZ(), seed);
        boolean anchorProtected = anchorProtection != StartupSettlementProtection.ProtectionClass.NONE;
        boolean anchorEligible = anchorZone == StartupPlainsEnclave.Zone.WOODLAND_BUFFER && !anchorProtected;
        String anchorDetail = anchorEligibilityDetail(anchor, seed, anchorZone, anchorProtection, anchorEligible);
        ApocalypseFirstLight.LOGGER.info("[AFL SETTLEMENT ECOLOGY] {}", anchorDetail);
        if (!anchorEligible) {
            return new Result(false, "ANCHOR_NOT_SETTLEMENT_ELIGIBLE", null, 0, 0, 0, anchorDetail);
        }
        Plan plan = createPlan(level, anchor);
        if (!plan.valid()) return Result.failure(plan.reason(), plan, plan.detail());
        return apply(level, plan);
    }

    private static String anchorEligibilityDetail(BlockPos anchor, long seed, StartupPlainsEnclave.Zone zone,
                                                   StartupSettlementProtection.ProtectionClass protection,
                                                   boolean eligible) {
        return String.format("anchor=%s anchorZone=%s anchorProtected=%s anchorEligible=%s protectionClass=%s woodlandShapeSource=%s distance=%s plainsBoundary=%d settlementProtectionBoundary=%d woodlandBoundary=%d",
                anchor.toShortString(), zone, protection != StartupSettlementProtection.ProtectionClass.NONE, eligible,
                protection, StartupPlainsEnclave.woodlandShapeSource(anchor.getX(), anchor.getZ(), seed),
                format(StartupSettlementProtection.distanceFromCenter(anchor.getX(), anchor.getZ())),
                StartupPlainsEnclave.plainsBoundary(anchor.getX(), anchor.getZ(), seed),
                StartupSettlementProtection.settlementProtectionBoundary(anchor.getX(), anchor.getZ(), seed),
                StartupPlainsEnclave.woodlandOuterBoundary(anchor.getX(), anchor.getZ(), seed));
    }

    private static Plan createPlan(ServerLevel level, BlockPos anchor) {
        long totalStart = System.nanoTime();
        long precheckStart = System.nanoTime();
        Footprint compact = compactFootprint(level.getSeed(), anchor);
        SettlementFitResult compactFit = validateFootprint(level, compact, level.getSeed());
        long precheckElapsed = elapsedMs(precheckStart);
        logFootprint("COMPACT_PRECHECK", anchor, compact, compactFit);
        if (!compactFit.fit()) {
            return emptyPlan("STARTUP_PROTECTED_ZONE_INTERSECTION", "COMPACT_PRECHECK", anchor, compact.bounds(),
                    new TerrainStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0D),
                    footprintRejectionDetail("COMPACT_PRECHECK", compact, compactFit, precheckElapsed, 0, 0, elapsedMs(totalStart)));
        }

        Footprint candidateSearch = candidateSearchFootprint(level.getSeed(), anchor);
        AABB preloadBounds = unionBounds(compact.bounds(), candidateSearch.bounds());
        SettlementSurfaceSampler.ensureDevChunksReady(level, (int) preloadBounds.minX, (int) preloadBounds.maxX,
                (int) preloadBounds.minZ, (int) preloadBounds.maxZ);
        TerrainStats global = terrainStats(level, compact);
        if (global.invalidSamples() > MAX_INVALID_SURFACE_SAMPLES || global.validSamples() == 0)
            return emptyPlan("INSUFFICIENT_VALID_SURFACE_DATA", "GLOBAL_ROBUST", anchor, compact.bounds(), global,
                    timingDetail(precheckElapsed, 0, 0, elapsedMs(totalStart)));
        if (global.effectiveRelief() > MAX_EFFECTIVE_RELIEF)
            return emptyPlan("RELIEF_TOO_HIGH", "GLOBAL_ROBUST", anchor, compact.bounds(), global,
                    timingDetail(precheckElapsed, 0, 0, elapsedMs(totalStart)));
        if (global.outlierRatio() > MAX_OUTLIER_RATIO)
            return emptyPlan("OUTLIER_RATIO_TOO_HIGH", "GLOBAL_ROBUST", anchor, compact.bounds(), global,
                    timingDetail(precheckElapsed, 0, 0, elapsedMs(totalStart)));

        long roadPlanningStart = System.nanoTime();
        RoadChoice main = chooseMainRoad(level, anchor);
        if (main == null) return emptyPlan("MAIN_ROAD_TERRAIN", "ROAD_CORRIDOR", anchor, compact.bounds(), global,
                timingDetail(precheckElapsed, elapsedMs(roadPlanningStart), 0, elapsedMs(totalStart)));
        List<RoadPlan> roadPlans = new ArrayList<>();
        roadPlans.add(main.plan());
        for (int along : BRANCH_ALONG) {
            RoadChoice local = chooseLocalRoad(level, anchor, main.plan(), along);
            if (local != null) roadPlans.add(local.plan());
        }
        List<Segment> roads = roadPlans.stream().map(RoadPlan::segment).toList();
        boolean northSouth = main.plan().segment().northSouth();
        List<Lot> lots = plannedLots(anchor, northSouth);
        List<Lot> usableLots = new ArrayList<>();
        int plannedResidential = 0, usableResidential = 0, plannedCommercial = 0, usableCommercial = 0;
        for (Lot lot : lots) {
            if (lot.type() == LotType.RESIDENTIAL) plannedResidential++; else plannedCommercial++;
            int limit = lot.type() == LotType.RESIDENTIAL ? MAX_RESIDENTIAL_LOT_RELIEF : MAX_COMMERCIAL_LOT_RELIEF;
            if (lotStats(level, lot).effectiveRelief() > limit) continue;
            usableLots.add(lot);
            if (lot.type() == LotType.RESIDENTIAL) usableResidential++; else usableCommercial++;
        }
        LotSummary lotSummary = new LotSummary(plannedResidential, usableResidential, plannedCommercial, usableCommercial);
        long roadPlanningElapsed = elapsedMs(roadPlanningStart);
        long finalValidationStart = System.nanoTime();
        Footprint finalFootprint = finalFootprint(roadPlans, lots);
        SettlementFitResult finalFit = validateFootprint(level, finalFootprint, level.getSeed());
        logFootprint("FINAL", anchor, finalFootprint, finalFit);
        long finalValidationElapsed = elapsedMs(finalValidationStart);
        String timing = timingDetail(precheckElapsed, roadPlanningElapsed, finalValidationElapsed, elapsedMs(totalStart));
        String footprintDetail = footprintDetail(finalFootprint, finalFit) + " " + timing;
        if (!finalFit.fit()) {
            return new Plan(false, "STARTUP_PROTECTED_ZONE_INTERSECTION", "FINAL_FOOTPRINT", anchor, northSouth, roads, roadPlans,
                    usableLots, finalFootprint.bounds(), global, main.plan().stats(), lotSummary, MAIN_OFFSETS.length * 2,
                    footprintRejectionDetail("FINAL", finalFootprint, finalFit, precheckElapsed, roadPlanningElapsed, finalValidationElapsed, elapsedMs(totalStart)));
        }
        CandidateValidation validation = validateCandidate(level, anchor, finalFootprint, level.getSeed());
        logEcology(anchor, validation, finalFit, level.getSeed());
        String candidateDetail = footprintDetail + " " + validation.detail();
        Plan plan = new Plan(true, "OK", "COMPLETE", anchor, northSouth, roads, roadPlans, usableLots, finalFootprint.bounds(), global,
                main.plan().stats(), lotSummary, MAIN_OFFSETS.length * 2, candidateDetail);
        if (!validation.valid()) return plan.withFailure(validation.reason(), "CANDIDATE_VALIDATION", candidateDetail);
        if (usableResidential < 8 || usableCommercial < 1) return plan.withFailure("TOO_FEW_USABLE_LOTS", "LOT_FILTER", candidateDetail);
        return plan;
    }

    private static Plan emptyPlan(String reason, String stage, BlockPos anchor, AABB bounds, TerrainStats terrain, String detail) {
        return new Plan(false, reason, stage, anchor, false, List.of(), List.of(), List.of(), bounds, terrain, null,
                LotSummary.empty(), 0, detail);
    }

    private static List<Lot> plannedLots(BlockPos anchor, boolean northSouth) {
        List<Lot> lots = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            int side = i % 2 == 0 ? -1 : 1, along = -88 + i * 56;
            BlockPos origin = northSouth ? new BlockPos(anchor.getX() + side * 26, anchor.getY(), anchor.getZ() + along)
                    : new BlockPos(anchor.getX() + along, anchor.getY(), anchor.getZ() + side * 26);
            lots.add(new Lot(origin, RESIDENTIAL_LOT_WIDTH, RESIDENTIAL_LOT_DEPTH, side < 0 ? Direction.EAST : Direction.WEST, LotType.RESIDENTIAL));
        }
        for (int i = 0; i < 2; i++) {
            int along = -36 + i * 72;
            BlockPos origin = northSouth ? new BlockPos(anchor.getX() + (i == 0 ? -34 : 2), anchor.getY(), anchor.getZ() + along)
                    : new BlockPos(anchor.getX() + along, anchor.getY(), anchor.getZ() + (i == 0 ? -34 : 2));
            lots.add(new Lot(origin, COMMERCIAL_LOT_WIDTH, COMMERCIAL_LOT_DEPTH, northSouth ? Direction.EAST : Direction.SOUTH, LotType.COMMERCIAL));
        }
        return lots;
    }

    private static Footprint compactFootprint(long seed, BlockPos anchor) {
        boolean northSouth = mix(seed, anchor.getX(), anchor.getZ()) % 2 == 0;
        Segment main = mainSegment(anchor, northSouth, 0);
        List<FootprintGeometry> geometry = new ArrayList<>();
        geometry.add(FootprintGeometry.road(main.withMargin(MAIN_CLEAR_MARGIN)));
        for (int along : BRANCH_ALONG) {
            geometry.add(FootprintGeometry.road(localSegment(anchor, main, along, 0).withMargin(LOCAL_CLEAR_MARGIN)));
        }
        for (Lot lot : plannedLots(anchor, northSouth)) geometry.add(FootprintGeometry.lot(lot, LOT_CLEAR_MARGIN));
        return buildFootprint(geometry);
    }

    private static Footprint candidateSearchFootprint(long seed, BlockPos anchor) {
        List<FootprintGeometry> geometry = new ArrayList<>();
        for (boolean northSouth : new boolean[]{true, false}) for (int offset : MAIN_OFFSETS) {
            Segment main = mainSegment(anchor, northSouth, offset);
            geometry.add(FootprintGeometry.road(main.withMargin(MAIN_CLEAR_MARGIN)));
            for (int along : BRANCH_ALONG) for (int localOffset : LOCAL_OFFSETS) {
                geometry.add(FootprintGeometry.road(localSegment(anchor, main, along, localOffset).withMargin(LOCAL_CLEAR_MARGIN)));
            }
        }
        return buildFootprint(geometry);
    }

    private static Footprint finalFootprint(List<RoadPlan> roads, List<Lot> lots) {
        List<FootprintGeometry> geometry = new ArrayList<>();
        for (RoadPlan road : roads) {
            int margin = road.segment().kind() == RoadClass.MAIN ? MAIN_CLEAR_MARGIN : LOCAL_CLEAR_MARGIN;
            geometry.add(FootprintGeometry.road(road.segment().withMargin(margin)));
        }
        for (Lot lot : lots) geometry.add(FootprintGeometry.lot(lot, LOT_CLEAR_MARGIN));
        return buildFootprint(geometry);
    }

    private static Footprint buildFootprint(List<FootprintGeometry> geometry) {
        if (geometry.isEmpty()) return new Footprint(List.of(), new AABB(0, 0, 0, 1, 1, 1), 0, 0, 0, 0, 0, 0, 0);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        Set<Long> roadCells = new HashSet<>(), lotCells = new HashSet<>();
        for (FootprintGeometry item : geometry) {
            minX = Math.min(minX, item.minX()); maxX = Math.max(maxX, item.maxX());
            minZ = Math.min(minZ, item.minZ()); maxZ = Math.max(maxZ, item.maxZ());
            Set<Long> target = item.road() ? roadCells : lotCells;
            for (BlockPos cell : item.cells()) target.add(cellKey(cell.getX(), cell.getZ()));
        }
        Set<Long> occupied = new HashSet<>(roadCells); occupied.addAll(lotCells);
        return new Footprint(List.copyOf(geometry), new AABB(minX, 0, minZ, maxX + 1, 1, maxZ + 1),
                minX, maxX, minZ, maxZ, roadCells.size(), lotCells.size(), occupied.size());
    }

    private static SettlementFitResult validateFootprint(ServerLevel level, Footprint footprint, long seed) {
        Set<Long> visited = new HashSet<>();
        int samples = 0, protectedHits = 0;
        FitHit first = null;
        for (FootprintGeometry geometry : footprint.geometry()) for (int[] point : geometry.samplePoints()) {
            if (!visited.add(cellKey(point[0], point[1]))) continue;
            samples++;
            StartupSettlementProtection.ProtectionClass protection = StartupSettlementProtection.protectionAt(point[0], point[1], seed);
            if (protection != StartupSettlementProtection.ProtectionClass.NONE) {
                protectedHits++;
                if (first == null) {
                    first = new FitHit(point[0], point[1], StartupPlainsEnclave.zoneAt(point[0], point[1], seed), protection,
                            StartupSettlementProtection.distanceFromCenter(point[0], point[1]),
                            StartupPlainsEnclave.plainsBoundary(point[0], point[1], seed),
                            StartupSettlementProtection.settlementProtectionBoundary(point[0], point[1], seed),
                            StartupPlainsEnclave.woodlandOuterBoundary(point[0], point[1], seed));
                }
            }
        }
        return new SettlementFitResult(protectedHits == 0, footprint.minX(), footprint.maxX(), footprint.minZ(), footprint.maxZ(),
                footprint.geometry().size(), samples, protectedHits, first, footprint.roadArea(), footprint.lotArea(), footprint.occupiedArea());
    }

    private static void logFootprint(String phase, BlockPos anchor, Footprint footprint, SettlementFitResult fit) {
        ApocalypseFirstLight.LOGGER.info("[AFL SETTLEMENT FOOTPRINT] anchor={} phase={} minX={} maxX={} minZ={} maxZ={} width={} depth={} roadSegments={} lotCount={} geometryCount={} sampleCount={} protectedHits={} fit={} roadArea={} lotArea={} occupiedArea={} boundingArea={}",
                anchor.toShortString(), phase, fit.minX(), fit.maxX(), fit.minZ(), fit.maxZ(), fit.width(), fit.depth(),
                footprint.geometry().stream().filter(FootprintGeometry::road).count(), footprint.geometry().stream().filter(g -> !g.road()).count(),
                fit.geometryCount(), fit.sampleCount(), fit.protectedHits(), fit.fit(), fit.roadArea(), fit.lotArea(), fit.occupiedArea(), fit.boundingArea());
        if (!fit.fit() && fit.firstProtectedHit() != null) {
            FitHit hit = fit.firstProtectedHit();
            ApocalypseFirstLight.LOGGER.info("[AFL SETTLEMENT FOOTPRINT REJECTED] phase={} reason=STARTUP_PROTECTED_ZONE_INTERSECTION firstHitX={} firstHitZ={} zone={} protectionClass={} distance={} plainsBoundary={} settlementProtectionBoundary={} woodlandBoundary={}",
                    phase, hit.x(), hit.z(), hit.zone(), hit.protection(), format(hit.distance()), hit.plainsBoundary(), hit.settlementProtectionBoundary(), hit.woodlandBoundary());
        }
    }

    private static String footprintDetail(Footprint footprint, SettlementFitResult fit) {
        return String.format("actualPlannedWidth=%d actualPlannedDepth=%d roadArea=%d lotArea=%d occupiedArea=%d boundingArea=%d geometryCount=%d sampleCount=%d protectedHits=%d fit=%s",
                fit.width(), fit.depth(), fit.roadArea(), fit.lotArea(), fit.occupiedArea(), fit.boundingArea(), fit.geometryCount(), fit.sampleCount(), fit.protectedHits(), fit.fit());
    }

    private static String footprintRejectionDetail(String phase, Footprint footprint, SettlementFitResult fit, long precheckMs, long roadPlanningMs, long finalValidationMs, long totalMs) {
        String first = "";
        if (fit.firstProtectedHit() != null) {
            FitHit hit = fit.firstProtectedHit();
            first = String.format(" firstHitX=%d firstHitZ=%d zone=%s protectionClass=%s distance=%s plainsBoundary=%d settlementProtectionBoundary=%d woodlandBoundary=%d",
                    hit.x(), hit.z(), hit.zone(), hit.protection(), format(hit.distance()), hit.plainsBoundary(), hit.settlementProtectionBoundary(), hit.woodlandBoundary());
        }
        return String.format("phase=%s %s %s%s", phase, footprintDetail(footprint, fit),
                timingDetail(precheckMs, roadPlanningMs, finalValidationMs, totalMs), first);
    }

    private static String timingDetail(long precheckMs, long roadPlanningMs, long finalValidationMs, long totalMs) {
        return String.format("precheckElapsedMs=%d roadPlanningElapsedMs=%d finalValidationElapsedMs=%d totalElapsedMs=%d",
                precheckMs, roadPlanningMs, finalValidationMs, totalMs);
    }

    private static long elapsedMs(long startNanos) { return (System.nanoTime() - startNanos) / 1_000_000L; }
    private static long cellKey(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    private static AABB unionBounds(AABB first, AABB second) {
        return new AABB(Math.min(first.minX, second.minX), 0, Math.min(first.minZ, second.minZ),
                Math.max(first.maxX, second.maxX), 1, Math.max(first.maxZ, second.maxZ));
    }

    private static RoadChoice chooseMainRoad(ServerLevel level, BlockPos anchor) {
        List<RoadChoice> choices = new ArrayList<>();
        for (boolean northSouth : new boolean[]{true, false}) for (int offset : MAIN_OFFSETS) {
            Segment segment = mainSegment(anchor, northSouth, offset);
            RoadPlan road = evaluateRoad(level, segment, null);
            RoadChoice choice = new RoadChoice(road, choices.size() + 1);
            choices.add(choice);
            logCandidate("MAIN", northSouth, offset, road.stats());
        }
        return choices.stream().filter(c -> c.plan().stats().valid())
                .min(Comparator.comparingDouble((RoadChoice c) -> c.plan().stats().earthworkCost())
                        .thenComparingInt(c -> c.plan().stats().maxCutDepth() + c.plan().stats().maxFillDepth())
                        .thenComparingInt(c -> c.plan().stats().waterFillBlocks()).thenComparingInt(RoadChoice::candidateCount))
                .map(choice -> {
                    RoadStats s = choice.plan().stats();
                    ApocalypseFirstLight.LOGGER.info("[AFL ROAD SELECTED] class=MAIN orientation={} offset={} earthworkCost={} cutBlocks={} fillBlocks={} waterFillBlocks={} maxCutDepth={} maxFillDepth={} gradeRoughness={} candidateCount={}",
                            choice.plan().segment().northSouth() ? "NORTH_SOUTH" : "EAST_WEST", choice.plan().segment().offset(), format(s.earthworkCost()),
                            s.cutBlocks(), s.fillBlocks(), s.waterFillBlocks(), s.maxCutDepth(), s.maxFillDepth(), s.maxTargetAdjacentDelta(), choices.size());
                    return choice;
                }).orElse(null);
    }

    private static RoadChoice chooseLocalRoad(ServerLevel level, BlockPos anchor, RoadPlan main, int along) {
        List<RoadChoice> choices = new ArrayList<>();
        for (int offset : LOCAL_OFFSETS) {
            Segment segment = localSegment(anchor, main.segment(), along, offset);
            int connectionX = main.segment().northSouth() ? main.segment().x1() : anchor.getX() + along + offset;
            int connectionZ = main.segment().northSouth() ? anchor.getZ() + along + offset : main.segment().z1();
            int connectionY = main.targetYAt(connectionX, connectionZ);
            RoadPlan original = evaluateRoad(level, segment, connectionY);
            int originalIntersectionTargetY = targetAt(original, segment.x1(), segment.z1());
            RoadPlan road = adjustLocalRoadPlan(level, main, original, originalIntersectionTargetY);
            RoadChoice choice = new RoadChoice(road, choices.size() + 1);
            choices.add(choice);
            logCandidate("LOCAL", segment.northSouth(), offset, road.stats());
        }
        return choices.stream().filter(c -> c.plan().stats().valid())
                .min(Comparator.comparingDouble((RoadChoice c) -> c.plan().stats().earthworkCost())
                        .thenComparingInt(c -> c.plan().stats().maxCutDepth() + c.plan().stats().maxFillDepth())
                        .thenComparingInt(RoadChoice::candidateCount))
                .map(choice -> {
                    RoadStats s = choice.plan().stats();
                    ApocalypseFirstLight.LOGGER.info("[AFL ROAD SELECTED] class=LOCAL orientation={} branch={} offset={} earthworkCost={} cutBlocks={} fillBlocks={} waterFillBlocks={} maxCutDepth={} maxFillDepth={} gradeRoughness={}",
                            choice.plan().segment().northSouth() ? "NORTH_SOUTH" : "EAST_WEST", along, choice.plan().segment().offset(), format(s.earthworkCost()),
                            s.cutBlocks(), s.fillBlocks(), s.waterFillBlocks(), s.maxCutDepth(), s.maxFillDepth(), s.maxTargetAdjacentDelta());
                    return choice;
                }).orElse(null);
    }

    private static RoadPlan adjustLocalRoadPlan(ServerLevel level, RoadPlan main, RoadPlan local, int originalIntersectionTargetY) {
        List<Station> adjustedStations = local.stations().stream()
                .map(station -> station.withTargetY(adjustedLocalTarget(main, local, station.x(), station.z(), station.targetY())))
                .toList();
        return new RoadPlan(local.segment(), calculateStats(level, local.segment(), adjustedStations), adjustedStations,
                originalIntersectionTargetY);
    }

    private static Segment mainSegment(BlockPos anchor, boolean northSouth, int offset) {
        if (northSouth) return new Segment(anchor.getX() + offset, anchor.getZ() - MAIN_HALF_LENGTH - STUB_LENGTH,
                anchor.getX() + offset, anchor.getZ() + MAIN_HALF_LENGTH + STUB_LENGTH, MAIN_ROAD_WIDTH, RoadClass.MAIN, true, offset);
        return new Segment(anchor.getX() - MAIN_HALF_LENGTH - STUB_LENGTH, anchor.getZ() + offset,
                anchor.getX() + MAIN_HALF_LENGTH + STUB_LENGTH, anchor.getZ() + offset, MAIN_ROAD_WIDTH, RoadClass.MAIN, false, offset);
    }

    private static Segment localSegment(BlockPos anchor, Segment main, int along, int offset) {
        int length = along == -16 ? 64 : 48;
        if (main.northSouth()) {
            int z = anchor.getZ() + along + offset;
            return new Segment(main.x1(), z, main.x1() - length, z, LOCAL_ROAD_WIDTH, RoadClass.LOCAL, false, offset);
        }
        int x = anchor.getX() + along + offset;
        return new Segment(x, main.z1(), x, main.z1() - length, LOCAL_ROAD_WIDTH, RoadClass.LOCAL, true, offset);
    }

    private static RoadPlan evaluateRoad(ServerLevel level, Segment segment, Integer forcedStartY) {
        List<Station> stations = new ArrayList<>();
        int length = Math.max(Math.abs(segment.x2() - segment.x1()), Math.abs(segment.z2() - segment.z1()));
        for (int distance = 0; distance <= length; distance += ROAD_SAMPLE_STEP) stations.add(sampleStation(level, segment, distance, length));
        if (stations.isEmpty() || stations.get(stations.size() - 1).distance() != length) stations.add(sampleStation(level, segment, length, length));
        for (int i = 0; i < stations.size(); i++) {
            Station station = stations.get(i);
            int target = station.naturalY();
            if (i == 0 && forcedStartY != null) target = forcedStartY;
            if (i > 0) target = clamp(target, stations.get(i - 1).targetY() - 1, stations.get(i - 1).targetY() + 1);
            stations.set(i, station.withTargetY(target));
        }
        return new RoadPlan(segment, calculateStats(level, segment, stations), List.copyOf(stations), Integer.MIN_VALUE);
    }

    private static Station sampleStation(ServerLevel level, Segment segment, int distance, int length) {
        int x = segment.x1() + (segment.x2() - segment.x1()) * distance / Math.max(1, length);
        int z = segment.z1() + (segment.z2() - segment.z1()) * distance / Math.max(1, length);
        ConstructionSurface construction = constructionSurface(level, x, z);
        if (!construction.valid()) return new Station(distance, x, z, construction.constructionGroundY(), construction.constructionGroundY(),
                construction.rawTopY(), construction.vegetationHeightIgnored(), false, 0, "INVALID", level);
        int waterDepth = 0;
        for (int y = construction.constructionGroundY() - 1; y >= construction.constructionGroundY() - 4; y--) {
            if (level.getFluidState(new BlockPos(x, y, z)).isEmpty()) break;
            waterDepth++;
        }
        BlockState surface = level.getBlockState(construction.ground());
        boolean water = !level.getFluidState(construction.ground()).isEmpty();
        boolean frozen = surface.is(Blocks.ICE) || surface.is(Blocks.FROSTED_ICE) || surface.is(Blocks.PACKED_ICE) || surface.is(Blocks.BLUE_ICE);
        return new Station(distance, x, z, construction.constructionGroundY(), construction.constructionGroundY(), construction.rawTopY(),
                construction.vegetationHeightIgnored(), true, waterDepth, water || frozen ? "WATER_OR_ICE" : "LAND", level);
    }

    private static ConstructionSurface constructionSurface(ServerLevel level, int x, int z) {
        SettlementSurfaceSampler.SurfaceSample raw = SettlementSurfaceSampler.sample(level, x, z);
        if (!raw.valid()) return new ConstructionSurface(x, z, raw.y(), raw.y(), 0, false);
        int y = raw.y() - 1;
        int ignored = 0;
        for (int depth = 0; depth <= MAX_CONSTRUCTION_SURFACE_SCAN; depth++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (isConstructionVegetation(state)) {
                ignored++;
                y--;
                continue;
            }
            if (!level.getFluidState(pos).isEmpty()) return new ConstructionSurface(x, z, raw.y(), y + 1, ignored, true);
            if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
                return new ConstructionSurface(x, z, raw.y(), y + 1, ignored, false);
            }
            return new ConstructionSurface(x, z, raw.y(), y + 1, ignored, true);
        }
        return new ConstructionSurface(x, z, raw.y(), y + 1, ignored, false);
    }

    private static boolean isConstructionVegetation(BlockState state) {
        if (!state.getFluidState().isEmpty()) return false;
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS) || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.VINE)
                || state.is(Blocks.SNOW) || state.canBeReplaced();
    }

    private static RoadStats calculateStats(ServerLevel level, Segment segment, List<Station> stations) {
        int invalid = 0, cut = 0, fill = 0, waterFill = 0, vegetation = 0, hard = 0;
        int maxCut = 0, maxFill = 0, maxNaturalDelta = 0, maxTargetDelta = 0, deepRun = 0, maxDeepRun = 0, waterRun = 0, maxWaterRun = 0;
        int supportedColumns = 0, fillRequiredColumns = 0, unsupportedColumns = 0, unresolvedColumns = 0;
        int fullWidthMaxFillDepth = 0, fullWidthMaxCutDepth = 0, foundationRepairableColumns = 0, maxFoundationDepth = 0;
        int maxUnsupportedRun = 0, unsupportedRun = 0, maxUnsupportedAcrossSection = 0;
        int rawTopMaxDelta = 0, vegetationColumnsIgnored = 0, maxVegetationHeightIgnored = 0;
        List<Integer> natural = new ArrayList<>();
        Map<Long, ConstructionSurface> surfaceCache = new HashMap<>();

        for (int i = 0; i < stations.size(); i++) {
            Station station = stations.get(i);
            natural.add(station.naturalY());
            rawTopMaxDelta = Math.max(rawTopMaxDelta, station.rawTopY() - station.naturalY());
            maxVegetationHeightIgnored = Math.max(maxVegetationHeightIgnored, station.vegetationHeightIgnored());
            if (i > 0) {
                maxNaturalDelta = Math.max(maxNaturalDelta, Math.abs(station.naturalY() - stations.get(i - 1).naturalY()));
                maxTargetDelta = Math.max(maxTargetDelta, Math.abs(station.targetY() - stations.get(i - 1).targetY()));
            }
            vegetation += estimateVegetation(station, segment);
        }

        int length = Math.max(Math.abs(segment.x2() - segment.x1()), Math.abs(segment.z2() - segment.z1()));
        int half = segment.width() / 2;
        int roadColumnSamples = 0, validColumns = 0;
        for (int distance = 0; distance <= length; distance++) {
            int x = segment.x1() + (segment.x2() - segment.x1()) * distance / Math.max(1, length);
            int z = segment.z1() + (segment.z2() - segment.z1()) * distance / Math.max(1, length);
            int targetY = targetAt(segment, stations, x, z);
            boolean deepAtDistance = false, waterAtDistance = false;
            int unsupportedAtDistance = 0;
            for (int lateral = -half; lateral <= half; lateral++) {
                int columnX = segment.northSouth() ? x + lateral : x;
                int columnZ = segment.northSouth() ? z : z + lateral;
                BlockPos columnPos = new BlockPos(columnX, 0, columnZ);
                ConstructionSurface construction = surfaceCache.computeIfAbsent(cellKey(columnX, columnZ),
                        ignored -> constructionSurface(level, columnX, columnZ));
                roadColumnSamples++;
                if (!construction.valid()) {
                    invalid++;
                    hard++;
                    continue;
                }
                validColumns++;
                if (construction.vegetationHeightIgnored() > 0) {
                    vegetationColumnsIgnored++;
                    rawTopMaxDelta = Math.max(rawTopMaxDelta, construction.rawTopY() - construction.constructionGroundY());
                    maxVegetationHeightIgnored = Math.max(maxVegetationHeightIgnored, construction.vegetationHeightIgnored());
                }
                int difference = construction.constructionGroundY() - targetY;
                int cutDepth = Math.max(0, difference);
                int fillDepth = Math.max(0, -difference);
                cut += cutDepth;
                fill += fillDepth;
                maxCut = Math.max(maxCut, cutDepth);
                maxFill = Math.max(maxFill, fillDepth);
                fullWidthMaxCutDepth = Math.max(fullWidthMaxCutDepth, cutDepth);

                RoadbedSupportProbe probe = probeRoadbed(level, columnPos, targetY - 1, MAX_ALLOWED_CUT_FILL_DEPTH);
                if (probe.supportFound()) {
                    supportedColumns++;
                } else {
                    unsupportedColumns++;
                    unsupportedAtDistance++;
                    RoadbedSupportProbe foundation = probeRoadbed(level, columnPos, targetY - 1, MAX_ISOLATED_FOUNDATION_DEPTH);
                    if (foundation.supportFound()) {
                        foundationRepairableColumns++;
                        maxFoundationDepth = Math.max(maxFoundationDepth, foundation.requiredFillDepth());
                    } else {
                        unresolvedColumns++;
                        hard++;
                    }
                }
                if (probe.requiredFillDepth() > 0) fillRequiredColumns++;
                fullWidthMaxFillDepth = Math.max(fullWidthMaxFillDepth, probe.requiredFillDepth());

                BlockState surface = level.getBlockState(construction.ground());
                boolean waterOrIce = !level.getFluidState(construction.ground()).isEmpty()
                        || surface.is(Blocks.ICE) || surface.is(Blocks.FROSTED_ICE)
                        || surface.is(Blocks.PACKED_ICE) || surface.is(Blocks.BLUE_ICE)
                        || probe.fluidDepth() > 0;
                if (waterOrIce) {
                    waterFill++;
                    waterAtDistance = true;
                }
                if (Math.max(cutDepth, fillDepth) > MAX_NORMAL_CUT_FILL_DEPTH) deepAtDistance = true;
            }
            if (unsupportedAtDistance > 0) {
                unsupportedRun++;
                maxUnsupportedAcrossSection = Math.max(maxUnsupportedAcrossSection, unsupportedAtDistance);
            } else {
                maxUnsupportedRun = Math.max(maxUnsupportedRun, unsupportedRun);
                unsupportedRun = 0;
            }
            if (deepAtDistance) deepRun++; else { maxDeepRun = Math.max(maxDeepRun, deepRun); deepRun = 0; }
            if (waterAtDistance) waterRun++; else { maxWaterRun = Math.max(maxWaterRun, waterRun); waterRun = 0; }
        }
        maxUnsupportedRun = Math.max(maxUnsupportedRun, unsupportedRun);
        maxDeepRun = Math.max(maxDeepRun, deepRun);
        maxWaterRun = Math.max(maxWaterRun, waterRun);
        double waterRatio = validColumns == 0 ? 1.0D : (double) waterFill / (double) validColumns;
        if (maxDeepRun > MAX_CONSECUTIVE_DEEP_STATIONS || maxWaterRun > MAX_CONSECUTIVE_WATER_STATIONS || waterRatio > MAX_WATER_STATION_RATIO) hard++;
        String unsupportedPolicyReason = unsupportedPolicyReason(segment.kind(), roadColumnSamples, unsupportedColumns,
                unresolvedColumns, maxUnsupportedRun, maxUnsupportedAcrossSection, maxFoundationDepth);
        boolean roadbedPolicyValid = unsupportedPolicyReason.equals("OK");
        if (!roadbedPolicyValid) hard++;
        String rejectionReason = !roadbedPolicyValid ? "ROADBED_UNSUPPORTED" : invalid > 0 ? "INVALID_SURFACE" : hard > 0 ? "ROAD_TERRAIN" : "OK";
        double cost = cut * CUT_COST + fill * FILL_COST + waterFill * WATER_COST + vegetation * 0.05D
                + foundationRepairableColumns * FOUNDATION_REPAIR_COST;
        boolean valid = validColumns > 0 && invalid == 0 && unresolvedColumns == 0 && roadbedPolicyValid && hard == 0;
        return new RoadStats(valid, roadColumnSamples, validColumns, invalid, min(natural), max(natural),
                maxNaturalDelta, maxTargetDelta, cut, fill, maxCut, fullWidthMaxFillDepth, waterFill, vegetation,
                hard, maxDeepRun, maxWaterRun, waterRatio, cost, segment.width(), roadColumnSamples,
                supportedColumns, fillRequiredColumns, unsupportedColumns, unresolvedColumns, foundationRepairableColumns,
                rawTopMaxDelta, vegetationColumnsIgnored, maxVegetationHeightIgnored, maxUnsupportedRun,
                maxUnsupportedAcrossSection, maxFoundationDepth, fullWidthMaxFillDepth, fullWidthMaxCutDepth,
                rejectionReason, unsupportedPolicyReason, foundationRepairableColumns > 0);
    }

    private static String unsupportedPolicyReason(RoadClass kind, int roadColumns, int unsupportedColumns, int unresolvedColumns,
                                                  int maxUnsupportedRun, int maxUnsupportedAcrossSection, int maxFoundationDepth) {
        if (unresolvedColumns > 0 || maxFoundationDepth > MAX_ISOLATED_FOUNDATION_DEPTH) return "FOUNDATION_TOO_DEEP";
        if (unsupportedColumns == 0) return "OK";
        int maxColumns = kind == RoadClass.MAIN ? MAIN_MAX_ISOLATED_UNSUPPORTED_COLUMNS : LOCAL_MAX_ISOLATED_UNSUPPORTED_COLUMNS;
        double maxRatio = kind == RoadClass.MAIN ? MAIN_MAX_UNSUPPORTED_RATIO : LOCAL_MAX_UNSUPPORTED_RATIO;
        int maxRun = kind == RoadClass.MAIN ? MAIN_MAX_UNSUPPORTED_RUN : LOCAL_MAX_UNSUPPORTED_RUN;
        int maxAcross = kind == RoadClass.MAIN ? MAIN_MAX_UNSUPPORTED_ACROSS_SECTION : LOCAL_MAX_UNSUPPORTED_ACROSS_SECTION;
        if (unsupportedColumns > maxColumns) return "TOO_MANY_COLUMNS";
        if ((double) unsupportedColumns / Math.max(1, roadColumns) > maxRatio) return "RATIO_TOO_HIGH";
        if (maxUnsupportedRun > maxRun) return "RUN_TOO_LONG";
        if (maxUnsupportedAcrossSection > maxAcross) return "SECTION_TOO_WIDE";
        return "OK";
    }

    private static int estimateVegetation(Station station, Segment segment) {
        int count = 0, half = segment.width() / 2;
        for (int lateral = -half; lateral <= half; lateral++) {
            int x = segment.northSouth() ? station.x() + lateral : station.x();
            int z = segment.northSouth() ? station.z() : station.z() + lateral;
            for (int dy = 0; dy <= 6; dy++) {
                BlockState state = station.level().getBlockState(new BlockPos(x, station.naturalY() + dy - 1, z));
                if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)) count++;
            }
        }
        return count;
    }

    private static void logCandidate(String kind, boolean northSouth, int offset, RoadStats stats) {
        ApocalypseFirstLight.LOGGER.info("[AFL ROAD CANDIDATE] class={} orientation={} offset={} valid={} rejectionReason={} unsupportedPolicyReason={} roadWidth={} roadColumnSamples={} supportedColumns={} fillRequiredColumns={} unsupportedColumns={} unsupportedRatio={} maxUnsupportedRun={} maxUnsupportedAcrossSection={} unresolvedColumns={} foundationRepairableColumns={} foundationRepairRequired={} maxFoundationDepth={} fullWidthMaxFillDepth={} fullWidthMaxCutDepth={} rawTopMaxDelta={} vegetationColumnsIgnored={} maxVegetationHeightIgnored={} cutBlocks={} fillBlocks={} maxCutDepth={} maxFillDepth={} waterFillBlocks={} vegetationClearEstimate={} invalidSamples={} hardObstacleCount={} earthworkCost={} maxDeepRun={} maxWaterRun={} gradeRoughness={}",
                kind, northSouth ? "NORTH_SOUTH" : "EAST_WEST", offset, stats.valid(), stats.rejectionReason(), stats.unsupportedPolicyReason(),
                stats.roadWidth(), stats.roadColumnSamples(), stats.supportedColumns(), stats.fillRequiredColumns(), stats.unsupportedColumns(),
                format(stats.unsupportedRatio()), stats.maxUnsupportedRun(), stats.maxUnsupportedAcrossSection(), stats.unresolvedColumns(),
                stats.foundationRepairableColumns(), stats.foundationRepairRequired(), stats.maxFoundationDepth(), stats.fullWidthMaxFillDepth(),
                stats.fullWidthMaxCutDepth(), stats.rawTopMaxDelta(), stats.vegetationColumnsIgnored(), stats.maxVegetationHeightIgnored(),
                stats.cutBlocks(), stats.fillBlocks(), stats.maxCutDepth(), stats.maxFillDepth(), stats.waterFillBlocks(), stats.vegetationClearEstimate(),
                stats.invalidSamples(), stats.hardObstacleCount(), format(stats.earthworkCost()), stats.maxDeepRun(), stats.maxWaterRun(), stats.maxTargetAdjacentDelta());
        if (!stats.valid()) ApocalypseFirstLight.LOGGER.info("[AFL ROAD REJECTED] class={} orientation={} offset={} reason={} unsupportedPolicyReason={} invalidSamples={} hardObstacleCount={} unresolvedColumns={} unsupportedColumns={} maxCutDepth={} maxFillDepth={} waterRatio={} maxDeepRun={} maxWaterRun={}",
                kind, northSouth ? "NORTH_SOUTH" : "EAST_WEST", offset, stats.rejectionReason(), stats.unsupportedPolicyReason(), stats.invalidSamples(),
                stats.hardObstacleCount(), stats.unresolvedColumns(), stats.unsupportedColumns(), stats.maxCutDepth(), stats.maxFillDepth(),
                format(stats.waterStationRatio()), stats.maxDeepRun(), stats.maxWaterRun());
    }

    private static Result apply(ServerLevel level, Plan plan) {
        long applyStart = System.nanoTime();
        RoadTargetMap roadTargetMap = buildRoadTargetMap(plan);
        for (IntersectionDiagnostic intersection : roadTargetMap.intersections()) {
            ApocalypseFirstLight.LOGGER.info("[AFL ROAD INTERSECTION] x={} z={} mainTargetY={} localOriginalTargetY={} localAdjustedTargetY={} transitionLength={} overlapWidth={} gradeDeltaBefore={} gradeDeltaAfter={}",
                    intersection.x(), intersection.z(), intersection.mainTargetY(), intersection.localOriginalTargetY(),
                    intersection.localAdjustedTargetY(), intersection.transitionLength(), intersection.overlapWidth(),
                    intersection.gradeDeltaBefore(), intersection.gradeDeltaAfter());
        }

        RoadbedConsistency consistency = inspectRoadbed(level, roadTargetMap);
        ApocalypseFirstLight.LOGGER.info("[AFL ROADBED CONSISTENCY] roadColumns={} supportedColumns={} filledColumns={} unsupportedColumns={} foundationRepairableColumns={} foundationFilledBlocks={} unresolvedColumns={} maxActualFillDepth={} plannerMaxFillDepth={} maxFoundationDepth={} mismatchCount={}",
                consistency.roadColumns(), consistency.supportedColumns(), consistency.filledColumns(),
                consistency.unsupportedColumns(), consistency.foundationRepairableColumns(), consistency.foundationFilledBlocks(),
                consistency.unresolvedColumns(), consistency.maxActualFillDepth(), consistency.plannerMaxFillDepth(),
                consistency.maxFoundationDepth(), consistency.mismatchCount());
        if (consistency.mismatchCount() > 0) {
            String detail = String.format("roadbedConsistency=FAILED roadColumns=%d supportedColumns=%d filledColumns=%d unsupportedColumns=%d foundationRepairableColumns=%d foundationFilledBlocks=%d unresolvedColumns=%d maxActualFillDepth=%d plannerMaxFillDepth=%d maxFoundationDepth=%d mismatchCount=%d",
                    consistency.roadColumns(), consistency.supportedColumns(), consistency.filledColumns(), consistency.unsupportedColumns(),
                    consistency.foundationRepairableColumns(), consistency.foundationFilledBlocks(), consistency.unresolvedColumns(),
                    consistency.maxActualFillDepth(), consistency.plannerMaxFillDepth(), consistency.maxFoundationDepth(), consistency.mismatchCount());
            return new Result(false, "ROADBED_PLAN_APPLY_MISMATCH", plan, 0, 0, 0, detail);
        }

        int logs = 0, leaves = 0, other = 0, cut = 0, roadbedFilledBlocks = 0, water = 0;
        Map<BlockPos, Integer> roadCells = new HashMap<>();
        for (Map.Entry<BlockPos, Integer> entry : roadTargetMap.targets().entrySet()) {
            BlockPos footprint = entry.getKey();
            ConstructionSurface construction = constructionSurface(level, footprint.getX(), footprint.getZ());
            if (!construction.valid()) continue;
            int naturalGround = construction.constructionGroundY() - 1;
            int targetGround = entry.getValue() - 1;
            for (int y = targetGround; y <= naturalGround + ROAD_CLEARANCE_HEIGHT; y++) {
                BlockPos p = new BlockPos(footprint.getX(), y, footprint.getZ());
                if (!level.getFluidState(p).isEmpty()) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    water++;
                }
            }
            if (naturalGround > targetGround) {
                for (int y = targetGround; y <= naturalGround; y++) {
                    level.setBlock(new BlockPos(footprint.getX(), y, footprint.getZ()), Blocks.AIR.defaultBlockState(), 3);
                    cut++;
                }
            }
            RoadbedColumn column = consistency.columns().get(footprint);
            if (column != null && column.fillDepth() > 0) {
                for (int y = targetGround - column.fillDepth(); y < targetGround; y++) {
                    BlockPos foundation = new BlockPos(footprint.getX(), y, footprint.getZ());
                    if (!level.getBlockState(foundation).is(Blocks.STONE)) {
                        level.setBlock(foundation, Blocks.STONE.defaultBlockState(), 3);
                        roadbedFilledBlocks++;
                    }
                }
            }
            BlockPos roadSurface = new BlockPos(footprint.getX(), targetGround, footprint.getZ());
            level.setBlock(roadSurface, Blocks.GRAY_CONCRETE.defaultBlockState(), 3);
            roadCells.put(footprint, targetGround);
        }

        Map<BlockPos, Integer> rowColumns = buildRowColumns(plan, roadTargetMap, roadCells);
        RowClearanceResult rowClearance = clearRoadRow(level, roadCells, rowColumns);
        logs += rowClearance.logsRemoved();
        leaves += rowClearance.leavesRemoved();
        other += rowClearance.otherVegetationRemoved();

        EdgeClosureResult edgeClosure = closeRoadEdgeCavities(level, roadCells, rowColumns);

        long applyElapsed = elapsedMs(applyStart);
        String detail = (plan.detail() == null ? "" : plan.detail() + " ") + String.format("roadApply=APPLIED roads=%d mainCandidates=%d cutBlocks=%d fillBlocks=%d waterFillBlocks=%d roadbedFilledBlocks=%d roadbedSupportedColumns=%d roadbedUnsupportedColumns=%d foundationRepairableColumns=%d foundationFilledBlocks=%d unresolvedColumns=%d intersectionAdjustedStations=%d postClearLeaves=%d postClearLogs=%d postClearVegetation=%d applyElapsedMs=%d vegetationCleared=%d rowColumns=%d rowMarginColumns=%d scanHeight=%d treeSeedsFound=%d treesFullyRemoved=%d remainingRoadLogs=%d remainingRoadLeaves=%d remainingRoadVegetation=%d edgeColumnsChecked=%d cavityColumnsFound=%d cavityColumnsClosed=%d deepCavityColumnsSkipped=%d edgeFillBlocks=%d maxEdgeClosureDepth=%d",
                plan.roadPlans().size(), plan.mainCandidateCount(), cut, roadbedFilledBlocks, water, roadbedFilledBlocks,
                consistency.supportedColumns(), consistency.unsupportedColumns(), consistency.foundationRepairableColumns(), consistency.foundationFilledBlocks(),
                consistency.unresolvedColumns(), roadTargetMap.intersectionAdjustedStations(), rowClearance.leavesRemoved(),
                rowClearance.logsRemoved(), rowClearance.otherVegetationRemoved(), applyElapsed, rowClearance.logsRemoved() + rowClearance.leavesRemoved() + rowClearance.otherVegetationRemoved(),
                rowClearance.rowColumns(), rowClearance.rowMarginColumns(), rowClearance.scanHeight(), rowClearance.treeSeedsFound(), rowClearance.treesFullyRemoved(),
                rowClearance.remainingRoadLogs(), rowClearance.remainingRoadLeaves(), rowClearance.remainingRoadVegetation(), edgeClosure.edgeColumnsChecked(),
                edgeClosure.cavityColumnsFound(), edgeClosure.cavityColumnsClosed(), edgeClosure.deepCavityColumnsSkipped(), edgeClosure.edgeFillBlocks(), edgeClosure.maxEdgeClosureDepth());
        ApocalypseFirstLight.LOGGER.info("[AFL ROAD ROW CLEARANCE] roadColumns={} rowColumns={} rowMarginColumns={} scanHeight={} logsRemoved={} leavesRemoved={} otherVegetationRemoved={} treeSeedsFound={} treesFullyRemoved={} remainingRoadLogs={} remainingRoadLeaves={} remainingRoadVegetation={}",
                rowClearance.roadColumns(), rowClearance.rowColumns(), rowClearance.rowMarginColumns(), rowClearance.scanHeight(), rowClearance.logsRemoved(),
                rowClearance.leavesRemoved(), rowClearance.otherVegetationRemoved(), rowClearance.treeSeedsFound(), rowClearance.treesFullyRemoved(),
                rowClearance.remainingRoadLogs(), rowClearance.remainingRoadLeaves(), rowClearance.remainingRoadVegetation());
        ApocalypseFirstLight.LOGGER.info("[AFL ROAD EDGE CLOSURE] edgeColumnsChecked={} cavityColumnsFound={} cavityColumnsClosed={} deepCavityColumnsSkipped={} edgeFillBlocks={} maxEdgeClosureDepth={}",
                edgeClosure.edgeColumnsChecked(), edgeClosure.cavityColumnsFound(), edgeClosure.cavityColumnsClosed(), edgeClosure.deepCavityColumnsSkipped(),
                edgeClosure.edgeFillBlocks(), edgeClosure.maxEdgeClosureDepth());
        ApocalypseFirstLight.LOGGER.info("[AFL ROAD APPLY] {}", detail);
        return new Result(true, "OK", plan, rowClearance.logsRemoved(), rowClearance.leavesRemoved(), rowClearance.otherVegetationRemoved(), detail);
    }

    private static Map<BlockPos, Integer> buildRowColumns(Plan plan, RoadTargetMap roadTargetMap, Map<BlockPos, Integer> roadCells) {
        Map<BlockPos, Integer> rowColumns = new HashMap<>();
        for (RoadPlan road : plan.roadPlans()) {
            for (BlockPos cell : road.segment().withMargin(ROAD_ROW_MARGIN).cells()) {
                int target = targetAt(road, cell.getX(), cell.getZ());
                if (roadTargetMap.targets().containsKey(cell)) target = roadTargetMap.targets().get(cell);
                rowColumns.putIfAbsent(cell, target);
            }
        }
        rowColumns.putAll(roadCells);
        return rowColumns;
    }

    private static RowClearanceResult clearRoadRow(ServerLevel level, Map<BlockPos, Integer> roadCells,
                                                   Map<BlockPos, Integer> rowColumns) {
        Set<BlockPos> processedLogs = new HashSet<>();
        List<TreeCleanup> trees = new ArrayList<>();
        for (Map.Entry<BlockPos, Integer> entry : rowColumns.entrySet()) {
            BlockPos column = entry.getKey();
            int targetGround = entry.getValue();
            for (int y = targetGround + 1; y <= targetGround + CLEARANCE_SCAN_HEIGHT; y++) {
                BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                if (!level.getBlockState(pos).is(BlockTags.LOGS) || !processedLogs.add(pos)) continue;
                TreeCleanup tree = collectTree(level, pos);
                processedLogs.addAll(tree.logs());
                trees.add(tree);
            }
        }

        int logsRemoved = 0;
        int leavesRemoved = 0;
        int otherVegetationRemoved = 0;
        int treesFullyRemoved = 0;
        for (TreeCleanup tree : trees) {
            for (BlockPos pos : tree.logs()) {
                BlockState state = level.getBlockState(pos);
                if (!isRoadVegetation(state)) continue;
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                if (state.is(BlockTags.LOGS)) logsRemoved++;
                else if (state.is(BlockTags.LEAVES)) leavesRemoved++;
                else otherVegetationRemoved++;
            }
            for (BlockPos pos : tree.leaves()) {
                BlockState state = level.getBlockState(pos);
                if (!isRoadVegetation(state)) continue;
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                if (state.is(BlockTags.LOGS)) logsRemoved++;
                else if (state.is(BlockTags.LEAVES)) leavesRemoved++;
                else otherVegetationRemoved++;
            }
            boolean logsRemain = tree.logs().stream().anyMatch(pos -> level.getBlockState(pos).is(BlockTags.LOGS));
            if (!logsRemain) treesFullyRemoved++;
        }

        for (Map.Entry<BlockPos, Integer> entry : rowColumns.entrySet()) {
            BlockPos column = entry.getKey();
            int targetGround = entry.getValue();
            for (int y = targetGround + 1; y <= targetGround + CLEARANCE_SCAN_HEIGHT; y++) {
                BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                BlockState state = level.getBlockState(pos);
                if (!isRoadVegetation(state)) continue;
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                if (state.is(BlockTags.LOGS)) logsRemoved++;
                else if (state.is(BlockTags.LEAVES)) leavesRemoved++;
                else otherVegetationRemoved++;
            }
        }

        int remainingRoadLogs = 0;
        int remainingRoadLeaves = 0;
        int remainingRoadVegetation = 0;
        for (Map.Entry<BlockPos, Integer> entry : roadCells.entrySet()) {
            BlockPos column = entry.getKey();
            for (int y = entry.getValue() + 1; y <= entry.getValue() + REMAINING_CLEARANCE_HEIGHT; y++) {
                BlockState state = level.getBlockState(new BlockPos(column.getX(), y, column.getZ()));
                if (!isRoadVegetation(state)) continue;
                remainingRoadVegetation++;
                if (state.is(BlockTags.LOGS)) remainingRoadLogs++;
                else if (state.is(BlockTags.LEAVES)) remainingRoadLeaves++;
            }
        }
        int rowMarginColumns = Math.max(0, rowColumns.size() - roadCells.size());
        return new RowClearanceResult(roadCells.size(), rowColumns.size(), rowMarginColumns, CLEARANCE_SCAN_HEIGHT,
                logsRemoved, leavesRemoved, otherVegetationRemoved, trees.size(), treesFullyRemoved,
                remainingRoadLogs, remainingRoadLeaves, remainingRoadVegetation);
    }

    private static TreeCleanup collectTree(ServerLevel level, BlockPos seed) {
        Set<BlockPos> logs = new HashSet<>();
        Set<BlockPos> leaves = new HashSet<>();
        ArrayDeque<BlockPos> logQueue = new ArrayDeque<>();
        logQueue.add(seed);
        int minX = seed.getX() - TREE_HORIZONTAL_RADIUS;
        int maxX = seed.getX() + TREE_HORIZONTAL_RADIUS;
        int minZ = seed.getZ() - TREE_HORIZONTAL_RADIUS;
        int maxZ = seed.getZ() + TREE_HORIZONTAL_RADIUS;
        int minY = seed.getY() - TREE_SCAN_DOWN;
        int maxY = seed.getY() + TREE_SCAN_UP;
        while (!logQueue.isEmpty()) {
            BlockPos pos = logQueue.removeFirst();
            if (!insideTreeBounds(pos, minX, maxX, minY, maxY, minZ, maxZ) || !logs.add(pos)) continue;
            if (!level.getBlockState(pos).is(BlockTags.LOGS)) {
                logs.remove(pos);
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (insideTreeBounds(next, minX, maxX, minY, maxY, minZ, maxZ)
                        && level.getBlockState(next).is(BlockTags.LOGS)) logQueue.addLast(next);
            }
        }

        ArrayDeque<BlockPos> leafQueue = new ArrayDeque<>();
        for (BlockPos log : logs) {
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
                BlockPos next = log.offset(dx, dy, dz);
                if (insideTreeBounds(next, minX, maxX, minY, maxY, minZ, maxZ)
                        && level.getBlockState(next).is(BlockTags.LEAVES)) leafQueue.addLast(next);
            }
        }
        while (!leafQueue.isEmpty()) {
            BlockPos pos = leafQueue.removeFirst();
            if (!insideTreeBounds(pos, minX, maxX, minY, maxY, minZ, maxZ) || !leaves.add(pos)) continue;
            if (!level.getBlockState(pos).is(BlockTags.LEAVES)) {
                leaves.remove(pos);
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos next = pos.offset(dx, dy, dz);
                if (insideTreeBounds(next, minX, maxX, minY, maxY, minZ, maxZ)
                        && level.getBlockState(next).is(BlockTags.LEAVES)) leafQueue.addLast(next);
            }
        }
        return new TreeCleanup(logs, leaves);
    }

    private static boolean insideTreeBounds(BlockPos pos, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private static EdgeClosureResult closeRoadEdgeCavities(ServerLevel level, Map<BlockPos, Integer> roadCells,
                                                           Map<BlockPos, Integer> rowColumns) {
        int edgeColumnsChecked = 0;
        int cavityColumnsFound = 0;
        int cavityColumnsClosed = 0;
        int deepCavityColumnsSkipped = 0;
        int edgeFillBlocks = 0;
        int maxEdgeClosureDepth = 0;
        Set<BlockPos> checked = new HashSet<>();
        for (BlockPos road : roadCells.keySet()) {
            int roadGround = roadCells.get(road);
            for (int dx = -ROAD_ROW_MARGIN; dx <= ROAD_ROW_MARGIN; dx++) for (int dz = -ROAD_ROW_MARGIN; dz <= ROAD_ROW_MARGIN; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 0) continue;
                BlockPos edge = new BlockPos(road.getX() + dx, 0, road.getZ() + dz);
                if (!rowColumns.containsKey(edge) || roadCells.containsKey(edge) || !checked.add(edge)) continue;
                boolean adjacent = false;
                for (int nx = -1; nx <= 1; nx++) for (int nz = -1; nz <= 1; nz++) {
                    if (nx == 0 && nz == 0) continue;
                    if (roadCells.containsKey(new BlockPos(edge.getX() + nx, 0, edge.getZ() + nz))) adjacent = true;
                }
                if (!adjacent) continue;
                edgeColumnsChecked++;
                int targetGround = rowColumns.get(edge);
                BlockPos top = new BlockPos(edge.getX(), targetGround, edge.getZ());
                if (!isEdgeCavity(level, top)) continue;
                cavityColumnsFound++;
                EdgeSupport support = findEdgeSupport(level, top);
                if (!support.found()) {
                    deepCavityColumnsSkipped++;
                    continue;
                }
                int fillDepth = targetGround - support.supportY();
                maxEdgeClosureDepth = Math.max(maxEdgeClosureDepth, fillDepth);
                for (int y = support.supportY() + 1; y <= targetGround; y++) {
                    BlockPos fill = new BlockPos(edge.getX(), y, edge.getZ());
                    BlockState desired = y == targetGround ? edgeTopMaterial(level, edge.getX(), edge.getZ(), roadGround) : Blocks.STONE.defaultBlockState();
                    if (!level.getBlockState(fill).equals(desired)) {
                        level.setBlock(fill, desired, 3);
                        edgeFillBlocks++;
                    }
                }
                cavityColumnsClosed++;
            }
        }
        return new EdgeClosureResult(edgeColumnsChecked, cavityColumnsFound, cavityColumnsClosed, deepCavityColumnsSkipped,
                edgeFillBlocks, maxEdgeClosureDepth);
    }

    private static boolean isEdgeCavity(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty() || level.getBlockState(pos).canBeReplaced();
    }

    private static EdgeSupport findEdgeSupport(ServerLevel level, BlockPos top) {
        for (int depth = 1; depth <= MAX_EDGE_CLOSURE_DEPTH; depth++) {
            BlockPos pos = top.below(depth);
            BlockState state = level.getBlockState(pos);
            if (isStableRoadbed(level, pos, state)) return new EdgeSupport(true, pos.getY());
            if (!isRoadbedFillable(level, pos, state)) return new EdgeSupport(false, 0);
        }
        return new EdgeSupport(false, 0);
    }

    private static BlockState edgeTopMaterial(ServerLevel level, int x, int z, int referenceGround) {
        for (int radius = 1; radius <= ROAD_ROW_MARGIN + 1; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                BlockState state = level.getBlockState(new BlockPos(x + dx, referenceGround, z + dz));
                if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                        || state.is(Blocks.GRAVEL)) return state;
            }
        }
        return Blocks.DIRT.defaultBlockState();
    }

    private static RoadTargetMap buildRoadTargetMap(Plan plan) {
        Map<BlockPos, Integer> targets = new HashMap<>();
        Map<BlockPos, RoadClass> kinds = new HashMap<>();
        Set<BlockPos> mainCells = new HashSet<>();
        RoadPlan main = plan.roadPlans().get(0);
        for (BlockPos cell : main.segment().cells()) {
            targets.put(cell, targetAt(main, cell.getX(), cell.getZ()));
            kinds.put(cell, RoadClass.MAIN);
            mainCells.add(cell);
        }
        List<IntersectionDiagnostic> intersections = new ArrayList<>();
        int adjustedStations = 0;
        for (int roadIndex = 1; roadIndex < plan.roadPlans().size(); roadIndex++) {
            RoadPlan local = plan.roadPlans().get(roadIndex);
            int ix = local.segment().x1();
            int iz = local.segment().z1();
            int mainTarget = targetAt(main, ix, iz);
            int localOriginal = local.originalIntersectionTargetY() == Integer.MIN_VALUE
                    ? targetAt(local, ix, iz) : local.originalIntersectionTargetY();
            intersections.add(new IntersectionDiagnostic(ix, iz, mainTarget, localOriginal, mainTarget,
                    INTERSECTION_TRANSITION_DISTANCE, Math.min(main.segment().width(), local.segment().width()),
                    Math.abs(mainTarget - localOriginal), 0));
            for (Station station : local.stations()) if (station.distance() <= INTERSECTION_TRANSITION_DISTANCE) adjustedStations++;
            for (BlockPos cell : local.segment().cells()) {
                int adjustedTarget = targetAt(local, cell.getX(), cell.getZ());
                if (mainCells.contains(cell)) {
                    targets.put(cell, targetAt(main, cell.getX(), cell.getZ()));
                } else {
                    targets.putIfAbsent(cell, adjustedTarget);
                    kinds.putIfAbsent(cell, RoadClass.LOCAL);
                }
            }
        }
        return new RoadTargetMap(targets, kinds, intersections, adjustedStations);
    }

    private static int adjustedLocalTarget(RoadPlan main, RoadPlan local, int x, int z, int localTarget) {
        int distance = roadDistance(local.segment(), x, z);
        int mainTarget = targetAt(main, x, z);
        if (distance <= INTERSECTION_OVERLAP_DISTANCE) return mainTarget;
        if (distance >= INTERSECTION_TRANSITION_DISTANCE) return localTarget;
        double fraction = (double) (distance - INTERSECTION_OVERLAP_DISTANCE)
                / (INTERSECTION_TRANSITION_DISTANCE - INTERSECTION_OVERLAP_DISTANCE);
        return (int) Math.round(mainTarget + (localTarget - mainTarget) * fraction);
    }

    private static int roadDistance(Segment segment, int x, int z) {
        return segment.northSouth() ? Math.abs(z - segment.z1()) : Math.abs(x - segment.x1());
    }

    private static RoadbedConsistency inspectRoadbed(ServerLevel level, RoadTargetMap roadTargetMap) {
        Map<BlockPos, RoadbedColumn> columns = new HashMap<>();
        int supported = 0, filledColumns = 0, unsupported = 0, foundationRepairable = 0, foundationFilledBlocks = 0;
        int unresolved = 0, maxDepth = 0, maxFoundationDepth = 0, filledBlocks = 0;
        for (Map.Entry<BlockPos, Integer> entry : roadTargetMap.targets().entrySet()) {
            BlockPos cell = entry.getKey();
            int targetGround = entry.getValue() - 1;
            RoadbedColumn column = inspectRoadbedColumn(level, cell, targetGround, roadTargetMap.kinds().getOrDefault(cell, RoadClass.MAIN));
            columns.put(cell, column);
            if (column.supported()) supported++;
            if (column.normalUnsupported()) unsupported++;
            if (column.foundationRepairable()) {
                foundationRepairable++;
                foundationFilledBlocks += column.foundationDepth();
                maxFoundationDepth = Math.max(maxFoundationDepth, column.foundationDepth());
            }
            if (column.unresolved()) unresolved++;
            if (column.fillDepth() > 0) {
                filledColumns++;
                filledBlocks += column.fillDepth();
            }
            maxDepth = Math.max(maxDepth, column.normalRequiredFillDepth());
        }
        return new RoadbedConsistency(roadTargetMap.targets().size(), supported, filledColumns, unsupported, foundationRepairable,
                foundationFilledBlocks, unresolved, maxDepth, MAX_ALLOWED_CUT_FILL_DEPTH, maxFoundationDepth, unresolved, columns, filledBlocks);
    }

    private static RoadbedColumn inspectRoadbedColumn(ServerLevel level, BlockPos cell, int targetGround, RoadClass kind) {
        RoadbedSupportProbe normal = probeRoadbed(level, cell, targetGround, MAX_ALLOWED_CUT_FILL_DEPTH);
        if (normal.supportFound()) return new RoadbedColumn(true, normal.requiredFillDepth(), false, 0, false, false, normal.requiredFillDepth());
        RoadbedSupportProbe foundation = probeRoadbed(level, cell, targetGround, MAX_ISOLATED_FOUNDATION_DEPTH);
        if (foundation.supportFound()) return new RoadbedColumn(true, foundation.requiredFillDepth(), true,
                foundation.requiredFillDepth(), false, true, normal.requiredFillDepth());
        return new RoadbedColumn(false, foundation.requiredFillDepth(), false, 0, true, true, normal.requiredFillDepth());
    }

    private static RoadbedSupportProbe probeRoadbed(ServerLevel level, BlockPos cell, int targetGround, int maxDepth) {
        int fluidDepth = 0;
        for (int depth = 0; depth <= maxDepth; depth++) {
            BlockPos support = new BlockPos(cell.getX(), targetGround - depth - 1, cell.getZ());
            BlockState state = level.getBlockState(support);
            if (!level.getFluidState(support).isEmpty()) fluidDepth++;
            if (isStableRoadbed(level, support, state)) {
                return new RoadbedSupportProbe(true, depth, false, fluidDepth, state);
            }
            if (depth == maxDepth || !isRoadbedFillable(level, support, state)) {
                return new RoadbedSupportProbe(false, depth + 1, true, fluidDepth, state);
            }
        }
        return new RoadbedSupportProbe(false, maxDepth + 1, true, fluidDepth, Blocks.AIR.defaultBlockState());
    }

    private static boolean isStableRoadbed(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && level.getFluidState(pos).isEmpty() && !state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isRoadbedFillable(ServerLevel level, BlockPos pos, BlockState state) {
        return state.isAir() || !level.getFluidState(pos).isEmpty() || state.canBeReplaced();
    }

    private static boolean isRoadVegetation(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS) || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.VINE)
                || state.is(Blocks.SNOW) || state.canBeReplaced();
    }

    private static int targetAt(RoadPlan road, int x, int z) {
        return targetAt(road.segment(), road.stations(), x, z);
    }

    private static int targetAt(Segment segment, List<Station> stations, int x, int z) {
        if (stations.isEmpty()) return 0;
        int route = segment.northSouth() ? Math.abs(z - segment.z1()) : Math.abs(x - segment.x1());
        if (route <= stations.get(0).distance()) return stations.get(0).targetY();
        for (int i = 1; i < stations.size(); i++) {
            Station next = stations.get(i);
            if (route <= next.distance()) {
                Station previous = stations.get(i - 1);
                int span = Math.max(1, next.distance() - previous.distance());
                double fraction = (double) (route - previous.distance()) / span;
                return (int) Math.round(previous.targetY() + (next.targetY() - previous.targetY()) * fraction);
            }
        }
        return stations.get(stations.size() - 1).targetY();
    }

    private static CandidateValidation validateCandidate(ServerLevel level, BlockPos anchor, Footprint footprint, long seed) {
        int total = 0, valid = 0, invalid = 0, woodland = 0, outside = 0, fallout = 0, scorched = 0, other = 0;
        Set<String> biomeCategories = new HashSet<>();
        for (int[] point : footprint.samplePoints()) {
            total++;
            SettlementSurfaceSampler.SurfaceSample sample = SettlementSurfaceSampler.sample(level, point[0], point[1]);
            if (!sample.valid()) {
                invalid++;
                continue;
            }
            valid++;
            var biome = level.getBiome(sample.ground());
            if (biome.is(AflBiomes.IRRADIATED_WOODLAND)) {
                woodland++;
                biomeCategories.add("WOODLAND");
            } else if (biome.is(AflBiomes.FALLOUT_BARRENS)) {
                fallout++;
                biomeCategories.add("FALLOUT");
            } else if (biome.is(AflBiomes.SCORCHED_LANDS)) {
                scorched++;
                biomeCategories.add("SCORCHED");
            } else {
                other++;
                biomeCategories.add("OTHER");
            }
            if (StartupPlainsEnclave.zoneAt(point[0], point[1], seed) == StartupPlainsEnclave.Zone.OUTSIDE) {
                outside++;
            }
        }
        boolean surfaceValid = total > 0 && valid > 0 && invalid <= MAX_INVALID_SURFACE_SAMPLES;
        String reason = surfaceValid ? "OK" : "CANDIDATE_SURFACE_DATA_INVALID";
        BunkerSavedData bunker = level.getDataStorage().computeIfAbsent(BunkerSavedData::load, BunkerSavedData::new, BunkerSavedData.ID);
        if (surfaceValid && bunker.isGenerated()) {
            double dx = bunker.getOrigin().getX() - anchor.getX();
            double dz = bunker.getOrigin().getZ() - anchor.getZ();
            if (dx * dx + dz * dz < 128.0D * 128.0D) {
                reason = "BUNKER_OVERLAP_RISK";
                surfaceValid = false;
            }
        }
        return new CandidateValidation(surfaceValid, reason, total, valid, invalid, woodland, outside, fallout, scorched,
                other, biomeCategories.size() > 1);
    }

    private static void logEcology(BlockPos anchor, CandidateValidation validation, SettlementFitResult fit, long seed) {
        StartupPlainsEnclave.Zone anchorZone = StartupPlainsEnclave.zoneAt(anchor.getX(), anchor.getZ(), seed);
        StartupSettlementProtection.ProtectionClass protection =
                StartupSettlementProtection.protectionAt(anchor.getX(), anchor.getZ(), seed);
        ApocalypseFirstLight.LOGGER.info("[AFL SETTLEMENT ECOLOGY] anchor={} anchorZone={} anchorProtected={} anchorEligible=true protectedHits={} woodlandSamples={} outsideBiomeSamples={} falloutSamples={} scorchedSamples={} otherBiomeSamples={} crossBiome={} fit={} candidateSamples={} candidateValidSamples={} candidateInvalidSamples={} candidateReason={}",
                anchor.toShortString(), anchorZone, protection != StartupSettlementProtection.ProtectionClass.NONE,
                fit.protectedHits(), validation.woodlandSamples(), validation.outsideBiomeSamples(),
                validation.falloutSamples(), validation.scorchedSamples(), validation.otherBiomeSamples(),
                validation.crossBiome(), fit.fit(), validation.totalSamples(), validation.validSamples(),
                validation.invalidSamples(), validation.reason());
    }

    private static TerrainStats terrainStats(ServerLevel level, AABB bounds) {
        List<Integer> values = new ArrayList<>();
        for (int x = (int) bounds.minX; x <= bounds.maxX; x += GLOBAL_SAMPLE_SPACING) for (int z = (int) bounds.minZ; z <= bounds.maxZ; z += GLOBAL_SAMPLE_SPACING) {
            SettlementSurfaceSampler.SurfaceSample sample = SettlementSurfaceSampler.sample(level, x, z);
            values.add(sample.valid() ? sample.y() : level.getMinBuildHeight());
        }
        return stats(values);
    }

    private static TerrainStats terrainStats(ServerLevel level, Footprint footprint) {
        List<Integer> values = new ArrayList<>();
        for (int[] point : footprint.samplePoints()) {
            SettlementSurfaceSampler.SurfaceSample sample = SettlementSurfaceSampler.sample(level, point[0], point[1]);
            values.add(sample.valid() ? sample.y() : level.getMinBuildHeight());
        }
        return stats(values);
    }

    private static TerrainStats lotStats(ServerLevel level, Lot lot) {
        List<Integer> values = new ArrayList<>();
        for (int x = lot.origin().getX(); x <= lot.origin().getX() + lot.width(); x += 8) for (int z = lot.origin().getZ(); z <= lot.origin().getZ() + lot.depth(); z += 8) {
            SettlementSurfaceSampler.SurfaceSample sample = SettlementSurfaceSampler.sample(level, x, z);
            values.add(sample.valid() ? sample.y() : level.getMinBuildHeight());
        }
        return stats(values);
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
    private static long mix(long seed, int x, int z) { long h = seed ^ ((long) x * 341873128712L) ^ ((long) z * 132897987541L); h ^= h >>> 33; return h; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static int min(List<Integer> values) { return values.stream().filter(v -> v > -64).min(Integer::compareTo).orElse(0); }
    private static int max(List<Integer> values) { return values.stream().filter(v -> v > -64).max(Integer::compareTo).orElse(0); }
    private static String format(double value) { return String.format("%.2f", value); }

    public static String rejectDiagnostic(Result result) {
        Plan plan = result.plan();
        if (plan == null) return "validatorVersion=" + VALIDATOR_VERSION + " stage=PRECHECK reason=" + result.reason()
                + (result.detail() == null ? "" : " " + result.detail());
        TerrainStats t = plan.terrain();
        String base = String.format("validatorVersion=%s stage=%s reason=%s anchor=%s sampleSpacing=%d samples=%d validSamples=%d invalidSamples=%d invalidRatio=%.3f minY=%d p10=%d p25=%d median=%d p75=%d p90=%d maxY=%d effectiveRelief=%d maxEffectiveRelief=%d outliers=%d outlierRatio=%.3f maxOutlierRatio=%.3f heightmapType=MOTION_BLOCKING_NO_LEAVES percentilesBasedOn=VALID_ONLY mainCandidateCount=%d",
                VALIDATOR_VERSION, plan.stage(), result.reason(), plan.anchor().toShortString(), GLOBAL_SAMPLE_SPACING, t.sampleCount(), t.validSamples(), t.invalidSamples(), t.sampleCount() == 0 ? 0.0D : (double) t.invalidSamples() / t.sampleCount(), t.minY(), t.p10(), t.p25(), t.median(), t.p75(), t.p90(), t.maxY(), t.effectiveRelief(), MAX_EFFECTIVE_RELIEF, t.outlierCount(), t.outlierRatio(), MAX_OUTLIER_RATIO, plan.mainCandidateCount());
        if (plan.roadStats() != null) {
            RoadStats r = plan.roadStats();
            base += String.format(" mainRoadSampleCount=%d validRoadSamples=%d invalidRoadSamples=%d cutBlocks=%d fillBlocks=%d waterFillBlocks=%d maxCutDepth=%d maxFillDepth=%d hardObstacleCount=%d earthworkCost=%s gradeRoughness=%d", r.sampleCount(), r.validSamples(), r.invalidSamples(), r.cutBlocks(), r.fillBlocks(), r.waterFillBlocks(), r.maxCutDepth(), r.maxFillDepth(), r.hardObstacleCount(), format(r.earthworkCost()), r.maxTargetAdjacentDelta());
        }
        LotSummary lots = plan.lotSummary();
        return base + (result.detail() == null ? "" : " " + result.detail()) + String.format(" plannedResidential=%d usableResidential=%d rejectedResidential=%d plannedCommercial=%d usableCommercial=%d rejectedCommercial=%d", lots.plannedResidential(), lots.usableResidential(), lots.rejectedResidential(), lots.plannedCommercial(), lots.usableCommercial(), lots.rejectedCommercial());
    }

    public static TerrainStats terrainCheckHere(ServerLevel level, BlockPos pos) { return terrainStats(level, compactFootprint(level.getSeed(), pos)); }
    public static boolean passesGlobalTerrain(TerrainStats stats) { return stats.effectiveRelief() <= MAX_EFFECTIVE_RELIEF && stats.outlierRatio() <= MAX_OUTLIER_RATIO; }

    public record TerrainStats(int sampleCount, int validSamples, int invalidSamples, int minY, int p10, int p25, int median, int p75, int p90, int maxY, int effectiveRelief, int outlierCount, double outlierRatio) {}
    public record RoadStats(boolean valid, int sampleCount, int validSamples, int invalidSamples, int minY, int maxY, int maxNaturalAdjacentDelta, int maxTargetAdjacentDelta, int cutBlocks, int fillBlocks, int maxCutDepth, int maxFillDepth, int waterFillBlocks, int vegetationClearEstimate, int hardObstacleCount, int maxDeepRun, int maxWaterRun, double waterStationRatio, double earthworkCost, int roadWidth, int roadColumnSamples, int supportedColumns, int fillRequiredColumns, int unsupportedColumns, int unresolvedColumns, int foundationRepairableColumns, int rawTopMaxDelta, int vegetationColumnsIgnored, int maxVegetationHeightIgnored, int maxUnsupportedRun, int maxUnsupportedAcrossSection, int maxFoundationDepth, int fullWidthMaxFillDepth, int fullWidthMaxCutDepth, String rejectionReason, String unsupportedPolicyReason, boolean foundationRepairRequired) {
        double unsupportedRatio() { return roadColumnSamples == 0 ? 0.0D : (double) unsupportedColumns / roadColumnSamples; }
    }
    private record RoadTargetMap(Map<BlockPos, Integer> targets, Map<BlockPos, RoadClass> kinds, List<IntersectionDiagnostic> intersections,
                                 int intersectionAdjustedStations) {}
    private record IntersectionDiagnostic(int x, int z, int mainTargetY, int localOriginalTargetY,
                                          int localAdjustedTargetY, int transitionLength, int overlapWidth,
                                          int gradeDeltaBefore, int gradeDeltaAfter) {}
    private record RoadbedColumn(boolean supported, int fillDepth, boolean foundationRepairable, int foundationDepth,
                                 boolean unresolved, boolean normalUnsupported, int normalRequiredFillDepth) {}
    private record RoadbedSupportProbe(boolean supportFound, int requiredFillDepth, boolean unsupported,
                                       int fluidDepth, BlockState supportBlock) {}
    private record RoadbedConsistency(int roadColumns, int supportedColumns, int filledColumns, int unsupportedColumns,
                                       int foundationRepairableColumns, int foundationFilledBlocks, int unresolvedColumns,
                                       int maxActualFillDepth, int plannerMaxFillDepth, int maxFoundationDepth, int mismatchCount,
                                       Map<BlockPos, RoadbedColumn> columns, int filledBlocks) {}
    private record TreeCleanup(Set<BlockPos> logs, Set<BlockPos> leaves) {}
    private record RowClearanceResult(int roadColumns, int rowColumns, int rowMarginColumns, int scanHeight,
                                      int logsRemoved, int leavesRemoved, int otherVegetationRemoved,
                                      int treeSeedsFound, int treesFullyRemoved, int remainingRoadLogs,
                                      int remainingRoadLeaves, int remainingRoadVegetation) {}
    private record EdgeSupport(boolean found, int supportY) {}
    private record EdgeClosureResult(int edgeColumnsChecked, int cavityColumnsFound, int cavityColumnsClosed,
                                     int deepCavityColumnsSkipped, int edgeFillBlocks, int maxEdgeClosureDepth) {}
    private record CandidateValidation(boolean valid, String reason, int totalSamples, int validSamples, int invalidSamples,
                                       int woodlandSamples, int outsideBiomeSamples, int falloutSamples,
                                       int scorchedSamples, int otherBiomeSamples, boolean crossBiome) {
        String detail() {
            return String.format("candidateSamples=%d candidateValidSamples=%d candidateInvalidSamples=%d woodlandSamples=%d outsideBiomeSamples=%d falloutSamples=%d scorchedSamples=%d otherBiomeSamples=%d crossBiome=%s candidateReason=%s",
                    totalSamples, validSamples, invalidSamples, woodlandSamples, outsideBiomeSamples,
                    falloutSamples, scorchedSamples, otherBiomeSamples, crossBiome, reason);
        }
    }
    public record Result(boolean success, String reason, Plan plan, int logsCleared, int leavesCleared, int otherVegetationCleared, String detail) {
        static Result failure(String reason) { return failure(reason, null, null); }
        static Result failure(String reason, Plan plan) { return failure(reason, plan, null); }
        static Result failure(String reason, Plan plan, String detail) { return new Result(false, reason, plan, 0, 0, 0, detail); }
    }
    public record Plan(boolean valid, String reason, String stage, BlockPos anchor, boolean northSouth, List<Segment> roads, List<RoadPlan> roadPlans, List<Lot> lots, AABB bounds, TerrainStats terrain, RoadStats roadStats, LotSummary lotSummary, int mainCandidateCount, String detail) {
        private List<Segment> clearance() { return roads.stream().map(r -> r.withMargin(r.kind() == RoadClass.MAIN ? MAIN_CLEAR_MARGIN : r.kind() == RoadClass.LOCAL ? LOCAL_CLEAR_MARGIN : LOT_CLEAR_MARGIN)).toList(); }
        private Plan withStage(String replacementStage) { return new Plan(valid, reason, replacementStage, anchor, northSouth, roads, roadPlans, lots, bounds, terrain, roadStats, lotSummary, mainCandidateCount, detail); }
        private Plan withFailure(String replacementReason, String replacementStage, String replacementDetail) { return new Plan(false, replacementReason, replacementStage, anchor, northSouth, roads, roadPlans, lots, bounds, terrain, roadStats, lotSummary, mainCandidateCount, replacementDetail); }
    }
    public record RoadPlan(Segment segment, RoadStats stats, List<Station> stations, int originalIntersectionTargetY) {
        int targetYAt(int x, int z) { for (Station station : stations) if (station.x() == x && station.z() == z) return station.targetY(); return stations.isEmpty() ? 0 : stations.get(0).targetY(); }
    }
    private record RoadChoice(RoadPlan plan, int candidateCount) {}
    public record LotSummary(int plannedResidential, int usableResidential, int plannedCommercial, int usableCommercial) {
        static LotSummary empty() { return new LotSummary(0, 0, 0, 0); }
        int rejectedResidential() { return plannedResidential - usableResidential; }
        int rejectedCommercial() { return plannedCommercial - usableCommercial; }
    }
    private record Footprint(List<FootprintGeometry> geometry, AABB bounds, int minX, int maxX, int minZ, int maxZ,
                             int roadArea, int lotArea, int occupiedArea) {
        int width() { return maxX - minX + 1; }
        int depth() { return maxZ - minZ + 1; }
        long boundingArea() { return (long) width() * depth(); }
        List<int[]> samplePoints() {
            Set<Long> seen = new HashSet<>();
            List<int[]> points = new ArrayList<>();
            for (FootprintGeometry item : geometry) for (int[] point : item.samplePoints()) {
                if (seen.add(cellKey(point[0], point[1]))) points.add(point);
            }
            return points;
        }
    }
    private record FootprintGeometry(boolean road, Segment segment, Lot lot, int margin) {
        static FootprintGeometry road(Segment segment) { return new FootprintGeometry(true, segment, null, 0); }
        static FootprintGeometry lot(Lot lot, int margin) { return new FootprintGeometry(false, null, lot, margin); }
        int minX() { return road ? segment.x1() == segment.x2() ? segment.x1() - segment.width() / 2 : Math.min(segment.x1(), segment.x2()) : lot.origin().getX() - margin; }
        int maxX() { return road ? segment.x1() == segment.x2() ? segment.x1() + segment.width() / 2 : Math.max(segment.x1(), segment.x2()) : lot.origin().getX() + lot.width() + margin; }
        int minZ() { return road ? segment.z1() == segment.z2() ? segment.z1() - segment.width() / 2 : Math.min(segment.z1(), segment.z2()) : lot.origin().getZ() - margin; }
        int maxZ() { return road ? segment.z1() == segment.z2() ? segment.z1() + segment.width() / 2 : Math.max(segment.z1(), segment.z2()) : lot.origin().getZ() + lot.depth() + margin; }
        List<BlockPos> cells() {
            if (road) return segment.cells();
            List<BlockPos> out = new ArrayList<>();
            for (int x = minX(); x <= maxX(); x++) for (int z = minZ(); z <= maxZ(); z++) out.add(new BlockPos(x, 0, z));
            return out;
        }
        List<int[]> samplePoints() {
            List<int[]> out = new ArrayList<>();
            if (road) {
                int length = Math.max(Math.abs(segment.x2() - segment.x1()), Math.abs(segment.z2() - segment.z1()));
                for (int distance = 0; distance <= length; distance += 16) addRoadSamples(out, distance, length);
                if (length % 16 != 0) addRoadSamples(out, length, length);
                return out;
            }
            for (int x = minX(); x <= maxX(); x += 16) for (int z = minZ(); z <= maxZ(); z += 16) out.add(new int[]{x, z});
            out.add(new int[]{maxX(), minZ()}); out.add(new int[]{minX(), maxZ()}); out.add(new int[]{maxX(), maxZ()});
            return out;
        }
        private void addRoadSamples(List<int[]> out, int distance, int length) {
            int x = segment.x1() + (segment.x2() - segment.x1()) * distance / Math.max(1, length);
            int z = segment.z1() + (segment.z2() - segment.z1()) * distance / Math.max(1, length);
            int half = segment.width() / 2;
            if (segment.x1() == segment.x2()) {
                out.add(new int[]{x - half, z}); out.add(new int[]{x, z}); out.add(new int[]{x + half, z});
            } else {
                out.add(new int[]{x, z - half}); out.add(new int[]{x, z}); out.add(new int[]{x, z + half});
            }
        }
    }
    private record SettlementFitResult(boolean fit, int minX, int maxX, int minZ, int maxZ, int geometryCount,
                                       int sampleCount, int protectedHits, FitHit firstProtectedHit, int roadArea,
                                       int lotArea, int occupiedArea) {
        int width() { return maxX - minX + 1; }
        int depth() { return maxZ - minZ + 1; }
        long boundingArea() { return (long) width() * depth(); }
    }
    private record FitHit(int x, int z, StartupPlainsEnclave.Zone zone, StartupSettlementProtection.ProtectionClass protection,
                          double distance, int plainsBoundary, int settlementProtectionBoundary, int woodlandBoundary) {}
    public record Segment(int x1, int z1, int x2, int z2, int width, RoadClass kind, boolean northSouth, int offset) {
        Segment(int x1, int z1, int x2, int z2, int width, RoadClass kind) { this(x1, z1, x2, z2, width, kind, x1 == x2, 0); }
        Segment withMargin(int margin) { return new Segment(x1, z1, x2, z2, width + margin * 2, kind, northSouth, offset); }
        List<BlockPos> cells() {
            List<BlockPos> out = new ArrayList<>();
            int half = width / 2;
            if (x1 == x2) {
                for (int x = x1 - half; x <= x1 + half; x++) for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) out.add(new BlockPos(x, 0, z));
            } else if (z1 == z2) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) for (int z = z1 - half; z <= z1 + half; z++) out.add(new BlockPos(x, 0, z));
            } else {
                int minX = Math.min(x1, x2) - half, maxX = Math.max(x1, x2) + half, minZ = Math.min(z1, z2) - half, maxZ = Math.max(z1, z2) + half;
                for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) out.add(new BlockPos(x, 0, z));
            }
            return out;
        }
    }
    public record Lot(BlockPos origin, int width, int depth, Direction facing, LotType type) { Segment segment() { return new Segment(origin.getX(), origin.getZ(), origin.getX() + width, origin.getZ() + depth, 1, RoadClass.LOT); } }
    private record ConstructionSurface(int x, int z, int rawTopY, int constructionGroundY, int vegetationHeightIgnored, boolean valid) {
        BlockPos ground() { return new BlockPos(x, constructionGroundY - 1, z); }
    }
    private record Station(int distance, int x, int z, int naturalY, int targetY, int rawTopY, int vegetationHeightIgnored,
                           boolean valid, int waterDepth, String reason, ServerLevel level) {
        Station withTargetY(int target) {
            return new Station(distance, x, z, naturalY, target, rawTopY, vegetationHeightIgnored, valid, waterDepth, reason, level);
        }
    }
    public enum LotType { RESIDENTIAL, COMMERCIAL }
    public enum RoadClass { MAIN, LOCAL, REGIONAL_STUB, LOT }
}
