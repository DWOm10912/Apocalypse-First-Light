package com.antaurora.apofirstlight.worldgen.rural;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Map;

/** One chunk-safe replay unit for a deterministic Rural plan. */
public final class RuralNaturalPiece extends StructurePiece {
    private final BlockPos center;
    private transient RuralPlan cachedPlan;
    private transient boolean planningAttempted;
    private transient boolean generationFailed;

    public RuralNaturalPiece(BlockPos center, BoundingBox bounds, RuralPlan plan) {
        super(RuralNaturalWorldgen.RURAL_PIECE.get(), 0, bounds);
        this.center = center;
        this.cachedPlan = plan;
        this.planningAttempted = true;
    }

    public RuralNaturalPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(RuralNaturalWorldgen.RURAL_PIECE.get(), tag);
        this.center = new BlockPos(tag.getInt("CenterX"), tag.getInt("CenterY"), tag.getInt("CenterZ"));
        if (tag.getBoolean("PlanValid")) {
            try {
                this.cachedPlan = readPlan(tag);
                this.planningAttempted = this.cachedPlan != null;
            } catch (RuntimeException exception) {
                ApocalypseFirstLight.LOGGER.warn("[Rural Natural] saved plan could not be restored center={}", center,
                        exception);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CenterX", center.getX());
        tag.putInt("CenterY", center.getY());
        tag.putInt("CenterZ", center.getZ());
        tag.putBoolean("PlanValid", cachedPlan != null && cachedPlan.valid());
        if (cachedPlan != null && cachedPlan.valid()) writePlan(tag, cachedPlan);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                             RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        boolean intersects = getBoundingBox().intersects(chunkBox);
        ApocalypseFirstLight.LOGGER.info(
                "[AFL RURAL NATURAL][POST_PROCESS_ENTER] center={} tier={} currentChunk={} chunkBox={} pieceBounds={} intersects={} planLoaded={} planIdentity={}",
                center, cachedPlan == null ? "unknown" : cachedPlan.scaleTier(), chunkPos, chunkBox,
                getBoundingBox(), intersects, cachedPlan != null, cachedPlan == null ? 0 : System.identityHashCode(cachedPlan));
        if (!(level instanceof WorldGenRegion region)) {
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL RURAL NATURAL][POST_PROCESS_SUMMARY] center={} currentChunk={} blocksAttempted=0 blocksWritten=0 reason=WRONG_WORLD_TYPE_GUARD",
                    center, chunkPos);
            return;
        }
        try {
            if (!planningAttempted) {
                planningAttempted = true;
                ApocalypseFirstLight.LOGGER.warn(
                        "[AFL RURAL NATURAL][PLAN_MISSING] center={} currentChunk={} saved structure piece has no serialized plan; skipping replanning",
                        center, chunkPos);
            }
            RuralPlan plan = cachedPlan;
            if (plan == null) {
                ApocalypseFirstLight.LOGGER.info(
                        "[AFL RURAL NATURAL][POST_PROCESS_SUMMARY] center={} currentChunk={} blocksAttempted=0 blocksWritten=0 reason=PLAN_MISSING",
                        center, chunkPos);
                return;
            }
            if (!plan.valid()) {
                ApocalypseFirstLight.LOGGER.info(
                        "[AFL RURAL NATURAL][POST_PROCESS_SUMMARY] center={} currentChunk={} blocksAttempted=0 blocksWritten=0 reason=PLAN_INVALID failureReason={} tier={}",
                        center, chunkPos, plan.failureReason(), plan.scaleTier());
                return;
            }
            RuralGenerator.NaturalPlacementSummary summary = RuralGenerator.generateNaturalChunk(level, plan, chunkBox);
            String reason = summary.blocksWritten() > 0 ? "OK"
                    : summary.blocksAttempted() == 0 ? "NO_CONTENT_FOR_THIS_CHUNK" : "TEMPLATE_CLIPPED_EMPTY";
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL RURAL NATURAL][POST_PROCESS_SUMMARY] center={} tier={} currentChunk={} buildings={} naturalSuitableLots={} adaptableLots={} unusableLots={} blocksAttempted={} blocksWritten={} roadBlocks={} terrainPrepBlocks={} vegetationCleared={} structureBlocks={} farmBlocks={} cropBlocks={} irrigationBlocks={} totalCutBlocks={} totalFillBlocks={} maxCutDepth={} maxFillDepth={} terrainBlendBlocks={} exposedFillSurfaceBlocks={} reason={}",
                    center, plan.scaleTier(), chunkPos, plan.lots().size(), plan.naturalSuitableLots(),
                    plan.adaptableLots(), plan.unusableLots(), summary.blocksAttempted(), summary.blocksWritten(),
                    summary.roadBlocks(), summary.terrainPrepBlocks(), summary.vegetationCleared(),
                    summary.structureBlocks(), summary.farmBlocks(), summary.cropBlocks(), summary.irrigationBlocks(),
                    summary.totalCutBlocks(), summary.totalFillBlocks(), summary.maxCutDepth(), summary.maxFillDepth(),
                    summary.terrainBlendBlocks(), summary.exposedFillSurfaceBlocks(), reason);
        } catch (Throwable throwable) {
            if (!generationFailed) {
                generationFailed = true;
                ApocalypseFirstLight.LOGGER.error("[Rural Natural] bounded generation failed center={} tier={} chunk={}",
                        center, cachedPlan == null ? "unknown" : cachedPlan.scaleTier(), chunkPos, throwable);
            }
        }
    }

    private static void writePlan(CompoundTag tag, RuralPlan plan) {
        tag.putString("PlanTier", plan.scaleTier().name());
        tag.putLong("PlanSeed", plan.deterministicSeed());
        tag.putString("RoadLayout", plan.roadLayout());
        tag.putInt("TargetBuildings", plan.targetBuildings());
        tag.putInt("CandidateLots", plan.candidateLots());
        tag.putInt("RejectedLots", plan.rejectedLots());
        tag.putBoolean("FallbackUsed", plan.fallbackUsed());
        writeBox(tag, "Reservation", plan.reservation());
        CompoundTag site = new CompoundTag();
        RuralPlan.SiteScore score = plan.site();
        site.putInt("Sampled", score.sampledColumns());
        site.putInt("Valid", score.validGroundSamples());
        site.putInt("Vegetation", score.correctedVegetationSamples());
        site.putInt("Water", score.waterSamples());
        site.putInt("Steep", score.steepSamples());
        site.putDouble("WaterRatio", score.waterRatio());
        site.putInt("P10", score.p10Y());
        site.putInt("Median", score.medianY());
        site.putInt("P90", score.p90Y());
        site.putInt("Relief", score.robustRelief());
        site.putDouble("SteepRatio", score.steepRatio());
        site.putDouble("Score", score.score());
        tag.put("Site", site);
        ListTag roads = new ListTag();
        for (RuralPlan.Road road : plan.roads()) {
            CompoundTag value = new CompoundTag();
            value.putString("Direction", road.direction().getName());
            value.putInt("Width", road.width());
            value.putBoolean("Branch", road.branch());
            writeBox(value, "Bounds", road.bounds());
            roads.add(value);
        }
        tag.put("Roads", roads);
        ListTag lots = new ListTag();
        for (RuralPlan.Lot lot : plan.lots()) {
            CompoundTag value = new CompoundTag();
            value.putString("Structure", lot.structure().id().toString());
            writePos(value, "Origin", lot.origin());
            value.putString("Rotation", lot.rotation().name());
            writeBox(value, "Bounds", lot.bounds());
            value.putInt("BaseY", lot.baseY());
            value.putString("RoadFacing", lot.roadFacing().getName());
            value.putString("Classification", lot.classification().name());
            value.putInt("MinSurfaceY", lot.minSurfaceY());
            value.putInt("MaxSurfaceY", lot.maxSurfaceY());
            value.putInt("PredictedCut", lot.predictedCutBlocks());
            value.putInt("PredictedFill", lot.predictedFillBlocks());
            value.putInt("MaxCut", lot.maxCutDepth());
            value.putInt("MaxFill", lot.maxFillDepth());
            lots.add(value);
        }
        tag.put("Lots", lots);
        tag.putInt("FarmPlotTarget", plan.farmPlotTarget());
        ListTag farms = new ListTag();
        for (RuralFarmPlot plot : plan.farmPlots()) writeFarm(farms, plot);
        tag.put("FarmPlots", farms);
    }

    private static RuralPlan readPlan(CompoundTag tag) {
        RuralScaleTier tier = RuralScaleTier.valueOf(tag.getString("PlanTier"));
        BlockPos planCenter = new BlockPos(tag.getInt("CenterX"), tag.getInt("CenterY"), tag.getInt("CenterZ"));
        BoundingBox reservation = readBox(tag, "Reservation");
        CompoundTag siteTag = tag.getCompound("Site");
        RuralPlan.SiteScore site = new RuralPlan.SiteScore(siteTag.getInt("Sampled"), siteTag.getInt("Valid"),
                siteTag.getInt("Vegetation"), siteTag.getInt("Water"), siteTag.getInt("Steep"),
                siteTag.getDouble("WaterRatio"), siteTag.getInt("P10"), siteTag.getInt("Median"),
                siteTag.getInt("P90"), siteTag.getInt("Relief"), siteTag.getDouble("SteepRatio"),
                siteTag.getDouble("Score"));
        ListTag roadsTag = tag.getList("Roads", 10);
        if (roadsTag.isEmpty()) return null;
        List<RuralPlan.Road> roads = new java.util.ArrayList<>();
        for (int i = 0; i < roadsTag.size(); i++) {
            CompoundTag value = roadsTag.getCompound(i);
            roads.add(new RuralPlan.Road(direction(value.getString("Direction")), readBox(value, "Bounds"),
                    value.getInt("Width"), value.getBoolean("Branch")));
        }
        ListTag lotsTag = tag.getList("Lots", 10);
        List<RuralPlan.Lot> lots = new java.util.ArrayList<>();
        for (int i = 0; i < lotsTag.size(); i++) {
            CompoundTag value = lotsTag.getCompound(i);
            RuralStructurePool.Definition definition = RuralStructurePool.definition(
                    new ResourceLocation(value.getString("Structure")));
            if (definition == null) return null;
            lots.add(new RuralPlan.Lot(definition, readPos(value, "Origin"),
                    Rotation.valueOf(value.getString("Rotation")), readBox(value, "Bounds"),
                    value.getInt("BaseY"), direction(value.getString("RoadFacing")),
                    value.contains("Classification")
                            ? RuralPlan.LotClassification.valueOf(value.getString("Classification"))
                            : RuralPlan.LotClassification.NATURALLY_SUITABLE,
                    value.contains("MinSurfaceY") ? value.getInt("MinSurfaceY") : value.getInt("BaseY"),
                    value.contains("MaxSurfaceY") ? value.getInt("MaxSurfaceY") : value.getInt("BaseY"),
                    value.getInt("PredictedCut"), value.getInt("PredictedFill"),
                    value.getInt("MaxCut"), value.getInt("MaxFill")));
        }
        ListTag farmsTag = tag.getList("FarmPlots", 10);
        List<RuralFarmPlot> farms = new java.util.ArrayList<>();
        for (int i = 0; i < farmsTag.size(); i++) farms.add(readFarm(farmsTag.getCompound(i)));
        List<RuralPlan.Road> branches = roads.subList(1, roads.size());
        return RuralPlan.validNatural(planCenter, reservation, site, roads.get(0), branches, lots,
                tag.getInt("TargetBuildings"), tag.getInt("CandidateLots"), tag.getInt("RejectedLots"),
                tag.getBoolean("FallbackUsed"), new java.util.EnumMap<>(RuralPlan.RejectionReason.class),
                List.of(), tag.getInt("FarmPlotTarget"), farms, List.of(), tier,
                tag.getLong("PlanSeed"), tag.getString("RoadLayout"));
    }

    private static void writeFarm(ListTag farms, RuralFarmPlot plot) {
        CompoundTag value = new CompoundTag();
        value.putInt("Index", plot.index());
        value.putString("Owner", plot.ownerId());
        value.putString("Shape", plot.shape().name());
        writeBox(value, "Bounds", plot.bounds());
        value.putInt("BaseY", plot.baseY());
        value.putString("Crop", plot.crop().name());
        value.putString("IrrigationType", plot.irrigationType().name());
        ListTag cells = new ListTag();
        for (RuralFarmPlot.Cell cell : plot.cells()) {
            CompoundTag cellTag = new CompoundTag();
            cellTag.putInt("X", cell.x()); cellTag.putInt("Z", cell.z()); cellTag.putLong("Key", cell.key());
            cells.add(cellTag);
        }
        value.put("Cells", cells);
        ListTag fences = new ListTag();
        for (RuralFarmPlot.Fence fence : plot.fences()) {
            CompoundTag fenceTag = new CompoundTag();
            writePos(fenceTag, "Pos", fence.pos()); fenceTag.putString("Facing", fence.facing().getName());
            fences.add(fenceTag);
        }
        value.put("Fences", fences);
        ListTag gates = new ListTag();
        for (RuralFarmPlot.Gate gate : plot.gates()) {
            CompoundTag gateTag = new CompoundTag();
            writePos(gateTag, "Pos", gate.pos()); writePos(gateTag, "Inside", gate.insideCell());
            gateTag.putString("Facing", gate.facing().getName()); gates.add(gateTag);
        }
        value.put("Gates", gates);
        writePositions(value, "Irrigation", plot.irrigationCells());
        writePositions(value, "Path", plot.pathCells());
        CompoundTag surfaces = new CompoundTag();
        for (Map.Entry<Long, Integer> entry : plot.surfaceYs().entrySet())
            surfaces.putInt(Long.toString(entry.getKey()), entry.getValue());
        value.put("SurfaceYs", surfaces);
        farms.add(value);
    }

    private static RuralFarmPlot readFarm(CompoundTag value) {
        List<RuralFarmPlot.Cell> cells = new java.util.ArrayList<>();
        ListTag cellTags = value.getList("Cells", 10);
        for (int i = 0; i < cellTags.size(); i++) {
            CompoundTag cell = cellTags.getCompound(i);
            cells.add(new RuralFarmPlot.Cell(cell.getInt("X"), cell.getInt("Z"), cell.getLong("Key")));
        }
        List<RuralFarmPlot.Fence> fences = new java.util.ArrayList<>();
        ListTag fenceTags = value.getList("Fences", 10);
        for (int i = 0; i < fenceTags.size(); i++) {
            CompoundTag fence = fenceTags.getCompound(i);
            fences.add(new RuralFarmPlot.Fence(readPos(fence, "Pos"), direction(fence.getString("Facing"))));
        }
        List<RuralFarmPlot.Gate> gates = new java.util.ArrayList<>();
        ListTag gateTags = value.getList("Gates", 10);
        for (int i = 0; i < gateTags.size(); i++) {
            CompoundTag gate = gateTags.getCompound(i);
            gates.add(new RuralFarmPlot.Gate(readPos(gate, "Pos"), readPos(gate, "Inside"),
                    direction(gate.getString("Facing"))));
        }
        CompoundTag surfacesTag = value.getCompound("SurfaceYs");
        Map<Long, Integer> surfaces = new java.util.HashMap<>();
        for (String key : surfacesTag.getAllKeys()) surfaces.put(Long.parseLong(key), surfacesTag.getInt(key));
        return new RuralFarmPlot(value.getInt("Index"), value.getString("Owner"),
                RuralFarmPlot.ShapeType.valueOf(value.getString("Shape")), readBox(value, "Bounds"),
                value.getInt("BaseY"), RuralFarmPlot.CropType.valueOf(value.getString("Crop")),
                RuralFarmPlot.IrrigationType.valueOf(value.getString("IrrigationType")), cells, fences, gates,
                readPositions(value, "Irrigation"), readPositions(value, "Path"), surfaces, true, "OK");
    }

    private static void writePositions(CompoundTag tag, String key, List<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) { CompoundTag value = new CompoundTag(); writePos(value, "Pos", pos); list.add(value); }
        tag.put(key, list);
    }

    private static List<BlockPos> readPositions(CompoundTag tag, String key) {
        List<BlockPos> result = new java.util.ArrayList<>();
        ListTag list = tag.getList(key, 10);
        for (int i = 0; i < list.size(); i++) result.add(readPos(list.getCompound(i), "Pos"));
        return result;
    }

    private static void writePos(CompoundTag tag, String key, BlockPos pos) {
        CompoundTag value = new CompoundTag();
        value.putInt("X", pos.getX()); value.putInt("Y", pos.getY()); value.putInt("Z", pos.getZ());
        tag.put(key, value);
    }

    private static BlockPos readPos(CompoundTag tag, String key) {
        CompoundTag value = tag.getCompound(key);
        return new BlockPos(value.getInt("X"), value.getInt("Y"), value.getInt("Z"));
    }

    private static void writeBox(CompoundTag tag, String key, BoundingBox box) {
        CompoundTag value = new CompoundTag();
        value.putInt("MinX", box.minX()); value.putInt("MinY", box.minY()); value.putInt("MinZ", box.minZ());
        value.putInt("MaxX", box.maxX()); value.putInt("MaxY", box.maxY()); value.putInt("MaxZ", box.maxZ());
        tag.put(key, value);
    }

    private static BoundingBox readBox(CompoundTag tag, String key) {
        CompoundTag value = tag.getCompound(key);
        return new BoundingBox(value.getInt("MinX"), value.getInt("MinY"), value.getInt("MinZ"),
                value.getInt("MaxX"), value.getInt("MaxY"), value.getInt("MaxZ"));
    }

    private static Direction direction(String name) {
        Direction value = Direction.byName(name);
        return value == null ? Direction.SOUTH : value;
    }
}
