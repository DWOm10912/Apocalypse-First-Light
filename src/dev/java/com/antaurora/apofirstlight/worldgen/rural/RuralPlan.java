package com.antaurora.apofirstlight.worldgen.rural;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.List;

/** Immutable in-memory result. No world mutation happens while a RuralPlan is built. */
public final class RuralPlan {
    private final BlockPos center;
    private final BoundingBox reservation;
    private final SiteScore site;
    private final Road road;
    private final List<Road> branchRoads;
    private final List<Lot> lots;
    private final int targetBuildings;
    private final int candidateLots;
    private final int rejectedLots;
    private final boolean fallbackUsed;
    private final boolean valid;
    private final String failureReason;
    private final Map<RejectionReason, Integer> rejectionCounts;
    private final List<String> barnRejectionDetails;
    private final int farmPlotTarget;
    private final List<RuralFarmPlot> farmPlots;
    private final List<String> farmPlotRejections;

    private RuralPlan(BlockPos center, BoundingBox reservation, SiteScore site, Road road,
                      List<Road> branchRoads, List<Lot> lots, int targetBuildings, int candidateLots,
                      int rejectedLots, boolean fallbackUsed, boolean valid, String failureReason,
                      Map<RejectionReason, Integer> rejectionCounts, List<String> barnRejectionDetails,
                      int farmPlotTarget, List<RuralFarmPlot> farmPlots, List<String> farmPlotRejections) {
        this.center = center;
        this.reservation = reservation;
        this.site = site;
        this.road = road;
        this.branchRoads = List.copyOf(branchRoads);
        this.lots = List.copyOf(lots);
        this.targetBuildings = targetBuildings;
        this.candidateLots = candidateLots;
        this.rejectedLots = rejectedLots;
        this.fallbackUsed = fallbackUsed;
        this.valid = valid;
        this.failureReason = failureReason;
        EnumMap<RejectionReason, Integer> counts = new EnumMap<>(RejectionReason.class);
        for (RejectionReason reason : RejectionReason.values()) {
            counts.put(reason, rejectionCounts.getOrDefault(reason, 0));
        }
        this.rejectionCounts = Map.copyOf(counts);
        this.barnRejectionDetails = List.copyOf(barnRejectionDetails);
        this.farmPlotTarget = farmPlotTarget;
        this.farmPlots = List.copyOf(farmPlots);
        this.farmPlotRejections = List.copyOf(farmPlotRejections);
    }

    public static RuralPlan valid(BlockPos center, BoundingBox reservation, SiteScore site, Road road,
                                  List<Road> branchRoads, List<Lot> lots, int targetBuildings,
                                  int candidateLots, int rejectedLots, boolean fallbackUsed,
                                  Map<RejectionReason, Integer> rejectionCounts,
                                  List<String> barnRejectionDetails) {
        return new RuralPlan(center, reservation, site, road, branchRoads, lots, targetBuildings,
                candidateLots, rejectedLots, fallbackUsed, true, "OK", rejectionCounts, barnRejectionDetails,
                0, List.of(), List.of());
    }

    public static RuralPlan valid(BlockPos center, BoundingBox reservation, SiteScore site, Road road,
                                  List<Road> branchRoads, List<Lot> lots, int targetBuildings,
                                  int candidateLots, int rejectedLots, boolean fallbackUsed,
                                  Map<RejectionReason, Integer> rejectionCounts,
                                  List<String> barnRejectionDetails, int farmPlotTarget,
                                  List<RuralFarmPlot> farmPlots, List<String> farmPlotRejections) {
        return new RuralPlan(center, reservation, site, road, branchRoads, lots, targetBuildings,
                candidateLots, rejectedLots, fallbackUsed, true, "OK", rejectionCounts, barnRejectionDetails,
                farmPlotTarget, farmPlots, farmPlotRejections);
    }

    public static RuralPlan invalid(BlockPos center, BoundingBox reservation, SiteScore site,
                                    Road road, List<Road> branchRoads, int targetBuildings,
                                    List<Lot> lots, int candidateLots, int rejectedLots, String failureReason,
                                    Map<RejectionReason, Integer> rejectionCounts,
                                    List<String> barnRejectionDetails) {
        return new RuralPlan(center, reservation, site, road, branchRoads, lots, targetBuildings,
                candidateLots, rejectedLots, false, false, failureReason, rejectionCounts, barnRejectionDetails,
                0, List.of(), List.of());
    }

    public BlockPos center() { return center; }
    public BoundingBox reservation() { return reservation; }
    public SiteScore site() { return site; }
    public Road road() { return road; }
    public List<Road> branchRoads() { return branchRoads; }
    public List<Road> roads() {
        java.util.ArrayList<Road> result = new java.util.ArrayList<>(1 + branchRoads.size());
        result.add(road);
        result.addAll(branchRoads);
        return List.copyOf(result);
    }
    public List<Lot> lots() { return lots; }
    public int targetBuildings() { return targetBuildings; }
    public int candidateLots() { return candidateLots; }
    public int acceptedLots() { return lots.size(); }
    public int rejectedLots() { return rejectedLots; }
    public boolean fallbackUsed() { return fallbackUsed; }
    public boolean valid() { return valid; }
    public String failureReason() { return failureReason; }
    public Map<RejectionReason, Integer> rejectionCounts() { return rejectionCounts; }
    public List<String> barnRejectionDetails() { return barnRejectionDetails; }
    public int farmPlotTarget() { return farmPlotTarget; }
    public List<RuralFarmPlot> farmPlots() { return farmPlots; }
    public int farmPlotCount() { return farmPlots.size(); }
    public List<String> farmPlotRejections() { return farmPlotRejections; }

    public enum RejectionReason {
        ROLE_MISMATCH,
        TOO_SMALL,
        ROAD_OVERLAP,
        RESERVATION_BOUNDS,
        STRUCTURE_OVERLAP,
        TERRAIN_RELIEF,
        WATER,
        INVALID_GROUND,
        ROTATED_FOOTPRINT,
        OTHER
    }

    public record SiteScore(int sampledColumns, int validGroundSamples, int correctedVegetationSamples,
                            int waterSamples, int steepSamples, double waterRatio,
                            int p10Y, int medianY, int p90Y, int robustRelief,
                            double steepRatio, double score) {
    }

    public record Road(Direction direction, BoundingBox bounds, int width, boolean branch) {
    }

    public record Lot(RuralStructurePool.Definition structure, BlockPos origin, Rotation rotation,
                      BoundingBox bounds, int baseY, Direction roadFacing) {
    }
}
