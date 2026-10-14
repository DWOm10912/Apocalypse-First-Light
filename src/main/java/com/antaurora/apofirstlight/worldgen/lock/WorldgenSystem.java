package com.antaurora.apofirstlight.worldgen.lock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One declared generation system and its world-compatibility version (WorldgenRules). Pure value. */
public record WorldgenSystem(String id, String version, Severity severity, String meaning) {
    public enum Severity {
        /** New chunks would not fit old ones (terrain, water, caves, biomes, surface): loading is refused. */
        HARD,
        /** Only decoration of new chunks differs (e.g. trees): loading goes on, the change is logged and recorded. */
        SOFT
    }

    public static Map<String, String> versions(List<WorldgenSystem> rules) {
        Map<String, String> m = new LinkedHashMap<>();
        for (WorldgenSystem r : rules) m.put(r.id(), r.version());
        return m;
    }

    /** Severity of a system id under these rules (an unknown or retired system counts as HARD). */
    public static Severity severity(List<WorldgenSystem> rules, String id) {
        for (WorldgenSystem r : rules) if (r.id().equals(id)) return r.severity();
        return Severity.HARD;
    }

    public static String meaning(List<WorldgenSystem> rules, String id) {
        for (WorldgenSystem r : rules) if (r.id().equals(id)) return r.meaning();
        return "retired system";
    }
}
