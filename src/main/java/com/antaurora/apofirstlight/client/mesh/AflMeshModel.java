package com.antaurora.apofirstlight.client.mesh;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resource-generation data only. Never holds live GeoBones or mutable instance poses. */
public final class AflMeshModel {
    private final Map<String, List<AflMeshPart>> bones;
    private final Map<String, List<AflMeshPart>> cutout, translucent;
    private final int formatVersion, partCount;

    AflMeshModel(int formatVersion, Map<String, List<AflMeshPart>> bones) {
        this.formatVersion = formatVersion;
        var copy = new LinkedHashMap<String, List<AflMeshPart>>();
        bones.forEach((name, parts) -> copy.put(name, List.copyOf(parts)));
        this.bones = Map.copyOf(copy);
        var opaque = new LinkedHashMap<String, List<AflMeshPart>>();
        var glass = new LinkedHashMap<String, List<AflMeshPart>>();
        copy.forEach((name, parts) -> {
            var a = parts.stream().filter(p -> p.layer() == AflMeshPart.Layer.CUTOUT).toList();
            var b = parts.stream().filter(p -> p.layer() == AflMeshPart.Layer.TRANSLUCENT).toList();
            if (!a.isEmpty()) opaque.put(name, a);
            if (!b.isEmpty()) glass.put(name, b);
        });
        cutout = Map.copyOf(opaque);
        translucent = Map.copyOf(glass);
        this.partCount = copy.values().stream().mapToInt(List::size).sum();
    }

    public List<AflMeshPart> parts(String bone) { return bones.getOrDefault(bone, List.of()); }
    public List<AflMeshPart> parts(String bone, AflMeshPart.Layer layer) {
        return (layer == AflMeshPart.Layer.CUTOUT ? cutout : translucent).getOrDefault(bone, List.of());
    }
    public boolean hasTranslucent() { return !translucent.isEmpty(); }
    public int partCount() { return partCount; }
    public int formatVersion() { return formatVersion; }
}
