package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollBody;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollDefinition;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollSnapshot;
import com.antaurora.apofirstlight.survivor.SurvivorCorpse;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Ragdoll V1 R-C (docs/dev/ragdoll_v1_plan.md 4): a corpse's drawn pose this frame, from its two newest snapshots (one
 * tick apart, interpolated by the partial tick), as a skeleton pose in model px for a model drawn at the corpse's pose
 * origin facing yaw 0; and the eye in the world for the death camera.
 */
public final class CorpsePoses {
    private static RagdollBody converter;
    private static final RagdollDefinition DEF = RagdollDefinition.get();
    private static final int N = DEF.bodies.length;
    private static final Vector3d[] POS = new Vector3d[N], PREV = new Vector3d[N];
    private static final Quaterniond[] ROT = new Quaterniond[N], PREV_ROT = new Quaterniond[N];

    static {
        for (int i = 0; i < N; i++) {
            POS[i] = new Vector3d();
            PREV[i] = new Vector3d();
            ROT[i] = new Quaterniond();
            PREV_ROT[i] = new Quaterniond();
        }
    }

    private CorpsePoses() {
    }

    /** The corpse's pose this frame into {@code out}; false when it has none (it lies on its back, V0). */
    static boolean pose(SurvivorCorpse corpse, float partial, SkeletonPose out) {
        float[] now = corpse.pose(), before = corpse.posePrevious();
        if (now == null || before == null) return false;
        Vec3 o = corpse.poseOrigin(), po = corpse.poseOriginPrevious();
        Vector3d origin = new Vector3d(o.x, o.y, o.z);
        RagdollSnapshot.read(now, origin, POS, ROT);
        RagdollSnapshot.read(before, new Vector3d(po.x, po.y, po.z), PREV, PREV_ROT);
        for (int i = 0; i < N; i++) {
            PREV[i].lerp(POS[i], partial, POS[i]);
            nlerp(PREV_ROT[i], ROT[i], partial, ROT[i]);
        }
        if (converter == null) converter = new RagdollBody(DEF);
        converter.pose(out, origin, POS, ROT);
        return true;
    }

    /** Where the eye socket of a posed corpse is (world) and which way the head faces (world forward and up). */
    @Nullable
    static Vec3[] eye(SurvivorCorpse corpse, SkeletonPose pose) {
        return eye(pose, corpse.poseOrigin());
    }

    /** The same for a pose drawn at {@code o} facing yaw 0: {eye, forward, up}, or null without an eye socket. */
    @Nullable
    public static Vec3[] eye(SkeletonPose pose, Vec3 o) {
        SkeletonRig rig = pose.rig;
        SkeletonRig.Socket socket = rig.sockets.get("eye");
        if (socket == null) return null;
        Vector3d origin = new Vector3d(o.x, o.y, o.z);
        Vector3f p = pose.point(socket.bone(), socket.pos(), new Vector3f());
        Vector3d at = RagdollBody.world(p, origin, 0, new Vector3d());
        // the head's model forward (-z) and up (-y), turned by the head bone, then into the world (yaw 0 facing)
        Quaterniond face = RagdollBody.facing(0);
        org.joml.Quaternionf head = pose.rotation(socket.bone(), new org.joml.Quaternionf());
        Vector3f f = head.transform(new Vector3f(0, 0, -1)), u = head.transform(new Vector3f(0, -1, 0));
        Vector3d fw = face.transform(new Vector3d(f.x, f.y, f.z)).normalize();
        Vector3d up = face.transform(new Vector3d(u.x, u.y, u.z)).normalize();
        return new Vec3[]{new Vec3(at.x, at.y, at.z), new Vec3(fw.x, fw.y, fw.z), new Vec3(up.x, up.y, up.z)};
    }

    /** Normalised linear blend the short way round (no JOML slerp: see RagdollBody#axis). */
    private static void nlerp(Quaterniond a, Quaterniond b, double t, Quaterniond out) {
        double dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        double s = dot < 0 ? -1 : 1;
        out.set(a.x + (s * b.x - a.x) * t, a.y + (s * b.y - a.y) * t, a.z + (s * b.z - a.z) * t, a.w + (s * b.w - a.w) * t).normalize();
    }
}
