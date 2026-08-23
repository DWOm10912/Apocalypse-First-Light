package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tier-0 corridor reject, cached engineering fetch, then target-chunk-owned rendering. */
public final class NaturalHighwayGenerationAdapter {
    private NaturalHighwayGenerationAdapter() {}

    public static boolean generate(WorldGenLevel level, ChunkGenerator generator) {
        if (!(level instanceof WorldGenRegion region)
                || region.getLevel().dimension() != Level.OVERWORLD) return false;

        NaturalHighwayRuntimeStats.FeatureScope feature = NaturalHighwayRuntimeStats.beginFeature();
        try {
            ChunkPos target = region.getCenter();

            // TIER 0: no RandomState, sampler, padded plan, profile, resolver, or large allocation.
            long plannerStarted = System.nanoTime();
            PrimaryHighwayNetwork network = new PrimaryHighwayNetwork(level.getSeed());
            List<PrimaryHighwayNetwork.Corridor> ns = network.nearby(
                    PrimaryHighwayNetwork.Orientation.PRIMARY_NORTH_SOUTH,
                    target.getMinBlockX(), target.getMaxBlockX(), PrimaryHighwayNetwork.FOOTPRINT_HALF_WIDTH);
            List<PrimaryHighwayNetwork.Corridor> ew = network.nearby(
                    PrimaryHighwayNetwork.Orientation.PRIMARY_EAST_WEST,
                    target.getMinBlockZ(), target.getMaxBlockZ(), PrimaryHighwayNetwork.FOOTPRINT_HALF_WIDTH);
            NaturalHighwayRuntimeStats.plannerQuery(System.nanoTime() - plannerStarted, ns.size(), ew.size());
            if (ns.isEmpty() && ew.isEmpty()) {
                NaturalHighwayRuntimeStats.finishRejected(feature);
                return false;
            }

            RandomState randomState = region.getLevel().getChunkSource().randomState();
            NaturalHighwayCacheManager.WorldCache cache = NaturalHighwayCacheManager.forLevel(
                    region.getLevel(), generator, randomState);
            HighwayTerrainSampler terrain = new HighwayTerrainSampler(level, generator, randomState, cache);
            List<CorridorEngineeringSegment> segments = new ArrayList<>(ns.size() + ew.size());
            for (PrimaryHighwayNetwork.Corridor corridor : ns) {
                segments.add(segmentForChunk(target, network, corridor, terrain, cache, level));
            }
            for (PrimaryHighwayNetwork.Corridor corridor : ew) {
                segments.add(segmentForChunk(target, network, corridor, terrain, cache, level));
            }
            // At a shared crossing, the lower carriageway is authoritative before the overpass.
            segments.sort(Comparator.comparingInt(segment -> segment.upperAtNode() ? 1 : 0));

            Set<String> encounteredNodes = new HashSet<>();
            ChunkOwnedHighwayWriter writer = new ChunkOwnedHighwayWriter(level, target);
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
                HighwayRenderer.renderNatural(level, segment.profile(), segment.engineeredCorridor(), writer);
                NaturalHighwayRuntimeStats.render(System.nanoTime() - renderStarted);
            }
            NaturalHighwayRuntimeStats.placement(writer.asphaltSurfaceBlocks(), writer.clearedBlocks(),
                    writer.duplicateAttempts(), writer.illegalWrites());
            NaturalHighwayRuntimeStats.blockWrite(writer.blockWriteNanos());
            NaturalHighwayRuntimeStats.finishAccepted(feature);
            return writer.changedBlocks() > 0;
        } catch (RuntimeException | Error failure) {
            NaturalHighwayRuntimeStats.finishFailed(feature);
            throw failure;
        }
    }

    private static CorridorEngineeringSegment segmentForChunk(
            ChunkPos target, PrimaryHighwayNetwork network,
            PrimaryHighwayNetwork.Corridor corridor, HighwayTerrainSampler terrain,
            NaturalHighwayCacheManager.WorldCache cache, WorldGenLevel level) {
        long station = corridor.orientation() == PrimaryHighwayNetwork.Orientation.PRIMARY_NORTH_SOUTH
                ? target.getMinBlockZ() : target.getMinBlockX();
        long segmentIndex = CorridorEngineeringSegment.segmentIndex(station);
        NaturalHighwayCacheManager.SegmentKey key = new NaturalHighwayCacheManager.SegmentKey(
                corridor.orientation(), corridor.index(), segmentIndex,
                CorridorEngineeringSegment.ENGINEERING_VERSION);
        return cache.segment(key, () -> CorridorEngineeringSegment.build(level, network, corridor,
                segmentIndex, terrain, cache));
    }
}
