package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.spatial.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Finite graph reservations, independent of generated chunks and mutable claim indexes. */
public final class HighwaySpatialClaimProvider {
    private static final ResourceLocation OWNER = new ResourceLocation("apocalypse_firstlight", "primary_highway");
    private static final String VERSION = "highway_spatial_v2_graph_1";
    private static final ResourceLocation DIMENSION_REGISTRY = new ResourceLocation("minecraft", "dimension");
    private static final ResourceLocation OVERWORLD_ID = new ResourceLocation("minecraft", "overworld");
    // Frozen AFL Overworld extent: data/minecraft/worldgen/noise_settings/overworld.json.
    private static final YRange OVERWORLD_HEIGHT = new YRange(-64, 320);

    private HighwaySpatialClaimProvider() {}

    public static int constructionEnvelopeHalfWidth() { return HighwayRouteGraph.CONSTRUCTION_HALF_WIDTH; }

    /**
     * Returns all finite graph edges intersecting the query. Callers expand the query for their
     * safety margin. Claims reserve the finite Overworld height, including biome-ineligible portions
     * of an approved route. Full edge bounds keep claim identity/content independent of query windows.
     * Input coordinates follow the Minecraft world-coordinate domain.
     */
    public static List<SpatialClaim> query(long seed, ResourceKey<Level> dimension, BoundsXZ area) {
        if (!isOverworld(dimension) || area.isEmpty()) return List.of();
        HighwayRouteGraph graph = HighwayRouteGraph.forSeed(seed);
        return query(graph, dimension, area);
    }

    /** Same consumer for build-time synthetic graphs and the published seed graph. */
    public static List<SpatialClaim> query(HighwayRouteGraph graph, ResourceKey<Level> dimension, BoundsXZ area) {
        if (!isOverworld(dimension) || area.isEmpty()) return List.of();
        List<SpatialClaim> claims = new ArrayList<>();
        for (HighwayRouteGraph.Edge edge : graph.query(area, constructionEnvelopeHalfWidth())) {
            BoundsXZ bounds = edge.bounds(constructionEnvelopeHalfWidth());
            claims.add(new SpatialClaim(DeterministicClaimId.create(OWNER, dimension, VERSION,
                    graph.seed() + ":" + edge.routeId() + ":" + edge.id()), OWNER, dimension, VERSION, bounds,
                    Optional.of(OVERWORLD_HEIGHT), SpatialClaimType.INFRASTRUCTURE, SpatialClaimStrength.HARD,
                    ClaimPriorityPolicy.defaultPriorityFor(SpatialClaimType.INFRASTRUCTURE,
                            SpatialClaimStrength.HARD), 0, List.of()));
        }
        for(var zone:graph.reservedZones()) {
            // Seams lie outside the core: a seam-only query must not be rejected by core intersection.
            for (var seam : HighwayReservedSeams.forZone(graph, zone)) if (seam.bounds().intersects(area)) {
                claims.add(new SpatialClaim(DeterministicClaimId.create(OWNER, dimension, VERSION,
                        graph.seed() + ":" + zone.routeId() + ":" + seam.id()), OWNER, dimension, VERSION,
                        seam.bounds(), Optional.of(OVERWORLD_HEIGHT), SpatialClaimType.INFRASTRUCTURE,
                        SpatialClaimStrength.HARD, ClaimPriorityPolicy.defaultPriorityFor(
                        SpatialClaimType.INFRASTRUCTURE, SpatialClaimStrength.HARD), 0, List.of()));
            }
            if (!zone.bounds().intersects(area)) continue;
            claims.add(new SpatialClaim(DeterministicClaimId.create(OWNER,dimension,VERSION,
                    graph.seed()+":"+zone.routeId()+":"+zone.id()),OWNER,dimension,VERSION,zone.bounds(),
                    Optional.of(OVERWORLD_HEIGHT),SpatialClaimType.INFRASTRUCTURE,SpatialClaimStrength.HARD,
                    ClaimPriorityPolicy.defaultPriorityFor(SpatialClaimType.INFRASTRUCTURE,SpatialClaimStrength.HARD),0,List.of()));
        }
        for(var bridge:SeaBridgeGeometry.query(graph,area)) {
            claims.add(new SpatialClaim(DeterministicClaimId.create(OWNER,dimension,"sea_bridge_v1",
                    graph.seed()+":"+bridge.crossing().id()),OWNER,dimension,"sea_bridge_v1",bridge.bounds(),
                    Optional.of(OVERWORLD_HEIGHT),SpatialClaimType.INFRASTRUCTURE,SpatialClaimStrength.HARD,
                    ClaimPriorityPolicy.defaultPriorityFor(SpatialClaimType.INFRASTRUCTURE,SpatialClaimStrength.HARD),0,List.of()));
        }
        return List.copyOf(claims);
    }

    // Same ResourceKey identity as Level.OVERWORLD, without initializing game registries in pure consumers.
    private static boolean isOverworld(ResourceKey<Level> dimension) {
        return dimension != null && dimension.registry().equals(DIMENSION_REGISTRY)
                && dimension.location().equals(OVERWORLD_ID);
    }
}
