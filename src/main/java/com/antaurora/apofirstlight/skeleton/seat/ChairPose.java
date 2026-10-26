package com.antaurora.apofirstlight.skeleton.seat;

import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * AFL Block-Mesh V2 sat on a chair, over {@link ChairLegs} (docs/dev/block_mesh_v2_b_ingame.md 12): pure (model px, Java:
 * y down, front -z, the body's right -x), for client/skeleton/ChairSitClient and the offline checks alike.
 * <ul>
 *   <li>The hands on the thighs; a hand that holds or uses something, swings, or is an action's is left alone (as
 *   SeatPose#lapHands).</li>
 *   <li>The look is the front's only (cabin/ChairSit#LOOK): the chest twists toward it within the waist's twist, scooting
 *   forward to keep off the backrest.</li>
 *   <li>Sat a while, one foot fidgets ({@link Idle}; user 2026-10-25: "坐久了抖腿或者是动动脚", the leaning and the crossed
 *   legs dropped): the heel bounces on the ball of the foot ({@link Idle#BOUNCE}), or the foot slides forward and out and
 *   rests there ({@link Idle#SHIFT}).</li>
 * </ul>
 */
public final class ChairPose {
    public enum Idle { NONE, BOUNCE, SHIFT }

    /** Bouncing: the foot's tip toe-down about its toes at most this far (deg), this often (Hz). */
    public static final float BOUNCE_DEG = 8F, BOUNCE_HZ = 4.5F;
    /** Shifted: the foot this far forward and out (px). */
    public static final float SHIFT_AHEAD = 0.9F, SHIFT_OUT = 0.4F;
    /**
     * B3's torso (the straight jacket, px): half its width and depth. Twisted at the waist its back corner swings behind
     * the hips (45 deg: 4.8 px for 2.25 square); past the backrest less {@link #BACK_CLEAR} the sitter scoots forward on
     * the seat by the rest, the feet with it (user 2026-10-25 offline: 8 cm into the slats).
     */
    public static final float TORSO_HALF_W = 4.5F, TORSO_HALF_D = 2.25F, BACK_CLEAR = 0.3F;

    private ChairPose() {
    }

    /**
     * Before the body solve, after {@link ChairLegs#legs}: forward on the seat as far as the chest's twist needs (a chair
     * with a back), then the fidget: {@code idle} on the {@code right} or left foot, {@code w} of the way, {@code time} s in.
     */
    public static void body(HumanoidPoser.Input in, ChairLegs.Chair c, ChairLegs.Result legs, Idle idle, float w, float time, boolean right) {
        float scoot = scoot(c, legs, in.chestYaw);
        if (scoot > 0F && in.hipTarget != null) {
            in.hipTarget.z -= scoot;   // front is -z
            in.ankleRight.z -= scoot;
            in.ankleLeft.z -= scoot;
            legs.hip.z -= scoot;
            legs.knee.z -= scoot;
            legs.ankle.z -= scoot;
        }
        if (w <= 0F || idle == Idle.NONE) return;
        Vector3f ankle = right ? in.ankleRight : in.ankleLeft;
        float side = right ? -1F : 1F;   // the body's right is -x
        if (idle == Idle.BOUNCE) {
            // the heel up and down on the ball of the foot: the foot tips about its toes, the ankle rises with it
            float phase = 0.5F - 0.5F * (float) Math.cos(2 * Math.PI * BOUNCE_HZ * time);
            float extra = (float) Math.toRadians(BOUNCE_DEG * phase * w), base = (float) Math.toRadians(legs.footTipDeg);
            ankle.y -= up(legs, base + extra) - up(legs, base);       // y is down
            ankle.z += back(legs, base + extra) - back(legs, base);   // +z is back
            Quaternionf rest = new Quaternionf().rotationY((float) Math.toRadians(in.rootYaw)).rotateX(base + extra);
            if (right) in.footRestRight = rest;
            else in.footRestLeft = rest;
        } else {
            ankle.add(side * SHIFT_OUT * w, 0, -SHIFT_AHEAD * w);
        }
    }

    /** The ankle over the toe tip (px) with the foot tipped toe-down {@code a} (rad) about it. */
    private static float up(ChairLegs.Result legs, float a) {
        return ChairLegs.TOE_AHEAD * (float) Math.sin(a) + legs.ankleHeight * (float) Math.cos(a);
    }

    /** The ankle behind the toe tip (px), the same. */
    private static float back(ChairLegs.Result legs, float a) {
        return ChairLegs.TOE_AHEAD * (float) Math.cos(a) - legs.ankleHeight * (float) Math.sin(a);
    }

    /** After the body solve: the free hands on the thighs (IK). */
    public static void hands(HumanoidPoser.Input in, SkeletonPose pose, HumanoidPoser poser) {
        for (boolean right : new boolean[]{true, false}) {
            if ((right ? in.right : in.left) == HumanoidPoser.ArmMode.VANILLA) continue;
            if ((right ? in.vanillaRightWeight : in.vanillaLeftWeight) > 0F) continue;
            float side = right ? -1F : 1F;
            Vector3f point = SeatPose.lapPoint(pose, right, SeatPose.CHAIR_LAP_ALONG, SeatPose.CHAIR_LAP_OUT, 2.6F);
            (right ? in.poleRight : in.poleLeft).set(side * SeatPose.CHAIR_POLE_OUT, 1F, SeatPose.CHAIR_POLE_BACK);
            (right ? in.targetRight : in.targetLeft).set(SeatPose.contact(in, pose, poser, right, point));
            if (right) { in.right = HumanoidPoser.ArmMode.IK; in.ikRightWeight = 1F; }
            else { in.left = HumanoidPoser.ArmMode.IK; in.ikLeftWeight = 1F; }
        }
    }

    /** How far forward (px) the hips go for a chest twisted {@code chestYaw} (deg) to keep its back corner off the backrest. */
    public static float scoot(ChairLegs.Chair c, ChairLegs.Result legs, float chestYaw) {
        if (!c.hasBack()) return 0F;
        float a = (float) Math.toRadians(Math.min(90F, Math.abs(chestYaw)));
        float rear = TORSO_HALF_W * (float) Math.sin(a) + TORSO_HALF_D * (float) Math.cos(a);
        float room = (c.backBehind() - c.hipAhead()) * legs.pxPerMetre - BACK_CLEAR;
        return Math.max(0F, rear - Math.max(room, TORSO_HALF_D));
    }
}
