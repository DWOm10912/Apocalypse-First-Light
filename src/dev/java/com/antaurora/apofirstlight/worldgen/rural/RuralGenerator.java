package com.antaurora.apofirstlight.worldgen.rural;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Deterministic, command-only Rural V1.2 planner and commit phase. */
public final class RuralGenerator {
    public static final int RESERVATION_SIZE = 128;
    public static final int ROAD_LENGTH = 80;
    public static final int BRANCH_LENGTH = 40;
    public static final int ROAD_WIDTH = 5;
    public static final int MIN_BUILDING_COUNT = 5;
    public static final int MAX_SITE_WATER_RATIO = 22;
    public static final int MAX_SITE_ROBUST_RELIEF = 18;
    public static final double MAX_SITE_STEEP_RATIO = 0.20D;
    public static final int MAX_LOT_RELIEF = 6;
    /** Per-lot vertical adaptation budget. The site may slope, but a building may not need more than this. */
    public static final int MAX_LOT_CORRECTION = 3;
    public static final int MAX_LOT_FILL_DEPTH = 3;
    public static final int CLEARANCE_MARGIN = 1;
    public static final int CLEARANCE_TOP_MARGIN = 1;
    public static final int LOT_MARGIN = 2;

    private static final int SITE_SAMPLE_STEP = 8;
    private static final long RANDOM_SALT = 0x524D4C5F56311L;
    private static final long TARGET_SALT = 0x544152474554L;
    private static final org.slf4j.Logger LOGGER = ApocalypseFirstLight.LOGGER;

    private RuralGenerator() {
    }

    public static RuralPlan plan(ServerLevel level, BlockPos center) {
        BoundingBox reservation = reservation(center);
        RuralPlan.SiteScore site = inspectSite(level, reservation);
        Direction mainDirection = siteDirection(level.getSeed(), center);
        RuralPlan.Road mainRoad = road(center, mainDirection, false);
        List<RuralPlan.Road> branchRoads = branchRoads(center, mainDirection, level.getSeed());
        int target = targetBuildingCount(level.getSeed(), center);
        List<RuralLayoutPlanner.Candidate> candidates = List.of();
        RejectionTracker rejectionTracker = new RejectionTracker();
        LOGGER.debug("[Rural] candidate origin={} reservation={} siteScore={} waterRatio={} robustRelief={} steepRatio={} targetBuildings={}",
                center, reservation, site.score(), site.waterRatio(), site.robustRelief(), site.steepRatio(), target);
        if (site.waterRatio() * 100.0D > MAX_SITE_WATER_RATIO) {
            return invalid(center, reservation, site, mainRoad, branchRoads, target, candidates,
                    List.of(), rejectionTracker, "site water ratio exceeds threshold");
        }
        if (site.validGroundSamples() < site.sampledColumns()) {
            return invalid(center, reservation, site, mainRoad, branchRoads, target, candidates,
                    List.of(), rejectionTracker, "site natural ground sample unavailable");
        }
        if (site.robustRelief() > MAX_SITE_ROBUST_RELIEF) {
            return invalid(center, reservation, site, mainRoad, branchRoads, target, candidates,
                    List.of(), rejectionTracker, "site robust relief exceeds threshold");
        }
        if (site.steepRatio() > MAX_SITE_STEEP_RATIO) {
            return invalid(center, reservation, site, mainRoad, branchRoads, target, candidates,
                    List.of(), rejectionTracker, "site steep-column ratio exceeds threshold");
        }

        Map<RuralStructurePool.Definition, StructureTemplate> templates = new LinkedHashMap<>();
        for (RuralStructurePool.Definition definition : RuralStructurePool.definitions()) {
            Optional<StructureTemplate> template = level.getServer().getStructureManager().get(definition.id());
            if (template.isEmpty()) {
                LOGGER.debug("[Rural] required template missing id={}", definition.id());
                return invalid(center, reservation, site, mainRoad, branchRoads, target, candidates,
                        List.of(), rejectionTracker, "missing structure template " + definition.id());
            }
            templates.put(definition, template.get());
        }
        candidates = RuralLayoutPlanner.candidates(center, mainDirection, branchRoads.get(0).direction(),
                templates.get(RuralStructurePool.BARN));
        LOGGER.debug("[Rural] candidate origin={} reservation={} siteScore={} waterRatio={} robustRelief={} steepRatio={} targetBuildings={} candidateLots={} barnTemplateSize={}",
                center, reservation, site.score(), site.waterRatio(), site.robustRelief(), site.steepRatio(), target,
                candidates.size(), templates.get(RuralStructurePool.BARN).getSize());

        List<RuralPlan.Road> allRoads = new ArrayList<>(1 + branchRoads.size());
        allRoads.add(mainRoad);
        allRoads.addAll(branchRoads);
        List<RuralPlan.Lot> accepted = new ArrayList<>();
        Map<RuralStructurePool.Definition, Integer> counts = new HashMap<>();
        Set<Integer> usedSlots = new HashSet<>();
        RandomSource random = RandomSource.create(level.getSeed() ^ center.asLong() ^ RANDOM_SALT);

        int farmhouseSlot = forceDefinition(RuralStructurePool.FARMHOUSE, RuralStructurePool.Role.FARMHOUSE,
                candidates, templates, level, reservation, allRoads, accepted, counts, usedSlots, rejectionTracker);
        if (farmhouseSlot >= 0) {
            LOGGER.debug("[Rural] forced farmhouse slot={} position={} rotation={}", farmhouseSlot,
                    accepted.get(accepted.size() - 1).origin(), accepted.get(accepted.size() - 1).rotation());
        }
        int barnSlot = forceDefinition(RuralStructurePool.BARN, RuralStructurePool.Role.AGRICULTURAL_LARGE,
                candidates, templates, level, reservation, allRoads, accepted, counts, usedSlots, rejectionTracker);
        if (barnSlot >= 0) {
            LOGGER.debug("[Rural] forced barn slot={} position={} rotation={}", barnSlot,
                    accepted.get(accepted.size() - 1).origin(), accepted.get(accepted.size() - 1).rotation());
        }

        for (int index = 0; index < candidates.size() && accepted.size() < target; index++) {
            if (usedSlots.contains(index)) continue;
            RuralLayoutPlanner.Candidate candidate = candidates.get(index);
            List<RuralStructurePool.Definition> available = availableFor(candidate.role(), counts);
            boolean acceptedCandidate = false;
            while (!available.isEmpty()) {
                RuralStructurePool.Definition selected = weightedPick(available, random);
                LotFit fit = fit(level, templates.get(selected), selected, candidate, reservation, allRoads, accepted);
                if (fit.accepted()) {
                    accepted.add(fit.lot());
                    counts.merge(selected, 1, Integer::sum);
                    usedSlots.add(index);
                    acceptedCandidate = true;
                    LOGGER.debug("[Rural] selected role={} structure={} slot={} position={} rotation={}",
                            candidate.role(), selected.id(), index, fit.lot().origin(), fit.lot().rotation());
                    break;
                }
                available.remove(selected);
                rejectionTracker.record(fit.reason());
                LOGGER.debug("[Rural] rejected lot slot={} structure={} reason={}", index, selected.id(), fit.reason());
            }
            if (!acceptedCandidate) {
                LOGGER.debug("[Rural] candidate slot={} role={} rejected", index, candidate.role());
            }
        }

        if (counts.getOrDefault(RuralStructurePool.FARMHOUSE, 0) != 1) {
            return invalid(center, reservation, site, mainRoad, branchRoads, target, candidates, accepted,
                    rejectionTracker, "farmhouse could not be placed exactly once");
        }
        if (counts.getOrDefault(RuralStructurePool.BARN, 0) != 1) {
            return invalid(center, reservation, site, mainRoad, branchRoads, target, candidates, accepted,
                    rejectionTracker, "barn could not be placed exactly once");
        }

        boolean fallbackUsed = accepted.size() < target;
        if (accepted.size() < MIN_BUILDING_COUNT) {
            return invalid(center, reservation, site, mainRoad, branchRoads, target, candidates, accepted,
                    rejectionTracker, "usable lots below minimum: " + accepted.size());
        }
        if (fallbackUsed && site.score() >= 0.90D) {
            return invalid(center, reservation, site, mainRoad, branchRoads, target, candidates, accepted,
                    rejectionTracker, "ideal site did not reach target building count: " + accepted.size() + "/" + target);
        }
        RuralFarmPlanner.Result farm;
        try {
            farm = RuralFarmPlanner.plan(level, center, reservation, allRoads, accepted);
        } catch (Throwable throwable) {
            LOGGER.warn("[Rural] optional farm planning skipped center={} reason={}", center, throwable.toString());
            farm = new RuralFarmPlanner.Result(0, List.of(),
                    List.of("planning_exception=" + throwable.getClass().getSimpleName()), 0);
        }
        LOGGER.debug("[Rural] farm plan target={} accepted={} attempts={} rejected={}",
                farm.target(), farm.count(), farm.attempts(), farm.rejections().size());
        int rejectedLots = Math.max(0, candidates.size() - accepted.size());
        LOGGER.debug("[Rural] final plan validation result=OK lots={} target={} fallbackUsed={} mainRoad={} branches={}",
                accepted.size(), target, fallbackUsed, mainRoad.bounds(), branchRoads.size());
        return RuralPlan.valid(center, reservation, site, mainRoad, branchRoads, accepted, target,
                candidates.size(), rejectedLots, fallbackUsed, rejectionTracker.counts(), rejectionTracker.barnDetails(),
                farm.target(), farm.plots(), farm.rejections());
    }

    public static GenerationResult generate(ServerLevel level, BlockPos center) {
        RuralPlan plan = plan(level, center);
        if (!plan.valid()) return GenerationResult.failed(plan.failureReason(), plan);

        CommitTracker tracker = new CommitTracker(center, plan);
        try {
            tracker.phase = "PRE_COMMIT";
            String validationFailure = preCommitValidate(level, plan, tracker);
            if (validationFailure != null) {
                LOGGER.debug("[Rural] pre-commit validation failed center={} reason={}", center, validationFailure);
                return GenerationResult.failed("pre-commit validation failed: " + validationFailure, plan);
            }

            RoadPlacementResult roadPlacement = placeRoad(level, plan, tracker);
            placeDriveways(level, plan, null, null);
            int terrainBlocks = 0;
            int logsCleared = 0;
            int leavesCleared = 0;
            int vegetationCleared = 0;
            for (int index = 0; index < plan.lots().size(); index++) {
                RuralPlan.Lot lot = plan.lots().get(index);
                tracker.beginLot("TERRAIN_PREP", index, lot);
                RuralTerrainAdapter.PreparationResult result = RuralTerrainAdapter.prepare(level, lot);
                terrainBlocks += result.changed();
                logsCleared += result.logsCleared();
                leavesCleared += result.leavesCleared();
                vegetationCleared += result.vegetationCleared();
                tracker.completedSteps.add("TERRAIN_PREP_" + index);
            }
            int placed = 0;
            for (int index = 0; index < plan.lots().size(); index++) {
                RuralPlan.Lot lot = plan.lots().get(index);
                tracker.beginLot("STRUCTURE_" + index, index, lot);
                StructureTemplate template = level.getServer().getStructureManager().get(lot.structure().id()).orElse(null);
                if (template == null || !place(level, template, lot)) {
                    LOGGER.error("[Rural] commit failure phase={} structure={} position={}", tracker.phase,
                            lot.structure().id(), lot.origin());
                    return GenerationResult.failed("commit failed for " + lot.structure().id(), plan);
                }
                tracker.completedSteps.add("STRUCTURE_" + index);
                placed++;
            }
            FarmPlacementResult farmPlacement = placeFarmPlots(level, plan, tracker);
            tracker.phase = "POST_COMMIT";
            tracker.completedSteps.add("POST_COMMIT");
            LOGGER.debug("[Rural] commit success lots={} mainRoadBlocks={} branchRoadBlocks={} terrainBlocks={} logsCleared={} leavesCleared={} vegetationCleared={} farmPlots={} farmlandBlocks={} cropBlocks={} fenceBlocks={} gateBlocks={} irrigationBlocks={} pathBlocks={} farmlandLogsCleared={} farmlandLeavesCleared={} farmlandVegetationCleared={}",
                    placed, roadPlacement.mainRoadBlocks(), roadPlacement.branchRoadBlocks(), terrainBlocks,
                    logsCleared, leavesCleared, vegetationCleared, farmPlacement.farmPlots(), farmPlacement.farmlandBlocks(),
                    farmPlacement.cropBlocks(), farmPlacement.fenceBlocks(), farmPlacement.gateBlocks(),
                    farmPlacement.irrigationBlocks(), farmPlacement.pathBlocks(), farmPlacement.logsCleared(),
                    farmPlacement.leavesCleared(), farmPlacement.vegetationCleared());
            return GenerationResult.success(plan, placed, roadPlacement.mainRoadBlocks(), roadPlacement.branchRoadBlocks(),
                    terrainBlocks + farmPlacement.terrainBlocks(), logsCleared + farmPlacement.logsCleared(),
                    leavesCleared + farmPlacement.leavesCleared(), vegetationCleared + farmPlacement.vegetationCleared(),
                    farmPlacement);
        } catch (Throwable throwable) {
            throw new GenerationCrashedException(tracker.context(), throwable);
        }
    }

    /**
     * Replays a validated natural plan inside one structure-generation chunk only.
     * Unlike the developer command path this method never loads neighboring chunks.
     */
    public static NaturalPlacementSummary generateNaturalChunk(WorldGenLevel level, RuralPlan plan,
                                                               BoundingBox chunkBox) {
        if (!plan.valid()) return NaturalPlacementSummary.empty();
        NaturalPlacementStats stats = new NaturalPlacementStats();
        placeNaturalRoad(level, plan, chunkBox, stats);
        placeDriveways(level, plan, chunkBox, stats);
        for (RuralPlan.Lot lot : plan.lots()) {
            RuralTerrainAdapter.PreparationResult preparation = RuralTerrainAdapter.prepare(level, lot, chunkBox);
            stats.addPreparation(preparation);
            StructureTemplate template = level.getLevel().getServer().getStructureManager()
                    .get(lot.structure().id()).orElse(null);
            if (template == null) continue;
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setMirror(Mirror.NONE)
                    .setRotation(lot.rotation())
                    .setBoundingBox(chunkBox)
                    .setIgnoreEntities(true);
            stats.blocksAttempted++;
            if (template.placeInWorld(level, lot.origin(), lot.origin(), settings,
                    RandomSource.create(plan.deterministicSeed() ^ lot.origin().asLong()), 2)) {
                stats.blocksWritten++;
                stats.structureBlocks++;
            }
        }
        for (RuralFarmPlot plot : plan.farmPlots()) {
            RuralTerrainAdapter.PreparationResult preparation = RuralTerrainAdapter.prepare(level, plot, chunkBox);
            stats.addPreparation(preparation);
            placeNaturalFarmPlot(level, plot, plan.deterministicSeed(), chunkBox, stats);
        }
        return stats.summary();
    }

    private static void placeNaturalRoad(WorldGenLevel level, RuralPlan plan, BoundingBox chunkBox,
                                         NaturalPlacementStats stats) {
        for (RuralPlan.Road road : plan.roads()) {
            BoundingBox bounds = road.bounds();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (x < chunkBox.minX() || x > chunkBox.maxX()
                            || z < chunkBox.minZ() || z > chunkBox.maxZ()) continue;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    BlockPos pos = new BlockPos(x, y, z);
                    stats.blocksAttempted++;
                    if (level.setBlock(pos, Blocks.GRAVEL.defaultBlockState(), 3)) {
                        stats.blocksWritten++;
                        stats.roadBlocks++;
                    }
                }
            }
        }
    }

    private static void placeDriveways(WorldGenLevel level, RuralPlan plan, BoundingBox chunkBox,
                                       NaturalPlacementStats stats) {
        for (RuralPlan.Lot lot : plan.lots()) {
            BlockPos start = frontMidpoint(lot.bounds(), lot.roadFacing()).relative(lot.roadFacing());
            BlockPos target = nearestRoadCell(start, plan.roads());
            Direction travel = dominantDirection(start, target);
            Direction width = travel.getClockWise();
            int length = Math.max(Math.abs(target.getX() - start.getX()), Math.abs(target.getZ() - start.getZ()));
            for (int step = 0; step <= length; step++) {
                double progress = length == 0 ? 0.0D : step / (double) length;
                BlockPos center = new BlockPos(Mth.floor(Mth.lerp(progress, start.getX(), target.getX())), 0,
                        Mth.floor(Mth.lerp(progress, start.getZ(), target.getZ())));
                for (int lateral = -1; lateral <= 1; lateral++) {
                    int x = center.getX() + width.getStepX() * lateral;
                    int z = center.getZ() + width.getStepZ() * lateral;
                    if (chunkBox != null && (x < chunkBox.minX() || x > chunkBox.maxX()
                            || z < chunkBox.minZ() || z > chunkBox.maxZ())) continue;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    if (stats != null) stats.blocksAttempted++;
                    if (level.setBlock(new BlockPos(x, y, z), Blocks.GRAVEL.defaultBlockState(), 3)
                            && stats != null) {
                        stats.blocksWritten++;
                        stats.roadBlocks++;
                    }
                }
            }
        }
    }

    private static BlockPos frontMidpoint(BoundingBox bounds, Direction facing) {
        int x = (bounds.minX() + bounds.maxX()) / 2;
        int z = (bounds.minZ() + bounds.maxZ()) / 2;
        return switch (facing) {
            case NORTH -> new BlockPos(x, 0, bounds.minZ());
            case SOUTH -> new BlockPos(x, 0, bounds.maxZ());
            case WEST -> new BlockPos(bounds.minX(), 0, z);
            case EAST -> new BlockPos(bounds.maxX(), 0, z);
            default -> throw new IllegalArgumentException("Horizontal road facing required: " + facing);
        };
    }

    private static BlockPos nearestRoadCell(BlockPos start, List<RuralPlan.Road> roads) {
        BlockPos best = start;
        int bestDistance = Integer.MAX_VALUE;
        for (RuralPlan.Road road : roads) {
            int x = Mth.clamp(start.getX(), road.bounds().minX(), road.bounds().maxX());
            int z = Mth.clamp(start.getZ(), road.bounds().minZ(), road.bounds().maxZ());
            int distance = Math.abs(x - start.getX()) + Math.abs(z - start.getZ());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new BlockPos(x, 0, z);
            }
        }
        return best;
    }

    private static Direction dominantDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void placeNaturalFarmPlot(WorldGenLevel level, RuralFarmPlot plot, long seed,
                                             BoundingBox chunkBox, NaturalPlacementStats stats) {
        for (BlockPos pos : plot.irrigationCells()) {
            BlockPos placed = new BlockPos(pos.getX(), plot.baseY(), pos.getZ());
            if (!chunkBox.isInside(placed)) continue;
            stats.blocksAttempted++;
            if (level.setBlock(placed, Blocks.WATER.defaultBlockState(), 3)) {
                stats.blocksWritten++;
                stats.irrigationBlocks++;
                stats.farmBlocks++;
            }
        }
        for (BlockPos pos : plot.pathCells()) {
            BlockPos placed = new BlockPos(pos.getX(), plot.baseY(), pos.getZ());
            if (!chunkBox.isInside(placed)) continue;
            stats.blocksAttempted++;
            if (level.setBlock(placed, Blocks.DIRT_PATH.defaultBlockState(), 3)) {
                stats.blocksWritten++;
                stats.farmBlocks++;
            }
        }
        Set<Long> irrigationKeys = plot.irrigationCells().stream()
                .map(pos -> BlockPos.asLong(pos.getX(), 0, pos.getZ())).collect(java.util.stream.Collectors.toSet());
        Set<Long> pathKeys = plot.pathCells().stream()
                .map(pos -> BlockPos.asLong(pos.getX(), 0, pos.getZ())).collect(java.util.stream.Collectors.toSet());
        for (RuralFarmPlot.Cell cell : plot.cells()) {
            if (irrigationKeys.contains(cell.key()) || pathKeys.contains(cell.key())) continue;
            BlockPos farmland = new BlockPos(cell.x(), plot.baseY(), cell.z());
            if (!chunkBox.isInside(farmland)) continue;
            stats.blocksAttempted++;
            if (level.setBlock(farmland, Blocks.FARMLAND.defaultBlockState(), 3)) {
                stats.blocksWritten++;
                stats.farmBlocks++;
            }
            stats.blocksAttempted++;
            if (level.setBlock(farmland.above(), plot.crop().state(growthBand(seed, plot.index(), cell.key())), 3)) {
                stats.blocksWritten++;
                stats.cropBlocks++;
                stats.farmBlocks++;
            }
        }
        for (RuralFarmPlot.Fence fence : plot.fences()) {
            if (!chunkBox.isInside(fence.pos())) continue;
            stats.blocksAttempted++;
            if (level.setBlock(fence.pos(), Blocks.OAK_FENCE.defaultBlockState(), 3)) {
                stats.blocksWritten++;
                stats.farmBlocks++;
            }
        }
        for (RuralFarmPlot.Gate gate : plot.gates()) {
            if (chunkBox.isInside(gate.pos())) {
                stats.blocksAttempted++;
                if (level.setBlock(gate.pos(), Blocks.OAK_FENCE_GATE.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, gate.facing()), 3)) {
                    stats.blocksWritten++;
                    stats.farmBlocks++;
                }
            }
        }
    }

    public record NaturalPlacementSummary(int blocksAttempted, int blocksWritten, int roadBlocks,
                                          int terrainPrepBlocks, int vegetationCleared, int structureBlocks,
                                          int farmBlocks, int cropBlocks, int irrigationBlocks,
                                          int totalCutBlocks, int totalFillBlocks, int maxCutDepth,
                                          int maxFillDepth, int terrainBlendBlocks, int exposedFillSurfaceBlocks) {
        private static NaturalPlacementSummary empty() {
            return new NaturalPlacementSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private static final class NaturalPlacementStats {
        private int blocksAttempted;
        private int blocksWritten;
        private int roadBlocks;
        private int terrainPrepBlocks;
        private int vegetationCleared;
        private int structureBlocks;
        private int farmBlocks;
        private int cropBlocks;
        private int irrigationBlocks;
        private int totalCutBlocks;
        private int totalFillBlocks;
        private int maxCutDepth;
        private int maxFillDepth;
        private int terrainBlendBlocks;
        private int exposedFillSurfaceBlocks;

        private void addPreparation(RuralTerrainAdapter.PreparationResult preparation) {
            terrainPrepBlocks += preparation.changed();
            vegetationCleared += preparation.vegetationCleared();
            totalCutBlocks += preparation.cutBlocks();
            totalFillBlocks += preparation.fillBlocks();
            maxCutDepth = Math.max(maxCutDepth, preparation.maxCutDepth());
            maxFillDepth = Math.max(maxFillDepth, preparation.maxFillDepth());
            terrainBlendBlocks += preparation.terrainBlendBlocks();
            exposedFillSurfaceBlocks += preparation.exposedFillSurfaceBlocks();
        }

        private NaturalPlacementSummary summary() {
            return new NaturalPlacementSummary(blocksAttempted, blocksWritten, roadBlocks, terrainPrepBlocks,
                    vegetationCleared, structureBlocks, farmBlocks, cropBlocks, irrigationBlocks,
                    totalCutBlocks, totalFillBlocks, maxCutDepth, maxFillDepth, terrainBlendBlocks,
                    exposedFillSurfaceBlocks);
        }
    }

    private static String preCommitValidate(ServerLevel level, RuralPlan plan, CommitTracker tracker) {
        if (!plan.valid()) return "plan.valid=false";
        if (plan.lots().size() != plan.targetBuildings()) {
            return "finalBuildings=" + plan.lots().size() + " targetBuildings=" + plan.targetBuildings();
        }
        long farmhouseCount = plan.lots().stream()
                .filter(lot -> lot.structure() == RuralStructurePool.FARMHOUSE).count();
        long barnCount = plan.lots().stream()
                .filter(lot -> lot.structure() == RuralStructurePool.BARN).count();
        if (farmhouseCount != 1) return "farmhouseCount=" + farmhouseCount;
        if (barnCount != 1) return "barnCount=" + barnCount;
        if (plan.road() == null || plan.branchRoads().isEmpty()) return "road geometry missing";

        List<RuralPlan.Lot> checked = new ArrayList<>();
        Set<ResourceLocation> preflightedStructures = new HashSet<>();
        for (int index = 0; index < plan.lots().size(); index++) {
            RuralPlan.Lot lot = plan.lots().get(index);
            tracker.beginLot("PRE_COMMIT", index, lot);
            if (lot.structure() == null || lot.origin() == null || lot.rotation() == null
                    || lot.bounds() == null) return "lot " + index + " has null metadata";
            Optional<StructureTemplate> template = level.getServer().getStructureManager().get(lot.structure().id());
            if (template.isEmpty()) return "template missing " + lot.structure().id();
            if (preflightedStructures.add(lot.structure().id())) {
                String structurePreflightFailure = preflightStructureResource(level, lot.structure().id());
                if (structurePreflightFailure != null) return structurePreflightFailure;
            }
            StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(Mirror.NONE)
                    .setRotation(lot.rotation());
            BoundingBox recomputed = template.get().getBoundingBox(settings, lot.origin());
            if (!inside(recomputed, plan.reservation())) return "lot " + index + " outside reservation";
            for (RuralPlan.Road road : plan.roads()) {
                if (intersects2d(recomputed, road.bounds(), LOT_MARGIN)) {
                    return "lot " + index + " overlaps road";
                }
            }
            for (RuralPlan.Lot previous : checked) {
                if (intersects2d(recomputed, previous.bounds(), LOT_MARGIN)) {
                    return "lot " + index + " overlaps structure";
                }
            }
            checked.add(lot);
            tracker.completedSteps.add("PRE_COMMIT_LOT_" + index);
        }
        for (RuralFarmPlot plot : plan.farmPlots()) {
            if (!plot.valid()) return "farm plot " + plot.index() + " invalid: " + plot.rejectionReason();
            if (!inside(plot.bounds(), plan.reservation())) return "farm plot " + plot.index() + " outside reservation";
        }
        return null;
    }

    /**
     * Validate sign payloads from the same compressed structure resource that the structure manager loads.
     * StructureTemplate keeps its palette list private in the 1.20.1 public API, so validating the source NBT
     * here is the non-reflective equivalent of checking every StructureBlockInfo before any world writes.
     */
    private static String preflightStructureResource(ServerLevel level, ResourceLocation structureId) {
        ResourceLocation resourceId = new ResourceLocation(structureId.getNamespace(),
                "structures/" + structureId.getPath() + ".nbt");
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(resourceId);
        if (resource.isEmpty()) return "structure resource missing " + resourceId;
        try (InputStream input = resource.get().open()) {
            CompoundTag root = NbtIo.readCompressed(input);
            return validateSignPayloads(resourceId, root);
        } catch (IOException exception) {
            return "structure resource read failed " + resourceId + ": " + exception.getMessage();
        }
    }

    private static String validateSignPayloads(ResourceLocation resourceId, CompoundTag root) {
        if (!root.contains("palette", 9) || !root.contains("blocks", 9)) {
            return "structure resource missing palette/blocks " + resourceId;
        }
        ListTag palette = root.getList("palette", 10);
        ListTag blocks = root.getList("blocks", 10);
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            int stateIndex = block.getInt("state");
            if (stateIndex < 0 || stateIndex >= palette.size()) {
                return "structure resource has invalid palette index " + resourceId + " blocks[" + index + "]="
                        + stateIndex;
            }
            String blockName = palette.getCompound(stateIndex).getString("Name");
            if (!blockName.endsWith("_sign")) continue;
            if (!block.contains("nbt", 10)) continue;

            CompoundTag blockEntity = block.getCompound("nbt");
            for (String side : List.of("front_text", "back_text")) {
                if (!blockEntity.contains(side)) continue;
                if (!blockEntity.contains(side, 10)) {
                    return "structure resource sign " + resourceId + " blocks[" + index + "].nbt." + side
                            + " is not a CompoundTag";
                }
                try {
                    DataResult<SignText> result = SignText.DIRECT_CODEC.parse(NbtOps.INSTANCE,
                            blockEntity.getCompound(side));
                    if (result.result().isEmpty()) {
                        String message = result.error().map(error -> error.message()).orElse("codec rejected payload");
                        return "structure resource sign codec rejected " + resourceId + " blocks[" + index
                                + "].nbt." + side + ": " + message;
                    }
                } catch (RuntimeException exception) {
                    return "structure resource sign codec threw " + resourceId + " blocks[" + index + "].nbt."
                            + side + ": " + exception.getClass().getSimpleName() + ": " + exception.getMessage();
                }
            }
        }
        return null;
    }

    private static RuralPlan invalid(BlockPos center, BoundingBox reservation, RuralPlan.SiteScore site,
                                     RuralPlan.Road mainRoad, List<RuralPlan.Road> branchRoads, int target,
                                     List<RuralLayoutPlanner.Candidate> candidates, List<RuralPlan.Lot> accepted,
                                     RejectionTracker rejectionTracker, String reason) {
        return RuralPlan.invalid(center, reservation, site, mainRoad, branchRoads, target, accepted,
                candidates.size(), Math.max(0, candidates.size() - accepted.size()),
                reason,
                rejectionTracker.counts(), rejectionTracker.barnDetails());
    }

    private static int forceDefinition(RuralStructurePool.Definition definition, RuralStructurePool.Role role,
                                       List<RuralLayoutPlanner.Candidate> candidates,
                                       Map<RuralStructurePool.Definition, StructureTemplate> templates,
                                       ServerLevel level, BoundingBox reservation, List<RuralPlan.Road> roads,
                                       List<RuralPlan.Lot> accepted,
                                       Map<RuralStructurePool.Definition, Integer> counts, Set<Integer> usedSlots,
                                       RejectionTracker rejectionTracker) {
        for (int index = 0; index < candidates.size(); index++) {
            if (usedSlots.contains(index) || candidates.get(index).role() != role) continue;
            LotFit fit = fit(level, templates.get(definition), definition, candidates.get(index), reservation, roads,
                    accepted);
            if (!fit.accepted()) {
                rejectionTracker.record(fit.reason());
                if (definition == RuralStructurePool.BARN) {
                    rejectionTracker.recordBarnFailure(definition, candidates.get(index), templates.get(definition), fit);
                }
            }
            if (fit.accepted()) {
                accepted.add(fit.lot());
                counts.merge(definition, 1, Integer::sum);
                usedSlots.add(index);
                return index;
            }
        }
        return -1;
    }

    private static List<RuralStructurePool.Definition> availableFor(RuralStructurePool.Role role,
                                                                      Map<RuralStructurePool.Definition, Integer> counts) {
        List<RuralStructurePool.Definition> result = new ArrayList<>();
        List<RuralStructurePool.Definition> definitions = RuralStructurePool.definitions();
        switch (role) {
            case RESIDENTIAL -> addIfAvailable(result, definitions.get(2), counts);
            case AGRICULTURAL_UTILITY -> {
                addIfAvailable(result, definitions.get(3), counts);
                addIfAvailable(result, definitions.get(4), counts);
            }
            case LANDMARK -> addIfAvailable(result, definitions.get(5), counts);
            case FLEX -> {
                addIfAvailable(result, definitions.get(2), counts);
                addIfAvailable(result, definitions.get(3), counts);
                addIfAvailable(result, definitions.get(4), counts);
                addIfAvailable(result, definitions.get(5), counts);
            }
            default -> {
            }
        }
        return result;
    }

    private static void addIfAvailable(List<RuralStructurePool.Definition> result,
                                       RuralStructurePool.Definition definition,
                                       Map<RuralStructurePool.Definition, Integer> counts) {
        if (counts.getOrDefault(definition, 0) < definition.maxCount()) result.add(definition);
    }

    private static RuralPlan.SiteScore inspectSite(ServerLevel level, BoundingBox reservation) {
        List<Integer> heights = new ArrayList<>();
        int water = 0;
        int steep = 0;
        int edges = 0;
        int sampledColumns = 0;
        int validGroundSamples = 0;
        int correctedVegetationSamples = 0;
        Map<Long, Integer> sampled = new HashMap<>();
        for (int x = reservation.minX(); x <= reservation.maxX(); x += SITE_SAMPLE_STEP) {
            for (int z = reservation.minZ(); z <= reservation.maxZ(); z += SITE_SAMPLE_STEP) {
                sampledColumns++;
                level.getChunk(x >> 4, z >> 4);
                RuralTerrainSampler.Sample sample = RuralTerrainSampler.sample(level, x, z);
                if (!sample.valid()) continue;
                validGroundSamples++;
                correctedVegetationSamples += sample.vegetationBlocksSkipped();
                heights.add(sample.surfaceY());
                sampled.put(BlockPos.asLong(x, 0, z), sample.surfaceY());
                if (sample.water()) water++;
            }
        }
        for (Map.Entry<Long, Integer> entry : sampled.entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            for (Direction direction : new Direction[]{Direction.EAST, Direction.SOUTH}) {
                long neighborKey = BlockPos.asLong(pos.getX() + direction.getStepX() * SITE_SAMPLE_STEP, 0,
                        pos.getZ() + direction.getStepZ() * SITE_SAMPLE_STEP);
                Integer neighbor = sampled.get(neighborKey);
                if (neighbor != null) {
                    edges++;
                    if (Math.abs(entry.getValue() - neighbor) > 8) steep++;
                }
            }
        }
        heights.sort(Comparator.naturalOrder());
        int p10 = percentile(heights, 0.10D);
        int median = percentile(heights, 0.50D);
        int p90 = percentile(heights, 0.90D);
        double waterRatio = validGroundSamples == 0 ? 1.0D : water / (double) validGroundSamples;
        double steepRatio = edges == 0 ? 0.0D : steep / (double) edges;
        double score = Mth.clamp(1.0D - waterRatio * 0.8D - (p90 - p10) / 36.0D - steepRatio * 0.5D, 0.0D, 1.0D);
        return new RuralPlan.SiteScore(sampledColumns, validGroundSamples, correctedVegetationSamples,
                water, steep, waterRatio, p10, median, p90, p90 - p10, steepRatio, score);
    }

    private static LotFit fit(ServerLevel level, StructureTemplate template, RuralStructurePool.Definition definition,
                              RuralLayoutPlanner.Candidate candidate, BoundingBox reservation, List<RuralPlan.Road> roads,
                              List<RuralPlan.Lot> accepted) {
        Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            return LotFit.rejected(RuralPlan.RejectionReason.TOO_SMALL, "template size=" + size);
        }
        Rotation rotation = candidate.rotationOverride() == null
                ? RuralLayoutPlanner.rotationFor(definition.frontDirection(), candidate.roadFacing())
                : candidate.rotationOverride();
        if (!RuralLayoutPlanner.facesRoad(definition, rotation, candidate.roadFacing())) {
            return LotFit.rejected(RuralPlan.RejectionReason.FRONT_ROAD_FACING_MISMATCH,
                    "front=" + definition.frontDirection() + " rotation=" + rotation
                            + " roadFacing=" + candidate.roadFacing());
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(rotation);
        BoundingBox atGround = template.getBoundingBox(settings,
                new BlockPos(candidate.anchor().getX(), 0, candidate.anchor().getZ()));
        if (!inside(atGround, reservation)) {
            return LotFit.rejected(RuralPlan.RejectionReason.RESERVATION_BOUNDS,
                    "bounds=" + atGround + " reservation=" + reservation);
        }
        for (RuralPlan.Road road : roads) {
            if (intersects2d(atGround, road.bounds(), LOT_MARGIN)) {
                return LotFit.rejected(RuralPlan.RejectionReason.ROAD_OVERLAP,
                        "bounds=" + atGround + " road=" + road.bounds());
            }
        }
        for (RuralPlan.Lot lot : accepted) {
            if (intersects2d(atGround, lot.bounds(), LOT_MARGIN)) {
                return LotFit.rejected(RuralPlan.RejectionReason.STRUCTURE_OVERLAP,
                        "bounds=" + atGround + " existing=" + lot.bounds());
            }
        }

        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        int validSamples = 0;
        int stepX = Math.max(1, (atGround.maxX() - atGround.minX()) / 4);
        int stepZ = Math.max(1, (atGround.maxZ() - atGround.minZ()) / 4);
        for (int x = atGround.minX(); x <= atGround.maxX(); x += stepX) {
            for (int z = atGround.minZ(); z <= atGround.maxZ(); z += stepZ) {
                RuralTerrainSampler.Sample sample = RuralTerrainSampler.sample(level, x, z);
                if (!sample.valid()) {
                    return LotFit.rejected(RuralPlan.RejectionReason.INVALID_GROUND,
                            "sample=" + x + "," + z);
                }
                if (sample.water()) {
                    return LotFit.rejected(RuralPlan.RejectionReason.WATER,
                            "sample=" + x + "," + z);
                }
                validSamples++;
                minSurface = Math.min(minSurface, sample.surfaceY());
                maxSurface = Math.max(maxSurface, sample.surfaceY());
            }
        }
        if (validSamples == 0) {
            return LotFit.rejected(RuralPlan.RejectionReason.INVALID_GROUND, "no footprint samples");
        }
        int relief = maxSurface - minSurface;
        if (relief > MAX_LOT_RELIEF) {
            return LotFit.rejected(RuralPlan.RejectionReason.TERRAIN_RELIEF, "lot relief=" + relief);
        }
        if (relief > MAX_LOT_FILL_DEPTH) {
            return LotFit.rejected(RuralPlan.RejectionReason.TERRAIN_RELIEF,
                    "foundation fill too deep relief=" + relief);
        }

        int desiredGroundY = maxSurface;
        BlockPos origin = new BlockPos(candidate.anchor().getX(),
                desiredGroundY - definition.groundAnchorOffsetY(), candidate.anchor().getZ());
        BoundingBox finalBox = template.getBoundingBox(settings, origin);
        return LotFit.accepted(new RuralPlan.Lot(definition, origin, rotation, finalBox, desiredGroundY,
                candidate.roadFacing()));
    }

    private static RoadPlacementResult placeRoad(ServerLevel level, RuralPlan plan, CommitTracker tracker) {
        Set<Long> mainPositions = new HashSet<>();
        Set<Long> branchPositions = new HashSet<>();
        for (RuralPlan.Road road : plan.roads()) {
            BoundingBox bounds = road.bounds();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    long key = BlockPos.asLong(x, 0, z);
                    if (road.branch()) branchPositions.add(key);
                    else mainPositions.add(key);
                }
            }
        }
        Set<Long> allPositions = new HashSet<>(mainPositions);
        allPositions.addAll(branchPositions);
        tracker.phase = "ROAD_MAIN";
        for (long key : mainPositions) {
            BlockPos horizontal = BlockPos.of(key);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, horizontal.getX(), horizontal.getZ()) - 1;
            level.setBlock(new BlockPos(horizontal.getX(), y, horizontal.getZ()), Blocks.GRAVEL.defaultBlockState(), 3);
        }
        tracker.completedSteps.add("ROAD_MAIN");
        tracker.phase = "ROAD_BRANCH";
        for (long key : branchPositions) {
            BlockPos horizontal = BlockPos.of(key);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, horizontal.getX(), horizontal.getZ()) - 1;
            level.setBlock(new BlockPos(horizontal.getX(), y, horizontal.getZ()), Blocks.GRAVEL.defaultBlockState(), 3);
        }
        tracker.completedSteps.add("ROAD_BRANCH");
        return new RoadPlacementResult(mainPositions.size(), branchPositions.size(), allPositions.size());
    }

    private static FarmPlacementResult placeFarmPlots(ServerLevel level, RuralPlan plan, CommitTracker tracker) {
        int farmPlots = 0;
        int terrainBlocks = 0;
        int farmlandBlocks = 0;
        int cropBlocks = 0;
        int fenceBlocks = 0;
        int gateBlocks = 0;
        int irrigationBlocks = 0;
        int pathBlocks = 0;
        int logsCleared = 0;
        int leavesCleared = 0;
        int vegetationCleared = 0;
        for (RuralFarmPlot plot : plan.farmPlots()) {
            FarmPlotPlacementPlan placement;
            try {
                placement = preflightFarmPlot(level, plot);
            } catch (Throwable throwable) {
                ApocalypseFirstLight.LOGGER.warn("[Rural] optional farm plot preflight skipped index={} owner={} reason={}",
                        plot.index(), plot.ownerId(), throwable.toString());
                continue;
            }
            FarmPlotCommitJournal journal = new FarmPlotCommitJournal();
            try {
                for (int x = plot.bounds().minX() - 1 >> 4; x <= plot.bounds().maxX() + 1 >> 4; x++) {
                    for (int z = plot.bounds().minZ() - 1 >> 4; z <= plot.bounds().maxZ() + 1 >> 4; z++) {
                        level.getChunk(x, z);
                    }
                }
                journal.captureRegion(level, plot);
                tracker.phase = "FARMLAND_PREP";
                RuralTerrainAdapter.PreparationResult preparation = RuralTerrainAdapter.prepare(level, plot);
                int plotTerrainBlocks = preparation.changed();
                int plotLogsCleared = preparation.logsCleared();
                int plotLeavesCleared = preparation.leavesCleared();
                int plotVegetationCleared = preparation.vegetationCleared();
                for (BlockPos pos : plot.irrigationCells()) {
                    BlockPos placed = new BlockPos(pos.getX(), plot.baseY(), pos.getZ());
                    level.setBlock(placed, Blocks.WATER.defaultBlockState(), 3);
                }
                int plotIrrigationBlocks = plot.irrigationCells().size();
                for (BlockPos pos : plot.pathCells()) {
                    BlockPos placed = new BlockPos(pos.getX(), plot.baseY(), pos.getZ());
                    level.setBlock(placed, Blocks.DIRT_PATH.defaultBlockState(), 3);
                }
                int plotPathBlocks = plot.pathCells().size();
                int plotFarmlandBlocks = 0;
                int plotCropBlocks = 0;
                for (RuralFarmPlot.Cell cell : plot.cells()) {
                    long key = cell.key();
                    if (placement.irrigation().contains(key) || placement.path().contains(key)) continue;
                    BlockPos farmland = new BlockPos(cell.x(), plot.baseY(), cell.z());
                    level.setBlock(farmland, Blocks.FARMLAND.defaultBlockState(), 3);
                    plotFarmlandBlocks++;
                    level.setBlock(farmland.above(), placement.crops().get(key), 3);
                    plotCropBlocks++;
                }
                int plotFenceBlocks = 0;
                for (RuralFarmPlot.Fence fence : plot.fences()) {
                    level.setBlock(fence.pos(), Blocks.OAK_FENCE.defaultBlockState(), 3);
                    plotFenceBlocks++;
                }
                int plotGateBlocks = 0;
                for (RuralFarmPlot.Gate gate : plot.gates()) {
                    level.setBlock(gate.pos(), Blocks.OAK_FENCE_GATE.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, gate.facing()), 3);
                    plotGateBlocks++;
                }
                journal.discard();
                terrainBlocks += plotTerrainBlocks;
                logsCleared += plotLogsCleared;
                leavesCleared += plotLeavesCleared;
                vegetationCleared += plotVegetationCleared;
                irrigationBlocks += plotIrrigationBlocks;
                pathBlocks += plotPathBlocks;
                farmlandBlocks += plotFarmlandBlocks;
                cropBlocks += plotCropBlocks;
                fenceBlocks += plotFenceBlocks;
                gateBlocks += plotGateBlocks;
                farmPlots++;
            } catch (Throwable throwable) {
                try {
                    journal.rollback(level);
                } catch (Throwable rollbackFailure) {
                    ApocalypseFirstLight.LOGGER.error("[Rural] farm plot rollback failed index={} owner={}",
                            plot.index(), plot.ownerId(), rollbackFailure);
                }
                ApocalypseFirstLight.LOGGER.warn("[Rural] optional farm plot skipped index={} owner={} reason={}",
                        plot.index(), plot.ownerId(), throwable.toString());
            }
        }
        return new FarmPlacementResult(farmPlots, terrainBlocks, farmlandBlocks, cropBlocks,
                fenceBlocks, gateBlocks, irrigationBlocks, pathBlocks, logsCleared, leavesCleared, vegetationCleared);
    }

    private static FarmPlotPlacementPlan preflightFarmPlot(ServerLevel level, RuralFarmPlot plot) {
        Set<Long> irrigation = new HashSet<>();
        Map<Long, net.minecraft.world.level.block.state.BlockState> fixedStates = new LinkedHashMap<>();
        for (BlockPos pos : plot.irrigationCells()) {
            long key = BlockPos.asLong(pos.getX(), 0, pos.getZ());
            if (!plot.contains(pos.getX(), pos.getZ()) || !irrigation.add(key)) {
                throw new IllegalStateException("invalid irrigation cell " + pos);
            }
            fixedStates.put(key, Blocks.WATER.defaultBlockState());
        }
        Set<Long> path = new HashSet<>();
        for (BlockPos pos : plot.pathCells()) {
            long key = BlockPos.asLong(pos.getX(), 0, pos.getZ());
            if (!path.add(key) || irrigation.contains(key)) {
                throw new IllegalStateException("invalid path cell " + pos);
            }
            fixedStates.put(key, Blocks.DIRT_PATH.defaultBlockState());
        }
        Map<Long, net.minecraft.world.level.block.state.BlockState> crops = new LinkedHashMap<>();
        for (RuralFarmPlot.Cell cell : plot.cells()) {
            if (irrigation.contains(cell.key()) || path.contains(cell.key())) continue;
            RuralFarmPlot.GrowthBand band = growthBand(level.getSeed(), plot.index(), cell.key());
            net.minecraft.world.level.block.state.BlockState cropState = plot.crop().state(band);
            crops.put(cell.key(), cropState);
            fixedStates.put(cell.key(), Blocks.FARMLAND.defaultBlockState());
        }
        for (RuralFarmPlot.Fence fence : plot.fences()) {
            fixedStates.put(BlockPos.asLong(fence.pos().getX(), 0, fence.pos().getZ()),
                    Blocks.OAK_FENCE.defaultBlockState());
        }
        for (RuralFarmPlot.Gate gate : plot.gates()) {
            fixedStates.put(BlockPos.asLong(gate.pos().getX(), 0, gate.pos().getZ()),
                    Blocks.OAK_FENCE_GATE.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, gate.facing()));
        }
        if (crops.isEmpty()) throw new IllegalStateException("farm plot has no crop cells");
        if (fixedStates.isEmpty()) throw new IllegalStateException("farm plot has no final states");
        return new FarmPlotPlacementPlan(Set.copyOf(irrigation), Set.copyOf(path), Map.copyOf(crops));
    }

    private static RuralFarmPlot.GrowthBand growthBand(long seed, int plotIndex, long cellKey) {
        int roll = (int) Math.floorMod(seed ^ (long) plotIndex * 0x9E3779B97F4A7C15L ^ cellKey, 100L);
        if (roll < 10) return RuralFarmPlot.GrowthBand.EARLY;
        if (roll < 40) return RuralFarmPlot.GrowthBand.MID;
        if (roll < 80) return RuralFarmPlot.GrowthBand.LATE;
        return RuralFarmPlot.GrowthBand.MATURE;
    }

    private static boolean place(ServerLevel level, StructureTemplate template, RuralPlan.Lot lot) {
        for (int x = lot.bounds().minX() >> 4; x <= lot.bounds().maxX() >> 4; x++) {
            for (int z = lot.bounds().minZ() >> 4; z <= lot.bounds().maxZ() >> 4; z++) level.getChunk(x, z);
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(lot.rotation());
        return template.placeInWorld(level, lot.origin(), lot.origin(), settings,
                RandomSource.create(level.getSeed() ^ lot.origin().asLong()), 2);
    }

    private static RuralPlan.Road road(BlockPos center, Direction direction, boolean branch) {
        int length = branch ? BRANCH_LENGTH : ROAD_LENGTH;
        if (!branch) {
            if (direction.getAxis() == Direction.Axis.X) {
                return new RuralPlan.Road(direction,
                        new BoundingBox(center.getX() - length / 2, 0, center.getZ() - ROAD_WIDTH / 2,
                                center.getX() + length / 2 - 1, 0, center.getZ() + ROAD_WIDTH / 2), ROAD_WIDTH, false);
            }
            return new RuralPlan.Road(direction,
                    new BoundingBox(center.getX() - ROAD_WIDTH / 2, 0, center.getZ() - length / 2,
                            center.getX() + ROAD_WIDTH / 2, 0, center.getZ() + length / 2 - 1), ROAD_WIDTH, false);
        }
        int endX = center.getX() + direction.getStepX() * (length - 1);
        int endZ = center.getZ() + direction.getStepZ() * (length - 1);
        return new RuralPlan.Road(direction,
                new BoundingBox(Math.min(center.getX(), endX) - ROAD_WIDTH / 2, 0,
                        Math.min(center.getZ(), endZ) - ROAD_WIDTH / 2,
                        Math.max(center.getX(), endX) + ROAD_WIDTH / 2, 0,
                        Math.max(center.getZ(), endZ) + ROAD_WIDTH / 2), ROAD_WIDTH, true);
    }

    private static List<RuralPlan.Road> branchRoads(BlockPos center, Direction mainDirection, long seed) {
        Direction branchDirection = Math.floorMod(seed ^ center.asLong() ^ 0x4252414E4348L, 2L) == 0
                ? mainDirection.getClockWise() : mainDirection.getCounterClockWise();
        return List.of(road(center, branchDirection, true));
    }

    private static BoundingBox reservation(BlockPos center) {
        int half = RESERVATION_SIZE / 2;
        return new BoundingBox(center.getX() - half, 0, center.getZ() - half,
                center.getX() + half - 1, 0, center.getZ() + half - 1);
    }

    private static int targetBuildingCount(long seed, BlockPos center) {
        return 6 + (int) Math.floorMod(seed ^ center.asLong() ^ TARGET_SALT, 3L);
    }

    private static Direction siteDirection(long seed, BlockPos center) {
        return switch ((int) Math.floorMod(seed ^ center.asLong(), 4L)) {
            case 1 -> Direction.SOUTH;
            case 2 -> Direction.WEST;
            case 3 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    private static RuralStructurePool.Definition weightedPick(List<RuralStructurePool.Definition> definitions,
                                                               RandomSource random) {
        int total = definitions.stream().mapToInt(RuralStructurePool.Definition::weight).sum();
        if (total <= 0) return definitions.get(0);
        int value = random.nextInt(total);
        for (RuralStructurePool.Definition definition : definitions) {
            value -= definition.weight();
            if (value < 0) return definition;
        }
        return definitions.get(definitions.size() - 1);
    }

    private static int percentile(List<Integer> sorted, double fraction) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.round((sorted.size() - 1) * fraction);
        return sorted.get(Mth.clamp(index, 0, sorted.size() - 1));
    }

    private static boolean inside(BoundingBox box, BoundingBox outer) {
        return box.minX() >= outer.minX() && box.maxX() <= outer.maxX()
                && box.minZ() >= outer.minZ() && box.maxZ() <= outer.maxZ();
    }

    private static boolean intersects2d(BoundingBox a, BoundingBox b, int margin) {
        return a.minX() - margin <= b.maxX() && a.maxX() + margin >= b.minX()
                && a.minZ() - margin <= b.maxZ() && a.maxZ() + margin >= b.minZ();
    }

    private record RoadPlacementResult(int mainRoadBlocks, int branchRoadBlocks, int totalRoadBlocks) {
    }

    private record FarmPlacementResult(int farmPlots, int terrainBlocks, int farmlandBlocks, int cropBlocks,
                                       int fenceBlocks, int gateBlocks, int irrigationBlocks, int pathBlocks,
                                       int logsCleared, int leavesCleared, int vegetationCleared) {
    }

    private record FarmPlotPlacementPlan(Set<Long> irrigation, Set<Long> path,
                                         Map<Long, net.minecraft.world.level.block.state.BlockState> crops) {
    }

    public static final class GenerationCrashedException extends RuntimeException {
        private final String context;

        private GenerationCrashedException(String context, Throwable cause) {
            super("Rural generation crashed: " + context, cause);
            this.context = context;
        }

        public String context() {
            return context;
        }
    }

    private static final class CommitTracker {
        private final BlockPos center;
        private final int targetBuildings;
        private final int finalBuildings;
        private String phase = "PRE_COMMIT";
        private int lotIndex = -1;
        private String structureId = "none";
        private String role = "none";
        private BlockPos origin;
        private Rotation rotation;
        private int baseY;
        private final List<String> completedSteps = new ArrayList<>();

        private CommitTracker(BlockPos center, RuralPlan plan) {
            this.center = center;
            this.targetBuildings = plan.targetBuildings();
            this.finalBuildings = plan.lots().size();
        }

        private void beginLot(String phase, int index, RuralPlan.Lot lot) {
            this.phase = phase;
            this.lotIndex = index;
            this.structureId = lot.structure() == null ? "null" : lot.structure().id().toString();
            this.role = lot.structure() == null ? "null" : lot.structure().role().name();
            this.origin = lot.origin();
            this.rotation = lot.rotation();
            this.baseY = lot.baseY();
        }

        private String context() {
            return String.format(
                    "center=%s phase=%s currentStructure=%s lotRole=%s lotIndex=%d origin=%s rotation=%s baseY=%d targetBuildings=%d finalPlannedBuildings=%d completedSteps=%s PARTIAL_COMMIT=true",
                    center, phase, structureId, role, lotIndex, origin, rotation, baseY,
                    targetBuildings, finalBuildings, completedSteps);
        }
    }

    public record GenerationResult(boolean success, String message, RuralPlan plan,
                                   int buildingsPlaced, int mainRoadBlocks, int branchRoadBlocks, int terrainBlocks,
                                   int logsCleared, int leavesCleared, int vegetationCleared,
                                   int farmPlots, int farmlandBlocks, int cropBlocks, int fenceBlocks,
                                   int gateBlocks, int irrigationBlocks, int pathBlocks,
                                   int farmlandLogsCleared, int farmlandLeavesCleared,
                                   int farmlandVegetationCleared) {
        public int roadBlocks() {
            return mainRoadBlocks + branchRoadBlocks;
        }

        public static GenerationResult success(RuralPlan plan, int buildings, int mainRoadBlocks, int branchRoadBlocks,
                                                int terrainBlocks,
                                                int logsCleared, int leavesCleared, int vegetationCleared,
                                                FarmPlacementResult farm) {
            return new GenerationResult(true, "OK", plan, buildings, mainRoadBlocks, branchRoadBlocks, terrainBlocks,
                    logsCleared, leavesCleared, vegetationCleared, farm.farmPlots(), farm.farmlandBlocks(),
                    farm.cropBlocks(), farm.fenceBlocks(), farm.gateBlocks(), farm.irrigationBlocks(), farm.pathBlocks(),
                    farm.logsCleared(), farm.leavesCleared(), farm.vegetationCleared());
        }

        public static GenerationResult failed(String message, RuralPlan plan) {
            return new GenerationResult(false, message, plan, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private record LotFit(RuralPlan.Lot lot, boolean accepted, RuralPlan.RejectionReason reason, String detail) {
        private static LotFit accepted(RuralPlan.Lot lot) {
            return new LotFit(lot, true, null, "OK");
        }

        private static LotFit rejected(RuralPlan.RejectionReason reason, String detail) {
            return new LotFit(null, false, reason, detail);
        }
    }

    private static final class RejectionTracker {
        private final Map<RuralPlan.RejectionReason, Integer> counts = new java.util.EnumMap<>(RuralPlan.RejectionReason.class);
        private final List<String> barnDetails = new ArrayList<>();

        private RejectionTracker() {
            for (RuralPlan.RejectionReason reason : RuralPlan.RejectionReason.values()) counts.put(reason, 0);
        }

        private void record(RuralPlan.RejectionReason reason) {
            if (reason != null) counts.merge(reason, 1, Integer::sum);
        }

        private void recordBarnFailure(RuralStructurePool.Definition definition, RuralLayoutPlanner.Candidate candidate,
                                       StructureTemplate template, LotFit fit) {
            if (barnDetails.size() >= 5) return;
            Vec3i size = template.getSize();
            barnDetails.add(String.format(
                    "reason=%s detail=%s candidate=%s bounds=%s requiredFootprint=%dx%dx%d rotation=%s roadSide=%s distanceFromRoad=%d",
                    fit.reason(), fit.detail(), candidate.anchor().toShortString(),
                    RuralLayoutPlanner.boundsAt(template, candidate.rotationOverride() == null
                            ? RuralLayoutPlanner.rotationFor(definition.frontDirection(), candidate.roadFacing())
                            : candidate.rotationOverride(), candidate.anchor()),
                    size.getX(), size.getY(), size.getZ(),
                    candidate.rotationOverride() == null
                            ? RuralLayoutPlanner.rotationFor(definition.frontDirection(), candidate.roadFacing())
                            : candidate.rotationOverride(),
                    candidate.roadSide(), candidate.distanceFromRoad()));
        }

        private Map<RuralPlan.RejectionReason, Integer> counts() {
            return new java.util.EnumMap<>(counts);
        }

        private List<String> barnDetails() {
            return List.copyOf(barnDetails);
        }
    }
}
