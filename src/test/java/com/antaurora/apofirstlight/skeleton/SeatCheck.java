package com.antaurora.apofirstlight.skeleton;

import com.antaurora.apofirstlight.skeleton.seat.RiderSeat;
import com.antaurora.apofirstlight.skeleton.seat.SeatPose;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.util.Locale;

/**
 * Seated riders V1 (docs/dev/seated_rider_v1_plan.md 6): the T15's seats offline. For the driver, the passenger and a bed
 * bench, seated still and through the get-in / get-out moves: torso share, eye and head top against the roof, hands on
 * the wheel's grips, soles on the floor, thighs on the cushion. Pictures with {@code AFL_SNAP_SEAT=<dir>}.
 * <p>T15 rig frame (metres): X the truck's right, Y up from the ground, Z back (src/main/blockbench/vehicles/pickup_t15.rig.json,
 * tools/pickup-t15/cab.mjs). The truck faces world +z (yaw 0): rig (X, Y, Z) is world (-X, Y, -Z).
 */
final class SeatCheck {
    private static int failures;
    static final float ROOF = 1.69F, FLOOR = 0.535F, BED_FLOOR = 0.772F;

    /** A T15 seat: rig x, z of the hips, the cushion top, the floor, the facing (deg, + right), the door side. */
    record T15(String name, RiderSeat.Kind kind, float x, float z, float cushion, float floor, float yaw, int doorSide) {}

    static final T15 DRIVER = new T15("driver", RiderSeat.Kind.DRIVER, -0.37F, 0.06F, 0.845F, FLOOR, 0F, -1);
    static final T15 PASSENGER = new T15("passenger", RiderSeat.Kind.PASSENGER, 0.37F, 0.06F, 0.845F, FLOOR, 0F, 1);
    static final T15 BENCH = new T15("bench", RiderSeat.Kind.BENCH, -0.63F, 1.00F, 0.89F, BED_FLOOR, 90F, 0);

    private SeatCheck() {
    }

    /** The frame for one draw: where the render origin is (truck world, metres), the body yaw, the draw scale. */
    record Frame(Vector3f origin, float yaw, float scale) {
        Matrix4f toModel() {
            return new Matrix4f().translation(0, 1.501F, 0).scale(1F / 0.9375F).scale(-1, -1, 1)
                    .rotateY((float) Math.toRadians(yaw - 180F)).scale(1F / scale);
        }

        /** A rig point to model px. */
        Vector3f model(float x, float y, float z) {
            return toModel().transformPosition(new Vector3f(-x, y, -z).sub(origin)).mul(16F);
        }

        /** Model px back to rig metres. */
        Vector3f rig(Vector3f model) {
            Vector3f w = new Matrix4f(toModel()).invert().transformPosition(new Vector3f(model).div(16F)).add(origin);
            return new Vector3f(-w.x, w.y, -w.z);
        }

        /** A world yaw to the model's turn (rest body -> facing that way). */
        Quaternionf turn(float worldYaw) {
            return new Quaternionf().rotationY((float) Math.toRadians(worldYaw - yaw));
        }
    }

    /** A rig point turned about the left (or right) door's hinge by the door's angle (deg), as PickupTruckRenderer. */
    static float[] door(T15 t, float x, float y, float z, float angle) {
        float side = t.doorSide < 0 ? -1 : 1, px = 0.825F * side, pz = -0.916F;
        double a = Math.toRadians(t.doorSide < 0 ? -angle : angle), c = Math.cos(a), s = Math.sin(a);
        float rx = x - px, rz = z - pz;
        return new float[]{(float) (px + rx * c + rz * s), y, (float) (pz - rx * s + rz * c)};
    }

    /** The seat in model px for a frame (the door open by {@code door} 0..1). */
    static SeatPose.Seat seat(T15 t, Frame f, float door) {
        SeatPose.Seat s = new SeatPose.Seat();
        s.kind = t.kind;
        s.cushion.set(f.model(t.x, t.cushion, t.z));
        s.turn.set(f.turn(t.yaw));
        s.doorSide = t.doorSide;
        s.door = door;
        float angle = 68F * door;
        if (t.kind == RiderSeat.Kind.DRIVER) {
            s.anchors.put("grip_l", f.model(-0.56F, 1.125F, -0.40F));
            s.anchors.put("grip_r", f.model(-0.18F, 1.125F, -0.40F));
        }
        // floor rests: in the seat's frame, ahead of the cushion's front edge, under the thighs
        // rig: the truck's forward is -Z, its right +X; a seat turned yaw deg right faces (sin, 0, -cos) in rig x/z
        float fx = (float) Math.sin(Math.toRadians(t.yaw)), fz = (float) -Math.cos(Math.toRadians(t.yaw));
        float rx = -fz, rz = fx;   // the seat's right in rig x/z
        float ahead = t.kind == RiderSeat.Kind.BENCH ? 0.30F : 0.40F;
        for (int side = -1; side <= 1; side += 2) {
            float x = t.x + fx * ahead + rx * side * 0.10F, z = t.z + fz * ahead + rz * side * 0.10F;
            s.anchors.put(side > 0 ? "floor_r" : "floor_l", f.model(x, t.floor, z));
        }
        if (t.doorSide != 0) {
            float sd = t.doorSide;
            float[] pull = door(t, 0.76F * sd, 0.95F, -0.12F, angle), outer = door(t, 0.85F * sd, 0.98F, 0.057F, angle);
            s.anchors.put("door_pull", f.model(pull[0], pull[1], pull[2]));
            s.anchors.put("door_outer", f.model(outer[0], outer[1], outer[2]));
        }
        return s;
    }

    static int run(SkeletonRig rig, HumanoidPoser poser, String snap) throws Exception {
        failures = 0;
        File dir = snap == null ? null : new File(snap);
        if (dir != null) dir.mkdirs();
        System.out.println("\nseated riders (scale " + SeatPose.SCALE + ", eye " + SeatPose.EYE_OVER_SEAT + " m over the cushion; T15 roof " + ROOF + ")");
        float torso = seated(rig, poser, DRIVER, dir);
        seated(rig, poser, PASSENGER, dir);
        seated(rig, poser, BENCH, dir);
        moves(rig, poser, DRIVER, torso, dir);
        moves(rig, poser, PASSENGER, torso, dir);
        bench(rig, poser, torso);
        if (dir != null) {
            SkeletonSnapshot.toWorld = PlayerSkeletonCheck.toModel().invert();
            SkeletonSnapshot.scenery.clear();
        }
        return failures;
    }

    private static void fail(String message) {
        failures++;
        System.out.println("  FAIL " + message);
    }

    /**
     * The rider's render origin (truck world metres) for the proposed placement: the eye EYE_OVER_SEAT over the cushion
     * and EYE_AHEAD ahead of its point.
     */
    static Vector3f riderOrigin(T15 t) {
        float eye = t.cushion + SeatPose.EYE_OVER_SEAT;
        double a = Math.toRadians(t.yaw);
        float fx = (float) Math.sin(a) * SeatPose.EYE_AHEAD, fz = (float) -Math.cos(a) * SeatPose.EYE_AHEAD;   // rig x/z ahead
        return new Vector3f(-(t.x + fx), eye - 1.62F, -(t.z + fz));
    }

    private static float seated(SkeletonRig rig, HumanoidPoser poser, T15 t, File dir) throws Exception {
        Frame f = new Frame(riderOrigin(t), t.yaw, SeatPose.SCALE);
        SeatPose.Seat seat = seat(t, f, 0F);
        float eyeLevel = f.toModel().transformPosition(new Vector3f(0, 1.62F, 0)).y * 16F;
        float worst = 0;
        String worstAt = "";
        for (float[] look : new float[][]{{0, 0}, {0, 40}, {-60, 20}, {60, 20}, {0, -30}}) {
            HumanoidPoser.Input in = new HumanoidPoser.Input();
            in.viewYaw = look[0];
            in.viewPitch = look[1];
            in.chestPitch = HumanoidPoser.chestPitch(look[1], false);
            SkeletonPose pose = new SkeletonPose(rig);
            SeatPose.Moment m = SeatPose.moment(SeatPose.Move.NONE, t.doorSide != 0, 0, 0);
            SeatPose.body(in, seat, new SeatPose.Stand(), m, 0.72F, eyeLevel);
            HumanoidPoser.Report r = poser.solveBody(in, pose);
            SeatPose.hands(in, pose, poser, seat, m);
            poser.solveArms(in, pose, r);
            Vector3f eye = f.rig(pose.point(rig.head, rig.sockets.get("eye").pos(), new Vector3f()));
            Vector3f top = f.rig(pose.point(rig.head, new Vector3f(0, -8, 0), new Vector3f()));
            // the hat layer's top over the head's top corners (looking around tips them up)
            float headTop = -1;
            for (float[] c : new float[][]{{-4.5F, -8.5F, -4.5F}, {4.5F, -8.5F, -4.5F}, {-4.5F, -8.5F, 4.5F}, {4.5F, -8.5F, 4.5F}})
                headTop = Math.max(headTop, f.rig(pose.point(rig.head, new Vector3f(c[0], c[1], c[2]), new Vector3f())).y);
            float wristR = wristError(rig, pose, in, true), wristL = wristError(rig, pose, in, false);
            float soleR = f.rig(pose.point(rig.footR, new Vector3f(rig.pivot[rig.footR]).add(0, 2, 0), new Vector3f())).y - t.floor;
            float soleL = f.rig(pose.point(rig.footL, new Vector3f(rig.pivot[rig.footL]).add(0, 2, 0), new Vector3f())).y - t.floor;
            float[] thigh = thighOnCushion(rig, pose, f, t);
            if (look[0] == 0 && look[1] == 0) {
                System.out.printf(Locale.ROOT, "  %-9s torso %.2f  eye y %.3f (camera %.3f) z %+.3f from the hips  head top %.3f (hat %.3f)  hands reach R %.2f L %.2f, wrist off R %.2f L %.2f px%n",
                        t.name, r.torso, eye.y, t.cushion + SeatPose.EYE_OVER_SEAT, t.z - eye.z, top.y, headTop, r.rightReach, r.leftReach, wristR, wristL);
                System.out.printf(Locale.ROOT, "            legs reach R %.2f L %.2f, soles over the floor R %+.3f L %+.3f m, thigh underside vs cushion: deepest %+.3f m, highest %+.3f m%n",
                        r.rightLegReach, r.leftLegReach, soleR, soleL, thigh[0], thigh[1]);
            }
            if (t.cushion < 1F && headTop > worst) { worst = headTop; worstAt = look[0] + "/" + look[1]; }
            if (t.kind == RiderSeat.Kind.DRIVER && (wristR > 0.6F || wristL > 0.6F))
                fail(t.name + " look " + look[0] + "/" + look[1] + ": a hand misses its grip (" + wristR + ", " + wristL + " px)");
            if (Math.abs(soleR) > 0.04F || Math.abs(soleL) > 0.04F)
                fail(t.name + " look " + look[0] + "/" + look[1] + ": a sole is off the floor (" + soleR + ", " + soleL + ")");
            if (thigh[0] < -0.07F) fail(t.name + ": a thigh sinks " + thigh[0] + " m into the cushion");
            Vector3f camera = new Vector3f(f.origin).add(0, 1.62F, 0);
            float level = (float) Math.hypot(eye.x + camera.x, eye.z + camera.z);   // rig x/z are world -x/-z
            if (look[0] == 0 && look[1] == 0) System.out.printf(Locale.ROOT, "            drawn eye off the camera: %.3f m level%n", level);
            if (look[0] == 0 && look[1] == 0 && level > 0.03F) fail(t.name + ": the drawn eye is " + level + " m off the camera");
            if (Math.abs(eye.y - (t.cushion + SeatPose.EYE_OVER_SEAT)) > 0.01F && look[1] == 0)
                fail(t.name + ": the drawn eye is off the camera height (" + eye.y + ")");
            if (dir != null && look[0] == 0 && (look[1] == 0 || look[1] == 40)) {
                snapshots(rig, pose, in, f, t, seat, eye, look[1], dir, "seat_" + t.name + "_p" + (int) look[1]);
            }
            if (look[0] == 0 && look[1] == 0) torsoOut = r.torso;
        }
        if (t.doorSide != 0) {
            System.out.printf(Locale.ROOT, "            hat top highest %.3f at look %s (roof %.2f)%n", worst, worstAt, ROOF);
            if (worst > ROOF - 0.01F) fail(t.name + ": the head reaches the roof (" + worst + ")");
        }
        return torsoOut;
    }

    private static float torsoOut = 0.72F;

    /** The posed wrist's distance from its IK target's wrist (px). */
    private static float wristError(SkeletonRig rig, SkeletonPose pose, HumanoidPoser.Input in, boolean right) {
        if ((right ? in.right : in.left) != HumanoidPoser.ArmMode.IK) return 0;
        int hand = right ? rig.handR : rig.handL, upper = right ? rig.upperArmR : rig.upperArmL;
        Vector3f want = (right ? in.targetRight : in.targetLeft).transformPosition(new Vector3f(rig.pivot[hand]).sub(rig.pivot[upper]));
        return pose.point(hand, rig.pivot[hand], new Vector3f()).distance(want);
    }

    /**
     * The thighs' underside against the cushion's top (cab: the cushion profile of cab.mjs, rising from 0.842 at the back
     * to 0.878 near the front edge at Z -0.29; bench: flat): {deepest, highest} gap over the part above the cushion, m.
     */
    private static float[] thighOnCushion(SkeletonRig rig, SkeletonPose pose, Frame f, T15 t) {
        float deepest = Float.MAX_VALUE, highest = -Float.MAX_VALUE;
        for (int thigh : new int[]{rig.thighR, rig.thighL}) {
            Vector3f p = rig.pivot[thigh];
            for (int i = 0; i <= 6; i++) for (int k = -1; k <= 1; k++) {
                // the back face (z +2) of the thigh's box, which faces down when the thigh lies forward
                Vector3f m = pose.point(thigh, new Vector3f(p.x + k * 1.8F, p.y + i, p.z + 2F), new Vector3f());
                Vector3f w = f.rig(m);
                float cushion = cushionTop(t, w);
                if (Float.isNaN(cushion)) continue;
                deepest = Math.min(deepest, w.y - cushion);
                highest = Math.max(highest, w.y - cushion);
            }
        }
        return new float[]{deepest == Float.MAX_VALUE ? 0 : deepest, highest == -Float.MAX_VALUE ? 0 : highest};
    }

    /** The cushion's top under a rig point, NaN past its front edge. */
    private static float cushionTop(T15 t, Vector3f w) {
        if (t.kind == RiderSeat.Kind.BENCH) return Math.abs(w.x - t.x) < 0.12F && Math.abs(w.z - t.z) < 0.24F ? t.cushion : Float.NaN;
        float gx = -w.z;   // cab.mjs: x forward
        if (gx > 0.29F || gx < -0.21F) return Float.NaN;
        return 0.842F + (0.878F - 0.842F) * (gx + 0.21F) / 0.41F;
    }

    /** The get-in / get-out moves through the door: timings, door, hands on the pull and the handle. */
    private static void moves(SkeletonRig rig, HumanoidPoser poser, T15 t, float torso, File dir) throws Exception {
        System.out.println("  " + t.name + " get in (door shuts " + SeatPose.SHUT_IN_TICKS + " ticks after sitting down) / get out (shuts " + SeatPose.SHUT_OUT_TICKS + " ticks after getting up):");
        float sd = t.doorSide;
        for (SeatPose.Move move : new SeatPose.Move[]{SeatPose.Move.GET_IN, SeatPose.Move.GET_OUT}) {
            boolean in = move == SeatPose.Move.GET_IN;
            float end = in ? SeatPose.IN_END : SeatPose.OUT_MAX;
            float pullBest = Float.MAX_VALUE, pushBest = Float.MAX_VALUE;
            StringBuilder line = new StringBuilder();
            for (float s = 0; s <= end + 1e-3F; s += 0.1F) {
                float door = in ? 1 - SeatPose.smooth((s - SeatPose.SHUT_IN_TICKS / 20F) / 0.7F)
                        : SeatPose.smooth(s / 0.7F) * (1 - SeatPose.smooth((s - SeatPose.SHUT_OUT_TICKS / 20F) / 0.7F));
                SeatPose.Moment m = SeatPose.moment(move, true, s, door);
                float scale = SeatPose.scale(m.seated);
                // get in: the render origin is the seat (the body yaw the seat's); get out: the exit spot, facing out
                Vector3f exit = new Vector3f(-(1.45F * sd), 0, -(-0.30F));
                Frame f = in ? new Frame(riderOrigin(t), t.yaw, scale) : new Frame(exit, sd < 0 ? -90F : 90F, scale);
                SeatPose.Seat seat = seat(t, f, door);
                SeatPose.Stand stand = new SeatPose.Stand();
                if (in) {
                    stand.feet.set(f.model(1.45F * sd, 0, -0.30F));
                    stand.turn.set(f.turn(sd < 0 ? 90F : -90F));   // stood facing the cab
                }
                HumanoidPoser.Input input = new HumanoidPoser.Input();
                SkeletonPose pose = new SkeletonPose(rig);
                SeatPose.body(input, seat, stand, m, torso, Float.NaN);
                HumanoidPoser.Report r = poser.solveBody(input, pose);
                SeatPose.hands(input, pose, poser, seat, m);
                poser.solveArms(input, pose, r);
                if (m.pull > 0.9F) {
                    boolean right = t.doorSide > 0;
                    int hand = right ? rig.handR : rig.handL;
                    pullBest = Math.min(pullBest, pose.point(hand, rig.pivot[hand], new Vector3f()).distance(seat.anchors.get("door_pull")) * f.scale * 0.9375F / 16F);
                }
                if (m.push > 0.9F) {
                    Vector3f outer = seat.anchors.get("door_outer");
                    int hand = outer.x < 0 ? rig.handR : rig.handL;
                    pushBest = Math.min(pushBest, pose.point(hand, rig.pivot[hand], new Vector3f()).distance(outer) * f.scale * 0.9375F / 16F);
                }
                if (!in && m.done && s < end) end = s;
                line.append(String.format(Locale.ROOT, " %.1f:%s%.2f/%.2f", s, m.seated >= 0.999F ? "S" : m.seated <= 0.001F ? "U" : "~", door, m.pull + m.push));
                if (dir != null && (Math.abs(s * 10 - Math.round(s * 10)) < 1e-3) && Math.round(s * 10) % 3 == 0) {
                    Vector3f eye = f.rig(pose.point(rig.head, rig.sockets.get("eye").pos(), new Vector3f()));
                    snapshots(rig, pose, input, f, t, seat, eye, 0, dir, String.format(Locale.ROOT, "move_%s_%s_%02d", t.name, in ? "in" : "out", Math.round(s * 10)));
                }
            }
            System.out.println("    " + (in ? "in " : "out") + line);
            if (in) System.out.printf(Locale.ROOT, "    hand on the door pull while it holds it: nearest %.3f m%n", pullBest);
            else System.out.printf(Locale.ROOT, "    hand on the pull %.3f m, on the outside handle while pushing %.3f m%n", pullBest, pushBest);
            if (in && pullBest > 0.12F) fail(t.name + " get in: the hand never comes near the door pull (" + pullBest + " m)");
            if (!in && pushBest > 0.12F) fail(t.name + " get out: the hand never comes near the outside handle (" + pushBest + " m)");
        }
    }

    /** A bench: the half-second moves finish seated / standing. */
    private static void bench(SkeletonRig rig, HumanoidPoser poser, float torso) {
        SeatPose.Moment a = SeatPose.moment(SeatPose.Move.GET_IN, false, SeatPose.BENCH_BODY, 0);
        SeatPose.Moment b = SeatPose.moment(SeatPose.Move.GET_OUT, false, SeatPose.BENCH_BODY, 0);
        if (a.seated < 0.999F || b.seated > 0.001F) fail("bench moves do not end seated / standing");
        else System.out.println("  bench: in / out over " + SeatPose.BENCH_BODY + " s");
    }

    // ------------------------------------------------------------------ pictures

    private static void snapshots(SkeletonRig rig, SkeletonPose pose, HumanoidPoser.Input in, Frame f, T15 t, SeatPose.Seat seat,
                                  Vector3f eyeRig, float pitch, File dir, String base) throws Exception {
        Matrix4f toWorld = new Matrix4f(f.toModel()).invert();
        SkeletonSnapshot.toWorld = toWorld;
        SkeletonSnapshot.marks.clear();
        SkeletonSnapshot.anchors.clear();
        for (boolean right : new boolean[]{true, false}) {
            int hand = right ? rig.handR : rig.handL, upper = right ? rig.upperArmR : rig.upperArmL;
            SkeletonSnapshot.marks.add((right ? in.targetRight : in.targetLeft).transformPosition(new Vector3f(rig.pivot[hand]).sub(rig.pivot[upper])));
        }
        SkeletonSnapshot.marks.add(pose.point(rig.handR, rig.pivot[rig.handR], new Vector3f()));
        SkeletonSnapshot.marks.add(pose.point(rig.handL, rig.pivot[rig.handL], new Vector3f()));
        SkeletonSnapshot.anchors.addAll(seat.anchors.values());
        scenery(t, f);
        Vector3f eye = new Vector3f(-eyeRig.x, eyeRig.y, -eyeRig.z).sub(f.origin);
        Matrix4f fp = GunStance.eye(eye, t.yaw, pitch).invert().mul(toWorld);
        Vector3f hip = new Vector3f(-t.x, t.cushion + 0.1F, -t.z).sub(f.origin);
        SkeletonSnapshot.bodyViews(rig, pose, fp, hip, dir, base);
    }

    /** Grey context: cushion, backrest, floor, steering wheel (cab), as world polygons around the render origin. */
    private static void scenery(T15 t, Frame f) {
        SkeletonSnapshot.scenery.clear();
        java.util.function.Function<float[], Vector3f> w = p -> new Vector3f(-p[0], p[1], -p[2]).sub(f.origin);
        java.util.function.Consumer<float[][]> quad = q -> {
            Vector3f[] v = new Vector3f[q.length];
            for (int i = 0; i < q.length; i++) v[i] = w.apply(q[i]);
            SkeletonSnapshot.scenery.add(v);
        };
        if (t.kind == RiderSeat.Kind.BENCH) {
            quad.accept(new float[][]{{-0.757F, 0.89F, 0.76F}, {-0.56F, 0.89F, 0.76F}, {-0.56F, 0.89F, 1.74F}, {-0.757F, 0.89F, 1.74F}});
            quad.accept(new float[][]{{-0.757F, BED_FLOOR, 0.40F}, {0.757F, BED_FLOOR, 0.40F}, {0.757F, BED_FLOOR, 2.2F}, {-0.757F, BED_FLOOR, 2.2F}});
            return;
        }
        float x0 = t.x - 0.27F, x1 = t.x + 0.27F;
        quad.accept(new float[][]{{x0, 0.842F, 0.21F}, {x1, 0.842F, 0.21F}, {x1, 0.878F, -0.29F}, {x0, 0.878F, -0.29F}});
        quad.accept(new float[][]{{x0, 0.83F, 0.10F}, {x1, 0.83F, 0.10F}, {x1, 1.39F, 0.155F}, {x0, 1.39F, 0.155F}});
        quad.accept(new float[][]{{-0.74F, FLOOR, 0.0F}, {0.74F, FLOOR, 0.0F}, {0.74F, FLOOR, -0.95F}, {-0.74F, FLOOR, -0.95F}});
        if (t.kind == RiderSeat.Kind.DRIVER) {
            double tilt = Math.toRadians(27);
            float cy = 1.125F, cz = -0.40F, cx = -0.37F;
            for (int i = 0; i < 16; i++) {
                double a0 = 2 * Math.PI * i / 16, a1 = 2 * Math.PI * (i + 1) / 16;
                float[][] q = new float[4][];
                double[] as = {a0, a1, a1, a0};
                float[] rs = {0.18F, 0.18F, 0.20F, 0.20F};
                for (int k = 0; k < 4; k++) {
                    double lx = Math.cos(as[k]) * rs[k], lu = Math.sin(as[k]) * rs[k];
                    q[k] = new float[]{cx + (float) lx, cy + (float) (lu * Math.cos(tilt)), cz - (float) (lu * Math.sin(tilt))};
                }
                quad.accept(q);
            }
            for (float px : new float[]{-0.53F, -0.41F, -0.25F})
                quad.accept(new float[][]{{px - 0.04F, 0.60F, -0.735F}, {px + 0.04F, 0.60F, -0.735F}, {px + 0.04F, 0.68F, -0.735F}, {px - 0.04F, 0.68F, -0.735F}});
        }
    }
}
