package com.antaurora.apofirstlight.physics.rope;

import org.joml.Matrix4d;
import org.joml.Matrix4dc;

/**
 * A {@link MeshShape} placed in simulation space by a similarity (rotation, uniform scale, translation), moving from its
 * last frame's placement to this frame's across the chain's substeps. A node's move over a substep is swept against the
 * triangles (relative to the part, as it stood at either end of the substep), so a fast turn cannot carry the part through
 * the strap; a node that still lies just inside a closed part is pushed back out through its nearest face, margin (shape
 * units) being how deep that may be. A placement with no size (a bone an animation hides by scaling it to 0) collides with
 * nothing.
 */
public final class MeshCollider implements RopeCollider {
    private final MeshShape shape;
    private final double margin;
    private final Matrix4d from = new Matrix4d(), to = new Matrix4d(), simFromLocal = new Matrix4d(), localFromSim = new Matrix4d();
    private final Matrix4d lastLocalFromSim = new Matrix4d();
    private final double[] hit = new double[7];
    /** The longest move swept (shape units): half a block, far beyond a node's move relative to a part in one substep. */
    private static final double SWEEP_MAX = 0.5;
    private double scale = 1;
    private boolean placed, degenerate;

    public MeshCollider(MeshShape shape, double margin) {
        this.shape = shape;
        this.margin = margin;
    }

    public MeshShape shape() { return shape; }

    /** This frame's placement; the first one also stands for the last frame. */
    public void place(Matrix4dc simFromLocal) {
        if (placed) from.set(to); else from.set(simFromLocal);
        to.set(simFromLocal);
        placed = true;
        set(0);
        lastLocalFromSim.set(localFromSim);
    }

    /**
     * The share of a rigid motion (sim to sim) the rope's nodes were carried along this frame ({@link RopeChain#carry}):
     * the last frame's placement moves by the same share, so the sweep sees the nodes where they are relative to the part.
     */
    public void carry(Matrix4dc motion, double share) {
        if (share <= 0 || !placed) return;
        Matrix4d moved = new Matrix4d(motion).mul(from);
        double b = 1 - share;
        from.set(
                from.m00() * b + moved.m00() * share, from.m01() * b + moved.m01() * share, from.m02() * b + moved.m02() * share, 0,
                from.m10() * b + moved.m10() * share, from.m11() * b + moved.m11() * share, from.m12() * b + moved.m12() * share, 0,
                from.m20() * b + moved.m20() * share, from.m21() * b + moved.m21() * share, from.m22() * b + moved.m22() * share, 0,
                from.m30() * b + moved.m30() * share, from.m31() * b + moved.m31() * share, from.m32() * b + moved.m32() * share, 1);
        set(0);
        lastLocalFromSim.set(localFromSim);
    }

    /** Places the collider where it is now, with no motion from the last frame (static settles, resets). */
    public void hold(Matrix4dc simFromLocal) {
        placed = false;
        place(simFromLocal);
    }

    @Override
    public void blend(double a) {
        lastLocalFromSim.set(localFromSim);
        set(a);
    }

    private void set(double a) {
        if (a >= 1) simFromLocal.set(to);
        else if (a <= 0) simFromLocal.set(from);
        else {
            double b = 1 - a;
            simFromLocal.set(
                    from.m00() * b + to.m00() * a, from.m01() * b + to.m01() * a, from.m02() * b + to.m02() * a, 0,
                    from.m10() * b + to.m10() * a, from.m11() * b + to.m11() * a, from.m12() * b + to.m12() * a, 0,
                    from.m20() * b + to.m20() * a, from.m21() * b + to.m21() * a, from.m22() * b + to.m22() * a, 0,
                    from.m30() * b + to.m30() * a, from.m31() * b + to.m31() * a, from.m32() * b + to.m32() * a, 1);
        }
        scale = Math.cbrt(Math.abs(simFromLocal.determinant3x3()));
        degenerate = !(scale > 1e-9) || !Double.isFinite(scale + simFromLocal.m30() + simFromLocal.m31() + simFromLocal.m32());
        if (!degenerate) simFromLocal.invertAffine(localFromSim);
    }

    @Override
    public boolean sweep(double[] start, int so, double[] pos, int o, double r, double[] normal) {
        if (shape.isEmpty() || degenerate) return false;
        Matrix4d m = lastLocalFromSim, n = localFromSim;
        double x = start[so], y = start[so + 1], z = start[so + 2];
        double ax = m.m00() * x + m.m10() * y + m.m20() * z + m.m30();
        double ay = m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
        double az = m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
        x = pos[o]; y = pos[o + 1]; z = pos[o + 2];
        double bx = n.m00() * x + n.m10() * y + n.m20() * z + n.m30();
        double by = n.m01() * x + n.m11() * y + n.m21() * z + n.m31();
        double bz = n.m02() * x + n.m12() * y + n.m22() * z + n.m32();
        // a move longer than a substep's (the part came back from hidden) only resolves where it ended
        if ((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az) > SWEEP_MAX * SWEEP_MAX) return resolve(pos, o, r, normal);
        if (shape.sweep(ax, ay, az, bx, by, bz, hit) < 0) return resolve(pos, o, r, normal);
        // stopped where it entered, its radius out along the face
        double rl = r / scale;
        double lx = hit[0] + hit[3] * rl, ly = hit[1] + hit[4] * rl, lz = hit[2] + hit[5] * rl;
        double nx = hit[3], ny = hit[4], nz = hit[5];
        Matrix4d s = simFromLocal;
        pos[o] = s.m00() * lx + s.m10() * ly + s.m20() * lz + s.m30();
        pos[o + 1] = s.m01() * lx + s.m11() * ly + s.m21() * lz + s.m31();
        pos[o + 2] = s.m02() * lx + s.m12() * ly + s.m22() * lz + s.m32();
        double wx = s.m00() * nx + s.m10() * ny + s.m20() * nz, wy = s.m01() * nx + s.m11() * ny + s.m21() * nz,
                wz = s.m02() * nx + s.m12() * ny + s.m22() * nz, len = Math.sqrt(wx * wx + wy * wy + wz * wz);
        normal[0] = wx / len; normal[1] = wy / len; normal[2] = wz / len;
        resolve(pos, o, r, normal);
        return true;
    }

    @Override
    public boolean resolve(double[] pos, int o, double r, double[] normal) {
        if (shape.isEmpty() || degenerate) return false;
        Matrix4d m = localFromSim;
        double x = pos[o], y = pos[o + 1], z = pos[o + 2];
        double lx = m.m00() * x + m.m10() * y + m.m20() * z + m.m30();
        double ly = m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
        double lz = m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
        double rl = r / scale;
        int t = shape.nearest(lx, ly, lz, rl + margin, hit);
        if (t < 0) return false;
        double dx = lx - hit[0], dy = ly - hit[1], dz = lz - hit[2], dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double side = dx * hit[3] + dy * hit[4] + dz * hit[5];
        double nx, ny, nz;
        if ((t & 1 << 30) != 0 && side < 0 || dist < 1e-9) { nx = hit[3]; ny = hit[4]; nz = hit[5]; }
        else if (dist < rl) { nx = dx / dist; ny = dy / dist; nz = dz / dist; }
        else return false;
        lx = hit[0] + nx * rl; ly = hit[1] + ny * rl; lz = hit[2] + nz * rl;
        Matrix4d s = simFromLocal;
        pos[o] = s.m00() * lx + s.m10() * ly + s.m20() * lz + s.m30();
        pos[o + 1] = s.m01() * lx + s.m11() * ly + s.m21() * lz + s.m31();
        pos[o + 2] = s.m02() * lx + s.m12() * ly + s.m22() * lz + s.m32();
        double wx = s.m00() * nx + s.m10() * ny + s.m20() * nz, wy = s.m01() * nx + s.m11() * ny + s.m21() * nz,
                wz = s.m02() * nx + s.m12() * ny + s.m22() * nz, len = Math.sqrt(wx * wx + wy * wy + wz * wz);
        normal[0] = wx / len; normal[1] = wy / len; normal[2] = wz / len;
        return true;
    }
}
