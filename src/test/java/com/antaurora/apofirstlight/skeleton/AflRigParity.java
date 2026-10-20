package com.antaurora.apofirstlight.skeleton;

import com.antaurora.apofirstlight.skeleton.action.ActionPose;
import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.antaurora.apofirstlight.skeleton.action.PlayerActions;
import com.antaurora.apofirstlight.skeleton.action.PlayerProps;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Parity data for the AFL Rig Blockbench plugin (docs/dev/afl_rig_blockbench_plugin_v1.md).
 * <p>
 * The player actions are posed by the real ActionPose and HumanoidPoser, as ActionCheck poses them: standing, facing
 * ahead, no anchors. The output is every bone's skinning matrix and every prop's placement (and whether its key hides it),
 * at a few moments, view pitches, arm widths, a crouch and (gun actions) the own first person's longer reach, written as JSON. {@code afl_rig_test/holster_mag} (test
 * resources only) uses every phase 2 prop frame.
 * <p>
 * tools/blockbench/afl_rig_check.mjs poses the same scenes with the plugin's port and compares the two.
 * Run it with {@code ./gradlew aflRigParity}.
 * <p>
 * Java only, as the plugin does not mirror: on the rest pose a left-side player's props are the right side's reflected
 * (M P S, PlayerProps.Prop#reflection) for every frame kind; a mismatch stops the run.
 */
final class AflRigParity {
    private static final String[] ACTIONS = {"item/crowbar", "item/thermometer_armpit", "item/jerry_can", "item/nozzle_fill",
            "machine/generator_pull", "tool/crowbar_smash", "afl_rig_test/holster_mag", "gun/p9_01_draw", "gun/p9_01_holster",
            "gun/p9_01_idle", "gun/p9_01_sprint", "gun/p9_01_reload_tactical", "gun/p9_01_reload_empty", "gun/p9_01_inspect",
            "gun/p9_01_reload_tactical_ads", "gun/p9_01_reload_tactical_novice_fumble", "gun/p9_01_reload_empty_experienced"};
    private static final float[] TIMES = {0F, 0.05F, 0.1F, 0.2F, 0.3F, 0.4F, 0.5F, 0.65F, 0.8F, 0.9F, 1.0F, 1.12F, 1.2F};
    private static final float[] PITCHES = {20F, -30F, 50F};

    private AflRigParity() {
    }

    public static void main(String[] args) throws Exception {
        SkeletonRig rig = SkeletonRig.get();
        HumanoidPoser poser = new HumanoidPoser(rig, SkeletonClips.locomotion());
        StringBuilder out = new StringBuilder("{\"frames\": [\n");
        boolean first = true;
        float mirror = 0;
        for (String id : ACTIONS) {
            PlayerAction action = PlayerActions.get(id);
            if (action == null) throw new IllegalStateException("no action " + id);
            mirror = Math.max(mirror, mirrorCheck(rig, id, action));
            for (float pitch : PITCHES) for (boolean slim : new boolean[]{false, true}) for (boolean crouch : new boolean[]{false, true})
                for (boolean fp : new boolean[]{false, true}) {
                if (crouch && (slim || pitch != PITCHES[0])) continue;
                // the own first person's longer reach with a gun (GunStance#firstPersonReach): gun actions, standing, wide arms
                if (fp && (crouch || slim || !id.startsWith("gun/"))) continue;
                for (float t : TIMES) {
                    HumanoidPoser.Input in = PlayerSkeletonCheck.standing(slim, action.chestTwist, 0, pitch, false, crouch);
                    ActionPose.Moment m = new ActionPose.Moment();
                    m.action = action;
                    m.seconds = t;
                    m.view = ActionPose.viewFrame(in.eyeTarget, in.viewYaw, in.viewPitch);   // the plugin's: the eye, the look
                    if (fp) {
                        m.viewAhead = GunStance.firstPersonReach(pitch);
                        in.protraction += m.viewAhead;
                    }
                    boolean shows = ActionPose.body(in, rig, m);
                    SkeletonPose pose = new SkeletonPose(rig);
                    HumanoidPoser.Report report = poser.solveBody(in, pose);
                    in.right = HumanoidPoser.ArmMode.CLIP;
                    in.left = HumanoidPoser.ArmMode.CLIP;
                    if (shows) ActionPose.hands(in, pose, poser, m);
                    poser.solveArms(in, pose, report);
                    if (!first) out.append(",\n");
                    first = false;
                    out.append(String.format(Locale.ROOT, "{\"action\": \"%s\", \"t\": %s, \"pitch\": %s, \"slim\": %s, \"crouch\": %s, \"fp\": %s, \"world\": [",
                            id, t, pitch, slim, crouch, fp));
                    for (int b = 0; b < rig.size(); b++) out.append(b > 0 ? ", " : "").append(matrix(pose.world[b]));
                    out.append("], \"prop\": ").append(shows && m.prop != null ? matrix(m.prop) : "null").append(", \"props\": [");
                    for (int i = 0; i < action.props.size(); i++) {
                        Matrix4f p = shows && i < m.props.length ? m.props[i] : null;
                        out.append(i > 0 ? ", " : "").append(p == null ? "null" : matrix(p));
                    }
                    out.append("], \"hidden\": [");
                    for (int i = 0; i < action.props.size(); i++) out.append(i > 0 ? ", " : "").append(shows && i < m.propHidden.length && m.propHidden[i]);
                    out.append("]}");
                }
            }
        }
        out.append("\n]}\n");
        File file = new File(args.length > 0 ? args[0] : "build/afl_rig_parity.json");
        Files.writeString(file.toPath(), out, StandardCharsets.UTF_8);
        System.out.println(String.format(Locale.ROOT, "afl rig parity: %s; mirrored props on the rest pose off by at most %.6f px", file.getAbsolutePath(), mirror));
    }

    /** The worst distance (px) of a mirrored prop from the reflected unmirrored one, rest pose; throws past 0.001 px. */
    private static float mirrorCheck(SkeletonRig rig, String id, PlayerAction action) {
        float worst = 0;
        Vector3f[] probes = {new Vector3f(), new Vector3f(4, 0, 0), new Vector3f(0, 4, 0), new Vector3f(0, 0, 4)};
        for (float t : TIMES) {
            SkeletonPose pose = new SkeletonPose(rig);
            pose.update();
            ActionPose.Moment right = moment(action, t, false), left = moment(action, t, true);
            ActionPose.props(pose, right);
            ActionPose.props(pose, left);
            for (int i = 0; i < right.props.length; i++) {
                Matrix4f a = right.props[i], b = left.props[i];
                if ((a == null) != (b == null)) throw new IllegalStateException(id + " t=" + t + ": prop " + i + " placed on one side only");
                if (a == null || action.props.get(i).keys().stream().anyMatch(k -> k.frame().startsWith("anchor:"))) continue;
                Matrix4f s = PlayerProps.get(action.props.get(i).id()).reflection();
                if (s == null) continue;   // without a symmetry plane only the turn is reflected
                Matrix4f want = new Matrix4f().scale(-1, 1, 1).mul(a).mul(s);
                for (Vector3f p : probes) worst = Math.max(worst, want.transformPosition(p, new Vector3f()).distance(b.transformPosition(p, new Vector3f())));
            }
        }
        if (worst > 1e-3F) throw new IllegalStateException(id + ": mirrored props off by " + worst + " px");
        return worst;
    }

    private static ActionPose.Moment moment(PlayerAction action, float t, boolean mirror) {
        ActionPose.Moment m = new ActionPose.Moment();
        m.action = action;
        m.seconds = t;
        m.mirror = mirror;
        // a view frame turned about x alone reflects onto itself
        m.view = ActionPose.viewFrame(PlayerSkeletonCheck.toModel().transformPosition(PlayerSkeletonCheck.eyeO(false)).mul(16F), 0, 20);
        return m;
    }

    /** Column-major, as Matrix4f#get. */
    private static String matrix(Matrix4f m) {
        float[] a = m.get(new float[16]);
        StringBuilder s = new StringBuilder("[");
        for (int i = 0; i < 16; i++) s.append(i > 0 ? ", " : "").append(String.format(Locale.ROOT, "%.6f", a[i]));
        return s.append("]").toString();
    }
}
