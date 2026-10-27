package com.antaurora.apofirstlight.client.creature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.creature.infected.InfectedEntity;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.SkinMesh;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Infected Z1a's look (docs/gameplay/afl_infected_z0_design.md 12): the r6 shambler's skinned mesh, texture and clips,
 * made in Blender by design/creatures/infected_z0/z1_shambler_export.py (creatures/infected/shambler_mesh.json,
 * shambler_clips.json, textures/entity/infected/shambler.png). Its own 14-bone rig: pelvis, belly, chest, neck, head, a
 * hinged jaw, two-bone arms and legs.
 * <ul>
 *   <li>Living ({@link #animate}): an idle clip (one of two, by the entity id) blended into the walk by the walking speed;
 *   the walk's phase is the distance walked over the clip's cycle (walk_cycle_px), so the planted foot keeps its place;
 *   a swing (InfectedEntity's entity event) plays the attack clip over the upper body; the head turns toward where the
 *   infected looks, in the body's frame.</li>
 *   <li>Dead ({@link #fromRagdoll}): the ragdoll falls on the player rig (survivor/SurvivorRagdolls), whose bones match the
 *   shambler's by place: the belly takes half the chest's turn at the waist, the neck and jaw ride the chest and head.</li>
 * </ul>
 * Vertices: x y z u v j0..j3 w0..w3 in Java model px (y down, the feet at 24), as the Block-Mesh body's.
 */
public final class InfectedModel {
    public static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/entity/infected/shambler.png");
    private static final String MESH = "creatures/infected/shambler_mesh.json", CLIPS = "creatures/infected/shambler_clips.json";
    private static final int STRIDE = 13;
    /** The model is drawn at the vanilla player's 0.9375. */
    static final float SCALE = 0.9375F;
    /** Bones the swing plays on; the legs keep walking or standing. */
    private static final String[] UPPER = {"belly", "chest", "neck", "head", "jaw", "upper.R", "fore.R", "upper.L", "fore.L"};
    /** Walking speed (blocks per tick) at which the walk has fully taken over from the idle. */
    private static final float FULL_WALK = 0.022F;
    private static final float HEAD_YAW = 60F, HEAD_PITCH = 40F;

    @Nullable private static InfectedModel shambler;
    private static boolean failed;

    final String[] bones;
    final int[] parent;
    final Vector3f[] pivot;
    final int[] quadPart;
    final float[] v;
    private final Map<String, Clip> clips;
    /** Blocks the body goes in one walk cycle (two steps), at the drawn scale. */
    private final float walkCycle;
    private final float attackLength;
    private final boolean[] upper;
    private final Quaternionf[] base, layer;

    private record Clip(float length, boolean loop, boolean smooth, float[] t, float[] lift, Quaternionf[][] rot) {}

    private InfectedModel(JsonObject mesh, JsonObject clipJson) {
        JsonArray b = mesh.getAsJsonArray("bones");
        int n = b.size();
        bones = new String[n];
        parent = new int[n];
        pivot = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            JsonObject o = b.get(i).getAsJsonObject();
            bones[i] = o.get("name").getAsString();
            parent[i] = o.get("parent").getAsInt();
            if (parent[i] >= i) throw new IllegalArgumentException("infected mesh: bone " + bones[i] + " before its parent");
            JsonArray p = o.getAsJsonArray("pivot");
            pivot[i] = new Vector3f(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat());
        }
        JsonArray quads = mesh.getAsJsonArray("quad_parts"), verts = mesh.getAsJsonArray("vertices");
        quadPart = new int[quads.size()];
        for (int i = 0; i < quadPart.length; i++) quadPart[i] = quads.get(i).getAsInt();
        v = new float[verts.size()];
        int k = 0;
        for (JsonElement e : verts) v[k++] = e.getAsFloat();
        if (v.length != quadPart.length * 4 * STRIDE) throw new IllegalArgumentException("infected mesh: vertices vs quads");

        JsonArray clipBones = clipJson.getAsJsonArray("bones");
        if (clipBones.size() != n) throw new IllegalArgumentException("infected clips: other bones than the mesh");
        for (int i = 0; i < n; i++) {
            if (!clipBones.get(i).getAsString().equals(bones[i])) throw new IllegalArgumentException("infected clips: bone order");
        }
        clips = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : clipJson.getAsJsonObject("clips").entrySet()) {
            JsonObject c = e.getValue().getAsJsonObject();
            JsonArray keys = c.getAsJsonArray("keys");
            float[] t = new float[keys.size()], lift = new float[keys.size()];
            Quaternionf[][] rot = new Quaternionf[keys.size()][n];
            for (int i = 0; i < t.length; i++) {
                JsonObject key = keys.get(i).getAsJsonObject();
                t[i] = key.get("t").getAsFloat();
                lift[i] = key.get("lift").getAsFloat();
                JsonArray r = key.getAsJsonArray("rot");
                for (int j = 0; j < n; j++) {
                    JsonArray q = r.get(j).getAsJsonArray();
                    rot[i][j] = new Quaternionf(q.get(0).getAsFloat(), q.get(1).getAsFloat(), q.get(2).getAsFloat(), q.get(3).getAsFloat()).normalize();
                }
            }
            clips.put(e.getKey(), new Clip(c.get("length").getAsFloat(), c.get("loop").getAsBoolean(),
                    "smooth".equals(c.get("ease").getAsString()), t, lift, rot));
        }
        for (String need : new String[]{"walk", "idle_1", "idle_2", "attack"}) {
            if (!clips.containsKey(need)) throw new IllegalArgumentException("infected clips: no " + need);
        }
        walkCycle = clipJson.get("walk_cycle_px").getAsFloat() / 16F * SCALE;
        attackLength = clips.get("attack").length;
        upper = new boolean[n];
        for (String u : UPPER) upper[index(u)] = true;
        base = newRot(n);
        layer = newRot(n);
    }

    /** The shambler, loaded once; null (logged once) when its files are missing or broken. */
    @Nullable
    static InfectedModel shambler() {
        if (shambler == null && !failed) {
            try {
                shambler = new InfectedModel(read(MESH), read(CLIPS));
            } catch (IOException | RuntimeException e) {
                failed = true;
                ApocalypseFirstLight.LOGGER.error("[AFL Infected] shambler mesh or clips failed to load", e);
            }
        }
        return shambler;
    }

    private static JsonObject read(String path) throws IOException {
        try (InputStream in = InfectedModel.class.getClassLoader().getResourceAsStream("assets/" + ApocalypseFirstLight.MOD_ID + "/" + path)) {
            if (in == null) throw new IOException("missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    int index(String bone) {
        for (int i = 0; i < bones.length; i++) if (bones[i].equals(bone)) return i;
        throw new IllegalArgumentException("infected mesh: no bone " + bone);
    }

    Matrix4f[] newMatrices() {
        Matrix4f[] m = new Matrix4f[bones.length];
        for (int i = 0; i < m.length; i++) m[i] = new Matrix4f();
        return m;
    }

    // ---------------------------------------------------------------- living

    private final float[] lift = new float[1];
    private final Quaternionf look = new Quaternionf();
    private final Vector3f at = new Vector3f();

    private static Quaternionf[] newRot(int n) {
        Quaternionf[] q = new Quaternionf[n];
        for (int i = 0; i < q.length; i++) q[i] = new Quaternionf();
        return q;
    }

    /** The living infected's bone matrices this frame (model px, the model's frame). */
    void animate(InfectedEntity e, float partial, Matrix4f[] out) {
        float time = (e.tickCount + partial) / 20F + (e.getId() % 17) * 0.37F;
        sample(clips.get((e.getId() & 1) == 0 ? "idle_1" : "idle_2"), time, base, lift);
        float idleLift = lift[0];
        float walk = Mth.clamp(e.walkSpeed(partial) / FULL_WALK, 0F, 1F);
        walk = walk * walk * (3F - 2F * walk);
        float rootLift = idleLift;
        if (walk > 0F) {
            sample(clips.get("walk"), e.walkDistance(partial) / walkCycle, layer, lift);
            for (int i = 0; i < bones.length; i++) nlerp(base[i], layer[i], walk, base[i]);
            rootLift = Mth.lerp(walk, idleLift, lift[0]);
        }
        float swing = e.swingTime(partial);
        if (swing >= 0F && swing <= attackLength) {
            sample(clips.get("attack"), swing, layer, lift);
            float w = Mth.clamp(Math.min(swing / 0.08F, (attackLength - swing) / 0.12F), 0F, 1F);
            for (int i = 0; i < bones.length; i++) if (upper[i]) nlerp(base[i], layer[i], w, base[i]);
        }
        matrices(base, rootLift, out);
        // the head turns toward the look in the body's frame, about its posed pivot
        float yaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)),
                -HEAD_YAW, HEAD_YAW);
        float pitch = Mth.clamp(Mth.lerp(partial, e.xRotO, e.getXRot()), -HEAD_PITCH, HEAD_PITCH);
        int head = index("head");
        look.rotationZYX(0F, yaw * Mth.DEG_TO_RAD, pitch * Mth.DEG_TO_RAD);
        out[head].transformPosition(at.set(pivot[head]));
        Matrix4f turn = new Matrix4f().translation(at).rotate(look).translate(-at.x, -at.y, -at.z);
        for (int i = head; i < bones.length; i++) {
            if (i == head || descends(i, head)) turn.mul(out[i], out[i]);
        }
    }

    private boolean descends(int child, int ancestor) {
        for (int p = parent[child]; p >= 0; p = parent[p]) if (p == ancestor) return true;
        return false;
    }

    /** W_b = W_parent * T(P) * R * T(-P); the root raised by {@code lift} px. */
    private void matrices(Quaternionf[] rot, float rootLift, Matrix4f[] out) {
        for (int i = 0; i < bones.length; i++) {
            Vector3f p = pivot[i];
            if (parent[i] < 0) out[i].translation(0F, -rootLift, 0F);
            else out[i].set(out[parent[i]]);
            out[i].translate(p).rotate(rot[i]).translate(-p.x, -p.y, -p.z);
        }
    }

    private void sample(Clip c, float time, Quaternionf[] out, float[] liftOut) {
        float t = c.loop ? (float) (time - Math.floor(time / c.length) * c.length) : Mth.clamp(time, 0F, c.length);
        int last = c.t.length - 1, i = 0;
        while (i < last - 1 && c.t[i + 1] <= t) i++;
        float span = c.t[i + 1] - c.t[i];
        float f = span <= 0F ? 0F : Mth.clamp((t - c.t[i]) / span, 0F, 1F);
        if (c.smooth) f = f * f * (3F - 2F * f);
        for (int b = 0; b < bones.length; b++) nlerp(c.rot[i][b], c.rot[i + 1][b], f, out[b]);
        liftOut[0] = Mth.lerp(f, c.lift[i], c.lift[i + 1]);
    }

    /** Normalised linear blend the short way round. */
    private static void nlerp(Quaternionf a, Quaternionf b, float t, Quaternionf out) {
        float dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        float s = dot < 0F ? -1F : 1F;
        out.set(a.x + (s * b.x - a.x) * t, a.y + (s * b.y - a.y) * t, a.z + (s * b.z - a.z) * t, a.w + (s * b.w - a.w) * t).normalize();
    }

    // ---------------------------------------------------------------- dead

    private int[] ragdollSource;
    private boolean[] ragdollHalf;

    /** A ragdoll pose on the player rig as the shambler's bone matrices. */
    void fromRagdoll(SkeletonPose pose, Matrix4f[] out) {
        SkeletonRig rig = pose.rig;
        if (ragdollSource == null) {
            int[] src = new int[bones.length];
            boolean[] half = new boolean[bones.length];
            for (int i = 0; i < bones.length; i++) {
                String name = bones[i];
                String side = name.endsWith(".R") ? "_r" : name.endsWith(".L") ? "_l" : "";
                src[i] = switch (name.replace(".R", "").replace(".L", "")) {
                    case "pelvis" -> rig.pelvis;
                    case "belly" -> {
                        half[i] = true;
                        yield rig.chest;
                    }
                    case "chest", "neck" -> rig.chest;
                    case "head", "jaw" -> rig.head;
                    case "upper" -> rig.find("upper_arm" + side);
                    case "fore" -> rig.find("forearm" + side);
                    case "thigh" -> rig.find("thigh" + side);
                    case "shin" -> rig.find("shin" + side);
                    default -> -1;
                };
                if (src[i] < 0) throw new IllegalArgumentException("infected ragdoll: no player bone for " + name);
            }
            ragdollSource = src;
            ragdollHalf = half;
        }
        for (int i = 0; i < bones.length; i++) {
            if (ragdollHalf[i]) pose.half(ragdollSource[i], out[i]);
            else out[i].set(pose.world[ragdollSource[i]]);
        }
    }

    /** The rest pose (identity matrices). */
    void rest(Matrix4f[] out) {
        for (Matrix4f m : out) m.identity();
    }

    // ---------------------------------------------------------------- drawing

    private static final Vector3f[] CORNER = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};

    /** The mesh skinned by {@code mats}, from the model-origin pose stack (model px / 16). */
    void draw(Matrix4f[] mats, PoseStack stack, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a) {
        PoseStack.Pose last = stack.last();
        Matrix4f mp = last.pose();
        Matrix3f mn = last.normal();
        Vector3f p = new Vector3f(), n = new Vector3f();
        SkinMesh.Sink sink = (x, y, z, u, v, nx, ny, nz) -> {
            mp.transformPosition(x / 16F, y / 16F, z / 16F, p);
            mn.transform(nx, ny, nz, n);
            consumer.vertex(p.x, p.y, p.z, r, g, b, a, u, v, overlay, light, n.x, n.y, n.z);
        };
        float[] uv = new float[8];
        Vector3f rest = new Vector3f(), part = new Vector3f();
        for (int q = 0; q < quadPart.length; q++) {
            for (int k = 0; k < 4; k++) {
                int o = (q * 4 + k) * STRIDE;
                rest.set(this.v[o], this.v[o + 1], this.v[o + 2]);
                Vector3f c = CORNER[k].zero();
                for (int j = 0; j < 4; j++) {
                    float w = this.v[o + 9 + j];
                    if (w <= 0F) continue;
                    mats[(int) this.v[o + 5 + j]].transformPosition(rest, part);
                    c.fma(w, part);
                }
                // SkinMesh#quad divides the uv by 64; the mesh's is 0..1
                uv[k * 2] = this.v[o + 3] * 64F;
                uv[k * 2 + 1] = this.v[o + 4] * 64F;
            }
            SkinMesh.quad(sink, CORNER[0], uv[0], uv[1], CORNER[1], uv[2], uv[3], CORNER[2], uv[4], uv[5], CORNER[3], uv[6], uv[7]);
        }
    }
}
