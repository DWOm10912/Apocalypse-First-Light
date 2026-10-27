package com.antaurora.apofirstlight.skeleton;

import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.antaurora.apofirstlight.skeleton.action.PlayerActions;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.antaurora.apofirstlight.client.workbench.FurnitureBench.*;

/**
 * The furniture bench work's actions (docs/gameplay/furniture_bench_work_v1.md 3) through the game's poser on the vanilla
 * rig and on the default body's (rigs/afl_block_mesh_v2.json), the same scenes: ActionCheck's palm, wrist, twist, chest,
 * soles and first-person cover for each. The anchors as client/workbench/FurnitureWorkClient#anchors places them, for the
 * sample work points of tools/author-furniture-actions.mjs --report (a near right and a far left nail of a frame, the box's
 * top nail; the can and the wipes at their own stands); the player 20 cm behind the stand, stepped there by the approach.
 * Standalone, no game bootstrap. Run: gradlew furnitureActionCheck.
 */
public final class FurnitureActionCheck {
    private static final String[] ACTIONS = {"crosscut", "nail_set", "nail_hold", "nail_strike", "nail_glance", "nail_seat",
            "fit_hinge", "fit_knob", "fit_knob_wooden", "fit_escutcheon", "screw_start", "screw_hold", "screw_turn", "screw_slip",
            "screw_seat", "can_open", "wipe", "can_close"};
    /** The sample work points (bench frame x, top, z). */
    static final Map<String, double[]> SLOTS = new LinkedHashMap<>();

    static {
        SLOTS.put("near_right", new double[]{0.83, 0.94, 0.095});
        SLOTS.put("far_left", new double[]{1.57, 0.94, 0.405});
        SLOTS.put("box_top", new double[]{1.35, 1.05, 0.3725});
    }

    private FurnitureActionCheck() {
    }

    public static void main(String[] args) throws Exception {
        SkeletonRig vanilla = SkeletonRig.get(), b = SkeletonRig.blockMeshV2();
        HumanoidPoser pv = new HumanoidPoser(vanilla, SkeletonClips.locomotion()), pb = new HumanoidPoser(b, SkeletonClips.locomotion());
        int fv = run("vanilla", vanilla, pv), fb = run("Block-Mesh V2", b, pb);
        System.out.printf("%nfurniture actions: vanilla %d FAIL, Block-Mesh V2 %d FAIL%n", fv, fb);
    }

    private static int run(String label, SkeletonRig rig, HumanoidPoser poser) throws Exception {
        GunStance.useRig(rig);
        System.out.println("\n==== " + label);
        int fails = 0;
        for (String id : ACTIONS) {
            String action = "furniture/" + id;
            PlayerAction a = PlayerActions.get(action);
            if (a == null) {
                System.out.println("  FAIL " + action + ": no definition");
                fails++;
                continue;
            }
            boolean cut = id.equals("crosscut"), own = id.startsWith("can") || id.startsWith("wipe");
            Map<String, double[]> slots = cut || own ? Map.of("-", SLOTS.get("near_right")) : SLOTS;
            for (var slot : slots.entrySet()) {
                int n = cut ? 13 : 4;   // the crosscut each half second: the saw's strokes past the hand on the board
                for (int i = 0; i <= n; i++) {
                    float t = a.length * i / n;
                    Map<String, double[]> bench = cut ? crosscut(t) : assembly(id, slot.getValue());
                    double[] st = bench.get("stand");
                    // the player 20 cm behind the stand: render-origin blocks = bench frame less (stand - 0.2 z)
                    Map<String, Vector3f> anchors = new HashMap<>();
                    bench.forEach((k, v) -> anchors.put(k, new Vector3f((float) (v[0] - st[0]), (float) v[1], (float) (v[2] - st[2] + 0.2))));
                    String name = id + (slot.getKey().equals("-") ? "" : "@" + slot.getKey()) + "_" + String.format("%.2f", t);
                    fails += ActionCheck.one(rig, poser, action, name, t, anchors);
                }
            }
        }
        return fails;
    }

    /** The crosscut's anchors (FurnitureWorkClient#anchors) at its second, no off-cut on the pile yet. */
    static Map<String, double[]> crosscut(double t) {
        Map<String, double[]> m = new HashMap<>();
        double yaw = Math.toRadians(CUT_YAW);
        m.put("stand", new double[]{CUT_STAND_X, 0, CUT_STAND_Z});
        m.put("face", new double[]{CUT_STAND_X + 3 * Math.sin(yaw), 0, CUT_STAND_Z + 3 * Math.cos(yaw)});
        m.put("kerf", new double[]{CUT_X, CUT_TOP - 0.01, kerfZ(t)});
        m.put("hold", new double[]{CUT_HOLD_X + CUT_PIECE * (1 - slide(t)), CUT_HOLD_Y, (CUT_Z0 + CUT_Z1) / 2});
        double[] c = carried(t, 0);
        m.put("carry", new double[]{c[0], c[1] + 0.02, c[2]});
        return m;
    }

    /** The assembly's anchors (FurnitureWorkClient#anchors) for work at {@code slot}: the nail or screw set, not driven yet. */
    static Map<String, double[]> assembly(String id, double[] slot) {
        Map<String, double[]> m = new HashMap<>();
        boolean can = id.startsWith("can"), wipe = id.startsWith("wipe");
        double top = slot[1], z0 = 0.06, z1 = 0.46;
        double x = can || wipe ? 1.02 : slot[0], z = can || wipe ? 0.25 : slot[2];
        double sz = can ? STAND_Z : standZ(z), sx = can ? CAN_STAND : wipe ? WIPE_STAND : standX(x);
        m.put("stand", new double[]{sx, 0, sz});
        m.put("face", new double[]{sx, 0, sz + 3});
        m.put("press", wipe ? new double[]{WIPE_B + 0.22, top + 0.03, (z0 + z1) / 2}
                : new double[]{Math.min(x + PRESS_LEFT, 1.62), top + 0.03, Math.max(0.09, Math.min(z - PRESS_BACK, sz + PRESS_REACH))});
        m.put("head", new double[]{x, top + NAIL_LEN * 0.85, z});
        m.put("shead", new double[]{x, top + SCREW_LEN * 0.65, z});
        m.put("hw", new double[]{x - 0.038, top + 0.001, z});
        m.put("box", new double[]{BOX[0], BOX[1] + 0.13, BOX[2]});
        double[] mouth = mouth();
        m.put("can", mouth);
        m.put("cantop", new double[]{mouth[0], mouth[1] + 0.025, mouth[2]});
        m.put("canhold", new double[]{CAN[0] + 0.075, CAN[1] + 0.07, CAN[2]});
        m.put("lid", LID);
        m.put("stick_bench", STICK);
        m.put("stick_rest", new double[]{CAN[0] - 0.02, CAN[1] + 0.02, CAN[2]});
        m.put("brush_rest", new double[]{BRUSH_REST[0], BRUSH_REST[1] + BRUSH_LIFT, BRUSH_REST[2]});
        m.put("brush_can", new double[]{mouth[0], mouth[1] + BRUSH_LIFT, mouth[2]});
        for (int i = 0; i < 3; i++) {
            double bz = z0 + (z1 - z0) * (i + 0.5) / 3;
            m.put("w" + i + "a", new double[]{WIPE_A, top + WIPE_UP, bz});
            m.put("w" + i + "b", new double[]{WIPE_B, top + WIPE_UP, bz});
        }
        return m;
    }
}
