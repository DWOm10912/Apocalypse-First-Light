package com.antaurora.apofirstlight.skeleton;

import com.antaurora.apofirstlight.skeleton.seat.RiderSeat;
import com.antaurora.apofirstlight.skeleton.seat.SeatPose;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout;
import com.antaurora.apofirstlight.vehicle.ShiftLever;
import com.antaurora.apofirstlight.vehicle.SteeringHands;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.util.Locale;

/**
 * Seated riders V1 (docs/dev/seated_rider_v1_plan.md 6): the T15's seats offline. For the driver, the passenger and a bed
 * bench, seated still and through the get-in / get-out moves: torso share, eye and head top against the roof, hands on
 * the wheel's grips (also with the wheel turned), soles on the floor, thighs on the cushion, the head upright looking
 * back over a shoulder. Pictures with {@code AFL_SNAP_SEAT=<dir>}.
 * <p>T15 rig frame (metres): X the truck's right, Y up from the ground, Z back (src/main/blockbench/vehicles/pickup_t15.rig.json,
 * tools/pickup-t15/cab.mjs). The truck faces world +z (yaw 0): rig (X, Y, Z) is world (-X, Y, -Z).
 */
final class SeatCheck {
    private static int failures;
    /** The headliner's underside (the roof went up 10 cm 2026-10-16), the cab floor, the bed floor. */
    static final float ROOF = 1.79F, FLOOR = 0.535F, BED_FLOOR = 0.772F;

    /** A T15 seat: rig x, z of the hips, the cushion top, the floor, the facing (deg, + right), the door side. */
    record T15(String name, RiderSeat.Kind kind, float x, float z, float cushion, float floor, float yaw, int doorSide) {}

    static final T15 DRIVER = new T15("driver", RiderSeat.Kind.DRIVER, -0.37F, 0.06F, 0.815F, FLOOR, 0F, -1);
    static final T15 PASSENGER = new T15("passenger", RiderSeat.Kind.PASSENGER, 0.37F, 0.06F, 0.815F, FLOOR, 0F, 1);
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

    /** The seat in model px for a frame (the door open by {@code door} 0..1), the hands on their rests. */
    static SeatPose.Seat seat(T15 t, Frame f, float door) {
        return seat(t, f, door, new double[2], new double[2]);
    }

    /** The seat with the hands this far round the rim from their rests (deg, left / right; SteeringHands) and off it (m). */
    static SeatPose.Seat seat(T15 t, Frame f, float door, double[] hand, double[] lift) {
        SeatPose.Seat s = new SeatPose.Seat();
        s.kind = t.kind;
        s.cushion.set(f.model(t.x, t.cushion, t.z));
        s.turn.set(f.turn(t.yaw));
        s.doorSide = t.doorSide;
        s.door = door;
        float angle = 68F * door;
        if (t.kind == RiderSeat.Kind.DRIVER) {
            double[] l = PickupTruckLayout.grip(false, Math.toRadians(hand[0]), lift[0]), r = PickupTruckLayout.grip(true, Math.toRadians(hand[1]), lift[1]);
            s.anchors.put("grip_l", f.model((float) l[0], (float) l[1], (float) l[2]));
            s.anchors.put("grip_r", f.model((float) r[0], (float) r[1], (float) r[2]));
            double[] hub = PickupTruckLayout.HUB, axis = PickupTruckLayout.AXIS;
            s.anchors.put("wheel_hub", f.model((float) hub[0], (float) hub[1], (float) hub[2]));
            s.anchors.put("wheel_axis", f.model((float) axis[0], (float) axis[1], (float) axis[2]));
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
        for (float[] look : new float[][]{{0, 0}, {0, 40}, {-60, 20}, {60, 20}, {0, -30}, {-90, 0}, {90, 0}, {-120, 0}, {120, 0}, {120, 30}, {-120, -20}}) {
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
            // the head upright (no flip past the neck's turn) and how far it turns off the seat's way
            Vector3f neck = f.rig(pose.point(rig.head, new Vector3f(0, 0, 0), new Vector3f()));
            Vector3f crown = f.rig(pose.point(rig.head, new Vector3f(0, -8, 0), new Vector3f()));
            Vector3f face = f.rig(pose.point(rig.head, new Vector3f(0, -4, -4), new Vector3f())).sub(f.rig(pose.point(rig.head, new Vector3f(0, -4, 0), new Vector3f())));
            float upright = new Vector3f(crown).sub(neck).normalize().y;
            float faceYaw = (float) Math.toDegrees(Math.atan2(face.x, -face.z)) - t.yaw;   // off the seat's way (rig: forward -Z, right +X)
            faceYaw = (faceYaw + 540F) % 360F - 180F;
            if (Math.abs(look[0]) >= 90)
                System.out.printf(Locale.ROOT, "            look %+.0f/%+.0f: head up %.2f, face turned %+.0f deg%s%n", look[0], look[1], upright, faceYaw,
                        t.kind == RiderSeat.Kind.DRIVER ? String.format(Locale.ROOT, ", wrist off R %.2f L %.2f px", wristR, wristL) : "");
            if (upright < 0.5F) fail(t.name + " look " + look[0] + "/" + look[1] + ": the head tips over (up " + upright + ")");
            if (t.kind == RiderSeat.Kind.DRIVER && (wristR > 0.6F || wristL > 0.6F))
                fail(t.name + " look " + look[0] + "/" + look[1] + ": a hand misses its grip (" + wristR + ", " + wristL + " px)");
            if (Math.abs(soleR) > 0.04F || Math.abs(soleL) > 0.04F)
                fail(t.name + " look " + look[0] + "/" + look[1] + ": a sole is off the floor (" + soleR + ", " + soleL + ")");
            if (thigh[0] < -0.07F) fail(t.name + ": a thigh sinks " + thigh[0] + " m into the cushion");
            Vector3f camera = new Vector3f(f.origin).add(0, 1.62F, 0);
            float level = (float) Math.hypot(eye.x + camera.x, eye.z + camera.z);   // rig x/z are world -x/-z
            if (look[0] == 0 && look[1] == 0) System.out.printf(Locale.ROOT, "            drawn eye off the camera: %.3f m level%n", level);
            // 3 cm at the first 0.72 size, with the size
            if (look[0] == 0 && look[1] == 0 && level > 0.042F * SeatPose.SCALE) fail(t.name + ": the drawn eye is " + level + " m off the camera");
            if (Math.abs(eye.y - (t.cushion + SeatPose.EYE_OVER_SEAT)) > 0.01F && look[1] == 0)
                fail(t.name + ": the drawn eye is off the camera height (" + eye.y + ")");
            if (dir != null && look[0] == 0 && (look[1] == 0 || look[1] == 40)) {
                snapshots(rig, pose, in, f, t, seat, eye, look[1], dir, "seat_" + t.name + "_p" + (int) look[1]);
            }
            if (look[0] == 0 && look[1] == 0) torsoOut = r.torso;
        }
        if (t.kind == RiderSeat.Kind.DRIVER) {
            steering(rig, poser, t, f, eyeLevel);
            shifting(rig, poser, t, f, eyeLevel);
        }
        if (t.doorSide != 0) {
            System.out.printf(Locale.ROOT, "            hat top highest %.3f at look %s (roof %.2f)%n", worst, worstAt, ROOF);
            if (worst > ROOF - 0.01F) fail(t.name + ": the head reaches the roof (" + worst + ")");
        }
        return torsoOut;
    }

    private static float torsoOut = 0.72F;

    /** The driver's hands posed with these places on the rim; the wrist errors (px, right / left) and the twist (deg). */
    private static float[] steer(SkeletonRig rig, HumanoidPoser poser, T15 t, Frame f, float eyeLevel, double[] hand, double[] lift) {
        return posed(rig, poser, f, eyeLevel, seat(t, f, 0F, hand, lift), null);
    }

    /** The driver's hands, the right one at this point (model frame metres, PickupTruckLayout#rightHand), the lever at these tilts. */
    private static float[] shifted(SkeletonRig rig, HumanoidPoser poser, T15 t, Frame f, float eyeLevel, double leftAngle, double[] right,
                                   double fore, double across, SkeletonPose[] out) {
        SeatPose.Seat seat = seat(t, f, 0F, new double[]{leftAngle, 0}, new double[2]);
        seat.anchors.put("grip_r", f.model((float) right[0], (float) right[1], (float) right[2]));
        double[] rim = PickupTruckLayout.grip(true, 0, 0), knob = PickupTruckLayout.knob(fore, across);
        seat.anchors.put("wheel_rim", f.model((float) rim[0], (float) rim[1], (float) rim[2]));
        seat.anchors.put("shift_knob", f.model((float) knob[0], (float) knob[1], (float) knob[2]));
        return posed(rig, poser, f, eyeLevel, seat, out);
    }

    private static float[] posed(SkeletonRig rig, HumanoidPoser poser, Frame f, float eyeLevel, SeatPose.Seat seat, SkeletonPose[] out) {
        HumanoidPoser.Input in = new HumanoidPoser.Input();
        in.chestPitch = HumanoidPoser.chestPitch(0, false);
        SkeletonPose pose = new SkeletonPose(rig);
        SeatPose.Moment m = SeatPose.moment(SeatPose.Move.NONE, true, 0, 0);
        SeatPose.body(in, seat, new SeatPose.Stand(), m, 0.72F, eyeLevel);
        HumanoidPoser.Report r = poser.solveBody(in, pose);
        SeatPose.hands(in, pose, poser, seat, m);
        poser.solveArms(in, pose, r);
        if (out != null) out[0] = pose;
        return new float[]{wristError(rig, pose, in, true), wristError(rig, pose, in, false)};
    }

    /**
     * The gear lever (ShiftLever, docs/dev/seated_rider_v1_plan.md 13): the right hand on the knob in every gear and on
     * the gate's paths, on its way from the rim; then a drive up and down the gears (with a quick double press and a turn
     * of the wheel while the hand is away): no jumps, the lever never moving without the hand, the wrists on their
     * targets. And the clutch pedal against the left leg's reach (why the foot is not posed).
     */
    private static void shifting(SkeletonRig rig, HumanoidPoser poser, T15 t, Frame f, float eyeLevel) {
        float worst = 0;
        String worstAt = "";
        double[][] gate = {{1, -1, 1}, {2, -1, -1}, {3, 0, 1}, {4, 0, -1}, {5, 1, 1}, {-1, 1, -1}, {0, 0, 0}, {9, -1, 0}, {9, 1, 0}};
        for (double[] g : gate) {
            double fore = g[2] * ShiftLever.FORE, across = g[1] * ShiftLever.ACROSS;
            float[] w = shifted(rig, poser, t, f, eyeLevel, 0, PickupTruckLayout.knob(fore, across), fore, across, null);
            String at = g[0] == 9 ? "gate end " + (g[1] < 0 ? "left" : "right") : g[0] < 0 ? "R" : g[0] == 0 ? "N" : "gear " + (int) g[0];
            if (w[0] > worst) { worst = w[0]; worstAt = at; }
        }
        double[] rim0 = PickupTruckLayout.grip(true, 0, 0);
        for (double a : new double[]{0.15, 0.3, 0.5, 0.7, 0.85}) {
            float[] w = shifted(rig, poser, t, f, eyeLevel, 0, PickupTruckLayout.rightHand(rim0, 0, 0, a), 0, 0, null);
            if (w[0] > worst) { worst = w[0]; worstAt = String.format(Locale.ROOT, "on the way, %.0f%%", a * 100); }
        }
        double[] k = PickupTruckLayout.knob(0, 0);
        System.out.printf(Locale.ROOT, "            gear lever: knob (neutral) %.2f m from the right hand's rest; right wrist off at most %.2f px (%s) over the gate and the way there%n",
                dist(k, rim0), worst, worstAt);
        if (worst > 0.6F) fail(t.name + ": the right hand misses the gear knob (" + worst + " px, " + worstAt + ")");
        // the drive: {time s, gear pressed}; one step per press as the gearbox takes them
        double[][] presses = {{0.5, 1}, {1.5, 2}, {2.5, 3}, {3.5, 4}, {4.5, 5}, {5.5, 4}, {6.3, 3}, {6.42, 2}, {7.6, 1}, {8.6, 0}, {9.3, -1}, {10.4, 0}};
        ShiftLever lever = new ShiftLever();
        SteeringHands h = new SteeringHands();
        double dt = 1 / 60.0, time = 0;
        int gear = 0, target = 0, press = 0;
        double shiftLeft = 0, shiftTotal = 0;
        double[] prevR = null, prevL = null;
        double prevFore = 0, prevAcross = 0;
        float worstWrist = 0, worstStep = 0;
        int loose = 0, changes = 0;
        double knobTime = 0, firstOff = Double.NaN, firstOn = Double.NaN;
        h.reset(0, 0);
        lever.update(0, 0, 0);
        StringBuilder trace = new StringBuilder();
        while (time < 12) {
            time += dt;
            if (press < presses.length && time >= presses[press][0]) {
                target = (int) presses[press][1];
                shiftTotal = gear == 0 || target == 0 ? 0.35 * 0.6 : 0.35;
                shiftLeft = shiftTotal;
                press++;
                changes++;
            }
            if (shiftLeft > 0) {
                shiftLeft -= dt;
                if (shiftLeft <= 0) gear = target;
            }
            // the wheel: a turn to 120 deg and back while shifting up to 4th
            double wheel = time > 3.3 && time < 4.3 ? 120 * Math.sin(Math.PI * (time - 3.3)) : 0;
            lever.update(time, shiftLeft > 0 ? target : gear, shiftLeft > 0 ? shiftTotal : 0);
            h.update(time, wheel, lever.handOff());
            double[] rim = lever.hand > 0 ? PickupTruckLayout.grip(true, lever.handGoing ? Math.toRadians(h.angle[1]) : 0, 0)
                    : PickupTruckLayout.grip(true, Math.toRadians(h.angle[1]), h.lift[1]);
            double[] r = PickupTruckLayout.rightHand(rim, lever.fore, lever.across, lever.hand);
            double[] l = PickupTruckLayout.grip(false, Math.toRadians(h.angle[0]), h.lift[0]);
            if (prevR != null) worstStep = (float) Math.max(worstStep, Math.max(dist(r, prevR), dist(l, prevL)));
            boolean moved = Math.abs(lever.fore - prevFore) + Math.abs(lever.across - prevAcross) > 1e-6;
            if (moved && lever.hand < 0.999) loose++;
            if (lever.hand >= 0.999) knobTime += dt;
            if (lever.hand > 0 && Double.isNaN(firstOff)) firstOff = time;
            if (moved && Double.isNaN(firstOn)) firstOn = time;
            prevR = r;
            prevL = l;
            prevFore = lever.fore;
            prevAcross = lever.across;
            if (Math.round(time / dt) % 3 == 0) {
                float[] w = shifted(rig, poser, t, f, eyeLevel, h.angle[0], r, lever.fore, lever.across, null);
                worstWrist = Math.max(worstWrist, Math.max(w[0], w[1]));
            }
            if (press > 0 && Math.abs(time - presses[press - 1][0] - 0.3) < dt / 2)
                trace.append(String.format(Locale.ROOT, "  %.1fs->%s: fore %+.1f across %+.1f hand %.2f", time, target < 0 ? "R" : target == 0 ? "N" : String.valueOf(target),
                        lever.fore, lever.across, lever.hand));
        }
        System.out.printf(Locale.ROOT, "            shifting (%d presses): first hand off %.2fs after the press, lever moving %.2fs after; hand on the knob %.1f s in all; lever moved without the hand %d frames%n",
                changes, firstOff - presses[0][0], firstOn - presses[0][0], knobTime, loose);
        System.out.printf(Locale.ROOT, "            fastest hand step %.3f m per 1/60 s, wrist off at most %.2f px; at the end: lever fore %+.1f across %+.1f, hand %.2f, right hand on the rim at %+.0f deg%n",
                worstStep, worstWrist, lever.fore, lever.across, lever.hand, h.angle[1]);
        System.out.println("            0.3 s after each press:" + trace);
        if (loose > 0) fail(t.name + ": the gear lever moved without the hand (" + loose + " frames)");
        if (worstWrist > 0.6F) fail(t.name + ": a hand misses its target while shifting (" + worstWrist + " px)");
        if (worstStep > 0.08F) fail(t.name + ": a hand jumps while shifting (" + worstStep + " m in a frame)");
        if (lever.hand > 0 || !h.holding[1]) fail(t.name + ": the right hand did not come back to the wheel");
        // the clutch pedal (tools/pickup-t15 pedal_clutch) against the left leg
        SkeletonPose[] out = new SkeletonPose[1];
        shifted(rig, poser, t, f, eyeLevel, 0, PickupTruckLayout.grip(true, 0, 0), 0, 0, out);
        SkeletonPose p = out[0];
        Vector3f hip = f.rig(p.point(rig.thighL, rig.pivot[rig.thighL], new Vector3f()));
        Vector3f knee = f.rig(p.point(rig.shinL, rig.pivot[rig.shinL], new Vector3f()));
        Vector3f ankle = f.rig(p.point(rig.footL, rig.pivot[rig.footL], new Vector3f()));
        Vector3f pedal = new Vector3f(-0.53F, 0.665F, -0.735F);
        System.out.printf(Locale.ROOT, "            clutch pedal %.2f m from the left hip (%.2f ahead), the left leg reaches %.2f m to the ankle: %s%n",
                hip.distance(pedal), hip.z - pedal.z, hip.distance(knee) + knee.distance(ankle),
                hip.distance(pedal) > hip.distance(knee) + knee.distance(ankle) ? "out of reach, the foot is not posed" : "within reach");
    }

    /**
     * The driver's hands on the wheel (SteeringHands): every place within each hand's reach, on and off the rim; then a
     * drive: to the lock at the fastest steering rate, let go, still, a slow turn the other way, back. One hand off the
     * rim at a time, no jumps, the wrists on their targets.
     */
    private static void steering(SkeletonRig rig, HumanoidPoser poser, T15 t, Frame f, float eyeLevel) {
        float worst = 0;
        String worstAt = "";
        for (double lift : new double[]{0, SteeringHands.LIFT})
            for (double a = 0; a <= 1.0001; a += 0.1) {
                // the right hand from its lowest to its highest reach, the left the same way round (its mirror)
                double[] hand = {SteeringHands.min(0) + a * (SteeringHands.max(0) - SteeringHands.min(0)), SteeringHands.min(1) + a * (SteeringHands.max(1) - SteeringHands.min(1))};
                float[] w = steer(rig, poser, t, f, eyeLevel, hand, new double[]{lift, lift});
                for (int i = 0; i < 2; i++)
                    if (w[i] > worst) { worst = w[i]; worstAt = String.format(Locale.ROOT, "%s hand at %+.0f deg, %s", i == 0 ? "right" : "left", hand[1 - i], lift > 0 ? "lifted" : "on the rim"); }
            }
        System.out.printf(Locale.ROOT, "            hands over their whole reach (up %.0f, down %.0f deg): wrist off at most %.2f px (%s)%n",
                SteeringHands.UP, SteeringHands.DOWN, worst, worstAt);
        if (worst > 0.6F) fail(t.name + ": a hand misses the rim within its reach (" + worst + " px, " + worstAt + ")");
        // the drive: {seconds, wheel deg at its end}; steering 55 deg/s and back 75 deg/s at the road wheels
        double fast = Math.toDegrees(com.antaurora.apofirstlight.vehicle.physics.VehicleSpec.T15.steerRate) * PickupTruckLayout.STEERING_RATIO;
        double back = Math.toDegrees(com.antaurora.apofirstlight.vehicle.physics.VehicleSpec.T15.steerReturn) * PickupTruckLayout.STEERING_RATIO;
        double[][] legs = {{540 / fast, 540}, {0.6, 540}, {540 / back, 0}, {1.2, 0}, {300 / 280.0, -300}, {0.8, -300}, {300 / back, 0}, {1.2, 0}};
        SteeringHands h = new SteeringHands();
        double dt = 1 / 60.0, time = 0, wheel = 0;
        h.reset(0, 0);
        double[] prevL = null, prevR = null;
        float worstWrist = 0, worstStep = 0;
        int overs = 0, bothOff = 0;
        boolean[] wasHolding = {true, true};
        double slidFast = 0, slidSlow = 0;
        StringBuilder trace = new StringBuilder();
        for (int leg = 0; leg < legs.length; leg++) {
            double from = wheel, to = legs[leg][1], length = legs[leg][0];
            int steps = (int) Math.ceil(length / dt);
            for (int k = 1; k <= steps; k++) {
                time += dt;
                double before = wheel;
                wheel = from + (to - from) * k / steps;
                double[] gripBefore = {h.angle[0] - before, h.angle[1] - before};
                h.update(time, wheel);
                for (int i = 0; i < 2; i++) {
                    if (!h.holding[i] && wasHolding[i]) overs++;
                    // a hand that held through this step and is not where the wheel took it: the rim slid
                    if (h.holding[i] && wasHolding[i]) {
                        double slid = Math.abs(h.angle[i] - (gripBefore[i] + wheel));
                        if (leg == 0) slidFast += slid / 2;
                        if (leg == 4) slidSlow += slid / 2;
                    }
                    wasHolding[i] = h.holding[i];
                }
                if (!h.holding[0] && !h.holding[1]) bothOff++;
                double[] l = PickupTruckLayout.grip(false, Math.toRadians(h.angle[0]), h.lift[0]), r = PickupTruckLayout.grip(true, Math.toRadians(h.angle[1]), h.lift[1]);
                if (prevL != null) {
                    float step = (float) Math.max(dist(l, prevL), dist(r, prevR));
                    worstStep = Math.max(worstStep, step);
                }
                prevL = l;
                prevR = r;
                if (k % 3 == 0) {
                    float[] w = steer(rig, poser, t, f, eyeLevel, h.angle, h.lift);
                    worstWrist = Math.max(worstWrist, Math.max(w[0], w[1]));
                }
            }
            trace.append(String.format(Locale.ROOT, "  %.2fs %+.0f: L %+.0f%s R %+.0f%s", time, wheel, h.angle[0], h.holding[0] ? "" : "~", h.angle[1], h.holding[1] ? "" : "~"));
        }
        System.out.printf(Locale.ROOT, "            drive (lock at %.0f deg/s, back at %.0f, slow turn at 280): %d hand moves over, both off the rim %d frames, rim slid through the hands %.0f deg on the fast turn-in / %.0f on the slow one%n",
                fast, back, overs, bothOff, slidFast, slidSlow);
        System.out.printf(Locale.ROOT, "            fastest hand step %.3f m per 1/60 s, wrist off at most %.2f px%n", worstStep, worstWrist);
        System.out.println("            after each leg:" + trace);
        if (bothOff > 0) fail(t.name + ": both hands off the wheel for " + bothOff + " frames");
        if (worstWrist > 0.6F) fail(t.name + ": a hand misses the wheel while steering (" + worstWrist + " px)");
        if (Math.abs(h.angle[0]) > 1 || Math.abs(h.angle[1]) > 1) fail(t.name + ": the hands did not settle back on 9 and 3 (" + h.angle[0] + ", " + h.angle[1] + ")");
        if (slidSlow > 30) fail(t.name + ": the rim slid " + slidSlow + " deg through the hands on a slow turn");
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    /** The posed wrist's distance from its IK target's wrist (px). */
    private static float wristError(SkeletonRig rig, SkeletonPose pose, HumanoidPoser.Input in, boolean right) {
        if ((right ? in.right : in.left) != HumanoidPoser.ArmMode.IK) return 0;
        int hand = right ? rig.handR : rig.handL, upper = right ? rig.upperArmR : rig.upperArmL;
        Vector3f want = (right ? in.targetRight : in.targetLeft).transformPosition(new Vector3f(rig.pivot[hand]).sub(rig.pivot[upper]));
        return pose.point(hand, rig.pivot[hand], new Vector3f()).distance(want);
    }

    /**
     * The thighs' underside against the cushion's top (cab: the cushion profile of cab.mjs, rising from 0.812 at the back
     * to 0.848 near the front edge at Z -0.29; bench: flat): {deepest, highest} gap over the part above the cushion, m.
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
        return 0.812F + (0.848F - 0.812F) * (gx + 0.21F) / 0.41F;
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
                float scale = SeatPose.scale(m.size);
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
        quad.accept(new float[][]{{x0, 0.812F, 0.21F}, {x1, 0.812F, 0.21F}, {x1, 0.848F, -0.29F}, {x0, 0.848F, -0.29F}});
        quad.accept(new float[][]{{x0, 0.80F, 0.10F}, {x1, 0.80F, 0.10F}, {x1, 1.39F, 0.155F}, {x0, 1.39F, 0.155F}});
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
