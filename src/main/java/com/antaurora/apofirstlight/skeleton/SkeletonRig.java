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

    /**
     * AFL Block-Mesh V2 (docs/dev/block_mesh_v2_b_ingame.md): a rig at other proportions than vanilla's, solved by the same
     * poser on the same action data, and what that changes. Java model px unless named otherwise.
     * <ul>
     * <li>{@code handCapX / handCapY}: the hand's end (its middle) off the shoulder pivot, right arm (the left mirrors x):
     * the hand targets' arm frame (GunStance#centreX, #distalY); {@code handHalf} the hand's half width;</li>
     * <li>{@code hipHeight}, {@code footX}, {@code ankle}: Bedrock px, the seat's standing hips, feet apart, ankle over the
     * sole (SeatPose); {@code seatScale} a seated rider's draw size, {@code seatLegStretch} whether the seat lengthens the
     * legs;</li>
     * <li>{@code refHip, refThigh, refShin, refAnkle}: vanilla's right leg (Bedrock px) the locomotion clips were made for:
     * the feet go where that leg would put them (HumanoidPoser#plantFeet);</li>
     * <li>{@code keyWarp}: per leg / pelvis bone, a vanilla rest box and this rig's (6 + 6 floats): an action's prop key on
     * that bone keeps its place in the part ({@link #warpKey});</li>
     * <li>{@code legPivot}: the vanilla leg parts' frame (worn leg gear) at the rig's own hips;</li>
     * <li>{@code mesh}, {@code texture}: the body's skinned mesh and its skin (client/skeleton/BlockMeshBody).</li>
     * </ul>
     */
    public record Profile(float handCapX, float handCapY, float handHalf, float hipHeight, float footX, float ankle, float seatScale,
                          boolean seatLegStretch, Vector3f refHip, float refThigh, float refShin, float refAnkle,
                          Map<Integer, float[]> keyWarp, Vector3f legPivot, String mesh, String texture) {}

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
    /** Null for vanilla's proportions. */
    public final Profile profile;
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
        profile = json.has("block_mesh") ? profile(json.getAsJsonObject("block_mesh")) : null;
        root = bone("root"); pelvis = bone("pelvis"); chest = bone("chest"); head = bone("head");
        upperArmR = bone("upper_arm_r"); forearmR = bone("forearm_r"); handR = bone("hand_r");
        upperArmL = bone("upper_arm_l"); forearmL = bone("forearm_l"); handL = bone("hand_l");
        thighR = bone("thigh_r"); shinR = bone("shin_r"); footR = bone("foot_r");
        thighL = bone("thigh_l"); shinL = bone("shin_l"); footL = bone("foot_l");
    }

    private Profile profile(JsonObject b) {
        JsonArray cap = b.getAsJsonArray("hand_cap");
        JsonObject leg = b.getAsJsonObject("reference_leg");
        Map<Integer, float[]> warp = new HashMap<>();
        for (var e : b.getAsJsonObject("key_warp").entrySet()) {
            JsonObject w = e.getValue().getAsJsonObject();
            float[] f = new float[12];
            int i = 0;
            for (String k : new String[]{"block", "mesh"})
                for (JsonElement corner : w.getAsJsonArray(k)) for (JsonElement v : corner.getAsJsonArray()) f[i++] = v.getAsFloat();
            warp.put(bone(e.getKey()), f);
        }
        JsonArray hip = leg.getAsJsonArray("hip"), legPivot = b.getAsJsonArray("leg_part_pivot");
        return new Profile(cap.get(0).getAsFloat(), cap.get(1).getAsFloat(), b.get("hand_half").getAsFloat(), b.get("hip_height").getAsFloat(),
                b.get("foot_x").getAsFloat(), b.get("ankle").getAsFloat(), b.get("seat_scale").getAsFloat(), b.get("seat_leg_stretch").getAsBoolean(),
                new Vector3f(hip.get(0).getAsFloat(), hip.get(1).getAsFloat(), 0), leg.get("thigh").getAsFloat(), leg.get("shin").getAsFloat(),
                leg.get("ankle").getAsFloat(), Map.copyOf(warp), java(new Vector3f(legPivot.get(0).getAsFloat(), legPivot.get(1).getAsFloat(), 0)),
                b.get("mesh").getAsString(), b.get("texture").getAsString());
    }

    /**
     * A prop key's rest point (Java model px) on {@code bone}: as it is, or for a {@link Profile} rig moved per axis from the
     * bone's vanilla box to its own (the holster's draw stays at the holster on longer legs).
     */
    public Vector3f warpKey(int bone, Vector3f rest) {
        float[] w = profile == null ? null : profile.keyWarp().get(bone);
        if (w == null) return rest;
        float[] p = {rest.x, rest.y, rest.z};
        for (int i = 0; i < 3; i++) p[i] = w[6 + i] + (p[i] - w[i]) / (w[3 + i] - w[i]) * (w[9 + i] - w[6 + i]);
        return rest.set(p[0], p[1], p[2]);
    }

    public static SkeletonRig load(InputStream in) throws IOException {
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new SkeletonRig(JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    /** AFL Block-Mesh V2, prototype B (user 2026-10-25): the poser's 16 bones at the B body's proportions. */
    public static final String BLOCK_MESH_V2 = "assets/apocalypse_firstlight/rigs/afl_block_mesh_v2.json";
    private static SkeletonRig blockMesh;

    /** The Block-Mesh V2 rig from the classpath (the dev test, client/skeleton/BlockMeshBody). */
    public static synchronized SkeletonRig blockMeshV2() {
        if (blockMesh == null) {
            try (InputStream in = SkeletonRig.class.getClassLoader().getResourceAsStream(BLOCK_MESH_V2)) {
                if (in == null) throw new IOException("missing " + BLOCK_MESH_V2);
                blockMesh = load(in);
            } catch (IOException e) {
                throw new IllegalStateException("block mesh v2 rig", e);
            }
        }
        return blockMesh;
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
