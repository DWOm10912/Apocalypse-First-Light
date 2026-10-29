package com.antaurora.apofirstlight.client.creature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.creature.deer.DeerEntity;
import com.antaurora.apofirstlight.creature.deer.DeerSchedule;
import com.antaurora.apofirstlight.ground.GroundView;
import com.antaurora.apofirstlight.skeleton.SkinMesh;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * The white-tailed deer's look (docs/gameplay/afl_whitetail_deer_d0_design.md 11): the D0 concept's buck and doe as
 * skinned meshes with their clips, made in Blender by design/creatures/whitetail_deer_d0/deer_export.py
 * (creatures/deer/{buck,doe}_mesh.json, clips.json, textures/entity/deer/{buck,doe}{,_blink,_dead}.png). Its own 25-bone rig:
 * pelvis, lumbar, chest, a two-bone neck, head, ears, tail, four-bone legs. The doe's mesh is built at 0.92; the clips are
 * the buck's (rotations as they are, lift and gait cycles times the mesh's scale).
 *
 * <p>Layers, so it never stands like a statue (design 3, idle):
 * <ol>
 *   <li>the stance: stand, graze (head down by DeerSchedule's beat, the same the server bites to) and alert, blended by
 *   weights that ease over about 0.3 s; the head snaps up into alert faster than it goes down to graze;</li>
 *   <li>the gaits by ground speed: walk, trot, bound, each phased by distance over its cycle so the planted hooves keep their
 *   place;</li>
 *   <li>one-shot idles when it stands still (look back, head shake, sniff, shift a forefoot), one every 8–25 s; the hoof
 *   stamp on its entity event;</li>
 *   <li>procedural: the ears turn on their own (each its own way now and then, both forward when alert, laid back in
 *   flight), the tail swishes and goes up in flight, the head follows the look over the neck, a tug at each bite, a slow
 *   chew with the head up, and a blink every 4–8 s (the *_blink texture).</li>
 * </ol>
 * The layers' state is per deer and client-only (a weak map). Vertices: x y z u v j0..j3 w0..w3 in Java model px (y down,
 * hooves at 24), as the infected's.
 */
public final class DeerModel {
    private static final String CLIPS = "creatures/deer/clips.json";
    private static final int STRIDE = 13;
    /** Drawn at the vanilla player's 0.9375, as the infected. */
    static final float SCALE = 0.9375F;
    private static final String[] IDLES = {"look_back", "shake", "sniff", "shift", "look_back", "sniff"};
    private static final float HEAD_YAW = 75F, HEAD_PITCH = 30F;

    @Nullable private static DeerModel buck, doe;
    private static boolean failed;

    final String kind;
    final float meshScale;
    final ResourceLocation texture, blinkTexture, deadTexture;
    final String[] bones;
    final int[] parent;
    final Vector3f[] pivot;
    final int[] quadPart;
    final float[] v;
    private final Map<String, Clip> clips = new HashMap<>();
    /** Blocks one cycle covers, at the drawn scale. */
    private final float walkCycle, trotCycle, boundCycle;
    final float stompHit;
    private final int neck1, neck2, head, earL, earR, tail;
    /** The neck, head and ears: a grazing step keeps them where grazing has them (the gaits carry their own neck). */
    private final boolean[] headChain;
    /** Per leg (fore L, fore R, hind L, hind R): the bone the reach turns at the top, the joint it bends, the hoof. */
    private final int[][] legs;
    /** Fore to hind hooves along the body (px) and the middle between them (z). */
    private final float hoofSpan, hoofMidZ;
    private final Quaternionf[] base, layer, extra;

    private record Clip(float length, boolean loop, boolean smooth, float[] t, float[] lift, Quaternionf[][] rot) {}

    private DeerModel(String kind, JsonObject mesh, JsonObject clipJson) {
        this.kind = kind;
        meshScale = mesh.get("scale").getAsFloat();
        texture = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/entity/deer/" + kind + ".png");
        blinkTexture = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/entity/deer/" + kind + "_blink.png");
        deadTexture = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/entity/deer/" + kind + "_dead.png");
        JsonArray b = mesh.getAsJsonArray("bones");
        int n = b.size();
        bones = new String[n];
        parent = new int[n];
        pivot = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            JsonObject o = b.get(i).getAsJsonObject();
            bones[i] = o.get("name").getAsString();
            parent[i] = o.get("parent").getAsInt();
            if (parent[i] >= i) throw new IllegalArgumentException("deer mesh: bone " + bones[i] + " before its parent");
            JsonArray p = o.getAsJsonArray("pivot");
            pivot[i] = new Vector3f(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat());
        }
        JsonArray quads = mesh.getAsJsonArray("quad_parts"), verts = mesh.getAsJsonArray("vertices");
        quadPart = new int[quads.size()];
        for (int i = 0; i < quadPart.length; i++) quadPart[i] = quads.get(i).getAsInt();
        v = new float[verts.size()];
        int k = 0;
        for (JsonElement e : verts) v[k++] = e.getAsFloat();
        if (v.length != quadPart.length * 4 * STRIDE) throw new IllegalArgumentException("deer mesh: vertices vs quads");

        JsonArray clipBones = clipJson.getAsJsonArray("bones");
        if (clipBones.size() != n) throw new IllegalArgumentException("deer clips: other bones than the mesh");
        for (int i = 0; i < n; i++) {
            if (!clipBones.get(i).getAsString().equals(bones[i])) throw new IllegalArgumentException("deer clips: bone order");
        }
        for (Map.Entry<String, JsonElement> e : clipJson.getAsJsonObject("clips").entrySet()) {
            JsonObject c = e.getValue().getAsJsonObject();
            JsonArray keys = c.getAsJsonArray("keys");
            float[] t = new float[keys.size()], lift = new float[keys.size()];
            Quaternionf[][] rot = new Quaternionf[keys.size()][n];
            for (int i = 0; i < t.length; i++) {
                JsonObject key = keys.get(i).getAsJsonObject();
                t[i] = key.get("t").getAsFloat();
                lift[i] = key.get("lift").getAsFloat() * meshScale;
                JsonArray r = key.getAsJsonArray("rot");
                for (int j = 0; j < n; j++) {
                    JsonArray q = r.get(j).getAsJsonArray();
                    rot[i][j] = new Quaternionf(q.get(0).getAsFloat(), q.get(1).getAsFloat(), q.get(2).getAsFloat(), q.get(3).getAsFloat()).normalize();
                }
            }
            clips.put(e.getKey(), new Clip(c.get("length").getAsFloat(), c.get("loop").getAsBoolean(),
                    "smooth".equals(c.get("ease").getAsString()), t, lift, rot));
        }
        for (String need : new String[]{"stand", "graze", "alert", "stomp", "walk", "trot", "bound", "death", "look_back", "shake", "sniff", "shift"}) {
            if (!clips.containsKey(need)) throw new IllegalArgumentException("deer clips: no " + need);
        }
        float px = SCALE * meshScale / 16F;
        walkCycle = clipJson.get("walk_cycle_px").getAsFloat() * px;
        trotCycle = clipJson.get("trot_cycle_px").getAsFloat() * px;
        boundCycle = clipJson.get("bound_cycle_px").getAsFloat() * px;
        stompHit = clipJson.get("stomp_hit").getAsFloat();
        neck1 = index("neck_1");
        neck2 = index("neck_2");
        head = index("head");
        earL = index("ear.L");
        earR = index("ear.R");
        tail = index("tail");
        headChain = new boolean[n];
        for (int i : new int[]{neck1, neck2, head, earL, earR}) headChain[i] = true;
        legs = new int[][]{{index("fl_upper.L"), index("fl_cannon.L"), index("fl_hoof.L")},
                {index("fl_upper.R"), index("fl_cannon.R"), index("fl_hoof.R")},
                {index("hl_thigh.L"), index("hl_cannon.L"), index("hl_hoof.L")},
                {index("hl_thigh.R"), index("hl_cannon.R"), index("hl_hoof.R")}};
        float fore = pivot[legs[0][2]].z, hind = pivot[legs[2][2]].z;
        hoofSpan = hind - fore;
        hoofMidZ = (fore + hind) * 0.5F;
        base = newRot(n);
        layer = newRot(n);
        extra = newRot(n);
    }

    /** The buck's or the doe's model, loaded once; null (logged once) when the files are missing or broken. */
    @Nullable
    static DeerModel of(boolean doeKind) {
        if (buck == null && !failed) {
            try {
                JsonObject clips = read(CLIPS);
                buck = new DeerModel("buck", read("creatures/deer/buck_mesh.json"), clips);
                doe = new DeerModel("doe", read("creatures/deer/doe_mesh.json"), clips);
            } catch (IOException | RuntimeException e) {
                failed = true;
                buck = doe = null;
                ApocalypseFirstLight.LOGGER.error("[AFL Deer] deer meshes or clips failed to load", e);
            }
        }
        return doeKind ? doe : buck;
    }

    private static JsonObject read(String path) throws IOException {
        try (InputStream in = DeerModel.class.getClassLoader().getResourceAsStream("assets/" + ApocalypseFirstLight.MOD_ID + "/" + path)) {
            if (in == null) throw new IOException("missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    int index(String bone) {
        for (int i = 0; i < bones.length; i++) if (bones[i].equals(bone)) return i;
        throw new IllegalArgumentException("deer mesh: no bone " + bone);
    }

    Matrix4f[] newMatrices() {
        Matrix4f[] m = new Matrix4f[bones.length];
        for (int i = 0; i < m.length; i++) m[i] = new Matrix4f();
        return m;
    }

    private static Quaternionf[] newRot(int n) {
        Quaternionf[] q = new Quaternionf[n];
        for (int i = 0; i < q.length; i++) q[i] = new Quaternionf();
        return q;
    }

    // ---------------------------------------------------------------- per-deer layer state (client)

    private static final Map<DeerEntity, Anim> ANIM = new WeakHashMap<>();

    /** What the procedural layers remember between frames for one deer. Times in seconds of its own tick clock. */
    private static final class Anim {
        final Random random;
        float last = Float.NaN;
        float stand = 1F, graze, alert;
        @Nullable String idle;
        float idleStart, nextIdle;
        /** Ears now and wanted: tilt and turn, the left's then the right's (Blender degrees; the turn mirrored). */
        final float[] ear = new float[4], earWant = new float[4];
        final float[] earUntil = new float[2];
        float nextTwitch;
        float blinkUntil, nextBlink;
        float swishStart = -99F, nextSwish;
        float tailUp;
        /** The ground under each hoof (px above the entity's floor), followed so it does not jump block to block. */
        final float[] ground = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        /** Per hoof: held on the ground; where (model z) and at what distance walked it was put down; the slip left after
         *  letting go (px, faded out). */
        final boolean[] planted = new boolean[4];
        final float[] plantZ = new float[4], plantDist = new float[4], slip = new float[4];

        Anim(int id) {
            random = new Random(id * 0x9E3779B97F4A7C15L);
            nextIdle = 4F + random.nextFloat() * 10F;
            nextBlink = 1F + random.nextFloat() * 4F;
            nextSwish = 2F + random.nextFloat() * 6F;
            nextTwitch = random.nextFloat() * 2F;
        }
    }

    // ---------------------------------------------------------------- living

    private final float[] lift = new float[1];
    private final int[] into = new int[1];
    private final Vector3f at = new Vector3f();
    private final Quaternionf q = new Quaternionf(), id = new Quaternionf();

    /** Texture for this frame: the blink frame while the eyes are shut. */
    ResourceLocation texture(DeerEntity e, float partial) {
        Anim a = ANIM.get(e);
        return a != null && (e.tickCount + partial) / 20F < a.blinkUntil ? blinkTexture : texture;
    }

    /** The living deer's bone matrices this frame (model px, the model's frame). */
    void animate(DeerEntity e, float partial, Matrix4f[] out) {
        Anim a = ANIM.computeIfAbsent(e, d -> new Anim(d.getId()));
        float time = (e.tickCount + partial) / 20F;
        float dt = Float.isNaN(a.last) ? 0F : Mth.clamp(time - a.last, 0F, 0.1F);
        a.last = time;
        Random rnd = a.random;
        byte state = e.state();
        long now = e.level().getGameTime();
        boolean headDown = state == DeerEntity.GRAZE && DeerSchedule.headDown(e.getId(), e.stateAt(), now, into);
        float speed = e.walkSpeed(partial);
        boolean still = speed < 0.01F;

        // 1. the stance
        float wantStand = 0F, wantGraze = 0F, wantAlert = 0F;
        switch (state) {
            case DeerEntity.GRAZE -> {
                if (headDown) wantGraze = 1F;
                else wantStand = 1F;
            }
            case DeerEntity.ALERT -> wantAlert = 1F;
            case DeerEntity.FLEE -> {
                if (still) wantAlert = 1F;
                else wantStand = 1F;
            }
            default -> wantStand = 1F;
        }
        float fast = ease(dt, 0.08F), slow = ease(dt, wantGraze > a.graze ? 0.3F : 0.16F);
        a.alert += (wantAlert - a.alert) * (wantAlert > a.alert ? fast : slow);
        a.graze += (wantGraze - a.graze) * (wantAlert > 0F ? fast : slow);
        a.stand += (wantStand - a.stand) * slow;
        float total = a.stand + a.graze + a.alert;
        sample(clips.get("stand"), 0F, base, lift);
        float rootLift = lift[0];
        if (a.stand + a.graze > 1e-4F) {
            sample(clips.get("graze"), 0F, layer, lift);
            blend(base, layer, a.graze / (a.stand + a.graze));
        }
        if (total > 1e-4F && a.alert > 1e-4F) {
            sample(clips.get("alert"), 0F, layer, lift);
            blend(base, layer, a.alert / total);
        }

        // 3. the stamp, over the whole body (it stamps from the alert stance)
        float stomp = e.stompTime(partial);
        Clip stompClip = clips.get("stomp");
        if (stomp >= 0F && stomp <= stompClip.length) {
            sample(stompClip, stomp, layer, lift);
            blend(base, layer, Mth.clamp(Math.min(stomp / 0.08F, (stompClip.length - stomp) / 0.15F), 0F, 1F));
        }

        // 3. one-shot idles while it stands still with its head up
        boolean idleOk = still && a.alert < 0.3F && a.graze < 0.3F && (state == DeerEntity.STAND || state == DeerEntity.GRAZE);
        if (a.idle != null) {
            Clip c = clips.get(a.idle);
            float t = time - a.idleStart;
            if (t > c.length || (!idleOk && t < c.length - 0.25F)) {
                a.idle = null;
            } else {
                sample(c, t, layer, lift);
                float w = Mth.clamp(Math.min(t / 0.15F, (c.length - t) / 0.2F), 0F, 1F);
                for (int i = 0; i < bones.length; i++) {
                    nlerp(id.identity(), layer[i], w, q);
                    base[i].premul(q);
                }
            }
        } else if (idleOk && time >= a.nextIdle) {
            a.idle = IDLES[rnd.nextInt(IDLES.length)];
            a.idleStart = time;
            a.nextIdle = time + 8F + rnd.nextFloat() * 17F;
        } else if (!idleOk && time >= a.nextIdle) {
            a.nextIdle = time + 2F + rnd.nextFloat() * 4F;
        }

        // 2. the gaits
        float loco = smooth(0.004F, 0.03F, speed);
        float trot = smooth(0.10F, 0.15F, speed), bound = smooth(0.26F, 0.40F, speed);
        if (loco > 0F) {
            float dist = e.walkDistance(partial);
            sample(clips.get("walk"), dist / walkCycle, layer, lift);
            float gaitLift = lift[0];
            if (trot > 0F) {
                sample(clips.get("trot"), dist / trotCycle, extra, lift);
                blend(layer, extra, trot);
                gaitLift = Mth.lerp(trot, gaitLift, lift[0]);
            }
            if (bound > 0F) {
                sample(clips.get("bound"), dist / boundCycle, extra, lift);
                blend(layer, extra, bound);
                gaitLift = Mth.lerp(bound, gaitLift, lift[0]);
            }
            for (int i = 0; i < bones.length; i++) nlerp(base[i], layer[i], headChain[i] ? loco * (1F - a.graze) : loco, base[i]);
            rootLift = Mth.lerp(loco, rootLift, gaitLift);
        }

        // 4. the bite tug (head down) and the chew (head up between spells)
        if (headDown && into[0] >= DeerSchedule.BITE_FIRST) {
            float p = (into[0] - DeerSchedule.BITE_FIRST) % DeerSchedule.BITE_EVERY + partial;
            if (p < 8F) base[neck2].premul(blender(-8F * Mth.sin(Mth.PI * p / 8F) * a.graze, 0F, 0F, q));
        } else if (state == DeerEntity.GRAZE && still) {
            base[head].premul(blender(1.2F * Mth.sin(time * 10F) * (1F - a.graze), 0F, 0F, q));
        }

        // 4. ears: twitches when calm, forward when alert, laid back in flight (the bound clip lays them back itself)
        if (state == DeerEntity.FLEE && !still) {
            float back = 1F - bound;
            setEars(a, 26F * back, 8F * back, 26F * back, 8F * back);
        } else if (a.alert > 0.5F) {
            setEars(a, -10F, -22F, -10F, -22F);
        } else {
            for (int s = 0; s < 2; s++) {
                if (time >= a.earUntil[s]) {
                    a.earWant[s * 2] = 0F;
                    a.earWant[s * 2 + 1] = 0F;
                }
            }
            if (time >= a.nextTwitch) {
                a.nextTwitch = time + 1.2F + rnd.nextFloat() * 2.8F;
                int which = rnd.nextInt(3);   // left, right or both
                boolean flick = rnd.nextFloat() < 0.3F;
                for (int s = 0; s < 2; s++) {
                    if (which != 2 && which != s) continue;
                    a.earWant[s * 2] = flick ? 18F : -8F + rnd.nextFloat() * 23F;
                    a.earWant[s * 2 + 1] = flick ? 0F : -30F + rnd.nextFloat() * 60F;
                    a.earUntil[s] = time + (flick ? 0.12F : 0.3F + rnd.nextFloat() * 2.2F);
                }
            }
        }
        float earEase = ease(dt, 0.05F);
        for (int i = 0; i < 4; i++) a.ear[i] += (a.earWant[i] - a.ear[i]) * earEase;
        base[earL].premul(blender(a.ear[0], 0F, a.ear[1], q));
        base[earR].premul(blender(a.ear[2], 0F, -a.ear[3], q));

        // 4. tail: up in flight (the bound clip has it up itself), a swish now and then
        a.tailUp += ((state == DeerEntity.FLEE ? 1F : 0F) - a.tailUp) * ease(dt, 0.1F);
        float swish = time - a.swishStart;
        float side = swish >= 0F && swish < 0.6F ? 22F * Mth.sin(swish * Mth.TWO_PI / 0.3F) * (1F - swish / 0.6F) : 0F;
        if (time >= a.nextSwish) {
            a.swishStart = time;
            a.nextSwish = time + (a.alert > 0.5F ? 1.5F : 3F) + rnd.nextFloat() * 7F;
        }
        base[tail].premul(blender(150F * a.tailUp * (1F - bound), side, 0F, q));

        // 4. blink
        if (time >= a.nextBlink) {
            a.blinkUntil = time + 0.12F;
            a.nextBlink = time + 4F + rnd.nextFloat() * 4F;
        }

        matrices(base, rootLift, out);
        for (int i = 0; i < 4; i++) out[legs[i][2]].transformPosition(tip(i, hoof[i]));   // the hooves as animated, on flat ground
        ground(e, partial, a, dt, 1F - bound, out);
        plant(e, partial, a, dt, speed, out);

        // 4. the head follows the look over the neck (not with the head down grazing)
        float look = 1F - a.graze;
        if (look > 0.01F) {
            float yaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)),
                    -HEAD_YAW, HEAD_YAW) * look;
            float pitch = Mth.clamp(Mth.lerp(partial, e.xRotO, e.getXRot()), -HEAD_PITCH, HEAD_PITCH) * look * 0.6F;
            turn(out, neck1, yaw * 0.25F, 0F);
            turn(out, neck2, yaw * 0.3F, 0F);
            turn(out, head, yaw * 0.45F, pitch);
        }
    }

    private static void setEars(Anim a, float tiltL, float turnL, float tiltR, float turnR) {
        a.earWant[0] = tiltL;
        a.earWant[1] = turnL;
        a.earWant[2] = tiltR;
        a.earWant[3] = turnR;
        a.earUntil[0] = a.earUntil[1] = 0F;
    }

    /** Turns {@code bone} and everything on it about its posed pivot, in the model's frame (as the infected's head). */
    private void turn(Matrix4f[] out, int bone, float yaw, float pitch) {
        out[bone].transformPosition(at.set(pivot[bone]));
        q.rotationZYX(0F, yaw * Mth.DEG_TO_RAD, pitch * Mth.DEG_TO_RAD);
        Matrix4f turn = new Matrix4f().translation(at).rotate(q).translate(-at.x, -at.y, -at.z);
        for (int i = bone; i < bones.length; i++) {
            if (i == bone || descends(i, bone)) turn.mul(out[i], out[i]);
        }
    }

    // ---------------------------------------------------------------- the ground under the hooves

    @Nullable private static WeakReference<Level> groundLevel;
    @Nullable private static GroundView groundView;
    private final Vector3f[] hoof = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private final Vector3f p0 = new Vector3f(), p1 = new Vector3f(), tip = new Vector3f();
    private final Matrix4f shift = new Matrix4f();

    /** The ground as drawn and stood on, the client's copy (one view for the render thread, made again for a new level). */
    private static GroundView view(Level level) {
        if (groundView == null || groundLevel == null || groundLevel.get() != level) {
            groundLevel = new WeakReference<>(level);
            groundView = GroundView.of(level);
        }
        return groundView;
    }

    /**
     * Stands the animated deer on the ground as drawn (GroundView#surfaceBelow: Terrain Smooth's slopes, edited columns, any
     * block's top). The entity's box stands on the ground at its middle only and the deer is 1.4 blocks long, so on a slope
     * its fore hooves sank in and the hind ones hung in the air (user 2026-10-28: "卡脚"). Now:
     * <ol>
     *   <li>the ground is read under each animated hoof (followed over 0.06 s);</li>
     *   <li>the body pitches to the line between the fore pair's and the hind pair's ground and comes down as far as the
     *   lowest hoof needs (a straight leg cannot reach further);</li>
     *   <li>each leg bends (two bones in its side plane: shoulder or hip, then carpus or hock) so its hoof keeps its animated
     *   height over its own ground.</li>
     * </ol>
     * {@code weight} fades it out in a bound, the hooves in the air.
     */
    private void ground(DeerEntity e, float partial, Anim a, float dt, float weight, Matrix4f[] out) {
        if (weight <= 0.01F) return;
        GroundView view = view(e.level());
        double ex = Mth.lerp(partial, e.xo, e.getX()), ey = Mth.lerp(partial, e.yo, e.getY()), ez = Mth.lerp(partial, e.zo, e.getZ());
        float yaw = (180F - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)) * Mth.DEG_TO_RAD;
        float cs = Mth.cos(yaw), sn = Mth.sin(yaw), k = SCALE / 16F, follow = ease(dt, 0.06F);
        for (int i = 0; i < 4; i++) {
            // model px to the world as the renderer draws it: turned to the body, x flipped, SCALE / 16 a px
            float wx = -hoof[i].x * k, wz = hoof[i].z * k;
            double s = view.surfaceBelow(ex + wx * cs + wz * sn, ey + 1.0, ez - wx * sn + wz * cs);
            float h = Double.isNaN(s) ? 0F : Mth.clamp((float) (s - ey), -1F, 1F) / k;
            a.ground[i] = Float.isNaN(a.ground[i]) ? h : a.ground[i] + (h - a.ground[i]) * follow;
        }
        float fore = (a.ground[0] + a.ground[1]) * 0.5F, hind = (a.ground[2] + a.ground[3]) * 0.5F;
        // a step up a whole block would tip it over 50°: at most 25° (tan 0.47), the legs take the rest
        float mean = (fore + hind) * 0.5F, slope = Mth.clamp((fore - hind) / hoofSpan, -0.47F, 0.47F);
        float drop = 0F;
        for (int i = 0; i < 4; i++) drop = Math.max(drop, mean + slope * (hoofMidZ - hoof[i].z) - a.ground[i] - 0.4F);
        // the body: pitched about the ground between the hooves (fore up: a turn toward -y at -z), then raised or lowered
        shift.translation(0F, -(mean - drop) * weight, 0F).translate(0F, 24F, hoofMidZ)
                .rotateX(-(float) Math.atan(slope) * weight).translate(0F, -24F, -hoofMidZ);
        for (Matrix4f m : out) shift.mul(m, m);
        // the legs: each hoof back to its animated height over its own ground (model y is down, the floor at 24)
        for (int i = 0; i < 4; i++) {
            out[legs[i][2]].transformPosition(tip(i, tip));
            float want = 24F - a.ground[i] + (hoof[i].y - 24F);
            float y = Mth.lerp(weight, tip.y, want);
            if (Math.abs(y - tip.y) > 0.02F) reach(out, i, tip.z, y);
        }
    }

    /**
     * A hoof on the ground stays where it was put down until the clip lifts it (user 2026-10-28: "虽然脚有在动，但是我感觉不
     * 协调"): the body moves on at the entity's speed and the leg reaches back to the held point (the same two-bone reach),
     * so nothing slides. The bound's keys have no stance sweep of their own, so there this is what plants the hooves. A hoof
     * held more than 4 px off its clip lets go; the slip left at letting go fades out over 0.07 s instead of jumping.
     */
    private void plant(DeerEntity e, float partial, Anim a, float dt, float speed, Matrix4f[] out) {
        float dist = e.walkDistance(partial), k = SCALE / 16F, fade = 1F - ease(dt, 0.07F);
        for (int i = 0; i < 4; i++) {
            out[legs[i][2]].transformPosition(tip(i, tip));
            boolean down = hoof[i].y >= 23.65F && speed > 0.003F;   // within 0.35 px of the floor in the clip
            if (down) {
                if (!a.planted[i]) {
                    a.planted[i] = true;
                    a.plantZ[i] = tip.z + a.slip[i];
                    a.plantDist[i] = dist;
                    a.slip[i] = 0F;
                }
                // a held point stays in the world: the body going forward (-z) carries it back (+z) in the model
                float held = a.plantZ[i] + (dist - a.plantDist[i]) / k;
                if (Math.abs(held - tip.z) <= 4F) {
                    reach(out, i, held, tip.y);
                    continue;
                }
                a.planted[i] = false;
                a.slip[i] = Mth.clamp(held - tip.z, -4F, 4F);
            } else if (a.planted[i]) {
                a.planted[i] = false;
                a.slip[i] = Mth.clamp(a.plantZ[i] + (dist - a.plantDist[i]) / k - tip.z, -4F, 4F);
            }
            if (Math.abs(a.slip[i]) > 0.02F) {
                reach(out, i, tip.z + a.slip[i], tip.y);
                a.slip[i] *= fade;
            } else {
                a.slip[i] = 0F;
            }
        }
    }

    /** The hoof's tip at rest (on the floor under the hoof bone's pivot), into {@code into}. */
    private Vector3f tip(int leg, Vector3f into) {
        Vector3f p = pivot[legs[leg][2]];
        return into.set(p.x, 24F, p.z);
    }

    /**
     * Two bones in the leg's side plane (y, z): the top bone turns so the joint lands where the hoof can reach (y, z), bent
     * the way nearer the joint's place now; then the joint turns the hoof there.
     */
    private void reach(Matrix4f[] out, int leg, float tz, float ty) {
        int[] l = legs[leg];
        out[l[0]].transformPosition(p0.set(pivot[l[0]]));
        out[l[1]].transformPosition(p1.set(pivot[l[1]]));
        out[l[2]].transformPosition(tip(leg, tip));
        float a = len(p1.y - p0.y, p1.z - p0.z), b = len(tip.y - p1.y, tip.z - p1.z);
        if (a < 0.1F || b < 0.1F) return;
        float c = Mth.clamp(len(ty - p0.y, tz - p0.z), Math.abs(a - b) + 0.05F, a + b - 0.05F);
        float toward = angle(tz - p0.z, ty - p0.y);
        float bend = (float) Math.acos(Mth.clamp((a * a + c * c - b * b) / (2F * a * c), -1F, 1F));
        float j1 = toward + bend, j2 = toward - bend;
        float d1 = len(p0.y + a * Mth.cos(j1) - p1.y, p0.z + a * Mth.sin(j1) - p1.z);
        float d2 = len(p0.y + a * Mth.cos(j2) - p1.y, p0.z + a * Mth.sin(j2) - p1.z);
        turnX(out, l[0], p0, (d1 <= d2 ? j1 : j2) - angle(p1.z - p0.z, p1.y - p0.y));
        out[l[1]].transformPosition(p1.set(pivot[l[1]]));
        out[l[2]].transformPosition(tip(leg, tip));
        turnX(out, l[1], p1, angle(tz - p1.z, ty - p1.y) - angle(tip.z - p1.z, tip.y - p1.y));
    }

    /** Turns {@code bone} and everything on it about {@code at} round the model's x axis (rad; +: y toward z). */
    private void turnX(Matrix4f[] out, int bone, Vector3f at, float rad) {
        if (Math.abs(rad) < 1e-5F) return;
        Matrix4f turn = new Matrix4f().translation(at).rotateX(rad).translate(-at.x, -at.y, -at.z);
        for (int i = bone; i < bones.length; i++) {
            if (i == bone || descends(i, bone)) turn.mul(out[i], out[i]);
        }
    }

    /** The angle of (y, z) = (r cos, r sin), as a turn round x moves it. */
    private static float angle(float z, float y) {
        return (float) Math.atan2(z, y);
    }

    private static float len(float a, float b) {
        return (float) Math.sqrt(a * a + b * b);
    }

    // ---------------------------------------------------------------- dead

    /** The carcass: the death clip {@code seconds} after the death, then its last pose. */
    void dead(float seconds, Matrix4f[] out) {
        sample(clips.get("death"), seconds, base, lift);
        matrices(base, lift[0], out);
    }

    // ---------------------------------------------------------------- pose maths

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

    private void blend(Quaternionf[] into, Quaternionf[] with, float t) {
        if (t <= 0F) return;
        for (int i = 0; i < bones.length; i++) nlerp(into[i], with[i], t, into[i]);
    }

    /** A Blender-armature (x, y, z) degree turn (Euler YXZ, the export's convention) as a Java-model quaternion. */
    private static Quaternionf blender(float x, float y, float z, Quaternionf out) {
        return out.rotationY(-z * Mth.DEG_TO_RAD).rotateX(x * Mth.DEG_TO_RAD).rotateZ(y * Mth.DEG_TO_RAD);
    }

    private static float ease(float dt, float tau) {
        return 1F - (float) Math.exp(-dt / tau);
    }

    private static float smooth(float lo, float hi, float x) {
        float t = Mth.clamp((x - lo) / (hi - lo), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    /** Normalised linear blend the short way round. */
    private static void nlerp(Quaternionf a, Quaternionf b, float t, Quaternionf out) {
        float dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        float s = dot < 0F ? -1F : 1F;
        out.set(a.x + (s * b.x - a.x) * t, a.y + (s * b.y - a.y) * t, a.z + (s * b.z - a.z) * t, a.w + (s * b.w - a.w) * t).normalize();
    }

    // ---------------------------------------------------------------- drawing

    private static final Vector3f[] CORNER = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};

    /** The mesh skinned by {@code mats}, from the model-origin pose stack (model px / 16). */
    void draw(Matrix4f[] mats, PoseStack stack, VertexConsumer consumer, int light, int overlay) {
        PoseStack.Pose last = stack.last();
        Matrix4f mp = last.pose();
        Matrix3f mn = last.normal();
        Vector3f p = new Vector3f(), n = new Vector3f();
        SkinMesh.Sink sink = (x, y, z, u, v, nx, ny, nz) -> {
            mp.transformPosition(x / 16F, y / 16F, z / 16F, p);
            mn.transform(nx, ny, nz, n);
            consumer.vertex(p.x, p.y, p.z, 1F, 1F, 1F, 1F, u, v, overlay, light, n.x, n.y, n.z);
        };
        float[] uv = new float[8];
        Vector3f rest = new Vector3f(), part = new Vector3f();
        for (int qd = 0; qd < quadPart.length; qd++) {
            for (int k = 0; k < 4; k++) {
                int o = (qd * 4 + k) * STRIDE;
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
