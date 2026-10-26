package com.antaurora.apofirstlight.skeleton.seat;

import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * AFL Block-Mesh V2 on a chair (docs/dev/block_mesh_v2_b_ingame.md 11): where the hips go and where the legs put the
 * feet, from the chair's own size. Pure (model px, Java: y down, front -z), for client/skeleton/BlockMeshBody and the
 * offline checks alike.
 * <p>The hips sit at vanilla's riding hip height over the chair's floor (the thighs on the cushion; vanilla's riders are
 * calibrated to it: their feet {@link Chair#drop} under the floor, the hips B's reference hip over the feet) less the
 * cushion's give, {@link Chair#hipAhead} ahead of the seat's middle: far enough forward that the knee clears the
 * cushion's front edge with the shin hanging (B's 6 px thigh is 35 cm; the office chair's seat is 60 cm deep). The thigh
 * slopes down at most {@link Chair#thighMaxDeg} (more would cut into the seat), the shin hangs straight down from the
 * knee; when that leaves the foot over the floor, the heel comes up and the foot tips, toes down, until the toes touch
 * (at most {@link #FOOT_TIP_MAX_DEG}). No root shift for the eye: the own first-person camera follows the posed eye
 * (BlockMeshBody chaircam).
 */
public final class ChairLegs {
    /**
     * A chair: the hips' place (metres), how far the thighs may slope, its seat; {@code drop}: how far under the floor
     * vanilla's rider has its feet (0.35 less the seat's riding offset), the hips' height being calibrated by it;
     * {@code backBehind} / {@code backTop}: the backrest's front face behind the seat's middle and its top over the floor
     * (NaN: none to put an arm over).
     */
    public record Chair(String name, float hipAhead, float sink, float thighMaxDeg, float frontEdge, float cushion, float drop,
                        float backBehind, float backTop) {
        public boolean hasBack() {
            return !Float.isNaN(backTop);
        }
    }

    /**
     * cabin_mesh/furniture/chair.json (cabin/ChairSit): a hard 58 x 45 cm seat at 0.465 m, its front edge 21 cm ahead, the
     * ladder back's slats 19.8 cm behind, the top rail at 0.96 m; vanilla's rider was 0.122 under the floor (FurnitureSeat 0.228).
     */
    public static final Chair WOOD = new Chair("wood", 0F, 0F, 3F, 0.21F, 0.465F, 0.122F, 0.198F, 0.96F);
    /** meshes/modern_office_chair (OfficeChairEntity): a padded seat at 0.547 m, 60 cm deep, its front edge 35 cm ahead. */
    public static final Chair OFFICE = new Chair("office", 0.13F, 0.02F, 5F, 0.352F, 0.547F, 0.04F, Float.NaN, Float.NaN);
    public static final float FOOT_TIP_MAX_DEG = 30F;
    /** The toe tip (bm2_rig.py FOOT front) ahead of the ankle, px. */
    static final float TOE_AHEAD = 3.5F;

    private ChairLegs() {
    }

    /** What {@link #legs} chose, for the checks: thigh slope, heel lift (px), foot tip (deg), the joints (model px). */
    public static final class Result {
        public float thighDeg, heelLift, footTipDeg, floor, pxPerMetre, ankleHeight;
        public final Vector3f hip = new Vector3f(), knee = new Vector3f(), ankle = new Vector3f();
    }

    /**
     * The poser's input for a rider on {@code c}: hips ({@link HumanoidPoser.Input#hipTarget}), legs by IK, the foot's
     * turn. {@code floor}: the chair's floor (model y, px), {@code pxPerMetre}: model px in a metre of the world.
     */
    public static Result legs(HumanoidPoser.Input in, SkeletonRig rig, Chair c, float floor, float pxPerMetre) {
        SkeletonRig.Profile b = rig.profile;
        Result out = new Result();
        out.floor = floor;
        out.pxPerMetre = pxPerMetre;
        out.ankleHeight = rig.profile.ankle();
        Vector3f thigh = rig.pivot[rig.thighR], shin = rig.pivot[rig.shinR], foot = rig.pivot[rig.footR];
        float l1 = thigh.distance(shin), l2 = shin.distance(foot), ankle = b.ankle();
        // the hip joints: vanilla's riding hips (its feet drop under the floor, the hips refHip over them), sunk into the
        // cushion, ahead of the seat's middle (the model's origin: the seat entity's or the AFL sitter's place)
        out.hip.set(0, floor - b.refHip().y + (c.drop() + c.sink()) * pxPerMetre, -c.hipAhead() * pxPerMetre);
        in.legsVanilla = false;
        in.eyeTarget = null;
        // no stride on a chair: a held A / D / W / S still moves the rider a little inside travel() before positionRider puts
        // it back, so vanilla's walk animation runs (user 2026-10-25: the legs spread on A / D)
        in.move = 0F;
        in.run = 0F;
        in.walkPhase = 0F;
        in.turnStep = -1F;
        // the look turns the chest at the waist; the hips stay square on the seat (standing they take all of it, HIP_SHARE)
        in.hipShare = 0F;
        in.hipTarget = new Vector3f(out.hip).add(0, rig.pivot[rig.pelvis].y - thigh.y, rig.pivot[rig.pelvis].z - thigh.z);
        // thigh down to bring the hanging shin's foot onto the floor, within its slope; the rest comes up as the heel
        float drop = floor - out.hip.y - l2 - ankle;
        float sin = Math.max((float) Math.sin(Math.toRadians(-20)), Math.min((float) Math.sin(Math.toRadians(c.thighMaxDeg())), drop / l1));
        float cos = (float) Math.sqrt(1 - sin * sin);
        out.thighDeg = (float) Math.toDegrees(Math.asin(sin));
        out.heelLift = Math.max(0F, drop - l1 * sin);
        out.knee.set(0, out.hip.y + l1 * sin, out.hip.z - l1 * cos);
        out.ankle.set(0, out.knee.y + l2, out.knee.z);
        if (drop - l1 * sin < 0) out.ankle.y = floor - ankle;          // a low seat: the IK lifts the knee
        // the foot tipped about the ankle until its toe tip touches the floor
        float tip = 0;
        for (int deg = 1; deg <= FOOT_TIP_MAX_DEG && out.heelLift > 0; deg++) {
            float r = (float) Math.toRadians(deg);
            tip = deg;
            if (TOE_AHEAD * (float) Math.sin(r) + ankle * (float) Math.cos(r) - ankle >= out.heelLift) break;
        }
        out.footTipDeg = tip;
        in.legIkWeight = 1F;
        in.ankleRight.set(-b.footX(), out.ankle.y, out.ankle.z);
        in.ankleLeft.set(b.footX(), out.ankle.y, out.ankle.z);
        in.kneePoleRight.set(0, -0.6F, -1F);
        in.kneePoleLeft.set(0, -0.6F, -1F);
        // toes down: about the body's right axis, in the root's facing (the poser's rest foot is the root's turn)
        in.footRest = tip > 0 ? new Quaternionf().rotationY((float) Math.toRadians(in.rootYaw)).rotateX((float) Math.toRadians(tip)) : null;
        return out;
    }
}
