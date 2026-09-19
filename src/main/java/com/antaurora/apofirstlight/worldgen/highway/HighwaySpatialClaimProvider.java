package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.worldgen.spatial.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pure V1 route reservations, independent of terrain, generated chunks and mutable claim indexes. */
public final class HighwaySpatialClaimProvider {
    private static final ResourceLocation OWNER = new ResourceLocation("apocalypse_firstlight", "primary_highway");
    private static final String VERSION = "highway_spatial_v1";
    private static final int CONSTRUCTION_HALF_WIDTH = 32;

    private HighwaySpatialClaimProvider() {}

    public static int constructionEnvelopeHalfWidth() { return CONSTRUCTION_HALF_WIDTH; }

    /**
     * Returns all predicted corridors intersecting the query. Callers expand the query for their
     * safety margin. Claims reserve full columns, conservatively including biome-ineligible portions
     * of a route. Full longitudinal bounds keep claim identity/content independent of query windows.
     * Input coordinates follow the Minecraft world-coordinate domain.
     */
    public static List<SpatialClaim> query(long seed, ResourceKey<Level> dimension, BoundsXZ area) {
        if (!Level.OVERWORLD.equals(dimension) || area.isEmpty()) return List.of();
        PrimaryHighwayNetwork network = new PrimaryHighwayNetwork(seed);
        List<SpatialClaim> claims = new ArrayList<>();
        for (PrimaryHighwayNetwork.Orientation orientation : PrimaryHighwayNetwork.Orientation.values()) {
            boolean ns = orientation == PrimaryHighwayNetwork.Orientation.PRIMARY_NORTH_SOUTH;
            int min = ns ? area.minX() : area.minZ();
            int max = (ns ? area.maxXExclusive() : area.maxZExclusive()) - 1;
            for (PrimaryHighwayNetwork.Corridor corridor : network.nearby(orientation, min, max,
                    constructionEnvelopeHalfWidth())) {
                int low = corridor.fixedCoordinate() - constructionEnvelopeHalfWidth();
                int high = corridor.fixedCoordinate() + constructionEnvelopeHalfWidth() + 1;
                BoundsXZ bounds = ns
                        ? new BoundsXZ(low, Integer.MIN_VALUE, high, Integer.MAX_VALUE)
                        : new BoundsXZ(Integer.MIN_VALUE, low, Integer.MAX_VALUE, high);
                claims.add(new SpatialClaim(DeterministicClaimId.create(OWNER, dimension, VERSION,
                        seed + ":" + corridor.id()), OWNER, dimension, VERSION, bounds, Optional.empty(),
                        SpatialClaimType.INFRASTRUCTURE, SpatialClaimStrength.HARD,
                        ClaimPriorityPolicy.defaultPriorityFor(SpatialClaimType.INFRASTRUCTURE,
                                SpatialClaimStrength.HARD), 0, List.of()));
            }
        }
        return List.copyOf(claims);
    }
}
