package com.antaurora.apofirstlight.worldgen.rural;

import com.antaurora.apofirstlight.worldgen.highway.HighwaySpatialClaimProvider;
import com.antaurora.apofirstlight.worldgen.spatial.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.Optional;

/** Fixed natural Overworld V1 policy; no mutable registration order or persisted acceptance state. */
final class RuralHighwayConflict {
    static final int SAFETY_BUFFER = 12;
    private static final ResourceLocation OWNER = new ResourceLocation("apocalypse_firstlight", "rural");
    private static final String VERSION = "rural_highway_conflict_v1";

    private RuralHighwayConflict() {}

    static Optional<SpatialClaim> blocker(long seed, BoundingBox reservation) {
        BoundsXZ bounds = new BoundsXZ(reservation.minX(), reservation.minZ(),
                reservation.maxX() + 1, reservation.maxZ() + 1);
        SpatialClaim rural = new SpatialClaim(DeterministicClaimId.create(OWNER, Level.OVERWORLD,
                VERSION, seed + ":" + bounds.minX() + ":" + bounds.minZ()), OWNER, Level.OVERWORLD,
                VERSION, bounds, Optional.empty(), SpatialClaimType.SITE, SpatialClaimStrength.HARD,
                ClaimPriorityPolicy.defaultPriorityFor(SpatialClaimType.SITE, SpatialClaimStrength.HARD),
                SAFETY_BUFFER, List.of());
        // These two code-owned V1 producers form a fixed policy, not the profile-coordination pipeline.
        for (SpatialClaim highway : HighwaySpatialClaimProvider.query(seed, Level.OVERWORLD,
                bounds.expand(SAFETY_BUFFER))) {
            ClaimConflict conflict = ClaimConflictResolver.resolveWinner(highway, rural);
            if (conflict.kind() == ClaimConflict.Kind.BLOCKING
                    && conflict.winner().filter(highway::equals).isPresent()) return Optional.of(highway);
        }
        return Optional.empty();
    }
}
