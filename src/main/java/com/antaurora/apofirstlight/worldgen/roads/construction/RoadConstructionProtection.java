package com.antaurora.apofirstlight.worldgen.roads.construction;

import com.antaurora.apofirstlight.authoring.BuildingAuthoringCommands;
import com.antaurora.apofirstlight.world.biome.StartupPlainsEnclave;
import com.antaurora.apofirstlight.world.biome.StartupSettlementProtection;
import com.antaurora.apofirstlight.world.bunker.BunkerSavedData;
import com.antaurora.apofirstlight.worldgen.highway.HighwaySpatialClaimProvider;
import com.antaurora.apofirstlight.worldgen.spatial.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Known authoritative exclusions only. Does not certify player/mod block provenance. */
public final class RoadConstructionProtection {
    private static final String VERSION = "roads_construction_protection_1";
    private static final ResourceLocation BUNKER = new ResourceLocation("apocalypse_firstlight", "bunker");
    private RoadConstructionProtection() {}

    public static List<SpatialClaim> query(ServerLevel level, BoundsXZ area) {
        if (!Level.OVERWORLD.equals(level.dimension())) throw new IllegalArgumentException("OVERWORLD_REQUIRED");
        if (area.isEmpty() || area.width() > 1024 || area.depth() > 1024)
            throw new IllegalArgumentException("PROTECTION_QUERY_BUDGET");
        List<SpatialClaim> claims = new ArrayList<>(HighwaySpatialClaimProvider.query(level.getSeed(), level.dimension(), area));
        // 2026-10-10: the startup-protection square round (0, 0) is retired with the startup Plains.
        BunkerSavedData bunker = level.getDataStorage().get(BunkerSavedData::load, BunkerSavedData.ID);
        if (bunker != null && bunker.isGenerated()) {
            var template = level.getServer().getStructureManager().get(BUNKER)
                    .orElseThrow(() -> new IllegalArgumentException("BUNKER_PROTECTION_UNKNOWN"));
            Rotation rotation;
            try { rotation = Rotation.valueOf(bunker.getRotation()); }
            catch (RuntimeException exception) { throw new IllegalArgumentException("BUNKER_ROTATION_UNKNOWN"); }
            var box = template.getBoundingBox(new StructurePlaceSettings().setMirror(Mirror.NONE)
                    .setRotation(rotation), bunker.getOrigin());
            add(level, area, claims, "placed_bunker", new BoundsXZ(box.minX(), box.minZ(), box.maxX()+1, box.maxZ()+1).expand(8));
        }
        if (BuildingAuthoringCommands.overlaps(level.dimension(), new AABB(area.minX(), level.getMinBuildHeight(),
                area.minZ(), area.maxXExclusive(), level.getMaxBuildHeight(), area.maxZExclusive()))) {
            // The public authoring guard exposes overlap, not all session geometry. Reserve the queried region.
            add(level, area, claims, "active_authoring_reservation", area);
        }
        claims.sort(Comparator.comparing(SpatialClaim::id));
        return List.copyOf(claims);
    }

    private static void add(ServerLevel level, BoundsXZ query, List<SpatialClaim> out, String name, BoundsXZ bounds) {
        if (!bounds.intersects(query)) return;
        ResourceLocation owner = new ResourceLocation("apocalypse_firstlight", name);
        out.add(new SpatialClaim(DeterministicClaimId.create(owner,level.dimension(),VERSION,name),owner,
                level.dimension(),VERSION,bounds,Optional.empty(),SpatialClaimType.PROTECTED_SITE,
                SpatialClaimStrength.HARD,ClaimPriorityPolicy.defaultPriorityFor(SpatialClaimType.PROTECTED_SITE,
                SpatialClaimStrength.HARD),0,List.of()));
    }
}
