package com.antaurora.apofirstlight.dev.highway.r4;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.roads.construction.RoadConstructionProtection;
import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-site search in the game (docs/worldgen/highway_r4_sample_v1.md, F step 2): R4SiteSearch on this world's Terrain V2
 * plan, then the five best candidates that are at least one segment length apart and outside AFL road protection (highway
 * claims, a placed bunker). Read-only.
 */
final class R4Site {
    record Found(List<R4SiteSearch.Candidate> picked, R4SiteSearch.Result all, int protectedSkipped) {}

    private R4Site() {}

    static TerrainPlanSurface plan(ServerLevel level) {
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) level.getChunkSource().randomState();
        return access.apocalypse$hasTerrainPlan() ? access.apocalypse$terrainPlanSurface() : null;
    }

    static Found find(ServerLevel level, double cx, double cz, int radius, double length) {
        TerrainPlanSurface p = plan(level);
        if (p == null) throw new IllegalArgumentException("NO_TERRAIN_V2_PLAN in this world; create a new Terrain V2 world");
        var all = R4SiteSearch.search(p, cx, cz, radius, length);
        List<R4SiteSearch.Candidate> picked = new ArrayList<>();
        int skipped = 0;
        for (var c : all.candidates()) {
            if (picked.size() >= 5) break;
            boolean near = false;
            for (var q : picked) if (Math.hypot(q.midX() - c.midX(), q.midZ() - c.midZ()) < length) near = true;
            if (near) continue;
            double m = length / 2 + R4Earthworks.MAX_HALF_WIDTH + 24;
            var area = new BoundsXZ((int) Math.floor(c.midX() - m), (int) Math.floor(c.midZ() - m), (int) Math.ceil(c.midX() + m), (int) Math.ceil(c.midZ() + m));
            if (!RoadConstructionProtection.query(level, area).isEmpty()) { skipped++; continue; }
            picked.add(c);
        }
        return new Found(picked, all, skipped);
    }
}
