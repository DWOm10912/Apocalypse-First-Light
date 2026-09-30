package com.antaurora.apofirstlight.meshshape;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mesh Shape profiles, read once from the mod jar at {@code /data/<namespace>/mesh_shapes/<path>.json}. Shapes are
 * gameplay data needed identically on both sides and already while Minecraft builds its block-state caches, so they are
 * not resource- or data-pack reloadable (like native_guns). A missing or invalid profile logs once and degrades to a
 * full cube, never a crash.
 */
public final class AflMeshShapes {
    private static final Map<ResourceLocation, AflMeshShapeProfile> CACHE = new ConcurrentHashMap<>();

    private AflMeshShapes() {
    }

    public static AflMeshShapeProfile get(ResourceLocation id) {
        return CACHE.computeIfAbsent(id, AflMeshShapes::load);
    }

    /** Profiles are populated by block-state shape cache construction before world picking starts. */
    public static int pickRadius() {
        return CACHE.values().stream().mapToInt(AflMeshShapeProfile::pickRadius).max().orElse(0);
    }

    private static AflMeshShapeProfile load(ResourceLocation id) {
        String path = "/data/" + id.getNamespace() + "/mesh_shapes/" + id.getPath() + ".json";
        try (var in = AflMeshShapes.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalArgumentException("not found");
            return AflMeshShapeProfile.parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) {
            ApocalypseFirstLight.LOGGER.error("Rejected AFL mesh shape profile {}: {}; using a full cube", path, e.getMessage());
            return AflMeshShapeProfile.fallback();
        }
    }
}
