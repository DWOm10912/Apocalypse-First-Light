package com.antaurora.apofirstlight.worldgen.rural;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Ordered, frozen V1 pool membership. Declared weights/counts retain legacy meanings only. */
public record RuralRecipe(List<Entry> entries) {
    public RuralRecipe { entries = List.copyOf(entries); }

    public record Entry(ResourceLocation asset, RuralStructurePool.Role role, int declaredWeight, int declaredMaxCount) { }

    public static RuralRecipe parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.get("schema_version").getAsInt() != 1
                || !"LEGACY_NATURAL_V1".equals(root.get("selection_mode").getAsString()))
            throw new IllegalArgumentException("Unsupported Rural legacy recipe version/mode");
        if (!root.keySet().equals(Set.of("schema_version", "selection_mode", "entries")))
            throw new IllegalArgumentException("Unexpected Rural recipe root field");
        JsonArray entries = root.getAsJsonArray("entries");
        var result = new ArrayList<Entry>();
        var seen = new HashSet<ResourceLocation>();
        for (var value : entries) {
            JsonObject item = value.getAsJsonObject();
            if (!item.keySet().equals(Set.of("asset", "role", "legacy_declared_weight", "legacy_declared_max_count")))
                throw new IllegalArgumentException("Unexpected Rural recipe entry field");
            ResourceLocation asset = ResourceLocation.tryParse(item.get("asset").getAsString());
            if (asset == null || !"apocalypse_firstlight".equals(asset.getNamespace()) || !seen.add(asset))
                throw new IllegalArgumentException("Invalid or duplicate Rural recipe asset " + asset);
            int weight = item.get("legacy_declared_weight").getAsInt();
            int maxCount = item.get("legacy_declared_max_count").getAsInt();
            if (weight < 0 || maxCount <= 0) throw new IllegalArgumentException("Invalid Rural legacy count/weight");
            result.add(new Entry(asset, RuralStructurePool.Role.valueOf(item.get("role").getAsString()), weight, maxCount));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Empty Rural legacy recipe");
        return new RuralRecipe(result);
    }
}
