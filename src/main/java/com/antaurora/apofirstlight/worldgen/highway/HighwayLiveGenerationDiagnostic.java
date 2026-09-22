package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;

/** Current-code dry replay only. Never invokes a placed feature, renderer, or block writer. */
final class HighwayLiveGenerationDiagnostic {
    private HighwayLiveGenerationDiagnostic() {}

    enum DropPoint {
        FEATURE_NOT_ELIGIBLE, CHUNK_NOT_LOADED, NO_GRAPH_EDGE, SEGMENT_SELECTION,
        ENGINEERING, BUILD_RIBBON, RENDERER, MIXED, NONE, PLANNED_RESERVATION, GRADE_INFEASIBLE
    }

    record EdgeReport(HighwayRouteGraph.Edge edge, long coordinateStation, long selectionStation,
                      long coreStart, long coreEnd, double haloStart, double haloEnd,
                      int surfaceSamples, int viaductSamples, int tunnelSamples, int otherSamples,
                      String firstUnsupported, boolean geometryDeferred, boolean safeSkipTriggered,
                      int corridorCells, int ownedCells, int targetFootprintCells,
                      DropPoint firstDrop, String detail) {}

    record Result(long seed, int x, int z, ChunkPos chunk, BoundsXZ bounds,
                  ResourceLocation dimension, String biome, boolean featureEligible,
                  List<EdgeReport> edges, DropPoint firstDrop, String detail) {}

    static Result diagnose(ServerLevel level, int x, int z) {
        ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
        BoundsXZ bounds = NaturalHighwayGenerationAdapter.chunkBounds(chunk);
        ResourceLocation dimension = level.dimension().location();
        if (level.dimension() != Level.OVERWORLD) {
            return new Result(level.getSeed(), x, z, chunk, bounds, dimension, "not sampled", false,
                    List.of(), DropPoint.FEATURE_NOT_ELIGIBLE, "Highway feature requires Overworld");
        }
        HighwayRouteGraph graph = HighwayRouteGraph.forSeed(level.getSeed());
        // Do not load or generate a chunk merely to answer the biome question.
        if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
            return new Result(level.getSeed(), x, z, chunk, bounds, dimension, "unloaded", false,
                    List.of(), DropPoint.CHUNK_NOT_LOADED, "Visit/load the target chunk before diagnosing");
        }
        BlockPos sample = new BlockPos(x, level.getSeaLevel(), z);
        var biomeHolder = level.getBiome(sample);
        String biome = level.registryAccess().registryOrThrow(Registries.BIOME)
                .getKey(biomeHolder.value()).toString();
        TagKey<Biome> featureBiomes = TagKey.create(Registries.BIOME,
                new ResourceLocation("apocalypse_firstlight", "primary_highway_generation"));
        boolean featureEligible = biomeHolder.is(featureBiomes);
        if (!featureEligible) {
            return new Result(level.getSeed(), x, z, chunk, bounds, dimension, biome, false,
                    List.of(), DropPoint.FEATURE_NOT_ELIGIBLE,
                    "Biome sample at sea level is outside primary_highway_generation tag");
        }

        for(var geometry:SeaBridgeGeometry.query(graph,bounds)) {
            if(!geometry.bounds().contains(x,z))continue;
            var cache=new NaturalHighwayCacheManager.WorldCache();
            var terrain=new HighwayTerrainSampler(level,level.getChunkSource().getGenerator(),level.getChunkSource().randomState(),cache);
            try {
                var bridge=SeaBridgeEngineering.build(level,graph,geometry,terrain,cache);
                long cells=bridge.ready()?bridge.corridor().cells().size():0;
                long owned=bridge.ready()?bridge.corridor().cells().stream().filter(c->bounds.contains(c.x(),c.z())).count():0;
                long ownedLandmark=bridge.landmark().placements().stream()
                        .filter(p->bounds.contains(p.pos().getX(),p.pos().getZ())).count();
                long ownedCable=bridge.cables().cables().stream().flatMap(c->c.placements().stream())
                        .filter(p->bounds.contains(p.pos().getX(),p.pos().getZ())).count();
                long localPiers=geometry.pierStations().stream().filter(s->{
                    var p=geometry.plan().sample(geometry.plan().localDistance(s));
                    return bounds.expand(11).contains((int)Math.round(p.x()),(int)Math.round(p.z()));
                }).count();
                DropPoint drop=!bridge.ready()?DropPoint.GRADE_INFEASIBLE:owned+ownedLandmark+ownedCable==0?DropPoint.RENDERER:DropPoint.NONE;
                return new Result(level.getSeed(),x,z,chunk,bounds,dimension,biome,true,List.of(),drop,
                        "SEA_BRIDGE_V1 crossingId="+geometry.crossing().id()+" generationStatus="+bridge.status()
                        +" deckCells="+cells+" ownedCells="+owned+" pierCount="+bridge.pierCount()
                        +" plannedLocalPiers="+localPiers+" foundationFailures="+bridge.foundationFailures()
                        +" mainlandAbutment="+bridge.abutmentStatus(true)+" satelliteAbutment="+bridge.abutmentStatus(false)
                        +" gradeStart="+(bridge.ready()?bridge.profile().sampleAt(0).roadY():"UNKNOWN")
                        +" gradeEnd="+(bridge.ready()?bridge.profile().sampleAt(geometry.plan().length()).roadY():"UNKNOWN")
                        +" ownedLandmarkCells="+ownedLandmark+" ownedCableCells="+ownedCable+" "+bridge.landmarkDescription()
                        +" wouldRender="+(drop==DropPoint.NONE)+" DRY REPLAY; no blocks written; not historical generation");
            } catch(RuntimeException failure) {
                return new Result(level.getSeed(),x,z,chunk,bounds,dimension,biome,true,List.of(),DropPoint.ENGINEERING,
                        "SEA_BRIDGE_V1 crossingId="+geometry.crossing().id()+" wouldRender=false "+failure.getMessage());
            }
        }
        var modules=HighwayRampModules.forGraph(graph).query(bounds);
        for(var module:modules) {
            if(!module.zone().bounds().contains(x,z) && module.seams().stream().noneMatch(s->s.bounds().contains(x,z)))continue;
            var rampCache=new NaturalHighwayCacheManager.WorldCache();
            var rampTerrain=new HighwayTerrainSampler(level,level.getChunkSource().getGenerator(),level.getChunkSource().randomState(),rampCache);
            try {
                var ramp=HighwayRampEngineering.build(level,graph,module,rampTerrain,rampCache);
                int cells=ramp.ready()?ramp.corridor().cells().size():0;
                long owned=ramp.ready()?ramp.corridor().cells().stream().filter(c->bounds.contains(c.x(),c.z())).count():0;
                DropPoint drop=!ramp.ready()?(ramp.status().equals("GRADE_INFEASIBLE")?DropPoint.GRADE_INFEASIBLE:DropPoint.BUILD_RIBBON)
                        :owned==0?DropPoint.RENDERER:DropPoint.NONE;
                return new Result(level.getSeed(),x,z,chunk,bounds,dimension,biome,true,List.of(),drop,
                        "moduleId="+module.id()+" moduleType="+module.type()+" moduleFound=true moduleCells="+cells
                        +" ownedCells="+owned+" gradeStart="+ramp.grade().startY()+" gradeEnd="+ramp.grade().endY()
                        +" status="+ramp.status()+" wouldRender="+(drop==DropPoint.NONE)+" DRY REPLAY; no blocks written");
            } catch(RuntimeException failure) {
                return new Result(level.getSeed(),x,z,chunk,bounds,dimension,biome,true,List.of(),DropPoint.ENGINEERING,
                        "moduleId="+module.id()+" moduleFound=true wouldRender=false "+failure.getMessage());
            }
        }
        List<HighwayRouteGraph.Edge> edges = NaturalHighwayGenerationAdapter.queryForChunk(graph, chunk);
        if (edges.isEmpty()) {
            return new Result(level.getSeed(), x, z, chunk, bounds, dimension, biome, true,
                    List.of(), DropPoint.NO_GRAPH_EDGE, "No graph edge intersects the target chunk query");
        }

        var generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        // Isolated in-memory cache: same production sampler and segment builder, no world cache changes.
        var cache = new NaturalHighwayCacheManager.WorldCache();
        var terrain = new HighwayTerrainSampler(level, generator, randomState, cache);
        List<EdgeReport> reports = new ArrayList<>(edges.size());
        for (HighwayRouteGraph.Edge edge : edges) {
            long selected = edge.clampStation(edge.globalStation(chunk.getMinBlockX(), chunk.getMinBlockZ()));
            long coordinate = edge.clampStation(edge.globalStation(x, z));
            try {
                CorridorEngineeringSegment segment = NaturalHighwayGenerationAdapter.segmentForChunk(
                        chunk, graph, edge, terrain, cache, level);
                HighwayProfile profile = segment.profile();
                HighwayCorridor corridor = segment.engineeredCorridor();
                int surface = 0, viaduct = 0, tunnel = 0, other = 0;
                String firstUnsupported = "none";
                for (HighwayProfile.Sample profileSample : profile.samples()) {
                    switch (profileSample.mode()) {
                        case GROUND, CUT, FILL -> surface++;
                        case VIADUCT -> viaduct++;
                        case TUNNEL -> tunnel++;
                        default -> other++;
                    }
                    if (edge.geometry() != null && firstUnsupported.equals("none")
                            && profileSample.mode() == HighwayTerrainMode.TUNNEL) {
                        firstUnsupported = unsupported(profile, profileSample);
                    }
                }
                if (edge.geometry() != null && firstUnsupported.equals("none") && HighwayTunnelSpanResolver.mightContainTunnel(
                        profile.samples(), profile::tunnelAllowed)) {
                    var tunnels = HighwayTunnelSpanResolver.resolve(profile.samples(), profile::tunnelAllowed);
                    if (!tunnels.spans().isEmpty()) {
                        var first = profile.sampleAt(tunnels.spans().get(0).startStation());
                        firstUnsupported = "TUNNEL_SPAN@station=" + Math.round(profile.plan().globalStation(first.distance()))
                                + " x=" + Math.round(first.x()) + " z=" + Math.round(first.z());
                    }
                }

                int owned = 0;
                BoundsXZ edgeBounds = edge.bounds(HighwayRouteGraph.CONSTRUCTION_HALF_WIDTH);
                for (HighwayCorridor.Cell cell : corridor.cells()) {
                    if (bounds.contains(cell.x(), cell.z()) && edgeBounds.contains(cell.x(), cell.z())) owned++;
                }
                int targetFootprint = edge.geometry() == null ? -1
                        : edge.geometry().raster(bounds, HighwayGeometry.ROAD_HALF_WIDTH).size();
                boolean deferred = corridor.geometryDeferred();
                boolean safeSkip = deferred && !firstUnsupported.equals("none");
                DropPoint drop = classify(true, true, safeSkip, corridor.cells().size(), owned, targetFootprint);
                String detail = switch (drop) {
                    case BUILD_RIBBON -> "POLYLINE corridor has zero cells; unsupported=" + firstUnsupported;
                    case SEGMENT_SELECTION -> "Target has ribbon footprint but selected segment owns zero cells";
                    case RENDERER -> "No target-chunk road cells; renderer would write no road here";
                    case NONE -> "Preflight has owned road cells; actual writes/old chunk history not verified";
                    default -> "";
                };
                reports.add(new EdgeReport(edge, coordinate, selected, segment.coreStartStation(),
                        segment.coreEndStation(), segment.plan().stationOffset(),
                        segment.plan().globalStation(segment.plan().length()), surface, viaduct, tunnel,
                        other, firstUnsupported, deferred, safeSkip, corridor.cells().size(), owned,
                        targetFootprint, drop, detail));
            } catch (RuntimeException failure) {
                long index = CorridorEngineeringSegment.segmentIndex(selected);
                long start = index * CorridorEngineeringSegment.ENGINEERING_SEGMENT_LENGTH;
                long coreStart = Math.max(edge.startStation(), start);
                long coreEnd = Math.min(edge.endStation(), start + CorridorEngineeringSegment.ENGINEERING_SEGMENT_LENGTH - 1L);
                double haloStart = Math.max(edge.startStation(), start - CorridorEngineeringSegment.ENGINEERING_HALO);
                double haloEnd = Math.min(edge.endStation(), start
                        + CorridorEngineeringSegment.ENGINEERING_SEGMENT_LENGTH - 1L
                        + CorridorEngineeringSegment.ENGINEERING_HALO);
                DropPoint drop = coreStart > coreEnd ? DropPoint.SEGMENT_SELECTION : DropPoint.ENGINEERING;
                reports.add(new EdgeReport(edge, coordinate, selected, coreStart, coreEnd, haloStart, haloEnd,
                        0, 0, 0, 0, "unavailable", false, false, 0, 0, -1,
                        drop, failure.getClass().getSimpleName() + ": " + failure.getMessage()));
            }
        }
        DropPoint overall = reports.stream().allMatch(report -> report.firstDrop() == reports.get(0).firstDrop())
                ? reports.get(0).firstDrop() : DropPoint.MIXED;
        return new Result(level.getSeed(), x, z, chunk, bounds, dimension, biome, true,
                List.copyOf(reports), overall,
                "DRY REPLAY of current code; not a historical chunk-generation log");
    }

    static String reservationAt(HighwayRouteGraph graph,int x,int z) {
        return graph.reservedZones().stream().filter(r->r.bounds().contains(x,z)).map(r->r.status()+" id="+r.id())
                .findFirst().orElse(null);
    }

    private static String unsupported(HighwayProfile profile, HighwayProfile.Sample sample) {
        return sample.mode() + "@station=" + Math.round(profile.plan().globalStation(sample.distance()))
                + " x=" + Math.round(sample.x()) + " z=" + Math.round(sample.z());
    }

    /** Pure first-drop decision for the production-shaped preflight and synthetic contract tests. */
    static DropPoint classify(boolean featureEligible, boolean edgeFound, boolean safeSkip,
                              int cells, int ownedCells, int targetFootprintCells) {
        if (!featureEligible) return DropPoint.FEATURE_NOT_ELIGIBLE;
        if (!edgeFound) return DropPoint.NO_GRAPH_EDGE;
        if (safeSkip) return DropPoint.BUILD_RIBBON;
        if (targetFootprintCells > 0 && ownedCells == 0) return DropPoint.SEGMENT_SELECTION;
        if (cells == 0 || ownedCells == 0) return DropPoint.RENDERER;
        return DropPoint.NONE;
    }
}
