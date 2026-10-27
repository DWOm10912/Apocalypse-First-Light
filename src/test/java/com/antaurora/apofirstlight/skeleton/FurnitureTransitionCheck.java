package com.antaurora.apofirstlight.skeleton;

import com.antaurora.apofirstlight.skeleton.action.ActionPose;
import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.antaurora.apofirstlight.skeleton.action.PlayerActions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.antaurora.apofirstlight.client.workbench.FurnitureBench.glideSeconds;

/**
 * Docs furniture_bench_work_v1.md 12.9: the furniture work's action changes frame by frame (60 a second) through the game's
 * poser, on the vanilla rig and the default body's. A piece's run of actions: the crosscut, a nail set and driven, the next
 * nail elsewhere on the frame, a hinge screwed on, the can opened and a wipe, the tick with nothing to play, the next
 * piece's crosscut, the work stopped; the player standing still 20 cm behind the work's start, looking 30 deg down.
 * <p>
 * The own first-person camera each frame as client/skeleton/SkeletonActions puts it: on the posed eye, eased from the
 * player's own with the step; the view's yaw the posed body's facing, its pitch the action's. ActionPose, SkeletonPose
 * #blendFrom and FurnitureBench#glideSeconds are the game's own; the track (SkeletonActions#moment), the crossfade
 * (PlayerSkeleton#crossfade) and the stance's glide (FurnitureWorkClient#glide) need the game and are restated here.
 * The old rules for comparison (before 12.9, approximately): a new step blended in from the player, the nothing-to-play
 * tick no action, no glide, no crossfade, the camera on the posed eye at once, the way back over the blend-out.
 * <p>
 * Fails (the new rules only): the camera moving over MAX_MOVE cm or turning over MAX_TURN deg in a frame, or off the posed
 * eye in the middle of the work (the flash). Standalone, no game bootstrap. Run: gradlew furnitureTransitionCheck.
 */
public final class FurnitureTransitionCheck {
    private static final float DT = 1F / 60F, MAX_MOVE = 3F, MAX_TURN = 3F, LOOK = 30F;
    /** The player's feet (bench frame): 20 cm behind where the furniture's work starts from (WorkbenchWork FURNITURE_X, _Z). */
    private static final double[] PLAYER = {0.78, 0, -0.33};
    /** As SkeletonActions. */
    private static final double CROSSFADE = 0.3, LOOK_FADE = 0.5, RESTART = 0.3;
    /** The work stops this long before the run's end: the way back. */
    private static final float AFTER = 1F;

    /** One play: the action (null: the plan with nothing to play), how long (s), its work point, old chain (FurnitureWork before 12.9), a hold. */
    private record Play(String action, float seconds, String slot, boolean oldChain, boolean hold) {}

    private FurnitureTransitionCheck() {
    }

    public static void main(String[] args) throws Exception {
        List<Play> run = run();
        int fails = 0;
        for (SkeletonRig rig : new SkeletonRig[]{SkeletonRig.get(), SkeletonRig.blockMeshV2()}) {
            GunStance.useRig(rig);
            HumanoidPoser poser = new HumanoidPoser(rig, SkeletonClips.locomotion());
            String label = rig.profile == null ? "vanilla" : "Block-Mesh V2";
            simulate(label + ", old rules", rig, poser, run, false);
            fails += simulate(label + ", new rules", rig, poser, run, true);
        }
        System.out.printf("%nfurniture transitions: %d FAIL%n", fails);
    }

    private static List<Play> run() {
        List<Play> r = new ArrayList<>();
        r.add(play("crosscut", "-", false));
        r.add(play("nail_set", "near_right", false));
        for (int i = 0; i < 2; i++) {
            r.add(new Play("furniture/nail_hold", 0.75F, "near_right", true, true));
            r.add(play("nail_strike", "near_right", true));
        }
        r.add(new Play("furniture/nail_hold", 0.75F, "near_right", true, true));
        r.add(play("nail_seat", "near_right", true));
        r.add(play("nail_set", "far_left", true));   // the step's next nail: chained before too
        r.add(new Play("furniture/nail_hold", 0.75F, "far_left", true, true));
        r.add(play("nail_seat", "far_left", true));
        r.add(play("fit_hinge", "box_top", false));
        r.add(play("screw_start", "box_top", true));
        r.add(new Play("furniture/screw_hold", 0.7F, "box_top", true, true));
        r.add(play("screw_turn", "box_top", true));
        r.add(new Play("furniture/screw_hold", 0.7F, "box_top", true, true));
        r.add(play("screw_seat", "box_top", true));
        r.add(play("can_open", "-", false));
        r.add(play("wipe", "-", true));
        r.add(new Play(null, 0.05F, "-", false, false));   // the piece done: a plan with nothing to play, a tick
        r.add(play("crosscut", "-", false));   // the next piece
        return r;
    }

    private static Play play(String id, String slot, boolean oldChain) {
        PlayerAction a = PlayerActions.get("furniture/" + id);
        return new Play("furniture/" + id, a.length, slot, oldChain, false);
    }

    /** The old rules' action: the approach without its way back (the blend-out's). */
    private static final Map<String, PlayerAction> OLD = new HashMap<>();

    private static PlayerAction action(String id, boolean newRules) throws Exception {
        if (newRules) return PlayerActions.get(id);
        PlayerAction a = OLD.get(id);
        if (a != null) return a;
        String path = "/assets/apocalypse_firstlight/player_actions/" + id + ".json";
        try (var in = FurnitureTransitionCheck.class.getResourceAsStream(path)) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.has("approach")) json.getAsJsonObject("approach").remove("out");
            a = PlayerAction.parse(id, json);
        }
        OLD.put(id, a);
        return a;
    }

    private static int simulate(String label, SkeletonRig rig, HumanoidPoser poser, List<Play> run, boolean newRules) throws Exception {
        Matrix4f toModel = PlayerSkeletonCheck.toModel(), fromModel = new Matrix4f(toModel).invert();
        float total = AFTER;
        for (Play p : run) total += p.seconds();
        float stopAt = total - AFTER;
        // the track (SkeletonActions#moment)
        String id = null;
        PlayerAction action = null;
        double start = 0, shown = 0, stoppedAt = -1, switched = Double.NaN;
        float fromPitch = Float.NaN, lookPitch = Float.NaN;
        boolean stepping = false, everStepped = false;
        Vector3f residual = null;
        // the glide (FurnitureWorkClient#glide), the crossfade's poses (PlayerSkeleton#crossfade)
        double[] fromStand = null, fromFace = null, toStand = null, toFace = null;
        double glideAt = 0, glideLen = 0;
        SkeletonPose pose = new SkeletonPose(rig), last = new SkeletonPose(rig), from = new SkeletonPose(rig);
        boolean hasLast = false;
        Vector3f prevCam = null;
        float prevYaw = 0, prevPitch = 0;
        List<String> worst = new ArrayList<>();
        float maxMove = 0, maxTurn = 0;
        String maxMoveAt = "", maxTurnAt = "";
        int flashes = 0, fails = 0;
        // the own player's box out of the camera's view while the camera is on the posed eye (doc 12.10: vanilla's frustum
        // test then skipped the own body, no solve, the camera fell back in 3 frames, the body in view again: a flicker)
        Map<String, Integer> culled = new java.util.TreeMap<>();
        int steppingFrames = 0;
        float firstGap = Float.NaN, lastGap = Float.NaN;
        String flashAt = "";
        String lastPlay = null;
        float lastSeconds = 0;
        for (int frame = 0; frame * DT < total; frame++) {
            float now = frame * DT;
            // the live play, its second, its anchors (bench frame)
            Play cur = null;
            float acc = 0, local = 0;
            if (now < stopAt) for (Play p : run) {
                if (now < acc + p.seconds()) {
                    cur = p;
                    local = now - acc;
                    break;
                }
                acc += p.seconds();
            }
            String liveId = null;
            float seconds = 0;
            double liveStart = 0;
            String slot = "-";
            if (cur != null && cur.action() == null && newRules && lastPlay != null) {
                // the new rules hold the play before at its end (FurnitureWorkClient#frame, the tail)
                liveId = lastPlay;
                seconds = lastSeconds;
                liveStart = now - seconds;
            } else if (cur != null && cur.action() != null) {
                liveId = cur.action();
                PlayerAction a = action(liveId, newRules);
                seconds = cur.hold() && liveId.endsWith("nail_hold") ? raised(local) : Math.min(local, a.length - 0.01F);
                liveStart = now - seconds;
                if (!cur.hold()) liveStart = acc;
                slot = cur.slot();
                lastPlay = liveId;
                lastSeconds = seconds;
            }
            if (cur != null && cur.action() == null) slot = "-";
            boolean liveChain = newRules || cur != null && cur.oldChain();
            if (liveId != null) {
                boolean chained = liveChain && id != null && stoppedAt < 0;
                boolean again = newRules && chained && liveId.equals(id) && liveStart - start > RESTART;
                if (!liveId.equals(id) || stoppedAt >= 0 || again) {
                    boolean resumed = liveId.equals(id) && stoppedAt >= 0;
                    action = action(liveId, newRules);
                    id = liveId;
                    start = resumed ? start : now;
                    shown = resumed || chained ? shown : now;
                    if (chained && newRules) {
                        switched = now;
                        fromPitch = stepping ? lookPitch : Float.NaN;
                        if (hasLast) from.copyFrom(last);
                    } else if (!resumed) switched = Double.NaN;
                    stoppedAt = -1;
                }
                start = liveStart;
            } else if (action != null && stoppedAt < 0) stoppedAt = now;
            if (action != null && stoppedAt >= 0 && now - stoppedAt > (newRules ? ActionPose.fadeOut(action) : action.blendOut)) {
                action = null;
                id = null;
            }
            // the moment
            ActionPose.Moment m = null;
            if (action != null) {
                m = new ActionPose.Moment();
                m.action = action;
                m.seconds = (float) ((stoppedAt >= 0 ? stoppedAt : now) - start);
                if (action.loop == PlayerAction.Loop.TIME) m.seconds = ActionPose.loopSeconds(action, m.seconds, 0F);
                m.stopped = stoppedAt >= 0 ? (float) (now - stoppedAt) : -1F;
                m.age = (float) ((stoppedAt >= 0 ? stoppedAt : now) - shown);
                m.stepAge = m.age;   // old: the approach eased on the age (the uncommitted round before), new: the chain's
                Map<String, double[]> bench = id.endsWith("crosscut") ? FurnitureActionCheck.crosscut(m.seconds)
                        : FurnitureActionCheck.assembly(id.substring(id.indexOf('/') + 1), FurnitureActionCheck.SLOTS.getOrDefault(slot, FurnitureActionCheck.SLOTS.get("near_right")));
                if (newRules) {
                    double[] st = bench.get("stand"), fa = bench.get("face");
                    if (toStand == null) {
                        fromStand = toStand = st;
                        fromFace = toFace = fa;
                        glideAt = now;
                        glideLen = 0;
                    } else if (dist(st, toStand) > 1e-4 || dist(fa, toFace) > 1e-4) {
                        double k = smooth(glideLen <= 0 ? 1 : (now - glideAt) / glideLen);
                        fromStand = lerp(fromStand, toStand, k);
                        fromFace = lerp(fromFace, toFace, k);
                        toStand = st;
                        toFace = fa;
                        glideAt = now;
                        glideLen = glideSeconds(dist(fromStand, st));
                    }
                    double k = smooth(glideLen <= 0 ? 1 : (now - glideAt) / glideLen);
                    bench.put("stand", lerp(fromStand, toStand, k));
                    bench.put("face", lerp(fromFace, toFace, k));
                }
                Map<String, Vector3f> model = new HashMap<>();
                bench.forEach((k, v) -> model.put(k, toModel.transformPosition(new Vector3f((float) (v[0] - PLAYER[0]), (float) v[1],
                        (float) (v[2] - PLAYER[2]))).mul(16F)));
                m.anchors = model::get;
            }
            // the solve (PlayerSkeleton#solve)
            HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, 0, 0, LOOK, false, false);
            boolean shows = false;
            if (m != null) {
                m.view = ActionPose.viewFrame(in.eyeTarget, in.viewYaw, in.viewPitch);
                shows = ActionPose.body(in, rig, m);
            }
            HumanoidPoser.Report report = poser.solveBody(in, pose.reset());
            in.right = HumanoidPoser.ArmMode.CLIP;
            in.left = HumanoidPoser.ArmMode.CLIP;
            if (shows) ActionPose.hands(in, pose, poser, m);
            poser.solveArms(in, pose, report);
            float fade = Double.isNaN(switched) ? 1F : GunStance.smooth((float) ((now - switched) / CROSSFADE));
            if (shows && newRules && fade < 1F && hasLast) pose.blendFrom(from, new Matrix4f(), fade);
            last.copyFrom(pose);
            hasLast = true;
            // the camera (SkeletonActions#solved, #camera, #cameraRotation)
            float w = !shows ? 0F : newRules ? ActionPose.stepWeight(m) : ActionPose.weight(m);
            boolean anchored = shows && m.action.approach != null && m.anchors.apply(m.action.approach.anchor()) != null;
            boolean was = stepping;
            stepping = shows && w > 0 && (m.seconds > 0 || newRules) && anchored;
            if (!stepping || !was) residual = null;
            Vector3f real = PlayerSkeletonCheck.eyeO(false), cam = new Vector3f(real);
            float yaw = 0, pitch = LOOK;
            if (stepping) {
                Vector3f posed = fromModel.transformPosition(ActionPose.cameraEye(pose).div(16F));
                if (!everStepped) firstGap = posed.distance(real) * 100;
                lastGap = posed.distance(real) * 100;
                everStepped = true;
                // new: on the posed eye less the gap to vanilla's camera when the step began, eased out by the weight
                if (newRules) {
                    if (residual == null) residual = new Vector3f(posed).sub(real);
                    cam.set(posed).sub(new Vector3f(residual).mul(1F - Math.min(1F, w)));
                } else cam.set(posed);
                float p = ActionPose.pitch(m);
                double since = Double.isNaN(switched) ? LOOK_FADE : now - switched;
                if (newRules && !Float.isNaN(p) && !Float.isNaN(fromPitch) && since < LOOK_FADE)
                    p = fromPitch + (p - fromPitch) * GunStance.smooth((float) (since / LOOK_FADE));
                lookPitch = p;
                if (!Float.isNaN(lookPitch)) {
                    Vector3f f = fromModel.transformDirection(ActionPose.facing(pose));
                    float lookYaw = (float) Math.toDegrees(Math.atan2(-f.x, f.z));
                    float lw = newRules ? Math.min(1F, w) : ActionPose.weight(m);
                    yaw = lw * wrap(lookYaw);
                    pitch = LOOK + (lookPitch - LOOK) * lw;
                }
            } else if (everStepped && liveId != null) {
                flashes++;
                if (flashAt.isEmpty()) flashAt = String.format("%.2f s %s", now, liveId);
            }
            if (stepping) {
                steppingFrames++;
                if (outOfView(cam, yaw, pitch)) culled.merge(liveId == null ? "-" : liveId.substring(liveId.indexOf('/') + 1), 1, Integer::sum);
            }
            if (prevCam != null) {
                float move = cam.distance(prevCam) * 100, turn = Math.max(Math.abs(wrap(yaw - prevYaw)), Math.abs(pitch - prevPitch));
                String at = String.format("%.2f s %s", now, liveId == null ? "-" : liveId.substring(liveId.indexOf('/') + 1));
                if (move > maxMove) {
                    maxMove = move;
                    maxMoveAt = at;
                }
                if (turn > maxTurn) {
                    maxTurn = turn;
                    maxTurnAt = at;
                }
                if (move > MAX_MOVE || turn > MAX_TURN) {
                    if (worst.size() < 8) worst.add(String.format("    %s: moved %.1f cm, turned %.1f deg", at, move, turn));
                    if (newRules) fails++;
                }
            }
            prevCam = cam;
            prevYaw = yaw;
            prevPitch = pitch;
        }
        if (newRules && flashes > 0) fails += flashes;
        System.out.printf("%n==== %s%n  posed eye off the player's own: %.2f cm on the first stepping frame, %.2f cm on the last%n", label, firstGap, lastGap);
        System.out.printf("  camera per frame: most moved %.2f cm (%s), most turned %.2f deg (%s); off the posed eye mid-work %d frames%s%n",
                maxMove, maxMoveAt, maxTurn, maxTurnAt, flashes, flashes > 0 ? " (first " + flashAt + ")" : "");
        int out = culled.values().stream().mapToInt(Integer::intValue).sum();
        System.out.printf("  own box wholly out of the camera's view (vanilla would skip the body): %d of %d stepping frames %s%n",
                out, steppingFrames, culled);
        for (String s : worst) System.out.println(s);
        if (newRules) System.out.println(fails == 0 ? "  PASS" : "  FAIL " + fails + " frames");
        return fails;
    }

    /**
     * The player's own box as EntityRenderer#shouldRender tests it (0.6 x 1.8 at its feet, the origin, grown 0.5 each way) wholly outside the view from {@code cam} turned yaw / pitch
     * (Minecraft's, deg): 70 deg high, 16:9, the near plane at 0.05. Outside when all 8 corners are behind one plane.
     */
    static boolean outOfView(Vector3f cam, float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double[] f = {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
        double[] up = {-Math.sin(y) * Math.sin(p), Math.cos(p), Math.cos(y) * Math.sin(p)};
        double[] r = {f[1] * up[2] - f[2] * up[1], f[2] * up[0] - f[0] * up[2], f[0] * up[1] - f[1] * up[0]};
        double tv = Math.tan(Math.toRadians(35)), th = tv * 16 / 9;
        // inward normals: near, left, right, bottom, top
        double[][] n = {f, add(f, r, 1 / th), add(f, r, -1 / th), add(f, up, 1 / tv), add(f, up, -1 / tv)};
        double[] off = {0.05, 0, 0, 0, 0};
        for (int k = 0; k < n.length; k++) {
            boolean allOut = true;
            for (int i = 0; i < 8 && allOut; i++) {
                double cx = ((i & 1) == 0 ? -0.8 : 0.8) - cam.x, cy = ((i & 2) == 0 ? -0.5 : 2.3) - cam.y, cz = ((i & 4) == 0 ? -0.8 : 0.8) - cam.z;
                if (n[k][0] * cx + n[k][1] * cy + n[k][2] * cz >= off[k] * (k == 0 ? 1 : 0)) allOut = false;
            }
            if (allOut) return true;
        }
        return false;
    }

    private static double[] add(double[] a, double[] b, double s) {
        return new double[]{a[0] + b[0] * s, a[1] + b[1] * s, a[2] + b[2] * s};
    }

    /** The nail's hold raised by the needle (FurnitureWorkClient#raised: zone 0.5..0.7 of a 1.2 s turn), the hold's second. */
    private static float raised(float local) {
        double ph = (local / 1.2) % 1, z0 = 0.5, z1 = 0.7, k = ph < z0 ? ph / z0 : ph <= z1 ? 1 : 1 - (ph - z1) / (1 - z1);
        k = k * k * (3 - 2 * k);
        return (float) Math.max(0.02, k * 0.999);
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    private static double[] lerp(double[] a, double[] b, double k) {
        return new double[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }

    private static float wrap(float deg) {
        deg %= 360F;
        if (deg >= 180F) deg -= 360F;
        if (deg < -180F) deg += 360F;
        return deg;
    }
}
