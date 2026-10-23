package com.antaurora.apofirstlight.physics.rope;

import java.util.List;

/**
 * Something a rope node must stay out of (docs/native_guns/attachments/sling_physics_v1.md 4). Positions are in the
 * chain's simulation space (metres, world axes, gravity -y).
 */
public interface RopeCollider {
    /**
     * Moves the point pos[o .. o + 2] out of this collider so that it keeps r away from its surface. Returns true when it
     * touched; the outward contact normal is then written to normal[0 .. 2].
     */
    boolean resolve(double[] pos, int o, double r, double[] normal);

    /**
     * Continuous collision: the point moved from from[fo ..] (where it stood at the last substep, against this collider as
     * it stood then) to pos[o ..]. A collider that can be crossed within one substep (a thin part, a fast turn) stops the
     * point where it entered; the default only resolves where it ended.
     */
    default boolean sweep(double[] from, int fo, double[] pos, int o, double r, double[] normal) { return resolve(pos, o, r, normal); }

    /** Where a moving collider stands between its last frame (0) and this one (1); the chain calls it every substep. */
    default void blend(double a) {}

    RopeCollider NONE = (pos, o, r, normal) -> false;

    /** Several colliders, each resolved in turn; the last one touched gives the normal. */
    static RopeCollider of(List<? extends RopeCollider> all) {
        if (all.isEmpty()) return NONE;
        if (all.size() == 1) return all.get(0);
        RopeCollider[] list = all.toArray(new RopeCollider[0]);
        return new RopeCollider() {
            @Override
            public boolean resolve(double[] pos, int o, double r, double[] normal) {
                boolean hit = false;
                for (RopeCollider c : list) hit |= c.resolve(pos, o, r, normal);
                return hit;
            }

            @Override
            public boolean sweep(double[] from, int fo, double[] pos, int o, double r, double[] normal) {
                boolean hit = false;
                for (RopeCollider c : list) hit |= c.sweep(from, fo, pos, o, r, normal);
                return hit;
            }

            @Override
            public void blend(double a) { for (RopeCollider c : list) c.blend(a); }
        };
    }

    /** The half space below a plane: points stay r above it (n unit, n . p = d on the plane). */
    static RopeCollider plane(double nx, double ny, double nz, double d) {
        return (pos, o, r, normal) -> {
            double dist = pos[o] * nx + pos[o + 1] * ny + pos[o + 2] * nz - d;
            if (dist >= r) return false;
            pos[o] += nx * (r - dist); pos[o + 1] += ny * (r - dist); pos[o + 2] += nz * (r - dist);
            normal[0] = nx; normal[1] = ny; normal[2] = nz;
            return true;
        };
    }
}
