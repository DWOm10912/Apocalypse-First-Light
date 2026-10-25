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
     * 4: U4 entrance shapes (bowls, porches, talus; design 22).
     * 5: rocks on the entrance cuts (boulders and loose stones; design 22.7).
     * 6: great sinkholes as Pit (lobed cliffs in bedding bands on the chamber floor; design 22.8).
     * 7: the entrance rocks look like the rock they came from (Rock R3; design 22.9).
     * 8: the trail from the road to the start cave's mouth, its sign and markers, no trees on it (U5b; design 24.5).
     * 9: the geology's rocks instead of stone and deepslate, no vanilla rock blobs (U3a; underground_u3_rocks_ores_design.md 2.2).
     * 10: soil only in each column's top run (GeologyPass#unroof), no vanilla ores (U6 V3; underground_u6_rock_faces_design.md 2.2).
     * 11: the ore rocks, batch 1: coal seams, banded iron, quartz veins, lead-zinc dolomite, copper breccia (U3b;
     * underground_u3_rocks_ores_design.md 14).
     */
    public static final int RULES = 11;

    /** The bound plans by seed: the mesh forest keeps its trunks off their trails (ForestPlan exclusion; U5b). */
    private static final Map<Long, UndergroundPlan> ACTIVE = new java.util.concurrent.ConcurrentHashMap<>();
    /** The bound plans' geology by seed: GeologyPass lays its rocks (U3a). */
    private static final Map<Long, GeologyModel> GEOLOGY = new java.util.concurrent.ConcurrentHashMap<>();

    /** A bound deep world's geology, or null. */
    public static GeologyModel geology(long seed) { return GEOLOGY.get(seed); }

    static {
        com.antaurora.apofirstlight.tree.ForestPlan.exclude((seed, x, z, radius) -> {
            UndergroundPlan p = ACTIVE.get(seed);
            return p != null && !p.trails.isEmpty() && p.trailNear(x + 0.5, z + 0.5)[0] < CaveTrail.TREE_CLEAR;
        });
    }

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
        GeologyQuery q = query(n);
        UndergroundPlan plan = q.plan;
        GEOLOGY.put(plan.seed, q.geology);
        ref.set(plan);
        ACTIVE.put(plan.seed, plan);
        ApocalypseFirstLight.LOGGER.info("[AFL underground] planned caves bound to this world (rules {}, floor Y {})", RULES, MIN_Y);
    }
}
