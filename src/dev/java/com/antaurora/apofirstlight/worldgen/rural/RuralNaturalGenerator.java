package com.antaurora.apofirstlight.worldgen.rural;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
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
    private static final int MAX_SITE_WATER_RATIO = RuralGenerator.MAX_SITE_WATER_RATIO;
    private static final int MAX_SITE_RELIEF = RuralGenerator.MAX_SITE_ROBUST_RELIEF;
    private static final double MAX_SITE_STEEP_RATIO = RuralGenerator.MAX_SITE_STEEP_RATIO;
    private static final int CORE_SITE_PROBE_RADIUS = 32;
    private static final long PLAN_SALT = 0x4E41545552414C31L;

    private RuralNaturalGenerator() {
    }

    static BoundingBox reservationFor(long seed, BlockPos center) {
        return reservation(center, reservationSize(RuralScaleTier.choose(seed, center)));
    }

    public static RuralPlan plan(WorldGenRegion level, BlockPos center) {
        return plan(RuralTerrainSampler.source(level), level.getServer().getStructureManager()::get,
                level.getSeed(), center);
    }

    public static RuralPlan plan(Structure.GenerationContext context, BlockPos center) {
        RuralTerrainSource terrain = RuralTerrainSampler.source(context.chunkGenerator(), context.heightAccessor(),
                context.randomState());
        return plan(terrain, context.structureTemplateManager()::get, context.seed(), center);
    }

    private static RuralPlan plan(RuralTerrainSource terrain,
                                  Function<ResourceLocation, Optional<StructureTemplate>> templateSource,
                                  long seed, BlockPos center) {
        long totalStart = System.nanoTime();
        RuralTerrainProbeCache probeCache = new RuralTerrainProbeCache(terrain);
        terrain = probeCache;
        RuralScaleTier tier = RuralScaleTier.choose(seed, center);
        PlanningBudget planningBudget = new PlanningBudget();
        int maxLotEvaluationRequests = maxLotEvaluationRequests(tier);
        int maxRequestsPerSpec = maxRequestsPerSpec(tier);
        int farmTarget = tier.targetFarms(seed, center);
        BoundingBox reservation = reservationFor(seed, center);
        long siteStart = System.nanoTime();
        RuralPlan.SiteScore site = inspectSite(terrain, center);
        long siteInspectNanos = System.nanoTime() - siteStart;
        Direction mainDirection = siteDirection(seed, center);
        List<RuralPlan.Road> networkRoads = RuralRoadPlanner.plan(center, mainDirection, tier, seed);
        RuralPlan.Road mainRoad = networkRoads.get(0);
        List<RuralPlan.Road> branches = networkRoads.subList(1, networkRoads.size());
        int target = tier.targetBuildings(seed, center);
        Map<RuralPlan.RejectionReason, Integer> rejections = emptyRejections();

        LOGGER.debug("[Rural Natural] candidate center={} chunk={} tier={} tierRoll={} targetBuildings={} farmPlotTarget={} siteScore={} waterRatio={} relief={} steepRatio={}",
                center, new net.minecraft.world.level.ChunkPos(center), tier,
                Math.floorMod(seed ^ center.asLong() ^ 0x525552414C534341L, 100), target,
                farmTarget, site.score(), site.waterRatio(), site.robustRelief(), site.steepRatio());

        if (site.sampledColumns() == 0 || site.validGroundSamples() == 0) {
            return reject(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "SITE", "site has no usable terrain samples", rejections, List.of(), 0,
                    farmTarget, List.of(), 0, probeCache, planningBudget, false);
        }
        if (site.waterRatio() * 100.0D > MAX_SITE_WATER_RATIO) {
            return reject(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "SITE", "site water ratio exceeds threshold", rejections, List.of(), 0,
                    farmTarget, List.of(), 0, probeCache, planningBudget, false);
        }
        if (site.robustRelief() > MAX_SITE_RELIEF || site.steepRatio() > MAX_SITE_STEEP_RATIO) {
            return reject(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "SITE", "site relief or steep ratio exceeds threshold", rejections, List.of(), 0,
                    farmTarget, List.of(), 0, probeCache, planningBudget, false);
        }

        long templateStart = System.nanoTime();
        Map<RuralStructurePool.Definition, StructureTemplate> templates = RuralPlanningCore
                .catalog(RuralPlanningCore.SelectionMode.LEGACY_NATURAL_V1, templateSource).templates();
        long templateLookupNanos = System.nanoTime() - templateStart;
        StructureTemplate barnTemplate = templates.get(RuralStructurePool.BARN);
        if (barnTemplate == null) {
            return reject(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "TEMPLATE", "missing structure template " + RuralStructurePool.BARN.id(), rejections,
                    List.of(), 0, farmTarget, List.of(), 0, probeCache, planningBudget, false);
        }
        RuralRoadNetwork roadNetwork = RuralRoadNetwork.from(networkRoads, List.of(), reservation);
        List<RuralFrontagePlanner.Frontage> frontages = RuralFrontagePlanner.frontages(roadNetwork, seed, center);
        List<RuralStructurePool.Definition> specs = assignments(tier, seed, center);
        boolean barnAttempted = specs.contains(RuralStructurePool.BARN);
        long lotStart = System.nanoTime();
        List<RuralPlan.Lot> accepted = new ArrayList<>();
        List<RuralPlan.Road> roads = new ArrayList<>(1 + branches.size());
        roads.add(mainRoad);
        roads.addAll(branches);
        for (RuralStructurePool.Definition spec : specs) {
            RuralPlan.Lot lot = null;
            for (RuralStructurePool.Definition variant : RuralPlanningCore.naturalAlternatives(spec)) {
                StructureTemplate template = templates.get(variant);
                if (template == null) {
                    return reject(center, reservation, site, mainRoad, branches, target, tier, seed,
                            "TEMPLATE", "missing structure template " + variant.id(), rejections,
                            accepted, specs.size(), farmTarget, List.of(), 0, probeCache, planningBudget, barnAttempted);
                }
                lot = findLot(terrain, template, variant, frontages, reservation, roads,
                        accepted, planningBudget, maxLotEvaluationRequests, maxRequestsPerSpec);
                if (lot != null) break;
            }
            if (lot != null) accepted.add(lot);
            if (accepted.size() >= target) break;
        }
        long lotPlanningNanos = System.nanoTime() - lotStart;

        if (accepted.size() < tier.minBuildings()) {
            return reject(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "LOT", "suitable or adaptable lots below tier minimum: " + accepted.size(), rejections,
                    accepted, specs.size(), farmTarget, List.of(), 0, probeCache, planningBudget, barnAttempted);
        }
        if (!hasResidential(accepted)) {
            return reject(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "COMPOSITION", "natural rural has no residential building", rejections, accepted, specs.size(),
                    farmTarget, List.of(), 0, probeCache, planningBudget, barnAttempted);
        }
        if (tier == RuralScaleTier.FULL_RURAL && accepted.stream().noneMatch(lot ->
                lot.structure().role() == RuralStructurePool.Role.RESIDENTIAL)) {
            return reject(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "COMPOSITION", "full rural requires a non-farmhouse residence", rejections, accepted,
                    specs.size(), farmTarget, List.of(), 0, probeCache, planningBudget, barnAttempted);
        }
        if (tier == RuralScaleTier.FARMSTEAD || tier == RuralScaleTier.FULL_RURAL) {
            if (accepted.stream().noneMatch(lot -> lot.structure().role() == RuralStructurePool.Role.AGRICULTURAL_LARGE)) {
                return reject(center, reservation, site, mainRoad, branches, target, tier, seed,
                        "COMPOSITION", "tier requires a barn", rejections, accepted, specs.size(), farmTarget,
                        List.of(), 0, probeCache, planningBudget, barnAttempted);
            }
        }
        if (tier == RuralScaleTier.RURAL_CLUSTER && accepted.stream().noneMatch(lot ->
                lot.structure().role() == RuralStructurePool.Role.AGRICULTURAL_LARGE
                        || lot.structure().role() == RuralStructurePool.Role.AGRICULTURAL_UTILITY)) {
            return reject(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "COMPOSITION", "rural cluster requires an agricultural building", rejections, accepted,
                    specs.size(), farmTarget, List.of(), 0, probeCache, planningBudget, barnAttempted);
        }

        long farmStart = System.nanoTime();
        RuralFarmPlanner.Result farm = RuralFarmlandPlanner.plan(terrain, seed, center, reservation, roads,
                accepted, tier, farmTarget);
        long farmPlanningNanos = System.nanoTime() - farmStart;
        if (farm.count() < tier.minFarms()) {
            return reject(center, reservation, site, mainRoad, branches, target, tier, seed,
                    "FARM", "usable natural farm plots below tier minimum: " + farm.count(), rejections,
                    accepted, specs.size(), farmTarget, farm.plots(), farm.attempts(), probeCache, planningBudget,
                    barnAttempted);
        }
        LOGGER.debug("[Rural Natural] accepted center={} tier={} buildings={}/{} naturalLots={} adaptableLots={} unusableLots={} cut={} fill={} maxCut={} maxFill={} farms={}/{} roadLayout={} reservation={} structures={}",
                center, tier, accepted.size(), target, naturalSuitableLots(accepted), adaptableLots(accepted),
                Math.max(0, specs.size() - accepted.size()), predictedCut(accepted), predictedFill(accepted),
                maxCut(accepted), maxFill(accepted), farm.count(), farmTarget, tier.roadLayout(), reservation,
                accepted.stream().map(lot -> lot.structure().id().toString() + "@" + lot.origin()
                        + "/" + lot.rotation()).toList());
        LOGGER.debug("[AFL RURAL NATURAL][PLAN_TIMING] center={} siteInspectMs={} templateLookupMs={} lotPlanningMs={} farmPlanningMs={} totalPlanMs={}",
                center, millis(siteInspectNanos), millis(templateLookupNanos), millis(lotPlanningNanos),
                millis(farmPlanningNanos), millis(System.nanoTime() - totalStart));
        double totalPlanMs = millis(System.nanoTime() - totalStart);
        if (totalPlanMs > 250.0D) {
            LOGGER.warn("[AFL RURAL NATURAL][SLOW_PLAN] center={} totalPlanMs={} uniqueTerrainProbes={} lotEvaluationRequests={} lotEvaluationsExecuted={} farms={}",
                    center, totalPlanMs, probeCache.uniqueProbes(), planningBudget.lotEvaluationRequests,
                    planningBudget.lotEvaluationsExecuted, farm.count());
        }
        LOGGER.debug("[AFL RURAL NATURAL][PLAN_BUDGET] center={} uniqueTerrainProbes={} terrainCacheHits={} terrainBudgetExceeded={} lotEvaluationRequests={} lotEvaluationsExecuted={} lotEvaluationRequestCap={} lotBudgetExceeded={}",
                center, probeCache.uniqueProbes(), probeCache.cacheHits(), probeCache.budgetExceeded(),
                planningBudget.lotEvaluationRequests, planningBudget.lotEvaluationsExecuted,
                maxLotEvaluationRequests, planningBudget.lotEvaluationBudgetExceeded);
        logTierAttempt(center, tier, seed, "ACCEPT", "ACCEPT", "OK", true, accepted, farm.plots(),
                probeCache, planningBudget, farm.attempts(), barnAttempted);
        return RuralPlan.validNatural(center, reservation, site, mainRoad, branches, accepted, target,
                specs.size(), Math.max(0, specs.size() - accepted.size()), accepted.size() < target,
                rejections, List.of(), farmTarget, farm.plots(), farm.rejections(), tier, seed, tier.roadLayout());
    }

    private static RuralPlan reject(BlockPos center, BoundingBox reservation, RuralPlan.SiteScore site,
                                    RuralPlan.Road road, List<RuralPlan.Road> branches, int target,
                                    RuralScaleTier tier, long seed, String stage, String reason,
                                    Map<RuralPlan.RejectionReason, Integer> rejections, List<RuralPlan.Lot> accepted,
                                    int candidateLots, int farmTarget, List<RuralFarmPlot> farms, int farmAttempts,
                                    RuralTerrainProbeCache probeCache, PlanningBudget planningBudget,
                                    boolean barnAttempted) {
        logTierAttempt(center, tier, seed, "REJECT", stage, reason, !stage.equals("SITE"), accepted, farms,
                probeCache, planningBudget, farmAttempts, barnAttempted);
        return RuralPlan.invalidNatural(center, reservation, site, road, branches, target, accepted,
                candidateLots, Math.max(0, candidateLots - accepted.size()), reason, rejections, List.of(), tier,
                seed, tier.roadLayout(), farmTarget, farms, List.of());
    }

    private static void logTierAttempt(BlockPos center, RuralScaleTier tier, long seed, String result,
                                       String rejectStage, String rejectReason, boolean sitePassed,
                                       List<RuralPlan.Lot> accepted, List<RuralFarmPlot> farms,
                                       RuralTerrainProbeCache probeCache, PlanningBudget planningBudget,
                                       int farmAttempts, boolean barnAttempted) {
        boolean barnAccepted = accepted.stream().anyMatch(lot -> lot.structure() == RuralStructurePool.BARN);
        LOGGER.info("[AFL RURAL NATURAL][TIER_ATTEMPT] center={} tier={} tierRoll={} result={} rejectStage={} rejectReason={} sitePassed={} acceptedBuildings={} acceptedFarms={} terrainProbes={} lotEvaluationRequests={} lotEvaluationsExecuted={} lotEvaluationRequestCap={} farmAttempts={} terrainBudgetExceeded={} lotBudgetExceeded={} barnAttempted={} barnAccepted={}",
                center, tier, Math.floorMod(seed ^ center.asLong() ^ 0x525552414C534341L, 100), result,
                rejectStage, rejectReason, sitePassed, accepted.size(), farms.size(), probeCache.uniqueProbes(),
                planningBudget.lotEvaluationRequests, planningBudget.lotEvaluationsExecuted,
                maxLotEvaluationRequests(tier), farmAttempts, probeCache.budgetExceeded(),
                planningBudget.lotEvaluationBudgetExceeded, barnAttempted, barnAccepted);
    }

    private static RuralPlan.SiteScore inspectSite(RuralTerrainSource terrain, BlockPos center) {
        List<Integer> heights = new ArrayList<>();
        int water = 0;
        int sampledColumns = 0;
        int valid = 0;
        int vegetation = 0;
        boolean centerValid = false;
        boolean centerWater = false;
        int[][] offsets = {{0, 0}, {0, -1}, {0, 1}, {-1, 0}, {1, 0},
                {-1, -1}, {-1, 1}, {1, -1}, {1, 1}, {0, -2}, {0, 2}, {-2, 0}, {2, 0}};
        int radius = CORE_SITE_PROBE_RADIUS;
        int half = Math.max(1, radius / 2);
        for (int i = 0; i < offsets.length; i++) {
            int dx = offsets[i][0] * (i < 9 ? radius : half);
            int dz = offsets[i][1] * (i < 9 ? radius : half);
            sampledColumns++;
            RuralTerrainSampler.Sample sample = terrain.sample(center.getX() + dx, center.getZ() + dz);
            if (i == 0) {
                centerValid = sample.valid();
                centerWater = sample.water();
            }
            if (!sample.valid()) continue;
            valid++;
            vegetation += sample.vegetationBlocksSkipped();
            heights.add(sample.surfaceY());
            if (sample.water()) water++;
        }
        int steep = 0;
        int edges = 0;
        for (int i = 1; i < heights.size(); i++) {
            edges++;
            if (Math.abs(heights.get(i) - heights.get(0)) > 8) steep++;
        }
        heights.sort(Integer::compareTo);
        int p10 = percentile(heights, 0.10D);
        int median = percentile(heights, 0.50D);
        int p90 = percentile(heights, 0.90D);
        double waterRatio = !centerValid || centerWater || valid == 0 ? 1.0D : water / (double) valid;
        double steepRatio = edges == 0 ? 0.0D : steep / (double) edges;
        double score = Mth.clamp(1.0D - waterRatio * 0.8D - (p90 - p10) / 36.0D - steepRatio * 0.5D,
                0.0D, 1.0D);
        return new RuralPlan.SiteScore(sampledColumns, valid, vegetation, water, steep, waterRatio,
                p10, median, p90, p90 - p10, steepRatio, score);
    }

    private static RuralPlan.Lot findLot(RuralTerrainSource terrain, StructureTemplate template,
                                         RuralStructurePool.Definition definition,
                                         List<RuralFrontagePlanner.Frontage> frontages,
                                         BoundingBox reservation, List<RuralPlan.Road> roads,
                                         List<RuralPlan.Lot> accepted, PlanningBudget budget,
                                         int maxLotEvaluationRequests, int maxRequestsPerSpec) {
        int requestsForSpec = 0;
        for (var placement : RuralFrontagePlanner.placements(frontages, template, definition, reservation)) {
            if (requestsForSpec >= maxRequestsPerSpec) return null;
            if (budget.lotEvaluationRequests >= maxLotEvaluationRequests) {
                budget.lotEvaluationBudgetExceeded = true;
                return null;
            }
            requestsForSpec++;
            budget.lotEvaluationRequests++;
            Rotation rotation = placement.rotation();
            int candidateX = placement.origin().getX();
            int candidateZ = placement.origin().getZ();
            StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(rotation);
            BoundingBox atGround = placement.bounds();
            RuralLotAnchor access = RuralAccessPlanner.connect(placement.anchor(), reservation, roads, accepted);
            if (access == null) continue;
            budget.lotEvaluationsExecuted++;
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
            BlockPos origin = new BlockPos(candidateX, desiredY - definition.groundAnchorOffsetY(), candidateZ);
            BoundingBox finalBox = template.getBoundingBox(settings, origin);
            if (!inside(finalBox, reservation)) continue;
            int sampleScale = Math.max(1, (atGround.getXSpan() * atGround.getZSpan()) / surfaceYs.size());
            int predictedCut = surfaceYs.stream().mapToInt(surface -> Math.max(0, surface - desiredY)).sum() * sampleScale;
            int predictedFill = surfaceYs.stream().mapToInt(surface -> Math.max(0, desiredY - surface)).sum() * sampleScale;
            RuralPlan.LotClassification classification = maxCutDepth == 0 && maxFillDepth == 0
                    ? RuralPlan.LotClassification.NATURALLY_SUITABLE : RuralPlan.LotClassification.ADAPTABLE;
            ApocalypseFirstLight.LOGGER.debug(
                    "[AFL RURAL NATURAL][LOT_ACCEPT] role={} structure={} groundY={} minSurfaceY={} maxSurfaceY={} relief={} classification={} predictedCut={} predictedFill={} maxCut={} maxFill={} origin={} rotation={}",
                    definition.role(), definition.id(), desiredY, minSurface, maxSurface, maxSurface - minSurface,
                    classification, predictedCut, predictedFill, maxCutDepth, maxFillDepth,
                    origin, rotation);
            return new RuralPlan.Lot(definition, origin, rotation, finalBox, desiredY, access.facing(),
                    classification, minSurface, maxSurface, predictedCut, predictedFill,
                    maxCutDepth, maxFillDepth).withAccess(access.atOriginY(origin.getY()));
        }
        return null;
    }

    private static List<Integer> sampleLot(RuralTerrainSource terrain, BoundingBox bounds) {
        List<Integer> result = new ArrayList<>();
        int midX = (bounds.minX() + bounds.maxX()) / 2;
        int midZ = (bounds.minZ() + bounds.maxZ()) / 2;
        int[][] points = {{bounds.minX(), bounds.minZ()}, {bounds.minX(), bounds.maxZ()},
                {bounds.maxX(), bounds.minZ()}, {bounds.maxX(), bounds.maxZ()},
                {midX, bounds.minZ()}, {midX, bounds.maxZ()}, {bounds.minX(), midZ},
                {bounds.maxX(), midZ}, {midX, midZ}};
        for (int[] point : points) {
            RuralTerrainSampler.Sample sample = terrain.sample(point[0], point[1]);
            if (!sample.valid() || sample.water()) return null;
            result.add(sample.surfaceY());
        }
        return result;
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

    private static List<RuralStructurePool.Definition> assignments(RuralScaleTier tier, long seed, BlockPos center) {
        List<RuralStructurePool.Definition> result = new ArrayList<>();
        // Preserve the former role-slot order/count and selection policy, without using its coordinates.
        List<RuralStructurePool.Role> roles = new ArrayList<>();
        addRoles(roles, RuralStructurePool.Role.FARMHOUSE, 4);
        addRoles(roles, RuralStructurePool.Role.AGRICULTURAL_LARGE, 2);
        addRoles(roles, RuralStructurePool.Role.AGRICULTURAL_UTILITY, 4);
        addRoles(roles, RuralStructurePool.Role.RESIDENTIAL, 8);
        addRoles(roles, RuralStructurePool.Role.FLEX, 4);
        addRoles(roles, RuralStructurePool.Role.LANDMARK, 2);
        int limit = Math.min(roles.size(), Math.max(tier.maxBuildings() * 3, tier.minBuildings()));
        if (tier != RuralScaleTier.ISOLATED_HOMESTEAD) {
            result.add(RuralPlanningCore.selectRequiredFarmhouse(seed, center));
            roles.remove(RuralStructurePool.Role.FARMHOUSE);
        }
        if (tier == RuralScaleTier.FARMSTEAD || tier == RuralScaleTier.RURAL_CLUSTER
                || tier == RuralScaleTier.FULL_RURAL) {
            result.add(RuralStructurePool.BARN);
            roles.remove(RuralStructurePool.Role.AGRICULTURAL_LARGE);
        }
        for (RuralStructurePool.Role role : roles) {
            RuralStructurePool.Definition selected = RuralPlanningCore.selectNatural(
                    role, seed, center, result.size());
            if (selected != null) result.add(selected);
            if (result.size() >= limit) break;
        }
        return result;
    }

    private static void addRoles(List<RuralStructurePool.Role> roles, RuralStructurePool.Role role, int count) {
        for (int i = 0; i < count; i++) roles.add(role);
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

    private static int maxLotEvaluationRequests(RuralScaleTier tier) {
        return switch (tier) {
            case ISOLATED_HOMESTEAD -> 27;
            case FARMSTEAD -> 63;
            case RURAL_CLUSTER, FULL_RURAL -> (tier.maxBuildings() + 3) * maxRequestsPerSpec(tier);
        };
    }

    private static int maxRequestsPerSpec(RuralScaleTier tier) {
        return switch (tier) {
            case ISOLATED_HOMESTEAD -> 9;
            case FARMSTEAD -> 9;
            case RURAL_CLUSTER, FULL_RURAL -> 18;
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

    private static final class PlanningBudget {
        private int lotEvaluationRequests;
        private int lotEvaluationsExecuted;
        private boolean lotEvaluationBudgetExceeded;
    }
}
