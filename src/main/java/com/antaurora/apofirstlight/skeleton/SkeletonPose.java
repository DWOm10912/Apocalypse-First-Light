package com.antaurora.apofirstlight.skeleton;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Player skeleton V1: one pose of the rig, Java model px. Per bone a local rotation about its pivot and a local
 * translation (only root, pelvis and the shoulders use it), and from those the skinning matrices: {@code world[b]} maps a
 * rest-pose point of bone b to its posed place, {@code W_b = W_parent * T(offset_b) * T(P_b) * R_b * T(-P_b)}.
 */
public final class SkeletonPose {
    public final SkeletonRig rig;
    public final Quaternionf[] local;
    public final Vector3f[] offset;
    public final Matrix4f[] world;
    /**
     * Seated riders V1: per bone, how much its top end (the skin ring at the bone's own pivot end, SkinMesh) is squashed
     * toward its pivot, 1 = none (the compressed torso); and the shoulders' drop in the chest frame (model px) with it.
     */
    public final float[] squash;
    public float shoulderDrop;

    public SkeletonPose(SkeletonRig rig) {
        this.rig = rig;
        int n = rig.size();
        local = new Quaternionf[n];
        offset = new Vector3f[n];
        world = new Matrix4f[n];
        squash = new float[n];
        java.util.Arrays.fill(squash, 1F);
        for (int i = 0; i < n; i++) {
            local[i] = new Quaternionf();
            offset[i] = new Vector3f();
            world[i] = new Matrix4f();
        }
    }

    public SkeletonPose reset() {
        for (int i = 0; i < local.length; i++) {
            local[i].identity();
            offset[i].zero();
            world[i].identity();
            squash[i] = 1F;
        }
        shoulderDrop = 0F;
        return this;
    }

    public void copyFrom(SkeletonPose other) {
        for (int i = 0; i < local.length; i++) {
            local[i].set(other.local[i]);
            offset[i].set(other.offset[i]);
            world[i].set(other.world[i]);
            squash[i] = other.squash[i];
        }
        shoulderDrop = other.shoulderDrop;
    }

    /**
     * Eases this pose from {@code from} (the pose shown before, model px in its own frame) by {@code f} (0: from, 1: this
     * one), every bone's local rotation and offset, and recomputes the matrices. {@code toHere}: from's model px to this
     * pose's (the body's frame may have turned since): taken by the root, which carries the rest.
     */
    public void blendFrom(SkeletonPose from, Matrix4f toHere, float f) {
        Quaternionf turn = toHere.getNormalizedRotation(new Quaternionf()), q = new Quaternionf();
        Vector3f o = new Vector3f();
        for (int b = 0; b < local.length; b++) {
            q.set(from.local[b]);
            o.set(from.offset[b]);
            if (rig.parent[b] < 0) {
                // W = T(offset) T(P) R T(-P) taken over: R' = turn R, offset' = toHere(offset + P) - P
                Vector3f p = rig.pivot[b];
                q.premul(turn);
                toHere.transformPosition(o.add(p)).sub(p);
            }
            local[b].set(q.slerp(local[b], f));
            offset[b].set(o.lerp(offset[b], f));
        }
        update();
    }

    /** Recomputes every skinning matrix from the local rotations and offsets (parents come first in the rig). */
    public void update() {
        for (int b = 0; b < local.length; b++) update(b);
    }

    public void update(int b) {
        Vector3f p = rig.pivot[b];
        Matrix4f m = world[b];
        if (rig.parent[b] < 0) m.identity();
        else m.set(world[rig.parent[b]]);
        m.translate(offset[b]).translate(p).rotate(local[b]).translate(-p.x, -p.y, -p.z);
    }

    /** Updates a bone and everything under it. */
    public void updateFrom(int b) {
        update(b);
        for (int c = b + 1; c < local.length; c++) if (descends(c, b)) update(c);
    }

    public boolean descends(int child, int ancestor) {
        for (int p = rig.parent[child]; p >= 0; p = rig.parent[p]) if (p == ancestor) return true;
        return false;
    }

    /** The posed position of a rest-pose point carried by bone b. */
    public Vector3f point(int b, Vector3f rest, Vector3f out) {
        return world[b].transformPosition(rest, out);
    }

    /** World (model space) rotation of a bone. */
    public Quaternionf rotation(int b, Quaternionf out) {
        return world[b].getNormalizedRotation(out);
    }

    /**
     * The skinning matrix half way through bone b's own rotation (and offset), for the shared ring of vertices at its
     * joint: {@code W_parent * T(offset/2) * T(P) * slerp(I, R, 0.5) * T(-P)}.
     */
    public Matrix4f half(int b, Matrix4f out) {
        Vector3f p = rig.pivot[b];
        Quaternionf h = new Quaternionf().slerp(local[b], 0.5F);
        out.set(world[rig.parent[b]]);
        return out.translate(offset[b].x * 0.5F, offset[b].y * 0.5F, offset[b].z * 0.5F)
                .translate(p).rotate(h).translate(-p.x, -p.y, -p.z);
    }
}
