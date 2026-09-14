package com.antaurora.apofirstlight.worldgen.spatial;

import com.antaurora.apofirstlight.worldgen.core.QueryBudget;
import com.antaurora.apofirstlight.worldgen.core.WorldgenIdentity;

/**
 * Logical read-only query, with no Level, chunk loading, IO or registration side effects.
 * Area is a half-open XZ envelope (no Y restriction). Return every claim whose bounds plus its
 * exclusion margin can affect area, not just claims with an origin/center inside it.
 * For a candidate with a nonzero margin, caller must supply an envelope that also covers that
 * candidate's margin. BoundsXZ.expand fails explicitly on overflow; never wrap or silently shrink
 * such an envelope. A larger envelope may conservatively over-return, but must not omit conflicts.
 * COMPLETE certifies only this provider snapshot and this area, not all systems in a live world.
 */
public interface ClaimQuery {
    ClaimQueryResult query(WorldgenIdentity identity, BoundsXZ area, QueryBudget budget);
}
