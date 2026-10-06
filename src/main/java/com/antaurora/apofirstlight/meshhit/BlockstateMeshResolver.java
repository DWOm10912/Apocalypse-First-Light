package com.antaurora.apofirstlight.meshhit;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which models (and turned how) draw a block state, read from the block's own blockstate JSON in the mod jar, so the server
 * knows it too (docs/rendering/mesh_hit_runtime_v1.md): {@code variants} (keys "prop=value,..." or ""; an array of weighted
 * variants: the first) and {@code multipart} ({@code when} with "a|b" alternatives, "!" negation, {@code OR} / {@code AND});
 * {@code x} / {@code y} turns. Only this mod's blocks.
 */
final class BlockstateMeshResolver {
    record Part(ResourceLocation model, int x, int y) {}

    private static final Map<ResourceLocation, JsonObject> FILES = new ConcurrentHashMap<>();
    private static final JsonObject NONE = new JsonObject();

    private BlockstateMeshResolver() {
    }

    static List<Part> parts(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null || !ApocalypseFirstLight.MOD_ID.equals(id.getNamespace())) return List.of();
        JsonObject file = FILES.computeIfAbsent(id, BlockstateMeshResolver::read);
        List<Part> parts = new ArrayList<>();
        try {
            if (file.has("variants")) {
                for (Map.Entry<String, JsonElement> e : file.getAsJsonObject("variants").entrySet()) {
                    if (matchesKey(state, e.getKey())) {
                        parts.add(part(e.getValue()));
                        break;
                    }
                }
            } else if (file.has("multipart")) {
                for (JsonElement element : file.getAsJsonArray("multipart")) {
                    JsonObject entry = element.getAsJsonObject();
                    if (!entry.has("when") || when(state, entry.getAsJsonObject("when"))) parts.add(part(entry.get("apply")));
                }
            }
        } catch (RuntimeException e) {
            ApocalypseFirstLight.LOGGER.error("[AFL MESH HIT] could not read the blockstate of {}", id, e);
            return List.of();
        }
        return parts;
    }

    private static JsonObject read(ResourceLocation id) {
        try (InputStream in = BlockstateMeshResolver.class.getResourceAsStream("/assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json")) {
            return in == null ? NONE : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return NONE;
        }
    }

    private static Part part(JsonElement element) {
        JsonObject o = element.isJsonArray() ? element.getAsJsonArray().get(0).getAsJsonObject() : element.getAsJsonObject();
        return new Part(new ResourceLocation(o.get("model").getAsString()), o.has("x") ? o.get("x").getAsInt() : 0, o.has("y") ? o.get("y").getAsInt() : 0);
    }

    private static boolean matchesKey(BlockState state, String key) {
        if (key.isEmpty() || key.equals("normal")) return true;
        for (String pair : key.split(",")) {
            int eq = pair.indexOf('=');
            if (eq < 0 || !matches(state, pair.substring(0, eq), pair.substring(eq + 1))) return false;
        }
        return true;
    }

    private static boolean when(BlockState state, JsonObject when) {
        if (when.has("OR")) {
            for (JsonElement e : when.getAsJsonArray("OR")) if (when(state, e.getAsJsonObject())) return true;
            return false;
        }
        if (when.has("AND")) {
            for (JsonElement e : when.getAsJsonArray("AND")) if (!when(state, e.getAsJsonObject())) return false;
            return true;
        }
        for (Map.Entry<String, JsonElement> e : when.entrySet()) if (!matches(state, e.getKey(), e.getValue().getAsString())) return false;
        return true;
    }

    private static boolean matches(BlockState state, String name, String wanted) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) return false;
        String value = valueName(state, property);
        boolean negate = wanted.startsWith("!");
        boolean any = false;
        for (String alternative : (negate ? wanted.substring(1) : wanted).split("\\|")) any |= alternative.equals(value);
        return any != negate;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
