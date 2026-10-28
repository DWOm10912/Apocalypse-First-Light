package com.antaurora.apofirstlight.liquid.world;

import com.antaurora.apofirstlight.liquid.LiquidCell;
import com.antaurora.apofirstlight.liquid.LiquidEngine;
import com.antaurora.apofirstlight.worldgen.terrain.v2.HydroQuery;
import com.antaurora.apofirstlight.worldgen.terrain.v2.RiverChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The river plan's steady flow field, R1 (docs/gameplay/water_river_v1_design.md 2): in a world with the
 * {@code river_flow} gate (C7: the channels were left without vanilla water) every river column a chunk brings into the
 * engine becomes river water: held at the river's normal surface eta (HydroQuery, C1), its bed the ground as it is in the
 * game, moving downstream at the Manning velocity of its own depth. Not simulated: nothing flows between river columns,
 * water running in from beside them is taken (the engine's river cells are sinks), so a river costs only its banks' faces
 * while water touches them.
 * <ul>
 *   <li>Velocity (2.1, 2.2): v = (1/n) h^(2/3) S^(1/2), the local wide-channel form (deep water fast, the shallow edge
 *   slow), n {@link #MANNING_N} (C's bed materials later), S the reach's slope; times {@link #velocityScale}; at most
 *   {@link #MAX_SPEED} (the plan's 1.5 m/s limit on the mean).</li>
 *   <li>Direction: the centreline's downstream tangent there.</li>
 *   <li>Columns still holding vanilla water (kept at a mouth onto the sea or a lake) are left to it.</li>
 * </ul>
 * The rest of R1 (white water by Froude number, the river's sound, the compatibility list of 12) comes after.
 */
public final class RiverFlow {
    /** Manning's n of a gravel bed, until C gives the bed's own (2.1). */
    public static final double MANNING_N = 0.035;
    /** The fastest a river's water moves (m/s). */
    public static final double MAX_SPEED = 1.5;
    /** {@code river_velocity_scale} (2.1): every river's speed times this (dev: /afl dev water river scale). */
    public static volatile double velocityScale = 1;

    private RiverFlow() {
    }

    /** The level's river query when its rivers are AFL's (the gate, a Terrain V2 plan with rivers); else null. */
    static HydroQuery hydro(ServerLevel level) {
        return RiverChannels.of(level.getChunkSource().randomState());
    }

    /** The water's velocity at depth h (m) on a reach of the slope (m a metre), m/s. */
    public static double speed(double depth, double slope) {
        if (depth <= 0) return 0;
        return Math.min(MAX_SPEED, velocityScale / MANNING_N * Math.cbrt(depth * depth) * Math.sqrt(Math.max(0, slope)));
    }

    /** The chunk's river columns as river water (when its chunk came into the engine); how many. */
    static int apply(ServerLevel level, LiquidEngine e, HydroQuery h, int cx, int cz) {
        HydroQuery.Point p = new HydroQuery.Point();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int n = 0;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = (cx << 4) + lx, z = (cz << 4) + lz;
                if (!h.river(x + 0.5, z + 0.5, p)) continue;
                // vanilla's water kept at a mouth
                if (!level.getBlockState(pos.set(x, (int) Math.floor(p.eta - 0.5), z)).getFluidState().isEmpty()) continue;
                LiquidCell c = e.riverLayer(x, z, p.eta);
                if (c == null) continue;
                double v = speed(p.eta - c.floor(), p.slope);
                e.setRiver(c, p.eta, p.tx * v, p.tz * v);
                n++;
            }
        }
        return n;
    }
}
