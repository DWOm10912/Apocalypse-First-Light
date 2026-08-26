package com.antaurora.apofirstlight.worldgen.rural;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Natural-generation planner. It is read-only and never asks the world to load a chunk.
 * The resulting plan is replayed by each bounded StructurePiece invocation.
 */
public final class RuralNaturalGenerator {
    private static final org.slf4j.Logger LOGGER = ApocalypseFirstLight.LOGGER;
    private static final int SITE_SAMPLE_STEP = 8;
    private static final int MAX_SITE_WATER_RATIO = RuralGenerator.MAX_SITE_WATER_RATIO;
    private static final int MAX_SITE_RELIEF = RuralGenerator.MAX_SITE_ROBUST_RELIEF;
    private static final double MAX_SITE_STEEP_RATIO = RuralGenerator.MAX_SITE_STEEP_RATIO;
    private static final long PLAN_SALT = 0x4E41545552414C31L;

    private RuralNaturalGenerator() {
    }

    public static RuralPlan plan(WorldGenRegion level, BlockPos center) {
        return plan(RuralTerrainSampler.source(level), level.getServer().getStructureManager()::get,
                level.getSeed(), center, 0.0D);
    }

    public static RuralPlan plan(Structure.GenerationContext context, BlockPos center) {
        RuralTerrainSource terrain = RuralTerrainSampler.source(context.chunkGenerator(), context.heightAccessor(),
                context.randomState());
        return plan(terrain, context.structureTemplateManager()::get, context.seed(), center, 0.0D);
    }

    public static RuralPlan plan(Structure.GenerationContext context, BlockPos center, double fastSiteCheckMs) {
        RuralTerrainSource terrain = RuralTerrainSampler.source(context.chunkGenerator(), context.heightAccessor(),
                context.randomState());
        return plan(terrain, context.structureTemplateManager()::get, context.seed(), center, fastSiteCheckMs);
    }

    public static boolean fastSiteCheck(Structure.GenerationContext context, BlockPos center) {
        RuralTerrainSource terrain = RuralTerrainSampler.source(context.chunkGenerator(), context.heightAccessor(),
                context.randomState());
        int valid = 0;
        int water = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx : new int[]{-24, 0, 24}) {
            for (int dz : new int[]{-24, 0, 24}) {
                RuralTerrainSampler.Sample sample = terrain.sample(center.getX() + dx, center.getZ() + dz);
                if (!sample.valid()) continue;
                valid++;
                if (sample.water()) water++;
                min = Math.min(min, sample.surfaceY());
                max = Math.max(max, sample.surfaceY());
            }
        }
        double waterRatio = valid == 0 ? 1.0D : water / (double) valid;
        return valid >= 8 && waterRatio * 100.0D <= MAX_SITE_WATER_RATIO
                && max - min <= MAX_SITE_RELIEF + RuralGenerator.MAX_LOT_CORRECTION;
    }

    private static RuralPlan plan(RuralTerrainSource terrain,
                                  Function<ResourceLocation, Optional<StructureTemplate>> templateSource,
                                  long seed, BlockPos center, double fastSiteCheckMs) {
        long totalStart = System.nanoTime();
        RuralScaleTier tier = RuralScaleTier.choose(seed, center);
        int reservationSize = reservationSize(tier);
        BoundingBox reservation = reservation(center, reservationSize);
        long siteStart = System.nanoTime();
        RuralPlan.SiteScore site = inspectSite(terrain, reservation);
        long siteInspectNanos = System.nanoTime() - siteStart;
        Direction mainDirection = siteDirection(seed, center);
        RuralPlan.Road mainRoad = road(center, mainDirection, tier.roadLength(), tier.roadWidth(), false);
        List<RuralPlan.Road> branches = branches(center, mainDirection, tier, seed);
        int target = tier.targetBuildings(seed, center);
        Map<RuralPlan.RejectionReason, Integer> rejections = emptyRejections();

        LOGGER.debug("[Rural Natural] candidate center={} chunk={} tier={} tierRoll={} targetBuildings={} farmPlotTarget={} siteScore={} waterRatio={} relief={} steepRatio={}",
                center, new net.minecraft.world.level.ChunkPos(center), tier,
                Math.floorMod(seed ^ center.asLong() ^ 0x525552414C534341L, 100), target,
                tier.targetFarms(seed, center), site.score(), site.waterRatio(), site.robustRelief(), site.steepRatio());

        if (site.sampledColumns() == 0 || site.validGroundSamples() == 0) {
            return invalid(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "site has no usable terrain samples", rejections);
        }
        if (site.waterRatio() * 100.0D > MAX_SITE_WATER_RATIO) {
            return invalid(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "site water ratio exceeds threshold", rejections);
        }
        if (site.robustRelief() > MAX_SITE_RELIEF || site.steepRatio() > MAX_SITE_STEEP_RATIO) {
            return invalid(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "site relief or steep ratio exceeds threshold", rejections);
        }

        long templateStart = System.nanoTime();
        Map<RuralStructurePool.Definition, StructureTemplate> templates = new LinkedHashMap<>();
        for (RuralStructurePool.Definition definition : RuralStructurePool.definitions()) {
            Optional<StructureTemplate> template = templateSource.apply(definition.id());
            if (template.isPresent()) templates.put(definition, template.get());
        }
        long templateLookupNanos = System.nanoTime() - templateStart;
        StructureTemplate barnTemplate = templates.get(RuralStructurePool.BARN);
        if (barnTemplate == null) {
            return invalid(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "missing structure template " + RuralStructurePool.BARN.id(), rejections);
        }
        Direction branchDirection = branches.isEmpty() ? mainDirection : branches.get(0).direction();
        List<RuralLayoutPlanner.Candidate> candidates = RuralLayoutPlanner.candidates(center, mainDirection,
                branchDirection, barnTemplate);
        List<Spec> specs = assignments(tier, seed, center, candidates);
        long lotStart = System.nanoTime();
        List<RuralPlan.Lot> accepted = new ArrayList<>();
        List<RuralPlan.Road> roads = new ArrayList<>(1 + branches.size());
        roads.add(mainRoad);
        roads.addAll(branches);
        for (Spec spec : specs) {
            StructureTemplate template = templates.get(spec.definition());
            if (template == null) {
                return invalid(center, reservation, site, mainRoad, branches, target, tier, seed,
                        "missing structure template " + spec.definition().id(), rejections);
            }
            RuralPlan.Lot lot = findLot(terrain, template, spec.definition(), spec, reservation, roads, accepted);
            if (lot != null) accepted.add(lot);
            if (accepted.size() >= target) break;
        }
        long lotPlanningNanos = System.nanoTime() - lotStart;

        if (accepted.size() < tier.minBuildings()) {
            return invalid(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "suitable or adaptable lots below tier minimum: " + accepted.size(), rejections);
        }
        if (accepted.size() < target && tier == RuralScaleTier.FULL_RURAL) {
            return invalid(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "full rural could not reach target building count", rejections);
        }
        if (!hasResidential(accepted)) {
            return invalid(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "natural rural has no residential building", rejections);
        }
        if (tier == RuralScaleTier.FULL_RURAL && accepted.stream().noneMatch(lot ->
                lot.structure().role() == RuralStructurePool.Role.RESIDENTIAL)) {
            return invalid(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "full rural requires a non-farmhouse residence", rejections);
        }
        if (tier == RuralScaleTier.FARMSTEAD || tier == RuralScaleTier.FULL_RURAL) {
            if (accepted.stream().noneMatch(lot -> lot.structure().role() == RuralStructurePool.Role.AGRICULTURAL_LARGE)) {
                return invalid(center, reservation, site, mainRoad, branches, target, tier, seed,
                        "tier requires a barn", rejections);
            }
        }
        if (tier == RuralScaleTier.RURAL_CLUSTER && accepted.stream().noneMatch(lot ->
                lot.structure().role() == RuralStructurePool.Role.AGRICULTURAL_LARGE
                        || lot.structure().role() == RuralStructurePool.Role.AGRICULTURAL_UTILITY)) {
            return invalid(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "rural cluster requires an agricultural building", rejections);
        }

        int farmTarget = tier.targetFarms(seed, center);
        long farmStart = System.nanoTime();
        RuralFarmPlanner.Result farm = RuralFarmPlanner.plan(terrain, seed, center, reservation, roads, accepted,
                farmTarget, farmTarget);
        long farmPlanningNanos = System.nanoTime() - farmStart;
        if (farm.count() < tier.minFarms()) {
            return RuralPlan.invalidNatural(center, reservation, site, mainRoad, branches, target, accepted,
                    specs.size(), Math.max(0, specs.size() - accepted.size()),
                    "usable natural farm plots below tier minimum: " + farm.count(),
                    rejections, List.of(), tier, seed, tier.roadLayout(), farmTarget, farm.plots(),
                    farm.rejections());
        }
        LOGGER.debug("[Rural Natural] accepted center={} tier={} buildings={}/{} naturalLots={} adaptableLots={} unusableLots={} cut={} fill={} maxCut={} maxFill={} farms={}/{} roadLayout={} reservation={} structures={}",
                center, tier, accepted.size(), target, naturalSuitableLots(accepted), adaptableLots(accepted),
                Math.max(0, specs.size() - accepted.size()), predictedCut(accepted), predictedFill(accepted),
                maxCut(accepted), maxFill(accepted), farm.count(), farmTarget, tier.roadLayout(), reservation,
                accepted.stream().map(lot -> lot.structure().id().toString() + "@" + lot.origin()
                        + "/" + lot.rotation()).toList());
        LOGGER.debug("[AFL RURAL NATURAL][PLAN_TIMING] center={} fastSiteCheckMs={} siteInspectMs={} templateLookupMs={} lotPlanningMs={} farmPlanningMs={} totalPlanMs={}",
                center, fastSiteCheckMs, millis(siteInspectNanos), millis(templateLookupNanos), millis(lotPlanningNanos),
                millis(farmPlanningNanos), millis(System.nanoTime() - totalStart));
        return RuralPlan.validNatural(center, reservation, site, mainRoad, branches, accepted, target,
                specs.size(), Math.max(0, specs.size() - accepted.size()), accepted.size() < target,
                rejections, List.of(), farmTarget, farm.plots(), farm.rejections(), tier, seed, tier.roadLayout());
    }

    private static RuralPlan invalid(BlockPos center, BoundingBox reservation, RuralPlan.SiteScore site,
                                     RuralPlan.Road road, List<RuralPlan.Road> branches, int target,
                                     RuralScaleTier tier, long seed, String reason,
                                     Map<RuralPlan.RejectionReason, Integer> rejections) {
        return RuralPlan.invalidNatural(center, reservation, site, road, branches, target, List.of(), 0, 0,
                reason, rejections, List.of(), tier, seed, tier.roadLayout(), tier.targetFarms(seed, center),
                List.of(), List.of());
    }

    private static RuralPlan.SiteScore inspectSite(RuralTerrainSource terrain, BoundingBox reservation) {
        List<Integer> heights = new ArrayList<>();
        Map<Long, Integer> sampled = new HashMap<>();
        int water = 0;
        int steep = 0;
        int edges = 0;
        int sampledColumns = 0;
        int valid = 0;
        int vegetation = 0;
        for (int x = reservation.minX(); x <= reservation.maxX(); x += SITE_SAMPLE_STEP) {
            for (int z = reservation.minZ(); z <= reservation.maxZ(); z += SITE_SAMPLE_STEP) {
                sampledColumns++;
                RuralTerrainSampler.Sample sample = terrain.sample(x, z);
                if (!sample.valid()) continue;
                valid++;
                vegetation += sample.vegetationBlocksSkipped();
                heights.add(sample.surfaceY());
                sampled.put(BlockPos.asLong(x, 0, z), sample.surfaceY());
                if (sample.water()) water++;
            }
        }
        for (Map.Entry<Long, Integer> entry : sampled.entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            for (Direction direction : new Direction[]{Direction.EAST, Direction.SOUTH}) {
                Integer neighbor = sampled.get(BlockPos.asLong(pos.getX() + SITE_SAMPLE_STEP * direction.getStepX(),
                        0, pos.getZ() + SITE_SAMPLE_STEP * direction.getStepZ()));
                if (neighbor != null) {
                    edges++;
                    if (Math.abs(entry.getValue() - neighbor) > 8) steep++;
                }
            }
        }
        heights.sort(Integer::compareTo);
        int p10 = percentile(heights, 0.10D);
        int median = percentile(heights, 0.50D);
        int p90 = percentile(heights, 0.90D);
        double waterRatio = valid == 0 ? 1.0D : water / (double) valid;
        double steepRatio = edges == 0 ? 0.0D : steep / (double) edges;
        double score = Mth.clamp(1.0D - waterRatio * 0.8D - (p90 - p10) / 36.0D - steepRatio * 0.5D,
                0.0D, 1.0D);
        return new RuralPlan.SiteScore(sampledColumns, valid, vegetation, water, steep, waterRatio,
                p10, median, p90, p90 - p10, steepRatio, score);
    }

    private static RuralPlan.Lot findLot(RuralTerrainSource terrain, StructureTemplate template,
                                         RuralStructurePool.Definition definition, Spec spec,
                                         BoundingBox reservation, List<RuralPlan.Road> roads,
                                         List<RuralPlan.Lot> accepted) {
        int[][] offsets = {{0, 0}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}, {8, 0}, {-8, 0}, {0, 8}, {0, -8}};
        for (int attempt = 0; attempt < offsets.length; attempt++) {
            Rotation rotation = spec.rotation();
            int offsetIndex = attempt;
            int candidateX = spec.x() + offsets[offsetIndex][0];
            int candidateZ = spec.z() + offsets[offsetIndex][1];
            StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(rotation);
            if (!RuralLayoutPlanner.facesRoad(definition, rotation, spec.roadFacing())) {
                continue;
            }
            BoundingBox atGround = template.getBoundingBox(settings, new BlockPos(candidateX, 0, candidateZ));
            if (!inside(atGround, reservation)) continue;
            if (roads.stream().anyMatch(road -> intersects2d(atGround, road.bounds(), RuralGenerator.LOT_MARGIN))) continue;
            if (accepted.stream().anyMatch(lot -> intersects2d(atGround, lot.bounds(), RuralGenerator.LOT_MARGIN))) continue;
            List<Integer> surfaceYs = sampleLot(terrain, atGround);
            if (surfaceYs == null || surfaceYs.isEmpty()) continue;
            int minSurface = surfaceYs.stream().mapToInt(Integer::intValue).min().orElse(Integer.MAX_VALUE);
            int maxSurface = surfaceYs.stream().mapToInt(Integer::intValue).max().orElse(Integer.MIN_VALUE);
            if (maxSurface - minSurface > RuralGenerator.MAX_LOT_RELIEF) continue;
            int desiredY = median(surfaceYs);
            int maxCutDepth = surfaceYs.stream().mapToInt(surface -> Math.max(0, surface - desiredY)).max().orElse(0);
            int maxFillDepth = surfaceYs.stream().mapToInt(surface -> Math.max(0, desiredY - surface)).max().orElse(0);
            if (maxCutDepth > RuralGenerator.MAX_LOT_CORRECTION
                    || maxFillDepth > RuralGenerator.MAX_LOT_CORRECTION) continue;
            int sampleScale = Math.max(1, (atGround.getXSpan() * atGround.getZSpan()) / surfaceYs.size());
            int predictedCut = surfaceYs.stream().mapToInt(surface -> Math.max(0, surface - desiredY)).sum() * sampleScale;
            int predictedFill = surfaceYs.stream().mapToInt(surface -> Math.max(0, desiredY - surface)).sum() * sampleScale;
            RuralPlan.LotClassification classification = maxCutDepth == 0 && maxFillDepth == 0
                    ? RuralPlan.LotClassification.NATURALLY_SUITABLE : RuralPlan.LotClassification.ADAPTABLE;
            BlockPos origin = new BlockPos(candidateX, desiredY - definition.groundAnchorOffsetY(), candidateZ);
            BoundingBox finalBox = template.getBoundingBox(settings, origin);
            if (!inside(finalBox, reservation)) continue;
            ApocalypseFirstLight.LOGGER.debug(
                    "[AFL RURAL NATURAL][LOT_ACCEPT] role={} structure={} groundY={} minSurfaceY={} maxSurfaceY={} relief={} classification={} predictedCut={} predictedFill={} maxCut={} maxFill={} origin={} rotation={}",
                    definition.role(), definition.id(), desiredY, minSurface, maxSurface, maxSurface - minSurface,
                    classification, predictedCut, predictedFill, maxCutDepth, maxFillDepth, origin, rotation);
            return new RuralPlan.Lot(definition, origin, rotation, finalBox, desiredY, spec.roadFacing(),
                    classification, minSurface, maxSurface, predictedCut, predictedFill, maxCutDepth, maxFillDepth);
        }
        return null;
    }

    private static List<Integer> sampleLot(RuralTerrainSource terrain, BoundingBox bounds) {
        List<Integer> result = new ArrayList<>();
        int[] xs = sampleCoordinates(bounds.minX(), bounds.maxX());
        int[] zs = sampleCoordinates(bounds.minZ(), bounds.maxZ());
        for (int x : xs) {
            for (int z : zs) {
                RuralTerrainSampler.Sample sample = terrain.sample(x, z);
                if (!sample.valid() || sample.water()) return null;
                result.add(sample.surfaceY());
            }
        }
        return result;
    }

    private static int[] sampleCoordinates(int min, int max) {
        int span = max - min;
        return new int[]{min, min + span / 4, min + span / 2, min + (span * 3) / 4, max};
    }

    private static int median(List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        return sorted.get(sorted.size() / 2);
    }

    private static int naturalSuitableLots(List<RuralPlan.Lot> lots) {
        return (int) lots.stream().filter(lot -> lot.classification() == RuralPlan.LotClassification.NATURALLY_SUITABLE).count();
    }

    private static int adaptableLots(List<RuralPlan.Lot> lots) {
        return (int) lots.stream().filter(lot -> lot.classification() == RuralPlan.LotClassification.ADAPTABLE).count();
    }

    private static int predictedCut(List<RuralPlan.Lot> lots) {
        return lots.stream().mapToInt(RuralPlan.Lot::predictedCutBlocks).sum();
    }

    private static int predictedFill(List<RuralPlan.Lot> lots) {
        return lots.stream().mapToInt(RuralPlan.Lot::predictedFillBlocks).sum();
    }

    private static int maxCut(List<RuralPlan.Lot> lots) {
        return lots.stream().mapToInt(RuralPlan.Lot::maxCutDepth).max().orElse(0);
    }

    private static int maxFill(List<RuralPlan.Lot> lots) {
        return lots.stream().mapToInt(RuralPlan.Lot::maxFillDepth).max().orElse(0);
    }

    private static List<Spec> assignments(RuralScaleTier tier, long seed, BlockPos center,
                                          List<RuralLayoutPlanner.Candidate> candidates) {
        List<Spec> result = new ArrayList<>();
        int limit = Math.min(candidates.size(), Math.max(tier.maxBuildings() * 3, tier.minBuildings()));
        Set<RuralLayoutPlanner.Candidate> used = new HashSet<>();
        if (tier != RuralScaleTier.ISOLATED_HOMESTEAD) {
            addRequired(result, used, candidates, RuralStructurePool.Role.FARMHOUSE, RuralStructurePool.FARMHOUSE);
        }
        if (tier == RuralScaleTier.FARMSTEAD || tier == RuralScaleTier.RURAL_CLUSTER
                || tier == RuralScaleTier.FULL_RURAL) {
            addRequired(result, used, candidates, RuralStructurePool.Role.AGRICULTURAL_LARGE,
                    RuralStructurePool.BARN);
        }
        for (RuralLayoutPlanner.Candidate candidate : candidates) {
            if (used.contains(candidate)) continue;
            RuralStructurePool.Definition selected = definitionFor(candidate.role(), seed, center, result.size());
            if (selected != null) result.add(spec(selected, candidate));
            if (result.size() >= limit) break;
        }
        return result;
    }

    private static void addRequired(List<Spec> result, Set<RuralLayoutPlanner.Candidate> used,
                                    List<RuralLayoutPlanner.Candidate> candidates,
                                    RuralStructurePool.Role role, RuralStructurePool.Definition definition) {
        candidates.stream().filter(candidate -> candidate.role() == role).findFirst().ifPresent(candidate -> {
            result.add(spec(definition, candidate));
            used.add(candidate);
        });
    }

    private static Spec spec(RuralStructurePool.Definition definition, RuralLayoutPlanner.Candidate candidate) {
        Rotation rotation = candidate.rotationOverride() == null
                ? RuralLayoutPlanner.rotationFor(definition.frontDirection(), candidate.roadFacing())
                : candidate.rotationOverride();
        return new Spec(definition, candidate.anchor().getX(), candidate.anchor().getZ(), rotation,
                candidate.roadFacing());
    }

    private static RuralStructurePool.Definition definitionFor(RuralStructurePool.Role role, long seed,
                                                                 BlockPos center, int index) {
        RuralStructurePool.Role selectedRole = role == RuralStructurePool.Role.FARMHOUSE
                ? RuralStructurePool.Role.RESIDENTIAL : role;
        if (selectedRole == RuralStructurePool.Role.AGRICULTURAL_LARGE) return null;
        List<RuralStructurePool.Definition> matching = RuralStructurePool.definitions().stream()
                .filter(definition -> definition.role() == selectedRole
                        || selectedRole == RuralStructurePool.Role.FLEX && definition.weight() > 0)
                .toList();
        if (matching.isEmpty()) return null;
        return matching.get((int) Math.floorMod(seed ^ center.asLong() ^ index, matching.size()));
    }

    private static List<RuralPlan.Road> branches(BlockPos center, Direction main, RuralScaleTier tier, long seed) {
        if (tier == RuralScaleTier.FULL_RURAL) {
            return List.of(road(center, main.getClockWise(), RuralGenerator.BRANCH_LENGTH,
                    RuralGenerator.ROAD_WIDTH, true));
        }
        if (tier == RuralScaleTier.RURAL_CLUSTER && (seed & 1L) == 0) {
            return List.of(road(center, main.getClockWise(), 24, 3, true));
        }
        return List.of();
    }

    private static RuralPlan.Road road(BlockPos center, Direction direction, int length, int width, boolean branch) {
        if (direction.getAxis() == Direction.Axis.X) {
            return new RuralPlan.Road(direction, new BoundingBox(center.getX() - length / 2, 0,
                    center.getZ() - width / 2, center.getX() + length / 2 - 1, 0,
                    center.getZ() + width / 2), width, branch);
        }
        return new RuralPlan.Road(direction, new BoundingBox(center.getX() - width / 2, 0,
                center.getZ() - length / 2, center.getX() + width / 2, 0,
                center.getZ() + length / 2 - 1), width, branch);
    }

    private static BoundingBox reservation(BlockPos center, int size) {
        int half = size / 2;
        return new BoundingBox(center.getX() - half, 0, center.getZ() - half,
                center.getX() + half - 1, 0, center.getZ() + half - 1);
    }

    private static int reservationSize(RuralScaleTier tier) {
        return switch (tier) {
            case ISOLATED_HOMESTEAD -> 56;
            case FARMSTEAD -> 72;
            case RURAL_CLUSTER -> 96;
            case FULL_RURAL -> RuralGenerator.RESERVATION_SIZE;
        };
    }

    private static Direction siteDirection(long seed, BlockPos center) {
        return switch ((int) Math.floorMod(seed ^ center.asLong() ^ PLAN_SALT, 4L)) {
            case 1 -> Direction.SOUTH;
            case 2 -> Direction.WEST;
            case 3 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    private static double millis(long nanos) { return nanos / 1_000_000.0D; }

    private static boolean hasResidential(List<RuralPlan.Lot> lots) {
        return lots.stream().anyMatch(lot -> lot.structure().role() == RuralStructurePool.Role.RESIDENTIAL
                || lot.structure().role() == RuralStructurePool.Role.FARMHOUSE);
    }

    private static boolean inside(BoundingBox box, BoundingBox outer) {
        return box.minX() >= outer.minX() && box.maxX() <= outer.maxX()
                && box.minZ() >= outer.minZ() && box.maxZ() <= outer.maxZ();
    }

    private static boolean intersects2d(BoundingBox a, BoundingBox b, int margin) {
        return a.minX() - margin <= b.maxX() && a.maxX() + margin >= b.minX()
                && a.minZ() - margin <= b.maxZ() && a.maxZ() + margin >= b.minZ();
    }

    private static int percentile(List<Integer> sorted, double fraction) {
        if (sorted.isEmpty()) return 0;
        return sorted.get(Mth.clamp((int) Math.round((sorted.size() - 1) * fraction), 0, sorted.size() - 1));
    }

    private static Map<RuralPlan.RejectionReason, Integer> emptyRejections() {
        EnumMap<RuralPlan.RejectionReason, Integer> result = new EnumMap<>(RuralPlan.RejectionReason.class);
        for (RuralPlan.RejectionReason reason : RuralPlan.RejectionReason.values()) result.put(reason, 0);
        return result;
    }

    private static final class Spec {
        private final RuralStructurePool.Definition definition;
        private final int x;
        private final int z;
        private final Rotation rotation;
        private final Direction roadFacing;

        private Spec(RuralStructurePool.Definition definition, int x, int z, Rotation rotation, Direction roadFacing) {
            this.definition = definition;
            this.x = x;
            this.z = z;
            this.rotation = rotation;
            this.roadFacing = roadFacing;
        }

        private RuralStructurePool.Definition definition() { return definition; }
        private int x() { return x; }
        private int z() { return z; }
        private Rotation rotation() { return rotation; }
        private Direction roadFacing() { return roadFacing; }
    }
}
