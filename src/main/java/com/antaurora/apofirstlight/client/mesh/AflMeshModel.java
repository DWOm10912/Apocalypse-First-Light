package com.antaurora.apofirstlight.client.mesh;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resource-generation data only. Never holds live GeoBones or mutable instance poses. */
public final class AflMeshModel {
    private final Map<String, List<AflMeshPart>> bones;
    private final int formatVersion, partCount;

    AflMeshModel(int formatVersion, Map<String, List<AflMeshPart>> bones) {
        this.formatVersion = formatVersion;
        var copy = new LinkedHashMap<String, List<AflMeshPart>>();
        bones.forEach((name, parts) -> copy.put(name, List.copyOf(parts)));
        this.bones = Map.copyOf(copy);
        this.partCount = copy.values().stream().mapToInt(List::size).sum();
    }

    public List<AflMeshPart> parts(String bone) { return bones.getOrDefault(bone, List.of()); }
    public int partCount() { return partCount; }
    public int formatVersion() { return formatVersion; }
}
