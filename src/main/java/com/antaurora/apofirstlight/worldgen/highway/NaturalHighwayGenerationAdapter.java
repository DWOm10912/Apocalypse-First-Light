package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tier-0 corridor reject, cached engineering fetch, then target-chunk-owned rendering. */
public final class NaturalHighwayGenerationAdapter {
    private static final int HYGIENE_NEIGHBOR_WRITE_RADIUS = 16;

    private NaturalHighwayGenerationAdapter() {}

    public static boolean generate(WorldGenLevel level, ChunkGenerator generator) {
        if (!(level instanceof WorldGenRegion region)
                || region.getLevel().dimension() != Level.OVERWORLD) return false;

        NaturalHighwayRuntimeStats.FeatureScope feature = NaturalHighwayRuntimeStats.beginFeature();
        try {
            ChunkPos target = region.getCenter();

            // TIER 0: immutable macro graph query; first seed access reconstructs its finite routes.
            // No RandomState, terrain sampler, padded plan, profile or engineering resolver yet.
            long plannerStarted = System.nanoTime();
            HighwayRouteGraph graph = HighwayRouteGraph.forSeed(level.getSeed());
            BoundsXZ area = new BoundsXZ(target.getMinBlockX(), target.getMinBlockZ(),
                    target.getMaxBlockX() + 1, target.getMaxBlockZ() + 1);
            List<HighwayRouteGraph.Edge> routes = graph.query(area, HighwayRouteGraph.FOOTPRINT_HALF_WIDTH);
            // A neighbour's vegetation feature may legally write one chunk into this target.
            // Query that narrow halo before doing any profile/engineering work.
            List<HighwayRouteGraph.Edge> hygieneRoutes = graph.query(
                    area.expand(HYGIENE_NEIGHBOR_WRITE_RADIUS), HighwayRouteGraph.FOOTPRINT_HALF_WIDTH);
            NaturalHighwayRuntimeStats.plannerQuery(System.nanoTime() - plannerStarted,
                    (int) hygieneRoutes.stream().filter(edge -> edge.orientation() == HighwayRouteGraph.Orientation.NORTH_SOUTH).count(),
                    (int) hygieneRoutes.stream().filter(edge -> edge.orientation() == HighwayRouteGraph.Orientation.EAST_WEST).count());
            if (hygieneRoutes.isEmpty()) {
                NaturalHighwayRuntimeStats.hygieneFastReject();
                NaturalHighwayRuntimeStats.finishRejected(feature);
                return false;
            }

            RandomState randomState = region.getLevel().getChunkSource().randomState();
            NaturalHighwayCacheManager.WorldCache cache = NaturalHighwayCacheManager.forLevel(
                    region.getLevel(), generator, randomState);
            HighwayTerrainSampler terrain = new HighwayTerrainSampler(level, generator, randomState, cache);
            Map<String, CorridorEngineeringSegment> segmentById = new LinkedHashMap<>();
            for (HighwayRouteGraph.Edge edge : hygieneRoutes) {
                segmentById.put(edge.id(), segmentForChunk(target, graph, edge, terrain, cache, level));
            }
            List<CorridorEngineeringSegment> segments = new ArrayList<>(routes.size());
            for (HighwayRouteGraph.Edge edge : routes) segments.add(segmentById.get(edge.id()));
            // At a shared crossing, the lower carriageway is authoritative before the overpass.
            segments.sort(Comparator.comparingInt(segment -> segment.upperAtNode() ? 1 : 0));

            Set<String> encounteredNodes = new HashSet<>();
            ChunkOwnedHighwayWriter writer = new ChunkOwnedHighwayWriter(level, target);
            // All intersecting corridors share one actual target-chunk snapshot captured before
            // lower/upper interchange render order is allowed to mutate any block.
            HighwayPreConstructionSnapshot constructionSnapshot = HighwayPreConstructionSnapshot.capture(
                    level, segments.stream().map(CorridorEngineeringSegment::engineeredCorridor).toList(), writer);
            NaturalHighwayRuntimeStats.constructionSnapshot(constructionSnapshot);
            for (CorridorEngineeringSegment segment : segments) {
                for (InterstateInterchangeNode node : segment.nodes()) {
                    if (node.x() + InterstateInterchangeNode.INTERCHANGE_RESERVE_RADIUS < target.getMinBlockX()
                            || node.x() - InterstateInterchangeNode.INTERCHANGE_RESERVE_RADIUS > target.getMaxBlockX()
                            || node.z() + InterstateInterchangeNode.INTERCHANGE_RESERVE_RADIUS < target.getMinBlockZ()
                            || node.z() - InterstateInterchangeNode.INTERCHANGE_RESERVE_RADIUS > target.getMaxBlockZ()) {
                        continue;
                    }
                    if (encounteredNodes.add(node.id())) {
                        NaturalHighwayRuntimeStats.node(node);
                        ApocalypseFirstLight.LOGGER.debug(
                                "[AFL HIGHWAY NETWORK] nodeId={} x={} z={} nsCorridorId={} ewCorridorId={} upperCorridor={} lowerCorridor={}",
                                node.id(), node.x(), node.z(), node.northSouth().id(), node.eastWest().id(),
                                node.upper(), node.lower());
                    }
                }
                long renderStarted = System.nanoTime();
                HighwayRenderStats renderStats = HighwayRenderer.renderNatural(level, segment.profile(),
                        segment.engineeredCorridor(), constructionSnapshot,
                        new FiniteRouteHighwayWriter(writer, segment.corridor()));
                NaturalHighwayRuntimeStats.render(System.nanoTime() - renderStarted);
                NaturalHighwayRuntimeStats.clearance(renderStats);
            }
            // This is deliberately after all target-owned construction.  It may only remove
            // natural obstruction from planned airspace and bounded, triggered tree components.
            HighwayFinalHygienePass.Result hygiene = HighwayFinalHygienePass.run(level, target,
                    segmentById.values().stream().map(CorridorEngineeringSegment::engineeredCorridor).toList());
            NaturalHighwayRuntimeStats.hygiene(hygiene);
            NaturalHighwayRuntimeStats.placement(writer.asphaltSurfaceBlocks(), writer.clearedBlocks(),
                    writer.duplicateAttempts(), writer.illegalWrites());
            NaturalHighwayRuntimeStats.blockWrite(writer.blockWriteNanos());
            NaturalHighwayRuntimeStats.finishAccepted(feature);
            return writer.changedBlocks() > 0 || hygiene.writerWrites() > 0;
        } catch (RuntimeException | Error failure) {
            NaturalHighwayRuntimeStats.finishFailed(feature);
            throw failure;
        }
    }

    private static CorridorEngineeringSegment segmentForChunk(
            ChunkPos target, HighwayRouteGraph graph,
            HighwayRouteGraph.Edge corridor, HighwayTerrainSampler terrain,
            NaturalHighwayCacheManager.WorldCache cache, WorldGenLevel level) {
        long station = corridor.clampStation(corridor.globalStation(target.getMinBlockX(), target.getMinBlockZ()));
        long segmentIndex = CorridorEngineeringSegment.segmentIndex(station);
        NaturalHighwayCacheManager.SegmentKey key = new NaturalHighwayCacheManager.SegmentKey(
                corridor.routeId(), corridor.id(), segmentIndex,
                CorridorEngineeringSegment.ENGINEERING_VERSION);
        return cache.segment(key, () -> CorridorEngineeringSegment.build(level, graph, corridor,
                segmentIndex, terrain, cache));
    }
}
