package com.antaurora.apofirstlight.physics.rope;

import org.joml.Matrix4dc;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

/**
 * An oriented box (a body part: torso, head, arm, leg): centre, three orthonormal axes and half extents in simulation space.
 * A node inside is pushed out through the nearest face; one just outside is pushed off the nearest surface point.
 */
public final class BoxCollider implements RopeCollider {
    private final double cx, cy, cz;
    private final double[] axes = new double[9], half = new double[3];
    /** No size along some axis (a part scaled to 0) or not finite: collides with nothing. */
    private final boolean degenerate;

    /**
     * The box lo .. hi of a local frame placed by sim (sim from local: a rotation with a scale per axis, as a body part's
     * matrix times the simulation frame).
     */
    public BoxCollider(Matrix4dc sim, double[] lo, double[] hi) {
        Vector3d c = sim.transformPosition(new Vector3d((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2));
        cx = c.x; cy = c.y; cz = c.z;
        boolean bad = !Double.isFinite(cx + cy + cz);
        for (int k = 0; k < 3; k++) {
            Vector3d a = sim.transformDirection(new Vector3d(k == 0 ? 1 : 0, k == 1 ? 1 : 0, k == 2 ? 1 : 0));
            double len = a.length();
            if (!(len > 1e-9) || !Double.isFinite(len)) { bad = true; len = 1; }
            axes[k * 3] = a.x / len; axes[k * 3 + 1] = a.y / len; axes[k * 3 + 2] = a.z / len;
            half[k] = Math.abs(hi[k] - lo[k]) / 2 * len;
        }
        degenerate = bad;
    }

    public static BoxCollider of(Matrix4dc simFromView, Matrix4fc viewFromLocal, double[] lo, double[] hi) {
        return new BoxCollider(new org.joml.Matrix4d(simFromView).mul(new org.joml.Matrix4d(viewFromLocal)), lo, hi);
    }

    /** A point that crossed into the box (grown by r) this substep stops on the face it came in through. */
    @Override
    public boolean sweep(double[] start, int so, double[] pos, int o, double r, double[] normal) {
        if (degenerate) return false;
        double enter = 0, exit = 1, sign = 0;
        int face = -1;
        for (int k = 0; k < 3; k++) {
            double ax = axes[k * 3], ay = axes[k * 3 + 1], az = axes[k * 3 + 2];
            double a = (start[so] - cx) * ax + (start[so + 1] - cy) * ay + (start[so + 2] - cz) * az;
            double b = (pos[o] - cx) * ax + (pos[o + 1] - cy) * ay + (pos[o + 2] - cz) * az;
            double e = half[k] + r, d = b - a;
            if (Math.abs(d) < 1e-12) {
                if (Math.abs(a) > e) return false;
                continue;
            }
            double t0 = (-e - a) / d, t1 = (e - a) / d;
            if (t0 > t1) { double t = t0; t0 = t1; t1 = t; }
            if (t0 > enter) { enter = t0; face = k; sign = d > 0 ? -1 : 1; }
            exit = Math.min(exit, t1);
            if (enter > exit) return false;
        }
        // started inside (the box moved onto it): out through the nearest face
        if (face < 0) return resolve(pos, o, r, normal);
        for (int j = 0; j < 3; j++) {
            pos[o + j] = start[so + j] + (pos[o + j] - start[so + j]) * enter;
            normal[j] = axes[face * 3 + j] * sign;
        }
        return true;
    }

    @Override
    public boolean resolve(double[] pos, int o, double r, double[] normal) {
        if (degenerate) return false;
        double qx = pos[o] - cx, qy = pos[o + 1] - cy, qz = pos[o + 2] - cz;
        double[] l = new double[3];
        boolean inside = true;
        for (int k = 0; k < 3; k++) {
            l[k] = qx * axes[k * 3] + qy * axes[k * 3 + 1] + qz * axes[k * 3 + 2];
            if (Math.abs(l[k]) > half[k] + r) return false;
            if (Math.abs(l[k]) > half[k]) inside = false;
        }
        if (inside) {
            // out through the nearest face
            int best = 0;
            double depth = Double.MAX_VALUE;
            for (int k = 0; k < 3; k++) { double d = half[k] - Math.abs(l[k]); if (d < depth) { depth = d; best = k; } }
            double s = l[best] >= 0 ? 1 : -1, move = depth + r;
            for (int j = 0; j < 3; j++) { normal[j] = axes[best * 3 + j] * s; pos[o + j] += normal[j] * move; }
            return true;
        }
        // nearest surface point
        double px = cx, py = cy, pz = cz;
        for (int k = 0; k < 3; k++) {
            double v = Math.max(-half[k], Math.min(half[k], l[k]));
            px += axes[k * 3] * v; py += axes[k * 3 + 1] * v; pz += axes[k * 3 + 2] * v;
        }
        double dx = pos[o] - px, dy = pos[o + 1] - py, dz = pos[o + 2] - pz, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist >= r || dist < 1e-12) return false;
        normal[0] = dx / dist; normal[1] = dy / dist; normal[2] = dz / dist;
        pos[o] = px + normal[0] * r; pos[o + 1] = py + normal[1] * r; pos[o + 2] = pz + normal[2] * r;
        return true;
    }
}
