package com.antaurora.apofirstlight.worldgen.network;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.federal.FederalMap;
import com.antaurora.apofirstlight.worldgen.lock.WorldgenGates;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkBuild;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkJson;
import com.antaurora.apofirstlight.worldgen.network.road.RoadNetworkStage;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import com.antaurora.apofirstlight.worldgen.network.route.RoadNetworkPlan;
import com.antaurora.apofirstlight.worldgen.network.route.RouteTerrain;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanFingerprint;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The road network of a federal world at run time (C3.2; docs/worldgen/federal_highways_c3.md, 10): planned once per
 * seed and plan (RoadNetworkPlan, about 8 s), cached as JSON under {@code <game dir>/afl_cache/road_network/} (named by
 * the plan rules, the seed and the plan digest), and read back into the runtime designs from that JSON on every side.
 * The world lock probes its digest (the road_network probe) and, when the world's gates are on, binds it to the
 * overworld's noise state before the first chunk:
 * <ul>
 *   <li>terrain_works on: the density's works (RoadWorksRef) and the chunk pass's columns;</li>
 *   <li>road_network on: the routes sent to clients (pavement mesh).</li>
 * </ul>
 * No chunk is loaded or generated for this; everything is a pure function of the plan.
 */
public final class RoadNetworkRuntime {
    private RoadNetworkRuntime() {}

    /** The network of one plan: the whole plan JSON, its digest, the built routes' JSON (client packet) and their works. */
    public record Network(String json, String digest, String builtJson, RoadWorks works, TerrainPlanSurface plan) {}

    private static final Map<TerrainPlanSurface, Network> CACHE = new WeakHashMap<>();
    /** Works of the worlds with roads, by seed: the mesh forest keeps its roots off them (ForestPlan exclusion). */
    private static final Map<Long, RoadWorks> ACTIVE = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        com.antaurora.apofirstlight.tree.ForestPlan.exclude((seed, x, z, radius) -> {
            RoadWorks w = ACTIVE.get(seed);
            return w != null && w.clear(x, z, radius);
        });
    }

    /** The network of a federal plan (memory, disk cache or one build). */
    public static synchronized Network get(long seed, TerrainPlanSurface plan, String planDigest) {
        Network n = CACHE.get(plan);
        if (n != null) return n;
        Path file = FMLPaths.GAMEDIR.get().resolve("afl_cache").resolve("road_network").resolve(String.format(Locale.ROOT, "%s_%s_%s.json",
                RoadNetworkPlan.VERSION, Long.toUnsignedString(seed), planDigest.substring(0, Math.min(16, planDigest.length()))));
        FederalMap map = FederalMap.forSeed(seed);
        RouteTerrain terrain = new RouteTerrain(plan, map);
        String json = null;
        try {
            if (Files.isRegularFile(file)) json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            ApocalypseFirstLight.LOGGER.warn("[AFL roads] cannot read the road network cache {}; rebuilding", file, e);
        }
        if (json == null || !json.startsWith("{\"version\":\"" + RoadNetworkPlan.VERSION + "\"")) {
            long t0 = System.nanoTime();
            json = RoadNetworkPlan.build(map, terrain).toJson();
            ApocalypseFirstLight.LOGGER.info("[AFL roads] planned the road network for seed {} in {} ms", seed, (System.nanoTime() - t0) / 1_000_000);
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                ApocalypseFirstLight.LOGGER.warn("[AFL roads] cannot write the road network cache {}", file, e);
            }
        }
        RoadWorks.Water water = (x, z) -> {
            int w = terrain.water(x, z);
            return w == RouteTerrain.RIVER || w == RouteTerrain.LAKE || w == RouteTerrain.SEA;
        };
        // C3.3a: the whole plan, legs through towns joined, ends trimmed (RoadNetworkBuild); the server's works read the
        // same JSON the clients get
        long t1 = System.nanoTime();
        // whatever the seed plans, the world loads: a route the stage cannot build is left out (RoadNetworkBuild), and an
        // error building the network leaves the world without roads (logged), never a crash at world load (2026-10-15)
        String builtJson;
        java.util.List<com.antaurora.apofirstlight.worldgen.network.road.RoadDesign> designs;
        try {
            RoadNetworkBuild.Result b = RoadNetworkBuild.build(json);
            for (String line : b.report()) ApocalypseFirstLight.LOGGER.info("[AFL roads] {}", line);
            if (b.conflicts() > 0) ApocalypseFirstLight.LOGGER.warn("[AFL roads] {} stations where two built roads' works still meet", b.conflicts());
            // group D: the box culverts and the wing walls' reach, decided here from the terrain and carried to the clients
            // (an error there leaves the slab culverts and the full wing walls of C3.3a, logged, never a world without roads)
            String structured;
            try {
                com.antaurora.apofirstlight.worldgen.network.road.RoadStructures.Result st =
                        com.antaurora.apofirstlight.worldgen.network.road.RoadStructures.attach(b.json(), terrain);
                for (String line : st.report()) ApocalypseFirstLight.LOGGER.info("[AFL roads] {}", line);
                structured = st.json();
            } catch (RuntimeException e) {
                ApocalypseFirstLight.LOGGER.error("[AFL roads] cannot design the box culverts and wing walls for seed {}; slab culverts", seed, e);
                structured = b.json();
            }
            ApocalypseFirstLight.LOGGER.info("[AFL roads] built network: {} roads in {} ms", b.routes().size(), (System.nanoTime() - t1) / 1_000_000);
            builtJson = structured;
            designs = RoadNetworkJson.designs(builtJson);
        } catch (RuntimeException e) {
            ApocalypseFirstLight.LOGGER.error("[AFL roads] cannot build the road network for seed {}; this world gets no roads", seed, e);
            builtJson = RoadNetworkJson.write(RoadNetworkPlan.VERSION, RoadNetworkBuild.STAGE, java.util.List.of());
            designs = java.util.List.of();
        }
        n = new Network(json, digest(json), builtJson, new RoadWorks(designs, water), plan);
        CACHE.put(plan, n);
        return n;
    }

    /** First 16 hex digits of the SHA-256 of the JSON (the same digest RoadNetworkPlan.digest gives). */
    static String digest(String json) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < 8; i++) s.append(String.format(Locale.ROOT, "%02x", h[i]));
            return s.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Binds the world's road systems to its noise state (the version lock, right after the verdict, before any chunk):
     * the works and the network when their gates are on, nothing otherwise.
     */
    public static void bind(RandomState state, Set<String> on) {
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) state;
        boolean works = on.contains(WorldgenGates.TERRAIN_WORKS), roads = on.contains(WorldgenGates.ROAD_NETWORK);
        TerrainPlanSurface plan = access.apocalypse$terrainPlanSurface();
        Network n = null;
        if ((works || roads) && plan != null && plan.federal()) {
            synchronized (RoadNetworkRuntime.class) { n = CACHE.get(plan); }   // the lock's probe built it a moment ago
            if (n == null) n = get(access.apocalypse$getSeed(), plan, TerrainPlanFingerprint.of(plan).planDigest());
        }
        // the chunk pass finishes what the density shaped: both or neither (they are declared and recorded together)
        boolean active = n != null && works && roads;
        if (n != null && !active)
            ApocalypseFirstLight.LOGGER.warn("[AFL roads] only one of road_network / terrain_works is on for this world; no road is generated");
        access.apocalypse$roadWorksRef().set(active ? n.works() : null);
        access.apocalypse$setRoadNetwork(active ? n : null);
        if (active) ACTIVE.put(access.apocalypse$getSeed(), n.works());
        else ACTIVE.remove(access.apocalypse$getSeed());
        // road_structure's collision follows the bridge mesh of this network (other dimensions bind nothing: keep it)
        if (active) com.antaurora.apofirstlight.block.RoadStructureBlock.works(n.works());
        if (active) ApocalypseFirstLight.LOGGER.info("[AFL roads] built routes for this world: {}, {} roads ({} m)", RoadNetworkStage.BUILT,
                n.works().roads().size(), String.format(Locale.ROOT, "%.0f", n.works().roads().stream().mapToDouble(d -> d.length).sum()));
    }

    /** The network bound to a noise state, or null (no roads in that world or dimension). */
    public static Network bound(RandomState state) {
        return ((RandomStateSeedAccess) (Object) state).apocalypse$roadNetwork();
    }

    public static Network bound(ServerLevel level) { return bound(level.getChunkSource().randomState()); }
}
