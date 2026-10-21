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
 * inner leg first, turning to the seat's way; it shrinks to {@link #SCALE} only while it ducks in through the opening
 * ({@link Moment#size}); from {@link #PULL_FROM} s the door-side
 * hand reaches for the door's inside pull and rides it while the door shuts (when the rider shuts it within the move;
 * since 2026-10-16 the vehicle never shuts it by itself), letting go once it is nearly shut; the hands go to the wheel
 * or the lap; done at {@link #IN_END} s.</li>
 * <li>{@link Move#GET_OUT} through a door: the door-side hand rides the inside pull while the door opens, the body leaves
 * the seat {@link #OUT_FROM}-{@link #OUT_BODY} s and grows back while it ducks out, then the hand nearer the door takes its outside handle
 * and pushes while the door shuts (when someone shuts it within the move), letting go
 * half way; done then (or at {@link #OUT_MAX} s when the door stays open).</li>
 * <li>Without a door (a bench): only the body, {@link #BENCH_BODY} s each way.</li>
 * </ul>
 */
public final class SeatPose {
    /**
     * How large a seated rider is drawn (the vanilla-sized body in a real-size cab). 0.72 under the T15's first roof; the
     * roof went up 10 cm and the cushion down 3 cm (user 2026-10-16, plan B) so the size step is smaller
     * (docs/dev/seated_rider_v1_plan.md 11).
     */
    public static final float SCALE = 0.82F;
    /**
     * Metres the seated eye is ahead of the cushion point (the body sits a little forward of upright at the wheel): where
     * the vehicle puts the rider's camera, with {@link #EYE_OVER_SEAT}. With the size (0.09 m at 0.72).
     */
    public static final float EYE_AHEAD = 0.125F * SCALE;
    /**
     * Metres from the cushion's top to a seated rider's eyes: where the vehicle puts the rider's camera
     * ({@code riderY = cushion + EYE_OVER_SEAT - eyeHeight}). The torso is compressed to bring the drawn eye there. With
     * the size (0.59 m at 0.72).
     */
    public static final float EYE_OVER_SEAT = 0.8194F * SCALE;
    /**
     * The neck turns the head this far off the chest before a driver's shoulders follow the look (deg, under the rig's
     * 70 deg head limit): looking back over a shoulder the chest turns up to {@link #DRIVER_LOOK_BACK} more, the hands
     * stay on the wheel; the view turns on past what the head shows (the eyes).
     */
    public static final float NECK_TURN = 64F, DRIVER_LOOK_BACK = 8F;
    /** The pelvis pivot over the cushion's top (px): the thighs' half thickness, a little sunk in. */
    public static final float HIP_LIFT = 2.2F;
    /**
     * Seated leg lengths over the rig's: the thighs reach the cushion's front edge (their full-size length), the shins
     * the floor with the knees about level with the hips (a vanilla leg drawn smaller is too short for a real cab).
     */
    public static final float THIGH = 1F / SCALE, SHIN = 1.6F;
    /** The ankle over the sole (px). */
    public static final float ANKLE = 2F;
    /** The share of the rim's turn (about the forearm) a hand on the steering wheel turns with. */
    public static final float WHEEL_ROLL = 0.8F;
    /** A grip this share of the rim's radius off the rim no longer turns with it. */
    public static final float OFF_RIM = 0.25F;
    /** Model px from the hand's contact point back to the wrist. */
    public static final float PALM = 1F;
    public static final float IN_BODY = 0.75F, PULL_FROM = 0.45F, IN_END = 1.7F;
    public static final float OUT_FROM = 0.25F, OUT_BODY = 1.0F, OUT_MAX = 2.0F, BENCH_BODY = 0.5F;

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
        /** DRIVER: the hands from the lap (0) to the wheel (1) (RiderSeat#hold); the body leans in to the wheel with them. */
        public float hold = 1F;
        /** DRIVER: the right hand on the ignition key (RiderSeat#reach, anchors key_r / key_axis / key_up). */
        public float reach;
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
        /** 0 standing .. 1 seated: hips, torso; the turn; each leg (inner leads). */
        public float seated = 1F, turned = 1F, innerLeg = 1F, outerLeg = 1F;
        /**
         * 0 standing size .. 1 seated size ({@link #scale}): only while the body ducks through the door opening (seated
         * share {@link #SIZE_FROM}-{@link #SIZE_TO}), where it moves most and the door frame hides part of it; a size
         * changing over the whole move showed (user 2026-10-16).
         */
        public float size = 1F;
        /** The seated hands (wheel / lap) for the door-side and the other hand. */
        public float doorHandGoal = 1F, otherHandGoal = 1F;
        /** The door-side hand on the inside pull; the pushing hand on the outside handle. */
        public float pull, push;
        /** The chest turned toward the door (deg, signed by the side). */
        public float lean;
        public boolean done = true;
    }

    /** The seated share over which a move through a door changes the size. */
    public static final float SIZE_FROM = 0.3F, SIZE_TO = 0.75F;

    private SeatPose() {
    }

    /** The weights {@code seconds} into a move; {@code door}: how far the seat's door is open (0..1). */
    public static Moment moment(Move move, boolean hasDoor, float seconds, float door) {
        Moment m = new Moment();
        if (move == Move.NONE) return m;
        float s = Math.max(0F, seconds);
        if (!hasDoor) {
            float e = smooth(s / BENCH_BODY);
            m.seated = m.turned = m.innerLeg = m.outerLeg = m.size = move == Move.GET_IN ? e : 1 - e;
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
            // the pull: reached for from PULL_FROM while the rider shuts the door, let go as it comes shut; not into a door
            // left wide open (the rider shuts it when it likes: user 2026-10-16)
            m.pull = smooth((s - PULL_FROM) / 0.25F) * smooth((door - 0.04F) / 0.16F) * (1 - smooth((door - 0.6F) / 0.25F))
                    * (1 - smooth((s - IN_END + 0.3F) / 0.3F));
            m.lean = 20F * m.pull;
            m.doorHandGoal = smooth((s - 0.7F) / 0.5F);
            m.otherHandGoal = smooth((s - 0.5F) / 0.4F);
            m.size = sizeShare(m.seated);
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
            m.size = sizeShare(m.seated);
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
        // a driver with the hands in the lap sits like a passenger; at the wheel upright, shoulders forward, the look turning
        // the head more than the chest
        float drive = driver ? seat.hold : 0F;
        float recline = seat.kind == RiderSeat.Kind.BENCH ? 4F : -8F * (1 - drive);
        in.chestPitch = in.chestPitch + (recline - in.chestPitch) * m.seated;
        // under a cab's roof the head tips back only so far (the camera still looks up)
        if (seat.doorSide != 0) in.viewPitch = Math.max(in.viewPitch, -12F * m.seated + in.viewPitch * (1 - m.seated));
        if (driver) in.protraction = in.protraction + (2F - in.protraction) * drive;
        float share = 0.35F + (0.1F - 0.35F) * drive, limit = 30F + (6F - 30F) * drive;
        float look = Math.max(-limit, Math.min(limit, in.viewYaw * share));
        // a driver looking back past the neck's turn: the shoulders follow a little (the hands stay on the wheel)
        float over = Math.abs(in.viewYaw - look) - NECK_TURN;
        if (driver && over > 0) look += Math.copySign(Math.min(over, DRIVER_LOOK_BACK), in.viewYaw) * drive;
        float twist = look - seat.doorSide * m.lean;
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
            // a hand that holds something (or uses it) is not laid in the lap: it holds it as standing (vanilla's arm), so a
            // seated rider uses things; on the wheel it goes as far as the driver holds it (user 2026-10-21)
            boolean holding = (right ? in.right : in.left) == HumanoidPoser.ArmMode.VANILLA;
            Vector3f grip = seat.kind == RiderSeat.Kind.DRIVER ? seat.anchors.get(right ? "grip_r" : "grip_l") : null;
            float hold = grip != null ? seat.hold : 0F, rest = holding ? 0F : 1F;
            Matrix4f lap = rest > 0 ? lap(in, pose, poser, right) : null;
            Matrix4f wheel = hold > 0 ? wheel(in, pose, poser, seat, right, grip) : null;
            Matrix4f goal = wheel == null ? lap : lap == null || hold >= 1 ? wheel : blend(lap, wheel, hold, rig);
            float wGoal = (doorHand ? m.doorHandGoal : m.otherHandGoal) * (rest + (1 - rest) * hold);
            Matrix4f target = goal;
            float w = goal == null ? 0F : wGoal;
            Vector3f pull = seat.anchors.get("door_pull");
            if (doorHand && pull != null && m.pull > 0) {
                Matrix4f t = contact(in, pose, poser, right, pull);
                target = w > 0 ? blend(target, t, m.pull, rig) : t;
                w = w + (1 - w) * m.pull;
            }
            if (outer != null && right == pushRight && m.push > 0) {
                Matrix4f t = contact(in, pose, poser, right, outer);
                target = w > 0 ? blend(target, t, m.push, rig) : t;
                w = w + (1 - w) * m.push;
            }
            // the driver's right hand on the ignition key (over whatever it held or did; the host hides a held item)
            Vector3f key = right ? seat.anchors.get("key_r") : null;
            float reach = key != null ? seat.reach : 0F;
            if (reach > 0) {
                Matrix4f t = key(in, pose, poser, seat, key);
                target = w > 0 ? blend(target, t, reach, rig) : t;
                w = w + (1 - w) * reach;
            }
            if (w <= 0) continue;
            (right ? in.targetRight : in.targetLeft).set(target);
            if (right) { in.right = HumanoidPoser.ArmMode.IK; in.ikRightWeight = w; }
            else { in.left = HumanoidPoser.ArmMode.IK; in.ikLeftWeight = w; }
            if (hold >= 0.5F || reach >= 0.5F) (right ? in.poleRight : in.poleLeft).set(right ? -0.6F : 0.6F, 1F, 0.25F);
        }
    }

    /** A hand's place in the lap: on the thigh, a little short of the knee. */
    private static Matrix4f lap(HumanoidPoser.Input in, SkeletonPose pose, HumanoidPoser poser, boolean right) {
        SkeletonRig rig = pose.rig;
        int thigh = right ? rig.thighR : rig.thighL, shin = right ? rig.shinR : rig.shinL;
        Vector3f hip = pose.point(thigh, rig.pivot[thigh], new Vector3f()), knee = pose.point(shin, rig.pivot[shin], new Vector3f());
        Vector3f up = pose.rotation(rig.pelvis, new Quaternionf()).transform(new Vector3f(0, -1, 0));
        Vector3f lap = new Vector3f(hip).lerp(knee, 0.6F).add(up.mul(2.6F));
        return contact(in, pose, poser, right, lap);
    }

    /**
     * A hand on the steering wheel: {@link #contact}, the hand then turned about the forearm by the part of the rim's turn
     * from its rest (9 / 3 o'clock) that is about the forearm ({@link #WHEEL_ROLL} of it), so the wrist goes round with
     * the rim (the anchors {@code wheel_hub} and {@code wheel_axis}: the column's axis; without them, no turn). With
     * {@code wheel_rim} (a point on the rim), the turn fades out as the grip leaves the rim (the right hand on its way to the
     * gear lever's knob): none {@link #OFF_RIM} of the rim's radius off it.
     */
    private static Matrix4f wheel(HumanoidPoser.Input in, SkeletonPose pose, HumanoidPoser poser, Seat seat, boolean right, Vector3f grip) {
        Vector3f hub = seat.anchors.get("wheel_hub"), axisPoint = seat.anchors.get("wheel_axis");
        if (hub == null || axisPoint == null) return contact(in, pose, poser, right, grip);
        Vector3f wrist = new Vector3f(grip);
        Quaternionf q = new Quaternionf();
        for (int i = 0; i < 2; i++) {
            q = poser.forearm(in, pose, right, wrist);
            wrist.set(grip).sub(q.transform(new Vector3f(0, 1, 0)).mul(PALM));
        }
        Vector3f axis = new Vector3f(axisPoint).sub(hub).normalize();
        Vector3f rest = new Vector3f(axis).cross(seat.up()).normalize().mul(right ? 1 : -1);
        Vector3f radial = new Vector3f(grip).sub(hub);
        radial.sub(new Vector3f(axis).mul(radial.dot(axis)));
        float on = 1F;
        Vector3f rim = seat.anchors.get("wheel_rim");
        if (rim != null) {
            Vector3f r0 = new Vector3f(rim).sub(hub);
            r0.sub(new Vector3f(axis).mul(r0.dot(axis)));
            float radius = r0.length();
            if (radius > 1e-3F) on = 1F - smooth(Math.abs(radial.length() - radius) / (OFF_RIM * radius));
        }
        if (radial.lengthSquared() > 1e-6F && on > 0) {
            float turn = (float) Math.atan2(new Vector3f(rest).cross(radial).dot(axis), rest.dot(radial));
            float along = axis.dot(q.transform(new Vector3f(0, 1, 0)));
            q.rotateY(turn * along * WHEEL_ROLL * on);
        }
        return frame(pose.rig, right, wrist, q);
    }

    /**
     * The right hand on the ignition key: {@link #contact} on {@code key_r}, turned about the forearm by the key's turn
     * from upright ({@code key_up} against the seat's up, about {@code key_axis}), as the wheel's rim turns the wrists.
     */
    private static Matrix4f key(HumanoidPoser.Input in, SkeletonPose pose, HumanoidPoser poser, Seat seat, Vector3f key) {
        Vector3f axisPoint = seat.anchors.get("key_axis"), upPoint = seat.anchors.get("key_up");
        Vector3f wrist = new Vector3f(key);
        Quaternionf q = new Quaternionf();
        for (int i = 0; i < 2; i++) {
            q = poser.forearm(in, pose, true, wrist);
            wrist.set(key).sub(q.transform(new Vector3f(0, 1, 0)).mul(PALM));
        }
        if (axisPoint != null && upPoint != null) {
            Vector3f axis = new Vector3f(axisPoint).sub(key).normalize();
            Vector3f rest = new Vector3f(seat.up()), now = new Vector3f(upPoint).sub(key);
            rest.sub(new Vector3f(axis).mul(rest.dot(axis)));
            now.sub(new Vector3f(axis).mul(now.dot(axis)));
            if (rest.lengthSquared() > 1e-6F && now.lengthSquared() > 1e-6F) {
                float turn = (float) Math.atan2(new Vector3f(rest).cross(now).dot(axis), rest.dot(now));
                q.rotateY(turn * axis.dot(q.transform(new Vector3f(0, 1, 0))));
            }
        }
        return frame(pose.rig, true, wrist, q);
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

    /** The draw scale for a size share ({@link Moment#size}). */
    public static float scale(float size) {
        return 1F + (SCALE - 1F) * Math.max(0F, Math.min(1F, size));
    }

    /** A move's size share from its seated share: all of it within SIZE_FROM-SIZE_TO. */
    static float sizeShare(float seated) {
        return smooth((seated - SIZE_FROM) / (SIZE_TO - SIZE_FROM));
    }

    public static float smooth(float t) {
        t = Math.max(0F, Math.min(1F, t));
        return t * t * (3 - 2 * t);
    }
}
