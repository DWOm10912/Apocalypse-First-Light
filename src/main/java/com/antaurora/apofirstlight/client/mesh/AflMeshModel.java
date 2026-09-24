package com.antaurora.apofirstlight.client.mesh;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resource-generation data only. Never holds live GeoBones or mutable instance poses. */
public final class AflMeshModel {
    private final Map<String, List<AflMeshPart>> bones;

    AflMeshModel(Map<String, List<AflMeshPart>> bones) {
        var copy = new LinkedHashMap<String, List<AflMeshPart>>();
        bones.forEach((name, parts) -> copy.put(name, List.copyOf(parts)));
        this.bones = Map.copyOf(copy);
    }

    public List<AflMeshPart> parts(String bone) { return bones.getOrDefault(bone, List.of()); }
    public int partCount() { return bones.values().stream().mapToInt(List::size).sum(); }
}
