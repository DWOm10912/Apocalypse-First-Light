package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.lock.WorldgenGateState;
import com.antaurora.apofirstlight.worldgen.home.ForestHomeLayout;
import com.antaurora.apofirstlight.worldgen.lock.WorldgenGates;
import com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime;
import com.antaurora.apofirstlight.worldgen.network.contract.RotatedRect;
import com.antaurora.apofirstlight.worldgen.network.road.RoadCounty;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * River / lake V1 R2 (docs/worldgen/river_lake_v1_design.md 13): the river query of each world whose rivers are AFL's
 * (the {@code river_flow} gate), bound with the world's road works (RoadNetworkRuntime binds them first), so the wider
 * channel narrows back to the planned width at the world's bridges, culverts, towns, driveway and forest home ({@link #narrow}). Its users: the chunk's channel
 * (NoiseFillRiverMixin -> RiverWaterFill), the flow field (RiverFlow), the carvers (no cave under the wider channel,
 * {@link #carveCeiling}), the mesh forest (no trunk on its banks: a ForestPlan exclusion) and the plants (VegetationPlan:
 * the wider channel is water, {@link #active}).
 */
public final class RiverChannels {
    /** Trees stay this far (m) off the channel's water (ForestPlan.BANK's distance, from the wider edge). */
    public static final double TREE_BANK = com.antaurora.apofirstlight.tree.ForestPlan.BANK;

    private static final Map<Long, HydroQuery> ACTIVE = new ConcurrentHashMap<>();
    private static final ThreadLocal<HydroQuery.Point> POINT = ThreadLocal.withInitial(HydroQuery.Point::new);

    static {
        com.antaurora.apofirstlight.tree.ForestPlan.exclude((seed, x, z, radius) -> {
            HydroQuery h = ACTIVE.get(seed);
            if (h == null) return false;
            HydroQuery.Point p = POINT.get();
            return h.near(x + 0.5, z + 0.5, TREE_BANK, p) && p.edge < TREE_BANK;
        });
    }

    private RiverChannels() {}

    /** Binds the world's query to its noise state (WorldgenGateState, after the road works): only with the gate on. */
    public static void bind(RandomState state, Set<String> on) {
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) state;
        long seed = access.apocalypse$getSeed();
        TerrainPlanSurface plan = access.apocalypse$hasTerrainPlan() ? access.apocalypse$terrainPlanSurface() : null;
        if (!on.contains(WorldgenGates.RIVER_FLOW) || plan == null || plan.rivers == null) {
            ACTIVE.remove(seed);
            return;
        }
        RoadWorks works = access.apocalypse$roadWorksRef().get();
        RoadNetworkRuntime.Network network = access.apocalypse$roadNetwork();
        long t0 = System.nanoTime();
        HydroQuery h = HydroQuery.of(plan.rivers, narrow(works, network == null ? null : network.home()));
        ACTIVE.put(seed, h);
        ApocalypseFirstLight.LOGGER.info("[AFL rivers] river channels for this world ({} vertices, {} road works) in {} ms",
                plan.rivers.vertices(), works == null ? "no" : "with", (System.nanoTime() - t0) / 1_000_000);
    }

    /**
     * Where the wider channel keeps the planned width (HydroQuery.Narrow): the road works' (RoadWorks#holdsRiver: the
     * formations, towns, driveway) and round the forest home's graded ring, so neither the channel nor its graded banks
     * (RiverBed.REACH, past a channel up to BANK_REACH wider) reach the ring. Null: nowhere (no roads in the world).
     */
    public static HydroQuery.Narrow narrow(RoadWorks works, RoadCounty.Home home) {
        if (works == null) return null;
        if (home == null || home.reservation() == null) return works::holdsRiver;
        RotatedRect keep = ForestHomeLayout.plot(home.reservation()).grow(ForestHomeLayout.GRADE_MARGIN);
        double hold = RiverBed.REACH + RiverNetwork.BANK_REACH;
        return (x, z, extra) -> works.holdsRiver(x, z, extra) || keep.distance(x, z) <= extra + hold;
    }

    /** The bound query of the world of this seed (its wider channel), or null: the vegetation's water distance. */
    public static HydroQuery active(long seed) {
        return ACTIVE.get(seed);
    }

    /** The world's river query when its rivers are AFL's; else null. */
    public static HydroQuery of(RandomState state) {
        if (!WorldgenGateState.enabled(state, WorldgenGates.RIVER_FLOW)) return null;
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) state;
        if (!access.apocalypse$hasTerrainPlan()) return null;
        HydroQuery h = ACTIVE.get(access.apocalypse$getSeed());
        if (h != null) return h;
        TerrainPlanSurface plan = access.apocalypse$terrainPlanSurface();
        return plan == null || plan.rivers == null ? null : HydroQuery.of(plan.rivers);
    }

    private static final ThreadLocal<long[]> CEILING_KEY = ThreadLocal.withInitial(() -> new long[256]);
    private static final ThreadLocal<int[]> CEILING = ThreadLocal.withInitial(() -> new int[256]);
    private static final ThreadLocal<HydroQuery[]> CEILING_OWNER = ThreadLocal.withInitial(() -> new HydroQuery[256]);

    /**
     * The lowest Y a carver must leave alone in the column for the wider channel: 8 under the water level of a channel
     * or its graded banks reaching the column (deep holes are at most 3.5 m); Integer.MAX_VALUE elsewhere and in worlds
     * without AFL rivers. Cached per column for the carving thread.
     */
    public static int carveCeiling(RandomState state, int x, int z) {
        HydroQuery h = of(state);
        if (h == null) return Integer.MAX_VALUE;
        int i = (x & 15) | ((z & 15) << 4);
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        long[] keys = CEILING_KEY.get();
        int[] ceiling = CEILING.get();
        HydroQuery[] owner = CEILING_OWNER.get();
        if (owner[i] != h || keys[i] != key) {
            HydroQuery.Point p = POINT.get();
            ceiling[i] = h.near(x + 0.5, z + 0.5, RiverBed.REACH, p) ? (int) Math.floor(p.eta) - 8 : Integer.MAX_VALUE;
            keys[i] = key;
            owner[i] = h;
        }
        return ceiling[i];
    }
}
