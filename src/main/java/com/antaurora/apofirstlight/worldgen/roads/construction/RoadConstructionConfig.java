package com.antaurora.apofirstlight.worldgen.roads.construction;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

/** Bounded engineering policy; configuration is read explicitly, never generated during planning. */
public record RoadConstructionConfig(int maxColumns, int maxEdits, int maxGuards, int maxChunks,
        int maxCutDepth, int maxFillHeight, int maxSegmentEdits, int maxNoiseDeviation,
        int maxGradeH16PerBlock, int shoulderWidth, int supportDepth, int clearanceHeight,
        int columnsPerTick, int maxChecksPerTick, int maxEditsPerTick, int planLifetimeTicks) {
    public RoadConstructionConfig {
        check(maxColumns, 100, 50000, "maxColumns"); check(maxEdits, 100, 240000, "maxEdits");
        check(maxGuards, 100, 500000, "maxGuards"); check(maxChunks, 1, 512, "maxChunks");
        check(maxCutDepth, 0, 3, "maxCutDepth"); check(maxFillHeight, 0, 3, "maxFillHeight");
        check(maxSegmentEdits, 100, maxEdits, "maxSegmentEdits"); check(maxNoiseDeviation, 0, 4, "maxNoiseDeviation");
        check(maxGradeH16PerBlock, 0, 1, "maxGradeH16PerBlock"); check(shoulderWidth, 1, 4, "shoulderWidth");
        check(supportDepth, 1, 3, "supportDepth"); check(clearanceHeight, 3, 5, "clearanceHeight");
        check(columnsPerTick, 1, 128, "columnsPerTick"); check(maxChecksPerTick, 16, 4096, "maxChecksPerTick");
        check(maxEditsPerTick, 1, 512, "maxEditsPerTick"); check(planLifetimeTicks, 200, 72000, "planLifetimeTicks");
    }
    private static void check(int value, int min, int max, String name) {
        if (value < min || value > max) throw new IllegalArgumentException(name + " must be " + min + ".." + max);
    }
    public static RoadConstructionConfig defaults() {
        return new RoadConstructionConfig(20000,120000,400000,256,3,3,50000,3,1,3,2,3,64,2048,256,12000);
    }
    /** JSON uses the record's camelCase field names. Unknown, fractional and out-of-range values fail closed. */
    public static RoadConstructionConfig load(Path path) throws IOException {
        if (!Files.exists(path)) return defaults();
        if (Files.size(path) > 16384) throw new IllegalArgumentException("Road construction config is too large");
        var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        var fields = RoadConstructionConfig.class.getRecordComponents();
        var accepted = new HashSet<String>();
        for (var field : fields) accepted.add(field.getName());
        for (String key : root.keySet()) if (!accepted.contains(key)) throw new IllegalArgumentException("Unknown road config key: " + key);
        int[] values = new int[fields.length];
        try {
            var fallback = defaults();
            for (int i=0;i<fields.length;i++) {
                String key = fields[i].getName();
                if(root.has(key)&&(!root.get(key).isJsonPrimitive()||!root.get(key).getAsJsonPrimitive().isNumber()))
                    throw new IllegalArgumentException("Road config value must be a JSON number: "+key);
                values[i] = root.has(key) ? root.get(key).getAsBigDecimal().intValueExact()
                        : (Integer) fields[i].getAccessor().invoke(fallback);
            }
        } catch (ReflectiveOperationException | ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid integer road construction config", exception);
        }
        return new RoadConstructionConfig(values[0],values[1],values[2],values[3],values[4],values[5],values[6],values[7],
                values[8],values[9],values[10],values[11],values[12],values[13],values[14],values[15]);
    }
}
