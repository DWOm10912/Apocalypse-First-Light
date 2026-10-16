package com.antaurora.apofirstlight.skeleton.seat;

import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Seated riders V1 (docs/dev/seated_rider_v1_plan.md 3, 4): a seat and the moves onto and off it, as the poser's input.
 * Pure (model px, Java: y down, front -z, the body's right -x); the game glue (client/skeleton/SeatedRiders) turns a
 * {@link RiderSeat} into a {@link Seat} and keeps the time, the offline checks build their own. Two steps around
 * HumanoidPoser#solveBody, as the player actions: {@link #body} (hips, facing, torso, legs, chest), {@link #hands}
 * (wheel, lap, door).
 * <p>Moves (seconds from the start; the door is the vehicle's own, the hand follows it as it swings):
 * <ul>
 * <li>{@link Move#GET_IN} through a door: 0-{@link #IN_BODY} s the body steps from where it stood into the seat, the
 * inner leg first, turning to the seat's way and shrinking to {@link #SCALE}; from {@link #PULL_FROM} s the door-side
 * hand reaches for the door's inside pull and rides it while the door shuts (the vehicle shuts it
 * {@link #SHUT_IN_TICKS} ticks after the rider sat down), letting go once it is nearly shut; the hands go to the wheel
 * or the lap; done at {@link #IN_END} s.</li>
 * <li>{@link Move#GET_OUT} through a door: the door-side hand rides the inside pull while the door opens, the body leaves
 * the seat {@link #OUT_FROM}-{@link #OUT_BODY} s and grows back, then the hand nearer the door takes its outside handle
 * and pushes while the door shuts (the vehicle shuts it {@link #SHUT_OUT_TICKS} ticks after the rider got up), letting go
 * half way; done then (or at {@link #OUT_MAX} s when the door stays open).</li>
 * <li>Without a door (a bench): only the body, {@link #BENCH_BODY} s each way.</li>
 * </ul>
 */
public final class SeatPose {
    /** How large a seated rider is drawn (the vanilla-sized body in a real-size cab). */
    public static final float SCALE = 0.72F;
    /**
     * Metres the seated eye is ahead of the cushion point (the body sits a little forward of upright at the wheel): where
     * the vehicle puts the rider's camera, with {@link #EYE_OVER_SEAT}.
     */
    public static final float EYE_AHEAD = 0.09F;
    /**
     * Metres from the cushion's top to a seated rider's eyes: where the vehicle puts the rider's camera
     * ({@code riderY = cushion + EYE_OVER_SEAT - eyeHeight}). The torso is compressed to bring the drawn eye there.
     */
    public static final float EYE_OVER_SEAT = 0.59F;
    /** The pelvis pivot over the cushion's top (px): the thighs' half thickness, a little sunk in. */
    public static final float HIP_LIFT = 2.2F;
    /**
     * Seated leg lengths over the rig's: the thighs reach the cushion's front edge (their full-size length), the shins
     * the floor with the knees about level with the hips (a vanilla leg drawn smaller is too short for a real cab).
     */
    public static final float THIGH = 1F / SCALE, SHIN = 1.6F;
    /** The ankle over the sole (px). */
    public static final float ANKLE = 2F;
    /** Model px from the hand's contact point back to the wrist. */
    public static final float PALM = 1F;
    public static final float IN_BODY = 0.75F, PULL_FROM = 0.45F, IN_END = 1.7F;
    public static final float OUT_FROM = 0.25F, OUT_BODY = 1.0F, OUT_MAX = 2.0F, BENCH_BODY = 0.5F;
    /** The door timings the vehicle should use (game ticks after the rider sat down / got up until the door shuts). */
    public static final int SHUT_IN_TICKS = 14, SHUT_OUT_TICKS = 20;

    public enum Move { NONE, GET_IN, GET_OUT }

    /** A seat this frame, model px. */
    public static final class Seat {
        public RiderSeat.Kind kind = RiderSeat.Kind.PASSENGER;
        /** The cushion's top under the hips. */
        public final Vector3f cushion = new Vector3f();
        /** Rest body -> seated body (model rotation). */
        public final Quaternionf turn = new Quaternionf();
        public int doorSide;
        public float door;
        public final Map<String, Vector3f> anchors = new HashMap<>();

        public Vector3f up() { return turn.transform(new Vector3f(0, -1, 0)); }

        public Vector3f front() { return turn.transform(new Vector3f(0, 0, -1)); }

        public Vector3f right() { return turn.transform(new Vector3f(-1, 0, 0)); }

        /** A point of the seat's frame: px right, up, ahead of the cushion point. */
        public Vector3f local(float right, float up, float ahead) {
            return new Vector3f(cushion).add(right().mul(right)).add(up().mul(up)).add(front().mul(ahead));
        }
    }

    /** Where one stands at a move's other end: the feet (model px) and the facing (rest body -> standing body). */
    public static final class Stand {
        public final Vector3f feet = new Vector3f(0, 24, 0);
        public final Quaternionf turn = new Quaternionf();
    }

    /** The weights at one moment of a move. */
    public static final class Moment {
        /** 0 standing .. 1 seated: hips, torso, scale; the turn; each leg (inner leads). */
        public float seated = 1F, turned = 1F, innerLeg = 1F, outerLeg = 1F;
        /** The seated hands (wheel / lap) for the door-side and the other hand. */
        public float doorHandGoal = 1F, otherHandGoal = 1F;
        /** The door-side hand on the inside pull; the pushing hand on the outside handle. */
        public float pull, push;
        /** The chest turned toward the door (deg, signed by the side). */
        public float lean;
        public boolean done = true;
    }

    private SeatPose() {
    }

    /** The weights {@code seconds} into a move; {@code door}: how far the seat's door is open (0..1). */
    public static Moment moment(Move move, boolean hasDoor, float seconds, float door) {
        Moment m = new Moment();
        if (move == Move.NONE) return m;
        float s = Math.max(0F, seconds);
        if (!hasDoor) {
            float e = smooth(s / BENCH_BODY);
            m.seated = m.turned = m.innerLeg = m.outerLeg = move == Move.GET_IN ? e : 1 - e;
            m.doorHandGoal = m.otherHandGoal = move == Move.GET_IN ? smooth((s - 0.2F) / 0.4F) : 1 - smooth(s / 0.3F);
            m.done = s >= (move == Move.GET_IN ? 0.6F : BENCH_BODY);
            return m;
        }
        if (move == Move.GET_IN) {
            float p = Math.min(1F, s / IN_BODY);
            m.seated = smooth(p);
            m.turned = smooth(p * 1.3F);
            m.innerLeg = smooth(p * 1.25F);
            m.outerLeg = smooth(p * 1.25F - 0.25F);
            // the pull: reached for from PULL_FROM, let go as the door comes shut
            m.pull = smooth((s - PULL_FROM) / 0.25F) * smooth((door - 0.04F) / 0.16F) * (1 - smooth((s - IN_END + 0.3F) / 0.3F));
            m.lean = 20F * m.pull;
            m.doorHandGoal = smooth((s - 0.7F) / 0.5F);
            m.otherHandGoal = smooth((s - 0.5F) / 0.4F);
            m.done = s >= IN_END;
        } else {
            float p = Math.max(0F, Math.min(1F, (s - OUT_FROM) / (OUT_BODY - OUT_FROM)));
            m.seated = 1 - smooth(p);
            m.turned = 1 - smooth(p * 1.3F);
            m.innerLeg = 1 - smooth(p * 1.25F - 0.25F);
            m.outerLeg = 1 - smooth(p * 1.25F);
            m.pull = 1 - smooth((s - 0.3F) / 0.25F);
            m.lean = 20F * m.pull;
            m.doorHandGoal = 0F;
            m.otherHandGoal = 1 - smooth(s / 0.3F);
            // the push: on the outside handle once out, with the door until it has swung half shut
            m.push = smooth((s - 0.8F) / 0.25F) * smooth((door - 0.4F) / 0.2F) * (1 - smooth((s - OUT_MAX + 0.2F) / 0.2F));
            m.done = s >= OUT_BODY + 0.1F && door < 0.35F || s >= OUT_MAX;
        }
        return m;
    }

    /**
     * Before solveBody: hips, facing, torso, legs and chest for the seat at this moment. {@code torso}: the seated
     * torso share (from the last seated frame's report) for the moves; seated still, {@code eyeLevel} (model px y, NaN:
     * none) solves it.
     */
    public static void body(HumanoidPoser.Input in, Seat seat, Stand stand, Moment m, float torso, float eyeLevel) {
        Vector3f up = seat.up();
        Vector3f seatHip = seat.local(0, HIP_LIFT, 0);
        Vector3f standUp = stand.turn.transform(new Vector3f(0, -1, 0));
        Vector3f standHip = new Vector3f(stand.feet).add(new Vector3f(standUp).mul(12F));
        in.hipTarget = new Vector3f(standHip).lerp(seatHip, m.seated).add(new Vector3f(up).mul(1.5F * (float) Math.sin(Math.PI * m.seated)));
        in.rootTurn = new Quaternionf(stand.turn).slerp(seat.turn, m.turned);
        in.rootYaw = 0F;
        in.eyeTarget = null;
        in.move = 0F;
        in.crouch = 0F;
        in.turnStep = -1F;
        in.legsVanilla = false;
        if (m.seated >= 1F && !Float.isNaN(eyeLevel)) in.eyeLevel = eyeLevel;
        else in.torsoDirect = 1F + (torso - 1F) * m.seated;
        // chest: leaning back a little in a cab seat, upright on a bench; a twist with the look, smaller at the wheel
        boolean driver = seat.kind == RiderSeat.Kind.DRIVER;
        float recline = seat.kind == RiderSeat.Kind.BENCH ? 4F : driver ? 0F : -8F;
        in.chestPitch = in.chestPitch + (recline - in.chestPitch) * m.seated;
        // under a cab's roof the head tips back only so far (the camera still looks up)
        if (seat.doorSide != 0) in.viewPitch = Math.max(in.viewPitch, -12F * m.seated + in.viewPitch * (1 - m.seated));
        if (driver) in.protraction = 2F;
        float share = driver ? 0.1F : 0.35F, limit = driver ? 6F : 30F;
        float twist = Math.max(-limit, Math.min(limit, in.viewYaw * share)) - seat.doorSide * m.lean;
        in.chestYaw = in.chestYaw + (twist - in.chestYaw) * m.seated;
        // legs: from under the standing hips to the floor rests, the inner leg (away from the door) first, lifted between
        in.legIkWeight = 1F;
        in.thighStretch = 1F + (THIGH - 1F) * m.seated;
        in.shinStretch = 1F + (SHIN - 1F) * m.seated;
        boolean rightInner = seat.doorSide <= 0;
        for (boolean right : new boolean[]{true, false}) {
            float e = right == rightInner ? m.innerLeg : m.outerLeg;
            float side = right ? -1.9F : 1.9F;
            Vector3f standAnkle = new Vector3f(stand.feet).add(stand.turn.transform(new Vector3f(side, -ANKLE, 0)));
            Vector3f floor = seat.anchors.get(right ? "floor_r" : "floor_l");
            if (floor == null) floor = seat.local(right ? 2F : -2F, -8.5F, 7F);
            Vector3f seatAnkle = new Vector3f(floor).add(new Vector3f(up).mul(ANKLE));
            Vector3f ankle = new Vector3f(standAnkle).lerp(seatAnkle, e).add(new Vector3f(up).mul(3F * (float) Math.sin(Math.PI * e)));
            (right ? in.ankleRight : in.ankleLeft).set(ankle);
            Quaternionf poleTurn = new Quaternionf(stand.turn).slerp(seat.turn, e);
            (right ? in.kneePoleRight : in.kneePoleLeft).set(poleTurn.transform(new Vector3f(right ? -0.15F : 0.15F, -0.6F, -1F)).normalize());
        }
        in.footRest = new Quaternionf(in.rootTurn);
    }

    /**
     * After solveBody: the hands. Seated: the wheel's grips (DRIVER, when the seat has them) or the lap; the moves add the
     * door's pull and push. Sets each arm to IK with its target and weight, or leaves it to the clip.
     */
    public static void hands(HumanoidPoser.Input in, SkeletonPose pose, HumanoidPoser poser, Seat seat, Moment m) {
        SkeletonRig rig = pose.rig;
        boolean doorRight = seat.doorSide > 0;
        Vector3f outer = seat.anchors.get("door_outer");
        // the pushing hand: the one on the handle's side of the standing body
        boolean pushRight = outer != null && outer.x < 0;
        for (boolean right : new boolean[]{true, false}) {
            boolean doorHand = seat.doorSide != 0 && right == doorRight;
            Matrix4f goal = goal(in, pose, poser, seat, right);
            float wGoal = doorHand ? m.doorHandGoal : m.otherHandGoal;
            Matrix4f target = goal;
            float w = wGoal;
            Vector3f pull = seat.anchors.get("door_pull");
            if (doorHand && pull != null && m.pull > 0) {
                Matrix4f t = contact(in, pose, poser, right, pull);
                target = w > 0 ? blend(goal, t, m.pull, rig) : t;
                w = w + (1 - w) * m.pull;
            }
            if (outer != null && right == pushRight && m.push > 0) {
                Matrix4f t = contact(in, pose, poser, right, outer);
                target = w > 0 ? blend(target, t, m.push, rig) : t;
                w = w + (1 - w) * m.push;
            }
            if (w <= 0) continue;
            (right ? in.targetRight : in.targetLeft).set(target);
            if (right) { in.right = HumanoidPoser.ArmMode.IK; in.ikRightWeight = w; }
            else { in.left = HumanoidPoser.ArmMode.IK; in.ikLeftWeight = w; }
            if (seat.kind == RiderSeat.Kind.DRIVER && seat.anchors.containsKey(right ? "grip_r" : "grip_l"))
                (right ? in.poleRight : in.poleLeft).set(right ? -0.6F : 0.6F, 1F, 0.25F);
        }
    }

    /** A hand's seated place: its grip on the wheel, else the lap (on the thigh, a little short of the knee). */
    private static Matrix4f goal(HumanoidPoser.Input in, SkeletonPose pose, HumanoidPoser poser, Seat seat, boolean right) {
        SkeletonRig rig = pose.rig;
        Vector3f grip = seat.kind == RiderSeat.Kind.DRIVER ? seat.anchors.get(right ? "grip_r" : "grip_l") : null;
        if (grip != null) return contact(in, pose, poser, right, grip);
        int thigh = right ? rig.thighR : rig.thighL, shin = right ? rig.shinR : rig.shinL;
        Vector3f hip = pose.point(thigh, rig.pivot[thigh], new Vector3f()), knee = pose.point(shin, rig.pivot[shin], new Vector3f());
        Vector3f up = pose.rotation(rig.pelvis, new Quaternionf()).transform(new Vector3f(0, -1, 0));
        Vector3f lap = new Vector3f(hip).lerp(knee, 0.6F).add(up.mul(2.6F));
        return contact(in, pose, poser, right, lap);
    }

    /**
     * The IK target for a hand on a point: the forearm's own untwisted frame (HumanoidPoser#forearm), the wrist
     * {@link #PALM} px back from the point.
     */
    public static Matrix4f contact(HumanoidPoser.Input in, SkeletonPose pose, HumanoidPoser poser, boolean right, Vector3f point) {
        SkeletonRig rig = pose.rig;
        Vector3f wrist = new Vector3f(point);
        Quaternionf q = new Quaternionf();
        for (int i = 0; i < 2; i++) {
            q = poser.forearm(in, pose, right, wrist);
            wrist.set(point).sub(q.transform(new Vector3f(0, 1, 0)).mul(PALM));
        }
        return frame(rig, right, wrist, q);
    }

    private static Matrix4f frame(SkeletonRig rig, boolean right, Vector3f wrist, Quaternionf rotation) {
        int upper = right ? rig.upperArmR : rig.upperArmL, hand = right ? rig.handR : rig.handL;
        Vector3f o = new Vector3f(rig.pivot[hand]).sub(rig.pivot[upper]);
        return new Matrix4f().translation(wrist).rotate(rotation).translate(-o.x, -o.y, -o.z);
    }

    /** Two hand targets blended: the wrists along a line, the turns by slerp. */
    private static Matrix4f blend(Matrix4f a, Matrix4f b, float w, SkeletonRig rig) {
        Vector3f o = new Vector3f(rig.pivot[rig.handR]).sub(rig.pivot[rig.upperArmR]);
        Vector3f wa = a.transformPosition(new Vector3f(o)), wb = b.transformPosition(new Vector3f(o));
        Quaternionf qa = a.getUnnormalizedRotation(new Quaternionf()), qb = b.getUnnormalizedRotation(new Quaternionf());
        return new Matrix4f().translation(wa.lerp(wb, w)).rotate(qa.slerp(qb, w)).translate(-o.x, -o.y, -o.z);
    }

    /** The draw scale for a seated share. */
    public static float scale(float seated) {
        return 1F + (SCALE - 1F) * Math.max(0F, Math.min(1F, seated));
    }

    public static float smooth(float t) {
        t = Math.max(0F, Math.min(1F, t));
        return t * t * (3 - 2 * t);
    }
}
