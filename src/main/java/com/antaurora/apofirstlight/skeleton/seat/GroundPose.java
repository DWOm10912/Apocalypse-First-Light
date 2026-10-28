package com.antaurora.apofirstlight.skeleton.seat;

import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * AFL Block-Mesh V2 sat on the ground (docs/dev/block_mesh_v2_b_ingame.md 17.5; user 2026-10-26 "可以做3种坐姿，盘腿、两条腿弓起来
 * 那种，还有一种是我们现在的这种坐姿", "每次坐下随机一种", "前臂搭在膝盖上"): the sitting clips (body.sit_down, body.sit,
 * body.stand_up) were keyed for vanilla's 12 px legs, so B's 15 px ones left the seat 3.4 px (20 cm) over the ground. Here
 * the hips go down on the ground and the legs and the free hands go where the approved sheet put them
 * (design/characters/block_mesh_v2/b4_blender/b5_ground_sit_r1.png, b5_ground_sit.py), by the poser's leg and arm IK, over
 * the clip by a weight (sitting down, getting up). The pelvis keeps the clip's tilt; the chest leans back by the style's
 * {@link Style#bend}. Pure (model px, Java: y down, front -z, the body's right -x), for client/skeleton/GroundSitClient and
 * the offline checks alike.
 * <ul>
 *   <li>{@link Style#OUT}: as before, the left leg out straight (toes up), the right knee up, the right forearm over it,
 *   the left hand on the left thigh;</li>
 *   <li>{@link Style#CROSS}: cross-legged, the knees wide and low, the right shin in front, the left foot tucked under it,
 *   the hands on the knees;</li>
 *   <li>{@link Style#KNEES}: both knees up, the feet flat ahead, the forearms over the knees, the hands hanging in front.</li>
 * </ul>
 * Mirrored (the right leg the hurt one, GroundSitClient#mirror): across the body's middle.
 */
public final class GroundPose {
    /** The sheet's numbers are Blender px (z up, front -y, the body's right +x): {@link #b} turns them into model px. */
    public enum Style {
        OUT(2.55F, -8F,
                Leg.straight(b(0.02F, -1F, -0.03F), b(0F, -0.3F, 1F), b(0F, -1F, -0.3F)),
                Leg.reach(b(2.6F, -7.2F, 1.5F), b(0.15F, -0.4F, 1F), b(0.05F, -1F, 0F), b(0F, 0F, -1F)),
                Hand.atKnee(b(-0.3F, -2.4F, 3.3F), b(0.6F, 1F, -0.2F), b(0F, -0.45F, -1F)),
                Hand.atHip(b(-0.3F, -5.6F, 3.9F), b(-0.6F, 1F, 0.3F), b(0F, -1F, -0.35F))),
        CROSS(2.0F, -16F,
                Leg.bent(b(-4.7F, -3.7F, 0F), b(0.97F, -0.23F, 0.03F), b(1F, -0.3F, 0F), b(0F, 1F, 0.3F)),
                Leg.bent(b(4.2F, -4.3F, 0.1F), b(-0.74F, -0.68F, 0.05F), b(-1F, -0.25F, 0F), b(0F, 1F, 0.2F)),
                Hand.atKnee(b(-0.8F, -0.6F, 3.4F), b(1F, 0.6F, -0.2F), b(-0.25F, -0.6F, -1F)),
                Hand.atKnee(b(0.8F, -0.6F, 3.4F), b(-1F, 0.6F, -0.2F), b(0.25F, -0.6F, -1F))),
        KNEES(1.4F, -12F,
                Leg.reach(b(-2.7F, -7.6F, 1.5F), b(-0.1F, -0.4F, 1F), b(-0.05F, -1F, 0F), b(0F, 0F, -1F)),
                Leg.reach(b(2.7F, -7.6F, 1.5F), b(0.1F, -0.4F, 1F), b(0.05F, -1F, 0F), b(0F, 0F, -1F)),
                Hand.atKnee(b(-0.4F, -2.2F, 3.3F), b(0.5F, 1F, -0.2F), b(-0.1F, -0.4F, -1F)),
                Hand.atKnee(b(0.4F, -2.2F, 3.3F), b(-0.5F, 1F, -0.2F), b(0.1F, -0.4F, -1F)));

        /** The hip joints over the ground (px); the chest's lean on the clip's (deg, + forward); legs and hands left, right. */
        public final float hipHeight, bend;
        final Leg legL, legR;
        final Hand handR, handL;

        Style(float hipHeight, float bend, Leg legL, Leg legR, Hand handR, Hand handL) {
            this.hipHeight = hipHeight;
            this.bend = bend;
            this.legL = legL;
            this.legR = legR;
            this.handR = handR;
            this.handL = handL;
        }
    }

    /** The hips this far behind the model's origin (px, the sheet's): over the place the player stood. */
    public static final float HIP_BACK = 1F;

    /**
     * A leg: by its thigh's and shin's ways from the hip ({@code thigh} non-null), or its ankle's place over the ground
     * under the hip ({@code ankle}: x and z off the model's origin, y up from the ground) with the knee toward {@code pole};
     * the foot's toes and sole ways.
     */
    record Leg(Vector3f thigh, Vector3f shin, Vector3f ankle, Vector3f pole, Vector3f toe, Vector3f sole) {
        static Leg straight(Vector3f way, Vector3f toe, Vector3f sole) {
            return new Leg(way, way, null, null, toe, sole);
        }

        static Leg bent(Vector3f thigh, Vector3f shin, Vector3f toe, Vector3f sole) {
            return new Leg(thigh, shin, null, null, toe, sole);
        }

        static Leg reach(Vector3f ankle, Vector3f pole, Vector3f toe, Vector3f sole) {
            return new Leg(null, null, new Vector3f(ankle.x, -ankle.y, ankle.z), pole, toe, sole);
        }
    }

    /** A free hand: its wrist off the knee (or the hip) of its own side, the elbow toward {@code pole}, the hand's way. */
    record Hand(Vector3f off, boolean knee, Vector3f pole, Vector3f way) {
        static Hand atKnee(Vector3f off, Vector3f pole, Vector3f way) {
            return new Hand(off, true, pole, way);
        }

        static Hand atHip(Vector3f off, Vector3f pole, Vector3f way) {
            return new Hand(off, false, pole, way);
        }
    }

    private GroundPose() {
    }

    /** Blender px (x the body's right, y front -, z up) to model px. */
    static Vector3f b(float x, float y, float z) {
        return new Vector3f(-x, -z, y);
    }

    /**
     * The style a sit takes: {@link Style#OUT} for a hurt leg (the splint and dressings are keyed on it), otherwise one of
     * the three by the seat entity's id (the same on every client).
     */
    public static Style style(int seatId, boolean legHurt) {
        if (legHurt) return Style.OUT;
        int h = (seatId * 0x9E3779B1) >>> 16;
        return Style.values()[h % 3];
    }

    /**
     * Before the body solve, over the sitting clip on the stance layer: the hips on the ground ({@code floor}: its model
     * y), the legs by IK, the chest's lean, {@code w} of the way (0: the clip as it is). The hand targets come after the
     * body solve ({@link #hands}).
     */
    public static void body(HumanoidPoser.Input in, SkeletonRig rig, Style s, boolean mirror, float floor, float w) {
        if (w <= 0F) return;
        Quaternionf yaw = new Quaternionf().rotationY((float) Math.toRadians(in.rootYaw));
        Vector3f hip = yaw.transform(new Vector3f(0, 0, HIP_BACK)).add(0, floor - s.hipHeight, 0);
        in.hipTarget = new Vector3f(hip);
        in.hipWeight = w;
        in.plantedBlend = true;
        in.legIkWeight = w;
        in.hipShare *= 1F - w;
        in.chestPitch += s.bend * w;
        float thigh = rig.pivot[rig.shinR].distance(rig.pivot[rig.thighR]), shin = rig.pivot[rig.footR].distance(rig.pivot[rig.shinR]);
        for (boolean right : new boolean[]{true, false}) {
            Leg leg = (right != mirror) ? s.legR : s.legL;
            int t = right ? rig.thighR : rig.thighL;
            Vector3f hipJoint = new Vector3f(hip).add(yaw.transform(new Vector3f(rig.pivot[t]).sub(rig.pivot[rig.pelvis]).mul(1, 0, 0)));
            Vector3f ankle, pole;
            if (leg.thigh() != null) {
                Vector3f knee = new Vector3f(hipJoint).add(yaw.transform(side(leg.thigh(), mirror).normalize().mul(thigh)));
                ankle = new Vector3f(knee).add(yaw.transform(side(leg.shin(), mirror).normalize().mul(shin)));
                pole = new Vector3f(knee).sub(hipJoint);
            } else {
                ankle = yaw.transform(side(leg.ankle(), mirror).mul(1, 0, 1)).add(0, floor - leg.ankle().y, 0);
                pole = yaw.transform(side(leg.pole(), mirror));
            }
            (right ? in.ankleRight : in.ankleLeft).set(ankle);
            (right ? in.kneePoleRight : in.kneePoleLeft).set(pole);
            Quaternionf foot = new Quaternionf(yaw).mul(foot(side(leg.toe(), mirror), side(leg.sole(), mirror)));
            if (right) in.footRestRight = foot;
            else in.footRestLeft = foot;
        }
    }

    /**
     * After the body solve: the free hands where the style puts them (IK, {@code w} of the way). A hand that holds or uses
     * something, or swings, is left as it is (SeatPose#lapHands); an action's hands come after.
     */
    public static void hands(HumanoidPoser.Input in, SkeletonPose pose, HumanoidPoser poser, Style s, boolean mirror, float w) {
        if (w <= 0F) return;
        SkeletonRig rig = pose.rig;
        Quaternionf yaw = new Quaternionf().rotationY((float) Math.toRadians(in.rootYaw));
        Quaternionf chest = pose.rotation(rig.chest, new Quaternionf()).conjugate();
        for (boolean right : new boolean[]{true, false}) {
            if ((right ? in.right : in.left) == HumanoidPoser.ArmMode.VANILLA) continue;
            if ((right ? in.vanillaRightWeight : in.vanillaLeftWeight) > 0F) continue;
            Hand hand = (right != mirror) ? s.handR : s.handL;
            int from = hand.knee() ? (right ? rig.shinR : rig.shinL) : (right ? rig.thighR : rig.thighL);
            Vector3f wrist = pose.point(from, rig.pivot[from], new Vector3f()).add(yaw.transform(side(hand.off(), mirror)));
            (right ? in.poleRight : in.poleLeft).set(chest.transform(yaw.transform(side(hand.pole(), mirror))));
            Quaternionf fore = poser.forearm(in, pose, right, wrist);
            Vector3f way = yaw.transform(side(hand.way(), mirror)).normalize();
            Quaternionf q = new Quaternionf().rotationTo(fore.transform(new Vector3f(0, 1, 0)), way).mul(fore);
            (right ? in.targetRight : in.targetLeft).set(SeatPose.frame(rig, right, wrist, q));
            if (right) { in.right = HumanoidPoser.ArmMode.IK; in.ikRightWeight = w; }
            else { in.left = HumanoidPoser.ArmMode.IK; in.ikLeftWeight = w; }
        }
    }

    /** The sheet's vector for this side: across the body's middle when mirrored. */
    private static Vector3f side(Vector3f v, boolean mirror) {
        return new Vector3f(mirror ? -v.x : v.x, v.y, v.z);
    }

    /** The foot's model turn: its toes (rest -z) and sole (rest +y, down) these ways. */
    private static Quaternionf foot(Vector3f toe, Vector3f sole) {
        Vector3f y = new Vector3f(sole).normalize();
        Vector3f z = new Vector3f(toe).negate();
        z.sub(new Vector3f(y).mul(z.dot(y))).normalize();
        Vector3f x = new Vector3f(y).cross(z);
        return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
    }

    /** 0 before {@code from}, 1 after {@code to}, smoothstep between (the sitting clips' share of the pose, GroundSitClient). */
    public static float ease(float t, float from, float to) {
        float x = Math.max(0F, Math.min(1F, (t - from) / (to - from)));
        return x * x * (3F - 2F * x);
    }
}
