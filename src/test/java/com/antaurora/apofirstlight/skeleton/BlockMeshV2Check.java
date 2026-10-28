package com.antaurora.apofirstlight.skeleton;

import com.antaurora.apofirstlight.skeleton.seat.ChairLegs;
import com.antaurora.apofirstlight.skeleton.seat.ChairPose;
import com.antaurora.apofirstlight.skeleton.seat.GroundPose;
import com.antaurora.apofirstlight.skeleton.seat.SeatPose;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * AFL Block-Mesh V2, prototype B in game (docs/dev/block_mesh_v2_b_ingame.md 3, 8.3): the game's poser on the B rig
 * (rigs/afl_block_mesh_v2.json) against the vanilla rig, the same inputs. Standalone, no game bootstrap. Run: gradlew
 * blockMeshV2Check.
 * <ol>
 * <li>the mesh file: bones resolve, weights sum to 1;</li>
 * <li>feet (HumanoidPoser#plantFeet): stand, walk, run, crouch, crouch-walk over the stride, the sole's lowest point of
 * each foot on B against vanilla's (px; within 0.5 holds);</li>
 * <li>guns: BR51 ready / ADS and the P9 at its ready, stand / crouch, pitch -60..60: the hand's end against its target
 * (px) and the IK reach on both rigs; B holds while its reach is <= 1.02 and its hand no more than 0.75 px further off
 * than vanilla's;</li>
 * <li>chairs (ChairLegs, docs 11): the wood and the office chair as the game sits B on them: the feet reach their targets,
 * the soles on the floor, the thighs on the cushion, the knees past the seat's front edge with the shins hanging, the
 * hands reach the lap;</li>
 * <li>ground sits (GroundPose, docs 17.5): the three styles, plain and mirrored, the seat and the soles on the ground,
 * nothing into it, the ankles and hands on their targets; sitting down and getting up part way.</li>
 * </ol>
 */
public final class BlockMeshV2Check {
    private static int failures;
    /**
     * Failures B has that vanilla has not. B2 (2026-10-25) had 13 actions, 1 seat and 2 gun cells; all fixed for B only
     * (docs/dev/block_mesh_v2_b_ingame.md 14): the P9 reloads' gun wrist 54-60 deg, the recoil pull 1.0 px short, the P9
     * draw crouched / holster 0.77 / 0.62 px short (HumanoidPoser: the shoulder's reach and the elbow's swing, the hand's
     * end kept on its target past the wrist's limit); the jerry can "0.2 px into the thigh" was vanilla's leg boxes on B's
     * longer legs (ActionCheck#bodyBoxes: 0.8 px clear); the bench's eye 4.1 cm off the camera (SeatPose#BENCH_BACK); the
     * P9 aimed 60 up / crouched 30 down support hand 1.0-1.3 px off (the elbow's swing, the hand's end kept). None now.
     */
    private static final int KNOWN_ACTIONS = 0, KNOWN_SEATS = 0, KNOWN_GUNS = 0;
    private static int gunFails;

    private BlockMeshV2Check() {
    }

    public static void main(String[] args) throws Exception {
        SkeletonRig vanilla = SkeletonRig.get(), b = SkeletonRig.blockMeshV2();
        HumanoidPoser pv = new HumanoidPoser(vanilla, SkeletonClips.locomotion()), pb = new HumanoidPoser(b, SkeletonClips.locomotion());
        mesh(b);
        feet(vanilla, pv, b, pb);
        guns(vanilla, pv, b, pb);
        use(b);
        chairs(b, pb);
        groundSits(b, pb);
        System.out.printf("guns: B %d cells off vanilla's (known %d)%n", gunFails, KNOWN_GUNS);
        if (gunFails > KNOWN_GUNS) fail("guns: B " + gunFails + " cells against known " + KNOWN_GUNS);
        // the player actions and the seats (T15 at B's 0.85) on B: their own checks, failures counted against vanilla's
        use(b);
        int actionsB = ActionCheck.run(b, pb, null), seatsB = SeatCheck.run(b, pb, null);
        use(vanilla);
        int actionsV = ActionCheck.run(vanilla, pv, null), seatsV = SeatCheck.run(vanilla, pv, null);
        System.out.printf("%nactions: vanilla %d FAIL, B %d FAIL (known %d); seats: vanilla %d FAIL, B %d FAIL (known %d)%n",
                actionsV, actionsB, KNOWN_ACTIONS, seatsV, seatsB, KNOWN_SEATS);
        // B no worse than vanilla (docs/dev/block_mesh_v2_b_ingame.md 14)
        if (actionsB > actionsV + KNOWN_ACTIONS) fail("actions: B " + actionsB + " against vanilla's " + actionsV + " + known " + KNOWN_ACTIONS);
        if (seatsB > seatsV + KNOWN_SEATS) fail("seats: B " + seatsB + " against vanilla's " + seatsV + " + known " + KNOWN_SEATS);
        System.out.println(failures == 0 ? "\nBlock-Mesh V2 check PASS" : "\nBlock-Mesh V2 check: " + failures + " FAIL");
        if (failures > 0) System.exit(1);
    }

    /** The wood chair (FurnitureSeat riding offset 0.228) and the office chair (0.31): vanilla's rider at y + it - 0.35. */
    private static void chairs(SkeletonRig rig, HumanoidPoser poser) {
        Object[][] chairs = {{ChairLegs.WOOD, 0.228F}, {ChairLegs.OFFICE, 0.31F}};
        for (Object[] c : chairs) {
            ChairLegs.Chair chair = (ChairLegs.Chair) c[0];
            float floorUp = 0.35F - (Float) c[1];
            Matrix4f toModel = PlayerSkeletonCheck.toModel();
            float floor = toModel.transformPosition(new Vector3f(0, floorUp, 0)).mul(16F).y;
            float pxPerMetre = toModel.transformDirection(new Vector3f(0, 1, 0)).length() * 16F;
            HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, 0, 0, 0, false, false);
            ChairLegs.Result r = ChairLegs.legs(in, rig, chair, floor, pxPerMetre);
            SkeletonPose pose = new SkeletonPose(rig);
            HumanoidPoser.Report report = poser.solveBody(in, pose);
            in.right = in.left = HumanoidPoser.ArmMode.CLIP;
            SeatPose.lapHands(in, pose, poser);
            poser.solveArms(in, pose, report);
            Vector3f ankle = pose.point(rig.footR, rig.pivot[rig.footR], new Vector3f());
            Vector3f hip = pose.point(rig.thighR, rig.pivot[rig.thighR], new Vector3f());
            Vector3f knee = pose.point(rig.shinR, rig.pivot[rig.shinR], new Vector3f());
            float tip = pose.point(rig.footR, new Vector3f(rig.pivot[rig.footR].x, 24F, -3.5F), new Vector3f()).y;
            float heel = pose.point(rig.footR, new Vector3f(rig.pivot[rig.footR].x, 24F, 2F), new Vector3f()).y;
            float sole = (floor - Math.max(tip, heel)) / pxPerMetre * 100F;                       // cm over the floor
            float thighBottom = (floor - hip.y - 2F) / pxPerMetre;                                // m over the floor
            float kneeClear = (-knee.z - 2F) / pxPerMetre - chair.frontEdge();                    // the shin's back face past the edge
            float footOff = ankle.distance(in.ankleRight);
            System.out.printf("chair %s: thigh %.1f deg, heel up %.2f px, foot tip %.0f deg; foot off its target %.3f px, sole %.1f cm "
                            + "over the floor, thigh bottom %.3f m (cushion %.3f), shin %.1f cm past the front edge, hand reach %.2f / %.2f%n",
                    chair.name(), r.thighDeg, r.heelLift, r.footTipDeg, footOff, sole, thighBottom, chair.cushion(), kneeClear * 100F,
                    report.rightReach, report.leftReach);
            if (footOff > 0.1F) fail("chair " + chair.name() + ": foot " + footOff + " px off its target");
            if (sole < -0.5F || sole > 1F) fail("chair " + chair.name() + ": sole " + sole + " cm over the floor");
            if (thighBottom < chair.cushion() - chair.sink() - 0.01F) fail("chair " + chair.name() + ": thigh into the seat");
            if (kneeClear < 0F) fail("chair " + chair.name() + ": shin into the seat's front edge");
            // the look turned aside (the chest's twist) and A / D (the game's walk animation running while seated): the same legs
            HumanoidPoser.Input walking = PlayerSkeletonCheck.standing(false, 40F, 40F, 0, false, false);   // + a look 40 deg aside
            walking.move = 1F;
            walking.walkPhase = 0.25F;
            walking.turnStep = 0.1F;
            ChairLegs.legs(walking, rig, chair, floor, pxPerMetre);
            SkeletonPose moved = new SkeletonPose(rig);
            poser.solveBody(walking, moved);
            float drift = 0;
            for (int bone : new int[]{rig.thighR, rig.shinR, rig.footR, rig.thighL, rig.shinL, rig.footL})
                drift = Math.max(drift, moved.point(bone, rig.pivot[bone], new Vector3f()).distance(pose.point(bone, rig.pivot[bone], new Vector3f())));
            System.out.printf("chair %s: legs with a 40 deg chest twist and the walk input on, off the still pose by %.3f px%n", chair.name(), drift);
            if (drift > 0.01F) fail("chair " + chair.name() + ": the look / walk input moves the legs " + drift + " px");
            if (chair.hasBack()) chairPoses(rig, poser, chair, floor, pxPerMetre);
        }
    }

    /**
     * The wooden chair's foot fidgets, also with the chest twisted to the look's edge (ChairPose, docs/dev/block_mesh_v2_b_ingame.md 12): each hand on its
     * target, the feet on theirs, the twisted torso's back corners off the backrest's slats.
     */
    private static void chairPoses(SkeletonRig rig, HumanoidPoser poser, ChairLegs.Chair chair, float floor, float pxPerMetre) {
        float peak = 0.5F / ChairPose.BOUNCE_HZ;   // the heel at its highest
        Object[][] cases = {{ChairPose.Idle.NONE, true, 0F, 0F}, {ChairPose.Idle.BOUNCE, true, 0F, 0F}, {ChairPose.Idle.BOUNCE, false, 0F, 0F},
                {ChairPose.Idle.SHIFT, true, 0F, 0F}, {ChairPose.Idle.SHIFT, false, 0F, 0F}, {ChairPose.Idle.NONE, true, 45F, 90F},
                {ChairPose.Idle.NONE, true, -45F, -90F}, {ChairPose.Idle.BOUNCE, false, 45F, 90F}};
        float slats = chair.backBehind() * pxPerMetre;
        for (Object[] c : cases) {
            ChairPose.Idle idle = (ChairPose.Idle) c[0];
            boolean fidgetRight = (Boolean) c[1];
            HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, (Float) c[2], (Float) c[3], 0, false, false);
            ChairLegs.Result r = ChairLegs.legs(in, rig, chair, floor, pxPerMetre);
            ChairPose.body(in, chair, r, idle, 1F, peak, fidgetRight);
            SkeletonPose pose = new SkeletonPose(rig);
            HumanoidPoser.Report report = poser.solveBody(in, pose);
            in.right = in.left = HumanoidPoser.ArmMode.CLIP;
            ChairPose.hands(in, pose, poser);
            poser.solveArms(in, pose, report);
            // the fidgeting foot's toe tip stays on the floor while bouncing (it is what the heel rocks on)
            int fidget = fidgetRight ? rig.footR : rig.footL;
            float toe = (floor - pose.point(fidget, new Vector3f(rig.pivot[fidget].x, 24F, -3.5F), new Vector3f()).y) / pxPerMetre * 100F;
            float hand = 0, foot = 0, back = -99;
            for (boolean right : new boolean[]{true, false}) {
                int upper = right ? rig.upperArmR : rig.upperArmL, h = right ? rig.handR : rig.handL, f = right ? rig.footR : rig.footL;
                Vector3f want = (right ? in.targetRight : in.targetLeft).transformPosition(new Vector3f(rig.pivot[h]).sub(rig.pivot[upper]));
                hand = Math.max(hand, pose.point(h, rig.pivot[h], new Vector3f()).distance(want));
                foot = Math.max(foot, pose.point(f, rig.pivot[f], new Vector3f()).distance(right ? in.ankleRight : in.ankleLeft));
            }
            Vector3f chest = rig.pivot[rig.chest];
            for (float x : new float[]{-ChairPose.TORSO_HALF_W, ChairPose.TORSO_HALF_W})
                for (float y : new float[]{chest.y - 1F, chest.y - 7F})
                    back = Math.max(back, pose.point(rig.chest, new Vector3f(x, y, chest.z + ChairPose.TORSO_HALF_D), new Vector3f()).z);
            float clear = (slats - back) / pxPerMetre * 100F;
            System.out.printf("chair %s %s %s, chest %.0f view %.0f: scoot %.2f px; hands off %.2f px, feet off %.2f px, toe %.1f cm over the floor, "
                    + "torso %.1f cm off the slats%n", chair.name(), idle, fidgetRight ? "right" : "left", (Float) c[2], (Float) c[3],
                    ChairPose.scoot(chair, r, in.chestYaw), hand, foot, toe, clear);
            if (hand > 0.5F) fail("chair " + chair.name() + " " + idle + ": a hand " + hand + " px off its target");
            if (foot > 0.1F) fail("chair " + chair.name() + " " + idle + ": a foot " + foot + " px off its target");
            if (clear < 0F) fail("chair " + chair.name() + " " + idle + ": the torso " + (-clear) + " cm into the backrest");
            if (idle == ChairPose.Idle.BOUNCE && Math.abs(toe) > 0.6F) fail("chair " + chair.name() + " bouncing: the toe " + toe + " cm off the floor");
        }
    }

    /**
     * Sat on the ground (GroundPose, docs/dev/block_mesh_v2_b_ingame.md 17.5): each style, plain and mirrored, over the
     * sitting clip as GroundSitClient puts it on (the rider's feet on the seat's face: the ground at the model's origin).
     * The body mesh skinned rigidly by each vertex's heaviest bone: the seat (pelvis and thighs) and the soles against the
     * ground (px, + = over it), nothing into it; the ankles and wrists on their targets. Then the sitting down and getting
     * up part way: nothing into the ground.
     */
    private static void groundSits(SkeletonRig rig, HumanoidPoser poser) throws Exception {
        System.out.println("\nground sits: seat / soles over the ground (px), lowest point, ankles / hands off their targets (px), reach");
        float[][] rest;
        int[] from;
        try (InputStream s = BlockMeshV2Check.class.getClassLoader().getResourceAsStream("assets/apocalypse_firstlight/" + rig.profile.mesh())) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(s, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray bones = json.getAsJsonArray("bones"), v = json.getAsJsonArray("vertices");
            int[] source = new int[bones.size()];
            for (int i = 0; i < source.length; i++) {
                String name = bones.get(i).getAsJsonObject().get("name").getAsString();
                int same = rig.find(name);
                source[i] = same >= 0 ? same : switch (name) {
                    case "spine", "neck" -> rig.chest;
                    case "clavicle_r" -> rig.upperArmR;
                    case "clavicle_l" -> rig.upperArmL;
                    case "toes_r" -> rig.footR;
                    case "toes_l" -> rig.footL;
                    default -> throw new IllegalArgumentException(name);
                };
            }
            int n = v.size() / 13;
            rest = new float[n][];
            from = new int[n];
            for (int i = 0; i < n; i++) {
                rest[i] = new float[]{v.get(i * 13).getAsFloat(), v.get(i * 13 + 1).getAsFloat(), v.get(i * 13 + 2).getAsFloat()};
                int best = 0;
                for (int j = 1; j < 4; j++) if (v.get(i * 13 + 9 + j).getAsFloat() > v.get(i * 13 + 9 + best).getAsFloat()) best = j;
                from[i] = source[v.get(i * 13 + 5 + best).getAsInt()];
            }
        }
        float floor = PlayerSkeletonCheck.toModel().transformPosition(new Vector3f()).mul(16F).y;
        record Phase(String name, String clip, float time, float w) {}
        Phase[] phases = {new Phase("sat", "body.sit", 0.5F, 1F),
                new Phase("sit down 0.7 s", "body.sit_down", 0.7F, GroundPose.ease(0.7F, 0.55F, 1.15F)),
                new Phase("sit down 0.85 s", "body.sit_down", 0.85F, GroundPose.ease(0.85F, 0.55F, 1.15F)),
                new Phase("sit down 1.0 s", "body.sit_down", 1.0F, GroundPose.ease(1.0F, 0.55F, 1.15F)),
                new Phase("sit down 1.15 s", "body.sit_down", 1.15F, 1F),
                new Phase("stand up 0.2 s", "body.stand_up", 0.2F, 1F - GroundPose.ease(0.2F, 0.1F, 0.55F)),
                new Phase("stand up 0.3 s", "body.stand_up", 0.3F, 1F - GroundPose.ease(0.3F, 0.1F, 0.55F)),
                new Phase("stand up 0.45 s", "body.stand_up", 0.45F, 1F - GroundPose.ease(0.45F, 0.1F, 0.55F)),
                new Phase("sit down 0.35 s", "body.sit_down", 0.35F, 0F), new Phase("sit down 0.95 s", "body.sit_down", 0.95F, 1F),
                new Phase("stand up 0.5 s", "body.stand_up", 0.5F, 0F), new Phase("stand up 0.75 s", "body.stand_up", 0.75F, 0F)};
        // B's own sitting clips (body_b.*, tools/medical-actions-v1a/author_ground_sit_b.cjs: the game's now) against vanilla's on B
        for (boolean mirror : new boolean[]{false, true})
            for (Phase ph : phases) {
                float[] own = groundSit(rig, poser, null, mirror, ph.clip(), ph.time(), 1F, floor, rest, from);
                float[] clip = groundSit(rig, poser, null, mirror, ph.clip(), ph.time(), 0F, floor, rest, from);
                System.out.printf("  B CLIPS %-6s %-15s: seat %+.2f (vanilla's clips on B %+.2f), soles R %+.2f L %+.2f, lowest %+.2f %s (vanilla's %+.2f)%n",
                        mirror ? "mirror" : "plain", ph.name(), own[0], clip[0], own[1], own[2], own[3], rig.names[(int) own[9]], clip[3]);
                String what = "ground sit B clips" + (mirror ? " mirrored " : " ") + ph.name();
                // sat 0.6 px; sitting down and getting up 1.1 (0.85 s: the right ankle between two keys, as vanilla's clips on B)
                if (-own[3] > (ph.clip().equals("body.sit") ? 0.6F : 1.1F)) fail(what + ": " + (-own[3]) + " px into the ground");
                if (ph.clip().equals("body.sit") && Math.abs(own[0]) > 0.5F) fail(what + ": the seat " + own[0] + " px off the ground");
            }
        for (GroundPose.Style style : GroundPose.Style.values())
            for (boolean mirror : new boolean[]{false, true})
                for (Phase ph : phases) {
                    float[] own = groundSit(rig, poser, style, mirror, ph.clip(), ph.time(), ph.w(), floor, rest, from);
                    // the sitting clips on their own (B's plantFeet): the feet's own dip while sitting down and getting up
                    float[] clip = ph.w() < 1F ? groundSit(rig, poser, style, mirror, ph.clip(), ph.time(), 0F, floor, rest, from) : own;
                    System.out.printf("  %-5s %-6s %-15s w %.2f: seat %+.2f, soles R %+.2f L %+.2f, lowest %+.2f (the clip alone %+.2f); "
                                    + "ankles %.2f, hands R %.2f L %.2f; reach %.2f / %.2f%n", style, mirror ? "mirror" : "plain", ph.name(), ph.w(),
                            own[0], own[1], own[2], own[3], clip[3], own[4], own[5], own[6], own[7], own[8]);
                    String what = "ground sit " + style + (mirror ? " mirrored " : " ") + ph.name();
                    if (-own[3] > Math.max(0.6F, -clip[3] + 0.2F)) fail(what + ": " + (-own[3]) + " px into the ground (the clip alone " + (-clip[3]) + ")");
                    if (ph.w() < 1F) continue;
                    if (Math.abs(own[0]) > 0.5F) fail(what + ": the seat " + own[0] + " px off the ground");
                    if (Math.min(own[1], own[2]) > 0.6F) fail(what + ": no foot on the ground");
                    if (own[4] > 0.1F) fail(what + ": an ankle " + own[4] + " px off its target");
                    if (Math.max(own[5], own[6]) > 0.5F) fail(what + ": a hand " + Math.max(own[5], own[6]) + " px off its target");
                }
    }

    /**
     * One ground sit solved as the game does it: {seat, sole R, sole L, lowest point (px over the ground), ankles off,
     * hand ends R, L off (px), reach R, reach L}.
     */
    private static float[] groundSit(SkeletonRig rig, HumanoidPoser poser, @org.jetbrains.annotations.Nullable GroundPose.Style style, boolean mirror, String clip, float time,
                                     float w, float floor, float[][] rest, int[] from) {
        HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, 0, 0, 0, false, false);
        in.eyeTarget = null;
        in.chestPitch = 0F;
        boolean[] mask = new boolean[rig.size()];
        java.util.Arrays.fill(mask, true);
        // style null: B's own sitting clips (body_b.*, GroundSitClient as the game has it now; w 0: vanilla's clips on B)
        boolean own = style == null && w > 0F;
        SkeletonClips.Clip c = SkeletonClips.byKey(own ? "body_b." + clip.substring(clip.indexOf('.') + 1) : clip);
        if (c == null) throw new IllegalStateException("no clip for " + clip);
        in.stance.add(new HumanoidPoser.Input.StanceClip(c, time, 1F, mask, mirror));
        if (own) in.plantWeight = 0F;
        if (style != null) GroundPose.body(in, rig, style, mirror, floor, w);
        SkeletonPose pose = new SkeletonPose(rig);
        HumanoidPoser.Report report = poser.solveBody(in, pose);
        in.right = in.left = HumanoidPoser.ArmMode.CLIP;
        if (style != null) GroundPose.hands(in, pose, poser, style, mirror, w);
        poser.solveArms(in, pose, report);
        String objDir = System.getenv("AFL_SIT_OBJ");
        if (objDir != null && style == null && !mirror)
            obj(pose, java.nio.file.Path.of(objDir, (own ? "b_" : "vanilla_") + clip.substring(clip.indexOf('.') + 1) + "_" + Math.round(time * 100) + ".obj"), floor);
        float seat = -Float.MAX_VALUE, footR = -Float.MAX_VALUE, footL = -Float.MAX_VALUE, low = -Float.MAX_VALUE;
        int lowBone = 0;
        Vector3f p = new Vector3f();
        for (int i = 0; i < rest.length; i++) {
            pose.point(from[i], p.set(rest[i][0], rest[i][1], rest[i][2]), p);
            int b = from[i];
            if (b == rig.pelvis || b == rig.thighR || b == rig.thighL) seat = Math.max(seat, p.y);
            if (b == rig.footR) footR = Math.max(footR, p.y);
            if (b == rig.footL) footL = Math.max(footL, p.y);
            if (p.y > low) { low = p.y; lowBone = b; }
        }
        if (System.getenv("AFL_SIT_DUMP") != null && (w == 1F || w == 0F) && clip.equals("body.sit") && !mirror) {
            System.out.printf("DUMP %s %s w %.0f%n", style == null ? (own ? "B_CLIPS" : "VANILLA_CLIPS") : style, mirror ? "mirror" : "plain", w);
            for (int b : new int[]{rig.pelvis, rig.chest, rig.head, rig.upperArmR, rig.forearmR, rig.handR, rig.upperArmL, rig.forearmL, rig.handL,
                    rig.thighR, rig.shinR, rig.footR, rig.thighL, rig.shinL, rig.footL}) {
                Vector3f at = pose.point(b, rig.pivot[b], new Vector3f());
                Quaternionf local = new Quaternionf(pose.local[b]);
                float deg = (float) Math.toDegrees(2 * Math.acos(Math.min(1F, Math.abs(local.w))));
                System.out.printf("  %-12s blender (%+6.2f %+6.2f %+6.2f)  local turn %5.1f deg%n", rig.names[b], -at.x, at.z, floor - at.y, deg);
            }
        }
        float ankle = 0;
        float[] hand = new float[2];
        if (w > 0F && style != null) for (boolean right : new boolean[]{true, false}) {
            int f = right ? rig.footR : rig.footL, upper = right ? rig.upperArmR : rig.upperArmL, h = right ? rig.handR : rig.handL;
            ankle = Math.max(ankle, pose.point(f, rig.pivot[f], new Vector3f()).distance(right ? in.ankleRight : in.ankleLeft));
            // the hand's end (HumanoidPoser: kept on its target past the wrist's limit, the wrist moved)
            Vector3f cap = new Vector3f((right ? 1F : -1F) * rig.profile.handCapX(), rig.profile.handCapY(), 0F);
            Vector3f want = (right ? in.targetRight : in.targetLeft).transformPosition(new Vector3f(cap));
            hand[right ? 0 : 1] = pose.point(h, cap.add(rig.pivot[upper]), new Vector3f()).distance(want);
        }
        return new float[]{floor - seat, floor - footR, floor - footL, floor - low, ankle, hand[0], hand[1], report.rightReach, report.leftReach, lowBone};
    }

    /**
     * The B body mesh in this pose as the game skins it (BlockMeshBody#matrices: the spine half the chest's turn, the
     * clavicle the arm's turn less 8 deg, four weights; the toes' bend on the ground left out), as an OBJ in Blender px
     * (z up from the ground, the front +y, the body's right +x) for a render next to the concept (AFL_SIT_OBJ).
     */
    private static void obj(SkeletonPose pose, java.nio.file.Path out, float floor) {
        SkeletonRig rig = pose.rig;
        try (InputStream s = BlockMeshV2Check.class.getClassLoader().getResourceAsStream("assets/apocalypse_firstlight/" + rig.profile.mesh())) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(s, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray bones = json.getAsJsonArray("bones"), v = json.getAsJsonArray("vertices");
            Matrix4f[] m = new Matrix4f[bones.size()];
            for (int i = 0; i < m.length; i++) {
                String name = bones.get(i).getAsJsonObject().get("name").getAsString();
                int same = rig.find(name);
                m[i] = switch (name) {
                    case "spine" -> pose.half(rig.chest, new Matrix4f());
                    case "neck" -> new Matrix4f(pose.world[rig.chest]);
                    case "clavicle_r" -> clavicle(pose, rig.upperArmR);
                    case "clavicle_l" -> clavicle(pose, rig.upperArmL);
                    case "toes_r" -> new Matrix4f(pose.world[rig.footR]);
                    case "toes_l" -> new Matrix4f(pose.world[rig.footL]);
                    default -> new Matrix4f(pose.world[same]);
                };
            }
            StringBuilder o = new StringBuilder("# " + out.getFileName() + " (BlockMeshV2Check#obj)\n");
            int n = v.size() / 13;
            Vector3f rest = new Vector3f(), part = new Vector3f(), sum = new Vector3f();
            for (int i = 0; i < n; i++) {
                rest.set(v.get(i * 13).getAsFloat(), v.get(i * 13 + 1).getAsFloat(), v.get(i * 13 + 2).getAsFloat());
                sum.zero();
                for (int j = 0; j < 4; j++) {
                    float wt = v.get(i * 13 + 9 + j).getAsFloat();
                    if (wt > 0F) sum.add(m[v.get(i * 13 + 5 + j).getAsInt()].transformPosition(rest, part).mul(wt));
                }
                o.append(String.format(java.util.Locale.ROOT, "v %.4f %.4f %.4f%nvt %.5f %.5f%n", -sum.x, -sum.z, floor - sum.y,
                        v.get(i * 13 + 3).getAsFloat(), 1F - v.get(i * 13 + 4).getAsFloat()));
            }
            for (int q = 0; q < n / 4; q++) {
                int a = q * 4 + 1;
                o.append(String.format("f %d/%d %d/%d %d/%d %d/%d%n", a, a, a + 1, a + 1, a + 2, a + 2, a + 3, a + 3));
            }
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.writeString(out, o);
        } catch (Exception e) {
            throw new IllegalStateException("obj " + out, e);
        }
    }

    /** BlockMeshBody#clavicle. */
    private static Matrix4f clavicle(SkeletonPose pose, int upperArm) {
        Matrix4f arm = pose.world[upperArm];
        Quaternionf chest = pose.world[pose.rig.chest].getUnnormalizedRotation(new Quaternionf()).normalize();
        Quaternionf rel = chest.conjugate().mul(arm.getUnnormalizedRotation(new Quaternionf()).normalize());
        if (rel.w < 0) rel.set(-rel.x, -rel.y, -rel.z, -rel.w);
        float angle = 2F * (float) Math.acos(Math.min(1F, rel.w)), dead = (float) Math.toRadians(8F);
        Quaternionf keep = new Quaternionf();
        if (angle > dead) keep.slerp(rel, (angle - dead) / angle);
        Quaternionf delta = new Quaternionf(rel).conjugate().mul(keep);
        Vector3f p = pose.rig.pivot[upperArm];
        return new Matrix4f(arm).translate(p).rotate(delta).translate(-p.x, -p.y, -p.z);
    }

    private static void use(SkeletonRig rig) {
        GunStance.useRig(rig);
        SeatPose.useRig(rig);
    }

    private static void fail(String what) {
        failures++;
        System.out.println("  FAIL " + what);
    }

    private static void mesh(SkeletonRig rig) throws Exception {
        try (InputStream in = BlockMeshV2Check.class.getClassLoader().getResourceAsStream("assets/apocalypse_firstlight/" + rig.profile.mesh())) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray bones = json.getAsJsonArray("bones"), v = json.getAsJsonArray("vertices");
            int quads = json.getAsJsonArray("quad_parts").size(), n = v.size() / 13, bad = 0;
            for (int i = 0; i < n; i++) {
                float sum = 0;
                for (int j = 0; j < 4; j++) {
                    int joint = v.get(i * 13 + 5 + j).getAsInt();
                    if (joint < 0 || joint >= bones.size()) bad++;
                    sum += v.get(i * 13 + 9 + j).getAsFloat();
                }
                if (Math.abs(sum - 1F) > 1e-3F) bad++;
            }
            System.out.println("mesh: " + bones.size() + " bones, " + quads + " quads, " + n + " vertices, bad weights " + bad);
            if (bad > 0 || n != quads * 4) fail("mesh weights / vertices");
        }
    }

    /** The foot's lowest sole point (the largest Java y): the box under the foot bone, heel to ball. */
    private static float sole(SkeletonPose pose, boolean right, float halfX, float front, float back) {
        SkeletonRig rig = pose.rig;
        int foot = right ? rig.footR : rig.footL;
        float x = rig.pivot[foot].x, low = -Float.MAX_VALUE;
        for (float dx : new float[]{-halfX, halfX}) for (float z : new float[]{front, back})
            low = Math.max(low, pose.point(foot, new Vector3f(x + dx, 24F, z), new Vector3f()).y);
        return low;
    }

    private static void feet(SkeletonRig vanilla, HumanoidPoser pv, SkeletonRig b, HumanoidPoser pb) {
        System.out.println("\nfeet: the sole's lowest point, B - vanilla (px, + = lower), worst over the stride");
        record State(String name, boolean crouch, float move, float run) {}
        SkeletonPose sv = new SkeletonPose(vanilla), sb = new SkeletonPose(b);
        for (State st : new State[]{new State("stand", false, 0, 0), new State("walk", false, 1, 0), new State("run", false, 1, 1),
                new State("crouch", true, 0, 0), new State("crouch walk", true, 1, 0)}) {
            float worst = 0, worstAbs = 0;
            for (float phase = 0; phase < (st.move() > 0 ? 1F : 0.01F); phase += 0.0625F) {
                for (boolean right : new boolean[]{true, false}) {
                    use(vanilla);
                    HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, 0, 0, 0, false, st.crouch());
                    in.move = st.move();
                    in.run = st.run();
                    in.walkPhase = phase;
                    pv.solve(in, sv);
                    float lv = sole(sv, right, 2F, -2F, 2F);
                    use(b);
                    in = PlayerSkeletonCheck.standing(false, 0, 0, 0, false, st.crouch());
                    in.move = st.move();
                    in.run = st.run();
                    in.walkPhase = phase;
                    pb.solve(in, sb);
                    float lb = sole(sb, right, 2F, -1.5F, 2F);
                    float d = lb - lv;
                    if (Math.abs(d) > worstAbs) { worstAbs = Math.abs(d); worst = d; }
                }
            }
            System.out.printf("  %-12s %+.2f px (%+.1f cm)%n", st.name(), worst, worst * 0.9375F / 16F * 100F);
            if (worstAbs > 0.5F) fail("feet " + st.name() + " off vanilla's by " + worst + " px");
        }
    }

    private static void guns(SkeletonRig vanilla, HumanoidPoser pv, SkeletonRig b, HumanoidPoser pb) throws Exception {
        System.out.println("\nguns: hand end -> target (px) and IK reach, worst of both hands, vanilla | B");
        PlayerSkeletonCheck.Gun br51 = PlayerSkeletonCheck.Gun.of("br51_01"), p9 = PlayerSkeletonCheck.Gun.of("p9_01");
        record Hold(String name, PlayerSkeletonCheck.Gun gun, float aim) {}
        for (Hold h : new Hold[]{new Hold("BR51 ready", br51, 0), new Hold("BR51 ADS", br51, 1), new Hold("P9 ready", p9, 0), new Hold("P9 ADS", p9, 1)})
            for (boolean crouch : new boolean[]{false, true}) {
                StringBuilder line = new StringBuilder(String.format("  %-10s %-6s", h.name(), crouch ? "crouch" : "stand"));
                for (float pitch : new float[]{-60, -30, 0, 30, 60}) {
                    line.append(String.format(" | %+3.0f:", pitch));
                    float vanillaOff = 0;
                    for (SkeletonRig rig : new SkeletonRig[]{vanilla, b}) {
                        use(rig);
                        HumanoidPoser poser = rig == b ? pb : pv;
                        SkeletonPose pose = new SkeletonPose(rig);
                        HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, GunStance.CHEST_TWIST, 0, pitch, h.gun() == br51, crouch);
                        HumanoidPoser.Report r = PlayerSkeletonCheck.gunSolve(poser, in, pose, h.gun(), 1, 0, h.aim(), "static_idle", 0);
                        float off = Math.max(handOff(pose, in.targetRight, true), h.gun() == p9 || in.ikLeftWeight > 0.5F ? handOff(pose, in.targetLeft, false) : 0);
                        float reach = Math.max(r.rightReach, r.leftReach);
                        line.append(String.format(" %.2f/%.2f", off, reach)).append(rig == vanilla ? " |" : "");
                        if (rig == vanilla) vanillaOff = off;
                        // B no further off than vanilla's by more than 0.75 px (4.4 cm)
                        else if (reach > 1.02F || off > vanillaOff + 0.75F)
                            gunFails++;
                    }
                }
                System.out.println(line);
            }
    }

    /** The posed hand end against where its target puts it (the arm frame's cap: GunStance#centreX, #distalY), px. */
    private static float handOff(SkeletonPose pose, Matrix4f target, boolean right) {
        SkeletonRig rig = pose.rig;
        int upper = right ? rig.upperArmR : rig.upperArmL, hand = right ? rig.handR : rig.handL;
        Vector3f cap = new Vector3f(GunStance.centreX(right, false), GunStance.distalY(), 0);
        Vector3f want = target.transformPosition(new Vector3f(cap));
        Vector3f have = pose.point(hand, new Vector3f(cap).add(rig.pivot[upper]), new Vector3f());
        return have.distance(want);
    }
}
