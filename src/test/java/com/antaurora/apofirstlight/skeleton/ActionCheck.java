package com.antaurora.apofirstlight.skeleton;

import com.antaurora.apofirstlight.skeleton.action.ActionPose;
import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.antaurora.apofirstlight.skeleton.action.PlayerActions;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Player actions V1 offline checks (docs/dev/player_actions_v1_plan.md 5): each action posed by the real ActionPose and
 * HumanoidPoser at a few moments, against its scene (anchors as the game places them, the player at the render origin
 * facing +z, so left is +x):
 * <ul>
 * <li>the palm on its target (px), the wrist bend, the elbow and the forearm's twist (deg);</li>
 * <li>the arms' centre lines outside the chest box (px; the skin is 1.5-2 px thick around them);</li>
 * <li>planted soles on the ground (px), the body's step (blocks);</li>
 * <li>first person (the posed eye, the view as client/skeleton/SkeletonActions turns it): how much of the screen each arm
 * covers, and the nearest skin.</li>
 * </ul>
 * Pictures: {@code AFL_SNAP_ACTIONS=<out dir> ./gradlew skeletonCheck} (first person from the posed eye, four third-person
 * cameras; yellow: the anchors, magenta: the IK wrist targets, green: the posed wrists).
 */
final class ActionCheck {
    private static final float PX = 1F / 16F;
    /** The thickest arm's half width (px): a centre line closer than this to the chest box puts skin into it. */
    private static final float ARM_HALF = 2F;

    /** The arm's half thickness: vanilla's box, or the Block-Mesh V2 arm at the shoulder (1.5 px). */
    private static float armHalf(SkeletonRig rig) {
        return rig.profile != null ? 1.5F : ARM_HALF;
    }
    private static final float HAND_ERR = 0.6F, WRIST_MAX = 50F, SOLE_ERR = 0.6F, COVER_MAX = 0.4F;
    /** Forearm twist (deg): the box forearm shows any twist as a rope (in-game round 1, 2026-10-16: the thermometer's 90). */
    private static final float TWIST_MAX = 30F;
    private static int failures;
    /** client/PortableGeneratorPull BRACE (structure px x, z). */
    private static final float BRACE_X = 13.2F, BRACE_Z = 12.4F;

    private ActionCheck() {
    }

    /**
     * A scene: seconds into the action, its parameter, the anchors (render-origin blocks), what the player looked at before
     * (null: 20 deg down; sets the view the action starts from), planted feet, crouched; the own first person's longer reach
     * with a gun (GunStance#firstPersonReach) and the look's pitch (NaN: as {@code look} says).
     */
    private record Scene(String action, String name, float seconds, float param, Map<String, Vector3f> anchors, Vector3f look, boolean planted,
                         boolean crouch, boolean mirror, float age, boolean fp, float pitch) {
        Scene(String action, String name, float seconds, float param, Map<String, Vector3f> anchors, Vector3f look, boolean planted, boolean crouch) {
            this(action, name, seconds, param, anchors, look, planted, crouch, false, Float.NaN);
        }

        Scene(String action, String name, float seconds, float param, Map<String, Vector3f> anchors, Vector3f look, boolean planted,
              boolean crouch, boolean mirror, float age) {
            this(action, name, seconds, param, anchors, look, planted, crouch, mirror, age, false, Float.NaN);
        }

        /** A gun action in the own first person, looking {@code pitch} down. */
        static Scene firstPerson(String action, String name, float seconds, float pitch, boolean crouch) {
            return new Scene(action, name, seconds, 0, Map.of(), null, false, crouch, false, Float.NaN, true, pitch);
        }
    }

    /** client/JerryCanPourView LOOK_OVER: the locked pour view aims this far over the opening (blocks). */
    private static final float POUR_LOOK_OVER = 0.1F;
    /** client/JerryCanPourView PITCH_MAX: the locked pour view looks down at most this far (deg; the opening shows lower). */
    private static final float POUR_PITCH_MAX = 50F;
    /** First person: visible skin nearer the eye than this (blocks) fills the screen as a slab. */
    private static final float SKIN_NEAR = 0.2F;

    /** A prop's surface (model px) closer than this to the body boxes is in the skin. */
    private static final float PROP_CLEAR = 0F;

    static int run(SkeletonRig rig, HumanoidPoser poser, String snapDir) throws Exception {
        failures = 0;
        System.out.println("\nplayer actions (palm off target <= " + HAND_ERR + " px, wrist <= " + WRIST_MAX + " deg, forearm twist <= " + TWIST_MAX + " deg, arm centre lines >= "
                + ARM_HALF + " px outside the chest box, planted soles within " + SOLE_ERR + " px of the ground, first person: an arm on <= "
                + Math.round(COVER_MAX * 100) + "% of the screen)");
        for (Scene sc : scenes()) check(rig, poser, sc, snapDir);
        return failures;
    }

    /**
     * One scene of another check's (FurnitureActionCheck): the action at its second, fully blended in, standing planted
     * after a look 20 deg down; the anchors in render-origin blocks. The failures it adds.
     */
    static int one(SkeletonRig rig, HumanoidPoser poser, String action, String name, float seconds, Map<String, Vector3f> anchors) throws Exception {
        int before = failures;
        check(rig, poser, new Scene(action, name, seconds, 0, anchors, null, true, false, false, 1F), null);
        return failures - before;
    }

    private static Scene[] scenes() {
        // recoil starter (structure px, block/PortableDieselGeneratorBlock HANDLE_REST, PULL; client/PortableGeneratorPull
        // BRACE): the holder stands on the structure's +x side facing -x, so structure -x = ahead (+z), +z = left (+x)
        float d = 1.2F;
        Vector3f handle = new Vector3f(0, 5.542F * PX, d);
        Vector3f pull = new Vector3f(-1.617F * PX, 4.558F * PX, -7.352F * PX);
        Vector3f brace = new Vector3f(handle).add((BRACE_Z - 7.135F) * PX, (10.784F - 5.542F) * PX, (13.702F - BRACE_X) * PX);
        java.util.List<Scene> list = new java.util.ArrayList<>();
        for (float v : new float[]{0F, 0.5F, 1F}) {
            Map<String, Vector3f> a = new HashMap<>();
            a.put("handle_rest", handle);
            a.put("pull_grip", new Vector3f(pull).mul(v).add(handle));
            a.put("brace", brace);
            list.add(new Scene("machine/generator_pull", "pull_" + Math.round(v * 100), 1.0F, v, a, handle, true, false));
        }
        // fuel nozzle (the holder crouches, client/NozzleFillView): a car filler (0.9 high) and a jerry can on the ground
        // (0.35 high), the grip 0.1 over and 0.12 back
        for (float h : new float[]{0.9F, 0.35F}) {
            Map<String, Vector3f> a = new HashMap<>();
            Vector3f opening = new Vector3f(0.1F, h, 1.1F);
            a.put("opening", opening);
            a.put("nozzle_grip", new Vector3f(opening).add(0, 0.1F, -0.12F));
            list.add(new Scene("item/nozzle_fill", "nozzle_" + Math.round(h * 100), 1.0F, Math.max(0, Math.min(1, (0.9F - h) / 0.6F)), a, opening, false, true));
        }
        // clinical thermometer: going in, and held
        list.add(new Scene("item/thermometer_armpit", "thermo_in", 0.2F, 0, Map.of(), null, false, false));
        list.add(new Scene("item/thermometer_armpit", "thermo_held", 1.5F, 0, Map.of(), null, false, false));
        // jerry can (S2): carried; pouring (crouched by client/JerryCanPourView) into fill openings as high as a fuel
        // station's sump fill cover in the ground (-0.55), the big diesel generator's (0.39) and the portable set's (0.64), a
        // block ahead: the left hand at the cap, then on the spout end, the spout over the opening; the tip mirrored for a
        // left-handed player. Anchors and parameter as client/skeleton/SkeletonActions (SPOUT_OVER 0.2, SPOUT_LOWEST 0.65,
        // POUR_UPRIGHT 1.0, POUR_LEAN_SPAN 0.6, POUR_STEP_IN 0.9).
        list.add(new Scene("item/jerry_can", "can_carry", 0F, 0, Map.of(), null, false, false, false, 1F));
        list.add(new Scene("item/jerry_can", "can_carry_crouch", 0F, 0, Map.of(), null, false, true, false, 1F));
        for (float h : new float[]{-0.55F, 0.39F, 0.64F}) {
            Vector3f opening = new Vector3f(0.1F, h, 1.0F);
            Vector3f spout = new Vector3f(opening.x, Math.max(h + 0.2F, 0.65F), opening.z);
            boolean crouch = spout.y < 0.75F;   // POUR_CROUCH_BELOW: a higher spout is poured standing
            float low = Math.max(0, Math.min(1, (1.0F + (crouch ? 0 : 0.35F) - spout.y) / 0.6F));
            for (float t : new float[]{1.2F, 2.5F, 3.2F}) {
                float in = Math.min(1, t / 0.9F);
                float param = low * in * in * (3 - 2 * in);
                String name = "can_" + Math.round(h * 100) + "_" + Math.round(t * 10);
                list.add(new Scene("item/jerry_can", name, t, param, Map.of("opening", opening, "spout", spout), null, false, crouch, false, 1F));
            }
            if (h == 0.64F) list.add(new Scene("item/jerry_can", "can_64_32_left", 3.2F, low, Map.of("opening", opening, "spout", spout), null, false, crouch, true, 1F));
        }
        // crowbar (S2 round 3): held, and its melee swing (cocked, chopped); the smash wound up, the impact, resting on the
        // glass, going back; the hand anchors from the view (eye, 20 deg down, as the scene looks), the third person's and
        // the own first person's (the view anchors' first_person offsets)
        for (float t : new float[]{0F, 0.09F, 0.19F})
            list.add(new Scene("item/crowbar", "crowbar_hold_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        PlayerAction crowbar = PlayerActions.get("tool/crowbar_smash");
        if (crowbar != null) for (boolean fp : new boolean[]{false, true}) for (float t : new float[]{0.4F, 0.6F, 0.9F, 1.3F}) {
            Map<String, Vector3f> a = new HashMap<>();
            Vector3f e = PlayerSkeletonCheck.eyeO(false);
            for (PlayerAction.ViewAnchor v : crowbar.viewAnchors) {
                org.joml.Vector3d w = ActionPose.viewAnchor(v, new org.joml.Vector3d(e.x, e.y, e.z), 0, 20, false, fp);
                a.put(v.name(), new Vector3f((float) w.x, (float) w.y, (float) w.z));
            }
            list.add(new Scene("tool/crowbar_smash", (fp ? "crowbar_fp_" : "crowbar_") + Math.round(t * 100), t, 0, a, null, false, false));
        }
        // P9 draw / holster (docs/dev/p9_draw_holster_actions_v1.md): the authored poses at full weight (the hand on the
        // holstered gun, pulled out, muzzle ahead at the hip; back the same way), the carry frame as the gun prop's carry
        // preview (no live mount here). The draw ends (0.44: both hands on) and the holster starts at the idle's chest
        // ready (chest frame, docs/dev/p9_stance_loops_v1.md).
        for (float t : new float[]{0.12F, 0.2F, 0.32F, 0.44F})
            list.add(new Scene("gun/p9_01_draw", "p9_draw_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        list.add(new Scene("gun/p9_01_draw", "p9_draw_12_crouch", 0.12F, 0, Map.of(), null, false, true));
        for (float t : new float[]{0.2F, 0.32F, 0.4F})
            list.add(new Scene("gun/p9_01_holster", "p9_holster_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        // P9 idle (docs/dev/p9_stance_loops_v1.md): the chest ready at full weight, standing and crouched, and looking 45 down
        // (the chest follows the look in part); the runtime weighs it under the raise and the sprint
        // (SkeletonGunStance#stanceClips) and wraps its time. The own first person holds a gun through its old view model
        // (client/skeleton/PlayerSkeleton#firstPersonHands): no first-person gun scenes
        list.add(new Scene("gun/p9_01_idle", "p9_idle", 1F, 0, Map.of(), null, false, false));
        list.add(new Scene("gun/p9_01_idle", "p9_idle_crouch", 1F, 0, Map.of(), null, false, true));
        list.add(new Scene("gun/p9_01_idle", "p9_idle_down", 1F, 0, Map.of(), new Vector3f(), false, false));
        // P9 sprint (user 2026-10-17): the idle's chest ready, its clip turning the chest left and right over the stride
        // (a STRIDE loop: seconds = the stride's phase); standing here, the run's hips not turned against it
        for (float t : new float[]{0F, 0.25F, 0.5F, 0.75F})
            list.add(new Scene("gun/p9_01_sprint", "p9_sprint_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        list.add(new Scene("gun/p9_01_sprint", "p9_sprint_0_down", 0F, 0, Map.of(), new Vector3f(), false, false));
        // P9 trained-tier reload / inspect (docs/dev/p9_reload_inspect_v1.md 3): the support hand on the magazine prop at the
        // pouch, the old magazine falling, the new one going in; the inspect's left and right turns and its magazine check
        for (float t : new float[]{0.12F, 0.2F, 0.28F, 0.4F, 0.62F, 0.86F, 1.3F})
            list.add(new Scene("gun/p9_01_reload_tactical", "p9_reload_tac_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        list.add(new Scene("gun/p9_01_reload_tactical", "p9_reload_tac_40_crouch", 0.4F, 0, Map.of(), null, false, true));
        for (float t : new float[]{0.66F, 1.0F, 1.6F})
            list.add(new Scene("gun/p9_01_reload_empty", "p9_reload_empty_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        for (float t : new float[]{1.2F, 2.6F, 3.8F, 4.3F})
            list.add(new Scene("gun/p9_01_inspect", "p9_inspect_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        list.add(new Scene("gun/p9_01_inspect_empty", "p9_inspect_empty_260", 2.6F, 0, Map.of(), null, false, false));
        // reloads while aiming (2.7): the gun on the view frame under the eye line, the magazine change by feel
        for (float t : new float[]{0.1F, 0.3F, 0.62F, 0.86F, 1.0F})
            list.add(new Scene("gun/p9_01_reload_tactical_ads", "p9_reload_tac_ads_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        list.add(new Scene("gun/p9_01_reload_tactical_ads", "p9_reload_tac_ads_30_crouch", 0.3F, 0, Map.of(), null, false, true));
        list.add(new Scene("gun/p9_01_reload_tactical_ads", "p9_reload_tac_ads_62_down", 0.62F, 0, Map.of(), new Vector3f(), false, false));
        for (float t : new float[]{0.9F, 1.1F})
            list.add(new Scene("gun/p9_01_reload_empty_ads", "p9_reload_empty_ads_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        // lower-tier reloads (docs/dev/gun_proficiency_v1_plan.md 4): the gun worked lower and closer, the fumble's second try,
        // the novice's second look, the overhand rack (the support hand on the helper prop riding the slide)
        for (float t : new float[]{0.4F, 0.9F, 1.3F})
            list.add(new Scene("gun/p9_01_reload_tactical_experienced", "p9_reload_tac_exp_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        for (float t : new float[]{1.76F, 1.88F})
            list.add(new Scene("gun/p9_01_reload_empty_experienced", "p9_reload_empty_exp_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        for (float t : new float[]{1.2F, 2.2F})
            list.add(new Scene("gun/p9_01_reload_tactical_novice", "p9_reload_tac_nov_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        list.add(new Scene("gun/p9_01_reload_tactical_novice", "p9_reload_tac_nov_120_crouch", 1.2F, 0, Map.of(), null, false, true));
        for (float t : new float[]{1.76F, 2.1F})
            list.add(new Scene("gun/p9_01_reload_tactical_novice_fumble", "p9_reload_tac_fum_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        for (float t : new float[]{2.84F, 3.04F})
            list.add(new Scene("gun/p9_01_reload_empty_novice", "p9_reload_empty_nov_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        // the experienced reload while aiming (docs/dev/p9_reload_inspect_v1.md 5.1): the trained ADS action on slower beats
        for (float t : new float[]{0.4F, 0.9F, 1.2F})
            list.add(new Scene("gun/p9_01_reload_tactical_experienced_ads", "p9_reload_tac_exp_ads_" + Math.round(t * 100), t, 0, Map.of(), null, false, false));
        list.add(new Scene("gun/p9_01_reload_empty_experienced_ads", "p9_reload_empty_exp_ads_130", 1.3F, 0, Map.of(), null, false, false));
        return list.toArray(Scene[]::new);
    }

    private static void check(SkeletonRig rig, HumanoidPoser poser, Scene sc, String snapDir) throws Exception {
        PlayerAction action = PlayerActions.get(sc.action());
        if (action == null) {
            fail(sc.action() + ": no definition");
            return;
        }
        Matrix4f toModel = PlayerSkeletonCheck.toModel();
        ActionPose.Moment m = new ActionPose.Moment();
        m.action = action;
        m.seconds = sc.seconds();
        m.param = sc.param();
        m.mirror = sc.mirror();
        m.age = sc.age();
        Map<String, Vector3f> model = new HashMap<>();
        sc.anchors().forEach((k, v) -> model.put(k, toModel.transformPosition(new Vector3f(v)).mul(16F)));
        m.anchors = model::get;
        // a pour locks the view on the opening from the posed eye (client/JerryCanPourView); the head follows the view, so
        // solve again with the view the last solve's eye gives
        float[] lock = null;
        HumanoidPoser.Input in = null;
        SkeletonPose pose = null;
        boolean locks = sc.anchors().containsKey("opening") && sc.action().equals("item/jerry_can");
        for (int pass = 0; pass < (locks ? 3 : 1); pass++) {
            float look = !Float.isNaN(sc.pitch()) ? sc.pitch() : sc.look() == null ? 20 : 45;
            in = PlayerSkeletonCheck.standing(false, action.chestTwist, 0, lock != null ? lock[1] : look, false, sc.crouch());
            m.view = ActionPose.viewFrame(in.eyeTarget, in.viewYaw, in.viewPitch);
            if (sc.fp()) {
                // client/skeleton/SkeletonGunStance#hold: the view frame's keys ahead, the shoulders reaching as much more
                m.viewAhead = GunStance.firstPersonReach(in.viewPitch);
                in.protraction += m.viewAhead;
            }
            if (!ActionPose.body(in, rig, m)) {
                fail(sc.name() + ": the action does not show");
                return;
            }
            pose = new SkeletonPose(rig);
            HumanoidPoser.Report report = poser.solveBody(in, pose);
            in.right = HumanoidPoser.ArmMode.CLIP;
            in.left = HumanoidPoser.ArmMode.CLIP;
            ActionPose.hands(in, pose, poser, m);
            poser.solveArms(in, pose, report);
            if (locks) {
                Vector3f e = new Matrix4f(toModel).invert().transformPosition(ActionPose.cameraEye(pose).div(16F));
                Vector3f d = new Vector3f(sc.anchors().get("opening")).add(0, POUR_LOOK_OVER, 0).sub(e);
                lock = new float[]{(float) Math.toDegrees(Math.atan2(-d.x, d.z)), Math.min(POUR_PITCH_MAX, (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))))};
            }
        }

        StringBuilder line = new StringBuilder(String.format("  %-13s", sc.name()));
        for (int k = 0; k < 2; k++) {
            boolean right = k == 0;
            if ((right ? in.right : in.left) != HumanoidPoser.ArmMode.IK) {
                line.append(right ? " | R clip" : " | L clip");
                continue;
            }
            int upper = right ? rig.upperArmR : rig.upperArmL, fore = right ? rig.forearmR : rig.forearmL, hand = right ? rig.handR : rig.handL;
            Vector3f palmRest = new Vector3f(rig.pivot[upper]).add(GunStance.centreX(right, false), GunStance.DISTAL_Y - GunStance.PALM_Y, 0);
            Vector3f palm = pose.point(hand, palmRest, new Vector3f());
            Vector3f want = (right ? in.targetRight : in.targetLeft).transformPosition(new Vector3f(palmRest).sub(rig.pivot[upper]));
            float err = palm.distance(want);
            float wrist = angle(pose.local[hand]), elbow = angle(pose.local[fore]);
            Quaternionf f = pose.local[fore];
            float twist = angle(new Quaternionf(0, f.y, 0, f.w).normalize());
            line.append(String.format(" | %s palm off %.2f px wrist %.0f elbow %.0f twist %.0f", right ? "R" : "L", err, wrist, elbow, twist));
            float ik = right ? in.ikRightWeight : in.ikLeftWeight;
            if (ik < 0.99F) line.append(String.format(" (IK weight %.2f)", ik));
            else if (err > HAND_ERR) fail(sc.name() + (right ? " right" : " left") + " palm " + err + " px off its target (out of reach?)");
            if (twist > TWIST_MAX) fail(sc.name() + (right ? " right" : " left") + " forearm twisted " + twist + " deg");
            if (wrist > WRIST_MAX) fail(sc.name() + (right ? " right" : " left") + " wrist bent " + wrist + " deg");
        }
        // arm centre lines against the chest box (rest model px: x -4..4, y 0..12, z -2..2; the rig's body box: Block-Mesh V2's
        // torso is 9 px)
        Matrix4f chestInv = new Matrix4f(pose.world[rig.chest]).invert();
        SkeletonRig.Box body = rig.skin.stream().filter(s -> s.part().equals("body")).findFirst().orElseThrow().classic();
        float worst = Float.MAX_VALUE;
        String where = "";
        for (int k = 0; k < 2; k++) {
            boolean right = k == 0;
            int upper = right ? rig.upperArmR : rig.upperArmL, fore = right ? rig.forearmR : rig.forearmL, hand = right ? rig.handR : rig.handL;
            float cx = rig.pivot[upper].x + GunStance.centreX(right, false);
            // forearm and hand only: the upper arm hangs off the shoulder beside the chest by construction (from the
            // forearm's middle: vanilla 6 px down the arm, the wrist at 8)
            float elbow = rig.pivot[fore].y - rig.pivot[upper].y, wrist = rig.pivot[hand].y - rig.pivot[upper].y;
            for (float y = (elbow + wrist) / 2F; y <= GunStance.distalY(); y += 0.5F) {
                int bone = y < wrist ? fore : hand;
                Vector3f p = pose.point(bone, new Vector3f(cx, rig.pivot[upper].y + y, 0), new Vector3f());
                chestInv.transformPosition(p);
                float out = outside(p, body.x(), body.x() + body.w(), body.y(), body.y() + body.h(), body.z(), body.z() + body.d());
                if (out < worst) {
                    worst = out;
                    where = (right ? "R " : "L ") + (bone == hand ? "hand" : "forearm");
                }
            }
        }
        line.append(String.format(" | chest clearance %.1f px (%s)", worst, where));
        if (worst < armHalf(rig)) fail(sc.name() + " " + where + " " + worst + " px from the chest box (skin into the body)");
        java.util.List<Vector3f> propBox = new java.util.ArrayList<>();
        if (m.prop != null) {
            // the prop's box against the body's boxes (rest px, each in its bone's frame): chest, pelvis, legs, head
            com.antaurora.apofirstlight.skeleton.action.PlayerProps.Prop prop = com.antaurora.apofirstlight.skeleton.action.PlayerProps.get(action.prop);
            float clear = Float.MAX_VALUE;
            String part = "";
            for (int i = 0; i <= 6; i++) for (int j = 0; j <= 6; j++) for (int k = 0; k <= 6; k++) {
                if (i % 6 != 0 && j % 6 != 0 && k % 6 != 0) continue;   // the surface only
                Vector3f item = new Vector3f(prop.min()).lerp(new Vector3f(prop.max()), 0).add(
                        (prop.max().x - prop.min().x) * i / 6F, (prop.max().y - prop.min().y) * j / 6F, (prop.max().z - prop.min().z) * k / 6F);
                Vector3f p = m.prop.transformPosition(prop.local(item));
                if (i % 6 == 0 && j % 6 == 0 && k % 6 == 0) propBox.add(p);
                for (Object[] b : bodyBoxes(rig)) {
                    float[] x = (float[]) b[2];
                    Vector3f q = new Matrix4f(pose.world[(int) b[1]]).invert().transformPosition(new Vector3f(p));
                    float o = outside(q, x[0], x[1], x[2], x[3], x[4], x[5]);
                    if (o < clear) {
                        clear = o;
                        part = (String) b[0];
                    }
                }
            }
            line.append(String.format(" | prop clearance %.1f px (%s)", clear, part));
            SkeletonRig.Socket es = rig.sockets.get("eye");
            Vector3f eyePx = pose.point(es.bone(), es.pos(), new Vector3f());
            float near = Float.MAX_VALUE;
            for (Vector3f c : propBox) near = Math.min(near, c.distance(eyePx) / ActionPose.PX_PER_BLOCK);
            line.append(String.format(", %.2f blocks from the eye", near));
            if (sc.anchors().containsKey("opening")) {
                Vector3f spout = m.prop.transformPosition(com.antaurora.apofirstlight.skeleton.action.ActionPose.point(prop, "spout", m.mirror));
                Vector3f o = model.get("opening");
                line.append(String.format(" | spout %.2f blocks over the feet", (24 - spout.y) / ActionPose.PX_PER_BLOCK));
                line.append(String.format(", spout over the opening %.1f px up, %.1f px off", o.y - spout.y,
                        (float) Math.hypot(spout.x - o.x, spout.z - o.z)));
            }
            if (clear < PROP_CLEAR) fail(sc.name() + " prop " + (-clear) + " px into the " + part);
            if ("hand_saw".equals(action.prop) && !m.mirror) {
                // the hand saw's blade against the hands (user 2026-10-26: the crosscut sawed into the hand holding the board):
                // the left hand must stay off the blade; the right one is in the handle and only reported
                for (int k = 0; k < 2; k++) {
                    boolean right = k == 0;
                    float cut = bladeCut(rig, pose, prop, m.prop, right);
                    line.append(String.format(" | %s hand %s", right ? "R" : "L", Float.isNaN(cut) ? "off the blade"
                            : cut > 0 ? String.format("%.1f px into the blade", cut) : String.format("%.1f px from the blade", -cut)));
                    if (!right && cut > 0) fail(sc.name() + " left hand " + cut + " px into the saw's blade");
                }
            }
            if (System.getenv("AFL_DEBUG") != null) {
                for (String n : new java.util.TreeSet<>(prop.points().keySet())) {
                    Vector3f q = m.prop.transformPosition(com.antaurora.apofirstlight.skeleton.action.ActionPose.point(prop, n, m.mirror));
                    System.out.printf("    prop %s at (%.1f %.1f %.1f) px (left, up, ahead)%n", n, q.x, 24 - q.y, -q.z);
                }
            }
        }
        // the soles of a planted stance on the ground (rest sole y = 24 px)
        if (sc.planted()) {
            float soleErr = 0;
            for (int foot : new int[]{rig.footR, rig.footL}) {
                float x = rig.pivot[foot].x;
                for (float z : new float[]{-2F, 2F}) {
                    Vector3f p = pose.point(foot, new Vector3f(x, 24F, z), new Vector3f());
                    soleErr = Math.max(soleErr, Math.abs(p.y - 24F));
                }
            }
            line.append(String.format(" | soles off ground %.2f px", soleErr));
            if (soleErr > SOLE_ERR) fail(sc.name() + " soles " + soleErr + " px off the ground");
        }
        if (System.getenv("AFL_DEBUG") != null) {
            // joints as (left, up, ahead) px from the stepped root on the ground (Java model: left = +x, up = -y, ahead = -z)
            StringBuilder j = new StringBuilder("    joints");
            String[] names = {"pelvis", "thigh_r", "shin_r", "foot_r", "thigh_l", "shin_l", "foot_l", "chest", "head",
                    "upper_arm_r", "forearm_r", "hand_r", "upper_arm_l", "forearm_l", "hand_l"};
            for (String n : names) {
                int b = rig.bone(n);
                Vector3f p = pose.point(b, rig.pivot[b], new Vector3f()).sub(in.rootOffset);
                j.append(String.format(" %s(%.1f %.1f %.1f)", n, p.x, 24 - p.y, -p.z));
            }
            System.out.println(j);
        }
        // first person: from the camera the game puts on the stepped body (ActionPose#cameraEye), looking at the work
        SkeletonRig.Socket s = rig.sockets.get("eye");
        Vector3f eye = new Matrix4f(toModel).invert().transformPosition(
                (in.rootOffset.lengthSquared() > 0 ? ActionPose.cameraEye(pose) : pose.point(s.bone(), s.pos(), new Vector3f())).div(16F));
        // the view as client/skeleton/SkeletonActions turns it: the body's facing, the action's pitch
        // the crowbar: looking ahead at the glass; a first-person gun scene: its own look
        float yaw = 0, pitch = sc.fp() ? in.viewPitch : sc.action().contains("crowbar") ? 20 : 30;
        if (lock != null) {
            yaw = lock[0];
            pitch = lock[1];
        } else if (action.approach != null && !Float.isNaN(action.approach.pitch())) {
            Vector3f f = new Matrix4f(toModel).invert().transformDirection(ActionPose.facing(pose));
            yaw = (float) Math.toDegrees(Math.atan2(-f.x, f.z));
            pitch = ActionPose.pitch(m);
        }
        if (System.getenv("AFL_DEBUG") != null) {
            Vector3f e = pose.point(s.bone(), s.pos(), new Vector3f()).sub(in.rootOffset);
            Vector3f neck = pose.point(rig.head, rig.pivot[rig.head], new Vector3f()).sub(in.rootOffset);
            System.out.printf("    eye (%.1f %.1f %.1f) neck (%.1f %.1f %.1f) px, view yaw %.0f pitch %.0f, head local %.0f deg%n",
                    e.x, 24 - e.y, -e.z, neck.x, 24 - neck.y, -neck.z, yaw, pitch, angle(pose.local[rig.head]));
        }
        Matrix4f fp = GunStance.eye(eye, yaw, pitch).invert().mul(new Matrix4f(toModel).invert());
        // first-person cover: share of a 48 x 27 screen grid each arm and the torso cover, and the nearest skin in view
        PlayerSkeletonCheck.View view = new PlayerSkeletonCheck.View(fp, (float) Math.tan(Math.toRadians(35)), (float) Math.tan(Math.toRadians(35)) * 16F / 9F);
        float[] cover = cover(rig, pose, view);
        if (System.getenv("AFL_DEBUG") != null && m.prop != null) {
            // where the prop's points show in first person (screen -1..1, x right, y up; "off" outside it)
            com.antaurora.apofirstlight.skeleton.action.PlayerProps.Prop prop = com.antaurora.apofirstlight.skeleton.action.PlayerProps.get(action.prop);
            StringBuilder b = new StringBuilder("    FP prop");
            for (String n : new java.util.TreeSet<>(prop.points().keySet())) {
                Vector3f q = view.view(m.prop.transformPosition(ActionPose.point(prop, n, m.mirror)));
                float[] at = view.screen(q);
                b.append(at == null ? String.format(" %s behind", n) : Math.abs(at[0]) > 1 || Math.abs(at[1]) > 1
                        ? String.format(" %s off(%.2f %.2f)", n, at[0], at[1]) : String.format(" %s (%.2f %.2f) %.2f", n, at[0], at[1], q.length()));
            }
            System.out.println(b);
        }
        if (action.firstPerson == PlayerAction.FirstPerson.VIEW_MODEL) line.append(" | FP: its view model");
        else line.append(String.format(" | FP cover R %.0f%% L %.0f%%, nearest skin %.2f", cover[0] * 100, cover[1] * 100, cover[3]));
        // the smash with the third person's anchors is not what the own view sees (that is crowbar_fp_*): no cover limit
        boolean thirdOnly = sc.name().startsWith("crowbar_") && !sc.name().startsWith("crowbar_fp_") && !sc.name().startsWith("crowbar_hold_");
        if (action.firstPerson == PlayerAction.FirstPerson.BODY && !thirdOnly && Math.max(cover[0], cover[1]) > COVER_MAX) fail(sc.name() + " first person: an arm covers " + Math.max(cover[0], cover[1]) * 100 + "% of the screen");
        if (lock != null) {
            line.append(String.format(" | view locked pitch %.0f", pitch));
            if (cover[3] < SKIN_NEAR) fail(sc.name() + " first person: skin " + cover[3] + " blocks from the eye");
        }
        Vector3f step = new Vector3f(in.rootOffset).div(ActionPose.PX_PER_BLOCK);
        line.append(String.format(" | step %.2f blocks, root yaw %.0f", step.length(), in.rootYaw));
        System.out.println(line);

        if (snapDir != null) {
            File dir = new File(snapDir);
            dir.mkdirs();
            SkeletonSnapshot.marks.clear();
            SkeletonSnapshot.anchors.clear();
            SkeletonSnapshot.marks.add(in.targetRight.transformPosition(new Vector3f(rig.pivot[rig.handR]).sub(rig.pivot[rig.upperArmR])));
            SkeletonSnapshot.marks.add(in.targetLeft.transformPosition(new Vector3f(rig.pivot[rig.handL]).sub(rig.pivot[rig.upperArmL])));
            SkeletonSnapshot.marks.add(pose.point(rig.handR, rig.pivot[rig.handR], new Vector3f()));
            SkeletonSnapshot.marks.add(pose.point(rig.handL, rig.pivot[rig.handL], new Vector3f()));
            SkeletonSnapshot.anchors.addAll(model.values());
            SkeletonSnapshot.anchors.addAll(propBox);
            if (m.prop != null) {   // a long prop's line (the crowbar): its end to its top, dotted
                com.antaurora.apofirstlight.skeleton.action.PlayerProps.Prop prop = com.antaurora.apofirstlight.skeleton.action.PlayerProps.get(action.prop);
                Vector3f a = ActionPose.point(prop, "end", m.mirror), b = ActionPose.point(prop, "top", m.mirror);
                if (a != null && b != null) for (int i = 0; i <= 16; i++)
                    SkeletonSnapshot.anchors.add(m.prop.transformPosition(new Vector3f(a).lerp(b, i / 16F)));
            }
            Vector3f at = new Matrix4f(toModel).invert().transformPosition(new Vector3f(in.rootOffset).div(16F));   // the step, blocks
            at.y = 0.8F;
            SkeletonSnapshot.bodyViews(rig, pose, fp, at, dir, "action_" + sc.name());
            SkeletonSnapshot.anchors.clear();
        }
    }

    /**
     * How far a hand's box goes through the hand saw's blade (px, the shallower side), or minus its gap to it, or NaN when no
     * part of the hand is over the blade. The blade (meshes/hand_saw.aflmesh.json, item units): the tooth line x 0.301, the
     * back 0.654 at the heel (y -0.016) to 0.386 at the toe (1.274), in the plane z 0.5; the handle behind the heel is not it.
     */
    private static float bladeCut(SkeletonRig rig, SkeletonPose pose, com.antaurora.apofirstlight.skeleton.action.PlayerProps.Prop prop,
                                  Matrix4f placed, boolean right) {
        Vector3f h = placed.transformPosition(prop.local(new Vector3f(0.301F, -0.016F, 0.5F)));
        Vector3f t = placed.transformPosition(prop.local(new Vector3f(0.301F, 1.274F, 0.5F)));
        Vector3f bh = placed.transformPosition(prop.local(new Vector3f(0.654F, -0.016F, 0.5F)));
        Vector3f bt = placed.transformPosition(prop.local(new Vector3f(0.386F, 1.274F, 0.5F)));
        Vector3f u = new Vector3f(t).sub(h);
        float len = u.length();
        u.div(len);
        Vector3f v = new Vector3f(bh).sub(h);
        v.sub(new Vector3f(u).mul(v.dot(u))).normalize();
        Vector3f w = new Vector3f(u).cross(v);
        float backHeel = new Vector3f(bh).sub(h).dot(v), backToe = new Vector3f(bt).sub(h).dot(v);
        int upper = right ? rig.upperArmR : rig.upperArmL, hand = right ? rig.handR : rig.handL;
        float cx = rig.pivot[upper].x + GunStance.centreX(right, false), half = armHalf(rig);
        float y0 = rig.pivot[hand].y, y1 = rig.pivot[upper].y + GunStance.distalY();
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE, gap = Float.MAX_VALUE;
        boolean over = false;
        for (int i = 0; i <= 8; i++) for (int j = 0; j <= 8; j++) for (int k = 0; k <= 8; k++) {
            Vector3f p = pose.point(hand, new Vector3f(cx - half + 2 * half * i / 8F, y0 + (y1 - y0) * j / 8F, -half + 2 * half * k / 8F), new Vector3f());
            Vector3f d = p.sub(h);
            float a = d.dot(u), b = d.dot(v), c = d.dot(w);
            if (a < 0 || a > len || b < 0 || b > backHeel + (backToe - backHeel) * a / len) continue;
            over = true;
            lo = Math.min(lo, c);
            hi = Math.max(hi, c);
            gap = Math.min(gap, Math.abs(c));
        }
        if (!over) return Float.NaN;
        return lo < 0 && hi > 0 ? Math.min(-lo, hi) : -gap;
    }

    /** First-person cover of the right arm, left arm and torso (0..1 of the screen) and the nearest visible skin (blocks). */
    private static float[] cover(SkeletonRig rig, SkeletonPose pose, PlayerSkeletonCheck.View v) {
        boolean[][][] cells = new boolean[3][48][27];
        float nearest = Float.MAX_VALUE;
        float[] near3 = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        Vector3f[] nearAt = new Vector3f[3];
        for (PlayerSkeletonCheck.Quad q : PlayerSkeletonCheck.mesh(pose, false, true, k -> true)) {
            String part = rig.skin.get(q.part()).part();
            int k = part.equals("right_arm") ? 0 : part.equals("left_arm") ? 1 : part.equals("body") ? 2 : -1;
            if (k < 0) continue;
            for (int a = 0; a <= 16; a++) for (int b = 0; b <= 16; b++) {
                Vector3f mp = bilerp(q.p(), a / 16F, b / 16F);
                Vector3f p = v.view(new Vector3f(mp));
                float[] sc = v.screen(p);
                if (sc == null || Math.abs(sc[0]) > 1 || Math.abs(sc[1]) > 1) continue;
                nearest = Math.min(nearest, p.length());
                if (p.length() < near3[k]) {
                    near3[k] = p.length();
                    nearAt[k] = mp;
                }
                cells[k][Math.min(47, (int) ((sc[0] + 1) / 2 * 48))][Math.min(26, (int) ((sc[1] + 1) / 2 * 27))] = true;
            }
        }
        float[] out = new float[4];
        for (int k = 0; k < 3; k++) {
            int n = 0;
            for (boolean[] col : cells[k]) for (boolean c : col) if (c) n++;
            out[k] = n / (48F * 27F);
        }
        out[3] = nearest;
        if (System.getenv("AFL_DEBUG") != null) for (int k = 0; k < 2; k++) if (nearAt[k] != null) {
            boolean right = k == 0;
            int[] bones = right ? new int[]{rig.upperArmR, rig.forearmR, rig.handR} : new int[]{rig.upperArmL, rig.forearmL, rig.handL};
            StringBuilder b = new StringBuilder(right ? "    R nearest from" : "    L nearest from");
            for (int bone : bones) b.append(String.format(" %s %.1f", rig.names[bone], pose.point(bone, rig.pivot[bone], new Vector3f()).distance(nearAt[k])));
            System.out.println(b);
        }
        if (System.getenv("AFL_DEBUG") != null) System.out.printf("    nearest R arm %.2f L arm %.2f torso %.2f, torso cover %.0f%%%%%n", near3[0], near3[1], near3[2], out[2] * 100);
        return out;
    }

    private static Vector3f bilerp(Vector3f[] p, float s, float t) {
        Vector3f a = new Vector3f(p[0]).lerp(p[1], s), b = new Vector3f(p[3]).lerp(p[2], s);
        return a.lerp(b, t);
    }

    /** Signed distance (px) from a point to an axis box: positive outside, negative inside. */
    /**
     * The body's boxes for the prop check (rest model px, each in its bone's frame: x min/max, y min/max (down), z min/max).
     * Vanilla's: as they were set by hand. Block-Mesh V2's (a rig with a profile) from its own skin boxes, cut at the bones
     * (its legs 15 px from y 9, the hips at 6..9: vanilla's thigh box 12..18 on it was half its shin), the shin with the
     * foot, its head 7.5 x 7.5 x 7.25 (b3_body.json: legs x 0..4, y 9..22.5).
     */
    private static Object[][] bodyBoxes(SkeletonRig rig) {
        if (rig.profile == null) return new Object[][]{{"chest", rig.chest, new float[]{-4, 4, 0, 12, -2, 2}}, {"pelvis", rig.pelvis, new float[]{-4, 4, 12, 14, -2, 2}},
                {"thigh_r", rig.thighR, new float[]{-3.9F, 0.1F, 12, 18, -2, 2}}, {"thigh_l", rig.thighL, new float[]{-0.1F, 3.9F, 12, 18, -2, 2}},
                {"shin_r", rig.shinR, new float[]{-3.9F, 0.1F, 18, 24, -2, 2}}, {"shin_l", rig.shinL, new float[]{-0.1F, 3.9F, 18, 24, -2, 2}},
                {"head", rig.head, new float[]{-4, 4, -8, 0, -4, 4}}};
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        for (SkeletonRig.SkinPart part : rig.skin) {
            if (!part.part().equals("body") && !part.part().endsWith("_leg")) continue;
            SkeletonRig.Box box = part.classic();
            String[] names = part.part().equals("body") ? new String[]{"chest", "pelvis"}
                    : part.part().startsWith("right") ? new String[]{"thigh_r", "shin_r"} : new String[]{"thigh_l", "shin_l"};
            // the first cut ends the first bone; the rest of the box (shin and foot) goes with the second
            float cut = box.y() + part.cuts()[0];
            // B3's straight jacket is half a px deeper than the skin box (ChairPose#TORSO_HALF_D)
            float z = part.part().equals("body") ? com.antaurora.apofirstlight.skeleton.seat.ChairPose.TORSO_HALF_D : box.d() / 2F;
            out.add(new Object[]{names[0], part.bones()[0], new float[]{box.x(), box.x() + box.w(), box.y(), cut, -z, z}});
            out.add(new Object[]{names[1], part.bones()[1], new float[]{box.x(), box.x() + box.w(), cut, box.y() + box.h(), -z, z}});
        }
        out.add(new Object[]{"head", rig.head, new float[]{-3.75F, 3.75F, -7.5F, 0, -3.625F, 3.625F}});
        return out.toArray(new Object[0][]);
    }

    private static float outside(Vector3f p, float x0, float x1, float y0, float y1, float z0, float z1) {
        float dx = Math.max(x0 - p.x, p.x - x1), dy = Math.max(y0 - p.y, p.y - y1), dz = Math.max(z0 - p.z, p.z - z1);
        float ox = Math.max(dx, 0), oy = Math.max(dy, 0), oz = Math.max(dz, 0);
        float out = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
        return out > 0 ? out : Math.max(dx, Math.max(dy, dz));
    }

    private static float angle(Quaternionf q) {
        return (float) Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(q.w))));
    }

    private static void fail(String message) {
        failures++;
        System.out.println("  FAIL " + message);
    }
}
