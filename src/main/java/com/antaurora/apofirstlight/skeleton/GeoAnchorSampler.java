package com.antaurora.apofirstlight.skeleton;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Player skeleton V1 (docs/dev/player_skeleton_v1_design.md 4, 10.9): a Native gun's bone frames from its geo and
 * animation JSON, the same chain as tools/check-skeleton-arm-reach.mjs and tools/audit-player-arm-presentation.mjs#aflScene
 * (GeckoLib: pivot x and rotation x / y negated, position x negated, Z-Y-X). The result is in the gun's bare GeckoLib
 * model frame (blocks), the frame NativeAdsProfile's hip() and ads() place. {@link #frame} of {@code right_hand_anchor} /
 * {@code left_hand_anchor} is the hand anchor NativeHandBinding describes (origin at the hand cap's centre). Pure.
 */
public final class GeoAnchorSampler {
    private record Bone(String name, String parent, float px, float py, float pz, float rx, float ry, float rz) {}

    private final Map<String, Bone> bones = new HashMap<>();
    private final Map<String, Map<String, SkeletonClips.Channel[]>> clips = new HashMap<>();

    public static GeoAnchorSampler load(InputStream geo, InputStream animation) throws IOException {
        GeoAnchorSampler s = new GeoAnchorSampler();
        try (var g = new InputStreamReader(geo, StandardCharsets.UTF_8)) {
            JsonArray list = JsonParser.parseReader(g).getAsJsonObject().getAsJsonArray("minecraft:geometry")
                    .get(0).getAsJsonObject().getAsJsonArray("bones");
            for (JsonElement e : list) {
                JsonObject b = e.getAsJsonObject();
                float[] p = vec(b.get("pivot")), r = vec(b.get("rotation"));
                String parent = b.has("parent") ? b.get("parent").getAsString() : null;
                s.bones.put(b.get("name").getAsString(), new Bone(b.get("name").getAsString(), parent,
                        -p[0] / 16F, p[1] / 16F, p[2] / 16F, r[0], r[1], r[2]));
            }
        }
        if (animation != null) try (var a = new InputStreamReader(animation, StandardCharsets.UTF_8)) {
            JsonObject anims = JsonParser.parseReader(a).getAsJsonObject().getAsJsonObject("animations");
            for (var clip : anims.entrySet()) {
                Map<String, SkeletonClips.Channel[]> channels = new HashMap<>();
                JsonObject c = clip.getValue().getAsJsonObject();
                if (c.has("bones")) for (var bone : c.getAsJsonObject("bones").entrySet()) {
                    JsonObject ch = bone.getValue().getAsJsonObject();
                    channels.put(bone.getKey(), new SkeletonClips.Channel[]{
                            ch.has("rotation") ? SkeletonClips.channel(ch.get("rotation")) : null,
                            ch.has("position") ? SkeletonClips.channel(ch.get("position")) : null,
                            ch.has("scale") ? SkeletonClips.channel(ch.get("scale")) : null});
                }
                s.clips.put(clip.getKey(), channels);
            }
        }
        return s;
    }

    /** Loads {@code assets/<namespace>/geo/<name>.geo.json} and {@code animations/<name>.animation.json} from the classpath. */
    public static GeoAnchorSampler resource(String namespace, String name) {
        ClassLoader loader = GeoAnchorSampler.class.getClassLoader();
        try (InputStream geo = loader.getResourceAsStream("assets/" + namespace + "/geo/" + name + ".geo.json");
             InputStream anim = loader.getResourceAsStream("assets/" + namespace + "/animations/" + name + ".animation.json")) {
            if (geo == null) throw new IOException("missing geo " + name);
            return load(geo, anim);
        } catch (IOException e) {
            throw new IllegalStateException("gun anchors " + name, e);
        }
    }

    public boolean has(String bone) { return bones.containsKey(bone); }

    /** A bone's frame at its pivot (for an anchor bone: the anchor), clip at t seconds (null clip = rest). */
    public Matrix4f frame(String bone, String clip, float t) {
        Bone b = bones.get(bone);
        if (b == null) return null;
        Map<String, SkeletonClips.Channel[]> anim = clip == null ? Map.of() : clips.getOrDefault(clip, Map.of());
        return matrix(b, anim, t, new HashMap<>()).translate(b.px, b.py, b.pz);
    }

    private Matrix4f matrix(Bone b, Map<String, SkeletonClips.Channel[]> anim, float t, Map<String, Matrix4f> cache) {
        Matrix4f cached = cache.get(b.name);
        if (cached != null) return new Matrix4f(cached);
        Matrix4f m = b.parent == null || !bones.containsKey(b.parent) ? new Matrix4f()
                : matrix(bones.get(b.parent), anim, t, cache);
        SkeletonClips.Channel[] ch = anim.get(b.name);
        float[] rot = new float[3], pos = new float[3], scale = {1, 1, 1};
        if (ch != null) {
            if (ch[0] != null) ch[0].sample(t, rot);
            if (ch[1] != null) ch[1].sample(t, pos);
            if (ch[2] != null) ch[2].sample(t, scale);
        }
        float rx = -b.rx - rot[0], ry = -b.ry - rot[1], rz = b.rz + rot[2];
        m.translate(-pos[0] / 16F, pos[1] / 16F, pos[2] / 16F)
                .translate(b.px, b.py, b.pz)
                .rotateZ((float) Math.toRadians(rz)).rotateY((float) Math.toRadians(ry)).rotateX((float) Math.toRadians(rx))
                .scale(scale[0], scale[1], scale[2])
                .translate(-b.px, -b.py, -b.pz);
        cache.put(b.name, new Matrix4f(m));
        return m;
    }

    private static float[] vec(JsonElement e) {
        if (e == null || !e.isJsonArray()) return new float[3];
        JsonArray a = e.getAsJsonArray();
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }
}
