package com.antaurora.apofirstlight.worldgen.lock;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Per-world switches of the gated generation systems (docs/worldgen/worldgen_version_lock_v1.md, "按世界的开关";
 * Main / C3 contract, section 10). A gated system is generation a world has only if it was created with it:
 * <ul>
 *   <li>new world (no record, not initialised): every gated system this build declares is on and recorded;</li>
 *   <li>world with a record: a gated system is on exactly when the record has it (a different version is a HARD
 *   change as usual; a gated system the record has and this build lacks is HARD too);</li>
 *   <li>a gated system the record lacks is off, and that is not a finding: new chunks of an older world never grow
 *   half a road or half a house;</li>
 *   <li>adopted legacy world: every gated system off.</li>
 * </ul>
 * The lock evaluates and records the world's effective fingerprint ({@link #effective}): the running rules without the
 * gated systems the world does not have, and without their probes (a probe of a gated system carries the system's id).
 * The switches are bound to the overworld's RandomState at load (WorldgenGateState), before any chunk generates;
 * generation code asks WorldgenGateState.enabled. Pure Java (the offline lock checks use it). One owner: C (contract section 12, item 5). Main registers start_area by these rules.
 */
public final class WorldgenGates {
    private WorldgenGates() {}

    /** C3: the public road network (routes, pavement, bridges, drainage). */
    public static final String ROAD_NETWORK = "road_network";
    /** C3: the terrain-works layer (road formations and other works applied to the planned ground in the density). */
    public static final String TERRAIN_WORKS = "terrain_works";
    /** Main: the forest-house start (site, reservation, placement). */
    public static final String START_AREA = "start_area";
    /** C: the framework towns' buildings on their lots (TownBuildings, TownBuildingPass; 2026-10-19). */
    public static final String TOWN_BUILDINGS = "town_buildings";
    /** C, N1c: the natural ground made of the AFL blocks (NaturalGroundPass; 2026-10-25). */
    public static final String NATURAL_GROUND = "natural_ground";
    /**
     * C7 of the river plan (docs/gameplay/water_river_v1_design.md 8, 12; R1 2026-10-26): the river channels hold AFL's
     * flowing water (the steady flow field), no vanilla river water; mouths onto the sea and lakes keep theirs.
     */
    public static final String RIVER_FLOW = "river_flow";

    /** Every gated system id, in a fixed order. */
    public static final List<String> GATED = List.of(ROAD_NETWORK, TERRAIN_WORKS, START_AREA, TOWN_BUILDINGS, NATURAL_GROUND, RIVER_FLOW);

    public static boolean gated(String id) { return GATED.contains(id); }

    /**
     * The fingerprint a world is evaluated and recorded with: the running one for a new world; without the gated
     * systems (and their probes) the stored record lacks; without any gated system for a world being adopted.
     */
    public static WorldgenFingerprint effective(WorldgenLockRecord stored, WorldgenFingerprint current, boolean initialized) {
        if (stored == null && !initialized) return current;
        Map<String, String> systems = new TreeMap<>(current.systems()), probes = new TreeMap<>(current.probes());
        for (String id : GATED) {
            boolean keep = stored != null && stored.fingerprint().systems().containsKey(id);
            if (keep) continue;
            systems.remove(id);
            probes.remove(id);
        }
        if (systems.size() == current.systems().size() && probes.size() == current.probes().size()) return current;
        return new WorldgenFingerprint(current.profile(), current.seed(), systems, current.planCacheFormat(), current.planDigest(), probes,
                current.tiles());
    }

    /** The gated systems on in a recorded fingerprint. */
    public static Set<String> on(WorldgenFingerprint recorded) {
        Set<String> out = new LinkedHashSet<>();
        if (recorded != null) for (String id : GATED) if (recorded.systems().containsKey(id)) out.add(id);
        return Set.copyOf(out);
    }
}
