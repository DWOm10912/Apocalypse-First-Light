package com.antaurora.apofirstlight.skeleton;

import com.antaurora.apofirstlight.skeleton.seat.ChairLegs;
import com.antaurora.apofirstlight.skeleton.seat.ChairPose;
import com.antaurora.apofirstlight.skeleton.seat.SeatPose;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
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
 * hands reach the lap.</li>
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
