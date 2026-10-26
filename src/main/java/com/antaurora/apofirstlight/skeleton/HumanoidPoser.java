package com.antaurora.apofirstlight.skeleton;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

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
        /**
         * Degrees, the root's frame: a hit's flinch (client/skeleton/HitReactClient), + pitch leaning forward, + roll leaning
         * to the left. The pelvis takes {@link #FLINCH_PELVIS} of it (the legs kept), the chest {@link #FLINCH_CHEST} in all,
         * the head {@link #FLINCH_HEAD} on top of the view.
         */
        public float flinchPitch, flinchRoll;
        /**
         * 0..1: how far the head goes back from the view to the clips' head (lying out cold: the face on the ground, not
         * where the view looks; client/skeleton/ProneClient).
         */
        public float headClip;
        /** Where the eye socket should be (model px; only x and z are used), or null. */
        public Vector3f eyeTarget;
        public float maxRootShift = 4.27F;
        /** How much of the chest's twist the hips take ({@link #HIP_SHARE} standing; 0 on a chair: ChairLegs, the hips on the seat). */
        public float hipShare = HIP_SHARE;
        public ArmMode right = ArmMode.CLIP, left = ArmMode.CLIP;
        /** How much of an IK arm the IK takes (the rest is the clip's arm): a hand on its way to or off a target. */
        public float ikRightWeight = 1F, ikLeftWeight = 1F;
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
        /**
         * An action's clips (docs/dev/player_actions_v1_plan.md 2.3), over the locomotion on the bones of its mask: the
         * main clip at {@code actionTime} s by {@code actionWeight}, then the parameter clip (its first key) by
         * {@code paramWeight}. {@code actionMirror}: authored for the right side, played for the left (bones _r / _l
         * swapped, y and z turns negated). Null clips: none.
         */
        public SkeletonClips.Clip actionClip, paramClip;
        public float actionTime, actionWeight, paramWeight;
        public boolean[] actionMask;
        public boolean actionMirror;
        /**
         * A gun's stance loops (docs/dev/p9_stance_loops_v1.md: the ready's idle, the sprint), before the action's clips:
         * each on its mask's bones by its weight, the locomotion keeping what is left of a bone (its clips' weights summing
         * to at most 1). Never mirrored (guns are right-handed).
         */
        public final List<StanceClip> stance = new ArrayList<>();
        /**
         * One stance loop's clip at its time (s), weight and mask (PlayerAction#mask); {@code mirror}: across the body's
         * middle, as a mirrored action (sitting on the ground with the right leg out: client/skeleton/GroundSitClient).
         */
        public record StanceClip(SkeletonClips.Clip clip, float time, float weight, boolean[] mask, boolean mirror) {
            public StanceClip(SkeletonClips.Clip clip, float time, float weight, boolean[] mask) {
                this(clip, time, weight, mask, false);
            }
        }
        /** Model px the root moves (an action stepping up to what it works on), before the eye's own shift. */
        public final Vector3f rootOffset = new Vector3f();
        /** Riding: the legs take vanilla's angles. */
        public boolean legsVanilla;
        public final Quaternionf vanillaRightLeg = new Quaternionf(), vanillaLeftLeg = new Quaternionf();
        /**
         * Seated riders V1 (docs/dev/seated_rider_v1_plan.md 3): the root's whole rotation in model space (null: the
         * {@link #rootYaw} turn), for a seat that faces its own way or tilts with its vehicle.
         */
        public Quaternionf rootTurn;
        /** Where the pelvis pivot goes (model px; null: where the clips put it): the root moves there. */
        public Vector3f hipTarget;
        /**
         * Model px y the eye should come down to (NaN: none): the torso is compressed (chest and head lowered, the chest's
         * top end squashed, the shoulders with it) down to {@link #torsoMin} of its height, so a cab's eye height fits.
         */
        public float eyeLevel = Float.NaN, torsoMin = 0.62F;
        /** Without an {@link #eyeLevel}: the torso's height share as given (1: whole), for the moves onto and off a seat. */
        public float torsoDirect = 1F;
        /** Legs by two-bone IK to the ankle targets (model px), by this weight over the clips' legs; knees toward the poles. */
        public float legIkWeight;
        public final Vector3f ankleRight = new Vector3f(), ankleLeft = new Vector3f();
        public final Vector3f kneePoleRight = new Vector3f(0, -0.6F, -1F), kneePoleLeft = new Vector3f(0, -0.6F, -1F);
        /** The feet's model rotation for IK legs (soles flat on the floor); null: the root's. */
        public Quaternionf footRest;
        /** One foot's own rotation over {@link #footRest} (a seated heel bouncing: ChairPose); null: footRest. */
        public Quaternionf footRestRight, footRestLeft;
        /**
         * Thigh and shin lengthened by these factors (1: rest): a seated rider drawn smaller keeps legs long enough for the
         * knees to reach the cushion's edge and the soles the floor.
         */
        public float thighStretch = 1F, shinStretch = 1F;
    }

    /** What a frame needed, for the checks. */
    public static final class Report {
        /** Distance shoulder -> wrist target over the arm length (after protraction); >1 means the hand fell short. */
        public float rightReach, leftReach;
        /** How far (px) a support hand slid back along its gun. */
        public float rightSlide, leftSlide;
        public float rightElbowDeg, leftElbowDeg;
        public final Vector3f rootShift = new Vector3f();
        /** The torso's height share after {@link Input#eyeLevel} (1: whole); hip -> ankle target over the leg's length. */
        public float torso = 1F, rightLegReach, leftLegReach;
        /** The clip's arms (local rotations upper, fore, hand right then left) before any arm solve, for re-solving. */
        final Quaternionf[] clipArms = {new Quaternionf(), new Quaternionf(), new Quaternionf(), new Quaternionf(),
                new Quaternionf(), new Quaternionf()};
    }

    private static final String IDLE = "locomotion.idle", WALK = "locomotion.walk", RUN = "locomotion.run",
            CROUCH_IDLE = "locomotion.crouch_idle", CROUCH_WALK = "locomotion.crouch_walk", TURN = "locomotion.turn_step";
    private static final float ELBOW_MAX = (float) Math.toRadians(145), TWIST_MAX = (float) Math.toRadians(90),
            WRIST_MAX = (float) Math.toRadians(60);
    /**
     * Block-Mesh V2 (a rig with a profile) only: its shoulders sit 0.5 px further out and up than vanilla's, where the clips
     * were keyed, so a hand held in front of the chest (the P9 reloads) leaves its elbow straighter and the wrist bent past
     * what a wrist does (54-60 deg against vanilla's 45-47). Where the wrist would bend past {@link #REACH_WRIST}, the
     * shoulder comes forward toward the hand by up to {@link #REACH_IN} px (the shoulder blade's protraction, about 6 cm),
     * the elbow bending more, each px costing {@link #REACH_COST}; a hand out of the arm's reach takes the same px first.
     * A wrist still past WRIST_MAX after that and the elbow's swing ({@link #SWING_MAX}) keeps the hand's end on its target
     * (the wrist moved, the hand turned less)
     * (the recoil-start pull, the P9 draw and holster: 0.6-1.0 px short; docs/dev/block_mesh_v2_b_ingame.md 14.1).
     */
    private static final float REACH_WRIST = (float) Math.toRadians(40), REACH_TWIST = (float) Math.toRadians(25),
            REACH_IN = 1F, REACH_COST = 0.01F;
    /**
     * With it the elbow swings about the shoulder-wrist line, at most {@link #SWING_MAX} off the pole's place, each degree
     * squared costing {@link #SWING_COST}: a hand held close (the P9 support hand aimed up or crouched: the elbow folded,
     * the wrist at its 60 deg limit) turns more by the elbow's swing than by the shoulder.
     */
    private static final float SWING_MAX = (float) Math.toRadians(60), SWING_COST = 3e-5F;
    /** The hand's end kept on its target past the wrist's limit: at most this many rounds, each stepping this much over. */
    private static final int PALM_ROUNDS = 12;
    private static final float PALM_RELAX = 1.8F;
    /** Share of the chest's turn (against the root) taken at the hips, and the thighs' share of that. */
    static final float HIP_SHARE = 1.0F, LEG_SHARE = 0.5F;

    private final SkeletonRig rig;
    private final SkeletonClips clips;
    private final float upperLength, foreLength, thighLength, shinLength, neckHalf;
    /** Each bone's mirror (_r <-> _l; itself for the middle bones). */
    private final int[] mirror;

    public HumanoidPoser(SkeletonRig rig, SkeletonClips clips) {
        this.rig = rig;
        this.clips = clips;
        upperLength = rig.pivot[rig.forearmR].distance(rig.pivot[rig.upperArmR]);
        foreLength = rig.pivot[rig.handR].distance(rig.pivot[rig.forearmR]);
        thighLength = rig.pivot[rig.shinR].distance(rig.pivot[rig.thighR]);
        shinLength = rig.pivot[rig.footR].distance(rig.pivot[rig.shinR]);
        neckHalf = rig.pivot[rig.head].distance(rig.pivot[rig.chest]);
        mirror = new int[rig.size()];
        for (int b = 0; b < rig.size(); b++) {
            String n = rig.names[b];
            String m = n.endsWith("_r") ? n.substring(0, n.length() - 2) + "_l" : n.endsWith("_l") ? n.substring(0, n.length() - 2) + "_r" : n;
            mirror[b] = rig.find(m) >= 0 ? rig.find(m) : b;
        }
    }

    public SkeletonRig rig() { return rig; }

    /**
     * The chest's share of the view pitch (deg, + = down). Empty-handed 30 % within -15..20; with a rifle 50 % within
     * -25..30, the upper body leaning back with the gun when looking up.
     */
    public static float chestPitch(float viewPitch, boolean rifle) {
        return rifle ? Math.max(-25F, Math.min(30F, viewPitch * 0.5F)) : Math.max(-15F, Math.min(20F, viewPitch * 0.3F));
    }

    /** A hit's flinch shares (HitReactClient; docs/gameplay/injury_knockdown_v1_plan.md 2). */
    static final float FLINCH_PELVIS = 0.25F, FLINCH_CHEST = 0.5F, FLINCH_HEAD = 0.5F;

    private static Quaternionf flinch(Input in, float share) {
        return new Quaternionf().rotationX(rad(in.flinchPitch * share)).rotateZ(rad(in.flinchRoll * share));
    }

    public Report solve(Input in, SkeletonPose pose) {
        Report report = solveBody(in, pose);
        solveArms(in, pose, report);
        return report;
    }

    /**
     * Everything but the arms (they keep the clip's): the game places a gun against the posed chest (a mount on the back,
     * a holster) before it knows the hand targets, then calls {@link #solveArms}.
     */
    public Report solveBody(Input in, SkeletonPose pose) {
        Report report = new Report();
        pose.reset();
        locomotion(in, pose);
        stance(in, pose);
        action(in, pose);
        if (in.rootTurn != null) pose.local[rig.root].set(in.rootTurn);
        else pose.local[rig.root].rotationY(rad(in.rootYaw));
        pose.offset[rig.root].add(in.rootOffset);
        Quaternionf chestClip = new Quaternionf(pose.local[rig.chest]);
        float twist = clamp(in.chestYaw, rig.limits[rig.chest], 1), bend = clamp(in.chestPitch, rig.limits[rig.chest], 0);
        // The turn of the upper body is taken at the hips, the torso turning as one piece: worn gear rides the chest frame
        // and would cut into a lower torso twisted against it. Standing, the thighs turn LEG_SHARE of it about the vertical;
        // walking they keep to the root (the way the stride goes).
        // About the pelvis' own up axis (through the waist pivot), so the chest ends where a waist twist put it.
        float hip = twist * in.hipShare, legShare = LEG_SHARE * (1 - clamp01(in.move));
        pose.local[rig.pelvis].rotateY(rad(hip));
        Quaternionf legBack = new Quaternionf().rotationY(rad(-hip * (1 - legShare)));
        pose.local[rig.thighR].premul(legBack);
        pose.local[rig.thighL].premul(legBack);
        pose.local[rig.chest].rotationY(rad(twist - hip)).rotateX(rad(bend)).mul(chestClip);
        if (in.flinchPitch != 0F || in.flinchRoll != 0F) {
            Quaternionf pelvis = flinch(in, FLINCH_PELVIS), chest = flinch(in, FLINCH_CHEST - FLINCH_PELVIS);
            pose.local[rig.pelvis].premul(pelvis);
            Quaternionf legsBack = new Quaternionf(pelvis).conjugate();
            pose.local[rig.thighR].premul(legsBack);
            pose.local[rig.thighL].premul(legsBack);
            pose.local[rig.chest].premul(chest);
        }
        pose.update();
        // head: the view in model space, relative to the chest as posed; headClip of the way back to the clips' head
        Quaternionf headClip = in.headClip > 0 ? new Quaternionf(pose.local[rig.head]) : null;
        Quaternionf view = new Quaternionf().rotationY(rad(in.viewYaw)).rotateX(rad(in.viewPitch));
        if (in.flinchPitch != 0F || in.flinchRoll != 0F) view.premul(flinch(in, FLINCH_HEAD));
        Quaternionf chestWorld = pose.rotation(rig.chest, new Quaternionf());
        pose.local[rig.head].set(new Quaternionf(chestWorld).conjugate().mul(view));
        clampHead(pose.local[rig.head], rig.limits[rig.head]);
        if (headClip != null) pose.local[rig.head].slerp(headClip, clamp01(in.headClip));
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
        if (in.hipTarget != null) {
            Vector3f at = pose.point(rig.pelvis, rig.pivot[rig.pelvis], new Vector3f());
            pose.offset[rig.root].add(new Vector3f(in.hipTarget).sub(at));
            pose.update();
        }
        if (!Float.isNaN(in.eyeLevel)) report.torso = torso(in, pose);
        else if (in.torsoDirect < 1F) report.torso = compress(pose, 1F, Math.max(in.torsoMin, in.torsoDirect));
        if (in.legsVanilla) {
            Quaternionf pelvisWorld = pose.rotation(rig.pelvis, new Quaternionf()).conjugate();
            legVanilla(pose, rig.thighR, rig.shinR, rig.footR, new Quaternionf(pelvisWorld).mul(in.vanillaRightLeg));
            legVanilla(pose, rig.thighL, rig.shinL, rig.footL, new Quaternionf(pelvisWorld).mul(in.vanillaLeftLeg));
        }
        if (in.thighStretch != 1F || in.shinStretch != 1F) {
            for (boolean right : new boolean[]{true, false}) {
                pose.offset[right ? rig.shinR : rig.shinL].y += thighLength * (in.thighStretch - 1F);
                pose.offset[right ? rig.footR : rig.footL].y += shinLength * (in.shinStretch - 1F);
            }
            pose.update();
        }
        if (rig.profile != null && in.legIkWeight < 1F && !in.legsVanilla) plantFeet(pose, 1F - clamp01(in.legIkWeight));
        if (in.legIkWeight > 0) {
            report.rightLegReach = leg(in, pose, true);
            report.leftLegReach = leg(in, pose, false);
        }
        int[] arms = {rig.upperArmR, rig.forearmR, rig.handR, rig.upperArmL, rig.forearmL, rig.handL};
        for (int i = 0; i < 6; i++) report.clipArms[i].set(pose.local[arms[i]]);
        return report;
    }

    /**
     * The torso compressed until the eye is at {@link Input#eyeLevel}: chest and head each come down {@code h(1-t)} (h:
     * the chest pivot to the neck), the chest's top end squashed by t about its pivot (SkinMesh), the shoulders down with
     * the torso's top. Returns t.
     */
    private float torso(Input in, SkeletonPose pose) {
        // measured at the neck, so the head's own nod does not change the torso: a level head's eye is this far over it
        float neckLevel = in.eyeLevel + (rig.pivot[rig.head].y - rig.sockets.get("eye").pos().y);
        float t = 1F;
        for (int pass = 0; pass < 2; pass++) {
            float at = pose.point(rig.head, rig.pivot[rig.head], new Vector3f()).y;
            t = compress(pose, t, Math.max(in.torsoMin, Math.min(1F, t - (neckLevel - at) / (2 * neckHalf))));
        }
        return t;
    }

    /** From torso share {@code from} to {@code to}: chest and head down by the difference, the squash and the shoulders. */
    private float compress(SkeletonPose pose, float from, float to) {
        float drop = neckHalf * (from - to);
        pose.offset[rig.chest].y += drop;
        pose.offset[rig.head].y += drop;
        pose.squash[rig.chest] = to;
        pose.shoulderDrop = neckHalf * (1 - to);
        pose.update();
        return to;
    }

    /**
     * Two-bone leg IK (seated riders): the thigh from its pivot toward the ankle target, the knee toward the pole (model
     * px direction), the shin folding back, the foot turned to {@link Input#footRest} within its limit. Returns hip ->
     * target over the leg's length.
     */
    private float leg(Input in, SkeletonPose pose, boolean right) {
        Quaternionf own = right ? in.footRestRight : in.footRestLeft;
        Quaternionf rest = own != null ? new Quaternionf(own) : in.footRest != null ? new Quaternionf(in.footRest) : pose.rotation(rig.root, new Quaternionf());
        return legTo(pose, right, right ? in.ankleRight : in.ankleLeft, right ? in.kneePoleRight : in.kneePoleLeft, rest,
                clamp01(in.legIkWeight), in.thighStretch, in.shinStretch);
    }

    /**
     * AFL Block-Mesh V2 (a rig with a {@link SkeletonRig.Profile}): the locomotion and action clips turn the legs for
     * vanilla's shorter ones, so on longer legs the same turns lift the feet. Each foot goes where vanilla's leg, turned
     * as the clips turn this one, puts its sole (its hip lower by the longer waist), by IK with the knee in the plane it
     * has; the foot keeps its turn. {@code w}: how much (the seat's leg IK takes the rest).
     */
    private void plantFeet(SkeletonPose pose, float w) {
        SkeletonRig.Profile p = rig.profile;
        Quaternionf rootRot = pose.rotation(rig.root, new Quaternionf()), pelvisRot = pose.rotation(rig.pelvis, new Quaternionf());
        for (boolean right : new boolean[]{true, false}) {
            int thigh = right ? rig.thighR : rig.thighL, shin = right ? rig.shinR : rig.shinL, foot = right ? rig.footR : rig.footL;
            float sx = right ? -1F : 1F;
            Vector3f hip = pose.point(thigh, rig.pivot[thigh], new Vector3f());
            // vanilla's hip (Java y down): below this one by the waist's difference (the root's frame), apart by the feet's (the pelvis')
            Vector3f vanillaHip = new Vector3f(hip).add(rootRot.transform(new Vector3f(0, (24F - p.refHip().y) - rig.pivot[thigh].y, 0)))
                    .add(pelvisRot.transform(new Vector3f(sx * p.refHip().x - rig.pivot[thigh].x, 0, 0)));
            Quaternionf thighRot = pose.rotation(thigh, new Quaternionf()), shinRot = pose.rotation(shin, new Quaternionf());
            Quaternionf footRot = pose.rotation(foot, new Quaternionf());
            Vector3f ankle = new Vector3f(vanillaHip).add(thighRot.transform(new Vector3f(0, p.refThigh(), 0)))
                    .add(shinRot.transform(new Vector3f(0, p.refShin(), 0)));
            // the sole where vanilla's is: this ankle nearer the sole, the feet apart as this rig's
            ankle.add(footRot.transform(new Vector3f(sx * (p.footX() - p.refHip().x), p.refAnkle() - p.ankle(), 0)));
            Vector3f pole = thighRot.transform(new Vector3f(0, 0, -1));
            legTo(pose, right, ankle, pole, footRot, w, 1F, 1F);
        }
    }

    private float legTo(SkeletonPose pose, boolean right, Vector3f target, Vector3f kneePole, Quaternionf footWorld, float w,
                        float thighStretch, float shinStretch) {
        int thigh = right ? rig.thighR : rig.thighL, shin = right ? rig.shinR : rig.shinL, foot = right ? rig.footR : rig.footL;
        Vector3f hip = pose.point(thigh, rig.pivot[thigh], new Vector3f());
        Vector3f toTarget = new Vector3f(target).sub(hip);
        float thighLength = this.thighLength * thighStretch, shinLength = this.shinLength * shinStretch;
        float reach = thighLength + shinLength, distance = toTarget.length();
        float d = Math.max(0.15F * reach, Math.min(distance, 0.999F * reach));
        Vector3f dir = distance > 1e-4F ? new Vector3f(toTarget).div(distance) : new Vector3f(0, 1, 0);
        float cosKnee = (thighLength * thighLength + shinLength * shinLength - d * d) / (2 * thighLength * shinLength);
        float flex = Math.min(rad(150), (float) Math.PI - (float) Math.acos(Math.max(-1, Math.min(1, cosKnee))));
        float reachD = (float) Math.sqrt(thighLength * thighLength + shinLength * shinLength + 2 * thighLength * shinLength * Math.cos(flex));
        float cosHip = (thighLength * thighLength + reachD * reachD - shinLength * shinLength) / (2 * thighLength * reachD);
        float hipAngle = (float) Math.acos(Math.max(-1, Math.min(1, cosHip)));
        Vector3f pole = new Vector3f(kneePole);
        Vector3f side = new Vector3f(pole).sub(new Vector3f(dir).mul(pole.dot(dir)));
        if (side.lengthSquared() < 1e-8F) side.set(0, 0, -1).sub(new Vector3f(dir).mul(-dir.z));
        side.normalize();
        // thigh: +y toward the knee, the knee's front (-z) toward the pole, the shin folding back (+z)
        Vector3f y = new Vector3f(dir).mul((float) Math.cos(hipAngle)).add(new Vector3f(side).mul((float) Math.sin(hipAngle))).normalize();
        Vector3f z = new Vector3f(side).sub(new Vector3f(y).mul(side.dot(y))).normalize().negate();
        Vector3f x = new Vector3f(y).cross(z).normalize();
        Quaternionf thighWorld = new Quaternionf().setFromNormalized(new Matrix4f().set3x3(new org.joml.Matrix3f(x, y, z)));
        Quaternionf pelvisWorld = pose.rotation(rig.parent[thigh], new Quaternionf());
        Quaternionf thighLocal = new Quaternionf(pelvisWorld).conjugate().mul(thighWorld).normalize();
        Quaternionf shinLocal = new Quaternionf().rotationX(flex);
        Quaternionf footLocal = new Quaternionf(thighWorld).mul(shinLocal).conjugate().mul(footWorld).normalize();
        clampEuler(footLocal, rig.limits[foot]);
        pose.local[thigh].slerp(thighLocal, w).normalize();
        pose.local[shin].slerp(shinLocal, w).normalize();
        pose.local[foot].slerp(footLocal, w).normalize();
        pose.updateFrom(thigh);
        return distance / reach;
    }

    /** The arms on a body from {@link #solveBody} (again, from the clip's arms, when the targets changed). */
    public void solveArms(Input in, SkeletonPose pose, Report report) {
        int[] arms = {rig.upperArmR, rig.forearmR, rig.handR, rig.upperArmL, rig.forearmL, rig.handL};
        for (int i = 0; i < 6; i++) pose.local[arms[i]].set(report.clipArms[i]);
        pose.offset[rig.upperArmR].set(0, pose.shoulderDrop, 0);
        pose.offset[rig.upperArmL].set(0, pose.shoulderDrop, 0);
        pose.updateFrom(rig.upperArmR);
        pose.updateFrom(rig.upperArmL);
        Quaternionf chestWorld = pose.rotation(rig.chest, new Quaternionf());
        arm(in, pose, report, true, chestWorld);
        arm(in, pose, report, false, chestWorld);
    }

    /**
     * After {@link #solveBody}, with the hand targets set: how far (model px) both targets must come back along {@code back}
     * (unit, model px) for each wrist to be within the IK's reach of its shoulder (0.98 of the arm plus
     * {@link Input#protraction}; the shoulder offsets included). 0 when they already are. A gun held out at arm's length
     * (GunStance.Kind#sightDistance) comes back toward the eye by this much instead of leaving a hand short.
     */
    public float shortfall(Input in, SkeletonPose pose, Vector3f back) {
        Quaternionf chestWorld = pose.rotation(rig.chest, new Quaternionf());
        float want = 0.98F * (upperLength + foreLength) + in.protraction, s = 0;
        for (boolean right : new boolean[]{true, false}) {
            if ((right ? in.right : in.left) != ArmMode.IK || (right ? in.ikRightWeight : in.ikLeftWeight) < 0.5F) continue;
            int upper = right ? rig.upperArmR : rig.upperArmL, hand = right ? rig.handR : rig.handL;
            Vector3f restPivot = rig.pivot[upper];
            Vector3f wrist = (right ? in.targetRight : in.targetLeft).transformPosition(new Vector3f(rig.pivot[hand]).sub(restPivot));
            Vector3f shoulder = pose.world[rig.chest].transformPosition(new Vector3f(restPivot))
                    .add(chestWorld.transform(shoulderSet(in, pose, right)));
            Vector3f d = wrist.sub(shoulder);
            if (d.length() <= want) continue;
            // smallest t >= 0 with |d + t back| = want; past the closest approach, the closest approach
            float b = d.dot(back), c = d.lengthSquared() - want * want, disc = b * b - c;
            s = Math.max(s, Math.max(0, disc >= 0 ? -b - (float) Math.sqrt(disc) : -b));
        }
        return s;
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

    /**
     * The stance loops over the locomotion ({@link Input#stance}): on each bone, the turns and offsets of the clips that key
     * it by their weights, the locomotion's by what is left (none past a total of 1, the clips' then shared).
     */
    private void stance(Input in, SkeletonPose pose) {
        if (in.stance.isEmpty()) return;
        Quaternionf base = new Quaternionf(), q = new Quaternionf(), sum = new Quaternionf();
        Vector3f p = new Vector3f();
        for (int b = 0; b < rig.size(); b++) {
            float turned = 0, moved = 0;
            for (Input.StanceClip s : in.stance) {
                if (s.clip() == null || s.weight() <= 0 || b >= s.mask().length || !s.mask()[b]) continue;
                int from = s.mirror() ? mirror[b] : b;
                if (s.clip().rotates(from)) turned += s.weight();
                if (s.clip().moves(from)) moved += s.weight();
            }
            if (turned > 0) {
                float share = turned > 1 ? 1 / turned : 1, rest = Math.max(0, 1 - turned);
                base.set(pose.local[b]);
                sum.set(base.x * rest, base.y * rest, base.z * rest, base.w * rest);
                for (Input.StanceClip s : in.stance) {
                    int from = s.mirror() ? mirror[b] : b;
                    if (s.clip() == null || s.weight() <= 0 || b >= s.mask().length || !s.mask()[b] || !s.clip().rotates(from)) continue;
                    s.clip().rotation(from, s.time(), q);
                    if (s.mirror()) q.set(q.x, -q.y, -q.z, q.w);
                    if (base.dot(q) < 0) q.set(-q.x, -q.y, -q.z, -q.w);
                    float w = s.weight() * share;
                    sum.set(sum.x + q.x * w, sum.y + q.y * w, sum.z + q.z * w, sum.w + q.w * w);
                }
                if (sum.lengthSquared() > 1e-12F) pose.local[b].set(sum).normalize();
            }
            if (moved > 0) {
                float share = moved > 1 ? 1 / moved : 1;
                Vector3f offset = pose.offset[b].mul(Math.max(0, 1 - moved));
                for (Input.StanceClip s : in.stance) {
                    int from = s.mirror() ? mirror[b] : b;
                    if (s.clip() == null || s.weight() <= 0 || b >= s.mask().length || !s.mask()[b] || !s.clip().moves(from)) continue;
                    s.clip().position(from, s.time(), p);
                    if (s.mirror()) p.x = -p.x;
                    offset.add(p.mul(s.weight() * share));
                }
            }
        }
    }

    /** The action's clips over the locomotion, on its mask's bones (plan 2.3: override, weighted). */
    private void action(Input in, SkeletonPose pose) {
        if (in.actionMask == null) return;
        apply(in, pose, in.actionClip, in.actionTime, in.actionWeight);
        apply(in, pose, in.paramClip, 0F, in.paramWeight);
    }

    private void apply(Input in, SkeletonPose pose, SkeletonClips.Clip clip, float time, float weight) {
        if (clip == null || weight <= 0) return;
        float w = Math.min(1F, weight);
        Quaternionf q = new Quaternionf();
        Vector3f p = new Vector3f();
        for (int b = 0; b < rig.size(); b++) {
            if (b >= in.actionMask.length || !in.actionMask[b]) continue;
            int from = in.actionMirror ? mirror[b] : b;
            if (clip.rotates(from)) {
                clip.rotation(from, time, q);
                if (in.actionMirror) q.set(q.x, -q.y, -q.z, q.w);   // mirrored across the body's middle (x)
                pose.local[b].slerp(q, w).normalize();
            }
            if (clip.moves(from)) {
                clip.position(from, time, p);
                if (in.actionMirror) p.x = -p.x;
                pose.offset[b].lerp(p, w);
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
            float w = clamp01(right ? in.ikRightWeight : in.ikLeftWeight);
            if (w > 0) {
                Quaternionf u = new Quaternionf(pose.local[upper]), f = new Quaternionf(pose.local[fore]),
                        h = new Quaternionf(pose.local[hand]);
                ik(in, pose, report, right, upper, fore, hand, chestWorld);
                if (w < 1) {
                    pose.local[upper].set(u.slerp(pose.local[upper], w));
                    pose.local[fore].set(f.slerp(pose.local[fore], w));
                    pose.local[hand].set(h.slerp(pose.local[hand], w));
                    pose.offset[upper].set(new Vector3f(0, pose.shoulderDrop, 0).lerp(pose.offset[upper], w));
                }
            }
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

    /**
     * After {@link #solveBody}: the forearm's frame (model rotation, +y toward the hand) the IK will give a wrist at
     * {@code wrist} (model px) with no forearm twist, as {@link #ik} places the elbow (the shoulder's offset and reach
     * included, no support slide). A hand turned this way needs no wrist bend and no twist (player actions V1: any other
     * turn twists the 4 px box forearm like a rope).
     */
    public Quaternionf forearm(Input in, SkeletonPose pose, boolean right, Vector3f wrist) {
        int upper = right ? rig.upperArmR : rig.upperArmL;
        Quaternionf chestWorld = pose.rotation(rig.chest, new Quaternionf());
        Vector3f shoulder = pose.world[rig.chest].transformPosition(new Vector3f(rig.pivot[upper]))
                .add(chestWorld.transform(shoulderSet(in, pose, right)));
        float reach = upperLength + foreLength;
        Vector3f toTarget = new Vector3f(wrist).sub(shoulder);
        float distance = toTarget.length();
        float protract = Math.min(in.protraction, Math.max(0, distance - 0.98F * reach));
        if (protract > 0 && distance > 1e-4F) shoulder.add(new Vector3f(toTarget).mul(protract / distance));
        toTarget.set(wrist).sub(shoulder);
        distance = toTarget.length();
        float d = Math.max(0.15F * reach, Math.min(distance, 0.999F * reach));
        Vector3f dir = distance > 1e-4F ? new Vector3f(toTarget).div(distance) : new Vector3f(0, 1, 0);
        float cosElbow = (upperLength * upperLength + foreLength * foreLength - d * d) / (2 * upperLength * foreLength);
        float flex = Math.min(ELBOW_MAX, (float) Math.PI - (float) Math.acos(Math.max(-1, Math.min(1, cosElbow))));
        float reachD = (float) Math.sqrt(upperLength * upperLength + foreLength * foreLength + 2 * upperLength * foreLength * Math.cos(flex));
        float cosShoulder = (upperLength * upperLength + reachD * reachD - foreLength * foreLength) / (2 * upperLength * reachD);
        float shoulderAngle = (float) Math.acos(Math.max(-1, Math.min(1, cosShoulder)));
        Vector3f pole = chestWorld.transform(new Vector3f(right ? in.poleRight : in.poleLeft));
        Vector3f side = new Vector3f(pole).sub(new Vector3f(dir).mul(pole.dot(dir)));
        if (side.lengthSquared() < 1e-8F) side.set(0, 0, 1).sub(new Vector3f(dir).mul(dir.z));
        side.normalize();
        Vector3f y = new Vector3f(dir).mul((float) Math.cos(shoulderAngle)).add(new Vector3f(side).mul((float) Math.sin(shoulderAngle))).normalize();
        Vector3f z = new Vector3f(side).sub(new Vector3f(y).mul(side.dot(y))).normalize();
        Vector3f x = new Vector3f(y).cross(z).normalize();
        Quaternionf upperWorld = new Quaternionf().setFromNormalized(new Matrix4f().set3x3(new org.joml.Matrix3f(x, y, z)));
        return upperWorld.mul(new Quaternionf().rotationX(-flex));
    }

    private void ik(Input in, SkeletonPose pose, Report report, boolean right, int upper, int fore, int hand, Quaternionf chestWorld) {
        Matrix4f target = right ? in.targetRight : in.targetLeft;
        Vector3f restPivot = rig.pivot[upper];
        Vector3f wrist = target.transformPosition(new Vector3f(rig.pivot[hand]).sub(restPivot));
        Quaternionf handWorld = target.getUnnormalizedRotation(new Quaternionf());
        Matrix4f chest = pose.world[rig.chest];
        Vector3f set = shoulderSet(in, pose, right);
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
        // Block-Mesh V2: a hand still out of reach (the clips' targets were set for vanilla's shoulders) takes up to
        // REACH_IN more of the shoulder blade's reach, shared with the wrist's below
        float extra = rig.profile != null ? Math.min(REACH_IN, Math.max(0, distance - 0.98F * reach - protract)) : 0F;
        protract += extra;
        Vector3f shift = new Vector3f();
        if (protract > 0 && distance > 1e-4F) shift.set(toTarget).mul(protract / distance);
        pose.offset[upper].set(new Quaternionf(chestWorld).conjugate().transform(new Vector3f(shift))).add(set);
        shoulder.add(shift);
        toTarget.set(wrist).sub(shoulder);
        distance = toTarget.length();
        Vector3f poleWorld = chestWorld.transform(new Vector3f(right ? in.poleRight : in.poleLeft));
        Quaternionf[] arm = new Quaternionf[3];
        float flex = armAt(shoulder, wrist, poleWorld, 0F, handWorld, chestWorld, arm), used = 0F;
        if (rig.profile != null && angleOf(arm[2]) > REACH_WRIST && distance > 1e-4F) {
            // one axis at a time (about 30 arm solves): the swing by a 20 deg scan and a golden-section search about the
            // best; the shoulder forward over [0, what REACH_IN leaves] with that swing; the swing again, finer. Each
            // search on a cost smooth in its axis, so the arm moves smoothly frame to frame; kept only where it beats the
            // pole's arm
            Vector3f along = new Vector3f(toTarget).div(distance);
            Quaternionf[] tried = new Quaternionf[3];
            float room = Math.max(0F, Math.min(REACH_IN - extra, 0.5F * distance)), step = (float) Math.toRadians(20);
            float swing = 0F, bestSwing = cost(shoulder, along, 0F, 0F, wrist, poleWorld, handWorld, chestWorld, tried);
            for (int k = -3; k <= 3; k++) {
                if (k == 0) continue;
                float c = cost(shoulder, along, 0F, k * step, wrist, poleWorld, handWorld, chestWorld, tried);
                if (c < bestSwing) { bestSwing = c; swing = k * step; }
            }
            swing = golden(shoulder, along, 0F, swing, false, Math.max(-SWING_MAX, swing - step), Math.min(SWING_MAX, swing + step), 8,
                    wrist, poleWorld, handWorld, chestWorld, tried);
            float forward = room > 1e-4F ? golden(shoulder, along, 0F, swing, true, 0F, room, 8, wrist, poleWorld, handWorld, chestWorld, tried) : 0F;
            float fine = step / 2F;
            swing = golden(shoulder, along, forward, swing, false, Math.max(-SWING_MAX, swing - fine), Math.min(SWING_MAX, swing + fine), 6,
                    wrist, poleWorld, handWorld, chestWorld, tried);
            if (cost(shoulder, along, forward, swing, wrist, poleWorld, handWorld, chestWorld, tried) < wristCost(arm)) {
                Vector3f more = new Vector3f(along).mul(forward);
                shoulder.add(more);
                pose.offset[upper].add(new Quaternionf(chestWorld).conjugate().transform(more));
                used = swing;
                flex = armAt(shoulder, wrist, poleWorld, used, handWorld, chestWorld, arm);
            }
            // the wrist still past its limit: the hand turns less than asked, so the wrist moves to keep the hand's end where
            // the target puts it (the palm stays on the grip, the hand a little off its angle); PALM_ROUNDS over-relaxed
            // rounds of the fixed point, back to plain steps where one would miss by more
            if (angleOf(arm[2]) > WRIST_MAX) {
                Vector3f capArm = new Vector3f((right ? 1F : -1F) * rig.profile.handCapX(), rig.profile.handCapY(), 0F);
                Vector3f capHand = new Vector3f(capArm).add(restPivot).sub(rig.pivot[hand]);
                Vector3f capWant = target.transformPosition(new Vector3f(capArm));
                Quaternionf[] next = new Quaternionf[3];
                float miss = capMiss(arm, chestWorld, wrist, capHand, capWant), relax = PALM_RELAX;
                for (int k = 0; k < PALM_ROUNDS && miss > 1e-3F; k++) {
                    Vector3f err = capPoint(arm, chestWorld, wrist, capHand).sub(capWant);
                    Vector3f moved = new Vector3f(wrist).sub(new Vector3f(err).mul(relax));
                    float f2 = armAt(shoulder, moved, poleWorld, used, handWorld, chestWorld, next);
                    float m2 = capMiss(next, chestWorld, moved, capHand, capWant);
                    if (m2 >= miss && relax > 1F) { relax = 1F; continue; }
                    if (m2 >= miss) break;
                    wrist.set(moved);
                    System.arraycopy(next, 0, arm, 0, 3);
                    flex = f2;
                    miss = m2;
                }
            }
        }
        pose.local[upper].set(arm[0]);
        pose.local[fore].set(arm[1]);
        limitAngle(arm[2], WRIST_MAX);
        pose.local[hand].set(arm[2]);
        float ratio = new Vector3f(wrist).sub(shoulder).length() / reach;
        if (right) { report.rightReach = ratio; report.rightElbowDeg = (float) Math.toDegrees(flex); }
        else { report.leftReach = ratio; report.leftElbowDeg = (float) Math.toDegrees(flex); }
    }

    /**
     * Two-bone IK from {@code shoulder} to {@code wrist} (model px), the elbow toward {@code poleWorld} swung {@code swing}
     * (rad) about the shoulder-wrist line: the arm's locals
     * into {@code out} (upper arm in the chest, the forearm flexed and twisted toward the hand within TWIST_MAX, the rest at
     * the wrist, not yet limited); the elbow's flex (rad).
     */
    private float armAt(Vector3f shoulder, Vector3f wrist, Vector3f poleWorld, float swing, Quaternionf handWorld, Quaternionf chestWorld,
                        Quaternionf[] out) {
        float reach = upperLength + foreLength;
        Vector3f toTarget = new Vector3f(wrist).sub(shoulder);
        float distance = toTarget.length();
        float d = Math.max(0.15F * reach, Math.min(distance, 0.999F * reach));
        Vector3f dir = distance > 1e-4F ? new Vector3f(toTarget).div(distance) : new Vector3f(0, 1, 0);
        float cosElbow = (upperLength * upperLength + foreLength * foreLength - d * d) / (2 * upperLength * foreLength);
        float flex = Math.min(ELBOW_MAX, (float) Math.PI - (float) Math.acos(Math.max(-1, Math.min(1, cosElbow))));
        // shoulder angle for the (possibly clamped) flex
        float reachD = (float) Math.sqrt(upperLength * upperLength + foreLength * foreLength + 2 * upperLength * foreLength * Math.cos(flex));
        float cosShoulder = (upperLength * upperLength + reachD * reachD - foreLength * foreLength) / (2 * upperLength * reachD);
        float shoulderAngle = (float) Math.acos(Math.max(-1, Math.min(1, cosShoulder)));
        Vector3f side = new Vector3f(poleWorld).sub(new Vector3f(dir).mul(poleWorld.dot(dir)));
        if (side.lengthSquared() < 1e-8F) side.set(0, 0, 1).sub(new Vector3f(dir).mul(dir.z));
        side.normalize();
        if (swing != 0F) side.rotateAxis(swing, dir.x, dir.y, dir.z);
        // upper arm: +y toward the elbow, +z toward the pole (the elbow's point), the forearm folding toward -z
        Vector3f y = new Vector3f(dir).mul((float) Math.cos(shoulderAngle)).add(new Vector3f(side).mul((float) Math.sin(shoulderAngle))).normalize();
        Vector3f z = new Vector3f(side).sub(new Vector3f(y).mul(side.dot(y))).normalize();
        Vector3f x = new Vector3f(y).cross(z).normalize();
        Quaternionf upperWorld = new Quaternionf().setFromNormalized(new Matrix4f().set3x3(new org.joml.Matrix3f(x, y, z)));
        out[0] = new Quaternionf(chestWorld).conjugate().mul(upperWorld).normalize();
        Quaternionf foreFlex = new Quaternionf().rotationX(-flex);
        Quaternionf rel = new Quaternionf(upperWorld).mul(foreFlex).conjugate().mul(handWorld);
        Quaternionf twist = new Quaternionf(0, rel.y, 0, rel.w);
        if (twist.lengthSquared() < 1e-8F) twist.identity();
        else twist.normalize();
        limitAngle(twist, TWIST_MAX);
        out[1] = new Quaternionf(foreFlex).mul(twist);
        out[2] = new Quaternionf(twist).conjugate().mul(rel).normalize();
        return flex;
    }

    /**
     * The arm with the shoulder {@code in} px along {@code along} and the elbow swung {@code swing} (rad): its
     * {@link #wristCost}, {@link #REACH_COST} a px squared and {@link #SWING_COST} a degree squared.
     */
    private float cost(Vector3f shoulder, Vector3f along, float in, float swing, Vector3f wrist, Vector3f poleWorld, Quaternionf handWorld,
                       Quaternionf chestWorld, Quaternionf[] tried) {
        armAt(new Vector3f(along).mul(in).add(shoulder), wrist, poleWorld, swing, handWorld, chestWorld, tried);
        float d = (float) Math.toDegrees(swing);
        return wristCost(tried) + REACH_COST * in * in + SWING_COST * d * d;
    }

    /** A golden-section search over [lo, hi] of the shoulder's reach ({@code reach}) or the swing, the other held. */
    private float golden(Vector3f shoulder, Vector3f along, float in, float swing, boolean reach, float lo, float hi, int rounds,
                         Vector3f wrist, Vector3f poleWorld, Quaternionf handWorld, Quaternionf chestWorld, Quaternionf[] tried) {
        float g = 0.618034F, a = hi - g * (hi - lo), b = lo + g * (hi - lo);
        float ca = cost(shoulder, along, reach ? a : in, reach ? swing : a, wrist, poleWorld, handWorld, chestWorld, tried);
        float cb = cost(shoulder, along, reach ? b : in, reach ? swing : b, wrist, poleWorld, handWorld, chestWorld, tried);
        for (int k = 0; k < rounds; k++) {
            if (ca < cb) {
                hi = b; b = a; cb = ca; a = hi - g * (hi - lo);
                ca = cost(shoulder, along, reach ? a : in, reach ? swing : a, wrist, poleWorld, handWorld, chestWorld, tried);
            } else {
                lo = a; a = b; ca = cb; b = lo + g * (hi - lo);
                cb = cost(shoulder, along, reach ? b : in, reach ? swing : b, wrist, poleWorld, handWorld, chestWorld, tried);
            }
        }
        return (lo + hi) / 2F;
    }

    /** The hand's end (model px) as the arm draws it: the wrist bent at most WRIST_MAX. */
    private static Vector3f capPoint(Quaternionf[] arm, Quaternionf chestWorld, Vector3f wrist, Vector3f capHand) {
        Quaternionf bent = new Quaternionf(arm[2]);
        limitAngle(bent, WRIST_MAX);
        return new Quaternionf(chestWorld).mul(arm[0]).mul(arm[1]).mul(bent).transform(new Vector3f(capHand)).add(wrist);
    }

    private static float capMiss(Quaternionf[] arm, Quaternionf chestWorld, Vector3f wrist, Vector3f capHand, Vector3f want) {
        return capPoint(arm, chestWorld, wrist, capHand).distance(want);
    }

    /** The wrist bend past {@link #REACH_WRIST} and the forearm twist past {@link #REACH_TWIST}, rad squared. */
    private static float wristCost(Quaternionf[] arm) {
        float wrist = Math.max(0F, angleOf(arm[2]) - REACH_WRIST);
        float twist = Math.max(0F, angleOf(new Quaternionf(0, arm[1].y, 0, arm[1].w).normalize()) - REACH_TWIST);
        return wrist * wrist + twist * twist;
    }

    private static float angleOf(Quaternionf q) {
        return 2F * (float) Math.acos(Math.min(1F, Math.abs(q.w)));
    }

    /** An arm's shoulder set in the chest frame, with the compressed torso's shoulder drop. */
    private static Vector3f shoulderSet(Input in, SkeletonPose pose, boolean right) {
        return new Vector3f(right ? in.shoulderRight : in.shoulderLeft).add(0, pose.shoulderDrop, 0);
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

    /**
     * The head's limits, turn first, then nod (the way the view is built): Z-Y-X angles keep their middle (the turn)
     * within 90 deg, so a look turned further (a seated rider looks up to 120 deg off the seat's way) came out as a flip
     * and a nod, and the clamped nod tipped the head over onto its side (user 2026-10-16).
     */
    static void clampHead(Quaternionf q, SkeletonRig.Limit limit) {
        if (limit == null) return;
        Vector3f e = q.getEulerAnglesYXZ(new Vector3f());
        float x = clampDeg(e.x, limit.minX(), limit.maxX()), y = clampDeg(e.y, limit.minY(), limit.maxY()),
                z = clampDeg(e.z, limit.minZ(), limit.maxZ());
        q.rotationYXZ(y, x, z);
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
