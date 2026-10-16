package com.antaurora.apofirstlight.skeleton;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Player skeleton V1 (docs/dev/player_skeleton_v1_design.md 1, 10.1): the rig definition read from
 * {@code assets/apocalypse_firstlight/rigs/afl_humanoid_v1.json}. The JSON is in Blockbench / Bedrock px (+y up, origin
 * on the ground); everything here is converted to Java model px (y down, the vanilla PlayerModel's space: y 0 at the
 * neck, the feet at y 24). Pure data, no Minecraft classes, so the offline checks (src/test) load the same rig.
 */
public final class SkeletonRig {
    public static final String RESOURCE = "assets/apocalypse_firstlight/rigs/afl_humanoid_v1.json";

    /** One textured box of the skin, cut into segments along y (its bones from top to bottom). */
    public record SkinPart(String part, String overlay, int[] bones, int[] cuts, Box classic, Box slim, int u, int v,
                           int overlayU, int overlayV) {
        public Box box(boolean slimSkin) { return slimSkin ? slim : classic; }
    }

    /** Java model px: min corner and integer size (the vanilla Cube's dimensions, which also set its UV layout). */
    public record Box(float x, float y, float z, int w, int h, int d) {}

    public record Socket(int bone, Vector3f pos) {}

    /** Degrees, Java model axes: the bone's local Z-Y-X angles. NaN where the axis is free. */
    public record Limit(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {}

    public final String[] names;
    public final int[] parent;
    /** Java model px. */
    public final Vector3f[] pivot;
    public final List<SkinPart> skin;
    public final float overlayInflate;
    /** Vanilla PlayerModel part name -> bone. */
    public final Map<String, Integer> vanillaParts;
    public final Map<String, Socket> sockets;
    public final Limit[] limits;
    private final Map<String, Integer> index = new HashMap<>();

    public final int root, pelvis, chest, head, upperArmR, forearmR, handR, upperArmL, forearmL, handL,
            thighR, shinR, footR, thighL, shinL, footL;

    private SkeletonRig(JsonObject json) {
        JsonArray bones = json.getAsJsonArray("bones");
        int n = bones.size();
        names = new String[n];
        parent = new int[n];
        pivot = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            JsonObject b = bones.get(i).getAsJsonObject();
            names[i] = b.get("name").getAsString();
            index.put(names[i], i);
        }
        for (int i = 0; i < n; i++) {
            JsonObject b = bones.get(i).getAsJsonObject();
            JsonElement p = b.get("parent");
            parent[i] = p == null || p.isJsonNull() ? -1 : bone(p.getAsString());
            if (parent[i] >= i) throw new IllegalArgumentException("rig: parent after child " + names[i]);
            pivot[i] = java(vec(b.getAsJsonArray("pivot")));
        }
        overlayInflate = json.has("overlay_inflate") ? json.get("overlay_inflate").getAsFloat() : 0.25F;
        List<SkinPart> parts = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("skin")) {
            JsonObject s = e.getAsJsonObject();
            JsonArray boneNames = s.getAsJsonArray("bones"), cutList = s.getAsJsonArray("cuts");
            int[] ids = new int[boneNames.size()], cuts = new int[cutList.size()];
            for (int i = 0; i < ids.length; i++) ids[i] = bone(boneNames.get(i).getAsString());
            for (int i = 0; i < cuts.length; i++) cuts[i] = cutList.get(i).getAsInt();
            if (cuts.length != ids.length - 1) throw new IllegalArgumentException("rig: cuts vs bones in " + s.get("part"));
            Box classic = box(s.getAsJsonArray("from"), s.getAsJsonArray("size"));
            Box slim = s.has("slim_from") ? box(s.getAsJsonArray("slim_from"), s.getAsJsonArray("slim_size")) : classic;
            JsonArray uv = s.getAsJsonArray("uv"), ouv = s.getAsJsonArray("overlay_uv");
            parts.add(new SkinPart(s.get("part").getAsString(), s.get("overlay").getAsString(), ids, cuts, classic, slim,
                    uv.get(0).getAsInt(), uv.get(1).getAsInt(), ouv.get(0).getAsInt(), ouv.get(1).getAsInt()));
        }
        skin = List.copyOf(parts);
        Map<String, Integer> vanilla = new LinkedHashMap<>();
        for (var e : json.getAsJsonObject("vanilla_parts").entrySet()) vanilla.put(e.getKey(), bone(e.getValue().getAsString()));
        vanillaParts = Map.copyOf(vanilla);
        Map<String, Socket> socketMap = new HashMap<>();
        for (var e : json.getAsJsonObject("sockets").entrySet()) {
            JsonObject s = e.getValue().getAsJsonObject();
            socketMap.put(e.getKey(), new Socket(bone(s.get("bone").getAsString()), java(vec(s.getAsJsonArray("pos")))));
        }
        sockets = Map.copyOf(socketMap);
        limits = new Limit[n];
        if (json.has("limits_deg")) for (var e : json.getAsJsonObject("limits_deg").entrySet()) {
            JsonObject l = e.getValue().getAsJsonObject();
            float[] x = range(l, "x"), y = range(l, "y"), z = range(l, "z");
            limits[bone(e.getKey())] = new Limit(x[0], x[1], y[0], y[1], z[0], z[1]);
        }
        root = bone("root"); pelvis = bone("pelvis"); chest = bone("chest"); head = bone("head");
        upperArmR = bone("upper_arm_r"); forearmR = bone("forearm_r"); handR = bone("hand_r");
        upperArmL = bone("upper_arm_l"); forearmL = bone("forearm_l"); handL = bone("hand_l");
        thighR = bone("thigh_r"); shinR = bone("shin_r"); footR = bone("foot_r");
        thighL = bone("thigh_l"); shinL = bone("shin_l"); footL = bone("foot_l");
    }

    public static SkeletonRig load(InputStream in) throws IOException {
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new SkeletonRig(JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    private static SkeletonRig loaded;

    /** The rig from the classpath (the mod jar in game, build/resources in the offline checks). */
    public static synchronized SkeletonRig get() {
        if (loaded == null) {
            try (InputStream in = SkeletonRig.class.getClassLoader().getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IOException("missing " + RESOURCE);
                loaded = load(in);
            } catch (IOException e) {
                throw new IllegalStateException("player skeleton rig", e);
            }
        }
        return loaded;
    }

    public int size() { return names.length; }

    public int bone(String name) {
        Integer i = index.get(name);
        if (i == null) throw new IllegalArgumentException("rig: no bone " + name);
        return i;
    }

    /** -1 when absent. */
    public int find(String name) { return index.getOrDefault(name, -1); }

    /** Bedrock px (y up, ground 0) to Java model px (y down, neck 0). */
    public static Vector3f java(Vector3f bedrock) { return new Vector3f(bedrock.x, 24F - bedrock.y, bedrock.z); }

    private static Vector3f vec(JsonArray a) { return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()); }

    private static float[] range(JsonObject o, String axis) {
        if (!o.has(axis)) return new float[]{Float.NaN, Float.NaN};
        JsonArray a = o.getAsJsonArray(axis);
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat()};
    }

    /** Bedrock box (min corner, y up) to the Java min corner (top of the box). */
    private static Box box(JsonArray from, JsonArray size) {
        Vector3f f = vec(from), s = vec(size);
        return new Box(f.x, 24F - (f.y + s.y), f.z, Math.round(s.x), Math.round(s.y), Math.round(s.z));
    }
}
