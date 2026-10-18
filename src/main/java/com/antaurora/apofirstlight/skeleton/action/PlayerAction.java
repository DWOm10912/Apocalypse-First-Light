package com.antaurora.apofirstlight.skeleton.action;

import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Player actions V1 (docs/dev/player_actions_v1_plan.md 2; design docs/dev/player_skeleton_v1_design.md 10.2): one action
 * definition, {@code assets/apocalypse_firstlight/player_actions/<group>/<name>.json}. V1 reads the clip and its
 * parameter clip, layer, mask, priority, blend times, the step up to what the action works on ({@link Approach}), both
 * hands' target timelines, the props' placement timelines ({@link PropKey}, S2; several since AFL Rig phase 2), the first-person mode and the check
 * values; {@code events}, {@code locks} and {@code cancel} are kept as JSON for the later stages. Authored for the right
 * side; a left-side player plays it mirrored. Pure.
 */
public final class PlayerAction {
    public enum Layer { STANCE, ACTION, FULL_BODY }

    /**
     * First person (S2, docs/dev/player_actions_v1_plan.md 10): BODY, the skeleton draws the acting arms in the own view
     * (S1's actions); VIEW_MODEL, the own first-person view keeps the item's own view model (its camera bone, spout and
     * view turning are tied to it) and the action shows in third person and to the others only.
     */
    public enum FirstPerson { BODY, VIEW_MODEL }

    /**
     * A gun's stance loop (docs/dev/p9_stance_loops_v1.md): NONE, played once; TIME, over and over by the clock (standing
     * idle); STRIDE, once a stride, its time the locomotion's stride phase (sprinting). The gun stance plays these by its
     * state, not by their blend times.
     */
    public enum Loop { NONE, TIME, STRIDE }

    /**
     * A hand target: {@code pose}, {@code socket:<name>}, {@code block:<name>} (V1), {@code prop:<point>} (a point on the
     * action's first prop where its placement puts it, S2), {@code prop:<id>:<point>} (a point on the prop {@code id} of
     * {@link #props}, AFL Rig phase 2); the other kinds parse, resolve to none.
     */
    public record Target(String kind, String name) {
        public static final Target POSE = new Target("pose", "");

        static Target parse(String s) {
            int colon = s.indexOf(':');
            return colon < 0 ? new Target(s, "") : new Target(s.substring(0, colon), s.substring(colon + 1));
        }

        public boolean pose() { return kind.equals("pose"); }
    }

    /**
     * From {@code at} s the hand goes to {@code target}, over {@code blend} s (eased, about the palm). {@code pole}: the
     * elbow's way (chest frame), null for the side's default; {@code roll}: the hand's turn (deg) about the line from the
     * shoulder to the target.
     */
    public record HandKey(float at, Target target, float blend, Vector3f pole, float roll) {}

    /**
     * The body steps up to an anchor: it stands {@code distance} blocks from it (level, on the player's side) and
     * {@code side} blocks to its own left of the line to it (so the anchor comes in front of the right hand), at most
     * {@code max} blocks from where the player is, facing {@code face} (the anchor itself: square along that line). Look
     * only: the player stays where it is. The head (and the first-person view, on the posed eye) looks along the facing,
     * {@code pitch} deg down, going to {@code pitchHigh} as the action's parameter goes to 1 (JSON {@code pitch}: a number
     * or [at 0, at 1]; NaN: the player's own view). {@code in}: seconds of the action's time over which the step eases in
     * (0: with the action's blend-in), so an action whose time runs back (the jerry can's pour) steps back with it.
     */
    public record Approach(String anchor, float distance, float side, float max, String face, float pitch, float pitchHigh, float in) {}

    /**
     * The prop's placement from {@code at} s, eased over {@code blend} s from the one before: its {@code point}
     * (PlayerProps) sits at {@code pos} and it is turned {@code rot} (deg; about z, then y, then x, in the frame; the
     * prop's own axes as its item model's). {@code frame}: {@code chest} or {@code root} (pos: rest model px carried by that
     * bone), {@code shoulder_r} / {@code shoulder_l} (pos: px from that posed shoulder joint, turned as the root: within an
     * arm's reach standing or crouched, and level however the chest leans), {@code anchor:<name>} (pos: px from one of the
     * action's anchors, turned as the root: the jerry can's spout over the fill opening; a key whose anchor is unknown
     * holds the key before it). AFL Rig phase 2 (docs/dev/afl_rig_blockbench_plugin_v1.md 10): {@code bone:<bone>} (pos:
     * rest model px carried by that bone, as chest and root: the thigh; not an arm bone, which the IK places after the
     * props), {@code socket:<name>} (pos: px from that body socket, turned as its bone: the holster on the right thigh),
     * {@code prop:<id>} (pos: px in an earlier prop's model frame, turned from it: a magazine seated in the pistol rides
     * with it). {@code carry} (a gun prop, docs/dev/p9_draw_holster_actions_v1.md 2.3): the gun at its carry mount as the
     * body's carried-weapons layer draws it, its size there included (ActionPose.Moment#carry); pos (px) and rot turn it
     * about the key's point in that frame, normally 0. The hands reach for it with {@code prop:} targets. {@code view}
     * (docs/dev/p9_stance_loops_v1.md): pos px from the eye (the camera's), turned as the view (yaw and pitch, with the first
     * person's sway and recoil), axes as the rest model's: a pistol held out before the view (ActionPose.Moment#view). {@code hidden}:
     * from this key on the prop is not drawn (its placement still counts: hands, props riding on it).
     */
    public record PropKey(float at, String frame, String point, Vector3f pos, Vector3f rot, float blend, boolean hidden) {}

    /** One prop of the action: its PlayerProps id, placement timeline, and the second from which it is drawn open (NaN). */
    public record PropTrack(String id, List<PropKey> keys, float openFrom) {}

    /**
     * An anchor the action places from the player's view (S2: the crowbar's swing, where nothing in the world gives one):
     * {@code offset} blocks (right, up, ahead) from the eye, on the view's level frame or, {@code look}, along the look
     * (ahead the look itself, up the screen's up). ActionPose#viewAnchor. A left-side player's right is its left.
     * {@code first_person}: the offset in the player's own first-person view instead, or null (the same): the crowbar's
     * wind-up goes up behind the shoulder, which the own camera would see only as the bar sweeping through it.
     */
    public record ViewAnchor(String name, boolean look, Vector3f offset, Vector3f firstPerson) {}

    public final String id;
    /** Clip keys ({@code <group>.<name>}), or null. */
    public final String clip, paramClip;
    public final Layer layer;
    public final String mask;
    public final int priority;
    public final float blendIn, blendOut;
    public final Approach approach;
    public final List<HandKey> right, left;
    public final FirstPerson firstPerson;
    /** JSON {@code loop} ({@code "time"}, {@code "stride"}) and the loop's {@code length} (s; NaN: the clip's). */
    public final Loop loop;
    public final float length;
    /**
     * JSON {@code hip_fire}: an idle loop that is the gun's hip-fire stance (docs/dev/p9_stance_loops_v1.md: the gun held out
     * before the view, its muzzle on the crosshair): a shot does not raise the gun from it, and it is brought back toward
     * the eye where the arms are too short, as the raised stance is.
     */
    public final boolean hipFire;
    /**
     * JSON {@code view_pitch} [up, down] (deg, - = up), or null: how far up and down its {@code view} frame follows the
     * look's pitch (docs/dev/p9_stance_loops_v1.md: past it the arms cannot hold the gun on the view, it would go into the
     * chest); eased over the last ActionPose#VIEW_KNEE.
     */
    public final float[] viewPitch;
    /**
     * JSON {@code chest_twist} (deg, + = the chest right of the view): a gun stance loop's bladed body (SkeletonGunStance
     * #chestTwist, under its weight; docs/dev/p9_stance_loops_v1.md); the plugin and the checks pose it too. 0: square.
     */
    public final float chestTwist;
    /**
     * The props, JSON {@code prop} (one) or {@code props} (a list, AFL Rig phase 2: the pistol and its magazines), in
     * order: a prop placed on another ({@code prop:<id>} frame) comes after it. The first is the held item.
     */
    public final List<PropTrack> props;
    /** The first prop (PlayerProps id) and its placement timeline, or null / empty. */
    public final String prop;
    public final List<PropKey> propKeys;
    /** From this second on the first prop is drawn open (PlayerProps.Prop#openBlock: the jerry can's cap swung off), or NaN. */
    public final float propOpenFrom;
    public final List<ViewAnchor> viewAnchors;
    /** Kept for later stages and the checks. */
    public final JsonArray events;
    public final JsonObject check;

    private PlayerAction(String id, JsonObject json) {
        this.id = id;
        int format = json.has("format_version") ? json.get("format_version").getAsInt() : 1;
        if (format != 1) throw new IllegalArgumentException("player action " + id + ": format_version " + format);
        clip = string(json, "clip");
        paramClip = string(json, "param_clip");
        layer = Layer.valueOf(json.has("layer") ? json.get("layer").getAsString().toUpperCase() : "ACTION");
        mask = json.has("mask") ? json.get("mask").getAsString() : "upper";
        priority = json.has("priority") ? json.get("priority").getAsInt() : 50;
        JsonObject blend = json.has("blend") ? json.getAsJsonObject("blend") : new JsonObject();
        blendIn = blend.has("in") ? blend.get("in").getAsFloat() : 0.25F;
        blendOut = blend.has("out") ? blend.get("out").getAsFloat() : 0.25F;
        if (json.has("approach")) {
            JsonObject a = json.getAsJsonObject("approach");
            approach = new Approach(a.get("anchor").getAsString(), a.get("distance").getAsFloat(),
                    a.has("side") ? a.get("side").getAsFloat() : 0F, a.get("max").getAsFloat(),
                    a.has("face") ? a.get("face").getAsString() : a.get("anchor").getAsString(),
                    pitch(a, 0), pitch(a, 1), a.has("in") ? a.get("in").getAsFloat() : 0F);
        } else approach = null;
        JsonObject hands = json.has("hands") ? json.getAsJsonObject("hands") : new JsonObject();
        right = keys(id, hands, "right");
        left = keys(id, hands, "left");
        firstPerson = json.has("first_person") ? FirstPerson.valueOf(json.get("first_person").getAsString().toUpperCase()) : FirstPerson.BODY;
        loop = json.has("loop") ? Loop.valueOf(json.get("loop").getAsString().toUpperCase()) : Loop.NONE;
        length = json.has("length") ? json.get("length").getAsFloat() : Float.NaN;
        hipFire = json.has("hip_fire") && json.get("hip_fire").getAsBoolean();
        chestTwist = json.has("chest_twist") ? json.get("chest_twist").getAsFloat() : 0F;
        if (json.has("view_pitch")) {
            JsonArray v = json.getAsJsonArray("view_pitch");
            viewPitch = new float[]{v.get(0).getAsFloat(), v.get(1).getAsFloat()};
            if (!(viewPitch[0] < viewPitch[1])) throw new IllegalArgumentException("view_pitch: up " + viewPitch[0] + " not above down " + viewPitch[1]);
        } else {
            viewPitch = null;
        }
        if (loop != Loop.NONE && !(length > 0) && clip == null) throw new IllegalArgumentException("player action " + id + ": a loop without a length or clip");
        if (json.has("prop") && json.has("props")) throw new IllegalArgumentException("player action " + id + ": both prop and props");
        List<PropTrack> tracks = new ArrayList<>();
        if (json.has("prop")) tracks.add(track(id, json.getAsJsonObject("prop"), tracks));
        if (json.has("props")) for (JsonElement e : json.getAsJsonArray("props")) tracks.add(track(id, e.getAsJsonObject(), tracks));
        props = List.copyOf(tracks);
        PropTrack first = props.isEmpty() ? null : props.get(0);
        prop = first == null ? null : first.id();
        propKeys = first == null ? List.of() : first.keys();
        propOpenFrom = first == null ? Float.NaN : first.openFrom();
        List<ViewAnchor> view = new ArrayList<>();
        if (json.has("view_anchors")) for (var e : json.getAsJsonObject("view_anchors").entrySet()) {
            JsonObject a = e.getValue().getAsJsonObject();
            view.add(new ViewAnchor(e.getKey(), a.has("look") && a.get("look").getAsBoolean(), vector(a, "offset"),
                    a.has("first_person") ? vector(a, "first_person") : null));
        }
        viewAnchors = List.copyOf(view);
        events = json.has("events") ? json.getAsJsonArray("events") : new JsonArray();
        check = json.has("check") ? json.getAsJsonObject("check") : new JsonObject();
    }

    public static PlayerAction parse(String id, JsonObject json) {
        return new PlayerAction(id, json);
    }

    /** The index of the prop {@code propId} in {@link #props}, or -1. */
    public int propIndex(String propId) {
        for (int i = 0; i < props.size(); i++) if (props.get(i).id().equals(propId)) return i;
        return -1;
    }

    /** The bones the action's clips may move (V1 names: full, upper, arms, arm_r, arm_l, legs), for this rig. */
    public boolean[] mask(SkeletonRig rig, boolean mirror) {
        boolean[] m = new boolean[rig.size()];
        String side = mask;
        if (mirror && side.equals("arm_r")) side = "arm_l";
        else if (mirror && side.equals("arm_l")) side = "arm_r";
        int[] armR = {rig.upperArmR, rig.forearmR, rig.handR}, armL = {rig.upperArmL, rig.forearmL, rig.handL};
        switch (side) {
            case "full" -> java.util.Arrays.fill(m, true);
            case "upper" -> { set(m, armR); set(m, armL); set(m, rig.chest, rig.head); }
            case "arms" -> { set(m, armR); set(m, armL); }
            case "arm_r" -> set(m, armR);
            case "arm_l" -> set(m, armL);
            case "legs" -> set(m, rig.root, rig.pelvis, rig.thighR, rig.shinR, rig.footR, rig.thighL, rig.shinL, rig.footL);
            default -> throw new IllegalArgumentException("player action " + id + ": mask " + mask);
        }
        return m;
    }

    private static void set(boolean[] m, int... bones) {
        for (int b : bones) m[b] = true;
    }

    private static List<HandKey> keys(String id, JsonObject hands, String side) {
        List<HandKey> out = new ArrayList<>();
        if (!hands.has(side)) return out;
        float last = -1;
        for (JsonElement e : hands.getAsJsonArray(side)) {
            JsonObject k = e.getAsJsonObject();
            float at = k.has("at") ? k.get("at").getAsFloat() : 0F;
            if (at < last) throw new IllegalArgumentException("player action " + id + ": " + side + " keys out of order");
            last = at;
            Vector3f pole = null;
            if (k.has("pole")) {
                JsonArray p = k.getAsJsonArray("pole");
                pole = new Vector3f(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat());
            }
            out.add(new HandKey(at, Target.parse(k.get("target").getAsString()), k.has("blend") ? k.get("blend").getAsFloat() : 0.2F,
                    pole, k.has("roll") ? k.get("roll").getAsFloat() : 0F));
        }
        return List.copyOf(out);
    }

    /** One prop object ({@code {"id", "keys", "open_from"}}); {@code before}: the props listed before it. */
    private static PropTrack track(String id, JsonObject prop, List<PropTrack> before) {
        String propId = prop.get("id").getAsString();
        if (before.stream().anyMatch(t -> t.id().equals(propId))) throw new IllegalArgumentException("player action " + id + ": prop " + propId + " twice");
        List<PropKey> out = new ArrayList<>();
        float last = -1;
        for (JsonElement e : prop.getAsJsonArray("keys")) {
            JsonObject k = e.getAsJsonObject();
            float at = k.has("at") ? k.get("at").getAsFloat() : 0F;
            if (at < last) throw new IllegalArgumentException("player action " + id + ": prop keys out of order");
            last = at;
            String frame = k.has("frame") ? k.get("frame").getAsString() : "chest";
            boolean known = List.of("chest", "root", "shoulder_r", "shoulder_l", "carry", "view").contains(frame) || frame.startsWith("anchor:")
                    || frame.startsWith("bone:") || frame.startsWith("socket:")
                    || frame.startsWith("prop:") && before.stream().anyMatch(t -> t.id().equals(frame.substring(5)));
            if (!known) throw new IllegalArgumentException("player action " + id + ": prop frame " + frame
                    + (frame.startsWith("prop:") ? " (a prop listed before this one)" : ""));
            out.add(new PropKey(at, frame, k.get("point").getAsString(), vector(k, "pos"), vector(k, "rot"),
                    k.has("blend") ? k.get("blend").getAsFloat() : 0.3F, k.has("hidden") && k.get("hidden").getAsBoolean()));
        }
        if (out.isEmpty()) throw new IllegalArgumentException("player action " + id + ": a prop without keys");
        return new PropTrack(propId, List.copyOf(out), prop.has("open_from") ? prop.get("open_from").getAsFloat() : Float.NaN);
    }

    private static Vector3f vector(JsonObject o, String key) {
        if (!o.has(key)) return new Vector3f();
        JsonArray a = o.getAsJsonArray(key);
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    private static float pitch(JsonObject approach, int end) {
        if (!approach.has("pitch")) return Float.NaN;
        JsonElement p = approach.get("pitch");
        return p.isJsonArray() ? p.getAsJsonArray().get(end).getAsFloat() : p.getAsFloat();
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }
}
