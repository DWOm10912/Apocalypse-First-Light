package com.antaurora.apofirstlight.client.blockmesh;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile.*;
import com.antaurora.apofirstlight.client.mesh.AflMeshLoader;
import com.antaurora.apofirstlight.client.mesh.AflMeshModel;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.io.Reader;
import java.util.*;

/** Small animation/profile decoder only. Geometry is always decoded by AflMeshLoader. */
public final class AflBlockMeshProfileLoader {
    private record PartSpec(String parent, Vec3 pivot, Transform rest) {}

    public static Map<ResourceLocation, AflBlockMeshProfile> loadAll(ResourceManager manager,
                                                                     Map<ResourceLocation, AflMeshModel> meshes) {
        var profiles = new HashMap<ResourceLocation, AflBlockMeshProfile>();
        manager.listResources("block_mesh_profiles", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (var reader = resource.openAsReader()) {
                var profile = load(reader, meshes);
                require(manager.getResource(profile.texture()).isPresent(), "base texture not found: " + profile.texture());
                profiles.put(id, profile);
            } catch (Exception | StackOverflowError e) {
                ApocalypseFirstLight.LOGGER.error("Rejected AFL block mesh profile {}: {}", id, e.getMessage());
            }
        });
        return Map.copyOf(profiles);
    }

    private static AflBlockMeshProfile load(Reader reader, Map<ResourceLocation, AflMeshModel> meshes) throws Exception {
        var text = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            require(text.length() + count <= AflMeshLoader.MAX_CHARACTERS, "profile exceeds 4 MiB");
            text.append(buffer, 0, count);
        }
        var root = object(JsonParser.parseString(text.toString()), "profile");
        keys(root, "format_version", "geometry", "texture", "origin", "scale", "facing", "bounds", "parts", "animations");
        require(number(root.get("format_version")) == 1, "unsupported profile version");
        var geometry = new ResourceLocation(string(root.get("geometry")));
        var texture = new ResourceLocation(string(root.get("texture")));
        var mesh = meshes.get(geometry);
        require(mesh != null, "no valid AFL sidecar for " + geometry);
        String facing = root.has("facing") ? string(root.get("facing")) : "horizontal";
        require(facing.equals("horizontal") || facing.equals("none"), "facing must be horizontal or none");
        var origin = vector(root, "origin", Vec3.ZERO, false);
        var scale = vector(root, "scale", new Vec3(1, 1, 1), true);
        AABB bounds = new AABB(0, 0, 0, 1, 1, 1);
        if (root.has("bounds")) {
            var a = array(root.get("bounds"), 6);
            double[] b = new double[6];
            for (int i = 0; i < 6; i++) {
                b[i] = number(a.get(i));
                require(Math.abs(b[i]) <= 64, "bounds must be finite and within 64 blocks");
            }
            require(b[0] < b[3] && b[1] < b[4] && b[2] < b[5], "empty or reversed bounds");
            bounds = new AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
        }
        var definitions = object(root.get("parts"), "parts");
        require(!definitions.keySet().isEmpty() && definitions.size() <= 128, "parts count must be 1..128");
        var parts = new LinkedHashMap<String, PartSpec>();
        for (var entry : definitions.entrySet()) {
            String name = name(entry.getKey());
            var p = object(entry.getValue(), name);
            keys(p, "parent", "pivot", "rest");
            String parent = p.has("parent") ? name(string(p.get("parent"))) : null;
            require(p.has("pivot"), "missing pivot for " + name);
            parts.put(name, new PartSpec(parent, vector(p, "pivot", Vec3.ZERO, false),
                    p.has("rest") ? transform(p.get("rest")) : Transform.IDENTITY));
        }
        require(parts.keySet().containsAll(mesh.boneNames()), "profile omits a mesh bone binding");
        for (var p : parts.values()) require(p.parent() == null || parts.containsKey(p.parent()), "unknown parent");

        var animations = new LinkedHashMap<String, Animation>();
        var motions = new HashMap<String, Motion>();
        if (root.has("animations")) {
            var definitionsA = object(root.get("animations"), "animations");
            require(definitionsA.size() <= 32, "maximum 32 animation channels");
            for (var entry : definitionsA.entrySet()) {
                String channel = name(entry.getKey());
                var a = object(entry.getValue(), channel);
                keys(a, "duration_ticks", "easing", "transforms");
                double duration = number(a.get("duration_ticks"));
                require(duration > 0 && duration <= 72000, "duration_ticks must be in (0,72000]");
                Easing easing = Easing.valueOf(string(a.get("easing")).toUpperCase(Locale.ROOT));
                animations.put(channel, new Animation(duration, easing));
                var transforms = object(a.get("transforms"), "transforms");
                require(!transforms.keySet().isEmpty(), "empty animation transforms");
                for (var target : transforms.entrySet()) {
                    require(parts.containsKey(target.getKey()), "unknown animated part " + target.getKey());
                    require(motions.putIfAbsent(target.getKey(), new Motion(channel, transform(target.getValue()))) == null,
                            "V1 permits only one animation channel per part");
                }
            }
        }
        // Validate even disconnected cyclic components before building the immutable tree.
        for (String name : parts.keySet()) {
            var path = new HashSet<String>();
            for (String p = name; p != null; p = parts.get(p).parent())
                require(path.add(p) && path.size() <= 32, "cyclic or excessively deep part hierarchy");
        }
        var roots = new ArrayList<Part>();
        for (var entry : parts.entrySet()) if (entry.getValue().parent() == null)
            roots.add(build(entry.getKey(), parts, motions));
        return new AflBlockMeshProfile(geometry, texture, origin, scale, facing.equals("horizontal"), roots, animations, bounds);
    }

    private static Part build(String name, Map<String, PartSpec> parts, Map<String, Motion> motions) {
        var p = parts.get(name);
        var children = new ArrayList<Part>();
        for (var entry : parts.entrySet()) if (name.equals(entry.getValue().parent()))
            children.add(build(entry.getKey(), parts, motions));
        return new Part(name, p.pivot(), p.rest(), motions.get(name), children);
    }
    private static Transform transform(JsonElement value) {
        var t = object(value, "transform");
        keys(t, "translation", "rotation", "scale");
        return new Transform(vector(t, "translation", Vec3.ZERO, false), vector(t, "rotation", Vec3.ZERO, false),
                vector(t, "scale", new Vec3(1, 1, 1), true));
    }
    private static Vec3 vector(JsonObject object, String key, Vec3 fallback, boolean scale) {
        if (!object.has(key)) return fallback;
        var a = array(object.get(key), 3);
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) {
            v[i] = number(a.get(i));
            require(Math.abs(v[i]) <= (key.equals("rotation") ? 36000 : 256), key + " out of range");
            require(!scale || v[i] >= 0 && v[i] <= 64, "scale must be 0..64 (no implicit mirroring)");
        }
        return new Vec3(v[0], v[1], v[2]);
    }
    private static void keys(JsonObject object, String... allowed) {
        require(Set.of(allowed).containsAll(object.keySet()), "unknown fields: " + object.keySet());
    }
    private static String name(String name) {
        require(!name.isBlank() && name.length() <= 128, "invalid part/channel name");
        return name;
    }
    private static JsonObject object(JsonElement value, String context) {
        require(value != null && value.isJsonObject(), context + " must be object");
        return value.getAsJsonObject();
    }
    private static JsonArray array(JsonElement value, int length) {
        require(value != null && value.isJsonArray() && value.getAsJsonArray().size() == length, "invalid vector length");
        return value.getAsJsonArray();
    }
    private static String string(JsonElement value) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), "expected string");
        return value.getAsString();
    }
    private static double number(JsonElement value) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), "expected number");
        double d = value.getAsDouble();
        require(Double.isFinite(d), "non-finite number");
        return d;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    private AflBlockMeshProfileLoader() {}
}
