package com.antaurora.apofirstlight.worldgen.lock;

import com.antaurora.apofirstlight.worldgen.lock.WorldgenSystem.Severity;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanFingerprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Decides what a world load does with the generation version lock (docs/worldgen/worldgen_version_lock_v1.md). Pure:
 * no world, no IO; WorldgenLockEvents applies the verdict.
 * <ul>
 *   <li>no record, world not initialised yet: {@link Action#CREATE} (a new world, before its first chunk);</li>
 *   <li>no record, world already initialised: {@link Action#ADOPT_LEGACY} (a world from before the lock; nothing about
 *   its old chunks is known, the running rules become its baseline from now on);</li>
 *   <li>same output: {@link Action#MATCH} (a different plan cache format or label alone is not a change);</li>
 *   <li>only SOFT changes (trees, or plan values that differ at floating-point level only):
 *   {@link Action#UPDATE_SOFT}, logged and added to the record's history;</li>
 *   <li>any HARD change: {@link Action#BLOCK}, the world is not opened and nothing is written, unless the world's
 *   server config carries this exact change's token: {@link Action#ACCEPT_HARD} (recorded in the history).</li>
 * </ul>
 */
public final class WorldgenLockPolicy {
    private WorldgenLockPolicy() {}

    public enum Action { CREATE, ADOPT_LEGACY, MATCH, UPDATE_SOFT, ACCEPT_HARD, BLOCK }

    public record Finding(String system, Severity severity, String stored, String current, String note) {}

    /** notes: informational lines (e.g. a plan cache format change) that do not count as findings. */
    public record Verdict(Action action, List<Finding> findings, List<String> notes, String token) {
        public boolean hard() { return findings.stream().anyMatch(f -> f.severity() == Severity.HARD); }
    }

    public static Verdict evaluate(WorldgenLockRecord stored, WorldgenFingerprint current, List<WorldgenSystem> rules,
                                   boolean worldInitialized, String acceptToken) {
        Objects.requireNonNull(current);
        if (stored == null)
            return new Verdict(worldInitialized ? Action.ADOPT_LEGACY : Action.CREATE, List.of(), List.of(), "");
        WorldgenFingerprint was = stored.fingerprint();
        List<Finding> findings = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (stored.schema() > WorldgenLockRecord.SCHEMA)
            notes.add("the lock record was written by a newer AFL build (schema " + stored.schema() + ")");
        if (was.seed() != current.seed())
            findings.add(new Finding("seed", Severity.HARD, Long.toString(was.seed()), Long.toString(current.seed()),
                    "the world's seed is not the one its record was made for"));
        if (!was.profile().equals(current.profile()))
            findings.add(new Finding("profile", Severity.HARD, was.profile(), current.profile(), "another world generation profile"));
        TreeSet<String> ids = new TreeSet<>(was.systems().keySet());
        ids.addAll(current.systems().keySet());
        for (String id : ids) {
            String a = was.systems().get(id), b = current.systems().get(id);
            if (Objects.equals(a, b)) continue;
            findings.add(new Finding(id, WorldgenSystem.severity(rules, id), a == null ? "(none)" : a, b == null ? "(none)" : b,
                    WorldgenSystem.meaning(rules, id)));
        }
        boolean planDeclared = changed(was, current, "terrain_plan") || changed(was, current, "national_template");
        boolean noise = false;
        if (!was.planDigest().equals(current.planDigest())) {
            TerrainPlanFingerprint.TileComparison cmp = TerrainPlanFingerprint.compare(was.tiles(), current.tiles());
            String where = where(cmp);
            if (planDeclared) {
                notes.add("plan values differ, as the declared terrain_plan / national_template change says" + where);
            } else if (cmp.noiseOnly(current.tiles() == null ? 0 : current.tiles().size())) {
                noise = true;
                findings.add(new Finding("plan_values", Severity.SOFT, was.planDigest(), current.planDigest(),
                        "plan values differ at floating-point level only (" + cmp.changedTiles() + " tiles, at most "
                                + String.format(java.util.Locale.ROOT, "%.2f", cmp.maxHeightShift()) + " m mean height): "
                                + "another CPU architecture or JVM math library built this world's plan"));
            } else {
                findings.add(new Finding("plan_values", Severity.HARD, was.planDigest(), current.planDigest(),
                        "the plan's heights / water / rivers / ecology changed although no rule version changed"
                                + (cmp.comparable() ? " (" + cmp.changedTiles() + " tiles of 512 m, up to "
                                + String.format(java.util.Locale.ROOT, "%.2f", cmp.maxHeightShift()) + " m mean height)" : "") + where));
            }
        }
        TreeSet<String> probes = new TreeSet<>(was.probes().keySet());
        probes.addAll(current.probes().keySet());
        for (String p : probes) {
            String a = was.probes().get(p), b = current.probes().get(p);
            if (Objects.equals(a, b)) continue;
            if (a == null || b == null) {
                notes.add("probe " + p + " is only on one side (" + (a == null ? "new" : "retired") + ")");
                continue;
            }
            if (explained(was, current, p)) {
                notes.add("probe " + p + " differs, as the declared changes say");
            } else if (noise) {
                findings.add(new Finding("probe:" + p, Severity.SOFT, a, b, "follows the floating-point plan difference"));
            } else {
                findings.add(new Finding("probe:" + p, Severity.HARD, a, b, switch (p) {
                    case WorldgenFingerprint.PROBE_DENSITY -> "the chunk terrain density changed without a declared version change "
                            + "(another mod or data pack replacing overworld noise settings, or an undeclared AFL change)";
                    case WorldgenFingerprint.PROBE_BIOMES -> "the overworld biomes changed without a declared version change "
                            + "(another biome mod or data pack, or an undeclared AFL change)";
                    default -> "the plan's runtime queries changed without a declared version change";
                }));
            }
        }
        if (was.planCacheFormat() != current.planCacheFormat())
            notes.add("plan cache format " + was.planCacheFormat() + " -> " + current.planCacheFormat() + " (storage only, not a change)");
        String token = token(was, current);
        Action action;
        if (findings.isEmpty()) action = Action.MATCH;
        else if (findings.stream().noneMatch(f -> f.severity() == Severity.HARD)) action = Action.UPDATE_SOFT;
        else action = token.equals(acceptToken == null ? "" : acceptToken.trim()) ? Action.ACCEPT_HARD : Action.BLOCK;
        return new Verdict(action, List.copyOf(findings), List.copyOf(notes), token);
    }

    private static boolean changed(WorldgenFingerprint a, WorldgenFingerprint b, String id) {
        return !Objects.equals(a.systems().get(id), b.systems().get(id));
    }

    /** A probe difference is expected when a declared system it reads from changed. */
    private static boolean explained(WorldgenFingerprint a, WorldgenFingerprint b, String probe) {
        boolean plan = changed(a, b, "terrain_plan") || changed(a, b, "national_template");
        return switch (probe) {
            case WorldgenFingerprint.PROBE_DENSITY -> plan || changed(a, b, "terrain_density");
            case WorldgenFingerprint.PROBE_BIOMES -> plan || changed(a, b, "ecology");
            default -> plan || changed(a, b, "river_carving") || changed(a, b, "ecology");
        };
    }

    private static String where(TerrainPlanFingerprint.TileComparison c) {
        if (!c.comparable() || c.changedTiles() <= 0) return "";
        return "; around x " + TerrainPlanFingerprint.tileOrigin(c.minCol()) + ".." + TerrainPlanFingerprint.tileOrigin(c.maxCol() + 1)
                + ", z " + TerrainPlanFingerprint.tileOrigin(c.minRow()) + ".." + TerrainPlanFingerprint.tileOrigin(c.maxRow() + 1);
    }

    /** The token that accepts exactly this change (stored baseline -> running rules), 8 hex digits. */
    public static String token(WorldgenFingerprint stored, WorldgenFingerprint current) {
        return WorldgenFingerprint.sha16(stored.id() + ">" + current.id()).substring(0, 8);
    }

    /** A multi-line report for the log, the crash report and the status command. */
    public static String describe(Verdict v) {
        StringBuilder sb = new StringBuilder();
        for (Finding f : v.findings())
            sb.append("  [").append(f.severity()).append("] ").append(f.system()).append(": ").append(f.stored()).append(" -> ")
                    .append(f.current()).append(" (").append(f.note()).append(")\n");
        for (String n : v.notes()) sb.append("  note: ").append(n).append('\n');
        return sb.toString();
    }
}
