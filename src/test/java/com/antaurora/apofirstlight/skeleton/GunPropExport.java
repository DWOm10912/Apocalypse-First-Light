package com.antaurora.apofirstlight.skeleton;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * P9 draw / holster V1 (docs/dev/p9_draw_holster_actions_v1.md 2.1-2.3): a carried gun's player prop file
 * ({@code player_props/<gun>.json}) from its geo, animation, Vextris mesh and item model, so the prop is the gun as the game
 * holds and holsters it:
 * <ul>
 * <li>{@code scale}: GunStance.Carried#scale (its size on the body);</li>
 * <li>{@code bounds}: the bind-pose mesh as client/MaintenanceGunRendering#bounds collects it (its skipped bones skipped;
 * the mesh is in bone-pivot-local GeckoLib blocks);</li>
 * <li>points {@code grip_r}, {@code grip_l} (the hand anchors in {@code static_idle} at 0 s, the firing hand's
 * GunStance.Carried#handDrop lower; grip_l its supportDrop lower), {@code muzzle} (its anchor at rest);</li>
 * <li>{@code grips}: those anchor frames with the kind's grips (GunStance.Kind#gripRight / gripLeft);</li>
 * <li>{@code carry}: client/inventory/CarriedWeaponsLayer#thighFrame at rest (a pistol, the right thigh; its holster
 * size is the item model's thirdperson_righthand scale), in the prop's frame.</li>
 * </ul>
 * {@code ./gradlew gunPropExport} writes them; {@code --check} first compares instead (exit 1 on a difference).
 */
final class GunPropExport {
    private static final String NS = "apocalypse_firstlight", ROOT = "assets/" + NS + "/";
    /** client/inventory/CarriedWeaponsLayer HOLSTER_X, HOLSTER_MUZZLE_Y, HOLSTER_FRONT_Z (px); vanilla's right leg pivot (px). */
    private static final Vector3f HOLSTER = new Vector3f(3.25F, -7.15F, -0.98F), RIGHT_LEG = new Vector3f(-1.9F, 12F, 0F);
    /** client/MaintenanceGunRendering#copy: bones (and their children) left out of the bind pose, besides empty_old*. */
    private static final Set<String> SKIPPED = Set.of("reload_magazine", "additional_magazine", "lefthand", "righthand", "camera", "view", "positioning");

    private GunPropExport() {
    }

    public static void main(String[] args) throws IOException {
        boolean check = false;
        List<String> guns = new ArrayList<>();
        for (String a : args) {
            if (a.equals("--check")) check = true;
            else guns.add(a);
        }
        int differ = 0;
        for (String gun : guns) {
            String text = text(build(gun));
            Path file = Path.of("src/main/resources/" + ROOT + "player_props/" + gun + ".json");
            if (check) {
                boolean same = Files.exists(file) && Files.readString(file).replace("\r\n", "\n").equals(text);
                System.out.println(gun + (same ? ": up to date" : ": DIFFERS"));
                if (!same) differ++;
            } else {
                Files.writeString(file, text);
                System.out.println("wrote " + file);
            }
        }
        if (differ > 0) System.exit(1);
    }

    /** The prop file of a carried gun (id path). */
    static JsonObject build(String gun) throws IOException {
        GunStance.Carried carried = GunStance.carried(gun);
        if (carried == null) throw new IllegalArgumentException("not a carried gun: " + gun);
        GeoAnchorSampler sampler = GeoAnchorSampler.resource(NS, carried.asset());
        float scale = carried.scale();
        float[] e = bounds(sampler, carried.asset());
        // the hands where the stance puts them on the gun (GunStance.Carried#rightHand / #leftHand: their drops under the anchors)
        Matrix4f right = carried.rightHand(sampler.frame("right_hand_anchor", "static_idle", 0F));
        Matrix4f leftRest = sampler.frame("left_hand_anchor", "static_idle", 0F);
        Matrix4f left = carried.leftHand(leftRest, leftRest);
        Matrix4f muzzle = sampler.frame("muzzle_anchor", null, 0F);

        JsonObject o = new JsonObject();
        o.addProperty("about", "The " + gun + " as a player action's prop (docs/dev/p9_draw_holster_actions_v1.md 2.1-2.3), written by"
                + " ./gradlew gunPropExport (src/test/.../skeleton/GunPropExport): its bare GeckoLib frame (blocks), its size on the"
                + " body (GunStance), the hand anchors at static_idle 0 s (each hand's drop lower) with the kind's grips,"
                + " the thigh holster at rest. Do not edit.");
        o.add("items", array(gun));
        o.addProperty("gun", gun);
        o.addProperty("scale", round(scale));
        JsonArray b = new JsonArray();
        b.add(vec(new Vector3f(e[0], e[1], e[2])));
        b.add(vec(new Vector3f(e[3], e[4], e[5])));
        o.add("bounds", b);
        JsonObject points = new JsonObject();
        points.add("grip_r", vec(right.getTranslation(new Vector3f())));
        points.add("grip_l", vec(left.getTranslation(new Vector3f())));
        if (muzzle != null) points.add("muzzle", vec(muzzle.getTranslation(new Vector3f())));
        o.add("points", points);
        JsonObject grips = new JsonObject();
        grips.add("grip_r", grip(right, carried.kind().gripRight));
        grips.add("grip_l", grip(left, carried.kind().gripLeft));
        o.add("grips", grips);
        if (carried.kind() == GunStance.Kind.PISTOL) o.add("carry", thighCarry(gun, scale, e));
        return o;
    }

    /** The bind-pose mesh's bounds (gun blocks) {minX, minY, minZ, maxX, maxY, maxZ}. */
    static float[] bounds(GeoAnchorSampler sampler, String asset) throws IOException {
        JsonObject mesh = read(ROOT + "meshes/" + asset + ".aflmesh.json");
        if (!"bone_pivot_local_blocks".equals(mesh.get("coordinate_space").getAsString()))
            throw new IllegalStateException(asset + ": mesh coordinate space " + mesh.get("coordinate_space"));
        float[] e = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        Map<String, Matrix4f> frames = new HashMap<>();
        for (JsonElement p : mesh.getAsJsonArray("parts")) {
            String bone = p.getAsJsonObject().get("bone").getAsString();
            if (!drawn(sampler, bone)) continue;
            Matrix4f f = frames.computeIfAbsent(bone, k -> sampler.frame(k, null, 0F));
            for (JsonElement v : p.getAsJsonObject().getAsJsonArray("vertices")) {
                JsonArray a = v.getAsJsonArray();
                Vector3f q = f.transformPosition(new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()));
                e[0] = Math.min(e[0], q.x); e[1] = Math.min(e[1], q.y); e[2] = Math.min(e[2], q.z);
                e[3] = Math.max(e[3], q.x); e[4] = Math.max(e[4], q.y); e[5] = Math.max(e[5], q.z);
            }
        }
        if (!(e[3] > e[0])) throw new IllegalStateException(asset + ": no mesh in the bind pose");
        return e;
    }

    /** The bone is in the bind pose (MaintenanceGunRendering#copy keeps it and every bone above it). */
    private static boolean drawn(GeoAnchorSampler sampler, String bone) {
        if (!sampler.has(bone)) return false;
        for (String b = bone; b != null; b = sampler.parent(b)) if (b.startsWith("empty_old") || SKIPPED.contains(b)) return false;
        return true;
    }

    /**
     * CarriedWeaponsLayer#thighFrame at rest (gun blocks to model blocks: the right leg part, turned half round z as the worn
     * meshes, the muzzle's top edge to the holster's floor, muzzle down, the holster size) as the prop's placement on the
     * thigh: {@code {"bone": "thigh_r", "pos", "quat", "scale"}} (rest model px, its size over {@code scale}).
     */
    private static JsonObject thighCarry(String gun, float scale, float[] e) throws IOException {
        float hand = handScale(gun);
        Matrix4f g = new Matrix4f()
                .translate(RIGHT_LEG.x / 16F, RIGHT_LEG.y / 16F, RIGHT_LEG.z / 16F)
                .rotateZ((float) Math.PI)
                .translate(HOLSTER.x / 16F, HOLSTER.y / 16F, HOLSTER.z / 16F)
                .rotateX((float) Math.toRadians(-90))
                .scale(hand)
                .translate(-(e[0] + e[3]) / 2F, -e[4], -e[2]);
        float k = 16F * scale;
        Matrix4f rest = new Matrix4f().scaling(16F).mul(g).scale(1F / k, -1F / k, -1F / k);
        JsonObject c = new JsonObject();
        c.addProperty("bone", "thigh_r");
        c.add("pos", vec(rest.getTranslation(new Vector3f())));
        c.add("quat", quat(rest.getUnnormalizedRotation(new Quaternionf()).normalize()));
        c.addProperty("scale", round(rest.getScale(new Vector3f()).x));
        return c;
    }

    /** The item model's thirdperson_righthand scale (through forge:separate_transforms' base and the parents). */
    static float handScale(String gun) throws IOException {
        String model = "item/" + gun;
        for (int depth = 0; depth < 8 && model != null; depth++) {
            JsonObject m = read(ROOT + "models/" + model + ".json");
            if (m.has("display") && m.getAsJsonObject("display").has("thirdperson_righthand"))
                return m.getAsJsonObject("display").getAsJsonObject("thirdperson_righthand").getAsJsonArray("scale").get(0).getAsFloat();
            JsonObject next = m.has("base") ? m.getAsJsonObject("base") : m;
            String parent = next.has("parent") ? next.get("parent").getAsString() : null;
            model = parent == null || !parent.startsWith(NS + ":") ? null : parent.substring(NS.length() + 1);
        }
        throw new IllegalStateException(gun + ": no thirdperson_righthand scale");
    }

    private static JsonObject grip(Matrix4f anchor, GunStance.Grip turn) {
        JsonObject g = new JsonObject();
        g.add("pos", vec(anchor.getTranslation(new Vector3f())));
        g.add("quat", quat(anchor.getUnnormalizedRotation(new Quaternionf()).normalize()));
        g.add("grip", vec(new Vector3f(turn.pitch(), turn.yaw(), turn.roll())));
        return g;
    }

    private static JsonObject read(String path) throws IOException {
        try (InputStream in = GunPropExport.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IOException("missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static float round(float v) {
        float r = Math.round(v * 1e6F) / 1e6F;
        return r == 0F ? 0F : r;   // no -0
    }

    private static JsonArray vec(Vector3f v) {
        JsonArray a = new JsonArray();
        a.add(round(v.x));
        a.add(round(v.y));
        a.add(round(v.z));
        return a;
    }

    private static JsonArray quat(Quaternionf q) {
        if (q.w < 0) q.set(-q.x, -q.y, -q.z, -q.w);
        JsonArray a = new JsonArray();
        a.add(round(q.x));
        a.add(round(q.y));
        a.add(round(q.z));
        a.add(round(q.w));
        return a;
    }

    private static JsonArray array(String s) {
        JsonArray a = new JsonArray();
        a.add(s);
        return a;
    }

    /** As the hand-written prop files: two-space indent, arrays of numbers on one line. */
    static String text(JsonObject o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o, "");
        return sb.append('\n').toString();
    }

    private static void write(StringBuilder sb, JsonElement e, String indent) {
        if (e.isJsonObject()) {
            sb.append("{\n");
            var entries = new ArrayList<>(e.getAsJsonObject().entrySet());
            for (int i = 0; i < entries.size(); i++) {
                sb.append(indent).append("  \"").append(entries.get(i).getKey()).append("\": ");
                write(sb, entries.get(i).getValue(), indent + "  ");
                sb.append(i + 1 < entries.size() ? ",\n" : "\n");
            }
            sb.append(indent).append('}');
        } else if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            sb.append('[');
            for (int i = 0; i < a.size(); i++) {
                if (i > 0) sb.append(", ");
                write(sb, a.get(i), indent);
            }
            sb.append(']');
        } else if (e.getAsJsonPrimitive().isNumber()) {
            float f = e.getAsFloat();
            sb.append(f == Math.rint(f) ? String.format(Locale.ROOT, "%.1f", f) : Float.toString(f));
        } else {
            sb.append(e.toString());
        }
    }
}
