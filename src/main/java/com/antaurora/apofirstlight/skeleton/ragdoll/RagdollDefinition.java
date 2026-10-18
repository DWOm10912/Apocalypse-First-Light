package com.antaurora.apofirstlight.skeleton.ragdoll;

import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Ragdoll V1 (docs/dev/ragdoll_v1_plan.md 2): the humanoid rig as rigid bodies, built from the rig JSON so another
 * skeleton gets its own. Eleven boxes cut from the skin boxes at the joints (pelvis, chest, head, upper arms, forearms
 * with the hands, thighs, shins with the feet), their masses, the joints between them (ball joints with swing and twist
 * limits; elbows and knees hinges) and the points that touch the world. Java model px (y down, front -z, the body's
 * right -x), the rig's own axes; pure data.
 */
public final class RagdollDefinition {
    public enum Joint { ROOT, BALL, HINGE }

    /**
     * One rigid body. Limits in radians about the model axes of the child relative to its parent (rest = 0). The joint
     * point may drift up to {@code slack} model px along each of the parent's axes (the neck: a box head on a thinner
     * chest would otherwise rest on an edge).
     */
    public record Body(String name, int bone, int[] attached, float[] box, float mass, Vector3f centre, Vector3f inertia,
                       int parent, Joint joint, Vector3f anchor, Vector3f slack, float xMin, float xMax, float zMin, float zMax,
                       float twistMin, float twistMax, Vector3f[] points, Vector3f[] inner) {
        /** Box size (model px) along x, y, z. */
        public float size(int axis) { return box[axis + 3] - box[axis]; }

        Body withSlack(float sx, float sy, float sz) {
            return new Body(name, bone, attached, box, mass, centre, inertia, parent, joint, anchor, new Vector3f(sx, sy, sz),
                    xMin, xMax, zMin, zMax, twistMin, twistMax, points, inner);
        }
    }

    /** Model px to metres (the player model draws at 15/16). */
    public static final float SCALE = 0.9375F, PX = SCALE / 16F;
    /** Sample spacing on a box's surface (model px). */
    private static final float SPACING = 2F;
    /** Self-collision points sit this far inside the box (model px), so neighbours that touch at rest do not collide. */
    private static final float INSET = 0.4F;

    public final SkeletonRig rig;
    public final Body[] bodies;
    /** Pairs of bodies whose surfaces may not cross (limbs against the trunk and against each other). */
    public final int[][] selfPairs;
    public final float totalMass;
    public final int pelvis, chest, head;

    private RagdollDefinition(SkeletonRig rig) {
        this.rig = rig;
        List<Body> list = new ArrayList<>();
        SkeletonRig.Box trunk = part(rig, "body"), armR = part(rig, "right_arm"), armL = part(rig, "left_arm"),
                legR = part(rig, "right_leg"), legL = part(rig, "left_leg");
        float waist = rig.pivot[rig.chest].y;
        // trunk
        list.add(body(rig, "pelvis", rig.pelvis, new int[]{rig.root}, cut(trunk, waist, trunk.y() + trunk.h()), 12F, -1,
                Joint.ROOT, rig.pivot[rig.pelvis], 0, 0, 0, 0, 0, 0));
        list.add(body(rig, "chest", rig.chest, new int[0], cut(trunk, trunk.y(), waist), 22F, 0,
                Joint.BALL, rig.pivot[rig.chest], -25, 30, -15, 15, -45, 45));
        Vector3f hp = rig.pivot[rig.head];
        list.add(body(rig, "head", rig.head, new int[0], new float[]{hp.x - 4, hp.y - 8, hp.z - 4, hp.x + 4, hp.y, hp.z + 4}, 5F, 1,
                Joint.BALL, hp, -70, 60, -35, 35, -70, 70).withSlack(0, 0, 2));
        // arms: the right arm swings out about +z, the left about -z; forward is -x
        arm(list, rig, armR, rig.upperArmR, rig.forearmR, rig.handR, true);
        arm(list, rig, armL, rig.upperArmL, rig.forearmL, rig.handL, false);
        leg(list, rig, legR, rig.thighR, rig.shinR, rig.footR, true);
        leg(list, rig, legL, rig.thighL, rig.shinL, rig.footL, false);
        bodies = list.toArray(new Body[0]);
        float m = 0;
        for (Body b : bodies) m += b.mass();
        totalMass = m;
        pelvis = index("pelvis");
        chest = index("chest");
        head = index("head");
        int uR = index("upper_arm_r"), fR = index("forearm_r"), uL = index("upper_arm_l"), fL = index("forearm_l"),
                tR = index("thigh_r"), sR = index("shin_r"), tL = index("thigh_l"), sL = index("shin_l");
        selfPairs = new int[][]{
                {fR, chest}, {fR, pelvis}, {fR, head}, {uR, pelvis}, {fL, chest}, {fL, pelvis}, {fL, head}, {uL, pelvis},
                {fR, fL}, {fR, uL}, {fL, uR}, {fR, tR}, {fR, tL}, {fL, tR}, {fL, tL},
                {tR, tL}, {sR, sL}, {tR, sL}, {sR, tL}};
    }

    private static RagdollDefinition loaded;

    public static synchronized RagdollDefinition get() {
        if (loaded == null) loaded = new RagdollDefinition(SkeletonRig.get());
        return loaded;
    }

    public static RagdollDefinition of(SkeletonRig rig) {
        return new RagdollDefinition(rig);
    }

    public int index(String name) {
        for (int i = 0; i < bodies.length; i++) if (bodies[i].name().equals(name)) return i;
        throw new IllegalArgumentException("ragdoll: no body " + name);
    }

    /** The body a bone moves with (its own or the one it is attached to), or -1. */
    public int bodyOf(int bone) {
        for (int i = 0; i < bodies.length; i++) {
            if (bodies[i].bone() == bone) return i;
            for (int a : bodies[i].attached()) if (a == bone) return i;
        }
        return -1;
    }

    private void arm(List<Body> list, SkeletonRig rig, SkeletonRig.Box box, int upper, int fore, int hand, boolean right) {
        float elbow = rig.pivot[fore].y;
        String s = right ? "_r" : "_l";
        float zMin = right ? -30 : -160, zMax = right ? 160 : 30;
        list.add(body(rig, "upper_arm" + s, upper, new int[0], cut(box, box.y(), elbow), 2.5F, 1,
                Joint.BALL, rig.pivot[upper], -170, 50, zMin, zMax, -80, 80));
        list.add(body(rig, "forearm" + s, fore, new int[]{hand}, cut(box, elbow, box.y() + box.h()), 2F, list.size() - 1,
                Joint.HINGE, rig.pivot[fore], limit(rig, fore, 0, -145), limit(rig, fore, 1, 0), 0, 0, 0, 0));
    }

    private void leg(List<Body> list, SkeletonRig rig, SkeletonRig.Box box, int thigh, int shin, int foot, boolean right) {
        float knee = rig.pivot[shin].y;
        String s = right ? "_r" : "_l";
        float zMin = right ? -15 : -50, zMax = right ? 50 : 15;
        list.add(body(rig, "thigh" + s, thigh, new int[0], cut(box, box.y(), knee), 9F, 0,
                Joint.BALL, rig.pivot[thigh], -120, 30, zMin, zMax, -40, 40));
        list.add(body(rig, "shin" + s, shin, new int[]{foot}, cut(box, knee, box.y() + box.h()), 4.5F, list.size() - 1,
                Joint.HINGE, rig.pivot[shin], limit(rig, shin, 0, 0), limit(rig, shin, 1, 150), 0, 0, 0, 0));
    }

    /** The rig's x limit of a bone (degrees), or the fallback. */
    private static float limit(SkeletonRig rig, int bone, int end, float fallback) {
        SkeletonRig.Limit l = rig.limits[bone];
        if (l == null) return fallback;
        float v = end == 0 ? l.minX() : l.maxX();
        return Float.isNaN(v) ? fallback : v;
    }

    private static Body body(SkeletonRig rig, String name, int bone, int[] attached, float[] box, float mass, int parent,
                             Joint joint, Vector3f anchor, float xMin, float xMax, float zMin, float zMax, float tMin, float tMax) {
        Vector3f centre = new Vector3f((box[0] + box[3]) / 2, (box[1] + box[4]) / 2, (box[2] + box[5]) / 2);
        float w = (box[3] - box[0]) * PX, h = (box[4] - box[1]) * PX, d = (box[5] - box[2]) * PX;
        Vector3f inertia = new Vector3f(mass / 12 * (h * h + d * d), mass / 12 * (w * w + d * d), mass / 12 * (w * w + h * h));
        float r = (float) Math.PI / 180F;
        return new Body(name, bone, attached, box, mass, centre, inertia, parent, joint, new Vector3f(anchor), new Vector3f(),
                xMin * r, xMax * r, zMin * r, zMax * r, tMin * r, tMax * r, surface(box, 0F), surface(box, INSET));
    }

    /** Points on a box's surface, at most SPACING apart, the box shrunk by {@code inset}. */
    private static Vector3f[] surface(float[] box, float inset) {
        float[] lo = {box[0] + inset, box[1] + inset, box[2] + inset}, hi = {box[3] - inset, box[4] - inset, box[5] - inset};
        int[] n = new int[3];
        for (int a = 0; a < 3; a++) n[a] = Math.max(1, (int) Math.ceil((hi[a] - lo[a]) / SPACING));
        List<Vector3f> out = new ArrayList<>();
        for (int i = 0; i <= n[0]; i++) for (int j = 0; j <= n[1]; j++) for (int k = 0; k <= n[2]; k++) {
            if (i != 0 && i != n[0] && j != 0 && j != n[1] && k != 0 && k != n[2]) continue;
            out.add(new Vector3f(lo[0] + (hi[0] - lo[0]) * i / n[0], lo[1] + (hi[1] - lo[1]) * j / n[1], lo[2] + (hi[2] - lo[2]) * k / n[2]));
        }
        return out.toArray(new Vector3f[0]);
    }

    private static SkeletonRig.Box part(SkeletonRig rig, String name) {
        for (SkeletonRig.SkinPart p : rig.skin) if (p.part().equals(name)) return p.classic();
        throw new IllegalArgumentException("ragdoll: no skin part " + name);
    }

    /** The slice of a skin box between two model y levels, as {minX, minY, minZ, maxX, maxY, maxZ}. */
    private static float[] cut(SkeletonRig.Box b, float top, float bottom) {
        return new float[]{b.x(), top, b.z(), b.x() + b.w(), bottom, b.z() + b.d()};
    }
}
