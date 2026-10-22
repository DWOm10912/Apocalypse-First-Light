package com.antaurora.apofirstlight.worldgen.underground;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The underground plan of a running world (U2, docs/worldgen/underground_geology_caves_v0_design.md section 20). One plan
 * per road network (the seed's roads, towns and home: the plan keeps clear of them), made once on the server thread
 * (about 1–2 s) and shared by the chunk cut (NoiseChunkUndergroundMixin) and {@code /afl dev underground}. A deep world
 * (preset apocalypse_firstlight:federal_deep_proto) gets it bound at load; any other world only plans it for the
 * read-only command.
 */
public final class UndergroundRuntime {
    private UndergroundRuntime() {}

    /** The floor the plan is made for: the deep preset's min_y (dimension_type/overworld_deep.json). */
    public static final int MIN_Y = -256;
    /**
     * Planned caves in chunks: the planner, the cut and the fluids (version lock system "underground"). Bump when chunks
     * change. 2: caves V2 round 1 (base-level water, shaped sections, wall relief, the start dry cave; design 21).
     * 3: round 2 (systems on the high ground, the states' plan forms and ways in; design 21.10).
     */
    public static final int RULES = 3;

    private static final Map<RoadNetworkRuntime.Network, GeologyQuery> CACHE = new WeakHashMap<>();

    /** The world's plan, made on first use. */
    public static synchronized GeologyQuery query(RoadNetworkRuntime.Network n) {
        GeologyQuery q = CACHE.get(n);
        if (q != null) return q;
        long seed = n.plan().seed, t0 = System.nanoTime();
        GeologyModel g = new GeologyModel(seed, n.plan(), FederalMap.forSeed(seed), MIN_Y);
        GeoProtect pr = new GeoProtect(n.plan(), n.works().roads(), n.works().towns().plans(), n.home());
        q = GeologyQuery.plan(g, pr);
        ApocalypseFirstLight.LOGGER.info("[AFL underground] planned seed {}: {} caves, {} shapes, {} entrances in {} ms", seed,
                q.plan.caves.size(), q.plan.shapes.size(), q.plan.entrances.size(), (System.nanoTime() - t0) / 1_000_000);
        CACHE.put(n, q);
        return q;
    }

    /** True when a plan exists for the network already (the command says whether it planned just now). */
    public static synchronized boolean planned(RoadNetworkRuntime.Network n) { return CACHE.containsKey(n); }

    /**
     * Binds the plan to a deep world's noise state (WorldgenGateState.bind, right after the roads, before any chunk).
     * Nothing for a router without planned caves. Without a road network there is nothing to plan around: the
     * underground then stays solid, and the log says so.
     */
    public static void bind(RandomState state) {
        UndergroundRef ref = ((RandomStateSeedAccess) (Object) state).apocalypse$underground();
        if (ref == null) return;
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(state);
        if (n == null) {
            ref.set(null);
            ApocalypseFirstLight.LOGGER.warn("[AFL underground] deep world without roads, towns and home bound: no caves are cut, the underground stays solid");
            return;
        }
        ref.set(query(n).plan);
        ApocalypseFirstLight.LOGGER.info("[AFL underground] planned caves bound to this world (rules {}, floor Y {})", RULES, MIN_Y);
    }
}
