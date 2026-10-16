package com.antaurora.apofirstlight.skeleton;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Player skeleton V1 (docs/dev/player_skeleton_v1_design.md 4.2, 5, 10.5): one frame of the player's pose from plain
 * inputs, Java model px (the vanilla PlayerModel's space after LivingEntityRenderer's transforms). Pure: the game glue
 * (client/skeleton/PlayerSkeleton) fills an {@link Input}, the offline checks (src/test) fill their own.
 * <ol>
 * <li>locomotion: the clips of {@code animations/player/locomotion.animation.json}, one stride phase for all the moving
 * clips (walk to run never changes feet), crouch blended in, the step turn added on top of the legs;</li>
 * <li>facing: the root turned to the pelvis' own yaw, the chest's turn taken at the hips (the torso turns as one piece,
 * the thighs partly with it), the chest bent by the look, the head taking the rest of the view (clamped to the rig's
 * limits);</li>
 * <li>the root moved level so the {@code eye} socket sits under the camera (5.1), at most {@link Input#maxRootShift};</li>
 * <li>arms: the clip's arms (empty hands), vanilla's arm angles (ordinary items: bow, shield, eating) or two-bone IK to a
 * hand target (guns), with up to {@link Input#protraction} px of shoulder reach and clamped elbow and wrist.</li>
 * </ol>
 */
public final class HumanoidPoser {
    public enum ArmMode { CLIP, VANILLA, IK }

    public static final class Input {
        public boolean slim;
        /** Stride phase 0..1 (vanilla walk animation position / 9.43), how much of the stride shows, how much of it runs. */
        public float walkPhase, move, run;
        public float crouch;
        /** Seconds into the step-turn clip, or negative. */
        public float turnStep = -1F;
        /** Seconds, for the idle clips' breathing. */
        public float time;
        /** Degrees: root (pelvis) yaw in the vanilla body frame; chest yaw and pitch relative to the pelvis. */
        public float rootYaw, chestYaw, chestPitch;
        /** Degrees, vanilla body frame: view yaw (vanilla netHeadYaw) and pitch (down positive). */
        public float viewYaw, viewPitch;
        /** Where the eye socket should be (model px; only x and z are used), or null. */
        public Vector3f eyeTarget;
        public float maxRootShift = 4.27F;
        public ArmMode right = ArmMode.CLIP, left = ArmMode.CLIP;
        /** Model-space arm rotations from vanilla's setupAnim, for VANILLA arms and the punch blend of CLIP arms. */
        public final Quaternionf vanillaRight = new Quaternionf(), vanillaLeft = new Quaternionf();
        public float vanillaRightWeight, vanillaLeftWeight;
        /** IK hand targets: the rest arm frame (arm px, origin at the shoulder pivot) to model px. */
        public final Matrix4f targetRight = new Matrix4f(), targetLeft = new Matrix4f();
        /** Elbow directions in the chest's frame. */
        public final Vector3f poleRight = new Vector3f(-0.35F, 1F, 0.45F), poleLeft = new Vector3f(0.35F, 1F, 0.45F);
        public float protraction = 1.5F;
        /**
         * A support hand's slide (model px, direction toward the stock, length = the furthest slide): when the arm cannot
         * reach its target, the target moves back along the gun this far at most (the hand takes the handguard nearer).
         * Zero = no slide.
         */
        public final Vector3f slideRight = new Vector3f(), slideLeft = new Vector3f();
        /**
         * An IK arm's shoulder set (chest frame px, x toward the body's left, y down, z back): the shoulder girdle drawn
         * forward and in, as a stock in the shoulder pocket does, before {@link #protraction} adds its reach.
         */
        public final Vector3f shoulderRight = new Vector3f(), shoulderLeft = new Vector3f();
        /** Riding: the legs take vanilla's angles. */
        public boolean legsVanilla;
        public final Quaternionf vanillaRightLeg = new Quaternionf(), vanillaLeftLeg = new Quaternionf();
    }

    /** What a frame needed, for the checks. */
    public static final class Report {
        /** Distance shoulder -> wrist target over the arm length (after protraction); >1 means the hand fell short. */
        public float rightReach, leftReach;
        /** How far (px) a support hand slid back along its gun. */
        public float rightSlide, leftSlide;
        public float rightElbowDeg, leftElbowDeg;
        public final Vector3f rootShift = new Vector3f();
    }

    private static final String IDLE = "locomotion.idle", WALK = "locomotion.walk", RUN = "locomotion.run",
            CROUCH_IDLE = "locomotion.crouch_idle", CROUCH_WALK = "locomotion.crouch_walk", TURN = "locomotion.turn_step";
    private static final float ELBOW_MAX = (float) Math.toRadians(145), TWIST_MAX = (float) Math.toRadians(90),
            WRIST_MAX = (float) Math.toRadians(60);
    /** Share of the chest's turn (against the root) taken at the hips, and the thighs' share of that. */
    static final float HIP_SHARE = 1.0F, LEG_SHARE = 0.5F;

    private final SkeletonRig rig;
    private final SkeletonClips clips;
    private final float upperLength, foreLength;

    public HumanoidPoser(SkeletonRig rig, SkeletonClips clips) {
        this.rig = rig;
        this.clips = clips;
        upperLength = rig.pivot[rig.forearmR].distance(rig.pivot[rig.upperArmR]);
        foreLength = rig.pivot[rig.handR].distance(rig.pivot[rig.forearmR]);
    }

    public SkeletonRig rig() { return rig; }

    /**
     * The chest's share of the view pitch (deg, + = down). Empty-handed 30 % within -15..20; with a rifle 50 % within
     * -25..30, the upper body leaning back with the gun when looking up.
     */
    public static float chestPitch(float viewPitch, boolean rifle) {
        return rifle ? Math.max(-25F, Math.min(30F, viewPitch * 0.5F)) : Math.max(-15F, Math.min(20F, viewPitch * 0.3F));
    }

    public Report solve(Input in, SkeletonPose pose) {
        Report report = new Report();
        pose.reset();
        locomotion(in, pose);
        pose.local[rig.root].rotationY(rad(in.rootYaw));
        Quaternionf chestClip = new Quaternionf(pose.local[rig.chest]);
        float twist = clamp(in.chestYaw, rig.limits[rig.chest], 1), bend = clamp(in.chestPitch, rig.limits[rig.chest], 0);
        // The turn of the upper body is taken at the hips, the torso turning as one piece: worn gear rides the chest frame
        // and would cut into a lower torso twisted against it. Standing, the thighs turn LEG_SHARE of it about the vertical;
        // walking they keep to the root (the way the stride goes).
        // About the pelvis' own up axis (through the waist pivot), so the chest ends where a waist twist put it.
        float hip = twist * HIP_SHARE, legShare = LEG_SHARE * (1 - clamp01(in.move));
        pose.local[rig.pelvis].rotateY(rad(hip));
        Quaternionf legBack = new Quaternionf().rotationY(rad(-hip * (1 - legShare)));
        pose.local[rig.thighR].premul(legBack);
        pose.local[rig.thighL].premul(legBack);
        pose.local[rig.chest].rotationY(rad(twist - hip)).rotateX(rad(bend)).mul(chestClip);
        pose.update();
        // head: the view in model space, relative to the chest as posed
        Quaternionf view = new Quaternionf().rotationY(rad(in.viewYaw)).rotateX(rad(in.viewPitch));
        Quaternionf chestWorld = pose.rotation(rig.chest, new Quaternionf());
        pose.local[rig.head].set(new Quaternionf(chestWorld).conjugate().mul(view));
        clampEuler(pose.local[rig.head], rig.limits[rig.head]);
        pose.update(rig.head);
        if (in.eyeTarget != null) {
            SkeletonRig.Socket eye = rig.sockets.get("eye");
            Vector3f at = pose.point(eye.bone(), eye.pos(), new Vector3f());
            float dx = in.eyeTarget.x - at.x, dz = in.eyeTarget.z - at.z, length = (float) Math.sqrt(dx * dx + dz * dz);
            if (length > in.maxRootShift) { dx *= in.maxRootShift / length; dz *= in.maxRootShift / length; }
            pose.offset[rig.root].add(dx, 0, dz);
            report.rootShift.set(dx, 0, dz);
            pose.update();
        }
        chestWorld = pose.rotation(rig.chest, chestWorld);
        arm(in, pose, report, true, chestWorld);
        arm(in, pose, report, false, chestWorld);
        if (in.legsVanilla) {
            Quaternionf pelvisWorld = pose.rotation(rig.pelvis, new Quaternionf()).conjugate();
            legVanilla(pose, rig.thighR, rig.shinR, rig.footR, new Quaternionf(pelvisWorld).mul(in.vanillaRightLeg));
            legVanilla(pose, rig.thighL, rig.shinL, rig.footL, new Quaternionf(pelvisWorld).mul(in.vanillaLeftLeg));
        }
        return report;
    }

    private void locomotion(Input in, SkeletonPose pose) {
        float move = clamp01(in.move), run = clamp01(in.run), crouch = clamp01(in.crouch), stand = 1 - crouch;
        String[] names = {IDLE, WALK, RUN, CROUCH_IDLE, CROUCH_WALK};
        float[] weights = {stand * (1 - move), stand * move * (1 - run), stand * move * run, crouch * (1 - move), crouch * move};
        float[] times = new float[5];
        for (int i = 0; i < 5; i++) {
            SkeletonClips.Clip c = clips.get(names[i]);
            boolean cyclic = i == 1 || i == 2 || i == 4;
            times[i] = cyclic ? in.walkPhase * c.length : in.time;
        }
        Quaternionf q = new Quaternionf(), sum = new Quaternionf();
        Vector3f p = new Vector3f();
        float turn = in.turnStep >= 0 && clips.has(TURN) ? 1 - move : 0;
        for (int b = 0; b < rig.size(); b++) {
            sum.set(0, 0, 0, 0);
            Vector3f offset = pose.offset[b].zero();
            for (int i = 0; i < 5; i++) {
                if (weights[i] <= 0) continue;
                SkeletonClips.Clip c = clips.get(names[i]);
                c.rotation(b, times[i], q);
                if (sum.dot(q) < 0) q.set(-q.x, -q.y, -q.z, -q.w);
                sum.set(sum.x + q.x * weights[i], sum.y + q.y * weights[i], sum.z + q.z * weights[i], sum.w + q.w * weights[i]);
                offset.add(c.position(b, times[i], p).mul(weights[i]));
            }
            if (sum.lengthSquared() < 1e-12F) sum.identity();
            pose.local[b].set(sum).normalize();
            if (turn > 0 && isLeg(b)) {
                // added on top of the blended legs (the clip's change from its first key), so a crouched step stays crouched
                SkeletonClips.Clip c = clips.get(TURN);
                Quaternionf first = c.rotation(b, 0, new Quaternionf()).conjugate();
                Quaternionf step = first.mul(c.rotation(b, in.turnStep, q));
                pose.local[b].mul(new Quaternionf().slerp(step, turn)).normalize();
                Vector3f firstPos = c.position(b, 0, new Vector3f());
                offset.add(c.position(b, in.turnStep, p).sub(firstPos).mul(turn));
            }
        }
    }

    private boolean isLeg(int b) {
        return b == rig.thighR || b == rig.shinR || b == rig.footR || b == rig.thighL || b == rig.shinL || b == rig.footL;
    }

    private void arm(Input in, SkeletonPose pose, Report report, boolean right, Quaternionf chestWorld) {
        int upper = right ? rig.upperArmR : rig.upperArmL, fore = right ? rig.forearmR : rig.forearmL, hand = right ? rig.handR : rig.handL;
        ArmMode mode = right ? in.right : in.left;
        Quaternionf toChest = new Quaternionf(chestWorld).conjugate();
        if (mode == ArmMode.IK) {
            ik(in, pose, report, right, upper, fore, hand, chestWorld);
        } else {
            Quaternionf vanilla = new Quaternionf(toChest).mul(right ? in.vanillaRight : in.vanillaLeft);
            float w = mode == ArmMode.VANILLA ? 1 : clamp01(right ? in.vanillaRightWeight : in.vanillaLeftWeight);
            if (w > 0) {
                pose.local[upper].slerp(vanilla, w);
                pose.local[fore].slerp(new Quaternionf(), w);
                pose.local[hand].slerp(new Quaternionf(), w);
            }
        }
        pose.updateFrom(upper);
    }

    private void ik(Input in, SkeletonPose pose, Report report, boolean right, int upper, int fore, int hand, Quaternionf chestWorld) {
        Matrix4f target = right ? in.targetRight : in.targetLeft;
        Vector3f restPivot = rig.pivot[upper];
        Vector3f wrist = target.transformPosition(new Vector3f(rig.pivot[hand]).sub(restPivot));
        Quaternionf handWorld = target.getUnnormalizedRotation(new Quaternionf());
        Matrix4f chest = pose.world[rig.chest];
        Vector3f set = new Vector3f(right ? in.shoulderRight : in.shoulderLeft);
        Vector3f shoulder = chest.transformPosition(new Vector3f(restPivot)).add(chestWorld.transform(new Vector3f(set)));
        float reach = upperLength + foreLength;
        Vector3f toTarget = new Vector3f(wrist).sub(shoulder);
        float distance = toTarget.length();
        // support hand: slide back along the gun until the wrist is in reach (after the shoulder's own reach)
        Vector3f slideMax = right ? in.slideRight : in.slideLeft;
        float slideLength = slideMax.length(), slid = 0;
        float want = 0.98F * reach + in.protraction;
        if (slideLength > 1e-4F && distance > want) {
            Vector3f axis = new Vector3f(slideMax).div(slideLength);
            // smallest s >= 0 with |toTarget + s axis| = want; past the closest approach the slide cannot help further
            float b = toTarget.dot(axis), c = toTarget.lengthSquared() - want * want, disc = b * b - c;
            slid = disc >= 0 ? -b - (float) Math.sqrt(disc) : -b;
            slid = Math.max(0, Math.min(slideLength, slid));
            Vector3f move = new Vector3f(axis).mul(slid);
            wrist.add(move);
            toTarget.add(move);
            distance = toTarget.length();
        }
        if (right) report.rightSlide = slid;
        else report.leftSlide = slid;
        // shoulder reach: the shoulder moves toward the target before the arm gives up (never a longer arm)
        float protract = Math.min(in.protraction, Math.max(0, distance - 0.98F * reach));
        Vector3f shift = new Vector3f();
        if (protract > 0 && distance > 1e-4F) shift.set(toTarget).mul(protract / distance);
        pose.offset[upper].set(new Quaternionf(chestWorld).conjugate().transform(new Vector3f(shift))).add(set);
        shoulder.add(shift);
        toTarget.set(wrist).sub(shoulder);
        distance = toTarget.length();
        float d = Math.max(0.15F * reach, Math.min(distance, 0.999F * reach));
        Vector3f dir = distance > 1e-4F ? new Vector3f(toTarget).div(distance) : new Vector3f(0, 1, 0);
        float cosElbow = (upperLength * upperLength + foreLength * foreLength - d * d) / (2 * upperLength * foreLength);
        float flex = Math.min(ELBOW_MAX, (float) Math.PI - (float) Math.acos(Math.max(-1, Math.min(1, cosElbow))));
        // shoulder angle for the (possibly clamped) flex
        float reachD = (float) Math.sqrt(upperLength * upperLength + foreLength * foreLength + 2 * upperLength * foreLength * Math.cos(flex));
        float cosShoulder = (upperLength * upperLength + reachD * reachD - foreLength * foreLength) / (2 * upperLength * reachD);
        float shoulderAngle = (float) Math.acos(Math.max(-1, Math.min(1, cosShoulder)));
        Vector3f pole = chestWorld.transform(new Vector3f(right ? in.poleRight : in.poleLeft));
        Vector3f side = new Vector3f(pole).sub(new Vector3f(dir).mul(pole.dot(dir)));
        if (side.lengthSquared() < 1e-8F) side.set(0, 0, 1).sub(new Vector3f(dir).mul(dir.z));
        side.normalize();
        // upper arm: +y toward the elbow, +z toward the pole (the elbow's point), the forearm folding toward -z
        Vector3f y = new Vector3f(dir).mul((float) Math.cos(shoulderAngle)).add(new Vector3f(side).mul((float) Math.sin(shoulderAngle))).normalize();
        Vector3f z = new Vector3f(side).sub(new Vector3f(y).mul(side.dot(y))).normalize();
        Vector3f x = new Vector3f(y).cross(z).normalize();
        Quaternionf upperWorld = new Quaternionf().setFromNormalized(new Matrix4f().set3x3(new org.joml.Matrix3f(x, y, z)));
        pose.local[upper].set(new Quaternionf(chestWorld).conjugate().mul(upperWorld)).normalize();
        Quaternionf foreFlex = new Quaternionf().rotationX(-flex);
        Quaternionf rel = new Quaternionf(upperWorld).mul(foreFlex).conjugate().mul(handWorld);
        Quaternionf twist = new Quaternionf(0, rel.y, 0, rel.w);
        if (twist.lengthSquared() < 1e-8F) twist.identity();
        else twist.normalize();
        limitAngle(twist, TWIST_MAX);
        pose.local[fore].set(foreFlex).mul(twist);
        Quaternionf wristQ = new Quaternionf(twist).conjugate().mul(rel).normalize();
        limitAngle(wristQ, WRIST_MAX);
        pose.local[hand].set(wristQ);
        float ratio = new Vector3f(wrist).sub(shoulder).length() / reach;
        if (right) { report.rightReach = ratio; report.rightElbowDeg = (float) Math.toDegrees(flex); }
        else { report.leftReach = ratio; report.leftElbowDeg = (float) Math.toDegrees(flex); }
    }

    private void legVanilla(SkeletonPose pose, int thigh, int shin, int foot, Quaternionf local) {
        pose.local[thigh].set(local);
        pose.local[shin].identity();
        pose.local[foot].identity();
        pose.updateFrom(thigh);
    }

    private static void limitAngle(Quaternionf q, float max) {
        if (q.w < 0) q.set(-q.x, -q.y, -q.z, -q.w);
        float angle = 2 * (float) Math.acos(Math.min(1, q.w));
        if (angle > max) new Quaternionf().slerp(q, max / angle, q);
    }

    /** Clamps a local rotation's Z-Y-X angles to the rig's limit (the free axes stay). */
    static void clampEuler(Quaternionf q, SkeletonRig.Limit limit) {
        if (limit == null) return;
        Vector3f e = q.getEulerAnglesZYX(new Vector3f());
        float x = clampDeg(e.x, limit.minX(), limit.maxX()), y = clampDeg(e.y, limit.minY(), limit.maxY()),
                z = clampDeg(e.z, limit.minZ(), limit.maxZ());
        q.rotationZYX(z, y, x);
    }

    private static float clampDeg(float radians, float min, float max) {
        if (Float.isNaN(min)) return radians;
        return Math.max(rad(min), Math.min(rad(max), radians));
    }

    /** axis 0 = x (pitch), 1 = y (twist). */
    private static float clamp(float degrees, SkeletonRig.Limit limit, int axis) {
        if (limit == null) return degrees;
        float min = axis == 0 ? limit.minX() : limit.minY(), max = axis == 0 ? limit.maxX() : limit.maxY();
        return Float.isNaN(min) ? degrees : Math.max(min, Math.min(max, degrees));
    }

    private static float clamp01(float v) { return Math.max(0, Math.min(1, v)); }

    private static float rad(float degrees) { return (float) Math.toRadians(degrees); }
}
