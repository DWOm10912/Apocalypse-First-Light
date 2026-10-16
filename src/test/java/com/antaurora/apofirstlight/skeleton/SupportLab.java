package com.antaurora.apofirstlight.skeleton;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tuning only (AFL_LAB2): the support hand under the handguard. The hand's top face on the handguard's bottom, its wrist
 * straight toward a chosen forearm direction; measured for reach, wrist, twist and how much of the gun goes into the arms.
 */
final class SupportLab {
    private SupportLab() {
    }

    /** Points on the gun's faces (gun px, static idle). */
    static List<Vector3f> points(PlayerSkeletonCheck.Gun g) throws Exception {
        SkeletonSnapshot.GunMesh mesh = SkeletonSnapshot.gunMesh(g.carried().asset());
        List<Vector3f> out = new ArrayList<>();
        Map<String, Matrix4f> frames = new HashMap<>();
        for (int i = 0; i < mesh.faces().size(); i++) {
            Matrix4f f = frames.computeIfAbsent(mesh.bones().get(i), b -> g.sampler().frame(b, "static_idle", 0F, null, null));
            if (f == null) continue;
            float[][] face = mesh.faces().get(i);
            Vector3f c = new Vector3f();
            List<Vector3f> vs = new ArrayList<>();
            for (float[] v : face) {
                Vector3f p = f.transformPosition(new Vector3f(v[0], v[1], v[2])).mul(16F);
                vs.add(p);
                c.add(p);
            }
            c.div(vs.size());
            out.add(c);
            for (int k = 0; k < vs.size(); k++) {
                Vector3f a = vs.get(k), b = vs.get((k + 1) % vs.size());
                out.add(new Vector3f(a));
                out.add(new Vector3f(a).add(c).mul(0.5F));
                out.add(new Vector3f(a).add(b).mul(0.5F));
            }
        }
        return out;
    }

    /** Gun points (model px) inside each segment of one arm (upper, fore, hand), the box shrunk by {@code shrink}. */
    static int[] inside(SkeletonRig rig, SkeletonPose pose, boolean slim, List<Vector3f> modelPts, boolean right, float shrink) {
        SkeletonRig.SkinPart part = null;
        for (SkeletonRig.SkinPart p : rig.skin) if (p.part().equals(right ? "right_arm" : "left_arm")) part = p;
        SkeletonRig.Box b = part.box(slim);
        int[] cuts = part.cuts();
        float[] y0 = {0, cuts[0], cuts[1]}, y1 = {cuts[0], cuts[1], b.h()};
        int[] count = new int[3];
        for (int k = 0; k < 3; k++) {
            Matrix4f inv = new Matrix4f(pose.world[part.bones()[k]]).invert();
            for (Vector3f p : modelPts) {
                Vector3f r = inv.transformPosition(p, new Vector3f());
                if (r.x > b.x() + shrink && r.x < b.x() + b.w() - shrink && r.z > b.z() + shrink && r.z < b.z() + b.d() - shrink
                        && r.y > b.y() + y0[k] + (k == 0 ? shrink : 0) && r.y < b.y() + y1[k] - (k == 2 ? shrink : 0)) count[k]++;
            }
        }
        return count;
    }

    /**
     * The support hand's IK target: the hand cap {@code at} (gun px) moved down until the hand's highest corner is
     * {@code overlap} px (model) above the gun's bottom there; the forearm toward {@code elbowDir} (gun frame, unit, from
     * the hand toward the elbow), turned {@code roll} about it.
     */
    static void target(Matrix4f gun, Vector3f at, Vector3f elbowDir, float roll, float overlap, boolean slim, Matrix4f out) {
        Matrix4f px = new Matrix4f().scaling(16F).mul(gun).scale(1F / 16F);
        Quaternionf gunRot = gun.getNormalizedRotation(new Quaternionf());
        Vector3f cap = px.transformPosition(new Vector3f(at));
        Vector3f finger = gunRot.transform(new Vector3f(elbowDir).negate()).normalize();
        Vector3f up = gunRot.transform(new Vector3f(0, 1, 0));
        Quaternionf r = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), finger);
        r.premul(new Quaternionf().rotationAxis((float) Math.toRadians(roll), finger.x, finger.y, finger.z));
        float w = slim ? 1.5F : 2F, high = -1e9F;
        for (float x : new float[]{-w, w}) for (float y : new float[]{-2F, 0F}) for (float z : new float[]{-2F, 2F})
            high = Math.max(high, r.transform(new Vector3f(x, y, z)).dot(up));
        cap.sub(new Vector3f(up).mul(high - overlap));
        out.translation(cap).rotate(r).translate(-GunStance.centreX(false, slim), -GunStance.DISTAL_Y, 0);
    }

    /** The lowest gun point (gun px y) within {@code r} px of (x, z). */
    static float bottom(List<Vector3f> pts, float x, float z, float r) {
        float y = 1e9F;
        for (Vector3f p : pts) if (Math.abs(p.x - x) < r && Math.abs(p.z - z) < r) y = Math.min(y, p.y);
        return y;
    }

    static void run(SkeletonRig rig, HumanoidPoser poser) throws Exception {
        SkeletonPose pose = new SkeletonPose(rig);
        PlayerSkeletonCheck.Gun g = PlayerSkeletonCheck.Gun.of("br51_01");
        List<Vector3f> pts = points(g);
        for (SkeletonRig.SkinPart p : rig.skin) System.out.println("skin " + p.part() + " " + p.box(false) + " cuts " + java.util.Arrays.toString(p.cuts()));
        // the gun's profile along the bore: lowest and highest point near the centre line, x range
        StringBuilder prof = new StringBuilder("BR51 profile (gun px) z: ymin..ymax x");
        for (float z = -28; z <= 26; z += 2) {
            float lo = 1e9F, hi = -1e9F, xl = 1e9F, xh = -1e9F;
            for (Vector3f p : pts) if (Math.abs(p.z - z) < 1F) {
                lo = Math.min(lo, p.y); hi = Math.max(hi, p.y); xl = Math.min(xl, p.x); xh = Math.max(xh, p.x);
            }
            prof.append(String.format("%n  %+3.0f: %.1f..%.1f  x %.1f..%.1f", z, lo, hi, xl, xh));
        }
        System.out.println(prof);
        // the current hold: gun points inside each arm segment
        for (float aim : new float[]{0, 1}) {
            HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, g.carried().kind().chestTwist, 0, 0, true, false);
            PlayerSkeletonCheck.gunSolve(poser, in, pose, g, 1, 0, aim, "static_idle", 0);
            List<Vector3f> mp = model(pts, PlayerSkeletonCheck.lastGun);
            System.out.println("current " + (aim > 0 ? "ADS" : "ready") + ": inside R " + java.util.Arrays.toString(inside(rig, pose, false, mp, true, 0.15F))
                    + " L " + java.util.Arrays.toString(inside(rig, pose, false, mp, false, 0.15F)));
        }
        // sweep: support point along the handguard, forearm direction, roll
        record Result(String key, float score, String line) {}
        for (float aim : new float[]{0, 1}) {
            List<Result> results = new ArrayList<>();
            for (float z : new float[]{-13, -11, -9, -7, -5}) for (float down = 10; down <= 70; down += 10) for (float out = -10; out <= 50; out += 10) {
                Result best = null;
                for (float roll = 0; roll < 360; roll += 30) {
                    final float fz = z, fdown = down, fout = out, froll = roll;
                    float x = -0.2F, bot = bottom(pts, x, z, 1.2F);
                    Vector3f dir = new Vector3f(0, 0, 1).rotateX((float) Math.toRadians(fdown)).rotateY((float) Math.toRadians(-fout));
                    PlayerSkeletonCheck.labLeft = (gun, in) -> target(gun, new Vector3f(x, bot, fz), dir, froll, 0.3F, in.slim, in.targetLeft);
                    float worst = 0, wristMax = 0, twistMax = 0, offMax = 0, coverMid = 0;
                    int clip = 0;
                    for (float pitch : new float[]{-30, 0, 30}) {
                        HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, g.carried().kind().chestTwist, 0, pitch, true, false);
                        HumanoidPoser.Report r = PlayerSkeletonCheck.gunSolve(poser, in, pose, g, 1, 0, aim, "static_idle", 0);
                        PlayerSkeletonCheck.Hold h = PlayerSkeletonCheck.measure(rig, pose, in, false, pitch);
                        List<Vector3f> mp = model(pts, PlayerSkeletonCheck.lastGun);
                        int[] c = inside(rig, pose, false, mp, false, 0.15F);
                        clip = Math.max(clip, c[0] + c[1] + c[2]);
                        worst = Math.max(worst, r.leftReach);
                        wristMax = Math.max(wristMax, h.wristL());
                        twistMax = Math.max(twistMax, h.twistL());
                        offMax = Math.max(offMax, h.errL());
                        if (pitch == 0) coverMid = h.centreL();
                    }
                    float score = wristMax + 0.3F * twistMax + offMax * 2 + clip * 5 + Math.max(0, worst - 1F) * 500 + coverMid * 50;
                    String line = String.format("z %+3.0f down %2.0f out %+3.0f roll %3.0f | reach %.2f wrist %2.0f twist %2.0f off %2.0f clip %3d coverMid %2.0f%% bottom %.1f",
                            fz, fdown, fout, froll, worst, wristMax, twistMax, offMax, clip, coverMid * 100, bot);
                    if (best == null || score < best.score()) best = new Result(z + "/" + down + "/" + out, score, line);
                }
                results.add(best);
            }
            PlayerSkeletonCheck.labLeft = null;
            results.sort((a, b) -> Float.compare(a.score(), b.score()));
            System.out.println("LAB2 " + (aim > 0 ? "ADS" : "ready") + " best:");
            for (int i = 0; i < Math.min(15, results.size()); i++) System.out.println("  " + results.get(i).line());
        }
    }

    /**
     * The rifle on the shoulder (tuning): its butt plate centre {@code butt} (gun px) on the chest point {@code pocket}
     * (rest model px, carried by the chest), the bore along the view, at {@code k} times real size; model blocks.
     */
    static Matrix4f onShoulder(SkeletonPose pose, HumanoidPoser.Input in, PlayerSkeletonCheck.Gun g, Vector3f butt, Vector3f pocket, float k) {
        Vector3f at = pose.point(pose.rig.chest, new Vector3f(pocket), new Vector3f()).div(16F);
        Matrix4f view = PlayerSkeletonCheck.toModel().mul(GunStance.eye(PlayerSkeletonCheck.eyeO(in.crouch > 0.5F), in.viewYaw, in.viewPitch));
        view.setTranslation(0, 0, 0);
        GunStance.Profile p = g.profile();
        return new Matrix4f().translation(at).mul(view).scale(g.carried().realScale() * k)
                .rotate(new Quaternionf().rotationXYZ((float) Math.toRadians(p.adsPitch()), (float) Math.toRadians(p.adsYaw()), (float) Math.toRadians(p.adsRoll())))
                .translate(-butt.x / 16F, -butt.y / 16F, -butt.z / 16F);
    }

    /** One arm's best hold found by a sweep. */
    record Best(float score, String line, Matrix4f target, Vector3f pole) {}

    static void shoulder(SkeletonRig rig, HumanoidPoser poser) throws Exception {
        SkeletonPose pose = new SkeletonPose(rig);
        PlayerSkeletonCheck.Gun g = PlayerSkeletonCheck.Gun.of("br51_01");
        GunStance.Kind kind = g.carried().kind();
        List<Vector3f> pts = points(g);
        Vector3f butt = new Vector3f(0, 8F, 26.8F);
        float pitchRun = Float.parseFloat(System.getenv().getOrDefault("AFL_LAB_PITCH", "0"));
        float[] ks = floats(System.getenv().getOrDefault("AFL_LAB_K", "1,1.25,1.5,1.75,2"));
        float[] twists = floats(System.getenv().getOrDefault("AFL_LAB_TWIST", kind.chestTwist + "," + (kind.chestTwist + 20F)));
        Matrix4f rightRest = g.sampler().frame("right_hand_anchor", "static_idle", 0F);
        for (float k : ks) for (float twist : twists) {
            Vector3f pocket = new Vector3f(-3.6F, 1.5F, -2.2F);
            // the right hand: grip turns and poles swept
            Best bestR = null;
            for (float gp = -60; gp <= 20; gp += 20) for (float gy = -40; gy <= 40; gy += 20) for (float gr = -40; gr <= 40; gr += 20)
                for (float[] pole : new float[][]{{-0.35F, 1F, 0.45F}, {-0.5F, 1F, 1F}, {-1F, 1F, 0.3F}, {-0.2F, 1F, 1F}}) {
                    HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, twist, 0, pitchRun, true, false);
                    HumanoidPoser.Report r = poser.solveBody(in, pose);
                    Matrix4f gun = onShoulder(pose, in, g, butt, pocket, k);
                    setUp(in, kind, gun, g);
                    GunStance.handTarget(gun, rightRest, new GunStance.Grip(gp, gy, gr), true, false, in.targetRight);
                    in.poleRight.set(pole[0], pole[1], pole[2]);
                    poser.solveArms(in, pose, r);
                    PlayerSkeletonCheck.Hold h = PlayerSkeletonCheck.measure(rig, pose, in, false, pitchRun);
                    int[] c = inside(rig, pose, false, model(pts, gun), true, 0.15F);
                    float score = h.wristR() + 0.3F * h.twistR() + 2 * h.errR() + 5 * (c[0] + c[1]) + Math.max(0, r.rightReach - 1F) * 500;
                    if (bestR == null || score < bestR.score())
                        bestR = new Best(score, String.format("R grip %+3.0f,%+3.0f,%+3.0f pole %s reach %.2f wrist %2.0f twist %2.0f off %2.0f drop %3.0f clip %d/%d/%d",
                                gp, gy, gr, java.util.Arrays.toString(pole), r.rightReach, h.wristR(), h.twistR(), h.errR(), h.dropR(), c[0], c[1], c[2]),
                                new Matrix4f(in.targetRight), new Vector3f(pole[0], pole[1], pole[2]));
                }
            // the left hand under the handguard
            Best bestL = null;
            for (float z = -16; z <= -4; z += 3) for (float down = 10; down <= 60; down += 10) for (float out = 0; out <= 50; out += 10)
                for (float roll = 0; roll < 360; roll += 45) for (float[] pole : new float[][]{{0.35F, 1F, 0.45F}, {0.15F, 1F, 0.25F}, {0.6F, 1F, 0.2F}}) {
                    HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, twist, 0, pitchRun, true, false);
                    HumanoidPoser.Report r = poser.solveBody(in, pose);
                    Matrix4f gun = onShoulder(pose, in, g, butt, pocket, k);
                    setUp(in, kind, gun, g);
                    in.targetRight.set(bestR.target());
                    in.poleRight.set(bestR.pole());
                    float bot = bottom(pts, -0.2F, z, 1.2F);
                    Vector3f dir = new Vector3f(0, 0, 1).rotateX((float) Math.toRadians(down)).rotateY((float) Math.toRadians(-out));
                    target(gun, new Vector3f(-0.2F, bot, z), dir, roll, 0.3F, false, in.targetLeft);
                    in.poleLeft.set(pole[0], pole[1], pole[2]);
                    poser.solveArms(in, pose, r);
                    PlayerSkeletonCheck.Hold h = PlayerSkeletonCheck.measure(rig, pose, in, false, pitchRun);
                    int[] c = inside(rig, pose, false, model(pts, gun), false, 0.15F);
                    float score = h.wristL() + 0.3F * h.twistL() + 2 * h.errL() + 5 * (c[0] + c[1] + c[2]) + r.leftSlide * 20
                            + Math.max(0, r.leftReach - 1F) * 500 + h.centreL() * 30;
                    if (bestL == null || score < bestL.score())
                        bestL = new Best(score, String.format("L z %+3.0f down %2.0f out %2.0f roll %3.0f pole %s reach %.2f slide %.1f wrist %2.0f twist %2.0f off %2.0f drop %3.0f clip %d/%d/%d coverMid %2.0f%%",
                                z, down, out, roll, java.util.Arrays.toString(pole), r.leftReach, r.leftSlide, h.wristL(), h.twistL(), h.errL(), h.dropL(),
                                c[0], c[1], c[2], h.centreL() * 100), new Matrix4f(in.targetLeft), new Vector3f(pole[0], pole[1], pole[2]));
                }
            System.out.printf("LAB3 k %.2f twist %.0f pitch %.0f%n    %s%n    %s%n", k, twist, pitchRun, bestR.line(), bestL.line());
            String snap = System.getenv("AFL_SNAP");
            if (snap != null) {
                HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, twist, 0, pitchRun, true, false);
                HumanoidPoser.Report r = poser.solveBody(in, pose);
                Matrix4f gun = onShoulder(pose, in, g, butt, pocket, k);
                setUp(in, kind, gun, g);
                in.targetRight.set(bestR.target());
                in.poleRight.set(bestR.pole());
                in.targetLeft.set(bestL.target());
                in.poleLeft.set(bestL.pole());
                poser.solveArms(in, pose, r);
                java.io.File dir = new java.io.File(snap);
                dir.mkdirs();
                SkeletonSnapshot.views(rig, pose, g, gun, pitchRun, dir, String.format("k%03d_t%02d", Math.round(k * 100), Math.round(twist)));
            }
        }
    }

    private static float[] floats(String csv) {
        String[] parts = csv.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Float.parseFloat(parts[i].trim());
        return out;
    }

    private static void setUp(HumanoidPoser.Input in, GunStance.Kind kind, Matrix4f gun, PlayerSkeletonCheck.Gun g) {
        in.right = in.left = HumanoidPoser.ArmMode.IK;
        in.ikRightWeight = in.ikLeftWeight = 1F;
        in.shoulderRight.set(kind.shoulderRight);
        in.shoulderLeft.set(kind.shoulderLeft);
        in.poleRight.set(kind.poleRight);
        in.poleLeft.set(kind.poleLeft);
        GunStance.supportSlide(new Matrix4f().scaling(16F).mul(gun), g.profile(), kind.supportSlide, in.slideLeft);
        GunStance.handTarget(gun, g.sampler().frame("right_hand_anchor", "static_idle", 0F), kind.gripRight, true, in.slim, in.targetRight);
        GunStance.handTarget(gun, g.sampler().frame("left_hand_anchor", "static_idle", 0F), kind.gripLeft, false, in.slim, in.targetLeft);
    }

    /**
     * Tuning (AFL_LAB4): the rifle along the view, its butt level with the chest point {@code pocket}, slid along the bore
     * until the right wrist is at {@code reach} of the arm; then the best right grip and the left hand under the handguard.
     */
    static void extended(SkeletonRig rig, HumanoidPoser poser) throws Exception {
        SkeletonPose pose = new SkeletonPose(rig);
        PlayerSkeletonCheck.Gun g = PlayerSkeletonCheck.Gun.of("br51_01");
        GunStance.Kind kind = g.carried().kind();
        List<Vector3f> pts = points(g);
        Vector3f butt = new Vector3f(0, 8F, 26.8F);
        float pitchRun = Float.parseFloat(System.getenv().getOrDefault("AFL_LAB_PITCH", "0"));
        float reach = Float.parseFloat(System.getenv().getOrDefault("AFL_LAB_REACH", "0.92"));
        float[] ks = floats(System.getenv().getOrDefault("AFL_LAB_K", "1,1.25,1.5,1.75"));
        for (String pocketText : System.getenv().getOrDefault("AFL_LAB_POCKETS", "-2.5,1.5,-2.2").split(";")) {
        float[] pk = floats(pocketText);
        Vector3f pocket = new Vector3f(pk[0], pk[1], pk[2]);
        String tag = pocket.y < 0.5F ? "hi" : "ch";
        float[] twists = floats(System.getenv().getOrDefault("AFL_LAB_TWIST", "20,40"));
        Matrix4f rightRest = g.sampler().frame("right_hand_anchor", "static_idle", 0F);
        GunStance.Grip g0 = new GunStance.Grip(-40, 0, -20);
        for (float k : ks) for (float twist : twists) {
            // slide along the bore until the right wrist is at the wanted reach
            float lo = Float.parseFloat(System.getenv().getOrDefault("AFL_LAB_MINSLIDE", "-16")), hi = 16F;
            for (int it = 0; it < 30; it++) {
                float s = (lo + hi) / 2;
                HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, twist, 0, pitchRun, true, false);
                HumanoidPoser.Report r = poser.solveBody(in, pose);
                Matrix4f gun = slid(onShoulder(pose, in, g, butt, pocket, k), in, s);
                setUp(in, kind, gun, g);
                GunStance.handTarget(gun, rightRest, g0, true, false, in.targetRight);
                poser.solveArms(in, pose, r);
                if (r.rightReach > reach) hi = s; else lo = s;
            }
            final float slide = (lo + hi) / 2;
            Best bestR = null;
            for (float gp = -60; gp <= 20; gp += 20) for (float gy = -40; gy <= 40; gy += 20) for (float gr = -40; gr <= 40; gr += 20)
                for (float[] pole : new float[][]{{-0.35F, 1F, 0.45F}, {-0.5F, 1F, 1F}, {-1F, 1F, 0.3F}, {-0.2F, 1F, 1F}, {-1F, 0.5F, 0F}}) {
                    HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, twist, 0, pitchRun, true, false);
                    HumanoidPoser.Report r = poser.solveBody(in, pose);
                    Matrix4f gun = slid(onShoulder(pose, in, g, butt, pocket, k), in, slide);
                    setUp(in, kind, gun, g);
                    GunStance.handTarget(gun, rightRest, new GunStance.Grip(gp, gy, gr), true, false, in.targetRight);
                    in.poleRight.set(pole[0], pole[1], pole[2]);
                    poser.solveArms(in, pose, r);
                    PlayerSkeletonCheck.Hold h = PlayerSkeletonCheck.measure(rig, pose, in, false, pitchRun);
                    int[] c = inside(rig, pose, false, model(pts, gun), true, 0.15F);
                    float score = h.wristR() + 0.3F * h.twistR() + 2 * h.errR() + 2 * (c[0] + c[1]) + Math.max(0, r.rightReach - 1F) * 500;
                    if (bestR == null || score < bestR.score())
                        bestR = new Best(score, String.format("R grip %+3.0f,%+3.0f,%+3.0f pole %s reach %.2f elbow %3.0f wrist %2.0f twist %2.0f off %2.0f drop %3.0f clip %d/%d/%d",
                                gp, gy, gr, java.util.Arrays.toString(pole), r.rightReach, r.rightElbowDeg, h.wristR(), h.twistR(), h.errR(), h.dropR(), c[0], c[1], c[2]),
                                new Matrix4f(in.targetRight), new Vector3f(pole[0], pole[1], pole[2]));
                }
            Best bestL = null;
            for (float z : floats(System.getenv().getOrDefault("AFL_LAB_LZ", "-16,-13,-10,-7,-4,-1,2"))) for (float down = -30; down <= 50; down += 10) for (float out = 0; out <= 75; out += 15)
                for (float roll = 0; roll < 360; roll += 45) for (float[] pole : new float[][]{{0.35F, 1F, 0.45F}, {0.15F, 1F, 0.25F}, {0.6F, 1F, 0.2F}, {1F, 0.5F, 0F}}) {
                    HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, twist, 0, pitchRun, true, false);
                    HumanoidPoser.Report r = poser.solveBody(in, pose);
                    Matrix4f gun = slid(onShoulder(pose, in, g, butt, pocket, k), in, slide);
                    setUp(in, kind, gun, g);
                    in.targetRight.set(bestR.target());
                    in.poleRight.set(bestR.pole());
                    in.slideLeft.zero();
                    float bot = bottom(pts, -0.2F, z, 0.6F);
                    Vector3f dir = new Vector3f(0, 0, 1).rotateX((float) Math.toRadians(down)).rotateY((float) Math.toRadians(-out));
                    target(gun, new Vector3f(-0.2F, bot, z), dir, roll, 0.3F, false, in.targetLeft);
                    in.poleLeft.set(pole[0], pole[1], pole[2]);
                    poser.solveArms(in, pose, r);
                    PlayerSkeletonCheck.Hold h = PlayerSkeletonCheck.measure(rig, pose, in, false, pitchRun);
                    int[] c = inside(rig, pose, false, model(pts, gun), false, 0.15F);
                    // forward on the handguard is better; short of it is out
                    float score = h.wristL() + 0.3F * h.twistL() + 2 * h.errL() + 2 * (c[0] + c[1] + c[2]) + (z + 16) * 1.5F
                            + Math.max(0, r.leftReach - 1F) * 500 + h.centreL() * 30;
                    if (bestL == null || score < bestL.score())
                        bestL = new Best(score, String.format("L z %+3.0f down %2.0f out %2.0f roll %3.0f pole %s reach %.2f elbow %3.0f wrist %2.0f twist %2.0f off %2.0f drop %3.0f clip %d/%d/%d coverMid %2.0f%%",
                                z, down, out, roll, java.util.Arrays.toString(pole), r.leftReach, r.leftElbowDeg, h.wristL(), h.twistL(), h.errL(), h.dropL(),
                                c[0], c[1], c[2], h.centreL() * 100), new Matrix4f(in.targetLeft), new Vector3f(pole[0], pole[1], pole[2]));
                }
            // pose it again with both and say where the butt is (chest rest px; the chest front is z -2)
            HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, twist, 0, pitchRun, true, false);
            HumanoidPoser.Report r = poser.solveBody(in, pose);
            Matrix4f gun = slid(onShoulder(pose, in, g, butt, pocket, k), in, slide);
            setUp(in, kind, gun, g);
            in.targetRight.set(bestR.target());
            in.poleRight.set(bestR.pole());
            in.targetLeft.set(bestL.target());
            in.poleLeft.set(bestL.pole());
            in.slideLeft.zero();
            poser.solveArms(in, pose, r);
            Vector3f buttRest = new Matrix4f(pose.world[rig.chest]).invert().transformPosition(
                    new Matrix4f().scaling(16F).mul(gun).transformPosition(new Vector3f(butt).div(16F)));
            System.out.printf("LAB4 " + tag + " k %.2f twist %.0f pitch %.0f slide %.1f px, butt at chest rest (%.1f %.1f %.1f), gun length %.1f px%n    %s%n    %s%n",
                    k, twist, pitchRun, slide, buttRest.x, buttRest.y, buttRest.z, 54.8F * g.carried().realScale() * k, bestR.line(), bestL.line());
            String snap = System.getenv("AFL_SNAP");
            if (snap != null) {
                java.io.File dir = new java.io.File(snap);
                dir.mkdirs();
                SkeletonSnapshot.views(rig, pose, g, gun, pitchRun, dir, String.format("%s%03d_t%02d", tag, Math.round(k * 100), Math.round(twist)));
            }
        }
        }
    }

    /** A gun frame (model blocks) moved {@code px} model px ahead along the view. */
    static Matrix4f slid(Matrix4f gun, HumanoidPoser.Input in, float px) {
        Matrix4f view = PlayerSkeletonCheck.toModel().mul(GunStance.eye(PlayerSkeletonCheck.eyeO(in.crouch > 0.5F), in.viewYaw, in.viewPitch));
        Vector3f ahead = view.transformDirection(new Vector3f(0, 0, -1)).normalize(px / 16F);
        return new Matrix4f().translation(ahead).mul(gun);
    }

    /** Tuning (AFL_LAB5): where the right hand's target, wrist and cap end up against its anchor (model px). */
    static void debugRight(SkeletonRig rig, HumanoidPoser poser) throws Exception {
        SkeletonPose pose = new SkeletonPose(rig);
        PlayerSkeletonCheck.Gun g = PlayerSkeletonCheck.Gun.of("br51_01");
        GunStance.Kind kind = g.carried().kind();
        Matrix4f rightRest = g.sampler().frame("right_hand_anchor", "static_idle", 0F);
        for (GunStance.Grip grip : new GunStance.Grip[]{GunStance.Grip.NONE, kind.gripRight}) {
            HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, kind.chestTwist, 0, 0, true, false);
            HumanoidPoser.Report r = PlayerSkeletonCheck.gunSolve(poser, in, pose, g, 1, 0, 0, "static_idle", 0);
            Matrix4f gun = PlayerSkeletonCheck.lastGun;
            GunStance.handTarget(gun, rightRest, grip, true, false, in.targetRight);
            poser.solveArms(in, pose, r);
            Vector3f anchor = new Matrix4f().scaling(16F).mul(gun).mul(rightRest).transformPosition(new Vector3f());
            Vector3f sh = new Vector3f(rig.pivot[rig.upperArmR]);
            Vector3f capRest = new Vector3f(sh).add(GunStance.centreX(true, false), GunStance.DISTAL_Y, 0);
            Vector3f capT = in.targetRight.transformPosition(new Vector3f(capRest).sub(sh));
            Vector3f wristT = in.targetRight.transformPosition(new Vector3f(rig.pivot[rig.handR]).sub(sh));
            Vector3f shoulderT = in.targetRight.transformPosition(new Vector3f());
            Vector3f wrist = pose.point(rig.handR, rig.pivot[rig.handR], new Vector3f());
            Vector3f cap = pose.point(rig.handR, capRest, new Vector3f());
            Vector3f shoulder = pose.point(rig.upperArmR, sh, new Vector3f());
            System.out.printf("LAB5 grip %s reach %.2f%n  anchor %s%n  cap target %s  posed cap %s%n  wrist target %s  posed wrist %s%n  target's shoulder %s  posed shoulder %s%n  rest pivots shoulder %s hand %s scale %s%n",
                    grip, r.rightReach, anchor, capT, cap, wristT, wrist, shoulderT, shoulder, sh, rig.pivot[rig.handR],
                    new Matrix4f().scaling(16F).mul(gun).mul(rightRest).getScale(new Vector3f()));
        }
    }

    /** Tuning (AFL_LAB6): the left hand's target turn between the clip's anchor (end of a reload) and the support under the handguard. */
    static void reloadEnd(SkeletonRig rig, HumanoidPoser poser) throws Exception {
        SkeletonPose pose = new SkeletonPose(rig);
        PlayerSkeletonCheck.Gun g = PlayerSkeletonCheck.Gun.of("br51_01");
        for (String clip : new String[]{"static_idle", "reload_tactical", "reload_empty", "reload_empty_drum", "reload_magazine", "inspect"}) {
            float len = clip.equals("static_idle") ? 0 : g.sampler().length(clip);
            for (float t : clip.equals("static_idle") ? new float[]{0} : new float[]{len - 0.3F, len - 0.1F, len}) {
                HumanoidPoser.Input in = PlayerSkeletonCheck.standing(false, g.carried().kind().chestTwist, 0, 0, true, false);
                PlayerSkeletonCheck.gunSolve(poser, in, pose, g, 1, 0, 0, clip, t);
                Matrix4f gun = PlayerSkeletonCheck.lastGun;
                Matrix4f anchor = GunStance.handTarget(gun, g.sampler().frame("left_hand_anchor", clip, t, null, "static_idle"), GunStance.Grip.NONE, false, false, new Matrix4f());
                Matrix4f under = GunStance.supportUnder(gun, g.carried().support(), false, new Matrix4f());
                Quaternionf qa = anchor.getUnnormalizedRotation(new Quaternionf()), qu = under.getUnnormalizedRotation(new Quaternionf());
                float turn = (float) Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(qa.dot(qu)))));
                float move = anchor.getTranslation(new Vector3f()).distance(under.getTranslation(new Vector3f()));
                System.out.printf("LAB6 %s t %.2f: anchor -> under: hand turns %.0f deg, moves %.1f px%n", clip, t, turn, move);
            }
        }
    }

    static List<Vector3f> model(List<Vector3f> gunPx, Matrix4f gun) {
        Matrix4f px = new Matrix4f().scaling(16F).mul(gun).scale(1F / 16F);
        List<Vector3f> out = new ArrayList<>(gunPx.size());
        for (Vector3f p : gunPx) out.add(px.transformPosition(p, new Vector3f()));
        return out;
    }
}
