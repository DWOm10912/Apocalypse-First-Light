package com.antaurora.apofirstlight.skeleton;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Player skeleton V1 (docs/dev/player_skeleton_v1_design.md 10.1): Bedrock animation clips (format_version 1.8.0, as
 * Blockbench exports and the Native guns use) for the skeleton's bones. Keyframes: number arrays, {@code {"vector"}} or
 * {@code {"pre", "post", "lerp_mode"}}; linear, catmullrom and step. Molang strings are not supported (read as 0).
 * Rotation keeps its numbers (deg, Z-Y-X, the same as the Java ModelPart angles); position is Bedrock px, so its y
 * changes sign in Java model px. Pure, no Minecraft classes.
 */
public final class SkeletonClips {
    public static final String LOCOMOTION = "assets/apocalypse_firstlight/animations/player/locomotion.animation.json";

    /** One channel: keys in seconds, values as [pre, post] per key. */
    static final class Channel {
        final float[] times;
        final float[][] pre, post;
        final byte[] mode; // 0 linear, 1 catmullrom, 2 step

        Channel(float[] times, float[][] pre, float[][] post, byte[] mode) {
            this.times = times;
            this.pre = pre;
            this.post = post;
            this.mode = mode;
        }

        void sample(float t, float[] out) {
            int n = times.length;
            if (n == 1 || t <= times[0]) { copy(n == 1 ? post[0] : pre[0], out); return; }
            if (t >= times[n - 1]) { copy(post[n - 1], out); return; }
            int i = Arrays.binarySearch(times, t);
            if (i >= 0) { copy(post[i], out); return; }
            int b = -i - 1, a = b - 1;
            float f = (t - times[a]) / (times[b] - times[a]);
            if (mode[a] == 2) { copy(post[a], out); return; }
            if (mode[a] == 1 || mode[b] == 1) {
                float[] p0 = a > 0 ? post[a - 1] : post[a], p3 = b < n - 1 ? pre[b + 1] : pre[b];
                for (int k = 0; k < 3; k++) out[k] = catmullRom(f, p0[k], post[a][k], pre[b][k], p3[k]);
                return;
            }
            for (int k = 0; k < 3; k++) out[k] = post[a][k] + (pre[b][k] - post[a][k]) * f;
        }

        private static void copy(float[] v, float[] out) { System.arraycopy(v, 0, out, 0, 3); }

        private static float catmullRom(float t, float p0, float p1, float p2, float p3) {
            float t2 = t * t, t3 = t2 * t;
            return 0.5F * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
        }
    }

    public static final class Clip {
        public final String name;
        public final float length;
        public final boolean loop;
        final Channel[] rotation, position;

        Clip(String name, float length, boolean loop, int bones) {
            this.name = name;
            this.length = length;
            this.loop = loop;
            rotation = new Channel[bones];
            position = new Channel[bones];
        }

        public boolean rotates(int bone) { return rotation[bone] != null; }

        public boolean moves(int bone) { return position[bone] != null; }

        /** Local rotation of a bone at t seconds (identity where the clip has no channel). */
        public Quaternionf rotation(int bone, float t, Quaternionf out) {
            Channel c = rotation[bone];
            if (c == null) return out.identity();
            float[] v = new float[3];
            c.sample(time(t), v);
            return out.rotationZYX((float) Math.toRadians(v[2]), (float) Math.toRadians(v[1]), (float) Math.toRadians(v[0]));
        }

        /** Local translation in Java model px (Bedrock y up turned to Java y down). */
        public Vector3f position(int bone, float t, Vector3f out) {
            Channel c = position[bone];
            if (c == null) return out.zero();
            float[] v = new float[3];
            c.sample(time(t), v);
            return out.set(v[0], -v[1], v[2]);
        }

        private float time(float t) {
            if (length <= 0) return 0;
            return loop ? ((t % length) + length) % length : Math.min(Math.max(t, 0), length);
        }
    }

    private final Map<String, Clip> clips = new HashMap<>();

    public Clip get(String name) {
        Clip c = clips.get(name);
        if (c == null) throw new IllegalArgumentException("skeleton clip missing: " + name);
        return c;
    }

    public boolean has(String name) { return clips.containsKey(name); }

    public static SkeletonClips load(SkeletonRig rig, InputStream in) throws IOException {
        SkeletonClips result = new SkeletonClips();
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject anims = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
            for (var e : anims.entrySet()) {
                JsonObject a = e.getValue().getAsJsonObject();
                float length = a.has("animation_length") ? a.get("animation_length").getAsFloat() : 0F;
                boolean loop = a.has("loop") && a.get("loop").isJsonPrimitive() && a.get("loop").getAsJsonPrimitive().isBoolean()
                        && a.get("loop").getAsBoolean();
                Clip clip = new Clip(e.getKey(), length, loop, rig.size());
                if (a.has("bones")) for (var b : a.getAsJsonObject("bones").entrySet()) {
                    int bone = rig.find(b.getKey());
                    if (bone < 0) continue; // helper bones of the template (ik_*, prop_*) are not part of the body
                    JsonObject ch = b.getValue().getAsJsonObject();
                    if (ch.has("rotation")) clip.rotation[bone] = channel(ch.get("rotation"));
                    if (ch.has("position")) clip.position[bone] = channel(ch.get("position"));
                }
                result.clips.put(e.getKey(), clip);
            }
        }
        return result;
    }

    private static SkeletonClips locomotion;

    /** The locomotion clips from the classpath. */
    public static synchronized SkeletonClips locomotion() {
        if (locomotion == null) {
            try (InputStream in = SkeletonClips.class.getClassLoader().getResourceAsStream(LOCOMOTION)) {
                if (in == null) throw new IOException("missing " + LOCOMOTION);
                locomotion = load(SkeletonRig.get(), in);
            } catch (IOException e) {
                throw new IllegalStateException("player skeleton clips", e);
            }
        }
        return locomotion;
    }

    static Channel channel(JsonElement e) {
        if (e.isJsonArray() || e.isJsonPrimitive() || e.isJsonObject() && e.getAsJsonObject().has("vector")) {
            float[] v = vector(e, true);
            return new Channel(new float[]{0}, new float[][]{v}, new float[][]{v}, new byte[]{0});
        }
        var entries = e.getAsJsonObject().entrySet().stream()
                .sorted((x, y) -> Float.compare(Float.parseFloat(x.getKey()), Float.parseFloat(y.getKey()))).toList();
        int n = entries.size();
        float[] times = new float[n];
        float[][] pre = new float[n][], post = new float[n][];
        byte[] mode = new byte[n];
        for (int i = 0; i < n; i++) {
            var k = entries.get(i);
            times[i] = Float.parseFloat(k.getKey());
            pre[i] = vector(k.getValue(), true);
            post[i] = vector(k.getValue(), false);
            if (k.getValue().isJsonObject() && k.getValue().getAsJsonObject().has("lerp_mode")) {
                String m = k.getValue().getAsJsonObject().get("lerp_mode").getAsString();
                mode[i] = (byte) (m.equals("catmullrom") ? 1 : m.equals("step") ? 2 : 0);
            }
        }
        return new Channel(times, pre, post, mode);
    }

    /** A keyframe value; {@code arriving} picks "pre" over "post". */
    private static float[] vector(JsonElement e, boolean arriving) {
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            float[] v = new float[3];
            for (int i = 0; i < 3 && i < a.size(); i++) v[i] = number(a.get(i));
            return v;
        }
        if (e.isJsonPrimitive()) { float f = number(e); return new float[]{f, f, f}; }
        JsonObject o = e.getAsJsonObject();
        if (o.has("vector")) return vector(o.get("vector"), arriving);
        JsonElement pick = arriving ? (o.has("pre") ? o.get("pre") : o.get("post")) : (o.has("post") ? o.get("post") : o.get("pre"));
        return pick == null ? new float[3] : vector(pick, arriving);
    }

    private static float number(JsonElement e) {
        try {
            return e.getAsFloat();
        } catch (RuntimeException ignored) {
            return 0F; // molang
        }
    }
}
