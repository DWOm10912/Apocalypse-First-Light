package com.antaurora.apofirstlight.worldgen.lock;

import com.antaurora.apofirstlight.worldgen.lock.WorldgenLockPolicy.Action;
import com.antaurora.apofirstlight.worldgen.lock.WorldgenLockPolicy.Verdict;
import com.antaurora.apofirstlight.worldgen.lock.WorldgenSystem.Severity;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanFingerprint.Tiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline decision-table test of the world generation version lock (tools/federal-worldgen/run.sh LockPolicyChecks;
 * docs/worldgen/worldgen_version_lock_v1.md). Pure: synthetic fingerprints, no world. What it cannot cover (the NBT file,
 * the Forge event order, the real probes) needs an in-game test in a throwaway world.
 */
public final class LockPolicyChecks {
    static final List<WorldgenSystem> RULES = List.of(
            new WorldgenSystem("national_template", "macro_geography#3", Severity.HARD, "land / sea"),
            new WorldgenSystem("terrain_plan", "afl_terrain_plan_v2_r1#4", Severity.HARD, "plan"),
            new WorldgenSystem("terrain_density", "overworld_density#1", Severity.HARD, "density"),
            new WorldgenSystem("river_carving", "river_carver#1", Severity.HARD, "rivers"),
            new WorldgenSystem("ecology", "ecology_r1#1", Severity.HARD, "ecology"),
            new WorldgenSystem("forest", "mesh_forest#1", Severity.SOFT, "trees"));

    static int failed;

    public static void main(String[] args) {
        WorldgenFingerprint base = fp(RULES, 4, "aaaa", probes("p1", "d1", "b1"), tiles(0, 0, 0));
        WorldgenLockRecord rec = record(base);

        expect("new world, no record", eval(null, base, false, ""), Action.CREATE);
        expect("old world, no record (legacy)", eval(null, base, true, ""), Action.ADOPT_LEGACY);
        expect("same rules, same output", eval(rec, base, true, ""), Action.MATCH);
        Verdict format = eval(rec, fp(RULES, 5, "aaaa", probes("p1", "d1", "b1"), tiles(0, 0, 0)), true, "");
        expect("plan cache format changed, output same", format, Action.MATCH);
        check("  ... reported as a note only", format.notes().stream().anyMatch(n -> n.contains("storage only")));
        expect("trees changed (SOFT)", eval(rec, fp(with(RULES, "forest", "vanilla_trees"), 4, "aaaa", probes("p1", "d1", "b1"), tiles(0, 0, 0)), true, ""),
                Action.UPDATE_SOFT);

        WorldgenFingerprint planBump = fp(with(RULES, "terrain_plan", "afl_terrain_plan_v2_r1#5"), 4, "bbbb", probes("p2", "d2", "b2"), tiles(300, 0, 0));
        Verdict blocked = eval(rec, planBump, true, "");
        expect("declared plan rule change (HARD)", blocked, Action.BLOCK);
        check("  ... probe differences explained by the declared change, not extra findings",
                blocked.findings().size() == 1 && blocked.findings().get(0).system().equals("terrain_plan"));
        expect("  ... wrong token", eval(rec, planBump, true, "deadbeef"), Action.BLOCK);
        expect("  ... this change's token", eval(rec, planBump, true, blocked.token()), Action.ACCEPT_HARD);
        check("  ... the token is bound to this change", !blocked.token().equals(eval(rec, fp(with(RULES, "terrain_plan", "afl_terrain_plan_v2_r1#6"), 4, "cccc",
                probes("p2", "d2", "b2"), tiles(300, 0, 0)), true, "").token()));

        expect("plan values differ at float level only (1 tile, 1 cm)", eval(rec, fp(RULES, 4, "abab", probes("p9", "d1", "b1"), tiles(1, 0, 0)), true, ""),
                Action.UPDATE_SOFT);
        Verdict silent = eval(rec, fp(RULES, 4, "cdcd", probes("p9", "d1", "b1"), tiles(80, 0, 0)), true, "");
        expect("plan values changed, no version bumped", silent, Action.BLOCK);
        check("  ... names the place", silent.findings().stream().anyMatch(f -> f.note().contains("around x")));
        Verdict mod = eval(rec, fp(RULES, 4, "aaaa", probes("p1", "dX", "b1"), tiles(0, 0, 0)), true, "");
        expect("density probe changed, nothing declared (another mod / data pack)", mod, Action.BLOCK);
        check("  ... says why", mod.findings().stream().anyMatch(f -> f.note().contains("another mod")));
        expect("density rule declared changed", eval(rec, fp(with(RULES, "terrain_density", "overworld_density#2"), 4, "aaaa", probes("p1", "dX", "b1"),
                tiles(0, 0, 0)), true, ""), Action.BLOCK);
        expect("other seed", eval(rec, new WorldgenFingerprint("afl_terrain_v2", 7, WorldgenSystem.versions(RULES), 4, "aaaa", probes("p1", "d1", "b1"),
                tiles(0, 0, 0)), true, ""), Action.BLOCK);
        List<WorldgenSystem> plus = new ArrayList<>(RULES);
        plus.add(new WorldgenSystem("roads", "national_roads#1", Severity.HARD, "roads"));
        expect("a new HARD system appears", eval(rec, fp(plus, 4, "aaaa", probes("p1", "d1", "b1"), tiles(0, 0, 0)), true, ""), Action.BLOCK);
        check("unknown system ids count as HARD", WorldgenSystem.severity(RULES, "something_new") == Severity.HARD);

        WorldgenLockRecord adopted = new WorldgenLockRecord(WorldgenLockRecord.SCHEMA, WorldgenLockRecord.ADOPTED, 0, "test", base, List.of());
        check("an adopted world stays LEGACY", adopted.legacy());
        WorldgenLockRecord moved = adopted.withBaseline(planBump, new WorldgenLockRecord.HistoryEntry(1, "accept", "x"));
        check("  ... also after an accepted change", moved.legacy() && moved.fingerprint() == planBump);
        WorldgenLockRecord h = rec;
        for (int k = 0; k < 100; k++) h = h.withBaseline(base, new WorldgenLockRecord.HistoryEntry(k, "soft", "k" + k));
        check("history is bounded and keeps the first entry", h.history().size() <= 64 && h.history().get(0).event().equals("create"));

        gated(base, rec);

        System.out.println(failed == 0 ? "lock policy: all cases PASS" : "lock policy: " + failed + " FAILED");
        if (failed > 0) System.exit(1);
    }

    static Verdict eval(WorldgenLockRecord stored, WorldgenFingerprint current, boolean initialized, String token) {
        return WorldgenLockPolicy.evaluate(stored, current, RULES, initialized, token);
    }

    /** Gated systems (WorldgenGates; the Main / C3 contract, section 10): per-world switches. */
    static void gated(WorldgenFingerprint base, WorldgenLockRecord rec) {
        List<WorldgenSystem> withRoads = new ArrayList<>(RULES);
        withRoads.add(new WorldgenSystem(WorldgenGates.ROAD_NETWORK, "road_network_c3#1", Severity.HARD, "roads"));
        withRoads.add(new WorldgenSystem(WorldgenGates.START_AREA, "forest_home_b#1", Severity.HARD, "start"));
        Map<String, String> pr = probes("p1", "d1", "b1");
        pr.put(WorldgenGates.ROAD_NETWORK, "r1");
        WorldgenFingerprint roads = fp(withRoads, 4, "aaaa", pr, tiles(0, 0, 0));

        Verdict created = WorldgenLockPolicy.evaluate(null, roads, withRoads, false, "");
        expect("gated: new world", created, Action.CREATE);
        check("  ... records the gated systems on", WorldgenGates.on(created.effective()).containsAll(List.of(WorldgenGates.ROAD_NETWORK, WorldgenGates.START_AREA)));
        Verdict adopted = WorldgenLockPolicy.evaluate(null, roads, withRoads, true, "");
        expect("gated: legacy world adopted", adopted, Action.ADOPT_LEGACY);
        check("  ... every gated system off, its probe dropped", WorldgenGates.on(adopted.effective()).isEmpty()
                && !adopted.effective().probes().containsKey(WorldgenGates.ROAD_NETWORK));
        check("  ... and its baseline is the pre-gate fingerprint", adopted.effective().id().equals(base.id()));

        Verdict older = WorldgenLockPolicy.evaluate(rec, roads, withRoads, true, "");
        expect("gated: world recorded before the gated systems", older, Action.MATCH);
        check("  ... they stay off for it, as a note", WorldgenGates.on(older.effective()).isEmpty()
                && older.notes().stream().anyMatch(n -> n.contains("gated systems off")));
        WorldgenFingerprint roadsSoft = fp(with(withRoads, "forest", "vanilla_trees"), 4, "aaaa", pr, tiles(0, 0, 0));
        Verdict soft = WorldgenLockPolicy.evaluate(rec, roadsSoft, withRoads, true, "");
        expect("gated: SOFT change on that world", soft, Action.UPDATE_SOFT);
        check("  ... the new baseline does not switch them on", WorldgenGates.on(soft.effective()).isEmpty());

        WorldgenLockRecord roadRec = record(created.effective());
        expect("gated: world created with them, same build", WorldgenLockPolicy.evaluate(roadRec, roads, withRoads, true, ""), Action.MATCH);
        check("  ... they stay on", WorldgenGates.on(WorldgenLockPolicy.evaluate(roadRec, roads, withRoads, true, "").effective())
                .contains(WorldgenGates.ROAD_NETWORK));
        List<WorldgenSystem> roads2 = with(withRoads, WorldgenGates.ROAD_NETWORK, "road_network_c3#2");
        Map<String, String> pr2 = probes("p1", "d1", "b1");
        pr2.put(WorldgenGates.ROAD_NETWORK, "r2");
        Verdict bump = WorldgenLockPolicy.evaluate(roadRec, fp(roads2, 4, "aaaa", pr2, tiles(0, 0, 0)), roads2, true, "");
        expect("gated: road rules bumped (HARD)", bump, Action.BLOCK);
        check("  ... the road probe is explained by the declared change", bump.findings().size() == 1
                && bump.findings().get(0).system().equals(WorldgenGates.ROAD_NETWORK));
        Verdict silentRoads = WorldgenLockPolicy.evaluate(roadRec, fp(withRoads, 4, "aaaa", pr2, tiles(0, 0, 0)), withRoads, true, "");
        expect("gated: road output changed, nothing declared", silentRoads, Action.BLOCK);
        check("  ... says so", silentRoads.findings().stream().anyMatch(f -> f.system().equals("probe:" + WorldgenGates.ROAD_NETWORK)));
        expect("gated: world with roads opened by a build without them", WorldgenLockPolicy.evaluate(roadRec, base, RULES, true, ""), Action.BLOCK);
    }

    static void expect(String name, Verdict v, Action want) {
        boolean ok = v.action() == want;
        if (!ok) failed++;
        System.out.printf("%-4s %-70s %s%s%n", ok ? "ok" : "FAIL", name, v.action(), ok ? "" : " (want " + want + ")\n" + WorldgenLockPolicy.describe(v));
    }

    static void check(String name, boolean ok) {
        if (!ok) failed++;
        System.out.printf("%-4s %s%n", ok ? "ok" : "FAIL", name);
    }

    static WorldgenFingerprint fp(List<WorldgenSystem> rules, int format, String plan, Map<String, String> probes, Tiles tiles) {
        return new WorldgenFingerprint("afl_terrain_v2", 42, WorldgenSystem.versions(rules), format, plan, probes, tiles);
    }

    static WorldgenLockRecord record(WorldgenFingerprint f) {
        return new WorldgenLockRecord(WorldgenLockRecord.SCHEMA, WorldgenLockRecord.CREATED, 0, "test", f,
                List.of(new WorldgenLockRecord.HistoryEntry(0, "create", "")));
    }

    static Map<String, String> probes(String plan, String density, String biomes) {
        Map<String, String> m = new HashMap<>();
        m.put(WorldgenFingerprint.PROBE_PLAN, plan);
        m.put(WorldgenFingerprint.PROBE_DENSITY, density);
        m.put(WorldgenFingerprint.PROBE_BIOMES, biomes);
        return m;
    }

    static List<WorldgenSystem> with(List<WorldgenSystem> rules, String id, String version) {
        List<WorldgenSystem> out = new ArrayList<>();
        for (WorldgenSystem s : rules) out.add(s.id().equals(id) ? new WorldgenSystem(id, version, s.severity(), s.meaning()) : s);
        return out;
    }

    /** 44 x 44 tiles (the r1 grid); the first {@code changed} tiles moved by 1 cm (noise) or, past 1 %, by 30 cm. */
    static Tiles tiles(int changed, int wet, int river) {
        int n = 44 * 44;
        int[] mean = new int[n], w = new int[n], r = new int[n], z = new int[n];
        for (int t = 0; t < n; t++) mean[t] = 7000 + t;
        for (int t = 0; t < Math.min(changed, n); t++) mean[t] += changed <= 1 ? 1 : 30;
        w[0] = wet;
        r[0] = river;
        return new Tiles(44, 44, mean, w, r, z);
    }
}
