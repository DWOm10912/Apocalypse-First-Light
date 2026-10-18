package com.antaurora.apofirstlight.skeleton.ragdoll;

import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Ragdoll V1 (docs/dev/ragdoll_v1_plan.md 3): a body's boxes as plain numbers, for the network and the corpse record.
 * Per box seven floats: its centre relative to an origin (blocks, world axes) and its turn (quaternion x y z w, model
 * axes to world). Pure data.
 */
public final class RagdollSnapshot {
    public static final int FLOATS = 7;

    private RagdollSnapshot() {
    }

    /** The boxes of a body relative to {@code origin}. */
    public static float[] capture(RagdollBody body, Vector3d origin) {
        float[] out = new float[body.size() * FLOATS];
        for (int i = 0; i < body.size(); i++) {
            Vector3d p = body.position(i);
            Quaterniond q = body.orientation(i);
            int o = i * FLOATS;
            out[o] = (float) (p.x - origin.x);
            out[o + 1] = (float) (p.y - origin.y);
            out[o + 2] = (float) (p.z - origin.z);
            out[o + 3] = (float) q.x;
            out[o + 4] = (float) q.y;
            out[o + 5] = (float) q.z;
            out[o + 6] = (float) q.w;
        }
        return out;
    }

    /** Box places back from a snapshot (absolute, with {@code origin} added); the turns normalised. */
    public static void read(float[] data, Vector3d origin, Vector3d[] pos, Quaterniond[] rot) {
        for (int i = 0; i < pos.length; i++) {
            int o = i * FLOATS;
            pos[i].set(origin.x + data[o], origin.y + data[o + 1], origin.z + data[o + 2]);
            rot[i].set(data[o + 3], data[o + 4], data[o + 5], data[o + 6]).normalize();
        }
    }

    /** True when {@code data} fits a body of {@code bodies} boxes: the right length, finite, no zero turn. */
    public static boolean valid(float[] data, int bodies) {
        if (data == null || data.length != bodies * FLOATS) return false;
        for (int i = 0; i < bodies; i++) {
            int o = i * FLOATS;
            double n = 0;
            for (int k = 0; k < FLOATS; k++) {
                float v = data[o + k];
                if (!Float.isFinite(v) || Math.abs(v) > 64F) return false;
                if (k >= 3) n += v * v;
            }
            if (n < 0.5) return false;
        }
        return true;
    }
}
